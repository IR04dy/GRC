package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.IrPlaybook;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface IrPlaybookRepository extends JpaRepository<IrPlaybook, Long> {

    Optional<IrPlaybook> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<IrPlaybook> findByOrganisationIdAndPlaybookCode(Long organisationId, String playbookCode);

    List<IrPlaybook> findByOrganisationIdOrderByCategoryAscPlaybookCodeAsc(Long organisationId);

    /** Template playbooks live under org=0 — seeded in V150. */
    List<IrPlaybook> findByOrganisationIdAndStatus(Long organisationId, String status);
}
