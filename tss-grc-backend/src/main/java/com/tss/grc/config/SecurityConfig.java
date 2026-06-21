package com.tss.grc.config;

import com.tss.grc.security.JwtAuthenticationFilter;
import com.tss.grc.security.ModulePermissionEvaluator;
import com.tss.grc.security.PublicEndpointRateLimitFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.access.expression.method.MethodSecurityExpressionHandler;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Spring Security configuration for the TSS GRC REST API.
 *
 * <p>Authentication model:
 * <ul>
 *   <li>Stateless — no HTTP sessions; Spring Security session management set to STATELESS</li>
 *   <li>JWT Bearer tokens — access token (15 min) passed in Authorization header</li>
 *   <li>Refresh tokens — long-lived (7 days), stored as HttpOnly cookie by AuthController</li>
 *   <li>CSRF disabled — not needed for stateless JWT authentication</li>
 * </ul>
 *
 * <p>The frontend (tss-grc-ui/) is a standalone static project served separately.
 * CORS is configured in {@link CorsConfig}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ModulePermissionEvaluator modulePermissionEvaluator;
    private final PublicEndpointRateLimitFilter publicEndpointRateLimitFilter;

    /**
     * Dedicated filter chain for Swagger UI endpoints.
     * Order(1) — evaluated first so swagger paths bypass the restrictive API chain.
     * No CSP restriction: Swagger UI requires inline scripts/styles to render.
     */
    @Bean
    @Order(1)
    public SecurityFilterChain swaggerFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/swagger-ui.html", "/swagger-ui/**",
                             "/v3/api-docs", "/v3/api-docs/**",
                             "/webjars/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    /**
     * SEC-CFG-006: {@link PublicEndpointRateLimitFilter} is a {@code @Component}
     * that extends {@code OncePerRequestFilter}, so Spring Boot would otherwise
     * auto-register it as a servlet filter for {@code /*} in ADDITION to the
     * explicit position it occupies inside the security chain (added via
     * {@code addFilterBefore} below). That double registration would count every
     * public-portal request twice and halve the effective throttle. Disabling the
     * servlet auto-registration leaves it running exactly once — in the security
     * chain, ahead of the JWT filter.
     */
    @Bean
    public FilterRegistrationBean<PublicEndpointRateLimitFilter> publicRateLimitFilterRegistration(
            PublicEndpointRateLimitFilter filter) {
        FilterRegistrationBean<PublicEndpointRateLimitFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    @Order(2)
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // ── Disable CSRF — stateless JWT API does not need it ─────────
            .csrf(AbstractHttpConfigurer::disable)

            // ── Stateless session — no HttpSession created or used ─────────
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // ── Authorization rules ───────────────────────────────────────
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/refresh").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/logout").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // WebSocket handshake — JWT validation happens at the STOMP CONNECT
                // frame via WebSocketJwtInterceptor; HTTP layer just permits the upgrade.
                .requestMatchers("/ws/**").permitAll()
                // Swagger UI — disabled in prod via springdoc.swagger-ui.enabled=false
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/webjars/**").permitAll()
                // DD-82: public case-tracker — anonymous submitters look up their
                // intake by reference code without logging in.
                .requestMatchers(HttpMethod.GET, "/api/v1/intake/track/**").permitAll()
                // Slice 10-D: vendor BCP attestation submission portal — token IS
                // the credential, so these two endpoints are auth-free.
                // SEC-CFG-006: brute-force/enumeration is now throttled per-IP by
                // PublicEndpointRateLimitFilter (wired below) for this and every
                // other public token/portal path; tokens are 122-bit random UUIDs.
                .requestMatchers(HttpMethod.GET,  "/api/v1/tprm/bcp-attestations/by-token/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/tprm/bcp-attestations/by-token/**").permitAll()
                // Sprint 11 — PDPL Article 12 transparency: public list of
                // APPROVED sub-processors with internal fields stripped.
                .requestMatchers(HttpMethod.GET, "/api/v1/privacy/subprocessors/public").permitAll()
                // Sprint 13 — Whistleblower / Ethics Hotline public portal.
                // SAMA CG Art.28 + CMA CG Art.87 require a confidential
                // reporting channel; the submission_token IS the credential
                // for status check + reporter reply. Reporter IP is NOT
                // logged in the audit ledger (see WhistleblowerCaseService).
                .requestMatchers(HttpMethod.POST, "/api/v1/ethics/whistleblower/public/submit").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/v1/ethics/whistleblower/public/status/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/v1/ethics/whistleblower/public/reply/**").permitAll()
                // Sprint 13 — Customer Complaints Management public portal.
                // SAMA Banking Consumer Protection Principles 2021 +
                // CMA Investor Complaints Regulation require an open
                // intake channel; the submission_token is the credential
                // for the public tracking endpoint, which returns a
                // PII-stripped view (no phone/email/national-ID).
                .requestMatchers(HttpMethod.POST, "/api/v1/complaints/public").permitAll()
                .requestMatchers(HttpMethod.GET,  "/api/v1/complaints/public/track/**").permitAll()
                // SEC-FIN-002: belt-and-braces — the SCM sub-controllers
                // (/api/scm/**: contract-clauses, fourth-party, resilience,
                // resilience/tests) are gated per-method with
                // hasPermission('SCM', ...). This path rule is defence-in-depth:
                // every /api/scm/** request must carry an SCM authority (or be a
                // platform admin) so a future endpoint added without an explicit
                // @PreAuthorize cannot silently fall back to "merely logged in".
                .requestMatchers("/api/scm/**").access((authn, ctx) -> {
                    var a = authn.get();
                    boolean ok = a != null && a.isAuthenticated() && a.getAuthorities().stream()
                        .map(org.springframework.security.core.GrantedAuthority::getAuthority)
                        .anyMatch(g -> g.equals("ROLE_PLATFORM_ADMIN")
                                    || g.equals("SCM_READ")
                                    || g.equals("SCM_WRITE")
                                    || g.equals("SCM_DELETE"));
                    return new org.springframework.security.authorization.AuthorizationDecision(ok);
                })
                .anyRequest().authenticated()
            )

            // ── SEC-CFG-006: per-IP throttle on the public token/portal
            //     endpoints, ahead of the JWT filter so anonymous callers are
            //     rate-limited before any authentication work. Non-public paths
            //     pass straight through. Anchored on the same framework filter
            //     (UsernamePasswordAuthenticationFilter) as the JWT filter — a
            //     custom filter class cannot be used as an ordering anchor
            //     because it has no registered order in Spring Security's
            //     FilterComparator. It is registered BEFORE the JWT filter
            //     below, so it stays ahead of it in the resolved chain. ───────
            .addFilterBefore(publicEndpointRateLimitFilter, UsernamePasswordAuthenticationFilter.class)

            // ── JWT filter: validates Bearer token on every request ───────
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)

            // ── Auth-failure semantics ────────────────────────────────────
            // Without explicit handlers, Spring Security returns 403 for
            // BOTH unauthenticated and unauthorized cases — which made the
            // frontend unable to distinguish "your token expired" (should
            // refresh + retry) from "you genuinely can't do that" (give
            // up). Wire proper handlers so:
            //   - missing / invalid / expired JWT → 401 Unauthorized
            //   - authenticated but lacking permission → 403 Forbidden
            // Both write empty bodies — error JSON is handled by the
            // global @ControllerAdvice once requests reach a controller.
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(restAuthenticationEntryPoint())
                .accessDeniedHandler(restAccessDeniedHandler()))

            // ── Security response headers ─────────────────────────────────
            .headers(headers -> headers
                .frameOptions(frame -> frame.deny())
                .contentTypeOptions(content -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .maxAgeInSeconds(31536000)
                    .includeSubDomains(true))
                .referrerPolicy(referrer -> referrer
                    .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                // API-specific CSP — no scripts/styles served from backend
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives("default-src 'none'; frame-ancestors 'none'; form-action 'none'"))
            );

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public MethodSecurityExpressionHandler methodSecurityExpressionHandler() {
        DefaultMethodSecurityExpressionHandler handler = new DefaultMethodSecurityExpressionHandler();
        handler.setPermissionEvaluator(modulePermissionEvaluator);
        return handler;
    }

    /**
     * Returns HTTP 401 (not 403) for missing/invalid/expired JWTs so the
     * frontend can transparently call /api/auth/refresh and retry. The body
     * is intentionally empty — the WWW-Authenticate header tells the
     * caller this is a Bearer-token auth challenge.
     */
    @Bean
    public AuthenticationEntryPoint restAuthenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
        };
    }

    /**
     * Returns HTTP 403 only for cases where the user IS authenticated but
     * lacks the required permission for the endpoint. The frontend treats
     * this as a hard "no" — no refresh attempt, no retry.
     */
    @Bean
    public AccessDeniedHandler restAccessDeniedHandler() {
        return (request, response, accessDeniedException) ->
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
    }
}
