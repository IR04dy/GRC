package com.tss.grc.module.sust.capa.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.repository.CorrectiveActionRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
