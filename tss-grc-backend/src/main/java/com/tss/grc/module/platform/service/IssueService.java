package com.tss.grc.module.platform.service;

import com.tss.grc.common.exception.IllegalTransitionException;
import com.tss.grc.common.validation.ServerOwnedState;
import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.entity.Issue;
import com.tss.grc.module.platform.repository.IssueRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Issue tracker — generic cross-module finding lifecycle.
 *
 * <p>Lifecycle: {@code OPEN → IN_PROGRESS → (BLOCKED →)? RESOLVED → CLOSED}
 * with {@code OPEN → CANCELLED} as the only short-circuit. Transitions are
 * validated by {@link #transition(Long, String)} — bypass via {@link #update}
 * is rejected to keep the state machine inspectable.
 *
 * <p>Resolution and close timestamps stamp themselves automatically on the
 * matching transition. {@code resolution} is required when moving to
 * {@code RESOLVED} or {@code CLOSED}; lifecycle codes are limited to the
 * fixed set in {@link #RESOLUTIONS}.
 */
@Service
@RequiredArgsConstructor
public class IssueService {

    private static final Set<String> STATUSES = Set.of(
        "OPEN", "IN_PROGRESS", "BLOCKED", "RESOLVED", "CLOSED", "CANCELLED");
    /** F2-220 — the state an issue is CREATED in. update() already refuses to change status; the
     *  create did not. F2-297 moved that refusal onto {@code ServerOwnedState.requireUnchanged},
     *  so it no longer says "Use transition() to change status" — it names the route. */
    private static final String INITIAL_STATUS = "OPEN";
    private static final Set<String> SEVERITIES = Set.of("LOW", "MEDIUM", "HIGH", "CRITICAL");
    private static final Set<String> PRIORITIES = Set.of("LOW", "NORMAL", "HIGH", "URGENT");
    private static final Set<String> RESOLUTIONS = Set.of(
        "FIXED", "WONT_FIX", "DUPLICATE", "NOT_REPRODUCIBLE", "DEFERRED");

    private final IssueRepository repo;
    private final AuditLogService audit;
    private final ApplicationEventPublisher publisher;

    // ── F8 emit ───────────────────────────────────────────────────────────────
    // Issues are the cross-module finding lifecycle — they fan into CAPA,
    // evidence, and notifications. F8 events let downstream consumers
    // (assignee inbox, manager escalation, audit committee KPI) react
    // automatically.
    private Map<String, Object> payloadFor(Issue i, String previousStatus) {
        Map<String, Object> m = new HashMap<>();
        m.put("issueId",         nullSafeStr(i.getId()));
        m.put("issueCode",       nullSafeStr(i.getIssueCode()));
        m.put("status",          nullSafeStr(i.getStatus()));
        m.put("previousStatus",  nullSafeStr(previousStatus));
        m.put("severity",        nullSafeStr(i.getSeverity()));
        m.put("priority",        nullSafeStr(i.getPriority()));
        m.put("resolution",      nullSafeStr(i.getResolution()));
        m.put("assigneeUserId",  nullSafeStr(i.getAssigneeUserId()));
        m.put("assigneeRole",    nullSafeStr(i.getAssigneeRole()));
        m.put("capaId",          nullSafeStr(i.getCapaId()));
        return m;
    }
    private static String nullSafeStr(Object o) { return o == null ? "" : o.toString(); }

    private void emit(String trigger, Issue i, String previousStatus) {
        publisher.publishEvent(new DomainEvent(
            TenantContext.getCurrentTenant(), trigger, "Issue", i.getId(),
            "issue:" + nullSafeStr(i.getIssueCode()), payloadFor(i, previousStatus)));
    }

    @Transactional(readOnly = true)
    public Page<Issue> list(int page, int size) {
        return repo.findByOrganisationIdOrderByIdDesc(
            TenantContext.getCurrentTenant(), PageRequest.of(page, size));
    }

    @Transactional(readOnly = true)
    public Issue get(Long id) {
        return repo.findByIdAndOrganisationId(id, TenantContext.getCurrentTenant())
            .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + id));
    }

    @Transactional
    public Issue create(Issue body) {
        validateBase(body);
        body.setId(null);
        body.setOrganisationId(TenantContext.getCurrentTenant());
        // F2-220 — update() has refused to change status since this class was written ("Use
        // transition() to change status", and the class javadoc says that is to keep the state
        // machine inspectable). The create did not, and had no vocabulary check either — and
        // platform.issues carries no CHECK constraints at all, so an arbitrary string was
        // storable and transition()'s `default -> false` then made the row permanently
        // untransitionable. Closing an issue through here also skipped the mandatory resolution
        // code and both timestamps.
        body.setStatus(ServerOwnedState.requireInitial(
            body.getStatus(), INITIAL_STATUS, "an issue",
            "POST /api/v1/issues/{id}/transition"));
        body.setResolution(null);
        body.setResolvedAt(null);
        body.setClosedAt(null);
        // F2-238 — severity and priority were field initialisers, so update()'s vocabulary-checked
        // guards could never be false and every PATCH reset them to MEDIUM/NORMAL: measured 8 of 13
        // and 12 of 13 live rows in both databases, including the CRITICAL/URGENT issues CcmService
        // and KriBreachService open on a third consecutive control-test failure and a sustained-RED
        // KRI. Both producers set the pair explicitly, so this default only serves a bare API create.
        if (body.getSeverity() == null) body.setSeverity("MEDIUM");
        if (body.getPriority() == null) body.setPriority("NORMAL");
        if (body.getIssueCode() == null || body.getIssueCode().isBlank()) {
            body.setIssueCode(nextCode());
        }
        Issue saved = repo.save(body);
        audit.log("CREATE", "Issue", saved.getId(),
            "Issue created: " + saved.getIssueCode() + " — " + saved.getTitleEn());
        emit("ISSUE_CREATED", saved, null);
        return saved;
    }

    @Transactional
    public Issue update(Long id, Issue patch) {
        Issue existing = get(id);
        // F2-297 — one of four hand-rolled copies of the rule F2-296 shared; see BcpPlanService
        // #update for why the copy was wrong in three ways.
        ServerOwnedState.requireUnchanged(
            patch.getStatus(), existing.getStatus(), "status", "Issue " + id,
            "POST /api/v1/issues/" + id + "/transition, which walks the machine and emits the"
            + " lifecycle event for the move",
            null);
        if (patch.getTitleEn()          != null) existing.setTitleEn(patch.getTitleEn());
        if (patch.getTitleAr()          != null) existing.setTitleAr(patch.getTitleAr());
        if (patch.getDescription()      != null) existing.setDescription(patch.getDescription());
        if (patch.getSeverity()         != null) {
            if (!SEVERITIES.contains(patch.getSeverity()))
                throw new IllegalArgumentException("severity must be one of " + SEVERITIES);
            existing.setSeverity(patch.getSeverity());
        }
        if (patch.getPriority()         != null) {
            if (!PRIORITIES.contains(patch.getPriority()))
                throw new IllegalArgumentException("priority must be one of " + PRIORITIES);
            existing.setPriority(patch.getPriority());
        }
        if (patch.getAssigneeUserId()   != null) existing.setAssigneeUserId(patch.getAssigneeUserId());
        if (patch.getAssigneeRole()     != null) existing.setAssigneeRole(patch.getAssigneeRole());
        if (patch.getDueDate()          != null) existing.setDueDate(patch.getDueDate());
        if (patch.getCapaId()           != null) existing.setCapaId(patch.getCapaId());
        if (patch.getEvidenceId()       != null) existing.setEvidenceId(patch.getEvidenceId());

        Issue saved = repo.save(existing);
        audit.log("UPDATE", "Issue", saved.getId(),
            "Issue updated: " + saved.getIssueCode());
        return saved;
    }

    /**
     * State-machine transition. Stamps {@code resolvedAt}/{@code closedAt}
     * automatically and demands {@code resolution} be set on the patch when
     * moving to RESOLVED or CLOSED.
     */
    /**
     * The register's declared edges, in ONE place.
     *
     * <p>⚠⚠ F2-304 — this was an inline {@code switch}, and its refusal is the one that shows why
     * [[F2-303]]'s grep found five of eleven: the message sat on a <b>continuation line</b>
     * ({@code new IllegalArgumentException(} then {@code "Illegal transition: "}), so a pattern
     * anchored on {@code IllegalArgumentException("Illegal} could never match it. 🔑 <b>Wording was
     * only half the reason a single-line grep under-counted; a line break is the other half.</b>
     *
     * <p>Terminal states are declared with an empty set so the terminal set is derivable rather than
     * typed a second time ([[F2-196]]), and a refusal from one says {@code allowed: []}.
     */
    static final Map<String, Set<String>> TRANSITIONS = Map.of(
        "OPEN",        Set.of("IN_PROGRESS", "BLOCKED", "RESOLVED", "CANCELLED"),
        "IN_PROGRESS", Set.of("BLOCKED", "RESOLVED", "CANCELLED"),
        "BLOCKED",     Set.of("IN_PROGRESS", "RESOLVED", "CANCELLED"),
        "RESOLVED",    Set.of("CLOSED", "IN_PROGRESS"),
        "CLOSED",      Set.of(),
        "CANCELLED",   Set.of());

    @Transactional
    public Issue transition(Long id, String toStatus, String resolution) {
        Issue issue = get(id);
        if (toStatus == null || !STATUSES.contains(toStatus))
            throw new IllegalArgumentException("status must be one of " + STATUSES);

        String from = issue.getStatus();
        Set<String> allowed = TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(toStatus)) throw new IllegalTransitionException(from, toStatus, allowed);

        if ("RESOLVED".equals(toStatus) || "CLOSED".equals(toStatus)) {
            if (resolution == null || resolution.isBlank())
                throw new IllegalArgumentException("resolution is required when closing/resolving");
            if (!RESOLUTIONS.contains(resolution))
                throw new IllegalArgumentException("resolution must be one of " + RESOLUTIONS);
            issue.setResolution(resolution);
        }

        issue.setStatus(toStatus);
        LocalDateTime now = LocalDateTime.now();
        if ("RESOLVED".equals(toStatus)) {
            issue.setResolvedAt(now);
        } else if ("CLOSED".equals(toStatus)) {
            if (issue.getResolvedAt() == null) issue.setResolvedAt(now);
            issue.setClosedAt(now);
        }
        Issue saved = repo.save(issue);
        audit.log("TRANSITION", "Issue", saved.getId(),
            "Issue " + saved.getIssueCode() + " " + from + " → " + toStatus);
        String trigger = switch (toStatus) {
            case "IN_PROGRESS" -> "ISSUE_STARTED";
            case "BLOCKED"     -> "ISSUE_BLOCKED";
            case "RESOLVED"    -> "ISSUE_RESOLVED";
            case "CLOSED"      -> "ISSUE_CLOSED";
            case "CANCELLED"   -> "ISSUE_CANCELLED";
            default            -> "ISSUE_TRANSITIONED";
        };
        emit(trigger, saved, from);
        return saved;
    }

    @Transactional(readOnly = true)
    public Map<String, Long> stats() {
        Long org = TenantContext.getCurrentTenant();
        Map<String, Long> out = new HashMap<>();
        for (String s : STATUSES)   out.put("status_" + s.toLowerCase(),   repo.countByOrganisationIdAndStatus(org, s));
        for (String s : SEVERITIES) out.put("severity_" + s.toLowerCase(), repo.countByOrganisationIdAndSeverity(org, s));
        return out;
    }

    @Transactional(readOnly = true)
    public List<Issue> bySource(String sourceModule) {
        return repo.findByOrganisationIdAndSourceModuleOrderByIdDesc(
            TenantContext.getCurrentTenant(), sourceModule);
    }

    // ─── Helpers ──────────────────────────────────────────────────

    private void validateBase(Issue i) {
        if (i.getTitleEn() == null || i.getTitleEn().isBlank())
            throw new IllegalArgumentException("titleEn is required");
        if (i.getSourceModule() == null || i.getSourceModule().isBlank())
            throw new IllegalArgumentException("sourceModule is required");
        if (i.getSeverity() != null && !SEVERITIES.contains(i.getSeverity()))
            throw new IllegalArgumentException("severity must be one of " + SEVERITIES);
        if (i.getPriority() != null && !PRIORITIES.contains(i.getPriority()))
            throw new IllegalArgumentException("priority must be one of " + PRIORITIES);
    }

    /**
     * Generates an issue code in the form ISS-YYYY-NNNNNN. The counter is
     * derived from the existing row count for the year — cheap and good
     * enough for the issue-tracker scale. If two creates land in the same
     * millisecond and pick the same number, the unique index rejects the
     * second; the service does not retry by design (the caller can submit
     * again with a clearer title).
     */
    private String nextCode() {
        Long org = TenantContext.getCurrentTenant();
        int year = Year.now().getValue();
        long count = repo.findByOrganisationIdOrderByIdDesc(org, PageRequest.of(0, 1)).getTotalElements();
        return String.format("ISS-%d-%06d", year, count + 1);
    }
}
