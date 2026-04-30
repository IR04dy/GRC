package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceControlRepository extends JpaRepository<EvidenceControl, Long> {
    List<EvidenceControl> findByFrameworkIdAndIsActiveTrue(Long frameworkId);
    List<EvidenceControl> findByFrameworkIdIn(java.util.Collection<Long> frameworkIds);
}
