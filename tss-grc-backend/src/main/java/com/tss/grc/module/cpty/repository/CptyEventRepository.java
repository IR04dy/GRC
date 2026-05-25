package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.CptyEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CptyEventRepository extends JpaRepository<CptyEvent, Long> {

    List<CptyEvent> findByCounterpartyIdAndOrganisationIdOrderByOccurredAtDesc(
            Long counterpartyId, Long organisationId);
}
