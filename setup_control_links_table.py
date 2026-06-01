"""
Dev-DB: create evidence.control_links — the generic, polymorphic mapping that
lets ANY entity map to many sub-controls across frameworks (optional, manual,
user-maintained). Replace-set semantics (hard delete + re-insert per entity).
Idempotent. Flyway V216 mirrors it.
"""
import sys, pyodbc
sys.stdout.reconfigure(encoding="utf-8")
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")
cn = pyodbc.connect(CONN, autocommit=True); cur = cn.cursor()
if cur.execute("SELECT OBJECT_ID('evidence.control_links')").fetchval() is None:
    cur.execute("""
        CREATE TABLE evidence.control_links (
            id              BIGINT IDENTITY(1,1) PRIMARY KEY,
            organisation_id BIGINT NOT NULL,
            entity_type     NVARCHAR(60) NOT NULL,
            entity_id       BIGINT NOT NULL,
            sub_control_id  BIGINT NOT NULL,
            created_by      BIGINT NULL,
            created_at      DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME()
        )""")
    cur.execute("""CREATE UNIQUE INDEX ux_control_links
                   ON evidence.control_links(organisation_id, entity_type, entity_id, sub_control_id)""")
    print("+ created evidence.control_links (+ unique index)")
else:
    print("= evidence.control_links already exists (no-op)")
cn.close()
