# ADR-017 — The Owner-Blocked Decisions, Settled on Leader-Aligned Saudi Defaults

**Status:** Accepted — closes **F2-35 (O-3)**, **D-2 (O-5)**, **D-9 (O-6)**, **F2-42**, **F2-49**,
**F2-53**, the integration-ownership call, the fraud-persona call, the `*:EXPORT` tier rule, and the two
`board_pack` residuals. Converts **D-1 (O-4)** and **O-7** from *blocked* to *mechanised*.
**Date:** 2026-07-30
**Relates to:** `ADR-010` (permission-only authorisation), `ADR-013` (per-module SKU + entitlement — this
ADR supplies the pricing question ADR-013 deliberately left open), `ADR-014` (committee voting rights),
`ADR-015` (change authority + CAB voting), `ADR-016` (documentation governance)
**Decision owner:** the platform owner. Recorded as the leader-aligned default **on the owner's explicit
instruction** — *"do what[ever] rel[ies] on me based on the best practice and the market leaders and the
suitable to Saudi market"* (2026-07-30) — the same standing arrangement ADR-013 and ADR-016 were recorded
under.

## Context

§1b of `UNIFIED_IMPLEMENTATION_PLAN.md` established the programme's real constraint: **every completed item
is engineer-solo work, every stalled item waits on a non-engineer.** Seven owner actions (O-1…O-7) plus a
tail of per-finding product calls had accumulated with no aggregate view. One of them, **F2-35, gates all of
Wave 2**; another, **D-1, gates S6 and therefore S7, S8 and CE-13**.

The owner has now delegated the decision set with a stated standard: best practice, market-leader
precedent, Saudi-market suitability. This ADR applies that standard once, per decision, and states what
would change its mind. Two items are **not** decided here because no standard can supply them — they are
per-tenant facts — and for those the decision is *how the platform behaves in their absence*, which is a
real decision and was the thing actually blocking code.

⚠ **What this ADR does not do.** It does not set prices. ADR-013 separated *enforcement model* from
*pricing* and that separation holds: below, "paid" means **the module gets an entitlement SKU code and is
not in the free Base**, not a number. Numbers remain the owner's, and nothing in the codebase needs them.

---

## D-1 — Board & committee governance and access governance are **PAID**, not free Base (closes F2-35 / O-3)

**The question as filed:** are committee/board governance and access governance paid SKUs or free Base?
The reviewer's own anchors put ~$0.5M–2.7M/yr on the answer.

**Decision: both are paid SKUs.** Base contains the *platform* and nothing that a buyer would recognise as
a discipline.

**Market-leader precedent — both are separately-sold product categories, not features:**

| Discipline | How the leaders sell it |
|---|---|
| Board & committee governance | **Diligent Boards**, **Nasdaq Boardvantage**, **Computershare Board Portal** are standalone products with their own buyer (company secretary / board chair) and **per-director-seat** pricing. Diligent's entire business is this one module. |
| Access governance | **SailPoint IdentityIQ/IdentityNow**, **Saviynt EIC**, **Oracle IGA** — licensed **per managed identity**, never bundled free. ServiceNow ships no access-certification engine at all; it partners for it. |
| Everything else | **ServiceNow IRM**, **Archer** ("use cases" on a common platform), **MetricStream**, **OneTrust**, **AuditBoard** — all per-domain licensing on a free-with-first-module platform. Exactly ADR-013's shape. |

Giving either away free would be unique in the market, and would give away the two modules that have a
**buyer outside the GRC team** — which is where seat expansion comes from.

**Saudi-market fit — both are regulator-mandated here, which is a stronger sales position than elsewhere:**

- **Board/committee.** The Companies Law requires an Audit Committee; **CMA Corporate Governance
  Regulations** require a Nomination & Remuneration Committee, documented charters, and minuted
  proceedings. **SAMA's Principles of Corporate Governance for Banks** require Audit, Risk, Nomination &
  Remuneration and Compliance committees with documented quorum and minutes. Board minutes and resolutions
  are *regulatory artefacts* in KSA, not internal convenience. A module that produces them is a compliance
  purchase, not a productivity purchase.
- **Access governance.** **NCA ECC-2 domain 2-2 (Identity & Access Management)** requires periodic review
  of identities and access rights, and **SAMA CSF 3.3.5** requires the same. Recertification is therefore
  a *mandated recurring control* in Saudi, not an optional hygiene feature — the single easiest module to
  justify in a budget cycle.

**Consequently:** the free Base is `auth`, tenancy, users/roles, the workflow engine, the evidence
subsystem, the audit ledger, exports, branding and the dashboard shell. Those are platform: every leader
gives the platform away with the first module, and none of them is a discipline a buyer names in an RFP.

**Two corollaries that must not be conflated** (this is the kind of thing that gets merged by accident):

1. **A SKU code is not a permission module.** Board governance gets its own **entitlement** code so it can
   be sold separately (`@RequireModule`, ADR-013). Its **authorisation** stays on the `ADMIN` permission
   family per F2-05 slice 5. Entitlement answers *did you buy it*; permissions answer *may you do it*.
   Creating a `BOARD` permission module to mirror a SKU would fragment governance administration for no
   authorisation gain — see the `board_pack` residual below.
2. **Grandfathering follows §6d:** grant-all-then-tighten, never licence-by-data-presence, which
   false-negatives on read-only and seasonal usage and 403s a paying customer.

**What would change this:** a design partner refusing to pay for committee governance specifically. That is
a discount conversation, not a packaging one.

**Still owed and not invented here:** F2-35's row notes the catalogue exists in **three contradictory
versions**. This ADR ratifies the *answer*; reconciling the three lists into one is mechanical work that
belongs in the module registry (`ADR-009`, F2-01) as the single source, with the two answers above as
fixed points. No SKU list is authored in this document, because a fourth list is the opposite of a
decision.

---

## D-2 — CAB/ECAB committee identity: **fail closed, never defaulted** (mechanises O-4)

**Why this is not decided here.** Which committee constitutes a given tenant's CAB is a **charter fact**
owned by that tenant. Orgs 2 and 3 have no committees at all. Any default I picked would be fabricated
governance — the failure mode this programme has already paid for four times (W2-1a-ii's invented
regulatory deadlines being the worst).

**What was actually blocking code was not the fact — it was the absence of a rule for its absence.** That
is decidable, and is decided:

- Change governance reads `cab_committee_id` / `ecab_committee_id` from **per-tenant configuration with no
  default**.
- Unset ⇒ the CAB-dependent transitions **fail closed** with an actionable ProblemDetail naming the missing
  configuration and the screen that sets it — not a 500, and not a silent pass.
- Setting it is a **required step in tenant onboarding**, blocking activation of the change-governance SKU.
- ITIL 4 supports this: the CAB is *a role*, not necessarily a standing committee, so the platform must not
  assume a committee exists. SAMA CSF and NCA ECC require **documented** change authority — which is
  satisfied by the tenant naming it, not by us guessing.

**Effect:** S6 is unblocked for engineering. The fact stays with the customer, where it belongs, and its
absence is now visible instead of latent.

---

## D-3 — ECAB retrospective ratification SLA: **5 business days**, per-tenant overridable (closes D-2 / O-5)

ITIL 4's emergency-change practice authorises through the ECAB and **retrospectively reviews at the next
scheduled CAB**; SAMA CSF requires post-implementation review of emergency changes; NCA ECC requires
documented approval of changes. For a weekly CAB cadence — the market norm — "next scheduled CAB" is
5 business days, so the default and the practice coincide.

**Decision: 5 business days, overridable per tenant.** Two details that matter more than the number:

- **The clock starts at implementation, not at raise.** An emergency change is implemented first; measuring
  from the raise timestamp would let a slow raise consume the ratification window.
- **Breach escalates to the CISO and lands in the unauthorised-change register** (CE-11's surface) rather
  than merely turning a cell red. An SLA with no consequence is a report, not a control.

---

## D-4 — SKU shelf name: **"Change Governance & CAB"** (closes D-9 / O-6)

Accept §6d's recommendation, which round 2 had left withdrawn-pending. Under the ITIL name "Change
Enablement" the SKU competes with ServiceNow and Jira on CMDB, service catalogue and release pipelines and
loses on brand. What §6c builds is change *governance* — CAB quorum integrity, SoD recusal, e-signed
minutes, evidence-bound conditions, an unauthorised-change register — bought by the CISO/CAE/CCO against
SAMA CSF and NCA ECC change control. Same code, different buyer.

**Decision:** `Change Governance & CAB` is the shelf/SKU name; **`change_mgmt` remains the package name and
"Change Enablement" the internal practice name**, because ITIL alignment is an asset in engineering
documentation and a liability on a price list.

---

## D-5 — F2-42: **expose the server aggregate as JSON and render it**; retire the fabricated page

`/board-pack` renders `buildBoardPack()` from 8 hardcoded arrays while the server builds a real pack from 8
live repositories and already serves it as PDF and PPTX. Two board packs exist and **the PDF is the correct
one**.

**Decision: consolidate onto the server aggregate.** Expose it as JSON, render that, delete the 8 arrays.
Not the other option (retire the page for the export only) because a board portal whose only output is a
download is not competitive with Diligent, and not "rewire the arrays client-side" because that duplicates
server logic and drifts — the PM-C1 façade lesson.

Two things make this cheaper than when F2-42 was filed: F2-05 slice 5 gave `board_pack` a real
authorisation model (`ADMIN:READ`/`ADMIN:WRITE`), so exposing an aggregate endpoint is now safe; and the
sample-data banner already shipped, so there is no honesty regression while it is built.

---

## D-6 — F2-49: **no file may exist that Flyway will not apply** (closes F2-49)

Independently re-confirmed today from the built artifact, not from the plan: `/actuator/info` on the fresh
jar reports `unversionedFiles: ["V46a__cyber_pdpl_bridge_seed_fix.sql",
"V47a__evidence_chain_broken_demo.sql"]`. `V46a` does not match `V<digits>__`, so Flyway silently skips
both — for 280-odd migrations now. **W0-7 found this by itself, which is exactly what it was built for.**

**Decision, as a rule rather than a one-off:**

1. If the content is still required → **renumber to the next free version** and apply through the normal
   ledger.
2. If it is already present in the schema by another route → **delete the file.**
3. Either way, a test asserts **every** file under `db/migration/sqlserver/` matches Flyway's versioned
   pattern, so this class cannot recur silently.

Rationale: a file that *looks* applied and is not is strictly worse than a missing file — it is the same
defect class as the staged jar that appeared to deliver migrations it did not carry, which §1 calls the
plan's most consequential error. Names ending in a letter (`46a`) read as deliberate hotfixes, which is
precisely why nobody questioned them.

⚠ Both filenames suggest **demo/seed** content (`_seed_fix`, `_broken_demo`), so option 2 is the likely
outcome — but that must be **measured against the schema**, not inferred from the name, before either file
is deleted.

---

## D-7 — F2-53: catalogue **`KB:WRITE` only** (closes F2-53) — ⚠ **amended on implementation, same day**

The knowledge base's *writes* are administrator-only because `KB` is not a catalogued module, so
`hasAuthority('KB_WRITE')` names a permission nobody can hold and the guard collapses to
`hasRole('PLATFORM_ADMIN')`.

**Decision: give writes their own permission.** Not folded into `ADMIN` (whose READ excludes operational
personas who are the main *readers* of a KB) and not into `COMPLIANCE` (which misattributes ownership and
would deny cyber/BCM authors). Market precedent: ServiceNow Knowledge Management carries its own roles
(`knowledge`, `knowledge_admin`) for the same reason — the read and write populations of a KB are shaped
unlike any other module's, wide and narrow respectively.

**`KB:WRITE` → `GRC_ADMINISTRATOR`, `ORG_ADMIN`, `COMPLIANCE_OFFICER`** (shipped as **V329**). The third
grant is the one that matters: without it the fix merely moves authoring from the *platform* administrator
to the *tenant* administrator, and neither is the author of PDPL/ECC/NDMO/Nazaha/NCEMA guidance.

> ### ⚠ Amendment — the read half of this decision was wrong, and implementing it is what showed that
>
> This section originally said *"catalogue `KB:READ`/`KB:WRITE`"* and *"`KB:READ` to every internal role …
> external and machine personas get neither, an internal KB may quote regulator correspondence and vendor
> assessments."* **That was reasoned from the finding's framing rather than from the corpus, and reading
> the corpus refuted it.** All 22 articles are `status='PUBLISHED'`, `visibility='INTERNAL'`, and they are
> staff operational guidance: *"Whistleblowing Channel — How to Report"*, *"Disclosing a Conflict of
> Interest (Nazaha)"*, *"Handling Data-Subject Requests under PDPL"*, *"Business Continuity Activation
> Steps (NCEMA)"*, *"Delegating approvals when you're away"*, *"Employee Onboarding & Offboarding Access"*.
>
> **A whistleblowing procedure readable only by permission-holders is worse than no procedure.** Under
> Nazaha-aligned disclosure practice — and under this platform's own ethics module, which exists to receive
> those reports — every employee must be able to find out how to report. The same holds for the
> conflict-of-interest and DSR walkthroughs. The existing guard, `isAuthenticated()`, **cannot** wrongly
> exclude an employee; a grant list **can only ever** wrongly exclude one. So granting `KB:READ` is not
> merely unnecessary — it is worse than the guard it would have replaced.
>
> **`KB:READ` is therefore not created**, and V329's fourth verification `THROW`s if it ever appears,
> because a catalogued permission that guards no endpoint is F2-13b's documented anti-pattern (15 already
> exist) and a later symmetry-minded migration must not add one without amending this ADR too.
> `KbArticleGuardTest` pins both reads as `isAuthenticated()` for the same reason.
>
> **The transferable point, and why this amendment earns its space:** *"convert role guards to
> permissions"* is a strong default, not a law. This is the counter-example — and the only way to find it
> was to read what the endpoint actually serves rather than what its guard looked like. The same discipline
> corrected D-11's `board_pack` reading and the census instrument itself, all on the same day.
>
> ➕ **Measuring it also found a defect neither the finding nor this ADR mentioned.** `listPublished`
> filters `status='PUBLISHED'`, but the by-slug route resolved on slug alone with **no status filter** — so
> any authenticated caller who knew or guessed a slug could read an article that had never been published.
> Slugs here are guessable by construction (`whistleblowing-channel-how-to-report`), so an author's
> in-progress revision of exactly that procedure was readable before anyone reviewed it. The list endpoint
> hid it: a draft never appeared there, so nothing advertised the slug. Fixed in the same slice —
> unpublished articles now **404** for everyone except `KB:WRITE` holders, who must be able to open their
> own drafts. 404 rather than 403, because *whether an unpublished slug exists* is itself information.
> Live-proven: `viewer` 200 → 404 on a draft, then 200 once published, which is what shows the 404 was
> status and not authorisation.
>
> ⬜ **Raised, not built:** `visibility` is stored on every article and settable through the API
> (`@Pattern(INTERNAL|PUBLIC|RESTRICTED)`), and **nothing anywhere reads it** — the same shape as
> `board_pack`'s `audience` in D-11, found independently in a second module the same day. All 22 rows are
> INTERNAL so there is no live exposure, but deciding what `PUBLIC` and `RESTRICTED` mean for a GRC
> knowledge base is a product call, and half-building it would be worse than naming it.

---

## D-8 — Integration administration: catalogue an **`INTEGRATIONS`** module (closes the slice-4 open call)

F2-05 slice 4 converted the four *ingest* endpoints to `CYBER:WRITE` (correct — the eight seeded `*_INGEST`
machine roles already hold it) and deliberately left the other 20 guards alone, because **no catalogued
permission fits**: `CYBER:READ` would open integration configuration and recorded third-party API-call logs
to 19 roles including `VIEWER`, `REGULATOR_VIEWER` and `EXTERNAL_AUDITOR`; `PLATFORM:WRITE` is `ORG_ADMIN`
alone and would drop the CISO who runs the integrations.

**Decision: `INTEGRATIONS:READ` + `INTEGRATIONS:WRITE`.** Integration administration is genuinely its own
concern — credential rotation, endpoint configuration, and logs of what left the platform. That is neither
security operations nor platform administration, and the measured non-fit of both is the evidence.

- `INTEGRATIONS:READ` → `ORG_ADMIN`, `GRC_ADMINISTRATOR`, `CISO`, `CYBER_MANAGER`
- `INTEGRATIONS:WRITE` → `ORG_ADMIN`, `CISO`
- **Unchanged:** the four ingest endpoints stay on `CYBER:WRITE`; host containment and vulnerability
  risk-acceptance stay **narrower** than the ingest tier, because `CYBER:WRITE` includes eight machine
  feeds and a SIEM must not be able to quarantine a host.

Saudi anchor: outbound integration logs are a **data-transfer** surface. PDPL restricts transfers of
personal data outside the Kingdom, and NCA ECC-2 requires control over data leaving the organisation — so
"who may read the record of what we sent to a third party" is a named regulatory question, which argues for
its own permission rather than a borrowed one.

**`socops` (110 guards, no catalogued module) is explicitly NOT resolved here.** It is ten times the size
and its population question is different; it gets its own ADR when its slice is scheduled.

---

## D-9 — Fraud: create a **`FRAUD_INVESTIGATOR`** role (closes the slice-3 open call)

V328 catalogued `FRAUD:READ`/`FRAUD:WRITE` and deliberately declined to anoint an investigator, because
`FORENSICS_LEAD`, `IR_LEAD` and `SECURITY_ANALYST` were all plausible and picking one would have been a
guess dressed as a fix. The question is now answered on regulatory grounds rather than plausibility.

**Decision: create the role.** **SAMA's fraud-control expectations and the AML/CTF Law treat fraud
investigation as a function distinct from AML and from compliance**, and combining fraud investigation with
compliance *approval* breaks segregation of duties — the investigator would approve their own case
outcome. `FRAUD:WRITE` therefore goes to a dedicated `FRAUD_INVESTIGATOR`, while `FRAUD:READ` stays with
the assurance roles V328 named (`COMPLIANCE_OFFICER`, `INTERNAL_AUDITOR`, `RISK_MANAGER`).

⚠ **This is not a reversal of "do not seed the 40 missing role names."** That rule forbids creating roles
*to make an unconverted guard work* — resurrecting `FRAUD_OFFICER` so a `hasAnyRole` leg fires. Here the
guard is already a permission, the role is being created because **the business function exists in the
regulation and currently has no home**, and the grant is a row in `iam.role_permissions` rather than a
name in Java. Opposite direction, opposite reasoning.

---

## D-10 — `*:EXPORT`: a **tier rule**, not eleven separate calls (closes the 11 open EXPORT decisions)

F2-27 enforced `MODULE:EXPORT` on 40 handlers and left 11 grant decisions open. Deciding them one at a time
invites eleven inconsistencies.

**Decision — one rule:**

> **`EXPORT` follows `READ` for the same module, except where the export is a bulk extract of personal data
> or board-confidential material, where `EXPORT` requires `WRITE` or an explicit assurance mandate.**

The exception list is short and regulator-derived: **`PRIVACY:EXPORT`** (PDPL — a bulk data-subject extract
is a transfer, not a read), **`IAM:EXPORT`** (NCA ECC 2-2 — an entitlement dump is an attacker's shopping
list), and **board/committee exports** (CMA/SAMA charter confidentiality). Everything else follows READ,
which F2-27 already measured as access-preserving: 11 of 14 modules grant READ and EXPORT to identical role
sets.

**Unchanged:** `EVIDENCE:EXPORT` and `ADMIN:EXPORT` keep their documented
`ExportAuthorizationRatchetTest.ALLOWLIST` entries. Those are deliberate and **must not be "fixed"** — the
ADMIN one is a blank form template, and breaking 14 roles for no DLP gain was already considered and
rejected.

➕ F2-13a's third result applies directly and is adopted as policy: **enforcing a permission is also a
re-grant decision.** F2-27 changed what `MODULE:EXPORT` *means* — it gated nothing before and gates 40 file
endpoints after — and not one grant was re-reviewed. Any future slice that gives an existing permission new
teeth must re-review its grants in the same commit.

---

## D-11 — The two `board_pack` residuals from F2-05 slice 5

**(a) No `BOARD` permission module.** `ADMIN` is the right authorisation owner: it already carries
governance administration, `ADMIN:WRITE` is exactly {`BOARD_SECRETARY`, `GRC_ADMINISTRATOR`, `ORG_ADMIN`},
and the stored data agrees — 4 of 16 packs record `drafted_by='board.secretary@tss.com.sa'`. Board
governance is sold separately via its **SKU code** (D-1), not via a second permission family. Fragmenting
governance authorisation to mirror a price list would buy nothing and cost a migration.

**(b) `audience` must actually filter reads — this is a real defect, now scheduled.** Every pack carries
`audience` ∈ {BOARD, AUDIT_COMMITTEE, RISK_COMMITTEE, CYBER_COMMITTEE, EXEC} and `listForOrg()` **ignores
it**, serving every pack to the whole `ADMIN:READ` population. An AUDIT_COMMITTEE pack reaching a
`SUPPLIER_MANAGER` is a charter-confidentiality breach under CMA/SAMA committee rules, and it is exactly
how Diligent/Boardvantage differ from a document folder: **per-committee books, visible to that
committee's members.** F2-26 already links committee seats to platform users, so the mechanism exists.

**Decision:** audience-scoped visibility bound to committee membership, defaulting to **deny** for
non-members, with `ORG_ADMIN`/`PLATFORM_ADMIN` retaining full visibility for administration. Filed as a
P1 finding rather than folded into an authorisation slice, because it changes what a list endpoint returns
and needs its own probe.

---

## D-12 — Authorship attribution: **do not backfill**, and `nca_ecc` is a different question

**`board_pack.drafted_by` is not backfilled.** Existing rows hold display labels — `Sarah Al-Qahtani`,
`Board & Committees Secretariat` — which are historical facts about who assembled those packs. Overwriting
them with a guessed username would **fabricate provenance in a governance record**, which is precisely the
F2-40 defect (one vote rewriting 20 approved resolutions). New rows carry the authenticated principal; old
rows keep what they said. If identity linkage is later required, it arrives as a *new* `drafted_by_user_id`
column, additive, leaving the label intact.

**`nca_ecc` is not the same defect and must not be swept into the same fix.** `NcaEccFilingDto.draftedBy`
is `@NotBlank` and client-supplied, and `NcaEccFilingService` trusts it — structurally identical to the
`board_pack` bug. But **the UI collects it deliberately** (`NcaEccFilingsPage.tsx`, *"Drafted by \*"*), and
an NCA ECC filing may legitimately be prepared by a **named external consultant** who has no platform
login. Stamping the principal would destroy real information and break a shipped form.

**Decision:** keep `draftedBy` as a client-supplied *preparer* label, and add a **server-stamped
`submittedBy`** alongside it. The regulator's question is "who is accountable for this submission", which
the principal answers; "who drafted it" is a different fact that may legitimately name a third party.

---

## Consequences

**Unblocked immediately:** Wave 2 (W2-0, F2-04) via D-1; S6/S7/S8/CE-13 via D-2 and D-3; F2-42, F2-49,
F2-53, and the fraud/integration/export tails.

**Newly scheduled work this ADR creates**, in dependency order — each needs its own verified slice, none is
done by this document: the module-registry SKU reconciliation (D-1); per-tenant CAB configuration with
fail-closed transitions (D-2); the ECAB SLA clock and escalation (D-3); the board-pack JSON aggregate
(D-5); the migration-naming test plus renumber-or-delete (D-6); `KB` catalogue + grants (D-7);
`INTEGRATIONS` catalogue + grants and the remaining 20 guard conversions (D-8); `FRAUD_INVESTIGATOR` +
grant (D-9); the EXPORT grant sweep under the new rule (D-10); audience-scoped board-pack reads (D-11);
`nca_ecc` `submittedBy` (D-12). Migrations will be needed for D-7, D-8, D-9 and probably D-10.

**Still genuinely owner-only, and deliberately left so:**

| | |
|---|---|
| **O-1** | The **elevated restart**. A fresh V328 jar and SPA are staged and smoke-proven as of 2026-07-30; the prod DB is backed up and `RESTORE VERIFYONLY`-checked. Only an elevated shell can swap it. |
| **O-7** | **Commissioning** the penetration test, the witnessed restore/failover and the WCAG conformance statement. Procurement cannot be delegated to an engineer — but §7's listing them as "out of scope" **is withdrawn**: NCA ECC requires penetration testing, SAMA and NCEMA require BCM restore evidence, and a WCAG statement is table stakes for Saudi public-sector procurement. They move to the owner register as three named deliverables, and the engineering side will produce the **specifications** (pen-test scope + rules of engagement, restore-drill runbook + evidence template, WCAG 2.2 AA statement skeleton over the axe evidence already in `audit/`) so what remains for the owner is signing a vendor, not scoping a project. |
| **Pricing** | Every number. D-1 decides *paid vs Base*, never *how much*. |

**What would reopen this ADR:** a design partner refusing to pay for committee governance (D-1); a tenant
whose charter genuinely has no CAB-equivalent, which would make D-2's fail-closed rule a dead end rather
than a prompt; or a Saudi regulatory change to ECC-2 domain 2-2 or the CMA governance regulations.
