#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INDEX="$ROOT_DIR/index.html"
README="$ROOT_DIR/README.md"

[[ -f "$INDEX" ]] || { echo "Missing index.html"; exit 1; }
[[ -f "$README" ]] || { echo "Missing README.md"; exit 1; }

# Basic structural checks for the SPA shell and core integrations
rg -q '<!DOCTYPE html>' "$INDEX"
rg -q '<title>منصة GRC الوطنية \| National GRC Platform</title>' "$INDEX"
rg -q 'chart\.js@4\.4\.0' "$INDEX"
rg -q 'html2pdf\.bundle\.min\.js' "$INDEX"
rg -q 'const RBAC_ROLES=' "$INDEX"
rg -q 'function exportPagePDF\(' "$INDEX"

# Docs sanity checks
rg -q '^# National GRC Platform \(Prototype\)' "$README"
rg -q 'python3 -m http\.server 8080' "$README"

echo "Smoke tests passed."
