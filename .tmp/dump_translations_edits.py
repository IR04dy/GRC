#!/usr/bin/env python3
import json, sys, re
from pathlib import Path
sys.stdout.reconfigure(encoding="utf-8")
JSONL = Path(sys.argv[1])
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else None
if OUT:
    sys.stdout = OUT.open("w", encoding="utf-8")

target_re = re.compile(r"translations\.js$", re.I)
edits = []

with JSONL.open(encoding="utf-8") as f:
    for line in f:
        try:
            rec = json.loads(line)
        except Exception:
            continue
        if rec.get("type") != "assistant":
            continue
        for block in rec.get("message", {}).get("content", []) or []:
            if not isinstance(block, dict):
                continue
            if block.get("type") != "tool_use":
                continue
            name = block.get("name", "")
            inp = block.get("input", {}) or {}
            p = inp.get("file_path") or ""
            if not target_re.search(p):
                continue
            ts = rec.get("timestamp", "")
            if name == "Write":
                edits.append((ts, "WRITE", p, inp.get("content", "")))
            elif name == "Edit":
                edits.append((ts, "EDIT", p, (inp.get("old_string", ""), inp.get("new_string", ""), inp.get("replace_all", False))))

edits.sort(key=lambda x: x[0])
print(f"Total ops on translations.js: {len(edits)}")
for i, (ts, kind, p, payload) in enumerate(edits):
    print(f"\n----- [{i}] {ts} {kind} -----")
    print(f"PATH: {p}")
    if kind == "EDIT":
        old, new, ra = payload
        print(f"REPLACE_ALL: {ra}")
        print(f"OLD ({len(old)} chars):")
        print(old[:600])
        if len(old) > 600:
            print(f"... [+{len(old)-600} more]")
        print(f"NEW ({len(new)} chars):")
        print(new[:600])
        if len(new) > 600:
            print(f"... [+{len(new)-600} more]")
    else:
        print(f"CONTENT length: {len(payload)} chars")
