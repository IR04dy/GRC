package com.tss.grc.module.audit_lc.ia_plan.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — Audit-universe entry (an auditable process).
 *
 * <p>{@code planId} is nullable — a NULL row is a master-library entry,
 * a non-NULL row is a snapshot pulled into a specific plan. Risk scores
 * are on the 0-25 IA convention: inherent 0-25, control environment
 * strength 0-1, residual = inherent × (1 - controlEnv).
 */
@Entity
@Table(schema = "audit_lc", name = "ia_audit_universe_entry")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IaAuditUniverseEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "entry_code", nullable = false, length = 64)
    private String entryCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    @Column(name = "process_name_en", length = 255)
    private String processNameEn;

    @Column(name = "process_name_ar", length = 255)
    private String processNameAr;

    @Column(name = "business_unit_en", length = 255)
    private String businessUnitEn;

    @Column(name = "business_unit_ar", length = 255)
    private String businessUnitAr;

    @Column(name = "inherent_risk_score", precision = 4, scale = 2)
    private BigDecimal inherentRiskScore;

    @Column(name = "control_environment_score", precision = 4, scale = 2)
    private BigDecimal controlEnvironmentScore;

    @Column(name = "residual_risk_score", precision = 4, scale = 2)
    private BigDecimal residualRiskScore;

    @Column(name = "last_audited_date")
    private LocalDate lastAuditedDate;

    /** SATISFACTORY | PARTIAL | UNSATISFACTORY */
    @Column(name = "last_audit_rating", length = 20)
    private String lastAuditRating;

    @Column(name = "planned_audit_year")
    private Integer plannedAuditYear;

    /** Q1 | Q2 | Q3 | Q4 */
    @Column(name = "planned_quarter", length = 10)
    private String plannedQuarter;

    @Column(name = "linked_risk_id")
    private Long linkedRiskId;

    @Column(name = "linked_control_ids", length = 500)
    private String linkedControlIds;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

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
