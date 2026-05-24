package com.tss.grc.module.privacy.subprocessor.repository;

import com.tss.grc.module.privacy.subprocessor.entity.Subprocessor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link Subprocessor}.
 *
 * <p>Every read filters by {@code organisation_id}. The
 * {@code @Where(is_deleted = 0)} clause on the entity layers in the
 * soft-delete filter automatically; the
 * {@code isDeletedFalse} suffix in finder names is preserved for
 * Spring Data's method-name parser to bind.
 */
public interface SubprocessorRepository extends JpaRepository<Subprocessor, Long> {

    List<Subprocessor> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    Optional<Subprocessor> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
            Long organisationId, UUID externalId);

    Optional<Subprocessor> findByOrganisationIdAndSubprocessorCodeAndIsDeletedFalse(
            Long organisationId, String subprocessorCode);

    long countByOrganisationIdAndSubprocessorCode(Long organisationId, String subprocessorCode);

    List<Subprocessor> findByOrganisationIdAndLifecycleStageAndIsDeletedFalse(
            Long organisationId, String lifecycleStage);

    List<Subprocessor> findByOrganisationIdAndCountryOfProcessingAndIsDeletedFalse(
            Long organisationId, String countryOfProcessing);

    long countByOrganisationIdAndLifecycleStageAndIsDeletedFalse(
            Long organisationId, String lifecycleStage);

    long countByOrganisationIdAndCountryOfProcessingAndIsDeletedFalse(
            Long organisationId, String countryOfProcessing);

    /**
     * Sub-processors whose 12-month review window has elapsed. The
     * DPO's daily inbox is built on this finder.
     */
    @Query("""
            SELECT s FROM Subprocessor s
             WHERE s.organisationId = :orgId
               AND s.isDeleted = false
               AND s.nextReviewDueAt IS NOT NULL
               AND s.nextReviewDueAt < :cutoff
               AND s.lifecycleStage <> 'RETIRED'
             ORDER BY s.nextReviewDueAt ASC
            """)
    List<Subprocessor> findRequiringReview(@Param("orgId") Long organisationId,
                                            @Param("cutoff") LocalDateTime cutoff);
}
