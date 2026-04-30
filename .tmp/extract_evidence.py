#!/usr/bin/env python3
"""Reconstruct file contents from a Claude Code session JSONL by replaying Write+Edit ops in order.

Handles:
- Both forward-slash MSYS paths (/c/AYMAN/...) and backslash Windows paths (C:\\AYMAN\\...)
- Multiple worktree-prefix variants (.claude\\worktrees\\quirky-borg, .claire\\worktrees\\quirky-borg, laughing-benz, etc.)
- Path-style normalization so ops on the same logical file get merged chronologically
"""
import json
import sys
import re
from pathlib import Path
from collections import defaultdict

JSONL = Path(sys.argv[1])
OUT_DIR = Path(sys.argv[2])
sys.stdout.reconfigure(encoding="utf-8") if hasattr(sys.stdout, "reconfigure") else None

# Recognise these prefixes and strip them off (whichever style they appear in)
PREFIXES = [
    r"C:\AYMAN\Github\Claude-GRC\.claude\worktrees\laughing-benz-d27606",
    r"C:\AYMAN\Github\Claude-GRC\.claude\worktrees\quirky-borg",
    r"C:\AYMAN\Github\Claude-GRC\.claire\worktrees\quirky-borg",
    r"C:\AYMAN\Github\Claude-GRC",  # bare repo root, last so above match first
]


def to_msys(p):
    """Backslash C: path -> forward-slash /c/ path, lowercased."""
    p = p.replace("\\", "/")
    if re.match(r"^[A-Za-z]:/", p):
        p = "/" + p[0].lower() + p[2:]
    return p


PREFIXES_MSYS = [to_msys(x) for x in PREFIXES]


def normalize(path):
    """Strip any known prefix and return (relative_posix_path, matched) or (None, False) if no prefix matched."""
    p = to_msys(path)
    for pref in PREFIXES_MSYS:
        if p.lower().startswith(pref.lower()):
            rel = p[len(pref):].lstrip("/")
            return rel, True
    return None, False


# Filter: which relative paths are part of the evidence module / SQL / shared
KEEP_PATTERNS = [
    re.compile(r"(^|/)module/evidence/", re.I),
    re.compile(r"^tss-grc-ui/evidence/", re.I),
    re.compile(r"setup_evidence", re.I),
    re.compile(r"migration/.*evidence", re.I),
    re.compile(r"evidence.*\.sql$", re.I),
    re.compile(r"V\d+__evidence", re.I),
    re.compile(r"V_evidence", re.I),
]
SHARED_PATTERNS = [
    re.compile(r"tss-grc-ui/assets/js/translations\.js$", re.I),
    re.compile(r"tss-grc-ui/assets/js/app\.js$", re.I),
]
ALL_PATTERNS = KEEP_PATTERNS + SHARED_PATTERNS


# Replay state per *normalized* path
ops = defaultdict(list)   # rel_path -> list of (timestamp, kind, payload)
unrecognized_paths = set()

with JSONL.open(encoding="utf-8") as f:
    for line in f:
        try:
            rec = json.loads(line)
        except Exception:
            continue
        if rec.get("type") != "assistant":
            continue
        msg = rec.get("message", {})
        content = msg.get("content", [])
        if not isinstance(content, list):
            continue
        for block in content:
            if not isinstance(block, dict):
                continue
            if block.get("type") != "tool_use":
                continue
            name = block.get("name", "")
            inp = block.get("input", {}) or {}
            ts = rec.get("timestamp", "")
            p = inp.get("file_path")
            if not p:
                continue
            rel, matched = normalize(p)
            if not matched:
                unrecognized_paths.add(p)
                continue
            if name == "Write":
                c = inp.get("content")
                if c is not None:
                    ops[rel].append((ts, "write", c))
            elif name == "Edit":
                o = inp.get("old_string")
                n = inp.get("new_string")
                ra = inp.get("replace_all", False)
                if o is not None and n is not None:
                    ops[rel].append((ts, "edit", (o, n, ra)))


def matches_any(rel, patterns):
    return any(p.search(rel) for p in patterns)


evidence_files = {p: o for p, o in ops.items() if matches_any(p, KEEP_PATTERNS)}
shared_files = {p: o for p, o in ops.items() if matches_any(p, SHARED_PATTERNS)}

print(f"Total normalized paths with ops: {len(ops)}")
print(f"Unrecognized prefixes (skipped): {len(unrecognized_paths)}")
for u in list(unrecognized_paths)[:5]:
    print(f"  ?  {u}")

print(f"\nEVIDENCE files ({len(evidence_files)}):")
for p in sorted(evidence_files):
    n_w = sum(1 for _, k, _ in evidence_files[p] if k == "write")
    n_e = sum(1 for _, k, _ in evidence_files[p] if k == "edit")
    print(f"  {p}  (writes={n_w}, edits={n_e})")

print(f"\nSHARED files (translations/app.js) ({len(shared_files)}):")
for p in sorted(shared_files):
    n_w = sum(1 for _, k, _ in shared_files[p] if k == "write")
    n_e = sum(1 for _, k, _ in shared_files[p] if k == "edit")
    print(f"  {p}  (writes={n_w}, edits={n_e})")


def reconstruct(rel_path, op_list, allow_disk_base=False):
    """Replay ops in chronological order. Returns (content, error)."""
    op_list = sorted(op_list, key=lambda x: x[0])
    content = None
    if allow_disk_base:
        disk = OUT_DIR / rel_path
        if disk.is_file():
            content = disk.read_text(encoding="utf-8")
    for ts, kind, payload in op_list:
        if kind == "write":
            content = payload
        elif kind == "edit":
            old, new, replace_all = payload
            if content is None:
                return None, f"edit before any Write at {ts} (no disk base)"
            if replace_all:
                content = content.replace(old, new)
            else:
                cnt = content.count(old)
                if cnt == 0:
                    return None, f"edit at {ts}: old_string not found"
                # Spec says exactly one occurrence; allow first-occurrence replacement
                content = content.replace(old, new, 1)
    return content, None


print("\n=== RECONSTRUCTING EVIDENCE FILES ===")
written = 0
failed = []
for rel, op_list in evidence_files.items():
    content, err = reconstruct(rel, op_list, allow_disk_base=False)
    if content is None:
        failed.append((rel, err))
        continue
    dest = OUT_DIR / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(content, encoding="utf-8")
    written += 1
    print(f"  WROTE {rel}  ({len(content)} bytes)")

print("\n=== RECONSTRUCTING SHARED FILES (with on-disk base) ===")
shared_written = 0
shared_failed = []
for rel, op_list in shared_files.items():
    content, err = reconstruct(rel, op_list, allow_disk_base=True)
    if content is None:
        shared_failed.append((rel, err))
        continue
    dest = OUT_DIR / rel
    dest.parent.mkdir(parents=True, exist_ok=True)
    dest.write_text(content, encoding="utf-8")
    shared_written += 1
    print(f"  WROTE {rel}  ({len(content)} bytes)")

print(f"\nReconstructed evidence: {written}/{len(evidence_files)}")
print(f"Reconstructed shared:   {shared_written}/{len(shared_files)}")
if failed:
    print("\nEvidence failures:")
    for p, err in failed:
        print(f"  FAIL {p}  -- {err}")
if shared_failed:
    print("\nShared failures:")
    for p, err in shared_failed:
        print(f"  FAIL {p}  -- {err}")
