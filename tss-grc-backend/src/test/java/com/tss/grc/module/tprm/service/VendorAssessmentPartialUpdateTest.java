package com.tss.grc.module.tprm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-240 — {@code PUT /api/v1/tprm/assessments/{id}} must not downgrade a rating it was not sent.
 *
 * <p>The sharpest reset of the nineteen this slice swept, by what the initialiser ASSERTS.
 * {@code VendorAssessment} declared {@code riskRating = "MEDIUM"}, so
 * {@code updateAssessment}'s {@code if (req.getRiskRating() != null)} was true on every request and
 * a body that never mentioned the rating wrote MEDIUM over it. Measured identical in dev and prod:
 * <b>13 of 19</b> live assessments are HIGH, so the reset silently downgrades the third-party risk
 * rating on two thirds of the register — a SAMA outsourcing / TPRM control input.
 *
 * <p>Latent by caller today: this route has no frontend write client at all, which is F2-214's
 * invitation-to-build shape rather than a defence. The fixture is built the way Jackson builds a
 * request — {@code new VendorAssessment()} with only the keys the caller sent — because that is the
 * only fixture shape that can see this defect. One that sets every field first cannot.
 */
@ExtendWith(MockitoExtension.class)
class VendorAssessmentPartialUpdateTest {

    private static final Long ORG = 1L;

    @Mock private VendorRepository vendorRepo;
    @Mock private VendorAssessmentRepository assessmentRepo;
    @InjectMocks private TprmService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private Vendor vendor() {
        Vendor v = new Vendor();
        v.setId(4L);
        v.setOrganisationId(ORG);
        v.setVendorName("Gulf Cloud Services");
        return v;
    }

    /** A manual (non-questionnaire) assessment rated HIGH by a real reviewer. */
    private VendorAssessment stored() {
        VendorAssessment a = new VendorAssessment();
        a.setId(11L);
        a.setOrganisationId(ORG);
        a.setVendorId(4L);
        a.setTemplateId(null);           // manual — so the questionnaire-ownership guard stays out
        a.setRiskRating("HIGH");
        a.setFindings("Sub-processor list incomplete; no evidence of pen-test remediation.");
        return a;
    }

    private void stubUpdate(VendorAssessment a) {
        when(vendorRepo.findById(4L)).thenReturn(Optional.of(vendor()));
        when(assessmentRepo.findById(11L)).thenReturn(Optional.of(a));
        when(assessmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("an edit that omits riskRating leaves a HIGH assessment HIGH")
    void omittedRatingIsPreserved() {
        VendorAssessment a = stored();
        stubUpdate(a);

        VendorAssessment body = new VendorAssessment();   // exactly what a partial caller sends
        body.setFindings("Sub-processor list received; remediation evidence still outstanding.");

        VendorAssessment saved = service.updateAssessment(11L, body);
        assertThat(saved.getRiskRating())
            .as("the initialiser was \"MEDIUM\", so before F2-240 editing the findings text "
                + "downgraded the vendor's rating — 13 of 19 live rows in dev and prod are HIGH")
            .isEqualTo("HIGH");
        assertThat(saved.getFindings()).contains("still outstanding");
    }

    @Test
    @DisplayName("a rating the caller DOES send is applied")
    void sentRatingStillApplies() {
        VendorAssessment a = stored();
        stubUpdate(a);

        VendorAssessment body = new VendorAssessment();
        body.setRiskRating("CRITICAL");

        assertThat(service.updateAssessment(11L, body).getRiskRating()).isEqualTo("CRITICAL");
    }

    @Test
    @DisplayName("the create still supplies the rating the entity stopped initialising")
    void createStillDefaultsTheRating() {
        when(vendorRepo.findById(4L)).thenReturn(Optional.of(vendor()));
        when(assessmentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        VendorAssessment req = new VendorAssessment();
        req.setVendorId(4L);
        VendorAssessment saved = service.createAssessment(req);

        assertThat(saved.getRiskRating())
            .as("risk_rating is NOT NULL, so if the create stops defaulting it the fix has turned a "
                + "silent downgrade into a 500 — and ddl-auto=none means nothing else would notice")
            .isEqualTo(TprmService.DEFAULT_RISK_RATING);
    }

    @Test
    @DisplayName("the entity itself defaults nothing, so the update's guard can fire")
    void theEntityNoLongerInitialisesTheRating() {
        assertThat(new VendorAssessment().getRiskRating()).isNull();
    }

    /**
     * The separate defect this slice's probe walked into — F2-213's family, not this one.
     *
     * <p>{@code updateAssessment} wrote {@code assessmentDate}, {@code assessor}, {@code status},
     * {@code findings} and {@code nextReviewDate} unconditionally. Because {@code assessment_date}
     * and {@code status} are NOT NULL, the route answered <b>400 "A required field was not
     * supplied"</b> to every partial body — and a body that did supply those two nulled the other
     * three, populated on 18 of 19 live rows in dev and 19 of 19 in prod.
     */
    @Test
    @DisplayName("a partial edit preserves the five columns the route used to overwrite")
    void theOtherFullReplaceFieldsArePreservedToo() {
        VendorAssessment a = stored();
        a.setAssessmentDate(java.time.LocalDate.of(2026, 3, 1));
        a.setAssessor("Reem Al-Harbi");
        a.setStatus("COMPLETED");
        a.setNextReviewDate(java.time.LocalDate.of(2027, 3, 1));
        stubUpdate(a);

        VendorAssessment body = new VendorAssessment();
        body.setFindings("Evidence received.");

        VendorAssessment saved = service.updateAssessment(11L, body);
        assertThat(saved.getAssessmentDate())
            .as("NOT NULL — writing this null is why the route answered 400 rather than losing data")
            .isEqualTo(java.time.LocalDate.of(2026, 3, 1));
        assertThat(saved.getStatus()).isEqualTo("COMPLETED");
        assertThat(saved.getAssessor()).isEqualTo("Reem Al-Harbi");
        assertThat(saved.getNextReviewDate()).isEqualTo(java.time.LocalDate.of(2027, 3, 1));
        assertThat(saved.getFindings()).isEqualTo("Evidence received.");
    }
}
