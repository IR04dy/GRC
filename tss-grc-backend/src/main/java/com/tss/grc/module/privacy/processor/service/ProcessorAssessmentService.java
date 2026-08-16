package com.tss.grc.module.privacy.processor.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.privacy.processor.entity.ProcessorAssessment;
import com.tss.grc.module.privacy.processor.repository.ProcessorAssessmentRepository;
import com.tss.grc.module.tprm.automation.TprmAutomationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Lifecycle service for {@link ProcessorAssessment}.
 *
 * <p>The eight Art. 28 controls are each {@code PASS | PASS_WITH_CONDITIONS |
 * FAIL | NOT_APPLICABLE}. The overall outcome and risk score are derived from
 * the worst-case tier across the eight controls.
 */
@Service
@RequiredArgsConstructor
public class ProcessorAssessmentService {

    private static final List<String> STATUSES = List.of("IN_PROGRESS", "COMPLETED", "APPROVED", "REJECTED");
    private static final Set<String>  CONTROL_OUTCOMES = Set.of("PASS", "PASS_WITH_CONDITIONS", "FAIL", "NOT_APPLICABLE");

    private final ProcessorAssessmentRepository repo;
    @Autowired(required = false) private TprmAutomationService automation;

    @Transactional(readOnly = true)
    public Page<ProcessorAssessment> list(Pageable p) {
        return repo.findByOrganisationIdOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), p);
    }
    @Transactional(readOnly = true)
    public Page<ProcessorAssessment> listByStatus(String status, Pageable p) {
        return repo.findByOrganisationIdAndStatusOrderByCreatedAtDesc(TenantContext.getCurrentTenant(), status, p);
    }
    @Transactional(readOnly = true)
    public ProcessorAssessment get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
                .orElseThrow(() -> new EntityNotFoundException("ProcessorAssessment " + id));
    }
    @Transactional
    public ProcessorAssessment create(ProcessorAssessment req) {
        req.setOrganisationId(TenantContext.getCurrentTenant());
        req.setId(null);
        // F2-22b(iv) — the same defect as F2-22b(ii)'s BCM cycle, in privacy: `if (status ==
        // null) IN_PROGRESS` let every OTHER status through, so a create could file a processor
        // as APPROVED without ever passing /score (which derives the outcome from the eight
        // controls) or /approve (which stamps the approver). Reject and name the path that owns
        // the state — do not silently force DRAFT, because a silent drop is F2-44's defect.
        if (req.getStatus() == null) req.setStatus("IN_PROGRESS");
        else if (!"IN_PROGRESS".equals(req.getStatus())) {
            throw new IllegalArgumentException(
                "A new processor assessment starts IN_PROGRESS (received: " + req.getStatus()
                + "). Score it via POST /api/privacy/processor-assessments/{id}/score, then "
                + "approve or reject it — those transitions derive the outcome and risk score "
                + "from the eight Art. 28 controls and stamp the approver.");
        }
        // ⚙ F2-219 — REFUTED, and the measurement is worth keeping. This create persists the request
        // object, so it looks like it accepts approvedAt / approverName / assessedAt / assessedBy /
        // assessorName / overallOutcome / riskScore beside an IN_PROGRESS row. All SEVEN already
        // carry @JsonProperty(access = READ_ONLY) on the entity, so the wire cannot supply any of
        // them. A scrub here would be decoration, and an inert construct is worse than none because
        // it answers the question a reader was about to ask (F2-184) — so there is deliberately no
        // scrub, and this comment is the answer instead.
        //
        // 🔑 It appeared on F2-218's work list only because that gate knew BaseEntity's nine
        // server-owned names and no per-field annotations; F2-219 taught it to read them.
        return repo.save(req);
    }

    @Transactional
    public ProcessorAssessment scoreAndComplete(Long id, Map<String, String> controls) {
        return scoreAndComplete(id, controls, null, null);
    }

    @Transactional
    public ProcessorAssessment scoreAndComplete(Long id, Map<String, String> controls,
                                                Long assessorId, String assessorName) {
        ProcessorAssessment a = get(id);
        controls.forEach((k, v) -> {
            if (v != null && !CONTROL_OUTCOMES.contains(v))
                throw new IllegalArgumentException("Control outcome must be one of " + CONTROL_OUTCOMES);
            switch (k) {
                case "c1WrittenInstructions" -> a.setC1WrittenInstructions(v);
                case "c2Confidentiality"     -> a.setC2Confidentiality(v);
                case "c3SecurityMeasures"    -> a.setC3SecurityMeasures(v);
                case "c4SubProcessorAuth"    -> a.setC4SubProcessorAuth(v);
                case "c5BreachNotification"  -> a.setC5BreachNotification(v);
                case "c6DsrAssistance"       -> a.setC6DsrAssistance(v);
                case "c7DeletionOrReturn"    -> a.setC7DeletionOrReturn(v);
                case "c8AuditRights"         -> a.setC8AuditRights(v);
                default -> { /* ignore unknown keys */ }
            }
        });
        // Derive overall outcome + risk score
        String[] outcomes = {
                a.getC1WrittenInstructions(), a.getC2Confidentiality(), a.getC3SecurityMeasures(),
                a.getC4SubProcessorAuth(), a.getC5BreachNotification(), a.getC6DsrAssistance(),
                a.getC7DeletionOrReturn(), a.getC8AuditRights()
        };
        boolean anyFail = false; int conditional = 0; int evaluated = 0;
        for (String o : outcomes) {
            if (o == null || "NOT_APPLICABLE".equals(o)) continue;
            evaluated++;
            if ("FAIL".equals(o)) anyFail = true;
            if ("PASS_WITH_CONDITIONS".equals(o)) conditional++;
        }
        String overall = anyFail ? "FAIL" : conditional > 0 ? "PASS_WITH_CONDITIONS" : "PASS";
        a.setOverallOutcome(overall);
        a.setRiskScore(BigDecimal.valueOf(anyFail ? 8.0 : 1.0 + conditional * 1.5)
                .setScale(1, java.math.RoundingMode.HALF_UP));
        a.setStatus("COMPLETED");
        a.setAssessedAt(LocalDateTime.now());
        // F2-22b(iv): stamp WHO scored it. Nothing set these before, so the register's
        // "assessed on <date> (<assessor>)" line rendered a date next to an em dash.
        if (assessorId   != null) a.setAssessedBy(assessorId);
        if (assessorName != null) a.setAssessorName(assessorName);
        ProcessorAssessment saved = repo.save(a);
        // Enhancement E: auto-CAPA on FAIL outcome.
        if ("FAIL".equals(saved.getOverallOutcome()) && automation != null) {
            automation.onProcessorAssessmentFailed(saved);
        }
        return saved;
    }

    /**
     * F2-22b(iv) — {@code approverName} is the authenticated principal, never a request field.
     *
     * <p>It used to come from the body, and the caller was the SPA sending the literal string
     * {@code 'admin'} for every approval, with the controller defaulting to {@code "admin"} when
     * the key was absent. So the PDPL Art. 28 register recorded "admin" as the approver of every
     * processor regardless of who clicked, and any caller could name anyone. DF-3's rule: a field
     * that records who did something is never the caller's to set.
     */
    @Transactional
    public ProcessorAssessment approve(Long id, String approverName) {
        if (approverName == null || approverName.isBlank()) {
            throw new IllegalStateException(
                "No authenticated principal to record as the approver of processor assessment "
                + id + ". Approval attribution is the record's whole value.");
        }
        ProcessorAssessment a = get(id);
        a.setStatus("APPROVED");
        a.setApprovedAt(LocalDateTime.now());
        a.setApproverName(approverName);
        return repo.save(a);
    }

    @Transactional
    public ProcessorAssessment reject(Long id) {
        ProcessorAssessment a = get(id);
        a.setStatus("REJECTED");
        return repo.save(a);
    }
}
