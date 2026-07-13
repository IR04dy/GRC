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

/** G4 (plan §5.7 / REG-10) — one EU CBAM export line: embedded-emissions data per
 *  in-scope product and reporting period. */
@Entity
@Table(schema = "sust", name = "cbam_lines")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CbamLine extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, updatable = false) private UUID externalId = UUID.randomUUID();
    /** ALUMINIUM|STEEL|CEMENT|FERTILISERS|HYDROGEN|ELECTRICITY. */
    @Column(name = "cn_group", nullable = false, length = 20) private String cnGroup;
    @Column(name = "product_desc_en", nullable = false, length = 300) private String productDescEn;
    @Column(name = "product_desc_ar", length = 300) private String productDescAr;
    @Column(name = "period_year", nullable = false) private Integer periodYear;
    @Column(name = "period_quarter", nullable = false) private Integer periodQuarter;
    @Column(name = "export_volume_tonnes", nullable = false, precision = 18, scale = 4) private BigDecimal exportVolumeTonnes;
    @Column(name = "embedded_tco2e_per_tonne", nullable = false, precision = 18, scale = 6) private BigDecimal embeddedTco2ePerTonne;
    @Column(name = "total_embedded_tco2e", nullable = false, precision = 18, scale = 4) private BigDecimal totalEmbeddedTco2e;
    @Column(name = "eu_importer", length = 200) private String euImporter;
    /** MEASURED | DEFAULT_VALUES. */
    @Column(name = "data_basis", nullable = false, length = 30) private String dataBasis = "MEASURED";
    /** GF-6 (V299) — audit trail of a derived factor: the GHG-inventory inputs
     *  (year, S1+S2 total, share %, tonnes) that produced embeddedTco2ePerTonne. */
    @Column(name = "data_basis_note", length = 500) private String dataBasisNote;
    /** DRAFT | REPORTED. */
    @Column(nullable = false, length = 15) private String status = "DRAFT";
}
