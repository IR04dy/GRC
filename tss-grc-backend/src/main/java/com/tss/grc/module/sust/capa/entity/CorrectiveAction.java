package com.tss.grc.module.sust.capa.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Sustainability Corrective Action (V58 — slice 4 of the Sustainability
 * module). CAPA-like remediation register that closes the loop on gaps
 * surfaced by V55 (Climate Risks), V56 (GHG Scope-3 coverage) and V57
 * (Annual Annex open_gaps_count).
 *
 * <p>Composition with source is by ({@code sourceType}, {@code sourceRef})
 * — no FK, so any sustainability gap (current or future) can target the
 * same remediation register.
 *
 * <p>Status machine: {@code OPEN → IN_PROGRESS → COMPLETED → VERIFIED →
 * CLOSED}; any state can be cancelled. Rules enforced in
 * {@code CorrectiveActionService}:
 * <ul>
 *   <li><b>Rule A</b> — verifier cannot equal owner (separation of duties).</li>
 *   <li><b>Rule B</b> — {@code COMPLETED} requires {@code evidenceRef}.</li>
 *   <li><b>Rule C</b> — {@code CLOSED} is terminal.</li>
 * </ul>
 */
@Entity
@Table(schema = "sust", name = "corrective_action")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CorrectiveAction {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", length = 64)         private String externalId;
    @Column(name = "action_code", length = 40)         private String actionCode;

    @Column(name = "source_type", nullable = false, length = 30) private String sourceType;
    @Column(name = "source_ref", length = 64)                    private String sourceRef;

    @Column(name = "title_en", nullable = false, length = 255) private String titleEn;
    @Column(name = "title_ar", length = 255)                   private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;

    @Column(nullable = false, length = 20) private String priority = "MEDIUM";
    @Column(nullable = false, length = 20) private String status   = "OPEN";

    /** CT-3 (V297) — CAR/PAR classification: CORRECTIVE (reactive, gap-driven)
     *  or PREVENTIVE (raised before a nonconformity materialises). One register,
     *  one lifecycle; auto-opened CAPAs are always CORRECTIVE. */
    @Column(name = "action_type", nullable = false, length = 20) private String actionType = "CORRECTIVE";

    @Column(name = "owner_name",  length = 255) private String ownerName;
    @Column(name = "owner_email", length = 255) private String ownerEmail;
    @Column(name = "assigned_to", length = 255) private String assignedTo;

    @Column(name = "target_date")     private LocalDate     targetDate;
    @Column(name = "started_at")      private LocalDateTime startedAt;
    @Column(name = "completed_at")    private LocalDateTime completedAt;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "verified_by", length = 255) private String verifiedBy;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "verified_at")     private LocalDateTime verifiedAt;
    @Column(name = "closed_at")       private LocalDateTime closedAt;

    @Column(name = "root_cause_en",       columnDefinition = "NVARCHAR(MAX)") private String rootCauseEn;
    @Column(name = "root_cause_ar",       columnDefinition = "NVARCHAR(MAX)") private String rootCauseAr;
    @Column(name = "remediation_plan_en", columnDefinition = "NVARCHAR(MAX)") private String remediationPlanEn;
    @Column(name = "remediation_plan_ar", columnDefinition = "NVARCHAR(MAX)") private String remediationPlanAr;

    @Column(name = "evidence_ref", length = 255) private String evidenceRef;

    @Column(name = "cost_estimate_sar", precision = 18, scale = 2) private BigDecimal costEstimateSar;
    @Column(name = "effort_hours") private Integer effortHours;

    @Column(name = "linked_capa_id") private Long linkedCapaId;
    @Column(name = "linked_risk_id") private Long linkedRiskId;

    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;

    @Column(name = "is_deleted", nullable = false) private Boolean isDeleted = false;
    @Column(name = "created_at", nullable = false, updatable = false) private LocalDateTime createdAt;
    @Column(name = "created_by", length = 100)                        private String createdBy;
    @Column(name = "updated_at") private LocalDateTime updatedAt;
    @Column(name = "updated_by", length = 100) private String updatedBy;

    @PrePersist
    void onCreate() { if (createdAt == null) createdAt = LocalDateTime.now(); }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
