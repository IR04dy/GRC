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
        String readme = readZipEntry(pkg.bytes(), "README.pdf");
        assertThat(readme).startsWith("%PDF");
        assertThat(readme.length()).isGreaterThan(1000);
    }

    @Test void generate_manifestContainsSha256ForEveryFile() throws Exception {
        BcmEvidencePackageService.EvidencePackage pkg = service.generate(CYCLE_ID, "auditor");
        String manifest = readZipEntry(pkg.bytes(), "MANIFEST.txt");
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
        String readme = readZipEntry(pkg.bytes(), "README.pdf");
        assertThat(readme).startsWith("%PDF");
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
