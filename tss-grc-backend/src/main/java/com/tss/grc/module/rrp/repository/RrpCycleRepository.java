package com.tss.grc.module.rrp.repository;

import com.tss.grc.module.rrp.entity.RrpCycle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RrpCycleRepository extends JpaRepository<RrpCycle, Long> {

    Page<RrpCycle> findByOrganisationIdOrderByFiscalYearDesc(
            Long organisationId, Pageable pageable);

    /**
     * The same ordering, UNCAPPED -- F2-421.
     *
     * <p>{@code dashboard()} used the paged overload above with {@code PageRequest.of(0, 50)} and
     * then derived {@code overdueCycleCount} and the red/amber indicator sums from that window,
     * beside four all-time {@code countByOrganisationIdAndStatus} figures in the same method. The
     * cap's order is {@code fiscal_year DESC}, which keeps the NEWEST cycles, while an overdue
     * cycle is one whose due date has passed -- so the window dropped exactly the rows the figure
     * was looking for, and under-reported late SAMA recovery-plan submissions.
     *
     * <p>Derived, so it inherits {@code RrpCycle}'s {@code @Where(is_deleted = 0)} and agrees with
     * the paged overload on soft-delete scoping (checked, not assumed). Deliberately NOT a SQL
     * {@code COUNT}/{@code SUM} beside the paged fetch: that leaves two populations and re-creates
     * F2-365's defect. The paged overload stays -- {@code listCycles} is a genuine paged read.
     *
     * <p>{@code NcaEccFilingRepository} has declared exactly this overload all along.
     */
    List<RrpCycle> findByOrganisationIdOrderByFiscalYearDesc(Long organisationId);

    Optional<RrpCycle> findByIdAndOrganisationId(Long id, Long organisationId);

    Optional<RrpCycle> findByReferenceAndOrganisationId(String reference, Long organisationId);

    Optional<RrpCycle> findFirstByOrganisationIdOrderByFiscalYearDesc(Long organisationId);

    long countByOrganisationIdAndStatus(Long organisationId, String status);
}
