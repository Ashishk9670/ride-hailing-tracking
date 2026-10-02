package com.example.ridehailing.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/** Issues and validates HS256 JWTs carrying {@code sub} and a {@code roles} claim. */
public class JwtTokenService {
    public static final String ISSUER = "ride-hailing-tracking";
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final long ttlSeconds;

    public JwtTokenService(AuthProperties props) {
        SecretKey key = new SecretKeySpec(props.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        this.ttlSeconds = props.tokenTtlSeconds();
    }

    public String issue(String subject, Collection<String> roles) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuer(ISSUER).subject(subject).issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds)).claim("roles", List.copyOf(roles)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    public long ttlSeconds() { return ttlSeconds; }

    public JwtDecoder decoder() { return decoder; }

    /** @throws org.springframework.security.oauth2.jwt.JwtException when invalid or expired */
    public Jwt decode(String token) { return decoder.decode(token); }

    public static List<String> rolesOf(Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles == null ? List.of() : roles;
    }

    /** True when the token carries a role allowed to use the tracking channels (rider or driver). */
    public static boolean canTrack(Jwt jwt) {
        List<String> roles = rolesOf(jwt);
        return roles.contains(Roles.RIDER) || roles.contains(Roles.DRIVER);
    }
}
