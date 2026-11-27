package com.apigen;

import com.apigen.domain.Endpoint;
import com.apigen.service.RequestParameterService;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class RequestParameterServiceTests {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RequestParameterService service = new RequestParameterService(mapper);
    private final String definition = """
        [{"name":"numero","location":"path","type":"text","required":true},
         {"name":"limite","location":"query","type":"number","defaultValue":"50"}]
        """;

    @Test void acceptsContractAndAppliesTypedDefault() throws Exception {
        service.validateDefinition("/contribuables/{numero}", mapper.readTree(definition));
        Endpoint endpoint = new Endpoint();
        endpoint.setRequestParameters(definition);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("pathVariables", new LinkedHashMap<>(Map.of("numero", "001")));
        context.put("query", new LinkedHashMap<>());
        service.validateRequest(endpoint, context);
        assertEquals("001", ((Map<?, ?>) context.get("pathVariables")).get("numero"));
        assertEquals(new java.math.BigDecimal("50"), ((Map<?, ?>) context.get("query")).get("limite"));
    }

    @Test void rejectsMissingIdentifierBeforeExecution() {
        Endpoint endpoint = new Endpoint();
        endpoint.setRequestParameters(definition);
        assertThrows(ApiException.class, () -> service.validateRequest(endpoint, new LinkedHashMap<>()));
    }

    @Test void rejectsInvalidNumbers() {
        Endpoint endpoint = new Endpoint();
        endpoint.setRequestParameters(definition);
        Map<String, Object> context = new LinkedHashMap<>();
        context.put("pathVariables", new LinkedHashMap<>(Map.of("numero", "1")));
        context.put("query", new LinkedHashMap<>(Map.of("limite", "pas un nombre")));
        assertThrows(ApiException.class, () -> service.validateRequest(endpoint, context));
    }

    @Test void rejectsUndeclaredPathAndDuplicates() throws Exception {
        var empty = mapper.readTree("[]");
        assertThrows(ApiException.class, () -> service.validateDefinition("/{numero}", empty));
        var duplicate = mapper.readTree("""
            [{"name":"ville","location":"query","type":"text"},
             {"name":"ville","location":"query","type":"text"}]
            """);
        assertThrows(ApiException.class, () -> service.validateDefinition("/", duplicate));
    }

    @Test void allowsLegacyEndpointsWithoutMetadata() {
        assertDoesNotThrow(() -> service.validateDefinition("/{numero}", null));
        assertDoesNotThrow(() -> service.validateRequest(new Endpoint(), new LinkedHashMap<>()));
    }
}
