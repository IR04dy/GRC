import { clearSession, getAccessToken, setSession, type UserInfo } from './auth';

// VITE_API_BASE is empty by default → calls go to /api/* on the same origin and Vite proxies
// to http://localhost:8084. Set VITE_API_BASE in .env.local to bypass the proxy.
const API_BASE = (import.meta.env.VITE_API_BASE ?? '').replace(/\/$/, '');

export class ApiError extends Error {
  constructor(public status: number, message: string, public body?: unknown) {
    super(message);
  }
}

/**
 * Human-readable message from an RFC-7807 problem body. Bean-validation 400s
 * carry the per-field reasons in `.errors` and NO `.detail` — without folding
 * those in, every required-field miss toasts a bare "Validation Failed" and
 * the button looks broken. Now: "Validation Failed — displayNameAr: Field
 * must not be blank".
 */
function problemMessage(parsed: unknown, fallback: string): string {
  const p = (parsed as {
    detail?: string; title?: string; message?: string; errors?: Record<string, string>;
  }) ?? {};
  let msg = p.detail ?? p.title ?? p.message ?? fallback;
  if (p.errors && typeof p.errors === 'object') {
    const fields = Object.entries(p.errors).map(([f, m]) => `${f}: ${m}`).join('; ');
    if (fields) msg = `${msg} — ${fields}`;
  }
  return msg;
}

interface FetchOpts extends Omit<RequestInit, 'body'> {
  body?: unknown;
  /** Skip Authorization header even if a token exists. */
  anonymous?: boolean;
  /** Internal — set when we already retried, prevents infinite recursion. */
  _retried?: boolean;
}

// ─── Transparent token refresh ─────────────────────────────────────────────
//
// Why: the access token is short-lived (1 h dev / 15 min prod) but the
// refresh token sits in an HttpOnly cookie good for 7 days. Before this
// change, the moment the access token expired EVERY page started showing
// "Failed to load …" toasts forever — the only recovery was to navigate
// back to /login manually.
//
// New flow: when a request comes back 401, we transparently call
// /api/auth/refresh (which mints a new access token from the refresh
// cookie), store it, and retry the original request once. If the refresh
// itself returns 401 the refresh token is also expired — at that point we
// clear the session and bounce to /login.
//
// Concurrency: many pages fire fetches in parallel. If 8 of them hit 401
// at the same time we MUST NOT call /refresh 8 times — that would rotate
// the refresh cookie 8 times and leave 7 of those rotations stranded.
// `inflightRefresh` deduplicates: every caller awaits the same promise.

let inflightRefresh: Promise<string | null> | null = null;

async function performRefresh(): Promise<string | null> {
  try {
    const res = await fetch(`${API_BASE}/api/auth/refresh`, {
      method: 'POST',
      credentials: 'include',  // sends the HttpOnly refresh cookie
      headers: { 'Accept': 'application/json' },
    });
    if (!res.ok) return null;
    const body = await res.json().catch(() => null) as { accessToken?: string } | null;
    const newToken = body?.accessToken;
    if (!newToken) return null;
    sessionStorage.setItem('grc.accessToken', newToken);
    return newToken;
  } catch {
    return null;
  }
}

function refreshAccessToken(): Promise<string | null> {
  if (!inflightRefresh) {
    inflightRefresh = performRefresh().finally(() => { inflightRefresh = null; });
  }
  return inflightRefresh;
}

/** Last-resort bounce when the refresh cookie is also dead. */
function redirectToLogin(): void {
  clearSession();
  // Don't trample a deliberate visit to /login itself.
  if (typeof window !== 'undefined' && !window.location.pathname.startsWith('/login')) {
    const next = window.location.pathname + window.location.search;
    window.location.replace(`/login?next=${encodeURIComponent(next)}`);
  }
}

/**
 * A path segment that is a negative integer. No id this platform issues can look like this —
 * every one is a SQL Server IDENTITY or a UUID. Anchored to a whole segment so a query value
 * never matches; the query string is stripped before the test regardless.
 */
const PLACEHOLDER_ID_SEGMENT = /\/-\d+(?=\/|$)/;

/**
 * F2-171/F2-172 — no endpoint in this platform keys on a negative id, so a negative id in a
 * request path is a client-side bug, not a record.
 *
 * <b>What this was for, and why it is still here after the cause was removed.</b> F2-171
 * added it because intake promotion merged client-only rows into eleven registers and those
 * rows rendered the same live governance actions as server rows. F2-172 removed that
 * merge — promotion writes a real record now, and nothing in the app mints a synthetic id
 * any more, so the population this guard was written for is empty.
 *
 * It stays because it is an invariant rather than a ceiling. Every id this platform issues
 * is a SQL Server IDENTITY (starts at 1, counts up) or a UUID, so "a path segment that is a
 * negative integer" is unreachable by construction and free to assert. A ceiling tolerating
 * a population would have had to go with the population; a total assertion at the single
 * choke point every call passes through costs one regex and turns the next accidental
 * placeholder scheme into a named error instead of a 404 nobody can explain — which is
 * precisely how F2-171's harms presented.
 *
 * Thrown, not swallowed: pages already funnel `ApiError` into `problemMessage()` toasts, so
 * the user gets a sentence naming the cause instead of a silent no-op. Status 0 marks it as
 * client-side — no HTTP request was made.
 */
function assertNotPlaceholder(path: string): void {
  if (PLACEHOLDER_ID_SEGMENT.test(path.split('?')[0])) {
    throw new ApiError(0,
      'This action addressed a record id that no server ever issues (a negative number), so '
      + 'no request was sent. That is a bug in the screen that produced it, not something '
      + 'you can retry.');
  }
}

async function request<T>(path: string, opts: FetchOpts = {}): Promise<T> {
  assertNotPlaceholder(path);
  const { body, anonymous, headers, _retried, ...rest } = opts;
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
    // Backend uses Spring's `ProblemDetail.forStatusAndDetail(...)` (RFC-7807)
    // which serialises the human-readable string in `.detail` — NOT `.message`.
    // The legacy `.message` is kept as a fallback for any endpoint that still
    // returns the old shape. Without this, every backend error ApiError.message
    // fell back to res.statusText ("Internal Server Error" etc.) so the toast
    // never showed the actual reason. apiError.ts's getErrorDisplayMessage()
    // already extracted .detail correctly for callers that used it — but
    // pages that did `toast.danger(e.message)` directly missed it.
    const msg = problemMessage(parsed, res.statusText || 'Request failed');

    // 401 handling: try once to refresh + retry. Skip if:
    //   - this IS the refresh / login / logout endpoint (would loop)
    //   - we already retried once (avoid recursion)
    //   - this is an anonymous request (no token to refresh)
    const isAuthEndpoint = path.startsWith('/api/auth/');
    if (res.status === 401 && !_retried && !anonymous && !isAuthEndpoint) {
      const newToken = await refreshAccessToken();
      if (newToken) {
        // Retry once with the fresh token. Recursion is bounded by _retried.
        return request<T>(path, { ...opts, _retried: true });
      }
      // Refresh failed — refresh cookie itself expired. Bounce to /login.
      redirectToLogin();
    } else if (res.status === 401 && !isAuthEndpoint && !anonymous) {
      // Already retried once and still 401 → give up.
      // Skip for anonymous requests: those are public endpoints (e.g. DSR
      // portal, trust-center); a 401 there means a route is misconfigured,
      // not that the user's session expired.
      redirectToLogin();
    }

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
export async function downloadFile(
  path: string,
  fallbackName: string,
  opts?: { method?: string; jsonBody?: unknown },
): Promise<void> {
  // Wrap in the same refresh-on-401 path as request(). We can't call
  // request() directly because we need the raw Response (blob + headers).
  // …which is exactly why the F2-171 check has to be repeated: this is a second
  // network entry point, not a caller of the first.
  assertNotPlaceholder(path);
  const hasBody = opts?.jsonBody !== undefined;
  const attempt = async (token: string | null): Promise<Response> => {
    return fetch(`${API_BASE}${path}`, {
      method: opts?.method ?? 'GET',
      headers: {
        ...(token ? { 'Authorization': `Bearer ${token}` } : {}),
        ...(hasBody ? { 'Content-Type': 'application/json' } : {}),
      },
      credentials: 'include',
      ...(hasBody ? { body: JSON.stringify(opts!.jsonBody) } : {}),
    });
  };

  let res = await attempt(getAccessToken());
  if (res.status === 401) {
    const newToken = await refreshAccessToken();
    if (newToken) {
      res = await attempt(newToken);
    } else {
      redirectToLogin();
    }
  }
  if (!res.ok) {
    // Match request()'s ProblemDetail handling: download endpoints return
    // binary on success but JSON ProblemDetail on failure (Spring's
    // standard error response). Without this, a 503 "Module not yet
    // bootstrapped" surfaced as a generic "Service Unavailable" toast.
    let parsed: unknown = undefined;
    try {
      const text = await res.text();
      if (text) parsed = JSON.parse(text);
    } catch { /* body wasn't JSON; carry on with statusText only */ }
    const msg = problemMessage(parsed, res.statusText || 'Download failed');
    throw new ApiError(res.status, msg, parsed);
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

/**
 * Upload a single binary file via multipart/form-data (e.g. attach a document
 * to an evidence item). The browser sets the multipart Content-Type + boundary,
 * so we must NOT set Content-Type ourselves. Mirrors downloadFile's 401-refresh.
 */
export async function uploadFile<T>(path: string, file: File, field = 'file'): Promise<T> {
  assertNotPlaceholder(path);   // third network entry point — see request()
  const attempt = async (token: string | null): Promise<Response> => {
    const fd = new FormData();
    fd.append(field, file);
    return fetch(`${API_BASE}${path}`, {
      method: 'POST',
      headers: { ...(token ? { 'Authorization': `Bearer ${token}` } : {}) },
      credentials: 'include',
      body: fd,
    });
  };

  let res = await attempt(getAccessToken());
  if (res.status === 401) {
    const newToken = await refreshAccessToken();
    if (newToken) res = await attempt(newToken);
    else redirectToLogin();
  }

  const text = await res.text();
  let parsed: unknown = undefined;
  if (text) { try { parsed = JSON.parse(text); } catch { parsed = text; } }
  if (!res.ok) {
    const msg = problemMessage(parsed, res.statusText || 'Upload failed');
    throw new ApiError(res.status, msg, parsed);
  }
  return parsed as T;
}

// ─── Ethics & Compliance — Cases (whistleblower + investigations) ──────────
export interface EthicsCaseDto {
  id: number; organisationId?: number;
  caseCode?: string;
  titleEn: string; titleAr?: string;
  category: string;
  severity: string;
  intakeChannel: string;
  reporterName?: string; reporterEmail?: string; reporterEmployeeId?: number;
  isAnonymous?: boolean;
  subjectSummary?: string;
  description: string;
  location?: string;
  dateOfIncident?: string;
  frameworkId?: number;
  clauseRef?: string;
  status: string;
  triageDecision?: string;
  investigatorName?: string;
  committeeName?: string;
  triagedAt?: string;
  investigationStarted?: string;
  investigationEnded?: string;
  outcome?: string;
  outcomeSummary?: string;
  regulatorNotified?: boolean;
  regulatorReference?: string;
  retaliationCheckAt?: string;
  retaliationObserved?: boolean;
  closureDate?: string;
  createdAt?: string;
}
export interface CaseActionDto {
  id?: number; caseId?: number;
  actionType: string;
  title: string;
  description?: string;
  /** Server-owned: the column is `insertable = false`, so the database supplies it. */
  actionAt?: string;
  /** Who did it — server-owned since F2-248, taken from the authenticated principal. */
  actorName?: string;
  /**
   * The actor's user id.
   *
   * F2-248 — declared here for the same reason as `ChangeCommentDto.authorId`: the wire carried it
   * and this type did not, so the timeline could not distinguish an attributed action from a
   * free-text office label. `EthicsPage` sent no actor at all, so every action recorded through the
   * platform's own screen stored `actor_name = NULL` and `actor_id = NULL` — the action log of a
   * misconduct investigation saying what happened and when, and not who did it.
   */
  actorId?: number;
  isConfidential?: boolean;
}
/**
 * F2-248 — what a caller may send when recording an ethics case action.
 *
 * No actor: `addAction` used to bind the entity and never touch `actorName`/`actorId`, so both were
 * stored exactly as they arrived. The server now takes both from the principal.
 */
export interface CaseActionCreateDto {
  actionType: string;
  title: string;
  description?: string;
  isConfidential?: boolean;
}
export interface CaseLinkDto {
  id?: number; caseId?: number;
  entityType: string; entityId: number;
  linkType: string;
  notes?: string;
  createdAt?: string;
}
export interface EthicsAnalyticsDto {
  total: number;
  byStatus: Record<string, number>;
  byCategory: Record<string, number>;
  // `open` is the COMPLEMENT of the declared terminal set (EthicsCaseLifecycle.TERMINAL), so a
  // status nobody has heard of counts as open. It was an inclusion list of five states and
  // reported 10 of 26 (F2-290).
  open: number; closed: number; substantiated: number;
  // How many rows carry a status / category the platform does not declare, and which therefore
  // appear in NO bucket above. `ethics.cases` has no CHECK on either column: 11 and 6 rows in
  // both databases today. Disclosed rather than dropped — the bars would otherwise not sum to
  // `total` with nothing on screen saying why.
  undeclaredStatus: number; undeclaredCategory: number;
}
export interface LinkedEthicsCaseView {
  linkId: number; linkType: string;
  caseId: number; caseCode?: string;
  titleEn: string; titleAr?: string;
  status: string; severity: string; category: string;
}
export const ethicsCaseApi = {
  list:        (qs: { status?: string; category?: string; severity?: string; page?: number; size?: number } = {}) => {
    const p = new URLSearchParams();
    if (qs.status)   p.set('status', qs.status);
    if (qs.category) p.set('category', qs.category);
    if (qs.severity) p.set('severity', qs.severity);
    p.set('page', String(qs.page ?? 0));
    p.set('size', String(qs.size ?? 100));
    return api.get<PageResponse<EthicsCaseDto>>(`/api/v1/ethics/cases?${p.toString()}`);
  },
  get:        (id: number) => api.get<EthicsCaseDto>(`/api/v1/ethics/cases/${id}`),
  create:     (body: Partial<EthicsCaseDto>) => api.post<EthicsCaseDto>('/api/v1/ethics/cases', body),
  update:     (id: number, body: Partial<EthicsCaseDto>) => api.put<EthicsCaseDto>(`/api/v1/ethics/cases/${id}`, body),
  remove:     (id: number) => api.delete<void>(`/api/v1/ethics/cases/${id}`),
  transition: (id: number, body: Record<string, unknown>) => api.post<EthicsCaseDto>(`/api/v1/ethics/cases/${id}/transition`, body),

  actions:    (id: number) => api.get<CaseActionDto[]>(`/api/v1/ethics/cases/${id}/actions`),
  addAction:  (id: number, body: CaseActionCreateDto) => api.post<CaseActionDto>(`/api/v1/ethics/cases/${id}/actions`, body),

  links:      (id: number) => api.get<CaseLinkDto[]>(`/api/v1/ethics/cases/${id}/links`),
  addLink:    (id: number, body: Partial<CaseLinkDto>) => api.post<CaseLinkDto>(`/api/v1/ethics/cases/${id}/links`, body),
  deleteLink: (linkId: number) => api.delete<void>(`/api/v1/ethics/cases/links/${linkId}`),
  linksForEntity: (entityType: string, entityId: number) =>
                  api.get<LinkedEthicsCaseView[]>(`/api/v1/ethics/cases/links/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),

  analytics:  () => api.get<EthicsAnalyticsDto>('/api/v1/ethics/cases/analytics'),
};

// ─── Ethics — Disclosures (CoI / Gifts / Hospitality / Outside Business) ──
export interface DisclosureDto {
  id: number; organisationId?: number;
  disclosureCode?: string;
  disclosureType: string;
  titleEn: string; titleAr?: string;
  description: string;
  discloserName: string;
  discloserEmail?: string;
  discloserDepartment?: string;
  discloserRole?: string;
  counterpartyName?: string;
  counterpartyRelationship?: string;
  monetaryValue?: number;
  currency?: string;
  occurredOn?: string;
  status: string;
  decision?: string;
  decisionNotes?: string;
  decidedByName?: string;
  decidedAt?: string;
  frameworkId?: number;
  clauseRef?: string;
  createdAt?: string;
}
export interface DisclosureAnalyticsDto {
  total: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
}
export const disclosureApi = {
  list: (qs: { type?: string; status?: string; page?: number; size?: number } = {}) => {
    const p = new URLSearchParams();
    if (qs.type)   p.set('type', qs.type);
    if (qs.status) p.set('status', qs.status);
    p.set('page', String(qs.page ?? 0));
    p.set('size', String(qs.size ?? 100));
    return api.get<PageResponse<DisclosureDto>>(`/api/v1/ethics/disclosures?${p.toString()}`);
  },
  get:        (id: number) => api.get<DisclosureDto>(`/api/v1/ethics/disclosures/${id}`),
  create:     (body: Partial<DisclosureDto>) => api.post<DisclosureDto>('/api/v1/ethics/disclosures', body),
  update:     (id: number, body: Partial<DisclosureDto>) => api.put<DisclosureDto>(`/api/v1/ethics/disclosures/${id}`, body),
  remove:     (id: number) => api.delete<void>(`/api/v1/ethics/disclosures/${id}`),
  transition: (id: number, body: Record<string, unknown>) => api.post<DisclosureDto>(`/api/v1/ethics/disclosures/${id}/transition`, body),
  analytics:  () => api.get<DisclosureAnalyticsDto>('/api/v1/ethics/disclosures/analytics'),
};

// ─── Ethics — Sanctions / PEP / CDD / EDD (KYC) ────────────────────────────
export interface KycSubjectDto {
  id: number; organisationId?: number;
  subjectCode?: string;
  subjectType: string;
  fullName: string; fullNameAr?: string;
  nationality?: string;
  countryOfResidence?: string;
  dateOfBirth?: string;
  nationalId?: string;
  commercialRegistration?: string;
  subjectLinkType?: string; subjectLinkId?: number;
  riskRating: string;
  isPep?: boolean; isSanctioned?: boolean;
  cddLevel: string;
  cddCompletedAt?: string;
  eddRequired?: boolean;
  eddCompletedAt?: string;
  lastScreenedAt?: string;
  nextReviewDate?: string;
  status: string;
  frameworkId?: number;
  notes?: string;
  createdAt?: string;
}
export interface ScreeningHitDto {
  id?: number; subjectId?: number;
  listSource: string;
  listEntryRef?: string;
  matchScore: number;
  matchSummary?: string;
  screenedAt?: string;
  disposition: string;
  dispositionNotes?: string;
  decidedByName?: string;
  decidedAt?: string;
  sarFiled?: boolean;
  sarReference?: string;
}
export interface KycAnalyticsDto {
  total: number; approved: number; rejected: number;
  pending: number; underReview: number;
  highRisk: number; prohibited: number;
  peps: number; sanctioned: number;
}
export const kycApi = {
  list: (qs: { type?: string; status?: string; risk?: string; page?: number; size?: number } = {}) => {
    const p = new URLSearchParams();
    if (qs.type)   p.set('type', qs.type);
    if (qs.status) p.set('status', qs.status);
    if (qs.risk)   p.set('risk', qs.risk);
    p.set('page', String(qs.page ?? 0));
    p.set('size', String(qs.size ?? 100));
    return api.get<PageResponse<KycSubjectDto>>(`/api/v1/ethics/kyc?${p.toString()}`);
  },
  get:        (id: number) => api.get<KycSubjectDto>(`/api/v1/ethics/kyc/${id}`),
  create:     (body: Partial<KycSubjectDto>) => api.post<KycSubjectDto>('/api/v1/ethics/kyc', body),
  update:     (id: number, body: Partial<KycSubjectDto>) => api.put<KycSubjectDto>(`/api/v1/ethics/kyc/${id}`, body),
  remove:     (id: number) => api.delete<void>(`/api/v1/ethics/kyc/${id}`),
  completeCdd:(id: number) => api.post<KycSubjectDto>(`/api/v1/ethics/kyc/${id}/cdd-complete`),
  completeEdd:(id: number) => api.post<KycSubjectDto>(`/api/v1/ethics/kyc/${id}/edd-complete`),
  approve:    (id: number) => api.post<KycSubjectDto>(`/api/v1/ethics/kyc/${id}/approve`),
  reject:     (id: number, reason: string) => api.post<KycSubjectDto>(`/api/v1/ethics/kyc/${id}/reject`, { reason }),
  runScreening:(id: number) => api.post<KycSubjectDto>(`/api/v1/ethics/kyc/${id}/screen`),
  hits:       (id: number) => api.get<ScreeningHitDto[]>(`/api/v1/ethics/kyc/${id}/hits`),
  recordHit:  (id: number, body: Partial<ScreeningHitDto>) => api.post<ScreeningHitDto>(`/api/v1/ethics/kyc/${id}/hits`, body),
  disposition:(hitId: number, body: { disposition: string; notes?: string; decidedBy?: string; sarFiled?: boolean; sarReference?: string }) =>
                api.post<ScreeningHitDto>(`/api/v1/ethics/kyc/hits/${hitId}/disposition`, body),
  analytics:  () => api.get<KycAnalyticsDto>('/api/v1/ethics/kyc/analytics'),
};

// ─── Ethics — AML transaction alerts ───────────────────────────────────────
export interface AmlAlertDto {
  id: number; organisationId?: number;
  alertCode?: string;
  alertType: string;
  severity: string;
  ruleId?: string; ruleName?: string;
  titleEn: string; titleAr?: string;
  description: string;
  subjectId?: number;
  // F2-277 — derived from ethics.kyc_subjects, which holds both halves on all 16 rows.
  subjectName?: string;
  subjectNameAr?: string;
  counterpartyName?: string;
  counterpartyCountry?: string;
  transactionCount?: number;
  aggregateAmount?: number;
  currency?: string;
  periodFrom?: string;
  periodTo?: string;
  firstDetectedAt?: string;
  status: string;
  analystName?: string;
  /**
   * F2-249 — the resolvable half of the analyst reference. NULL means the assignment names a
   * team or an external party rather than a platform user, and the register discloses that
   * rather than presenting a label as if it were a person.
   */
  analystId?: number;
  slaDueAt?: string;
  disposition?: string;
  dispositionNotes?: string;
  decidedAt?: string;
  decidedByName?: string;
  strFiled?: boolean;
  strReference?: string;
  strFiledAt?: string;
  frameworkId?: number;
  clauseRef?: string;
}
export interface AmlAnalyticsDto {
  total: number;
  byStatus: Record<string, number>;
  bySeverity: Record<string, number>;
  openAlerts: number;
  strFiled: number;
}
/**
 * F2-249 — who an AML alert is being assigned to. Mirrors the server's `AssignAnalystRequest`.
 *
 * The two are not interchangeable: an id names a platform user and the server derives the label
 * from it; a name records an assignment to a team or an external party and leaves the id NULL, so
 * the register can tell the two kinds apart instead of presenting both as a person.
 */
export interface AssignAnalystDto {
  analystUserId?: number;
  analystName?: string;
}

export const amlAlertApi = {
  list: (qs: { status?: string; type?: string; severity?: string; page?: number; size?: number } = {}) => {
    const p = new URLSearchParams();
    if (qs.status)   p.set('status', qs.status);
    if (qs.type)     p.set('type', qs.type);
    if (qs.severity) p.set('severity', qs.severity);
    p.set('page', String(qs.page ?? 0));
    p.set('size', String(qs.size ?? 100));
    return api.get<PageResponse<AmlAlertDto>>(`/api/v1/ethics/aml-alerts?${p.toString()}`);
  },
  get:        (id: number) => api.get<AmlAlertDto>(`/api/v1/ethics/aml-alerts/${id}`),
  create:     (body: Partial<AmlAlertDto>) => api.post<AmlAlertDto>('/api/v1/ethics/aml-alerts', body),
  update:     (id: number, body: Partial<AmlAlertDto>) => api.put<AmlAlertDto>(`/api/v1/ethics/aml-alerts/${id}`, body),
  remove:     (id: number) => api.delete<void>(`/api/v1/ethics/aml-alerts/${id}`),
  /**
   * F2-249 — exactly one of the two. `analystUserId` is a platform user, which the server
   * resolves in-tenant and whose label it then DERIVES; `analystName` records an assignment to a
   * team or an external party and leaves the id NULL. Sending a name that contradicts a supplied
   * id is refused, so the row can never describe two different assignees.
   */
  assign:     (id: number, body: AssignAnalystDto) => api.post<AmlAlertDto>(`/api/v1/ethics/aml-alerts/${id}/assign`, body),
  transition: (id: number, body: Record<string, unknown>) => api.post<AmlAlertDto>(`/api/v1/ethics/aml-alerts/${id}/transition`, body),
  analytics:  () => api.get<AmlAnalyticsDto>('/api/v1/ethics/aml-alerts/analytics'),
};

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
  implementationPlan?: string;   // CH-11
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
  // CH-11: structured post-implementation review (PIR)
  pirSummary?: string;
  pirObjectivesMet?: boolean;
  pirLessons?: string;
  pirFollowUp?: string;
  pirReviewedBy?: string;
  pirReviewedAt?: string;
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
/** S7e/CE-5 — a condition the approving authority attached to a change (V352). */
export interface ChangeConditionDto {
  id: number;
  changeRequestId: number;
  sortOrder?: number;
  textEn: string;
  textAr: string;
  imposedById?: number | null;
  imposedByName?: string | null;
  imposedAt?: string | null;
  ownerName?: string | null;
  dueAt?: string | null;
  /** OPEN blocks implementation. SATISFIED and WAIVED are deliberately distinct: collapsing them
   *  would make a governance breach indistinguishable from compliance in every report. */
  status: 'OPEN' | 'SATISFIED' | 'WAIVED';
  closedById?: number | null;
  closedByName?: string | null;
  closedAt?: string | null;
  closureNotes?: string | null;
}

/**
 * S8a/CE-9 — an evidence item as the change module sees it (the `EvidencePort.EvidenceRef` record).
 *
 * Deliberately a projection, not `EvidenceItemDto`: the change module has no business knowing the
 * subsystem's 30-odd columns, and the four fields that matter here are the ones prose could never
 * carry — `sha256`, `fileName`, the review `status`, and `verified`.
 */
export interface ChangeEvidenceRef {
  id: number;
  externalId?: string | null;
  titleEn?: string | null;
  titleAr?: string | null;
  evidenceType?: string | null;
  /** The subsystem's review state: DRAFT / PENDING_REVIEW / APPROVED / EXPIRED / … */
  status?: string | null;
  /** True only for APPROVED. A condition may be recorded satisfied only when something here is true. */
  verified: boolean;
  fileName?: string | null;
  sha256?: string | null;
}

/** What a caller may set. Mirrors the backend record — no status, no ids, no closing fields. */
export interface ChangeConditionRequest {
  textEn: string;
  textAr: string;
  ownerName?: string;
  dueAt?: string;
  sortOrder?: number;
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
  /**
   * Who wrote it — server-owned since F2-248, taken from the authenticated principal.
   *
   * `NOT NULL` in the column, so the wire always carries it.
   */
  authorName: string;
  /**
   * The author's user id.
   *
   * F2-248 — the wire has always carried this (the endpoint returns the entity) and this type
   * never declared it, which is F2-198's `crossBorderTransfer` shape: the register could not tell
   * an attributed row from a free-text label, so it showed both identically. Measured: NULL on
   * **45 of 45** dev rows and **46 of 46** prod rows, because nothing had ever written the column.
   * Rows created from F2-248 onwards carry it; the pre-existing ones never will, and the panel
   * discloses that difference rather than letting an unattributable row read as a tidy one.
   */
  authorId?: number;
  body: string;
  createdAt?: string;
}
/**
 * F2-248 — what a caller may send when adding a comment: the text, and nothing else.
 *
 * The author used to be a request field (`getUser()?.username ?? 'You'`), and the prod register
 * holds a row whose author is the literal string `'You'`. The server now takes both halves of the
 * attribution from the principal and the bound type has no field for either, so the forgery is not
 * expressible rather than merely rejected.
 */
export interface ChangeCommentCreateDto {
  body: string;
}
/**
 * S8b/CE-12 — one ITIL/ISO KPI, with the population it was computed over.
 *
 * `basis` answers *how much of the population is covered*; `provenance` answers *whether the covered
 * rows were measured or asserted*. They are independent: the CAB quorum-met rate is `COMPUTED` (2 of 2
 * held meetings recorded a basis) **and** `ASSERTED` (both bases were typed by a human, not derived
 * from the roster). Rendering only the value would present that as a passed control.
 */
export interface ChangeKpi {
  key: string;
  /** null when `basis` is NO_DATA or NOT_MEASURABLE — never render a 0 in its place. */
  value: number | null;
  unit: 'PCT' | 'DAYS' | 'COUNT' | 'RATIO_PER_MEETING';
  numerator?: number | null;
  /** What the value was divided by. For DAYS metrics this is the sample size. */
  denominator?: number | null;
  /** The population the metric ideally covers. Below `denominator` ⇒ the figure is partial. */
  population?: number | null;
  basis: 'COMPUTED' | 'COMPUTED_PARTIAL' | 'NO_DATA' | 'NOT_MEASURABLE';
  provenance?: 'MEASURED' | 'ASSERTED' | 'MIXED' | null;
  /** Stable code naming what is missing, translated by the FE. Set only when NOT_MEASURABLE. */
  blockedBy?: string | null;
}

export interface ChangeAnalyticsDto {
  total: number;
  byStatus: Record<string, number>;
  byType: Record<string, number>;
  byOutcome: Record<string, number>;
  /**
   * ⚠ F2-107: this divided by *rows that have an outcome* until S8b, so it rendered 100% in dev off
   * 2 rows out of 17. The denominator is now changes that reached a terminal status, and the two keys
   * below expose it — a rate whose denominator the caller cannot see is the defect, not the number.
   */
  successRatePct: number;
  successRateDenominator?: number;
  successRateOutcomesRecorded?: number;
  /** The full §12 pack, keyed by metric. Absent on a deployment without the KPI service. */
  kpis?: Record<string, ChangeKpi>;
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

// ─── CH-17: authorisation matrix + freeze windows + calendar + conflicts ───
export interface ChangeAuthMatrixCell {
  id: number; organisationId?: number;
  changeType: string; riskLevel: string;
  gate: string; minApprovals: number; requiredRoles?: string | null;
  /** S8e/CE-2 — opt-in per cell: this gate authorises only on a live pre-approved model. */
  requiresModel?: boolean;
  notesEn?: string; notesAr?: string;
}
// ─── S8e/CE-2: the change model catalogue ───────────────────────────────────
/**
 * A pre-approved procedure. `preApproved` alone means nothing — a model confers pre-approval only
 * while it is APPROVED and `authorisedUntil` is in the future, which is why the server never sends
 * a bare boolean and the UI never computes one.
 */
export interface ChangeModelDto {
  id: number; organisationId?: number;
  code: string; nameEn: string; nameAr: string;
  descriptionEn?: string | null; descriptionAr?: string | null;
  changeType: string; riskDefault?: string | null; gateOverride?: string | null;
  preApproved: boolean;
  authorisedByName?: string | null; authorisedAt?: string | null; authorisedUntil?: string | null;
  standardSteps?: string | null; rollbackTemplate?: string | null; testTemplate?: string | null;
  maxLeadTimeHours?: number | null; reviewCadence?: string | null;
  status: 'DRAFT' | 'APPROVED' | 'SUSPENDED' | 'RETIRED';
  createdBy?: string | null; createdAt?: string | null;
}
/** S8g/CE-8 — one entry on the forward schedule of change. */
export interface ScheduleEntryDto {
  changeId: number;
  changeCode?: string | null;
  titleEn?: string | null; titleAr?: string | null;
  changeType: string; riskLevel: string; status: string;
  windowStart?: string | null; windowEnd?: string | null;
  outageExpected: boolean;
  outageMinutes?: number | null;
  servicesAffected?: string | null;
  /** ⚠ The complete declaration — flag AND duration AND named services. Not the raw flag. */
  outageDeclared: boolean;
  /** Set when the entry sits inside an active freeze window that does not exempt it. */
  freezeCode?: string | null;
}
/** A schedule collision: two changes overlapping, or a change inside a freeze window. */
export interface ScheduleCollisionDto {
  reasonCode: 'WINDOW_OVERLAP' | 'OUTAGE_OVERLAP' | 'INSIDE_FREEZE_WINDOW';
  changeId: number; changeCode?: string | null;
  otherChangeId?: number | null; otherChangeCode?: string | null;
  freezeCode?: string | null; freezeNameEn?: string | null; freezeNameAr?: string | null;
  overlapFrom?: string | null; overlapTo?: string | null;
  sameCategory: boolean;
}
/** A publication of the forward schedule, without its snapshot payload. */
export interface SchedulePublicationDto {
  id: number;
  windowFrom: string; windowTo: string;
  entryCount: number; outageCount: number; freezeCount: number;
  contentSha256: string;
  publishedByName: string; publishedAt: string;
  notes?: string | null;
}
/** The forward schedule, its collisions, and — deliberately — what it could not see. */
export interface ForwardScheduleDto {
  from: string; to: string;
  entries: ScheduleEntryDto[];
  collisions: ScheduleCollisionDto[];
  outageCount: number;
  totalOutageMinutes: number;
  freezeCount: number;
  /** ⚠ Forward-status changes with no window at all — invisible to collision detection. */
  unscheduledCount: number;
  collisionsByReason: Record<string, number>;
  /** null when the schedule has never been published — different from "published but stale". */
  latestPublication?: SchedulePublicationDto | null;
  latestPublicationStale: boolean;
}

/**
 * S8h/CE-10 — an addressee the workflow engine can actually notify.
 *
 * <p>`users` is how many active people the engine's own fan-out would reach. Only addressees with
 * `users > 0` are ever returned, because a picker that offers an empty department is a picker that
 * helps you route work into a void.
 */
export interface EngineAddresseeDto {
  type: string;              // ROLE | DEPARTMENT | COMMITTEE | USER
  id?: number | null;        // null for ROLE
  key?: string | null;       // role name, or department/committee code
  label: string;
  users: number;
}

/** One engine request anchored to a change or a change task. */
export interface EngineRequestRefDto {
  id: number;
  code: string;
  requestType: string;
  status: string;
  titleEn?: string | null;
  titleAr?: string | null;
  dueDate?: string | null;
  slaHours?: number | null;
  priority?: string | null;
  /** NONE | ON_TRACK | DUE_TODAY | OVERDUE — the engine's own countdown, not recomputed here. */
  countdown?: string | null;
  recipientCategory?: string | null;
  recipientCount: number;
  subjectType: string;       // ChangeRequest | ChangeTask
  subjectId: number;
}

export interface RaiseEngineRequestBody {
  requestType: string;
  titleEn?: string;
  titleAr?: string;
  description?: string;
  addresseeType: string;
  addresseeId?: number | null;
  addresseeKey?: string | null;
  addresseeLabel?: string | null;
  dueDate?: string;
  slaHours?: number;
  priority?: string;
  escalationRole?: string;
}

/** One distinct task-owner label and whether the engine could reach it. */
export interface OwnerVerdictDto {
  label: string;
  matchedType?: string | null;   // null when the label matches nothing at all
  matchedLabel?: string | null;
  reachable: boolean;
  reason?: string | null;
  users: number;
  openTasks: number;
  overdueTasks: number;
}

/**
 * Which open change tasks have an owner the platform can notify.
 *
 * ⚠ `reachable`/`unreachable` count TASKS, not labels: one unreachable label carrying nine overdue
 * tasks is nine people who were never told. And `engineAbsent` must be honoured — with no engine
 * every verdict is unknown, so rendering `unreachable: 0` as good news would be reporting an absence
 * as a measurement.
 */
export interface TaskAddressabilityDto {
  scope: string;
  openTasks: number;
  overdueOpen: number;
  /** Open tasks with no owner label at all — not fixable by appointing anybody. */
  unnamed: number;
  reachable: number;
  unreachable: number;
  byReason: Record<string, number>;
  owners: OwnerVerdictDto[];
  engineAbsent: boolean;
}

/**
 * CE-13 — one referral in a committee's open queue, with what the change module knows of its subject.
 *
 * ⚠ `subjectState` is the whole point. `PRESENT` is a real queue item; `SUBJECT_NOT_STATED` and
 * `SUBJECT_NOT_FOUND` are rows the committee can never table, and they need a different remedy —
 * clear them, not decide them. Dev's only two open CAB referrals are both `SUBJECT_NOT_FOUND`.
 */
export interface CabQueueRowDto {
  referralId: number;
  changeId?: number;
  changeCode?: string;
  subjectEn?: string;
  subjectAr?: string;
  referralStatus?: string;
  mode?: string;
  meetingId?: number;
  resolutionId?: number;
  subjectState: string;
  /** Null exactly when the subject could not be found — never a fabricated status. */
  changeStatus?: string;
}

/** CE-13 — a change whose committee decision is still ahead, and why it is not in front of them. */
export interface CabPendingDto {
  id: number;
  changeCode?: string;
  titleEn: string;
  titleAr?: string;
  changeType?: string;
  riskLevel?: string;
  status: string;
  gate: string;
  minApprovals?: number;
  scheduledStart?: string;
  reason: string;
}

/** CE-13 — the sitting a queue will be heard at. Identity only: no quorum, minutes or tally. */
export interface CabSittingDto {
  id: number;
  code?: string;
  titleEn?: string;
  titleAr?: string;
  scheduledAt?: string;
  status?: string;
  agendaItems: number;
  /** Agenda lines this module tabled. Empty with a non-empty queue = the agenda needs rebuilding. */
  tabledSubjects: string[];
}

/** CE-13 — one gate (CAB or ECAB): which committee holds it, when it sits, what is in front of it. */
export interface CabGateDto {
  gate: string;
  committeeId?: number;
  committeeNameEn?: string;
  committeeNameAr?: string;
  committeeStatus?: string;
  /** A live committee is named, so a change can be referred here. */
  mapped: boolean;
  /**
   * The gate will actually carry a decision (A17-D2 additionally requires an active voting seat).
   *
   * ⚠ Distinct from `mapped`, and they differ in dev right now: the CAB is correctly mapped to a
   * committee with zero voting seats, so referral and tabling work while the approval is refused
   * (F2-96). One flag would send an operator to re-map a mapping that is already right.
   */
  canDecide: boolean;
  /** A17-D2's own fault code when the gate cannot decide. Rendered verbatim — never re-worded. */
  fault?: string;
  sitting?: CabSittingDto;
  queue: CabQueueRowDto[];
  awaiting: CabPendingDto[];
}

/**
 * CE-13 — the CAB queue.
 *
 * ⚠ `engineAbsent` must be honoured: with no committee engine every gate is unresolvable, and an
 * empty queue rendered as good news would report a clean CAB that nobody checked (S8h's rule).
 */
export interface CabQueueDto {
  gates: CabGateDto[];
  awaiting: number;
  notReferred: number;
  notTabled: number;
  orphans: number;
  tabled: number;
  byReason: Record<string, number>;
  engineAbsent: boolean;
}

/**
 * S8f/CE-11 — one reconciliation failure between the authorisation a change required and the
 * authorisation the platform can show it received.
 *
 * ⚠ `UNATTRIBUTED_AUTHORISATION` is **not** an unauthorised change: the votes satisfy the cell and
 * only `cab_decision_at` is missing. Six of dev's eleven findings are that code, so a UI that styled
 * them like the rest would show eight breaches where three exist.
 */
export interface UnauthorisedFindingDto {
  changeId: number;
  changeCode?: string | null;
  titleEn?: string | null; titleAr?: string | null;
  changeType: string; riskLevel: string; status: string;
  reasonCode: 'NO_AUTHORISATION_RECORD' | 'INSUFFICIENT_APPROVALS' | 'UNATTRIBUTED_AUTHORISATION'
            | 'NO_AUTHORISATION_POLICY' | 'EXPIRED_MODEL_APPLIED' | 'RATIFICATION_BREACHED';
  severity: 'HIGH' | 'MEDIUM';
  /** Whether the change is live. The difference between a breach and a catch. */
  effective: boolean;
  gateRequired?: string | null;
  minApprovalsRequired?: number | null;
  approvalsRecorded?: number | null;
  cabDecisionStamped: boolean;
  ratificationState?: string | null;
  dispositionState?: 'ACCEPTED_RISK' | 'RETROSPECTIVELY_AUTHORISED' | 'REVERSED' | 'FALSE_POSITIVE' | null;
  dispositionByName?: string | null;
  dispositionAt?: string | null;
  dispositionNote?: string | null;
}
/** The register, with the population it was found in and — deliberately — what it cannot look for. */
export interface UnauthorisedRegisterDto {
  scope: string;
  findings: UnauthorisedFindingDto[];
  population: number;
  /** Genuine authority gaps. Excludes UNATTRIBUTED_AUTHORISATION, and counts disposed gaps too. */
  authorityGaps: number;
  unattributed: number;
  open: number;
  disposed: number;
  /** Changes that took effect on an AUTO cell alone — a fact, not an accusation (S8e's territory). */
  autoAuthorisedEffective: number;
  byReason: Record<string, number>;
  bySeverity: Record<string, number>;
  /** Failure classes this reconciliation is blind to, each with a translated reason on screen. */
  undetectable: string[];
}
export interface UnauthorisedDispositionDto {
  id: number; changeRequestId: number; reasonCode: string; state: string;
  note?: string | null; disposedByName: string; disposedAt: string;
}
/** Why a change is or is not backed by a live pre-approval. `reason` is always present. */
export interface ChangeModelBacking {
  modelBacked: boolean;
  reason: 'LIVE_PRE_APPROVAL' | 'NO_MODEL_CITED' | 'DEVIATED_FROM_MODEL' | 'MODEL_NOT_FOUND'
        | 'PRE_APPROVAL_EXPIRED' | 'MODEL_NOT_APPROVED' | 'NOT_PRE_APPROVED';
  modelCode?: string | null; modelNameEn?: string | null; modelNameAr?: string | null;
  modelStatus?: string | null; authorisedByName?: string | null; authorisedUntil?: string | null;
  deviation?: boolean; deviationNotes?: string | null;
}

/** One required role from the matrix and whether the recorded approvals met it (F2-95). */
export interface RequiredRoleState {
  role: string;
  /** SATISFIED | MISSING | NO_ACTIVE_HOLDER | NOT_A_ROLE */
  status: 'SATISFIED' | 'MISSING' | 'NO_ACTIVE_HOLDER' | 'NOT_A_ROLE';
  satisfiedBy?: string | null;
}
export interface ChangeAuthorisationView {
  changeType: string; riskLevel: string; gate: string;
  minApprovals: number; requiredRoles?: string | null;
  approvesRecorded: number;
  /** The vote count alone — what `satisfied` used to mean on its own. */
  approvalsSatisfied: boolean;
  /** Every configured role requirement met. False when any is unmet OR unverifiable. */
  rolesSatisfied: boolean;
  requiredRoleStates: RequiredRoleState[];
  /** Roles that are checkable here and were not held by any approver — the actionable ask. */
  roleGaps: string[];
  /** Roles no approval could ever satisfy: nobody holds them, or they are not roles. */
  unverifiableRoles: string[];
  /**
   * ⚠ F2-95 changed this field's meaning: it is now `approvalsSatisfied && rolesSatisfied`.
   * It previously counted votes only, so it read `true` beside `requiredRoles: "…,CISO"` when
   * no CISO had approved. Nothing renders this payload yet — the pair was served to API
   * consumers and awaits CE-13's CAB workspace — so widening it breaks no screen.
   */
  satisfied: boolean;
  roleRemedy?: string | null;
}
export interface ChangeFreezeWindowDto {
  id?: number; organisationId?: number;
  code?: string; nameEn: string; nameAr?: string;
  startsAt: string; endsAt: string;
  scope?: string; scopeValue?: string | null;
  allowEmergency?: boolean; active?: boolean;
  notesEn?: string; notesAr?: string;
}
export interface ChangeCalendarItem {
  id: number; changeCode?: string; titleEn?: string; titleAr?: string;
  changeType?: string; riskLevel?: string; status?: string;
  scheduledStart?: string; scheduledEnd?: string;
}
export interface ChangeCalendarView {
  from: string; to: string;
  changes: ChangeCalendarItem[];
  freezes: ChangeFreezeWindowDto[];
}
export interface ChangeConflictView {
  id: number; changeCode?: string; titleEn?: string; status?: string;
  scheduledStart?: string; scheduledEnd?: string;
  sameCategory?: boolean; sharedEntities?: string[];
}

// Track D: committee re-route — a change's CAB decision routed through a committee.
export interface ChangeReferralView {
  id?: number; referralId?: number; committeeId?: number; gate?: string; mode?: string;
  status?: string; meetingId?: number | null; resolutionId?: number | null; referredBy?: string;
  // enriched committee decision (from the linked resolution + meeting)
  resolutionOutcome?: string; voteFor?: number; voteAgainst?: number; voteAbstain?: number;
  meetingStatus?: string; quorumMet?: boolean;
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

  // S7e/CE-5: the conditions register. Note the guard split the endpoints encode — imposing and
  // waiving are CHANGE:APPROVE acts, satisfying is CHANGE:WRITE.
  conditions:      (id: number) => api.get<ChangeConditionDto[]>(`/api/v1/changes/${id}/conditions`),
  openConditions:  () => api.get<ChangeConditionDto[]>('/api/v1/changes/conditions/open'),
  addCondition:    (id: number, body: ChangeConditionRequest) =>
                     api.post<ChangeConditionDto>(`/api/v1/changes/${id}/conditions`, body),
  satisfyCondition: (conditionId: number, notes?: string) =>
                     api.post<ChangeConditionDto>(`/api/v1/changes/conditions/${conditionId}/satisfy`, { notes }),
  waiveCondition:  (conditionId: number, reason: string) =>
                     api.post<ChangeConditionDto>(`/api/v1/changes/conditions/${conditionId}/waive`, { reason }),

  // S8a/CE-9: evidence on a change and on its conditions — the platform evidence subsystem, not
  // change_links. Attaches an EXISTING item: hashing, versioning and the four-eyes review belong to
  // the evidence workspace, and a second upload door here would have none of them.
  evidence:            (id: number) => api.get<ChangeEvidenceRef[]>(`/api/v1/changes/${id}/evidence`),
  attachEvidence:      (id: number, evidenceId: number, notes?: string) =>
                         api.post<ChangeEvidenceRef>(`/api/v1/changes/${id}/evidence`, { evidenceId, notes }),
  conditionEvidence:   (conditionId: number) =>
                         api.get<ChangeEvidenceRef[]>(`/api/v1/changes/conditions/${conditionId}/evidence`),
  attachConditionEvidence: (conditionId: number, evidenceId: number, notes?: string) =>
                         api.post<ChangeEvidenceRef>(`/api/v1/changes/conditions/${conditionId}/evidence`,
                                                     { evidenceId, notes }),

  comments:    (id: number) => api.get<ChangeCommentDto[]>(`/api/v1/changes/${id}/comments`),
  comment:     (id: number, body: ChangeCommentCreateDto) => api.post<ChangeCommentDto>(`/api/v1/changes/${id}/comments`, body),

  analytics:   () => api.get<ChangeAnalyticsDto>('/api/v1/changes/analytics'),

  // CH-17: classification-driven authorisation + freeze/calendar/conflicts
  authorisationMatrix: () => api.get<ChangeAuthMatrixCell[]>('/api/v1/changes/authorisation-matrix'),
  authorisation: (id: number) => api.get<ChangeAuthorisationView>(`/api/v1/changes/${id}/authorisation`),
  // S8e/CE-2 — the model catalogue. Authoring is WRITE; granting the pre-approval is APPROVE and
  // is refused for the model's own author, so the UI must not offer "create and approve" as one act.
  models:        () => api.get<ChangeModelDto[]>('/api/v1/changes/models'),
  createModel:   (body: Record<string, unknown>) => api.post<ChangeModelDto>('/api/v1/changes/models', body),
  updateModel:   (id: number, body: Record<string, unknown>) => api.put<ChangeModelDto>(`/api/v1/changes/models/${id}`, body),
  approveModel:  (id: number, validForDays: number, note?: string) =>
    api.post<ChangeModelDto>(`/api/v1/changes/models/${id}/approve`, { validForDays, note }),
  setModelStatus:(id: number, status: string) =>
    api.post<ChangeModelDto>(`/api/v1/changes/models/${id}/status`, { status }),
  changeModel:   (id: number) => api.get<ChangeModelBacking>(`/api/v1/changes/${id}/model`),
  applyModel:    (id: number, modelId: number, deviationNotes?: string) =>
    api.post<ChangeModelBacking>(`/api/v1/changes/${id}/model`, { modelId, deviationNotes }),

  // S8f/CE-11 — the unauthorised-change register. Reading is CHANGE:READ; recording an outcome is
  // CHANGE:APPROVE, because signing off work that already happened must not sit inside the privilege
  // of the people whose changes the register measures.
  unauthorisedRegister: () =>
    api.get<UnauthorisedRegisterDto>('/api/v1/changes/unauthorised-register'),
  disposeFinding: (changeId: number, reasonCode: string, state: string, note?: string) =>
    api.post<UnauthorisedDispositionDto>(
      `/api/v1/changes/unauthorised-register/${changeId}/${encodeURIComponent(reasonCode)}/dispose`,
      { state, note }),

  // S8g/CE-8 — the forward schedule. Reading is CHANGE:READ; PUBLISHING is CHANGE:APPROVE, because
  // issuing the schedule the business plans around is a governance act, not change authorship.
  // Declaring an outage is CHANGE:WRITE — it describes the change rather than authorising it.
  forwardSchedule: (from?: string, to?: string) => {
    const p = new URLSearchParams();
    if (from) p.set('from', from);
    if (to)   p.set('to', to);
    const q = p.toString();
    return api.get<ForwardScheduleDto>(`/api/v1/changes/forward-schedule${q ? '?' + q : ''}`);
  },
  schedulePublications: () =>
    api.get<SchedulePublicationDto[]>('/api/v1/changes/schedule-publications'),
  publishSchedule: (from: string, to: string, notes?: string) =>
    api.post<SchedulePublicationDto>('/api/v1/changes/schedule-publications', { from, to, notes }),
  schedulePublication: (id: number) =>
    api.get<{ publication: SchedulePublicationDto; entriesJson: string;
              digestMatches: boolean; recomputedSha256: string }>(
      `/api/v1/changes/schedule-publications/${id}`),
  declareOutage: (id: number, outageExpected: boolean, outageMinutes?: number, servicesAffected?: string) =>
    api.post<ChangeRequestDto>(`/api/v1/changes/${id}/outage`,
      { outageExpected, outageMinutes, servicesAffected }),

  // S8h / CE-10 — workflow-engine adoption (the 15th module)
  taskAddressability: () =>
    api.get<TaskAddressabilityDto>('/api/v1/changes/task-addressability'),
  engineAddressees: () =>
    api.get<EngineAddresseeDto[]>('/api/v1/changes/engine-addressees'),
  engineRequests: (id: number) =>
    api.get<EngineRequestRefDto[]>(`/api/v1/changes/${id}/engine-requests`),
  raiseEngineRequest: (id: number, body: RaiseEngineRequestBody) =>
    api.post<EngineRequestRefDto>(`/api/v1/changes/${id}/engine-requests`, body),
  routeTaskThroughEngine: (taskId: number, body: RaiseEngineRequestBody) =>
    api.post<EngineRequestRefDto>(`/api/v1/changes/tasks/${taskId}/engine-requests`, body),

  // CE-13 — the CAB queue. Read-only: the actions the workspace offers are referToCommittee (below)
  // and the committee module's own agenda rebuild, not new write endpoints.
  cabQueue: () => api.get<CabQueueDto>('/api/v1/changes/cab-queue'),

  freezeWindows: () => api.get<ChangeFreezeWindowDto[]>('/api/v1/changes/freeze-windows'),
  createFreeze:  (body: Partial<ChangeFreezeWindowDto>) => api.post<ChangeFreezeWindowDto>('/api/v1/changes/freeze-windows', body),
  deleteFreeze:  (id: number) => api.delete<void>(`/api/v1/changes/freeze-windows/${id}`),
  calendar:      (from?: string, to?: string) => {
    const p = new URLSearchParams();
    if (from) p.set('from', from);
    if (to)   p.set('to', to);
    const q = p.toString();
    return api.get<ChangeCalendarView>(`/api/v1/changes/calendar${q ? '?' + q : ''}`);
  },
  conflicts:     (id: number) => api.get<ChangeConflictView[]>(`/api/v1/changes/${id}/conflicts`),
  // Track D: route the CAB decision through a committee
  referToCommittee: (id: number, committeeId?: number) =>
    api.post<ChangeReferralView>(`/api/v1/changes/${id}/refer-to-committee`, committeeId != null ? { committeeId } : {}),
  committeeReferral: (id: number) => api.get<ChangeReferralView>(`/api/v1/changes/${id}/committee`),
  linkCommitteeResolution: (id: number, resolutionId: number) =>
    api.post<ChangeReferralView>(`/api/v1/changes/${id}/committee/resolution`, { resolutionId }),
};

// ─── Reporting — Scheduled reports (R4) ────────────────────────────────────
export interface ReportScheduleDto {
  id: number; organisationId?: number;
  externalId?: string;
  name: string; description?: string;
  /**
   * F2-278 — `ReportScheduleService#update` writes both of these from the request (guarded, so
   * nothing was destroyed) and this type declared neither, so the Arabic name and description of
   * a scheduled regulatory report were **frozen**: settable only by a seed or a direct API call,
   * and never editable from `ReportSchedulesPage`, which is the screen that edits this register.
   * F2-275's shape — an Arabic column that EXISTS is not one that can be WRITTEN — and F2-198's
   * for the read half: the wire has always carried them because the handler returns the entity.
   *
   * ⚠ Not latent: populated on 8 of 11 live rows in BOTH databases, so eight schedules already
   * hold an Arabic label that no screen could show or correct (house rule 7, on the primary
   * language). Both are `nvarchar(255)`/`nvarchar(MAX)` and nullable, mirroring their English
   * siblings.
   */
  nameAr?: string; descriptionAr?: string;
  reportType: string;        // RISK_REGISTER | FINDINGS_AGING | KRI_DASHBOARD
  format: string;            // PDF | XLSX
  language: string;          // en | ar
  cadence: string;           // DAILY | WEEKLY | MONTHLY | QUARTERLY
  cadenceAnchor?: string;
  timeOfDay: string;
  recipients: string;
  ccRecipients?: string;
  isActive?: boolean;
  lastRunAt?: string;
  nextRunAt: string;
  lastRunStatus?: string;
  lastRunMessage?: string;
  createdAt?: string;
}
export interface ReportRunDto {
  id: number; scheduleId: number;
  triggeredAt?: string;
  triggeredBy: string;
  status: string;
  durationMs?: number;
  bytes?: number;
  recipients?: string;
  message?: string;
}
export const reportScheduleApi = {
  list:    (page = 0, size = 50) => api.get<PageResponse<ReportScheduleDto>>(`/api/v1/reporting/schedules${pageQ(page, size)}`),
  get:     (id: number) => api.get<ReportScheduleDto>(`/api/v1/reporting/schedules/${id}`),
  create:  (body: Partial<ReportScheduleDto>) => api.post<ReportScheduleDto>('/api/v1/reporting/schedules', body),
  update:  (id: number, body: Partial<ReportScheduleDto>) => api.put<ReportScheduleDto>(`/api/v1/reporting/schedules/${id}`, body),
  remove:  (id: number) => api.delete<void>(`/api/v1/reporting/schedules/${id}`),
  runNow:  (id: number) => api.post<ReportRunDto>(`/api/v1/reporting/schedules/${id}/run-now`),
  runs:    (id: number) => api.get<ReportRunDto[]>(`/api/v1/reporting/schedules/${id}/runs`),
  recent:  () => api.get<ReportRunDto[]>('/api/v1/reporting/schedules/runs/recent'),
};

export const reportingApi = {
  riskRegisterPdf:  (lang: string) => downloadFile(`/api/v1/reporting/risk-register.pdf?lang=${encodeURIComponent(lang)}`, 'risk-register.pdf'),
  riskRegisterXlsx: () => downloadFile('/api/v1/reporting/risk-register.xlsx', 'risk-register.xlsx'),
  riskRegisterDocx: (lang: string) => downloadFile(`/api/v1/reporting/risk-register.docx?lang=${encodeURIComponent(lang)}`, 'risk-register.docx'),
  changeRegisterPdf:  (lang: string) => downloadFile(`/api/v1/reporting/change-register.pdf?lang=${encodeURIComponent(lang)}`, 'change-register.pdf'),
  changeRegisterXlsx: () => downloadFile('/api/v1/reporting/change-register.xlsx', 'change-register.xlsx'),
  changeRegisterDocx: (lang: string) => downloadFile(`/api/v1/reporting/change-register.docx?lang=${encodeURIComponent(lang)}`, 'change-register.docx'),
  cabPackPdf:         (id: number, lang: string) => downloadFile(`/api/v1/reporting/change/${id}/cab-pack.pdf?lang=${encodeURIComponent(lang)}`, `cab-pack-${id}.pdf`),
  // S8d/CE-14b — the CAB decision register: one row per change past a decision point, each stating
  // whether the decision is recorded or inferred and what evidences it. Guarded CHANGE:EXPORT +
  // CHANGE:READ, so the caller must be behind `canExport('CHANGE')` like the register above.
  changeDecisionRegisterPdf:  (lang: string) => downloadFile(`/api/v1/reporting/change-decision-register.pdf?lang=${encodeURIComponent(lang)}`, 'cab-decision-register.pdf'),
  changeDecisionRegisterXlsx: (lang: string) => downloadFile(`/api/v1/reporting/change-decision-register.xlsx?lang=${encodeURIComponent(lang)}`, 'cab-decision-register.xlsx'),
  policyDocumentPdf:  (id: number, lang: string) => downloadFile(`/api/v1/reporting/policy/${id}/document.pdf?lang=${encodeURIComponent(lang)}`, `policy-${id}.pdf`),
  // S7f/CE-14a — the MEETING's records, distinct from cabPackPdf above (which packs one change).
  // Guarded ADMIN:EXPORT + ADMIN:READ server-side: the committee engine is generic, not change-only.
  committeeAgendaPdf:  (meetingId: number, lang: string) => downloadFile(`/api/v1/reporting/committee/meeting/${meetingId}/agenda.pdf?lang=${encodeURIComponent(lang)}`, `committee-agenda-${meetingId}.pdf`),
  committeeMinutesPdf: (meetingId: number, lang: string) => downloadFile(`/api/v1/reporting/committee/meeting/${meetingId}/minutes.pdf?lang=${encodeURIComponent(lang)}`, `committee-minutes-${meetingId}.pdf`),
  findingsPdf:      (lang: string) => downloadFile(`/api/v1/reporting/findings-aging.pdf?lang=${encodeURIComponent(lang)}`, 'findings.pdf'),
  findingsXlsx:     () => downloadFile('/api/v1/reporting/findings-aging.xlsx', 'findings.xlsx'),
  findingsDocx:     (lang: string) => downloadFile(`/api/v1/reporting/findings-aging.docx?lang=${encodeURIComponent(lang)}`, 'findings.docx'),
  kriPdf:           (lang: string) => downloadFile(`/api/v1/reporting/kri-dashboard.pdf?lang=${encodeURIComponent(lang)}`, 'kri-dashboard.pdf'),
  kriXlsx:          () => downloadFile('/api/v1/reporting/kri-dashboard.xlsx', 'kri-dashboard.xlsx'),
  kriDocx:          (lang: string) => downloadFile(`/api/v1/reporting/kri-dashboard.docx?lang=${encodeURIComponent(lang)}`, 'kri-dashboard.docx'),
  // S1 — board pack
  boardPackPdf:     (lang: string) => downloadFile(`/api/v1/reporting/board-pack.pdf?lang=${encodeURIComponent(lang)}`, 'board-pack.pdf'),
  boardPackPptx:    (lang: string) => downloadFile(`/api/v1/reporting/board-pack.pptx?lang=${encodeURIComponent(lang)}`, 'board-pack.pptx'),
  // P5 — branded XLSX / DOCX from client-assembled tabular data (packets whose data lives client-side)
  packetXlsx:       (body: TabularExportPayload) =>
    downloadFile('/api/v1/reporting/packet.xlsx', `${body.module}_${body.exportType}.xlsx`, { method: 'POST', jsonBody: body }),
  packetDocx:       (body: TabularExportPayload) =>
    downloadFile('/api/v1/reporting/packet.docx', `${body.module}_${body.exportType}.docx`, { method: 'POST', jsonBody: body }),
};

/** Client payload for {@link reportingApi.packetXlsx} — mirrors the backend TabularExportRequest. */
export interface TabularExportPayload {
  module: string;
  exportType: string;
  lang?: string;
  sheets: { name: string; headers: string[]; rows: string[][] }[];
}

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
  titleEn: string; titleAr?: string; descriptionEn?: string; descriptionAr?: string;
  categoryId?: number; categoryName?: string; categoryNameEn?: string; categoryNameAr?: string;
  ownerId?: number; ownerName?: string; ownerFullNameEn?: string; ownerUsername?: string;
  status: string;
  inherentLikelihood: number; inherentImpact: number; inherentScore: number;
  residualLikelihood?: number; residualImpact?: number; residualScore?: number;
  riskLevel?: string; identifiedDate?: string; reviewDate?: string;
  createdAt?: string; updatedAt?: string;
}
export interface RiskRequest {
  titleEn: string; titleAr?: string; descriptionEn?: string; descriptionAr?: string;
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
  // F2-164 — `acceptRisk` (POST /risks/{id}/accept) is GONE, with its endpoint. It
  // was a bare RISK:WRITE flip to ACCEPTED, and `RiskService.close` treated that
  // status as an exemption from the mandatory-treatment gate — so one click retired
  // a CRITICAL risk, permanently (the expiry sweep iterates acceptance RECORDS and
  // this route created none). Use `riskAcceptanceApi` below: create → approve, the
  // second at RISK:APPROVE. `RiskAcceptancesPanel` is the screen for it.
  closeRisk:       (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/close`),
  reopenRisk:      (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/reopen`),
  archiveRisk:     (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/archive`),
  incidentBump:    (id: number) => api.post<RiskResponse>(`/api/v1/risk/risks/${id}/incident-bump`),
};

// ─── Risk acceptance (structured, time-boxed, approval-gated) — ERM epic E-A ─
export interface RiskAcceptanceDto {
  id: number; externalId: string; riskId: number;
  justificationEn: string; justificationAr?: string;
  compensatingControlsEn?: string; compensatingControlsAr?: string;
  residualScoreAtAcceptance?: number; appetiteCeiling?: number; requiresBoard?: boolean;
  expiresAt: string; status: string;
  croSignedBy?: string; boardSignedBy?: string;
  approvedBy?: string; approvedAt?: string; rejectionReason?: string; createdAt?: string;
}
export const riskAcceptanceApi = {
  listForRisk: (riskId: number) =>
    api.get<RiskAcceptanceDto[]>(`/api/v1/risk/risks/${riskId}/acceptances`),
  create: (riskId: number, body: {
    justificationEn: string; justificationAr?: string;
    compensatingControlsEn?: string; compensatingControlsAr?: string;
    appetiteCeiling?: number; expiresAt: string; croSignedBy?: string; boardSignedBy?: string;
  }) => api.post<RiskAcceptanceDto>(`/api/v1/risk/risks/${riskId}/acceptances`, body),
  approve: (id: number, boardSignedBy?: string) =>
    api.post<RiskAcceptanceDto>(`/api/v1/risk/acceptances/${id}/approve`, { boardSignedBy }),
  reject: (id: number, reason: string) =>
    api.post<RiskAcceptanceDto>(`/api/v1/risk/acceptances/${id}/reject`, { reason }),
  revoke: (id: number, reason: string) =>
    api.post<RiskAcceptanceDto>(`/api/v1/risk/acceptances/${id}/revoke`, { reason }),
};

// ─── Treatment plans (canonical rich risk_lc model) — ERM epic E-D ──────────
// The single canonical treatment model (actions + approval + cost-benefit),
// superseding the legacy simple risk.risk_treatments rows.
export interface TreatmentPlanDto {
  id: string; riskId: number; strategy: string;
  rationaleEn: string; rationaleAr: string; status: string;
  costSarK: number; costBenefitEn?: string | null; costBenefitAr?: string | null;
  createdAt?: string; submittedAt?: string;
  approvedBy?: string | null; approvedAt?: string | null; rejectedReason?: string | null;
  /**
   * ⚠⚠ F2-204: this field was MISSING from this interface while the server has always sent it.
   *
   * `TreatmentPlanDto` in `module/risk_lc/dto` declares `List<TreatmentActionDto> actions` and
   * maps them on every read. F2-198's `crossBorderTransfer` shape exactly — the value is on the
   * wire and the client type simply never mentioned it, so no caller could see it.
   *
   * 🔑 It also refuted a claim I wrote earlier in this very slice. I read THIS interface, saw no
   * `actions`, and concluded the server had none — then wrote that into a registry note and two
   * code comments. The backend gate's new leg caught it by finding a `TreatmentAction` @Entity.
   * One file short is the whole error, and this is its third instance in this lineage.
   */
  actions?: TreatmentActionOnWire[];
}

/** The action shape the server actually returns. Mirrors `TreatmentPlanDto.TreatmentActionDto`. */
export interface TreatmentActionOnWire {
  id: string;
  titleEn: string;
  titleAr: string;
  ownerRole: string;
  targetDate: string;
  status: string;
  expectedRiskReduction: number;
  completedAt?: string | null;
  evidenceRef?: string | null;
}
export const treatmentPlanApi = {
  /** Every plan for the tenant. Added by F2-204 — the risk-lifecycle workspace's list. */
  listForOrg: () => api.get<TreatmentPlanDto[]>('/api/risk-lc/treatment-plans'),
  // F2-204: `listForRisk` is DELETED. `listForOrg` reaches the same endpoint and
  // `plansForRisk()` filters the cached register, so it was a second client for a live
  // endpoint with no caller — the shape F2-165's gate refuses, and it fired on it. It had sat
  // in that gate's hole list for months attributed to "the plan the F2-164 close gate reads
  // has no register view", when in fact a whole workspace page was reading a browser copy.
  create: (body: {
    riskId: number; strategy: string; rationaleEn: string; rationaleAr: string;
    costSarK: number; costBenefitEn?: string; costBenefitAr?: string;
  }) => api.post<TreatmentPlanDto>('/api/risk-lc/treatment-plans', body),
  /**
   * Move a plan through the ERM approval machine, taking the row back from the server.
   *
   * F2-204: `rejectedReason` is the REJECTING OFFICER'S words and nothing else may fill it —
   * `RiskLifecyclePage` used to pass the literal `'Insufficient cost-benefit justification'`,
   * and since the server persists this field a naive wire would have written invented text
   * into `risk_lc.treatment_plans.rejected_reason` as a Chief Risk Officer's stated rationale.
   * There is deliberately no `approvedBy`: `TreatmentPlanService` stamps the authenticated
   * principal (F2-22b(vi)).
   */
  transition: (id: string, body: { status: string; rejectedReason?: string }) =>
    api.post<TreatmentPlanDto>(
      `/api/risk-lc/treatment-plans/${encodeURIComponent(id)}/transition`, body),
};

/**
 * The ERM treatment-plan machine, mirroring `TreatmentPlanService.ALLOWED` edge for edge.
 * Pinned by `RiskTreatmentMachineParityTest`; read through the predicates below so there is
 * one implementation of "what can this plan become?" (F2-203's shape).
 */
export const TREATMENT_ALLOWED: Record<string, readonly string[]> = {
  DRAFT:       ['SUBMITTED'],
  SUBMITTED:   ['APPROVED', 'REJECTED'],
  APPROVED:    ['IN_PROGRESS'],
  IN_PROGRESS: ['COMPLETED'],
  REJECTED:    ['DRAFT'],
  COMPLETED:   [],
};

// ─── Typed register linkage — ERM epic E-G ──────────────────────────────────
export interface RiskLinkDto {
  id: number; riskId: number; linkType: string; targetRef: string;
  targetId?: number | null; descriptionEn?: string | null; createdAt?: string;
}
export const RISK_LINK_TYPES = [
  'OBJECTIVE', 'PROCESS', 'SERVICE', 'ASSET', 'SUPPLIER',
  'PROJECT', 'INCIDENT', 'CONTROL', 'OBLIGATION', 'COMMITTEE', 'POLICY', 'OTHER',
] as const;
export const riskLinkApi = {
  listForRisk: (riskId: number) =>
    api.get<RiskLinkDto[]>(`/api/v1/risk/risks/${riskId}/links`),
  create: (riskId: number, body: {
    linkType: string; targetRef: string; targetId?: number | null; descriptionEn?: string;
  }) => api.post<RiskLinkDto>(`/api/v1/risk/risks/${riskId}/links`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/risk/links/${id}`),
};

// ─── Methodology + multi-dimensional impact — ERM epic E-F ──────────────────
export interface MethodologyConfigDto {
  impactDimensions: string[]; likelihoodScaleMax: number; impactScaleMax: number;
}
export interface ImpactDimensionEntry { dimension: string; score: number | null; }
export interface ImpactRatingsResponse {
  dimensions: ImpactDimensionEntry[]; overallImpact: number | null; impactScaleMax: number;
}
export const methodologyApi = {
  get: () => api.get<MethodologyConfigDto>('/api/v1/risk/methodology'),
  update: (body: Partial<{ impactDimensions: string[]; likelihoodScaleMax: number; impactScaleMax: number }>) =>
    api.put<MethodologyConfigDto>('/api/v1/risk/methodology', body),
};
export const riskImpactApi = {
  getDimensions: (riskId: number) =>
    api.get<ImpactRatingsResponse>(`/api/v1/risk/risks/${riskId}/impact-dimensions`),
  setDimensions: (riskId: number, dimensions: { dimension: string; score: number }[]) =>
    api.put<ImpactRatingsResponse>(`/api/v1/risk/risks/${riskId}/impact-dimensions`, { dimensions }),
};

// ─── Scheduled review / reassessment — ERM epic E-J ─────────────────────────
export interface RiskReviewDto {
  id: number; riskId: number; reviewedAt: string;
  reviewedBy?: number | null; reviewedByName?: string | null;
  decision: string;
  residualLikelihoodBefore?: number | null; residualImpactBefore?: number | null;
  residualLikelihoodAfter?: number | null; residualImpactAfter?: number | null;
  residualScoreSnapshot?: number | null; riskLevelSnapshot?: string | null;
  nextReviewDate?: string | null; noteEn?: string | null; noteAr?: string | null;
}
export interface RiskReviewOverdueRow {
  riskId: number; riskCode: string; titleEn: string; titleAr?: string | null;
  status: string; riskLevel?: string | null; residualScore?: number | null;
  reviewDate?: string | null; reviewStatus: string; daysOverdue: number;
}
export const REVIEW_DECISIONS = ['CONFIRMED', 'RESCORED', 'ESCALATE', 'RETIRE_SUGGESTED'] as const;
export const riskReviewApi = {
  reassess: (riskId: number, body: {
    residualLikelihood?: number | null; residualImpact?: number | null;
    cadenceDays?: number | null; decision?: string; noteEn?: string; noteAr?: string;
  }) => api.post<RiskReviewDto>(`/api/v1/risk/risks/${riskId}/reassess`, body),
  history: (riskId: number) => api.get<RiskReviewDto[]>(`/api/v1/risk/risks/${riskId}/reviews`),
  overdue: () => api.get<RiskReviewOverdueRow[]>('/api/v1/risk/reviews/overdue'),
  runSweep: () => api.post<{ flagged: number }>('/api/v1/risk/reviews/run-overdue-sweep'),
};

// ─── Reporting + audit evidence package — ERM epic E-K ──────────────────────
export interface TopRiskRow {
  riskId: number; riskCode: string; titleEn: string; titleAr?: string | null;
  status: string; riskLevel?: string | null; residualScore?: number | null;
  inherentScore?: number | null; ownerId?: number | null; categoryId?: number | null;
}
export interface AppetiteBreachRow {
  breachId: number; appetiteId: number; appetiteTitleEn?: string | null; categoryId?: number | null;
  severity?: string | null; observedValue?: number | null; toleranceUpper?: number | null;
  currentExposure?: number | null; status?: string | null; breachedAt?: string | null; acknowledgedAt?: string | null;
}
export interface RiskRegisterStats {
  totalRisks: number; byLevel: Record<string, number>; byStatus: Record<string, number>;
  reviewOverdue: number; activeAcceptances: number; openAppetiteBreaches: number;
}
export interface RiskAuditPackage {
  organisationId: number; generatedAt: string; stats: RiskRegisterStats;
  topRisks: TopRiskRow[]; openAppetiteBreaches: AppetiteBreachRow[];
}
export const riskReportApi = {
  topRisks: (limit = 10) => api.get<TopRiskRow[]>(`/api/v1/risk/reports/top-risks?limit=${limit}`),
  appetiteBreaches: (openOnly = false) =>
    api.get<AppetiteBreachRow[]>(`/api/v1/risk/reports/appetite-breaches?openOnly=${openOnly}`),
  registerStats: () => api.get<RiskRegisterStats>('/api/v1/risk/reports/register-stats'),
  auditPackage: () => api.get<RiskAuditPackage>('/api/v1/risk/reports/audit-package'),
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
  // KRIs — retired (ERM epic E-E). The canonical KRI engine is the single
  // source of truth: use `kriApi` in lib/kriEngine.ts (/api/v1/risk/kri/*).
  // Appetite
  appetites:   (page = 0, size = 50) => api.get<PageResponse<RiskAppetiteStatementDto>>(`/api/v1/risk/appetite${pageQ(page, size)}`),
  getAppetite: (id: number) => api.get<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}`),
  createAppetite: (body: Partial<RiskAppetiteStatementDto>) => api.post<RiskAppetiteStatementDto>('/api/v1/risk/appetite', body),
  updateAppetite: (id: number, body: Partial<RiskAppetiteStatementDto>) => api.put<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}`, body),
  deleteAppetite: (id: number) => api.delete<void>(`/api/v1/risk/appetite/${id}`),
  // F2-431 — no `approvedBy`. The server records the authenticated principal, so sending
  // one was a field the server ignores, which tells a caller their value matters (F2-184).
  approveAppetite: (id: number) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/approve`),
  retireAppetite:  (id: number) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/retire`),
  updateExposure:  (id: number, value: number, notes?: string) => api.post<RiskAppetiteStatementDto>(`/api/v1/risk/appetite/${id}/exposure`, { value, notes }),
  appetiteBreaches: (id: number) => api.get<AppetiteBreachEventDto[]>(`/api/v1/risk/appetite/${id}/breaches`),
  appetiteDashboard: () => api.get<{ total: number; withinTolerance: number; exceeded: number; critical: number; noExposure: number }>('/api/v1/risk/appetite/dashboard'),
};

// ─── Risk Bow-Tie Analysis (ISO 31010) ──────────────────────────────────────
// Sprint 11 P1 GRC. Each bow-tie has one top-event header plus four child
// collections: threats (left), consequences (right), preventive controls
// (between threat & top event), and mitigating controls (between top event &
// consequence). The detail page renders the full picture as an SVG bow-tie.

export interface BowTieAnalysisResponse {
  id: number;
  externalId: string;
  bowtieCode: string;
  linkedRiskId?: number;
  topEventTitleEn: string;
  topEventTitleAr?: string;
  topEventDescriptionEn?: string;
  topEventDescriptionAr?: string;
  riskCategory?: string;
  assessmentDate: string;
  assessedByName?: string;
  methodology: string;
  overallResidualRating?: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  overallControlEffectiveness?: number;
  status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'SUPERSEDED' | 'ARCHIVED';
  approvedAt?: string;
  approvedByName?: string;
  nextReviewAt?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface BowTieThreatDto {
  id?: number;
  externalId?: string;
  bowtieId?: number;
  threatOrder?: number;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  inherentLikelihood?: string;
  residualLikelihood?: string;
  source?: string;
}

export interface BowTieConsequenceDto {
  id?: number;
  externalId?: string;
  bowtieId?: number;
  consequenceOrder?: number;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  consequenceType?: string;
  inherentSeverity?: string;
  residualSeverity?: string;
  estimatedLossSar?: number;
}

export interface BowTiePreventiveControlDto {
  id?: number;
  externalId?: string;
  bowtieId?: number;
  threatId?: number;
  threatExternalId?: string;
  controlOrder?: number;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlType?: string;
  linkedControlId?: number;
  effectiveness: 'VERY_LOW' | 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  lastTestedAt?: string;
  lastTestResult?: string;
  responsibleRole?: string;
  responsibleOwner?: string;
}

export interface BowTieMitigatingControlDto {
  id?: number;
  externalId?: string;
  bowtieId?: number;
  consequenceId?: number;
  consequenceExternalId?: string;
  controlOrder?: number;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  controlType?: string;
  linkedControlId?: number;
  effectiveness: 'VERY_LOW' | 'LOW' | 'MEDIUM' | 'HIGH' | 'VERY_HIGH';
  lastTestedAt?: string;
  lastTestResult?: string;
  responsibleRole?: string;
  responsibleOwner?: string;
}

export interface BowTieFullResponse {
  header: BowTieAnalysisResponse;
  threats: BowTieThreatDto[];
  consequences: BowTieConsequenceDto[];
  preventiveControls: BowTiePreventiveControlDto[];
  mitigatingControls: BowTieMitigatingControlDto[];
}

export interface BowTieAnalysisRequest {
  bowtieCode?: string;
  linkedRiskId?: number;
  topEventTitleEn: string;
  topEventTitleAr?: string;
  topEventDescriptionEn?: string;
  topEventDescriptionAr?: string;
  riskCategory?: string;
  assessmentDate?: string;
  assessedByName?: string;
  methodology?: string;
  nextReviewAt?: string;
  threats?: BowTieThreatDto[];
  consequences?: BowTieConsequenceDto[];
}

export const bowtieApi = {
  list:      () => api.get<BowTieAnalysisResponse[]>('/api/v1/risk/bowtie'),
  get:       (externalId: string) => api.get<BowTieFullResponse>(`/api/v1/risk/bowtie/${externalId}`),
  create:    (body: BowTieAnalysisRequest) => api.post<BowTieAnalysisResponse>('/api/v1/risk/bowtie', body),
  update:    (externalId: string, body: BowTieAnalysisRequest) => api.put<BowTieAnalysisResponse>(`/api/v1/risk/bowtie/${externalId}`, body),
  addThreat: (externalId: string, dto: BowTieThreatDto) => api.post<BowTieThreatDto>(`/api/v1/risk/bowtie/${externalId}/threats`, dto),
  removeThreat: (threatExternalId: string) => api.delete<void>(`/api/v1/risk/bowtie/threats/${threatExternalId}`),
  addConsequence: (externalId: string, dto: BowTieConsequenceDto) => api.post<BowTieConsequenceDto>(`/api/v1/risk/bowtie/${externalId}/consequences`, dto),
  removeConsequence: (consExternalId: string) => api.delete<void>(`/api/v1/risk/bowtie/consequences/${consExternalId}`),
  addPreventiveControl: (threatExternalId: string, dto: BowTiePreventiveControlDto) =>
    api.post<BowTiePreventiveControlDto>(`/api/v1/risk/bowtie/threats/${threatExternalId}/preventive-controls`, dto),
  addMitigatingControl: (consExternalId: string, dto: BowTieMitigatingControlDto) =>
    api.post<BowTieMitigatingControlDto>(`/api/v1/risk/bowtie/consequences/${consExternalId}/mitigating-controls`, dto),
  submit:    (externalId: string) => api.post<BowTieAnalysisResponse>(`/api/v1/risk/bowtie/${externalId}/submit`),
  approve:   (externalId: string, approverName?: string) =>
    api.post<BowTieAnalysisResponse>(`/api/v1/risk/bowtie/${externalId}/approve`, { approverName }),
  archive:   (externalId: string) => api.delete<void>(`/api/v1/risk/bowtie/${externalId}`),
};

// ─── Dashboard ──────────────────────────────────────────────────────────────
export interface DashboardStats {
  totalRisks: number; criticalRisks: number; highRisks: number; mediumRisks: number; lowRisks: number;
  openRisks: number; closedRisks: number; totalUsers: number; activeUsers: number;
  openCriticalRisks: number; openHighRisks: number; openIncidents: number;
  complianceScore: number; pendingTasks: number; overdueItems?: number;
}
export interface TrendPoint { label: string; value: number }
export const dashboardApi = {
  stats: () => api.get<DashboardStats>('/api/v1/dashboard/stats'),
  kpiTrend: (metric = 'risks', weeks = 12) =>
    api.get<TrendPoint[]>(`/api/v1/dashboard/kpi-trend?metric=${encodeURIComponent(metric)}&weeks=${weeks}`),
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
  // Legacy BcmController moved to /legacy-plans/* after audit T1.1 carve-out:
  // the canonical /api/v1/bcm/plans now serves the new BcpPlanController shape
  // (planCode + sections + versions). The legacy /bcm/legacy page on the UI
  // still wants the older BcmPlan shape (planNameEn + INVOKED status etc.),
  // so it stays on the renamed legacy path.
  plans:        (page = 0, size = 50) => api.get<PageResponse<BcmPlanDto>>(`/api/v1/bcm/legacy-plans${pageQ(page, size)}`),
  createPlan:   (body: BcmPlanRequest) => api.post<BcmPlanDto>('/api/v1/bcm/legacy-plans', body),
  updatePlan:   (id: number, body: BcmPlanRequest) => api.put<BcmPlanDto>(`/api/v1/bcm/legacy-plans/${id}`, body),
  deletePlan:   (id: number) => api.delete<void>(`/api/v1/bcm/legacy-plans/${id}`),
  // Plan ISO-22301 lifecycle workflow
  submitPlanForReview: (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/legacy-plans/${id}/submit-for-review`),
  approvePlan:  (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/legacy-plans/${id}/approve`),
  invokePlan:   (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/legacy-plans/${id}/invoke`),
  restorePlan:  (id: number) => api.post<BcmPlanDto>(`/api/v1/bcm/legacy-plans/${id}/restore`),
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
  // BIA dependency graph (M3) — legacy process-only shape kept on the renamed
  // path. The canonical /api/v1/bcm/dependency-graph now serves the richer
  // multi-type graph (BIA + service + BCP + DRP + strategy + cloud) from
  // BcmDependencyGraphController and is consumed by lib/bcmDependencyGraph.ts.
  dependencyGraph: () => api.get<{
    nodes: Array<{ id: number; nameEn: string; nameAr?: string; criticality?: string }>;
    edges: Array<{ id: number; from: number; to: number; dependencyType: string; criticalityImpact?: string; notes?: string }>;
  }>('/api/v1/bcm/dependency-graph-processes'),
  listDependencies:  () => api.get<Array<{ id: number; processId: number; dependsOnProcessId: number; dependencyType: string; criticalityImpact?: string; notes?: string }>>('/api/v1/bcm/dependencies'),
  createDependency:  (body: { processId: number; dependsOnProcessId: number; dependencyType?: string; criticalityImpact?: string; notes?: string }) =>
                       api.post<unknown>('/api/v1/bcm/dependencies', body),
  deleteDependency:  (id: number) => api.delete<void>(`/api/v1/bcm/dependencies/${id}`),
  stats:        () => api.get<unknown>('/api/v1/bcm/stats'),
};

// ─── BCM dependency-cascade simulator (Sprint-9 Slice 9-A) ───────────────────
// GET /api/v1/bcm/dependency-cascade/{failedProcessId} — given one critical
// process failure, BFS the downstream consumers and bucket impact at the BCM
// time-bands (1h / 4h / 8h / 24h), flag MTPD breaches, and decay confidence
// with cascade depth. Pure compute over bcm.process_dependencies +
// critical_processes + bia_records. Source-process list comes from
// bcmApi.dependencyGraph().nodes.
export interface CascadeBandSummary { impacted: number; breached: number; }
export interface CascadeImpactRow {
  processId: number;
  processNameEn: string;
  processNameAr?: string;
  criticality?: string;
  depth: number;                 // edges from the failed source
  reachHour: number;             // band the cascade lands in (1/4/8/24)
  mtpdHours?: number | null;
  rtoHours?: number | null;
  /**
   * F2-282 — the approval status of the BIA record these objectives came from
   * (`APPROVED` | `UNDER_REVIEW` | `IN_PROGRESS` | `DRAFT`; the column has no
   * CHECK, so treat the vocabulary as open).
   *
   * On the wire because a process can carry several BIAs and the platform picks
   * one — the lowest stated MTPD, now via a single shared predicate rather than
   * the three hand-rolled loops that disagreed. Measured 2026-08-22 in dev and
   * prod: **4 of 8 processes resolve to an unapproved record**, and for
   * `Core Banking Operations` (CRITICAL) the winner is an `IN_PROGRESS` row
   * stating MTPD 1h over an `APPROVED` one stating 72h.
   *
   * Which of those is the organisation's real tolerance is an owner decision, so
   * this is disclosed and never acted on — a reader must be able to tell a
   * ratified recovery objective from a draft.
   */
  mtpdSourceStatus?: string | null;
  breached: boolean;             // reachHour >= MTPD (or RTO)
  confidence: number;            // 0..1, drops with depth
}
export interface CascadeResult {
  sourceProcessId: number;
  sourceProcessNameEn: string;
  sourceProcessNameAr?: string;
  sourceCriticality?: string;
  totalImpactedCount: number;
  breachedCount: number;
  byHourBand: Record<string, CascadeBandSummary>;   // keys "1" | "4" | "8" | "24"
  impacts: CascadeImpactRow[];
}
export const CASCADE_BANDS = [1, 4, 8, 24];
export const dependencyCascadeApi = {
  simulate: (failedProcessId: number) =>
    api.get<CascadeResult>(`/api/v1/bcm/dependency-cascade/${failedProcessId}`),
};

// ─── BCM DR-test trend (Sprint-9 Slice 9-B) ──────────────────────────────────
// GET /api/v1/bcm/dr-test-trend?quarters=8 — rolling-quarter time-series of DR
// test outcomes over bcm.dr_plans, plus a snapshot (total DRPs, never-tested,
// overdue >12mo, recent-12mo pass rate). The DB outcome taxonomy is
// PASSED / FAILED / GAP / NOT_TESTED, so the service's `partial` bucket is
// always 0 and GAP/NOT_TESTED fall into `na`; the UI folds partial+na into a
// single "gap / other" series. passRate / recentPassRate are 0..1.
export interface DrQuarterStats {
  quarter: string;            // "2026-Q1"
  quarterStart: string;       // ISO date
  quarterEnd: string;         // ISO date
  totalTested: number;
  passed: number;
  failed: number;
  partial: number;
  na: number;
  passRate: number;           // 0..1
}
export interface DrTrendSnapshot {
  totalDrps: number;
  neverTested: number;
  testedOverdue: number;      // last test older than 12 months
  testedRecently: number;     // last test within 12 months
  passedRecently: number;
  recentPassRate: number;     // 0..1
}
export interface DrTrendReport {
  evaluatedOn: string;        // ISO date
  series: DrQuarterStats[];
  snapshot: DrTrendSnapshot;
}
export const DR_TREND_DEFAULT_QUARTERS = 8;
export const DR_TREND_MAX_QUARTERS = 20;
export const drTestTrendApi = {
  trend: (quarters?: number) =>
    api.get<DrTrendReport>(`/api/v1/bcm/dr-test-trend${quarters ? `?quarters=${quarters}` : ''}`),
};

// ─── AI-assisted control mapping (Sprint-8 Slice 8-E) ────────────────────────
// POST /api/v1/controls/ai/suggest — given a free-text description of a risk,
// finding, requirement or gap, returns the top-N matching controls from the
// unified library (platform.controls). Deterministic scoring: blended Jaccard
// token overlap + Saudi-native regulatory-keyword boost (SAMA/NCA/SDAIA/PDPL…)
// + explicit control-code mention. No LLM — auditable + reproducible for a
// SAMA/NCA examiner. confidence/jaccard are 0..1.
export interface ControlSuggestion {
  controlId: number;
  controlCode: string;
  domain: string;
  titleEn: string;
  titleAr?: string | null;
  jaccard: number;       // 0..1 — raw token-overlap
  confidence: number;    // 0..1 — blended jaccard + Saudi boost + code mention
  rationale: string;     // human-readable explanation of the match
}
export interface ControlSuggestRequest {
  description: string;
  domain?: string;       // optional domain filter (CYBER | BCM | PRIVACY | …)
  topN?: number;
}
export interface ControlSuggestResponse {
  query: string;
  suggestions: ControlSuggestion[];
  totalScored: number;   // how many controls scored > 0 before the top-N cut
}
export const aiControlMapApi = {
  suggest: (body: ControlSuggestRequest) =>
    api.post<ControlSuggestResponse>('/api/v1/controls/ai/suggest', body),
};

// ─── PDPL data-flow graph (Sprint-8 Slice 8-C) ───────────────────────────────
// GET /api/v1/privacy/data-flow-graph — a node-link graph over the RoPA
// (privacy.processing_activities): CATEGORY → ACTIVITY → COUNTRY + MECHANISM,
// the SDAIA PDPL "where does personal data flow" picture a DPO needs for
// inspections. Pure read aggregator; bilingual labels carried through.
export interface DataFlowNode {
  id: string;                          // "ACTIVITY:7" | "CATEGORY:HEALTH" | "COUNTRY:United States" | "MECHANISM:SCC"
  type: string;                        // ACTIVITY | CATEGORY | COUNTRY | MECHANISM
  labelEn: string;
  labelAr: string;
  attrs: Record<string, string>;       // ACTIVITY: legalBasis/status/specialCategoryFlag/retentionPeriodMonths · COUNTRY: adequacy
}
export interface DataFlowEdge {
  source: string;
  target: string;
  relation: string;                    // USED_BY | TRANSFERS_TO | GOVERNED_BY
  label?: string | null;               // adequacy tag for TRANSFERS_TO; null otherwise
}
export interface DataFlowSummary {
  activityCount: number;
  crossBorderActivityCount: number;
  distinctDestinations: number;
  categoryEdgeCount: number;
  nodesByType: Record<string, number>;
  categoryFrequency: Record<string, number>;
  mechanismFrequency: Record<string, number>;
}
export interface DataFlowGraph {
  nodes: DataFlowNode[];
  edges: DataFlowEdge[];
  summary: DataFlowSummary;
}
export const pdplDataFlowApi = {
  graph: () => api.get<DataFlowGraph>('/api/v1/privacy/data-flow-graph'),
};

// ─── Cyber · Frameworks ─────────────────────────────────────────────────────
/** F2-252 — `cyber.cyber_frameworks`, tenant-owned. See `cyberApi.frameworks` for the ⚠. */
export interface CyberFrameworkDto {
  id: number; name: string; nameAr?: string;
  version?: string; regulator?: string; frameworkCode?: string;
}

// ─── Cyber · Controls ───────────────────────────────────────────────────────
export interface CyberControlDto {
  id: number; frameworkId?: number;
  // F2-277 — the server derives BOTH halves from the resolved framework; neither is the
  // caller's to send (F2-252).
  frameworkName?: string; frameworkNameAr?: string;
  domain?: string;
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
  // F2-275 — `descriptionAr` is a real, populated column on `cyber.sub_controls` and this type
  // never declared it, so the Arabic description of an NCA ECC sub-control was invisible to every
  // client (F2-198's shape). F2-213 had removed it from the server's update whitelist to stop the
  // English form DELETING it, which preserved it and left it permanently uneditable — frozen and
  // protected are indistinguishable from the caller's side. The server now writes it under a null
  // guard, and `FullReplaceUpdateContractTest` requires this type to be able to carry it: the
  // server may not write a column its only client cannot put on the wire.
  descriptionEn?: string; descriptionAr?: string;
  controlOwner?: string;
  maturityLevel?: string; implementationStatus?: string;
  evidenceRequired?: boolean;
}
export interface CyberSubControlRequest {
  controlId: number; subControlNumber?: string;
  // Declaring it is what makes the guarded server write legitimate. Whether the sub-control FORM
  // grows an Arabic textarea is a separate product question and stays filed — a field the type can
  // express and the form does not collect is honest; a field the server writes and the type cannot
  // express is not.
  titleEn: string; titleAr?: string; descriptionEn?: string; descriptionAr?: string;
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
  staleSince?: string; reRequestId?: number;   // CY-J — set by the expiry sweep
}
export interface CyberEvidenceRequest {
  titleEn: string; titleAr?: string;
  evidenceType?: string;
  controlId?: number;
  frameworkRequirement?: string; ownerName?: string;
  version?: string;
  reviewDate?: string; expiryDate?: string;
  fileName?: string; status?: string;
}

// ─── Cyber · Policies (CY-I) — shared policy engine, cyber-scoped ────────────
export interface CyberPolicyDto {
  id: number; policyCode: string; version: string;
  titleEn: string; titleAr?: string; summaryEn?: string; summaryAr?: string;
  category: string; status: string;
  ownerName?: string; ownerEmail?: string; approverName?: string;
  effectiveDate?: string; expiryDate?: string; nextReviewDate?: string; reviewCadence?: string;
  publishedAt?: string; createdAt?: string;
}
export interface CyberPolicyRequest {
  policyCode: string; titleEn: string; titleAr?: string; summaryEn?: string;
  category?: string; ownerName?: string; ownerEmail?: string;
  effectiveDate?: string; nextReviewDate?: string; reviewCadence?: string;
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
  // CY-E — SLA clock
  escalatedAt?: string; lastReminderAt?: string; reminderCount?: number;
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
  // F2-277 — declared for the same reason frameworkName is: the server DERIVES both halves
  // from the resolved framework and accepts an echo, so a form that round-trips the whole
  // row keeps working in either language. Sending a value that contradicts the framework is
  // refused, which is what keeps this from being an inert field (F2-184).
  frameworkNameAr?: string;
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
  // ⚠ F2-212: `status`, `discoveryDate` and `targetResolutionDate` were declared here
  // and `cyber.vulnerabilities` (22 columns, read from sys.columns) has NO COLUMN for
  // any of them — `GET /api/v1/cyber/vulnerabilities` returns the `Vulnerability`
  // ENTITY, so they could never arrive. `workflowStatus` is the column that exists;
  // `r.workflowStatus ?? r.status` on CyberPage was a chain whose second operand was
  // dead, the third instance of that shape after F2-211's two.
  //
  // `capaId` STAYS: `capa_id` is a real column, null on all 20 live rows because no
  // vulnerability has been linked to a CAPA yet. That is a data fact, not a contract
  // fact — F2-211's distinction, and 1 of the 4 fields F2-210 filed as dead.
  workflowStatus?: string;
  remediationNotes?: string;
  capaId?: number;
  overdue?: boolean;
  reopenedCount?: number;
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
/** Dashboard aggregate shape returned by `GET /api/v1/cyber/dashboard`.
 *  See `CyberDashboardDto` on the backend — extended in PR for /cyber/dashboard polish
 *  with overallScore + per-framework breakdowns + expiringEvidence60d. */
export interface CyberDashboardDto {
  totalControls: number;
  compliantControls: number;
  nonCompliantControls: number;
  partialControls: number;
  openVulnerabilities: number;
  criticalVulnerabilities: number;
  overdueVulnerabilities: number;
  openIncidents: number;
  criticalIncidents: number;
  totalAssets: number;
  criticalAssets: number;
  openActionPlans: number;
  overdueActionPlans: number;
  totalEvidence: number;
  expiredEvidence: number;
  expiringEvidence60d: number;
  overallScore: number;
  eccScore: number;
  csccScore: number;
  cccScore: number;
  otccScore: number;
  recentIncidents?: unknown[];
  recentVulnerabilities?: unknown[];
  recentActionPlans?: unknown[];
}

export const cyberApi = {
  dashboard:      () => api.get<CyberDashboardDto>('/api/v1/cyber/dashboard'),
  /**
   * F2-252 — the tenant's real cyber framework register, so the control form can offer a picker
   * over what exists instead of a hard-coded list.
   *
   * ⚠ This is NOT `compliance.frameworks`. There are two disjoint framework registers with
   * OVERLAPPING id spaces: this one (`cyber.cyber_frameworks`, tenant-owned) and the global
   * regulator catalogue at `/api/v1/compliance/frameworks`. Id 2 is "ISO 27001" here and
   * "NCA Cloud Cybersecurity Controls" there, so a control's `frameworkId` resolved against the
   * wrong register dereferences cleanly and names the wrong framework.
   */
  frameworks:     () => api.get<CyberFrameworkDto[]>('/api/v1/cyber/frameworks'),
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
  runEvidenceExpirySweep: () => api.post<{ expired: number }>('/api/v1/cyber/evidence/run-expiry-sweep'),

  // Action Plans
  actionPlans:        (page = 0, size = 50) => api.get<PageResponse<CyberActionPlanDto>>(`/api/v1/cyber/action-plans${pageQ(page, size)}`),
  createActionPlan:   (body: CyberActionPlanRequest) => api.post<CyberActionPlanDto>('/api/v1/cyber/action-plans', body),
  updateActionPlan:   (id: number, body: CyberActionPlanRequest) => api.put<CyberActionPlanDto>(`/api/v1/cyber/action-plans/${id}`, body),
  deleteActionPlan:   (id: number) => api.delete<void>(`/api/v1/cyber/action-plans/${id}`),
  completePlan:       (id: number) => api.post<CyberActionPlanDto>(`/api/v1/cyber/action-plans/${id}/complete`),

  // Exports (CY-F) — server-side Excel (POI) + Arabic-RTL PDF (OpenPDF).
  // register ∈ controls|vulnerabilities|assessments|incidents|action-plans|evidence.
  exportRegister:     (register: string, format: 'xlsx' | 'pdf', lang?: string) =>
    downloadFile(
      `/api/v1/cyber/export?register=${encodeURIComponent(register)}&format=${format}${lang ? `&lang=${encodeURIComponent(lang)}` : ''}`,
      `cyber-${register}.${format}`),

  // Policies (CY-I) — cyber policy lifecycle wired to the shared policy engine.
  policies:           (latestOnly = false, status?: string) =>
    api.get<CyberPolicyDto[]>(`/api/v1/cyber/policies?latestOnly=${latestOnly}${status ? `&status=${encodeURIComponent(status)}` : ''}`),
  createPolicy:       (body: CyberPolicyRequest) => api.post<CyberPolicyDto>('/api/v1/cyber/policies', body),
  updatePolicy:       (id: number, body: Partial<CyberPolicyRequest>) => api.put<CyberPolicyDto>(`/api/v1/cyber/policies/${id}`, body),
  transitionPolicy:   (id: number, to: string) => api.post<CyberPolicyDto>(`/api/v1/cyber/policies/${id}/transition`, { to }),
  newPolicyVersion:   (policyCode: string, newVersion: string) => api.post<CyberPolicyDto>(`/api/v1/cyber/policies/${encodeURIComponent(policyCode)}/new-version`, { newVersion }),
  deletePolicy:       (id: number) => api.delete<void>(`/api/v1/cyber/policies/${id}`),

  // Catalog
  // F2-165 — `controlsAll` and `frameworks` deleted here: same GETs as
  // `lib/frameworkControls.ts#listAllControls` / `#listFrameworks`, which
  // `FrameworkControlsPage` calls. Neither of these had a caller.
  // dashboard: moved up (typed as CyberDashboardDto)
};

// ─── Vulnerability LIFECYCLE register (cyber_lc) — a SECOND register, deliberately adjacent ──

/**
 * ⚠⚠ There are TWO vulnerability registers in this platform and confusing them is the whole
 * risk of this client existing. They are placed side by side here so a reader cannot pick one
 * without seeing the other.
 *
 *   `cyberApi.vulns`      → `/api/v1/cyber/vulnerabilities`   → `cyber.vulnerabilities`     (21 rows)
 *   `vulnLifecycleApi`    → `/api/cyber-lc/vulnerabilities`   → `cyber_lc.vulnerabilities`  (36 rows)
 *
 * Both counts measured 2026-08-13 and identical in dev and prod. They are NOT the same object
 * at the same fidelity: the `cyber_lc` register carries the NCA ECC remediation state machine,
 * an SLA deadline computed from the ECC severity matrix, and the three attribution columns
 * (`triaged_by` / `verified_by` / `risk_accepted_by`) an ECC reviewer reads to see who decided
 * what. `cyber.vulnerabilities` has none of that.
 *
 * F2-205 wired this one because `CyberPage`'s tab already renders the other, and the 36 rows
 * behind this endpoint had NO client at all — the three pages that display them were reading
 * six hard-coded rows out of `localStorage`. Which register should survive is an OWNER decision
 * (F2-196's two-registers-one-object question) and is filed, not resolved here: retiring either
 * one changes what the platform reports to the National Cybersecurity Authority.
 */
export interface VulnLifecycleDto {
  id: string;
  assetId: string;
  cveId?: string | null;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string | null;
  descriptionAr?: string | null;
  severity: string;
  cvssScore?: number | null;
  detectionSource?: string | null;
  status: string;
  detectedAt?: string | null;
  triagedAt?: string | null;
  triagedBy?: string | null;
  ownerAssignedAt?: string | null;
  owner?: string | null;
  remediationStartedAt?: string | null;
  remediationMethodEn?: string | null;
  remediationMethodAr?: string | null;
  verificationAt?: string | null;
  verifiedBy?: string | null;
  closedAt?: string | null;
  slaDeadline?: string | null;
  breachCount?: number | null;
  slaExtended?: boolean | null;
  riskAcceptedBy?: string | null;
  riskAcceptanceExpiry?: string | null;
  riskAcceptanceReasonEn?: string | null;
  riskAcceptanceReasonAr?: string | null;
  falsePositiveReasonEn?: string | null;
  falsePositiveReasonAr?: string | null;
  threatIntelId?: string | null;
}

export const vulnLifecycleApi = {
  /** The tenant's remediation register, newest detection first. `CYBER:READ`. */
  list: () => api.get<VulnLifecycleDto[]>('/api/cyber-lc/vulnerabilities'),

  /**
   * Move a vulnerability through the NCA ECC remediation machine, taking the row back from the
   * server so the caller never has to compute the new state.
   *
   * ⚠ There is deliberately no `triagedBy`, `verifiedBy` or `riskAcceptedBy` here: F2-22b(vi)
   * removed all three from `TransitionRequest` because the server stamps them from the
   * authenticated principal. Accepting a vulnerability's risk is an AUTHORITY claim — it is what
   * an ECC reviewer reads to see who chose to live with the exposure — so it is never the body's
   * to name. The reason fields ARE the caller's, because they are the deciding officer's words.
   */
  transition: (id: string, body: {
    status: string;
    owner?: string;
    remediationMethodEn?: string;
    remediationMethodAr?: string;
    riskAcceptanceExpiry?: string;
    riskAcceptanceReasonEn?: string;
    riskAcceptanceReasonAr?: string;
    falsePositiveReasonEn?: string;
    falsePositiveReasonAr?: string;
  }) => api.post<VulnLifecycleDto>(
    `/api/cyber-lc/vulnerabilities/${encodeURIComponent(id)}/transition`, body),

  // ⚠ `get`, `create` and `delete` are NOT wrapped. All three endpoints exist and are guarded,
  // but no screen opens a vulnerability detail view, creates one by hand (detection is what
  // creates these) or deletes one — and F2-201's gate leg fired on exactly this: an unused
  // client method should not ship. Add each in the commit that adds the screen calling it.
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
  // F2-214 — `description` was declared here and `DataSubjectRequest` has no such property
  // (fourteen fields, none of them free text). `POST|PUT /api/v1/privacy/dsrs` binds that
  // entity, so the DSR fulfilment wizard's composed narrative — including its identity
  // verification evidence — was discarded on every submission. Filed by F2-213, closed here
  // by the same gate that found the breach half. Do not re-add it without a column.
}
// ⚠⚠ F2-211: this interface declared `severity`, `occurredAt`, `reportedAt` and
// `affectedRecords`. `GET /api/v1/privacy/breaches` returns the DataBreach ENTITY,
// and `privacy.data_breaches` — read from sys.columns, 25 columns, identical in dev
// and prod — has NO COLUMN for any of the four. They could never arrive, so the
// register's Severity badge, Records count and Occurred date rendered an em-dash on
// all 17 live rows, its severity filter could never match a row, and the column
// titled with the PDPL Article 20 72-hour clock returned early on `!r.occurredAt`
// and so never once displayed a countdown against real data.
//
// ⚠ Two fields that LOOK identical in a wire probe are NOT dead and stay declared:
// `sdaiaSubmissionRef` and `sdaiaSubmittedAt` have real columns that are null on
// all 17 rows. `default-property-inclusion: non_null` renders "no column" and
// "column exists, every row null" identically, and they demand opposite fixes —
// only sys.columns adjudicates. Measuring from the wire alone called 6 fields dead
// here and 6 more on DpiaDto; the entity says 4 and 0.
export interface PrivacyBreachDto {
  id: number; titleEn: string; descriptionEn?: string;
  /**
   * F2-271 (V387) — the Arabic pair. Declared because the wire now carries it: F2-211's rule is
   * that the wire nominates and `sys.columns` adjudicates, and declaring a field the entity does
   * not have is exactly the defect that slice measured on this same register.
   */
  titleAr?: string; descriptionAr?: string;
  breachType?: string; status?: string;
  /** The affected-subject count the server actually keeps (17/17 non-null). */
  dataSubjectsAffectedCount?: number;
  /** Whether this breach requires authority notification at all (17/17 non-null). */
  pdpcNotificationRequired?: boolean;
  // SDAIA notification workflow (PDPL Article 31 — 72-hour notification)
  /** NOT NULL on the entity — the awareness stamp the statutory clock runs from. */
  discoveredAt?: string;
  sdaiaNotificationStatus?: string;   // NOT_REPORTED | IN_PREPARATION | SUBMITTED | ACKNOWLEDGED
  sdaiaSubmissionRef?: string | null;
  sdaiaSubmittedAt?: string | null;

  /**
   * F2-285 — cyber → PDPL provenance. The BREACH end of the bridge F2-281 mapped
   * the incident end of.
   *
   * Real columns on `privacy.data_breaches`, written by
   * `CyberPdplBridgeService`'s native SQL and read by NOTHING until F2-285 — zero
   * references in either tree outside that service. The bridge's own audit line
   * has always claimed the link is *bidirectional*; it writes both directions and
   * only one was readable, which F2-281 made visible by putting
   * `linkedDataBreachCode` on the incident's wire. The bridge log cannot answer it
   * either: all three of its finders key on `cyberIncidentId` and none on
   * `dataBreachId`.
   *
   * Read-only. `POST/PUT /api/v1/privacy/breaches` bind the ENTITY, so a writable
   * mapping would let a caller forge "this breach was auto-generated from cyber
   * incident N" — a provenance claim on a PDPL Article 20 record. Nothing here is
   * declared on a request type, and that is the point (F2-233).
   */
  /** True when the bridge raised this breach rather than a person recording it. */
  autoGenerated?: number;
  /** The bridge rule, e.g. `CYBER_TO_PDPL_V1`. */
  autoGenerationRule?: string | null;
  autoGeneratedAt?: string | null;
  sourceCyberIncidentId?: number | null;
  /** The originating incident's display reference, e.g. `INC-00001`. */
  sourceCyberIncidentNumber?: string | null;
}
/** Authoritative SDAIA submission status for a breach (GET sdaia-submission). */
export interface PrivacySdaiaStatus {
  breachId: number;
  discoveredAt?: string | null;
  sdaiaNotificationStatus?: string;
  sdaiaSubmissionRef?: string | null;
  sdaiaSubmittedAt?: string | null;
  /** discoveredAt + 72h — the PDPL Article 31 SDAIA notification deadline. */
  pdplArticle31DeadlineAt?: string | null;
}
export interface SdaiaSubmissionRequest {
  status?: string;            // SUBMITTED (default) | IN_PREPARATION | ACKNOWLEDGED
  submissionRef?: string;
  submittedAt?: string | null;
}
export interface PrivacyRopaDto {
  id: number; activityNameEn: string; activityNameAr?: string;
  purposeEn?: string; legalBasis?: string;
  /**
   * ⚠⚠ F2-210 — this was declared `retentionPeriod?: string` and the server has
   * never sent a key by that name. `GET /api/v1/privacy/ropa` returns the
   * `ProcessingActivity` entity, whose column is `retention_period_months`, so
   * the wire carries `retentionPeriodMonths` — probed live: **non-null on 11 of
   * 11 rows** (24 / 36 / 60 / 84 / 120), while `retentionPeriod` was non-null on
   * **0 of 11**.
   *
   * Three surfaces read the wrong name, and the third is the reason this is a
   * defect rather than a cosmetic slip:
   *   • `PrivacyPage`'s ROPA "Retention" column rendered an em-dash on every row
   *   • `RopaDetailPage`'s "Data retention → Period" rail card, likewise
   *   • `RopaDetailPage`'s **"Completeness check (SDAIA examiner expectations)"**
   *     panel, whose `<Check yes={!!item.retentionPeriod}>Retention period set`
   *     therefore reported UNCHECKED for every activity in the register.
   *
   * 🔑 PDPL Article 31 makes the retention period a mandatory ROPA field, so the
   * platform was telling a DPO its statutory register was incomplete on a point
   * where it is complete. Note the DIRECTION: F2-193, F2-195 and F2-205 all erred
   * flatteringly; this one errs pessimistically, which does not create false
   * assurance but does send someone to fix data that is already correct — and it
   * is far less likely to be reported, because "no data yet" is a plausible
   * reading of an empty column.
   */
  retentionPeriodMonths?: number; status?: string;
  /**
   * F2-198 — PDPL Article 29 cross-border transfer flag. `GET /api/v1/privacy/ropa` returns the
   * `ProcessingActivity` entity, so this has always been on the wire (probed: present on all 11
   * rows, 5 true) — this type simply never declared it, which is why the retired `/ropa` page
   * invented its own cross-border count instead of reading the register.
   */
  crossBorderTransfer?: boolean;
}
/**
 * F2-214 — the write twin of `PrivacyBreachDto`, corrected against the same entity.
 *
 * F2-211 adjudicated the READ types against `DataBreach` by reflection and retired
 * `severity` / `affectedRecords` / `occurredAt` / `reportedAt`. This type sat eight lines
 * below the one it corrected and kept every retired name, because a request type has no
 * wire to nominate from — nothing ever returns it — so it was outside that instrument by
 * construction. `POST|PUT /api/v1/privacy/breaches` binds the ENTITY, and Spring Boot
 * disables FAIL_ON_UNKNOWN_PROPERTIES, so each of those names was discarded in silence.
 *
 * Probed live 2026-08-15 with the exact payload `BreachNotificationWizard` sends:
 * **HTTP 400, "A required field was not supplied"** — `breach_type` is NOT NULL and the
 * wizard named `severity` instead. The entity's own names return 201. The PDPL Article 20
 * breach register has never been creatable from that wizard; all 17 rows are seeded.
 *
 * `pdpcNotifiedAt` is deliberately absent: it is stamped by `notifyDataOffice` and is the
 * evidence the data office was told, not a form field (F2-213's `completedAt` rule).
 * There is no `severity` column on `privacy.data_breaches` — do not re-add that one here.
 *
 * ⚠ This block used to read "and no `titleAr` column … the second is filed as a real gap against
 * house rule 7". **F2-271 closed that gap (V387).** `title_ar` and `description_ar` exist now,
 * nullable and width-mirrored against their English siblings, so both are declared below. They are
 * optional because nothing can invent Arabic governance text for the 17 rows that predate the
 * column (F2-30's reason for its own ratchet), not because they are decoration.
 */
export interface PrivacyBreachRequest {
  titleEn: string;
  /** F2-271 (V387). Optional — see the note above on why it is not mandatory. */
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  /** NOT NULL on privacy.data_breaches. Omitting it is a 400 that names no field. */
  breachType: string;
  discoveredAt?: string;
  dataSubjectsAffectedCount?: number | null;
  pdpcNotificationRequired?: boolean;
  status?: string;
}
/**
 * ⚠⚠ F2-210 — this type declared `processNameEn` / `processNameAr` /
 * `dataCategories` / `retentionPeriod`, and **the endpoint binds none of them**.
 * `POST /api/v1/privacy/ropa` takes `@RequestBody ProcessingActivity` — the
 * entity — whose properties are `activityNameEn`, `activityNameAr`, `purposeEn`,
 * `legalBasis`, `retentionPeriodMonths`, `dataCategoriesJson`, `status`.
 * Spring Boot disables `FAIL_ON_UNKNOWN_PROPERTIES`, so every unrecognised key
 * was silently discarded and `activity_name_en` (NOT NULL) arrived null.
 *
 * 🔑 Probed live with the exact payload `RopaWizard` sent: **HTTP 400,
 * "A required field was not supplied. Check the request body against the
 * endpoint's schema."** — a message that names no field, on a form where nothing
 * is in fact missing. The same probe with the entity's own names returns **201**.
 * So the PDPL Article 31 register's creation wizard has never been able to
 * create a record, which is why all 11 rows in dev and prod are seeded.
 *
 * ⚠ F2-198 and F2-207 both cited `RopaWizard` as the real creation path when
 * deciding to retire a fabricated ROPA surface — as evidence the real one was
 * complete. Neither probed the create. **A capability claim is the
 * least-verified thing in a filing**, and "there is a wizard" is not "the wizard
 * works".
 */
export interface PrivacyRopaRequest {
  activityNameEn: string; activityNameAr?: string;
  purposeEn?: string;
  /**
   * F2-278 — `PrivacyService.updateRopa` writes this UNCONDITIONALLY and this type did not
   * declare it, so a `PUT /ropa/{id}` would send null and null the Arabic purpose on every
   * edit. Exactly the defect F2-214 fixed for `crossBorderTransfer` eight lines below, in the
   * same method.
   *
   * ⚠ F2-272 created it: that slice added `setPurposeAr` to the whitelist so V388's column
   * would stop being permanently uneditable, and left the wire unable to carry it — turning
   * "silently discarded" into "nulled on every edit" for every caller that cannot send the
   * field, which is every caller. Its own comment reads "F2-214's shape, not F2-213's reset":
   * true of the code it replaced and false of the code it wrote.
   *
   * ⚠ Latent, and say so rather than implying otherwise: `purpose_ar` is populated on 0 of 11
   * live rows in dev and the column is ABSENT in prod (V388 is one of the 47 pending
   * migrations), and `updateRopa` has no caller — `apiClientMethodReachability` records it as
   * "HOLE: a RoPA record can be created and never corrected". That entry adjudicates the
   * CLIENT and says nothing about the ENDPOINT, so it reads as an invitation to build the
   * edit screen that would fire the erasure (F2-214's own words, about this very endpoint).
   *
   * ⚙ Declared and NOT collected by `RopaWizard`, which is the treatment `activityNameAr`
   * above already has: giving the wizard Arabic inputs is a product change, filed by F2-210
   * and F2-272, and is deliberately not smuggled into a wire fix.
   */
  purposeAr?: string;
  legalBasis?: string;
  retentionPeriodMonths?: number;
  dataCategoriesJson?: string;
  status?: string;
  /**
   * F2-214 — `PrivacyService.updateRopa` writes this unconditionally and this type did not
   * declare it, so `PUT /ropa/{id}` sent null and cleared the PDPL Article 29 cross-border
   * flag on every edit. Non-null on 11 of 11 live rows in dev and prod, 5 of them true —
   * and `PrivacyPage`'s cross-border tile (the one signal F2-198 preserved when it retired
   * the fabricated `/ropa` page) counts exactly this column.
   */
  crossBorderTransfer?: boolean;
}
export const privacyApi = {
  dsrs:           (page = 0, size = 50) => api.get<PageResponse<PrivacyDsrDto>>(`/api/v1/privacy/dsrs${pageQ(page, size)}`),
  getDsr:         (id: number) => api.get<PrivacyDsrDto>(`/api/v1/privacy/dsrs/${id}`),
  getBreach:      (id: number) => api.get<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}`),
  getRopa:        (id: number) => api.get<PrivacyRopaDto>(`/api/v1/privacy/ropa/${id}`),
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
  // SDAIA submission workflow (PDPL Article 31 — manual portal, recorded here)
  sdaiaStatus:    (id: number) => api.get<PrivacySdaiaStatus>(`/api/v1/privacy/breaches/${id}/sdaia-submission`),
  recordSdaiaSubmission: (id: number, body: SdaiaSubmissionRequest) =>
    api.post<PrivacyBreachDto>(`/api/v1/privacy/breaches/${id}/sdaia-submission`, body),
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
  // Policy register — legacy read-only archive (write lifecycle retired in C-4;
  // canonical register is policy.documents at /policy-management).
  policies:           (page = 0, size = 50) => api.get<PageResponse<PolicyDto>>(`/api/v1/compliance/policies${pageQ(page, size)}`),
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
  capaReopen:            (id: number, body: { reason: string }) =>
                            api.post<ComplianceFindingDto>(`/api/v1/compliance/findings/${id}/capa/reopen`, body),

  // External audit engagements — §5.3 recommendation #3
  auditEngagements:      (page = 0, size = 50) => api.get<PageResponse<AuditEngagementDto>>(`/api/v1/compliance/audit-engagements${pageQ(page, size)}`),
  createAuditEngagement: (body: AuditEngagementRequest) => api.post<AuditEngagementDto>('/api/v1/compliance/audit-engagements', body),
  updateAuditEngagement: (id: number, body: AuditEngagementRequest) => api.put<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}`, body),
  deleteAuditEngagement: (id: number) => api.delete<void>(`/api/v1/compliance/audit-engagements/${id}`),
  advanceAuditEngagement: (id: number, status: string) => api.post<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}/advance`, { status }),
  scheduleAuditFollowup:  (id: number, followupDate: string) => api.post<AuditEngagementDto>(`/api/v1/compliance/audit-engagements/${id}/schedule-followup`, { followupDate }),
  auditEngagementStats:   () => api.get<Record<string, number>>('/api/v1/compliance/audit-engagements/stats'),
};

// ─── Regulatory horizon scanner (Sprint-8 Slice 8-B) ─────────────────────────
// GET /api/v1/compliance/regulatory-horizon — tenant-wide snapshot of every
// not-yet-closed regulatory change bucketed by effective date, with authority
// + impact distributions for an executive horizon dashboard.
export type HorizonBucket = 'PAST_DUE' | 'IMMINENT' | 'NEAR_TERM' | 'QUARTER' | 'HORIZON';

export interface RegulatoryHorizonRowDto {
  id: number;
  sourceAuthority: string;        // SAMA / NCA / SDAIA / CMA / NCEMA / ZATCA / MOH / OTHER
  titleEn: string;
  titleAr?: string;
  citation?: string;
  effectiveDate?: string;
  daysUntilEffective: number;     // negative for PAST_DUE
  bucket: HorizonBucket;
  applicability: string;          // UNKNOWN / APPLICABLE / NOT_APPLICABLE / PARTIAL
  impactLevel: string;            // UNKNOWN / LOW / MEDIUM / HIGH / CRITICAL
  status: string;
  assignedToId?: number;
}
export interface RegulatoryHorizonDto {
  evaluatedOn: string;
  activeChangeCount: number;
  byBucket: Record<string, number>;
  bySourceAuthority: Record<string, number>;
  byImpactLevel: Record<string, number>;
  rows: RegulatoryHorizonRowDto[];
}
export const regulatoryHorizonApi = {
  scan: () => api.get<RegulatoryHorizonDto>('/api/v1/compliance/regulatory-horizon'),
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
  dpiaCount?: number;
  kriCount?: number;
  riskCount?: number; riskAsRiskFw?: number; riskAsCompFw?: number;
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
export interface FrameworkDpiaRowDto {
  id: number;
  titleEn: string; titleAr?: string;
  triggerReason?: string;
  status?: string;
  overallResidualRisk?: string;
  assessmentDate?: string; nextReviewDate?: string;
  clauseRef?: string;
}
export interface FrameworkKriRowDto {
  id: number; code?: string;
  nameEn: string; nameAr?: string;
  unit?: string; direction?: string;
  greenMax?: number; amberMax?: number;
  frequency?: string; owner?: string;
  clauseRef?: string;
}
export interface FrameworkRiskRowDto {
  id: number;
  titleEn: string; titleAr?: string;
  status?: string;
  inherentScore?: number; residualScore?: number;
  riskLevel?: string;
  matchedAsRiskFramework?: boolean;
  matchedAsComplianceFramework?: boolean;
  riskFrameworkClause?: string;
  complianceFrameworkClause?: string;
}
export const frameworkApi = {
  list:           ()                 => api.get<FrameworkDto[]>('/api/v1/compliance/frameworks'),
  policiesFor:    (frameworkId: number) => api.get<FrameworkPolicyRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/policies`),
  findingsFor:    (frameworkId: number) => api.get<FrameworkFindingRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/findings`),
  controlsFor:    (frameworkId: number) => api.get<FrameworkControlRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/controls`),
  dpiasFor:       (frameworkId: number) => api.get<FrameworkDpiaRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/dpias`),
  krisFor:        (frameworkId: number) => api.get<FrameworkKriRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/kris`),
  risksFor:       (frameworkId: number) => api.get<FrameworkRiskRowDto[]>(`/api/v1/compliance/frameworks/${frameworkId}/risks`),
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
// ⚠⚠ F2-212: these two types declared 9 of the 15 fields `IncidentResponse` sends and
// 8 of the 12 `IncidentRequest` accepts. The six the read side never mentioned —
// `resolutionDate`, `timelineJson`, `lessonsLearned`, `lessonsLearnedAr`, `createdAt`,
// `updatedAt` — are all real columns on `cyber.incidents`, all on the wire, and
// `lessons_learned` / `lessons_learned_ar` are populated on 3 of 44 live incidents in
// BOTH databases. Nothing could render them, so `IncidentsPage` built a lessons-learned
// register that DERIVED its text from the severity band and kept operator-authored
// entries in `localStorage` — beside a server that already stores and returns them.
// F2-198's shape (the wire carries it, the client never declares it) as the cause of
// F2-208's shape (a browser store shadowing a populated column).
export interface IncidentDto {
  id: number; title: string; titleAr?: string;
  severity: string; category?: string; status: string;
  assignee?: string; discoveryDate?: string;
  description?: string;
  /** Post-incident activity (NIST 800-61r2 §3.4). Real columns, on the wire. */
  resolutionDate?: string;
  timelineJson?: string;
  lessonsLearned?: string; lessonsLearnedAr?: string;
  createdAt?: string; updatedAt?: string;

  /**
   * PDPL classification (F2-281). Real columns on `cyber.incidents`, written by
   * the cyber -> PDPL bridge (`/api/privacy/cyber-bridge/trigger`) and declared
   * by nothing until F2-281 -- so an incident classified as affecting personal
   * data looked identical to one that was not, on every surface.
   *
   * Read-only: the bridge owns the writes, and `IncidentRequest` deliberately
   * declares none of these. A field the request type does not declare can never
   * arrive (F2-233), which is why there is no matching entry below.
   */
  affectsPersonalData?: boolean;
  /** Estimated affected data subjects. Absent means NOT ASSESSED, never zero. */
  dataSubjectsAffectedEst?: number;
  dataCategoriesAffected?: string;
  personalDataPiiAssessment?: string;
  linkedDataBreachId?: number;
  /** The linked breach's external id -- the reference an SDAIA filing cites. */
  linkedDataBreachCode?: string;
  piiClassifiedAt?: string;
  piiClassifiedBy?: number;
}
export interface IncidentRequest {
  title: string; titleAr?: string;
  severity: string; category?: string; status?: string;
  assignee?: string; discoveryDate?: string;
  description?: string;
  // The server's `IncidentRequest` has accepted these four since it was written, and
  // `IncidentService` persists them; the client simply never sent them.
  resolutionDate?: string;
  timelineJson?: string;
  lessonsLearned?: string; lessonsLearnedAr?: string;
}
export interface IncidentPhaseSnapshot {
  id: number; status: string; nistPhase: string;
  ncaHeyNotifiedAt?: string; dataOfficeNotifiedAt?: string;
  citcNotifiedAt?: string; samaNotifiedAt?: string; hrsdNotifiedAt?: string;
  mitreTactics?: string; mitreTechniques?: string;
  /**
   * F2-281 — this snapshot carried `dataOfficeNotifiedAt`, the PDPL Article 20
   * notification stamp, and not the classification that makes that notification
   * owed. Two of N, in one payload.
   */
  affectsPersonalData?: boolean;
  piiClassifiedAt?: string;
  linkedDataBreachCode?: string;
}
export interface RegulatoryTemplate {
  code: 'NCA_HEY' | 'DATA_OFFICE' | 'CITC' | 'SAMA' | 'HRSD';
  titleEn: string; titleAr: string;
  deadlineHours: number; hoursSinceDiscovery: number; hoursRemaining: number;
  overdue: boolean; notifiedAt?: string;
  submissionChannel: string; applicabilityHint: string;
  prefilledBody: string;
  /**
   * F2-281. Applicability was disclosed as prose for a human to judge --
   * correct for SAMA ("for SAMA-licensed financial institutions") and CITC,
   * which the platform cannot know, and wrong for PDPL Article 20, which is
   * exactly the question `affects_personal_data` answers and which the platform
   * had stored on the row all along.
   *
   * Carried, NOT acted on: every template is still offered and `overdue` is
   * unchanged, because turning "nobody has assessed this yet" into a clean bill
   * on a statutory clock is the failure an examiner writes up.
   */
  piiClassification: 'CONFIRMED' | 'NOT_CLASSIFIED';
}
export type SaudiRegulator = 'NCA_HEY' | 'DATA_OFFICE' | 'CITC' | 'SAMA' | 'HRSD';
/**
 * Mirrors `module/incident/dto/IncidentStats`.
 *
 * F2-195 — `stats` was typed `unknown` and had no caller, so the Incidents KPI strip rendered
 * `"4.2h"` for mean-time-to-resolve while this endpoint was already computing the real figure.
 * `avgDays` is the mean of `ChronoUnit.DAYS.between(discoveryDate, resolutionDate)` over **all**
 * resolved incidents (those carrying both dates) — so it is **days**, and the fabricated value was
 * not merely 26× low, it was in the wrong unit. (F2-345 — it was previously averaged over the 200
 * most-recently-created incidents, which windowed and downward-biased the figure at scale while
 * its sibling counts were all-time.)
 */
export interface IncidentStatsDto {
  total: number; open: number; investigating: number; resolved: number;
  critical: number; high: number;
  /** Mean days from discovery to resolution. Days, not hours. */
  avgDays: number;
}
export const incidentApi = {
  list:    (page = 0, size = 50) => api.get<PageResponse<IncidentDto>>(`/api/v1/incidents${pageQ(page, size)}`),
  get:     (id: number) => api.get<IncidentDto>(`/api/v1/incidents/${id}`),
  create:  (body: IncidentRequest) => api.post<IncidentDto>('/api/v1/incidents', body),
  update:  (id: number, body: IncidentRequest) => api.put<IncidentDto>(`/api/v1/incidents/${id}`, body),
  remove:  (id: number) => api.delete<void>(`/api/v1/incidents/${id}`),
  stats:   () => api.get<IncidentStatsDto>('/api/v1/incidents/stats'),
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
  /** When the evidence was actually collected (maps to collected_at). */
  collectedAt?: string;
  validFrom?: string; validTo?: string; isExpired?: boolean; createdAt?: string;
  fileName?: string; fileSize?: number; mimeType?: string;
}
export interface EvidenceItemRequest {
  titleEn: string; titleAr?: string; descriptionEn?: string;
  evidenceType: string; subControlId?: number;
  collectedDate?: string; expiryDate?: string;
}
export interface EvidenceRequestDto {
  id: number; requestCode: string;
  subControlCode?: string; frameworkCode?: string;
  /** Generic entity anchor (BCM plan / BIA / governance / crisis / CAPA, …). */
  entityType?: string; entityId?: number;
  titleEn: string; titleAr?: string; descriptionEn?: string;
  status?: string; priority?: string; requestedAt?: string; dueDate?: string;
  requestedById?: number; requestedByName?: string;
  assignedToId?: number; assignedToName?: string;
  departmentId?: number; departmentName?: string;
  notes?: string; createdAt?: string; updatedAt?: string;
  /** @deprecated legacy field — backend now returns assignedToName/requestedByName. */
  ownerName?: string;
}
export interface EvidenceRequestCommentDto {
  id: number; requestId: number;
  authorId?: number; authorName?: string;
  body: string; createdAt?: string;
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
  // F2-165 — `approve` / `reject` / `submit` deleted here. They POSTed the same
  // `/api/v1/evidence/items/{id}/…` routes as `lib/evidenceItems.ts`, whose
  // `approveEvidenceItem` / `rejectEvidenceItem` / `submitEvidenceItem` the screens
  // actually call; these three had no caller and were a second client for a live
  // endpoint. Read by name shape they looked like the four-eyes evidence control going
  // unreachable; read by ENDPOINT they were dead code. Pinned by
  // `apiClientMethodReachability.test.ts` — that invariant is a hard zero.
  uploadFile:   (id: number, file: File) => uploadFile<EvidenceItemDto>(`/api/v1/evidence/items/${id}/file`, file),
  stats:        () => api.get<unknown>('/api/v1/evidence/items/stats'),
  expired:      () => api.get<PageResponse<EvidenceItemDto>>('/api/v1/evidence/items/expired'),
  requests:     (page = 0, size = 50) => api.get<PageResponse<EvidenceRequestDto>>(`/api/v1/evidence/requests${pageQ(page, size)}`),
  /** All requests anchored to one generic aggregate (newest first). */
  requestsByEntity: (entityType: string, entityId: number) =>
    api.get<EvidenceRequestDto[]>(`/api/v1/evidence/requests/by-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),
  // Raise an evidence request against a department/owner with a due date; the
  // EvidenceRequestSlaScheduler then chases it (overdue reminders). subControlId
  // is optional — a request can be standalone, or anchored to any aggregate via
  // entityType/entityId (e.g. a BCM plan, BIA, governance policy, CAPA).
  createRequest: (body: { titleEn: string; titleAr?: string; descriptionEn?: string; subControlId?: number; entityType?: string; entityId?: number; assignedToId?: number; departmentId?: number; dueDate?: string; priority?: string; notes?: string }) =>
    api.post<EvidenceRequestDto>('/api/v1/evidence/requests', body),
  updateRequestStatus: (id: number, status: string) =>
    api.post<EvidenceRequestDto>(`/api/v1/evidence/requests/${id}/status?status=${encodeURIComponent(status)}`),
  deleteRequest: (id: number) => api.delete<void>(`/api/v1/evidence/requests/${id}`),
  /** Comment thread on an evidence request (plan task §12.9). */
  requestComments: (id: number) =>
    api.get<EvidenceRequestCommentDto[]>(`/api/v1/evidence/requests/${id}/comments`),
  addRequestComment: (id: number, body: string) =>
    api.post<EvidenceRequestCommentDto>(`/api/v1/evidence/requests/${id}/comments`, { body }),
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

// ─── Cyber cloud-service inventory (NCA CSCC §1.1) ───────────────────────────
// GET/POST/PATCH /api/v1/cyber/cloud-services — one row per (service × provider)
// the tenant uses, with residency, CSCC tier, criticality, and security posture.
// Distinct from /api/v1/outsourcing/cloud-services (SAMA materiality lens).
export interface CyberCloudServiceDto {
  id: number;
  externalId?: string;
  organisationId?: number;
  serviceName: string;
  provider: string;
  serviceModel: string;        // IAAS | PAAS | SAAS | FAAS | DBAAS
  deploymentModel: string;     // PUBLIC | PRIVATE | HYBRID | COMMUNITY
  dataClassification?: string; // PUBLIC | INTERNAL | CONFIDENTIAL | RESTRICTED | TOP_SECRET
  residencyRegion?: string;
  kingdomDataResident: boolean;
  csccTier?: string;
  contractId?: number;
  ownerUserId?: number;
  criticality: string;         // LOW | MEDIUM | HIGH | CRITICAL
  ssoEnabled: boolean;
  mfaEnforced: boolean;
  encryptionAtRest: boolean;
  encryptionInTransit: boolean;
  status: string;              // ACTIVE | UNDER_REVIEW | DEPROVISIONED | ARCHIVED
  lastReviewDate?: string;
  nextReviewDate?: string;
  notes?: string;
}
export const CLOUD_SERVICE_MODELS = ['IAAS', 'PAAS', 'SAAS', 'FAAS', 'DBAAS'];
// GOV_PRIVATE — the Saudi government private cloud. F2-238 measured it on 3 of the 15 live rows in
// dev AND prod (the National Government Cloud, the SDAIA/NDMO national data platform, and the
// sanctioned analytics platform) while both this list and the server's Set refused it. It renders
// verbatim in the register, so the omission cost nothing on screen; what it did cost is that the
// creation form could not record a government-cloud service as one.
export const CLOUD_DEPLOYMENT_MODELS = ['PUBLIC', 'PRIVATE', 'HYBRID', 'COMMUNITY', 'GOV_PRIVATE'];
export const CLOUD_DATA_CLASSES = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED', 'TOP_SECRET'];
export const CLOUD_CRITICALITIES = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];
export const CLOUD_STATUSES = ['ACTIVE', 'UNDER_REVIEW', 'DEPROVISIONED', 'ARCHIVED'];
export const cyberCloudServicesApi = {
  list:   () => api.get<CyberCloudServiceDto[]>('/api/v1/cyber/cloud-services'),
  get:    (id: number) => api.get<CyberCloudServiceDto>(`/api/v1/cyber/cloud-services/${id}`),
  create: (body: Partial<CyberCloudServiceDto>) => api.post<CyberCloudServiceDto>('/api/v1/cyber/cloud-services', body),
  update: (id: number, body: Partial<CyberCloudServiceDto>) => api.patch<CyberCloudServiceDto>(`/api/v1/cyber/cloud-services/${id}`, body),
  stats:  () => api.get<Record<string, number>>('/api/v1/cyber/cloud-services/stats'),
};

// ─── Threat-intel IOC correlation (Sprint-8 Slice 8-D) ───────────────────────
// POST /api/v1/cyber/threat-intel/correlate — match an IOC bundle (CVE / IP /
// HOSTNAME) against the tenant's cyber_assets + vulnerabilities. Pure read
// aggregator; no persistence. regulatorEscalation=true → SAMA Cyber Framework
// §3.5 4-hour notification clock should start. Distinct from the SOC-ops
// threat-intel module (/api/v1/socops/...).
export type IocType = 'CVE' | 'IP' | 'HOSTNAME';
export type IocSeverity = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFO';
export const IOC_TYPES: IocType[] = ['CVE', 'IP', 'HOSTNAME'];
export const IOC_SEVERITIES: IocSeverity[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW', 'INFO'];
export interface IndicatorOfCompromise {
  type: IocType | string;
  value: string;
  severity?: IocSeverity | string;
  sourceFeed?: string;
  context?: string;
}
export interface ThreatIntelRequest {
  feedName: string;
  feedTimestamp?: string;
  indicators: IndicatorOfCompromise[];
}
export interface CorrelationHit {
  iocType: string;
  iocValue: string;
  iocSeverity?: string;
  matchedEntityType: string;   // VULNERABILITY | ASSET
  matchedEntityId: number;
  matchedTitleEn?: string;
  matchedTitleAr?: string;
  matchedSeverity?: string;
  blendedSeverity: string;
  assetName?: string;
  sourceFeed?: string;
}
export interface CorrelationReport {
  feedName?: string;
  evaluatedAt: string;
  hits: CorrelationHit[];
  matchCount: number;
  distinctIocsMatched: number;
  distinctEntitiesMatched: number;
  regulatorEscalation: boolean;
}
export const threatIntelApi = {
  correlate: (body: ThreatIntelRequest) =>
    api.post<CorrelationReport>('/api/v1/cyber/threat-intel/correlate', body),
};

// ─── FAIR quantitative-risk calculator (Sprint-8 Slice 8-A) ──────────────────
// POST /api/v1/risk/fair/calculate — stateless Monte-Carlo over four triangular
// factors → annualised loss exposure (ALE) distribution. No DB, no persistence.
export interface FairTriangular { min: number; mostLikely: number; max: number; }
export interface FairInputs {
  scenarioName: string;
  threatEventFrequency: FairTriangular;  // events / year
  vulnerability: FairTriangular;         // [0, 1] — share of threat events that become losses
  primaryLoss: FairTriangular;           // SAR / event
  secondaryLoss: FairTriangular;         // SAR / event
  iterations?: number;                   // default 10_000, max 100_000
}
export interface FairResult {
  scenarioName: string;
  iterations: number;
  aleMean: number; aleP10: number; aleP50: number; aleP90: number; aleMax: number;
  lefMean: number;                       // mean Loss Event Frequency (events/yr)
  lmMean: number;                        // mean Loss Magnitude (SAR/event)
  aleHistogram: Record<string, number>;  // 10 buckets, key = "lo..hi"
  currency: string;
}
export const fairApi = {
  calculate: (body: FairInputs) => api.post<FairResult>('/api/v1/risk/fair/calculate', body),
};

// ─── Active sessions (refresh-token store) — self-service security ────────────
// GET/DELETE /api/v1/auth/sessions — list my active refresh-token sessions and
// revoke them individually or all at once ("sign out everywhere").
export interface AuthSessionDto {
  id: number;
  issuedAt: string;
  expiresAt: string;
  ipAddress?: string;
  userAgent?: string;
  revokedAt?: string;
  revokeReason?: string;
  active: boolean;
}
export const sessionsApi = {
  listMine:  () => api.get<AuthSessionDto[]>('/api/v1/auth/sessions'),
  revoke:    (id: number) => api.delete<{ id: number; status: string }>(`/api/v1/auth/sessions/${id}`),
  revokeAll: () => api.post<{ userId: number; revoked: number; status: string }>('/api/v1/auth/sessions/revoke-all'),
};

// ─── Departments (platform) — used to route an evidence request to an owner ──
export interface DepartmentDto {
  id: number; code?: string; nameEn: string; nameAr?: string;
}
export const departmentsApi = {
  list: () => api.get<DepartmentDto[]>('/api/v1/departments'),
};

// ─── In-house e-signature (sign-off / attestation) ───────────────────────────
export type SignIntent = 'APPROVE' | 'REVIEW' | 'ACKNOWLEDGE' | 'ATTEST' | 'REJECT';
export const SIGN_INTENTS: SignIntent[] = ['APPROVE', 'REVIEW', 'ACKNOWLEDGE', 'ATTEST', 'REJECT'];
export interface SignatureDto {
  id: number; entityType: string; entityId: number;
  intent: SignIntent; statementEn?: string;
  signerId?: number;
  // F2-277 — signerNameAr deliberately has no username fallback: the username is
  // language-neutral and already keeps the row identifiable through signerName.
  signerName?: string; signerNameAr?: string;
  signerRole?: string;
  payloadHash: string; signedAt?: string;
}
export const signaturesApi = {
  list: (entityType: string, entityId: number) =>
    api.get<SignatureDto[]>(`/api/v1/signatures/${entityType}/${entityId}`),
  sign: (entityType: string, entityId: number, body: { intent: SignIntent; statementEn?: string }) =>
    api.post<SignatureDto>(`/api/v1/signatures/${entityType}/${entityId}`, body),
};

// ─── Generic typed attachments — any entity can own files of a doc_type ──────
export type AttachmentDocType =
  'PLAN' | 'REPORT' | 'POLICY' | 'CERTIFICATE' | 'EVIDENCE'
  | 'CONTRACT' | 'MINUTES' | 'QUESTIONNAIRE' | 'SCREENSHOT' | 'OTHER';

export const ATTACHMENT_DOC_TYPES: AttachmentDocType[] = [
  'PLAN', 'REPORT', 'POLICY', 'CERTIFICATE', 'EVIDENCE',
  'CONTRACT', 'MINUTES', 'QUESTIONNAIRE', 'SCREENSHOT', 'OTHER',
];

/** NDMO-aligned data classification an attachment/evidence artifact may carry. */
export type AttachmentClassification = 'PUBLIC' | 'INTERNAL' | 'CONFIDENTIAL' | 'RESTRICTED';
export const ATTACHMENT_CLASSIFICATIONS: AttachmentClassification[] = [
  'PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED',
];

export interface AttachmentDto {
  id: number;
  entityType: string; entityId: number;
  docType: AttachmentDocType;
  titleEn?: string;
  fileName: string; fileSize?: number; mimeType?: string; sha256?: string;
  validTo?: string; classification?: AttachmentClassification | null;
  uploadedById?: number; createdAt?: string;
  version?: number; versionGroupId?: number;
  downloadUrl: string;
}

// ─── Generic control mapping — any entity ↔ many sub-controls (cross-framework) ──
export type ControlSatisfaction = 'FULL' | 'PARTIAL' | 'PLANNED';
export interface ControlLinkDto {
  subControlId: number;
  subControlCode?: string; subControlNameEn?: string; subControlNameAr?: string;
  controlCode?: string; frameworkCode?: string;
  satisfaction?: ControlSatisfaction;
}
export type EvidenceCoverage = 'COVERED' | 'PARTIAL' | 'GAP' | 'EXPIRED';
export interface ControlEvidenceDto {
  subControlId: number;
  subControlCode?: string; subControlNameEn?: string; subControlNameAr?: string;
  controlCode?: string; frameworkCode?: string;
  satisfaction?: ControlSatisfaction;
  evidenceGuidanceEn?: string; evidenceGuidanceAr?: string;
  total: number; approved: number; pendingReview: number; draft: number; rejected: number;
  expiringSoon: number; expired: number;
  coverage: EvidenceCoverage;
}
export const controlLinksApi = {
  get: (entityType: string, entityId: number) =>
    api.get<ControlLinkDto[]>(`/api/v1/control-links/${entityType}/${entityId}`),
  set: (entityType: string, entityId: number, subControlIds: number[], satisfaction?: Record<number, ControlSatisfaction>) =>
    api.put<ControlLinkDto[]>(`/api/v1/control-links/${entityType}/${entityId}`, { subControlIds, satisfaction }),
  evidence: (entityType: string, entityId: number) =>
    api.get<ControlEvidenceDto[]>(`/api/v1/control-links/${entityType}/${entityId}/evidence`),
};

export const attachmentsApi = {
  list: (entityType: string, entityId: number) =>
    api.get<AttachmentDto[]>(`/api/v1/attachments/${entityType}/${entityId}`),
  upload: (entityType: string, entityId: number, file: File,
           opts?: { docType?: AttachmentDocType; title?: string; validTo?: string; classification?: AttachmentClassification }) => {
    const q = new URLSearchParams();
    q.set('docType', opts?.docType ?? 'OTHER');
    if (opts?.title) q.set('title', opts.title);
    if (opts?.validTo) q.set('validTo', opts.validTo);
    if (opts?.classification) q.set('classification', opts.classification);
    return uploadFile<AttachmentDto>(`/api/v1/attachments/${entityType}/${entityId}?${q.toString()}`, file);
  },
  download: (a: AttachmentDto) => downloadFile(`/api/v1/attachments/${a.id}/download`, a.fileName),
  update: (id: number, body: { docType?: AttachmentDocType; titleEn?: string; validTo?: string; classification?: AttachmentClassification | '' }) =>
    api.patch<AttachmentDto>(`/api/v1/attachments/${id}`, body),
  newVersion: (id: number, file: File, opts?: { docType?: AttachmentDocType; title?: string; validTo?: string; classification?: AttachmentClassification }) => {
    const q = new URLSearchParams();
    if (opts?.docType) q.set('docType', opts.docType);
    if (opts?.title) q.set('title', opts.title);
    if (opts?.validTo) q.set('validTo', opts.validTo);
    if (opts?.classification) q.set('classification', opts.classification);
    const qs = q.toString();
    return uploadFile<AttachmentDto>(`/api/v1/attachments/${id}/version${qs ? '?' + qs : ''}`, file);
  },
  history: (id: number) => api.get<AttachmentDto[]>(`/api/v1/attachments/${id}/history`),
  remove: (id: number) => api.delete<void>(`/api/v1/attachments/${id}`),
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

// ─── Consent Records (PDPL Art. 5 ledger) ──────────────────────────────────
//
// One row per (data subject × purpose) consent grant. Append-mostly:
// create / withdraw / supersede only — editing a granted consent's legal
// terms requires issuing a new row with a bumped version.
export interface ConsentRecordDto {
  id: number;
  externalId?: string;
  dataSubjectRef: string;          // pseudonymous (hashed) — never raw PII
  purposeCode: string;
  purposeDescription?: string;
  legalBasis: string;              // CONSENT | LEGITIMATE_INTEREST | CONTRACT | LEGAL_OBLIGATION | VITAL_INTEREST | PUBLIC_TASK
  grantedAt: string;
  grantedVia: string;              // WEB_FORM | PAPER | IN_PERSON | API | EMAIL | OTHER
  validUntil?: string;
  withdrawnAt?: string;
  withdrawnReason?: string;
  evidenceId?: number;
  version: string;                 // e.g. "v1", "v2.3"
  language: string;                // "ar" | "en"
  status: 'ACTIVE' | 'EXPIRED' | 'WITHDRAWN' | 'SUPERSEDED';
}
export interface ConsentStatsDto {
  active?: number;
  expired?: number;
  withdrawn?: number;
  superseded?: number;
}
export const consentApi = {
  list:        (page = 0, size = 100) => api.get<PageResponse<ConsentRecordDto>>(`/api/v1/privacy/consents${pageQ(page, size)}`),
  get:         (id: number) => api.get<ConsentRecordDto>(`/api/v1/privacy/consents/${id}`),
  history:     (subjectRef: string) => api.get<ConsentRecordDto[]>(`/api/v1/privacy/consents/by-subject/${encodeURIComponent(subjectRef)}`),
  grant:       (body: Partial<ConsentRecordDto>) => api.post<ConsentRecordDto>('/api/v1/privacy/consents', body),
  withdraw:    (id: number, reason: string) => api.post<ConsentRecordDto>(`/api/v1/privacy/consents/${id}/withdraw`, { reason }),
  supersede:   (id: number, supersededByConsentId: number) =>
                 api.post<ConsentRecordDto>(`/api/v1/privacy/consents/${id}/supersede`, { supersededByConsentId }),
  stats:       () => api.get<ConsentStatsDto>('/api/v1/privacy/consents/stats'),
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
  // F2-431 — no `executedById`. workflow.action_log records who moved the instance, taken
  // from the principal; `ownerId` on startInstance stays, because an ASSIGNMENT is the
  // caller's to choose and an ATTRIBUTION is not (F2-226).
  executeTransition: (id: number, actionCode: string, comments?: string) =>
    api.post<WorkflowInstanceDto>(`/api/v1/workflow/instances/${id}/transitions`, { actionCode, comments }),
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

// ── Access Governance — RBAC console (roles / matrix / effective access) ────
//    Backend: com.tss.grc.module.iam.governance (P0). Reads IAM:READ,
//    mutations IAM:WRITE, retire IAM:DELETE.
export interface GovRoleDto {
  id: number; roleName: string; displayNameEn: string; displayNameAr?: string | null;
  systemRole: boolean; active: boolean; organisationId?: number | null;
  /**
   * F2-280 — the privileged-access classification, and it is the EFFECTIVE answer rather than
   * the stored `iam.roles.is_privileged` flag. Two server-side readers of "privileged"
   * disagreed (the break-glass "Privileged holders" KPI read the flag; the quarterly NCA ECC
   * 2-2 / SAMA CSF 3.3.5 review derived it from APPROVE/DELETE grants) and now share one
   * predicate — their union, because the flag carries a judgement the grants cannot express
   * and the grants cover roles a tenant creates, which the flag never could. Read-only: no
   * write path exists, because un-flagging a role narrows a privileged review and that is a
   * governance decision.
   */
  privileged: boolean;
}
export interface GovMatrixCell { module: string; action: string; permissionName: string; granted: boolean; }
export interface GovMatrixDto { roleId: number; roleName: string; systemRole: boolean; cells: GovMatrixCell[]; }
export interface GovEffectiveGrant { module: string; action: string; viaRoles: string[]; }
export interface GovEffectiveAccessDto {
  userId: number; username: string; platformAdmin: boolean; roles: string[];
  /** F2-62 — subset of `roles` that is deactivated: contributes no grants, no admin bypass. */
  inactiveRoles?: string[];
  grants: GovEffectiveGrant[];
}
export const governanceApi = {
  roles:           () => api.get<GovRoleDto[]>('/api/v1/iam/governance/roles'),
  createRole:      (b: { roleName: string; displayNameEn: string; displayNameAr?: string }) =>
    api.post<GovRoleDto>('/api/v1/iam/governance/roles', b),
  updateRole:      (id: number, b: { roleName: string; displayNameEn: string; displayNameAr?: string; isActive?: boolean }) =>
    api.put<GovRoleDto>(`/api/v1/iam/governance/roles/${id}`, b),
  deleteRole:      (id: number) => api.delete<void>(`/api/v1/iam/governance/roles/${id}`),
  // F2-56 — copies a shared (global) role into the tenant under a new name, carrying its
  // grants. Shared roles are one row for every tenant, so a tenant may not edit one; this
  // is how it gets a variant it owns and can tune.
  cloneRole:       (id: number, b: { roleName: string; displayNameEn: string; displayNameAr: string }) =>
    api.post<GovRoleDto>(`/api/v1/iam/governance/roles/${id}/clone`, b),
  matrix:          (id: number) => api.get<GovMatrixDto>(`/api/v1/iam/governance/roles/${id}/matrix`),
  saveMatrix:      (id: number, grants: { module: string; action: string }[]) =>
    api.put<GovMatrixDto>(`/api/v1/iam/governance/roles/${id}/matrix`, { grants }),
  effectiveAccess: (userId: number) =>
    api.get<GovEffectiveAccessDto>(`/api/v1/iam/governance/users/${userId}/effective-access`),
};

// ── Access Governance — assurance surface (P1–P3 + V294) ────────────────────
//    Requests (maker-checker + engine anchor), recertification campaigns,
//    SoD rules/violations, break-glass, analytics, evidence ZIP.
export interface GovAccessRequestDto {
  id: number; beneficiaryUserId: number; roleId: number;
  validFrom?: string | null; validTo?: string | null; justification: string;
  status: string; approvedBy?: number | null; decidedAt?: string | null;
  rejectionReason?: string | null; provisionedAt?: string | null;
  engineRequestId?: number | null; createdBy?: number | null; createdAt?: string;
}
export interface GovCampaignDto {
  id: number; titleEn: string; titleAr?: string | null; scope: string;
  scopeModule?: string | null; scopeUserId?: number | null; reviewerUserId?: number | null;
  windowStart: string; windowEnd: string; windowHijri?: string | null;
  status: string; closedAt?: string | null; exceptionNote?: string | null;
}
export interface GovCampaignView { campaign: GovCampaignDto; total: number; pending: number; }
export interface GovReviewItemDto {
  id: number; campaignId: number; userId: number; username: string;
  roleId: number; roleName: string; assignmentSource?: string | null;
  validTo?: string | null; decision: string; decidedBy?: number | null;
  decidedAt?: string | null; note?: string | null;
}
export interface GovSodRuleDto {
  id: number; ruleCode: string; ruleType: string;
  moduleA?: string | null; actionA?: string | null; moduleB?: string | null; actionB?: string | null;
  structuralKey?: string | null; rolePatternCsv?: string | null;
  severity: string; rationaleEn: string; rationaleAr?: string | null; isActive: boolean;
}
export interface GovSodViolationDto {
  id: number; ruleId: number; ruleCode: string; severity: string;
  userId: number; username: string; detailEn?: string | null;
  detectionSource: string; status: string; detectedAt: string;
  dispositionNote?: string | null; acceptedUntil?: string | null;
}
export interface GovBreakGlassDto {
  id: number; activatedBy: number; activatedByUsername: string; roleId: number;
  incidentRef: string; reason?: string | null; activatedAt: string; expiresAt: string;
  status: string; reviewStatus: string; reviewedBy?: number | null;
  reviewedAt?: string | null; reviewNote?: string | null;
}
export interface GovAnalyticsDto {
  privilegedHolders: number; dormantGrants: number; grantsExpiring30d: number;
  openSodViolations: number; breakGlassActive: number; breakGlassAwaitingReview: number;
  platformAdminActions30d: number;
  campaignCompletion: { id: number; titleEn: string; status: string; total: number; decided: number; completionPct: number }[];
  topGrantHolders: { username: string; grants: number }[];
}
export const governanceAssuranceApi = {
  requests:        (status?: string) =>
    api.get<GovAccessRequestDto[]>(`/api/v1/iam/governance/requests${status ? `?status=${status}` : ''}`),
  createRequest:   (b: { beneficiaryUserId: number; roleId: number; justification: string; validFrom?: string; validTo?: string }) =>
    api.post<GovAccessRequestDto>('/api/v1/iam/governance/requests', b),
  approveRequest:  (id: number, b?: { override?: boolean; overrideJustification?: string }) =>
    api.post<GovAccessRequestDto>(`/api/v1/iam/governance/requests/${id}/approve`, b ?? {}),
  rejectRequest:   (id: number, reason: string) =>
    api.post<GovAccessRequestDto>(`/api/v1/iam/governance/requests/${id}/reject`, { reason }),
  campaigns:       () => api.get<GovCampaignView[]>('/api/v1/iam/governance/campaigns'),
  createCampaign:  (b: { titleEn: string; titleAr?: string; scope: string; scopeModule?: string; windowStart: string; windowEnd: string; reviewerUserId?: number }) =>
    api.post<GovCampaignView>('/api/v1/iam/governance/campaigns', b),
  campaignItems:   (id: number) => api.get<GovReviewItemDto[]>(`/api/v1/iam/governance/campaigns/${id}/items`),
  decideItem:      (campaignId: number, itemId: number, decision: string, note?: string) =>
    api.post<GovReviewItemDto>(`/api/v1/iam/governance/campaigns/${campaignId}/items/${itemId}/decision`, { decision, note }),
  closeCampaign:   (id: number, exceptionNote?: string) =>
    api.post<GovCampaignView>(`/api/v1/iam/governance/campaigns/${id}/close`, exceptionNote ? { exceptionNote } : {}),
  sodRules:        () => api.get<GovSodRuleDto[]>('/api/v1/iam/governance/sod/rules'),
  sodViolations:   (status?: string) =>
    api.get<GovSodViolationDto[]>(`/api/v1/iam/governance/sod/violations${status ? `?status=${status}` : ''}`),
  sodScan:         () => api.post<{ violationsRaised: number }>('/api/v1/iam/governance/sod/scan', {}),
  disposition:     (id: number, b: { status: string; note: string; acceptedUntil?: string }) =>
    api.post<GovSodViolationDto>(`/api/v1/iam/governance/sod/violations/${id}/disposition`, b),
  breakGlass:      () => api.get<GovBreakGlassDto[]>('/api/v1/iam/governance/break-glass'),
  activateBreakGlass: (b: { incidentRef: string; reason?: string; validityHours?: number }) =>
    api.post<GovBreakGlassDto>('/api/v1/iam/governance/break-glass', b),
  reviewBreakGlass: (id: number, note: string) =>
    api.post<GovBreakGlassDto>(`/api/v1/iam/governance/break-glass/${id}/review`, { note }),
  analytics:       () => api.get<GovAnalyticsDto>('/api/v1/iam/governance/analytics'),
  evidencePackage: () =>
    downloadFile('/api/v1/iam/governance/evidence-package.zip', 'AccessGovernance_EvidencePackage.zip'),
};

// ── Green Finance (الصكوك الخضراء) — instruments / UoP / impact / reports ───
export interface GfInstrumentDto {
  id: number; instrumentCode: string; instrumentType: string; nameEn: string; nameAr?: string | null;
  isin?: string | null; amountSarMn: number; issuanceCostsSarMn?: number | null;
  shariahStructure?: string | null; shariahApprovalRef?: string | null;
  externalReviewer?: string | null; spoRating?: string | null; status: string;
  prospectusRef?: string | null;   // GF-5 (V298, REG-8)
  issueDate?: string | null; maturityDate?: string | null;   // GF-4 dual-calendar display
}
/** GF-1 — org-level attributed impact, double-count protected per project. */
export interface GfPortfolioImpactDto {
  tco2eAvoidedYr: number; mwhRenewableYr: number; waterM3SavedYr: number;
  estimateBasis: boolean; projectsCounted: number; projectsCapped: number;
  byInstrument: { instrumentId: number; instrumentCode: string;
    tco2eAvoidedYr: number; mwhRenewableYr: number; waterM3SavedYr: number }[];
  byProject: { projectId: number; projectCode: string;
    rawShareSum: number; countedShare: number; dedupApplied: boolean }[];
  /** GF-3 — deployment of raised proceeds (post-issuance instruments). */
  allocation: { netProceedsSarMn: number; allocatedSarMn: number;
    allocationRatioPct: number; instruments: number };
}
export interface GfProjectDto {
  id: number; projectCode: string; nameEn: string; nameAr?: string | null; category: string;
  totalCostSarMn: number; actualTco2eAvoidedYr?: number | null; actualImpactBasis?: string; status: string;
  evidenceItemId?: number | null;   // GF-2 (V298) — S-C bridge FK
}
export interface GfAllocationDto {
  id: number; instrumentId: number; projectId: number; amountSarMn: number;
  allocationDate: string; financingType: string; status: string; rejectionReason?: string | null;
}
export interface GfSummaryDto {
  instrumentId: number; instrumentCode: string; status: string;
  netProceedsSarMn: number; allocatedSarMn: number; unallocatedSarMn: number;
  allocationRatioPct: number; refinancingSharePct: number;
  tco2eAvoidedYr: number; mwhRenewableYr: number; waterM3SavedYr: number;
  intensityTco2ePerSarMn: number; approvedAllocations: number; pendingAllocations: number;
  estimateBasis: boolean;
}
export interface GfReportDto {
  id: number; instrumentId: number; periodYear: number; reportType: string;
  allocatedSarMn: number; allocationRatioPct: number; tco2eAvoidedYr: number; status: string;
}
export const greenFinanceApi = {
  instruments:   () => api.get<GfInstrumentDto[]>('/api/v1/sustainability/finance/instruments'),
  createInstrument: (b: Record<string, unknown>) =>
    api.post<GfInstrumentDto>('/api/v1/sustainability/finance/instruments', b),
  transition:    (id: number, status: string, shariahApprovalRef?: string) =>
    api.post<GfInstrumentDto>(`/api/v1/sustainability/finance/instruments/${id}/transition`, { status, shariahApprovalRef }),
  projects:      () => api.get<GfProjectDto[]>('/api/v1/sustainability/finance/projects'),
  createProject: (b: Record<string, unknown>) =>
    api.post<GfProjectDto>('/api/v1/sustainability/finance/projects', b),
  allocations:   (instrumentId: number) =>
    api.get<GfAllocationDto[]>(`/api/v1/sustainability/finance/instruments/${instrumentId}/allocations`),
  propose:       (b: { instrumentId: number; projectId: number; amountSarMn: number; allocationDate: string; financingType?: string }) =>
    api.post<GfAllocationDto>('/api/v1/sustainability/finance/allocations', b),
  approveAlloc:  (id: number) => api.post<GfAllocationDto>(`/api/v1/sustainability/finance/allocations/${id}/approve`, {}),
  rejectAlloc:   (id: number, reason: string) =>
    api.post<GfAllocationDto>(`/api/v1/sustainability/finance/allocations/${id}/reject`, { reason }),
  summary:       (instrumentId: number) =>
    api.get<GfSummaryDto>(`/api/v1/sustainability/finance/instruments/${instrumentId}/summary`),
  portfolioImpact: () =>
    api.get<GfPortfolioImpactDto>('/api/v1/sustainability/finance/portfolio-impact'),
  reports:       (instrumentId: number) =>
    api.get<GfReportDto[]>(`/api/v1/sustainability/finance/instruments/${instrumentId}/reports`),
  createReport:  (b: { instrumentId: number; periodYear: number; reportType?: string }) =>
    api.post<GfReportDto>('/api/v1/sustainability/finance/reports', b),
  approveReport: (id: number) => api.post<GfReportDto>(`/api/v1/sustainability/finance/reports/${id}/approve`, {}),
  publishReport: (id: number) => api.post<GfReportDto>(`/api/v1/sustainability/finance/reports/${id}/publish`, {}),
  // V295 — standalone per-report exports (shared tabular engine)
  reportXlsx:    (id: number, lang = 'en') =>
    downloadFile(`/api/v1/sustainability/finance/reports/${id}/export.xlsx?lang=${lang}`, 'GreenFinance_AllocationReport.xlsx'),
  reportDocx:    (id: number, lang = 'en') =>
    downloadFile(`/api/v1/sustainability/finance/reports/${id}/export.docx?lang=${lang}`, 'GreenFinance_AllocationReport.docx'),
  evidenceZip:   () => downloadFile('/api/v1/sustainability/finance/evidence-package.zip', 'GreenFinance_EvidencePackage.zip'),
};

// ── Green Portfolio (المحفظة الخضراء) + carbon credits ──────────────────────
export interface GpTaxonomyDto { id: number; code: string; nameEn: string; nameAr?: string | null; source: string; }
export interface GpAssetDto {
  id: number; assetCode: string; nameEn: string; nameAr?: string | null; sector?: string | null;
  exposureSarMn: number; taxonomyCode?: string | null; eligibilityStatus: string; proposedStatus?: string | null;
}
export interface GpSummaryDto {
  totalExposureSarMn: number; assessedExposureSarMn: number; eligibleExposureSarMn: number;
  greenAssetRatioPct: number; categoryMixSarMn: Record<string, number>;
  weightedDataQuality: number; financedEmissionsTco2e: number; assetLinkedEmissionsTco2e: number;
  assets: number; eligibleAssets: number; pendingTags: number;
}
export interface GpCreditDto {
  id: number; registry: string; serialRange: string; vintageYear: number;
  quantityTco2e: number; status: string; retirementDate?: string | null; retirementPurpose?: string | null;
  evidenceItemId?: number | null;   // GF-2 (V298) — S-C bridge FK (prefers this over certificate_ref text)
}
export interface GpOverlayDto {
  scenarioId: number; scenarioType: string; scenarioLossRatePct: number;
  byCategory: { taxonomyCode: string; exposureSarMn: number; stressedLossSarMn: number }[];
  totalEligibleExposureSarMn: number; totalStressedLossSarMn: number;
}
export const greenPortfolioApi = {
  taxonomy:  () => api.get<GpTaxonomyDto[]>('/api/v1/sustainability/portfolio/taxonomy'),
  assets:    () => api.get<GpAssetDto[]>('/api/v1/sustainability/portfolio/assets'),
  createAsset: (b: { assetCode: string; nameEn: string; nameAr?: string; sector?: string; exposureSarMn: number }) =>
    api.post<GpAssetDto>('/api/v1/sustainability/portfolio/assets', b),
  proposeTag:  (id: number, taxonomyCode: string, requestedStatus: string) =>
    api.post<GpAssetDto>(`/api/v1/sustainability/portfolio/assets/${id}/propose-tag`, { taxonomyCode, requestedStatus }),
  decideTag:   (id: number, approve: boolean) =>
    api.post<GpAssetDto>(`/api/v1/sustainability/portfolio/assets/${id}/decide-tag`, { approve }),
  summary:   () => api.get<GpSummaryDto>('/api/v1/sustainability/portfolio/summary'),
  credits:   () => api.get<GpCreditDto[]>('/api/v1/sustainability/portfolio/credits'),
  purchase:  (b: { registry: string; serialRange: string; vintageYear: number; quantityTco2e: number; priceSarPerT?: number }) =>
    api.post<GpCreditDto>('/api/v1/sustainability/portfolio/credits', b),
  retire:    (id: number, certificateRef: string, purpose: string) =>
    api.post<GpCreditDto>(`/api/v1/sustainability/portfolio/credits/${id}/retire`, { certificateRef, purpose }),
  overlay:   (scenarioId: number) =>
    api.get<GpOverlayDto>(`/api/v1/sustainability/portfolio/climate-overlay?scenarioId=${scenarioId}`),
};

// ── ESG regulatory horizon — targets / IFRS S1-S2 / NCEC permits / CBAM ─────
export interface EsgTargetDto {
  id: number; targetCode: string; titleEn: string; titleAr?: string | null; scope: string; unit: string;
  baselineYear: number; baselineValue: number; targetYear: number; targetValue: number;
  sgiAligned: boolean; status: string;
}
export interface EsgTargetViewDto {
  target: EsgTargetDto;
  actuals: { id: number; year: number; value: number }[];
  milestones: { id: number; year: number; value: number; labelEn?: string | null }[];
  expectedForLatestActual?: number | null; trajectory: string;
}
export interface IfrsReqDto {
  id: number; reqCode: string; standard: string; pillar: string; titleEn: string; titleAr?: string | null;
  status: string; coverageRef?: string | null; ownerName?: string | null;
}
export interface EnvPermitViewDto {
  permit: { id: number; permitNumber: string; facilityEn: string; facilityAr?: string | null;
    authority: string; permitType?: string | null; expiryDate: string; status: string };
  daysToExpiry: number;
}
export interface EnvViolationDto {
  id: number; permitId: number; violationDate: string; descriptionEn: string;
  severity: string; status: string; linkedCapaId?: number | null;
}
export interface CbamLineDto {
  id: number; cnGroup: string; productDescEn: string; periodYear: number; periodQuarter: number;
  exportVolumeTonnes: number; embeddedTco2ePerTonne: number; totalEmbeddedTco2e: number; dataBasis: string;
  dataBasisNote?: string | null;   // GF-6 (V299) — derive-from-GHG audit trail
}
/** GF-6 — derive-from-GHG suggestion: the factor + a full echo of the inputs. */
export interface CbamDerivationDto {
  year: number; sharePct: number; productionTonnes: number;
  totalS1S2Tco2e: number; recordsCounted: number;
  factorTco2ePerTonne: number; dataBasisNote: string;
}
export const esgHorizonApi = {
  targets:      () => api.get<EsgTargetViewDto[]>('/api/v1/sustainability/horizon/targets'),
  createTarget: (b: Record<string, unknown>) =>
    api.post<EsgTargetDto>('/api/v1/sustainability/horizon/targets', b),
  addActual:    (id: number, year: number, value: number) =>
    api.post('/api/v1/sustainability/horizon/targets/' + id + '/actuals', { year, value }),
  addMilestone: (id: number, year: number, value: number, labelEn?: string) =>
    api.post('/api/v1/sustainability/horizon/targets/' + id + '/milestones', { year, value, labelEn }),
  trajectorySweep: () =>
    api.post<{ capasRaised: number }>('/api/v1/sustainability/horizon/targets/trajectory-sweep', {}),
  ifrs:         () => api.get<IfrsReqDto[]>('/api/v1/sustainability/horizon/ifrs'),
  ifrsSummary:  () => api.get<{ total: number; covered: number; partial: number; gap: number; coveragePct: number }>(
    '/api/v1/sustainability/horizon/ifrs/summary'),
  setIfrsStatus: (id: number, status: string, coverageRef?: string, ownerName?: string) =>
    api.post<IfrsReqDto>(`/api/v1/sustainability/horizon/ifrs/${id}/status`, { status, coverageRef, ownerName }),
  permits:      () => api.get<EnvPermitViewDto[]>('/api/v1/sustainability/horizon/env-permits'),
  createPermit: (b: Record<string, unknown>) =>
    api.post('/api/v1/sustainability/horizon/env-permits', b),
  permitSweep:  (leadDays = 90) =>
    api.post<{ capasRaised: number }>(`/api/v1/sustainability/horizon/env-permits/expiry-sweep?leadDays=${leadDays}`, {}),
  violations:   (permitId: number) =>
    api.get<EnvViolationDto[]>(`/api/v1/sustainability/horizon/env-permits/${permitId}/violations`),
  recordViolation: (permitId: number, b: { violationDate: string; descriptionEn: string; descriptionAr?: string; severity?: string }) =>
    api.post<EnvViolationDto>(`/api/v1/sustainability/horizon/env-permits/${permitId}/violations`, b),
  cbam:         () => api.get<CbamLineDto[]>('/api/v1/sustainability/horizon/cbam'),
  createCbam:   (b: Record<string, unknown>) => api.post('/api/v1/sustainability/horizon/cbam', b),
  cbamSummary:  () => api.get<{ totalEmbeddedTco2e: number; byGroup: Record<string, number>; lines: number }>(
    '/api/v1/sustainability/horizon/cbam/summary'),
  cbamDerive:   (year: number, sharePct: number, productionTonnes: number) =>
    api.get<CbamDerivationDto>(
      `/api/v1/sustainability/horizon/cbam/derive?year=${year}&sharePct=${sharePct}&productionTonnes=${productionTonnes}`),
  // V295 — ISAE 3410 assurance-lineage view
  assuranceLineage: (year: number) =>
    api.get<AssuranceLineageDto>(`/api/v1/sustainability/horizon/assurance-lineage?year=${year}`),
};

export interface AssuranceLineageRow {
  objectType: string; code: string; nameEn: string;
  hasData: boolean; hasEvidence: boolean; verified?: boolean | null; grade: string;
}
export interface AssuranceLineageDto {
  year: number; total: number; complete: number; partial: number; missing: number;
  readinessPct: number;
  byType: { objectType: string; total: number; complete: number; partial: number; missing: number }[];
  rows: AssuranceLineageRow[];
}

// ── Workflow submissions — generic ledger for the 12 wizards without ─────
//    dedicated tables (Risk Treatment, Risk Acceptance, Incident RCA,
//    Management Review, Digital Signature, Control Test, Cloud CCC, NCA
//    ECC Self-Assessment, Consent Management, Cross-Border Transfer,
//    Supplier Exit Plan, Board Pack). Backed by V125 workflow.workflow_submissions.
export interface WorkflowSubmissionDto {
  id: number;
  externalId?: string;
  workflowId: string;
  workflowModule: string;
  titleEn: string;
  titleAr?: string;
  status: string;
  summaryEn?: string;
  summaryAr?: string;
  regulatoryAnchor?: string;
  payloadJson: string;
  submittedAt: string;
  reviewedAt?: string;
  reviewerNote?: string;
  spawnedRequestId?: number | null;   // G1 — the engine request this submission was routed as
}
export interface WorkflowSubmissionRequest {
  workflowId: string;
  workflowModule: string;
  titleEn: string;
  titleAr?: string;
  summaryEn?: string;
  summaryAr?: string;
  regulatoryAnchor?: string;
  status?: string;
  payloadJson: string;
}
/**
 * Per-wizard aggregate counts for the Workflow Hub.
 *
 * One entry per catalog workflowId the tenant has at least one submission for.
 * Backed by a single tenant-scoped JPQL aggregate — replaces the client-side
 * aggregation in `WorkflowHubPage` that loaded every submission row on every
 * Hub visit.
 */
export interface WorkflowSubmissionCountDto {
  workflowId: string;
  total:      number;   // all statuses
  open:       number;   // SUBMITTED + UNDER_REVIEW
  approved:   number;   // APPROVED
}
export const workflowSubmissionApi = {
  create:  (body: WorkflowSubmissionRequest) =>
    api.post<WorkflowSubmissionDto>('/api/v1/workflow-submissions', body),
  list:    (workflowId?: string, module?: string) => {
    const qs = workflowId ? `?workflowId=${encodeURIComponent(workflowId)}`
            : module      ? `?module=${encodeURIComponent(module)}`
            : '';
    return api.get<WorkflowSubmissionDto[]>(`/api/v1/workflow-submissions${qs}`);
  },
  get:     (id: number) =>
    api.get<WorkflowSubmissionDto>(`/api/v1/workflow-submissions/${id}`),
  review:  (id: number, body: { decision: string; reviewerNote?: string }) =>
    api.post<WorkflowSubmissionDto>(`/api/v1/workflow-submissions/${id}/review`, body),
  /** G1 — persist the primary engine request this submission was routed as. */
  linkSpawnedRequest: (id: number, requestId: number) =>
    api.put<WorkflowSubmissionDto>(`/api/v1/workflow-submissions/${id}/spawned-request/${requestId}`),
  countsByWorkflow: () =>
    api.get<WorkflowSubmissionCountDto[]>('/api/v1/workflow-submissions/counts/by-workflow'),
};

// ── Audit log — F7 ─────────────────────────────────────────────────────
//    The unified platform audit-log surface. WorkflowSubmissionService
//    (and every other state-changing service) writes here on each
//    CREATE / APPROVE / REJECT / CLOSE. The detail-page Activity card
//    calls historyFor(...) to render forensic history inline.
export interface AuditLogEntryDto {
  id: number;
  externalId?: string;
  userId?: number | null;
  userUsername?: string | null;
  userRole?: string | null;
  actionType: string;
  entityType?: string | null;
  entityId?: number | null;
  entityCode?: string | null;
  newValueJson?: string | null;
  sourceModule?: string | null;
  severity: string;
  message?: string | null;
  createdAt: string;
  sourceEndpoint?: string | null;
  ipAddress?: string | null;
  workflowStatusBefore?: string | null;
  workflowStatusAfter?: string | null;
}
export interface AuditLogPageDto {
  content: AuditLogEntryDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
export interface AuditLogFilters {
  actionType?: string;
  sourceModule?: string;
  severity?: string;
  page?: number;
  size?: number;
}
export const auditLogApi = {
  historyFor: (entityType: string, entityId: number) =>
    api.get<AuditLogEntryDto[]>(
      `/api/v1/audit-log/history?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),
  /** Platform-wide audit log — filterable + paginated (F7). */
  list: (f: AuditLogFilters = {}) => {
    const q = new URLSearchParams();
    if (f.actionType)   q.set('actionType', f.actionType);
    if (f.sourceModule) q.set('sourceModule', f.sourceModule);
    if (f.severity)     q.set('severity', f.severity);
    q.set('page', String(f.page ?? 0));
    q.set('size', String(f.size ?? 100));
    return api.get<AuditLogPageDto>(`/api/v1/audit-log?${q.toString()}`);
  },
  /** Rolling 24-hour counts: { events, warns, errors }. */
  stats: () => api.get<Record<string, number>>(`/api/v1/audit-log/stats`),
  /** Follow one business action across modules via its correlation id. */
  trace: (correlationId: string) =>
    api.get<AuditLogEntryDto[]>(`/api/v1/audit-log/trace/${encodeURIComponent(correlationId)}`),
};

// ── Brand theme — per-tenant branding persisted server-side so exports carry it ──
//    Backend: com.tss.grc.module.platform.branding (BrandThemeController).
//    GET open to any authenticated user; PUT requires PLATFORM:WRITE.
export interface BrandThemeDto {
  orgNameEn: string;
  orgNameAr: string;
  taglineEn: string;
  taglineAr: string;
  primaryHex: string;
  accentHex: string;
  logoDataUrl?: string | null;
  customised?: boolean;
}
export const brandThemeApi = {
  get:  () => api.get<BrandThemeDto>('/api/v1/platform/brand-theme'),
  save: (body: Partial<BrandThemeDto>) => api.put<BrandThemeDto>('/api/v1/platform/brand-theme', body),
};

// ── Issues — generic cross-module finding tracker ──────────────────────
//    Backend: com.tss.grc.module.platform (IssueController + IssueService).
//    Lifecycle OPEN → IN_PROGRESS → (BLOCKED →)? RESOLVED → CLOSED, or
//    OPEN → CANCELLED. resolution required on RESOLVED/CLOSED.
export interface IssueDto {
  id: number;
  externalId?: string;
  issueCode?: string | null;
  titleEn: string;
  titleAr?: string | null;
  description?: string | null;
  sourceModule: string;
  sourceEntityType?: string | null;
  sourceEntityId?: number | null;
  severity: string;
  priority: string;
  assigneeUserId?: number | null;
  assigneeRole?: string | null;
  dueDate?: string | null;
  status: string;
  resolution?: string | null;
  resolutionNotes?: string | null;
  resolvedAt?: string | null;
  closedAt?: string | null;
  capaId?: number | null;
  evidenceId?: number | null;
  createdAt?: string;
}
export interface IssuePageDto {
  content: IssueDto[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
/** Allowed status transitions — mirrors IssueService's state machine. */
export const ISSUE_TRANSITIONS: Record<string, string[]> = {
  OPEN:        ['IN_PROGRESS', 'BLOCKED', 'RESOLVED', 'CANCELLED'],
  IN_PROGRESS: ['BLOCKED', 'RESOLVED', 'CANCELLED'],
  BLOCKED:     ['IN_PROGRESS', 'RESOLVED', 'CANCELLED'],
  RESOLVED:    ['CLOSED', 'IN_PROGRESS'],
  CLOSED:      [],
  CANCELLED:   [],
};
export const ISSUE_RESOLUTIONS = ['FIXED', 'WONT_FIX', 'DUPLICATE', 'NOT_REPRODUCIBLE', 'DEFERRED'];
export const ISSUE_SOURCE_MODULES = ['AUDIT', 'CYBER', 'BCM', 'RISK', 'COMPLIANCE', 'SCM', 'TPRM', 'PRIVACY', 'MANUAL'];
export const issuesApi = {
  list:       (page = 0, size = 50) => api.get<IssuePageDto>(`/api/v1/issues?page=${page}&size=${size}`),
  bySource:   (module: string) => api.get<IssueDto[]>(`/api/v1/issues/by-source/${encodeURIComponent(module)}`),
  get:        (id: number) => api.get<IssueDto>(`/api/v1/issues/${id}`),
  create:     (body: Partial<IssueDto>) => api.post<IssueDto>('/api/v1/issues', body),
  transition: (id: number, to: string, resolution?: string) =>
    api.post<IssueDto>(`/api/v1/issues/${id}/transition`, { to, resolution }),
  stats:      () => api.get<Record<string, number>>('/api/v1/issues/stats'),
};

// ── Automation rule runs — F8 ─────────────────────────────────────────
//    PR #21 wired wizard submissions into the F8 engine + V127 seeded
//    AR-21..AR-25. The submission detail page reads runs/for-entity to
//    show "which automation rules fired for this submission".
export interface AutomationRuleRunDto {
  id: number;
  ruleId: number;
  firedAt: string;
  triggerEvent: string;
  entityType?: string | null;
  entityId?: number | null;
  outcome: string;          // SUCCESS | FAILED | SKIPPED
  errorMessage?: string | null;
  resultSummary?: string | null;
}
export const automationApi = {
  runsForEntity: (entityType: string, entityId: number) =>
    api.get<AutomationRuleRunDto[]>(
      `/api/v1/automation/runs/for-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),
};

// ── CAPA tasks ─────────────────────────────────────────────────────────
//    F2-208 — this client is now the ONLY source of CAPA rows.
//
//    It used to be list-only, and `useCapas()` merged what it returned with a
//    twenty-row corpus in the browser plus a localStorage overlay of "added"
//    rows and per-row "patches". So the register showed 34 rows where 14
//    existed, `create()` wrote nowhere, and advancing a CAPA — including the
//    independent-verification sign-off — wrote a patch that was applied on top
//    of the REAL server rows on render. `POST /api/capa/tasks` and
//    `POST /{id}/transition` had existed the whole time; the second even stamps
//    the verifier from the authenticated principal (F2-22b(vi)) because a
//    caller-supplied verifier name is a governance defect.
//
//    Each row carries `numericId` (the Long PK) so EntityActivityTimeline +
//    EntityAutomationRuns can fetch the real audit_log / rule_run rows on the
//    detail page — which is also why no local event history is kept any more.
export interface CapaCountsDto {
  /** Non-terminal CAPAs (everything except CLOSED + CANCELLED). */
  open:    number;
  /** Open AND past target date. Always ≤ open. */
  overdue: number;
}
/** Mirrors {@code module/capa/dto/CapaTaskDto.java}. */
export interface CapaTaskDto {
  /** Human-readable externalId (e.g. "CAPA-2026-001"). */
  id:        string;
  /** Internal Long PK used by audit-log + automation lookups. */
  numericId?: number;
  originType: string;
  originId:   string;
  titleEn:    string;
  titleAr:    string;
  descriptionEn?: string;
  descriptionAr?: string;
  capaType:   string;   // 'CORRECTIVE' | 'PREVENTIVE'
  severity?:  string;   // 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL'
  owner:      string;
  targetDate: string;   // ISO date
  status?:    string;   // 'OPEN' | 'IN_PROGRESS' | 'AWAITING_VERIFICATION' | 'CLOSED' | 'CANCELLED'
  startedAt?:                   string | null;
  submittedForVerificationAt?:  string | null;
  verifiedAt?:                  string | null;
  verifiedBy?:                  string | null;
  closedAt?:                    string | null;
  cancelledAt?:                 string | null;
  cancellationReasonEn?:        string | null;
  progressNotesEn?:             string | null;
  progressNotesAr?:             string | null;
  evidenceRefs?:                string | null;
  /** When the CAPA was raised — `capa.tasks.created_at`, server-stamped. */
  createdAt?:                   string | null;
}
/**
 * ⚠ There is deliberately NO create wrapper here, and the reason is worth reading
 * before adding one.
 *
 * `POST /api/capa/tasks` exists and is guarded `CAPA:WRITE`, but no screen creates
 * a CAPA and none should through this client: the module's front door is the
 * intake substrate (DD-76 — a CONTROL_OBSERVATION submission is triaged into a
 * finding, and a CAPA follows from the finding), and the four non-seeded rows in
 * `capa.tasks` were all raised server-side by other modules' automation
 * (`origin_type` = `rca`, `processor_assessment`).
 *
 * F2-208 nearly shipped a `create` here to replace the deleted browser-local one.
 * It would have had exactly one caller — a modal fed by a fabricated
 * preventive-action trigger engine reading mock KRIs — which is worse than none:
 * an unused client method should not ship (F2-207), and a real write driven by an
 * invented signal is worse than a fake write driven by one.
 *
 * If a create screen is genuinely wanted, add the wrapper in that commit, and see
 * the filed finding first: `/capa`'s "New CAPA" button opens the intake form for a
 * control observation, which lands in a DIFFERENT register.
 */
/**
 * Body for `POST /api/capa/tasks/{id}/transition`. `status` is the TARGET state and
 * is validated server-side against the machine — an illegal edge is a 409, not a
 * silent no-op.
 *
 * No `verifiedBy`: the server stamps the verifier from the authenticated principal
 * and the field was removed from the request DTO for that reason. Do not add one.
 */
export interface CapaTaskTransitionRequest {
  status: 'OPEN' | 'IN_PROGRESS' | 'AWAITING_VERIFICATION' | 'CLOSED' | 'CANCELLED';
  cancellationReasonEn?: string;
  progressNotesEn?: string;
  progressNotesAr?: string;
  evidenceRefs?: string;
}
export const capaTaskApi = {
  counts:   () => api.get<CapaCountsDto>('/api/capa/tasks/counts'),
  list:     () => api.get<CapaTaskDto[]>('/api/capa/tasks'),
  transition: (id: string, body: CapaTaskTransitionRequest) =>
    api.post<CapaTaskDto>(`/api/capa/tasks/${encodeURIComponent(id)}/transition`, body),
};

// ── Compliance Calendar (PR #60 backend) ──────────────────────────────
//    Saudi-workdays-aware unified calendar feed: stitched from persisted
//    user events + Saudi public holidays + auto-aggregated rows from
//    dated GRC entities. See module/platform/calendar/.
export interface CalendarEventDto {
  /** Persisted PK for USER rows; null for AGGREGATED rows. */
  id?:        number | null;
  /** Stable identifier — synthetic-but-deterministic for AGGREGATED rows. */
  externalId: string;
  /** USER (persisted) | AGGREGATED (synthesised). */
  source:     'USER' | 'AGGREGATED';
  titleEn:    string;
  titleAr?:   string | null;
  descriptionEn?: string | null;
  descriptionAr?: string | null;
  /** Matches V133 CHECK constraint set — see EVENT_TYPE_LABEL in
   *  lib/saudiCalendar.ts for the 14 categories. */
  eventType:  string;
  /** ISO date-time (UTC). */
  startsAt:   string;
  endsAt?:    string | null;
  allDay?:    boolean;
  location?:  string | null;
  ownerId?:   number | null;
  /** Pre-resolved label for UI display (saves a second roundtrip). */
  ownerLabel?: string | null;
  severity?:  string | null;
  /** Linked GRC entity — drives navigate-on-click. */
  entityType?: string | null;
  entityId?:   number | null;
  reminderDays?: string | null;
  recurrence?:  string | null;
  recurrenceUntil?: string | null;
  isHoliday?:  boolean;
  status?:     string | null;
  /** Server-computed Saudi workday flag — UI uses for the
   *  "Falls on weekend" warning chip. */
  onWorkday?:  boolean;
}
export interface CalendarEventCreateRequest {
  titleEn:        string;
  titleAr?:       string;
  descriptionEn?: string;
  descriptionAr?: string;
  eventType:      string;
  startsAt:       string;
  endsAt?:        string;
  allDay?:        boolean;
  location?:      string;
  ownerId?:       number;
  severity?:      string;
  entityType?:    string;
  entityId?:      number;
  reminderDays?:  string;
  recurrence?:    string;
  recurrenceUntil?: string;
}
export const calendarApi = {
  /** Outlook-equivalent feed for [from, to). {@code types} is an
   *  optional comma-separated event-type filter (e.g.
   *  "CAPA_DUE,FINDING_DEADLINE,PUBLIC_HOLIDAY"). */
  list:  (from: string, to: string, types?: string) =>
    api.get<CalendarEventDto[]>(
      `/api/v1/calendar/events?from=${from}&to=${to}${types ? `&types=${encodeURIComponent(types)}` : ''}`),
  /** "On the calendar" panel — persisted events linked to entity. */
  forEntity: (entityType: string, entityId: number) =>
    api.get<CalendarEventDto[]>(
      `/api/v1/calendar/events/for-entity?entityType=${encodeURIComponent(entityType)}&entityId=${entityId}`),
  get:    (externalId: string) =>
    api.get<CalendarEventDto>(`/api/v1/calendar/events/${externalId}`),
  create: (req: CalendarEventCreateRequest) =>
    api.post<CalendarEventDto>('/api/v1/calendar/events', req),
  update: (externalId: string, req: CalendarEventCreateRequest) =>
    api.put<CalendarEventDto>(`/api/v1/calendar/events/${externalId}`, req),
  remove: (externalId: string) =>
    api.delete<void>(`/api/v1/calendar/events/${externalId}`),
};

// ─── Notifications ───────────────────────────────────────────────────

/**
 * One row from the notification inbox. Backend produces these via the
 * F8 NOTIFY/ESCALATE action handlers — each notification represents a
 * single user-addressed message generated when an automation rule fires
 * for a role the user holds.
 */
export interface NotificationDto {
  id: number;
  titleEn: string;
  titleAr: string | null;
  messageEn: string;
  messageAr: string | null;
  notificationType: string | null;
  link: string | null;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPage {
  content: NotificationDto[];
  totalElements: number;
  totalPages: number;
  number: number;          // 0-indexed page
  size: number;
}

export const notificationApi = {
  /** Current user's notifications, newest first. Spring Page format. */
  list: (page = 0, size = 20) =>
    api.get<NotificationPage>(`/api/v1/notifications?page=${page}&size=${size}`),
  /** Unread badge count for the sidebar / shell. */
  unreadCount: () =>
    api.get<{ count: number }>('/api/v1/notifications/unread-count'),
  /** Mark a single notification as read. */
  markRead: (id: number) =>
    api.patch<void>(`/api/v1/notifications/${id}/read`, {}),
  /** Mark every notification owned by the current user as read. */
  markAllRead: () =>
    api.patch<void>('/api/v1/notifications/read-all', {}),
};

// ── Intake (the front door) — F2-172 ──────────────────────────────────────
/**
 * The intake substrate's server client.
 *
 * <p>Until F2-172 nothing in the product called `/api/v1/intake`. `IntakeController` and
 * `IntakeService` were built at DD-82, were reachable, and had never been invoked: probed
 * live on 2026-08-09 the inbox answered 200 with `totalElements: 0` and
 * `intake.intake_records` held 0 rows, while `lib/intake.ts` drove the whole
 * submit → triage → promote lifecycle in `localStorage` (F2-171). So a submission was
 * invisible to every other user and died with the browser's site data.
 *
 * Two things had to be true on the server before this client could exist, and neither was:
 * the server accepted 16 of the frontend's 26 intake kinds, and `POST` was guarded by
 * `INTAKE:WRITE` — held by 3 of 69 roles, all administrators. See `IntakeKindRouting` and
 * `IntakeSubmitGuardTest`.
 *
 * `payload` crosses the wire as a JSON *string* (the column is NVARCHAR(MAX)); the mapping
 * in `lib/intake.ts` owns the parse so no page has to know.
 */
export interface IntakeRecordWire {
  id: number;
  referenceCode: string;
  kind: string;
  status: string;
  channel: string;
  submitterIsAnonymous: boolean;
  submitterUserId: number | null;
  submitterRole: string | null;
  submitterName: string | null;
  submitterEmail: string | null;
  submitterPhone: string | null;
  payload: string | null;
  assignedTo: string | null;
  slaDueAt: string | null;
  severity: string | null;
  acknowledgedAt: string;
  promotedToEntityType: string | null;
  promotedToRecordId: string | null;
  promotedAt: string | null;
  rejectionReason: string | null;
  locale: string | null;
  history: { at: string; status: string; actor: string; note: string | null }[];
}

/** The PII-free projection the PUBLIC tracker returns — deliberately not IntakeRecordWire.
 *  A reference code is a weak, enumerable bearer, so the server never sends submitter
 *  identity, the payload, the assignee or the event history to an unauthenticated caller
 *  (SEC-FIN-001). A page rendering this must show less, not fill the gaps. */
export interface IntakePublicStatusWire {
  referenceCode: string;
  kind: string;
  status: string;
  acknowledgedAt: string;
  slaDueAt: string | null;
}

export interface IntakeSubmissionBody {
  kind: string;
  channel: string;
  submitterIsAnonymous: boolean;
  submitterRole?: string;
  submitterName?: string;
  submitterEmail?: string;
  submitterPhone?: string;
  payload: string;
  severity?: string;
  locale?: string;
}

export const intakeApi = {
  /** Guarded by `isAuthenticated()`, not a permission — filing is a duty every employee is
   *  under (ADR-010 §Sanctioned exceptions). The submitter is bound server-side from the
   *  token, so the identity fields here are descriptive and never decide attribution. */
  submit: (body: IntakeSubmissionBody) =>
    api.post<IntakeRecordWire>('/api/v1/intake', body),
  /** Triager inbox. `INTAKE:READ` — reading other people's submissions is a privilege even
   *  though filing is not: this inbox holds whistleblowing reports and DSRs. */
  list: (params?: { status?: string; kind?: string; page?: number; size?: number }) => {
    const q = new URLSearchParams();
    if (params?.status) q.set('status', params.status);
    if (params?.kind) q.set('kind', params.kind);
    q.set('page', String(params?.page ?? 0));
    q.set('size', String(params?.size ?? 100));
    return api.get<PageResponse<IntakeRecordWire>>(`/api/v1/intake?${q.toString()}`);
  },
  get: (id: number) => api.get<IntakeRecordWire>(`/api/v1/intake/${id}`),
  /**
   * Apply a triage decision. On PROMOTE the server creates the register record, so the
   * response's `promotedToEntityType` and `promotedToRecordId` name a row that now exists —
   * **read them, never send them.**
   *
   * ⚠ The body is deliberately `{decision, note}` and nothing else. `TriageDecisionRequest`
   * carries no promotion fields at all, and Jackson drops unknown properties silently — so
   * a caller that "helpfully" adds one gets no error and no effect. The type is the warning.
   */
  triage: (id: number, body: { decision: 'PROMOTE' | 'REJECT' | 'CLOSE'; note?: string }) =>
    api.put<IntakeRecordWire>(`/api/v1/intake/${id}/triage`, body),
  /**
   * The submit path for a genuinely public surface (`/portal/dsr` — a data subject
   * exercising PDPL Art. 4-7, who has no account).
   *
   * ⚠ **This is expected to be refused today, and calling it anyway is the point.**
   * F2-138 records that no anonymous intake endpoint exists: `POST /api/v1/intake` is
   * `isAuthenticated()`, so a member of the public gets a 401. The page shows that refusal.
   * The alternative — not calling, and printing a notice — would leave the portal unable to
   * start working when the anonymous endpoint lands, and it is a memory-based control.
   * What must never come back is the third option, which is what this replaced: mint a
   * reference code client-side and tell the data subject their statutory clock has started.
   *
   * `anonymous: true` is load-bearing. Without it a 401 sends `redirectToLogin()`, so a
   * data subject filing a DSR would be bounced to a staff login screen.
   */
  submitPublic: (body: IntakeSubmissionBody) =>
    api.post<IntakeRecordWire>('/api/v1/intake', body, { anonymous: true }),
  /** Public — no token. `anonymous: true` keeps the Authorization header off so a stale
   *  session cannot turn a public lookup into a 401 redirect. */
  track: (referenceCode: string) =>
    api.get<IntakePublicStatusWire>(
      `/api/v1/intake/track/${encodeURIComponent(referenceCode)}`, { anonymous: true }),
};
