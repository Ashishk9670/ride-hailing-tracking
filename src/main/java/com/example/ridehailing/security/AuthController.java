package com.example.ridehailing.security;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Demo token minting. Anyone can ask for any role, so it only exists when
 * {@code ridehailing.auth.dev-token-endpoint-enabled=true}; production must obtain tokens from an IdP.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "ridehailing.auth.dev-token-endpoint-enabled", havingValue = "true")
public class AuthController {
    private final JwtTokenService tokens;

    public AuthController(JwtTokenService tokens) { this.tokens = tokens; }

    public record TokenRequest(@NotBlank String subject, @NotEmpty List<String> roles) {}

    @PostMapping("/token")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> token(@Valid @RequestBody TokenRequest req) {
        if (!Roles.ALL.containsAll(req.roles())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles must be among " + Roles.ALL);
        }
        return Map.of("accessToken", tokens.issue(req.subject(), req.roles()), "tokenType", "Bearer",
                "expiresIn", tokens.ttlSeconds());
    }
}
