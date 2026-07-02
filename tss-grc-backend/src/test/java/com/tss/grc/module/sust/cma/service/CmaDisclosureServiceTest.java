package com.tss.grc.module.sust.cma.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.entity.CorrectiveAction;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
