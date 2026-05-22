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

    /** Members in a committee who hold a specific skill at a given level
     *  or above. Used to answer "does this committee have a financial
     *  expert?" (CMA Article 54, SAMA RGov § 5.3.2). */
    @Query("""
        SELECT s FROM CommitteeMemberSkill s
        WHERE s.organisationId = :organisationId
          AND s.committeeId = :committeeId
          AND s.skillDimension = :dimension
        ORDER BY s.skillLevel DESC, s.memberName ASC
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
