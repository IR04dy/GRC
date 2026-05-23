package com.tss.grc.module.tprm.bcp_attestation.repository;

import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpCampaign;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link VendorBcpCampaign} (Slice 10-D).
 *
 * <p>Every finder explicitly takes {@code organisationId} — the platform
 * does not run a global Hibernate tenant filter (per CLAUDE.md). Soft-
 * deleted rows are already hidden by the entity's
 * {@code @Where(is_deleted = 0)} clause.
 */
public interface VendorBcpCampaignRepository extends JpaRepository<VendorBcpCampaign, Long> {

    List<VendorBcpCampaign> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    List<VendorBcpCampaign> findByOrganisationIdAndStatusAndIsDeletedFalseOrderByCreatedAtDesc(
        Long organisationId, String status);

    Optional<VendorBcpCampaign> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    Optional<VendorBcpCampaign> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    boolean existsByOrganisationIdAndCampaignCodeAndIsDeletedFalse(
        Long organisationId, String campaignCode);
}
