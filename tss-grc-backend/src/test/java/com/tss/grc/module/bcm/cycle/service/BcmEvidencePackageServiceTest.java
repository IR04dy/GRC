package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.module.platform.export.PdfTheme;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.*;
import com.tss.grc.module.bcm.cycle.repository.*;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.branding.service.BrandThemeService;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

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
    @Mock private BrandThemeService brandThemeService;
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

        // README.pdf cover resolves the tenant brand (defaults are fine for the test).
        when(brandThemeService.resolve()).thenReturn(new BrandThemeService.ResolvedBrand(
            null, null, null, null, "#094242", "#C9A977", null));
    }
    @AfterEach void tearDown() { TenantContext.clear(); }

    @Test void generate_emptyCycle_producesValidZipWithAllEntries() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");

        Set<String> entries = listZipEntries(pkg.bytes());
        assertThat(entries).containsExactlyInAnyOrder(
            "README.pdf",
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

    @Test void generate_readmeIsBrandedPdf() throws Exception {
        // The cover is now a branded README.pdf (PdfTheme). Text is glyph-encoded in the
        // PDF so we assert it is a valid, non-trivial PDF rather than grepping content.
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");
        String readme = readZipBinaryHead(pkg.bytes(), "README.pdf");
        assertThat(readme).startsWith("%PDF");
        assertThat(readme.length()).isGreaterThan(1000);
    }

    @Test void generate_manifestContainsSha256ForEveryFile() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor");
        String manifest = readZipText(pkg.bytes(), "MANIFEST.txt");
        assertThat(manifest).contains("README.pdf");
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

    @Test void generate_anonymousStillProducesReadme() throws Exception {
        // generatedBy=null must not break the README.pdf cover (it falls back to "system").
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, null);
        String readme = readZipBinaryHead(pkg.bytes(), "README.pdf");
        assertThat(readme).startsWith("%PDF");
    }


    /**
     * \u26a0\u26a0 F2-430 \u2014 {@code writeAuditCsv} ended {@code sb.toString().getBytes()}: the
     * <b>platform default</b> charset, measured {@code Cp1252} on this JDK 17 (pre-JEP 400), where
     * {@code "\u0645\u062e\u0627\u0637\u0631".getBytes()} is five {@code 0x3f} bytes \u2014 <b>five question marks</b>.
     *
     * <p>Not latent. Of the <b>1,488</b> BCM-scoped rows this package exports in dev (387 in prod),
     * <b>137</b> (11 in prod) carry a character Cp1252 cannot represent, and they are of two kinds:
     * <ul>
     *   <li>the platform's own transition arrow {@code U+2192} \u2014 136 occurrences \u2014 so
     *       {@code "BIA BIA-2026-000007 DRAFT \u2192 UNDER_REVIEW"} reaches the regulator as
     *       {@code "DRAFT ? UNDER_REVIEW"}, which <b>no longer says which state it came from</b>; and</li>
     *   <li>an Arabic personal name \u2014 the crisis <b>communication contact</b>, the person a BCM
     *       plan calls during an incident \u2014 rendered {@code "????? ?????????"}.</li>
     * </ul>
     *
     * <p>\U0001f511 The fix is the platform's own convention, one module away: {@code CalendarController}
     * declares {@code charset=UTF-8} on its ICS download <em>and</em> encodes with
     * {@code StandardCharsets.UTF_8}. No BOM is written anywhere in this codebase, so "plain UTF-8"
     * is settled by measurement rather than taste.
     */
    @Test void auditCsvIsUtf8SoTheArrowAndArabicSurviveTheRegulatorHandoff() throws Exception {
        var transition = new com.tss.grc.module.platform.auditlog.entity.AuditLogEntry();
        transition.setId(41L);
        transition.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 24, 9, 15, 0));
        transition.setUserUsername("so_officer");
        transition.setActionType("TRANSITION");
        transition.setEntityType("BcmBiaAssessment");
        transition.setSourceModule("bcm");
        // verbatim from platform.audit_log in both databases
        transition.setMessage("BIA BIA-2026-000007 DRAFT \u2192 UNDER_REVIEW");

        var arabic = new com.tss.grc.module.platform.auditlog.entity.AuditLogEntry();
        arabic.setId(42L);
        arabic.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 24, 9, 16, 0));
        arabic.setUserUsername("compliance");
        arabic.setActionType("CREATE");
        arabic.setEntityType("BcmCommunicationContact");
        arabic.setSourceModule("bcm");
        arabic.setMessage("Created communication contact: \u0646\u0648\u0631\u0629 \u0627\u0644\u0642\u062d\u0637\u0627\u0646\u064a");

        when(auditLog.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
                eq(ORG_ID), eq("bcm"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(transition, arabic)));

        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");

        // Decoded as the consumer will decode it, NOT as the writer encoded it.
        String csv = readZipText(pkg.bytes(), "audit_log.csv");
        assertThat(csv)
            .as("the transition arrow must survive \u2014 'DRAFT ? UNDER_REVIEW' does not say which "
                + "state the record came from")
            .contains("DRAFT \u2192 UNDER_REVIEW");
        assertThat(csv)
            .as("an Arabic contact name must survive on an Arabic-primary platform (house rule 7)")
            .contains("\u0646\u0648\u0631\u0629 \u0627\u0644\u0642\u062d\u0637\u0627\u0646\u064a");
        assertThat(csv)
            .as("nothing may have been replaced by '?' \u2014 the irreversible Cp1252 outcome")
            .doesNotContain("?");

        // And the bytes really are UTF-8: the arrow is the 3-byte sequence E2 86 92.
        byte[] raw = readZipBytes(pkg.bytes(), "audit_log.csv");
        assertThat(new String(raw, java.nio.charset.StandardCharsets.UTF_8))
            .as("round-tripping the emitted bytes through UTF-8 must be lossless")
            .isEqualTo(csv);
        assertThat(indexOfSeq(raw, new byte[] {(byte) 0xE2, (byte) 0x86, (byte) 0x92}))
            .as("U+2192 must be emitted as its UTF-8 encoding E2 86 92")
            .isGreaterThan(0);
    }

    /**
     * \u26a0 F2-430 \u2014 the MANIFEST is the same shape in the same file: hex digests plus one
     * em-dash. {@code U+2014} <em>is</em> representable in Cp1252 (0x97), so this half was benign on
     * <em>this</em> platform and not on ISO-8859-1 or ASCII. Closed as the sibling of a live site
     * ([[F2-215]]: fixing the field you noticed leaves the remainder looking covered).
     */
    @Test void manifestIsUtf8() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor");
        byte[] raw = readZipBytes(pkg.bytes(), "MANIFEST.txt");
        assertThat(new String(raw, java.nio.charset.StandardCharsets.UTF_8))
            .as("the manifest header carries an em-dash; UTF-8 must encode it as E2 80 94")
            .contains("\u2014");
        assertThat(indexOfSeq(raw, new byte[] {(byte) 0xE2, (byte) 0x80, (byte) 0x94}))
            .as("U+2014 must be emitted as its UTF-8 encoding, not Cp1252's single 0x97")
            .isGreaterThan(0);
    }

    private static int indexOfSeq(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= haystack.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
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

    private static byte[] readZipBytes(byte[] zipBytes, String name) throws Exception {
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry e;
            while ((e = zin.getNextEntry()) != null) {
                if (e.getName().equals(name)) {
                    return zin.readAllBytes();
                }
                zin.closeEntry();
            }
        }
        throw new IllegalStateException("Entry not found: " + name);
    }

    /**
     * Read a <b>text</b> entry the way its consumer will: <b>UTF-8</b>.
     *
     * <p>\u26a0\u26a0 F2-430 \u2014 this used to be {@code new String(zin.readAllBytes())}, the platform
     * default, which is the <em>same</em> defect the writer had. Encoding and decoding with one
     * charset round-trips losslessly whatever that charset is, so every assertion below passed
     * identically over Cp1252 bytes \u2014 <b>a round-trip test is structurally blind to a charset
     * defect, because the defect is symmetric.</b> Decode as the regulator's SIEM will, or the
     * leg proves only that the test agrees with the code ([[F2-159]]).
     */
    private static String readZipText(byte[] zipBytes, String name) throws Exception {
        return new String(readZipBytes(zipBytes, name), java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Read an entry <b>byte-transparently</b> (ISO-8859-1 maps every byte to exactly one code
     * point), for probing a binary entry's header without a decoder mangling it.
     */
    private static String readZipBinaryHead(byte[] zipBytes, String name) throws Exception {
        return new String(readZipBytes(zipBytes, name), java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    // ── F2-324: the audit CSV's selection ────────────────────────────────────────────────────────

    /**
     * \u26a0\u26a0 F2-324 \u2014 the audit CSV carried <b>15.0% of the BCM trail in dev and 6.8% in prod</b>,
     * because it selected on {@code source_module = 'bcm'} and 686 of the platform's 722
     * audit-writing sites could not set that column. It omitted 136 and 119
     * {@code BcmAssessmentCycle} rows \u2014 the trail of the very cycle the package documents \u2014 while
     * {@code README.pdf} printed the truncated count as the total.
     *
     * <p>\U0001f511 The entity vocabulary must be <b>derived</b>: {@code module/bcm} declares 52 entity
     * classes, and a constant naming them is the artefact [[F2-217]] found measuring its own
     * contents. This leg pins the narrowing \u2014 without it the package would select audit rows for
     * every entity in the platform.
     */
    @Test void bcmEntityTypes_takesOnlyThisModulesEntitiesFromTheMetamodel() {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.metamodel.Metamodel mm =
            mock(jakarta.persistence.metamodel.Metamodel.class);
        when(em.getMetamodel()).thenReturn(mm);
        // ⚠ Mockito forbids a nested when(...): entityType() stubs, so it cannot be evaluated
        // inside the outer stubbing's argument list. Build the types first.
        var bcmType   = entityType(com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle.class);
        var otherType = entityType(com.tss.grc.module.platform.auditlog.entity.AuditLogEntry.class);
        when(mm.getEntities()).thenReturn(Set.of(bcmType, otherType));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);

        assertThat(service.bcmEntityTypes()).as("""
            The metamodel filter no longer narrows to com.tss.grc.module.bcm, so the evidence package \
            would pull audit rows for entities that have nothing to do with BCM.""")
            .containsExactly("BcmAssessmentCycle");
    }

    /**
     * \u26a0\u26a0 F2-324 \u2014 with a metamodel present, {@code generate} must use the widened finder.
     *
     * <p>\u26a0 Every other leg in this class exercises the FALLBACK, because {@code @InjectMocks} leaves
     * {@code em} null \u2014 that is the pre-F2-324 selection, kept working on purpose. So the widened
     * path is reachable only from a fixture that supplies one, and without this leg the ternary could
     * be reverted with the whole class still green.
     */
    @Test void generate_withAMetamodel_usesTheWidenedAuditSelection() throws Exception {
        jakarta.persistence.EntityManager em = mock(jakarta.persistence.EntityManager.class);
        jakarta.persistence.metamodel.Metamodel mm =
            mock(jakarta.persistence.metamodel.Metamodel.class);
        when(em.getMetamodel()).thenReturn(mm);
        var bcmType = entityType(com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle.class);
        when(mm.getEntities()).thenReturn(Set.of(bcmType));
        org.springframework.test.util.ReflectionTestUtils.setField(service, "em", em);
        when(auditLog.findForModuleOrEntityTypes(eq(ORG_ID), eq("bcm"), any()))
            .thenReturn(List.of());

        service.generate(CYCLE_ID, "auditor.tester");

        verify(auditLog).findForModuleOrEntityTypes(eq(ORG_ID), eq("bcm"),
            org.mockito.ArgumentMatchers.argThat(t -> t.contains("BcmAssessmentCycle")));
        verify(auditLog, never()).findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
            any(), any(), any(Pageable.class));
    }

    /**
     * \u26a0\u26a0 F2-325 \u2014 the regulator {@code audit_log.csv} recorded when/what/where and dropped
     * <b>who</b>. The actor ({@code user_username}) is present on <b>89 of the 90</b> on-request BCM
     * rows the package carries in dev (12 of 13 in prod) and the platform's own unified viewer renders
     * a User column \u2014 so the two readers of one row disagreed, and the file that leaves the platform
     * was the incomplete one ([[F2-215]] two-of-N, one column over from [[F2-324]]'s {@code source_module}
     * in this same writer). This leg fails if the actor column is dropped or its order changes.
     *
     * <p>\U0001f511 A row with no principal (an off-request sweep or automation firing) must render a
     * <b>blank</b> actor cell, never an invented one \u2014 the honest state the viewer shows as
     * {@code "\u2014"} ([[F2-213]]/[[F2-209]]). {@code user_role} is co-populated exactly with
     * {@code user_username} (0 rows carry one without the other, both databases), so it adds no blank;
     * {@code user_id} is deliberately absent, being 0% populated in both databases ([[F2-198]]).
     */
    @Test void auditCsv_carriesTheActorAndRoleAndBlanksItHonestlyWhenAbsent() throws Exception {
        var acted = new com.tss.grc.module.platform.auditlog.entity.AuditLogEntry();
        acted.setId(7L);
        acted.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 24, 9, 15, 0));
        acted.setUserUsername("so_officer");
        acted.setUserRole("CISO,SUSTAINABILITY_OFFICER");   // comma \u2192 must be quoted by csv()
        acted.setActionType("TRANSITION");
        acted.setEntityType("BcpPlan");
        acted.setSourceModule("bcm");
        acted.setMessage("Plan approved");

        var offRequest = new com.tss.grc.module.platform.auditlog.entity.AuditLogEntry();
        offRequest.setId(8L);
        offRequest.setCreatedAt(java.time.LocalDateTime.of(2026, 8, 24, 3, 0, 0));
        offRequest.setActionType("RULE_FIRED");             // automation: no principal
        offRequest.setEntityType("BcpPlan");
        offRequest.setSourceModule("bcm");
        offRequest.setMessage("Escalation rule fired");

        when(auditLog.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(
                eq(ORG_ID), eq("bcm"), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(acted, offRequest)));

        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor.tester");
        String csv = readZipText(pkg.bytes(), "audit_log.csv");
        java.util.List<String> lines = csv.lines().toList();

        assertThat(lines.get(0)).as("the CSV must name the actor, in when\u2192who\u2192what order")
            .startsWith("id,created_at,user_username,user_role,action_type,");

        String actedLine = lines.stream().filter(l -> l.startsWith("7,")).findFirst().orElseThrow();
        assertThat(actedLine)
            .as("a row with a principal carries the actor and the role it held")
            .contains(",so_officer,")
            .contains("\"CISO,SUSTAINABILITY_OFFICER\"");

        String offLine = lines.stream().filter(l -> l.startsWith("8,")).findFirst().orElseThrow();
        assertThat(offLine)
            .as("a row with no principal blanks the actor \u2014 never a fabricated one")
            .contains(",,,RULE_FIRED,");
    }

    /** A metamodel {@code EntityType} that answers only {@code getJavaType()} \u2014 all this filter reads. */
    @SuppressWarnings("unchecked")
    private static jakarta.persistence.metamodel.EntityType<?> entityType(Class<?> c) {
        jakarta.persistence.metamodel.EntityType<?> t =
            mock(jakarta.persistence.metamodel.EntityType.class);
        when(((jakarta.persistence.metamodel.EntityType<Object>) t).getJavaType())
            .thenReturn((Class<Object>) c);
        return t;
    }
}
