package com.tss.grc.module.reporting.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.repository.ControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.evidence.service.EvidenceStorageService;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * F2-326 — the platform-wide regulator / audit evidence package's {@code Audit_Trail.xlsx}
 * (the pack an auditor or regulator is handed) named the actor and dropped the ROLE it held.
 *
 * <p>The platform's own unified viewer (AuditLogPage) renders "{@code <user> · <role>}" and the
 * BCM evidence package ({@link com.tss.grc.module.bcm.cycle.service.BcmEvidencePackageService},
 * F2-325) carries both — so two readers of one row disagreed, and the file that leaves the platform
 * was the poorer one ([[F2-215]] two-of-N: fixing the actor in one export left the same data leaving
 * through a second export with the role dropped). {@code user_role} is co-populated exactly with
 * {@code user_username} (0 rows carry one without the other, both databases — F2-325's measurement),
 * so it adds no blank, and it is the field an auditor reads for a segregation-of-duties check (F2-54).
 * A row with no principal blanks the actor rather than fabricating one ([[F2-213]]/[[F2-209]]).
 */
@ExtendWith(MockitoExtension.class)
class RegulatorPackageServiceTest {

    private static final long ORG_ID = 3L;

    @Mock ControlRepository controlRepo;
    @Mock EvidenceItemRepository evidenceRepo;
    @Mock AuditLogService auditLogService;
    @Mock OrganisationRepository orgRepo;
    @Mock BrandThemeService brandThemeService;
    @Mock EvidenceStorageService evidenceStorage;

    @InjectMocks RegulatorPackageService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        when(orgRepo.findById(ORG_ID)).thenReturn(Optional.empty());
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            "Test Org", null, null, null, "#094242", "#C9A977", null));
        when(controlRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
        when(evidenceRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of()));
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void auditTrailSheetCarriesTheActorAndTheRoleHeldAndBlanksItHonestlyWhenAbsent() throws Exception {
        AuditLogEntry acted = new AuditLogEntry();
        acted.setId(7L);
        acted.setCreatedAt(LocalDateTime.of(2026, 8, 24, 9, 15, 0));
        acted.setUserUsername("so_officer");
        acted.setUserRole("CISO,SUSTAINABILITY_OFFICER");   // a comma-bearing role, the SoD field (F2-54)
        acted.setActionType("TRANSITION");
        acted.setEntityType("BcpPlan");
        acted.setSourceModule("bcm");
        acted.setSeverity("INFO");
        acted.setMessage("Plan approved");

        AuditLogEntry offRequest = new AuditLogEntry();   // an off-request sweep / automation: no principal
        offRequest.setId(8L);
        offRequest.setCreatedAt(LocalDateTime.of(2026, 8, 24, 3, 0, 0));
        offRequest.setActionType("RULE_FIRED");
        offRequest.setSourceModule("platform");
        offRequest.setSeverity("INFO");
        offRequest.setMessage("Escalation rule fired");

        when(auditLogService.list(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(acted, offRequest)));

        RegulatorPackageService.Package pkg = service.generate("auditor.tester");
        byte[] xlsx = readZipEntryBytes(pkg.bytes(), "Audit_Trail.xlsx");

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(xlsx))) {
            Sheet sheet = wb.getSheetAt(0);

            List<String> headers = rowStrings(sheet.getRow(0));
            assertThat(headers)
                .as("the audit-trail sheet must name the role next to the actor, in when->who->what order")
                .containsExactly("Time (UTC)", "Action", "Module", "Entity", "Code",
                                 "User", "Role", "Severity", "Message");
            int userCol = headers.indexOf("User");
            int roleCol = headers.indexOf("Role");
            int actionCol = headers.indexOf("Action");
            assertThat(roleCol).as("Role sits immediately after User").isEqualTo(userCol + 1);

            List<String> actedRow  = findDataRow(sheet, userCol, "so_officer");
            assertThat(actedRow.get(roleCol))
                .as("a row with a principal carries the role that principal held at the time")
                .isEqualTo("CISO,SUSTAINABILITY_OFFICER");

            List<String> offRow = findDataRowByCol(sheet, actionCol, "RULE_FIRED");
            assertThat(offRow.get(userCol))
                .as("a row with no principal blanks the actor -- never a fabricated one").isEmpty();
            assertThat(offRow.get(roleCol))
                .as("and blanks the role with it").isEmpty();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────

    private static List<String> rowStrings(Row row) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            out.add(row.getCell(i) == null ? "" : row.getCell(i).getStringCellValue());
        }
        return out;
    }

    private static List<String> findDataRow(Sheet sheet, int col, String value) {
        for (int r = 1; r <= sheet.getLastRowNum(); r++) {
            List<String> cells = rowStrings(sheet.getRow(r));
            if (col < cells.size() && value.equals(cells.get(col))) return cells;
        }
        throw new IllegalStateException("No data row with column " + col + " == " + value);
    }

    private static List<String> findDataRowByCol(Sheet sheet, int col, String value) {
        return findDataRow(sheet, col, value);
    }

    private static byte[] readZipEntryBytes(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(name)) return zin.readAllBytes();
                zin.closeEntry();
            }
        }
        throw new IllegalStateException("Entry not found: " + name);
    }
}
