package com.tss.grc.module.reporting.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A per-tenant scheduled report definition. Every minute the
 * {@code ReportSchedulerService} ticks, picks up rows whose {@code nextRunAt}
 * has elapsed, regenerates the PDF/XLSX via {@code ExportService}, attempts
 * email delivery via {@code JavaMailSender} (graceful no-op when SMTP is not
 * configured), persists a {@code ReportRun}, and recomputes {@code nextRunAt}.
 */
@Entity
@Table(schema = "reporting", name = "report_schedules")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class ReportSchedule {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // F2-286 — the column is insertable = false, so the DATABASE refuses a caller's value
    // and the create hands back the instance Jackson bound: without this the response
    // echoed a value the row does not carry, and only a re-fetch showed the truth. This
    // says the mapping refuses it, not that the caller should never state it — where the
    // caller should, the fix is to make the column insertable (F2-286 did that for
    // ethics.screening_hits.screened_at, whose value nothing could ever state).
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "external_id", insertable = false, updatable = false)
    private UUID externalId;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "name", nullable = false, length = 255)
    private String name;             // English (legacy column)

    @Column(name = "name_ar", length = 255)
    private String nameAr;           // Arabic (added V20)

    @Column(name = "description", columnDefinition = "NVARCHAR(MAX)")
    private String description;      // English (legacy column)

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;    // Arabic (added V20)

    @Column(name = "report_type", nullable = false, length = 40)
    private String reportType; // RISK_REGISTER | FINDINGS_AGING | KRI_DASHBOARD

    @Column(name = "format", nullable = false, length = 10)
    private String format = "PDF"; // PDF | XLSX

    @Column(name = "language", nullable = false, length = 5)
    private String language = "en"; // en | ar

    @Column(name = "cadence", nullable = false, length = 20)
    private String cadence = "WEEKLY"; // DAILY | WEEKLY | MONTHLY | QUARTERLY

    @Column(name = "cadence_anchor", length = 20)
    private String cadenceAnchor;
    // Day-of-week (MONDAY..SUNDAY) for WEEKLY, day-of-month for MONTHLY / QUARTERLY

    @Column(name = "time_of_day", nullable = false, length = 5)
    private String timeOfDay = "06:00"; // HH:mm UTC

    @Column(name = "recipients", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String recipients; // comma-separated

    @Column(name = "cc_recipients", columnDefinition = "NVARCHAR(MAX)")
    private String ccRecipients;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    @Column(name = "next_run_at", nullable = false)
    private LocalDateTime nextRunAt;

    @Column(name = "last_run_status", length = 20)
    private String lastRunStatus;

    @Column(name = "last_run_message", columnDefinition = "NVARCHAR(MAX)")
    private String lastRunMessage;

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
