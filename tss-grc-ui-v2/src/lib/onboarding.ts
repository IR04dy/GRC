// Onboarding wizard — Track A (sales-readiness).
//
// First-run setup that takes a fresh tenant from "logged in for the first time"
// to "ready to use the platform" in under 5 minutes:
//
//   Step 1 · Organisation profile  — name (EN/AR), sector, size, regulator scope
//   Step 2 · Framework picker      — pre-checked from the sector preset
//   Step 3 · Invite users          — email + role rows (kept locally; backend
//                                    integration comes via SCIM in prod)
//   Step 4 · First-run tour        — deep-links into Dashboard / Risk / Comp.
//
// State is stored locally so the wizard can be paused + resumed. When the
// final step is confirmed, an `installedAt` timestamp marks completion and
// the production-readiness checklist + dashboard banner pick that up.

export type Sector =
  | 'banking' | 'insurance' | 'capital-markets' | 'telecom'
  | 'healthcare' | 'government' | 'energy' | 'retail-other';

export type OrgSize = 'sme' | 'mid' | 'enterprise' | 'group';

export interface FrameworkOption {
  /** Stable code used as primary key in the persistence layer. */
  code: string;
  /** Display name (EN). */
  nameEn: string;
  /** Display name (AR). */
  nameAr: string;
  /** Issuing regulator / authority. */
  regulator: string;
  /** Short rationale shown next to the checkbox. */
  blurbEn: string;
  blurbAr: string;
  /** Sectors that should auto-select this framework. */
  recommendedFor: Sector[];
}

/** All installable framework packs available at sign-up time. */
export const FRAMEWORK_OPTIONS: FrameworkOption[] = [
  {
    code: 'NCA-ECC-V3',
    nameEn: 'NCA ECC v3 (Essential Cybersecurity Controls)',
    nameAr: 'الضوابط الأساسية للأمن السيبراني — الهيئة الوطنية للأمن السيبراني (الإصدار الثالث)',
    regulator: 'NCA',
    blurbEn: 'Mandatory baseline for any Saudi entity that processes citizen data or critical infrastructure.',
    blurbAr: 'إلزامي لكل جهة سعودية تعالج بيانات المواطنين أو ترتبط بالبنية التحتية الحساسة.',
    recommendedFor: ['banking', 'insurance', 'capital-markets', 'telecom', 'healthcare', 'government', 'energy', 'retail-other'],
  },
  {
    code: 'SAMA-CSF',
    nameEn: 'SAMA Cyber Security Framework',
    nameAr: 'إطار الأمن السيبراني — البنك المركزي السعودي (ساما)',
    regulator: 'SAMA',
    blurbEn: 'Required for all SAMA-licensed banks, insurers and payment service providers.',
    blurbAr: 'إلزامي لكل البنوك وشركات التأمين ومزوّدي خدمات الدفع المرخّصين من ساما.',
    recommendedFor: ['banking', 'insurance'],
  },
  {
    code: 'SAMA-OPRES',
    nameEn: 'SAMA Operational Resilience Framework',
    nameAr: 'إطار المرونة التشغيلية — ساما',
    regulator: 'SAMA',
    blurbEn: 'Business continuity, incident response and third-party concentration thresholds for financial firms.',
    blurbAr: 'استمرارية الأعمال والاستجابة للحوادث ومخاطر تركّز الأطراف الثالثة للجهات المالية.',
    recommendedFor: ['banking', 'insurance'],
  },
  {
    code: 'PDPL',
    nameEn: 'PDPL — Personal Data Protection Law',
    nameAr: 'نظام حماية البيانات الشخصية — هيئة سدايا',
    regulator: 'SDAIA',
    blurbEn: '72-hour breach notification, ROPA, DPIA and data subject rights for Saudi residents.',
    blurbAr: 'إشعار خرق خلال 72 ساعة، سجل أنشطة المعالجة، تقييم الأثر، وحقوق أصحاب البيانات.',
    recommendedFor: ['banking', 'insurance', 'capital-markets', 'telecom', 'healthcare', 'government', 'energy', 'retail-other'],
  },
  {
    code: 'CMA-CGR',
    nameEn: 'CMA Corporate Governance Regulations',
    nameAr: 'لائحة حوكمة الشركات — هيئة السوق المالية',
    regulator: 'CMA',
    blurbEn: 'Mandatory for Saudi listed companies — board composition, audit committee, related-party disclosure.',
    blurbAr: 'إلزامية للشركات المدرجة — تكوين المجلس، لجنة المراجعة، الإفصاح عن الأطراف ذات العلاقة.',
    recommendedFor: ['capital-markets'],
  },
  {
    code: 'CITC-CCRF',
    nameEn: 'CITC Cybersecurity Regulatory Framework',
    nameAr: 'إطار تنظيم الأمن السيبراني — هيئة الاتصالات والفضاء والتقنية',
    regulator: 'CITC',
    blurbEn: 'Telecom + cloud + ICT operators — incident reporting and licensee obligations.',
    blurbAr: 'مشغّلو الاتصالات والحوسبة السحابية وتقنية المعلومات — إشعارات الحوادث والتزامات المرخَّص له.',
    recommendedFor: ['telecom'],
  },
  {
    code: 'NCEMA',
    nameEn: 'NCEMA Business Continuity Standard',
    nameAr: 'معيار استمرارية الأعمال — المركز الوطني لإدارة الكوارث (نسعى)',
    regulator: 'NCEMA',
    blurbEn: 'Critical national infrastructure BCM — RTO/RPO targets and crisis activation criteria.',
    blurbAr: 'استمرارية أعمال البنى التحتية الحيوية — أهداف الاستعادة ومعايير تفعيل إدارة الأزمات.',
    recommendedFor: ['energy', 'telecom', 'healthcare', 'government'],
  },
  {
    code: 'SDAIA-AI',
    nameEn: 'SDAIA AI Ethics Principles',
    nameAr: 'مبادئ أخلاقيات الذكاء الاصطناعي — هيئة سدايا',
    regulator: 'SDAIA',
    blurbEn: 'Saudi AI governance — fairness, transparency, accountability and human oversight.',
    blurbAr: 'حوكمة الذكاء الاصطناعي — العدالة والشفافية والمساءلة والإشراف البشري.',
    recommendedFor: ['banking', 'insurance', 'telecom', 'healthcare', 'government'],
  },
  {
    code: 'ZATCA-EINV',
    nameEn: 'ZATCA E-Invoicing (Fatoora)',
    nameAr: 'الفوترة الإلكترونية — هيئة الزكاة والضريبة والجمارك (فاتورة)',
    regulator: 'ZATCA',
    blurbEn: 'Phase 2 integration — XML/UBL invoice format, QR codes and clearance API.',
    blurbAr: 'المرحلة الثانية — صيغة XML/UBL، رموز QR، وواجهة المُقاصّة.',
    recommendedFor: ['retail-other', 'energy', 'telecom'],
  },
  {
    code: 'ISO-27001',
    nameEn: 'ISO/IEC 27001:2022',
    nameAr: 'الآيزو 27001:2022',
    regulator: 'ISO',
    blurbEn: 'International ISMS standard — useful as a maturity overlay on top of NCA ECC.',
    blurbAr: 'معيار نظام إدارة أمن المعلومات — مفيد كطبقة نضج فوق ضوابط NCA ECC.',
    recommendedFor: ['banking', 'insurance', 'capital-markets', 'telecom', 'energy'],
  },
];

export interface InviteRow {
  email: string;
  /** Role code matching IAM / RBAC. */
  role: 'admin' | 'risk-manager' | 'compliance-officer' | 'auditor' | 'viewer';
}

export interface OnboardingState {
  /** Current step (0-3). */
  step: number;
  organisationNameEn: string;
  organisationNameAr: string;
  sector: Sector;
  size: OrgSize;
  /** Selected framework codes. */
  selectedFrameworks: string[];
  /** Pending user invitations. */
  invites: InviteRow[];
  /** ISO timestamp when the wizard was completed. Empty = still onboarding. */
  installedAt?: string;
}

const KEY = 'grc.onboarding.v1';
const EVENT = 'grc.onboarding.changed';

const DEFAULT_STATE: OnboardingState = {
  step: 0,
  organisationNameEn: '',
  organisationNameAr: '',
  sector: 'banking',
  size: 'mid',
  selectedFrameworks: [],
  invites: [],
};

export function loadOnboarding(): OnboardingState {
  if (typeof window === 'undefined') return DEFAULT_STATE;
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return DEFAULT_STATE;
    return { ...DEFAULT_STATE, ...JSON.parse(raw) };
  } catch { return DEFAULT_STATE; }
}

export function saveOnboarding(patch: Partial<OnboardingState>): OnboardingState {
  const current = loadOnboarding();
  const next: OnboardingState = { ...current, ...patch };
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
  return next;
}

export function resetOnboarding(): void {
  try { window.localStorage.removeItem(KEY); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribeOnboarding(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => { if (e.key === KEY) cb(); };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

/** Get the recommended framework codes for a sector. */
export function recommendedFrameworks(sector: Sector): string[] {
  return FRAMEWORK_OPTIONS
    .filter(f => f.recommendedFor.includes(sector))
    .map(f => f.code);
}

/** Has the wizard been completed at least once? */
export function isOnboarded(): boolean {
  return !!loadOnboarding().installedAt;
}

// ─── First-run tour deep-links ─────────────────────────────────────────────

export interface TourTarget {
  code: string;
  labelKey: string;
  descriptionKey: string;
  to: string;
}

export const TOUR_TARGETS: TourTarget[] = [
  { code: 'dashboard',  labelKey: 'onbTourDashboard',  descriptionKey: 'onbTourDashboardSub',  to: '/dashboard' },
  { code: 'risk',       labelKey: 'onbTourRisk',       descriptionKey: 'onbTourRiskSub',       to: '/risk' },
  { code: 'compliance', labelKey: 'onbTourCompliance', descriptionKey: 'onbTourComplianceSub', to: '/compliance' },
  { code: 'authority',  labelKey: 'onbTourAuthority',  descriptionKey: 'onbTourAuthoritySub',  to: '/authorities' },
  { code: 'board-pack', labelKey: 'onbTourBoardPack', descriptionKey: 'onbTourBoardPackSub',  to: '/board-pack' },
];
