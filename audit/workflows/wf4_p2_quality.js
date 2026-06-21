export const meta = {
  name: 'tss-grc-p2-quality',
  description: 'P2 safe quality batch: error-handling (log the cause) + dead-code removal + 2 correctness bugs, by module cluster',
  phases: [{ title: 'P2 quality fixes' }],
}

const RULES = `
You are a staff Java engineer applying SMALL, SAFE, behaviour-preserving quality fixes on the branch
fix/audit-p1-hardening (working dir = repo root). HARD RULES:
- Make ONLY the changes for the findings listed below. Match the file's existing style/imports.
- Error-handling fixes: log the exception OBJECT (cause/stacktrace) — e.g. log.warn("... {}", id, ex) — and add a
  SLF4J logger if the class lacks one (prefer @Slf4j if the class already uses Lombok; else a private static
  Logger). KEEP the existing swallow/fail-closed behaviour unless the finding says otherwise.
- Dead-code fixes: delete the named dead methods/fields/imports; then remove anything that ONLY existed to shield
  them (and confirm the file still compiles logically — no remaining references).
- If a change alters observable behaviour (a corrected query, a fail-fast), UPDATE the affected unit test(s) so
  they assert the new correct behaviour. Search for the test by the service/class name.
- DO NOT run mvn, gradle, git, or any build/test command. DO NOT switch branches. Edit source/tests only.
- Cite real line numbers; open files before editing. When done, return a concise summary: files changed, tests
  updated, and anything you intentionally skipped (with why).`

const clusters = [
  { slug: 'p2-bcm', label: 'p2:bcm+reporting', prompt: `
Apply these dead-code + error-handling fixes:
- QUAL-BCMP-001 (dead code): delete the no-op _touch() methods and KEEP_*/OBJECTS_KEEP_IMPORT dummy fields, plus
  the now-unused imports/loggers they shielded, in: bcm/service/StrategyRoiService.java (~535),
  bcm/service/QuantitativeBiaService.java (~503), bcm/cycle/service/BcmRiskService.java (~227),
  bcm/cycle/service/BcmCycleService.java (~322), bcm/cycle/runtime/observer/service/ExerciseObserverService.java
  (~641), reporting/service/ExportService.java (~494), bcm/board_pack/.../BoardPackPptxService.java (~402),
  calendar/.../CalendarService.java (~344), controls/.../ControlLibraryService.java (~394),
  compliance/.../CompliancePostureService.java (~235). (Search each for "_touch", "KEEP", "OBJECTS_KEEP_IMPORT".)
- QUAL-BCMP-007 (Low): bcm/cycle/runtime/service/ExerciseRuntimeService.java ~280 — log the exception object
  (rename the caught var from 'ignored' to 'ex' and pass it to log.warn).
- QUAL-BCMP-008 (Low): reporting/service/ExportService.java ~357/401/464 — narrow the over-broad catch(Exception)
  around workbook writing to IOException (let RuntimeExceptions propagate to the GlobalExceptionHandler), or at
  minimum log the exception object. Keep export behaviour otherwise.
- QUAL-BCMP-012 (Low): bcm/binder/service/BcpBinderService.java ~290 — log the swallowed exception object in the
  reflection-based principal probe (keep the null-on-anonymous fallback).` },

  { slug: 'p2-privacy-ndi', label: 'p2:privacy+ndi', prompt: `
- QUAL-CP-003: privacy/service/PrivacyService.java ~163 and ~183 — the two empty catch(Exception ignored) on the
  SDAIA breach path. Add a logger and log.warn("... {}", id, ex) in both; keep the swallow.
- QUAL-ESG-003: ndi/audit/service/NdiAuditPublisher.java ~40/49/60/82 — add a SLF4J logger and log the full
  exception (cause) at WARN in each catch (the Javadoc already claims warn-logging). Keep the swallow.
- QUAL-ESG-007 (dead code): ndi/reports/service/NdiReportService.java ~58 — remove the injected-but-unused
  ObjectMapper field, its constructor parameter and assignment (and the @SuppressWarnings shielding it).` },

  { slug: 'p2-socops', label: 'p2:socops', prompt: `
- QUAL-CP-005 (error-handling part ONLY): socops/service/SocOpsScheduledSweep.java ~391 — change the broad
  catch's log to include the exception object: log.error("[socops sweep] org={} scan failed", orgId, ex). Do NOT
  do the larger generic-sweep-helper refactor (out of scope).` },

  { slug: 'p2-gov-scm', label: 'p2:governance+scm', prompt: `
- QUAL-FA-005: governance/pivot/service/RiskControlFindingPivotService.java ~656 — in safeList/safeOpt, log the
  swallowed RuntimeException at WARN with context (the class already has an Slf4j logger). Keep returning the
  empty fallback.
- QUAL-FA-007: scm/service/ScmService.java ~39 — replace the 'default to 1L on null context' fallback in orgId()
  with TenantContext.requireOrganisationId() (fail-fast, matching the platform convention). Update any ScmService
  test that relied on the 1L default to set TenantContext.setCurrentTenant(...) in setup (and clear in teardown).` },

  { slug: 'p2-auth-wf', label: 'p2:auth+workflow', prompt: `
- QUAL-IE-008: auth/AuthController.java ~94 and ~173 — add a class Logger and log.error("...", userId, ex) in the
  broad catch blocks before returning 500 (keep the fail-closed 500).
- QUAL-IE-005 (dead code): workflow/approval_chains/service/ApprovalChainInstanceService.java — remove the unused
  private Logger field (~line 79) and the two slf4j imports (~24-25) IF they are genuinely unused (grep the file
  for 'log.' first; if used, leave them).
- QUAL-IE-012 (dead code): approvals/service/ApprovalRequestService.java ~171 — delete the empty no-op if-block.` },

  { slug: 'p2-tprm-ccm', label: 'p2:tprm+ccm', prompt: `
- QUAL-TI-005 (dead code): tprm/outsourcing/controller/OutsourcingController.java ~255/267 — delete the
  _silenceUnused and __keepIfNeeded dead methods and the now-unused imports; same pattern in
  cpty/service/CptyService.java ~557. If a currentUserIdOrNull() helper is left unused after deletion, remove it
  too (do NOT wire new behaviour).
- QUAL-TI-009: tprm/automation/TprmAutomationService.java ~60/83/107 and ccm/service/CcmService.java ~197/286 —
  pass the Throwable as the final SLF4J arg so the stacktrace renders: log.warn("...failed for {}", id, ex).` },

  { slug: 'p2-correctness', label: 'p2:compliance+fraud+rrp+auditlc', prompt: `
- QUAL-RC-002 (CORRECTNESS bug): compliance/service/ComplianceService.java ~233 — getStats counts findings by
  status="CRITICAL", but CRITICAL is a SEVERITY value (status is OPEN/CLOSED), so the criticalFindings tile is
  always 0. Add/use a countByOrganisationIdAndSeverity finder on the findings repository and count severity
  "CRITICAL"; keep openFindings counting by status. UPDATE the ComplianceService test to assert the corrected
  critical count.
- QUAL-FA-003 (dead code): fraud/service/FraudService.java ~333 — the triageCount tile counts status="TRIAGE"
  which the state machine never sets. Remove the triageCount tile (and its query) — cleaner than repurposing.
  Update the FraudService stats test if it asserts triageCount.
- QUAL-FA-004 (dead code): rrp/service/RrpService.java ~144 — the SUBMITTED hint branch is unreachable after the
  allow-list check. Hoist the SUBMITTED special-case ahead of the generic guard so it is reachable.
- QUAL-FA-009: audit_lc/service/AuditLifecycleService.java ~104/178/250 — IllegalTransitionException is built
  with an empty allowed-set (discarding the informative message). Pass the real allowed-from set (mirror the
  Map<String,Set<String>> pattern used elsewhere) so the 409 message lists the valid transitions.` },

  { slug: 'p2-risk', label: 'p2:risk', prompt: `
- QUAL-RC-008 (dead code/dup): collapse the identical-bodied nullSafe()/safe() helper pair to ONE per class in
  risk/bowtie/service/BowTieAnalysisService.java (~536) and risk/service/RiskService.java (~309) — keep one,
  delete the alias, update internal call sites in that file.
- QUAL-RC-009: risk/kri/service/KriMeasurementService.java ~175 — evaluateAll always logs
  KRI_EVALUATION_SKIPPED per KRI per tick and counts skips as 'dispatched'. Rename the counter to 'skipped'
  (don't count skips as dispatched) and downgrade the per-KRI log to debug (or aggregate one summary line).` },
]

phase('P2 quality fixes')
const SUMMARY = { type:'object', additionalProperties:false,
  required:['slug','filesChanged','testsUpdated','notes'],
  properties:{ slug:{type:'string'}, filesChanged:{type:'array',items:{type:'string'}},
    testsUpdated:{type:'array',items:{type:'string'}}, notes:{type:'string'} } }

const results = (await parallel(clusters.map(c => () =>
  agent(`${c.prompt}\n${RULES}\nReturn the summary object. slug="${c.slug}".`,
    { label: c.label, phase: 'P2 quality fixes', schema: SUMMARY })))).filter(Boolean)

log(`P2 quality: ${results.length} clusters done; ${results.reduce((a,r)=>a+(r.filesChanged?.length||0),0)} files changed`)
return { clusters: results.length, results }
