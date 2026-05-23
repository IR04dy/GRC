package com.tss.grc.module.platform.push;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.DispatchPayload;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.RegisterRequest;
import com.tss.grc.module.platform.push.dto.PushSubscriptionDtos.Response;
import com.tss.grc.module.platform.push.entity.WebPushSubscription;
import com.tss.grc.module.platform.push.repository.WebPushSubscriptionRepository;
import com.tss.grc.module.platform.push.service.WebPushService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Slice 9-F — WebPushService unit tests.
 *
 * <ul>
 *   <li>register happy path — new row, audit + F8 event</li>
 *   <li>register idempotent — same (user × endpoint) updates instead of duplicating</li>
 *   <li>unregister soft-deletes, audits, emits F8 event</li>
 *   <li>unregister rejects subscriptions owned by another user</li>
 *   <li>list returns only current user's subscriptions</li>
 *   <li>tenant isolation — service refuses if no tenant in context</li>
 *   <li>dispatch fan-out — touches all matching enabled subscriptions and emits a summary event</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class WebPushServiceTest {

    private static final Long ORG_ID  = 73L;
    private static final Long USER_ID = 4242L;
    private static final Long OTHER_USER_ID = 99L;

    @Mock private WebPushSubscriptionRepository repo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private WebPushService service;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Authenticate as our test user — service pulls userId off the SecurityContext.
        var principal = new GrcUserDetails(USER_ID, ORG_ID, "commander",
            "x", true, true, List.of(new SimpleGrantedAuthority("ROLE_CRISIS_COMMANDER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        // Every save echoes the entity back with an id stamped on if missing.
        when(repo.save(any(WebPushSubscription.class))).thenAnswer(inv -> {
            WebPushSubscription s = inv.getArgument(0);
            if (s.getId() == null) s.setId(101L);
            return s;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static RegisterRequest req(String endpoint, String label) {
        return new RegisterRequest(endpoint, "p256-key", "auth-key", label);
    }

    // ─── register ───────────────────────────────────────────────

    @Test
    void register_happyPath_createsRowAndEmitsEvent() {
        when(repo.findByOrganisationIdAndUserIdAndEndpoint(ORG_ID, USER_ID, "https://push.example/abc"))
            .thenReturn(Optional.empty());

        Response out = service.register(req("https://push.example/abc", "iPhone 15"));

        assertThat(out.id()).isEqualTo(101L);
        assertThat(out.deviceLabel()).isEqualTo("iPhone 15");
        assertThat(out.enabled()).isTrue();
        verify(repo, times(1)).save(any(WebPushSubscription.class));
        verify(audit, atLeastOnce()).log(eq("CREATE"), eq("WebPushSubscription"), eq(101L), anyString());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("WEB_PUSH_REGISTERED");
        assertThat(cap.getValue().payload().get("isNew")).isEqualTo(true);
    }

    @Test
    void register_sameEndpointTwice_updatesInsteadOfDuplicating() {
        // 1st call: insert
        when(repo.findByOrganisationIdAndUserIdAndEndpoint(ORG_ID, USER_ID, "https://push.example/abc"))
            .thenReturn(Optional.empty())
            // 2nd call: existing row returned (simulated by re-stubbing)
            .thenReturn(Optional.of(existingSub(101L, "https://push.example/abc")));

        service.register(req("https://push.example/abc", "iPhone 15"));
        Response out = service.register(req("https://push.example/abc", "iPhone 15 — re-registered"));

        assertThat(out.id()).isEqualTo(101L);
        assertThat(out.deviceLabel()).isEqualTo("iPhone 15 — re-registered");
        // 2 saves total (1 insert + 1 update) — never two inserts.
        verify(repo, times(2)).save(any(WebPushSubscription.class));
        verify(audit, atLeastOnce()).log(eq("UPDATE"), eq("WebPushSubscription"), eq(101L), anyString());
    }

    @Test
    void register_emptyEndpoint_rejected() {
        assertThatThrownBy(() -> service.register(new RegisterRequest("", "k", "a", null)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("endpoint");
    }

    // ─── unregister ─────────────────────────────────────────────

    @Test
    void unregister_softDeletesAndEmitsEvent() {
        WebPushSubscription existing = existingSub(101L, "https://push.example/abc");
        when(repo.findByIdAndOrganisationId(101L, ORG_ID)).thenReturn(Optional.of(existing));

        service.unregister(101L);

        ArgumentCaptor<WebPushSubscription> cap = ArgumentCaptor.forClass(WebPushSubscription.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getIsDeleted()).isTrue();
        assertThat(cap.getValue().getEnabled()).isFalse();
        verify(audit, atLeastOnce()).log(eq("ARCHIVE"), eq("WebPushSubscription"), eq(101L), anyString());

        ArgumentCaptor<DomainEvent> evt = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(evt.capture());
        assertThat(evt.getValue().triggerEvent()).isEqualTo("WEB_PUSH_UNREGISTERED");
    }

    @Test
    void unregister_otherUsersSubscription_rejected() {
        WebPushSubscription owned = existingSub(202L, "https://push.example/foreign");
        owned.setUserId(OTHER_USER_ID);
        when(repo.findByIdAndOrganisationId(202L, ORG_ID)).thenReturn(Optional.of(owned));

        assertThatThrownBy(() -> service.unregister(202L))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
        verify(repo, never()).save(any(WebPushSubscription.class));
    }

    // ─── list ───────────────────────────────────────────────────

    @Test
    void listForCurrentUser_filtersByOrgAndUser() {
        WebPushSubscription a = existingSub(101L, "https://push.example/a");
        WebPushSubscription b = existingSub(102L, "https://push.example/b");
        when(repo.findByOrganisationIdAndUserIdOrderByCreatedAtDesc(ORG_ID, USER_ID))
            .thenReturn(List.of(b, a));

        List<Response> out = service.listForCurrentUser();
        assertThat(out).hasSize(2);
        assertThat(out).extracting(Response::id).containsExactly(102L, 101L);
    }

    // ─── tenant isolation ──────────────────────────────────────

    @Test
    void register_withoutTenant_rejected() {
        TenantContext.clear();
        assertThatThrownBy(() -> service.register(req("https://push.example/abc", "iPhone")))
            .isInstanceOf(IllegalStateException.class);
    }

    // ─── dispatch stub ─────────────────────────────────────────

    @Test
    void dispatchToUserIds_touchesAllMatchingAndEmitsSummaryEvent() {
        WebPushSubscription d1 = existingSub(101L, "https://push.example/a");
        WebPushSubscription d2 = existingSub(102L, "https://push.example/b");
        when(repo.findByOrganisationIdAndUserIdInAndEnabled(eq(ORG_ID), eq(List.of(USER_ID, OTHER_USER_ID)), eq(Boolean.TRUE)))
            .thenReturn(List.of(d1, d2));

        int n = service.dispatchToUserIds(
            List.of(USER_ID, OTHER_USER_ID),
            new DispatchPayload("CRIS-2026-0007", "أزمة سيبرانية", "Cyber crisis", "CRITICAL", "/crisis/mobile"));

        assertThat(n).isEqualTo(2);
        verify(repo, times(2)).save(any(WebPushSubscription.class));
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, atLeastOnce()).publishEvent(cap.capture());
        assertThat(cap.getAllValues()).extracting(DomainEvent::triggerEvent).contains("WEB_PUSH_DISPATCHED");
    }

    @Test
    void dispatchToUserIds_emptyList_isNoOp() {
        int n = service.dispatchToUserIds(List.of(), new DispatchPayload("X", "أ", "x", "INFO", "/x"));
        assertThat(n).isEqualTo(0);
        verify(repo, never()).save(any());
        verify(publisher, never()).publishEvent(any());
    }

    // ─── Helpers ────────────────────────────────────────────────

    private static WebPushSubscription existingSub(Long id, String endpoint) {
        WebPushSubscription s = new WebPushSubscription();
        s.setId(id);
        s.setOrganisationId(ORG_ID);
        s.setUserId(USER_ID);
        s.setEndpoint(endpoint);
        s.setP256dhKey("p256-key");
        s.setAuthKey("auth-key");
        s.setEnabled(Boolean.TRUE);
        s.setIsDeleted(Boolean.FALSE);
        return s;
    }
}
