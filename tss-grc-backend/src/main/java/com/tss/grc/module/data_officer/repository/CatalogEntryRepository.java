package com.tss.grc.module.data_officer.repository;

import com.tss.grc.module.data_officer.entity.Entities.CatalogEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("doCatalogEntryRepository")
public interface CatalogEntryRepository extends JpaRepository<CatalogEntry, Long> {
    Optional<CatalogEntry> findByOrganisationIdAndReference(Long organisationId, String reference);
    Optional<CatalogEntry> findByOrganisationIdAndId(Long organisationId, Long id);
    List<CatalogEntry> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    long countByOrganisationIdAndContainsPii(Long organisationId, Boolean containsPii);
}
