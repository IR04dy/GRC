# ADR-004 — Transactional Outbox and Domain Events

**Status:** Accepted  
**Date:** 2026-07-25

## Decision

Material aggregate changes write an event to a transactional outbox in the same transaction. Dispatch is asynchronous, idempotent and observable. In-process delivery is permitted initially; the contract permits later broker adoption.

## Alternatives

- direct synchronous service chains: rejected for cross-package propagation;
- dual database/broker writes: rejected because they are not atomic;
- broker-first rewrite: rejected until event contracts mature.

## Consequences

Reliable decoupling and replay at the cost of eventual consistency and outbox operations.

## Proof

Crash/retry tests, idempotent consumers, schema compatibility and replay audit.

