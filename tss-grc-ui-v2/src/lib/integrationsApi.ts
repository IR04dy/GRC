// External integration adapters — live backend client.
//
// Surfaces the real /api/v1/integrations/* adapters (Tenable, CrowdStrike,
// Splunk, ServiceNow) that ship in com.tss.grc.module.integrations but were
// never wired to any UI. Each adapter exposes a status/health read and (for
// the inbound pollers) a manual poll that ingests findings/detections. In dev
// the adapters run in STUB mode, so status + poll are fully exercisable.

import { api } from './api';

export type IntegrationKey = 'tenable' | 'crowdstrike' | 'splunk' | 'servicenow';

/** Union of the fields the four adapter status/health endpoints can return. */
export interface IntegrationStatus {
  enabled?: boolean;
  healthy?: boolean;
  /** STUB or LIVE. */
  mode?: string;
  platform?: string;
  baseUrl?: string;
  pollingEnabled?: boolean;
  lastPollAt?: string;
  lastActionAt?: string;
  lastSuccessfulPushAt?: string;
}

export interface IntegrationPollResult {
  ingested: number;
  at: string;
}

const BASE = '/api/v1/integrations';

/** ServiceNow exposes health at /health; the others at /status. */
export const INTEGRATION_STATUS_PATH: Record<IntegrationKey, string> = {
  tenable: 'status', crowdstrike: 'status', splunk: 'status', servicenow: 'health',
};

export function getIntegrationStatus(key: IntegrationKey) {
  return api.get<IntegrationStatus>(`${BASE}/${key}/${INTEGRATION_STATUS_PATH[key]}`);
}

/** Manual inbound poll — only Tenable + CrowdStrike expose a no-body poll. */
export function pollIntegration(key: 'tenable' | 'crowdstrike') {
  return api.post<IntegrationPollResult>(`${BASE}/${key}/poll`);
}

// ─── Outbound smoke-test pushes (Splunk / ServiceNow) ────────────────

export interface SplunkTestPushRequest {
  sourceModule: string;     // SECURITY_EVENT / FINDING / RISK / CAPA_TASK / COMPLIANCE_GAP
  sourceId: string;
  host?: string;
  event?: Record<string, unknown>;
}
export interface ServiceNowTestPushRequest {
  grcSourceModule: string;  // CYBER_INCIDENT / AUDIT_FINDING / CAPA_TASK / COMPLIANCE_GAP
  grcSourceId: string;
  shortDescription: string;
  description?: string;
  severity: string;         // CRITICAL / HIGH / MEDIUM / LOW
  category?: string;
}

export function splunkTestPush(body: SplunkTestPushRequest) {
  return api.post<Record<string, unknown>>(`${BASE}/splunk/test-push`, body);
}
export function serviceNowTestPush(body: ServiceNowTestPushRequest) {
  return api.post<Record<string, unknown>>(`${BASE}/servicenow/test-push`, body);
}

// ─── Config + audit (recorded-calls) — all four adapters ─────────────
//
// Each adapter exposes GET /config (masked secrets) and a STUB-only
// GET /stub/recorded-calls. The recorded-calls feed is the integration
// audit trail — every poll / health-check / outbound action the stub
// client saw, frozen at call time.

/** Masked configuration as a (possibly nested) key/value map. */
export type IntegrationConfig = Record<string, unknown>;

export function getIntegrationConfig(key: IntegrationKey) {
  return api.get<IntegrationConfig>(`${BASE}/${key}/config`);
}

/** A single call the stub client recorded. Mirrors the backend StubInvocation. */
export interface StubInvocation {
  at: string;
  /** POLL / RECAST / ACCEPT_RISK / REMOVE_RECAST / CONTAIN / LIFT / PUSH / HEALTH … */
  method: string;
  targetId?: string | null;
  payload?: Record<string, unknown> | null;
}

export function getRecordedCalls(key: IntegrationKey) {
  return api.get<StubInvocation[]>(`${BASE}/${key}/stub/recorded-calls`);
}

// ─── Tenable VM disposition actions (outbound) ───────────────────────
//
// recast / accept-risk / remove-recast against a Tenable plugin id. The
// justification is the load-bearing audit field (NCA ECC-2 / SAMA CSF) —
// the backend rejects anything under 30 characters.

export interface TenableActionResponse {
  actionType: string;            // RECAST_SEVERITY / ACCEPT_RISK / REMOVE_RECAST
  targetPluginId: string;
  targetAssetUuid?: string | null;
  accepted: boolean;
  httpStatus: number;
  attemptCount: number;
  tenableRuleId?: string | null; // "DISABLED" when the adapter is off
}

export interface TenableRecastRequest {
  newSeverity: 'INFO' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  justification: string;         // min 30 chars (server-enforced)
  assetUuid?: string;            // null/absent = global recast
}
export interface TenableAcceptRiskRequest {
  justification: string;         // min 30 chars (server-enforced)
  expiresAt?: string;            // ISO LocalDateTime; absent = open-ended
  assetUuid?: string;
}

export function tenableRecast(pluginId: string, body: TenableRecastRequest) {
  return api.post<TenableActionResponse>(`${BASE}/tenable/recast/${encodeURIComponent(pluginId)}`, body);
}
export function tenableAcceptRisk(pluginId: string, body: TenableAcceptRiskRequest) {
  return api.post<TenableActionResponse>(`${BASE}/tenable/accept-risk/${encodeURIComponent(pluginId)}`, body);
}
export function tenableRemoveRecast(pluginId: string, assetUuid?: string) {
  return api.post<TenableActionResponse>(
    `${BASE}/tenable/remove-recast/${encodeURIComponent(pluginId)}`,
    assetUuid ? { assetUuid } : {});
}

// ─── CrowdStrike Falcon containment (outbound) ───────────────────────
//
// contain / lift a Falcon host. Containment isolates the host from the
// network; lifting restores it. reason is the audit field.

export interface ContainmentResponse {
  actionType: string;            // CONTAIN_HOST / LIFT_CONTAINMENT
  targetHostId: string;
  accepted: boolean;
  httpStatus: number;
  attemptCount: number;
  falconResourceId?: string | null; // "DISABLED" when the adapter is off
}

export function crowdstrikeContain(hostId: string, reason: string) {
  return api.post<ContainmentResponse>(`${BASE}/crowdstrike/contain/${encodeURIComponent(hostId)}`, { reason });
}
export function crowdstrikeLift(hostId: string) {
  return api.post<ContainmentResponse>(`${BASE}/crowdstrike/lift/${encodeURIComponent(hostId)}`);
}

// ─── Capability catalog — which adapters expose which surfaces ───────
//
// All four expose /config + /stub/recorded-calls. Outbound actions differ:
// Tenable = VM disposition, CrowdStrike = host containment, Splunk/
// ServiceNow = outbound test-push (lives on the Connectors page).

export type AdapterAction = 'tenable-disposition' | 'crowdstrike-containment' | 'outbound-push';

export const INTEGRATION_ACTION: Record<IntegrationKey, AdapterAction> = {
  tenable: 'tenable-disposition',
  crowdstrike: 'crowdstrike-containment',
  splunk: 'outbound-push',
  servicenow: 'outbound-push',
};

export function getAdapterMeta(key: string): IntegrationAdapterMeta | undefined {
  return INTEGRATION_ADAPTERS.find(a => a.key === key);
}

/** Pretty tone for a recorded-call method badge. */
export function stubMethodTone(method: string): IntegrationTone {
  switch (method.toUpperCase()) {
    case 'RECAST': case 'ACCEPT_RISK': case 'CONTAIN':
      return 'warning';
    case 'REMOVE_RECAST': case 'LIFT':
      return 'info';
    case 'POLL': case 'PUSH': case 'CREATE_INCIDENT':
      return 'success';
    case 'HEALTH':
      return 'neutral';
    default:
      return 'neutral';
  }
}

// ─── Display catalog ─────────────────────────────────────────────────

export interface IntegrationAdapterMeta {
  key: IntegrationKey;
  nameEn: string;
  nameAr: string;
  kindEn: string;
  kindAr: string;
  /** Direction of data flow: inbound pollers can be polled on demand. */
  canPoll: boolean;
}

export const INTEGRATION_ADAPTERS: IntegrationAdapterMeta[] = [
  { key: 'tenable',     nameEn: 'Tenable VM',         nameAr: 'Tenable لإدارة الثغرات', kindEn: 'Vulnerability scanner', kindAr: 'فاحص ثغرات', canPoll: true },
  { key: 'crowdstrike', nameEn: 'CrowdStrike Falcon', nameAr: 'CrowdStrike Falcon',     kindEn: 'EDR',                  kindAr: 'كشف واستجابة', canPoll: true },
  { key: 'splunk',      nameEn: 'Splunk SIEM',        nameAr: 'Splunk SIEM',            kindEn: 'SIEM',                 kindAr: 'SIEM', canPoll: false },
  { key: 'servicenow',  nameEn: 'ServiceNow ITSM',    nameAr: 'ServiceNow ITSM',        kindEn: 'ITSM',                 kindAr: 'إدارة الخدمات', canPoll: false },
];

export type IntegrationTone = 'success' | 'info' | 'warning' | 'danger' | 'neutral';

export function integrationModeTone(mode?: string): IntegrationTone {
  return (mode ?? '').toUpperCase() === 'LIVE' ? 'success' : 'info';
}
