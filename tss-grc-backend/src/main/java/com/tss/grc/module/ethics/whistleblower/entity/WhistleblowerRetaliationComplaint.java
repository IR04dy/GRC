package com.tss.grc.module.ethics.whistleblower.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;

/**
 * Retaliation-protection sub-flow on a {@link WhistleblowerCase} — Sprint 13.
 *
 * <p>A retaliation claim is itself a protected report and is owned by
 * an independent protector (typically HR Director or external
 * investigator) who must not be in the supervisory chain of the
 * original case's investigator. Hence this is a separate table with
 * its own state machine and its own actor.
 *
 * <h3>Status machine</h3>
 * <pre>
 *   RECEIVED → VALIDATED → UPHELD | DISMISSED
 *           ↘ DISMISSED  (validation found no bona-fide claim)
 * </pre>
 */
@Entity
@Table(schema = "ethics", name = "whistleblower_retaliation_complaint")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WhistleblowerRetaliationComplaint extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "related_case_id", nullable = false)
    private Long relatedCaseId;

    @Column(name = "claimant_user_id")
    private Long claimantUserId;

    @Column(name = "claimant_name", length = 255)
    private String claimantName;

    @Column(name = "claimed_actor", nullable = false, length = 255)
    private String claimedActor;

    @Column(name = "claimed_action", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String claimedAction;

    @Column(name = "claimed_at_period_start")
    private LocalDate claimedAtPeriodStart;

    /** RECEIVED | VALIDATED | UPHELD | DISMISSED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "RECEIVED";

    @Column(name = "assigned_protector_user_id")
    private Long assignedProtectorUserId;
}
