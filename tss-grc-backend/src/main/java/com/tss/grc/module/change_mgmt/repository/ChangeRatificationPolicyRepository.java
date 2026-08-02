package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeRatificationPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/** A17-D3 — per-tenant ratification SLA lookup. Unique on organisation_id (V345). */
public interface ChangeRatificationPolicyRepository extends JpaRepository<ChangeRatificationPolicy, Long> {
    Optional<ChangeRatificationPolicy> findByOrganisationId(Long organisationId);
}
