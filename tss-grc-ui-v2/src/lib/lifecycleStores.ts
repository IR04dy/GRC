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
   * gate exists. `auditLifecycle.ts` fetches four `/api/audit-lc/*` resources in
   * `hydrateAuditLifecycle()`, so its list is real — but every mutation writes a
   * module-level array and the only thing that ever looked like a server write was the
   * no-op mirror this slice deleted. So a finding closed on that screen is gone on reload,
   * while the screen it was closed on looks server-backed. It also carries the F2-158
   * fallback shape: `if (_findings.length === 0) _findings = SEED_FINDINGS`, so a tenant
   * with an empty register sees invented findings rather than an empty one.
   */
  | 'SERVER_READ_LOCAL_WRITE';

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
    rowsProd: 6,
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
    state: 'BROWSER_LOCAL',
    endpoint: '/api/risk-lc/treatment-plans',
    table: 'risk_lc.treatment_plans',
    rowsDev: 11,
    rowsProd: 11,
    localKey: 'grc.risk.treatment.v1',
    note:
      'The only one of these with a partial real client already: lib/api.ts calls GET '
      + '/api/risk-lc/treatment-plans?riskId= and POST on the same path, so the risk detail '
      + 'view and this lifecycle workspace read two different stores for the same plans. '
      + 'Wiring this store means reconciling those two callers, not just adding a client.',
  },
  {
    id: 'audit-findings',
    module: 'lib/auditLifecycle.ts',
    state: 'SERVER_READ_LOCAL_WRITE',
    endpoint: '/api/audit-lc/findings',
    table: 'audit_lc.findings',
    rowsDev: 20,
    rowsProd: 20,
    // No localStorage at all — the cache is module-level `let _findings: Finding[]`, so it
    // does not even survive a reload, let alone reach another user.
    localKey: '',
    note:
      'The one store that READS the server and does not write it. hydrateAuditLifecycle() '
      + 'fetches annual-plans, working-papers, findings and capas, so the 20 rows are real; but '
      + 'every mutation assigns a module-level array and the deleted mirror was the only thing '
      + 'resembling a write. An internal-audit finding closed here is gone on reload and was '
      + 'never visible to a second auditor. It also seeds SEED_FINDINGS when the server returns '
      + 'zero rows, which is the F2-158 fallback shape: an empty register renders as invented '
      + 'findings rather than as empty.',
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
