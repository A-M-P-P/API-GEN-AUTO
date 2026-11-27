package com.apigen;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiGenApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
    }

    @Test
    void loginReturnsJwtAndDashboardIsProtected() throws Exception {
        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin2026!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.endpointCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(4)));
    }

    @Autowired private com.apigen.repository.OrganizationRepository organizations;
    @Autowired private com.apigen.repository.ApiDefinitionRepository apiDefinitions;
    @Autowired private com.apigen.service.ConsumerAuthService consumerAuth;

    private String consumerToken(String orgCode, String apiCode) {
        var org = organizations.findByCodeIgnoreCase(orgCode).orElseThrow();
        var api = apiDefinitions.findByCodeIgnoreCaseAndOrganizationId(apiCode, org.getId())
            .orElseGet(() -> {
                var unclassified = apiDefinitions.findByCodeIgnoreCaseAndOrganizationIsNull(apiCode).orElseThrow();
                unclassified.setOrganization(org);
                return apiDefinitions.save(unclassified);
            });
        var profile = consumerAuth.save(org.getId(), null,
            new com.apigen.service.ConsumerAuthService.ProfileInput("test-" + java.util.UUID.randomUUID(), "Test", true,
                "LOCAL", java.util.List.of(api.getId()), java.util.List.of()));
        var client = consumerAuth.createClient(profile.id, "test-client");
        return consumerAuth.login(orgCode, profile.code,
            java.util.Map.of("client_id", client.clientId(), "client_secret", client.secret()), "test").access_token();
    }

    @Test
    void gatewayExecutesDemoPipeline() throws Exception {
        mockMvc.perform(post("/default/api/gateway/demo/echo").header("Authorization", "Bearer " + consumerToken("default", "demo"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"bonjour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.echo").value("BONJOUR"))
                .andExpect(jsonPath("$.data.receivedVia").value("POST"));

        mockMvc.perform(get("/default/api/gateway/demo/hello").header("Authorization", "Bearer " + consumerToken("default", "demo")).param("name", "Amina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Bonjour Amina"));

        mockMvc.perform(get("/default/api/gateway/demo/unknown"))
                .andExpect(status().isNotFound());
    }

    @Test
    void apiKeyFlowProtectsInboundEndpoint() throws Exception {
        String token = loginToken();

        String apiResponse = mockMvc.perform(post("/api/apis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"test-key","name":"API test clé","status":"ACTIVE","inboundAuth":"API_KEY"}
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long apiId = objectMapper.readTree(apiResponse).get("id").asLong();

        mockMvc.perform(post("/api/apis/" + apiId + "/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Salutation",
                                  "method": "GET",
                                  "path": "/hello",
                                  "direction": "INBOUND",
                                  "pipeline": [
                                    {"type":"MAP","source":"request.query","mappings":[
                                      {"source":"name","target":"greeting","default":"world"}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated());

        String keyResponse = mockMvc.perform(post("/api/apis/" + apiId + "/api-key")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String apiKey = objectMapper.readTree(keyResponse).get("apiKey").asText();

        mockMvc.perform(get("/default/api/gateway/test-key/hello").param("name", "Amina"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/default/api/gateway/test-key/hello").param("name", "Amina")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void apiCodeIsScopedToOrganization() throws Exception {
        String token = loginToken();
        long orgA = createOrganization(token, "org-a");
        long orgB = createOrganization(token, "org-b");

        createApiWithSetStep(token, orgA, "shared", "A");
        createApiWithSetStep(token, orgB, "shared", "B");

        // Le même code est accepté dans une autre organisation, mais pas deux fois dans la même.
        mockMvc.perform(post("/api/apis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"shared","name":"Doublon","organizationId":%d}
                                """.formatted(orgA)))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/org-a/api/gateway/shared").header("Authorization", "Bearer " + consumerToken("org-a", "shared")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("A"));

        mockMvc.perform(get("/org-b/api/gateway/shared").header("Authorization", "Bearer " + consumerToken("org-b", "shared")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("B"));
    }

    @Test
    void textAndRedirectResponseFormats() throws Exception {
        String token = loginToken();
        long textApi = createApi(token, "fmt-text", null, null);
        createEndpoint(token, textApi, "GET", "TEXT", null,
                "[{\"type\":\"SET\",\"key\":\"value\",\"value\":\"bonjour\"}]");

        mockMvc.perform(get("/default/api/gateway/fmt-text").header("Authorization", "Bearer " + consumerToken("default", "fmt-text")))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(content().string("bonjour"))
                .andExpect(header().exists("X-Run-Id"));

        long redirectApi = createApi(token, "fmt-redirect", null, null);
        createEndpoint(token, redirectApi, "GET", "REDIRECT", null,
                "[{\"type\":\"SET\",\"key\":\"value\",\"value\":\"https://exemple.fr/page\"}]");

        mockMvc.perform(get("/default/api/gateway/fmt-redirect").header("Authorization", "Bearer " + consumerToken("default", "fmt-redirect")))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", startsWith("https://exemple.fr/")));
    }

    @Test
    void xmlInputIsParsedIntoRequestBody() throws Exception {
        String token = loginToken();
        long apiId = createApi(token, "fmt-xml", null, null);
        createEndpoint(token, apiId, "POST", "JSON", null, """
                [{"type":"MAP","mappings":[
                  {"source":"request.body.client.nom","target":"greeting"}
                ]}]
                """);

        mockMvc.perform(post("/default/api/gateway/fmt-xml").header("Authorization", "Bearer " + consumerToken("default", "fmt-xml"))
                        .contentType("application/xml")
                        .content("<client><nom>Amina</nom></client>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Amina"));
    }

    private long createApi(String token, String code, String responseFormat, String redirectLocation) throws Exception {
        String content = """
                {"code":"%s","name":"API %s","status":"ACTIVE"}
                """.formatted(code, code);
        if (responseFormat != null || redirectLocation != null) {
            content = content.replace("\"status\":\"ACTIVE\"",
                    "\"status\":\"ACTIVE\"" + (responseFormat == null ? "" : ",\"responseFormat\":\"" + responseFormat + "\"")
                            + (redirectLocation == null ? "" : ",\"redirectLocation\":\"" + redirectLocation + "\""));
        }
        String response = mockMvc.perform(post("/api/apis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createEndpoint(String token, long apiId, String method, String format, String fileName, String pipeline) throws Exception {
        String content = """
                {
                  "name": "Test",
                  "method": "%s",
                  "path": "/",
                  "direction": "INBOUND",
                  "responseFormat": "%s",
                  "pipeline": %s
                }
                """.formatted(method, format, pipeline);
        if (fileName != null) {
            content = content.replace("\"responseFormat\"", "\"responseFileName\":\"" + fileName + "\",\"responseFormat\"");
        }
        mockMvc.perform(post("/api/apis/" + apiId + "/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isCreated());
    }

    private long createOrganization(String token, String code) throws Exception {
        String response = mockMvc.perform(post("/api/organizations")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"Organisation %s"}
                                """.formatted(code, code)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void createApiWithSetStep(String token, long organizationId, String code, String value) throws Exception {
        String response = mockMvc.perform(post("/api/apis")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"%s","name":"API %s","status":"ACTIVE","organizationId":%d}
                                """.formatted(code, code, organizationId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long apiId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(post("/api/apis/" + apiId + "/endpoints")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Propriétaire",
                                  "method": "GET",
                                  "path": "/",
                                  "direction": "INBOUND",
                                  "pipeline": [{"type":"SET","key":"owner","value":"%s"}]
                                }
                                """.formatted(value)))
                .andExpect(status().isCreated());
    }

    private String loginToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"Admin2026!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("token").asText();
    }
}
