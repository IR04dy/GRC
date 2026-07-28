package com.tss.grc.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-22b(iv) — an outcome the platform DERIVES is not the caller's to assert.
 *
 * <p>Fourth tranche, and the first one where measurement found live bypasses on both targets
 * rather than defence in depth. Tranches (i)–(iii) covered the tenant anchor, then
 * {@code approvedBy}/{@code approvedAt}, then decision/verification provenance. What is left in the
 * CRIT ranking is the <em>scores and outcomes</em>, and the useful distinction there is not
 * "is it sensitive" but <b>does a derivation already exist elsewhere</b>:
 *
 * <ul>
 *   <li>{@code ClimateRiskOpportunity.riskScore} recomputes as likelihood × impact on <em>both</em>
 *       create and update, and {@code ThirdPartyCyberAssessment.riskScore} recomputes on create and
 *       on every posture change — so binding them is inert. Left alone deliberately; annotating
 *       them would claim a fix where there was no exposure.</li>
 *   <li>{@code NdiComplianceAssessment.complianceScore} looks CRIT but {@code update} copies a
 *       whitelist that excludes it and then calls {@code applyRules}, which derives it. Also inert.
 *       <b>The ranking is entity-level and cannot see a service-side whitelist</b> — which is why
 *       every entry has to be read rather than swept.</li>
 *   <li>The two below had a derivation AND accepted the caller's number. Those are the defects.</li>
 * </ul>
 *
 * <p><b>Both were reproduced live before the fix</b>, against the running stack:
 * a {@code POST /api/privacy/processor-assessments} returned 201 with {@code status=APPROVED},
 * {@code overallOutcome=PASS}, {@code riskScore=1.0} and a forged approver while all eight PDPL
 * Art. 28 controls were still {@code NULL} in the row; and a {@code POST /api/v1/tprm/assessments}
 * created a vendor assessment claiming a {@code REVIEWED} 40-of-40 questionnaire scoring 5% with
 * <b>zero</b> rows in {@code tprm.questionnaire_responses}.
 */
class DerivedOutcomeBindingTest {

    /** Boot's Jackson, not {@code new ObjectMapper()} — see {@code DecisionProvenanceBindingTest}. */
    private static final ObjectMapper MAPPER = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /** One hostile body naming every derived field, so a partial fix cannot pass. */
    private static final String HOSTILE_PROCESSOR = """
        {
          "processorName": "Forged",
          "processingScopeEn": "scope",
          "assessmentType": "INITIAL",
          "overallOutcome": "PASS",
          "riskScore": 1.0,
          "assessedAt": "2001-01-01T00:00:00",
          "assessedBy": 4242,
          "assessorName": "nobody",
          "approvedAt": "2001-01-01T00:00:00",
          "approverName": "the_dpo_who_never_saw_this"
        }
        """;

    private static final String HOSTILE_VENDOR_ASSESSMENT = """
        {
          "vendorId": 1,
          "assessmentDate": "2026-07-28",
          "templateId": 999999,
          "totalQuestions": 40,
          "answeredCount": 40,
          "computedScorePct": 5.00,
          "workflowStatus": "REVIEWED"
        }
        """;

    @Test
    void aProcessorAssessmentCannotDeclareItsOwnOutcome() throws Exception {
        ProcessorAssessment bound = MAPPER.readValue(HOSTILE_PROCESSOR, ProcessorAssessment.class);

        // The fields the body legitimately carries still arrive — this is a create, after all.
        assertThat(bound.getProcessorName()).isEqualTo("Forged");
        assertThat(bound.getAssessmentType()).isEqualTo("INITIAL");

        assertThat(bound.getOverallOutcome())
            .as("PASS/FAIL is derived from the eight Art. 28 controls by scoreAndComplete(); a "
                + "posted outcome is a due-diligence conclusion with no due diligence behind it")
            .isNull();
        assertThat(bound.getRiskScore())
            .as("the risk score is 8.0 on any FAIL and 1.0 + 1.5 per condition otherwise — it is a "
                + "function of the controls, not an opinion")
            .isNull();
    }

    @Test
    void aProcessorAssessmentCannotNameItsOwnAssessorOrApprover() throws Exception {
        ProcessorAssessment bound = MAPPER.readValue(HOSTILE_PROCESSOR, ProcessorAssessment.class);

        assertThat(bound.getApproverName())
            .as("approvedAt was made READ_ONLY by tranche (ii) and the NAME was left bindable — "
                + "the same companion miss as decidedByName in tranche (iii), and the name is the "
                + "half that appears in the register")
            .isNull();
        assertThat(bound.getApprovedAt()).isNull();
        assertThat(bound.getAssessorName()).isNull();
        assertThat(bound.getAssessedBy()).isNull();
        assertThat(bound.getAssessedAt())
            .as("backdating the assessment date would move the next-review clock")
            .isNull();
    }

    @Test
    void aVendorAssessmentCannotAssertAQuestionnaireThatNeverRan() throws Exception {
        VendorAssessment bound = MAPPER.readValue(HOSTILE_VENDOR_ASSESSMENT, VendorAssessment.class);

        assertThat(bound.getVendorId()).as("the legitimate half of the body still binds").isEqualTo(1L);

        assertThat(bound.getTemplateId())
            .as("templateId is set by assign-template and is the marker that the engine owns the "
                + "score — forging it would also make the score look derived")
            .isNull();
        assertThat(bound.getComputedScorePct())
            .as("computedScorePct is the weighted rubric result; asserting it is asserting the "
                + "answers")
            .isNull();
        assertThat(bound.getWorkflowStatus())
            .as("workflowStatus keeps its initial value — REVIEWED must be reached through "
                + "complete + review, not claimed")
            .isEqualTo("NOT_STARTED");
        assertThat(bound.getAnsweredCount())
            .as("40 answered of 40 with no responses on file is the whole defect")
            .isZero();
        assertThat(bound.getTotalQuestions()).isZero();
    }

    /**
     * The counterpart that keeps the fix honest: {@code riskScore}/{@code riskRating} on a vendor
     * assessment are the "legacy free-form" columns and a MANUAL assessment sets them by design.
     * Making them READ_ONLY would have been the F2-40 trap — mistaking a designed assertion for a
     * bypass. The questionnaire-owned case is refused in {@code TprmService} instead, where the
     * service can see whether a template owns the row; Jackson cannot.
     */
    @Test
    void aManualScoreStillBindsBecauseManualAssessmentsAreReal() throws Exception {
        VendorAssessment bound = MAPPER.readValue("""
            {"vendorId": 1, "riskScore": 42.50, "riskRating": "HIGH", "status": "COMPLETED"}
            """, VendorAssessment.class);

        assertThat(bound.getRiskScore()).isEqualByComparingTo(new BigDecimal("42.50"));
        assertThat(bound.getRiskRating()).isEqualTo("HIGH");
        assertThat(bound.getStatus()).isEqualTo("COMPLETED");
    }

    /**
     * ...and every derived field must still SERIALISE. The processor register renders the outcome
     * badge, the risk score, and "assessed on &lt;date&gt; (&lt;assessor&gt;)"; the TPRM register
     * renders the questionnaire progress and the computed percentage. Hiding them with
     * {@code @JsonIgnore} is the trap F2-22a documented, where the mitigation takes the UI down.
     */
    @Test
    void everyDerivedFieldIsStillReadable() throws Exception {
        ProcessorAssessment p = new ProcessorAssessment();
        p.setOverallOutcome("FAIL");
        p.setRiskScore(new BigDecimal("8.0"));
        p.setApproverName("real_dpo");
        p.setAssessorName("real_assessor");
        p.setAssessedBy(7L);
        p.setAssessedAt(LocalDateTime.of(2026, 7, 28, 10, 0));
        assertThat(MAPPER.writeValueAsString(p))
            .contains("\"overallOutcome\":\"FAIL\"")
            .contains("\"riskScore\":8.0")
            .contains("\"approverName\":\"real_dpo\"")
            .contains("\"assessorName\":\"real_assessor\"")
            .contains("\"assessedBy\":7")
            .contains("2026-07-28T10:00");

        VendorAssessment v = new VendorAssessment();
        v.setWorkflowStatus("REVIEWED");
        v.setTemplateId(3L);
        v.setAnsweredCount(40);
        v.setTotalQuestions(40);
        v.setComputedScorePct(new BigDecimal("17.31"));
        assertThat(MAPPER.writeValueAsString(v))
            .contains("\"workflowStatus\":\"REVIEWED\"")
            .contains("\"templateId\":3")
            .contains("\"answeredCount\":40")
            .contains("\"totalQuestions\":40")
            .contains("\"computedScorePct\":17.31");
    }
}
