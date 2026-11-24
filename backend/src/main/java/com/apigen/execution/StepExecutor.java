package com.apigen.execution;

import com.apigen.service.MappingService;
import com.apigen.service.connector.FileConnector;
import com.apigen.service.connector.JdbcConnector;
import com.apigen.service.connector.RestConnector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Dispatcheur d'étapes du pipeline.
 */
@Component
public class StepExecutor {

    private static final Logger log = LoggerFactory.getLogger(StepExecutor.class);

    private final RestConnector restConnector;
    private final JdbcConnector jdbcConnector;
    private final FileConnector fileConnector;
    private final MappingService mappingService;
    private final TemplateEngine templates;

    public StepExecutor(RestConnector restConnector,
                        JdbcConnector jdbcConnector,
                        FileConnector fileConnector,
                        MappingService mappingService,
                        TemplateEngine templates) {
        this.restConnector = restConnector;
        this.jdbcConnector = jdbcConnector;
        this.fileConnector = fileConnector;
        this.mappingService = mappingService;
        this.templates = templates;
    }

    @SuppressWarnings("unchecked")
    public Object execute(Map<String, Object> step, ExecutionContext context) {
        String type = String.valueOf(step.getOrDefault("type", "MAP")).trim().toUpperCase(Locale.ROOT);
        return switch (type) {
            case "HTTP", "REST" -> restConnector.execute(step, context);
            case "DB_QUERY", "SQL_QUERY" -> jdbcConnector.query(step, context);
            case "DB_EXEC", "DB_UPDATE", "SQL_EXEC" -> jdbcConnector.update(step, context);
            case "FILE_READ" -> fileConnector.read(step, context);
            case "FILE_WRITE" -> fileConnector.write(step, context);
            case "MAP", "TRANSFORM" -> mappingService.apply(step, context);
            case "SET" -> {
                String key = String.valueOf(step.get("key"));
                Object value = templates.resolve(step.get("value"), context);
                Map<String, Object> vars = (Map<String, Object>) context.getRoot()
                        .computeIfAbsent("vars", ignored -> new LinkedHashMap<String, Object>());
                vars.put(key, value);
                yield value;
            }
            case "LOG" -> {
                String message = templates.resolveString(
                        step.get("message") == null ? "Execution" : String.valueOf(step.get("message")), context);
                log.info("[pipeline] {}", message);
                yield context.getOutput();
            }
            default -> throw new IllegalArgumentException("Type d'étape inconnu : " + type);
        };
    }
}
