package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RiskCategoryRepository extends JpaRepository<RiskCategory, Long> {
    List<RiskCategory> findByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
