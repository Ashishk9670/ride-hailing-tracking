package com.example.ridehailing.security;

import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param jwtSecret HMAC-SHA256 key (at least 32 bytes) shared with whatever issues the tokens
 * @param tokenTtlSeconds lifetime of tokens minted by the dev token endpoint
 * @param devTokenEndpointEnabled exposes POST /api/auth/token, which mints a token for ANY subject and roles
 *     (demo / tests only; production tokens come from a real identity provider)
 */
@ConfigurationProperties(prefix = "ridehailing.auth")
public record AuthProperties(String jwtSecret, long tokenTtlSeconds, boolean devTokenEndpointEnabled) {
    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("ridehailing.auth.jwt-secret must be at least 32 bytes");
        }
        if (tokenTtlSeconds <= 0) tokenTtlSeconds = 3600;
    }
}
