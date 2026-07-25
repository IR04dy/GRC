# ADR-006 — State Machines and Approvals

**Status:** Accepted  
**Date:** 2026-07-25

## Decision

Domain objects expose named commands, not arbitrary status setters. Every transition defines source, target, permission, eligible authority, SoD, evidence, validation, side effects and audit event. Approvals use immutable authenticated user IDs and effective authority records.

## Alternatives

- generic `transition(targetStatus)`: rejected for regulated decisions;
- client-computed eligibility: rejected;
- workflow engine for every trivial lifecycle: rejected; simple aggregates may own local state machines.

## Consequences

More explicit APIs and tests, much stronger decision integrity.

## Proof

Transition matrices, property tests, invalid-transition tests, forged-actor tests and two-principal live probes.

