package com.tss.grc.module.risk.kri.controller;

import com.tss.grc.module.risk.kri.dto.KriDtos.KriBreachResponse;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDashboardSnapshot;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriDefinitionResponse;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriHistoryPoint;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementRequest;
import com.tss.grc.module.risk.kri.dto.KriDtos.KriMeasurementResponse;
import com.tss.grc.module.risk.kri.service.KriBreachService;
import com.tss.grc.module.risk.kri.service.KriDashboardService;
import com.tss.grc.module.risk.kri.service.KriDefinitionService;
import com.tss.grc.module.risk.kri.service.KriMeasurementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KriControllerTest {

    private static final UUID EX = UUID.randomUUID();

    @Mock private KriDefinitionService definitionService;
    @Mock private KriMeasurementService measurementService;
    @Mock private KriBreachService breachService;
    @Mock private KriDashboardService dashboardService;
    @InjectMocks private KriController controller;

    private static KriDefinitionResponse stubDef() {
        return new KriDefinitionResponse(
                1L, EX, "KRI-CYBER-FLA-001",
                "Failed login attempts per day",
                "محاولات تسجيل الدخول الفاشلة في اليوم",
                null, null,
                "CYBER", "COUNT", "LOWER_IS_BETTER",
                new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("200"), new BigDecimal("200"),
                "DAILY", "SQL", null,
                null, null,
                null, null,
                Boolean.TRUE,
                LocalDateTime.now(), null,
                null, null, null);
    }

    @Test
    void listDefinitions_dispatchesToService() {
        when(definitionService.list()).thenReturn(List.of(stubDef()));
        List<KriDefinitionResponse> out = controller.listDefinitions(null);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).kriCode()).isEqualTo("KRI-CYBER-FLA-001");
        verify(definitionService).list();
    }

    @Test
    void listDefinitions_byCategory_routesToFilteredFinder() {
        when(definitionService.listByCategory("CYBER")).thenReturn(List.of(stubDef()));
        List<KriDefinitionResponse> out = controller.listDefinitions("CYBER");
        assertThat(out).hasSize(1);
        verify(definitionService).listByCategory("CYBER");
    }

    @Test
    void createDefinition_returns201() {
        when(definitionService.create(any(KriDefinitionRequest.class))).thenReturn(stubDef());
        KriDefinitionRequest req = new KriDefinitionRequest(
                null, "Failed login attempts per day",
                "محاولات تسجيل الدخول الفاشلة",
                null, null,
                "CYBER", "COUNT", "LOWER_IS_BETTER",
                new BigDecimal("50"), new BigDecimal("50"),
                new BigDecimal("200"), new BigDecimal("200"),
                "DAILY", "SQL", null,
                null, null, null, null, Boolean.TRUE);
        ResponseEntity<KriDefinitionResponse> resp = controller.createDefinition(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void recordMeasurement_returns201() {
        KriMeasurementResponse stub = new KriMeasurementResponse(
                900L, UUID.randomUUID(), 1L, "KRI-CYBER-FLA-001",
                LocalDateTime.now(), new BigDecimal("30"),
                "GREEN", "MANUAL", null, null, null);
        when(measurementService.record(eq(EX), any(KriMeasurementRequest.class))).thenReturn(stub);
        ResponseEntity<KriMeasurementResponse> resp = controller.record(EX,
                new KriMeasurementRequest(new BigDecimal("30"), null, "MANUAL", null, null));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("GREEN");
    }

    @Test
    void dashboard_dispatchesToService() {
        KriDashboardSnapshot snap = new KriDashboardSnapshot(
                15L, 15L, 12L, 2L, 1L, 0L, 2L, 1L, List.of(), List.of());
        when(dashboardService.snapshot()).thenReturn(snap);
        KriDashboardSnapshot out = controller.dashboard();
        assertThat(out.totalKris()).isEqualTo(15L);
        verify(dashboardService).snapshot();
    }

    @Test
    void history_dispatchesWithDayWindow() {
        when(dashboardService.history(EX, 90)).thenReturn(List.of(
                new KriHistoryPoint(LocalDateTime.now(), new BigDecimal("30"), "GREEN")));
        List<KriHistoryPoint> out = controller.history(EX, 90);
        assertThat(out).hasSize(1);
        verify(dashboardService).history(EX, 90);
    }

    @Test
    void openBreaches_dispatchesToBreachService() {
        when(breachService.listOpen()).thenReturn(List.of(
                new KriBreachResponse(
                        1L, UUID.randomUUID(), 42L, "KRI-CYBER-FLA-001",
                        "Failed logins", "محاولات تسجيل الدخول الفاشلة",
                        900L, "AMBER",
                        LocalDateTime.now().minusHours(2), null,
                        null, null, null)));
        List<KriBreachResponse> out = controller.openBreaches();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).breachLevel()).isEqualTo("AMBER");
    }
}
