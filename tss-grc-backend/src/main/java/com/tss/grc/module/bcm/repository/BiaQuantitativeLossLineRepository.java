package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BiaQuantitativeLossLine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Tenant-scoped repository for quantitative-BIA loss lines (Slice 9-C).
 *
 * <p>Lines are normally accessed via the parent {@code BiaQuantitative}
 * entity's {@code @OneToMany} collection. This repository exists for
 * tests and reporting paths that need to query lines directly without
 * loading the header.
 */
public interface BiaQuantitativeLossLineRepository extends JpaRepository<BiaQuantitativeLossLine, Long> {

    List<BiaQuantitativeLossLine> findByBiaQuantitativeIdAndIsDeletedFalse(Long biaQuantitativeId);

    List<BiaQuantitativeLossLine> findByOrganisationIdAndBiaQuantitativeIdAndIsDeletedFalse(
        Long organisationId, Long biaQuantitativeId);
}
