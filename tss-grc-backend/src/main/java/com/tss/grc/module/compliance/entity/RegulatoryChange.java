package com.tss.grc.module.compliance.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Regulatory change tracking — assessment §5.3 critical recommendation #1.
 * State machine: NEW → APPLICABILITY_ASSESSED → IMPACT_ASSESSED → CONTROLS_UPDATED → CLOSED
 *                NEW → NOT_APPLICABLE (terminal)
 */
@Entity(name = "ComplianceRegulatoryChange")
@Table(schema = "compliance", name = "regulatory_changes")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegulatoryChange extends BaseEntity {

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    /** Issuing authority — NCA, SDAIA, SAMA, CMA, ZATCA, CITC, NCEMA, MoCI, HRSD, MoH, MOI, OTHER */
    @Column(name = "source_authority", nullable = false, length = 50)
    private String sourceAuthority;

    @Column(name = "title_en", nullable = false, length = 500)
    private String titleEn;

    @Column(name = "title_ar", length = 500)
    private String titleAr;

    /** Citation reference, e.g. "PDPL Implementing Regulations Article 12" */
    @Column(name = "citation", length = 255)
    private String citation;

    @Column(name = "summary_en", columnDefinition = "NVARCHAR(MAX)")
    private String summaryEn;

    @Column(name = "summary_ar", columnDefinition = "NVARCHAR(MAX)")
    private String summaryAr;

    @Column(name = "published_date")
    private LocalDate publishedDate;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    /** UNKNOWN | APPLICABLE | NOT_APPLICABLE | PARTIAL */
    @Column(name = "applicability", nullable = false, length = 30)
    private String applicability = "UNKNOWN";

    /** UNKNOWN | LOW | MEDIUM | HIGH | CRITICAL */
    @Column(name = "impact_level", nullable = false, length = 20)
    private String impactLevel = "UNKNOWN";

    /** NEW | APPLICABILITY_ASSESSED | IMPACT_ASSESSED | CONTROLS_UPDATED | CLOSED | NOT_APPLICABLE */
    @Column(name = "status", nullable = false, length = 40)
    private String status = "NEW";

    @Column(name = "impact_summary", columnDefinition = "NVARCHAR(MAX)")
    private String impactSummary;

    @Column(name = "controls_updated_count", nullable = false)
    private Integer controlsUpdatedCount = 0;

    @Column(name = "assigned_to_id")
    private Long assignedToId;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;
}
