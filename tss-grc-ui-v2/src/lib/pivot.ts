// Risk-Control-Finding Pivot — Sprint 11 REST client.
//
// Cross-module aggregation slice — the auditor's "for this risk,
// show me every linked control + finding + CAPA + CCM result on one
// screen". Three lenses share a single response shape; the
// `focalEntity` discriminator tells the caller which entity is at
// the centre.
//
// Saudi-native bilingual page title:
//   ar: محور المخاطر والضوابط والنتائج
//   en: Risk-Control-Finding Pivot
//
// Regulatory driver:
//   - NCA ECC-2 audit pattern (linked controls + tests + findings)
//   - SAMA CSF 1.2.3 (Risk Treatment) traceability
//   - IIA Standard 2210.A1 (engagement planning)

import { api } from './api';

// ─── Type system ─────────────────────────────────────────────────

export type FocalEntity = 'RISK' | 'CONTROL' | 'FINDING';

export interface RiskSummary {
  id: number;
  externalId?: string;
  code?: string;
  titleEn?: string;
  titleAr?: string;
  riskLevel?: string;
  residualScore?: number;
  ownerId?: number;
  status?: string;
}

export interface ControlSummary {
  id: number;
  externalId?: string;
  code?: string;
  titleEn?: string;
  titleAr?: string;
  controlType?: string;
  automationLevel?: string;
  status?: string;
  domain?: string;
  ownerUserId?: number;
}

export interface FindingSummary {
  id: number;
  externalId?: string;
  refNumber?: string;
  titleEn?: string;
  titleAr?: string;
  severity?: string;
  status?: string;
  engagementCode?: string;
  raisedAt?: string;
}

export interface ControlPivot {
  id: number;
  code?: string;
  titleEn?: string;
  titleAr?: string;
  effectiveness?: string;
  lastTestStatus?: string;
  lastTestAt?: string;
  domain?: string;
  ownerUserId?: number;
}

export interface RiskPivot {
  id: number;
  externalId?: string;
  titleEn?: string;
  titleAr?: string;
  riskLevel?: string;
  residualScore?: number;
  status?: string;
}

export interface FindingPivot {
  id: number;
  externalId?: string;
  refNumber?: string;
  titleEn?: string;
  titleAr?: string;
  severity?: string;
  status?: string;
  raisedAt?: string;
  engagementCode?: string;
}

export interface CapaPivot {
  id: number;
  externalId?: string;
  titleEn?: string;
  titleAr?: string;
  targetDate?: string;
  status?: string;
  assignee?: string;
  daysOverdue?: number;
  sourceModule?: 'AUDIT_LC' | 'CAPA';
}

export interface CcmCoverageStats {
  totalTests: number;
  passingLast24h: number;
  failingLast24h: number;
  errorLast24h: number;
  lastRunAt?: string;
}

export interface PivotSummary {
  controlsCount: number;
  effectiveControlsCount: number;
  effectiveControlsPct: number;
  openFindingsCount: number;
  overdueCapaCount: number;
  linkedRisksCount: number;
  dataCompletenessPct: number;
}

export interface PivotResponse {
  focalEntity: FocalEntity;
  computedAt: string;
  risk?: RiskSummary;
  control?: ControlSummary;
  finding?: FindingSummary;
  linkedRisks: RiskPivot[];
  linkedControls: ControlPivot[];
  linkedFindings: FindingPivot[];
  linkedCapa: CapaPivot[];
  ccmCoverage: CcmCoverageStats;
  summary: PivotSummary;
}

// ─── REST client ─────────────────────────────────────────────────

export const pivotApi = {
  byRisk:    (riskId:    number | string) => api.get<PivotResponse>(`/api/v1/governance/pivot/risk/${riskId}`),
  byControl: (controlId: number | string) => api.get<PivotResponse>(`/api/v1/governance/pivot/control/${controlId}`),
  byFinding: (findingId: number | string) => api.get<PivotResponse>(`/api/v1/governance/pivot/finding/${findingId}`),
};

// ─── Display helpers (Saudi-native — NOT literal) ────────────────

export const EFFECTIVENESS_LABEL: Record<string, { en: string; ar: string; tone: 'good' | 'warn' | 'bad' | 'neutral' }> = {
  EFFECTIVE:         { en: 'Effective',         ar: 'فعّال',           tone: 'good' },
  NEEDS_IMPROVEMENT: { en: 'Needs improvement', ar: 'بحاجة لتحسين',     tone: 'warn' },
  INEFFECTIVE:       { en: 'Ineffective',       ar: 'غير فعّال',        tone: 'bad' },
  NOT_TESTED:        { en: 'Not tested',        ar: 'لم يُختبر',        tone: 'neutral' },
};

export const SEVERITY_TONE: Record<string, 'good' | 'warn' | 'bad' | 'neutral'> = {
  CRITICAL:    'bad',
  HIGH:        'bad',
  MEDIUM:      'warn',
  LOW:         'neutral',
  OBSERVATION: 'neutral',
};

export const STATUS_TONE: Record<string, 'good' | 'warn' | 'bad' | 'neutral'> = {
  OPEN:                  'warn',
  MGMT_RESPONSE:         'warn',
  IN_REMEDIATION:        'warn',
  AWAITING_VERIFICATION: 'warn',
  IN_PROGRESS:           'warn',
  CLOSED:                'good',
  CANCELLED:             'neutral',
  OVERDUE:               'bad',
  PASS:                  'good',
  FAIL:                  'bad',
  ERROR:                 'bad',
  TIMEOUT:               'bad',
};

export const FOCAL_ENTITY_LABEL: Record<FocalEntity, { en: string; ar: string }> = {
  RISK:    { en: 'Risk lens',    ar: 'منظور المخاطر' },
  CONTROL: { en: 'Control lens', ar: 'منظور الضوابط' },
  FINDING: { en: 'Finding lens', ar: 'منظور النتائج' },
};

export function pivotTitle(lang: 'en' | 'ar'): string {
  return lang === 'ar'
    ? 'محور المخاطر والضوابط والنتائج'
    : 'Risk-Control-Finding Pivot';
}
