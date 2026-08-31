package com.tss.grc.module.reginspect.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateDocumentRequestRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateInspectionRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.DashboardResponse;
import com.tss.grc.module.reginspect.entity.RegDocumentRequest;
import com.tss.grc.module.reginspect.entity.RegInspection;
import com.tss.grc.module.reginspect.repository.RegDocumentRequestRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionUpdateRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-354 — {@code RegInspectService.generateReference} must mint the SAMA/NCA on-site-inspection
 * reference {@code <regulator>-OSI-<year>-Q<q>-NNN} from {@code MAX(ordinal) + 1}, not from a
 * {@code count(...) + 1} over a capped {@code PageRequest.of(0, 200)} window.
 *
 * <p>The old seed was wrong two ways: the derived finder respects {@code @Where(is_deleted = 0)}
 * (live rows only), and the 200-row window is itself capped — so a soft-delete gap, a concurrent
 * create, or more than 200 inspections in a quarter re-minted an in-use ordinal, and the filtered
 * unique index {@code ux_reginspect_external_reference (organisation_id, external_reference) WHERE
 * is_deleted = 0} (V341) then 500'd the INSERT (the F2-337 / F2-351 / F2-353 collision shape).
 *
 * <p>A unit test cannot trigger a real unique-index 500, so the proof is the SEEDING (MAX, not the
 * capped count — {@code verify(never())} the capped {@code Pageable} finder), the skip-loop over a
 * live collision, and the year+quarter scope of the seed pattern. Reverting to the capped-count body
 * makes these legs fail (the MAX/exists stubs go unused under strict stubbing, and the reverted body
 * calls the un-stubbed capped finder — a null {@code Page} it dereferences). F2-248: the service had
 * no test.
 *
 * <p>F2-355 — the same MAX-seed rule for the document-request reference {@code <inspRef>-DR-NNN},
 * which {@code createDocumentRequest} seeded from {@code findBy…OrderByRequestedAtDesc(...).size() +
 * 1} (live-only, {@code @Where}-scoped). Milder than the inspection reference: the DR reference
 * carries no unique index, so a soft-delete gap or a concurrent create yields a silent DUPLICATE
 * rather than a 500. The DR legs assert the ordinal seeds from {@code findMaxDrOrdinal} (value 8 from
 * MAX 7, not 1 from the empty live-count list) and steps over a live collision.
 */
@ExtendWith(MockitoExtension.class)
class RegInspectServiceTest {

    private static final Long ORG = 4L;

    @Mock private RegInspectionRepository inspectionRepo;
    @Mock private RegDocumentRequestRepository drRepo;
    @Mock private RegInspectionUpdateRepository updateRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher publisher;
    @InjectMocks private RegInspectService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** Drives the public create with a save stub, returning the external reference the service minted. */
    private String mintedReference() {
        when(inspectionRepo.save(any(RegInspection.class))).thenAnswer(inv -> {
            RegInspection i = inv.getArgument(0);
            i.setId(77L);
            return i;
        });
        service.createInspection(CreateInspectionRequest.builder()
                .regulator("SAMA").titleEn("On-site review").titleAr("مراجعة ميدانية").build());
        ArgumentCaptor<RegInspection> cap = ArgumentCaptor.forClass(RegInspection.class);
        verify(inspectionRepo).save(cap.capture());
        return cap.getValue().getExternalReference();
    }

    @Test
    void firstInspectionOrdinalIsOne() {
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(0);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        String ref = mintedReference();

        assertThat(ref).startsWith("SAMA-OSI-").endsWith("-001");
        // Sourcing (the defect): the ordinal comes from the uncapped MAX finder, never the capped
        // PageRequest window.
        verify(inspectionRepo, never())
                .findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class));
    }

    @Test
    void ordinalSeedsFromMaxNotCappedCount() {
        // Seven ordinals issued this quarter (some rows may be soft-deleted, so a live count over a
        // capped window would be lower and re-mint a live ordinal — the collision). MAX + 1 is next.
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(7);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        assertThat(mintedReference()).endsWith("-008");
        verify(inspectionRepo, never())
                .findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class));
    }

    @Test
    void ordinalSkipsAnExistingLiveReference() {
        // A peer create grabbed ordinal 6 in the window between the seed read and this INSERT; the
        // skip-loop (live-only existsBy) steps over it.
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), anyString())).thenReturn(5);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(
                eq(ORG), argThat(s -> s != null && s.endsWith("-006")))).thenReturn(true);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(
                eq(ORG), argThat(s -> s != null && s.endsWith("-007")))).thenReturn(false);

        assertThat(mintedReference()).endsWith("-007");
    }

    @Test
    void seedPatternScopesToYearAndQuarter() {
        ArgumentCaptor<String> pat = ArgumentCaptor.forClass(String.class);
        when(inspectionRepo.findMaxOsiOrdinal(eq(ORG), pat.capture())).thenReturn(0);
        when(inspectionRepo.existsByOrganisationIdAndExternalReference(eq(ORG), anyString()))
                .thenReturn(false);

        mintedReference();

        // The MAX seed is scoped to every regulator's inspection this year+quarter (the ordinal is a
        // shared per-quarter sequence), so the pattern is %-OSI-<year>-Q<q>-%.
        assertThat(pat.getValue()).contains("-OSI-").contains("-Q").startsWith("%").endsWith("-%");
    }

    // ── F2-355: document-request reference seeds from MAX, not the live count ──────────

    private RegInspection inspection(long id, String externalReference) {
        RegInspection i = new RegInspection();
        i.setId(id);
        i.setOrganisationId(ORG);
        i.setExternalReference(externalReference);
        i.setRegulator("SAMA");
        i.setStatus("PLANNED");
        return i;
    }

    /** Drives createDocumentRequest with a save stub, returning the DR reference the service minted. */
    private String mintedDrReference(RegInspection insp) {
        when(inspectionRepo.findByIdAndOrganisationId(insp.getId(), ORG)).thenReturn(Optional.of(insp));
        when(drRepo.save(any(RegDocumentRequest.class))).thenAnswer(inv -> {
            RegDocumentRequest dr = inv.getArgument(0);
            dr.setId(55L);
            return dr;
        });
        service.createDocumentRequest(insp.getId(), CreateDocumentRequestRequest.builder()
                .titleEn("Provide the policy").titleAr("قدّم السياسة")
                .dueDate(LocalDate.now().plusDays(7)).build());
        ArgumentCaptor<RegDocumentRequest> cap = ArgumentCaptor.forClass(RegDocumentRequest.class);
        verify(drRepo).save(cap.capture());
        return cap.getValue().getReference();
    }

    @Test
    void firstDocumentRequestOrdinalIsOne() {
        RegInspection insp = inspection(10L, "SAMA-OSI-2026-Q1-001");
        when(drRepo.findMaxDrOrdinal(10L, ORG)).thenReturn(0);
        when(drRepo.existsByInspectionIdAndOrganisationIdAndReference(eq(10L), eq(ORG), anyString()))
                .thenReturn(false);

        assertThat(mintedDrReference(insp)).isEqualTo("SAMA-OSI-2026-Q1-001-DR-001");
    }

    @Test
    void documentRequestOrdinalSeedsFromMaxNotLiveCount() {
        // Seven DR ordinals issued (some rows may be soft-deleted, so the live-count seed would be
        // lower and re-mint a live ordinal — the duplicate). MAX + 1 is the next free ordinal.
        RegInspection insp = inspection(10L, "SAMA-OSI-2026-Q1-001");
        when(drRepo.findMaxDrOrdinal(10L, ORG)).thenReturn(7);
        when(drRepo.existsByInspectionIdAndOrganisationIdAndReference(eq(10L), eq(ORG), anyString()))
                .thenReturn(false);

        // 8 (from MAX 7), not 1 (which the empty live-count list would have produced).
        assertThat(mintedDrReference(insp)).isEqualTo("SAMA-OSI-2026-Q1-001-DR-008");
    }

    @Test
    void documentRequestSkipsAnExistingLiveReference() {
        // A peer create grabbed ordinal 6; the skip-loop (live-only existsBy) steps over it.
        RegInspection insp = inspection(10L, "SAMA-OSI-2026-Q1-001");
        when(drRepo.findMaxDrOrdinal(10L, ORG)).thenReturn(5);
        when(drRepo.existsByInspectionIdAndOrganisationIdAndReference(
                eq(10L), eq(ORG), argThat(s -> s != null && s.endsWith("-DR-006")))).thenReturn(true);
        when(drRepo.existsByInspectionIdAndOrganisationIdAndReference(
                eq(10L), eq(ORG), argThat(s -> s != null && s.endsWith("-DR-007")))).thenReturn(false);

        assertThat(mintedDrReference(insp)).isEqualTo("SAMA-OSI-2026-Q1-001-DR-007");
    }

    // ── F2-406: dashboard DR-counter sum covers ALL inspections, not the recent-10 window ──────

    private static RegInspection dashInsp(long id, int openDr, int overdueDr) {
        RegInspection i = new RegInspection();
        i.setId(id);
        i.setOpenDrCount(openDr);
        i.setOverdueDrCount(overdueDr);
        return i;
    }

    @Test
    void dashboardSumsDrCountersOverAllInspectionsNotJustTheRecentWindow() {
        // 12 live inspections, ordered createdAt DESC. The 10 most-recent carry ONE open DR between
        // them; the 2 older (outside a top-10 window) still hold the bulk — 10 open + 4 overdue. A
        // dashboard that sums only the recent-10 window under-reports the tenant-wide DR KPIs.
        List<RegInspection> all = new ArrayList<>();
        all.add(dashInsp(1L, 1, 0));                              // most-recent, 1 open DR
        for (long id = 2; id <= 10; id++) all.add(dashInsp(id, 0, 0)); // 9 more recent, 0 DRs
        all.add(dashInsp(11L, 5, 2));                             // older, outside the window
        all.add(dashInsp(12L, 5, 2));                             // older, outside the window

        when(inspectionRepo.countByOrganisationIdAndStatusIn(eq(ORG), anyCollection())).thenReturn(3L);
        when(inspectionRepo.countByOrganisationIdAndStatus(ORG, "CLOSED")).thenReturn(9L);
        // The fix reads the uncapped finder; the pre-fix body read the capped Page. Both stubbed
        // lenient so the pre-fix control fails on the VALUE, not on an UnnecessaryStubbingException.
        lenient().when(inspectionRepo.findByOrganisationIdOrderByCreatedAtDesc(ORG)).thenReturn(all);
        lenient().when(inspectionRepo
                        .findByOrganisationIdOrderByCreatedAtDesc(eq(ORG), any(Pageable.class)))
                .thenReturn(new PageImpl<>(all.subList(0, 10)));

        DashboardResponse r = service.dashboard();

        // Discriminating: the open/overdue DR KPIs cover ALL 12 inspections (1 + 5 + 5 = 11 open,
        // 0 + 2 + 2 = 4 overdue), where the old windowed sum saw only the recent-10 (1 open / 0 overdue).
        assertThat(r.getOpenDrCount()).isEqualTo(11L);
        assertThat(r.getOverdueDrCount()).isEqualTo(4L);
        // Guards (F2-214): the all-time counts are unchanged and recentInspections is still the top-10.
        assertThat(r.getActiveCount()).isEqualTo(3L);
        assertThat(r.getClosedCount()).isEqualTo(9L);
        assertThat(r.getRecentInspections()).hasSize(10);
    }
}
