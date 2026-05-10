package com.tss.grc.module.workflow.repository;

import com.tss.grc.module.workflow.entity.WorkflowActionLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowActionLogRepository extends JpaRepository<WorkflowActionLog, Long> {
    List<WorkflowActionLog> findByInstanceIdOrderByExecutedAtDesc(Long instanceId);
}
