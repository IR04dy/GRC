#!/usr/bin/env python3
"""Dump the full content of specific Write/Edit ops on a file."""
import json, sys, re
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")

JSONL = Path(sys.argv[1])
file_pattern = re.compile(sys.argv[2], re.I)
which = int(sys.argv[3])  # 0-indexed which op (after sort)
field = sys.argv[4] if len(sys.argv) > 4 else "new"  # 'new' or 'old' or 'content'
out = Path(sys.argv[5]) if len(sys.argv) > 5 else None

ops = []
with JSONL.open(encoding="utf-8") as f:
    for line in f:
        try:
            rec = json.loads(line)
        except Exception:
            continue
        if rec.get("type") != "assistant":
            continue
        for block in rec.get("message", {}).get("content", []) or []:
            if not isinstance(block, dict) or block.get("type") != "tool_use":
                continue
            inp = block.get("input", {}) or {}
            p = inp.get("file_path") or ""
            if not file_pattern.search(p):
                continue
            ops.append((rec.get("timestamp", ""), block.get("name"), inp))

ops.sort(key=lambda x: x[0])
ts, name, inp = ops[which]
text = ""
if name == "Edit":
    if field == "new":
        text = inp.get("new_string", "")
    else:
        text = inp.get("old_string", "")
elif name == "Write":
    text = inp.get("content", "")

if out:
    out.write_text(text, encoding="utf-8")
    print(f"Wrote {len(text)} chars to {out}")
else:
    print(text)
