// TCFD Climate Scenarios + Scope-3 Financed Emissions API client —
// Sprint 11 (V186).
//
// Wraps /api/v1/climate/* — three endpoint groups:
//   • /scenarios     — TCFD scenarios + sector impacts + weighted-EL
//   • /financed-emissions — PCAF records + targets + roll-ups
//   • /report/annual — IFRS S2 disclosure pack
//
// Lifecycle:
//   Scenarios: DRAFT → IN_REVIEW → BOARD_APPROVED → ARCHIVED
//   Emissions: DRAFT → IN_REVIEW → APPROVED → PUBLISHED → ARCHIVED
//
// Saudi-native AR labels for scenario types, asset classes, lifecycle
// stages. The page reads these directly — no on-screen string lives in
// just one language.

import { api } from './api';
import type { BadgeTone } from '../components/ui/Badge';

// ─── TCFD scenarios ────────────────────────────────────────────────

export type ScenarioType = 'ORDERLY' | 'DISORDERLY' | 'HOT_HOUSE' | 'CUSTOM';

export type ReferenceFramework =
  | 'NGFS' | 'IEA_NZE_2050'
  | 'IPCC_RCP_2_6' | 'IPCC_RCP_4_5' | 'IPCC_RCP_8_5'
  | 'IFRS_S2' | 'CUSTOM';

export type ScenarioLifecycleStage =
  | 'DRAFT' | 'IN_REVIEW' | 'BOARD_APPROVED' | 'ARCHIVED';

export interface TcfdScenario {
  id: number;
  externalId: string;
  scenarioCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  scenarioType: ScenarioType;
  referenceFramework: ReferenceFramework;
  timeHorizonYears: number;
  globalWarmingCelsius: number | string;
  assumptionsJson?: string;
  portfolioAtRiskSarBillions?: number | string;
  expectedLossSarMillions?: number | string;
  probabilityWeight: number | string;
  lifecycleStage: ScenarioLifecycleStage;
  approvedByUserId?: number;
  approvedAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TcfdScenarioRequest {
  scenarioCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  scenarioType: ScenarioType;
  referenceFramework: ReferenceFramework;
  timeHorizonYears: number;
  globalWarmingCelsius: number | string;
  assumptionsJson?: string;
  // S-E: portfolioAtRisk + expectedLoss are DERIVED server-side from the
  // scenario's sector impacts (Σ exposure × PD-uplift × scenario factor) and
  // returned read-only on TcfdScenario — they are no longer request inputs.
  probabilityWeight?: number | string;
}

export interface TcfdSectorImpact {
  id: number;
  externalId: string;
  scenarioId: number;
  sectorCode: string;
  sectorNameEn: string;
  sectorNameAr?: string;
  exposureSarMillions: number | string;
  transitionRiskScore: number | string;
  physicalRiskScore: number | string;
  expectedPdUpliftBps: number;
  mitigantActionsEn?: string;
  mitigantActionsAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface TcfdSectorImpactRequest {
  sectorCode: string;
  sectorNameEn: string;
  sectorNameAr?: string;
  exposureSarMillions: number | string;
  transitionRiskScore: number | string;
  physicalRiskScore: number | string;
  expectedPdUpliftBps?: number;
  mitigantActionsEn?: string;
  mitigantActionsAr?: string;
}

export interface ProbabilityWeightedExpectedLoss {
  weightedExpectedLossSarMillions: number | string;
  totalProbabilityWeight: number | string;
  approvedScenarioCount: number;
  warning?: string;
}

// ─── Financed emissions ────────────────────────────────────────────

export type AssetClass =
  | 'LISTED_EQUITY' | 'CORPORATE_BONDS' | 'BUSINESS_LOANS'
  | 'PROJECT_FINANCE' | 'COMMERCIAL_REAL_ESTATE'
  | 'MORTGAGES' | 'MOTOR_VEHICLE_LOANS' | 'SOVEREIGN_DEBT';

export type ReportingPeriod = 'ANNUAL' | 'Q1' | 'Q2' | 'Q3' | 'Q4';

export type EmissionsLifecycleStage =
  | 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'PUBLISHED' | 'ARCHIVED';

export interface FinancedEmissionsRecord {
  id: number;
  externalId: string;
  reportingYear: number;
  reportingPeriod: ReportingPeriod;
  assetClass: AssetClass;
  assetClassLabelEn: string;
  assetClassLabelAr?: string;
  outstandingAmountSarMillions: number | string;
  attributionFactor: number | string;
  dataQualityScore: number;
  emissionsScope1Tco2e: number | string;
  emissionsScope2Tco2e: number | string;
  emissionsScope3Tco2e: number | string;
  totalFinancedEmissionsTco2e: number | string;
  methodologyEn?: string;
  methodologyAr?: string;
  lifecycleStage: EmissionsLifecycleStage;
  createdAt?: string;
  updatedAt?: string;
}

export interface FinancedEmissionsRequest {
  reportingYear: number;
  reportingPeriod: ReportingPeriod;
  assetClass: AssetClass;
  assetClassLabelEn: string;
  assetClassLabelAr?: string;
  outstandingAmountSarMillions: number | string;
  attributionFactor: number | string;
  dataQualityScore: number;
  emissionsScope1Tco2e?: number | string;
  emissionsScope2Tco2e?: number | string;
  emissionsScope3Tco2e?: number | string;
  methodologyEn?: string;
  methodologyAr?: string;
}

export interface FinancedEmissionsTotal {
  reportingYear: number;
  reportingPeriod: ReportingPeriod;
  totalFinancedEmissionsTco2e: number | string;
  totalScope1Tco2e: number | string;
  totalScope2Tco2e: number | string;
  totalScope3Tco2e: number | string;
  totalOutstandingSarMillions: number | string;
  weightedAverageDataQuality: number | string;
  assetClassCount: number;
}

// ─── Targets ───────────────────────────────────────────────────────

export type AlignmentFramework =
  | 'SBTI_1_5C' | 'SBTI_WELL_BELOW_2C' | 'NZBA' | 'CUSTOM';

export interface FinancedEmissionsTarget {
  id: number;
  externalId: string;
  targetCode: string;
  titleEn: string;
  titleAr?: string;
  targetYear: number;
  baselineYear: number;
  baselineEmissionsTco2e: number | string;
  targetEmissionsTco2e: number | string;
  reductionPercent: number | string;
  sbtiAligned: boolean;
  alignmentFramework: AlignmentFramework;
  createdAt?: string;
  updatedAt?: string;
}

export interface FinancedEmissionsTargetRequest {
  targetCode?: string;
  titleEn: string;
  titleAr?: string;
  targetYear: number;
  baselineYear: number;
  baselineEmissionsTco2e: number | string;
  targetEmissionsTco2e: number | string;
  sbtiAligned?: boolean;
  alignmentFramework: AlignmentFramework;
}

export interface TargetProgress {
  targetExternalId: string;
  targetCode: string;
  titleEn: string;
  titleAr?: string;
  baselineYear: number;
  baselineEmissionsTco2e: number | string;
  targetYear: number;
  targetEmissionsTco2e: number | string;
  reductionPercent: number | string;
  currentEmissionsTco2e: number | string;
  currentReportingYear?: number;
  achievedReductionPercent: number | string;
  achievementRatio: number | string;
  sbtiAligned: boolean;
  alignmentFramework: AlignmentFramework;
}

// ─── Annual report ─────────────────────────────────────────────────

export interface AnnualClimateReport {
  reportingYear: number;
  scenarios: TcfdScenario[];
  sectorImpacts: TcfdSectorImpact[];
  weightedExpectedLoss: ProbabilityWeightedExpectedLoss;
  financedEmissionsTotal: FinancedEmissionsTotal;
  emissionsRecords: FinancedEmissionsRecord[];
  targetProgress: TargetProgress[];
  generatedAtIso: string;
}

// ─── Labels (Saudi-native EN/AR) ───────────────────────────────────

export const SCENARIO_TYPE_LABELS: Record<ScenarioType, { en: string; ar: string }> = {
  ORDERLY:    { en: 'Orderly',     ar: 'منظَّم' },
  DISORDERLY: { en: 'Disorderly',  ar: 'غير منظَّم' },
  HOT_HOUSE:  { en: 'Hot-house',   ar: 'احتباس حراري' },
  CUSTOM:     { en: 'Custom',      ar: 'مخصَّص' },
};

export const FRAMEWORK_LABELS: Record<ReferenceFramework, { en: string; ar: string }> = {
  NGFS:         { en: 'NGFS',           ar: 'NGFS' },
  IEA_NZE_2050: { en: 'IEA NZE 2050',   ar: 'IEA الحياد الصافي 2050' },
  IPCC_RCP_2_6: { en: 'IPCC RCP 2.6',   ar: 'IPCC RCP 2.6' },
  IPCC_RCP_4_5: { en: 'IPCC RCP 4.5',   ar: 'IPCC RCP 4.5' },
  IPCC_RCP_8_5: { en: 'IPCC RCP 8.5',   ar: 'IPCC RCP 8.5' },
  IFRS_S2:      { en: 'IFRS S2',        ar: 'IFRS S2' },
  CUSTOM:       { en: 'Custom',         ar: 'مخصَّص' },
};

export const SCENARIO_STAGE_LABELS: Record<ScenarioLifecycleStage, { en: string; ar: string }> = {
  DRAFT:          { en: 'Draft',          ar: 'مسوّدة' },
  IN_REVIEW:      { en: 'In review',      ar: 'قيد المراجعة' },
  BOARD_APPROVED: { en: 'Board approved', ar: 'مُعتمَد من المجلس' },
  ARCHIVED:       { en: 'Archived',       ar: 'مؤرشف' },
};

export const ASSET_CLASS_LABELS: Record<AssetClass, { en: string; ar: string }> = {
  LISTED_EQUITY:          { en: 'Listed equity',           ar: 'الأسهم المُدرَجة' },
  CORPORATE_BONDS:        { en: 'Corporate bonds',         ar: 'السندات المؤسسية' },
  BUSINESS_LOANS:         { en: 'Business loans',          ar: 'قروض الأعمال' },
  PROJECT_FINANCE:        { en: 'Project finance',         ar: 'تمويل المشاريع' },
  COMMERCIAL_REAL_ESTATE: { en: 'Commercial real estate',  ar: 'العقارات التجارية' },
  MORTGAGES:              { en: 'Mortgages',               ar: 'قروض الرهن العقاري' },
  MOTOR_VEHICLE_LOANS:    { en: 'Motor vehicle loans',     ar: 'قروض السيارات' },
  SOVEREIGN_DEBT:         { en: 'Sovereign debt',          ar: 'السندات السيادية' },
};

export const EMISSIONS_STAGE_LABELS: Record<EmissionsLifecycleStage, { en: string; ar: string }> = {
  DRAFT:     { en: 'Draft',     ar: 'مسوّدة' },
  IN_REVIEW: { en: 'In review', ar: 'قيد المراجعة' },
  APPROVED:  { en: 'Approved',  ar: 'مُعتمَد' },
  PUBLISHED: { en: 'Published', ar: 'منشور' },
  ARCHIVED:  { en: 'Archived',  ar: 'مؤرشف' },
};

export const ALIGNMENT_LABELS: Record<AlignmentFramework, { en: string; ar: string }> = {
  SBTI_1_5C:           { en: 'SBTi 1.5°C',          ar: 'SBTi 1.5°م' },
  SBTI_WELL_BELOW_2C:  { en: 'SBTi well-below 2°C', ar: 'SBTi دون 2°م بكثير' },
  NZBA:                { en: 'NZBA',                ar: 'تحالف الحياد الصافي للبنوك' },
  CUSTOM:              { en: 'Custom',              ar: 'مخصَّص' },
};

// ─── Tones ─────────────────────────────────────────────────────────

export function scenarioStageTone(s: ScenarioLifecycleStage): BadgeTone {
  switch (s) {
    case 'BOARD_APPROVED': return 'success';
    case 'IN_REVIEW':      return 'info';
    case 'DRAFT':          return 'warning';
    case 'ARCHIVED':       return 'neutral';
  }
}

export function emissionsStageTone(s: EmissionsLifecycleStage): BadgeTone {
  switch (s) {
    case 'PUBLISHED': return 'success';
    case 'APPROVED':  return 'info';
    case 'IN_REVIEW': return 'info';
    case 'DRAFT':     return 'warning';
    case 'ARCHIVED':  return 'neutral';
  }
}

export function scenarioTypeTone(t: ScenarioType): BadgeTone {
  switch (t) {
    case 'ORDERLY':    return 'success';
    case 'DISORDERLY': return 'warning';
    case 'HOT_HOUSE':  return 'danger';
    case 'CUSTOM':     return 'neutral';
  }
}

/** PCAF data quality 1 = best (verified), 5 = worst (proxy). */
export function dataQualityTone(score: number): BadgeTone {
  if (score <= 2) return 'success';
  if (score === 3) return 'info';
  if (score === 4) return 'warning';
  return 'danger';
}

// ─── Formatters ────────────────────────────────────────────────────

export function fmtTco2e(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(n)) return '—';
  const abs = Math.abs(n);
  if (abs >= 1_000_000) return `${(n / 1_000_000).toFixed(2)} M tCO₂e`;
  if (abs >= 1_000)     return `${(n / 1_000).toFixed(1)} k tCO₂e`;
  return `${n.toFixed(0)} tCO₂e`;
}

export function fmtSarMillions(value?: number | string | null, isAr = false): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(n)) return '—';
  const unit = isAr ? 'مليون ر.س' : 'M SAR';
  if (Math.abs(n) >= 1_000) return `${(n / 1_000).toFixed(2)} ${isAr ? 'مليار ر.س' : 'B SAR'}`;
  return `${n.toFixed(2)} ${unit}`;
}

export function fmtPercent(value?: number | string | null, decimals = 2): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(n)) return '—';
  return `${n.toFixed(decimals)}%`;
}

export function fmtCelsius(value?: number | string | null): string {
  if (value === null || value === undefined || value === '') return '—';
  const n = typeof value === 'string' ? Number(value) : value;
  if (!Number.isFinite(n)) return '—';
  return `${n.toFixed(1)}°C`;
}

// ─── API calls ─────────────────────────────────────────────────────

const SCENARIOS = '/api/v1/climate/scenarios';
const EMISSIONS = '/api/v1/climate/financed-emissions';
const REPORT    = '/api/v1/climate/report';

// Scenarios.
export function listScenarios(): Promise<TcfdScenario[]> {
  return api.get<TcfdScenario[]>(SCENARIOS);
}
export function listApprovedScenarios(): Promise<TcfdScenario[]> {
  return api.get<TcfdScenario[]>(`${SCENARIOS}/approved`);
}
export function getScenario(externalId: string): Promise<TcfdScenario> {
  return api.get<TcfdScenario>(`${SCENARIOS}/${externalId}`);
}
export function createScenario(body: TcfdScenarioRequest): Promise<TcfdScenario> {
  return api.post<TcfdScenario>(SCENARIOS, body);
}
export function updateScenario(externalId: string, body: TcfdScenarioRequest): Promise<TcfdScenario> {
  return api.put<TcfdScenario>(`${SCENARIOS}/${externalId}`, body);
}
export function transitionScenario(externalId: string, toStage: ScenarioLifecycleStage): Promise<TcfdScenario> {
  return api.post<TcfdScenario>(`${SCENARIOS}/${externalId}/transition`, { toStage });
}
export function listSectorImpacts(externalId: string): Promise<TcfdSectorImpact[]> {
  return api.get<TcfdSectorImpact[]>(`${SCENARIOS}/${externalId}/sector-impacts`);
}
export function addSectorImpact(externalId: string, body: TcfdSectorImpactRequest): Promise<TcfdSectorImpact> {
  return api.post<TcfdSectorImpact>(`${SCENARIOS}/${externalId}/sector-impacts`, body);
}
export function getProbabilityWeightedExpectedLoss(): Promise<ProbabilityWeightedExpectedLoss> {
  return api.get<ProbabilityWeightedExpectedLoss>(`${SCENARIOS}/weighted-expected-loss`);
}

// Financed emissions.
export function listEmissions(year?: number): Promise<FinancedEmissionsRecord[]> {
  const q = year != null ? `?year=${year}` : '';
  return api.get<FinancedEmissionsRecord[]>(`${EMISSIONS}${q}`);
}
export function getEmission(externalId: string): Promise<FinancedEmissionsRecord> {
  return api.get<FinancedEmissionsRecord>(`${EMISSIONS}/${externalId}`);
}
export function createEmission(body: FinancedEmissionsRequest): Promise<FinancedEmissionsRecord> {
  return api.post<FinancedEmissionsRecord>(EMISSIONS, body);
}
export function updateEmission(externalId: string, body: FinancedEmissionsRequest): Promise<FinancedEmissionsRecord> {
  return api.put<FinancedEmissionsRecord>(`${EMISSIONS}/${externalId}`, body);
}
export function transitionEmission(externalId: string, toStage: EmissionsLifecycleStage): Promise<FinancedEmissionsRecord> {
  return api.post<FinancedEmissionsRecord>(`${EMISSIONS}/${externalId}/transition`, { toStage });
}
export function getEmissionsTotal(year: number, period?: ReportingPeriod): Promise<FinancedEmissionsTotal> {
  const q = period ? `&period=${period}` : '';
  return api.get<FinancedEmissionsTotal>(`${EMISSIONS}/totals?year=${year}${q}`);
}

// Targets.
export function listTargets(): Promise<FinancedEmissionsTarget[]> {
  return api.get<FinancedEmissionsTarget[]>(`${EMISSIONS}/targets`);
}
export function createTarget(body: FinancedEmissionsTargetRequest): Promise<FinancedEmissionsTarget> {
  return api.post<FinancedEmissionsTarget>(`${EMISSIONS}/targets`, body);
}
export function getTargetProgress(): Promise<TargetProgress[]> {
  return api.get<TargetProgress[]>(`${EMISSIONS}/target-progress`);
}

// Annual report.
export function getAnnualReport(year: number): Promise<AnnualClimateReport> {
  return api.get<AnnualClimateReport>(`${REPORT}/annual?year=${year}`);
}
