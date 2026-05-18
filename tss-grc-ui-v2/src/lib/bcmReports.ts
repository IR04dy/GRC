// BCM-7 report generator client.
//
// Surfaces the /api/v1/bcm/reports/* endpoints to the UI as typed wrappers.
// The report payloads are loosely typed (Record<string, unknown>) because
// each report has its own shape; the viewer page renders the structure
// generically (KPIs + tables) and the catalogue codes drive the labels.

import { api } from './api';

export interface BcmReportDef {
  code:    string;
  titleEn: string;
  titleAr: string;
}

/** Fetch the stable catalogue of 15 reports. */
export async function listReportCatalog(): Promise<BcmReportDef[]> {
  return api.get<BcmReportDef[]>('/api/v1/bcm/reports/catalog');
}

/**
 * Generate one report. The returned object always includes
 * {@code reportCode} and {@code generatedAt}; the remaining keys
 * vary per report (see {@code BcmReportService} catalogue docs).
 */
export async function generateReport(code: string): Promise<Record<string, unknown>> {
  return api.get<Record<string, unknown>>(
    `/api/v1/bcm/reports/${encodeURIComponent(code)}`);
}
