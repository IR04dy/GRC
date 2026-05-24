package com.tss.grc.module.privacy.subprocessor.repository;

import com.tss.grc.module.privacy.subprocessor.entity.SubprocessorEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Org-scoped finders for {@link SubprocessorEvent}.
 */
public interface SubprocessorEventRepository extends JpaRepository<SubprocessorEvent, Long> {

    List<SubprocessorEvent> findByOrganisationIdAndSubprocessorIdAndIsDeletedFalseOrderByEventDateDesc(
            Long organisationId, Long subprocessorId);

    Optional<SubprocessorEvent> findByOrganisationIdAndExternalIdAndIsDeletedFalse(
            Long organisationId, UUID externalId);

    List<SubprocessorEvent> findByOrganisationIdAndIsDeletedFalseOrderByEventDateDesc(Long organisationId);

    long countByOrganisationIdAndSubprocessorIdAndEventTypeAndIsDeletedFalse(
            Long organisationId, Long subprocessorId, String eventType);
}
