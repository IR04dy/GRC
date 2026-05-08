// MOC (Ministry of Commerce / وزارة التجارة) compliance — DD-85.
//
// Three regimes that touch every commercial entity in Saudi Arabia:
//
//   1) Tasattur (نظام مكافحة التستر) — Anti-Concealment Law (2020).
//      The most-cited Saudi commercial offence: a Saudi national fronts
//      a business actually controlled by a foreigner. Penalties include
//      fines up to SAR 5M, prison up to 5 years, deportation, and asset
//      confiscation. MOC publishes a Tasattur Risk Index (TRI) for every
//      registered firm based on a basket of indicators (foreign-supplier
//      concentration, ownership-vs-operations mismatch, payroll-vs-
//      revenue ratios, beneficial-ownership disclosure gaps).
//
//   2) Consumer Protection Law (نظام حماية المستهلك) + e-Commerce Law.
//      Refunds, returns, warranty obligations, deceptive marketing,
//      price labelling, unsolicited contact rules.
//
//   3) Commercial Registry obligations (التزامات السجل التجاري) — keep
//      the CR current, beneficial-ownership disclosure, annual
//      confirmation, trade-name + activity-code accuracy.
//
// References:
//   Anti-Concealment Law (2020) — Royal Decree M/4
//   Consumer Protection Law (2014) — Royal Decree M/24, with e-Commerce
//                                    Law amendments (2019)
//   Commercial Register Law (2022 update) — Royal Decree M/40

// ── Tasattur risk index (TRI) ──────────────────────────────────────────
//
// Each indicator contributes 0–10 points based on observed deviation.
// Total score 0–100 → tone band: LOW <25, MEDIUM 25-49, HIGH 50-74,
// CRITICAL ≥ 75. The matrix is what MOC's investigators use as a
// triage tool — firms above HIGH typically get an on-site visit
// within 30 days.

export type TasatturRiskBand = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface TasatturIndicator {
  /** Stable code mirrored to control clauses. */
  code: string;
  titleEn: string;
  titleAr: string;
  /** Why this matters — what concealment pattern this catches. */
  rationaleEn: string;
  rationaleAr: string;
  /** Current weight (0-10). 0 = no concern, 10 = strong indicator. */
  weight: number;
}

export const TASATTUR_INDICATORS: TasatturIndicator[] = [
  {
    code: 'TST-1',
    titleEn: 'Single foreign supplier > 70% of payments',
    titleAr: 'مورد أجنبي واحد > 70٪ من المدفوعات',
    rationaleEn: 'A dominant foreign supplier with no commercial alternative is a classic concealment pattern — payments often flow to the real beneficial owner offshore.',
    rationaleAr: 'هيمنة مورد أجنبي واحد دون بديل تجاري نمط شائع للتستر — المدفوعات تتجه عادةً للمالك الحقيقي خارج المملكة.',
    weight: 4,
  },
  {
    code: 'TST-2',
    titleEn: 'Saudi-Iqama payroll ratio < 25% of revenue',
    titleAr: 'نسبة رواتب السعوديين/المقيمين < 25٪ من الإيراد',
    rationaleEn: 'Concealed firms frequently underpay registered Saudi staff while real operations are run by undocumented foreign workers.',
    rationaleAr: 'الشركات المتستَّر عليها كثيراً ما تدفع رواتب منخفضة للسعوديين بينما تُدار العمليات الفعلية بعمالة غير موثَّقة.',
    weight: 6,
  },
  {
    code: 'TST-3',
    titleEn: 'Beneficial-ownership disclosure outdated > 12 months',
    titleAr: 'إفصاح المالك الحقيقي قديم > 12 شهراً',
    rationaleEn: 'Annual UBO disclosure is mandatory under the 2022 CR amendments. Lapsed disclosure flags concealment risk.',
    rationaleAr: 'إفصاح المالك المنتفع سنوياً إلزامي وفق تعديلات السجل 2022. التأخير يرفع جرس إنذار التستر.',
    weight: 7,
  },
  {
    code: 'TST-4',
    titleEn: 'Power of attorney to single non-Saudi for all banking + signing',
    titleAr: 'وكالة لشخص غير سعودي واحد لكل البنوك والتوقيع',
    rationaleEn: 'A blanket POA from the Saudi owner to one non-Saudi for all banking + commercial signing is the operational signature of a concealment arrangement.',
    rationaleAr: 'الوكالة الشاملة من المالك السعودي لشخص غير سعودي واحد للبنوك والتوقيع التجاري بصمة عملية لترتيب التستر.',
    weight: 9,
  },
  {
    code: 'TST-5',
    titleEn: 'Saudi owner not present at premises during business hours',
    titleAr: 'المالك السعودي غير موجود في المقر خلال ساعات العمل',
    rationaleEn: 'MOC inspectors weight in-person presence heavily — an absentee owner with active operations is a strong indicator.',
    rationaleAr: 'مفتشو الوزارة يولون حضور المالك أهمية كبيرة — غياب المالك مع تشغيل نشط مؤشر قوي.',
    weight: 8,
  },
  {
    code: 'TST-6',
    titleEn: 'CR activities mismatch actual operations',
    titleAr: 'أنشطة السجل التجاري لا تطابق العمليات الفعلية',
    rationaleEn: 'CR registered as "general trading" while operations are restaurant + delivery is the most common mismatch — and a fast track to enforcement.',
    rationaleAr: 'تسجيل السجل كـ"تجارة عامة" بينما العمليات مطعم + توصيل أكثر أنماط التطابق غير المُطابق شيوعاً — وأسرع طريق للإنفاذ.',
    weight: 6,
  },
];

export interface TasatturRiskBandMeta {
  band: TasatturRiskBand;
  labelEn: string;
  labelAr: string;
  tone: 'success' | 'info' | 'warning' | 'danger';
  /** Lower bound of TRI score for this band (≥). */
  minScore: number;
  /** What MOC typically does at this band. */
  enforcementEn: string;
  enforcementAr: string;
}

export const TASATTUR_BANDS: TasatturRiskBandMeta[] = [
  {
    band: 'CRITICAL', labelEn: 'Critical', labelAr: 'حرج',
    tone: 'danger', minScore: 75,
    enforcementEn: 'On-site MOC inspection within 7 days. CR suspension likely. Bank-account freeze pending investigation.',
    enforcementAr: 'تفتيش ميداني للوزارة خلال 7 أيام. يُرجَّح تعليق السجل. تجميد الحسابات البنكية ريثما يُحقَّق.',
  },
  {
    band: 'HIGH', labelEn: 'High', labelAr: 'مرتفع',
    tone: 'warning', minScore: 50,
    enforcementEn: 'Desk audit + on-site visit within 30 days. Beneficial-ownership re-disclosure mandated.',
    enforcementAr: 'مراجعة مكتبية + زيارة ميدانية خلال 30 يوماً. يُلزَم بإعادة إفصاح المالك الحقيقي.',
  },
  {
    band: 'MEDIUM', labelEn: 'Medium', labelAr: 'متوسط',
    tone: 'info', minScore: 25,
    enforcementEn: 'Quarterly desk review. Remediation plan may be requested.',
    enforcementAr: 'مراجعة مكتبية ربع سنوية. قد يُطلَب خطة معالجة.',
  },
  {
    band: 'LOW', labelEn: 'Low', labelAr: 'منخفض',
    tone: 'success', minScore: 0,
    enforcementEn: 'No active enforcement. Annual self-attestation only.',
    enforcementAr: 'لا إنفاذ فعلي. الإقرار الذاتي السنوي فقط.',
  },
];

export function classifyTasatturBand(score: number): TasatturRiskBand {
  for (const m of TASATTUR_BANDS) if (score >= m.minScore) return m.band;
  return 'LOW';
}

export function tasatturBandMeta(band: TasatturRiskBand): TasatturRiskBandMeta {
  return TASATTUR_BANDS.find(b => b.band === band)!;
}

// ── Consumer Protection requirements ───────────────────────────────────

export interface MocRequirement {
  code: string;
  titleEn: string;
  titleAr: string;
  descriptionEn: string;
  descriptionAr: string;
  obligation: 'MANDATORY' | 'RECOMMENDED';
  cadence?: 'EVENT_DRIVEN' | 'MONTHLY' | 'QUARTERLY' | 'ANNUAL';
}

export const MOC_CONSUMER_REQUIREMENTS: MocRequirement[] = [
  {
    code: 'CP-1',
    titleEn: 'Refund / return window — 7 days minimum',
    titleAr: 'نافذة الاسترجاع — 7 أيام كحد أدنى',
    descriptionEn: 'e-Commerce Law Art. 14 — buyer can return goods within 7 days without justification. Refunds within 14 days. Non-compliance fine up to SAR 1M.',
    descriptionAr: 'نظام التجارة الإلكترونية المادة 14 — يحق للمشتري إرجاع البضاعة خلال 7 أيام دون مبرّر. استرداد خلال 14 يوماً. غرامة عدم الامتثال حتى 1 مليون ريال.',
    obligation: 'MANDATORY',
  },
  {
    code: 'CP-2',
    titleEn: 'Bilingual price labelling (Arabic mandatory)',
    titleAr: 'لصق الأسعار بلغتين (العربية إلزامية)',
    descriptionEn: 'Consumer Protection Law Art. 8 — every price tag must include the Arabic price. English may be appended. SAR 100k fine per violation.',
    descriptionAr: 'نظام حماية المستهلك المادة 8 — كل لصاقة سعر يجب أن تتضمّن السعر بالعربية. يمكن إضافة الإنجليزية. غرامة 100 ألف ريال عن كل مخالفة.',
    obligation: 'MANDATORY',
  },
  {
    code: 'CP-3',
    titleEn: 'Warranty disclosure on durable goods',
    titleAr: 'الإفصاح عن الضمان للسلع المعمّرة',
    descriptionEn: 'Minimum 1-year warranty on electronics + appliances; minimum 5 years on vehicles. Warranty terms in Arabic.',
    descriptionAr: 'ضمان لا يقل عن سنة على الإلكترونيات والأجهزة؛ 5 سنوات على المركبات. شروط الضمان بالعربية.',
    obligation: 'MANDATORY',
  },
  {
    code: 'CP-4',
    titleEn: 'Deceptive-marketing ban — claims must be substantiated',
    titleAr: 'حظر التسويق المخادع — يجب إثبات الادعاءات',
    descriptionEn: 'No "best", "fastest", "cheapest" claims without independent substantiation. Influencer-marketing disclosure required.',
    descriptionAr: 'لا ادعاءات بـ"الأفضل" أو "الأسرع" أو "الأرخص" دون إثبات مستقل. يلزم الإفصاح في تسويق المؤثّرين.',
    obligation: 'MANDATORY',
  },
  {
    code: 'CP-5',
    titleEn: 'Customer complaint resolution within 5 working days',
    titleAr: 'حل شكوى العميل خلال 5 أيام عمل',
    descriptionEn: 'MOC consumer-protection portal escalation if unresolved. Public complaint board updated weekly.',
    descriptionAr: 'تصعيد بوّابة حماية المستهلك للوزارة عند عدم الحل. لوحة الشكاوى العلنية تُحدَّث أسبوعياً.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
];

export const MOC_REGISTRY_REQUIREMENTS: MocRequirement[] = [
  {
    code: 'CR-1',
    titleEn: 'Annual CR confirmation submission',
    titleAr: 'تأكيد السجل التجاري السنوي',
    descriptionEn: 'Submit annual confirmation via the MOC portal within 30 days of CR anniversary. Failure suspends the CR.',
    descriptionAr: 'تقديم التأكيد السنوي عبر بوّابة الوزارة خلال 30 يوماً من تاريخ ذكرى السجل. التأخير يُعلِّق السجل.',
    obligation: 'MANDATORY', cadence: 'ANNUAL',
  },
  {
    code: 'CR-2',
    titleEn: 'Beneficial-ownership disclosure',
    titleAr: 'إفصاح المالك الحقيقي',
    descriptionEn: 'Disclose every natural person owning ≥ 25% of the entity, directly or indirectly. Annual refresh; event-driven update on any change.',
    descriptionAr: 'الإفصاح عن كل شخص طبيعي يملك ≥ 25٪ من المنشأة مباشرة أو غير مباشرة. تحديث سنوي + عند أي تغيير.',
    obligation: 'MANDATORY', cadence: 'ANNUAL',
  },
  {
    code: 'CR-3',
    titleEn: 'Trade-name + activity-code accuracy',
    titleAr: 'دقّة الاسم التجاري ورموز الأنشطة',
    descriptionEn: 'CR-registered activities must match actual operations within 30 days of any change. Catching firms operating outside their CR scope is a major Tasattur indicator.',
    descriptionAr: 'الأنشطة المسجَّلة يجب أن تطابق العمليات الفعلية خلال 30 يوماً من أي تغيير. ضبط منشأة تعمل خارج نطاق سجلها مؤشر تستر رئيسي.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
  {
    code: 'CR-4',
    titleEn: 'Capital-change notification to MOC',
    titleAr: 'إشعار الوزارة بتغيير رأس المال',
    descriptionEn: 'Any change to share capital, partners, or legal form must be filed with MOC within 30 days.',
    descriptionAr: 'أي تغيير في رأس المال أو الشركاء أو الشكل القانوني يُسجَّل لدى الوزارة خلال 30 يوماً.',
    obligation: 'MANDATORY', cadence: 'EVENT_DRIVEN',
  },
];

// ── Demo state ─────────────────────────────────────────────────────────

export interface MocState {
  /** Tasattur indicator scores keyed by indicator code (0-10 each). */
  indicatorScores: Record<string, number>;
  /** When the latest MOC self-assessment was submitted. */
  lastAssessmentAt?: string;
  /** When the CR was last confirmed via the MOC portal. */
  lastCrConfirmationAt?: string;
}

const STATE_KEY = 'grc.moc.state';
// Default: a tenant landing in the LOW band (TRI ~16) — typical for a
// well-governed mid-sized SaaS company. Can be edited via UI later.
const DEFAULT_STATE: MocState = {
  indicatorScores: {
    'TST-1': 2,  // moderate concentration, but multiple suppliers
    'TST-2': 1,  // payroll ratio healthy
    'TST-3': 0,  // BO disclosed last quarter
    'TST-4': 0,  // no blanket POA
    'TST-5': 1,  // Saudi owner part-time at premises
    'TST-6': 0,  // CR matches operations
  },
  lastAssessmentAt: '2026-04-12',
  lastCrConfirmationAt: '2026-01-15',
};

export function loadMocState(): MocState {
  try {
    const raw = localStorage.getItem(STATE_KEY);
    if (!raw) return DEFAULT_STATE;
    const parsed = JSON.parse(raw);
    return { ...DEFAULT_STATE, ...parsed };
  } catch { return DEFAULT_STATE; }
}

/** Compute TRI score = sum of (indicator score × weight) capped at 100. */
export function computeTasatturScore(state: MocState): number {
  let score = 0;
  for (const ind of TASATTUR_INDICATORS) {
    const raw = state.indicatorScores[ind.code] ?? 0;
    score += raw * ind.weight / 10; // raw 0-10 × weight 0-10 / 10 = 0..weight
  }
  return Math.min(100, Math.round(score));
}
