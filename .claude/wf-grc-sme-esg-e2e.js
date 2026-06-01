export const meta = {
  name: 'grc-sme-experts-esg-e2e',
  description: 'Independent SME/Expert audit of Saudi GRC catalogs (NCA, NDI, data-mgmt, PDPL, BCM, ERM, ESG) + day-to-day module review + live end-to-end journeys + workflow/state-machine tests of all processes',
  whenToUse: 'Comprehensive from-scratch audit across all seven framework families incl. ESG, day-to-day module support review, live E2E journeys, and explicit workflow-engine / state-machine verification against the running backend.',
  phases: [
    { title: 'Map catalogs', detail: 'one read-only SME agent per framework family (incl. ERM + ESG)' },
    { title: 'Review modules', detail: 'evidence lifecycle, GRC Expert, ERM/risk, ESG, day-to-day support' },
    { title: 'E2E + workflows', detail: 'live WRITE journey + read journeys per family + workflow-engine/state-machine pass' },
    { title: 'Report', detail: 'synthesize overall verdict + prioritized gaps' },
  ],
}

const ENV = [
  'SHARED ENVIRONMENT (use Bash with python + curl/urllib; only `python` is on PATH, not python3):',
  '- Backend RUNNING at http://localhost:8084 (health: GET /actuator/health -> status UP).',
  '- Login: POST http://localhost:8084/api/auth/login  (path is /api/auth/login, NOT /api/v1/...).',
  '  body: {"username":"admin","password":"Admin@12345"}  -> JSON field accessToken (TTL ~1h).',
  '  Then send header  Authorization: Bearer <accessToken>  on every /api/v1/* call.',
  '- DB (SQL Server) via python pyodbc. Always reconfigure stdout to utf-8 before printing Arabic:',
  '    import pyodbc, sys; sys.stdout.reconfigure(encoding="utf-8")',
  '    c=pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")',
  '- SME CATALOG tables (GLOBAL reference data, NOT org-scoped) — real columns:',
  '    evidence.frameworks (id, code, name_en, name_ar, issuer, is_active ...)',
  '    evidence.controls   (id, framework_id, control_code, name_en, name_ar ...)',
  '    evidence.sub_controls (id, control_id, sub_control_code, name_en, name_ar, evidence_guidance, evidence_guidance_ar, is_active ...)',
  '  Recommended evidence per sub-control = evidence_guidance (EN) + evidence_guidance_ar (AR).',
  '  Inspect real columns first via INFORMATION_SCHEMA.COLUMNS if unsure.',
  '- GRC EXPERT API: GET http://localhost:8084/api/v1/evidence/expert (recommended-evidence catalog with COVERED/PARTIAL/EXPIRED/GAP rollup per sub-control). NOTE: a freshness fix was just merged — coverage is now COVERED only when an APPROVED item is still within valid_to; if all approved evidence is expired the sub-control reports EXPIRED (not COVERED). Verify this holds.',
  '- EVIDENCE LIFECYCLE API: POST /api/v1/evidence/requests, GET /api/v1/evidence/requests, EvidenceItem submit/approve, EvidenceReview complete.',
  '- RISK / ERM: /api/v1/risk/* and /api/v1/risks; dashboard /api/v1/dashboard/*. NOTE: risk.risks.risk_level was just changed to band on RESIDUAL (COALESCE(residual L*I, inherent L*I)). Verify riskLevel reflects residual.',
  '- PLATFORM WORKFLOW ENGINE: look for /api/v1/workflow* (module.workflow / platform) — definitions + instances + transitions. Many modules also carry their own workflow_status state machines enforced in the service layer.',
  '- ESG: there is NO ESG framework in evidence.frameworks (confirmed). ESG likely lives as register/module surfaces: climate risk, TCFD, emissions/GHG, materiality, ESG disclosures/scores, emerging-risks. Frontend lib: tss-grc-ui-v2/src/lib/{climateRisk,emergingRisks}.ts and esg pages. Find where ESG day-to-day management actually lives and assess it; do NOT silently FAIL if it is register-driven.',
  '- Repo root: C:/AYMAN/Github/Claude-GRC-2/.claude/worktrees/tender-curie-cee787. Frontend: tss-grc-ui-v2 (React). Backend: tss-grc-backend/src/main/java/com/tss/grc/module/.',
  '- Return ONLY the structured object requested. Be precise and quantitative.',
].join('\n')

const CATALOG_SCHEMA = {
  type: 'object',
  required: ['family', 'totalFrameworks', 'totalControls', 'totalSubControls', 'recEvidenceEnCount', 'recEvidenceArCount', 'bilingualPct', 'verdict'],
  properties: {
    family: { type: 'string' },
    frameworks: { type: 'array', items: { type: 'object', properties: {
      code: { type: 'string' }, nameEn: { type: 'string' }, controls: { type: 'integer' }, subControls: { type: 'integer' } } } },
    totalFrameworks: { type: 'integer' },
    totalControls: { type: 'integer' },
    totalSubControls: { type: 'integer' },
    recEvidenceEnCount: { type: 'integer' },
    recEvidenceArCount: { type: 'integer' },
    bilingualPct: { type: 'number' },
    livesWhere: { type: 'string' },
    samples: { type: 'array', items: { type: 'string' } },
    gaps: { type: 'array', items: { type: 'string' } },
    verdict: { type: 'string', enum: ['PASS', 'WARN', 'FAIL'] },
  },
}

const MODULE_SCHEMA = {
  type: 'object',
  required: ['module', 'supportsDayToDay', 'verdict'],
  properties: {
    module: { type: 'string' },
    supportsDayToDay: { type: 'boolean' },
    capabilities: { type: 'array', items: { type: 'string' } },
    gaps: { type: 'array', items: { type: 'string' } },
    severity: { type: 'string', enum: ['NONE', 'LOW', 'MEDIUM', 'HIGH'] },
    verdict: { type: 'string', enum: ['PASS', 'WARN', 'FAIL'] },
    notes: { type: 'string' },
  },
}

const E2E_SCHEMA = {
  type: 'object',
  required: ['journey', 'mode', 'verdict'],
  properties: {
    journey: { type: 'string' },
    mode: { type: 'string', enum: ['WRITE', 'READ', 'WORKFLOW'] },
    steps: { type: 'array', items: { type: 'object', properties: {
      step: { type: 'string' }, httpStatus: { type: 'integer' }, result: { type: 'string' } } } },
    createdIds: { type: 'array', items: { type: 'string' } },
    verdict: { type: 'string', enum: ['PASS', 'WARN', 'FAIL'] },
    notes: { type: 'string' },
  },
}

const REPORT_SCHEMA = {
  type: 'object',
  required: ['overallVerdict', 'summary'],
  properties: {
    overallVerdict: { type: 'string', enum: ['PASS', 'WARN', 'FAIL'] },
    familyVerdicts: { type: 'array', items: { type: 'object', properties: { family: { type: 'string' }, verdict: { type: 'string' }, note: { type: 'string' } } } },
    moduleVerdicts: { type: 'array', items: { type: 'object', properties: { module: { type: 'string' }, verdict: { type: 'string' }, note: { type: 'string' } } } },
    e2eVerdicts: { type: 'array', items: { type: 'object', properties: { journey: { type: 'string' }, verdict: { type: 'string' }, note: { type: 'string' } } } },
    regressionChecks: { type: 'array', items: { type: 'string' } },
    topGaps: { type: 'array', items: { type: 'string' } },
    recommendations: { type: 'array', items: { type: 'string' } },
    summary: { type: 'string' },
  },
}

// ─── Phase 1 — Map SME catalogs (read-only, one per family) ───────────────
phase('Map catalogs')
const FAMILIES = [
  { key: 'NCA', hint: 'National Cybersecurity Authority frameworks: codes/names containing ECC, CCC, DCC, CSCC, OTCC, TCC, or NCA. Map ALL NCA frameworks found.' },
  { key: 'NDI', hint: 'National Data Index: SDAIA/NDMO. Codes/names containing NDI, National Data, NDMO-DMPDP.' },
  { key: 'Data Management', hint: 'NDMO Data Management framework (data governance domains). Codes/names containing NDMO, Data Management, Data Governance. If it overlaps the NDI framework, say so explicitly.' },
  { key: 'PDPL', hint: 'Personal Data Protection Law: codes/names containing PDPL or Personal Data Protection.' },
  { key: 'BCM', hint: 'Business Continuity Management: codes/names containing BCM, Business Continuity, ISO 22301, SAMA BCM, NCEMA.' },
  { key: 'ERM', hint: 'Enterprise Risk Management: codes/names containing ERM, COSO, ISO 31000, SAMA ERM, Enterprise Risk, RISK-. If only ERM-1 / RISK-1 exist as thin catalogs and the live ERM capability lives in the risk module, say so explicitly and rate WARN rather than FAIL.' },
  { key: 'ESG', hint: 'Environmental, Social & Governance: codes/names containing ESG, climate, TCFD, sustainability, emissions, GHG, GRI, materiality. ESG is NOT expected to exist as a sub-control catalog in evidence.frameworks (confirmed absent). Report zero catalog counts if so, then state WHERE ESG actually lives (climate-risk / emerging-risks / ESG-disclosure register/module, frontend lib climateRisk.ts / emergingRisks.ts) and whether that capability is bilingual + operable. Rate WARN (register-driven, capability exists elsewhere) rather than FAIL; only FAIL if there is no ESG capability anywhere.' },
]
const catalogs = await parallel(FAMILIES.map(f => () =>
  agent(ENV + '\n\nTASK: Map the SME sub-control + recommended-evidence catalog for the ' + f.key + ' family.\nScope hint: ' + f.hint + '\nSteps:\n1. Query evidence.frameworks for every framework in this family (LIKE on code/name_en, case-insensitive). List each: code, name_en, #controls, #sub_controls.\n2. Totals: frameworks / controls / sub-controls.\n3. Recommended-evidence coverage: count active sub_controls with non-empty evidence_guidance (EN) and with non-empty evidence_guidance_ar (AR). bilingualPct = (sub-controls with BOTH) / total active sub-controls * 100 (report 0 if no sub-controls).\n4. 2-3 sample sub-controls (code + short EN and AR evidence snippet) as proof, if any exist.\n5. If the family has NO catalog framework, set counts to 0 and use livesWhere to state where the capability actually lives (module/register/frontend) and whether it is bilingual + operable.\n6. Gaps: sub-controls missing EN or AR guidance, inactive frameworks, or absence of a catalog.\nREAD ONLY — no INSERT/UPDATE/DELETE.\nVerdict PASS if the family has frameworks+controls+sub-controls with ~100% bilingual recommended evidence; WARN if partial OR register/module-driven rather than catalogued; FAIL only if the capability is absent entirely.',
    { label: 'catalog:' + f.key, phase: 'Map catalogs', schema: CATALOG_SCHEMA })
)).then(r => r.filter(Boolean))

// ─── Phase 2 — Module / lifecycle review (read-only) ──────────────────────
phase('Review modules')
const MODULES = [
  { key: 'evidence-lifecycle', prompt: 'Review the EVIDENCE-MANAGEMENT LIFECYCLE module (com.tss.grc.module.evidence). Read entities (EvidenceRequest, EvidenceItem, EvidenceReview), services, controllers. Document: (a) lifecycle state machine(s) + allowed transitions; (b) how recommended evidence from the SME catalog feeds an EvidenceRequest; (c) day-to-day support: file/binary upload, expiry/EXPIRED + auto-expiry scheduler, Request<->Item FK linkage (request_id), unified review path, NEEDS_REVISION, transition-validation guards (unknown 400 / illegal 409); (d) VERIFY the just-merged GRC Expert freshness fix: GrcExpertService coverage demotes COVERED->EXPIRED when all approved items are past valid_to (aggregateFreshApprovedBySubControl). Rate severity + verdict.' },
  { key: 'grc-expert-surface', prompt: 'Review the GRC EXPERT surface (GET /api/v1/evidence/expert + GrcExpertService + GrcExpertPage). Confirm framework->control->sub-control->recommended-evidence (EN+AR) tree, COVERED/PARTIAL/EXPIRED/GAP rollup per sub-control, rollup math consistency, and that raising a request from a recommended-evidence line wires to the evidence lifecycle. Confirm the EXPIRED state renders (badge tone + bilingual label) after the recent freshness fix. Verdict + gaps.' },
  { key: 'erm-risk-module', prompt: 'Review the ENTERPRISE RISK MANAGEMENT capability (com.tss.grc.module.risk + risk UI). Cover risk register, assessment (likelihood x impact -> inherent vs residual), treatment plans, KRIs, heatmap, appetite, workflow_status transitions, and risk<->control/framework linkage. VERIFY the just-merged residual-rating fix: risk.risks.risk_level now bands on COALESCE(residual, inherent) and the register heatmap/level/KPI use residual-with-inherent-fallback. Identify remaining gaps (status-enum mismatch, mock links/kris tabs, EN-only treatment/assessment notes, null computed scores in POST). Rate severity + verdict.' },
  { key: 'esg-module', prompt: 'Review the ESG capability (Environmental, Social & Governance). ESG has no sub-control catalog, so find where it lives: climate-risk, TCFD, GHG/emissions, materiality assessment, ESG disclosure/scoring, emerging-risks. Inspect backend (search module.risk / module.reporting / any esg package and /api/* endpoints) and frontend (tss-grc-ui-v2/src/lib/{climateRisk,emergingRisks}.ts + esg/climate pages). Document day-to-day ESG operator support: can an operator maintain ESG metrics/disclosures/climate risks end to end? Is it bilingual Arabic-primary? Is it backed by live APIs or mock/static data? Rate severity + verdict.' },
  { key: 'day-to-day-management', prompt: 'Review whether the NCA / NDI / data-management / PDPL / BCM / ERM / ESG modules support an operator day-to-day journey: dashboards (KPIs), registers (list/filter/drill), workflows (state transitions), bilingual Arabic-primary RTL UI. Sample backend /api/v1/* endpoints and frontend tss-grc-ui-v2/src. Confirm the privacy_lc DSR 500 is fixed (GET /api/privacy-lc/dsr-cases now 200) and i18n defaults to Arabic-primary. Identify cross-cutting strengths and what is missing for daily operation. Verdict + gaps + severity.' },
]
const modules = await parallel(MODULES.map(m => () =>
  agent(ENV + '\n\nTASK (READ ONLY code+API review): ' + m.prompt,
    { label: 'review:' + m.key, phase: 'Review modules', schema: MODULE_SCHEMA })
)).then(r => r.filter(Boolean))

// ─── Phase 3 — Live E2E journeys + workflow-engine pass ───────────────────
phase('E2E + workflows')
const writeJourney = () => agent(ENV + '\n\nTASK: Run the LIVE end-to-end evidence-management Collect journey (mode=WRITE). You are the ONLY agent permitted to create persisted state.\nSteps (record HTTP status + key payload each):\n1. Login -> accessToken.\n2. GET /api/v1/evidence/expert. Pick ONE sub-control whose rollup is GAP. Note subControlId + framework/control codes.\n3. POST /api/v1/evidence/requests for it (priority HIGH, bilingual title). Capture id + requestCode + status.\n4. GET /api/v1/evidence/requests?status=OPEN and confirm persistence.\n5. Create an EvidenceItem (requestId set), submit (DRAFT->PENDING_REVIEW), approve (PENDING_REVIEW->APPROVED); confirm the request auto-fulfils (OPEN->SUBMITTED) and the expert rollup flips GAP->COVERED.\nCreate AT MOST 2 requests + 1 item. Report every createdId. THEN CLEAN UP: DELETE the item then the request (soft-delete) and confirm the rollup returns to GAP. Verdict PASS if the full collect->fulfil loop worked and cleanup succeeded.',
  { label: 'e2e:collect-WRITE', phase: 'E2E + workflows', schema: E2E_SCHEMA })

const workflowJourney = () => agent(ENV + '\n\nTASK: Verify the WORKFLOW ENGINE + cross-module STATE MACHINES (mode=WORKFLOW, negative-path + read only — create NO net new persisted state).\nSteps:\n1. Login.\n2. Discover the platform workflow engine: find and GET its endpoints (search backend module.workflow + /api/v1/workflow*, definitions/instances). Record what is exposed.\n3. For at least THREE different state machines, prove the guard rejects an illegal transition with HTTP 409 (these create no new state): e.g. (a) evidence request status -> an illegal value or illegal move; (b) evidence item DRAFT -> APPROVED (skip review) expecting 409 with from/to/allowed; (c) a risk illegal transition; (d) an unknown status -> expect 400. Record the actual from/to/allowed payloads.\n4. Document, by code inspection, the legal transition path for each (do NOT execute legal transitions that persist state, except those already covered by the WRITE agent).\n5. Confirm the GlobalExceptionHandler mapping: IllegalArgumentException->400, IllegalTransitionException->409.\nVerdict PASS if the workflow engine endpoints respond and at least three state machines enforce guards (illegal->409, unknown->400). Report createdIds = [] (must create nothing).',
  { label: 'e2e:workflow-engine', phase: 'E2E + workflows', schema: E2E_SCHEMA })

const READ_SURFACES = [
  { key: 'NCA-cyber', prompt: 'NCA cybersecurity: browse the expert/framework-controls API, drill an NCA framework -> control -> sub-control, confirm recommended evidence EN+AR is returned, and hit the cyber module register endpoints (controls, incidents, assets, vulnerabilities, assessments, dashboard).' },
  { key: 'NDI-NDMO-data', prompt: 'NDI / NDMO data management: drill the NDMO framework -> control -> sub-control via the catalog API, confirm bilingual recommended evidence, and hit the data-officer / data-classification register endpoints (dashboard, assets, flows, exceptions, NDI cycles/aggregate).' },
  { key: 'PDPL-privacy', prompt: 'PDPL privacy: drill the PDPL framework -> control -> sub-control, confirm bilingual recommended evidence, hit the privacy DSR / breach / ROPA register endpoints + stats + SDAIA Art.31, AND confirm GET /api/privacy-lc/dsr-cases now returns 200 (was 500 before the recent fix).' },
  { key: 'BCM', prompt: 'BCM: drill the BCM framework -> control -> sub-control, confirm bilingual recommended evidence, and hit the BCM register endpoints (BIA, continuity plans + sections, processes, exercises, DR trend, risks, strategies, stats).' },
  { key: 'ERM-risk', prompt: 'ERM / risk: exercise the risk register journey end to end (read-only) — list risks, drill ONE risk (inherent vs residual rating, treatment, assessment), hit risk dashboard/heatmap/KRI/appetite endpoints, confirm bilingual fields, AND confirm riskLevel now reflects RESIDUAL (find a risk whose residual band differs from its inherent band and check the label matches residual).' },
  { key: 'ESG', prompt: 'ESG: exercise whatever ESG surface exists end to end (read-only) — climate risks / TCFD / emissions / materiality / ESG disclosures / emerging-risks. Hit the relevant /api/* endpoints (or note if the page is backed by mock/static frontend data with no live API). Confirm bilingual fields where present. Verdict PASS if a coherent ESG browse/drill journey works (live or clearly-identified mock); WARN if ESG is mock-only or thin.' },
]
const readThunks = READ_SURFACES.map(s => () =>
  agent(ENV + '\n\nTASK: Run a READ-ONLY live end-to-end user journey for the ' + s.key + ' surface (mode=READ). Do NOT create state.\nJourney: ' + s.prompt + '\nRecord each step with HTTP status and a one-line result. Confirm the relevant catalog/register endpoints respond 200 with coherent bilingual data. Verdict PASS if the browse->drill journey works and bilingual content renders.',
    { label: 'e2e-read:' + s.key, phase: 'E2E + workflows', schema: E2E_SCHEMA })
)

const e2e = (await parallel([writeJourney, workflowJourney].concat(readThunks))).filter(Boolean)

// ─── Phase 4 — Synthesis ──────────────────────────────────────────────────
phase('Report')
const report = await agent(ENV + '\n\nYou are the lead reviewer. Synthesize ONE executive verdict from the collected results.\n\nSME CATALOG RESULTS (NCA / NDI / data-mgmt / PDPL / BCM / ERM / ESG):\n' + JSON.stringify(catalogs, null, 1) + '\n\nMODULE / LIFECYCLE REVIEWS (evidence lifecycle, GRC Expert, ERM/risk, ESG, day-to-day):\n' + JSON.stringify(modules, null, 1) + '\n\nLIVE E2E JOURNEYS + WORKFLOW-ENGINE PASS:\n' + JSON.stringify(e2e, null, 1) + '\n\nProduce: overallVerdict (PASS if every catalogued family is complete+bilingual, all E2E journeys pass, and workflow guards are enforced; WARN if catalogs/E2E pass but reviews surfaced structural gaps, or a family like ERM/ESG is register-driven rather than catalogued; FAIL if a control-framework family is absent or a core journey/guard failed); per-family verdicts (one per family incl. ERM + ESG); per-module verdicts; per-journey verdicts (incl. the workflow-engine pass); a regressionChecks array explicitly stating whether the 4 recently-merged fixes hold (ERM residual riskLevel, GRC Expert EXPIRED freshness, privacy_lc DSR 200, Arabic-primary default); prioritized top gaps; concrete recommendations; and a 3-4 sentence executive summary that directly answers the user request (independent SME experts for NCA, NDI, data-management, PDPL, BCM, ERM and ESG; all modules reviewed for day-to-day management; all processes AND workflows end-to-end tested).',
  { label: 'synthesis', phase: 'Report', schema: REPORT_SCHEMA })

return { catalogs, modules, e2e, report }
