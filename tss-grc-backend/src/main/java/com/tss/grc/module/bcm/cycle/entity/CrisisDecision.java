package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Decision log entry — captures every material decision taken during a
 * {@link CrisisEvent}. Forms the spine of the after-action report.
 *
 * <p>Each row records the decision text, who made it, when, the rationale,
 * and any follow-up action with owner and due date. If the action escalates
 * into a corrective action, {@code capaId} carries the soft FK.
 */
@Entity
@Table(schema = "bcm", name = "crisis_decisions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisDecision {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "crisis_event_id", nullable = false)
    private Long crisisEventId;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt = LocalDateTime.now();

    @Column(name = "decided_by")
    private Long decidedBy;

    /** Denormalised name so the after-action PDF doesn't need a user join. */
    @Column(name = "decided_by_name", length = 255)
    private String decidedByName;

    @Column(name = "decision_text", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String decisionText;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String rationale;
    @Column(name = "action_required", columnDefinition = "NVARCHAR(MAX)") private String actionRequired;

    @Column(name = "action_owner_id")  private Long      actionOwnerId;
    @Column(name = "action_due_date")  private LocalDate actionDueDate;

    /** Soft FK to bcm.corrective_actions if the decision spawned a CAPA. */
    @Column(name = "capa_id")
    private Long capaId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
