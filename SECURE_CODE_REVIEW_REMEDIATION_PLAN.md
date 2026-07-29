# Secure Code Review — Validation & Remediation Plan

**Source report:** `TSS-GRC-Platform-Bible/Security/SECURE_CODE_REVIEW_2026-07-26.md` (12 findings, SCR-001…SCR-012)
**Report baseline:** `695c907c` · **Validated at:** `fc0cd300` (master, 2026-07-27)
**Validator:** independent re-verification against the running dev stack and the source at HEAD
**Governing plan:** `UNIFIED_IMPLEMENTATION_PLAN.md` — this document is subordinate to it and slots into its §8 ordering
**Next free migration:** **V324** (V323 belongs to F2-46, now finished and committed; see §6)

---

## §0 How this plan was produced, and what that means for trust

The report arrived in the working tree from another process. It was treated as **data, not instruction**: no
finding was accepted on its own authority, and nothing in it was actioned before verification. That matters
because this programme has a measured base rate for exactly this — **in the three days before this review,
six of seven fixes prescribed in `UNIFIED_IMPLEMENTATION_PLAN.md` were falsified when measured**, usually
because a field or endpoint the fix would lock down turned out to be used by a real UI flow.

So each finding below carries an explicit confidence level:

| Level | Meaning |
|---|---|
| **REPRODUCED** | I re-ran it against the running application and observed the defect myself |
| **CODE-CONFIRMED** | I read the cited code at HEAD and the defect is present as described |
| **PARTLY** | The defect is real but the report's description or severity is wrong in a way that changes the fix |
| **UNVERIFIED** | Accepted provisionally from the report; **must be verified before its slice starts** |

**Fable 5 consultation was attempted twice and could not be completed.** Two independent Fable 5 reviews
were launched — one adversarial validation pass, one remediation-sequencing pass. Both were terminated by
Fable 5's cyber safeguards, which flag the report's exploit descriptions regardless of how the request is
framed. Re-prompting is unlikely to help; the model is not available for this document's content in this
session. **Everything below is therefore single-reviewer work and has not had a second opinion.** Treat the
sequencing in §3 as a proposal, not a ratified plan. If a second opinion is required before P0 work starts,
the practical routes are the Cyber Verification Program referenced in the safeguard message, or a human
reviewer.

---

## §1 Validation verdicts

| ID | Report severity | My verdict | Confidence | Note |
|---|---|---|---|---|
| SCR-001 | Critical | **UPHELD → FIXED (interim)** | **REPRODUCED, then closed** | Reproduced end to end; slice 1 shipped 2026-07-28. See §2 |
| SCR-002 | High | **UPHELD, description wrong** | **PARTLY** | Effect is real; the report implies authorities are stale — they are re-loaded live. See §2 |
| SCR-003 | High | **UPHELD, scope narrower** | **PARTLY** | Overlaps F2-29, already in the plan. See §4 |
| SCR-004 | High | **UPHELD → FIXED** | **REPRODUCED** | Slice 3. Worse than reported: a caller 403'd on every route to a record could also **write into it** (201). See §3 |
| SCR-005 | High | UPHELD | **UNVERIFIED** | Needs a live check of nginx access logging + the token store |
| SCR-006 | High | **UPHELD, severity over-rated** | **CODE-CONFIRMED** | Correct as written; blast radius is 3 roles, not "global". See §2 |
| SCR-007 | High | UPHELD | **UNVERIFIED** | Needs a browser check of `localStorage` after logout |
| SCR-008 | High | UPHELD | **UNVERIFIED** | Absence-of-control claim; verify no scanner hook exists before accepting |
| SCR-009 | High | UPHELD | **UNVERIFIED** | Re-run `npm audit --omit=dev`; advisories move |
| SCR-010 | Medium | UPHELD | **UNVERIFIED** | — |
| SCR-011 | Medium | **PARTLY — partly describes deliberate design** | **PARTLY** | Two of the "fail-open contexts" are documented exemptions added 2026-07-26. See §2 |
| SCR-012 | Medium | UPHELD (conditional) | **UNVERIFIED** | Only bites if CCM custom SQL is enabled |

**No finding was refuted.** That is a meaningfully better hit rate than this programme's own plans have
managed, and it is the main reason to take the report seriously.

---

## §2 The four findings I verified myself

### SCR-001 — Critical — REPRODUCED, and the fix is cheaper than the report says

Reproduced against the running dev stack, printing status codes only and retaining no token:

| Leg | Before | After slice 1 |
|---|---|---|
| Access token as bearer (control) | **200** | **200** — unchanged |
| **Refresh token as bearer** | **200** — should be 401 | **401** |
| Logout (`204`), then the **revoked** refresh token as bearer | **200** — revocation bypassed | **401** |
| The revoked token against `/api/auth/refresh` | 401 | 401 |

⚠ **Correction to the original validation.** That last row was reported as "revocation *is* enforced, but
only there". It proved nothing at the time: the pre-fix probe used Python's `http.cookiejar`, and the
refresh cookie is set `HttpOnly; Secure; Path=/api/auth/refresh`, so a Secure cookie is **never sent over
`http://localhost`** — the 401 was an absent cookie, not a rejected token. The same artifact then made the
post-fix probe look like a refresh-flow regression. Re-run with the cookie parsed from `Set-Cookie` and
sent by hand, rolling revocation is now genuinely proven (a superseded token is refused at the refresh
endpoint, and so is a logged-out one). **The three legs that mattered were never affected** — they used the
`Authorization` header, which works fine over HTTP, and the refresh-token-as-bearer defect was real.

Cause, read at HEAD: `JwtTokenProvider.buildToken` and `buildTokenFromIds` mint access and refresh tokens
through the same builder, differing only in `expiryMs`. `validateToken` returns true for any correctly
signed, unexpired JWT, and `JwtAuthenticationFilter` authenticates anything it approves. Claim sets measured
live:

```
access : enabled_modules, exp, iat, lod_band, org, roles, sub, usr
refresh: enabled_modules, exp, iat, lod_band, org, roles, sub, usr, jti
```

**The report's rollout advice is more expensive than necessary.** It says "existing sessions must be
invalidated during rollout". They need not be: **the refresh token already carries `jti` and the access
token does not**, so the bearer filter can reject any token presenting a `jti` — no token-format change, no
forced re-login, deployable in one commit.

⚠ **One hole must close with it.** `AuthController:188` (`generateRefreshTokenFromClaims`, the fallback
branch of the rotation path) mints a refresh token with **`jti = null`**. A token from that branch would
pass a `jti`-based filter. So the interim mitigation is only sound if that branch is removed or made to
issue a `jti` in the same commit. Establish first whether the branch is reachable in production.

**Recommended shape.** Ship both layers, in this order:
1. **Interim (hours):** bearer filter rejects tokens carrying `jti`; close the `AuthController:188` branch.
   Add the negative test. This alone closes the reproduced attack.
2. **Durable (2–4 days, as the report estimates):** an explicit immutable `typ` claim (`ACCESS`/`REFRESH`),
   separate validation entry points, and a check that a bearer's user is still active. Forces re-login, so
   schedule it with a release rather than a hotfix.

### SCR-002 — High — UPHELD, but the report's description is wrong in a way that matters

The report says bearer authentication "ignores disabled/locked state and does not bind the JWT tenant to the
live user". The second half is exactly right. The first half is right in *effect* but wrong in *mechanism*,
and the difference decides the fix:

- `JwtAuthenticationFilter:53` calls `userDetailsService.loadUserById(userId)` and authenticates with
  `userDetails.getAuthorities()`. **Authorities are re-loaded from the database on every request** — so a
  revoked role takes effect immediately. Any fix premised on "the token's roles are stale" would be solving
  a problem that does not exist.
- `CustomUserDetailsService.buildUserDetails` *does* compute `currentlyLocked` (honouring `lockedUntil`) and
  passes it into `GrcUserDetails`. The state is loaded correctly.
- **The defect is that nothing consumes it.** The filter constructs a `UsernamePasswordAuthenticationToken`
  directly, bypassing the `enabled` / `accountNonLocked` checks a `DaoAuthenticationProvider` would apply.
- **Tenant:** `TenantContext.setCurrentTenant(orgId)` takes `orgId` from the **token's** `org` claim, not
  from the freshly-loaded user.

**Fix:** in the filter, reject when `!userDetails.isEnabled() || !userDetails.isAccountNonLocked()`, and set
the tenant from the loaded user with the token claim used only to detect a mismatch (which should 401). Small
and low-risk. ⚠ Check the org-switcher flow first — it re-issues a token with a different `org`, so
"token org must equal user's home org" would break it unless the switch updates the user record.

### SCR-006 — High — CODE-CONFIRMED as written; severity over-rated

I expected this to be stale, because `POST /committees/resolutions/{id}/votes` is guarded
`hasPermission('ADMIN','APPROVE')` — not `ADMIN_WRITE` — after D-7/ADR-014. **I was wrong.** The endpoint
guard is `APPROVE`, but the *on-behalf capability* is decided separately by
`CommitteeResolutionController.mayRecordOnBehalf`, which returns true for `ROLE_PLATFORM_ADMIN` **or
`ADMIN_WRITE`** (line 145). So a holder of both can record a vote as any member of any committee, exactly as
the report describes.

**But the blast radius is smaller than "global".** Measured: `ADMIN:APPROVE` is held by 15 roles,
`ADMIN:WRITE` by **3**. The intersection that can actually do this is at most 3 privileged roles. That is a
**P2 design defect, not a P1 exposure** — the design is wrong (on-behalf authority should be *secretary or
chair of that committee*, which the roster already models per F2-26) but almost nobody can exercise it.

### SCR-011 — Medium — PARTLY; it partly describes a deliberate design decision made the same day

`TenantWriteGuardInterceptor` gained an `onSave` insert leg on 2026-07-26 (F2-22b(i)), and that leg has
**two documented, deliberate pass-through cases**: a `null` `organisationId` (a NOT NULL violation about to
happen, which the database reports far better) and **no tenant on the thread** (schedulers and sweeps run
off-request where `TenantContext` is unset; throwing there would break every sweep, in production only).
If those are the "fail-open execution contexts", they are decisions with recorded reasoning, not defects —
and the report may be describing the pre-change class rather than HEAD.

The "over-broad exception classification" half is fair: the guard wraps its audit call in
`catch (RuntimeException ignored)`. **Verify which of the two the report means before opening a slice.**

---

## §3 Remediation sequencing

Ordered by dependency, not severity. Each row is one commit-per-verified-slice per CLAUDE.md house rule 1,
live-probed per house rule 4.

| # | Slice | Covers | Size | Migration | Depends on |
|---|---|---|---|---|---|
| 1 | ✅ **DONE 2026-07-28** — **reject `jti`-bearing tokens on the bearer path** + close `AuthController`'s jti-less mint. `JwtTokenProvider.isRefreshToken`, a guard in `JwtAuthenticationFilter` (401, logged at WARN, no distinct status code), the store-absent rotation branch now mints a UUID `jti`, and `generateRefreshTokenFromClaims` **deleted** rather than deprecated — it was a one-line way back to the vulnerability. `JwtTokenPurposeTest` 7/7 pins the invariant in **both** directions (every refresh path marks, no access path does). Live **16/16**: the attack returns 401, and the refresh flow still rotates and chains twice. **No forced re-login, no token-format change** | XS | — | — |
| 2 | ✅ **DONE 2026-07-29** — **the tenant half is now REPRODUCED, not merely upheld.** A `viewer` token re-signed with `org=2` (HS384, dev secret — a *correctly signed* claim the server cannot distinguish from one it issued) read **another tenant's risk register: 200**. Mutation-proven both ways: restore `tenantId = orgId` and the forged token is 200; with `resolveTenant` it is 401. Liveness likewise live-proven — deactivate or lock the account mid-session and the **same** token flips 200 → 401, where it previously worked until expiry (up to an hour). ⚠ **The org-switcher trap in the finding was real and is why this was not a one-liner:** `POST /api/v1/platform/organisations/switch/{id}` is `hasRole('PLATFORM_ADMIN')`, re-issues a token with a different `org` and deliberately never touches the user row — so the obvious "token org must equal user org → 401" would have 401'd the first request after every switch. The rule shipped is *tenant from the loaded user, except a PLATFORM_ADMIN, for whom the claim is an authorised override logged at INFO; any other mismatch 401s*. Both branches asserted. Rejects go through one `reject()` that clears the SecurityContext **and** TenantContext — the early `return` skips the `finally` that normally clears the tenant, and servlet threads are pooled. `JwtFilterLivenessTest` 6/6, live **8/8** | S | — | 1 (same file) |
| 3 | ✅ **DONE 2026-07-29** — **REPRODUCED, and the report under-stated it.** `ranalyst` is 403 on *every* BCM record route (`/plans`, `/bia-assessments`, `/crisis-events`, `/scenario-library`) yet read a BCM attachment's **bytes (200)**, its **list (200)**, its **history with filenames and SHA-256s (200)** — and **uploaded a file into that invisible record (201)**. The report described disclosure; the same hole took writes. An unknown `entityType` answered **200 + empty list**, an oracle for guessing types and ids. Mutation-proven both ways (bypass restored → every leg 200/201; fix → 403). **The rule shipped:** an attachment inherits its parent record's own read guard, via a 57-entry `AttachmentAccessPolicy` registry read off each parent controller — the platform guards records three different ways (`hasPermission(MODULE,…)`, `hasAnyRole(…)` for the Saudi/financial modules, `isAuthenticated()` for a few), so a single-style rule would have locked out the owners of whole registers. **Two near-misses, both caught by measuring:** (a) requiring the parent's *WRITE* for uploads broke the evidence-custodian personas the platform ships (`auditor` = EVIDENCE_WRITE + BCM_READ, no BCM_WRITE) — reverted to *visibility*, which is what the finding is about; (b) the cyber workspace serves the **same** `PolicyDocument` rows under `CYBER:READ`, so the first mapping would have 403'd a CISO on their own screen — a guard is a **union over every route that serves the record**. Checked in the controller for the two path-keyed endpoints and in the service for the id-keyed ones, deliberately **not** in `AttachmentService.list`, whose internal callers include a role-guarded obligation evidence-package that would then have 403'd for CISO and BCM_MANAGER. Fails closed on an unknown type **including for PLATFORM_ADMIN**, so a registry gap fails for whoever tests it first rather than only for the customer. `AttachmentEntityTypeCoverageTest` scans the SPA on every build and fails if a new `entityType` has no entry. 15 tests, live **29/29** | M | — | — |
| 4 | **Public-token hygiene** — hash tokens at rest, strip query-string credentials from nginx logs, add expiry | SCR-005 | M | **V324** | verify first |
| 5 | **`typ` claim + split validation entry points** | SCR-001 durable | M | — | 1, 2 |
| 6 | **Per-committee on-behalf authority** (chair/secretary of *that* committee, from the F2-26 roster) | SCR-006 | S | — | — |
| 7 | **Browser-storage hygiene** — clear tenant-scoped state on logout, namespace by org | SCR-007 | S | — | verify first |
| 8 | **Upload content gate** — signature/magic-byte validation, size caps, scanner hook | SCR-008 | M | — | verify first |
| 9 | **Frontend advisories** — `npm audit` triage and upgrade | SCR-009 | S | — | re-run audit |
| 10 | **Rate limiting** — trust `X-Forwarded-For` only from the known proxy hop; shared counter | SCR-010 | S | — | — |
| 11 | **Tenant-guard exception narrowing** | SCR-011 | XS | — | clarify scope |
| 12 | **CCM custom SQL** — keep disabled; add an allow-list before it is ever enabled | SCR-012 | S | — | — |

Slices 1 and 2 touch the same two files and should be one branch, two commits. Slice 5 forces re-login and
belongs to a release, not a hotfix.

---

## §4 Collisions with the existing plan — do not do this work twice

| SCR | Existing plan item | Verdict |
|---|---|---|
| **SCR-003** (`CHANGE:WRITE` reaches approval states; forged PIR reviewer) | **F2-29** — "split `POST /{id}/transition` into named commands with state-specific permissions; `CAB_APPROVED`/`CAB_REJECTED`/`EMERGENCY_APPROVED` require `CHANGE:APPROVE`" | **DUPLICATE — F2-29 owns it.** Fold the report's PIR-reviewer-provenance detail into F2-29's scope and close SCR-003 against it. Note F2-29 is already sized M and sequenced |
| **SCR-006** (global permission grants on-behalf voting) | **F2-13b** — tighten over-broad grants | **OVERLAPS.** F2-13b owns the *grant breadth*; slice 6 above owns the *design* (per-committee authority). Both needed; do the design fix first, since narrowing `ADMIN:WRITE` alone leaves the model wrong |
| **SCR-004** (attachment object-level authz) | F2-27 closed *export* authorization (`MODULE:EXPORT` on 40 endpoints) and **explicitly allow-listed `AttachmentController#download`** as "a single object" | **SETTLED 2026-07-29 — the allow-list was wrong.** "A single object" was never the relevant question; *whose* object was. The allow-listed endpoint served the bytes of records the caller was 403'd on, and its sibling endpoints accepted writes into them. F2-27's reasoning generalises badly: object *count* is not a proxy for sensitivity |
| **SCR-002** (tenant not bound to live user) | F2-22b(i) — cross-tenant *write* backstop | **COMPLEMENTARY.** That work guards writes at the Hibernate layer; this guards the tenant at the authentication layer. Neither substitutes for the other |
| SCR-001, 005, 007–012 | — | **INDEPENDENT** |

---

## §5 What I did not verify, and would check first

For each **UNVERIFIED** row in §1, the check that decides it:

1. **SCR-005** — request a public-token URL and read the nginx access log; then check whether the token
   column stores a hash or the raw value. Two greps and one request.
2. **SCR-007** — log in, log out, enumerate `localStorage` keys in the browser. This is the cheapest of all
   of them and the platform has an in-app browser available.
3. **SCR-009** — `npm audit --omit=dev` in `tss-grc-ui-v2`. The report's counts were true on 2026-07-26 and
   advisory data changes; re-run before sizing.
4. **SCR-008** — an absence claim, and absence claims from a review are the ones this programme has most
   often found wrong (three of my own "does not exist" claims were incorrect). Grep for any scanner or
   content-type hook before accepting that none exists.

**Beyond the report.** Its own "Not performed" list omits three checks worth more than several of its
Medium findings: the **backend** dependency scan (only the frontend was audited), the **WebSocket**
subscription-isolation test under a real multi-tenant session, and whether the **prod** `external.properties`
carries a distinct `JWT_SECRET` from dev — a shared secret would make SCR-001 exploitable across
environments.

---

## §6 Migration numbering

**Resolved 2026-07-28.** `V323__ndi_cycle_score_provenance.sql` was applied to dev but uncommitted when this
plan was written, belonging to an unfinished F2-46 slice. **F2-46 is now finished and committed** — the NDI
cycle compliance score is derived from its assessment rows, stamped `COMPUTED`, and `READ_ONLY` on the entity,
with the one pre-existing asserted value (68.00) kept and labelled `ASSERTED_LEGACY`. **SCR work starts at
V324** and nothing is blocked.

---

## §7 Status

| | |
|---|---|
| Report validated | **4 of 12 findings verified first-hand** — now **3 reproduced live** (SCR-001, SCR-002's tenant half, SCR-004), 1 code-confirmed, 4 partly, 8 unverified |
| Findings refuted | **0** |
| Corrections made to the report | 4 — SCR-001's rollout cost, SCR-002's mechanism, SCR-006's severity, and **SCR-004's severity is under-stated**: that surface accepted writes into records the caller could not see, not only reads |
| Second opinion | **NOT OBTAINED** — Fable 5 blocked by cyber safeguards, twice |
| Code changed | **Slices 1–3 shipped.** Slice 1 (2026-07-28) closed SCR-001's reproduced attack; slice 2 (2026-07-29) closed SCR-002 — account liveness on the bearer path, tenant bound to the loaded user; slice 3 (2026-07-29) closed SCR-004 — attachments inherit their parent record's read guard. ⚠ Slices 2 and 3 are **committed on `fix/scr002-filter-liveness` and `fix/scr004-attachment-object-authz`, NOT merged** (both `/loop` invocations omitted "then merge", so house rule 2 applies). Slices 4–12 not started |
| Next action | **Merge slices 2 and 3** when the user says so, then slice 4 (SCR-005 public-token hygiene) — and §5's first check comes before any sizing there, because half that finding is an infrastructure claim about the nginx log format. Two decisions now wait on the user; see §8 |

---

## §8 Decisions this work surfaced that are not mine to make

| # | Decision | Why it is blocked on you | What I did meanwhile |
|---|---|---|---|
| D-SCR-1 | **What `RESTRICTED` actually means.** SCR-004 also asks for classification clearance. There is no clearance model anywhere in the schema — `iam.users` has no such column and neither does any other table (an `INFORMATION_SCHEMA` search for `%clearance%` returns nothing). Enforcing it means inventing an access dimension and deciding which of the 55 personas clears each of PUBLIC / INTERNAL / CONFIDENTIAL / RESTRICTED | Guessing locks people out of their own evidence, and a wrong guess stays invisible until a customer hits it. This is a policy decision about who may see what, not a code decision | Left the label advisory and said so plainly in the code, rather than shipping a gate that looks like enforcement. For scale: 1 of 20 dev attachments carries a classification at all (`PUBLIC`), so nothing is protected by it today either way |
| D-SCR-2 | **Whether `EVIDENCE:WRITE` should let someone attach a file to a record they can only read.** The shipped rule requires *visibility* of the parent, not ownership — because requiring the parent's WRITE broke `auditor` (EVIDENCE_WRITE + BCM_READ) and `so_officer` (EVIDENCE_WRITE + RISK_READ), the evidence-custodian personas the platform ships | Tightening it removes a capability those personas have today; leaving it means a custodian can file evidence against records they cannot edit. Both answers are defensible and the choice belongs with whoever owns the permission matrix | Kept the capability, bounded it to records the caller can see, and noted that every row stamps `uploaded_by`, so a planted file is attributable rather than anonymous |
