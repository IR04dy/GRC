# DF-3 / F2-21 live probe — the forged-vote attempt against the running stack.
#
# Expectations:
#   1. member votes as themselves (no memberName)      -> 200, on_behalf = 0, recorded_by = them
#   2. member votes in ANOTHER member's name            -> 403  (the defect, now closed)
#   3. non-roster name, even with authority             -> 400
#   4. secretary/chair records on behalf                -> 200, on_behalf = 1
#   5. recusal survives a case/padding variant          -> stored vote RECUSED, canonical name
import json, sys, urllib.request, urllib.error, pyodbc

BASE = "http://localhost:8084"
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost,1433;DATABASE=TSS_GRC;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
TAG = "[REVIEW-TEST] DF-3 probe"

def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    data = json.dumps(body).encode() if body is not None else None
    try:
        with urllib.request.urlopen(req, data, timeout=30) as r:
            raw = r.read().decode("utf-8", "replace")
            return r.status, raw
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:
        return 0, str(e)

def login(u, p):
    st, body = call("POST", "/api/auth/login", body={"username": u, "password": p})
    if st != 200:
        print(f"  !! login {u} -> {st}: {body[:300]}"); return None
    return json.loads(body).get("accessToken")

db = pyodbc.connect(CONN, timeout=10, autocommit=True)
cur = db.cursor()

# ── fixture: a committee with a 3-member roster, a meeting, and a resolution ──
cur.execute("SELECT TOP 1 id FROM admin.committees WHERE organisation_id=1 AND is_deleted=0 ORDER BY id")
row = cur.fetchone()
if not row:
    print("no committee in org 1 — cannot probe"); sys.exit(2)
CID = row[0]

# Map the two probe personas onto real platform users so memberUserId resolves.
cur.execute("SELECT id, username FROM iam.users WHERE username IN ('admin','so_officer','viewer')")
users = {u: i for i, u in cur.fetchall()}
print(f"  committee={CID}  users={users}")

cur.execute("DELETE FROM admin.committee_member_terms WHERE CHARINDEX(?, member_name) > 0", TAG)
def seat(name, uid, voting=1, role="Member"):
    cur.execute("""INSERT INTO admin.committee_member_terms
        (organisation_id, committee_id, member_name, member_user_id, role, is_voting_member,
         is_independent, appointed_at, term_starts_at, renewal_status)
        VALUES (1, ?, ?, ?, ?, ?, 0, GETDATE(), GETDATE(), 'ACTIVE')""",
        CID, name, uid, role, voting)

A = f"{TAG} Alpha"      # so_officer
B = f"{TAG} Beta"        # admin
C = f"{TAG} Observer"    # non-voting
seat(A, users.get("so_officer"))
seat(B, users.get("admin"))
seat(C, users.get("viewer"), voting=0)

# meeting_id is NOT NULL on committee_resolutions, so the fixture needs a meeting on
# this committee. Reuse one if present; otherwise create it.
cur.execute("SELECT TOP 1 id FROM admin.committee_meetings WHERE organisation_id=1 AND committee_id=? ORDER BY id DESC", CID)
mrow = cur.fetchone()
if mrow:
    MEET = mrow[0]
else:
    cur.execute("""INSERT INTO admin.committee_meetings
        (organisation_id, committee_id, title_en, title_ar, meeting_date, status)
        VALUES (1, ?, ?, ?, GETDATE(), 'SCHEDULED')""", CID, TAG + " meeting", TAG + " اجتماع")
    cur.execute("SELECT TOP 1 id FROM admin.committee_meetings WHERE organisation_id=1 AND committee_id=? ORDER BY id DESC", CID)
    MEET = cur.fetchone()[0]

cur.execute("""INSERT INTO admin.committee_resolutions
    (organisation_id, meeting_id, committee_id, resolution_code, seq, title_en, title_ar,
     body_en, outcome, confidentiality)
    VALUES (1, ?, ?, ?, 999, ?, ?, ?, 'DEFERRED', 'INTERNAL')""",
    MEET, CID, TAG[:40] + " R1", TAG + " resolution", TAG + " قرار", TAG + " body")
cur.execute("SELECT TOP 1 id FROM admin.committee_resolutions WHERE CHARINDEX(?, resolution_code) > 0 ORDER BY id DESC", TAG[:40])
RES = cur.fetchone()[0]
print(f"  meeting={MEET}  resolution={RES}  seats: Alpha(so_officer,voting) Beta(admin,voting) Observer(viewer,non-voting)")

t_admin = login("admin", "Admin@12345")          # PLATFORM_ADMIN -> may record on behalf
t_so    = login("so_officer", "Admin@12345")     # ordinary member
if not (t_admin and t_so):
    sys.exit(2)

results = []
def check(label, got, want):
    ok = got == want
    results.append((ok, label, got, want))
    print(f"  {'PASS' if ok else 'FAIL'}  {label:62} got {got}, want {want}")

P = f"/api/v1/committees/resolutions/{RES}/votes"

# 1. Alpha (so_officer) votes as themselves — no memberName in the body at all.
#
# This returns 403, and NOT because of DF-3: the endpoint guard is
# hasPermission('ADMIN','WRITE'), so_officer holds no ADMIN permission, and — verified
# against iam.role_permissions — *** ZERO roles hold ADMIN:WRITE at all ***. Committee
# voting is therefore reachable only by PLATFORM_ADMIN, which bypasses in code. The
# "member casts their own vote" path DF-3 introduces as the safe default is unreachable
# for every real committee member, so in practice every vote is recorded by an
# administrator and (correctly, after this fix) stamped on_behalf = 1.
#
# That is a pre-existing authorisation-catalogue defect, not a DF-3 regression — DF-3
# only made it visible. Recorded as finding F2-23 and as a plan item; fixing it needs a
# governance decision (which roles may cast a committee vote?), so it is deliberately
# NOT patched by widening ADMIN:WRITE, which would hand committee administration to
# every voting member.
st, body = call("POST", P, t_so, {"vote": "FOR", "memberRole": "Member"})
check("member self-vote blocked by the ADMIN:WRITE endpoint guard (F2-23, pre-existing)", st, 403)

# 2. THE DEFECT: Alpha tries to vote in Beta's name. Must be 403.
st, body = call("POST", P, t_so, {"memberName": B, "vote": "FOR", "memberRole": "Member"})
check("member votes in ANOTHER member's name -> forbidden", st, 403)
if st == 403:
    print(f"        message: {body[:160]}")

# 3. Non-roster name, even from an authority holder.
st, _ = call("POST", P, t_admin, {"memberName": "Nobody At All", "vote": "FOR"})
check("non-roster name -> rejected", st, 400)

# 4. admin (PLATFORM_ADMIN) records Alpha's vote on behalf.
st, _ = call("POST", P, t_admin, {"memberName": A, "vote": "AGAINST", "memberRole": "Member",
                                  "rationale": "recorded from the floor"})
check("secretary/chair records on behalf", st, 200)

# 5. Non-voting member cannot cast a counted vote.
st, _ = call("POST", P, t_admin, {"memberName": C, "vote": "FOR"})
check("non-voting member -> rejected", st, 400)

# ── what actually landed in the database ────────────────────────────────────
cur.execute("""SELECT member_name, vote, recorded_by_user_id, recorded_by_name, on_behalf
               FROM admin.committee_resolution_vote WHERE resolution_id = ? ORDER BY member_name""", RES)
rows = cur.fetchall()
print("\n  stored votes (member | vote | recorded_by | name | on_behalf):")
for r in rows:
    print(f"    {r[0]:34} {r[1]:8} {str(r[2]):6} {str(r[3]):12} {r[4]}")

by = {r[0]: r for r in rows}
check("Alpha's row exists exactly once", len(rows), 1)
if A in by:
    r = by[A]
    check("the on-behalf record is the only vote row for Alpha", r[1], "AGAINST")
    check("on_behalf stamped on the secretary's record", int(r[4]), 1)
    check("recorded_by is the SECRETARY, not the member", r[2], users.get("admin"))
check("no row was created for the forged name", B in by, False)
check("no row was created for the non-roster name", "Nobody At All" in by, False)

# ── cleanup (CHARINDEX, never LIKE — brackets are a character class) ────────
cur.execute("DELETE FROM admin.committee_resolution_vote WHERE resolution_id = ?", RES)
cur.execute("DELETE FROM admin.committee_resolutions WHERE id = ?", RES)
cur.execute("DELETE FROM admin.committee_member_terms WHERE CHARINDEX(?, member_name) > 0", TAG)
print("\n  cleaned up test rows")

bad = [r for r in results if not r[0]]
print(f"\n{'=' * 80}\n{len(results) - len(bad)}/{len(results)} checks passed")
sys.exit(1 if bad else 0)
