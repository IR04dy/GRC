// Contract clause library API client — uses shared api.ts.

import { api } from './api';

export type ClauseStatus       = 'DRAFT' | 'APPROVED' | 'DEPRECATED';
export type ClauseRisk         = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type ClauseNegotiability = 'FIXED' | 'NEGOTIABLE' | 'DEFAULT';
export type ClauseCategory     =
  | 'DATA_PROTECTION' | 'SLA' | 'AUDIT_RIGHTS' | 'EXIT_RIGHTS'
  | 'SECURITY' | 'COMPLIANCE' | 'LIABILITY' | 'SUBPROCESSORS'
  | 'FORCE_MAJEURE' | 'OTHER';

export interface ContractClause {
  id: number; clauseCode: string; externalId?: string;
  titleEn: string; titleAr?: string;
  category: ClauseCategory; applicableTo?: string;
  bodyEn: string; bodyAr?: string;
  guidanceEn?: string; guidanceAr?: string;
  regulatoryRefs?: string;
  riskLevel: ClauseRisk; negotiability: ClauseNegotiability; version: string;
  status: ClauseStatus;
  approvedAt?: string; approverName?: string;
  deprecatedAt?: string; deprecationReason?: string;
  owner?: string; tags?: string;
  createdAt: string; updatedAt: string;
}

export interface ClauseCreateRequest {
  clauseCode: string; titleEn: string; titleAr?: string;
  category: ClauseCategory; applicableTo?: string;
  bodyEn: string; bodyAr?: string;
  guidanceEn?: string; guidanceAr?: string;
  regulatoryRefs?: string;
  riskLevel?: ClauseRisk; negotiability?: ClauseNegotiability; version?: string;
  owner?: string; tags?: string;
}

const BASE = '/api/scm/contract-clauses';

export function listClauses(status?: ClauseStatus, category?: ClauseCategory, page = 0, size = 200):
    Promise<{ content: ContractClause[]; totalElements: number }> {
  const qs = [
    status   ? `status=${status}`     : null,
    category ? `category=${category}` : null,
  ].filter(Boolean).join('&');
  return api.get(`${BASE}?page=${page}&size=${size}${qs ? '&' + qs : ''}`);
}

export function createClause(body: ClauseCreateRequest): Promise<ContractClause> {
  return api.post(BASE, body);
}

export function approveClause(id: number): Promise<ContractClause> {
  return api.post(`${BASE}/${id}/approve`);
}

export function deprecateClause(id: number, reason: string): Promise<ContractClause> {
  return api.post(`${BASE}/${id}/deprecate`, { reason });
}

export const CLAUSE_STATUS_TONE: Record<ClauseStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  DRAFT: 'warning', APPROVED: 'success', DEPRECATED: 'info',
};
export const CLAUSE_RISK_TONE: Record<ClauseRisk, 'success'|'warning'|'danger'|'info'> = {
  LOW: 'info', MEDIUM: 'info', HIGH: 'warning', CRITICAL: 'danger',
};
export const CLAUSE_NEGOTIABILITY_TONE: Record<ClauseNegotiability, 'success'|'warning'|'danger'|'info'> = {
  FIXED: 'danger', NEGOTIABLE: 'info', DEFAULT: 'success',
};
export const CLAUSE_CATEGORY_LABEL: Record<ClauseCategory, string> = {
  DATA_PROTECTION: 'cclCat_DATA_PROTECTION',
  SLA: 'cclCat_SLA', AUDIT_RIGHTS: 'cclCat_AUDIT_RIGHTS',
  EXIT_RIGHTS: 'cclCat_EXIT_RIGHTS', SECURITY: 'cclCat_SECURITY',
  COMPLIANCE: 'cclCat_COMPLIANCE', LIABILITY: 'cclCat_LIABILITY',
  SUBPROCESSORS: 'cclCat_SUBPROCESSORS', FORCE_MAJEURE: 'cclCat_FORCE_MAJEURE',
  OTHER: 'cclCat_OTHER',
};
