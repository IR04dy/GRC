"""
Dev-DB: ensure platform.attachments supports the generic, typed attachment
facility. A bare platform.attachments table already existed (empty, unused) with
file_size_bytes / sha256_hash / uploaded_by / uploaded_at / is_deleted. This adds
the three columns the typed facility needs (doc_type, title_en, valid_to) and
creates the table outright on a fresh DB. Idempotent. Flyway V215 mirrors it.
"""
import sys, pyodbc
sys.stdout.reconfigure(encoding="utf-8")
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;"
        "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;Unicode_Results=Yes;")
cn = pyodbc.connect(CONN, autocommit=True); cur = cn.cursor()

if cur.execute("SELECT OBJECT_ID('platform.attachments')").fetchval() is None:
    cur.execute("""
        CREATE TABLE platform.attachments (
            id              BIGINT IDENTITY(1,1) PRIMARY KEY,
            external_id     UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
            organisation_id BIGINT NOT NULL,
            entity_type     NVARCHAR(100) NOT NULL,
            entity_id       BIGINT        NOT NULL,
            doc_type        NVARCHAR(30)  NOT NULL DEFAULT 'OTHER',
            title_en        NVARCHAR(300) NULL,
            file_name       NVARCHAR(300) NOT NULL,
            file_path       NVARCHAR(1000) NOT NULL,
            file_size_bytes BIGINT        NOT NULL,
            mime_type       NVARCHAR(200) NOT NULL,
            sha256_hash     NVARCHAR(64)  NOT NULL,
            valid_to        DATE          NULL,
            uploaded_by     BIGINT        NOT NULL,
            uploaded_at     DATETIME2     NOT NULL DEFAULT SYSUTCDATETIME(),
            is_deleted      BIT           NOT NULL DEFAULT 0,
            deleted_by      BIGINT        NULL
        )""")
    cur.execute("""CREATE INDEX ix_attachments_entity
                   ON platform.attachments(organisation_id, entity_type, entity_id)
                   WHERE is_deleted = 0""")
    print("+ created platform.attachments (+ index)")
else:
    added = []
    if cur.execute("SELECT COL_LENGTH('platform.attachments','doc_type')").fetchval() is None:
        cur.execute("ALTER TABLE platform.attachments ADD doc_type NVARCHAR(30) NOT NULL DEFAULT 'OTHER'"); added.append("doc_type")
    if cur.execute("SELECT COL_LENGTH('platform.attachments','title_en')").fetchval() is None:
        cur.execute("ALTER TABLE platform.attachments ADD title_en NVARCHAR(300) NULL"); added.append("title_en")
    if cur.execute("SELECT COL_LENGTH('platform.attachments','valid_to')").fetchval() is None:
        cur.execute("ALTER TABLE platform.attachments ADD valid_to DATE NULL"); added.append("valid_to")
    print("+ added columns: " + ", ".join(added) if added else "= platform.attachments already typed (no-op)")
cn.close()
