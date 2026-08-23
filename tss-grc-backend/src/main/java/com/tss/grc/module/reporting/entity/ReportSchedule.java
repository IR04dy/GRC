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

    // ⚠⚠ F2-293 removed `= "PDF"`, `= "en"`, `= "WEEKLY"` and `= "06:00"` from the four fields
    // below, and `= true` from `isActive`. `ReportScheduleController` binds this entity on POST and
    // PUT, so Jackson deserialised an omitted key into the initialiser, every
    // `if (s.getX() != null)` in `ReportScheduleService#apply` was true on EVERY request, and a
    // partial PUT rewrote all five. `ReportScheduleService#create` owns the defaults now.
    //
    // ⚠⚠ SCREEN-REACHABLE BY A ONE-CLICK CONTROL, which is what separates this from F2-291 and
    // F2-292. Both of those were API-reachable only, because the edit form echoed the whole DTO
    // back — and this page's edit form does too (`reqFromDto` spreads `{...s}`). But
    // `ReportSchedulesPage#togglePause` sends `{ isActive: !s.isActive }` and nothing else, so
    // clicking Pause or Resume on a scheduled regulatory report rewrote its format, language,
    // cadence and delivery time. Probed live: XLSX/ar/QUARTERLY/22:00 became PDF/en/WEEKLY/06:00.
    // 🔑 F2-283's screen-vs-API distinction is per CALL SITE, not per page: one page held an
    // echoing edit modal and a one-field toggle, and only reading BOTH answers the question.
    //
    // Measured identical in dev and prod, over 11 live rows: cadence differs from its initialiser
    // on 10, language on 9 (nine reports are Arabic — house rule 7), time_of_day on 9, format on 1.
    // `isActive` differs on 0, because the toggle is the caller that always sends it — so the field
    // the filing called sharpest was the one field this defect could not reach, and the four it
    // listed as collateral were the live ones.
    @Column(name = "format", nullable = false, length = 10)
    private String format; // PDF | XLSX  (see ReportSchedulerService#renderBody)

    @Column(name = "language", nullable = false, length = 5)
    private String language; // en | ar

    @Column(name = "cadence", nullable = false, length = 20)
    private String cadence; // ReportSchedulerService.SUPPORTED_CADENCES

    @Column(name = "cadence_anchor", length = 20)
    private String cadenceAnchor;
    // Day-of-week (MONDAY..SUNDAY) for WEEKLY, day-of-month for MONTHLY / QUARTERLY / SEMIANNUAL.
    // ⚠ This field has NO initialiser and never had one, which is why it survived the toggle — it
    // is the control that shows the reset was the initialisers and not the null guards.

    @Column(name = "time_of_day", nullable = false, length = 5)
    private String timeOfDay; // HH:mm UTC

    @Column(name = "recipients", nullable = false, columnDefinition = "NVARCHAR(MAX)")
    private String recipients; // comma-separated

    @Column(name = "cc_recipients", columnDefinition = "NVARCHAR(MAX)")
    private String ccRecipients;

    // ⚠ F2-293 removed `= true`. LATENT via the frontend — `togglePause` is the one caller that
    // always sends this field, and the create form seeds it — but a machine caller sending any
    // partial body (which is what the client's own `Partial<ReportScheduleDto>` invites) RESUMED a
    // paused report: probed live, `PUT {"cadence":"MONTHLY"}` took is_active from false to true.
    // 🔑 A reset that restarts delivery MANUFACTURES an assurance rather than losing a fact
    // (F2-240's rule), so it is closed even though no live row differs from the initialiser.
    @Column(name = "is_active", nullable = false)
    private Boolean isActive;

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
