package com.apigen.web;

import com.apigen.domain.ConsumerProfile;
import com.apigen.service.ConsumerAuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
public class ConsumerAuthController {
    private final ConsumerAuthService service;
    private final com.apigen.service.AuditService audit;
    public ConsumerAuthController(ConsumerAuthService service, com.apigen.service.AuditService audit) { this.service = service; this.audit = audit; }

    @PostMapping("/{org}/api/auth/{profile}/token")
    public ResponseEntity<?> token(@PathVariable String org, @PathVariable String profile,
                                  @RequestBody Map<String, Object> body, HttpServletRequest request) {
        try { return confidential(service.login(org, profile, body, request.getRemoteAddr())); }
        catch (ApiException e) {
            audit.record("consumer", "CONSUMER_LOGIN_DENIED", "ConsumerProfile", null, "Tentative de connexion refusée");
            throw e;
        }
    }
    @PostMapping("/{org}/api/auth/{profile}/refresh")
    public ResponseEntity<?> refresh(@PathVariable String org, @PathVariable String profile,
                                    @RequestBody Map<String, Object> body, HttpServletRequest request) {
        @SuppressWarnings("unchecked") Map<String, Object> credentials = body.get("credentials") instanceof Map<?, ?> m
            ? (Map<String, Object>) m : Map.of();
        try { return confidential(service.refresh(org, profile, Objects.toString(body.get("refresh_token"), ""), credentials, request.getRemoteAddr())); }
        catch (ApiException e) {
            audit.record("consumer", "CONSUMER_REFRESH_DENIED", "ConsumerProfile", null, "Renouvellement refusé");
            throw e;
        }
    }
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<?> failure(ApiException e) {
        return ResponseEntity.status(e.getStatus()).cacheControl(CacheControl.noStore())
            .header("Pragma", "no-cache").body(Map.of("message", e.getMessage()));
    }
    private ResponseEntity<?> confidential(Object value) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("Pragma", "no-cache").body(value);
    }

    @GetMapping("/api/organizations/{orgId}/auth-profiles")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<ConsumerProfile> list(@PathVariable Long orgId) { return service.list(orgId); }

    @PostMapping("/api/organizations/{orgId}/auth-profiles")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ConsumerProfile create(@PathVariable Long orgId, @RequestBody ConsumerAuthService.ProfileInput body) {
        return service.save(orgId, null, body);
    }
    @PutMapping("/api/organizations/{orgId}/auth-profiles/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ConsumerProfile update(@PathVariable Long orgId, @PathVariable Long id, @RequestBody ConsumerAuthService.ProfileInput body) {
        return service.save(orgId, id, body);
    }
    @GetMapping("/api/consumer-profiles/{id}/clients")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<ConsumerAuthService.ClientView> clients(@PathVariable Long id) { return service.clients(id); }
    @PostMapping("/api/consumer-profiles/{id}/clients")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public ResponseEntity<?> client(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return confidential(service.createClient(id, body.get("clientId")));
    }
    @DeleteMapping("/api/consumer-profiles/{id}/clients/{clientId}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void disable(@PathVariable Long id, @PathVariable Long clientId) { service.disableClient(id, clientId); }
    @GetMapping("/api/consumer-profiles/{id}/sessions")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public List<ConsumerAuthService.SessionView> sessions(@PathVariable Long id) { return service.sessionList(id); }
    @DeleteMapping("/api/consumer-profiles/{id}/sessions/{sessionId}")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void revokeSession(@PathVariable Long id, @PathVariable Long sessionId) { service.revokeSession(id, sessionId); }
    @PostMapping("/api/consumer-profiles/{id}/revoke")
    @PreAuthorize("hasAnyRole('ADMIN','EDITOR')")
    public void revoke(@PathVariable Long id) { service.revokeProfile(id); }
}
