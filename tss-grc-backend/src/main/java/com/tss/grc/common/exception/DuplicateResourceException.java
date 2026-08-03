package com.tss.grc.common.exception;

/**
 * A uniqueness rule the caller can fix by changing one field — mapped to <b>409 Conflict</b> with
 * the field named.
 *
 * <h3>Why this exists rather than reusing what was there (F2-88)</h3>
 * A duplicate code answered <b>500</b>, because the unique index is enforced at flush and the
 * resulting {@code DataIntegrityViolationException} fell into the generic database handler.
 * {@link GlobalExceptionHandler} now maps that whole class to a 409 as a backstop — but a backstop
 * cannot say <em>which field</em>: it sees {@code UQ_com_code}, not {@code code}. Only the service
 * knows the domain field behind the constraint, so the pre-flight needs its own signal.
 *
 * <p>The obvious candidate, {@link BusinessRuleException}, maps to <b>403 Forbidden</b> — which
 * would tell an administrator they lack permission to constitute a committee when in fact the name
 * is taken. Two different problems must not share a status code; the caller's next action is
 * different in each case.
 *
 * <p>Carries the same {@code conflictKind} vocabulary the generic handler emits, so a client can
 * treat pre-flighted and database-detected duplicates identically and simply prefer {@code field}
 * when it is present.
 */
public class DuplicateResourceException extends RuntimeException {

    /** The request field the caller should change, e.g. {@code "code"}. */
    private final String field;

    public DuplicateResourceException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
