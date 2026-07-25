# Module Independence and Packaging

## Finding

The current codebase has feature modules, but most are not independently saleable packages. Licensing tables and a guard aspect exist; enforcement is currently concentrated in SOC Ops. Direct package imports and shared database access make disablement, release and extraction risky.

## Independence levels

| Level | Meaning |
|---|---|
| L0 | Folder only |
| L1 | Named module with routes and permissions |
| L2 | Owned API and data; forbidden peer repositories |
| L3 | Enforced entitlement, manifest and disable test |
| L4 | Independently versioned package and contract suite |
| L5 | Independently deployable when justified |

Baseline platform average is approximately **L1–L2**. SOC Ops partially reaches L3 through entitlement guards, but still depends on platform and shared runtime services.

## Package manifest

Every commercial package must publish:

```yaml
code: ERM
version: 2.0.0
requires:
  kernel: ">=2.0 <3"
optional:
  - COMPLIANCE
permissions:
  - ERM:READ
  - ERM:WRITE
  - ERM:APPROVE
  - ERM:EXPORT
routes:
  - /risk
ownedSchemas:
  - risk
publishedEvents:
  - risk.identified.v1
  - risk.rating-changed.v1
consumedEvents:
  - control.effectiveness-changed.v1
disablePolicy: read-only
removalPolicy: export-and-retain
```

The manifest drives backend entitlement, UI navigation, permission seed, health checks, dependency display and deployment validation.

## Dependency policy

Allowed:

- package → kernel interface;
- package → another package's public API;
- package → versioned event;
- reporting/read model → published projection.

Forbidden:

- package → peer entity;
- package → peer repository;
- package → peer internal service;
- kernel → commercial package;
- cross-schema foreign key that prevents package removal without an accepted exception;
- UI direct route to an unlicensed package.

## Package acceptance test

For each package:

1. install kernel plus package only;
2. boot and migrate a fresh tenant;
3. run its complete journey;
4. verify unlicensed users receive server-side denial;
5. enable optional dependency and verify enrichment;
6. disable optional dependency and verify graceful degradation;
7. upgrade from previous supported version;
8. export package data;
9. disable package and verify read-only/hidden behaviour;
10. verify no data, route, scheduler or event leaks across tenants.

## Migration sequence

1. Introduce package manifests and one global entitlement interceptor.
2. Define kernel contracts for identity, evidence, workflow, audit and notification.
3. Add architecture tests and dependency ratchet without moving code.
4. Move direct peer reads behind owner query interfaces.
5. Build event-backed projections for dashboards.
6. Split frontend route and navigation registries.
7. Package one lighthouse module—ERM or Privacy—end to end.
8. Repeat by commercial priority.
9. Extract services only where runtime evidence justifies it.

## Why not database-per-module now

SQL Server schemas can support logical ownership while the platform matures. Immediate database fragmentation would multiply transactions, reporting complexity and operations before contracts are stable. The correct intermediate target is strict schema ownership, public query contracts and an outbox.

