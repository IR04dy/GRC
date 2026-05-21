// Module licensing client — Framework Intersection F9.
//
// Talks to /api/v1/licensing/* and returns typed shapes that the
// admin UI can render directly. The list endpoint is tenant-scoped
// server-side, so every call here is implicitly for the logged-in
// tenant — no organisationId in the URL.

import { api } from './api';

export type LicenseTier = 'FREE' | 'PRO' | 'ENTERPRISE';

export interface OrganisationLicense {
  id: number;
  organisationId: number;
  moduleCode: string;
  tier: LicenseTier;
  seatCount?: number | null;
  validFrom?: string | null;   // ISO date (yyyy-MM-dd)
  validTo?: string | null;     // ISO date or null = no expiry
  enabled: boolean;
  notes?: string | null;
}

export interface MyEnabledModulesResponse {
  organisationId: number;
  enabled_modules: string[];
}

/** Every module the platform knows about (the seed list from V97). */
export async function listCatalog(): Promise<string[]> {
  return api.get<string[]>('/api/v1/licensing/catalog');
}

/** All licence rows for the current tenant, ordered by module code. */
export async function listLicenses(): Promise<OrganisationLicense[]> {
  return api.get<OrganisationLicense[]>('/api/v1/licensing/modules');
}

/** Enabled module codes for the current tenant (cheap, cached server-side). */
export async function myEnabled(): Promise<MyEnabledModulesResponse> {
  return api.get<MyEnabledModulesResponse>('/api/v1/licensing/me/enabled');
}

/**
 * Upsert a licence row. The backend stamps {@code organisationId} from the
 * caller's JWT, so the field on {@code body} is ignored — supply it as a hint
 * for type-checking only.
 */
export async function upsertLicense(body: Partial<OrganisationLicense>): Promise<OrganisationLicense> {
  return api.post<OrganisationLicense>('/api/v1/licensing/modules', body);
}

/** Flip the enabled flag to false. Cheaper round-trip than upsert. */
export async function disableLicense(moduleCode: string): Promise<void> {
  await api.post(`/api/v1/licensing/modules/${encodeURIComponent(moduleCode)}/disable`);
}

// ─── Frontend gate helpers ──────────────────────────────────────────────

/**
 * Quick local check the sidebar / route guard can use without a round-trip.
 * Pass the cached {@link MyEnabledModulesResponse} loaded once at app start.
 */
export function moduleEnabled(cache: MyEnabledModulesResponse | null | undefined,
                              moduleCode: string): boolean {
  if (!cache) return true;  // fail-open until we've loaded the list — server still enforces
  return cache.enabled_modules.includes(moduleCode);
}
