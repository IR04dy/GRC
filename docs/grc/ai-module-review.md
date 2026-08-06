# AI Governance / Model-Risk Module — Deep Review & Improvement Roadmap
> **HISTORIC — point-in-time snapshot (all files 2026-06-01). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


**Date:** 2026-06-01
**Method:** Standards-grounded code review of `module.ai` (backend) + `pages/{ai,ai-governance,aiLifecycle}` (frontend), mapped to ISO/IEC 42001, NIST AI RMF, EU AI Act, SDAIA AI Ethics Principles, and SAMA-style model-risk management; benchmarked vs Credo AI, Holistic AI, IBM watsonx.governance, ServiceNow & OneTrust AI Governance.

## Verdict: strong core, governance + GenAI gaps

The module is **more mature than most GRC platforms**: it already has a real model registry (`MlModel`, 25+ fields), an 8-stage lifecycle state machine (PROPOSED→…→DECOMMISSIONED), independent validation, drift monitoring, recertification, retraining change-control, a fairness/bias/explainability audit framework, and full bilingual support — with SDAIA/SAMA alignment baked into the F8 automation rules. The gaps are at the **governance, GenAI, and artifact** layers.

## What's PRESENT (don't rebuild)
Model registry & inventory · 8-stage lifecycle with validator gating · `ModelValidation` workflow · `MlModelDriftObservation` (4 drift types) · `RetrainingEvent` change-control · `Recertification` (annual + SDAIA attestation) · `MlModelAudit` (FAIRNESS/BIAS/EXPLAINABILITY/ROBUSTNESS/PRIVACY/SECURITY/PERFORMANCE) · risk tiering (CRITICAL/HIGH/MED/LOW) · PDPL DPIA link · human-in-the-loop gates · bilingual EN/AR throughout.

## Top gaps (vs standards + market leaders)

| # | Gap | Standard it closes | Effort |
|---|---|---|---|
| 1 | **No AI governance policy/charter** entity (board oversight, AI risk appetite, escalation) — only scattered automation rules | ISO 42001 · NIST GOVERN · OneTrust/Credo hub | L |
| 2 | **No dedicated AI risk/impact assessment** — risk tier is a hardcoded enum, no inherent/residual scoring or AIA workflow | ISO 42001 · NIST MAP · EU AI Act | M |
| 3 | **GenAI/LLM controls absent** — the LLM chatbot has no prompt-injection, hallucination/groundedness, data-leakage, content-safety, or inference logging | EU AI Act · NIST · Holistic AI | M |
| 4 | **Explainability is a boolean flag**, not a managed artifact — no model card / SHAP report / fairness report storage or generation | EU AI Act Annex IV · NIST RMF App.D | M |
| 5 | **No continuous fairness monitoring** — fairness is point-in-time audits, no live demographic-disparity tracking post-deployment | NIST MEASURE · Credo/Holistic | M |
| 6 | **Third-party / vendor AI ungoverned** — OpenAI/NICE Actimize tracked informally; no vendor AI risk, audit rights, SLA, or incident notification | ISO 42001 §8.4 supplier mgmt | M |
| 7 | **No EU AI Act risk-tier mapping** (no PROHIBITED tier) and **no conformity/attestation** workflow | EU AI Act | S |
| 8 | **No model failure-mode taxonomy** — drift→Issue, but root cause is free text (no concept-drift/adversarial/distribution-shift catalog) | NIST MANAGE | S |
| 9 | **Retraining data provenance/quality gate missing** on `RetrainingEvent` | ISO 42001 · model risk | S |
| 10 | **No intersectional (multi-attribute) bias analysis** — fairness metrics are univariate | SDAIA fairness · Holistic AI | S |

## Recommended sequence (highest value, lowest risk first)

1. **AI Risk & Impact Assessment** (new `ai_risk_assessment` entity + lifecycle + register) — gate `APPROVED_FOR_PROD` on an approved assessment for HIGH/CRITICAL models. Closes the ISO 42001 / NIST MAP gap and anchors everything else. *(M)*
2. **EU AI Act tiering + conformity** — add a `PROHIBITED` tier + an `ai_act_risk_category` + a lightweight conformity-attestation step on the model. Small, regulator-visible. *(S)*
3. **Explainability artifacts + auto model card** — replace `hasExplainabilityDoc` boolean with a `model_explainability_artifact` table (MODEL_CARD/SHAP/FAIRNESS), auto-generated on EXPLAINABILITY audit pass, stored as evidence. *(M)*
4. **GenAI/LLM control framework** — `llm_safety_policy` + `llm_inference_log` (prompt-injection detect, content-safety, PII scan, retention) required before an LLM model goes to prod. *(M)*
5. **Continuous fairness monitoring** + **third-party AI register** — live disparity metrics with SLA thresholds; vendor-AI governance linked to TPRM. *(M)*

Each is an independent, shippable increment (entity + migration + service + controller + register page + i18n), verifiable end-to-end like the other modules.

*(Full capability inventory — PRESENT/PARTIAL/MISSING per standard — retained in the review run output.)*
