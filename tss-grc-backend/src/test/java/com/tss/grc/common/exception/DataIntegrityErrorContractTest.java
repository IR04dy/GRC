package com.tss.grc.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * F2-88 — a constraint the database enforces and the code did not check must not be a 500.
 *
 * <p><b>Reproduced before it was fixed:</b> {@code POST /api/admin/committees} with the same payload
 * twice returned 201 then <b>500 "An unexpected database error occurred."</b> The unique index is
 * enforced at flush, so the failure arrives as a {@link DataIntegrityViolationException} and fell
 * into the generic database handler's else-branch. It sits on the first step of tenant onboarding
 * for change governance — the step F2-87's fail-closed refusal routes an administrator to — and
 * W1-5's golden path hit it on its second consecutive run, which is what makes it worth a status
 * code rather than a note: it is what the second run of anything looks like.
 *
 * <p><b>Why the class matters more than the endpoint.</b> Measured on this database: <b>264 unique
 * non-PK indexes, 124 UNIQUE constraints, 181 foreign keys</b>. Each is a future 500 of this exact
 * shape, which is why the fix is a mapping for the whole family and not only a pre-flight.
 */
class DataIntegrityErrorContractTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    /** The real shape: Spring's translated message ends in {@code constraint [NAME]}. */
    private static DataIntegrityViolationException violation(int sqlErrorCode, String constraint) {
        SQLException sql = new SQLException(
            "Violation of UNIQUE KEY constraint '" + constraint + "'. Cannot insert duplicate key "
            + "in object 'admin.committees'. The duplicate key value is (1, CMT-X).",
            "23000", sqlErrorCode);
        return new DataIntegrityViolationException(
            "could not execute statement [...]; constraint [" + constraint + "]", sql);
    }

    @Test
    void aDuplicateKeyIsAConflictAndNotAServerError() {
        ProblemDetail pd = handler.handleDataIntegrityViolation(violation(2627, "UQ_com_code"));

        assertThat(pd.getStatus())
            .as("201-then-500 on the same payload told the caller the server was broken when the "
              + "name was simply taken")
            .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "UNIQUE");
        assertThat(pd.getProperties())
            .as("the constraint identifier is what makes the 409 actionable for support")
            .containsEntry("constraint", "UQ_com_code");
    }

    /**
     * The response must not carry the driver's message. It contains the schema <em>and the duplicate
     * values</em> — tenant data — which is exactly what prod hardening strips root messages for.
     */
    @Test
    void theResponseNeverEchoesTheDatabaseMessageOrTheDuplicateValues() {
        ProblemDetail pd = handler.handleDataIntegrityViolation(violation(2627, "UQ_com_code"));

        assertThat(pd.getDetail())
            .doesNotContain("CMT-X")
            .doesNotContain("admin.committees")
            .doesNotContain("duplicate key value")
            .doesNotContain("could not execute statement");
        assertThat(pd.getDetail())
            .as("and it must still tell the caller what to do")
            .contains("already exists")
            .contains("unique");
    }

    @Test
    void theThreeFailureKindsGetTheStatusTheCallerCanActOn() {
        // 2601 — unique INDEX rather than a UNIQUE KEY constraint. Same remedy, same status; a
        // handler that only knew 2627 would 500 on half the platform's 264 unique indexes.
        assertThat(handler.handleDataIntegrityViolation(violation(2601, "IX_uq_x")).getStatus())
            .isEqualTo(HttpStatus.CONFLICT.value());

        ProblemDetail fk = handler.handleDataIntegrityViolation(violation(547, "FK_a_b"));
        assertThat(fk.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(fk.getProperties()).containsEntry("conflictKind", "REFERENCE");

        // A NOT NULL violation is the caller's malformed request, not a conflict — 400.
        ProblemDetail notNull = handler.handleDataIntegrityViolation(violation(515, null));
        assertThat(notNull.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(notNull.getProperties()).containsEntry("conflictKind", "REQUIRED_FIELD");
    }

    @Test
    void anUnrecognisedIntegrityErrorStillFailsAsAConflictRatherThanA500() {
        ProblemDetail pd = handler.handleDataIntegrityViolation(violation(99999, "CK_something"));

        assertThat(pd.getStatus())
            .as("the default arm is the whole point of a backstop — an unmapped integrity error "
              + "must not fall back to the generic 500 this finding was about")
            .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "INTEGRITY");
    }

    @Test
    void aViolationWithNoSqlExceptionInTheChainDoesNotThrowWhileBeingHandled() {
        // An exception handler that throws turns a 409 into a 500 and loses the original entirely.
        ProblemDetail pd = handler.handleDataIntegrityViolation(
            new DataIntegrityViolationException("no cause, no constraint suffix"));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).containsEntry("conflictKind", "INTEGRITY");
        assertThat(pd.getProperties())
            .as("no constraint could be parsed, so the property must be absent rather than null")
            .doesNotContainKey("constraint");
    }

    @Test
    void thePreFlightedHalfNamesTheFieldWhichTheBackstopCannot() {
        ProblemDetail pd = handler.handleDuplicateResource(
            new DuplicateResourceException("code",
                "A committee with code 'CAB-1' already exists in this organisation."));

        assertThat(pd.getStatus())
            .as("BusinessRuleException would have said 403 — telling an administrator they lack "
              + "permission when the name is simply taken")
            .isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(pd.getProperties()).containsEntry("field", "code");
        assertThat(pd.getProperties())
            .as("same conflictKind as the generic handler, so a client can treat both halves alike")
            .containsEntry("conflictKind", "UNIQUE");
        assertThat(pd.getProperties())
            .as("the `errors` map is the shape the FE's problemMessage() already folds into a toast")
            .hasEntrySatisfying("errors", v ->
                assertThat(v.toString()).contains("code"));
    }
}
