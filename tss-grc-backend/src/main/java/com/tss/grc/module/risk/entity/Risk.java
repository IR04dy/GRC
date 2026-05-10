package com.tss.grc.module.risk.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(schema = "risk", name = "risks")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class Risk extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "source_module", length = 50)
    private String sourceModule = "ERM";

    @Column(name = "source_entity_id")
    private Long sourceEntityId;

    @Column(name = "owner_id")
    private Long ownerId;

    @Column(name = "status", length = 20)
    private String status = "OPEN";

    @Column(name = "inherent_likelihood")
    private Integer inherentLikelihood;

    @Column(name = "inherent_impact")
    private Integer inherentImpact;

    // computed persisted column — read-only in JPA
    @Column(name = "inherent_score", insertable = false, updatable = false)
    private Integer inherentScore;

    @Column(name = "residual_likelihood")
    private Integer residualLikelihood;

    @Column(name = "residual_impact")
    private Integer residualImpact;

    @Column(name = "residual_score", insertable = false, updatable = false)
    private Integer residualScore;

    // computed persisted column — read-only
    @Column(name = "risk_level", insertable = false, updatable = false, length = 20)
    private String riskLevel;

    @Column(name = "identified_date")
    private LocalDate identifiedDate = LocalDate.now();

    @Column(name = "review_date")
    private LocalDate reviewDate;

    @Column(name = "workflow_instance_id")
    private Long workflowInstanceId;

    /** Risk-management taxonomy framework (ISO 31000, COSO ERM, NIST RMF) — IA-5. */
    @Column(name = "risk_framework_id")
    private Long riskFrameworkId;

    @Column(name = "risk_framework_clause", length = 100)
    private String riskFrameworkClause;

    /** Compliance framework this risk threatens (NCA ECC, SAMA CSF, PDPL …) — IA-5. */
    @Column(name = "compliance_framework_id")
    private Long complianceFrameworkId;

    @Column(name = "compliance_framework_clause", length = 100)
    private String complianceFrameworkClause;
}
