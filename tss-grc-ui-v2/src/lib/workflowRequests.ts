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
}

// ─── Responses ───────────────────────────────────────────────────────
export interface WorkflowRecipientDto {
  id: number; recipientType: RecipientType; recipientId: number | null;
  recipientKey: string | null; recipientLabel: string | null;
  actorRole: ActorRole; stepNo: number; status: string;
  respondedAt: string | null; respondedBy: number | null; response: string | null;
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
  closedAt: string | null; reopenedAt: string | null;
  createdAt: string; updatedAt: string | null;
  recipients: WorkflowRecipientDto[] | null;
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
  respond:  (id: number, note?: string) => act(id, 'respond', note),
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
