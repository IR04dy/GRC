// SOC Operations API client + types — wires the frontend to the V150 +
// V151 backbone shipped by PR #97 / V151 automation rules.
//
// All endpoints live under /api/v1/socops/* and are gated server-side by
// @RequireModule("SOCOPS"). Frontend hides nav entries that aren't in the
// authenticated user's enabledModules.

import { api } from './api';

// ─── Page wrapper (matches Spring Data Page<T>) ───────────────────────
export interface PageResp<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;       // current page index (0-based)
  size: number;
  first: boolean;
  last: boolean;
}

const pageQ = (page = 0, size = 50) => `?page=${page}&size=${size}`;

// ─── Dashboard ───────────────────────────────────────────────────────
export interface SeasonalOverlayBadge {
  overlayCode: string;
  titleEn: string;
  titleAr: string;
  hijriLabel: string;
  tighteningKris: boolean;
  tighteningSlas: boolean;
}

export interface EventBucket {
  id: number;
  severity: string;
  ruleName: string;
  status: string;
  sourceSystem: string;
  eventTime: string;
  slaDueAt: string;
}

export interface RegulatorExamBucket {
  id: number;
  examinationCode: string;
  regulator: string;
  titleEn: string;
  status: string;
  responseDueAt: string;
}

export interface SocOpsDashboard {
  openEventsCritical: number;
  openEventsHigh: number;
  openEventsMedium: number;
  openEventsTotal: number;
  eventsApproachingSlaBreach: number;
  mttdMinutes: number | null;
  mttrMinutes: number | null;
  activePlaybookInvocations: number;
  playbookTotalCount: number;
  actionableTiUnactioned: number;
  actionableTiTotal: number;
  scannerRunsLast24h: number;
  scannerCriticalsOpen: number;
  pentestsInFieldwork: number;
  pentestsRetestDue: number;
  certsExpiring30d: number;
  certsExpiring90d: number;
  certsQuantumUnsafe: number;
  sbomComponentsTotal: number;
  sbomComponentsWithCves: number;
  awarenessCampaignsRunning: number;
  awarenessLastPassRatePercent: number | null;
  regulatorExamsOpen: number;
  regulatorExamsResponseDueSoon: number;
  regulatorExamsByRegulator: Record<string, number>;
  activeOverlays: SeasonalOverlayBadge[];
  topRecentEvents: EventBucket[];
  upcomingExams: RegulatorExamBucket[];
}

// ─── Security events ─────────────────────────────────────────────────
export interface SecurityEvent {
  id: number;
  externalId: string;
  sourceSystem: string;
  eventTime: string;
  ingestedAt: string;
  severity: string;
  category: string | null;
  ruleName: string | null;
  sourceHost: string | null;
  sourceIp: string | null;
  destinationHost: string | null;
  destinationIp: string | null;
  username: string | null;
  assetId: number | null;
  status: string;
  assignedToUserId: number | null;
  incidentId: number | null;
  triagedAt: string | null;
  resolvedAt: string | null;
  slaDueAt: string | null;
  notes: string | null;
}

// ─── IR playbooks ────────────────────────────────────────────────────
export interface IrPlaybook {
  id: number;
  playbookCode: string;
  titleEn: string;
  titleAr: string | null;
  descriptionEn: string | null;
  descriptionAr: string | null;
  category: string;
  severityTrigger: string | null;
  targetMttdMinutes: number | null;
  targetMttrMinutes: number | null;
  alignedFrameworks: string | null;
  status: string;
  version: number;
  invocationCount: number;
  lastReviewedAt: string | null;
}

export interface IrPlaybookStep {
  id: number;
  playbookId: number;
  stepOrder: number;
  phase: string;
  titleEn: string;
  titleAr: string | null;
  instructionsEn: string | null;
  instructionsAr: string | null;
  responsibleRole: string | null;
  estimatedMinutes: number | null;
  isCritical: boolean;
}

// ─── Pentests ────────────────────────────────────────────────────────
export interface PentestEngagement {
  id: number;
  engagementCode: string;
  titleEn: string;
  titleAr: string | null;
  pentestType: string;
  scope: string | null;
  vendorName: string | null;
  ncaCertifiedVendor: boolean;
  startedAt: string | null;
  completedAt: string | null;
  status: string;
  findingsTotal: number;
  findingsCritical: number;
  findingsHigh: number;
  retestDueAt: string | null;
  retestCompletedAt: string | null;
  costSar: number | null;
  alignedFrameworks: string | null;
}

// ─── Regulator exams ─────────────────────────────────────────────────
export interface RegulatorExamination {
  id: number;
  examinationCode: string;
  regulator: string;
  examinationType: string;
  titleEn: string;
  titleAr: string | null;
  scopeAreas: string | null;
  leadExaminerName: string | null;
  notifiedAt: string | null;
  fieldworkStartAt: string | null;
  fieldworkEndAt: string | null;
  draftReportAt: string | null;
  finalReportAt: string | null;
  responseDueAt: string | null;
  responseSubmittedAt: string | null;
  closedAt: string | null;
  status: string;
  findingsTotal: number;
  findingsCritical: number;
  findingsHigh: number;
  findingsMedLow: number;
  overallRating: string | null;
  relatedFramework: string | null;
}

// ─── Seasonal overlays ───────────────────────────────────────────────
export interface SeasonalOverlay {
  id: number;
  overlayCode: string;
  titleEn: string;
  titleAr: string | null;
  startsAt: string;
  endsAt: string;
  hijriLabel: string | null;
  threatProfile: string | null;
  autoTightenKris: boolean;
  autoTightenSlas: boolean;
  isActive: boolean;
}

// ─── API surface ─────────────────────────────────────────────────────
export const socopsApi = {
  dashboard: () => api.get<SocOpsDashboard>('/api/v1/socops/dashboard'),

  events:        (page = 0, size = 50) =>
    api.get<PageResp<SecurityEvent>>(`/api/v1/socops/events${pageQ(page, size)}`),
  eventGet:      (id: number) =>
    api.get<SecurityEvent>(`/api/v1/socops/events/${id}`),
  eventTriage:   (id: number, assigneeUserId?: number) =>
    api.post<SecurityEvent>(`/api/v1/socops/events/${id}/triage`,
      assigneeUserId === undefined ? {} : { assigneeUserId }),
  eventResolve:  (id: number, resolution: string, falsePositive = false) =>
    api.post<SecurityEvent>(`/api/v1/socops/events/${id}/resolve`,
      { resolution, falsePositive }),

  playbooks:     () => api.get<IrPlaybook[]>('/api/v1/socops/playbooks'),
  templates:     () => api.get<IrPlaybook[]>('/api/v1/socops/playbooks/templates'),
  playbookGet:   (id: number) => api.get<IrPlaybook>(`/api/v1/socops/playbooks/${id}`),
  playbookSteps: (id: number) => api.get<IrPlaybookStep[]>(`/api/v1/socops/playbooks/${id}/steps`),
  cloneTemplate: (templateCode: string) =>
    api.post<IrPlaybook>('/api/v1/socops/playbooks/clone', { templateCode }),
  invokePlaybook: (id: number) =>
    api.post<IrPlaybook>(`/api/v1/socops/playbooks/${id}/invoke`),

  pentests:      (page = 0, size = 50) =>
    api.get<PageResp<PentestEngagement>>(`/api/v1/socops/pentests${pageQ(page, size)}`),
  pentestGet:    (id: number) =>
    api.get<PentestEngagement>(`/api/v1/socops/pentests/${id}`),

  regulatorExams: (page = 0, size = 50) =>
    api.get<PageResp<RegulatorExamination>>(`/api/v1/socops/regulator-exams${pageQ(page, size)}`),
  regulatorExamGet: (id: number) =>
    api.get<RegulatorExamination>(`/api/v1/socops/regulator-exams/${id}`),

  activeOverlays: () =>
    api.get<SeasonalOverlay[]>('/api/v1/socops/seasonal-overlays/active-now'),
  allOverlays:    () =>
    api.get<SeasonalOverlay[]>('/api/v1/socops/seasonal-overlays'),

  certsExpiring: (days = 30) =>
    api.get<unknown[]>(`/api/v1/socops/crypto-assets/expiring?days=${days}`),
};
