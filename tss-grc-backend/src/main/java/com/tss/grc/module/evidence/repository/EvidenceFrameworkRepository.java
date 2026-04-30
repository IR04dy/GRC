package com.tss.grc.module.evidence.repository;

import com.tss.grc.module.evidence.entity.EvidenceFramework;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceFrameworkRepository extends JpaRepository<EvidenceFramework, Long> {
    List<EvidenceFramework> findByIsActiveTrue();
}
