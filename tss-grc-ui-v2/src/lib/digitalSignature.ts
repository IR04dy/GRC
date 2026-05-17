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

import { api } from './api';

const BASE = '/api/signature/transactions';

export function listSignatures(page = 0, size = 50):
    Promise<{ content: DigitalSignatureTransaction[]; totalElements: number }> {
  return api.get(`${BASE}?page=${page}&size=${size}`);
}

export function listMyInbox(): Promise<DigitalSignatureTransaction[]> {
  return api.get(`${BASE}/inbox`);
}

export function listForEntity(entityType: string, entityId: number): Promise<DigitalSignatureTransaction[]> {
  return api.get(`${BASE}/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`);
}

export function createSignature(body: DigitalSignatureCreateRequest): Promise<DigitalSignatureTransaction> {
  return api.post(BASE, body);
}

export function signTransaction(id: number, signedDocumentHash: string,
                                certificateReference?: string): Promise<DigitalSignatureTransaction> {
  return api.post(`${BASE}/${id}/sign`, { signedDocumentHash, certificateReference });
}

export function rejectTransaction(id: number, rejectionReason: string): Promise<DigitalSignatureTransaction> {
  return api.post(`${BASE}/${id}/reject`, { rejectionReason });
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
