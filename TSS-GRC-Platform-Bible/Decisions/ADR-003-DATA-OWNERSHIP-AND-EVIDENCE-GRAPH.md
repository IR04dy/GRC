# ADR-003 — Data Ownership and Evidence Graph

**Status:** Accepted  
**Date:** 2026-07-25

## Decision

Each canonical object has one owning package. Cross-domain views consume published projections. A shared evidence graph links claims, controls, obligations, risks, tests, issues, decisions and files without transferring ownership.

## Alternatives

- shared repositories across modules: rejected;
- duplicate independent copies: rejected except immutable snapshots;
- one universal mega-entity: rejected.

## Consequences

Lineage and reuse improve while eventual consistency appears in dashboards. Projection freshness must be visible.

## Proof

Ownership catalogue, lineage queries, projection lag metrics and no peer repository imports.

