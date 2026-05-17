package com.tss.grc.module.ndi.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

/**
 * NDI Evidence Item service — slice 7.
 *
 * <p>Owns the CRUD and lifecycle of evidence artefacts feeding the NDI
 * submission package. Rule 6 (expiry auto-flip) is applied in every
 * write path so a row never persists in an inconsistent state.
 */
@Service
@Transactional
public class NdiEvidenceItemService {

    private final NdiEvidenceItemRepository repo;

    public NdiEvidenceItemService(NdiEvidenceItemRepository repo) {
        this.repo = repo;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    public record EvidenceAggregate(
        long total,
        int activeCount,
        int pendingCount,
        int expiredCount,
        int rejectedCount,
        int supersededCount,
        int expiringSoonCount,
        Map<String, Long> byType,
        Map<String, Long> byClassification
    ) {}

    @Transactional(readOnly = true)
    public Page<NdiEvidenceItem> list(Long cycleId, String type, String status, Pageable pageable) {
        if (cycleId != null) return repo.findByOrganisationIdAndCycleIdOrderByCreatedAtDesc(org(), cycleId, pageable);
        if (type != null)    return repo.findByOrganisationIdAndEvidenceTypeOrderByCreatedAtDesc(org(), type, pageable);
        if (status != null)  return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(org(), status, pageable);
        return repo.findByOrganisationIdOrderByCreatedAtDesc(org(), pageable);
    }

    @Transactional(readOnly = true)
    public NdiEvidenceItem get(Long id) {
        return repo.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
    }

    public NdiEvidenceItem create(NdiEvidenceItem body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getEvidenceType() == null || body.getEvidenceType().isBlank())
            throw new IllegalArgumentException("evidenceType is required");
        body.setId(null);
        body.setOrganisationId(org());
        applyExpiryRule(body);
        return repo.save(body);
    }

    public NdiEvidenceItem update(Long id, NdiEvidenceItem body) {
        NdiEvidenceItem e = get(id);
        if (body.getEvidenceCode()    != null) e.setEvidenceCode(body.getEvidenceCode());
        if (body.getCycleId()         != null) e.setCycleId(body.getCycleId());
        if (body.getTitleEn()         != null) e.setTitleEn(body.getTitleEn());
        if (body.getTitleAr()         != null) e.setTitleAr(body.getTitleAr());
        if (body.getDescriptionEn()   != null) e.setDescriptionEn(body.getDescriptionEn());
        if (body.getDescriptionAr()   != null) e.setDescriptionAr(body.getDescriptionAr());
        if (body.getEvidenceType()    != null) e.setEvidenceType(body.getEvidenceType());
        if (body.getClassification()  != null) e.setClassification(body.getClassification());
        if (body.getFileRef()         != null) e.setFileRef(body.getFileRef());
        if (body.getMimeType()        != null) e.setMimeType(body.getMimeType());
        if (body.getFileSizeBytes()   != null) e.setFileSizeBytes(body.getFileSizeBytes());
        if (body.getSourceSystem()    != null) e.setSourceSystem(body.getSourceSystem());
        if (body.getOwnerName()       != null) e.setOwnerName(body.getOwnerName());
        if (body.getOwnerEmail()      != null) e.setOwnerEmail(body.getOwnerEmail());
        if (body.getReviewerName()    != null) e.setReviewerName(body.getReviewerName());
        if (body.getEffectiveDate()   != null) e.setEffectiveDate(body.getEffectiveDate());
        if (body.getExpiryDate()      != null) e.setExpiryDate(body.getExpiryDate());
        if (body.getReviewedAt()      != null) e.setReviewedAt(body.getReviewedAt());
        if (body.getStatus()          != null) e.setStatus(body.getStatus());
        if (body.getTags()            != null) e.setTags(body.getTags());
        if (body.getNotes()           != null) e.setNotes(body.getNotes());
        applyExpiryRule(e);
        return repo.save(e);
    }

    public NdiEvidenceItem approve(Long id, String reviewerName) {
        NdiEvidenceItem e = get(id);
        if (!"PENDING".equals(e.getStatus()))
            throw new IllegalStateException("Only PENDING evidence can be approved (was " + e.getStatus() + ")");
        e.setStatus("ACTIVE");
        e.setReviewerName(reviewerName);
        e.setReviewedAt(LocalDate.now());
        applyExpiryRule(e);
        return repo.save(e);
    }

    public NdiEvidenceItem reject(Long id, String reason, String reviewerName) {
        NdiEvidenceItem e = get(id);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("rejection reason is required");
        e.setStatus("REJECTED");
        e.setRejectionReason(reason);
        e.setReviewerName(reviewerName);
        e.setReviewedAt(LocalDate.now());
        return repo.save(e);
    }

    public NdiEvidenceItem supersede(Long oldId, Long newId) {
        NdiEvidenceItem older = get(oldId);
        NdiEvidenceItem newer = get(newId);
        older.setStatus("SUPERSEDED");
        newer.setSupersedesId(oldId);
        repo.save(older);
        return repo.save(newer);
    }

    public void delete(Long id) {
        NdiEvidenceItem e = get(id);
        e.setIsDeleted(true);
        repo.save(e);
    }

    /** Mass-scan ACTIVE evidence and flip past-expiry rows to EXPIRED. Returns flipped IDs. */
    public List<Long> runExpiryCheck() {
        LocalDate today = LocalDate.now();
        List<NdiEvidenceItem> expired = repo.findExpiredActive(org(), today);
        List<Long> ids = new ArrayList<>();
        for (NdiEvidenceItem e : expired) {
            e.setStatus("EXPIRED");
            repo.save(e);
            ids.add(e.getId());
        }
        return ids;
    }

    @Transactional(readOnly = true)
    public EvidenceAggregate aggregate() {
        Long orgId = org();
        LocalDate today = LocalDate.now();
        LocalDate horizon = today.plusDays(60);

        Page<NdiEvidenceItem> page = repo.findByOrganisationIdOrderByCreatedAtDesc(orgId, Pageable.unpaged());
        Map<String, Long> byType = new TreeMap<>();
        Map<String, Long> byClass = new TreeMap<>();
        int active = 0, pending = 0, expired = 0, rejected = 0, superseded = 0;
        for (NdiEvidenceItem e : page) {
            byType.merge(e.getEvidenceType(), 1L, Long::sum);
            byClass.merge(e.getClassification(), 1L, Long::sum);
            switch (e.getStatus()) {
                case "ACTIVE"     -> active++;
                case "PENDING"    -> pending++;
                case "EXPIRED"    -> expired++;
                case "REJECTED"   -> rejected++;
                case "SUPERSEDED" -> superseded++;
                default -> {}
            }
        }
        int expiringSoon = repo.findExpiringSoon(orgId, today, horizon).size();
        return new EvidenceAggregate(
            page.getTotalElements(), active, pending, expired, rejected, superseded,
            expiringSoon, byType, byClass);
    }

    @Transactional(readOnly = true)
    public List<NdiEvidenceItem> expiringSoon(int days) {
        LocalDate today = LocalDate.now();
        return repo.findExpiringSoon(org(), today, today.plusDays(days));
    }

    /** Apply Rule 6 — expiry auto-flip — to a single row. */
    private void applyExpiryRule(NdiEvidenceItem e) {
        if (e.getStatus() == null) return;
        if (!"ACTIVE".equals(e.getStatus())) return;
        if (e.getExpiryDate() != null && e.getExpiryDate().isBefore(LocalDate.now())) {
            e.setStatus("EXPIRED");
        }
    }
}
