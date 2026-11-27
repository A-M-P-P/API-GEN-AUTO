package com.apigensetup;

import org.springframework.core.env.Environment;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.io.*;
import java.security.*;
import java.sql.*;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/setup")
public class SetupController {
    private final Path file;
    private final String code;
    private final Instant expires = Instant.now().plusSeconds(1800);
    private int attempts;
    private long window = System.currentTimeMillis();
    private volatile boolean saved;
    private final Environment env;

    public SetupController(Environment env) {
        this.env = env;
        file = Path.of(env.getProperty("apigen.runtime-config", "./config/runtime.properties")).toAbsolutePath().normalize();
        code = random();
        System.err.println("API-GEN — MODE INSTALLATION UNIQUEMENT. Code temporaire (30 min) : " + code);
        System.err.println("API-GEN — Fichier cible : " + file + ". Désactivez APIGEN_SETUP_ENABLED après enregistrement et redémarrez.");
    }
    public record Input(String type, String host, Integer port, String database,
                        String oracleAddressing, String username, String password) {}
    record DatabaseSpec(String driver, String dialect, String url, String migrations, String product, int minimum) {}

    @GetMapping("/status")
    public ResponseEntity<?> status() { return response(Map.of("setup", true, "saved", saved)); }

    @PostMapping("/test")
    public ResponseEntity<?> test(@RequestHeader(value = "X-Setup-Code", required = false) String token, @RequestBody Input input) {
        authorize(token);
        return response(check(input));
    }
    @PostMapping("/save")
    public synchronized ResponseEntity<?> save(@RequestHeader(value = "X-Setup-Code", required = false) String token, @RequestBody Input input) throws IOException {
        authorize(token);
        if (saved) throw failure("La configuration est déjà enregistrée. Désactivez le mode installation puis redémarrez.");
        Map<String, Object> checked = check(input); // Recheck server-side; do not trust the UI.
        DatabaseSpec spec = spec(input);
        Properties p = new Properties();
        if (Files.exists(file)) {
            if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)) throw failure("Le fichier cible doit être un fichier normal, pas un lien.");
            try (InputStream in = Files.newInputStream(file)) { p.load(in); }
        }
        p.setProperty("spring.datasource.url", spec.url());
        p.setProperty("spring.datasource.driver-class-name", spec.driver());
        p.setProperty("spring.datasource.username", input.username());
        p.setProperty("spring.datasource.password", input.password() == null ? "" : input.password());
        p.setProperty("spring.jpa.database-platform", spec.dialect());
        p.setProperty("spring.jpa.properties.hibernate.default_schema", switch (input.type()) {
            case "ORACLE" -> input.username().toUpperCase(Locale.ROOT);
            case "POSTGRESQL" -> "public";
            case "SQLSERVER" -> "dbo";
            case "H2" -> "PUBLIC";
            default -> "";
        });
        p.setProperty("spring.h2.console.enabled", "false");
        p.remove("spring.flyway.default-schema"); p.remove("spring.flyway.schemas");
        if (input.type().equals("POSTGRESQL")) p.setProperty("spring.flyway.default-schema", "public");
        if (input.type().equals("SQLSERVER")) p.setProperty("spring.flyway.default-schema", "dbo");
        // Flyway must use the same credentials as the platform.
        for (String key : List.of("spring.flyway.url", "spring.flyway.user", "spring.flyway.password")) p.remove(key);
        p.setProperty("spring.flyway.locations", spec.migrations());
        p.setProperty("spring.flyway.enabled", "true");
        p.setProperty("spring.flyway.baseline-on-migrate", "false");
        p.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        p.setProperty("apigen.seed.demo", "false");
        if (missing(p, "apigen.jwt.secret", "APIGEN_JWT_SECRET")) p.setProperty("apigen.jwt.secret", random() + random());
        if (missing(p, "apigen.crypto.key", "APIGEN_CRYPTO_KEY")) {
            if (Boolean.TRUE.equals(checked.get("existingTables"))) {
                throw failure("La base contient déjà des tables. Renseignez explicitement sa clé apigen.crypto.key dans le fichier externe avant l’enregistrement pour préserver les secrets existants.");
            }
            p.setProperty("apigen.crypto.key", random());
        }
        // Never persist the switch: it must remain an explicit operator-controlled recovery mode.
        p.remove("apigen.setup.enabled");
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), ".apigen-", ".tmp");
        try {
            if (Files.getFileStore(temporary).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
            try (OutputStream out = Files.newOutputStream(temporary)) { p.store(out, "API-GEN - configuration privee. Ne pas publier ni versionner."); }
            // No unsafe non-atomic fallback that could leave a partial credentials file.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
        saved = true;
        return response(Map.of("message", "Configuration enregistrée. Désactivez APIGEN_SETUP_ENABLED puis redémarrez le backend. Aucun transfert de données n’a été effectué."));
    }
    private boolean missing(Properties p, String key, String variable) {
        String explicit = p.getProperty(key, env.getProperty(variable, ""));
        if (!explicit.isBlank() && !explicit.contains("A_REMPLACER")) return false;
        return true;
    }
    private Map<String, Object> check(Input input) {
        DatabaseSpec spec = spec(input);
        Properties props = new Properties();
        props.setProperty("user", input.username()); props.setProperty("password", input.password() == null ? "" : input.password());
        switch (input.type()) {
            case "ORACLE" -> {
                props.setProperty("oracle.net.CONNECT_TIMEOUT", "8000"); props.setProperty("oracle.jdbc.ReadTimeout", "10000");
                props.setProperty("oracle.jdbc.timezoneAsRegion", "false");
            }
            case "MYSQL", "POSTGRESQL" -> {
                props.setProperty("connectTimeout", input.type().equals("MYSQL") ? "8000" : "8");
                props.setProperty("socketTimeout", input.type().equals("MYSQL") ? "10000" : "10");
            }
            case "SQLSERVER" -> { props.setProperty("loginTimeout", "8"); props.setProperty("socketTimeout", "10000"); }
            default -> { }
        }
        try {
            Class.forName(spec.driver());
            DriverManager.setLoginTimeout(8);
            try (Connection c = DriverManager.getConnection(spec.url(), props)) {
                var meta = c.getMetaData();
                if (!meta.getDatabaseProductName().toLowerCase(Locale.ROOT).contains(spec.product())) throw failure("Le serveur ne correspond pas au type de base sélectionné.");
                if (meta.getDatabaseMajorVersion() < spec.minimum()) throw failure("Version incompatible : " + meta.getDatabaseProductName() + " " + meta.getDatabaseMajorVersion() + ". Version majeure minimale : " + spec.minimum() + ".");
                boolean existing;
                try (ResultSet tables = meta.getTables(c.getCatalog(), c.getSchema(), "%", new String[]{"TABLE"})) { existing = tables.next(); }
                return Map.of("message", "Connexion réussie et version compatible. Les droits de création et les migrations seront vérifiés au démarrage normal.", "product", meta.getDatabaseProductName(), "majorVersion", meta.getDatabaseMajorVersion(), "existingTables", existing);
            }
        } catch (SQLException e) {
            String message = switch (e.getErrorCode()) {
                case 1017 -> "Identifiant ou mot de passe refusé. Vérifiez aussi le SID/service sélectionné.";
                case 28000 -> "Compte Oracle verrouillé. Contactez votre administrateur de base.";
                default -> "Connexion impossible. Vérifiez le serveur, le port, le nom de base, les identifiants et les paramètres TLS du serveur.";
            };
            throw failure(message);
        } catch (ClassNotFoundException e) { throw failure("Pilote indisponible. Vérifiez le déploiement du backend."); }
    }
    static DatabaseSpec spec(Input i) {
        if (i == null || i.type() == null || i.username() == null || i.username().isBlank() || i.username().length() > 128) throw failure("Renseignez le type de base et l’utilisateur.");
        if ("H2".equals(i.type())) return new DatabaseSpec("org.h2.Driver", "org.hibernate.dialect.H2Dialect", "jdbc:h2:file:./data/apigen;MODE=Oracle;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH", "classpath:db/migration", "h2", 2);
        if (i.host() == null || !i.host().matches("[A-Za-z0-9_.-]{1,253}") || i.port() == null || i.port() < 1 || i.port() > 65535
                || i.database() == null || !i.database().matches("[A-Za-z0-9_.-]{1,128}") || i.password() == null || i.password().isEmpty()) throw failure("Renseignez le serveur (nom DNS ou IPv4), un port valide, la base/service et le mot de passe.");
        String host = i.host() + ":" + i.port(), db = i.database();
        return switch (i.type()) {
            case "ORACLE" -> {
                if (!Set.of("SID", "SERVICE").contains(Objects.toString(i.oracleAddressing(), ""))) throw failure("Choisissez SID ou nom de service Oracle.");
                yield new DatabaseSpec("oracle.jdbc.OracleDriver", "org.hibernate.dialect.OracleDialect", "jdbc:oracle:thin:@" + ("SID".equals(i.oracleAddressing()) ? host + ":" + db : "//" + host + "/" + db), "classpath:db/migration", "oracle", 12);
            }
            case "POSTGRESQL" -> new DatabaseSpec("org.postgresql.Driver", "org.hibernate.dialect.PostgreSQLDialect", "jdbc:postgresql://" + host + "/" + db, "classpath:db/platform/postgresql", "postgresql", 13);
            case "MYSQL" -> new DatabaseSpec("com.mysql.cj.jdbc.Driver", "org.hibernate.dialect.MySQLDialect", "jdbc:mysql://" + host + "/" + db, "classpath:db/platform/mysql", "mysql", 8);
            case "SQLSERVER" -> new DatabaseSpec("com.microsoft.sqlserver.jdbc.SQLServerDriver", "org.hibernate.dialect.SQLServerDialect", "jdbc:sqlserver://" + host + ";databaseName=" + db + ";encrypt=true;trustServerCertificate=false", "classpath:db/platform/sqlserver", "microsoft sql server", 13);
            default -> throw failure("Type de base non pris en charge.");
        };
    }
    private synchronized void authorize(String value) {
        long now = System.currentTimeMillis();
        if (now - window > 60_000) { window = now; attempts = 0; }
        if (++attempts > 20) throw new SetupFailure(HttpStatus.TOO_MANY_REQUESTS, "Trop de tentatives. Réessayez dans une minute.");
        if (saved || Instant.now().isAfter(expires) || value == null || value.length() > 256 || !MessageDigest.isEqual(code.getBytes(java.nio.charset.StandardCharsets.UTF_8), value.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
            throw new SetupFailure(HttpStatus.FORBIDDEN, "Code d’installation invalide ou expiré. Consultez la console du serveur ; redémarrez le mode installation si nécessaire.");
    }
    private static String random() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private static SetupFailure failure(String message) { return new SetupFailure(HttpStatus.BAD_REQUEST, message); }
    private static class SetupFailure extends RuntimeException {
        final HttpStatus status;
        SetupFailure(HttpStatus status, String message) { super(message); this.status = status; }
    }
    private ResponseEntity<?> response(Object body) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body); }
    @ExceptionHandler(SetupFailure.class)
    ResponseEntity<?> failed(SetupFailure e) { return ResponseEntity.status(e.status).cacheControl(CacheControl.noStore()).body(Map.of("message", e.getMessage())); }
    @ExceptionHandler(IOException.class)
    ResponseEntity<?> ioFailure() { return ResponseEntity.status(500).cacheControl(CacheControl.noStore()).body(Map.of("message", "Enregistrement impossible. Vérifiez les droits du fichier de configuration et la prise en charge du remplacement atomique. Aucun démarrage normal n’a été déclenché.")); }
}
