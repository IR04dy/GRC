// F2-04b — the frontend half of the entitlement gate.
//
// F2-04a made the server refuse: 197 controllers carry `@RequireModule`, and an
// unlicensed call now answers 403 `errors/module-not-licensed` (F2-84 — it used
// to answer 500, so entitlement had never once been observable). This file is
// the UX half: don't offer a tenant what they have not bought.
//
// ─── This gate is cosmetic, and that is deliberate ────────────────────────
// Everything here can be removed with a devtools console. It is not a security
// boundary and must never be treated as one — the boundary is
// `LicenseGuardAspect`, server-side, and it holds whether or not the sidebar
// still shows a link. That is precisely why F2-04 was split (a) server then
// (b) frontend: shipping (b) first would have been a paywall you could delete.
//
// ─── Failure direction: fail OPEN ─────────────────────────────────────────
// Every unknown here resolves to "show it":
//   * a path with no SKU mapping        -> shown (Base, or new and unmapped)
//   * the enabled-modules list not yet loaded -> shown
//   * the enabled-modules request failed      -> shown
// A wrong "show" costs a 403 the user can read. A wrong "hide" removes a paid
// feature with no error anywhere, and the customer discovers it. The server is
// authoritative in both directions, so fail-open is the cheap mistake.
//
// ⚠ No PLATFORM_ADMIN bypass — checked, not assumed. `LicenseGuardAspect`
// consults `licensing.isModuleEnabled` and nothing else: entitlement is
// commercial, not authorisation, so an admin of an unlicensed tenant is refused
// like everyone else. This is the OPPOSITE of `canExport`/`canApprove` in
// auth.ts, which do honour the admin bypass because `ModulePermissionEvaluator`
// bypasses in code. Do not "make them consistent" — they are consistent with
// their own server-side enforcers, which is the property that matters.

import { useContext, useEffect, useState, type ReactNode } from 'react';
import { stableContext } from './stableContext';
import { moduleEnabled, myEnabled, type MyEnabledModulesResponse, type RatifiedSku } from './licensing';
import { isBarePath } from './publicPaths';
import { isAuthenticated } from './auth';

/**
 * Route-prefix → SKU. The FE's URL scheme is already the module taxonomy, so
 * this maps ~60 prefixes rather than annotating 328 individual `<Route>`s — the
 * same grain the server uses (package → SKU, not endpoint → SKU).
 *
 * ─── How these assignments were decided ──────────────────────────────────
 * Not from the URL alone. Each page's own `/api/..` literals plus those of the
 * named `lib/*` exports it imports were resolved against the backend's
 * `@RequestMapping` + `@RequireModule` pair, so the SKU comes from the
 * annotation that will actually refuse the call. Six assignments the URL scheme
 * would have got backwards:
 *   /rca                -> SOCOPS   (`/api/incident/rca`, not risk — the sidebar
 *                                    files it under the Risk pillar)
 *   /incidents          -> SOCOPS   (`incident` package, same)
 *   /cyber-pdpl-bridge  -> PDPL     (`/api/privacy/cyber-bridge`, not CYBER)
 *   /framework-controls -> CYBER    (`/api/v1/cyber/...`, not COMPLIANCE)
 *   /resilience-tests   -> TPRM     (`/api/scm/resilience/tests`, not BCM)
 *   /third-party-cyber  -> TPRM     (`/api/tprm/cyber-assessments`, not CYBER)
 * A nav *pillar* is a UX grouping and does not imply a SKU; several pillars mix
 * SKUs on purpose (the Risk pillar shows SOCOPS incidents, the Privacy pillar
 * shows DATA_AI_GOV's NDI).
 *
 * ⚠ Matching is on SEGMENT boundaries — `path === prefix` or
 * `path.startsWith(prefix + '/')` — never bare `startsWith`. Without that,
 * `/audit` swallows `/audit-log` (Base, the platform audit ledger) and `/cyber`
 * swallows `/cyber-pdpl-bridge` (PDPL). Longest match wins, so a more specific
 * prefix can carve an exception out of a broader one.
 *
 * Absence means ungated: Base Platform is mandatory and row-less (ADR-018 R4),
 * so `/dashboard`, `/workflows`, `/approvals`, `/evidence`, `/capa`,
 * `/knowledge-base`, `/reports`, `/audit-log`, `/admin`, `/settings` and the
 * rest of the kernel are simply not listed here.
 */
export const SKU_BY_PATH_PREFIX: ReadonlyArray<readonly [string, RatifiedSku]> = [
  // ─── RISK (risk, risk_lc, ccm) ───────────────────────────────────────
  ['/risk', 'RISK'],
  ['/risk-lifecycle', 'RISK'],
  ['/risk-scenarios', 'RISK'],
  ['/emerging-risk', 'RISK'],
  ['/ccm', 'RISK'],

  // ─── COMPLIANCE (compliance, policy, nca_ecc, regchange, reginspect, regreporting) ───
  ['/compliance', 'COMPLIANCE'],
  ['/policies', 'COMPLIANCE'],
  ['/policy', 'COMPLIANCE'],
  ['/policy-management', 'COMPLIANCE'],
  ['/policy-templates', 'COMPLIANCE'],
  ['/regulatory', 'COMPLIANCE'],
  ['/regulatory-change', 'COMPLIANCE'],
  ['/nca-ecc', 'COMPLIANCE'],
  ['/nca-ecc-export', 'COMPLIANCE'],
  ['/reginspect', 'COMPLIANCE'],
  ['/regreporting', 'COMPLIANCE'],
  ['/attestations', 'COMPLIANCE'],
  ['/controls', 'COMPLIANCE'],
  ['/control-crosswalk', 'COMPLIANCE'],
  ['/framework-packs', 'COMPLIANCE'],
  ['/findings', 'COMPLIANCE'],

  // ─── AUDIT_LC (audit_lc, audit_eng, audit_committee) ────────────────
  // NB `/audit-log` is Base and is protected by segment-boundary matching.
  ['/audit', 'AUDIT_LC'],
  ['/audit-lifecycle', 'AUDIT_LC'],
  ['/audit-workspace', 'AUDIT_LC'],
  ['/audit-committee', 'AUDIT_LC'],

  // ─── CYBER (cyber, cyber_lc) ────────────────────────────────────────
  ['/cyber', 'CYBER'],
  ['/cyber-lifecycle', 'CYBER'],
  ['/framework-controls', 'CYBER'],
  ['/assets', 'CYBER'],

  // ─── SOCOPS (socops, incident) ──────────────────────────────────────
  ['/socops', 'SOCOPS'],
  ['/incidents', 'SOCOPS'],
  ['/rca', 'SOCOPS'],

  // ─── BCM (bcm, bcm_lc, opres) ───────────────────────────────────────
  ['/bcm', 'BCM'],
  ['/bcm-lifecycle', 'BCM'],
  ['/bcm-dependency', 'BCM'],
  ['/crisis', 'BCM'],
  ['/crisis-management', 'BCM'],
  ['/dr-plans', 'BCM'],
  ['/opres', 'BCM'],

  // ─── PDPL (privacy, privacy_lc, data_officer, dataclass) ────────────
  ['/privacy', 'PDPL'],
  ['/privacy-lifecycle', 'PDPL'],
  ['/pdpl', 'PDPL'],
  ['/pdpl-extensions', 'PDPL'],
  ['/data-lifecycle', 'PDPL'],
  ['/data-officer', 'PDPL'],
  ['/dataclass', 'PDPL'],
  ['/dpias', 'PDPL'],
  ['/ropa', 'PDPL'],
  ['/data-sharing', 'PDPL'],
  ['/processor-assessments', 'PDPL'],
  ['/cyber-pdpl-bridge', 'PDPL'],

  // ─── TPRM (tprm, scm, outsourcing) ──────────────────────────────────
  ['/tprm', 'TPRM'],
  ['/scm', 'TPRM'],
  ['/outsourcing', 'TPRM'],
  ['/vendors', 'TPRM'],
  ['/contract-clauses', 'TPRM'],
  ['/fourth-party', 'TPRM'],
  ['/supplier-resilience', 'TPRM'],
  ['/third-party-cyber', 'TPRM'],
  ['/resilience-tests', 'TPRM'],

  // ─── ESG (sust) ─────────────────────────────────────────────────────
  ['/esg', 'ESG'],
  ['/green-finance', 'ESG'],

  // ─── ETHICS (ethics, consumer, aml, fraud) ──────────────────────────
  ['/ethics', 'ETHICS'],
  ['/complaints', 'ETHICS'],
  ['/aml', 'ETHICS'],
  ['/fraud', 'ETHICS'],

  // ─── SAUDI_FS (sharia, openbanking, rrp, cpty) ──────────────────────
  ['/sharia', 'SAUDI_FS'],
  ['/openbanking', 'SAUDI_FS'],
  ['/rrp', 'SAUDI_FS'],
  ['/cpty', 'SAUDI_FS'],

  // ─── DATA_AI_GOV (ndi, ai) ──────────────────────────────────────────
  ['/ndi', 'DATA_AI_GOV'],
  ['/ai', 'DATA_AI_GOV'],
  ['/ai-lifecycle', 'DATA_AI_GOV'],

  // ─── BOARD (board_pack + the admin.committee carve-out, ADR-018 R6) ──
  ['/board-pack', 'BOARD'],
  ['/committees', 'BOARD'],

  // ─── CHANGE (change_mgmt) ───────────────────────────────────────────
  ['/changes', 'CHANGE'],

  // ─── ACCESS_GOV (the iam.governance carve-out, ADR-018 R6) ──────────
  ['/access-governance', 'ACCESS_GOV'],
];

/**
 * Which SKU sells this route, or `null` for Base / unmapped / public.
 *
 * Query strings and hashes are stripped first: several nav destinations are
 * tab deep-links (`/ndi?tab=compliance`, `/changes?tab=governance`) and would
 * otherwise match nothing and silently read as Base.
 */
export function skuForPath(pathname: string): RatifiedSku | null {
  const path = pathname.split('?')[0].split('#')[0];
  if (isBarePath(path)) return null;      // public / no-session: never gated
  let best: RatifiedSku | null = null;
  let bestLen = -1;
  for (const [prefix, sku] of SKU_BY_PATH_PREFIX) {
    if ((path === prefix || path.startsWith(prefix + '/')) && prefix.length > bestLen) {
      best = sku;
      bestLen = prefix.length;
    }
  }
  return best;
}

// ─── Navigation filtering ─────────────────────────────────────────────────

/**
 * Structural minimum this filter needs; Sidebar's `NavItem` satisfies it.
 *
 * `gate` is an optional per-item predicate for concerns that are **not**
 * entitlement — today only `BOARD:EXPORT` on the board pack (F2-42). It lives on
 * the item rather than in a second parameter so there is one filter, one
 * group-emptying rule, and the reason an entry can vanish is declared next to the
 * entry. Entitlement asks *did this tenant buy it*; a gate asks *may this user see
 * it*. Both can hide a row and they are not the same question.
 */
export interface GatedNavItem {
  to: string;
  children?: GatedNavItem[];
  gate?: () => boolean;
}
/** Structural minimum this filter needs; Sidebar's `NavGroup` satisfies it. */
export interface GatedNavGroup<I extends GatedNavItem> { items: I[] }

/**
 * Drops nav entries whose SKU the tenant has not licensed.
 *
 * <p>Lives here rather than inline in Sidebar so it is unit-testable: "hides the
 * wrong thing" is the expensive failure in this slice (a paid feature vanishes
 * with no error anywhere), and it is not observable from a render snapshot.
 *
 * <p>Two rules that are easy to get backwards:
 * <ul>
 *   <li>A parent survives if <b>it</b> is licensed <b>or any child is</b>. A hub
 *       row whose own path is gated would otherwise take licensed children with
 *       it.</li>
 *   <li>A group emptied of items is dropped <b>with its header</b>. Leaving
 *       "ESG &amp; Climate" present but empty advertises exactly the module
 *       being hidden, and reads as a bug rather than a licence boundary.</li>
 * </ul>
 */
export function filterNavByEntitlement<I extends GatedNavItem, G extends GatedNavGroup<I>>(
  groups: readonly G[],
  isEnabled: (sku: RatifiedSku | null) => boolean,
): G[] {
  // `gate?.() !== false` rather than `gate?.() ?? true`: an item with no gate and
  // an item whose gate says yes must behave identically, and only the !== form
  // keeps a gate that throws or returns undefined from hiding the row.
  const keep = (it: GatedNavItem) =>
    isEnabled(skuForPath(it.to)) && it.gate?.() !== false;
  const out: G[] = [];
  for (const g of groups) {
    const items: I[] = [];
    for (const it of g.items) {
      const children = it.children?.filter(c => keep(c)) as I['children'];
      if (keep(it) || (children && children.length > 0)) {
        items.push(children ? { ...it, children } : it);
      }
    }
    if (items.length > 0) out.push({ ...g, items });
  }
  return out;
}

// ─── React binding ────────────────────────────────────────────────────────

export interface EntitlementState {
  /** False until the enabled-modules list has been fetched (or has failed). */
  ready: boolean;
  /** Enabled SKU codes for the current tenant; empty until `ready`. */
  enabled: ReadonlySet<string>;
  /** Is this SKU licensed? Fail-open while not `ready`. */
  isEnabled: (sku: RatifiedSku | null | undefined) => boolean;
  /** Is the module serving this route licensed? Fail-open for Base/unmapped. */
  isPathEnabled: (pathname: string) => boolean;
  /**
   * Re-fetch. Called by the licensing admin screen after it grants or revokes a
   * SKU, because an admin editing their *own* tenant would otherwise keep a
   * stale sidebar until the next reload ("I enabled ESG and nothing appeared").
   *
   * Not needed for the org switcher: that does a hard `window.location.reload()`
   * after `switchOrg`, so the provider remounts and re-fetches anyway. An
   * earlier draft of this file listened for a `grc:org-switched` event — which
   * nothing dispatches, making it exactly the zero-caller helper F2-04b exists
   * to fix. Checked before wiring, not after.
   */
  refresh: () => void;
}

const FAIL_OPEN: EntitlementState = {
  ready: false,
  enabled: new Set<string>(),
  isEnabled: () => true,
  isPathEnabled: () => true,
  refresh: () => {},
};

// ⚠ This one matters most. Its default is FAIL_OPEN, so a duplicated module does not throw — it
// silently serves the fail-open default to every consumer, which is entitlement quietly switching
// itself off (F2-04's territory). A crash is loud; this would not have been.
const EntitlementContext = stableContext<EntitlementState>('grc.entitlement', FAIL_OPEN);

/**
 * `useEntitlement()` outside a provider returns the fail-open state rather than
 * throwing. A missing provider must not blank the application — the same
 * judgement as the fail-open default above, and it keeps this usable from tests
 * and from any surface that renders outside the shell.
 */
export const useEntitlement = () => useContext(EntitlementContext);

/**
 * Loads `GET /api/v1/licensing/me/enabled` once per session and shares it.
 *
 * Mounted in `AppLayout`, which is the single persistent shell — so this is one
 * request per login, not one per navigation. Licences are per organisation, and
 * the org switcher hard-reloads after `switchOrg`, so a switch remounts this and
 * refetches without needing an event.
 *
 * It holds the whole {@link MyEnabledModulesResponse} rather than just a Set so
 * the membership test can stay in `moduleEnabled()` — the helper F2-04 named,
 * which had no callers until this provider. One implementation, one place to fix.
 */
export function EntitlementProvider({ children }: { children: ReactNode }) {
  const [cache, setCache] = useState<MyEnabledModulesResponse | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!isAuthenticated()) {
      // No session: nothing to entitle. Stay fail-open — the endpoint is
      // isAuthenticated(), so an anonymous caller could only get a 401.
      setCache(null);
      return;
    }
    let alive = true;
    myEnabled()
      .then((r: MyEnabledModulesResponse) => { if (alive) setCache(r); })
      .catch(err => {
        // Fail open, loudly. A licensing outage must not black out the product;
        // the server still refuses anything genuinely unlicensed.
        console.error('[entitlement] could not load enabled modules — showing all', err);
        if (alive) setCache(null);
      });
    return () => { alive = false; };
  }, [tick]);

  const ready = cache != null;
  const enabled: ReadonlySet<string> = new Set(cache?.enabled_modules ?? []);

  const isEnabled = (sku: RatifiedSku | null | undefined) =>
    // Base / unmapped is always shown; otherwise defer to the shared helper,
    // which fails open on a null cache.
    sku ? moduleEnabled(cache, sku) : true;

  return (
    <EntitlementContext.Provider value={{
      ready,
      enabled,
      isEnabled,
      isPathEnabled: (p: string) => isEnabled(skuForPath(p)),
      refresh: () => setTick(t => t + 1),
    }}>
      {children}
    </EntitlementContext.Provider>
  );
}
