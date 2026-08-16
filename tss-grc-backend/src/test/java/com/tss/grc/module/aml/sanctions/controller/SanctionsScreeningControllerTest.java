package com.tss.grc.module.aml.sanctions.controller;

import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.CheckResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.DashboardResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.EntryBulkImportResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.HitReviewRequest;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ListResponse;
import com.tss.grc.module.aml.sanctions.dto.SanctionsDtos.ScreenRequest;
import com.tss.grc.module.aml.sanctions.service.SanctionsDashboardService;
import com.tss.grc.module.aml.sanctions.service.SanctionsHitService;
import com.tss.grc.module.aml.sanctions.service.SanctionsListService;
import com.tss.grc.module.aml.sanctions.service.SanctionsScreeningService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — Smoke tests for {@link SanctionsScreeningController}.
 *
 * <p>Verifies method dispatch and response passthrough; the {@code @PreAuthorize}
 * checks run through Spring Security proxy and are covered by the integration
 * test layer elsewhere.
 */
@ExtendWith(MockitoExtension.class)
class SanctionsScreeningControllerTest {

    @Mock private SanctionsListService listService;
    @Mock private SanctionsScreeningService screeningService;
    @Mock private SanctionsHitService hitService;
    @Mock private SanctionsDashboardService dashboardService;
    @InjectMocks private SanctionsScreeningController controller;

    private static ListResponse stubList() {
        return new ListResponse(
            100L, UUID.randomUUID(), null,
            "OFAC-SDN", "OFAC SDN", "OFAC SDN",
            "https://placeholder", "OFAC",
            LocalDateTime.now(), "DAILY", 16000, true,
            LocalDateTime.now(), null);
    }

    private static CheckResponse stubCheck(String result) {
        return new CheckResponse(
            500L, UUID.randomUUID(), 7L,
            "SCR-2026-000001", "CUSTOMER_ONBOARDING", "CUST-1",
            "Ahmed Al-Saud", "أحمد آل سعود",
            null, "SA", null,
            LocalDateTime.now(), "REAL_TIME_API", "OFAC-SDN,UN-CONSOLIDATED",
            result, List.of(),
            LocalDateTime.now(), null);
    }

    private static HitResponse stubHit() {
        return new HitResponse(
            900L, UUID.randomUUID(), 500L, 11L,
            "OFAC-SDN", "Ali Hassan Mahmoud", "علي حسن محمود",
            BigDecimal.valueOf(92.00), "FUZZY_NAME", "PENDING_REVIEW",
            null, null, null, null, false,
            null, null, null, LocalDateTime.now());
    }

    @Test void listLists_dispatchesToService() {
        when(listService.listVisible()).thenReturn(List.of(stubList()));
        List<ListResponse> out = controller.listLists();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).listCode()).isEqualTo("OFAC-SDN");
        verify(listService).listVisible();
    }

    @Test void refreshList_dispatches() {
        when(listService.refresh(100L)).thenReturn(stubList());
        ListResponse out = controller.refreshList(100L);
        assertThat(out.listCode()).isEqualTo("OFAC-SDN");
        verify(listService).refresh(100L);
    }

    @Test void performScreening_returnsResultPassthrough() {
        ScreenRequest req = new ScreenRequest(
            "CUSTOMER_ONBOARDING", "CUST-X", "Ali", null,
            null, null, null, "MANUAL", List.of("OFAC-SDN"));
        when(screeningService.screen(any(ScreenRequest.class))).thenReturn(stubCheck("POTENTIAL_HIT"));
        CheckResponse out = controller.performScreening(req);
        assertThat(out.result()).isEqualTo("POTENTIAL_HIT");
        verify(screeningService).screen(req);
    }

    @Test void listChecks_filterParam_routes() {
        when(screeningService.listChecksByResult("CONFIRMED_HIT"))
            .thenReturn(List.of(stubCheck("CONFIRMED_HIT")));
        List<CheckResponse> out = controller.listChecks("CONFIRMED_HIT");
        assertThat(out).hasSize(1);
        assertThat(out.get(0).result()).isEqualTo("CONFIRMED_HIT");
    }

    @Test void listHits_defaultStatusIsPendingReview() {
        when(hitService.listByStatus("PENDING_REVIEW")).thenReturn(List.of(stubHit()));
        List<HitResponse> out = controller.listHits("PENDING_REVIEW");
        assertThat(out).hasSize(1);
        verify(hitService).listByStatus("PENDING_REVIEW");
    }

    @Test void reviewHit_dispatches() {
        HitReviewRequest req = new HitReviewRequest(
            "FALSE_POSITIVE", "common name", "اسم شائع", null);
        when(hitService.reviewHit(eq(900L), any())).thenReturn(stubHit());
        controller.reviewHit(900L, req);
        verify(hitService).reviewHit(900L, req);
    }

    @Test void dashboard_returnsSnapshot() {
        DashboardResponse snap = new DashboardResponse(
            8, 8, 2, 1, 0, 0, 0, 0.0, LocalDateTime.now());
        when(dashboardService.dashboard()).thenReturn(snap);
        DashboardResponse out = controller.dashboard();
        assertThat(out.checksLast24h()).isEqualTo(8L);
        assertThat(out.hitsPendingReview()).isEqualTo(2L);
    }

    @Test void bulkImport_pathBodyMismatchRejected() {
        EntryBulkImportRequest req = new EntryBulkImportRequest(999L, List.of());
        assertThatThrownBy(() -> controller.bulkImportEntries(100L, req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mismatch");
    }

    @Test void bulkImport_matchedIds_dispatches() {
        EntryBulkImportRequest req = new EntryBulkImportRequest(100L, List.of());
        when(listService.bulkImportEntries(req)).thenReturn(
            new EntryBulkImportResponse(0, 0, 0));
        controller.bulkImportEntries(100L, req);
        verify(listService).bulkImportEntries(req);
    }
}
