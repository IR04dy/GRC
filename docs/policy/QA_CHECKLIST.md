# Policy Management Module — QA Checklist

Manual-QA gate for the Policy Management module (`module.policy`, schema
`policy.*`, migration V79). This document is walkable without source
access — every rule the service enforces has a matching item below.

The module owns three resources, each with its own state machine:

| Resource    | States                                                                 |
|-------------|------------------------------------------------------------------------|
| Policy      | DRAFT → REVIEW → APPROVED → PUBLISHED → EXPIRED \| ARCHIVED            |
| Exception   | REQUESTED → UNDER_REVIEW → APPROVED \| REJECTED → EXPIRED \| REVOKED   |
| Attestation | PENDING → ATTESTED \| DECLINED \| EXPIRED                              |

---

## Policy documents

### Lifecycle
- [ ] `POST /api/policy/documents` creates a row in DRAFT with version
      defaulted to `1.0` and `organisationId` from the JWT.
- [ ] Creating a second policy with the same `(policyCode, version)`
      pair within the same tenant is rejected with `already exists`.
- [ ] `POST /:id/transition` walks DRAFT → REVIEW → APPROVED →
      PUBLISHED in order. Backward moves DRAFT ← REVIEW and APPROVED →
      REVIEW are allowed; every other backward move returns 400 with
      `IllegalTransition`.
- [ ] Transitioning to `PUBLISHED` without `effective_date` set returns
      400 with `effective_date is required before publishing`.
- [ ] Successful PUBLISHED transition stamps `published_at` to "now"
      and never overwrites it on subsequent updates.
- [ ] APPROVED transition with an `actor` query/body parameter writes
      that name into `approver_name`.
- [ ] PUBLISHED → EXPIRED and PUBLISHED → ARCHIVED both succeed.
      EXPIRED → ARCHIVED succeeds; no other transition out of EXPIRED.

### Edit guard
- [ ] PUT on a DRAFT or REVIEW row updates every field passed in the
      payload.
- [ ] PUT on a PUBLISHED row only updates `nextReviewDate`, `notes`,
      and `retiredReason`. All other fields (title, body URL, owner,
      effective_date) are ignored.
- [ ] DELETE on a PUBLISHED row returns 400 with "Cannot delete a
      PUBLISHED policy — transition to ARCHIVED first".
- [ ] DELETE on any other status soft-deletes (`is_deleted = 1`) and
      the row disappears from list endpoints immediately.

### Versioning
- [ ] `POST /:policyCode/new-version?newVersion=1.1` clones the latest
      version's metadata into a new DRAFT row; `notes` field reads
      "Cloned from {code} v{oldVersion}".
- [ ] Re-using an existing `(policyCode, newVersion)` pair is rejected.
- [ ] `latestOnly=true` query parameter on the list endpoint returns
      one row per `policyCode` (the highest version).

### Expiry sweep
- [ ] `POST /run-expiry-sweep` flips every PUBLISHED row whose
      `expiry_date` is in the past to EXPIRED, returns the list of
      flipped IDs.
- [ ] Running the sweep twice in a row returns `[]` the second time.

## Policy exceptions

- [ ] `POST /exceptions` requires `policyId`, `titleEn`, and
      `justificationEn`; missing any returns 400.
- [ ] Defaults applied at create: status=REQUESTED, riskLevel=MEDIUM,
      requestedAt=now.
- [ ] `transitionException`: REQUESTED → UNDER_REVIEW stamps
      `reviewer_name` (from actor) and `reviewed_at`.
- [ ] UNDER_REVIEW → APPROVED stamps `approver_name` and `approved_at`.
- [ ] Any → REJECTED requires a non-blank `reason`; rejection stamps
      `rejected_at` and `rejection_reason`.
- [ ] APPROVED → EXPIRED and APPROVED → REVOKED both succeed; no other
      transition from APPROVED is allowed.
- [ ] `runExceptionExpirySweep` flips APPROVED exceptions whose
      `expiry_date` is past to EXPIRED.

## Policy attestations

- [ ] `POST /attestations` requires `policyId` and `userUsername`;
      missing either returns 400. Status defaults to PENDING.
- [ ] `POST /:id/attest` only works on PENDING rows. Calling on an
      already-ATTESTED row returns 400 with "Only PENDING attestations
      can be attested (was ATTESTED)".
- [ ] Successful attest stamps `attestedAt` and optionally writes
      `evidenceRef` from the payload.
- [ ] `POST /:id/decline` only works on PENDING; requires a non-blank
      reason; stamps `declinedAt` and writes `declinedReason`.

## Aggregate endpoint

- [ ] `GET /aggregate` returns `{total, published, inReview, draft,
      expired, expiringSoon, openExceptions, approvedExceptions,
      pendingAttestations, byCategory}`.
- [ ] `expiringSoon` counts only PUBLISHED rows with `expiry_date`
      between today and today+60 days inclusive.
- [ ] `openExceptions` = count(REQUESTED) + count(UNDER_REVIEW).
- [ ] `byCategory` is keyed by the `category` column (case-sensitive).

---

## Cross-cutting checks

### Tenant isolation
- [ ] Every list/get/update endpoint filters by `organisation_id` from
      the JWT — no row from another tenant appears in any response.
- [ ] All three entities carry `is_deleted` + a `@Where(clause =
      "is_deleted = 0")` annotation; JPA reads never return
      soft-deleted rows.

### Bilingual content
- [ ] `titleEn` / `titleAr`, `summaryEn` / `summaryAr`,
      `justificationEn` / `justificationAr`, and
      `compensatingControlsEn` / `compensatingControlsAr` are all
      readable and writable independently.
- [ ] AR strings follow Saudi-native phrasing per the MEMORY.md note
      (no literal translations).
- [ ] RTL layout flips correctly on every policy tab when the user
      switches to Arabic.

### Bean qualification
- [ ] The bean is registered as `policyManagementService` so it does
      not clash with the older `module.compliance.PolicyService`.

### Tests
- [ ] `mvn test -Dtest='com.tss.grc.module.policy.service.PolicyServiceTest'`
      passes. The test class locks:
      - lifecycle transitions (effective_date guard, illegal moves)
      - DELETE-of-PUBLISHED refusal
      - exception approval / rejection rules
      - attestation attest / decline rules
      - expiry sweep
      - duplicate-version rejection

### Documentation
- [ ] STATUS.md "Policy Management" section is current with the
      release date.
- [ ] CLAUDE.md "Backend module structure" lists `policy` among the
      modules and documents the `policy.*` schema.

---

Last updated: alongside Sprint 2 close-out + Policy unit tests.
