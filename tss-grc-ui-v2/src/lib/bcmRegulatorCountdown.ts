// Saudi regulator countdown timer client — Slice 7-E (Sprint-7).
//
// Wraps GET /api/v1/bcm/regulator-countdowns and friends. For every
// active crisis event, returns the applicable Saudi regulator
// notification deadlines with traffic-light status.

import { api } from './api';

export type CountdownStatus =
  | 'ON_TRACK'
  | 'NEAR_DEADLINE'
  | 'BREACHED'
  | 'NOTIFIED'
  | 'NOT_REQUIRED';

export interface CountdownRow {
  crisisEventId: number;
  eventCode: string;
  titleEn: string;
  titleAr?: string;
  severity?: number;
  eventStatus?: string;

  regulator: string;
  windowHours: number;
  legalBasisEn: string;

  anchorAt?: string;
  deadlineAt?: string;
  evaluatedAt: string;
  hoursRemaining: number;
  minutesRemaining: number;

  status: CountdownStatus;

  notificationId?: number;
  notifiedAt?: string;
  referenceNumber?: string;
  notifiedLate: boolean;
}

export interface CountdownSummary {
  totalRows: number;
  breachedCount: number;
  nearDeadlineCount: number;
  onTrackCount: number;
  notifiedCount: number;
}

export async function regulatorCountdowns(): Promise<CountdownRow[]> {
  return api.get<CountdownRow[]>('/api/v1/bcm/regulator-countdowns');
}

export async function regulatorCountdownSummary(): Promise<CountdownSummary> {
  return api.get<CountdownSummary>('/api/v1/bcm/regulator-countdowns/summary');
}

export async function regulatorCountdownsForEvent(crisisEventId: number): Promise<CountdownRow[]> {
  return api.get<CountdownRow[]>(`/api/v1/bcm/regulator-countdowns/event/${crisisEventId}`);
}

/** Map a countdown status to a UI badge tone. */
export function countdownTone(status: CountdownStatus): 'success' | 'info' | 'warning' | 'danger' {
  switch (status) {
    case 'BREACHED': return 'danger';
    case 'NEAR_DEADLINE': return 'warning';
    case 'ON_TRACK': return 'info';
    case 'NOTIFIED': return 'success';
    default: return 'info';
  }
}

/** Human-readable regulator label with bilingual fallback. */
export function regulatorLabel(code: string, ar: boolean): string {
  const labels: Record<string, [string, string]> = {
    SAMA_CYBER: ['SAMA Cyber',              'ساما — السيبراني'],
    SAMA_BCF:   ['SAMA BCF',                'ساما — استمرارية الأعمال'],
    NCA:        ['NCA',                     'الهيئة الوطنية للأمن السيبراني'],
    SDAIA_PDPL: ['SDAIA PDPL Art.23',       'SDAIA — نظام حماية البيانات م.23'],
    NCEMA:      ['NCEMA',                   'المركز الوطني لإدارة الطوارئ'],
    NRC:        ['NRC',                     'هيئة الرقابة النووية والإشعاعية'],
    CMA:        ['CMA',                     'هيئة السوق المالية'],
    MOH:        ['MOH',                     'وزارة الصحة'],
  };
  const pair = labels[code];
  if (!pair) return code;
  return ar ? pair[1] : pair[0];
}
