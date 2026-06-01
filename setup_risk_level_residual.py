"""
Dev-DB fix: make risk.risks.risk_level reflect RESIDUAL (post-control) exposure.

The original computed column banded on inherent_likelihood * inherent_impact
only, so the headline rating ignored the effect of controls. An operating ERM
register and board pack report on residual risk. This redefines the computed
column to band on the residual score when a residual assessment exists, falling
back to the inherent score otherwise (matches the frontend
RiskRegisterPage residual-with-inherent-fallback view).

Bands are unchanged: CRITICAL >= 20, HIGH >= 12, MEDIUM >= 6, LOW >= 1.

A computed column cannot be ALTERed in place, so it is dropped and re-added.
This is metadata-only (no stored data, no schemabound deps, no index on the
column — verified), and the entity maps risk_level insertable=false/updatable=false,
so JPA is unaffected. Idempotent: re-running detects the already-residual
definition and skips. Flyway V212 carries the same change to staging/prod.
"""
import sys
import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")

# COALESCE(residual L*I, inherent L*I) banded into the level labels.
NEW_DEF = (
    "(CASE "
    "WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact])>=20 THEN N'CRITICAL' "
    "WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact])>=12 THEN N'HIGH' "
    "WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact])>=6 THEN N'MEDIUM' "
    "WHEN COALESCE([residual_likelihood]*[residual_impact],[inherent_likelihood]*[inherent_impact])>=1 THEN N'LOW' "
    "END)"
)


def main():
    cn = pyodbc.connect(CONN, autocommit=True)
    cur = cn.cursor()

    current = cur.execute(
        "SELECT cc.definition FROM sys.computed_columns cc "
        "WHERE cc.object_id=OBJECT_ID('risk.risks') AND cc.name='risk_level'").fetchval()

    if current and "residual_likelihood" in current:
        print("= risk.risks.risk_level already residual-based (no-op)")
        print("  def: " + current)
        cn.close()
        return

    print("- current (inherent-only) def: " + str(current))
    if cur.execute("SELECT COL_LENGTH('risk.risks','risk_level')").fetchval() is not None:
        cur.execute("ALTER TABLE risk.risks DROP COLUMN risk_level")
        print("  dropped old computed column")
    cur.execute("ALTER TABLE risk.risks ADD risk_level AS " + NEW_DEF)
    print("+ re-added risk_level as residual-with-inherent-fallback")

    # Verify + show a few rows where residual differs from inherent
    new_def = cur.execute(
        "SELECT definition FROM sys.computed_columns "
        "WHERE object_id=OBJECT_ID('risk.risks') AND name='risk_level'").fetchval()
    print("  new def: " + new_def)
    print("  sample (id, inhSc, resSc, level):")
    for r in cur.execute(
        "SELECT TOP 6 id, inherent_score, residual_score, risk_level "
        "FROM risk.risks WHERE organisation_id=1 ORDER BY id").fetchall():
        print("    id=%s inhSc=%s resSc=%s level=%s" % (r[0], r[1], r[2], r[3]))
    cn.close()


if __name__ == "__main__":
    main()
