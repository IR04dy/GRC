package com.tss.grc.module.change_mgmt.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * S8e / CE-2 — a change model: the record that makes "pre-approved" mean something (V354).
 *
 * <p>G-2: <i>"No change model catalogue — STANDARD is an unbacked string, so 'pre-approved' is a
 * claim with no record."</i> Measured 2026-08-04: <b>5</b> STANDARD changes in each database, all of
 * them on the {@code AUTO} cell with {@code min_approvals = 0}, and <b>zero</b> ever referred to a
 * committee. A change was pre-approved because somebody typed {@code STANDARD} and {@code LOW}.
 *
 * <p>A model is the pre-approval: a named procedure, authorised by somebody, <b>with an expiry</b>.
 * {@code ck_cmodel_preapproval} makes {@code pre_approved = 1} unrepresentable without all three —
 * the {@code ck_change_ratification_clock} pattern, where the incomplete state cannot exist rather
 * than being validated in a service somebody later bypasses.
 *
 * <p><b>{@link #authorisedUntil} is why this is a control and not a label.</b> A pre-approval that
 * never expires is F2-62's shape: a control that can never come due. {@link #isLivePreApproval}
 * is the predicate every consumer must use — never {@code pre_approved} on its own.
 */
@Entity
@Table(schema = "change_mgmt", name = "change_model")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ChangeModel {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", insertable = false, updatable = false)
    private java.util.UUID externalId;

    /** Unique per tenant among live rows ({@code ux_change_model_code}). What a change cites. */
    @NotBlank
    @Column(name = "code", nullable = false, length = 64)
    private String code;

    @NotBlank
    @Column(name = "name_en", nullable = false, length = 300)
    private String nameEn;

    /** House rule 7 — mandatory, and NOT NULL in the schema too (V354 self-check 2). */
    @NotBlank
    @Column(name = "name_ar", nullable = false, length = 300)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "change_type", nullable = false, length = 24)
    private String changeType;      // STANDARD | NORMAL | EMERGENCY

    @Column(name = "risk_default", length = 16)
    private String riskDefault;     // LOW | MEDIUM | HIGH | CRITICAL, or null

    /**
     * A gate this model claims regardless of the matrix cell, or {@code null} to leave the matrix
     * in charge. Deliberately advisory-free: {@code ChangeModelService} exposes it, and only the
     * authorisation resolution reads it, so it cannot become the F2-95 shape (a column configured
     * everywhere and applied nowhere).
     */
    @Column(name = "gate_override", length = 8)
    private String gateOverride;    // AUTO | CAB | ECAB, or null

    @Column(name = "pre_approved", nullable = false)
    private Boolean preApproved = false;

    @Column(name = "authorised_by_id")
    private Long authorisedById;

    @Column(name = "authorised_by_name", length = 200)
    private String authorisedByName;

    @Column(name = "authorised_at")
    private LocalDateTime authorisedAt;

    /** The expiry. Without it a pre-approval is permanent, which is not a control. */
    @Column(name = "authorised_until")
    private LocalDateTime authorisedUntil;

    @Column(name = "standard_steps", columnDefinition = "NVARCHAR(MAX)")
    private String standardSteps;

    @Column(name = "rollback_template", columnDefinition = "NVARCHAR(MAX)")
    private String rollbackTemplate;

    @Column(name = "test_template", columnDefinition = "NVARCHAR(MAX)")
    private String testTemplate;

    @Column(name = "max_lead_time_hours")
    private Integer maxLeadTimeHours;

    @Column(name = "review_cadence", length = 32)
    private String reviewCadence;

    @Column(name = "status", nullable = false, length = 16)
    private String status = ChangeModelStatus.DRAFT;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by", length = 200)
    private String createdBy;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "updated_by", length = 200)
    private String updatedBy;

    @Version
    @Column(name = "row_version", insertable = false, updatable = false)
    private byte[] rowVersion;

    /**
     * The only honest way to ask "does this model confer pre-approval right now?".
     *
     * <p>⚠ Three conditions, and each is a defect this platform has already had: the flag alone is
     * a label; {@code APPROVED} alone lets a draft authorise work; and ignoring
     * {@code authorisedUntil} lets an <b>expired</b> pre-approval keep authorising — the same
     * mistake as counting EXPIRED evidence as verified, which S8a refused because EXPIRED is 29 of
     * the 78 live evidence items.
     */
    public boolean isLivePreApproval(LocalDateTime now) {
        return Boolean.TRUE.equals(preApproved)
            && ChangeModelStatus.APPROVED.equals(status)
            && authorisedUntil != null
            && authorisedUntil.isAfter(now);
    }

    /** True when the model once conferred pre-approval and the authorisation has lapsed. */
    public boolean isExpiredPreApproval(LocalDateTime now) {
        return Boolean.TRUE.equals(preApproved)
            && authorisedUntil != null
            && !authorisedUntil.isAfter(now);
    }
}
