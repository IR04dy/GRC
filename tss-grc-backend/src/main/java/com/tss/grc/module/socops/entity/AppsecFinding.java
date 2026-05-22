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
 * Application security finding — unified ingest destination for SAST,
 * DAST, IAST, SCA, secrets, and IaC findings from Veracode / Checkmarx /
 * Snyk / SonarQube / GitHub Advanced Security / Fortify / Semgrep.
 * Each row carries the severity that drives the NCA remediation SLA.
 */
@Entity
@Table(schema = "socops", name = "appsec_finding")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class AppsecFinding extends BaseEntity {

    @Column(name = "organisation_id", nullable = false) private Long organisationId;
    @Column(name = "external_id", nullable = false, length = 120) private String externalId;
    @Column(nullable = false, length = 40) private String scanner;
    @Column(name = "scan_type", nullable = false, length = 20) private String scanType;
    @Column(name = "application_name", nullable = false, length = 300) private String applicationName;
    @Column(name = "application_id") private Long applicationId;
    @Column(name = "repository_url", length = 500) private String repositoryUrl;
    @Column(length = 120) private String branch;
    @Column(name = "finding_title", nullable = false, length = 500) private String findingTitle;
    @Column(length = 40) private String cwe;
    @Column(length = 40) private String cve;
    @Column(nullable = false, length = 20) private String severity;
    @Column(name = "cvss_score", precision = 4, scale = 1) private BigDecimal cvssScore;
    @Column(name = "file_path", length = 500) private String filePath;
    @Column(name = "line_number") private Integer lineNumber;
    @Column(name = "component_name", length = 300) private String componentName;
    @Column(name = "component_version", length = 120) private String componentVersion;
    @Column(name = "fixed_version", length = 120) private String fixedVersion;
    @Column(columnDefinition = "NVARCHAR(MAX)") private String description;
    @Column(name = "remediation_advice", columnDefinition = "NVARCHAR(MAX)") private String remediationAdvice;
    @Column(nullable = false, length = 30) private String status = "OPEN";
    @Column(name = "assigned_to_user_id") private Long assignedToUserId;
    @Column(name = "discovered_at", nullable = false) private LocalDateTime discoveredAt;
    @Column(name = "fixed_at") private LocalDateTime fixedAt;
    @Column(name = "sla_due_at") private LocalDateTime slaDueAt;
    @Column(name = "suppression_reason", columnDefinition = "NVARCHAR(MAX)") private String suppressionReason;
}
