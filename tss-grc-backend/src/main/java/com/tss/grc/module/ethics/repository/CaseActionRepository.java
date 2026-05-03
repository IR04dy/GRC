package com.tss.grc.module.ethics.repository;

import com.tss.grc.module.ethics.entity.CaseAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CaseActionRepository extends JpaRepository<CaseAction, Long> {
    List<CaseAction> findByCaseIdOrderByActionAtAsc(Long caseId);
}
