// PDPL extensions API client (Saudi GRC prompt §3.5).
//
// Three sibling capabilities under one lib:
//   - cross-border transfer (PDPL Art. 29)
//   - disposal certificate (PDPL Art. 18 retention)
//   - privacy notice templating (PDPL Art. 12)
//
// All are per-tenant, status-driven, and share the page surface at
// /pdpl-extensions with one tab per capability.

function authHeaders(extra?: Record<string, string>): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return {
    ...(extra ?? {}),
    ...(tok ? { Authorization: `Bearer ${tok}` } : {}),
  };
}

// ─── Cross-border transfer ───────────────────────────────────────────

export type CbtStatus = 'REQUESTED' | 'ASSESSED' | 'APPROVED' | 'REJECTED' | 'EXPIRED';
export type TransferMechanism = 'ADEQUACY' | 'SCC' | 'BCR' | 'EXPLICIT_CONSENT'
  | 'CONTRACT_NECESSITY' | 'VITAL_INTEREST' | 'PUBLIC_INTEREST';

export interface CrossBorderTransfer {
  id: number;
  ropaCode: string;
  ropaTitleEn?: string;
  sourceCountry: string;
  destinationCountry: string;
  destinationOrgName: string;
  destinationOrgRole?: string;
  dataCategories: string;
  dataSubjectsCountEst?: number;
  transferMechanism: TransferMechanism;
  safeguardsEn?: string;
  safeguardsAr?: string;
  justificationEn: string;
  justificationAr?: string;
  residencyCheckOutcome?: 'PASS' | 'PASS_WITH_CONDITIONS' | 'FAIL';
  status: CbtStatus;
  rejectionReason?: string;
  approverName?: string;
  effectiveFrom?: string;
  expiryDate?: string;
  createdAt: string;
  updatedAt: string;
}

export async function listCrossBorderTransfers(page = 0, size = 50) {
  const r = await fetch(`/api/privacy/cross-border-transfers?page=${page}&size=${size}`, { headers: authHeaders() });
  if (!r.ok) throw new Error(`listCbt HTTP ${r.status}`);
  return r.json() as Promise<{ content: CrossBorderTransfer[]; totalElements: number }>;
}
export async function createCrossBorderTransfer(body: Partial<CrossBorderTransfer>) {
  const r = await fetch('/api/privacy/cross-border-transfers', {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`createCbt HTTP ${r.status}`);
  return r.json() as Promise<CrossBorderTransfer>;
}
export async function cbtAssess(id: number, residencyCheckBy: string, outcome: 'PASS' | 'PASS_WITH_CONDITIONS' | 'FAIL') {
  const r = await fetch(`/api/privacy/cross-border-transfers/${id}/assess`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ residencyCheckBy, residencyCheckOutcome: outcome }),
  });
  if (!r.ok) throw new Error(`cbtAssess HTTP ${r.status}`);
  return r.json() as Promise<CrossBorderTransfer>;
}
export async function cbtApprove(id: number) {
  const r = await fetch(`/api/privacy/cross-border-transfers/${id}/approve`, { method: 'POST', headers: authHeaders() });
  if (!r.ok) throw new Error(`cbtApprove HTTP ${r.status}`);
  return r.json() as Promise<CrossBorderTransfer>;
}
export async function cbtReject(id: number, rejectionReason: string) {
  const r = await fetch(`/api/privacy/cross-border-transfers/${id}/reject`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ rejectionReason }),
  });
  if (!r.ok) throw new Error(`cbtReject HTTP ${r.status}`);
  return r.json() as Promise<CrossBorderTransfer>;
}

export const CBT_STATUS_TONE: Record<CbtStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  REQUESTED: 'warning', ASSESSED: 'info', APPROVED: 'success', REJECTED: 'danger', EXPIRED: 'primary',
};

// ─── Disposal certificate ────────────────────────────────────────────

export type DisposalStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'VERIFIED' | 'CANCELLED';
export type DisposalMethod = 'DELETE' | 'ANONYMIZE' | 'ARCHIVE' | 'DESTROY';

export interface DisposalCertificate {
  id: number;
  relatedEntityType: string;
  relatedEntityId: string;
  relatedEntityLabel?: string;
  retentionRuleRef?: string;
  scopeDescriptionEn: string;
  recordsCountEst?: number;
  disposalMethod: DisposalMethod;
  status: DisposalStatus;
  scheduledDate?: string;
  executedAt?: string;
  executorName?: string;
  verifiedAt?: string;
  verifierName?: string;
  evidenceRef?: string;
  signatureTxId?: number;
  ownerRole?: string;
  ownerName?: string;
  createdAt: string;
  updatedAt: string;
}

export async function listDisposals(page = 0, size = 50) {
  const r = await fetch(`/api/privacy/disposal-certificates?page=${page}&size=${size}`, { headers: authHeaders() });
  if (!r.ok) throw new Error(`listDisposals HTTP ${r.status}`);
  return r.json() as Promise<{ content: DisposalCertificate[]; totalElements: number }>;
}
export async function createDisposal(body: Partial<DisposalCertificate>) {
  const r = await fetch('/api/privacy/disposal-certificates', {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`createDisposal HTTP ${r.status}`);
  return r.json() as Promise<DisposalCertificate>;
}
export async function disposalStart(id: number) {
  const r = await fetch(`/api/privacy/disposal-certificates/${id}/start`, { method: 'POST', headers: authHeaders() });
  if (!r.ok) throw new Error(`disposalStart HTTP ${r.status}`);
  return r.json() as Promise<DisposalCertificate>;
}
export async function disposalComplete(id: number, executorName?: string, evidenceRef?: string) {
  const r = await fetch(`/api/privacy/disposal-certificates/${id}/complete`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ executorName, evidenceRef }),
  });
  if (!r.ok) throw new Error(`disposalComplete HTTP ${r.status}`);
  return r.json() as Promise<DisposalCertificate>;
}
export async function disposalVerify(id: number, verifierName?: string, signatureTxId?: number) {
  const r = await fetch(`/api/privacy/disposal-certificates/${id}/verify`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ verifierName, signatureTxId }),
  });
  if (!r.ok) throw new Error(`disposalVerify HTTP ${r.status}`);
  return r.json() as Promise<DisposalCertificate>;
}

export const DISPOSAL_STATUS_TONE: Record<DisposalStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  SCHEDULED: 'warning', IN_PROGRESS: 'info', COMPLETED: 'primary', VERIFIED: 'success', CANCELLED: 'danger',
};

// ─── Privacy notice ──────────────────────────────────────────────────

export type NoticeStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface PrivacyNotice {
  id: number;
  noticeCode: string;
  version: number;
  titleEn: string;
  titleAr: string;
  purposeEn: string;
  purposeAr?: string;
  dataCategories?: string;
  recipients?: string;
  retentionPeriodEn?: string;
  retentionPeriodAr?: string;
  dpoContact?: string;
  lawfulBasis?: string;
  bodyEn: string;
  bodyAr?: string;
  linkedRopaCodes?: string;
  status: NoticeStatus;
  effectiveFrom?: string;
  effectiveTo?: string;
  publishedAt?: string;
  supersedesId?: number;
  createdAt: string;
  updatedAt: string;
}

export async function listNotices(page = 0, size = 50) {
  const r = await fetch(`/api/privacy/notices?page=${page}&size=${size}`, { headers: authHeaders() });
  if (!r.ok) throw new Error(`listNotices HTTP ${r.status}`);
  return r.json() as Promise<{ content: PrivacyNotice[]; totalElements: number }>;
}
export async function createNotice(body: Partial<PrivacyNotice>) {
  const r = await fetch('/api/privacy/notices', {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify(body),
  });
  if (!r.ok) throw new Error(`createNotice HTTP ${r.status}`);
  return r.json() as Promise<PrivacyNotice>;
}
export async function publishNotice(id: number) {
  const r = await fetch(`/api/privacy/notices/${id}/publish`, { method: 'POST', headers: authHeaders() });
  if (!r.ok) throw new Error(`publishNotice HTTP ${r.status}`);
  return r.json() as Promise<PrivacyNotice>;
}
export async function archiveNotice(id: number, archiveReasonEn?: string) {
  const r = await fetch(`/api/privacy/notices/${id}/archive`, {
    method: 'POST', headers: authHeaders({ 'Content-Type': 'application/json' }),
    body: JSON.stringify({ archiveReasonEn }),
  });
  if (!r.ok) throw new Error(`archiveNotice HTTP ${r.status}`);
  return r.json() as Promise<PrivacyNotice>;
}
export async function supersedeNotice(id: number) {
  const r = await fetch(`/api/privacy/notices/${id}/supersede`, { method: 'POST', headers: authHeaders() });
  if (!r.ok) throw new Error(`supersedeNotice HTTP ${r.status}`);
  return r.json() as Promise<PrivacyNotice>;
}

export const NOTICE_STATUS_TONE: Record<NoticeStatus, 'primary' | 'success' | 'danger' | 'warning' | 'info'> = {
  DRAFT: 'warning', PUBLISHED: 'success', ARCHIVED: 'primary',
};
