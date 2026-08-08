import { describe, it, expect } from 'vitest';
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-16 — the page-size ratchet the plan asks for: "no new file > 800 lines".
 *
 * <b>Why a ratchet now, when the splits are not done.</b> F2-16 and F2-12 are the real work —
 * `EsgPage.tsx` and `NdiPage.tsx` (4,969) split along tab boundaries. Those are multi-session
 * refactors. This is the cheap half that stops the problem growing in the meantime, and it is listed
 * in the row as part of the deliverable rather than invented here.
 *
 * <b>⚠ This javadoc said `EsgPage` goes first "because `sust` has ~200 tests to catch mistakes and
 * `ndi` has almost none". That was false, and it was repeated from the F2-16 row.</b> Those ~200 are
 * <b>backend</b> tests for `module/sust`. Measured 2026-08-08: the only file in the frontend that
 * named `EsgPage.tsx` was this one, which counts its lines — the page had <b>no behavioural test at
 * all</b>. `EsgPage` still goes first, because it is the larger file and `ndi` needs F2-12's
 * characterisation tests over its 82 writes first; but <b>a test count for a backend module is not
 * coverage of the page that calls it</b>. Tranche 1 (2026-08-08) took it 5,583 → 4,994 and gave it
 * its first 9 tests, in `pages/esg/EsgOverviewTab.test.tsx`; tranche 2 took it <b>4,994 → 3,201</b>
 * by moving the four regulatory tabs (CMA / TCFD / materiality / metrics) into a file each, with 18
 * more tests in `pages/esg/EsgRegulatoryTabs.test.tsx`; tranche 3 took it <b>3,201 → 564</b> with the
 * seven operational tabs (climate, CAPA, evidence, annex, GHG, SGI, settings).
 *
 * <b>So `EsgPage.tsx` is GONE from `OVERSIZE` below — 5,583 → 564, a tenth of where it started.</b>
 * What remains in it is the shell, the tab switch, `CalculatorTab` (120) and `SurveyTab` (186), and
 * the shared `Field` / `Stat` / `inputStyle` primitives those two use. Those two tabs were left
 * deliberately: moving either one means moving the primitives into a shared module in the same
 * commit — the page and its own tabs must not import from each other, and `inputStyle` is a `const`,
 * so a cycle there is a TDZ hazard rather than a style question. At 564 lines the ceiling this
 * ratchet exists to enforce is met, so that move is now optional rather than owed.
 *
 * <b>Why a pinned SET and not a count.</b> A count ceiling lets a new 900-line page appear as long as
 * something else shrinks — which is precisely the trade the row is trying to prevent. Pinning the
 * paths means a NEW offender fails even while the total falls. Same shape as F2-38's
 * `DIRECTORY_IMPORTERS` list, for the same reason.
 *
 * <b>⚠ Translations are exempt, and that is a judgement worth stating.</b> `translations/en.ts`
 * (6,658 lines) and `ar.ts` (6,633) are the two largest files in the frontend and are excluded: they
 * are flat key-value tables, one line per string, and splitting them would scatter a lookup that reads
 * correctly as one unit while making a missing-key diff harder. The row's target is *pages*. Excluding
 * them is a decision, so it is named here rather than hidden in a glob.
 */
describe('F2-16 — source files over 800 lines may only shrink in number, and no new one may appear', () => {
  const SRC = join(__dirname, '..');
  const LIMIT = 800;

  /**
   * Files over the limit, measured 2026-08-06. **This list may only shrink.**
   *
   * Do not add to it. If a change would push a new file over 800 lines, split the file — that is the
   * whole point of the ratchet, and every entry below is a file someone will eventually have to split
   * anyway. The three that matter most are at the bottom: `EsgPage.tsx` and `NdiPage.tsx` are F2-16 and
   * F2-12, and `lib/api.ts` is a single 4,160-line module that every page imports.
   */
  const OVERSIZE: readonly string[] = [
    'App.tsx',
    'components/layout/Sidebar.tsx',
    'lib/aiGovernance.ts',
    'lib/api.ts',
    'lib/auditLifecycle.ts',
    'lib/bcmLifecycle.ts',
    'lib/capa.ts',
    'lib/committees.ts',
    'lib/cyberLifecycle.ts',
    'lib/intakeForms.ts',
    'lib/socops.ts',
    'lib/stakeholders.ts',
    'pages/ai/AiModelDetailPage.tsx',
    'pages/audit/AuditWorkspacePage.tsx',
    'pages/bcm-lifecycle/BcmLifecyclePage.tsx',
    'pages/bcm/BcmPage.tsx',
    'pages/bcm/BcmReadinessPage.tsx',
    'pages/bcm/BcmReportsPage.tsx',
    'pages/bcm/BcpBuilderPage.tsx',
    'pages/calendar/CalendarPage.tsx',
    'pages/capa/CapaPage.tsx',
    'pages/committees/CommitteesPage.tsx',
    'pages/compliance/CompliancePage.tsx',
    'pages/controls/ControlLibraryPage.tsx',
    'pages/cyber/CyberPage.tsx',
    'pages/dashboard/ExecutiveDashboard.tsx',
    'pages/dataOfficer/OtherPages.tsx',
    // ✔ `pages/esg/EsgPage.tsx` LEFT THIS LIST on 2026-08-08 — F2-16 tranche 3 took it to 564 lines,
    // from the 5,583 that made it the largest file in the frontend. It is not commented out here as a
    // placeholder: the stale-entry check below fails on any entry that is no longer oversized, which
    // is what forced this deletion rather than leaving the list overstating the debt.
    'pages/esg/GreenFinancePage.tsx',
    'pages/ethics/EthicsPage.tsx',
    'pages/fraud/FraudCaseDetailPage.tsx',
    'pages/ndi/NdiPage.tsx',
    'pages/outsourcing/OutsourcingDetailPage.tsx',
    'pages/policy/PolicyManagementPage.tsx',
    'pages/regulatory/RegulatoryLibraryPage.tsx',
    'pages/risk/RiskRegisterPage.tsx',
    'pages/scm/ScmPage.tsx',
    'pages/tprm/TprmPage.tsx',
  ];

  /** Flat key-value string tables — see the class note. */
  const EXEMPT_PREFIXES = ['translations/'];

  function sources(dir: string, out: string[] = []): string[] {
    for (const name of readdirSync(dir)) {
      const full = join(dir, name);
      if (statSync(full).isDirectory()) {
        if (name === 'node_modules' || name === '__tests__') continue;
        sources(full, out);
      } else if (/\.(ts|tsx)$/.test(name) && !/\.test\.tsx?$/.test(name)) {
        out.push(full);
      }
    }
    return out;
  }

  function relative(abs: string): string {
    return abs.slice(SRC.length + 1).split('\\').join('/');
  }

  /**
   * ⚠ `split('\n').length` counts one more than a line-iterator does when the file ends with a
   * newline, which nearly every file here does. The figures in `OVERSIZE`'s comment were taken with an
   * iterator, so this reports e.g. 1272 for the file measured at 1271. Immaterial against a threshold
   * of 800, but recorded because two counts of the same thing disagreeing by one is how a figure
   * quietly becomes untrustworthy — and this session has spent a lot of time on exactly that.
   */
  function lineCount(abs: string): number {
    return readFileSync(abs, 'utf8').split('\n').length;
  }

  function measure(): Map<string, number> {
    const m = new Map<string, number>();
    for (const abs of sources(SRC)) {
      const rel = relative(abs);
      if (EXEMPT_PREFIXES.some(p => rel.startsWith(p))) continue;
      const n = lineCount(abs);
      if (n > LIMIT) m.set(rel, n);
    }
    return m;
  }

  it('no file outside the pinned list exceeds 800 lines', () => {
    const over = measure();
    const unexpected = [...over.keys()].filter(f => !OVERSIZE.includes(f)).sort();

    expect(unexpected,
      `${unexpected.map(f => `${f} (${over.get(f)} lines)`).join(', ')} exceeds ${LIMIT} lines and is `
      + 'not on the pinned list. Split it rather than adding it: a count-based ceiling would let a new '
      + 'oversized page in whenever another shrank, which is exactly the trade F2-16 exists to '
      + 'prevent.').toEqual([]);
  });

  it('the pinned list stays honest — a file that was split must leave it', () => {
    const over = measure();
    const stale = OVERSIZE.filter(f => !over.has(f)).sort();

    // Honest in both directions. A stale entry means someone did the work and the list now overstates
    // the debt, which makes the ratchet look tighter than it is — the same failure the F2-38 guard's
    // stale-entry check exists for.
    expect(stale,
      `${stale.join(', ')} is pinned as oversized but is now under ${LIMIT} lines (or was renamed or `
      + 'deleted). Remove it from OVERSIZE so the list keeps measuring what it claims.').toEqual([]);
  });

  it('the scan sees the frontend it claims to — a broken walk must not read as zero offenders', () => {
    const all = sources(SRC).map(relative);

    // Without this, a walk that returned nothing would make both assertions above vacuously pass:
    // zero offenders and zero stale entries only if the pinned list were also empty — but the stale
    // check would then fail, so this floor is really about the first assertion. Cheap, and it is the
    // instrument-check discipline that F2-79 established.
    expect(all.length,
      `the source walk found ${all.length} files; the frontend has ~750 non-test sources. A collapsed `
      + 'walk reports zero oversized files.').toBeGreaterThan(500);

    expect(all).toContain('pages/esg/EsgPage.tsx');

    // ⚠ This assertion has now fired TWICE, both times correctly, and it is worth understanding why
    // rather than just reading its current form. It began as `> 4000` with the message "if it is no
    // longer oversized the split happened and this test should be updated deliberately, not left
    // asserting a stale shape". Tranche 2 (→ 3,201) fired it, so it became a two-sided 800 < n < 3,300
    // — two-sided on purpose, because a one-sided "still oversized" check would wave through a tranche
    // that GREW the page. Tranche 3 (→ 564) fired it again, and this is the terminal state: the file
    // is out of OVERSIZE, so `measure()` no longer contains it at all and the bound has to invert.
    expect(measure().has('pages/esg/EsgPage.tsx'),
      'EsgPage.tsx was F2-16\'s headline file, 5,583 lines and the largest in the frontend; tranches '
      + '1–3 took it to 564. If it is oversized again, a change put page-level code back into the '
      + 'shell instead of into a tab module — the whole point of the split.').toBe(false);
    expect(lineCount(join(SRC, 'pages/esg/EsgPage.tsx')),
      'anti-vacuity for the assertion above: `measure()` also omits a file that was renamed or '
      + 'deleted, which would make "not oversized" true for the wrong reason.')
      .toBeGreaterThan(200);
  });
});
