// ESG / Saudi Green Initiative — Sprint 6 (C2) + production hardening.
//
// CMA Corporate Governance Regulations Articles 91-94 (2024 amendment)
// require Saudi listed companies to disclose ESG performance against the
// TCFD framework with the annual report. Saudi Green Initiative commits
// the Kingdom to net-zero by 2060, with interim Scope 1+2 reduction
// targets per sector. Tadawul ESG Disclosure Guidelines extend this to
// non-listed entities on a comply-or-explain basis.
//
// This module models:
//   • Multi-year emissions time-series (Scope 1 / 2 / 3, sub-categorised)
//   • GHG Protocol-aligned emission factors for the Saudi grid + key fuels
//   • TCFD pillars (Governance, Strategy, Risk Mgmt, Metrics & Targets)
//   • CMA Article 91-94 disclosure checklist (bilingual)
//   • SGI sectoral commitments (energy, industry, transport, waste,
//     forestry — mirrors SGI's own taxonomy)
//   • Vendor ESG survey (Scope 3 supplier engagement)
//   • Persistence overlay so calculator entries + survey responses survive
//     across sessions

export type EsgPillar = 'GOVERNANCE' | 'STRATEGY' | 'RISK_MGMT' | 'METRICS_TARGETS';

// ─── Multi-year emissions time-series ──────────────────────────────────────

export interface EmissionYearRecord {
  year: number;
  scope1: number;       // tCO2e
  scope2: number;
  scope3: number;
  /** Coverage % of operations measured for Scope 3. */
  scope3CoveragePct: number;
  /** Third-party verification status. */
  verifiedScope1: boolean;
  verifiedScope2: boolean;
  verifiedScope3: boolean;
}

export const EMISSIONS_TIMESERIES: EmissionYearRecord[] = [
  { year: 2020, scope1: 17_810, scope2: 51_200, scope3: 168_900, scope3CoveragePct: 35, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
  { year: 2021, scope1: 16_950, scope2: 48_410, scope3: 158_220, scope3CoveragePct: 41, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
  { year: 2022, scope1: 16_200, scope2: 46_120, scope3: 152_330, scope3CoveragePct: 48, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
  { year: 2023, scope1: 15_510, scope2: 44_250, scope3: 147_910, scope3CoveragePct: 55, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
  { year: 2024, scope1: 14_980, scope2: 42_310, scope3: 145_010, scope3CoveragePct: 60, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
  { year: 2025, scope1: 14_220, scope2: 38_910, scope3: 142_480, scope3CoveragePct: 67, verifiedScope1: true, verifiedScope2: true, verifiedScope3: false },
];

/** Most recent measured year — used for KPI surfaces. */
export const CURRENT_YEAR_RECORD = EMISSIONS_TIMESERIES[EMISSIONS_TIMESERIES.length - 1];

/** Year-over-year % change between latest two recorded years. */
export function yoyChange(scope: 1 | 2 | 3): number {
  if (EMISSIONS_TIMESERIES.length < 2) return 0;
  const prev = EMISSIONS_TIMESERIES[EMISSIONS_TIMESERIES.length - 2];
  const curr = EMISSIONS_TIMESERIES[EMISSIONS_TIMESERIES.length - 1];
  const prevVal = scope === 1 ? prev.scope1 : scope === 2 ? prev.scope2 : prev.scope3;
  const currVal = scope === 1 ? curr.scope1 : scope === 2 ? curr.scope2 : curr.scope3;
  if (prevVal === 0) return 0;
  return Math.round(((currVal - prevVal) / prevVal) * 1000) / 10;
}

// ─── GHG Protocol emission factors (Saudi-context) ──────────────────────────

export interface EmissionFactor {
  code: string;
  /** Activity unit (e.g. "kWh", "litre", "tonne"). */
  unit: string;
  /** kgCO2e per unit. */
  kgCo2ePerUnit: number;
  scope: 1 | 2 | 3;
  category: string;
  nameEn: string;
  nameAr: string;
  /** Source authority for the factor. */
  source: string;
}

/**
 * Saudi-context emission factors. Sources:
 *   • Saudi Electricity Co. (SEC) grid factor 2024 — 0.689 kgCO2e/kWh
 *   • IPCC 2006 stationary combustion (diesel, natural gas)
 *   • DEFRA 2024 transport — air business class long-haul
 *   • EPA mobile combustion — petrol passenger car
 *   • DEFRA 2024 — UK average refrigerant leakage (used as proxy until SDAIA
 *     publishes Saudi-specific F-gas factors).
 */
export const EMISSION_FACTORS: EmissionFactor[] = [
  // Scope 1 — direct
  { code: 'EF-DIESEL',     unit: 'litre', kgCo2ePerUnit: 2.687, scope: 1, category: 'STATIONARY_FUEL', nameEn: 'Diesel (stationary combustion)', nameAr: 'ديزل (احتراق ثابت)', source: 'IPCC 2006 Vol.2 Ch.2' },
  { code: 'EF-NATGAS',     unit: 'm³',    kgCo2ePerUnit: 2.022, scope: 1, category: 'STATIONARY_FUEL', nameEn: 'Natural gas',                    nameAr: 'غاز طبيعي',         source: 'IPCC 2006 Vol.2 Ch.2' },
  { code: 'EF-PETROL-CAR', unit: 'litre', kgCo2ePerUnit: 2.296, scope: 1, category: 'MOBILE',          nameEn: 'Petrol — passenger fleet',       nameAr: 'بنزين — أسطول الركاب', source: 'EPA mobile combustion' },
  { code: 'EF-REFRIG',     unit: 'kg',    kgCo2ePerUnit: 1430,   scope: 1, category: 'F_GAS',           nameEn: 'Refrigerant leakage (R-134a)',   nameAr: 'تسرّب الغازات المبرّدة (R-134a)', source: 'IPCC AR4 GWP' },
  // Scope 2 — purchased electricity
  { code: 'EF-SEC-GRID',   unit: 'kWh',   kgCo2ePerUnit: 0.689, scope: 2, category: 'ELECTRICITY',     nameEn: 'SEC grid electricity (Saudi)',   nameAr: 'كهرباء شبكة SEC (السعودية)',     source: 'Saudi Electricity Co. 2024' },
  // Scope 3 — value chain
  { code: 'EF-AIR-BIZ',     unit: 'km',    kgCo2ePerUnit: 0.394, scope: 3, category: 'BUSINESS_TRAVEL', nameEn: 'Air travel — business long-haul', nameAr: 'سفر جوي — درجة رجال الأعمال طويل المدى', source: 'DEFRA 2024' },
  { code: 'EF-AIR-ECO',     unit: 'km',    kgCo2ePerUnit: 0.149, scope: 3, category: 'BUSINESS_TRAVEL', nameEn: 'Air travel — economy long-haul',  nameAr: 'سفر جوي — اقتصادية طويلة المدى', source: 'DEFRA 2024' },
  { code: 'EF-WASTE-LF',    unit: 'tonne', kgCo2ePerUnit: 458,   scope: 3, category: 'WASTE',           nameEn: 'Mixed waste to landfill',         nameAr: 'نفايات مختلطة إلى المكبّ',     source: 'DEFRA 2024' },
];

// ─── TCFD disclosure checklist ─────────────────────────────────────────────

export interface TcfdItem {
  pillar: EsgPillar;
  recommendationCode: string;
  titleEn: string;
  titleAr: string;
  status: 'DISCLOSED' | 'PARTIAL' | 'NOT_DISCLOSED';
  evidenceRef?: string;
}

export const TCFD_ITEMS: TcfdItem[] = [
  // Governance
  { pillar: 'GOVERNANCE', recommendationCode: 'a',
    titleEn: 'Board oversight of climate risks',
    titleAr: 'إشراف المجلس على المخاطر المناخية',
    status: 'DISCLOSED', evidenceRef: 'Board ESG Committee charter' },
  { pillar: 'GOVERNANCE', recommendationCode: 'b',
    titleEn: 'Management role in assessing climate risks',
    titleAr: 'دور الإدارة في تقييم المخاطر المناخية',
    status: 'DISCLOSED', evidenceRef: 'CRO mandate v2.0' },
  // Strategy
  { pillar: 'STRATEGY', recommendationCode: 'a',
    titleEn: 'Climate-related risks + opportunities (short / medium / long term)',
    titleAr: 'المخاطر والفرص المناخية (قصير / متوسط / طويل الأمد)',
    status: 'DISCLOSED', evidenceRef: '2026 Annual Report §4' },
  { pillar: 'STRATEGY', recommendationCode: 'b',
    titleEn: 'Impact on business, strategy, financial planning',
    titleAr: 'الأثر على الأعمال والاستراتيجية والتخطيط المالي',
    status: 'PARTIAL', evidenceRef: 'Business plan 2026 (climate add-on)' },
  { pillar: 'STRATEGY', recommendationCode: 'c',
    titleEn: 'Resilience under different climate scenarios (incl. 2°C)',
    titleAr: 'الصمود تحت سيناريوهات مناخية مختلفة (بما في ذلك 2°م)',
    status: 'PARTIAL', evidenceRef: 'Scenario analysis FY2026' },
  // Risk management
  { pillar: 'RISK_MGMT', recommendationCode: 'a',
    titleEn: 'Process for identifying climate risks',
    titleAr: 'إجراء تحديد المخاطر المناخية',
    status: 'DISCLOSED', evidenceRef: 'POL-CLIMATE-RISK-001' },
  { pillar: 'RISK_MGMT', recommendationCode: 'b',
    titleEn: 'Process for managing climate risks',
    titleAr: 'إجراء إدارة المخاطر المناخية',
    status: 'DISCLOSED', evidenceRef: 'Risk appetite — climate band' },
  { pillar: 'RISK_MGMT', recommendationCode: 'c',
    titleEn: 'Integration into overall risk management',
    titleAr: 'التكامل مع إدارة المخاطر الشاملة',
    status: 'DISCLOSED' },
  // Metrics & targets
  { pillar: 'METRICS_TARGETS', recommendationCode: 'a',
    titleEn: 'Metrics for climate risks + opportunities',
    titleAr: 'مقاييس المخاطر والفرص المناخية',
    status: 'DISCLOSED', evidenceRef: 'GHG inventory 2025' },
  { pillar: 'METRICS_TARGETS', recommendationCode: 'b',
    titleEn: 'Scope 1, 2, 3 emissions disclosure',
    titleAr: 'الإفصاح عن انبعاثات النطاقات 1 و 2 و 3',
    status: 'PARTIAL', evidenceRef: 'Scope 3 — 67% coverage' },
  { pillar: 'METRICS_TARGETS', recommendationCode: 'c',
    titleEn: 'Targets to manage climate risks + performance',
    titleAr: 'الأهداف لإدارة المخاطر المناخية والأداء',
    status: 'DISCLOSED', evidenceRef: 'Net-zero 2050 commitment' },
];

// ─── CMA Articles 91-94 (Saudi listed-company ESG disclosure) ──────────────

export interface CmaArticle {
  /** Article reference per CMA Corporate Governance Regulations 2024 amendment. */
  article: string;
  /** Sub-clause where applicable. */
  clause?: string;
  titleEn: string;
  titleAr: string;
  /** What needs to be disclosed in the annual report. */
  requirementEn: string;
  requirementAr: string;
  status: 'DISCLOSED' | 'PARTIAL' | 'NOT_DISCLOSED' | 'NOT_APPLICABLE';
  evidenceRef?: string;
}

export const CMA_ARTICLES: CmaArticle[] = [
  {
    article: '91', clause: '(a)',
    titleEn: 'Board oversight of sustainability + ESG',
    titleAr: 'إشراف المجلس على الاستدامة والحوكمة البيئية والاجتماعية',
    requirementEn: 'Disclose the board\'s role in setting ESG strategy, approving climate-related targets, and reviewing performance against them.',
    requirementAr: 'الإفصاح عن دور المجلس في وضع استراتيجية الحوكمة البيئية والاجتماعية، واعتماد الأهداف المتعلّقة بالمناخ، ومراجعة الأداء.',
    status: 'DISCLOSED', evidenceRef: 'Annual Report 2025 §3.4',
  },
  {
    article: '91', clause: '(b)',
    titleEn: 'Sustainability committee composition',
    titleAr: 'تشكيل لجنة الاستدامة',
    requirementEn: 'Disclose the composition + mandate of the sustainability / ESG committee, with at least one independent director.',
    requirementAr: 'الإفصاح عن تشكيل ومهام لجنة الاستدامة / الحوكمة البيئية والاجتماعية، بحضور عضو مستقل واحد على الأقل.',
    status: 'DISCLOSED', evidenceRef: 'Committee Charter v3.0',
  },
  {
    article: '92', clause: '(a)',
    titleEn: 'Material ESG topics — assessment',
    titleAr: 'تقييم الموضوعات الجوهرية للحوكمة البيئية والاجتماعية',
    requirementEn: 'Conduct + disclose a double-materiality assessment of ESG topics, identifying which are most relevant to the business model.',
    requirementAr: 'إجراء والإفصاح عن تقييم الأهمية النسبية المزدوجة لموضوعات الحوكمة البيئية والاجتماعية، وتحديد الأكثر صلة بنموذج الأعمال.',
    status: 'DISCLOSED', evidenceRef: 'Materiality matrix 2025',
  },
  {
    article: '92', clause: '(b)',
    titleEn: 'Climate-related risks + opportunities',
    titleAr: 'المخاطر والفرص المتعلّقة بالمناخ',
    requirementEn: 'Disclose climate-related risks (transition + physical) and opportunities, mapped to short / medium / long-term horizons per TCFD.',
    requirementAr: 'الإفصاح عن المخاطر المتعلّقة بالمناخ (الانتقال والمادية) والفرص، بحسب الآفاق القصيرة والمتوسطة والطويلة وفق TCFD.',
    status: 'PARTIAL', evidenceRef: 'Risk register — climate band',
  },
  {
    article: '93', clause: '(a)',
    titleEn: 'GHG emissions — Scope 1 + 2',
    titleAr: 'انبعاثات الغازات الدفيئة — النطاقان 1 و 2',
    requirementEn: 'Disclose Scope 1 and Scope 2 emissions in tCO2e, with year-over-year comparison and target trajectory to 2030.',
    requirementAr: 'الإفصاح عن انبعاثات النطاقين 1 و 2 بالطن المكافئ ثاني أكسيد الكربون، مع المقارنة السنوية والمسار حتى 2030.',
    status: 'DISCLOSED', evidenceRef: 'GHG inventory 2025',
  },
  {
    article: '93', clause: '(b)',
    titleEn: 'GHG emissions — Scope 3',
    titleAr: 'انبعاثات النطاق 3',
    requirementEn: 'Disclose Scope 3 emissions for the most material categories. Where coverage is limited, disclose the % of operations covered.',
    requirementAr: 'الإفصاح عن انبعاثات النطاق 3 للفئات الأكثر جوهرية. إن كانت التغطية محدودة، الإفصاح عن النسبة المُشمَلة.',
    status: 'PARTIAL', evidenceRef: 'Scope 3 — 67% coverage',
  },
  {
    article: '93', clause: '(c)',
    titleEn: 'Energy intensity + renewable share',
    titleAr: 'كثافة الطاقة وحصة المتجدّدة',
    requirementEn: 'Disclose energy consumption (MWh) and the share of renewable energy in the mix, aligned to SGI 2030 50% target.',
    requirementAr: 'الإفصاح عن استهلاك الطاقة (ميغاواط ساعة) وحصة الطاقة المتجدّدة، بمحاذاة هدف SGI 2030 (50%).',
    status: 'DISCLOSED', evidenceRef: 'Energy mix 2025',
  },
  {
    article: '93', clause: '(d)',
    titleEn: 'Water + waste metrics',
    titleAr: 'مؤشرات المياه والنفايات',
    requirementEn: 'Disclose water withdrawal, waste generated, and waste-diversion rate (recycle + reuse) where material.',
    requirementAr: 'الإفصاح عن سحب المياه والنفايات المُولَّدة ونسبة التحويل (إعادة التدوير + الاستخدام) عند الجوهرية.',
    status: 'DISCLOSED', evidenceRef: 'Operations report 2025',
  },
  {
    article: '94', clause: '(a)',
    titleEn: 'Saudization (Nitaqat) compliance',
    titleAr: 'الالتزام بالسعودة (نطاقات)',
    requirementEn: 'Disclose Nitaqat tier (Platinum / Green / Yellow), Saudization rate, and improvements vs prior year.',
    requirementAr: 'الإفصاح عن فئة نطاقات (بلاتيني / أخضر / أصفر) ونسبة السعودة والتحسّن مقارنة بالعام السابق.',
    status: 'DISCLOSED', evidenceRef: 'HRSD 2025 Q4',
  },
  {
    article: '94', clause: '(b)',
    titleEn: 'Female workforce participation',
    titleAr: 'مشاركة المرأة في القوى العاملة',
    requirementEn: 'Disclose female workforce participation %, leadership representation %, and gender pay-gap analysis.',
    requirementAr: 'الإفصاح عن نسبة مشاركة المرأة في العاملين، ونسبة التمثيل القيادي، وتحليل فجوة الأجور بين الجنسين.',
    status: 'DISCLOSED', evidenceRef: 'HR diversity report 2025',
  },
  {
    article: '94', clause: '(c)',
    titleEn: 'Health, safety + LTIFR',
    titleAr: 'الصحة والسلامة وLTIFR',
    requirementEn: 'Disclose lost-time injury frequency rate (LTIFR), fatalities, and total recordable incidents.',
    requirementAr: 'الإفصاح عن معدّل تكرار الإصابات المُفقِدة للوقت (LTIFR) والوفيات وإجمالي الحوادث.',
    status: 'PARTIAL', evidenceRef: 'HSSE report 2025',
  },
];

// ─── Saudi Green Initiative — sectoral commitments ─────────────────────────

// SGI-E — the static SGI_COMMITMENTS mock (+ its SgiSector/SgiCommitment types)
// was removed. The SGI register is now backend-driven (module/sust/sgi, screen
// ESG → SGI tab, client in lib/sustainabilitySgi.ts) with live progress, source
// lineage (SGI-C) and governance (SGI-A/B); the overview KPIs come from the live
// S-P dashboard aggregate (lib/esgDashboard.ts).

// ─── Vendor ESG survey (Scope 3 supplier engagement) ───────────────────────

export interface VendorEsgSurvey {
  /** Internal id. */
  id: string;
  vendorNameEn: string;
  vendorNameAr: string;
  /** SAR thousands annual spend with this vendor. */
  spendSarK: number;
  /** Survey status. */
  status: 'INVITED' | 'IN_PROGRESS' | 'SUBMITTED' | 'OVERDUE';
  /** Last update timestamp. */
  updatedAt: string;
  /** Self-reported Scope 1+2 emissions (tCO2e/yr). */
  reportedScope12?: number;
  /** Renewable energy share %. */
  renewablePct?: number;
  /** Saudization rate %. */
  saudizationPct?: number;
  /** Has a published Net-Zero target? */
  hasNetZeroTarget?: boolean;
  /** ISO 14001 certified? */
  iso14001?: boolean;
}

export const VENDOR_SURVEYS: VendorEsgSurvey[] = [
  { id: 'VES-001', vendorNameEn: 'Saudi Aramco IT Services',  vendorNameAr: 'خدمات تقنية أرامكو السعودية',  spendSarK: 12_500, status: 'SUBMITTED',   updatedAt: '2026-03-12', reportedScope12: 8_400, renewablePct: 24, saudizationPct: 78, hasNetZeroTarget: true,  iso14001: true },
  { id: 'VES-002', vendorNameEn: 'STC Cloud',                  vendorNameAr: 'سحابة STC',                     spendSarK: 4_500,  status: 'SUBMITTED',   updatedAt: '2026-03-04', reportedScope12: 3_120, renewablePct: 31, saudizationPct: 84, hasNetZeroTarget: true,  iso14001: true },
  { id: 'VES-003', vendorNameEn: 'NEOM Logistics',             vendorNameAr: 'نيوم للوجستيات',                spendSarK: 2_810,  status: 'IN_PROGRESS', updatedAt: '2026-04-22', reportedScope12: undefined, renewablePct: undefined, saudizationPct: undefined, hasNetZeroTarget: undefined, iso14001: undefined },
  { id: 'VES-004', vendorNameEn: 'Riyadh Cleaning Services',  vendorNameAr: 'الرياض لخدمات النظافة',         spendSarK: 1_200,  status: 'OVERDUE',     updatedAt: '2026-02-08', reportedScope12: undefined, renewablePct: undefined, saudizationPct: undefined, hasNetZeroTarget: undefined, iso14001: undefined },
  { id: 'VES-005', vendorNameEn: 'Saudi Catering Co.',         vendorNameAr: 'شركة التموين السعودية',         spendSarK: 1_080,  status: 'INVITED',     updatedAt: '2026-04-30', reportedScope12: undefined, renewablePct: undefined, saudizationPct: undefined, hasNetZeroTarget: undefined, iso14001: undefined },
  { id: 'VES-006', vendorNameEn: 'Bawan Services',             vendorNameAr: 'بوان للخدمات',                  spendSarK: 940,    status: 'SUBMITTED',   updatedAt: '2026-03-19', reportedScope12: 1_220, renewablePct: 8, saudizationPct: 62, hasNetZeroTarget: false, iso14001: false },
];

// ─── Persistence (calculator entries + survey responses) ────────────────────

export interface CalculatorEntry {
  id: string;
  factorCode: string;
  amount: number;
  /** ISO date when activity occurred. */
  occurredAt: string;
  /** Bilingual note. */
  noteEn?: string;
  noteAr?: string;
  /** Computed at entry-time so the row is stable. */
  computedKgCo2e: number;
}

const CALC_KEY = 'grc.esg.calc.v1';
const SURVEY_KEY = 'grc.esg.survey.v1';
const EVENT = 'grc.esg.changed';

export function loadCalcEntries(): CalculatorEntry[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(CALC_KEY);
    return raw ? JSON.parse(raw) as CalculatorEntry[] : [];
  } catch { return []; }
}

export function appendCalcEntry(entry: Omit<CalculatorEntry, 'id' | 'computedKgCo2e'>): CalculatorEntry {
  const factor = EMISSION_FACTORS.find(f => f.code === entry.factorCode);
  if (!factor) throw new Error(`Unknown factor: ${entry.factorCode}`);
  const computed = entry.amount * factor.kgCo2ePerUnit;
  const full: CalculatorEntry = {
    ...entry,
    id: `CALC-${Date.now()}-${Math.random().toString(36).slice(2, 6)}`,
    computedKgCo2e: Math.round(computed * 100) / 100,
  };
  const next = [...loadCalcEntries(), full];
  try { window.localStorage.setItem(CALC_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
  return full;
}

export function deleteCalcEntry(id: string): void {
  const next = loadCalcEntries().filter(e => e.id !== id);
  try { window.localStorage.setItem(CALC_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function loadSurveyOverrides(): Partial<Record<string, Partial<VendorEsgSurvey>>> {
  if (typeof window === 'undefined') return {};
  try {
    const raw = window.localStorage.getItem(SURVEY_KEY);
    return raw ? JSON.parse(raw) : {};
  } catch { return {}; }
}

export function saveSurveyOverride(id: string, patch: Partial<VendorEsgSurvey>): void {
  const overrides = loadSurveyOverrides();
  overrides[id] = { ...(overrides[id] ?? {}), ...patch };
  try { window.localStorage.setItem(SURVEY_KEY, JSON.stringify(overrides)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function effectiveSurveys(): VendorEsgSurvey[] {
  const overrides = loadSurveyOverrides();
  return VENDOR_SURVEYS.map(v => ({ ...v, ...(overrides[v.id] ?? {}) }));
}

export function subscribeEsg(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if (e.key === CALC_KEY || e.key === SURVEY_KEY) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

// ─── ESG annex generator (printable text) ─────────────────────────────────

export function buildEsgAnnex(orgEn: string, orgAr: string): string {
  const lines: string[] = [];
  const div = '─'.repeat(64);
  const curr = CURRENT_YEAR_RECORD;
  lines.push(div);
  lines.push('ESG / SUSTAINABILITY ANNEX — ANNUAL REPORT');
  lines.push('ملحق الاستدامة والحوكمة البيئية — التقرير السنوي');
  lines.push(div);
  lines.push('');
  lines.push(`Reporting entity: ${orgEn}`);
  lines.push(`الجهة المُعِدّة  : ${orgAr}`);
  lines.push(`Reporting year: ${curr.year}`);
  lines.push(`Generated     : ${new Date().toISOString()}`);
  lines.push('');
  lines.push('1. GHG EMISSIONS · انبعاثات الغازات الدفيئة');
  lines.push(`   Scope 1: ${curr.scope1.toLocaleString()} tCO2e (verified: ${curr.verifiedScope1 ? 'YES' : 'no'})`);
  lines.push(`   Scope 2: ${curr.scope2.toLocaleString()} tCO2e (verified: ${curr.verifiedScope2 ? 'YES' : 'no'})`);
  lines.push(`   Scope 3: ${curr.scope3.toLocaleString()} tCO2e (coverage ${curr.scope3CoveragePct}%, verified: ${curr.verifiedScope3 ? 'YES' : 'no'})`);
  lines.push(`   Total  : ${(curr.scope1 + curr.scope2 + curr.scope3).toLocaleString()} tCO2e`);
  lines.push(`   YoY    : Scope 1 ${yoyChange(1)}%, Scope 2 ${yoyChange(2)}%, Scope 3 ${yoyChange(3)}%`);
  lines.push('');
  // SGI-E — SGI commitments live in the backend register (ESG → SGI tab) with
  // their own bilingual XLSX export; they are no longer rendered from a mock here.
  lines.push('2. CMA ARTICLES 91-94 DISCLOSURE · إفصاح المواد 91-94 من لائحة الحوكمة');
  CMA_ARTICLES.forEach(a => {
    lines.push(`   Art. ${a.article}${a.clause ?? ''} [${a.status.padEnd(14)}] ${a.titleEn} / ${a.titleAr}`);
    if (a.evidenceRef) lines.push(`     evidence: ${a.evidenceRef}`);
  });
  lines.push('');
  lines.push('3. TCFD ALIGNMENT · المحاذاة مع TCFD');
  TCFD_ITEMS.forEach(t => {
    lines.push(`   [${t.pillar.padEnd(15)}] (${t.recommendationCode}) [${t.status.padEnd(14)}] ${t.titleEn}`);
  });
  lines.push('');
  lines.push(div);
  lines.push(`Generated by TSS GRC Platform`);
  lines.push(div);
  return lines.join('\n');
}

/** Export P5 — the same annex data as tabular sheets for a branded .xlsx
 *  (POST /api/v1/reporting/packet.xlsx). A "Status" header triggers the
 *  workbook's status conditional-formatting. */
export function buildEsgAnnexSheets(): { name: string; headers: string[]; rows: string[][] }[] {
  const curr = CURRENT_YEAR_RECORD;
  return [
    {
      name: 'GHG Emissions',
      headers: ['Scope', 'tCO2e', 'Verified', 'Coverage %', 'YoY %'],
      rows: [
        ['Scope 1', String(curr.scope1), curr.verifiedScope1 ? 'Yes' : 'No', '100', String(yoyChange(1))],
        ['Scope 2', String(curr.scope2), curr.verifiedScope2 ? 'Yes' : 'No', '100', String(yoyChange(2))],
        ['Scope 3', String(curr.scope3), curr.verifiedScope3 ? 'Yes' : 'No', String(curr.scope3CoveragePct), String(yoyChange(3))],
        ['Total', String(curr.scope1 + curr.scope2 + curr.scope3), '', '', ''],
      ],
    },
    // SGI-E — the Saudi Green Initiative sheet was dropped from this mock annex;
    // the live SGI register exports its own bilingual XLSX (SGI tab → Export).
    {
      name: 'CMA Articles 91-94',
      headers: ['Article', 'Title', 'Status', 'Evidence'],
      rows: CMA_ARTICLES.map(a => [`${a.article}${a.clause ?? ''}`, a.titleEn, a.status, a.evidenceRef ?? '']),
    },
    {
      name: 'TCFD Alignment',
      headers: ['Pillar', 'Recommendation', 'Status', 'Title'],
      rows: TCFD_ITEMS.map(t => [t.pillar, t.recommendationCode, t.status, t.titleEn]),
    },
  ];
}

// ─── Aggregation ───────────────────────────────────────────────────────────

export const PILLAR_LABEL: Record<EsgPillar, { en: string; ar: string }> = {
  GOVERNANCE:      { en: 'Governance',          ar: 'الحوكمة' },
  STRATEGY:        { en: 'Strategy',            ar: 'الاستراتيجية' },
  RISK_MGMT:       { en: 'Risk management',     ar: 'إدارة المخاطر' },
  METRICS_TARGETS: { en: 'Metrics & targets',   ar: 'المقاييس والأهداف' },
};

export const STATUS_TONE: Record<TcfdItem['status'], 'success' | 'warning' | 'danger'> = {
  DISCLOSED:      'success',
  PARTIAL:        'warning',
  NOT_DISCLOSED:  'danger',
};

export const STATUS_LABEL: Record<TcfdItem['status'] | CmaArticle['status'], { en: string; ar: string }> = {
  DISCLOSED:       { en: 'Disclosed',        ar: 'مُفصَح' },
  PARTIAL:         { en: 'Partial',          ar: 'جزئي' },
  NOT_DISCLOSED:   { en: 'Not disclosed',    ar: 'غير مُفصَح' },
  NOT_APPLICABLE:  { en: 'N/A',              ar: 'لا ينطبق' },
};

// SGI-E — SECTOR_LABEL (keyed by the removed SgiSector mock type) is gone; the
// live SGI tab labels sectors via sgiSectorLabel() in lib/sustainabilitySgi.ts.

export const SURVEY_TONE: Record<VendorEsgSurvey['status'], 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  SUBMITTED:   'success',
  IN_PROGRESS: 'info',
  INVITED:     'neutral',
  OVERDUE:     'danger',
};

export const SURVEY_LABEL: Record<VendorEsgSurvey['status'], { en: string; ar: string }> = {
  SUBMITTED:   { en: 'Submitted',   ar: 'مُقدَّم' },
  IN_PROGRESS: { en: 'In progress', ar: 'قيد التنفيذ' },
  INVITED:     { en: 'Invited',     ar: 'مدعوّ' },
  OVERDUE:     { en: 'Overdue',     ar: 'متأخّر' },
};

export interface EsgStats {
  totalEmissions: number;
  scope1: number;
  scope2: number;
  scope3: number;
  yoyTotal: number;
  tcfdTotal: number;
  tcfdDisclosed: number;
  tcfdCoveragePct: number;
  cmaTotal: number;
  cmaDisclosed: number;
  cmaCoveragePct: number;
  sgiOnTrackCount: number;
  sgiTotal: number;
  surveyTotal: number;
  surveySubmitted: number;
  surveyOverdue: number;
}

// SGI-E — aggregateEsg() (the static ESG-overview mock) was removed; the
// Overview KPI strip is fed by the live dashboard aggregate via
// mapDashboardToStats() in lib/esgDashboard.ts. EsgStats (above) is retained as
// the shared shape that mapper targets.

// Legacy exports kept for backwards compatibility with existing page code.
export const EMISSIONS = [
  { scope: 1 as const, tco2e: CURRENT_YEAR_RECORD.scope1, yoyChange: yoyChange(1), coveragePct: 100, verified: CURRENT_YEAR_RECORD.verifiedScope1 },
  { scope: 2 as const, tco2e: CURRENT_YEAR_RECORD.scope2, yoyChange: yoyChange(2), coveragePct: 100, verified: CURRENT_YEAR_RECORD.verifiedScope2 },
  { scope: 3 as const, tco2e: CURRENT_YEAR_RECORD.scope3, yoyChange: yoyChange(3), coveragePct: CURRENT_YEAR_RECORD.scope3CoveragePct, verified: CURRENT_YEAR_RECORD.verifiedScope3 },
];
