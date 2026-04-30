#!/usr/bin/env python3
"""Wrap hardcoded text in cyber/index.html with data-i18n attributes."""
import re
import sys
from pathlib import Path

sys.stdout.reconfigure(encoding="utf-8")
HTML = Path("C:/AYMAN/Github/Claude-GRC-2/tss-grc-ui/cyber/index.html")
text = HTML.read_text(encoding="utf-8")

MAP = {
    # Reuse existing
    "Title":             "label.title",
    "Owner":             "cyber.field.owner",
    "Maturity":          "cyber.field.maturity",
    "Type":              "cyber.field.type",
    "Status":            "cyber.field.status",
    "Severity":          "cyber.field.severity",
    "Asset":             "cyber.field.asset",
    "Priority":          "cyber.field.priority",
    "Result":            "cyber.field.result",
    "Version":           "cyber.field.version",
    # New label.* keys
    "Impl. Status":      "label.implStatus",
    "Parent Control":    "label.parentControl",
    "Assessor":          "label.assessor",
    "Date":              "label.date",
    "CAPA":              "label.capa",
    "Vulnerability":     "label.vulnerability",
    "CVE":               "label.cve",
    "SLA (days)":        "label.slaDays",
    "Due Date":          "label.dueDate",
    "Exception Title":   "label.exceptionTitle",
    "Approver":          "label.approver",
    "Expiry Date":       "label.expiryDate",
    "Incident #":        "label.incidentNo",
    "Affected Asset":    "label.affectedAsset",
    "Impact":            "label.impact",
    "Lead":              "label.lead",
    "Asset Name":        "label.assetName",
    "Business Owner":    "label.businessOwner",
    "Technical Owner":   "label.technicalOwner",
    "Classification":    "label.classification",
    "Criticality":       "label.criticality",
    "IP / Host":         "label.ipHost",
    "Evidence Title":    "label.evidenceTitle",
    "Linked Control":    "label.linkedControl",
    "Framework Req.":    "label.frameworkReq",
    "Review Date":       "label.reviewDate",
    "Source":            "label.source",
    # Modal titles
    "Add Control":       "cyber.btn.addControl",
    "Add Sub-Control":   "cyber.btn.addSubControl",
    "Add Asset":         "cyber.btn.addAsset",
    "Add Evidence":      "cyber.btn.addEvidence",
    "Add Action Plan":   "cyber.btn.addActionPlan",
    "Delete":            "common.delete",
    "Loading…":          "common.loading",
}

used = []
changes = [0]

def make_repl(tag_name):
    pattern = re.compile(
        r'<(' + tag_name + r')(\b[^>]*)>([^<]+?)</\1>',
        re.IGNORECASE,
    )
    def repl(m):
        attrs = m.group(2) or ""
        if 'data-i18n' in attrs:
            return m.group(0)
        inner = m.group(3).strip()
        if inner not in MAP:
            return m.group(0)
        key = MAP[inner]
        used.append((key, inner))
        changes[0] += 1
        new_attrs = (attrs.rstrip() + " " if attrs.strip() else " ") + f'data-i18n="{key}"'
        return f'<{m.group(1)}{new_attrs}>{m.group(3)}</{m.group(1)}>'
    return pattern, repl

# Wrap th, h5, button, td (where it's only hardcoded text), label
for tag in ("th", "h5", "button", "label", "td"):
    pat, repl = make_repl(tag)
    text = pat.sub(repl, text)

HTML.write_text(text, encoding="utf-8")
print(f"Total replacements: {changes[0]}")
print("Used keys (deduped):")
for k in sorted(set(u[0] for u in used)):
    matching = next(v for kk, v in used if kk == k)
    print(f"  {k:35s} -> {matching!r}")
