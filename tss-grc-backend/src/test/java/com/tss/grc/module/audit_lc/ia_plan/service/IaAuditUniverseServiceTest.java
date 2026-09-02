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
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
            // F2-413 removed residualRiskScore from this record — the server always derives it.
            null, null, null, null, null, null
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

    // ─── F2-392 / F2-413: the service is source-of-truth for the residual ─────────────────
    //
    // create()'s own comment says "recompute even if caller passed an explicit value", and the
    // update()/recomputeResidual() siblings always derive it. Pre-F2-392, create() trusted
    // req.residualRiskScore() when non-null, letting a caller store a residual that disagreed
    // with inherent × (1 - controlEnv), and these two legs supplied a bogus residual to prove it
    // was dropped.
    //
    // ⚠ F2-413 then REMOVED residualRiskScore from UniverseCreateRequest, and that retired the
    // claim these legs were written to make: "the server ignores a caller-supplied residual" is no
    // longer EXPRESSIBLE, because the bound type has no component for one to arrive in. The
    // absence is the enforcement (F2-233), and it is asserted by
    // InertValidationConstraintTest rather than here. 🔑 Removing an inert field retires the test
    // that proved the server ignored it — so what survives is the claim that still has content:
    // the residual is DERIVED from the formula, on both write paths.

    /** create() derives the residual from inherent × (1 - controlEnv). */
    @Test void create_derivesResidualFromTheFormula() {
        UniverseCreateRequest req = new UniverseCreateRequest(
            PLAN_ID, "AU-OVERRIDE-001", "AU-OVERRIDE-001 EN", "AU-OVERRIDE-001 AR",
            null, null, null, null,
            new BigDecimal("22.00"),   // inherent
            new BigDecimal("0.30"),    // controlEnv -> formula = 22 × 0.70 = 15.40
            null, null, null, null, null, null);
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-OVERRIDE-001", PLAN_ID))
            .thenReturn(Optional.empty());
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        UniverseResponse out = service.create(req);

        // Pre-fix this stored the caller's 1.00; fixed, it derives 15.40.
        assertThat(out.residualRiskScore()).isEqualByComparingTo("15.40");
    }

    /** bulkImport() routes through create(), so the derivation holds on that path too. */
    @Test void bulkImport_alsoDerivesResidualFromTheFormula() {
        UniverseCreateRequest entry = new UniverseCreateRequest(
            PLAN_ID, "AU-BULK-OVR-001", "t EN", "t AR",
            null, null, null, null,
            new BigDecimal("18.00"),   // inherent
            new BigDecimal("0.40"),    // controlEnv -> formula = 18 × 0.60 = 10.80
            null, null, null, null, null, null);
        UniverseBulkImportRequest req = new UniverseBulkImportRequest(PLAN_ID, List.of(entry));
        when(repo.findByOrganisationIdAndEntryCodeAndPlanIdAndIsDeletedFalse(ORG_ID, "AU-BULK-OVR-001", PLAN_ID))
            .thenReturn(Optional.empty());
        org.mockito.ArgumentCaptor<IaAuditUniverseEntry> cap =
            org.mockito.ArgumentCaptor.forClass(IaAuditUniverseEntry.class);
        when(repo.save(any(IaAuditUniverseEntry.class))).thenAnswer(inv -> inv.getArgument(0));

        service.bulkImport(req);

        verify(repo).save(cap.capture());
        assertThat(cap.getValue().getResidualRiskScore()).isEqualByComparingTo("10.80");
    }
    // ═══ F2-425 — the derived residual cannot leave its column once its inputs are bounded ═════

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void startValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void stopValidator() {
        if (validatorFactory != null) validatorFactory.close();
    }

    private static Set<String> violatedFields(Object req) {
        return validator.validate(req).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static UniverseCreateRequest scored(String inherent, String strength) {
        return new UniverseCreateRequest(null, "AU-F425-001", "T", "ت", null, null, null, null,
                new BigDecimal(inherent), new BigDecimal(strength),
                null, null, null, null, null, null);
    }

    /**
     * The arithmetic that the caps exist for. This leg does NOT discriminate — {@code
     * computeResidual} is unchanged, so it passes before and after F2-425 (F2-225). It is here
     * because the two numbers it records are the whole reason the bounds are what they are, and a
     * reader who deletes a cap should meet them.
     */
    @Test
    void computeResidual_unboundedStrengthLeavesTheColumnInBothDirections() {
        // A control strength written as a PERCENTAGE rather than the documented 0-1 fraction — the
        // obvious slip on the bulk-paste form, which does Number(cell) with no range check at all.
        assertThat(IaAuditUniverseService.computeResidual(
                new BigDecimal("22"), new BigDecimal("75")))
            .as("strength as a percentage derives a residual far outside decimal(4,2) -> SQL 8115")
            .isEqualByComparingTo("-1628.00");

        // Only slightly out of range, and this one is the quiet half: it STORES.
        assertThat(IaAuditUniverseService.computeResidual(
                new BigDecimal("22"), new BigDecimal("1.5")))
            .as("a strength above 1 derives a NEGATIVE residual, which decimal(4,2) accepts — it "
                + "then bands 'low' (residualBand's fall-through is green) and sorts LAST in both "
                + "surfaces that rank the audit universe by residual DESC")
            .isEqualByComparingTo("-11.00");

        // A negative strength inverts (1 - strength) and pushes the residual up instead.
        assertThat(IaAuditUniverseService.computeResidual(
                new BigDecimal("25"), new BigDecimal("-3")))
            .as("a negative strength overflows decimal(4,2) upward")
            .isEqualByComparingTo("100.00");
    }

    /**
     * The invariant the caps buy: over the whole bounded input box the residual is between zero and
     * inherent, so it is inside decimal(4,2) BY CONSTRUCTION rather than by a check somebody has to
     * remember to keep (F2-213 — absent rather than latent).
     */
    @Test
    void computeResidual_staysInsideItsColumnOverTheWholeBoundedBox() {
        BigDecimal ceiling = new BigDecimal("99.99");
        for (String inherent : new String[] {"0.00", "0.01", "22.00", "50.00", "99.99"}) {
            for (String strength : new String[] {"0.00", "0.30", "0.99", "1.00"}) {
                BigDecimal res = IaAuditUniverseService.computeResidual(
                        new BigDecimal(inherent), new BigDecimal(strength));
                assertThat(res)
                    .as("residual for inherent=%s strength=%s must not be negative", inherent, strength)
                    .isGreaterThanOrEqualTo(BigDecimal.ZERO);
                assertThat(res)
                    .as("residual for inherent=%s strength=%s must fit decimal(4,2)", inherent, strength)
                    .isLessThanOrEqualTo(ceiling);
            }
        }
    }

    @Test
    void theBoundaryRefusesAControlStrengthWrittenAsAPercentage() {
        assertThat(violatedFields(scored("22", "75")))
            .as("75 means 75% to a human and 7500% to the formula")
            .contains("controlEnvironmentScore");
        assertThat(violatedFields(scored("22", "1.5")))
            .as("anything above 1 makes the residual negative")
            .contains("controlEnvironmentScore");
        assertThat(violatedFields(scored("22", "-0.1")))
            .as("a negative strength pushes the residual above its column")
            .contains("controlEnvironmentScore");
        assertThat(violatedFields(scored("100", "0.30")))
            .as("inherent above the column's own decimal(4,2) ceiling")
            .contains("inherentRiskScore");
    }

    @Test
    void theBoundaryStillAcceptsEveryScoreTheRegisterHolds() {
        // Both databases measure 20 rows with inherent in [13.00, 22.00] and strength in
        // [0.30, 0.55], 0 out of range on either axis — which is what makes the caps safe to
        // enable at all (F2-183). The extremes of the register plus the exact bounds:
        for (String[] ok : new String[][] {{"13.00", "0.30"}, {"22.00", "0.55"},
                                           {"0.00", "0.00"}, {"99.99", "1.00"}}) {
            assertThat(violatedFields(scored(ok[0], ok[1])))
                .as("inherent=%s strength=%s is legitimate and must not be refused", ok[0], ok[1])
                .doesNotContain("inherentRiskScore", "controlEnvironmentScore");
        }
    }
}
