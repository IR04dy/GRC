package com.tss.grc.module.scm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity(name = "ScmVendorAssessment")
@Table(schema = "scm", name = "vendor_assessments")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class VendorAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "vendor_id", nullable = false)
    private Long vendorId;

    @Column(name = "assessment_type", length = 50)
    private String assessmentType = "INITIAL";   // legal: CK_scm_assess_type admits INITIAL

    @Column(name = "assessment_date")
    private LocalDate assessmentDate;

    @Column(name = "next_assessment_date")
    private LocalDate nextAssessmentDate;

    @Column(name = "overall_score", precision = 5, scale = 2)
    private BigDecimal overallScore;

    // F2-241 — `risk_rating` is NOT NULL with the database DEFAULT 'MEDIUM' and had NO entity-side
    // filling mechanism of any kind, while ScmService#applyAssessment writes it only
    // `if (req.getRiskRating() != null)`. A DB DEFAULT does not rescue that: the column is mapped
    // insertable, so Hibernate names it in the INSERT and sends an explicit null, and the create
    // answers 500 for any caller that omits the key. ScmPage always sends one, which is the only
    // reason it has never been seen — and a defence in one client is not a defence (F2-240).
    @Column(name = "risk_rating", length = 20)
    private String riskRating = "MEDIUM";

    // ⚠⚠ F2-241 — this was `= "DRAFT"`, a token `CK_scm_assess_status` REFUSES
    // (PLANNED | IN_PROGRESS | COMPLETED | ESCALATED). Any create that let the initialiser stand
    // sent 'DRAFT' and the database refused the INSERT: HTTP 500 on a SAMA outsourcing supplier
    // assessment. The value here is the column's own DEFAULT — that DEFAULT is the schema author's
    // statement of which token the initial state should be, and where an entity disagrees with it in
    // the direction the CHECK refuses, the entity is the half that is wrong.
    //
    // 🔑 An initialiser is a one-token vocabulary that nothing had ever compared against the column,
    // because it is never DECLARED as a vocabulary. EntityInitialiserAcceptedByColumnTest now
    // constructs every @Entity and asks each field, so this shape cannot recur silently.
    @Column(name = "status", length = 30)
    private String status = "PLANNED";

    @Column(name = "assessor_id")
    private Long assessorId;

    @Column(name = "findings", columnDefinition = "NVARCHAR(MAX)")
    private String findings;

    @Column(name = "recommendations", columnDefinition = "NVARCHAR(MAX)")
    private String recommendations;

    @Column(name = "escalated_to_risk", nullable = false)
    private Boolean escalatedToRisk = false;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;
}
