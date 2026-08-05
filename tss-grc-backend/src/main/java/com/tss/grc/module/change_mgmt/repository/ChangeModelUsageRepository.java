package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeModelUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** S8e / CE-2 — change ↔ model bindings. One live row per change ({@code ux_..._one_per_change}). */
public interface ChangeModelUsageRepository extends JpaRepository<ChangeModelUsage, Long> {

    Optional<ChangeModelUsage> findByOrganisationIdAndChangeRequestId(
        Long organisationId, Long changeRequestId);

    List<ChangeModelUsage> findByOrganisationIdAndChangeModelId(
        Long organisationId, Long changeModelId);

    /**
     * Every live binding in the tenant — S8f reads them all to check each still rests on an
     * unexpired model, rather than issuing one query per change in the register's population.
     */
    List<ChangeModelUsage> findByOrganisationId(Long organisationId);

    long countByOrganisationId(Long organisationId);
}
