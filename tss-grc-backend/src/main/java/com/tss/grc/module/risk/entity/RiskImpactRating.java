package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Per-risk, per-dimension impact score — ERM remediation epic E-F.
 *
 * <p>A risk's impact is assessed across several dimensions (financial,
 * operational, regulatory, reputational, cyber, privacy, safety, strategic);
 * the overall inherent impact that feeds the L×I score is the worst (max)
 * dimension. The set of valid dimensions + the score ceiling come from
 * {@link RiskMethodologyConfig}.
 */
@Entity
@Table(schema = "risk", name = "risk_impact_ratings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskImpactRating {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "dimension", nullable = false, length = 40)
    private String dimension;

    @Column(name = "score", nullable = false)
    private Integer score;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
