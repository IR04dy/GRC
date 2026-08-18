package com.tss.grc.module.platform.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Generic issue tracker entity — a thin, cross-module finding record.
 *
 * <p>Used wherever a module surfaces an actionable problem that needs
 * tracking but doesn't warrant its own bespoke table:
 * <ul>
 *   <li>audit findings raised from working papers</li>
 *   <li>cyber observations that don't reach incident severity</li>
 *   <li>BCM exercise gaps that aren't yet promoted to a formal Gap</li>
 *   <li>manual issues opened by users via {@code /issues/new}</li>
 * </ul>
 *
 * <p>Soft-FK fields ({@code sourceEntityType} + {@code sourceEntityId},
 * {@code capaId}, {@code evidenceId}) let an issue link back to the
 * originating record and forward to its remediation, without forcing
 * a hard FK that would couple deletion lifecycles.
 *
 * <p>Lifecycle: OPEN → IN_PROGRESS → (BLOCKED →)? RESOLVED → CLOSED, or
 * OPEN → CANCELLED. {@code resolution} only meaningful in RESOLVED/CLOSED.
 */
@Entity
@Table(schema = "platform", name = "issues")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Issue extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Tenant-unique short code; assigned by service layer (e.g. ISS-2026-000123). */
    @Column(name = "issue_code", nullable = false, length = 50)
    private String issueCode;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;

    /** audit | cyber | bcm | risk | compliance | scm | tprm | privacy | manual */
    @Column(name = "source_module", nullable = false, length = 50)
    private String sourceModule;

    @Column(name = "source_entity_type", length = 100)
    private String sourceEntityType;

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity;

    /** LOW | NORMAL | HIGH | URGENT */
    @Column(name = "priority", nullable = false, length = 20)
    private String priority;

    @Column(name = "assignee_user_id") private Long   assigneeUserId;
    @Column(name = "assignee_role", length = 100)     private String assigneeRole;
    @Column(name = "due_date")        private LocalDate dueDate;

    /** OPEN | IN_PROGRESS | BLOCKED | RESOLVED | CLOSED | CANCELLED */
    @Column(name = "status", nullable = false, length = 20)
    private String status;

    /** FIXED | WONT_FIX | DUPLICATE | NOT_REPRODUCIBLE | DEFERRED — only set on resolve/close. */
    @Column(name = "resolution", length = 20)
    private String resolution;

    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;

    @Column(name = "resolved_at")  private LocalDateTime resolvedAt;
    @Column(name = "resolved_by")  private Long          resolvedBy;
    @Column(name = "closed_at")    private LocalDateTime closedAt;

    /** Soft FK to capa.capa_tasks. */
    @Column(name = "capa_id") private Long capaId;

    /** Soft FK to evidence.evidence_items. */
    @Column(name = "evidence_id") private Long evidenceId;
}
