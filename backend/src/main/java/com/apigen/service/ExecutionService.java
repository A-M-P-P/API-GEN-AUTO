package com.apigen.service;

import com.apigen.domain.Endpoint;
import com.apigen.domain.ExchangeRun;
import com.apigen.domain.RunStatus;
import com.apigen.domain.TriggerType;
import com.apigen.execution.ExecutionContext;
import com.apigen.execution.ExecutionException;
import com.apigen.execution.StepExecutor;
import com.apigen.repository.ExchangeRunRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);
    private static final int MAX_SUMMARY = 8000;

    private final StepExecutor stepExecutor;
    private final ExchangeRunRepository runRepository;
    private final ObjectMapper objectMapper;

    public ExecutionService(StepExecutor stepExecutor,
                            ExchangeRunRepository runRepository,
                            ObjectMapper objectMapper) {
        this.stepExecutor = stepExecutor;
        this.runRepository = runRepository;
        this.objectMapper = objectMapper;
    }

    public record ExecutionOutcome(ExchangeRun run, Object output, Throwable error) {
        public boolean success() {
            return error == null;
        }
    }

    public ExecutionOutcome run(Endpoint endpoint, Map<String, Object> initial, TriggerType trigger, String correlationId) {
        Instant started = Instant.now();
        ExchangeRun run = new ExchangeRun();
        run.setApi(endpoint.getApi());
        run.setEndpoint(endpoint);
        run.setDirection(endpoint.getDirection());
        run.setTriggerType(trigger);
        run.setCorrelationId(correlationId);
        run.setStatus(RunStatus.RUNNING);
        run.setStartedAt(started);
        run.setRequestSummary(summarize(initial));
        run = runRepository.save(run);

        ExecutionContext context = new ExecutionContext();
        Map<String, Object> root = new LinkedHashMap<>();
        if (initial != null) {
            root.putAll(initial);
        }
        root.putIfAbsent("trigger", trigger.name());
        context.setInitial(root);

        Object output = null;
        Throwable failure = null;
        try {
            output = executePipeline(endpoint.getPipeline(), context);
        } catch (Exception e) {
            failure = e;
            log.warn("Exécution échouée pour l'endpoint {} ({}) : {}",
                    endpoint.getId(), endpoint.getName(), e.getMessage());
        }

        Instant finished = Instant.now();
        run.setFinishedAt(finished);
        run.setDurationMs(Duration.between(started, finished).toMillis());
        run.setResponseSummary(summarize(output));
        if (failure == null) {
            run.setStatus(RunStatus.SUCCESS);
        } else {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(truncate(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage(), 4000));
        }
        runRepository.save(run);
        return new ExecutionOutcome(run, output, failure);
    }

    public ExecutionOutcome manualRun(Endpoint endpoint, JsonNode payload) {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("trigger", TriggerType.MANUAL.name());
        initial.put("payload", payload == null || payload.isNull()
                ? new LinkedHashMap<String, Object>()
                : objectMapper.convertValue(payload, new TypeReference<Object>() {
        }));
        return run(endpoint, initial, TriggerType.MANUAL, shortId());
    }

    public ExecutionOutcome scheduledRun(Endpoint endpoint, JsonNode payload) {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("trigger", TriggerType.SCHEDULE.name());
        initial.put("payload", payload == null || payload.isNull()
                ? new LinkedHashMap<String, Object>()
                : objectMapper.convertValue(payload, new TypeReference<Object>() {
        }));
        return run(endpoint, initial, TriggerType.SCHEDULE, shortId());
    }

    public ExecutionOutcome inboundRun(Endpoint endpoint, Map<String, Object> request) {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("trigger", TriggerType.INBOUND.name());
        initial.put("request", request);
        return run(endpoint, initial, TriggerType.INBOUND, shortId());
    }

    public Object executePipeline(String pipelineJson, ExecutionContext context) {
        List<Map<String, Object>> steps = parseSteps(pipelineJson);
        for (int i = 0; i < steps.size(); i++) {
            Map<String, Object> step = steps.get(i);
            String name = step.get("name") == null ? String.valueOf(i) : String.valueOf(step.get("name"));
            try {
                Object result = stepExecutor.execute(step, context);
                context.recordStep(name, result);
            } catch (Exception e) {
                String message = "Étape " + (i + 1) + " (« " + name + " ») : " + e.getMessage();
                throw new ExecutionException(i, name, message, e);
            }
        }
        return context.getOutput();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseSteps(String pipelineJson) {
        if (pipelineJson == null || pipelineJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode node = objectMapper.readTree(pipelineJson);
            List<Map<String, Object>> steps = new ArrayList<>();
            if (node.isArray()) {
                for (JsonNode item : node) {
                    steps.add(objectMapper.convertValue(item, new TypeReference<Map<String, Object>>() {
                    }));
                }
            } else if (node.isObject()) {
                steps.add(objectMapper.convertValue(node, new TypeReference<Map<String, Object>>() {
                }));
            } else {
                throw new IllegalArgumentException("Le pipeline doit être un tableau JSON d'étapes");
            }
            return steps;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Pipeline JSON illisible : " + e.getMessage(), e);
        }
    }

    private String summarize(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return truncate(objectMapper.writeValueAsString(value), MAX_SUMMARY);
        } catch (Exception e) {
            return truncate(String.valueOf(value), MAX_SUMMARY);
        }
    }

    private String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max) + "...";
    }

    private String shortId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
