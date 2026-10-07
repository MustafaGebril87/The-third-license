package com.thethirdlicense.services;

import com.thethirdlicense.models.RevokedToken;
import com.thethirdlicense.repositories.RevokedTokenRepository;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

/**
 * Deny-list for JWTs revoked before expiry (logout, refresh rotation).
 * Stores a SHA-256 hash of the token, never the token itself.
 */
@Service
public class RevokedTokenService {

    private final RevokedTokenRepository revokedTokenRepository;

    public RevokedTokenService(RevokedTokenRepository revokedTokenRepository) {
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;
        String hash = hash(token);
        if (!revokedTokenRepository.existsById(hash)) {
            revokedTokenRepository.save(new RevokedToken(hash, new Date()));
        }
    }

    public boolean isRevoked(String token) {
        return revokedTokenRepository.existsById(hash(token));
    }

    private static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
