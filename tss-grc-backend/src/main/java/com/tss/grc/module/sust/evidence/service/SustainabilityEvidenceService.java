package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.repository.SustainabilityEvidenceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Sustainability evidence service.
 *
 * <p>Approval lifecycle:
 * <pre>
 *   PENDING_REVIEW → APPROVED  (reviewer signed off)
 *   PENDING_REVIEW → REJECTED  (sent back with comments)
 *   APPROVED → SUPERSEDED      (replaced by a newer version)
 *   APPROVED → EXPIRED         (Rule 9 — valid_to passed)
 * </pre>
 *
 * <p>Rule 9 — {@link #sweepExpired()} marks APPROVED rows whose
 * {@code valid_to} has passed as EXPIRED. Returns the count + list of
 * expired evidence so downstream services can mark the linked
 * disclosures EVIDENCE_REQUIRED. Designed to be called by a scheduled
 * job (cron / @Scheduled) — wired separately.
 */
@Service
@RequiredArgsConstructor
public class SustainabilityEvidenceService {

    private static final Set<String> APPROVAL_STATUSES = Set.of(
        "PENDING_REVIEW", "APPROVED", "REJECTED", "EXPIRED", "SUPERSEDED");

    private static final Set<String> CONFIDENTIALITY = Set.of(
        "INTERNAL", "RESTRICTED", "CONFIDENTIAL", "PUBLIC");

    private final SustainabilityEvidenceRepository repo;

    private Long org() { return TenantContext.getCurrentTenant(); }

    public Page<SustainabilityEvidence> list(Pageable p) {
        return repo.findByOrganisationIdOrderByCreatedAtDescIdDesc(org(), p);
    }
    public Page<SustainabilityEvidence> listByModule(String moduleArea, Pageable p) {
        return repo.findByOrganisationIdAndModuleAreaOrderByCreatedAtDescIdDesc(org(), moduleArea, p);
    }
    public Page<SustainabilityEvidence> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndApprovalStatusOrderByCreatedAtDescIdDesc(org(), status, p);
    }
    public SustainabilityEvidence get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Evidence " + id));
    }

    @Transactional
    public SustainabilityEvidence create(SustainabilityEvidence req) {
        req.setId(null);
        req.setOrganisationId(org());
        if (req.getApprovalStatus() == null || req.getApprovalStatus().isBlank())
            req.setApprovalStatus("PENDING_REVIEW");
        if (req.getConfidentiality() == null || req.getConfidentiality().isBlank())
            req.setConfidentiality("INTERNAL");
        validate(req);
        return repo.save(req);
    }

    @Transactional
    public SustainabilityEvidence update(Long id, SustainabilityEvidence req) {
        SustainabilityEvidence cur = get(id);
        if ("SUPERSEDED".equals(cur.getApprovalStatus()) || "EXPIRED".equals(cur.getApprovalStatus()))
            throw new IllegalStateException(
                "Evidence is " + cur.getApprovalStatus() + " and cannot be modified. Upload a new version instead.");
        if (req.getTitleEn() != null)              cur.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null)              cur.setTitleAr(req.getTitleAr());
        if (req.getDescriptionEn() != null)        cur.setDescriptionEn(req.getDescriptionEn());
        if (req.getDescriptionAr() != null)        cur.setDescriptionAr(req.getDescriptionAr());
        if (req.getModuleArea() != null)           cur.setModuleArea(req.getModuleArea());
        if (req.getEvidenceType() != null)         cur.setEvidenceType(req.getEvidenceType());
        if (req.getFileUrl() != null)              cur.setFileUrl(req.getFileUrl());
        if (req.getFileName() != null)             cur.setFileName(req.getFileName());
        if (req.getVersion() != null)              cur.setVersion(req.getVersion());
        if (req.getReportingYear() != null)        cur.setReportingYear(req.getReportingYear());
        if (req.getValidFrom() != null)            cur.setValidFrom(req.getValidFrom());
        if (req.getValidTo() != null)              cur.setValidTo(req.getValidTo());
        if (req.getExpiryDate() != null)           cur.setExpiryDate(req.getExpiryDate());
        if (req.getConfidentiality() != null)      cur.setConfidentiality(req.getConfidentiality());
        if (req.getVerificationStatus() != null)   cur.setVerificationStatus(req.getVerificationStatus());
        if (req.getVerificationProvider() != null) cur.setVerificationProvider(req.getVerificationProvider());
        if (req.getVerificationDate() != null)     cur.setVerificationDate(req.getVerificationDate());
        if (req.getDigitalSignatureStatus() != null) cur.setDigitalSignatureStatus(req.getDigitalSignatureStatus());
        if (req.getOwnerName() != null)            cur.setOwnerName(req.getOwnerName());
        if (req.getOwnerEmail() != null)           cur.setOwnerEmail(req.getOwnerEmail());
        if (req.getNotes() != null)                cur.setNotes(req.getNotes());
        validate(cur);
        return repo.save(cur);
    }

    @Transactional
    public SustainabilityEvidence approve(Long id, String approvedBy) {
        SustainabilityEvidence cur = get(id);
        if (!"PENDING_REVIEW".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("Only PENDING_REVIEW evidence can be approved.");
        if (approvedBy == null || approvedBy.isBlank())
            throw new IllegalArgumentException("approvedBy required.");
        cur.setApprovalStatus("APPROVED");
        cur.setApprovedBy(approvedBy);
        cur.setApprovedAt(LocalDateTime.now());
        return repo.save(cur);
    }

    @Transactional
    public SustainabilityEvidence reject(Long id, String reviewerName, String reason) {
        SustainabilityEvidence cur = get(id);
        if (!"PENDING_REVIEW".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("Only PENDING_REVIEW evidence can be rejected.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Rejection reason required.");
        cur.setApprovalStatus("REJECTED");
        cur.setReviewerName(reviewerName);
        cur.setReviewedAt(LocalDateTime.now());
        cur.setRejectionReason(reason);
        return repo.save(cur);
    }

    /**
     * Supersede an evidence row by pointing a NEW row at its id. The
     * old row flips to SUPERSEDED — auditable but locked.
     */
    @Transactional
    public SustainabilityEvidence supersede(Long oldId, SustainabilityEvidence newReq) {
        SustainabilityEvidence old = get(oldId);
        if (!"APPROVED".equals(old.getApprovalStatus()))
            throw new IllegalStateException("Only APPROVED evidence can be superseded.");
        newReq.setSupersedesId(oldId);
        SustainabilityEvidence created = create(newReq);
        old.setApprovalStatus("SUPERSEDED");
        repo.save(old);
        return created;
    }

    @Transactional
    public void softDelete(Long id) {
        SustainabilityEvidence cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
    }

    // ── Rule 9 — expiry sweep ──────────────────────────────────────

    /**
     * Mark all APPROVED evidence with valid_to in the past as EXPIRED.
     * Returns the list of expired rows so callers can fan-out to the
     * linked disclosures (CMA / TCFD) and mark them EVIDENCE_REQUIRED.
     * Designed to be called by a daily scheduled job.
     */
    @Transactional
    public List<SustainabilityEvidence> sweepExpired() {
        LocalDate today = LocalDate.now();
        List<SustainabilityEvidence> due = repo.findByOrganisationIdAndApprovalStatusAndValidToBefore(
            org(), "APPROVED", today);
        for (SustainabilityEvidence e : due) {
            e.setApprovalStatus("EXPIRED");
        }
        return repo.saveAll(due);
    }

    /** Per-org aggregate for the dashboard / repository tab. */
    public Map<String, Object> aggregate() {
        // Tenant scoped at the DB layer (replaces an earlier
        // findAll().stream().filter pattern that pulled every org's rows
        // into memory before filtering — both a multi-tenant leak risk
        // and a memory bomb at scale).
        List<SustainabilityEvidence> all = repo.findByOrganisationId(org());
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        Map<String, Integer> byModule = new LinkedHashMap<>();
        for (String s : APPROVAL_STATUSES) byStatus.put(s, 0);
        LocalDate today = LocalDate.now();
        LocalDate soon = today.plusDays(30);
        int expiringSoon = 0;
        for (SustainabilityEvidence e : all) {
            byStatus.merge(e.getApprovalStatus(), 1, Integer::sum);
            if (e.getModuleArea() != null) byModule.merge(e.getModuleArea(), 1, Integer::sum);
            if ("APPROVED".equals(e.getApprovalStatus())
                && e.getValidTo() != null
                && !e.getValidTo().isBefore(today)
                && !e.getValidTo().isAfter(soon)) {
                expiringSoon++;
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("total", all.size());
        out.put("byStatus", byStatus);
        out.put("byModule", byModule);
        out.put("expiringSoon30d", expiringSoon);
        return out;
    }

    private void validate(SustainabilityEvidence e) {
        if (e.getTitleEn() == null || e.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn required");
        if (!APPROVAL_STATUSES.contains(e.getApprovalStatus()))
            throw new IllegalArgumentException("approval_status must be one of " + APPROVAL_STATUSES);
        if (!CONFIDENTIALITY.contains(e.getConfidentiality()))
            throw new IllegalArgumentException("confidentiality must be one of " + CONFIDENTIALITY);
        if (e.getValidFrom() != null && e.getValidTo() != null
            && e.getValidTo().isBefore(e.getValidFrom()))
            throw new IllegalArgumentException("valid_to must be >= valid_from");
    }
}
