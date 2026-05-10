// Vendor onboarding workflow + tier-driven reassessment cadence — DD-74.
//
// Most TPRM platforms (OneTrust Vendorpedia, Prevalent, Aravo, ProcessUnity,
// Archer Vendor) treat the vendor lifecycle as two separate flows that
// mesh:
//
//   1) ONBOARDING — a 6-stage front-door pipeline that walks every new
//      vendor from "we want to use them" to "active, contracted, signed-
//      off". Each stage has an owner, a gate, and an artefact. Until the
//      vendor reaches ACTIVE the platform can refuse to issue purchase
//      orders or grant tenant access.
//
//   2) REASSESSMENT CADENCE — once a vendor is active, periodic risk
//      re-evaluation runs on a tier-driven schedule. Tier-1 vendors
//      (mission-critical, customer-facing, hold customer PII, or annual
//      spend > 500k SAR) get re-assessed every year. Tier-2 = every two
//      years. Tier-3 = every three years. Tier-4 = ad-hoc only.
//
// Saudi anchors:
//   SAMA Outsourcing §5.1     — material-outsourcing risk assessment
//   SAMA Outsourcing §5.3     — periodic vendor performance reviews
//   NRC (Tadawul-Listed)      — board-level oversight of critical 3rd parties
//   PDPL Art. 19              — processor diligence + DPA before sharing PII
//
// International anchors:
//   ISO 27036 §6.3            — supplier-relationship lifecycle
//   ISO 27001 §A.5.19         — supplier-relationship security
//   NIST SP 800-161           — C-SCRM throughout the lifecycle
//   OCC Bulletin 2013-29      — third-party risk management lifecycle
//   EBA Outsourcing Guidelines (2019/02) — periodic reviews + register

// ── Tier classification ─────────────────────────────────────────────────

export type VendorTier = 'TIER_1' | 'TIER_2' | 'TIER_3' | 'TIER_4';

export interface VendorTierMeta {
  tier: VendorTier;
  labelEn: string;
  labelAr: string;
  /** Months between reassessments — 0 = ad-hoc only (no auto-schedule). */
  cadenceMonths: number;
  /** Cadence label rendered to the user. */
  cadenceLabelEn: string;
  cadenceLabelAr: string;
  /** Tone used for badges. */
  tone: 'danger' | 'warning' | 'info' | 'neutral';
  /** Short profile of what lands in this tier. */
  profileEn: string;
  profileAr: string;
}

export const VENDOR_TIERS: Record<VendorTier, VendorTierMeta> = {
  TIER_1: {
    tier: 'TIER_1',
    labelEn: 'Tier 1 — Critical',
    labelAr: 'الدرجة 1 — حرجة',
    cadenceMonths: 12,
    cadenceLabelEn: 'Annual reassessment',
    cadenceLabelAr: 'إعادة تقييم سنوية',
    tone: 'danger',
    profileEn: 'Customer-facing, holds customer PII, regulated outsourcing, or revenue-critical (e.g. core banking, payment processor, primary cloud).',
    profileAr: 'تواجه العميل، تحفظ بيانات شخصية للعملاء، إسناد منظَّم، أو حرجة للإيراد (الجوهر المصرفي، معالج الدفع، السحابة الرئيسية).',
  },
  TIER_2: {
    tier: 'TIER_2',
    labelEn: 'Tier 2 — High',
    labelAr: 'الدرجة 2 — مرتفعة',
    cadenceMonths: 24,
    cadenceLabelEn: 'Biennial reassessment',
    cadenceLabelAr: 'إعادة تقييم كل سنتين',
    tone: 'warning',
    profileEn: 'Significant operational impact, employee data only, or material spend (>500k SAR/yr).',
    profileAr: 'أثر تشغيلي كبير، بيانات موظفين فقط، أو إنفاق جوهري (>500 ألف ريال/سنوياً).',
  },
  TIER_3: {
    tier: 'TIER_3',
    labelEn: 'Tier 3 — Moderate',
    labelAr: 'الدرجة 3 — متوسطة',
    cadenceMonths: 36,
    cadenceLabelEn: 'Triennial reassessment',
    cadenceLabelAr: 'إعادة تقييم كل ثلاث سنوات',
    tone: 'info',
    profileEn: 'Departmental tools, no PII processing, low blast-radius (e.g. SaaS productivity).',
    profileAr: 'أدوات إدارية، لا تعالج بيانات شخصية، أثر محدود (أدوات إنتاجية).',
  },
  TIER_4: {
    tier: 'TIER_4',
    labelEn: 'Tier 4 — Low',
    labelAr: 'الدرجة 4 — منخفضة',
    cadenceMonths: 0,
    cadenceLabelEn: 'Ad-hoc only',
    cadenceLabelAr: 'حسب الحاجة فقط',
    tone: 'neutral',
    profileEn: 'One-off purchases, supplies, no system access — reassessment only on material change.',
    profileAr: 'مشتريات لمرة واحدة، مستلزمات، لا وصول للأنظمة — إعادة التقييم عند التغيير الجوهري فقط.',
  },
};

// ── Onboarding stages ───────────────────────────────────────────────────

export type OnboardingStage =
  | 'INTAKE'           // Just submitted — basic vendor info captured
  | 'TIER_ASSESSMENT'  // TPRM analyst classifies into Tier 1-4
  | 'DUE_DILIGENCE'    // Financial, sanctions, ZATCA, NRC checks
  | 'SECURITY_REVIEW'  // CISO Office: ISO 27001 / SOC 2 evidence, DPA review
  | 'CONTRACT'         // Legal + Procurement: contract drafted and signed
  | 'EXEC_APPROVAL'    // Risk-tier-gated executive sign-off
  | 'ACTIVE'           // Operating — moves into reassessment cadence
  | 'OFFBOARDING';     // Notice given, data return, access revocation

export interface OnboardingStageMeta {
  stage: OnboardingStage;
  labelEn: string;
  labelAr: string;
  ownerEn: string;
  ownerAr: string;
  slaDays: number;
  /** What artefact must exist before the stage can be advanced. */
  exitGateEn: string;
  exitGateAr: string;
  /** Stages that come after — used for advancement UI. */
  next: OnboardingStage | null;
}

export const ONBOARDING_STAGES: Record<OnboardingStage, OnboardingStageMeta> = {
  INTAKE: {
    stage: 'INTAKE',
    labelEn: 'Intake',                      labelAr: 'الاستلام',
    ownerEn: 'Business owner',              ownerAr: 'مالك العمل',
    slaDays: 2,
    exitGateEn: 'Vendor name, scope of services, business case captured.',
    exitGateAr: 'اسم المورد، نطاق الخدمات، والحاجة التجارية موثَّقة.',
    next: 'TIER_ASSESSMENT',
  },
  TIER_ASSESSMENT: {
    stage: 'TIER_ASSESSMENT',
    labelEn: 'Tier classification',         labelAr: 'تصنيف الدرجة',
    ownerEn: 'TPRM analyst',                ownerAr: 'محلّل المخاطر',
    slaDays: 3,
    exitGateEn: 'Tier 1-4 assigned with rationale; SAMA Outsourcing §5.1 form complete if material.',
    exitGateAr: 'تم تعيين الدرجة 1–4 مع المبرّر؛ نموذج إسناد ساما §5.1 مكتمل إن كان الإسناد جوهرياً.',
    next: 'DUE_DILIGENCE',
  },
  DUE_DILIGENCE: {
    stage: 'DUE_DILIGENCE',
    labelEn: 'Due diligence',               labelAr: 'العناية الواجبة',
    ownerEn: 'TPRM + Compliance + Finance', ownerAr: 'مخاطر الأطراف الثالثة + الامتثال + المالية',
    slaDays: 10,
    exitGateEn: 'Sanctions screen (UN/OFAC/SAMA), ZATCA tax compliance, financial-health check, NRC reference (if applicable).',
    exitGateAr: 'فحص العقوبات (الأمم المتحدة/مكتب الأصول الأجنبية/ساما)، الامتثال الضريبي زاتكا، فحص الملاءة المالية، مرجع لائحة الحوكمة (عند الانطباق).',
    next: 'SECURITY_REVIEW',
  },
  SECURITY_REVIEW: {
    stage: 'SECURITY_REVIEW',
    labelEn: 'Security review',             labelAr: 'المراجعة الأمنية',
    ownerEn: 'CISO Office + DPO',           ownerAr: 'مكتب رئيس أمن المعلومات + مسؤول حماية البيانات',
    slaDays: 10,
    exitGateEn: 'ISO 27001 / SOC 2 / NCA ECC evidence collected, DPA reviewed, cross-border transfer mechanism agreed.',
    exitGateAr: 'تجميع أدلة ISO 27001 / SOC 2 / ECC، مراجعة اتفاقية معالجة البيانات، الاتفاق على آلية النقل عبر الحدود.',
    next: 'CONTRACT',
  },
  CONTRACT: {
    stage: 'CONTRACT',
    labelEn: 'Contracting',                 labelAr: 'التعاقد',
    ownerEn: 'Legal + Procurement',         ownerAr: 'القانوني + المشتريات',
    slaDays: 14,
    exitGateEn: 'MSA + DPA + SLA signed, exit / off-boarding clause included.',
    exitGateAr: 'اتفاقية الخدمات الرئيسية + معالجة البيانات + اتفاقية مستوى الخدمة موقَّعة، مع بند الخروج / إنهاء التعاقد.',
    next: 'EXEC_APPROVAL',
  },
  EXEC_APPROVAL: {
    stage: 'EXEC_APPROVAL',
    labelEn: 'Executive approval',          labelAr: 'الاعتماد التنفيذي',
    ownerEn: 'Risk Committee / CEO',        ownerAr: 'لجنة المخاطر / الرئيس التنفيذي',
    slaDays: 5,
    exitGateEn: 'Tier 1: board / CEO sign-off. Tier 2: Risk Committee. Tier 3-4: TPRM Lead.',
    exitGateAr: 'الدرجة 1: اعتماد المجلس / الرئيس التنفيذي. الدرجة 2: لجنة المخاطر. الدرجة 3–4: قائد فريق المخاطر.',
    next: 'ACTIVE',
  },
  ACTIVE: {
    stage: 'ACTIVE',
    labelEn: 'Active',                      labelAr: 'نشط',
    ownerEn: 'Vendor relationship owner',   ownerAr: 'مالك العلاقة مع المورد',
    slaDays: 0,
    exitGateEn: 'Operating; reassessment cadence runs in parallel.',
    exitGateAr: 'تعمل العلاقة؛ تدور دورة إعادة التقييم بالتوازي.',
    next: 'OFFBOARDING',
  },
  OFFBOARDING: {
    stage: 'OFFBOARDING',
    labelEn: 'Off-boarding',                labelAr: 'إنهاء التعاقد',
    ownerEn: 'TPRM + IT',                   ownerAr: 'مخاطر الأطراف الثالثة + تقنية المعلومات',
    slaDays: 30,
    exitGateEn: 'Access revoked, data returned/destroyed (PDPL Art. 19), assets recovered.',
    exitGateAr: 'إلغاء الوصول، إرجاع/إتلاف البيانات (المادة 19 من نظام حماية البيانات)، استرجاع الأصول.',
    next: null,
  },
};

export const ONBOARDING_ORDER: OnboardingStage[] = [
  'INTAKE', 'TIER_ASSESSMENT', 'DUE_DILIGENCE', 'SECURITY_REVIEW', 'CONTRACT', 'EXEC_APPROVAL', 'ACTIVE',
];

// ── Cadence helpers ─────────────────────────────────────────────────────

/** Compute the next reassessment date given a tier and the date of the
 *  most recent assessment (or activation if no prior assessment). */
export function nextReviewDate(tier: VendorTier, lastAssessedAt: string | Date): Date | null {
  const meta = VENDOR_TIERS[tier];
  if (meta.cadenceMonths === 0) return null;
  const base = typeof lastAssessedAt === 'string' ? new Date(lastAssessedAt) : lastAssessedAt;
  const next = new Date(base);
  next.setMonth(next.getMonth() + meta.cadenceMonths);
  return next;
}

/** True when a vendor is past due for reassessment. */
export function isReviewOverdue(tier: VendorTier, lastAssessedAt: string | Date, now: Date = new Date()): boolean {
  const next = nextReviewDate(tier, lastAssessedAt);
  if (!next) return false;
  return next.getTime() < now.getTime();
}

/** Days until next review (negative = overdue). */
export function daysUntilReview(tier: VendorTier, lastAssessedAt: string | Date, now: Date = new Date()): number | null {
  const next = nextReviewDate(tier, lastAssessedAt);
  if (!next) return null;
  return Math.round((next.getTime() - now.getTime()) / 86_400_000);
}

/** Bucketing helper for the TPRM dashboard. */
export type ReviewBucket = 'OVERDUE' | 'DUE_SOON' | 'ON_TRACK' | 'AD_HOC';

export function reviewBucket(tier: VendorTier, lastAssessedAt: string | Date, now: Date = new Date()): ReviewBucket {
  if (VENDOR_TIERS[tier].cadenceMonths === 0) return 'AD_HOC';
  const days = daysUntilReview(tier, lastAssessedAt, now)!;
  if (days < 0)  return 'OVERDUE';
  if (days < 60) return 'DUE_SOON';
  return 'ON_TRACK';
}

// ── Exec-approval gate (matches EXEC_APPROVAL exit gate) ────────────────

export type ExecApprover = 'BOARD_OR_CEO' | 'RISK_COMMITTEE' | 'TPRM_LEAD';

export function requiredApprover(tier: VendorTier): ExecApprover {
  switch (tier) {
    case 'TIER_1': return 'BOARD_OR_CEO';
    case 'TIER_2': return 'RISK_COMMITTEE';
    default:       return 'TPRM_LEAD';
  }
}
