// NDI Audit Trail API client (slice 14 · V78).

import { api } from './api';

export type AuditSeverity = 'INFO' | 'WARN' | 'ERROR';

export type AuditAction =
  | 'CREATE' | 'UPDATE' | 'DELETE' | 'TRANSITION' | 'APPROVE'
  | 'SUBMIT' | 'ACKNOWLEDGE' | 'REJECT' | 'EXPORT' | 'IMPORT'
  | 'REGENERATE' | 'REOPEN' | 'CHECK_EXPIRY' | 'MARK_OFFICIAL'
  | 'MARK_PLACEHOLDER';

export interface NdiAuditEvent {
  id?: number;
  organisationId?: number;
  cycleId?: number;
  entityType: string;
  entityId?: number;
  entityCode?: string;
  action: AuditAction;
  fromState?: string;
  toState?: string;
  severity: AuditSeverity;
  actorUsername?: string;
  actorRole?: string;
  actorIp?: string;
  summary?: string;
  diffJson?: string;
  correlationId?: string;
  occurredAt: string;
}

export interface AuditAggregate {
  total: number;
  byEntityType: Record<string, number>;
  byAction: Record<string, number>;
  bySeverity: Record<string, number>;
}

export interface Page<T> {
  content: T[]; totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/ndi/audit';

export function searchAudit(opts: {
  cycleId?: number; entityType?: string; action?: AuditAction;
  severity?: AuditSeverity; page?: number; size?: number;
} = {}) {
  const p = new URLSearchParams();
  if (opts.cycleId != null) p.set('cycleId', String(opts.cycleId));
  if (opts.entityType)      p.set('entityType', opts.entityType);
  if (opts.action)          p.set('action', opts.action);
  if (opts.severity)        p.set('severity', opts.severity);
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 200));
  return api.get<Page<NdiAuditEvent>>(`${BASE}?${p.toString()}`);
}

export function auditAggregate() { return api.get<AuditAggregate>(`${BASE}/aggregate`); }
export function recordAudit(body: Partial<NdiAuditEvent>) { return api.post<NdiAuditEvent>(BASE, body); }

export function severityTone(s: AuditSeverity): 'info' | 'warning' | 'danger' {
  switch (s) {
    case 'INFO':  return 'info';
    case 'WARN':  return 'warning';
    case 'ERROR': return 'danger';
  }
}
