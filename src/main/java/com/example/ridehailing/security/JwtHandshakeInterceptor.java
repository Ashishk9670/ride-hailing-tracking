package com.example.ridehailing.security;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Authenticates the raw WebSocket upgrade. Browsers cannot set headers on {@code new WebSocket(url)}, so the
 * bearer token is accepted as {@code ?access_token=} (or an Authorization header for non-browser clients).
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {
    private final JwtTokenService tokens;

    public JwtHandshakeInterceptor(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Map<String, Object> attributes) {
        String token = request.getHeaders().getFirst("Authorization");
        if (token != null && token.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = token.substring(7).trim();
        } else {
            token = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("access_token");
        }
        try {
            if (token != null) {
                Jwt jwt = tokens.decode(token);
                if (JwtTokenService.canTrack(jwt)) {
                    attributes.put("sub", jwt.getSubject());
                    return true;
                }
            }
        } catch (JwtException ignored) {
            // fall through to 401
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                               Exception exception) {}
}
