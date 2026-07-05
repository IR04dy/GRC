package com.tss.grc.module.socops.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;

/**
 * SOC-E — a detection-rule / use-case in the SOC's detection LIBRARY.
 *
 * <p>Records the SIEM/EDR/NDR content the SOC maintains and, crucially, how each rule maps
 * to the MITRE ATT&amp;CK framework ({@link #mitreTechniques} is required). This is what lets
 * the SOC reason about ATT&amp;CK coverage and lets a threat-intel action link an IOC to the
 * concrete detection that watches for it (see {@link ThreatIntelAction} target_type=DETECTION).
 */
@Entity
@Table(schema = "socops", name = "detection_rule")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class DetectionRule extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "rule_code", nullable = false, length = 60) private String ruleCode;
    @Column(name = "title_en", nullable = false, length = 300) private String titleEn;
    @Column(name = "title_ar", length = 300) private String titleAr;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String description;

    @Column(name = "detection_type", nullable = false, length = 30) private String detectionType;
    @Column(length = 80) private String platform;
    @Column(name = "log_source", length = 300) private String logSource;
    @Column(name = "detection_logic", columnDefinition = "NVARCHAR(MAX)") private String detectionLogic;

    @Column(name = "mitre_tactics", length = 300) private String mitreTactics;
    @Column(name = "mitre_techniques", nullable = false, length = 300) private String mitreTechniques;
    @Column(name = "data_sources", length = 300) private String dataSources;

    @Column(length = 20) private String severity;
    @Column(nullable = false, length = 20) private String status = "DRAFT";
    @Column(nullable = false) private Boolean enabled = true;
    @Column(name = "false_positive_rate", length = 20) private String falsePositiveRate;
    @Column(name = "aligned_frameworks", length = 300) private String alignedFrameworks;
    @Column(name = "last_triggered_at") private LocalDateTime lastTriggeredAt;
}
