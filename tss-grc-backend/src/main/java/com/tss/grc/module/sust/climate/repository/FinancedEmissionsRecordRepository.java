package com.tss.grc.module.sust.climate.repository;

import com.tss.grc.module.sust.climate.entity.FinancedEmissionsRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FinancedEmissionsRecordRepository extends JpaRepository<FinancedEmissionsRecord, Long> {

    List<FinancedEmissionsRecord> findByOrganisationIdAndIsDeletedFalseOrderByReportingYearDescAssetClassAsc(Long organisationId);

    List<FinancedEmissionsRecord> findByOrganisationIdAndReportingYearAndIsDeletedFalseOrderByAssetClassAsc(
            Long organisationId, Integer reportingYear);

    List<FinancedEmissionsRecord> findByOrganisationIdAndReportingYearAndReportingPeriodAndIsDeletedFalseOrderByAssetClassAsc(
            Long organisationId, Integer reportingYear, String reportingPeriod);

    Optional<FinancedEmissionsRecord> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<FinancedEmissionsRecord> findByOrganisationIdAndReportingYearAndReportingPeriodAndAssetClassAndIsDeletedFalse(
            Long organisationId, Integer reportingYear, String reportingPeriod, String assetClass);

    // ── V293 per-asset PCAF rollup (plan §5.1) ──────────────────────────────

    Optional<FinancedEmissionsRecord> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    List<FinancedEmissionsRecord> findByOrganisationIdAndAssetRefAndIsDeletedFalseOrderByReportingYearDesc(
            Long organisationId, Long assetRef);
}
