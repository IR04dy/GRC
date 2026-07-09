package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.util.UUID;

/** G4 (plan §5.5) — an org-wide ESG target with baseline→target trajectory; off-track
 *  actuals raise an automatic corrective action. */
@Entity
@Table(schema = "sust", name = "esg_targets")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class EsgTarget extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, updatable = false) private UUID externalId = UUID.randomUUID();
    @Column(name = "target_code", nullable = false, length = 40) private String targetCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    /** GHG_S1|GHG_S2|GHG_S3|ENERGY|WATER|WASTE|RENEWABLES_SHARE. */
    @Column(nullable = false, length = 30) private String scope;
    @Column(nullable = false, length = 40) private String unit;
    @Column(name = "baseline_year", nullable = false) private Integer baselineYear;
    @Column(name = "baseline_value", nullable = false, precision = 18, scale = 4) private BigDecimal baselineValue;
    @Column(name = "target_year", nullable = false) private Integer targetYear;
    @Column(name = "target_value", nullable = false, precision = 18, scale = 4) private BigDecimal targetValue;
    @Column(name = "sgi_aligned", nullable = false) private Boolean sgiAligned = false;
    /** ACTIVE|ACHIEVED|RETIRED. */
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
}
