package com.tss.grc.module.regreporting.repository;

import com.tss.grc.module.regreporting.entity.RegReportFiling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface RegReportFilingRepository
        extends JpaRepository<RegReportFiling, Long> {

    Page<RegReportFiling> findByOrganisationIdOrderByDueDateAsc(
            Long organisationId, Pageable pageable);

    Optional<RegReportFiling> findByIdAndOrganisationId(Long id, Long organisationId);

    List<RegReportFiling> findByObligationIdAndOrganisationIdOrderByPeriodEndDesc(
            Long obligationId, Long organisationId);

    Optional<RegReportFiling> findFirstByObligationIdAndOrganisationIdOrderByPeriodEndDesc(
            Long obligationId, Long organisationId);

    List<RegReportFiling> findByOrganisationIdAndDueDateBetweenOrderByDueDateAsc(
            Long organisationId, LocalDate from, LocalDate to);

    List<RegReportFiling> findByOrganisationIdAndStatusInOrderByDueDateAsc(
            Long organisationId, Collection<String> statuses);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatusIn(
            Long organisationId, Collection<String> statuses);

    long countByOrganisationIdAndStatusNotInAndDueDateBefore(
            Long organisationId, Collection<String> statuses, LocalDate cutoff);
}
