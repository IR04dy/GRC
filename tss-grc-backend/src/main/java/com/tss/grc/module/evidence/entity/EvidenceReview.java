package com.tss.grc.module.evidence.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "evidence", name = "evidence_reviews")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EvidenceReview extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "evidence_id", nullable = false)
    private Long evidenceId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "result", length = 30)
    private String result;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;

    @Column(name = "status", nullable = false, length = 20)
    private String status = "PENDING";
}
