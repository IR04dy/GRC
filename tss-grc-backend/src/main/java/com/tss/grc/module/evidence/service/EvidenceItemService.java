package com.tss.grc.module.evidence.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.*;
import com.tss.grc.module.evidence.entity.*;
import com.tss.grc.module.evidence.repository.*;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvidenceItemService {

    private final EvidenceItemRepository itemRepository;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;
    private final EvidenceLinkRepository linkRepository;
    private final EvidenceRequestRepository requestRepository;
    private final UserRepository userRepository;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;
    private final EvidenceStorageService storage;

    // ── Item lifecycle state machine ──────────────────────────────────────────
    static final Set<String> ITEM_STATUSES =
        Set.of("DRAFT", "PENDING_REVIEW", "APPROVED", "REJECTED", "NEEDS_REVISION", "EXPIRED");

    private static final Map<String, Set<String>> ITEM_TRANSITIONS = Map.of(
        "DRAFT",          Set.of("PENDING_REVIEW"),
        "PENDING_REVIEW", Set.of("APPROVED", "REJECTED", "NEEDS_REVISION"),
        "NEEDS_REVISION", Set.of("PENDING_REVIEW", "DRAFT"),
        "REJECTED",       Set.of("PENDING_REVIEW", "DRAFT"),
        "APPROVED",       Set.of("EXPIRED", "PENDING_REVIEW"),
        "EXPIRED",        Set.of("PENDING_REVIEW", "DRAFT")
    );

    // ── F8 payload + emit ─────────────────────────────────────────────────────
    //
    // Evidence is the heartbeat of compliance — every framework control
    // ultimately resolves to one or more evidence items proving it's in
    // place. State changes drive downstream attestation/audit-pack
    // workflows. Events:
    //
    //   EVIDENCE_CREATED       — new item (DRAFT default)
    //   EVIDENCE_SUBMITTED     — DRAFT → PENDING_REVIEW
    //   EVIDENCE_APPROVED      — PENDING_REVIEW → APPROVED
    //   EVIDENCE_REJECTED      — PENDING_REVIEW → REJECTED
    //   EVIDENCE_TRANSITIONED  — any other status change (catch-all)
    //   EVIDENCE_DELETED       — soft-delete

    private Map<String, Object> payloadFor(EvidenceItem e, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("evidenceId",     nullSafeStr(e.getId()));
        m.put("externalId",     nullSafeStr(e.getExternalId()));
        m.put("status",         nullSafeStr(e.getStatus()));
        m.put("previousStatus", nullSafeStr(previousStatus));
        m.put("evidenceType",   nullSafeStr(e.getEvidenceType()));
        m.put("sourceModule",   nullSafeStr(e.getSourceModule()));
        m.put("subControlId",   nullSafeStr(e.getSubControlId()));
        m.put("ownerId",        nullSafeStr(e.getOwnerId()));
        m.put("validFrom",      nullSafeStr(e.getValidFrom()));
        m.put("validTo",        nullSafeStr(e.getValidTo()));
        return m;
    }

    /** "" for null so the equality matcher cannot NPE. */
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    /** "<not provided>" for null in human-readable audit messages. */
    private static String nullSafe(Object o) { return o == null ? "<not provided>" : o.toString(); }

    /** Current authenticated user's id, or null if unavailable (e.g. system context). */
    private Long currentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof GrcUserDetails u) {
            return u.getUserId();
        }
        return null;
    }

    private void emit(String trigger, EvidenceItem e, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(), trigger, "EvidenceItem", e.getId(),
            "evidence:" + e.getId(), payloadFor(e, previousStatus)));
    }

    @Transactional(readOnly = true)
    public Page<EvidenceItemResponse> list(String status, String type, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<EvidenceItem> page = (status != null || type != null)
                ? itemRepository.findByOrgFiltered(orgId, status, type, pageable)
                : itemRepository.findByOrganisationId(orgId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EvidenceItemResponse get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        return toResponse(item);
    }

    @Transactional(readOnly = true)
    public List<EvidenceItemResponse> listExpired() {
        Long orgId = TenantContext.getCurrentTenant();
        return itemRepository.findExpired(orgId, LocalDate.now())
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<EvidenceItemResponse> listBySubControl(Long subControlId) {
        Long orgId = TenantContext.getCurrentTenant();
        return itemRepository.findBySubControl(orgId, subControlId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public EvidenceDashboardStats getStats() {
        Long orgId = TenantContext.getCurrentTenant();
        long total    = itemRepository.countByOrganisationId(orgId);
        long approved = itemRepository.countByOrganisationIdAndStatus(orgId, "APPROVED");
        long pending  = itemRepository.countByOrganisationIdAndStatus(orgId, "PENDING_REVIEW");
        long draft    = itemRepository.countByOrganisationIdAndStatus(orgId, "DRAFT");
        long rejected = itemRepository.countByOrganisationIdAndStatus(orgId, "REJECTED");
        long expired  = itemRepository.findExpired(orgId, LocalDate.now()).size();
        long frameworks = frameworkRepository.findByIsActiveTrue().size();
        return new EvidenceDashboardStats(total, approved, pending, draft, rejected, expired,
                0L, 0L, 0L, frameworks);
    }

    @Transactional
    public EvidenceItemResponse create(EvidenceItemCreateRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = new EvidenceItem();
        item.setOrganisationId(orgId);
        applyRequest(item, req);
        item = itemRepository.save(item);
        audit.log("CREATE", "EvidenceItem", item.getId(),
            "Evidence created: " + nullSafe(item.getTitleEn())
                + " (" + nullSafe(item.getEvidenceType()) + ", " + nullSafe(item.getStatus()) + ")");
        emit("EVIDENCE_CREATED", item, null);
        return toResponse(item);
    }

    @Transactional
    public EvidenceItemResponse update(Long id, EvidenceItemCreateRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        applyRequest(item, req);
        item = itemRepository.save(item);
        return toResponse(item);
    }

    @Transactional
    public EvidenceItemResponse submitForReview(Long id) {
        return transitionStatus(id, "PENDING_REVIEW");
    }

    @Transactional
    public EvidenceItemResponse approve(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        // Segregation of duties: the person who created/submitted a piece of
        // evidence must not be the one who approves it. A different reviewer
        // has to sign off. (ECC 2-3 / ISO 27001 A.5.3 / SAMA — four-eyes.)
        Long currentUser = currentUserId();
        if (currentUser != null && currentUser.equals(item.getCreatedBy())) {
            throw new AccessDeniedException(
                "Segregation of duties: you cannot approve evidence you submitted; a different reviewer must approve it.");
        }
        return transitionStatus(id, "APPROVED");
    }

    @Transactional
    public EvidenceItemResponse reject(Long id) {
        return transitionStatus(id, "REJECTED");
    }

    @Transactional
    public EvidenceItemResponse requestRevision(Long id) {
        return transitionStatus(id, "NEEDS_REVISION");
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        item.setIsDeleted(true);
        itemRepository.save(item);
        audit.log("DELETE", "EvidenceItem", item.getId(),
            "Evidence soft-deleted: " + nullSafe(item.getTitleEn()));
        emit("EVIDENCE_DELETED", item, null);
    }

    /**
     * System-level nightly sweep (runs with no tenant context). Flips
     * APPROVED items whose validTo has passed to EXPIRED, emits
     * EVIDENCE_EXPIRED, and re-opens the originating request so the gap
     * re-surfaces in the coverage rollup. Returns the count expired.
     * Driven by {@link EvidenceExpiryScheduler}.
     */
    @Transactional
    public int expireStaleEvidence() {
        List<EvidenceItem> stale = itemRepository.findApprovedExpiredAllOrgs(LocalDate.now());
        int expired = 0;
        for (EvidenceItem item : stale) {
            TenantContext.setCurrentTenant(item.getOrganisationId());
            try {
                String previousStatus = item.getStatus();
                item.setStatus("EXPIRED");
                itemRepository.save(item);
                audit.log("TRANSITION", "EvidenceItem", item.getId(),
                    "Auto-expired: " + previousStatus + " → EXPIRED (validTo " + item.getValidTo() + ")");
                emit("EVIDENCE_EXPIRED", item, previousStatus);
                if (item.getRequestId() != null) {
                    requestRepository.findById(item.getRequestId()).ifPresent(r -> {
                        if (!"CLOSED".equals(r.getStatus())) {
                            r.setStatus("OPEN");
                            r.setFulfilledAt(null);
                            r.setFulfilledById(null);
                            requestRepository.save(r);
                        }
                    });
                }
                expired++;
            } finally {
                TenantContext.clear();
            }
        }
        return expired;
    }

    /**
     * Attach a binary document to an evidence item. Stores the bytes via
     * {@link EvidenceStorageService} (size + type validated, path-traversal
     * safe), records the SHA-256 into the chain-of-custody hash, and stamps
     * file metadata + collectedAt. Backs POST /items/{id}/file (multipart).
     */
    @Transactional
    public EvidenceItemResponse attachFile(Long id, MultipartFile file) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        EvidenceStorageService.StoredFile stored = storage.store(orgId, file);
        item.setFileName(stored.originalName());
        item.setFilePath(stored.relativePath());
        item.setFileSize(stored.size());
        item.setMimeType(stored.mimeType());
        item.setEvidenceHash(stored.sha256());
        item.setCollectedAt(LocalDateTime.now());
        item = itemRepository.save(item);
        audit.log("UPLOAD", "EvidenceItem", item.getId(),
            "File attached: " + nullSafe(stored.originalName())
                + " (" + stored.size() + " bytes, sha256 " + stored.sha256().substring(0, 12) + "…)");
        emit("EVIDENCE_FILE_ATTACHED", item, item.getStatus());
        return toResponse(item);
    }

    @Transactional
    public void addLink(EvidenceLinkRequest req) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceLink link = new EvidenceLink();
        link.setOrganisationId(orgId);
        link.setEvidenceId(req.evidenceId());
        link.setLinkedModule(req.linkedModule());
        link.setLinkedEntityType(req.linkedEntityType());
        link.setLinkedEntityId(req.linkedEntityId());
        link.setNotes(req.notes());
        linkRepository.save(link);
    }

    private EvidenceItemResponse transitionStatus(Long id, String newStatus) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceItem item = itemRepository.findById(id)
                .filter(e -> e.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence not found: " + id));
        String previousStatus = item.getStatus();
        validateTransition(previousStatus, newStatus);
        item.setStatus(newStatus);
        item = itemRepository.save(item);
        audit.log("TRANSITION", "EvidenceItem", item.getId(),
            "Status " + nullSafe(previousStatus) + " → " + newStatus);
        // Pick the most specific trigger for the destination state so
        // downstream rules can target the lifecycle stage they care
        // about (approve fans out attestation campaigns; reject pings
        // the submitter for a revised package; submit-for-review pings
        // the reviewer queue).
        String trigger = switch (newStatus) {
            case "PENDING_REVIEW" -> "EVIDENCE_SUBMITTED";
            case "APPROVED"       -> "EVIDENCE_APPROVED";
            case "REJECTED"       -> "EVIDENCE_REJECTED";
            case "NEEDS_REVISION" -> "EVIDENCE_REVISION_REQUESTED";
            case "EXPIRED"        -> "EVIDENCE_EXPIRED";
            default               -> "EVIDENCE_TRANSITIONED";
        };
        emit(trigger, item, previousStatus);
        // Close the collect→fulfil loop: an APPROVED item that was raised
        // against a request stamps that request as fulfilled.
        if ("APPROVED".equals(newStatus) && item.getRequestId() != null) {
            fulfilRequest(item, orgId);
        }
        return toResponse(item);
    }

    /**
     * State-machine guard. Unknown destination statuses are a client error
     * (400 via IllegalArgumentException); known-but-illegal moves are a
     * conflict (409 via IllegalTransitionException). A no-op same-status
     * transition is allowed so callers stay idempotent.
     */
    private void validateTransition(String from, String to) {
        if (!ITEM_STATUSES.contains(to)) {
            throw new IllegalArgumentException(
                "Unknown evidence status: '" + to + "'. Valid statuses: " + ITEM_STATUSES);
        }
        if (to.equals(from)) return;
        Set<String> allowed = ITEM_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    /** Stamp the originating request as fulfilled when its evidence is approved. */
    private void fulfilRequest(EvidenceItem item, Long orgId) {
        requestRepository.findById(item.getRequestId())
            .filter(r -> r.getOrganisationId().equals(orgId))
            .ifPresent(r -> {
                r.setFulfilledById(item.getOwnerId());
                r.setFulfilledAt(LocalDateTime.now());
                if (!"CLOSED".equals(r.getStatus()) && !"APPROVED".equals(r.getStatus())) {
                    r.setStatus("SUBMITTED");
                }
                requestRepository.save(r);
            });
    }

    private void applyRequest(EvidenceItem item, EvidenceItemCreateRequest req) {
        item.setTitleEn(req.titleEn());
        item.setTitleAr(req.titleAr());
        item.setDescriptionEn(req.descriptionEn());
        item.setSubControlId(req.subControlId());
        item.setRequestId(req.requestId());
        item.setEvidenceType(req.evidenceType());
        item.setSourceModule(req.sourceModule());
        item.setSourceEntityId(req.sourceEntityId());
        item.setOwnerId(req.ownerId());
        item.setVersion(req.version());
        item.setValidFrom(req.validFrom());
        item.setValidTo(req.validTo());
        item.setFileName(req.fileName());
        if (req.status() != null) item.setStatus(req.status());
    }

    private EvidenceItemResponse toResponse(EvidenceItem e) {
        String subControlCode = null, subControlNameEn = null, controlCode = null, frameworkCode = null;

        if (e.getSubControlId() != null) {
            Optional<EvidenceSubControl> sc = subControlRepository.findById(e.getSubControlId());
            if (sc.isPresent()) {
                subControlCode   = sc.get().getSubControlCode();
                subControlNameEn = sc.get().getNameEn();
                Optional<EvidenceControl> ctrl = controlRepository.findById(sc.get().getControlId());
                if (ctrl.isPresent()) {
                    controlCode = ctrl.get().getControlCode();
                    Optional<EvidenceFramework> fw = frameworkRepository.findById(ctrl.get().getFrameworkId());
                    if (fw.isPresent()) frameworkCode = fw.get().getCode();
                }
            }
        }

        String ownerName = null;
        if (e.getOwnerId() != null) {
            ownerName = userRepository.findById(e.getOwnerId())
                    .map(User::getFullNameEn).orElse(null);
        }

        boolean isExpired = e.getValidTo() != null && e.getValidTo().isBefore(LocalDate.now());
        String evidenceCode = "EV-" + String.format("%05d", e.getId());

        return new EvidenceItemResponse(
                e.getId(), e.getExternalId(), evidenceCode, e.getOrganisationId(),
                e.getSubControlId(), subControlCode, subControlNameEn, controlCode, frameworkCode,
                e.getTitleEn(), e.getTitleAr(), e.getDescriptionEn(),
                e.getEvidenceType(), e.getSourceModule(),
                e.getOwnerId(), ownerName, e.getVersion(),
                e.getValidFrom(), e.getValidTo(), isExpired,
                e.getStatus(), e.getFileName(), e.getFileSize(), e.getMimeType(),
                e.getCreatedAt(), e.getUpdatedAt()
        );
    }
}
