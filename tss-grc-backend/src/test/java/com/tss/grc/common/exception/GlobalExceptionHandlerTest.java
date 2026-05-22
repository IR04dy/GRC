package com.tss.grc.common.exception;

import org.junit.jupiter.api.Test;
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
}
