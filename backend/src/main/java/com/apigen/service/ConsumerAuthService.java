package com.apigen.service;

import com.apigen.domain.*;
import com.apigen.execution.*;
import com.apigen.repository.*;
import com.apigen.web.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Consumer credentials and tokens never enter ExchangeRun or Studio JWT authentication. */
@Service
public class ConsumerAuthService {
    private final ConsumerProfileRepository profiles;
    private final ConsumerClientRepository clients;
    private final ConsumerSessionRepository sessions;
    private final OrganizationRepository organizations;
    private final ApiDefinitionRepository apis;
    private final PasswordEncoder passwords;
    private final ObjectMapper json;
    private final StepExecutor executor;
    private final OrganizationContextService contexts;
    private final AuditService audit;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, long[]> attempts = new LinkedHashMap<>();

    public ConsumerAuthService(ConsumerProfileRepository profiles, ConsumerClientRepository clients,
            ConsumerSessionRepository sessions, OrganizationRepository organizations, ApiDefinitionRepository apis,
            PasswordEncoder passwords, ObjectMapper json, StepExecutor executor,
            OrganizationContextService contexts, AuditService audit) {
        this.profiles = profiles; this.clients = clients; this.sessions = sessions;
        this.organizations = organizations; this.apis = apis; this.passwords = passwords;
        this.json = json; this.executor = executor; this.contexts = contexts; this.audit = audit;
    }

    @org.springframework.scheduling.annotation.Scheduled(cron = "0 30 3 * * *", zone = "UTC")
    @Transactional
    public void purgeExpiredSessions() {
        sessions.deleteByRefreshExpiresBefore(Instant.now().minus(Duration.ofDays(30)));
    }

    public record ProfileInput(String code, String name, boolean active, String mode,
                               List<Long> apiIds, Object pipeline) {}
    public record ClientView(Long id, String clientId, boolean active) {}
    public record ClientSecret(Long id, String clientId, String secret) {}
    public record Tokens(String access_token, String token_type, long expires_in,
                         String refresh_token, long refresh_expires_in) {}

    @Transactional
    public List<ConsumerProfile> list(Long organizationId) {
        organizations.findById(organizationId).orElseThrow(() -> ApiException.notFound("Organisation introuvable."));
        ensureDefault(organizationId);
        return profiles.findByOrganizationId(organizationId);
    }

    @Transactional
    public void ensureDefault(Long organizationId) {
        if (profiles.findByOrganizationIdAndCode(organizationId, "default").isEmpty()) {
            ConsumerProfile p = new ConsumerProfile();
            p.organizationId = organizationId; p.code = "default"; p.name = "Authentification par défaut";
            profiles.save(p); // Deliberately disabled until an administrator grants APIs.
        }
    }

    @Transactional
    public ConsumerProfile save(Long organizationId, Long id, ProfileInput input) {
        if (input.code() == null || !input.code().matches("[a-z][a-z0-9-]{1,59}") || input.name() == null
                || input.name().isBlank() || input.name().length() > 150) {
            throw ApiException.badRequest("Renseignez le code (2 à 60 lettres minuscules, chiffres ou tirets) et le nom du système.");
        }
        organizations.findById(organizationId).orElseThrow(() -> ApiException.notFound("Organisation introuvable."));
        ConsumerProfile p = id == null ? new ConsumerProfile() : requireProfile(id);
        if (id != null && !p.organizationId.equals(organizationId)) throw ApiException.notFound("Profil introuvable.");
        var duplicate = profiles.findByOrganizationIdAndCode(organizationId, input.code());
        if (duplicate.isPresent() && !duplicate.get().id.equals(id)) throw ApiException.conflict("Ce code de système existe déjà.");
        if (id != null && !p.code.equals(input.code())) throw ApiException.badRequest("Le code d’un profil existant ne peut pas changer.");
        if (!Set.of("LOCAL", "PIPELINE").contains(Objects.toString(input.mode(), ""))) throw ApiException.badRequest("Choisissez le mode de vérification.");
        Set<Long> grants = new LinkedHashSet<>(input.apiIds() == null ? List.of() : input.apiIds());
        for (Long apiId : grants) {
            if (apiId == null || apiId <= 0) throw ApiException.badRequest("Sélectionnez des API valides dans la liste.");
            ApiDefinition api = apis.findById(apiId).orElseThrow(() -> ApiException.badRequest("API introuvable."));
            if (api.getOrganization() == null || !api.getOrganization().getId().equals(organizationId)) throw ApiException.badRequest("Choisissez uniquement des API de cette organisation.");
        }
        var pipeline = json.valueToTree(input.pipeline());
        if (!pipeline.isArray()) throw ApiException.badRequest("Le pipeline d’authentification doit être une liste d’étapes.");
        for (var step : pipeline) {
            // No LOG, file access, DB writes, fallback on errors or arbitrary step types in auth.
            if (!Set.of("DB_QUERY", "SQL_QUERY", "HTTP", "REST", "MAP", "TRANSFORM", "SET").contains(step.path("type").asText())) {
                throw ApiException.badRequest("Étape non autorisée pour l’authentification : utilisez lecture SQL, HTTP, MAP ou SET.");
            }
            if (Set.of("DB_QUERY", "SQL_QUERY").contains(step.path("type").asText())
                    && step.path("sql").asText().contains("{{")) {
                throw ApiException.badRequest("Pour l’authentification SQL, utilisez ? et params, jamais de valeur insérée dans le texte SQL.");
            }
            if (step.has("failOnError") && !step.path("failOnError").asBoolean()) {
                throw ApiException.badRequest("Une erreur d’authentification doit interrompre le pipeline : retirez failOnError=false.");
            }
        }
        if (input.active() && (grants.isEmpty() || (input.mode().equals("PIPELINE") && pipeline.isEmpty()))) {
            throw ApiException.badRequest("Avant activation, sélectionnez les API autorisées et configurez le pipeline si nécessaire.");
        }
        if (id != null) revokeProfile(id);
        p.organizationId = organizationId; p.code = input.code(); p.name = input.name().trim();
        p.active = input.active(); p.mode = input.mode(); p.apiIds = write(grants); p.pipeline = pipeline.toString();
        profiles.save(p);
        audit.record("CONSUMER_PROFILE_SAVE", "ConsumerProfile", p.id.toString(), "Configuration du profil " + p.code);
        return p;
    }

    @Transactional
    public List<ClientView> clients(Long profileId) {
        requireProfile(profileId);
        return clients.findByProfileId(profileId).stream().map(c -> new ClientView(c.id, c.clientId, c.active)).toList();
    }

    @Transactional
    public ClientSecret createClient(Long profileId, String clientId) {
        ConsumerProfile p = requireProfile(profileId);
        if (!"LOCAL".equals(p.mode)) throw ApiException.badRequest("Ce système vérifie ses utilisateurs dans son pipeline, sans compte local.");
        if (clientId == null || !clientId.matches("[A-Za-z0-9_@.-]{2,100}")) throw ApiException.badRequest("Renseignez un identifiant de 2 à 100 caractères, sans espace.");
        if (clients.findByProfileIdAndClientId(profileId, clientId).isPresent()) throw ApiException.conflict("Cet identifiant existe déjà.");
        ConsumerClient c = new ConsumerClient();
        c.profileId = profileId; c.clientId = clientId;
        String secret = randomToken(); c.secretHash = passwords.encode(secret); clients.save(c);
        audit.record("CONSUMER_CREATE", "ConsumerClient", c.id.toString(), "Création d’un consommateur local");
        return new ClientSecret(c.id, c.clientId, secret);
    }

    @Transactional
    public void disableClient(Long profileId, Long id) {
        requireProfile(profileId);
        ConsumerClient c = clients.findById(id).orElseThrow(() -> ApiException.notFound("Consommateur introuvable."));
        if (!c.profileId.equals(profileId)) throw ApiException.notFound("Consommateur introuvable.");
        c.active = false;
        for (ConsumerSession s : sessions.findByProfileId(profileId)) if (id.equals(s.clientId)) s.revoked = true;
        audit.record("CONSUMER_DISABLE", "ConsumerClient", id.toString(), "Désactivation et révocation des accès");
    }

    public record SessionView(Long id, String subject, Instant accessExpires, Instant refreshExpires, boolean revoked) {}
    @Transactional
    public List<SessionView> sessionList(Long profileId) {
        requireProfile(profileId);
        return sessions.findByProfileId(profileId).stream()
            .map(s -> new SessionView(s.id, s.subject, s.accessExpires, s.refreshExpires, s.revoked)).toList();
    }
    @Transactional
    public void revokeSession(Long profileId, Long sessionId) {
        requireProfile(profileId);
        ConsumerSession s = sessions.findById(sessionId).orElseThrow(() -> ApiException.notFound("Session introuvable."));
        if (!s.profileId.equals(profileId)) throw ApiException.notFound("Session introuvable.");
        s.revoked = true;
        audit.record("CONSUMER_SESSION_REVOKE", "ConsumerSession", sessionId.toString(), "Révocation d’une session consommateur");
    }

    @Transactional
    public void revokeProfile(Long id) {
        requireProfile(id);
        for (ConsumerSession s : sessions.findByProfileId(id)) s.revoked = true;
        audit.record("CONSUMER_REVOKE", "ConsumerProfile", id.toString(), "Révocation des sessions du système");
    }

    @Transactional
    public Tokens login(String orgCode, String profileCode, Map<String, Object> credentials, String remote) {
        limit(orgCode + ":" + profileCode + ":" + remote);
        ConsumerProfile p = activeProfile(orgCode, profileCode);
        String subject;
        Long clientId = null;
        Set<Long> grants = ids(p.apiIds);
        if ("LOCAL".equals(p.mode)) {
            String username = Objects.toString(credentials.get("client_id"), "");
            String secret = Objects.toString(credentials.get("client_secret"), "");
            if (!secret.matches("[A-Za-z0-9_-]{43}") || username.length() > 100) throw denied();
            ConsumerClient c = clients.findByProfileIdAndClientId(p.id, username).orElse(null);
            if (c == null || !c.active || !passwords.matches(secret, c.secretHash)) throw denied();
            subject = c.clientId; clientId = c.id;
        } else {
            ExternalIdentity identity = verifyExternal(p, credentials);
            subject = identity.subject();
            grants.retainAll(identity.apiIds());
        }
        if (grants.isEmpty()) throw denied();
        ConsumerSession session = new ConsumerSession();
        session.profileId = p.id; session.subject = subject; session.clientId = clientId;
        session.apiIds = write(grants); session.refreshExpires = Instant.now().plus(Duration.ofDays(7));
        Tokens tokens = rotate(session);
        audit.record("CONSUMER_LOGIN", "ConsumerProfile", p.id.toString(), "Session consommateur créée");
        return tokens;
    }

    private record ExternalIdentity(String subject, Set<Long> apiIds) {}
    private ExternalIdentity verifyExternal(ConsumerProfile p, Map<String, Object> credentials) {
        try {
            ExecutionContext context = new ExecutionContext();
            context.setInitial(Map.of("request", Map.of("body", credentials), "org",
                    contexts.forOrganization(organizations.findById(p.organizationId).orElseThrow()).asRoot()));
            for (var node : json.readTree(p.pipeline)) {
                @SuppressWarnings("unchecked") Map<String, Object> step = json.convertValue(node, Map.class);
                context.recordStep(node.path("name").asText("step" + context.getSteps().size()), executor.execute(step, context));
            }
            Object output = context.getOutput();
            if (output instanceof List<?> rows && rows.size() == 1) output = rows.get(0);
            if (!(output instanceof Map<?, ?> result) || !(Boolean.TRUE.equals(result.get("authenticated"))
                    || "true".equals(result.get("authenticated")))) throw denied();
            String subject = Objects.toString(result.get("subject"), "");
            if (subject.isBlank() || subject.length() > 200) throw denied();
            Object rights = result.containsKey("apiIds") ? result.get("apiIds") : result.get("apiids");
            return new ExternalIdentity(subject, ids(rights instanceof String text ? text : write(rights)));
        } catch (Exception e) { throw denied(); }
    }

    @Transactional
    public Tokens refresh(String org, String code, String raw, Map<String, Object> credentials, String remote) {
        limit(org + ":" + code + ":" + remote);
        ConsumerProfile p = activeProfile(org, code);
        ConsumerSession s = sessions.findByRefreshHash(hash(raw)).orElseThrow(this::denied);
        if (!p.id.equals(s.profileId) || !valid(s) || !s.refreshExpires.isAfter(Instant.now())) throw denied();
        if ("PIPELINE".equals(p.mode)) {
            // No retained password: caller supplies credentials again to recheck upstream rights.
            ExternalIdentity identity = verifyExternal(p, credentials == null ? Map.of() : credentials);
            if (!s.subject.equals(identity.subject())) throw denied();
            Set<Long> grants = ids(s.apiIds); grants.retainAll(identity.apiIds()); grants.retainAll(ids(p.apiIds));
            if (grants.isEmpty()) throw denied();
            s.apiIds = write(grants);
        }
        // Family has a fixed seven-day maximum lifetime; rotating never extends it.
        Tokens result = rotate(s);
        audit.record("CONSUMER_REFRESH", "ConsumerProfile", p.id.toString(), "Session consommateur renouvelée");
        return result;
    }

    @Transactional(readOnly = true)
    public String authorize(ApiDefinition api, String header) {
        // Gateway entities are detached (open-in-view=false); load current ownership in this transaction.
        api = apis.findById(api.getId()).orElseThrow(this::denied);
        if (header == null || !header.startsWith("Bearer ")) throw denied();
        ConsumerSession s = sessions.findByAccessHash(hash(header.substring(7).trim())).orElseThrow(this::denied);
        ConsumerProfile p = profiles.findById(s.profileId).orElseThrow(this::denied);
        Organization org = api.getOrganization();
        if (org == null || !org.isActive() || !org.getId().equals(p.organizationId) || !p.active || !valid(s)
                || !s.accessExpires.isAfter(Instant.now()) || !ids(s.apiIds).contains(api.getId())
                || !ids(p.apiIds).contains(api.getId())) throw denied();
        return s.subject;
    }

    private boolean valid(ConsumerSession s) {
        return !s.revoked && (s.clientId == null || clients.findById(s.clientId).map(c -> c.active).orElse(false));
    }
    private Tokens rotate(ConsumerSession s) {
        String access = randomToken(), refresh = randomToken();
        Instant now = Instant.now();
        s.accessHash = hash(access); s.refreshHash = hash(refresh);
        s.accessExpires = now.plus(Duration.ofHours(24));
        if (s.accessExpires.isAfter(s.refreshExpires)) s.accessExpires = s.refreshExpires;
        sessions.saveAndFlush(s);
        return new Tokens(access, "Bearer", Duration.between(now, s.accessExpires).toSeconds(), refresh,
                Duration.between(now, s.refreshExpires).toSeconds());
    }
    private ConsumerProfile activeProfile(String orgCode, String code) {
        Organization org = organizations.findByCodeIgnoreCase(orgCode).orElseThrow(this::denied);
        ConsumerProfile p = profiles.findByOrganizationIdAndCode(org.getId(), code).orElseThrow(this::denied);
        if (!org.isActive() || !p.active) throw denied();
        return p;
    }
    private ConsumerProfile requireProfile(Long id) {
        return profiles.lockById(id).orElseThrow(() -> ApiException.notFound("Profil d’authentification introuvable."));
    }
    private Set<Long> ids(String value) {
        try {
            var node = json.readTree(value);
            if (node == null || !node.isArray()) throw new IllegalArgumentException();
            Set<Long> result = new LinkedHashSet<>();
            for (var id : node) { if (!id.isIntegralNumber() || !id.canConvertToLong()) throw new IllegalArgumentException(); result.add(id.longValue()); }
            return result;
        } catch (Exception e) { throw denied(); }
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw ApiException.badRequest("Configuration illisible."); } }
    private String randomToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String hash(String raw) {
        if (raw == null || raw.length() < 32 || raw.length() > 200) throw denied();
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private ApiException denied() { return ApiException.unauthorized("Authentification refusée ou accès expiré. Vérifiez vos identifiants et vos droits, puis reconnectez-vous."); }
    private synchronized void limit(String key) {
        long now = System.currentTimeMillis();
        attempts.entrySet().removeIf(e -> now - e.getValue()[0] > 60_000);
        if (!attempts.containsKey(key) && attempts.size() >= 10_000) throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Réessayez dans une minute.");
        long[] bucket = attempts.computeIfAbsent(key, k -> new long[]{now, 0});
        if (++bucket[1] > 20) throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Réessayez dans une minute.");
    }
}
