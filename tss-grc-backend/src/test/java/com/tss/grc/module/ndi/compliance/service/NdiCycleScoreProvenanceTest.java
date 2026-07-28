package com.tss.grc.module.ndi.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.entity.NdiAssessmentCycle;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-46 — the NDI cycle compliance score is derived, not declared.
 *
 * <p><b>What was measured.</b> {@code ndi.assessment_cycle.compliance_score} had no setter anywhere
 * in the codebase. It was written only by whatever arrived in
 * {@code NdiAssessmentCycleService.create()}'s request body, because that method saves the bound
 * entity. Four consumers read it — the NDI dashboard, the governance-domain rollup, the NDI report,
 * and {@code NdiSubmissionPackageService}, <b>the package submitted to the regulator</b>. On the dev
 * tenant, cycle 2 asserted <b>68.00</b> while its seven child assessments averaged <b>66.43</b> —
 * so the figure submitted upward disagreed with the assessment it claimed to summarise, and nothing
 * reconciled the two.
 *
 * <p>So this is F2-40's shape: a scalar with no provenance feeding a governance artefact. Unlike
 * F2-40, a derivation exists and can be run, so the score becomes {@code COMPUTED} rather than
 * merely labelled. ⚠ But see {@link #theCycleScoreIsTheMeanOfItsScoredAssessments} — the child rows
 * are seeded values that never passed through {@code scoreFor} either, so the derivation is sound
 * going forward while today's inputs are themselves asserted. This slice fixes the roll-up having
 * <em>no</em> derivation; it does not make the seeded children authoritative.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NdiCycleScoreProvenanceTest {

    private static final Long ORG = 1L;
    private static final Long CYCLE = 5L;

    @Mock NdiComplianceAssessmentRepository repo;
    @Mock NdiAssessmentCycleRepository cycleRepo;
    @Mock NdiFrameworkItemRepository frameworkRepo;
    @Mock com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;
    @Mock com.tss.grc.module.ndi.integration.NdiCrossLinkService crossLink;

    @InjectMocks NdiComplianceAssessmentService service;

    private NdiAssessmentCycle cycle;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG);
        cycle = new NdiAssessmentCycle();
        cycle.setId(CYCLE);
        cycle.setOrganisationId(ORG);
        when(cycleRepo.findById(CYCLE)).thenReturn(Optional.of(cycle));
        when(cycleRepo.save(any(NdiAssessmentCycle.class))).thenAnswer(i -> i.getArgument(0));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void children(String... scores) {
        List<NdiComplianceAssessment> rows = new ArrayList<>();
        for (String s : scores) {
            NdiComplianceAssessment a = new NdiComplianceAssessment();
            a.setOrganisationId(ORG);
            a.setCycleId(CYCLE);
            a.setComplianceScore(s == null ? null : new BigDecimal(s));
            rows.add(a);
        }
        when(repo.findByOrganisationIdAndCycleId(ORG, CYCLE)).thenReturn(rows);
    }

    /**
     * The dev tenant's real cycle-2 children, read from the database rather than invented:
     * 100, 55, 60, 50, 95, 65, 40 → 465 / 7 = <b>66.43</b>, against the 68.00 the cycle asserted.
     *
     * <p>⚠ Worth recording, because it narrows the claim this slice can make: those child values do
     * <b>not</b> match {@link NdiComplianceAssessmentService#scoreFor}'s vocabulary, which yields
     * only 100 / 50 / 0. The seeded rows never passed through {@code applyRules}, so they are
     * asserted numbers too. The derivation below is correct <em>going forward</em> — a row saved
     * through the service gets {@code scoreFor}'s value — but today's 66.43 is the mean of seeded
     * figures, not of computed ones. The defect it fixes is still real: the roll-up had no
     * derivation at all and disagreed with its own children.
     */
    @Test
    void theCycleScoreIsTheMeanOfItsScoredAssessments() {
        children("100", "55", "60", "50", "95", "65", "40");

        NdiAssessmentCycle out = service.recomputeCycleComplianceScore(CYCLE);

        assertThat(out.getComplianceScore()).isEqualByComparingTo("66.43");
        assertThat(out.getScoreSource()).isEqualTo("COMPUTED");
        assertThat(out.getScoreComputedAt()).isNotNull();
    }

    /**
     * {@code scoreFor} returns null for NOT_APPLICABLE, EVIDENCE_REQUIRED and UNDER_REVIEW. Those
     * rows must leave both numerator and denominator alone: an item nobody has assessed yet must not
     * drag the percentage down, and an N/A item is not a failure. This is also how
     * {@code NdiDashboardService} and {@code NdiSubmissionPackageService} already average the column,
     * so a different rule here would make the cycle disagree with the dashboard reading it.
     */
    @Test
    void unscoredRowsAreExcludedFromBothNumeratorAndDenominator() {
        children("100", null, "50", null, null);

        assertThat(service.recomputeCycleComplianceScore(CYCLE).getComplianceScore())
            .as("mean of 100 and 50, not of five rows")
            .isEqualByComparingTo("75.00");
    }

    /**
     * "Nothing assessed yet" and "assessed at zero" are different facts, and the submission package
     * distinguishes them by NULL. Writing 0.00 here would report full non-compliance for a cycle
     * that has not started.
     */
    @Test
    void aCycleWithNoScoredRowsKeepsItsScoreUntouchedRatherThanBeingZeroed() {
        children(null, null);
        cycle.setComplianceScore(null);

        NdiAssessmentCycle out = service.recomputeCycleComplianceScore(CYCLE);

        assertThat(out.getComplianceScore()).isNull();
        assertThat(out.getScoreSource())
            .as("and it stays unlabelled, matching V323's treatment of never-scored cycles")
            .isNull();
    }

    /**
     * The one pre-V323 asserted value is kept by the migration and replaced only by a real
     * recompute — which is a deliberate act, not a migration silently restating a governance figure.
     */
    @Test
    void aRecomputeReplacesAnAssertedLegacyValueAndClaimsIt() {
        cycle.setComplianceScore(new BigDecimal("68.00"));
        cycle.setScoreSource("ASSERTED_LEGACY");
        children("100", "50");

        NdiAssessmentCycle out = service.recomputeCycleComplianceScore(CYCLE);

        assertThat(out.getComplianceScore()).isEqualByComparingTo("75.00");
        assertThat(out.getScoreSource()).isEqualTo("COMPUTED");
    }

    /** Another tenant's cycle id must not be recomputable, even with a valid id. */
    @Test
    void aCycleBelongingToAnotherTenantIsNotTouched() {
        cycle.setOrganisationId(99L);
        children("100");

        assertThat(service.recomputeCycleComplianceScore(CYCLE)).isNull();
    }

    /**
     * F2-47 — an omitted {@code status} used to return <b>500</b>, not 400.
     *
     * <p>{@code COMPLIANCE_STATUSES} is an immutable {@code Set.of(...)}, and
     * {@code Set.of(...).contains(null)} <em>throws</em> rather than returning false. The controller
     * reads {@code body.get("status")}, so any request using a different key — or omitting it —
     * produced a NullPointerException and a 500 with no indication of what was wrong.
     *
     * <p>Found the honest way: my own F2-46 probe sent {@code complianceStatus} instead of
     * {@code status} and I spent three rounds treating the 500 as a platform defect before reading
     * the controller. The bug was real, just not the one I thought I had found.
     */
    @Test
    void anOmittedStatusIsABadRequestNotAServerError() {
        NdiComplianceAssessment row = new NdiComplianceAssessment();
        row.setOrganisationId(ORG);
        row.setCycleId(CYCLE);
        row.setLifecycleStatus("DRAFT");
        when(repo.findByIdAndOrganisationId(7L, ORG)).thenReturn(Optional.of(row));

        org.assertj.core.api.Assertions
            .assertThatThrownBy(() -> service.setStatus(7L, null, null))
            .isInstanceOf(IllegalArgumentException.class)   // -> 400
            .hasMessageContaining("status is required")
            .hasMessageContaining("COMPLIANT");             // names the vocabulary
    }

    @Test
    void aNullCycleIdIsAcceptedQuietly() {
        assertThat(service.recomputeCycleComplianceScore(null))
            .as("assessment rows may not carry a cycle; the caller should not have to null-check")
            .isNull();
    }

    /**
     * The score is server-owned: {@code READ_ONLY} on the entity, so no create or update can supply
     * it however the service is written. It must still SERIALISE — all four consumers read it, and
     * hiding it would be the F2-22a {@code @JsonIgnore} trap.
     */
    @Test
    void theScoreAndItsProvenanceCannotBePostedButAreStillReadable() throws Exception {
        var mapper = new com.fasterxml.jackson.databind.ObjectMapper()
            .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());

        NdiAssessmentCycle bound = mapper.readValue(
            "{\"cycleNameEn\":\"NDI 2027\",\"complianceScore\":99.99,"
            + "\"scoreSource\":\"COMPUTED\",\"scoreComputedAt\":\"2001-01-01T00:00:00\"}",
            NdiAssessmentCycle.class);

        assertThat(bound.getCycleNameEn()).as("ordinary fields still bind").isEqualTo("NDI 2027");
        assertThat(bound.getComplianceScore()).isNull();
        assertThat(bound.getScoreSource()).isNull();
        assertThat(bound.getScoreComputedAt()).isNull();

        cycle.setComplianceScore(new BigDecimal("66.43"));
        cycle.setScoreSource("COMPUTED");
        String json = mapper.writeValueAsString(cycle);
        assertThat(json).contains("66.43").contains("COMPUTED");
    }
}
