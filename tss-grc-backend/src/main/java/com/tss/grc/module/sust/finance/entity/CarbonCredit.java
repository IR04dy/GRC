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
import java.time.LocalDate;
import java.util.UUID;

/**
 * Green-Finance uplift G3 (plan §5.3 / REG-11) — one voluntary carbon-credit lot
 * (e.g. RVCMC auction purchase). Retirement requires a registry certificate
 * reference and a stated claim purpose — enforced in the service — so offsets are
 * only ever disclosed alongside gross reductions (integrity per ICVCM practice).
 */
@Entity
@Table(schema = "sust", name = "carbon_credits")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CarbonCredit extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(nullable = false, length = 80)
    private String registry;

    @Column(name = "serial_range", nullable = false, length = 200)
    private String serialRange;

    @Column(name = "vintage_year", nullable = false)
    private Integer vintageYear;

    @Column(name = "project_type", length = 80)
    private String projectType;

    @Column(name = "project_country", length = 60)
    private String projectCountry;

    @Column(name = "quantity_tco2e", nullable = false, precision = 18, scale = 4)
    private BigDecimal quantityTco2e;

    @Column(name = "price_sar_per_t", precision = 18, scale = 4)
    private BigDecimal priceSarPerT;

    /** PURCHASED | RETIRED. */
    @Column(nullable = false, length = 15)
    private String status = "PURCHASED";

    @Column(name = "retirement_date")
    private LocalDate retirementDate;

    @Column(name = "retirement_purpose", length = 300)
    private String retirementPurpose;

    @Column(name = "certificate_ref", length = 200)
    private String certificateRef;

    /** GF-2 (V298) — registry-certificate evidence item (S-C bridge). Retirement
     *  keeps accepting the legacy {@code certificateRef} text but prefers this FK;
     *  the lineage view treats either as evidence. */
    @Column(name = "evidence_item_id")
    private Long evidenceItemId;
}
