# ADR-008 — Human-Accountable AI

**Status:** Accepted  
**Date:** 2026-07-25

## Decision

AI produces suggestions, summaries and draft mappings with source citations, confidence, model version and limitations. It cannot approve, accept risk, close findings, determine legal compliance or notify regulators without an accountable human command.

## Alternatives

- autonomous material decisions: rejected;
- no AI: rejected because assisted evidence and mapping can materially reduce effort;
- hidden generic chatbot: rejected.

## Consequences

Safer adoption and stronger auditability; some automation remains deliberately bounded.

## Proof

Evaluation sets, human override records, prompt/model lineage, leakage tests and drift monitoring.

