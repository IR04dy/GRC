package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.DoEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("doEventRepository")
public interface DoEventRepository extends JpaRepository<DoEvent, Long> {
    List<DoEvent> findByOrganisationIdAndEntityTypeAndEntityIdOrderByOccurredAtDesc(
        Long organisationId, String entityType, Long entityId);
}
