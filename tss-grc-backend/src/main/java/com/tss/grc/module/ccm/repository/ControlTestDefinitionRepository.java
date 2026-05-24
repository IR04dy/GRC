package com.tss.grc.module.ccm.repository;

import com.tss.grc.module.ccm.entity.ControlTestDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link ControlTestDefinition}.
 *
 * <p>All queries derive from JPA method-name conventions so they
 * automatically pick up the {@code @Where(is_deleted = 0)} filter on
 * the entity. The native query is only used by
 * {@code CcmSchedulerService} when it wants the small "due to run"
 * subset — we don't want to load every test row on every poll.
 */
public interface ControlTestDefinitionRepository extends JpaRepository<ControlTestDefinition, Long> {

    List<ControlTestDefinition> findByOrganisationIdAndIsDeletedFalseOrderByCreatedAtDesc(Long organisationId);

    Optional<ControlTestDefinition> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<ControlTestDefinition> findByOrganisationIdAndTestCodeAndIsDeletedFalse(Long organisationId, String testCode);

    long countByOrganisationIdAndTestCode(Long organisationId, String testCode);

    List<ControlTestDefinition> findByOrganisationIdAndIsEnabledTrueAndIsDeletedFalse(Long organisationId);

    /**
     * Cross-tenant list of enabled tests that have never run, or whose
     * {@code last_run_at} is older than {@code thresholdMinutes} minutes
     * ago. The cron-aware "what's due" filtering then happens in the
     * scheduler service (JPQL can't evaluate a cron expression).
     *
     * <p>Cross-tenant on purpose — the scheduler walks every tenant
     * each tick.
     */
    @Query("""
        SELECT d FROM ControlTestDefinition d
        WHERE d.isEnabled = true
          AND d.isDeleted = false
          AND (d.lastRunAt IS NULL OR d.lastRunAt < :cutoff)
        """)
    List<ControlTestDefinition> findCandidatesForRun(@Param("cutoff") java.time.LocalDateTime cutoff);
}
