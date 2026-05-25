package com.tss.grc.module.opres.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

/**
 * SAMA Op-Res 2024 catalog pillar — one of 5 framework dimensions
 * each assessment evaluates against the 5-level maturity ladder.
 *
 * <p>{@code organisation_id IS NULL} → shared catalog row for all
 * tenants; a tenant-specific row replaces it via cascade priority
 * (catalog row used unless the tenant added a custom pillar).
 */
@Entity
@Table(schema = "opres", name = "opres_pillar")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class OpResPillar extends BaseEntity {

    @Column(name = "organisation_id")
    private Long organisationId;

    /**
     * Stable code: {@code GOVERNANCE | IBS_MAPPING | TOLERANCES |
     * MAPPING_TESTING | LESSONS}.
     */
    @Column(name = "code", nullable = false, length = 32)
    private String code;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "name_en", nullable = false, length = 256)
    private String nameEn;

    @Column(name = "name_ar", nullable = false, length = 256)
    private String nameAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** SAMA Op-Res Framework 2024 reference (e.g. "§3.2"). */
    @Column(name = "sama_reference", length = 64)
    private String samaReference;

    /**
     * Maturity-ladder rubric — JSON array of 5 levels each with
     * {@code en} + {@code ar} bilingual descriptions of what
     * "good" looks like at that level. Stored as JSON so the UI
     * can render the rubric inline next to the level Select.
     */
    @Column(name = "maturity_rubric_json", columnDefinition = "NVARCHAR(MAX)")
    private String maturityRubricJson;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
