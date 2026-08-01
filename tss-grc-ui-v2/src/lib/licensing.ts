// Module licensing client — Framework Intersection F9.
//
// Talks to /api/v1/licensing/* and returns typed shapes that the
// admin UI can render directly. The list endpoint is tenant-scoped
// server-side, so every call here is implicitly for the logged-in
// tenant — no organisationId in the URL.

import { api } from './api';

export type LicenseTier = 'FREE' | 'PRO' | 'ENTERPRISE';

/**
 * The 15 ratified SKUs — ADR-018, mirroring `LicensingService.SELLABLE_MODULES`.
 *
 * Declared here so `RatifiedSku` can be a union type: a typo in the path→SKU map
 * (`src/lib/entitlement.tsx`) then fails `tsc` instead of silently hiding a
 * module from every tenant forever, which is the failure mode a string-typed map
 * would have. Base Platform is deliberately absent — it is mandatory, carries no
 * licence row and is never gated (ADR-018 R4).
 *
 * ⚠ This is a second copy of a list the server owns, so it is held to the server
 * by a test: `FrontendSkuVocabularyTest` reads this array out of this file and
 * asserts set equality with `SELLABLE_MODULES`. Adding a SKU in Java without
 * adding it here fails the backend build.
 */
export const RATIFIED_SKUS = [
  'RISK', 'COMPLIANCE', 'AUDIT_LC', 'CYBER', 'SOCOPS', 'BCM', 'PDPL', 'TPRM',
  'ESG', 'ETHICS', 'SAUDI_FS', 'DATA_AI_GOV', 'BOARD', 'CHANGE', 'ACCESS_GOV',
] as const;

export type RatifiedSku = typeof RATIFIED_SKUS[number];

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

/**
 * Every code a licence row may carry: the 15 ratified SKUs **plus 12 retired
 * codes** that existing rows still name (ADR-018 §5 maps each to its
 * destination). Use {@link listSellableCatalog} for anything the user picks
 * from — offering the union offers twelve codes the server will refuse to
 * create.
 *
 * (This said "the seed list from V97" until 2026-08-01. V97 seeded 20 codes for
 * `organisation_id = 1` as a literal; the catalogue has since been ratified and
 * split, and V344 backfilled every organisation × every ratified SKU.)
 */
export async function listCatalog(): Promise<string[]> {
  return api.get<string[]>('/api/v1/licensing/catalog');
}

/**
 * The 15 ratified SKUs — what may be *newly* licensed. Prefer this in any picker.
 * Mirrors {@link RATIFIED_SKUS}; fetch it when you want the server's answer
 * rather than the compiled-in one.
 */
export async function listSellableCatalog(): Promise<string[]> {
  return api.get<string[]>('/api/v1/licensing/catalog/sellable');
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
 * Upsert a licence row.
 *
 * ⚠ **`organisationId` is honoured, not ignored.** This comment said the backend
 * stamped it from the caller's JWT "so the field on body is ignored"; it did, and
 * that was **F2-45** — a body naming organisation 2 produced a row for
 * organisation 1 and returned 200, so an operator licensing a customer silently
 * licensed a different one. Since 2026-08-01 licensing is the platform's one
 * legitimate body-supplied tenant, the target organisation must exist, and
 * omitting the field still means "the organisation I am switched into".
 *
 * ⚠ Also **partial updates are partial** (F2-82): fields you omit are left
 * alone. Sending a whole `OrganisationLicense` you built client-side will
 * overwrite `tier`, `validFrom` and `enabled` with its defaults — which used to
 * silently re-enable modules the business had deliberately disabled. Send only
 * the fields you mean to change.
 */
export async function upsertLicense(body: Partial<OrganisationLicense>): Promise<OrganisationLicense> {
  return api.post<OrganisationLicense>('/api/v1/licensing/modules', body);
}

/**
 * Flip the enabled flag to false. Cheaper round-trip than upsert.
 *
 * `organisationId` is optional and defaults to the caller's tenant, matching
 * {@link upsertLicense} — deliberately, because a licence you can grant across
 * tenants and cannot revoke across tenants is worse than one you can do neither
 * to (F2-45).
 */
export async function disableLicense(moduleCode: string, organisationId?: number): Promise<void> {
  const qs = organisationId == null ? '' : `?organisationId=${organisationId}`;
  await api.post(`/api/v1/licensing/modules/${encodeURIComponent(moduleCode)}/disable${qs}`);
}

// ─── Frontend gate helpers ──────────────────────────────────────────────

/**
 * The membership check behind the licence gate: is this module in the tenant's
 * enabled list?
 *
 * Fail-open on a null cache — the list has not loaded, or its request failed, and
 * the server still enforces. A wrong "show" costs a readable 403; a wrong "hide"
 * removes a paid feature silently.
 *
 * ⚠ **This function is the reason F2-04 was filed.** It shipped with the
 * entitlement subsystem and had **zero callers** for four months — the finding's
 * words were "the existing `moduleEnabled()` FE helper"; there was no gate for it
 * to be part of. `EntitlementProvider` in `entitlement.tsx` is its caller now,
 * and it stays the single implementation of the check rather than being
 * reimplemented there, so a fix lands in one place.
 */
export function moduleEnabled(cache: MyEnabledModulesResponse | null | undefined,
                              moduleCode: string): boolean {
  if (!cache) return true;  // fail-open until we've loaded the list — server still enforces
  return cache.enabled_modules.includes(moduleCode);
}
