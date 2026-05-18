package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmComplianceMapping;
import com.tss.grc.module.bcm.cycle.repository.BcmComplianceMappingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
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
    @AfterEach void tearDown() { TenantContext.clear(); }

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
        BcmComplianceMapping m = mapping("ISO_22301", "4.1", "NOT_ASSESSED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(m));

        assertThatThrownBy(() -> service.assessCompliance(1L, "PROBABLY", "QA", "x"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("complianceStatus");
    }

    @Test void assess_setsStatusReviewerTimestamp() {
        BcmComplianceMapping m = mapping("ISO_22301", "4.1", "NOT_ASSESSED");
        when(repo.findByIdAndOrganisationId(1L, ORG_ID)).thenReturn(Optional.of(m));

        BcmComplianceMapping out = service.assessCompliance(1L, "COMPLIANT", "BCM Manager", "All controls in place");
        assertThat(out.getComplianceStatus()).isEqualTo("COMPLIANT");
        assertThat(out.getAssessedBy()).isEqualTo("BCM Manager");
        assertThat(out.getAssessedAt()).isNotNull();
        assertThat(out.getNotes()).contains("All controls");
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
