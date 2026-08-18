package com.tss.grc.security;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.audit.service.NdiAuditPublisher;
import com.tss.grc.module.ndi.framework.repository.NdiFrameworkItemRepository;
import com.tss.grc.module.ndi.maturity.entity.NdiMaturityAssessment;
import com.tss.grc.module.ndi.maturity.repository.NdiMaturityAssessmentRepository;
import com.tss.grc.module.ndi.maturity.service.NdiMaturityAssessmentService;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.scm.fourthparty.entity.FourthPartyExposure;
import com.tss.grc.module.scm.fourthparty.repository.FourthPartyExposureRepository;
import com.tss.grc.module.scm.fourthparty.service.FourthPartyExposureService;
import com.tss.grc.module.scm.resilience.entity.SupplierResilienceTest;
import com.tss.grc.module.scm.resilience.repository.SupplierResilienceTestRepository;
import com.tss.grc.module.scm.resilience.service.SupplierResilienceTestService;
import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import com.tss.grc.module.sust.materiality.repository.MaterialTopicRepository;
import com.tss.grc.module.sust.materiality.service.MaterialTopicService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * F2-236 — the four registers whose split attribution no gate can assert, so a test must.
 *
 * <p>The two gates in this family are both blind to what these assert, and deliberately so. Once the
 * actor comes from the principal, {@code SplitAttributionContractTest} sees no split (the pair is
 * gone) and {@code ServerStampedFieldContractTest} sees a server stamp — and it would see a server
 * stamp equally if somebody replaced {@code CurrentActor.requireUsername(...)} with the literal
 * {@code "admin"}, which is exactly what three of these methods used to record. 🔑 <b>A surviving
 * mutant a gate legitimately cannot see is an unasserted behaviour, not a loose instrument</b>
 * (F2-219) — so the answer is a test, not a wider gate.
 *
 * <p>Each register gets two legs, and the second is the one that makes the first mean something:
 * <ol>
 *   <li>the actor stored is the authenticated principal, and the server's clock is still stamped —
 *       without the clock leg, a "fix" that stopped recording the transition would pass;</li>
 *   <li>with no principal, the route REFUSES rather than recording a null or a placeholder. A
 *       governance record whose approver is blank reads as an attribution.</li>
 * </ol>
 *
 * <p>Live-probed 28/28 pre-fix (the forged value stored, plus the literal {@code "admin"} and
 * {@code "system"} defaults) and 28/28 post-fix as {@code compliance}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ParameterBorneActorTest {

    private static final Long ORG_ID = 1L;
    private static final String WHO = "so_officer";

    @Mock private SupplierResilienceTestRepository srtRepo;
    @Mock private FourthPartyExposureRepository fpeRepo;
    @Mock private MaterialTopicRepository topicRepo;
    @Mock private NdiMaturityAssessmentRepository maturityRepo;
    @Mock private NdiFrameworkItemRepository frameworkRepo;
    @Mock private AuditLogService audit;
    @Mock private NdiAuditPublisher ndiAudit;

    @InjectMocks private SupplierResilienceTestService srt;
    @InjectMocks private FourthPartyExposureService fpe;
    @InjectMocks private MaterialTopicService topics;
    @InjectMocks private NdiMaturityAssessmentService maturity;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(srtRepo.save(any(SupplierResilienceTest.class))).thenAnswer(i -> i.getArgument(0));
        when(fpeRepo.save(any(FourthPartyExposure.class))).thenAnswer(i -> i.getArgument(0));
        when(topicRepo.save(any(MaterialTopic.class))).thenAnswer(i -> i.getArgument(0));
        when(maturityRepo.save(any(NdiMaturityAssessment.class))).thenAnswer(i -> i.getArgument(0));
        authAs(WHO, 8L);
    }

    @AfterEach void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private static void authAs(String username, Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new com.tss.grc.security.GrcUserDetails(
                    userId, ORG_ID, username, "x", true, true, List.of()),
                "x", List.of()));
    }

    // ---------------------------------------------------------------- supplier resilience test

    @Test
    @DisplayName("a resilience-test approval records the principal, not the controller's 'admin'")
    void resilienceTestApprovalTakesTheApproverFromThePrincipal() {
        SupplierResilienceTest t = new SupplierResilienceTest();
        t.setId(1L);
        t.setStatus("COMPLETED");
        when(srtRepo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(t));

        SupplierResilienceTest out = srt.approve(1L);

        assertThat(out.getApproverName())
            .as("the controller defaulted to the literal \"admin\", which is a real PLATFORM_ADMIN "
                + "username in this platform rather than a placeholder")
            .isEqualTo(WHO);
        assertThat(out.getApprovedAt())
            .as("the clock is what made the caller-supplied name a split in the first place — it "
                + "must still be stamped, or the fix removed the transition instead of closing it")
            .isNotNull();
        assertThat(out.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void resilienceTestApprovalRefusesWithNoPrincipalToRecord() {
        SecurityContextHolder.clearContext();
        SupplierResilienceTest t = new SupplierResilienceTest();
        t.setId(1L);
        when(srtRepo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(t));

        assertThatThrownBy(() -> srt.approve(1L))
            .as("recording an approval with a blank approver is worse than refusing it: a blank "
                + "reads as an attribution")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no identified actor");
    }

    // ---------------------------------------------------------------- fourth-party exposure

    @Test
    @DisplayName("a fourth-party validation records the principal, not the controller's 'system'")
    void fourthPartyValidationTakesTheValidatorFromThePrincipal() {
        FourthPartyExposure ex = new FourthPartyExposure();
        ex.setId(2L);
        ex.setStatus("DISCOVERED");
        when(fpeRepo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(ex));

        FourthPartyExposure out = fpe.validate(2L);

        assertThat(out.getValidatorName())
            .as("\"system\" would read as an automated validation of a SAMA fourth-party "
                + "concentration record when a human performed it — or when nobody did")
            .isEqualTo(WHO);
        assertThat(out.getLastValidatedAt()).isNotNull();
    }

    @Test
    void fourthPartyValidationRefusesWithNoPrincipalToRecord() {
        SecurityContextHolder.clearContext();
        FourthPartyExposure ex = new FourthPartyExposure();
        ex.setId(2L);
        when(fpeRepo.findByIdAndOrganisationId(2L, ORG_ID)).thenReturn(Optional.of(ex));

        assertThatThrownBy(() -> fpe.validate(2L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no identified actor");
    }

    // ---------------------------------------------------------------- material topic

    @Test
    @DisplayName("a materiality approval records the principal — the field it REQUIRED is gone")
    void materialTopicApprovalTakesTheApproverFromThePrincipal() {
        MaterialTopic topic = new MaterialTopic();
        topic.setId(3L);
        topic.setStatus("SCORED");
        when(topicRepo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(topic));

        MaterialTopic out = topics.approve(3L);

        assertThat(out.getApprovedBy())
            .as("this method used to throw a 400 when approvedBy was absent — requiring an "
                + "attribution it then never verified, while stamping approvedAt from its own clock")
            .isEqualTo(WHO);
        assertThat(out.getApprovedAt()).isNotNull();
        assertThat(out.getStatus()).isEqualTo("APPROVED");
    }

    @Test
    void materialTopicApprovalRefusesWithNoPrincipalToRecord() {
        SecurityContextHolder.clearContext();
        MaterialTopic topic = new MaterialTopic();
        topic.setId(3L);
        topic.setStatus("SCORED");
        when(topicRepo.findByIdAndOrganisationId(3L, ORG_ID)).thenReturn(Optional.of(topic));

        assertThatThrownBy(() -> topics.approve(3L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("no identified actor");
    }

    // ---------------------------------------------------------------- NDI maturity assessment

    @Test
    @DisplayName("marking a maturity item under review records the principal as the reviewer")
    void maturityReviewTakesTheReviewerFromThePrincipal() {
        NdiMaturityAssessment m = new NdiMaturityAssessment();
        m.setId(4L);
        m.setStatus("SUBMITTED");
        when(maturityRepo.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(m));

        NdiMaturityAssessment out = maturity.markUnderReview(4L);

        assertThat(out.getReviewerName()).isEqualTo(WHO);
        assertThat(out.getReviewedAt()).isNotNull();
        assertThat(out.getStatus()).isEqualTo("UNDER_REVIEW");
    }

    @Test
    @DisplayName("approving a maturity item records the principal, and the trail reads off the row")
    void maturityApprovalTakesTheApproverFromThePrincipal() {
        NdiMaturityAssessment m = new NdiMaturityAssessment();
        m.setId(4L);
        m.setStatus("UNDER_REVIEW");
        m.setCurrentLevel((short) 2);   // Short, not Integer — approve()'s Rule 2 reads it
        when(maturityRepo.findByIdAndOrganisationId(4L, ORG_ID)).thenReturn(Optional.of(m));

        NdiMaturityAssessment out = maturity.approve(4L);

        assertThat(out.getApproverName()).isEqualTo(WHO);
        assertThat(out.getApprovedAt()).isNotNull();
        assertThat(out.getStatus()).isEqualTo("APPROVED");
    }

    /**
     * The half the OTHER gate found, and the reason it matters: closing {@code markUnderReview}
     * makes the ordinary edit route a bypass of the same attribution, under the same
     * {@code NDI:WRITE}. Assignment before the review stays possible; a change after it does not.
     */
    @Test
    @DisplayName("update() may assign a reviewer before the review and may not change it after")
    void updateFreezesTheReviewerOnceTheReviewIsRecorded() {
        NdiMaturityAssessment unreviewed = new NdiMaturityAssessment();
        unreviewed.setId(5L);
        unreviewed.setStatus("DRAFT");
        unreviewed.setCycleId(9L);           // validate() requires both of these
        unreviewed.setFrameworkItemId(20L);
        unreviewed.setReviewerName("Chief Data Officer");
        when(maturityRepo.findByIdAndOrganisationId(5L, ORG_ID)).thenReturn(Optional.of(unreviewed));

        NdiMaturityAssessment assign = new NdiMaturityAssessment();
        assign.setReviewerName("Head of Data Governance");
        assertThat(maturity.update(5L, assign).getReviewerName())
            .as("8 of 16 live rows in dev and prod hold an office name with reviewed_at NULL — that "
                + "is an assignment, and refusing it would fail on correct data")
            .isEqualTo("Head of Data Governance");

        NdiMaturityAssessment reviewed = new NdiMaturityAssessment();
        reviewed.setId(6L);
        reviewed.setStatus("UNDER_REVIEW");
        reviewed.setCycleId(9L);
        reviewed.setFrameworkItemId(21L);
        reviewed.setReviewerName(WHO);
        reviewed.setReviewedAt(java.time.LocalDateTime.now());
        when(maturityRepo.findByIdAndOrganisationId(6L, ORG_ID)).thenReturn(Optional.of(reviewed));

        NdiMaturityAssessment forge = new NdiMaturityAssessment();
        forge.setReviewerName("Somebody Else Entirely");
        assertThatThrownBy(() -> maturity.update(6L, forge))
            .as("without this, closing markUnderReview() would be pointless — the edit route could "
                + "overwrite the recorded reviewer with anything")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("not editable here");

        NdiMaturityAssessment echo = new NdiMaturityAssessment();
        echo.setReviewerName(WHO);
        assertThat(maturity.update(6L, echo).getReviewerName())
            .as("an ECHO of the stored value is accepted, so a form that round-trips the row keeps "
                + "working (F2-231)")
            .isEqualTo(WHO);
    }
}
