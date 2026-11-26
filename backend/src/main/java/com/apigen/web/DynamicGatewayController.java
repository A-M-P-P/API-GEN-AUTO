package com.apigen.web;

import com.apigen.domain.ApiDefinition;
import com.apigen.domain.Endpoint;
import com.apigen.domain.InboundAuth;
import com.apigen.domain.Organization;
import com.apigen.repository.ApiDefinitionRepository;
import com.apigen.repository.EndpointRepository;
import com.apigen.repository.OrganizationRepository;
import com.apigen.security.ApiKeyService;
import com.apigen.security.JwtService;
import com.apigen.service.ExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Passerelle dynamique : expose les endpoints INBOUND configurés en base.
 * URL : /{code-organisation}/api/gateway/{code-api}{chemin-endpoint}
 */
@RestController
public class DynamicGatewayController {

    private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "x-api-key", "cookie", "proxy-authorization");
    /** Namespace des API non rattachées à une organisation. */
    private static final String UNCLASSIFIED_ORG_CODE = "default";

    private final ApiDefinitionRepository apiRepository;
    private final OrganizationRepository organizationRepository;
    private final EndpointRepository endpointRepository;
    private final ApiKeyService apiKeyService;
    private final JwtService jwtService;
    private final ExecutionService executionService;
    private final ObjectMapper objectMapper;

    public DynamicGatewayController(ApiDefinitionRepository apiRepository,
                                    OrganizationRepository organizationRepository,
                                    EndpointRepository endpointRepository,
                                    ApiKeyService apiKeyService,
                                    JwtService jwtService,
                                    ExecutionService executionService,
                                    ObjectMapper objectMapper) {
        this.apiRepository = apiRepository;
        this.organizationRepository = organizationRepository;
        this.endpointRepository = endpointRepository;
        this.apiKeyService = apiKeyService;
        this.jwtService = jwtService;
        this.executionService = executionService;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/{organizationCode}/api/gateway/{apiCode}/**")
    public ResponseEntity<Object> handle(@PathVariable String organizationCode,
                                         @PathVariable String apiCode,
                                         HttpServletRequest request,
                                         @RequestBody(required = false) String rawBody) {
        String method = request.getMethod().toUpperCase(Locale.ROOT);
        String path = normalizePath(extractEndpointPath(organizationCode, apiCode, request));

        Organization organization = organizationRepository.findByCodeIgnoreCase(organizationCode).orElse(null);
        if (organization == null && !UNCLASSIFIED_ORG_CODE.equalsIgnoreCase(organizationCode)) {
            return error(HttpStatus.NOT_FOUND, "Organisation introuvable : " + organizationCode);
        }

        ApiDefinition api = null;
        if (organization != null) {
            api = apiRepository.findByCodeIgnoreCaseAndOrganizationId(apiCode, organization.getId()).orElse(null);
        }
        if (api == null && (organization == null || UNCLASSIFIED_ORG_CODE.equalsIgnoreCase(organizationCode))) {
            // « default » désigne aussi les API non classées (rétrocompatibilité des données).
            api = apiRepository.findByCodeIgnoreCaseAndOrganizationIsNull(apiCode).orElse(null);
        }
        api = api == null || api.getStatus() != com.apigen.domain.ApiStatus.ACTIVE ? null : api;
        if (api == null) {
            return error(HttpStatus.NOT_FOUND,
                    "API inactive ou introuvable : " + organizationCode + "/" + apiCode);
        }

        EndpointMatch match = findEndpoint(api.getId(), method, path);
        if (match == null) {
            return error(HttpStatus.NOT_FOUND, "Endpoint introuvable : " + method + " " + path);
        }
        Endpoint endpoint = match.endpoint();
        if (!endpoint.isActive()) {
            return error(HttpStatus.NOT_FOUND, "Endpoint désactivé : " + method + " " + path);
        }

        ResponseEntity<Object> authFailure = checkInboundAuth(api, request);
        if (authFailure != null) {
            return authFailure;
        }

        Map<String, Object> requestContext = buildRequestContext(request, method, path, match.variables(), rawBody);
        ExecutionService.ExecutionOutcome outcome = executionService.inboundRun(endpoint, requestContext);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("runId", outcome.run().getId());
        response.put("correlationId", outcome.run().getCorrelationId());
        if (outcome.success()) {
            response.put("status", "SUCCESS");
            response.put("data", outcome.output());
            return ResponseEntity.ok(response);
        }
        response.put("status", "FAILED");
        response.put("error", outcome.run().getErrorMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(response);
    }

    private ResponseEntity<Object> checkInboundAuth(ApiDefinition api, HttpServletRequest request) {
        InboundAuth auth = api.getInboundAuth() == null ? InboundAuth.NONE : api.getInboundAuth();
        return switch (auth) {
            case NONE -> null;
            case API_KEY -> {
                String raw = request.getHeader("X-API-Key");
                if (raw != null && apiKeyService.matches(raw, api.getApiKeyHash())) {
                    yield null;
                }
                yield error(HttpStatus.UNAUTHORIZED, "Clé API absente ou invalide");
            }
            case JWT -> {
                String header = request.getHeader("Authorization");
                if (header == null || !header.startsWith("Bearer ")) {
                    yield error(HttpStatus.UNAUTHORIZED, "Token JWT requis");
                }
                try {
                    jwtService.extractUsername(header.substring(7).trim());
                    yield null;
                } catch (JwtException | IllegalArgumentException e) {
                    yield error(HttpStatus.UNAUTHORIZED, "Token JWT invalide ou expiré");
                }
            }
        };
    }

    private EndpointMatch findEndpoint(Long apiId, String method, String path) {
        Endpoint exact = endpointRepository.findByApiIdAndMethodIgnoreCaseAndPath(apiId, method, path).orElse(null);
        if (exact != null) {
            return new EndpointMatch(exact, Map.of());
        }
        for (Endpoint candidate : endpointRepository.findByApiIdAndActiveTrue(apiId)) {
            Map<String, String> variables = matchPath(candidate.getPath(), path);
            if (variables != null) {
                return new EndpointMatch(candidate, variables);
            }
        }
        return null;
    }

    private record EndpointMatch(Endpoint endpoint, Map<String, String> variables) {
    }

    private Map<String, String> matchPath(String template, String actual) {
        String[] templateParts = template.split("/");
        String[] actualParts = actual.split("/");
        if (templateParts.length != actualParts.length) {
            return null;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        for (int i = 0; i < templateParts.length; i++) {
            String expected = templateParts[i];
            if (expected.startsWith("{") && expected.endsWith("}")) {
                variables.put(expected.substring(1, expected.length() - 1), actualParts[i]);
            } else if (!expected.equals(actualParts[i])) {
                return null;
            }
        }
        return variables;
    }

    private Map<String, Object> buildRequestContext(HttpServletRequest request, String method, String path,
                                                     Map<String, String> pathVariables, String rawBody) {
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("method", method);
        context.put("path", path);

        Map<String, Object> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                query.put(key, values[0]);
            }
        });
        context.put("query", query);

        Map<String, String> headers = new LinkedHashMap<>();
        Collections.list(request.getHeaderNames()).forEach(name -> {
            if (!SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                headers.put(name, request.getHeader(name));
            }
        });
        context.put("headers", headers);
        context.put("remoteAddr", request.getRemoteAddr());
        context.put("pathVariables", pathVariables == null ? Map.of() : pathVariables);

        Object body = null;
        String contentType = request.getContentType();
        if (rawBody != null && !rawBody.isBlank()) {
            if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
                try {
                    body = objectMapper.readValue(rawBody, Object.class);
                } catch (Exception ignored) {
                    body = rawBody;
                }
            } else {
                body = rawBody;
            }
        }
        context.put("body", body);
        context.put("contentType", contentType);
        return context;
    }

    private String extractEndpointPath(String organizationCode, String apiCode, HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && uri.startsWith(contextPath)) {
            uri = uri.substring(contextPath.length());
        }
        String prefix = "/" + organizationCode + "/api/gateway/" + apiCode;
        int index = uri.indexOf(prefix);
        String path = index >= 0 ? uri.substring(index + prefix.length()) : "/";
        try {
            return URLDecoder.decode(path, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return path;
        }
    }

    private String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String normalized = path.startsWith("/") ? path : "/" + path;
        if (normalized.length() > 1 && normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private ResponseEntity<Object> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(body);
    }
}
