package com.tss.grc.module.auth.refresh_token.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.auth.refresh_token.entity.RefreshTokenStore;
import com.tss.grc.module.auth.refresh_token.repository.RefreshTokenStoreRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link RefreshTokenStoreService} (Open Issue #002).
 *
 * <p>Uses a hand-rolled in-memory fake of the repository instead of Mockito
 * to keep the test self-contained and fast. Audit + event sinks capture calls
 * so the F7+F8 parity assertions can be made.
 */
class RefreshTokenStoreServiceTest {

    private FakeRepo repo;
    private CapturingAudit audit;
    private CapturingPublisher publisher;
    private RefreshTokenStoreService service;

    @BeforeEach
    void setUp() {
        repo = new FakeRepo();
        audit = new CapturingAudit();
        publisher = new CapturingPublisher();
        service = new RefreshTokenStoreService(repo, audit, publisher);
        TenantContext.setCurrentTenant(7L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── 1. issue ───────────────────────────────────────────────────────

    @Test
    void recordIssuance_persists_row_and_emits_audit_and_event() {
        String jti = service.newJti();
        LocalDateTime expires = LocalDateTime.now().plusDays(7);

        RefreshTokenStore saved = service.recordIssuance(
            42L, "jdoe", jti, expires, "Mozilla/5.0", "10.0.0.1");

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getJti()).isEqualTo(jti);
        assertThat(saved.getOrganisationId()).isEqualTo(7L);
        assertThat(saved.getUserId()).isEqualTo(42L);
        assertThat(saved.getUsername()).isEqualTo("jdoe");
        assertThat(saved.getRevokedAt()).isNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(audit.calls).anyMatch(a -> a.action.equals(RefreshTokenStoreService.EV_ISSUED));
        assertThat(publisher.events).anyMatch(e -> e.triggerEvent().equals(RefreshTokenStoreService.EV_ISSUED));
    }

    // ─── 2. assertValid happy path ──────────────────────────────────────

    @Test
    void assertValid_returns_row_for_active_jti() {
        String jti = service.newJti();
        service.recordIssuance(42L, "jdoe", jti, LocalDateTime.now().plusDays(7), "ua", "ip");

        RefreshTokenStore row = service.assertValid(jti);

        assertThat(row.getJti()).isEqualTo(jti);
    }

    // ─── 3. assertValid throws for revoked ──────────────────────────────

    @Test
    void assertValid_throws_for_revoked_token() {
        String jti = service.newJti();
        service.recordIssuance(42L, "jdoe", jti, LocalDateTime.now().plusDays(7), "ua", "ip");
        service.revoke(jti, 42L, RefreshTokenStoreService.REASON_USER_LOGOUT);

        assertThatThrownBy(() -> service.assertValid(jti))
            .isInstanceOf(RefreshTokenStoreService.InvalidRefreshTokenException.class)
            .hasMessageContaining("revoked");
    }

    // ─── 4. assertValid throws for expired ──────────────────────────────

    @Test
    void assertValid_throws_for_expired_token() {
        String jti = service.newJti();
        service.recordIssuance(42L, "jdoe", jti, LocalDateTime.now().minusMinutes(1), "ua", "ip");

        assertThatThrownBy(() -> service.assertValid(jti))
            .isInstanceOf(RefreshTokenStoreService.InvalidRefreshTokenException.class)
            .hasMessageContaining("expired");
    }

    // ─── 5. assertValid throws for unknown jti ──────────────────────────

    @Test
    void assertValid_throws_for_unknown_jti() {
        assertThatThrownBy(() -> service.assertValid("bogus-jti"))
            .isInstanceOf(RefreshTokenStoreService.InvalidRefreshTokenException.class)
            .hasMessageContaining("not found");
    }

    @Test
    void assertValid_throws_for_blank_jti() {
        assertThatThrownBy(() -> service.assertValid("  "))
            .isInstanceOf(RefreshTokenStoreService.InvalidRefreshTokenException.class)
            .hasMessageContaining("missing");
    }

    // ─── 6. revoke marks the row and emits audit+event ──────────────────

    @Test
    void revoke_sets_revokedAt_and_reason_and_emits_audit_and_event() {
        String jti = service.newJti();
        service.recordIssuance(42L, "jdoe", jti, LocalDateTime.now().plusDays(7), "ua", "ip");

        boolean changed = service.revoke(jti, 99L, RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE);

        assertThat(changed).isTrue();
        RefreshTokenStore row = repo.findByJti(jti).orElseThrow();
        assertThat(row.getRevokedAt()).isNotNull();
        assertThat(row.getRevokedByUserId()).isEqualTo(99L);
        assertThat(row.getRevokeReason()).isEqualTo(RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE);
        assertThat(audit.calls).anyMatch(a -> a.action.equals(RefreshTokenStoreService.EV_REVOKED));
        assertThat(publisher.events).anyMatch(e -> e.triggerEvent().equals(RefreshTokenStoreService.EV_REVOKED));
    }

    @Test
    void revoke_is_idempotent_for_already_revoked_token() {
        String jti = service.newJti();
        service.recordIssuance(42L, "jdoe", jti, LocalDateTime.now().plusDays(7), "ua", "ip");
        service.revoke(jti, 99L, RefreshTokenStoreService.REASON_USER_LOGOUT);
        LocalDateTime first = repo.findByJti(jti).orElseThrow().getRevokedAt();

        boolean changed = service.revoke(jti, 99L, RefreshTokenStoreService.REASON_SUSPECTED_COMPROMISE);

        assertThat(changed).isFalse();
        // Already-revoked rows are not touched a second time.
        assertThat(repo.findByJti(jti).orElseThrow().getRevokedAt()).isEqualTo(first);
        assertThat(repo.findByJti(jti).orElseThrow().getRevokeReason())
            .isEqualTo(RefreshTokenStoreService.REASON_USER_LOGOUT);
    }

    // ─── 7. bulk revoke ─────────────────────────────────────────────────

    @Test
    void revokeAllForUser_revokes_every_active_session_and_emits_bulk_event() {
        service.recordIssuance(42L, "jdoe", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip1");
        service.recordIssuance(42L, "jdoe", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip2");
        service.recordIssuance(42L, "jdoe", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip3");
        // Different user — should not be touched.
        service.recordIssuance(43L, "other", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip");

        int n = service.revokeAllForUser(42L, 1L, RefreshTokenStoreService.REASON_ADMIN_REVOKE_USER);

        assertThat(n).isEqualTo(3);
        assertThat(service.listActive(42L)).isEmpty();
        assertThat(service.listActive(43L)).hasSize(1);
        assertThat(publisher.events).anyMatch(e ->
            e.triggerEvent().equals(RefreshTokenStoreService.EV_BULK_REVOKED));
    }

    // ─── 8. rolling refresh — old jti revoked when new one minted ───────

    @Test
    void rolling_refresh_revokes_previous_jti() {
        String oldJti = service.newJti();
        service.recordIssuance(42L, "jdoe", oldJti, LocalDateTime.now().plusDays(7), "ua", "ip");

        // Simulate rolling refresh: mint new + revoke old.
        String newJti = service.newJti();
        service.recordIssuance(42L, "jdoe", newJti, LocalDateTime.now().plusDays(7), "ua", "ip2");
        service.revoke(oldJti, 42L, RefreshTokenStoreService.REASON_ROLLING_REFRESH);

        assertThatThrownBy(() -> service.assertValid(oldJti))
            .isInstanceOf(RefreshTokenStoreService.InvalidRefreshTokenException.class);
        // The new one still works.
        assertThat(service.assertValid(newJti).getJti()).isEqualTo(newJti);
    }

    // ─── 9. tenant isolation ────────────────────────────────────────────

    @Test
    void listActive_returns_only_current_tenant_sessions() {
        // Tenant 7 (from setUp) — one row.
        service.recordIssuance(42L, "jdoe", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip");

        // Switch tenant context — a fresh row should land in the other tenant.
        TenantContext.setCurrentTenant(99L);
        service.recordIssuance(42L, "jdoe", service.newJti(), LocalDateTime.now().plusDays(7), "ua", "ip");
        List<RefreshTokenStore> sessionsInTenant99 = service.listActive(42L);

        TenantContext.setCurrentTenant(7L);
        List<RefreshTokenStore> sessionsInTenant7 = service.listActive(42L);

        assertThat(sessionsInTenant99).hasSize(1);
        assertThat(sessionsInTenant99.get(0).getOrganisationId()).isEqualTo(99L);
        assertThat(sessionsInTenant7).hasSize(1);
        assertThat(sessionsInTenant7.get(0).getOrganisationId()).isEqualTo(7L);
    }

    // ─── 10. inactive after revocation ──────────────────────────────────

    @Test
    void isActive_returns_false_after_revoke() {
        String jti = service.newJti();
        RefreshTokenStore row = service.recordIssuance(
            42L, "jdoe", jti, LocalDateTime.now().plusDays(7), "ua", "ip");
        assertThat(row.isActive()).isTrue();
        service.revoke(jti, 42L, RefreshTokenStoreService.REASON_USER_LOGOUT);
        RefreshTokenStore revoked = repo.findByJti(jti).orElseThrow();
        assertThat(revoked.isActive()).isFalse();
    }

    @Test
    void recordIssuance_rejects_missing_required_args() {
        LocalDateTime exp = LocalDateTime.now().plusDays(7);
        assertThatThrownBy(() -> service.recordIssuance(null, "u", "j", exp, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordIssuance(1L, "u", null, exp, null, null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.recordIssuance(1L, "u", "j", null, null, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ─── In-memory fakes ────────────────────────────────────────────────

    /**
     * Minimal fake of {@link RefreshTokenStoreRepository} — only the methods
     * actually used by the service. We don't extend JpaRepository because
     * doing so brings in 30+ methods we don't care about; the service holds
     * the interface type so the cast goes through fine.
     */
    static class FakeRepo implements RefreshTokenStoreRepository {
        private final Map<Long, RefreshTokenStore> byId = new HashMap<>();
        private final AtomicLong seq = new AtomicLong();

        @Override public Optional<RefreshTokenStore> findByJti(String jti) {
            return byId.values().stream().filter(r -> jti.equals(r.getJti())).findFirst();
        }
        @Override public List<RefreshTokenStore> findByOrganisationIdAndUserIdAndRevokedAtIsNullOrderByIssuedAtDesc(
                Long orgId, Long userId) {
            return byId.values().stream()
                .filter(r -> orgId.equals(r.getOrganisationId())
                          && userId.equals(r.getUserId())
                          && r.getRevokedAt() == null)
                .sorted((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt()))
                .toList();
        }
        @Override public List<RefreshTokenStore> findByOrganisationIdAndUserIdOrderByIssuedAtDesc(
                Long orgId, Long userId) {
            return byId.values().stream()
                .filter(r -> orgId.equals(r.getOrganisationId()) && userId.equals(r.getUserId()))
                .sorted((a, b) -> b.getIssuedAt().compareTo(a.getIssuedAt()))
                .toList();
        }
        @Override public int revokeAllByUserId(Long userId, LocalDateTime revokedAt, Long revokedBy, String reason) {
            int n = 0;
            for (RefreshTokenStore r : byId.values()) {
                if (userId.equals(r.getUserId()) && r.getRevokedAt() == null) {
                    r.setRevokedAt(revokedAt);
                    r.setRevokedByUserId(revokedBy);
                    r.setRevokeReason(reason);
                    n++;
                }
            }
            return n;
        }

        // ── JpaRepository surface — minimal stubs ─────────────────────
        @Override public <S extends RefreshTokenStore> S save(S entity) {
            if (entity.getId() == null) {
                java.lang.reflect.Field f;
                try {
                    f = RefreshTokenStore.class.getDeclaredField("id");
                    f.setAccessible(true);
                    f.set(entity, seq.incrementAndGet());
                } catch (Exception e) { throw new RuntimeException(e); }
            }
            byId.put(entity.getId(), entity);
            return entity;
        }
        @Override public Optional<RefreshTokenStore> findById(Long id) { return Optional.ofNullable(byId.get(id)); }
        @Override public List<RefreshTokenStore> findAll() { return new ArrayList<>(byId.values()); }
        @Override public long count() { return byId.size(); }
        @Override public void deleteById(Long id) { byId.remove(id); }
        @Override public void delete(RefreshTokenStore entity) { byId.remove(entity.getId()); }
        @Override public void deleteAll() { byId.clear(); }
        // Unused JpaRepository methods — throw to catch any test that strays.
        @Override public List<RefreshTokenStore> findAllById(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public boolean existsById(Long id) { return byId.containsKey(id); }
        @Override public void flush() {}
        @Override public <S extends RefreshTokenStore> S saveAndFlush(S e) { return save(e); }
        @Override public <S extends RefreshTokenStore> List<S> saveAllAndFlush(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch(Iterable<RefreshTokenStore> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllByIdInBatch(Iterable<Long> ids) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllInBatch() { byId.clear(); }
        @Override public RefreshTokenStore getOne(Long id) { return byId.get(id); }
        @Override public RefreshTokenStore getById(Long id) { return byId.get(id); }
        @Override public RefreshTokenStore getReferenceById(Long id) { return byId.get(id); }
        @Override public <S extends RefreshTokenStore> List<S> findAll(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> List<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> ex, org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> long count(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> boolean exists(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore, R> R findBy(org.springframework.data.domain.Example<S> ex, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> f) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> List<S> saveAll(Iterable<S> es) { throw new UnsupportedOperationException(); }
        @Override public List<RefreshTokenStore> findAll(org.springframework.data.domain.Sort s) { throw new UnsupportedOperationException(); }
        @Override public org.springframework.data.domain.Page<RefreshTokenStore> findAll(org.springframework.data.domain.Pageable p) { throw new UnsupportedOperationException(); }
        @Override public <S extends RefreshTokenStore> Optional<S> findOne(org.springframework.data.domain.Example<S> ex) { throw new UnsupportedOperationException(); }
        @Override public void deleteAll(Iterable<? extends RefreshTokenStore> es) { throw new UnsupportedOperationException(); }
        @Override public void deleteAllById(Iterable<? extends Long> ids) { throw new UnsupportedOperationException(); }
    }

    /** Captures audit calls for assertion. */
    static class CapturingAudit extends AuditLogService {
        final List<AuditCall> calls = new ArrayList<>();
        CapturingAudit() { super(null, null); }
        @Override
        public com.tss.grc.module.platform.auditlog.entity.AuditLogEntry log(
                String action, String entityType, Long entityId, String message) {
            calls.add(new AuditCall(action, entityType, entityId, message));
            return new com.tss.grc.module.platform.auditlog.entity.AuditLogEntry();
        }
    }
    record AuditCall(String action, String entityType, Long entityId, String message) {}

    /** Captures application events for assertion. */
    static class CapturingPublisher implements ApplicationEventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publishEvent(Object event) {
            if (event instanceof DomainEvent de) events.add(de);
        }
        @Override public void publishEvent(org.springframework.context.ApplicationEvent event) {}
    }
}
