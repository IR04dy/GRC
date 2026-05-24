package com.tss.grc.module.ethics.whistleblower.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * Timeline entry on a {@link WhistleblowerCase} — Sprint 13.
 *
 * <p>{@link #isVisibleToReporter} controls what shows on the public
 * {@code /status/{token}} page. Investigation notes default to NOT
 * visible (internal); triage decisions + closure summaries default
 * to visible. The Ethics Officer can flip per-row.
 *
 * <p>{@link #actorUserId} is null when the reporter posted a reply
 * via the public-token path.
 */
@Entity
@Table(schema = "ethics", name = "whistleblower_case_update")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WhistleblowerCaseUpdate extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "case_id", nullable = false)
    private Long caseId;

    /**
     * TRIAGE_DECISION | INVESTIGATOR_ASSIGNED | FINDING_RECORDED |
     * STATUS_CHANGED | REPORTER_REPLY | INVESTIGATION_NOTE | CLOSURE.
     */
    @Column(name = "update_type", nullable = false, length = 40)
    private String updateType;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Column(name = "is_visible_to_reporter", nullable = false)
    private Boolean isVisibleToReporter = false;

    @Column(name = "body_en", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyEn;

    @Column(name = "body_ar", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String bodyAr;

    /** Pipe-delimited list of attachment refs / file paths. */
    @Column(name = "attachment_refs", length = 500)
    private String attachmentRefs;
}
