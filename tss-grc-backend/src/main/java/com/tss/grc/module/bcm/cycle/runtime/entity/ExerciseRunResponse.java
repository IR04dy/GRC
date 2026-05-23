package com.tss.grc.module.bcm.cycle.runtime.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Slice 10-B — A participant's response to a dispatched inject.
 *
 * <p>{@code responseTimeSeconds} is computed at write time from
 * {@code response_at - run_inject.actual_dispatched_at}. The facilitator may
 * (post-run) score the response quality 0–100 and add a comment, both
 * bilingual.
 */
@Entity
@Table(schema = "bcm", name = "exercise_run_response")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ExerciseRunResponse extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "run_inject_id", nullable = false)
    private Long runInjectId;

    @Column(name = "participant_user_id", nullable = false)
    private Long participantUserId;

    @Column(name = "response_text_en", length = 2000)
    private String responseTextEn;

    @Column(name = "response_text_ar", length = 2000)
    private String responseTextAr;

    @Column(name = "decision_made_en", length = 1000)
    private String decisionMadeEn;

    @Column(name = "decision_made_ar", length = 1000)
    private String decisionMadeAr;

    @Column(name = "response_at", nullable = false)
    private LocalDateTime responseAt = LocalDateTime.now();

    /** Seconds between the inject's actual_dispatched_at and {@link #responseAt}. */
    @Column(name = "response_time_seconds")
    private Integer responseTimeSeconds;

    /** Facilitator post-run score, 0..100. */
    @Column(name = "quality_score", precision = 4, scale = 2)
    private BigDecimal qualityScore;

    @Column(name = "facilitator_comment_en", length = 1000)
    private String facilitatorCommentEn;

    @Column(name = "facilitator_comment_ar", length = 1000)
    private String facilitatorCommentAr;
}
