# Saudi-GRC SME Evidence Catalog — QA + Build-Spec Report
> **HISTORIC — point-in-time snapshot (all files 2026-06-01). Not a statement of current state.**
> For what the platform is today read [`CLAUDE.md`](../../CLAUDE.md) and [`PROJECT_CHRONICLE.md`](../../PROJECT_CHRONICLE.md); for measured figures, [`MEASURED_BASELINE.md`](../../TSS-GRC-Platform-Bible/Appendix/MEASURED_BASELINE.md). Kept for provenance. *(W0-6)*


**Overall verdict: PASS** — all 9 active named-family frameworks PASS, none FAIL.
133 active controls / 529 active sub-controls across NCA / NDI / PDPL / BCM,
fully clean and bilingual. Global catalog Arabic-guidance coverage: **100%**.

- Source: read-only QA workflow `grc-sme-catalogs` (run `wf_b8bc599d-8a2`) — 1 inventory + 9 per-framework verify agents + 1 synthesis, no DB writes.
- Scope: `evidence.frameworks` / `evidence.controls` / `evidence.sub_controls` (global reference data; `is_active = 1`).

## Per-framework results

| Framework | Family | Controls | Sub-controls | missing `name_ar` | missing guidance | missing `guidance_ar` | dups | Verdict |
|---|---|---:|---:|---:|---:|---:|---:|---|
| ECC-2:2024 | NCA | 29 | 107 | 0 | 0 | 0 | 0 | PASS |
| CCC-1:2019 | NCA | 24 | 77 | 0 | 0 | 0 | 0 | PASS |
| DCC-1:2022 | NCA | 8 | 40 | 0 | 0 | 0 | 0 | PASS |
| CSCC-1:2020 | NCA | 8 | 36 | 0 | 0 | 0 | 0 | PASS |
| OTCC-1:2022 | NCA | 13 | 43 | 0 | 0 | 0 | 0 | PASS |
| TCC-1:2021 | NCA | 9 | 29 | 0 | 0 | 0 | 0 | PASS |
| NDMO-DMPDP-1 | NDI | 15 | 77 | 0 | 0 | 0 | 0 | PASS |
| PDPL-1:2023 | PDPL | 14 | 59 | 0 | 0 | 0 | 0 | PASS |
| BCM-ISO22301-SAMA | BCM | 13 | 61 | 0 | 0 | 0 | 0 | PASS |
| **Total (named families)** | — | **133** | **529** | **0** | **0** | **0** | **0** | **PASS** |

> The global inventory counts 138 controls / 542 sub-controls because it also
> includes `ERM-1` (3 controls / 8 sub-controls) and `RISK-1` (2 / 5), which sit
> **outside** the four named families. The 5-control / 13-sub-control delta is
> fully accounted for by those two non-family frameworks.

## Gaps — found and resolved

- **No FAILs.** Every QA'd framework is PASS.
- **No missing Arabic guidance within the four named families** — all 529 family sub-controls carry non-empty `evidence_guidance_ar` (plus EN guidance and `name_ar`).
- **Global Arabic-guidance gap (now fixed):** the QA pass reported 97.6% global coverage (529/542) — the 13 missing `evidence_guidance_ar` were all in `ERM-1` / `RISK-1`. Those rows also had Arabic text mis-placed in the *English* `evidence_guidance` column. Resolved by `setup_erm_risk_guidance_ar_backfill.py`, which moved the Arabic into `evidence_guidance_ar` and set a correct English `evidence_guidance`. **Global coverage is now 542/542 = 100%.**
- **3 inactive shells (not blocking):** `TW-1` (NCA), `BCM-1`, `BCM-2` exist with zero active controls and are correctly excluded.

## Build-spec confirmation — deep sub-controls wired to the evidence lifecycle

| Family | Frameworks | Controls | Sub-controls | Recommended evidence (EN + AR) on every sub-control | Status |
|---|---|---:|---:|---|---|
| **NCA** | ECC-2:2024, CCC-1:2019, DCC-1:2022, CSCC-1:2020, OTCC-1:2022, TCC-1:2021 | 91 | 332 | Yes | CONFIRMED |
| **NDI** | NDMO-DMPDP-1 | 15 | 77 | Yes | CONFIRMED |
| **PDPL** | PDPL-1:2023 | 14 | 59 | Yes | CONFIRMED |
| **BCM** | BCM-ISO22301-SAMA | 13 | 61 | Yes | CONFIRMED |

All four families carry deep sub-controls, each with recommended evidence guidance
(EN + AR) wired to the evidence lifecycle. **Build spec satisfied.**
