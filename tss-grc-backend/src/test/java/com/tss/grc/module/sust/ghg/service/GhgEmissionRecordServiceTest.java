package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.entity.EvidenceItem;
import com.tss.grc.module.evidence.repository.EvidenceItemRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.evidence.controller.SustEvidenceLinkController;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
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
    /** F2-243 — Rule 2 resolves the evidence item it cites, so the gate needs the real collaborator. */
    @Mock private EvidenceItemRepository evidenceItems;
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

    // ── S-J / F2-243 — an evidence-item FK satisfies the verify gate (Rule 2) ─────
    //
    // ⚠ This pair replaces a single test named
    // `verify_acceptsWhenEvidenceItemIdPresent_evenIfRefBlank`, which asserted that the PRESENCE of
    // an id is enough — i.e. it asserted the defect as correct, in its own name. That is the Nth
    // instance in this programme of a test locking one in (F2-159, F2-175, F2-227, F2-234, F2-236),
    // and a name that states the behaviour under review is the strongest possible reason not to look
    // further (F2-216). Repurposed rather than deleted: the half that protects the record — a real
    // evidence item discharges Rule 2 with no free-text ref — is kept, and the half that was missing
    // is now asserted beside it.

    @Test
    void verify_acceptsAUsableEvidenceItem_withNoFreeTextRef() {
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(10L); r.setOrganisationId(ORG_ID); r.setStatus("SUBMITTED");
        r.setEvidenceRef(null); r.setEvidenceItemId(77L);        // real FK, no free-text ref
        r.setVerified(false);
        when(repo.findByIdAndOrganisationId(10L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));
        when(evidenceItems.findUsableAsEvidence(eq(77L), eq(ORG_ID), any()))
            .thenReturn(Optional.of(new EvidenceItem()));

        GhgEmissionRecord after = service.transition(10L, "VERIFIED");
        assertThat(after.getStatus()).isEqualTo("VERIFIED");
        assertThat(after.getVerified()).isTrue();
    }

    @Test
    void verify_refusesAnItemTheEvidenceModuleWillNotVouchFor_namingItsState() {
        // Probed live before the fix: a record citing the register's one REJECTED item verified with
        // HTTP 200 and stored verified = true, and so did one citing an EXPIRED item. 51 of the 78
        // live items are not APPROVED, in dev and prod alike.
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(11L); r.setOrganisationId(ORG_ID); r.setStatus("SUBMITTED");
        r.setEvidenceRef(null); r.setEvidenceItemId(30L);
        r.setVerified(false);
        when(repo.findByIdAndOrganisationId(11L, ORG_ID)).thenReturn(Optional.of(r));
        when(evidenceItems.findUsableAsEvidence(eq(30L), eq(ORG_ID), any()))
            .thenReturn(Optional.empty());
        EvidenceItem rejected = new EvidenceItem();
        rejected.setStatus("REJECTED");
        when(evidenceItems.findByIdAndOrganisationId(30L, ORG_ID)).thenReturn(Optional.of(rejected));

        assertThatThrownBy(() -> service.transition(11L, "VERIFIED"))
            .isInstanceOf(IllegalArgumentException.class)
            // The state has to be in the message: "not usable" on its own sends nobody anywhere,
            // and the caller needs to know whether to chase a review or a re-collection (F2-180).
            .hasMessageContaining("30")
            .hasMessageContaining("REJECTED");
        assertThat(r.getStatus()).as("the record must not move").isEqualTo("SUBMITTED");
        assertThat(r.getVerified()).isFalse();
    }

    @Test
    void verify_stillRefusesWhenThereIsNoEvidenceAtAll() {
        // The gate's original job, unchanged — and it must be refused BEFORE anything is resolved,
        // so a record with no evidence never reaches the evidence module at all.
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(12L); r.setOrganisationId(ORG_ID); r.setStatus("SUBMITTED");
        r.setEvidenceRef("  "); r.setEvidenceItemId(null);
        when(repo.findByIdAndOrganisationId(12L, ORG_ID)).thenReturn(Optional.of(r));

        assertThatThrownBy(() -> service.transition(12L, "VERIFIED"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("requires evidence");
        verifyNoInteractions(evidenceItems);
    }

    @Test
    void verify_theLegacyFreeTextRefStillDischargesRule2_withoutResolvingAnything() {
        // Deliberately untouched: it is what the five APPROVED rows in both databases were verified
        // against (BV and KPMG assurance statements predating the S-C bridge). Tightening it would
        // refuse requests that work today for no gain the caller can act on (F2-182).
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(13L); r.setOrganisationId(ORG_ID); r.setStatus("SUBMITTED");
        r.setEvidenceRef("KPMG-Verif-2025-S2-Riyadh.pdf"); r.setEvidenceItemId(null);
        when(repo.findByIdAndOrganisationId(13L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        assertThat(service.transition(13L, "VERIFIED").getStatus()).isEqualTo("VERIFIED");
        verifyNoInteractions(evidenceItems);
    }

    @Test
    void update_acceptsAnEchoOfTheStoredEvidenceItemButRefusesAChange() {
        // The register's PUT binds the whole entity, so a round-trip edit resends the id it just
        // read: an echo must pass (F2-231/F2-234). A change must not — evidence_item_id belongs to
        // the S-C bridge, which is the only writer that also records the EvidenceLink the assurance
        // package is built from.
        GhgEmissionRecord r = new GhgEmissionRecord();
        r.setId(14L); r.setOrganisationId(ORG_ID); r.setStatus("DRAFT");
        // update() ends in validate(), so the row has to be a legal one — an incomplete fixture
        // throws for an unrelated reason and the assertion below would pass on the wrong exception
        // (F2-219: a test that fails on the unmutated tree makes every mutation read as killed).
        r.setScope((short) 1); r.setCategory("Mobile combustion");
        r.setSourceNameEn("Fleet diesel"); r.setReportingYear((short) 2025);
        r.setEmissionValueTco2e(new BigDecimal("12.50"));
        r.setEvidenceItemId(77L);
        when(repo.findByIdAndOrganisationId(14L, ORG_ID)).thenReturn(Optional.of(r));
        when(repo.save(any(GhgEmissionRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        GhgEmissionRecord echo = new GhgEmissionRecord();
        echo.setEvidenceItemId(77L);
        echo.setNotes("edited");
        assertThat(service.update(14L, echo).getNotes()).isEqualTo("edited");

        GhgEmissionRecord change = new GhgEmissionRecord();
        change.setEvidenceItemId(78L);
        assertThatThrownBy(() -> service.update(14L, change))
            .isInstanceOf(IllegalArgumentException.class)
            // Asserted against the CONSTANT, not a typed-out path: my first draft of the message
            // spelled an endpoint that does not exist, and a hand-written literal here would have
            // agreed with it (F2-198's guessed path, inside a fix's own diagnostic).
            .hasMessageContaining(SustEvidenceLinkController.PATH);
        assertThat(r.getEvidenceItemId()).as("the stored linkage must be untouched").isEqualTo(77L);
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
