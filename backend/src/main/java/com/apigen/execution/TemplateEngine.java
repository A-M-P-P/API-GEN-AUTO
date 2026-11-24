package com.apigen.execution;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Résolution de templates {{chemin.dotted}} dans les chaînes et les objets.
 */
@Component
public class TemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]+?)\\s*\\}\\}");

    private final ObjectMapper objectMapper;

    public TemplateEngine(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object resolve(Object value, ExecutionContext context) {
        if (value == null) {
            return null;
        }
        if (value instanceof String text) {
            return resolveText(text, context);
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), resolve(entry.getValue(), context));
            }
            return result;
        }
        if (value instanceof List<?> list) {
            return list.stream().map(item -> resolve(item, context)).toList();
        }
        return value;
    }

    public String resolveString(String template, ExecutionContext context) {
        Object resolved = resolveText(template, context);
        return stringify(resolved);
    }

    private Object resolveText(String text, ExecutionContext context) {
        Matcher matcher = PLACEHOLDER.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        if (matcher.start() == 0 && matcher.end() == text.length()) {
            return getByPath(matcher.group(1).trim(), context);
        }
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            Object value = getByPath(matcher.group(1).trim(), context);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(stringify(value)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    /**
     * Chemin absolu : request.body.x, steps.0.output, output, trigger, payload.
     */
    public Object getByPath(String path, ExecutionContext context) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if ("output".equals(path)) {
            return context.getOutput();
        }
        return path(context.getRoot(), path);
    }

    /**
     * Chemin relatif à une source (Map ou List).
     */
    @SuppressWarnings("unchecked")
    public Object path(Object source, String path) {
        if (source == null || path == null || path.isBlank()) {
            return source;
        }
        Object current = source;
        for (String part : path.split("\\.")) {
            if (current == null) {
                return null;
            }
            if (current instanceof Map<?, ?> map) {
                current = map.get(part);
            } else if (current instanceof List<?> list) {
                try {
                    current = list.get(Integer.parseInt(part));
                } catch (NumberFormatException | IndexOutOfBoundsException e) {
                    return null;
                }
            } else {
                return null;
            }
        }
        return current;
    }

    public String stringify(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String s) {
            return s;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value);
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> asMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return null;
    }

    public List<Object> asList(Object value) {
        if (value instanceof List<?> list) {
            return new java.util.ArrayList<>(list);
        }
        return null;
    }
}
