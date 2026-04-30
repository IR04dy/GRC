package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.iam.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JWT authentication filter — runs once per request.
 *
 * <p>Extracts the Bearer token from the {@code Authorization} header,
 * validates it via {@link JwtTokenProvider}, loads the {@link UserDetails},
 * and sets the {@link org.springframework.security.core.Authentication} in
 * the {@link SecurityContextHolder}.
 *
 * <p>Also sets the tenant context ({@link TenantContext}) from the JWT
 * {@code org} claim so that all repository queries are automatically scoped
 * to the authenticated user's organisation.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider         tokenProvider;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest  request,
                                    HttpServletResponse response,
                                    FilterChain         filterChain)
            throws ServletException, IOException {
        try {
            String token = extractBearerToken(request);
            if (StringUtils.hasText(token) && tokenProvider.validateToken(token)) {

                Long userId  = tokenProvider.getUserIdFromToken(token);
                Long orgId   = tokenProvider.getOrganisationIdFromToken(token);

                UserDetails userDetails = userDetailsService.loadUserById(userId);

                UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(auth);

                // Set tenant context for downstream repository filtering
                TenantContext.setCurrentTenant(orgId);
            }
        } catch (Exception ex) {
            log.error("Cannot set user authentication from JWT: {}", ex.getMessage());
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            // Always clear tenant context to prevent thread-local leakage in thread pools
            TenantContext.clear();
        }
    }

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
