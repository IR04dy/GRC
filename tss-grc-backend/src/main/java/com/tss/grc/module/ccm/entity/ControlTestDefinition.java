package com.tss.grc.module.ccm.entity;

import com.tss.grc.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One CCM test — Sprint 11 Continuous Control Monitoring Engine.
 *
 * <p>A test definition is the "what to test, when, how often" half of the
 * CCM picture. Each row pairs:
 * <ul>
 *   <li>a {@code test_type} (LDAP_GROUP_MEMBERSHIP, MFA_ENROLLMENT_PERCENTAGE,
 *       SSL_CERT_EXPIRY, …) — the check shape</li>
 *   <li>a {@code target_system_type} (LDAP_AD, AZURE_AD, SPLUNK, CROWDSTRIKE,
 *       INTERNAL_DB, …) — where the data comes from</li>
 *   <li>a {@code target_config_json} — connection details with secrets
 *       referenced as {@code ${ENV_VAR}} placeholders, never embedded</li>
 *   <li>an {@code expected_condition_json} — the threshold the runner
 *       compares the live state against (e.g. {@code maxMembers: 5})</li>
 *   <li>a {@code frequency_cron} — Spring cron expression</li>
 * </ul>
 *
 * <p>Operator opt-in: {@link #isEnabled} defaults to {@code false}. The
 * runner only touches external systems for tests the operator has
 * explicitly turned on. The 10 seeded tests ship disabled.
 *
 * <p>{@link #consecutiveFailures} drives the auto-Issue creation logic:
 * three consecutive {@code FAIL} runs on a {@code CRITICAL}-severity
 * test opens a {@link com.tss.grc.module.platform.entity.Issue} so the
 * audit-committee dashboard surfaces it without operator action.
 */
@Entity
@Table(schema = "ccm", name = "control_test_definition")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ControlTestDefinition extends BaseEntity {

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "test_code", nullable = false, length = 64)
    private String testCode;

    @Column(name = "title_en", nullable = false, length = 255)
    private String titleEn;

    @Column(name = "title_ar", length = 255)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /** Soft FK to platform.controls.id — which unified control this test attests. */
    @Column(name = "control_id")
    private Long controlId;

    /**
     * LDAP_GROUP_MEMBERSHIP | AD_PRIVILEGED_USERS | MFA_ENROLLMENT_PERCENTAGE
     * | LOG_RETENTION_DAYS | NETWORK_ACL_RULE | SSL_CERT_EXPIRY
     * | PASSWORD_POLICY | INACTIVE_ACCOUNTS | SHARED_ACCOUNT_USAGE
     * | FILE_INTEGRITY | BACKUP_RECENCY | PATCH_LEVEL
     * | CUSTOM_SQL | CUSTOM_HTTP
     */
    @Column(name = "test_type", nullable = false, length = 40)
    private String testType;

    /**
     * LDAP_AD | AZURE_AD | SPLUNK | ELK | CROWDSTRIKE | TENABLE | SERVICENOW
     * | INTERNAL_DB | FILE_SYSTEM | CUSTOM
     */
    @Column(name = "target_system_type", nullable = false, length = 40)
    private String targetSystemType;

    @Column(name = "target_config_json", columnDefinition = "NVARCHAR(MAX)")
    private String targetConfigJson;

    @Column(name = "expected_condition_json", columnDefinition = "NVARCHAR(MAX)")
    private String expectedConditionJson;

    /** CRITICAL | HIGH | MEDIUM | LOW. */
    @Column(name = "severity", nullable = false, length = 20)
    private String severity = "MEDIUM";

    /** Spring 6-field cron expression, e.g. "0 0 6 * * *" for daily 06:00. */
    @Column(name = "frequency_cron", nullable = false, length = 40)
    private String frequencyCron;

    @Column(name = "timeout_seconds", nullable = false)
    private Integer timeoutSeconds = 60;

    /** Master enable flag — defaults to false (operator opts in per-test). */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled = Boolean.FALSE;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** PASS | FAIL | ERROR | SKIPPED | TIMEOUT — null if never run. */
    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    /** Reset to 0 on any non-FAIL status; incremented on FAIL/ERROR/TIMEOUT. */
    @Column(name = "consecutive_failures", nullable = false)
    private Integer consecutiveFailures = 0;
}
