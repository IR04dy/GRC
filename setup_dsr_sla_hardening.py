"""
Dev-DB hardening: add the two DSR SLA-breach columns the DsrCase entity maps.

The privacy_lc.dsr_cases entity (module.privacy_lc.entity.DsrCase) maps
`sla_breached_at` and `sla_breach_notified`, added for staging/prod by Flyway
migration V115. The dev DB is Python-bootstrapped (Flyway OFF), so it never
received them -> GET /api/privacy-lc/dsr-cases returned HTTP 500
("Invalid column name 'sla_breached_at'").

This script mirrors V115's two idempotent ALTERs for the dev DB. Safe to re-run.
"""
import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")


def main():
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()

    added = []
    if cur.execute("SELECT COL_LENGTH('privacy_lc.dsr_cases','sla_breached_at')").fetchval() is None:
        cur.execute("ALTER TABLE privacy_lc.dsr_cases ADD sla_breached_at DATETIME2 NULL")
        added.append("sla_breached_at")
    if cur.execute("SELECT COL_LENGTH('privacy_lc.dsr_cases','sla_breach_notified')").fetchval() is None:
        cur.execute("ALTER TABLE privacy_lc.dsr_cases ADD sla_breach_notified BIT NOT NULL DEFAULT 0")
        added.append("sla_breach_notified")

    if added:
        print("+ added columns to privacy_lc.dsr_cases: " + ", ".join(added))
    else:
        print("= privacy_lc.dsr_cases already has both SLA columns (no-op)")

    # Verify both now present
    cols = [r[0] for r in cur.execute(
        "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS "
        "WHERE TABLE_SCHEMA='privacy_lc' AND TABLE_NAME='dsr_cases' "
        "AND COLUMN_NAME IN ('sla_breached_at','sla_breach_notified')").fetchall()]
    ok = {"sla_breached_at", "sla_breach_notified"}.issubset(set(cols))
    print(("OK" if ok else "FAIL") + " — dsr_cases SLA columns present: " + str(sorted(cols)))
    cn.close()


if __name__ == "__main__":
    main()
