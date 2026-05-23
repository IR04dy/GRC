package com.tss.grc.module.bcm.comms.repository;

import com.tss.grc.module.bcm.comms.entity.CrisisCommsCascade;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link CrisisCommsCascade} (Slice 9-E).
 */
public interface CrisisCommsCascadeRepository extends JpaRepository<CrisisCommsCascade, Long> {

    List<CrisisCommsCascade> findByOrganisationIdAndIsDeletedFalseOrderByDispatchedAtDesc(Long organisationId);

    List<CrisisCommsCascade> findByOrganisationIdAndCrisisEventIdAndIsDeletedFalseOrderByDispatchedAtDesc(
        Long organisationId, Long crisisEventId);

    Optional<CrisisCommsCascade> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);
}
