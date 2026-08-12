import { describe, expect, it } from 'vitest';
import { readFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

/**
 * F2-194 — the Connectors page may not render a fabricated corpus beside the real engine.
 *
 * <p>What was wrong: `pages/intake/ConnectorsPage` rendered `LiveControlTests`, which drives the
 * real backend monitoring engine (`/api/v1/ccm` — persisted control-test definitions, a real runner
 * per test, a 60-second scheduler tick), and then **directly beneath it** rendered
 * `components/compliance/CcmDashboard`, which computed a *"% CCM health"* badge, six KPIs, a ticking
 * `SlaCountdown` over synthesised stale-evidence breaches, and a control-coverage matrix citing NCA
 * ECC / ISO 27001 / SOC 2 / PDPL codes — all from `lib/ccm.ts`'s hard-coded `CCM_CHECKS`. A façade
 * beside a working subsystem, on one screen. ADR-017 §D-5 ratified retiring the fabricated surface
 * for exactly this shape in [[F2-42]], so this was the ratified fix rather than a new product call.
 *
 * <p>🔑 The fabrication's timestamps were generated from `Date.now()` at module load so the demo
 * would "always show realistic freshness without hard-coded dates going stale". **A fabrication
 * anchored to the clock can never look stale** — which is why this one outlived F2-193's connector
 * catalogue, whose frozen 2026-05-05 stamps were three months out of date and visibly so.
 *
 * <p>This is modelled on `boardPackProvenance.test.ts` and copies its two load-bearing ideas:
 * assertions run over **comment-stripped** source (the page documents its own history in prose, and
 * that documentation is worth keeping — a naive regex fails on it, which is the phantom-match class
 * F2-04a hit three times), and the check asserts a **pairing** — the fabrication absent AND the real
 * fetch present. Removing a fabricated panel without a real one behind it is the dishonest half of
 * this change, and only the pairing can tell the two apart.
 */

const SRC = join(process.cwd(), 'src');
const PAGE = join(SRC, 'pages', 'intake', 'ConnectorsPage.tsx');

function read(p: string): string {
  return readFileSync(p, 'utf-8');
}

/** Strips `//` and block comments, so prose about the history is not read as code. */
function stripComments(src: string): string {
  return src
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split('\n')
    .map(l => {
      const i = l.indexOf('//');
      return i < 0 ? l : l.slice(0, i);
    })
    .join('\n');
}

const page = read(PAGE);
const pageCode = stripComments(page);

/**
 * Corpora that must not reach this page. Each is a hard-coded array of record literals that the
 * F2-194 census found outside the `mock*` filename boundary — so `demoFallbackContract.test.ts`,
 * which matches `/mock/i` imports, cannot see any of them.
 */
const FABRICATED_CORPORA = [
  'CCM_CHECKS',        // lib/ccm.ts — 9 checks, timestamps generated from Date.now()
  'ASSETS',            // lib/assets.ts — 19 assets
  'APPROVAL_REQUESTS', // lib/approvals.ts — 12 requests
  'ALL_WORKFLOWS',     // lib/mockWorkflows.ts — the cross-module in-flight list
  'CAPAS',             // lib/capa.ts — 20 corrective actions
] as const;

describe('F2-194 — the Connectors page renders the real monitoring engine, not a fabricated one', () => {
  it('the fabricated CCM panel component is gone', () => {
    expect(existsSync(join(SRC, 'components', 'compliance', 'CcmDashboard.tsx')),
      'the fabricated panel is back — F2-194 regressed').toBe(false);
  });

  it('nothing mounts a fabricated CCM panel on this page', () => {
    expect(pageCode).not.toMatch(/<CcmDashboard\s*\/?>/);
    expect(pageCode).not.toMatch(/from\s+['"][^'"]*compliance\/CcmDashboard['"]/);
  });

  it('the explanation of what was removed survives, in prose and not in code', () => {
    // The same string, required present in the raw source and absent once comments are stripped.
    // ⚠ The first draft asserted the bare word `CcmDashboard` in the raw source, which passed for a
    // reason unrelated to the prose: the page imports the type `CcmDashboardSnapshot` from the real
    // engine's client. Sibling of the import-instead-of-call hole below — both are substring checks
    // satisfied by something that is not the claim.
    const RETIRED = 'components/compliance/CcmDashboard';
    expect(page, 'the note naming what was retired is gone — a future reader would find an '
      + 'unexplained absence and could re-add the panel in good faith').toContain(RETIRED);
    expect(pageCode, 'the retired path is in CODE, not prose').not.toContain(RETIRED);
  });

  it('imports no fabricated corpus', () => {
    const found = FABRICATED_CORPORA.filter(
      c => new RegExp(String.raw`import[^;]*\b${c}\b`, 's').test(pageCode));
    expect(found, `fabricated corpora imported: ${found.join(', ')}`).toEqual([]);
  });

  it('...and does drive the real engine, which is the other half of the pairing', () => {
    // Absent both halves, a page that simply deleted its monitoring panel would pass the checks
    // above. The real engine is what makes the removal honest rather than a quiet capability loss.
    //
    // ⚠ These assert the CALL, not the name. The first draft used `toContain('getCcmDashboard')`
    // and a mutation that replaced the call with `Promise.resolve(null)` SURVIVED — because the
    // import statement at the top of the file still contains the name. Third instance in this
    // lineage of a substring check satisfied by something other than the claim it is testing:
    // F2-192's reason citing `list`, F2-193's reason quoting the sentence it corrected, and now an
    // import standing in for a call site.
    for (const call of ['listCcmTests', 'getCcmDashboard', 'runCcmTestNow', 'getIntegrationStatus']) {
      expect(pageCode, `${call} is imported but never called`)
        .toMatch(new RegExp(String.raw`\b${call}\s*\(`));
    }
  });

  it('the freshness computation lost its last renderer here, not just its call site', () => {
    // aggregateCoverage / connectorById / checksForConnector existed only for the deleted panel.
    // Keeping them would leave a decoy: a coverage aggregator implies something measures coverage.
    const ccm = read(join(SRC, 'lib', 'ccm.ts'));
    for (const gone of ['export function aggregateCoverage', 'export function connectorById',
      'export function checksForConnector', 'export interface CcmCoverage']) {
      expect(ccm, `${gone} survived the panel it was written for`).not.toContain(gone);
    }
  });

  it('lib/ccm.ts says what it is, so the next reader is not misled', () => {
    // The module's name and its "CCM engine" header both suggest a feed. The platform has a real
    // engine elsewhere; this file is a catalogue. That has to be stated where it is read.
    const ccm = read(join(SRC, 'lib', 'ccm.ts'));
    expect(ccm).toMatch(/hard-coded corpus, not a feed/);
    expect(ccm).toMatch(/ccmApi|CcmDashboardPage/);
  });
});
