export const meta = {
  name: 'tss-grc-security-highs',
  description: 'Fix the remaining HIGH security findings: cross-tenant IDOR org-scoping + function-level authz (hasPermission) + XSS',
  phases: [{ title: 'Security High fixes' }],
}

const RULES = `
You are a senior application-security engineer on branch fix/audit-p1-hardening (working dir = repo root).
For each assigned finding ID: read its full entry in audit/all_findings.json (file/lines/rootCause/resolution),
open the cited code, and apply the fix. HARD RULES:
- BFLA fix: replace @PreAuthorize("isAuthenticated()") (or a missing annotation on a write endpoint) with
  @PreAuthorize("hasPermission('MODULE','ACTION')") using the correct MODULE for the controller's domain
  (RISK, CYBER, COMPLIANCE, BCM, TPRM, EVIDENCE, NDI, PDPL/PRIVACY, POLICY, SCM, PLATFORM_ADMIN, AUTOMATION,
  LICENSING, SIGNATURE, APPROVAL, AUDIT_LOG, BOARD_PACK, CONTROL_LIB, REGULATORY...) and ACTION
  (READ for GET, WRITE for POST/PUT/PATCH, DELETE for DELETE, APPROVE/TRIAGE for those transitions). Mirror a
  sibling well-guarded controller in the same module (e.g. Sprint-11 climate /api/v1/climate/** and ai/models
  use hasAnyRole/hasPermission) for the exact role/permission names. Keep READ endpoints readable by the normal
  reader roles; restrict admin/config ops (licensing, automation, audit-ledger) tightly.
- IDOR fix: load single rows scoped to the tenant — repo.findByIdAndOrganisationId(id, TenantContext
  .requireOrganisationId()) (or the module's existing scoped finder), throwing EntityNotFoundException (-> 404)
  on a miss. Add the scoped finder to the repository if missing. Mirror the CyberService fix already in this repo.
- Segregation-of-duties / actor-from-body fixes: derive the acting identity from the authenticated principal
  (GrcUserDetails.getUserId()), never a request-body field; require the principal match the required actor/role.
- XSS fix: sanitise or escape server/user data before innerHTML / dangerouslySetInnerHTML; prefer textContent.
- UPDATE existing tests broken by the change: MockMvc/controller tests need an authenticated user WITH the new
  role (or @WithMockUser(roles=...)); service tests that now require tenant scope must set
  TenantContext.setCurrentTenant(...) in setup and clear in teardown (mirror CyberServiceTest).
- DO NOT run mvn/gradle/git or switch branches. Edit source/tests only. Cite real line numbers. Preserve all
  legitimate same-tenant behaviour.
After finishing, return the summary object: which findings fixed, files changed, tests updated, anything skipped.`

const SUMMARY = { type:'object', additionalProperties:false,
  required:['slug','findingsFixed','filesChanged','testsUpdated','notes'],
  properties:{ slug:{type:'string'}, findingsFixed:{type:'array',items:{type:'string'}},
    filesChanged:{type:'array',items:{type:'string'}}, testsUpdated:{type:'array',items:{type:'string'}},
    notes:{type:'string'} } }

// Module clusters (disjoint files). Each agent reads the finding details itself.
const clusters = [
  { slug:'sh-platform',  label:'highs:platform',    ids:['SEC-PLAT-001','SEC-PLAT-002'] },
  { slug:'sh-ndi',       label:'highs:ndi',         ids:['SEC-NDI-001','SEC-NDI-002'] },
  { slug:'sh-privacy',   label:'highs:privacy',     ids:['SEC-PRIV-001','SEC-PRIV-002'] },
  { slug:'sh-compliance',label:'highs:compliance',  ids:['SEC-CMP-003','SEC-CMP-001'] },
  { slug:'sh-cyber',     label:'highs:cyber',       ids:['SEC-CYB-003'] },
  { slug:'sh-esg',       label:'highs:sustainability', ids:['SEC-ESG-001'] },
  { slug:'sh-evidence',  label:'highs:evidence',    ids:['SEC-EVD-001'] },
  { slug:'sh-scm',       label:'highs:scm',         ids:['SEC-FIN-002'] },
  { slug:'sh-iam-sig',   label:'highs:approvals+sig', ids:['SEC-IAM-002','SEC-IAM-003'] },
  { slug:'sh-integr',    label:'highs:integrations',ids:['SEC-INT-002'] },
  { slug:'sh-risk',      label:'highs:risk',        ids:['SEC-RISK-001'] },
  { slug:'sh-tprm',      label:'highs:tprm',        ids:['SEC-TPRM-001','SEC-TPRM-002'] },
  { slug:'sh-bcm',       label:'highs:bcm',         ids:['SEC-BCM-001'] },
  { slug:'sh-xss',       label:'highs:xss',         ids:['SEC-FE-001'] },
]

phase('Security High fixes')
const results = (await parallel(clusters.map(c => () =>
  agent(`Fix these audit findings: ${c.ids.join(', ')}. Read each in audit/all_findings.json, then apply the fix.\n${RULES}\nReturn the summary; slug="${c.slug}".`,
    { label: c.label, phase: 'Security High fixes', schema: SUMMARY })))).filter(Boolean)

const fixed = results.flatMap(r => r.findingsFixed || [])
log(`Security Highs: ${results.length} clusters, ${fixed.length} findings fixed, ${results.reduce((a,r)=>a+(r.filesChanged?.length||0),0)} files`)
return { clusters: results.length, fixed, results }
