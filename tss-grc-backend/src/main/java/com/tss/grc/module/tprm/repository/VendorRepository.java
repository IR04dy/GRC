package com.tss.grc.module.tprm.repository;

import com.tss.grc.module.tprm.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface VendorRepository extends JpaRepository<Vendor, Long> {
    Page<Vendor> findByOrganisationId(Long organisationId, Pageable pageable);
    long countByOrganisationIdAndRiskRating(Long organisationId, String riskRating);

    /**
     * Tenant-scoped single-row lookup. Used to derive the owning organisation
     * of a {@link com.tss.grc.module.tprm.entity.VendorAssessment} (which carries
     * no organisation_id of its own) so assessment mutations cannot cross tenants
     * (SEC-TPRM-001, SEC-TPRM-002).
     */
    Optional<Vendor> findByIdAndOrganisationId(Long id, Long organisationId);

    /** Used by Slice 10-D campaign open to materialise invitations. */
    List<Vendor> findByOrganisationIdAndStatus(Long organisationId, String status);

    /** Used by Slice 10-D when {@code mandatoryThreshold = CRITICAL_ONLY}. */
    List<Vendor> findByOrganisationIdAndRiskRatingAndStatus(
        Long organisationId, String riskRating, String status);

    /** Used by Slice 10-D when {@code mandatoryThreshold = HIGH_AND_ABOVE}. */
    List<Vendor> findByOrganisationIdAndRiskRatingInAndStatus(
        Long organisationId, List<String> riskRatings, String status);
}
