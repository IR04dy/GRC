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
| SCR-001 | Critical | **UPHELD** | **REPRODUCED** | Reproduced end to end, including the revocation bypass. See §2 |
| SCR-002 | High | **UPHELD, description wrong** | **PARTLY** | Effect is real; the report implies authorities are stale — they are re-loaded live. See §2 |
| SCR-003 | High | **UPHELD, scope narrower** | **PARTLY** | Overlaps F2-29, already in the plan. See §4 |
| SCR-004 | High | **UPHELD** | **CODE-CONFIRMED** | `/{id}/download` carries only `EVIDENCE:READ` |
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

| Leg | Result |
|---|---|
| Access token as bearer (control) | **200** |
| **Refresh token as bearer** | **200** — should be 401 |
| Logout (`204`), then the **revoked** refresh token as bearer | **200** — revocation bypassed |
| The revoked token against `/api/auth/refresh` | **401** — revocation *is* enforced, but only there |

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
| 1 | **Reject `jti`-bearing tokens on the bearer path** + close `AuthController:188`'s jti-less mint | SCR-001 interim | XS | — | — |
| 2 | **Enforce `enabled`/`accountNonLocked` in the filter; tenant from the loaded user** | SCR-002 | S | — | 1 (same file) |
| 3 | **Object-level authorization on attachment download** — caller must be able to read the parent entity; carry classification | SCR-004 | M | — | — |
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
| **SCR-004** (attachment object-level authz) | F2-27 closed *export* authorization (`MODULE:EXPORT` on 40 endpoints) and **explicitly allow-listed `AttachmentController#download`** as "a single object" | **INDEPENDENT, and it inherits an assumption worth revisiting.** F2-27's allow-list entry assumed single-object downloads were low risk. SCR-004 is the argument that "single object" was never the relevant question — *whose* object was |
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
| Report validated | **4 of 12 findings verified first-hand** (1 reproduced live, 3 code-confirmed), 4 partly, 8 unverified |
| Findings refuted | **0** |
| Corrections made to the report | 3 — SCR-001's rollout cost, SCR-002's mechanism, SCR-006's severity |
| Second opinion | **NOT OBTAINED** — Fable 5 blocked by cyber safeguards, twice |
| Code changed | **None.** This is a plan; no remediation has been implemented |
| Next action | Slice 1 (SCR-001 interim), pending the user's go-ahead on sequencing |
