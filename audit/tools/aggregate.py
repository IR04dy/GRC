#!/usr/bin/env python3
"""Aggregate all finding JSON files, validate/normalize, dedupe, and emit:
   - audit/all_findings.json   (full normalized list)
   - audit/crit_high.json      (compact Critical/High list for the verification pass)
   Prints a phase x severity matrix.
"""
import json, glob, os, sys

ROOT = r"D:\AYMAN\GRC"
FIND_DIR = os.path.join(ROOT, "audit", "findings")
REQUIRED = ["id","title","phase","category","severity","cvss","file","lines","description",
            "rootCause","evidence","impact","reproduction","resolution","patchBefore",
            "patchAfter","effort","references"]
SEVS = ["Critical","High","Medium","Low","Info"]
PHASES = ["Security","Quality","Performance","Functional","E2E","UX","Accessibility"]

def norm_sev(s):
    s = (s or "").strip().capitalize()
    return s if s in SEVS else "Info"

def main():
    files = sorted(glob.glob(os.path.join(FIND_DIR, "*.json")))
    all_f, seen, problems = [], set(), []
    for fp in files:
        name = os.path.basename(fp)
        try:
            with open(fp, encoding="utf-8") as fh:
                data = json.load(fh)
        except Exception as e:
            problems.append(f"{name}: PARSE ERROR {e}")
            continue
        items = data.get("findings", data if isinstance(data, list) else [])
        for it in items:
            missing = [k for k in REQUIRED if k not in it]
            if missing:
                # tolerate: fill blanks so the report still renders, but record it
                for k in missing:
                    it[k] = [] if k == "references" else ""
                problems.append(f"{name} [{it.get('id','?')}]: filled missing {missing}")
            it["severity"] = norm_sev(it.get("severity"))
            if it.get("phase") not in PHASES:
                it["phase"] = it.get("phase") or "Security"
            if not isinstance(it.get("references"), list):
                it["references"] = [str(it.get("references"))] if it.get("references") else []
            if not isinstance(it.get("locations"), list):
                it["locations"] = []
            fid = it.get("id") or f"{name}-{len(all_f)}"
            if fid in seen:
                fid = f"{fid}-dup{len(all_f)}"
            it["id"] = fid
            it["_source"] = name
            seen.add(fid)
            all_f.append(it)

    # order: phase, then severity
    sev_rank = {s:i for i,s in enumerate(SEVS)}
    ph_rank = {p:i for i,p in enumerate(PHASES)}
    all_f.sort(key=lambda x: (ph_rank.get(x["phase"],9), sev_rank.get(x["severity"],9), x["id"]))

    with open(os.path.join(ROOT,"audit","all_findings.json"),"w",encoding="utf-8") as fh:
        json.dump(all_f, fh, ensure_ascii=False, indent=2)

    crit_high = [{"id":f["id"],"title":f["title"],"phase":f["phase"],"severity":f["severity"],
                  "category":f["category"],"file":f["file"],"lines":f["lines"]}
                 for f in all_f if f["severity"] in ("Critical","High")]
    with open(os.path.join(ROOT,"audit","crit_high.json"),"w",encoding="utf-8") as fh:
        json.dump(crit_high, fh, ensure_ascii=False, indent=2)

    # matrix
    matrix = {p:{s:0 for s in SEVS} for p in PHASES}
    for f in all_f:
        matrix[f["phase"]][f["severity"]] += 1
    print(f"TOTAL findings: {len(all_f)} from {len(files)} files")
    hdr = "Phase".ljust(14) + "".join(s.rjust(10) for s in SEVS) + "   TOTAL"
    print(hdr); print("-"*len(hdr))
    for p in PHASES:
        row = matrix[p]; tot = sum(row.values())
        if tot: print(p.ljust(14)+"".join(str(row[s]).rjust(10) for s in SEVS)+str(tot).rjust(9))
    tot_by_sev = {s:sum(matrix[p][s] for p in PHASES) for s in SEVS}
    print("ALL".ljust(14)+"".join(str(tot_by_sev[s]).rjust(10) for s in SEVS)+str(len(all_f)).rjust(9))
    print(f"\nCritical+High for verification: {len(crit_high)}")
    if problems:
        print(f"\n{len(problems)} data problems:")
        for p in problems[:40]: print("  - "+p)

if __name__ == "__main__":
    main()
