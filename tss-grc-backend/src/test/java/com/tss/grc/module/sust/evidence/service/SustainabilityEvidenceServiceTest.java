package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.repository.SustainabilityEvidenceRepository;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-404 — the evidence FILE is certified content, so replacing it on an APPROVED row must
 * re-review (F2-378 shape).
 *
 * <p>{@link SustainabilityEvidenceService#approve} applies a four-eyes / SoD sign-off
 * (approver != owner, SEC-ESG-002) on the evidence <b>document</b>; {@link
 * SustainabilityEvidenceService#supersede} is the dedicated route for replacing that document
 * (a fresh {@code PENDING_REVIEW} row that must be re-approved). {@link
 * SustainabilityEvidenceService#update} blocks only {@code SUPERSEDED}/{@code EXPIRED} — not
 * {@code APPROVED} — and rewrites {@code file_url} in place, so a replaced file left the row
 * {@code APPROVED} with the stale approver, and the ESG / CMA / TCFD / ISAE 3410 assurance
 * register kept surfacing an approved document the reviewer never saw. The fix resets to
 * {@code PENDING_REVIEW} (clearing the stale approval) when the file changed on an APPROVED row.
 *
 * <p>Only {@link #update_fileReplacedOnApprovedRow_resetsToPendingReview()} discriminates; the
 * two guards ({@link #update_metadataChangeOnApprovedRow_staysApproved()}, {@link
 * #update_fileReplacedOnPendingRow_staysPending()}) prove the fix touches nothing else (F2-214,
 * F2-225).
 */
@ExtendWith(MockitoExtension.class)
class SustainabilityEvidenceServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private SustainabilityEvidenceRepository repo;
    @Mock private OrganisationSettingsService settingsService;
    @Mock private AuditLogService audit;
    @Mock private CorrectiveActionService capaService;
    @InjectMocks private SustainabilityEvidenceService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── F2-404 — replacing the reviewed document re-reviews ──────────

    @Test
    void update_fileReplacedOnApprovedRow_resetsToPendingReview() {
        SustainabilityEvidence cur = approved();       // file_url = s3://ev/old.pdf, approved by Khalid
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(SustainabilityEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        SustainabilityEvidence req = new SustainabilityEvidence();
        req.setFileUrl("s3://ev/new.pdf");             // the reviewed document is replaced

        SustainabilityEvidence out = service.update(5L, req);

        // The four-eyes sign-off was on the OLD document, so the new one must be re-reviewed.
        assertThat(out.getApprovalStatus())
            .as("replacing the file of APPROVED evidence must re-enter review")
            .isEqualTo("PENDING_REVIEW");                 // pre-fix: APPROVED
        assertThat(out.getApprovedBy()).isNull();
        assertThat(out.getApprovedAt()).isNull();
    }

    @Test
    void update_metadataChangeOnApprovedRow_staysApproved() {
        SustainabilityEvidence cur = approved();
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(SustainabilityEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        SustainabilityEvidence req = new SustainabilityEvidence();
        req.setTitleEn("Corrected title (typo fix)");   // metadata only, file unchanged

        SustainabilityEvidence out = service.update(5L, req);

        // A benign metadata edit must not invalidate the approval (F2-214).
        assertThat(out.getApprovalStatus()).isEqualTo("APPROVED");
        assertThat(out.getApprovedBy()).isEqualTo("Khalid Al-Otaibi");
        assertThat(out.getApprovedAt()).isNotNull();
    }

    @Test
    void update_fileReplacedOnPendingRow_staysPending() {
        SustainabilityEvidence cur = approved();
        cur.setApprovalStatus("PENDING_REVIEW");        // not yet approved
        cur.setApprovedBy(null);
        cur.setApprovedAt(null);
        when(repo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(SustainabilityEvidence.class))).thenAnswer(inv -> inv.getArgument(0));

        SustainabilityEvidence req = new SustainabilityEvidence();
        req.setFileUrl("s3://ev/new.pdf");

        SustainabilityEvidence out = service.update(5L, req);

        // Scope: a not-yet-approved row has no certification to invalidate — nothing to reset.
        assertThat(out.getApprovalStatus()).isEqualTo("PENDING_REVIEW");
    }

    /** An APPROVED evidence row that passes validate() (title + confidentiality + status). */
    private static SustainabilityEvidence approved() {
        SustainabilityEvidence e = new SustainabilityEvidence();
        e.setId(5L);
        e.setOrganisationId(ORG_ID);
        e.setTitleEn("Scope 3 assurance letter 2025");
        e.setConfidentiality("INTERNAL");
        e.setModuleArea("GHG");
        e.setFileUrl("s3://ev/old.pdf");
        e.setFileName("assurance-2025.pdf");
        e.setApprovalStatus("APPROVED");
        e.setApprovedBy("Khalid Al-Otaibi");
        e.setApprovedAt(LocalDateTime.of(2026, 1, 1, 9, 0));
        return e;
    }
}
