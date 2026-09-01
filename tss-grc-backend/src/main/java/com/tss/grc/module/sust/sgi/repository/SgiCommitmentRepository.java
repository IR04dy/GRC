package com.tss.grc.module.sust.sgi.repository;

import com.tss.grc.module.sust.sgi.entity.SgiCommitment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface SgiCommitmentRepository extends JpaRepository<SgiCommitment, Long> {

    Page<SgiCommitment> findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(Long org, Pageable p);

    Page<SgiCommitment> findByOrganisationIdAndStatusOrderByTargetYearAscCommitmentCodeAsc(Long org, String status, Pageable p);

    Page<SgiCommitment> findByOrganisationIdAndSectorOrderByTargetYearAscCommitmentCodeAsc(Long org, String sector, Pageable p);

    Optional<SgiCommitment> findByIdAndOrganisationId(Long id, Long org);

    Optional<SgiCommitment> findByOrganisationIdAndCommitmentCode(Long org, String commitmentCode);

    /** All active commitments — used by the aggregate / dashboard endpoint. */
    List<SgiCommitment> findByOrganisationIdAndStatus(Long org, String status);

    /**
     * F2-415 — the same ordering as the paged overload above, <b>uncapped</b>, for the
     * CMA annual-report annex. {@code AnnexGeneratorService} fetched section 2 through
     * {@code PageRequest.of(0, 100)} and published {@code sgi.size()} as
     * {@code sgiCommitmentCount}. ⚠ The cap bites harder here than on the climate
     * section: that one is ordered by {@code riskScore DESC}, so a truncated list at
     * least keeps the most severe rows, while this ordering is
     * {@code targetYear ASC, commitmentCode ASC} — <b>orthogonal to importance</b>, so
     * the rows dropped are simply the latest-target ones.
     */
    List<SgiCommitment> findByOrganisationIdOrderByTargetYearAscCommitmentCodeAsc(Long org);

    /** SGI-B — tenants that own any (non-deleted) commitment, for the org-explicit surveillance sweep. */
    @Query("SELECT DISTINCT c.organisationId FROM SgiCommitment c")
    List<Long> findDistinctOrganisationIds();
}
