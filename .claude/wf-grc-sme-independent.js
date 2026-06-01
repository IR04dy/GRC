export const meta = {
  name: 'grc-sme-experts-independent-e2e',
  description: 'Independent end-to-end verification of Saudi GRC SME/Expert catalogs (NCA/NDI/data-management/PDPL/BCM), evidence lifecycle, day-to-day module review, and live user-journey tests',
  whenToUse: 'Comprehensive, from-scratch audit of the SME sub-control + recommended-evidence catalogs and the evidence-management lifecycle, with live E2E journeys against the running backend.',
  phases: [
    { title: 'Map catalogs', detail: 'one read-only agent per framework family' },
    { title: 'Review modules', detail: 'evidence lifecycle, GRC Expert wiring, day-to-day management support' },
    { title: 'E2E journeys', detail: 'one live WRITE journey + read journeys per family' },
    { title: 'Report', detail: 'synthesize overall verdict + prioritized gaps' },
  ],
}

const ENV = [
  'SHARED ENVIRONMENT (use Bash with python3 + curl):',
  '- Backend RUNNING at http://localhost:8084 (health: GET /actuator/health -> status UP).',
  '- Login: POST http://localhost:8084/api/auth/login  (path is /api/auth/login, NOT /api/v1/...).',
  '  body: {"username":"admin","password":"Admin@12345"}  -> JSON field accessToken (TTL ~1h).',
  '  Then send header  Authorization: Bearer <accessToken>  on every /api/v1/* call.',
  '- DB (SQL Server) via python pyodbc. Always reconfigure stdout to utf-8 before printing Arabic:',
  '    import pyodbc, sys; sys.stdout.reconfigure(encoding="utf-8")',
  '    c=pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")',
  '- SME CATALOG tables (GLOBAL reference data, NOT org-scoped):',
  '    evidence.frameworks (id, code, name_en, name_ar, is_active ...)',
  '    evidence.controls   (id, framework_id, code, title_en, title_ar ...)',
  '    evidence.sub_controls (id, control_id, code, title_en, title_ar, evidence_guidance, evidence_guidance_ar, is_active ...)',
  '  Recommended evidence per sub-control = evidence_guidance (EN) + evidence_guidance_ar (AR).',
  '  Inspect real columns first via INFORMATION_SCHEMA.COLUMNS if unsure.',
  '- GRC EXPERT API: GET http://localhost:8084/api/v1/evidence/expert (recommended-evidence catalog with COVERED/PARTIAL/GAP rollup per sub-control).',
  '- EVIDENCE LIFECYCLE API: POST /api/v1/evidence/requests, GET /api/v1/evidence/requests, EvidenceItem submit/approve, EvidenceReview complete.',
  '  Backend source: tss-grc-backend/src/main/java/com/tss/grc/module/evidence/.',
  '- Repo root: C:/AYMAN/Github/Claude-GRC-2/.claude/worktrees/tender-curie-cee787. Frontend: tss-grc-ui-v2 (React).',
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
    mode: { type: 'string', enum: ['WRITE', 'READ'] },
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
  { key: 'BCM', hint: 'Business Continuity Management: codes/names containing BCM, Business Continuity, ISO 22301, or SAMA BCM.' },
]
const catalogs = await parallel(FAMILIES.map(f => () =>
  agent(ENV + '\n\nTASK: Map the SME sub-control + recommended-evidence catalog for the ' + f.key + ' family.\nScope hint: ' + f.hint + '\nSteps:\n1. Query evidence.frameworks for every framework in this family (LIKE on code/name_en, case-insensitive). List each: code, name_en, #controls, #sub_controls.\n2. Totals: frameworks / controls / sub-controls.\n3. Recommended-evidence coverage: count active sub_controls with non-empty evidence_guidance (EN) and with non-empty evidence_guidance_ar (AR). bilingualPct = (sub-controls with BOTH) / total active sub-controls * 100.\n4. 2-3 sample sub-controls (code + short EN and AR evidence snippet) as proof.\n5. Gaps: sub-controls missing EN or AR guidance, inactive frameworks.\nREAD ONLY — no INSERT/UPDATE/DELETE.\nVerdict PASS if family has frameworks+controls+sub-controls and ~100% bilingual recommended evidence; WARN if partial; FAIL if absent/empty.',
    { label: 'catalog:' + f.key, phase: 'Map catalogs', schema: CATALOG_SCHEMA })
)).then(r => r.filter(Boolean))

// ─── Phase 2 — Module / lifecycle review (read-only) ──────────────────────
phase('Review modules')
const MODULES = [
  { key: 'evidence-lifecycle', prompt: 'Review the EVIDENCE-MANAGEMENT LIFECYCLE module (com.tss.grc.module.evidence). Read entities (EvidenceRequest, EvidenceItem, EvidenceReview), services, controllers. Document: (a) lifecycle state machine(s) + allowed transitions; (b) how recommended evidence from the SME catalog feeds an EvidenceRequest; (c) structural gaps for day-to-day evidence management: file/binary upload, expiry/EXPIRED transition + auto-expiry, Request<->Item FK linkage, divergent review mechanisms (item submit/approve vs EvidenceReview), NEEDS_REVISION handling, request-status transition validation. Rate severity + verdict.' },
  { key: 'grc-expert-surface', prompt: 'Review the GRC EXPERT surface. Inspect GET /api/v1/evidence/expert and its service (aggregateStatusBySubControl / aggregateExpiryBySubControl producing COVERED/PARTIAL/GAP rollup per sub-control). Confirm each sub-control recommended evidence (EN+AR) is surfaced and wired to the evidence lifecycle (drill sub-control -> create request). Check frontend expert + framework-controls pages render bilingually. Assess day-to-day usability for a GRC officer. Verdict + gaps.' },
  { key: 'day-to-day-management', prompt: 'Review whether the NCA / NDI / data-management / PDPL / BCM modules support an operator day-to-day journey: dashboards (KPIs), registers (list/filter/drill), workflows (state transitions), bilingual Arabic-primary RTL UI. Sample backend /api/v1/* endpoints and frontend tss-grc-ui-v2/src (pages + lib). Identify strengths and what is missing for daily operation. Verdict + gaps + severity.' },
]
const modules = await parallel(MODULES.map(m => () =>
  agent(ENV + '\n\nTASK (READ ONLY code+API review): ' + m.prompt,
    { label: 'review:' + m.key, phase: 'Review modules', schema: MODULE_SCHEMA })
)).then(r => r.filter(Boolean))

// ─── Phase 3 — Live E2E journeys (1 WRITE + N READ) ───────────────────────
phase('E2E journeys')
const writeJourney = () => agent(ENV + '\n\nTASK: Run the LIVE end-to-end evidence-management Collect journey (mode=WRITE). You are the ONLY agent permitted to write.\nSteps (record HTTP status + key payload each):\n1. Login -> accessToken.\n2. GET /api/v1/evidence/expert. Pick ONE sub-control whose rollup is GAP. Note subControlId + framework/control codes.\n3. POST /api/v1/evidence/requests to create an EvidenceRequest for it (priority HIGH, bilingual title if DTO allows). Capture created id + reference + status.\n4. GET /api/v1/evidence/requests and confirm persistence (status OPEN/DRAFT, correct subControlId).\n5. If a safe forward transition exists, exercise ONE EvidenceItem submit and/or EvidenceReview step and report status; else mark SKIP with reason.\nCreate AT MOST 2 requests total. Report every createdId for cleanup. Verdict PASS if a request was created and persisted.',
  { label: 'e2e:collect-WRITE', phase: 'E2E journeys', schema: E2E_SCHEMA })

const READ_SURFACES = [
  { key: 'NCA-cyber', prompt: 'NCA cybersecurity: browse the expert/framework-controls API, drill an NCA framework -> control -> sub-control, confirm recommended evidence EN+AR is returned, and hit the cyber module register endpoints.' },
  { key: 'NDI-NDMO-data', prompt: 'NDI / NDMO data management: drill the NDI/NDMO framework -> control -> sub-control via the catalog API, confirm bilingual recommended evidence, and hit the data-officer / data-classification register endpoints.' },
  { key: 'PDPL-privacy', prompt: 'PDPL privacy: drill the PDPL framework -> control -> sub-control, confirm bilingual recommended evidence, and hit the privacy / DSR / breach register endpoints.' },
  { key: 'BCM', prompt: 'BCM: drill the BCM framework -> control -> sub-control, confirm bilingual recommended evidence, and hit the BCM register endpoints.' },
]
const readThunks = READ_SURFACES.map(s => () =>
  agent(ENV + '\n\nTASK: Run a READ-ONLY live end-to-end user journey for the ' + s.key + ' surface (mode=READ). Do NOT write.\nJourney: ' + s.prompt + '\nRecord each step with HTTP status and a one-line result. Confirm the catalog returns sub-controls with bilingual recommended evidence and related list endpoints respond 200 with coherent data. Verdict PASS if the browse->drill journey works and bilingual evidence renders.',
    { label: 'e2e-read:' + s.key, phase: 'E2E journeys', schema: E2E_SCHEMA })
)

const e2e = (await parallel([writeJourney].concat(readThunks))).filter(Boolean)

// ─── Phase 4 — Synthesis ──────────────────────────────────────────────────
phase('Report')
const report = await agent(ENV + '\n\nYou are the lead reviewer. Synthesize ONE executive verdict from the collected results.\n\nSME CATALOG RESULTS:\n' + JSON.stringify(catalogs, null, 1) + '\n\nMODULE / LIFECYCLE REVIEWS:\n' + JSON.stringify(modules, null, 1) + '\n\nLIVE E2E JOURNEYS:\n' + JSON.stringify(e2e, null, 1) + '\n\nProduce: overallVerdict (PASS if every family catalog is complete+bilingual and all E2E journeys pass; WARN if catalogs/E2E pass but module reviews surfaced structural hardening gaps; FAIL if any catalog is absent/empty or a core E2E journey failed); per-family verdicts; per-module verdicts; per-journey verdicts; prioritized top gaps; concrete recommendations; and a 3-4 sentence executive summary that directly answers the user request (SME experts for NCA/NDI/data-management/PDPL/BCM based on sub-controls + recommended evidence wired to the evidence lifecycle, reviewed for day-to-day management and E2E-tested).',
  { label: 'synthesis', phase: 'Report', schema: REPORT_SCHEMA })

return { catalogs, modules, e2e, report }
