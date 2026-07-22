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
    for _ in range(3):
        s,b=req("POST","/api/v1/auth/login",b={"username":u,"password":p})
        if isinstance(b,dict):
            t=b.get("accessToken") or b.get("token") or b.get("access_token")
            if t: return t
        print("  login retry:",s,str(b)[:80])
    return None
admin=login("admin","Admin@12345")
print("admin token present:",bool(admin))
cn=pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes",autocommit=True)
cur=cn.cursor()
def q(sql,*a): cur.execute(sql,*a); c=[d[0] for d in cur.description]; return [dict(zip(c,r)) for r in cur.fetchall()]
try:
    s,b=req("POST","/api/v1/changes",admin,{"changeCode":TAG,"titleEn":TAG+" links","titleAr":"ذ","changeType":"NORMAL"})
    print("create ->",s,"|",type(b).__name__, b if not isinstance(b,dict) else b.get("id"))
    cid=b["id"]
    print("LINKS round-trip:")
    s,_=req("POST",f"/api/v1/changes/{cid}/links",admin,{"entityType":"RISK","entityId":999001,"linkType":"AFFECTS"})
    print(f"  addLink RISK#999001 -> {s}")
    s,by=req("GET","/api/v1/changes/links/by-entity?entityType=RISK&entityId=999001",admin)
    print(f"  by-entity RISK#999001 -> {s}  rows={len(by) if isinstance(by,list) else by}")

    print("\nP9 TENANT ISOLATION (move to org3, read as org1 admin):")
    cur.execute("UPDATE change_mgmt.change_requests SET organisation_id=3 WHERE id=?",cid)
    s,_=req("GET",f"/api/v1/changes/{cid}",admin); print(f"  GET foreign-tenant change -> {s}  (expect 404 => isolation holds)")
    s,_=req("GET",f"/api/v1/changes/{cid}/approvals",admin); print(f"  GET foreign-tenant approvals -> {s}  (expect 404)")
    cur.execute("UPDATE change_mgmt.change_requests SET organisation_id=1 WHERE id=?",cid)

    print("\nNO CHANGE-REGISTER EXPORT ENDPOINT:")
    for ep in ("/api/v1/changes/export/pdf","/api/v1/changes/export","/api/v1/changes/register/pdf"):
        s,_=req("GET",ep,admin); print(f"  GET {ep} -> {s}")
finally:
    ids=[r['id'] for r in q("SELECT id FROM change_mgmt.change_requests WHERE CHARINDEX(?,title_en)>0",TAG)]
    if ids:
        marks=",".join("?"*len(ids))
        for t in ("change_tasks","change_approvals","change_comments","change_links"):
            cur.execute(f"DELETE FROM change_mgmt.{t} WHERE change_request_id IN ({marks})",*ids)
        cur.execute(f"DELETE FROM change_mgmt.change_requests WHERE id IN ({marks})",*ids)
        cur.execute(f"DELETE FROM platform.audit_log WHERE entity_type='ChangeRequest' AND entity_id IN ({marks})",*ids)
    print("\ncleanup ids:",ids,"| live rows:",q("SELECT COUNT(*) n FROM change_mgmt.change_requests WHERE is_deleted=0")[0]['n'])
    cn.close()
