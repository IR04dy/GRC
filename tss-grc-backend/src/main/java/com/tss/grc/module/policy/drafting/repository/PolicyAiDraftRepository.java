package com.tss.grc.module.policy.drafting.repository;

import com.tss.grc.module.policy.drafting.entity.PolicyAiDraft;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PolicyAiDraftRepository extends JpaRepository<PolicyAiDraft, Long> {

    Optional<PolicyAiDraft> findByIdAndOrganisationId(Long id, Long organisationId);

    List<PolicyAiDraft> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<PolicyAiDraft> findByOrganisationIdAndStatusOrderByCreatedAtDesc(
        Long organisationId,
        String status
    );
}
