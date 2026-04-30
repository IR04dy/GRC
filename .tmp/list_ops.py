import json, sys, re
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")
JSONL = Path(sys.argv[1])
pat = re.compile(sys.argv[2], re.I)
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
            ts = rec.get("timestamp", "")
            if block.get("name") == "Edit":
                ops.append((ts, "Edit", p, len(inp.get("old_string","")), len(inp.get("new_string",""))))
            elif block.get("name") == "Write":
                ops.append((ts, "Write", p, 0, len(inp.get("content",""))))
ops.sort(key=lambda x: x[0])
for i, o in enumerate(ops):
    print(f"[{i}] {o[0]}  {o[1]:<5} old={o[3]:>5}  new={o[4]:>5}  {o[2]}")
