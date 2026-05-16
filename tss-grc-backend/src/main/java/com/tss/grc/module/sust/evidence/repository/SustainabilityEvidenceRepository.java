package com.tss.grc.module.sust.evidence.repository;

import com.tss.grc.module.sust.evidence.entity.SustainabilityEvidence;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface SustainabilityEvidenceRepository extends JpaRepository<SustainabilityEvidence, Long> {

    Page<SustainabilityEvidence> findByOrganisationIdOrderByCreatedAtDescIdDesc(Long org, Pageable p);

    Page<SustainabilityEvidence> findByOrganisationIdAndModuleAreaOrderByCreatedAtDescIdDesc(
        Long org, String moduleArea, Pageable p);

    Page<SustainabilityEvidence> findByOrganisationIdAndApprovalStatusOrderByCreatedAtDescIdDesc(
        Long org, String approvalStatus, Pageable p);

    Optional<SustainabilityEvidence> findByIdAndOrganisationId(Long id, Long org);

    /** All evidence for a year — used by aggregate / expiry sweep. */
    List<SustainabilityEvidence> findByOrganisationIdAndReportingYear(Long org, Short year);

    /** Rule 9 sweep — evidence whose valid_to has passed but is still
     *  showing as APPROVED. Used by the periodic expiry job. */
    List<SustainabilityEvidence> findByOrganisationIdAndApprovalStatusAndValidToBefore(
        Long org, String approvalStatus, LocalDate cutoff);
}
