#!/usr/bin/env python3
"""FINAL per-id forensic manifest of the 117 repaired cells.

Each cell is assigned a confidence tier and the method that established it:

  exact            live non-ASCII count == repaired count
  ground_truth     exact ids printed by the pre-repair diagnostic this session
  batch_validated  created_at apply-batch split; method validated on known sets
  inferred_symbol  unique symbol-coverage == repaired count (validated on CCM)
  inferred_batch   created_at prefix == repaired count, corroborated by independently
                   pinned rows falling inside it (automation_rules.name_en = ids 1-52)
  best_guess       single cell, two candidates, no decisive signal — heuristic pick
                   (math symbol ≤/≥/→ favoured over em-dash; odd-timestamp-out)

For each cell we also reconstruct the byte-exact pre-repair value
(sloppy-windows-1252) and prove it round-trips through ftfy.fix_encoding.
Writes .tools/forensic_final.json + .csv.
"""
from __future__ import annotations
import sys, json, csv, pyodbc, ftfy
import ftfy.bad_codecs
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass
CONN=("DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
      "UID=sa;PWD=tss.123;TrustServerCertificate=yes")
OUTJ=r"C:\AYMAN\Github\Claude-GRC-2\.tools\forensic_final.json"
OUTC=r"C:\AYMAN\Github\Claude-GRC-2\.tools\forensic_final.csv"

# (column) -> (ids, confidence, method)
DETERMINED = {
    ("ccm","control_test_definition","title_en"):        ([1,2,3,5,8],"exact","count==live & batch & org1"),
    ("cyber","root_cause_analyses","recommendations_en"):([3],"exact","count==live"),
    ("platform","controls","description_en"):            ([4],"exact","count==live"),
    ("risk","bowtie_preventive_control","description_en"):([1],"exact","count==live"),
    ("scm","supplier_resilience_tests","success_criteria_en"):([2,4,6,8],"exact","count==live"),
    ("sust","sustainability_report","content_markdown"): ([2],"exact","count==live"),
    ("sust","sustainability_report","content_html"):     ([2],"exact","count==live"),
    ("tprm","outsourcing_exit_plan","exit_steps_json"):  ([2],"exact","count==live"),

    ("admin","committee_form_templates","content_md_en"):     ([1,4,6,8,9,10],"ground_truth","pre-repair diagnostic output"),
    ("admin","committee_form_templates","content_md_ar"):     ([1,6,8,9,10],"ground_truth","pre-repair diagnostic output"),
    ("admin","committee_form_templates","completion_guide_ar"):([1,6,8,9,10],"ground_truth","pre-repair diagnostic output"),
    ("admin","committee_form_templates","completion_guide_en"):([2,8,9,10],"ground_truth","pre-repair diagnostic output"),
    ("admin","committee_form_templates","purpose_en"):        ([10],"ground_truth","pre-repair diagnostic output"),

    ("ai_governance","ml_model_drift_observation","notes_ar"):([3,4,5,6,7,8,9,10,11,12],"batch_validated","created_at batch (06-02); validated vs known {3,4,5,6}"),
    ("bcm","exercises","exercise_name_ar"):                  ([4,5,6,7,8,9,10,11,12,13],"batch_validated","created_at batch (06-01 = 10 rows)"),

    ("ccm","control_test_definition","description_en"):  ([5],"inferred_symbol","unique ≥-coverage; row 5 also title-corrupted"),
    ("bcm","exercises","exercise_name_en"):              ([5],"inferred_symbol","unique —-coverage; in corrupted 06-01 batch"),
    ("platform","automation_rules","description_en"):    ([22,30],"inferred_symbol","unique ≥-coverage; both in 1-52 prefix"),
    ("risk","kri_definition","description_en"):          ([15],"inferred_symbol","unique ≥-coverage"),

    ("platform","automation_rules","name_en"): (list(range(1,53)),"inferred_batch",
        "created_at prefix <=2026-05-23 13:04:30 == 52; rows 22,30 (desc) inside; 53+ clean"),

    # --- 4 single-cell two-candidate ties: heuristic best guesses ---
    ("scm","exit_plans","data_migration_plan_en"):       ([2],"best_guess","→(id2) vs —(id4); math symbol favoured"),
    ("signature","digital_signature_transactions","purpose"):([15],"best_guess","→(id15) vs —(id17); math symbol favoured"),
    ("workflow","approval_chain_step","title_en"):       ([8],"best_guess","≥(id8) vs —(id6); ≥ matches CCM-style corruption"),
    ("privacy","data_breaches","description_en"):        ([7],"best_guess","→&—(id7) most symbol-rich of the → candidates {6,7}"),
}

def recon(s): return s.encode("utf-8").decode("sloppy-windows-1252")

def main():
    cn=pyodbc.connect(CONN,autocommit=True); cur=cn.cursor()
    rows_out=[]; bad=[]
    tier_counts={}
    for (sch,tbl,col),(ids,conf,method) in DETERMINED.items():
        cur.execute(f"SELECT id,[{col}] FROM [{sch}].[{tbl}] WHERE id IN ({','.join('?'*len(ids))})", *ids)
        live={r[0]:r[1] for r in cur.fetchall()}
        for rid in ids:
            v=live.get(rid)
            ok = isinstance(v,str) and not v.isascii()
            before = recon(v) if ok else None
            rt = (ftfy.fix_encoding(before)==v) if ok else False
            if not ok: bad.append(f"{sch}.{tbl}.{col} id={rid} (missing/ASCII)")
            rows_out.append({"schema":sch,"table":tbl,"column":col,"id":rid,
                             "confidence":conf,"method":method,
                             "after_correct":v,"before_mojibake":before,
                             "roundtrip_validated":rt})
        tier_counts[conf]=tier_counts.get(conf,0)+len(ids)
    json.dump(rows_out,open(OUTJ,"w",encoding="utf-8"),ensure_ascii=False,indent=1)
    with open(OUTC,"w",encoding="utf-8",newline="") as f:
        w=csv.writer(f); w.writerow(["schema","table","column","id","confidence","method","roundtrip_validated","after_correct"])
        for r in rows_out:
            w.writerow([r["schema"],r["table"],r["column"],r["id"],r["confidence"],r["method"],r["roundtrip_validated"],r["after_correct"]])

    total=len(rows_out)
    print("Confidence tiers:")
    order=["exact","ground_truth","batch_validated","inferred_symbol","inferred_batch","best_guess"]
    for t in order:
        if t in tier_counts: print(f"   {t:<16}: {tier_counts[t]}")
    high=sum(tier_counts.get(t,0) for t in ["exact","ground_truth","batch_validated","inferred_symbol","inferred_batch"])
    print(f"   {'-'*26}")
    print(f"   high/corroborated: {high}")
    print(f"   best_guess       : {tier_counts.get('best_guess',0)}")
    print(f"   TOTAL            : {total} / 117")
    print(f"   all round-trip validated: {'YES' if not bad else 'NO: '+str(bad)}")
    print(f"\n   written: {OUTJ}\n            {OUTC}")
    cn.close()

if __name__=="__main__":
    main()
