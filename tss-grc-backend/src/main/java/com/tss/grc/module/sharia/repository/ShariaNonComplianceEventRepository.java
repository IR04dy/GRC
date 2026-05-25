package com.tss.grc.module.sharia.repository;

import com.tss.grc.module.sharia.entity.ShariaNonComplianceEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShariaNonComplianceEventRepository extends JpaRepository<ShariaNonComplianceEvent, Long> {

    Page<ShariaNonComplianceEvent> findByOrganisationIdOrderByDetectedAtDesc(
            Long organisationId, Pageable pageable);

    Optional<ShariaNonComplianceEvent> findByIdAndOrganisationId(Long id, Long organisationId);

    List<ShariaNonComplianceEvent> findByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    @org.springframework.data.jpa.repository.Query(
            "SELECT COALESCE(SUM(e.snciAmountSar), 0) FROM ShariaNonComplianceEvent e " +
                    "WHERE e.organisationId = :org AND e.status IN :statuses AND e.isDeleted = 0")
    BigDecimal sumSnciByStatus(@org.springframework.data.repository.query.Param("org") Long org,
                                @org.springframework.data.repository.query.Param("statuses") java.util.Collection<String> statuses);
}
