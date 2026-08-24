package com.tss.grc.module.ethics.whistleblower.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.common.validation.ClosedVocabulary;
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
import com.tss.grc.module.platform.tenancy.PublicPortalTenantResolver;
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

    // ─── F2-179 — the four caller-supplied vocabularies this table constrains ─────────
    //
    // ⚠⚠ What stood here before: `safeChannel` and `safeReporterType` returned their argument
    // unchanged (defaulting only a blank), and `safeSeverity` switched case-SENSITIVELY. All four
    // columns are collated Latin1_General_100_CI_AS_SC_UTF8, so their CHECK constraints cannot tell
    // 'anonymous' from 'ANONYMOUS' — and `submitCase` is the platform's PUBLIC, UNAUTHENTICATED
    // portal, the widest caller surface in the product. Two consequences, both reproduced live
    // before this was written:
    //
    //   (a) `isAnonymousReporterType` asked "ANONYMOUS".equals(<the RAW request value>). A reporter
    //       submitting reporterType:"anonymous" was recorded is_anonymous = FALSE, and the branch in
    //       submitCase then PERSISTED their name, email, phone and employee id. Keeping the
    //       reporter's identity out of the record is the one thing this module exists to do.
    //   (b) `safeSeverity`'s switch missed 'critical' and returned MEDIUM — a silent downgrade of a
    //       fraud or corruption report's triage band, discarding the reporter's own assessment.
    //
    // 🔑 The two do-nothing helpers are the `safeRole(String role) { return role; }` shape F2-175
    // deleted from MlModelDriftService: a method named as though it validates, which returns its
    // argument unchanged. Second instance in this program, in a different module — **the name is the
    // tell, not the module**, so `safe*` is worth grepping for.
    //
    // ⚙ Why the vocabularies are declared here as data rather than checked inline: ClosedVocabulary
    // carries the CHECK constraint's name, so ClosedVocabularyPinTest reads the migration SQL and
    // fails the build if the two ever disagree. A bare Set.of(...) is a claim about the schema that
    // nothing verifies, which is how AttachmentAccessPolicy's role list drifted three times.

    static final ClosedVocabulary REPORTER_TYPE = ClosedVocabulary.of(
        "reporterType", "CK_wb_reporter_type",
        "EMPLOYEE", "MANAGER", "CONTRACTOR", "VENDOR", "CUSTOMER", "EXTERNAL", "ANONYMOUS");

    static final ClosedVocabulary SUBMISSION_CHANNEL = ClosedVocabulary.of(
        "submissionChannel", "CK_wb_channel",
        "PUBLIC_PORTAL", "INTERNAL_PORTAL", "EMAIL", "PHONE", "IN_PERSON", "EXTERNAL_PARTNER");

    static final ClosedVocabulary CATEGORY = ClosedVocabulary.of(
        "category", "CK_wb_category",
        "FRAUD", "CORRUPTION", "CONFLICT_OF_INTEREST", "HARASSMENT", "DISCRIMINATION", "SAFETY",
        "ENVIRONMENT", "PRIVACY_VIOLATION", "FINANCIAL_MISCONDUCT", "IT_SECURITY",
        "REGULATORY_NON_COMPLIANCE", "OTHER");

    static final ClosedVocabulary SEVERITY = ClosedVocabulary.of(
        "severity", "CK_wb_severity", "CRITICAL", "HIGH", "MEDIUM", "LOW");

    /** The reporter type meaning "do not keep my identity" — named because a branch turns on it. */
    static final String REPORTER_TYPE_ANONYMOUS = "ANONYMOUS";

    /** Assumed when a submitter who did not claim anonymity does not say what they are. */
    static final String REPORTER_TYPE_DEFAULT = "EXTERNAL";

    /** The retained fail-soft band — see {@link ClosedVocabulary#coerce} for why it is not a 400. */
    static final String SEVERITY_DEFAULT = "MEDIUM";

    private final WhistleblowerCaseRepository caseRepo;
    private final WhistleblowerCaseUpdateRepository updateRepo;
    private final AuditLogService audit;
    private final AuditLogRepository auditRepo;
    private final ApplicationEventPublisher publisher;
    private final PublicPortalTenantResolver publicTenant;

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
     * Public-portal submission. No tenant context required — but the tenant must be
     * <i>named</i>: see {@link PublicPortalTenantResolver}. An unresolvable tenant is a 404, never
     * org 1 (F2-83).
     *
     * <p>This path is the ONLY one that uses {@link #anonymousAudit}: no
     * IP, no user-agent, no source endpoint written to the audit ledger.
     *
     * @param orgShortCode the tenant's public short code from the portal link's {@code ?org=};
     *                     ignored when a logged-in employee files through the same form
     */
    @Transactional
    public CaseSubmissionResponse submitCase(CaseSubmissionRequest req, String orgShortCode) {
        Long orgId = publicTenant.resolve(orgShortCode);

        WhistleblowerCase c = new WhistleblowerCase();
        c.setOrganisationId(orgId);
        c.setCaseCode(generateCode(orgId));
        c.setSubmissionToken(UUID.randomUUID());
        c.setSubmittedAt(LocalDateTime.now());
        // F2-179 — normalise the reporter type ONCE, then let the anonymity decision read that
        // single normalised value. The defect was that the stored value and the value the branch
        // below tested were different expressions of the same field: one had been through
        // safeReporterType, the other was req.reporterType() raw.
        boolean claimedAnonymous = Boolean.TRUE.equals(req.isAnonymous());
        String reporterType = REPORTER_TYPE.orDefault(req.reporterType(),
                claimedAnonymous ? REPORTER_TYPE_ANONYMOUS : REPORTER_TYPE_DEFAULT);

        c.setSubmissionChannel(SUBMISSION_CHANNEL.orDefault(req.submissionChannel(), "PUBLIC_PORTAL"));
        c.setReporterType(reporterType);
        c.setIsAnonymous(claimedAnonymous || REPORTER_TYPE_ANONYMOUS.equals(reporterType));
        if (!Boolean.TRUE.equals(c.getIsAnonymous())) {
            c.setReporterName(req.reporterName());
            c.setReporterEmail(req.reporterEmail());
            c.setReporterPhone(req.reporterPhone());
            c.setReporterEmployeeId(req.reporterEmployeeId());
        }
        c.setCategory(CATEGORY.require(req.category()));
        // coerce, not require: an unrecognised severity used to be accepted as MEDIUM, and a report
        // that never arrives is worse than one triaged at the wrong band. Casing is what is fixed.
        c.setSeverity(SEVERITY.coerce(req.severity(), SEVERITY_DEFAULT));
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
            // Same coerce-not-require reasoning as submitCase: this re-grades an existing case, and
            // an officer's 'critical' must land on CRITICAL rather than being read as unrecognised
            // and reset to MEDIUM — which is what the old case-sensitive switch did.
            c.setSeverity(SEVERITY.coerce(req.severity(), SEVERITY_DEFAULT));
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
     *
     * <p>F2-54: it also stamps {@code user_role_source = SUPPRESSED_BY_DESIGN}. The NULLs above
     * are a deliberate protection, and until now they were indistinguishable in the ledger from
     * the platform simply failing to record an actor — so the one place that withholds identity
     * on purpose looked exactly like a gap. This is the second of the two writers of
     * {@code platform.audit_log}; the other is {@code AuditLogService.recordEvent}.
     */
    void anonymousAudit(Long orgId, String actionType, Long entityId, String message) {
        AuditLogEntry e = new AuditLogEntry();
        e.setOrganisationId(orgId);
        e.setActionType(actionType);
        e.setEntityType("WhistleblowerCase");
        e.setEntityId(entityId);
        // ⚠⚠ F2-324 — the module, not `ethics.whistleblower`. This writer bypasses
        // recordEvent by design (see the javadoc above), so it gets no derived value and
        // must name the module itself. A dotted module.subpackage token was a third
        // vocabulary in a column whose two readers both match it with a single `=`.
        e.setSourceModule("ethics");
        e.setMessage(message);
        e.setSeverity("INFO");
        // Explicitly leave ip_address / user_agent / source_endpoint /
        // user_username / user_role NULL — see service Javadoc — and SAY SO in the row.
        e.setUserRoleSource(AuditLogService.SRC_SUPPRESSED);
        auditRepo.save(e);
        log.debug("[audit-anon] org={} {} WhistleblowerCase#{} (ip-suppressed)",
                orgId, actionType, entityId);
    }
}
