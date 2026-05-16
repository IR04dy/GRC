package com.tss.grc.module.sust.metrics.repository;

import com.tss.grc.module.sust.metrics.entity.EsgMetric;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EsgMetricRepository extends JpaRepository<EsgMetric, Long> {

    Page<EsgMetric> findByOrganisationIdAndReportingYearOrderByCategoryAscMetricCodeAsc(
        Long org, Short year, Pageable p);

    Page<EsgMetric> findByOrganisationIdAndReportingYearAndCategoryOrderByMetricCodeAsc(
        Long org, Short year, String category, Pageable p);

    Optional<EsgMetric> findByIdAndOrganisationId(Long id, Long org);

    List<EsgMetric> findByOrganisationIdAndReportingYear(Long org, Short year);
}
