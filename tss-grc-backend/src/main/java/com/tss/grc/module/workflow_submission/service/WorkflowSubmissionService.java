package com.tss.grc.module.workflow_submission.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.workflow_submission.entity.WorkflowSubmission;
import com.tss.grc.module.workflow_submission.repository.WorkflowSubmissionRepository;
import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final ApplicationEventPublisher    eventPublisher;

    private Long org() {
        Long o = TenantContext.getCurrentTenant();
        if (o == null) throw new IllegalStateException("No tenant in TenantContext");
        return o;
    }

    // ── Inputs ────────────────────────────────────────────────────────

    public record CreateBody(
        @NotBlank @Size(max = 80)   String workflowId,
        @NotBlank @Size(max = 40)   String workflowModule,
        @NotBlank @Size(max = 300)  String titleEn,
        @Size(max = 300)            String titleAr,
        @Size(max = 1000)           String summaryEn,
        @Size(max = 1000)           String summaryAr,
        @Size(max = 60)             String regulatoryAnchor,
        @Size(max = 40)             String status,
        // Wizard draft JSON — capped so a single submission cannot store an
        // unbounded blob. NVARCHAR(MAX) on the column, but 1 MiB is well
        // beyond any legitimate wizard payload.
        @NotBlank @Size(max = 1_048_576) String payloadJson
    ) {}

    public record ReviewBody(
        @NotBlank @Size(max = 40)   String decision,
        @Size(max = 1000)           String reviewerNote
    ) {}

    // ── Mutations ─────────────────────────────────────────────────────

    @Transactional
    public WorkflowSubmission create(CreateBody body, Long submitterUserId) {
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
        // Stamp the submitter server-side from the authenticated principal so
        // the maker-checker self-review guard in review(...) has a trustworthy
        // owner to compare the reviewer against.
        s.setSubmittedBy(submitterUserId);
        s.setCreatedBy(submitterUserId);
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

        // Fan out to F8 — the engine listens for WORKFLOW_SUBMISSION_CREATED
        // and matches enabled rules against the payload (workflowId,
        // workflowModule, status, regulatoryAnchor). Seed rules in V127
        // route PDPL breaches to the DPO, NCA cyber incidents to the CISO,
        // above-appetite risk acceptances to the Risk Committee, etc.
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "WORKFLOW_SUBMISSION_CREATED",
            "WorkflowSubmission", saved.getId(), null,
            payloadFor(saved)));
        return saved;
    }

    @Transactional
    public WorkflowSubmission review(Long id, ReviewBody body, Long reviewerUserId) {
        WorkflowSubmission s = repo.findByOrganisationIdAndId(org(), id)
            .orElseThrow(() -> new EntityNotFoundException(
                "WorkflowSubmission %d not found for this tenant".formatted(id)));

        // Segregation of duties (maker-checker): the reviewer must not be the
        // submitter. Both ids are server-stamped (submittedBy on create,
        // reviewerUserId derived from the authenticated principal), so a user
        // cannot approve / reject / close their own submission.
        if (reviewerUserId != null
                && s.getSubmittedBy() != null
                && reviewerUserId.equals(s.getSubmittedBy())) {
            throw new IllegalStateException(
                "Segregation of duties: a submission cannot be reviewed by its own submitter");
        }

        String decision = body.decision() == null ? "" : body.decision().toUpperCase(Locale.ROOT);
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
        s.setReviewedBy(reviewerUserId);
        s.setReviewedAt(LocalDateTime.now());
        WorkflowSubmission saved = repo.save(s);
        audit.log(s.getStatus(), "WorkflowSubmission", saved.getId(),
            "Workflow submission " + previousStatus + " → " + s.getStatus()
            + " for workflow=" + saved.getWorkflowId()
            + (body.reviewerNote() != null && !body.reviewerNote().isBlank()
                ? ", note=" + body.reviewerNote() : ""));

        // Fan out to F8 — separate event from CREATED so rules can target
        // status transitions specifically (e.g. "approved a board pack →
        // CREATE_RECORD a regulatory-filing reminder").
        Map<String, Object> payload = payloadFor(saved);
        payload.put("previousStatus", previousStatus);
        eventPublisher.publishEvent(new DomainEvent(
            saved.getOrganisationId(), "WORKFLOW_SUBMISSION_REVIEWED",
            "WorkflowSubmission", saved.getId(), null,
            payload));
        return saved;
    }

    /** Forms remediation (G1) — persist the primary engine request a submission was routed as.
     *  Idempotent: only the first spawned request becomes the canonical pointer; subsequent
     *  requests raised from the submission are still discoverable via the sourceRecord anchor. */
    @Transactional
    public WorkflowSubmission linkSpawnedRequest(Long id, Long requestId) {
        WorkflowSubmission s = get(id);   // org-scoped
        if (s.getSpawnedRequestId() == null && requestId != null) {
            s.setSpawnedRequestId(requestId);
            s = repo.save(s);
            audit.log("LINK_REQUEST", "WorkflowSubmission", s.getId(),
                "Linked spawned engine request #" + requestId);
        }
        return s;
    }

    /** Build the canonical payload Map for F8 rule-condition matching.
     *  Engine evaluates equality + contains on these string keys. */
    private static Map<String, Object> payloadFor(WorkflowSubmission s) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("workflowId",       nullSafe(s.getWorkflowId()));
        m.put("workflowModule",   nullSafe(s.getWorkflowModule()));
        m.put("status",           nullSafe(s.getStatus()));
        m.put("regulatoryAnchor", nullSafe(s.getRegulatoryAnchor()));
        m.put("titleEn",          nullSafe(s.getTitleEn()));
        return m;
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }

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

    /** Per-wizard aggregate counts for the Workflow Hub.
     *
     *  <p>Returns a list of {@link WorkflowCount}s — one entry per
     *  {@code workflowId} the tenant has at least one submission for.
     *  Single SQL query under the hood; replaces the client-side
     *  aggregation that scaled O(rows) per Hub visit. */
    public List<WorkflowCount> countsByWorkflowId() {
        Long o = org();
        return repo.countsByWorkflowId(o).stream()
            .map(row -> new WorkflowCount(
                (String) row[0],
                ((Number) row[1]).longValue(),
                ((Number) row[2]).longValue(),
                ((Number) row[3]).longValue()))
            .toList();
    }

    public record WorkflowCount(String workflowId, long total, long open, long approved) {}
}
