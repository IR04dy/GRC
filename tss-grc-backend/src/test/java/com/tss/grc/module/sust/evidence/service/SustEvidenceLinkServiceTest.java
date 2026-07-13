package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.repository.EvidenceLinkRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.climate.repository.ClimateRiskOpportunityRepository;
import com.tss.grc.module.sust.cma.repository.CmaDisclosureRepository;
import com.tss.grc.module.sust.finance.entity.CarbonCredit;
import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import com.tss.grc.module.sust.finance.entity.GreenProject;
import com.tss.grc.module.sust.finance.entity.InstrumentAllocation;
import com.tss.grc.module.sust.finance.repository.CarbonCreditRepository;
import com.tss.grc.module.sust.finance.repository.GreenInstrumentRepository;
import com.tss.grc.module.sust.finance.repository.GreenProjectRepository;
import com.tss.grc.module.sust.finance.repository.InstrumentAllocationRepository;
import com.tss.grc.module.sust.finance.service.AssuranceLineageService;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.metrics.repository.EsgMetricRepository;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.tcfd.repository.TcfdItemRepository;
import com.tss.grc.module.sust.vendor.repository.VendorEsgSurveyRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * GF-2 (V298) — the four green-finance object types join the S-C evidence
 * bridge: {@code link()} must set each entity's {@code evidence_item_id} FK
 * (org-scoped lookup) and record the generic link. Also pins the lineage
 * grades green rows will surface with (verified = n/a → never a PARTIAL for
 * lacking an attestation step that doesn't exist — plan §5 decision #3).
 */
@ExtendWith(MockitoExtension.class)
class SustEvidenceLinkServiceTest {

    private static final Long ORG = 1L;

    @Mock private EvidenceItemRepository evidenceItemRepo;
    @Mock private EvidenceLinkRepository evidenceLinkRepo;
    @Mock private AuditLogService audit;
    @Mock private GhgEmissionRecordRepository ghgRepo;
    @Mock private CmaDisclosureRepository cmaRepo;
    @Mock private TcfdItemRepository tcfdRepo;
    @Mock private SgiCommitmentRepository sgiRepo;
    @Mock private ClimateRiskOpportunityRepository climateRepo;
    @Mock private EsgMetricRepository metricRepo;
    @Mock private VendorEsgSurveyRepository vendorSurveyRepo;
    @Mock private GreenProjectRepository greenProjectRepo;
    @Mock private GreenInstrumentRepository greenInstrumentRepo;
    @Mock private InstrumentAllocationRepository instrumentAllocationRepo;
    @Mock private CarbonCreditRepository carbonCreditRepo;
    @Mock private EvidenceItem item;

    @InjectMocks private SustEvidenceLinkService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private void stubItem() {
        when(item.getOrganisationId()).thenReturn(ORG);
        when(evidenceItemRepo.findById(9L)).thenReturn(Optional.of(item));
        when(evidenceLinkRepo.findByOrganisationIdAndLinkedModuleAndLinkedEntityIdAndIsDeletedFalse(
            ORG, SustEvidenceLinkService.MODULE, 5L)).thenReturn(List.of());
    }

    @Test
    void link_setsFkOnGreenProject() {
        stubItem();
        GreenProject p = new GreenProject();
        when(greenProjectRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 5L)).thenReturn(Optional.of(p));

        service.link("GreenProject", 5L, 9L, null);

        assertThat(p.getEvidenceItemId()).isEqualTo(9L);
        verify(greenProjectRepo).save(p);
        verify(evidenceLinkRepo).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void link_setsFkOnGreenInstrument_allocation_andCredit() {
        stubItem();
        GreenInstrument gi = new GreenInstrument();
        InstrumentAllocation ia = new InstrumentAllocation();
        CarbonCredit cc = new CarbonCredit();
        when(greenInstrumentRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 5L)).thenReturn(Optional.of(gi));
        when(instrumentAllocationRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 5L)).thenReturn(Optional.of(ia));
        when(carbonCreditRepo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG, 5L)).thenReturn(Optional.of(cc));

        service.link("GreenInstrument", 5L, 9L, null);
        service.link("InstrumentAllocation", 5L, 9L, null);
        service.link("CarbonCredit", 5L, 9L, null);

        assertThat(gi.getEvidenceItemId()).isEqualTo(9L);
        assertThat(ia.getEvidenceItemId()).isEqualTo(9L);
        assertThat(cc.getEvidenceItemId()).isEqualTo(9L);
    }

    @Test
    void link_rejectsUnknownEntityType() {
        assertThatThrownBy(() -> service.link("GreenWidget", 5L, 9L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("entityType");
    }

    // ── GF-2 lineage grades for green rows (verified = n/a → NULL) ────

    @Test
    void greenLineageGrades_dataAndEvidenceDriveTheGrade_verifiedNeverPenalises() {
        // A project with actuals + linked evidence is assurance-COMPLETE even
        // though green objects have no attestation step (verified = null).
        assertThat(AssuranceLineageService.grade(true,  true,  null)).isEqualTo("COMPLETE");
        // Actuals without evidence — the exact gap GF-2 exists to close.
        assertThat(AssuranceLineageService.grade(true,  false, null)).isEqualTo("PARTIAL");
        // A PLANNED-style row with no underlying figures.
        assertThat(AssuranceLineageService.grade(false, false, null)).isEqualTo("MISSING");
    }
}
