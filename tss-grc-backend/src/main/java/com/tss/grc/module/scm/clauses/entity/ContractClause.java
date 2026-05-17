package com.tss.grc.module.scm.clauses.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Standard contractual clause template (Saudi GRC §3.3 + gap-analysis item G).
 *
 * <p>Lifecycle: {@code DRAFT → APPROVED → DEPRECATED}.
 */
@Entity
@Table(schema = "scm", name = "contract_clauses")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ContractClause {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;

    @Column(name = "clause_code", nullable = false, length = 64) private String clauseCode;
    @Column(name = "title_en", nullable = false, length = 255)   private String titleEn;
    @Column(name = "title_ar", length = 255)                     private String titleAr;

    /** DATA_PROTECTION | SLA | AUDIT_RIGHTS | EXIT_RIGHTS | SECURITY | COMPLIANCE | LIABILITY | SUBPROCESSORS | FORCE_MAJEURE | OTHER. */
    @Column(nullable = false, length = 20) private String category;

    @Column(name = "applicable_to", columnDefinition = "NVARCHAR(MAX)") private String applicableTo;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String bodyEn;
    @Column(name = "body_ar", columnDefinition = "NVARCHAR(MAX)") private String bodyAr;

    @Column(name = "guidance_en", columnDefinition = "NVARCHAR(MAX)") private String guidanceEn;
    @Column(name = "guidance_ar", columnDefinition = "NVARCHAR(MAX)") private String guidanceAr;

    @Column(name = "regulatory_refs", columnDefinition = "NVARCHAR(MAX)") private String regulatoryRefs;

    /** LOW | MEDIUM | HIGH | CRITICAL. */
    @Column(name = "risk_level", nullable = false, length = 10) private String riskLevel = "MEDIUM";

    /** FIXED | NEGOTIABLE | DEFAULT. */
    @Column(nullable = false, length = 15) private String negotiability = "NEGOTIABLE";

    @Column(nullable = false, length = 20) private String version = "1.0";

    /** DRAFT | APPROVED | DEPRECATED. */
    @Column(nullable = false, length = 20) private String status = "APPROVED";

    @Column(name = "approved_at")    private LocalDateTime approvedAt;
    @Column(name = "approved_by")    private Long          approvedBy;
    @Column(name = "approver_name", length = 255) private String approverName;
    @Column(name = "deprecated_at")  private LocalDateTime deprecatedAt;
    @Column(name = "deprecation_reason", length = 500) private String deprecationReason;

    @Column(length = 255)         private String owner;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String tags;

    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long          deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long          createdBy;
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long          updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
