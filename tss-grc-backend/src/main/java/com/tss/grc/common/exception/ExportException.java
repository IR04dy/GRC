package com.tss.grc.common.exception;

/**
 * Thrown when document generation (PDF / Excel / PPTX / DOCX) fails.
 *
 * <p>Replaces the historical pattern of {@code throw new RuntimeException("X
 * generation failed", e)} scattered across export services. The raw
 * RuntimeException surfaced via {@link GlobalExceptionHandler#handleGeneral}
 * as a generic "An unexpected error occurred" message, which made it
 * impossible for users to tell the difference between an export bug and a
 * platform outage.
 *
 * <p>{@link GlobalExceptionHandler} maps this to HTTP 500 with a clear
 * "Document generation failed" message and includes the failed
 * {@code documentKind} ("PDF", "Excel", "PPTX") so toasts can show
 * "PDF export failed — try again or contact support".
 */
public class ExportException extends RuntimeException {

    private final String documentKind;

    public ExportException(String documentKind, String message, Throwable cause) {
        super(message, cause);
        this.documentKind = documentKind;
    }

    public String getDocumentKind() { return documentKind; }
}
