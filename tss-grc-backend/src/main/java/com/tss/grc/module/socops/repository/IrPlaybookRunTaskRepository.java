package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.IrPlaybookRunTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IrPlaybookRunTaskRepository extends JpaRepository<IrPlaybookRunTask, Long> {

    Optional<IrPlaybookRunTask> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Tasks of a run, in step order — the checklist view. */
    List<IrPlaybookRunTask> findByOrganisationIdAndRunIdOrderByStepOrderAsc(
        Long organisationId, Long runId);

    /** Live progress: how many of a run's tasks are already in the given status. */
    long countByOrganisationIdAndRunIdAndStatus(Long organisationId, Long runId, String status);
}
