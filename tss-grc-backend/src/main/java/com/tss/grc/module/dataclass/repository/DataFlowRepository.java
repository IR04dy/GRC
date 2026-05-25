package com.tss.grc.module.dataclass.repository;

import com.tss.grc.module.dataclass.entity.DataFlow;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DataFlowRepository extends JpaRepository<DataFlow, Long> {
    List<DataFlow> findByOrganisationIdOrderByCreatedAtDesc(Long org);
    List<DataFlow> findByOrganisationIdAndDataAssetId(Long org, Long dataAssetId);
    Optional<DataFlow> findByOrganisationIdAndId(Long org, Long id);
    Optional<DataFlow> findByOrganisationIdAndReference(Long org, String reference);
    long countByOrganisationIdAndStatus(Long org, String status);
}
