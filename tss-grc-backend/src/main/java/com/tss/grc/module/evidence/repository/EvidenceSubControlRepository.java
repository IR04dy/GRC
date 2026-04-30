package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceSubControl;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceSubControlRepository extends JpaRepository<EvidenceSubControl, Long> {
    List<EvidenceSubControl> findByControlIdAndIsActiveTrue(Long controlId);
    List<EvidenceSubControl> findByControlIdIn(java.util.Collection<Long> controlIds);
}
