import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { isTestFile, relPosix, stripComments, walkSources } from './support/sourceScan';

/**
 * F2-68(c) — an API client the product never calls is a backend endpoint with no user.
 *
 * <p>The orphan-discovery audit shipped scans for the <em>database</em> end of this question
 * (`table_xref.py`, `schema_drift.py`) and for routes the user cannot reach
 * (`routeReachability.test.ts`). This is the missing middle: {@code src/lib} holds 126 typed API
 * client modules and <b>931</b> exported functions that call the backend. This scan asks which of
 * them nothing calls.
 *
 * <h3>Why that question is worth gating</h3>
 * The answer names capability that was built, wired to a real endpoint, and then never reached from
 * any screen. Measured on the first run: <b>127</b> such exports, including
 * {@code generateBcpFromBia}/{@code generateDrpFromBia} (generate a continuity plan from a BIA — a
 * flagship BCM capability whose controller is live), the three {@code mobileCrisis} push-subscription
 * calls (crisis notifications), {@code policyCommitteeReferral} (the committee-routing view that
 * Track D shipped), and eight {@code platformPersistence} adapters — those last ones over
 * {@code platform.persistence_row}, the table F2-71 found absent from the database for three months
 * while the client silently fell back to {@code localStorage}.
 *
 * <p>The precedent this generalises is F2-04b's: {@code moduleEnabled()} was exported, tested and
 * called by <b>nobody for four months</b> while the entitlement subsystem it belonged to never once
 * said no. Nothing in CI could see that. This scan can.
 *
 * <h3>A ratchet, not a hard zero</h3>
 * 127 cannot be wired in one slice, and <b>a red build nobody can fix gets disabled</b> — the same
 * reasoning that made F2-70's 67-table gate decrease-only. So the list below is exhaustive and
 * <em>both</em> directions are enforced: a new unreached export fails, and an entry that is now
 * reached (or no longer exists) also fails, so the list cannot rot into a blanket suppression. The
 * module-level check above it <em>is</em> a hard zero, because a whole client module that nothing
 * imports is unambiguous.
 *
 * <h3>Deliberately conservative</h3>
 * "Reached" is a token match in any non-test production file — not a resolved import. A same-named
 * symbol somewhere else therefore counts as a use, so this <b>under</b>-reports. That is the
 * intended direction: F2-07b's scan over-reported by 28 and would have sent someone rewiring
 * navigation that already worked, and <em>a scan that matches too little does not merely
 * under-report, it invents defects</em>. Tests never count as callers — a client only a test calls
 * is unreached by the product.
 */

const SRC = join(__dirname, '..');
const LIB = join(SRC, 'lib');

/** `export function|const|let|var|class NAME` at top level. */
const DECL = /^export\s+(?:async\s+)?(?:function|const|let|var|class)\s+([A-Za-z_$][\w$]*)/gm;
/** The export talks to the backend — `api.get(...)` &c., or a raw `/api/...` path. */
const CALLS_API = /api\.(?:get|post|put|patch|del|delete)\b|\/api\//;

/**
 * Clients that are deliberately unimported. Empty, and that is the point: the one entry this scan
 * found on its first run — {@code lib/sustainabilityEvidence.ts}, ten API functions over the
 * superseded {@code /api/sustainability/evidence} silo — was deleted rather than excused, because
 * {@code lib/evidenceItems.ts} says in its own header that it replaced it.
 */
const INTENTIONALLY_UNIMPORTED: Record<string, string> = {};

/**
 * Every API-calling export that no production file calls, as measured. Each line is a backend
 * endpoint the frontend can reach and never does — a finding, not a suppression. Owned by F2-68(c);
 * the ones worth wiring are triaged in UNIFIED_IMPLEMENTATION_PLAN.md, not here.
 */
const KNOWN_UNREACHED: readonly string[] = [
  // ESG / sustainability — 27
  'lib/climate.ts#getEmission',
  'lib/climate.ts#listApprovedScenarios',
  'lib/climate.ts#listSectorImpacts',
  'lib/climateRisk.ts#deleteClimateItem',
  'lib/climateRisk.ts#getClimateItem',
  'lib/climateRisk.ts#listTopClimateItems',
  'lib/ghgInventory.ts#deleteGhgRecord',
  'lib/scenarioLibrary.ts#recommendedScenarios',
  'lib/sustEvidenceLinks.ts#listSustEvidenceForEntity',
  'lib/sustEvidenceLinks.ts#sustEvidencePackage',
  'lib/sustainabilityAnnex.ts#getReportingYear',
  'lib/sustainabilityAnnex.ts#getSustainabilityReport',
  'lib/sustainabilityAnnex.ts#listSustainabilityReports',
  'lib/sustainabilityCapa.ts#capasForSource',
  'lib/sustainabilityCapa.ts#getCapa',
  'lib/sustainabilityCma.ts#deleteCma',
  'lib/sustainabilityCma.ts#getCma',
  'lib/sustainabilityMateriality.ts#deleteMaterialTopic',
  'lib/sustainabilityMateriality.ts#getMaterialTopic',
  'lib/sustainabilityMetrics.ts#deleteMetric',
  'lib/sustainabilityMetrics.ts#getMetric',
  'lib/sustainabilitySgi.ts#deleteSgi',
  'lib/sustainabilitySgi.ts#getSgi',
  'lib/sustainabilitySgi.ts#getSgiByCode',
  'lib/sustainabilitySgi.ts#getSgiProgress',
  'lib/sustainabilityTcfd.ts#deleteTcfd',
  'lib/sustainabilityTcfd.ts#getTcfd',
  // BCM / crisis / resilience — 31
  'lib/bcmCompliance.ts#getMapping',
  'lib/bcmCycle.ts#deleteRisk',
  'lib/bcmReadinessScore.ts#readinessLeaderboard',
  'lib/bcmReadinessScore.ts#scoreForBia',
  'lib/bcmReadinessScore.ts#scoreForCycle',
  'lib/bcmRegulatorCountdown.ts#regulatorCountdowns',
  'lib/bcmRegulatorCountdown.ts#regulatorCountdownsForEvent',
  'lib/bcpBinder.ts#getBinder',
  'lib/bcpPlans.ts#sectionCatalogue',
  'lib/biaAssessments.ts#generateBcpFromBia',
  'lib/biaAssessments.ts#generateDrpFromBia',
  'lib/crisisComms.ts#archiveTemplate',
  // 'lib/crisisComms.ts#listCascades' — wired by F2-147: the dispatch wizard now shows the
  // cascades already sent for the selected crisis event. Until then the only view of a cascade
  // was the transient result step, which "Send another" discards.
  'lib/exerciseObserver.ts#listObservations',
  'lib/exerciseObserver.ts#observationsByType',
  'lib/exerciseRuntime.ts#getRunTimeline',
  // 🔑 `listRunsForExercise` and `startRun` LEFT this list with F2-163, and the fact that they were
  // on it is the lesson. This allow-list recorded "nothing calls these" — which, read out loud, is
  // "no screen lists or starts an exercise run". That is exactly the defect F2-163 found from the
  // navigation side (three consoles, 1,862 lines, no entry point), and F2-161 fixed the guards of,
  // from the authorisation side. ⚙ Three instruments held a piece of one defect, and a tolerated
  // entry in one of their allow-lists was the proof another needed. When an export sits here, ask
  // what the USER cannot do — not just which function is unused.
  'lib/mobileCrisis.ts#listPushSubscriptions',
  'lib/mobileCrisis.ts#registerPushSubscription',
  'lib/mobileCrisis.ts#unregisterPushSubscription',
  'lib/mtpdInheritance.ts#getDependency',
  'lib/mtpdInheritance.ts#getProcessHistory',
  'lib/planReview.ts#planAttestations',
  'lib/planReview.ts#reviewQueue',
  'lib/rootCauseAnalysis.ts#getRca',
  'lib/rootCauseAnalysis.ts#rcasForEntity',
  'lib/strategyRoi.ts#compareStrategyRoi',
  'lib/strategyRoi.ts#getStrategyRoi',
  'lib/supplierResilience.ts#alternatesForPrimary',
  'lib/supplierResilience.ts#plansForVendor',
  // NDI — 16
  'lib/ndiCompliance.ts#deleteCompliance',
  'lib/ndiDomains.ts#getDomain',
  'lib/ndiEvidence.ts#getEvidence',
  'lib/ndiFramework.ts#getFrameworkItem',
  'lib/ndiGaps.ts#getGap',
  'lib/ndiMaturity.ts#deleteMaturity',
  'lib/ndiMaturity.ts#getMaturity',
  'lib/ndiOpex.ts#deleteOpex',
  'lib/ndiOpex.ts#getOpex',
  'lib/ndiPlan.ts#reorderItems',
  'lib/ndiPlan.ts#rollupPlan',
  'lib/ndiReports.ts#getReport',
  'lib/ndiSettings.ts#deleteSetting',
  'lib/ndiSettings.ts#getSettingByKey',
  'lib/ndiSettings.ts#listSettings',
  'lib/ndiSubmission.ts#getSubmission',
  // Governance / committees / policy / audit — 13
  'lib/auditLifecycle.ts#hydrateAuditLifecycle',
  'lib/auditLifecycle.ts#saveAnnualPlan',
  'lib/auditLifecycle.ts#saveCapa',
  'lib/auditLifecycle.ts#saveWorkingPaper',
  'lib/committees.ts#actionsByCommittee',
  'lib/committees.ts#actionsByMeeting',
  'lib/committees.ts#getAction',
  'lib/committees.ts#openActionsForCommittee',
  'lib/committees.ts#openActionsForOwner',
  'lib/committees.ts#removeSkill',
  'lib/committees.ts#resolutionsByCommittee',
  'lib/committees.ts#resolutionsByMeeting',
  'lib/policy.ts#policyCommitteeReferral',
  // Compliance / controls / obligations — 18
  'lib/ccmApi.ts#getCcmRecentRuns',
  'lib/ccmApi.ts#getCcmTestRuns',
  'lib/controlLibrary.ts#deleteRiskControlLink',
  'lib/controlLibrary.ts#getControl',
  'lib/controlLibrary.ts#listCitationMappings',
  'lib/controlLibrary.ts#listObjectiveControls',
  'lib/controlLibrary.ts#listRiskControlLinks',
  'lib/controlLibrary.ts#mappingCoverage',
  'lib/grcExpert.ts#listSubControlEvidence',
  'lib/obligations.ts#deleteObligation',
  'lib/obligations.ts#listAttestationsForCampaign',
  'lib/obligations.ts#listObligationsByOwner',
  'lib/obligations.ts#listObligationsByRegulator',
  'lib/obligations.ts#listObligationsDue',
  'lib/regulatoryAuthority.ts#deleteAuthorityDocument',
  'lib/regulatoryAuthority.ts#deleteCitation',
  'lib/regulatoryAuthority.ts#getAuthorityDocument',
  'lib/regulatoryAuthority.ts#getCitationView',
  // Risk — 2
  'lib/riskAdvanced.ts#getTreatment',
  'lib/riskScenarios.ts#scenariosByRisk',
  // TPRM / outsourcing / vendor — 5
  'lib/outsourcing.ts#getMaterialityAssessment',
  'lib/outsourcing.ts#listOpenEvents',
  'lib/outsourcing.ts#listOverdueExitTests',
  'lib/outsourcing.ts#listOverdueReviews',
  'lib/tprmQuestionnaire.ts#getQtTemplate',
  // Privacy / PDPL — 1
  'lib/ropaWorkflows.ts#ropaWorkflowsBundle',
  // Platform services — 11
  'lib/api.ts#riskReportApi',
  'lib/digitalSignature.ts#listForEntity',
  'lib/platformPersistence.ts#APPROVAL_ADAPTER',
  'lib/platformPersistence.ts#AUDIT_ENG_ADAPTER',
  'lib/platformPersistence.ts#BOARD_PACK_ADAPTER',
  'lib/platformPersistence.ts#CAPA_ADAPTER',
  'lib/platformPersistence.ts#NCA_ECC_ADAPTER',
  'lib/platformPersistence.ts#REGCHANGE_ADAPTER',
  'lib/platformPersistence.ts#adapterSave',
  'lib/platformPersistence.ts#adapterTransition',
  'lib/savedViews.ts#syncSavedViewsFromBackend',
  // Other modules — 3
  'lib/cyberLifecycle.ts#ECC_DOMAINS_DEF',
  'lib/evidenceItems.ts#getEvidenceItem',
  'lib/evidenceItems.ts#updateEvidenceItem',
];

// ── the scan ───────────────────────────────────────────────────────────────────────────────────

interface ApiExport { key: string; module: string; name: string }

const ALL = walkSources(SRC).map(path => ({
  path,
  rel: relPosix(SRC, path),
  test: isTestFile(path),
  body: stripComments(readFileSync(path, 'utf8')),
}));

/** Identifiers present in each production file — the "is it called anywhere" index. */
const PRODUCTION = ALL.filter(f => !f.test);
const TOKENS_BY_FILE = new Map(
  PRODUCTION.map(f => [f.path, new Set(f.body.match(/[A-Za-z_$][\w$]*/g) ?? [])] as const));

/** Text of one declaration: from its `export` to the next top-level `export`, or end of file. */
function declarationSpan(body: string, from: number): string {
  const next = body.indexOf('\nexport ', from + 1);
  return body.slice(from, next === -1 ? body.length : next);
}

const libFiles = ALL.filter(f => f.path.startsWith(LIB) && !f.test);

function apiExports(): ApiExport[] {
  const out: ApiExport[] = [];
  for (const f of libFiles) {
    for (const m of f.body.matchAll(DECL)) {
      if (!CALLS_API.test(declarationSpan(f.body, m.index!))) continue;
      out.push({ key: `${f.rel}#${m[1]}`, module: f.rel, name: m[1] });
    }
  }
  return out;
}

const isCalled = (e: ApiExport) =>
  PRODUCTION.some(f => f.rel !== e.module && TOKENS_BY_FILE.get(f.path)!.has(e.name));

/** Which lib modules does production code import by path? Resolved, not token-matched. */
function importedModules(): Set<string> {
  const byRel = new Map(ALL.map(f => [f.rel, f] as const));
  const hit = new Set<string>();
  for (const f of PRODUCTION) {
    for (const m of f.body.matchAll(/from\s+['"](\.[^'"]+)['"]/g)) {
      const dir = f.rel.split('/').slice(0, -1);
      const parts = [...dir, ...m[1].split('/')];
      const stack: string[] = [];
      for (const p of parts) {
        if (p === '.' || p === '') continue;
        if (p === '..') stack.pop();
        else stack.push(p);
      }
      const base = stack.join('/');
      for (const cand of [`${base}.ts`, `${base}.tsx`, `${base}/index.ts`, `${base}/index.tsx`]) {
        if (byRel.has(cand) && cand !== f.rel) { hit.add(cand); break; }
      }
    }
  }
  return hit;
}

// ── assertions ─────────────────────────────────────────────────────────────────────────────────

describe('F2-68(c) — no API client module is shipped with no importer', () => {
  it('every lib module that calls the backend is imported by production code', () => {
    const clients = [...new Set(apiExports().map(e => e.module))];
    const imported = importedModules();
    const orphans = clients
      .filter(m => !imported.has(m))
      .filter(m => !(m in INTENTIONALLY_UNIMPORTED));

    expect(orphans,
      'These modules wrap real backend endpoints and no production file imports them — a client '
      + 'shipped with no user. Either wire it, delete it, or add it to INTENTIONALLY_UNIMPORTED '
      + 'with the reason:\n  ' + orphans.join('\n  ')).toEqual([]);
  });

  it('the unimported allow-list stays honest — no entry for a module that is gone or now used', () => {
    const clients = new Set(apiExports().map(e => e.module));
    const imported = importedModules();
    const stale = Object.keys(INTENTIONALLY_UNIMPORTED)
      .filter(m => !clients.has(m) || imported.has(m));
    expect(stale, 'INTENTIONALLY_UNIMPORTED names modules that no longer need excusing:\n  '
      + stale.join('\n  ')).toEqual([]);
  });
});

describe('F2-68(c) — unreached API-client exports only ever decrease', () => {
  const known = new Set(KNOWN_UNREACHED);

  it('no export becomes unreached without being recorded', () => {
    const fresh = apiExports().filter(e => !isCalled(e)).map(e => e.key).filter(k => !known.has(k));
    expect(fresh,
      'New API-client exports that nothing calls. Either call them from a screen, or — if they '
      + 'are genuinely not reachable yet — add them to KNOWN_UNREACHED so the count is visible '
      + 'rather than silent:\n  ' + fresh.join('\n  ')).toEqual([]);
  });

  it('the list only shrinks — an entry that is now called must be removed', () => {
    // Both directions, so the list cannot rot into a blanket suppression: this is the half that
    // forces it down as capability gets wired.
    const nowCalled = apiExports().filter(e => known.has(e.key) && isCalled(e)).map(e => e.key);
    expect(nowCalled, 'These are now called — delete them from KNOWN_UNREACHED (progress, not a '
      + 'failure):\n  ' + nowCalled.join('\n  ')).toEqual([]);
  });

  it('the list names nothing that no longer exists', () => {
    // A different staleness from the above: the export was renamed or deleted, so the entry is
    // exempting a symbol that is not there. An allow-list that outlives its subjects hides its rot.
    const present = new Set(apiExports().map(e => e.key));
    const vanished = KNOWN_UNREACHED.filter(k => !present.has(k));
    expect(vanished, 'KNOWN_UNREACHED names exports that no longer exist (renamed, deleted, or no '
      + 'longer calling the API):\n  ' + vanished.join('\n  ')).toEqual([]);
  });

  it('has no duplicate entries', () => {
    expect(KNOWN_UNREACHED.length).toBe(new Set(KNOWN_UNREACHED).size);
  });
});

describe('F2-68(c) — the scan is not vacuous', () => {
  // Every assertion above is an `toEqual([])`, which is exactly what a scan that matches nothing
  // also produces. These floors are what separate "clean" from "blind".
  const exports_ = apiExports();

  it('finds the API-client surface it is meant to measure', () => {
    const modules = new Set(exports_.map(e => e.module));
    expect(modules.size, 'the lib API-client census collapsed').toBeGreaterThanOrEqual(100);
    expect(exports_.length, 'the API-calling export census collapsed').toBeGreaterThanOrEqual(800);
    expect(libFiles.length).toBeGreaterThanOrEqual(200);
  });

  it('sees a called export as called, and an uncalled one as uncalled', () => {
    // Positive control: fetchBoardPack is called by BoardPackPage (F2-42).
    const called = exports_.find(e => e.name === 'fetchBoardPack');
    expect(called, 'fetchBoardPack vanished — repoint this control').toBeDefined();
    expect(isCalled(called!)).toBe(true);

    // Negative control: generateBcpFromBia has a live controller and no caller.
    const uncalled = exports_.find(e => e.name === 'generateBcpFromBia');
    expect(uncalled, 'generateBcpFromBia vanished — repoint this control').toBeDefined();
    expect(isCalled(uncalled!)).toBe(false);
  });

  it('recognises an API call in every shape this codebase writes one', () => {
    for (const shape of ['api.get<Foo>(`${B}/x`)', 'api.post(B, b)', 'api.put(`${B}/1`, b)',
                         'api.patch(B, b)', 'api.delete(`${B}/1`)', 'api.del(B)',
                         "fetch('/api/v1/x')"]) {
      expect(CALLS_API.test(shape), `unrecognised API shape: ${shape}`).toBe(true);
    }
    expect(CALLS_API.test('return items.map(x => x.id)'), 'false positive').toBe(false);
  });
});

describe('the comment stripper this scan depends on is sound', () => {
  /**
   * The regression pin for the defect that made the first version of this scan blind. The previous
   * one-line helper — block comments then line comments — let a `/*` inside a `//` line open a
   * phantom block that ran to the next close marker, deleting real code in 23 files.
   */
  it('a line comment containing a block-open does not swallow the file', () => {
    const src = [
      '// Wraps /api/sustainability/evidence/*.',
      "export function listEvidence() { return api.get('/api/x'); }",
      '/** doc */',
      'export function after() {}',
    ].join('\n');
    const out = stripComments(src);
    expect(out, 'the phantom-block bug is back — this is F2-68(c)\'s blindness')
      .toContain('listEvidence');
    expect(out).toContain('after');
    expect(out).not.toContain('Wraps');
    expect(out).not.toContain('doc');
  });

  it('a block comment containing a line-comment marker is still removed whole', () => {
    const out = stripComments('/* see http://example.com */ export const x = 1;');
    expect(out).not.toContain('example.com');
    expect(out).toContain('export const x = 1;');
  });

  it('comment markers inside strings and templates survive', () => {
    expect(stripComments("const u = 'https://x/y';")).toContain('https://x/y');
    expect(stripComments('const p = `/api/v1/a/*`;')).toContain('/api/v1/a/*');
  });

  it('strips ordinary comments — it is not a no-op', () => {
    expect(stripComments('const a = 1; // trailing')).not.toContain('trailing');
    expect(stripComments('/* leading */ const a = 1;')).not.toContain('leading');
  });
});
