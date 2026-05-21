/**
 * Saudi-workday math + Hijri date helpers for the Compliance Calendar.
 *
 * <p>Mirrors the server-side {@code SaudiWorkdayCalendar} (PR #60) — the
 * server is the source of truth for deadline math because the F8
 * reminder scheduler depends on identical results. The client uses
 * these utilities only for instant UX feedback (greying out weekend
 * cells in the month grid, rendering Hijri dual-dates, filtering
 * "weekend" event chips).
 *
 * <p>Public-holiday awareness comes from the API: every event the
 * backend returns carries an {@code onWorkday} flag the server has
 * already computed. The client should NOT re-derive workday-ness
 * from a hard-coded holiday list — that would drift the moment the
 * Council of Senior Scholars confirms an Eid date.
 */

/** True if {@code date}'s day-of-week is Friday or Saturday — i.e. the
 *  Saudi weekend. Doesn't consult holidays; combine with the API's
 *  {@code onWorkday} flag for full coverage. JS Date.getDay() returns
 *  0=Sun ... 6=Sat — Friday=5 and Saturday=6 are the weekend. */
export function isSaudiWeekend(date: Date): boolean {
  const d = date.getDay();
  return d === 5 || d === 6;
}

/** Days-of-week as ISO indices for the Saudi week start (Sunday).
 *  Returns [Sun, Mon, Tue, Wed, Thu, Fri, Sat] so the calendar grid
 *  matches the cultural expectation. */
export const SAUDI_WEEK_DAYS = [0, 1, 2, 3, 4, 5, 6] as const; // Sun-first

/** Bilingual short labels for the day-of-week header row. */
export function dowShort(d: number, isAr: boolean): string {
  const EN = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'];
  const AR = ['الأحد', 'الإثنين', 'الثلاثاء', 'الأربعاء', 'الخميس', 'الجمعة', 'السبت'];
  return (isAr ? AR : EN)[d] ?? '';
}

/** Hijri (Umm Al-Qura) date as a localised string. Modern browsers
 *  ship the {@code islamic-umalqura} calendar with Intl; we lean on
 *  it rather than shipping a table. Fallback to the Gregorian
 *  representation if the runtime lacks support (very old Safari). */
export function hijriDate(date: Date, isAr: boolean): string {
  try {
    const locale = isAr ? 'ar-SA-u-ca-islamic-umalqura' : 'en-u-ca-islamic-umalqura';
    return new Intl.DateTimeFormat(locale, {
      day: 'numeric', month: 'short', year: 'numeric',
    }).format(date);
  } catch {
    return '';
  }
}

/** Short Hijri day-of-month only — for compact display under a
 *  Gregorian cell in the month grid. */
export function hijriDayOnly(date: Date): string {
  try {
    return new Intl.DateTimeFormat('en-u-ca-islamic-umalqura', { day: 'numeric' })
      .format(date);
  } catch {
    return '';
  }
}

/** Color-coded tone per event type, used by the calendar chips +
 *  agenda rows. Keep in sync with the backend's V133 CHECK constraint. */
export type CalendarEventType =
  | 'AUDIT_DAY' | 'REPORT_DEADLINE' | 'WORKFLOW_DEADLINE'
  | 'CAPA_DUE'  | 'RISK_ATTESTATION' | 'FINDING_DEADLINE'
  | 'AAR_REVIEW' | 'DR_TEST' | 'DSR_DEADLINE'
  | 'MEETING'   | 'REMINDER' | 'TRAINING'
  | 'PUBLIC_HOLIDAY' | 'CUSTOM';

export const EVENT_TYPE_TONE: Record<CalendarEventType,
  'primary' | 'info' | 'warning' | 'danger' | 'success' | 'accent' | 'neutral'> = {
  AUDIT_DAY:         'accent',
  REPORT_DEADLINE:   'warning',
  WORKFLOW_DEADLINE: 'primary',
  CAPA_DUE:          'danger',
  RISK_ATTESTATION:  'warning',
  FINDING_DEADLINE:  'danger',
  AAR_REVIEW:        'info',
  DR_TEST:           'info',
  DSR_DEADLINE:      'danger',
  MEETING:           'primary',
  REMINDER:          'neutral',
  TRAINING:          'success',
  PUBLIC_HOLIDAY:    'accent',
  CUSTOM:            'neutral',
};

/** Bilingual event-type labels for filters + tooltips. */
export const EVENT_TYPE_LABEL: Record<CalendarEventType, { en: string; ar: string }> = {
  AUDIT_DAY:         { en: 'Audit day',         ar: 'يوم تدقيق' },
  REPORT_DEADLINE:   { en: 'Report deadline',   ar: 'استحقاق تقرير' },
  WORKFLOW_DEADLINE: { en: 'Workflow due',      ar: 'استحقاق مهمة' },
  CAPA_DUE:          { en: 'CAPA due',          ar: 'استحقاق إجراء' },
  RISK_ATTESTATION:  { en: 'Risk attestation',  ar: 'توكيد خطر' },
  FINDING_DEADLINE:  { en: 'Finding deadline',  ar: 'استحقاق نتيجة' },
  AAR_REVIEW:        { en: 'Review meeting',    ar: 'اجتماع مراجعة' },
  DR_TEST:           { en: 'DR exercise',       ar: 'اختبار تعافي' },
  DSR_DEADLINE:      { en: 'PDPL DSR deadline', ar: 'استحقاق طلب بيانات' },
  MEETING:           { en: 'Meeting',           ar: 'اجتماع' },
  REMINDER:          { en: 'Reminder',          ar: 'تذكير' },
  TRAINING:          { en: 'Training',          ar: 'تدريب' },
  PUBLIC_HOLIDAY:    { en: 'Public holiday',    ar: 'إجازة رسمية' },
  CUSTOM:            { en: 'Custom',            ar: 'مخصص' },
};

/** Build the 6×7 grid of dates for a month view. Starts on the
 *  Sunday before/at the 1st of the target month and runs 42 cells
 *  forward — matches the Outlook layout. */
export function buildMonthGrid(year: number, monthZeroIndexed: number): Date[] {
  const first = new Date(year, monthZeroIndexed, 1);
  // Saudi week starts Sunday; JS getDay() Sun=0 — perfect.
  const offset = first.getDay();   // 0 if 1st is Sunday
  const start = new Date(year, monthZeroIndexed, 1 - offset);
  const cells: Date[] = [];
  for (let i = 0; i < 42; i++) {
    cells.push(new Date(start.getFullYear(), start.getMonth(), start.getDate() + i));
  }
  return cells;
}

/** Returns true if two dates share the same Y/M/D. */
export function isSameDay(a: Date, b: Date): boolean {
  return a.getFullYear() === b.getFullYear()
      && a.getMonth() === b.getMonth()
      && a.getDate() === b.getDate();
}

/** Day-only ISO date (YYYY-MM-DD) without timezone shift. */
export function toIsoDate(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${y}-${m}-${day}`;
}

/** Bilingual month-year header. */
export function monthYearLabel(year: number, monthZeroIndexed: number, isAr: boolean): string {
  const d = new Date(year, monthZeroIndexed, 1);
  return new Intl.DateTimeFormat(isAr ? 'ar-SA' : 'en-US', {
    month: 'long', year: 'numeric',
  }).format(d);
}
