package com.tss.grc.module.ndi.gap.repository;

import com.tss.grc.module.ndi.gap.entity.NdiGap;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NdiGapRepository extends JpaRepository<NdiGap, Long> {

    Page<NdiGap> findByOrganisationIdAndCycleIdOrderBySeverityAscIdAsc(Long org, Long cycleId, Pageable p);

    Page<NdiGap> findByOrganisationIdAndCycleIdAndStatusOrderBySeverityAscIdAsc(
        Long org, Long cycleId, String status, Pageable p);

    Page<NdiGap> findByOrganisationIdAndCycleIdAndSeverityOrderByIdAsc(
        Long org, Long cycleId, String severity, Pageable p);

    Optional<NdiGap> findByIdAndOrganisationId(Long id, Long org);

    Optional<NdiGap> findByOrganisationIdAndSourceTypeAndSourceId(Long org, String sourceType, Long sourceId);

    List<NdiGap> findByOrganisationIdAndCycleId(Long org, Long cycleId);
}
