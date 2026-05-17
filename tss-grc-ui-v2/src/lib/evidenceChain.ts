// Evidence chain-of-custody API client — uses shared api.ts.

import { api } from './api';

export type ChainStatus = 'VALID' | 'BROKEN' | 'UNVERIFIED';

export interface ChainItemSummary {
  evidenceId: number;
  externalId?: string;
  titleEn?: string;
  titleAr?: string;
  currentVersionNo?: number;
  chainVersionCount?: number;
  chainStatus?: ChainStatus;
  chainLastVerifiedAt?: string;
  chainFirstBreakVersion?: number;
}

export interface ChainVersionLink {
  versionId: number;
  versionNo: number;
  chainPosition: number;
  contentHashSha256?: string;
  chainHashSha256?: string;
  chainStatus?: ChainStatus;
  chainBreakReason?: string;
  snapshottedAt?: string;
  fileName?: string;
  fileSize?: number;
  changeSummary?: string;
}

export interface ChainReport {
  evidenceId: number;
  externalId?: string;
  titleEn?: string;
  chainStatus?: ChainStatus;
  versionCount: number;
  validCount: number;
  brokenCount: number;
  firstBreakVersion?: number;
  verifiedAt?: string;
  versions: ChainVersionLink[];
}

const BASE = '/api/evidence/chain';

export function listChainItems(status?: ChainStatus): Promise<ChainItemSummary[]> {
  const qs = status ? `?status=${status}` : '';
  return api.get(`${BASE}${qs}`);
}

export function getChainReport(evidenceId: number): Promise<ChainReport> {
  return api.get(`${BASE}/${evidenceId}`);
}

export function verifyChain(evidenceId: number): Promise<ChainReport> {
  return api.post(`${BASE}/${evidenceId}/verify`);
}

export const CHAIN_STATUS_TONE: Record<ChainStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  VALID: 'success', BROKEN: 'danger', UNVERIFIED: 'warning',
};

export function shortHash(h?: string): string {
  if (!h) return '—';
  if (h.length < 12) return h;
  return h.substring(0, 8) + '…' + h.substring(h.length - 4);
}
