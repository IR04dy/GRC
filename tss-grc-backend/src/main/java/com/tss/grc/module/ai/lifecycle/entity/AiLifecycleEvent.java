package com.tss.grc.module.ai.lifecycle.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/** Chronological timeline event for AI lifecycle surface. */
@Entity
@Table(schema = "ai_governance", name = "ai_lifecycle_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AiLifecycleEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "model_id")           private Long modelId;
    @Column(name = "validation_id")      private Long validationId;
    @Column(name = "retraining_id")      private Long retrainingId;
    @Column(name = "recertification_id") private Long recertificationId;

    @Column(name = "occurred_at", nullable = false) private LocalDateTime occurredAt;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "actor_name", length = 256) private String actorName;
    @Column(name = "status_after", length = 32) private String statusAfter;
    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)") private String noteEn;
    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)") private String noteAr;
}
