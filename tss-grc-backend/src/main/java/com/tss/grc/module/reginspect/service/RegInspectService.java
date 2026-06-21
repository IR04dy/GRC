package com.tss.grc.module.reginspect.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateDocumentRequestRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.CreateInspectionRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.DashboardResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.DocumentRequestResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.InspectionResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.InspectionUpdateResponse;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.SubmitDocumentRequestRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.TransitionRequest;
import com.tss.grc.module.reginspect.dto.RegInspectDtos.UpdateInspectionRequest;
import com.tss.grc.module.reginspect.entity.RegDocumentRequest;
import com.tss.grc.module.reginspect.entity.RegInspection;
import com.tss.grc.module.reginspect.entity.RegInspectionUpdate;
import com.tss.grc.module.reginspect.repository.RegDocumentRequestRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionRepository;
import com.tss.grc.module.reginspect.repository.RegInspectionUpdateRepository;
import com.tss.grc.security.GrcUserDetails;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Regulatory Inspection Workspace service — Sprint 15 PR #1.
 *
 * <p>State machine:
 * <pre>
 *   PLANNED → ENTRY_MEETING → FIELDWORK → EXIT_MEETING →
 *   RESPONSE_DRAFTING → CLOSED
 * </pre>
 *
 * <p>F7 + F8 trigger codes:
 * <ul>
 *   <li>{@code REG_INSPECTION_CREATED}</li>
 *   <li>{@code REG_INSPECTION_TRANSITIONED}</li>
 *   <li>{@code REG_INSPECTION_CLOSED}</li>
 *   <li>{@code REG_DOCUMENT_REQUEST_OPENED}</li>
 *   <li>{@code REG_DOCUMENT_REQUEST_SUBMITTED}</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class RegInspectService {

    /** Allowed forward transitions only — no rewinds. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "PLANNED",            Set.of("ENTRY_MEETING", "CLOSED"),
            "ENTRY_MEETING",      Set.of("FIELDWORK", "CLOSED"),
            "FIELDWORK",          Set.of("EXIT_MEETING", "CLOSED"),
            "EXIT_MEETING",       Set.of("RESPONSE_DRAFTING", "CLOSED"),
            "RESPONSE_DRAFTING",  Set.of("CLOSED"),
            "CLOSED",             Set.of());

    private static final Set<String> ACTIVE_STATUSES = Set.of(
            "PLANNED", "ENTRY_MEETING", "FIELDWORK",
            "EXIT_MEETING", "RESPONSE_DRAFTING");

    private static final Set<String> OPEN_DR_STATUSES = Set.of(
            "OPEN", "IN_PROGRESS");

    private final RegInspectionRepository inspectionRepo;
    private final RegDocumentRequestRepository drRepo;
    private final RegInspectionUpdateRepository updateRepo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ─── Inspection writes ──────────────────────────────────────────

    @Transactional
    public InspectionResponse createInspection(CreateInspectionRequest req) {
        RegInspection i = new RegInspection();
        i.setOrganisationId(org());
        i.setExternalReference(generateReference(req.getRegulator()));
        i.setRegulator(req.getRegulator());
        i.setTitleEn(req.getTitleEn());
        i.setTitleAr(req.getTitleAr());
        i.setStatus("PLANNED");
        i.setNotifiedAt(req.getNotifiedAt());
        i.setEntryDate(req.getEntryDate());
        i.setExitDate(req.getExitDate());
        i.setLeadInspectorName(req.getLeadInspectorName());
        i.setScopeEn(req.getScopeEn());
        i.setScopeAr(req.getScopeAr());
        i.setCoordinatorUserId(req.getCoordinatorUserId());
        i.setSponsorUserId(req.getSponsorUserId());

        RegInspection saved = inspectionRepo.save(i);

        audit.log("REG_INSPECTION_CREATED", "RegInspection", saved.getId(),
                "Inspection opened — " + saved.getRegulator() + " / "
                        + saved.getExternalReference());
        emit("REG_INSPECTION_CREATED", "RegInspection", saved.getId(),
                saved.getExternalReference(),
                Map.of("regulator", saved.getRegulator(),
                        "externalReference", saved.getExternalReference()));

        timeline(saved.getId(), "STATUS_CHANGED", null, "PLANNED",
                "Inspection workspace opened.",
                "افتتاح ورشة التفتيش.", null);

        return toInspection(saved);
    }

    @Transactional
    public InspectionResponse updateInspection(Long id, UpdateInspectionRequest req) {
        RegInspection i = loadInspection(id);
        if (req.getTitleEn() != null) i.setTitleEn(req.getTitleEn());
        if (req.getTitleAr() != null) i.setTitleAr(req.getTitleAr());
        if (req.getNotifiedAt() != null) i.setNotifiedAt(req.getNotifiedAt());
        if (req.getEntryDate() != null) i.setEntryDate(req.getEntryDate());
        if (req.getExitDate() != null) i.setExitDate(req.getExitDate());
        if (req.getLeadInspectorName() != null) i.setLeadInspectorName(req.getLeadInspectorName());
        if (req.getScopeEn() != null) i.setScopeEn(req.getScopeEn());
        if (req.getScopeAr() != null) i.setScopeAr(req.getScopeAr());
        if (req.getCoordinatorUserId() != null) i.setCoordinatorUserId(req.getCoordinatorUserId());
        if (req.getSponsorUserId() != null) i.setSponsorUserId(req.getSponsorUserId());
        if (req.getExecutiveSummaryEn() != null) i.setExecutiveSummaryEn(req.getExecutiveSummaryEn());
        if (req.getExecutiveSummaryAr() != null) i.setExecutiveSummaryAr(req.getExecutiveSummaryAr());

        RegInspection saved = inspectionRepo.save(i);
        audit.log("REG_INSPECTION_UPDATED", "RegInspection", saved.getId(),
                "Inspection metadata updated.");
        return toInspection(saved);
    }

    @Transactional
    public InspectionResponse transition(Long id, TransitionRequest req) {
        RegInspection i = loadInspection(id);
        String from = i.getStatus();
        String to = req.getToStatus();
        Set<String> allowed = ALLOWED.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStateException(
                    "Illegal transition " + from + " → " + to
                            + " (allowed: " + allowed + ")");
        }
        i.setStatus(to);
        if ("CLOSED".equals(to)) {
            i.setClosedAt(LocalDateTime.now());
        }
        RegInspection saved = inspectionRepo.save(i);

        String trigger = "CLOSED".equals(to)
                ? "REG_INSPECTION_CLOSED"
                : "REG_INSPECTION_TRANSITIONED";
        audit.log(trigger, "RegInspection", saved.getId(),
                "Status " + from + " → " + to);
        emit(trigger, "RegInspection", saved.getId(),
                saved.getExternalReference(),
                Map.of("from", from, "to", to,
                        "regulator", saved.getRegulator(),
                        "externalReference", saved.getExternalReference()));

        timeline(saved.getId(),
                "CLOSED".equals(to) ? "CLOSURE" : "STATUS_CHANGED",
                from, to, req.getNoteEn(), req.getNoteAr(), null);

        return toInspection(saved);
    }

    // ─── Document request writes ────────────────────────────────────

    @Transactional
    public DocumentRequestResponse createDocumentRequest(Long inspectionId,
                                                          CreateDocumentRequestRequest req) {
        RegInspection i = loadInspection(inspectionId);
        if (Set.of("CLOSED").contains(i.getStatus())) {
            throw new IllegalStateException(
                    "Inspection is CLOSED — cannot add document requests");
        }

        long ordinal = drRepo.findByInspectionIdAndOrganisationIdOrderByRequestedAtDesc(
                i.getId(), org()).size() + 1L;

        RegDocumentRequest dr = new RegDocumentRequest();
        dr.setOrganisationId(org());
        dr.setInspectionId(i.getId());
        dr.setReference(i.getExternalReference() + "-DR-" + String.format("%03d", ordinal));
        dr.setTitleEn(req.getTitleEn());
        dr.setTitleAr(req.getTitleAr());
        dr.setDescriptionEn(req.getDescriptionEn());
        dr.setDescriptionAr(req.getDescriptionAr());
        dr.setStatus("OPEN");
        dr.setRequestedAt(LocalDateTime.now());
        dr.setDueDate(req.getDueDate());
        dr.setAssignedToUserId(req.getAssignedToUserId());

        RegDocumentRequest saved = drRepo.save(dr);
        recountInspectionCounters(i);

        audit.log("REG_DOCUMENT_REQUEST_OPENED", "RegDocumentRequest", saved.getId(),
                "DR " + saved.getReference() + " opened, due " + saved.getDueDate());
        emit("REG_DOCUMENT_REQUEST_OPENED", "RegDocumentRequest", saved.getId(),
                saved.getReference(),
                Map.of("inspectionId", String.valueOf(i.getId()),
                        "reference", saved.getReference(),
                        "regulator", i.getRegulator(),
                        "dueDate", String.valueOf(saved.getDueDate())));

        timeline(i.getId(), "DOCUMENT_REQUEST", null, null,
                "Opened DR " + saved.getReference() + ": " + saved.getTitleEn(),
                "فتح طلب وثائق " + saved.getReference() + ": " + saved.getTitleAr(),
                saved.getId());

        return toDocumentRequest(saved);
    }

    @Transactional
    public DocumentRequestResponse submitDocumentRequest(
            Long drId, SubmitDocumentRequestRequest req) {
        RegDocumentRequest dr = loadDocumentRequest(drId);
        if ("SUBMITTED".equals(dr.getStatus())) {
            throw new IllegalStateException("DR already submitted");
        }
        dr.setStatus("SUBMITTED");
        dr.setSubmittedAt(LocalDateTime.now());
        dr.setEvidenceReference(req.getEvidenceReference());
        dr.setSubmissionNotesEn(req.getSubmissionNotesEn());
        dr.setSubmissionNotesAr(req.getSubmissionNotesAr());
        RegDocumentRequest saved = drRepo.save(dr);

        RegInspection i = loadInspection(dr.getInspectionId());
        recountInspectionCounters(i);

        audit.log("REG_DOCUMENT_REQUEST_SUBMITTED", "RegDocumentRequest", saved.getId(),
                "DR " + saved.getReference() + " submitted");
        emit("REG_DOCUMENT_REQUEST_SUBMITTED", "RegDocumentRequest", saved.getId(),
                saved.getReference(),
                Map.of("inspectionId", String.valueOf(i.getId()),
                        "reference", saved.getReference(),
                        "regulator", i.getRegulator(),
                        "overdue", String.valueOf(isOverdue(saved))));

        timeline(i.getId(), "DR_SUBMITTED", null, null,
                "Submitted DR " + saved.getReference()
                        + " (evidence: " + req.getEvidenceReference() + ")",
                "تقديم طلب الوثائق " + saved.getReference()
                        + " (الدليل: " + req.getEvidenceReference() + ")",
                saved.getId());

        return toDocumentRequest(saved);
    }

    // ─── Reads ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<InspectionResponse> listInspections(int page, int size) {
        return inspectionRepo.findByOrganisationIdOrderByCreatedAtDesc(
                org(), PageRequest.of(page, size))
                .map(this::toInspection);
    }

    @Transactional(readOnly = true)
    public InspectionResponse getInspection(Long id) {
        return toInspection(loadInspection(id));
    }

    @Transactional(readOnly = true)
    public List<DocumentRequestResponse> listDocumentRequests(Long inspectionId) {
        loadInspection(inspectionId);
        return drRepo.findByInspectionIdAndOrganisationIdOrderByRequestedAtDesc(
                        inspectionId, org()).stream()
                .map(this::toDocumentRequest).toList();
    }

    @Transactional(readOnly = true)
    public List<InspectionUpdateResponse> listTimeline(Long inspectionId) {
        loadInspection(inspectionId);
        return updateRepo.findByInspectionIdAndOrganisationIdOrderByOccurredAtDesc(
                        inspectionId, org()).stream()
                .map(RegInspectService::toUpdate).toList();
    }

    @Transactional(readOnly = true)
    public DashboardResponse dashboard() {
        long activeCount = inspectionRepo.countByOrganisationIdAndStatusIn(
                org(), ACTIVE_STATUSES);
        long closedCount = inspectionRepo.countByOrganisationIdAndStatus(
                org(), "CLOSED");

        Page<RegInspection> recent = inspectionRepo
                .findByOrganisationIdOrderByCreatedAtDesc(
                        org(), PageRequest.of(0, 10));

        long openDr = 0;
        long overdueDr = 0;
        for (RegInspection i : recent.getContent()) {
            openDr   += i.getOpenDrCount() == null ? 0 : i.getOpenDrCount();
            overdueDr += i.getOverdueDrCount() == null ? 0 : i.getOverdueDrCount();
        }

        return DashboardResponse.builder()
                .activeCount(activeCount)
                .closedCount(closedCount)
                .openDrCount(openDr)
                .overdueDrCount(overdueDr)
                .recentInspections(recent.stream()
                        .map(this::toInspection).toList())
                .build();
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private RegInspection loadInspection(Long id) {
        return inspectionRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Inspection " + id + " not found in tenant"));
    }

    private RegDocumentRequest loadDocumentRequest(Long id) {
        return drRepo.findByIdAndOrganisationId(id, org())
                .orElseThrow(() -> new EntityNotFoundException(
                        "Document request " + id + " not found in tenant"));
    }

    private void recountInspectionCounters(RegInspection i) {
        long open = drRepo.countByInspectionIdAndOrganisationIdAndStatus(
                i.getId(), org(), "OPEN")
                + drRepo.countByInspectionIdAndOrganisationIdAndStatus(
                i.getId(), org(), "IN_PROGRESS");
        long submitted = drRepo.countByInspectionIdAndOrganisationIdAndStatus(
                i.getId(), org(), "SUBMITTED");

        List<RegDocumentRequest> drs = drRepo
                .findByInspectionIdAndOrganisationIdOrderByRequestedAtDesc(i.getId(), org());
        long overdue = drs.stream().filter(this::isOverdue).count();

        i.setOpenDrCount((int) open);
        i.setSubmittedDrCount((int) submitted);
        i.setOverdueDrCount((int) overdue);
        inspectionRepo.save(i);
    }

    private boolean isOverdue(RegDocumentRequest dr) {
        return !"SUBMITTED".equals(dr.getStatus())
                && dr.getDueDate() != null
                && dr.getDueDate().isBefore(LocalDate.now());
    }

    private String generateReference(String regulator) {
        LocalDate today = LocalDate.now();
        int q = ((today.getMonthValue() - 1) / 3) + 1;
        // Count existing inspections this quarter for ordinal — racy but
        // good enough for human-readable ref; uniqueness enforced by DB.
        long ordinal = inspectionRepo.findByOrganisationIdOrderByCreatedAtDesc(
                org(), PageRequest.of(0, 200)).stream()
                .filter(x -> x.getCreatedAt() != null
                        && x.getCreatedAt().getYear() == today.getYear()
                        && (((x.getCreatedAt().getMonthValue() - 1) / 3) + 1) == q)
                .count() + 1L;
        return regulator + "-OSI-" + today.getYear() + "-Q" + q + "-"
                + String.format("%03d", ordinal);
    }

    private void timeline(Long inspectionId, String updateType, String statusFrom,
                           String statusAfter, String noteEn, String noteAr,
                           Long linkedDrId) {
        RegInspectionUpdate u = new RegInspectionUpdate();
        u.setOrganisationId(org());
        u.setInspectionId(inspectionId);
        u.setOccurredAt(LocalDateTime.now());
        u.setUpdateType(updateType);
        u.setActorUserId(currentUserId());
        u.setStatusAfter(statusAfter);
        u.setNoteEn(noteEn);
        u.setNoteAr(noteAr);
        u.setLinkedDocumentRequestId(linkedDrId);
        updateRepo.save(u);
    }

    private static Long org() { return TenantContext.requireOrganisationId(); }

    private static Long currentUserId() {
        return com.tss.grc.common.security.CurrentActor.userId();
    }

    private void emit(String trigger, String entityType, Long entityId,
                       String correlationId, Map<String, Object> payload) {
        publisher.publishEvent(new DomainEvent(
                TenantContext.getCurrentTenant(),
                trigger, entityType, entityId, correlationId,
                payload == null ? Map.of() : new HashMap<>(payload)));
    }

    // ─── DTO mappers ────────────────────────────────────────────────

    private InspectionResponse toInspection(RegInspection i) {
        return InspectionResponse.builder()
                .id(i.getId())
                .externalReference(i.getExternalReference())
                .regulator(i.getRegulator())
                .titleEn(i.getTitleEn())
                .titleAr(i.getTitleAr())
                .status(i.getStatus())
                .notifiedAt(i.getNotifiedAt())
                .entryDate(i.getEntryDate())
                .exitDate(i.getExitDate())
                .closedAt(i.getClosedAt())
                .leadInspectorName(i.getLeadInspectorName())
                .scopeEn(i.getScopeEn())
                .scopeAr(i.getScopeAr())
                .coordinatorUserId(i.getCoordinatorUserId())
                .sponsorUserId(i.getSponsorUserId())
                .executiveSummaryEn(i.getExecutiveSummaryEn())
                .executiveSummaryAr(i.getExecutiveSummaryAr())
                .openDrCount(i.getOpenDrCount())
                .overdueDrCount(i.getOverdueDrCount())
                .submittedDrCount(i.getSubmittedDrCount())
                .createdAt(i.getCreatedAt())
                .updatedAt(i.getUpdatedAt())
                .build();
    }

    private DocumentRequestResponse toDocumentRequest(RegDocumentRequest dr) {
        return DocumentRequestResponse.builder()
                .id(dr.getId())
                .inspectionId(dr.getInspectionId())
                .reference(dr.getReference())
                .titleEn(dr.getTitleEn())
                .titleAr(dr.getTitleAr())
                .descriptionEn(dr.getDescriptionEn())
                .descriptionAr(dr.getDescriptionAr())
                .status(dr.getStatus())
                .requestedAt(dr.getRequestedAt())
                .dueDate(dr.getDueDate())
                .submittedAt(dr.getSubmittedAt())
                .assignedToUserId(dr.getAssignedToUserId())
                .evidenceReference(dr.getEvidenceReference())
                .submissionNotesEn(dr.getSubmissionNotesEn())
                .submissionNotesAr(dr.getSubmissionNotesAr())
                .overdue(isOverdue(dr))
                .build();
    }

    private static InspectionUpdateResponse toUpdate(RegInspectionUpdate u) {
        return InspectionUpdateResponse.builder()
                .id(u.getId())
                .inspectionId(u.getInspectionId())
                .occurredAt(u.getOccurredAt())
                .updateType(u.getUpdateType())
                .actorUserId(u.getActorUserId())
                .actorName(u.getActorName())
                .statusAfter(u.getStatusAfter())
                .noteEn(u.getNoteEn())
                .noteAr(u.getNoteAr())
                .linkedDocumentRequestId(u.getLinkedDocumentRequestId())
                .build();
    }
}
