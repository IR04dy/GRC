package com.tss.grc.module.audit_committee.dashboard.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AiSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.AttestationResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.CapaSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ClimateSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ControlSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FinalizeRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.FindingSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.GenerateSnapshotRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.IncidentSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.LiveDashboardResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ObligationSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RecordResolutionRequest;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.RiskSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotResponse;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.SnapshotSummary;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.ThreeLinesSection;
import com.tss.grc.module.audit_committee.dashboard.dto.AuditCommitteeDashboardDtos.VendorBcpSection;
import com.tss.grc.module.audit_committee.dashboard.service.AuditCommitteeDashboardService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Controller wiring smoke tests — Sprint 11 Audit Committee Dashboard.
 *
 * <p>The {@code @PreAuthorize} role guards are enforced by Spring at
 * the proxy boundary; here we lock the URL → service dispatch.
 */
@ExtendWith(MockitoExtension.class)
class AuditCommitteeDashboardControllerTest {

    private static final UUID EX = UUID.fromString("00000000-0000-0000-0000-000000000010");
    private static final Long ORG_ID = 1L;

    @Mock private AuditCommitteeDashboardService service;
    @InjectMocks private AuditCommitteeDashboardController controller;

    @BeforeEach void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach void tearDown() { TenantContext.clear(); }

    private static LiveDashboardResponse stubLive() {
        return new LiveDashboardResponse(
                LocalDateTime.of(2026, 5, 23, 9, 0),
                new RiskSection(10, 1, 3, 4, 2, 4, List.of()),
                new ControlSection(40, 30, 5, 75.0, 10, 5, 0.9),
                new FindingSection(8, 1, 2, 3, 2, 4, 3, 1, 6, 70.0),
                new CapaSection(12, 2, 3, 83.3),
                new AiSection(5, 1, 1, 2, 1, 1, 0),
                new ClimateSection(7, 4, 1_200_000.0, 1100.0, 1000.0, 10.0),
                new ObligationSection(20, 1, 90.0, Map.of("SAMA", 8L, "NCA", 5L)),
                new IncidentSection(3, 1, 1, 1, 0, 4.5, 2),
                new VendorBcpSection("BCP-2026-Q2", 12, 9, 75.0, 1),
                new ThreeLinesSection(10, 6, 5, 1)
        );
    }

    private static SnapshotResponse stubSnap() {
        return new SnapshotResponse(
                1L, EX, "AC-DASH-2026-Q2-MEETING",
                LocalDate.of(2026, 7, 15), "QUARTERLY",
                LocalDateTime.of(2026, 7, 15, 9, 0),
                42L, "{\"risk\":{}}",
                null, null, null,
                false, null, null,
                List.of()
        );
    }

    private static AttestationResponse stubAtt() {
        return new AttestationResponse(
                7L, UUID.randomUUID(), 1L, 42L,
                "CHAIR",
                "Reviewed and confirmed.", "تمت المراجعة والتأكيد.",
                LocalDateTime.now(),
                "deadbeef".repeat(8)
        );
    }

    @Test
    void live_dispatchesToService() {
        when(service.computeLiveDashboard(ORG_ID)).thenReturn(stubLive());
        LiveDashboardResponse out = controller.live();
        assertThat(out).isNotNull();
        assertThat(out.risk().totalRisks()).isEqualTo(10);
        assertThat(out.threeLines().line1Count()).isEqualTo(10);
        verify(service).computeLiveDashboard(ORG_ID);
    }

    @Test
    void generateSnapshot_returns201Created() {
        when(service.generateSnapshot(any(GenerateSnapshotRequest.class))).thenReturn(stubSnap());
        ResponseEntity<SnapshotResponse> out = controller.generate(
                new GenerateSnapshotRequest(LocalDate.of(2026, 7, 15), "QUARTERLY", null));
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody()).isNotNull();
        assertThat(out.getBody().snapshotCode()).contains("AC-DASH");
    }

    @Test
    void listSnapshots_dispatchesToService() {
        when(service.listSnapshots()).thenReturn(List.of(
                new SnapshotSummary(1L, EX, "AC-DASH-2026-Q1-MEETING",
                        LocalDate.of(2026, 3, 15), "QUARTERLY",
                        LocalDateTime.now(), false, 2L)));
        List<SnapshotSummary> out = controller.list();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).attestationCount()).isEqualTo(2L);
    }

    @Test
    void getSnapshot_dispatchesToService() {
        when(service.getSnapshot(EX)).thenReturn(stubSnap());
        SnapshotResponse out = controller.get(EX);
        assertThat(out.snapshotCode()).isEqualTo("AC-DASH-2026-Q2-MEETING");
        verify(service).getSnapshot(EX);
    }

    @Test
    void attest_returns201Created() {
        when(service.attestSnapshot(any(UUID.class), any(AttestRequest.class))).thenReturn(stubAtt());
        ResponseEntity<AttestationResponse> out = controller.attest(EX,
                new AttestRequest("CHAIR", "ok", "تم"));
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(out.getBody().attestedRole()).isEqualTo("CHAIR");
    }

    @Test
    void finalize_dispatchesToService() {
        SnapshotResponse snap = stubSnap();
        when(service.finalizeSnapshot(any(UUID.class), any(FinalizeRequest.class))).thenReturn(snap);
        SnapshotResponse out = controller.finalizeSnapshot(EX,
                new FinalizeRequest("Meeting concluded.", "اختتم الاجتماع."));
        assertThat(out).isNotNull();
    }

    @Test
    void recordResolution_dispatchesToService() {
        when(service.recordResolution(any(UUID.class), any(RecordResolutionRequest.class)))
                .thenReturn(stubSnap());
        SnapshotResponse out = controller.recordResolution(EX,
                new RecordResolutionRequest("Approve 2027 plan", "اعتماد خطة 2027"));
        assertThat(out).isNotNull();
    }
}
