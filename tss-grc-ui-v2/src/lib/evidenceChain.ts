// Evidence chain-of-custody API client. Backend: /api/evidence/chain

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

function authHeaders(): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return tok ? { Authorization: `Bearer ${tok}` } : {};
}

export async function listChainItems(status?: ChainStatus): Promise<ChainItemSummary[]> {
  const qs = status ? `?status=${status}` : '';
  const res = await fetch(`${BASE}${qs}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listChainItems HTTP ${res.status}`);
  return res.json();
}

export async function getChainReport(evidenceId: number): Promise<ChainReport> {
  const res = await fetch(`${BASE}/${evidenceId}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`getChainReport HTTP ${res.status}`);
  return res.json();
}

export async function verifyChain(evidenceId: number): Promise<ChainReport> {
  const res = await fetch(`${BASE}/${evidenceId}/verify`, { method: 'POST', headers: authHeaders() });
  if (!res.ok) throw new Error(`verifyChain HTTP ${res.status}`);
  return res.json();
}

export const CHAIN_STATUS_TONE: Record<ChainStatus, 'primary'|'success'|'danger'|'warning'|'info'> = {
  VALID:      'success',
  BROKEN:     'danger',
  UNVERIFIED: 'warning',
};

/** Format a 64-char SHA-256 hex string as "abcd1234…ef12" for tight display. */
export function shortHash(h?: string): string {
  if (!h) return '—';
  if (h.length < 12) return h;
  return h.substring(0, 8) + '…' + h.substring(h.length - 4);
}
