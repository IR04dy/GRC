package com.tss.grc.module.ethics.coi.repository;

import com.tss.grc.module.ethics.coi.entity.CoiDeclaration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CoiDeclarationRepository extends JpaRepository<CoiDeclaration, Long> {

    List<CoiDeclaration> findByOrganisationIdAndIsDeletedFalse(Long organisationId);

    List<CoiDeclaration> findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDeclarationDateDesc(
            Long organisationId, Long campaignId);

    List<CoiDeclaration> findByOrganisationIdAndUserIdAndIsDeletedFalseOrderByDeclarationDateDesc(
            Long organisationId, Long userId);

    List<CoiDeclaration> findByOrganisationIdAndReviewOutcomeAndIsDeletedFalseOrderByDeclarationDateDesc(
            Long organisationId, String reviewOutcome);

    Optional<CoiDeclaration> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    long countByOrganisationIdAndCampaignIdAndIsDeletedFalse(Long organisationId, Long campaignId);
}
