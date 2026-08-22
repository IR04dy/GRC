package com.tss.grc.module.privacy.dsa.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * PDPL data-sharing agreement (gap-analysis: PDPL data sharing / disclosure).
 *
 * <p>Status machine: {@code DRAFT → IN_REVIEW → ACTIVE → EXPIRED | TERMINATED}.
 * See V51 migration for schema commentary.
 */
@Entity
@Table(schema = "privacy", name = "data_sharing_agreements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataSharingAgreement {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", length = 64)
    private String externalId;

    @Column(name = "agreement_code", length = 40)
    private String agreementCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "recipient_type", nullable = false, length = 30)
    private String recipientType;

    @Column(name = "recipient_name", nullable = false, length = 255)
    private String recipientName;

    @Column(name = "recipient_country", length = 3)
    private String recipientCountry;

    @Column(name = "recipient_legal_role", nullable = false, length = 30)
    private String recipientLegalRole = "CONTROLLER";

    @Column(name = "data_categories", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String dataCategories;

    @Column(name = "data_subjects", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String dataSubjects;

    @Column(name = "purpose_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String purposeEn;

    @Column(name = "purpose_ar", columnDefinition = "NVARCHAR(MAX)")
    private String purposeAr;

    @Column(name = "lawful_basis", nullable = false, length = 30)
    private String lawfulBasis;

    @Column(name = "cross_border_mechanism", nullable = false, length = 30)
    private String crossBorderMechanism = "NONE";

    @Column(name = "retention_period_days")
    private Integer retentionPeriodDays;

    @Column(name = "sub_disclosure_allowed", nullable = false)
    private Short subDisclosureAllowed = 0;

    @Column(name = "subject_notified", nullable = false)
    private Short subjectNotified = 1;

    @Column(name = "linked_ropa_codes", columnDefinition = "NVARCHAR(MAX)")
    private String linkedRopaCodes;

    @Column(name = "linked_privacy_notice_id")
    private Long linkedPrivacyNoticeId;

    @Column(name = "linked_cbt_id")
    private Long linkedCbtId;

    @Column(name = "effective_from")
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "signed_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime signedAt;

    @Column(name = "signer_name", length = 255)
    private String signerName;

    @Column(name = "counterparty_signer", length = 255)
    private String counterpartySigner;

    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "termination_reason_en", columnDefinition = "NVARCHAR(MAX)")
    private String terminationReasonEn;

    /** F2-272 (V388) — house rule 7. Nullable and NOT @NotBlank on purpose: bean validation fires on UPDATE too, so requiring Arabic would make every row that predates V388 uneditable, and nothing can invent Arabic governance text (F2-30's reason). {@code title_ar} and {@code purpose_ar} were already here. */
    @Column(name = "termination_reason_ar", columnDefinition = "NVARCHAR(MAX)")
    private String terminationReasonAr;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
    @Column(name = "is_active",  nullable = false) private Short isActive  = 1;
    @Column(name = "deleted_at") private LocalDateTime deletedAt;
    @Column(name = "deleted_by") private Long deletedBy;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt = LocalDateTime.now();
    @Column(name = "created_by") private Long createdBy;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @Column(name = "updated_by") private Long updatedBy;
    @Column(name = "row_version", nullable = false) private Long rowVersion = 0L;

    @PreUpdate public void onUpdate() { this.updatedAt = LocalDateTime.now(); }
}
