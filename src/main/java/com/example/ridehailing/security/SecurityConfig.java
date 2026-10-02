package com.example.ridehailing.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Stateless JWT resource server. REST is authorised per role here; browser WebSocket upgrades cannot send an
 * Authorization header, so {@code /ws/trips/*} and the GraphQL-over-WebSocket upgrade are authenticated by
 * {@link JwtHandshakeInterceptor} and {@link GraphQlWsAuthInterceptor} instead.
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    JwtTokenService jwtTokenService(AuthProperties props) { return new JwtTokenService(props); }

    @Bean
    JwtDecoder jwtDecoder(JwtTokenService tokens) { return tokens.decoder(); }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("roles");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtAuthenticationConverter converter) throws Exception {
        RequestMatcher graphQlWebSocketUpgrade = (HttpServletRequest r) ->
                "/graphql".equals(r.getRequestURI()) && "websocket".equalsIgnoreCase(r.getHeader("Upgrade"));
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/", "/index.html", "/favicon.ico", "/error").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/health", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/token").permitAll()
                        .requestMatchers("/ws/trips/**").permitAll()
                        .requestMatchers(graphQlWebSocketUpgrade).permitAll()
                        .requestMatchers("/api/simulator/**").hasRole(Roles.ADMIN)
                        .requestMatchers(HttpMethod.POST, "/api/rides").hasRole(Roles.RIDER)
                        .requestMatchers(HttpMethod.POST, "/api/drivers/*/location").hasRole(Roles.DRIVER)
                        .requestMatchers("/api/trips/**", "/api/drivers/nearby", "/graphql")
                        .hasAnyRole(Roles.RIDER, Roles.DRIVER)
                        .anyRequest().denyAll())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)));
        return http.build();
    }
}
