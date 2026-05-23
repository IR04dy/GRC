package com.tss.grc.module.tprm.bcp_attestation.repository;

import com.tss.grc.module.tprm.bcp_attestation.entity.VendorBcpAttestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link VendorBcpAttestation} (Slice 10-D).
 *
 * <p>Each finder takes {@code organisationId} explicitly. Soft-deleted
 * rows are already hidden by the entity's {@code @Where} clause. Token
 * lookups (used by the vendor portal endpoint) are intentionally
 * unscoped — the token IS the credential.
 */
public interface VendorBcpAttestationRepository extends JpaRepository<VendorBcpAttestation, Long> {

    List<VendorBcpAttestation> findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByCreatedAtAsc(
        Long organisationId, Long campaignId);

    List<VendorBcpAttestation> findByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalseOrderByCreatedAtAsc(
        Long organisationId, Long campaignId, String status);

    List<VendorBcpAttestation> findByOrganisationIdAndCampaignIdAndStatusInAndIsDeletedFalseOrderByCreatedAtAsc(
        Long organisationId, Long campaignId, List<String> statuses);

    /** "Vendors past due for first reminder" — service uses this for the reminder job. */
    List<VendorBcpAttestation> findByOrganisationIdAndCampaignIdAndStatusInAndInvitedAtBeforeAndIsDeletedFalse(
        Long organisationId, Long campaignId, List<String> statuses, LocalDateTime cutoff);

    Optional<VendorBcpAttestation> findByOrganisationIdAndIdAndIsDeletedFalse(
        Long organisationId, Long id);

    Optional<VendorBcpAttestation> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    /** Token lookup — the vendor portal endpoint resolves the row from this. */
    Optional<VendorBcpAttestation> findBySubmissionTokenAndIsDeletedFalse(UUID submissionToken);

    long countByOrganisationIdAndCampaignIdAndStatusAndIsDeletedFalse(
        Long organisationId, Long campaignId, String status);

    long countByOrganisationIdAndCampaignIdAndStatusInAndIsDeletedFalse(
        Long organisationId, Long campaignId, List<String> statuses);
}
