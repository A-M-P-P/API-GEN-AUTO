package com.apigen.service.connector;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Propriétés à transmettre au pilote JDBC, construites automatiquement selon l'URL.
 *
 * <p>Objectif : accepter n'importe quelle base cible (Oracle 10g, 11g, 12c, 19c, PostgreSQL,
 * MySQL, SQL Server…) sans réglage manuel. Le cas le plus fréquent est le fuseau horaire :
 * le pilote Oracle envoie par défaut le <em>nom</em> de la région de la JVM
 * (ex. {@code Africa/Douala}). Une base ancienne dont le fichier de fuseaux ne connaît pas
 * cette région répond alors {@code ORA-01882: timezone region not found}, ou
 * {@code ORA-00604} au niveau SQL récursif. En demandant un décalage numérique
 * ({@code oracle.jdbc.timezoneAsRegion=false}), la connexion passe sur toutes les versions.</p>
 */
public final class JdbcConnectionProps {

    private JdbcConnectionProps() {
    }

    /** Vrai pour toute URL Oracle, quelle que soit la version du serveur. */
    public static boolean isOracle(String jdbcUrl) {
        return jdbcUrl != null && jdbcUrl.regionMatches(true, 0, "jdbc:oracle:", 0, "jdbc:oracle:".length());
    }

    /** Propriétés par défaut pour une URL donnée (vide si la base n'a besoin de rien). */
    public static Map<String, String> defaults(String jdbcUrl) {
        Map<String, String> props = new LinkedHashMap<>();
        if (isOracle(jdbcUrl)) {
            // Évite ORA-01882 sur les bases dont le fichier de fuseaux est ancien.
            props.put("oracle.jdbc.timezoneAsRegion", "false");
        }
        return props;
    }

    /** Fusionne les valeurs utilisateur avec les valeurs par défaut (l'utilisateur prime). */
    public static Map<String, String> merge(String jdbcUrl, Map<String, String> overrides) {
        Map<String, String> props = defaults(jdbcUrl);
        if (overrides != null) {
            overrides.forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null) {
                    props.put(key, value);
                }
            });
        }
        return props;
    }

    /** Propriétés du pilote, éventuellement accompagnées des identifiants. */
    public static Properties toProperties(Map<String, String> values, String username, String password) {
        Properties properties = new Properties();
        if (values != null) {
            properties.putAll(values);
        }
        if (username != null && !username.isBlank()) {
            properties.setProperty("user", username);
        }
        if (password != null) {
            properties.setProperty("password", password);
        }
        return properties;
    }

    /** Raccourci : propriétés par défaut + identifiants, pour {@code DriverManager}. */
    public static Properties forUrl(String jdbcUrl, String username, String password) {
        return toProperties(defaults(jdbcUrl), username, password);
    }
}
