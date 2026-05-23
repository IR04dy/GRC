# Mass-Assignment Defense Pattern

**Status**: Implemented per-service (PR #121 + #123 — 19 services patched). Sprint-6 proposes a platform-wide AOP backstop.

## The vulnerability

Spring controllers that accept JPA entities directly via `@RequestBody` are vulnerable to a class of IDOR / cross-tenant write attack via JPA's `merge()` semantics.

### Attack path

1. Authenticated tenant **A** sends:
   ```json
   POST /api/v1/committees/{id}/actions
   { "id": 12345, "actionCode": "ATTACK", "ownerName": "x", ... }
   ```
   where row 12345 belongs to tenant **B**.

2. The service overwrites the inbound entity's tenant stamp:
   ```java
   in.setOrganisationId(TenantContext.getCurrentTenant());  // = A
   ```

3. The service calls `repo.save(in)`:
   ```java
   repo.save(in);   // in.id != null
   ```

4. Hibernate routes through `entityManager.merge()`:
   ```sql
   SELECT * FROM committee_action_items WHERE id = 12345;
   ```
   The row is found (it belongs to B). Hibernate then copies fields from the detached `in` onto the managed entity — **including the just-set `organisationId = A`**.

5. The managed entity is then flushed:
   ```sql
   UPDATE committee_action_items
   SET organisation_id = A, action_code = 'ATTACK', ...
   WHERE id = 12345;
   ```

6. **Tenant B's row is now silently rewritten and re-tagged as tenant A's**.

### Why the existing defenses don't catch this

- `@Where(is_deleted = 0)` on the entity — only filters reads, doesn't block writes.
- `TenantContext` org-stamping in the service — happens *before* the SELECT in merge(), and Hibernate copies the new value onto the managed entity.
- `findByIdAndOrganisationId(...)` query pattern — bypassed because the controller never calls it; it just hands the entity straight to `save()`.

The vulnerability is invisible to most test patterns because legitimate clients never send `id` on create. It only surfaces with a hostile client.

## The fix (per-service)

One line at the top of every create-style service method:

```java
@Transactional
public Committee create(Committee req) {
    req.setId(null);  // ← mass-assignment defense
    req.setOrganisationId(TenantContext.getCurrentTenant());
    return repo.save(req);
}
```

With `id == null`, JPA routes through `entityManager.persist()` (a clean INSERT) instead of `merge()`. No SELECT, no field-copy from the detached entity, no cross-tenant write path.

## Services patched (PRs #121 + #123)

| Module | Service | Methods |
|---|---|---|
| `admin.committee` | `CommitteeActionItemService` | `create` |
| `admin.committee` | `CommitteeResolutionService` | `create` |
| `admin.committee` | `CommitteeMemberRegistryService` | `appoint`, `addSkill` |
| `admin.committee` | `CommitteeMeetingPapersService` | `circulate` |
| `cyber` | `CyberService` | `saveFramework`, `saveControl`, `saveSubControl`, `saveIncident`, `saveAsset` |
| `bcm` | `BcmService` | `createPlan`, `createProcess`, `createBia`, `createExercise` |
| `compliance` | `PolicyService` | `create` |
| `compliance` | `RegulatoryChangeService` | `create` |
| `bcm.service.crisis` | `CrisisCommsService` | `createTemplate`, `createAudience` |
| `privacy` | `DpiaService` | `create` |

**Total: 19 service methods** across 11 services. Plus 17 services verified safe (already used the `new Entity() + apply(dto)` pattern).

## Services verified safe (use safer pattern)

These accept a request DTO instead of the entity, then explicitly construct a fresh entity:

```java
@Transactional
public IncidentResponse create(IncidentRequest req) {
    Incident i = new Incident();                   // id is null by default
    i.setOrganisationId(TenantContext.getCurrentTenant());
    apply(i, req);                                 // copies safe fields only
    return mapper.toResponse(incidentRepo.save(i));
}
```

This pattern is structurally immune — the entity is never loaded from the request body, so there's no `id` to defend against.

| Module | Service |
|---|---|
| `approvals` | `ApprovalRequestService` |
| `audit_lc` | `FindingService`, `AuditLifecycleService` (uses `setId(null)` explicitly in createCapa) |
| `audit_eng` | `AuditEngagementService` |
| `bcm.cycle` | `CrisisAarService`, `BcmComplianceMappingService` (uses `setId(null)` explicitly) |
| `bcm_lc` | `InvocationService` |
| `board_pack` | `BoardPackService` |
| `capa` | `CapaTaskService` |
| `change_mgmt` | `ChangeManagementService` |
| `compliance` | `AuditEngagementService` |
| `cyber_lc` | `VulnerabilityService` |
| `evidence` | `EvidenceItemService`, `EvidenceRequestService`, `EvidenceReviewService` |
| `ethics` | `AmlAlertService`, `DisclosureService`, `EthicsCaseService`, `KycService` |
| `incident` | `IncidentService` |
| `nca_ecc` | `NcaEccFilingService` |
| `risk` | `RiskService`, `RiskTreatmentService` (uses `setId(null)` explicitly) |
| `risk_lc` | `TreatmentPlanService` |
| `privacy.extensions` | `CrossBorderTransferService` |

## How to verify on new services

When reviewing a new service's create-style method, ask:

1. **Does the method accept a JPA entity type?** (e.g. `public Committee create(Committee req)`)
   - If yes → continue to step 2.
   - If no (uses a DTO/Request type) → likely safe; verify the entity is constructed fresh via `new EntityName()`.

2. **Does the method call `repo.save(req)` directly without first setting `req.setId(null)`?**
   - If yes → **vulnerable**. Add `req.setId(null)` at the top.
   - If no → safe.

3. **Bonus check**: does any update-style method copy fields from `req` onto an existing managed entity without explicitly preserving `existing.id` and `existing.organisationId`?
   - If yes → check carefully; the update path can also be exploited if `req.organisationId` is honored.

## Sprint-6 proposal — platform-wide AOP backstop

The per-service `setId(null)` defense works but is easy to forget on the next write endpoint. A platform-wide defense would catch the next regression automatically.

### Why `@PrePersist` alone doesn't help

JPA lifecycle hooks fire on the entity, not on the repository. `@PrePersist` runs *after* JPA has decided to route through `persist()` vs `merge()` — by which point the cross-tenant copy has already happened in the merge path.

### Why `@PreUpdate` alone doesn't help

By the time `@PreUpdate` fires, the managed entity has already been mutated with the attacker-supplied fields. The hook sees `organisationId == TenantContext` (because the service overwrote it) and can't tell this was an attack.

### What does work — Hibernate `Interceptor.onFlushDirty`

Hibernate's `Interceptor` interface exposes **both** old and new property values on flush:

```java
@Override
public boolean onFlushDirty(
    Object entity, Object id,
    Object[] currentState, Object[] previousState,
    String[] propertyNames, Type[] types
) {
    int orgIdx = indexOf(propertyNames, "organisationId");
    if (orgIdx < 0) return false;
    Object oldOrg = previousState[orgIdx];
    Object newOrg = currentState[orgIdx];
    if (!Objects.equals(oldOrg, newOrg)) {
        // Cross-tenant write detected.
        // Allow only if caller is PLATFORM_ADMIN with explicit org-switch flag set.
        if (!TenantContext.isCrossTenantWriteAllowed()) {
            throw new SecurityException(
                "Cross-tenant write attempt blocked on entity " +
                entity.getClass().getSimpleName() + " id=" + id +
                " (org " + oldOrg + " → " + newOrg + ")");
        }
    }
    return false;
}
```

Combined with a thread-local flag `TenantContext.isCrossTenantWriteAllowed()` that legitimate code paths (org switcher for `PLATFORM_ADMIN`) opt into, this gives a hard backstop that doesn't rely on every service remembering `setId(null)`.

### Acceptance criteria for the Sprint-6 work

- [ ] Hibernate `Interceptor` registered globally in `application.yml` (`spring.jpa.properties.hibernate.session_factory.interceptor`)
- [ ] `TenantContext.allowCrossTenantWrite()` + `clearCrossTenantWrite()` thread-local helpers
- [ ] Org switcher (`OrganisationController.switchTo`) opts in via `try-with-resources` pattern
- [ ] Unit test that simulates the attack: existing tenant-A user, hostile body with cross-tenant `id` → interceptor throws → response is 403
- [ ] Unit test that exercises legitimate org-switch path: PLATFORM_ADMIN flips orgs → no exception
- [ ] Decision: throw vs log+audit. Throw is safer; log+audit gives ops visibility for tracking attack attempts. Likely both: throw to block, audit-log the attempt.

### Estimated effort

~1-2 days for the interceptor + tests + integration into the existing tenant context. The audit log integration adds another 0.5 day. Compatible with the existing `setId(null)` per-service defense — they layer.
