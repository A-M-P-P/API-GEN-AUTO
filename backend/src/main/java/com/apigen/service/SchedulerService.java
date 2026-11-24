package com.apigen.service;

import com.apigen.config.AppProperties;
import com.apigen.domain.Schedule;
import com.apigen.repository.ScheduleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduleRepository scheduleRepository;
    private final EndpointService endpointService;
    private final AppProperties properties;
    private final TaskExecutor taskExecutor;

    public SchedulerService(ScheduleRepository scheduleRepository,
                            EndpointService endpointService,
                            AppProperties properties,
                            @Qualifier("taskExecutor") TaskExecutor taskExecutor) {
        this.scheduleRepository = scheduleRepository;
        this.endpointService = endpointService;
        this.properties = properties;
        this.taskExecutor = taskExecutor;
    }

    @Scheduled(fixedDelayString = "${apigen.scheduler.poll-interval-ms:15000}")
    @Transactional
    public void poll() {
        if (!properties.scheduler().enabled()) {
            return;
        }
        List<Schedule> due = scheduleRepository.findByEnabledTrueAndNextRunAtLessThanEqual(Instant.now());
        for (Schedule schedule : due) {
            claim(schedule);
        }
    }

    protected void claim(Schedule schedule) {
        // Réserve le créneau avant l'exécution asynchrone pour éviter les déclenchements multiples.
        schedule.setNextRunAt(endpointService.nextRun(schedule.getCronExpression(), Instant.now()));
        scheduleRepository.save(schedule);
        Long endpointId = schedule.getEndpoint().getId();
        log.info("Planification déclenchée pour l'endpoint {} ({})", endpointId, schedule.getCronExpression());
        taskExecutor.execute(() -> executeScheduled(endpointId));
    }

    @Transactional
    public void executeScheduled(Long endpointId) {
        try {
            endpointService.runScheduled(endpointId);
            scheduleRepository.findByEndpointId(endpointId).ifPresent(schedule -> {
                schedule.setLastRunAt(Instant.now());
                scheduleRepository.save(schedule);
            });
        } catch (Exception e) {
            log.error("Exécution planifiée échouée pour l'endpoint {} : {}", endpointId, e.getMessage(), e);
        }
    }
}
