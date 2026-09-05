package com.tss.grc.module.ndi.submission.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.domain.service.NdiGovernanceDomainService;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import com.tss.grc.module.ndi.gap.repository.NdiCorrectiveActionRepository;
import com.tss.grc.module.ndi.gap.repository.NdiGapRepository;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
import com.tss.grc.module.ndi.opex.repository.NdiOperationalExcellenceKpiRepository;
import com.tss.grc.module.ndi.plan.repository.NdiImprovementPlanRepository;
import com.tss.grc.module.ndi.submission.entity.NdiSubmissionPackage;
import com.tss.grc.module.ndi.submission.repository.NdiSubmissionPackageRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-466 — {@code NdiSubmissionPackageService} had <b>no test of any kind</b>, which is not the
 * same as a path somebody decided was safe (F2-248) — and it is the module's regulator-facing
 * NDMO submission register.
 *
 * <p>The service resolves its cycle four times. {@code get()}, {@code create()} and
 * {@code regenerate()} all name the failure; {@code finalize()} was the fourth and the only bare
 * {@code orElseThrow()}, so a package whose cycle had since been soft-deleted answered an
 * <b>opaque 500</b> — {@link NoSuchElementException} maps to no {@code @ExceptionHandler} — where
 * its two siblings answer a clean 404 naming the cycle. F2-215's two-of-N at N=4, in one class, on
 * one finder.
 *
 * <p><b>No live probe, and that is a calibration rather than a gap</b> (F2-434/F2-456/F2-460): the
 * empty case is exactly what a mocked repository expresses, and the <em>reachability</em> was
 * established from the schema and the routes — {@code NdiAssessmentCycle} carries
 * {@code @Where(is_deleted = 0)} and {@code DELETE /api/ndi/cycles/{id}} soft-deletes. Planting a
 * cycle and a package and then soft-deleting the cycle would write to a database that is to be
 * left exactly as it is.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("F2-466 — finalize() names a missing cycle instead of answering an opaque 500")
class NdiSubmissionPackageFinalizeTest {

    private static final Long ORG      = 1L;
    private static final Long PKG_ID   = 42L;
    private static final Long CYCLE_ID = 77L;

    @Mock private NdiSubmissionPackageRepository packages;
    @Mock private NdiAssessmentCycleRepository cycles;
    @Mock private NdiMaturityAssessmentRepository maturity;
    @Mock private NdiComplianceAssessmentRepository compliance;
    @Mock private NdiOperationalExcellenceKpiRepository opex;
    @Mock private NdiEvidenceItemRepository evidence;
    @Mock private NdiGapRepository gaps;
    @Mock private NdiCorrectiveActionRepository capas;
    @Mock private NdiImprovementPlanRepository plans;
    @Mock private NdiGovernanceDomainService domainService;
    @Mock private ObjectMapper json;
    @Mock private NdiAuditPublisher audit;

    @InjectMocks private NdiSubmissionPackageService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static NdiSubmissionPackage draftPackage() {
        NdiSubmissionPackage p = new NdiSubmissionPackage();
        p.setId(PKG_ID);
        p.setOrganisationId(ORG);
        p.setCycleId(CYCLE_ID);
        p.setVersion(1);
        p.setPackageCode("PKG-2026-v1");
        p.setStatus("DRAFT");
        return p;
    }

    /**
     * The discriminating leg. Pre-fix this threw {@link NoSuchElementException}, which
     * {@code GlobalExceptionHandler} maps nowhere, so the caller received "An unexpected error
     * occurred" with the class stripped in production by SEC-CFG-001.
     */
    @Test
    @DisplayName("a package whose cycle has been soft-deleted gets a 404 naming the cycle")
    void finalize_whenTheCycleIsGone_namesItRatherThanFailingOpaquely() {
        when(packages.findByIdAndOrganisationId(PKG_ID, ORG)).thenReturn(Optional.of(draftPackage()));
        // Every preflight child query is left unstubbed: Mockito answers an empty List, which is
        // exactly what the database returns for a soft-deleted cycle — see the vacuity leg below.
        when(cycles.findByIdAndOrganisationId(CYCLE_ID, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.finalize(PKG_ID))
            .as("the failure has to name the cycle: EntityNotFoundException maps to 404")
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Cycle not found")
            .hasMessageContaining(String.valueOf(CYCLE_ID));

        assertThatThrownBy(() -> service.finalize(PKG_ID))
            .as("...and specifically NOT NoSuchElementException, which no @ExceptionHandler maps, "
                + "so it would reach the caller as an opaque 500 (F2-227/F2-339/F2-348/F2-349/F2-448)")
            .isNotInstanceOf(NoSuchElementException.class);

        verify(packages, never()).save(org.mockito.ArgumentMatchers.any());
    }

    /**
     * Pins the reason the bare {@code orElseThrow()} was reachable rather than masked: the guard
     * standing in front of it cannot refuse a deleted cycle. {@code preflight}'s only
     * <em>blocker</em> is {@code criticalGapsWithoutCapa} — every other finding is a warning — and
     * for a soft-deleted cycle every child query returns empty, so {@code canFinalize} passes
     * <b>vacuously</b>.
     *
     * <p>Asserted so that a future reader who tightens {@code preflight} does not conclude it
     * closed F2-466, and so that one who reads {@code finalize}'s guard chain can see why the line
     * after it is the one that fails.
     */
    @Test
    @DisplayName("preflight passes vacuously when every child register is empty")
    void preflight_forACycleWithNoChildren_passesVacuously() {
        NdiSubmissionPackageService.Preflight pf = service.preflight(CYCLE_ID);

        assertThat(pf.blockers()).as("no blocker can be raised from empty child registers").isEmpty();
        assertThat(pf.canFinalize())
            .as("so the guard in front of the cycle lookup passes for a cycle that is GONE — "
                + "which is what makes that lookup's failure the caller's first symptom")
            .isTrue();
        assertThat(pf.warnings()).isEmpty();
        assertThat(pf.cycleId()).isEqualTo(CYCLE_ID);
    }

    /**
     * The guard leg (F2-225): the refusal must fire for a missing cycle and NOT for a package that
     * is simply in the wrong state, or the fix would be indistinguishable from one that broke
     * {@code finalize} outright.
     */
    @Test
    @DisplayName("a non-DRAFT package is still refused on its status, before the cycle is resolved")
    void finalize_ofANonDraftPackage_stillRefusesOnTheStatus() {
        NdiSubmissionPackage p = draftPackage();
        p.setStatus("READY");
        when(packages.findByIdAndOrganisationId(PKG_ID, ORG)).thenReturn(Optional.of(p));

        assertThatThrownBy(() -> service.finalize(PKG_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Only DRAFT packages can be finalized");

        verify(cycles, never()).findByIdAndOrganisationId(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
    }
}
