"""Platform map generator — scans the repo and emits the platform graph twice:

  docs/PLATFORM_MAP.cypher   Neo4j-loadable graph (MERGE-idempotent, constraints included)
  docs/PLATFORM_MAP.md       the same map as a human-readable document

Everything structural is DETECTED from code (modules, controllers, API base
paths, DB schemas, schedulers, cross-module imports, FE routes); the curated
dictionaries below add only what code cannot express (one-line descriptions,
regulator anchors, screen-prefix ownership). Re-run after structural changes:

  py .tools/gen_platform_map.py
"""
from __future__ import annotations
import re, subprocess, sys
from collections import defaultdict
from datetime import date
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BE   = ROOT / "tss-grc-backend/src/main/java/com/tss/grc/module"
APP  = ROOT / "tss-grc-ui-v2/src/App.tsx"
OUT_CYPHER = ROOT / "docs/PLATFORM_MAP.cypher"
OUT_MD     = ROOT / "docs/PLATFORM_MAP.md"

# ── curated: one-line description per backend module ─────────────────────────
MODULE_DESC = {
    "admin": "Committees (catalog/papers/actions), branding, licensing, org administration",
    "ai": "AI suggestions — control mapper, drafting aids",
    "aml": "Anti-money-laundering sanctions screening (auxiliary)",
    "approvals": "Legacy approval-chain endpoints (superseded by the workflow engine)",
    "attachment": "Generic file-attachment endpoints (auxiliary)",
    "audit_committee": "Audit-committee dashboard aggregate",
    "audit_eng": "Internal-audit engagements (auxiliary)",
    "audit_lc": "Internal-audit lifecycle screens aggregate",
    "auth": "JWT auth: login/refresh cookie, sessions, lockout, SSO/Nafath",
    "bcm": "Business continuity: BIA, BCP/DR, exercises, crisis, NCEMA/OpRes packets",
    "bcm_lc": "BCM lifecycle screens aggregate",
    "board_pack": "Executive board-pack export",
    "capa": "Platform CAPA tasks (global register; distinct from sust auto-CAPA)",
    "ccm": "Continuous control monitoring (locked-down SQL runner)",
    "change_mgmt": "Change-management register (auxiliary)",
    "compliance": "Obligations, frameworks/controls, findings+CAPA, posture, horizon",
    "consumer": "Consumer-protection complaints (auxiliary)",
    "controlmap": "Control crosswalk / framework mapping",
    "cpty": "Counterparty register (auxiliary)",
    "cyber": "NCA ECC, assets, vulnerabilities+SLA, threat intel, 9-step incidents, cloud",
    "cyber_lc": "Cyber lifecycle screens aggregate",
    "dashboard": "Executive dashboard aggregates",
    "data_officer": "Data-officer privacy console",
    "dataclass": "Data classification register (auxiliary)",
    "datagov": "Data governance: retention, disposal, catalog, lineage, quality",
    "ethics": "Ethics: whistleblower, conflicts of interest (auxiliary)",
    "evidence": "Evidence subsystem: SHA-256 items, four-eyes review, requests, typed links",
    "fraud": "Fraud register (auxiliary)",
    "governance": "Governance charters/bodies (auxiliary)",
    "iam": "Users/roles/permission matrix + access governance (maker-checker, SoD, recert, break-glass)",
    "incident": "Platform incidents + RCA",
    "intake": "Public intake portal + triage inbox",
    "integrations": "SIEM/EDR/VM/ITSM connectors: Splunk, CrowdStrike, Tenable, ServiceNow",
    "knowledge": "Knowledge base",
    "nca_ecc": "NCA ECC self-assessment filings + export",
    "ndi": "NDI governance + scheduled reports",
    "openbanking": "Open-banking register (auxiliary)",
    "opres": "SAMA operational-resilience packet",
    "outsourcing": "SAMA outsourcing register (admin approval queue)",
    "platform": "Platform services: audit ledger, notifications, brand theme, org settings",
    "policy": "Policy documents: versioned files, review cycle, SoD approvals",
    "privacy": "PDPL: RoPA, DPIA, DSR (30-day), breach (72-hour), consent, transfers",
    "privacy_lc": "Privacy lifecycle screens aggregate (orphaned *_lc tables landmine)",
    "regchange": "Regulatory-change register",
    "reginspect": "Regulatory inspections (auxiliary)",
    "regreporting": "Regulatory reporting calendar (auxiliary)",
    "reporting": "Export engine: branded PDF (Amiri RTL), XLSX/DOCX/PPTX, packets, export history",
    "risk": "ERM: register, appetite, KRI, treatments, acceptance, FAIR, scenarios",
    "risk_lc": "Risk lifecycle screens aggregate",
    "rrp": "Recovery & resolution planning (auxiliary)",
    "scm": "Supply chain: fourth-party map, contract clauses, vendor portal",
    "sharia": "Shariah governance (auxiliary)",
    "signature": "E-signature requests",
    "socops": "SOC operations: 20 aggregates — SIEM events, DLP, forensics custody, reviews",
    "sust": "Sustainability/ESG: GHG (15 Scope-3 cats), disclosures, green finance/portfolio, SGI, horizon, auto-CAPA",
    "tprm": "Third-party risk: vendors, assessments, outsourcing, BCP campaigns",
    "workflow": "Workflow request engine: approvals/reviews/acks/broadcasts, SLA+escalation, EXTERNAL tokens",
    "workflow_submission": "Workflow submissions + structured request forms",
}

# canonical modules named in CLAUDE.md; everything else = auxiliary/V1-era
CANONICAL = {
    "admin","ai","auth","bcm","compliance","cyber","dashboard","datagov","evidence",
    "iam","incident","intake","ndi","platform","privacy","reporting","risk","scm",
    "socops","sust","tprm","workflow",
}

# ── curated: regulator / framework anchors (module -> regulators) ────────────
REGULATORS = {
    "NCA ECC":            ["cyber", "nca_ecc", "iam"],
    "SAMA CSF":           ["cyber", "iam"],
    "SAMA OpRes":         ["bcm", "opres"],
    "SAMA Outsourcing":   ["tprm", "outsourcing"],
    "PDPL / SDAIA":       ["privacy", "datagov", "cyber", "iam"],
    "CMA":                ["sust"],
    "Tadawul ESG":        ["sust"],
    "SOCPA IFRS S1/S2":   ["sust"],
    "GHG Protocol":       ["sust"],
    "ISAE 3410":          ["sust"],
    "TCFD / IFRS S2":     ["sust"],
    "Saudi Green Initiative": ["sust"],
    "ICMA GBP / CCE":     ["sust"],
    "RVCMC":              ["sust"],
    "NCEC":               ["sust"],
    "EU CBAM":            ["sust"],
    "PCAF":               ["sust"],
    "NCEMA":              ["bcm"],
    "MWAN / SEEC":        ["sust"],
}

# ── curated: shared platform services (id -> (provider, description)) ────────
SERVICES = {
    "WorkflowEngine":    ("workflow",  "Generic request engine — consumed via REST + record-anchored FE panels (detected from ModuleEngineRequestsPanel imports)"),
    "EvidenceSubsystem": ("evidence",  "SHA-256 uploads, four-eyes review, typed links, requests w/ SLA"),
    "ExportStack":       ("reporting", "PdfTheme+Amiri RTL, XlsxStyler, TabularExportService, ExportNaming, export audit"),
    "AuditLedger":       ("platform",  "platform.audit_log — before/after JSON, every export logged"),
    "Notifications":     ("platform",  "In-app notifications: notifyUser + broadcastToRole (SMTP pending relay)"),
    "AutoCapaEngine":    ("sust",      "sust.corrective_action — idempotent (source_type, source_ref) auto-CAPAs for 10+ gap types; internal to sust (ck_ca_source whitelist gates new sources)"),
}
# import-prefix -> service id (detection rules)
SERVICE_IMPORT = {
    "com.tss.grc.module.workflow.":                    "WorkflowEngine",
    "com.tss.grc.module.evidence.":                    "EvidenceSubsystem",
    "com.tss.grc.module.reporting.":                   "ExportStack",
    "com.tss.grc.module.platform.auditlog.":           "AuditLedger",
    "com.tss.grc.module.platform.service.Notification":"Notifications",
    "com.tss.grc.module.sust.capa.":                   "AutoCapaEngine",
}

EXTERNAL = {
    "Splunk":      ("integrations", "SIEM HEC ingest (HMAC fail-closed; stub+live)"),
    "CrowdStrike": ("integrations", "EDR detections pull (stub+live)"),
    "Tenable":     ("integrations", "Vulnerability import (stub+live)"),
    "ServiceNow":  ("integrations", "ITSM ticket bridge (stub+live)"),
    "SMTP relay":  ("platform",     "Outbound mail — configured via external.properties, off until a relay exists"),
    "LDAP / AD":   ("auth",         "Directory auth + role mapping (optional)"),
    "Nafath SSO":  ("auth",         "Saudi national SSO (screen present)"),
    "Sentry":      ("platform",     "Error monitoring (optional DSN)"),
}

# ── curated: FE route-prefix -> owning backend module (longest prefix wins) ──
ROUTE_OWNER = {
    "/login": "auth", "/sso": "auth", "/account": "auth",
    "/dashboard": "dashboard", "/settings": "admin", "/admin": "admin",
    "/committees": "admin", "/onboarding": "admin", "/branding": "admin",
    "/risk": "risk", "/emerging-risk": "risk", "/risk-scenarios": "risk",
    "/risk-lifecycle": "risk_lc",
    "/compliance": "compliance", "/regulatory": "compliance",
    "/regulatory-change": "regchange", "/framework-controls": "compliance",
    "/controls": "compliance", "/control-crosswalk": "controlmap",
    "/policy": "policy", "/policy-management": "policy", "/policy-templates": "policy",
    "/cyber": "cyber", "/third-party-cyber": "cyber", "/nca-ecc": "nca_ecc",
    "/cyber-lifecycle": "cyber_lc", "/socops": "socops", "/soc": "socops",
    "/bcm": "bcm", "/dr-plans": "bcm", "/crisis": "bcm",
    "/resilience-tests": "bcm", "/bcm-lifecycle": "bcm_lc",
    "/privacy": "privacy", "/pdpl": "privacy", "/pdpl-extensions": "privacy",
    "/ropa": "privacy", "/data-lifecycle": "privacy", "/data-sharing": "privacy",
    "/processor-assessments": "privacy", "/privacy-lifecycle": "privacy_lc",
    "/data-governance": "datagov", "/datagov": "datagov",
    "/tprm": "tprm", "/scm": "scm", "/fourth-party": "scm",
    "/contract-clauses": "scm", "/portal/vendor": "scm", "/vendors": "tprm",
    "/esg": "sust", "/green-finance": "sust", "/sustainability": "sust",
    "/access-governance": "iam", "/users": "iam", "/roles": "iam",
    "/approvals": "workflow", "/attestations": "workflow",
    "/workflow-versions": "workflow", "/workflows": "workflow_submission",
    "/signatures": "signature",
    "/evidence": "evidence", "/evidence-chain": "evidence", "/file-upload": "evidence",
    "/export-history": "reporting", "/board-pack": "board_pack",
    "/audit-log": "platform", "/notifications": "platform",
    "/connectors": "integrations", "/intake": "intake", "/public/intake": "intake",
    "/issues": "incident", "/incidents": "incident", "/rca": "incident",
    "/ndi": "ndi", "/knowledge-base": "knowledge", "/ai": "ai",
    "/aml": "aml", "/ethics": "ethics", "/whistleblower": "ethics",
    "/fraud": "fraud", "/cpty": "cpty", "/audit": "audit_lc",
    "/capa": "capa", "/ccm": "ccm", "/change": "change_mgmt",
    "/complaints": "consumer", "/openbanking": "openbanking", "/opres": "opres",
    "/outsourcing": "outsourcing", "/rrp": "rrp", "/sharia": "sharia",
    "/reginspect": "reginspect", "/regreporting": "regreporting",
    "/governance": "governance", "/data-officer": "data_officer",
    "/dataclass": "dataclass", "/audit-committee": "audit_committee",
    # second sweep — resolved via page-component directories
    "/": "dashboard", "*": "platform",
    "/assets": "cyber", "/authorities": "compliance", "/authority": "compliance",
    "/buyer-gap": "scm", "/supplier-resilience": "scm",
    "/calendar": "platform", "/communications": "platform",
    "/lifecycle-overview": "platform", "/portal/trust-center": "platform",
    "/expert": "ai", "/findings": "compliance", "/framework-packs": "compliance",
    "/dpias": "privacy", "/portal/dsr": "privacy",
    "/policies": "policy",
    "/portal/speakup": "ethics", "/portal/track": "intake",
    "/public/complaint": "consumer", "/public/track-complaint": "consumer",
    "/public/workflow-requests": "workflow",
    "/reports": "reporting",
}

# page-dir (tss-grc-ui-v2/src/pages/<dir>) -> backend module, for FE-side
# detection of workflow-engine adoption (the engine is consumed via REST +
# record-anchored panels, not Java imports — import-scanning alone misses it).
PAGE_DIR_MODULE = {
    "audit": "audit_lc", "bcm": "bcm", "compliance": "compliance", "cyber": "cyber",
    "detail": "policy",  # PolicyDetailPage lives under pages/detail/
    "esg": "sust", "evidence": "evidence", "incidents": "incident", "ndi": "ndi",
    "privacy": "privacy", "reporting": "reporting", "risk": "risk", "scm": "scm",
    "socops": "socops", "tprm": "tprm", "workflow": "workflow",
    "changes": "change_mgmt", "ethics": "ethics",
}

# ══ detection ════════════════════════════════════════════════════════════════
RE_REQMAP  = re.compile(r'@RequestMapping\(\s*"([^"]+)"')
RE_SCHEMA  = re.compile(r'@Table\([^)]*schema\s*=\s*"([^"]+)"')
RE_IMPORT  = re.compile(r'^import\s+(com\.tss\.grc\.module\.(\w+)\.[\w.]+);', re.M)
RE_ROUTE   = re.compile(r'<Route path="([^"]+)"')

def scan_backend():
    mods = {}
    for d in sorted(p for p in BE.iterdir() if p.is_dir()):
        name = d.name
        info = {"controllers": 0, "api": set(), "schemas": set(),
                "sched": 0, "deps": set(), "services": set()}
        for f in d.rglob("*.java"):
            text = f.read_text(encoding="utf-8", errors="replace")
            if f.name.endswith("Controller.java"):
                info["controllers"] += 1
                info["api"].update(RE_REQMAP.findall(text))
            info["schemas"].update(RE_SCHEMA.findall(text))
            if "@Scheduled" in text:
                info["sched"] += 1
            for full, dep in RE_IMPORT.findall(text):
                if dep != name:
                    info["deps"].add(dep)
                for prefix, svc in SERVICE_IMPORT.items():
                    if full.startswith(prefix) and SERVICES[svc][0] != name:
                        info["services"].add(svc)
        mods[name] = info
    # Workflow-engine adoption is FE-visible (record-anchored panels), not a
    # Java import — detect pages importing ModuleEngineRequestsPanel.
    pages_root = ROOT / "tss-grc-ui-v2/src/pages"
    if pages_root.exists():
        for f in pages_root.rglob("*.tsx"):
            if "ModuleEngineRequestsPanel" not in f.read_text(encoding="utf-8", errors="replace"):
                continue
            mod = PAGE_DIR_MODULE.get(f.relative_to(pages_root).parts[0])
            if mod and mod in mods and mod != SERVICES["WorkflowEngine"][0]:
                mods[mod]["services"].add("WorkflowEngine")
    return mods

def scan_routes():
    routes = sorted(set(RE_ROUTE.findall(APP.read_text(encoding="utf-8", errors="replace"))))
    owned, unmapped = {}, []
    # Longest prefix wins, so "/cyber-lifecycle" beats "/cyber" etc.
    # "/" and "*" are exact-match only — as prefixes they would swallow every
    # future unmapped route instead of letting it surface in the report.
    EXACT_ONLY = {"/", "*"}
    prefixes = sorted((p for p in ROUTE_OWNER if p not in EXACT_ONLY), key=len, reverse=True)
    for r in routes:
        owner = ROUTE_OWNER.get(r) if r in EXACT_ONLY else \
            next((ROUTE_OWNER[p] for p in prefixes if r == p or r.startswith(p)), None)
        if owner:
            owned[r] = owner
        else:
            unmapped.append(r)
    return owned, unmapped

def esc(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "\\'")

def git_head() -> str:
    try:
        return subprocess.run(["git", "rev-parse", "--short", "HEAD"], cwd=ROOT,
                              capture_output=True, text=True).stdout.strip()
    except Exception:
        return "unknown"

# ══ emitters ═════════════════════════════════════════════════════════════════
def emit_cypher(mods, owned, unmapped, head):
    L = []
    A = L.append
    A("// TSS GRC Platform map — generated by .tools/gen_platform_map.py")
    A(f"// generated {date.today().isoformat()} at commit {head} — do not edit by hand; re-run the generator.")
    A("// Load into Neo4j 5.x:  cypher-shell -f PLATFORM_MAP.cypher   (MERGE-idempotent)")
    A("")
    for lbl, key in [("Platform","id"),("Module","name"),("Schema","name"),("ApiBase","path"),
                     ("Screen","path"),("Service","id"),("Integration","name"),("Regulator","name")]:
        A(f"CREATE CONSTRAINT {lbl.lower()}_{key} IF NOT EXISTS FOR (n:{lbl}) REQUIRE n.{key} IS UNIQUE;")
    A("")
    A(f"MERGE (p:Platform {{id: 'tss-grc'}}) SET p.name = 'TSS GRC Platform', "
      f"p.generatedAt = '{date.today().isoformat()}', p.commit = '{head}';")
    A("")
    A("// ── modules ──")
    for m, i in mods.items():
        desc = esc(MODULE_DESC.get(m, "(undescribed)"))
        A(f"MERGE (m:Module {{name: '{m}'}}) SET m.description = '{desc}', "
          f"m.canonical = {str(m in CANONICAL).lower()}, m.controllers = {i['controllers']}, "
          f"m.schedulers = {i['sched']};")
        A(f"MERGE (p:Platform {{id: 'tss-grc'}}) WITH p MATCH (m:Module {{name: '{m}'}}) MERGE (p)-[:HAS_MODULE]->(m);")
    A("")
    A("// ── API base paths ──")
    for m, i in mods.items():
        for a in sorted(i["api"]):
            A(f"MERGE (a:ApiBase {{path: '{esc(a)}'}});")
            A(f"MATCH (m:Module {{name: '{m}'}}), (a:ApiBase {{path: '{esc(a)}'}}) MERGE (m)-[:EXPOSES]->(a);")
    A("")
    A("// ── DB schemas ──")
    schemas = sorted({s for i in mods.values() for s in i["schemas"]})
    for s in schemas:
        A(f"MERGE (s:Schema {{name: '{esc(s)}'}});")
    for m, i in mods.items():
        for s in sorted(i["schemas"]):
            A(f"MATCH (m:Module {{name: '{m}'}}), (s:Schema {{name: '{esc(s)}'}}) MERGE (m)-[:OWNS_SCHEMA]->(s);")
    A("")
    A("// ── cross-module dependencies (import-detected) ──")
    for m, i in mods.items():
        for d in sorted(i["deps"]):
            if d in mods:
                A(f"MATCH (a:Module {{name: '{m}'}}), (b:Module {{name: '{d}'}}) MERGE (a)-[:DEPENDS_ON]->(b);")
    A("")
    A("// ── shared platform services ──")
    for sid, (prov, desc) in SERVICES.items():
        A(f"MERGE (s:Service {{id: '{sid}'}}) SET s.description = '{esc(desc)}', s.provider = '{prov}';")
        A(f"MATCH (s:Service {{id: '{sid}'}}), (m:Module {{name: '{prov}'}}) MERGE (m)-[:PROVIDES]->(s);")
    for m, i in mods.items():
        for svc in sorted(i["services"]):
            A(f"MATCH (m:Module {{name: '{m}'}}), (s:Service {{id: '{svc}'}}) MERGE (m)-[:USES]->(s);")
    A("")
    A("// ── external integrations ──")
    for name, (mod, desc) in EXTERNAL.items():
        A(f"MERGE (x:Integration {{name: '{esc(name)}'}}) SET x.description = '{esc(desc)}';")
        A(f"MATCH (m:Module {{name: '{mod}'}}), (x:Integration {{name: '{esc(name)}'}}) MERGE (m)-[:INTEGRATES_WITH]->(x);")
    A("")
    A("// ── regulators / frameworks ──")
    for reg, mlist in REGULATORS.items():
        A(f"MERGE (r:Regulator {{name: '{esc(reg)}'}});")
        for m in mlist:
            A(f"MATCH (m:Module {{name: '{m}'}}), (r:Regulator {{name: '{esc(reg)}'}}) MERGE (m)-[:ADDRESSES]->(r);")
    A("")
    A("// ── frontend screens ──")
    for r, owner in sorted(owned.items()):
        A(f"MERGE (sc:Screen {{path: '{esc(r)}'}});")
        A(f"MATCH (sc:Screen {{path: '{esc(r)}'}}), (m:Module {{name: '{owner}'}}) MERGE (sc)-[:SERVED_BY]->(m);")
    for r in sorted(unmapped):
        A(f"MERGE (sc:Screen {{path: '{esc(r)}'}}) SET sc.unmapped = true;")
    A("")
    OUT_CYPHER.write_text("\n".join(L) + "\n", encoding="utf-8")
    return len(L)

def emit_md(mods, owned, unmapped, head):
    screens_by_mod = defaultdict(list)
    for r, m in owned.items():
        screens_by_mod[m].append(r)
    schemas = sorted({s for i in mods.values() for s in i["schemas"]})
    n_api = sum(len(i["api"]) for i in mods.values())
    n_deps = sum(len([d for d in i["deps"] if d in mods]) for i in mods.values())

    L = []
    A = L.append
    A("# TSS GRC Platform Map")
    A("")
    A(f"> Generated {date.today().isoformat()} at commit `{head}` by `.tools/gen_platform_map.py` —")
    A("> **do not edit by hand**; re-run the generator after structural changes.")
    A("> Graph-database twin: [`PLATFORM_MAP.cypher`](PLATFORM_MAP.cypher) (Neo4j 5, MERGE-idempotent).")
    A("> Structural facts are code-detected; descriptions, regulator anchors and screen")
    A("> ownership are curated in the generator.")
    A("")
    A("## At a glance")
    A("")
    A("| Metric | Count |")
    A("|---|---:|")
    A(f"| Backend modules (`module/*`) | {len(mods)} — {sum(1 for m in mods if m in CANONICAL)} canonical + {sum(1 for m in mods if m not in CANONICAL)} auxiliary/V1-era |")
    A(f"| REST controllers | {sum(i['controllers'] for i in mods.values())} |")
    A(f"| API base paths (`@RequestMapping`) | {n_api} |")
    A(f"| DB schemas referenced by entities | {len(schemas)} |")
    A(f"| Scheduler classes (`@Scheduled`) | {sum(i['sched'] for i in mods.values())} |")
    A(f"| Cross-module import edges | {n_deps} |")
    A(f"| Frontend routes (screens) | {len(owned) + len(unmapped)} ({len(unmapped)} unmapped to a module) |")
    A("")
    A("## Topology")
    A("")
    A("```")
    A("Browser (React SPA, tss-grc-ui-v2, :5173 dev / nginx :80 prod)")
    A("   │  same-origin /api/** + /ws")
    A("   ▼")
    A("Spring Boot modular monolith (:8084 dev / :8090 prod)")
    A("   ├─ security: JWT filter → TenantContext (org per request) → hasPermission guards")
    A("   ├─ 58 modules (controller / dto / entity / repository / service)")
    A("   ├─ shared services: workflow engine · evidence · exports · audit ledger ·")
    A("   │                   notifications · auto-CAPA")
    A("   └─ schedulers (org-explicit, off-request-safe)")
    A("   ▼")
    A("SQL Server TSS_GRC — 50+ schemas, Flyway V1..V296 (OFF in dev), soft deletes,")
    A("every query filtered by organisation_id")
    A("```")
    A("")
    A("## Shared platform services")
    A("")
    A("| Service | Provider | Consumed by (detected) |")
    A("|---|---|---|")
    for sid, (prov, desc) in SERVICES.items():
        users = sorted(m for m, i in mods.items() if sid in i["services"])
        cell = f"{len(users)}: " + ", ".join(f"`{u}`" for u in users) if users else "— (provider-internal)"
        A(f"| **{sid}** — {desc} | `{prov}` | {cell} |")
    A("")
    A("## External integrations")
    A("")
    A("| Integration | Owning module | Notes |")
    A("|---|---|---|")
    for name, (mod, desc) in EXTERNAL.items():
        A(f"| {name} | `{mod}` | {desc} |")
    A("")
    A("## Regulator / framework anchors")
    A("")
    A("| Regulator / framework | Modules |")
    A("|---|---|")
    for reg, mlist in REGULATORS.items():
        A(f"| {reg} | " + ", ".join(f"`{m}`" for m in mlist) + " |")
    A("")
    A("## Modules")
    A("")
    A("Canonical modules first (the CLAUDE.md set), then auxiliary/V1-era aggregates.")
    A("")
    for group, title in [(True, "Canonical"), (False, "Auxiliary / V1-era")]:
        A(f"### {title}")
        A("")
        for m, i in mods.items():
            if (m in CANONICAL) != group:
                continue
            A(f"#### `{m}` — {MODULE_DESC.get(m, '(undescribed)')}")
            A("")
            A(f"- **Controllers:** {i['controllers']}" +
              (f" · **Schedulers:** {i['sched']}" if i['sched'] else ""))
            if i["api"]:
                shown = sorted(i["api"])
                A(f"- **API bases ({len(shown)}):** " + ", ".join(f"`{a}`" for a in shown[:8]) +
                  (f" … +{len(shown)-8} more" if len(shown) > 8 else ""))
            if i["schemas"]:
                A("- **DB schemas:** " + ", ".join(f"`{s}`" for s in sorted(i["schemas"])))
            deps = sorted(d for d in i["deps"] if d in mods)
            if deps:
                A("- **Depends on:** " + ", ".join(f"`{d}`" for d in deps))
            if i["services"]:
                A("- **Uses services:** " + ", ".join(f"`{s}`" for s in sorted(i["services"])))
            scr = sorted(screens_by_mod.get(m, []))
            if scr:
                A(f"- **Screens ({len(scr)}):** " + ", ".join(f"`{r}`" for r in scr[:10]) +
                  (f" … +{len(scr)-10} more" if len(scr) > 10 else ""))
            A("")
    A("## DB schema census")
    A("")
    A(", ".join(f"`{s}`" for s in schemas))
    A("")
    if unmapped:
        A("## Screens not yet mapped to a module")
        A("")
        A("These routes have no curated owner prefix in the generator (extend `ROUTE_OWNER`):")
        A("")
        for r in sorted(unmapped):
            A(f"- `{r}`")
        A("")
    A("## Querying the graph")
    A("")
    A("```cypher")
    A("// which modules depend on the workflow engine?")
    A("MATCH (m:Module)-[:USES]->(s:Service {id:'WorkflowEngine'}) RETURN m.name;")
    A("// blast radius: what breaks if the evidence module changes?")
    A("MATCH (m:Module)-[:DEPENDS_ON]->(e:Module {name:'evidence'}) RETURN m.name;")
    A("// every screen + API surface a regulator audit would touch")
    A("MATCH (m:Module)-[:ADDRESSES]->(r:Regulator {name:'NCA ECC'})")
    A("OPTIONAL MATCH (sc:Screen)-[:SERVED_BY]->(m) OPTIONAL MATCH (m)-[:EXPOSES]->(a:ApiBase)")
    A("RETURN m.name, collect(DISTINCT sc.path), collect(DISTINCT a.path);")
    A("```")
    A("")
    OUT_MD.write_text("\n".join(L) + "\n", encoding="utf-8")
    return len(L)

def main():
    if not BE.exists():
        sys.exit(f"backend module dir not found: {BE}")
    (ROOT / "docs").mkdir(exist_ok=True)
    mods = scan_backend()
    owned, unmapped = scan_routes()
    head = git_head()
    nc = emit_cypher(mods, owned, unmapped, head)
    nm = emit_md(mods, owned, unmapped, head)
    print(f"modules={len(mods)} apiBases={sum(len(i['api']) for i in mods.values())} "
          f"schemas={len({s for i in mods.values() for s in i['schemas']})} "
          f"routes={len(owned)+len(unmapped)} (unmapped={len(unmapped)})")
    print(f"wrote {OUT_CYPHER.relative_to(ROOT)} ({nc} lines)")
    print(f"wrote {OUT_MD.relative_to(ROOT)} ({nm} lines)")

if __name__ == "__main__":
    main()
