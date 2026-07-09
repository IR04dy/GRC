package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.GreenProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Green-Finance G1 — org-scoped finders. */
public interface GreenProjectRepository extends JpaRepository<GreenProject, Long> {

    List<GreenProject> findByOrganisationIdAndIsDeletedFalseOrderByProjectCodeAsc(Long organisationId);

    Optional<GreenProject> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    boolean existsByOrganisationIdAndProjectCodeAndIsDeletedFalse(Long organisationId, String projectCode);
}
