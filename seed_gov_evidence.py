# -*- coding: utf-8 -*-
"""Deepen the Saudi-government EVIDENCE layer (organisation_id = 1) for the
TSS GRC default tenant — a Saudi general authority (هيئة عامة).

The evidence schema has two layers:

  (1) An org-agnostic CATALOG (frameworks / controls / sub_controls / control_links)
      that is already a rich, Saudi-regulator-anchored reference registry
      (NCA ECC/DCC/CSCC/CCC/OTCC/TCC, PDPL, NDMO+SDAIA, BCM ISO22301+NCEMA,
      ESG-CMA, AI ISO42001). This is SHARED REFERENCE DATA — left untouched.

  (2) An org-scoped TENANT EVIDENCE layer (evidence_items, evidence_requests,
      evidence_links, evidence_reviews, evidence_versions, connectors) whose
      org-1 rows are already Saudi-government-themed.

Per the discovery focus ("prefer deepen/cross-link a modest government layer, or
skip tables already rich"), this script performs THREE targeted, idempotent
backfills on EXISTING org-1 rows — it inserts NO new evidence items/requests and
NEVER alters schema:

  A) evidence.evidence_items  — DEEPEN chain-of-custody / provenance columns that
     are empty for org-1 (collection_method, collector_name, collector_id,
     witness_id, tool_version, evidence_hash, review_due_date, chain_status,
     chain_version_count). Turns the catalog into board/regulator-grade WORM
     evidence with named government collectors and freshness re-review dates
     (assessment §5.9 chain-of-custody; PDPL / NCA ECC evidence-currency).

  B) evidence.evidence_requests — DEEPEN department_id (and assigned_to_id where
     null) by mapping each request's subject to the owning government department
     in platform.departments (CYBER / SOC / IT / BCM / DATA_GOV / PRIVACY /
     PROCUREMENT / INTERNAL_AUD ...). Shows realistic government ownership of the
     evidence-collection loop.

  C) evidence.evidence_links — FILL a handful of government cross-links from
     existing org-1 evidence_items to existing module entities (compliance
     obligation/finding, privacy ROPA, BCM plan, cyber asset, risk), resolved to
     REAL ids — so the "reused as evidence across the GRC programme" story shows.
     Any link whose target cannot be resolved is skipped and reported.

SKIPPED (already rich / reference data / WORM internals): frameworks, controls,
sub_controls, control_links (catalog); evidence_versions (hash-chain internals);
connector_definitions + evidence_connectors (vendor stubs); evidence_reviews
(already a coherent status/result spread).

Idempotency: every UPDATE is guarded by "target column IS NULL" and every
evidence_links INSERT by a natural key (org+evidence+module+entity) existence
check, so a SECOND run inserts/updates 0 rows.

Safety: INSERT / idempotent-UPDATE ONLY. No DELETE/TRUNCATE/DROP/ALTER. org 1
only. evidence schema only (the soft-reference ids it resolves from
platform.departments / iam.users / consuming modules are READ-only lookups).

    Usage:  python seed_gov_evidence.py
"""
from __future__ import annotations

import hashlib
import sys

import pyodbc

sys.stdout.reconfigure(encoding="utf-8")

_CONN_CANDIDATES = [
    "DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Unicode_Results=Yes;",
    "DRIVER={ODBC Driver 18 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;"
    "UID=sa;PWD=tss.123;Encrypt=no;TrustServerCertificate=yes;Unicode_Results=Yes;",
]


def connect() -> pyodbc.Connection:
    last = None
    for cs in _CONN_CANDIDATES:
        try:
            return pyodbc.connect(cs)
        except Exception as exc:  # noqa: BLE001 - try next driver
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1

# iam.users (org 1): admin(1) rmanager(2) compliance(3) auditor(4) ranalyst(5) viewer(6)
U_ADMIN, U_RISK, U_COMPL, U_AUDIT, U_ANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6

# ── Government collector role titles (EN / AR), keyed by a short collector code.
# collector_name carries the bilingual role; collector_id maps to a real iam user.
COLLECTORS = {
    "SOC": ("Security Operations Center Analyst / محلل مركز العمليات الأمنية", U_ANALYST),
    "CYBER_GRC": ("Cyber Risk & Compliance Officer / أخصائي مخاطر والتزام الأمن السيبراني", U_COMPL),
    "CISO_OFF": ("Information Security Office / مكتب أمن المعلومات", U_ANALYST),
    "IT_OPS": ("IT Operations Engineer / مهندس عمليات تقنية المعلومات", U_ADMIN),
    "DATA_OFF": ("Data Management Office Officer / أخصائي مكتب إدارة البيانات", U_COMPL),
    "PRIVACY": ("Data Protection Officer (DPO) / مسؤول حماية البيانات الشخصية", U_COMPL),
    "BCM": ("Business Continuity Officer / أخصائي استمرارية الأعمال", U_ADMIN),
    "AUDIT": ("Internal Audit Officer / مدقق داخلي", U_AUDIT),
    "RISK": ("Enterprise Risk Officer / أخصائي إدارة المخاطر المؤسسية", U_RISK),
    "AI_GOV": ("AI Governance Officer / أخصائي حوكمة الذكاء الاصطناعي", U_COMPL),
    "PROC": ("Vendor & Procurement Security Officer / أخصائي أمن الموردين والمشتريات", U_COMPL),
}

# Witness for chain-of-custody (dual-control): Internal Audit witnesses sensitive
# artefacts; otherwise no witness. Use auditor(4).
WITNESS_AUDIT = U_AUDIT

# Tool versions stamped on automated collections.
TOOL_SCANNER = "Tenable Nessus 10.7 / NCA-aligned scan profile"
TOOL_SIEM = "Microsoft Sentinel (KQL ruleset 2026.1)"
TOOL_AGENT = "GRC Evidence Agent 2.4"
TOOL_API = "GRC Evidence Connector 2.4 (API pull)"
TOOL_NAFATH = "Nafath digital-signature gateway"


def classify_item(evidence_type: str, title: str) -> dict:
    """Return provenance backfill for one evidence item from its type + title.

    collection_method enum (no DB CHECK):
        MANUAL | AGENT | API_PULL | SIEM | SCANNER | NAFATH_SIGNED
    """
    t = (title or "").lower()
    et = (evidence_type or "").upper()

    # ── Pick a collection method ─────────────────────────────────────────────
    if any(k in t for k in ("vulnerability scan", "vuln scan", "penetration", "scan report")):
        method, tool, collector = "SCANNER", TOOL_SCANNER, "SOC"
    elif "siem" in t or "alerting" in t:
        method, tool, collector = "SIEM", TOOL_SIEM, "SOC"
    elif et in ("CONFIGURATION", "LOG"):
        # config/log artefacts pulled by agent or API
        method = "AGENT"
        tool = TOOL_AGENT
        if any(k in t for k in ("vpn", "connection log", "backup log")):
            method, tool = "API_PULL", TOOL_API
        collector = "SOC" if any(k in t for k in ("firewall", "network", "siem", "vpn")) else "IT_OPS"
    elif et == "POLICY" and ("strategy" in t or "framework" in t or "board" in t):
        # board-/president-approved policies are Nafath-signed at approval
        method, tool, collector = "NAFATH_SIGNED", TOOL_NAFATH, "CYBER_GRC"
    elif et in ("POLICY", "PROCEDURE", "DOCUMENT", "SCREENSHOT", "REPORT", "AUDIT",
                "ASSESSMENT", "TEST", "CERTIFICATE", "OTHER"):
        method, tool, collector = "MANUAL", None, "CYBER_GRC"
    else:
        method, tool, collector = "MANUAL", None, "CYBER_GRC"

    # ── Refine collector by subject domain (overrides default where clearer) ──
    if any(k in t for k in ("personal data", "ropa", "record of processing", "dpia",
                            "lawful basis", "consent", "data-subject", "data subject", "pdpl")):
        collector = "PRIVACY"
    elif any(k in t for k in ("data governance", "data classification", "data labeling",
                              "data labelling", "data handling", "asset classification")):
        collector = "DATA_OFF"
    elif any(k in t for k in ("ai ", "model card", "fairness", "xgboost", "credit decision",
                              "credit model")):
        collector = "AI_GOV"
    elif et == "AUDIT" or "audit" in t or "review" in t and "access review" in t:
        collector = "AUDIT"
    elif any(k in t for k in ("dr test", "disaster recovery", "failover", "continuity",
                              "bcm", "bcp", "bia", "drill", "dr site", "dr strategy",
                              "exercise", "backup")):
        collector = "BCM"
    elif any(k in t for k in ("risk register", "risk appetite", "risk assessment",
                              "risk treatment", "kri", "loss event", "near-miss",
                              "risk review", "control self-assessment", "control testing")):
        collector = "RISK"
    elif any(k in t for k in ("vendor", "third-party", "third party", "due-diligence",
                              "due diligence", "processor", "supplier")):
        collector = "PROC"

    name, cid = COLLECTORS[collector]

    # ── Witness: Internal Audit witnesses high-assurance artefacts ────────────
    witness = WITNESS_AUDIT if et in ("AUDIT", "ASSESSMENT", "TEST", "CERTIFICATE") else None

    return {"method": method, "tool": tool, "collector_name": name,
            "collector_id": cid, "witness_id": witness}


def map_request_department(title: str) -> str | None:
    """Map a request's subject to the owning government department CODE."""
    t = (title or "").lower()
    if any(k in t for k in ("penetration", "vulnerability scan", "endpoint security",
                            "baseline configuration")):
        return "SOC"
    if any(k in t for k in ("iam", "access review", "encryption", "mfa")):
        return "CYBER"
    if any(k in t for k in ("data classification", "data labeling", "data labelling")):
        return "DATA_GOV"
    if any(k in t for k in ("privacy", "dsr", "personal data", "ropa")):
        return "PRIVACY"
    if any(k in t for k in ("awareness training", "security awareness")):
        return "CYBER_GRC"
    if any(k in t for k in ("tabletop", "ransomware", "crisis")):
        return "BCM"
    if any(k in t for k in ("dr test", "disaster recovery", "backup", "failover",
                            "continuity")):
        return "IT"
    if any(k in t for k in ("vendor", "supplier", "third-party", "third party",
                            "questionnaire")):
        return "PROCUREMENT"
    if any(k in t for k in ("loss event", "risk")):
        return "RISK"
    if "cybersecurity strategy" in t or "strategy formulation" in t:
        return "CYBER_GRC"
    # default: cyber GRC owns generic cyber-evidence requests
    return "CYBER_GRC"


def sha256_hex(seed: str) -> str:
    return hashlib.sha256(seed.encode("utf-8")).hexdigest()


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    # ── Resolve department code -> id (org 1), read-only lookup ───────────────
    cur.execute(
        "SELECT code, id FROM platform.departments WHERE organisation_id=?", ORG
    )
    DEPT = {code: did for code, did in cur.fetchall()}

    # =====================================================================
    # A) DEEPEN evidence.evidence_items — chain-of-custody / provenance
    # =====================================================================
    print("=" * 72)
    print("A) evidence.evidence_items — backfill chain-of-custody / provenance")
    print("=" * 72)

    cur.execute(
        """SELECT id, evidence_type, status, title_en, collected_at, external_id,
                  collection_method, collector_name, collector_id, witness_id,
                  tool_version, evidence_hash, review_due_date, chain_status,
                  chain_version_count
             FROM evidence.evidence_items
            WHERE organisation_id=? AND is_deleted=0
            ORDER BY id""",
        ORG,
    )
    items = cur.fetchall()
    a_updated = 0
    for (iid, etype, status, title, collected_at, ext, c_method, c_cname, c_cid,
         c_wid, c_tool, c_hash, c_review, c_chain, c_cvc) in items:
        cls = classify_item(etype, title)

        sets = []
        params = []
        # method (guard: only when NULL)
        if c_method is None:
            sets.append("collection_method = ?"); params.append(cls["method"])
        if c_cname is None:
            sets.append("collector_name = ?"); params.append(cls["collector_name"])
        if c_cid is None:
            sets.append("collector_id = ?"); params.append(cls["collector_id"])
        if c_wid is None and cls["witness_id"] is not None:
            sets.append("witness_id = ?"); params.append(cls["witness_id"])
        if c_tool is None and cls["tool"] is not None:
            sets.append("tool_version = ?"); params.append(cls["tool"])
        if c_hash is None:
            # deterministic SHA-256-shaped placeholder, stable per item (external_id)
            sets.append("evidence_hash = ?")
            params.append(sha256_hex(f"ev-item:{ORG}:{iid}:{ext}"))
        if c_review is None and collected_at is not None:
            # freshness re-review date 12 months after collection (PDPL/ECC currency).
            # Policies/strategies re-reviewed annually; reports/logs at 6 months.
            months = 6 if (etype or "").upper() in ("REPORT", "LOG", "TEST", "SCREENSHOT") else 12
            sets.append("review_due_date = DATEADD(MONTH, ?, CAST(? AS date))")
            params.extend([months, collected_at])
        if c_chain is None:
            sets.append("chain_status = ?"); params.append("VALID")
        if c_cvc is None:
            sets.append("chain_version_count = ?"); params.append(1)

        if not sets:
            continue

        sets.append("updated_at = SYSUTCDATETIME()")
        sets.append("updated_by = ?"); params.append(U_ADMIN)
        params.append(iid)
        # idempotency guard reinforced in WHERE: at least one provenance col still NULL
        cur.execute(
            "UPDATE evidence.evidence_items SET " + ", ".join(sets) +
            " WHERE id = ? AND organisation_id = " + str(ORG) +
            " AND (collection_method IS NULL OR collector_name IS NULL OR "
            "collector_id IS NULL OR evidence_hash IS NULL OR chain_status IS NULL OR "
            "chain_version_count IS NULL OR review_due_date IS NULL)",
            *params,
        )
        if cur.rowcount > 0:
            a_updated += 1
            print(f"  ~ item {iid:4} [{(etype or ''):12}] {cls['method']:13} "
                  f"by {cls['collector_name'].split(' / ')[0]}")
    print(f"\n  evidence_items deepened: {a_updated} of {len(items)}")

    # =====================================================================
    # B) DEEPEN evidence.evidence_requests — owning department + assignee
    # =====================================================================
    print("\n" + "=" * 72)
    print("B) evidence.evidence_requests — backfill owning department / assignee")
    print("=" * 72)

    cur.execute(
        """SELECT id, title_en, status, department_id, assigned_to_id
             FROM evidence.evidence_requests
            WHERE organisation_id=? AND is_deleted=0
            ORDER BY id""",
        ORG,
    )
    reqs = cur.fetchall()
    b_updated = 0
    b_unresolved = []
    for rid, title, status, dept_id, asg_id in reqs:
        sets, params = [], []
        if dept_id is None:
            code = map_request_department(title)
            did = DEPT.get(code)
            if did is None:
                b_unresolved.append((rid, code, title))
            else:
                sets.append("department_id = ?"); params.append(did)
        if asg_id is None:
            # assign generic/stub requests to the cyber-GRC analyst by default
            sets.append("assigned_to_id = ?"); params.append(U_ANALYST)

        if not sets:
            continue
        sets.append("updated_at = SYSUTCDATETIME()")
        sets.append("updated_by = ?"); params.append(U_ADMIN)
        params.append(rid)
        cur.execute(
            "UPDATE evidence.evidence_requests SET " + ", ".join(sets) +
            " WHERE id = ? AND organisation_id = " + str(ORG) +
            " AND (department_id IS NULL OR assigned_to_id IS NULL)",
            *params,
        )
        if cur.rowcount > 0:
            b_updated += 1
            dlbl = map_request_department(title)
            print(f"  ~ req {rid:4} -> dept {dlbl:11} : {(title or '')[:48]}")
    print(f"\n  evidence_requests deepened: {b_updated} of {len(reqs)}")
    for rid, code, title in b_unresolved:
        print(f"  ! req {rid} dept code '{code}' not found — skipped: {title[:40]}")

    # =====================================================================
    # C) FILL evidence.evidence_links — government cross-links (resolved ids)
    # =====================================================================
    print("\n" + "=" * 72)
    print("C) evidence.evidence_links — government cross-links to module entities")
    print("=" * 72)

    def item_id_by_title(substr: str) -> int | None:
        cur.execute(
            "SELECT TOP 1 id FROM evidence.evidence_items "
            "WHERE organisation_id=? AND is_deleted=0 AND title_en LIKE ? ORDER BY id",
            ORG, f"%{substr}%",
        )
        r = cur.fetchone()
        return r[0] if r else None

    def target_id(sql: str) -> int | None:
        try:
            cur.execute(sql, ORG)
            r = cur.fetchone()
            return r[0] if r else None
        except Exception:  # noqa: BLE001 - target table/column missing -> skip
            return None

    # Resolve target entity ids in the consuming modules (real existing rows).
    obl_ecc = target_id(
        "SELECT TOP 1 id FROM compliance.obligation WHERE organisation_id=? "
        "AND is_deleted=0 AND title_en LIKE '%NCA ECC%' ORDER BY id")
    obl_any = target_id(
        "SELECT TOP 1 id FROM compliance.obligation WHERE organisation_id=? "
        "AND is_deleted=0 ORDER BY id")
    find_ir = target_id(
        "SELECT TOP 1 id FROM compliance.findings WHERE organisation_id=? "
        "AND is_deleted=0 AND title LIKE '%Incident Response%' ORDER BY id")
    find_pwd = target_id(
        "SELECT TOP 1 id FROM compliance.findings WHERE organisation_id=? "
        "AND is_deleted=0 AND title LIKE '%Password%' ORDER BY id")
    ropa_kyc = target_id(
        "SELECT TOP 1 id FROM privacy.processing_activities WHERE organisation_id=? "
        "AND is_deleted=0 ORDER BY id")
    bcm_dr = target_id(
        "SELECT TOP 1 id FROM bcm.bcm_plans WHERE organisation_id=? "
        "AND is_deleted=0 AND plan_name_en LIKE '%Disaster Recovery%' ORDER BY id")
    bcm_any = target_id(
        "SELECT TOP 1 id FROM bcm.bcm_plans WHERE organisation_id=? "
        "AND is_deleted=0 ORDER BY id")
    asset_core = target_id(
        "SELECT TOP 1 id FROM cyber.cyber_assets WHERE organisation_id=? "
        "AND is_deleted=0 AND asset_name LIKE '%Core Banking%' ORDER BY id")
    asset_any = target_id(
        "SELECT TOP 1 id FROM cyber.cyber_assets WHERE organisation_id=? "
        "AND is_deleted=0 ORDER BY id")
    risk_ecc = target_id(
        "SELECT TOP 1 id FROM risk.risks WHERE organisation_id=? "
        "AND is_deleted=0 AND title_en LIKE '%NCA ECC%' ORDER BY id")

    bcm_dr = bcm_dr or bcm_any
    obl_ecc = obl_ecc or obl_any
    asset_core = asset_core or asset_any

    # Resolve the source evidence items by title fragment.
    ev_strategy = item_id_by_title("Cybersecurity Strategy")
    ev_ropa = item_id_by_title("Record of Processing")
    ev_dr = item_id_by_title("DR Test Results")
    ev_vuln = item_id_by_title("Vulnerability Scan Report")
    ev_dpia = item_id_by_title("DPIA")
    ev_irp = item_id_by_title("Incident Response Playbook")
    ev_lawful = item_id_by_title("Lawful basis register")

    # Candidate cross-links: (evidence_id, module, entity_type, entity_id, note)
    candidates = [
        (ev_strategy, "COMPLIANCE", "obligation", obl_ecc,
         "NCA Cybersecurity Strategy evidences the ECC annual self-assessment obligation"),
        (ev_strategy, "AUDIT", "finding", find_ir,
         "Strategy referenced in remediation of the Incident Response audit finding"),
        (ev_ropa, "PRIVACY", "processing_activity", ropa_kyc,
         "Personal Data Inventory / ROPA backs the registered processing activity"),
        (ev_lawful, "PRIVACY", "processing_activity", ropa_kyc,
         "Lawful basis register (PDPL Art.5) supports the processing activity"),
        (ev_dr, "BCM", "plan", bcm_dr,
         "DR test results evidence the IT Disaster Recovery Plan"),
        (ev_vuln, "CYBER", "asset", asset_core,
         "Vulnerability scan report covers the Core Banking asset"),
        (ev_dpia, "PRIVACY", "dpia", target_id(
            "SELECT TOP 1 id FROM privacy.dpia WHERE organisation_id=? "
            "AND is_deleted=0 ORDER BY id"),
         "DPIA artefact links to the privacy DPIA record"),
        (ev_irp, "AUDIT", "finding", find_pwd,
         "Incident Response Playbook supports remediation evidence"),
    ]

    c_inserted, c_skipped = 0, []
    for ev_id, module, etype, ent_id, note in candidates:
        if ev_id is None:
            c_skipped.append(f"{module}: source evidence item not found")
            continue
        if ent_id is None:
            c_skipped.append(f"{module}/{etype}: target entity not resolvable")
            continue
        # natural-key existence guard (idempotent)
        cur.execute(
            "SELECT 1 FROM evidence.evidence_links WHERE organisation_id=? AND "
            "evidence_id=? AND linked_module=? AND linked_entity_id=? AND is_deleted=0",
            ORG, ev_id, module, ent_id,
        )
        if cur.fetchone():
            continue
        cur.execute(
            """INSERT INTO evidence.evidence_links
                   (organisation_id, evidence_id, linked_module, linked_entity_type,
                    linked_entity_id, notes, created_by, created_at, is_deleted)
               VALUES (?, ?, ?, ?, ?, ?, ?, SYSUTCDATETIME(), 0)""",
            ORG, ev_id, module, etype, ent_id, note, U_ADMIN,
        )
        c_inserted += 1
        print(f"  + link ev={ev_id:4} -> {module:11} {etype}#{ent_id}")
    print(f"\n  evidence_links inserted: {c_inserted}")
    for s in c_skipped:
        print(f"  ! skipped link — {s}")

    # =====================================================================
    # VERIFICATION
    # =====================================================================
    print("\n" + "=" * 72)
    print("VERIFICATION (organisation_id = 1)")
    print("=" * 72)

    def n(sql):
        cur.execute(sql, ORG); return cur.fetchone()[0]

    tot_items = n("SELECT COUNT(*) FROM evidence.evidence_items WHERE organisation_id=? AND is_deleted=0")
    print("\nevidence_items provenance fill:")
    for col in ["collection_method", "collector_name", "collector_id", "witness_id",
                "tool_version", "evidence_hash", "review_due_date", "chain_status",
                "chain_version_count"]:
        cnt = n(f"SELECT COUNT({col}) FROM evidence.evidence_items WHERE organisation_id=? AND is_deleted=0")
        print(f"  {col:24} {cnt:3} / {tot_items}")

    tot_req = n("SELECT COUNT(*) FROM evidence.evidence_requests WHERE organisation_id=? AND is_deleted=0")
    dept_filled = n("SELECT COUNT(department_id) FROM evidence.evidence_requests WHERE organisation_id=? AND is_deleted=0")
    asg_filled = n("SELECT COUNT(assigned_to_id) FROM evidence.evidence_requests WHERE organisation_id=? AND is_deleted=0")
    print(f"\nevidence_requests: department_id {dept_filled}/{tot_req}, "
          f"assigned_to_id {asg_filled}/{tot_req}")

    tot_links = n("SELECT COUNT(*) FROM evidence.evidence_links WHERE organisation_id=? AND is_deleted=0")
    print(f"evidence_links total: {tot_links}")

    total_changes = a_updated + b_updated + c_inserted
    print("\n" + "-" * 72)
    print(f"SUMMARY  items_deepened={a_updated}  requests_deepened={b_updated}  "
          f"links_inserted={c_inserted}  total_changes={total_changes}")
    print("Re-run this script: a second pass must report 0 changes (idempotent).")
    conn.close()


if __name__ == "__main__":
    main()
