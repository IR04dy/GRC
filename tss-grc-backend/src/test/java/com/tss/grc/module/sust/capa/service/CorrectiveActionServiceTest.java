package com.tss.grc.module.sust.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.service.NotificationService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CorrectiveActionService — locks V58 Rules A / B / C.
 *
 * <p>Rule A (SEC-ESG-002) — the verifier is the authenticated principal,
 * never a client-supplied string, and must differ from the owner
 * (separation of duties; case-insensitive trim-aware comparison against
 * the stored owner name / email). Attribution ({@code verifiedBy}) is
 * stamped from the principal so it cannot be forged.
 *
 * <p>Rule B — transition to COMPLETED requires {@code evidenceRef}.
 *
 * <p>Rule C — CLOSED and CANCELLED are terminal — no transitions out,
 * and {@link CorrectiveActionService#update(Long, CorrectiveAction)}
 * refuses to modify terminal-state rows.
 */
@ExtendWith(MockitoExtension.class)
class CorrectiveActionServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private CorrectiveActionRepository repo;
    @Mock private OrganisationSettingsService settingsService;
    @Mock private AuditLogService audit;
    @Mock private NotificationService notifications;
    @InjectMocks private CorrectiveActionService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── Rule A — verifier (the authenticated principal) ≠ owner ─────

    @Test
    void verify_refusesWhenPrincipalEqualsOwner() {
        CorrectiveAction a = action("COMPLETED", "Sara Al-Zahrani");
        a.setEvidenceRef("ev.pdf");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        // The authenticated caller IS the owner — self-verification is refused.
        assertThatThrownBy(() -> service.transition(3L, "VERIFIED", user("Sara Al-Zahrani")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule A");
    }

    @Test
    void verify_caseInsensitiveOwnerMatch_stillRefuses() {
        CorrectiveAction a = action("COMPLETED", "Sara Al-Zahrani");
        a.setEvidenceRef("ev.pdf");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        // Different casing + whitespace on the principal still counts as same person.
        assertThatThrownBy(() -> service.transition(3L, "VERIFIED", user("  sara al-zahrani  ")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void verify_refusesWhenPrincipalEqualsOwnerEmail() {
        CorrectiveAction a = action("COMPLETED", "Sara Al-Zahrani");
        a.setOwnerEmail("sara@tss4it.com");
        a.setEvidenceRef("ev.pdf");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        // SoD also blocks the owner identified by email.
        assertThatThrownBy(() -> service.transition(3L, "VERIFIED", user("sara@tss4it.com")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule A");
    }

    @Test
    void verify_stampsPrincipalAsVerifier_andAcceptsDifferentVerifier() {
        CorrectiveAction a = action("COMPLETED", "Sara Al-Zahrani");
        a.setEvidenceRef("ev.pdf");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrectiveAction after = service.transition(3L, "VERIFIED", user("Khalid Al-Otaibi"));

        assertThat(after.getStatus()).isEqualTo("VERIFIED");
        // verifiedBy is taken from the authenticated principal, not the body.
        assertThat(after.getVerifiedBy()).isEqualTo("Khalid Al-Otaibi");
        assertThat(after.getVerifiedAt()).isNotNull();
    }

    @Test
    void verify_refusesWhenUnauthenticated() {
        CorrectiveAction a = action("COMPLETED", "Sara");
        a.setEvidenceRef("ev.pdf");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        // No principal → cannot verify (attribution would be unattributable).
        assertThatThrownBy(() -> service.transition(3L, "VERIFIED", (GrcUserDetails) null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Rule B — COMPLETED requires evidence ───────────────────────

    @Test
    void complete_refusesWithoutEvidence() {
        CorrectiveAction a = action("IN_PROGRESS", "Sara");
        a.setEvidenceRef(null);
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(3L, "COMPLETED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule B");
    }

    @Test
    void complete_acceptsWithEvidence() {
        CorrectiveAction a = action("IN_PROGRESS", "Sara");
        a.setEvidenceRef("Q1-rebind.xlsx");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrectiveAction after = service.transition(3L, "COMPLETED");

        assertThat(after.getStatus()).isEqualTo("COMPLETED");
        assertThat(after.getCompletedAt()).isNotNull();
    }

    // ── Rule C — terminal states ────────────────────────────────────

    @Test
    void transition_fromClosed_isIllegal() {
        CorrectiveAction a = action("CLOSED", "Sara");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(3L, "IN_PROGRESS"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_fromCancelled_isIllegal() {
        CorrectiveAction a = action("CANCELLED", "Sara");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transition(3L, "VERIFIED", user("Khalid")))
            .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void update_refusesOnTerminalState() {
        CorrectiveAction a = action("CLOSED", "Sara");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));

        CorrectiveAction patch = new CorrectiveAction();
        patch.setTitleEn("new title");

        assertThatThrownBy(() -> service.update(3L, patch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CLOSED");
    }

    // ── Lifecycle plumbing — start sets startedAt, close sets closedAt

    @Test
    void start_setsStartedAt() {
        CorrectiveAction a = action("OPEN", "Sara");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrectiveAction after = service.transition(3L, "IN_PROGRESS");

        assertThat(after.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(after.getStartedAt()).isNotNull();
    }

    @Test
    void close_fromVerified_setsClosedAt() {
        CorrectiveAction a = action("VERIFIED", "Sara");
        when(repo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrectiveAction after = service.transition(3L, "CLOSED");

        assertThat(after.getStatus()).isEqualTo("CLOSED");
        assertThat(after.getClosedAt()).isNotNull();
    }

    // ── S-B — aggregate due-soon windows read from settings ─────────

    @Test
    void aggregate_dueSoonWindows_respectConfiguredDays() {
        LocalDate today = LocalDate.now();
        when(repo.findByOrganisationIdAndStatusIn(eq(ORG_ID), any())).thenReturn(List.of(
            dued(today.minusDays(1)),   // overdue
            dued(today.plusDays(3)),    // within 7d + 30d
            dued(today.plusDays(15)),   // within 30d, outside 7d
            dued(today.plusDays(60))));  // outside 30d

        // Defaults — warning 7d, executive-escalation 30d.
        stubCapaWindows(7, 30);
        Map<String, Object> agg = service.aggregate();
        assertThat(agg).containsEntry("overdue", 1);
        assertThat(agg).containsEntry("dueSoon", 2);         // +3d, +15d
        assertThat(agg).containsEntry("dueSoonWarning", 1);  // +3d only

        // Widen both windows → both counts grow (the settings drive behaviour).
        stubCapaWindows(20, 90);
        Map<String, Object> agg2 = service.aggregate();
        assertThat(agg2).containsEntry("overdue", 1);
        assertThat(agg2).containsEntry("dueSoon", 3);         // +3d, +15d, +60d
        assertThat(agg2).containsEntry("dueSoonWarning", 2);  // +3d, +15d
    }

    private void stubCapaWindows(int warnDays, int execDays) {
        OrganisationSettings s = new OrganisationSettings();
        s.setCapaOverdueWarningDays(warnDays);
        s.setCapaExecutiveEscalationDays(execDays);
        when(settingsService.getOrCreate()).thenReturn(s);
    }

    private static CorrectiveAction dued(LocalDate target) {
        CorrectiveAction a = action("OPEN", "Sara");
        a.setTargetDate(target);
        return a;
    }

    // ── S-D — auto-CAPA idempotency + dedup (mirrors AR-EVD-GAP) ────

    @Test
    void autoOpen_createsOpenCapa_whenNoneExists() {
        when(repo.findByOrganisationIdAndSourceTypeAndSourceRef(ORG_ID, "CMA_DISCLOSURE", "5"))
            .thenReturn(List.of());
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> {
            CorrectiveAction c = inv.getArgument(0);
            c.setId(42L);
            return c;
        });
        stubCapaWindows(7, 30);   // executive-escalation window drives the due date

        CorrectiveAction ca = service.autoOpen("CMA_DISCLOSURE", "5",
            "CMA Art. 91 disclosure gap (PARTIAL)", "عربي", "desc", "HIGH");

        assertThat(ca.getStatus()).isEqualTo("OPEN");
        assertThat(ca.getSourceType()).isEqualTo("CMA_DISCLOSURE");
        assertThat(ca.getSourceRef()).isEqualTo("5");
        assertThat(ca.getPriority()).isEqualTo("HIGH");
        assertThat(ca.getTargetDate()).isEqualTo(LocalDate.now().plusDays(30));
        verify(audit).log(eq("AUTO_CREATE"), eq("CorrectiveAction"), any(), any());
        // S-N — a fresh auto-CAPA notifies the sustainability officers.
        verify(notifications).broadcastToRole(eq(ORG_ID), eq("SUSTAINABILITY_OFFICER"),
            any(), any(), any(), eq("SUST_CAPA"), eq("CorrectiveAction"), any());
    }

    @Test
    void autoOpen_isIdempotent_whenNonTerminalCapaAlreadyTracksTheSource() {
        CorrectiveAction existing = action("IN_PROGRESS", "owner");
        existing.setSourceType("CMA_DISCLOSURE");
        existing.setSourceRef("5");
        when(repo.findByOrganisationIdAndSourceTypeAndSourceRef(ORG_ID, "CMA_DISCLOSURE", "5"))
            .thenReturn(List.of(existing));

        CorrectiveAction ca = service.autoOpen("CMA_DISCLOSURE", "5", "t", null, "d", "HIGH");

        // Returns the existing CAPA and never creates a duplicate.
        assertThat(ca).isSameAs(existing);
        verify(repo, never()).save(any());
        // No new CAPA → no notification on the dedup no-op path.
        verify(notifications, never()).broadcastToRole(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void autoOpen_createsFresh_whenPriorCapaForSourceIsClosed() {
        // A CLOSED/CANCELLED CAPA no longer "covers" the gap — a re-surfaced gap
        // re-opens a fresh one (uses the new EVIDENCE source type from V251).
        CorrectiveAction closed = action("CLOSED", "owner");
        closed.setSourceType("EVIDENCE");
        closed.setSourceRef("12");
        when(repo.findByOrganisationIdAndSourceTypeAndSourceRef(ORG_ID, "EVIDENCE", "12"))
            .thenReturn(List.of(closed));
        when(repo.save(any(CorrectiveAction.class))).thenAnswer(inv -> inv.getArgument(0));
        stubCapaWindows(7, 30);

        CorrectiveAction ca = service.autoOpen("EVIDENCE", "12", "Rejected evidence", null, "d", "HIGH");

        assertThat(ca.getStatus()).isEqualTo("OPEN");
        assertThat(ca).isNotSameAs(closed);
        verify(repo).save(any(CorrectiveAction.class));
    }

    @Test
    void autoOpen_rejectsUnknownSourceType() {
        assertThatThrownBy(() -> service.autoOpen("WIDGET", "1", "t", null, "d", "HIGH"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceType");
    }

    // ── Helper ──────────────────────────────────────────────────────
    private static CorrectiveAction action(String status, String owner) {
        CorrectiveAction a = new CorrectiveAction();
        a.setId(3L);
        a.setOrganisationId(ORG_ID);
        a.setStatus(status);
        a.setOwnerName(owner);
        a.setPriority("HIGH");
        a.setSourceType("MANUAL");
        a.setTitleEn("Test action");
        return a;
    }

    /** An authenticated principal whose username is the verifier identity. */
    private static GrcUserDetails user(String username) {
        return new GrcUserDetails(7L, ORG_ID, username, "x", true, true, List.of());
    }
}
