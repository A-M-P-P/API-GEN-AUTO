package com.apigen.service.connector;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcConnectionPropsTest {

    @Test
    void oracleUrlsGetTheTimezoneWorkaround() {
        Map<String, String> props = JdbcConnectionProps.defaults("jdbc:oracle:thin:@db:1521:ORCL");
        assertEquals("false", props.get("oracle.jdbc.timezoneAsRegion"),
                "Oracle doit envoyer un décalage, pas un nom de région (ORA-01882)");
    }

    @Test
    void nonOracleUrlsGetNoProperty() {
        assertTrue(JdbcConnectionProps.defaults("jdbc:postgresql://db:5432/inventaire").isEmpty());
        assertTrue(JdbcConnectionProps.defaults("jdbc:mysql://db:3306/rh").isEmpty());
        assertTrue(JdbcConnectionProps.defaults("jdbc:sqlserver://db:1433;databaseName=ventes").isEmpty());
        assertTrue(JdbcConnectionProps.defaults(null).isEmpty());
    }

    @Test
    void userOverridesWinOverDefaults() {
        Map<String, String> merged = JdbcConnectionProps.merge("jdbc:oracle:thin:@db:1521:ORCL",
                Map.of("oracle.jdbc.timezoneAsRegion", "true", "oracle.jdbc.defaultRowPrefetch", "50"));
        assertEquals("true", merged.get("oracle.jdbc.timezoneAsRegion"));
        assertEquals("50", merged.get("oracle.jdbc.defaultRowPrefetch"));
    }

    @Test
    void credentialsAreAddedToDriverProperties() {
        Properties properties = JdbcConnectionProps.forUrl("jdbc:oracle:thin:@db:1521:ORCL", "sigif", "secret");
        assertEquals("false", properties.getProperty("oracle.jdbc.timezoneAsRegion"));
        assertEquals("sigif", properties.getProperty("user"));
        assertEquals("secret", properties.getProperty("password"));

        Properties blank = JdbcConnectionProps.forUrl("jdbc:postgresql://db:5432/apigen", "", null);
        assertFalse(blank.containsKey("user"));
        assertFalse(blank.containsKey("password"));
    }

    @Test
    void oracleDetectionIsCaseInsensitiveAndPrefixOnly() {
        assertTrue(JdbcConnectionProps.isOracle("JDBC:ORACLE:thin:@db:1521:ORCL"));
        assertTrue(JdbcConnectionProps.isOracle("jdbc:oracle:thin:@//db:1521/SERVICE"));
        assertFalse(JdbcConnectionProps.isOracle("jdbc:postgresql://oracle-host:5432/db"));
        assertFalse(JdbcConnectionProps.isOracle(""));
    }
}
