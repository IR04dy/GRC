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
