#!/usr/bin/env python3
"""Extract activities (user prompts + todo list state) from a Claude Code session JSONL."""
import json
import sys
from pathlib import Path

JSONL = Path(sys.argv[1])
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else None
sys.stdout.reconfigure(encoding="utf-8") if hasattr(sys.stdout, "reconfigure") else None
if OUT:
    sys.stdout = OUT.open("w", encoding="utf-8")

user_prompts = []
todo_snapshots = []   # list of (timestamp, todos)
last_todos = None
session_start = None
session_end = None

with JSONL.open(encoding="utf-8") as f:
    for line in f:
        try:
            rec = json.loads(line)
        except Exception:
            continue
        ts = rec.get("timestamp", "")
        if ts:
            if session_start is None or ts < session_start:
                session_start = ts
            if session_end is None or ts > session_end:
                session_end = ts
        if rec.get("type") == "user":
            msg = rec.get("message", {})
            content = msg.get("content")
            if isinstance(content, str) and content.strip():
                t = content.strip()
                if not t.startswith("<") and not t.startswith("[Request"):
                    user_prompts.append((ts, t))
            elif isinstance(content, list):
                for block in content:
                    if isinstance(block, dict) and block.get("type") == "text":
                        t = (block.get("text") or "").strip()
                        if t and not t.startswith("<") and not t.startswith("[Request"):
                            user_prompts.append((ts, t))
        if rec.get("type") == "assistant":
            msg = rec.get("message", {})
            content = msg.get("content", [])
            if isinstance(content, list):
                for block in content:
                    if not isinstance(block, dict):
                        continue
                    if block.get("type") == "tool_use" and block.get("name") == "TodoWrite":
                        todos = (block.get("input", {}) or {}).get("todos", [])
                        if todos:
                            todo_snapshots.append((ts, todos))
                            last_todos = todos

print("=" * 80)
print(f"SESSION SPAN")
print("=" * 80)
print(f"  start: {session_start}")
print(f"  end  : {session_end}")
print(f"  user prompts : {len(user_prompts)}")
print(f"  todo updates : {len(todo_snapshots)}")

print("\n" + "=" * 80)
print(f"USER PROMPTS (chronological)")
print("=" * 80)
for i, (ts, p) in enumerate(user_prompts):
    short = p.replace("\n", " ")[:300]
    print(f"\n[{i:3d}] {ts}")
    print(f"      {short}")

print("\n" + "=" * 80)
print(f"FINAL TODO LIST (last TodoWrite call at {todo_snapshots[-1][0] if todo_snapshots else 'N/A'})")
print("=" * 80)
if last_todos:
    for t in last_todos:
        status = t.get("status", "?")
        marker = {"completed": "[x]", "in_progress": "[~]", "pending": "[ ]"}.get(status, "[?]")
        print(f"  {marker} {t.get('content', '')}")
else:
    print("  (no TodoWrite calls in this session)")
