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
        mockMvc.perform(post("/api/gateway/default/demo/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"bonjour\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.data.echo").value("BONJOUR"))
                .andExpect(jsonPath("$.data.receivedVia").value("POST"));

        mockMvc.perform(get("/api/gateway/default/demo/hello").param("name", "Amina"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Bonjour Amina"));

        mockMvc.perform(get("/api/gateway/default/demo/unknown"))
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

        mockMvc.perform(get("/api/gateway/default/test-key/hello").param("name", "Amina"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/gateway/default/test-key/hello").param("name", "Amina")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greeting").value("Amina"));
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
