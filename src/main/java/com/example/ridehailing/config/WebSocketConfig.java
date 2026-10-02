package com.example.ridehailing.config;

import com.example.ridehailing.security.JwtHandshakeInterceptor;
import com.example.ridehailing.ws.TripWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final TripWebSocketHandler handler;
    private final JwtHandshakeInterceptor auth;

    public WebSocketConfig(TripWebSocketHandler handler, JwtHandshakeInterceptor auth) {
        this.handler = handler;
        this.auth = auth;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/trips/*").addInterceptors(auth).setAllowedOriginPatterns("*");
    }
}
