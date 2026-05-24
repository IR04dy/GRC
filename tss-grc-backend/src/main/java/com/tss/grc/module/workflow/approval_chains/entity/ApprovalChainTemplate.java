package com.tss.grc.module.workflow.approval_chains.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Sprint 13 — Approval-chain template (the ladder blueprint).
 *
 * <p>One long-lived row per configured ladder. Steps live in
 * {@link ApprovalChainStep}; live in-flight ladders in
 * {@link ApprovalChainInstance}.
 *
 * <h3>Scoping</h3>
 * The template is matched to a wizard submission via {@code
 * appliesToWorkflowCodes} (comma-separated list of wizard codes) and
 * / or {@code appliesToModule}. A single template per (org, scope)
 * may be marked {@code isDefault = true}; the service guards that
 * uniqueness on set-default.
 */
@Entity
@Table(schema = "workflow", name = "approval_chain_template")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ApprovalChainTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "template_code", nullable = false, length = 64)
    private String templateCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", nullable = false, length = 500)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Comma-separated wizard codes (e.g. "CAPA_CLOSURE,FINDING_CLOSURE"). */
    @Column(name = "applies_to_workflow_codes", columnDefinition = "NVARCHAR(MAX)")
    private String appliesToWorkflowCodes;

    @Column(name = "applies_to_module", length = 40)
    private String appliesToModule;

    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;
}
