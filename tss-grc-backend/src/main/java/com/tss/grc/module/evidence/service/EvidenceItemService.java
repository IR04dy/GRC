package com.tss.grc.module.evidence.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.*;
import com.tss.grc.module.evidence.entity.*;
import com.tss.grc.module.evidence.repository.*;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
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
        item.setStatus(newStatus);
        item = itemRepository.save(item);
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
