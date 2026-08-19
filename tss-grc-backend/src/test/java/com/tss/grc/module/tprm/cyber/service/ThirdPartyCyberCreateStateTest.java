package com.tss.grc.module.tprm.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.cyber.entity.ThirdPartyCyberAssessment;
import com.tss.grc.module.tprm.cyber.repository.ThirdPartyCyberAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import com.tss.grc.module.tprm.service.TprmVendorReferenceResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-230 — the create path: it forces the initial state, and the derived tier reads the declared
 * ladder rather than restating it.
 *
 * <p>F2-220's shape, tenth instance: {@code if (status == null) status = "DRAFT"} is a DEFAULT and not
 * a guard, so a caller could create an assessment already APPROVED and skip {@code submit()} and
 * {@code approve()} — the only writers of {@code assessedAt}, {@code approvedAt} and
 * {@code approverName}.
 */
class ThirdPartyCyberCreateStateTest {

    private ThirdPartyCyberAssessmentRepository repo;
    private ThirdPartyCyberAssessmentService service;

    @BeforeEach
    void setUp() {
        repo = mock(ThirdPartyCyberAssessmentRepository.class);
        // F2-247 — a REAL TprmVendorReferenceResolver over a mocked VendorRepository, never a mocked
        // resolver. A mock returns null and the create's vendor-reference legs would then go green
        // asserting nothing (F2-237). These fixtures supply a label and no id, so the unlinked
        // path is taken and the repository is never consulted.
        service = new ThirdPartyCyberAssessmentService(
                repo, new TprmVendorReferenceResolver(mock(VendorRepository.class)));
        when(repo.save(any(ThirdPartyCyberAssessment.class)))
            .thenAnswer(inv -> inv.getArgument(0));
        TenantContext.setCurrentTenant(1L);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private static ThirdPartyCyberAssessment req(String inherent, String status) {
        ThirdPartyCyberAssessment a = new ThirdPartyCyberAssessment();
        a.setVendorName("[REVIEW-TEST] Vendor");
        a.setInherentCyberRisk(inherent);
        a.setScopeDescriptionEn("scope");
        a.setStatus(status);
        return a;
    }

    @Test
    @DisplayName("a create that names APPROVED is refused, not silently honoured")
    void aCreateMayNotNameItsOwnState() {
        assertThatThrownBy(() -> service.create(req("HIGH", "APPROVED")))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(req("HIGH", "RETIRED")))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("a create with no status lands in DRAFT — nothing that works today starts failing")
    void aCreateWithNoStatusStillWorks() {
        service.create(req("HIGH", null));
        ArgumentCaptor<ThirdPartyCyberAssessment> cap =
            ArgumentCaptor.forClass(ThirdPartyCyberAssessment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo("DRAFT");
        // No screen has a create form for this register, so this is the only shape a real caller uses.
    }

    @Test
    @DisplayName("a create naming DRAFT explicitly is accepted — the initial state is not forbidden")
    void namingTheInitialStateIsFine() {
        service.create(req("HIGH", "DRAFT"));
        verify(repo).save(any(ThirdPartyCyberAssessment.class));
    }

    @Test
    @DisplayName("inherent risk is normalised and refused through its own vocabulary")
    void inherentRiskIsClosed() {
        service.create(req(" high ", null));
        ArgumentCaptor<ThirdPartyCyberAssessment> cap =
            ArgumentCaptor.forClass(ThirdPartyCyberAssessment.class);
        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getInherentCyberRisk()).isEqualTo("HIGH");

        assertThatThrownBy(() -> service.create(req("ELEVATED", null)))
            .as("ELEVATED is a RESIDUAL rung, not an inherent one — two risk vocabularies on one "
              + "entity, overlapping only at LOW, and mixing them is what F2-196 is about")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("the derived tier comes from the ladder, and the CRITICAL-inherent floor holds")
    void theDerivedTierReadsTheLadder() {
        // A vendor with nothing recorded scores 20 (MFA off + no pentest) → the lowest rung.
        ThirdPartyCyberAssessment low = service.create(req("LOW", null));
        assertThat(low.getResidualRiskTier()).isEqualTo(ResidualRiskTier.ladder().get(0));

        // Same score, but CRITICAL inherent: the floor lifts it one rung. Expressed as an elevation
        // over the ladder, so it stays a floor if a rung is ever inserted below MODERATE.
        ThirdPartyCyberAssessment crit = service.create(req("CRITICAL", null));
        assertThat(crit.getResidualRiskTier()).isEqualTo(ResidualRiskTier.ladder().get(1));
        assertThat(ResidualRiskTier.atLeast(crit.getResidualRiskTier(), "MODERATE")).isTrue();
    }

    @Test
    @DisplayName("every score band lands on a declared rung, and the top band reaches the top rung")
    void everyBandLandsOnARung() {
        // The bands are held against the ladder by a static check in the service; this drives the
        // whole range through the real arithmetic so a band that fell off the end would fail here
        // rather than silently capping.
        for (int posture = 0; posture <= 100; posture += 5) {
            ThirdPartyCyberAssessment a = req("LOW", null);
            a.setExternalPostureScore(posture);
            a.setQuestionnaireScore(posture);
            a.setCriticalVulnsOpen(100 - posture);
            ThirdPartyCyberAssessment saved = service.create(a);
            assertThat(ResidualRiskTier.ladder())
                .as("posture %d produced tier %s", posture, saved.getResidualRiskTier())
                .contains(saved.getResidualRiskTier());
        }
        // The worst possible input must reach the top rung — otherwise CRITICAL is unreachable and the
        // vocabulary declares a state nothing can produce.
        ThirdPartyCyberAssessment worst = req("CRITICAL", null);
        worst.setExternalPostureScore(0);
        worst.setQuestionnaireScore(0);
        worst.setCriticalVulnsOpen(50);
        worst.setBreachHistory24m((short) 1);
        assertThat(service.create(worst).getResidualRiskTier())
            .isEqualTo(ResidualRiskTier.LADDER.top());
    }
}
