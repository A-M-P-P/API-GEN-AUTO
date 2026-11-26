package com.apigen.service;

import com.apigen.domain.ApiDefinition;
import com.apigen.domain.Endpoint;
import com.apigen.domain.Schedule;
import com.apigen.dto.ApiDtos;
import com.apigen.dto.RunDtos;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.ScheduleRepository;
import com.apigen.security.SecurityUtils;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.regex.Pattern;

@Service
public class EndpointService {

    private static final Pattern PATH_PATTERN = Pattern.compile("^/[A-Za-z0-9_/\\-{}.]*$");

    private final EndpointRepository endpointRepository;
    private final ScheduleRepository scheduleRepository;
    private final ApiDefinitionService apiService;
    private final ExecutionService executionService;
    private final EndpointMapper mapper;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public EndpointService(EndpointRepository endpointRepository,
                           ScheduleRepository scheduleRepository,
                           ApiDefinitionService apiService,
                           ExecutionService executionService,
                           EndpointMapper mapper,
                           ObjectMapper objectMapper,
                           AuditService auditService) {
        this.endpointRepository = endpointRepository;
        this.scheduleRepository = scheduleRepository;
        this.apiService = apiService;
        this.executionService = executionService;
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public ApiDtos.EndpointResponse get(Long id) {
        return mapper.toResponse(endpointWithSchedule(id));
    }

    @Transactional
    public ApiDtos.EndpointResponse create(Long apiId, ApiDtos.EndpointRequest request) {
        ApiDefinition api = apiService.requireEntity(apiId);
        validatePath(request.path());
        String method = request.method().toUpperCase();
        endpointRepository.findByApiIdAndMethodIgnoreCaseAndPath(apiId, method, request.path())
                .ifPresent(existing -> {
                    throw ApiException.conflict("Un endpoint existe déjà pour " + method + " " + request.path());
                });

        Endpoint endpoint = new Endpoint();
        endpoint.setApi(api);
        endpoint.setName(request.name().trim());
        endpoint.setMethod(method);
        endpoint.setPath(request.path());
        endpoint.setDirection(request.direction() == null ? com.apigen.domain.Direction.INBOUND : request.direction());
        endpoint.setDescription(request.description());
        applyResponseFormat(endpoint, request);
        endpoint.setPipeline(writePipeline(request.pipeline()));
        endpoint.setActive(request.active() == null || request.active());
        Endpoint saved = endpointRepository.save(endpoint);
        api.touch();
        auditService.record("ENDPOINT_CREATE", "Endpoint", String.valueOf(saved.getId()),
                "Création de l'endpoint " + saved.getName() + " sur " + api.getCode());
        return mapper.toResponse(endpointWithSchedule(saved.getId()));
    }

    @Transactional
    public ApiDtos.EndpointResponse update(Long id, ApiDtos.EndpointRequest request) {
        Endpoint endpoint = endpointWithSchedule(id);
        validatePath(request.path());
        String method = request.method().toUpperCase();
        endpointRepository.findByApiIdAndMethodIgnoreCaseAndPath(endpoint.getApi().getId(), method, request.path())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw ApiException.conflict("Un endpoint existe déjà pour " + method + " " + request.path());
                });
        endpoint.setName(request.name().trim());
        endpoint.setMethod(method);
        endpoint.setPath(request.path());
        if (request.direction() != null) {
            endpoint.setDirection(request.direction());
        }
        endpoint.setDescription(request.description());
        applyResponseFormat(endpoint, request);
        endpoint.setPipeline(writePipeline(request.pipeline()));
        if (request.active() != null) {
            endpoint.setActive(request.active());
        }
        endpoint.touch();
        Endpoint saved = endpointRepository.save(endpoint);
        auditService.record("ENDPOINT_UPDATE", "Endpoint", String.valueOf(saved.getId()),
                "Mise à jour de l'endpoint " + saved.getName());
        return mapper.toResponse(endpointWithSchedule(saved.getId()));
    }

    @Transactional
    public void delete(Long id) {
        Endpoint endpoint = endpointRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Endpoint introuvable : " + id));
        endpointRepository.delete(endpoint);
        auditService.record("ENDPOINT_DELETE", "Endpoint", String.valueOf(id), "Suppression de " + endpoint.getName());
    }

    @Transactional
    public ApiDtos.EndpointResponse setActive(Long id, boolean active) {
        Endpoint endpoint = endpointWithSchedule(id);
        endpoint.setActive(active);
        endpoint.touch();
        Endpoint saved = endpointRepository.save(endpoint);
        return mapper.toResponse(endpointWithSchedule(saved.getId()));
    }

    @Transactional
    public ApiDtos.EndpointResponse upsertSchedule(Long endpointId, ApiDtos.ScheduleRequest request) {
        Endpoint endpoint = endpointWithSchedule(endpointId);
        validateCron(request.cronExpression());
        Schedule schedule = scheduleRepository.findByEndpointId(endpointId).orElseGet(Schedule::new);
        schedule.setEndpoint(endpoint);
        schedule.setCronExpression(request.cronExpression().trim());
        schedule.setEnabled(request.enabled() == null || request.enabled());
        schedule.setPayload(writeJson(request.payload()));
        if (schedule.isEnabled()) {
            schedule.setNextRunAt(nextRun(request.cronExpression(), Instant.now()));
        } else {
            schedule.setNextRunAt(null);
        }
        scheduleRepository.save(schedule);
        endpoint.setSchedule(schedule);
        auditService.record("SCHEDULE_UPDATE", "Endpoint", String.valueOf(endpointId),
                "Planification " + request.cronExpression() + " sur " + endpoint.getName());
        return mapper.toResponse(endpointWithSchedule(endpointId));
    }

    @Transactional
    public void deleteSchedule(Long endpointId) {
        Schedule schedule = scheduleRepository.findByEndpointId(endpointId)
                .orElseThrow(() -> ApiException.notFound("Aucune planification pour cet endpoint"));
        scheduleRepository.delete(schedule);
        auditService.record("SCHEDULE_DELETE", "Endpoint", String.valueOf(endpointId), "Suppression de la planification");
    }

    @Transactional
    public RunDtos.RunResponse runNow(Long endpointId, JsonNode payload) {
        Endpoint endpoint = endpointWithSchedule(endpointId);
        ExecutionService.ExecutionOutcome outcome = executionService.manualRun(endpoint, payload);
        return runResponse(outcome);
    }

    @Transactional
    public RunDtos.RunResponse runScheduled(Long endpointId) {
        Endpoint endpoint = endpointWithSchedule(endpointId);
        Schedule schedule = endpoint.getSchedule();
        JsonNode payload = readJson(schedule == null ? null : schedule.getPayload());
        ExecutionService.ExecutionOutcome outcome = executionService.scheduledRun(endpoint, payload);
        return runResponse(outcome);
    }

    private Endpoint endpointWithSchedule(Long id) {
        return endpointRepository.findByIdWithSchedule(id)
                .orElseThrow(() -> ApiException.notFound("Endpoint introuvable : " + id));
    }

    public Instant nextRun(String cron, Instant from) {
        CronExpression expression = CronExpression.parse(cron);
        ZonedDateTime next = expression.next(from.atZone(ZoneOffset.UTC));
        return next == null ? null : next.toInstant();
    }

    private void validateCron(String cron) {
        try {
            CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("Expression cron invalide : " + e.getMessage());
        }
    }

    private void validatePath(String path) {
        if (!PATH_PATTERN.matcher(path).matches()) {
            throw ApiException.badRequest("Chemin d'endpoint invalide");
        }
    }

    private void applyResponseFormat(Endpoint endpoint, ApiDtos.EndpointRequest request) {
        endpoint.setResponseFormat(request.responseFormat() == null
                ? com.apigen.domain.ResponseFormat.JSON : request.responseFormat());
        endpoint.setResponseFileName(blankToNull(request.responseFileName()));
        endpoint.setResponseContentType(blankToNull(request.responseContentType()));
        endpoint.setRedirectLocation(blankToNull(request.redirectLocation()));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String writePipeline(JsonNode pipeline) {
        if (pipeline == null || pipeline.isNull()) {
            return "[]";
        }
        if (!pipeline.isArray() && !pipeline.isObject()) {
            throw ApiException.badRequest("Le pipeline doit être un tableau JSON d'étapes");
        }
        return writeJson(pipeline);
    }

    private String writeJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(node);
        } catch (Exception e) {
            throw ApiException.badRequest("JSON invalide");
        }
    }

    private JsonNode readJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException("JSON stocké illisible", e);
        }
    }

    private RunDtos.RunResponse runResponse(ExecutionService.ExecutionOutcome outcome) {
        var run = outcome.run();
        var endpoint = run.getEndpoint();
        var api = run.getApi();
        return new RunDtos.RunResponse(
                run.getId(),
                api == null ? null : api.getId(),
                api == null ? null : api.getCode(),
                api == null ? null : api.getName(),
                endpoint == null ? null : endpoint.getId(),
                endpoint == null ? null : endpoint.getName(),
                run.getStatus(),
                run.getDirection(),
                run.getTriggerType(),
                run.getCorrelationId(),
                run.getRequestSummary(),
                run.getResponseSummary(),
                run.getErrorMessage(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getDurationMs()
        );
    }
}
