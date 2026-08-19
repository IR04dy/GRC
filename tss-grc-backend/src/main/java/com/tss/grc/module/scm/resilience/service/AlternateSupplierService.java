package com.tss.grc.module.scm.resilience.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.scm.service.VendorReferenceResolver;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.AltCreateRequest;
import com.tss.grc.module.scm.resilience.dto.SupplierResilienceDtos.AltUpdateRequest;
import com.tss.grc.module.scm.resilience.entity.AlternateSupplier;
import com.tss.grc.module.scm.resilience.repository.AlternateSupplierRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle service for {@link AlternateSupplier}.
 * State machine: {@code DRAFT → APPROVED → ARCHIVED}.
 * DRAFT can also short-circuit to ARCHIVED for stale entries.
 */
@Service
@RequiredArgsConstructor
public class AlternateSupplierService {

    private static final Set<String> ROLES = Set.of("BACKUP_PRIMARY", "BACKUP_SECONDARY", "TERTIARY");
    private static final Set<String> OUTCOMES = Set.of("READY", "CONDITIONAL", "GAP", "NOT_VIABLE");
    private static final Set<String> CONTRACTUAL = Set.of("NONE", "MOU_SIGNED", "FRAMEWORK_ACTIVE");

    private final AlternateSupplierRepository repo;

    /** F2-246 - the single writer of this register's (vendor id, vendor name) pair. */
    private final VendorReferenceResolver vendorRefs;

    @Transactional(readOnly = true)
    public Page<AlternateSupplier> list(Pageable pageable) {
        return repo.findByOrganisationIdOrderByPrimaryVendorNameAscAlternateVendorRoleAsc(
                TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Page<AlternateSupplier> listByStatus(String status, Pageable pageable) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(
                TenantContext.getCurrentTenant(), status, pageable);
    }

    @Transactional(readOnly = true)
    public AlternateSupplier get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("AlternateSupplier " + id));
    }

    @Transactional(readOnly = true)
    public List<AlternateSupplier> byPrimaryVendor(Long primaryVendorId) {
        return repo.findByOrganisationIdAndPrimaryVendorIdOrderByAlternateVendorRoleAsc(
                TenantContext.getCurrentTenant(), primaryVendorId);
    }

    @Transactional
    public AlternateSupplier create(AltCreateRequest req) {
        if (!ROLES.contains(req.alternateVendorRole())) {
            throw new IllegalArgumentException(
                    "Unknown role '" + req.alternateVendorRole() + "'; expected " + ROLES);
        }
        if (req.contractualStatus() != null && !CONTRACTUAL.contains(req.contractualStatus())) {
            throw new IllegalArgumentException(
                    "Unknown contractualStatus '" + req.contractualStatus() + "'; expected " + CONTRACTUAL);
        }
        if (req.lastAssessedOutcome() != null && !OUTCOMES.contains(req.lastAssessedOutcome())) {
            throw new IllegalArgumentException(
                    "Unknown outcome '" + req.lastAssessedOutcome() + "'; expected " + OUTCOMES);
        }
        AlternateSupplier a = new AlternateSupplier();
        a.setOrganisationId(TenantContext.getCurrentTenant());
        a.setExternalId("ALT_" + UUID.randomUUID().toString().substring(0, 12));
        // F2-246 - the PRIMARY vendor is the one being replaced and it has an id column,
        // so it must resolve; alternateVendorName has no id column at all and stays free
        // text by design (a substitute supplier need not be registered yet).
        // The tenant is read directly rather than from the half-built row: a resolve
        // placed above the organisation stamp would pass null, the filter could never
        // match, and EVERY linked create would answer 404. Found by the live probe -
        // and the probe legs that expect a refusal cannot see it, because a refusal
        // caused by a null tenant looks exactly like a correct one.
        var vendor = vendorRefs.resolve(req.primaryVendorId(), req.primaryVendorName(),
                TenantContext.getCurrentTenant());
        a.setPrimaryVendorId(vendor.id());
        a.setPrimaryVendorName(vendor.name());
        a.setAlternateVendorName(req.alternateVendorName());
        a.setAlternateVendorCountry(req.alternateVendorCountry());
        a.setAlternateVendorRole(req.alternateVendorRole());
        a.setServiceScopeEn(req.serviceScopeEn());
        a.setServiceScopeAr(req.serviceScopeAr());
        a.setEstimatedOnboardingDays(req.estimatedOnboardingDays());
        a.setEstimatedCostSar(req.estimatedCostSar());
        a.setLastAssessedOutcome(req.lastAssessedOutcome());
        a.setContractualStatus(req.contractualStatus() != null ? req.contractualStatus() : "NONE");
        a.setNotesEn(req.notesEn());
        a.setNotesAr(req.notesAr());
        a.setStatus("DRAFT");
        return repo.save(a);
    }

    @Transactional
    public AlternateSupplier update(Long id, AltUpdateRequest req) {
        AlternateSupplier a = get(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new IllegalTransitionException(a.getStatus(), "DRAFT", Set.of("DRAFT"));
        }
        if (req.alternateVendorName() != null) a.setAlternateVendorName(req.alternateVendorName());
        if (req.alternateVendorCountry() != null) a.setAlternateVendorCountry(req.alternateVendorCountry());
        if (req.alternateVendorRole() != null && ROLES.contains(req.alternateVendorRole()))
            a.setAlternateVendorRole(req.alternateVendorRole());
        if (req.serviceScopeEn() != null) a.setServiceScopeEn(req.serviceScopeEn());
        if (req.serviceScopeAr() != null) a.setServiceScopeAr(req.serviceScopeAr());
        if (req.estimatedOnboardingDays() != null) a.setEstimatedOnboardingDays(req.estimatedOnboardingDays());
        if (req.estimatedCostSar() != null) a.setEstimatedCostSar(req.estimatedCostSar());
        if (req.lastAssessedOutcome() != null && OUTCOMES.contains(req.lastAssessedOutcome())) {
            a.setLastAssessedOutcome(req.lastAssessedOutcome());
            a.setLastAssessedAt(LocalDateTime.now());
        }
        if (req.contractualStatus() != null && CONTRACTUAL.contains(req.contractualStatus()))
            a.setContractualStatus(req.contractualStatus());
        if (req.notesEn() != null) a.setNotesEn(req.notesEn());
        if (req.notesAr() != null) a.setNotesAr(req.notesAr());
        return a;
    }

    @Transactional
    public AlternateSupplier approve(Long id, String approverName, Long approverId) {
        AlternateSupplier a = get(id);
        if (!"DRAFT".equals(a.getStatus())) {
            throw new IllegalTransitionException(a.getStatus(), "APPROVED", Set.of("DRAFT"));
        }
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApprovedBy(approverId);
        a.setApproverName(approverName);
        return a;
    }

    @Transactional
    public AlternateSupplier archive(Long id, String reason) {
        AlternateSupplier a = get(id);
        if ("ARCHIVED".equals(a.getStatus())) {
            throw new IllegalTransitionException(a.getStatus(), "ARCHIVED", Set.of("DRAFT", "APPROVED"));
        }
        a.setStatus("ARCHIVED");
        a.setArchivedAt(LocalDateTime.now());
        a.setArchiveReason(reason);
        return a;
    }
}
