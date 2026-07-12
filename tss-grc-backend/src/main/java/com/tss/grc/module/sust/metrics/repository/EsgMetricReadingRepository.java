package com.tss.grc.module.sust.metrics.repository;

import com.tss.grc.module.sust.metrics.entity.EsgMetricReading;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Reading-history repository for ESG metrics (epic S-R). Org-scoped per query. */
public interface EsgMetricReadingRepository extends JpaRepository<EsgMetricReading, Long> {

    List<EsgMetricReading> findByOrganisationIdAndMetricIdOrderByAsOfDescIdDesc(Long organisationId, Long metricId);

    /** V293 reading-required gate — does the metric have at least one reading? */
    boolean existsByOrganisationIdAndMetricId(Long organisationId, Long metricId);
}
