// Continuous Control Monitoring (CCM) — live backend client (L3).
//
// Unlike `ccm.ts` (a static demo catalogue of connector→control mappings),
// this talks to the real CCM engine under /api/v1/ccm: persisted control-test
// definitions, real runner execution (run-now), and run history. It is the
// "first real connector integration" — the UI driving the live monitoring
// engine instead of a baked-in array.
//
// Backend: com.tss.grc.module.ccm (CcmController, CcmService, ControlTestRunner*).

import { api } from './api';

export interface CcmTestDefinition {
  id: number;
  externalId: string;
  testCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlId?: number;
  /** Runner discriminator, e.g. BACKUP_RECENCY / INACTIVE_ACCOUNTS / CUSTOM_SQL. */
  testType: string;
  /** Where the runner reaches, e.g. INTERNAL_DB / AWS / LDAP. */
  targetSystemType: string;
  targetConfigJson?: string;
  expectedConditionJson?: string;
  severity?: string;
  frequencyCron: string;
  timeoutSeconds?: number;
  isEnabled: boolean;
  lastRunAt?: string;
  lastRunStatus?: string;
  consecutiveFailures?: number;
  createdAt?: string;
  updatedAt?: string;
}

export interface CcmTestRun {
  id: number;
  externalId: string;
  testDefinitionId: number;
  testCode?: string;
  titleEn?: string;
  titleAr?: string;
  testType?: string;
  targetSystemType?: string;
  severity?: string;
  runAt: string;
  durationMs?: number;
  /** PASS / FAIL / ERROR / TIMEOUT / … */
  status: string;
  actualValueJson?: string;
  failureReason?: string;
  evidenceHash?: string;
  capaTaskId?: number;
}

export interface CcmFailingTest {
  externalId: string;
  testCode?: string;
  titleEn?: string;
  titleAr?: string;
  severity?: string;
  consecutiveFailures?: number;
  lastRunAt?: string;
  lastRunStatus?: string;
  controlId?: number;
}

export interface CcmDashboardSnapshot {
  totalTests: number;
  enabledTests: number;
  passingLast24h: number;
  failingLast24h: number;
  errorLast24h: number;
  timeoutLast24h: number;
  passRateLast24h: number;
  recentRuns: CcmTestRun[];
  failingTests: CcmFailingTest[];
}

const BASE = '/api/v1/ccm';

export function listCcmTests() { return api.get<CcmTestDefinition[]>(`${BASE}/tests`); }
export function getCcmDashboard() { return api.get<CcmDashboardSnapshot>(`${BASE}/dashboard`); }
export function enableCcmTest(externalId: string) { return api.post<CcmTestDefinition>(`${BASE}/tests/${externalId}/enable`); }
export function disableCcmTest(externalId: string) { return api.post<CcmTestDefinition>(`${BASE}/tests/${externalId}/disable`); }
export function runCcmTestNow(externalId: string) { return api.post<CcmTestRun>(`${BASE}/tests/${externalId}/run-now`); }
export function getCcmRecentRuns(limit = 50) { return api.get<CcmTestRun[]>(`${BASE}/runs/recent?limit=${limit}`); }
export function getCcmTestRuns(externalId: string, limit = 50) { return api.get<CcmTestRun[]>(`${BASE}/tests/${externalId}/runs?limit=${limit}`); }

// ─── UI helpers ──────────────────────────────────────────────────────

export type CcmTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral';

export function ccmStatusTone(s?: string): CcmTone {
  switch ((s ?? '').toUpperCase()) {
    case 'PASS':                 return 'success';
    case 'WARN': case 'WARNING':
    case 'TIMEOUT':              return 'warning';
    case 'FAIL': case 'ERROR':   return 'danger';
    default:                     return 'neutral';
  }
}

export function ccmSeverityTone(s?: string): CcmTone {
  switch ((s ?? '').toUpperCase()) {
    case 'CRITICAL': return 'danger';
    case 'HIGH':     return 'warning';
    case 'MEDIUM':   return 'info';
    case 'LOW':      return 'neutral';
    default:         return 'neutral';
  }
}

export function ccmStatusLabel(s: string | undefined, lang: 'en' | 'ar'): string {
  const k = (s ?? '').toUpperCase();
  const ar: Record<string, string> = {
    PASS: 'ناجح', FAIL: 'فاشل', ERROR: 'خطأ', TIMEOUT: 'انتهت المهلة',
    WARN: 'تحذير', WARNING: 'تحذير', RUNNING: 'قيد التشغيل', NO_DATA: 'لا بيانات',
  };
  if (lang === 'ar') return ar[k] ?? (s ?? '—');
  return s ?? '—';
}
