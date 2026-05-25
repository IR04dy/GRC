package com.tss.grc.module.cpty.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

/**
 * One rating action on a counterparty — watchlist add/remove,
 * downgrade, upgrade, default, or recovery.
 *
 * <p>Drives the F8 events:
 * <ul>
 *   <li>{@code COUNTERPARTY_RATING_DOWNGRADED} — actionType = DOWNGRADE</li>
 *   <li>{@code COUNTERPARTY_RATING_UPGRADED}   — actionType = UPGRADE</li>
 *   <li>{@code COUNTERPARTY_DEFAULTED}         — actionType = DEFAULT</li>
 *   <li>{@code COUNTERPARTY_WATCHLIST_NEGATIVE} — actionType = WATCHLIST_NEGATIVE</li>
 *   <li>{@code COUNTERPARTY_WATCHLIST_REMOVED}  — actionType = WATCHLIST_REMOVE</li>
 * </ul>
 */
@Entity
@Table(schema = "cpty", name = "rating_action")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RatingAction extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "counterparty_id", nullable = false)
    private Long counterpartyId;

    /**
     * Action type:
     * {@code WATCHLIST_NEGATIVE | WATCHLIST_POSITIVE | WATCHLIST_REMOVE |
     *        DOWNGRADE | UPGRADE | DEFAULT | RECOVERY | OUTLOOK_CHANGE}.
     */
    @Column(name = "action_type", nullable = false, length = 24)
    private String actionType;

    @Column(name = "action_date", nullable = false)
    private LocalDate actionDate;

    /** Prior grade (e.g. "R3"). */
    @Column(name = "grade_before", length = 16)
    private String gradeBefore;

    /** New grade (e.g. "R5"). */
    @Column(name = "grade_after", length = 16)
    private String gradeAfter;

    /** Prior outlook. */
    @Column(name = "outlook_before", length = 16)
    private String outlookBefore;

    /** New outlook. */
    @Column(name = "outlook_after", length = 16)
    private String outlookAfter;

    /** Trigger source — INTERNAL_REVIEW | EXTERNAL_AGENCY | EARLY_WARNING | KRI_BREACH | MANUAL. */
    @Column(name = "trigger_source", length = 24)
    private String triggerSource;

    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    @Column(name = "actor_user_id")
    private Long actorUserId;
}
