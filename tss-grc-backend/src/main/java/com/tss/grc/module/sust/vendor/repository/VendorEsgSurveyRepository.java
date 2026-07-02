package com.tss.grc.module.sust.vendor.repository;

import com.tss.grc.module.sust.vendor.entity.VendorEsgSurvey;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Vendor ESG survey repository — soft-delete filtered (via the entity
 * {@code @Where}) and org-scoped on every finder (S-O). {@code organisation_id}
 * is passed explicitly rather than relying on a Hibernate filter.
 */
public interface VendorEsgSurveyRepository extends JpaRepository<VendorEsgSurvey, Long> {

    Optional<VendorEsgSurvey> findByIdAndOrganisationId(Long id, Long organisationId);

    Page<VendorEsgSurvey> findByOrganisationIdOrderByIdDesc(Long organisationId, Pageable p);

    Page<VendorEsgSurvey> findByOrganisationIdAndStatusOrderByIdDesc(
        Long organisationId, String status, Pageable p);

    Page<VendorEsgSurvey> findByOrganisationIdAndReportingYearOrderByIdDesc(
        Long organisationId, Short reportingYear, Pageable p);

    List<VendorEsgSurvey> findByOrganisationId(Long organisationId);

    /** Dedup guard — one live survey per (tenant, vendor, year). */
    Optional<VendorEsgSurvey> findByOrganisationIdAndTprmVendorIdAndReportingYear(
        Long organisationId, Long tprmVendorId, Short reportingYear);

    /** Non-response sweep — SENT surveys whose deadline has passed. */
    List<VendorEsgSurvey> findByOrganisationIdAndStatusAndDueDateBefore(
        Long organisationId, String status, LocalDate cutoff);

    /** Distinct tenants that have at least one survey — drives the off-request escalation sweep. */
    @org.springframework.data.jpa.repository.Query(
        "select distinct v.organisationId from VendorEsgSurvey v")
    List<Long> findDistinctOrganisationIds();

    /** Next external-id sequence for a tenant+year (MAX across ALL rows incl. soft-deleted — collision-safe). */
    @org.springframework.data.jpa.repository.Query(value =
        "SELECT ISNULL(MAX(TRY_CAST(RIGHT(external_id, 4) AS INT)), 0) " +
        "FROM sust.vendor_esg_survey WITH (NOLOCK) " +
        "WHERE organisation_id = :org AND external_id LIKE :prefix + '%'",
        nativeQuery = true)
    Integer findMaxExternalIdSeq(Long org, String prefix);
}
