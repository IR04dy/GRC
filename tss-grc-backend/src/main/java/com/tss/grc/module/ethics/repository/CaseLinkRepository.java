package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.CaseLink;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseLinkRepository extends JpaRepository<CaseLink, Long> {
    List<CaseLink> findByCaseId(Long caseId);
    List<CaseLink> findByEntityTypeAndEntityId(String entityType, Long entityId);
}
