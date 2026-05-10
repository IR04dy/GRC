// Client-side CSV / JSON exporter — DD-46.
//
// Until now, the "Export" button on most register pages just showed a
// toast.info("export coming soon") hint (from DD-39). That was always
// a stop-gap; the user's repeated point was "demo only, not real".
// The real backend exports (PDF/XLSX via reportingApi) only exist for
// Risk and Changes — we can't add server-side endpoints for the rest
// without a Sprint-2 backend deployment.
//
// What we *can* do entirely client-side, today, with zero deps:
//   - Render the visible table data as CSV and trigger a download
//   - Render the same data as JSON (useful for re-import / debug)
//
// Used by every register page's "Export" workflow-bar button.

export type ExportRow = Record<string, string | number | boolean | null | undefined>;

/** Convert a value to its CSV cell representation, with proper RFC-4180
 *  escaping (wrap in quotes if the cell contains comma / quote / newline,
 *  and double up any embedded quote).
 */
function csvCell(v: unknown): string {
  if (v === null || v === undefined) return '';
  const s = typeof v === 'string' ? v : String(v);
  if (s.includes(',') || s.includes('"') || s.includes('\n') || s.includes('\r')) {
    return `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}

/** Trigger a CSV download from in-memory rows.
 *  Headers default to the union of all keys in `rows`. */
export function downloadCsv(rows: ExportRow[], filename: string, headers?: string[]): void {
  if (rows.length === 0) {
    // Still emit an empty file with headers so the user gets feedback.
    const cols = headers ?? [];
    triggerDownload(cols.join(',') + '\r\n', filename, 'text/csv;charset=utf-8');
    return;
  }
  const cols = headers ?? Array.from(rows.reduce<Set<string>>(
    (acc, r) => { Object.keys(r).forEach(k => acc.add(k)); return acc; }, new Set(),
  ));
  const headerLine = cols.map(csvCell).join(',');
  const lines = rows.map(r => cols.map(c => csvCell(r[c])).join(','));
  // BOM keeps Excel happy with UTF-8 (Arabic content otherwise renders mojibake).
  const body = '﻿' + [headerLine, ...lines].join('\r\n') + '\r\n';
  triggerDownload(body, filename, 'text/csv;charset=utf-8');
}

/** Trigger a JSON download. */
export function downloadJson(data: unknown, filename: string): void {
  triggerDownload(JSON.stringify(data, null, 2), filename, 'application/json;charset=utf-8');
}

function triggerDownload(content: string, filename: string, mime: string): void {
  const blob = new Blob([content], { type: mime });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  a.style.display = 'none';
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  // Free the object URL after the next tick so the download is allowed
  // to start on slow browsers.
  setTimeout(() => URL.revokeObjectURL(url), 100);
}

/** Stamp filename with today's ISO date, e.g. "capa-register-2026-05-04.csv". */
export function stampFilename(prefix: string, ext: 'csv' | 'json'): string {
  return `${prefix}-${new Date().toISOString().slice(0, 10)}.${ext}`;
}
