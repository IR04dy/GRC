package com.tss.grc.module.ai.lifecycle.repository;

import com.tss.grc.module.ai.lifecycle.entity.AiLifecycleEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AiLifecycleEventRepository extends JpaRepository<AiLifecycleEvent, Long> {
    List<AiLifecycleEvent> findByOrganisationIdAndModelIdOrderByOccurredAtDesc(Long org, Long modelId);
}
