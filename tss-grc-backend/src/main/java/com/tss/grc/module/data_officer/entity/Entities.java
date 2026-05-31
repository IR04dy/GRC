package com.tss.grc.module.data_officer.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** Sprint 33 — Saudi Data Officer Toolkit entities. Single file because the
 *  module's 10 surfaces share a tight common shape (org_id + reference +
 *  state + bilingual fields + timeline) and splitting into 14 files would
 *  add navigation cost without semantic benefit. */
public final class Entities {
    private Entities() {}

    @Entity(name = "DoEvent")
    @Table(schema = "data_officer", name = "do_event")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class DoEvent extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "entity_type", nullable = false, length = 40) private String entityType;
        @Column(name = "entity_id", nullable = false) private Long entityId;
        @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt = LocalDateTime.now();
        @Column(name = "event_type", nullable = false, length = 64) private String eventType;
        @Column(name = "actor_user_id") private Long actorUserId;
        @Column(name = "status_after", length = 32) private String statusAfter;
        @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)") private String noteEn;
        @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)") private String noteAr;
        @Column(name = "payload_json", columnDefinition = "NVARCHAR(MAX)") private String payloadJson;
    }

    // ─── 1. DSR ────────────────────────────────────────────────────────
    @Entity(name = "DoDsrRequest")
    @Table(schema = "data_officer", name = "dsr_request")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class DsrRequest extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "request_type", nullable = false, length = 24) private String requestType;
        @Column(name = "channel", length = 24) private String channel;
        @Column(name = "requester_name_en", length = 256) private String requesterNameEn;
        @Column(name = "requester_name_ar", length = 256) private String requesterNameAr;
        @Column(name = "requester_national_id", length = 20) private String requesterNationalId;
        @Column(name = "requester_email", length = 256) private String requesterEmail;
        @Column(name = "requester_phone", length = 40) private String requesterPhone;
        @Column(name = "request_summary_en", columnDefinition = "NVARCHAR(MAX)") private String requestSummaryEn;
        @Column(name = "request_summary_ar", columnDefinition = "NVARCHAR(MAX)") private String requestSummaryAr;
        @Column(name = "status", nullable = false, length = 32) private String status = "RECEIVED";
        @Column(name = "received_at", nullable = false) private LocalDateTime receivedAt = LocalDateTime.now();
        @Column(name = "sla_deadline") private LocalDateTime slaDeadline;
        @Column(name = "id_verified_at") private LocalDateTime idVerifiedAt;
        @Column(name = "id_verification_method", length = 40) private String idVerificationMethod;
        @Column(name = "fulfilled_at") private LocalDateTime fulfilledAt;
        @Column(name = "rejection_reason_en", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonEn;
        @Column(name = "rejection_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String rejectionReasonAr;
        @Column(name = "rejection_basis", length = 40) private String rejectionBasis;
        @Column(name = "assigned_user_id") private Long assignedUserId;
        @Column(name = "linked_systems_json", columnDefinition = "NVARCHAR(MAX)") private String linkedSystemsJson;
        @Column(name = "response_text_en", columnDefinition = "NVARCHAR(MAX)") private String responseTextEn;
        @Column(name = "response_text_ar", columnDefinition = "NVARCHAR(MAX)") private String responseTextAr;
        @Column(name = "response_attachment_evidence_id") private Long responseAttachmentEvidenceId;
    }

    // ─── 2. Consent ────────────────────────────────────────────────────
    @Entity(name = "DoConsentRecord")
    @Table(schema = "data_officer", name = "consent_record")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class ConsentRecord extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "data_subject_id", length = 64) private String dataSubjectId;
        @Column(name = "purpose_code", nullable = false, length = 40) private String purposeCode;
        @Column(name = "purpose_label_en", length = 256) private String purposeLabelEn;
        @Column(name = "purpose_label_ar", length = 256) private String purposeLabelAr;
        @Column(name = "notice_version_id") private Long noticeVersionId;
        @Column(name = "channel", length = 24) private String channel;
        @Column(name = "consent_text_en", columnDefinition = "NVARCHAR(MAX)") private String consentTextEn;
        @Column(name = "consent_text_ar", columnDefinition = "NVARCHAR(MAX)") private String consentTextAr;
        @Column(name = "granted_at") private LocalDateTime grantedAt;
        @Column(name = "granted_evidence", length = 256) private String grantedEvidence;
        @Column(name = "withdrawn_at") private LocalDateTime withdrawnAt;
        @Column(name = "withdrawn_reason", length = 256) private String withdrawnReason;
        @Column(name = "valid_until") private LocalDateTime validUntil;
        @Column(name = "status", nullable = false, length = 24) private String status = "GRANTED";
        @Column(name = "legal_basis", length = 40) private String legalBasis;
        @Column(name = "is_special_category", nullable = false) private Boolean isSpecialCategory = false;
    }

    // ─── 3. DPIA ───────────────────────────────────────────────────────
    @Entity(name = "DoDpia")
    @Table(schema = "data_officer", name = "dpia")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class Dpia extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "title_en", nullable = false, length = 256) private String titleEn;
        @Column(name = "title_ar", nullable = false, length = 256) private String titleAr;
        @Column(name = "processing_description_en", columnDefinition = "NVARCHAR(MAX)") private String processingDescriptionEn;
        @Column(name = "processing_description_ar", columnDefinition = "NVARCHAR(MAX)") private String processingDescriptionAr;
        @Column(name = "trigger_reason", nullable = false, length = 40) private String triggerReason;
        @Column(name = "is_high_risk", nullable = false) private Boolean isHighRisk = false;
        @Column(name = "status", nullable = false, length = 24) private String status = "DRAFT";
        @Column(name = "data_owner_user_id") private Long dataOwnerUserId;
        @Column(name = "dpo_user_id") private Long dpoUserId;
        @Column(name = "submitted_at") private LocalDateTime submittedAt;
        @Column(name = "dpo_opinion_en", columnDefinition = "NVARCHAR(MAX)") private String dpoOpinionEn;
        @Column(name = "dpo_opinion_ar", columnDefinition = "NVARCHAR(MAX)") private String dpoOpinionAr;
        @Column(name = "dpo_opinion_at") private LocalDateTime dpoOpinionAt;
        @Column(name = "decided_at") private LocalDateTime decidedAt;
        @Column(name = "decision_rationale_en", columnDefinition = "NVARCHAR(MAX)") private String decisionRationaleEn;
        @Column(name = "decision_rationale_ar", columnDefinition = "NVARCHAR(MAX)") private String decisionRationaleAr;
        @Column(name = "conditions_en", columnDefinition = "NVARCHAR(MAX)") private String conditionsEn;
        @Column(name = "conditions_ar", columnDefinition = "NVARCHAR(MAX)") private String conditionsAr;
        @Column(name = "next_review_due") private LocalDate nextReviewDue;
        @Column(name = "linked_processing_activity_id") private Long linkedProcessingActivityId;
    }

    // ─── 4. Breach ─────────────────────────────────────────────────────
    @Entity(name = "DoBreach")
    @Table(schema = "data_officer", name = "breach")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class Breach extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "title_en", nullable = false, length = 256) private String titleEn;
        @Column(name = "title_ar", nullable = false, length = 256) private String titleAr;
        @Column(name = "detected_at", nullable = false) private LocalDateTime detectedAt;
        @Column(name = "discovered_at", nullable = false) private LocalDateTime discoveredAt = LocalDateTime.now();
        @Column(name = "sdaia_deadline") private LocalDateTime sdaiaDeadline;
        @Column(name = "status", nullable = false, length = 24) private String status = "DETECTED";
        @Column(name = "breach_type", length = 40) private String breachType;
        @Column(name = "cause", length = 40) private String cause;
        @Column(name = "severity", length = 16) private String severity;
        @Column(name = "affected_record_count") private Long affectedRecordCount;
        @Column(name = "affected_subject_count") private Long affectedSubjectCount;
        @Column(name = "affected_categories_json", columnDefinition = "NVARCHAR(MAX)") private String affectedCategoriesJson;
        @Column(name = "is_special_category", nullable = false) private Boolean isSpecialCategory = false;
        @Column(name = "is_cross_border", nullable = false) private Boolean isCrossBorder = false;
        @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
        @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
        @Column(name = "containment_actions_en", columnDefinition = "NVARCHAR(MAX)") private String containmentActionsEn;
        @Column(name = "containment_actions_ar", columnDefinition = "NVARCHAR(MAX)") private String containmentActionsAr;
        @Column(name = "sdaia_notified_at") private LocalDateTime sdaiaNotifiedAt;
        @Column(name = "sdaia_reference", length = 64) private String sdaiaReference;
        @Column(name = "subjects_notified_at") private LocalDateTime subjectsNotifiedAt;
        @Column(name = "subject_notification_template_en", columnDefinition = "NVARCHAR(MAX)") private String subjectNotificationTemplateEn;
        @Column(name = "subject_notification_template_ar", columnDefinition = "NVARCHAR(MAX)") private String subjectNotificationTemplateAr;
        @Column(name = "closed_at") private LocalDateTime closedAt;
        @Column(name = "closure_summary_en", columnDefinition = "NVARCHAR(MAX)") private String closureSummaryEn;
        @Column(name = "closure_summary_ar", columnDefinition = "NVARCHAR(MAX)") private String closureSummaryAr;
        @Column(name = "linked_incident_id") private Long linkedIncidentId;
    }

    // ─── 5. PrivacyNotice ──────────────────────────────────────────────
    @Entity(name = "DoPrivacyNotice")
    @Table(schema = "data_officer", name = "privacy_notice")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class PrivacyNotice extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "version_label", nullable = false, length = 40) private String versionLabel;
        @Column(name = "channel", nullable = false, length = 24) private String channel;
        @Column(name = "title_en", nullable = false, length = 256) private String titleEn;
        @Column(name = "title_ar", nullable = false, length = 256) private String titleAr;
        @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String bodyEn;
        @Column(name = "body_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)") private String bodyAr;
        @Column(name = "status", nullable = false, length = 24) private String status = "DRAFT";
        @Column(name = "effective_from") private LocalDate effectiveFrom;
        @Column(name = "published_at") private LocalDateTime publishedAt;
        @Column(name = "superseded_by_id") private Long supersededById;
        @Column(name = "legal_review_user_id") private Long legalReviewUserId;
        @Column(name = "requires_re_consent", nullable = false) private Boolean requiresReConsent = false;
    }

    // ─── 6. Retention ──────────────────────────────────────────────────
    @Entity(name = "DoRetentionRule")
    @Table(schema = "data_officer", name = "retention_rule")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class RetentionRule extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "data_category", nullable = false, length = 64) private String dataCategory;
        @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
        @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
        @Column(name = "retention_months", nullable = false) private Integer retentionMonths;
        @Column(name = "legal_basis_en", columnDefinition = "NVARCHAR(MAX)") private String legalBasisEn;
        @Column(name = "legal_basis_ar", columnDefinition = "NVARCHAR(MAX)") private String legalBasisAr;
        @Column(name = "disposal_method", length = 32) private String disposalMethod;
        @Column(name = "review_cadence_months") private Integer reviewCadenceMonths;
        @Column(name = "next_review_due") private LocalDate nextReviewDue;
        @Column(name = "owner_role", length = 64) private String ownerRole;
        @Column(name = "owner_user_id") private Long ownerUserId;
        @Column(name = "linked_asset_ref", length = 64) private String linkedAssetRef;
        @Column(name = "status", nullable = false, length = 24) private String status = "ACTIVE";
    }

    @Entity(name = "DoDisposalJob")
    @Table(schema = "data_officer", name = "disposal_job")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class DisposalJob extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "retention_rule_id", nullable = false) private Long retentionRuleId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "scheduled_for", nullable = false) private LocalDate scheduledFor;
        @Column(name = "started_at") private LocalDateTime startedAt;
        @Column(name = "completed_at") private LocalDateTime completedAt;
        @Column(name = "status", nullable = false, length = 24) private String status = "SCHEDULED";
        @Column(name = "records_disposed") private Long recordsDisposed;
        @Column(name = "evidence_hash", length = 128) private String evidenceHash;
        @Column(name = "legal_hold_reason", columnDefinition = "NVARCHAR(MAX)") private String legalHoldReason;
    }

    // ─── 7. NDR ────────────────────────────────────────────────────────
    @Entity(name = "DoNdrSubmission")
    @Table(schema = "data_officer", name = "ndr_submission")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class NdrSubmission extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "cycle_year", nullable = false) private Integer cycleYear;
        @Column(name = "title_en", length = 256) private String titleEn;
        @Column(name = "title_ar", length = 256) private String titleAr;
        @Column(name = "status", nullable = false, length = 24) private String status = "DRAFT";
        @Column(name = "dataset_count", nullable = false) private Integer datasetCount = 0;
        @Column(name = "submitted_at") private LocalDateTime submittedAt;
        @Column(name = "submission_ref", length = 64) private String submissionRef;
        @Column(name = "accepted_at") private LocalDateTime acceptedAt;
        @Column(name = "response_summary_en", columnDefinition = "NVARCHAR(MAX)") private String responseSummaryEn;
        @Column(name = "response_summary_ar", columnDefinition = "NVARCHAR(MAX)") private String responseSummaryAr;
    }

    // ─── 8. Catalog ────────────────────────────────────────────────────
    @Entity(name = "DoCatalogEntry")
    @Table(schema = "data_officer", name = "catalog_entry")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class CatalogEntry extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "dataset_name_en", nullable = false, length = 256) private String datasetNameEn;
        @Column(name = "dataset_name_ar", length = 256) private String datasetNameAr;
        @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
        @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
        @Column(name = "domain", length = 40) private String domain;
        @Column(name = "source_system", length = 128) private String sourceSystem;
        @Column(name = "storage_location", length = 256) private String storageLocation;
        @Column(name = "classification", length = 16) private String classification;
        @Column(name = "contains_pii", nullable = false) private Boolean containsPii = false;
        @Column(name = "contains_special_category", nullable = false) private Boolean containsSpecialCategory = false;
        @Column(name = "owner_role", length = 64) private String ownerRole;
        @Column(name = "owner_user_id") private Long ownerUserId;
        @Column(name = "steward_user_id") private Long stewardUserId;
        @Column(name = "custodian_team", length = 128) private String custodianTeam;
        @Column(name = "row_count_approx") private Long rowCountApprox;
        @Column(name = "refresh_frequency", length = 40) private String refreshFrequency;
        @Column(name = "last_profiled_at") private LocalDateTime lastProfiledAt;
    }

    @Entity(name = "DoCatalogLineage")
    @Table(schema = "data_officer", name = "catalog_lineage")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class CatalogLineage extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "source_entry_id", nullable = false) private Long sourceEntryId;
        @Column(name = "target_entry_id", nullable = false) private Long targetEntryId;
        @Column(name = "transform_label", length = 256) private String transformLabel;
    }

    // ─── 9. Quality ────────────────────────────────────────────────────
    @Entity(name = "DoQualityRule")
    @Table(schema = "data_officer", name = "quality_rule")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class QualityRule extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "catalog_entry_id", nullable = false) private Long catalogEntryId;
        @Column(name = "rule_name_en", nullable = false, length = 256) private String ruleNameEn;
        @Column(name = "rule_name_ar", length = 256) private String ruleNameAr;
        @Column(name = "dimension", nullable = false, length = 24) private String dimension;
        @Column(name = "rule_expression", columnDefinition = "NVARCHAR(MAX)") private String ruleExpression;
        @Column(name = "threshold_pct", precision = 5, scale = 2) private BigDecimal thresholdPct;
        @Column(name = "severity", length = 16) private String severity;
        @Column(name = "is_active", nullable = false) private Boolean isActive = true;
    }

    @Entity(name = "DoQualityRun")
    @Table(schema = "data_officer", name = "quality_run")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class QualityRun extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "quality_rule_id", nullable = false) private Long qualityRuleId;
        @Column(name = "run_at", nullable = false) private LocalDateTime runAt = LocalDateTime.now();
        @Column(name = "records_checked") private Long recordsChecked;
        @Column(name = "records_failed") private Long recordsFailed;
        @Column(name = "pass_pct", precision = 5, scale = 2) private BigDecimal passPct;
        @Column(name = "band", length = 8) private String band;
        @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)") private String notesEn;
        @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)") private String notesAr;
    }

    // ─── 10. Ethics ────────────────────────────────────────────────────
    @Entity(name = "DoEthicsReview")
    @Table(schema = "data_officer", name = "ethics_review")
    @Where(clause = "is_deleted = 0")
    @Getter @Setter @NoArgsConstructor
    public static class EthicsReview extends BaseEntity {
        @Column(name = "organisation_id", nullable = false) private Long organisationId;
        @Column(name = "reference", nullable = false, length = 64) private String reference;
        @Column(name = "subject_type", length = 40) private String subjectType;
        @Column(name = "subject_ref", length = 128) private String subjectRef;
        @Column(name = "title_en", nullable = false, length = 256) private String titleEn;
        @Column(name = "title_ar", nullable = false, length = 256) private String titleAr;
        @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)") private String summaryEn;
        @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)") private String summaryAr;
        @Column(name = "requested_at", nullable = false) private LocalDateTime requestedAt = LocalDateTime.now();
        @Column(name = "status", nullable = false, length = 24) private String status = "REQUESTED";
        @Column(name = "risk_dimensions_json", columnDefinition = "NVARCHAR(MAX)") private String riskDimensionsJson;
        @Column(name = "committee_meeting_at") private LocalDateTime committeeMeetingAt;
        @Column(name = "opinion_en", columnDefinition = "NVARCHAR(MAX)") private String opinionEn;
        @Column(name = "opinion_ar", columnDefinition = "NVARCHAR(MAX)") private String opinionAr;
        @Column(name = "conditions_en", columnDefinition = "NVARCHAR(MAX)") private String conditionsEn;
        @Column(name = "conditions_ar", columnDefinition = "NVARCHAR(MAX)") private String conditionsAr;
        @Column(name = "decided_at") private LocalDateTime decidedAt;
        @Column(name = "next_review_due") private LocalDate nextReviewDue;
    }
}
