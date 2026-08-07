package com.tss.grc.module.ai.governance.service;

import com.tss.grc.module.ai.governance.repository.MlModelRepository;
import com.tss.grc.security.EntityOwnershipResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.UUID;

/**
 * F2-159 / ADR-017 D-14 — the first {@link EntityOwnershipResolver}: who owns an ML model.
 *
 * <p>D-14 refused to seat a {@code MODEL_OWNER} role because the platform already holds the answer per
 * record: {@code ai_governance.ml_model.model_owner_user_id} is {@code NOT NULL} and populated on
 * <b>25 of 25</b> rows, resolving to real {@code iam.users}. A role would have been <em>looser</em> than
 * the column — the owner of model A could act on model B — so the guard had to become entity-bound, and
 * the mechanism for that did not exist until now.
 *
 * <p><b>Why this measurably changes access rather than tidying a guard.</b> The 14 sites that recited
 * {@code MODEL_OWNER} were pure {@code hasAnyRole} guards, so the name matched nothing and the model's
 * own owner was refused their own model. Dev's 25 models are owned by two users: {@code admin} (19,
 * irrelevant — {@code PLATFORM_ADMIN} bypasses) and <b>{@code ranalyst} (6)</b>. {@code ranalyst} holds
 * {@code AI:READ} and {@code AI:WRITE} and <em>still</em> could not open a model it owns, because the
 * guard asks who you are and not what you may do — F2-05's thesis in one persona.
 */
@Component
@RequiredArgsConstructor
public class MlModelOwnershipResolver implements EntityOwnershipResolver {

    /** As written in the guards: {@code hasPermission(#externalId, 'ML_MODEL', 'OWNER')}. */
    public static final String TARGET_TYPE = "ML_MODEL";

    private final MlModelRepository repository;

    @Override
    public String targetType() {
        return TARGET_TYPE;
    }

    /**
     * <p>The id is the {@code externalId} the route already carries — a UUID, and the value the client
     * sends. It is parsed defensively: a malformed id is <b>not owned</b>, not an exception. This runs
     * before the rest of the expression has decided anything, so throwing here would turn a failed
     * ownership leg into a 500 on an endpoint the caller may legitimately reach by role.
     *
     * <p>Scoped by {@code organisationId} even though {@code external_id} is globally unique across the
     * 25 rows. Uniqueness today is not a guarantee tomorrow, and this is the one place a raw
     * client-supplied identifier is looked up before any other authorisation has run — F2-04a's
     * cross-tenant lesson applies with the least margin here.
     */
    @Override
    @Transactional(readOnly = true)
    public boolean isOwnedBy(Serializable id, Long userId, Long organisationId) {
        UUID externalId = parse(id);
        if (externalId == null) return false;
        return repository.findByOrganisationIdAndExternalIdAndIsDeletedFalse(organisationId, externalId)
            .map(m -> userId.equals(m.getModelOwnerUserId()))
            .orElse(false);
    }

    private static UUID parse(Serializable id) {
        if (id instanceof UUID u) return u;
        try {
            return UUID.fromString(id.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
