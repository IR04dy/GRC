package com.tss.grc.module.bcm.repository;

import com.tss.grc.module.bcm.entity.BiaRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * ⚠ F2-279 added the three tenant-scoped finders. Before that slice this interface had two
 * finders and neither mentioned the tenant, because {@link BiaRecord} did not map
 * {@code organisation_id} — so scoping was delegated entirely to the parent
 * {@code CriticalProcess} link, and a planted row whose own {@code organisation_id} said 2
 * was served to an org-1 caller (probed 2026-08-22). {@code V228} had already built
 * {@code ix_bia_records_org (organisation_id, is_deleted)} for exactly these queries and
 * nothing had ever used it.
 * <p>The parent-process check in {@code BcmService} is kept as well rather than replaced: it
 * validates the <em>reference a caller supplies</em>, which is a different question from who
 * owns the row (F2-242 — verify a reference where it is stored).
 * <p>⚠ {@code findByProcessId}/{@code findByProcessIdIn} stay because three services outside
 * {@code BcmService} call the latter ({@code DependencyCascadeService},
 * {@code BcmBoardPackService}, {@code MtpdInheritanceService}), each passing a process-id list
 * it resolved from its own tenant's processes — tenant-correct by that route, so re-scoping
 * them is defence in depth rather than a defect, and out of this slice. All five finders now
 * inherit the entity's {@code @Where(is_deleted = 0)}.
 */
public interface BiaRecordRepository extends JpaRepository<BiaRecord, Long> {

    Optional<BiaRecord> findByIdAndOrganisationId(Long id, Long organisationId);

    List<BiaRecord> findByOrganisationId(Long organisationId);

    Page<BiaRecord> findByOrganisationIdAndProcessId(Long organisationId, Long processId,
                                                     Pageable pageable);

    Page<BiaRecord> findByProcessId(Long processId, Pageable pageable);

    List<BiaRecord> findByProcessIdIn(List<Long> processIds);
}
