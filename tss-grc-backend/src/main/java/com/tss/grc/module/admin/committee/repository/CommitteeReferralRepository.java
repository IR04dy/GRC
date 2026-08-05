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

    /** S7b / CE-4 — the CAB queue for one committee. Org-scoped, like every query here. */
    java.util.List<CommitteeReferral> findByOrganisationIdAndCommitteeIdAndStatusInOrderByIdAsc(
        Long organisationId, Long committeeId, java.util.Collection<String> statuses);

    /** F2-113 — one referring module's open referrals, across every committee: the population the
     *  force-recusal gate applies to, and so the population its coverage is measured over. */
    java.util.List<CommitteeReferral> findByOrganisationIdAndSourceModuleAndStatusInOrderByIdAsc(
        Long organisationId, String sourceModule, java.util.Collection<String> statuses);
}
