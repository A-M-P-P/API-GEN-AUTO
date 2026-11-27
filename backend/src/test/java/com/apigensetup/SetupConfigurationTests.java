package com.apigensetup;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SetupConfigurationTests {
    private SetupController.Input input(String type, String host, int port, String database, String addressing) {
        return new SetupController.Input(type, host, port, database, addressing, "apigen", "test-password");
    }
    @Test void constructsOnlySupportedDriverUrls() {
        assertEquals("jdbc:postgresql://db.example:5432/apigen", SetupController.spec(input("POSTGRESQL", "db.example", 5432, "apigen", "")).url());
        assertEquals("jdbc:mysql://db.example:3306/apigen", SetupController.spec(input("MYSQL", "db.example", 3306, "apigen", "")).url());
        assertEquals("jdbc:oracle:thin:@//db.example:1521/APIPDB", SetupController.spec(input("ORACLE", "db.example", 1521, "APIPDB", "SERVICE")).url());
        assertEquals("jdbc:oracle:thin:@db.example:1521:ORCL", SetupController.spec(input("ORACLE", "db.example", 1521, "ORCL", "SID")).url());
        assertTrue(SetupController.spec(input("SQLSERVER", "db.example", 1433, "apigen", "")).url().endsWith("encrypt=true;trustServerCertificate=false"));
    }
    @Test void doesNotPermitJdbcOptionInjection() {
        assertThrows(RuntimeException.class, () -> SetupController.spec(input("SQLSERVER", "db.example", 1433, "apigen;trustServerCertificate=true", "")));
        assertThrows(RuntimeException.class, () -> SetupController.spec(input("POSTGRESQL", "db.example/path", 5432, "apigen", "")));
        assertThrows(RuntimeException.class, () -> SetupController.spec(input("ORACLE", "localhost", 0, "ORCL", "SID")));
        assertThrows(RuntimeException.class, () -> SetupController.spec(input("FTP", "localhost", 21, "apigen", "")));
    }
    @Test void h2CannotInjectFilePathsOrRunSqlOnConnect() {
        var spec = SetupController.spec(input("H2", "", 0, "bad;INIT=RUNSCRIPT", ""));
        assertFalse(spec.url().contains("INIT"));
        assertTrue(spec.url().startsWith("jdbc:h2:file:./data/apigen;"));
    }
    @Test void everyEngineHasAllSevenMigrationResources() {
        String[] names = {"V1__init.sql", "V2__organizations.sql", "V3__organization_context.sql", "V4__api_code_per_organization.sql", "V5__endpoint_response_formats.sql", "V6__request_parameters.sql", "V7__consumer_authentication.sql"};
        for (String engine : new String[]{"postgresql", "mysql", "sqlserver"}) {
            for (String name : names) assertNotNull(getClass().getResource("/db/platform/" + engine + "/" + name));
        }
    }
}
