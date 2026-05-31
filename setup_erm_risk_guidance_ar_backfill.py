"""Backfill bilingual evidence guidance for the ERM-1 + RISK-1 frameworks.

QA (workflow grc-sme-catalogs re-run) found the global evidence catalog at
97.6% Arabic-guidance coverage: the only 13 sub-controls missing
evidence_guidance_ar are in ERM-1 (framework_id=7) and RISK-1 (framework_id=8),
which sit OUTSIDE the four SME families (NCA/NDI/PDPL/BCM).

Root cause: those 13 rows had Arabic text in the *English* evidence_guidance
column and an empty evidence_guidance_ar. This script makes both fields correct:
  - evidence_guidance_ar <- the existing Arabic (moved into the right column)
  - evidence_guidance    <- a proper English recommended-evidence line

Idempotent: only rows whose evidence_guidance_ar is still empty are touched, so
re-running is a no-op.
"""
from __future__ import annotations
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Encrypt=yes;TrustServerCertificate=yes;"
        "sendStringParametersAsUnicode=yes;")

# sub_control_code -> correct English recommended-evidence line.
# (The Arabic already present in evidence_guidance is moved into evidence_guidance_ar.)
EN = {
    "ERM-1-1-1": "Upload the approved enterprise risk-management policy (PDF) with version and sign-off.",
    "ERM-1-1-2": "Upload the board-approved risk-appetite statement with quantitative thresholds.",
    "ERM-1-1-3": "Upload the risk-committee charter and the latest committee minutes.",
    "ERM-1-2-1": "Upload the current risk register with impact and likelihood ratings per risk.",
    "ERM-1-2-2": "Upload the risk-assessment methodology document and the scoring matrix.",
    "ERM-1-2-3": "Upload the latest quarterly risk-review report presented to the committee.",
    "ERM-1-3-1": "Upload the treatment plans for all high and critical risks, with owners and due dates.",
    "ERM-1-3-2": "Upload the Key Risk Indicator (KRI) report or dashboard for the latest quarter.",
    "RISK-1-1-1": "Upload an export of the operational loss-event database for the period.",
    "RISK-1-1-2": "Upload the quarterly operational-incident classification / analysis report.",
    "RISK-1-1-3": "Upload the near-miss reporting log with the corrective actions taken.",
    "RISK-1-2-1": "Upload the latest control self-assessment (CSA) results.",
    "RISK-1-2-2": "Upload the independent control-testing report.",
}

cn = pyodbc.connect(CONN, autocommit=False)
cur = cn.cursor()
updated = skipped = 0
for code, en in EN.items():
    cur.execute(
        "SELECT sc.id, sc.evidence_guidance, sc.evidence_guidance_ar "
        "FROM evidence.sub_controls sc JOIN evidence.controls c ON sc.control_id=c.id "
        "WHERE c.framework_id IN (7,8) AND sc.sub_control_code=? AND c.is_active=1 AND sc.is_active=1",
        code)
    row = cur.fetchone()
    if not row:
        print(f"  ! {code}: not found — skipped")
        continue
    sc_id, cur_guid, cur_guid_ar = row
    if cur_guid_ar and cur_guid_ar.strip():
        skipped += 1
        continue
    # the Arabic currently living in evidence_guidance becomes the AR guidance
    arabic = (cur_guid or "").strip()
    cur.execute(
        "UPDATE evidence.sub_controls SET evidence_guidance_ar=?, evidence_guidance=? WHERE id=?",
        arabic, en, sc_id)
    updated += 1

cn.commit()
print(f"ERM-1/RISK-1 guidance backfill: updated={updated}, already-ok skipped={skipped}")

# verify global coverage
cur.execute("SELECT COUNT(*) FROM evidence.sub_controls sc JOIN evidence.controls c ON sc.control_id=c.id "
            "WHERE c.is_active=1 AND sc.is_active=1")
total = cur.fetchone()[0]
cur.execute("SELECT COUNT(*) FROM evidence.sub_controls sc JOIN evidence.controls c ON sc.control_id=c.id "
            "WHERE c.is_active=1 AND sc.is_active=1 AND sc.evidence_guidance_ar IS NOT NULL "
            "AND LTRIM(RTRIM(sc.evidence_guidance_ar))<>N''")
have = cur.fetchone()[0]
print(f"Global evidence_guidance_ar coverage: {have}/{total} = {round(100.0*have/total, 1)}%")
cn.close()
