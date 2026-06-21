export const meta = {
  name: 'tss-grc-security-audit',
  description: 'Phase 1 deep security audit: per-module review + crown-jewels + scanner triage, writing structured findings to disk',
  phases: [
    { title: 'Security finders' },
  ],
}

// ---- Shared finding-file spec (every agent writes one JSON file) ----
const FINDING_SPEC = `
You are producing REAL, verified security findings for a professional audit report.

OUTPUT CONTRACT — write a single UTF-8 JSON file (no markdown fences, no prose) to the
exact path given as OUTFILE below. The file content MUST be a JSON object:
  { "findings": [ <finding>, ... ] }
Each <finding> MUST have EXACTLY these keys (all string values unless noted):
  "id"           : unique id using the PREFIX given to you, e.g. "<PREFIX>-001"
  "title"        : short specific title
  "phase"        : "Security"
  "category"     : OWASP/CWE category, e.g. "Broken Access Control (A01)", "SQL Injection (A03)"
  "severity"     : one of "Critical","High","Medium","Low","Info"
  "cvss"         : short CVSS-style vector or risk rationale, e.g. "CVSS 8.1 (AV:N/AC:L/PR:L/...) — cross-tenant read"
  "file"         : primary repo-relative path, e.g. "tss-grc-backend/src/main/java/.../FooController.java"
  "lines"        : real line range you verified, e.g. "42-58"
  "locations"    : array of extra "file:lines" strings (may be empty [])
  "description"  : detailed technical description
  "rootCause"    : the underlying cause
  "evidence"     : a SHORT real code snippet (copied from the file) or tool output proving it
  "impact"       : concrete real-world impact
  "reproduction" : step-by-step repro (curl/UI steps) where applicable
  "resolution"   : explanation of the fix
  "patchBefore"  : the vulnerable code (before)
  "patchAfter"   : the fixed code (after)
  "effort"       : one of "S","M","L"
  "references"   : array of strings (CWE/OWASP/CVE links or ids)

HARD RULES:
- Open and READ the actual files. Cite REAL line numbers. NEVER fabricate paths, lines, or code.
- Verify exploitability before rating High/Critical. If a control mitigates it elsewhere, lower the rating and say so.
- Prefer fewer, high-confidence findings over speculation. Mark genuinely uncertain items "Info" or "Low".
- Multi-tenancy (org scoping via TenantContext/organisationId) is the platform's #1 risk — scrutinise it.
- Use repo-relative paths (the repo root is the current working directory).
- After writing the file, return ONLY the summary object described by your schema.
`

const SUMMARY_SCHEMA = {
  type: 'object',
  additionalProperties: false,
  required: ['slug', 'outfile', 'findingCount', 'critical', 'high', 'medium', 'low', 'info', 'notes'],
  properties: {
    slug: { type: 'string' },
    outfile: { type: 'string' },
    findingCount: { type: 'integer' },
    critical: { type: 'integer' },
    high: { type: 'integer' },
    medium: { type: 'integer' },
    low: { type: 'integer' },
    info: { type: 'integer' },
    notes: { type: 'string' },
  },
}

const MOD = 'tss-grc-backend/src/main/java/com/tss/grc/module'
const SEC_CHECKLIST = `
DEEP SECURITY REVIEW CHECKLIST for the assigned package(s):
1. AuthZ: enumerate every @RestController endpoint; confirm each has a correct @PreAuthorize
   (right MODULE:ACTION). Flag endpoints with NO authorization or too-broad scope (BOLA/BFLA, A01).
2. Multi-tenancy: confirm every repository finder AND every service read/write is scoped by
   organisationId / TenantContext. Flag any query returning or mutating data without org scoping
   (cross-tenant exposure / IDOR). THIS IS THE #1 RISK.
3. Injection: flag nativeQuery=true, createQuery/createNativeQuery with concatenation, dynamic
   JPQL/SQL/Sort, or any user input reaching SQL/OS/LDAP (A03).
4. Input validation: DTOs missing Jakarta @Valid/constraints; unvalidated path/query params;
   mass-assignment; unbounded page sizes.
5. Sensitive data exposure: secrets/PII in logs, responses, exceptions; missing field-level authz.
6. Business-logic & race conditions: workflow state transitions, check-then-act, missing
   optimistic locking on financial/risk-scoring mutations.
7. SSRF (outbound HTTP with user-controlled URL), insecure deserialization, file upload handling.
`

// Balanced module groups (by LOC) — together they cover every backend package.
const groups = [
  { slug: 'sec-bcm',        prefix: 'SEC-BCM',   paths: [`${MOD}/bcm`, `${MOD}/bcm_lc`], note: 'largest module; prioritise controllers/services/repositories + org scoping' },
  { slug: 'sec-platform',   prefix: 'SEC-PLAT',  paths: [`${MOD}/platform`, `${MOD}/dashboard`, `${MOD}/datagov`, `${MOD}/reporting`] },
  { slug: 'sec-esg-ai',     prefix: 'SEC-ESG',   paths: [`${MOD}/sust`, `${MOD}/ai`] },
  { slug: 'sec-ndi-data',   prefix: 'SEC-NDI',   paths: [`${MOD}/ndi`, `${MOD}/dataclass`, `${MOD}/data_officer`] },
  { slug: 'sec-integr',     prefix: 'SEC-INT',   paths: [`${MOD}/integrations`, `${MOD}/ccm`], note: 'integrations = OUTBOUND HTTP adapters; scrutinise SSRF, secret handling, webhook auth' },
  { slug: 'sec-risk',       prefix: 'SEC-RISK',  paths: [`${MOD}/risk`, `${MOD}/risk_lc`, `${MOD}/capa`] },
  { slug: 'sec-privacy',    prefix: 'SEC-PRIV',  paths: [`${MOD}/privacy`, `${MOD}/privacy_lc`, `${MOD}/consumer`, `${MOD}/ethics`] },
  { slug: 'sec-cyber',      prefix: 'SEC-CYB',   paths: [`${MOD}/cyber`, `${MOD}/cyber_lc`, `${MOD}/socops`] },
  { slug: 'sec-tprm',       prefix: 'SEC-TPRM',  paths: [`${MOD}/tprm`, `${MOD}/outsourcing`, `${MOD}/cpty`, `${MOD}/openbanking`] },
  { slug: 'sec-compliance', prefix: 'SEC-CMP',   paths: [`${MOD}/compliance`, `${MOD}/regreporting`, `${MOD}/reginspect`, `${MOD}/regchange`, `${MOD}/nca_ecc`, `${MOD}/policy`, `${MOD}/controlmap`] },
  { slug: 'sec-iam-admin',  prefix: 'SEC-IAM',   paths: [`${MOD}/admin`, `${MOD}/iam`, `${MOD}/workflow`, `${MOD}/workflow_submission`, `${MOD}/approvals`, `${MOD}/signature`], note: 'IAM/admin/approvals = high authz value' },
  { slug: 'sec-evidence',   prefix: 'SEC-EVD',   paths: [`${MOD}/evidence`, `${MOD}/attachment`, `${MOD}/knowledge`, `${MOD}/board_pack`, `${MOD}/audit_lc`, `${MOD}/audit_committee`, `${MOD}/audit_eng`, `${MOD}/governance`], note: 'evidence/attachment = file upload + SoD' },
  { slug: 'sec-fincrime',   prefix: 'SEC-FIN',   paths: [`${MOD}/scm`, `${MOD}/sharia`, `${MOD}/aml`, `${MOD}/rrp`, `${MOD}/fraud`, `${MOD}/incident`, `${MOD}/opres`, `${MOD}/change_mgmt`, `${MOD}/intake`] },
]

const FIND_DIR = 'audit/findings'

phase('Security finders')

const moduleAgents = groups.map(g => () => {
  const outfile = `${FIND_DIR}/${g.slug}.json`
  const prompt = `You are a senior application security engineer auditing the TSS GRC Spring Boot backend (Java 17, Spring Security 6, multi-tenant).
Audit these package(s): ${g.paths.join(', ')}.
${g.note ? 'FOCUS: ' + g.note : ''}
${SEC_CHECKLIST}
${FINDING_SPEC}
PREFIX = "${g.prefix}"
OUTFILE = "${outfile}"
Write the JSON file to OUTFILE, then return the summary.`
  return agent(prompt, { label: g.slug, phase: 'Security finders', schema: SUMMARY_SCHEMA })
})

// Crown-jewel / cross-cutting security agents
const crownJewels = [
  {
    slug: 'sec-cj-auth', prefix: 'SEC-AUTH', label: 'cj:auth-jwt',
    prompt: `Audit authentication, JWT, session and token handling. Read:
tss-grc-backend/src/main/java/com/tss/grc/security/ (JwtTokenProvider, JwtAuthenticationFilter, GrcUserDetails, ModulePermissionEvaluator and siblings),
tss-grc-backend/src/main/java/com/tss/grc/module/auth/ (AuthController, refresh-token services).
Check: JWT signing algorithm + secret strength/rotation, access/refresh expiry, refresh-cookie flags
(HttpOnly/Secure/SameSite), token revocation, logout, account lockout, password policy & BCrypt usage,
TenantContext population from claims (and cleanup to avoid thread-local leakage), filter ordering,
timing/enumeration on login, error messages. Trace token from creation to validation.`,
  },
  {
    slug: 'sec-cj-config', prefix: 'SEC-CFG', label: 'cj:web-config',
    prompt: `Audit Spring web security configuration. Read everything under
tss-grc-backend/src/main/java/com/tss/grc/config/ (SecurityConfig, CorsConfig, WebMvcConfig, OpenApiConfig,
DataSourceConfig, CacheConfig, LocaleConfig). Check: CSRF strategy for cookie-based refresh, security
headers/CSP/HSTS/X-Frame-Options, CORS allowedOriginPatterns + allowCredentials combination, which
endpoints are permitAll vs authenticated, actuator exposure (/actuator/*), Swagger exposure, error/exception
handling leaking stack traces, multipart limits. Cross-reference application-dev.yml and application-prod.yml.`,
  },
  {
    slug: 'sec-cj-secrets', prefix: 'SEC-SEC', label: 'cj:secrets-infra',
    prompt: `Audit secrets, configuration and supply-chain. Read:
tss-grc-backend/src/main/resources/application.yml, application-dev.yml, application-prod.yml, *.properties;
the repository-root Python scripts (setup_*.py, seed_*.py, fix_*.py — ~70 files) for hardcoded DB credentials,
BCrypt literals, and string-built SQL; Dockerfile, .dockerignore; .github/ workflows if present.
Check: hardcoded/default secrets (sa/tss.123, JWT secrets), secrets logged or committed, weak crypto,
SQL built by string concatenation in Python, overly-permissive defaults that could ship to prod.
Distinguish INTENTIONAL dev defaults (rate Info/Low with rationale) from genuinely dangerous exposure.`,
  },
  {
    slug: 'sec-cj-frontend', prefix: 'SEC-FE', label: 'cj:frontend',
    prompt: `Audit the frontend for client-side security. Read selectively under tss-grc-ui-v2/src/
(especially lib/ — the API client, auth/token storage, http wrapper — and any components using
dangerouslySetInnerHTML / innerHTML / eval / new Function), plus the legacy static UI tss-grc-ui/ (HTML/JS).
Check: JWT/access-token storage (sessionStorage/localStorage vs memory) and XSS exposure, DOM-based XSS sinks,
unsanitised render of server/user data, open redirects, hardcoded endpoints/secrets, CSP reliance,
clickjacking, sensitive data in console logs, postMessage/websocket (STOMP) trust.`,
  },
]

const cjAgents = crownJewels.map(c => () => {
  const outfile = `${FIND_DIR}/${c.slug}.json`
  return agent(`You are a senior application security engineer. ${c.prompt}
${FINDING_SPEC}
PREFIX = "${c.prefix}"
OUTFILE = "${outfile}"
Write the JSON file to OUTFILE, then return the summary.`, { label: c.label, phase: 'Security finders', schema: SUMMARY_SCHEMA })
})

// Scanner-triage agents — convert raw tool output into verified findings
const triage = [
  {
    slug: 'sec-trivy', prefix: 'SEC-CVE', label: 'triage:trivy',
    prompt: `Triage the Trivy dependency-CVE scan at audit/raw/trivy.json (36 vulnerabilities across Java/JS deps).
Read the file. Produce ONE finding per affected package (group multiple CVEs of the same package), with the
installed version, fixed version, highest CVSS, and the concrete upgrade fix (pom.xml / package.json bump).
Set severity from the CVE severity. Cross-check the package is actually a direct/transitive dependency
(cite pom.xml or package-lock.json). references[] = the CVE ids.`,
  },
  {
    slug: 'sec-npm', prefix: 'SEC-NPM', label: 'triage:npm-audit',
    prompt: `Triage the npm audit JSON at audit/raw/npm-audit.json (5 vulns). Read it. One finding per advisory
with the vulnerable package, path, severity, and the remediation (npm version bump / override). references[] = GHSA/CVE.`,
  },
  {
    slug: 'sec-bandit', prefix: 'SEC-PY', label: 'triage:bandit',
    prompt: `Triage the Bandit scan at audit/raw/bandit.json (Python DB scripts). Read it. DEDUPE by rule id
(e.g. B608 SQL string-build, B106 hardcoded password). Produce one finding per distinct rule class with the
count, the most representative file:line examples (verify by opening 1-2 cited files), severity, and a fix.
Treat dev-only seed scripts pragmatically but still report SQL-injection-prone patterns (B608) and hardcoded creds.`,
  },
  {
    slug: 'sec-gitleaks', prefix: 'SEC-LEAK', label: 'triage:gitleaks',
    prompt: `Triage the gitleaks secret scan at audit/raw/gitleaks.json (21 hits). Read it. Group by secret type/file.
Open cited files to confirm. Classify: genuinely sensitive committed secrets (High) vs intentional dev defaults
like sa/tss.123 or the documented dev JWT secret (Info/Low, with rationale that prod externalises them).
One finding per group.`,
  },
]

const triageAgents = triage.map(t => () => {
  const outfile = `${FIND_DIR}/${t.slug}.json`
  return agent(`You are a senior application security engineer triaging scanner output for an audit report. ${t.prompt}
${FINDING_SPEC}
PREFIX = "${t.prefix}"
OUTFILE = "${outfile}"
Write the JSON file to OUTFILE, then return the summary.`, { label: t.label, phase: 'Security finders', schema: SUMMARY_SCHEMA })
})

const all = [...moduleAgents, ...cjAgents, ...triageAgents]
const summaries = (await parallel(all)).filter(Boolean)

const total = summaries.reduce((a, s) => a + (s.findingCount || 0), 0)
const crit = summaries.reduce((a, s) => a + (s.critical || 0), 0)
const high = summaries.reduce((a, s) => a + (s.high || 0), 0)
log(`Security finders done: ${summaries.length} agents, ${total} findings (${crit} critical, ${high} high)`)

return { agents: summaries.length, totalFindings: total, critical: crit, high, summaries }
