package com.smartparking.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * JWT blacklist service.
 * Tokens are stored with their expiry time so the map self-prunes.
 * Entries are lost on server restart — acceptable for single-instance deployment.
 */
@Service
public class TokenBlacklistService {

    @Autowired
    private JwtUtil jwtUtil;

    // Map<token, expiry> — avoids unbounded memory growth
    private final Map<String, Instant> blacklist = new ConcurrentHashMap<>();

    public void blacklist(String token) {
        Instant expiry = jwtUtil.extractExpiry(token);
        blacklist.put(token, expiry);
    }

    public boolean isBlacklisted(String token) {
        Instant expiry = blacklist.get(token);
        if (expiry == null) return false;
        // Auto-evict if already past expiry — token would be rejected by JwtUtil anyway
        if (expiry.isBefore(Instant.now())) {
            blacklist.remove(token);
            return false;
        }
        return true;
    }

    // Hourly sweep to clean up any tokens not yet lazily evicted
    @Scheduled(fixedRate = 3_600_000)
    public void evictExpired() {
        Instant now = Instant.now();
        blacklist.entrySet().removeIf(e -> e.getValue().isBefore(now));
    }
}