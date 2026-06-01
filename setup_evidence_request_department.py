"""
Dev-DB: add evidence.evidence_requests.department_id so an evidence request can
be assigned to a department (in addition to / instead of a named assignee).
Idempotent. Flyway V214 carries the same change to staging/prod.
"""
import sys, pyodbc
sys.stdout.reconfigure(encoding="utf-8")
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")
cn = pyodbc.connect(CONN, autocommit=True); cur = cn.cursor()
if cur.execute("SELECT COL_LENGTH('evidence.evidence_requests','department_id')").fetchval() is None:
    cur.execute("ALTER TABLE evidence.evidence_requests ADD department_id BIGINT NULL")
    print("+ added evidence.evidence_requests.department_id")
else:
    print("= evidence.evidence_requests.department_id already present (no-op)")
cn.close()
