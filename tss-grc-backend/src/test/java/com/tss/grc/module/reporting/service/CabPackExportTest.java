package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeApproval;
import com.tss.grc.module.change_mgmt.entity.ChangeLink;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.entity.ChangeTask;
import com.tss.grc.module.change_mgmt.repository.ChangeApprovalRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeLinkRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.change_mgmt.repository.ChangeTaskRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * CH-11 — the CAB pack is a single-change consolidated PDF (details, plans,
 * votes, tasks, links, PIR). Byte-level: assert PDF magic + a non-trivial body,
 * and that a cross-tenant / missing change is rejected (not silently exported).
 */
@ExtendWith(MockitoExtension.class)
class CabPackExportTest {

    private static final Long ORG = 1L;
    private static final Long CHANGE_ID = 55L;

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
    @InjectMocks ExportService svc;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static ChangeRequest change() {
        ChangeRequest c = new ChangeRequest();
        c.setId(CHANGE_ID);
        c.setOrganisationId(ORG);
        c.setChangeCode("CHG-2026-0055");
        c.setTitleEn("Upgrade the payments gateway");
        c.setChangeType("NORMAL");
        c.setRiskLevel("HIGH");
        c.setStatus("CLOSED");
        c.setImplementationPlan("1. Blue/green deploy  2. Cut over  3. Verify");
        c.setTestPlan("Regression in staging");
        c.setRollbackPlan("Flip traffic back to blue");
        c.setPirSummary("Cutover clean; objectives met.");
        c.setPirObjectivesMet(true);
        c.setPirReviewedBy("Dana Reviewer");
        return c;
    }

    @Test
    void cabPackPdf_producesAPdfForAnOwnedChange() {
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        when(orgRepo.findById(ORG)).thenReturn(Optional.empty());
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(change()));
        ChangeApproval a = new ChangeApproval();
        a.setDecision("APPROVE"); a.setApproverName("Bob CAB"); a.setApproverRole("CAB Chair");
        when(changeApprovalRepo.findByChangeRequestIdOrderByDecidedAtAsc(CHANGE_ID)).thenReturn(List.of(a));
        ChangeTask k = new ChangeTask();
        k.setTitleEn("Snapshot DB"); k.setStatus("DONE"); k.setOwnerName("ops");
        when(changeTaskRepo.findByChangeRequestIdOrderBySortOrderAsc(CHANGE_ID)).thenReturn(List.of(k));
        ChangeLink l = new ChangeLink();
        l.setEntityType("RISK"); l.setEntityId(9L); l.setLinkType("MITIGATES");
        when(changeLinkRepo.findByChangeRequestId(CHANGE_ID)).thenReturn(List.of(l));

        byte[] b = svc.cabPackPdf(CHANGE_ID, "en");

        assertThat(b).startsWith(new byte[]{'%', 'P', 'D', 'F'});   // PDF magic
        assertThat(b.length).isGreaterThan(2000);                  // a real, non-empty document
    }

    @Test
    void cabPackPdf_rejectsAChangeFromAnotherTenant() {
        // Cross-tenant / missing → EntityNotFoundException, never a leaked export.
        lenient().when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        lenient().when(orgRepo.findById(ORG)).thenReturn(Optional.empty());
        ChangeRequest foreign = change();
        foreign.setOrganisationId(999L);   // different tenant
        when(changeRepo.findById(CHANGE_ID)).thenReturn(Optional.of(foreign));

        assertThatThrownBy(() -> svc.cabPackPdf(CHANGE_ID, "en"))
            .isInstanceOf(EntityNotFoundException.class);
    }
}
