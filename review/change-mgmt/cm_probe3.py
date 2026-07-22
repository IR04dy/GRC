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
    for path in ("/api/v1/auth/login","/api/auth/login"):
        s,b=req("POST",path,b={"username":u,"password":p})
        if isinstance(b,dict):
            t=b.get("accessToken") or b.get("token") or b.get("access_token")
            if t: return t
    return None
admin=login("admin","Admin@12345")
print("admin token present:", bool(admin))
s,b=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" diag","titleAr":"ذ","changeType":"NORMAL","riskLevel":"HIGH","requestedByName":"Alice Requester"})
print("create ->",s, "type:",type(b).__name__, "| body:", (b if not isinstance(b,dict) else {k:b[k] for k in ('id','status','changeType') if k in b}))

cn=pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes",autocommit=True)
cur=cn.cursor()
def q(sql,*a): cur.execute(sql,*a); c=[d[0] for d in cur.description]; return [dict(zip(c,r)) for r in cur.fetchall()]
try:
    if isinstance(b,dict):
        cid=b["id"]
        print("\nP5b CLEAN self-approval:")
        for t in ("SUBMITTED","IMPACT_ASSESSED","CAB_PENDING"):
            st,_=req("POST",f"/api/v1/changes/{cid}/transition",admin,{"target":t}); print(f"   ->{t}: {st}")
        st,rb=req("POST",f"/api/v1/changes/{cid}/approvals",admin,
                  {"approverName":"Alice Requester","approverRole":"Requester","decision":"APPROVE","comments":"self"})
        print(f"   self-approval (approver==requester, CAB_PENDING) -> {st}  (expect 201 => NO SoD)")
        ap=q("SELECT approver_name,approver_id,decision FROM change_mgmt.change_approvals WHERE change_request_id=?",cid)
        print(f"   persisted: {ap}  >>> approver_id NULL")

        print("\nP3b AUDIT coverage:")
        s2,b2=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" audit","titleAr":"ذ","changeType":"NORMAL"})
        aid=b2["id"]
        req("PUT",f"/api/v1/changes/{aid}",admin,{"status":"IMPLEMENTED","outcome":"SUCCESS"})
        aud=q("SELECT action_type,workflow_status_before,workflow_status_after FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id=? ORDER BY id",aid)
        print(f"   CREATE + PUT(status=IMPLEMENTED) audit rows: {aud}")
        print("     >>> only CREATE; the PUT status jump is UNAUDITED")
        s3,b3=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" txn","titleAr":"ذ","changeType":"NORMAL"})
        tid=b3["id"]; req("POST",f"/api/v1/changes/{tid}/transition",admin,{"target":"SUBMITTED"})
        aud2=[r['action_type'] for r in q("SELECT action_type FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id=? ORDER BY id",tid)]
        print(f"   CREATE + transition(SUBMITTED) audit rows: {aud2}  (transition IS audited)")
finally:
    ids=[r['id'] for r in q("SELECT id FROM change_mgmt.change_requests WHERE CHARINDEX(?,title_en)>0",TAG)]
    if ids:
        marks=",".join("?"*len(ids))
        for t in ("change_tasks","change_approvals","change_comments","change_links"):
            cur.execute(f"DELETE FROM change_mgmt.{t} WHERE change_request_id IN ({marks})",*ids)
        cur.execute(f"DELETE FROM change_mgmt.change_requests WHERE id IN ({marks})",*ids)
        cur.execute(f"DELETE FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id IN ({marks})",*ids)
    print("\ncleanup removed ids:",ids,"| live rows now:",q("SELECT COUNT(*) n FROM change_mgmt.change_requests WHERE is_deleted=0")[0]['n'])
    cn.close()
