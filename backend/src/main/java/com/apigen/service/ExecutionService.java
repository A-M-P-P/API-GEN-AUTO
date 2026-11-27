package com.apigen.service;

import com.apigen.domain.Endpoint;
import com.apigen.domain.ExchangeRun;
import com.apigen.domain.RunStatus;
import com.apigen.domain.TriggerType;
import com.apigen.execution.ExecutionContext;
import com.apigen.execution.ExecutionException;
import com.apigen.execution.StepExecutor;
import com.apigen.repository.ExchangeRunRepository;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Locale;
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
    private final OrganizationContextService organizationContextService;
    private final ObjectMapper objectMapper;
    private final RequestParameterService requestParameters;

    public ExecutionService(StepExecutor stepExecutor,
                            ExchangeRunRepository runRepository,
                            OrganizationContextService organizationContextService,
                            ObjectMapper objectMapper, RequestParameterService requestParameters) {
        this.stepExecutor = stepExecutor;
        this.runRepository = runRepository;
        this.organizationContextService = organizationContextService;
        this.objectMapper = objectMapper;
        this.requestParameters = requestParameters;
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
            // Tout ce qui peut échouer fait partie de l'exécution : le studio doit toujours
            // recevoir un résultat lisible, jamais un HTTP 500 sans explication.
            run = runRepository.save(run);
            // Chaque organisation fournit son propre contexte (variables, secrets, surcharges).
            context.setRootValue("org", organizationContextService.forEndpoint(endpoint.getId()).asRoot());
            output = executePipeline(endpoint.getPipeline(), context);
        } catch (Exception e) {
            failure = e;
            log.warn("Exécution échouée pour l'endpoint {} ({})", endpoint.getId(), endpoint.getName(), e);
        }

        Instant finished = Instant.now();
        run.setFinishedAt(finished);
        run.setDurationMs(Duration.between(started, finished).toMillis());
        run.setResponseSummary(summarize(output));
        if (failure == null) {
            run.setStatus(RunStatus.SUCCESS);
        } else {
            run.setStatus(RunStatus.FAILED);
            run.setErrorMessage(friendlyMessage(failure));
        }
        try {
            runRepository.save(run);
        } catch (Exception e) {
            // Même si l'historique n'est pas disponible, on renvoie le résultat à l'utilisateur.
            log.warn("Impossible d'enregistrer l'historique de l'exécution {}", correlationId, e);
        }
        return new ExecutionOutcome(run, output, failure);
    }

    public ExecutionOutcome manualRun(Endpoint endpoint, JsonNode payload) {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("trigger", TriggerType.MANUAL.name());
        Object body = readPayload(payload);
        initial.put("payload", body);
        // Un test depuis le studio doit se comporter comme un appel réel : les pipelines
        // INBOUND lisent request.body / request.query, et pas seulement payload.
        initial.put("request", simulatedRequest(endpoint, body));
        return run(endpoint, initial, TriggerType.MANUAL, shortId());
    }

    public ExecutionOutcome scheduledRun(Endpoint endpoint, JsonNode payload) {
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("trigger", TriggerType.SCHEDULE.name());
        Object body = readPayload(payload);
        initial.put("payload", body);
        initial.put("request", simulatedRequest(endpoint, body));
        return run(endpoint, initial, TriggerType.SCHEDULE, shortId());
    }

    private Object readPayload(JsonNode payload) {
        return payload == null || payload.isNull()
                ? new LinkedHashMap<String, Object>()
                : objectMapper.convertValue(payload, new TypeReference<Object>() {
        });
    }

    /** Construit un contexte {@code request} cohérent pour les tests sans serveur HTTP. */
    private Map<String, Object> simulatedRequest(Endpoint endpoint, Object body) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("method", endpoint.getMethod());
        request.put("path", endpoint.getPath());
        request.put("headers", Map.of());
        if (body instanceof Map<?, ?> map) {
            Object query = map.get("query");
            request.put("query", query instanceof Map<?, ?> values ? new LinkedHashMap<>(values) : new LinkedHashMap<>());
            Object variables = map.get("pathVariables");
            request.put("pathVariables", variables instanceof Map<?, ?> values ? new LinkedHashMap<>(values) : new LinkedHashMap<>());
            Object nestedBody = map.get("body");
            request.put("body", nestedBody == null ? body : nestedBody);
        } else {
            request.put("query", new LinkedHashMap<>());
            request.put("pathVariables", new LinkedHashMap<>());
            request.put("body", body);
        }
        requestParameters.validateRequest(endpoint, request);
        return request;
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

    /**
     * Message destiné à un utilisateur non technique : on garde le libellé métier quand il
     * existe, on traduit les erreurs techniques en phrase d'action et on laisse le détail
     * court entre parenthèses. La pile complète reste dans les logs serveur.
     */
    public String friendlyMessage(Throwable failure) {
        if (failure == null) {
            return null;
        }
        if (failure instanceof ApiException) {
            return truncate(failure.getMessage(), 4000);
        }
        String detail = failure.getMessage() == null
                ? failure.getClass().getSimpleName() : failure.getMessage();
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        String rootDetail = root.getMessage() == null ? root.getClass().getSimpleName() : root.getMessage();
        String lower = (detail + " " + rootDetail).toLowerCase(Locale.ROOT);

        String headline;
        if (root instanceof SQLException || lower.contains("ora-") || lower.contains("jdbc")) {
            headline = "La base de données n'a pas pu être utilisée. Vérifiez la connexion choisie dans l'endpoint.";
        } else if (lower.contains("unknownhost") || lower.contains("connect") || lower.contains("timeout")
                || lower.contains("timed out") || lower.contains("econnrefused")) {
            headline = "Le service distant n'a pas répondu. Vérifiez l'URL et réessayez.";
        } else if (lower.contains("type d'étape inconnu") || lower.contains("pipeline json illisible")) {
            headline = "Le pipeline de l'endpoint contient une étape ou un JSON non reconnu.";
        } else {
            headline = "L'exécution n'a pas pu aboutir. Vérifiez la configuration de l'endpoint et réessayez.";
        }
        return truncate(headline + " (détail : " + detail + ")", 4000);
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
