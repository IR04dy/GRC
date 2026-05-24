package com.tss.grc.module.audit_committee.dashboard.repository;

import com.tss.grc.module.audit_committee.dashboard.entity.DashboardSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Tenant-scoped finders for {@link DashboardSnapshot}.
 *
 * <p>Soft-deleted rows are already hidden by the entity's
 * {@code @Where} clause.
 */
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, Long> {

    List<DashboardSnapshot> findByOrganisationIdAndIsDeletedFalseOrderByMeetingDateDesc(Long organisationId);

    Optional<DashboardSnapshot> findByOrganisationIdAndExternalIdAndIsDeletedFalse(Long organisationId, UUID externalId);

    Optional<DashboardSnapshot> findByOrganisationIdAndIdAndIsDeletedFalse(Long organisationId, Long id);

    Optional<DashboardSnapshot> findByOrganisationIdAndSnapshotCodeAndIsDeletedFalse(Long organisationId, String snapshotCode);

    boolean existsByOrganisationIdAndSnapshotCodeAndIsDeletedFalse(Long organisationId, String snapshotCode);
}
