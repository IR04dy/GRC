// PDPL 72-hour breach response engine — Sprint 4.
//
// Saudi PDPL (Personal Data Protection Law) Article 25 + Implementing
// Regulations require notification to SDAIA "without undue delay and within
// no later than 72 hours of becoming aware" of a personal-data breach,
// and notification to affected data subjects "without undue delay" where
// the breach is likely to result in high risk to their rights.
//
// This module:
//   1. Splits the 72-hour budget into stage sub-budgets so the response
//      team sees micro-deadlines, not just the macro one.
//   2. Generates a draft SDAIA notification packet with every Article 25
//      field populated, ready for DPO review and dispatch.
//   3. Provides a separate 7-day window for affected-subjects
//      notification when severity ≥ HIGH.

import type { PrivacyBreachDto } from './api';

// ─── Stage budget ───────────────────────────────────────────────────────────

export const PDPL_BREACH_STAGES = [
  { code: 'DETECTED',                hoursBudget: 0,   labelEn: 'Detected',                  labelAr: 'الكشف' },
  { code: 'ASSESSED',                hoursBudget: 24,  labelEn: 'Severity & scope assessed', labelAr: 'تقييم الخطورة والنطاق' },
  { code: 'CONTAINED',               hoursBudget: 24,  labelEn: 'Contained',                 labelAr: 'احتواء' },
  { code: 'NOTIFIED_DPA',            hoursBudget: 24,  labelEn: 'SDAIA notified',            labelAr: 'إبلاغ سدايا' },
  { code: 'NOTIFIED_DATA_SUBJECTS',  hoursBudget: 144, labelEn: 'Data subjects notified',    labelAr: 'إبلاغ أصحاب البيانات' }, // 7 days from SDAIA notify
  { code: 'CLOSED',                  hoursBudget: 0,   labelEn: 'Closed',                    labelAr: 'إغلاق' },
] as const;

export type PdplStageCode = typeof PDPL_BREACH_STAGES[number]['code'];

/** Total cumulative budget to a given stage (in hours from detection). */
export function cumulativeBudgetTo(stage: PdplStageCode): number {
  let sum = 0;
  for (const s of PDPL_BREACH_STAGES) {
    sum += s.hoursBudget;
    if (s.code === stage) return sum;
  }
  return sum;
}

// PDPL Article 25 hard deadlines — referenced by the console UI.
export const SDAIA_DEADLINE_HOURS = 72;       // notify regulator
export const DATA_SUBJECTS_DEADLINE_HOURS = 168; // 7 days; "without undue delay"

// ─── Severity rubric ────────────────────────────────────────────────────────
//
// PDPL doesn't enumerate severity bands explicitly; it asks the controller
// to assess "the likelihood and severity of risk to data subjects' rights
// and freedoms". This rubric encodes the practical SDAIA test that listed
// entities follow:
//   CRITICAL — special-category data OR ≥ 100k subjects OR identity data
//              suitable for impersonation
//   HIGH     — financial / health / location data OR 10k–100k subjects
//   MEDIUM   — contact data only OR 1k–10k subjects
//   LOW      — minimal data, < 1k subjects, encrypted at rest

export type PdplSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface PdplSeverityInputs {
  affectedRecords?: number;
  /** Boolean flags for special-category, financial, health, or
   *  identification-quality data. */
  specialCategory?: boolean;
  financial?: boolean;
  health?: boolean;
  identifying?: boolean;
  /** Whether the data was encrypted at rest. */
  encryptedAtRest?: boolean;
}

export function classifySeverity(b: PdplSeverityInputs): PdplSeverity {
  const n = b.affectedRecords ?? 0;
  if (b.specialCategory || b.identifying || n >= 100_000) return 'CRITICAL';
  if (b.financial || b.health || n >= 10_000) return 'HIGH';
  if (n >= 1_000 && !b.encryptedAtRest) return 'MEDIUM';
  return 'LOW';
}

/** Whether this severity requires data-subject notification (PDPL Art. 26). */
export function requiresDataSubjectNotification(severity: PdplSeverity): boolean {
  return severity === 'HIGH' || severity === 'CRITICAL';
}

// ─── SDAIA notification packet ──────────────────────────────────────────────
//
// Mirrors the fields SDAIA's online notification form expects (PDPL IR
// Art. 25). Generating this from the breach record gives the DPO a draft
// to review and dispatch rather than a blank form.

export interface SdaiaNotificationPacket {
  /** Internal reference — populates SDAIA submission ID once dispatched. */
  internalRef: string;
  /** Reporting controller details. */
  controller: {
    nameEn: string;
    nameAr: string;
    crNumber?: string;
    dpoNameEn: string;
    dpoNameAr: string;
    dpoEmail: string;
    dpoPhone: string;
  };
  /** Article 25(a) — nature of the breach. */
  natureOfBreach: { en: string; ar: string };
  /** Article 25(b) — categories and approximate count. */
  categoriesAffected: string[];
  approximateSubjects: number;
  approximateRecords: number;
  /** Article 25(c) — likely consequences. */
  likelyConsequences: { en: string; ar: string };
  /** Article 25(d) — measures taken. */
  containmentMeasures: { en: string; ar: string };
  /** Article 25(d) — measures proposed. */
  proposedMeasures: { en: string; ar: string };
  /** Detection / awareness timestamps. */
  detectedAt: string;
  awareAt: string;
  /** Whether data subjects have been or will be notified separately. */
  dataSubjectsNotified: boolean;
  dataSubjectsNotificationPlannedAt?: string;
}

const DEFAULT_CONTROLLER: SdaiaNotificationPacket['controller'] = {
  nameEn: 'TSS Saudi Holding',
  nameAr: 'القابضة السعودية للتقنية',
  crNumber: '1010299292',
  dpoNameEn: 'Mona Al-Bishi',
  dpoNameAr: 'منى البيشي',
  dpoEmail: 'dpo@tss.sa',
  dpoPhone: '+966 11 200 0000',
};

/**
 * Generate a draft SDAIA notification packet from a breach record. The
 * narrative fields are deterministic so a DPO can edit-then-send rather
 * than draft from scratch. Bilingual by design.
 */
export function generateSdaiaNotification(
  breach: PrivacyBreachDto,
  severity: PdplSeverity = 'HIGH',
): SdaiaNotificationPacket {
  const cats = ['Identification data', 'Contact data'];
  if (severity === 'HIGH' || severity === 'CRITICAL') cats.push('Financial / transactional data');
  if (severity === 'CRITICAL') cats.push('Special-category data');

  return {
    internalRef: `PDPL-N-${breach.id}-${new Date().getFullYear()}`,
    controller: DEFAULT_CONTROLLER,
    natureOfBreach: {
      en: `${breach.breachType ?? 'Confidentiality'} breach involving ${breach.affectedRecords?.toLocaleString() ?? 'an undetermined number of'} record(s). ${breach.descriptionEn ?? ''}`,
      ar: `حادثة إخلال بـ${breach.breachType === 'AVAILABILITY' ? 'التوافر' : breach.breachType === 'INTEGRITY' ? 'السلامة' : 'السرية'} تشمل ${breach.affectedRecords?.toLocaleString() ?? 'عدد غير محدد من'} سجل(ات).`,
    },
    categoriesAffected: cats,
    approximateSubjects: breach.affectedRecords ?? 0,
    approximateRecords: breach.affectedRecords ?? 0,
    likelyConsequences: {
      en: severity === 'CRITICAL'
        ? 'High risk of identity theft, financial loss, or unauthorised account access for affected data subjects. Affected subjects will be notified directly.'
        : severity === 'HIGH'
          ? 'Elevated risk of unsolicited contact, phishing, or unauthorised marketing. Direct notification to affected data subjects required under PDPL Art. 26.'
          : 'Limited risk to affected subjects given encryption at rest and prompt containment. No direct notification required.',
      ar: severity === 'CRITICAL'
        ? 'مخاطر مرتفعة جداً تشمل سرقة الهوية والخسائر المالية والوصول غير المصرّح به للحسابات. سيتمّ إبلاغ أصحاب البيانات مباشرة.'
        : severity === 'HIGH'
          ? 'مخاطر مرتفعة من الاتصال غير المرغوب فيه أو التصيّد أو التسويق غير المصرّح به. الإبلاغ المباشر مطلوب وفق المادة 26 من نظام حماية البيانات.'
          : 'مخاطر محدودة على أصحاب البيانات بفضل التشفير أثناء التخزين والاحتواء السريع. لا يلزم إبلاغ مباشر.',
    },
    containmentMeasures: {
      en: 'Affected systems isolated from the network within the first hour of detection. Privileged access tokens revoked. Forensic image of impacted hosts captured for SDAIA inspection.',
      ar: 'تمّ عزل الأنظمة المتأثّرة عن الشبكة خلال الساعة الأولى من الكشف، وإلغاء الرموز الإمتيازية، والتقاط صورة جنائية للأنظمة المتأثرة لفحص سدايا.',
    },
    proposedMeasures: {
      en: 'Reset of all credentials in scope, mandatory MFA enforcement, third-party DLP scan, and a post-incident review reported to the Risk Committee within 30 days.',
      ar: 'إعادة تعيين بيانات الدخول، وفرض التحقق الثنائي الإلزامي، وفحص DLP بطرف ثالث، ومراجعة ما بعد الحادثة تُقدَّم للجنة المخاطر خلال 30 يوماً.',
    },
    detectedAt: breach.occurredAt ?? new Date().toISOString(),
    awareAt: breach.reportedAt ?? breach.occurredAt ?? new Date().toISOString(),
    dataSubjectsNotified: breach.status === 'CLOSED',
    dataSubjectsNotificationPlannedAt: requiresDataSubjectNotification(severity)
      ? addHours(breach.reportedAt ?? new Date().toISOString(), DATA_SUBJECTS_DEADLINE_HOURS)
      : undefined,
  };
}

// ─── Hour-precision countdown ──────────────────────────────────────────────

export interface BreachClock {
  hoursElapsed: number;
  hoursRemaining: number;        // negative when over the 72h budget
  hoursToDataSubjects: number;   // negative when over the 7-day budget
  pctConsumed: number;           // 0–999 (capped above 999%)
  tier: 'GREEN' | 'AMBER' | 'RED' | 'BREACHED';
  /** Expected next stage by elapsed time. */
  expectedStage: PdplStageCode;
}

export function computeBreachClock(
  awareAt: string,
  now: Date = new Date(),
): BreachClock {
  const aware = new Date(awareAt);
  const elapsedMs = now.getTime() - aware.getTime();
  const hoursElapsed = elapsedMs / 3_600_000;
  const hoursRemaining = SDAIA_DEADLINE_HOURS - hoursElapsed;
  const hoursToDataSubjects = DATA_SUBJECTS_DEADLINE_HOURS - hoursElapsed;
  const pctConsumed = Math.min(999, Math.round((hoursElapsed / SDAIA_DEADLINE_HOURS) * 100));

  let tier: BreachClock['tier'];
  if (hoursRemaining < 0) tier = 'BREACHED';
  else if (hoursRemaining <= 12) tier = 'RED';
  else if (hoursRemaining <= 36) tier = 'AMBER';
  else tier = 'GREEN';

  // Determine the expected stage — purely informational, doesn't enforce.
  let expectedStage: PdplStageCode = 'DETECTED';
  let cumulative = 0;
  for (const s of PDPL_BREACH_STAGES) {
    cumulative += s.hoursBudget;
    if (hoursElapsed <= cumulative) { expectedStage = s.code; break; }
    expectedStage = s.code;
  }

  return {
    hoursElapsed: Math.round(hoursElapsed * 10) / 10,
    hoursRemaining: Math.round(hoursRemaining * 10) / 10,
    hoursToDataSubjects: Math.round(hoursToDataSubjects * 10) / 10,
    pctConsumed,
    tier,
    expectedStage,
  };
}

function addHours(iso: string, hours: number): string {
  const d = new Date(iso);
  d.setTime(d.getTime() + hours * 3_600_000);
  return d.toISOString();
}

// ─── Format helpers ─────────────────────────────────────────────────────────

export function formatHoursLabel(hours: number, lang: 'en' | 'ar'): string {
  const abs = Math.abs(hours);
  const days = Math.floor(abs / 24);
  const remH = Math.round(abs - days * 24);
  if (lang === 'ar') {
    if (days === 0) return `${remH} ساعة`;
    return `${days}ي ${remH}س`;
  }
  if (days === 0) return `${remH}h`;
  return `${days}d ${remH}h`;
}
