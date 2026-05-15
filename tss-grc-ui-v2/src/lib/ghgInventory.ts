// GHG Emission Inventory API client (Sustainability module · V56).
//
// Wraps /api/sustainability/ghg/*. Used by the new "GHG inventory" tab on
// /esg. Spec workflow rules enforced:
//   • Rule 1 — aggregate() returns scope3PartialFlag when Scope-3
//     weighted coverage is below 70% (drives a dashboard warning).
//   • Rule 2 — POST /:id/verify rejects without evidence_ref. The UI
//     refuses to call /verify when the row has no evidence (mirror).

import { api } from './api';

export type GhgStatus = 'DRAFT' | 'SUBMITTED' | 'VERIFIED' | 'APPROVED' | 'ARCHIVED';
export type GhgScope = 1 | 2 | 3;

export interface GhgEmissionRecord {
  id?: number;
  organisationId?: number;
  recordCode?: string;

  reportingYear: number;
  scope: GhgScope;
  category: string;

  sourceNameEn: string;
  sourceNameAr?: string;

  businessUnit?: string;
  facility?: string;

  activityData?: number;
  activityUnit?: string;
  emissionFactor?: number;
  emissionFactorUnit?: string;
  emissionValueTco2e: number;
  coveragePercent: number;       // 0..100

  verified: boolean;
  verificationProvider?: string;
  verificationDate?: string;

  methodology?: string;
  notes?: string;

  evidenceRef?: string;
  ownerName?: string;
  ownerEmail?: string;

  status?: GhgStatus;
  submittedAt?: string;
  approvedAt?: string;
}

export interface GhgAggregate {
  year: number;
  scope1Total: number;
  scope2Total: number;
  scope3Total: number;
  grandTotal: number;
  scope3Coverage: number;
  scope3PartialFlag: boolean;     // Rule 1 trigger
  recordCount: number;
}

export interface GhgYoY {
  year: number;
  scope1YoY: number;
  scope2YoY: number;
  scope3YoY: number;
  totalYoY: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  number: number; size: number; totalPages: number;
}

const BASE = '/api/sustainability/ghg';

export function listGhgRecords(opts: { year?: number; scope?: GhgScope; page?: number; size?: number } = {}) {
  const p = new URLSearchParams();
  if (opts.year)  p.set('year', String(opts.year));
  if (opts.scope) p.set('scope', String(opts.scope));
  p.set('page', String(opts.page ?? 0));
  p.set('size', String(opts.size ?? 100));
  return api.get<Page<GhgEmissionRecord>>(`${BASE}?${p.toString()}`);
}

export function ghgAggregate(year: number) {
  return api.get<GhgAggregate>(`${BASE}/aggregate?year=${year}`);
}
export function ghgYoy(year: number) {
  return api.get<GhgYoY>(`${BASE}/yoy?year=${year}`);
}
export function createGhgRecord(body: Partial<GhgEmissionRecord>) {
  return api.post<GhgEmissionRecord>(BASE, body);
}
export function updateGhgRecord(id: number, body: Partial<GhgEmissionRecord>) {
  return api.put<GhgEmissionRecord>(`${BASE}/${id}`, body);
}
export function submitGhgRecord(id: number)  { return api.post<GhgEmissionRecord>(`${BASE}/${id}/submit`); }
export function verifyGhgRecord(id: number)  { return api.post<GhgEmissionRecord>(`${BASE}/${id}/verify`); }
export function approveGhgRecord(id: number) { return api.post<GhgEmissionRecord>(`${BASE}/${id}/approve`); }
export function archiveGhgRecord(id: number) { return api.post<GhgEmissionRecord>(`${BASE}/${id}/archive`); }
export function deleteGhgRecord(id: number)  { return api.delete<void>(`${BASE}/${id}`); }

/** Tone for a status badge. Mirrors the convention used in other modules. */
export function ghgStatusTone(s: GhgStatus | undefined):
    'neutral' | 'info' | 'warning' | 'success' | 'primary' {
  switch (s) {
    case 'APPROVED':  return 'success';
    case 'VERIFIED':  return 'primary';
    case 'SUBMITTED': return 'info';
    case 'ARCHIVED':  return 'neutral';
    default:          return 'warning';   // DRAFT
  }
}

/** Rule-2 client-side mirror — disables the Verify button until evidence is set. */
export function canVerify(r: GhgEmissionRecord): boolean {
  return r.status === 'SUBMITTED' && !!r.evidenceRef && r.evidenceRef.trim().length > 0;
}
