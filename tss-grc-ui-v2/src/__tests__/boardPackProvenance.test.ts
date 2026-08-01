import { describe, expect, it } from 'vitest';
import { existsSync, readFileSync } from 'node:fs';
import { join } from 'node:path';
import { filterNavByEntitlement, type GatedNavItem } from '../lib/entitlement';
import { stripComments } from './support/sourceScan';

/**
 * F2-42 / ADR-017 D-5 — the board pack has one source, and it is the server.
 *
 * <p>The platform had <b>two</b> board packs. `/board-pack` rendered
 * `buildBoardPack()` from eight fabricated sources — three of them named
 * `mockCompliance`, `mockPrivacy`, `mockWorkflows`, one a function called
 * `synthesiseAllBreaches()` — while `BoardPackService` built a real pack from live
 * repositories and served it as PDF and PPTX. The screen and the download
 * disagreed, and the download was right.
 *
 * <p>These are provenance assertions, not rendering ones. What matters about this
 * page is <b>where its numbers come from</b>, and that is a property of which
 * modules it imports — so that is what is checked. The figures themselves are
 * verified where they are computed (`BoardPackAggregateServiceTest`) and end-to-end
 * against the running stack.
 *
 * <p>⚠ The single most important assertion here is the last one: that the page does
 * not fetch through `useApiList`. That hook substitutes supplied mock data whenever
 * a request fails or returns zero rows. Wiring the board pack through it would
 * re-create F2-42 exactly — fabricated board figures, shown silently — while every
 * other test in this file still passed.
 */

const SRC = join(__dirname, '..');
const page = readFileSync(join(SRC, 'pages', 'board-pack', 'BoardPackPage.tsx'), 'utf8');
const client = readFileSync(join(SRC, 'lib', 'boardPackAggregate.ts'), 'utf8');
const sidebar = readFileSync(join(SRC, 'components', 'layout', 'Sidebar.tsx'), 'utf8');

/**
 * Source with comments removed.
 *
 * ⚠ Required, not tidiness. Both files deliberately *name* what they replaced —
 * `buildBoardPack()`, `useApiList` — in prose explaining the history. The first
 * draft of this file asserted over raw source and failed on its own
 * documentation: a regex counting a comment as code. That is the **third** time in
 * this programme a source scan has done exactly this (F2-04a: `@RestControllerAdvice`
 * containing `@RestController`, a `package-info.java` describing its own layout,
 * and `RequireModule.java`'s own usage example). Keeping the prose and stripping it
 * here is better than deleting the explanation to satisfy a regex.
 *
 * ⚠ **Corrected by F2-68(c):** the stripper this file originally shipped — strip block
 * comments, then strip line comments — is unsound. A `//` line containing a block-OPEN
 * marker opens a phantom block comment that runs to the next block-CLOSE marker
 * anywhere later in the file, and it mangled 23 files here. **It did not change any
 * conclusion here** — both files below come out byte-identical under either version,
 * re-measured — but over-stripping makes *absence* assertions pass for the wrong
 * reason, so relying on that would have been luck. It now uses the shared sound
 * scanner, whose own soundness is pinned in `apiClientReachability.test.ts`.
 */
const pageCode = stripComments(page);
const clientCode = stripComments(client);

describe('the fabricated board pack is gone', () => {
  it('lib/boardPack.ts no longer exists', () => {
    expect(existsSync(join(SRC, 'lib', 'boardPack.ts')),
      'the fabricated builder is back — F2-42 regressed').toBe(false);
  });

  it('nothing calls buildBoardPack()', () => {
    // Asserted over comment-stripped source: the page still names it in prose
    // explaining the history, and that documentation is worth keeping.
    expect(pageCode).not.toMatch(/buildBoardPack\s*\(/);
    expect(clientCode).not.toContain('buildBoardPack');
    // ...and the prose really is still there, so this test cannot pass because the
    // explanation was deleted to satisfy it.
    expect(page).toContain('buildBoardPack');
  });

  it('the page imports no mock or synthesised module', () => {
    // Each of these was a real import of the old page, by name.
    const banned = [
      'mockCompliance', 'mockPrivacy', 'mockWorkflows',
      'synthesiseAllBreaches', 'riskAppetiteSaudi', 'appetiteBreach',
      'CCM_CHECKS', 'CHAIN_RULES', 'APPROVAL_REQUESTS', 'AUDIT_ENGAGEMENTS',
    ];
    const found = banned.filter(b => new RegExp(`import[^;]*\\b${b}\\b`, 's').test(page));
    expect(found, `fabricated sources re-imported: ${found.join(', ')}`).toEqual([]);
  });

  it('the sample-data banner is gone, because the figures are real', () => {
    // Removing the banner is only honest once the data is live. If the banner text
    // is absent AND the aggregate fetch is absent, that is the dishonest
    // combination — so this asserts the pairing, not just the removal.
    expect(page).not.toContain('Sample data —');
    expect(page).toContain('fetchBoardPack');
  });
});

describe('the page reads the server aggregate', () => {
  it('fetches the same endpoint that produces the PDF and the deck', () => {
    expect(client).toContain("'/api/v1/reporting/board-pack.json'");
  });

  it('does NOT use useApiList — its demo fallback would rebuild F2-42', () => {
    // The hook takes mock data and substitutes it on failure or zero rows,
    // surfacing isDemo. Correct for a register; on a board pack it is the defect.
    // Comment-stripped, because both files name the hook in prose saying why not.
    expect(pageCode).not.toContain('useApiList');
    expect(clientCode).not.toContain('useApiList');
  });

  it('renders explicit forbidden / error states instead of falling back', () => {
    expect(page).toContain("'forbidden'");
    expect(page).toContain("e.status === 403");
    expect(page).toContain('board-pack-forbidden');
    expect(page).toContain('board-pack-error');
  });

  it('distinguishes a 403 from a failure', () => {
    // A 403 is the BOARD:EXPORT guard working. Rendering it as an error would turn
    // a permissions conversation into a bug report — and rendering an error as a
    // 403 would hide a real outage.
    const forbiddenIdx = page.indexOf("setState('forbidden')");
    const errorIdx = page.indexOf("setState('error')");
    expect(forbiddenIdx).toBeGreaterThan(-1);
    expect(errorIdx).toBeGreaterThan(-1);
    expect(forbiddenIdx, 'the 403 branch must be checked before the generic error branch')
      .toBeLessThan(errorIdx);
  });

  it('never renders an absent figure as zero', () => {
    // A board pack showing 0 tCO2e is a claim; showing an em dash is an absence.
    // The distinction is the whole reason `available` exists on the wire.
    expect(page).toContain("!pack.sustainability?.available");
    expect(page).toContain('bpSustUnavailable');
    expect(page).toMatch(/function fmt\([\s\S]*?'—'/);
  });
});

describe('the nav entry matches who the server will serve', () => {
  it('is gated on BOARD:EXPORT', () => {
    expect(sidebar).toMatch(/id: 'board-pack'[\s\S]{0,400}?gate: \(\) => canExport\('BOARD'\)/);
  });

  it('the gate is actually applied by the nav filter', () => {
    const shown = filterNavByEntitlement(
      [{ items: [{ to: '/board-pack', gate: () => true }, { to: '/dashboard' }] }],
      () => true);
    expect(shown[0].items.map(i => i.to)).toEqual(['/board-pack', '/dashboard']);

    const hidden = filterNavByEntitlement(
      [{ items: [{ to: '/board-pack', gate: () => false }, { to: '/dashboard' }] }],
      () => true);
    expect(hidden[0].items.map(i => i.to)).toEqual(['/dashboard']);
  });

  it('an item with no gate is unaffected', () => {
    // `gate?.() !== false` — an absent gate and a passing gate must be identical.
    const out = filterNavByEntitlement([{ items: [{ to: '/dashboard' }] }], () => true);
    expect(out[0].items).toHaveLength(1);
  });

  it('a gate can empty a group, header included', () => {
    const out = filterNavByEntitlement(
      [{ items: [{ to: '/board-pack', gate: () => false }] }], () => true);
    expect(out).toEqual([]);
  });

  it('gates children too', () => {
    const nav: { items: GatedNavItem[] }[] = [{
      items: [{ to: '/dashboard', children: [
        { to: '/board-pack', gate: () => false },
        { to: '/reports/centre' },
      ] }],
    }];
    const out = filterNavByEntitlement(nav, () => true);
    expect(out[0].items[0].children!.map(c => c.to)).toEqual(['/reports/centre']);
  });

  it('entitlement and permission are independent — either alone can hide a row', () => {
    // /board-pack is BOARD (a SKU) and BOARD:EXPORT (a permission). They answer
    // different questions: did the tenant buy it, and may this user see it.
    const noSku = filterNavByEntitlement(
      [{ items: [{ to: '/board-pack', gate: () => true }] }],
      sku => sku !== 'BOARD');
    expect(noSku, 'unlicensed tenant must not see it even with the permission').toEqual([]);

    const noPerm = filterNavByEntitlement(
      [{ items: [{ to: '/board-pack', gate: () => false }] }], () => true);
    expect(noPerm, 'licensed tenant without the permission must not see it either').toEqual([]);
  });
});
