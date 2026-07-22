package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.change_mgmt.entity.ChangeRequest;
import com.tss.grc.module.change_mgmt.repository.ChangeRequestRepository;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.module.risk.kri.repository.KriDefinitionRepository;
import com.tss.grc.module.risk.kri.repository.KriMeasurementRepository;
import com.tss.grc.module.risk.repository.RiskRepository;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CH-4 — the change register export must emit CHANGE data (not the risk register,
 * which the Changes "Export" button previously downloaded). Byte-level: parse the
 * XLSX and assert the sheet name + a change code row.
 */
@ExtendWith(MockitoExtension.class)
class ChangeRegisterExportTest {

    private static final Long ORG = 1L;

    @Mock OrganisationRepository orgRepo;
    @Mock RiskRepository riskRepo;
    @Mock AuditFindingRepository findingRepo;
    @Mock KriDefinitionRepository kriRepo;
    @Mock KriMeasurementRepository measRepo;
    @Mock BrandThemeService brandThemeService;
    @Mock ChangeRequestRepository changeRepo;
    @InjectMocks ExportService svc;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static ChangeRequest ch(String code, String type, String status) {
        ChangeRequest c = new ChangeRequest();
        c.setChangeCode(code);
        c.setTitleEn("Title " + code);
        c.setChangeType(type);
        c.setRiskLevel("MEDIUM");
        c.setStatus(status);
        c.setChangeOwnerName("owner");
        return c;
    }

    @Test
    void changeRegisterXlsx_emitsChangeRows_onAChangeRegisterSheet() throws Exception {
        // headerStyle() pulls the tenant brand colour for the header fill.
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#0B5FFF", "#00B8A9", null));
        when(changeRepo.findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(
                ch("CHG-1", "NORMAL", "DRAFT"),
                ch("CHG-2", "EMERGENCY", "CLOSED"))));

        byte[] b = svc.changeRegisterXlsx();

        assertThat(b).startsWith(new byte[]{'P', 'K'});   // OOXML zip magic
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(b))) {
            Sheet s = wb.getSheetAt(0);
            assertThat(s.getSheetName()).isEqualTo("Change Register");            // NOT "Risk Register"
            assertThat(s.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Code");
            assertThat(s.getRow(1).getCell(0).getStringCellValue()).isEqualTo("CHG-1");
            assertThat(s.getRow(2).getCell(0).getStringCellValue()).isEqualTo("CHG-2");
            assertThat(s.getLastRowNum()).isEqualTo(2);   // header + 2 data rows
        }
        verify(changeRepo).findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any());
    }
}
