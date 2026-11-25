package com.apigen.service.connector;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pool de connexions JDBC par base cible.
 *
 * <p>Chaque {@link com.apigen.domain.Connection} de type base de données dispose de son propre
 * pool HikariCP, identifié par {@code conn:<id>}. Un pool est recréé automatiquement lorsque
 * l'empreinte de la configuration change (URL, identifiants, taille de pool) et fermé après
 * une période d'inactivité.</p>
 *
 * <p>La création du pool est paresseuse ({@code initializationFailTimeout = -1}) : une base
 * injoignable ne lève une erreur qu'au moment du premier {@link #borrow(String, PoolSpec)},
 * avec le message du pilote JDBC.</p>
 */
@Component
public class JdbcPoolManager {

    private static final Logger log = LoggerFactory.getLogger(JdbcPoolManager.class);
    private static final long IDLE_EVICTION_MS = 15 * 60 * 1000L;
    private static final int MAX_POOL_CAP = 20;

    /**
     * Spécification immutable d'un pool : tout changement d'un champ recrée le pool.
     *
     * @param driverProperties propriétés transmises au pilote JDBC (fuseau horaire Oracle,
     *                          options propres à une base exotique)
     */
    public record PoolSpec(String jdbcUrl, String username, String password, int maxPoolSize,
                           Map<String, String> driverProperties) {
        public PoolSpec(String jdbcUrl, String username, String password, int maxPoolSize) {
            this(jdbcUrl, username, password, maxPoolSize, Map.of());
        }

        public int effectiveMaxPoolSize() {
            return Math.max(1, Math.min(MAX_POOL_CAP, maxPoolSize));
        }
    }

    /**
     * Construit la spécification à partir de la configuration stockée d'une connexion.
     * Méthode statique : aucun bean injecté, donc utilisable sans risque de cycle.
     */
    public static PoolSpec specOf(JsonNode baseConfig, Map<String, Object> credentials) {
        String url = text(baseConfig, "jdbcUrl");
        if (url == null || url.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl manquant dans la configuration de la connexion");
        }
        String username = credentials == null ? null : string(credentials.get("username"));
        String password = credentials == null ? null : string(credentials.get("password"));
        int maxPoolSize = 5;
        if (baseConfig != null && baseConfig.hasNonNull("maxPoolSize")) {
            try {
                maxPoolSize = baseConfig.get("maxPoolSize").asInt(5);
            } catch (Exception ignored) {
                maxPoolSize = 5;
            }
        }
        return new PoolSpec(url, username, password, maxPoolSize, driverProperties(baseConfig, url));
    }

    /** Propriétés du pilote : valeurs par défaut selon l'URL + surcharges de la connexion. */
    private static Map<String, String> driverProperties(JsonNode baseConfig, String url) {
        Map<String, String> overrides = new java.util.LinkedHashMap<>();
        if (baseConfig != null && baseConfig.hasNonNull("driverProperties")
                && baseConfig.get("driverProperties").isObject()) {
            baseConfig.get("driverProperties").fields().forEachRemaining(entry -> {
                if (!entry.getValue().isNull()) {
                    overrides.put(entry.getKey(), entry.getValue().asText());
                }
            });
        }
        return JdbcConnectionProps.merge(url, overrides);
    }

    private static String text(JsonNode node, String field) {
        return node == null || !node.hasNonNull(field) ? null : node.get(field).asText();
    }

    private static String string(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static final class PoolEntry {
        final String fingerprint;
        final HikariDataSource dataSource;
        volatile long lastBorrow = System.currentTimeMillis();

        PoolEntry(String fingerprint, HikariDataSource dataSource) {
            this.fingerprint = fingerprint;
            this.dataSource = dataSource;
        }
    }

    private final Map<String, PoolEntry> pools = new ConcurrentHashMap<>();

    /**
     * Emprunte une connexion au pool identifié par {@code key}.
     *
     * @param key  identifiant du pool ({@code conn:<id>} ou {@code test:<hash>})
     * @param spec paramètres de connexion ; un changement d'empreinte recrée le pool
     * @return une connexion ouverte, à fermer par l'appelant (try-with-resources)
     */
    public Connection borrow(String key, PoolSpec spec) throws SQLException {
        String fingerprint = fingerprint(spec);
        PoolEntry entry = pools.compute(key, (ignored, existing) -> {
            if (existing != null && existing.fingerprint.equals(fingerprint)) {
                return existing;
            }
            if (existing != null) {
                closeQuietly(existing);
            }
            return new PoolEntry(fingerprint, createDataSource(key, spec));
        });
        entry.lastBorrow = System.currentTimeMillis();
        return entry.dataSource.getConnection();
    }

    /** Ferme le pool d'une connexion (appelé à la mise à jour ou à la suppression). */
    public void evict(String key) {
        PoolEntry removed = pools.remove(key);
        if (removed != null) {
            closeQuietly(removed);
            log.info("Pool JDBC fermé pour {}", key);
        }
    }

    /** Nombre de pools ouverts, exposé à des fins de diagnostic. */
    public int poolCount() {
        return pools.size();
    }

    /** Ferme les pools inutilisés depuis plus de {@link #IDLE_EVICTION_MS}. */
    @Scheduled(fixedDelayString = "${apigen.jdbc-pool.evict-interval-ms:300000}")
    public void evictIdlePools() {
        long now = System.currentTimeMillis();
        pools.entrySet().removeIf(entry -> {
            PoolEntry pool = entry.getValue();
            if (now - pool.lastBorrow <= IDLE_EVICTION_MS) {
                return false;
            }
            closeQuietly(pool);
            log.info("Pool JDBC inactif fermé pour {}", entry.getKey());
            return true;
        });
    }

    @PreDestroy
    public void shutdown() {
        pools.values().forEach(this::closeQuietly);
        pools.clear();
    }

    private HikariDataSource createDataSource(String key, PoolSpec spec) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(spec.jdbcUrl());
        if (spec.username() != null && !spec.username().isBlank()) {
            config.setUsername(spec.username());
        }
        if (spec.password() != null) {
            config.setPassword(spec.password());
        }
        config.setDataSourceProperties(JdbcConnectionProps.toProperties(
                spec.driverProperties(), spec.username(), spec.password()));
        config.setMaximumPoolSize(spec.effectiveMaxPoolSize());
        config.setMinimumIdle(Math.min(1, spec.effectiveMaxPoolSize()));
        config.setConnectionTimeout(10_000);
        config.setValidationTimeout(5_000);
        config.setIdleTimeout(10 * 60_000);
        config.setMaxLifetime(30 * 60_000);
        config.setKeepaliveTime(5 * 60_000);
        // Ne pas échouer à la création : la base peut être momentanément injoignable.
        config.setInitializationFailTimeout(-1);
        config.setPoolName("apigen-" + key.replaceAll("[^A-Za-z0-9_-]", "-"));
        config.setLeakDetectionThreshold(60_000);
        return new HikariDataSource(config);
    }

    private String fingerprint(PoolSpec spec) {
        String raw = spec.jdbcUrl() + '\u0000' + spec.username() + '\u0000' + spec.password()
                + '\u0000' + spec.effectiveMaxPoolSize() + '\u0000'
                + new java.util.TreeMap<>(spec.driverProperties());
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            return Integer.toHexString(raw.hashCode());
        }
    }

    private void closeQuietly(PoolEntry entry) {
        try {
            entry.dataSource.close();
        } catch (Exception e) {
            log.debug("Fermeture du pool ignorée : {}", e.getMessage());
        }
    }
}
