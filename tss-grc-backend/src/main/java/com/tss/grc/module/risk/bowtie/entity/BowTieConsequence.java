package com.tss.grc.module.risk.bowtie.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * One consequence pathway on the RIGHT side of the bow-tie. A consequence
 * is "what happens if the top event materialises". Multiple mitigating
 * controls hang off it (the chain of barriers between top event and the
 * realised consequence).
 */
@Entity
@Table(schema = "risk", name = "bowtie_consequence")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class BowTieConsequence extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "bowtie_id", nullable = false)
    private Long bowtieId;

    @Column(name = "consequence_order", nullable = false)
    private Integer consequenceOrder = 0;

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** FINANCIAL/REGULATORY/REPUTATIONAL/OPERATIONAL/LEGAL/HUMAN_SAFETY/ENVIRONMENTAL. */
    @Column(name = "consequence_type", length = 40)
    private String consequenceType;

    /** INSIGNIFICANT/MINOR/MODERATE/MAJOR/CATASTROPHIC. */
    @Column(name = "inherent_severity", length = 20)
    private String inherentSeverity;

    @Column(name = "residual_severity", length = 20)
    private String residualSeverity;

    /** Optional — used when consequence_type = FINANCIAL. */
    @Column(name = "estimated_loss_sar", precision = 18, scale = 2)
    private BigDecimal estimatedLossSar;
}
