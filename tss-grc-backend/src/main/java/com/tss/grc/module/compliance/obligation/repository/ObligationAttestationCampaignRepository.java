package com.tss.grc.module.compliance.obligation.repository;

import com.tss.grc.module.compliance.obligation.entity.ObligationAttestationCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link ObligationAttestationCampaign}.
 *
 * <p>Each finder takes {@code organisationId} explicitly per the
 * platform tenant-scope contract.
 */
public interface ObligationAttestationCampaignRepository
    extends JpaRepository<ObligationAttestationCampaign, Long> {

    List<ObligationAttestationCampaign> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<ObligationAttestationCampaign> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String status);

    Optional<ObligationAttestationCampaign> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    Optional<ObligationAttestationCampaign> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    boolean existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(
        Long organisationId, String campaignCode);
}
