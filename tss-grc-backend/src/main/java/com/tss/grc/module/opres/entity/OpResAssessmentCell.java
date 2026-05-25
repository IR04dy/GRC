package com.tss.grc.module.opres.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * One pillar-level cell inside an {@link OpResAssessment}.
 *
 * <p>Captures the current-state maturity score (1-5), the target
 * score the board has approved as the year-end goal, evidence
 * references, and a justification narrative. The 5 cells per
 * assessment (one per pillar) are joined by the service layer to
 * compute aggregate scores on the parent.
 */
@Entity
@Table(schema = "opres", name = "opres_assessment_cell")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OpResAssessmentCell extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "assessment_id", nullable = false)
    private Long assessmentId;

    @Column(name = "pillar_id", nullable = false)
    private Long pillarId;

    /** Cached pillar code at cell-write time (audit-stable). */
    @Column(name = "pillar_code_snapshot", nullable = false, length = 32)
    private String pillarCodeSnapshot;

    /** 1-5 — current observed maturity level. */
    @Column(name = "current_score")
    private Integer currentScore;

    /** 1-5 — board-approved target for FY-end. */
    @Column(name = "target_score")
    private Integer targetScore;

    /** Free-form bilingual justification for the score. */
    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    /**
     * Optional reference to supporting evidence (URL or evidence-locker
     * id). The frontend renders this as a click-through chip.
     */
    @Column(name = "evidence_reference", length = 512)
    private String evidenceReference;

    /** The user who last edited this cell. */
    @Column(name = "last_edited_by_user_id")
    private Long lastEditedByUserId;
}
