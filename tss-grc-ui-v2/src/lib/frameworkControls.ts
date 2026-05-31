// Framework controls API client — uses shared api.ts.

import { api } from './api';

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

export function listFrameworks(): Promise<CyberFramework[]> {
  return api.get('/api/v1/cyber/frameworks');
}

export function listAllControls(): Promise<CyberControl[]> {
  return api.get('/api/v1/cyber/controls/all');
}

export interface CyberSubControl {
  id: number;
  controlId?: number;
  subControlNumber?: string;
  titleEn?: string; titleAr?: string;
  descriptionEn?: string;        // recommended evidence text
  descriptionAr?: string;        // Arabic recommended evidence text
  controlOwner?: string;
  implementationStatus?: string;
  maturityLevel?: string;
  evidenceRequired?: boolean;
}

/** Sub-controls for a single control. Backend returns a Spring Page; we unwrap .content. */
export async function listSubControlsByControl(controlId: number): Promise<CyberSubControl[]> {
  const res = await api.get<{ content?: CyberSubControl[] } | CyberSubControl[]>(
    `/api/v1/cyber/sub-controls?controlId=${controlId}&size=300`);
  return Array.isArray(res) ? res : (res?.content ?? []);
}

export const CTRL_IMPL_TONE: Record<string, 'success'|'warning'|'danger'|'info'> = {
  IMPLEMENTED: 'success', PARTIAL: 'warning', PLANNED: 'info',
  NOT_APPLICABLE: 'info', NOT_IMPLEMENTED: 'danger',
};
export const CTRL_MATURITY_TONE: Record<string, 'success'|'warning'|'danger'|'info'> = {
  L1: 'danger', L2: 'warning', L3: 'info', L4: 'success', L5: 'success',
};
