package com.tss.grc.module.ai.lifecycle.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Retraining cycle for an AI/ML model. */
@Entity
@Table(schema = "ai_governance", name = "retraining_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RetrainingEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "model_id", nullable = false)        private Long modelId;
    @Column(name = "reference", nullable = false, length = 64) private String reference;

    /** PROPOSED/APPROVED/IN_PROGRESS/DEPLOYED/ROLLED_BACK/REJECTED/ABANDONED. */
    @Column(name = "status", nullable = false, length = 16)
    private String status = "PROPOSED";

    /** DRIFT/SCHEDULED/MANUAL/POST_INCIDENT. */
    @Column(name = "trigger_reason", length = 24) private String triggerReason;

    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)") private String rationaleEn;
    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)") private String rationaleAr;

    @Column(name = "from_version_label", length = 64) private String fromVersionLabel;
    @Column(name = "to_version_label", length = 64)   private String toVersionLabel;

    @Column(name = "approved_at")   private LocalDateTime approvedAt;
    @Column(name = "started_at")    private LocalDateTime startedAt;
    @Column(name = "deployed_at")   private LocalDate deployedAt;
    @Column(name = "rolled_back_at") private LocalDate rolledBackAt;

    /** Outcome metrics post-deployment — JSON like {accuracy:0.95, ...}. */
    @Column(name = "post_deploy_metrics_json", columnDefinition = "NVARCHAR(MAX)")
    private String postDeployMetricsJson;

    @Column(name = "rollback_reason_en", columnDefinition = "NVARCHAR(MAX)") private String rollbackReasonEn;
    @Column(name = "rollback_reason_ar", columnDefinition = "NVARCHAR(MAX)") private String rollbackReasonAr;

    @Column(name = "owner_user_id") private Long ownerUserId;
}
