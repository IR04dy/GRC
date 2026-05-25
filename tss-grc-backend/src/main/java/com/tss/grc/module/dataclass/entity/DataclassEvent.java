package com.tss.grc.module.dataclass.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/** Chronological timeline event for Data Classification surface. */
@Entity
@Table(schema = "dataclass", name = "dataclass_event")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DataclassEvent extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "data_asset_id")     private Long dataAssetId;
    @Column(name = "data_flow_id")      private Long dataFlowId;
    @Column(name = "data_exception_id") private Long dataExceptionId;
    @Column(name = "occurred_at", nullable = false)   private LocalDateTime occurredAt;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "actor_user_id") private Long actorUserId;
    @Column(name = "actor_name", length = 256) private String actorName;
    @Column(name = "status_after", length = 32) private String statusAfter;
    @Column(name = "note_en", columnDefinition = "NVARCHAR(MAX)") private String noteEn;
    @Column(name = "note_ar", columnDefinition = "NVARCHAR(MAX)") private String noteAr;
}
