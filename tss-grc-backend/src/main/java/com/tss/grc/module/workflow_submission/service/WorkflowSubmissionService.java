package com.tss.grc.module.workflow_submission.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.repository.WorkflowSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CRUD + state-machine for {@link WorkflowSubmission}.
 *
 * <p>Every read/write is tenant-scoped via {@link TenantContext}.
 * Every state change writes to {@code platform.audit_log} via
 * {@link AuditLogService} so a GRC reviewer can prove who submitted /
 * reviewed / closed a record. The state machine is intentionally
 * permissive — the wizard owns the transitions (SUBMITTED →
 * UNDER_REVIEW → APPROVED/REJECTED → CLOSED).
 */
@Service
@RequiredArgsConstructor
public class WorkflowSubmissionService {

    private final WorkflowSubmissionRepository repo;
    private final AuditLogService              audit;

    private Long org() {
        Long o = TenantContext.getCurrentTenant();
        if (o == null) throw new IllegalStateException("No tenant in TenantContext");
        return o;
    }

    // ── Inputs ────────────────────────────────────────────────────────

    public record CreateBody(
        String workflowId,
        String workflowModule,
        String titleEn,
        String titleAr,
        String summaryEn,
        String summaryAr,
        String regulatoryAnchor,
        String status,
        String payloadJson
    ) {}

    public record ReviewBody(String decision, String reviewerNote) {}

    // ── Mutations ─────────────────────────────────────────────────────

    @Transactional
    public WorkflowSubmission create(CreateBody body) {
        if (body.workflowId() == null || body.workflowId().isBlank())
            throw new IllegalArgumentException("workflowId is required");
        if (body.workflowModule() == null || body.workflowModule().isBlank())
            throw new IllegalArgumentException("workflowModule is required");
        if (body.titleEn() == null || body.titleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (body.payloadJson() == null || body.payloadJson().isBlank())
            throw new IllegalArgumentException("payloadJson is required");

        WorkflowSubmission s = new WorkflowSubmission();
        s.setOrganisationId(org());
        s.setWorkflowId(body.workflowId());
        s.setWorkflowModule(body.workflowModule());
        s.setTitleEn(body.titleEn());
        s.setTitleAr(body.titleAr());
        s.setSummaryEn(body.summaryEn());
        s.setSummaryAr(body.summaryAr());
        s.setRegulatoryAnchor(body.regulatoryAnchor());
        s.setStatus(body.status() == null || body.status().isBlank() ? "SUBMITTED" : body.status());
        s.setPayloadJson(body.payloadJson());
        s.setSubmittedAt(LocalDateTime.now());

        WorkflowSubmission saved = repo.save(s);
        audit.log("CREATE", "WorkflowSubmission", saved.getId(),
            "Workflow submission created: workflow=" + saved.getWorkflowId()
            + ", module=" + saved.getWorkflowModule()
            + ", status=" + saved.getStatus()
            + (saved.getRegulatoryAnchor() != null
                ? ", anchor=" + saved.getRegulatoryAnchor() : ""));
        return saved;
    }

    @Transactional
    public WorkflowSubmission review(Long id, ReviewBody body) {
        WorkflowSubmission s = repo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException(
                "WorkflowSubmission %d not found for this tenant".formatted(id)));
        String decision = body.decision() == null ? "" : body.decision().toUpperCase();
        String previousStatus = s.getStatus();
        switch (decision) {
            case "APPROVE", "APPROVED" -> s.setStatus("APPROVED");
            case "REJECT", "REJECTED" -> s.setStatus("REJECTED");
            case "CLOSE", "CLOSED"     -> s.setStatus("CLOSED");
            case "REVIEW", "UNDER_REVIEW" -> s.setStatus("UNDER_REVIEW");
            default -> throw new IllegalArgumentException(
                "Unknown review decision: " + body.decision());
        }
        s.setReviewerNote(body.reviewerNote());
        s.setReviewedAt(LocalDateTime.now());
        WorkflowSubmission saved = repo.save(s);
        audit.log(s.getStatus(), "WorkflowSubmission", saved.getId(),
            "Workflow submission " + previousStatus + " → " + s.getStatus()
            + " for workflow=" + saved.getWorkflowId()
            + (body.reviewerNote() != null && !body.reviewerNote().isBlank()
                ? ", note=" + body.reviewerNote() : ""));
        return saved;
    }

    // ── Queries ───────────────────────────────────────────────────────

    public List<WorkflowSubmission> list() {
        return repo.findByOrganisationIdOrderBySubmittedAtDesc(org());
    }

    public List<WorkflowSubmission> listByWorkflowId(String workflowId) {
        return repo.findByOrganisationIdAndWorkflowIdOrderBySubmittedAtDesc(org(), workflowId);
    }

    public List<WorkflowSubmission> listByModule(String module) {
        return repo.findByOrganisationIdAndWorkflowModuleOrderBySubmittedAtDesc(org(), module);
    }

    public WorkflowSubmission get(Long id) {
        return repo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException(
                "WorkflowSubmission %d not found for this tenant".formatted(id)));
    }
}
