package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.QualityRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("doQualityRunRepository")
public interface QualityRunRepository extends JpaRepository<QualityRun, Long> {
    List<QualityRun> findByOrganisationIdAndQualityRuleIdOrderByRunAtDesc(Long organisationId, Long qualityRuleId);
    List<QualityRun> findByOrganisationIdOrderByRunAtDesc(Long organisationId);
}
