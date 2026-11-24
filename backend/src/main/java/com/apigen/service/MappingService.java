package com.apigen.service;

import com.apigen.execution.ExecutionContext;
import com.apigen.execution.TemplateEngine;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Étape MAP : transforme une liste ou un objet en appliquant des règles
 * source -> cible avec transformations optionnelles.
 */
@Service
public class MappingService {

    private final TemplateEngine templates;

    public MappingService(TemplateEngine templates) {
        this.templates = templates;
    }

    @SuppressWarnings("unchecked")
    public Object apply(Map<String, Object> step, ExecutionContext context) {
        String sourcePath = string(step.get("source"), "output");
        Object source = resolveSource(sourcePath, context);

        List<Map<String, Object>> mappings = (List<Map<String, Object>>) (List<?>) step.get("mappings");
        if (mappings == null || mappings.isEmpty()) {
            throw new IllegalArgumentException("L'étape MAP doit contenir au moins un mapping");
        }

        boolean merge = "MERGE".equalsIgnoreCase(string(step.get("mode"), "REPLACE"))
                || Boolean.TRUE.equals(step.get("merge"));

        if (source instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(mapItem(item, mappings, merge, context));
            }
            return result;
        }
        return mapItem(source, mappings, merge, context);
    }

    private Object resolveSource(String sourcePath, ExecutionContext context) {
        if (sourcePath == null || sourcePath.isBlank()) {
            return context.getOutput();
        }
        if (isAbsolute(sourcePath)) {
            return templates.getByPath(sourcePath, context);
        }
        Object value = templates.path(context.getOutput(), sourcePath);
        if (value == null && context.getOutput() instanceof Map<?, ?>) {
            value = templates.getByPath(sourcePath, context);
        }
        return value;
    }

    private boolean isAbsolute(String path) {
        return path.equals("output") || path.startsWith("request.") || path.startsWith("steps.")
                || path.startsWith("trigger") || path.startsWith("payload");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapItem(Object item, List<Map<String, Object>> mappings, boolean merge, ExecutionContext context) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (merge && item instanceof Map<?, ?> existing) {
            result.putAll((Map<String, Object>) existing);
        }
        for (Map<String, Object> rule : mappings) {
            String source = string(rule.get("source"), null);
            String target = string(rule.get("target"), null);
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Chaque mapping doit définir une cible (target)");
            }
            Object value;
            if (source == null || source.isBlank()) {
                value = null;
            } else if (isAbsolute(source)) {
                value = templates.getByPath(source, context);
            } else {
                value = templates.path(item, source);
            }
            if (value == null) {
                value = templates.resolve(rule.get("default"), context);
            }
            value = transform(value, rule.get("transform"), (Map<String, Object>) rule.get("params"));
            setValue(result, target, value);
        }
        return result;
    }

    private Object transform(Object value, Object transform, Map<String, Object> params) {
        if (transform == null || value == null) {
            return value;
        }
        String name = String.valueOf(transform).toUpperCase(Locale.ROOT);
        try {
            return switch (name) {
                case "UPPER" -> String.valueOf(value).toUpperCase(Locale.ROOT);
                case "LOWER" -> String.valueOf(value).toLowerCase(Locale.ROOT);
                case "TRIM" -> String.valueOf(value).trim();
                case "TO_STRING" -> String.valueOf(value);
                case "TO_INT" -> Integer.valueOf(String.valueOf(value).trim());
                case "TO_LONG" -> Long.valueOf(String.valueOf(value).trim());
                case "TO_DOUBLE" -> Double.valueOf(String.valueOf(value).trim());
                case "TO_BOOLEAN" -> Boolean.parseBoolean(String.valueOf(value).trim());
                case "CONCAT" -> {
                    String prefix = params == null ? "" : string(params.get("prefix"), "");
                    String suffix = params == null ? "" : string(params.get("suffix"), "");
                    yield prefix + value + suffix;
                }
                case "DATE_FORMAT" -> formatDate(value, params == null ? "yyyy-MM-dd" : string(params.get("pattern"), "yyyy-MM-dd"));
                default -> throw new IllegalArgumentException("Transformation inconnue : " + transform);
            };
        } catch (NumberFormatException | DateTimeParseException e) {
            throw new IllegalArgumentException("Transformation " + name + " impossible pour la valeur : " + value, e);
        }
    }

    private String formatDate(Object value, String pattern) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT).withZone(ZoneOffset.UTC);
        if (value instanceof Number number) {
            return formatter.format(Instant.ofEpochMilli(number.longValue()));
        }
        String text = String.valueOf(value).trim();
        try {
            return formatter.format(Instant.parse(text));
        } catch (DateTimeParseException ignored) {
            return formatter.format(LocalDateTime.parse(text).toInstant(ZoneOffset.UTC));
        }
    }

    @SuppressWarnings("unchecked")
    private void setValue(Map<String, Object> target, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> current = target;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = current.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                current.put(parts[i], next);
            }
            current = (Map<String, Object>) next;
        }
        current.put(parts[parts.length - 1], value);
    }

    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }
}
