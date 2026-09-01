package com.tss.grc.module.risk.repository;

import com.tss.grc.module.risk.entity.RiskAppetiteStatement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RiskAppetiteStatementRepository extends JpaRepository<RiskAppetiteStatement, Long> {
    Page<RiskAppetiteStatement> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId, Pageable pageable);

    /**
     * The same population, UNCAPPED -- F2-422.
     *
     * <p>RiskAppetiteService#dashboard took the 200 most-recently-created statements and published
     * {@code all.size()} as "total" beside four severity buckets counted over the same
     * window -- F2-420's orthogonal case, since creation order is unrelated to how many
     * statements exist.
     *
     * <p>Derived, so it inherits {@code RiskAppetiteStatement}'s {@code @Where(is_deleted = 0)} and agrees
     * with the paged overload on soft-delete scoping (checked, not assumed). Deliberately NOT
     * a SQL {@code COUNT} beside the paged fetch: that leaves two populations and re-creates
     * F2-365's defect. The paged overload stays -- the list endpoints are genuine paged reads.
     */
    List<RiskAppetiteStatement> findByOrganisationIdOrderByCreatedAtDesc(Long organisationId);
    List<RiskAppetiteStatement> findByOrganisationIdAndStatus(Long organisationId, String status);
}
