package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Risk appetite statement — assessment §5.1 ERM #6.
 *
 * appetite_level: AVERSE | MINIMAL | CAUTIOUS | OPEN | HUNGRY
 * status:         DRAFT | APPROVED | UNDER_REVIEW | RETIRED
 */
@Entity
@Table(schema = "risk", name = "risk_appetite_statements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskAppetiteStatement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "statement_en", columnDefinition = "NVARCHAR(MAX)")
    private String statementEn;

    @Column(name = "statement_ar", columnDefinition = "NVARCHAR(MAX)")
    private String statementAr;

    @Column(name = "risk_dimension", nullable = false, length = 60)
    private String riskDimension;

    @Column(name = "appetite_level", nullable = false, length = 20)
    private String appetiteLevel;

    @Column(name = "tolerance_lower", precision = 18, scale = 4)
    private BigDecimal toleranceLower;

    @Column(name = "tolerance_upper", precision = 18, scale = 4)
    private BigDecimal toleranceUpper;

    @Column(name = "unit", length = 40)
    private String unit;

    @Column(name = "current_exposure", precision = 18, scale = 4)
    private BigDecimal currentExposure;

    @Column(name = "approved_by", length = 255)
    private String approvedBy;

    @Column(name = "approved_at")
    private LocalDate approvedAt;

    @Column(name = "next_review_date")
    private LocalDate nextReviewDate;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "DRAFT";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
