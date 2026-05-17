package com.tss.grc.module.sust.cma.repository;

import com.tss.grc.module.sust.cma.entity.CmaDisclosure;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CmaDisclosureRepository extends JpaRepository<CmaDisclosure, Long> {

    Page<CmaDisclosure> findByOrganisationIdAndReportingYearOrderByArticleAscClauseAsc(
        Long org, Short year, Pageable p);

    Page<CmaDisclosure> findByOrganisationIdAndReportingYearAndDisclosureStatusOrderByArticleAscClauseAsc(
        Long org, Short year, String status, Pageable p);

    Optional<CmaDisclosure> findByIdAndOrganisationId(Long id, Long org);

    Optional<CmaDisclosure> findByOrganisationIdAndReportingYearAndArticleAndClause(
        Long org, Short year, String article, String clause);

    /** All disclosures for one year — used by the aggregate / readiness endpoint. */
    List<CmaDisclosure> findByOrganisationIdAndReportingYear(Long org, Short year);
}
