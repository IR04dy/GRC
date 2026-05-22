package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.AwarenessCampaign;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface AwarenessCampaignRepository extends JpaRepository<AwarenessCampaign, Long> {

    Optional<AwarenessCampaign> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<AwarenessCampaign> findByOrganisationIdAndCampaignCode(Long organisationId, String campaignCode);

    Page<AwarenessCampaign> findByOrganisationIdOrderByStartedAtDesc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
