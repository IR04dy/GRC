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

> ### ⚠ Amended 2026-07-30 — **"read from per-tenant configuration" has nowhere to read from**
>
> Measured: **there is no per-tenant platform configuration table.** The only settings tables are
> module-local — `bcm.settings`, `ndi.settings`, `risk.methodology_config`, `sust.organisation_settings`. So
> the decision's first clause is not a column read; it is a **new config surface** — table, service, admin
> screen, tenant-scoped. That makes D-2 the queue's long pole, and it is why D-2 sits fifth despite being the
> highest-leverage item: nothing downstream of it is scheduled *because* it is unbuilt, which is a real
> critical-path argument, but a spine-sized build does not become a decision-implementation slice by being
> urgent.
>
> **Build it generically.** Tenant regulator identity, data-residency flags and outsourcing no-objection
> references will all need the same home; a `cab_committee_id` column bolted somewhere convenient would have
> to be moved to make room for them. The premise is confirmed by the data: **only org 1 has committees (17);
> orgs 2 and 3 have zero** — so there is nothing to default *to*, and the two alternatives to failing closed
> are inventing a committee (evidence pollution) or silently skipping the gate (the self-approval defect class
> this programme exists to close).
>
> **Fail-closed is also the commercially correct call, against the ITSM precedent rather than with it.**
> ServiceNow ships stock change models and approval policies referencing groups you populate, and an
> unconfigured instance processes changes on day one; Jira Service Management treats approvals as optional and
> proceeds. Both **fail open** — and get away with it because their buyer is IT ops, their instances are
> effectively single-tenant, and change approval there is workflow plumbing rather than compliance evidence.
> None of those three conditions holds here. The nearer precedent is the board-portal category — Diligent,
> Boardvantage — which **never defaults committee composition**. Two conditions on the implementation, both
> from the commercial seat: the failure must be a **guided onboarding moment that names the screen**, not a
> dead-end error mid-demo; and the SKU-activation gate must mean no tenant ever meets it in normal operation.

> ### ✅ Closed 2026-08-01 — **A17-D2: the config surface already existed, and the missing half was the rule**
>
> **The amendment above got its own blocker wrong, and it is worth being precise about how.** It said "there
> is no per-tenant platform configuration table", sized D-2 at **M→L**, and put it last as *"spine work, not a
> decision-implementation"*. Measured before starting: **`admin.committee_gate_map` has been exactly that
> table since V310 (2026-07-23)** — `(organisation_id, gate, committee_id)`, unique on the pair, seeded by
> V314, and already read by `CommitteeReferralService.refer`. It shipped **seven days before** the amendment
> declared it absent. The generic-config argument still holds for tenant regulator identity and
> data-residency flags; it never applied to D-2's own clause. *Re-measure the prerequisite before inheriting
> the estimate.*
>
> **What was genuinely unbuilt was the decision's second clause** — the rule for the fact's absence — and it
> was unbuilt in the worst direction. Measured live: all three tenants' matrices demand CAB (5 cells) and
> ECAB (6); only org 1 had a gate-map row; and orgs 2 and 3 **approved CAB-routed changes anyway**, because
> the committee gate engaged only when a referral already existed and no referral can be created for an
> unmapped gate. `EMERGENCY_APPROVED` was weaker still — no committee, no vote, 200 straight from `DRAFT`.
> That is the silent pass this decision forbids, filed as **F2-87**.
>
> **What shipped, and the three judgements inside it:**
> 1. **Only the permissive transitions fail closed** — `CAB_APPROVED` and `EMERGENCY_APPROVED`, not
>    `CAB_REJECTED`. Refusing a *rejection* for want of a committee keeps alive a change somebody tried to
>    kill. Fail-closed has a direction; a classification ratchet now forces every future decision target to
>    declare which side it is on.
> 2. **The refusal and the health report share one SQL predicate**, textually, asserted by a test. They had
>    already drifted: a row pointing at a **DISSOLVED** committee made the report call the gate healthy while
>    `refer()` rejected it — the F2-62 shape, caught here before it was reported by anyone else.
> 3. **A silent matrix is not a demand.** No cell for a change's (type, risk) ⇒ no committee required. The
>    matrix *is* the tenant's documented authority; failing closed on its silence would refuse approvals in
>    tenants that never asked for a CAB.
>
> **Both commercial conditions were met and one was only met by building the screen.** `admin.committee_gate_map`
> had an entity, a two-method read repository and **no writer of any kind** — no service, no endpoint, no UI —
> so the only way a tenant could name its own change authority was a DBA `INSERT`, and "an error that names
> the screen" would have been a lie. `GET/PUT/DELETE /api/v1/changes/authority` plus a Change-authority panel
> on `/committees` now exist, and the round trip is verified live: create committee → name the gate → the same
> refused transition returns **200** → clear the gate → **409** again.
>
> ⚠ **One SKU coupling is recorded rather than solved, because no annotation can solve it.** Naming the
> authority is change governance (`CHANGE`), but **constituting** a committee is licensed as `BOARD`. A tenant
> that buys change governance without the board pack can reach the screen and have nothing to select. All
> three dev tenants hold both, so it is latent — but it belongs in the **D-4 shelf decision**: either the
> Change Governance SKU includes the committee subsystem, or its onboarding gate must say it depends on it.
> (The first draft tried to dodge this by putting the endpoint in the committee package under a `CHANGE`
> guard; `EntitlementCoverageRatchetTest` refused it, correctly — package↔SKU is the source of truth, so the
> controller moved to `change_mgmt` instead.)
>
> **The SKU-activation clause is NOT discharged.** D-2 also says setting the gate is a required onboarding
> step blocking change-governance activation. Nothing creates an organisation yet (**F2-81**), so there is no
> onboarding sequence to insert it into; the clause is carried there rather than quietly dropped. Until then
> the boot WARN and the panel's amber banner are the only prompts — which is exactly the posture F2-87 says is
> insufficient on its own, and is why the gate now refuses instead of warning.

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

> ### ⚠ Amended 2026-07-30 — **as specified this cannot be built; three amendments make it buildable**
>
> Measured before sequencing: `change_mgmt.change_requests` has `implementation_started` /
> `implementation_ended`, so the clock **does** have a legitimate start — that half of the decision survives
> contact with the schema. But there are **no ratification, SLA or escalation columns** (a migration is
> required), and **the unauthorised-change register this decision escalates into does not exist as a table** —
> CE-11 is an unbuilt item in an unstarted wave. The consequence half of the control has nowhere to land, and
> by this decision's own sentence that makes it a report.
>
> 1. **Interim escalation destination.** CISO notification + an audit-ledger event, so the SLA is enforceable
>    before CE-11. When CE-11 lands, the register becomes an additional sink, not a replacement.
> 2. **Bound the per-tenant override.** "Overridable per tenant" with no ceiling is not a control — the first
>    examiner question about any tenant-settable SLA is *what stops a tenant choosing 365 days?* Set a
>    maximum, and make exceeding it a documented, approved deviation rather than a settings field.
> 3. **Grandfather the existing row.** There is exactly **one** EMERGENCY change in the data, already
>    `IMPLEMENTED` and never ratified. Without grandfathering, the feature's first act on deployment is a
>    spurious escalation to the CISO about a historical seed row — the DF-3/V322 precedent applies: label
>    legacy rows, do not retro-fabricate a clock they never ran under.
>
> **Re-homed:** D-3 belongs inside the S6/S7 change-governance rebuild that D-2 unblocks, where its columns
> and its register both have somewhere to live. It stays last in the ADR-017 queue and is not a standalone
> slice.

> ### ✅ Implemented 2026-08-02 — `V345`, and re-measurement changed the design twice
>
> Built as `feat/a17-d3-ecab-ratification-sla`. Unit suite **2,855/0/0**, IT **4/4**, **48/48** live probes
> against the running stack with four real personas. Three of the amendment's premises held in **both**
> databases. Two things it did not know changed the design:
>
> **1. The clock has a column but no value.** The one EMERGENCY row's `implementation_started` *and*
> `implementation_ended` are both **NULL** — those columns are only ever written by the IMPLEMENTING /
> IMPLEMENTED transitions and the row was seeded straight into IMPLEMENTED. So this decision's
> *"the clock **does** have a legitimate start — that half survives contact with the schema"* is true of the
> schema and **false of the data**. It matters because `WHERE ratification_due_at < now` against NULL is
> UNKNOWN, which filters the row **out** — reporting it compliant forever rather than exempt. That is F2-62's
> dead-control shape, and it turned amendment 3 from a courtesy into a structural requirement. The design
> answer is `ck_change_ratification_clock`: **PENDING cannot exist without a due date**, so the breach
> predicate is total by constraint rather than by hope. Proven live — the database refuses the update.
>
> **2. Ratification is orthogonal to `status`, so D-3 never needed X-2.** An emergency change awaiting
> ratification is genuinely `IMPLEMENTED` *and* genuinely awaiting ratification. A single `status` column has
> to discard one of those facts, and the one it would discard — *this is live in production* — is what the
> FSC, the change calendar and the KPI pack all read. `ck_change_status` is untouched and a test pins that.
> Six states, not five: **`AWAITING_IMPLEMENTATION`** exists because this decision starts the clock at
> implementation, so between ECAB approval and go-live the change is neither compliant nor breached, and
> collapsing that window into either answer is a lie in one direction or the other. Breach is **derived**
> (`PENDING` + past due), not a seventh state, so a late ratification cannot overwrite the fact that it
> breached — `ratification_escalated_at` survives it.
>
> **Detail decided under standing delegation:** the clock anchors on **`implementation_started`**, not
> `implementation_ended`. It is the earliest defensible anchor, so it cannot be gamed by dragging out the
> implementation window — the same gaming the *"not at raise"* clause exists to prevent — and it is the first
> moment the change is actually live, which is when an unratified emergency change becomes an unauthorised
> production change in the register's sense. Stated explicitly because leaving it ambiguous would recreate
> F-014's two-answers defect.
>
> **Amendment 2 is enforced twice.** `sla_business_days` is bounded **1..10** by `ck_ratification_sla_bounds`
> in the database *and* by `@Min`/`@Max` on the DTO *and* re-checked in the service. Ten is two weekly CAB
> cycles. Three layers is not redundancy: the DTO produces the 400 that explains the rule, the CHECK makes it
> true for a direct UPDATE or a support script, and the service covers server-side callers that never see a
> DTO. A ratchet asserts all three agree, and a stored out-of-bound value falls back to the ADR default
> rather than being honoured. Live: 365 → 400, 0 → 400, 3 → 200, and **an existing deadline does not move**
> when the SLA changes (due dates are stamped once, at implementation).
>
> **Two findings the build could not avoid.** **F2-93** — the change record named *when* the CAB decided and
> never *who*, so the SoD test this decision requires had nothing to compare against; fixed in the same
> migration, not back-filled from the audit ledger (that would manufacture provenance). **F2-94 (P1)** — the
> escalation fired, was audited, incremented `fire_count`, and **reached nobody**: the F8 executor reads
> `to_role`, and every rule seeded since V96 writes `roles`. **126 of 164 rule codes platform-wide** are in
> that state, including `AR-97` HIGH-risk-emergency-change → CTO + CISO. D-3's own rule is fixed and
> live-verified as `ESCALATE → CISO (1 delivered)` landing in a real inbox; the other 125 need their own
> slice.
>
> **What the interim escalation actually is, now that it runs:** a daily sweep (cron configurable — a
> hard-coded daily job means the consequence half can only ever be exercised by unit tests) that publishes
> `CHANGE_RATIFICATION_BREACHED`, stamps `ratification_escalated_at`, and writes a ledger line naming the
> consequence: *"Until ratified it stands as an unauthorised production change."* When CE-11 lands, the
> register becomes an **additional** sink. `GET /api/v1/changes/ratification-register` says so in its own
> payload (`registerScope: EMERGENCY_RATIFICATION_ONLY`) so no consumer mistakes it for the full article.
>
> ⚠ **The sweep's first cut was itself a dead control, and six green unit tests could not see it.**
> `@Transactional` on a method called from `tick()` in the same class is self-invocation — the proxy is
> bypassed, so the native escalation stamp threw *"Executing an update/delete query"* on **every** run while
> `tick()` logged one WARN and continued. Mocked repositories need no transaction, so the tests were green
> against a control that had never once fired. Only the live fire against a real database found it. Fixed
> with a per-change `REQUIRES_NEW` `TransactionTemplate`; the WARN is now an ERROR that says out loud that no
> escalation was recorded; and a test asserts the propagation, because that was the one property whose
> absence every other test was blind to.
>
> **No frontend.** The register and policy endpoints exist for S7/CE-13 to consume. Adding a screen now would
> be nav-to-nowhere — the F2-02 defect CE-0 deliberately avoided.

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

> ### ⚠ Amended 2026-07-30 — **the safety argument above names the wrong module**
>
> The sentence "F2-05 slice 5 gave `board_pack` a real authorisation model … so exposing an aggregate
> endpoint is now safe" is **wrong**, and it is wrong in the direction that matters. There are **two** board
> packs: `board_pack` is a curated CRUD entity (16 stored rows, tightened by slice 5 to `ADMIN:READ` = 21
> roles / `ADMIN:WRITE` = 3 roles), while the **live aggregate this decision exposes** is
> `reporting.BoardPackService`, built from 11 repositories. Slice 5 gave that nothing. Its sibling export
> endpoints carry `hasPermission('REPORTING','EXPORT') or hasPermission('RISK','EXPORT')` — **24 of 59
> roles**, including `VIEWER` and `SUPPLIER_MANAGER`.
>
> **Consequences, both binding:**
>
> 1. **D-5 must not ship before D-10a.** Doing so replaces a *labelled mock* with an *unlabelled leak* —
>    real content rendered live to those 24 roles. The banner currently makes the page honest; removing the
>    banner while the tier is still wide makes it dishonest in the other direction.
> 2. **The JSON endpoint must write the same export-audit record as the PDF/PPTX paths.** JSON of an
>    aggregate is an export in substance, and a read-shaped route that is an export in substance is exactly
>    how a narrowed control acquires a side door. Nearly free: `ExportAuditInterceptor` is registered on
>    `/api/**`, so coverage is automatic — only the `entity_code` extraction needs to name it.
>
> ➕ Found while checking a council claim: the aggregate pulls **200 itemised risks and 1,000 itemised audit
> findings** per call. That is an operational extract, not board MI → **F2-55**. Re-tiering who may pull the
> pack does not fix what is in it.

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

## D-8 — Integration administration: catalogue an **`INTEGRATIONS`** module (closes the slice-4 open call) — ✅ **SHIPPED V343**

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

> ### ⚠ Amended 2026-07-30 — **this decision WIDENS access; it was being planned as a narrowing**
>
> The sequencing note that carried this into the plan described D-8 as narrowing a PDPL/ECC-2 surface. It is
> the opposite, and the measurement says so plainly. The 20 remaining guards recite **nine role names, of
> which six do not exist** in `iam.roles`: `SOC_AUTOMATION`, `INTEGRATION_SERVICE`, `SOC_ANALYST`,
> `SOC_TIER_2`, `INCIDENT_COMMANDER`, `BOARD_MEMBER`. Because `hasAnyRole` never reaches
> `ModulePermissionEvaluator` and so gets **no PLATFORM_ADMIN bypass**, a guard naming only ghosts admits
> nobody. Net effect today: roughly **10 endpoints are PLATFORM_ADMIN-only by attrition** and the rest are
> `PLATFORM_ADMIN + CISO`. Moving to `INTEGRATIONS:READ` (4 roles) / `WRITE` (2 roles) therefore **grants
> access that nobody currently has**.
>
> The decision stands — accidental admin-only-ness is not a security model, and the grants named above are
> the right ones. What changes is the **class of work**: this is a **re-grant**, so by the rule adopted in
> D-10 it must be probed and justified as one, in the same commit. Specifically:
>
> - Record a **need-to-know rationale for `CYBER_MANAGER`** reading third-party egress logs, which may
>   contain personal data (PDPL) — the widest of the four new grants and the only one that is not obviously
>   an administrator.
> - Probe each converted endpoint with a role that gains access and one that does not, and state the
>   before/after population per endpoint rather than in aggregate.
> - The ghost names must be **deleted, not seeded**. Seeding `SOC_ANALYST` to make an old guard work would
>   make an absent control look present — §7's standing exclusion.

> ### ✅ Shipped as V343, 2026-08-01 — and the decision changed shape in two places once it was measured
>
> **15 of the 20 guards moved**, not 20. Suite **2752/0/0**; live **154 checks across 7 personas**, with a
> before/after diff over 132 cells: **114 unchanged, 17 gains, 1 loss, 0 unexpected**. Role-guard ratchet
> **552 → 537**. Grants: `INTEGRATIONS:READ` → `ORG_ADMIN`, `GRC_ADMINISTRATOR`, `CISO`, `CYBER_MANAGER`;
> `INTEGRATIONS:WRITE` → `ORG_ADMIN`, `CISO`. All six ghost names are gone from the package, and
> `SOC_ANALYST`, `SOC_TIER_2`, `SOC_AUTOMATION` and `INTEGRATION_SERVICE` are now absent from **every**
> `@PreAuthorize` on the platform — this package was their last user (the distinct-role-name census fell
> 74 → 62 over F2-05, and these four came out here).
>
> **1. The READ/WRITE line is not where this decision drew it.** D-8 put "logs of what left the platform"
> under `INTEGRATIONS:READ`. Measured against the code, the only endpoints that return anything resembling
> a payload are the four `/stub/recorded-calls` diagnostics, and `StubInvocation` carries the **frozen
> inbound payload verbatim**. They are therefore on `INTEGRATIONS:WRITE` — a GET on the write tier, which
> looks wrong and is right: the tier is *integration administrator*, and widening a payload dump to the two
> assurance personas READ adds would have been the opposite of this decision's purpose. Conversely the PDPL
> framing in the amendment is **narrower than it claimed**: those four endpoints resolve the stub bean via
> `ObjectProvider#getIfAvailable` and return `[]` when it is absent, which it is unless `mode=STUB`. So the
> personal-data exposure is real in dev and in any customer pilot left in STUB, and nil in LIVE. *A mode is
> not a guard, so the guard still had to be right — but the risk register entry should say "pilot", not
> "production".*
>
> **2. The five outbound-action endpoints were NOT converted, and the reason is a revocation this decision
> did not foresee.** D-8 listed host containment and vulnerability risk-acceptance under "Unchanged" and
> said only that they must stay narrower than the ingest tier. Measured: `INTEGRATIONS:WRITE` is
> `{ORG_ADMIN, CISO}`, and `RISK_MANAGER` holds neither — so converting `recast` / `accept-risk` /
> `remove-recast` onto it would have **taken severity override and vulnerability risk-acceptance away from
> the one role whose function that is**, while handing host quarantine to a tenant administrator with no
> security-operations claim. Live-proven after the change: the throwaway `ORG_ADMIN` probe user gets 200 on
> all 15 converted endpoints and **403 on all five actions**, which is the design, not a gap. Their ghost
> legs were stripped without converting them — provably access-preserving, since a name absent from
> `iam.roles` can never satisfy `hasAnyRole`. *Vulnerability risk-acceptance is a two-population decision —
> cyber **and** risk — that no catalogued permission expresses. Naming one is a real open question and it is
> better left named than answered by whichever permission happened to be nearest.*
>
> **The `CYBER_MANAGER` rationale this amendment demanded, and it came out stronger than expected.**
> `CYBER_MANAGER` already holds `CYBER:READ` + `WRITE` + `DELETE` + `EXPORT`, so every detection and
> vulnerability finding these four adapters ingest is already readable *and exportable* by that role.
> `INTEGRATIONS:READ` adds only the record of the **transport** — adapter health, masked configuration,
> last-poll/last-push timestamps — for payloads the role may already read in full. Under PDPL that is the
> weaker access, not the stronger one. Refusing it would produce a cyber manager who can export a
> vulnerability register but cannot tell whether the scanner feeding it stopped four weeks ago.
>
> **One access loss, recorded in the guard that takes it.** `RISK_MANAGER` loses
> `GET /api/v1/integrations/tenable/status`, where it was named alongside PLATFORM_ADMIN, CISO and **five**
> ghosts — `BOARD_MEMBER` on an adapter kill-switch endpoint being the tell that the list was written by
> enumeration. It keeps all three Tenable actions and every `RISK:*` permission, and it holds no `CYBER`
> permission on either database, so it never had a route to the findings themselves.
>
> **`BOARD_MEMBER` came off `accept-risk`, and `docs/integrations/TENABLE.md` had to be corrected rather
> than edited.** That page justified the name on SAMA CSF grounds — residual-risk decisions sit with the
> board or its delegated committee — which is a sound requirement whose implementation never existed: the
> role is absent from both databases, so no board member ever reached the endpoint. The page now says so,
> because a customer building an access matrix from the old table would have documented a board-level
> control the platform does not have. **The same correction was owed on CROWDSTRIKE.md, which described
> `lift` as "narrower than `contain` … not Tier 2 analysts" when four ghost names had made the two
> identical all along.** *F2-05 slice 4's keeper was "when a ghost role appears in a guard, grep the docs
> for it". This slice adds the sharper version: the docs did not merely repeat the ghost, they described a
> **distinction between two controls that did not differ** — a runbook can assert a control the code never
> had, and an ECC-2 evidence pack built from it inherits the claim.*
>
> **What the F2-05 slice-4 note got wrong, which is worth stating plainly because it held for three days.**
> It recorded that the remaining 20 guards were "not defective: they name PLATFORM_ADMIN, CISO and
> RISK_MANAGER, which all exist". Those three do exist — and six *other* names in the same twenty guards do
> not. **Checking the names a guard needs is not the same as checking the names a guard has.** That
> sentence, plus "no catalogued permission matches this population", is what kept the module
> administrator-only: *"no permission fits" is an argument for cataloguing one, not for leaving the guard
> alone.*

---

## D-9 — Fraud: create a **`FRAUD_INVESTIGATOR`** role (closes the slice-3 open call) — ✅ **SHIPPED V330**

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
name in Java. Opposite direction, opposite reasoning. **One** role, not two — the old ghost guard named both
`FRAUD_OFFICER` and `FRAUD_INVESTIGATOR` for the same function, and creating both would re-import the habit
that produced 40 ghost names.

> ### ✅ Shipped as V330, 2026-07-30 — and the measurement made the argument stronger than this section did
>
> The SoD claim above was reasoned from regulation. It is now **measured**: `FRAUD:WRITE` was held by exactly
> `GRC_ADMINISTRATOR` and `ORG_ADMIN`, and those two hold **10 and 8 `APPROVE` permissions** respectively
> (ADMIN, BCM, CAPA, CHANGE, IAM, NDI, RISK, SIGNATURE, SUST, WORKFLOW). So the only two roles that could
> investigate a fraud case were also the platform's broadest approvers — not a theoretical conflict.
>
> The three rejected candidates were also checked rather than dismissed: `FORENSICS_LEAD` (15 perms),
> `IR_LEAD` (17) and `SECURITY_ANALYST` (18) hold **zero** APPROVE permissions each, so all three are
> correctly investigator-shaped — they were rejected purely because they are *cyber incident* personas and
> fraud is financial crime. There is no `AML_OFFICER` role and no AML module in `iam.permissions`, so FRAUD is
> the only financial-crime surface catalogued and it had no owner at all.
>
> ➕ **Three row-shape choices were measured, not assumed.** `organisation_id = NULL` (global) because **47 of
> the 58 existing roles are scoped to `organisation_id = 1`** and therefore do not exist for orgs 2 and 3 — a
> platform persona must not inherit that, and that 47 is its own finding. `is_privileged = 0` because the
> **only** reader of that column anywhere is `BreakGlassEventRepository`: flagging it would file every fraud
> investigator as a break-glass subject, which is the opposite of what the word suggests. And
> `display_name_ar` is mandatory, because a role name is user-visible.
>
> ⚠ **What V330 does NOT do, stated so it is not assumed.** It makes SoD *possible*; it does not enforce it.
> Measured: `FraudService` has no self-check and no four-eyes gate on `setOutcome`/`close`, so one principal
> can still open, investigate and resolve the same case. V330 also deliberately does **not revoke**
> `FRAUD:WRITE` from the two administrators — doing so would leave every tenant unable to work a fraud case
> until someone assigns the new role, which is an outage rather than a control. **Maker-checker on fraud case
> outcome is a separate slice**, and it is the control that actually closes the SoD gap.
>
> Live-proven **11/11**: a `FRAUD_INVESTIGATOR`-only account opened a case, triaged it, recorded a finding,
> read it back and read the dashboard — the entire lifecycle that was PLATFORM_ADMIN-only before V328 and
> administrator-only after it. `FORENSICS_LEAD` and `viewer` stay 403.

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

> ### ⚠ Amended 2026-07-30 — **split into D-10a and D-10b; the rule bundles two different sizes**
>
> This decision reads as one rule and implements as two pieces of work. *"EXPORT follows READ"* is a grant
> edit. *"…except where EXPORT requires WRITE or **an explicit assurance mandate**"* invokes a mechanism
> that **does not exist** — there is no mandate entity, no time-boxing, no engagement scope, nowhere to
> minute it. Bundling them means either the narrowing waits on the mechanism, or the mechanism is faked.
>
> **D-10a — the measured narrowing (shippable now).** `REPORTING:EXPORT or RISK:EXPORT` gates the board pack
> and is held by **24 of 59 roles**. Strip the four with no colourable claim: **`VIEWER`** (generic
> read-only — fails ECC 2-2 / SAMA CSF 3.3.5 least-privilege on sight), **`SUPPLIER_MANAGER`** (the pack
> carries counterparty risk about the very suppliers this role negotiates with — conflict of interest and
> PDPL purpose limitation), **`MA_LEAD`** (concentrates price-sensitive MNPI — CMA market conduct), and
> **`EMERGENCY_RESPONDER`** (needs BCM data, not the risk register). Re-review the wide baselines in the same
> commit: `WORKFLOW:EXPORT` **55/59 including 8 machine accounts**, `CHANGE:EXPORT` 46/59, `AI:EXPORT` 42/59.
> ➕ Measured, and it inverts this decision's own emphasis: the two named **exceptions are already tight**
> (`PRIVACY:EXPORT` **8/59**, `IAM:EXPORT` **3/59**). The exceptions needed no work; the **baseline** needs
> all of it. The rule was written pointing at the wrong end of the distribution.
>
> **D-10b — the assurance-mandate mechanism (the part that was hiding).** `EXTERNAL_AUDITOR` and
> `REGULATOR_VIEWER` are **not** stripped by a grant edit, because Saudi law creates an *obligation to
> furnish* — Banking Control Law art. 18, Capital Market Law inspection powers, Companies Law auditor access,
> NCA ECC assessment. But every one of those is an obligation to produce **through a governed disclosure
> channel**: scoped to the authority, time-boxed to the requisition or engagement, minuted, logged. None
> creates a standing self-service credential inside the institution's systems. So a standing grant is the
> wrong shape *and* deleting it with nothing in its place manufactures the inverse finding — inability to
> furnish, or obstruction of an audit mid-engagement. `REGULATOR_VIEWER` fails specifically because one role
> cannot encode per-authority scope: SAMA as AML supervisor, a CMA securities inspector and an NCA ECC
> assessor are not entitled to the same material.
>
> **Ordering consequence:** D-10a first in the whole ADR-017 queue, D-10b third — after D-5, which depends on
> D-10a's tier existing but not on the mandate mechanism.
>
> ➕ **Step 0 of D-10a is a look-back, and it is the step this ADR omitted entirely.** After "24 of 59", the
> first question internal audit asks is *was it exercised?* — and any hit starts a PDPL art. 20 assessment
> with a 72-hour SDAIA clock. Runnable, because `ExportAuditInterceptor` covers `/api/**`: dev shows **366
> EXPORT events, 72 of them board-pack**, and the only low-privilege pulls trace to this programme's own
> probing. **The same query has not been run on prod**, where the answer is unknown. Running it also found
> **F2-54** — `platform.audit_log.user_role` records a *permission*, not a role, on all 5,801 rows.

> ### ⚠ Amended 2026-08-01 — **D-10c: the rule was one-directional, and read as a floor it undoes its own fixes**
>
> This amendment discharges **F2-61(a)**. It is an engineer-drafted amendment to a rule already ratified under
> this ADR's standing delegation, not a fresh owner call. Drafted from a measurement, not from the sentence.
>
> **The problem with "EXPORT follows READ" is that it is a comparison without a direction.** Read as a
> **ceiling** it says *EXPORT must not exceed READ* — narrowing. Read as a **floor** it says *wherever READ is
> held, EXPORT should be too* — widening. Both readings are faithful to the words, and they disagree about
> real grants. F2-61's own proposed shape ("default deny for generic personas") assumed the ceiling; the
> sentence as written also licenses the floor. Measured consequences of the floor reading
> (`Appendix/scripts/export_grant_policy.py`, dev and prod, 2026-08-01): it would add **2** grants —
> `CHANGE:EXPORT` and `REGULATORY:EXPORT` to `VIEWER`. **`REGULATORY:EXPORT` is precisely the grant V333 and
> V335 revoked from `VIEWER` three days earlier, under this very rule.** A rule that re-grants what it was
> used to revoke is not a rule yet.
>
> **Decision — the rule is a CEILING, and it is stated as a containment:**
>
> > **For every module, the set of roles holding `MODULE:EXPORT` must be a subset of those holding
> > `MODULE:READ`. A file is a superset of a screen: there is no coherent authorisation state in which a
> > principal may extract the whole register and be refused one record of it. `EXPORT` never implies a grant
> > — holding `READ` creates no entitlement to `EXPORT`, which remains a separate decision per module.**
>
> That is strictly narrower than the original sentence and contradicts nothing already shipped: D-10a's
> revocations, F2-59's and F2-60's narrowings all move in the ceiling direction.
>
> **Enforced in the guard, not in the grant table.** The containment is a property of every export handler:
> `hasPermission('M','EXPORT') and hasPermission('M','READ')`. Maintaining it by hand in `iam.role_permissions`
> is what failed — V320 granted one permission to satisfy one endpoint's AND-composition and inverted three
> other guards it never mentioned, because **a permission is module-wide and a guard is not.** A grant edit
> can be undone by the next grant edit; a composed guard cannot drift.
>
> **The three documented exceptions, enumerated rather than described:**
>
> **(i) A module with no same-module `READ`.** `BOARD:READ` is deliberately not catalogued (D-11(a) put board
> reads on `ADMIN:READ`), while `BOARD:EXPORT` is held by 8 roles. For such a module the containment is
> **unsatisfiable, not unsatisfied**, and the guard composes with the module's *actual* read authority. The
> original rule silently assumed every `MODULE:EXPORT` has a `MODULE:READ` to follow; one of 26 does not.
>
> **(ii) A guard already narrowed on an independent axis.** `MlModelController`'s AI export ANDs
> `hasAnyRole('RISK_MANAGER','CISO','DPO','AUDITOR','PLATFORM_ADMIN')`, so `AI:EXPORT` alone is insufficient
> and `AUDITOR` holding it without `AI:READ` cannot widen anything. V320 granted that deliberately and
> recorded why. A role list is a second gate, and two gates satisfy the intent of one containment.
>
> **(iii) A grant that exists only to satisfy an exception of type (ii).** `CISO` and `CFO` hold `BCM:EXPORT`
> without `BCM:READ` for exactly that reason. Rather than revoke (which breaks the binder endpoint's recorded
> intent) or grant `BCM:READ` (which widens two executive roles across all of BCM to fix an over-grant — the
> wrong direction under NCA ECC 2-2 least privilege), the **guards** were composed. The grant survives in the
> table and is now unreachable in code.
>
> **What that decided, on the platform, in this slice.** Three BCM export handlers authorised on `BCM:EXPORT`
> alone: the BCP plan PDF, the crisis after-action report, and the cycle evidence ZIP whose own source calls
> it *"the most sensitive export on the platform"*. Live before: `so_officer` (CISO) → **403** on
> `GET /api/v1/bcm/plans`, **200 + 38,043 bytes** of that plan as PDF, **200 + 55,107 bytes** of evidence ZIP.
> **The file was the only way that user could see any of it** — F2-27's ratified premise running backwards.
> Live after: **403 on all three**, with `auditor`, `compliance`, `rmanager` and `viewer` — every persona
> holding both — still served real bytes on the screen *and* the file.
>
> **What is deferred to F2-61(c), and it is a real remainder, not a formality.** **32 of the 40** file-returning
> handlers still authorise on `EXPORT` alone. None is currently exploitable, because after this slice every
> module's grants satisfy the containment — but each is one grant edit away from the BCM defect, which is
> exactly how BCM got there. Composing them is module-sized with a before/after probe each, because a module
> whose `EXPORT` is legitimately wider would lose live access on the day the AND lands. Ceiling pinned at 32 in
> `ExportAuthorizationRatchetTest`: it may fall and must never rise.
>
> ### ✅ Closed 2026-08-01 — **F2-61(c): all 32, in one change, and the paragraph above got the reason for slicing backwards**
>
> The remainder is discharged. **All 32 now AND a `READ` permission** (33 counting `/board-pack.json`, which is
> not file-returning but carries the identical guard by `BoardPackGuardTest`'s own assertion), leaving **36 of
> the 38** `EXPORT`-guarded file handlers composed and **exactly two** documented exceptions.
>
> ⚠ **It was one code change, not the module-at-a-time revocation the paragraph above prescribes — and that
> paragraph had the argument inverted.** "Module-sized with a probe each" is the right shape for a *grant*
> revocation, because a revocation removes access and the loss has to be argued per module. This slice moved
> the enforcement to the *guard*, where the collateral is not argued but **proved**: `export_grant_policy.py`
> measures every affected module as `EXPORT ⊆ READ` in dev **and** production, so every principal that
> satisfied the old guard satisfies the new one — no revocation, nothing to slice. **The instrument chosen
> determines whether a change needs slicing at all**, and the deferral inherited a granularity from an
> instrument it had already replaced.
>
> **Live, and the probe manufactured the shape rather than waiting for it.** No live role was inverted for any
> of the 32, so a persona probe could only have shown 200-before and 200-after. Instead a `[REVIEW-TEST]` role
> was given **11 `*:EXPORT` grants and no `READ` anywhere** — the exact shape V320 Part 3 produced by accident
> — and probed against every endpoint. **Before: 33 of 33 served 200 with real bytes** (31,404-byte risk
> register PDF, 40,404-byte board pack, 34,903-byte deck, 53,139-byte regulator evidence ZIP, 68,686-byte IAM
> access-governance ZIP, 44,995 bytes of the whole tenant calendar) while the same token was refused **403 on
> all seven module screens that answer** — risk, findings, changes, board packs, cyber controls, NDI reports,
> SGI commitments. **After: 0 of 33**, with `so_officer`, `compliance`, `rmanager` and `viewer` unchanged in
> **all 164 remaining cells**.
>
> **Exception (i) resolved concretely.** The two board-pack exports compose **`ADMIN:READ`** — not because
> `BOARD:READ` is missing and something had to be picked, but because `ADMIN:READ` is the guard
> `BoardPackController`'s own screen already carries (F2-05 slice 5). That makes it the containment this clause
> asks for rather than a stand-in: a principal refused the board workspace cannot take the pack as a file.
> `BOARD:EXPORT` is 8 roles, `ADMIN:READ` is 21, and the 8 are a measured subset of the 21 in both databases.
>
> ⚠ **Exception (ii) was over-broad as written, and the ratchet's version of it was hiding a handler.** This
> clause says *"a role list is a second gate, and two gates satisfy the intent of one containment"* — and the
> ratchet implemented that as a pattern, so **any** `has(Any)?Role(` counted as sufficient narrowing.
> `SecurityEventController#export` (`CYBER:EXPORT` + a four-role list) sat inside that exemption **with no
> claim to it**: measured in both databases, none of its four named roles holds `CYBER:EXPORT` without
> `CYBER:READ`, so it satisfied the containment for free and was excused from stating so. It now ANDs
> `CYBER:READ`. **Clause (ii) is therefore narrowed: a role list is not a reason on its own. The bar is a
> named role that ANDing `READ` would measurably revoke** — which is true of exactly two handlers,
> `MlModelController#export` (`AUDITOR`) and `BcpBinderController#download` (`CISO`, `CFO`). Both are now
> enumerated by name in `ExportAuthorizationRatchetTest.EXPORT_WIDER_THAN_READ` instead of matched by pattern,
> because a pattern-based exemption also lets the next handler dodge the rule by adding a role list. *An
> exemption that is easier to satisfy than the rule will be satisfied instead of the rule.*
>
> **The ceiling is retired, and that is a change of kind, not of number.** `BARE_EXPORT_CEILING = 32` is gone;
> `everyExportHandlerAlsoDemandsARead` asserts the uncomposed set is **empty**, with an anti-vacuity floor of
> 36 composed handlers and an assertion that both named exceptions still exist in the scanned surface. **A
> ceiling tolerates the population it measures** — which is correct while a sweep is pending and wrong the
> moment it lands, because after that a tolerated guard is indistinguishable from a reviewed one.
> Mutation-proven twice: restoring one bare guard fails by handler name, and restoring it *with a role list*
> — the form the old ceiling accepted — **still** fails.
>
> ➕ **Found in passing, and it is this clause's own lesson (b) recurring: four F2-27-era guard comments
> asserted access-preservation in the direction that preserves nothing, and V335 falsified all four.** Each
> said some variant of *"every role holding `MODULE:READ` also holds `MODULE:EXPORT`"* — the floor direction,
> which rules out a harmless reader-who-cannot-export and says nothing about the exporter-who-cannot-read that
> is the hole. And each was overtaken three days later when **F2-59/V335 revoked ten `*:EXPORT` grants from
> `REGULATOR_VIEWER`**: `NdiReportController` ("32 of 58 — zero gap"), `ClimateReportController` and
> `GreenFinanceController` ("all 33 roles — zero gap", now 31 of 32), and `BcmEvidencePackageController`
> ("all 17 roles", now 16 of 17). All four corrected in place, because they are the notes a reviewer reads
> when deciding whether an export guard is safe — and all four would have argued that this slice was
> unnecessary.
>
> ➕ **A measurement worth keeping even though it changed nothing here.** The tier rule's *breadth* problem is
> six modules, not twenty-four: `WORKFLOW` 54, `CHANGE` 45, `AI` 42, `SUST` 31, `NDI` 31, `COMPLIANCE` 31 of 58
> roles, while 14 of the 26 sit at 14 roles or fewer. Any future narrowing should start there, and the
> `VIEWER`-holds-16 figure is now **identical in dev and prod** — the dev-16/prod-17 divergence F2-61 recorded
> closed when V335 reached production.
>
> ⚠ **And the endpoint this whole exception chain protects returns 500 for every caller** — `admin`,
> `auditor` and `so_officer` all get `PDF export failed` from `/api/v1/bcm/binders/1/download` on three real
> `BILINGUAL` rows. V320 granted two roles a module-wide permission to preserve access to something that
> works for nobody. Filed as **F2-78**; not fixed here.

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

> ### ⚠ Amendment, 2026-07-30 — **this decision does not survive its own impact query. Enforcement is deferred; the prerequisite shipped as V331.**
>
> Measuring what "default deny for non-members" would actually do, before building it:
>
> | audience | packs | resolves to `committee_type` | linked platform users |
> |---|---:|---|---:|
> | `AUDIT_COMMITTEE` | 1 | `AUDIT_COMMITTEE` | **0** |
> | `BOARD` | 7 | `BOARD` | **0** |
> | `CYBER_COMMITTEE` | 1 | *(nothing — real type is `CYBER_SECURITY`)* | **0** |
> | `EXEC` | 4 | *(nothing — real type is `EXEC_COMMITTEE`)* | **0** |
> | `RISK_COMMITTEE` | 3 | `RISK_COMMITTEE` | 2 |
>
> **13 of 16 packs would have become unreadable by any non-admin principal — all 7 BOARD packs among
> them.** The decision would have locked the board out of board packs. Only **5 of 31** committee member
> terms are linked to a platform user, and **zero** on the Board and the Audit Committee.
>
> That is not a data-quality accident to be waited out. **F2-26 deliberately supports directors who will
> never have a login** (`no_platform_user` + a declared-EXTERNAL state), so membership-by-platform-login
> is *structurally* incapable of being the whole control for this record type. Any design that treats it
> as authoritative is wrong in principle, not just early.
>
> ➕ **And the join it depended on was broken anyway** — three vocabularies, two of them wrong:
> `BoardPackDto`'s `@Pattern` accepted 4 values, the table held 5, and `admin.committees.committee_type`
> has 17. One pack held `CYBER_COMMITTEE`, **which the API could no longer create**, and neither
> `CYBER_COMMITTEE` nor `EXEC` matched any committee — so `audience = committee_type` resolved **3 of 5**
> and would have silently mis-scoped the rest. Silently is the whole problem: no error, no log line, just
> a governance pack visible to the wrong set or to nobody.
>
> **What shipped instead (V331):** the two drifted values are re-pointed onto the committee vocabulary,
> the DTO pattern is widened to that vocabulary, and the migration **throws if any stored audience fails
> to resolve to a real `committee_type`** — so the drift cannot recur unnoticed and the join D-11 needs
> is now sound. `BoardPackGuardTest` pins both directions, including that the two dead spellings stay
> refused. No read behaviour changed; `unresolved` went 5 → 0.
>
> **Enforcement is gated on a stated readiness condition, not a hope.** Before the read filter lands it
> needs (a) a cross-module committee lookup behind a port, because validating and joining against live
> committees from `board_pack` is a boundary crossing ADR-012 governs — and notably **no architecture
> test enforces ADR-012 today**, so the boundary is a decision without a control; and (b) a resolution
> of who reads a pack when its committee has no linked members, which cannot be "nobody". The likely
> answer is *secretariat + linked members* — `BOARD_SECRETARY` already holds `ADMIN:READ` and is the
> persona that circulates packs — but that narrows 21 roles to roughly 3 and is a product call, not a
> fix. **This is the F2-27 lesson repeating: the impact query falsified the design the plan had
> pre-committed to, and the right response was to ship the measurement rather than the guess.**

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

## D-13 — F2-144(b): filing a conflict of interest is **`isAuthenticated()`**, and the exception becomes a rule (closes F2-144)

**The decision asked for:** may `CoiController#submitDeclaration` be guarded by `isAuthenticated()`, which
ADR-010 forbids for writes?

**Decision: yes — and D-7 stops being a one-off.** ADR-010 gains a §Sanctioned exceptions section defining
the class both members belong to: **an endpoint that discharges an obligation every employee is under is
guarded by `isAuthenticated()`, not by a permission.** Admission needs a universal duty, a
**server-bound subject**, and a pinning test. Two members today: knowledge-base reads (D-7) and COI
submission (this decision).

**Why not a permission.** Symmetrically-wrong guards are not symmetrically costly. A grant list can only
ever wrongly *exclude* — and for an endpoint that is how a person *complies with a duty*, exclusion means
the obligation still stands, the platform records no discharge of it, and the gap reads as the employee's
fault. D-7 reached this by reading the knowledge base's corpus; the same logic transfers to the act
itself. Note the platform had **already** agreed for the instructions: D-7 deliberately keeps *"Disclosing
a Conflict of Interest (Nazaha)"* readable by every employee. Gating the *doing* behind a grant while the
*explaining* is open would have left the product describing a duty to people it then refused.

**Why this was not safe to decide before now, which is the part worth keeping.** F2-144 found the guard
reciting `'ETHICS_OFFICER'` and `'USER'` — **neither is a role** — so submission had collapsed to three
administrators and the stated capability had never worked. The obvious remedy, opening the guard, would
have been **worse than the defect**: `submitDeclaration` took `userId` from the request body, so any
authenticated caller could have attested in a colleague's name. The dead legs were the only thing
preventing an unbound write. Part (a) moved the choice of declarant to the server; only then did (b)
become a decision about authorisation rather than a hole. **Server-bound subject is therefore condition 2
of the new rule, not a footnote to it** — without it the exception is an impersonation path.

➕ **A second, quieter change came with it, and it is the more durable one.**
`ControllerAuthorizationRatchetTest` asserted `count <= 21` while its javadoc *enumerated* those 21 in
three reasoned buckets. Prose is not an assertion. A count ceiling tolerates the population it measures:
any member could have been swapped for a different unguarded endpoint, or all 21 guarded and 21 fresh ones
introduced, and the test would have read green — the `BARE_EXPORT_CEILING` defect house rule 3 already
records. Granting this exception was the first request to *add* a floor member since the enumeration was
written, so the floor became a **pinned set** asserted in both directions. It now fails when a member is
guarded and not removed, which a ceiling can never do, and the count (22) is derived from the reviewed set
rather than declared beside it.

⚠ **What was deliberately not done:** `ETHICS_OFFICER` remains in `CoiController.MANAGE_GUARD` as a dead
leg. Dropping it there would silently ratify **compliance-officer ownership of ethics oversight** — F2-145's
reasoning applied to a second module — and no such decision has been made. Reviewing, opening and closing
campaigns and recording recusals are authority acts over *other people's* declarations; they keep the role
set and are the correct target for a later F2-05 conversion to `ETHICS:*`. So a one-line change removing
two dead names moves the ghost baseline by **one** (24 → 23), which is the clearest illustration to hand
that the ghost count is a *name* count, not a defect count.

**No migration.** Nothing is catalogued: the point of the decision is that no permission is created. Had
one been, F2-13b's anti-pattern — a catalogued permission guarding no endpoint — would apply.

---

## D-14 — F2-145: the seven Saudi/financial modules' domain roles — **three mechanisms, not two options**

**The decision asked for:** which of the seven modules' ghost domain roles should exist?

**The finding offered a binary and it is the wrong frame.** F2-145 recommends either *(a)* seat the roles
or *(b)* convert to `MODULE:*` permissions per ADR-010. Measuring what the platform **already models**
shows the eleven ghost names are not one kind of thing, and two of them already have their answer built
and populated in the database. Answering (a)-or-(b) uniformly would have created a role for a fact the
schema already records.

### The measurement (re-taken from the authorities, not from the row)

Read from `iam.roles`, `iam.permissions` and the reflection dump — **611 guard sites across 38 packages**;
a `grep` is not admissible here, because these guards span source lines (F2-13a: 3 by grep, 73 by
reflection).

| module | perm module | ghost names (guard sites) | who actually holds it today |
|---|---|---|---|
| `sharia` | **none** | `SHARIA_OFFICER` 21 · `SHARIA_AUDITOR` 17 · `SHARIA_BOARD_CHAIR` 4 · `CRO` 13 | PLATFORM_ADMIN 21, COMPLIANCE_OFFICER 21, RISK_MANAGER 9, INTERNAL_AUDITOR 9 |
| `ethics` | **none** | `ETHICS_OFFICER` **22** · `AUDIT_COMMITTEE_MEMBER` 6 | PLATFORM_ADMIN 26, COMPLIANCE_OFFICER 26, BOARD_SECRETARY 11 |
| `openbanking` | **none** | `OPEN_BANKING_OFFICER` 15 · `CRO` 3 | PLATFORM_ADMIN 18, COMPLIANCE_OFFICER 18, RISK_MANAGER 9, CISO 9 |
| `aml` | **none** | `AML_OFFICER` 14 · `KYC_OFFICER` 6 · `ONBOARDING_AGENT` 5 | PLATFORM_ADMIN 14, COMPLIANCE_OFFICER 13, CISO 7 |
| `cpty` | **none** | `CRO` 14 · `CREDIT_OFFICER` 12 | PLATFORM_ADMIN 14, RISK_MANAGER 12, CFO 10 |
| `consumer` | **none** | `CONSUMER_PROTECTION_OFFICER` 9 | **PLATFORM_ADMIN 9 and COMPLIANCE_OFFICER 9 — nobody else at all** |
| `ai` | **`AI` (READ/WRITE/EXPORT)** | `MODEL_OWNER` 14 · `CIO` 11 · `CRO` 8 | RISK_MANAGER 23, CISO 20, DPO 19, AUDITOR 14 |

Three corrections to the row fall straight out of this:

1. **`ETHICS_OFFICER` is 22 sites, not 23** — D-13 removed one when `SUBMIT_GUARD` became
   `isAuthenticated()`. The row was right when written.
2. **`ai` does not belong in the seven.** It is the only one with a catalogued permission module, and its
   surviving population — risk manager, CISO, DPO, auditor — *is* a defensible AI-governance population.
   The row's own criterion is *"the module belongs to whoever else the guard lists — usually
   `COMPLIANCE_OFFICER`"*; in `ai`, `COMPLIANCE_OFFICER` appears on 5 of 26 sites. **So it is six modules
   with the F2-145 defect, and `ai` has a different one** (below).
3. **`cpty` is the mildest and `consumer` the most severe.** `cpty` loses *both* its domain names (`CRO`
   14, `CREDIT_OFFICER` 12) but keeps RISK_MANAGER and the CFO, so counterparty credit is at least held by
   risk. `consumer` has **no non-administrator holder except compliance on any of its 9 guards**: the SAMA
   consumer-protection module is compliance's, entirely, by attrition.

**And one fact that makes the cost of (a) much lower than assumed:** all **58** rows in `iam.roles` carry
`organisation_id = NULL` and `is_system_role = 1`. Roles here are **global**, not per-tenant — so seating
one is a single insert plus grants, not a per-tenant provisioning exercise. That was worth measuring
before choosing, because "seat eight roles across every tenant" and "insert eight rows" are different
decisions.

### Decision — classify by asking what the platform already models

| mechanism | the test | applies to |
|---|---|---|
| **1. Seat the role** | the ghost names a **job family** inside the institution, and nothing in the schema records who holds it | `SHARIA_OFFICER`, `SHARIA_AUDITOR`, `ETHICS_OFFICER`, `OPEN_BANKING_OFFICER`, `AML_OFFICER`, `KYC_OFFICER`, `ONBOARDING_AGENT`, `CONSUMER_PROTECTION_OFFICER`, `CREDIT_OFFICER` — **9 roles** |
| **2. Bind to the record** | the platform already stores **who, on this row** | `MODEL_OWNER` |
| **3. Seat on a body, not a role** | the person holds a **term-limited seat**, may be external, and is already a row | `SHARIA_BOARD_CHAIR` |

**Mechanism 2 — `MODEL_OWNER` is a category error, not a missing role.**
`ai_governance.ml_model.model_owner_user_id` **exists and is populated on 25 of 25 rows**, resolving to
real `iam.users` (`admin`, `ranalyst`). So the platform knows exactly who owns each model, per model, and
`hasAnyRole('MODEL_OWNER')` asks instead whether the caller belongs to a job family called MODEL_OWNER —
a question the data cannot answer and was never meant to. **The fix is an entity-bound guard against
`model_owner_user_id`**, which the platform already does at scale (`BASELINE_ENTITY_BOUND_WRITES` = 214).
Seating a `MODEL_OWNER` role would have been actively wrong: it would let the owner of model A act on
model B, which is looser than what the column supports today. ➕ Separately, `ai` has **4 APPROVE-shaped
methods and no `AI:APPROVE`** catalogued — noted for the conversion, not a live gap, since those guards
are role-based today.

**Mechanism 3 — the Sharia board is already a roster.**
`sharia.sharia_scholar` holds **5 rows** — `ssb_role` ∈ {`CHAIRMAN`, `MEMBER`×3, `SECRETARY`}, one
`TERM_EXPIRING`, with `term_start`/`term_end`, `credentials`, `coi_declared_at`, `annual_fee_sar`,
`meetings_attended_ytd` and institutional emails. **A Sharia board chair is a seat with a term, not a job
family** — and the table has **no `user_id` column**, which is [[F2-26]]'s roster→user linkage problem
exactly. So `SHARIA_BOARD_CHAIR` is not seated as a role; the scholar path binds to
`sharia_scholar.ssb_role = 'CHAIRMAN'` once that linkage exists, and until then it stays a **declared gap
rather than a fabricated role**. `SHARIA_OFFICER` and `SHARIA_AUDITOR` *are* job families — the
institution's internal Sharia-compliance and Sharia-audit functions, distinct from the external board —
and they are seated.

**The permission layer is orthogonal and still required.** Six of the seven modules have **no catalogued
permission module at all**, so ADR-010 conversion applies on top. Action sets derived from the guarded
method names rather than convention: `SHARIA` READ/WRITE/APPROVE · `ETHICS` READ/WRITE/APPROVE ·
`OPENBANKING` READ/WRITE/APPROVE · `AML` READ/WRITE/APPROVE · `CPTY` READ/WRITE (**no APPROVE-shaped
method exists**) · `CONSUMER` READ/WRITE/APPROVE = **17 permissions**. **No `EXPORT` for any of them** —
none has an export handler, and a catalogued permission guarding no endpoint is F2-13b's anti-pattern
(15 already exist).

### ⚠ The part that changes another ADR: behaviour-preserving conversion would ratify the defect

ADR-010 says conversion is *"behaviour-preserving: catalogue the permission, then grant it to exactly the
roles the old `hasAnyRole` list named, so day-one behaviour is identical. This is a refactor, not a policy
change — which is what makes it safe to do at scale."*

**That rule is correct in general and wrong for exactly this set.** "The roles the old list named" is
ambiguous in a way that only matters when a name is a ghost: it can mean *the names written in the
expression* or *the roles that could ever match*. For these six modules the two differ, and the
behaviour-preserving reading picks the second — so converting `consumer` would catalogue `CONSUMER:*` and
grant it to `PLATFORM_ADMIN` + `COMPLIANCE_OFFICER`, **permanently encoding in data the attrition that
F2-145 exists to report**. The guard's dead leg is at least evidence of intent; a grant table is a
decision.

**Amendment (carried into ADR-010 §Conversion):** *behaviour-preserving conversion is only safe where the
current behaviour is correct.* Where a `hasAnyRole` list names a role that does not exist, the conversion
must grant to the **intended** population — which the ghost name records — and the ghost must be resolved
by this decision's three mechanisms **before** the conversion, not dropped as part of it. This is the same
ordering D-13 needed: **bind the subject first, then move the guard.**

### What ships, in order

1. **A migration** seating the 9 roles (global, `is_system_role = 1`), cataloguing the 17 permissions, and
   granting them to the intended population — the ghost names plus the real ones already on each guard.
2. **The guard conversion** per module, `hasAnyRole` → `hasPermission`, which is F2-05's backlog and now
   has an approved grantee list to convert *to*.
3. **`MODEL_OWNER`** → entity-bound guard on `model_owner_user_id`. Not a role, not a permission.
4. **`SHARIA_BOARD_CHAIR`** → waits on [[F2-26]]'s roster linkage. Recorded as a gap, deliberately not
   filled with a role.

⚠ **`CRO`, `CIO`, `AUDIT_COMMITTEE_MEMBER` and `BOARD_MEMBER` are NOT in scope here.** They appear across
these modules (`CRO` on 13 sharia + 14 cpty + 8 ai + 3 openbanking) but they are platform-wide governance
identities, not any module's domain role — F2-52's own remaining queue, and `BOARD_MEMBER` /
`AUDIT_COMMITTEE_MEMBER` are [[F2-26]] seats by the same reasoning as the Sharia chair.

---

## D-15 — F2-141: the tables should **not** exist — and the population is **36, not 5**

**The decision asked for:** five tables (37 rows) are referenced by no Java code at all. Should they
exist? `reporting.report_definitions` looks like a superseded duplicate of `ndi.report_definition`.

**Answer: no, none of them should — and neither should the thirty-one the row does not mention.** The
lead is confirmed, and generalises further than it was stated.

### The population is an artifact of the instrument that found it

F2-141 found its five tables through a **bilingual column scan**: it could only see a dead table that
happens to carry an `*_ar` column beside a `NOT NULL` `*_en`. The committed register
(`Appendix/TABLE_DISPOSITION_REGISTER.csv`, generated by `table_xref.py`) already classifies every table
in both databases, and three of its codes mean *no JPA mapping and no Java reference anywhere*:

| code | tables | rows (dev = prod) | how the rows got there |
|---|---|---|---|
| `ORPHAN_CONFIRMED` | 10 | 0 | nothing ever wrote them |
| `SEED_ONLY` | 16 | 305 | the only writer is a Python seed script |
| `LEGACY` | 10 | 76 | a migration, or by hand |
| **total** | **36** | **381** | |

So the question is over **36 tables holding 381 rows the application cannot read** — present in
production because it was restored from a dev dump. This is F2-141's own lesson one level up: *an
instrument's population is narrower than its subject.* Its five are a subset, and not the interesting one.

### Verified twice, because a single scan is not evidence

The register's "no Java reference" claim was re-derived independently — a comment-stripped scan of 1,843
main + 352 test + 778 frontend files for the schema-qualified name and for `@Table`, with seven
anti-vacuity positives that must read REFERENCED. **36 of 36 clean.**

⚠ **The first run of that verification reported 5 of the 36 as REFERENCED, and the bug is the finding.**
The matcher took `@Table(name = "…")` without the schema, so it matched:

| dead table | matched the entity for | |
|---|---|---|
| `bcm_lc.bia_records` | `bcm.bia_records` | `BiaRecord` |
| `compliance.evidence_items` | `evidence.evidence_items` | `EvidenceItem` |
| `cyber_lc.cyber_incidents` | `cyber.cyber_incidents` | `CyberIncident` |
| `cyber_lc.threat_intel` | `socops.threat_intel` | `ThreatIntel` |
| `privacy_lc.cross_border_transfers` | `privacy.cross_border_transfers` | `CrossBorderTransfer` |

Every false positive is a **same-name, different-schema pair where one is live and one is dead**. A
schema-blind name check cannot tell them apart — and neither can a developer reading
`@Table(name = "bia_records")` without looking at the schema attribute. **That is why the twins have
survived**, and it makes one operational rule non-negotiable: any `DROP` here is schema-qualified, and
the schema is the whole content of the statement. (`table_xref.py` itself does resolve the schema,
including the `hibernate.default_schema` fallback — the hole was in the verification scan, not the
register.)

### What the 36 actually are: a first-generation data model that was superseded and never removed

Ten of the 36 have a **live, mapped successor whose name is a near-match in another schema**:

| dead | live successor |
|---|---|
| `reporting.report_definitions` (20 rows) | `ndi.report_definition` |
| `audit.audit_logs` | `platform.audit_log` |
| `bcm_lc.bia_records` | `bcm.bia_records` |
| `bcm_lc.drills`, `bcm_lc.recovery_strategies` | `bcm.*` (the live BCM module) |
| `compliance.compliance_assessments` | `ndi.compliance_assessment` |
| `compliance.evidence_items` | `evidence.evidence_items`, `ndi.evidence_item` |
| `cyber_lc.cyber_incidents` | `cyber.cyber_incidents` |
| `cyber_lc.threat_intel` | `socops.threat_intel` |
| `privacy.data_flows` | `dataclass.data_flow` |
| `privacy_lc.cross_border_transfers` | `privacy.cross_border_transfers` |
| `risk_lc.kri_breaches` (12 rows) | `risk.kri_breach` |

**The lead was right and understated.** `reporting.report_definitions` and `ndi.report_definition` share
only seven generic columns (`id`, `external_id`, `organisation_id`, `report_type`, `created_at`,
`created_by`, `is_deleted`); the live one adds `report_code`, `schedule_cron`, `recipients`, `language`,
`last_run_at/status/message/size_bytes` and a `status`. It is not a duplicate — it is a **generational
predecessor**, and the successor is strictly more capable. The `*_lc` schemas (`bcm_lc`, `cyber_lc`,
`privacy_lc`, `risk_lc`) are the same story: lifecycle packages created ahead of modules that were then
built properly elsewhere, which is exactly what `CLAUDE.md`'s landmine list already says about
`privacy_lc`.

### What decides each one is what a *customer's* database does with it

Not "does dev have it" — dev is Python-bootstrapped. Measured over all 350 migration files:

| verdict | tables | meaning |
|---|---|---|
| created by a migration at/below V292 | **33** | a baselined deployment skips the file forever, so the table is there only via the dump restore — but a **genuinely fresh install runs V1 upward and creates all 33, empty** |
| created by nothing at all | **3** | `compliance.compliance_assessments`, `compliance.compliance_findings`, `compliance.evidence_items` — **exactly [[F2-140]]'s three**, independently confirmed here, and F2-140's own decision was *no DDL* (V361 adds the 27 missing columns and deliberately creates none of the 3 tables), so a fresh install will not have them |
| created above the baseline | **0** | nothing recent ships dead schema |

And the ordering constraint, measured rather than assumed: **24 of the 36 have a writer** — 16 written by
one of six Python seed scripts (`seed_real_data.py`, `setup_bcm_db.py`, `seed_gov_compliance.py`,
`seed_gov_cyber.py`, `seed_gov_govdocs.py`, `seed_gov_reporting_wf.py`), 15 written by a migration
(V28, V120–V123), **7 by both**. **Dropping a table whose seed insert still exists breaks the bootstrap**,
so the writer goes first. Same ordering as D-13: bind the subject, then open the guard.

⚠ Several of those migration inserts cannot work anyway. This programme has measured that SQL Server's
deferred name resolution covers a missing *table* but not a missing *column*, so 7 of 7 "guarded" V121
demo inserts were compile-time failures whose `CATCH` had never printed. A migration writing to a table
the application cannot read is dead weight or a silent failure — never a feature.

### The decision, in three parts, and only two are available to engineering

| | | |
|---|---|---|
| **(a)** | **Do not ship a 37th.** ✅ **DONE** — three assertions in `SchemaContractRatchetTest`, DB-free: no migration above the baseline may **create** a table nothing maps (88 created above V292, 88 mapped, **hard zero**); none may **INSERT or ALTER** a table the application cannot read (**hard zero**); and the 36 are a **decreasing ratchet** which also catches the one path the hard gates cannot see — a table added by the Python bootstrap, where no migration exists to fail. |
| **(b)** | **Stop the seed scripts and V120–V123 writing to them.** Available without an owner decision; it is source, not data. Not in this slice — it is a separate, mechanical change and belongs with the drop it enables. |
| **(c)** | **`DROP` the 36.** ⛔ **Owner-authorised only** (house rule 6: destructive DB operations need explicit OK), even for the 10 that hold zero rows in both databases. Recommended order: the 10 `ORPHAN_CONFIRMED` first (nothing is lost, and it is the cheapest proof the drop mechanism is right), then the 16 `SEED_ONLY` **after (b)**, then the 10 `LEGACY` per-table, since their rows may be the only copy of something. |

**Why the ratchet is a ratchet and its two companions are hard zeros:** the 36 are historical debt whose
removal needs a decision engineering cannot make, so a red build there would be unfixable and would get
switched off. The forward-looking property needs no decision at all, so it is asserted at zero. Keep the
split — a ceiling tolerates the population it measures, which is why D-13 replaced the authorisation
ceiling with a pinned set. Here the register already names all 36 by name, and re-listing them in Java
would create the second authority RC-A exists to prevent.

**Mutation-proven 6 of 6**, and one mutation changed the code: injecting a scratch migration that creates
an unmapped, unregistered table failed on *unmapped* and never reached the register assertion, so the two
were split into separate `@Test` methods. Third time in this programme that two assertions in one test let
the first mask the second. The comment-stripping is load-bearing and measured as such: the identical
`INSERT INTO bcm_lc.drills` passes when commented out and fails when not.

### Live-proven, because "superseded" was otherwise an inference from two column lists — 7/7

Suite **3,364/0/0**. And the successor claim was made an *observation*: `GET /api/ndi/reports` returns
**12** rows whose ids are a subset of `ndi.report_definition`'s, not the dead table's 20; the JSON carries
`nameEn` and no `reportNameEn`; a row inserted straight into `reporting.report_definitions` is **invisible**
to the endpoint (12 rows, unchanged) and an id that exists only in the dead table **404s**; and the
converse control fires — a row inserted into `ndi.report_definition` appears immediately (12 → 13). Both
tables restored to 20/12 rows, residue clean, `CHARINDEX` deletes.

⚠ **Two of this probe's own legs were wrong first, both failing in the reassuring direction.** The org
came back `None` from the login response (which carries no `organisationId`), so the id-set query matched
nothing and leg 1 read as *"the endpoint does not serve ndi"* — the opposite of the truth. And a leg
probing `/api/v1/search` **passed while proving nothing**: there is no global search endpoint on this
platform, so the 404 body trivially contained no tag. It was replaced, not excused. *A check whose subject
does not exist returns the answer you were hoping for.*

Two more measurable signs of the generational gap surfaced while writing the probe:
`reporting.report_definitions.external_id` is a `uniqueidentifier` where the whole current platform uses
`NVARCHAR`, and its `report_type` carries **no CHECK constraint** while `ndi.report_definition` has four
(`report_type`, `format`, `language`, `status`).

**What this does not close:** F2-141's original subject, the five Arabic columns. Its own sharpened
recommendation — *do not map them* — stands, because mapping a bilingual column on a table no code reads
creates exactly what this programme keeps deleting. The bilingual baseline stays at 158 and the reason is
recorded in the test rather than in a commit message.

---

## Consequences

**Unblocked immediately:** Wave 2 (W2-0, F2-04) via D-1; S6/S7/S8/CE-13 via D-2 and D-3; F2-42, F2-49,
F2-53, and the fraud/integration/export tails. F2-144 via D-13, which also converts the authorisation
ratchet from a count ceiling to a pinned set. F2-145 via D-14 — which also amends ADR-010's conversion rule, because behaviour-preserving conversion of a guard whose behaviour is wrong ratifies the defect.

**Newly scheduled work this ADR creates**, in dependency order — each needs its own verified slice, none is
done by this document: the module-registry SKU reconciliation (D-1); per-tenant CAB configuration with
fail-closed transitions (D-2); the ECAB SLA clock and escalation (D-3); the board-pack JSON aggregate
(D-5); the migration-naming test plus renumber-or-delete (D-6); `KB` catalogue + grants (D-7);
~~`INTEGRATIONS` catalogue + grants and the remaining 20 guard conversions (D-8)~~ ✅ **V343, 15 of the 20 —
the other 5 are outbound actions deliberately left as role guards, see D-8's shipped block**;
`FRAUD_INVESTIGATOR` + grant (D-9); the EXPORT grant sweep under the new rule (D-10); audience-scoped
board-pack reads (D-11); `nca_ecc` `submittedBy` (D-12). Migrations will be needed for D-7, D-8, D-9 and
probably D-10.

**Still genuinely owner-only, and deliberately left so:**

| | |
|---|---|
| **O-1** | The **elevated restart**. A fresh V328 jar and SPA are staged and smoke-proven as of 2026-07-30; the prod DB is backed up and `RESTORE VERIFYONLY`-checked. Only an elevated shell can swap it. |
| **O-7** | **Commissioning** the penetration test, the witnessed restore/failover and the WCAG conformance statement. Procurement cannot be delegated to an engineer — but §7's listing them as "out of scope" **is withdrawn**: NCA ECC requires penetration testing, SAMA and NCEMA require BCM restore evidence, and a WCAG statement is table stakes for Saudi public-sector procurement. They move to the owner register as three named deliverables, and the engineering side will produce the **specifications** (pen-test scope + rules of engagement, restore-drill runbook + evidence template, WCAG 2.2 AA statement skeleton over the axe evidence already in `audit/`) so what remains for the owner is signing a vendor, not scoping a project. |
| **Pricing** | Every number. D-1 decides *paid vs Base*, never *how much*. |

**What would reopen this ADR:** a design partner refusing to pay for committee governance (D-1); a tenant
whose charter genuinely has no CAB-equivalent, which would make D-2's fail-closed rule a dead end rather
than a prompt; or a Saudi regulatory change to ECC-2 domain 2-2 or the CMA governance regulations.
