package com.tss.grc.module.sust.finance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.util.UUID;

/** G4 (plan §5.4 / REG-1) — one IFRS S1/S2 disclosure requirement with coverage status
 *  (COVERED | PARTIAL | GAP) — the SOCPA-readiness checklist row. */
@Entity
@Table(schema = "sust", name = "ifrs_s_requirements")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IfrsRequirement extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, updatable = false) private UUID externalId = UUID.randomUUID();
    @Column(name = "req_code", nullable = false, length = 30) private String reqCode;
    /** S1 | S2. */
    @Column(nullable = false, length = 5) private String standard;
    /** GOVERNANCE|STRATEGY|RISK_MGMT|METRICS_TARGETS. */
    @Column(nullable = false, length = 30) private String pillar;
    @Column(name = "title_en", nullable = false, length = 400) private String titleEn;
    @Column(name = "title_ar", length = 400) private String titleAr;
    /** COVERED | PARTIAL | GAP. */
    @Column(nullable = false, length = 10) private String status = "GAP";
    @Column(name = "coverage_ref", length = 300) private String coverageRef;
    @Column(name = "owner_name", length = 200) private String ownerName;
}
