package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.EsgTargetMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EsgTargetMilestoneRepository extends JpaRepository<EsgTargetMilestone, Long> {

    List<EsgTargetMilestone> findByOrganisationIdAndTargetIdAndIsDeletedFalseOrderByYearAsc(
        Long organisationId, Long targetId);

    boolean existsByOrganisationIdAndTargetIdAndYearAndIsDeletedFalse(
        Long organisationId, Long targetId, Integer year);
}
