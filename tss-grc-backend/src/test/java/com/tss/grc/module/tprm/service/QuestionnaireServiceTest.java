package com.tss.grc.module.tprm.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.QuestionnaireQuestion;
import com.tss.grc.module.tprm.entity.QuestionnaireResponse;
import com.tss.grc.module.tprm.entity.QuestionnaireTemplate;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.QuestionnaireQuestionRepository;
import com.tss.grc.module.tprm.repository.QuestionnaireResponseRepository;
import com.tss.grc.module.tprm.repository.QuestionnaireTemplateRepository;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * QuestionnaireService — V81 scoring + lifecycle rule lock.
 *
 * <ul>
 *   <li>Templates: question set freezes once ACTIVE (add/edit/delete
 *       refused); cannot activate an empty template; transitions
 *       DRAFT→ACTIVE→ARCHIVED only.</li>
 *   <li>Responses: scored_points + weighted_points computed from the
 *       question's answer type / weight / invert_score flag.</li>
 *   <li>Aggregate: posture% = Σ(scored·weight)/Σ(max·weight)·100,
 *       risk% = 100 − posture%, band derived from template thresholds.</li>
 *   <li>NA responses skipped from numerator/denominator and require a
 *       justification.</li>
 *   <li>Complete refused while any mandatory question is unanswered.</li>
 *   <li>Reviewed only allowed from COMPLETED.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class QuestionnaireServiceTest {

    private static final Long ORG_ID    = 7L;
    private static final Long TPL_ID    = 50L;
    private static final Long VA_ID     = 200L;
    private static final Long VENDOR_ID = 99L;

    @Mock private QuestionnaireTemplateRepository templates;
    @Mock private QuestionnaireQuestionRepository questions;
    @Mock private QuestionnaireResponseRepository responses;
    @Mock private VendorAssessmentRepository assessments;
    @Mock private VendorRepository vendorRepo;
    @InjectMocks private QuestionnaireService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** Stub the tenant guard (SEC-TPRM-002): the assessment's vendor is in the caller's org. */
    private void vendorInCallerOrg() {
        Vendor v = new Vendor();
        v.setId(VENDOR_ID);
        v.setOrganisationId(ORG_ID);
        when(vendorRepo.findByIdAndOrganisationId(VENDOR_ID, ORG_ID)).thenReturn(Optional.of(v));
    }

    private static QuestionnaireTemplate template(String status) {
        QuestionnaireTemplate t = new QuestionnaireTemplate();
        t.setId(TPL_ID);
        t.setOrganisationId(ORG_ID);
        t.setTemplateCode("CYBER-DD");
        t.setVersion("1.0");
        t.setNameEn("Cyber Due Diligence");
        t.setCategory("CYBER");
        t.setThresholdLowMax(30);
        t.setThresholdMediumMax(60);
        t.setThresholdHighMax(80);
        t.setStatus(status);
        return t;
    }

    private static QuestionnaireQuestion question(Long id, String code, String type, double weight,
                                                  boolean mandatory, boolean invert) {
        QuestionnaireQuestion q = new QuestionnaireQuestion();
        q.setId(id);
        q.setOrganisationId(ORG_ID);
        q.setTemplateId(TPL_ID);
        q.setSectionCode("SEC");
        q.setQuestionCode(code);
        q.setQuestionEn("Q " + code);
        q.setAnswerType(type);
        q.setWeight(BigDecimal.valueOf(weight));
        q.setMandatory(mandatory);
        q.setInvertScore(invert);
        q.setSequenceOrder(id == null ? 1 : id.intValue());
        return q;
    }

    private static VendorAssessment vendorAssessment() {
        VendorAssessment va = new VendorAssessment();
        va.setId(VA_ID);
        va.setVendorId(99L);
        va.setAssessmentDate(LocalDate.now());
        va.setTemplateId(TPL_ID);
        va.setTotalQuestions(2);
        va.setAnsweredCount(0);
        va.setWorkflowStatus("IN_PROGRESS");
        return va;
    }

    // ─── Template lifecycle ───────────────────────────────────────

    @Test void activate_emptyTemplateRefused() {
        QuestionnaireTemplate t = template("DRAFT");
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(t));
        when(questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of());

        assertThatThrownBy(() -> service.transitionTemplate(TPL_ID, "ACTIVE"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("no questions");
    }

    // ─── F2-398: template threshold-ordering guard ────────────────

    /** Discriminating: createTemplate with inverted thresholds (low 80 > medium 60 > high 30) is
     *  rejected. Pre-fix (guard call removed) the inverted set reaches templates.save. */
    @Test void createTemplate_rejectsInvertedThresholds() {
        QuestionnaireTemplate t = template("DRAFT");
        t.setThresholdLowMax(80);
        t.setThresholdMediumMax(60);
        t.setThresholdHighMax(30);
        assertThatThrownBy(() -> service.createTemplate(t))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-decreasing");
    }

    /** Discriminating (F2-292 — validate the MERGED row): a DRAFT partial update that sets ONLY low
     *  (90) inverts it against the stored medium (60) and is rejected — proving the guard reads the
     *  merged entity, not the request (whose medium/high are null). */
    @Test void updateTemplate_rejectsPartialUpdateThatInvertsAgainstStoredThreshold() {
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(template("DRAFT")));
        QuestionnaireTemplate body = new QuestionnaireTemplate();
        body.setThresholdLowMax(90);   // stored is 30/60/80 → merged 90/60/80 inverts
        assertThatThrownBy(() -> service.updateTemplate(TPL_ID, body))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("non-decreasing");
    }

    /** Guard (non-discriminating): non-decreasing thresholds pass — including a degenerate-but-coherent
     *  equal pair (30/30/80, an empty MEDIUM band) that the non-strict rule accepts (F2-214), and a
     *  null threshold (the entity defaults it at persist). */
    @Test void validateThresholdOrder_acceptsNonDecreasingAndNullThresholds() {
        assertThatCode(() -> {
            QuestionnaireService.validateThresholdOrder(30, 60, 80);
            QuestionnaireService.validateThresholdOrder(30, 30, 80);
            QuestionnaireService.validateThresholdOrder(null, 60, 80);
        }).doesNotThrowAnyException();
    }

    @Test void activate_stampsActivatedAt() {
        QuestionnaireTemplate t = template("DRAFT");
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(t));
        when(questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(question(1L, "Q-1", "BOOLEAN", 1.0, true, false)));
        when(templates.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireTemplate out = service.transitionTemplate(TPL_ID, "ACTIVE");
        assertThat(out.getStatus()).isEqualTo("ACTIVE");
        assertThat(out.getActivatedAt()).isNotNull();
    }

    @Test void illegalTemplateTransitionRefused() {
        QuestionnaireTemplate t = template("ARCHIVED");
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(t));

        // F2-303 — 409. ARCHIVED is terminal, so the allowed set is empty and the message says
        // so; the old wording ("Illegal template transition") named neither a status nor a target.
        assertThatThrownBy(() -> service.transitionTemplate(TPL_ID, "DRAFT"))
            .isInstanceOf(IllegalTransitionException.class)
            .hasMessageContaining("Illegal transition")
            .hasMessageContaining("allowed: []");
    }

    @Test void addQuestion_toActiveTemplateRefused() {
        QuestionnaireTemplate t = template("ACTIVE");
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(t));

        QuestionnaireQuestion q = question(null, "Q-NEW", "BOOLEAN", 1.0, true, false);
        q.setId(null);

        assertThatThrownBy(() -> service.createQuestion(TPL_ID, q))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE template");
    }

    // ─── Assignment ───────────────────────────────────────────────

    @Test void assignTemplate_onlyAllowedFromActive() {
        VendorAssessment va = vendorAssessment(); va.setTemplateId(null);
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(template("DRAFT")));

        assertThatThrownBy(() -> service.assignTemplate(VA_ID, TPL_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ACTIVE template");
    }

    @Test void assignTemplate_initialisesTotalsAndStatus() {
        VendorAssessment va = vendorAssessment(); va.setTemplateId(null);
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(template("ACTIVE")));
        when(questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(
                question(1L, "Q-1", "SCALE_1_5", 1.0, true, false),
                question(2L, "Q-2", "BOOLEAN",   1.0, true, false)));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorAssessment out = service.assignTemplate(VA_ID, TPL_ID);
        assertThat(out.getTemplateId()).isEqualTo(TPL_ID);
        assertThat(out.getTotalQuestions()).isEqualTo(2);
        assertThat(out.getAnsweredCount()).isEqualTo(0);
        assertThat(out.getWorkflowStatus()).isEqualTo("IN_PROGRESS");
    }

    // ─── Scoring math ─────────────────────────────────────────────

    @Test void upsertResponse_computesScoreAndRating() {
        VendorAssessment va = vendorAssessment();
        QuestionnaireQuestion q1 = question(1L, "Q-1", "SCALE_1_5", 1.0, true, false);
        QuestionnaireQuestion q2 = question(2L, "Q-2", "BOOLEAN",   1.0, true, false);

        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(questions.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(q1));
        when(questions.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(q2));
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(template("ACTIVE")));
        when(responses.findByOrganisationIdAndVendorAssessmentIdAndQuestionId(ORG_ID, VA_ID, 1L))
            .thenReturn(Optional.empty());

        // Mutable list backing the post-save aggregate.
        List<QuestionnaireResponse> stored = new ArrayList<>();
        when(responses.save(any())).thenAnswer(inv -> {
            QuestionnaireResponse r = inv.getArgument(0);
            if (r.getId() == null) r.setId((long) (stored.size() + 1));
            // replace any existing for same questionId
            stored.removeIf(x -> x.getQuestionId().equals(r.getQuestionId()));
            stored.add(r);
            return r;
        });
        when(responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(ORG_ID, VA_ID))
            .thenAnswer(inv -> List.copyOf(stored));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireResponse body1 = new QuestionnaireResponse();
        body1.setQuestionId(1L);
        body1.setAnswerValue("5");
        body1.setNotApplicable(false);
        service.upsertResponse(VA_ID, body1, "alice");

        // After 1 answer: posture = 5/5 × 100 = 100% → risk = 0% → LOW
        assertThat(va.getComputedScorePct()).isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(va.getRiskRating()).isEqualTo("LOW");

        // Second answer: BOOLEAN = NO → 0/1
        when(responses.findByOrganisationIdAndVendorAssessmentIdAndQuestionId(ORG_ID, VA_ID, 2L))
            .thenReturn(Optional.empty());
        QuestionnaireResponse body2 = new QuestionnaireResponse();
        body2.setQuestionId(2L);
        body2.setAnswerValue("NO");
        body2.setNotApplicable(false);
        service.upsertResponse(VA_ID, body2, "alice");

        // Posture = (5 + 0) / (5 + 1) = 83.33%; risk = 100 − 83.33 = 16.67 → LOW
        assertThat(va.getComputedScorePct()).isEqualByComparingTo(new BigDecimal("16.67"));
        assertThat(va.getRiskRating()).isEqualTo("LOW");
        assertThat(va.getAnsweredCount()).isEqualTo(2);
    }

    @Test void invertScore_flipsContribution() {
        VendorAssessment va = vendorAssessment(); va.setTotalQuestions(1);
        // Inverted Boolean: YES (=1) becomes 0/1 effectively
        QuestionnaireQuestion q = question(1L, "Q-1", "BOOLEAN", 1.0, true, true);

        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(questions.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(q));
        when(templates.findByIdAndOrganisationId(TPL_ID, ORG_ID)).thenReturn(Optional.of(template("ACTIVE")));
        when(responses.findByOrganisationIdAndVendorAssessmentIdAndQuestionId(ORG_ID, VA_ID, 1L))
            .thenReturn(Optional.empty());

        List<QuestionnaireResponse> stored = new ArrayList<>();
        when(responses.save(any())).thenAnswer(inv -> {
            QuestionnaireResponse r = inv.getArgument(0);
            r.setId(1L);
            stored.add(r);
            return r;
        });
        when(responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(ORG_ID, VA_ID))
            .thenAnswer(inv -> List.copyOf(stored));
        when(assessments.save(any())).thenAnswer(inv -> inv.getArgument(0));

        QuestionnaireResponse body = new QuestionnaireResponse();
        body.setQuestionId(1L);
        body.setAnswerValue("YES");
        body.setNotApplicable(false);
        service.upsertResponse(VA_ID, body, null);

        // Posture would be 1/1=100; inverted → 0/1=0 → risk = 100 → CRITICAL
        assertThat(va.getComputedScorePct()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(va.getRiskRating()).isEqualTo("CRITICAL");
    }

    @Test void naResponse_requiresJustification() {
        VendorAssessment va = vendorAssessment(); va.setTotalQuestions(1);
        QuestionnaireQuestion q = question(1L, "Q-1", "BOOLEAN", 1.0, false, false);

        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(questions.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(q));
        when(responses.findByOrganisationIdAndVendorAssessmentIdAndQuestionId(ORG_ID, VA_ID, 1L))
            .thenReturn(Optional.empty());

        QuestionnaireResponse body = new QuestionnaireResponse();
        body.setQuestionId(1L);
        body.setNotApplicable(true);
        // No naJustification supplied

        assertThatThrownBy(() -> service.upsertResponse(VA_ID, body, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("naJustification");
    }

    @Test void scale_outOfRangeRejected() {
        VendorAssessment va = vendorAssessment();
        QuestionnaireQuestion q = question(1L, "Q-1", "SCALE_1_5", 1.0, true, false);

        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(questions.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(q));

        QuestionnaireResponse body = new QuestionnaireResponse();
        body.setQuestionId(1L);
        body.setAnswerValue("9");
        body.setNotApplicable(false);

        assertThatThrownBy(() -> service.upsertResponse(VA_ID, body, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("SCALE_1_5");
    }

    // ─── Workflow ─────────────────────────────────────────────────

    @Test void markComplete_refusesWhenMandatoryUnanswered() {
        VendorAssessment va = vendorAssessment();
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();
        when(questions.findByOrganisationIdAndTemplateIdOrderBySequenceOrderAsc(ORG_ID, TPL_ID))
            .thenReturn(List.of(
                question(1L, "Q-1", "BOOLEAN", 1.0, true, false),
                question(2L, "Q-2", "BOOLEAN", 1.0, true, false)));
        // Only Q-1 has a scored response
        QuestionnaireResponse r1 = new QuestionnaireResponse();
        r1.setQuestionId(1L); r1.setScoredPoints(BigDecimal.ONE); r1.setNotApplicable(false);
        when(responses.findByOrganisationIdAndVendorAssessmentIdOrderByQuestionId(ORG_ID, VA_ID))
            .thenReturn(List.of(r1));

        assertThatThrownBy(() -> service.markComplete(VA_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Q-2");
    }

    @Test void markReviewed_onlyFromCompleted() {
        VendorAssessment va = vendorAssessment();
        va.setWorkflowStatus("IN_PROGRESS");
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        vendorInCallerOrg();

        assertThatThrownBy(() -> service.markReviewed(VA_ID))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("COMPLETED");
    }

    // ─── SEC-TPRM-002: cross-tenant assessment workflow mutation prevention ───

    @Test void markReviewed_crossTenant_throwsEntityNotFound() {
        // Assessment exists and is COMPLETED, but its parent vendor is NOT in the
        // caller's org — the tenant guard must reject before the workflow flips.
        VendorAssessment va = vendorAssessment();
        va.setWorkflowStatus("COMPLETED");
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        when(vendorRepo.findByIdAndOrganisationId(VENDOR_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markReviewed(VA_ID))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("VendorAssessment not found");

        assertThat(va.getWorkflowStatus()).isEqualTo("COMPLETED");
    }

    @Test void markComplete_crossTenant_throwsEntityNotFound() {
        VendorAssessment va = vendorAssessment();
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        when(vendorRepo.findByIdAndOrganisationId(VENDOR_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.markComplete(VA_ID))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("VendorAssessment not found");
    }

    @Test void assignTemplate_crossTenant_throwsEntityNotFound() {
        VendorAssessment va = vendorAssessment(); va.setTemplateId(null);
        when(assessments.findById(VA_ID)).thenReturn(Optional.of(va));
        when(vendorRepo.findByIdAndOrganisationId(VENDOR_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assignTemplate(VA_ID, TPL_ID))
            .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
            .hasMessageContaining("VendorAssessment not found");
    }
}
