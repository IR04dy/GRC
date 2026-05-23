package com.tss.grc.module.bcm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Slice 9-C — one (time_band × loss_type) cell of the quantitative BIA
 * matrix. Up to 5 × 8 = 40 rows hang off each {@link BiaQuantitative}.
 *
 * <p>{@code organisationId} is denormalised here on purpose: it lets the
 * tenant-scope guard query lines without joining the header, and lets
 * the line table participate in tenant-isolation tests directly.
 */
@Entity
@Table(schema = "bcm", name = "bia_quantitative_loss_line")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BiaQuantitativeLossLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bia_quantitative_id", nullable = false)
    private BiaQuantitative biaQuantitative;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "time_band", nullable = false, length = 10)
    private String timeBand;

    @Column(name = "loss_type", nullable = false, length = 40)
    private String lossType;

    @Column(name = "amount_sar", nullable = false, precision = 18, scale = 2)
    private BigDecimal amountSar;

    @Column(name = "basis_narrative_en", columnDefinition = "NVARCHAR(MAX)")
    private String basisNarrativeEn;

    @Column(name = "basis_narrative_ar", columnDefinition = "NVARCHAR(MAX)")
    private String basisNarrativeAr;

    @Column(name = "confidence", nullable = false, length = 10)
    private String confidence = "MEDIUM";

    @Column(name = "is_estimate", nullable = false)
    private Boolean isEstimate = true;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
