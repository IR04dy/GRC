package com.tss.grc.module.bcm.comms.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Slice 9-E — pre-approved crisis-communication template register (v2).
 *
 * <p>The v2 register layers on top of the legacy {@code crisis_comms_templates}
 * table (V18) which was limited to 5 audiences and EMAIL/SMS only. v2 closes
 * the SAMA BCF Annex B gap by introducing:
 *
 * <ul>
 *   <li>7-audience taxonomy — BOARD, REGULATOR, CUSTOMERS, STAFF, PARTNERS,
 *       MEDIA, PUBLIC — matching SAMA BCF Annex B "material stakeholder
 *       groups".</li>
 *   <li>10-scenario taxonomy — RANSOMWARE, DATA_BREACH, OUTAGE, NATURAL_DISASTER,
 *       OT_INCIDENT, REGULATORY_NOTIFICATION, MEDIA_RESPONSE, RECOVERY,
 *       LESSONS_LEARNED, GENERIC_MAJOR.</li>
 *   <li>Approval state machine — DRAFT → IN_REVIEW → APPROVED_BY_LEGAL →
 *       APPROVED_BY_CISO → RETIRED — so templates are pre-approved before
 *       they appear in the dispatch picker.</li>
 *   <li>Regulatory anchor citation (PDPL Art.31, SAMA SCRO MoU, NCA ECC-2
 *       §2.7.4, CMA Listing Rules Art.83 …) for the auditor's evidence
 *       chain.</li>
 * </ul>
 */
@Entity
@Table(schema = "bcm", name = "crisis_comms_template_v2")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisCommsTemplateV2 {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 255)
    private String titleAr;

    /** BOARD | REGULATOR | CUSTOMERS | STAFF | PARTNERS | MEDIA | PUBLIC */
    @Column(name = "audience", nullable = false, length = 40)
    private String audience;

    /** RANSOMWARE | DATA_BREACH | OUTAGE | NATURAL_DISASTER | OT_INCIDENT |
     *  REGULATORY_NOTIFICATION | MEDIA_RESPONSE | RECOVERY | LESSONS_LEARNED |
     *  GENERIC_MAJOR */
    @Column(name = "scenario_category", nullable = false, length = 40)
    private String scenarioCategory;

    /** CRITICAL | HIGH | MEDIUM | INFORMATIONAL */
    @Column(name = "severity_band", nullable = false, length = 20)
    private String severityBand = "HIGH";

    @Column(name = "subject_en", nullable = false, length = 255)
    private String subjectEn;

    @Column(name = "subject_ar", nullable = false, length = 255)
    private String subjectAr;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    /** 160-char-aware short variant for SMS / WhatsApp. NULL when email-only. */
    @Column(name = "sms_body_en", length = 500)
    private String smsBodyEn;

    @Column(name = "sms_body_ar", length = 500)
    private String smsBodyAr;

    /** JSON array of {@code {"key":"...","label_en":"...","label_ar":"...","type":"text|number|datetime|textarea","required":true}}. */
    @Column(name = "placeholders_json", columnDefinition = "NVARCHAR(MAX)")
    private String placeholdersJson;

    @Column(name = "regulatory_anchor", length = 500)
    private String regulatoryAnchor;

    /** DRAFT | IN_REVIEW | APPROVED_BY_LEGAL | APPROVED_BY_CISO | RETIRED */
    @Column(name = "approval_status", nullable = false, length = 20)
    private String approvalStatus = "DRAFT";

    @Column(name = "approved_by_legal_at")
    private LocalDateTime approvedByLegalAt;

    @Column(name = "approved_by_legal_name", length = 255)
    private String approvedByLegalName;

    @Column(name = "approved_by_ciso_at")
    private LocalDateTime approvedByCisoAt;

    @Column(name = "approved_by_ciso_name", length = 255)
    private String approvedByCisoName;

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
