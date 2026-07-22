import urllib.request, urllib.error, json, pyodbc

BASE = "http://localhost:5173"   # Vite proxy → :8084 (validates the browser path)
TAG  = "[REVIEW-TEST]"
created_ids = []

def req(method, path, token=None, body=None):
    url = BASE + path
    data = json.dumps(body).encode() if body is not None else None
    r = urllib.request.Request(url, data=data, method=method)
    r.add_header("Content-Type", "application/json")
    if token: r.add_header("Authorization", "Bearer " + token)
    try:
        with urllib.request.urlopen(r, timeout=30) as resp:
            raw = resp.read().decode()
            try: return resp.status, json.loads(raw)
            except: return resp.status, raw
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try: return e.code, json.loads(raw)
        except: return e.code, raw
    except Exception as e:
        return -1, str(e)

def login(u, p):
    for path in ("/api/v1/auth/login", "/api/auth/login"):
        s, b = req("POST", path, body={"username": u, "password": p})
        if s == 200 and isinstance(b, dict):
            tok = b.get("accessToken") or b.get("token") or b.get("access_token")
            if tok: return tok
    return None

def short(b, n=120):
    s = b if isinstance(b, str) else json.dumps(b, ensure_ascii=False)
    return s[:n]

print("="*70); print("AUTH"); print("="*70)
admin = login("admin", "Admin@12345")
so    = login("so_officer", "Admin@12345")
viewer= login("viewer", "Viewer@12345")
print("admin token:", "OK" if admin else "FAIL")
print("so_officer :", "OK" if so else "FAIL")
print("viewer     :", "OK" if viewer else "FAIL")
assert admin, "cannot proceed without admin"

def mk(title_suffix, extra=None):
    body = {"changeCode": f"{TAG} CHG", "titleEn": f"{TAG} {title_suffix}",
            "titleAr": "اختبار مراجعة", "changeType": "NORMAL", "riskLevel": "HIGH",
            "category": "INFRASTRUCTURE", "requestedByName": "Alice Requester"}
    if extra: body.update(extra)
    s, b = req("POST", "/api/v1/changes", admin, body)
    if s in (200,201) and isinstance(b, dict):
        created_ids.append(b["id"]); return b
    print("   mk FAILED", s, short(b)); return None

print("\n"+"="*70); print("P1  PERMISSION GUARD (viewer)"); print("="*70)
if viewer:
    s,_ = req("GET", "/api/v1/changes", viewer);              print(f"  viewer GET  /changes            -> {s}")
    s,b = req("POST","/api/v1/changes", viewer, {"titleEn":TAG+" nope"}); print(f"  viewer POST /changes (write)    -> {s}  (expect 403)")
else:
    print("  viewer login failed; skipping")

print("\n"+"="*70); print("P2  BASELINE CREATE (admin)"); print("="*70)
c1 = mk("bypass-target")
print(f"  created id={c1['id'] if c1 else '?'}  status={c1.get('status') if c1 else '?'}  (expect DRAFT)")

print("\n"+"="*70); print("P3  update() LIFECYCLE BYPASS  vs  guarded transition()"); print("="*70)
if c1:
    s,b = req("PUT", f"/api/v1/changes/{c1['id']}", admin, {"status":"CLOSED"})
    s2,g = req("GET", f"/api/v1/changes/{c1['id']}", admin)
    print(f"  PUT  status=CLOSED on DRAFT      -> {s}   then GET status = {g.get('status') if isinstance(g,dict) else g}")
    print(f"     >>> {'BYPASS CONFIRMED (guard skipped)' if isinstance(g,dict) and g.get('status')=='CLOSED' else 'no bypass'}")
c2 = mk("guard-contrast")
if c2:
    s,b = req("POST", f"/api/v1/changes/{c2['id']}/transition", admin, {"target":"CLOSED"})
    print(f"  transition DRAFT->CLOSED (guard) -> {s}   msg={short(b,90)}   (expect 400)")

print("\n"+"="*70); print("P4  INVALID status/type PERSISTED via update() (no CHECK, no @Valid)"); print("="*70)
if c2:
    s,b = req("PUT", f"/api/v1/changes/{c2['id']}", admin, {"status":"ZZZ_INVALID","changeType":"MADE_UP","riskLevel":"NUCLEAR"})
    s2,g = req("GET", f"/api/v1/changes/{c2['id']}", admin)
    print(f"  PUT junk enums -> {s}   persisted: status={g.get('status')} type={g.get('changeType')} risk={g.get('riskLevel')}" if isinstance(g,dict) else f"  {s} {g}")

print("\n"+"="*70); print("P5  CAB APPROVAL THEATER (no quorum / no SoD / reject ignored)"); print("="*70)
c3 = mk("cab-no-votes")
if c3:
    for tgt in ("SUBMITTED","IMPACT_ASSESSED","CAB_PENDING"):
        s,_ = req("POST", f"/api/v1/changes/{c3['id']}/transition", admin, {"target":tgt})
    s,b = req("POST", f"/api/v1/changes/{c3['id']}/transition", admin, {"target":"CAB_APPROVED"})
    print(f"  CAB_APPROVED with ZERO approvals -> {s}   (expect 200 => votes don't gate)")
c4 = mk("cab-reject-ignored")
if c4:
    for tgt in ("SUBMITTED","IMPACT_ASSESSED","CAB_PENDING"):
        req("POST", f"/api/v1/changes/{c4['id']}/transition", admin, {"target":tgt})
    s,b = req("POST", f"/api/v1/changes/{c4['id']}/approvals", admin,
              {"approverName":"Bob CAB","approverRole":"CAB","decision":"REJECT","comments":"Not safe"})
    print(f"  record REJECT vote               -> {s}")
    s,b = req("POST", f"/api/v1/changes/{c4['id']}/transition", admin, {"target":"CAB_APPROVED"})
    print(f"  CAB_APPROVED despite REJECT      -> {s}   (expect 200 => reject ignored)")
    # self-approval (approver == requester), approverId never captured
    s,b = req("POST", f"/api/v1/changes/{c4['id']}/approvals", admin,
              {"approverName":"Alice Requester","approverRole":"Requester","decision":"APPROVE"})
    print(f"  SELF-approval (approver=requester)-> {s}   (expect 201 => no SoD)")

print("\n"+"="*70); print("P6  HARD DELETE task (no is_deleted col)"); print("="*70)
if c1:
    s,b = req("POST", f"/api/v1/changes/{c1['id']}/tasks", admin, {"titleEn":TAG+" task","status":"PENDING"})
    tid = b.get("id") if isinstance(b,dict) else None
    print(f"  add task -> {s} id={tid}")
    if tid:
        s,_ = req("DELETE", f"/api/v1/changes/tasks/{tid}", admin)
        print(f"  DELETE task -> {s} (expect 204)")

print("\n"+"="*70); print("P7  COMMENT author attribution"); print("="*70)
if c1:
    s,b = req("POST", f"/api/v1/changes/{c1['id']}/comments", admin, {"body":TAG+" no author field"})
    print(f"  POST comment w/o authorName -> {s}  stored authorName={b.get('authorName') if isinstance(b,dict) else b!r}  (expect 'Unknown')")

# ---- DB-side confirmations ----
print("\n"+"="*70); print("DB-SIDE CONFIRMATIONS"); print("="*70)
cn = pyodbc.connect("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;TrustServerCertificate=yes", autocommit=True)
cur = cn.cursor()
def q(sql,*a):
    cur.execute(sql,*a); cols=[d[0] for d in cur.description]; return [dict(zip(cols,r)) for r in cur.fetchall()]

if c1:
    aud = q("""SELECT action, COUNT(*) n FROM platform.audit_log
               WHERE entity_type='ChangeRequest' AND entity_id=? GROUP BY action""", c1['id'])
    print(f"  P7  audit_log actions for changed id {c1['id']} (PUT set CLOSED): {aud}")
    print("      >>> expect CREATE only; NO row for the status-changing PUT")
if c4:
    apr = q("""SELECT approver_name, approver_id, decision FROM change_mgmt.change_approvals
               WHERE change_request_id=?""", c4['id'])
    print(f"  P5  approvals persisted for id {c4['id']}: {apr}")
    print("      >>> approver_id NULL => no verifiable identity")

# P9 tenant guard (reversible): move c2 to org 3, read as admin(org1) -> expect 404, then restore
if c2:
    cur.execute("UPDATE change_mgmt.change_requests SET organisation_id=3 WHERE id=?", c2['id'])
    s,_ = req("GET", f"/api/v1/changes/{c2['id']}", admin)
    print(f"  P9  cross-tenant GET (id moved to org3, read as org1 admin) -> {s}  (expect 404 => tenant filter works)")
    cur.execute("UPDATE change_mgmt.change_requests SET organisation_id=1 WHERE id=?", c2['id'])

print("\n"+"="*70); print("CLEANUP ([REVIEW-TEST] rows via CHARINDEX)"); print("="*70)
# delete children first, then tagged changes (hard delete of my own test rows)
ids = q("SELECT id FROM change_mgmt.change_requests WHERE CHARINDEX(?, title_en)>0", TAG)
idlist = [r['id'] for r in ids]
print("  tagged change ids to remove:", idlist)
for t in ("change_tasks","change_approvals","change_comments","change_links"):
    if idlist:
        marks=",".join("?"*len(idlist))
        cur.execute(f"DELETE FROM change_mgmt.{t} WHERE change_request_id IN ({marks})", *idlist)
cur.execute("DELETE FROM change_mgmt.change_requests WHERE CHARINDEX(?, title_en)>0", TAG)
left = q("SELECT COUNT(*) n FROM change_mgmt.change_requests WHERE CHARINDEX(?, title_en)>0", TAG)
print("  tagged rows remaining after cleanup:", left[0]['n'])
cn.close()
print("\nDONE.")
