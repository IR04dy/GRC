package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CaseSubmissionResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.CloseRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.InvestigationUpdateRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicCaseResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.PublicUpdateResponse;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.ReporterReplyRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.TriageRequest;
import com.tss.grc.module.ethics.whistleblower.dto.WhistleblowerDtos.UpdateResponse;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCaseUpdate;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseRepository;
import com.tss.grc.module.ethics.whistleblower.repository.WhistleblowerCaseUpdateRepository;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Sprint 13 — Whistleblower / Ethics Hotline service.
 *
 * <p>State machine (enforced here):
 * <pre>
 *   RECEIVED → TRIAGE
 *   TRIAGE → UNDER_INVESTIGATION | CLOSED_NO_ACTION | REFERRED
 *   UNDER_INVESTIGATION → SUBSTANTIATED | UNSUBSTANTIATED | REFERRED
 *   SUBSTANTIATED  → CLOSED_REMEDIATED
 *   UNSUBSTANTIATED → CLOSED_NO_ACTION
 *   REFERRED        → CLOSED_NO_ACTION | CLOSED_REMEDIATED
 *   CLOSED_*        (terminal)
 * </pre>
 *
 * <p>F8 events:
 * <ul>
 *   <li>{@code WHISTLEBLOWER_CASE_SUBMITTED}</li>
 *   <li>{@code WHISTLEBLOWER_TRIAGE_DECIDED}</li>
 *   <li>{@code WHISTLEBLOWER_INVESTIGATION_STARTED}</li>
 *   <li>{@code WHISTLEBLOWER_INVESTIGATION_NOTE}</li>
 *   <li>{@code WHISTLEBLOWER_CASE_CLOSED}</li>
 *   <li>{@code WHISTLEBLOWER_SENIOR_EXEC_FLAGGED} — fires on submit AND on
 *       triage whenever {@code involvesSeniorExecutive=1}, so the Ethics
 *       Committee Chair + Internal Auditor are notified out-of-band.</li>
 * </ul>
 *
 * <h3>Reporter anonymity</h3>
 * Public-portal submissions are audited via {@link #anonymousAudit} —
 * a path that bypasses the IP / user-agent auto-fill in
 * {@link AuditLogService}. The reporter's IP address is NEVER written
 * to the audit ledger or the case row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WhistleblowerCaseService {

    /** Allowed status transitions. */
    private static final Map<String, Set<String>> ALLOWED = Map.of(
            "RECEIVED",            Set.of("TRIAGE"),
            "TRIAGE",              Set.of("UNDER_INVESTIGATION", "CLOSED_NO_ACTION", "REFERRED"),
            "UNDER_INVESTIGATION", Set.of("SUBSTANTIATED", "UNSUBSTANTIATED", "REFERRED"),
            "SUBSTANTIATED",       Set.of("CLOSED_REMEDIATED"),
            "UNSUBSTANTIATED",     Set.of("CLOSED_NO_ACTION"),
            "REFERRED",            Set.of("CLOSED_NO_ACTION", "CLOSED_REMEDIATED"),
            "CLOSED_NO_ACTION",    Set.of(),
            "CLOSED_REMEDIATED",   Set.of()
    );

    /**
     * TODO: when a host-based tenant resolver lands, derive the org_id
     * from the request host (e.g. ethics.<bank>.sa). For now the public
     * portal defaults to org 1 — the demo bank. The constant is here so
     * the substitution is a single grep target.
     */
    static final Long PUBLIC_PORTAL_DEFAULT_ORG = 1L;

    private final WhistleblowerCaseRepository caseRepo;
    private final WhistleblowerCaseUpdateRepository updateRepo;
    private final AuditLogService audit;
    private final AuditLogRepository auditRepo;
    private final ApplicationEventPublisher publisher;

    // ─── Reads (internal) ─────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<CaseResponse> list() {
        return caseRepo.findByOrganisationIdAndIsDeletedFalseOrderBySubmittedAtDesc(org())
                .stream().map(CaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> listByStatus(String status) {
        return caseRepo.findByOrganisationIdAndStatusAndIsDeletedFalseOrderBySubmittedAtDesc(org(), status)
                .stream().map(CaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<CaseResponse> listBySeverity(String severity) {
        return caseRepo.findByOrganisationIdAndSeverityAndIsDeletedFalseOrderBySubmittedAtDesc(org(), severity)
                .stream().map(CaseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public CaseResponse get(Long id) {
        return CaseResponse.from(loadOrThrow(id));
    }

    @Transactional(readOnly = true)
    public List<UpdateResponse> listUpdates(Long caseId) {
        // Ensures the case is in-tenant.
        loadOrThrow(caseId);
        return updateRepo.findByOrganisationIdAndCaseIdAndIsDeletedFalseOrderByCreatedAtAsc(org(), caseId)
                .stream().map(UpdateResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long orgId = org();
        return Map.of(
                "total",        caseRepo.countByOrganisationIdAndIsDeletedFalse(orgId),
                "received",     caseRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "RECEIVED"),
                "triage",       caseRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "TRIAGE"),
                "investigating",caseRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "UNDER_INVESTIGATION"),
                "closed",       caseRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "CLOSED_NO_ACTION")
                              + caseRepo.countByOrganisationIdAndStatusAndIsDeletedFalse(orgId, "CLOSED_REMEDIATED")
        );
    }

    // ─── Public-portal submission ─────────────────────────────────────

    /**
     * Public-portal submission. No tenant context required — the org_id
     * is resolved from the request host or defaults to
     * {@link #PUBLIC_PORTAL_DEFAULT_ORG}.
     *
     * <p>This path is the ONLY one that uses {@link #anonymousAudit}: no
     * IP, no user-agent, no source endpoint written to the audit ledger.
     */
    @Transactional
    public CaseSubmissionResponse submitCase(CaseSubmissionRequest req) {
        Long orgId = resolvePublicOrg();

        WhistleblowerCase c = new WhistleblowerCase();
        c.setOrganisationId(orgId);
        c.setCaseCode(generateCode(orgId));
        c.setSubmissionToken(UUID.randomUUID());
        c.setSubmittedAt(LocalDateTime.now());
        c.setSubmissionChannel(safeChannel(req.submissionChannel(), "PUBLIC_PORTAL"));
        c.setReporterType(safeReporterType(req.reporterType(),
                Boolean.TRUE.equals(req.isAnonymous()) ? "ANONYMOUS" : "EXTERNAL"));
        c.setIsAnonymous(Boolean.TRUE.equals(req.isAnonymous()) || isAnonymousReporterType(req.reporterType()));
        if (!Boolean.TRUE.equals(c.getIsAnonymous())) {
            c.setReporterName(req.reporterName());
            c.setReporterEmail(req.reporterEmail());
            c.setReporterPhone(req.reporterPhone());
            c.setReporterEmployeeId(req.reporterEmployeeId());
        }
        c.setCategory(req.category());
        c.setSeverity(safeSeverity(req.severity()));
        c.setSubjectEn(req.subjectEn());
        c.setSubjectAr(req.subjectAr());
        c.setNarrativeEn(req.narrativeEn());
        c.setNarrativeAr(req.narrativeAr());
        c.setPartiesInvolved(req.partiesInvolved());
        c.setAllegedPeriodStart(req.allegedPeriodStart());
        c.setAllegedPeriodEnd(req.allegedPeriodEnd());
        c.setInvolvesSeniorExecutive(Boolean.TRUE.equals(req.involvesSeniorExecutive()));
        c.setStatus("RECEIVED");

        WhistleblowerCase saved = caseRepo.save(c);

        // Anonymous audit — NO IP, NO user-agent.
        anonymousAudit(orgId, "CREATE", saved.getId(),
                "Whistleblower case submitted: " + saved.getCaseCode()
                        + " · category=" + saved.getCategory()
                        + " · severity=" + saved.getSeverity()
                        + " · anonymous=" + saved.getIsAnonymous()
                        + " · seniorExec=" + saved.getInvolvesSeniorExecutive());

        emit("WHISTLEBLOWER_CASE_SUBMITTED", saved, null);
        if (Boolean.TRUE.equals(saved.getInvolvesSeniorExecutive())) {
            emit("WHISTLEBLOWER_SENIOR_EXEC_FLAGGED", saved, null);
        }

        return CaseSubmissionResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public PublicCaseResponse lookupByToken(UUID token) {
        WhistleblowerCase c = caseRepo.findBySubmissionTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid tracking token"));
        List<PublicUpdateResponse> visible = updateRepo
                .findByCaseIdAndIsVisibleToReporterTrueAndIsDeletedFalseOrderByCreatedAtAsc(c.getId())
                .stream().map(PublicUpdateResponse::from).toList();
        return new PublicCaseResponse(
                c.getCaseCode(), c.getStatus(), c.getCategory(), c.getSeverity(),
                c.getSubjectEn(), c.getSubjectAr(),
                c.getSubmittedAt(), c.getClosedAt(),
                visible
        );
    }

    /** Reporter posts a follow-up message via the public-token path. */
    @Transactional
    public PublicCaseResponse reportByToken(UUID token, ReporterReplyRequest req) {
        WhistleblowerCase c = caseRepo.findBySubmissionTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new EntityNotFoundException("Invalid tracking token"));

        WhistleblowerCaseUpdate u = new WhistleblowerCaseUpdate();
        u.setOrganisationId(c.getOrganisationId());
        u.setCaseId(c.getId());
        u.setUpdateType("REPORTER_REPLY");
        u.setActorUserId(null);  // reporter, no user-id
        u.setIsVisibleToReporter(true);
        u.setBodyEn(req.bodyEn());
        u.setBodyAr(req.bodyAr());
        updateRepo.save(u);

        anonymousAudit(c.getOrganisationId(), "REPORTER_REPLY", c.getId(),
                "Reporter posted reply on " + c.getCaseCode());
        return lookupByToken(token);
    }

    // ─── Internal: triage / investigation / close ─────────────────────

    @Transactional
    public CaseResponse triage(Long id, TriageRequest req, Long actorUserId) {
        WhistleblowerCase c = loadOrThrow(id);
        String from = c.getStatus();

        // First triage: RECEIVED → TRIAGE — record decision but DO NOT close
        // unless the decision rerouted to investigation / closure.
        if ("RECEIVED".equals(from)) {
            c.setStatus("TRIAGE");
        }
        c.setTriageByUserId(actorUserId);
        c.setTriageAt(LocalDateTime.now());
        c.setTriageDecisionEn(req.triageDecisionEn());
        c.setTriageDecisionAr(req.triageDecisionAr());
        if (req.severity() != null && !req.severity().isBlank()) {
            c.setSeverity(safeSeverity(req.severity()));
        }
        if (req.assignedInvestigatorUserId() != null) {
            c.setAssignedInvestigatorUserId(req.assignedInvestigatorUserId());
        }
        boolean wasSeniorExec = Boolean.TRUE.equals(c.getInvolvesSeniorExecutive());
        if (req.involvesSeniorExecutive() != null) {
            c.setInvolvesSeniorExecutive(req.involvesSeniorExecutive());
        }

        WhistleblowerCase saved = caseRepo.save(c);

        // Timeline row — visible to reporter.
        WhistleblowerCaseUpdate u = new WhistleblowerCaseUpdate();
        u.setOrganisationId(saved.getOrganisationId());
        u.setCaseId(saved.getId());
        u.setUpdateType("TRIAGE_DECISION");
        u.setActorUserId(actorUserId);
        u.setIsVisibleToReporter(true);
        u.setBodyEn(req.triageDecisionEn());
        u.setBodyAr(req.triageDecisionAr());
        updateRepo.save(u);

        audit.log("TRIAGE_DECIDED", "WhistleblowerCase", saved.getId(),
                "Triage decided on " + saved.getCaseCode()
                        + " · severity=" + saved.getSeverity()
                        + " · investigator=" + saved.getAssignedInvestigatorUserId());

        emit("WHISTLEBLOWER_TRIAGE_DECIDED", saved, from);
        if (!wasSeniorExec && Boolean.TRUE.equals(saved.getInvolvesSeniorExecutive())) {
            emit("WHISTLEBLOWER_SENIOR_EXEC_FLAGGED", saved, from);
        }
        return CaseResponse.from(saved);
    }

    /**
     * Move into UNDER_INVESTIGATION explicitly — the investigator picks
     * up the case. Optional: caller may pass an opening note.
     */
    @Transactional
    public CaseResponse startInvestigation(Long id, Long investigatorUserId,
                                            String openingNoteEn, String openingNoteAr,
                                            Long actorUserId) {
        WhistleblowerCase c = loadOrThrow(id);
        String from = c.getStatus();
        guardTransition(from, "UNDER_INVESTIGATION");

        c.setStatus("UNDER_INVESTIGATION");
        c.setAssignedInvestigatorUserId(investigatorUserId);
        c.setInvestigationStartedAt(LocalDateTime.now());
        WhistleblowerCase saved = caseRepo.save(c);

        WhistleblowerCaseUpdate u = new WhistleblowerCaseUpdate();
        u.setOrganisationId(saved.getOrganisationId());
        u.setCaseId(saved.getId());
        u.setUpdateType("INVESTIGATOR_ASSIGNED");
        u.setActorUserId(actorUserId);
        u.setIsVisibleToReporter(true);
        u.setBodyEn(openingNoteEn != null && !openingNoteEn.isBlank()
                ? openingNoteEn
                : "Investigation opened. The assigned investigator will reach out shortly if further information is needed.");
        u.setBodyAr(openingNoteAr != null && !openingNoteAr.isBlank()
                ? openingNoteAr
                : "تم فتح التحقيق. وسيتواصل المحقّق المُكلّف عند الحاجة لمزيد من المعلومات.");
        updateRepo.save(u);

        audit.log("INVESTIGATION_STARTED", "WhistleblowerCase", saved.getId(),
                "Investigation started on " + saved.getCaseCode() + " · investigator=" + investigatorUserId);

        emit("WHISTLEBLOWER_INVESTIGATION_STARTED", saved, from);
        return CaseResponse.from(saved);
    }

    /**
     * Investigator adds a note or finding. The {@code isVisibleToReporter}
     * flag controls whether this shows on the public status page.
     */
    @Transactional
    public UpdateResponse investigationUpdate(Long id, InvestigationUpdateRequest req, Long actorUserId) {
        WhistleblowerCase c = loadOrThrow(id);
        if (!"UNDER_INVESTIGATION".equals(c.getStatus()) && !"TRIAGE".equals(c.getStatus())) {
            throw new IllegalTransitionException(c.getStatus(), "INVESTIGATION_UPDATE",
                    Set.of("TRIAGE", "UNDER_INVESTIGATION"));
        }
        String type = (req.updateType() == null || req.updateType().isBlank())
                ? "INVESTIGATION_NOTE" : req.updateType();
        if (!"INVESTIGATION_NOTE".equals(type) && !"FINDING_RECORDED".equals(type)) {
            throw new IllegalArgumentException("update_type must be INVESTIGATION_NOTE or FINDING_RECORDED");
        }

        WhistleblowerCaseUpdate u = new WhistleblowerCaseUpdate();
        u.setOrganisationId(c.getOrganisationId());
        u.setCaseId(c.getId());
        u.setUpdateType(type);
        u.setActorUserId(actorUserId);
        u.setIsVisibleToReporter(Boolean.TRUE.equals(req.isVisibleToReporter()));
        u.setBodyEn(req.bodyEn());
        u.setBodyAr(req.bodyAr());
        WhistleblowerCaseUpdate saved = updateRepo.save(u);

        audit.log("INVESTIGATION_NOTE", "WhistleblowerCase", c.getId(),
                "Investigation update added: type=" + type + " · visibleToReporter="
                        + saved.getIsVisibleToReporter());

        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", String.valueOf(c.getId()));
        payload.put("caseCode", c.getCaseCode());
        payload.put("updateType", type);
        payload.put("visibleToReporter", String.valueOf(saved.getIsVisibleToReporter()));
        publisher.publishEvent(new DomainEvent(
                c.getOrganisationId(), "WHISTLEBLOWER_INVESTIGATION_NOTE",
                "WhistleblowerCase", c.getId(), c.getCaseCode(), payload));
        return UpdateResponse.from(saved);
    }

    /**
     * Terminal transition. {@code terminalStatus} must be one of
     * SUBSTANTIATED / UNSUBSTANTIATED / REFERRED / CLOSED_NO_ACTION /
     * CLOSED_REMEDIATED.
     */
    @Transactional
    public CaseResponse close(Long id, CloseRequest req, Long actorUserId) {
        WhistleblowerCase c = loadOrThrow(id);
        String from = c.getStatus();
        String to = req.terminalStatus();
        guardTransition(from, to);

        c.setStatus(to);
        c.setClosureSummaryEn(req.closureSummaryEn());
        c.setClosureSummaryAr(req.closureSummaryAr());
        if (req.linkedCapaTaskId() != null) {
            c.setLinkedCapaTaskId(req.linkedCapaTaskId());
        }
        if (to.startsWith("CLOSED_")) {
            c.setClosedAt(LocalDateTime.now());
        }
        WhistleblowerCase saved = caseRepo.save(c);

        WhistleblowerCaseUpdate u = new WhistleblowerCaseUpdate();
        u.setOrganisationId(saved.getOrganisationId());
        u.setCaseId(saved.getId());
        u.setUpdateType(to.startsWith("CLOSED_") ? "CLOSURE" : "STATUS_CHANGED");
        u.setActorUserId(actorUserId);
        u.setIsVisibleToReporter(true);
        u.setBodyEn(req.closureSummaryEn());
        u.setBodyAr(req.closureSummaryAr());
        updateRepo.save(u);

        audit.log("CASE_CLOSED", "WhistleblowerCase", saved.getId(),
                "Case " + saved.getCaseCode() + " transitioned " + from + " → " + to);

        emit("WHISTLEBLOWER_CASE_CLOSED", saved, from);
        return CaseResponse.from(saved);
    }

    /** Link a CAPA task without changing case state. */
    @Transactional
    public CaseResponse escalateToCapa(Long id, Long capaTaskId) {
        WhistleblowerCase c = loadOrThrow(id);
        c.setLinkedCapaTaskId(capaTaskId);
        WhistleblowerCase saved = caseRepo.save(c);
        audit.log("LINK_CAPA", "WhistleblowerCase", saved.getId(),
                "Linked CAPA task " + capaTaskId + " to " + saved.getCaseCode());
        return CaseResponse.from(saved);
    }

    // ─── Helpers ────────────────────────────────────────────────────

    private static void guardTransition(String from, String to) {
        Set<String> allowed = ALLOWED.getOrDefault(Objects.requireNonNullElse(from, ""), Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalTransitionException(from, to, allowed);
        }
    }

    private WhistleblowerCase loadOrThrow(Long id) {
        return caseRepo.findByOrganisationIdAndIdAndIsDeletedFalse(org(), id)
                .orElseThrow(() -> new EntityNotFoundException("Whistleblower case not found: " + id));
    }

    private static Long org() {
        return TenantContext.requireOrganisationId();
    }

    /**
     * Resolves the org for a public-portal submission. Today: defaults
     * to {@link #PUBLIC_PORTAL_DEFAULT_ORG} unless a tenant is already
     * set on the thread (e.g. a logged-in employee using the same form).
     *
     * <p>TODO: when host-based tenant resolution lands, look up
     * {@code organisation} by host header (ethics.<bank>.sa).
     */
    Long resolvePublicOrg() {
        Long fromContext = TenantContext.getCurrentTenant();
        return fromContext != null ? fromContext : PUBLIC_PORTAL_DEFAULT_ORG;
    }

    private String generateCode(Long orgId) {
        int year = java.time.Year.now().getValue();
        long count = caseRepo.countByOrganisationIdAndIsDeletedFalse(orgId);
        for (int seq = (int) count + 1; seq < count + 1000; seq++) {
            String candidate = String.format("WB-%d-%03d", year, seq);
            if (!caseRepo.existsByOrganisationIdAndCaseCodeAndIsDeletedFalse(orgId, candidate)) {
                return candidate;
            }
        }
        // Fallback — extremely unlikely.
        return "WB-" + year + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private static String safeChannel(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s;
    }

    private static String safeReporterType(String s, String fallback) {
        if (s == null || s.isBlank()) return fallback;
        return s;
    }

    private static boolean isAnonymousReporterType(String reporterType) {
        return "ANONYMOUS".equals(reporterType);
    }

    private static String safeSeverity(String s) {
        if (s == null || s.isBlank()) return "MEDIUM";
        return switch (s) {
            case "CRITICAL", "HIGH", "MEDIUM", "LOW" -> s;
            default -> "MEDIUM";
        };
    }

    private void emit(String trigger, WhistleblowerCase c, String previousStatus) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("caseId", String.valueOf(c.getId()));
        payload.put("caseCode", c.getCaseCode());
        payload.put("status", c.getStatus());
        if (previousStatus != null) payload.put("previousStatus", previousStatus);
        payload.put("category", c.getCategory());
        payload.put("severity", c.getSeverity());
        payload.put("isAnonymous", String.valueOf(c.getIsAnonymous()));
        payload.put("involvesSeniorExecutive", String.valueOf(c.getInvolvesSeniorExecutive()));
        publisher.publishEvent(new DomainEvent(
                c.getOrganisationId(), trigger,
                "WhistleblowerCase", c.getId(), c.getCaseCode(), payload));
    }

    /**
     * Writes an audit row WITHOUT capturing IP / user-agent / source endpoint —
     * this is the only audit path used for public-portal whistleblower
     * submissions to preserve reporter anonymity. The user fields are
     * also cleared because the caller is unauthenticated.
     */
    void anonymousAudit(Long orgId, String actionType, Long entityId, String message) {
        AuditLogEntry e = new AuditLogEntry();
        e.setOrganisationId(orgId);
        e.setActionType(actionType);
        e.setEntityType("WhistleblowerCase");
        e.setEntityId(entityId);
        e.setSourceModule("ethics.whistleblower");
        e.setMessage(message);
        e.setSeverity("INFO");
        // Explicitly leave ip_address / user_agent / source_endpoint /
        // user_username / user_role NULL — see service Javadoc.
        auditRepo.save(e);
        log.debug("[audit-anon] org={} {} WhistleblowerCase#{} (ip-suppressed)",
                orgId, actionType, entityId);
    }
}
