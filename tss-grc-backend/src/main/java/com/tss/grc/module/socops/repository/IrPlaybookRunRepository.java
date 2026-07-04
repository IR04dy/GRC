package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.IrPlaybookRun;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IrPlaybookRunRepository extends JpaRepository<IrPlaybookRun, Long> {

    Optional<IrPlaybookRun> findByIdAndOrganisationId(Long id, Long organisationId);

    /** All runs for the tenant, newest first — the global "runs" view. */
    List<IrPlaybookRun> findByOrganisationIdOrderByStartedAtDesc(Long organisationId);

    /** Runs of one playbook, newest first — the per-playbook history. */
    List<IrPlaybookRun> findByOrganisationIdAndPlaybookIdOrderByStartedAtDesc(
        Long organisationId, Long playbookId);
}
