package com.tss.grc.module.compliance.obligation.repository;

import com.tss.grc.module.compliance.obligation.entity.ObligationAttestation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped repository for {@link ObligationAttestation}.
 *
 * <p>Each finder takes {@code organisationId} explicitly. Soft-deleted
 * rows are already hidden by the entity's {@code @Where} clause.
 */
public interface ObligationAttestationRepository extends JpaRepository<ObligationAttestation, Long> {

    List<ObligationAttestation> findByOrganisationIdAndIsDeletedFalseOrderByDueDateAsc(Long organisationId);

    List<ObligationAttestation> findByOrganisationIdAndCampaignIdAndIsDeletedFalseOrderByDueDateAsc(
        Long organisationId, Long campaignId);

    List<ObligationAttestation> findByOrganisationIdAndObligationIdAndIsDeletedFalseOrderByDueDateDesc(
        Long organisationId, Long obligationId);

    List<ObligationAttestation> findByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalseOrderByDueDateAsc(
        Long organisationId, Long campaignId, String status);

    List<ObligationAttestation> findByOrganisationIdAndCampaignIdAndAttestationStatusInAndIsDeletedFalse(
        Long organisationId, Long campaignId, List<String> statuses);

    /** Pre-due reminder query — anything DUE / IN_PROGRESS with due_date before cutoff. */
    List<ObligationAttestation> findByOrganisationIdAndCampaignIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
        Long organisationId, Long campaignId, List<String> statuses, LocalDate dueDateCutoff);

    Optional<ObligationAttestation> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<ObligationAttestation> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
        Long organisationId, UUID externalId);

    long countByOrganisationIdAndCampaignIdAndAttestationStatusAndIsDeletedFalse(
        Long organisationId, Long campaignId, String status);

    long countByOrganisationIdAndCampaignIdAndAttestationStatusInAndIsDeletedFalse(
        Long organisationId, Long campaignId, List<String> statuses);

    long countByOrganisationIdAndAttestationStatusAndIsDeletedFalse(Long organisationId, String status);

    /** Distinct tenants with at least one attestation — drives the deadline scheduler's per-tenant loop. */
    @Query("select distinct a.organisationId from ObligationAttestation a")
    List<Long> findDistinctOrganisationIds();

    /** Org-wide overdue sweep: DUE / IN_PROGRESS cycles whose due_date is before the cutoff. */
    List<ObligationAttestation> findByOrganisationIdAndAttestationStatusInAndDueDateBeforeAndIsDeletedFalse(
        Long organisationId, List<String> statuses, LocalDate dueDateCutoff);
}
