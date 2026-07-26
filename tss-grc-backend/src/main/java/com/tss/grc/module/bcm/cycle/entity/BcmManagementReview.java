package com.tss.grc.module.bcm.cycle.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Management review record — audit Tier-3 #18 / ISO 22301 §9.3.
 *
 * <p>Captures the periodic management-review meeting at which the BCM
 * Manager presents BCMS performance to the executive sponsor and the
 * meeting body resolves decisions + improvement actions. The schema
 * stores attendees, agenda, KPI snapshot, decisions, and improvement
 * actions as JSON blobs (each item small + variable shape, doesn't
 * warrant a child table).
 */
@Entity
@Table(schema = "bcm", name = "management_reviews")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BcmManagementReview {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "review_code", nullable = false, length = 50)
    private String reviewCode;

    @Column(name = "cycle_id")
    private Long cycleId;

    @Column(name = "review_date", nullable = false)
    private LocalDate reviewDate;

    @Column(name = "chairperson_name", nullable = false, length = 255)
    private String chairpersonName;

    @Column(name = "attendees_json", columnDefinition = "NVARCHAR(MAX)")
    private String attendeesJson;

    @Column(name = "agenda_json", columnDefinition = "NVARCHAR(MAX)")
    private String agendaJson;

    @Column(name = "inputs_summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String inputsSummaryEn;
    @Column(name = "inputs_summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String inputsSummaryAr;

    /** KPI snapshot at meeting time — see V112 docstring for shape. */
    @Column(name = "kpi_snapshot_json", columnDefinition = "NVARCHAR(MAX)")
    private String kpiSnapshotJson;

    @Column(name = "decisions_json", columnDefinition = "NVARCHAR(MAX)")
    private String decisionsJson;

    @Column(name = "improvement_actions_json", columnDefinition = "NVARCHAR(MAX)")
    private String improvementActionsJson;

    /** DRAFT | IN_PROGRESS | APPROVED | ARCHIVED */
    @Column(nullable = false, length = 20)
    private String status = "DRAFT";

    @Column(name = "approved_by", length = 255)
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private String approvedBy;

    @Column(name = "approved_at")
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    private LocalDateTime approvedAt;

    @Column(name = "notes_en", columnDefinition = "NVARCHAR(MAX)")
    private String notesEn;
    @Column(name = "notes_ar", columnDefinition = "NVARCHAR(MAX)")
    private String notesAr;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
