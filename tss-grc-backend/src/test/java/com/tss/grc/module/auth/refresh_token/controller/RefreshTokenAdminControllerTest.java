package com.tss.grc.module.auth.refresh_token.controller;

import com.tss.grc.module.auth.refresh_token.entity.RefreshTokenStore;
import com.tss.grc.module.auth.refresh_token.service.RefreshTokenStoreService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RefreshTokenAdminController} (Open Issue #002).
 *
 * <p>Verifies the wiring between the service and the HTTP endpoints. Auth is
 * tested at the @PreAuthorize boundary, but here we exercise the controller
 * directly with a mock {@code RefreshTokenStoreService} since the auth
 * resolution is Spring's responsibility (other tests cover the
 * security-rule wiring).
 */
class RefreshTokenAdminControllerTest {

    private RefreshTokenStoreService service;
    private RefreshTokenAdminController controller;

    @BeforeEach
    void setUp() {
        service = mock(RefreshTokenStoreService.class);
        controller = new RefreshTokenAdminController(service);
    }

    @Test
    void listMine_returns_active_sessions_for_current_user() {
        RefreshTokenStore row = new RefreshTokenStore();
        row.setId(101L);
        row.setUserId(42L);
        row.setJti("jti-1");
        row.setIssuedAt(LocalDateTime.now().minusHours(2));
        row.setExpiresAt(LocalDateTime.now().plusDays(7));
        row.setIpAddress("10.0.0.1");
        row.setUserAgent("Mozilla/5.0");
        when(service.listActive(42L)).thenReturn(List.of(row));

        Authentication auth = authFor(42L, "jdoe", "RISK_MANAGER");
        ResponseEntity<List<Map<String, Object>>> resp = controller.listMine(auth);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).hasSize(1);
        Map<String, Object> dto = resp.getBody().get(0);
        assertThat(dto.get("id")).isEqualTo(101L);
        assertThat(dto.get("ipAddress")).isEqualTo("10.0.0.1");
        assertThat(dto.get("active")).isEqualTo(true);
    }

    @Test
    void revokeOne_revokes_owned_session() {
        RefreshTokenStore row = new RefreshTokenStore();
        row.setId(101L);
        row.setUserId(42L);
        row.setJti("jti-1");
        row.setExpiresAt(LocalDateTime.now().plusDays(7));
        when(service.listAll(42L)).thenReturn(List.of(row));

        Authentication auth = authFor(42L, "jdoe", "RISK_MANAGER");
        ResponseEntity<?> resp = controller.revokeOne(101L, auth);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        verify(service, times(1)).revoke("jti-1", 42L, RefreshTokenStoreService.REASON_USER_LOGOUT);
    }

    @Test
    void revokeOne_returns_404_when_session_belongs_to_another_user() {
        // Service returns empty for current user, so the controller
        // can't find the requested session id and must 404.
        when(service.listAll(42L)).thenReturn(List.of());

        Authentication auth = authFor(42L, "jdoe", "RISK_MANAGER");
        ResponseEntity<?> resp = controller.revokeOne(999L, auth);

        assertThat(resp.getStatusCode().value()).isEqualTo(404);
        verify(service, never()).revoke(anyString(), anyLong(), anyString());
    }

    @Test
    void revokeAllMine_calls_service_revokeAllForUser_with_user_logout_reason() {
        when(service.revokeAllForUser(42L, 42L, RefreshTokenStoreService.REASON_USER_LOGOUT))
            .thenReturn(3);

        Authentication auth = authFor(42L, "jdoe", "RISK_MANAGER");
        ResponseEntity<Map<String, Object>> resp = controller.revokeAllMine(auth);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("revoked")).isEqualTo(3);
        verify(service).revokeAllForUser(42L, 42L, RefreshTokenStoreService.REASON_USER_LOGOUT);
    }

    @Test
    void revokeUser_uses_suspected_compromise_reason() {
        when(service.revokeAllForUser(7L, 1L, RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE))
            .thenReturn(5);

        Authentication auth = authFor(1L, "admin", "PLATFORM_ADMIN");
        ResponseEntity<Map<String, Object>> resp = controller.revokeUser(7L, auth);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody().get("userId")).isEqualTo(7L);
        assertThat(resp.getBody().get("revokedBy")).isEqualTo(1L);
        assertThat(resp.getBody().get("revoked")).isEqualTo(5);
        assertThat(resp.getBody().get("reason"))
            .isEqualTo(RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE);
    }

    // ── Helpers ───────────────────────────────────────────────────────

    private static Authentication authFor(Long userId, String username, String role) {
        GrcUserDetails p = new GrcUserDetails(
            userId, 7L, username, "x", true, true,
            List.of(new SimpleGrantedAuthority("ROLE_" + role)));
        return new UsernamePasswordAuthenticationToken(p, "x", p.getAuthorities());
    }
}
