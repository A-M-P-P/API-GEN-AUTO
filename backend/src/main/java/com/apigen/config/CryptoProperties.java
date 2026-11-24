package com.apigen.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apigen.crypto")
public record CryptoProperties(String key) {
}
