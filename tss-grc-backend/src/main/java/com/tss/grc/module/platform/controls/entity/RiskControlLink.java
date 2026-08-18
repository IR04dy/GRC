package com.tss.grc.module.platform.controls.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Risk ↔ Control link. First-class "Risk-Control Matrix" — the
 * single most common artefact auditors ask for. Added per GRC-expert
 * review R1 in {@code docs/grc/framework-intersection-plan.md}.
 *
 * <p>{@code risk_id} is a soft FK to {@code risk.risks.id} so this
 * table stays decoupled from the risk module's own JPA mappings.
 */
@Entity
@Table(schema = "platform", name = "risk_control_links")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RiskControlLink {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id", nullable = false)
    private Long riskId;

    @Column(name = "control_id", nullable = false)
    private Long controlId;

    /** MITIGATES | DETECTS | TRANSFERS | AVOIDS | COMPENSATES */
    @Column(name = "link_type", nullable = false, length = 20)
    // F2-240 - no initialiser ON PURPOSE. This entity is bound by its own PUT, so an
    // omitted key deserialises to the initialiser and the update's `!= null` guard can
    // never be false - the line reads as a partial update and behaves as a full
    // replace. The default is owned by ControlLibraryService#linkRiskToControl, asserted
    // by NeverFiringNullGuardContractTest in both directions.
    private String linkType;

    /** EFFECTIVE | NEEDS_IMPROVEMENT | INEFFECTIVE | NOT_TESTED */
    @Column(length = 30)
    private String effectiveness;

    @Column(name = "rationale_en", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleEn;

    @Column(name = "rationale_ar", columnDefinition = "NVARCHAR(MAX)")
    private String rationaleAr;

    @Column(name = "last_reviewed_at")
    private LocalDateTime lastReviewedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
