// Audit Committee Dashboard — Sprint 11 REST client.
//
// Distinct from the existing `lib/auditCommittee.ts` static charter
// data (committee composition, meeting register) — this file talks
// to the backend curation slice that aggregates risk + controls +
// findings + CAPA + AI + climate + obligations + incidents +
// vendor-BCP + 3LoD into a single board-facing view.
//
// Saudi-native bilingual page title:
//   ar: لوحة لجنة المراجعة   ← NOT literal "لوحة لجنة التدقيق"
//   en: Audit Committee Dashboard
//
// Regulatory driver: SAMA Corporate Governance Regulations §6 +
// CMA Listed-Companies CG Code Article 54 — Audit Committee
// responsibilities include regular reporting on risk, internal
// control, compliance and findings.

import { api } from './api';

// ─── Type system ─────────────────────────────────────────────────

export type MeetingType = 'QUARTERLY' | 'ANNUAL' | 'AD_HOC' | 'OFFCYCLE';

export type AttestationRole =
  | 'CHAIR'
  | 'MEMBER'
  | 'AUDIT_DIRECTOR'
  | 'EXTERNAL_AUDITOR'
  | 'COMPANY_SECRETARY';

export interface RiskTopRow {
  id: number;
  externalId?: string;
  titleEn: string;
  titleAr?: string;
  riskLevel?: string;
  residualScore?: number;
  status?: string;
}

export interface RiskSection {
  totalRisks: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  aboveAppetiteCount: number;
  topAboveAppetite: RiskTopRow[];
}

export interface ControlSection {
  totalControls: number;
  effectiveCount: number;
  failingCount: number;
  effectivenessPct: number;
  ccmTotalTests: number;
  ccmEnabledTests: number;
  ccmPassRateLast24h: number;  // 0..1
}

export interface FindingSection {
  totalOpen: number;
  criticalOpen: number;
  highOpen: number;
  mediumOpen: number;
  lowOpen: number;
  ageBucket0to30: number;
  ageBucket30to90: number;
  ageBucketOver90: number;
  closedLastQuarter: number;
  closureRateLastQuarterPct: number;
}

export interface CapaSection {
  totalOpen: number;
  overdueCount: number;
  closedThisQuarter: number;
  onTrackRatioPct: number;
}

export interface AiSection {
  totalModels: number;
  criticalCount: number;
  highCount: number;
  mediumCount: number;
  lowCount: number;
  missingFairnessAuditCount: number;
  unacknowledgedDriftCount: number;
}

export interface ClimateSection {
  totalScenarios: number;
  approvedScenarios: number;
  probabilityWeightedExpectedLossSar: number;
  financedEmissionsCurrentTco2e: number;
  financedEmissionsPreviousTco2e: number;
  financedEmissionsYoyChangePct?: number;
}

export interface ObligationSection {
  totalActive: number;
  overdueCount: number;
  attestedThisCyclePct: number;
  regulatorBreakdown: Record<string, number>;
}

export interface IncidentSection {
  lastQuarterTotal: number;
  lastQuarterCritical: number;
  lastQuarterHigh: number;
  lastQuarterMedium: number;
  lastQuarterLow: number;
  mttrAverageHours?: number;
  regulatorNotifiableCount: number;
}

export interface VendorBcpSection {
  latestCampaignCode?: string;
  latestCampaignInvited: number;
  latestCampaignSubmitted: number;
  latestCampaignResponseRatePct: number;
  belowRtoThresholdCount: number;
}

export interface ThreeLinesSection {
  line1Count: number;
  line2Count: number;
  line3Count: number;
  boardEscalations: number;
}

export interface LiveDashboardResponse {
  computedAt: string;
  risk: RiskSection;
  controls: ControlSection;
  findings: FindingSection;
  capa: CapaSection;
  ai: AiSection;
  climate: ClimateSection;
  obligations: ObligationSection;
  incidents: IncidentSection;
  vendorBcp: VendorBcpSection;
  threeLines: ThreeLinesSection;
}

export interface AttestationResponse {
  id: number;
  externalId: string;
  snapshotId: number;
  attestedByUserId: number;
  attestedRole: AttestationRole;
  attestationTextEn?: string;
  attestationTextAr?: string;
  attestedAt: string;
  signatureHash?: string;
}

export interface SnapshotResponse {
  id: number;
  externalId: string;
  snapshotCode: string;
  meetingDate: string;
  meetingType: MeetingType;
  generatedAt: string;
  generatedByUserId: number;
  kpisJson: string;
  executiveSummaryEn?: string;
  executiveSummaryAr?: string;
  committeeResolutions?: string;
  isFinalized: boolean;
  finalizedAt?: string;
  finalizedByUserId?: number;
  attestations: AttestationResponse[];
}

export interface SnapshotSummary {
  id: number;
  externalId: string;
  snapshotCode: string;
  meetingDate: string;
  meetingType: MeetingType;
  generatedAt: string;
  isFinalized: boolean;
  attestationCount: number;
}

export interface GenerateSnapshotRequest {
  meetingDate: string;   // ISO yyyy-mm-dd
  meetingType: MeetingType;
  snapshotCode?: string;
}

export interface AttestRequest {
  attestedRole: AttestationRole;
  attestationTextEn?: string;
  attestationTextAr?: string;
}

export interface RecordResolutionRequest {
  textEn: string;
  textAr?: string;
}

export interface FinalizeRequest {
  executiveSummaryEn: string;
  executiveSummaryAr?: string;
}

// ─── REST client ─────────────────────────────────────────────────

export const auditCommitteeDashboardApi = {
  live: () => api.get<LiveDashboardResponse>('/api/v1/audit-committee/dashboard/live'),
  listSnapshots: () =>
    api.get<SnapshotSummary[]>('/api/v1/audit-committee/dashboard/snapshots'),
  getSnapshot: (externalId: string) =>
    api.get<SnapshotResponse>(`/api/v1/audit-committee/dashboard/snapshots/${externalId}`),
  generate: (body: GenerateSnapshotRequest) =>
    api.post<SnapshotResponse>('/api/v1/audit-committee/dashboard/snapshots', body),
  attest: (externalId: string, body: AttestRequest) =>
    api.post<AttestationResponse>(
      `/api/v1/audit-committee/dashboard/snapshots/${externalId}/attest`, body),
  recordResolution: (externalId: string, body: RecordResolutionRequest) =>
    api.post<SnapshotResponse>(
      `/api/v1/audit-committee/dashboard/snapshots/${externalId}/resolutions`, body),
  finalize: (externalId: string, body: FinalizeRequest) =>
    api.post<SnapshotResponse>(
      `/api/v1/audit-committee/dashboard/snapshots/${externalId}/finalize`, body),
};

// ─── Display labels (Saudi-native — NOT literal translations) ───

export const MEETING_TYPE_LABEL: Record<MeetingType, { en: string; ar: string }> = {
  QUARTERLY: { en: 'Quarterly',  ar: 'ربع سنوي' },
  ANNUAL:    { en: 'Annual',     ar: 'سنوي' },
  AD_HOC:    { en: 'Ad-hoc',     ar: 'استثنائي' },
  OFFCYCLE:  { en: 'Off-cycle',  ar: 'خارج الجدول' },
};

export const ROLE_LABEL: Record<AttestationRole, { en: string; ar: string }> = {
  CHAIR:             { en: 'Chair',              ar: 'رئيس اللجنة' },
  MEMBER:            { en: 'Member',             ar: 'عضو' },
  AUDIT_DIRECTOR:    { en: 'Audit Director',     ar: 'مدير المراجعة الداخلية' },
  EXTERNAL_AUDITOR:  { en: 'External Auditor',   ar: 'المراجع الخارجي' },
  COMPANY_SECRETARY: { en: 'Company Secretary',  ar: 'أمين السر' },
};

/** Truncate signature hash for compact display. */
export function shortSignature(hash: string | undefined | null): string {
  if (!hash) return '—';
  if (hash.length <= 16) return hash;
  return hash.slice(0, 8) + '…' + hash.slice(-4);
}

/** Pretty number formatter — Saudi locale-aware grouping. */
export function fmtNumber(n: number | undefined | null, lang: 'en' | 'ar'): string {
  if (n == null) return '—';
  return new Intl.NumberFormat(lang === 'ar' ? 'ar-SA' : 'en-GB').format(n);
}

/** Format SAR with appropriate suffix (M / K). */
export function fmtSar(n: number | undefined | null, lang: 'en' | 'ar'): string {
  if (n == null || n === 0) return lang === 'ar' ? '٠ ر.س' : '0 SAR';
  const abs = Math.abs(n);
  let value: string;
  let suffix: string;
  if (abs >= 1_000_000) {
    value = (n / 1_000_000).toFixed(1);
    suffix = lang === 'ar' ? ' م.ر.س' : 'M SAR';
  } else if (abs >= 1_000) {
    value = (n / 1_000).toFixed(1);
    suffix = lang === 'ar' ? ' ك.ر.س' : 'K SAR';
  } else {
    value = n.toFixed(0);
    suffix = lang === 'ar' ? ' ر.س' : ' SAR';
  }
  return value + suffix;
}

/** Parsed resolution JSON entry. */
export interface ResolutionEntry {
  id: string;
  recordedAt: string;
  recordedByUserId: number;
  textEn: string;
  textAr?: string;
}

export function parseResolutions(json: string | undefined | null): ResolutionEntry[] {
  if (!json || json === '[]') return [];
  try {
    const parsed = JSON.parse(json);
    return Array.isArray(parsed) ? (parsed as ResolutionEntry[]) : [];
  } catch {
    return [];
  }
}
