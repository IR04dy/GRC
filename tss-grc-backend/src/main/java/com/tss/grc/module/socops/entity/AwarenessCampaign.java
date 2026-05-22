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
 * Security awareness campaign — phishing simulation, training push, workshop,
 * or tabletop exercise. NCA ECC 1-5-x requires a continuous programme; this
 * table is the evidence trail.
 */
@Entity
@Table(schema = "socops", name = "awareness_campaign")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AwarenessCampaign extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "campaign_code", nullable = false, length = 40) private String campaignCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "campaign_type", nullable = false, length = 40) private String campaignType;
    @Column(name = "target_audience", length = 500) private String targetAudience;
    @Column(name = "target_user_count", nullable = false) private Integer targetUserCount = 0;
    @Column(name = "started_at") private LocalDateTime startedAt;
    @Column(name = "ended_at") private LocalDateTime endedAt;
    @Column(nullable = false, length = 30) private String status = "PLANNED";
    @Column(name = "delivered_count", nullable = false) private Integer deliveredCount = 0;
    @Column(name = "opened_count", nullable = false) private Integer openedCount = 0;
    @Column(name = "clicked_count", nullable = false) private Integer clickedCount = 0;
    @Column(name = "reported_count", nullable = false) private Integer reportedCount = 0;
    @Column(name = "completed_count", nullable = false) private Integer completedCount = 0;
    @Column(name = "repeat_offender_count", nullable = false) private Integer repeatOffenderCount = 0;
    @Column(name = "pass_rate_percent", precision = 5, scale = 2) private BigDecimal passRatePercent;
    @Column(name = "aligned_controls", length = 500) private String alignedControls;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
