package com.tss.grc.module.privacy_lc.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(schema = "privacy_lc", name = "dsr_cases")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DsrCase {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** ACCESS/RECTIFICATION/ERASURE/OBJECTION/RESTRICTION/PORTABILITY/WITHDRAW_CONSENT */
    @Column(name = "type", nullable = false, length = 30)
    private String type;

    @Column(name = "subject_name", nullable = false) private String subjectName;
    @Column(name = "national_id_last4", nullable = false, length = 10) private String nationalIdLast4;
    @Column(name = "contact_email", nullable = false) private String contactEmail;

    /** INTAKE/VERIFYING_IDENTITY/COLLECTING/REDACTING/DELIVERED/REJECTED/EXTENDED */
    @Column(nullable = false, length = 30)
    private String status = "INTAKE";

    @Column(name = "filed_at", nullable = false) private LocalDateTime filedAt = LocalDateTime.now();
    @Column(name = "identity_verified_at") private LocalDateTime identityVerifiedAt;

    /** NAFATH/ID_DOCUMENT/IN_PERSON/VIDEO_KYC */
    @Column(name = "verification_method", length = 30) private String verificationMethod;

    @Column(name = "data_collected_at") private LocalDateTime dataCollectedAt;
    @Column(name = "data_summary_en", columnDefinition = "NVARCHAR(MAX)") private String dataSummaryEn;
    @Column(name = "data_summary_ar", columnDefinition = "NVARCHAR(MAX)") private String dataSummaryAr;

    @Column(name = "redaction_completed_at") private LocalDateTime redactionCompletedAt;
    @Column(name = "delivery_method", length = 20) private String deliveryMethod;
    @Column(name = "delivered_at") private LocalDateTime deliveredAt;

    /** PDPL Article 4 default: 30 days from filing. */
    @Column(name = "sla_deadline", nullable = false) private LocalDate slaDeadline;

    @Column(nullable = false) private Boolean extended = false;
    @Column(name = "extension_reason_en", columnDefinition = "NVARCHAR(MAX)") private String extensionReasonEn;
    @Column(name = "extension_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String extensionReasonAr;
    @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonEn;
    @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonAr;

    /** Comma-separated ROPA external_ids the data lives in. */
    @Column(name = "ropa_ids", columnDefinition = "NVARCHAR(MAX)") private String ropaIds;

    @Column(name = "is_deleted", nullable = false) private Short isDeleted = 0;
}
