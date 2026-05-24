package com.tss.grc.module.ai.governance.repository;

import com.tss.grc.module.ai.governance.entity.MlModelDriftObservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MlModelDriftObservationRepository
        extends JpaRepository<MlModelDriftObservation, Long> {

    List<MlModelDriftObservation> findByOrganisationIdAndModelIdAndIsDeletedFalseOrderByObservedAtDesc(
            Long organisationId, Long modelId);

    Optional<MlModelDriftObservation> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
            Long organisationId, UUID externalId);

    /** Tenant-wide unacknowledged drift inbox — drives the amber KPI tile. */
    List<MlModelDriftObservation> findByOrganisationIdAndIsAcknowledgedFalseAndIsDeletedFalseOrderByObservedAtDesc(
            Long organisationId);
}
