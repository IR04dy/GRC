package com.tss.grc.module.tprm.outsourcing.controller;

import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementCreateRequest;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.ArrangementResponse;
import com.tss.grc.module.tprm.outsourcing.dto.OutsourcingDtos.DashboardResponse;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingArrangementService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingDueDiligenceService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingExitPlanService;
import com.tss.grc.module.tprm.outsourcing.service.OutsourcingMonitoringService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — Lightweight controller smoke tests. The controller is a
 * thin pass-through; we mock the four services and assert dispatch
 * shape. {@code @PreAuthorize} annotations are enforced at the proxy
 * layer in integration tests, not here.
 */
@ExtendWith(MockitoExtension.class)
class OutsourcingControllerTest {

    @Mock private OutsourcingArrangementService arrangements;
    @Mock private OutsourcingDueDiligenceService dueDiligence;
    @Mock private OutsourcingMonitoringService monitoring;
    @Mock private OutsourcingExitPlanService exitPlans;
    @InjectMocks private OutsourcingController controller;

    private static ArrangementResponse stub(String stage) {
        return new ArrangementResponse(
            101L, UUID.randomUUID(), 7L,
            "OUT-2026-001", "Azure KSA", "أزور المملكة",
            null, null,
            "IT_INFRASTRUCTURE", null, null,
            true, null, null,
            true, true, "SAMA-NO-2024-1234", LocalDate.of(2024, 3, 15),
            null, null, new BigDecimal("18500000"), null, null,
            "CUSTOMER_DATA",
            "SA", "Riyadh", true,
            true, true, LocalDate.of(2024, 3, 20),
            null,
            stage, 1L, 2L, LocalDate.of(2026, 12, 31),
            LocalDateTime.now(), null
        );
    }

    @Test void list_dispatchesToService() {
        when(arrangements.list()).thenReturn(List.of(stub("ACTIVE")));
        List<ArrangementResponse> out = controller.list(null, null, null);
        assertThat(out).hasSize(1);
        verify(arrangements).list();
    }

    @Test void list_byStage_dispatchesToService() {
        when(arrangements.listByLifecycleStage("ACTIVE")).thenReturn(List.of(stub("ACTIVE")));
        controller.list("ACTIVE", null, null);
        verify(arrangements).listByLifecycleStage("ACTIVE");
    }

    @Test void list_byMateriality_dispatchesToService() {
        when(arrangements.listByMateriality(true)).thenReturn(List.of(stub("ACTIVE")));
        controller.list(null, true, null);
        verify(arrangements).listByMateriality(true);
    }

    @Test void create_dispatchesToService() {
        ArrangementCreateRequest req = new ArrangementCreateRequest(
            "OUT-2026-001", "Azure KSA", "أزور المملكة", null, null,
            "IT_INFRASTRUCTURE", null, null, true, "x", "x",
            true, true, "SAMA-NO-2024-1234", LocalDate.of(2024, 3, 15),
            LocalDate.of(2024, 4, 1), null, new BigDecimal("18500000"), null, null,
            "CUSTOMER_DATA", "SA", "Riyadh", true, true, true, LocalDate.of(2024, 3, 20),
            null, 1L, 2L, LocalDate.of(2026, 12, 31)
        );
        when(arrangements.create(any(ArrangementCreateRequest.class))).thenReturn(stub("PLANNED"));

        ArrangementResponse out = controller.create(req);

        assertThat(out.lifecycleStage()).isEqualTo("PLANNED");
        verify(arrangements).create(req);
    }

    @Test void dashboard_dispatchesToService() {
        when(arrangements.dashboard()).thenReturn(new DashboardResponse(8, 4, 1, 1, 0, 2, 1));
        DashboardResponse out = controller.dashboard();
        assertThat(out.totalArrangements()).isEqualTo(8);
        verify(arrangements).dashboard();
    }

    @Test void samaPending_dispatchesToService() {
        when(arrangements.listAwaitingSamaNonObjection()).thenReturn(List.of(stub("AWAITING_SAMA")));
        controller.samaPending();
        verify(arrangements).listAwaitingSamaNonObjection();
    }
}
