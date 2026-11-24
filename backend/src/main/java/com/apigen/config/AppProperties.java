package com.apigen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apigen")
public record AppProperties(
        Cors cors,
        Scheduler scheduler,
        Seed seed,
        Files files,
        Audit audit
) {
    public record Cors(java.util.List<String> allowedOrigins) {
    }

    public record Scheduler(long pollIntervalMs, boolean enabled) {
    }

    public record Seed(boolean demo) {
    }

    public record Files(String baseDir) {
    }

    public record Audit(int retentionDays) {
    }
}
