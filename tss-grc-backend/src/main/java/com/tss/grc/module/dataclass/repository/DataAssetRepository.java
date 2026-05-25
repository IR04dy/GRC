package com.tss.grc.module.dataclass.repository;

import com.tss.grc.module.dataclass.entity.DataAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataAssetRepository extends JpaRepository<DataAsset, Long> {
    List<DataAsset> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    Optional<DataAsset> findByOrganisationIdAndId(Long org, Long id);
    Optional<DataAsset> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndClassification(Long org, String classification);
    long countByOrganisationIdAndStatus(Long org, String status);
    long countByOrganisationIdAndPdplSpecialCategoryNot(Long org, String none);
}
