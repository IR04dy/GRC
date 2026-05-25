// Recovery & Resolution Plan — Sprint 17 PR #3.
//
// Backend: PR #245 (com.tss.grc.module.rrp).
// F8 rules: PR #246 (AR-221..AR-230).
//
// Annual SAMA D-SIB Recovery & Resolution Plan workspace. Cycle has
// catalogs of critical functions, recovery options, and live RAG-
// banded indicators feeding the worsening-only F8 breach trigger.
//
// Saudi-native bilingual sidebar:
//   ar : خطة الاستعادة والحلّ
//   en : Recovery & Resolution

import { api } from './api';

// ─── Enums ───────────────────────────────────────────────────────

export type CycleStatus =
  | 'DRAFT' | 'IN_PREPARATION' | 'BOARD_REVIEW' | 'APPROVED'
  | 'SUBMITTED' | 'ACKNOWLEDGED' | 'CLOSED';

export type OptionCategory =
  | 'CAPITAL' | 'LIQUIDITY' | 'ASSET_SALE' | 'BUSINESS_LINE' | 'EXPENSE' | 'OTHER';

export type Feasibility = 'HIGH' | 'MEDIUM' | 'LOW';

export type Materiality = 'SYSTEMIC' | 'HIGH' | 'MEDIUM' | 'LOW';

export type IndicatorCategory =
  | 'CAPITAL' | 'LIQUIDITY' | 'PROFITABILITY' | 'ASSET_QUALITY'
  | 'MARKET_CONFIDENCE' | 'OPERATIONAL' | 'OTHER';

export type Direction = 'HIGHER_IS_BETTER' | 'LOWER_IS_BETTER';

export type Band = 'GREEN' | 'AMBER' | 'RED';

export type EventType =
  | 'STATUS_CHANGED' | 'OPTION_ADDED' | 'OPTION_REASSESSED'
  | 'CRITICAL_FUNCTION_ADDED' | 'INDICATOR_BREACH' | 'INDICATOR_RECOVERED'
  | 'SCENARIO_REHEARSED' | 'BOARD_MILESTONE' | 'SUBMITTED'
  | 'ACK_RECEIVED' | 'INTERNAL_NOTE';

// ─── Requests ────────────────────────────────────────────────────

export interface CreateCycleRequest {
  fiscalYear: number;
  titleEn: string;
  titleAr: string;
  submissionDueDate?: string;
  ownerUserId?: number;
  sponsorUserId?: number;
}

export interface UpdateCycleRequest {
  titleEn?: string;
  titleAr?: string;
  submissionDueDate?: string;
  ownerUserId?: number;
  sponsorUserId?: number;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
  filingArtifactReference?: string;
  boardApprovalDate?: string;
  boardResolutionReference?: string;
}

export interface TransitionCycleRequest {
  toStatus: CycleStatus;
  noteEn?: string;
  noteAr?: string;
}

export interface SubmitCycleRequest {
  regulatorAckReference: string;
  filingArtifactReference?: string;
  noteEn?: string;
  noteAr?: string;
}

export interface CreateOptionRequest {
  optionCode: string;
  category: OptionCategory;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  capacitySarM: number;
  timeToExecuteDays: number;
  feasibility: Feasibility;
  impactEn?: string;
  impactAr?: string;
  preconditionsEn?: string;
  preconditionsAr?: string;
  limitationsEn?: string;
  limitationsAr?: string;
  accountableRole?: string;
}

export interface UpdateOptionRequest extends Partial<CreateOptionRequest> {}

export interface CreateCriticalFunctionRequest {
  functionCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  materiality: Materiality;
  maxTolerableDowntimeHours?: number;
  customerCountEstimate?: number;
  dailyVolumeEstimate?: number;
  criticalProviderDependenciesEn?: string;
  criticalProviderDependenciesAr?: string;
  continuityStrategyEn?: string;
  continuityStrategyAr?: string;
  ownerRole?: string;
}

export interface CreateIndicatorRequest {
  indicatorCode: string;
  category: IndicatorCategory;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  unit?: string;
  direction: Direction;
  greenThreshold?: number;
  amberThreshold?: number;
  currentValue?: number;
  measuredAt?: string;
  dataSource?: string;
  ownerRole?: string;
}

export interface RecordMeasurementRequest {
  value: number;
  measuredAt: string;
  noteEn?: string;
  noteAr?: string;
}

// ─── Responses ───────────────────────────────────────────────────

export interface CycleResponse {
  id: number;
  reference: string;
  fiscalYear: number;
  titleEn: string;
  titleAr: string;
  status: CycleStatus;
  submissionDueDate?: string;
  submittedAt?: string;
  regulatorAckReference?: string;
  acknowledgedAt?: string;
  closedAt?: string;
  ownerUserId?: number;
  sponsorUserId?: number;
  boardApprovalDate?: string;
  boardResolutionReference?: string;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
  filingArtifactReference?: string;
  criticalFunctionCount?: number;
  recoveryOptionCount?: number;
  indicatorCount?: number;
  indicatorsAmber?: number;
  indicatorsRed?: number;
  totalRecoveryCapacitySarM?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface OptionResponse {
  id: number;
  cycleId: number;
  optionCode: string;
  category: OptionCategory;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  capacitySarM: number;
  timeToExecuteDays: number;
  feasibility: Feasibility;
  impactEn?: string;
  impactAr?: string;
  preconditionsEn?: string;
  preconditionsAr?: string;
  limitationsEn?: string;
  limitationsAr?: string;
  accountableRole?: string;
}

export interface CriticalFunctionResponse {
  id: number;
  cycleId: number;
  functionCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  materiality: Materiality;
  maxTolerableDowntimeHours?: number;
  customerCountEstimate?: number;
  dailyVolumeEstimate?: number;
  criticalProviderDependenciesEn?: string;
  criticalProviderDependenciesAr?: string;
  continuityStrategyEn?: string;
  continuityStrategyAr?: string;
  ownerRole?: string;
}

export interface IndicatorResponse {
  id: number;
  cycleId: number;
  indicatorCode: string;
  category: IndicatorCategory;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string;
  descriptionAr?: string;
  unit?: string;
  direction: Direction;
  greenThreshold?: number;
  amberThreshold?: number;
  currentValue?: number;
  currentBand: Band;
  measuredAt?: string;
  dataSource?: string;
  ownerRole?: string;
}

export interface EventResponse {
  id: number;
  cycleId: number;
  occurredAt: string;
  eventType: EventType;
  actorUserId?: number;
  actorName?: string;
  statusAfter?: string;
  linkedOptionId?: number;
  linkedIndicatorId?: number;
  linkedCriticalFunctionId?: number;
  bandBefore?: Band;
  bandAfter?: Band;
  noteEn?: string;
  noteAr?: string;
}

export interface DashboardResponse {
  activeCycleCount: number;
  submittedCycleCount: number;
  closedCycleCount: number;
  indicatorsRed: number;
  indicatorsAmber: number;
  currentRecoveryCapacitySarM: number;
  recentCycles: CycleResponse[];
  byStatus: Record<string, number>;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

// ─── Label maps (Saudi-native) ──────────────────────────────────

export const CYCLE_STATUS_LABEL: Record<CycleStatus, { en: string; ar: string }> = {
  DRAFT:          { en: 'Draft',           ar: 'مسودّة' },
  IN_PREPARATION: { en: 'In preparation',  ar: 'قيد الإعداد' },
  BOARD_REVIEW:   { en: 'Board review',    ar: 'مراجعة المجلس' },
  APPROVED:       { en: 'Approved',        ar: 'مُعتمَدة' },
  SUBMITTED:      { en: 'Submitted',       ar: 'مُقدَّمة' },
  ACKNOWLEDGED:   { en: 'Acknowledged',    ar: 'مُستلَمة' },
  CLOSED:         { en: 'Closed',          ar: 'مغلقة' },
};

export const CYCLE_STATUS_TONE: Record<CycleStatus,
  'neutral' | 'info' | 'warning' | 'danger' | 'success'> = {
  DRAFT:          'neutral',
  IN_PREPARATION: 'info',
  BOARD_REVIEW:   'warning',
  APPROVED:       'info',
  SUBMITTED:      'success',
  ACKNOWLEDGED:   'success',
  CLOSED:         'success',
};

export const OPTION_CATEGORY_LABEL: Record<OptionCategory,
  { en: string; ar: string }> = {
  CAPITAL:       { en: 'Capital',           ar: 'رأس المال' },
  LIQUIDITY:     { en: 'Liquidity',         ar: 'السيولة' },
  ASSET_SALE:    { en: 'Asset sale',        ar: 'بيع الأصول' },
  BUSINESS_LINE: { en: 'Business line',     ar: 'خطّ النشاط' },
  EXPENSE:       { en: 'Expense reduction', ar: 'خفض المصروفات' },
  OTHER:         { en: 'Other',             ar: 'أخرى' },
};

export const FEASIBILITY_LABEL: Record<Feasibility, { en: string; ar: string }> = {
  HIGH:   { en: 'High',   ar: 'مرتفعة' },
  MEDIUM: { en: 'Medium', ar: 'متوسّطة' },
  LOW:    { en: 'Low',    ar: 'منخفضة' },
};

export const MATERIALITY_LABEL: Record<Materiality, { en: string; ar: string }> = {
  SYSTEMIC: { en: 'Systemic', ar: 'نظامية' },
  HIGH:     { en: 'High',     ar: 'مرتفعة' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسّطة' },
  LOW:      { en: 'Low',      ar: 'منخفضة' },
};

export const MATERIALITY_TONE: Record<Materiality,
  'neutral' | 'info' | 'warning' | 'danger'> = {
  SYSTEMIC: 'danger',
  HIGH:     'warning',
  MEDIUM:   'info',
  LOW:      'neutral',
};

export const INDICATOR_CATEGORY_LABEL: Record<IndicatorCategory,
  { en: string; ar: string }> = {
  CAPITAL:           { en: 'Capital',           ar: 'رأس المال' },
  LIQUIDITY:         { en: 'Liquidity',         ar: 'السيولة' },
  PROFITABILITY:     { en: 'Profitability',     ar: 'الربحيّة' },
  ASSET_QUALITY:     { en: 'Asset quality',     ar: 'جودة الأصول' },
  MARKET_CONFIDENCE: { en: 'Market confidence', ar: 'ثقة السوق' },
  OPERATIONAL:       { en: 'Operational',       ar: 'تشغيليّة' },
  OTHER:             { en: 'Other',             ar: 'أخرى' },
};

export const BAND_LABEL: Record<Band, { en: string; ar: string }> = {
  GREEN: { en: 'Green', ar: 'أخضر' },
  AMBER: { en: 'Amber', ar: 'كهرماني' },
  RED:   { en: 'Red',   ar: 'أحمر' },
};

export const BAND_TONE: Record<Band, 'success' | 'warning' | 'danger'> = {
  GREEN: 'success',
  AMBER: 'warning',
  RED:   'danger',
};

export const EVENT_TYPE_LABEL: Record<EventType, { en: string; ar: string }> = {
  STATUS_CHANGED:          { en: 'Status changed',         ar: 'تغيير الحالة' },
  OPTION_ADDED:            { en: 'Option added',           ar: 'إضافة خيار' },
  OPTION_REASSESSED:       { en: 'Option reassessed',      ar: 'إعادة تقييم خيار' },
  CRITICAL_FUNCTION_ADDED: { en: 'Critical function added',ar: 'إضافة وظيفة حيوية' },
  INDICATOR_BREACH:        { en: 'Indicator breach',       ar: 'تجاوز مؤشّر' },
  INDICATOR_RECOVERED:     { en: 'Indicator recovered',    ar: 'تحسّن مؤشّر' },
  SCENARIO_REHEARSED:      { en: 'Scenario rehearsed',     ar: 'بروفة سيناريو' },
  BOARD_MILESTONE:         { en: 'Board milestone',        ar: 'حدث مجلس' },
  SUBMITTED:               { en: 'Submitted',              ar: 'تقديم' },
  ACK_RECEIVED:            { en: 'Acknowledgment',         ar: 'إقرار استلام' },
  INTERNAL_NOTE:           { en: 'Internal note',          ar: 'ملاحظة داخلية' },
};

/** Next allowed cycle transitions. Mirrors RrpService.ALLOWED.
 *  Submit / acknowledge / close use dedicated endpoints. */
export function nextAllowed(status: CycleStatus): CycleStatus[] {
  switch (status) {
    case 'DRAFT':          return ['IN_PREPARATION'];
    case 'IN_PREPARATION': return ['BOARD_REVIEW'];
    case 'BOARD_REVIEW':   return ['APPROVED', 'IN_PREPARATION'];
    case 'APPROVED':       return ['IN_PREPARATION'];
    case 'ACKNOWLEDGED':   return ['CLOSED'];
    default:               return [];
  }
}

// ─── REST client ──────────────────────────────────────────────────

const PREFIX = '/api/v1/rrp';

export const rrpApi = {
  dashboard: () => api.get<DashboardResponse>(`${PREFIX}/dashboard`),

  // Cycles
  listCycles: (page = 0, size = 50) =>
    api.get<Page<CycleResponse>>(`${PREFIX}/cycles?page=${page}&size=${size}`),
  getCycle: (id: number) => api.get<CycleResponse>(`${PREFIX}/cycles/${id}`),
  createCycle: (body: CreateCycleRequest) =>
    api.post<CycleResponse>(`${PREFIX}/cycles`, body),
  updateCycle: (id: number, body: UpdateCycleRequest) =>
    api.put<CycleResponse>(`${PREFIX}/cycles/${id}`, body),
  transitionCycle: (id: number, body: TransitionCycleRequest) =>
    api.post<CycleResponse>(`${PREFIX}/cycles/${id}/transition`, body),
  submitCycle: (id: number, body: SubmitCycleRequest) =>
    api.post<CycleResponse>(`${PREFIX}/cycles/${id}/submit`, body),
  acknowledgeCycle: (id: number) =>
    api.post<CycleResponse>(`${PREFIX}/cycles/${id}/acknowledge`, {}),

  // Options
  listOptions: (cycleId: number) =>
    api.get<OptionResponse[]>(`${PREFIX}/cycles/${cycleId}/options`),
  addOption: (cycleId: number, body: CreateOptionRequest) =>
    api.post<OptionResponse>(`${PREFIX}/cycles/${cycleId}/options`, body),
  updateOption: (optionId: number, body: UpdateOptionRequest) =>
    api.put<OptionResponse>(`${PREFIX}/options/${optionId}`, body),

  // Critical functions
  listCriticalFunctions: (cycleId: number) =>
    api.get<CriticalFunctionResponse[]>(`${PREFIX}/cycles/${cycleId}/critical-functions`),
  addCriticalFunction: (cycleId: number, body: CreateCriticalFunctionRequest) =>
    api.post<CriticalFunctionResponse>(`${PREFIX}/cycles/${cycleId}/critical-functions`, body),

  // Indicators
  listIndicators: (cycleId: number) =>
    api.get<IndicatorResponse[]>(`${PREFIX}/cycles/${cycleId}/indicators`),
  addIndicator: (cycleId: number, body: CreateIndicatorRequest) =>
    api.post<IndicatorResponse>(`${PREFIX}/cycles/${cycleId}/indicators`, body),
  recordMeasurement: (indicatorId: number, body: RecordMeasurementRequest) =>
    api.post<IndicatorResponse>(`${PREFIX}/indicators/${indicatorId}/measurements`, body),

  // Events
  listEvents: (cycleId: number) =>
    api.get<EventResponse[]>(`${PREFIX}/cycles/${cycleId}/events`),
};
