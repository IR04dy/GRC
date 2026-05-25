package com.tss.grc.module.reginspect.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Timeline entry on a regulatory inspection.
 *
 * <p>Captures state transitions + significant actions so the workspace
 * has a chronological audit trail. Mirrors the pattern used by Fraud
 * cases + OpRes assessments.
 *
 * <p>Update types:
 * <pre>
 *   STATUS_CHANGED        — inspection moved through the state machine
 *   DOCUMENT_REQUEST      — new DR raised
 *   DR_SUBMITTED          — DR answered
 *   FINDING_RECORDED      — provisional finding noted
 *   RESPONSE_DRAFTED      — management response written
 *   INSPECTOR_COMMENT     — comment from regulator captured
 *   INTERNAL_NOTE         — team note (not for regulator-facing audit)
 *   CLOSURE               — inspection closed
 * </pre>
 */
@Entity
@Table(schema = "reginspect", name = "reginspect_inspection_update")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegInspectionUpdate extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "inspection_id", nullable = false)
    private Long inspectionId;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Column(name = "update_type", nullable = false, length = 32)
    private String updateType;

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

    @Column(name = "linked_document_request_id")
    private Long linkedDocumentRequestId;
}
