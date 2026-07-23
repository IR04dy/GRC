package com.tss.grc.module.change_mgmt.repository;

import com.tss.grc.module.change_mgmt.entity.ChangeAuthorisationMatrix;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChangeAuthorisationMatrixRepository extends JpaRepository<ChangeAuthorisationMatrix, Long> {
    Optional<ChangeAuthorisationMatrix> findByOrganisationIdAndChangeTypeAndRiskLevel(
        Long organisationId, String changeType, String riskLevel);
    List<ChangeAuthorisationMatrix> findByOrganisationIdOrderByChangeTypeAscRiskLevelAsc(Long organisationId);
}
