// Periodic-filing scheduler — generates synthetic pending notifications
// for cadence-driven regulator submissions (NRC semi-annual, SAMA CSF
// annual, PDPL annual, AML annual, Nazaha integrity, plus internal
// quarterly Audit-Cte briefings).
//
// ServiceNow IRM equivalent: "Regulatory Reporting Calendar". Each
// PeriodicFiling carries a baseline "lastFiled" date plus a cadence; the
// generator emits a synthetic CommLogEntry pending row whose deadline =
// lastFiled + cadence. The Communications workspace merges these with
// the static COMMS_LOG_BY_RECORD so calendar-driven obligations show up
// alongside event-driven ones.

import type { CommLogEntry } from './notificationMatrix';
import { NOTIFICATION_MATRIX } from './notificationMatrix';

export type Cadence = 'QUARTERLY' | 'SEMI_ANNUAL' | 'ANNUAL';

export const CADENCE_DAYS: Record<Cadence, number> = {
  QUARTERLY:   90,
  SEMI_ANNUAL: 180,
  ANNUAL:      365,
};

export interface PeriodicFiling {
  /** Composite key — e.g. "PERIODIC:NRC-2026-H1". */
  recordKey: string;
  /** Joins to NotificationSpec.id. */
  specId: string;
  /** ISO date the previous instance was filed. The next deadline =
      lastFiled + CADENCE_DAYS[cadence]. */
  lastFiledAt: string;
  cadence: Cadence;
  titleEn: string;
  titleAr: string;
}

// ── Demo data ──────────────────────────────────────────────────────────────
//
// Mix of regulator filings + internal recurring obligations. lastFiledAt
// values are chosen so half show up as "due soon" (≤30d) and half as
// "comfortable" (>60d) on the workspace dashboard.

export const PERIODIC_FILINGS: PeriodicFiling[] = [
  // NRC semi-annual — H1 2026 due ~end of June 2026
  { recordKey: 'PERIODIC:NRC-2026-H1', specId: 'NRC-SEMI',
    lastFiledAt: '2025-12-28', cadence: 'SEMI_ANNUAL',
    titleEn: 'NRC national-risk register — H1 2026 filing',
    titleAr: 'السجل الوطني للمخاطر — تقرير النصف الأول 2026' },
  { recordKey: 'PERIODIC:NRC-2026-H2', specId: 'NRC-SEMI',
    lastFiledAt: '2026-06-30', cadence: 'SEMI_ANNUAL',
    titleEn: 'NRC national-risk register — H2 2026 filing',
    titleAr: 'السجل الوطني للمخاطر — تقرير النصف الثاني 2026' },

  // SAMA CSF annual — due ~Q1 2027 (last self-assessment Q1 2026)
  { recordKey: 'PERIODIC:SAMA-CSF-2026', specId: 'SAMA-CSF-ANNUAL',
    lastFiledAt: '2026-02-15', cadence: 'ANNUAL',
    titleEn: 'SAMA CSF annual self-assessment 2026',
    titleAr: 'التقييم الذاتي السنوي لإطار البنك المركزي 2026' },

  // PDPL annual report — due ~Q1 2027 (last submitted Q1 2026)
  { recordKey: 'PERIODIC:PDPL-2026', specId: 'PDPL-ANNUAL',
    lastFiledAt: '2026-03-31', cadence: 'ANNUAL',
    titleEn: 'PDPL annual compliance report 2026',
    titleAr: 'التقرير السنوي للامتثال لنظام حماية البيانات 2026' },

  // AML annual programme report to Board
  { recordKey: 'PERIODIC:AML-2026', specId: 'AML-ANNUAL-BOARD',
    lastFiledAt: '2026-01-20', cadence: 'ANNUAL',
    titleEn: 'AML programme annual report 2026 — Board',
    titleAr: 'التقرير السنوي لبرنامج مكافحة غسل الأموال 2026 — المجلس' },

  // Nazaha integrity report
  { recordKey: 'PERIODIC:NAZAHA-2026', specId: 'NAZAHA-INTEGRITY',
    lastFiledAt: '2026-02-08', cadence: 'ANNUAL',
    titleEn: 'Nazaha annual integrity report 2026',
    titleAr: 'التقرير السنوي للنزاهة 2026 — هيئة الرقابة' },

  // Internal — quarterly Audit Committee critical-finding briefing
  { recordKey: 'PERIODIC:AUDIT-CTE-Q2-2026', specId: 'AUDIT-CTE',
    lastFiledAt: '2026-03-15', cadence: 'QUARTERLY',
    titleEn: 'Quarterly Audit Committee briefing — Q2 2026',
    titleAr: 'الإحاطة الفصلية للجنة المراجعة — الربع الثاني 2026' },
  { recordKey: 'PERIODIC:AUDIT-CTE-Q1-2026', specId: 'AUDIT-CTE',
    lastFiledAt: '2025-12-15', cadence: 'QUARTERLY',
    titleEn: 'Quarterly Audit Committee briefing — Q1 2026',
    titleAr: 'الإحاطة الفصلية للجنة المراجعة — الربع الأول 2026' },
];

/**
 * Synthesise pending CommLogEntry rows for every periodic filing.
 *
 * The downstream SLA-clock logic computes deadline = eventDetectedAt +
 * spec.withinHours. To make that equal lastFiledAt + cadenceDays, we
 * backdate the synthetic eventDetectedAt to (lastFiled + cadence -
 * spec.withinWindow). E.g. NRC-SEMI has withinHours = 30d and cadence =
 * 180d → eventDetectedAt = lastFiled + 150d, so deadline lands at
 * lastFiled + 180d = correct next-filing date.
 */
export function generatePeriodicPending(): Array<{ recordKey: string; entry: CommLogEntry }> {
  return PERIODIC_FILINGS.map(f => {
    const spec = NOTIFICATION_MATRIX.find(s => s.id === f.specId);
    const cadenceMs = CADENCE_DAYS[f.cadence] * 86_400_000;
    const submissionWindowMs = (spec?.withinHours ?? 24) * 3_600_000;
    const lastFiledMs = new Date(f.lastFiledAt).getTime();
    // Backdate so deadline = lastFiled + cadence
    const detectedMs = lastFiledMs + cadenceMs - submissionWindowMs;
    const entry: CommLogEntry = {
      id: `sched-${f.recordKey}`,
      specId: f.specId,
      eventDetectedAt: new Date(detectedMs).toISOString(),
      state: 'PENDING',
      noteEn: `Scheduled — last filed ${f.lastFiledAt}, cadence ${f.cadence.replace(/_/g, ' ')}.`,
    };
    return { recordKey: f.recordKey, entry };
  });
}

/**
 * Look up the human-readable title for a periodic-filing recordKey.
 * Returns undefined for non-periodic record keys.
 */
export function findPeriodicFiling(recordKey: string): PeriodicFiling | undefined {
  return PERIODIC_FILINGS.find(f => f.recordKey === recordKey);
}
