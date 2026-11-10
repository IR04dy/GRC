package com.tss.grc.module.ai.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Saudi GRC implementation prompt §27 — AI-assisted features ledger.
 *
 * <p>Every AI suggestion is persisted as a draft record so a human can
 * accept or reject it before it changes any real entity. The output is
 * structured JSON (not free-form text) so downstream services can apply
 * an accepted suggestion deterministically — e.g. an accepted
 * RISK_DESCRIPTION fills cause/consequence fields on the parent Risk.
 *
 * <p>The output comes from one of two sources in {@code AiSuggestionService}: the
 * AI gateway, or a rule-based heuristic used when the gateway call fails or returns
 * no usable draft. Both produce the same JSON shape and this record does not say
 * which one ran - only the free-text {@code rationale} hints at it. Heuristic output
 * is not model output, and neither source changes a real entity until a human
 * accepts the suggestion.
 */
@Entity
@Table(schema = "ai", name = "ai_suggestions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AiSuggestion {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /**
     * RISK_DESCRIPTION | BIA_RTO_RPO | EVIDENCE_QUALITY |
     * INCIDENT_ROOT_CAUSE | BOARD_SUMMARY
     */
    @Column(name = "feature_code", nullable = false, length = 40)
    private String featureCode;

    @Column(name = "related_entity", length = 40)
    private String relatedEntity;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @Column(name = "input_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String inputJson;

    @Column(name = "output_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String outputJson;

    /** DRAFT | ACCEPTED | REJECTED | EXPIRED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    /** 0..100, heuristic-derived; null when not computed. */
    @Column(precision = 5, scale = 2)
    private BigDecimal confidence;

    @Column(columnDefinition = "NVARCHAR(MAX)")
    private String rationale;

    @Column(name = "reviewed_by")
    private Long reviewedBy;

    @Column(name = "reviewed_at")
    private LocalDateTime reviewedAt;

    @Column(name = "reviewer_note", length = 1000)
    private String reviewerNote;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;
}
