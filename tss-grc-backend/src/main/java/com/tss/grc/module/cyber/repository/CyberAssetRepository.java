package com.tss.grc.module.cyber.repository;

import com.tss.grc.module.cyber.entity.CyberAsset;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CyberAssetRepository extends JpaRepository<CyberAsset, Long> {

    Page<CyberAsset> findByOrganisationId(Long orgId, Pageable pageable);

    List<CyberAsset> findAllByOrganisationId(Long orgId);
}
