package com.apigen;

import com.apigen.domain.*;
import com.apigen.execution.*;
import com.apigen.repository.*;
import com.apigen.service.ConsumerAuthService;
import com.apigen.web.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@ActiveProfiles("test")
class ConsumerAuthTests {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @Autowired ConsumerAuthService service;
    @Autowired OrganizationRepository organizations;
    @Autowired ApiDefinitionRepository apis;
    @Autowired ConsumerSessionRepository sessions;
    @Autowired ConsumerProfileRepository profiles;
    @MockBean StepExecutor executor;

    record Fixture(Organization org, ApiDefinition api, ConsumerProfile profile) {}
    private Fixture fixture(String mode) {
        String code = "auth-" + UUID.randomUUID();
        Organization org = new Organization(); org.setCode(code); org.setName(code); organizations.saveAndFlush(org);
        ApiDefinition api = new ApiDefinition(); api.setCode("data"); api.setName("Données"); api.setOrganization(org); api.setStatus(ApiStatus.ACTIVE); apis.saveAndFlush(api);
        Object pipeline = mode.equals("LOCAL") ? List.of() : List.of(Map.of("type", "HTTP", "url", "https://example.invalid/login"));
        ConsumerProfile profile = service.save(org.getId(), null, new ConsumerAuthService.ProfileInput("systeme", "Système", true, mode, List.of(api.getId()), pipeline));
        return new Fixture(org, api, profile);
    }
    private ConsumerAuthService.Tokens login(Fixture f) {
        var client = service.createClient(f.profile.id, "application");
        return service.login(f.org.getCode(), f.profile.code, Map.of("client_id", client.clientId(), "client_secret", client.secret()), "test");
    }
    @Test void localTokenIsHashedScopedAndImmediatelyRevocable() {
        Fixture f = fixture("LOCAL");
        var token = login(f);
        assertEquals("application", service.authorize(f.api, "Bearer " + token.access_token()));
        var stored = sessions.findByProfileId(f.profile.id).get(0);
        assertNotEquals(token.access_token(), stored.accessHash);
        assertNotEquals(token.refresh_token(), stored.refreshHash);
        assertTrue(token.expires_in() <= 86400 && token.expires_in() > 86000);
        assertThrows(ApiException.class, () -> service.authorize(fixture("LOCAL").api, "Bearer " + token.access_token()));
        service.revokeSession(f.profile.id, stored.id);
        assertThrows(ApiException.class, () -> service.authorize(f.api, "Bearer " + token.access_token()));
        assertThrows(ApiException.class, () -> service.refresh(f.org.getCode(), f.profile.code, token.refresh_token(), Map.of(), "test"));
    }
    @Test void refreshRotatesBothTokensAndRejectsReplay() {
        Fixture f = fixture("LOCAL"); var old = login(f);
        var next = service.refresh(f.org.getCode(), f.profile.code, old.refresh_token(), Map.of(), "test");
        assertNotEquals(old.access_token(), next.access_token());
        assertEquals("application", service.authorize(f.api, "Bearer " + next.access_token()));
        assertThrows(ApiException.class, () -> service.authorize(f.api, "Bearer " + old.access_token()));
        assertThrows(ApiException.class, () -> service.refresh(f.org.getCode(), f.profile.code, old.refresh_token(), Map.of(), "test"));
    }
    @Test void disabledClientCannotUseExistingTokens() {
        Fixture f = fixture("LOCAL"); var token = login(f);
        var client = service.clients(f.profile.id).get(0);
        service.disableClient(f.profile.id, client.id());
        assertThrows(ApiException.class, () -> service.authorize(f.api, "Bearer " + token.access_token()));
    }
    @Test void studioJwtAndMissingTokenCannotAuthorizeConsumer() {
        Fixture f = fixture("LOCAL");
        assertThrows(ApiException.class, () -> service.authorize(f.api, null));
        assertThrows(ApiException.class, () -> service.authorize(f.api, "Bearer eyJhbGciOiJIUzI1NiJ9.studio-account.not-consumer-token"));
    }
    @Test void expiredTokenCannotAuthorizeConsumer() {
        Fixture f = fixture("LOCAL"); var token = login(f);
        var s = sessions.findByProfileId(f.profile.id).get(0);
        s.accessExpires = java.time.Instant.now().minusSeconds(1); sessions.saveAndFlush(s);
        assertThrows(ApiException.class, () -> service.authorize(f.api, "Bearer " + token.access_token()));
    }
    @Test void pipelineMustProveIdentityAndRightsAndRecheckOnRefresh() {
        Fixture f = fixture("PIPELINE");
        when(executor.execute(anyMap(), any(ExecutionContext.class))).thenReturn(Map.of("authenticated", true, "subject", "agent-1", "apiIds", List.of(f.api.getId())));
        var token = service.login(f.org.getCode(), f.profile.code, Map.of("username", "agent-1", "password", "not-retained"), "test");
        assertEquals("agent-1", service.authorize(f.api, "Bearer " + token.access_token()));
        when(executor.execute(anyMap(), any(ExecutionContext.class))).thenReturn(Map.of("authenticated", false));
        assertThrows(ApiException.class, () -> service.refresh(f.org.getCode(), f.profile.code, token.refresh_token(), Map.of(), "test"));
        assertThrows(ApiException.class, () -> service.login(f.org.getCode(), f.profile.code, Map.of(), "test"));
    }
    @Test void externalRightsCannotEscapeAdministratorGrants() {
        Fixture f = fixture("PIPELINE"); Fixture other = fixture("LOCAL");
        when(executor.execute(anyMap(), any(ExecutionContext.class))).thenReturn(Map.of("authenticated", true, "subject", "agent", "apiIds", List.of(other.api.getId())));
        assertThrows(ApiException.class, () -> service.login(f.org.getCode(), f.profile.code, Map.of(), "test"));
    }
    @Test void publicTokenEndpointIsNotStudioLoginAndReturnsNoStore() throws Exception {
        Fixture f = fixture("LOCAL");
        var client = service.createClient(f.profile.id, "client-http");
        var body = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
            Map.of("client_id", client.clientId(), "client_secret", client.secret()));
        var response = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .post("/" + f.org.getCode() + "/api/auth/systeme/token")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(body))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.access_token").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        var raw = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).path("access_token").asText();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/dashboard")
            .header("Authorization", "Bearer " + raw))
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }
    @Test void defaultProfileIsDisabledUntilConfigured() {
        Fixture f = fixture("LOCAL");
        service.ensureDefault(f.org.getId());
        assertFalse(service.list(f.org.getId()).stream().filter(p -> p.code.equals("default")).findFirst().orElseThrow().active);
    }
}
