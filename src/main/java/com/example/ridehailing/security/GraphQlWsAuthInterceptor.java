package com.example.ridehailing.security;

import java.util.Map;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.graphql.server.WebSocketGraphQlInterceptor;
import org.springframework.graphql.server.WebSocketGraphQlRequest;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * GraphQL over WebSocket: the client sends {@code connection_init} with payload
 * {@code {"Authorization": "Bearer <jwt>"}}. A missing or invalid token makes Spring close the socket with 4401;
 * operations on a session that never authenticated are refused (fail closed).
 */
@Component
public class GraphQlWsAuthInterceptor implements WebSocketGraphQlInterceptor {
    private static final String AUTHENTICATED = GraphQlWsAuthInterceptor.class.getName() + ".SUBJECT";
    private final JwtTokenService tokens;

    public GraphQlWsAuthInterceptor(JwtTokenService tokens) { this.tokens = tokens; }

    @Override
    public Mono<Object> handleConnectionInitialization(WebSocketSessionInfo info, Map<String, Object> payload) {
        Object header = payload.getOrDefault("Authorization", payload.get("authorization"));
        if (!(header instanceof String h) || !h.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return Mono.error(new BadCredentialsException("Missing bearer token"));
        }
        try {
            Jwt jwt = tokens.decode(h.substring(7).trim());
            if (!JwtTokenService.canTrack(jwt)) return Mono.error(new AccessDeniedException("Role not allowed"));
            info.getAttributes().put(AUTHENTICATED, jwt.getSubject());
            return Mono.empty();
        } catch (JwtException e) {
            return Mono.error(new BadCredentialsException("Invalid token", e));
        }
    }

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        if (request instanceof WebSocketGraphQlRequest ws
                && !ws.getSessionInfo().getAttributes().containsKey(AUTHENTICATED)) {
            return Mono.error(new AccessDeniedException("Unauthenticated GraphQL WebSocket session"));
        }
        return chain.next(request);
    }
}
