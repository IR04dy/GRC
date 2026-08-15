package com.tss.grc.module.ndi.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.module.ndi.evidence.entity.NdiEvidenceItem;
import com.tss.grc.module.ndi.evidence.repository.NdiEvidenceItemRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit;

    public NdiEvidenceItemService(NdiEvidenceItemRepository repo,
                                  com.tss.grc.module.ndi.audit.service.NdiAuditPublisher audit) {
        this.repo = repo;
        this.audit = audit;
    }

    private Long org() { return TenantContext.getCurrentTenant(); }

    /**
     * The state evidence is filed in. Declared once and read by both the create's guard and
     * {@link #approve}'s precondition, so the state the server files a record in and the only state
     * it will approve from cannot drift apart. It agrees with {@code NdiEvidenceItem}'s own
     * {@code @PrePersist} default, which is what made this safe to enforce: the create form sends
     * no status at all, so nothing that works today starts failing.
     */
    private static final String INITIAL_STATUS = "PENDING";

    /**
     * SEC-NDI-003: the reviewer who approves/rejects evidence must be the
     * authenticated principal, never a request-body field. Mirrors the
     * SEC-NDI-002 pattern in {@code NdiAuditService.record()}.
     */
    private static String currentUsername() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a == null ? null : a.getName();
    }

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

    /**
     * F2-218: a create files evidence for review. It does not review it.
     *
     * <p>Before this, the three fields {@link #approve} and {@link #reject} stamp — the decision
     * ({@code status}), who made it ({@code reviewerName}) and when ({@code reviewedAt}) — were all
     * bound from the request body. Probed live 2026-08-15 as {@code ranalyst}, who holds
     * {@code NDI:WRITE} and <b>not</b> {@code NDI:APPROVE}: {@code POST /{id}/approve} answered
     * <b>403</b>, and this method answered <b>200</b> to {@code status=ACTIVE} with
     * {@code reviewerName="Nobody Who Reviewed This"} and {@code reviewedAt=2001-01-01}, on an item
     * whose {@code ownerName} was the caller. That is the SEC-NDI-003 maker/checker control —
     * reviewer resolved from the principal, reviewer != owner — bypassed in full, with no audit
     * transition row, by the unguarded route beside the guarded one.
     */
    public NdiEvidenceItem create(NdiEvidenceItem body) {
        if (body.getTitleEn() == null || body.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.getEvidenceType() == null || body.getEvidenceType().isBlank())
            throw new IllegalArgumentException("evidenceType is required");
        body.setId(null);
        body.setOrganisationId(org());
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_STATUS, "an evidence item", "POST /{id}/approve or /reject"));
        // The attribution and its timestamp belong to whoever performs the review. A create has
        // been reviewed by nobody, so any value here is a claim about a review that did not happen.
        body.setReviewerName(null);
        body.setReviewedAt(null);
        body.setRejectionReason(null);
        // Rule 6 still runs on every write path, as the class javadoc says — but it is a no-op here
        // now, because it only flips ACTIVE -> EXPIRED and a created item is PENDING. The flip that
        // matters happens in approve(), which sets ACTIVE and then applies the rule, so evidence
        // approved with an expiry date already past is EXPIRED immediately. Left in place so the
        // stated invariant holds for the next state this method might legitimately derive; a
        // comment claiming it expires anything on create would be describing a branch that cannot
        // run, which is worse than no comment (F2-184).
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
        // SEC-NDI-003: reviewerName is the attribution field for the maker/checker
        // SoD control — it is stamped server-side from the authenticated principal
        // at approve/reject, never bound from the request body.
        //
        // ⚠⚠ F2-218: that comment was correct and it covered ONE of the three fields the same
        // control owns. `reviewerName` is the attribution, `status` is the DECISION, and
        // `reviewedAt` is when it was made — and the two lines below the comment copied both of
        // the others straight from the request. Probed live as `ranalyst` (NDI:WRITE, no
        // NDI:APPROVE): POST /{id}/approve answered 403 and PUT /{id} with {"status":"ACTIVE"}
        // performed exactly that transition, 200, with no reviewer, no SoD check and no audit row.
        // Removing the name while leaving the decision meant a WRITE-only caller could approve
        // evidence; the record simply did not say who. Approving, rejecting and superseding are
        // POST /{id}/approve, /reject and /supersede-by/{newId}.
        if (body.getEffectiveDate()   != null) e.setEffectiveDate(body.getEffectiveDate());
        if (body.getExpiryDate()      != null) e.setExpiryDate(body.getExpiryDate());
        if (body.getTags()            != null) e.setTags(body.getTags());
        if (body.getNotes()           != null) e.setNotes(body.getNotes());
        applyExpiryRule(e);
        return repo.save(e);
    }

    public NdiEvidenceItem approve(Long id) {
        NdiEvidenceItem e = get(id);
        if (!"PENDING".equals(e.getStatus()))
            throw new IllegalStateException("Only PENDING evidence can be approved (was " + e.getStatus() + ")");
        // SEC-NDI-003: the reviewer is the authenticated principal, not a body
        // field; SoD requires the reviewer to differ from the evidence owner.
        String reviewerName = currentUsername();
        if (reviewerName != null && reviewerName.equalsIgnoreCase(e.getOwnerName())) {
            throw new IllegalStateException(
                "Segregation of duties: the evidence owner cannot also approve it.");
        }
        e.setStatus("ACTIVE");
        e.setReviewerName(reviewerName);
        e.setReviewedAt(LocalDate.now());
        applyExpiryRule(e);
        NdiEvidenceItem saved = repo.save(e);
        audit.publishTransition("EVIDENCE", saved.getId(), saved.getEvidenceCode(),
            saved.getCycleId(), "PENDING", saved.getStatus(), "Approved by " + reviewerName);
        return saved;
    }

    public NdiEvidenceItem reject(Long id, String reason) {
        NdiEvidenceItem e = get(id);
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("rejection reason is required");
        // SEC-NDI-003: the reviewer is the authenticated principal, not a body field.
        String reviewerName = currentUsername();
        String from = e.getStatus();
        e.setStatus("REJECTED");
        e.setRejectionReason(reason);
        e.setReviewerName(reviewerName);
        e.setReviewedAt(LocalDate.now());
        NdiEvidenceItem saved = repo.save(e);
        audit.publish("EVIDENCE", saved.getId(), saved.getEvidenceCode(),
            saved.getCycleId(), "REJECT", "WARN", "Rejected: " + reason);
        // also stamp the transition
        audit.publishTransition("EVIDENCE", saved.getId(), saved.getEvidenceCode(),
            saved.getCycleId(), from, "REJECTED", "Rejected by " + reviewerName);
        return saved;
    }

    public NdiEvidenceItem supersede(Long oldId, Long newId) {
        NdiEvidenceItem older = get(oldId);
        NdiEvidenceItem newer = get(newId);
        String oldFrom = older.getStatus();
        older.setStatus("SUPERSEDED");
        newer.setSupersedesId(oldId);
        repo.save(older);
        NdiEvidenceItem saved = repo.save(newer);
        audit.publishTransition("EVIDENCE", older.getId(), older.getEvidenceCode(),
            older.getCycleId(), oldFrom, "SUPERSEDED",
            "Superseded by " + (newer.getEvidenceCode() == null ? "#" + newId : newer.getEvidenceCode()));
        return saved;
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
            audit.publishTransition("EVIDENCE", e.getId(), e.getEvidenceCode(),
                e.getCycleId(), "ACTIVE", "EXPIRED", "Auto-flipped by expiry check (expired " + e.getExpiryDate() + ")");
        }
        if (!ids.isEmpty()) {
            audit.publish("EVIDENCE", null, null, null,
                "CHECK_EXPIRY", "INFO", "Expiry check flipped " + ids.size() + " evidence row(s) to EXPIRED");
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
