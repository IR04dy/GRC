package com.tss.grc.module.platform.controls.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Control ↔ Citation mapping. The heart of the framework
 * intersection design: one control can be linked to N citations
 * across many authority documents, with a {@code coverage} flag
 * (FULL | PARTIAL | COMPENSATING) describing the relationship.
 *
 * <p>Without this table the question "what regulations does this
 * control satisfy?" cannot be answered as data.
 */
@Entity
@Table(schema = "platform", name = "control_mappings")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlMapping {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "control_id", nullable = false)
    private Long controlId;

    @Column(name = "citation_id", nullable = false)
    private Long citationId;

    /** FULL | PARTIAL | COMPENSATING */
    @Column(nullable = false, length = 20)
    private String coverage = "FULL";

    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "reviewer_user_id")
    private Long reviewerUserId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
