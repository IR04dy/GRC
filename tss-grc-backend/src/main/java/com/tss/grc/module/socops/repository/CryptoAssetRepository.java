package com.tss.grc.module.socops.repository;

import com.tss.grc.module.socops.entity.CryptoAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CryptoAssetRepository extends JpaRepository<CryptoAsset, Long> {

    Optional<CryptoAsset> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<CryptoAsset> findByOrganisationIdAndAssetCode(Long organisationId, String assetCode);

    Page<CryptoAsset> findByOrganisationIdOrderByAssetCodeAsc(Long organisationId, Pageable pageable);

    long countByOrganisationIdAndStatus(Long organisationId, String status);

    long countByOrganisationIdAndQuantumSafe(Long organisationId, Boolean quantumSafe);

    /** Returns certs expiring within the given window — fuel for the
     *  cert-expiry alert workflow. */
    @Query("""
        SELECT c FROM CryptoAsset c
        WHERE c.organisationId = :organisationId
          AND c.validUntil IS NOT NULL
          AND c.validUntil BETWEEN :from AND :to
        ORDER BY c.validUntil ASC
    """)
    List<CryptoAsset> findExpiringBetween(Long organisationId, LocalDateTime from, LocalDateTime to);

    /** F2-376 — SOC-ops sweep tenant discovery: a tenant that uses this surface but has
     *  never ingested a SecurityEvent must still be swept for its escalations. */
    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT e.organisationId FROM CryptoAsset e")
    java.util.List<Long> findDistinctOrganisationIds();
}
