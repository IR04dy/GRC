import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-178 — the roster-coverage gap is visible on a screen, and it says the two numbers that differ.
 *
 * <h3>Why a source test rather than a render test</h3>
 * The properties at risk here are not rendering behaviours, they are *what the screen is willing to
 * say*. F2-26's per-committee report shipped with a KPI and a warning card and was, by every rendering
 * measure, working — while production sat at 0 of 31 seats linked for months, because the report can
 * only be read one committee at a time and nobody opened seventeen pages. A test that mounts the panel
 * and asserts a table appears would have passed on the F2-26 version too.
 *
 * So these assertions pin the three things that make the surface load-bearing:
 *
 *   1. the panel exists, is reachable from the committees screen, and asks the server for the
 *      *tenant-wide* roll-up — not `getSeatLinkage` in a loop, which cannot see a committee that has
 *      no roster at all (9 of 17 in dev, 11 of 17 in production);
 *   2. it distinguishes **no roster** from **0% linked**, because those are different defects with
 *      different remedies and one of them means the committee can never be quorate;
 *   3. it reports `currentLinked` — the seat guard's number — and not only `linked`. A roster whose
 *      terms have lapsed reads 100% covered and admits nobody, and nothing ever moves an ACTIVE term
 *      to EXPIRED, so that state arrives by calendar arithmetic rather than by anyone's mistake.
 */

const SRC = join(__dirname, '..');
const PANEL = join(SRC, 'components/committees/RosterCoveragePanel.tsx');
const PAGE = join(SRC, 'pages/committees/CommitteesPage.tsx');
const REGISTRY_PAGE = join(SRC, 'pages/committees/CommitteeMemberRegistryPage.tsx');
const CLIENT = join(SRC, 'lib/committees.ts');

/** Strip comments: this tree documents the retired mechanism in prose on purpose. */
function code(path: string): string {
  return readFileSync(path, 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^\s*\/\/.*$/gm, '');
}

describe('F2-178 — the tenant-wide roster-coverage surface', () => {
  it('asks the server for the tenant-wide roll-up', () => {
    expect(code(CLIENT)).toContain("api.get(`${REG_BASE}/linkage-coverage`)");
    expect(code(PANEL)).toContain('getRosterCoverage');
  });

  it('does not loop the per-committee report to build the tenant view', () => {
    // getSeatLinkage takes a committee id, so a loop over it structurally cannot report a committee
    // with no term rows — which is the majority state in both datasets. It would also issue a term
    // query and a user look-up per committee for a screen that shows neither.
    expect(code(PANEL)).not.toContain('getSeatLinkage');
  });

  it('is mounted on the committees screen, under the tab a reader is actually on', () => {
    const page = code(PAGE);
    expect(page).toContain("from '../../components/committees/RosterCoveragePanel'");
    // ⚠ The mount CONDITION is the assertion, not the component name. A mutation replacing the tab
    // check with `false` left the import and the JSX in place and survived an assertion that only
    // looked for the name — a panel nobody can reach is the F2-163 failure mode, and the whole point
    // of this slice is that an invisible report is the same as no report.
    expect(page).toContain("{tab === 'committees' && <RosterCoveragePanel />}");
  });

  it('self-hides for a persona without ADMIN:READ instead of leaving an error card', () => {
    const panel = code(PANEL);
    // The same stance ChangeAuthorityPanel takes: a permanent error card on a screen someone is
    // otherwise entitled to use trains them to ignore cards.
    expect(panel).toMatch(/catch\s*{\s*setDenied\(true\)/);
    expect(panel).toContain('if (denied ||');
  });

  it('names "no roster" as its own state rather than folding it into 0% coverage', () => {
    const panel = code(PANEL);
    // Both the tone and the label must branch on it. Asserting only that the file mentions `hasRoster`
    // let a mutation deleting the tone branch survive: the row kept its "No roster" label and lost the
    // colour that makes it read as worse than a partly-linked roster, which is the distinction the
    // state exists to draw (an empty committee can never be quorate).
    expect(panel).toMatch(/if \(!r\.hasRoster\) return 'danger';/);
    expect(panel).toMatch(/if \(!r\.hasRoster\) return isAr \? '[^']+' : 'No roster';/);
    // Both languages: an Arabic-primary product that falls back to English on its newest report reads
    // as half-built.
    expect(panel).toContain('لا يوجد أعضاء');
  });

  it('reports the seat guard number, not only the roster number', () => {
    const panel = code(PANEL);
    expect(panel).toContain('currentLinked');
    expect(panel).toContain('currentSeats');
    // "Admits nobody" is the statement a reader can act on; `coveragePct === 0` is not the same claim,
    // because a fully-lapsed roster reads 100%.
    expect(panel).toContain('Admits nobody');
    expect(panel).toContain('لا يُخوِّل أحداً');
  });

  it('links each row to the screen that fixes it, and names the right action', () => {
    const panel = code(PANEL);
    expect(panel).toContain('/members`}');
    // A committee with no roster needs members appointed; one with an unlinked roster needs seats
    // linked. Offering "Link seats" on an empty roster sends the administrator to a table with no rows.
    expect(panel).toContain('Appoint members');
    expect(panel).toContain('Link seats');
  });

  it('discloses lapsed links on the per-committee screen too', () => {
    const page = code(REGISTRY_PAGE);
    // Fires only when the two numbers actually disagree, so it stays silent until it has something to
    // say — as of 2026-08-09 that is every dev and production row, and the earliest production term
    // ends 2027-03-01.
    expect(page).toContain('linkage.currentLinked < linkage.linked');
    expect(page).toContain('still read ACTIVE');
  });

  it('carries the committee type, which is the argument of the seat guard it explains', () => {
    // hasPermission(type,'COMMITTEE','SEAT') names the committee TYPE, so a reader can tell which
    // guarded screens a red row is the explanation for. Dropping it would leave the report describing
    // a gap with no way to connect it to the refusal it causes.
    expect(code(CLIENT)).toMatch(/committeeType:\s*CommitteeType;/);
  });

  it('types both counts so a caller cannot silently read the wrong one', () => {
    const client = code(CLIENT);
    expect(client).toMatch(/currentSeats:\s*number;/);
    expect(client).toMatch(/currentLinked:\s*number;/);
    expect(client).toMatch(/currentlySeated:\s*boolean;/);
    expect(client).toMatch(/hasRoster:\s*boolean;/);
    expect(client).toMatch(/dissolvedExcluded:\s*number;/);
  });
});
