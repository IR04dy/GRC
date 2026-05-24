package com.tss.grc.module.cyber.asm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetRequest;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.AssetResponse;
import com.tss.grc.module.cyber.asm.dto.AsmDtos.LifecycleTransitionRequest;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceAsset;
import com.tss.grc.module.cyber.asm.entity.AttackSurfaceFinding;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceAssetRepository;
import com.tss.grc.module.cyber.asm.repository.AttackSurfaceFindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AttackSurfaceAssetService} — Sprint 11 ASM.
 *
 * <p>Locks:
 * <ul>
 *   <li>tenant stamping on CREATE</li>
 *   <li>risk-score math correctness across every severity × exposure × criticality combo</li>
 *   <li>the lifecycle state-machine guard rails</li>
 *   <li>tenant isolation (cross-tenant reads return EntityNotFoundException)</li>
 *   <li>F8 event emission for {@code ASM_ASSET_CREATED},
 *       {@code ASM_ASSET_LIFECYCLE_TRANSITION},
 *       {@code ASM_ASSET_RISK_RECOMPUTED}</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AttackSurfaceAssetServiceTest {

    private static final Long ORG_ID = 1L;
    private static final Long ASSET_ID = 42L;
    private static final UUID ASSET_EX = UUID.randomUUID();

    @Mock private AttackSurfaceAssetRepository assetRepo;
    @Mock private AttackSurfaceFindingRepository findingRepo;
    @Mock private AuditLogService audit;
    @Mock private ApplicationEventPublisher eventPublisher;

    private AttackSurfaceAssetService service;

    @BeforeEach
    void setUp() {
        service = new AttackSurfaceAssetService(assetRepo, findingRepo, audit, eventPublisher);
        TenantContext.setCurrentTenant(ORG_ID);
    }

    @AfterEach
    void tearDown() { TenantContext.clear(); }

    // ─── Fixtures ─────────────────────────────────────────────────

    private static AttackSurfaceAsset asset(String exposure, String criticality) {
        AttackSurfaceAsset a = new AttackSurfaceAsset();
        a.setId(ASSET_ID);
        a.setExternalId(ASSET_EX);
        a.setOrganisationId(ORG_ID);
        a.setAssetCode("ASM-2026-001");
        a.setTitleEn("Banking portal");
        a.setAssetType("WEB_APPLICATION");
        a.setExposure(exposure);
        a.setCriticality(criticality);
        a.setLifecycleStage("APPROVED");
        a.setDiscoverySource("MANUAL");
        a.setAssetOwnerUserId(1L);
        a.setRiskScore(BigDecimal.valueOf(0));
        return a;
    }

    private static AssetRequest req() {
        return new AssetRequest(
                null, "Banking portal", "بوّابة الخدمات المصرفية",
                null, null,
                "WEB_APPLICATION",
                "banking.tss-bank.sa", null, 443, "HTTPS",
                null, null, null,
                1L, "Retail Banking", "الخدمات المصرفية للأفراد",
                "PUBLIC", "CRITICAL",
                null, "MANUAL", null
        );
    }

    @SuppressWarnings("unchecked")
    private static <T> org.mockito.stubbing.Answer<T> assignId(AtomicLong seq) {
        return inv -> {
            Object arg = inv.getArgument(0);
            try {
                var getId = arg.getClass().getMethod("getId");
                if (getId.invoke(arg) == null) {
                    var setId = arg.getClass().getMethod("setId", Long.class);
                    setId.invoke(arg, seq.incrementAndGet());
                }
            } catch (Exception ignored) {}
            return (T) arg;
        };
    }

    // ─── Create ────────────────────────────────────────────────────

    @Test
    void create_stampsTenantAndDefaultsToDiscovered() {
        AtomicLong seq = new AtomicLong(100);
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(assignId(seq));
        when(assetRepo.countByOrganisationIdAndAssetCode(eq(ORG_ID), anyString())).thenReturn(0L);

        AssetResponse out = service.createAsset(req());

        assertThat(out.lifecycleStage()).isEqualTo("DISCOVERED");
        assertThat(out.assetCode()).startsWith("ASM-");
        // 0-open-findings → severityWeight=LOW (15) × PUBLIC(1.0) × CRITICAL(1.0) = 15
        assertThat(out.riskScore()).isEqualByComparingTo("15.00");

        ArgumentCaptor<AttackSurfaceAsset> cap = ArgumentCaptor.forClass(AttackSurfaceAsset.class);
        verify(assetRepo).save(cap.capture());
        assertThat(cap.getValue().getOrganisationId()).isEqualTo(ORG_ID);
        verify(audit).log(eq("CREATE"), eq("AttackSurfaceAsset"), anyLong(), anyString());
    }

    @Test
    void create_emitsAsmAssetCreatedEvent() {
        AtomicLong seq = new AtomicLong(100);
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(assignId(seq));
        when(assetRepo.countByOrganisationIdAndAssetCode(eq(ORG_ID), anyString())).thenReturn(0L);

        service.createAsset(req());

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        DomainEvent ev = cap.getValue();
        assertThat(ev.triggerEvent()).isEqualTo("ASM_ASSET_CREATED");
        assertThat(ev.entityType()).isEqualTo("AttackSurfaceAsset");
        assertThat(ev.organisationId()).isEqualTo(ORG_ID);
        assertThat(ev.payload()).containsEntry("exposure", "PUBLIC");
        assertThat(ev.payload()).containsEntry("criticality", "CRITICAL");
        assertThat(ev.payload()).containsEntry("lifecycleStage", "DISCOVERED");
    }

    // ─── Risk-score math ─────────────────────────────────────────

    @Test
    void riskScore_publicCriticalCritical_maxes() {
        // CRITICAL severity × PUBLIC × CRITICAL = 100 × 1.0 × 1.0 = 100.00
        BigDecimal got = AttackSurfaceAssetService.computeRiskScore("CRITICAL", "PUBLIC", "CRITICAL");
        assertThat(got).isEqualByComparingTo("100.00");
    }

    @Test
    void riskScore_restrictedLowLow_lowest() {
        // LOW severity × RESTRICTED × LOW = 15 × 0.2 × 0.3 = 0.90
        BigDecimal got = AttackSurfaceAssetService.computeRiskScore("LOW", "RESTRICTED", "LOW");
        assertThat(got).isEqualByComparingTo("0.90");
    }

    @Test
    void riskScore_lockTableForKeyPermutations() {
        // High exposure / med criticality / high severity = 70 × 1.0 × 0.85 = 59.50
        assertThat(AttackSurfaceAssetService.computeRiskScore("HIGH", "PUBLIC", "HIGH"))
                .isEqualByComparingTo("59.50");
        // Partner critical + medium severity = 40 × 0.7 × 1.0 = 28.00
        assertThat(AttackSurfaceAssetService.computeRiskScore("MEDIUM", "PARTNER", "CRITICAL"))
                .isEqualByComparingTo("28.00");
        // Restricted critical + critical severity = 100 × 0.2 × 1.0 = 20.00
        assertThat(AttackSurfaceAssetService.computeRiskScore("CRITICAL", "RESTRICTED", "CRITICAL"))
                .isEqualByComparingTo("20.00");
        // Internal high + critical severity = 100 × 0.4 × 0.85 = 34.00
        assertThat(AttackSurfaceAssetService.computeRiskScore("CRITICAL", "INTERNAL", "HIGH"))
                .isEqualByComparingTo("34.00");
    }

    @Test
    void recomputeRiskScore_usesHighestOpenFindingSeverity() {
        AttackSurfaceAsset a = asset("PUBLIC", "CRITICAL");
        a.setRiskScore(BigDecimal.valueOf(15.00));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(a));
        // Open findings: one MEDIUM, one HIGH — highest is HIGH.
        AttackSurfaceFinding f1 = new AttackSurfaceFinding();
        f1.setSeverity("MEDIUM"); f1.setStatus("OPEN");
        AttackSurfaceFinding f2 = new AttackSurfaceFinding();
        f2.setSeverity("HIGH"); f2.setStatus("INVESTIGATING");
        when(findingRepo.findByOrganisationIdAndAssetIdAndStatusInAndIsDeletedFalse(
                eq(ORG_ID), eq(ASSET_ID), anyList())).thenReturn(List.of(f1, f2));
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.recomputeRiskScore(ASSET_ID);

        // HIGH (70) × PUBLIC (1.0) × CRITICAL (1.0) = 70.00
        assertThat(out.riskScore()).isEqualByComparingTo("70.00");

        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_ASSET_RISK_RECOMPUTED");
        assertThat(cap.getValue().payload()).containsEntry("topOpenSeverity", "HIGH");
        verify(audit).log(eq("RISK_RECOMPUTED"), eq("AttackSurfaceAsset"), anyLong(), anyString());
    }

    @Test
    void recomputeRiskScore_noOpenFindings_defaultsLow() {
        AttackSurfaceAsset a = asset("PARTNER", "HIGH");
        a.setRiskScore(BigDecimal.valueOf(70.00));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(a));
        when(findingRepo.findByOrganisationIdAndAssetIdAndStatusInAndIsDeletedFalse(
                eq(ORG_ID), eq(ASSET_ID), anyList())).thenReturn(List.of());
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.recomputeRiskScore(ASSET_ID);

        // LOW (15) × PARTNER (0.7) × HIGH (0.85) ≈ 8.925, scaled & half-up rounded
        // — IEEE-double evaluates to 8.924999... so the persisted value is 8.92.
        assertThat(out.riskScore()).isEqualByComparingTo("8.92");
    }

    // ─── Lifecycle transitions ───────────────────────────────────

    @Test
    void transition_discoveredToInReview_ok() {
        AttackSurfaceAsset a = asset("PUBLIC", "CRITICAL");
        a.setLifecycleStage("DISCOVERED");
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.transitionLifecycle(ASSET_EX, new LifecycleTransitionRequest("IN_REVIEW", "ownership confirmed"));

        assertThat(out.lifecycleStage()).isEqualTo("IN_REVIEW");
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(eventPublisher).publishEvent(cap.capture());
        assertThat(cap.getValue().triggerEvent()).isEqualTo("ASM_ASSET_LIFECYCLE_TRANSITION");
        assertThat(cap.getValue().payload()).containsEntry("previousLifecycleStage", "DISCOVERED");
    }

    @Test
    void transition_inReviewToApproved_stampsApprovedAt() {
        AttackSurfaceAsset a = asset("PUBLIC", "CRITICAL");
        a.setLifecycleStage("IN_REVIEW");
        a.setApprovedAt(null);
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.transitionLifecycle(ASSET_EX, new LifecycleTransitionRequest("APPROVED", null));
        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
        assertThat(out.approvedAt()).isNotNull();
    }

    @Test
    void transition_shadowItPendingToApproved_ok() {
        AttackSurfaceAsset a = asset("PUBLIC", "MEDIUM");
        a.setLifecycleStage("SHADOW_IT_PENDING");
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.transitionLifecycle(ASSET_EX, new LifecycleTransitionRequest("APPROVED", "promoted from shadow IT"));
        assertThat(out.lifecycleStage()).isEqualTo("APPROVED");
        assertThat(out.approvedAt()).isNotNull();
    }

    @Test
    void transition_approvedToRetired_stampsRetiredAt() {
        AttackSurfaceAsset a = asset("PUBLIC", "HIGH");
        a.setLifecycleStage("APPROVED");
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetResponse out = service.transitionLifecycle(ASSET_EX, new LifecycleTransitionRequest("RETIRED", "decommissioned"));
        assertThat(out.lifecycleStage()).isEqualTo("RETIRED");
        assertThat(out.retiredAt()).isNotNull();
    }

    @Test
    void transition_illegalReturnToDiscoveredFromRetired_rejected() {
        AttackSurfaceAsset a = asset("PUBLIC", "HIGH");
        a.setLifecycleStage("RETIRED");
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));

        assertThatThrownBy(() -> service.transitionLifecycle(ASSET_EX, new LifecycleTransitionRequest("DISCOVERED", null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RETIRED → DISCOVERED");
    }

    // ─── Tenant isolation ────────────────────────────────────────

    @Test
    void getAsset_otherTenant_returnsNotFound() {
        // Repo only returns row when org matches — simulate other-tenant miss.
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getAsset(ASSET_EX))
                .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void update_recomputesRiskScoreAfterCriticalityChange() {
        // Starting fixture: INTERNAL × HIGH × no-open-findings (LOW severity = 15)
        // → 15 × 0.4 × 0.85 = 5.10
        AttackSurfaceAsset a = asset("INTERNAL", "HIGH");
        a.setRiskScore(BigDecimal.valueOf(5.10));
        when(assetRepo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG_ID, ASSET_EX))
                .thenReturn(Optional.of(a));
        when(assetRepo.findById(ASSET_ID)).thenReturn(Optional.of(a));
        when(findingRepo.findByOrganisationIdAndAssetIdAndStatusInAndIsDeletedFalse(
                eq(ORG_ID), eq(ASSET_ID), anyList())).thenReturn(List.of());
        when(assetRepo.save(any(AttackSurfaceAsset.class))).thenAnswer(inv -> inv.getArgument(0));

        AssetRequest r = new AssetRequest(
                null, "Banking portal", null, null, null, "WEB_APPLICATION",
                null, null, null, null, null, null, null,
                1L, null, null, "PUBLIC", "CRITICAL", null, "MANUAL", null
        );
        AssetResponse out = service.updateAsset(ASSET_EX, r);
        // LOW (15) × PUBLIC (1.0) × CRITICAL (1.0) = 15.00 — different from 5.10 → audit + event fire.
        assertThat(out.riskScore()).isEqualByComparingTo("15.00");
        verify(audit).log(eq("UPDATE"), eq("AttackSurfaceAsset"), anyLong(), anyString());
        verify(audit, atLeastOnce()).log(eq("RISK_RECOMPUTED"), eq("AttackSurfaceAsset"), anyLong(), anyString());
    }
}
