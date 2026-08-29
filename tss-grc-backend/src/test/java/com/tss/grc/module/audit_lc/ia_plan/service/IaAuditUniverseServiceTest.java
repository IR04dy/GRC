package com.tss.grc.module.audit_lc.ia_plan.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseBulkImportResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseCreateRequest;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseResponse;
import com.tss.grc.module.audit_lc.ia_plan.dto.IaDtos.UniverseUpdateRequest;
import com.tss.grc.module.audit_lc.ia_plan.entity.IaAuditUniverseEntry;
import com.tss.grc.module.audit_lc.ia_plan.repository.IaAuditUniverseEntryRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Sprint 13 — {@link IaAuditUniverseService} unit tests.
 *
 * <p>Covers:
 * <ul>
 *   <li>CRUD happy path</li>
 *   <li>{@code computeResidual} formula: inherent × (1 - controlEnv)</li>
 *   <li>Duplicate entry_code rejected within (org, plan_id)</li>
 *   <li>Bulk import idempotent: skips existing rows</li>
 *   <li>Update recomputes residual when scoring inputs change</li>
 *   <li>recomputeResidual API ignores the stored value</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class IaAuditUniverseServiceTest {

    private static final Long ORG_ID = 7L;
    private static final Long ENTRY_ID = 500L;
    private static final Long PLAN_ID = 100L;

    @Mock private IaAuditUniverseEntryRepository repo;
    @Mock private AuditLogService audit;

    private IaAuditUniverseService service;

    @BeforeEach void setUp() {
        TenantContext.setCurrentTenant(ORG_ID);
        service = new IaAuditUniverseService(repo, audit);
    }

    @AfterEach void tearDown() { TenantContext.clear(); }

    private static IaAuditUniverseEntry entry(String code) {
        IaAuditUniverseEntry e = new IaAuditUniverseEntry();
        e.setId(ENTRY_ID);
        e.setOrganisationId(ORG_ID);
        e.setPlanId(PLAN_ID);
        e.setEntryCode(code);
        e.setTitleEn(code + " EN");
        e.setTitleAr(code + " AR");
        e.setInherentRiskScore(new BigDecimal("20.00"));
        e.setControlEnvironmentScore(new BigDecimal("0.30"));
        e.setResidualRiskScore(new BigDecimal("14.00"));
        return e;
    }

    private static UniverseCreateRequest createReq(Long planId, String code, String inherent, String controlEnv) {
        return new UniverseCreateRequest(
            planId, code, code + " EN", code + " AR",
            null, null, null, null,
            inherent != null ? new BigDecimal(inherent) : null,
            controlEnv != null ? new BigDecimal(controlEnv) : null,
            null, null, null, null, null, null, null
        );
    }

    // ─── computeResidual ─────────────────────────────────────────────

    @Test void computeResidual_appliesIaConvention() {
        BigDecimal res = IaAuditUniverseService.computeResidual(
            new BigDecimal("22.00"), new BigDecimal("0.30"));
        // 22 × (1 - 0.30) = 22 × 0.70 = 15.40
        assertThat(res).isEqualByComparingTo("15.40");
    }

    @Test void computeResidual_handlesMaxStrength() {
        BigDecimal res = IaAuditUniverseService.computeResidual(
            new BigDecimal("25.00"), BigDecimal.ONE);
        assertThat(res).isEqualByComparingTo("0.00");
    }

    @Test void computeResidual_nullInputReturnsNull() {
        assertThat(IaAuditUniverseService.computeResidual(null, BigDecimal.ZERO)).isNull();
        assertThat(IaAuditUniverseService.computeResidual(BigDecimal.TEN, null)).isNull();
    }

    // ─── CRUD ────────────────────────────────────────────────────────

    @Test void create_persistsEntryAndComputesResidualWhenMissing() {
        UniverseCreateRequest req = createReq(PLAN_ID, "AU-AML-KYC-001", "22.00", "0.30");
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-AML-KYC-001", PLAN_ID))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> {
            IaAuditUniverseEntry e = inv.getArgument(0);
            e.setId(ENTRY_ID);
            return e;
        });

        UniverseResponse out = service.create(req);
        assertThat(out.entryCode()).isEqualTo("AU-AML-KYC-001");
        // Service computed residual since req.residualRiskScore() was null
        assertThat(out.residualRiskScore()).isEqualByComparingTo("15.40");
    }

    @Test void create_rejectsDuplicateEntryCode() {
        UniverseCreateRequest req = createReq(PLAN_ID, "AU-DUP-001", "10.00", "0.20");
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-DUP-001", PLAN_ID))
            .thenReturn(Optional.of(entry("AU-DUP-001")));

        assertThatThrownBy(() -> service.create(req))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already exists");
    }

    @Test void create_masterEntry_usesNullPlanLookup() {
        UniverseCreateRequest req = createReq(null, "AU-MASTER-001", "10.00", "0.20");
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdIsNullAndIsDeletedFalse(ORG_ID, "AU-MASTER-001"))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> {
            IaAuditUniverseEntry e = inv.getArgument(0);
            e.setId(ENTRY_ID);
            return e;
        });

        UniverseResponse out = service.create(req);
        assertThat(out.planId()).isNull();
        assertThat(out.residualRiskScore()).isEqualByComparingTo("8.00");
    }

    @Test void update_recomputesResidualWhenScoresTouched() {
        IaAuditUniverseEntry e = entry("AU-UPD-001");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENTRY_ID))
            .thenReturn(Optional.of(e));
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseUpdateRequest req = new UniverseUpdateRequest(
            null, null, null, null, null, null,
            new BigDecimal("18.00"),  // new inherent
            new BigDecimal("0.50"),   // new controlEnv
            null, null, null, null, null, null
        );
        UniverseResponse out = service.update(ENTRY_ID, req);
        // 18 × (1 - 0.50) = 9.00
        assertThat(out.residualRiskScore()).isEqualByComparingTo("9.00");
    }

    @Test void update_doesNotRecomputeWhenScoresUnchanged() {
        IaAuditUniverseEntry e = entry("AU-UPD-002");
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENTRY_ID))
            .thenReturn(Optional.of(e));
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseUpdateRequest req = new UniverseUpdateRequest(
            "New title", null, null, null, null, null,
            null, null, null, null, null, null, null, null
        );
        UniverseResponse out = service.update(ENTRY_ID, req);
        // Residual untouched — service did NOT recompute
        assertThat(out.residualRiskScore()).isEqualByComparingTo("14.00");
        assertThat(out.titleEn()).isEqualTo("New title");
    }

    @Test void recomputeResidual_overwritesStoredValue() {
        IaAuditUniverseEntry e = entry("AU-REC-001");
        e.setInherentRiskScore(new BigDecimal("15.00"));
        e.setControlEnvironmentScore(new BigDecimal("0.40"));
        e.setResidualRiskScore(new BigDecimal("99.00")); // stale
        when(repo.findByOrganisationIdAndIdAndIsDeletedFalse(ORG_ID, ENTRY_ID))
            .thenReturn(Optional.of(e));
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseResponse out = service.recomputeResidual(ENTRY_ID);
        // 15 × (1 - 0.40) = 9.00
        assertThat(out.residualRiskScore()).isEqualByComparingTo("9.00");
    }

    // ─── Bulk import ─────────────────────────────────────────────────

    @Test void bulkImport_idempotent_skipsExisting() {
        UniverseCreateRequest e1 = createReq(PLAN_ID, "AU-A-001", "20.00", "0.30");
        UniverseCreateRequest e2 = createReq(PLAN_ID, "AU-B-002", "18.00", "0.40");
        UniverseBulkImportRequest req = new UniverseBulkImportRequest(PLAN_ID, List.of(e1, e2));

        // e1 already exists, e2 doesn't
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-A-001", PLAN_ID))
            .thenReturn(Optional.of(entry("AU-A-001")));
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-B-002", PLAN_ID))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> {
            IaAuditUniverseEntry e = inv.getArgument(0);
            e.setId(ENTRY_ID);
            return e;
        });

        UniverseBulkImportResponse out = service.bulkImport(req);
        assertThat(out.totalSubmitted()).isEqualTo(2);
        assertThat(out.created()).isEqualTo(1);
        assertThat(out.skippedExisting()).isEqualTo(1);
        verify(repo, times(1)).save(any(IaAuditUniverseEntry.class));
    }

    @Test void bulkImport_inheritsPlanIdFromRequestWhenEntryHasNone() {
        UniverseCreateRequest entryWithoutPlan = createReq(null, "AU-INHERIT-001", "10.00", "0.10");
        UniverseBulkImportRequest req = new UniverseBulkImportRequest(PLAN_ID, List.of(entryWithoutPlan));

        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-INHERIT-001", PLAN_ID))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> {
            IaAuditUniverseEntry e = inv.getArgument(0);
            e.setId(ENTRY_ID);
            return e;
        });

        UniverseBulkImportResponse out = service.bulkImport(req);
        assertThat(out.created()).isEqualTo(1);
        // It used the PLAN_ID path, not the null-plan path. Service calls
        // the duplicate-check twice (once in bulkImport, once nested inside
        // create), so we assert "at least once" rather than exactly once.
        verify(repo, org.mockito.Mockito.atLeastOnce())
            .findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-INHERIT-001", PLAN_ID);
        // And never via the null-plan path
        verify(repo, org.mockito.Mockito.never())
            .findByOrganisationIdAndEntryCodeAndPlanIdIsNullAndIsDeletedFalse(ORG_ID, "AU-INHERIT-001");
    }

    // ─── Tenant isolation ────────────────────────────────────────────

    @Test void listAll_filtersByOrgId() {
        when(repo.findByOrganisationIdAndIsDeletedFalseOrderByEntryCodeAsc(ORG_ID))
            .thenReturn(List.of(entry("AU-T-001")));
        List<UniverseResponse> out = service.listAll();
        assertThat(out).hasSize(1);
        verify(repo).findByOrganisationIdAndIsDeletedFalseOrderByEntryCodeAsc(ORG_ID);
    }

    // ─── F2-392: the service is source-of-truth — a caller-supplied residual is ignored ──
    //
    // create()'s own comment says "recompute even if caller passed an explicit value", and
    // the update()/recomputeResidual() siblings always derive it. Pre-fix, create() trusted
    // req.residualRiskScore() when non-null, letting a caller store a residual that disagreed
    // with inherent × (1 - controlEnv). These legs supply a bogus residual and assert the
    // service derives the formula value instead.

    /** The discriminating leg: create() ignores a caller-supplied residual and derives it. */
    @Test void create_ignoresCallerSuppliedResidual_andDerivesFromTheFormula() {
        UniverseCreateRequest req = new UniverseCreateRequest(
            PLAN_ID, "AU-OVERRIDE-001", "AU-OVERRIDE-001 EN", "AU-OVERRIDE-001 AR",
            null, null, null, null,
            new BigDecimal("22.00"),   // inherent
            new BigDecimal("0.30"),    // controlEnv -> formula = 22 × 0.70 = 15.40
            new BigDecimal("1.00"),    // BOGUS caller residual, must be ignored
            null, null, null, null, null, null);
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-OVERRIDE-001", PLAN_ID))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseResponse out = service.create(req);

        // Pre-fix this stored the caller's 1.00; fixed, it derives 15.40.
        assertThat(out.residualRiskScore()).isEqualByComparingTo("15.40");
    }

    /** bulkImport() routes through create(), so it inherits the fix — a bogus residual in a
     *  bulk entry is ignored and the formula value is persisted. */
    @Test void bulkImport_alsoIgnoresCallerSuppliedResidual() {
        UniverseCreateRequest bogus = new UniverseCreateRequest(
            PLAN_ID, "AU-BULK-OVR-001", "t EN", "t AR",
            null, null, null, null,
            new BigDecimal("18.00"),   // inherent
            new BigDecimal("0.40"),    // controlEnv -> formula = 18 × 0.60 = 10.80
            new BigDecimal("0.01"),    // BOGUS caller residual
            null, null, null, null, null, null);
        UniverseBulkImportRequest req = new UniverseBulkImportRequest(PLAN_ID, List.of(bogus));
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-BULK-OVR-001", PLAN_ID))
            .thenReturn(Optional.empty());
        org.mockito.ArgumentCaptor<IaAuditUniverseEntry> cap =
            org.mockito.ArgumentCaptor.forClass(IaAuditUniverseEntry.class);
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bulkImport(req);

        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getResidualRiskScore()).isEqualByComparingTo("10.80");
    }
}
