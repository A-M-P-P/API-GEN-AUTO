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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.user.username").value("admin"))
                .andReturn().getResponse().getContentAsString();

        String token = objectMapper.readTree(response).get("token").asText();

        mockMvc.perform(get("/api/dashboard").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apiCount").value(1))
                .andExpect(jsonPath("$.endpointCount").value(4));
    }

    @Test
    void gatewayExecutesDemoPipeline() throws Exception {
        mockMvc.perform(post("/default/api/gateway/demo/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"bonjour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.echo").value("BONJOUR"))
                .andExpect(jsonPath("$.data.receivedVia").value("POST"));

        mockMvc.perform(get("/default/api/gateway/demo/hello").param("name", "Amina"))
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Amina"));
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

        mockMvc.perform(get("/org-a/api/gateway/shared"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("A"));

        mockMvc.perform(get("/org-b/api/gateway/shared"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("B"));
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
                        .content("{\"username\":\"admin\",\"password\":\"ChangeMe123!\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("token").asText();
    }
}
