package com.apigen.security;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;

@Service
public class ApiKeyService {

    private static final SecureRandom RANDOM = new SecureRandom();

    public String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "agk_" + HexFormat.of().formatHex(bytes);
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    public boolean matches(String rawKey, String storedHash) {
        if (rawKey == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(hash(rawKey).getBytes(java.nio.charset.StandardCharsets.UTF_8),
                storedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
