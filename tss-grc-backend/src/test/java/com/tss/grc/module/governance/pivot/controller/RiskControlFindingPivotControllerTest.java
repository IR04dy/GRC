package com.tss.grc.module.governance.pivot.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.CcmCoverageStats;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.ControlSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FindingSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.FocalEntity;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotResponse;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.PivotSummary;
import com.tss.grc.module.governance.pivot.dto.RiskControlFindingPivotDtos.RiskSummary;
import com.tss.grc.module.governance.pivot.service.RiskControlFindingPivotService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller wiring smoke tests — Risk-Control-Finding Pivot.
 *
 * <p>Locks the URL → service dispatch for all three lenses, plus
 * 404 surfacing when the focal entity is invisible.
 */
@ExtendWith(MockitoExtension.class)
class RiskControlFindingPivotControllerTest {

    private static final Long ORG_ID = 1L;

    @Mock private RiskControlFindingPivotService service;
    @InjectMocks private RiskControlFindingPivotController controller;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static PivotResponse stubRisk(Long id) {
        return new PivotResponse(
                FocalEntity.RISK,
                LocalDateTime.now(),
                new RiskSummary(id, "ext-" + id, "RISK-" + id,
                        "Customer-data leakage", "تسرّب بيانات العملاء",
                        "CRITICAL", 25, 7L, "OPEN"),
                null, null,
                List.of(), List.of(), List.of(), List.of(),
                new CcmCoverageStats(0L, 0L, 0L, 0L, null),
                new PivotSummary(0, 0, 0.0, 0, 0, 0, 50.0));
    }

    private static PivotResponse stubControl(Long id) {
        return new PivotResponse(
                FocalEntity.CONTROL,
                LocalDateTime.now(),
                null,
                new ControlSummary(id, "ext-" + id, "ECC-2-9",
                        "Backup integrity check", "فحص سلامة النسخ الاحتياطية",
                        "PREVENTIVE", "AUTOMATED", "ACTIVE", "RISK", null),
                null,
                List.of(), List.of(), List.of(), List.of(),
                new CcmCoverageStats(0L, 0L, 0L, 0L, null),
                new PivotSummary(1, 0, 0.0, 0, 0, 0, 50.0));
    }

    private static PivotResponse stubFinding(Long id) {
        return new PivotResponse(
                FocalEntity.FINDING,
                LocalDateTime.now(),
                null, null,
                new FindingSummary(id, "FIN-2026-001", "FIN-2026-001",
                        "Backup verification gap", "فجوة في التحقّق من النسخ",
                        "HIGH", "OPEN", "ENG-2026-01", LocalDateTime.now()),
                List.of(), List.of(), List.of(), List.of(),
                new CcmCoverageStats(0L, 0L, 0L, 0L, null),
                new PivotSummary(0, 0, 0.0, 0, 0, 0, 50.0));
    }

    @Test
    void byRisk_dispatchesToServiceAndReturnsResponseShape() {
        when(service.buildRiskPivot(10L)).thenReturn(stubRisk(10L));

        PivotResponse out = controller.byRisk(10L);

        assertThat(out).isNotNull();
        assertThat(out.focalEntity()).isEqualTo(FocalEntity.RISK);
        assertThat(out.risk().id()).isEqualTo(10L);
        assertThat(out.risk().titleAr()).contains("تسرّب");
        assertThat(out.control()).isNull();
        assertThat(out.finding()).isNull();
        verify(service).buildRiskPivot(10L);
    }

    @Test
    void byControl_dispatchesToServiceAndReturnsInvertedShape() {
        when(service.buildControlPivot(400L)).thenReturn(stubControl(400L));

        PivotResponse out = controller.byControl(400L);

        assertThat(out.focalEntity()).isEqualTo(FocalEntity.CONTROL);
        assertThat(out.control().code()).isEqualTo("ECC-2-9");
        assertThat(out.risk()).isNull();
        assertThat(out.finding()).isNull();
    }

    @Test
    void byFinding_dispatchesToServiceAndReturnsInvertedShape() {
        when(service.buildFindingPivot(800L)).thenReturn(stubFinding(800L));

        PivotResponse out = controller.byFinding(800L);

        assertThat(out.focalEntity()).isEqualTo(FocalEntity.FINDING);
        assertThat(out.finding().refNumber()).isEqualTo("FIN-2026-001");
        assertThat(out.risk()).isNull();
        assertThat(out.control()).isNull();
    }

    @Test
    void byRisk_propagatesEntityNotFound_forCrossOrgRequest() {
        when(service.buildRiskPivot(999L)).thenThrow(new EntityNotFoundException("Risk not found: 999"));
        assertThatThrownBy(() -> controller.byRisk(999L))
                .isInstanceOf(EntityNotFoundException.class);
    }
}
