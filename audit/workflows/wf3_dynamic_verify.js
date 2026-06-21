export const meta = {
  name: 'tss-grc-dynamic-and-verify',
  description: 'Phase 4-7 dynamic finders (functional/E2E/UX/a11y/best-practices) + adversarial verification of all Critical/High findings',
  phases: [{ title: 'Dynamic finders' }, { title: 'Verify Crit/High' }],
}

function spec(phase) {
  return `
You are producing REAL, verified ${phase} findings for a professional audit report, grounded in the dynamic
test artifacts already captured in audit/raw and audit/artifacts (the live app ran on http://localhost:5173 /
backend :8084; admin/Admin@12345). OUTPUT CONTRACT — write a single UTF-8 JSON file (no fences/prose) to OUTFILE:
  { "findings": [ <finding>, ... ] }
Each <finding> keys EXACTLY: "id" (use PREFIX), "title", "phase" (="${phase}"), "category",
  "severity" ("Critical"|"High"|"Medium"|"Low"|"Info"), "cvss" (risk rationale), "file" (repo-relative or the
  artifact path the evidence came from), "lines", "locations" (array), "description", "rootCause",
  "evidence" (real metric/snippet from the artifacts), "impact", "reproduction", "resolution",
  "patchBefore", "patchAfter", "effort" ("S"|"M"|"L"), "references" (array; WCAG SC ids for a11y).
RULES: ground every finding in a real artifact or real source line; never fabricate numbers. Repo root = cwd.
After writing the file, return ONLY the summary object from your schema.`
}

const SUMMARY_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['slug','outfile','findingCount','critical','high','medium','low','info','notes'],
  properties: { slug:{type:'string'},outfile:{type:'string'},findingCount:{type:'integer'},
    critical:{type:'integer'},high:{type:'integer'},medium:{type:'integer'},low:{type:'integer'},info:{type:'integer'},notes:{type:'string'} },
}
const VERDICT_SCHEMA = {
  type: 'object', additionalProperties: false, required: ['verdicts'],
  properties: { verdicts: { type: 'array', items: {
    type: 'object', additionalProperties: false,
    required: ['id','status','finalSeverity','rationale'],
    properties: { id:{type:'string'}, status:{type:'string',enum:['confirmed','refuted','adjusted']},
      finalSeverity:{type:'string',enum:['Critical','High','Medium','Low','Info']}, rationale:{type:'string'} } } } },
}

const FIND = 'audit/findings'

const finders = [
  { slug:'dyn-functional', prefix:'FUNC', phase:'Functional', label:'functional-coverage',
    prompt:`Assess functional test coverage. Facts: backend has 1858 tests, ALL PASS (audit/raw/mvn-test.log,
target/surefire-reports), JaCoCo line 38.5% / instruction 37.1% / branch 26.0% (target/site/jacoco/jacoco.csv);
frontend tss-grc-ui-v2 has NO test runner and 0 tests; tsc --noEmit = 0 errors. The backend has 231 controllers /
2175 endpoints. Produce findings on: (a) frontend has zero automated tests (High); (b) low branch coverage 26%
and which high-risk areas are untested — cross-reference audit/crit_high.json: for several Critical/High security
findings, check whether a *Test.java exists for that class (e.g. ccm CustomSqlRunner, bcm BcmService BIA path);
(c) recommend concrete unit/integration tests (with example test skeletons in patchAfter). Quantify.` },
  { slug:'dyn-e2e', prefix:'E2E', phase:'E2E', label:'e2e-workflow',
    prompt:`Assess E2E/workflow coverage from the Playwright run (audit/raw/playwright.log; results in
audit/artifacts/e2e/results: axe-summary.json, demo-bypass-url.txt, reload-url.txt, session-keys.txt).
Verified facts: 6 E2E tests pass — login success → /risk, login failure shows .login-error, the "continue as
demo" button does NOT bypass auth (it bounced to /login?next=/risk → route guard works), session persists across
reload, mobile renders. The access token is stored in sessionStorage (session-keys.txt shows the raw JWT).
Produce findings: positives as Info; gaps as Low/Medium — no E2E for logout, no CRUD/multi-step workflow journeys
(e.g. create→approve→archive across the documented state machines), no negative/permission-denied E2E, no
cross-tenant isolation E2E. Recommend a Playwright suite structure (patchAfter skeleton).` },
  { slug:'dyn-ux', prefix:'UX', phase:'UX', label:'ux-critique',
    prompt:`Do a UX/design critique. READ these screenshots with the Read tool:
audit/artifacts/e2e/results/shots/login.png, risk-register.png, dashboard.png, bcm.png, compliance.png, settings.png,
and audit/artifacts/e2e/results/shots/mobile/risk-register.png, mobile/dashboard.png. Also read the design tokens/
layout CSS in tss-grc-ui-v2/src/styles (or src/index.css / app shell). Apply design-critique methodology:
information hierarchy, layout consistency, navigation clarity, form feedback states, empty/loading/error states,
CTA wording & prominence, density, and responsive behaviour (mobile shots). Produce concrete UX findings with
the screen as file= and a specific recommendation in resolution. Severity by user impact (mostly Low/Medium).` },
  { slug:'dyn-uxcopy', prefix:'COPY', phase:'UX', label:'ux-copy',
    prompt:`Review UX copy / microcopy. Read tss-grc-ui-v2/src/translations/en.ts (and ar.ts keys) plus
tss-grc-ui-v2/src/pages/LoginPage.tsx and a couple of error/empty-state strings. Apply ux-copy best practices:
error messages should be specific & actionable, CTAs clear, empty states helpful, no jargon, consistent voice,
bilingual parity (EN/AR). Flag: generic errors ("loginNetworkError"), the pre-filled admin/Admin@12345
credentials in LoginPage (lines 25-26) shown by default (UX + security-adjacent), the "continue as demo" CTA that
silently bounces to login. Produce findings with before/after copy in patchBefore/patchAfter.` },
  { slug:'dyn-a11y', prefix:'A11Y', phase:'Accessibility', label:'a11y-wcag',
    prompt:`WCAG 2.1 AA accessibility audit. Read the axe results: audit/artifacts/e2e/results/axe-summary.json
and the per-page files audit/artifacts/e2e/results/axe/*.json. Verified: login page = 0 violations; all 9
authenticated pages fail ONLY "color-contrast" (serious, WCAG 1.4.3) with 68 total instances (risk-register 28,
dashboard 10, compliance 6, settings 6, cyber 4, privacy 4, bcm 4, tprm 3, knowledge-base 3); Lighthouse a11y
score for /login = 100. Find the ROOT CAUSE by reading the colour tokens in the frontend CSS
(tss-grc-ui-v2/src/styles or index.css — muted/secondary text colours on light backgrounds) and give exact
before/after token values that reach 4.5:1. Map each issue to its WCAG SC (references[]). Add findings for the
checks axe canNOT automate (keyboard nav, visible focus, focus order, SR landmarks, motion) as Medium/Low
recommendations, noting they need manual verification. Provide a per-criterion AA pass/fail view in the
description of a summary finding.` },
  { slug:'dyn-lighthouse', prefix:'LH', phase:'UX', label:'lighthouse-bp',
    prompt:`Triage the Lighthouse report at audit/raw/lighthouse-login.report.json (scores: Performance 38,
Accessibility 100, Best-Practices 77, SEO 82 for /login). Read the JSON 'audits' for the failing best-practices
and performance items: FCP, LCP, TBT, speed-index, total-byte-weight, unused-javascript, render-blocking,
console errors, deprecations, CSP/HTTPS best-practices. Produce findings for the notable failures with the real
metric values as evidence. The low Performance 38 even on the public login page indicates a heavy initial bundle
— cross-reference whether App.tsx code-splits routes. Severity by impact.` },
]

phase('Dynamic finders')
const finderSummaries = (await parallel(finders.map(f => () => agent(
  `${f.prompt}
${spec(f.phase)}
PREFIX = "${f.prefix}"
OUTFILE = "${FIND}/${f.slug}.json"
Write the JSON file, then return the summary.`,
  { label: f.label, phase: 'Dynamic finders', schema: SUMMARY_SCHEMA })))).filter(Boolean)

// ---- adversarial verification of the 55 Critical/High findings ----
phase('Verify Crit/High')
const N = 55, BATCH = 5
const batches = []
for (let i = 0; i < N; i += BATCH) batches.push([i, Math.min(i + BATCH, N)])

const verdictArrays = (await parallel(batches.map(([s, e2], bi) => () => agent(
  `You are a skeptical senior reviewer doing ADVERSARIAL verification for an audit report.
Read audit/crit_high.json — a JSON array of 55 Critical/High findings (fields: id,title,phase,severity,category,
file,lines). Verify ONLY the findings at array indices ${s} through ${e2 - 1} (inclusive of ${s}, exclusive of ${e2}).
For EACH assigned finding: open the cited file at the cited lines, read enough surrounding code to judge it, and
decide: "confirmed" (real and severity right), "adjusted" (real but wrong severity — give finalSeverity), or
"refuted" (not actually exploitable / mitigated elsewhere / false positive — explain why, set a lower
finalSeverity, usually Low/Info). Default to skepticism: if the cited code does not clearly support the claim,
lean toward refuted/adjusted. For security findings, specifically check whether org-scoping / @PreAuthorize /
a guard elsewhere already mitigates it. Return the verdicts array (one entry per assigned id).`,
  { label: `verify ${s}-${e2 - 1}`, phase: 'Verify Crit/High', schema: VERDICT_SCHEMA })))).filter(Boolean)

const verdicts = verdictArrays.flatMap(v => v.verdicts || [])
const confirmed = verdicts.filter(v => v.status === 'confirmed').length
const adjusted = verdicts.filter(v => v.status === 'adjusted').length
const refuted = verdicts.filter(v => v.status === 'refuted').length
log(`Verification: ${verdicts.length} verdicts — ${confirmed} confirmed, ${adjusted} adjusted, ${refuted} refuted`)

return { finderSummaries, dynFindings: finderSummaries.reduce((a,s)=>a+(s.findingCount||0),0), verdicts }
