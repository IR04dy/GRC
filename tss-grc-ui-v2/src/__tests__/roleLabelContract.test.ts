import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { INTAKE_CATALOG } from '../lib/intake';
import { ROLE_LABEL, roleLabel } from '../lib/roleLabels';
import { isTestFile, relPosix, stripComments, walkSources } from './support/sourceScan';

/**
 * JRN-10's second half — a submitter is shown a role, not a role CODE.
 *
 * <p>`INTAKE_CATALOG` routes each kind to a `defaultAssignee`, and F2-174 established that value is
 * a role name from `iam.roles`, pinned at a hard zero server-side. Three surfaces then rendered it
 * raw under a human label, so a submitter read "This will route to RISK_MANAGER for triage" — and
 * in Arabic an English SCREAMING_SNAKE token sat inside Arabic copy, on an Arabic-primary platform.
 *
 * <p>⚠ The plan named ONE of those three sites. The other two are the post-submit acknowledgement
 * — the screen a whistleblower reads after filing — and the HRSD process card, which wrapped the
 * code in `className="mono"`, i.e. styled it as an identifier rather than making it one (F2-215:
 * fixing the instance you noticed leaves the remainder looking covered).
 *
 * <h3>What each leg is for</h3>
 * <ul>
 *   <li>Coverage, both directions: a new assignee cannot ship unlabelled, and a stale entry cannot
 *       outlive its code (F2-199 — the direction nobody writes first).</li>
 *   <li>Wiring: a leg that exercises `roleLabel` says nothing about whether the PAGES call it
 *       (F2-205), so the raw field is banned everywhere but its own declaration.</li>
 *   <li>House rule 7, made checkable: an Arabic label that is not in Arabic script is the defect
 *       wearing a different hat.</li>
 * </ul>
 */

const SRC = join(process.cwd(), 'src');
const ARABIC = /[؀-ۿ]/;

/** Files allowed to name the raw field: the catalogue that declares it, and the labeller. */
const DECLARERS = new Set(['lib/intake.ts', 'lib/roleLabels.ts']);

const ASSIGNEES = [...new Set(Object.values(INTAKE_CATALOG).map(c => c.defaultAssignee))].sort();

describe('JRN-10 — a role reaches the screen as a label, never as a code', () => {
  it('every routed assignee has a label in both languages', () => {
    const unlabelled = ASSIGNEES.filter(code => !ROLE_LABEL[code]);
    expect(unlabelled, 'An INTAKE_CATALOG defaultAssignee with no human label. Read it out of '
      + 'iam.roles.display_name_en / display_name_ar rather than composing one — four of eleven '
      + 'first-draft guesses disagreed with what the platform ships.').toEqual([]);

    for (const [code, l] of Object.entries(ROLE_LABEL)) {
      expect(l.en.trim().length, `${code}: English label`).toBeGreaterThan(1);
      expect(l.ar.trim().length, `${code}: Arabic label`).toBeGreaterThan(1);
      expect(ARABIC.test(l.ar), `${code}: the Arabic label must be in Arabic (house rule 7)`).toBe(true);
      expect(l.en, `${code}: a label that is the code is not a label`).not.toBe(code);
      expect(l.ar, `${code}: a label that is the code is not a label`).not.toBe(code);
    }
  });

  it('no label entry outlives the code it labels', () => {
    const orphans = Object.keys(ROLE_LABEL).filter(code => !ASSIGNEES.includes(code));
    expect(orphans.sort(), 'A label for a role nothing routes to. Delete it in the commit that '
      + 'removed the routing, or the map becomes a second role catalogue nobody maintains.')
      .toEqual([]);
  });

  it('no surface renders the raw assignee code', () => {
    const offenders: string[] = [];
    for (const file of walkSources(SRC)) {
      if (isTestFile(file)) continue;
      const rel = relPosix(SRC, file);
      if (DECLARERS.has(rel)) continue;
      const body = stripComments(readFileSync(file, 'utf8'));
      // Every remaining mention must be the argument of roleLabel(...).
      for (const m of body.matchAll(/\.defaultAssignee\b/g)) {
        const before = body.slice(Math.max(0, (m.index ?? 0) - 60), m.index ?? 0);
        if (!/\broleLabel\(\s*[\w.]*$/.test(before)) offenders.push(`${rel}  …${before.slice(-40)}`);
      }
    }
    expect(offenders, 'A raw role code reaching the screen. Wrap it in roleLabel(code, lang) — '
      + 'that is the whole finding, and the mono styling on one of the three sites made it read '
      + 'as deliberate.').toEqual([]);
  });

  it('the labeller degrades to readable rather than to a screaming identifier', () => {
    expect(roleLabel('RISK_MANAGER', 'en')).toBe('Risk Manager');
    expect(roleLabel('RISK_MANAGER', 'ar')).toBe('مدير المخاطر');
    // An unmapped code is unreachable from the catalogue BECAUSE of the coverage leg above; this
    // pins what any other caller gets, and it is never the raw code.
    expect(roleLabel('SOME_NEW_ROLE', 'en')).toBe('Some New Role');
    expect(roleLabel('SOME_NEW_ROLE', 'ar')).toBe('Some New Role');
    expect(roleLabel(null, 'en')).toBe('');
    expect(roleLabel(undefined, 'ar')).toBe('');
  });

  it('the scan resolves a real corpus', () => {
    expect(ASSIGNEES.length, 'distinct routed assignees').toBeGreaterThan(8);
    expect(walkSources(SRC).filter(f => !isTestFile(f)).length, 'source files').toBeGreaterThan(700);
  });
});
