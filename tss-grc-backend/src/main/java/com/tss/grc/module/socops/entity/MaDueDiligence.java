package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * M&A cyber due-diligence engagement. PIF-driven acquisitions are a
 * constant feature of the Saudi market — cyber DD has to be tracked as a
 * workflow, not a Word document. Each row is one target-entity engagement;
 * per-area findings link to {@code compliance.audit_findings}.
 */
@Entity
@Table(schema = "socops", name = "ma_due_diligence")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class MaDueDiligence extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "engagement_code", nullable = false, length = 80) private String engagementCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "target_entity_name", nullable = false, length = 300) private String targetEntityName;
    @Column(name = "target_industry", length = 120) private String targetIndustry;
    @Column(name = "target_country", length = 80) private String targetCountry;
    @Column(name = "deal_type", nullable = false, length = 40) private String dealType;
    @Column(nullable = false, length = 40) private String phase = "PRE_LOI";
    @Column(name = "deal_status", length = 40) private String dealStatus;
    @Column(name = "risk_areas_assessed", columnDefinition = "NVARCHAR(MAX)") private String riskAreasAssessed;
    @Column(name = "findings_total", nullable = false) private Integer findingsTotal = 0;
    @Column(name = "findings_critical", nullable = false) private Integer findingsCritical = 0;
    @Column(name = "findings_high", nullable = false) private Integer findingsHigh = 0;
    @Column(name = "price_adjustment_sar", precision = 18, scale = 2) private BigDecimal priceAdjustmentSar;
    @Column(name = "cyber_lead_name", length = 255) private String cyberLeadName;
    @Column(name = "legal_lead_name", length = 255) private String legalLeadName;
    @Column(name = "finance_lead_name", length = 255) private String financeLeadName;
    @Column(name = "loi_signed_at") private LocalDateTime loiSignedAt;
    @Column(name = "fieldwork_start_at") private LocalDateTime fieldworkStartAt;
    @Column(name = "fieldwork_end_at") private LocalDateTime fieldworkEndAt;
    @Column(name = "final_report_at") private LocalDateTime finalReportAt;
    @Column(name = "deal_close_at") private LocalDateTime dealCloseAt;
    @Column(name = "integration_complete_at") private LocalDateTime integrationCompleteAt;
    @Column(name = "cyber_recommendation", length = 40) private String cyberRecommendation;
    @Column(name = "recommendation_notes", columnDefinition = "NVARCHAR(MAX)") private String recommendationNotes;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
