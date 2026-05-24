package com.tss.grc.module.aml.sanctions.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — A single sanctions screening check.
 *
 * <p>One row per subject (customer, transaction counterparty, beneficial
 * owner, vendor…) screened against one or more lists. The
 * {@code result} aggregates the per-hit dispositions: NO_HIT when no
 * potential matches were found, POTENTIAL_HIT when at least one hit
 * crossed the 75-score threshold, CONFIRMED_HIT when a hit has been
 * promoted to TRUE_POSITIVE by a reviewer.
 */
@Entity
@Table(schema = "aml", name = "screening_check")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ScreeningCheck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "check_code", nullable = false, length = 64)
    private String checkCode;

    /** CUSTOMER_ONBOARDING | TRANSACTION_COUNTERPARTY | BENEFICIAL_OWNER |
     *  EMPLOYEE_KYC | VENDOR_KYC | AD_HOC */
    @Column(name = "subject_type", nullable = false, length = 40)
    private String subjectType;

    @Column(name = "subject_reference", length = 255)
    private String subjectReference;

    @Column(name = "subject_name_en", nullable = false, length = 500)
    private String subjectNameEn;

    @Column(name = "subject_name_ar", length = 500)
    private String subjectNameAr;

    @Column(name = "subject_dob")
    private LocalDate subjectDob;

    @Column(name = "subject_country", length = 100)
    private String subjectCountry;

    @Column(name = "subject_id_document", length = 255)
    private String subjectIdDocument;

    @Column(name = "check_at", nullable = false)
    private LocalDateTime checkAt = LocalDateTime.now();

    /** MANUAL | REAL_TIME_API | BATCH_FILE | INTEGRATION_VENDOR */
    @Column(name = "check_source", nullable = false, length = 40)
    private String checkSource = "MANUAL";

    @Column(name = "lists_screened_codes", length = 500)
    private String listsScreenedCodes;

    /** NO_HIT | POTENTIAL_HIT | CONFIRMED_HIT */
    @Column(name = "result", nullable = false, length = 20)
    private String result = "NO_HIT";

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
