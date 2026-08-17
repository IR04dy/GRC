package com.tss.grc.module.admin.committee.service;

import com.tss.grc.common.validation.OrderedVocabulary;

import java.util.List;

/**
 * F2-230 — the competency ladder for {@code admin.committee_member_skills.skill_level}.
 *
 * <h2>The defect this closes</h2>
 * {@code CommitteeMemberSkillRepository#findByDimension} ended {@code ORDER BY s.skillLevel DESC},
 * over {@code BASIC | INTERMEDIATE | EXPERT}. Descending <em>alphabetically</em> that reads:
 *
 * <pre>  INTERMEDIATE &gt; EXPERT &gt; BASIC  </pre>
 *
 * <p>So the most qualified member sorted <b>second</b>. The query's own javadoc says what it is for:
 * <i>"Used to answer 'does this committee have a financial expert?' (CMA Article 54, SAMA RGov
 * § 5.3.2)"</i> — a governance question about board composition, answered by an alphabetical sort.
 * Measured 2026-08-17, identical in dev and prod: 31 rows holding EXPERT and INTERMEDIATE, so the
 * mis-ranking is live rather than latent, and it is live on exactly the two rungs the question
 * distinguishes.
 *
 * <p>⚠ <b>Blast radius, stated honestly.</b> No frontend client calls
 * {@code GET /committees/{id}/skills/by-dimension/{dimension}} — it is one of the platform's
 * clientless endpoints — so today the wrong order reaches API callers and not the skills-matrix
 * screen, which uses the sibling {@code …OrderByMemberNameAsc} query. That is a fact about today, not
 * a property of the control (F2-182), and it is the reason this is graded below the TPRM half of the
 * same finding rather than left alone.
 *
 * <p>⚠⚠ <b>The javadoc also described a filter that does not exist.</b> It read <i>"Members in a
 * committee who hold a specific skill at a given level <b>or above</b>"</i> — and the method takes
 * {@code (organisationId, committeeId, dimension)} with no level parameter of any kind. F2-184's
 * shape: a comment describing a check that does not exist is worse than no comment, because it
 * answers the question a reader was about to ask. The <em>ordering</em> is fixed here; the missing
 * threshold parameter is <b>not</b> added, because no caller asks for one and inventing a capability
 * to make a comment true is the wrong direction — the comment is corrected instead, and
 * {@link #atLeast} exists for the caller that eventually wants it.
 */
public final class SkillLevel {

    /**
     * Least to most competent, naming the {@code CHECK} V154 already declares
     * ({@code CK_member_skill_level}). Nothing needed adding to the schema here — the vocabulary was
     * always closed and always correct. What was missing was any statement of its <b>order</b>.
     */
    public static final OrderedVocabulary LADDER = OrderedVocabulary.of(
            "skillLevel", "CK_member_skill_level",
            "BASIC", "INTERMEDIATE", "EXPERT");

    private SkillLevel() {}

    /** Least-to-most-competent, as declared. */
    public static List<String> ladder() {
        return LADDER.ladder();
    }

    /** Position on the ladder, or {@link OrderedVocabulary#UNRECOGNISED}. */
    public static int rank(String level) {
        return LADDER.rank(level);
    }

    /**
     * Is {@code level} at or above {@code threshold}? The CMA Article 54 question, expressed once.
     *
     * <p>Currently read by {@code CommitteeMemberSkillRepositoryLadderTest} rather than by production
     * code, and deliberately kept: the ladder's order is only meaningful if something can ask a
     * threshold question of it, and the alternative — leaving the next reader to write
     * {@code level.equals("EXPERT")} — is the inclusion list this whole finding is about.
     */
    public static boolean atLeast(String level, String threshold) {
        return LADDER.atLeast(level, threshold);
    }
}
