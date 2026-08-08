package com.tss.grc.module.bcm.cycle.runtime.service;

import com.tss.grc.module.bcm.cycle.runtime.entity.ExerciseRun;
import com.tss.grc.module.bcm.cycle.runtime.repository.ExerciseRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-161 — the second {@link com.tss.grc.security.EntityOwnershipResolver}, unit-tested for the reason
 * F2-159 had to learn the hard way.
 *
 * <p>There, the mechanism was tested against a <b>fake</b> resolver and the guard shape by reflection, so
 * the one shipping implementation was unasserted — and mutation proved it: dropping the
 * {@code organisationId} filter and reducing ownership to mere <em>existence</em> both left the suite
 * green. 🔑 <b>Testing the abstraction is not testing the implementation, and a well-behaved fake hides
 * the gap.</b> So the second resolver got its own test before it was probed, not after.
 */
class ExerciseRunOwnershipResolverTest {

    private static final Long RUN = 7L;
    private static final Long FACILITATOR = 42L;
    private static final Long ORG = 1L;

    private ExerciseRunRepository repo;
    private ExerciseRunOwnershipResolver resolver;

    @BeforeEach
    void setUp() {
        repo = mock(ExerciseRunRepository.class);
        resolver = new ExerciseRunOwnershipResolver(repo);
        ExerciseRun run = new ExerciseRun();
        run.setId(RUN);
        run.setOrganisationId(ORG);
        run.setFacilitatorUserId(FACILITATOR);
        when(repo.findByIdAndOrganisationId(RUN, ORG)).thenReturn(Optional.of(run));
        when(repo.findByIdAndOrganisationId(any(), eq(2L))).thenReturn(Optional.empty());
    }

    @Test
    void the_recorded_facilitator_owns_the_run() {
        assertThat(resolver.isOwnedBy(RUN, FACILITATOR, ORG)).isTrue();
    }

    @Test
    void another_user_does_not_own_the_same_run() {
        // The per-record property a FACILITATOR role could never express: with a role, the facilitator of
        // run 1 could drive run 4, which during a live exercise is the one thing the runtime must refuse.
        assertThat(resolver.isOwnedBy(RUN, 99L, ORG)).isFalse();
    }

    @Test
    void the_lookup_is_scoped_by_organisation_in_the_query_itself() {
        assertThat(resolver.isOwnedBy(RUN, FACILITATOR, 2L)).isFalse();
        // Not just the answer: a resolver that fetched by id alone and filtered afterwards would satisfy
        // the assertion above while having READ another tenant's run.
        verify(repo).findByIdAndOrganisationId(RUN, 2L);
    }

    @Test
    void an_unknown_run_is_not_owned() {
        when(repo.findByIdAndOrganisationId(404L, ORG)).thenReturn(Optional.empty());
        assertThat(resolver.isOwnedBy(404L, FACILITATOR, ORG)).isFalse();
    }

    @Test
    void a_run_with_no_facilitator_is_owned_by_nobody() {
        // facilitator_user_id is populated 4/4 in dev but the column is nullable, and "null equals the
        // caller" must never be true. A null-tolerant comparison here would hand every unfacilitated run
        // to whoever asked first.
        ExerciseRun orphan = new ExerciseRun();
        orphan.setId(8L);
        orphan.setOrganisationId(ORG);
        orphan.setFacilitatorUserId(null);
        when(repo.findByIdAndOrganisationId(8L, ORG)).thenReturn(Optional.of(orphan));
        assertThat(resolver.isOwnedBy(8L, FACILITATOR, ORG)).isFalse();
    }

    @Test
    void the_id_is_accepted_as_a_Long_or_its_string_form_and_a_bad_one_does_not_throw() {
        // The guard is a string expression, so nothing forces the id to arrive as a Long. Throwing would
        // turn a failed ownership leg into a 500 on an endpoint the caller may reach by role.
        assertThat(resolver.isOwnedBy(RUN, FACILITATOR, ORG)).isTrue();
        assertThat(resolver.isOwnedBy(String.valueOf(RUN), FACILITATOR, ORG)).isTrue();
        assertThat(resolver.isOwnedBy("not-a-number", FACILITATOR, ORG)).isFalse();
        assertThat(resolver.isOwnedBy("", FACILITATOR, ORG)).isFalse();
    }

    @Test
    void it_registers_under_the_type_the_guards_name() {
        // A rename here without a matching guard edit silently refuses every facilitator, because the
        // registry denies an unregistered target type.
        assertThat(resolver.targetType()).isEqualTo("EXERCISE_RUN");
        assertThat(ExerciseRunOwnershipResolver.TARGET_TYPE).isEqualTo("EXERCISE_RUN");
    }
}
