package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.FinancedEmissionsTarget;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancedEmissionsTargetRepository extends JpaRepository<FinancedEmissionsTarget, Long> {

    List<FinancedEmissionsTarget> findByOrganisationIdAndIsDeletedFalseOrderByTargetYearAsc(Long organisationId);

    Optional<FinancedEmissionsTarget> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<FinancedEmissionsTarget> findByOrganisationIdAndTargetCodeAndIsDeletedFalse(Long organisationId, String targetCode);

    long countByOrganisationIdAndTargetCode(Long organisationId, String targetCode);
}
