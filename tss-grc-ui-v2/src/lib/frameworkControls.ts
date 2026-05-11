// Framework controls API client — reads from existing cyber controls endpoints.
// Backend: /api/v1/cyber/frameworks + /api/v1/cyber/controls/all

export interface CyberFramework {
  id: number; name: string; nameAr?: string;
  version?: string;
  description?: string; descriptionEn?: string; descriptionAr?: string;
  regulator?: string;
  frameworkCode?: string;
  domainCount?: number; controlCount?: number;
  effectiveFrom?: string;
  mandatoryFor?: string;
  referenceUrl?: string;
}

export interface CyberControl {
  id: number; externalId?: string;
  frameworkId?: number; frameworkName?: string;
  domain?: string; controlNumber?: string;
  titleEn?: string; titleAr?: string;
  procedureEn?: string;
  controlType?: string;
  controlOwner?: string;
  reviewFrequency?: string;
  maturityLevel?: string;
  implementationStatus?: string;
  status?: string;
}

function authHeaders(): HeadersInit {
  const tok = window.sessionStorage.getItem('grc.accessToken') ?? '';
  return tok ? { Authorization: `Bearer ${tok}` } : {};
}

export async function listFrameworks(): Promise<CyberFramework[]> {
  const res = await fetch('/api/v1/cyber/frameworks', { headers: authHeaders() });
  if (!res.ok) throw new Error(`listFrameworks HTTP ${res.status}`);
  return res.json();
}

export async function listAllControls(): Promise<CyberControl[]> {
  const res = await fetch('/api/v1/cyber/controls/all', { headers: authHeaders() });
  if (!res.ok) throw new Error(`listAllControls HTTP ${res.status}`);
  return res.json();
}

export const CTRL_IMPL_TONE: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  IMPLEMENTED: 'success',
  PARTIAL:     'warning',
  PLANNED:     'info',
  NOT_APPLICABLE: 'info',
  NOT_IMPLEMENTED: 'danger',
};
export const CTRL_MATURITY_TONE: Record<string, 'success' | 'warning' | 'danger' | 'info'> = {
  L1: 'danger',  L2: 'warning', L3: 'info',
  L4: 'success', L5: 'success',
};
