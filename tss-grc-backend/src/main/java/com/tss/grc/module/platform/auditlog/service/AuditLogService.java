package com.tss.grc.module.platform.auditlog.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Platform audit-log service — Framework Intersection F7.
 *
 * <p>Two paths into the log:
 * <ul>
 *   <li>The full builder: {@link #recordEvent(AuditEventBuilder)} for
 *       callers that want to set every field (workflow_status_before
 *       / _after, correlation id, payload diff, custom severity).</li>
 *   <li>The convenience {@link #log(String, String, Long, String)}
 *       for one-liner audit at the end of a service method.</li>
 * </ul>
 *
 * <p>HTTP context (ip/user_agent/endpoint) and Spring Security user
 * info are pulled automatically. TenantContext gives the
 * organisation_id. Callers never have to remember those fields.
 *
 * <h3>F2-54 — {@code user_role} records a role, and says how it knows</h3>
 *
 * <p>Until 2026-07-31 this class filled the column with
 * {@code auth.getAuthorities().iterator().next().getAuthority()}. The authority
 * collection is a {@link java.util.HashSet} holding <em>both</em> {@code ROLE_*} role
 * authorities and bare {@code MODULE_ACTION} permission authorities, so
 * {@code iterator().next()} took an arbitrary element of a hash set. Measured across
 * both databases: <b>16 distinct values, of which ZERO were role names</b> — 13 were
 * permissions ({@code BCM_APPROVE} alone on 1,665 rows) and 3 were {@code ROLE_}-prefixed.
 * It was also non-deterministic: {@code admin} appeared under <b>six</b> different
 * values, {@code so_officer} under four.
 *
 * <p>That matters more than a mislabelled column. This platform sells a
 * SAMA/NCA/CMA-grade audit trail, and an examiner reconciling "who did this" against
 * the role matrix got an answer that was not merely wrong but <em>differently wrong
 * each time</em>.
 *
 * <p>Now: {@link #resolveRoles} keeps only {@code ROLE_}-prefixed authorities, strips the
 * prefix, de-duplicates and <b>sorts</b> them — so a multi-role principal records its whole
 * set, deterministically, and a permission can never appear. Every row also carries
 * {@code user_role_source} saying how the value was obtained, because "no role recorded" and
 * "role deliberately not recorded" are different facts an auditor must be able to tell apart:
 *
 * <ul>
 *   <li>{@link #SRC_RESOLVED} — the actor's role set as at the action.</li>
 *   <li>{@link #SRC_RESOLVED_TRUNCATED} — as above but longer than the column; truncation is
 *       labelled rather than silent, which is the whole point of the finding.</li>
 *   <li>{@link #SRC_NO_ROLE_AUTHORITY} — authenticated, but the principal holds no
 *       {@code ROLE_*} authority at all (a machine/token identity carrying only permissions).</li>
 *   <li>{@link #SRC_NO_PRINCIPAL} — no authenticated principal: schedulers and off-request work.</li>
 *   <li>{@link #SRC_SUPPRESSED} — deliberately not recorded. Whistleblower reporting suppresses
 *       actor identity by design, and this label distinguishes that from a gap.</li>
 *   <li>{@link #SRC_LEGACY_AUTHORITY_SAMPLE} — set only by V339, on pre-fix rows that carry a
 *       value. <b>That value is an arbitrary authority, usually a permission; do not reconcile
 *       it against the role matrix.</b></li>
 *   <li>{@link #SRC_LEGACY_UNRECORDED} — set only by V339, on pre-fix rows with no value at all.
 *       The old code could not distinguish "no principal" from "principal with no authorities",
 *       so neither can this label, and it says so rather than guessing one.</li>
 * </ul>
 *
 * <p><b>History is labelled, never rewritten.</b> A permission does not determine which role
 * the actor held, so back-filling real roles would be fabrication — and an audit ledger that
 * edits its own past to look clean is worth less than one that admits a gap. This follows the
 * V322 precedent ({@code tally_source}: label every row, flip none).
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private static final Logger log = LoggerFactory.getLogger(AuditLogService.class);

    private static final Set<String> SEVERITIES = Set.of("INFO", "WARN", "ERROR");

    /** F2-324 — the platform's own package root; anything else on the stack is a proxy or the JDK. */
    private static final String PLATFORM_ROOT = "com.tss.grc.";
    /** F2-324 — a top-level package under this root IS a module (see {@code module-registry.yaml}). */
    private static final String MODULE_ROOT = "com.tss.grc.module.";

    // ─── F2-54: user_role provenance ──────────────────────────────
    /** {@code platform.audit_log.user_role} is NVARCHAR(60) — see V95. */
    static final int USER_ROLE_MAX = 60;

    /** Spring Security's role-authority prefix. Anything without it is a permission, not a role. */
    private static final String ROLE_PREFIX = "ROLE_";

    /** The actor's role set as at the action. */
    public static final String SRC_RESOLVED             = "RESOLVED";
    /** As {@link #SRC_RESOLVED}, but the set did not fit the column — truncation is labelled. */
    public static final String SRC_RESOLVED_TRUNCATED   = "RESOLVED_TRUNCATED";
    /** Authenticated, but the principal holds no {@code ROLE_*} authority (permissions only). */
    public static final String SRC_NO_ROLE_AUTHORITY    = "NO_ROLE_AUTHORITY";
    /** No authenticated principal: schedulers, async listeners, off-request work. */
    public static final String SRC_NO_PRINCIPAL         = "NO_PRINCIPAL";
    /** Deliberately not recorded — whistleblower reporting suppresses actor identity by design. */
    public static final String SRC_SUPPRESSED           = "SUPPRESSED_BY_DESIGN";
    /** V339 only: a pre-fix row whose value is an arbitrary authority (usually a permission). */
    public static final String SRC_LEGACY_AUTHORITY_SAMPLE = "LEGACY_AUTHORITY_SAMPLE";
    /** V339 only: a pre-fix row with no value, cause unrecoverable. */
    public static final String SRC_LEGACY_UNRECORDED    = "LEGACY_UNRECORDED";

    /**
     * Every label this column may hold. Pinned by test rather than by a DB CHECK — a CHECK on a
     * value-set the code keeps extending is the {@code ck_ca_source} landmine, and here a
     * rejected INSERT loses an audit row.
     */
    public static final Set<String> USER_ROLE_SOURCES = Set.of(
        SRC_RESOLVED, SRC_RESOLVED_TRUNCATED, SRC_NO_ROLE_AUTHORITY, SRC_NO_PRINCIPAL,
        SRC_SUPPRESSED, SRC_LEGACY_AUTHORITY_SAMPLE, SRC_LEGACY_UNRECORDED);

    private final AuditLogRepository repo;
    /** Optional: HttpServletRequest is only present inside an HTTP call. */
    private final ObjectProvider<HttpServletRequest> requestProvider;

    /** Convenience: log a simple action. */
    public AuditLogEntry log(String actionType, String entityType, Long entityId, String message) {
        return recordEvent(new AuditEventBuilder()
            .actionType(actionType)
            .entityType(entityType)
            .entityId(entityId)
            .message(message));
    }

    /**
     * ⚠⚠ F2-320 — {@link #log} for an event that belongs to <b>no tenant</b>.
     *
     * <p>Identical to {@code log(...)} except that it declares the event org-less, so filing it under
     * organisation 1 is the intended behaviour rather than a lost {@code TenantContext}. Exactly one
     * caller qualifies today: {@code DbSizeMonitorJob}, which audits the size of the database itself.
     *
     * <p>🔑 It exists because four off-request writers looked identical from in here and were
     * defects ([[F2-317]], [[F2-318]], [[F2-319]]) — without a way to say "no tenant", that case and
     * "lost the tenant" are the same call, which is what made all four silent.
     */
    @Transactional
    public AuditLogEntry logSystem(String actionType, String entityType, Long entityId,
                                   String message) {
        return recordEvent(new AuditEventBuilder()
            .actionType(actionType)
            .entityType(entityType)
            .entityId(entityId)
            .systemEvent()
            .message(message));
    }

    /**
     * ⚠⚠ F2-321 — the first frame outside this class, for the F2-320 warning.
     *
     * <p>{@code sourceModule} was the obvious thing to print and is <b>null on 3,524 of the 3,809</b>
     * off-request rows in dev, because the {@code log(...)} convenience method never sets it. A
     * warning naming nothing locatable is worse than none: it looks informative, so the reader stops.
     *
     * <p>⚠ {@code StackWalker} is used rather than {@code new Throwable().getStackTrace()} because it
     * does not materialise the whole trace — this runs on every unanchored audit write.
     */
    private static String callerOf() {
        String c = callerFrame();
        return c == null ? "unknown caller" : c;
    }

    /**
     * ⚠⚠ F2-324 — the first <b>platform</b> frame outside this class, shared by {@link #callerOf}
     * and {@link #callerModule}.
     *
     * <p>F2-321's version took the first frame outside this class by exact class-name equality. That
     * is not the same thing: {@link #recordEvent} is {@code @Transactional}, so this bean is
     * CGLIB-proxied, and the frames between the module caller and here include the generated
     * subclass — whose class name is {@code …AuditLogService$$SpringCGLIB$$0}, which is <b>not equal
     * to</b> {@code AuditLogService.class.getName()} — plus Spring's AOP interceptors. So the naive
     * filter could return Spring's proxy machinery, which is a diagnostic naming nothing locatable:
     * exactly the defect F2-321 was written to remove, one mechanism down.
     *
     * <p>🔑 Asking for the first {@code com.tss.grc} frame that is not this class is robust to the
     * proxy, to reflection and to any future advice <b>by construction</b>, rather than by listing
     * the frames observed today ([[F2-217]]: a filter keyed on a list measures the list).
     *
     * <p>⚠ {@code StackWalker} rather than {@code new Throwable().getStackTrace()} because it does
     * not materialise the whole trace — this runs on every audit write, not only the unanchored ones.
     */
    private static String callerFrame() {
        String self = AuditLogService.class.getName();
        return StackWalker.getInstance()
            .walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .filter(c -> c.startsWith(PLATFORM_ROOT) && !c.startsWith(self))
                .findFirst()
                .orElse(null));
    }

    /**
     * ⚠⚠ F2-324 — the module that wrote this row, derived from the caller's package.
     *
     * <p>{@code source_module} is read by two things — the {@code sourceModule} filter on
     * {@code GET /api/v1/audit-log}, whose control is a free-text box on a sidebar-linked page whose
     * own subtitle promises <em>"Every state-changing action across modules — filterable and
     * traceable"</em>, and {@code BcmEvidencePackageService}, which selects the BCM evidence
     * package's {@code audit_log.csv} with it. It was NULL on <b>87.1% of dev rows and 93.6% of prod
     * rows</b>, because {@link #log} constructs its builder internally and never set it: <b>686 of
     * the 722 audit-writing sites</b> could not attribute themselves however carefully they were
     * written.
     *
     * <p>🔑 The vocabulary is not invented and is not a list: a top-level package under
     * {@code com.tss.grc.module} <b>is</b> a module, enumerated exactly once by
     * {@code module-registry.yaml} and gated by {@code ModuleRegistryContractTest}. So the derivation
     * is correct by construction and {@code AuditSourceModuleContractTest} is what ties it to the
     * registry — there is no runtime lookup to go stale.
     *
     * <p>⚠ Returns null for a caller outside {@code com.tss.grc.module} — {@code config},
     * {@code security}, a scheduler in {@code common} — because those are not modules and inventing
     * a token for them would put a fifth spelling in a column that already had four. An explicit
     * {@code sourceModule(…)} on the builder always wins; this only fills the gap.
     */
    static String callerModule() {
        String c = callerFrame();
        if (c == null || !c.startsWith(MODULE_ROOT)) return null;
        int dot = c.indexOf('.', MODULE_ROOT.length());
        String seg = dot < 0 ? c.substring(MODULE_ROOT.length()) : c.substring(MODULE_ROOT.length(), dot);
        return seg.isBlank() ? null : seg;
    }

    /** Full builder path. */
    @Transactional
    public AuditLogEntry recordEvent(AuditEventBuilder b) {
        AuditLogEntry e = new AuditLogEntry();
        Long org = TenantContext.getCurrentTenant();
        if (org == null && !b.systemEvent) {
            // ⚠⚠ F2-320 — this fallback is correct for exactly ONE caller and was silently wrong
            // for four. F2-317, F2-318 and F2-319 each found an off-request writer whose rows all
            // landed in organisation 1: the risk-review sweep, the hourly IAM access-expiry sweep,
            // the nightly SoD scan, the vuln-SLA escalation and the @Async automation engine. Every
            // one of them had the organisation in hand — three had put it in the audit MESSAGE
            // because the row's own column was not carrying it.
            //
            // 🔑 The fallback is not the defect; being SILENT is. A writer that means "this event
            // belongs to no tenant" now says so with systemEvent(), and one that has simply lost its
            // tenant produces a log line naming the action type instead of a plausible row in
            // organisation 1's audit log.
            //
            // ⚠ A WARN rather than a throw, deliberately: off-request audit paths cannot be
            // enumerated statically (listener registration, reflection, SpEL), so refusing would
            // risk breaking a path nobody has seen — the one failure a fix must not have. The
            // default is unchanged; only its visibility is.
            // ⚠⚠ F2-321 — the CALLER is named, not the source module. F2-320's first version
            // printed `b.sourceModule`, and measured over the live rows that is NULL on 3,524 of the
            // 3,809 off-request entries in dev, because the `log(...)` convenience method never sets
            // it and that is what almost every writer uses. So the warning would have read
            // "module null" for 92% of the population it exists to surface — an artefact that looks
            // informative and is not, which is the shape this whole chain is about.
            log.warn("[audit] no tenant in context for action {} from {} — filing under organisation "
                     + "1. If this event genuinely belongs to no tenant, say so with "
                     + "AuditLogService.logSystem(...) or AuditEventBuilder.systemEvent(); otherwise "
                     + "anchor the tenant on the row it describes (F2-317/318/319).",
                     b.actionType, callerOf());
        }
        e.setOrganisationId(org != null ? org : 1L);

        // Required
        if (b.actionType == null || b.actionType.isBlank())
            throw new IllegalArgumentException("audit action_type is required");
        e.setActionType(b.actionType);

        e.setEntityType(b.entityType);
        e.setEntityId(b.entityId);
        e.setEntityExternalId(b.entityExternalId);
        e.setEntityCode(b.entityCode);
        e.setOldValueJson(b.oldValueJson);
        e.setNewValueJson(b.newValueJson);
        e.setWorkflowStatusBefore(b.workflowStatusBefore);
        e.setWorkflowStatusAfter(b.workflowStatusAfter);
        // ⚠⚠ F2-324 — derive when the builder did not say. An explicit value wins; this is what
        // makes the 686 log(...) sites attributable without editing any of them, which is the only
        // way a column with two readers gets filled at all.
        e.setSourceModule(b.sourceModule != null ? b.sourceModule : callerModule());
        e.setCorrelationId(b.correlationId);
        e.setParentAuditId(b.parentAuditId);
        e.setDigitalSignatureRef(b.digitalSignatureRef);
        e.setMessage(b.message);
        // Usually null; a caller may declare provenance (see SRC_SUPPRESSED). Validated, because a
        // free-text provenance column is a column that tells the reader nothing.
        if (b.userRoleSource != null && !USER_ROLE_SOURCES.contains(b.userRoleSource))
            throw new IllegalArgumentException(
                "user_role_source must be one of " + USER_ROLE_SOURCES + ", got: " + b.userRoleSource);
        e.setUserRoleSource(b.userRoleSource);

        if (b.severity != null) {
            if (!SEVERITIES.contains(b.severity))
                throw new IllegalArgumentException("severity must be INFO/WARN/ERROR");
            e.setSeverity(b.severity);
        }

        // Auto-fill from HTTP + security if available
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (e.getUserRoleSource() == null) {   // a caller may have declared it (see SRC_SUPPRESSED)
            if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
                e.setUserUsername(auth.getName());
                String roles = resolveRoles(auth);
                if (roles.isEmpty()) {
                    e.setUserRoleSource(SRC_NO_ROLE_AUTHORITY);
                } else if (roles.length() > USER_ROLE_MAX) {
                    e.setUserRole(roles.substring(0, USER_ROLE_MAX - 1) + "…");
                    e.setUserRoleSource(SRC_RESOLVED_TRUNCATED);
                } else {
                    e.setUserRole(roles);
                    e.setUserRoleSource(SRC_RESOLVED);
                }
            } else {
                e.setUserRoleSource(SRC_NO_PRINCIPAL);
            }
        }
        HttpServletRequest req = currentRequestOrNull();
        if (req != null) {
            e.setIpAddress(extractIp(req));
            e.setUserAgent(truncate(req.getHeader("User-Agent"), 500));
            e.setSourceEndpoint(req.getMethod() + " " + req.getRequestURI());
        }

        AuditLogEntry saved = repo.save(e);
        log.debug("[audit] org={} {} {}#{} ({})",
            saved.getOrganisationId(), saved.getActionType(),
            saved.getEntityType(), saved.getEntityId(), saved.getSeverity());
        return saved;
    }

    /**
     * The actor's ROLES, comma-separated, {@code ROLE_} stripped, de-duplicated and
     * <b>sorted</b> — or {@code ""} when the principal holds no role authority.
     *
     * <p>Three properties, each of which was a defect before F2-54:
     * <ol>
     *   <li><b>Roles only.</b> The authority collection mixes {@code ROLE_*} roles with bare
     *       {@code MODULE_ACTION} permissions; the old code took {@code iterator().next()} of it.
     *       Measured on both databases, that produced <b>16 distinct values and not one role
     *       name</b> — {@code BCM_APPROVE} alone on 1,665 dev rows.</li>
     *   <li><b>The whole set, not a sample.</b> {@code so_officer} really does hold two roles
     *       (CISO + SUSTAINABILITY_OFFICER) and the four-eyes flows depend on which one acted, so
     *       recording one of them is a lossy answer to the only question an examiner asks.</li>
     *   <li><b>Sorted, therefore deterministic.</b> The source is a {@link java.util.HashSet}, so
     *       without an ordering the same principal records a different string run to run —
     *       {@code admin} appeared under six values. That is worse than being wrong once: it
     *       defeats reconciliation, because two rows that disagree are indistinguishable from a
     *       role change between them.</li>
     * </ol>
     */
    static String resolveRoles(Authentication auth) {
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .filter(a -> a != null && a.startsWith(ROLE_PREFIX))
            .map(a -> a.substring(ROLE_PREFIX.length()))
            .filter(a -> !a.isBlank())
            .distinct()
            .sorted()
            .collect(Collectors.joining(","));
    }

    /**
     * The current HTTP request, or {@code null} when none is bound to this thread
     * (scheduler / async / event-listener execution).
     *
     * <p>The guard MUST be {@link RequestContextHolder#getRequestAttributes()}
     * (which returns {@code null} off-request, never throwing) — checked
     * <em>before</em> touching the request-scoped {@code HttpServletRequest}.
     * {@code ObjectProvider.getIfAvailable()} returns the request-scoped
     * <em>proxy</em> (non-null, no exception) even off-request; the "No
     * thread-bound request found" {@link IllegalStateException} only fires later
     * when a method is invoked on that proxy (e.g. in {@code extractIp}). A
     * try/catch around {@code getIfAvailable()} therefore never triggers — which
     * is why every async/scheduled audit write (automation rule engine, SocOps /
     * BCM / compliance-deadline sweeps) was still aborting. Returning {@code null}
     * here skips request-metadata capture entirely off-request.
     */
    private HttpServletRequest currentRequestOrNull() {
        if (RequestContextHolder.getRequestAttributes() == null) return null;
        try {
            return requestProvider.getIfAvailable();
        } catch (RuntimeException ex) {
            return null;
        }
    }

    // ─── Queries ──────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<AuditLogEntry> list(String actionType, String sourceModule, String severity, int page, int size) {
        Long org = TenantContext.getCurrentTenant();
        Pageable pageable = PageRequest.of(page, Math.min(size, 200));
        if (actionType != null && !actionType.isBlank())
            return repo.findByOrganisationIdAndActionTypeOrderByCreatedAtDesc(org, actionType, pageable);
        if (sourceModule != null && !sourceModule.isBlank())
            return repo.findByOrganisationIdAndSourceModuleOrderByCreatedAtDesc(org, sourceModule, pageable);
        if (severity != null && !severity.isBlank())
            return repo.findByOrganisationIdAndSeverityOrderByCreatedAtDesc(org, severity, pageable);
        return repo.findByOrganisationIdOrderByCreatedAtDesc(org, pageable);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> historyFor(String entityType, Long entityId) {
        return repo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            TenantContext.getCurrentTenant(), entityType, entityId);
    }

    @Transactional(readOnly = true)
    public List<AuditLogEntry> trace(String correlationId) {
        return repo.findByOrganisationIdAndCorrelationIdOrderByCreatedAtAsc(
            TenantContext.getCurrentTenant(), correlationId);
    }

    /** Lightweight stats for the admin dashboard widget. */
    @Transactional(readOnly = true)
    public Map<String, Long> stats24h() {
        Long org = TenantContext.getCurrentTenant();
        LocalDateTime cutoff = LocalDateTime.now().minusHours(24);
        return Map.of(
            "events",  repo.countByOrganisationIdAndCreatedAtAfter(org, cutoff),
            "warns",   repo.countByOrganisationIdAndSeverityAndCreatedAtAfter(org, "WARN",  cutoff),
            "errors",  repo.countByOrganisationIdAndSeverityAndCreatedAtAfter(org, "ERROR", cutoff)
        );
    }

    /** Generate a stable correlation id for a multi-step business action. */
    public static String newCorrelationId() { return UUID.randomUUID().toString(); }

    // ─── Helpers ──────────────────────────────────────────────────

    private static String extractIp(HttpServletRequest req) {
        String xff = req.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return truncate((comma < 0 ? xff : xff.substring(0, comma)).trim(), 60);
        }
        return truncate(req.getRemoteAddr(), 60);
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ─── Builder (compact API) ────────────────────────────────────

    public static class AuditEventBuilder {
        boolean systemEvent;
        String actionType, entityType, entityExternalId, entityCode;
        Long entityId, parentAuditId;
        String oldValueJson, newValueJson;
        String workflowStatusBefore, workflowStatusAfter;
        String sourceModule, correlationId, digitalSignatureRef;
        String severity, message, userRoleSource;

        /**
      * ⚠⚠ F2-320 — declare that this event belongs to <b>no tenant</b>, so filing it under
      * organisation 1 is the intended behaviour rather than a lost {@code TenantContext}.
      *
      * <p>Exactly one caller qualifies today: {@code DbSizeMonitorJob}, which audits the size of the
      * database itself — {@code entityType Database}, null entity id. Four other off-request writers
      * looked identical from here and were defects ([[F2-317]], [[F2-318]], [[F2-319]]), which is the
      * whole reason this is opt-in: without it, "no tenant" and "lost the tenant" are the same call.
      */
        public AuditEventBuilder systemEvent() { this.systemEvent = true; return this; }

        public AuditEventBuilder actionType(String v) { this.actionType = v; return this; }
        public AuditEventBuilder entityType(String v) { this.entityType = v; return this; }
        public AuditEventBuilder entityId(Long v)     { this.entityId = v; return this; }
        public AuditEventBuilder entityExternalId(String v) { this.entityExternalId = v; return this; }
        public AuditEventBuilder entityCode(String v) { this.entityCode = v; return this; }
        public AuditEventBuilder oldValueJson(String v) { this.oldValueJson = v; return this; }
        public AuditEventBuilder newValueJson(String v) { this.newValueJson = v; return this; }
        public AuditEventBuilder workflowStatusBefore(String v) { this.workflowStatusBefore = v; return this; }
        public AuditEventBuilder workflowStatusAfter(String v)  { this.workflowStatusAfter = v; return this; }
        public AuditEventBuilder sourceModule(String v) { this.sourceModule = v; return this; }
        public AuditEventBuilder correlationId(String v) { this.correlationId = v; return this; }
        public AuditEventBuilder parentAuditId(Long v) { this.parentAuditId = v; return this; }
        public AuditEventBuilder digitalSignatureRef(String v) { this.digitalSignatureRef = v; return this; }
        public AuditEventBuilder severity(String v) { this.severity = v; return this; }
        public AuditEventBuilder message(String v) { this.message = v; return this; }

        /**
         * Declare how {@code user_role} was obtained, suppressing the automatic resolution.
         * The only current use is {@link AuditLogService#SRC_SUPPRESSED} — a caller asserting
         * that actor identity is withheld <em>by design</em>, which an auditor must be able to
         * tell apart from a gap. Anything not in {@link AuditLogService#USER_ROLE_SOURCES} is
         * rejected, so this cannot become a free-text field.
         */
        public AuditEventBuilder userRoleSource(String v) { this.userRoleSource = v; return this; }
    }
}
