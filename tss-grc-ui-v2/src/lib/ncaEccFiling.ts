// NCA ECC annual filing client — typed wrappers around /api/nca-ecc/filings.
//
// Saudi NCA Essential Cybersecurity Controls (ECC-2:2024) require regulated
// entities to self-assess across five domains and file an annual return. Each
// filing is scored 1-5 per domain and runs a governance workflow:
//   DRAFT -> PEER_REVIEW -> CISO_APPROVAL -> SUBMITTED -> ACKNOWLEDGED
// (any non-terminal state may be CANCELLED).

import { api } from './api';

export interface NcaEccFiling {
  id: string;
  fiscalYear: number;
  frameworkVersion?: string;
  titleEn?: string;
  titleAr?: string;
  domain1Score?: number | null;
  domain2Score?: number | null;
  domain3Score?: number | null;
  domain4Score?: number | null;
  domain5Score?: number | null;
  overallScore?: number | string | null;
  responsesJson?: string | null;
  gapSummaryEn?: string | null;
  gapSummaryAr?: string | null;
  capaCount?: number;
  status: string;
  draftedBy: string;
  draftedAt?: string | null;
  peerReviewStartedAt?: string | null;
  peerReviewer?: string | null;
  peerReviewCompletedAt?: string | null;
  peerReviewCommentsEn?: string | null;
  cisoApprovalAt?: string | null;
  cisoApprover?: string | null;
  submittedAt?: string | null;
  submissionReference?: string | null;
  acknowledgedAt?: string | null;
  cancelledAt?: string | null;
  cancellationReasonEn?: string | null;
  linkedCapaIds?: string | null;
  linkedEvidence?: string | null;
}

export interface NcaEccTransition {
  status: string;
  /** Nominated on entry to PEER_REVIEW — who is being ASKED to review, not who has. */
  peerReviewer?: string;
  peerReviewCommentsEn?: string;
  // F2-225: `cisoApprover` is gone. The server stamps cisoApprovalAt from its own clock
  // in the same branch, so the approver is the authenticated principal. It is removed
  // from the type as well as the form, because a field the server ignores tells the
  // next caller their value matters (F2-184).
  submissionReference?: string;
  cancellationReasonEn?: string;
}

export interface NcaEccFilingCreate {
  fiscalYear: number;
  frameworkVersion?: string;
  titleEn?: string;
  titleAr?: string;
  // F2-225: `draftedBy` is gone. drafted_at is filled by the entity's own field
  // initialiser, so the server owns WHEN the draft was made and therefore owns WHO
  // made it. The server marks it READ_ONLY on the wire and refuses a create with no
  // authenticated principal.
  domain1Score?: number;
  domain2Score?: number;
  domain3Score?: number;
  domain4Score?: number;
  domain5Score?: number;
  gapSummaryEn?: string;
}

const BASE = '/api/nca-ecc/filings';

export const ncaEccFilingApi = {
  list:       () => api.get<NcaEccFiling[]>(BASE),
  get:        (id: string) => api.get<NcaEccFiling>(`${BASE}/${id}`),
  byYear:     (year: number) => api.get<NcaEccFiling>(`${BASE}/by-year/${year}`),
  create:     (body: NcaEccFilingCreate) => api.post<NcaEccFiling>(BASE, body),
  transition: (id: string, body: NcaEccTransition) => api.post<NcaEccFiling>(`${BASE}/${id}/transition`, body),
  remove:     (id: string) => api.delete<void>(`${BASE}/${id}`),
};

// ─── Lifecycle ───────────────────────────────────────────────────────────────

/** Mirrors the backend ALLOWED transition map. */
export const NCA_ALLOWED: Record<string, string[]> = {
  DRAFT:         ['PEER_REVIEW', 'CANCELLED'],
  PEER_REVIEW:   ['CISO_APPROVAL', 'DRAFT', 'CANCELLED'],
  CISO_APPROVAL: ['SUBMITTED', 'PEER_REVIEW', 'CANCELLED'],
  SUBMITTED:     ['ACKNOWLEDGED'],
  ACKNOWLEDGED:  [],
  CANCELLED:     [],
};

/** The forward (happy-path) successor for a status, or null if terminal. */
const FORWARD: Record<string, string> = {
  DRAFT: 'PEER_REVIEW', PEER_REVIEW: 'CISO_APPROVAL',
  CISO_APPROVAL: 'SUBMITTED', SUBMITTED: 'ACKNOWLEDGED',
};
export const forwardStatus = (status: string): string | null => FORWARD[status] ?? null;

export const NCA_STATUS_TONE: Record<string, 'neutral' | 'info' | 'warning' | 'success' | 'danger'> = {
  DRAFT: 'neutral', PEER_REVIEW: 'warning', CISO_APPROVAL: 'info',
  SUBMITTED: 'info', ACKNOWLEDGED: 'success', CANCELLED: 'danger',
};

export function ncaStatusLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'DRAFT':         return isAr ? 'مسودة' : 'Draft';
    case 'PEER_REVIEW':   return isAr ? 'مراجعة الأقران' : 'Peer review';
    case 'CISO_APPROVAL': return isAr ? 'اعتماد رئيس الأمن' : 'CISO approval';
    case 'SUBMITTED':     return isAr ? 'مُقدَّمة' : 'Submitted';
    case 'ACKNOWLEDGED':  return isAr ? 'مُقَرّة' : 'Acknowledged';
    case 'CANCELLED':     return isAr ? 'ملغاة' : 'Cancelled';
    default:              return status;
  }
}

/** Label for the forward-transition button from a given status. */
export function ncaAdvanceLabel(status: string, isAr: boolean): string {
  switch (status) {
    case 'DRAFT':         return isAr ? 'إرسال لمراجعة الأقران' : 'Send to peer review';
    case 'PEER_REVIEW':   return isAr ? 'اعتماد رئيس الأمن' : 'CISO approval';
    case 'CISO_APPROVAL': return isAr ? 'تقديم إلى الهيئة' : 'Submit to NCA';
    case 'SUBMITTED':     return isAr ? 'تأكيد الإقرار' : 'Mark acknowledged';
    default:              return isAr ? 'تقدّم' : 'Advance';
  }
}

export function overallScoreNum(f: NcaEccFiling): number | null {
  if (f.overallScore == null) return null;
  const n = typeof f.overallScore === 'string' ? parseFloat(f.overallScore) : f.overallScore;
  return Number.isFinite(n) ? n : null;
}

/** Maturity tone for a 1-5 score. */
export function scoreTone(score?: number | null): 'danger' | 'warning' | 'info' | 'success' | 'neutral' {
  if (score == null) return 'neutral';
  if (score >= 4.5) return 'success';
  if (score >= 3.5) return 'info';
  if (score >= 2.5) return 'warning';
  return 'danger';
}

// ─── The five NCA ECC-2:2024 domains ──────────────────────────────────────────

export interface NcaDomain { key: keyof NcaEccFiling; nameEn: string; nameAr: string }

export const NCA_DOMAINS: NcaDomain[] = [
  { key: 'domain1Score', nameEn: 'Cybersecurity Governance',            nameAr: 'حوكمة الأمن السيبراني' },
  { key: 'domain2Score', nameEn: 'Cybersecurity Defence',               nameAr: 'تحصين الأمن السيبراني' },
  { key: 'domain3Score', nameEn: 'Cybersecurity Resilience',            nameAr: 'صمود الأمن السيبراني' },
  { key: 'domain4Score', nameEn: 'Third-Party & Cloud Cybersecurity',   nameAr: 'الأمن السيبراني للأطراف الخارجية والحوسبة السحابية' },
  { key: 'domain5Score', nameEn: 'ICS Cybersecurity',                   nameAr: 'الأمن السيبراني لأنظمة التحكم الصناعي' },
];
