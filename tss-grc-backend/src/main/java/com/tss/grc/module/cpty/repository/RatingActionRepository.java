package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.RatingAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RatingActionRepository extends JpaRepository<RatingAction, Long> {

    List<RatingAction> findByCounterpartyIdAndOrganisationIdOrderByActionDateDesc(
            Long counterpartyId, Long organisationId);

    Optional<RatingAction> findByIdAndOrganisationId(Long id, Long organisationId);
}
