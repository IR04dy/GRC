package com.tss.grc.module.ndi.opex.repository;

import com.tss.grc.module.ndi.opex.entity.NdiOperationalExcellenceKpi;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiOperationalExcellenceKpiRepository extends JpaRepository<NdiOperationalExcellenceKpi, Long> {

    Page<NdiOperationalExcellenceKpi> findByOrganisationIdAndCycleIdOrderByCategoryAscIdAsc(Long org, Long cycleId, Pageable p);

    Page<NdiOperationalExcellenceKpi> findByOrganisationIdAndCycleIdAndStatusOrderByCategoryAscIdAsc(
        Long org, Long cycleId, String status, Pageable p);

    Page<NdiOperationalExcellenceKpi> findByOrganisationIdAndCycleIdAndCategoryOrderByIdAsc(
        Long org, Long cycleId, String category, Pageable p);

    Optional<NdiOperationalExcellenceKpi> findByIdAndOrganisationId(Long id, Long org);

    List<NdiOperationalExcellenceKpi> findByOrganisationIdAndCycleId(Long org, Long cycleId);
}
