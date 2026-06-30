package com.tss.grc.module.compliance.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.compliance.entity.AuditFinding;
import com.tss.grc.module.compliance.repository.AuditFindingRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * CAPA closure verification workflow — assessment §5.3 recommendation #6.
 *
 * <p>Stage flow on AuditFinding:
 *   IDENTIFIED → IMPLEMENTING → OWNER_TESTED → INDEPENDENT_TESTED
 *              → AUDIT_SIGNED_OFF → CLOSED
 *
 * <p>Every stage transition writes to {@code platform.audit_log} via
 * {@link AuditLogService} so the reusable {@code EntityActivityTimeline}
 * card (PRs #29-30) lights up on {@code FindingDetailPage}. The forensic
 * story already extends to workflow submissions, AAR, Risk, audit_lc
 * Finding, and CAPA — this PR adds the highest-traffic state machine
 * in the platform (the per-finding remediation lifecycle that every
 * reviewer walks every day).
 *
 * <p>Every transition also publishes a {@code FINDING_CAPA_STAGE_CHANGED}
 * {@link DomainEvent} so the F8 automation engine can route stage
 * transitions to NOTIFY / ESCALATE / CREATE_RECORD actions (rules can
 * match on {@code newStage="CLOSED"} for filing reminders,
 * {@code result="OWNER_TEST_FAIL"} for rework escalation, etc.).
 */
@Service
@RequiredArgsConstructor
public class CapaWorkflowService {

    private static final List<String> STAGE_ORDER = List.of(
        "IDENTIFIED", "IMPLEMENTING", "OWNER_TESTED",
        "INDEPENDENT_TESTED", "AUDIT_SIGNED_OFF", "CLOSED"
    );

    private static final Set<String> RESULT_VALUES = Set.of("PASS", "FAIL", "PARTIAL");

    private final AuditFindingRepository   findingRepo;
    private final AuditLogService          audit;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public AuditFinding markImplementing(Long findingId) {
        AuditFinding f = require(findingId);
        requireStage(f, "IDENTIFIED");
        String previousStage = stageOf(f);
        f.setCapaStage("IMPLEMENTING");
        AuditFinding saved = findingRepo.save(f);
        // Stage transition = its own audit row. actionType is the new stage
        // so the timeline card colours rows correctly (IMPLEMENTING → info).
        audit.log("IMPLEMENTING", "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " CAPA " + previousStage + " → IMPLEMENTING");
        publishStageEvent(saved, previousStage, null, null, null);
        return saved;
    }

    @Transactional
    public AuditFinding ownerTest(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "IMPLEMENTING");
        String result = str(body.get("result"));
        if (result == null || !RESULT_VALUES.contains(result))
            throw new IllegalArgumentException("result must be one of: PASS, FAIL, PARTIAL");
        String previousStage = stageOf(f);
        f.setOwnerTestAt(LocalDateTime.now());
        f.setOwnerTestResult(result);
        if (body.get("evidenceId") instanceof Number n) f.setOwnerTestEvidenceId(n.longValue());
        String notes = str(body.get("notes"));
        f.setOwnerTestNotes(notes);
        // FAIL keeps stage at IMPLEMENTING for rework; PASS/PARTIAL advances
        if ("FAIL".equals(result)) {
            f.setCapaStage("IMPLEMENTING");
        } else {
            f.setCapaStage("OWNER_TESTED");
        }
        AuditFinding saved = findingRepo.save(f);
        // Action type carries the result, not the stage — buyer reading
        // the timeline sees "OWNER_TEST_FAIL" pop in red against the
        // remediation row, even though the stage stayed at IMPLEMENTING.
        // This matches the "owner FAIL forces rework" semantics from the
        // CAPA workflow doc (§5.3 #6).
        audit.log("OWNER_TEST_" + result, "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " owner test " + result
            + " · stage " + previousStage + " → " + saved.getCapaStage()
            + (notes != null && !notes.isBlank() ? " · note: " + notes : ""));
        publishStageEvent(saved, previousStage, "OWNER_TEST_" + result, null, notes);
        return saved;
    }

    @Transactional
    public AuditFinding independentTest(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "OWNER_TESTED");
        String result = str(body.get("result"));
        if (result == null || !RESULT_VALUES.contains(result))
            throw new IllegalArgumentException("result must be one of: PASS, FAIL, PARTIAL");
        String previousStage = stageOf(f);
        f.setIndependentTestAt(LocalDateTime.now());
        f.setIndependentTestResult(result);
        Long byUserId = (body.get("byUserId") instanceof Number n) ? n.longValue() : null;
        if (byUserId != null) f.setIndependentTestById(byUserId);
        String notes = str(body.get("notes"));
        f.setIndependentTestNotes(notes);
        if ("FAIL".equals(result)) {
            // Send back to owner re-testing
            f.setCapaStage("IMPLEMENTING");
        } else {
            f.setCapaStage("INDEPENDENT_TESTED");
        }
        AuditFinding saved = findingRepo.save(f);
        audit.log("INDEPENDENT_TEST_" + result, "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " independent test " + result
            + " · stage " + previousStage + " → " + saved.getCapaStage()
            + (byUserId != null ? " · by user " + byUserId : "")
            + (notes != null && !notes.isBlank() ? " · note: " + notes : ""));
        publishStageEvent(saved, previousStage, "INDEPENDENT_TEST_" + result, byUserId, notes);
        return saved;
    }

    @Transactional
    public AuditFinding auditSignoff(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        requireStage(f, "INDEPENDENT_TESTED");
        String previousStage = stageOf(f);
        f.setAuditSignoffAt(LocalDateTime.now());
        Long byUserId = (body.get("byUserId") instanceof Number n) ? n.longValue() : null;
        if (byUserId != null) f.setAuditSignoffById(byUserId);
        String notes = str(body.get("notes"));
        f.setAuditSignoffNotes(notes);
        f.setCapaStage("AUDIT_SIGNED_OFF");
        AuditFinding saved = findingRepo.save(f);
        audit.log("AUDIT_SIGNED_OFF", "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " CAPA " + previousStage + " → AUDIT_SIGNED_OFF"
            + (byUserId != null ? " · signed off by user " + byUserId : "")
            + (notes != null && !notes.isBlank() ? " · note: " + notes : ""));
        publishStageEvent(saved, previousStage, null, byUserId, notes);
        return saved;
    }

    @Transactional
    public AuditFinding close(Long findingId) {
        AuditFinding f = require(findingId);
        requireStage(f, "AUDIT_SIGNED_OFF");
        String previousStage = stageOf(f);
        f.setCapaStage("CLOSED");
        f.setStatus("CLOSED");
        AuditFinding saved = findingRepo.save(f);
        // CLOSED is the canonical terminal — also flips Finding.status to
        // CLOSED, so the timeline shows both "stage advanced" and "finding
        // resolved" semantics in a single line.
        audit.log("CLOSED", "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " CAPA " + previousStage + " → CLOSED · finding closed");
        publishStageEvent(saved, previousStage, null, null, null);
        return saved;
    }

    /**
     * Reopen a CLOSED finding — the one transition out of the terminal stage.
     *
     * <p>Premature closure or a recurrence reactivates remediation: the finding
     * returns to {@code IDENTIFIED} / status {@code OPEN} and the prior cycle's
     * verification fields are cleared so the owner re-walks the lifecycle and
     * gathers fresh test evidence (the closed-out results stay in
     * {@code platform.audit_log}, not the live row). A reason is mandatory —
     * auditors must record why a signed-off finding was re-opened. Publishes
     * {@code FINDING_CAPA_STAGE_CHANGED} with {@code result="REOPENED"} so F8
     * rules can route reopen escalations (e.g. notify the audit committee when a
     * CLOSED finding recurs).
     */
    @Transactional
    public AuditFinding reopen(Long findingId, Map<String, Object> body) {
        AuditFinding f = require(findingId);
        // Only a CLOSED finding can be reopened (mirror of the forward guards).
        requireStage(f, "CLOSED");
        String reason = str(body.get("reason"));
        if (reason == null || reason.isBlank())
            throw new IllegalArgumentException("reason is required to reopen a finding");
        String previousStage = stageOf(f);
        f.setCapaStage("IDENTIFIED");
        f.setStatus("OPEN");
        // Fresh CAPA cycle — clear the prior cycle's verification fields so a
        // reopened finding doesn't display a stale "signed-off / PASS".
        f.setOwnerTestAt(null);         f.setOwnerTestResult(null);
        f.setOwnerTestEvidenceId(null); f.setOwnerTestNotes(null);
        f.setIndependentTestAt(null);   f.setIndependentTestResult(null);
        f.setIndependentTestById(null); f.setIndependentTestNotes(null);
        f.setAuditSignoffAt(null);      f.setAuditSignoffById(null);
        f.setAuditSignoffNotes(null);
        AuditFinding saved = findingRepo.save(f);
        audit.log("REOPENED", "AuditFinding", saved.getId(),
            "Finding #" + saved.getId() + " CAPA " + previousStage + " → IDENTIFIED · finding reopened · reason: " + reason);
        publishStageEvent(saved, previousStage, "REOPENED", null, reason);
        return saved;
    }

    private AuditFinding require(Long id) {
        Long org = TenantContext.getCurrentTenant();
        return findingRepo.findById(id)
            .filter(f -> f.getOrganisationId().equals(org))
            .orElseThrow(() -> new EntityNotFoundException("Finding not found: " + id));
    }

    private void requireStage(AuditFinding f, String expected) {
        String actual = f.getCapaStage() != null ? f.getCapaStage() : "IDENTIFIED";
        if (!STAGE_ORDER.contains(actual))
            throw new IllegalArgumentException("Unknown CAPA stage on finding: " + actual);
        if (!actual.equals(expected))
            throw new IllegalArgumentException(
                "Cannot perform action — finding is in stage " + actual + ", expected " + expected);
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    /** Default {@code IDENTIFIED} if the row has no stage yet — keeps the
     *  audit message readable even for findings created before CAPA
     *  staging existed. */
    private static String stageOf(AuditFinding f) {
        return f.getCapaStage() != null ? f.getCapaStage() : "IDENTIFIED";
    }

    /** Fan out to F8 — single event type {@code FINDING_CAPA_STAGE_CHANGED}
     *  fires from all five transition methods with a rich payload. One
     *  type (not five) keeps the engine matcher simple: a rule can target
     *  closure via {@code {newStage:"CLOSED"}}, rework via
     *  {@code {result:"OWNER_TEST_FAIL"}}, sign-off via
     *  {@code {newStage:"AUDIT_SIGNED_OFF"}}, etc.
     *
     *  <p>Payload values null-coerced to "" so the engine's equality check
     *  never NPEs on a sparse finding (matches the WorkflowSubmissionService
     *  / CapaTaskService nullSafe pattern).
     *
     *  @param result    e.g. "OWNER_TEST_PASS" / "INDEPENDENT_TEST_FAIL"
     *                   or {@code null} for stage-only transitions
     *                   (markImplementing, auditSignoff, close)
     *  @param byUserId  actor for sign-off / independent-test; null otherwise
     *  @param notes     free-text reviewer note; null when not provided
     */
    private void publishStageEvent(AuditFinding f, String previousStage,
                                   String result, Long byUserId, String notes) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("previousStage",     nullSafe(previousStage));
        payload.put("newStage",          nullSafe(f.getCapaStage()));
        payload.put("status",            nullSafe(f.getStatus()));
        payload.put("severity",          nullSafe(f.getSeverity()));
        // clauseRef is the regulatory anchor on the finding (e.g.
        // "NCA-ECC-1-3-1" / "PDPL Art. 28") — F8 rules use it to scope
        // to a specific framework family without needing to match on the
        // engagement code.
        payload.put("clauseRef",         nullSafe(f.getClauseRef()));
        payload.put("result",            nullSafe(result));
        payload.put("byUserId",          byUserId == null ? "" : String.valueOf(byUserId));
        payload.put("notes",             nullSafe(notes));
        eventPublisher.publishEvent(new DomainEvent(
            f.getOrganisationId(), "FINDING_CAPA_STAGE_CHANGED",
            "AuditFinding", f.getId(), null, payload));
    }

    private static String nullSafe(String v) { return v == null ? "" : v; }
}
