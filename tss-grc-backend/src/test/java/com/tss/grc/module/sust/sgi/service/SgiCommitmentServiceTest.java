package com.tss.grc.module.sust.sgi.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import com.tss.grc.module.sust.sgi.repository.SgiCommitmentRepository;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import com.tss.grc.security.GrcUserDetails;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SgiCommitmentService — direction-aware progress math + V59 lifecycle.
 *
 * <p>Locks two key behaviours:
 * <ul>
 *   <li>Progress is computed differently for raise-to-target ("50%
 *       renewables") vs lower-to-target ("3.15 m³/SAR1k water").</li>
 *   <li>Rule A — achieve requires current_value to meet target within
 *       the direction of the commitment.</li>
 *   <li>Rule B — retire requires a written reason.</li>
 *   <li>Rule C (SGI-A) — the achiever must differ from the last editor (SoD);
 *       PLATFORM_ADMIN bypasses.</li>
 *   <li>Rule D (SGI-A) — achieve requires attached evidence.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class SgiCommitmentServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private SgiCommitmentRepository repo;
    @Mock private OrganisationSettingsService settingsService;
    @Mock private AuditLogService audit;
    @Mock private CorrectiveActionService capaService;
    @InjectMocks private SgiCommitmentService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); SecurityContextHolder.clearContext(); }

    /** Bind an authenticated principal so CurrentActor.username()/isPlatformAdmin() resolve. */
    private void authAs(String username, boolean platformAdmin) {
        var authorities = platformAdmin
            ? List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN"))
            : List.<SimpleGrantedAuthority>of();
        GrcUserDetails principal = new GrcUserDetails(
            42L, ORG_ID, username, "x", true, true, authorities);
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(principal, "x", authorities));
    }

    /** S-B — the AT_RISK progress factor is now read from settings. */
    private void stubAtRiskFactor(String factor) {
        OrganisationSettings s = new OrganisationSettings();
        s.setSgiAtRiskProgressFactor(new BigDecimal(factor));
        when(settingsService.getOrCreate()).thenReturn(s);
    }

    // ── Direction-aware progress ────────────────────────────────────

    @Test
    void progress_increaseToTarget_computesCorrectly() {
        // Renewable share: 8% baseline → 22% current → 50% target.
        // (22 - 8) / (50 - 8) = 14 / 42 = 33.3%
        stubAtRiskFactor("0.70");
        SgiCommitment c = commitment(new BigDecimal("8"), new BigDecimal("22"), new BigDecimal("50"));

        Map<String, Object> p = service.progress(c);

        assertThat(p).containsEntry("direction", "INCREASE");
        assertThat(((BigDecimal) p.get("progressPct")).doubleValue()).isEqualTo(33.3);
    }

    @Test
    void progress_decreaseToTarget_computesCorrectly() {
        // Water intensity: 4.2 baseline → 3.7 current → 3.15 target.
        // Decrease-to: (baseline - current) / (baseline - target)
        // = (4.2 - 3.7) / (4.2 - 3.15) = 0.5 / 1.05 = 47.6%
        stubAtRiskFactor("0.70");
        SgiCommitment c = commitment(new BigDecimal("4.2"), new BigDecimal("3.7"), new BigDecimal("3.15"));

        Map<String, Object> p = service.progress(c);

        assertThat(p).containsEntry("direction", "DECREASE");
        assertThat(((BigDecimal) p.get("progressPct")).doubleValue()).isEqualTo(47.6);
    }

    @Test
    void progress_targetEqualsBaseline_returnsZero_whenCurrentDoesNotMatch() {
        stubAtRiskFactor("0.70");
        SgiCommitment c = commitment(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("100"));

        Map<String, Object> p = service.progress(c);

        assertThat(((BigDecimal) p.get("progressPct"))).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void progress_isCappedAt150_toGuardDashboardRendering() {
        // Massive over-achievement on a small target. Without the cap,
        // the dashboard's progress bar math would break.
        stubAtRiskFactor("0.70");
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("10"), new BigDecimal("1"));

        Map<String, Object> p = service.progress(c);

        assertThat(((BigDecimal) p.get("progressPct")).intValue()).isEqualTo(150);
    }

    // ── S-B — AT_RISK tier boundary is settings-driven ──────────────

    @Test
    void progress_tier_respectsConfiguredAtRiskFactor() {
        // Fixed, date-robust scenario: progress 75%, expected 100%
        // (target year already in the past → expectedPct pins to 100).
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("75"), new BigDecimal("100"));
        c.setBaselineYear((short) 2019);
        c.setTargetYear((short) 2020);

        // Lenient factor 0.70 → warning floor 70 → 75 ≥ 70 → AT_RISK.
        stubAtRiskFactor("0.70");
        assertThat(service.progress(c)).containsEntry("tier", "AT_RISK");

        // Stricter factor 0.80 → warning floor 80 → 75 < 80 → DELAYED.
        stubAtRiskFactor("0.80");
        assertThat(service.progress(c)).containsEntry("tier", "DELAYED");
    }

    // ── S-D — a behind-plan (DELAYED) commitment auto-opens a CAPA ──

    @Test
    void update_whenBehindPlan_opensAutoCapa() {
        // DELAYED: progress 40%, expected 100% (target year already past),
        // strict factor 0.80 → warning floor 80 → 40 < 80 → DELAYED.
        SgiCommitment cur = commitment(new BigDecimal("0"), new BigDecimal("40"), new BigDecimal("100"));
        cur.setId(7L);
        cur.setStatus("ACTIVE");
        cur.setCommitmentCode("SGI-RENEW-2030");
        cur.setTitleEn("Renewable energy share");
        cur.setSector("ENERGY");
        cur.setUnit("%");
        cur.setBaselineYear((short) 2019);
        cur.setTargetYear((short) 2020);
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(cur));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        stubAtRiskFactor("0.80");

        SgiCommitment patch = new SgiCommitment();
        patch.setCurrentValue(new BigDecimal("40"));   // refresh that leaves it behind plan
        service.update(7L, patch);

        // Behind plan (DELAYED) → auto-CAPA opened for (SGI_COMMITMENT, id 7).
        verify(capaService).autoOpen(eq("SGI_COMMITMENT"), eq("7"), any(), any(), any(), eq("HIGH"));
    }

    // ── Rule A — Achieve requires target met within direction ───────

    @Test
    void achieve_acceptsWhenIncreaseTargetMet() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        c.setEvidenceRef("EVD-SGI-1");            // Rule D — evidence attached
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("sara", false);

        SgiCommitment after = service.achieve(7L);

        assertThat(after.getStatus()).isEqualTo("ACHIEVED");
        // Attribution comes from the authenticated principal, not a body name.
        assertThat(after.getAchievedBy()).isEqualTo("sara");
        assertThat(after.getAchievedAt()).isNotNull();
    }

    @Test
    void achieve_refusesWhenIncreaseTargetNotMet() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("30"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.achieve(7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("target");
    }

    @Test
    void achieve_acceptsWhenDecreaseTargetMet() {
        // Lower-to: baseline 4.2 → current 3.0 (below target 3.15) → met.
        SgiCommitment c = commitment(new BigDecimal("4.2"), new BigDecimal("3.0"), new BigDecimal("3.15"));
        c.setId(7L); c.setStatus("ACTIVE");
        c.setEvidenceItemId(99L);                 // Rule D — real evidence FK
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("lina", false);

        SgiCommitment after = service.achieve(7L);

        assertThat(after.getStatus()).isEqualTo("ACHIEVED");
    }

    @Test
    void achieve_refusesWhenDecreaseTargetNotMet() {
        SgiCommitment c = commitment(new BigDecimal("4.2"), new BigDecimal("3.8"), new BigDecimal("3.15"));
        c.setId(7L); c.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.achieve(7L))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // ── Rule D — Achieve requires attached evidence (SGI-A, mirrors GHG Rule 2) ──

    @Test
    void achieve_refusesWithoutEvidence() {
        // Target is met, but no evidence_item_id and no evidence_ref.
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        authAs("sara", false);

        assertThatThrownBy(() -> service.achieve(7L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evidence");
    }

    // ── Rule C — Achieve blocked for the last editor (SGI-A, SoD) ────

    @Test
    void achieve_blocksWhenAchieverIsLastEditor() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        c.setEvidenceRef("EVD-SGI-1");            // Rule D satisfied so Rule C is what bites
        c.setUpdatedBy("sara");                    // sara last edited the numbers
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        authAs("sara", false);                     // …and sara tries to achieve

        assertThatThrownBy(() -> service.achieve(7L))
            .isInstanceOf(IllegalStateException.class)     // → 409
            .hasMessageContaining("Self-approval");
    }

    @Test
    void achieve_allowsWhenAchieverDiffersFromLastEditor() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        c.setEvidenceRef("EVD-SGI-1");
        c.setUpdatedBy("sara");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("omar", false);                     // a different second approver

        assertThat(service.achieve(7L).getStatus()).isEqualTo("ACHIEVED");
    }

    @Test
    void achieve_platformAdminBypassesSoD() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("50"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        c.setEvidenceRef("EVD-SGI-1");
        c.setUpdatedBy("admin");                   // admin last edited…
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("admin", true);                      // …and PLATFORM_ADMIN bypasses Rule C

        assertThat(service.achieve(7L).getStatus()).isEqualTo("ACHIEVED");
    }

    // ── Rule B — Retire requires reason ─────────────────────────────

    @Test
    void retire_refusesWithoutReason() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> service.retire(7L, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
        assertThatThrownBy(() -> service.retire(7L, "   "))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void retire_acceptsWithReason() {
        SgiCommitment c = commitment(new BigDecimal("0"), new BigDecimal("0"), new BigDecimal("50"));
        c.setId(7L); c.setStatus("ACTIVE");
        when(repo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(c));
        when(repo.save(any(SgiCommitment.class))).thenAnswer(inv -> inv.getArgument(0));
        authAs("sara", false);

        SgiCommitment after = service.retire(7L, "Replaced by SGI-30PCT-2030");

        assertThat(after.getStatus()).isEqualTo("RETIRED");
        assertThat(after.getRetiredReason()).contains("Replaced");
        assertThat(after.getRetiredBy()).isEqualTo("sara");   // principal, not body
    }

    // ── Helper ──────────────────────────────────────────────────────
    private static SgiCommitment commitment(BigDecimal baseline, BigDecimal current, BigDecimal target) {
        SgiCommitment c = new SgiCommitment();
        c.setOrganisationId(ORG_ID);
        c.setBaselineValue(baseline);
        c.setCurrentValue(current);
        c.setTargetValue(target);
        c.setBaselineYear((short) 2025);
        c.setTargetYear((short) 2030);
        c.setStatus("ACTIVE");
        return c;
    }
}
