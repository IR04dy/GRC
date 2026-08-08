import { describe, expect, it } from 'vitest';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { isTestFile, relPosix, stripComments, walkSources } from './support/sourceScan';

/**
 * F2-165 — the same question as F2-68(c), one granularity down.
 *
 * <p>F2-68(c) asks "does any production file call this API-client export?" and its `DECL` regex
 * sees `export const riskAcceptanceApi = { … }` as <b>one</b> export. `riskAcceptanceApi` had a
 * caller, so it counted as reached — while <b>four of its five methods had none</b>. Read aloud,
 * the true state was *"no screen can approve, reject, revoke or even view a formal risk
 * acceptance"*, and it is the approval that flips a risk to ACCEPTED. That is why the ungoverned
 * `POST /risks/{id}/accept` was load-bearing, and why retiring it (F2-164) required building the
 * panel first.
 *
 * <p>⚙ <b>An instrument's granularity is its blind spot, and the blind spot is invisible from
 * inside the instrument.</b> F2-68(c) is not wrong about anything it measures; it cannot express
 * the question. Identical shape to F2-163, where a per-`<Link>` route scan could not see a
 * `rowHref` navigation surface.
 *
 * <h3>What this measures, and the two buckets that carry meaning</h3>
 * <b>91 grouped clients, 1,070 methods, 189 with no caller</b> (measured 2026-08-08, after the 5
 * dead duplicates below were deleted). The finding's own recommendation was to sort those into
 * *governance-critical* and *not yet wired* — but reading the first one showed that sort is a
 * judgement call that invents defects. `evidenceApi.approve` had no caller and looked maximally
 * governance-critical; `lib/evidenceItems.ts#approveEvidenceItem` POSTs the <em>same</em> endpoint
 * and a screen calls it. Filed by name shape it would have read "no screen can approve evidence",
 * which is false.
 *
 * <p>So the partition here is mechanical and reproducible instead:
 * <ol>
 *   <li><b>Dead duplicate — hard zero, no allow-list</b> (first describe block). A dark method
 *       whose exact (verb, path) has a <em>reached</em> sibling is deletable dead code. Nothing is
 *       lost by deleting it and nothing is excused by keeping it, so this one can be an invariant
 *       today. It measured 5: the evidence trio, plus `cyberApi.controlsAll` / `cyberApi.frameworks`
 *       against `lib/frameworkControls.ts`. All five deleted, so the invariant starts at zero.</li>
 *   <li><b>{@link KNOWN_DARK} — a reported figure with a decrease-only ratchet.</b> 189 methods
 *       with no live twin, of which <b>148 carry `HOLE`</b>: the endpoint family a screen
 *       <em>does</em> reach, so the workflow has a gap (can approve, cannot create). That is the
 *       F2-164 shape and the dangerous one, because the screen looks complete. The other 41 are
 *       capability no screen touches at all — F2-68(c)'s original population.</li>
 * </ol>
 *
 * <p>The `HOLE` tags were generated once (family = the first four path segments with `{}` tokens
 * dropped) and are <em>annotation, not assertion</em> — nothing here recomputes them, so they can
 * describe the day they were measured without being able to rot into a false gate.
 *
 * <p>⚠ Why the second bucket is not an invariant: 189 cannot be wired in one slice, and an
 * unadjudicated red list becomes an allow-list — which is how a ceiling stops distinguishing
 * *tolerated* from *reviewed* (the `BARE_EXPORT_CEILING` precedent, F2-61(c)). It ratchets in both
 * directions so it cannot rot, and the annotations say what the user cannot do, because "which
 * function is unused" is the less useful half of the answer.
 *
 * <h3>⚠ Read {@code support/sourceScan.ts} before touching the scanning here</h3>
 * The first version of this measurement was a hand-rolled Python stripper. A <b>regex literal</b>
 * in `lib/api.ts` — `/filename="?([^";]+)"?/i` — put it into double-quote state at line 212 and
 * every comment after that point survived, so prose words (`permanently`, `below`) came out as
 * object keys. The repo's own {@link stripComments} documents that exact limitation and bounds its
 * string patterns to a single line, so it degrades safely. 🔑 <b>The helper existed, its docstring
 * warned about the precise failure, and re-implementing it reproduced the bug it was written to
 * avoid</b> — second occurrence of F2-07a's "grep for an instrument before building one".
 */

const SRC = join(__dirname, '..');
const LIB = join(SRC, 'lib');

/** `export const someApi = {` / `export const someApi: Type = {` — a grouped client. */
const GROUP = /^export\s+const\s+([A-Za-z_$][\w$]*)\s*(?::\s*[^=]+?)?=\s*\{/gm;
/** The group talks to the backend. */
const CALLS_API = /api\.(?:get|post|put|patch|del|delete)\b|\/api\//;

/**
 * Every grouped-client method no production file calls, as measured 2026-08-08.
 *
 * <p>`HOLE:` = the endpoint family IS reached by a screen, so this is a gap inside a live
 * workflow, not an unbuilt feature. Those are the ones worth reading twice.
 */
const KNOWN_DARK: readonly string[] = [
  // ── risk / ERM ──────────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#bowtieApi.removeConsequence',      // HOLE
  'lib/api.ts#bowtieApi.removeThreat',           // HOLE
  'lib/api.ts#bowtieApi.update',                 // HOLE: a bow-tie can be created and never edited
  // 🔑 `createAppetite` and `updateAppetite` LEFT this list in the same commit that added it, and
  // that is what the list is for. Both read "nothing calls this", which said out loud is "no tenant
  // can define a risk appetite" — while `approveAppetite`, `retireAppetite` and `updateExposure`
  // were all wired, and `RiskService.isOverAppetite` (half of F2-164's mandatory-treatment close
  // gate) matches on exactly the rows nothing could create. Filed as F2-166 and fixed here.
  'lib/api.ts#ermAdvancedApi.deleteAppetite',    // HOLE
  'lib/api.ts#ermAdvancedApi.getAppetite',       // HOLE: the list DTO is complete, so the panel never single-fetches
  'lib/api.ts#methodologyApi.update',            // HOLE
  'lib/api.ts#riskApi.archiveRisk',              // HOLE: E-J retire-from-register is API-only
  'lib/api.ts#riskReportApi.appetiteBreaches',
  'lib/api.ts#riskReportApi.auditPackage',
  'lib/api.ts#riskReportApi.registerStats',
  'lib/api.ts#riskReportApi.topRisks',
  'lib/api.ts#riskReviewApi.overdue',
  'lib/api.ts#riskReviewApi.runSweep',
  'lib/api.ts#treatmentPlanApi.listForRisk',     // HOLE: the plan the F2-164 close gate reads has no register view
  'lib/buyerGap.ts#ermGap.attest',               // HOLE: risk attestation cannot be recorded — yet its overdue SWEEP is wired
  'lib/buyerGap.ts#ermGap.linkRiskControl',      // HOLE: no screen links a control to a risk
  'lib/buyerGap.ts#ermGap.listAttestations',     // HOLE: as above
  'lib/buyerGap.ts#ermGap.listRiskControls',     // HOLE: as above
  'lib/buyerGap.ts#ermGap.uncontrolledRisks',
  'lib/kriEngine.ts#kriApi.closeBreach',         // HOLE: a KRI breach can be ACKNOWLEDGED and never closed
  'lib/kriEngine.ts#kriApi.listMeasurements',    // HOLE
  'lib/kriEngine.ts#kriApi.openBreaches',        // HOLE
  'lib/kriEngine.ts#kriApi.updateDefinition',    // HOLE: a KRI definition can be created and never edited
  // ── compliance / audit ──────────────────────────────────────────────────────────────────────────
  'lib/api.ts#complianceApi.auditEngagementStats',                             // HOLE
  'lib/api.ts#complianceApi.changeStats',                                      // HOLE
  'lib/api.ts#complianceApi.createAssessment',                                 // HOLE
  'lib/api.ts#complianceApi.createFinding',                                    // HOLE: the whole CAPA chain is wired on a finding nothing can raise
  'lib/api.ts#complianceApi.deleteFinding',                                    // HOLE: as above
  'lib/api.ts#complianceApi.findingsAgingStats',                               // HOLE
  'lib/api.ts#complianceApi.policies',                                         // HOLE
  'lib/api.ts#complianceApi.policyStats',                                      // HOLE
  'lib/api.ts#complianceApi.stats',
  'lib/api.ts#complianceApi.updateAssessment',                                 // HOLE
  'lib/api.ts#complianceApi.updateFinding',                                    // HOLE: as above
  'lib/api.ts#frameworkApi.addMapping',                                        // HOLE
  'lib/api.ts#frameworkApi.updateMapping',                                     // HOLE
  'lib/api.ts#issuesApi.get',                                                  // HOLE
  'lib/auditCommitteeDashboard.ts#auditCommitteeDashboardApi.listSnapshots',   // HOLE
  'lib/ccmEngine.ts#ccmApi.recentRuns',
  'lib/iaPlan.ts#iaApi.createEngagement',                                      // HOLE
  'lib/iaPlan.ts#iaApi.createPlan',                                            // HOLE
  'lib/iaPlan.ts#iaApi.listAllEngagements',                                    // HOLE
  'lib/iaPlan.ts#iaApi.listMasterUniverse',                                    // HOLE
  'lib/iaPlan.ts#iaApi.transitionPlan',                                        // HOLE: an internal-audit plan cannot be advanced
  // ── privacy / PDPL ──────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#consentApi.grant',                            // HOLE: PDPL consents are listed and withdrawn; none can be recorded
  'lib/api.ts#consentApi.supersede',                        // HOLE: as above
  'lib/api.ts#dpiaApi.create',                              // HOLE: a DPIA can be approved, rejected and expired but never created
  'lib/api.ts#dpiaApi.remove',                              // HOLE
  'lib/api.ts#dpiaApi.stats',                               // HOLE
  'lib/api.ts#dpiaApi.update',                              // HOLE: as above
  'lib/api.ts#privacyApi.deleteBreach',                     // HOLE
  'lib/api.ts#privacyApi.deleteRopa',                       // HOLE
  'lib/api.ts#privacyApi.updateBreach',                     // HOLE: a breach can be created, contained and closed — never corrected
  'lib/api.ts#privacyApi.updateRopa',                       // HOLE: a RoPA record can be created and never corrected
  'lib/buyerGap.ts#privacyGap.specialCategoryActivities',   // HOLE
  'lib/dataOfficer.ts#dataOfficerApi.getDsr',               // HOLE
  'lib/dataOfficer.ts#dataOfficerApi.listLineage',
  'lib/dataOfficer.ts#dataOfficerApi.timeline',
  'lib/dataOfficer.ts#dataOfficerApi.transitionDpia',       // HOLE: the data-officer DPIA queue cannot advance a record
  'lib/dataclass.ts#dataclassApi.createException',
  'lib/dataclass.ts#dataclassApi.getAsset',                 // HOLE
  'lib/dataclass.ts#dataclassApi.listExceptions',
  'lib/dataclass.ts#dataclassApi.transitionException',      // a data-classification exception cannot be decided
  'lib/subprocessors.ts#subprocessorsApi.byCountry',        // HOLE
  'lib/subprocessors.ts#subprocessorsApi.create',           // HOLE
  'lib/subprocessors.ts#subprocessorsApi.overdueReviews',   // HOLE
  'lib/subprocessors.ts#subprocessorsApi.update',           // HOLE
  // ── cyber / SOC ops ─────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#cyberApi.assetsAll',                      // HOLE
  'lib/api.ts#cyberApi.updatePolicy',                   // HOLE
  'lib/api.ts#cyberCloudServicesApi.get',               // HOLE
  'lib/asm.ts#asmApi.createAsset',                      // HOLE
  'lib/asm.ts#asmApi.dashboard',
  'lib/asm.ts#asmApi.getFinding',                       // HOLE
  'lib/asm.ts#asmApi.getShadowIt',                      // HOLE
  'lib/asm.ts#asmApi.transitionFinding',                // HOLE: ASM findings are listed and never transitioned
  'lib/asm.ts#asmApi.updateAsset',                      // HOLE
  'lib/ncaEccFiling.ts#ncaEccFilingApi.byYear',
  'lib/ncaEccFiling.ts#ncaEccFilingApi.get',            // HOLE
  'lib/ncaEccFiling.ts#ncaEccFilingApi.remove',         // HOLE
  'lib/socops.ts#socopsApi.activeOverlays',             // HOLE
  'lib/socops.ts#socopsApi.certsExpiring',              // HOLE
  'lib/socops.ts#socopsApi.cyberInsuranceRenewals',     // HOLE
  'lib/socops.ts#socopsApi.detectionDelete',            // HOLE
  'lib/socops.ts#socopsApi.detectionGet',               // HOLE
  'lib/socops.ts#socopsApi.detectionUpdate',            // HOLE
  'lib/socops.ts#socopsApi.forensicsByCase',            // HOLE
  'lib/socops.ts#socopsApi.forensicsTransferCustody',   // HOLE: chain-of-custody transfer is unreachable
  'lib/socops.ts#socopsApi.playbookSteps',              // HOLE
  'lib/socops.ts#socopsApi.runs',                       // HOLE
  'lib/socops.ts#socopsApi.ztMaturityByCode',           // HOLE
  'lib/vulnSla.ts#vulnSlaApi.compute',                  // HOLE
  'lib/vulnSla.ts#vulnSlaApi.deletePolicy',             // HOLE
  'lib/vulnSla.ts#vulnSlaApi.getFinding',               // HOLE
  'lib/vulnSla.ts#vulnSlaApi.getPolicy',                // HOLE
  'lib/vulnSla.ts#vulnSlaApi.listFindings',             // HOLE
  // ── BCM / crisis ────────────────────────────────────────────────────────────────────────────────
  'lib/aar.ts#aar.generate',                                  // HOLE
  'lib/aar.ts#aar.listForEvent',                              // HOLE
  'lib/api.ts#bcmApi.createDependency',
  'lib/api.ts#bcmApi.deleteDependency',
  'lib/api.ts#bcmApi.listDependencies',
  'lib/api.ts#crisisCommsApi.createAudience',                 // HOLE: audiences likewise
  'lib/api.ts#crisisCommsApi.createTemplate',                 // HOLE: templates are dispatched FROM, never maintained
  'lib/api.ts#crisisCommsApi.deleteTemplate',                 // HOLE
  'lib/api.ts#crisisCommsApi.updateAudience',                 // HOLE
  'lib/api.ts#crisisCommsApi.updateTemplate',                 // HOLE
  'lib/bcmManagementReviews.ts#bcmManagementReviewApi.get',   // HOLE
  // ── TPRM / vendor ───────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#scmApi.allVendors',                                 // HOLE
  'lib/api.ts#tprmApi.stats',
  'lib/cpty.ts#cptyApi.listLimits',                               // HOLE
  'lib/cpty.ts#cptyApi.update',                                   // HOLE
  'lib/outsourcingCloud.ts#outsourcingCloudApi.getArrangement',   // HOLE
  'lib/vendorEsg.ts#vendorEsgApi.aggregate',
  'lib/vendorEsg.ts#vendorEsgApi.create',                         // HOLE
  'lib/vendorEsg.ts#vendorEsgApi.get',                            // HOLE
  'lib/vendorEsg.ts#vendorEsgApi.sweepOverdue',
  // ── ethics / AML ────────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#amlAlertApi.get',                       // HOLE
  'lib/api.ts#disclosureApi.analytics',               // HOLE
  'lib/api.ts#disclosureApi.get',                     // HOLE
  'lib/api.ts#ethicsCaseApi.get',                     // HOLE
  'lib/api.ts#ethicsCaseApi.linksForEntity',          // HOLE
  'lib/api.ts#kycApi.disposition',                    // HOLE: a sanctions/KYC screening hit can never be dispositioned
  'lib/api.ts#kycApi.get',                            // HOLE
  'lib/coi.ts#coiApi.createCampaign',                 // HOLE
  'lib/complaints.ts#complaintsApi.reports',
  'lib/whistleblower.ts#whistleblowerApi.linkCapa',   // HOLE: a whistleblower case cannot be linked to its CAPA
  'lib/whistleblower.ts#whistleblowerApi.stats',      // HOLE
  // ── sustainability ──────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#esgHorizonApi.violations',    // HOLE
  'lib/api.ts#greenPortfolioApi.overlay',   // HOLE
  // ── platform / admin ────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#adminApi.patchUser',                             // HOLE: partial user edit is API-only
  'lib/api.ts#auditLogApi.trace',
  'lib/api.ts#brandThemeApi.get',                              // HOLE
  'lib/api.ts#calendarApi.forEntity',                          // HOLE
  'lib/api.ts#calendarApi.get',                                // HOLE
  'lib/api.ts#calendarApi.remove',                             // HOLE
  'lib/api.ts#calendarApi.update',                             // HOLE
  'lib/api.ts#dashboardApi.complianceByFramework',
  'lib/api.ts#dashboardApi.recentIncidents',
  'lib/api.ts#dashboardApi.recentRisks',
  'lib/api.ts#evidenceApi.expired',                            // HOLE
  'lib/api.ts#evidenceApi.stats',                              // HOLE
  'lib/api.ts#governanceApi.updateRole',                       // HOLE: a governance role can be created, cloned and deleted — never edited
  'lib/api.ts#incidentApi.get',                                // HOLE
  'lib/api.ts#incidentApi.setPhase',
  'lib/api.ts#incidentApi.stats',
  'lib/api.ts#reportScheduleApi.get',                          // HOLE
  'lib/api.ts#reportScheduleApi.runs',                         // HOLE
  'lib/api.ts#workflowApi.executeTransition',                  // as startInstance below
  'lib/api.ts#workflowApi.findInstance',
  'lib/api.ts#workflowApi.getInstance',
  'lib/api.ts#workflowApi.startInstance',                      // the /workflow/instances state-machine engine has no UI at all (the 14-module engine is workflowRequestsApi, a different surface)
  'lib/help.ts#helpApi.create',                                // HOLE
  'lib/help.ts#helpApi.get',                                   // HOLE
  'lib/help.ts#helpApi.listForModule',                         // HOLE
  'lib/help.ts#helpApi.update',                                // HOLE
  'lib/threeLines.ts#threeLinesApi.upsertMapping',             // HOLE: three-lines role banding cannot be configured
  'lib/threeLines.ts#threeLinesApi.userBand',                  // HOLE
  'lib/workflowRequests.ts#recipientListsApi.get',             // HOLE
  'lib/workflowRequests.ts#recipientListsApi.remove',          // HOLE
  'lib/workflowRequests.ts#recipientListsApi.update',          // HOLE
  'lib/workflowRequests.ts#workflowRequestsApi.runSlaSweep',   // HOLE
  'lib/workflowRequests.ts#workflowRequestsApi.submit',
  // ── AI + Saudi sector ───────────────────────────────────────────────────────────────────────────
  'lib/ai.ts#ai.listByEntity',                                // HOLE
  'lib/aiGovernance.ts#mlModelApi.create',                    // HOLE
  'lib/aiGovernance.ts#mlModelApi.highRiskWithoutFairness',   // HOLE
  'lib/aiGovernance.ts#mlModelApi.update',                    // HOLE
  'lib/openbanking.ts#obApi.getIncident',                     // HOLE
  'lib/openbanking.ts#obApi.listApiCalls',
  'lib/opres.ts#opresApi.updateAssessment',                   // HOLE
  'lib/reginspect.ts#reginspectApi.updateInspection',         // HOLE
  'lib/regreporting.ts#regreportingApi.calendar',
  'lib/regreporting.ts#regreportingApi.getObligation',        // HOLE
  'lib/regreporting.ts#regreportingApi.updateFiling',         // HOLE
  'lib/regreporting.ts#regreportingApi.updateObligation',     // HOLE
  'lib/rrp.ts#rrpApi.updateCycle',                            // HOLE
  'lib/rrp.ts#rrpApi.updateOption',
  'lib/sharia.ts#shariaApi.getResolution',                    // HOLE
  'lib/sharia.ts#shariaApi.getScholar',                       // HOLE
  'lib/sharia.ts#shariaApi.listResolutionEvents',             // HOLE
  // ── change management ────────────────────────────────────────────────────────────────────────────────────
  'lib/api.ts#changeApi.applyModel',                                            
  'lib/api.ts#changeApi.authorisation',                                         
  'lib/api.ts#changeApi.changeModel',                                           
  'lib/api.ts#changeApi.declareOutage',                                         
  'lib/api.ts#changeApi.engineRequests',                                        // HOLE
  'lib/api.ts#changeApi.get',                                                   // HOLE
  'lib/api.ts#changeApi.linkCommitteeResolution',                               // HOLE
  'lib/api.ts#changeApi.openConditions',                                        // HOLE
  'lib/api.ts#changeApi.routeTaskThroughEngine',                                // HOLE
  'lib/api.ts#changeApi.schedulePublications',                                  // HOLE
  'lib/api.ts#changeApi.updateModel',                                           // HOLE
];

// ── the scan ───────────────────────────────────────────────────────────────────────────────────

interface Method { key: string; module: string; group: string; name: string; span: string }

const ALL = walkSources(SRC).map(path => ({
  path,
  rel: relPosix(SRC, path),
  test: isTestFile(path),
  body: stripComments(readFileSync(path, 'utf8')),
}));

const PRODUCTION = ALL.filter(f => !f.test);
const LIB_FILES = PRODUCTION.filter(f => f.path.startsWith(LIB));
const OUTSIDE = PRODUCTION.filter(f => !f.path.startsWith(LIB));

/** The object literal beginning at `body[open]`, brace-balanced. */
function objectAt(body: string, open: number): string {
  let depth = 0;
  for (let i = open; i < body.length; i++) {
    if (body[i] === '{') depth++;
    else if (body[i] === '}' && --depth === 0) return body.slice(open, i + 1);
  }
  return body.slice(open);
}

/**
 * Depth-1 keys of an object literal, each with the text up to the next key.
 *
 * ⚠ A key may only start a SEGMENT — the previous meaningful character at depth 1 must be `{`
 * or `,`. Matching `NAME\s*[:(<]` at depth 1 instead pulls in generic type arguments, because
 * `api.get<PageResponse<Risk>>(url)` has its type parameters outside any paren and so a type
 * name looks exactly like a key. That produced `PageResponse`, `Array`, `Record` and `it` as
 * "methods" — the same too-wide-pattern mistake as F2-164's `.status ===` scan.
 */
function keyedSpans(block: string): Array<{ name: string; span: string }> {
  const found: Array<{ name: string; at: number }> = [];
  let depth = 0, parens = 0, brackets = 0, prev = '';
  for (let i = 0; i < block.length; ) {
    const c = block[i];
    if (c === '\'' || c === '"' || c === '`') {          // skip string/template bodies
      const q = c; i++;
      while (i < block.length && block[i] !== q) i += block[i] === '\\' ? 2 : 1;
      i++; prev = 'x'; continue;
    }
    if (c === '{') { depth++; prev = depth === 1 ? '{' : 'x'; i++; continue; }
    if (c === '}') { depth--; prev = 'x'; i++; continue; }
    if (c === '(') { parens++; prev = 'x'; i++; continue; }
    if (c === ')') { parens--; prev = 'x'; i++; continue; }
    if (c === '[') { brackets++; prev = 'x'; i++; continue; }
    if (c === ']') { brackets--; prev = 'x'; i++; continue; }
    if (depth === 1 && parens === 0 && brackets === 0 && (prev === '{' || prev === ',')) {
      const m = /^\s*(?:async\s+)?([A-Za-z_$][\w$]*)\s*[:(<]/.exec(block.slice(i));
      if (m) { found.push({ name: m[1], at: i }); i += m[0].length - 1; prev = 'x'; continue; }
    }
    if (!/\s/.test(c)) prev = depth === 1 && parens === 0 && brackets === 0 ? c : 'x';
    i++;
  }
  return found.map((f, k) => ({
    name: f.name,
    span: block.slice(f.at, k + 1 < found.length ? found[k + 1].at : block.length),
  }));
}

function methods(): Method[] {
  const out: Method[] = [];
  for (const f of LIB_FILES) {
    for (const m of f.body.matchAll(GROUP)) {
      const open = f.body.indexOf('{', m.index!);
      const block = objectAt(f.body, open);
      if (!CALLS_API.test(block)) continue;
      const spans = keyedSpans(block);
      if (spans.length < 2) continue;                    // a config object, not a client
      for (const s of spans) {
        out.push({ key: `${f.rel}#${m[1]}.${s.name}`, module: f.rel, group: m[1], name: s.name, span: s.span });
      }
    }
  }
  return out;
}

const METHODS = methods();

/**
 * Local names a file may be using for `group` — the import alias and a re-binding. A method reads
 * as called on `group.name`, `group?.name`, or a `const { name } = group` destructure. Deliberately
 * NOT a bare token match: `approve` alone appears on treatments, breaches and appetite statements,
 * and a pattern that matches too little does not merely under-report — it invents defects (F2-163).
 */
function aliases(group: string, body: string): string[] {
  const names = [group];
  for (const m of body.matchAll(new RegExp(`\\b${group}\\s+as\\s+([A-Za-z_$][\\w$]*)`, 'g'))) names.push(m[1]);
  for (const m of body.matchAll(new RegExp(`\\b(?:const|let|var)\\s+([A-Za-z_$][\\w$]*)\\s*=\\s*${group}\\b`, 'g'))) names.push(m[1]);
  return names;
}

function isCalled(m: Method): boolean {
  for (const f of PRODUCTION) {
    if (f.rel === m.module) continue;
    if (!f.body.includes(m.group)) continue;
    for (const alias of aliases(m.group, f.body)) {
      if (new RegExp(`\\b${alias}\\s*\\??\\.\\s*${m.name}\\b`).test(f.body)) return true;
    }
    for (const d of f.body.matchAll(new RegExp(`\\{([^{}]*)\\}\\s*=\\s*${m.group}\\b`, 'g'))) {
      if (d[1].split(',').some(p => p.trim().split(':')[0].trim() === m.name)) return true;
    }
  }
  return false;
}

// ── endpoints, for the dead-duplicate invariant ────────────────────────────────────────────────

const PATH_CONST = /(?:const|let)\s+([A-Za-z_$][\w$]*)\s*(?::\s*string\s*)?=\s*[`'"]([^`'"]*\/api\/[^`'"]*)[`'"]/g;
const API_CALL = /\bapi\.(get|post|put|patch|del|delete)\s*(?:<[^()]*?>)?\s*\(\s*([`'"][^`'"]*[`'"]|[A-Za-z_$][\w$]*)/g;

const CONSTS_BY_MODULE = new Map(LIB_FILES.map(f =>
  [f.rel, new Map([...f.body.matchAll(PATH_CONST)].map(m => [m[1], m[2]] as const))] as const));

function endpoints(m: Method): string[] {
  const consts = CONSTS_BY_MODULE.get(m.module)!;
  const out = new Set<string>();
  for (const c of m.span.matchAll(API_CALL)) {
    const verb = c[1] === 'del' ? 'delete' : c[1];
    let raw = c[2];
    let path: string | undefined;
    if (/^[`'"]/.test(raw)) {
      path = raw.slice(1, -1)
        .replace(/\$\{\s*([A-Za-z_$][\w$]*)\s*\}/g, (_, id) => consts.get(id) ?? '{}')
        .replace(/\$\{[^}]*\}/g, '{}');
    } else {
      path = consts.get(raw);
    }
    if (!path || !path.includes('/api/')) continue;
    out.add(`${verb} ${path.split('?')[0].replace(/\/$/, '')}`);
  }
  return [...out];
}

const REACHED_ENDPOINTS = new Set<string>();
for (const m of METHODS) if (isCalled(m)) for (const e of endpoints(m)) REACHED_ENDPOINTS.add(e);
// Plain function exports serve endpoints too — `evidenceItems.ts#approveEvidenceItem` is why this
// invariant exists at all, and it is a function, not a grouped method.
const PLAIN = /^export\s+(?:async\s+)?(?:function|const|let|var)\s+([A-Za-z_$][\w$]*)/gm;
/**
 * ⚠ The grouped clients must be excluded here. `export const aarApi = { … }` also matches `PLAIN`,
 * and the reachability test in this loop is a bare token match — so referencing the group object
 * anywhere marked every one of its endpoints reached, and the invariant below reported 192 "dead
 * duplicates" out of 196 dark methods. That is the object-granularity defect this entire finding is
 * about, reintroduced inside the invariant written to catch it. 🔑 An instrument can contain the bug
 * it measures, and it looks like a working instrument while it does.
 */
const GROUP_KEYS = new Set(METHODS.map(m => `${m.module}#${m.group}`));
for (const f of LIB_FILES) {
  const consts = CONSTS_BY_MODULE.get(f.rel)!;
  for (const d of f.body.matchAll(PLAIN)) {
    if (GROUP_KEYS.has(`${f.rel}#${d[1]}`)) continue;
    const next = f.body.indexOf('\nexport ', d.index! + 1);
    const span = f.body.slice(d.index!, next === -1 ? f.body.length : next);
    if (!CALLS_API.test(span)) continue;
    const reached = OUTSIDE.some(o => new RegExp(`\\b${d[1]}\\b`).test(o.body));
    if (!reached) continue;
    for (const c of span.matchAll(API_CALL)) {
      const verb = c[1] === 'del' ? 'delete' : c[1];
      const raw = c[2];
      const path = /^[`'"]/.test(raw)
        ? raw.slice(1, -1).replace(/\$\{\s*([A-Za-z_$][\w$]*)\s*\}/g, (_, id) => consts.get(id) ?? '{}')
                          .replace(/\$\{[^}]*\}/g, '{}')
        : consts.get(raw);
      if (path?.includes('/api/')) REACHED_ENDPOINTS.add(`${verb} ${path.split('?')[0].replace(/\/$/, '')}`);
    }
  }
}

const DARK = METHODS.filter(m => !isCalled(m));

// ── assertions ─────────────────────────────────────────────────────────────────────────────────

describe('F2-165 — a dark method whose endpoint has a live sibling is deletable dead code', () => {
  it('no grouped-client method duplicates an endpoint some other export already serves', () => {
    // ⚠ Hard zero, and it can be one BECAUSE the remedy is unambiguous: nothing is lost by
    // deleting the method and nothing is excused by keeping it. `evidenceApi.approve/reject/submit`
    // were the measured three — POSTing the same `/api/v1/evidence/items/{}/…` as
    // `evidenceItems.ts`, whose functions a screen calls. Reading them by name shape would have
    // filed "no screen can approve evidence"; reading the endpoint says "dead code".
    const dupes = DARK.flatMap(m => endpoints(m)
      .filter(e => REACHED_ENDPOINTS.has(e))
      .map(e => `${m.key}  →  ${e}`));
    expect(dupes,
      'These uncalled methods hit an endpoint another export already reaches. Delete them — a '
      + 'second client for a live endpoint is the shape that made F2-68(c) report `riskAcceptanceApi` '
      + 'clean:\n  ' + dupes.join('\n  ')).toEqual([]);
  });
});

describe('F2-165 — the dark-method population only decreases', () => {
  const known = new Set(KNOWN_DARK.map(k => k.split(/\s+\/\//)[0].trim()));

  it('no method becomes uncalled without being recorded', () => {
    const fresh = DARK.map(m => m.key).filter(k => !known.has(k));
    expect(fresh,
      'New grouped-client methods that nothing calls. Wire them from a screen, or record them here '
      + 'so the figure is visible rather than silent — and say what the USER cannot do, not just '
      + 'which function is unused:\n  ' + fresh.join('\n  ')).toEqual([]);
  });

  it('an entry that is now called must be removed', () => {
    const nowCalled = METHODS.filter(m => known.has(m.key) && isCalled(m)).map(m => m.key);
    expect(nowCalled, 'Now called — delete from KNOWN_DARK (progress, not a failure):\n  '
      + nowCalled.join('\n  ')).toEqual([]);
  });

  it('names nothing that no longer exists', () => {
    const present = new Set(METHODS.map(m => m.key));
    const vanished = [...known].filter(k => !present.has(k));
    expect(vanished, 'KNOWN_DARK names methods that are gone (renamed, deleted, or the client is no '
      + 'longer grouped):\n  ' + vanished.join('\n  ')).toEqual([]);
  });

  it('has no duplicate entries', () => {
    const keys = KNOWN_DARK.map(k => k.split(/\s+\/\//)[0].trim());
    expect(keys.length).toBe(new Set(keys).size);
  });
});

describe('F2-165 — the scan is not vacuous, and its keys are real', () => {
  it('finds the grouped-client surface it is meant to measure', () => {
    const groups = new Set(METHODS.map(m => `${m.module}#${m.group}`));
    expect(groups.size, 'the grouped-client census collapsed').toBeGreaterThanOrEqual(70);
    expect(METHODS.length, 'the method census collapsed').toBeGreaterThanOrEqual(800);
  });

  it('every extracted name is a real property, not a type argument or a word from prose', () => {
    // The regression pin for this slice's own defect. A hand-rolled stripper mis-tokenised a regex
    // literal in `lib/api.ts` and prose words came out as keys; a depth-1 match without the
    // segment-boundary rule turned `api.get<PageResponse<Risk>>` into a key called `PageResponse`.
    // Both failures look identical from outside: a plausible name that is not a method.
    // Checked against the STRIPPED body, deliberately: raw source carries comments between keys
    // (`,\n  // SOC ops events\n  runs:`), so a raw check fails on ~30 perfectly real methods and
    // would have had me "fix" an extractor that was right. Not circular — the extractor is a
    // char-walk with paren/bracket/string state, this is a flat regex; they agree only if the key
    // really is segment-initial, which is the property being asserted.
    const bodies = new Map(LIB_FILES.map(f => [f.rel, f.body] as const));
    const phantom = METHODS.filter(m =>
      !new RegExp(`[{,]\\s*(?:async\\s+)?${m.name}\\s*[:(<]`).test(bodies.get(m.module)!));
    expect(phantom.map(p => p.key),
      'These are not object keys in the source — the extractor is matching something else:\n  '
      + phantom.map(p => p.key).join('\n  ')).toEqual([]);
  });

  it('sees a called method as called and an uncalled one as uncalled', () => {
    // Positive control: F2-164 wired riskAcceptanceApi.approve into RiskAcceptancesPanel.
    const called = METHODS.find(m => m.key === 'lib/api.ts#riskAcceptanceApi.approve');
    expect(called, 'riskAcceptanceApi.approve vanished — repoint this control').toBeDefined();
    expect(isCalled(called!), 'F2-164 wired this; if it reads uncalled the matcher is broken').toBe(true);

    // Negative control: no screen dispositions a KYC screening hit.
    const uncalled = METHODS.find(m => m.key === 'lib/api.ts#kycApi.disposition');
    expect(uncalled, 'kycApi.disposition vanished — repoint this control').toBeDefined();
    expect(isCalled(uncalled!)).toBe(false);
  });

  it('resolves endpoints through a module-level path constant', () => {
    // `${BASE}/${id}/approve` is the dominant idiom; without constant folding the duplicate
    // invariant above compares `/{}/{}/approve` against nothing and silently passes.
    const withConst = METHODS.filter(m => endpoints(m).some(e => e.includes('/api/')));
    expect(withConst.length, 'endpoint resolution collapsed').toBeGreaterThanOrEqual(600);
    expect(REACHED_ENDPOINTS.size, 'no reached endpoints resolved — the invariant is vacuous')
      .toBeGreaterThanOrEqual(300);
  });

  it('the recorded dark population is the size the register publishes', () => {
    // F2-163's lesson, applied to my own figure: I filed that finding on a count my scan had
    // inflated by 13. A number in a register with nothing gating it goes stale or was never true.
    expect(KNOWN_DARK.length).toBe(DARK.length);
  });
});
