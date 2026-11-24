package com.apigen.service.connector;

import com.apigen.domain.ConnectionType;
import com.apigen.execution.ExecutionContext;
import com.apigen.execution.TemplateEngine;
import com.apigen.service.ConnectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Connecteur HTTP sortant. Supporte les authentifications BASIC, BEARER et API_KEY.
 */
@Component
public class RestConnector {

    private final ConnectionService connectionService;
    private final TemplateEngine templates;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;

    public RestConnector(ConnectionService connectionService,
                         TemplateEngine templates,
                         ObjectMapper objectMapper) {
        this.connectionService = connectionService;
        this.templates = templates;
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(15));
        factory.setReadTimeout(Duration.ofSeconds(60));
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(Map<String, Object> step, ExecutionContext context) {
        String method = string(step.get("method"), "GET").toUpperCase(Locale.ROOT);
        String url = templates.resolveString(string(step.get("url"), ""), context);
        int timeoutMs = integer(step.get("timeoutMs"), 60000);

        ConnectionService.ConnectionSettings settings = resolveConnection(step, context);
        if (settings != null && url != null && !url.isBlank() && !url.startsWith("http")) {
            String baseUrl = text(settings.baseConfig(), "baseUrl", "");
            url = stripTrailing(baseUrl) + "/" + stripLeading(url);
        }
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("L'étape HTTP doit définir une URL ou une connexion avec baseUrl");
        }

        Map<String, Object> headers = new LinkedHashMap<>();
        Map<String, Object> stepHeaders = (Map<String, Object>) step.get("headers");
        if (stepHeaders != null) {
            stepHeaders.forEach((key, value) -> headers.put(key, templates.resolveString(String.valueOf(value), context)));
        }
        if (settings != null) {
            applyAuth(headers, settings);
        }

        Object rawBody = step.containsKey("body") ? step.get("body") : step.get("bodyTemplate");
        Object body = templates.resolve(rawBody, context);
        if (body != null && !headers.containsKey("Content-Type")) {
            headers.put("Content-Type", (body instanceof Map || body instanceof List)
                    ? MediaType.APPLICATION_JSON_VALUE : MediaType.TEXT_PLAIN_VALUE);
        }

        String finalUrl = appendQuery(url, step.get("queryParams"), context);
        HttpHeaders httpHeaders = new HttpHeaders();
        headers.forEach((key, value) -> {
            if (value != null) {
                httpHeaders.add(key, String.valueOf(value));
            }
        });

        Object bodyValue = serializeBody(body);
        long start = System.nanoTime();

        Map<String, Object> result;
        try {
            RestClient.RequestBodySpec requestSpec = restClient
                    .method(HttpMethod.valueOf(method))
                    .uri(URI.create(finalUrl));
            requestSpec.headers(h -> h.addAll(httpHeaders));
            if (bodyValue != null) {
                requestSpec.body(bodyValue);
            }
            result = requestSpec.exchange((clientRequest, response) -> {
                        int status = response.getStatusCode().value();
                        byte[] bytes = response.getBody().readAllBytes();
                        String text = new String(bytes, StandardCharsets.UTF_8);
                        String contentType = response.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE);
                        Object parsed = parseBody(text, contentType);
                        Map<String, Object> responseHeaders = new LinkedHashMap<>();
                        response.getHeaders().forEach((name, values) -> {
                            if (values != null && !values.isEmpty()) {
                                responseHeaders.put(name, values.get(0));
                            }
                        });
                        Map<String, Object> res = new LinkedHashMap<>();
                        res.put("status", status);
                        res.put("ok", status >= 200 && status < 300);
                        res.put("headers", responseHeaders);
                        res.put("body", parsed);
                        return res;
                    }, true);
            result.put("durationMs", (System.nanoTime() - start) / 1_000_000);
            result.put("url", finalUrl);
            result.put("method", method);
        } catch (Exception e) {
            throw new IllegalStateException("Appel HTTP " + method + " " + finalUrl + " échoué : " + rootMessage(e), e);
        }

        Integer status = (Integer) result.get("status");
        boolean failOnError = !Boolean.FALSE.equals(step.get("failOnError"));
        if (failOnError && (status == null || status >= 400)) {
            throw new IllegalStateException("Le système distant a répondu " + status + " pour " + method + " " + finalUrl);
        }
        return result;
    }

    private ConnectionService.ConnectionSettings resolveConnection(Map<String, Object> step, ExecutionContext context) {
        Object rawId = templates.resolve(step.get("connectionId"), context);
        if (rawId == null || String.valueOf(rawId).isBlank()) {
            return null;
        }
        Long id = rawId instanceof Number number ? number.longValue() : Long.valueOf(String.valueOf(rawId));
        ConnectionService.ConnectionSettings settings = connectionService.settings(id);
        if (settings.connection().getType() != ConnectionType.REST) {
            throw new IllegalArgumentException("La connexion " + id + " n'est pas de type REST");
        }
        return settings;
    }

    private void applyAuth(Map<String, Object> headers, ConnectionService.ConnectionSettings settings) {
        String authType = text(settings.baseConfig(), "authType", "NONE").toUpperCase(Locale.ROOT);
        Map<String, Object> credentials = settings.credentials();
        switch (authType) {
            case "BASIC" -> {
                String user = value(credentials, "username");
                String password = value(credentials, "password");
                String token = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + token);
            }
            case "BEARER" -> headers.put("Authorization", "Bearer " + value(credentials, "token"));
            case "API_KEY" -> {
                String headerName = text(settings.baseConfig(), "apiKeyHeader", "X-API-Key");
                String prefix = text(settings.baseConfig(), "apiKeyPrefix", "");
                headers.put(headerName, prefix + value(credentials, "apiKey"));
            }
            default -> {
                // Pas d'authentification.
            }
        }
    }

    private Object parseBody(String text, String contentType) {
        if (text == null || text.isBlank()) {
            return null;
        }
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            try {
                JsonNode node = objectMapper.readTree(text);
                return objectMapper.treeToValue(node, Object.class);
            } catch (Exception ignored) {
                return text;
            }
        }
        return text;
    }

    private Object serializeBody(Object body) {
        if (body == null) {
            return null;
        }
        if (body instanceof String s) {
            return s;
        }
        if (body instanceof Map || body instanceof List) {
            try {
                return objectMapper.writeValueAsString(body);
            } catch (Exception e) {
                throw new IllegalArgumentException("Corps de requête non sérialisable", e);
            }
        }
        return String.valueOf(body);
    }

    @SuppressWarnings("unchecked")
    private String appendQuery(String url, Object queryParams, ExecutionContext context) {
        if (queryParams == null) {
            return url;
        }
        Object resolved = templates.resolve(queryParams, context);
        if (!(resolved instanceof Map<?, ?> map) || map.isEmpty()) {
            return url;
        }
        StringBuilder sb = new StringBuilder(url);
        String separator = url.contains("?") ? "&" : "?";
        for (Map.Entry<?, ?> entry : ((Map<String, Object>) map).entrySet()) {
            if (entry.getValue() == null) {
                continue;
            }
            sb.append(separator)
                    .append(encode(String.valueOf(entry.getKey())))
                    .append('=')
                    .append(encode(String.valueOf(entry.getValue())));
            separator = "&";
        }
        return sb.toString();
    }

    private String encode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String text(JsonNode node, String field, String defaultValue) {
        return node == null || !node.hasNonNull(field) ? defaultValue : node.get(field).asText();
    }

    private String value(Map<String, Object> credentials, String key) {
        Object value = credentials == null ? null : credentials.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String stripTrailing(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }

    private String stripLeading(String value) {
        return value == null ? "" : value.replaceFirst("^/+", "");
    }

    private String string(Object value, String defaultValue) {
        return value == null || String.valueOf(value).isBlank() ? defaultValue : String.valueOf(value);
    }

    private int integer(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
