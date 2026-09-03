package com.tss.grc.common.exception;

import com.tss.grc.module.platform.licensing.guard.ModuleDisabledException;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.NoResultException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Locale;
import java.net.URI;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * SEC-CFG-001 — when {@code false} (the default, and the value in every
     * non-dev profile) the catch-all handler returns a generic message plus a
     * correlation {@code reference} and OMITS the internal exception class name,
     * SQL object/schema names, and root-cause text from the client response.
     * The full detail is always written to the server log keyed by the same
     * reference. Set {@code grc.errors.verbose=true} only in dev so developers
     * still get the diagnostic surfaced in the toast.
     */
    @Value("${grc.errors.verbose:false}")
    private boolean verboseErrors;

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
            .collect(Collectors.toMap(FieldError::getField, fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid", (a, b) -> a));
        ProblemDetail pd = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/validation"));
        pd.setProperty("errors", errors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-30 — bean-validation failures raised by <em>Hibernate</em> rather than Spring MVC.
     *
     * <p>{@link #handleValidation} only sees {@code MethodArgumentNotValidException}, which
     * Spring MVC throws when a {@code @Valid @RequestBody} fails. A constraint on an
     * <em>entity</em> field is enforced somewhere else entirely: Hibernate's
     * {@code BeanValidationEventListener} runs it on pre-persist / pre-update and throws
     * {@link ConstraintViolationException} during flush, which Spring's transaction
     * interceptor then wraps in {@link TransactionSystemException} at commit. Neither was
     * handled, so <b>every entity-level constraint in the platform surfaced as a bare 500</b>
     * — measured before this handler existed, with no entity carrying a single
     * {@code @NotBlank}.
     *
     * <p>That mattered for F2-30 specifically. `CLAUDE.md` house rule 7 says Arabic names are
     * backend-mandatory; enforcing it needs constraints on {@code *_ar} fields, and the write
     * paths that most need it bind the JPA entity straight from the request body with no DTO
     * and no {@code @Valid} (`create(@RequestBody ChangeRequest body)` — the F2-22a class), so
     * the entity constraint is the only available lever. Adding those constraints without this
     * handler would have converted "you forgot the Arabic title" into an opaque 500 — making
     * the platform worse at explaining a failure it had just started detecting.
     *
     * <p><b>Scope, stated precisely.</b> {@link #handleConstraint} below already catches a
     * {@link ConstraintViolationException} that propagates <em>unwrapped</em> — the
     * {@code @Validated} service-method case, which throws before any transaction boundary.
     * That is not the entity case. A constraint on a JPA field fails during <b>flush</b>,
     * inside the transaction, so Spring's interceptor wraps it at commit and the unwrapped
     * handler never sees it. This handler closes that leg, and shares
     * {@link #constraintProblem} with the unwrapped one so both answer identically.
     */
    @ExceptionHandler(TransactionSystemException.class)
    public ProblemDetail handleTransactionSystem(TransactionSystemException ex) {
        ConstraintViolationException cve = findConstraintViolation(ex);
        if (cve != null) {
            return constraintProblem(cve);
        }
        // Some other commit failure — a genuine 500, and not something to dress up as a 400.
        log.error("Transaction failed on commit: {}", ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "The transaction could not be committed.");
        pd.setTitle("Transaction Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/transaction"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Walks the cause chain — the violation is nested inside TransactionSystemException. */
    private static ConstraintViolationException findConstraintViolation(Throwable t) {
        for (Throwable cur = t; cur != null && cur.getCause() != cur; cur = cur.getCause()) {
            if (cur instanceof ConstraintViolationException cve) {
                return cve;
            }
        }
        return null;
    }

    /**
     * Shared 400 body for a bean-validation failure, wrapped or not.
     *
     * <p>Carries the {@code errors} map keyed by property path <em>as well as</em> the joined
     * {@code detail} the unwrapped handler has always produced. The map is what the frontend's
     * {@code problemMessage()} folds into a toast naming the offending field, so entity-level
     * constraints report like every other validation failure with no frontend change.
     */
    private static ProblemDetail constraintProblem(ConstraintViolationException ex) {
        Map<String, String> errors = ex.getConstraintViolations().stream()
            .collect(Collectors.toMap(
                v -> v.getPropertyPath() == null ? "entity" : v.getPropertyPath().toString(),
                v -> v.getMessage() != null ? v.getMessage() : "invalid",
                (a, b) -> a));
        String detail = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .collect(Collectors.joining("; "));
        if (detail.isBlank()) {
            detail = "Validation failed.";
        }
        log.warn("[entity-validation] {}", errors);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/validation"));
        pd.setProperty("errors", errors);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://grc.tss.com/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-339 — a {@code @Service} that reads a row with {@code em.getSingleResult()} throws
     * {@link NoResultException} when the id is unknown, belongs to another tenant, or is
     * soft-deleted. It is a {@code jakarta.persistence.PersistenceException}, <b>not</b> a Spring
     * {@link DataAccessException}, so exception translation never touches it (that applies only to
     * {@code @Repository} beans) and it escaped every DB handler here, falling through to
     * {@link #handleGeneral} as a bare <i>"An unexpected database error occurred."</i> 500.
     *
     * <p>F2-281 observed exactly this on {@code CyberPdplBridgeService.triggerForIncident}
     * (<i>"a wrong id is a NoResultException rather than a 404 naming the field"</i>) and filed it;
     * the same shape is live on {@code EvidenceChainService.verifyChain}/{@code getChainStatus},
     * reached by {@code GET /evidence-chain/{id}} and {@code POST /evidence-chain/{id}/verify} with
     * the id taken straight from the path. {@code getSingleResult()} is the only source of this
     * exception, and every reachable thrower is a by-id lookup where zero rows means <i>not
     * found</i> — so 404 is unambiguously correct and there is no thrower for which the old 500 was
     * right. F2-227's lineage: an opaque 500 that cannot be diagnosed from the response gets
     * rediscovered instead of fixed.
     */
    @ExceptionHandler(NoResultException.class)
    public ProblemDetail handleNoResult(NoResultException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "The requested record was not found.");
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://grc.tss.com/errors/not-found"));
        // SEC-CFG-001: the class name and root message are dev-only, same gate as the catch-all.
        if (verboseErrors) {
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * <b>F2-84 — the entitlement guard had never once returned 403.</b>
     *
     * <p>{@link ModuleDisabledException} carries {@code @ResponseStatus(FORBIDDEN)} and no handler,
     * which is resolved by Spring's {@code ResponseStatusExceptionResolver} — and that resolver runs
     * <i>after</i> this {@code @RestControllerAdvice}, so {@link #handleGeneral} claimed it first and
     * returned <b>500 {@code errors/internal}</b>. The mechanism is the one already documented three
     * handlers below for {@link ResponseStatusException}; nobody connected it to the licence guard.
     *
     * <p>It stayed invisible because it was <em>unreachable</em>: entitlement covered only
     * {@code socops}, every tenant holds {@code SOCOPS}, and there are no socops controller tests —
     * so the guard's <b>failure</b> path had never executed. F2-04's sweep to 197 controllers turned
     * that from latent into the platform's answer to "you have not bought this module".
     *
     * <p>Why a 500 here is worse than it looks. In dev, {@code grc.errors.verbose=true} leaked the
     * exception class and root message, which is how the probe caught it. In <b>prod</b>
     * SEC-CFG-001 strips both, so a customer who simply has not licensed a module received a bare
     * {@code Internal Server Error} plus a correlation id — indistinguishable from an outage, and
     * the support path is a bug report rather than a sales conversation. It also broke the claim in
     * this exception's own javadoc ("Mapped to HTTP 403 Forbidden so the frontend can distinguish
     * licensing failures"), ADR-013, and the SKU catalogue's "server-denied when unlicensed".
     *
     * <p>{@code moduleCode} is exposed as a structured property deliberately. A 403 whose body does
     * not say <em>which</em> module is missing forces the frontend to guess, which is the F2-80 shape
     * — a handler that discards what the thrower knew. The organisation id is the caller's own, taken
     * from their JWT, so echoing it tells them nothing they did not send.
     */
    @ExceptionHandler(ModuleDisabledException.class)
    public ProblemDetail handleModuleDisabled(ModuleDisabledException ex) {
        log.info("[entitlement] org={} denied module={}", ex.getOrganisationId(), ex.getModuleCode());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
            "Your organisation is not licensed for the " + ex.getModuleCode()
            + " module. Contact your administrator to enable it.");
        pd.setTitle("Module Not Licensed");
        pd.setType(URI.create("https://grc.tss.com/errors/module-not-licensed"));
        pd.setProperty("moduleCode", ex.getModuleCode());
        pd.setProperty("organisationId", ex.getOrganisationId());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "You do not have permission to perform this action.");
        pd.setTitle("Access Denied");
        pd.setType(URI.create("https://grc.tss.com/errors/forbidden"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * <b>F2-80 — twenty-three denial messages were written and none was delivered.</b>
     *
     * <p>{@link #handleAccessDenied} above discards {@code ex.getMessage()} on purpose: for the
     * denials Spring Security raises, any detail is a hint about the guard. But <b>23</b> service
     * sites threw {@code AccessDeniedException} carrying a composed explanation, and inherited a
     * message policy written for a different sender. Every one surfaced as the flat
     * <i>"You do not have permission to perform this action."</i> — including the segregation-of-duty
     * gate, where the caller <i>has</i> the permission and was told to go and get it.
     *
     * <p>This handler is the other policy, and it is the same one {@link #handleBusinessRule} has
     * always applied two handlers down: <i>403 with the rule's own message, since explaining the rule
     * is the point.</i> The split is by <b>who threw it</b>, which is the only thing that reliably
     * distinguishes "the framework said no" from "the domain said no and knows why".
     *
     * <p>⚠ Deliberately NOT done by passing {@code ex.getMessage()} through
     * {@link #handleAccessDenied}. That is one line and it would have shipped a disclosure: two of
     * the 23 messages interpolated a colleague's name, so an unauthorised caller would have learned
     * it from a 403, and Spring's own {@code @PreAuthorize} denial text would have been echoed as
     * well. Opting in per throw site is what makes the per-message disclosure review possible;
     * {@code DomainAccessDeniedExceptionRatchetTest} keeps the boundary from eroding.
     *
     * <p>Logged at {@code info}: a refusal that explains itself to the caller should also be
     * findable by whoever they then ask about it. F2-80's own provenance is the argument — a probe
     * spent a false lead on a 403 whose cause was sitting in the log and not in the response.
     */
    @ExceptionHandler(DomainAccessDeniedException.class)
    public ProblemDetail handleDomainAccessDenied(DomainAccessDeniedException ex) {
        log.info("[access-denied] domain refusal: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Access Denied");
        pd.setType(URI.create("https://grc.tss.com/errors/forbidden"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Business-rule violation (e.g. segregation of duties) — 403 with the
     *  rule's own message, since explaining the rule is the point. */
    @ExceptionHandler(BusinessRuleException.class)
    public ProblemDetail handleBusinessRule(BusinessRuleException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        pd.setTitle("Operation Not Allowed");
        pd.setType(URI.create("https://grc.tss.com/errors/business-rule"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-88 — the pre-flighted half: a uniqueness clash the service detected before the flush, so
     * it can name the field. Shares {@code conflictKind=UNIQUE} with
     * {@link #handleDataIntegrityViolation} so a client need not care which half caught it, and adds
     * {@code errors} in the same shape bean validation uses — the FE's {@code problemMessage()}
     * already folds that map into a toast that names the field.
     */
    @ExceptionHandler(DuplicateResourceException.class)
    public ProblemDetail handleDuplicateResource(DuplicateResourceException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        pd.setType(URI.create("https://grc.tss.com/errors/data-integrity"));
        pd.setProperty("conflictKind", "UNIQUE");
        if (ex.getField() != null && !ex.getField().isBlank()) {
            pd.setProperty("field", ex.getField());
            pd.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ProblemDetail handleBadCredentials(BadCredentialsException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        pd.setTitle("Authentication Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/unauthorized"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(LockedException.class)
    public ProblemDetail handleLocked(LockedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Account is locked. Please try again later.");
        pd.setTitle("Account Locked");
        pd.setType(URI.create("https://grc.tss.com/errors/account-locked"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegal(IllegalArgumentException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        pd.setTitle("Bad Request");
        pd.setType(URI.create("https://grc.tss.com/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-348 — a malformed date STRING returned an opaque 500. Several controllers and intake
     * adapters parse a caller-supplied date field with a manual {@code LocalDate.parse(...)} /
     * {@code LocalDateTime.parse(...)} rather than a typed {@code @RequestParam LocalDate} (which
     * {@link MethodArgumentTypeMismatchException} already covers). {@link DateTimeParseException}
     * is a {@code java.time} {@code RuntimeException}, not an {@link IllegalArgumentException}, so
     * {@link #handleIllegal} never caught it and it fell through to {@link #handleGeneral} — a 500
     * for unambiguously bad client input. Same opaque-500 family as F2-227 (optimistic lock) and
     * F2-339 ({@code NoResultException}).
     *
     * <p>400 is unambiguous: a parse failure means the string did not match the expected date
     * format, which only a caller (or a malformed stored value) can produce — the server never
     * mints a date string it then fails to parse. Class and root message stay dev-only
     * (SEC-CFG-001), the same gate as the catch-all.
     */
    @ExceptionHandler(DateTimeParseException.class)
    public ProblemDetail handleBadDate(DateTimeParseException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "A date value could not be parsed. Expected an ISO date (e.g. 2026-08-25) or date-time.");
        pd.setTitle("Invalid Date");
        pd.setType(URI.create("https://grc.tss.com/errors/invalid-date"));
        if (verboseErrors) {
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-448 — {@code ?sort=<anything>} on a paged endpoint answered an opaque 500.
     *
     * <p>Spring Data binds the {@code sort} query parameter onto the {@link
     * org.springframework.data.domain.Pageable} and resolves it against the entity's metamodel at
     * query time. A property the entity does not have raises {@link PropertyReferenceException},
     * which lives in {@code org.springframework.data.mapping} and is <b>not</b> an
     * {@link IllegalArgumentException} — so {@link #handleIllegal} never caught it and it fell
     * through to {@link #handleGeneral}. Measured: <b>24 controllers bind a {@code Pageable} from
     * the request</b> across 34 {@code @PageableDefault} sites, so a single query parameter 500s
     * any of them, for any authenticated reader. Same opaque-500 family as F2-227 (optimistic
     * lock), F2-339 ({@code NoResultException}), F2-348 ({@code DateTimeParseException}) and
     * F2-349 (oversized upload).
     *
     * <p>⚠ <b>400 is unambiguous here, and that was measured rather than assumed.</b> A
     * {@code PropertyReferenceException} at request time could in principle come from a
     * <i>server-built</i> {@code Sort} naming a wrong property, which would be a server bug a 400
     * would mislabel. Measured 2026-09-03: the tree holds <b>three</b> {@code Sort.by(...)} calls
     * and all three use string literals, and <b>all 13 {@code @PageableDefault(sort = ...)}
     * literals resolve</b> — probed live, every one of those routes answers 200 with no
     * {@code sort} parameter. So at request time the only source is the caller's own input.
     *
     * <p>⚠⚠ <b>The detail names the PROPERTY and not the entity type.</b> The exception's
     * own message is {@code No property 'oops' found for type 'RiskAppetiteStatement'} — the
     * property is the caller's own input and telling them is the point, while the entity class is
     * an internal detail that SEC-CFG-001 keeps out of a production response. The class and the
     * root message stay dev-only behind {@code verboseErrors}, the same gate as the catch-all.
     */
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail handleBadSortProperty(PropertyReferenceException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
            "Cannot sort by '" + ex.getPropertyName() + "' — no such field on this record.");
        pd.setTitle("Invalid Sort Property");
        pd.setType(URI.create("https://grc.tss.com/errors/invalid-sort"));
        pd.setProperty("property", ex.getPropertyName());
        if (verboseErrors) {
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-349 — an oversized upload returned an opaque 500. The platform configures
     * {@code spring.servlet.multipart.max-file-size} (50 MB) and has multipart upload endpoints
     * (attachments, evidence, file upload), so a file over the limit throws
     * {@link MaxUploadSizeExceededException} during multipart parsing. It is a
     * {@code MultipartException} / {@code RuntimeException} matched by no handler here, so it fell
     * through to {@link #handleGeneral} — a 500 that reads as an outage rather than "your file is
     * too large." Same opaque-500 family as F2-227, F2-339 and F2-348.
     *
     * <p>413 Payload Too Large is the precise status; the limit is derived from the exception
     * ({@code getMaxUploadSize()}) so the message names it without duplicating the config value.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleUploadTooLarge(MaxUploadSizeExceededException ex) {
        long maxBytes = ex.getMaxUploadSize();
        String limit = maxBytes >= 0 ? (maxBytes / (1024 * 1024)) + " MB" : "the maximum allowed size";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.PAYLOAD_TOO_LARGE,
            "The uploaded file exceeds " + limit + ".");
        pd.setTitle("File Too Large");
        pd.setType(URI.create("https://grc.tss.com/errors/file-too-large"));
        if (verboseErrors) {
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Lifecycle state-machine rejection — 409 with from/to/allowed for the client. */
    @ExceptionHandler(IllegalTransitionException.class)
    public ProblemDetail handleIllegalTransition(IllegalTransitionException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Illegal State Transition");
        pd.setType(URI.create("https://grc.tss.com/errors/illegal-transition"));
        pd.setProperty("from", ex.getFrom());
        pd.setProperty("to", ex.getTo());
        pd.setProperty("allowed", ex.getAllowed());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * A tenant-isolation block from {@code TenantWriteGuardInterceptor} — <b>403, not 500</b>.
     *
     * <p>F2-22b. The interceptor throws {@link SecurityException} when a write would cross tenants
     * without an explicit {@code TenantContext.allowCrossTenantWrite()} opt-in, and nothing handled
     * it: the platform's most serious runtime security control surfaced as an "unexpected error".
     * Its own javadoc claimed "response is 403". That mattered twice over — a caller could not tell
     * a refusal from a bug, and neither could whoever watched the 500 rate, so a real attempt would
     * have looked exactly like a defect.
     *
     * <p>The detail deliberately does <b>not</b> echo {@code ex.getMessage()}. That message names
     * the entity and both organisation ids to make the server log actionable; returning it would
     * confirm to a prober which tenant ids exist. The audit ledger already carries the full text
     * under {@code SECURITY_BLOCK}, and the log line below carries it for operators.
     */
    @ExceptionHandler(SecurityException.class)
    public ProblemDetail handleTenantIsolation(SecurityException ex) {
        log.warn("Tenant isolation block: {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
            "This operation would write data belonging to another organisation and was refused.");
        pd.setTitle("Cross-Tenant Write Refused");
        pd.setType(URI.create("https://grc.tss.com/errors/cross-tenant-write"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * ADR-017 <b>D-2</b> — a CAB-dependent approval refused because the tenant has not named the
     * committee its own authorisation matrix demands. <b>409, not 500 and not a silent pass.</b>
     *
     * <p>409 rather than 422 for consistency: every other lifecycle refusal in this platform is a
     * 409 ({@link IllegalTransitionException}, the SoD gates, the quorum gates) and the frontend's
     * {@code problemMessage()} already renders one. The distinguishing signal for a client that
     * wants to <em>act</em> is the {@code type} URI plus the properties, not the status code.
     *
     * <p>Every property here exists because D-2 requires the refusal to be actionable — it must name
     * the missing configuration and the screen that sets it. {@code reason} separates the two
     * different fixes (nothing mapped vs mapped-at-a-dissolved-committee); {@code remedyScreen} is
     * what the frontend routes to; {@code remedyApi} is for headless onboarding. Echoing
     * {@code organisationId} tells the caller nothing they did not send — it is their own, from
     * their JWT — the same argument as {@link #handleModuleDisabled}.
     */
    @ExceptionHandler(ChangeAuthorityNotConfiguredException.class)
    public ProblemDetail handleChangeAuthorityNotConfigured(ChangeAuthorityNotConfiguredException ex) {
        log.warn("[change-governance] org={} gate={} target={} refused — {}",
            ex.getOrganisationId(), ex.getGate(), ex.getTarget(), ex.getReason());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Change Authority Not Configured");
        pd.setType(URI.create("https://grc.tss.com/errors/change-authority-not-configured"));
        pd.setProperty("gate", ex.getGate());
        pd.setProperty("reason", ex.getReason());
        pd.setProperty("target", ex.getTarget());
        pd.setProperty("organisationId", ex.getOrganisationId());
        pd.setProperty("configuration", ChangeAuthorityNotConfiguredException.CONFIGURATION);
        pd.setProperty("remedyScreen", ChangeAuthorityNotConfiguredException.REMEDY_SCREEN);
        pd.setProperty("remedyApi", ChangeAuthorityNotConfiguredException.REMEDY_API);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** Catch-all for IllegalStateException not caught by a more specific handler. */
    @ExceptionHandler(IllegalStateException.class)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        pd.setTitle("Conflict");
        pd.setType(URI.create("https://grc.tss.com/errors/conflict"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Document generation (PDF / Excel / PPTX / DOCX) failed. Returns a 500
     * with a clear "Document generation failed" message + the {@code
     * documentKind} so the frontend toast can render "PDF export failed —
     * try again or contact support" instead of the generic
     * "An unexpected error occurred" that the catch-all surfaces.
     */
    @ExceptionHandler(ExportException.class)
    public ProblemDetail handleExportFailure(ExportException ex) {
        log.error("Document export failed [{}]: {}", ex.getDocumentKind(), ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "Document generation failed. Please try again or contact your administrator.");
        pd.setTitle(ex.getDocumentKind() + " export failed");
        pd.setType(URI.create("https://grc.tss.com/errors/export-failed"));
        pd.setProperty("documentKind", ex.getDocumentKind());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Spring throws {@link NoResourceFoundException} when no controller
     * is mapped to the requested path. The default behaviour bubbles up
     * to {@link #handleGeneral} and surfaces as a 500, which is
     * misleading — there's no internal error, the URL just doesn't
     * exist. Translate to a proper 404 so the frontend's ErrorState
     * picks the "Not found" icon + message instead of red "server error".
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResource(NoResourceFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
            "No handler is mapped to this URL.");
        pd.setTitle("Not Found");
        pd.setType(URI.create("https://grc.tss.com/errors/not-found"));
        pd.setProperty("timestamp", Instant.now());
        pd.setProperty("path", ex.getResourcePath());
        return pd;
    }

    /**
     * GET on a POST-only path (or vice versa) was returning 500. Map to
     * 405 so the API matches HTTP semantics + frontend retry logic.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.METHOD_NOT_ALLOWED,
            "HTTP method " + ex.getMethod() + " is not supported on this endpoint.");
        pd.setTitle("Method Not Allowed");
        pd.setType(URI.create("https://grc.tss.com/errors/method-not-allowed"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Catches the "table doesn't exist yet" failure mode that hits a
     * fresh dev DB before its module-specific {@code
     * setup_<module>_db.py} bootstrap has run. Spring + Hibernate
     * wrap the underlying SQL error in a number of different
     * {@link DataAccessException} subclasses (BadSqlGrammar,
     * InvalidDataAccessResourceUsage, JpaSystemException,
     * UncategorizedDataAccessException, …) depending on the JPA /
     * JDBC path the query took. Catching the common ancestor catches
     * them all; we then walk the cause chain looking for SQL
     * Server's "Invalid object name 'schema.table'" (or "does not
     * exist" on Postgres / "not found" on H2).
     *
     * <p>Promotes the response to <strong>503 Service Unavailable</strong>
     * with a concrete remediation hint the user can act on. Modules
     * that need a different behaviour (e.g. silently empty page)
     * should still wrap their service-layer queries in a per-method
     * try/catch — that runs <em>before</em> this handler does, so
     * existing per-service guards keep their semantics.
     */
    /**
     * Companion to {@link #handleMissingDbObject}. When a service has a
     * try/catch that swallows a DataAccessException, Spring still keeps
     * the transaction in rollback-only state. On method return Spring's
     * transaction interceptor calls {@code commit()}, sees rollback-only,
     * and throws {@link UnexpectedRollbackException}. That escapes the
     * service-layer catch entirely and surfaces as a 500.
     *
     * <p>The proper fix is to mark the @Transactional with
     * {@code noRollbackFor = RuntimeException.class} on the read methods,
     * which the committee services now do. This handler is a
     * belt-and-braces — it makes any future module's accidentally
     * unprotected read return the friendly 503 instead of a 500.
     */
    @ExceptionHandler(UnexpectedRollbackException.class)
    public ProblemDetail handleUnexpectedRollback(UnexpectedRollbackException ex) {
        if (isMissingObjectError(ex)) {
            return missingObjectResponse(ex);
        }
        log.warn("Unexpected rollback (non-missing-object): {}", ex.getMessage());
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
            "The transaction was rolled back unexpectedly. The previous "
            + "operation may have left the database in an inconsistent state — "
            + "please retry.");
        pd.setTitle("Transaction rolled back");
        pd.setType(URI.create("https://grc.tss.com/errors/unexpected-rollback"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * F2-88 — a constraint the database enforces and the code did not check must not surface as a
     * 500.
     *
     * <p><b>The defect.</b> {@code POST /api/admin/committees} with a {@code code} the tenant
     * already holds answered <b>500 "An unexpected database error occurred."</b> — reproduced
     * deliberately: the same payload twice, 201 then 500. The unique index is enforced at flush, so
     * the failure arrives as a {@link DataIntegrityViolationException}, which
     * {@link #handleMissingDbObject}'s else-branch swept into the generic 500. It is on the first
     * step of tenant onboarding for change governance — the step F2-87's refusal routes an
     * administrator to — so "constitute a committee" answered 500 and turned a guided onboarding
     * moment into a support ticket. W1-5's golden path then hit it on its second consecutive run:
     * not an edge case, but what the second run of anything looks like.
     *
     * <p><b>Why a generic backstop and not only the pre-flight.</b> The specific fix is a
     * pre-flight in the service, which can name the field ({@code code}). But measured on this
     * database there are <b>264 unique non-PK indexes, 124 UNIQUE constraints and 181 foreign
     * keys</b> — every one of them a future 500 of exactly this shape. A pre-flight fixes one; this
     * maps the whole class onto the right status so the next unique index cannot repeat it.
     *
     * <p><b>What it deliberately does not say.</b> The driver's message carries the schema and the
     * <em>duplicate values</em> — {@code "The duplicate key value is (1, CMT-…)"} — which is tenant
     * data, and prod hardening (SEC-CFG-001) strips root messages for exactly that reason. So the
     * response names the <em>kind</em> of conflict and the constraint identifier, never the raw
     * message and never the values. The field name comes from the service pre-flight, which knows
     * the domain; the backstop's job is the status code and a truthful category.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrityViolation(
            org.springframework.dao.DataIntegrityViolationException ex) {
        return integrityResponse(ex);
    }

    /**
     * The single decode of a SQL Server error number into a status, a kind and a sentence — F2-432.
     *
     * <p>Extracted from {@link #handleDataIntegrityViolation} because a truncation does not arrive
     * as a {@code DataIntegrityViolationException} at all (see {@link #TRUNCATION_ERRORS}), so two
     * public handlers have to reach the same decision. One decode, two entry points: a second copy
     * of this switch would drift the moment one of them learned a new error number.
     */
    private ProblemDetail integrityResponse(Throwable ex) {
        int sqlError = sqlErrorCode(ex);
        String constraint = constraintName(ex);

        // SQL Server error numbers. Grouped by what the caller can do about it, which is the only
        // distinction that belongs in a status code.
        String kind;
        HttpStatus status;
        String detail;
        switch (sqlError) {
            case 2627, 2601 -> {                     // UNIQUE KEY / unique index violation
                kind = "UNIQUE";
                status = HttpStatus.CONFLICT;
                // ⚠⚠ F2-418 — this used to end "must be unique within your organisation", and that
                // clause was unconditional. Measured over the packaged migration chain: of the 260
                // unique guarantees that sit on a table an @Entity maps, 48 omit organisation_id
                // altogether, so for 18% of the population the sentence is false by construction.
                //
                // The sharpest is iam.users. V227 creates ix_users_username / ix_users_email GLOBAL
                // and says why — "login authenticates by username with no tenant context" — so a
                // tenant admin creating a user whose name is taken in ANOTHER organisation was told
                // to check the one thing that was already true, and the fact that would have
                // explained the refusal was the one the sentence denied. Worst on the provisioning
                // path: onboarding a second tenant whose administrator is named `admin` — the
                // username this platform's own docs use — reported it as non-unique within an
                // organisation that had zero users, and rolled the whole onboarding back.
                //
                // 🔑 F2-180's rule, one `case` label up in this same switch: a diagnostic that is
                // wrong in a specific, plausible direction is worse than a vague one — it does not
                // slow the reader down, it sends them somewhere. Scope is not derivable from the
                // exception (the driver names the constraint, not its key columns), so the honest
                // move is to stop asserting it; `constraint` is already on the response for support.
                detail = "A record with these values already exists. One of the fields you supplied "
                       + "must be unique — change it and try again. That uniqueness is scoped "
                       + "per field: some fields are per-organisation, some are platform-wide.";
            }
            case 547 -> {
                // ⚠⚠ F2-180 — SQL Server error 547 is BOTH "FOREIGN KEY" and "CHECK", and this arm
                // used to answer with the foreign-key wording for both. The comment here even said
                // "FOREIGN KEY / CHECK constraint", so the ambiguity was known and the message was
                // written for one of the two.
                //
                // What that cost, measured: F2-175 spent a probe iteration on a promotion that failed
                // CK_risks_status while being told "either something it refers to does not exist, or the
                // record is still referenced elsewhere" — a description of a foreign key, on a table
                // (risk.risks) that has no foreign keys at all. F2-180 then hit it again on
                // CK_vendors_status, and again 19 times over while measuring the incident register's
                // category vocabulary. Three investigations, each starting from a sentence about
                // references that was never true of any of them.
                //
                // 🔑 A diagnostic that is wrong in a specific, plausible direction is worse than a
                // vague one: it does not slow the reader down, it sends them somewhere.
                //
                // The two are told apart from the driver's own text, which says which kind of
                // constraint it was. It also names the table and column but NOT the offending value —
                // so naming the column is safe under SEC-CFG-001 and is exactly what the caller needs.
                boolean checkConstraint = isCheckConstraintViolation(ex);
                kind = checkConstraint ? "VALUE_NOT_ALLOWED" : "REFERENCE";
                status = HttpStatus.CONFLICT;
                detail = checkConstraint
                    ? "One of the values supplied is not one this field accepts. The field is "
                      + "restricted to a fixed set of values" + column(ex) + " — correct the value "
                      + "rather than retrying."
                    : "This operation conflicts with a related record: either something it "
                      + "refers to does not exist, or the record is still referenced elsewhere "
                      + "and cannot be removed.";
            }
            case 515 -> {                            // cannot insert NULL
                // ⚠⚠ F2-432 — this named no field, and the driver's own message names the column:
                // "Cannot insert the value NULL into column 'paper_code', table '…'". The extractor
                // for exactly that, `column(ex)`, sits in this class and was called by the arm ONE
                // `case` LABEL UP. F2-215's two-of-N at the tightest range this register has
                // recorded it — adjacent arms of one switch — and F2-214 wrote the consequence down
                // without connecting it to the handler: the PDPL Article 20 breach wizard's one
                // unsent NOT NULL field produced "A required field was not supplied", naming
                // nothing, on a form where nothing looks missing.
                kind = "REQUIRED_FIELD";
                status = HttpStatus.BAD_REQUEST;
                detail = "A required field was not supplied" + column(ex) + ". Check the request "
                       + "body against the endpoint's schema.";
            }
            case 2628, 8152 -> {                     // string or binary data would be truncated
                // ⚠⚠ F2-432 — a caller string longer than its column answered 500. EIGHT findings
                // (F2-185b, F2-338, F2-346, F2-347, F2-349, F2-350, F2-381, F2-428) each recorded
                // "→ an opaque 500, where a 400 naming the field is due" as the CONSEQUENCE of a
                // length defect, fixed its own cap or truncation, and left the diagnostic. F2-266's
                // headline: filings one at a time is what a population looks like when nothing
                // counts it — and F2-88's argument for this very arm, one `case` label away, was
                // already "a pre-flight fixes one; this maps the whole class onto the right status
                // so the next unique index cannot repeat it." The class was mapped for UNIQUE and
                // never for TRUNCATION.
                //
                // 🔑 Why it was a 500 rather than the `default` 409: SQL Server reports a
                // truncation with SQLSTATE **42000** — the syntax/access-rule class — where 515 and
                // 547 use 23000 (integrity constraint). Measured, both databases. So Hibernate
                // classifies it SQLGrammarException and Spring wraps it
                // InvalidDataAccessResourceUsageException, which is not a
                // DataIntegrityViolationException at all: every generic translator in the stack
                // files a caller's over-long string as a broken query. That is why
                // handleMissingDbObject routes it here, and why this keys on the ERROR NUMBER and
                // never on the exception class or the SQLSTATE.
                //
                // ⚠ Both numbers on purpose. This server (2019, compat 150) reports **2628**, the
                // enhanced message; 8152 is the legacy number for the identical condition on older
                // servers or lower compatibility levels. CLAUDE.md said 8152 in twelve places, so
                // an arm written from the register's own prose would have been dead code here.
                kind = "VALUE_TOO_LONG";
                status = HttpStatus.BAD_REQUEST;
                // ⚠ The 2628 message carries the column AND the truncated VALUE — unlike 547's,
                // which carries no value at all. `column(ex)` extracts the identifier by regex and
                // therefore cannot carry the value; the raw message stays server-side, which is the
                // whole point of SEC-CFG-001. The LIMIT is not in the message, so it is not
                // claimed: stating a number the database did not give would be the invented figure
                // this programme keeps finding.
                detail = "One of the values supplied is too long for its field" + column(ex)
                       + ". Shorten it and try again — the database does not report the maximum "
                       + "length, so check the endpoint's schema for the limit.";
            }
            default -> {
                kind = "INTEGRITY";
                status = HttpStatus.CONFLICT;
                detail = "The database rejected this change because it would break a data "
                       + "integrity rule.";
            }
        }

        // Full detail server-side only — this is where the schema and the duplicate values belong.
        log.warn("[integrity] sqlError={} constraint={} kind={} — {}",
            sqlError, constraint, kind, rootMessage(ex));

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(status == HttpStatus.CONFLICT ? "Conflict" : "Validation Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/data-integrity"));
        pd.setProperty("conflictKind", kind);
        if (constraint != null && !constraint.isBlank()) {
            // The constraint identifier, not the message: stable, actionable for support, and it
            // carries no row values.
            pd.setProperty("constraint", constraint);
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Whether an error-547 violation was a {@code CHECK} rather than a {@code FOREIGN KEY} — F2-180.
     *
     * <p>SQL Server shares 547 between the two and the driver's message is the only thing that
     * distinguishes them: <em>"The INSERT statement conflicted with the CHECK constraint
     * "CK_risks_status""</em> against <em>"…with the FOREIGN KEY constraint…"</em>. Matching on
     * {@code CHECK constraint} rather than on the absence of {@code FOREIGN KEY} is deliberate: an
     * unrecognised 547 keeps the old, broader wording instead of being asserted as a value problem.
     */
    private static boolean isCheckConstraintViolation(Throwable ex) {
        return chainText(ex).toUpperCase(java.util.Locale.ROOT).contains("CHECK CONSTRAINT");
    }

    /**
     * Every message in the cause chain, joined.
     *
     * <p>Scanning the chain rather than only {@link #rootMessage} because the layer carrying the
     * driver's text is not reliably the deepest one: Hibernate and Spring each wrap, and which link
     * ends up last depends on the operation. Reading only the root is how this kind of check comes to
     * work for inserts and quietly stop working for updates.
     */
    private static String chainText(Throwable ex) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t.getMessage() != null) sb.append(t.getMessage()).append('\n');
            if (t.getCause() == t) break;
        }
        return sb.toString();
    }

    /**
     * {@code " (column 'status')"} when the driver named the column, otherwise empty.
     *
     * <p>The 547 CHECK message carries the table and the column but not the offending value, so this
     * adds the one piece of information a caller cannot infer without leaking row data — the reason
     * prod hardening (SEC-CFG-001) strips root messages in the first place.
     */
    private static String column(Throwable ex) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("column\\s+'([A-Za-z0-9_]+)'").matcher(chainText(ex));
        return m.find() ? " (column '" + m.group(1) + "')" : "";
    }

    /**
     * SQL Server's error numbers for <em>"String or binary data would be truncated"</em> — F2-432.
     *
     * <p>{@code 2628} is the enhanced message, which names the table, the column and the truncated
     * value; {@code 8152} is the legacy number for the identical condition, still reported by older
     * servers and by lower database compatibility levels. Measured on both of this platform's
     * databases (SQL Server 2019, compatibility 150): <b>2628</b>. Handling only the number a
     * register's prose happens to quote is how a {@code case} label ends up unreachable while
     * looking like a fix.
     */
    private static final java.util.Set<Integer> TRUNCATION_ERRORS = java.util.Set.of(2628, 8152);

    /** Whether this failure is a value too long for its column, by error number — F2-432. */
    private static boolean isTruncationError(Throwable ex) {
        return TRUNCATION_ERRORS.contains(sqlErrorCode(ex));
    }

    /** Vendor error number from anywhere in the cause chain, or 0 when there is no SQLException. */
    private static int sqlErrorCode(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof java.sql.SQLException sqlEx) return sqlEx.getErrorCode();
            if (t.getCause() == t) break;
        }
        return 0;
    }

    /**
     * The violated constraint's name. Hibernate's own
     * {@code ConstraintViolationException#getConstraintName} is the reliable source; Spring's
     * translated message appends {@code constraint [NAME]} as a fallback for drivers Hibernate
     * cannot parse.
     */
    private static String constraintName(Throwable ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof org.hibernate.exception.ConstraintViolationException hce
                    && hce.getConstraintName() != null) {
                return hce.getConstraintName();
            }
            if (t.getCause() == t) break;
        }
        String msg = ex.getMessage();
        if (msg == null) return null;
        int at = msg.lastIndexOf("constraint [");
        if (at < 0) return null;
        int close = msg.indexOf(']', at);
        if (close < 0) return null;
        String name = msg.substring(at + "constraint [".length(), close);
        // ⚠ F2-432 — Hibernate writes "constraint [null]" when the failure had no constraint at
        // all, so this returned the four-character string "null" and the response carried
        // `"constraint": "null"`. Probed on the live server: every 515 and every 547 shipped it.
        // A property naming a constraint called `null` is an invented identifier reading as
        // measured data (F2-197's shape, on a response body) — and it points a support engineer
        // at something that does not exist, which is worse than saying nothing (F2-180).
        return "null".equals(name) || name.isBlank() ? null : name;
    }

    /**
     * F2-227 — a row changed under the caller. <b>409, and it says which entity.</b>
     *
     * <p>⚠⚠ <b>Why this handler had to exist before the defect it reports could be found.</b>
     * {@link org.springframework.dao.OptimisticLockingFailureException} is a
     * {@link DataAccessException}, so until now every one of them fell into
     * {@link #handleMissingDbObject} and came back as a bare <i>"An unexpected database error
     * occurred."</i> — <b>with no exception class, even in dev with
     * {@code grc.errors.verbose=true} explicitly on</b>, because that handler builds its own
     * ProblemDetail instead of delegating to {@link #handleGeneral}, which is the only place the
     * verbose diagnostics live.
     *
     * <p>🔑 <b>So the handler that intercepted this exception class was the one handler that
     * discarded the diagnostic verbose mode exists to provide</b> — and the exception class in
     * question is the one the ROWVERSION double-flush landmine produces (CLAUDE.md's second
     * landmine, recorded at F2-175 and hit again at F2-226). A platform-wide defect family was
     * undiagnosable by construction. F2-180's rule, inverted: a wrong-but-specific diagnostic at
     * least sends the reader somewhere; a *generic* diagnostic from a *specific* handler sends
     * them nowhere and looks deliberate.
     *
     * <p>409 rather than 500 is the honest status: nothing is broken server-side, the row simply
     * moved. {@code ObjectOptimisticLockingFailureException} carries the entity type and
     * identifier, so the message can name them — which is what turns "retry?" into a diagnosis.
     */
    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLocking(
            org.springframework.dao.OptimisticLockingFailureException ex) {
        String what = null;
        if (ex instanceof org.springframework.orm.ObjectOptimisticLockingFailureException o) {
            String type = o.getPersistentClassName();
            if (type != null) {
                int dot = type.lastIndexOf('.');
                what = (dot < 0 ? type : type.substring(dot + 1))
                    + (o.getIdentifier() != null ? " " + o.getIdentifier() : "");
            }
        }
        log.error("Optimistic locking failure on {}: {}",
            what == null ? "an entity" : what, ex.getMessage(), ex);

        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
            (what == null
                ? "This record was modified by another operation while you were saving it."
                : "This " + what + " was modified by another operation while you were saving it.")
            + " Nothing was written. Reload and try again — and if this is reproducible rather"
            + " than a race, the request is writing the same row twice in one transaction.");
        pd.setTitle("Concurrent Modification");
        pd.setType(URI.create("https://grc.tss.com/errors/optimistic-lock"));
        pd.setProperty("conflictKind", "STALE_ROW");
        if (what != null) pd.setProperty("entity", what);
        // SEC-CFG-001: the class name and root message are dev-only, same gate as the catch-all.
        if (verboseErrors) {
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleMissingDbObject(DataAccessException ex) {
        if (isMissingObjectError(ex)) {
            return missingObjectResponse(ex);
        }
        // F2-432 — a truncation arrives HERE rather than at handleDataIntegrityViolation, because
        // SQL Server reports it with the SQLSTATE of a grammar error (42000) and every translator
        // in the stack believes it. Re-file it as what it is: one decode, two entry points.
        if (isTruncationError(ex)) {
            return integrityResponse(ex);
        }
        // F2-227 — this used to build its own bare 500, while the comment here said "let the
        // catch-all handle it with a 500". It did not: `handleGeneral` is where SEC-CFG-001's
        // verbose diagnostics live, so every DataAccessException that reached this branch lost
        // its exception class and root message even in dev. A comment describing a behaviour the
        // code does not have is worse than no comment, because it answers the question a reader
        // was about to ask (F2-184's shape, in an exception handler). Now it actually delegates.
        return handleGeneral(ex);
    }

    /** Shared body builder for the 503 "module not bootstrapped"
     *  response. Both the DataAccessException and
     *  UnexpectedRollbackException handlers route here. */
    private ProblemDetail missingObjectResponse(Throwable ex) {
        String detail = "A database table or view this endpoint needs has not been "
            + "created in the current database. In dev, run the module's "
            + "setup_<module>_db.py bootstrap script (see CLAUDE.md). In other "
            + "profiles, ensure all Flyway migrations have applied successfully.";
        log.warn("[missing-db-object] {}", rootMessage(ex));
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, detail);
        pd.setTitle("Module not yet bootstrapped");
        pd.setType(URI.create("https://grc.tss.com/errors/db-object-missing"));
        // SEC-CFG-001 — the SQL root message echoes "Invalid object name
        // 'schema.table'", disclosing schema/table names. Only surface it
        // to the client in verbose (dev) mode; the warn-log above always
        // captures it server-side.
        if (verboseErrors) {
            pd.setProperty("rootCause", rootMessage(ex));
        }
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /** True when the exception chain contains a missing-table SQL
     *  error from any common backend (SQL Server / Postgres / H2). */
    private static boolean isMissingObjectError(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            String msg = cur.getMessage();
            if (msg != null) {
                String low = msg.toLowerCase(Locale.ROOT);
                if (low.contains("invalid object name")  // SQL Server
                    || low.contains("does not exist")    // Postgres
                    || low.contains("not found")         // H2 / generic
                    ) return true;
            }
            cur = cur.getCause();
        }
        return false;
    }

    /** Walk to the root and return its message — what the user
     *  actually needs to see ("Invalid object name 'admin.X'"). */
    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) cur = cur.getCause();
        return cur.getMessage();
    }

    /**
     * Malformed JSON in the request body (e.g. user sent invalid syntax,
     * unknown field, wrong type). Without this handler Spring's default was
     * to throw, get caught by {@link #handleGeneral}, and surface as a
     * misleading "Internal Server Error" — when really it was a 400 client
     * mistake. The detail message includes the part of the body that the
     * Jackson parser stumbled on.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadableBody(HttpMessageNotReadableException ex) {
        String detail = "The request body could not be parsed as JSON.";
        // Most readable bit of the error chain — Jackson typically gives
        // "Cannot deserialize value of type X from String '...'" or similar.
        String root = rootMessage(ex);
        if (root != null && !root.isBlank()) {
            // Trim noisy framework prefixes the user can't act on.
            String trimmed = root.replaceFirst("^JSON parse error: ", "");
            detail = "Could not parse request body: " + trimmed;
        }
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Malformed Request Body");
        pd.setType(URI.create("https://grc.tss.com/errors/bad-request"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Path variable or query param of the wrong type (e.g. "abc" for an
     * `int id` path variable, or "yesterday" for a `LocalDate` query param).
     * Spring's default behaviour escalated this to handleGeneral too,
     * which is wrong — it's a client error, not a server error.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String required = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "the expected type";
        String detail = String.format(
            "Parameter '%s' could not be parsed as %s (got '%s').",
            ex.getName(), required, String.valueOf(ex.getValue())
        );
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Invalid Parameter Type");
        pd.setType(URI.create("https://grc.tss.com/errors/bad-request"));
        pd.setProperty("parameter", ex.getName());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Required query parameter missing — e.g. `?from=` left off when the
     * controller declared {@code @RequestParam(required = true)}.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST,
            "Required " + ex.getParameterType() + " parameter '" + ex.getParameterName() + "' is missing.");
        pd.setTitle("Missing Required Parameter");
        pd.setType(URI.create("https://grc.tss.com/errors/bad-request"));
        pd.setProperty("parameter", ex.getParameterName());
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Bean Validation constraint violation surfaced from inside a service
     * (typically a {@code @Validated} method-level check), propagating
     * <em>unwrapped</em> because it was thrown before a transaction boundary.
     *
     * <p>F2-30 corrected this javadoc's claim that it stopped "Hibernate's constraint
     * failures" bubbling up as 500. It did not: an <em>entity</em> constraint fails at flush
     * inside the transaction and reaches the advice as {@link TransactionSystemException},
     * which nothing handled. {@link #handleTransactionSystem} is that leg; both now build the
     * body with {@link #constraintProblem}, which adds the {@code errors} map this handler
     * never emitted.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        return constraintProblem(ex);
    }

    /**
     * A controller may deliberately signal an HTTP status via
     * {@link ResponseStatusException} (e.g. the PDPL Art.12 public sub-processor
     * endpoint returns 404 for an unknown/blank org code — SEC-PRIV-003). Spring's
     * built-in {@code ResponseStatusExceptionResolver} runs AFTER this
     * {@code @RestControllerAdvice}, so without an explicit handler the catch-all
     * {@link #handleGeneral} hijacks the exception and mis-reports a deliberate
     * 4xx as a 500. Honour the embedded status + reason instead. Logged at WARN
     * (4xx) — these are client-facing signals, not server faults.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        HttpStatus resolved = HttpStatus.resolve(status.value());
        String detail = ex.getReason() != null && !ex.getReason().isBlank()
            ? ex.getReason()
            : (resolved != null ? resolved.getReasonPhrase() : "Request failed.");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setTitle(resolved != null ? resolved.getReasonPhrase() : "Error");
        pd.setType(URI.create("https://grc.tss.com/errors/response-status"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Catch-all for any exception that wasn't recognised above. Stays a 500
     * — these are genuine server errors that need investigating.
     *
     * <p>SEC-CFG-001 — diagnostics are gated to {@code grc.errors.verbose=true}
     * (dev only). In verbose mode the detail includes the exception class name
     * + its root message so the developer can grep the server log straight from
     * the toast text, and the {@code exception}/{@code rootCause} properties
     * carry the same for structured access. In every other profile the client
     * receives only a generic message plus a {@code reference} correlation id;
     * the full detail is logged server-side keyed by that same id, so internal
     * class names, package paths, and SQL object/schema names are never echoed
     * to the caller.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        String ref = UUID.randomUUID().toString();
        log.error("Unhandled exception ref={} [{}]: {}", ref, ex.getClass().getName(), ex.getMessage(), ex);
        ProblemDetail pd;
        if (verboseErrors) {
            String root = rootMessage(ex);
            String detail = ex.getClass().getSimpleName()
                + (root != null && !root.isBlank() ? ": " + root : "");
            pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
            pd.setProperty("exception", ex.getClass().getName());
            pd.setProperty("rootCause", root);
        } else {
            pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Reference: " + ref);
        }
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://grc.tss.com/errors/internal"));
        pd.setProperty("reference", ref);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
