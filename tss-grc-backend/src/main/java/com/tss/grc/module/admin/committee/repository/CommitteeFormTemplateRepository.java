package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeFormTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

/** Downloadable bilingual forms library (charter, agenda, minutes, …). */
public interface CommitteeFormTemplateRepository extends JpaRepository<CommitteeFormTemplate, Long> {

    Optional<CommitteeFormTemplate> findByFormCode(String formCode);

    @Query("""
        SELECT f FROM CommitteeFormTemplate f
        WHERE f.isActive = 1
        ORDER BY f.formType ASC, f.titleEn ASC
    """)
    List<CommitteeFormTemplate> findAllActiveOrdered();

    /** Forms applicable to a given committee_type. The {@code applicableTo}
     *  column holds a comma-separated list (or 'ALL'), so we filter
     *  client-side after pulling the small forms set — there are ~10 forms,
     *  not enough to warrant a SQL LIKE join. */
    @Query("""
        SELECT f FROM CommitteeFormTemplate f
        WHERE f.isActive = 1
          AND (f.applicableTo = 'ALL' OR f.applicableTo LIKE :likePattern)
        ORDER BY f.formType ASC, f.titleEn ASC
    """)
    List<CommitteeFormTemplate> findActiveApplicableTo(String likePattern);
}
