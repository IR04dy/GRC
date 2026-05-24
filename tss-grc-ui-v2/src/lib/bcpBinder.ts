// BCP/DRP offline binder client — Slice 10-F.
//
// Wraps /api/v1/bcm/plans/{planId}/binder + /api/v1/bcm/binders/* so the
// BCP Builder page can generate, list, download, and archive printable
// off-line binder PDFs. SAMA BCF Annex D requires every critical-business-
// service BCP to be available off-line — these binders are that artefact.

import { api, downloadFile } from './api';

export type BinderLanguage = 'EN' | 'AR' | 'BILINGUAL';
export type BinderStatus = 'GENERATING' | 'READY' | 'FAILED' | 'ARCHIVED';

export interface BinderExport {
  id: number;
  externalId: string;
  organisationId: number;
  bcpPlanId: number;
  exportCode: string;
  format: string;
  language: BinderLanguage;
  versionLabel?: string;
  watermarkText?: string;
  status: BinderStatus;
  errorMessage?: string;
  generatedAt: string;
  generatedByUserId?: number;
  generatedByName?: string;
  generationDurationMs?: number;
  pageCount?: number;
  byteSize?: number;
  downloadCount: number;
  lastDownloadedAt?: string;
}

// ─── API calls ────────────────────────────────────────────────────────

/** Generate a fresh binder. Returns the row in READY (or FAILED) state. */
export function generateBinder(
  planId: number, language: BinderLanguage = 'BILINGUAL',
): Promise<BinderExport> {
  return api.post<BinderExport>(
    `/api/v1/bcm/plans/${planId}/binder?language=${language}`, {});
}

/** Newest-first list of binder versions for a single plan. */
export function listBindersForPlan(planId: number): Promise<BinderExport[]> {
  return api.get<BinderExport[]>(`/api/v1/bcm/plans/${planId}/binders`);
}

export function getBinder(exportId: number): Promise<BinderExport> {
  return api.get<BinderExport>(`/api/v1/bcm/binders/${exportId}`);
}

/** Trigger a browser download — auth header + Content-Disposition handled
 *  by the shared `downloadFile` helper. */
export function downloadBinder(exportId: number, exportCode?: string): Promise<void> {
  const fallback = (exportCode ? exportCode : `binder-${exportId}`) + '.pdf';
  return downloadFile(`/api/v1/bcm/binders/${exportId}/download`, fallback);
}

/** Soft-delete (archive). */
export function archiveBinder(exportId: number): Promise<BinderExport> {
  return api.delete<BinderExport>(`/api/v1/bcm/binders/${exportId}`);
}

// ─── Helpers ──────────────────────────────────────────────────────────

export function languageLabel(l: BinderLanguage, isAr: boolean): string {
  if (isAr) return l === 'EN' ? 'إنجليزي'
              : l === 'AR' ? 'عربي'
              : 'ثنائي اللغة';
  return l === 'EN' ? 'English'
       : l === 'AR' ? 'Arabic'
       : 'Bilingual';
}

export function statusTone(s: BinderStatus): 'neutral' | 'info' | 'warning' | 'success' | 'danger' {
  switch (s) {
    case 'READY':       return 'success';
    case 'GENERATING':  return 'info';
    case 'FAILED':      return 'danger';
    case 'ARCHIVED':    return 'neutral';
  }
}

/** Compact byte-size formatter — "1.2 MB". */
export function fmtBytes(bytes?: number): string {
  if (bytes == null) return '—';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
