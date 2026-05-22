package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/**
 * Catalog of 19 Saudi-regulated committee templates (V154 seeded).
 * Read-only — orgs don't edit templates, they instantiate live
 * committees from them.
 */
public interface CommitteeTemplateRepository extends JpaRepository<CommitteeTemplate, Long> {

    Optional<CommitteeTemplate> findByTemplateCode(String templateCode);

    /** All active templates, ordered by domain → type → name. */
    @Query("""
        SELECT t FROM CommitteeTemplate t
        WHERE t.isActive = 1
        ORDER BY t.regulatoryDomain ASC, t.committeeType ASC, t.nameEn ASC
    """)
    List<CommitteeTemplate> findAllActiveOrdered();

    /** Templates filtered to a regulatory domain (banking, CMA, general, …). */
    @Query("""
        SELECT t FROM CommitteeTemplate t
        WHERE t.isActive = 1
          AND t.regulatoryDomain = :domain
        ORDER BY t.committeeType ASC, t.nameEn ASC
    """)
    List<CommitteeTemplate> findActiveByDomain(String domain);

    /** Templates filtered to a committee_type — used when an org wants to
     *  show all available templates for, say, Cyber Security committees. */
    List<CommitteeTemplate> findByCommitteeTypeAndIsActiveOrderByNameEnAsc(
        String committeeType, Short isActive);
}
