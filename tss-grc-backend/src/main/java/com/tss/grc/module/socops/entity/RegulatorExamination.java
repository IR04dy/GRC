package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Regulator examination tracker — SAMA on-site exams, NCA inspections,
 * NDMO reviews, CITC sector audits, ZATCA, CCHI, CMA.
 *
 * <p>One row per examination engagement. Findings live in the existing
 * {@code compliance.audit_findings} table linked back to this row via
 * the framework + examination-code key.
 */
@Entity
@Table(schema = "socops", name = "regulator_examination")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class RegulatorExamination extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "examination_code", nullable = false, length = 40) private String examinationCode;
    @Column(nullable = false, length = 40) private String regulator;
    @Column(name = "examination_type", nullable = false, length = 60) private String examinationType;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "scope_areas", columnDefinition = "NVARCHAR(MAX)") private String scopeAreas;
    @Column(name = "lead_examiner_name", length = 255) private String leadExaminerName;
    @Column(name = "notified_at") private LocalDateTime notifiedAt;
    @Column(name = "fieldwork_start_at") private LocalDateTime fieldworkStartAt;
    @Column(name = "fieldwork_end_at") private LocalDateTime fieldworkEndAt;
    @Column(name = "draft_report_at") private LocalDateTime draftReportAt;
    @Column(name = "final_report_at") private LocalDateTime finalReportAt;
    @Column(name = "response_due_at") private LocalDateTime responseDueAt;
    @Column(name = "response_submitted_at") private LocalDateTime responseSubmittedAt;
    @Column(name = "closed_at") private LocalDateTime closedAt;
    @Column(nullable = false, length = 30) private String status = "NOTIFIED";
    @Column(name = "findings_total", nullable = false) private Integer findingsTotal = 0;
    @Column(name = "findings_critical", nullable = false) private Integer findingsCritical = 0;
    @Column(name = "findings_high", nullable = false) private Integer findingsHigh = 0;
    @Column(name = "findings_med_low", nullable = false) private Integer findingsMedLow = 0;
    @Column(name = "overall_rating", length = 40) private String overallRating;
    @Column(name = "related_framework", length = 120) private String relatedFramework;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String notes;
}
