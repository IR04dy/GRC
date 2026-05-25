package com.tss.grc.module.consumer.complaints.repository;

import com.tss.grc.module.consumer.complaints.entity.Complaint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComplaintRepository extends JpaRepository<Complaint, Long> {

    List<Complaint> findByOrganisationIdAndIsDeletedFalseOrderBySubmittedOnDesc(Long organisationId);

    List<Complaint> findByOrganisationIdAndStatusAndIsDeletedFalseOrderBySubmittedOnDesc(
            Long organisationId, String status);

    /** Convenience used by callers that don't want to spell the long suffix. */
    default List<Complaint> findByOrganisationIdAndStatus(Long organisationId, String status) {
        return findByOrganisationIdAndStatusAndIsDeletedFalseOrderBySubmittedOnDesc(organisationId, status);
    }

    List<Complaint> findByOrganisationIdAndCategoryAndIsDeletedFalse(Long organisationId, String category);

    List<Complaint> findByOrganisationIdAndSeverityAndIsDeletedFalseOrderBySubmittedOnDesc(
            Long organisationId, String severity);

    Optional<Complaint> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    /**
     * Public anonymous tracking lookup — the token is unique across
     * tenants and IS the credential. Tenant context is NOT required.
     */
    Optional<Complaint> findBySubmissionToken(UUID submissionToken);

    /**
     * Used by the sequence generator on submission. Returns the row
     * with the lexicographically-largest complaint_no across all
     * tenants — safe because all rows share the {@code CMP-YYYY-NNNNNN}
     * format with zero-padded numerics.
     */
    Optional<Complaint> findTopByOrderByComplaintNoDesc();

    boolean existsByComplaintNo(String complaintNo);

    long countByOrganisationIdAndIsDeletedFalse(Long organisationId);
}
