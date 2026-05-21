package com.tss.grc.module.platform.calendar.repository;

import com.tss.grc.module.platform.calendar.entity.CalendarEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CalendarEventRepository extends JpaRepository<CalendarEvent, Long> {

    Optional<CalendarEvent> findByOrganisationIdAndExternalId(Long organisationId, UUID externalId);

    /** Live events in window. Includes holidays — UI filters out
     *  by event_type if a buyer prefers a holiday-free view. */
    @Query("""
        SELECT e FROM CalendarEvent e
        WHERE e.organisationId = :orgId
          AND e.startsAt >= :from
          AND e.startsAt <  :to
        ORDER BY e.startsAt ASC, e.id ASC
        """)
    List<CalendarEvent> findInWindow(@Param("orgId") Long orgId,
                                     @Param("from")  LocalDateTime from,
                                     @Param("to")    LocalDateTime to);

    /** Saudi public holidays for the tenant — used by the workday
     *  helper {@code SaudiWorkdayCalendar.isWorkday()} so the
     *  reminder scheduler knows when to push a Friday/Saturday hit
     *  forward to Sunday. */
    @Query("""
        SELECT e FROM CalendarEvent e
        WHERE e.organisationId = :orgId
          AND e.isHoliday = true
          AND e.startsAt >= :from
          AND e.startsAt <  :to
        ORDER BY e.startsAt ASC
        """)
    List<CalendarEvent> findHolidaysInWindow(@Param("orgId") Long orgId,
                                             @Param("from")  LocalDateTime from,
                                             @Param("to")    LocalDateTime to);

    /** Reverse lookup — "On the calendar" panel on entity detail pages. */
    List<CalendarEvent> findByOrganisationIdAndEntityTypeAndEntityIdOrderByStartsAtAsc(
        Long organisationId, String entityType, Long entityId);

    /** Upcoming events (window starts ≥ from, < to) that have a
     *  non-empty reminder_days ladder. Used by the F8 reminder
     *  scheduler so it doesn't scan the whole table every tick. */
    @Query("""
        SELECT e FROM CalendarEvent e
        WHERE e.organisationId = :orgId
          AND e.startsAt >= :from
          AND e.startsAt <  :to
          AND e.reminderDays IS NOT NULL
          AND e.reminderDays <> ''
          AND (e.status IS NULL OR e.status <> 'CANCELLED')
        ORDER BY e.startsAt ASC
        """)
    List<CalendarEvent> findUpcomingWithReminders(@Param("orgId") Long orgId,
                                                  @Param("from")  LocalDateTime from,
                                                  @Param("to")    LocalDateTime to);

    /** Distinct tenants that own at least one calendar event — used
     *  by the cross-tenant reminder sweep to scope itself. */
    @Query("SELECT DISTINCT e.organisationId FROM CalendarEvent e")
    List<Long> findDistinctOrganisationIds();
}
