package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceVersionRepository extends JpaRepository<EvidenceVersion, Long> {
    List<EvidenceVersion> findByEvidenceIdOrderByVersionNoDesc(Long evidenceId);
}
