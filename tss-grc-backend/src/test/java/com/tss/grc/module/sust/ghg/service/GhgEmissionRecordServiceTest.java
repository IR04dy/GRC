package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.EmissionFactorRepository;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
import com.tss.grc.module.sust.settings.entity.OrganisationSettings;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * GhgEmissionRecordService — locks the V56 workflow rules.
 *
 * <p>Rule 1 — {@code aggregate(year)} marks Scope 3 weighted coverage
 * below 70% with {@code scope3PartialFlag=true}.
 *
 * <p>Rule 2 — transitioning to VERIFIED requires {@code evidenceRef}
 * to be set; otherwise the service refuses with a clear message.
 */
@ExtendWith(MockitoExtension.class)
class GhgEmissionRecordServiceTest {

    private static final Long ORG_ID = 1L;

    @Mock private GhgEmissionRecordRepository repo;
    @Mock private OrganisationSettingsService settingsService;
    @Mock private AuditLogService audit;
    @Mock private EmissionFactorRepository factorRepo;
    @InjectMocks private GhgEmissionRecordService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    /** S-B — the Scope-3 partial boundary is now read from settings. */
    private void stubScope3Threshold(String threshold) {
        OrganisationSettings s = new OrganisationSettings();
        s.setScope3PartialCoverageThreshold(new BigDecimal(threshold));
        when(settingsService.getOrCreate()).thenReturn(s);
    }

    // ── Rule 1 — Scope 3 weighted coverage < 70% → scope3PartialFlag ─

    @Test
    void aggregate_marksScope3PartialFlag_whenWeightedCoverageBelow70() {
        // Two Scope-3 rows with 60% and 70% coverage; weighted avg falls
        // between them. Picking values whose weighted mean is 65%.
        stubScope3Threshold("70.00");
        GhgEmissionRecord s3a = scope(3, "100.00", "60.00");  // 60% coverage on 100t
        GhgEmissionRecord s3b = scope(3, "100.00", "70.00");  // 70% coverage on 100t
        // Weighted = (100*60 + 100*70) / (100+100) = 65 → below 70 → partial flag true.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(s3a, s3b));

        Map<String, Object> out = service.aggregate((short) 2025);

        assertThat(out).containsEntry("scope3PartialFlag", Boolean.TRUE);
        assertThat(((BigDecimal) out.get("scope3Coverage")).intValue()).isEqualTo(65);
    }

    @Test
    void aggregate_clearsScope3PartialFlag_whenCoverageAtOrAbove70() {
        stubScope3Threshold("70.00");
        // Single Scope-3 row at 100% coverage — well above the threshold.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(scope(3, "100.00", "100.00")));

        Map<String, Object> out = service.aggregate((short) 2025);

        assertThat(out).containsEntry("scope3PartialFlag", Boolean.FALSE);
    }

    @Test
    void aggregate_scope3PartialFlag_respectsConfiguredThreshold() {
        // Weighted coverage = 65% (two 100t rows at 60% and 70%).
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(scope(3, "100.00", "60.00"), scope(3, "100.00", "70.00")));

        // Lower the bar to 60 → 65 ≥ 60 → NOT partial (setting drives behaviour).
        stubScope3Threshold("60.00");
        assertThat(service.aggregate((short) 2025)).containsEntry("scope3PartialFlag", Boolean.FALSE);
    }

    @Test
    void aggregate_scope3PartialFlag_flipsTrue_whenThresholdRaised() {
        // Same 65% weighted coverage, but a stricter 90% bar → partial.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(scope(3, "100.00", "60.00"), scope(3, "100.00", "70.00")));

        stubScope3Threshold("90.00");
        assertThat(service.aggregate((short) 2025)).containsEntry("scope3PartialFlag", Boolean.TRUE);
    }

    @Test
    void aggregate_sumsScopesIndependently() {
        stubScope3Threshold("70.00");
        // Scope 1: 50t, Scope 2: 80t, Scope 3: 120t. Total should be 250.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025)).thenReturn(List.of(
            scope(1, "50.00",  "100.00"),
            scope(2, "80.00",  "100.00"),
            scope(3, "120.00", "100.00")));

        Map<String, Object> out = service.aggregate((short) 2025);

        assertThat(((BigDecimal) out.get("scope1Total")).intValue()).isEqualTo(50);
        assertThat(((BigDecimal) out.get("scope2Total")).intValue()).isEqualTo(80);
        assertThat(((BigDecimal) out.get("scope3Total")).intValue()).isEqualTo(120);
        assertThat(((BigDecimal) out.get("grandTotal")).intValue()).isEqualTo(250);
    }

    // ── Rule 2 — VERIFIED transition requires evidenceRef ────────────

    @Test
    void verify_refusesWhenEvidenceRefMissing() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L);
        r.setOrganisationId(ORG_ID);
        r.setStatus("SUBMITTED");
        r.setEvidenceRef(null);  // missing — Rule 2 trigger
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.transition(10L, "VERIFIED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("evidence_ref");
    }

    @Test
    void verify_acceptsWhenEvidenceRefPresent() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L);
        r.setOrganisationId(ORG_ID);
        r.setStatus("SUBMITTED");
        r.setEvidenceRef("Bureau-Veritas-2025.pdf");
        r.setVerified(false);
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord after = service.transition(10L, "VERIFIED");

        assertThat(after.getStatus()).isEqualTo("VERIFIED");
        assertThat(after.getVerified()).isTrue();
        assertThat(after.getVerificationDate()).isNotNull();
    }

    // ── State machine bounds ─────────────────────────────────────────

    @Test
    void transition_rejectsIllegalLeap() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("DRAFT");
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));

        // DRAFT → APPROVED is illegal; must go via SUBMITTED then VERIFIED.
        assertThatThrownBy(() -> service.transition(10L, "APPROVED"))
            .isInstanceOf(IllegalTransitionException.class);
    }

    // ── S-J — edit-lock + reopen (approved/verified numbers protected) ─

    @Test
    void update_onApprovedRecord_isLocked() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("APPROVED");
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));

        GhgEmissionRecord patch = new GhgEmissionRecord();
        patch.setEmissionValueTco2e(new BigDecimal("999.00"));
        assertThatThrownBy(() -> service.update(10L, patch))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("locked");
    }

    @Test
    void reopen_fromApproved_resetsToDraftAndClearsVerification() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("APPROVED");
        r.setVerified(true); r.setVerificationDate(java.time.LocalDate.now());
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord after = service.reopen(10L);

        assertThat(after.getStatus()).isEqualTo("DRAFT");
        assertThat(after.getVerified()).isFalse();
        assertThat(after.getVerificationDate()).isNull();
    }

    @Test
    void reopen_rejectsWhenNotApproved() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("VERIFIED");
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.reopen(10L)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void update_onVerifiedRecord_resetsVerification() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("VERIFIED");
        r.setScope((short) 1); r.setCategory("Stationary combustion");
        r.setSourceNameEn("Boiler"); r.setReportingYear((short) 2025);
        r.setEmissionValueTco2e(new BigDecimal("100.00"));
        r.setVerified(true); r.setVerificationDate(java.time.LocalDate.now());
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord patch = new GhgEmissionRecord();
        patch.setEmissionValueTco2e(new BigDecimal("150.00"));   // numbers changed
        GhgEmissionRecord after = service.update(10L, patch);

        assertThat(after.getStatus()).isEqualTo("SUBMITTED");    // verification invalidated
        assertThat(after.getVerified()).isFalse();
    }

    // ── S-J — evidence-item FK satisfies the verify gate (Rule 2) ─────

    @Test
    void verify_acceptsWhenEvidenceItemIdPresent_evenIfRefBlank() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("SUBMITTED");
        r.setEvidenceRef(null); r.setEvidenceItemId(77L);        // real FK, no free-text ref
        r.setVerified(false);
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord after = service.transition(10L, "VERIFIED");
        assertThat(after.getStatus()).isEqualTo("VERIFIED");
        assertThat(after.getVerified()).isTrue();
    }

    // ── S-J — library factor + unit registry → COMPUTED value ─────────

    @Test
    void create_withLibraryFactor_computesValueViaUnitRegistry() {
        EmissionFactor ef = new EmissionFactor();
        ef.setId(7L); ef.setOrganisationId(ORG_ID);
        ef.setFactorValue(new BigDecimal("2.5")); ef.setFactorUnit("kgCO2e/L"); ef.setActivityUnit("L");
        when(factorRepo.findByIdAndOrganisationId(7L, ORG_ID)).thenReturn(Optional.of(ef));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord req = new GhgEmissionRecord();
        req.setScope((short) 1); req.setCategory("Mobile combustion");
        req.setSourceNameEn("Fleet diesel"); req.setReportingYear((short) 2025);
        req.setEmissionFactorId(7L); req.setActivityData(new BigDecimal("1000"));   // 1000 L
        req.setEmissionValueTco2e(new BigDecimal("0"));   // caller value ignored → recomputed

        GhgEmissionRecord saved = service.create(req);

        // 1000 L × 2.5 kgCO2e/L = 2500 kg → 2.50 t (unit registry)
        assertThat(saved.getEmissionValueTco2e()).isEqualByComparingTo(new BigDecimal("2.50"));
        assertThat(saved.getValueSource()).isEqualTo("COMPUTED");
        assertThat(saved.getEmissionFactor()).isEqualByComparingTo(new BigDecimal("2.5"));
        assertThat(saved.getActivityUnit()).isEqualTo("L");     // pulled from the factor
    }

    @Test
    void create_withAssertedValueOnly_marksAsserted() {
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        GhgEmissionRecord req = new GhgEmissionRecord();
        req.setScope((short) 3); req.setCategory("Purchased goods");
        req.setSourceNameEn("Supplier X"); req.setReportingYear((short) 2025);
        req.setEmissionValueTco2e(new BigDecimal("500.00"));    // asserted — no activity/factor

        GhgEmissionRecord saved = service.create(req);

        assertThat(saved.getValueSource()).isEqualTo("ASSERTED");
        assertThat(saved.getEmissionValueTco2e()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    // ── S-J — bulk CSV import ─────────────────────────────────────────

    @Test
    void importCsv_createsGoodRows_andReportsBadOnes() {
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        String csv = String.join("\n",
            "reportingYear,scope,category,sourceNameEn,emissionValueTco2e",
            "2025,1,Stationary,Boiler A,120.00",
            "2025,2,Electricity,Grid import,300.00",
            "2025,9,BadScope,Nope,10.00");   // scope 9 → rejected by validate

        Map<String, Object> out = service.importCsv(csv);

        assertThat(out.get("created")).isEqualTo(2);
        assertThat(out.get("failed")).isEqualTo(1);
    }

    // ── S-J — YoY baseline honesty ────────────────────────────────────

    @Test
    void yoy_flagsWhenNoPriorYearBaseline() {
        stubScope3Threshold("70.00");
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(scope(1, "100.00", "100.00")));
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2024))
            .thenReturn(List.of());   // no prior-year data

        Map<String, Object> out = service.yoy((short) 2025);
        assertThat(out).containsEntry("priorYearHasData", Boolean.FALSE);
    }

    // ── Helper ───────────────────────────────────────────────────────
    private static GhgEmissionRecord scope(int scope, String tco2e, String coverage) {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setOrganisationId(ORG_ID);
        r.setReportingYear((short) 2025);
        r.setScope((short) scope);
        r.setEmissionValueTco2e(new BigDecimal(tco2e));
        r.setCoveragePercent(new BigDecimal(coverage));
        return r;
    }
}
