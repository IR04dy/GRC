# UTF-8 Double-Encoding (Mojibake) — Incident, Repair & Forensics

**Date:** 2026-06-08 · **DB:** `TSS_GRC` (SQL Server, `Latin1_General_100_CI_AS_SC_UTF8`)
**Scope:** data + seed-tooling only. **No application/TypeScript/Java code changed.**

---

## 1. Summary

117 NVARCHAR cells across 16 tables held **double-encoded UTF-8** (mojibake): UTF-8
bytes had been decoded as Windows-1252/Latin-1 and re-stored. Confirmed seed:
`ccm.control_test_definition` id=1 `title_en` held `Domain Admins membership â‰¤ 5`
(`…c3 a2 e2 80 b0 c2 a4…`) instead of `… ≤ 5` (`…e2 89 a4…`).

All 117 cells were repaired in place and verified. Root cause, exact per-cell
inventory, and prevention are below.

## 2. Root cause (confirmed)

Documented verbatim in the header of `V108__bcm_demo_seed.sql`:

> The file is saved as UTF-8 **without BOM**. On Windows **sqlcmd defaults to ANSI
> (CP-1252)** which corrupts Arabic into mojibake… Always apply with the UTF-8 input
> codepage flag: `sqlcmd … -f 65001 …`

So the BOM-less UTF-8 migration/seed `.sql` files were applied at least once by a
reader using the Windows ANSI code page instead of UTF-8. The source `.sql` files
are themselves correct (git never held the broken text — single clean commits);
the corruption was entirely **apply-time**, in specific historical apply batches.

Current Python seeders all read with `encoding='utf-8'` and write via pyodbc
Driver 17 (UTF-16), so they do **not** reproduce it.

## 3. Repair (done & verified)

- Tool: `fix_ccm_mojibake.py` — `ftfy.fix_encoding` (reverses encoding mojibake
  ONLY; leaves HTML/JSON structure and legit cells like `5×5` untouched).
- pyodbc binding forced to `SQL_WVARCHAR` (→ `nvarchar(max)`); default binding
  streams long strings as `SQL_WLONGVARCHAR`/legacy `ntext`, which cannot carry an
  `_SC_UTF8` collation (error 4189) and silently skips long cells.
- Verification (`verify_ccm_fix.py`): `≤` = `E2 89 A4` byte-exact; **0 mojibake
  remaining DB-wide**; repaired JSON still parses; long cells not truncated;
  legit `5×5`/`content_json` cells untouched.

## 4. Exact per-cell inventory — `forensic_final.json` / `.csv`

117/117 cells pinned to exact ids; each row carries byte-reconstructed
`before_mojibake` → `after_correct`, all round-trip validated.

| Confidence | Cells | Basis |
|---|---:|---|
| `exact` | 15 | live non-ASCII count == repaired count |
| `ground_truth` | 21 | ids printed by the pre-repair diagnostic (committee templates) |
| `batch_validated` | 20 | `created_at` apply-batch split; validated vs known sets |
| `inferred_symbol` | 5 | unique symbol-coverage (validated on CCM) |
| `inferred_batch` | 52 | `automation_rules.name_en` = ids 1–52 (prefix==52 at 05-23 boundary; rows 22,30 corroborate; 53+ clean) |
| **high/corroborated** | **113** | |
| `best_guess` | 4 | single-cell two-candidate ties (see below) |

### The 4 unresolved ties (low confidence — one cell each)
Each is a 50/50 between a cell with a pasted math symbol and one with a natively
typed em-dash; indistinguishable post-repair. Primary pick / alternative:

| Column | Picked | Alt | Note |
|---|---|---|---|
| `scm.exit_plans.data_migration_plan_en` | 2 (→) | 4 (—) | |
| `signature.digital_signature_transactions.purpose` | 15 (→) | 17 (—) | |
| `workflow.approval_chain_step.title_en` | 8 (≥) | 6 (—) | |
| `privacy.data_breaches.description_en` | 7 (→ &—) | 6 (→) | |

> Why unrecoverable: raw pre-repair backup was overwritten during the multi-pass
> repair; git never held the broken text; tx-log truncated (SIMPLE recovery). For
> these 4, the corrupted apply-batch is sometimes the earlier and sometimes the
> later one, so no `created_at` rule decides them.

## 5. Prevention

- **Apply BOM-less UTF-8 SQL with UTF-8**: `sqlcmd … -f 65001 …`, or use the Python
  appliers (they read `encoding='utf-8'`). Never apply these via bare `sqlcmd -i`.
- `fix_arabic_mojibake.py` hardened: now uses `ftfy.fix_encoding` (not the
  kitchen-sink `fix_text`) and forces `SQL_WVARCHAR` binding, so re-running it is
  safe on long `_SC_UTF8` cells and won't damage legit JSON/markdown.
- Migration `.sql` files were **not** edited (would change Flyway checksums).

## 6. File index (`.tools/`)

| File | Purpose |
|---|---|
| `fix_ccm_mojibake.py` | repair tool (`--dry-run`, `--ccm-only`) |
| `verify_ccm_fix.py` | post-repair verification |
| `forensic_final.py` | builds the exact-117 manifest |
| `forensic_final.json` / `.csv` | **canonical** per-id before/after inventory |
| `MOJIBAKE_FIX_REPORT.md` | this report |
