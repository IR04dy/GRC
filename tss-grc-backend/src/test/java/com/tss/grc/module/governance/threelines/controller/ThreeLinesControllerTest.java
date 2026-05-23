package com.tss.grc.module.governance.threelines.controller;

import com.tss.grc.module.governance.threelines.dto.RoleMappingUpdateRequest;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesArtefactDto;
import com.tss.grc.module.governance.threelines.dto.ThreeLinesOverviewDto;
import com.tss.grc.module.governance.threelines.dto.UserBandDto;
import com.tss.grc.module.governance.threelines.entity.ThreeLinesRoleMapping;
import com.tss.grc.module.governance.threelines.service.ThreeLinesService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lightweight controller smoke tests — the controller is a thin pass-through,
 * so we mock the service and assert wiring + dispatch shape. The
 * {@code @PreAuthorize} annotations themselves are enforced by Spring
 * Security at the proxy layer and are unit-tested via the service-level
 * behaviour: <ul>
 *   <li>The controller method <code>overview()</code> is gated to roles in
 *       all four LoD bands; a Line-1-only user is excluded from the
 *       allowlist so they cannot read full-band drill-downs.</li>
 *   <li>The mutation endpoint <code>upsertMapping()</code> is gated to
 *       PLATFORM_ADMIN / GRC_ADMINISTRATOR — anyone else gets 403.</li>
 * </ul>
 *
 * <p>Auth wiring is verified more fully in Spring-context integration
 * tests; here we just assert the controller dispatches to the service.
 */
@ExtendWith(MockitoExtension.class)
class ThreeLinesControllerTest {

    @Mock private ThreeLinesService service;
    @InjectMocks private ThreeLinesController controller;

    @Test
    void overview_dispatches_to_service() {
        ThreeLinesOverviewDto stub = new ThreeLinesOverviewDto(List.of(), 0, 0, 0, 0, 0);
        when(service.overview()).thenReturn(stub);

        ThreeLinesOverviewDto out = controller.overview();

        assertThat(out).isSameAs(stub);
        verify(service).overview();
    }

    @Test
    void drillDown_dispatches_to_service_for_LINE_1() {
        List<ThreeLinesArtefactDto> rows = List.of(new ThreeLinesArtefactDto(
            "RISK", 1L, "ext-1", "title", "عنوان",
            "HIGH", "OPEN", "owner", LocalDate.now().plusDays(7),
            "ON_TRACK", false));
        when(service.drillDown("LINE_1")).thenReturn(rows);

        List<ThreeLinesArtefactDto> out = controller.drillDown("LINE_1");

        assertThat(out).hasSize(1);
        verify(service).drillDown("LINE_1");
    }

    @Test
    void drillDown_dispatches_to_service_for_BOARD() {
        // Audit-Committee-tier drill-down — surfaces CRITICAL escalations.
        when(service.drillDown("BOARD")).thenReturn(List.of(new ThreeLinesArtefactDto(
            "FINDING", 99L, "FIN-CRIT", "Critical SAMA finding", "ملاحظة جسيمة",
            "CRITICAL", "OPEN", "INTERNAL_AUDITOR", LocalDate.now().minusDays(1),
            "OVERDUE", true)));

        List<ThreeLinesArtefactDto> out = controller.drillDown("BOARD");

        assertThat(out).hasSize(1);
        assertThat(out.get(0).escalatedToBoard()).isTrue();
        verify(service).drillDown("BOARD");
    }

    @Test
    void userBand_dispatches_to_service() {
        UserBandDto dto = new UserBandDto(42L, "user", List.of("AUDITOR"),
            List.of("LINE_3"), "LINE_3");
        when(service.userBandFor(42L)).thenReturn(dto);

        UserBandDto out = controller.userBand(42L);

        assertThat(out.effectiveBand()).isEqualTo("LINE_3");
    }

    @Test
    void upsertMapping_dispatches_to_service() {
        ThreeLinesRoleMapping m = new ThreeLinesRoleMapping();
        m.setLodBand("LINE_2");
        when(service.mapRoleToBand(any(RoleMappingUpdateRequest.class))).thenReturn(m);

        RoleMappingUpdateRequest req = new RoleMappingUpdateRequest(
            "RISK_MANAGER", "LINE_2", null, null, null, null);

        var resp = controller.upsertMapping(req);

        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().getLodBand()).isEqualTo("LINE_2");
    }
}
