import json, sys, re
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")
JSONL = Path(sys.argv[1])
target_idx = int(sys.argv[2])
out_old = Path(sys.argv[3])
out_new = Path(sys.argv[4])

pat = re.compile(r"translations\.js$", re.I)
ops = []
with JSONL.open(encoding="utf-8") as f:
    for line in f:
        try: rec = json.loads(line)
        except: continue
        if rec.get("type") != "assistant": continue
        for block in rec.get("message", {}).get("content", []) or []:
            if not isinstance(block, dict) or block.get("type") != "tool_use": continue
            inp = block.get("input", {}) or {}
            p = inp.get("file_path") or ""
            if not pat.search(p): continue
            ops.append((rec.get("timestamp",""), block.get("name"), inp))

ops.sort(key=lambda x: x[0])
ts, name, inp = ops[target_idx]
old = inp.get("old_string","") if name == "Edit" else ""
new = inp.get("new_string","") if name == "Edit" else inp.get("content","")
out_old.write_text(old, encoding="utf-8")
out_new.write_text(new, encoding="utf-8")
print(f"idx={target_idx} {ts} {name}  old={len(old)}  new={len(new)}")
