// CCM (Continuous Control Monitoring) Engine — Sprint 11.
//
// Tri-track regulatory driver: SAMA CSF 2.6 (Continuous Monitoring),
// NCA ECC-2 control 2-12 (Cybersecurity Event Logs & Monitoring), and
// PDPL Article 16 (retention enforcement). Replaces the manual
// "auditor screenshots AD, pastes into Word doc" ritual.
//
// Saudi-native bilingual page title:
//   ar: المراقبة المستمرة للضوابط  ← NOT literal "المراقبة المتواصلة"
//   en: Continuous Control Monitoring (CCM)
//
// This file is the REST-backed engine surface. The older
// `lib/ccm.ts` is a Sprint 4 client-side mock surface that pre-dates
// the engine — both coexist while the older asset-register screen
// (which still consumes the mock) is migrated.

import { api } from './api';

export type CcmTestType =
  | 'LDAP_GROUP_MEMBERSHIP'
  | 'AD_PRIVILEGED_USERS'
  | 'MFA_ENROLLMENT_PERCENTAGE'
  | 'LOG_RETENTION_DAYS'
  | 'NETWORK_ACL_RULE'
  | 'SSL_CERT_EXPIRY'
  | 'PASSWORD_POLICY'
  | 'INACTIVE_ACCOUNTS'
  | 'SHARED_ACCOUNT_USAGE'
  | 'FILE_INTEGRITY'
  | 'BACKUP_RECENCY'
  | 'PATCH_LEVEL'
  | 'CUSTOM_SQL'
  | 'CUSTOM_HTTP';

export type CcmTargetSystem =
  | 'LDAP_AD'
  | 'AZURE_AD'
  | 'SPLUNK'
  | 'ELK'
  | 'CROWDSTRIKE'
  | 'TENABLE'
  | 'SERVICENOW'
  | 'INTERNAL_DB'
  | 'FILE_SYSTEM'
  | 'CUSTOM';

export type CcmSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type CcmRunStatus = 'PASS' | 'FAIL' | 'ERROR' | 'SKIPPED' | 'TIMEOUT';

export interface TestDefinitionResponse {
  id: number;
  externalId: string;
  testCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlId?: number;
  testType: CcmTestType;
  targetSystemType: CcmTargetSystem;
  targetConfigJson?: string;
  expectedConditionJson?: string;
  severity: CcmSeverity;
  frequencyCron: string;
  timeoutSeconds: number;
  isEnabled: boolean;
  lastRunAt?: string;
  lastRunStatus?: CcmRunStatus;
  consecutiveFailures: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface TestDefinitionRequest {
  testCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlId?: number;
  testType: CcmTestType;
  targetSystemType: CcmTargetSystem;
  targetConfigJson?: string;
  expectedConditionJson?: string;
  severity?: CcmSeverity;
  frequencyCron: string;
  timeoutSeconds?: number;
}

export interface TestRunResponse {
  id: number;
  externalId: string;
  testDefinitionId: number;
  testCode?: string;
  titleEn?: string;
  titleAr?: string;
  testType?: CcmTestType;
  targetSystemType?: CcmTargetSystem;
  severity?: CcmSeverity;
  runAt: string;
  durationMs?: number;
  status: CcmRunStatus;
  actualValueJson?: string;
  failureReason?: string;
  evidenceHash?: string;
  capaTaskId?: number;
}

export interface FailingTestSummary {
  externalId: string;
  testCode: string;
  titleEn: string;
  titleAr?: string;
  severity: CcmSeverity;
  consecutiveFailures: number;
  lastRunAt?: string;
  lastRunStatus?: CcmRunStatus;
  controlId?: number;
}

export interface DashboardSnapshot {
  totalTests: number;
  enabledTests: number;
  passingLast24h: number;
  failingLast24h: number;
  errorLast24h: number;
  timeoutLast24h: number;
  passRateLast24h: number;
  recentRuns: TestRunResponse[];
  failingTests: FailingTestSummary[];
}

// ─── REST client ─────────────────────────────────────────────────────

export const ccmApi = {
  listTests: () => api.get<TestDefinitionResponse[]>('/api/v1/ccm/tests'),
  getTest:   (externalId: string) => api.get<TestDefinitionResponse>(`/api/v1/ccm/tests/${externalId}`),
  createTest: (body: TestDefinitionRequest) =>
    api.post<TestDefinitionResponse>('/api/v1/ccm/tests', body),
  updateTest: (externalId: string, body: TestDefinitionRequest) =>
    api.put<TestDefinitionResponse>(`/api/v1/ccm/tests/${externalId}`, body),
  enableTest:  (externalId: string) =>
    api.post<TestDefinitionResponse>(`/api/v1/ccm/tests/${externalId}/enable`),
  disableTest: (externalId: string) =>
    api.post<TestDefinitionResponse>(`/api/v1/ccm/tests/${externalId}/disable`),
  runNow:      (externalId: string) =>
    api.post<TestRunResponse>(`/api/v1/ccm/tests/${externalId}/run-now`),
  getRuns:     (externalId: string, limit = 50) =>
    api.get<TestRunResponse[]>(`/api/v1/ccm/tests/${externalId}/runs?limit=${limit}`),
  recentRuns:  (limit = 50) =>
    api.get<TestRunResponse[]>(`/api/v1/ccm/runs/recent?limit=${limit}`),
  dashboard:   () => api.get<DashboardSnapshot>('/api/v1/ccm/dashboard'),
};

// ─── Display labels (Saudi-native — NOT literal translations) ───────

export const CCM_STATUS_LABEL: Record<CcmRunStatus, { en: string; ar: string }> = {
  PASS:    { en: 'Pass',    ar: 'ناجح' },
  FAIL:    { en: 'Fail',    ar: 'فاشل' },
  ERROR:   { en: 'Error',   ar: 'خطأ' },
  TIMEOUT: { en: 'Timeout', ar: 'متجاوز للمهلة' },
  SKIPPED: { en: 'Skipped', ar: 'مُتخطّى' },
};

export const CCM_STATUS_TONE: Record<CcmRunStatus, 'success' | 'danger' | 'warning' | 'neutral' | 'info'> = {
  PASS:    'success',
  FAIL:    'danger',
  ERROR:   'warning',
  TIMEOUT: 'warning',
  SKIPPED: 'neutral',
};

export const CCM_SEVERITY_LABEL: Record<CcmSeverity, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const CCM_SEVERITY_TONE: Record<CcmSeverity, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low'> = {
  CRITICAL: 'risk-crit',
  HIGH:     'risk-high',
  MEDIUM:   'risk-med',
  LOW:      'risk-low',
};

export const CCM_TEST_TYPE_LABEL: Record<CcmTestType, { en: string; ar: string }> = {
  LDAP_GROUP_MEMBERSHIP:     { en: 'LDAP group membership',    ar: 'عضوية مجموعة LDAP' },
  AD_PRIVILEGED_USERS:       { en: 'AD privileged users',      ar: 'مستخدمو AD المتميّزون' },
  MFA_ENROLLMENT_PERCENTAGE: { en: 'MFA enrolment %',          ar: 'نسبة تفعيل MFA' },
  LOG_RETENTION_DAYS:        { en: 'Log retention (days)',     ar: 'احتفاظ السجلّات (أيام)' },
  NETWORK_ACL_RULE:          { en: 'Network ACL rule',         ar: 'قاعدة ACL للشبكة' },
  SSL_CERT_EXPIRY:           { en: 'SSL/TLS cert expiry',      ar: 'انتهاء شهادة TLS' },
  PASSWORD_POLICY:           { en: 'Password policy',          ar: 'سياسة كلمات المرور' },
  INACTIVE_ACCOUNTS:         { en: 'Inactive accounts',        ar: 'الحسابات الراكدة' },
  SHARED_ACCOUNT_USAGE:      { en: 'Shared account usage',     ar: 'استخدام الحسابات المشتركة' },
  FILE_INTEGRITY:            { en: 'File integrity',           ar: 'سلامة الملفات' },
  BACKUP_RECENCY:            { en: 'Backup recency',           ar: 'حداثة النسخ الاحتياطي' },
  PATCH_LEVEL:               { en: 'Patch level',              ar: 'مستوى التصحيحات' },
  CUSTOM_SQL:                { en: 'Custom SQL',               ar: 'SQL مخصّص' },
  CUSTOM_HTTP:               { en: 'Custom HTTP',              ar: 'HTTP مخصّص' },
};

export const CCM_TARGET_SYSTEM_LABEL: Record<CcmTargetSystem, { en: string; ar: string }> = {
  LDAP_AD:     { en: 'LDAP / AD',     ar: 'LDAP / AD' },
  AZURE_AD:    { en: 'Azure AD',      ar: 'Azure AD' },
  SPLUNK:      { en: 'Splunk',        ar: 'سبلانك' },
  ELK:         { en: 'Elastic / ELK', ar: 'إلاستيك' },
  CROWDSTRIKE: { en: 'CrowdStrike',   ar: 'كراودسترايك' },
  TENABLE:     { en: 'Tenable',       ar: 'تينابل' },
  SERVICENOW:  { en: 'ServiceNow',    ar: 'سيرفس ناو' },
  INTERNAL_DB: { en: 'Internal DB',   ar: 'قاعدة بيانات داخلية' },
  FILE_SYSTEM: { en: 'File system',   ar: 'نظام الملفات' },
  CUSTOM:      { en: 'Custom',        ar: 'مخصّص' },
};

/** Pretty-printed JSON or fallback when the input isn't parseable. */
export function prettyJson(json: string | undefined | null): string {
  if (!json) return '';
  try {
    return JSON.stringify(JSON.parse(json), null, 2);
  } catch {
    return json;
  }
}

/** Truncate the evidence hash for compact display. */
export function shortHash(hash: string | undefined | null): string {
  if (!hash) return '—';
  if (hash.length <= 12) return hash;
  return hash.slice(0, 8) + '…' + hash.slice(-4);
}
