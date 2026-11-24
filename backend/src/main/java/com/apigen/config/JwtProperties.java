package com.apigen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apigen.jwt")
public record JwtProperties(
        String secret,
        long expirationHours,
        String issuer
) {
}
