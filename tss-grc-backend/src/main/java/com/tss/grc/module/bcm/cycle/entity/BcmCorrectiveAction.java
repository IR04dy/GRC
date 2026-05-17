package com.tss.grc.module.bcm.cycle.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * BCM corrective action — Sprint BCM-3.
 *
 * <p>BCM-specific CAPA register (separate from the platform-wide
 * {@code capa.capa_tasks} table so BCM keeps its own SLA + close-out
 * rules; cross-link via {@code platform_capa_id} when an organisation
 * federates CAPAs across modules).
 */
@Entity
@Table(schema = "bcm", name = "corrective_actions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmCorrectiveAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "gap_id")
    private Long gapId;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "action_plan", columnDefinition = "NVARCHAR(MAX)")
    private String actionPlan;

    @Column(name = "owner_id")
    private Long ownerId;

    /** LOW | MEDIUM | HIGH | CRITICAL */
    @Column(nullable = false, length = 20)
    private String priority = "MEDIUM";

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "progress_percent", nullable = false)
    private Integer progressPercent = 0;

    @Column(name = "evidence_id")
    private Long evidenceId;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "closure_date")
    private LocalDate closureDate;

    /** OPEN | ASSIGNED | IN_PROGRESS | EVIDENCE_SUBMITTED | UNDER_REVIEW | CLOSED | OVERDUE | REOPENED */
    @Column(nullable = false, length = 30)
    private String status = "OPEN";

    @Column(name = "platform_capa_id")
    private Long platformCapaId;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
