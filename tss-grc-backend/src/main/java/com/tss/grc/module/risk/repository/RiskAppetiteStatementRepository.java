package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskAppetiteStatementRepository extends JpaRepository<RiskAppetiteStatement, Long> {
    Page<RiskAppetiteStatement> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);
    List<RiskAppetiteStatement> findByOrganisationIdAndStatus(Long organisationId, String status);
}
