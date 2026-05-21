package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * §5.1 #6 — Periodic risk-owner attestation log (V115).
 *
 * <p>SAMA expects evidence that the risk owner has personally re-confirmed
 * the residual posture + treatment of each risk on a regular cadence (NCA
 * ECC §1-3 requires the same for the control-mapped risks). One row per
 * attestation event so the audit trail survives even if the owner changes.
 *
 * <p>Residual-score + risk-level snapshots are captured at attestation
 * time so the attestation is defensible even if the risk drifts later.
 */
@Entity
@Table(schema = "risk", name = "risk_attestations")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskAttestation {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "attested_at", nullable = false)
    private LocalDateTime attestedAt = LocalDateTime.now();

    @Column(name = "attested_by")
    private Long attestedBy;

    @Column(name = "attested_by_name", length = 200)
    private String attestedByName;

    /** CONFIRMED | NEEDS_REVIEW | TRANSFER_OWNER */
    @Column(nullable = false, length = 30)
    private String decision = "CONFIRMED";

    @Column(name = "statement_en", columnDefinition = "NVARCHAR(MAX)")
    private String statementEn;

    @Column(name = "statement_ar", columnDefinition = "NVARCHAR(MAX)")
    private String statementAr;

    @Column(name = "residual_score_snapshot")
    private Integer residualScoreSnapshot;

    @Column(name = "risk_level_snapshot", length = 20)
    private String riskLevelSnapshot;

    @Column(name = "next_due_date")
    private LocalDate nextDueDate;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
