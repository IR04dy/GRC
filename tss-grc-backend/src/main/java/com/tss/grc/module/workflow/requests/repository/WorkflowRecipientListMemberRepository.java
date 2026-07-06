package com.tss.grc.module.workflow.requests.repository;

import com.tss.grc.module.workflow.requests.entity.WorkflowRecipientListMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WorkflowRecipientListMemberRepository extends JpaRepository<WorkflowRecipientListMember, Long> {

    List<WorkflowRecipientListMember> findByOrganisationIdAndListIdOrderByStepNoAscIdAsc(
        Long organisationId, Long listId);
}
