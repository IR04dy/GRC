import pyodbc, json

cn = pyodbc.connect(
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;"
    "DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes", autocommit=True)
c = cn.cursor()

def q(sql, *a):
    c.execute(sql, *a)
    cols = [d[0] for d in c.description]
    return [dict(zip(cols, r)) for r in c.fetchall()]

print("=== change_mgmt tables ===")
for r in q("""SELECT t.name FROM sys.tables t JOIN sys.schemas s ON t.schema_id=s.schema_id
              WHERE s.name='change_mgmt' ORDER BY t.name"""):
    print("  ", r['name'])

print("\n=== columns: change_requests (status/type/risk/is_deleted) ===")
for r in q("""SELECT c.name, ty.name tp, c.max_length, c.is_nullable
              FROM sys.columns c JOIN sys.types ty ON c.user_type_id=ty.user_type_id
              WHERE c.object_id=OBJECT_ID('change_mgmt.change_requests')
              AND c.name IN ('status','change_type','risk_level','category','outcome','is_deleted','title_ar','organisation_id')
              ORDER BY c.name"""):
    print("  ", r)

print("\n=== CHECK constraints on change_mgmt tables ===")
ck = q("""SELECT OBJECT_NAME(parent_object_id) tbl, name, definition
          FROM sys.check_constraints
          WHERE OBJECT_SCHEMA_NAME(parent_object_id)='change_mgmt' ORDER BY tbl,name""")
if not ck: print("   (NONE)")
for r in ck: print("  ", r['tbl'], "|", r['name'], "|", r['definition'])

print("\n=== change_tasks columns (is_deleted?) ===")
for r in q("""SELECT c.name FROM sys.columns c
              WHERE c.object_id=OBJECT_ID('change_mgmt.change_tasks') ORDER BY c.column_id"""):
    print("  ", r['name'])

print("\n=== FK / default constraints (externalId, created_at) ===")
for r in q("""SELECT c.name col, dc.definition
              FROM sys.default_constraints dc JOIN sys.columns c
                ON dc.parent_object_id=c.object_id AND dc.parent_column_id=c.column_id
              WHERE OBJECT_SCHEMA_NAME(dc.parent_object_id)='change_mgmt'
                AND OBJECT_NAME(dc.parent_object_id)='change_requests' ORDER BY col"""):
    print("  ", r)

print("\n=== organisations ===")
for r in q("SELECT id, name_en FROM platform.organisations ORDER BY id"):
    print("  ", r)

print("\n=== change_requests row counts by org+status ===")
rows = q("""SELECT organisation_id org, status, COUNT(*) n
            FROM change_mgmt.change_requests WHERE is_deleted=0
            GROUP BY organisation_id, status ORDER BY org, status""")
if not rows: print("   (NO CHANGE ROWS AT ALL)")
for r in rows: print("  ", r)

print("\n=== total change rows incl deleted ===")
print("  ", q("SELECT COUNT(*) n, SUM(CAST(is_deleted AS INT)) del FROM change_mgmt.change_requests")[0])

print("\n=== users for probing (admin/so_officer/viewer/compliance) ===")
for r in q("""SELECT u.username, u.organisation_id, u.is_active
              FROM iam.users u
              WHERE u.username IN ('admin','so_officer','viewer','compliance','auditor','ranalyst')
              ORDER BY u.username"""):
    print("  ", r)

print("\n=== WORKFLOW permission grants by role (which roles hold WORKFLOW.*) ===")
try:
    rows = q("""SELECT r.role_key, p.action
                FROM iam.role_permissions rp
                JOIN iam.roles r ON rp.role_id=r.id
                JOIN iam.permissions p ON rp.permission_id=p.id
                WHERE p.module='WORKFLOW' ORDER BY r.role_key, p.action""")
    for r in rows: print("  ", r)
except Exception as e:
    print("   perm query failed:", e)

cn.close()
