package com.tss.grc.module.cyber.asm.controller;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.FindingResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.ShadowItResponse;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceAssetRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceFindingRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceShadowItRepository;
import com.tss.grc.module.cyber.asm.service.AttackSurfaceAssetService;
import com.tss.grc.module.cyber.asm.service.AttackSurfaceFindingService;
import com.tss.grc.module.cyber.asm.service.ShadowItService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttackSurfaceController} — wiring smoke tests.
 *
 * <p>The {@code @PreAuthorize} role guards are enforced by Spring at
 * the proxy boundary; here we lock the dispatch from URL → service
 * contract.
 */
@ExtendWith(MockitoExtension.class)
class AttackSurfaceControllerTest {

    private static final Long ORG_ID = 1L;
    private static final UUID EX = UUID.randomUUID();

    @Mock private AttackSurfaceAssetService assetService;
    @Mock private AttackSurfaceFindingService findingService;
    @Mock private ShadowItService shadowItService;
    @Mock private AttackSurfaceAssetRepository assetRepo;
    @Mock private AttackSurfaceFindingRepository findingRepo;
    @Mock private AttackSurfaceShadowItRepository shadowItRepo;
    @InjectMocks private AttackSurfaceController controller;

    @BeforeEach
    void setUp() { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach
    void tearDown() { TenantContext.clear(); }

    private static AssetResponse stubAsset(String code, BigDecimal score) {
        return new AssetResponse(
                1L, EX, code, "Banking portal", "بوّابة الخدمات المصرفية",
                null, null,
                "WEB_APPLICATION",
                "banking.tss-bank.sa", null, 443, "HTTPS",
                null, null, null,
                1L, "Retail Banking", null,
                "PUBLIC", "CRITICAL",
                "APPROVED", "MANUAL",
                null, null, null, null, score,
                null, null
        );
    }

    @Test
    void listAssets_dispatchesToService() {
        when(assetService.listAssets()).thenReturn(List.of(stubAsset("ASM-2026-001", BigDecimal.valueOf(100))));
        List<AssetResponse> out = controller.listAssets();
        assertThat(out).hasSize(1);
        assertThat(out.get(0).assetCode()).isEqualTo("ASM-2026-001");
        verify(assetService).listAssets();
    }

    @Test
    void createAsset_returns201AndBody() {
        when(assetService.createAsset(any(AssetRequest.class))).thenReturn(stubAsset("ASM-2026-001", BigDecimal.valueOf(15)));

        AssetRequest req = new AssetRequest(
                null, "Banking portal", null, null, null,
                "WEB_APPLICATION",
                null, null, null, null, null, null, null,
                1L, null, null, "PUBLIC", "CRITICAL", null, "MANUAL", null
        );
        ResponseEntity<AssetResponse> resp = controller.createAsset(req);
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void recordFinding_dispatchesAndReturns201() {
        FindingResponse stub = new FindingResponse(
                1L, EX, 1L, UUID.randomUUID(), "ASM-2026-001",
                "WEAK_CIPHER", "HIGH", "TENABLE", "plugin-26928",
                "TLS 1.2 with CBC suite", null, null, null,
                null, null, "OPEN", null, null, null);
        when(findingService.recordFinding(any(FindingRequest.class))).thenReturn(stub);

        FindingRequest req = new FindingRequest(
                EX, "WEAK_CIPHER", "HIGH", "TENABLE", null,
                "TLS 1.2 with CBC suite", null, null, null);
        ResponseEntity<FindingResponse> resp = controller.recordFinding(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("OPEN");
        verify(findingService).recordFinding(any(FindingRequest.class));
    }

    @Test
    void recordShadowIt_returns201() {
        ShadowItResponse stub = new ShadowItResponse(
                1L, EX, "Notion", "نوشن", "notion.so",
                "EMAIL_SIGNUPS", null, 18,
                "UNKNOWN", false, "PENDING_REVIEW",
                null, null, null, null, null, null, null, null);
        when(shadowItService.recordDiscovery(any(ShadowItRequest.class))).thenReturn(stub);

        ShadowItRequest req = new ShadowItRequest(
                "Notion", "نوشن", "notion.so",
                "EMAIL_SIGNUPS", 18, "UNKNOWN", Boolean.FALSE,
                null, null, null, null);
        ResponseEntity<ShadowItResponse> resp = controller.recordShadowIt(req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().status()).isEqualTo("PENDING_REVIEW");
    }

    @Test
    void dashboard_aggregatesAcrossServicesAndRepos() {
        when(assetService.listAssets()).thenReturn(List.of(
                stubAsset("ASM-2026-001", BigDecimal.valueOf(100)),
                stubAsset("ASM-2026-002", BigDecimal.valueOf(70))));
        when(assetRepo.countByOrganisationIdAndExposureAndCriticalityAndIsDeletedFalse(ORG_ID, "PUBLIC", "CRITICAL"))
                .thenReturn(1L);
        when(findingRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG_ID, "OPEN")).thenReturn(2L);
        when(findingRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG_ID, "INVESTIGATING")).thenReturn(1L);
        when(findingService.listFindings()).thenReturn(List.of());
        when(shadowItRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG_ID, "PENDING_REVIEW")).thenReturn(2L);
        when(shadowItRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(ORG_ID, "IN_PROGRESS")).thenReturn(1L);
        when(shadowItService.list()).thenReturn(List.of());

        var snap = controller.dashboard();
        assertThat(snap.totalAssets()).isEqualTo(2);
        assertThat(snap.publicCriticalAssets()).isEqualTo(1);
        assertThat(snap.openFindings()).isEqualTo(3);
        assertThat(snap.pendingShadowIt()).isEqualTo(3);
    }
}
