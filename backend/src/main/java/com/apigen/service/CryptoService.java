package com.apigen.service;

import com.apigen.config.CryptoProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Chiffrement AES-256-GCM des secrets stockés en base.
 * Format : enc:v1:&lt;iv base64&gt;:&lt;ciphertext base64&gt;
 */
@Service
public class CryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_LENGTH = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKey key;
    private final SecureRandom random = new SecureRandom();

    public CryptoService(CryptoProperties properties) {
        this.key = deriveKey(properties.key());
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_LENGTH];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX
                    + Base64.getEncoder().encodeToString(iv) + ":"
                    + Base64.getEncoder().encodeToString(ciphertext);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de chiffrer le secret", e);
        }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        if (!value.startsWith(PREFIX)) {
            // Rétrocompatibilité : valeur en clair.
            return value;
        }
        try {
            String[] parts = value.substring(PREFIX.length()).split(":");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de déchiffrer le secret", e);
        }
    }

    private SecretKey deriveKey(String configured) {
        byte[] material;
        try {
            byte[] decoded = Base64.getDecoder().decode(configured);
            material = decoded.length >= 16 ? decoded : configured.getBytes(StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            material = configured.getBytes(StandardCharsets.UTF_8);
        }
        try {
            byte[] keyBytes = MessageDigest.getInstance("SHA-256").digest(material);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new IllegalStateException("Impossible de dériver la clé de chiffrement", e);
        }
    }
}
