package com.tss.grc.module.workflow_submission.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic wizard-submission ledger for the §7 catalog wizards that don't
 * yet have a dedicated per-domain table.
 *
 * <p>Twelve wizards (Risk Treatment, Risk Acceptance, Incident RCA,
 * Management Review, Digital Signature, Control Test, Cloud CCC, NCA ECC
 * Self-Assessment, Consent Management, Cross-Border Transfer, Supplier
 * Exit Plan, Board Pack) write here so they persist real records without
 * blocking on 12 separate Flyway migrations.
 *
 * <p>{@code payload_json} stores the full wizard draft; {@code summary_en}
 * + {@code regulatory_anchor} carry the regulator-visible columns so the
 * register view can render without parsing the JSON.
 *
 * <p>Dedicated tables can replace this for high-volume wizards later — the
 * payload schema is stable, so a migration would only need to extract.
 */
@Entity
@Table(schema = "workflow", name = "workflow_submissions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class WorkflowSubmission {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    /** Catalog identifier (workflowCatalog.ts id). */
    @Column(name = "workflow_id", nullable = false, length = 80)
    private String workflowId;

    /** ERM | BCM | SCM | NCA | PDPL | COMPLIANCE | EVIDENCE | INCIDENT | GOVERNANCE */
    @Column(name = "workflow_module", nullable = false, length = 40)
    private String workflowModule;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    /** DRAFT | SUBMITTED | UNDER_REVIEW | APPROVED | REJECTED | CLOSED */
    @Column(nullable = false, length = 40)
    private String status = "SUBMITTED";

    @Column(name = "summary_en", length = 1000)
    private String summaryEn;

    @Column(name = "summary_ar", length = 1000)
    private String summaryAr;

    /** e.g. "PDPL Art. 23", "ISO 22301 §9.3", "NCA ECC v2.0" */
    @Column(name = "regulatory_anchor", length = 60)
    private String regulatoryAnchor;

    @Column(name = "payload_json", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String payloadJson;

    @Column(name = "submitted_by")
    private Long submittedBy;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt = LocalDateTime.now();

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

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    private Long updatedBy;
}
