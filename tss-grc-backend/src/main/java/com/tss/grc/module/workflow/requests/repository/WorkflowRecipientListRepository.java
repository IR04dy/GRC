package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRecipientList;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WorkflowRecipientListRepository extends JpaRepository<WorkflowRecipientList, Long> {

    List<WorkflowRecipientList> findByOrganisationIdOrderByNameAsc(Long organisationId);

    Optional<WorkflowRecipientList> findByIdAndOrganisationId(Long id, Long organisationId);

    boolean existsByOrganisationIdAndName(Long organisationId, String name);
}
