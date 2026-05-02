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
  delete: <T>(p: string, opts?: FetchOpts) => request<T>(p, { ...opts, method: 'DELETE' }),
};

// ─── Auth endpoints ──────────────────────────────────────────────────────────
export interface LoginResponse {
  accessToken: string;
  user: UserInfo;
}
export async function login(username: string, password: string): Promise<LoginResponse> {
  const res = await api.post<LoginResponse>('/api/auth/login', { username, password }, { anonymous: true });
  setSession(res.accessToken, res.user);
  return res;
}
export async function logout(): Promise<void> {
  try { await api.post('/api/auth/logout'); } catch { /* ignore */ }
  clearSession();
}

// ─── Risk module DTOs (mirror RiskResponse / RiskRequest) ───────────────────
export interface RiskResponse {
  id: number;
  externalId?: string;
  riskCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  categoryId?: number;
  categoryName?: string;
  categoryNameEn?: string;
  categoryNameAr?: string;
  ownerId?: number;
  ownerName?: string;
  ownerFullNameEn?: string;
  ownerUsername?: string;
  status: string;
  inherentLikelihood: number;
  inherentImpact: number;
  inherentScore: number;
  residualLikelihood?: number;
  residualImpact?: number;
  residualScore?: number;
  riskLevel?: string;
  identifiedDate?: string;
  reviewDate?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface RiskRequest {
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  categoryId?: number;
  ownerId?: number;
  inherentLikelihood: number;
  inherentImpact: number;
  residualLikelihood?: number;
  residualImpact?: number;
  status?: string;
  reviewDate?: string;
}

export interface PageResponse<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}

export const riskApi = {
  list:   (status?: string, page = 0, size = 20) =>
            api.get<PageResponse<RiskResponse>>(
              `/api/v1/risk/risks?page=${page}&size=${size}${status ? `&status=${encodeURIComponent(status)}` : ''}`
            ),
  get:    (id: number) => api.get<RiskResponse>(`/api/v1/risk/risks/${id}`),
  create: (body: RiskRequest) => api.post<RiskResponse>('/api/v1/risk/risks', body),
  update: (id: number, body: RiskRequest) => api.put<RiskResponse>(`/api/v1/risk/risks/${id}`, body),
  remove: (id: number) => api.delete<void>(`/api/v1/risk/risks/${id}`),
};
