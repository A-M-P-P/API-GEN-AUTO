package com.apigen.service.connector;

import java.util.LinkedHashMap;
import java.util.Locale;
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
 *
 * <p>Sur une Oracle 10g, le pilote traduit aussi le Locale de la JVM en
 * {@code NLS_LANGUAGE}/{@code NLS_TERRITORY} ; un territoire inconnu déclenche
 * {@code ORA-12705}. Les maps {@code oracle.jdbc.languageMap} et
 * {@code oracle.jdbc.territoryMap} sont donc initialisées vers AMERICAN_AMERICA.</p>
 */
public final class JdbcConnectionProps {

    /**
     * Le pilote Oracle dérive NLS_LANGUAGE / NLS_TERRITORY du Locale de la JVM et exécute un
     * ALTER SESSION à chaque connexion. Sur une 10g, un territoire comme CAMEROON est inconnu :
     * ORA-00604 / ORA-12705. On traduit le Locale courant vers AMERICAN_AMERICA, valeurs
     * acceptées par toutes les versions, sans toucher au Locale global de l'application.
     */
    static {
        configureOracleNls();
    }

    private JdbcConnectionProps() {
    }

    /**
     * À appeler avant tout chargement du pilote Oracle : le pilote lit ces maps une seule fois,
     * lors de l'initialisation de sa classe NLS. Sans cela, un réglage posé au moment de la
     * première connexion peut arriver trop tard.
     */
    public static void configureOracleNls() {
        Locale locale = Locale.getDefault(Locale.Category.FORMAT);
        String language = locale.getLanguage();
        String country = locale.getCountry();
        setIfAbsent("oracle.jdbc.languageMap", pair(language, "AMERICAN"));
        setIfAbsent("oracle.jdbc.territoryMap", pair(country, "AMERICA"));
    }

    private static String pair(String code, String oracleName) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code + "=" + oracleName + ";" + code.toUpperCase(Locale.ROOT) + "=" + oracleName;
    }

    private static void setIfAbsent(String key, String value) {
        if (value != null && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
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
