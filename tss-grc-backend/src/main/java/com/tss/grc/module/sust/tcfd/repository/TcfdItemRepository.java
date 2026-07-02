package com.tss.grc.module.sust.tcfd.repository;

import com.tss.grc.module.sust.tcfd.entity.TcfdItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TcfdItemRepository extends JpaRepository<TcfdItem, Long> {

    Page<TcfdItem> findByOrganisationIdAndReportingYearOrderByPillarAscRecommendationCodeAsc(
        Long org, Short year, Pageable p);

    Page<TcfdItem> findByOrganisationIdAndReportingYearAndPillarOrderByRecommendationCodeAsc(
        Long org, Short year, String pillar, Pageable p);

    Optional<TcfdItem> findByIdAndOrganisationId(Long id, Long org);

    List<TcfdItem> findByOrganisationIdAndReportingYear(Long org, Short year);

    /** S-F — reverse visibility: TCFD items linked to a given climate risk/opportunity. */
    List<TcfdItem> findByOrganisationIdAndRelatedClimateRiskId(Long org, Long relatedClimateRiskId);
}
