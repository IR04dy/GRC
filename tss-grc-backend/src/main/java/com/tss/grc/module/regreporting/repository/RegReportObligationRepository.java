package com.tss.grc.module.regreporting.repository;

import com.tss.grc.module.regreporting.entity.RegReportObligation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RegReportObligationRepository
        extends JpaRepository<RegReportObligation, Long> {

    Page<RegReportObligation> findByOrganisationIdOrderByCodeAsc(
            Long organisationId, Pageable pageable);

    List<RegReportObligation> findByOrganisationIdAndActiveOrderByCodeAsc(
            Long organisationId, Boolean active);

    Optional<RegReportObligation> findByIdAndOrganisationId(
            Long id, Long organisationId);

    Optional<RegReportObligation> findByCodeAndOrganisationId(
            String code, Long organisationId);

    long countByOrganisationId(Long organisationId);

    long countByOrganisationIdAndActive(Long organisationId, Boolean active);
}
