package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.CatalogLineage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository("doCatalogLineageRepository")
public interface CatalogLineageRepository extends JpaRepository<CatalogLineage, Long> {
    List<CatalogLineage> findByOrganisationIdAndSourceEntryId(Long organisationId, Long sourceEntryId);
    List<CatalogLineage> findByOrganisationIdAndTargetEntryId(Long organisationId, Long targetEntryId);
}
