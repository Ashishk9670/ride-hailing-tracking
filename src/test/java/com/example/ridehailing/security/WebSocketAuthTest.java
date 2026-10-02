package com.example.ridehailing.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.graphql.server.WebSocketSessionInfo;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import reactor.test.StepVerifier;

class WebSocketAuthTest {
    JwtTokenService tokens = new JwtTokenService(new AuthProperties("test-secret-test-secret-test-secret-0123", 60, true));

    boolean handshake(String query, String authHeader, Map<String, Object> attrs, MockHttpServletResponse res) {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/ws/trips/t1");
        req.setQueryString(query);
        if (query != null) req.setParameter("x", "y");
        if (authHeader != null) req.addHeader("Authorization", authHeader);
        req.setRequestURI("/ws/trips/t1");
        req.setServerName("localhost");
        // URI used by the interceptor must include the query string
        ServletServerHttpRequest sreq = new ServletServerHttpRequest(req) {
            @Override public URI getURI() { return URI.create("http://localhost/ws/trips/t1" + (query == null ? "" : "?" + query)); }
        };
        return new JwtHandshakeInterceptor(tokens).beforeHandshake(sreq, new ServletServerHttpResponse(res), null, attrs);
    }

    @Test
    void acceptsValidTokenInQueryOrHeader() {
        String t = tokens.issue("alice", List.of("RIDER"));
        Map<String, Object> attrs = new HashMap<>();
        assertThat(handshake("access_token=" + t, null, attrs, new MockHttpServletResponse())).isTrue();
        assertThat(attrs).containsEntry("sub", "alice");
        assertThat(handshake(null, "Bearer " + t, new HashMap<>(), new MockHttpServletResponse())).isTrue();
    }

    @Test
    void rejectsMissingBadOrUnprivilegedTokens() {
        MockHttpServletResponse res = new MockHttpServletResponse();
        assertThat(handshake(null, null, new HashMap<>(), res)).isFalse();
        assertThat(res.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(handshake("access_token=garbage", null, new HashMap<>(), new MockHttpServletResponse())).isFalse();
        String adminOnly = tokens.issue("ops", List.of("ADMIN"));
        assertThat(handshake("access_token=" + adminOnly, null, new HashMap<>(), new MockHttpServletResponse())).isFalse();
    }

    private static WebSocketSessionInfo session(Map<String, Object> attrs) {
        WebSocketSessionInfo info = org.mockito.Mockito.mock(WebSocketSessionInfo.class);
        org.mockito.Mockito.when(info.getAttributes()).thenReturn(attrs);
        return info;
    }

    @Test
    void graphQlConnectionInitRequiresValidBearer() {
        GraphQlWsAuthInterceptor i = new GraphQlWsAuthInterceptor(tokens);
        String t = tokens.issue("alice", List.of("DRIVER"));
        StepVerifier.create(i.handleConnectionInitialization(session(new HashMap<>()), Map.of("Authorization", "Bearer " + t)))
                .verifyComplete();
        StepVerifier.create(i.handleConnectionInitialization(session(new HashMap<>()), Map.of()))
                .expectError(BadCredentialsException.class).verify();
        StepVerifier.create(i.handleConnectionInitialization(session(new HashMap<>()), Map.of("Authorization", "Bearer nope")))
                .expectError(BadCredentialsException.class).verify();
        String admin = tokens.issue("ops", List.of("ADMIN"));
        StepVerifier.create(i.handleConnectionInitialization(session(new HashMap<>()), Map.of("Authorization", "Bearer " + admin)))
                .expectError(AccessDeniedException.class).verify();
    }
}
