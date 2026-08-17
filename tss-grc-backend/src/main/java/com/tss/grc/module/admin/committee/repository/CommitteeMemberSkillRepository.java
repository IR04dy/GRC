package com.tss.grc.module.admin.committee.repository;

import com.tss.grc.module.admin.committee.entity.CommitteeMemberSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface CommitteeMemberSkillRepository extends JpaRepository<CommitteeMemberSkill, Long> {

    Optional<CommitteeMemberSkill> findByIdAndOrganisationId(Long id, Long organisationId);

    /** All skills for a committee — used to render the skills matrix
     *  (rows = members, cols = skill dimensions). */
    List<CommitteeMemberSkill> findByOrganisationIdAndCommitteeIdOrderByMemberNameAsc(
        Long organisationId, Long committeeId);

    /**
     * Every member holding a skill in one dimension, <b>most competent first</b>. Used to answer
     * "does this committee have a financial expert?" (CMA Article 54, SAMA RGov § 5.3.2).
     *
     * <p>⚠ It returns <b>all</b> levels in that dimension and takes no threshold. Until F2-230 this
     * javadoc claimed it returned members "at a given level or above", describing a parameter the
     * method has never had — F2-184's shape, a comment answering the question a reader was about to
     * ask. The threshold was not added to make the sentence true: no caller asks for one, and
     * {@code SkillLevel.atLeast} is where it belongs when one does.
     *
     * <p>⚠⚠ <b>The ordering was {@code ORDER BY s.skillLevel DESC}</b> — a lexical sort of
     * {@code BASIC | INTERMEDIATE | EXPERT}, which descending alphabetically reads INTERMEDIATE,
     * EXPERT, BASIC. So the CMA Article 54 question was answered with the <em>intermediate</em> member
     * at the top of the list, on 31 live rows in dev and prod, both of whose stored values (EXPERT and
     * INTERMEDIATE) are on the wrong side of that transposition.
     *
     * <p>The {@code CASE} is a second copy of {@code SkillLevel.ladder()} because {@code @Query} takes
     * a compile-time constant; {@code OrderedVocabularySortContractTest} compares them by reflection.
     */
    @Query("""
        SELECT s FROM CommitteeMemberSkill s
        WHERE s.organisationId = :organisationId
          AND s.committeeId = :committeeId
          AND s.skillDimension = :dimension
        ORDER BY
          CASE s.skillLevel
              WHEN 'BASIC' THEN 0 WHEN 'INTERMEDIATE' THEN 1 WHEN 'EXPERT' THEN 2
              ELSE -1 END DESC,
          s.memberName ASC
    """)
    List<CommitteeMemberSkill> findByDimension(Long organisationId, Long committeeId, String dimension);

    /** Aggregate count of members per skill dimension for one committee
     *  — drives the heat-map / coverage chart in the gap-analysis card. */
    @Query("""
        SELECT s.skillDimension, COUNT(s) FROM CommitteeMemberSkill s
        WHERE s.organisationId = :organisationId
          AND s.committeeId = :committeeId
        GROUP BY s.skillDimension
    """)
    List<Object[]> countByDimension(Long organisationId, Long committeeId);
}
