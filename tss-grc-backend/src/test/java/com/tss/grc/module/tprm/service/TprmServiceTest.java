package com.tss.grc.module.tprm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TprmService — Sprint 4 §4.1 test backlog.
 *
 * Locks the third-party-risk invariants:
 *   - Tenant scope on all vendor reads (cross-org → 404)
 *   - Vendor delete is soft (sets isDeleted=true via save, never deleteById)
 *   - Assessment create REQUIRES the vendor to exist in caller's org —
 *     prevents orphan assessments and cross-tenant assessment injection
 *   - High-risk count aggregates HIGH + CRITICAL (severity bucket merge)
 */
@ExtendWith(MockitoExtension.class)
class TprmServiceTest {

    private static final Long ORG_ID    = 1L;
    private static final Long OTHER_ORG = 999L;
    private static final Long VENDOR_ID = 11L;
    private static final Long ASSESS_ID = 77L;

    @Mock private VendorRepository vendorRepo;
    @Mock private VendorAssessmentRepository assessmentRepo;
    @InjectMocks private TprmService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_ID); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    private static Vendor vendor(Long orgId, String riskRating) {
        Vendor v = new Vendor();
        v.setId(VENDOR_ID);
        v.setOrganisationId(orgId);
        v.setVendorName("Acme Cloud Co.");
        v.setRiskRating(riskRating);
        v.setStatus("ACTIVE");
        return v;
    }

    private static VendorAssessment assessment(Long vendorId, String status) {
        VendorAssessment a = new VendorAssessment();
        a.setId(ASSESS_ID);
        a.setVendorId(vendorId);
        a.setStatus(status);
        a.setRiskRating("HIGH");
        return a;
    }

    // ─── Vendor: tenant scope ────────────────────────────────────────

    @Test
    void getVendor_other_tenant_throws_EntityNotFound() {
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "MEDIUM")));

        assertThatThrownBy(() -> service.getVendor(VENDOR_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");
    }

    @Test
    void getVendor_unknown_id_throws_EntityNotFound() {
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getVendor(VENDOR_ID))
            .isInstanceOf(EntityNotFoundException.class);
    }

    // ─── Vendor create / update / soft-delete ────────────────────────

    @Test
    void createVendor_stamps_organisation_from_TenantContext() {
        Vendor req = new Vendor();
        req.setVendorName("New Cloud Co.");
        when(vendorRepo.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createVendor(req);

        ArgumentCaptor<Vendor> captor = ArgumentCaptor.forClass(Vendor.class);
        verify(vendorRepo).save(captor.capture());
        assertThat(captor.getValue().getOrganisationId()).isEqualTo(ORG_ID);
    }

    @Test
    void updateVendor_other_tenant_throws_EntityNotFound() {
        Vendor req = new Vendor();
        req.setVendorName("renamed");
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "LOW")));

        assertThatThrownBy(() -> service.updateVendor(VENDOR_ID, req))
            .isInstanceOf(EntityNotFoundException.class);

        verify(vendorRepo, never()).save(any());
    }

    @Test
    void updateVendor_bumps_updatedAt() {
        Vendor existing = vendor(ORG_ID, "MEDIUM");
        java.time.LocalDateTime prior = java.time.LocalDateTime.now().minusDays(7);
        existing.setUpdatedAt(prior);
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(existing));
        when(vendorRepo.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        Vendor req = new Vendor();
        req.setVendorName("Acme Cloud Co. (updated)");
        req.setRiskRating("HIGH");
        service.updateVendor(VENDOR_ID, req);

        assertThat(existing.getUpdatedAt()).isAfter(prior);
        assertThat(existing.getRiskRating()).isEqualTo("HIGH");
    }

    @Test
    void deleteVendor_soft_deletes_via_save() {
        Vendor v = vendor(ORG_ID, "MEDIUM");
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(v));
        when(vendorRepo.save(any(Vendor.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteVendor(VENDOR_ID);

        assertThat(v.getIsDeleted()).isTrue();
        verify(vendorRepo).save(v);
        verify(vendorRepo, never()).deleteById(any());
    }

    // ─── Assessment: cross-tenant vendor injection prevention ───────

    @Test
    void createAssessment_for_other_tenant_vendor_throws_EntityNotFound() {
        // Critical security check: even if a caller submits an assessment for a
        // vendor in OTHER_ORG, the vendor lookup must reject it before the
        // assessment is persisted.
        VendorAssessment req = assessment(VENDOR_ID, "PLANNED");
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "MEDIUM")));

        assertThatThrownBy(() -> service.createAssessment(req))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");

        verify(assessmentRepo, never()).save(any());
    }

    @Test
    void createAssessment_for_same_tenant_vendor_succeeds() {
        VendorAssessment req = assessment(VENDOR_ID, "PLANNED");
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID, "MEDIUM")));
        when(assessmentRepo.save(any(VendorAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAssessment(req);
        verify(assessmentRepo).save(req);
    }

    @Test
    void createAssessment_for_unknown_vendor_throws() {
        VendorAssessment req = assessment(VENDOR_ID, "PLANNED");
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createAssessment(req))
            .isInstanceOf(EntityNotFoundException.class);
    }

    @Test
    void listAssessments_with_vendorId_verifies_org_ownership_first() {
        // Caller passes a vendorId — must walk through getVendor() so a vendor
        // belonging to OTHER_ORG is rejected before any assessment query runs.
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "MEDIUM")));

        assertThatThrownBy(() -> service.listAssessments(VENDOR_ID, Pageable.unpaged()))
            .isInstanceOf(EntityNotFoundException.class);

        // assessmentRepo.findByVendorId never called
        verify(assessmentRepo, never()).findByVendorId(any(), any());
    }

    @Test
    void listAssessments_noVendorId_usesPagedOrgScopedFinder_notVendorScan() {
        // PERF-PG-002: the org-wide listing must hit the paged org-scoped finder
        // directly — never the old "scan every vendor + findByVendorIdIn" path.
        Page<VendorAssessment> page = new PageImpl<>(java.util.List.of(assessment(VENDOR_ID, "REVIEWED")));
        when(assessmentRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);

        Page<VendorAssessment> out = service.listAssessments(null, Pageable.ofSize(20));

        assertThat(out.getContent()).hasSize(1);
        verify(assessmentRepo).findByOrganisationId(eq(ORG_ID), any(Pageable.class));
        verify(assessmentRepo, never()).findByVendorIdIn(any());
        verify(vendorRepo, never()).findByOrganisationId(any(), any());
    }

    @Test
    void deleteAssessment_unknown_id_throws() {
        when(assessmentRepo.findById(ASSESS_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAssessment(ASSESS_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Assessment not found");
    }

    @Test
    void deleteAssessment_soft_deletes_via_save() {
        VendorAssessment a = assessment(VENDOR_ID, "COMPLETED");
        when(assessmentRepo.findById(ASSESS_ID)).thenReturn(Optional.of(a));
        // SEC-TPRM-001: deleteAssessment now resolves the parent vendor to enforce
        // tenant scope — vendor must be in the caller's org for the delete to proceed.
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID, "MEDIUM")));
        when(assessmentRepo.save(any(VendorAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteAssessment(ASSESS_ID);

        assertThat(a.getIsDeleted()).isTrue();
        verify(assessmentRepo).save(a);
        verify(assessmentRepo, never()).deleteById(any());
    }

    // ─── SEC-TPRM-001: cross-tenant assessment mutation prevention ───

    @Test
    void updateAssessment_other_tenant_throws_EntityNotFound() {
        // Assessment exists, but its parent vendor belongs to OTHER_ORG — the
        // tenant guard via getVendor() must reject before any mutation/save.
        VendorAssessment existing = assessment(VENDOR_ID, "PLANNED");
        when(assessmentRepo.findById(ASSESS_ID)).thenReturn(Optional.of(existing));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "MEDIUM")));

        VendorAssessment req = assessment(VENDOR_ID, "REVIEWED");
        assertThatThrownBy(() -> service.updateAssessment(ASSESS_ID, req))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");

        verify(assessmentRepo, never()).save(any());
    }

    @Test
    void updateAssessment_same_tenant_succeeds() {
        VendorAssessment existing = assessment(VENDOR_ID, "PLANNED");
        when(assessmentRepo.findById(ASSESS_ID)).thenReturn(Optional.of(existing));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(ORG_ID, "MEDIUM")));
        when(assessmentRepo.save(any(VendorAssessment.class))).thenAnswer(inv -> inv.getArgument(0));

        VendorAssessment req = assessment(VENDOR_ID, "REVIEWED");
        req.setFindings("clean");
        service.updateAssessment(ASSESS_ID, req);

        assertThat(existing.getStatus()).isEqualTo("REVIEWED");
        assertThat(existing.getFindings()).isEqualTo("clean");
        verify(assessmentRepo).save(existing);
    }

    @Test
    void deleteAssessment_other_tenant_throws_EntityNotFound() {
        VendorAssessment a = assessment(VENDOR_ID, "COMPLETED");
        when(assessmentRepo.findById(ASSESS_ID)).thenReturn(Optional.of(a));
        when(vendorRepo.findById(VENDOR_ID)).thenReturn(Optional.of(vendor(OTHER_ORG, "MEDIUM")));

        assertThatThrownBy(() -> service.deleteAssessment(ASSESS_ID))
            .isInstanceOf(EntityNotFoundException.class)
            .hasMessageContaining("Vendor not found");

        assertThat(a.getIsDeleted()).isFalse();
        verify(assessmentRepo, never()).save(any());
    }

    // ─── Stats ───────────────────────────────────────────────────────

    @Test
    void getStats_highRiskVendors_merges_HIGH_and_CRITICAL() {
        // Stats counter must roll up CRITICAL into the high-risk bucket so
        // boards see one number rather than two split categories.
        Page<Vendor> page = new PageImpl<>(java.util.List.of()) {
            @Override public long getTotalElements() { return 25; }
        };
        when(vendorRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(vendorRepo.countByOrganisationIdAndRiskRating(ORG_ID, "HIGH")).thenReturn(4L);
        when(vendorRepo.countByOrganisationIdAndRiskRating(ORG_ID, "CRITICAL")).thenReturn(2L);

        Map<String, Long> stats = service.getStats();

        assertThat(stats).containsEntry("totalVendors", 25L);
        assertThat(stats).containsEntry("highRiskVendors", 6L); // 4 HIGH + 2 CRITICAL
    }

    @Test
    void getStats_zero_vendors_returns_zero_high_risk() {
        Page<Vendor> page = new PageImpl<>(java.util.List.of());
        when(vendorRepo.findByOrganisationId(eq(ORG_ID), any(Pageable.class))).thenReturn(page);
        when(vendorRepo.countByOrganisationIdAndRiskRating(ORG_ID, "HIGH")).thenReturn(0L);
        when(vendorRepo.countByOrganisationIdAndRiskRating(ORG_ID, "CRITICAL")).thenReturn(0L);

        Map<String, Long> stats = service.getStats();
        assertThat(stats).containsEntry("totalVendors", 0L);
        assertThat(stats).containsEntry("highRiskVendors", 0L);
    }
}
