// Which lifecycle workspaces still keep their state in the browser — declared, not implied.
//
// F2-202. This file exists because deleting a decoy removes a fingerprint.
//
// Until this slice, five lifecycle libraries wrote localStorage and then called
// `mirrorSaveToBackend(...)` / `mirrorTransitionToBackend(...)` from
// `lib/platformPersistence.ts`. Those helpers read a persistence-mode switch and returned
// immediately when the mode was `'demo'`. Measured: `setPersistenceMode` had **zero**
// callers anywhere in the product, the localStorage key `grc.persistence.mode.v1` was
// written by nothing, and the switch took no Vite env input — so the mode was not
// *defaulting* to `'demo'`, it was `'demo'` by construction, in every build, for every user.
// All three mirror helpers were unconditional no-ops.
//
// Two further measurements made them incoherent even in principle:
//   • The READ path never consulted the mode at all. `loadTreatmentPlans()` and its four
//     siblings read localStorage unconditionally, so a write that did reach the server
//     could never be shown. Writing to a store you never read from is not persistence.
//   • The badge in the topbar reads a DIFFERENT switch — `lib/productionMode.ts`, key
//     `grc.mode.v1`, which DOES take `VITE_MODE`. So a deployment could set
//     `VITE_MODE=production`, turn the badge green and label it "Production", while the
//     switch that actually gated the lifecycle writes stayed `'demo'` and unreachable.
//     `platformPersistence.ts`'s own header claimed it "Drives the ModeBadge in the topbar".
//     It did not, and never had.
//
// Deleting the no-op calls is right — a reader of `saveTreatmentPlan` should not see a REST
// mirror that cannot send. But deleting them and stopping would leave five workspaces
// looking like ordinary local state with nothing recording that a real, populated server
// register sits behind each one. So the fingerprint moves here, where it is enforced:
// `lifecycleStoreContract.test.ts` (frontend) and `LifecycleStoreBackingContractTest`
// (backend, which resolves every `endpoint` below to a real controller).
//
// ⚠ Row counts are as measured 2026-08-13 against dev (TSS_GRC) and prod
// (TSS_GRC_Deployment) with pyodbc. They are evidence that a real register exists and is
// not empty — they are NOT live figures and nothing renders them. If you re-measure and
// they have moved, update them; the gate checks they are numbers, not that they are current,
// because a frontend test cannot reach a database.
//
// ⚠⚠ Every `endpoint` below is resolved to a live controller by the BACKEND half of the gate,
// and that leg is not decoration: writing this file I guessed three of the eight paths from
// the schema name and got all three wrong, each differently — `/api/privacy/ropa` is really
// `/api/v1/privacy/ropa`, the DPIA controller is mounted at `/api/v1/privacy/dpias` (plural,
// its own class), and cross-border is `/api/privacy/cross-border-transfers` with NO `v1`
// segment at all. A plausible-looking endpoint string in a declared registry is worth
// nothing until something resolves it, and "the table is in schema X" does not predict the
// path. Resolve from the controller, never from the schema.

export type LifecycleStoreState =
  /** Reads and writes go to the server. Nothing about it is browser-local. */
  | 'SERVER_BACKED'
  /** Reads and writes are browser-local. A real register exists and no client reaches it. */
  | 'BROWSER_LOCAL'
  /**
   * Reads come from the server; writes do not leave the browser.
   *
   * ⚠ This third state was not in the first draft of this file, and finding it is why the
   * gate exists — `auditLifecycle.ts` was in it, and a store whose LIST is real is harder to
   * notice than an honestly local one.
   *
   * ⚠⚠ NO STORE IS IN THIS STATE TODAY. F2-203 closed the only member. The state is kept
   * declared because it is a shape this codebase demonstrably produces, and a reviewer who
   * finds another one needs somewhere to put it — but nothing below may claim it without a
   * measurement, and `theThirdStateHasNoMembers` in the frontend gate asserts the emptiness
   * so that re-introducing it is a deliberate act. The previous version of this comment
   * described `auditLifecycle.ts` in the present tense; leaving that in place would have
   * been F2-184's shape — a comment answering the question a reader was about to ask, with
   * an answer that had stopped being true.
   */
  | 'SERVER_READ_LOCAL_WRITE'
  /**
   * The register exists and is populated, and **nothing serves it**.
   *
   * ⚠⚠ F2-204 added this state because the first version of this file — mine, one slice
   * earlier — declared **6 browser-local corpora when the four modules own 16**. It happened to
   * list exactly the three `*_lc` corpora that have a controller plus the three `privacy` ones,
   * which is a coherent rule I never stated and therefore never checked. The other ten were
   * invisible, and `BASELINE_BROWSER_LOCAL_STORES = 6` read as "6 stores remain" when the real
   * answer was 16.
   *
   * 🔑 **A registry that declares one member per module reads exactly like a registry that
   * declares them all** — F2-198's finding about `demoFallbackContract` ("the invariant is real,
   * its POPULATION is a filename pattern"), committed in the artefact built to replace that
   * decoy. `theRegistryCoversEveryBrowserLocalCorpus` now derives the population from the
   * modules' own localStorage keys, so a corpus cannot be omitted by not being thought of.
   *
   * Seven tables are in this state, holding **69 rows in dev and prod that no endpoint can
   * reach**. They are NOT wireable by a frontend slice: there is no controller, no DTO and no
   * service. Naming them here is the point — an unwireable store and an unwired one are
   * different facts, and collapsing them would make the next slice re-measure all of it.
   */
  | 'NO_SERVER_SURFACE'
  /**
   * Reads and writes go to the server for the record, and a sub-resource of it is not served
   * at all. `risk-treatment` is the case: the plan is fully wired, and
   * `risk_lc.treatment_actions` holds 10 rows with no sub-resource on the controller and no
   * `actions` field on the DTO, so per-action progress cannot be persisted by any client.
   */
  | 'PARTIALLY_SERVER_BACKED';

export interface LifecycleStore {
  /** Stable key for the gate and for cross-referencing findings. */
  id: string;
  /** The lib module that owns the corpus. */
  module: string;
  state: LifecycleStoreState;
  /** The REST base a client would use. Resolved to a live controller by the backend gate. */
  endpoint: string;
  /** schema.table of the register the endpoint serves. */
  table: string;
  /** Rows measured 2026-08-13. */
  rowsDev: number;
  rowsProd: number;
  /**
   * The localStorage key the module actually uses — empty when it keeps no such key.
   *
   * ⚠⚠ This field is checked against the module's own source, and that check found errors in
   * the first draft of this file for **3 of the 5** stores, because the keys were copied from
   * the `PersistenceAdapter` definitions in the file being deleted and those adapters were
   * wrong about their own stores: `grc.bcm.invocations.v1` where the module writes
   * `grc.bcm.invocation.v1` (singular), `grc.cyber.vulnerabilities.v1` where it writes
   * `grc.cyber.vuln.v1`, and `grc.audit.findings.v1` for a module that keeps no localStorage
   * key at all. Had the mode switch ever been reachable, `adapterListAll` would have read
   * three keys nothing had ever written and returned its seed — which is independent
   * confirmation that the whole mechanism was never once exercised.
   */
  localKey: string;
  /**
   * What is true about this store today. Must name a consequence, not a label — and must
   * not assert anything the entry's own numbers do not support (F2-188: the reasons are the
   * least-verified part of the most authoritative-looking artefact).
   */
  note: string;
}

export const LIFECYCLE_STORES: readonly LifecycleStore[] = [
  {
    id: 'privacy-dsr',
    module: 'lib/privacyLcApi.ts',
    state: 'SERVER_BACKED',
    endpoint: '/api/privacy-lc/dsr-cases',
    table: 'privacy_lc.dsr_cases',
    rowsDev: 12,
    rowsProd: 12,
    localKey: '',
    note:
      'Wired by F2-202. Reads the register, decides through POST /{id}/transition, and takes '
      + 'the replacement row from the server response. The PDPL Article 4 deadline and the '
      + 'DSR reference code are minted server-side only — the browser copy minted both, the '
      + 'reference with three digits of Math.random().',
  },
  {
    id: 'privacy-ropa',
    module: 'lib/privacyLifecycle.ts',
    state: 'BROWSER_LOCAL',
    endpoint: '/api/v1/privacy/ropa',
    table: 'privacy.processing_activities',
    rowsDev: 11,
    rowsProd: 11,
    localKey: 'grc.privacy.ropa.v1',
    note:
      'The real register is privacy.processing_activities and PrivacyPage\'s ROPA tab already '
      + 'renders it — F2-198 retired the separate /ropa page onto that tab. This corpus is a '
      + 'second, editable copy behind the privacy-lifecycle screen. Note the privacy_lc twin '
      + 'table (privacy_lc.ropa_activities) holds 0 rows in both databases, so wiring this to '
      + 'privacy_lc rather than privacy would show an empty register.',
  },
  {
    id: 'privacy-dpia',
    module: 'lib/privacyLifecycle.ts',
    state: 'BROWSER_LOCAL',
    endpoint: '/api/v1/privacy/dpias',
    table: 'privacy.dpia',
    rowsDev: 6,
    // ⚠ Corrected by F2-204: this said 6. Prod holds 5. The two databases are not identical
    // everywhere, and asserting they are is how a declared number stops being a measurement.
    rowsProd: 5,
    localKey: 'grc.privacy.dpia.v1',
    note:
      'privacy.dpia holds 6 assessments with their own question, mitigation and review tables. '
      + 'The browser corpus carries its own PDPL Article 25 risk scoring, so a DPIA approved '
      + 'here is invisible to the server register a DPO would be audited against. '
      + 'privacy_lc.dpia_assessments holds 0 rows in both databases.',
  },
  {
    id: 'privacy-cbt',
    module: 'lib/privacyLifecycle.ts',
    state: 'BROWSER_LOCAL',
    endpoint: '/api/privacy/cross-border-transfers',
    table: 'privacy.cross_border_transfers',
    rowsDev: 9,
    rowsProd: 9,
    localKey: 'grc.privacy.cbt.v1',
    note:
      'PDPL Article 29 transfer assessments. privacy.cross_border_transfers holds 9 rows; '
      + 'F2-198 already surfaced the cross-border COUNT on the privacy page from the real '
      + 'register, so the number an operator sees there and the rows editable here come from '
      + 'two different places. privacy_lc.cross_border_transfers holds 0 rows.',
  },
  {
    id: 'risk-treatment',
    module: 'lib/riskLifecycle.ts',
    state: 'PARTIALLY_SERVER_BACKED',
    endpoint: '/api/risk-lc/treatment-plans',
    table: 'risk_lc.treatment_plans',
    // ⚠ Corrected by F2-204: this said 11/11. 11 rows exist, **10 are live** — the F2-202
    // measurement counted the soft-deleted one. A registry whose whole purpose is measured
    // numbers had two of them wrong (see privacy-dpia below for the other).
    rowsDev: 10,
    rowsProd: 10,
    localKey: '',
    note:
      'WIRED by F2-204. `transitionPlan` is the ERM approval of how a risk will be treated '
      + '(DRAFT->SUBMITTED->APPROVED/REJECTED->IN_PROGRESS->COMPLETED) and every decision went '
      + 'to localStorage: F2-201/F2-203\'s shape on a third control. The page also invented the '
      + 'SUBSTANCE of the decision, not just its author — an approving CRO name AND the literal '
      + 'rejection reason "Insufficient cost-benefit justification", which the server persists, '
      + 'so a naive wire would have written invented text into rejected_reason. PARTIAL because '
      + '`risk_lc.treatment_actions` holds 10 rows with no sub-resource and no DTO field, so '
      + 'per-action progress cannot be saved by any client; the "Mark done" button is removed '
      + 'rather than left reporting success. Filed.',
  },
  {
    id: 'audit-findings',
    module: 'lib/auditLifecycle.ts',
    state: 'SERVER_BACKED',
    endpoint: '/api/audit-lc/findings',
    table: 'audit_lc.findings',
    rowsDev: 20,
    rowsProd: 20,
    // No localStorage at all — the cache is module-level `let _findings: Finding[]`, so it
    // does not even survive a reload, let alone reach another user.
    localKey: '',
    note:
      'CLOSED by F2-203. It was the one store that READ the server and did not write it: '
      + 'hydrateAuditLifecycle() fetched all four registers, so the 20 rows were real, but every '
      + 'mutation assigned a module-level array and the mirror F2-202 deleted was the only thing '
      + 'resembling a write — an internal-audit finding closed here was gone on reload and never '
      + 'visible to a second auditor. createFinding and transitionFinding now POST to '
      + '/api/audit-lc/findings and /{id}/transition and replace the row from the response; the '
      + 'four SEED_* corpora are deleted, so an empty register reads as empty and an unreachable '
      + 'one reads as a failure. 7 of the 20 rows carry a status the server machine declares no '
      + 'edge out of and so cannot be advanced by any route — disclosed on the row, filed.',
  },
  // ══ The ten corpora F2-202's registry did not declare ═══════════════════════════════════
  //
  // Measured 2026-08-13 from the four modules' own localStorage keys, in both databases. Seven
  // have a populated table and NO controller, DTO or service anywhere in the backend — resolved
  // by searching every `@RequestMapping` path, not by looking under `module/*_lc/`, because
  // similar objects DO have controllers in other modules (`/api/v1/bcm/bia-assessments`,
  // `/api/v1/socops/ot-assets`, `/api/v1/cyber/threat-intel`) over DIFFERENT tables — which is
  // F2-196's disjoint-register shape and exactly what makes "there is no endpoint" easy to get
  // wrong in the reassuring direction.
  {
    id: 'risk-acceptances',
    module: 'lib/riskLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'risk_lc.acceptance_requests',
    rowsDev: 0,
    rowsProd: 0,
    localKey: 'grc.risk.accept.v1',
    note:
      'Accepting a risk instead of treating it is a governance decision with a named accepter, '
      + 'and `transitionAcceptance` moves it through CRO and board sign-off entirely in the '
      + 'browser. The table exists and is EMPTY in both databases, so unlike the other six in '
      + 'this state there is no data stranded — but there is also nothing to wire to.',
  },
  {
    id: 'risk-kri-breaches',
    module: 'lib/riskLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'risk_lc.kri_breaches',
    rowsDev: 12,
    rowsProd: 12,
    localKey: 'grc.risk.kribreach.v1',
    note:
      '12 real KRI breach events in both databases that no endpoint returns. The module fires, '
      + 'escalates and resolves them in localStorage. `risk_lc.kri_escalation_log` is empty, so '
      + 'no escalation has ever been recorded server-side.',
  },
  // ⚠⚠ `risk-treatment-actions` was REMOVED from this registry before the slice shipped, and
  // the backend gate is why. I added it as a NO_SERVER_SURFACE entry claiming nothing served
  // `risk_lc.treatment_actions`; `noServerSurfaceStoresReallyHaveNone` found a
  // `TreatmentAction` @Entity, and reading the SERVER's TreatmentPlanDto showed it declares
  // `List<TreatmentActionDto> actions` and maps all 10 rows on every read. The claim came from
  // reading the FRONTEND interface in `lib/api.ts`, which had never declared the field.
  //
  // It also did not belong here on its own terms: it declared `localKey: ''`, so it is not a
  // browser-local corpus at all — it was documentation smuggled into a registry keyed on
  // corpora, which is how a registry starts describing something other than its subject. The
  // real fact (reads work, writes have no sub-resource) lives on `risk-treatment`'s note.
  {
    id: 'bcm-bia',
    module: 'lib/bcmLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'bcm_lc.bia_records',
    rowsDev: 8,
    rowsProd: 8,
    localKey: 'grc.bcm.bia.v1',
    note:
      '8 business-impact analyses in both databases with no endpoint. ⚠ `module/bcm/cycle` DOES '
      + 'expose /api/v1/bcm/bia-assessments and /api/v1/bcm/bia/quantitative — over DIFFERENT '
      + 'tables. Wiring this store to those would be F2-196\'s defect, not a fix.',
  },
  {
    id: 'bcm-strategies',
    module: 'lib/bcmLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'bcm_lc.recovery_strategies',
    rowsDev: 8,
    rowsProd: 8,
    localKey: 'grc.bcm.strategy.v1',
    note: '8 recovery strategies in both databases. No controller anywhere in the backend.',
  },
  {
    id: 'bcm-drills',
    module: 'lib/bcmLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'bcm_lc.drills',
    rowsDev: 5,
    rowsProd: 5,
    localKey: 'grc.bcm.drill.v1',
    note:
      '5 drills in both databases. No controller. Note `bcm.exercise_run` is a separate, real '
      + 'and served exercise register (F2-161 gave it ownership-based authorisation), so this is '
      + 'a second store for a similar object — an owner decision, not a wiring task.',
  },
  {
    id: 'cyber-assets',
    module: 'lib/cyberLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'cyber_lc.assets',
    rowsDev: 14,
    rowsProd: 14,
    localKey: 'grc.cyber.asset.v1',
    note:
      '14 assets in both databases with no endpoint. ⚠ `socops` exposes /crypto-assets and '
      + '/ot-assets over different tables; neither serves this one.',
  },
  {
    id: 'cyber-soc-alerts',
    module: 'lib/cyberLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'cyber_lc.cyber_incidents',
    rowsDev: 10,
    rowsProd: 10,
    localKey: 'grc.cyber.soc.v1',
    note:
      '10 rows in both databases. `transitionSocAlert` triages and closes SOC alerts in the '
      + 'browser. ⚠ /api/v1/incidents is the `incident` module over a different table.',
  },
  {
    id: 'cyber-threats',
    module: 'lib/cyberLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: 'cyber_lc.threat_intel',
    rowsDev: 12,
    rowsProd: 12,
    localKey: 'grc.cyber.threat.v1',
    note:
      '12 rows in both databases. ⚠ /api/v1/cyber/threat-intel exists but serves the `cyber` '
      + 'module\'s own table — the only entity mapped to schema cyber_lc is Vulnerability, which '
      + 'is how this was resolved rather than inferred from the path looking right.',
  },
  {
    id: 'cyber-filings',
    module: 'lib/cyberLifecycle.ts',
    state: 'NO_SERVER_SURFACE',
    endpoint: '',
    table: '',
    rowsDev: 0,
    rowsProd: 0,
    localKey: 'grc.cyber.filing.v1',
    note:
      'The only corpus here with no table at all: there is no cyber_lc filing register, so '
      + '`transitionFiling` moves regulatory filings through a lifecycle that exists nowhere but '
      + 'this browser. `nca_ecc.filings` (4 rows, served) is the real filing register and '
      + 'F2-199 retired a fabricated page that shadowed it.',
  },
  {
    id: 'cyber-pentests',
    module: 'lib/cyberLifecycle.ts',
    // ⚠⚠ CORRECTED before shipping: I first declared this NO_SERVER_SURFACE. The backend gate
    // found a `PentestEngagement` @Entity, and `PentestEngagementController` mounts
    // /api/v1/socops/pentests. So it is served, and this is an ordinary BROWSER_LOCAL store.
    // 🔑 Two of my eleven NO_SERVER_SURFACE claims were refuted by the gate written to check
    // them — the same rate F2-188 and F2-192 found in their predecessors' reasons, which is the
    // argument for the gate rather than for trusting the count.
    state: 'BROWSER_LOCAL',
    endpoint: '/api/v1/socops/pentests',
    table: 'socops.pentest_engagement',
    rowsDev: 4,
    rowsProd: 4,
    localKey: 'grc.cyber.pentest.v1',
    note:
      '4 rows served by socops at /api/v1/socops/pentests — a DIFFERENT module and schema from '
      + 'the rest of `cyberLifecycle.ts`. So this is a browser-local copy of a register that '
      + 'belongs to socops, and wiring it means deciding whether the cyber lifecycle should own '
      + 'pen tests at all (F2-196\'s two-registers-one-object question), not just repointing a '
      + 'call. Owner decision.',
  },
  {
    id: 'bcm-invocations',
    module: 'lib/bcmLifecycle.ts',
    state: 'BROWSER_LOCAL',
    endpoint: '/api/bcm-lc/invocations',
    table: 'bcm_lc.invocations',
    rowsDev: 3,
    rowsProd: 3,
    localKey: 'grc.bcm.invocation.v1',
    note:
      'BcmPage.tsx already carries a comment naming GET /api/bcm-lc/invocations and recording '
      + 'that no api-client method exists for it, so this gap was known and written down '
      + 'beside the screen rather than anywhere a test could see it.',
  },
  {
    id: 'cyber-vulnerabilities',
    module: 'lib/cyberLifecycle.ts',
    state: 'BROWSER_LOCAL',
    endpoint: '/api/cyber-lc/vulnerabilities',
    table: 'cyber_lc.vulnerabilities',
    rowsDev: 36,
    rowsProd: 36,
    localKey: 'grc.cyber.vuln.v1',
    note:
      'The largest of the eight — 36 real vulnerabilities no client reads. Both '
      + 'VulnerabilitiesV2Page and the cyber-lifecycle workspace render the browser corpus, '
      + 'so remediation progress recorded on either is local to that browser.',
  },
];

/** The subset still keeping governance state in the browser. Decrease-only via the gate. */
export const BROWSER_LOCAL_LIFECYCLE_STORES: readonly LifecycleStore[] =
  LIFECYCLE_STORES.filter(s => s.state === 'BROWSER_LOCAL');

export function lifecycleStore(id: string): LifecycleStore | undefined {
  return LIFECYCLE_STORES.find(s => s.id === id);
}
