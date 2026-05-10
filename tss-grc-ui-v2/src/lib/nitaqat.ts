// Nitaqat (نطاقات) — Saudi Saudization band engine — DD-83.
//
// HRSD's Nitaqat program classifies every private-sector employer into a
// coloured band based on the percentage of Saudi nationals in their
// workforce. The band drives:
//
//   • Eligibility for new work-permit issuance / renewal
//   • Visa-quota grants
//   • Government-tender eligibility (Tadawul + Etimad gates)
//   • Renewal of commercial registration in some sectors
//
// Bands (high → low):
//   PLATINUM   — top performers, fast-track services
//   GREEN_HIGH — comfortable headroom, normal services
//   GREEN_MID  — meeting requirements
//   GREEN_LOW  — at the threshold
//   YELLOW     — below requirement, restricted privileges (no new visas,
//                expat visa renewals capped at one year, slowdown notices)
//   RED        — non-compliant, forbidden from issuing/renewing permits;
//                expats may transfer to other employers without consent
//
// Thresholds vary by **sector × establishment-size band**, and HRSD
// publishes the matrix annually. The numbers below are realistic
// defaults for the "Information & communication" sector (SIC 58–63),
// which is what most TSS demo tenants land in.
//
// References:
//   HRSD Nitaqat circular (latest annual update)
//   HRSD Saudi Labor Law (نظام العمل) Articles 3–14 — work permits + quotas
//   HRSD GOSI Implementing Regulations — contribution + registration rules

export type NitaqatBand = 'PLATINUM' | 'GREEN_HIGH' | 'GREEN_MID' | 'GREEN_LOW' | 'YELLOW' | 'RED';

export interface NitaqatBandMeta {
  band: NitaqatBand;
  labelEn: string;
  labelAr: string;
  /** Display tone for badges + chips. */
  tone: 'success' | 'info' | 'warning' | 'danger' | 'primary';
  /** Lower bound of Saudization % for the default sector (>= this %). */
  minPercent: number;
  /** Privileges granted at this band — what the user actually unlocks. */
  privilegesEn: string;
  privilegesAr: string;
  /** Hard restrictions when in this band. */
  restrictionsEn?: string;
  restrictionsAr?: string;
}

/** Default thresholds for the "Information & Communication" sector,
 *  medium establishment size (50–499 employees). HRSD sector matrices
 *  shift these by ±5–10% per sector × size band. */
export const NITAQAT_BANDS: NitaqatBandMeta[] = [
  {
    band: 'PLATINUM', labelEn: 'Platinum', labelAr: 'بلاتيني',
    tone: 'primary', minPercent: 40,
    privilegesEn: 'Fast-track e-services, free visa transfers from lower-band employers, eligible for all government tenders.',
    privilegesAr: 'خدمات إلكترونية متسارعة، نقل خدمات مجاني من الجهات في النطاقات الأدنى، أهلية لجميع المناقصات الحكومية.',
  },
  {
    band: 'GREEN_HIGH', labelEn: 'Green High', labelAr: 'أخضر مرتفع',
    tone: 'success', minPercent: 25,
    privilegesEn: 'Standard visa quotas, eligible to absorb expats from Yellow / Red employers, full government-tender access.',
    privilegesAr: 'حصص تأشيرات قياسية، إمكانية استيعاب المقيمين من جهات النطاق الأصفر / الأحمر، أهلية كاملة للمناقصات.',
  },
  {
    band: 'GREEN_MID', labelEn: 'Green Mid', labelAr: 'أخضر متوسط',
    tone: 'success', minPercent: 15,
    privilegesEn: 'Standard visa quotas, government-tender eligibility, normal e-service throughput.',
    privilegesAr: 'حصص تأشيرات قياسية، أهلية للمناقصات الحكومية، معدل خدمات إلكترونية طبيعي.',
  },
  {
    band: 'GREEN_LOW', labelEn: 'Green Low', labelAr: 'أخضر منخفض',
    tone: 'info', minPercent: 10,
    privilegesEn: 'At the minimum threshold — visa renewals proceed but new-quota grants slow.',
    privilegesAr: 'عند الحد الأدنى — تجديد التأشيرات يستمر لكن حصص جديدة تبطؤ.',
  },
  {
    band: 'YELLOW', labelEn: 'Yellow', labelAr: 'أصفر',
    tone: 'warning', minPercent: 5,
    privilegesEn: 'Restricted services. Visa renewals limited to one year. No new work-permit issuance.',
    privilegesAr: 'خدمات مقيّدة. تجديد التأشيرات لسنة واحدة فقط. لا تُصدَر تصاريح عمل جديدة.',
    restrictionsEn: 'Slowdown notice from HRSD; remediation plan required within 90 days; ineligible for new government tenders.',
    restrictionsAr: 'إنذار من الوزارة؛ يلزم خطة معالجة خلال 90 يوماً؛ غير مؤهَّل للمناقصات الحكومية الجديدة.',
  },
  {
    band: 'RED', labelEn: 'Red', labelAr: 'أحمر',
    tone: 'danger', minPercent: 0,
    privilegesEn: 'No new visas, no work-permit renewals, no government-tender eligibility.',
    privilegesAr: 'لا تأشيرات جديدة، لا تجديد لتصاريح العمل، عدم أهلية للمناقصات الحكومية.',
    restrictionsEn: 'Expats may transfer to other employers without current-employer consent. Commercial-registry renewal flagged.',
    restrictionsAr: 'يحقّ للمقيمين نقل خدماتهم لجهات أخرى دون موافقة صاحب العمل. يُعلَّق تجديد السجل التجاري.',
  },
];

/** Classify a Saudization percentage into a band. */
export function classifyNitaqatBand(percent: number): NitaqatBand {
  for (const meta of NITAQAT_BANDS) {
    if (percent >= meta.minPercent) return meta.band;
  }
  return 'RED';
}

export function nitaqatMeta(band: NitaqatBand): NitaqatBandMeta {
  return NITAQAT_BANDS.find(b => b.band === band)!;
}

// ── Compliance requirements (used to render the "Requirements" section
//    of the panel + the framework controls catalogue) ──────────────────

export interface NitaqatRequirement {
  /** Stable code mirrored to the framework's clauseRef. */
  code: string;
  titleEn: string;
  titleAr: string;
  descriptionEn: string;
  descriptionAr: string;
  /** Mandatory (M) vs Recommended (R). */
  obligation: 'MANDATORY' | 'RECOMMENDED';
  /** Reporting cadence to HRSD. */
  cadence?: 'MONTHLY' | 'QUARTERLY' | 'ANNUAL' | 'EVENT_DRIVEN';
}

export const NITAQAT_REQUIREMENTS: NitaqatRequirement[] = [
  {
    code: 'NITAQAT-1',
    titleEn: 'Maintain Saudization percentage at or above sector minimum',
    titleAr: 'الحفاظ على نسبة السعودة عند الحد الأدنى للقطاع أو أعلى',
    descriptionEn: 'Minimum threshold defined by HRSD per sector × establishment-size band; revised annually. Non-compliance drops the firm to Yellow / Red.',
    descriptionAr: 'الحد الأدنى يُحدَّد من قبل الوزارة لكل قطاع × حجم المنشأة؛ يُراجَع سنوياً. عدم الامتثال يخفّض المنشأة للنطاق الأصفر / الأحمر.',
    obligation: 'MANDATORY', cadence: 'MONTHLY',
  },
  {
    code: 'NITAQAT-2',
    titleEn: 'Submit monthly Saudization report via HRSD portal',
    titleAr: 'تقديم تقرير السعودة الشهري عبر بوّابة الوزارة',
    descriptionEn: 'Workforce headcount + Saudi/non-Saudi breakdown reported via Qiwa portal by the 5th of the following month.',
    descriptionAr: 'إحصاء القوى العاملة + توزيع السعوديين/غير السعوديين عبر بوابة قِوى قبل الخامس من الشهر التالي.',
    obligation: 'MANDATORY', cadence: 'MONTHLY',
  },
  {
    code: 'NITAQAT-3',
    titleEn: 'Restricted-jobs compliance — Saudis only in reserved roles',
    titleAr: 'الامتثال للمهن المقصورة — السعوديون فقط في الوظائف المحجوزة',
    descriptionEn: 'HRSD designates roles reserved for Saudi nationals (HR managers, sales in select sectors, government-relations officers, certain retail). Hiring expats in reserved roles triggers immediate Red-band drop.',
    descriptionAr: 'تُحدَّد المهن المقصورة على السعوديين (مدراء الموارد البشرية، المبيعات في قطاعات محدّدة، مسؤولو العلاقات الحكومية، بعض التجزئة). توظيف غير السعوديين في هذه المهن ينقل المنشأة فوراً للنطاق الأحمر.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
  {
    code: 'NITAQAT-4',
    titleEn: 'Annual Nitaqat band re-classification',
    titleAr: 'إعادة التصنيف السنوية للنطاق',
    descriptionEn: 'HRSD publishes the updated sector matrix annually (typically Q1). Firms re-banded based on rolling 13-week Saudization average.',
    descriptionAr: 'تُصدر الوزارة مصفوفة القطاعات سنوياً (عادة الربع الأول). تُعاد عملية التصنيف بناء على متوسط السعودة المتجدّد لـ 13 أسبوعاً.',
    obligation: 'MANDATORY', cadence: 'ANNUAL',
  },
];

export const HRSD_LABOR_REQUIREMENTS: NitaqatRequirement[] = [
  {
    code: 'LABOR-1',
    titleEn: 'Wage Protection System (WPS) — monthly salary deposits via approved bank',
    titleAr: 'نظام حماية الأجور — صرف الرواتب الشهرية عبر بنك مُعتمد',
    descriptionEn: 'All employee salaries must be deposited through a SAMA-licensed bank by the 11th day of the following month. Non-compliance suspends new hires + visa renewals.',
    descriptionAr: 'جميع رواتب الموظفين تُودَع عبر بنك مرخَّص من ساما قبل اليوم 11 من الشهر التالي. عدم الامتثال يوقف التوظيف الجديد + تجديد التأشيرات.',
    obligation: 'MANDATORY', cadence: 'MONTHLY',
  },
  {
    code: 'LABOR-2',
    titleEn: 'Employment contracts in Arabic + Saudi-compliant terms',
    titleAr: 'عقود العمل باللغة العربية + شروط متوافقة',
    descriptionEn: 'Every employment contract must be in Arabic (English may be appended). Probation ≤ 90 days, end-of-service benefit clause, weekly rest day, annual leave entitlement.',
    descriptionAr: 'كل عقد عمل يجب أن يكون باللغة العربية (يمكن إرفاق نسخة إنجليزية). فترة التجربة ≤ 90 يوماً، بند مكافأة نهاية الخدمة، يوم راحة أسبوعي، إجازة سنوية.',
    obligation: 'MANDATORY',
  },
  {
    code: 'LABOR-3',
    titleEn: 'Maximum work hours and overtime caps',
    titleAr: 'الحد الأقصى لساعات العمل والعمل الإضافي',
    descriptionEn: '8 hours/day or 48 hours/week (6 hours/day during Ramadan for Muslim employees). Overtime capped at 720 hours/year and paid at 150% of base rate.',
    descriptionAr: '8 ساعات يومياً أو 48 ساعة أسبوعياً (6 ساعات يومياً خلال رمضان للموظفين المسلمين). الحد الأقصى للعمل الإضافي 720 ساعة سنوياً، يُحتسَب بنسبة 150٪ من الأجر الأساسي.',
    obligation: 'MANDATORY',
  },
  {
    code: 'LABOR-4',
    titleEn: 'End-of-service benefits (EOSB) accrual',
    titleAr: 'احتساب مكافأة نهاية الخدمة',
    descriptionEn: 'Half-month salary per year for first 5 years; full month per year thereafter. Payable on termination unless dismissed for cause under Article 80.',
    descriptionAr: 'نصف راتب شهري عن كل سنة للسنوات الخمس الأولى؛ راتب شهري كامل لكل سنة بعد ذلك. تُستحَق عند انتهاء الخدمة ما لم يُفصَل العامل لسبب مشروع وفق المادة 80.',
    obligation: 'MANDATORY',
  },
  {
    code: 'LABOR-5',
    titleEn: 'Female employees — workplace facilities + maternity leave',
    titleAr: 'الموظفات — مرافق العمل وإجازة الأمومة',
    descriptionEn: '10 weeks paid maternity leave, dedicated prayer area, no termination during pregnancy or maternity leave.',
    descriptionAr: '10 أسابيع إجازة أمومة مدفوعة، مصلّى مخصّص، لا فصل أثناء الحمل أو إجازة الأمومة.',
    obligation: 'MANDATORY',
  },
];

export const HRSD_GOSI_REQUIREMENTS: NitaqatRequirement[] = [
  {
    code: 'GOSI-1',
    titleEn: 'Mandatory GOSI registration of all employees',
    titleAr: 'التسجيل الإلزامي لجميع الموظفين في التأمينات',
    descriptionEn: 'Every Saudi national + non-Saudi employee must be registered with GOSI within 5 working days of hire.',
    descriptionAr: 'كل موظف سعودي + غير سعودي يجب تسجيله في التأمينات خلال 5 أيام عمل من تاريخ التعيين.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
  {
    code: 'GOSI-2',
    titleEn: 'Monthly contribution remittance',
    titleAr: 'سداد الاشتراكات الشهرية',
    descriptionEn: 'Saudi employees: 22% (9% employee + 9% employer + 4% Saned unemployment). Non-Saudi employees: 2% occupational hazards (employer-funded). Due by 15th of following month.',
    descriptionAr: 'الموظفون السعوديون: 22٪ (9٪ موظف + 9٪ صاحب عمل + 4٪ ساند). الموظفون غير السعوديين: 2٪ تأمين الأخطار المهنية (يتحمّله صاحب العمل). الاستحقاق قبل اليوم 15 من الشهر التالي.',
    obligation: 'MANDATORY', cadence: 'MONTHLY',
  },
  {
    code: 'GOSI-3',
    titleEn: 'Workplace injury reporting within 24 hours',
    titleAr: 'الإبلاغ عن إصابات العمل خلال 24 ساعة',
    descriptionEn: 'Any workplace injury or occupational illness must be reported to GOSI within 24 hours. Failure exposes the employer to medical-claim cost recovery.',
    descriptionAr: 'أي إصابة عمل أو مرض مهني يجب الإبلاغ عنه للتأمينات خلال 24 ساعة. عدم الإبلاغ يُحمّل صاحب العمل تكاليف المطالبات الطبية.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
  {
    code: 'GOSI-4',
    titleEn: 'Annual contribution reconciliation',
    titleAr: 'التسوية السنوية للاشتراكات',
    descriptionEn: 'Year-end reconciliation against actual payroll. Over/under-payments adjusted. Discrepancies > 5% trigger audit.',
    descriptionAr: 'تسوية نهاية السنة مقابل كشوف الرواتب الفعلية. تُعدَّل الزيادات/النقص. الفروق التي تتجاوز 5٪ تُفعّل المراجعة.',
    obligation: 'MANDATORY', cadence: 'ANNUAL',
  },
];

// ── Demo state ─────────────────────────────────────────────────────────
//
// In a real deployment this state lives in a backend HR module; for the
// platform demo we hold it in localStorage so it's editable from the UI
// and persists across reloads. Defaults are tuned to GREEN_MID — a
// realistic "we're meeting requirements but not crushing it" position.

export interface NitaqatState {
  totalEmployees: number;
  saudiEmployees: number;
  /** ISO date of the last Saudization-report submission to HRSD. */
  lastReportSubmittedAt?: string;
  /** Sector × establishment-size profile — drives the threshold lookup. */
  sector: 'INFORMATION_COMMUNICATION' | 'BANKING' | 'RETAIL' | 'CONSTRUCTION' | 'MANUFACTURING';
  establishmentSize: 'GIANT' | 'LARGE' | 'MEDIUM' | 'SMALL';
}

const STATE_KEY = 'grc.nitaqat.state';
const DEFAULT_STATE: NitaqatState = {
  totalEmployees: 247,
  saudiEmployees: 51,           // ~20.6% — lands in GREEN_MID
  lastReportSubmittedAt: '2026-04-05',
  sector: 'INFORMATION_COMMUNICATION',
  establishmentSize: 'MEDIUM',
};

export function loadNitaqatState(): NitaqatState {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (!raw) return DEFAULT_STATE;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_STATE, ...parsed };
  } catch { return DEFAULT_STATE; }
}

export function computeSaudization(state: NitaqatState): number {
  if (state.totalEmployees === 0) return 0;
  return Math.round((state.saudiEmployees / state.totalEmployees) * 1000) / 10; // one decimal
}
