// SOC Operations API client + types — wires the frontend to the V150 +
// V151 + V152 backbone shipped by PRs #97 / #98 / #99 and the V153
// automation rules layered on top.
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

// ─── IR playbook RUNS (SOC-B) ────────────────────────────────────────
// Invoking a playbook instantiates a run + one clocked, owner-assigned task
// per step. Completing tasks advances the run to COMPLETED.
export interface IrPlaybookRun {
  id: number;
  playbookId: number;
  playbookCode: string | null;
  titleEn: string | null;
  titleAr: string | null;
  status: string;                 // RUNNING | COMPLETED | CANCELLED
  incidentId: number | null;
  triggeredByEventId: number | null;
  invokedBy: string | null;
  startedAt: string;
  completedAt: string | null;
  totalTasks: number;
  completedTasks: number;
  targetMttrMinutes: number | null;
  notes: string | null;
}

export interface IrPlaybookRunTask {
  id: number;
  runId: number;
  playbookId: number;
  stepId: number | null;
  stepOrder: number;
  phase: string;
  titleEn: string;
  titleAr: string | null;
  instructionsEn: string | null;
  instructionsAr: string | null;
  ownerRole: string | null;
  status: string;                 // PENDING | IN_PROGRESS | DONE | SKIPPED
  isCritical: boolean;
  estimatedMinutes: number | null;
  dueAt: string | null;
  startedAt: string | null;
  completedAt: string | null;
  completedBy: string | null;
  notes: string | null;
}

export interface IrPlaybookRunDetail {
  run: IrPlaybookRun;
  tasks: IrPlaybookRunTask[];
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

// ─── V150 entities (catalog completion) ─────────────────────────────

export interface ThreatIntel {
  id: number;
  externalId: string;
  source: string;                // Saudi-CERT | NCA | FS-ISAC | MISP | …
  publishedAt: string;
  expiresAt: string | null;
  titleEn: string;
  titleAr: string | null;
  summary: string | null;
  iocType: string | null;        // IP | DOMAIN | HASH | URL | …
  iocValue: string | null;
  severity: string | null;
  confidence: string | null;
  tlp: string | null;            // WHITE | GREEN | AMBER | RED
  mitreTactics: string | null;
  mitreTechniques: string | null;
  affectedSectors: string | null;
  actioned: boolean;
  actionedAt: string | null;
  actionNotes: string | null;
}

export interface ScannerRun {
  id: number;
  scanner: string;               // TENABLE | QUALYS | DEFENDER | …
  scanName: string | null;
  scope: string | null;
  startedAt: string;
  completedAt: string | null;
  status: string;
  findingsTotal: number;
  findingsCritical: number;
  findingsHigh: number;
  findingsMedium: number;
  findingsLow: number;
  assetsScanned: number;
  errorMessage: string | null;
}

export interface CryptoAsset {
  id: number;
  assetCode: string;
  assetType: string;              // CERT_TLS | CERT_SIGNING | KEY_HSM | …
  titleEn: string;
  titleAr: string | null;
  issuedBy: string | null;
  subjectDn: string | null;
  fingerprint: string | null;
  algorithm: string | null;
  keyLength: number | null;
  quantumSafe: boolean;
  validFrom: string | null;
  validUntil: string | null;
  deployedOn: string | null;
  owner: string | null;
  rotationPeriodDays: number | null;
  lastRotatedAt: string | null;
  classification: string | null;
  status: string;
}

export interface SbomComponent {
  id: number;
  applicationId: number | null;
  applicationName: string;
  componentName: string;
  componentVersion: string | null;
  packageEcosystem: string | null;   // npm | maven | pypi | …
  purl: string | null;
  license: string | null;
  supplier: string | null;
  hasKnownCves: boolean;
  highestCveSeverity: string | null;
  upgradeAvailable: boolean;
  lastScannedAt: string | null;
}

export interface ZtMaturity {
  id: number;
  assessmentCode: string;
  pillar: string;                 // IDENTITY | DEVICES | NETWORK | APPS | DATA
  currentStage: string;           // TRADITIONAL | INITIAL | ADVANCED | OPTIMAL
  targetStage: string | null;
  score: number | null;
  evidenceSummary: string | null;
  gapSummary: string | null;
  actionPlan: string | null;
  assessedAt: string | null;
  assessedBy: string | null;
}

export interface AwarenessCampaign {
  id: number;
  campaignCode: string;
  titleEn: string;
  titleAr: string | null;
  campaignType: string;            // PHISH_SIM | TRAINING | NEWSLETTER | …
  targetAudience: string | null;
  targetUserCount: number;
  startedAt: string | null;
  endedAt: string | null;
  status: string;
  deliveredCount: number;
  openedCount: number;
  clickedCount: number;
  reportedCount: number;
  completedCount: number;
  repeatOffenderCount: number;
  passRatePercent: number | null;
  alignedControls: string | null;
  notes: string | null;
}

// ─── V152 Tier-2 + Tier-3 entities ──────────────────────────────────

// OT/ICS asset (Purdue-level inventory) — Aramco / SABIC / Ma'aden scale
export interface OtAsset {
  id: number;
  assetCode: string;
  titleEn: string;
  titleAr: string | null;
  purdueLevel: number;          // 0..5
  assetClass: string;           // PLC | DCS | SCADA | HMI | RTU | HISTORIAN | …
  vendor: string | null;
  model: string | null;
  firmwareVersion: string | null;
  serialNumber: string | null;
  siteCode: string | null;
  siteName: string | null;
  processFunction: string | null;
  industrialProtocols: string | null;
  safetyIntegrityLevel: string | null;  // SIL1 | SIL2 | SIL3 | SIL4
  criticality: string;          // LOW | MEDIUM | HIGH | CRITICAL
  lastPatchedAt: string | null;
  patchWindow: string | null;
  remoteAccessEnabled: boolean;
  networkSegment: string | null;
  status: string;
  notes: string | null;
}

// IAM joiner-mover-leaver / privileged-access event
export interface IamJmlEvent {
  id: number;
  eventCode: string;
  eventType: string;            // JOINER | MOVER | LEAVER | PRIVILEGED_GRANT | …
  occurredAt: string;
  subjectUsername: string | null;
  subjectEmail: string | null;
  subjectDepartment: string | null;
  subjectJobTitle: string | null;
  sourceSystem: string;         // AD | Azure-AD | SailPoint | Okta | …
  targetSystem: string | null;
  roleOrEntitlement: string | null;
  privileged: boolean;
  outcome: string;              // PENDING | APPROVED | COMPLETED | REJECTED
  sodViolationDetected: boolean;
  sodViolationDetail: string | null;
  approverUsername: string | null;
  approverRole: string | null;
  requestedAt: string | null;
  approvedAt: string | null;
  completedAt: string | null;
  revokedAt?: string | null;
  reviewDueAt?: string | null;
}

// AppSec finding (SAST/DAST/IAST/SCA/secrets/IaC unified)
export interface AppsecFinding {
  id: number;
  externalId: string;
  scanner: string;              // VERACODE | CHECKMARX | SNYK | …
  scanType: string;             // SAST | DAST | IAST | SCA | SECRETS | IAC
  applicationName: string;
  applicationId: number | null;
  repositoryUrl: string | null;
  branch: string | null;
  findingTitle: string;
  cwe: string | null;
  cve: string | null;
  severity: string;
  cvssScore: number | null;
  filePath: string | null;
  lineNumber: number | null;
  componentName: string | null;
  componentVersion: string | null;
  fixedVersion: string | null;
  description: string | null;
  remediationAdvice: string | null;
  status: string;               // OPEN | TRIAGED | IN_PROGRESS | FIXED | …
  assignedToUserId: number | null;
  discoveredAt: string;
  fixedAt: string | null;
  slaDueAt: string | null;
  suppressionReason: string | null;
}

// DLP event (Microsoft Purview / Forcepoint / Symantec / Netskope)
export interface DlpEvent {
  id: number;
  externalId: string;
  sourceSystem: string;
  eventTime: string;
  severity: string;
  username: string | null;
  endpoint: string | null;
  channel: string | null;       // EMAIL | USB | CLOUD | WEB | …
  policyName: string | null;
  dataClassification: string | null;  // PUBLIC | INTERNAL | CONFIDENTIAL | SECRET | TOP_SECRET
  filename: string | null;
  fileSizeBytes: number | null;
  destination: string | null;
  action: string | null;        // BLOCKED | ALERTED | ALLOWED
  status: string;
  reviewerUserId: number | null;
  reviewedAt: string | null;
  reviewOutcome: string | null;
  notes: string | null;
}

// Cyber insurance policy
export interface CyberInsurance {
  id: number;
  policyNumber: string;
  carrierName: string;
  brokerName: string | null;
  policyType: string;
  coverageAreas: string | null;
  aggregateLimitSar: number | null;
  perEventLimitSar: number | null;
  retentionSar: number | null;
  effectiveFrom: string;
  effectiveTo: string;
  renewalDueAt: string | null;
  status: string;
  claimCount: number;
  lastClaimAt: string | null;
  lastClaimPaidSar: number | null;
  aggregateClaimsPaidSar: number | null;
  notes: string | null;
}

// Brand / dark-web monitoring alert
export interface BrandMonitoring {
  id: number;
  alertCode: string;
  sourceSystem: string;         // Recorded Future | Digital Shadows | SOCRadar | …
  detectedAt: string;
  severity: string;
  alertType: string | null;     // PHISHING_KIT | LEAKED_CRED | TYPOSQUAT | …
  brandKeyword: string | null;
  location: string | null;
  urlObserved: string | null;
  evidenceSummary: string | null;
  status: string;
  takedownRequestedAt: string | null;
  takedownCompletedAt: string | null;
  notes: string | null;
}

// Forensics artefact (chain-of-custody).
// Field names mirror the backend entity (`ForensicsArtefact`) verbatim — the
// previous shape (acquiredAt / custodian / location / md5 / sha256 / sha512 /
// disposalDueAt) was aspirational and never matched what Jackson serialised,
// so the register rendered blank Custodian / Acquired / Location / SHA-256 /
// Disposal columns. Page binds to these names.
export interface ForensicsArtefact {
  id: number;
  artefactCode: string;
  caseCode: string;
  artefactType: string | null;       // DISK_IMAGE | MEMORY_DUMP | PCAP | LOG | MOBILE | …
  titleEn: string | null;
  titleAr: string | null;
  capturedAt: string;                 // acquisition timestamp
  capturedByHandler: string | null;
  captureTool: string | null;
  captureMethod: string | null;
  sourceHost: string | null;
  sizeBytes: number | null;
  currentCustodian: string | null;
  currentLocation: string | null;
  md5Hash: string | null;
  sha256Hash: string | null;
  sha512Hash: string | null;
  integrityStatus: string;            // VERIFIED | UNKNOWN | COMPROMISED | CORRUPTED
  integrityVerifiedAt: string | null;
  legalHold: boolean;
  retentionUntil: string | null;      // disposal becomes due once retention expires
  privileged: boolean;
  notes: string | null;
}

// M&A cyber due diligence engagement.
// Field shape mirrors the backend entity (`MaDueDiligence`) verbatim —
// the previous shape (targetCompanyName / pifAligned / overallVerdict /
// phaseStartedAt / phaseDueAt) was aspirational and never matched what
// Jackson serialised. Page binds to these names.
export interface MaDueDiligence {
  id: number;
  engagementCode: string;
  titleEn: string;
  titleAr: string | null;
  targetEntityName: string;
  targetIndustry: string | null;
  targetCountry: string | null;
  dealType: string;              // ACQUISITION | MERGER | DIVESTITURE | JV
  phase: string;                 // PRE_LOI | PLANNING | DISCOVERY | ASSESSMENT | FINDINGS | INTEGRATION | CLOSED
  dealStatus: string | null;
  findingsTotal: number;
  findingsCritical: number;
  findingsHigh: number;
  cyberRecommendation: string | null; // GREEN | YELLOW | RED | DEAL_BLOCKER
  recommendationNotes: string | null;
  loiSignedAt: string | null;
  fieldworkStartAt: string | null;
  fieldworkEndAt: string | null;
  finalReportAt: string | null;
  dealCloseAt: string | null;
  integrationCompleteAt: string | null;
  notes: string | null;
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
  // SOC-C: promote a security event to a cyber incident (creates + links it).
  eventEscalate: (id: number, note?: string) =>
    api.post<SecurityEvent>(`/api/v1/socops/events/${id}/escalate`,
      note === undefined ? {} : { note }),

  playbooks:     () => api.get<IrPlaybook[]>('/api/v1/socops/playbooks'),
  templates:     () => api.get<IrPlaybook[]>('/api/v1/socops/playbooks/templates'),
  playbookGet:   (id: number) => api.get<IrPlaybook>(`/api/v1/socops/playbooks/${id}`),
  playbookSteps: (id: number) => api.get<IrPlaybookStep[]>(`/api/v1/socops/playbooks/${id}/steps`),
  cloneTemplate: (templateCode: string) =>
    api.post<IrPlaybook>('/api/v1/socops/playbooks/clone', { templateCode }),
  // SOC-B: invoking now instantiates a run + one clocked task per step.
  invokePlaybook: (id: number, body?: { incidentId?: number; note?: string }) =>
    api.post<IrPlaybookRunDetail>(`/api/v1/socops/playbooks/${id}/invoke`, body ?? {}),
  playbookRuns: (id: number) =>
    api.get<IrPlaybookRun[]>(`/api/v1/socops/playbooks/${id}/runs`),

  // Run execution (SOC-B).
  runs: () => api.get<IrPlaybookRun[]>('/api/v1/socops/playbook-runs'),
  runGet: (runId: number) =>
    api.get<IrPlaybookRunDetail>(`/api/v1/socops/playbook-runs/${runId}`),
  runStartTask: (runId: number, taskId: number) =>
    api.post<IrPlaybookRunDetail>(`/api/v1/socops/playbook-runs/${runId}/tasks/${taskId}/start`),
  runCompleteTask: (runId: number, taskId: number, note?: string) =>
    api.post<IrPlaybookRunDetail>(`/api/v1/socops/playbook-runs/${runId}/tasks/${taskId}/complete`,
      note === undefined ? {} : { note }),

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
    api.get<CryptoAsset[]>(`/api/v1/socops/crypto-assets/expiring?days=${days}`),

  // ── V150 register endpoints ──────────────────────────────────────
  threatIntel: (page = 0, size = 50) =>
    api.get<PageResp<ThreatIntel>>(`/api/v1/socops/threat-intel${pageQ(page, size)}`),
  threatIntelGet: (id: number) =>
    api.get<ThreatIntel>(`/api/v1/socops/threat-intel/${id}`),
  threatIntelAction: (id: number, body: { actioned: boolean; actionNotes?: string }) =>
    api.post<ThreatIntel>(`/api/v1/socops/threat-intel/${id}/action`, body),

  scannerRuns: (page = 0, size = 50) =>
    api.get<PageResp<ScannerRun>>(`/api/v1/socops/scanner-runs${pageQ(page, size)}`),
  scannerRunGet: (id: number) =>
    api.get<ScannerRun>(`/api/v1/socops/scanner-runs/${id}`),

  cryptoAssets: (page = 0, size = 50) =>
    api.get<PageResp<CryptoAsset>>(`/api/v1/socops/crypto-assets${pageQ(page, size)}`),
  cryptoAssetGet: (id: number) =>
    api.get<CryptoAsset>(`/api/v1/socops/crypto-assets/${id}`),

  sbom: (page = 0, size = 100) =>
    api.get<PageResp<SbomComponent>>(`/api/v1/socops/sbom${pageQ(page, size)}`),

  ztMaturity: () =>
    api.get<ZtMaturity[]>('/api/v1/socops/zt-maturity'),
  ztMaturityByCode: (assessmentCode: string) =>
    api.get<ZtMaturity[]>(`/api/v1/socops/zt-maturity/${encodeURIComponent(assessmentCode)}`),

  awareness: (page = 0, size = 50) =>
    api.get<PageResp<AwarenessCampaign>>(`/api/v1/socops/awareness${pageQ(page, size)}`),
  awarenessGet: (id: number) =>
    api.get<AwarenessCampaign>(`/api/v1/socops/awareness/${id}`),

  // ── V152 Tier-2 + Tier-3 ─────────────────────────────────────────
  otAssets: (page = 0, size = 50) =>
    api.get<PageResp<OtAsset>>(`/api/v1/socops/ot-assets${pageQ(page, size)}`),
  otAssetGet: (id: number) =>
    api.get<OtAsset>(`/api/v1/socops/ot-assets/${id}`),

  iamJml: (page = 0, size = 50) =>
    api.get<PageResp<IamJmlEvent>>(`/api/v1/socops/iam-jml${pageQ(page, size)}`),
  iamJmlGet: (id: number) =>
    api.get<IamJmlEvent>(`/api/v1/socops/iam-jml/${id}`),
  /** Privileged-access entitlements whose recertification is due within `days`
   *  (SAMA CSF 3.3.5 / NCA ECC 2-2-3 periodic privileged-access review). */
  iamJmlReviewsDue: (days = 30) =>
    api.get<IamJmlEvent[]>(`/api/v1/socops/iam-jml/privileged-reviews-due?days=${days}`),

  appsec: (page = 0, size = 50) =>
    api.get<PageResp<AppsecFinding>>(`/api/v1/socops/appsec${pageQ(page, size)}`),
  appsecGet: (id: number) =>
    api.get<AppsecFinding>(`/api/v1/socops/appsec/${id}`),

  dlp: (page = 0, size = 50) =>
    api.get<PageResp<DlpEvent>>(`/api/v1/socops/dlp${pageQ(page, size)}`),
  dlpGet: (id: number) =>
    api.get<DlpEvent>(`/api/v1/socops/dlp/${id}`),
  dlpReview: (id: number, outcome: string, notes?: string) =>
    api.post<DlpEvent>(`/api/v1/socops/dlp/${id}/review`, { outcome, notes: notes ?? '' }),

  cyberInsurance: () =>
    api.get<CyberInsurance[]>('/api/v1/socops/cyber-insurance'),
  cyberInsuranceGet: (id: number) =>
    api.get<CyberInsurance>(`/api/v1/socops/cyber-insurance/${id}`),
  cyberInsuranceRenewals: (days = 90) =>
    api.get<CyberInsurance[]>(`/api/v1/socops/cyber-insurance/renewals-due?days=${days}`),

  brandAlerts: (page = 0, size = 50) =>
    api.get<PageResp<BrandMonitoring>>(`/api/v1/socops/brand-monitoring${pageQ(page, size)}`),
  brandAlertGet: (id: number) =>
    api.get<BrandMonitoring>(`/api/v1/socops/brand-monitoring/${id}`),
  brandRequestTakedown: (id: number) =>
    api.post<BrandMonitoring>(`/api/v1/socops/brand-monitoring/${id}/request-takedown`),
  brandCompleteTakedown: (id: number, notes?: string) =>
    api.post<BrandMonitoring>(`/api/v1/socops/brand-monitoring/${id}/complete-takedown`,
      { notes: notes ?? '' }),

  forensics: (page = 0, size = 50) =>
    api.get<PageResp<ForensicsArtefact>>(`/api/v1/socops/forensics${pageQ(page, size)}`),
  forensicsGet: (id: number) =>
    api.get<ForensicsArtefact>(`/api/v1/socops/forensics/${id}`),
  forensicsByCase: (caseCode: string) =>
    api.get<ForensicsArtefact[]>(`/api/v1/socops/forensics/by-case/${encodeURIComponent(caseCode)}`),
  forensicsTransferCustody: (id: number, newCustodian: string, newLocation: string, notes?: string) =>
    api.post<ForensicsArtefact>(`/api/v1/socops/forensics/${id}/transfer-custody`,
      { newCustodian, newLocation, notes: notes ?? '' }),
  forensicsVerify: (id: number, verified = true) =>
    api.post<ForensicsArtefact>(`/api/v1/socops/forensics/${id}/verify-integrity`,
      { verified }),

  maDueDiligence: (page = 0, size = 50) =>
    api.get<PageResp<MaDueDiligence>>(`/api/v1/socops/ma-due-diligence${pageQ(page, size)}`),
  maDueDiligenceGet: (id: number) =>
    api.get<MaDueDiligence>(`/api/v1/socops/ma-due-diligence/${id}`),
  maTransitionPhase: (id: number, phase: string) =>
    api.post<MaDueDiligence>(`/api/v1/socops/ma-due-diligence/${id}/transition`, { phase }),
};
