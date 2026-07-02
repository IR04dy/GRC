package com.tss.grc.module.sust.ghg.repository;

import com.tss.grc.module.sust.ghg.entity.EmissionFactor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmissionFactorRepository extends JpaRepository<EmissionFactor, Long> {

    List<EmissionFactor> findByOrganisationIdOrderByFactorCodeAscVersionDesc(Long org);

    List<EmissionFactor> findByOrganisationIdAndIsActiveTrueOrderByFactorCodeAsc(Long org);

    Optional<EmissionFactor> findByIdAndOrganisationId(Long id, Long org);

    /** The current active version of a factor code (there is at most one). */
    Optional<EmissionFactor> findFirstByOrganisationIdAndFactorCodeAndIsActiveTrue(Long org, String factorCode);

    /** The highest-numbered version of a factor code, active or not (for version bump). */
    Optional<EmissionFactor> findFirstByOrganisationIdAndFactorCodeOrderByVersionDesc(Long org, String factorCode);
}
