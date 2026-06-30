package com.tss.grc.module.risk.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Structured, time-boxed risk acceptance — ERM remediation epic E-A.
 *
 * <p>Replaces the bare {@code RiskService.accept()} status flip and the demo
 * {@code RiskAcceptanceWizard} (which wrote to the generic workflow-submission
 * inbox). An <b>APPROVED</b> acceptance sets the parent risk to {@code ACCEPTED}
 * and carries an <b>enforceable expiry</b>; {@code RiskAcceptanceService.expireSweep}
 * flips lapsed acceptances to {@code EXPIRED} and reopens the risk for
 * reassessment. Board sign-off is required when residual exceeds the appetite
 * ceiling.
 */
@Entity
@Table(schema = "risk", name = "risk_acceptances")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskAcceptance extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "justification_en", columnDefinition = "NVARCHAR(MAX)", nullable = false)
    private String justificationEn;

    @Column(name = "justification_ar", columnDefinition = "NVARCHAR(MAX)")
    private String justificationAr;

    @Column(name = "compensating_controls_en", columnDefinition = "NVARCHAR(MAX)")
    private String compensatingControlsEn;

    @Column(name = "compensating_controls_ar", columnDefinition = "NVARCHAR(MAX)")
    private String compensatingControlsAr;

    /** Residual score snapshotted from the risk at request time (server-stamped, not client). */
    @Column(name = "residual_score_at_acceptance")
    private Integer residualScoreAtAcceptance;

    /** Optional appetite ceiling the acceptance is measured against. */
    @Column(name = "appetite_ceiling")
    private Integer appetiteCeiling;

    /** True when residual > appetite ceiling → Board sign-off required to approve. */
    @Column(name = "requires_board", nullable = false)
    private Boolean requiresBoard = false;

    /** Time-boxed expiry — mandatory. The sweep reopens the risk when it lapses. */
    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    /** SUBMITTED | APPROVED | REJECTED | EXPIRED | REVOKED */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SUBMITTED";

    @Column(name = "cro_signed_by", length = 200)
    private String croSignedBy;

    @Column(name = "board_signed_by", length = 200)
    private String boardSignedBy;

    @Column(name = "requested_by_id")
    private Long requestedById;

    @Column(name = "approved_by", length = 120)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", columnDefinition = "NVARCHAR(MAX)")
    private String rejectionReason;
}
