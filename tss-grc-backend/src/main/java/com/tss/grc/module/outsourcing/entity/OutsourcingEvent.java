package com.tss.grc.module.outsourcing.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/** Chronological timeline event for Outsourcing &amp; Cloud surface. */
@Entity
@Table(schema = "outsourcing", name = "outsourcing_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OutsourcingEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "arrangement_id")
    private Long arrangementId;

    @Column(name = "cloud_service_id")
    private Long cloudServiceId;

    @Column(name = "review_id")
    private Long reviewId;

    @Column(name = "exit_plan_id")
    private Long exitPlanId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "actor_name", length = 256)
    private String actorName;

    @Column(name = "status_after", length = 32)
    private String statusAfter;

    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)")
    private String noteEn;

    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)")
    private String noteAr;
}
