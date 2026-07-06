package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRequestEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRequestEventRepository extends JpaRepository<WorkflowRequestEvent, Long> {

    List<WorkflowRequestEvent> findByOrganisationIdAndRequestIdOrderByCreatedAtAscIdAsc(
        Long organisationId, Long requestId);
}
