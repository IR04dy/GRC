export const meta = {
  name: 'tss-grc-quality-perf-audit',
  description: 'Phase 2+3 (static): code-quality and static-performance review across all modules + frontend, writing structured findings to disk',
  phases: [{ title: 'Quality & perf finders' }],
}

function spec(phase) {
  return `
You are producing REAL, verified ${phase} findings for a professional audit report.
OUTPUT CONTRACT — write a single UTF-8 JSON file (no markdown fences, no prose) to the exact OUTFILE path given.
File content MUST be: { "findings": [ <finding>, ... ] }
Each <finding> MUST have EXACTLY these keys (strings unless noted):
  "id" (use the PREFIX, e.g. "<PREFIX>-001"), "title", "phase" (="${phase}"), "category",
  "severity" ("Critical"|"High"|"Medium"|"Low"|"Info"), "cvss" (risk rationale string),
  "file" (repo-relative), "lines" (real range), "locations" (array of "file:lines", may be []),
  "description", "rootCause", "evidence" (real snippet/metric), "impact", "reproduction",
  "resolution", "patchBefore", "patchAfter", "effort" ("S"|"M"|"L"), "references" (array of strings).
HARD RULES: open and READ real files; cite REAL line numbers; never fabricate. Quantify (counts, complexity
numbers, LOC). Prefer high-confidence findings; mark uncertain ones Low/Info. Repo root = current working dir.
After writing the file, return ONLY the summary object from your schema.`
}

const SUMMARY_SCHEMA = {
  type: 'object', additionalProperties: false,
  required: ['slug', 'outfile', 'findingCount', 'critical', 'high', 'medium', 'low', 'info', 'notes'],
  properties: {
    slug: { type: 'string' }, outfile: { type: 'string' }, findingCount: { type: 'integer' },
    critical: { type: 'integer' }, high: { type: 'integer' }, medium: { type: 'integer' },
    low: { type: 'integer' }, info: { type: 'integer' }, notes: { type: 'string' },
  },
}

const MOD = 'tss-grc-backend/src/main/java/com/tss/grc/module'
const FIND = 'audit/findings'

const QUAL_CHECK = `
CODE-QUALITY REVIEW for assigned package(s):
- Cyclomatic complexity / God classes / long methods (flag methods > ~40 LOC or clearly high-branching; name them with line refs).
- Code duplication / copy-paste blocks (cross-check with audit/raw/jscpd/jscpd-report.json if useful).
- Dead code, unused imports/fields, unreachable branches, commented-out code.
- Error handling: swallowed exceptions (empty catch, catch-and-continue), over-broad catch(Exception),
  exceptions logged at wrong level or losing the cause.
- Naming clarity; missing Javadoc on public APIs; magic numbers.
- SOLID / layering violations (controller doing business logic, service reaching into other modules' repos).
Quantify: number of classes/methods affected.`

const qualGroups = [
  { slug: 'qual-bcm-platform',  prefix: 'QUAL-BCMP', paths: [`${MOD}/bcm`, `${MOD}/platform`, `${MOD}/dashboard`, `${MOD}/reporting`] },
  { slug: 'qual-esg-ndi-ai',    prefix: 'QUAL-ESG',  paths: [`${MOD}/sust`, `${MOD}/ndi`, `${MOD}/ai`, `${MOD}/dataclass`, `${MOD}/data_officer`] },
  { slug: 'qual-risk-comp',     prefix: 'QUAL-RC',   paths: [`${MOD}/risk`, `${MOD}/compliance`, `${MOD}/regreporting`, `${MOD}/reginspect`, `${MOD}/policy`, `${MOD}/controlmap`] },
  { slug: 'qual-cyber-priv',    prefix: 'QUAL-CP',   paths: [`${MOD}/cyber`, `${MOD}/socops`, `${MOD}/privacy`, `${MOD}/consumer`, `${MOD}/ethics`] },
  { slug: 'qual-tprm-integr',   prefix: 'QUAL-TI',   paths: [`${MOD}/tprm`, `${MOD}/outsourcing`, `${MOD}/cpty`, `${MOD}/openbanking`, `${MOD}/integrations`, `${MOD}/ccm`] },
  { slug: 'qual-iam-evidence',  prefix: 'QUAL-IE',   paths: [`${MOD}/admin`, `${MOD}/iam`, `${MOD}/auth`, `${MOD}/approvals`, `${MOD}/signature`, `${MOD}/workflow`, `${MOD}/evidence`, `${MOD}/attachment`] },
  { slug: 'qual-fincrime-aud',  prefix: 'QUAL-FA',   paths: [`${MOD}/scm`, `${MOD}/sharia`, `${MOD}/aml`, `${MOD}/rrp`, `${MOD}/fraud`, `${MOD}/incident`, `${MOD}/opres`, `${MOD}/audit_lc`, `${MOD}/governance`] },
]

const PERF_AGENTS = [
  { slug: 'perf-nplus1', prefix: 'PERF-NQ', phase: 'Performance', label: 'perf:n+1',
    prompt: `Hunt N+1 queries and lazy-loading problems across the backend services and repositories
(${MOD}/*/service, ${MOD}/*/repository). Look for: loops that issue per-row queries, missing JOIN FETCH on
collections rendered in lists, @OneToMany/@ManyToOne with EAGER fetch, accessing LAZY collections inside
mapping loops, repository methods returning entities (not projections) for list views. Cite the service method
and the repository call. Use the running app behaviour as corroboration where you can reason about it.` },
  { slug: 'perf-indexes', prefix: 'PERF-IX', phase: 'Performance', label: 'perf:indexes',
    prompt: `Review database indexing. Read entity @Table/@Index annotations across ${MOD} and the DDL under
db/sqlserver/ and tss-grc-backend/src/main/resources/db/migration/sqlserver/. The platform is multi-tenant:
EVERY table is queried by organisation_id and most by FK + is_deleted. Flag tables/columns that are frequently
filtered (organisation_id, FKs, status, workflow_status, is_deleted) but lack a supporting index. Quantify how
many tables appear to lack an org/FK index.` },
  { slug: 'perf-pagination', prefix: 'PERF-PG', phase: 'Performance', label: 'perf:pagination',
    prompt: `Find unbounded result sets. Across controllers/services/repositories, flag findAll()/list endpoints
that return ALL rows with no Pageable/limit, especially register list endpoints (e.g. /api/v1/risk/risks).
Confirm by reading the controller + service + repository. Estimate blast radius (number of list endpoints
without pagination). Recommend Pageable + max page size.` },
  { slug: 'perf-pool-io', prefix: 'PERF-IO', phase: 'Performance', label: 'perf:pool-io',
    prompt: `Review connection-pool sizing and blocking I/O. Read Hikari config in application.yml /
application-dev.yml (maximum-pool-size). Note dev=10 / base=20 against SQL Server Express. Review the
integrations module outbound HTTP adapters (ServiceNow/Splunk/CrowdStrike/Tenable) for synchronous blocking
calls inside request threads, missing timeouts, and @Transactional scopes that hold a DB connection across
remote calls. Cross-reference the k6 result at audit/raw/k6-summary.json if present (breakpoint where errors
climb ~ pool exhaustion).` },
  { slug: 'perf-frontend', prefix: 'PERF-FE', phase: 'Performance', label: 'perf:frontend',
    prompt: `Review frontend runtime/load performance. The Lighthouse score for /login is Performance 38
(audit/raw/lighthouse-login.report.json — read its audits: FCP, LCP, TBT, total byte weight, unused JS).
Check tss-grc-ui-v2: is there route-level code splitting (React.lazy/Suspense) for the 300+ pages in App.tsx,
or is everything statically imported into one bundle? Check vite build chunking, recharts import cost, and
whether @tanstack/react-virtual is actually used for big tables. Cite App.tsx import style and vite.config.ts.` },
]

const TRIAGE = [
  { slug: 'qual-jscpd', prefix: 'QUAL-DUP', phase: 'Quality', label: 'triage:jscpd',
    prompt: `Triage the duplication report at audit/raw/jscpd/jscpd-report.json (overall 5.41%, 1666 clones).
Identify the largest / most-repeated clone clusters (by lines and by number of instances) and the files most
involved. Produce findings for the worst offenders with concrete dedupe recommendations (extract shared
component/util/base class). Open a couple of the cited files to confirm.` },
]

phase('Quality & perf finders')

const qa = qualGroups.map(g => () => agent(
  `You are a staff-level code reviewer auditing the TSS GRC Spring Boot backend.
Audit these package(s): ${g.paths.join(', ')}.
${QUAL_CHECK}
${spec('Quality')}
PREFIX = "${g.prefix}"
OUTFILE = "${FIND}/${g.slug}.json"
Write the JSON file, then return the summary.`,
  { label: g.slug, phase: 'Quality & perf finders', schema: SUMMARY_SCHEMA }))

const pa = PERF_AGENTS.map(p => () => agent(
  `You are a senior performance engineer auditing the TSS GRC platform (Spring Boot + SQL Server + React/Vite).
${p.prompt}
${spec(p.phase)}
PREFIX = "${p.prefix}"
OUTFILE = "${FIND}/${p.slug}.json"
Write the JSON file, then return the summary.`,
  { label: p.label, phase: 'Quality & perf finders', schema: SUMMARY_SCHEMA }))

const ta = TRIAGE.map(t => () => agent(
  `You are a staff-level code reviewer. ${t.prompt}
${spec(t.phase)}
PREFIX = "${t.prefix}"
OUTFILE = "${FIND}/${t.slug}.json"
Write the JSON file, then return the summary.`,
  { label: t.label, phase: 'Quality & perf finders', schema: SUMMARY_SCHEMA }))

const summaries = (await parallel([...qa, ...pa, ...ta])).filter(Boolean)
const total = summaries.reduce((a, s) => a + (s.findingCount || 0), 0)
log(`Quality+perf finders done: ${summaries.length} agents, ${total} findings`)
return { agents: summaries.length, totalFindings: total, summaries }
