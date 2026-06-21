package com.tss.grc.module.outsourcing.repository;

import com.tss.grc.module.outsourcing.entity.CloudService;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository("outsourcingCloudServiceRepository")
public interface CloudServiceRepository extends JpaRepository<CloudService, Long> {

    List<CloudService> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);

    List<CloudService> findByOrganisationIdAndArrangementId(Long organisationId, Long arrangementId);

    Optional<CloudService> findByOrganisationIdAndReference(Long organisationId, String reference);

    Optional<CloudService> findByOrganisationIdAndId(Long organisationId, Long id);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndDataClassification(Long organisationId, String dataClassification);

    /** Total cloud services for a tenant (QUAL-TI-008) — replaces
     *  {@code findByOrganisationIdOrderByCreatedAtDesc(o).size()} in the dashboard. */
    long countByOrganisationId(Long organisationId);

    /** Cloud-service counts grouped by data classification (non-null) for a
     *  tenant in one query (QUAL-TI-008) — replaces an in-memory
     *  {@code groupingBy(dataClassification)}. Returns {@code [classification, count]}. */
    @Query("SELECT c.dataClassification, COUNT(c) FROM OutsourcingCloudService c "
            + "WHERE c.organisationId = :org AND c.dataClassification IS NOT NULL "
            + "GROUP BY c.dataClassification")
    List<Object[]> countByDataClassificationGrouped(@Param("org") Long org);

    /** Cloud-service counts grouped by data residency (non-null) for a tenant in
     *  one query (QUAL-TI-008) — replaces an in-memory
     *  {@code groupingBy(dataResidency)}. Returns {@code [residency, count]}. */
    @Query("SELECT c.dataResidency, COUNT(c) FROM OutsourcingCloudService c "
            + "WHERE c.organisationId = :org AND c.dataResidency IS NOT NULL "
            + "GROUP BY c.dataResidency")
    List<Object[]> countByDataResidencyGrouped(@Param("org") Long org);
}
