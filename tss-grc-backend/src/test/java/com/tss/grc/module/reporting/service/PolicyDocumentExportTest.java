package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.policy.entity.PolicyDocument;
import com.tss.grc.module.policy.repository.PolicyDocumentRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CH-10 — the controlled policy document PDF renders the authored inline content
 * (content_md) into a real branded PDF; a missing / cross-tenant policy is
 * rejected (never a leaked export).
 */
@ExtendWith(MockitoExtension.class)
class PolicyDocumentExportTest {

    private static final Long ORG = 1L;
    private static final Long POLICY_ID = 77L;

    @Mock OrganisationRepository orgRepo;
    @Mock RiskRepository riskRepo;
    @Mock AuditFindingRepository findingRepo;
    @Mock KriDefinitionRepository kriRepo;
    @Mock KriMeasurementRepository measRepo;
    @Mock BrandThemeService brandThemeService;
    @Mock ChangeRequestRepository changeRepo;
    @Mock ChangeApprovalRepository changeApprovalRepo;
    @Mock ChangeTaskRepository changeTaskRepo;
    @Mock ChangeLinkRepository changeLinkRepo;
    @Mock PolicyDocumentRepository policyDocRepo;
    @InjectMocks ExportService svc;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static PolicyDocument policy() {
        PolicyDocument p = new PolicyDocument();
        p.setId(POLICY_ID);
        p.setOrganisationId(ORG);
        p.setPolicyCode("ISMS-001");
        p.setVersion("2.0");
        p.setTitleEn("Information Security Policy");
        p.setCategory("CYBER");
        p.setStatus("PUBLISHED");
        p.setContentMd("# Information Security Policy\n\n## Purpose\nProtect assets.\n\n## Scope\nAll staff.\n\n- MFA everywhere\n- Least privilege");
        return p;
    }

    @Test
    void policyDocumentPdf_rendersInlineContentToAPdf() {
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        when(orgRepo.findById(ORG)).thenReturn(Optional.empty());
        when(policyDocRepo.findByIdAndOrganisationId(POLICY_ID, ORG)).thenReturn(Optional.of(policy()));

        byte[] b = svc.policyDocumentPdf(POLICY_ID, "en");

        assertThat(b).startsWith(new byte[]{'%', 'P', 'D', 'F'});   // PDF magic
        assertThat(b.length).isGreaterThan(2000);                  // a real, non-empty document
    }

    @Test
    void policyDocumentPdf_rejectsAPolicyFromAnotherTenant() {
        lenient().when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        lenient().when(orgRepo.findById(ORG)).thenReturn(Optional.empty());
        // repo is tenant-scoped: a foreign policy simply isn't found
        when(policyDocRepo.findByIdAndOrganisationId(POLICY_ID, ORG)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> svc.policyDocumentPdf(POLICY_ID, "en"))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
