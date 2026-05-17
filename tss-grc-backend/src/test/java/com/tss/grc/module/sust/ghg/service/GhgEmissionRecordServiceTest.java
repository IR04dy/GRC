package com.tss.grc.module.sust.ghg.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.ghg.entity.GhgEmissionRecord;
import com.tss.grc.module.sust.ghg.repository.GhgEmissionRecordRepository;
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
    @InjectMocks private GhgEmissionRecordService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── Rule 1 — Scope 3 weighted coverage < 70% → scope3PartialFlag ─

    @Test
    void aggregate_marksScope3PartialFlag_whenWeightedCoverageBelow70() {
        // Two Scope-3 rows with 60% and 70% coverage; weighted avg falls
        // between them. Picking values whose weighted mean is 65%.
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
        // Single Scope-3 row at 100% coverage — well above the threshold.
        when(repo.findByOrganisationIdAndReportingYear(ORG_ID, (short) 2025))
            .thenReturn(List.of(scope(3, "100.00", "100.00")));

        Map<String, Object> out = service.aggregate((short) 2025);

        assertThat(out).containsEntry("scope3PartialFlag", Boolean.FALSE);
    }

    @Test
    void aggregate_sumsScopesIndependently() {
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
