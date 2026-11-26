package com.apigen.service;

import com.apigen.domain.Endpoint;
import com.apigen.domain.ResponseFormat;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Rendu de la réponse d'un endpoint INBOUND selon le format configuré.
 *
 * <p>Le format JSON conserve l'enveloppe historique. Les autres formats renvoient le résultat
 * du pipeline directement, avec les identifiants d'exécution dans des en-têtes dédiés.</p>
 */
@Service
public class ResponseFormatService {

    private static final String HEADER_RUN_ID = "X-Run-Id";
    private static final String HEADER_CORRELATION_ID = "X-Correlation-Id";

    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public ResponseFormatService(ObjectMapper objectMapper, XmlMapper xmlMapper) {
        this.objectMapper = objectMapper;
        this.xmlMapper = xmlMapper;
    }

    public ResponseEntity<Object> render(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) {
        ResponseFormat format = endpoint.getResponseFormat() == null
                ? ResponseFormat.JSON : endpoint.getResponseFormat();
        if (format == ResponseFormat.JSON) {
            return ResponseEntity.status(outcome.success() ? HttpStatus.OK : HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(envelope(outcome));
        }
        if (!outcome.success()) {
            // Les erreurs restent dans un format machine lisible, quel que soit le format de sortie.
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(envelope(outcome));
        }
        try {
            return switch (format) {
                case TEXT -> text(endpoint, outcome);
                case XML -> xml(endpoint, outcome);
                case FILE -> file(endpoint, outcome);
                case BINARY -> binary(endpoint, outcome);
                case REDIRECT -> redirect(endpoint, outcome);
                case JSON -> throw new IllegalStateException("Cas JSON déjà traité");
            };
        } catch (Exception e) {
            Map<String, Object> error = envelope(outcome);
            error.put("error", "Rendu " + format + " impossible : "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(error);
        }
    }

    private Map<String, Object> envelope(ExecutionService.ExecutionOutcome outcome) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("runId", outcome.run().getId());
        body.put("correlationId", outcome.run().getCorrelationId());
        body.put("status", outcome.success() ? "SUCCESS" : "FAILED");
        if (outcome.success()) {
            body.put("data", outcome.output());
        } else {
            body.put("error", outcome.run().getErrorMessage());
        }
        return body;
    }

    private ResponseEntity<Object> text(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) throws Exception {
        String value = toText(outcome.output());
        MediaType type = explicitType(endpoint, MediaType.TEXT_PLAIN);
        return ResponseEntity.ok()
                .headers(headers(outcome))
                .contentType(type)
                .body(value);
    }

    private ResponseEntity<Object> xml(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) throws Exception {
        String value;
        if (outcome.output() instanceof String text && text.trim().startsWith("<")) {
            value = text;
        } else {
            value = xmlMapper.writer().withRootName("response").writeValueAsString(outcome.output());
        }
        MediaType type = explicitType(endpoint, MediaType.APPLICATION_XML);
        return ResponseEntity.ok()
                .headers(headers(outcome))
                .contentType(type)
                .body(value);
    }

    private ResponseEntity<Object> file(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) throws Exception {
        String path = outputValue(outcome.output(), "path");
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("La réponse FILE requiert une étape FILE_WRITE (champ path)");
        }
        Path file = Path.of(path).toAbsolutePath().normalize();
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("Fichier introuvable : " + file);
        }
        String filename = filename(endpoint, outcome, file.getFileName().toString());
        MediaType type = explicitType(endpoint, guessType(filename));
        return ResponseEntity.ok()
                .headers(headers(outcome))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + sanitize(filename) + "\"")
                .contentType(type)
                .body(Files.readAllBytes(file));
    }

    private ResponseEntity<Object> binary(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) throws Exception {
        String encoded = outputValue(outcome.output(), "base64", "content", "data", "bytes");
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalArgumentException("La réponse BINARY requiert une valeur base64 (champ base64, content ou data)");
        }
        byte[] bytes = Base64.getMimeDecoder().decode(encoded);
        MediaType type = explicitType(endpoint, MediaType.APPLICATION_OCTET_STREAM);
        ResponseEntity.BodyBuilder builder = ResponseEntity.ok().headers(headers(outcome)).contentType(type);
        String filename = endpoint.getResponseFileName();
        if (filename != null && !filename.isBlank()) {
            builder.header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + sanitize(substitute(filename, outcome)) + "\"");
        }
        return builder.body(bytes);
    }

    private ResponseEntity<Object> redirect(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome) throws Exception {
        String location = endpoint.getRedirectLocation() == null || endpoint.getRedirectLocation().isBlank()
                ? toText(outcome.output()) : endpoint.getRedirectLocation();
        location = substitute(location, outcome);
        if (location.isBlank()) {
            throw new IllegalArgumentException("La réponse REDIRECT requiert une URL (redirectLocation ou sortie du pipeline)");
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .headers(headers(outcome))
                .header(HttpHeaders.LOCATION, location)
                .body(null);
    }

    private org.springframework.http.HttpHeaders headers(ExecutionService.ExecutionOutcome outcome) {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(HEADER_RUN_ID, String.valueOf(outcome.run().getId()));
        headers.set(HEADER_CORRELATION_ID, String.valueOf(outcome.run().getCorrelationId()));
        return headers;
    }

    private MediaType explicitType(Endpoint endpoint, MediaType fallback) {
        if (endpoint.getResponseContentType() == null || endpoint.getResponseContentType().isBlank()) {
            return fallback;
        }
        try {
            return MediaType.parseMediaType(endpoint.getResponseContentType());
        } catch (Exception e) {
            return fallback;
        }
    }

    private MediaType guessType(String filename) {
        String name = filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".csv")) return MediaType.parseMediaType("text/csv");
        if (name.endsWith(".json")) return MediaType.APPLICATION_JSON;
        if (name.endsWith(".xml")) return MediaType.APPLICATION_XML;
        if (name.endsWith(".txt") || name.endsWith(".log")) return MediaType.TEXT_PLAIN;
        if (name.endsWith(".xlsx")) return MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        if (name.endsWith(".xls")) return MediaType.parseMediaType("application/vnd.ms-excel");
        if (name.endsWith(".pdf")) return MediaType.parseMediaType("application/pdf");
        if (name.endsWith(".png")) return MediaType.parseMediaType("image/png");
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return MediaType.parseMediaType("image/jpeg");
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String filename(Endpoint endpoint, ExecutionService.ExecutionOutcome outcome, String fallback) {
        if (endpoint.getResponseFileName() == null || endpoint.getResponseFileName().isBlank()) {
            return fallback;
        }
        return substitute(endpoint.getResponseFileName(), outcome);
    }

    private String substitute(String template, ExecutionService.ExecutionOutcome outcome) {
        return template.replace("{runId}", String.valueOf(outcome.run().getId()))
                .replace("{correlationId}", String.valueOf(outcome.run().getCorrelationId()));
    }

    private String sanitize(String filename) {
        return filename.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private String toText(Object value) throws Exception {
        if (value == null) {
            return "";
        }
        if (value instanceof String text) {
            return text;
        }
        return objectMapper.writeValueAsString(value);
    }

    @SuppressWarnings("unchecked")
    private String outputValue(Object output, String... keys) {
        if (output instanceof String text) {
            return text;
        }
        if (output instanceof Map<?, ?> map) {
            for (String key : keys) {
                Object value = map.get(key);
                if (value != null) {
                    if (value instanceof String text) {
                        return text;
                    }
                    return String.valueOf(value);
                }
            }
        }
        return null;
    }
}
