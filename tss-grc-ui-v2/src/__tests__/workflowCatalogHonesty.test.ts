import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';
import { WORKFLOWS, type WorkflowDef } from '../lib/workflowCatalog';
import { stripComments } from './support/sourceScan';

/**
 * JRN-04 — the catalogue must not advertise a journey the platform does not have.
 *
 * <p>`workflowCatalog.ts` is the single source of truth behind the Workflow Hub AND every module
 * page's "Start a workflow" strip. Each entry carries a `status` from a three-value union, and the
 * file's own header states the contract: *"Adding a new wizard means: (1) ship the wizard page,
 * (2) flip status from 'PLANNED' to 'AVAILABLE' here."* So `AVAILABLE` is a claim that the journey
 * is shipped.
 *
 * <h3>What was wrong</h3>
 * All 35 entries read `AVAILABLE`, so the field could not distinguish anything — and because the
 * rendering layer is complete (tone, label, filter, three counters, a disabled card), the uniform
 * value did not produce silence. It produced a green *Available* badge on all 35 cards and a page
 * header reading "35 of 35 workflows shipped — 0 partial, 0 on backlog". 🔑 An invented all-clear
 * closes the question an absent badge would have raised (F2-197), which is why nothing could ever
 * have flagged it.
 *
 * <h3>🔑 The evidence was already in the tree, in a different gate</h3>
 * `apiClientMethodReachability.test.ts` records `ermGap.attest` (`:106`) and `aar.generate`
 * (`:191`) as HOLES — the client methods exist and nothing calls them — while the catalogue two
 * files away read `AVAILABLE` for exactly those two journeys. Two artefacts, one question, and
 * nobody was comparing them (F2-177). That coupling is not mechanically checkable here (the HOLE
 * list is keyed by client method, not by catalogue entry, and pairing them needs a reading), so
 * this gate asserts the half that IS mechanical: the vocabulary has to stay exercised.
 *
 * <h3>Which legs discriminate (F2-225)</h3>
 * <ul>
 *   <li>{@code theStatusVocabularyIsExercised} — the finding itself. Fires on the pre-fix tree.</li>
 *   <li>{@code everyRouteResolves} and {@code aNullRouteMeansPlanned} — <b>tripwires</b>. Both are
 *       clean today and neither fires on the pre-fix tree; they exist so the next stray route
 *       string or orphaned entry fails the build rather than being found by hand (F2-464 asked the
 *       route-citation question of server messages; this is the same question at a catalogue).</li>
 *   <li>{@code theNonWizardDestinationsAreAKnownSet} — a <b>work list with no verdicts</b>
 *       (F2-188/F2-330). A non-wizard destination is not itself a defect — `/bcm/bia` resolves to
 *       `BiaWizardPage`, which is a wizard at a non-`/workflows/` path — so a hard zero here would
 *       fail on correct code, the one failure a gate must never have (F2-214). What the exact
 *       assertion buys is that the next one forces a reading.</li>
 * </ul>
 */

const ROOT = join(process.cwd(), 'src');

/** Entries whose `status` is deliberately not `AVAILABLE`, each with the reason it is not. */
const NOT_AVAILABLE: Record<string, string> = {
  'erm-risk-attestation':
    'The destination offers the supervisor SWEEP, not the owner attestation the title names. The '
    + 'owner-facing POST exists server-side with no screen — apiClientMethodReachability.test.ts:106.',
  'bcm-aar':
    'The destination is a read-only list whose own empty state says generation happens elsewhere, '
    + 'and aar.generate has zero call sites — apiClientMethodReachability.test.ts:191. So the first '
    + 'step of this 7-step journey has no screen anywhere, and there is nowhere to repoint it.',
};

/**
 * Catalogue entries whose route is a register/console page rather than a `/workflows/*` wizard.
 *
 * ⚠ Deliberately NO verdicts. Writing thirteen confident reasons for destinations read only at
 * grep depth is exactly the artefact F2-188 measured at five claims, five false — and one of my
 * own candidates fell on reading: `pdpl-dpia` DOES offer DPIA creation, routed through the
 * Services Center (`PrivacyPage.tsx:427-431`), so a detector reporting "no create affordance" was
 * an artefact rather than a finding.
 */
const NON_WIZARD_DESTINATIONS: Record<string, string> = {
  'erm-risk-attestation': '/buyer-gap',
  'bcm-bia': '/bcm/bia',
  'bcm-bcp': '/bcm/plans',
  'bcm-drp': '/bcm/plans',
  'bcm-crisis': '/bcm/crisis-room',
  'bcm-aar': '/buyer-gap',
  'pdpl-dpia': '/privacy',
  'incident-triage': '/incidents',
  'evidence-request': '/evidence',
  'capa-workflow': '/capa',
  'governance-policy': '/policy',
  'compliance-evidence-package': '/compliance',
  'evidence-connectors': '/buyer-gap',
};

function declaredRoutes(): Set<string> {
  const app = stripComments(readFileSync(join(ROOT, 'App.tsx'), 'utf8'));
  const out = new Set<string>();
  for (const m of app.matchAll(/path="([^"]+)"/g)) {
    const p = m[1];
    out.add(p.startsWith('/') ? p : `/${p}`);
  }
  return out;
}

describe('JRN-04 — the workflow catalogue may not claim a journey the platform lacks', () => {
  it('the status vocabulary is exercised, so the Hub header is a measurement rather than an all-clear', () => {
    const flagged = Object.fromEntries(
      WORKFLOWS.filter(w => w.status !== 'AVAILABLE').map(w => [w.id, w.status]),
    );
    expect(Object.keys(flagged).sort(), 'The set of entries NOT claiming to be shipped. Enforced '
      + 'exactly and in both directions: a new entry cannot quietly join it, and flipping one back '
      + 'to AVAILABLE fails until its reason here is deleted on purpose. 🔑 If this set is ever '
      + 'empty again, every card reads Available and the header reads "N of N shipped — 0 partial, '
      + '0 on backlog", which is an invented all-clear rather than a measurement (F2-197).')
      .toEqual(Object.keys(NOT_AVAILABLE).sort());

    for (const [id, reason] of Object.entries(NOT_AVAILABLE)) {
      expect(reason.length, `${id}: a bare label is not a reason (F2-188)`).toBeGreaterThan(80);
      expect(flagged[id], `${id}: must not read AVAILABLE`).not.toBe('AVAILABLE');
    }
  });

  it('every catalogue route resolves to a route this app declares', () => {
    const declared = declaredRoutes();
    const stray = WORKFLOWS
      .filter(w => w.route && !declared.has(w.route))
      .map(w => `${w.id} -> ${w.route}`);
    expect(stray, 'A catalogue entry pointing at a route nothing declares. The card would navigate '
      + 'nowhere, and a stray string is indistinguishable from a considered destination.').toEqual([]);
    expect(declared.size, 'declared routes').toBeGreaterThan(200);
  });

  it('a null route means PLANNED, which is what the type says', () => {
    // `WorkflowDef.route` is documented "Wizard URL — `null` for PLANNED entries".
    // ⚠ Vacuous today (0 null routes, 0 PLANNED entries) and kept as a tripwire: the moment an
    // entry is added for a journey that does not exist yet, this is the leg that holds the pair
    // together rather than letting a null route ship as AVAILABLE.
    const broken = WORKFLOWS
      .filter(w => (w.route === null) !== (w.status === 'PLANNED'))
      .map(w => `${w.id} route=${w.route} status=${w.status}`);
    expect(broken, 'route === null and status === PLANNED must agree — the type says so.').toEqual([]);
  });

  it('the non-wizard destinations are a known set', () => {
    const actual = Object.fromEntries(
      WORKFLOWS
        .filter((w): w is WorkflowDef & { route: string } =>
          !!w.route && !w.route.startsWith('/workflows/'))
        .map(w => [w.id, w.route]),
    );
    expect(actual, 'A catalogue entry sending an operator to a register page rather than a wizard. '
      + 'That is not by itself a defect — /bcm/bia resolves to BiaWizardPage — but it is the '
      + 'population this finding lives in, so a new one has to be read rather than absorbed.')
      .toEqual(NON_WIZARD_DESTINATIONS);
  });

  it('both languages of the Hub header carry the same figures', () => {
    const hub = readFileSync(join(ROOT, 'pages', 'workflow', 'WorkflowHubPage.tsx'), 'utf8');
    const sub = hub.slice(hub.indexOf('sub={isAr'), hub.indexOf('eyebrow={isAr'));
    const [arabic, english] = sub.split(': `');
    for (const [label, half] of [['Arabic', arabic], ['English', english]] as const) {
      for (const field of ['counts.available', 'counts.total', 'counts.partial', 'counts.planned']) {
        expect(half.includes(field),
          `${label} header is missing ${field}. Arabic is the primary language (house rule 7), so `
          + `it may not carry LESS than the English — and this clause only became expressible once `
          + `a status was flipped, which is why the label moved with the predicate (F2-212).`)
          .toBe(true);
      }
    }
  });

  it('the catalogue is a real corpus', () => {
    expect(WORKFLOWS.length, 'catalogue entries').toBeGreaterThan(30);
    expect(new Set(WORKFLOWS.map(w => w.id)).size, 'ids must be unique').toBe(WORKFLOWS.length);
  });
});
