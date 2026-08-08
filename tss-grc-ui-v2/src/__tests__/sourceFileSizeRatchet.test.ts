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
 * more tests in `pages/esg/EsgRegulatoryTabs.test.tsx`. The page is now 43% of its original size and
 * still the fourth-largest file in the frontend, so it stays pinned below.
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
    'pages/esg/EsgPage.tsx',
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

    // ⚠ This asserted `> 4000` and fired on tranche 2, exactly as its own message asked it to: the
    // split happened, so the shape is updated deliberately rather than left stale. The bound tracks
    // the work — it is `> 800` once F2-16 is finished, at which point EsgPage leaves OVERSIZE and the
    // stale-entry check above becomes the guard. Keeping a bound at all is what makes the ratchet
    // notice a REGRESSION: a tranche that grew the page instead of shrinking it would pass a
    // one-sided "still oversized" assertion.
    expect(measure().get('pages/esg/EsgPage.tsx'),
      'EsgPage.tsx is F2-16\'s headline file. Tranche 1 took it 5,583 → 4,994 and tranche 2 → 3,201; '
      + 'if it has grown past 3,300 a tranche went backwards, and if it is under 800 the split is '
      + 'finished and this test plus OVERSIZE should be updated deliberately.')
      .toBeGreaterThan(800);
    expect(measure().get('pages/esg/EsgPage.tsx')).toBeLessThan(3300);
  });
});
