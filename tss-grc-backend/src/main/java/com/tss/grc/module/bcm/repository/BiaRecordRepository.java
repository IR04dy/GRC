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
 *
 * <p>⚠⚠ <b>F2-282 — the paragraph above names those three callers and asks only the TENANT
 * question of them.</b> A process can carry several BIA records (two of the ten live processes
 * do), so each of the three also had to choose WHICH one states that process's recovery
 * objectives — and all three chose with a hand-rolled loop, disagreeing on both the ordering
 * and the null handling. That is F2-215's two-of-N inside a comment: a reader who finds the
 * callers enumerated and one question answered about them does not then ask what the other
 * question is. The selection now lives in
 * {@link com.tss.grc.module.bcm.service.BiaObjectives#authoritativeByProcess}.
 *
 * <p>⚠ That predicate is deliberately <b>not</b> a {@code default} method here. It was, for one
 * draft, and it broke two existing {@code MtpdInheritanceServiceTest} cases: <b>a Mockito mock
 * does not execute default methods</b>, so the mocked repository answered nothing, the stubbed
 * {@code findByProcessIdIn} went unused and the resolved MTPD silently became empty. A decision
 * placed on a mockable interface is a decision every test that mocks that interface silently
 * loses. Keep queries here and decisions in a pure function.
 */
public interface BiaRecordRepository extends JpaRepository<BiaRecord, Long> {

    Optional<BiaRecord> findByIdAndOrganisationId(Long id, Long organisationId);

    List<BiaRecord> findByOrganisationId(Long organisationId);

    Page<BiaRecord> findByOrganisationIdAndProcessId(Long organisationId, Long processId,
                                                     Pageable pageable);

    Page<BiaRecord> findByProcessId(Long processId, Pageable pageable);

    List<BiaRecord> findByProcessIdIn(List<Long> processIds);

}
