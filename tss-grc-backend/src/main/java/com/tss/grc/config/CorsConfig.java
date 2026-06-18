package com.tss.grc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * CORS configuration for the TSS GRC REST API.
 *
 * <p>In <strong>development</strong>, the frontend ({@code tss-grc-ui/}) is served
 * from {@code http://localhost:3000} (live-server) while the API runs on
 * {@code http://localhost:8080}. CORS is required to allow cross-origin requests.
 *
 * <p>In <strong>production</strong>, Nginx serves both the static frontend and
 * proxies {@code /api/} to the backend on the same origin, so CORS is effectively
 * unused. {@code grc.cors.allowed-origins} is set to an empty list in the prod
 * profile to be explicit.
 */
@Configuration
public class CorsConfig {

    @Value("${grc.cors.allowed-origins:http://localhost:3000}")
    private List<String> allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Use allowedOriginPatterns (not allowedOrigins) so entries may contain
        // wildcards — required for dev LAN access (e.g. http://192.168.*:[*])
        // while still permitting credentials (the HttpOnly refresh cookie).
        // Plain, wildcard-free entries still match exactly, so prod behaviour
        // is unchanged.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept", "X-Requested-With"));
        config.setExposedHeaders(List.of("Authorization", "X-Total-Count", "X-Page-Number", "X-Page-Size"));
        config.setAllowCredentials(true);   // Required for HttpOnly refresh-token cookie
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
