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

/**
 * ⚠⚠ F2-435 — the fabricated CMA Art. 91-94 / TCFD ANNUAL-REPORT ANNEX was RETIRED from here.
 *
 * `buildEsgAnnex` and `buildEsgAnnexSheets` built an "ESG / SUSTAINABILITY ANNEX — ANNUAL REPORT"
 * — GHG Scope 1/2/3 with third-party verification flags, the CMA Articles 91-94 disclosure
 * statuses and the TCFD four-pillar alignment — from `EMISSIONS_TIMESERIES`, `CMA_ARTICLES` and
 * `TCFD_ITEMS`, module-level constants in a file that makes ZERO server calls. `/esg` emitted it
 * FOUR ways from its page header, on every tab: a `.txt` download, a print-to-PDF stamped
 * CONFIDENTIAL — SUSTAINABILITY, and branded `.xlsx`/`.docx` through `reportingApi.packet*`.
 *
 * 🔑🔑 The tell was in this file's own neighbours: `lib/sustainabilityCma.ts` says it "replaces
 * the static CMA_ARTICLES constant" and `EsgCmaTab`/`EsgTcfdTab` both say "promoted from the
 * static … constant to a live API-driven" register — V60 and V61. So both TABS were de-mocked and
 * the constants they were promoted OFF were left in place, still feeding the export. The
 * de-mocking stopped at the visible surface, which is why the page reads as finished
 * (F2-193: when you de-mock a store, grep for everything that summarised it — here, EXPORTED it).
 *
 * ⚙ Retired rather than rewired, per ADR-017 §D-5 and F2-199/F2-207: the REAL annex is a sibling
 * tab on the same page. `EsgAnnexTab` drives `lib/sustainabilityAnnex.ts` end to end —
 * `generateAnnex` → `submitReport` → `approveReport` → `publishReport` → `exportReport` — over
 * registers measured populated in BOTH databases (cma_disclosure 91, tcfd_item 85,
 * ghg_emission_record 41/40). A second annex surface for one statutory artefact is F2-196's own
 * defect, and this was the one with no lifecycle and no server.
 *
 * ⚠ What remains here is deliberate: `EMISSION_FACTORS` is the GHG-Protocol factor catalogue the
 * calculator needs (a reference table, not a register — F2-194's kept class), the `TcfdItem` /
 * `CmaArticle` TYPES are shared by the live clients, and the label/tone maps are read platform-
 * wide (`STATUS_TONE` at 84 sites). Do not re-add a corpus behind any of them.
 */

export type EsgPillar = 'GOVERNANCE' | 'STRATEGY' | 'RISK_MGMT' | 'METRICS_TARGETS';

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

