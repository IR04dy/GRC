package com.tss.grc.module.evidence.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.evidence.dto.EvidenceRequestCreateDto;
import com.tss.grc.module.evidence.dto.EvidenceRequestResponse;
import com.tss.grc.module.evidence.entity.EvidenceRequest;
import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import com.tss.grc.module.evidence.entity.EvidenceControl;
import com.tss.grc.module.evidence.entity.EvidenceFramework;
import com.tss.grc.module.evidence.repository.EvidenceControlRepository;
import com.tss.grc.module.evidence.repository.EvidenceFrameworkRepository;
import com.tss.grc.module.evidence.repository.EvidenceRequestRepository;
import com.tss.grc.module.evidence.repository.EvidenceSubControlRepository;
import com.tss.grc.module.iam.entity.User;
import com.tss.grc.module.iam.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class EvidenceRequestService {

    private final EvidenceRequestRepository requestRepository;
    private final EvidenceSubControlRepository subControlRepository;
    private final EvidenceControlRepository controlRepository;
    private final EvidenceFrameworkRepository frameworkRepository;
    private final UserRepository userRepository;

    // ── Request lifecycle state machine ───────────────────────────────────────
    private static final Set<String> REQUEST_STATUSES =
        Set.of("OPEN", "IN_PROGRESS", "SUBMITTED", "APPROVED", "REJECTED", "CLOSED");

    private static final Map<String, Set<String>> REQUEST_TRANSITIONS = Map.of(
        "OPEN",        Set.of("IN_PROGRESS", "SUBMITTED", "REJECTED", "CLOSED"),
        "IN_PROGRESS", Set.of("SUBMITTED", "OPEN", "REJECTED", "CLOSED"),
        "SUBMITTED",   Set.of("APPROVED", "REJECTED", "IN_PROGRESS", "CLOSED"),
        "APPROVED",    Set.of("CLOSED", "OPEN"),
        "REJECTED",    Set.of("OPEN", "IN_PROGRESS", "CLOSED"),
        "CLOSED",      Set.of("OPEN")
    );

    @Transactional(readOnly = true)
    public Page<EvidenceRequestResponse> list(String status, Pageable pageable) {
        Long orgId = TenantContext.getCurrentTenant();
        Page<EvidenceRequest> page = (status != null && !status.isBlank())
                ? requestRepository.findByOrgAndStatus(orgId, status, pageable)
                : requestRepository.findByOrganisationId(orgId, pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public EvidenceRequestResponse get(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceRequest req = requestRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence request not found: " + id));
        return toResponse(req);
    }

    @Transactional
    public EvidenceRequestResponse create(EvidenceRequestCreateDto dto) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceRequest req = new EvidenceRequest();
        req.setOrganisationId(orgId);
        applyDto(req, dto);
        req = requestRepository.save(req);
        return toResponse(req);
    }

    @Transactional
    public EvidenceRequestResponse update(Long id, EvidenceRequestCreateDto dto) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceRequest req = requestRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence request not found: " + id));
        applyDto(req, dto);
        req = requestRepository.save(req);
        return toResponse(req);
    }

    @Transactional
    public EvidenceRequestResponse updateStatus(Long id, String newStatus) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceRequest req = requestRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence request not found: " + id));
        validateTransition(req.getStatus(), newStatus);
        req.setStatus(newStatus);
        if ("SUBMITTED".equals(newStatus) || "APPROVED".equals(newStatus)) {
            req.setFulfilledAt(LocalDateTime.now());
        }
        req = requestRepository.save(req);
        return toResponse(req);
    }

    /** Unknown statuses → 400; known-but-illegal moves → 409; same-status is a no-op. */
    private void validateTransition(String from, String to) {
        if (!REQUEST_STATUSES.contains(to)) {
            throw new IllegalArgumentException(
                "Unknown request status: '" + to + "'. Valid statuses: " + REQUEST_STATUSES);
        }
        if (to.equals(from)) return;
        Set<String> allowed = REQUEST_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    @Transactional
    public void delete(Long id) {
        Long orgId = TenantContext.getCurrentTenant();
        EvidenceRequest req = requestRepository.findById(id)
                .filter(r -> r.getOrganisationId().equals(orgId))
                .orElseThrow(() -> new EntityNotFoundException("Evidence request not found: " + id));
        req.setIsDeleted(true);
        requestRepository.save(req);
    }

    private void applyDto(EvidenceRequest req, EvidenceRequestCreateDto dto) {
        req.setTitleEn(dto.titleEn());
        req.setTitleAr(dto.titleAr());
        req.setDescriptionEn(dto.descriptionEn());
        req.setSubControlId(dto.subControlId());
        req.setAssignedToId(dto.assignedToId());
        req.setDueDate(dto.dueDate());
        req.setNotes(dto.notes());
        if (dto.priority() != null) req.setPriority(dto.priority());
    }

    private EvidenceRequestResponse toResponse(EvidenceRequest r) {
        String subControlCode = null, subControlNameEn = null, frameworkCode = null;
        if (r.getSubControlId() != null) {
            Optional<EvidenceSubControl> sc = subControlRepository.findById(r.getSubControlId());
            if (sc.isPresent()) {
                subControlCode   = sc.get().getSubControlCode();
                subControlNameEn = sc.get().getNameEn();
                Optional<EvidenceControl> ctrl = controlRepository.findById(sc.get().getControlId());
                if (ctrl.isPresent()) {
                    Optional<EvidenceFramework> fw = frameworkRepository.findById(ctrl.get().getFrameworkId());
                    if (fw.isPresent()) frameworkCode = fw.get().getCode();
                }
            }
        }
        String requestedByName = r.getRequestedById() != null
                ? userRepository.findById(r.getRequestedById()).map(User::getFullNameEn).orElse(null)
                : null;
        String assignedToName = r.getAssignedToId() != null
                ? userRepository.findById(r.getAssignedToId()).map(User::getFullNameEn).orElse(null)
                : null;

        return new EvidenceRequestResponse(
                r.getId(), r.getExternalId(),
                "REQ-" + String.format("%05d", r.getId()),
                r.getSubControlId(), subControlCode, subControlNameEn, frameworkCode,
                r.getTitleEn(), r.getTitleAr(), r.getDescriptionEn(),
                r.getRequestedById(), requestedByName,
                r.getAssignedToId(), assignedToName,
                r.getDueDate(), r.getPriority(), r.getStatus(), r.getNotes(),
                r.getCreatedAt(), r.getUpdatedAt()
        );
    }
}
