import urllib.request, urllib.error, json, pyodbc

BASE="http://localhost:5173"; TAG="[REVIEW-TEST]"
def req(m,p,tok=None,b=None):
    d=json.dumps(b).encode() if b is not None else None
    r=urllib.request.Request(BASE+p,data=d,method=m); r.add_header("Content-Type","application/json")
    if tok: r.add_header("Authorization","Bearer "+tok)
    try:
        with urllib.request.urlopen(r,timeout=30) as x:
            raw=x.read().decode()
            try: return x.status,json.loads(raw)
            except: return x.status,raw
    except urllib.error.HTTPError as e:
        raw=e.read().decode()
        try: return e.code,json.loads(raw)
        except: return e.code,raw
def login(u,p):
    s,b=req("POST","/api/v1/auth/login",b={"username":u,"password":p})
    return b.get("accessToken") or b.get("token") if isinstance(b,dict) else None

admin=login("admin","Admin@12345"); viewer=login("viewer","Viewer@12345")

cn=pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes",autocommit=True)
cur=cn.cursor()
def q(sql,*a): cur.execute(sql,*a); cols=[d[0] for d in cur.description]; return [dict(zip(cols,r)) for r in cur.fetchall()]

try:
    print("="*70); print("P1-ROOTCAUSE  why did viewer POST succeed?"); print("="*70)
    roles=q("""SELECT r.role_name, r.is_system_role, r.is_privileged
               FROM iam.user_roles ur JOIN iam.roles r ON ur.role_id=r.id
               JOIN iam.users u ON ur.user_id=u.id
               WHERE u.username='viewer' AND (ur.is_deleted=0 OR ur.is_deleted IS NULL)""")
    print("  viewer roles:", roles)
    perms=q("""SELECT DISTINCT p.module,p.action
               FROM iam.user_roles ur JOIN iam.role_permissions rp ON ur.role_id=rp.role_id
               JOIN iam.permissions p ON rp.permission_id=p.id
               JOIN iam.users u ON ur.user_id=u.id
               WHERE u.username='viewer' AND p.module='WORKFLOW'
               AND (ur.is_deleted=0 OR ur.is_deleted IS NULL) ORDER BY p.action""")
    print("  viewer WORKFLOW perms:", perms)
    allmod=q("""SELECT DISTINCT p.module FROM iam.user_roles ur JOIN iam.role_permissions rp ON ur.role_id=rp.role_id
               JOIN iam.permissions p ON rp.permission_id=p.id JOIN iam.users u ON ur.user_id=u.id
               WHERE u.username='viewer' AND (ur.is_deleted=0 OR ur.is_deleted IS NULL)""")
    print("  viewer modules with ANY perm:", sorted(r['module'] for r in allmod))
    print("  WORKFLOW WRITE holders (roles):",
          [r['role_name'] for r in q("""SELECT DISTINCT r.role_name FROM iam.role_permissions rp
             JOIN iam.roles r ON rp.role_id=r.id JOIN iam.permissions p ON rp.permission_id=p.id
             WHERE p.module='WORKFLOW' AND p.action='WRITE' ORDER BY r.role_name""")])

    print("\n"+"="*70); print("P5b  CLEAN self-approval (still in CAB_PENDING)"); print("="*70)
    s,b=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" self-approve","titleAr":"ذ",
            "changeType":"NORMAL","riskLevel":"HIGH","requestedByName":"Alice Requester"})
    cid=b["id"]
    for t in ("SUBMITTED","IMPACT_ASSESSED","CAB_PENDING"): req("POST",f"/api/v1/changes/{cid}/transition",admin,{"target":t})
    s,b=req("POST",f"/api/v1/changes/{cid}/approvals",admin,
            {"approverName":"Alice Requester","approverRole":"Requester","decision":"APPROVE","comments":"I approve my own change"})
    print(f"  self-approval (approver==requester) while CAB_PENDING -> {s}  (expect 201 => NO SoD)")
    ap=q("SELECT approver_name,approver_id,decision FROM change_mgmt.change_approvals WHERE change_request_id=?",cid)
    print(f"  persisted approval: {ap}  >>> approver_id NULL => identity not captured")

    print("\n"+"="*70); print("P3b  AUDIT coverage of silent update() status change"); print("="*70)
    s,b=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" audit-check","titleAr":"ذ","changeType":"NORMAL"})
    aid=b["id"]
    req("PUT",f"/api/v1/changes/{aid}",admin,{"status":"IMPLEMENTED","outcome":"SUCCESS"})   # silent status jump
    req("POST",f"/api/v1/changes/{aid}/transition",admin,{"target":"SUBMITTED"}) if False else None
    aud=q("""SELECT action_type, workflow_status_before, workflow_status_after
             FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id=? ORDER BY id""",aid)
    print(f"  audit rows for id {aid} after CREATE + PUT(status=IMPLEMENTED): {aud}")
    print("      >>> expect CREATE only; the status-changing PUT leaves NO audit trail")

    # compare: a proper transition IS audited
    s,b=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" audit-txn","titleAr":"ذ","changeType":"NORMAL"})
    tid=b["id"]; req("POST",f"/api/v1/changes/{tid}/transition",admin,{"target":"SUBMITTED"})
    aud2=q("""SELECT action_type FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id=? ORDER BY id""",tid)
    print(f"  audit rows for id {tid} after CREATE + transition(SUBMITTED): {[r['action_type'] for r in aud2]}")

finally:
    print("\n"+"="*70); print("CLEANUP"); print("="*70)
    ids=[r['id'] for r in q("SELECT id FROM change_mgmt.change_requests WHERE CHARINDEX(?,title_en)>0 OR CHARINDEX(?,COALESCE(change_code,''))>0",TAG,TAG)]
    print("  tagged change ids:",ids)
    if ids:
        marks=",".join("?"*len(ids))
        for t in ("change_tasks","change_approvals","change_comments","change_links"):
            cur.execute(f"DELETE FROM change_mgmt.{t} WHERE change_request_id IN ({marks})",*ids)
    cur.execute("DELETE FROM change_mgmt.change_requests WHERE CHARINDEX(?,title_en)>0 OR CHARINDEX(?,COALESCE(change_code,''))>0",TAG,TAG)
    # also purge any audit rows we generated for those ids to keep the ledger clean
    if ids:
        marks=",".join("?"*len(ids))
        cur.execute(f"DELETE FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id IN ({marks})",*ids)
    left=q("SELECT COUNT(*) n FROM change_mgmt.change_requests WHERE CHARINDEX(?,title_en)>0",TAG)[0]['n']
    print("  tagged rows remaining:",left)
    tot=q("SELECT COUNT(*) n FROM change_mgmt.change_requests WHERE is_deleted=0")[0]['n']
    print("  total live change rows now:",tot,"(baseline was 15)")
    cn.close(); print("DONE.")
