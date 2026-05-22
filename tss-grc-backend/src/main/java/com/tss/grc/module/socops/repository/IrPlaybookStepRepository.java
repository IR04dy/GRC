package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.IrPlaybookStep;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface IrPlaybookStepRepository extends JpaRepository<IrPlaybookStep, Long> {

    List<IrPlaybookStep> findByOrganisationIdAndPlaybookIdOrderByStepOrderAsc(
        Long organisationId, Long playbookId);

    /** Looks up template steps under org=0 — used when cloning a template
     *  into a real organisation. */
    List<IrPlaybookStep> findByPlaybookIdOrderByStepOrderAsc(Long playbookId);
}
