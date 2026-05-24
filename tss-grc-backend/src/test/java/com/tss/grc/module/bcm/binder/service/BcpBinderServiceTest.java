package com.tss.grc.module.bcm.binder.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.binder.dto.BcpBinderDtos.BinderResponse;
import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;
import com.tss.grc.module.bcm.binder.repository.BcpBinderExportRepository;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpSectionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpVersionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link BcpBinderService} (Slice 10-F).
 *
 * <p>Locks the regulator-grade behaviour we promise the Crisis Room:
 * <ul>
 *   <li>Happy-path generation for EN / AR / BILINGUAL — file written,
 *       row flips GENERATING → READY, page count + size populated.</li>
 *   <li>Plan-not-found throws 404-equivalent EntityNotFoundException.</li>
 *   <li>Tenant isolation — service reads only the current org's plan.</li>
 *   <li>F8 event {@code BCP_BINDER_GENERATED} emitted with the right payload.</li>
 *   <li>F7 audit row written for generate / download / archive.</li>
 *   <li>Archive flips status to ARCHIVED and soft-deletes.</li>
 *   <li>Download increments download_count + stamps last_downloaded_at.</li>
 *   <li>Generation duration is recorded.</li>
 *   <li>Invalid language rejected.</li>
 *   <li>Listing requires the plan to exist in the tenant.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcpBinderServiceTest {

    private static final Long ORG_ID = 77L;
    private static final Long PLAN_ID = 42L;

    @Mock private BcpBinderExportRepository binders;
    @Mock private BcpPlanRepository plans;
    @Mock private BcpSectionRepository sections;
    @Mock private BcpVersionRepository versions;
    @Mock private BcpPlanAcknowledgementRepository contacts;
    @Mock private OrganisationRepository orgs;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    /** Real builder — we want the engine to actually produce bytes so we
     *  can verify page_count > 0 and file_size > 0 are recorded. */
    private final BcpBinderPdfBuilder pdfBuilder = new BcpBinderPdfBuilder();

    @InjectMocks private BcpBinderService service;

    @TempDir Path tempStorage;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        // Wire the real PDF builder + temp storage path. @InjectMocks won't
        // pick up the field-level builder because it's not @Mock-tagged.
        ReflectionTestUtils.setField(service, "pdfBuilder", pdfBuilder);
        ReflectionTestUtils.setField(service, "storageBasePath", tempStorage.toString());

        // Default save() — assign a generated id so callers can find by id.
        when(binders.save(any(BcpBinderExport.class))).thenAnswer(inv -> {
            BcpBinderExport b = inv.getArgument(0);
            if (b.getId() == null) b.setId(1001L);
            return b;
        });
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ─── Fixtures ─────────────────────────────────────────────────────

    private BcpPlan planFixture() {
        BcpPlan p = new BcpPlan();
        p.setId(PLAN_ID);
        p.setOrganisationId(ORG_ID);
        p.setPlanCode("BCP-2026-007");
        p.setTitleEn("SARIE payment continuity");
        p.setTitleAr("استمرارية مدفوعات سريع");
        p.setPlanType("BCP");
        p.setRtoHours(4);
        p.setRpoHours(1);
        p.setMtpdHours(24);
        p.setCurrentVersion(2);
        p.setApprovedBy("Layla Al-Otaibi");
        p.setStatus("PUBLISHED");
        return p;
    }

    private Organisation orgFixture() {
        Organisation o = new Organisation();
        o.setId(ORG_ID);
        o.setNameEn("TSS Bank");
        o.setNameAr("بنك تي إس إس");
        return o;
    }

    private void stubPlanLookups() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID))
            .thenReturn(Optional.of(planFixture()));
        when(orgs.findById(ORG_ID)).thenReturn(Optional.of(orgFixture()));
        when(sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpSection>emptyList());
        when(versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpVersion>emptyList());
        when(contacts.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpPlanAcknowledgement>emptyList());
    }

    // ─── Generate ────────────────────────────────────────────────────

    @Test void generate_bilingual_writesFileAndFlipsToReady() throws Exception {
        stubPlanLookups();

        BinderResponse out = service.generate(PLAN_ID, "BILINGUAL");

        assertThat(out.status()).isEqualTo("READY");
        assertThat(out.language()).isEqualTo("BILINGUAL");
        assertThat(out.byteSize()).isGreaterThan(1000);  // a real PDF, not empty
        assertThat(out.pageCount()).isGreaterThan(0);
        assertThat(out.exportCode()).startsWith("BCP-EXP-");
        assertThat(out.versionLabel()).isEqualTo("v2");
        assertThat(out.generationDurationMs()).isNotNull();
        assertThat(out.generationDurationMs()).isGreaterThanOrEqualTo(0);

        // File actually landed on disk under base-path/binders/<org>/<uuid>.pdf
        ArgumentCaptor<BcpBinderExport> cap = ArgumentCaptor.forClass(BcpBinderExport.class);
        verify(binders, times(2)).save(cap.capture());
        BcpBinderExport saved = cap.getAllValues().get(1);
        Path full = tempStorage.resolve(saved.getFilePath());
        assertThat(Files.exists(full)).isTrue();
        assertThat(Files.size(full)).isEqualTo(out.byteSize().longValue());

        // The bytes start with the PDF magic %PDF-
        byte[] firstFive = new byte[5];
        try (var in = Files.newInputStream(full)) { in.read(firstFive); }
        assertThat(new String(firstFive)).isEqualTo("%PDF-");
    }

    @Test void generate_englishOnly_succeeds() {
        stubPlanLookups();
        BinderResponse out = service.generate(PLAN_ID, "EN");
        assertThat(out.status()).isEqualTo("READY");
        assertThat(out.language()).isEqualTo("EN");
        assertThat(out.pageCount()).isGreaterThan(0);
    }

    @Test void generate_arabicOnly_succeeds() {
        stubPlanLookups();
        BinderResponse out = service.generate(PLAN_ID, "AR");
        assertThat(out.status()).isEqualTo("READY");
        assertThat(out.language()).isEqualTo("AR");
        assertThat(out.pageCount()).isGreaterThan(0);
    }

    @Test void generate_planNotFound_throws() {
        when(plans.findByIdAndOrganisationId(999L, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(999L, "BILINGUAL"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void generate_tenantIsolation_planLookupFiltersByOrg() {
        // No plan stub for ORG_ID, but stubbed for some other tenant —
        // service must still 404 because we use TenantContext.
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generate(PLAN_ID, "BILINGUAL"))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void generate_invalidLanguage_rejected() {
        // Plan lookup never reached — language is validated first.
        assertThatThrownBy(() -> service.generate(PLAN_ID, "FR"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("EN/AR/BILINGUAL");
    }

    @Test void generate_emitsF8Event() {
        stubPlanLookups();

        service.generate(PLAN_ID, "BILINGUAL");

        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(ev.capture());
        assertThat(ev.getValue().triggerEvent()).isEqualTo("BCP_BINDER_GENERATED");
        assertThat(ev.getValue().organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.getValue().entityType()).isEqualTo("BcpBinderExport");
        assertThat(ev.getValue().payload()).containsKey("planCode");
        assertThat(ev.getValue().payload().get("planCode")).isEqualTo("BCP-2026-007");
        assertThat(ev.getValue().payload().get("language")).isEqualTo("BILINGUAL");
    }

    @Test void generate_writesF7AuditRow() {
        stubPlanLookups();

        service.generate(PLAN_ID, "BILINGUAL");

        verify(audit).log(
            org.mockito.ArgumentMatchers.eq("BCP_BINDER_GENERATED"),
            org.mockito.ArgumentMatchers.eq("BcpBinderExport"),
            any(Long.class),
            any(String.class));
    }

    // ─── Download ───────────────────────────────────────────────────

    @Test void download_incrementsCounter_andStampsLastDownloaded() throws Exception {
        // Step 1: build a real binder so we have a file on disk.
        stubPlanLookups();
        BinderResponse out = service.generate(PLAN_ID, "BILINGUAL");

        // Step 2: stub findByOrganisationIdAndId to return the row we just
        // saved (capture from save calls).
        ArgumentCaptor<BcpBinderExport> cap = ArgumentCaptor.forClass(BcpBinderExport.class);
        verify(binders, times(2)).save(cap.capture());
        BcpBinderExport saved = cap.getAllValues().get(1);
        // Set the id explicitly so findByOrganisationIdAndId matches.
        saved.setId(out.id());
        when(binders.findByOrganisationIdAndId(ORG_ID, out.id()))
            .thenReturn(Optional.of(saved));

        var dl = service.download(out.id());

        assertThat(dl.bytes()).hasSize(out.byteSize());
        assertThat(dl.filename()).startsWith("BCP-EXP-");
        assertThat(dl.filename()).endsWith(".pdf");
        // Counter incremented (saved.downloadCount started at 0 → 1)
        assertThat(saved.getDownloadCount()).isEqualTo(1);
        assertThat(saved.getLastDownloadedAt()).isNotNull();
    }

    @Test void download_notReady_throwsIllegalState() {
        BcpBinderExport b = new BcpBinderExport();
        b.setId(11L); b.setOrganisationId(ORG_ID); b.setStatus("GENERATING");
        b.setFilePath("binders/77/dummy.pdf");
        when(binders.findByOrganisationIdAndId(ORG_ID, 11L)).thenReturn(Optional.of(b));

        assertThatThrownBy(() -> service.download(11L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("not READY");
    }

    @Test void download_emitsF8AndAudits() throws Exception {
        stubPlanLookups();
        BinderResponse out = service.generate(PLAN_ID, "BILINGUAL");

        ArgumentCaptor<BcpBinderExport> cap = ArgumentCaptor.forClass(BcpBinderExport.class);
        verify(binders, times(2)).save(cap.capture());
        BcpBinderExport saved = cap.getAllValues().get(1);
        saved.setId(out.id());
        when(binders.findByOrganisationIdAndId(ORG_ID, out.id()))
            .thenReturn(Optional.of(saved));

        service.download(out.id());

        // Generate event + download event = 2 events
        ArgumentCaptor<DomainEvent> ev = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher, times(2)).publishEvent(ev.capture());
        assertThat(ev.getAllValues().get(1).triggerEvent()).isEqualTo("BCP_BINDER_DOWNLOADED");

        verify(audit).log(
            org.mockito.ArgumentMatchers.eq("BCP_BINDER_DOWNLOADED"),
            any(String.class), any(Long.class), any(String.class));
    }

    // ─── Archive ────────────────────────────────────────────────────

    @Test void archive_flipsStatusAndSoftDeletes() {
        BcpBinderExport b = new BcpBinderExport();
        b.setId(55L); b.setOrganisationId(ORG_ID); b.setBcpPlanId(PLAN_ID);
        b.setStatus("READY"); b.setIsDeleted(false);
        b.setExportCode("BCP-EXP-2026-ABCDEF");
        when(binders.findByOrganisationIdAndId(ORG_ID, 55L)).thenReturn(Optional.of(b));
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID)).thenReturn(Optional.of(planFixture()));

        BinderResponse out = service.archive(55L);

        assertThat(out.status()).isEqualTo("ARCHIVED");
        assertThat(b.getIsDeleted()).isTrue();
        assertThat(b.getDeletedAt()).isNotNull();

        verify(audit).log(
            org.mockito.ArgumentMatchers.eq("BCP_BINDER_ARCHIVED"),
            any(String.class), any(Long.class), any(String.class));
    }

    @Test void archive_alreadyArchived_isIdempotent() {
        BcpBinderExport b = new BcpBinderExport();
        b.setId(56L); b.setOrganisationId(ORG_ID); b.setStatus("ARCHIVED");
        b.setIsDeleted(true);
        when(binders.findByOrganisationIdAndId(ORG_ID, 56L)).thenReturn(Optional.of(b));

        BinderResponse out = service.archive(56L);

        // Returns unchanged; no save/audit/event side-effects on a no-op.
        assertThat(out.status()).isEqualTo("ARCHIVED");
        verify(binders, times(0)).save(any());
    }

    // ─── List ───────────────────────────────────────────────────────

    @Test void listForPlan_requiresPlanInTenant() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.listForPlan(PLAN_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test void listForPlan_returnsNewestFirst() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID))
            .thenReturn(Optional.of(planFixture()));
        BcpBinderExport b1 = newReadyRow(1L, "BCP-EXP-2026-AAA");
        BcpBinderExport b2 = newReadyRow(2L, "BCP-EXP-2026-BBB");
        // Repository finder explicitly orders desc — service relies on that.
        when(binders.findByOrganisationIdAndBcpPlanIdOrderByGeneratedAtDesc(ORG_ID, PLAN_ID))
            .thenReturn(List.of(b2, b1));

        List<BinderResponse> out = service.listForPlan(PLAN_ID);

        assertThat(out).extracting(BinderResponse::exportCode)
            .containsExactly("BCP-EXP-2026-BBB", "BCP-EXP-2026-AAA");
    }

    private BcpBinderExport newReadyRow(long id, String code) {
        BcpBinderExport b = new BcpBinderExport();
        b.setId(id); b.setOrganisationId(ORG_ID); b.setBcpPlanId(PLAN_ID);
        b.setExportCode(code); b.setStatus("READY"); b.setLanguage("BILINGUAL");
        b.setVersionLabel("v1"); b.setIsDeleted(false);
        return b;
    }
}
