package com.tss.grc.common.exception;

import jakarta.persistence.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
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
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    @ExceptionHandler(EntityNotFoundException.class)
    public ProblemDetail handleNotFound(EntityNotFoundException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        pd.setTitle("Resource Not Found");
        pd.setType(URI.create("https://grc.tss.com/errors/not-found"));
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

    @ExceptionHandler(DataAccessException.class)
    public ProblemDetail handleMissingDbObject(DataAccessException ex) {
        if (isMissingObjectError(ex)) {
            return missingObjectResponse(ex);
        }
        // Otherwise it's a genuine SQL syntax error or similar — let
        // the catch-all handle it with a 500.
        log.error("Data-access error: {}", ex.getMessage(), ex);
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected database error occurred.");
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
        pd.setProperty("rootCause", rootMessage(ex));
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
                String low = msg.toLowerCase();
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
     * (typically a {@code @Validated} method-level check). Without this,
     * Hibernate's constraint failures bubbled up as 500.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraint(ConstraintViolationException ex) {
        String detail = ex.getConstraintViolations().stream()
            .map(v -> v.getPropertyPath() + " " + v.getMessage())
            .collect(Collectors.joining("; "));
        if (detail.isBlank()) detail = "Validation failed.";
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        pd.setTitle("Validation Failed");
        pd.setType(URI.create("https://grc.tss.com/errors/validation"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }

    /**
     * Catch-all for any exception that wasn't recognised above. Stays a 500
     * — these are genuine server errors that need investigating.
     *
     * <p>Returns a detail message that includes the exception class name +
     * its root message so the developer can grep the server log straight
     * from the toast text. The {@code rootCause} property carries the same
     * for structured access. Real bugs surface clearly instead of hiding
     * behind "An unexpected error occurred."
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        String root = rootMessage(ex);
        String detail = ex.getClass().getSimpleName()
            + (root != null && !root.isBlank() ? ": " + root : "");
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, detail);
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://grc.tss.com/errors/internal"));
        pd.setProperty("exception", ex.getClass().getName());
        pd.setProperty("rootCause", root);
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
