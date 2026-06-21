package com.tss.grc.module.tprm.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.tprm.entity.Vendor;
import com.tss.grc.module.tprm.entity.VendorAssessment;
import com.tss.grc.module.tprm.repository.VendorAssessmentRepository;
import com.tss.grc.module.tprm.repository.VendorRepository;
import com.tss.grc.platform.realtime.BroadcastHelper;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TprmService {

    private final VendorRepository vendorRepo;
    private final VendorAssessmentRepository assessmentRepo;

    @Autowired(required = false)
    private BroadcastHelper bcast;

    // ── Vendors ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<Vendor> listVendors(Pageable pageable) {
        return vendorRepo.findByOrganisationId(TenantContext.getCurrentTenant(), pageable);
    }

    @Transactional(readOnly = true)
    public Vendor getVendor(Long id) {
        return vendorRepo.findById(id)
            .filter(v -> v.getOrganisationId().equals(TenantContext.getCurrentTenant()))
            .orElseThrow(() -> new EntityNotFoundException("Vendor not found: " + id));
    }

    @Transactional
    public Vendor createVendor(Vendor req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        Vendor saved = vendorRepo.save(req);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vendor", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public Vendor updateVendor(Long id, Vendor req) {
        Vendor existing = getVendor(id);
        existing.setVendorName(req.getVendorName());
        existing.setVendorNameAr(req.getVendorNameAr());
        existing.setVendorType(req.getVendorType());
        existing.setCountry(req.getCountry());
        existing.setContactName(req.getContactName());
        existing.setContactEmail(req.getContactEmail());
        existing.setRiskRating(req.getRiskRating());
        existing.setStatus(req.getStatus());
        existing.setContractStartDate(req.getContractStartDate());
        existing.setContractEndDate(req.getContractEndDate());
        existing.setDescription(req.getDescription());
        existing.setUpdatedAt(LocalDateTime.now());
        Vendor saved = vendorRepo.save(existing);
        if (bcast != null) bcast.entity(saved.getOrganisationId(), "vendor", saved.getId(), saved);
        return saved;
    }

    @Transactional
    public void deleteVendor(Long id) {
        Vendor v = getVendor(id);
        v.setIsDeleted(true);
        vendorRepo.save(v);
        if (bcast != null) bcast.entity(v.getOrganisationId(), "vendor", v.getId(), java.util.Map.of("id", v.getId(), "deleted", true));
    }

    // ── Assessments ───────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<VendorAssessment> listAssessments(Long vendorId) {
        if (vendorId != null) {
            getVendor(vendorId); // verify org ownership
            return assessmentRepo.findByVendorId(vendorId, Pageable.unpaged()).getContent();
        }
        List<Long> vendorIds = vendorRepo.findByOrganisationId(TenantContext.getCurrentTenant(), Pageable.unpaged())
            .map(Vendor::getId).getContent();
        return assessmentRepo.findByVendorIdIn(vendorIds);
    }

    @Transactional
    public VendorAssessment createAssessment(VendorAssessment req) {
        getVendor(req.getVendorId()); // verify org ownership
        return assessmentRepo.save(req);
    }

    @Transactional
    public VendorAssessment updateAssessment(Long id, VendorAssessment req) {
        VendorAssessment existing = assessmentRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        // Tenant guard (SEC-TPRM-001): VendorAssessment has no organisation_id, so
        // ownership is enforced via the parent vendor — getVendor throws if the
        // vendor is not in the caller's org, blocking cross-tenant tampering.
        getVendor(existing.getVendorId());
        existing.setAssessmentDate(req.getAssessmentDate());
        existing.setAssessor(req.getAssessor());
        existing.setRiskScore(req.getRiskScore());
        existing.setRiskRating(req.getRiskRating());
        existing.setStatus(req.getStatus());
        existing.setFindings(req.getFindings());
        existing.setNextReviewDate(req.getNextReviewDate());
        return assessmentRepo.save(existing);
    }

    @Transactional
    public void deleteAssessment(Long id) {
        VendorAssessment a = assessmentRepo.findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Assessment not found: " + id));
        // Tenant guard (SEC-TPRM-001): block cross-tenant soft-delete via the parent vendor.
        getVendor(a.getVendorId());
        a.setIsDeleted(true);
        assessmentRepo.save(a);
    }

    // ── Stats ─────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        return Map.of(
            "totalVendors",    vendorRepo.findByOrganisationId(orgId, Pageable.unpaged()).getTotalElements(),
            "highRiskVendors", vendorRepo.countByOrganisationIdAndRiskRating(orgId, "HIGH")
                             + vendorRepo.countByOrganisationIdAndRiskRating(orgId, "CRITICAL")
        );
    }
}
