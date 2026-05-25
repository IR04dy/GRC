package com.tss.grc.module.cpty.repository;

import com.tss.grc.module.cpty.entity.CreditRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CreditRatingRepository extends JpaRepository<CreditRating, Long> {

    List<CreditRating> findByCounterpartyIdAndOrganisationIdOrderByEffectiveDateDesc(
            Long counterpartyId, Long organisationId);

    Optional<CreditRating> findFirstByCounterpartyIdAndOrganisationIdAndRatingTypeOrderByEffectiveDateDesc(
            Long counterpartyId, Long organisationId, String ratingType);

    Optional<CreditRating> findByIdAndOrganisationId(Long id, Long organisationId);
}
