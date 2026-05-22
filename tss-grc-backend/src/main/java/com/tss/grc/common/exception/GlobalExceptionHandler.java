package com.tss.grc.common.exception;

import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneral(Exception ex) {
        log.error("Unhandled exception [{}]: {}", ex.getClass().getName(), ex.getMessage(), ex);
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        pd.setTitle("Internal Server Error");
        pd.setType(URI.create("https://grc.tss.com/errors/internal"));
        pd.setProperty("timestamp", Instant.now());
        return pd;
    }
}
