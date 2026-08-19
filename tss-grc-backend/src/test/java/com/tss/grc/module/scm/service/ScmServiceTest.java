package com.tss.grc.module.scm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.dto.*;
import com.tss.grc.module.scm.entity.*;
import com.tss.grc.module.scm.repository.*;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ScmService — Sprint 4 §4.1 test backlog.
 *
 * Locks the supply-chain-management contracts:
 *
 * 1. Onboarding 6-stage walker:
 *      REGISTRATION → CRITICALITY_CLASSIFICATION → SECURITY_REVIEW
 *                  → OPERATIONAL_REVIEW → LEGAL_REVIEW → APPROVAL
 *    Each call to advanceOnboarding() advances one stage and appends the
 *    completed stage to the audit string. Final stage flips status=COMPLETED
 *    AND cascades onto vendor.onboardingStatus + vendor.status.
 *
 * 2. Supplier-incident flag flips: activateBackup, createCapa, escalateIncident,
 *    resolveIncident — each persists the corresponding flag/timestamp.
 *
 * 3. Assessment escalation: sets escalatedToRisk + status=ESCALATED (link into
 *    the ERM module).
 *
 * 4. Tenant scope on all reads/writes.
 */
@ExtendWith(MockitoExtension.class)
class ScmServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long VENDOR_ID = 11L;
    private static final Long ID        = 50L;

    @Mock private VendorRepository vendorRepo;
    @Mock private VendorOnboardingRepository onboardingRepo;
    @Mock private VendorAssessmentRepository assessmentRepo;
    @Mock private DependencyRepository dependencyRepo;
    @Mock private SupplierIncidentRepository incidentRepo;
    @Mock private ContractRepository contractRepo;
    @Mock private SlaRepository slaRepo;
    @InjectMocks private ScmService service;

    /**
     * F2-246 — a REAL {@link VendorReferenceResolver} over the mocked repository, replacing
     * the mock Mockito injects.
     *
     * <p>{@code ScmService#requireOwnVendor} now delegates its tenant check to that resolver,
     * and this class's cross-tenant legs (F2-242) exist to prove the check refuses. A mocked
     * collaborator returns null, so every one of those legs would go green while asserting
     * nothing — the shape F2-237 recorded, where a mocked repository happily returns rows for
     * a predicate that is wrong. The resolver is a pure function of the repository, so using
     * the real one costs nothing and keeps the legs about what they claim.
     */
    @BeforeEach void setUp() throws Exception {
        TenantContext.setCurrentTenant(ORG_ID);
        var f = ScmService.class.getDeclaredField("vendorRefs");
        f.setAccessible(true);
        f.set(service, new VendorReferenceResolver(vendorRepo));
    }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Vendor vendor(Long orgId) {
        Vendor v = new Vendor();
        v.setId(VENDOR_ID);
        v.setOrganisationId(orgId);
        v.setNameEn("Acme Cloud Services");
        v.setStatus("PENDING");
        v.setOnboardingStatus("PENDING");
        return v;
    }

    private static VendorOnboarding onboarding(String stage, Long orgId) {
        VendorOnboarding o = new VendorOnboarding();
        o.setId(ID);
        o.setOrganisationId(orgId);
        o.setVendorId(VENDOR_ID);
        o.setCurrentStage(stage);
        o.setStatus("IN_PROGRESS");
        o.setCompletedStages("");
        return o;
    }

    private static SupplierIncident incident(Long orgId) {
        SupplierIncident i = new SupplierIncident();
        i.setId(ID);
        i.setOrganisationId(orgId);
        i.setVendorId(VENDOR_ID);
        i.setStatus("OPEN");
        return i;
    }

    // ─── Onboarding stage walker ─────────────────────────────────────

    @Test
    void advanceOnboarding_walks_full_6_stage_chain() {
        String[] stages = {
            "REGISTRATION", "CRITICALITY_CLASSIFICATION", "SECURITY_REVIEW",
            "OPERATIONAL_REVIEW", "LEGAL_REVIEW", "APPROVAL"
        };
        for (int i = 0; i < stages.length - 1; i++) {
            VendorOnboarding o = onboarding(stages[i], ORG_ID);
            when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
            when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
            // vendorIndex() lookup
            when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

            service.advanceOnboarding(ID);
            assertThat(o.getCurrentStage()).isEqualTo(stages[i + 1]);
            assertThat(o.getCompletedStages()).contains(stages[i]);
        }
    }

    @Test
    void advanceOnboarding_at_final_stage_marks_COMPLETED_and_activates_vendor() {
        VendorOnboarding o = onboarding("APPROVAL", ORG_ID);
        Vendor v = vendor(ORG_ID);
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(v));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));

        service.advanceOnboarding(ID);

        assertThat(o.getStatus()).isEqualTo("COMPLETED");
        assertThat(o.getCompletedStages()).contains("APPROVAL");
        // Cascade: vendor flipped to ACTIVE and onboardingStatus=COMPLETED
        assertThat(v.getStatus()).isEqualTo("ACTIVE");
        assertThat(v.getOnboardingStatus()).isEqualTo("COMPLETED");
        verify(vendorRepo).save(v);
    }

    @Test
    void advanceOnboarding_unknown_stage_falls_back_to_REGISTRATION_index() {
        // Defensive: a corrupted record with an unknown stage starts walking from index 0
        VendorOnboarding o = onboarding("LEGACY_VALUE", ORG_ID);
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.advanceOnboarding(ID);
        // From idx=0 (REGISTRATION fallback), advances to idx=1 = CRITICALITY_CLASSIFICATION
        assertThat(o.getCurrentStage()).isEqualTo("CRITICALITY_CLASSIFICATION");
        assertThat(o.getCompletedStages()).contains("REGISTRATION");
    }

    @Test
    void advanceOnboarding_other_tenant_throws_EntityNotFound() {
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(onboarding("REGISTRATION", OTHER_ORG)));

        assertThatThrownBy(() -> service.advanceOnboarding(ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Onboarding not found");

        verify(onboardingRepo, never()).save(any());
    }

    @Test
    void approveOnboarding_short_circuits_to_APPROVAL_and_activates_vendor() {
        VendorOnboarding o = onboarding("SECURITY_REVIEW", ORG_ID);
        Vendor v = vendor(ORG_ID);
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(v));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));

        service.approveOnboarding(ID);

        assertThat(o.getStatus()).isEqualTo("COMPLETED");
        assertThat(o.getCurrentStage()).isEqualTo("APPROVAL");
        assertThat(v.getStatus()).isEqualTo("ACTIVE");
        assertThat(v.getOnboardingStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void rejectOnboarding_records_reason_and_flips_vendor_to_REJECTED() {
        VendorOnboarding o = onboarding("LEGAL_REVIEW", ORG_ID);
        Vendor v = vendor(ORG_ID);
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(v));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));

        service.rejectOnboarding(ID, "Failed PDPL data residency review");

        assertThat(o.getStatus()).isEqualTo("REJECTED");
        assertThat(o.getRejectionReason()).contains("PDPL");
        assertThat(v.getOnboardingStatus()).isEqualTo("REJECTED");
        // Vendor.status stays as PENDING — rejection doesn't activate them
        assertThat(v.getStatus()).isEqualTo("PENDING");
    }

    // ─── startOnboarding ─────────────────────────────────────────────

    @Test
    void startOnboarding_creates_at_REGISTRATION_and_flips_vendor_to_IN_PROGRESS() {
        OnboardingDtos.Request req = new OnboardingDtos.Request();
        req.setVendorId(VENDOR_ID);
        req.setAssignedToId(99L);
        Vendor v = vendor(ORG_ID);
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> {
            VendorOnboarding saved = inv.getArgument(0);
            saved.setId(ID);
            return saved;
        });
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(v));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));

        service.startOnboarding(req);

        ArgumentCaptor<VendorOnboarding> captor = ArgumentCaptor.forClass(VendorOnboarding.class);
        verify(onboardingRepo).save(captor.capture());
        VendorOnboarding saved = captor.getValue();
        assertThat(saved.getCurrentStage()).isEqualTo("REGISTRATION");
        assertThat(saved.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(saved.getOrganisationId()).isEqualTo(ORG_ID);
        assertThat(v.getOnboardingStatus()).isEqualTo("IN_PROGRESS");
    }

    // ─── Supplier incident workflow ──────────────────────────────────

    @Test
    void activateBackup_flips_backupPlanActivated_true() {
        SupplierIncident i = incident(ORG_ID);
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(i));
        when(incidentRepo.save(any(SupplierIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.activateBackup(ID);
        assertThat(i.getBackupPlanActivated()).isTrue();
    }

    @Test
    void createCapa_flips_capaCreated_true() {
        SupplierIncident i = incident(ORG_ID);
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(i));
        when(incidentRepo.save(any(SupplierIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.createCapa(ID);
        assertThat(i.getCapaCreated()).isTrue();
    }

    @Test
    void escalateIncident_flips_escalatedToManagement_true() {
        SupplierIncident i = incident(ORG_ID);
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(i));
        when(incidentRepo.save(any(SupplierIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.escalateIncident(ID);
        assertThat(i.getEscalatedToManagement()).isTrue();
    }

    @Test
    void resolveIncident_sets_RESOLVED_status_resolvedAt_and_notes() {
        SupplierIncident i = incident(ORG_ID);
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(i));
        when(incidentRepo.save(any(SupplierIncident.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.resolveIncident(ID, "Vendor restored service after 3h outage");
        assertThat(i.getStatus()).isEqualTo("RESOLVED");
        assertThat(i.getResolvedAt()).isNotNull();
        assertThat(i.getResolutionNotes()).contains("3h outage");
    }

    @Test
    void resolveIncident_other_tenant_throws_EntityNotFound() {
        when(incidentRepo.findById(ID)).thenReturn(Optional.of(incident(OTHER_ORG)));

        assertThatThrownBy(() -> service.resolveIncident(ID, "n/a"))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Incident not found");

        verify(incidentRepo, never()).save(any());
    }

    // ─── Assessment escalation ───────────────────────────────────────

    @Test
    void escalateAssessment_sets_escalatedToRisk_and_status_ESCALATED() {
        VendorAssessment a = new VendorAssessment();
        a.setId(ID);
        a.setOrganisationId(ORG_ID);
        a.setVendorId(VENDOR_ID);
        // F2-241 — was "DRAFT", a token CK_scm_assess_status REFUSES, so this fixture described a row
        // that could not exist in either database. A mocked repository has no constraint, which is
        // exactly why the illegal initialiser it was copied from looked normal for as long as it did.
        a.setStatus("PLANNED");
        when(assessmentRepo.findById(ID)).thenReturn(Optional.of(a));
        when(assessmentRepo.save(any(VendorAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));

        service.escalateAssessment(ID);
        assertThat(a.getEscalatedToRisk()).isTrue();
        assertThat(a.getStatus()).isEqualTo("ESCALATED");
    }

    // ─── Vendor: tenant scope + soft delete ──────────────────────────

    @Test
    void getVendor_other_tenant_throws_EntityNotFound() {
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG)));

        assertThatThrownBy(() -> service.getVendor(VENDOR_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");
    }

    @Test
    void deleteVendor_soft_deletes_via_save() {
        Vendor v = vendor(ORG_ID);
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));
        when(vendorRepo.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteVendor(VENDOR_ID);

        assertThat(v.getIsDeleted()).isTrue();
        verify(vendorRepo, times(1)).save(v);
        verify(vendorRepo, never()).deleteById(any());
    }

    // ─── F2-241: the create's defaults, and the caller's value ───────────────────────────────────

    /**
     * The behavioural half of {@code EntityInitialiserAcceptedByColumnTest}.
     *
     * <p>⚠ <b>No mocked test can reproduce the failure this fixes</b> — a mocked repository has no
     * CHECK constraint, so the illegal {@code "DRAFT"} was invisible to the whole suite and only the
     * live probe against real SQL Server disagreed (the same reason CLAUDE.md gives for the ROWVERSION
     * family). What is assertable here is the property the fix establishes: the values a bare create
     * lands on are the values the column admits, read from the migration SQL rather than restated.
     */
    private AssessmentDtos.Request bareAssessment() {
        AssessmentDtos.Request r = new AssessmentDtos.Request();
        r.setVendorId(VENDOR_ID);
        return r;                        // no type, no status, no rating — all three defaults apply
    }

    private VendorAssessment createdFrom(AssessmentDtos.Request req) {
        // ⚠ F2-242 CORRECTED THIS COMMENT. It read: "It does NOT resolve the vendor at all … so
        // stubbing vendorRepo.findById here would be an UnnecessaryStubbing that hid a real
        // asymmetry. Filed, not fixed in this slice." That filing is now closed — createAssessment
        // resolves the vendor inside the tenant (requireOwnVendor), so the stub below is REQUIRED,
        // and its absence is what an unverified reference looks like from a test's point of view.
        // Note the direction: an UnnecessaryStubbing failure is how F2-241 discovered the asymmetry
        // in the first place, and a MISSING stub is how it would have been reintroduced.
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID)));
        when(assessmentRepo.save(any(VendorAssessment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));
        ArgumentCaptor<VendorAssessment> saved = ArgumentCaptor.forClass(VendorAssessment.class);
        service.createAssessment(req);
        verify(assessmentRepo).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void createAssessment_lands_on_values_the_columns_own_CHECKs_admit() {
        VendorAssessment a = createdFrom(bareAssessment());

        // Compared against the migration SQL, never against a second copy of the vocabulary — a
        // restated list is the AttachmentAccessPolicy shape (two lists agreeing, maintained by nobody).
        assertThat(a.getStatus())
            .as("status was initialised \"DRAFT\", which CK_scm_assess_status refuses, so every "
              + "create that omitted the key was a refused INSERT -> 500")
            .isIn(com.tss.grc.support.MigrationVocabularies
                .vocabularyOn("scm", "vendor_assessments", "status"));
        assertThat(a.getRiskRating())
            .as("risk_rating is NOT NULL and had no entity-side filling mechanism at all, so an "
              + "omitted key arrived as an explicit null — a DB DEFAULT cannot rescue an insertable "
              + "column (F2-240)")
            .isIn(com.tss.grc.support.MigrationVocabularies
                .vocabularyOn("scm", "vendor_assessments", "risk_rating"));
        assertThat(a.getAssessmentType())
            .isIn(com.tss.grc.support.MigrationVocabularies
                .vocabularyOn("scm", "vendor_assessments", "assessment_type"));
    }

    @Test
    void createAssessment_keeps_a_value_the_caller_did_supply() {
        AssessmentDtos.Request r = bareAssessment();
        r.setStatus("IN_PROGRESS");
        r.setRiskRating("CRITICAL");
        r.setAssessmentType("TRIGGERED");

        VendorAssessment a = createdFrom(r);
        assertThat(a.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(a.getRiskRating()).isEqualTo("CRITICAL");
        assertThat(a.getAssessmentType()).isEqualTo("TRIGGERED");
    }

    @Test
    void applyAssessment_normalises_the_casing_the_collation_would_have_let_through() {
        // F2-179: these columns collate case-INSENSITIVELY, so 'in_progress' was ACCEPTED by the
        // CHECK and stored verbatim — and ASSESS_STATUS_OPTIONS plus the register's tone map compare
        // case-sensitively, so the row rendered with no status. Normalising at the boundary makes the
        // constraint, the Java comparison and the frontend's label map agree.
        AssessmentDtos.Request r = bareAssessment();
        r.setStatus("in_progress");
        r.setRiskRating("  critical  ");

        VendorAssessment a = createdFrom(r);
        assertThat(a.getStatus()).isEqualTo("IN_PROGRESS");
        assertThat(a.getRiskRating()).isEqualTo("CRITICAL");
    }

    @Test
    void applyAssessment_refuses_a_token_the_column_does_not_have() {
        AssessmentDtos.Request r = bareAssessment();
        r.setStatus("DRAFT");            // the literal pre-fix initialiser: legal nowhere
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID)));

        assertThatThrownBy(() -> service.createAssessment(r))
            .as("require, not coerce: nothing upstream coerces this field and the CHECK already "
              + "refuses it, so every value that succeeds today is a case-variant of a legal token "
              + "and a 400 naming the field cannot break a working request (F2-182)")
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status")
            .hasMessageContaining("IN_PROGRESS");
        verify(assessmentRepo, never()).save(any(VendorAssessment.class));
    }

    // ─── F2-242: an unverified foreign key is a cross-tenant write ───────────────────────────────

    /**
     * The behavioural half of {@code UnverifiedForeignKeyWriteContractTest}, which can only assert
     * the <em>shape</em>. These assert the <em>consequence</em>: probed live, a caller holding only
     * {@code SCM:WRITE} in organisation 1 moved an organisation-2 vendor from BLACKLISTED to ACTIVE
     * through {@code POST /scm/onboarding} + {@code /approve}.
     *
     * <p>⚠ Note what the mocks CAN and cannot show. A mocked repository has no tenant filter of its
     * own, so {@code findById} returning a foreign vendor is exactly the situation the real database
     * produces — which is why this family IS unit-testable where F2-241's CHECK family was not.
     */
    @Test
    void startOnboarding_refuses_a_vendor_of_another_tenant() {
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG)));
        OnboardingDtos.Request r = new OnboardingDtos.Request();
        r.setVendorId(VENDOR_ID);

        assertThatThrownBy(() -> service.startOnboarding(r))
            .as("the create is where the reference is STORED, so it is where the tenant question "
              + "has to be answered — everything downstream dereferences whatever it wrote")
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");

        verify(onboardingRepo, never()).save(any(VendorOnboarding.class));
        verify(vendorRepo, never()).save(any(Vendor.class));
    }

    @Test
    void startOnboarding_writes_the_vendor_it_resolved_and_nothing_else() {
        Vendor v = vendor(ORG_ID);
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(v));
        OnboardingDtos.Request r = new OnboardingDtos.Request();
        r.setVendorId(VENDOR_ID);

        service.startOnboarding(r);

        assertThat(v.getOnboardingStatus()).isEqualTo("IN_PROGRESS");
        // Exactly ONE load. The pre-fix code resolved the vendor a second time, unpredicated, to do
        // the write — so the object it verified and the object it wrote were not the same object.
        verify(vendorRepo, times(1)).findById(VENDOR_ID);
        verify(vendorRepo, times(1)).save(v);
    }

    @Test
    void approveOnboarding_will_not_touch_a_vendor_outside_the_onboarding_rows_tenant() {
        VendorOnboarding o = new VendorOnboarding();
        o.setId(ID);
        o.setOrganisationId(ORG_ID);
        o.setVendorId(VENDOR_ID);
        when(onboardingRepo.findById(ID)).thenReturn(Optional.of(o));
        when(onboardingRepo.save(any(VendorOnboarding.class))).thenAnswer(inv -> inv.getArgument(0));
        when(vendorRepo.findByOrganisationId(ORG_ID)).thenReturn(List.of(vendor(ORG_ID)));
        // A stored reference that points outside the tenant — the row a data fix or a restore
        // leaves behind. Reachability is a fact about today, not a property of a control (F2-182).
        Vendor foreign = vendor(OTHER_ORG);
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(foreign));

        service.approveOnboarding(ID);

        assertThat(foreign.getStatus())
            .as("pre-fix this flipped a supplier another tenant had BLACKLISTED to ACTIVE")
            .isEqualTo(vendor(OTHER_ORG).getStatus());
        verify(vendorRepo, never()).save(any(Vendor.class));
    }

    @Test
    void createIncident_refuses_a_vendor_of_another_tenant() {
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG)));
        IncidentDtos.Request r = new IncidentDtos.Request();
        r.setVendorId(VENDOR_ID);
        r.setIncidentType("OTHER");

        assertThatThrownBy(() -> service.createIncident(r))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");
        verify(incidentRepo, never()).save(any(SupplierIncident.class));
    }

    @Test
    void createAssessment_refuses_a_vendor_of_another_tenant() {
        // The F2-241 filing, closed: TprmService#createAssessment resolves the vendor with the
        // comment "verify org ownership" and this twin never did.
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG)));

        assertThatThrownBy(() -> service.createAssessment(bareAssessment()))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");
        verify(assessmentRepo, never()).save(any(VendorAssessment.class));
    }

    @Test
    void createDependency_refuses_a_foreign_vendor_on_EITHER_reference() {
        // Two references on one row, and only checking the obvious one leaves the other open —
        // F2-215's two-of-N shape at its smallest.
        DependencyDtos.Request r = new DependencyDtos.Request();
        r.setVendorId(VENDOR_ID);
        r.setAlternativeVendorId(12L);
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID)));
        when(vendorRepo.findById(12L)).thenReturn(Optional.of(vendor(OTHER_ORG)));

        assertThatThrownBy(() -> service.createDependency(r))
            .as("alternativeVendorId is the second reference and it is the one a reader skips")
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("12");
        verify(dependencyRepo, never()).save(any(Dependency.class));
    }

    @Test
    void sla_lands_on_the_status_its_column_admits() {
        // scm.slas has NO create path anywhere — no controller, no route, no frontend client, and
        // SlaRepository is injected into ScmService and never used. So this asserts the entity, which
        // is the only artefact that decides the value. It was "ON_TRACK": refused by the CHECK, and
        // the FLATTERING answer — an SLA nobody has measured is NOT_MEASURED, not on track.
        assertThat(new Sla().getStatus())
            .isIn(com.tss.grc.support.MigrationVocabularies.vocabularyOn("scm", "slas", "status"));
    }
}
