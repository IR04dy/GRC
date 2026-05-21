package com.tss.grc.module.platform.calendar.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Where;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Persisted calendar event — user-created reminders, meetings, training
 * days, plus seeded Saudi public holidays (is_holiday=1).
 *
 * <p>Auto-aggregated rows (workflow deadlines, CAPA due dates, etc.)
 * are <em>not</em> persisted here — the CalendarService stitches them
 * on the fly from their source tables so the calendar always reflects
 * the latest entity state. See V133 SQL migration for the rationale.
 *
 * <p>Saudi-workday awareness: the {@code is_holiday} column + a
 * code-level Sun-Thu workweek constant in {@code SaudiWorkdayCalendar}
 * together drive the {@code isWorkday()} / {@code nextWorkday()}
 * helpers used by deadline math and the F8 NOTIFY reminder scheduler.
 */
@Entity
@Table(schema = "platform", name = "calendar_events")
@Where(clause = "is_deleted = 0")
@Getter @Setter @NoArgsConstructor
public class CalendarEvent {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "organisation_id", nullable = false)
    private Long organisationId;

    @Column(name = "external_id", nullable = false, updatable = false)
    private UUID externalId = UUID.randomUUID();

    @Column(name = "title_en", nullable = false, length = 300)
    private String titleEn;

    @Column(name = "title_ar", length = 300)
    private String titleAr;

    @Column(name = "description_en", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionEn;

    @Column(name = "description_ar", columnDefinition = "NVARCHAR(MAX)")
    private String descriptionAr;

    /**
     * Free-form classification — driven by V133's CHECK constraint:
     * AUDIT_DAY | REPORT_DEADLINE | WORKFLOW_DEADLINE | CAPA_DUE |
     * RISK_ATTESTATION | FINDING_DEADLINE | AAR_REVIEW | DR_TEST |
     * DSR_DEADLINE | MEETING | REMINDER | TRAINING | PUBLIC_HOLIDAY |
     * CUSTOM.
     */
    @Column(name = "event_type", nullable = false, length = 40)
    private String eventType;

    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(name = "all_day", nullable = false)
    private Boolean allDay = Boolean.TRUE;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "owner_id")
    private Long ownerId;

    /** LOW | MEDIUM | HIGH | CRITICAL — optional. */
    @Column(name = "severity", length = 20)
    private String severity;

    /** Linked GRC entity (so the UI can navigate to its detail page). */
    @Column(name = "entity_type", length = 50)
    private String entityType;

    @Column(name = "entity_id")
    private Long entityId;

    /** Comma-separated days-before-start (e.g. "30,7,1"). */
    @Column(name = "reminder_days", length = 50)
    private String reminderDays;

    /** NONE | DAILY | WEEKLY | MONTHLY | QUARTERLY | ANNUAL. */
    @Column(name = "recurrence", nullable = false, length = 20)
    private String recurrence = "NONE";

    @Column(name = "recurrence_until")
    private java.time.LocalDate recurrenceUntil;

    /** Saudi public holidays flagged via the V134 seed. */
    @Column(name = "is_holiday", nullable = false)
    private Boolean isHoliday = Boolean.FALSE;

    /** SCHEDULED | COMPLETED | CANCELLED | SNOOZED. */
    @Column(name = "status", nullable = false, length = 20)
    private String status = "SCHEDULED";

    @Column(name = "is_deleted", nullable = false)
    private Boolean isDeleted = Boolean.FALSE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
