package com.tss.grc.module.privacy.extensions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * PDPL Art. 18 retention-disposal certificate.
 *
 * <p>State machine: SCHEDULED → IN_PROGRESS → COMPLETED → VERIFIED.
 * CANCELLED is an off-path terminal.
 */
@Entity
@Table(schema = "privacy", name = "disposal_certificates")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DisposalCertificate {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** ropa | data_asset | system. */
    @Column(name = "related_entity_type", nullable = false, length = 50) private String relatedEntityType;
    @Column(name = "related_entity_id", nullable = false, length = 120)  private String relatedEntityId;
    @Column(name = "related_entity_label", length = 255)                 private String relatedEntityLabel;

    @Column(name = "retention_rule_ref", length = 120) private String retentionRuleRef;

    @Column(name = "scope_description_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String scopeDescriptionEn;
    @Column(name = "scope_description_ar", columnDefinition = "NVARCHAR(MAX)") private String scopeDescriptionAr;
    @Column(name = "records_count_est") private Long recordsCountEst;

    /** DELETE | ANONYMIZE | ARCHIVE | DESTROY. */
    @Column(name = "disposal_method", nullable = false, length = 20) private String disposalMethod;
    @Column(name = "method_details_en", columnDefinition = "NVARCHAR(MAX)") private String methodDetailsEn;

    /** SCHEDULED | IN_PROGRESS | COMPLETED | VERIFIED | CANCELLED. */
    @Column(nullable = false, length = 20) private String status = "SCHEDULED";

    @Column(name = "scheduled_date") private LocalDateTime scheduledDate;
    @Column(name = "executed_at")    private LocalDateTime executedAt;
    @Column(name = "executed_by")    private Long executedBy;
    @Column(name = "executor_name", length = 255) private String executorName;
    @Column(name = "verified_at")    private LocalDateTime verifiedAt;
    @Column(name = "verified_by")    private Long verifiedBy;
    @Column(name = "verifier_name", length = 255) private String verifierName;

    @Column(name = "evidence_ref", length = 120) private String evidenceRef;
    @Column(name = "signature_tx_id") private Long signatureTxId;

    @Column(name = "owner_role", length = 100) private String ownerRole;
    @Column(name = "owner_name", length = 255) private String ownerName;

    @Column(name = "external_id", length = 64) private String externalId;
    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active", nullable = false) private Short isActive = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
