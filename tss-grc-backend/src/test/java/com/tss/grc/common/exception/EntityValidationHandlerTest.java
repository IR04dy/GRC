package com.tss.grc.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.transaction.TransactionSystemException;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * F2-30 — an entity-level constraint must surface as a 400 that names the field.
 *
 * <p>The reason this test exists rather than a live probe: the failure it pins is
 * <em>where the exception is thrown from</em>, and only one of the two paths is reachable
 * through a controller. A {@code @Valid @RequestBody} failure is caught by Spring MVC before
 * the service runs, so it never reaches the transaction. A constraint that fails at
 * <b>flush</b> — a service-layer save, a scheduler, a seeder, any write path without
 * {@code @Valid} — is thrown inside the transaction and arrives at the advice wrapped in
 * {@link TransactionSystemException}. Nothing handled that wrapper, so it fell through to the
 * catch-all as a bare <b>500</b>: no field name, no {@code errors} map, nothing the frontend's
 * {@code problemMessage()} could put in a toast.
 *
 * <p>The pre-existing {@code handleConstraint} looked like it covered this and did not — its
 * own javadoc claimed it stopped "Hibernate's constraint failures" reaching 500, but it only
 * catches a violation that propagates unwrapped. Adding {@code @NotBlank} to entity fields
 * without closing the wrapped leg would have turned "you forgot the Arabic title" into an
 * opaque server error, which is worse than not detecting it at all.
 */
class EntityValidationHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private static ConstraintViolation<?> violation(String path, String message) {
        Path p = mock(Path.class);
        when(p.toString()).thenReturn(path);
        ConstraintViolation<?> v = mock(ConstraintViolation.class);
        when(v.getPropertyPath()).thenReturn(p);
        when(v.getMessage()).thenReturn(message);
        return v;
    }

    @Test
    void a_constraint_that_fails_at_flush_is_a_400_naming_the_field_not_a_500() {
        var cve = new ConstraintViolationException(
            Set.of(violation("titleAr", "must not be blank")));
        // exactly how Spring presents it: wrapped by the transaction interceptor at commit
        var wrapped = new TransactionSystemException("Could not commit JPA transaction", cve);

        ProblemDetail pd = handler.handleTransactionSystem(wrapped);

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(pd.getTitle()).isEqualTo("Validation Failed");
        assertThat(pd.getDetail()).contains("titleAr").contains("must not be blank");
    }

    @Test
    void the_errors_map_is_keyed_by_field_so_the_frontend_toast_can_name_it() {
        var cve = new ConstraintViolationException(Set.of(
            violation("titleAr", "must not be blank"),
            violation("bodyAr", "must not be blank")));

        ProblemDetail pd = handler.handleTransactionSystem(
            new TransactionSystemException("commit failed", cve));

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) pd.getProperties().get("errors");
        assertThat(errors).containsEntry("titleAr", "must not be blank")
                          .containsEntry("bodyAr", "must not be blank");
    }

    @Test
    void the_violation_is_found_however_deeply_it_is_nested() {
        var cve = new ConstraintViolationException(Set.of(violation("nameAr", "must not be blank")));
        var deep = new TransactionSystemException("outer",
            new IllegalStateException("middle", new RuntimeException("inner", cve)));

        assertThat(handler.handleTransactionSystem(deep).getStatus())
            .isEqualTo(HttpStatus.BAD_REQUEST.value());
    }

    /**
     * The handler must not dress every commit failure up as a client error. A deadlock, a
     * connection drop or a rollback-only transaction is a server fault and stays a 500 —
     * reporting it as 400 would tell the user to fix their input for a problem that is not
     * theirs, and would hide a real outage behind a validation message.
     */
    @Test
    void a_commit_failure_with_no_constraint_violation_stays_a_500() {
        var pd = handler.handleTransactionSystem(
            new TransactionSystemException("Could not commit", new RuntimeException("deadlock")));

        assertThat(pd.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(pd.getTitle()).isEqualTo("Transaction Failed");
    }

    @Test
    void an_unwrapped_violation_answers_identically_to_a_wrapped_one() {
        var cve = new ConstraintViolationException(Set.of(violation("titleAr", "must not be blank")));

        ProblemDetail unwrapped = handler.handleConstraint(cve);
        ProblemDetail wrapped = handler.handleTransactionSystem(
            new TransactionSystemException("commit failed", cve));

        assertThat(unwrapped.getStatus()).isEqualTo(wrapped.getStatus());
        assertThat(unwrapped.getDetail()).isEqualTo(wrapped.getDetail());
        assertThat(unwrapped.getProperties().get("errors"))
            .isEqualTo(wrapped.getProperties().get("errors"));
    }
}
