package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.GreenInstrument;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Green-Finance G1 — org-scoped finders (top-level: Spring Data does not scan nested interfaces). */
public interface GreenInstrumentRepository extends JpaRepository<GreenInstrument, Long> {

    List<GreenInstrument> findByOrganisationIdAndIsDeletedFalseOrderByInstrumentCodeAsc(Long organisationId);

    Optional<GreenInstrument> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<GreenInstrument> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    boolean existsByOrganisationIdAndInstrumentCodeAndIsDeletedFalse(Long organisationId, String instrumentCode);
}
