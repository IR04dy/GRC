package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * NCEMA After-Action Report (AAR) — independent audit Tier-4 #23.
 *
 * <p>Saudi-native version of an ISO 22301 lessons-learned bundle: a
 * structured post-event report with timeline, decisions, regulatory
 * notifications, gaps, and corrective actions. Tied to a parent
 * {@link CrisisEvent} (soft FK — single AAR per event in v1, multiple
 * supported through {@code crisis_event_id} index without a unique
 * constraint).
 *
 * <p>Status lifecycle:
 *   DRAFT → UNDER_REVIEW → APPROVED → PUBLISHED
 *                                    ↓
 *                                RESCINDED
 *
 * <p>The {@code digitalSignatureTxnId} captures the moment the AAR was
 * signed off by the Crisis Manager — pairs with {@code module/signature/}.
 */
@Entity
@Table(schema = "bcm", name = "crisis_aar")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CrisisAar {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "crisis_event_id", nullable = false)
    private Long crisisEventId;

    @Column(name = "report_code", nullable = false, length = 50)
    private String reportCode;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    // NCEMA AAR structured sections — markdown bodies.

    @Column(name = "executive_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryEn;

    @Column(name = "executive_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String executiveSummaryAr;

    @Column(name = "timeline_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String timelineMdEn;

    @Column(name = "timeline_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String timelineMdAr;

    @Column(name = "decisions_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String decisionsMdEn;

    @Column(name = "decisions_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String decisionsMdAr;

    @Column(name = "what_worked_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String whatWorkedMdEn;

    @Column(name = "what_worked_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String whatWorkedMdAr;

    @Column(name = "what_failed_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String whatFailedMdEn;

    @Column(name = "what_failed_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String whatFailedMdAr;

    @Column(name = "root_cause_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseMdEn;

    @Column(name = "root_cause_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rootCauseMdAr;

    @Column(name = "recommendations_md_en", columnDefinition = "NVARCHAR(MAX)")
    private String recommendationsMdEn;

    @Column(name = "recommendations_md_ar", columnDefinition = "NVARCHAR(MAX)")
    private String recommendationsMdAr;

    @Column(name = "regulatory_notifications_md", columnDefinition = "NVARCHAR(MAX)")
    private String regulatoryNotificationsMd;

    @Column(name = "lessons_learned_ids_json", columnDefinition = "NVARCHAR(MAX)")
    private String lessonsLearnedIdsJson;

    @Column(name = "corrective_action_ids_json", columnDefinition = "NVARCHAR(MAX)")
    private String correctiveActionIdsJson;

    @Column(name = "gap_ids_json", columnDefinition = "NVARCHAR(MAX)")
    private String gapIdsJson;

    /** DRAFT | UNDER_REVIEW | APPROVED | PUBLISHED | RESCINDED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "approved_by")
    private Long approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "digital_signature_txn_id")
    private Long digitalSignatureTxnId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
