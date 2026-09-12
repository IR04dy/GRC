import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { attentionHref } from '../pages/dashboard/ExecutiveDashboard';
import { stripComments } from './support/sourceScan';

/**
 * JRN-08 — the executive dashboard's "Needs decision" rows must be able to reach their records.
 *
 * <h3>What was wrong</h3>
 * `AttentionStrip` builds the list from REAL data — `riskApi.list` and `complianceApi.findings`,
 * filtered to open CRITICAL/HIGH — and then dropped the primary key. `AttentionItem` was
 * `{ titleEn, titleAr, sev, age }`, so the list was not merely un-linked, it was **un-linkable**:
 * the renderer had nothing to navigate to. And because the list mixes two record types, carrying
 * the id also means carrying a discriminator and resolving two destinations.
 *
 * <h3>🔑 Why adding a field to the type would not have been enough</h3>
 * The four-field shape was declared TWICE — once as `type AttentionItem` and again, inline and
 * identical, as `AttentionList`'s parameter type. `decisions` is a variable reference rather than
 * a fresh object literal, so TypeScript's excess-property check never fires (F2-211's mechanism):
 * adding `id` to the named type alone still compiles, and the renderer still cannot read `it.id`.
 * Two copies of one shape with nobody maintaining the agreement (F2-177), which is why
 * {@code theItemShapeIsDeclaredOnce} is a leg rather than a tidy-up.
 *
 * <h3>What is NOT fixed here, and why</h3>
 * The findings half writes `titleAr: f.title` — the same English string in both languages on an
 * Arabic-primary UI — and carries no reference code. Adjudicated against the entity rather than
 * the client (F2-211): `compliance.findings` (`AuditFinding`) declares a single `title` column and
 * no code column at all, so neither is available to this component. That gap is the register's and
 * needs a migration; filed rather than papered over.
 *
 * <h3>Which legs discriminate (F2-225)</h3>
 * The shape, wiring and route legs fire on the pre-fix component. {@code everyDestinationResolves}
 * is a <b>tripwire</b>: both routes resolve today, and it exists so a typo'd `/risks/` — the plural
 * that does not exist — fails the build rather than being found by clicking (F2-464).
 */

const ROOT = join(process.cwd(), 'src');
const PAGE = join(ROOT, 'pages', 'dashboard', 'ExecutiveDashboard.tsx');

function source(): string {
  // ⚠ Comments stripped: the fix documents the retired inline shape and the removed MEDIUM token
  // by name, so a comment-blind scan reads the epitaph as the corpse (F2-193) and fails on a
  // correct tree.
  return stripComments(readFileSync(PAGE, 'utf8'));
}

/** Routes `App.tsx` declares, as written. */
function declaredRoutes(): Set<string> {
  const app = stripComments(readFileSync(join(ROOT, 'App.tsx'), 'utf8'));
  const out = new Set<string>();
  for (const m of app.matchAll(/path="([^"]+)"/g)) {
    const p = m[1];
    out.add(p.startsWith('/') ? p : `/${p}`);
  }
  return out;
}

/** The body of one top-level function, so a leg cannot be satisfied by a sibling component. */
function functionBody(src: string, name: string, nextName: string): string {
  const start = src.indexOf(`function ${name}(`);
  const end = src.indexOf(`function ${nextName}(`);
  expect(start, `${name} not found`).toBeGreaterThan(-1);
  expect(end, `${nextName} not found`).toBeGreaterThan(start);
  return src.slice(start, end);
}

describe('JRN-08 — a decision row must be able to reach its record', () => {
  it('a destination is derived per record type', () => {
    expect(attentionHref('risk', 42)).toBe('/risk/42');
    expect(attentionHref('finding', 7)).toBe('/findings/7');
  });

  it('every destination resolves to a route this app declares', () => {
    const declared = declaredRoutes();
    // Turn a concrete href back into the route pattern it must match.
    const patterns = (['risk', 'finding'] as const)
      .map(kind => attentionHref(kind, 1).replace(/\/1$/, '/:id'));

    const stray = patterns.filter(p => !declared.has(p));
    expect(stray, 'A decision row pointing at a route nothing declares navigates nowhere, and a '
      + 'stray string is indistinguishable from a considered destination. ⚠ `/risk/:id` is '
      + 'SINGULAR and `/findings/:id` is plural — the obvious guess for either one is wrong, which '
      + 'is exactly why this is resolved rather than read (F2-464).').toEqual([]);
    expect(declared.size, 'declared routes').toBeGreaterThan(200);
  });

  it('the item shape is declared once', () => {
    const src = source();
    const list = functionBody(src, 'AttentionList', 'DeadlineList');

    expect(list.includes('items: AttentionItem[]'),
      'AttentionList must take the named type. It used to re-declare the identical fields inline, '
      + 'which is why adding `id` to the type alone would have compiled and reached nothing.')
      .toBe(true);
    expect(/items:\s*Array<\{/.test(list),
      'An inline re-declaration of the row shape is the second copy this finding is about (F2-177).')
      .toBe(false);
  });

  it('both halves of the list carry the id and the discriminator', () => {
    const strip = functionBody(source(), 'AttentionStrip', 'ageLabel');

    for (const [kind, idExpr] of [['risk', 'r.id'], ['finding', 'f.id']] as const) {
      expect(strip.includes(`kind: '${kind}',`),
        `The ${kind} push must say which record type it is — the list mixes two, so an id alone `
        + 'cannot resolve a destination.').toBe(true);
      expect(strip.includes(`id: ${idExpr},`),
        `The ${kind} push must carry the primary key. Dropping it is what made the list `
        + 'un-linkable rather than merely un-linked.').toBe(true);
    }
  });

  it('the rows are links, and they call the derivation rather than composing a path', () => {
    const list = functionBody(source(), 'AttentionList', 'DeadlineList');

    expect(/attentionHref\s*\(/.test(list),
      'A leg that exercises the primitive says nothing about whether the component CALLS it '
      + '(F2-274). Assert the call shape.').toBe(true);
    expect(list.includes('<Link'),
      'The row itself is the affordance — a Badge and text with no destination is the defect.')
      .toBe(true);
    expect(/to=\{`\/(?:risk|findings)\//.test(list),
      'A path composed inline here would be a second copy of the route map, free to drift from '
      + 'the one the gate above resolves (F2-177).').toBe(false);
  });

  it('the severity union carries no token the list cannot produce', () => {
    const src = source();
    expect(src.includes("'CRITICAL' | 'HIGH' | 'MEDIUM'"),
      'Both pushes filter to CRITICAL/HIGH and map to one of the two, so MEDIUM was unreachable — '
      + 'and the badge is a TWO-way branch over the union, so a MEDIUM row would have rendered '
      + '"MEDIUM" in English and "عالي" (HIGH) in Arabic. Latent, and unrepresentable now rather '
      + 'than latent (F2-213).').toBe(false);
    expect(src.length, 'anti-vacuity — a stripper that emptied the file would pass every assertion '
      + 'above (F2-198)').toBeGreaterThan(20000);
  });
});
