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
