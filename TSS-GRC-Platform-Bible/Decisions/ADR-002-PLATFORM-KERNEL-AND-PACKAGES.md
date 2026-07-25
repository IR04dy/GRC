# ADR-002 — Platform Kernel and Installable Packages

**Status:** Accepted  
**Date:** 2026-07-25

## Context

The business requires independent module sales. Current licensing infrastructure exists, but controller enforcement is not platform-wide.

## Decision

Separate a mandatory kernel from independently entitled capability packages. Each package ships a manifest, permissions, routes, contracts, data ownership, migrations and lifecycle policy. Server-side entitlement is mandatory.

## Alternatives

- **UI-only hiding:** rejected; not security or commercial enforcement.
- **Separate customer branches:** rejected; destroys upgrade economics.
- **One all-inclusive SKU:** retained only as an enterprise bundle, not as architecture.

## Consequences

Clear packaging and safer delivery; additional dependency governance and package testing are required.

## Proof

An unlicensed API returns denial, package navigation disappears, kernel boots alone and optional dependencies degrade gracefully.

