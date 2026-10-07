package com.thethirdlicense.services;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory brute-force protection: after MAX_FAILURES failed logins for the same
 * username from the same IP within WINDOW, further attempts are blocked until the window passes.
 * Keyed by username+IP so an attacker can't lock a victim out from everywhere.
 * (Per-instance only — use a shared store such as Redis if you run several backend instances.)
 */
@Service
public class LoginAttemptService {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(15);

    private record Attempts(int count, Instant firstFailure) {}

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    public boolean isBlocked(String username, String ip) {
        Attempts a = attempts.get(key(username, ip));
        if (a == null) return false;
        if (a.firstFailure().plus(WINDOW).isBefore(Instant.now())) {
            attempts.remove(key(username, ip));
            return false;
        }
        return a.count() >= MAX_FAILURES;
    }

    public void recordFailure(String username, String ip) {
        attempts.compute(key(username, ip), (k, a) -> {
            if (a == null || a.firstFailure().plus(WINDOW).isBefore(Instant.now())) {
                return new Attempts(1, Instant.now());
            }
            return new Attempts(a.count() + 1, a.firstFailure());
        });
    }

    public void recordSuccess(String username, String ip) {
        attempts.remove(key(username, ip));
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.toLowerCase()) + "|" + (ip == null ? "" : ip);
    }
}
