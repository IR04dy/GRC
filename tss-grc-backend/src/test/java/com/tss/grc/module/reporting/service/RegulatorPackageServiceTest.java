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
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
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

    // ── F2-451: a capped list is fine to RENDER and wrong to COUNT ─────────

    @Test void theManifestPublishesTheRegisterTotalAndSeparatelyWhatTheSheetsHold() throws Exception {
        // The shape measured in dev on 2026-09-03: platform.audit_log held 12,470 rows for
        // organisation 1 (5,133 in prod) against MAX_AUDIT = 1000, and the manifest published the
        // capped page size under a key called "counts".
        when(auditLogService.list(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(entry(1L), entry(2L), entry(3L)),
                                       PageRequest.of(0, 1000), 12_470L));

        String manifest = new String(
            readZipEntryBytes(service.generate("auditor.tester").bytes(), "manifest.json"),
            StandardCharsets.UTF_8);

        assertThat(manifest)
            .as("\"counts\" means the REGISTER's size -- the key's own name")
            .contains("\"counts\": { \"controls\": 0, \"evidenceItems\": 0, \"auditEntries\": 12470 }");
        assertThat(manifest)
            .as("and \"includedRows\" means what the sheets in this ZIP actually hold")
            .contains("\"includedRows\": { \"controls\": 0, \"evidenceItems\": 0, \"auditEntries\": 3 }");
        assertThat(manifest)
            .as("with the subtraction done for a consumer's tooling")
            .contains("\"truncated\": true");
    }

    @Test void aCompletePackageDeclaresNoTruncationAndInventsNoAlarm() throws Exception {
        // The guard leg: the fix must not put a truncation note on a package that holds its whole
        // register. Every other register in both databases is comfortably inside its cap, so this
        // is the case almost every real package takes.
        when(auditLogService.list(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(entry(1L))));

        byte[] zip = service.generate("auditor.tester").bytes();
        String manifest = new String(readZipEntryBytes(zip, "manifest.json"), StandardCharsets.UTF_8);

        assertThat(manifest).contains("\"truncated\": false");
        assertThat(manifest)
            .as("counts and includedRows agree when nothing was withheld")
            .contains("\"counts\": { \"controls\": 0, \"evidenceItems\": 0, \"auditEntries\": 1 }")
            .contains("\"includedRows\": { \"controls\": 0, \"evidenceItems\": 0, \"auditEntries\": 1 }");
        assertThat(readmeText(zip))
            .as("the completeness subtitle survives, and no shortfall sentence is invented")
            .contains("Trail entries")
            .doesNotContain("Truncated for size");
    }

    @Test void theReadmeDisclosesTheTruncationBesideTheFigureNotInAMetadataRow() throws Exception {
        when(auditLogService.list(any(), any(), any(), anyInt(), anyInt()))
            .thenReturn(new PageImpl<>(List.of(entry(1L), entry(2L), entry(3L)),
                                       PageRequest.of(0, 1000), 12_470L));

        String readme = readmeText(service.generate("auditor.tester").bytes());

        assertThat(readme)
            .as("the disclosure sits with the figure, in the KPI subtitle")
            .contains("showing 3 of 12,470");
        assertThat(readme)
            .as("a subtitle asserting completeness must not survive next to a windowed figure")
            .doesNotContain("Trail entries");
        assertThat(readme)
            .as("and the contents section names which sheet was cut, and where the totals are")
            .contains("Truncated for size")
            .contains("Audit_Trail.xlsx")
            .contains("counts");
    }

    private static AuditLogEntry entry(long id) {
        AuditLogEntry e = new AuditLogEntry();
        e.setId(id);
        e.setCreatedAt(LocalDateTime.of(2026, 8, 24, 9, 15, 0));
        e.setActionType("TRANSITION");
        e.setSourceModule("bcm");
        e.setSeverity("INFO");
        e.setMessage("row " + id);
        return e;
    }

    private static String readmeText(byte[] zipBytes) throws Exception {
        byte[] pdf = readZipEntryBytes(zipBytes, "README.pdf");
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor x = new PdfTextExtractor(reader);
            StringBuilder sb = new StringBuilder();
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                sb.append(x.getTextFromPage(page)).append('\n');
            }
            return sb.toString();
        } finally {
            reader.close();
        }
    }

    // ── helpers ───────────────────────────────────────────────────────

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
