import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

/*
 * ─────────────────────────────────────────────────────────────────────────────
 * F2-207 — a `?tab=` link must land on a page that honours `?tab`.
 *
 * Found while retiring the browser-local PDPL registers. Three KPI tiles on
 * `PrivacyLifecyclePage` linked `to="?tab=dsr"`, and the page held its tab in `useState` and
 * never read the query string — so each of those tiles navigated, re-mounted the page on the
 * PIPELINE tab, and looked to the operator like a control that does nothing.
 *
 * This is F2-197's rule one step earlier. That finding was about a tile whose FIGURE disagrees
 * with the list it opens; here the NAVIGATION itself is the false claim, and it is worse in one
 * respect: a wrong figure invites a question, whereas a link that silently lands somewhere else
 * reads as the reader's own mistake.
 *
 * ⚠⚠ MY FIRST DETECTOR REPORTED A POPULATION OF 1 AND THAT WAS WRONG. It only looked at
 * RELATIVE links (`to="?tab=x"`), because that was the shape I had just found. `AuditWorkspacePage`
 * renders `<Link to="/audit-workspace?tab=engagements">` — an ABSOLUTE link to its own route —
 * and `SocOpsEventsPage` links `/cyber?tab=incidents` to a `CyberPage` that also ignored `?tab`.
 * The honest question is "does the page this link OPENS honour the tab?", regardless of how the
 * link is spelled; asked that way the answer was **5 links across 3 pages, of 22 total**.
 * 🔑 Scoping a detector to the shape that produced the finding is the failure this register
 * keeps recording (F2-192's headline); it reports a smaller population, which reads exactly like
 * "the rest are fine."
 *
 * So this ships as a HARD ZERO with no exemption map — all three sites are fixed:
 *   • `PrivacyLifecyclePage` + `CyberPage` now derive `tab` from the URL (the idiom
 *     `PrivacyPage` already used), so their inbound links work.
 *   • `AuditWorkspacePage`'s link is DELETED rather than honoured: `WsTabId` never had an
 *     `engagements` member, so there was no tab to open, and the list behind it is the
 *     fabricated `AUDIT_ENGAGEMENTS` corpus F2-203 filed. Building a tab over invented rows is
 *     the defect, not the fix.
 * ─────────────────────────────────────────────────────────────────────────────
 */

const SRC = join(__dirname, '..');
const read = (p: string) => readFileSync(join(SRC, p), 'utf8');

/** F2-198's alternation: line comments FIRST, in one left-to-right pass. */
const stripComments = (s: string) => s.replace(/(?<!:)\/\/[^\n]*|\/\*[\s\S]*?\*\//g, '');

/**
 * Every link target containing `?tab=`, however it is spelled.
 *
 * ⚠ TWO shapes, and the second was missing from the first draft — which would have made this
 * gate blind to the three links THIS SLICE ADDED. `PdplRegisterLinks` declares its targets in
 * an array (`{ to: '/privacy?tab=ropa', … }`) and renders `<Link to={x.to}>`, so a JSX-only
 * pattern sees `to={x.to}` and never the string. A gate that cannot see the links written the
 * way this codebase writes them certifies nothing.
 */
const TAB_LINK = /to[=:]\s*\{?["'`]([^"'`]*\?tab=[^"'`]*)["'`]/g;

/** A page honours `?tab` if it reads that param from the query string. */
const READS_TAB = /(?:search)?[Pp]arams\.get\(\s*['"]tab['"]/;

function tsxFiles(dir: string): string[] {
  const out: string[] = [];
  const walk = (rel: string) => {
    for (const e of readdirSync(join(SRC, rel), { withFileTypes: true })) {
      const child = rel ? `${rel}/${e.name}` : e.name;
      if (e.isDirectory()) {
        if (e.name === '__tests__' || e.name === 'node_modules') continue;
        walk(child);
      } else if (e.name.endsWith('.tsx')) {
        out.push(child);
      }
    }
  };
  walk(dir);
  return out;
}

/** route path -> component name, read from the router itself. */
function routeTable(): Map<string, string> {
  const app = stripComments(read('App.tsx'));
  const map = new Map<string, string>();
  for (const m of app.matchAll(/<Route\s+path="([^"]+)"\s+element=\{<(\w+)/g)) {
    map.set(m[1], m[2]);
  }
  return map;
}

/** component name -> the file that exports it. */
function componentFiles(): Map<string, string> {
  const map = new Map<string, string>();
  for (const f of tsxFiles('')) {
    for (const m of read(f).matchAll(/export\s+(?:default\s+)?function\s+(\w+)/g)) {
      if (!map.has(m[1])) map.set(m[1], f);
    }
  }
  return map;
}

interface Link { from: string; to: string; target: string }

function classify() {
  const routes = routeTable();
  const comps = componentFiles();
  const honoured: Link[] = [];
  const ignored: Link[] = [];
  const unresolved: Link[] = [];
  const viaRedirect: Link[] = [];

  for (const f of tsxFiles('')) {
    const src = stripComments(read(f));
    for (const m of src.matchAll(TAB_LINK)) {
      const to = m[1];
      const link: Link = { from: f, to, target: '' };
      if (to.startsWith('?')) {
        // Relative — it targets the page it is written on.
        link.target = `(self) ${f}`;
        (READS_TAB.test(src) ? honoured : ignored).push(link);
        continue;
      }
      const path = to.split('?')[0];
      const comp = routes.get(path);
      link.target = `${path} -> ${comp ?? 'no route'}`;
      // ⚠⚠ A route whose element is `<Navigate>` is its OWN defect class, and this gate caught
      // it on the link THIS SLICE WROTE. `/pdpl-extensions` redirects to `/data-lifecycle`, and
      // React Router's `<Navigate to="/x">` does not carry the search string over — so
      // `/pdpl-extensions?tab=cbt` arrives at `/data-lifecycle` with no `?tab` at all. It looked
      // right because that page's default tab happens to be `cbt`; the link was correct by luck
      // and would have broken the moment the default changed. F2-202's rule, one layer out: a
      // plausible path string is worth nothing until something resolves it.
      if (comp === 'Navigate') { viaRedirect.push(link); continue; }
      const targetFile = comp ? comps.get(comp) : undefined;
      if (!targetFile) unresolved.push(link);
      else if (READS_TAB.test(stripComments(read(targetFile)))) honoured.push(link);
      else ignored.push(link);
    }
  }
  return { honoured, ignored, unresolved, viaRedirect, routes };
}

describe('F2-207 — every ?tab= link opens a page that honours it', () => {
  it('the router and the link population are both resolvable', () => {
    // ⚠ A guard against the quietest failure mode: if the route regex stops matching, every
    // link becomes "unresolved" and a naive gate would report zero defects. So the census sizes
    // are asserted as floors — a collapse fails here rather than passing silently.
    const { honoured, ignored, unresolved, viaRedirect, routes } = classify();
    expect(routes.size, 'no routes parsed from App.tsx — the scan has rotted')
      .toBeGreaterThan(200);
    // ⚠ The floor is the number THIS detector returns (22), not the 25 my first draft used —
    // that came from a scan of a different population (it walked `.ts` too and counted a
    // regex inside a test file). F2-195's rule: a baseline lifted from a measurement of
    // something else is worse than no baseline, because it looks measured.
    expect(honoured.length + ignored.length + unresolved.length + viaRedirect.length,
      'no ?tab= links found at all — the scan has rotted').toBeGreaterThanOrEqual(22);
    expect(unresolved.map(l => `${l.from} -> "${l.to}" [${l.target}]`),
      'a ?tab= link points at a path with no <Route> — it can only 404 or fall through to the '
      + 'catch-all').toEqual([]);
  });

  it('no link routes its tab through a redirect, which would drop the query string', () => {
    const { viaRedirect } = classify();
    expect(
      viaRedirect.map(l => `${l.from} -> "${l.to}" [${l.target}]`),
      'React Router\'s <Navigate to="/x"> does not carry the search string, so the tab is lost '
      + 'in transit. Link to the route that renders the page, not to the redirect that points '
      + 'at it.',
    ).toEqual([]);
  });

  it('no link lands on a page that ignores the tab (hard zero, no exemptions)', () => {
    const { ignored } = classify();
    expect(
      ignored.map(l => `${l.from} -> "${l.to}" [target ${l.target} never reads ?tab]`),
      'A link that names a tab and lands on the default one is a control that appears to do '
      + 'nothing. Fix it by deriving the target page\'s tab from the URL (see PrivacyPage), or '
      + 'delete the link if the tab it names does not exist.',
    ).toEqual([]);
  });

  /*
   * ⚠⚠ A LEG I WROTE, RAN, AND DELETED — recorded because the deletion is the finding.
   *
   * It asserted "every page that reads ?tab narrows it to a declared tab", which is TRUE: I
   * checked all 12 by reading them. But the detector flagged `ChangesPage`, `CompliancePage`
   * and `EsgPage` — 3 for 3 false positives — because each narrows it a different way:
   *     ChangesPage     `TAB_IDS.includes(tabParam)`
   *     CompliancePage  `isTabId(urlTab)`            (a type-predicate helper)
   *     EsgPage         `isEsgTab(initialTab)`       (a differently-named predicate)
   *     PrivacyPage     `VALID_TABS.includes(urlTab)`
   * plus the two this slice added. That is at least four spellings of one idea, and "is this
   * value narrowed before use?" is a dataflow question a regex cannot answer (F2-182's rule).
   *
   * 🔑 A gate that fails on correct code is worse than no gate: it trains the next reader to
   * edit the assertion rather than the code. The claim is true, cheap to re-check by hand, and
   * not mechanically checkable at this cost — so it is written down here instead of asserted.
   * What IS mechanically sound is the leg above: whether the target page reads `?tab` at all.
   */
});

describe('F2-207 — the audit workspace link that named a tab it never had', () => {
  it('AuditWorkspacePage no longer links to an engagements tab', () => {
    const src = stripComments(read('pages/audit/AuditWorkspacePage.tsx'));
    expect(src, 'the link is deleted, not repointed — see the comment at the removal site')
      .not.toContain('?tab=engagements');
  });

  it('and the tab id it named is still absent from the union, which is why', () => {
    // The reverse leg. If someone later adds a real `engagements` tab this assertion fails and
    // they are told to reconsider the link — rather than the ban above quietly outliving its
    // reason. F2-199's lesson: the reverse direction is the one nobody writes first.
    const src = stripComments(read('pages/audit/AuditWorkspacePage.tsx'));
    const union = src.match(/type WsTabId\s*=\s*([^;]+);/);
    expect(union, 'type WsTabId not found').not.toBeNull();
    expect(union![1], 'an engagements tab now exists — revisit the deleted "view all" link, and '
      + 'note that `myEngagements` is still the fabricated AUDIT_ENGAGEMENTS corpus (F2-203)')
      .not.toContain('engagements');
  });
});
