package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * S8e / CE-2 — the model catalogue. Every query filters {@code organisationId} (CLAUDE.md:
 * developer responsibility); the soft-delete filter comes from {@code @Where} on the entity.
 */
public interface ChangeModelRepository extends JpaRepository<ChangeModel, Long> {

    List<ChangeModel> findByOrganisationIdOrderByCodeAsc(Long organisationId);

    Optional<ChangeModel> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Resolution by the key a change cites. Backed by {@code ux_change_model_code}. */
    Optional<ChangeModel> findByOrganisationIdAndCode(Long organisationId, String code);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
