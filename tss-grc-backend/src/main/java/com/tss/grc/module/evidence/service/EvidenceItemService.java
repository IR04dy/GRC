package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.*;
import com.tss.grc.module.evidence.entity.*;
import com.tss.grc.module.evidence.repository.*;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class EvidenceItemService {

    private final EvidenceItemRepository itemRepository;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;
    private final EvidenceLinkRepository linkRepository;
    private final UserRepository userRepository;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

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
        return transitionStatus(id, "APPROVED");
    }

    @Transactional
    public EvidenceItemResponse reject(Long id) {
        return transitionStatus(id, "REJECTED");
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
            default               -> "EVIDENCE_TRANSITIONED";
        };
        emit(trigger, item, previousStatus);
        return toResponse(item);
    }

    private void applyRequest(EvidenceItem item, EvidenceItemCreateRequest req) {
        item.setTitleEn(req.titleEn());
        item.setTitleAr(req.titleAr());
        item.setDescriptionEn(req.descriptionEn());
        item.setSubControlId(req.subControlId());
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
