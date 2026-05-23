package com.tss.grc.module.risk.bowtie.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.util.UUID;

/**
 * One threat / hazard pathway on the LEFT side of the bow-tie. A threat
 * is "what could cause the top event". Multiple preventive controls hang
 * off it (the chain of barriers between threat and top event).
 */
@Entity
@Table(schema = "risk", name = "bowtie_threat")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BowTieThreat extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "bowtie_id", nullable = false)
    private Long bowtieId;

    @Column(name = "threat_order", nullable = false)
    private Integer threatOrder = 0;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** RARE/UNLIKELY/POSSIBLE/LIKELY/ALMOST_CERTAIN. */
    @Column(name = "inherent_likelihood", length = 20)
    private String inherentLikelihood;

    @Column(name = "residual_likelihood", length = 20)
    private String residualLikelihood;

    /** Free-form source — e.g. "External actor", "Insider", "Third party", "Natural event". */
    @Column(name = "source", length = 120)
    private String source;
}
