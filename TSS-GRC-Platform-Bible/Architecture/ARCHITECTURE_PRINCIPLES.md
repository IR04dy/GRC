# Architecture Principles

## AP-01 — The modular monolith is the default

The product remains one deployable backend until an extraction has a measurable reason. Internal boundaries must nevertheless be as explicit as service boundaries.

## AP-02 — Platform kernel has no domain dependency

Kernel capabilities may be used by commercial packages. Kernel code must not import a commercial package or contain package-specific branching.

## AP-03 — Dependencies point inward to contracts

Packages expose commands, queries and events through public contracts. A package must not use another package's repository or entity.

## AP-04 — One source of truth, many projections

Each business object has one owner. Dashboards, search and cross-domain views use projections/read models, not peer repositories.

## AP-05 — Tenant isolation is invariant

Tenant context is mandatory for every synchronous operation and explicit for background work. Database uniqueness and write guards include organisation scope.

## AP-06 — State transitions are domain commands

Clients request intent; they do not set arbitrary status. The server validates current state, authority, evidence, SoD and invariants.

## AP-07 — Decisions contain provenance

Every approval records immutable user ID, effective role or committee term, decision, rationale, timestamp, tenant, object version and supporting evidence.

## AP-08 — Events are facts

Domain events use past tense, stable schemas, idempotency keys and an outbox. Consumers cannot assume delivery order unless explicitly partitioned.

## AP-09 — Contracts are versioned

Public APIs, events, package manifests and regulatory content have explicit compatibility policy and contract tests.

## AP-10 — Data residency is deployable configuration

Hosting, backup, search, files, telemetry and AI providers are selected by data class and customer policy. Saudi-region deployment must not require code forks.

## AP-11 — Security is enforced below presentation

Navigation hiding is convenience. Entitlement, permission, tenant and business-rule enforcement occur server-side.

## AP-12 — Observability preserves privacy

Logs use correlation and object references while excluding tokens, secrets and unnecessary personal or sensitive data.

## AP-13 — Arabic is domain data where legally material

Material titles, notices, decisions and regulatory records store Arabic and English values with completeness and translation status.

## AP-14 — Extraction is earned

A module is eligible for service extraction only after its in-process contracts, owned data, outbox events, load profile and operational owner are mature.

## Automated enforcement

The build must fail for:

- forbidden peer package imports;
- domain dependencies from kernel;
- repository access outside the owner;
- unlicensed commercial controllers;
- write endpoints without a real permission;
- entity uniqueness that omits tenant where tenant-scoped;
- public contracts without compatibility tests;
- state changes performed by generic status setters.

