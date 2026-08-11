package com.tss.grc.module.ndi.compliance.service;

import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.compliance.entity.NdiComplianceAssessment;
import com.tss.grc.module.ndi.compliance.repository.NdiComplianceAssessmentRepository;
import com.tss.grc.module.ndi.cycle.repository.NdiAssessmentCycleRepository;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import com.tss.grc.module.ndi.integration.NdiCrossLinkService;
import com.tss.grc.common.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-182 — the Rule 3 approval gate on {@code ndi.compliance_assessment}, and the claim about it.
 *
 * <p><b>What F2-182's register row said:</b> a lower-case {@code evidence_required}
 * <em>bypasses an approval gate on an NCA/NDI compliance item — a control bypass, not a display
 * bug.</em> It was the row's own "read this first".
 *
 * <p><b>What reading the code showed.</b> The bypass was <b>not reachable</b>. Every writer of the
 * column is closed: {@code setStatus} refused an unrecognised value with a 400 (F2-47 made sure a
 * missing one was a 400 rather than a 500), {@code update} silently ignored one, and
 * {@code startForCycle} / {@code attachEvidence} / {@code applyRules} all pass literals. The row's
 * "open writer" verdict came from a scan that saw
 * {@code cur.setComplianceStatus(req.getComplianceStatus())} and could not see the
 * {@code COMPLIANCE_STATUSES.contains(...) &&} guard in the very same {@code if}.
 *
 * <p>🔑 <b>Reachability and correctness are separate questions, and getting the first one wrong makes
 * the second look urgent.</b> The residue after the correction is real but narrower: the gate's
 * <b>read</b> was case-sensitive, so a row carrying that casing from any other origin — a data fix, a
 * restore, a future importer — would have walked through. Reachability was the only thing protecting
 * an NCA/NDI approval control, and reachability is a fact about today rather than a property of the
 * control. So the gate is closed anyway, and this class pins both halves: the writers refuse, <b>and</b>
 * the gate holds even against a row no writer could have produced.
 */
@ExtendWith(MockitoExtension.class)
class NdiComplianceStatusVocabularyTest {

    private static final Long ORG_ID = 1L;
    private static final Long ROW_ID = 55L;

    @Mock private NdiComplianceAssessmentRepository repo;
    @Mock private NdiAssessmentCycleRepository cycleRepo;
    @Mock private NdiFrameworkItemRepository frameworkRepo;
    @Mock private NdiAuditPublisher audit;
    @Mock private NdiCrossLinkService crossLink;

    @InjectMocks private NdiComplianceAssessmentService service;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }

    @AfterEach void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static NdiComplianceAssessment row(String complianceStatus, String lifecycleStatus) {
        NdiComplianceAssessment a = new NdiComplianceAssessment();
        a.setId(ROW_ID);
        a.setOrganisationId(ORG_ID);
        a.setCycleId(9L);
        a.setRequirementCode("NDI-2-1-3");
        a.setDomain("GOVERNANCE");
        a.setWeight(BigDecimal.ONE);
        a.setComplianceStatus(complianceStatus);
        a.setLifecycleStatus(lifecycleStatus);
        a.setReviewerName("reviewer_persona");
        return a;
    }

    private void authAs(String username) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(username, "x", java.util.List.of()));
    }

    // ── The gate itself ────────────────────────────────────────────────────────────

    /**
     * ⚠ The leg that justifies the change. This row could not be produced by any API caller — which
     * is precisely why it is the interesting one: it asks whether the control depends on the writers
     * upstream being closed, or holds on its own.
     */
    @Test
    @DisplayName("approve refuses a row flagged evidence-required in ANY casing")
    void theRule3GateHoldsAgainstARowNoWriterCouldHaveProduced() {
        authAs("approver_persona");
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID))
            .thenReturn(Optional.of(row("evidence_required", "UNDER_REVIEW")));

        assertThatThrownBy(() -> service.approve(ROW_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Cannot approve")
            .hasMessageContaining("EVIDENCE_REQUIRED");

        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("and it still refuses the canonical spelling — the gate is not merely case-blind")
    void theGateStillWorksForTheValueItAlwaysCaught() {
        authAs("approver_persona");
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID))
            .thenReturn(Optional.of(row(NdiComplianceAssessmentService.STATUS_EVIDENCE_REQUIRED,
                                        "UNDER_REVIEW")));

        assertThatThrownBy(() -> service.approve(ROW_ID))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Rule 3");
    }

    /**
     * The negative control, and it matters more than it looks: a gate that refused <em>everything</em>
     * would satisfy both tests above. An item genuinely fit for approval must still pass.
     */
    @Test
    @DisplayName("a compliant item with evidence still approves")
    void theGateDoesNotRefuseEverything() {
        authAs("approver_persona");
        NdiComplianceAssessment a = row("COMPLIANT", "UNDER_REVIEW");
        a.setEvidenceRef("EV-2026-0044");           // Rule 3 satisfied, so applyRules leaves it COMPLIANT
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(NdiComplianceAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        // no cycleRepo stub: approve() does not re-derive the cycle score, only setStatus/update do

        NdiComplianceAssessment saved = service.approve(ROW_ID);

        assertThat(saved.getLifecycleStatus()).isEqualTo("APPROVED");
        assertThat(saved.getApproverName()).isEqualTo("approver_persona");
    }

    /**
     * And the SoD control is untouched — worth one leg, because {@code approve} now has two refusal
     * paths and a change to the first must not have shadowed the second.
     */
    @Test
    @DisplayName("the reviewer still cannot approve their own item")
    void segregationOfDutiesSurvivesTheChange() {
        authAs("reviewer_persona");                 // same name as row(...) sets as reviewer
        NdiComplianceAssessment a = row("COMPLIANT", "UNDER_REVIEW");
        a.setEvidenceRef("EV-2026-0044");
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID)).thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.approve(ROW_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Segregation of duties");
    }

    // ── The writers ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("setStatus normalises a casing the column accepts, instead of 400-ing it")
    void setStatusNormalisesRatherThanRefusingALegalValueInTheWrongCase() {
        NdiComplianceAssessment a = row("UNDER_REVIEW", "IN_PROGRESS");
        a.setEvidenceRef("EV-2026-0044");
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID)).thenReturn(Optional.of(a));
        when(repo.save(any(NdiComplianceAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cycleRepo.findById(9L)).thenReturn(Optional.empty());

        NdiComplianceAssessment saved = service.setStatus(ROW_ID, "compliant", null);

        assertThat(saved.getComplianceStatus()).as(
            "before F2-182 this was a 400 for a value ck_ndi_comp_status accepts — the refusal was "
            + "safe but wrong, and an integration had no way to succeed")
            .isEqualTo("COMPLIANT");
        assertThat(saved.getComplianceScore()).isEqualByComparingTo(new BigDecimal("100"));
    }

    @Test
    @DisplayName("a token the column could never hold is still refused, naming it")
    void setStatusStillRefusesAnImpossibleToken() {
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID))
            .thenReturn(Optional.of(row("UNDER_REVIEW", "IN_PROGRESS")));

        assertThatThrownBy(() -> service.setStatus(ROW_ID, "MOSTLY_COMPLIANT", null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("MOSTLY_COMPLIANT");
        verify(repo, never()).save(any());
    }

    /**
     * F2-47's fix, re-pinned: an omitted status used to reach {@code Set.of(...).contains(null)}, which
     * throws NPE and surfaced as a 500. {@code require} must keep answering 400.
     */
    @Test
    @DisplayName("an omitted status is a 400, not a 500 — F2-47 must survive the conversion")
    void anOmittedStatusIsStillA400() {
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID))
            .thenReturn(Optional.of(row("UNDER_REVIEW", "IN_PROGRESS")));

        assertThatThrownBy(() -> service.setStatus(ROW_ID, null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("required");
    }

    /**
     * ⚙ The one deliberate behaviour change in this slice, pinned so it is a decision rather than a
     * side effect. {@code update} used to <b>silently ignore</b> an unrecognised status: the caller
     * supplied the field, received 200, and the status had not moved. That is the same class of defect
     * as the {@code safe*} helpers F2-179 deleted — an operation that reports success while declining
     * to do what was asked. It is now a 400 naming the value.
     */
    @Test
    @DisplayName("update no longer accepts-then-ignores an unrecognised status")
    void updateRefusesInsteadOfSilentlyDroppingTheField() {
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID))
            .thenReturn(Optional.of(row("UNDER_REVIEW", "IN_PROGRESS")));

        NdiComplianceAssessment patch = new NdiComplianceAssessment();
        patch.setComplianceStatus("nearly_compliant");

        assertThatThrownBy(() -> service.update(ROW_ID, patch))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("nearly_compliant");
        verify(repo, never()).save(any());
    }

    @Test
    @DisplayName("update normalises a recognised casing")
    void updateNormalisesARecognisedCasing() {
        NdiComplianceAssessment cur = row("UNDER_REVIEW", "IN_PROGRESS");
        cur.setEvidenceRef("EV-2026-0044");
        when(repo.findByIdAndOrganisationId(ROW_ID, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(NdiComplianceAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(cycleRepo.findById(9L)).thenReturn(Optional.empty());

        NdiComplianceAssessment patch = new NdiComplianceAssessment();
        patch.setComplianceStatus("non_compliant");
        NdiComplianceAssessment saved = service.update(ROW_ID, patch);

        assertThat(saved.getComplianceStatus()).isEqualTo("NON_COMPLIANT");
        assertThat(saved.getNeedsGap()).as("Rule 4 keys off the normalised value").isTrue();
    }
}
