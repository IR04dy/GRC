package com.tss.grc.module.platform.calendar.service;

import com.tss.grc.common.tenant.TenantContext;
import com.tss.grc.module.platform.auditlog.entity.AuditLogEntry;
import com.tss.grc.module.platform.auditlog.repository.AuditLogRepository;
import com.tss.grc.module.platform.auditlog.service.AuditLogService;
import com.tss.grc.module.platform.automation.event.DomainEvent;
import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import com.tss.grc.module.platform.calendar.repository.CalendarEventRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the F8 reminder scheduler. We mock the repos +
 * publisher and feed in a single tenant with a single event so the
 * scheduler's per-stop logic, idempotency check, and Saudi workday
 * shift are testable in isolation.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CalendarReminderSchedulerTest {

    @Mock CalendarEventRepository repo;
    @Mock AuditLogRepository auditRepo;
    @Mock AuditLogService audit;
    @Mock SaudiWorkdayCalendar workdays;
    @Mock ApplicationEventPublisher publisher;

    @InjectMocks CalendarReminderScheduler scheduler;

    private static final Long ORG = 1L;

    @BeforeEach
    void tenant() { TenantContext.setCurrentTenant(ORG); }

    @AfterEach
    void clear() { TenantContext.clear(); }

    /** Helper: build a calendar event with reminder ladder. */
    private CalendarEvent ev(Long id, LocalDate startsOn, String ladder, String eventType) {
        CalendarEvent e = new CalendarEvent();
        e.setId(id);
        e.setOrganisationId(ORG);
        e.setStartsAt(startsOn.atStartOfDay());
        e.setReminderDays(ladder);
        e.setEventType(eventType);
        e.setTitleEn("Test " + id);
        e.setTitleAr("اختبار " + id);
        return e;
    }

    @Test
    void parseLadderHandlesCommonShapes() {
        assertThat(CalendarReminderScheduler.parseLadder("7,1,0")).containsExactly(7, 1, 0);
        assertThat(CalendarReminderScheduler.parseLadder("")).isEmpty();
        assertThat(CalendarReminderScheduler.parseLadder(null)).isEmpty();
        assertThat(CalendarReminderScheduler.parseLadder("7, abc , 1")).containsExactly(7, 1);
        // Negative values dropped (we don't model "after" reminders).
        assertThat(CalendarReminderScheduler.parseLadder("7,-1,3")).containsExactly(7, 3);
    }

    @Test
    void firesWhenLadderStopLandsToday() {
        LocalDate today = LocalDate.of(2026, 6, 1);  // Monday — workday
        // Event in 7 days; ladder "7,1" → 7-day stop lands today,
        // 1-day stop lands on Sun 7 June (weekend, gets shifted back).
        CalendarEvent e = ev(101L, today.plusDays(7), "7,1", "CAPA_DUE");

        when(repo.findUpcomingWithReminders(eq(ORG), any(), any())).thenReturn(List.of(e));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "CalendarEvent", 101L)).thenReturn(List.of());
        // Default: every date is a workday. Specific weekend overrides below.
        when(workdays.isWorkday(eq(ORG), any(LocalDate.class))).thenReturn(true);
        // The 1-day stop's nominal is Sun 7 Jun — mark weekend so the
        // shift walks back to Thu 4 Jun (not today, so 1d stop won't fire).
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 7))).thenReturn(false); // Sun
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 6))).thenReturn(false); // Sat
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 5))).thenReturn(false); // Fri

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        DomainEvent de = cap.getValue();
        assertThat(de.triggerEvent()).isEqualTo("REMINDER_DUE");
        assertThat(de.entityId()).isEqualTo(101L);
        assertThat(de.payload()).containsEntry("daysBefore", 7);
        assertThat(de.payload()).containsEntry("eventType", "CAPA_DUE");
        assertThat(de.payload()).containsEntry("workdayShifted", false);
        verify(audit).log(eq("REMINDER_FIRED:7d"), eq("CalendarEvent"), eq(101L), anyString());
    }

    @Test
    void doesNotFireWhenAlreadyAudited() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        // Single-stop ladder so the 1-day stop's lateral fire path
        // isn't a test concern — only the 7-day stop is in scope.
        CalendarEvent e = ev(102L, today.plusDays(7), "7", "AUDIT_DAY");

        // Prior fire recorded.
        AuditLogEntry prior = new AuditLogEntry();
        prior.setActionType("REMINDER_FIRED:7d");
        prior.setEntityType("CalendarEvent");
        prior.setEntityId(102L);

        when(repo.findUpcomingWithReminders(eq(ORG), any(), any())).thenReturn(List.of(e));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "CalendarEvent", 102L)).thenReturn(List.of(prior));
        when(workdays.isWorkday(eq(ORG), any(LocalDate.class))).thenReturn(true);

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isZero();
        verify(publisher, never()).publishEvent(any());
        verify(audit, never()).log(anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void shiftsWeekendFireBackToWorkday() {
        // Event Sun 7 June; ladder "1" → nominal fire = Sat 6 June.
        // Saturday is Saudi weekend so the scheduler should shift the
        // fire back to Thursday 4 June. Today is the shifted date.
        LocalDate today = LocalDate.of(2026, 6, 4);   // Thu — workday
        LocalDate weekend = LocalDate.of(2026, 6, 6); // Sat — weekend
        CalendarEvent e = ev(103L, LocalDate.of(2026, 6, 7), "1", "MEETING");

        when(repo.findUpcomingWithReminders(eq(ORG), any(), any())).thenReturn(List.of(e));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "CalendarEvent", 103L)).thenReturn(List.of());
        // Saturday + Friday not workdays; Thursday is.
        when(workdays.isWorkday(ORG, weekend)).thenReturn(false);
        when(workdays.isWorkday(ORG, weekend.minusDays(1))).thenReturn(false);  // Fri
        when(workdays.isWorkday(ORG, today)).thenReturn(true);                  // Thu

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().payload()).containsEntry("workdayShifted", true);
        assertThat(cap.getValue().payload()).containsEntry("nominalFireDate", weekend.toString());
        assertThat(cap.getValue().payload()).containsEntry("actualFireDate", today.toString());
    }

    @Test
    void onlyFiresStopsWhoseShiftedDateIsToday() {
        LocalDate today = LocalDate.of(2026, 6, 1);  // Mon
        // Event Mon 8 June, ladder "7,1" → 7-day stop = 1 June (today),
        // 1-day stop = 7 June (Sun, weekend). Today the 7-day fires.
        // The 1-day stop should NOT fire today.
        CalendarEvent e = ev(104L, today.plusDays(7), "7,1", "REPORT_DEADLINE");

        when(repo.findUpcomingWithReminders(eq(ORG), any(), any())).thenReturn(List.of(e));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "CalendarEvent", 104L)).thenReturn(List.of());
        when(workdays.isWorkday(ORG, today)).thenReturn(true);
        // 1-day stop nominal date Sun 7 June, shifted to Thu 4 June.
        // Thu 4 != today (Mon 1) so it should not fire.
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 7))).thenReturn(false);
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 6))).thenReturn(false);
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 5))).thenReturn(false);
        when(workdays.isWorkday(ORG, LocalDate.of(2026, 6, 4))).thenReturn(true);

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isEqualTo(1);
        verify(publisher, times(1)).publishEvent(any(DomainEvent.class));
    }

    @Test
    void skipsEventsWithoutLadder() {
        LocalDate today = LocalDate.of(2026, 6, 1);
        // Empty repo result models the JPQL filter — events with null/
        // empty reminderDays don't surface.
        when(repo.findUpcomingWithReminders(eq(ORG), any(LocalDateTime.class), any(LocalDateTime.class)))
            .thenReturn(List.of());

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isZero();
        verify(publisher, never()).publishEvent(any());
    }

    @Test
    void firesPerStopIndependently() {
        // Ladder "3,1" — today the 1-day stop should fire (event tomorrow).
        // The 3-day stop's nominal date is two days ago. Confirm only
        // the matching stop fires.
        LocalDate today = LocalDate.of(2026, 6, 10);
        CalendarEvent e = ev(105L, today.plusDays(1), "3,1", "TRAINING");

        when(repo.findUpcomingWithReminders(eq(ORG), any(), any())).thenReturn(List.of(e));
        when(auditRepo.findByOrganisationIdAndEntityTypeAndEntityIdOrderByCreatedAtDesc(
            ORG, "CalendarEvent", 105L)).thenReturn(List.of());
        when(workdays.isWorkday(ORG, today)).thenReturn(true);
        when(workdays.isWorkday(ORG, today.minusDays(2))).thenReturn(true);  // 3d nominal

        int fired = scheduler.scanTenant(ORG, today);

        assertThat(fired).isEqualTo(1);
        ArgumentCaptor<DomainEvent> cap = ArgumentCaptor.forClass(DomainEvent.class);
        verify(publisher).publishEvent(cap.capture());
        assertThat(cap.getValue().payload()).containsEntry("daysBefore", 1);
    }
}
