package com.tss.grc.module.sust.materiality.repository;

import com.tss.grc.module.sust.materiality.entity.MaterialTopic;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MaterialTopicRepository extends JpaRepository<MaterialTopic, Long> {

    Page<MaterialTopic> findByOrganisationIdAndReportingYearOrderByMaterialityScoreDescIdAsc(
        Long org, Short year, Pageable p);

    Page<MaterialTopic> findByOrganisationIdAndReportingYearAndCategoryOrderByMaterialityScoreDescIdAsc(
        Long org, Short year, String category, Pageable p);

    Page<MaterialTopic> findByOrganisationIdAndReportingYearAndStatusOrderByMaterialityScoreDescIdAsc(
        Long org, Short year, String status, Pageable p);

    Optional<MaterialTopic> findByIdAndOrganisationId(Long id, Long org);

    List<MaterialTopic> findByOrganisationIdAndReportingYear(Long org, Short year);
}
