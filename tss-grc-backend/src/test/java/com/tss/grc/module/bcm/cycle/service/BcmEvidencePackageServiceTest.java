package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Lock the audit evidence package shape:
 *
 * <ul>
 *   <li>ZIP contains all 8 expected entries (cover, 5 xlsx, 1 csv, MANIFEST)</li>
 *   <li>Cover sheet mentions org, cycle name, year, status, generated-by</li>
 *   <li>MANIFEST contains SHA-256 hashes for every file</li>
 *   <li>Filename includes year + cycle id + timestamp</li>
 *   <li>Empty cycle still produces a valid zip (header-only sheets)</li>
 *   <li>Missing cycle throws EntityNotFoundException</li>
 *   <li>Generation writes an audit log entry</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcmEvidencePackageServiceTest {

    private static final Long ORG_ID = 91L;
    private static final Long CYCLE_ID = 42L;

    @Mock private BcmAssessmentCycleRepository cycles;
    @Mock private BcmBiaAssessmentRepository bias;
    @Mock private BcmRiskRepository risks;
    @Mock private ContinuityStrategyRepository strategies;
    @Mock private BcmGapRepository gaps;
    @Mock private BcmCorrectiveActionRepository capas;
    @Mock private BcmComplianceMappingRepository compliance;
    @Mock private AuditLogRepository auditLog;
    @Mock private OrganisationRepository organisations;
    @Mock private AuditLogService audit;
    @InjectMocks private BcmEvidencePackageService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);

        BcmAssessmentCycle c = new BcmAssessmentCycle();
        c.setId(CYCLE_ID); c.setOrganisationId(ORG_ID);
        c.setCycleNameEn("Annual Readiness 2026");
        c.setReportingYear((short) 2026);
        c.setStatus("APPROVED");
        when(cycles.findByIdAndOrganisationId(CYCLE_ID, ORG_ID)).thenReturn(Optional.of(c));

        Organisation org = new Organisation();
        org.setId(ORG_ID);
        org.setNameEn("ACME Bank");
        when(organisations.findById(ORG_ID)).thenReturn(Optional.of(org));

        // Empty rows by default — individual tests override as needed.
        when(bias.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, CYCLE_ID)).thenReturn(List.of());
        when(risks.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, CYCLE_ID)).thenReturn(List.of());
        when(strategies.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, CYCLE_ID)).thenReturn(List.of());
        when(gaps.findByOrganisationIdAndCycleIdOrderByIdDesc(ORG_ID, CYCLE_ID)).thenReturn(List.of());
        when(capas.findByOrganisationIdOrderByIdDesc(ORG_ID)).thenReturn(List.of());
        when(compliance.findByOrganisationIdOrderBySourceFrameworkAscRequirementCodeAsc(ORG_ID)).thenReturn(List.of());

        Page<com.tss.grc.module.platform.auditlog.entity.AuditLogEntry> emptyPage = new PageImpl<>(List.of());
        when(auditLog.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(eq(ORG_ID), eq("bcm"), any(Pageable.class)))
            .thenReturn(emptyPage);
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void generate_emptyCycle_producesValidZipWithAllEntries() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");

        Set<String> entries = listZipEntries(pkg.bytes());
        assertThat(entries).containsExactlyInAnyOrder(
            "cover.txt",
            "bias.xlsx",
            "risks.xlsx",
            "strategies.xlsx",
            "gaps_and_capas.xlsx",
            "compliance_matrix.xlsx",
            "audit_log.csv",
            "MANIFEST.txt"
        );
    }

    @Test void generate_filenameContainsYearAndCycleId() {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");
        assertThat(pkg.filename()).startsWith("bcm-evidence-2026-cycle-42-");
        assertThat(pkg.filename()).endsWith(".zip");
    }

    @Test void generate_coverSheetMentionsOrgAndCycle() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");
        String cover = readZipEntry(pkg.bytes(), "cover.txt");
        assertThat(cover).contains("ACME Bank");
        assertThat(cover).contains("Annual Readiness 2026");
        assertThat(cover).contains("2026");
        assertThat(cover).contains("Generated by");
        assertThat(cover).contains("auditor.tester");
    }

    @Test void generate_manifestContainsSha256ForEveryFile() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor");
        String manifest = readZipEntry(pkg.bytes(), "MANIFEST.txt");
        assertThat(manifest).contains("cover.txt");
        assertThat(manifest).contains("bias.xlsx");
        assertThat(manifest).contains("compliance_matrix.xlsx");
        assertThat(manifest).contains("audit_log.csv");
        // Each line should have a 64-char hex hash
        assertThat(manifest.lines()
            .filter(l -> !l.startsWith("#") && !l.isBlank())
            .allMatch(l -> l.length() >= 66 && l.matches("[0-9a-f]{64}\\s+.+"))).isTrue();
    }

    @Test void generate_missingCycle_throws() {
        when(cycles.findByIdAndOrganisationId(999L, ORG_ID)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.generate(999L, "auditor"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void generate_writesAuditLogEntry() {
        service.generate(CYCLE_ID, "auditor.tester");
        org.mockito.Mockito.verify(audit).log(
            eq("REPORT"), eq("BcmAssessmentCycle"), eq(CYCLE_ID), org.mockito.ArgumentMatchers.contains("Evidence package"));
    }

    @Test void generate_anonymousFallsBackToSystem() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, null);
        String cover = readZipEntry(pkg.bytes(), "cover.txt");
        assertThat(cover).contains("system");
    }

    // ─── ZIP helpers ──────────────────────────────────────────────

    private static Set<String> listZipEntries(byte[] zipBytes) throws Exception {
        Set<String> names = new HashSet<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                names.add(e.getName());
                zin.closeEntry();
            }
        }
        return names;
    }

    private static String readZipEntry(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(name)) {
                    return new String(zin.readAllBytes());
                }
                zin.closeEntry();
            }
        }
        throw new IllegalStateException("Entry not found: " + name);
    }
}
