package com.tss.grc.module.sust.cma.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CmaDisclosureService — locks V60 Rules 5 and 6.
 *
 * <p><b>Rule 5</b> — PARTIAL or NOT_DISCLOSED requires a gap
 * description (English OR Arabic; either suffices for the audit trail).
 *
 * <p><b>Rule 6</b> — DISCLOSED/PARTIAL with empty {@code evidenceRef}
 * is auto-rewritten to EVIDENCE_REQUIRED on save. Attaching evidence to
 * an EVIDENCE_REQUIRED row flips it back to UNDER_REVIEW.
 */
@ExtendWith(MockitoExtension.class)
class CmaDisclosureServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private CmaDisclosureRepository repo;
    @Mock private AuditLogService audit;
    @Mock private CorrectiveActionService capaService;
    @InjectMocks private CmaDisclosureService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── Rule 5 — gap required for PARTIAL / NOT_DISCLOSED ──────────

    @Test
    void transitionToPartial_refusesWithoutGap() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("Some evidence");   // evidence present → not Rule 6
        d.setGapDescriptionEn(null);
        d.setGapDescriptionAr(null);
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.transition(5L, "PARTIAL"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule 5");
    }

    @Test
    void transitionToNotDisclosed_refusesWithoutGap() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("Some evidence");
        d.setGapDescriptionEn(null);
        d.setGapDescriptionAr(null);
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.transition(5L, "NOT_DISCLOSED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule 5");
    }

    @Test
    void transitionToPartial_acceptsWithEnglishGap() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("ev");
        d.setGapDescriptionEn("Scope 3 coverage at 67%");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.transition(5L, "PARTIAL");

        assertThat(after.getDisclosureStatus()).isEqualTo("PARTIAL");
        assertThat(after.getLastReviewedAt()).isNotNull();
    }

    @Test
    void transitionToPartial_acceptsWithArabicGap() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("ev");
        d.setGapDescriptionAr("تغطية النطاق 3 عند 67%");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.transition(5L, "PARTIAL");

        assertThat(after.getDisclosureStatus()).isEqualTo("PARTIAL");
    }

    // ── S-D — PARTIAL / NOT_DISCLOSED auto-opens a corrective action ──

    @Test
    void transitionToPartial_opensAutoCapa_andBackLinks() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("ev");                        // has evidence → stays PARTIAL (not Rule 6)
        d.setGapDescriptionEn("Scope 3 coverage at 67%");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CorrectiveAction capa = new CorrectiveAction();
        capa.setId(99L);
        when(capaService.autoOpen(eq("CMA_DISCLOSURE"), eq("5"), any(), any(), any(), eq("HIGH")))
            .thenReturn(capa);

        CmaDisclosure after = service.transition(5L, "PARTIAL");

        assertThat(after.getDisclosureStatus()).isEqualTo("PARTIAL");
        // A disclosure gap fires the auto-CAPA (source CMA_DISCLOSURE / id 5) …
        verify(capaService).autoOpen(eq("CMA_DISCLOSURE"), eq("5"), any(), any(), any(), eq("HIGH"));
        // … and the disclosure is back-linked to it for the matrix UI.
        assertThat(after.getLinkedCapaId()).isEqualTo(99L);
    }

    // ── Rule 6 — DISCLOSED/PARTIAL with no evidence → EVIDENCE_REQUIRED

    @Test
    void transitionToDisclosed_withoutEvidence_autoFlipsToEvidenceRequired() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef(null);              // no evidence → Rule 6
        d.setGapDescriptionEn(null);
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.transition(5L, "DISCLOSED");

        // Service rewrites to EVIDENCE_REQUIRED instead of allowing
        // "Disclosed but no evidence" to silently sit in the matrix.
        assertThat(after.getDisclosureStatus()).isEqualTo("EVIDENCE_REQUIRED");
    }

    @Test
    void transitionToDisclosed_withEvidence_staysAsDisclosed() {
        CmaDisclosure d = baseDisclosure();
        d.setEvidenceRef("Annual Report 2025 §3.4");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.transition(5L, "DISCLOSED");

        assertThat(after.getDisclosureStatus()).isEqualTo("DISCLOSED");
    }

    @Test
    void attachEvidence_flipsEvidenceRequiredBackToUnderReview() {
        CmaDisclosure d = baseDisclosure();
        d.setDisclosureStatus("EVIDENCE_REQUIRED");
        d.setEvidenceRef(null);
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.attachEvidence(5L, "Annual Report 2025 §3.4", "Sara");

        assertThat(after.getEvidenceRef()).isEqualTo("Annual Report 2025 §3.4");
        assertThat(after.getDisclosureStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void attachEvidence_refusesEmptyRef() {
        // attachEvidence validates evidenceRef BEFORE touching the repo,
        // so we deliberately do not stub findByIdAndOrganisationId here —
        // Mockito strict mode would flag it as an unused stubbing.
        assertThatThrownBy(() -> service.attachEvidence(5L, "  ", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evidence_ref");
    }

    // ── Readiness aggregate ─────────────────────────────────────────

    @Test
    void readiness_excludesNotApplicableFromDenominator() {
        // 5 disclosures: 2 DISCLOSED, 1 PARTIAL, 1 NOT_APPLICABLE, 1 UNDER_REVIEW.
        // Applicable = 5 - 1 = 4; disclosed = 2; readiness = 50%.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025)).thenReturn(java.util.List.of(
            disclosure("DISCLOSED", "ev"),
            disclosure("DISCLOSED", "ev"),
            disclosure("PARTIAL", "ev"),
            disclosure("NOT_APPLICABLE", null),
            disclosure("UNDER_REVIEW", null)));

        var out = service.readiness((short) 2025);

        assertThat(out).containsEntry("total", 5);
        assertThat(out).containsEntry("applicable", 4);
        assertThat(out).containsEntry("readinessPct", 50);
    }

    // ── S-K — principal-bound approval + SoD + approved-lock ────────

    @Test
    void approve_refusesSelfApprovalByOwner() {
        CmaDisclosure d = baseDisclosure();
        d.setDisclosureStatus("DISCLOSED");
        d.setEvidenceOwner("Sara Al-Zahrani");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        // The owner cannot approve their own disclosure (separation of duties).
        assertThatThrownBy(() -> service.approve(5L, user("Sara Al-Zahrani")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("separation of duties");
    }

    @Test
    void approve_stampsApprovedByFromPrincipal_notBody() {
        CmaDisclosure d = baseDisclosure();
        d.setDisclosureStatus("DISCLOSED");
        d.setEvidenceOwner("Sara Al-Zahrani");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        CmaDisclosure after = service.approve(5L, user("Khalid Al-Otaibi"));

        assertThat(after.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(after.getApprovedBy()).isEqualTo("Khalid Al-Otaibi");   // from principal
        assertThat(after.getApprovedAt()).isNotNull();
    }

    @Test
    void approve_isTerminal_cannotReapprove() {
        CmaDisclosure d = baseDisclosure();
        d.setDisclosureStatus("DISCLOSED");
        d.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.approve(5L, user("Khalid")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("already approved");
    }

    @Test
    void update_onApprovedDisclosure_isLocked() {
        CmaDisclosure d = baseDisclosure();
        d.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        CmaDisclosure patch = new CmaDisclosure();
        patch.setTitleEn("tampered");
        assertThatThrownBy(() -> service.update(5L, patch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");
    }

    @Test
    void transition_onApprovedDisclosure_isLocked() {
        CmaDisclosure d = baseDisclosure();
        d.setApprovalStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(d));

        assertThatThrownBy(() -> service.transition(5L, "PARTIAL"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");
    }

    // ── S-K — official CMA Art.91–94 library seed ───────────────────

    @Test
    void seedOfficialLibrary_seeds11_whenYearEmpty() {
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2099)).thenReturn(List.of());
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(inv -> inv.getArgument(0));

        int n = service.seedOfficialLibrary((short) 2099);

        assertThat(n).isEqualTo(11);                                  // the canonical 11 disclosures
        verify(repo, times(11)).save(any(CmaDisclosure.class));
    }

    @Test
    void seedOfficialLibrary_isIdempotent_whenYearAlreadySeeded() {
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(baseDisclosure()));

        assertThat(service.seedOfficialLibrary((short) 2025)).isEqualTo(0);
        verify(repo, never()).save(any());
    }

    /** An authenticated principal whose username is the approver identity. */
    private static GrcUserDetails user(String username) {
        return new GrcUserDetails(7L, ORG_ID, username, "x", true, true, List.of());
    }

    // ── F2-231 — an echo is not a review, and a change is ────────────
    //
    // ⚠ These exist because the mutation harness predicted the gap and then proved it: reverting the
    // TCFD half was KILLED by its own tests, and reverting THIS half — the service TcfdItemService's
    // javadoc says it mirrors — SURVIVED the whole build. 🔑 Fixing two services and testing one
    // leaves the untested one free to drift back, which is how the pair diverged in the first place
    // (the SEC-ESG-003 scrub that lived here and not there).

    @Test
    void update_echoingTheSameStatusIsNotAReview() {
        CmaDisclosure cur = disclosure("UNDER_REVIEW", "EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(i -> i.getArgument(0));

        CmaDisclosure req = new CmaDisclosure();
        req.setTitleEn("Test disclosure (reworded)");
        req.setDisclosureStatus("UNDER_REVIEW");          // the echo the ESG tab always sends

        CmaDisclosure saved = service.update(5L, req);

        assertThat(saved.getLastReviewedAt()).isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(eq("UPDATE"), eq("CmaDisclosure"), any(), anyString());
    }

    /**
     * F2-297 — and neither is an echo in a DIFFERENT CASE. This test exists because the mutation
     * harness predicted its absence: F2-231 fixed this pair in both services and tested only the
     * TCFD half, so reverting the CMA half SURVIVED. {@code sust.cma_disclosure.disclosure_status}
     * collates case-insensitively, so {@code 'under_review'} IS the stored value; a bare
     * {@code equals} read it as a change, and {@code markReviewed} would then stamp a CMA
     * Article 92 review that never happened while the audit row recorded a TRANSITION that never
     * occurred. Latent (0 non-canonical values in dev or prod), so the data is what made it
     * harmless and not the code (F2-182).
     */
    @Test
    void update_echoingTheSameStatusInADifferentCaseIsStillNotAReview() {
        CmaDisclosure cur = disclosure("UNDER_REVIEW", "EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(i -> i.getArgument(0));

        CmaDisclosure req = new CmaDisclosure();
        req.setTitleEn("Test disclosure (reworded)");
        req.setDisclosureStatus("under_review");          // the same value, differently spelled

        CmaDisclosure saved = service.update(5L, req);

        assertThat(saved.getLastReviewedAt())
            .as("a case variant of the stored status is the stored status, so no review may be "
                + "manufactured and the clock must not move")
            .isEqualTo(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(eq("UPDATE"), eq("CmaDisclosure"), any(), anyString());
    }

    @Test
    void update_changingTheStatusStampsTheReviewClockAndLogsATransition() {
        CmaDisclosure cur = disclosure("UNDER_REVIEW", "EV-1");
        cur.setLastReviewedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(CmaDisclosure.class))).thenAnswer(i -> i.getArgument(0));

        CmaDisclosure req = new CmaDisclosure();
        req.setDisclosureStatus("DISCLOSED");

        CmaDisclosure saved = service.update(5L, req);

        assertThat(saved.getDisclosureStatus()).isEqualTo("DISCLOSED");
        assertThat(saved.getLastReviewedAt()).isAfter(LocalDateTime.of(2026, 1, 1, 9, 0));
        verify(audit).log(eq("TRANSITION"), eq("CmaDisclosure"), any(),
                          contains("UNDER_REVIEW -> DISCLOSED"));
    }

    // ── Helpers ─────────────────────────────────────────────────────
    private static CmaDisclosure baseDisclosure() {
        CmaDisclosure d = new CmaDisclosure();
        d.setId(5L);
        d.setOrganisationId(ORG_ID);
        d.setReportingYear((short) 2025);
        d.setArticle("91");
        d.setClause("(a)");
        d.setTitleEn("Test disclosure");
        d.setDisclosureStatus("UNDER_REVIEW");
        return d;
    }

    private static CmaDisclosure disclosure(String status, String evidence) {
        CmaDisclosure d = baseDisclosure();
        d.setDisclosureStatus(status);
        d.setEvidenceRef(evidence);
        return d;
    }
}
