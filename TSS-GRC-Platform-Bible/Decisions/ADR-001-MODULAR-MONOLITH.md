# ADR-001 — Enforced Modular Monolith

**Status:** Accepted  
**Date:** 2026-07-25

## Context

The platform has 58 backend module directories and extensive cross-domain reporting. It also has 734 direct cross-module imports and 14 bidirectional pairs. A microservices rewrite would distribute unstable boundaries and increase operational risk.

## Decision

Retain one deployable application while enforcing bounded contexts with public contracts, schema ownership, architecture tests and domain events. Extract a service only after measurable scale, isolation, residency, team-autonomy or release-cadence need.

## Alternatives

- **Immediate microservices:** rejected; high cost and distributed transaction/reporting burden.
- **Current unconstrained monolith:** rejected; prevents packaging and safe change.
- **Plugin classloader framework immediately:** deferred until package contracts are stable.

## Consequences

Faster remediation and lower operational overhead, but engineering discipline and automated boundary enforcement become mandatory.

## Proof

Zero forbidden imports, package-only boot tests and owner-contract tests.

