package com.tss.grc.module.ethics.whistleblower.repository;

import com.tss.grc.module.ethics.whistleblower.entity.WhistleblowerCase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WhistleblowerCaseRepository extends JpaRepository<WhistleblowerCase, Long> {

    List<WhistleblowerCase> findByOrganisationIdAndIsDeletedFalseOrderBySubmittedAtDesc(Long organisationId);

    List<WhistleblowerCase> findByOrganisationIdAndStatusAndIsDeletedFalseOrderBySubmittedAtDesc(
            Long organisationId, String status);

    List<WhistleblowerCase> findByOrganisationIdAndSeverityAndIsDeletedFalseOrderBySubmittedAtDesc(
            Long organisationId, String severity);

    List<WhistleblowerCase> findByOrganisationIdAndCategoryAndIsDeletedFalseOrderBySubmittedAtDesc(
            Long organisationId, String category);

    Optional<WhistleblowerCase> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    /**
     * Public-portal lookup. The token itself is unique across all tenants
     * (UUID) and IS the credential — the caller is NOT in a TenantContext.
     */
    Optional<WhistleblowerCase> findBySubmissionTokenAndIsDeletedFalse(UUID submissionToken);

    boolean existsByOrganisationIdAndCaseCodeAndIsDeletedFalse(Long organisationId, String caseCode);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);

    long countByOrganisationIdAndStatusAndIsDeletedFalse(Long organisationId, String status);
}
