package com.tss.grc.module.platform.calendar.dto;

import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Unified calendar-event DTO. Two flavours:
 *
 *   - {@code source = "USER"}    — round-trips from {@link CalendarEvent}
 *     (the persisted user-created + public-holiday rows).
 *   - {@code source = "AGGREGATED"} — synthetic rows the
 *     {@code CalendarService} builds on the fly from dated GRC
 *     entities (workflow submissions, CAPA tasks, risk attestations,
 *     finding remediations, AAR reviews, DR tests, DSR cases, report
 *     schedules). These have no persisted PK; they carry the source
 *     entity's {@code entityType + entityId} so the UI can navigate
 *     straight to its detail page.
 *
 * The frontend doesn't care which flavour a row is — both render
 * identically in the month grid + agenda. The {@code source} field
 * just lets the UI offer a "delete" affordance on USER rows and a
 * read-only chip on AGGREGATED rows.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CalendarEventDto {

    /** Persisted PK for USER rows; null for AGGREGATED rows. */
    private Long id;

    /** Stable identifier for client-side keying. AGGREGATED rows use
     *  a deterministic synthetic UUID from {@code entityType+entityId}. */
    private UUID externalId;

    // F2-184 — @NotBlank/@NotNull on source, titleEn, eventType and startsAt are gone for the same
    // reason F2-182 deleted the @Pattern below, and finding them is why F2-184 exists as its own
    // instrument: F2-182 fixed ONE field on this class by reading it, and left four. Only reflection
    // over controller parameters saw the rest. 🔑 Fixing the instance you noticed is not the same as
    // closing the shape, and a class you have already edited reads as one you have already checked.
    /** USER (persisted) | AGGREGATED (synthesised from a GRC entity). */
    private String source;

    private String titleEn;
    private String titleAr;
    private String descriptionEn;
    private String descriptionAr;

    private String eventType;

    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private Boolean allDay;

    private String location;
    private Long   ownerId;
    private String ownerLabel;   // resolved by the service for UI display

    private String severity;
    private String entityType;
    private Long   entityId;

    private String reminderDays;

    // F2-182 — a @Pattern(NONE|DAILY|…) used to sit here. This class is the OUTBOUND payload,
    // assembled by from(entity) below; bean validation runs only on a @Valid parameter, and no
    // endpoint binds this type. So the annotation validated nothing while reading like a guard, and
    // the inbound CreateRequest's own `recurrence` had none. Deleted rather than moved: an @Pattern
    // refuses 'monthly' where CalendarService.RECURRENCE normalises it, and a regex cannot be checked
    // against chk_calendar_recurrence the way a named ClosedVocabulary can.
    private String recurrence;

    private LocalDate recurrenceUntil;

    private Boolean isHoliday;
    private String  status;

    /** Workday hint for the frontend — true if the date is a Saudi
     *  business day (Sun-Thu and not a public holiday). The
     *  CalendarService stamps this server-side so the workday
     *  awareness is consistent with the F8 reminder scheduler. */
    private Boolean onWorkday;

    public static CalendarEventDto fromEntity(CalendarEvent e) {
        return CalendarEventDto.builder()
            .id(e.getId())
            .externalId(e.getExternalId())
            .source("USER")
            .titleEn(e.getTitleEn())
            .titleAr(e.getTitleAr())
            .descriptionEn(e.getDescriptionEn())
            .descriptionAr(e.getDescriptionAr())
            .eventType(e.getEventType())
            .startsAt(e.getStartsAt())
            .endsAt(e.getEndsAt())
            .allDay(e.getAllDay())
            .location(e.getLocation())
            .ownerId(e.getOwnerId())
            .severity(e.getSeverity())
            .entityType(e.getEntityType())
            .entityId(e.getEntityId())
            .reminderDays(e.getReminderDays())
            .recurrence(e.getRecurrence())
            .recurrenceUntil(e.getRecurrenceUntil())
            .isHoliday(e.getIsHoliday())
            .status(e.getStatus())
            .build();
    }

    /** Inbound payload from the create / update endpoints. */
    @Getter @Setter @NoArgsConstructor
    public static class CreateRequest {
        @NotBlank private String titleEn;
        private String titleAr;
        private String descriptionEn;
        private String descriptionAr;
        @NotBlank @Pattern(regexp = "AUDIT_DAY|REPORT_DEADLINE|WORKFLOW_DEADLINE|CAPA_DUE|RISK_ATTESTATION|FINDING_DEADLINE|AAR_REVIEW|DR_TEST|DSR_DEADLINE|MEETING|REMINDER|TRAINING|CUSTOM")
        private String eventType;
        @NotNull private LocalDateTime startsAt;
        private LocalDateTime endsAt;
        private Boolean allDay;
        private String location;
        private Long   ownerId;
        private String severity;
        private String entityType;
        private Long   entityId;
        private String reminderDays;
        private String recurrence;
        private LocalDate recurrenceUntil;
    }
}
