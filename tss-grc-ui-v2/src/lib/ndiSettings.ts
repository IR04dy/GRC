// NDI Settings API client (slice 13 · V77).

import { api } from './api';

export type NdiSettingType = 'STRING' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'JSON';

export interface NdiSetting {
  id?: number;
  organisationId?: number;
  settingKey: string;
  category: string;
  valueType: NdiSettingType;
  valueText?: string;
  nameEn: string;
  nameAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  defaultValue?: string;
  isLocked: boolean;
}

const BASE = '/api/ndi/settings';

export function listSettings()                    { return api.get<NdiSetting[]>(BASE); }
export function listSettingsGrouped()             { return api.get<Record<string, NdiSetting[]>>(`${BASE}/grouped`); }
export function getSettingByKey(key: string)      { return api.get<NdiSetting>(`${BASE}/by-key/${encodeURIComponent(key)}`); }
export function setSettingValue(key: string, value: string) {
  return api.put<NdiSetting>(`${BASE}/by-key/${encodeURIComponent(key)}`, { value });
}
export function resetSetting(key: string)         { return api.post<NdiSetting>(`${BASE}/by-key/${encodeURIComponent(key)}/reset`); }
export function createSetting(body: Partial<NdiSetting>) { return api.post<NdiSetting>(BASE, body); }
export function deleteSetting(id: number)         { return api.delete<void>(`${BASE}/${id}`); }

export function settingCategoryLabel(cat: string, lang: 'en' | 'ar'): string {
  const en: Record<string, string> = {
    GENERAL: 'General', THRESHOLDS: 'Thresholds', WORKFLOW: 'Workflow',
    ATTESTATION: 'Attestation', GUARDRAIL: 'Guardrail',
  };
  const ar: Record<string, string> = {
    GENERAL: 'عام', THRESHOLDS: 'العتبات', WORKFLOW: 'سير العمل',
    ATTESTATION: 'الإقرار', GUARDRAIL: 'الحماية',
  };
  return (lang === 'ar' ? ar : en)[cat] ?? cat;
}
