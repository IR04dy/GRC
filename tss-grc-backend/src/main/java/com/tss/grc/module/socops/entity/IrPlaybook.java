package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * Incident response runbook header. Each playbook is a named procedure the
 * SOC follows when a specific kind of event fires (phishing, ransomware,
 * BEC, DDoS, insider threat, BCP activation). Six templates ship in
 * V150 seed; tenants clone from the template (organisationId=0) on first
 * use, then version up as they iterate.
 */
@Entity
@Table(schema = "socops", name = "ir_playbook")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class IrPlaybook extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "playbook_code", nullable = false, length = 40) private String playbookCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)") private String descriptionEn;
    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)") private String descriptionAr;
    @Column(nullable = false, length = 60) private String category;
    @Column(name = "severity_trigger", length = 20) private String severityTrigger;
    @Column(name = "target_mttd_minutes") private Integer targetMttdMinutes;
    @Column(name = "target_mttr_minutes") private Integer targetMttrMinutes;
    @Column(name = "aligned_frameworks", length = 500) private String alignedFrameworks;
    @Column(nullable = false, length = 30) private String status = "ACTIVE";
    @Column(nullable = false) private Integer version = 1;
    @Column(name = "last_reviewed_at") private LocalDateTime lastReviewedAt;
    @Column(name = "last_reviewed_by", length = 255) private String lastReviewedBy;
    @Column(name = "invocation_count", nullable = false) private Integer invocationCount = 0;
}
