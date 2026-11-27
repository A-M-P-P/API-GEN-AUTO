package com.apigen.service;

import com.apigen.domain.Endpoint;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Shared contract for guided request parameters; never interpolates values into SQL. */
@Service
public class RequestParameterService {
    private static final Pattern NAME = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern VARIABLE = Pattern.compile("\\{([A-Za-z_][A-Za-z0-9_]*)}");
    private final ObjectMapper mapper;

    public RequestParameterService(ObjectMapper mapper) { this.mapper = mapper; }

    public void validateDefinition(String path, JsonNode definitions) {
        Set<String> variables = new HashSet<>();
        var matcher = VARIABLE.matcher(path);
        while (matcher.find()) {
            if (!variables.add(matcher.group(1))) fail("Un identifiant apparaît plusieurs fois dans le chemin.");
        }
        if (VARIABLE.matcher(path).replaceAll("").matches(".*[{}].*")) fail("Les accolades du chemin sont incorrectes.");
        // Existing endpoints without metadata remain compatible.
        if (definitions == null || definitions.isNull()) return;
        if (!definitions.isArray() || definitions.size() > 100) fail("Ajoutez au maximum 100 paramètres dans le formulaire.");
        Set<String> seen = new HashSet<>();
        Set<String> declaredPath = new HashSet<>();
        for (JsonNode p : definitions) {
            String name = p.path("name").asText();
            String location = p.path("location").asText();
            if (!NAME.matcher(name).matches()) fail("Nom de paramètre incorrect : utilisez des lettres, chiffres et _, sans espace.");
            if (!Set.of("path", "query").contains(location)) fail("Choisissez identifiant ou filtre pour « " + name + " ».");
            if (!seen.add(location + ":" + name)) fail("Le paramètre « " + name + " » est déclaré deux fois.");
            if (!Set.of("text", "number", "date", "boolean").contains(p.path("type").asText())) fail("Choisissez un type pour « " + name + " ».");
            if (location.equals("path")) {
                declaredPath.add(name);
                if (!variables.contains(name)) fail("Ajoutez {" + name + "} au chemin ou supprimez cet identifiant.");
                if (p.hasNonNull("defaultValue") && !p.path("defaultValue").asText().isBlank()) fail("Un identifiant du chemin ne peut pas avoir de valeur par défaut.");
            }
            if (p.hasNonNull("defaultValue") && !p.path("defaultValue").asText().isBlank()) convert(p, p.path("defaultValue").asText());
        }
        if (!declaredPath.equals(variables)) fail("Détectez et configurez tous les identifiants présents dans le chemin.");
    }

    @SuppressWarnings("unchecked")
    public void validateRequest(Endpoint endpoint, Map<String, Object> context) {
        if (endpoint.getRequestParameters() == null) return;
        JsonNode definitions;
        try { definitions = mapper.readTree(endpoint.getRequestParameters()); }
        catch (Exception e) { throw new IllegalStateException("Configuration des paramètres illisible", e); }
        if (definitions == null || definitions.isNull()) return;
        for (JsonNode p : definitions) {
            String name = p.path("name").asText();
            boolean path = p.path("location").asText().equals("path");
            String key = path ? "pathVariables" : "query";
            Map<String, Object> values = (Map<String, Object>) context.get(key);
            if (values == null) { values = new LinkedHashMap<>(); context.put(key, values); }
            Object raw = values.get(name);
            if (raw == null || raw.toString().isBlank()) {
                String fallback = p.path("defaultValue").asText("");
                if (!path && !fallback.isBlank()) raw = fallback;
                else {
                    if (path || p.path("required").asBoolean()) fail("Renseignez « " + label(p) + " » puis réessayez.");
                    continue;
                }
            }
            values.put(name, convert(p, raw.toString()));
        }
    }

    private Object convert(JsonNode p, String value) {
        try {
            return switch (p.path("type").asText()) {
                case "number" -> new BigDecimal(value);
                case "date" -> LocalDate.parse(value).toString();
                case "boolean" -> {
                    if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) throw new IllegalArgumentException();
                    yield Boolean.parseBoolean(value);
                }
                default -> value;
            };
        } catch (IllegalArgumentException | java.time.format.DateTimeParseException e) {
            fail("Vérifiez « " + label(p) + " » : valeur attendue de type " + switch (p.path("type").asText()) {
                case "number" -> "nombre";
                case "date" -> "date (AAAA-MM-JJ)";
                case "boolean" -> "oui/non (true ou false)";
                default -> "texte";
            } + ".");
            return null;
        }
    }
    private String label(JsonNode p) { return p.path("label").asText("").isBlank() ? p.path("name").asText() : p.path("label").asText(); }
    private void fail(String message) { throw ApiException.badRequest(message); }
}
