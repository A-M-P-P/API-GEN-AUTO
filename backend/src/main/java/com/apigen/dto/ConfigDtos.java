package com.apigen.dto;

import java.util.List;
import java.util.Map;

/** DTO de l'écran de configuration. */
public final class ConfigDtos {

    private ConfigDtos() {
    }

    /** Une propriété telle que vue par l'application. */
    public record ConfigEntry(
            String key,
            String label,
            String value,
            boolean masked,
            boolean secret,
            boolean editable,
            String source,
            String hint
    ) {
    }

    public record ConfigView(
            String runtimeConfigPath,
            boolean runtimeConfigExists,
            String activeProfile,
            String serverPort,
            String contextPath,
            boolean restartRequired,
            List<ConfigEntry> entries
    ) {
    }

    public record ConfigUpdateRequest(Map<String, String> values) {
    }

    public record ConfigUpdateResponse(
            List<String> changed,
            List<String> ignored,
            String writtenTo,
            boolean restartRequired
    ) {
    }
}
