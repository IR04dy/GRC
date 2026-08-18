package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmComplianceMapping;
import com.tss.grc.module.bcm.cycle.repository.BcmComplianceMappingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmComplianceMappingServiceTest {

    private static final Long ORG_ID = 61L;

    @Mock private BcmComplianceMappingRepository repo;
    @Mock private AuditLogService audit;
    @InjectMocks private BcmComplianceMappingService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(repo.save(any(BcmComplianceMapping.class))).thenAnswer(inv -> {
            BcmComplianceMapping m = inv.getArgument(0);
            if (m.getId() == null) m.setId(1L);
            return m;
        });
    }
    @AfterEach void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    private static BcmComplianceMapping mapping(String fw, String code, String status) {
        BcmComplianceMapping m = new BcmComplianceMapping();
        m.setId(1L); m.setOrganisationId(ORG_ID);
        m.setSourceFramework(fw);
        m.setRequirementCode(code);
        m.setRequirementTitleEn("PLACEHOLDER — title");
        m.setComplianceStatus(status);
        return m;
    }

    @Test void create_rejectsUnknownFramework() {
        BcmComplianceMapping m = mapping("BANANAS", "1.1", "NOT_ASSESSED"); m.setId(null);
        assertThatThrownBy(() -> service.create(m))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("sourceFramework");
    }

    @Test void create_requiresRequirementCode() {
        BcmComplianceMapping m = mapping("ISO_22301", "", "NOT_ASSESSED"); m.setId(null);
        assertThatThrownBy(() -> service.create(m))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requirementCode");
    }

    @Test void create_rejectsDuplicateOnUniqueIndex() {
        BcmComplianceMapping fresh = mapping("ISO_22301", "8.2.2", "NOT_ASSESSED"); fresh.setId(null);
        BcmComplianceMapping existing = mapping("ISO_22301", "8.2.2", "COMPLIANT");
        when(repo.findByOrganisationIdAndSourceFrameworkAndRequirementCode(ORG_ID, "ISO_22301", "8.2.2"))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(fresh))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void assess_rejectsUnknownStatus() {
        assertThatThrownBy(() -> service.assessCompliance(1L, "PROBABLY", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("complianceStatus");
    }

    /**
     * F2-234 — <b>this test used to assert the defect as correct.</b> It read
     * {@code assessCompliance(1L, "COMPLIANT", "BCM Manager", …)} and then asserted
     * {@code getAssessedBy()).isEqualTo("BCM Manager")} — i.e. that the value the CALLER passed is
     * the value STORED, which is precisely what was wrong: the server stamped {@code assessedAt}
     * from its own clock on the next line. Fourth instance in this programme of a test locking in a
     * defect (F2-159, F2-175, F2-227), and the hardest kind to spot because it described the code
     * accurately.
     *
     * <p>Repurposed the way F2-223 repurposed
     * {@code transition_boardReviewToApproved_requiresApprover}: the half that protects the record
     * (status, clock, notes) is kept, and the attribution is now asserted to come from the
     * authenticated principal.
     */
    @Test void assess_takesTheAssessorFromThePrincipalNotTheRequest() {
        f234AuthAs("so_officer", 8L);
        BcmComplianceMapping m = mapping("ISO_22301", "4.1", "NOT_ASSESSED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(m));

        BcmComplianceMapping out = service.assessCompliance(1L, "COMPLIANT", "All controls in place");
        assertThat(out.getComplianceStatus()).isEqualTo("COMPLIANT");
        assertThat(out.getAssessedBy())
            .as("the assessor of an ISO 22301 / SAMA OPRES / NCEMA requirement is whoever is signed "
                + "in — there is no longer any request field that could say otherwise")
            .isEqualTo("so_officer");
        assertThat(out.getAssessedAt()).isNotNull();
        assertThat(out.getNotes()).contains("All controls");
    }

    @Test void assess_refusesWhenThereIsNoPrincipalToRecord() {
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.assessCompliance(1L, "COMPLIANT", "x"))
            .as("an assessment with no identified assessor is not an assessment — recording it "
                + "with a null assessor would be a worse record than refusing it")
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("assessor");
    }

    /** F2-234 — the create is born unassessed; it may not carry an assessment. */
    @Test void create_refusesAnAssessedStatusAndClearsTheAssessmentStamps() {
        BcmComplianceMapping fresh = mapping("ISO_22301", "9.9", "COMPLIANT");
        assertThatThrownBy(() -> service.create(fresh))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("/assess");

        BcmComplianceMapping ok = mapping("ISO_22301", "9.9", null);
        ok.setAssessedAt(java.time.LocalDateTime.of(2001, 1, 1, 0, 0));
        ok.setAssessedBy("Nobody Who Assessed This");
        when(repo.findByOrganisationIdAndSourceFrameworkAndRequirementCode(ORG_ID, "ISO_22301", "9.9"))
            .thenReturn(Optional.empty());
        when(repo.save(org.mockito.ArgumentMatchers.any(BcmComplianceMapping.class)))
            .thenAnswer(i -> i.getArgument(0));

        BcmComplianceMapping saved = service.create(ok);
        assertThat(saved.getComplianceStatus()).isEqualTo("NOT_ASSESSED");
        assertThat(saved.getAssessedAt()).isNull();
        assertThat(saved.getAssessedBy()).isNull();
    }

    /** F2-223's helper: CurrentActor needs a GrcUserDetails principal, not a bare username token. */
    private static void f234AuthAs(String username, Long userId) {
        SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new com.tss.grc.security.GrcUserDetails(
                    userId, ORG_ID, username, "x", true, true, java.util.List.of()),
                "x", java.util.List.of()));
    }

    @Test void attachEvidence_rejectsNullId() {
        BcmComplianceMapping m = mapping("ISO_22301", "4.1", "NOT_ASSESSED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(m));
        assertThatThrownBy(() -> service.attachEvidence(1L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evidenceId");
    }

    @Test void attachGap_setsGapId() {
        BcmComplianceMapping m = mapping("ISO_22301", "8.2.2", "PARTIAL");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(m));

        BcmComplianceMapping out = service.attachGap(1L, 17L);
        assertThat(out.getGapId()).isEqualTo(17L);
    }
}
