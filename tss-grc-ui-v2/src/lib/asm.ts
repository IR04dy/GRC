// Attack Surface Management (ASM) — Sprint 11.
//
// Tri-track regulator surface for the CISO's external attack surface:
//   - NCA ECC-2 control 2-3 (Asset Management)
//   - SAMA Cyber Security Framework 4.2.1 (internet-facing inventory)
//   - NCA OTCC + CISA Cyber Hygiene Services (continuous monitoring)
//
// Saudi-native bilingual page title:
//   ar: إدارة سطح الهجوم   ← NOT literal "إدارة سطح الهجوم الخارجي"
//   en: Attack Surface Management (ASM)

import { api } from './api';

export type AsmAssetType =
  | 'WEB_APPLICATION' | 'MOBILE_APP' | 'API_ENDPOINT' | 'DNS_RECORD'
  | 'CERTIFICATE' | 'IP_RANGE' | 'EMAIL_SERVER' | 'FILE_SHARE'
  | 'VPN_GATEWAY' | 'CLOUD_BUCKET' | 'GIT_REPO' | 'ADMIN_PORTAL'
  | 'MARKETING_SITE' | 'SHADOW_IT_SAAS';

export type AsmExposure = 'PUBLIC' | 'PARTNER' | 'INTERNAL' | 'RESTRICTED';

export type AsmCriticality = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type AsmLifecycleStage =
  | 'DISCOVERED' | 'IN_REVIEW' | 'APPROVED' | 'SHADOW_IT_PENDING' | 'RETIRED';

export type AsmDiscoverySource =
  | 'MANUAL' | 'TENABLE' | 'CROWDSTRIKE' | 'DNS_SCAN' | 'CERT_TRANSPARENCY'
  | 'SHODAN' | 'CCM_RUNNER' | 'CUSTOMER_TICKET';

export type AsmProtocol = 'HTTP' | 'HTTPS' | 'TCP' | 'UDP' | 'SSH' | 'FTP' | 'SMTP' | 'DNS' | 'ICMP';

export type AsmFindingType =
  | 'EXPIRED_CERT' | 'WEAK_CIPHER' | 'UNPATCHED' | 'EXPOSED_PORT'
  | 'MISSING_HEADER' | 'OUTDATED_FRAMEWORK' | 'UNVERIFIED_OWNER'
  | 'NO_WAF' | 'NO_RATE_LIMIT' | 'DNS_TAKEOVER_RISK';

export type AsmFindingSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';

export type AsmFindingSource = 'TENABLE' | 'CROWDSTRIKE' | 'CCM_RUNNER' | 'MANUAL' | 'AUTOMATED_PROBE';

export type AsmFindingStatus =
  | 'OPEN' | 'INVESTIGATING' | 'ACCEPTED_RISK' | 'FIXED' | 'FALSE_POSITIVE' | 'OUT_OF_SCOPE';

export type ShadowItDiscoveredVia =
  | 'EMAIL_SIGNUPS' | 'EXPENSE_REPORT' | 'NETWORK_TRAFFIC' | 'USER_DECLARATION';

export type ShadowItDataClassification =
  | 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED' | 'UNKNOWN';

export type ShadowItStatus =
  | 'PENDING_REVIEW' | 'IN_PROGRESS' | 'SANCTIONED' | 'BLOCKED' | 'MIGRATED_TO_SANCTIONED' | 'NA';

export interface AsmAssetResponse {
  id: number;
  externalId: string;
  assetCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  assetType: AsmAssetType;
  hostname?: string;
  ipAddress?: string;
  port?: number;
  protocol?: AsmProtocol;
  certSubject?: string;
  certIssuer?: string;
  certExpiresAt?: string;
  assetOwnerUserId: number;
  businessUnitEn?: string;
  businessUnitAr?: string;
  exposure: AsmExposure;
  criticality: AsmCriticality;
  lifecycleStage: AsmLifecycleStage;
  discoverySource: AsmDiscoverySource;
  discoveredAt: string;
  approvedAt?: string;
  retiredAt?: string;
  tags?: string;
  riskScore?: number | string;
  createdAt?: string;
  updatedAt?: string;
}

export interface AsmAssetRequest {
  assetCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  assetType: AsmAssetType;
  hostname?: string;
  ipAddress?: string;
  port?: number;
  protocol?: AsmProtocol;
  certSubject?: string;
  certIssuer?: string;
  certExpiresAt?: string;
  assetOwnerUserId: number;
  businessUnitEn?: string;
  businessUnitAr?: string;
  exposure: AsmExposure;
  criticality: AsmCriticality;
  lifecycleStage?: AsmLifecycleStage;
  discoverySource: AsmDiscoverySource;
  tags?: string;
}

export interface AsmFindingResponse {
  id: number;
  externalId: string;
  assetId: number;
  assetExternalId?: string;
  assetCode?: string;
  findingType: AsmFindingType;
  severity: AsmFindingSeverity;
  source: AsmFindingSource;
  sourceRef?: string;
  descriptionEn: string;
  descriptionAr?: string;
  recommendationEn?: string;
  recommendationAr?: string;
  openedAt: string;
  closedAt?: string;
  status: AsmFindingStatus;
  capaTaskId?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface AsmFindingRequest {
  assetExternalId: string;
  findingType: AsmFindingType;
  severity: AsmFindingSeverity;
  source: AsmFindingSource;
  sourceRef?: string;
  descriptionEn: string;
  descriptionAr?: string;
  recommendationEn?: string;
  recommendationAr?: string;
}

export interface ShadowItResponse {
  id: number;
  externalId: string;
  serviceName: string;
  serviceNameAr?: string;
  vendorDomain?: string;
  discoveredVia: ShadowItDiscoveredVia;
  firstObservedAt: string;
  observedUserCount?: number;
  dataClassification: ShadowItDataClassification;
  pdplDataProcessed: boolean;
  status: ShadowItStatus;
  sanctionedVendorId?: number;
  promotedAssetId?: number;
  assignedToUserId?: number;
  deadline?: string;
  notesEn?: string;
  notesAr?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ShadowItRequest {
  serviceName: string;
  serviceNameAr?: string;
  vendorDomain?: string;
  discoveredVia: ShadowItDiscoveredVia;
  observedUserCount?: number;
  dataClassification?: ShadowItDataClassification;
  pdplDataProcessed?: boolean;
  assignedToUserId?: number;
  deadline?: string;
  notesEn?: string;
  notesAr?: string;
}

export interface AsmDashboardSnapshot {
  totalAssets: number;
  approvedAssets: number;
  shadowItPendingAssets: number;
  publicCriticalAssets: number;
  openFindings: number;
  criticalOpenFindings: number;
  pendingShadowIt: number;
  pdplShadowIt: number;
  topRiskAssets: AsmAssetResponse[];
  recentFindings: AsmFindingResponse[];
  pendingShadowItList: ShadowItResponse[];
}

// ─── REST client ─────────────────────────────────────────────────────

export const asmApi = {
  // Assets
  listAssets:    () => api.get<AsmAssetResponse[]>('/api/v1/asm/assets'),
  getAsset:      (externalId: string) => api.get<AsmAssetResponse>(`/api/v1/asm/assets/${externalId}`),
  createAsset:   (body: AsmAssetRequest) => api.post<AsmAssetResponse>('/api/v1/asm/assets', body),
  updateAsset:   (externalId: string, body: AsmAssetRequest) =>
    api.put<AsmAssetResponse>(`/api/v1/asm/assets/${externalId}`, body),
  transition:    (externalId: string, targetStage: AsmLifecycleStage, note?: string) =>
    api.post<AsmAssetResponse>(`/api/v1/asm/assets/${externalId}/transition`, { targetStage, note }),

  // Findings
  listFindings:         () => api.get<AsmFindingResponse[]>('/api/v1/asm/findings'),
  listFindingsForAsset: (externalId: string) =>
    api.get<AsmFindingResponse[]>(`/api/v1/asm/assets/${externalId}/findings`),
  getFinding:           (externalId: string) =>
    api.get<AsmFindingResponse>(`/api/v1/asm/findings/${externalId}`),
  recordFinding:        (body: AsmFindingRequest) =>
    api.post<AsmFindingResponse>('/api/v1/asm/findings', body),
  transitionFinding:    (externalId: string, targetStatus: AsmFindingStatus, note?: string) =>
    api.post<AsmFindingResponse>(`/api/v1/asm/findings/${externalId}/transition`, { targetStatus, note }),

  // Shadow IT
  listShadowIt:    () => api.get<ShadowItResponse[]>('/api/v1/asm/shadow-it'),
  getShadowIt:     (externalId: string) =>
    api.get<ShadowItResponse>(`/api/v1/asm/shadow-it/${externalId}`),
  recordShadowIt:  (body: ShadowItRequest) =>
    api.post<ShadowItResponse>('/api/v1/asm/shadow-it', body),
  sanctionShadowIt: (externalId: string, sanctionedVendorId: number, note?: string) =>
    api.post<ShadowItResponse>(`/api/v1/asm/shadow-it/${externalId}/sanction`, { sanctionedVendorId, note }),
  blockShadowIt:    (externalId: string, note?: string) =>
    api.post<ShadowItResponse>(`/api/v1/asm/shadow-it/${externalId}/block`, { note }),

  // Dashboard
  dashboard: () => api.get<AsmDashboardSnapshot>('/api/v1/asm/dashboard'),
};

// ─── Saudi-native bilingual display labels ──────────────────────────

export const ASM_ASSET_TYPE_LABEL: Record<AsmAssetType, { en: string; ar: string }> = {
  WEB_APPLICATION: { en: 'Web application',  ar: 'تطبيق ويب' },
  MOBILE_APP:      { en: 'Mobile app',       ar: 'تطبيق جوّال' },
  API_ENDPOINT:    { en: 'API endpoint',     ar: 'نقطة استدعاء برمجي' },
  DNS_RECORD:      { en: 'DNS record',       ar: 'سجل DNS' },
  CERTIFICATE:     { en: 'TLS certificate',  ar: 'شهادة TLS' },
  IP_RANGE:        { en: 'IP range',         ar: 'نطاق عناوين IP' },
  EMAIL_SERVER:    { en: 'Email server',     ar: 'خادم بريد إلكتروني' },
  FILE_SHARE:      { en: 'File share',       ar: 'مشاركة ملفات' },
  VPN_GATEWAY:     { en: 'VPN gateway',      ar: 'بوّابة شبكة افتراضية' },
  CLOUD_BUCKET:    { en: 'Cloud bucket',     ar: 'حاوية تخزين سحابي' },
  GIT_REPO:        { en: 'Git repository',   ar: 'مستودع Git' },
  ADMIN_PORTAL:    { en: 'Admin portal',     ar: 'بوّابة إدارة' },
  MARKETING_SITE:  { en: 'Marketing site',   ar: 'موقع تسويقي' },
  SHADOW_IT_SAAS:  { en: 'Shadow-IT SaaS',   ar: 'خدمة SaaS من تقنية الظل' },
};

export const ASM_EXPOSURE_LABEL: Record<AsmExposure, { en: string; ar: string }> = {
  PUBLIC:     { en: 'Public',     ar: 'عام' },
  PARTNER:    { en: 'Partner',    ar: 'شريك' },
  INTERNAL:   { en: 'Internal',   ar: 'داخلي' },
  RESTRICTED: { en: 'Restricted', ar: 'مقيّد' },
};

export const ASM_EXPOSURE_TONE: Record<AsmExposure, 'danger' | 'warning' | 'info' | 'neutral'> = {
  PUBLIC:     'danger',
  PARTNER:    'warning',
  INTERNAL:   'info',
  RESTRICTED: 'neutral',
};

export const ASM_CRITICALITY_LABEL: Record<AsmCriticality, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const ASM_CRITICALITY_TONE: Record<AsmCriticality, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH:     'warning',
  MEDIUM:   'info',
  LOW:      'neutral',
};

// Saudi-native lifecycle wording — operator-natural, not literal translations.
export const ASM_LIFECYCLE_LABEL: Record<AsmLifecycleStage, { en: string; ar: string }> = {
  DISCOVERED:        { en: 'Discovered',        ar: 'مُكتشَف' },
  IN_REVIEW:         { en: 'In review',         ar: 'قيد المراجعة' },
  APPROVED:          { en: 'Approved',          ar: 'مُعتَمد' },
  SHADOW_IT_PENDING: { en: 'Shadow-IT pending', ar: 'في انتظار تنظيم تقنية الظل' },
  RETIRED:           { en: 'Retired',           ar: 'متقاعد' },
};

export const ASM_LIFECYCLE_TONE: Record<AsmLifecycleStage, 'info' | 'warning' | 'success' | 'neutral'> = {
  DISCOVERED:        'info',
  IN_REVIEW:         'warning',
  APPROVED:          'success',
  SHADOW_IT_PENDING: 'warning',
  RETIRED:           'neutral',
};

export const ASM_DISCOVERY_SOURCE_LABEL: Record<AsmDiscoverySource, { en: string; ar: string }> = {
  MANUAL:            { en: 'Manual entry',        ar: 'إدخال يدوي' },
  TENABLE:           { en: 'Tenable',             ar: 'تينابل' },
  CROWDSTRIKE:       { en: 'CrowdStrike',         ar: 'كراودسترايك' },
  DNS_SCAN:          { en: 'DNS scan',            ar: 'مسح DNS' },
  CERT_TRANSPARENCY: { en: 'Cert transparency',   ar: 'شفافية الشهادات' },
  SHODAN:            { en: 'Shodan',              ar: 'شودان' },
  CCM_RUNNER:        { en: 'CCM runner',          ar: 'محرّك المراقبة المستمرة' },
  CUSTOMER_TICKET:   { en: 'Customer ticket',     ar: 'تذكرة من العميل' },
};

export const ASM_FINDING_TYPE_LABEL: Record<AsmFindingType, { en: string; ar: string }> = {
  EXPIRED_CERT:       { en: 'Expired certificate', ar: 'شهادة منتهية' },
  WEAK_CIPHER:        { en: 'Weak cipher',          ar: 'خوارزمية تشفير ضعيفة' },
  UNPATCHED:          { en: 'Unpatched',            ar: 'ينقصها تحديث أمني' },
  EXPOSED_PORT:       { en: 'Exposed port',         ar: 'منفذ مكشوف' },
  MISSING_HEADER:     { en: 'Missing security header', ar: 'ترويسة أمنية مفقودة' },
  OUTDATED_FRAMEWORK: { en: 'Outdated framework',   ar: 'إطار عمل قديم' },
  UNVERIFIED_OWNER:   { en: 'Unverified owner',     ar: 'مالك غير موثّق' },
  NO_WAF:             { en: 'No WAF in front',      ar: 'بلا جدار حماية تطبيقات' },
  NO_RATE_LIMIT:      { en: 'No rate limit',        ar: 'بلا تحديد معدّل' },
  DNS_TAKEOVER_RISK:  { en: 'DNS takeover risk',    ar: 'مخاطر سيطرة على DNS' },
};

export const ASM_FINDING_SEVERITY_LABEL: Record<AsmFindingSeverity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
  INFO:     { en: 'Info',     ar: 'إعلامي' },
};

export const ASM_FINDING_SEVERITY_TONE: Record<AsmFindingSeverity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low' | 'neutral'> = {
  CRITICAL: 'risk-crit',
  HIGH:     'risk-high',
  MEDIUM:   'risk-med',
  LOW:      'risk-low',
  INFO:     'neutral',
};

export const ASM_FINDING_STATUS_LABEL: Record<AsmFindingStatus, { en: string; ar: string }> = {
  OPEN:           { en: 'Open',           ar: 'مفتوح' },
  INVESTIGATING:  { en: 'Investigating',  ar: 'قيد التحقيق' },
  ACCEPTED_RISK:  { en: 'Accepted risk',  ar: 'مخاطرة مقبولة' },
  FIXED:          { en: 'Fixed',          ar: 'مُعالَج' },
  FALSE_POSITIVE: { en: 'False positive', ar: 'إيجابي كاذب' },
  OUT_OF_SCOPE:   { en: 'Out of scope',   ar: 'خارج النطاق' },
};

export const ASM_FINDING_STATUS_TONE: Record<AsmFindingStatus, 'danger' | 'warning' | 'success' | 'neutral' | 'info'> = {
  OPEN:           'danger',
  INVESTIGATING:  'warning',
  ACCEPTED_RISK:  'info',
  FIXED:          'success',
  FALSE_POSITIVE: 'neutral',
  OUT_OF_SCOPE:   'neutral',
};

export const SHADOW_IT_STATUS_LABEL: Record<ShadowItStatus, { en: string; ar: string }> = {
  PENDING_REVIEW:         { en: 'Pending review',         ar: 'في انتظار المراجعة' },
  IN_PROGRESS:            { en: 'In progress',            ar: 'قيد التنفيذ' },
  SANCTIONED:             { en: 'Sanctioned',             ar: 'موافَق عليها' },
  MIGRATED_TO_SANCTIONED: { en: 'Migrated to sanctioned', ar: 'مُنتقَل لمسار رسمي' },
  BLOCKED:                { en: 'Blocked',                ar: 'محجوب' },
  NA:                     { en: 'Not applicable',         ar: 'غير منطبق' },
};

export const SHADOW_IT_STATUS_TONE: Record<ShadowItStatus, 'danger' | 'warning' | 'success' | 'neutral' | 'info'> = {
  PENDING_REVIEW:         'warning',
  IN_PROGRESS:            'info',
  SANCTIONED:             'success',
  MIGRATED_TO_SANCTIONED: 'success',
  BLOCKED:                'danger',
  NA:                     'neutral',
};

export const SHADOW_IT_DATA_CLASS_LABEL: Record<ShadowItDataClassification, { en: string; ar: string }> = {
  PUBLIC:       { en: 'Public',       ar: 'عام' },
  INTERNAL:     { en: 'Internal',     ar: 'داخلي' },
  CONFIDENTIAL: { en: 'Confidential', ar: 'سرّي' },
  RESTRICTED:   { en: 'Restricted',   ar: 'مقيّد' },
  UNKNOWN:      { en: 'Unknown',      ar: 'غير معروف' },
};

export const SHADOW_IT_DISCOVERED_VIA_LABEL: Record<ShadowItDiscoveredVia, { en: string; ar: string }> = {
  EMAIL_SIGNUPS:    { en: 'Email signups',    ar: 'تسجيلات البريد الإلكتروني' },
  EXPENSE_REPORT:   { en: 'Expense report',   ar: 'تقرير مصروفات' },
  NETWORK_TRAFFIC:  { en: 'Network traffic',  ar: 'حركة الشبكة' },
  USER_DECLARATION: { en: 'User declaration', ar: 'إقرار من المستخدم' },
};

/** Format a numeric risk score 0–100 for the badge. */
export function formatRiskScore(score: number | string | undefined | null): string {
  if (score == null) return '—';
  const n = typeof score === 'string' ? parseFloat(score) : score;
  if (Number.isNaN(n)) return '—';
  return n.toFixed(1);
}

/** Tone for a numeric risk score — 60+ danger, 30+ warning, else info. */
export function riskScoreTone(score: number | string | undefined | null): 'danger' | 'warning' | 'info' | 'neutral' {
  if (score == null) return 'neutral';
  const n = typeof score === 'string' ? parseFloat(score) : score;
  if (Number.isNaN(n)) return 'neutral';
  if (n >= 60) return 'danger';
  if (n >= 30) return 'warning';
  return 'info';
}
