import { clearSession, getAccessToken, setSession, type UserInfo } from './auth';

// VITE_API_BASE is empty by default → calls go to /api/* on the same origin and Vite proxies
// to http://localhost:8084. Set VITE_API_BASE in .env.local to bypass the proxy.
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: unknown) {
    super(message);
  }
}

interface FetchOpts extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Skip Authorization header even if a token exists. */
  anonymous?: boolean;
}

async function request<T>(path: string, opts: FetchOpts = {}): Promise<T> {
  const { body, anonymous, headers, ...rest } = opts;
  const token = getAccessToken();

  const init: RequestInit = {
    ...rest,
    headers: {
      'Accept': 'application/json',
      ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}),
      ...(token && !anonymous ? { 'Authorization': `Bearer ${token}` } : {}),
      ...(headers as Record<string, string> | undefined),
    },
    credentials: 'include',
    body: body !== undefined ? JSON.stringify(body) : undefined,
  };

  const res = await fetch(`${API_BASE}${path}`, init);
  const text = await res.text();
  let parsed: unknown = undefined;
  if (text) {
    try { parsed = JSON.parse(text); } catch { parsed = text; }
  }

  if (!res.ok) {
    const msg = (parsed as { message?: string })?.message ?? res.statusText ?? 'Request failed';
    if (res.status === 401) clearSession();
    throw new ApiError(res.status, msg, parsed);
  }
  return parsed as T;
}

export const api = {
  get:    <T>(p: string, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'GET' }),
  post:   <T>(p: string, body?: unknown, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'POST', body }),
  put:    <T>(p: string, body?: unknown, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'PUT',  body }),
  patch:  <T>(p: string, body?: unknown, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'PATCH', body }),
  delete: <T>(p: string, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'DELETE' }),
};

/**
 * Download a binary file from the API as a browser-triggered save.
 * The filename is taken from the Content-Disposition header when present,
 * otherwise falls back to the supplied `fallbackName`.
 */
export async function downloadFile(path: string, fallbackName: string): Promise<void> {
  const token = getAccessToken();
  const res = await fetch(`${API_BASE}${path}`, {
    method: 'GET',
    headers: { ...(token ? { 'Authorization': `Bearer ${token}` } : {}) },
    credentials: 'include',
  });
  if (!res.ok) {
    if (res.status === 401) clearSession();
    throw new ApiError(res.status, res.statusText || 'Download failed');
  }
  const cd = res.headers.get('Content-Disposition') ?? '';
  const m = /filename="?([^";]+)"?/i.exec(cd);
  const name = m ? m[1] : fallbackName;
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url; a.download = name;
  document.body.appendChild(a); a.click();
  setTimeout(() => { URL.revokeObjectURL(url); a.remove(); }, 0);
}

// ─── Change Management (ITIL v4) ────────────────────────────────────────────
export interface ChangeRequestDto {
  id: number; organisationId?: number;
  changeCode?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  changeType: string;     // STANDARD | NORMAL | EMERGENCY
  category?: string;
  riskLevel?: string;     // LOW | MEDIUM | HIGH | CRITICAL
  impactSummary?: string;
  rollbackPlan?: string;
  testPlan?: string;
  businessJustification?: string;
  requestedByName?: string;
  changeOwnerName?: string;
  implementerName?: string;
  requestedDate?: string;
  scheduledStart?: string;
  scheduledEnd?: string;
  implementationStarted?: string;
  implementationEnded?: string;
  postReviewDate?: string;
  closureDate?: string;
  status: string;
  cabDecisionAt?: string;
  cabDecisionNotes?: string;
  outcome?: string;
  createdAt?: string;
}
export interface ChangeApprovalDto {
  id?: number; changeRequestId?: number;
  approverId?: number;
  approverName: string; approverRole?: string;
  decision: 'APPROVE' | 'REJECT' | 'DEFER' | 'ABSTAIN';
  comments?: string;
  decidedAt?: string;
}
export interface ChangeTaskDto {
  id?: number; changeRequestId?: number;
  sortOrder?: number;
  titleEn: string; titleAr?: string;
  ownerName?: string;
  status: string;          // PENDING | IN_PROGRESS | DONE | BLOCKED | SKIPPED
  dueAt?: string; completedAt?: string;
  notes?: string;
}
export interface ChangeLinkDto {
  id?: number; changeRequestId?: number;
  entityType: string; entityId: number;
  linkType: string;        // AFFECTS | TRIGGERED_BY | MITIGATES | TARGETS | REMEDIATES | DEPENDS_ON | INFORMS
  notes?: string;
  createdAt?: string;
}
export interface ChangeCommentDto {
  id?: number; changeRequestId?: number;
  authorName: string; body: string;
  createdAt?: string;
}
export interface ChangeAnalyticsDto {
  total: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
  byOutcome: Record<string, number>;
  successRatePct: number;
}
/** Used by other modules to render a "Linked Changes" panel for any entity. */
export interface LinkedChangeView {
  linkId: number;
  linkType: string;
  changeId: number;
  changeCode?: string;
  titleEn: string;
  titleAr?: string;
  status: string;
  changeType: string;
  riskLevel?: string;
  scheduledStart?: string;
}

export const changeApi = {
  list:        (qs: { status?: string; type?: string; risk?: string; category?: string; page?: number; size?: number } = {}) => {
    const p = new URLSearchParams();
    if (qs.status)   p.set('status', qs.status);
    if (qs.type)     p.set('type', qs.type);
    if (qs.risk)     p.set('risk', qs.risk);
    if (qs.category) p.set('category', qs.category);
    p.set('page', String(qs.page ?? 0));
    p.set('size', String(qs.size ?? 100));
    return api.get<PageResponse<ChangeRequestDto>>(`/api/v1/changes?${p.toString()}`);
  },
  get:         (id: number) => api.get<ChangeRequestDto>(`/api/v1/changes/${id}`),
  create:      (body: Partial<ChangeRequestDto>) => api.post<ChangeRequestDto>('/api/v1/changes', body),
  update:      (id: number, body: Partial<ChangeRequestDto>) => api.put<ChangeRequestDto>(`/api/v1/changes/${id}`, body),
  remove:      (id: number) => api.delete<void>(`/api/v1/changes/${id}`),
  transition:  (id: number, body: { target: string; notes?: string; outcome?: string }) =>
                 api.post<ChangeRequestDto>(`/api/v1/changes/${id}/transition`, body),

  approvals:   (id: number) => api.get<ChangeApprovalDto[]>(`/api/v1/changes/${id}/approvals`),
  approve:     (id: number, body: ChangeApprovalDto) => api.post<ChangeApprovalDto>(`/api/v1/changes/${id}/approvals`, body),

  tasks:       (id: number) => api.get<ChangeTaskDto[]>(`/api/v1/changes/${id}/tasks`),
  addTask:     (id: number, body: Partial<ChangeTaskDto>) => api.post<ChangeTaskDto>(`/api/v1/changes/${id}/tasks`, body),
  updateTask:  (taskId: number, body: Partial<ChangeTaskDto>) => api.put<ChangeTaskDto>(`/api/v1/changes/tasks/${taskId}`, body),
  deleteTask:  (taskId: number) => api.delete<void>(`/api/v1/changes/tasks/${taskId}`),

  links:       (id: number) => api.get<ChangeLinkDto[]>(`/api/v1/changes/${id}/links`),
  addLink:     (id: number, body: Partial<ChangeLinkDto>) => api.post<ChangeLinkDto>(`/api/v1/changes/${id}/links`, body),
  deleteLink:  (linkId: number) => api.delete<void>(`/api/v1/changes/links/${linkId}`),
  linksForEntity: (entityType: string, entityId: number) =>
                  api.get<LinkedChangeView[]>(`/api/v1/changes/links/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),

  comments:    (id: number) => api.get<ChangeCommentDto[]>(`/api/v1/changes/${id}/comments`),
  comment:     (id: number, body: Partial<ChangeCommentDto>) => api.post<ChangeCommentDto>(`/api/v1/changes/${id}/comments`, body),

  analytics:   () => api.get<ChangeAnalyticsDto>('/api/v1/changes/analytics'),
};

export const reportingApi = {
  riskRegisterPdf:  (lang: string) => downloadFile(`/api/v1/reporting/risk-register.pdf?lang=${encodeURIComponent(lang)}`, 'risk-register.pdf'),
  riskRegisterXlsx: () => downloadFile('/api/v1/reporting/risk-register.xlsx', 'risk-register.xlsx'),
  findingsPdf:      (lang: string) => downloadFile(`/api/v1/reporting/findings-aging.pdf?lang=${encodeURIComponent(lang)}`, 'findings.pdf'),
  findingsXlsx:     () => downloadFile('/api/v1/reporting/findings-aging.xlsx', 'findings.xlsx'),
  kriPdf:           (lang: string) => downloadFile(`/api/v1/reporting/kri-dashboard.pdf?lang=${encodeURIComponent(lang)}`, 'kri-dashboard.pdf'),
  kriXlsx:          () => downloadFile('/api/v1/reporting/kri-dashboard.xlsx', 'kri-dashboard.xlsx'),
};

// ─── Spring Page<T> wrapper ─────────────────────────────────────────────────
export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

function pageQ(page = 0, size = 50, status?: string, extra: Record<string, string | number | undefined> = {}): string {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));
  if (status) params.set('status', status);
  for (const [k, v] of Object.entries(extra)) if (v !== undefined && v !== '') params.set(k, String(v));
  return `?${params.toString()}`;
}

// ─── Auth ───────────────────────────────────────────────────────────────────
export interface LoginResponse { accessToken: string; user: UserInfo; }
export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>('/api/auth/login', { username, password }, { anonymous: true });
  setSession(res.accessToken, res.user);
  return res;
}
export async function logout(): Promise<void> {
  try { await api.post('/api/auth/logout'); } catch { /* ignore */ }
  clearSession();
}

// ─── Risk ───────────────────────────────────────────────────────────────────
export interface RiskResponse {
  id: number; externalId?: string; riskCode: string;
  titleEn: string; titleAr?: string; descriptionEn?: string;
  categoryId?: number; categoryName?: string; categoryNameEn?: string; categoryNameAr?: string;
  ownerId?: number; ownerName?: string; ownerFullNameEn?: string; ownerUsername?: string;
  status: string;
  inherentLikelihood: number; inherentImpact: number; inherentScore: number;
  residualLikelihood?: number; residualImpact?: number; residualScore?: number;
  riskLevel?: string; identifiedDate?: string; reviewDate?: string;
  createdAt?: string; updatedAt?: string;
}
export interface RiskRequest {
  titleEn: string; titleAr?: string; descriptionEn?: string;
  categoryId?: number; ownerId?: number;
  inherentLikelihood: number; inherentImpact: number;
  residualLikelihood?: number; residualImpact?: number;
  status?: string; reviewDate?: string;
}
export interface RiskCategoryDto {
  id: number; nameEn: string; nameAr?: string;
  isActive?: boolean;
}
export const riskApi = {
  list:       (status?: string, page = 0, size = 50) => api.get<PageResponse<RiskResponse>>(`/api/v1/risk/risks${pageQ(page, size, status)}`),
  get:        (id: number) => api.get<RiskResponse>(`/api/v1/risk/risks/${id}`),
  create:     (body: RiskRequest) => api.post<RiskResponse>('/api/v1/risk/risks', body),
  update:     (id: number, body: RiskRequest) => api.put<RiskResponse>(`/api/v1/risk/risks/${id}`, body),
  remove:     (id: number) => api.delete<void>(`/api/v1/risk/risks/${id}`),
  categories: () => api.get<RiskCategoryDto[]>('/api/v1/risk/categories'),
  // Risk lifecycle workflow (ISO 31000 / COSO ERM)
  submitForReview: (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/submit-for-review`),
  approveRisk:     (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/approve`),
  acceptRisk:      (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/accept`),
  closeRisk:       (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/close`),
  reopenRisk:      (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/reopen`),
  incidentBump:    (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/incident-bump`),
};

// ─── Platform — Organisations / Tenant switcher (P1) ────────────────────────
export interface OrganisationView {
  id: number;
  nameEn: string; nameAr?: string;
  shortCode: string;
  industrySector?: string; countryCode?: string;
  subscriptionTier?: string;
  primaryColor?: string; logoUrl?: string;
  isCurrent: boolean;
}
export interface OrgSwitchResponse {
  accessToken: string;
  organisationId: number;
  organisationNameEn: string;
  organisationNameAr?: string;
  shortCode: string;
}
export const platformApi = {
  accessibleOrgs: () => api.get<OrganisationView[]>('/api/v1/platform/organisations/accessible'),
  switchOrg:      (id: number) => api.post<OrgSwitchResponse>(`/api/v1/platform/organisations/switch/${id}`),
};

// ─── KRI threshold engine — assessment §5.1 ERM #5 ───────────────────────────
export interface KriDefinitionDto {
  id: number;
  organisationId?: number;
  riskId?: number; categoryId?: number;
  code?: string;
  nameEn: string; nameAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  unit?: string;
  direction: string; // HIGHER_IS_WORSE | LOWER_IS_WORSE
  greenMax?: number;
  amberMax?: number;
  targetValue?: number;
  frequency?: string;
  owner?: string;
  dataSource?: string;
  framework?: string;
  isActive?: boolean;
}
export interface KriReadingDto {
  id: number; kriId: number;
  readingValue: number; readingAt: string;
  band?: string; notes?: string;
}
export interface KriBreachEventDto {
  id: number; kriId: number; readingId: number;
  breachBand: string; readingValue: number; thresholdCrossed: number;
  breachedAt: string;
  acknowledgedAt?: string; acknowledgedById?: number;
  resolvedAt?: string; resolutionNotes?: string;
  status: string;
}

// ─── Risk appetite — assessment §5.1 ERM #6 ──────────────────────────────────
export interface RiskAppetiteStatementDto {
  id: number;
  organisationId?: number;
  categoryId?: number;
  titleEn: string; titleAr?: string;
  statementEn?: string; statementAr?: string;
  riskDimension: string;
  appetiteLevel: string;
  toleranceLower?: number; toleranceUpper?: number;
  unit?: string;
  currentExposure?: number;
  approvedBy?: string; approvedAt?: string;
  nextReviewDate?: string;
  status: string;
  createdAt?: string;
}
export interface AppetiteBreachEventDto {
  id: number; appetiteId: number;
  observedValue: number; breachSeverity: string;
  breachedAt: string;
  notes?: string;
  status: string;
}

export const ermAdvancedApi = {
  // KRIs
  kris:        (page = 0, size = 50) => api.get<PageResponse<KriDefinitionDto>>(`/api/v1/risk/kris${pageQ(page, size)}`),
  getKri:      (id: number) => api.get<KriDefinitionDto>(`/api/v1/risk/kris/${id}`),
  createKri:   (body: Partial<KriDefinitionDto>) => api.post<KriDefinitionDto>('/api/v1/risk/kris', body),
  updateKri:   (id: number, body: Partial<KriDefinitionDto>) => api.put<KriDefinitionDto>(`/api/v1/risk/kris/${id}`, body),
  deleteKri:   (id: number) => api.delete<void>(`/api/v1/risk/kris/${id}`),
  recordReading: (id: number, value: number, notes?: string) =>
                 api.post<{ readingId: number; band: string; previousBand: string; breachCreated: boolean; breachId?: number }>(`/api/v1/risk/kris/${id}/readings`, { value, notes }),
  readings:    (id: number) => api.get<KriReadingDto[]>(`/api/v1/risk/kris/${id}/readings`),
  breaches:    (id: number) => api.get<KriBreachEventDto[]>(`/api/v1/risk/kris/${id}/breaches`),
  ackBreach:   (breachId: number, userId?: number) => api.post<KriBreachEventDto>(`/api/v1/risk/kris/breaches/${breachId}/acknowledge`, { userId }),
  resolveBreach: (breachId: number, notes?: string) => api.post<KriBreachEventDto>(`/api/v1/risk/kris/breaches/${breachId}/resolve`, { notes }),
  kriDashboard: () => api.get<{ totalKris: number; green: number; amber: number; red: number; noData: number; openBreaches: number }>('/api/v1/risk/kris/dashboard'),
  // Appetite
  appetites:   (page = 0, size = 50) => api.get<PageResponse<RiskAppetiteStatementDto>>(`/api/v1/risk/appetite${pageQ(page, size)}`),
  getAppetite: (id: number) => api.get<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}`),
  createAppetite: (body: Partial<RiskAppetiteStatementDto>) => api.post<RiskAppetiteStatementDto>('/api/v1/risk/appetite', body),
  updateAppetite: (id: number, body: Partial<RiskAppetiteStatementDto>) => api.put<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}`, body),
  deleteAppetite: (id: number) => api.delete<void>(`/api/v1/risk/appetite/${id}`),
  approveAppetite: (id: number, approvedBy: string) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/approve`, { approvedBy }),
  retireAppetite:  (id: number) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/retire`),
  updateExposure:  (id: number, value: number, notes?: string) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/exposure`, { value, notes }),
  appetiteBreaches: (id: number) => api.get<AppetiteBreachEventDto[]>(`/api/v1/risk/appetite/${id}/breaches`),
  appetiteDashboard: () => api.get<{ total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number }>('/api/v1/risk/appetite/dashboard'),
};

// ─── Dashboard ──────────────────────────────────────────────────────────────
export interface DashboardStats {
  totalRisks: number; criticalRisks: number; highRisks: number; mediumRisks: number; lowRisks: number;
  openRisks: number; closedRisks: number; totalUsers: number; activeUsers: number;
  openCriticalRisks: number; openHighRisks: number; openIncidents: number;
  complianceScore: number; pendingTasks: number;
}
export const dashboardApi = {
  stats: () => api.get<DashboardStats>('/api/v1/dashboard/stats'),
  heatmap: () => api.get<unknown>('/api/v1/dashboard/heatmap'),
  riskByCategory: () => api.get<unknown>('/api/v1/dashboard/risk-by-category'),
  recentRisks: () => api.get<unknown>('/api/v1/dashboard/recent-risks'),
  recentIncidents: () => api.get<unknown>('/api/v1/dashboard/recent-incidents'),
  complianceByFramework: () => api.get<unknown>('/api/v1/dashboard/compliance-by-framework'),
};

// ─── BCM ────────────────────────────────────────────────────────────────────
export interface BcmPlanDto {
  id: number; planNameEn: string; planNameAr?: string;
  planType?: string; version?: string; status: string;
  nextReviewDate?: string; createdAt?: string;
}
export interface BcmPlanRequest {
  planNameEn: string; planNameAr?: string;
  planType?: string; version?: string; status?: string;
  nextReviewDate?: string;
}
export interface BcmProcessDto {
  id: number; processNameEn: string; processNameAr?: string;
  ownerId?: number; criticality?: string;
  status?: string;
}
export interface BcmProcessRequest {
  processNameEn: string; processNameAr?: string;
  ownerId?: number | null; criticality?: string;
}
export interface BcmBiaDto {
  id: number; processId?: number;
  assessmentDate?: string;
  rtoHours?: number; rpoHours?: number; mtpdHours?: number;
  overallImpact?: string; status?: string;
  linkedRiskId?: number;
}
export interface BcmBiaRequest {
  processId?: number | null;
  assessmentDate?: string;
  rtoHours?: number | null; rpoHours?: number | null; mtpdHours?: number | null;
  overallImpact?: string; status?: string;
  linkedRiskId?: number | null;
}
export interface BcmExerciseDto {
  id: number;
  planId?: number;
  exerciseNameEn: string; exerciseNameAr?: string;
  exerciseType: string; scenario?: string;
  scheduledDate?: string; executedDate?: string;
  score?: number; status: string;
  findings?: string; capaCount?: number;
  facilitator?: string;
}
export interface BcmExerciseRequest {
  planId?: number | null;
  exerciseNameEn: string; exerciseNameAr?: string;
  exerciseType?: string; scenario?: string;
  scheduledDate?: string; executedDate?: string;
  score?: number | null; status?: string;
  findings?: string; capaCount?: number;
  facilitator?: string;
}
export const bcmApi = {
  plans:        (page = 0, size = 50) => api.get<PageResponse<BcmPlanDto>>(`/api/v1/bcm/plans${pageQ(page, size)}`),
  createPlan:   (body: BcmPlanRequest) => api.post<BcmPlanDto>('/api/v1/bcm/plans', body),
  updatePlan:   (id: number, body: BcmPlanRequest) => api.put<BcmPlanDto>(`/api/v1/bcm/plans/${id}`, body),
  deletePlan:   (id: number) => api.delete<void>(`/api/v1/bcm/plans/${id}`),
  // Plan ISO-22301 lifecycle workflow
  submitPlanForReview: (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/plans/${id}/submit-for-review`),
  approvePlan:  (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/plans/${id}/approve`),
  invokePlan:   (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/plans/${id}/invoke`),
  restorePlan:  (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/plans/${id}/restore`),
  processes:      (page = 0, size = 50) => api.get<PageResponse<BcmProcessDto>>(`/api/v1/bcm/processes${pageQ(page, size)}`),
  createProcess:  (body: BcmProcessRequest) => api.post<BcmProcessDto>('/api/v1/bcm/processes', body),
  updateProcess:  (id: number, body: BcmProcessRequest) => api.put<BcmProcessDto>(`/api/v1/bcm/processes/${id}`, body),
  deleteProcess:  (id: number) => api.delete<void>(`/api/v1/bcm/processes/${id}`),
  /** /api/v1/bcm/bia returns a plain array, not a Page<T>. */
  bia:          () => api.get<BcmBiaDto[]>('/api/v1/bcm/bia'),
  createBia:    (body: BcmBiaRequest) => api.post<BcmBiaDto>('/api/v1/bcm/bia', body),
  updateBia:    (id: number, body: BcmBiaRequest) => api.put<BcmBiaDto>(`/api/v1/bcm/bia/${id}`, body),
  deleteBia:    (id: number) => api.delete<void>(`/api/v1/bcm/bia/${id}`),
  // Exercises (ISO 22301 §8.5)
  exercises:        (page = 0, size = 50) => api.get<PageResponse<BcmExerciseDto>>(`/api/v1/bcm/exercises${pageQ(page, size)}`),
  createExercise:   (body: BcmExerciseRequest) => api.post<BcmExerciseDto>('/api/v1/bcm/exercises', body),
  updateExercise:   (id: number, body: BcmExerciseRequest) => api.put<BcmExerciseDto>(`/api/v1/bcm/exercises/${id}`, body),
  deleteExercise:   (id: number) => api.delete<void>(`/api/v1/bcm/exercises/${id}`),
  completeExercise: (id: number, score: number, findings: string) =>
    api.post<BcmExerciseDto>(`/api/v1/bcm/exercises/${id}/complete`, { score, findings }),
  // BIA dependency graph (M3)
  dependencyGraph: () => api.get<{
    nodes: Array<{ id: number; nameEn: string; nameAr?: string; criticality?: string }>;
    edges: Array<{ id: number; from: number; to: number; dependencyType: string; criticalityImpact?: string; notes?: string }>;
  }>('/api/v1/bcm/dependency-graph'),
  listDependencies:  () => api.get<Array<{ id: number; processId: number; dependsOnProcessId: number; dependencyType: string; criticalityImpact?: string; notes?: string }>>('/api/v1/bcm/dependencies'),
  createDependency:  (body: { processId: number; dependsOnProcessId: number; dependencyType?: string; criticalityImpact?: string; notes?: string }) =>
                       api.post<unknown>('/api/v1/bcm/dependencies', body),
  deleteDependency:  (id: number) => api.delete<void>(`/api/v1/bcm/dependencies/${id}`),
  stats:        () => api.get<unknown>('/api/v1/bcm/stats'),
};

// ─── Cyber · Controls ───────────────────────────────────────────────────────
export interface CyberControlDto {
  id: number; frameworkId?: number; frameworkName?: string; domain?: string;
  controlNumber: string; titleEn: string; titleAr?: string;
  procedureEn?: string;
  controlType?: string; controlOwner?: string;
  reviewFrequency?: string; maturityLevel?: string;
  implementationStatus?: string; status?: string;
}

// ─── Cyber · Sub-Controls ───────────────────────────────────────────────────
export interface CyberSubControlDto {
  id: number; controlId: number;
  subControlNumber: string; titleEn: string; titleAr?: string;
  descriptionEn?: string;
  controlOwner?: string;
  maturityLevel?: string; implementationStatus?: string;
  evidenceRequired?: boolean;
}
export interface CyberSubControlRequest {
  controlId: number; subControlNumber?: string;
  titleEn: string; titleAr?: string; descriptionEn?: string;
  controlOwner?: string;
  maturityLevel?: string; implementationStatus?: string;
  evidenceRequired?: boolean;
}

// ─── Cyber · Assessments ────────────────────────────────────────────────────
export interface CyberAssessmentDto {
  id: number; controlId: number;
  assessmentDate?: string; assessorName?: string; reviewerName?: string;
  result?: string; maturityScore?: number;
  gapIdentified?: boolean; gapDescription?: string;
  riskLevel?: string; status?: string;
  assessorComments?: string; reviewerComments?: string;
  evidenceCount?: number; capaId?: number;
}
export interface CyberAssessmentRequest {
  controlId: number;
  assessorName?: string; reviewerName?: string;
  assessmentDate?: string;
  result?: string; maturityScore?: number;
  riskLevel?: string; gapIdentified?: boolean;
  gapDescription?: string;
  assessorComments?: string; reviewerComments?: string;
  status?: string;
}

// ─── Cyber · Incidents ──────────────────────────────────────────────────────
export interface CyberIncidentDto {
  id: number; incidentNumber: string;
  titleEn: string; titleAr?: string;
  incidentType?: string;
  affectedAssetId?: number; affectedAssetName?: string;
  impact?: string; severity?: string;
  workflowStatus?: string; status?: string;
  detectedAt?: string; reportedAt?: string; resolvedAt?: string;
  detectionDate?: string;
  incidentLead?: string;
  notificationSent?: boolean; bcmHandoff?: boolean;
  linkedVulnerabilitiesCount?: number; linkedEvidenceCount?: number;
  lessonsLearned?: string;
  responseStage?: string;
}
export interface CyberIncidentRequest {
  titleEn: string; titleAr?: string;
  incidentType?: string;
  affectedAssetId?: number;
  severity?: string; impact?: string;
  workflowStatus?: string;
  incidentLead?: string;
  bcmHandoff?: boolean;
  lessonsLearned?: string;
}

// ─── Cyber · Assets ─────────────────────────────────────────────────────────
export interface CyberAssetDto {
  id: number; assetName: string; assetType?: string;
  businessOwner?: string; technicalOwner?: string;
  classification?: string; criticality?: string;
  ipAddress?: string; hostname?: string; operatingSystem?: string;
  linkedVulnerabilitiesCount?: number;
  linkedIncidentsCount?: number;
  linkedControlsCount?: number;
  status?: string;
}
export interface CyberAssetRequest {
  assetName: string; assetType?: string;
  businessOwner?: string; technicalOwner?: string;
  classification?: string; criticality?: string;
  ipAddress?: string; hostname?: string; operatingSystem?: string;
  status?: string;
}

// ─── Cyber · Evidence ───────────────────────────────────────────────────────
export interface CyberEvidenceDto {
  id: number; titleEn: string; titleAr?: string;
  evidenceType?: string;
  controlId?: number; assessmentId?: number;
  frameworkRequirement?: string; ownerName?: string;
  version?: string;
  reviewDate?: string; expiryDate?: string;
  collectedDate?: string;
  fileName?: string; status?: string;
}
export interface CyberEvidenceRequest {
  titleEn: string;
  evidenceType?: string;
  controlId?: number;
  frameworkRequirement?: string; ownerName?: string;
  version?: string;
  reviewDate?: string; expiryDate?: string;
  fileName?: string; status?: string;
}

// ─── Cyber · Action Plans ───────────────────────────────────────────────────
export interface CyberActionPlanDto {
  id: number; titleEn: string; titleAr?: string;
  descriptionEn?: string;
  sourceType?: string; sourceId?: number; sourceRef?: string;
  priority?: string; ownerName?: string;
  dueDate?: string; targetDate?: string;
  workflowStatus?: string; status?: string;
  progressPct?: number; progressPercent?: number;
  completionEvidence?: string; validationNotes?: string;
  linkedControlId?: number;
  startDate?: string; completedAt?: string;
}
export interface CyberActionPlanRequest {
  titleEn: string; titleAr?: string;
  descriptionEn?: string;
  priority?: string; ownerName?: string;
  dueDate?: string;
  workflowStatus?: string;
  progressPct?: number;
  sourceType?: string;
  completionEvidence?: string;
}

// ─── Cyber · Vulnerability Exceptions ───────────────────────────────────────
export interface CyberExceptionDto {
  id: number; vulnerabilityId?: number;
  titleEn: string;
  exceptionReason?: string;
  workflowStatus?: string;
  ownerName?: string; approverName?: string;
  compensatingControls?: string;
  impactAssessment?: string;
  expiryDate?: string; approvedAt?: string;
}
export interface CyberExceptionRequest {
  vulnerabilityId?: number;
  titleEn: string;
  ownerName?: string; approverName?: string;
  expiryDate?: string;
  workflowStatus?: string;
  impactAssessment?: string;
  exceptionReason?: string;
  compensatingControls?: string;
}
export interface CyberControlRequest {
  controlNumber: string; titleEn: string; titleAr?: string;
  frameworkId?: number; frameworkName?: string;
  domain?: string;
  procedureEn?: string;
  controlType?: string; controlOwner?: string;
  reviewFrequency?: string; maturityLevel?: string;
  implementationStatus?: string;
  status?: string;
}
export interface CyberVulnDto {
  id: number;
  assetId?: number; assetName?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string;
  severity: string; cvssScore?: number;
  vulnerabilityType?: string; cveId?: string;
  ownerName?: string;
  slaDays?: number; dueDate?: string;
  workflowStatus?: string; status?: string;
  remediationNotes?: string;
  capaId?: number;
  overdue?: boolean;
  reopenedCount?: number;
  discoveryDate?: string; targetResolutionDate?: string;
}
export interface CyberVulnRequest {
  assetId?: number;
  titleEn: string; titleAr?: string;
  descriptionEn?: string;
  severity: string; cvssScore?: number;
  vulnerabilityType?: string; cveId?: string;
  ownerName?: string;
  slaDays?: number; dueDate?: string;
  workflowStatus?: string;
  remediationNotes?: string;
}
export const cyberApi = {
  controls:       (page = 0, size = 50) => api.get<PageResponse<CyberControlDto>>(`/api/v1/cyber/controls${pageQ(page, size)}`),
  createControl:  (body: CyberControlRequest) => api.post<CyberControlDto>('/api/v1/cyber/controls', body),
  updateControl:  (id: number, body: CyberControlRequest) => api.put<CyberControlDto>(`/api/v1/cyber/controls/${id}`, body),
  deleteControl:  (id: number) => api.delete<void>(`/api/v1/cyber/controls/${id}`),

  vulns:          (page = 0, size = 50) => api.get<PageResponse<CyberVulnDto>>(`/api/v1/cyber/vulnerabilities${pageQ(page, size)}`),
  createVuln:     (body: CyberVulnRequest) => api.post<CyberVulnDto>('/api/v1/cyber/vulnerabilities', body),
  updateVuln:     (id: number, body: CyberVulnRequest) => api.put<CyberVulnDto>(`/api/v1/cyber/vulnerabilities/${id}`, body),
  deleteVuln:     (id: number) => api.delete<void>(`/api/v1/cyber/vulnerabilities/${id}`),

  // Sub-controls
  subControls:        (page = 0, size = 50) => api.get<PageResponse<CyberSubControlDto>>(`/api/v1/cyber/sub-controls${pageQ(page, size)}`),
  createSubControl:   (body: CyberSubControlRequest) => api.post<CyberSubControlDto>('/api/v1/cyber/sub-controls', body),
  updateSubControl:   (id: number, body: CyberSubControlRequest) => api.put<CyberSubControlDto>(`/api/v1/cyber/sub-controls/${id}`, body),
  deleteSubControl:   (id: number) => api.delete<void>(`/api/v1/cyber/sub-controls/${id}`),

  // Assessments
  assessments:        (page = 0, size = 50) => api.get<PageResponse<CyberAssessmentDto>>(`/api/v1/cyber/assessments${pageQ(page, size)}`),
  createAssessment:   (body: CyberAssessmentRequest) => api.post<CyberAssessmentDto>('/api/v1/cyber/assessments', body),
  updateAssessment:   (id: number, body: CyberAssessmentRequest) => api.put<CyberAssessmentDto>(`/api/v1/cyber/assessments/${id}`, body),
  deleteAssessment:   (id: number) => api.delete<void>(`/api/v1/cyber/assessments/${id}`),
  approveAssessment:  (id: number) => api.post<CyberAssessmentDto>(`/api/v1/cyber/assessments/${id}/approve`),
  convertAssessmentGap: (id: number) => api.post(`/api/v1/cyber/assessments/${id}/convert-gap`),

  // Vulnerabilities (already declared above as createVuln/updateVuln/deleteVuln)
  escalateVuln:       (id: number) => api.post(`/api/v1/cyber/vulnerabilities/${id}/escalate`),

  // Vulnerability exceptions
  exceptions:         (page = 0, size = 50) => api.get<PageResponse<CyberExceptionDto>>(`/api/v1/cyber/exceptions${pageQ(page, size)}`),
  createException:    (body: CyberExceptionRequest) => api.post<CyberExceptionDto>('/api/v1/cyber/exceptions', body),
  updateException:    (id: number, body: CyberExceptionRequest) => api.put<CyberExceptionDto>(`/api/v1/cyber/exceptions/${id}`, body),
  deleteException:    (id: number) => api.delete<void>(`/api/v1/cyber/exceptions/${id}`),
  approveException:   (id: number) => api.post<CyberExceptionDto>(`/api/v1/cyber/exceptions/${id}/approve`),

  // Incidents
  incidents:          (page = 0, size = 50) => api.get<PageResponse<CyberIncidentDto>>(`/api/v1/cyber/incidents${pageQ(page, size)}`),
  createIncident:     (body: CyberIncidentRequest) => api.post<CyberIncidentDto>('/api/v1/cyber/incidents', body),
  updateIncident:     (id: number, body: CyberIncidentRequest) => api.put<CyberIncidentDto>(`/api/v1/cyber/incidents/${id}`, body),
  deleteIncident:     (id: number) => api.delete<void>(`/api/v1/cyber/incidents/${id}`),
  startResponse:      (id: number) => api.post<CyberIncidentDto>(`/api/v1/cyber/incidents/${id}/start-response`),

  // Assets
  assets:             (page = 0, size = 50) => api.get<PageResponse<CyberAssetDto>>(`/api/v1/cyber/assets${pageQ(page, size)}`),
  assetsAll:          () => api.get<CyberAssetDto[]>('/api/v1/cyber/assets/all'),
  createAsset:        (body: CyberAssetRequest) => api.post<CyberAssetDto>('/api/v1/cyber/assets', body),
  updateAsset:        (id: number, body: CyberAssetRequest) => api.put<CyberAssetDto>(`/api/v1/cyber/assets/${id}`, body),
  deleteAsset:        (id: number) => api.delete<void>(`/api/v1/cyber/assets/${id}`),

  // Evidence
  evidence:           (page = 0, size = 50) => api.get<PageResponse<CyberEvidenceDto>>(`/api/v1/cyber/evidence${pageQ(page, size)}`),
  createEvidence:     (body: CyberEvidenceRequest) => api.post<CyberEvidenceDto>('/api/v1/cyber/evidence', body),
  updateEvidence:     (id: number, body: CyberEvidenceRequest) => api.put<CyberEvidenceDto>(`/api/v1/cyber/evidence/${id}`, body),
  deleteEvidence:     (id: number) => api.delete<void>(`/api/v1/cyber/evidence/${id}`),

  // Action Plans
  actionPlans:        (page = 0, size = 50) => api.get<PageResponse<CyberActionPlanDto>>(`/api/v1/cyber/action-plans${pageQ(page, size)}`),
  createActionPlan:   (body: CyberActionPlanRequest) => api.post<CyberActionPlanDto>('/api/v1/cyber/action-plans', body),
  updateActionPlan:   (id: number, body: CyberActionPlanRequest) => api.put<CyberActionPlanDto>(`/api/v1/cyber/action-plans/${id}`, body),
  deleteActionPlan:   (id: number) => api.delete<void>(`/api/v1/cyber/action-plans/${id}`),
  completePlan:       (id: number) => api.post<CyberActionPlanDto>(`/api/v1/cyber/action-plans/${id}/complete`),

  // Catalog
  controlsAll:        () => api.get<CyberControlDto[]>('/api/v1/cyber/controls/all'),
  dashboard:          () => api.get<unknown>('/api/v1/cyber/dashboard'),
  frameworks:         () => api.get<unknown>('/api/v1/cyber/frameworks'),
};

// ─── Privacy ────────────────────────────────────────────────────────────────
export interface PrivacyDsrDto {
  id: number; requestType: string;
  requesterName: string; requesterEmail?: string;
  receivedAt?: string; dueDate?: string;
  status: string;
}
export interface PrivacyDsrRequest {
  requestType: string; requesterName: string; requesterEmail?: string;
  receivedAt?: string; dueDate?: string; status?: string;
  description?: string;
}
export interface PrivacyBreachDto {
  id: number; titleEn: string; descriptionEn?: string;
  breachType?: string; severity?: string; status?: string;
  occurredAt?: string; reportedAt?: string;
  affectedRecords?: number;
}
export interface PrivacyRopaDto {
  id: number; activityNameEn: string; activityNameAr?: string;
  purposeEn?: string; legalBasis?: string;
  retentionPeriod?: string; status?: string;
}
export interface PrivacyBreachRequest {
  titleEn: string; titleAr?: string;
  description?: string; severity?: string;
  affectedRecords?: number | null; discoveredAt?: string;
  reportedToAuthority?: boolean; status?: string;
}
export interface PrivacyRopaRequest {
  processNameEn: string; processNameAr?: string;
  legalBasis?: string; dataCategories?: string;
  retentionPeriod?: string; status?: string;
}
export const privacyApi = {
  dsrs:           (page = 0, size = 50) => api.get<PageResponse<PrivacyDsrDto>>(`/api/v1/privacy/dsrs${pageQ(page, size)}`),
  createDsr:      (body: PrivacyDsrRequest) => api.post<PrivacyDsrDto>('/api/v1/privacy/dsrs', body),
  updateDsr:      (id: number, body: PrivacyDsrRequest) => api.put<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}`, body),
  deleteDsr:      (id: number) => api.delete<void>(`/api/v1/privacy/dsrs/${id}`),
  // DSR workflow (PDPL 30-day SLA)
  startDsr:       (id: number) => api.post<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}/start`),
  fulfilDsr:      (id: number) => api.post<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}/fulfil`),
  extendDsr:      (id: number, days: number) => api.post<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}/extend`, { days }),
  rejectDsr:      (id: number) => api.post<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}/reject`),
  breaches:       (page = 0, size = 50) => api.get<PageResponse<PrivacyBreachDto>>(`/api/v1/privacy/breaches${pageQ(page, size)}`),
  createBreach:   (body: PrivacyBreachRequest) => api.post<PrivacyBreachDto>('/api/v1/privacy/breaches', body),
  updateBreach:   (id: number, body: PrivacyBreachRequest) => api.put<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}`, body),
  deleteBreach:   (id: number) => api.delete<void>(`/api/v1/privacy/breaches/${id}`),
  // Breach workflow (PDPL Article 20 — 72-hour notification)
  containBreach:  (id: number) => api.post<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}/contain`),
  notifyDataOffice: (id: number) => api.post<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}/notify-data-office`),
  closeBreach:    (id: number) => api.post<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}/close`),
  ropa:           (page = 0, size = 50) => api.get<PageResponse<PrivacyRopaDto>>(`/api/v1/privacy/ropa${pageQ(page, size)}`),
  createRopa:     (body: PrivacyRopaRequest) => api.post<PrivacyRopaDto>('/api/v1/privacy/ropa', body),
  updateRopa:     (id: number, body: PrivacyRopaRequest) => api.put<PrivacyRopaDto>(`/api/v1/privacy/ropa/${id}`, body),
  deleteRopa:     (id: number) => api.delete<void>(`/api/v1/privacy/ropa/${id}`),
  stats:          () => api.get<unknown>('/api/v1/privacy/stats'),
};

// ─── Compliance ─────────────────────────────────────────────────────────────
export interface ComplianceControlDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  framework: string; domain?: string;
  maturityLevel?: number; status: string; owner?: string; description?: string;
}
export interface ComplianceControlRequest {
  code: string; nameEn: string; nameAr?: string;
  framework: string; domain?: string;
  maturityLevel?: number; status?: string; owner?: string; description?: string;
}
export interface ComplianceAssessmentDto {
  id: number; framework: string; period: string;
  auditor?: string; score?: number; status: string;
  assessmentDate?: string; notes?: string;
}
export interface ComplianceFindingDto {
  id: number; title: string;
  severity: string; owner?: string;
  capa?: string; dueDate?: string; status: string;
  // CAPA closure verification — §5.3 #6
  capaStage?: string;
  ownerTestAt?: string;       ownerTestResult?: string;       ownerTestNotes?: string;
  independentTestAt?: string; independentTestResult?: string; independentTestNotes?: string;
  auditSignoffAt?: string;    auditSignoffNotes?: string;
  // Framework anchor (IA-2)
  frameworkId?: number; clauseRef?: string;
}
export interface ComplianceAssessmentRequest {
  framework: string; period: string;
  auditor?: string; score?: number | null; status?: string;
  assessmentDate?: string; notes?: string;
}
export interface ComplianceFindingRequest {
  title: string; severity?: string;
  owner?: string; capa?: string;
  dueDate?: string; status?: string;
  frameworkId?: number; clauseRef?: string;
}
// Regulatory change management (assessment §5.3 critical #1)
export interface RegulatoryChangeDto {
  id: number; sourceAuthority: string;
  titleEn: string; titleAr?: string; citation?: string;
  summaryEn?: string; summaryAr?: string;
  publishedDate?: string; effectiveDate?: string;
  applicability: string; impactLevel: string; status: string;
  impactSummary?: string; controlsUpdatedCount?: number;
  assignedToId?: number; closedAt?: string;
}
export interface RegulatoryChangeRequest {
  sourceAuthority: string;
  titleEn: string; titleAr?: string; citation?: string;
  summaryEn?: string; summaryAr?: string;
  publishedDate?: string; effectiveDate?: string;
  applicability?: string; impactLevel?: string;
  impactSummary?: string;
  assignedToId?: number | null;
}

// Policy lifecycle (assessment §5.3 critical #2)
export interface PolicyDto {
  id: number; code: string;
  titleEn: string; titleAr?: string;
  version: string; category?: string;
  contentEn?: string; contentAr?: string;
  status: string;
  authorId?: number; legalReviewerId?: number; approverId?: number;
  legalReviewedAt?: string; approvedAt?: string;
  publishedAt?: string; effectiveFrom?: string;
  nextReviewDate?: string; retiredAt?: string;
  rejectionReason?: string;
}
export interface PolicyRequest {
  code: string;
  titleEn: string; titleAr?: string;
  version?: string; category?: string;
  contentEn?: string; contentAr?: string;
  effectiveFrom?: string; nextReviewDate?: string;
}

export const complianceApi = {
  controls:           (page = 0, size = 50) => api.get<PageResponse<ComplianceControlDto>>(`/api/v1/compliance/controls${pageQ(page, size)}`),
  createControl:      (body: ComplianceControlRequest) => api.post<ComplianceControlDto>('/api/v1/compliance/controls', body),
  updateControl:      (id: number, body: ComplianceControlRequest) => api.put<ComplianceControlDto>(`/api/v1/compliance/controls/${id}`, body),
  deleteControl:      (id: number) => api.delete<void>(`/api/v1/compliance/controls/${id}`),
  assessments:        (page = 0, size = 50) => api.get<PageResponse<ComplianceAssessmentDto>>(`/api/v1/compliance/assessments${pageQ(page, size)}`),
  createAssessment:   (body: ComplianceAssessmentRequest) => api.post<ComplianceAssessmentDto>('/api/v1/compliance/assessments', body),
  updateAssessment:   (id: number, body: ComplianceAssessmentRequest) => api.put<ComplianceAssessmentDto>(`/api/v1/compliance/assessments/${id}`, body),
  findings:           (page = 0, size = 50) => api.get<PageResponse<ComplianceFindingDto>>(`/api/v1/compliance/findings${pageQ(page, size)}`),
  createFinding:      (body: ComplianceFindingRequest) => api.post<ComplianceFindingDto>('/api/v1/compliance/findings', body),
  updateFinding:      (id: number, body: ComplianceFindingRequest) => api.put<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}`, body),
  deleteFinding:      (id: number) => api.delete<void>(`/api/v1/compliance/findings/${id}`),
  // Regulatory change management — §5.3 critical #1
  regulatoryChanges:  (page = 0, size = 50) => api.get<PageResponse<RegulatoryChangeDto>>(`/api/v1/compliance/regulatory-changes${pageQ(page, size)}`),
  createChange:       (body: RegulatoryChangeRequest) => api.post<RegulatoryChangeDto>('/api/v1/compliance/regulatory-changes', body),
  updateChange:       (id: number, body: RegulatoryChangeRequest) => api.put<RegulatoryChangeDto>(`/api/v1/compliance/regulatory-changes/${id}`, body),
  deleteChange:       (id: number) => api.delete<void>(`/api/v1/compliance/regulatory-changes/${id}`),
  assessApplicability: (id: number, applicability: string) => api.post<RegulatoryChangeDto>(`/api/v1/compliance/regulatory-changes/${id}/assess-applicability`, { applicability }),
  assessImpact:       (id: number, impactLevel: string, impactSummary: string) => api.post<RegulatoryChangeDto>(`/api/v1/compliance/regulatory-changes/${id}/assess-impact`, { impactLevel, impactSummary }),
  markControlsUpdated:(id: number, controlsUpdatedCount: number) => api.post<RegulatoryChangeDto>(`/api/v1/compliance/regulatory-changes/${id}/mark-controls-updated`, { controlsUpdatedCount }),
  closeChange:        (id: number) => api.post<RegulatoryChangeDto>(`/api/v1/compliance/regulatory-changes/${id}/close`),
  changeStats:        () => api.get<unknown>('/api/v1/compliance/regulatory-changes/stats'),
  // Policy lifecycle — §5.3 critical #2
  policies:           (page = 0, size = 50) => api.get<PageResponse<PolicyDto>>(`/api/v1/compliance/policies${pageQ(page, size)}`),
  createPolicy:       (body: PolicyRequest) => api.post<PolicyDto>('/api/v1/compliance/policies', body),
  updatePolicy:       (id: number, body: PolicyRequest) => api.put<PolicyDto>(`/api/v1/compliance/policies/${id}`, body),
  deletePolicy:       (id: number) => api.delete<void>(`/api/v1/compliance/policies/${id}`),
  submitForLegalReview: (id: number) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/submit-for-legal-review`),
  legalApprovePolicy: (id: number) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/legal-approve`),
  execApprovePolicy:  (id: number) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/exec-approve`),
  markPolicyReviewDue:(id: number) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/mark-review-due`),
  retirePolicy:       (id: number) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/retire`),
  rejectPolicy:       (id: number, reason: string) => api.post<PolicyDto>(`/api/v1/compliance/policies/${id}/reject`, { reason }),
  policyStats:        () => api.get<unknown>('/api/v1/compliance/policies/stats'),
  // Findings aging engine — §5.3 recommendation #4
  findingsAging:      () => api.get<Array<ComplianceFindingDto & { daysOpen: number; escalationTier: string; createdAt?: string; capaStage?: string }>>('/api/v1/compliance/findings/aging'),
  findingsAgingStats: () => api.get<{ onTrack: number; managerEscalated: number; directorEscalated: number; execEscalated: number }>('/api/v1/compliance/findings/aging/stats'),
  stats:              () => api.get<unknown>('/api/v1/compliance/stats'),

  // CAPA closure verification — §5.3 recommendation #6
  capaStartImplementing: (id: number) => api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/start-implementing`),
  capaOwnerTest:         (id: number, body: { result: 'PASS' | 'FAIL' | 'PARTIAL'; notes?: string; evidenceId?: number }) =>
                            api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/owner-test`, body),
  capaIndependentTest:   (id: number, body: { result: 'PASS' | 'FAIL' | 'PARTIAL'; notes?: string; byUserId?: number }) =>
                            api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/independent-test`, body),
  capaAuditSignoff:      (id: number, body: { notes?: string; byUserId?: number }) =>
                            api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/audit-signoff`, body),
  capaClose:             (id: number) => api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/close`),

  // External audit engagements — §5.3 recommendation #3
  auditEngagements:      (page = 0, size = 50) => api.get<PageResponse<AuditEngagementDto>>(`/api/v1/compliance/audit-engagements${pageQ(page, size)}`),
  createAuditEngagement: (body: AuditEngagementRequest) => api.post<AuditEngagementDto>('/api/v1/compliance/audit-engagements', body),
  updateAuditEngagement: (id: number, body: AuditEngagementRequest) => api.put<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}`, body),
  deleteAuditEngagement: (id: number) => api.delete<void>(`/api/v1/compliance/audit-engagements/${id}`),
  advanceAuditEngagement: (id: number, status: string) => api.post<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}/advance`, { status }),
  scheduleAuditFollowup:  (id: number, followupDate: string) => api.post<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}/schedule-followup`, { followupDate }),
  auditEngagementStats:   () => api.get<Record<string, number>>('/api/v1/compliance/audit-engagements/stats'),
};

// ─── Compliance frameworks (IA-1 framework anchor) ───────────────────────────
export interface FrameworkDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  version?: string; issuer?: string;
  family?: string; isActive: boolean;
}
export interface PolicyMappingDto {
  mappingId: number; frameworkId: number;
  frameworkCode?: string; frameworkName?: string; frameworkVersion?: string;
  clauseRef?: string;
  satisfaction: 'FULL' | 'PARTIAL' | 'OUT_OF_SCOPE' | 'NOT_APPLICABLE';
  notes?: string;
}
export interface FrameworkPolicyRowDto {
  mappingId: number;
  policyId: number; code: string;
  titleEn: string; titleAr?: string;
  version?: string; category?: string; status?: string;
  nextReviewDate?: string;
  clauseRef?: string;
  satisfaction: string;
  notes?: string;
}
export interface FrameworkCoverageDto {
  frameworkId: number; code: string;
  nameEn: string; nameAr?: string;
  family?: string;
  policyCount: number;
  full: number; partial: number;
  outOfScope: number; notApplicable: number;
  openFindings?: number; closedFindings?: number;
  controlCount?: number; implementedControls?: number; avgMaturity?: number;
}
export interface FrameworkFindingRowDto {
  id: number; title: string;
  severity: string; owner?: string;
  dueDate?: string; status: string;
  capaStage?: string; clauseRef?: string;
  createdAt?: string;
}
export interface FrameworkControlRowDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  domain?: string; maturityLevel?: number;
  status?: string; owner?: string;
}
export const frameworkApi = {
  list:           ()                 => api.get<FrameworkDto[]>('/api/v1/compliance/frameworks'),
  policiesFor:    (frameworkId: number) => api.get<FrameworkPolicyRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/policies`),
  findingsFor:    (frameworkId: number) => api.get<FrameworkFindingRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/findings`),
  controlsFor:    (frameworkId: number) => api.get<FrameworkControlRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/controls`),
  mappingsFor:    (policyId: number) => api.get<PolicyMappingDto[]>(`/api/v1/compliance/policies/${policyId}/mappings`),
  addMapping:     (policyId: number, body: { frameworkId: number; clauseRef?: string; satisfaction?: string; notes?: string }) =>
                    api.post<PolicyMappingDto>(`/api/v1/compliance/policies/${policyId}/mappings`, body),
  updateMapping:  (mappingId: number, body: { clauseRef?: string; satisfaction?: string; notes?: string }) =>
                    api.put<PolicyMappingDto>(`/api/v1/compliance/policies/mappings/${mappingId}`, body),
  deleteMapping:  (mappingId: number) => api.delete<void>(`/api/v1/compliance/policies/mappings/${mappingId}`),
  coverage:       () => api.get<FrameworkCoverageDto[]>('/api/v1/compliance/frameworks/coverage'),
};

// External audit engagement DTO — §5.3 #3
export interface AuditEngagementDto {
  id: number;
  externalId?: string;
  organisationId?: number;
  engagementCode?: string;
  titleEn: string;
  titleAr?: string;
  auditType: string;
  framework?: string;
  auditorFirm?: string;
  leadAuditor?: string;
  engagementLetterDate?: string;
  openingMeetingDate?: string;
  fieldworkStartDate?: string;
  fieldworkEndDate?: string;
  reportIssuedDate?: string;
  closureDate?: string;
  nextFollowupDate?: string;
  scopeSummary?: string;
  status: string;
  findingsCount?: number;
  createdAt?: string;
  updatedAt?: string;
}
export interface AuditEngagementRequest {
  engagementCode?: string;
  titleEn: string;
  titleAr?: string;
  auditType: string;
  framework?: string;
  auditorFirm?: string;
  leadAuditor?: string;
  engagementLetterDate?: string;
  openingMeetingDate?: string;
  fieldworkStartDate?: string;
  fieldworkEndDate?: string;
  reportIssuedDate?: string;
  closureDate?: string;
  nextFollowupDate?: string;
  scopeSummary?: string;
  status?: string;
}

// ─── TPRM ───────────────────────────────────────────────────────────────────
export interface TprmVendorDto {
  id: number; vendorName: string; vendorNameAr?: string;
  vendorType?: string; country?: string;
  riskRating: string; status: string;
  contractEndDate?: string; description?: string;
}
export interface TprmVendorRequest {
  vendorName: string; vendorNameAr?: string;
  vendorType?: string; country?: string;
  riskRating?: string; status?: string;
  contractEndDate?: string; description?: string;
}
export const tprmApi = {
  vendors:       (page = 0, size = 50) => api.get<PageResponse<TprmVendorDto>>(`/api/v1/tprm/vendors${pageQ(page, size)}`),
  createVendor:  (body: TprmVendorRequest) => api.post<TprmVendorDto>('/api/v1/tprm/vendors', body),
  updateVendor:  (id: number, body: TprmVendorRequest) => api.put<TprmVendorDto>(`/api/v1/tprm/vendors/${id}`, body),
  deleteVendor:  (id: number) => api.delete<void>(`/api/v1/tprm/vendors/${id}`),
  stats:         () => api.get<unknown>('/api/v1/tprm/stats'),
};

// ─── Incidents ──────────────────────────────────────────────────────────────
export interface IncidentDto {
  id: number; title: string; titleAr?: string;
  severity: string; category?: string; status: string;
  assignee?: string; discoveryDate?: string;
  description?: string;
}
export interface IncidentRequest {
  title: string; titleAr?: string;
  severity: string; category?: string; status?: string;
  assignee?: string; discoveryDate?: string;
  description?: string;
}
export interface IncidentPhaseSnapshot {
  id: number; status: string; nistPhase: string;
  ncaHeyNotifiedAt?: string; dataOfficeNotifiedAt?: string;
  citcNotifiedAt?: string; samaNotifiedAt?: string; hrsdNotifiedAt?: string;
  mitreTactics?: string; mitreTechniques?: string;
}
export interface RegulatoryTemplate {
  code: 'NCA_HEY' | 'DATA_OFFICE' | 'CITC' | 'SAMA' | 'HRSD';
  titleEn: string; titleAr: string;
  deadlineHours: number; hoursSinceDiscovery: number; hoursRemaining: number;
  overdue: boolean; notifiedAt?: string;
  submissionChannel: string; applicabilityHint: string;
  prefilledBody: string;
}
export type SaudiRegulator = 'NCA_HEY' | 'DATA_OFFICE' | 'CITC' | 'SAMA' | 'HRSD';
export const incidentApi = {
  list:    (page = 0, size = 50) => api.get<PageResponse<IncidentDto>>(`/api/v1/incidents${pageQ(page, size)}`),
  get:     (id: number) => api.get<IncidentDto>(`/api/v1/incidents/${id}`),
  create:  (body: IncidentRequest) => api.post<IncidentDto>('/api/v1/incidents', body),
  update:  (id: number, body: IncidentRequest) => api.put<IncidentDto>(`/api/v1/incidents/${id}`, body),
  remove:  (id: number) => api.delete<void>(`/api/v1/incidents/${id}`),
  stats:   () => api.get<unknown>('/api/v1/incidents/stats'),
  // NIST 800-61 lifecycle
  advancePhase:    (id: number) => api.post<IncidentPhaseSnapshot>(`/api/v1/incidents/${id}/advance-phase`),
  setPhase:        (id: number, phase: string) => api.post<IncidentPhaseSnapshot>(`/api/v1/incidents/${id}/set-phase`, { phase }),
  closeIncident:   (id: number) => api.post<IncidentPhaseSnapshot>(`/api/v1/incidents/${id}/close`),
  // Saudi regulator notification
  notifyRegulator: (id: number, regulator: SaudiRegulator) => api.post<IncidentPhaseSnapshot>(`/api/v1/incidents/${id}/notify-regulator`, { regulator }),
  regulatoryTemplates: (id: number) => api.get<RegulatoryTemplate[]>(`/api/v1/incidents/${id}/regulatory-templates`),
};

// ─── Evidence ───────────────────────────────────────────────────────────────
export interface EvidenceItemDto {
  id: number; evidenceCode: string;
  subControlCode?: string; controlCode?: string; frameworkCode?: string;
  titleEn: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  evidenceType: string; status?: string;
  collectedDate?: string; expiryDate?: string;
}
export interface EvidenceItemRequest {
  titleEn: string; titleAr?: string; descriptionEn?: string;
  evidenceType: string; subControlId?: number;
  collectedDate?: string; expiryDate?: string;
}
export interface EvidenceRequestDto {
  id: number; requestCode: string;
  subControlCode?: string; frameworkCode?: string;
  titleEn: string; titleAr?: string;
  status?: string; requestedAt?: string; dueDate?: string;
  ownerName?: string;
}
export interface EvidenceReviewDto {
  id: number; evidenceId: number;
  evidenceTitleEn?: string;
  reviewerName?: string; reviewDate?: string;
  result?: string; notesEn?: string;
}
// Cross-framework matrix view (assessment §5.9 — control/sub-control as prime key, framework as tab)
export interface SubControlBlock {
  subControlId: number; subControlCode: string;
  nameEn: string; nameAr?: string; evidenceGuidance?: string;
  evidenceCount: number; freshestExpiry?: string;
  freshness: 'GREEN' | 'AMBER' | 'RED' | 'GREY';
  methodMix: Record<string, number>;
}
export interface ControlBlock {
  controlId: number; controlCode: string;
  nameEn: string; nameAr?: string; descriptionEn?: string;
  evidenceCount: number; freshestExpiry?: string;
  freshness: 'GREEN' | 'AMBER' | 'RED' | 'GREY';
  subControls: SubControlBlock[];
}
export interface FrameworkBlock {
  frameworkId: number; code: string;
  nameEn: string; nameAr?: string;
  version?: string; issuer?: string;
  controls: ControlBlock[];
  controlCount: number; evidenceCount: number;
}

// Version history (assessment §5.9 #1)
export interface EvidenceVersionDto {
  id: number; evidenceId: number; versionNo: number;
  titleEn?: string; titleAr?: string; descriptionEn?: string;
  evidenceHash?: string; previousHash?: string;
  fileName?: string; fileSize?: number;
  changeSummary?: string;
  snapshottedAt: string; snapshottedBy?: number;
}

// Connector library (assessment §5.9 #2)
export interface ConnectorDefinitionDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  connectorType: string; vendor?: string;
  descriptionEn?: string; collectionPlanEn?: string;
  targetFrameworks?: string;
  status: 'AVAILABLE' | 'CONFIGURED' | 'RUNNING' | 'DISABLED';
  lastRunAt?: string; lastRunStatus?: string; lastRunSummary?: string;
  evidenceCollectedCount: number;
}

export const evidenceApi = {
  items:        (page = 0, size = 50) => api.get<PageResponse<EvidenceItemDto>>(`/api/v1/evidence/items${pageQ(page, size)}`),
  createItem:   (body: EvidenceItemRequest) => api.post<EvidenceItemDto>('/api/v1/evidence/items', body),
  updateItem:   (id: number, body: EvidenceItemRequest) => api.put<EvidenceItemDto>(`/api/v1/evidence/items/${id}`, body),
  deleteItem:   (id: number) => api.delete<void>(`/api/v1/evidence/items/${id}`),
  approve:      (id: number) => api.post<EvidenceItemDto>(`/api/v1/evidence/items/${id}/approve`),
  reject:       (id: number) => api.post<EvidenceItemDto>(`/api/v1/evidence/items/${id}/reject`),
  stats:        () => api.get<unknown>('/api/v1/evidence/items/stats'),
  expired:      () => api.get<PageResponse<EvidenceItemDto>>('/api/v1/evidence/items/expired'),
  requests:     (page = 0, size = 50) => api.get<PageResponse<EvidenceRequestDto>>(`/api/v1/evidence/requests${pageQ(page, size)}`),
  reviews:      (page = 0, size = 50) => api.get<PageResponse<EvidenceReviewDto>>(`/api/v1/evidence/reviews${pageQ(page, size)}`),
  // Cross-framework matrix + version history + connectors (assessment §5.9)
  matrix:       () => api.get<FrameworkBlock[]>('/api/v1/evidence/matrix'),
  versions:     (id: number) => api.get<EvidenceVersionDto[]>(`/api/v1/evidence/items/${id}/versions`),
  snapshot:     (id: number, changeSummary: string, evidenceHash?: string) =>
                  api.post<EvidenceVersionDto>(`/api/v1/evidence/items/${id}/snapshot`, { changeSummary, evidenceHash }),
  connectors:   () => api.get<ConnectorDefinitionDto[]>('/api/v1/evidence/connectors'),
  setConnectorStatus: (id: number, status: string) =>
                  api.post<ConnectorDefinitionDto>(`/api/v1/evidence/connectors/${id}/status`, { status }),
  runConnector: (id: number) => api.post<ConnectorDefinitionDto>(`/api/v1/evidence/connectors/${id}/run`),
};

// ─── PDPL DPIA (assessment §5.4 critical #3) ────────────────────────────────
export interface DpiaDto {
  id: number; titleEn: string; titleAr?: string;
  triggerReason: string;
  status: string;
  overallResidualRisk?: string;
  processingActivityId?: number;
  dpoId?: number; assessorId?: number;
  assessmentDate?: string; nextReviewDate?: string;
  approvedAt?: string; rejectedAt?: string;
  rejectionReason?: string; conclusion?: string;
}
export const dpiaApi = {
  list:            (page = 0, size = 50) => api.get<PageResponse<DpiaDto>>(`/api/v1/privacy/dpias${pageQ(page, size)}`),
  get:             (id: number) => api.get<{ dpia: DpiaDto; questions: unknown[]; mitigations: unknown[]; reviews: unknown[] }>(`/api/v1/privacy/dpias/${id}`),
  create:          (body: Partial<DpiaDto>) => api.post<DpiaDto>('/api/v1/privacy/dpias', body),
  update:          (id: number, body: Partial<DpiaDto>) => api.put<DpiaDto>(`/api/v1/privacy/dpias/${id}`, body),
  remove:          (id: number) => api.delete<void>(`/api/v1/privacy/dpias/${id}`),
  submitForReview: (id: number) => api.post<DpiaDto>(`/api/v1/privacy/dpias/${id}/submit-for-review`),
  approve:         (id: number, conclusion?: string) => api.post<DpiaDto>(`/api/v1/privacy/dpias/${id}/approve`, { conclusion }),
  reject:          (id: number, reason: string) => api.post<DpiaDto>(`/api/v1/privacy/dpias/${id}/reject`, { reason }),
  expire:          (id: number) => api.post<DpiaDto>(`/api/v1/privacy/dpias/${id}/expire`),
  stats:           () => api.get<{ draftCount: number; inReviewCount: number; approvedCount: number; rejectedCount: number; expiredCount: number }>('/api/v1/privacy/dpias/stats'),
};

// ─── BCM Crisis Communication (assessment §5.5 #5) ─────────────────────────
export interface CrisisCommsTemplateDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  audienceRole: string; channel: string;
  subjectEn?: string; subjectAr?: string;
  bodyEn: string; bodyAr?: string;
  severityBand?: string; version: string;
  isActive: boolean;
}
export interface CrisisCommsAudienceDto {
  id: number;
  name: string; nameAr?: string;
  audienceRole: string;
  contactsCsv?: string;
  recipientCount: number;
  isActive: boolean;
}
export interface CrisisCommsDistributionDto {
  id: number; templateId: number; audienceId: number;
  planId?: number; channel: string;
  subjectSnapshot?: string; bodySnapshot?: string;
  recipientCount: number; sentAt: string; sentById?: number;
  deliveryStatus: string; deliverySummary?: string;
}
export const crisisCommsApi = {
  templates:        () => api.get<CrisisCommsTemplateDto[]>('/api/v1/bcm/comms/templates'),
  createTemplate:   (body: Partial<CrisisCommsTemplateDto>) => api.post<CrisisCommsTemplateDto>('/api/v1/bcm/comms/templates', body),
  updateTemplate:   (id: number, body: Partial<CrisisCommsTemplateDto>) => api.put<CrisisCommsTemplateDto>(`/api/v1/bcm/comms/templates/${id}`, body),
  deleteTemplate:   (id: number) => api.delete<void>(`/api/v1/bcm/comms/templates/${id}`),
  audiences:        () => api.get<CrisisCommsAudienceDto[]>('/api/v1/bcm/comms/audiences'),
  createAudience:   (body: Partial<CrisisCommsAudienceDto>) => api.post<CrisisCommsAudienceDto>('/api/v1/bcm/comms/audiences', body),
  updateAudience:   (id: number, body: Partial<CrisisCommsAudienceDto>) => api.put<CrisisCommsAudienceDto>(`/api/v1/bcm/comms/audiences/${id}`, body),
  distributions:    () => api.get<CrisisCommsDistributionDto[]>('/api/v1/bcm/comms/distributions'),
  send:             (templateId: number, audienceId: number, planId?: number, placeholders?: Record<string, string>) =>
    api.post<CrisisCommsDistributionDto>('/api/v1/bcm/comms/send', { templateId, audienceId, planId, placeholders: placeholders ?? {} }),
};

// ─── Workflow Engine (assessment §6.1) ──────────────────────────────────────
export interface WorkflowDefinitionDto {
  id: number; code: string;
  nameEn: string; nameAr?: string;
  targetModule: string; targetEntity: string;
  version: string; descriptionEn?: string;
  isActive: boolean;
}
export interface WorkflowStateDto {
  id: number; definitionId: number; code: string;
  nameEn: string; nameAr?: string;
  isInitial: boolean; isTerminal: boolean;
  sortOrder: number; uiColor?: string;
  descriptionEn?: string;
}
export interface WorkflowTransitionView {
  id: number;
  fromStateCode: string; toStateCode: string;
  actionCode: string;
  actionNameEn: string; actionNameAr?: string;
  requiredRole?: string; slaHours?: number;
  descriptionEn?: string;
}
export interface WorkflowDefinitionGraph {
  definition: WorkflowDefinitionDto;
  states: WorkflowStateDto[];
  transitions: WorkflowTransitionView[];
}
export interface WorkflowInstanceDto {
  id: number; definitionId: number;
  targetModule: string; targetEntity: string; targetEntityId: number;
  currentStateId: number; ownerId?: number;
  startedAt: string; completedAt?: string; lastTransitionAt?: string;
}
export const workflowApi = {
  definitions:    (targetModule?: string) => api.get<WorkflowDefinitionDto[]>(
    `/api/v1/workflow/definitions${targetModule ? `?targetModule=${encodeURIComponent(targetModule)}` : ''}`),
  graph:          (id: number) => api.get<WorkflowDefinitionGraph>(`/api/v1/workflow/definitions/${id}`),
  startInstance:  (body: { definitionId: number; targetModule: string; targetEntity: string; targetEntityId: number; ownerId?: number }) =>
    api.post<WorkflowInstanceDto>('/api/v1/workflow/instances', body),
  getInstance:    (id: number) => api.get<unknown>(`/api/v1/workflow/instances/${id}`),
  executeTransition: (id: number, actionCode: string, executedById?: number, comments?: string) =>
    api.post<WorkflowInstanceDto>(`/api/v1/workflow/instances/${id}/transitions`, { actionCode, executedById, comments }),
  findInstance:   (targetModule: string, targetEntity: string, targetEntityId: number) =>
    api.get<WorkflowInstanceDto | null>(`/api/v1/workflow/instances/by-target?targetModule=${targetModule}&targetEntity=${targetEntity}&targetEntityId=${targetEntityId}`),
};

// ─── SCM ────────────────────────────────────────────────────────────────────
export interface ScmVendorDto {
  id: number; nameEn: string; nameAr?: string;
  category?: string; criticality?: string;
  country?: string; serviceType?: string;
  riskLevel?: string; status: string;
  onboardingStatus?: string;
  contactName?: string; contactEmail?: string; contactPhone?: string;
  registrationNumber?: string; website?: string; description?: string;
}
export interface ScmVendorRequest {
  nameEn: string; nameAr?: string;
  category?: string; criticality?: string;
  country?: string; serviceType?: string;
  riskLevel?: string; status?: string;
  contactName?: string; contactEmail?: string; contactPhone?: string;
  registrationNumber?: string; website?: string;
  description?: string;
}
export interface ScmOnboardingDto {
  id: number; vendorId: number;
  vendorNameEn?: string; vendorNameAr?: string;
  currentStage: string; status: string;
  completedStages?: string;
  dueDate?: string; assignee?: string;
  notes?: string;
}
export interface ScmOnboardingRequest {
  vendorId: number; currentStage?: string;
  status?: string; assignedToId?: number | null;
  dueDate?: string; notes?: string;
}
export interface ScmAssessmentDto {
  id: number; vendorId: number;
  vendorNameEn?: string; vendorNameAr?: string;
  assessmentType?: string;
  assessmentDate?: string; nextAssessmentDate?: string;
  overallScore?: number; riskRating?: string;
  status: string; findings?: string; recommendations?: string;
  escalatedToRisk?: boolean;
}
export interface ScmAssessmentRequest {
  vendorId: number; assessmentType?: string;
  assessmentDate?: string; nextAssessmentDate?: string;
  overallScore?: number | null; riskRating?: string;
  status?: string; assessorId?: number | null;
  findings?: string; recommendations?: string;
}
export interface ScmDependencyDto {
  id: number; serviceNameEn: string; serviceNameAr?: string;
  assetName?: string; vendorId?: number; vendorNameEn?: string;
  dependencyType?: string; dependencyLevel?: string;
  hasAlternative?: boolean; alternativeVendorId?: number; alternativeVendorNameEn?: string;
  status?: string; impactAnalysis?: string;
}
export interface ScmDependencyRequest {
  serviceNameEn: string; serviceNameAr?: string;
  assetName?: string; vendorId?: number | null;
  dependencyType?: string; dependencyLevel?: string;
  hasAlternative?: boolean; alternativeVendorId?: number | null;
  status?: string; impactAnalysis?: string;
}
export interface ScmIncidentDto {
  id: number; incidentCode?: string; vendorId: number;
  vendorNameEn?: string; vendorNameAr?: string;
  incidentType?: string; impact?: string; description?: string;
  slaBreached?: boolean; status?: string;
  backupPlanActivated?: boolean; capaCreated?: boolean; escalatedToManagement?: boolean;
  reportedAt?: string; resolvedAt?: string; resolutionNotes?: string;
}
export interface ScmIncidentRequest {
  vendorId: number; incidentType?: string;
  impact?: string; description?: string;
  slaBreached?: boolean; assignedToId?: number | null;
}
export interface ScmContractDto {
  id: number; vendorId: number;
  vendorNameEn?: string;
  contractNumber: string; titleEn: string; titleAr?: string;
  contractType?: string;
  startDate?: string; endDate?: string;
  autoRenewal?: boolean; contractValue?: number;
  currency?: string; status?: string; notes?: string;
}
export interface ScmContractRequest {
  vendorId: number; contractNumber?: string;
  titleEn: string; titleAr?: string;
  contractType?: string; startDate?: string; endDate?: string;
  autoRenewal?: boolean; contractValue?: number | null;
  currency?: string; status?: string; notes?: string;
}
export interface ScmStats {
  totalVendors: number; criticalVendors: number; activeVendors: number;
  pendingOnboarding: number; totalDependencies: number; highDependencies: number;
  openIncidents: number; slaBreaches: number;
  activeContracts: number; expiringContracts: number;
}
export const scmApi = {
  // Vendors
  vendors:        (page = 0, size = 50) => api.get<PageResponse<ScmVendorDto>>(`/api/v1/scm/vendors${pageQ(page, size)}`),
  allVendors:     () => api.get<ScmVendorDto[]>('/api/v1/scm/vendors/all'),
  createVendor:   (body: ScmVendorRequest) => api.post<ScmVendorDto>('/api/v1/scm/vendors', body),
  updateVendor:   (id: number, body: ScmVendorRequest) => api.put<ScmVendorDto>(`/api/v1/scm/vendors/${id}`, body),
  deleteVendor:   (id: number) => api.delete<void>(`/api/v1/scm/vendors/${id}`),
  // Onboarding
  onboarding:        (page = 0, size = 50) => api.get<PageResponse<ScmOnboardingDto>>(`/api/v1/scm/onboarding${pageQ(page, size)}`),
  startOnboarding:   (body: ScmOnboardingRequest) => api.post<ScmOnboardingDto>('/api/v1/scm/onboarding', body),
  advanceOnboarding: (id: number) => api.post<ScmOnboardingDto>(`/api/v1/scm/onboarding/${id}/advance`),
  approveOnboarding: (id: number) => api.post<ScmOnboardingDto>(`/api/v1/scm/onboarding/${id}/approve`),
  rejectOnboarding:  (id: number, reason: string) => api.post<ScmOnboardingDto>(`/api/v1/scm/onboarding/${id}/reject`, { reason }),
  // Assessments
  assessments:        (page = 0, size = 50) => api.get<PageResponse<ScmAssessmentDto>>(`/api/v1/scm/assessments${pageQ(page, size)}`),
  createAssessment:   (body: ScmAssessmentRequest) => api.post<ScmAssessmentDto>('/api/v1/scm/assessments', body),
  updateAssessment:   (id: number, body: ScmAssessmentRequest) => api.put<ScmAssessmentDto>(`/api/v1/scm/assessments/${id}`, body),
  escalateAssessment: (id: number) => api.post<ScmAssessmentDto>(`/api/v1/scm/assessments/${id}/escalate`),
  // Dependencies
  dependencies:       (page = 0, size = 50) => api.get<PageResponse<ScmDependencyDto>>(`/api/v1/scm/dependencies${pageQ(page, size)}`),
  createDependency:   (body: ScmDependencyRequest) => api.post<ScmDependencyDto>('/api/v1/scm/dependencies', body),
  updateDependency:   (id: number, body: ScmDependencyRequest) => api.put<ScmDependencyDto>(`/api/v1/scm/dependencies/${id}`, body),
  deleteDependency:   (id: number) => api.delete<void>(`/api/v1/scm/dependencies/${id}`),
  // Incidents (disruptions)
  incidents:        (page = 0, size = 50) => api.get<PageResponse<ScmIncidentDto>>(`/api/v1/scm/incidents${pageQ(page, size)}`),
  createIncident:   (body: ScmIncidentRequest) => api.post<ScmIncidentDto>('/api/v1/scm/incidents', body),
  activateBackup:   (id: number) => api.post<ScmIncidentDto>(`/api/v1/scm/incidents/${id}/activate-backup`),
  createIncidentCapa: (id: number) => api.post<ScmIncidentDto>(`/api/v1/scm/incidents/${id}/create-capa`),
  escalateIncident: (id: number) => api.post<ScmIncidentDto>(`/api/v1/scm/incidents/${id}/escalate`),
  resolveIncident:  (id: number, notes: string) => api.post<ScmIncidentDto>(`/api/v1/scm/incidents/${id}/resolve`, { notes }),
  // Contracts
  contracts:       (page = 0, size = 50) => api.get<PageResponse<ScmContractDto>>(`/api/v1/scm/contracts${pageQ(page, size)}`),
  createContract:  (body: ScmContractRequest) => api.post<ScmContractDto>('/api/v1/scm/contracts', body),
  updateContract:  (id: number, body: ScmContractRequest) => api.put<ScmContractDto>(`/api/v1/scm/contracts/${id}`, body),
  // Stats
  stats:         () => api.get<ScmStats>('/api/v1/scm/stats'),
};

// ─── Admin ──────────────────────────────────────────────────────────────────
export interface AdminUserDto {
  id: number; username: string; email: string;
  fullNameEn?: string; fullNameAr?: string;
  isActive: boolean; isLocked?: boolean;
  roles: string[]; createdAt?: string; lastLoginAt?: string;
}
export interface AdminUserRequest {
  username: string; email: string;
  fullNameEn?: string; fullNameAr?: string;
  password?: string;
  roles?: string[];
  isActive?: boolean;
}
export interface AdminRoleDto {
  id: number; roleName: string;
  displayNameEn?: string; displayNameAr?: string;
  isSystemRole?: boolean; isActive?: boolean;
}
export const adminApi = {
  users:         (page = 0, size = 50) => api.get<PageResponse<AdminUserDto>>(`/api/v1/admin/users${pageQ(page, size)}`),
  createUser:    (body: AdminUserRequest) => api.post<AdminUserDto>('/api/v1/admin/users', body),
  updateUser:    (id: number, body: AdminUserRequest) => api.put<AdminUserDto>(`/api/v1/admin/users/${id}`, body),
  patchUser:     (id: number, body: Partial<AdminUserRequest>) => api.patch<AdminUserDto>(`/api/v1/admin/users/${id}`, body),
  deleteUser:    (id: number) => api.delete<void>(`/api/v1/admin/users/${id}`),
  roles:         () => api.get<AdminRoleDto[]>('/api/v1/admin/roles'),
};
