package com.tss.grc.module.risk.bowtie.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.util.UUID;

/**
 * A control barrier between a threat and the top event — left side of
 * the bow-tie. Reduces the likelihood that the threat pathway reaches
 * the top event.
 */
@Entity
@Table(schema = "risk", name = "bowtie_preventive_control")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BowTiePreventiveControl extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "bowtie_id", nullable = false)
    private Long bowtieId;

    @Column(name = "threat_id", nullable = false)
    private Long threatId;

    @Column(name = "control_order", nullable = false)
    private Integer controlOrder = 0;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** PREVENTIVE/DETECTIVE/CORRECTIVE/DIRECTIVE. */
    @Column(name = "control_type", length = 40)
    private String controlType;

    /** Soft FK into the unified control library (platform.controls). */
    @Column(name = "linked_control_id")
    private Long linkedControlId;

    /** VERY_LOW/LOW/MEDIUM/HIGH/VERY_HIGH — drives the overall-effectiveness score. */
    @Column(name = "effectiveness", nullable = false, length = 20)
    private String effectiveness = "MEDIUM";

    @Column(name = "last_tested_at")
    private LocalDate lastTestedAt;

    /** EFFECTIVE/PARTIALLY_EFFECTIVE/INEFFECTIVE/NOT_TESTED. */
    @Column(name = "last_test_result", length = 40)
    private String lastTestResult;

    @Column(name = "responsible_role", length = 120)
    private String responsibleRole;

    @Column(name = "responsible_owner", length = 255)
    private String responsibleOwner;
}
