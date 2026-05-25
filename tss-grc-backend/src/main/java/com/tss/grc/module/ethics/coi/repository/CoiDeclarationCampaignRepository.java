package com.tss.grc.module.ethics.coi.repository;

import com.tss.grc.module.ethics.coi.entity.CoiDeclarationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoiDeclarationCampaignRepository extends JpaRepository<CoiDeclarationCampaign, Long> {

    List<CoiDeclarationCampaign> findByOrganisationIdAndIsDeletedFalse(Long organisationId);

    List<CoiDeclarationCampaign> findByOrganisationIdAndStatus(Long organisationId, String status);

    List<CoiDeclarationCampaign> findByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);

    Optional<CoiDeclarationCampaign> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    boolean existsByOrganisationIdAndCodeAndIsDeletedFalse(Long organisationId, String code);
}
