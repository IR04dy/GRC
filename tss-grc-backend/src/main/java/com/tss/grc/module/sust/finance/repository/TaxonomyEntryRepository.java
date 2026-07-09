package com.tss.grc.module.sust.finance.repository;

import com.tss.grc.module.sust.finance.entity.TaxonomyEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Green-Finance G3 — taxonomy reads (platform default set = org 0, plus tenant CUSTOM rows). */
public interface TaxonomyEntryRepository extends JpaRepository<TaxonomyEntry, Long> {

    List<TaxonomyEntry> findByOrganisationIdInAndIsActiveTrueAndIsDeletedFalseOrderBySourceAscCodeAsc(
        List<Long> organisationIds);

    boolean existsByOrganisationIdInAndCodeAndIsActiveTrueAndIsDeletedFalse(List<Long> organisationIds, String code);
}
