package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeReferral;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommitteeReferralRepository extends JpaRepository<CommitteeReferral, Long> {
    // @Where on the entity filters is_deleted; the unique active index enforces
    // one live referral per (module, entity).
    Optional<CommitteeReferral> findByOrganisationIdAndSourceModuleAndSourceEntityId(
        Long organisationId, String sourceModule, Long sourceEntityId);

    Optional<CommitteeReferral> findByResolutionId(Long resolutionId);
}
