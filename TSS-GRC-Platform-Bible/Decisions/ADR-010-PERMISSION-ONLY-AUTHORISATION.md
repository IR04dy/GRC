# ADR-010 — Permission-Only Authorisation in Module Code

**Status:** Proposed
**Date:** 2026-07-25
**Relates to:** `ADR-006` (approvals use immutable authenticated user IDs).

## Context

Two authorisation idioms coexist and only one is enforced.

**27** permission modules are referenced through `hasPermission(MODULE, ACTION)`, evaluated by a single
`ModulePermissionEvaluator`. But **21 of 58** module packages contain no `hasPermission` call at all;
they guard with hard-coded role lists — `hasAnyRole('PLATFORM_ADMIN','COMPLIANCE_OFFICER','SHARIA_OFFICER',
'SHARIA_BOARD_CHAIR','CRO')` and similar, including `socops` with 58 write endpoints.

The consequence is commercial, not stylistic: the platform's per-tenant RBAC (55-role persona matrix,
`V272`) has **no effect** on those 21 modules. A customer who creates the role "Sharia Reviewer" and
grants it `SHARIA_WRITE` still receives 403, because the code compares role *names*. Least privilege is
unachievable, and a module sold standalone cannot be administered by its buyer.

The authorisation ratchet (currently 21) accepts `hasAnyRole`, so it measures **coverage** and never
**tightness**. A separate observation: `WORKFLOW_WRITE` is granted to 55 of 57 roles including `VIEWER`,
so an endpoint can be guarded and effectively unrestricted.

## Decision

`hasAnyRole` / `hasRole` are **forbidden in `module/**`**. Every write endpoint is guarded by
`hasPermission(MODULE, ACTION)` against a catalogued permission. Role→permission mapping lives in data
(grants), never in code. `PLATFORM_ADMIN` bypass remains, in the evaluator only.

Conversion is behaviour-preserving: catalogue the permission, then grant it to exactly the roles the old
`hasAnyRole` list named, so day-one behaviour is identical. This is a refactor, not a policy change —
which is what makes it safe to do at scale.

Two ratchets, both monotonic: **coverage** (unguarded write endpoints — at 21, enumerated floor) and
**tightness** (`hasAnyRole` occurrences in `module/**` — to zero). A third report, not yet a ratchet:
roles-per-permission, flagging any business-write permission held by more than half of roles or by any
read-only, external or machine persona.

## Sanctioned exceptions — universal-obligation endpoints

*Added 2026-08-06 (F2-144b), generalising the ruling ADR-017 D-7 made for knowledge-base reads.*

The rule above has exactly one carve-out, and it is narrow enough to enumerate: **an endpoint that
discharges an obligation every employee is under is guarded by `isAuthenticated()`, not by a permission.**

The reasoning is asymmetric risk, not convenience. A grant list can only ever wrongly **exclude** someone.
For most endpoints that is the safe direction — a practitioner who cannot reach a screen files a ticket.
For an endpoint that is how a person *complies with a duty*, exclusion is the expensive failure: the
obligation still exists, the platform records no discharge of it, and the resulting gap looks like the
employee's fault rather than the grant list's. `isAuthenticated()` cannot wrongly exclude an employee.

**Admission requires all three:**

1. **Universal duty or protection.** The obligation falls on every employee, not on a job family. Judged
   by reading what the endpoint actually serves — D-7's amendment was written after reading the knowledge
   base's 22 articles, which refuted the decision this ADR's own author had reached from the finding's
   framing.
2. **The server binds the subject.** The handler resolves *whose* record this is from the authenticated
   principal, never from the request body. **This is the load-bearing condition**: without it, an open
   guard is an impersonation path, and it is where F2-144 nearly went wrong — part (a) had to bind the
   declarant before part (b) could open the guard.
3. **Named and pinned.** Listed in `ControllerAuthorizationRatchetTest`'s sanctioned floor with its
   reason, plus a per-controller test asserting the expression, so a later uniformity sweep cannot
   "correct" it and so the exception is reviewable in one place.

**Current members — two, and each has a companion test:**

| Endpoint | Duty | Subject binding | Pinned by |
|---|---|---|---|
| `KbArticleController#listPublished` / `#bySlug` (read) | Every employee must be able to find out how to report wrongdoing, declare a conflict, or activate continuity — the corpus is whistleblowing/Nazaha/PDPL/NCEMA procedure | n/a (no subject; published + visibility filtered, drafts 404) | `KbArticleGuardTest` |
| `CoiController#submitDeclaration` (write) | Declaring a conflict of interest is an obligation on every employee under a Nazaha-aligned COI campaign | `CoiService#declarantFor` — the server picks the declarant; the body's `userId` is honoured only for holders of a COI management authority | `CoiSubmitGuardTest` + `CoiDeclarantBindingTest` |

**Not an exception, and worth stating because both were considered:** a *widely granted* permission
(`WORKFLOW_WRITE` sits with 55 of 57 roles) is not this — it is an over-grant to tighten, and it still
answers to the catalogue. Nor is a *self-scoped* endpoint automatically one: `SavedViewController#create`
resolves the row from the caller and is on the tolerated floor, but nobody is under a duty to save a view,
so it needs no ADR standing — it is simply an unconverted write.

**The transferable point:** *"convert role guards to permissions"* is a strong default, not a law. The
counter-examples are found by reading what an endpoint serves, never by reading its guard — and both
members here were found that way, one after the guard it replaced had already been written.

## Alternatives

- **Leave role guards, document the limitation:** rejected. It is a functional gap in the core value
  proposition, not a documentation gap.
- **Map roles → permissions at runtime inside the evaluator:** rejected. Hides the coupling instead of
  removing it, and leaves the catalogue incomplete, so no customer can see what is grantable.
- **Rebuild least privilege from scratch per persona:** the right eventual goal; the roles-per-permission
  report is the cheap first step that shows where it matters.

## Consequences

~21 migrations and ~150 annotation edits, parallelisable per module, each verified by a two-user live
probe (permitted role 200, `viewer` 403). Completes the RBAC promise and removes a standing enterprise
security-questionnaire failure. Tightening over-broad grants afterwards may affect existing users, so
each grant change needs a per-tenant impact query first.

## Proof

Tightness ratchet at zero; `viewer` receives 403 on every converted write endpoint while the previously
named role receives 200; a tenant-defined custom role granted `<MODULE>_WRITE` can perform the write —
the test that fails today for 21 modules.
