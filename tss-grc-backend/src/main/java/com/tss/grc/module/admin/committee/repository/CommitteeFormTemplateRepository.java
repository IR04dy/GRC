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

    /**
     * Forms applicable to a given {@code committee_type}. The {@code applicableTo} column holds a
     * comma-separated list (or {@code 'ALL'}), and the match is a <b>SQL LIKE</b> over that list.
     *
     * <p>⚠ This javadoc used to read <i>"so we filter client-side after pulling the small forms
     * set — there are ~10 forms, not enough to warrant a SQL LIKE join"</i>, describing a
     * mechanism the query does not use: the filtering is in the WHERE clause below and always has
     * been. F2-184's shape — a comment that answers the question a reader was about to ask.
     *
     * <p>⚠ A {@code %TYPE%} match over a comma list would over-match if one committee type
     * were a substring of another. Measured (F2-431, re-confirmed): the {@code committee_type}
     * CHECK vocabulary holds 22 values with <b>0 substring pairs</b> in both databases, so no
     * token can false-positive against another — true of today's data rather than by
     * construction, since the column has no delimiter-anchored match.
     *
     * <p>F2-449: the pattern is built by {@code CommitteeCatalogService} through
     * {@code LikePatterns.contains(...)} and this clause declares {@code ESCAPE}, so a committee
     * type carrying a {@code LIKE} metacharacter cannot widen the applicability match.
     */
    @Query("""
        SELECT f FROM CommitteeFormTemplate f
        WHERE f.isActive = 1
          AND (f.applicableTo = 'ALL' OR f.applicableTo LIKE :likePattern ESCAPE '\\')
        ORDER BY f.formType ASC, f.titleEn ASC
    """)
    List<CommitteeFormTemplate> findActiveApplicableTo(String likePattern);
}
