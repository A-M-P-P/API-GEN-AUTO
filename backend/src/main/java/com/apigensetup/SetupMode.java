package com.apigensetup;

/** Explicit recovery mode: never fall back silently after a production database failure. */
public final class SetupMode {
    private SetupMode() {}
    public static boolean enabled(String... args) {
        String value = System.getProperty("apigen.setup.enabled", System.getenv("APIGEN_SETUP_ENABLED"));
        for (String arg : args) if (arg.startsWith("--apigen.setup.enabled=")) value = arg.substring(arg.indexOf('=') + 1);
        return "true".equalsIgnoreCase(value);
    }
}
