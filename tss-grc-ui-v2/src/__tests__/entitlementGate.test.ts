import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import {
  SKU_BY_PATH_PREFIX, skuForPath, filterNavByEntitlement,
  type GatedNavItem,
} from '../lib/entitlement';
import { RATIFIED_SKUS, type RatifiedSku } from '../lib/licensing';
import { BARE_PREFIXES, isBarePath } from '../lib/publicPaths';

/**
 * F2-04b — the frontend entitlement gate.
 *
 * <p>F2-04a made the server refuse an unlicensed module; this gate stops the UI
 * offering one. It is <b>cosmetic by design</b> — a devtools console removes it —
 * so these tests are not about security. They are about the two ways a cosmetic
 * gate does real damage:
 *
 * <ol>
 *   <li><b>Hiding too much.</b> A paid module disappears from the nav with no
 *       error, no toast and nothing in a log. The customer discovers it. This is
 *       the expensive direction and most of this file points at it.</li>
 *   <li><b>Hiding the kernel.</b> Base Platform is mandatory and carries no
 *       licence row (ADR-018 R4), so a gate that catches {@code /workflows} or
 *       {@code /approvals} blacks out the 14 modules that adopted the workflow
 *       engine for every tenant at once.</li>
 * </ol>
 *
 * <p><b>Both directions of set containment are asserted.</b> This programme has
 * now got that wrong twice — ADR-017 D-10c and F2-61(a) — by checking only that
 * the guarded set was guarded, never that the unguarded set stayed unguarded.
 */

const SRC = join(__dirname, '..');
const sidebar = readFileSync(join(SRC, 'components', 'layout', 'Sidebar.tsx'), 'utf8');

/** Every `to: '<path>'` destination in the real NAV_GROUPS literal. */
const NAV_DESTINATIONS: string[] = [
  ...new Set(Array.from(sidebar.matchAll(/to:\s*'([^']+)'/g), m => m[1])),
];

/**
 * Base Platform paths. Not "everything not in the map" — that would make the
 * assertion circular and it would pass however the map changed. This is the
 * kernel enumerated independently, from ADR-018 R4's package list: platform,
 * iam, auth, admin, evidence, attachment, signature, workflow,
 * workflow_submission, approvals, intake, capa, dashboard, reporting,
 * integrations, knowledge, controlmap, governance.
 */
const BASE_PATHS = [
  '/dashboard', '/settings', '/admin', '/admin/licensing', '/admin/help-topics',
  '/account/sessions', '/approvals', '/signatures', '/workflows',
  '/workflows/requests', '/workflows/submissions', '/workflows/chains',
  '/workflow-versions', '/intake', '/intake-inbox', '/capa', '/evidence',
  '/evidence-chain', '/file-upload', '/export-history', '/audit-log',
  '/notifications', '/notifications/channels', '/communications', '/connectors',
  '/sso', '/onboarding', '/knowledge-base', '/reports/centre',
  '/reports/schedules', '/governance/three-lines', '/calendar',
  '/lifecycle-overview', '/issues', '/expert', '/buyer-gap', '/authorities',
];

describe('the path→SKU map is well-formed', () => {
  it('names only ratified SKUs', () => {
    const ratified = new Set<string>(RATIFIED_SKUS);
    const bad = SKU_BY_PATH_PREFIX.filter(([, sku]) => !ratified.has(sku));
    expect(bad, `unratified SKU codes in the map: ${JSON.stringify(bad)}`).toEqual([]);
  });

  it('gates every ratified SKU at least once', () => {
    // A SKU present in the catalogue but absent from this map is sold and then
    // never hidden — the buyer's neighbour keeps seeing it in their sidebar.
    // This is the direction that silently rots as routes get renamed.
    const gated = new Set(SKU_BY_PATH_PREFIX.map(([, sku]) => sku));
    const ungated = RATIFIED_SKUS.filter(s => !gated.has(s));
    expect(ungated, `ratified SKUs no path maps to: ${ungated.join(', ')}`).toEqual([]);
  });

  it('has no duplicate prefixes', () => {
    const seen = new Map<string, string>();
    const dupes: string[] = [];
    for (const [prefix, sku] of SKU_BY_PATH_PREFIX) {
      const prior = seen.get(prefix);
      if (prior) dupes.push(`${prefix} → ${prior} and ${sku}`);
      seen.set(prefix, sku);
    }
    expect(dupes, `a duplicate prefix makes one entry dead: ${dupes.join('; ')}`).toEqual([]);
  });

  it('is not trivially small (anti-vacuity floor)', () => {
    // Every "offenders is empty" assertion below passes on an empty map.
    expect(SKU_BY_PATH_PREFIX.length).toBeGreaterThanOrEqual(60);
    expect(NAV_DESTINATIONS.length).toBeGreaterThanOrEqual(180);
  });
});

describe('hyphen-suffixed siblings resolve to their own SKU, not the parent prefix', () => {
  // ⚠ TWO different mechanisms protect these, and conflating them was an error in
  // this file's first draft. Mutation-tested by replacing the boundary check with
  // a bare `startsWith`:
  //
  //   * /audit-log FAILED  — nothing maps it, so only the segment-boundary check
  //     stops `/audit` (AUDIT_LC) claiming the platform audit ledger. This is the
  //     case that needs the boundary rule.
  //   * /cyber-pdpl-bridge PASSED — it has its own longer entry, so longest-match
  //     already wins. The boundary rule is not what saves it.
  //
  // Both are worth pinning; only the first is evidence for the boundary check.
  it.each([
    ['/cyber-pdpl-bridge', 'PDPL', 'calls /api/privacy/cyber-bridge — PDPL, not CYBER'],
    ['/cyber-lifecycle', 'CYBER', 'own entry, same SKU as its parent'],
    ['/risk-lifecycle', 'RISK', 'ditto for risk'],
    ['/bcm-lifecycle', 'BCM', 'ditto for bcm'],
    ['/privacy-lifecycle', 'PDPL', 'ditto for privacy'],
    ['/data-lifecycle', 'PDPL', 'PDPL retention, unrelated to the *-lifecycle family'],
    ['/control-crosswalk', 'COMPLIANCE', 'own entry, not a child of /controls'],
    ['/regulatory-change', 'COMPLIANCE', 'own entry, not a child of /regulatory'],
  ])('%s → %s (%s)', (path, expected) => {
    expect(skuForPath(path)).toBe(expected);
  });

  it('/audit-log stays Base — the case only segment matching catches', () => {
    // No entry maps /audit-log. With bare `startsWith`, `/audit` (AUDIT_LC)
    // claims it and every tenant without Internal Audit loses the platform
    // audit ledger — a Base capability, and the one an auditor asks for first.
    expect(skuForPath('/audit-log')).toBeNull();
    // ...while /audit itself must still be gated, or this test could pass by
    // the map having lost its AUDIT_LC entries entirely.
    expect(skuForPath('/audit')).toBe('AUDIT_LC');
    expect(skuForPath('/audit/ia/plans')).toBe('AUDIT_LC');
  });

  it('resolves the longest matching prefix', () => {
    expect(skuForPath('/bcm/crisis-room')).toBe('BCM');
    expect(skuForPath('/socops/detections')).toBe('SOCOPS');
    // /crisis (BCM) vs /crisis-management (BCM) — same answer, but proves the
    // longer prefix is the one consulted rather than the first match found.
    expect(skuForPath('/crisis-management')).toBe('BCM');
  });

  it('strips query strings and hashes', () => {
    // Several nav destinations are tab deep-links; without stripping they match
    // nothing and read as Base, so a paid module would never be hidden.
    expect(skuForPath('/ndi?tab=compliance')).toBe('DATA_AI_GOV');
    expect(skuForPath('/changes?tab=governance')).toBe('CHANGE');
    expect(skuForPath('/risk#section')).toBe('RISK');
  });
});

describe('the kernel is never gated (ADR-018 R4)', () => {
  it.each(BASE_PATHS)('%s is Base — no SKU', path => {
    expect(skuForPath(path)).toBeNull();
  });

  it('would black out the workflow engine if /workflows were ever gated', () => {
    // Stated explicitly because the consequence is not local: 14 modules adopted
    // the workflow engine, and the engine's own screens are Base.
    expect(skuForPath('/workflows')).toBeNull();
    expect(skuForPath('/workflows/requests/42')).toBeNull();
  });
});

describe('public routes are never gated (the F2-04a rule, carried to the UI)', () => {
  // Server-side, LicenseGuardAspect lets an anonymous caller through precisely so
  // a regulator-mandated channel cannot 403. A UI gate that blocked the same page
  // because the HOST tenant lacks the SKU would reintroduce that outage one layer
  // up, where no server fix reaches it.
  it.each([
    '/ethics/whistleblower/submit',          // Nazaha / SAMA CG Art.28 / CMA CG Art.87
    '/ethics/whistleblower/status/abc123',
    '/portal/speakup',
    '/portal/vendor',
    '/public/complaint',                     // SAMA consumer protection
    '/privacy/public/subprocessors',         // PDPL Art.12 transparency list
    '/tprm/attestations/tok/submit',         // vendor BCP attestation
  ])('%s is not gated', path => {
    expect(skuForPath(path)).toBeNull();
  });

  it('gates the authenticated sibling of a public path', () => {
    // The exemption is anonymity, not the URL prefix: /ethics/... is ETHICS
    // unless it is one of the public endpoints. Without this, listing the public
    // portal would exempt the entire ethics module.
    expect(skuForPath('/ethics')).toBe('ETHICS');
    expect(skuForPath('/ethics/whistleblower/cases')).toBe('ETHICS');
    expect(skuForPath('/privacy/subprocessors')).toBe('PDPL');
    expect(skuForPath('/tprm/bcp-campaigns')).toBe('TPRM');
  });

  it('keeps publicPaths as the single source shared with AppLayout', () => {
    // AppLayout used to own this list privately. If it grows a second copy the
    // two answers to "is this route public" can diverge, and the UI starts
    // refusing submissions the server accepts.
    const layout = readFileSync(join(SRC, 'components', 'layout', 'AppLayout.tsx'), 'utf8');
    expect(layout).toContain("from '../../lib/publicPaths'");
    expect(layout).not.toContain('BARE_PREFIXES = [');
    expect(BARE_PREFIXES.length).toBeGreaterThanOrEqual(9);
    expect(isBarePath('/login')).toBe(true);
  });
});

describe('every nav destination resolves to a decision', () => {
  it('maps or deliberately does not map each one, with no crashes', () => {
    for (const to of NAV_DESTINATIONS) {
      const sku = skuForPath(to);
      expect(sku === null || (RATIFIED_SKUS as readonly string[]).includes(sku)).toBe(true);
    }
  });

  it('gates a majority of nav destinations — the gate must actually bite', () => {
    // If almost everything resolved to Base, this feature would "pass" while
    // hiding nothing. Measured 2026-08-01: 141 of 193 destinations carry a SKU.
    const gated = NAV_DESTINATIONS.filter(to => skuForPath(to) !== null);
    expect(gated.length).toBeGreaterThanOrEqual(120);
  });

  it('leaves the My Work cockpit entirely ungated', () => {
    // Every role hits these every session; they are Base and pinned. If one ever
    // acquires a SKU, a tenant loses their task list.
    for (const to of ['/dashboard', '/workflows', '/approvals', '/signatures',
                      '/intake-inbox', '/notifications', '/calendar',
                      '/lifecycle-overview']) {
      expect(skuForPath(to), `${to} must stay Base`).toBeNull();
    }
  });
});

describe('nav filtering', () => {
  // Typed explicitly: inferred, `items` becomes a union of with- and
  // without-children shapes and `.children` stops type-checking on the branch
  // the assertions need. (vitest transpiles without checking, so this only
  // surfaced under `tsc --noEmit`.)
  const nav: { id: string; items: GatedNavItem[] }[] = [
    { id: 'risk',  items: [{ to: '/risk' }, { to: '/incidents' }, { to: '/dashboard' }] },
    { id: 'esg',   items: [{ to: '/esg' }, { to: '/green-finance' }] },
    { id: 'work',  items: [{ to: '/approvals' }] },
    { id: 'hub',   items: [{ to: '/bcm', children: [{ to: '/bcm/bia' }, { to: '/dashboard' }] }] },
  ];
  const only = (...skus: RatifiedSku[]) =>
    (sku: RatifiedSku | null) => sku === null || skus.includes(sku);

  it('drops items whose SKU is not licensed', () => {
    const out = filterNavByEntitlement(nav, only('RISK'));
    const risk = out.find(g => g.id === 'risk')!;
    expect(risk.items.map(i => i.to)).toEqual(['/risk', '/dashboard']); // /incidents is SOCOPS
  });

  it('drops a group that empties, header included', () => {
    const out = filterNavByEntitlement(nav, only('RISK'));
    expect(out.map(g => g.id)).not.toContain('esg');
    // ...and keeps the Base-only group, which has no SKU to lose.
    expect(out.map(g => g.id)).toContain('work');
  });

  it('keeps a parent alive for the sake of a licensed child', () => {
    // /bcm is BCM and gated out here, but its Base child /dashboard survives —
    // dropping the parent would take the licensed child with it.
    const out = filterNavByEntitlement(nav, only('RISK'));
    const hub = out.find(g => g.id === 'hub');
    expect(hub).toBeDefined();
    expect(hub!.items[0].children!.map((c: GatedNavItem) => c.to)).toEqual(['/dashboard']);
  });

  it('does not mutate the input', () => {
    const before = JSON.stringify(nav);
    filterNavByEntitlement(nav, only('RISK'));
    expect(JSON.stringify(nav)).toBe(before);
  });

  it('is a no-op when everything is licensed', () => {
    const out = filterNavByEntitlement(nav, () => true);
    expect(out).toHaveLength(nav.length);
    expect(out.flatMap(g => g.items)).toHaveLength(nav.flatMap(g => g.items).length);
  });

  it('fails OPEN on the real nav, not closed', () => {
    // The provider's pre-load state answers `true` to everything. On the real
    // NAV_GROUPS that must yield the full sidebar — if it yielded a stub, every
    // user would watch the nav pop in, and a licensing outage would look like a
    // downgrade.
    const real = NAV_DESTINATIONS.map(to => ({ to }));
    const out = filterNavByEntitlement([{ items: real }], () => true);
    expect(out[0].items).toHaveLength(real.length);
  });
});

describe('the gate is wired in, not merely defined', () => {
  // F2-04's whole finding was a complete subsystem with no callers: 22 of 252
  // controllers annotated, and `moduleEnabled()` with zero callers. A test that
  // only exercised skuForPath would have passed in exactly that world.
  it('AppLayout mounts the provider and the route gate', () => {
    const layout = readFileSync(join(SRC, 'components', 'layout', 'AppLayout.tsx'), 'utf8');
    expect(layout).toContain('<EntitlementProvider>');
    expect(layout).toContain('<ModuleGate><Outlet /></ModuleGate>');
  });

  it('Sidebar renders the filtered groups, not NAV_GROUPS directly', () => {
    expect(sidebar).toContain('filterNavByEntitlement');
    expect(sidebar).toContain('{visibleGroups.map(group => {');
    expect(sidebar, 'the render still walks the unfiltered list')
      .not.toContain('{NAV_GROUPS.map(group => {');
  });

  it('the licensing admin screen refreshes the gate after a change', () => {
    const page = readFileSync(join(SRC, 'pages', 'admin', 'LicensingPage.tsx'), 'utf8');
    expect(page).toContain('refreshEntitlement()');
  });

  it('moduleEnabled() — the helper F2-04 named — finally has a caller', () => {
    // It shipped with the entitlement subsystem and sat unused for four months.
    // Reimplementing the membership check inside the provider and leaving this
    // orphaned would have reproduced the finding while appearing to fix it.
    const entitlement = readFileSync(join(SRC, 'lib', 'entitlement.tsx'), 'utf8');
    expect(entitlement).toContain('moduleEnabled(cache, sku)');
    expect(entitlement).toMatch(/import \{[^}]*moduleEnabled[^}]*\} from '\.\/licensing'/);
  });
});
