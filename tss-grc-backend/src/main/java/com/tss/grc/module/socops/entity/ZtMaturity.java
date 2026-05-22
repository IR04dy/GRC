package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Zero Trust maturity assessment — one row per pillar per assessment cycle.
 * Pillars follow the CISA Zero Trust Maturity Model: IDENTITY, DEVICES,
 * NETWORK, APPLICATIONS, DATA, plus the cross-cutting VISIBILITY and
 * AUTOMATION dimensions. Stage names mirror CISA's
 * TRADITIONAL / INITIAL / ADVANCED / OPTIMAL ladder.
 */
@Entity
@Table(schema = "socops", name = "zt_maturity")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ZtMaturity extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "assessment_code", nullable = false, length = 40) private String assessmentCode;
    @Column(nullable = false, length = 40) private String pillar;
    @Column(name = "current_stage", nullable = false, length = 20) private String currentStage;
    @Column(name = "target_stage", length = 20) private String targetStage;
    private Integer score;
    @Column(name = "evidence_summary", columnDefinition = "NVARCHAR(MAX)") private String evidenceSummary;
    @Column(name = "gap_summary", columnDefinition = "NVARCHAR(MAX)") private String gapSummary;
    @Column(name = "action_plan", columnDefinition = "NVARCHAR(MAX)") private String actionPlan;
    @Column(name = "assessed_at") private LocalDateTime assessedAt;
    @Column(name = "assessed_by", length = 255) private String assessedBy;
}
