package com.tss.grc.module.tprm.bcp_attestation.controller;

import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCloseRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignCreateRequest;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignResponse;
import com.tss.grc.module.tprm.bcp_attestation.dto.VendorBcpAttestationDtos.CampaignStatsResponse;
import com.tss.grc.module.tprm.bcp_attestation.service.VendorBcpCampaignService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lightweight controller smoke tests — the controller is a thin pass-through,
 * so we mock the service and assert wiring + dispatch shape. The
 * {@code @PreAuthorize} annotations themselves are enforced by Spring
 * Security at the proxy layer and are covered separately in integration tests.
 *
 * <p>Five tests covering the principal workflow surface: create, open,
 * send-reminders, escalate, close.
 */
@ExtendWith(MockitoExtension.class)
class VendorBcpCampaignControllerTest {

    @Mock private VendorBcpCampaignService service;
    @InjectMocks private VendorBcpCampaignController controller;

    private static CampaignResponse stub(String status) {
        return new CampaignResponse(
            101L, UUID.randomUUID(), 7L,
            "VBCP-2026-ANNUAL", "Annual BCP attestation",
            "الشهادة السنوية", null, null,
            2026, null,
            null, LocalDateTime.now().plusDays(45), null,
            14, 30,
            status, "CRITICAL_ONLY",
            "SAMA BCF §3.4; NCA ECC-2 4-2-3",
            0, 0, 0, 0, 0, 0,
            null, null,
            LocalDateTime.now(), null
        );
    }

    @Test void create_dispatchesToService() {
        when(service.createCampaign(any(CampaignCreateRequest.class))).thenReturn(stub("DRAFT"));
        CampaignCreateRequest req = new CampaignCreateRequest(
            "VBCP-2026-ANNUAL", "title", "ع", null, null,
            2026, null, null, 14, 30, "CRITICAL_ONLY", "ref");

        CampaignResponse out = controller.create(req);

        assertThat(out.status()).isEqualTo("DRAFT");
        verify(service).createCampaign(req);
    }

    @Test void list_dispatchesToService() {
        when(service.list()).thenReturn(List.of(stub("OPEN")));
        List<CampaignResponse> out = controller.list();
        assertThat(out).hasSize(1);
        verify(service).list();
    }

    @Test void open_dispatchesToService() {
        when(service.openCampaign(101L)).thenReturn(stub("OPEN"));
        CampaignResponse out = controller.open(101L);
        assertThat(out.status()).isEqualTo("OPEN");
        verify(service).openCampaign(101L);
    }

    @Test void sendReminders_dispatchesWithRoundParam() {
        when(service.sendReminders(eq(101L), anyInt())).thenReturn(stub("REMINDED_R1"));
        CampaignResponse out = controller.sendReminders(101L, 1);
        assertThat(out.status()).isEqualTo("REMINDED_R1");
        verify(service).sendReminders(101L, 1);
    }

    @Test void escalate_dispatchesToService() {
        when(service.escalateOverdue(101L)).thenReturn(stub("ESCALATED"));
        CampaignResponse out = controller.escalate(101L);
        assertThat(out.status()).isEqualTo("ESCALATED");
        verify(service).escalateOverdue(101L);
    }

    @Test void close_dispatchesToService() {
        when(service.closeCampaign(eq(101L), any(CampaignCloseRequest.class))).thenReturn(stub("CLOSED"));
        CampaignCloseRequest req = new CampaignCloseRequest("done", "تم");
        CampaignResponse out = controller.close(101L, req);
        assertThat(out.status()).isEqualTo("CLOSED");
        verify(service).closeCampaign(101L, req);
    }

    @Test void stats_dispatchesToService() {
        CampaignStatsResponse statsStub = new CampaignStatsResponse(101L, 7, 4, 3, 1, 1, 0, 57, 45);
        when(service.stats(101L)).thenReturn(statsStub);
        CampaignStatsResponse out = controller.stats(101L);
        assertThat(out.responseRatePercent()).isEqualTo(57);
        verify(service).stats(101L);
    }
}
