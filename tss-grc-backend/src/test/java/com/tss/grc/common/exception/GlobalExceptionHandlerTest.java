package com.tss.grc.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.InvalidDataAccessResourceUsageException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Locks the user-visible error response shape for the most commonly seen
 * exceptions. The frontend reads {@code title} + {@code detail} into a
 * toast, so the wording matters for the UX.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

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
    void missingDbObject_sqlServerSignature_returns503() {
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
}
