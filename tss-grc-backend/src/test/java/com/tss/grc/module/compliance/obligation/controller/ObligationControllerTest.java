package com.tss.grc.module.compliance.obligation.controller;

import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationCreateRequest;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationKpis;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationResponse;
import com.tss.grc.module.compliance.obligation.dto.ObligationDtos.ObligationTransitionRequest;
import com.tss.grc.module.compliance.obligation.service.ObligationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 11 — Lightweight smoke tests for the obligation controllers.
 *
 * <p>The controllers are thin pass-throughs; we mock the service and
 * assert dispatch shape. {@code @PreAuthorize} is enforced at the
 * Spring Security proxy layer and is covered separately by integration
 * tests.
 */
@ExtendWith(MockitoExtension.class)
class ObligationControllerTest {

    @Mock private ObligationService service;
    @InjectMocks private ObligationController controller;

    private static ObligationResponse stub(String stage) {
        return new ObligationResponse(
            101L, UUID.randomUUID(), 7L,
            "OBL-SAMA-2026-001",
            "SAMA monthly CAR return", "تقرير نسبة كفاية رأس المال الشهري",
            null, null,
            "SAMA", "SAMA Prudential Returns §4.2",
            "REPORTING", "MONTHLY", "CRITICAL",
            LocalDate.of(2026, 1, 15), LocalDate.of(2026, 6, 15), 7,
            42L, null,
            "Treasury", "الخزينة",
            null, null,
            stage,
            null, null,
            LocalDateTime.now(), null
        );
    }

    @Test void create_dispatchesToService() {
        when(service.create(any(ObligationCreateRequest.class))).thenReturn(stub("DRAFT"));
        ObligationCreateRequest req = new ObligationCreateRequest(
            "OBL-SAMA-2026-001", "t", "ع", null, null,
            "SAMA", null, "REPORTING", "MONTHLY", "CRITICAL",
            null, LocalDate.now().plusDays(30), 7, 42L, null,
            null, null, null, null);

        ObligationResponse out = controller.create(req);

        assertThat(out.lifecycleStage()).isEqualTo("DRAFT");
        verify(service).create(req);
    }

    @Test void list_dispatchesToService() {
        when(service.list()).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.list();
        assertThat(out).hasSize(1);
        verify(service).list();
    }

    @Test void kpis_dispatchesToService() {
        ObligationKpis stub = new ObligationKpis(20L, 2L, 5L, 4L);
        when(service.kpis()).thenReturn(stub);
        ObligationKpis out = controller.kpis();
        assertThat(out.activeCount()).isEqualTo(20L);
        assertThat(out.overdueCount()).isEqualTo(2L);
        verify(service).kpis();
    }

    @Test void due_dispatchesWithDaysParam() {
        when(service.listDueWithinDays(30)).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.due(30);
        assertThat(out).hasSize(1);
        verify(service).listDueWithinDays(30);
    }

    @Test void transition_dispatchesToService() {
        when(service.transition(eq(101L), any(ObligationTransitionRequest.class))).thenReturn(stub("ACTIVE"));
        ObligationTransitionRequest req = new ObligationTransitionRequest("ACTIVE", "Approved");
        ObligationResponse out = controller.transition(101L, req);
        assertThat(out.lifecycleStage()).isEqualTo("ACTIVE");
        verify(service).transition(101L, req);
    }

    @Test void byRegulator_dispatchesToService() {
        when(service.listByRegulator("SAMA")).thenReturn(List.of(stub("ACTIVE")));
        List<ObligationResponse> out = controller.byRegulator("SAMA");
        assertThat(out).hasSize(1);
        verify(service).listByRegulator("SAMA");
    }
}
