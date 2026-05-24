package com.tss.grc.module.audit_committee.dashboard.repository;

import com.tss.grc.module.audit_committee.dashboard.entity.DashboardAttestation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Tenant-scoped finders for {@link DashboardAttestation}.
 */
public interface DashboardAttestationRepository extends JpaRepository<DashboardAttestation, Long> {

    List<DashboardAttestation> findByOrganisationIdAndSnapshotIdAndIsDeletedFalseOrderByAttestedAtAsc(
            Long organisationId, Long snapshotId);

    long countByOrganisationIdAndSnapshotIdAndIsDeletedFalse(Long organisationId, Long snapshotId);
}
