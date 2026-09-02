package com.tss.grc.common.exception;

import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.orm.jpa.JpaSystemException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.UnexpectedRollbackException;
import org.springframework.web.server.ResponseStatusException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the user-visible error response shape for the most commonly seen
 * exceptions. The frontend reads {@code title} + {@code detail} into a
 * toast, so the wording matters for the UX.
 */
class GlobalExceptionHandlerTest {

    /**
     * Default handler — {@code verboseErrors=false} (the value in base/prod
     * profiles, SEC-CFG-001). Internal exception class names and SQL
     * object/schema names must NOT leak to the client in this mode.
     */
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** Handler configured as dev runs it ({@code grc.errors.verbose=true}). */
    private GlobalExceptionHandler verboseHandler() {
        GlobalExceptionHandler h = new GlobalExceptionHandler();
        ReflectionTestUtils.setField(h, "verboseErrors", true);
        return h;
    }

    @Test
    void exportException_pdf_returns500_withDocumentKindAndUserMessage() {
        ExportException ex = new ExportException("PDF", "PDF generation failed", new RuntimeException("oop"));
        ProblemDetail pd = handler.handleExportFailure(ex);
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getTitle()).isEqualTo("PDF export failed");
        // User-visible detail must NOT leak the underlying cause / stack trace.
        assertThat(pd.getDetail()).contains("try again");
        assertThat(pd.getDetail()).doesNotContain("oop");
        assertThat(pd.getProperties()).containsKey("documentKind");
        assertThat(pd.getProperties().get("documentKind")).isEqualTo("PDF");
    }

    @Test
    void exportException_excel_setsTitlePrefixToExcel() {
        ProblemDetail pd = handler.handleExportFailure(
            new ExportException("Excel", "boom", null));
        assertThat(pd.getTitle()).isEqualTo("Excel export failed");
    }

    @Test
    void exportException_pptx_setsTitlePrefixToPptx() {
        ProblemDetail pd = handler.handleExportFailure(
            new ExportException("PPTX", "boom", null));
        assertThat(pd.getTitle()).isEqualTo("PPTX export failed");
    }

    @Test
    void missingDbObject_sqlServerSignature_returns503_andDoesNotLeakSchemaInProd() {
        // Simulate the exact error a Spring repo throws when a JPA
        // query hits a SQL Server table that doesn't exist yet —
        // the common case on a fresh dev DB before a module's
        // setup_<module>_db.py has run.
        Throwable root = new RuntimeException(
            "Invalid object name 'admin.committee_templates'.");
        InvalidDataAccessResourceUsageException ex =
            new InvalidDataAccessResourceUsageException("could not execute query", root);

        ProblemDetail pd = handler.handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Module not yet bootstrapped");
        assertThat(pd.getDetail()).contains("setup_<module>_db.py");
        // SEC-CFG-001: with verbose diagnostics OFF (base/prod default) the SQL
        // object/schema name must NOT be echoed to the client.
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
        assertThat(pd.getDetail()).doesNotContain("admin.committee_templates");
    }

    @Test
    void missingDbObject_sqlServerSignature_surfacesRootCauseWhenVerbose() {
        // In dev (grc.errors.verbose=true) the SQL root message is still
        // surfaced so developers see "Invalid object name 'schema.table'".
        Throwable root = new RuntimeException(
            "Invalid object name 'admin.committee_templates'.");
        InvalidDataAccessResourceUsageException ex =
            new InvalidDataAccessResourceUsageException("could not execute query", root);

        ProblemDetail pd = verboseHandler().handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getProperties()).containsKey("rootCause");
        assertThat(pd.getProperties().get("rootCause"))
            .asString().contains("admin.committee_templates");
    }

    @Test
    void missingDbObject_postgresDoesNotExist_returns503() {
        Throwable root = new RuntimeException(
            "ERROR: relation \"socops.security_event\" does not exist");
        InvalidDataAccessResourceUsageException ex =
            new InvalidDataAccessResourceUsageException("could not execute query", root);

        ProblemDetail pd = handler.handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Module not yet bootstrapped");
    }

    @Test
    void invalidDataAccess_unrelatedSqlError_returns500() {
        // A genuine SQL syntax error must NOT be misclassified as a
        // missing table — the user needs a real 500 to investigate.
        Throwable root = new RuntimeException(
            "Incorrect syntax near the keyword 'WHERE'.");
        InvalidDataAccessResourceUsageException ex =
            new InvalidDataAccessResourceUsageException("could not execute query", root);

        ProblemDetail pd = handler.handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * Real-world Hibernate path: a missing table can surface as
     * {@link BadSqlGrammarException} when JDBC fails parsing the
     * generated SQL against the schema. PR #114-#116 only caught
     * the parent {@code InvalidDataAccessResourceUsageException}
     * which BadSqlGrammar DOES extend, so this should still work
     * — but we lock it in a test to prevent regression.
     */
    @Test
    void missingDbObject_badSqlGrammarSubtype_returns503() {
        Throwable root = new RuntimeException(
            "Invalid object name 'admin.committee_templates'.");
        SQLException sqlEx = new SQLException(
            "Invalid object name 'admin.committee_templates'.", null, 208, root);
        BadSqlGrammarException ex = new BadSqlGrammarException(
            "Statement preparation failed", "SELECT * FROM admin.committee_templates", sqlEx);

        ProblemDetail pd = handler.handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Module not yet bootstrapped");
    }

    /**
     * Another real-world Hibernate path: when Spring wraps the JPA
     * exception, it may surface as JpaSystemException with the SQL
     * Server error buried in the cause chain. PR #117 broadens the
     * handler to DataAccessException so this case is now covered too.
     */
    @Test
    void missingDbObject_jpaSystemExceptionSubtype_returns503() {
        Throwable root = new RuntimeException(
            "Invalid object name 'admin.committees'.");
        // Wrap once to simulate Hibernate's typical chain
        RuntimeException hibernate = new RuntimeException(
            "could not execute statement", root);
        JpaSystemException ex = new JpaSystemException(hibernate);

        ProblemDetail pd = handler.handleMissingDbObject(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Module not yet bootstrapped");
    }

    /**
     * When a @Transactional service catches a DataAccessException and
     * returns normally, Spring still has the transaction marked
     * rollback-only and throws UnexpectedRollbackException at commit
     * time. That escapes the service's catch — the global handler must
     * recognise it and surface the same friendly 503.
     */
    @Test
    void unexpectedRollback_withMissingObjectInCause_returns503() {
        Throwable root = new RuntimeException(
            "Invalid object name 'admin.committee_templates'.");
        UnexpectedRollbackException ex = new UnexpectedRollbackException(
            "Transaction silently rolled back because it has been marked as rollback-only",
            root);

        ProblemDetail pd = handler.handleUnexpectedRollback(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Module not yet bootstrapped");
        // SEC-CFG-001: non-verbose (base/prod) default must not echo the SQL
        // object/schema name; the verbose path still surfaces it.
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
        ProblemDetail verbose = verboseHandler().handleUnexpectedRollback(ex);
        assertThat(verbose.getProperties().get("rootCause"))
            .asString().contains("admin.committee_templates");
    }

    @Test
    void handleGeneral_nonVerbose_returnsGenericMessageWithReference_noLeak() {
        // SEC-CFG-001: the catch-all must not disclose the exception class
        // name, package path, or root-cause text to the client in base/prod.
        IllegalStateException root = new IllegalStateException(
            "connection to db.internal.tss.local refused");
        RuntimeException ex = new RuntimeException("service failure", root);

        ProblemDetail pd = handler.handleGeneral(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getTitle()).isEqualTo("Internal Server Error");
        assertThat(pd.getDetail()).contains("An unexpected error occurred");
        // A correlation reference is returned so the client can quote it to
        // support; the server log carries the full detail under the same id.
        assertThat(pd.getProperties()).containsKey("reference");
        assertThat(pd.getDetail()).contains(String.valueOf(pd.getProperties().get("reference")));
        // None of the internal detail leaks.
        assertThat(pd.getProperties()).doesNotContainKey("exception");
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
        assertThat(pd.getDetail()).doesNotContain("RuntimeException");
        assertThat(pd.getDetail()).doesNotContain("db.internal.tss.local");
    }

    @Test
    void handleGeneral_verbose_surfacesExceptionAndRootCause() {
        // In dev (grc.errors.verbose=true) the diagnostics are still surfaced
        // so a developer can grep the server log from the toast text.
        IllegalStateException root = new IllegalStateException("boom-cause");
        RuntimeException ex = new RuntimeException("service failure", root);

        ProblemDetail pd = verboseHandler().handleGeneral(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getProperties().get("exception"))
            .asString().contains("RuntimeException");
        assertThat(pd.getProperties().get("rootCause"))
            .asString().contains("boom-cause");
        assertThat(pd.getProperties()).containsKey("reference");
    }

    @Test
    void responseStatusException_honoursEmbeddedStatusAndReason_not500() {
        // SEC-PRIV-003 regression: the public sub-processor endpoint throws
        // ResponseStatusException(404). Without a dedicated handler the catch-all
        // hijacked it and returned 500; this must surface the real 404 + reason.
        ResponseStatusException ex = new ResponseStatusException(
            HttpStatus.NOT_FOUND, "No published list for that organisation.");

        ProblemDetail pd = handler.handleResponseStatus(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Not Found");
        assertThat(pd.getDetail()).isEqualTo("No published list for that organisation.");
    }

    @Test
    void responseStatusException_withoutReason_fallsBackToStatusPhrase() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.FORBIDDEN);

        ProblemDetail pd = handler.handleResponseStatus(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(pd.getDetail()).isEqualTo("Forbidden");
    }

    @Test
    void unexpectedRollback_withoutMissingObject_returns503WithGenericMessage() {
        // Rollback for some other reason (constraint violation etc.) —
        // still degrade to 503 with a retry hint rather than a 500.
        UnexpectedRollbackException ex = new UnexpectedRollbackException(
            "Transaction silently rolled back because it has been marked as rollback-only");

        ProblemDetail pd = handler.handleUnexpectedRollback(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());
        assertThat(pd.getTitle()).isEqualTo("Transaction rolled back");
        assertThat(pd.getDetail()).contains("retry");
    }

    @Test
    void noResult_fromGetSingleResult_returns404NotOpaque500() {
        // F2-339: a @Service's em.getSingleResult() on a missing/foreign/soft-deleted id throws
        // jakarta.persistence.NoResultException, which is NOT a Spring DataAccessException, so it
        // escaped every DB handler and fell through to handleGeneral as an opaque 500. A by-id
        // lookup returning zero rows is "not found" — 404, honestly.
        ProblemDetail pd = handler.handleNoResult(new NoResultException("No entity found for query"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getTitle()).isEqualTo("Resource Not Found");
        assertThat(pd.getDetail()).isEqualTo("The requested record was not found.");
        // SEC-CFG-001: base/prod (verboseErrors=false) must not leak the exception class or the
        // JPA root message ("No entity found for query" names the query shape).
        assertThat(pd.getProperties()).doesNotContainKey("exception");
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
    }

    @Test
    void noResult_verbose_surfacesExceptionClassButKeepsThe404() {
        ProblemDetail pd = verboseHandler().handleNoResult(new NoResultException("No entity found for query"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(pd.getProperties().get("exception")).isEqualTo(NoResultException.class.getName());
        assertThat(pd.getProperties().get("rootCause")).isEqualTo("No entity found for query");
    }

    /**
     * F2-339 — the defect is one of ROUTING: before the dedicated handler, a NoResultException
     * (not a Spring DataAccessException) matched only {@code @ExceptionHandler(Exception.class)},
     * so Spring's own resolver sent it to {@code handleGeneral} → opaque 500. This asserts the
     * resolution itself, which is what actually changed — and it is what a wholesale revert of the
     * fix would break (the resolver would pick {@code handleGeneral} again).
     */
    @Test
    void springRoutesNoResultToTheDedicatedHandler_notHandleGeneral() {
        ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        assertThat(resolver.resolveMethod(new NoResultException("No entity found for query")).getName())
            .isEqualTo("handleNoResult");
    }

    // ─── F2-348: a manual LocalDate.parse(callerString) throws DateTimeParseException — a
    //             java.time RuntimeException that escaped handleIllegal and fell to the opaque
    //             500 catch-all; it is bad client input, so 400. ────────────────────────────
    @Test
    void badDate_fromManualParse_returns400NotOpaque500() {
        ProblemDetail pd = handler.handleBadDate(parseFailure("not-a-date"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Invalid Date");
        // SEC-CFG-001: base/prod must not leak the exception class or the java.time root message.
        assertThat(pd.getProperties()).doesNotContainKey("exception");
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
    }

    @Test
    void badDate_verbose_surfacesExceptionClassButKeepsThe400() {
        ProblemDetail pd = verboseHandler().handleBadDate(parseFailure("2026-99-99"));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties().get("exception"))
            .isEqualTo(java.time.format.DateTimeParseException.class.getName());
    }

    /**
     * F2-348 — the defect is one of ROUTING (F2-339's shape): before the dedicated handler a
     * DateTimeParseException matched only {@code @ExceptionHandler(Exception.class)}, so Spring's
     * own resolver sent it to {@code handleGeneral} → opaque 500. This asserts the resolution, which
     * is what actually changed and what a revert of the @ExceptionHandler annotation would break.
     */
    @Test
    void springRoutesDateTimeParseToTheDedicatedHandler_notHandleGeneral() {
        ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        assertThat(resolver.resolveMethod(parseFailure("nope")).getName())
            .isEqualTo("handleBadDate");
    }

    // ─── F2-349: an oversized upload throws MaxUploadSizeExceededException during multipart
    //             parsing — a MultipartException matched by no handler, so it fell to the opaque
    //             500 catch-all; it is 413 Payload Too Large. ─────────────────────────────────
    @Test
    void uploadTooLarge_returns413NotOpaque500() {
        ProblemDetail pd = handler.handleUploadTooLarge(
            new org.springframework.web.multipart.MaxUploadSizeExceededException(50L * 1024 * 1024));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(pd.getTitle()).isEqualTo("File Too Large");
        assertThat(pd.getDetail()).contains("50 MB");   // limit derived from getMaxUploadSize()
        // SEC-CFG-001: base/prod must not leak the exception class or root message.
        assertThat(pd.getProperties()).doesNotContainKey("exception");
        assertThat(pd.getProperties()).doesNotContainKey("rootCause");
    }

    @Test
    void uploadTooLarge_verbose_surfacesExceptionClassButKeepsThe413() {
        ProblemDetail pd = verboseHandler().handleUploadTooLarge(
            new org.springframework.web.multipart.MaxUploadSizeExceededException(50L * 1024 * 1024));
        assertThat(pd.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(pd.getProperties().get("exception"))
            .isEqualTo(org.springframework.web.multipart.MaxUploadSizeExceededException.class.getName());
    }

    /**
     * F2-349 — the defect is one of ROUTING (F2-339 / F2-348's shape): before the dedicated handler
     * a MaxUploadSizeExceededException matched only {@code @ExceptionHandler(Exception.class)}, so
     * Spring's own resolver sent it to {@code handleGeneral} → opaque 500. This asserts the
     * resolution, which is what a revert of the @ExceptionHandler annotation would break.
     */
    @Test
    void springRoutesMaxUploadSizeToTheDedicatedHandler_notHandleGeneral() {
        ExceptionHandlerMethodResolver resolver =
            new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);
        assertThat(resolver.resolveMethod(
                new org.springframework.web.multipart.MaxUploadSizeExceededException(1024L)).getName())
            .isEqualTo("handleUploadTooLarge");
    }

    // ─────────────────────────────────────────────────────────────────────────────────────────
    // F2-432 — a value too long for its column answered 500, and a required field named nothing.
    //
    // The messages below are VERBATIM from the live server (SQL Server 2019, compatibility 150,
    // both databases), captured by the probe against
    // POST /api/v1/committees/papers/circulate as `compliance`. The truncated value is in the
    // 2628 fixture on purpose: the SEC-CFG-001 leg is worth nothing unless it is really there.
    // ─────────────────────────────────────────────────────────────────────────────────────────

    /** The 2628 message this server actually produces, truncated value and all. */
    private static final String TRUNCATION_2628 =
        "String or binary data would be truncated in table "
        + "'TSS_GRC.admin.committee_meeting_papers', column 'author_role'. "
        + "Truncated value: 'RRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRRR'.";

    /** The 515 message this server actually produces. */
    private static final String NULL_515 =
        "Cannot insert the value NULL into column 'paper_code', table "
        + "'TSS_GRC.admin.committee_meeting_papers'; column does not allow nulls. INSERT fails.";

    /**
     * A truncation as it really arrives: wrapped {@link InvalidDataAccessResourceUsageException},
     * because SQL Server reports it with SQLSTATE 42000 — the <em>grammar</em> class — so Hibernate
     * classifies it {@code SQLGrammarException} and Spring wraps it as a resource-usage problem
     * rather than an integrity one. That misclassification is the whole reason it was a 500.
     */
    private static InvalidDataAccessResourceUsageException truncation(int errorCode) {
        return new InvalidDataAccessResourceUsageException(
            "could not execute statement",
            new SQLException(TRUNCATION_2628, "42000", errorCode));
    }

    @Test
    void truncation_2628_is400NamingTheColumn_notAnOpaque500() {
        ProblemDetail pd = handler.handleMissingDbObject(truncation(2628));

        assertThat(pd.getStatus())
            .as("eight findings recorded this as an opaque 500; a caller's over-long string is a "
                + "400")
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "VALUE_TOO_LONG");
        assertThat(pd.getDetail())
            .as("the driver names the column and this used to discard it")
            .contains("author_role");
    }

    /**
     * 8152 is the legacy number for the identical condition. It is handled because CLAUDE.md quoted
     * it twelve times while this server reports 2628 — so an arm written from the register's prose
     * alone would have been unreachable, and one written from the measurement alone would break on
     * an older server.
     */
    @Test
    void truncation_legacy8152_isTreatedIdentically() {
        ProblemDetail pd = handler.handleMissingDbObject(truncation(8152));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "VALUE_TOO_LONG");
    }

    /**
     * The load-bearing safety leg. The 2628 message carries the truncated VALUE — tenant row data,
     * and unlike 547's message, which carries none. The column name is extracted by regex, so the
     * value cannot ride along; this asserts it in the mode that leaks most (verbose = dev).
     */
    @Test
    void truncation_neverEchoesTheTruncatedValue_evenVerbose() {
        for (GlobalExceptionHandler h : new GlobalExceptionHandler[]{handler, verboseHandler()}) {
            ProblemDetail pd = h.handleMissingDbObject(truncation(2628));
            String body = String.valueOf(pd.getDetail()) + pd.getProperties();
            assertThat(body)
                .as("SEC-CFG-001 — the row value must stay server-side")
                .doesNotContain("RRRRRRRRRR")
                .doesNotContain("Truncated value");
        }
    }

    /**
     * The limit is deliberately not stated: 2628 names the column and the value but NOT the
     * maximum length, so a number here would be invented (F2-197 / F2-425).
     */
    @Test
    void truncation_doesNotClaimALimitTheDatabaseNeverGave() {
        ProblemDetail pd = handler.handleMissingDbObject(truncation(2628));
        assertThat(pd.getDetail()).doesNotContain("120").doesNotContain("characters or fewer");
    }

    @Test
    void requiredField_515_namesTheColumnTheDriverNamed() {
        org.springframework.dao.DataIntegrityViolationException ex =
            new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement", new SQLException(NULL_515, "23000", 515));

        ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "REQUIRED_FIELD");
        assertThat(pd.getDetail())
            .as("`column(ex)` sat in the same class, called by the arm one case label up")
            .contains("paper_code");
    }

    /**
     * Hibernate writes {@code constraint [null]} when the failure had no constraint, and the
     * extractor returned the four-character string. Probed live: every 515 and every 547 shipped
     * {@code "constraint": "null"} — an invented identifier reading as measured data.
     */
    @Test
    void aFailureWithNoConstraintDoesNotShipAConstraintCalledNull() {
        org.springframework.dao.DataIntegrityViolationException ex =
            new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement [n/a]; SQL [n/a]; constraint [null]",
                new SQLException(NULL_515, "23000", 515));

        ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

        assertThat(pd.getProperties()).doesNotContainKey("constraint");
    }

    /** A real constraint name still reaches the response — the other direction of the same fix. */
    @Test
    void aRealConstraintNameIsStillReported() {
        org.springframework.dao.DataIntegrityViolationException ex =
            new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement [n/a]; SQL [n/a]; "
                + "constraint [CK_meeting_paper_confidentiality]",
                new SQLException("The INSERT statement conflicted with the CHECK constraint "
                    + "\"CK_meeting_paper_confidentiality\". The conflict occurred in database "
                    + "\"TSS_GRC\", table \"admin.committee_meeting_papers\", column "
                    + "'confidentiality'.", "23000", 547));

        ProblemDetail pd = handler.handleDataIntegrityViolation(ex);

        assertThat(pd.getProperties())
            .containsEntry("constraint", "CK_meeting_paper_confidentiality")
            .containsEntry("conflictKind", "VALUE_NOT_ALLOWED");
        assertThat(pd.getDetail())
            .as("F2-180's arm is untouched and still names its column")
            .contains("confidentiality");
    }

    /**
     * The narrowing that keeps this from failing on correct code: a truncation is recognised by its
     * ERROR NUMBER, never by its exception class or its SQLSTATE — both of which it shares with a
     * genuine grammar error and with a missing table. So a missing object still answers 503 and a
     * syntax error still answers 500, although all three arrive as the same Spring class.
     */
    @Test
    void aMissingTableAndASyntaxErrorAreNotMistakenForATruncation() {
        InvalidDataAccessResourceUsageException missing =
            new InvalidDataAccessResourceUsageException("could not prepare statement",
                new SQLException("Invalid object name 'privacy_lc.dsr_cases'.", "42S02", 208));
        assertThat(handler.handleMissingDbObject(missing).getStatus())
            .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE.value());

        InvalidDataAccessResourceUsageException grammar =
            new InvalidDataAccessResourceUsageException("could not execute query",
                new SQLException("Incorrect syntax near the keyword 'WHERE'.", "42000", 156));
        assertThat(handler.handleMissingDbObject(grammar).getStatus())
            .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    }

    /**
     * Anti-vacuity for the decode itself: one switch, two entry points. If a later reader gives
     * {@code handleMissingDbObject} its own copy of the truncation wording, these two stop agreeing
     * and this fails — which is the only thing stopping the two arms drifting (F2-177).
     */
    @Test
    void bothEntryPointsReachTheSameDecode() {
        ProblemDetail viaResourceUsage = handler.handleMissingDbObject(truncation(2628));
        ProblemDetail viaIntegrity = handler.handleDataIntegrityViolation(
            new org.springframework.dao.DataIntegrityViolationException(
                "could not execute statement",
                new SQLException(TRUNCATION_2628, "42000", 2628)));

        assertThat(viaIntegrity.getStatus()).isEqualTo(viaResourceUsage.getStatus());
        assertThat(viaIntegrity.getDetail()).isEqualTo(viaResourceUsage.getDetail());
        assertThat(viaIntegrity.getProperties().get("conflictKind"))
            .isEqualTo(viaResourceUsage.getProperties().get("conflictKind"));
    }

    /** A genuine DateTimeParseException, built by triggering a real parse (its constructor is fiddly). */
    private static java.time.format.DateTimeParseException parseFailure(String bad) {
        try {
            java.time.LocalDate.parse(bad);
        } catch (java.time.format.DateTimeParseException e) {
            return e;
        }
        throw new AssertionError("expected LocalDate.parse(\"" + bad + "\") to fail");
    }
}
