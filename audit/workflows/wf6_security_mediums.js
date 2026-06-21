export const meta = {
  name: 'tss-grc-security-mediums',
  description: 'Fix the code-level MEDIUM security findings: residual BFLA, mass-assignment, SoD, pagination caps, cross-tenant poisoning, outbound SSRF, config leaks, open redirect',
  phases: [{ title: 'Security Medium fixes' }],
}

const RULES = `
You are a senior application-security engineer on branch fix/audit-p1-hardening (working dir = repo root).
For each assigned finding ID: read its full entry in audit/all_findings.json (file/lines/rootCause/resolution/
patchBefore/patchAfter), open the cited code, and apply the prescribed fix. Mirror the patterns ALREADY landed in
this repo by the Critical/High remediation. HARD RULES:

- BFLA / missing-authorization fix: replace @PreAuthorize("isAuthenticated()") (or a missing annotation on a write
  endpoint) with @PreAuthorize("hasPermission('MODULE','ACTION')") using the correct MODULE for the controller's
  domain (RISK, CYBER, COMPLIANCE, BCM, TPRM, EVIDENCE, NDI, PRIVACY, POLICY, SCM, PLATFORM_ADMIN, CONTROL_LIB,
  BOARD_PACK, COMMITTEE, AUDIT_LOG, WORKFLOW, AML...) and ACTION (READ for GET, WRITE for POST/PUT/PATCH, DELETE for
  DELETE, APPROVE for approval/sign-off transitions). Restrict the platform-wide audit log + generic persistence +
  file-upload + control-library to hasRole('PLATFORM_ADMIN') or the proper admin permission. Read endpoints stay
  readable by reader roles. Mirror a sibling well-guarded controller in the same module for exact names. NEVER
  weaken or remove guards from genuinely public endpoints (auth login/refresh/logout, the public intake/status
  tracker, inbound webhooks) — leave those permitAll but see the config rules below.

- Mass-assignment fix (raw JPA @Entity bound as @RequestBody): do NOT let clients set server-controlled fields
  (id, organisationId, status/workflowStatus, createdBy/At, approvedBy, score/derived values). Prefer: stamp them
  server-side AFTER binding and overwrite any client value (entity.setOrganisationId(TenantContext
  .requireOrganisationId()); entity.setId(null) on create; force initial status). If the module already has a
  request DTO, bind that instead. Keep the change minimal and behaviour-preserving for legitimate fields.

- IDOR / cross-tenant fix: load single rows via repo.findByIdAndOrganisationId(id, TenantContext
  .requireOrganisationId()) -> EntityNotFoundException (404) on miss. For cross-tenant soft-FK references
  (SEC-NDI-004) validate the referenced row belongs to the caller's org before persisting. For platform-global
  tables written per-tenant (SEC-FIN-003 sanctions), require platform-admin or scope the write to the tenant.

- Segregation-of-duties: derive the acting identity from the authenticated principal (GrcUserDetails.getUserId()),
  never a request-body field; enforce verifier != owner / approver != submitter where the finding requires it.

- Pagination cap: clamp a client-supplied page size to a sane maximum (use the module's existing cap if any; else
  Math.min(requested, 200)) so an unbounded size cannot exhaust memory.

- Outbound SSRF / injection (integrations): never interpolate unvalidated client input into an outbound URL path or
  query; validate the asset/host/plugin id belongs to the caller's tenant (scoped repo lookup) before the call, and
  URL-encode path variables. For the Splunk webhook HMAC (SEC-INT-005) remove the insecure body-mismatch fallback so
  a failed HMAC always rejects.

- Config (SEC-CFG-*): GlobalExceptionHandler must not return the exception class name / root-cause message / stack
  to clients (return a generic message + a correlation id; log the detail server-side). For permitAll portal
  endpoints, keep them reachable but ensure they carry their own token/ownership check in the service. Preserve the
  TenantWriteGuard's protection; only tighten it.

- Open redirect (SEC-FE-003 legacy login.html): only honour same-origin/relative redirect targets; reject absolute
  or protocol-relative (//host) URLs — fall back to the default landing.

- Password policy (SEC-AUTH-002): if a StrongPassword validator already exists in the repo (added by earlier
  remediation), apply that annotation to the UserRequest password field; otherwise enforce min length 12 +
  complexity in the DTO. Do not duplicate an existing validator.

- UPDATE existing tests broken by the change: MockMvc/controller tests need an authenticated user WITH the new role
  (@WithMockUser(roles=...) or authorities); service tests that now require tenant scope must set
  TenantContext.setCurrentTenant(...) in setup and clear in teardown (mirror CyberServiceTest). If a test asserted
  the OLD insecure behaviour (e.g. client could set status/org, or an unguarded endpoint returned 200 for a plain
  user), rewrite it to assert the NEW secure behaviour — do not delete coverage.

- DO NOT run mvn/gradle/git or switch branches. Edit source/tests only. Cite real line numbers. Preserve all
  legitimate same-tenant behaviour. If a finding cannot be safely fixed without a schema/seed change, apply the
  code-side part and clearly note what remains in your summary.
After finishing, return the summary object.`

const SUMMARY = { type:'object', additionalProperties:false,
  required:['slug','findingsFixed','filesChanged','testsUpdated','notes'],
  properties:{ slug:{type:'string'}, findingsFixed:{type:'array',items:{type:'string'}},
    filesChanged:{type:'array',items:{type:'string'}}, testsUpdated:{type:'array',items:{type:'string'}},
    skipped:{type:'array',items:{type:'string'}}, notes:{type:'string'} } }

// Module clusters (disjoint files). Each agent reads each finding itself.
const clusters = [
  { slug:'sm-bcm',         label:'med:bcm',          ids:['SEC-BCM-002','SEC-BCM-003'] },
  { slug:'sm-cmp-policy',  label:'med:controlmap+policy', ids:['SEC-CMP-002','SEC-CMP-004'] },
  { slug:'sm-esg',         label:'med:sustainability', ids:['SEC-ESG-002','SEC-ESG-003'] },
  { slug:'sm-evidence',    label:'med:evidence+boardpack', ids:['SEC-EVD-002','SEC-EVD-003','SEC-EVD-004'] },
  { slug:'sm-iam-committee', label:'med:workflow+committee', ids:['SEC-IAM-004','SEC-IAM-005'] },
  { slug:'sm-integrations',label:'med:integrations-ssrf', ids:['SEC-INT-003','SEC-INT-004','SEC-INT-005'] },
  { slug:'sm-ndi',         label:'med:ndi',          ids:['SEC-NDI-003','SEC-NDI-004'] },
  { slug:'sm-platform',    label:'med:platform',     ids:['SEC-PLAT-003','SEC-PLAT-004','SEC-PLAT-005','SEC-PLAT-006','SEC-PLAT-007'] },
  { slug:'sm-privacy',     label:'med:privacy',      ids:['SEC-PRIV-003'] },
  { slug:'sm-risk',        label:'med:risk',         ids:['SEC-RISK-002','SEC-RISK-003','SEC-RISK-004'] },
  { slug:'sm-fin',         label:'med:aml+scm',      ids:['SEC-FIN-003','SEC-FIN-004'] },
  { slug:'sm-tprm',        label:'med:tprm',         ids:['SEC-TPRM-003'] },
  { slug:'sm-config',      label:'med:config',       ids:['SEC-CFG-001','SEC-CFG-006','SEC-CFG-009'] },
  { slug:'sm-auth-redirect', label:'med:authpolicy+redirect', ids:['SEC-AUTH-002','SEC-FE-003'] },
]

phase('Security Medium fixes')
const results = (await parallel(clusters.map(c => () =>
  agent(`Fix these audit findings: ${c.ids.join(', ')}. Read each in audit/all_findings.json, then apply the fix.\n${RULES}\nReturn the summary; slug="${c.slug}".`,
    { label: c.label, phase: 'Security Medium fixes', schema: SUMMARY })))).filter(Boolean)

const fixed = results.flatMap(r => r.findingsFixed || [])
const skipped = results.flatMap(r => r.skipped || [])
log(`Security Mediums: ${results.length} clusters, ${fixed.length} fixed, ${skipped.length} skipped, ${results.reduce((a,r)=>a+(r.filesChanged?.length||0),0)} files`)
return { clusters: results.length, fixed, skipped, results }
