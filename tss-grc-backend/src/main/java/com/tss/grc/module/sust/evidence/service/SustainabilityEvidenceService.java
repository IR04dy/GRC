package com.tss.grc.module.sust.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.sust.capa.service.CorrectiveActionService;
import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import com.tss.grc.module.sust.evidence.repository.SustainabilityEvidenceRepository;
import com.tss.grc.module.sust.settings.service.OrganisationSettingsService;
import com.tss.grc.security.GrcUserDetails;
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
    private final OrganisationSettingsService settingsService;
    private final AuditLogService audit;
    private final CorrectiveActionService capaService;

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
        // Mass-assignment guard (SEC-ESG-003): a create can never set
        // lifecycle / approval / attribution / audit columns. Force the
        // initial state and scrub any client-supplied workflow fields so a
        // row cannot be born APPROVED or carry a forged approver.
        req.setApprovalStatus("PENDING_REVIEW");
        req.setApprovedBy(null);
        req.setApprovedAt(null);
        req.setReviewerName(null);
        req.setReviewedAt(null);
        req.setRejectionReason(null);
        req.setCreatedBy(null);
        req.setUpdatedBy(null);
        if (req.getConfidentiality() == null || req.getConfidentiality().isBlank())
            req.setConfidentiality("INTERNAL");
        validate(req);
        SustainabilityEvidence saved = repo.save(req);
        audit.log("CREATE", "SustainabilityEvidence", saved.getId(), "Sustainability evidence created");
        return saved;
    }

    @Transactional
    public SustainabilityEvidence update(Long id, SustainabilityEvidence req) {
        SustainabilityEvidence cur = get(id);
        if ("SUPERSEDED".equals(cur.getApprovalStatus()) || "EXPIRED".equals(cur.getApprovalStatus()))
            throw new IllegalStateException(
                "Evidence is " + cur.getApprovalStatus() + " and cannot be modified. Upload a new version instead.");
        String previousFileUrl = cur.getFileUrl();
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
        // F2-404: the evidence FILE is the certified content. approve() applies a four-eyes / SoD
        // sign-off (approver != owner, SEC-ESG-002) on THIS document, and supersede() is the dedicated
        // route for replacing it (a fresh PENDING_REVIEW row that must be re-approved). Replacing the
        // file in place on an APPROVED row through update() left it APPROVED with the stale approver, so
        // the ESG / CMA / TCFD / ISAE 3410 assurance register kept surfacing an approved document the
        // reviewer never saw (F2-378 shape). Reset to PENDING_REVIEW and clear the stale approval so it
        // re-enters review — keyed on a real file change (Objects.equals) and scoped to APPROVED (a
        // benign metadata edit, or a file change on a not-yet-approved row, is untouched — F2-214).
        boolean forcedReview =
            !Objects.equals(previousFileUrl, cur.getFileUrl())
            && "APPROVED".equals(cur.getApprovalStatus());
        if (forcedReview) {
            cur.setApprovalStatus("PENDING_REVIEW");
            cur.setApprovedBy(null);
            cur.setApprovedAt(null);
        }
        SustainabilityEvidence saved = repo.save(cur);
        audit.log("UPDATE", "SustainabilityEvidence", saved.getId(),
            "Sustainability evidence updated"
                + (forcedReview ? " — file replaced, re-review required (APPROVED -> PENDING_REVIEW)" : ""));
        return saved;
    }

    @Transactional
    public SustainabilityEvidence approve(Long id, GrcUserDetails approver) {
        SustainabilityEvidence cur = get(id);
        if (!"PENDING_REVIEW".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("Only PENDING_REVIEW evidence can be approved.");
        if (approver == null || approver.getUsername() == null || approver.getUsername().isBlank())
            throw new IllegalArgumentException("Approval requires an authenticated approver.");
        // Approver identity is the authenticated principal — never a
        // request-body string — so approval attribution cannot be forged
        // (SEC-ESG-002).
        String approvedBy = approver.getUsername();
        // Separation of duties — the approver must not be the evidence owner.
        if (sameActor(approvedBy, cur.getOwnerName()) || sameActor(approvedBy, cur.getOwnerEmail()))
            throw new IllegalArgumentException(
                "Approver must differ from the evidence owner (separation of duties).");
        cur.setApprovalStatus("APPROVED");
        cur.setApprovedBy(approvedBy);
        cur.setApprovedAt(LocalDateTime.now());
        SustainabilityEvidence saved = repo.save(cur);
        audit.log("APPROVE", "SustainabilityEvidence", saved.getId(), "Sustainability evidence approved");
        return saved;
    }

    /** Trim-aware, case-insensitive identity compare used by the SoD check. */
    private static boolean sameActor(String a, String b) {
        return a != null && b != null && a.trim().equalsIgnoreCase(b.trim());
    }

    @Transactional
    public SustainabilityEvidence reject(Long id, GrcUserDetails reviewer, String reason) {
        SustainabilityEvidence cur = get(id);
        if (!"PENDING_REVIEW".equals(cur.getApprovalStatus()))
            throw new IllegalStateException("Only PENDING_REVIEW evidence can be rejected.");
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("Rejection reason required.");
        if (reviewer == null || reviewer.getUsername() == null || reviewer.getUsername().isBlank())
            throw new IllegalArgumentException("Rejection requires an authenticated reviewer.");
        cur.setApprovalStatus("REJECTED");
        // Reviewer attribution is the authenticated principal — not a
        // request-body string (SEC-ESG-002).
        cur.setReviewerName(reviewer.getUsername());
        cur.setReviewedAt(LocalDateTime.now());
        cur.setRejectionReason(reason);
        SustainabilityEvidence saved = repo.save(cur);
        audit.log("REJECT", "SustainabilityEvidence", saved.getId(), "Sustainability evidence rejected");
        // S-D — a rejected evidence item is a gap: idempotently open a CAPA so
        // the rejection is tracked to resubmission (mirrors the compliance F8
        // AR-EVD-GAP rule). Deduped by (EVIDENCE, id) in autoOpen.
        capaService.autoOpen(
            "EVIDENCE", String.valueOf(saved.getId()),
            "Sustainability evidence rejected — remediate & resubmit (#" + saved.getId() + ")",
            "تم رفض دليل الاستدامة — المعالجة وإعادة التقديم (#" + saved.getId() + ")",
            "Auto-opened by S-D: sustainability evidence #" + saved.getId()
                + " was rejected. Reason: " + reason,
            "HIGH");
        return saved;
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
        audit.log("SUPERSEDE", "SustainabilityEvidence", created.getId(),
                "Sustainability evidence superseded (replaces " + oldId + ")");
        return created;
    }

    @Transactional
    public void softDelete(Long id) {
        SustainabilityEvidence cur = get(id);
        cur.setIsDeleted(true);
        repo.save(cur);
        audit.log("DELETE", "SustainabilityEvidence", id, "Sustainability evidence deleted");
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
        return sweepExpiredForOrg(org());
    }

    /**
     * Org-parameterised sweep for the scheduled job (S-B). Takes the org
     * id explicitly rather than reading {@link TenantContext}, because the
     * scheduler runs off-request where the thread-local tenant is unset —
     * the same off-request landmine that bit the audit engine.
     */
    @Transactional
    public List<SustainabilityEvidence> sweepExpiredForOrg(Long orgId) {
        LocalDate today = LocalDate.now();
        List<SustainabilityEvidence> due = repo.findByOrganisationIdAndApprovalStatusAndValidToBefore(
            orgId, "APPROVED", today);
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
        // S-B — the "expiring soon" warning window is tenant-configurable
        // (default 30 days lives in the settings schema, not here).
        int windowDays = settingsService.getOrCreate().getEvidenceExpiryWarningDays();
        LocalDate soon = today.plusDays(windowDays);
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
        // Key kept as `expiringSoon30d` for frontend back-compat; the value
        // now honours the configured window, exposed alongside it.
        out.put("expiringSoon30d", expiringSoon);
        out.put("expiringSoonWindowDays", windowDays);
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
