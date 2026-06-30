package com.tss.grc.module.evidence.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * A single comment on an {@link EvidenceRequest} — the collaborative review
 * thread (clarifications, rejection reasons, resubmission notes). Append-only
 * from the UI; soft-deleted via {@code is_deleted}. See migration V240.
 */
@Entity
@Table(schema = "evidence", name = "evidence_request_comments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EvidenceRequestComment extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "request_id", nullable = false)
    private Long requestId;

    /** The user who wrote the comment (resolved to a display name in the response). */
    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "body", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String body;
}
