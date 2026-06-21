#!/usr/bin/env python3
"""Apply adversarial-verification verdicts to all_findings.json.
Reads audit/verdicts.json [{id,status,finalSeverity,rationale}], updates each matching
finding's severity to finalSeverity and attaches _verification, then re-sorts and rewrites."""
import json, os
ROOT = r"D:\AYMAN\GRC"
SEVS = ["Critical","High","Medium","Low","Info"]
PHASES = ["Security","Quality","Performance","Functional","E2E","UX","Accessibility"]

with open(os.path.join(ROOT,"audit","all_findings.json"),encoding="utf-8") as f:
    findings = json.load(f)
vp = os.path.join(ROOT,"audit","verdicts.json")
if not os.path.exists(vp):
    print("no verdicts.json — nothing to apply"); raise SystemExit
with open(vp,encoding="utf-8") as f:
    verdicts = json.load(f)

vmap = {v["id"]: v for v in verdicts}
changed = {"confirmed":0,"adjusted":0,"refuted":0}
downgrades = 0
for it in findings:
    v = vmap.get(it["id"])
    if not v: continue
    changed[v["status"]] = changed.get(v["status"],0)+1
    old = it["severity"]
    new = v.get("finalSeverity") or old
    if new in SEVS and new != old:
        downgrades += 1
    it["_verification"] = {"status": v["status"], "rationale": v.get("rationale",""), "from": old, "to": new}
    if new in SEVS:
        it["severity"] = new

sev_rank={s:i for i,s in enumerate(SEVS)}; ph_rank={p:i for i,p in enumerate(PHASES)}
findings.sort(key=lambda x:(ph_rank.get(x["phase"],9),sev_rank.get(x["severity"],9),x["id"]))
with open(os.path.join(ROOT,"audit","all_findings.json"),"w",encoding="utf-8") as f:
    json.dump(findings,f,ensure_ascii=False,indent=2)

print(f"Applied {len(verdicts)} verdicts: {changed}")
print(f"Severity changes: {downgrades}")
matrix={s:0 for s in SEVS}
for it in findings: matrix[it["severity"]]=matrix.get(it["severity"],0)+1
print("New severity totals: "+" · ".join(f"{s}={matrix[s]}" for s in SEVS))
