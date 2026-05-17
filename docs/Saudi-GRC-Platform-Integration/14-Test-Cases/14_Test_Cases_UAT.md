# 14 — UAT Test Cases

20 UAT scenarios per prompt §19. Each row maps to the user story
in `11_User_Stories.md` and the Mockito test in
`11_Acceptance_Criteria.md`.

| ID | Scenario | Preconditions | Steps | Expected | Role | Module |
|---|---|---|---|---|---|---|
| UAT-01 | BIA workflow happy path | Critical service exists | Open /bcm → BIA → Create → Fill RTO/RPO → Submit → Approve | BIA in APPROVED; evidence linked | BCM Manager | BCM |
| UAT-02 | BCP workflow | BIA approved | Open /bcm → Plans → Create → Submit → Approve → Publish | BCP PUBLISHED + sig audit row | BCM Manager | BCM |
| UAT-03 | Supplier risk workflow | Supplier exists | Open /scm → Supplier → Run Risk Assessment | Risk score recomputed; report exportable | Supplier Manager | SCM |
| UAT-04 | Supplier incident escalation | Supplier with active SLA | Trigger SLA breach via API | AR-14 fires; risk_score bumps | Supplier Manager | SCM |
| UAT-05 | ERM risk assessment | Risk OPEN | Open /risk → assess L=3, I=4 | residualScore=12 on parent risk | Risk Owner | Risk |
| UAT-06 | Risk acceptance | Risk in TREATMENT | Submit acceptance memo → committee approval (with sig) | Risk → ACCEPTED; audit row INFO | Risk Committee | Risk |
| UAT-07 | NCA control assessment | ECC v2 imported | Open /cyber → controls → assess control C-IAM-014 | Assessment SUCCESS; mapping coverage reflects on /compliance/posture | NCA Owner | Cyber |
| UAT-08 | NCA evidence expiry | Evidence flagged framework=NCA + expiry today | wait for nightly sweep | AR-09 fires; notification to owner | Compliance Officer | Evidence |
| UAT-09 | Cyber incident → PDPL breach | Incident type=CYBER + affects_personal_data=true | Classify incident as such | AR-04 fires; DataBreach opened; 72h timer ticking | DPO | Privacy |
| UAT-10 | PIA workflow | Processing activity HIGH risk | Open /privacy → DPIA → Create | DPIA required before activity goes live | DPO | Privacy |
| UAT-11 | Data subject request | DSR submitted | Open /privacy → DSR → triage → respond within 30d | SLA respected; AR-10 escalation if breached | Privacy Manager | Privacy |
| UAT-12 | Cross-border transfer request | New transfer requested | Submit form | AR-19 fires → assessment auto-created | DPO | Privacy |
| UAT-13 | Personal data breach | Detected breach | Record breach | 72h timer + AR-04 escalation to DPO | DPO | Privacy |
| UAT-14 | Digital signature approval | Policy DRAFT → APPROVED | Click Approve → DocuSign-style flow | Sig transaction created; audit row | Legal Reviewer | Policy |
| UAT-15 | Board report generation | Quarter end | Run Board Risk Report → sign → distribute | Report APPROVED + signed PDF | Board Secretary | Reporting |
| UAT-16 | Regulatory evidence package | NCA cycle close | Generate ECC package → submit | Package READY → SUBMITTED; manifest checksum recorded | Compliance Officer | NDI |
| UAT-17 | Critical service → DRP workflow | Service classified CRITICAL | Edit service | AR-02 fires → BIA + DRP + NCA assessment auto-created | BCM Manager | BCM |
| UAT-18 | Cloud service → CCC | New cloud service linked to critical service | Link | AR-05 fires → CCC + DRP + supplier resilience review | Cyber Manager | Cyber |
| UAT-19 | Vendor processes personal data → processor DD | Vendor flagged | Save | AR-03 fires → ProcessorAssessment opened | Supplier Manager | TPRM |
| UAT-20 | CAPA closure validation | CAPA EVIDENCE_SUBMITTED | Internal Auditor reviews → Close | CAPA CLOSED; gap status check | Internal Auditor | CAPA |

## Smoke test (after every release)

| Smoke | What |
|---|---|
| Login + dashboard load < 1.5s | `/login` → `/dashboard` |
| Risk register page load < 1.5s | `/risk` |
| Compliance Posture heatmap load < 2s | `/compliance/posture` |
| Audit log filter by action=TRANSITION returns rows | `/admin/audit-log` |
| Authority Documents catalog shows 19 placeholders | `/regulatory/library` |
| Sidebar hides unlicensed entries | manipulate `enabled_modules` |

## Regression suite per release

- All `*ServiceTest` Mockito classes pass (currently ~250 tests).
- `npx tsc --noEmit` clean.
- Manual QA against the 14 `docs/*/QA_CHECKLIST.md` files.
- Smoke UAT-01 through UAT-20.

## Test data conventions

- Demo tenant: `organisation_id = 1`.
- Demo users: `admin`, `riskmanager`, `auditor` (passwords in
  `CLAUDE.md`).
- Test entities use deterministic codes (RISK-CYB-0042,
  C-IAM-014, NCA-ECC-V2) so manual QA scripts reference them
  reproducibly.

---

**See also:** Mockito test classes; QA checklists under
`docs/*/QA_CHECKLIST.md`.
