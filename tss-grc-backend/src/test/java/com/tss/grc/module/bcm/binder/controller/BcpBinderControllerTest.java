package com.tss.grc.module.bcm.binder.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.binder.dto.BcpBinderDtos.BinderResponse;
import com.tss.grc.module.bcm.binder.entity.BcpBinderExport;
import com.tss.grc.module.bcm.binder.repository.BcpBinderExportRepository;
import com.tss.grc.module.bcm.binder.service.BcpBinderPdfBuilder;
import com.tss.grc.module.bcm.binder.service.BcpBinderService;
import com.tss.grc.module.bcm.cycle.entity.BcpPlan;
import com.tss.grc.module.bcm.cycle.entity.BcpPlanAcknowledgement;
import com.tss.grc.module.bcm.cycle.entity.BcpSection;
import com.tss.grc.module.bcm.cycle.entity.BcpVersion;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanAcknowledgementRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpPlanRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpSectionRepository;
import com.tss.grc.module.bcm.cycle.repository.BcpVersionRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.entity.Organisation;
import com.tss.grc.module.platform.repository.OrganisationRepository;
import com.tss.grc.security.GrcUserDetails;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Slice 10-F — slice-style controller test for the BCP binder REST surface.
 *
 * <p>Wires a real {@link BcpBinderService} + real {@link BcpBinderPdfBuilder}
 * against mocked repos / audit / publisher and drives the controller
 * directly — same pattern as {@code WebPushControllerTest} and the
 * Sprint-12 adapter controller tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>generate → 202 ACCEPTED + Location header</li>
 *   <li>list → newest-first ordering preserved from the repository</li>
 *   <li>get → detail response</li>
 *   <li>download → octet-stream PDF with proper Content-Disposition</li>
 *   <li>archive → 200 with status=ARCHIVED</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BcpBinderControllerTest {

    private static final Long ORG_ID = 88L;
    private static final Long PLAN_ID = 50L;
    private static final Long USER_ID = 9001L;

    @Mock private BcpBinderExportRepository binders;
    @Mock private BcpPlanRepository plans;
    @Mock private BcpSectionRepository sections;
    @Mock private BcpVersionRepository versions;
    @Mock private BcpPlanAcknowledgementRepository contacts;
    @Mock private OrganisationRepository orgs;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;

    private BcpBinderController controller;
    private BcpBinderService service;

    @TempDir Path tempStorage;

    @BeforeEach
    void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        var principal = new GrcUserDetails(USER_ID, ORG_ID, "bcm.admin", "x",
            true, true, List.of(new SimpleGrantedAuthority("ROLE_BCM_MANAGER")));
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", principal.getAuthorities()));

        when(binders.save(any(BcpBinderExport.class))).thenAnswer(inv -> {
            BcpBinderExport b = inv.getArgument(0);
            if (b.getId() == null) b.setId(7001L);
            return b;
        });

        BcpBinderPdfBuilder pdfBuilder = new BcpBinderPdfBuilder();
        service = new BcpBinderService(binders, plans, sections, versions, contacts,
            orgs, pdfBuilder, audit, publisher);
        ReflectionTestUtils.setField(service, "storageBasePath", tempStorage.toString());
        controller = new BcpBinderController(service);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
        SecurityContextHolder.clearContext();
    }

    private BcpPlan planFixture() {
        BcpPlan p = new BcpPlan();
        p.setId(PLAN_ID); p.setOrganisationId(ORG_ID);
        p.setPlanCode("BCP-2026-099"); p.setTitleEn("Critical service");
        p.setPlanType("BCP"); p.setCurrentVersion(1); p.setStatus("PUBLISHED");
        return p;
    }

    private void stubLookups() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID)).thenReturn(Optional.of(planFixture()));
        Organisation o = new Organisation();
        o.setId(ORG_ID); o.setNameEn("Test Bank"); o.setNameAr("بنك الاختبار");
        when(orgs.findById(ORG_ID)).thenReturn(Optional.of(o));
        when(sections.findByOrganisationIdAndPlanIdOrderBySectionOrderAsc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpSection>emptyList());
        when(versions.findByOrganisationIdAndPlanIdOrderByVersionNumberDesc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpVersion>emptyList());
        when(contacts.findByOrganisationIdAndPlanIdOrderByDistributedAtDesc(ORG_ID, PLAN_ID))
            .thenReturn(Collections.<BcpPlanAcknowledgement>emptyList());
    }

    @Test void generate_returns202WithLocationHeader() {
        stubLookups();
        ResponseEntity<BinderResponse> resp = controller.generate(PLAN_ID, "BILINGUAL");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        assertThat(resp.getHeaders().getLocation().toString())
            .startsWith("/api/v1/bcm/binders/");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("READY");
    }

    @Test void list_returnsNewestFirst() {
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID)).thenReturn(Optional.of(planFixture()));
        BcpBinderExport b1 = ready(1L, "BCP-EXP-2026-AAA");
        BcpBinderExport b2 = ready(2L, "BCP-EXP-2026-BBB");
        when(binders.findByOrganisationIdAndBcpPlanIdOrderByGeneratedAtDesc(ORG_ID, PLAN_ID))
            .thenReturn(List.of(b2, b1));

        List<BinderResponse> out = controller.list(PLAN_ID);

        assertThat(out).hasSize(2);
        assertThat(out.get(0).exportCode()).isEqualTo("BCP-EXP-2026-BBB");
        assertThat(out.get(1).exportCode()).isEqualTo("BCP-EXP-2026-AAA");
    }

    @Test void get_returnsDetail() {
        BcpBinderExport b = ready(33L, "BCP-EXP-2026-XYZ");
        when(binders.findByOrganisationIdAndId(ORG_ID, 33L)).thenReturn(Optional.of(b));

        BinderResponse out = controller.get(33L);

        assertThat(out.id()).isEqualTo(33L);
        assertThat(out.exportCode()).isEqualTo("BCP-EXP-2026-XYZ");
    }

    @Test void download_returnsOctetStreamPdf() {
        stubLookups();
        // First — generate so we have a file on disk and a real row.
        BinderResponse generated = controller.generate(PLAN_ID, "BILINGUAL").getBody();
        assertThat(generated).isNotNull();

        // Wire up the find for download — the captured saved row carries
        // the file_path; we reuse the live object from binders.save() side
        // effects rather than rebuilding fixtures from scratch.
        org.mockito.ArgumentCaptor<BcpBinderExport> cap =
            org.mockito.ArgumentCaptor.forClass(BcpBinderExport.class);
        org.mockito.Mockito.verify(binders, org.mockito.Mockito.atLeast(1)).save(cap.capture());
        BcpBinderExport saved = cap.getAllValues().get(cap.getAllValues().size() - 1);
        saved.setId(generated.id());
        when(binders.findByOrganisationIdAndId(ORG_ID, generated.id()))
            .thenReturn(Optional.of(saved));

        ResponseEntity<byte[]> resp = controller.download(generated.id());

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(resp.getHeaders().getFirst("Content-Disposition"))
            .contains("attachment").contains(".pdf");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().length).isGreaterThan(1000);
        assertThat(new String(resp.getBody(), 0, 5)).isEqualTo("%PDF-");
    }

    @Test void archive_returnsStatusArchived() {
        BcpBinderExport b = ready(44L, "BCP-EXP-2026-DEL");
        when(binders.findByOrganisationIdAndId(ORG_ID, 44L)).thenReturn(Optional.of(b));
        when(plans.findByIdAndOrganisationId(PLAN_ID, ORG_ID))
            .thenReturn(Optional.of(planFixture()));

        ResponseEntity<BinderResponse> resp = controller.archive(44L);

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("ARCHIVED");
    }

    private BcpBinderExport ready(long id, String code) {
        BcpBinderExport b = new BcpBinderExport();
        b.setId(id); b.setOrganisationId(ORG_ID); b.setBcpPlanId(PLAN_ID);
        b.setExportCode(code); b.setStatus("READY"); b.setLanguage("BILINGUAL");
        b.setVersionLabel("v1"); b.setIsDeleted(false);
        return b;
    }
}
