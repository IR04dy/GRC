package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RrpEventRepository extends JpaRepository<RrpEvent, Long> {

    List<RrpEvent> findByCycleIdAndOrganisationIdOrderByOccurredAtDesc(
            Long cycleId, Long organisationId);
}
