# ADR-005 — Layered Tenant Isolation

**Status:** Accepted  
**Date:** 2026-07-25

## Decision

Tenant isolation is enforced at request authentication, application query, repository contract, database write guard, cache key, object storage path, event envelope, websocket topic and background-job boundary.

## Alternatives

- application filtering only: rejected;
- database-per-tenant for all customers: not default, available for isolation tier;
- row-level security alone: rejected as sole control.

## Consequences

Defence in depth and flexible deployment, with more test matrices and explicit off-request context.

## Proof

Two-tenant negative tests across CRUD, export, search, files, cache, events, schedulers and realtime topics.

