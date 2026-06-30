package com.tss.grc.module.bcm.cycle.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.bcm.cycle.entity.BcmAssessmentCycle;
import com.tss.grc.module.bcm.cycle.entity.BcmGovernanceRecord;
import com.tss.grc.module.bcm.cycle.repository.BcmAssessmentCycleRepository;
import com.tss.grc.module.bcm.cycle.repository.BcmGovernanceRecordRepository;
import com.tss.grc.module.admin.committee.repository.CommitteeRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * BCM Assessment Cycle + Governance service — Sprint BCM-1.
 *
 * <p>Two closely-related resources kept together because they share
 * the same tenant + the same downstream readiness counters.
 *
 * <p>Cycle lifecycle: DRAFT → IN_PROGRESS → UNDER_REVIEW →
 * APPROVED → ARCHIVED. (No backwards moves except UNDER_REVIEW →
 * IN_PROGRESS for rework, and APPROVED → ARCHIVED at year-end.)
 *
 * <p>Governance lifecycle: DRAFT → UNDER_REVIEW → APPROVED → ACTIVE
 * (with CHANGES_REQUIRED loop back to DRAFT); ACTIVE → REVIEW_DUE
 * fires when {@code next_review_date} has passed; ARCHIVED is
 * terminal.
 *
 * <p>Every transition emits an F7 audit-log row with
 * {@code sourceModule = "bcm"}.
 */
@Service
@RequiredArgsConstructor
public class BcmCycleService {

    private static final Set<String> CYCLE_STATUSES =
        Set.of("DRAFT", "IN_PROGRESS", "UNDER_REVIEW", "APPROVED", "ARCHIVED");
    private static final Set<String> GOVERNANCE_STATUSES =
        Set.of("DRAFT", "UNDER_REVIEW", "CHANGES_REQUIRED", "APPROVED", "ACTIVE", "REVIEW_DUE", "ARCHIVED");
    private static final Set<String> REVIEW_FREQS =
        Set.of("ANNUAL", "SEMI_ANNUAL", "QUARTERLY", "ON_CHANGE");

    private final BcmAssessmentCycleRepository cycles;
    private final BcmGovernanceRecordRepository governance;
    private final CommitteeRepository committees;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    private Long org() { return TenantContext.getCurrentTenant(); }

    // ── F8 emit ───────────────────────────────────────────────────────────────
    // BCM cycle is the annual assessment heartbeat — every BIA refresh, BCP
    // edit, DR test, and governance record hangs off a cycle. F8 events let
    // downstream consumers (calendar, KPI dashboards, regulator-facing AAR)
    // react to cycle milestones automatically.
    private Map<String, Object> cyclePayload(BcmAssessmentCycle c, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("cycleId",         nullSafeStr(c.getId()));
        m.put("status",          nullSafeStr(c.getStatus()));
        m.put("previousStatus",  nullSafeStr(previousStatus));
        m.put("reportingYear",   nullSafeStr(c.getReportingYear()));
        m.put("bcmManagerId",    nullSafeStr(c.getBcmManagerId()));
        m.put("executiveSponsorId", nullSafeStr(c.getExecutiveSponsorId()));
        m.put("startDate",       nullSafeStr(c.getStartDate()));
        m.put("endDate",         nullSafeStr(c.getEndDate()));
        return m;
    }

    private Map<String, Object> governancePayload(BcmGovernanceRecord g, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("governanceId",    nullSafeStr(g.getId()));
        m.put("policyTitleEn",   nullSafeStr(g.getPolicyTitleEn()));
        m.put("policyVersion",   nullSafeStr(g.getPolicyVersion()));
        m.put("status",          nullSafeStr(g.getApprovalStatus()));
        m.put("previousStatus",  nullSafeStr(previousStatus));
        m.put("reviewFrequency", nullSafeStr(g.getReviewFrequency()));
        m.put("nextReviewDate",  nullSafeStr(g.getNextReviewDate()));
        return m;
    }

    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    private void emitCycle(String trigger, BcmAssessmentCycle c, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            org(), trigger, "BcmAssessmentCycle", c.getId(),
            "bcm_cycle:" + c.getId(), cyclePayload(c, previousStatus)));
    }

    private void emitGovernance(String trigger, BcmGovernanceRecord g, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            org(), trigger, "BcmGovernanceRecord", g.getId(),
            "bcm_governance:" + g.getId(), governancePayload(g, previousStatus)));
    }

    // ─── Assessment Cycles ────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmAssessmentCycle> listCycles(String status) {
        Long o = org();
        return (status != null && !status.isBlank())
            ? cycles.findByOrganisationIdAndStatusOrderByReportingYearDesc(o, status)
            : cycles.findByOrganisationIdOrderByReportingYearDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmAssessmentCycle getCycle(Long id) {
        return cycles.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("BCM cycle not found: " + id));
    }

    @Transactional
    public BcmAssessmentCycle createCycle(BcmAssessmentCycle body) {
        if (body.getCycleNameEn() == null || body.getCycleNameEn().isBlank())
            throw new IllegalArgumentException("cycleNameEn is required");
        if (body.getReportingYear() == null)
            throw new IllegalArgumentException("reportingYear is required");
        if (cycles.findByOrganisationIdAndReportingYear(org(), body.getReportingYear()).isPresent())
            throw new IllegalArgumentException(
                "A BCM cycle already exists for reporting year " + body.getReportingYear());
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getStatus() == null) body.setStatus("DRAFT");
        if (!CYCLE_STATUSES.contains(body.getStatus()))
            throw new IllegalArgumentException("Invalid status: " + body.getStatus());
        BcmAssessmentCycle saved = cycles.save(body);
        auditCycle(saved, "CREATE", null, saved.getStatus(),
            "BCM cycle created: " + saved.getCycleNameEn());
        emitCycle("BCM_CYCLE_CREATED", saved, null);
        return saved;
    }

    @Transactional
    public BcmAssessmentCycle updateCycle(Long id, BcmAssessmentCycle body) {
        BcmAssessmentCycle c = getCycle(id);
        if ("APPROVED".equals(c.getStatus()) || "ARCHIVED".equals(c.getStatus()))
            throw new IllegalStateException("Cannot edit a " + c.getStatus()
                + " cycle — re-open it via transition first");
        if (body.getCycleNameEn() != null) c.setCycleNameEn(body.getCycleNameEn());
        if (body.getCycleNameAr() != null) c.setCycleNameAr(body.getCycleNameAr());
        if (body.getStartDate() != null)   c.setStartDate(body.getStartDate());
        if (body.getEndDate() != null)     c.setEndDate(body.getEndDate());
        if (body.getScope() != null)       c.setScope(body.getScope());
        if (body.getBcmManagerId() != null)        c.setBcmManagerId(body.getBcmManagerId());
        if (body.getExecutiveSponsorId() != null)  c.setExecutiveSponsorId(body.getExecutiveSponsorId());
        c.setUpdatedAt(LocalDateTime.now());
        return cycles.save(c);
    }

    /** DRAFT → IN_PROGRESS → UNDER_REVIEW → APPROVED → ARCHIVED. */
    @Transactional
    public BcmAssessmentCycle transitionCycle(Long id, String to, String actor) {
        BcmAssessmentCycle c = getCycle(id);
        if (!CYCLE_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = c.getStatus();
        boolean ok = switch (from) {
            case "DRAFT"        -> "IN_PROGRESS".equals(to);
            case "IN_PROGRESS"  -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW" -> "APPROVED".equals(to) || "IN_PROGRESS".equals(to);
            case "APPROVED"     -> "ARCHIVED".equals(to);
            default             -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        c.setStatus(to);
        if ("APPROVED".equals(to)) {
            c.setApprovedBy(actor);
            c.setApprovedAt(LocalDateTime.now());
        }
        c.setUpdatedAt(LocalDateTime.now());
        BcmAssessmentCycle saved = cycles.save(c);
        auditCycle(saved, "TRANSITION", from, to,
            "BCM cycle " + saved.getId() + " " + from + " → " + to);
        String trigger = switch (to) {
            case "IN_PROGRESS"  -> "BCM_CYCLE_STARTED";
            case "UNDER_REVIEW" -> "BCM_CYCLE_UNDER_REVIEW";
            case "APPROVED"     -> "BCM_CYCLE_APPROVED";
            case "ARCHIVED"     -> "BCM_CYCLE_ARCHIVED";
            default             -> "BCM_CYCLE_TRANSITIONED";
        };
        emitCycle(trigger, saved, from);
        return saved;
    }

    // ─── Governance ────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<BcmGovernanceRecord> listGovernance(String approvalStatus) {
        Long o = org();
        return (approvalStatus != null && !approvalStatus.isBlank())
            ? governance.findByOrganisationIdAndApprovalStatusOrderByCreatedAtDesc(o, approvalStatus)
            : governance.findByOrganisationIdOrderByCreatedAtDesc(o);
    }

    @Transactional(readOnly = true)
    public BcmGovernanceRecord getGovernance(Long id) {
        return governance.findByIdAndOrganisationId(id, org())
            .orElseThrow(() -> new EntityNotFoundException("Governance record not found: " + id));
    }

    @Transactional
    public BcmGovernanceRecord createGovernance(BcmGovernanceRecord body) {
        if (body.getPolicyTitleEn() == null || body.getPolicyTitleEn().isBlank())
            throw new IllegalArgumentException("policyTitleEn is required");
        if (body.getReviewFrequency() != null && !REVIEW_FREQS.contains(body.getReviewFrequency()))
            throw new IllegalArgumentException("Invalid review frequency: " + body.getReviewFrequency());
        validateCommittee(body.getCommitteeId());
        body.setId(null);
        body.setOrganisationId(org());
        if (body.getPolicyVersion() == null) body.setPolicyVersion("1.0");
        if (body.getApprovalStatus() == null) body.setApprovalStatus("DRAFT");
        if (!GOVERNANCE_STATUSES.contains(body.getApprovalStatus()))
            throw new IllegalArgumentException("Invalid approval status: " + body.getApprovalStatus());
        BcmGovernanceRecord saved = governance.save(body);
        auditGovernance(saved, "CREATE", null, saved.getApprovalStatus(),
            "Governance record created: " + saved.getPolicyTitleEn());
        emitGovernance("BCM_GOVERNANCE_CREATED", saved, null);
        return saved;
    }

    @Transactional
    public BcmGovernanceRecord updateGovernance(Long id, BcmGovernanceRecord body) {
        BcmGovernanceRecord r = getGovernance(id);
        if ("APPROVED".equals(r.getApprovalStatus()) || "ACTIVE".equals(r.getApprovalStatus()))
            throw new IllegalStateException("Cannot edit a " + r.getApprovalStatus()
                + " governance record — transition to CHANGES_REQUIRED first");
        if (body.getPolicyTitleEn() != null) r.setPolicyTitleEn(body.getPolicyTitleEn());
        if (body.getPolicyTitleAr() != null) r.setPolicyTitleAr(body.getPolicyTitleAr());
        if (body.getPolicyVersion() != null) r.setPolicyVersion(body.getPolicyVersion());
        if (body.getScope() != null)         r.setScope(body.getScope());
        if (body.getObjective() != null)     r.setObjective(body.getObjective());
        if (body.getBcmManagerId() != null)        r.setBcmManagerId(body.getBcmManagerId());
        if (body.getExecutiveSponsorId() != null)  r.setExecutiveSponsorId(body.getExecutiveSponsorId());
        if (body.getCommitteeId() != null)         { validateCommittee(body.getCommitteeId()); r.setCommitteeId(body.getCommitteeId()); }
        if (body.getReviewFrequency() != null) {
            if (!REVIEW_FREQS.contains(body.getReviewFrequency()))
                throw new IllegalArgumentException("Invalid review frequency");
            r.setReviewFrequency(body.getReviewFrequency());
        }
        if (body.getLastReviewDate() != null) r.setLastReviewDate(body.getLastReviewDate());
        if (body.getNextReviewDate() != null) r.setNextReviewDate(body.getNextReviewDate());
        if (body.getEvidenceId() != null)     r.setEvidenceId(body.getEvidenceId());
        r.setUpdatedAt(LocalDateTime.now());
        return governance.save(r);
    }

    /** DRAFT → UNDER_REVIEW → APPROVED → ACTIVE; CHANGES_REQUIRED loops back. */
    @Transactional
    public BcmGovernanceRecord transitionGovernance(Long id, String to, String actor) {
        BcmGovernanceRecord r = getGovernance(id);
        if (!GOVERNANCE_STATUSES.contains(to))
            throw new IllegalArgumentException("Invalid target status: " + to);
        String from = r.getApprovalStatus();
        boolean ok = switch (from) {
            case "DRAFT"            -> "UNDER_REVIEW".equals(to);
            case "UNDER_REVIEW"     -> "APPROVED".equals(to) || "CHANGES_REQUIRED".equals(to);
            case "CHANGES_REQUIRED" -> "DRAFT".equals(to);
            case "APPROVED"         -> "ACTIVE".equals(to);
            case "ACTIVE"           -> "REVIEW_DUE".equals(to) || "ARCHIVED".equals(to);
            case "REVIEW_DUE"       -> "UNDER_REVIEW".equals(to) || "ARCHIVED".equals(to);
            default                 -> false;
        };
        if (!ok) throw new IllegalTransitionException(from, to, Set.of());
        r.setApprovalStatus(to);
        if ("APPROVED".equals(to)) {
            r.setApprovedBy(actor);
            r.setApprovedAt(LocalDateTime.now());
        }
        r.setUpdatedAt(LocalDateTime.now());
        BcmGovernanceRecord saved = governance.save(r);
        auditGovernance(saved, "TRANSITION", from, to,
            "Governance record " + saved.getId() + " " + from + " → " + to);
        String trigger = switch (to) {
            case "UNDER_REVIEW"     -> "BCM_GOVERNANCE_UNDER_REVIEW";
            case "APPROVED"         -> "BCM_GOVERNANCE_APPROVED";
            case "ACTIVE"           -> "BCM_GOVERNANCE_ACTIVATED";
            case "REVIEW_DUE"       -> "BCM_GOVERNANCE_REVIEW_DUE";
            case "ARCHIVED"         -> "BCM_GOVERNANCE_ARCHIVED";
            case "CHANGES_REQUIRED" -> "BCM_GOVERNANCE_CHANGES_REQUIRED";
            default                 -> "BCM_GOVERNANCE_TRANSITIONED";
        };
        emitGovernance(trigger, saved, from);
        return saved;
    }

    // ─── Helpers ───────────────────────────────────────────────────

    /** A non-null committee link must reference a real committee in this org. */
    private void validateCommittee(Long committeeId) {
        if (committeeId == null) return;
        committees.findByIdAndOrganisationId(committeeId, org()).orElseThrow(() ->
            new IllegalArgumentException("committeeId " + committeeId
                + " does not reference a committee in this organisation"));
    }

    private void auditCycle(BcmAssessmentCycle c, String action, String before, String after, String message) {
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType("BcmAssessmentCycle")
            .entityId(c.getId())
            .entityCode(c.getCycleNameEn())
            .workflowStatusBefore(before)
            .workflowStatusAfter(after)
            .sourceModule("bcm")
            .message(message));
    }

    private void auditGovernance(BcmGovernanceRecord r, String action, String before, String after, String message) {
        audit.recordEvent(new AuditLogService.AuditEventBuilder()
            .actionType(action)
            .entityType("BcmGovernanceRecord")
            .entityId(r.getId())
            .entityCode(r.getPolicyTitleEn())
            .workflowStatusBefore(before)
            .workflowStatusAfter(after)
            .sourceModule("bcm")
            .message(message));
    }
}
