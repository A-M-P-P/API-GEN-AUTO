package com.apigen.service;

import com.apigen.domain.ApiStatus;
import com.apigen.domain.RunStatus;
import com.apigen.dto.CommonDtos;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.repository.ConnectionRepository;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.ExchangeRunRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumMap;
import java.util.Map;

@Service
public class DashboardService {

    private final ApiDefinitionRepository apiRepository;
    private final EndpointRepository endpointRepository;
    private final ConnectionRepository connectionRepository;
    private final ExchangeRunRepository runRepository;
    private final RunService runService;

    public DashboardService(ApiDefinitionRepository apiRepository,
                            EndpointRepository endpointRepository,
                            ConnectionRepository connectionRepository,
                            ExchangeRunRepository runRepository,
                            RunService runService) {
        this.apiRepository = apiRepository;
        this.endpointRepository = endpointRepository;
        this.connectionRepository = connectionRepository;
        this.runRepository = runRepository;
        this.runService = runService;
    }

    @Transactional(readOnly = true)
    public CommonDtos.DashboardResponse dashboard() {
        Instant startOfDay = LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
        Instant since24h = Instant.now().minusSeconds(24 * 3600);

        long total24h = runRepository.countByStartedAtAfterAndStatus(since24h, RunStatus.SUCCESS)
                + runRepository.countByStartedAtAfterAndStatus(since24h, RunStatus.FAILED);
        long success24h = runRepository.countByStartedAtAfterAndStatus(since24h, RunStatus.SUCCESS);
        double successRate = total24h == 0 ? 0.0 : (success24h * 100.0) / total24h;

        Map<RunStatus, Long> runsByStatus = new EnumMap<>(RunStatus.class);
        for (RunStatus status : RunStatus.values()) {
            runsByStatus.put(status, runRepository.countByStatus(status));
        }

        return new CommonDtos.DashboardResponse(
                apiRepository.count(),
                apiRepository.countByStatus(ApiStatus.ACTIVE),
                endpointRepository.count(),
                connectionRepository.count(),
                runRepository.countByStartedAtAfterAndStatus(startOfDay, RunStatus.SUCCESS)
                        + runRepository.countByStartedAtAfterAndStatus(startOfDay, RunStatus.FAILED),
                Math.round(successRate * 10.0) / 10.0,
                runsByStatus,
                runService.recent(8)
        );
    }
}
