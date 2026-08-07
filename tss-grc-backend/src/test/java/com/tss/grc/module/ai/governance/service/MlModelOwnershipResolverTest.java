package com.tss.grc.module.ai.governance.service;

import com.tss.grc.module.ai.governance.entity.MlModel;
import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F2-159 — the resolver that actually answers the ownership question.
 *
 * <h3>⚠ Why this class exists, which is a lesson about my own test coverage</h3>
 * <p>{@code ModulePermissionEvaluatorTest} exercises the mechanism against a <b>fake</b> resolver, and
 * {@code MlModelOwnershipGuardTest} pins which endpoints carry the ownership leg. Neither touches the one
 * real implementation. The mutation run proved it: dropping the {@code organisationId} filter and
 * replacing the owner comparison with a null check — a cross-tenant ownership check and a check that only
 * asks whether the record <em>exists</em> — both left the suite <b>green</b>. Only the live probe would
 * have caught them, and a probe runs when somebody runs it.
 *
 * <p>🔑 <b>Testing the abstraction is not testing the implementation, and a fake makes that gap
 * invisible</b> — the fake behaved correctly, so every assertion above it passed while the code that ships
 * was unasserted. Same shape as F2-156 one level down: the thing that mirrors the decision was fine, the
 * decision itself was not measured.
 */
class MlModelOwnershipResolverTest {

    private static final UUID MODEL = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final Long OWNER = 7L;
    private static final Long ORG = 1L;

    private MlModelRepository repo;
    private MlModelOwnershipResolver resolver;

    @BeforeEach
    void setUp() {
        repo = mock(MlModelRepository.class);
        resolver = new MlModelOwnershipResolver(repo);
        MlModel m = new MlModel();
        m.setExternalId(MODEL);
        m.setOrganisationId(ORG);
        m.setModelOwnerUserId(OWNER);
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG, MODEL))
            .thenReturn(Optional.of(m));
        // Any other (org, id) pair resolves to nothing, which is what the real org-scoped finder does.
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(eq(2L), any()))
            .thenReturn(Optional.empty());
    }

    @Test
    void the_recorded_owner_owns_the_record() {
        assertThat(resolver.isOwnedBy(MODEL.toString(), OWNER, ORG)).isTrue();
    }

    @Test
    void a_different_user_does_not_own_the_same_record() {
        // The property a MODEL_OWNER role could never have expressed, and D-14's whole reason for
        // refusing to seat one: a role admits every holder to every record.
        assertThat(resolver.isOwnedBy(MODEL.toString(), 8L, ORG)).isFalse();
    }

    @Test
    void the_lookup_is_scoped_by_organisation() {
        assertThat(resolver.isOwnedBy(MODEL.toString(), OWNER, 2L)).isFalse();
        // Not just the answer — the QUERY must carry the tenant. A resolver that fetched globally and
        // filtered afterwards would pass the assertion above while reading another tenant's row.
        verify(repo).findByOrganisationIdAndExternalIdAndIsDeletedFalse(2L, MODEL);
    }

    @Test
    void an_unknown_record_is_not_owned() {
        UUID absent = UUID.fromString("99999999-9999-9999-9999-999999999999");
        when(repo.findByOrganisationIdAndExternalIdAndIsDeletedFalse(ORG, absent))
            .thenReturn(Optional.empty());
        assertThat(resolver.isOwnedBy(absent.toString(), OWNER, ORG)).isFalse();
    }

    @Test
    void a_malformed_id_is_not_owned_and_does_not_throw() {
        // The id is client-supplied and this runs before the rest of the guard expression has decided
        // anything, so throwing here turns a failed ownership leg into a 500 on an endpoint the caller
        // may legitimately reach by role.
        assertThat(resolver.isOwnedBy("not-a-uuid", OWNER, ORG)).isFalse();
        assertThat(resolver.isOwnedBy("", OWNER, ORG)).isFalse();
    }

    @Test
    void a_UUID_id_is_accepted_as_well_as_its_string_form() {
        assertThat(resolver.isOwnedBy(MODEL, OWNER, ORG)).isTrue();
    }

    @Test
    void it_registers_under_the_type_the_guards_name() {
        // The guards say hasPermission(#externalId, 'ML_MODEL', 'OWNER'); a rename here without a
        // matching guard edit would silently refuse every owner, because an unregistered type is denied.
        assertThat(resolver.targetType()).isEqualTo("ML_MODEL");
        assertThat(MlModelOwnershipResolver.TARGET_TYPE).isEqualTo("ML_MODEL");
    }
}
