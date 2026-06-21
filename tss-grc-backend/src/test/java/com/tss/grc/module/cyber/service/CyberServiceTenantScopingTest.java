package com.tss.grc.module.cyber.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.cyber.entity.CyberAsset;
import com.tss.grc.module.cyber.entity.CyberControl;
import com.tss.grc.module.cyber.entity.CyberIncident;
import com.tss.grc.module.cyber.repository.ControlAssessmentRepository;
import com.tss.grc.module.cyber.repository.CyberActionPlanRepository;
import com.tss.grc.module.cyber.repository.CyberAssetRepository;
import com.tss.grc.module.cyber.repository.CyberControlRepository;
import com.tss.grc.module.cyber.repository.CyberEvidenceRepository;
import com.tss.grc.module.cyber.repository.CyberFrameworkRepository;
import com.tss.grc.module.cyber.repository.CyberIncidentRepository;
import com.tss.grc.module.cyber.repository.CyberSubControlRepository;
import com.tss.grc.module.cyber.repository.VulnExceptionRepository;
import com.tss.grc.module.cyber.repository.VulnerabilityRepository;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * SEC-CYB-001 (Critical — cross-tenant IDOR) regression lock.
 *
 * <p>{@link CyberService} previously read/updated/deleted single entities via
 * Spring Data's unscoped {@code findById(id)}, so a caller authenticated for
 * org A could touch org B's rows by guessing ids. The fix routes every
 * single-id operation through {@code findByIdAndOrganisationId(id, orgId)} with
 * the org id taken from {@link TenantContext}.
 *
 * <p>These tests run with the caller's tenant set to {@code ORG_A}. They stub
 * the scoped finder to return {@link Optional#empty()} — i.e. the row exists
 * only in another tenant (or not at all). The contract is:
 * <ul>
 *   <li>the get / update / delete path throws {@link EntityNotFoundException}
 *       (mapped to 404 — no leak of whether the id exists elsewhere); and</li>
 *   <li>no {@code save(...)} is performed, so the foreign row is never
 *       modified or soft-deleted.</li>
 * </ul>
 *
 * <p>Pure Mockito — no Spring context, no DB. Mocking conventions follow
 * {@code ApprovalChainInstanceServiceTest} / {@code VulnerabilityServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class CyberServiceTenantScopingTest {

    /** The authenticated caller's tenant. */
    private static final Long ORG_A = 1L;
    /** A row id that belongs to a different tenant (org B). */
    private static final Long FOREIGN_ID = 999L;

    @Mock private CyberFrameworkRepository frameworkRepo;
    @Mock private CyberAssetRepository assetRepo;
    @Mock private CyberControlRepository controlRepo;
    @Mock private CyberSubControlRepository subControlRepo;
    @Mock private ControlAssessmentRepository assessmentRepo;
    @Mock private VulnerabilityRepository vulnerabilityRepo;
    @Mock private VulnExceptionRepository exceptionRepo;
    @Mock private CyberIncidentRepository incidentRepo;
    @Mock private CyberEvidenceRepository evidenceRepo;
    @Mock private CyberActionPlanRepository actionPlanRepo;

    @InjectMocks private CyberService service;

    @BeforeEach void setUp()    { TenantContext.setCurrentTenant(ORG_A); }
    @AfterEach  void tearDown() { TenantContext.clear(); }

    // ── GET path ───────────────────────────────────────────────────────────

    @Test
    void getControl_crossTenantId_throwsAndScopesByTenant() {
        // Row exists only in org B → scoped lookup with ORG_A returns empty.
        when(controlRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getControl(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);
    }

    // ── UPDATE path ──────────────────────────────────────────────────────────

    @Test
    void updateControl_crossTenantId_throwsAndDoesNotSave() {
        when(controlRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        CyberControl req = new CyberControl();
        req.setTitleEn("Attacker-supplied update");

        assertThatThrownBy(() -> service.updateControl(FOREIGN_ID, req))
                .isInstanceOf(EntityNotFoundException.class);

        // The foreign row must never be merged/overwritten.
        verify(controlRepo, never()).save(any(CyberControl.class));
    }

    @Test
    void updateAsset_crossTenantId_throwsAndDoesNotSave() {
        // CyberAsset uses an inline scoped lookup (no shared getter helper),
        // so it is exercised independently of the getControl path.
        when(assetRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        CyberAsset req = new CyberAsset();

        assertThatThrownBy(() -> service.updateAsset(FOREIGN_ID, req))
                .isInstanceOf(EntityNotFoundException.class);

        verify(assetRepo, never()).save(any(CyberAsset.class));
    }

    // ── DELETE path ──────────────────────────────────────────────────────────

    @Test
    void deleteIncident_crossTenantId_throwsAndDoesNotSoftDelete() {
        when(incidentRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteIncident(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);

        // Soft delete is a save() of is_deleted=true — must not happen for a
        // foreign row.
        verify(incidentRepo, never()).save(any(CyberIncident.class));
    }

    @Test
    void deleteAsset_crossTenantId_throwsAndDoesNotSoftDelete() {
        when(assetRepo.findByIdAndOrganisationId(FOREIGN_ID, ORG_A)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteAsset(FOREIGN_ID))
                .isInstanceOf(EntityNotFoundException.class);

        verify(assetRepo, never()).save(any(CyberAsset.class));
    }
}
