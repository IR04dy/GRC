package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.Vulnerability;
import com.tss.grc.module.cyber.repository.ControlAssessmentRepository;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * CY-F — {@link CyberExportService}. Confirms each register renders a real
 * workbook / PDF (magic-byte check) and that an unknown register/format is
 * rejected with a 400 ({@link IllegalArgumentException}) rather than reaching
 * the renderer.
 */
@ExtendWith(MockitoExtension.class)
class CyberExportServiceTest {

    private static final Long ORG = 1L;

    @Mock private CyberControlRepository controlRepo;
    @Mock private VulnerabilityRepository vulnRepo;
    @Mock private ControlAssessmentRepository assessmentRepo;
    @Mock private CyberIncidentRepository incidentRepo;
    @Mock private CyberActionPlanRepository actionPlanRepo;
    @Mock private CyberEvidenceRepository evidenceRepo;
    @Mock private OrganisationRepository orgRepo;
    @InjectMocks private CyberExportService service;

    @BeforeEach void setTenant() { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void clearTenant() { TenantContext.clear(); }

    private static String magic(byte[] b, int n) {
        return new String(b, 0, Math.min(n, b.length), StandardCharsets.ISO_8859_1);
    }

    @Test
    void controls_xlsx_isAValidWorkbook() {
        CyberControl c = new CyberControl();
        c.setControlNumber("ECC-1-1-1");
        c.setTitleEn("MFA on all admin access");
        when(controlRepo.findByOrganisationId(eq(ORG), any())).thenReturn(new PageImpl<>(List.of(c)));

        byte[] out = service.export("controls", "xlsx", null);
        assertThat(out).isNotEmpty();
        assertThat(magic(out, 2)).isEqualTo("PK");            // XLSX = zip container
    }

    @Test
    void vulnerabilities_arabicPdf_isAValidPdf() {
        Vulnerability v = new Vulnerability();
        v.setTitleEn("Log4Shell");
        v.setTitleAr("ثغرة Log4Shell");
        v.setSeverity("CRITICAL");
        when(vulnRepo.findByOrganisationId(eq(ORG), any())).thenReturn(new PageImpl<>(List.of(v)));

        byte[] out = service.export("vulnerabilities", "pdf", "ar");
        assertThat(magic(out, 4)).isEqualTo("%PDF");          // PDF header
    }

    @Test
    void unknownRegister_isRejectedWith400() {
        assertThatThrownBy(() -> service.export("bogus", "xlsx", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("register");
    }

    @Test
    void unknownFormat_isRejectedWith400() {
        assertThatThrownBy(() -> service.export("controls", "docx", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format");
    }
}
