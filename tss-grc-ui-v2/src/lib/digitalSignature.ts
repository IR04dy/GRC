// Digital signature API client — wraps /api/signature/transactions.
//
// Used by:
//   - SignaturesInboxPage     ("My pending signatures")
//   - ProcessDetailModal      (open a sign request for a workflow approval)
//   - BoardPack / RiskAcceptance / CAPA closure widgets
//
// State machine on the server (DigitalSignatureService.java):
//   REQUESTED → SIGNED | REJECTED | EXPIRED

export type SignatureStatus = 'REQUESTED' | 'SIGNED' | 'REJECTED' | 'EXPIRED';

export interface DigitalSignatureTransaction {
  id: number;
  relatedEntityType: string;
  relatedEntityId: number;
  relatedEntityLabel?: string;
  signerUserId?: number;
  signerName: string;
  signerEmail?: string;
  signerRole?: string;
  initiatorUserId?: number;
  initiatorName?: string;
  signatureProvider: string;
  purpose: string;
  purposeAr?: string;
  signedDocumentHash?: string;
  certificateReference?: string;
  status: SignatureStatus;
  rejectionReason?: string;
  signatureTimestamp?: string;
  expiryDate?: string;
  createdAt: string;
  updatedAt: string;
}

export interface DigitalSignatureCreateRequest {
  relatedEntityType: string;
  relatedEntityId: number;
  relatedEntityLabel?: string;
  signerUserId?: number;
  signerName: string;
  signerEmail?: string;
  signerRole?: string;
  signatureProvider?: string;
  purpose: string;
  purposeAr?: string;
  expiryDate?: string;
}

const BASE = '/api/signature/transactions';

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return {
    ...(extra ?? {}),
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
  };
}

export async function listSignatures(page = 0, size = 50): Promise<{ content: DigitalSignatureTransaction[]; totalElements: number }> {
  const res = await fetch(`${BASE}?page=${page}&size=${size}`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listSignatures HTTP ${res.status}`);
  return res.json();
}

export async function listMyInbox(): Promise<DigitalSignatureTransaction[]> {
  const res = await fetch(`${BASE}/inbox`, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listMyInbox HTTP ${res.status}`);
  return res.json();
}

export async function listForEntity(entityType: string, entityId: number): Promise<DigitalSignatureTransaction[]> {
  const url = `${BASE}/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`;
  const res = await fetch(url, { headers: authHeaders() });
  if (!res.ok) throw new Error(`listForEntity HTTP ${res.status}`);
  return res.json();
}

export async function createSignature(body: DigitalSignatureCreateRequest): Promise<DigitalSignatureTransaction> {
  const res = await fetch(BASE, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!res.ok) throw new Error(`createSignature HTTP ${res.status}`);
  return res.json();
}

export async function signTransaction(id: number, signedDocumentHash: string, certificateReference?: string): Promise<DigitalSignatureTransaction> {
  const res = await fetch(`${BASE}/${id}/sign`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ signedDocumentHash, certificateReference }),
  });
  if (!res.ok) throw new Error(`signTransaction HTTP ${res.status}`);
  return res.json();
}

export async function rejectTransaction(id: number, rejectionReason: string): Promise<DigitalSignatureTransaction> {
  const res = await fetch(`${BASE}/${id}/reject`, {
    method: 'POST',
    headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ rejectionReason }),
  });
  if (!res.ok) throw new Error(`rejectTransaction HTTP ${res.status}`);
  return res.json();
}

/**
 * Compute a sha256 hex digest in the browser. Used to bind a signer to a
 * specific payload at sign time (e.g. board pack JSON, risk acceptance
 * document) so the server never has to trust a client-supplied hash for
 * unrelated content.
 */
export async function sha256Hex(text: string): Promise<string> {
  const buf = new TextEncoder().encode(text);
  const hash = await crypto.subtle.digest('SHA-256', buf);
  return Array.from(new Uint8Array(hash))
    .map(b => b.toString(16).padStart(2, '0'))
    .join('');
}

export const STATUS_TONE: Record<SignatureStatus, 'primary' | 'success' | 'danger' | 'warning'> = {
  REQUESTED: 'warning',
  SIGNED:    'success',
  REJECTED:  'danger',
  EXPIRED:   'primary',
};
