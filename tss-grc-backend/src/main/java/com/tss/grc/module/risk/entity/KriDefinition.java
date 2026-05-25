package com.tss.grc.module.risk.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Key Risk Indicator definition — assessment §5.1 ERM #5.
 *
 * direction = HIGHER_IS_WORSE: green ≤ green_max < amber ≤ amber_max < red
 * direction = LOWER_IS_WORSE:  green ≥ green_max > amber ≥ amber_max > red
 */
// Qualified entity name avoids collision with the Sprint 13 KRI engine entity
// at com.tss.grc.module.risk.kri.entity.KriDefinition (PR #212). Two @Entity
// classes named KriDefinition in different packages share the same JPA entity
// name unless qualified.
@Entity(name = "LegacyKriDefinition")
@Table(schema = "risk", name = "kri_definitions")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class KriDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "risk_id")
    private Long riskId;

    @Column(name = "category_id")
    private Long categoryId;

    @Column(name = "code", length = 50)
    private String code;

    @Column(name = "name_en", nullable = false, length = 255)
    private String nameEn;

    @Column(name = "name_ar", length = 255)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    @Column(name = "unit", length = 40)
    private String unit;

    @Column(name = "direction", nullable = false, length = 20)
    private String direction = "HIGHER_IS_WORSE";

    @Column(name = "green_max", precision = 18, scale = 4)
    private BigDecimal greenMax;

    @Column(name = "amber_max", precision = 18, scale = 4)
    private BigDecimal amberMax;

    @Column(name = "target_value", precision = 18, scale = 4)
    private BigDecimal targetValue;

    @Column(name = "frequency", length = 40)
    private String frequency;

    @Column(name = "owner", length = 255)
    private String owner;

    @Column(name = "data_source", length = 500)
    private String dataSource;

    @Column(name = "framework", length = 100)
    private String framework;

    /** Typed FK to {@code compliance.frameworks} — IA-5 anchor. */
    @Column(name = "framework_id")
    private Long frameworkId;

    @Column(name = "clause_ref", length = 100)
    private String clauseRef;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
