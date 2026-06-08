#!/usr/bin/env python3
"""Post-repair verification. Read-only."""
from __future__ import annotations
import sys, pyodbc, ftfy
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass
CONN = ("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
        "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
cn = pyodbc.connect(CONN, autocommit=True)
cur = cn.cursor()
ok = True

# (1) THE required byte check: ccm id=1 title_en  ≤ must be E2 89 A4
print("=" * 70)
print("(1) ccm.control_test_definition id=1 title_en — byte check")
print("=" * 70)
cur.execute("SELECT title_en FROM ccm.control_test_definition WHERE id=1")
val = cur.fetchone()[0]
b = val.encode("utf-8")
expected = b"Domain Admins membership \xe2\x89\xa4 5"
print(f"  value : {val!r}")
print(f"  utf8  : {b.hex(' ')}")
print(f"  has E2 89 A4 (≤): {'YES' if b'\xe2\x89\xa4' in b else 'NO'}")
print(f"  exact match expected bytes: {'PASS' if b == expected else 'FAIL'}")
ok &= (b == expected)

# (2) all ccm rows clean
print("\n" + "=" * 70)
print("(2) ccm.control_test_definition — any remaining mojibake?")
print("=" * 70)
cur.execute("SELECT id,test_code,title_en,title_ar,description_en,description_ar "
            "FROM ccm.control_test_definition ORDER BY id")
ccm_bad = 0
for r in cur.fetchall():
    for c, v in zip(["title_en","title_ar","description_en","description_ar"], r[2:]):
        if isinstance(v, str) and v and ftfy.fix_encoding(v) != v:
            ccm_bad += 1
            print(f"  STILL BAD id={r[0]} {c}")
print(f"  remaining mojibaked cells in ccm: {ccm_bad}  ->  {'PASS' if ccm_bad==0 else 'FAIL'}")
ok &= (ccm_bad == 0)

# (3) DB-wide remaining mojibake (fix_encoding-positive)
print("\n" + "=" * 70)
print("(3) DB-wide remaining encoding-mojibake")
print("=" * 70)
cur.execute("""SELECT s.name,t.name,c.name FROM sys.columns c
JOIN sys.tables t ON c.object_id=t.object_id JOIN sys.schemas s ON t.schema_id=s.schema_id
JOIN sys.types ty ON c.user_type_id=ty.user_type_id
WHERE ty.name IN('nvarchar','nchar','ntext') AND s.name<>'flyway'
AND t.name<>'flyway_schema_history' AND c.is_computed=0
ORDER BY s.name,t.name,c.column_id""")
by={}
for s_,t_,c_ in cur.fetchall(): by.setdefault((s_,t_),[]).append(c_)
remaining=0; rem_detail={}
for (s_,t_),cols in by.items():
    cur.execute("SELECT 1 FROM sys.columns c JOIN sys.tables t ON c.object_id=t.object_id "
                "JOIN sys.schemas s ON t.schema_id=s.schema_id WHERE s.name=? AND t.name=? AND c.name='id'", s_,t_)
    if not cur.fetchone(): continue
    try:
        cur.execute(f"SELECT id,{', '.join('['+c+']' for c in cols)} FROM [{s_}].[{t_}]")
        rows=cur.fetchall()
    except pyodbc.Error: continue
    for rr in rows:
        for i,c in enumerate(cols,1):
            v=rr[i]
            if isinstance(v,str) and v and ftfy.fix_encoding(v)!=v:
                remaining+=1; rem_detail[f"{s_}.{t_}.{c}"]=rem_detail.get(f"{s_}.{t_}.{c}",0)+1
print(f"  remaining mojibaked cells DB-wide: {remaining}")
for k,n in rem_detail.items(): print(f"    {k}: {n}")
print(f"  -> {'PASS' if remaining==0 else 'FAIL'}")
ok &= (remaining==0)

# (4) the 12 fix_text-only cells must be UNCHANGED (no regression)
print("\n" + "=" * 70)
print("(4) legitimate cells left untouched (5×5, JSON) — sanity")
print("=" * 70)
cur.execute("SELECT body_en_md FROM kb.articles WHERE id=1")
kb = cur.fetchone()[0]
print(f"  kb.articles id=1 contains '5×5': {'YES (untouched)' if '5×5' in kb else 'NO (!)'}")
ok &= ('5×5' in kb)
cur.execute("SELECT content_json FROM sust.sustainability_report WHERE id=2")
js = cur.fetchone()[0]
import json as _j
try:
    _j.loads(js); print("  sust.content_json id=2 still valid JSON: YES")
except Exception as e:
    print(f"  sust.content_json id=2 JSON parse FAILED: {e}"); ok=False

# (5) long cells not truncated + Arabic readable
print("\n" + "=" * 70)
print("(5) long-cell integrity (no truncation) + Arabic spot-check")
print("=" * 70)
cur.execute("SELECT DATALENGTH(content_html)/2, LEFT(content_html,40) FROM sust.sustainability_report WHERE id=2")
ln, head = cur.fetchone()
print(f"  sust.content_html len={ln} chars; head={head!r}")
ok &= (ln and ln > 4000)   # was ~6610; must still be large (not truncated to 4000)
cur.execute("SELECT LEFT(content_md_ar,30) FROM admin.committee_form_templates WHERE id=1")
ar = cur.fetchone()[0]
print(f"  committee content_md_ar id=1 head={ar!r}")
print(f"  Arabic clean (no Ø/Ù mojibake): {'YES' if not any(ch in ar for ch in 'ØÙ') else 'NO'}")
ok &= not any(ch in ar for ch in 'ØÙ')

print("\n" + "=" * 70)
print("OVERALL:", "✅ ALL CHECKS PASS" if ok else "❌ SOME CHECKS FAILED")
print("=" * 70)
cn.close()
sys.exit(0 if ok else 1)
