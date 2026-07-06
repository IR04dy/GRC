// WF-3 — API client for the platform workflow REQUEST engine (§6 of the workflow-logic
// remediation spec). Typed to the backend DTOs at /api/v1/workflows/requests: create with
// recipients (the WorkflowActorSelector payload), lifecycle transitions, inbox, timeline.
import { api } from './api';

export interface PageResp<T> {
  content: T[]; totalElements: number; totalPages: number;
  number: number; size: number; first: boolean; last: boolean;
}

// ─── §5 actor selector payload ───────────────────────────────────────
export type RecipientType = 'USER' | 'ROLE' | 'DEPARTMENT' | 'COMMITTEE' | 'EXTERNAL';
export type ActorRole = 'ASSIGNEE' | 'REVIEWER' | 'APPROVER' | 'OBSERVER' | 'ESCALATION';
export type RoutingMode = 'SEQUENTIAL' | 'PARALLEL' | 'BROADCAST_ACK';

export interface WorkflowRecipientInput {
  recipientType: RecipientType;
  recipientId?: number;          // USER / DEPARTMENT / COMMITTEE
  recipientKey?: string;         // ROLE name or EXTERNAL contact email
  recipientLabel?: string;
  actorRole?: ActorRole;
  stepNo?: number;               // sequential order (1-based)
}

/** WF-6 — conditional routing (§7 #5): when the recipient completing stepNo responds
 *  with onOutcome, the branch overrides the default routing. GOTO_STEP is forward-only. */
export type BranchAction = 'GOTO_STEP' | 'RETURN' | 'CLOSE' | 'ESCALATE';
export interface WorkflowBranchRule {
  stepNo: number;
  onOutcome: string;
  action: BranchAction;
  targetStep?: number | null;    // GOTO_STEP only
}

export interface WorkflowRequestCreate {
  sourceModule: string;
  sourceRecordType?: string;
  sourceRecordId?: number;
  requestType: string;           // EVIDENCE_REQUEST / APPROVAL / REVIEW / TASK / ACKNOWLEDGEMENT / ...
  titleEn: string;
  titleAr?: string;
  description?: string;
  routingMode?: RoutingMode;
  completionRule?: 'ALL' | 'ANY';
  requiredAction?: string;
  dueDate?: string;
  slaHours?: number;
  priority?: 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
  severity?: string;
  reminderDaysBefore?: number;
  escalationRole?: string;
  escalationUserId?: number;
  responseRequired?: boolean;
  evidenceRequired?: boolean;
  closureCondition?: string;
  reviewerId?: number;
  approverId?: number;
  recipients: WorkflowRecipientInput[];
  branchRules?: WorkflowBranchRule[];   // WF-6
}

// ─── Responses ───────────────────────────────────────────────────────
export interface WorkflowRecipientDto {
  id: number; recipientType: RecipientType; recipientId: number | null;
  recipientKey: string | null; recipientLabel: string | null;
  actorRole: ActorRole; stepNo: number; status: string;
  respondedAt: string | null; respondedBy: number | null; response: string | null;
  responseOutcome: string | null;   // WF-6
}

export interface WorkflowEventDto {
  id: number; eventType: string; fromStatus: string | null; toStatus: string | null;
  actor: string | null; actorUserId: number | null; note: string | null; createdAt: string;
}

export interface WorkflowRequestDto {
  id: number; requestCode: string;
  sourceModule: string; sourceRecordType: string | null; sourceRecordId: number | null;
  requestType: string; titleEn: string; titleAr: string | null; description: string | null;
  initiatedBy: number | null; initiatedByName: string | null;
  recipientCategory: string | null; routingMode: RoutingMode; completionRule: string; currentStep: number;
  requiredAction: string | null; dueDate: string | null; slaHours: number | null;
  countdownStatus: 'NONE' | 'ON_TRACK' | 'DUE_TODAY' | 'OVERDUE'; hoursRemaining: number | null;
  priority: string; severity: string | null;
  reminderDaysBefore: number; lastReminderAt: string | null; reminderCount: number;
  escalationRole: string | null; escalationUserId: number | null; escalatedAt: string | null;
  status: string; responseRequired: boolean; evidenceRequired: boolean; closureCondition: string | null;
  reviewerId: number | null; approverId: number | null;
  evidenceRequestId: number | null;   // WF-4 — the bridged evidence.evidence_requests row
  closedAt: string | null; reopenedAt: string | null;
  createdAt: string; updatedAt: string | null;
  recipients: WorkflowRecipientDto[] | null;
  branchRules: WorkflowBranchRule[] | null;   // WF-6
}

const act = (id: number, action: string, note?: string, outcome?: string) =>
  api.post<WorkflowRequestDto>(`/api/v1/workflows/requests/${id}/${action}`,
    { note: note ?? null, outcome: outcome ?? null });

export const workflowRequestsApi = {
  create: (body: WorkflowRequestCreate) =>
    api.post<WorkflowRequestDto>('/api/v1/workflows/requests', body),
  list: (status?: string, page = 0, size = 50) =>
    api.get<PageResp<WorkflowRequestDto>>(
      `/api/v1/workflows/requests?page=${page}&size=${size}${status ? `&status=${encodeURIComponent(status)}` : ''}`),
  inbox: () => api.get<WorkflowRequestDto[]>('/api/v1/workflows/requests/inbox'),
  get: (id: number) => api.get<WorkflowRequestDto>(`/api/v1/workflows/requests/${id}`),
  auditLog: (id: number) => api.get<WorkflowEventDto[]>(`/api/v1/workflows/requests/${id}/audit-log`),

  send:     (id: number)                => act(id, 'send'),
  /** WF-6 — outcome (optional) can trigger a conditional branch rule. */
  respond:  (id: number, note?: string, outcome?: string) => act(id, 'respond', note, outcome),
  submit:   (id: number, note?: string) => act(id, 'submit', note),
  review:   (id: number, outcome: 'APPROVE' | 'RETURN', note?: string) => act(id, 'review', note, outcome),
  approve:  (id: number, note?: string) => act(id, 'approve', note),
  reject:   (id: number, note?: string) => act(id, 'reject', note),
  resubmit: (id: number, note?: string) => act(id, 'resubmit', note),
  escalate: (id: number, note?: string) => act(id, 'escalate', note),
  close:    (id: number, note?: string) => act(id, 'close', note),
  reopen:   (id: number, note?: string) => act(id, 'reopen', note),

  runSlaSweep: () => api.post<{ reminders: number; escalations: number }>(
    '/api/v1/workflows/requests/run-sla-sweep'),
};

// ─── WF-7 — EXTERNAL recipient public tracker (token-credentialed, NO auth) ──
export interface PublicRequestView {
  requestCode: string; requestType: string; titleEn: string; titleAr: string | null;
  requiredAction: string | null; description: string | null;
  dueDate: string | null; status: string; countdownStatus: string; priority: string;
  requestedBy: string | null; recipientLabel: string | null; recipientStatus: string; canRespond: boolean;
}

const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');

/** Bare fetch — deliberately attaches NO Authorization header; the path token is the credential. */
async function publicFetch<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(`${API_BASE}${path}`, {
    ...init, headers: { 'Content-Type': 'application/json', ...(init?.headers ?? {}) },
  });
  if (!res.ok) { const e = new Error(`HTTP ${res.status}`) as Error & { status?: number }; e.status = res.status; throw e; }
  return res.json() as Promise<T>;
}

export const publicWorkflowApi = {
  view: (token: string) =>
    publicFetch<PublicRequestView>(`/api/public/workflow-requests/${encodeURIComponent(token)}`),
  respond: (token: string, note?: string, outcome?: string) =>
    publicFetch<PublicRequestView>(`/api/public/workflow-requests/${encodeURIComponent(token)}/respond`,
      { method: 'POST', body: JSON.stringify({ note: note ?? null, outcome: outcome ?? null }) }),
};
