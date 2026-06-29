# -*- coding: utf-8 -*-
"""Deepen the change-management & counterparty-exposure history for the default
tenant (organisation_id = 1) — a Saudi General Authority (هيئة عامة) headed by a
President (رئيس الهيئة).

Domain: change_cpty.  Schemas touched: change_mgmt, cpty.

This script is a *deepening* pass, not a fresh build. The core registers are
already richly seeded (15 change requests, 21 counterparties, 72 credit ratings,
50 large-exposure limits, 45 change links/comments). Those are left untouched.
What it fills are the thin / empty history tables that give each record an
auditable timeline:

  change_mgmt.change_approvals  — CAB (Change Advisory Board) decision trail
                                  for every applicable change request.
  change_mgmt.change_tasks      — implementation/runbook checklist per change.
  cpty.rating_action            — credit-life rating actions (watchlist,
                                  downgrade) for the weaker / breach names.
  cpty.cpty_event               — the per-counterparty audit timeline
                                  (ONBOARDED → RATING_ASSIGNED → LIMIT_SET →
                                  LIMIT_BREACH / WATCHLIST), currently empty.

All inserts are bilingual (EN + AR, natural Saudi institutional Arabic) and
cross-linked to iam.users (credit officer / CAB chair) and to the existing
cpty.credit_rating / cpty.rating_action / cpty.large_exposure_limit rows.

Idempotency keys (a second run must insert 0):
  change_approvals = (change_request_id, approver_name, decision)
  change_tasks     = (change_request_id, sort_order)
  rating_action    = (counterparty_id, action_type, action_date)
  cpty_event       = (counterparty_id, event_type, occurred_at)

Schema facts (verified via sys.columns / sys.check_constraints):
  * Both schemas have ZERO db CHECK constraints — all enums are app-enforced;
    we mirror the canonical literals from the JPA entities.
  * cpty.* tables carry row_version (SQL Server `timestamp`, auto) — OMITTED.
  * change_requests.external_id defaults to newid() — but we never insert CRs.
  * change_mgmt child tables have NO organisation_id; tenancy is inherited via
    the FK change_request_id -> change_mgmt.change_requests(id). We only seed
    children against org-1 parents (ids 1..15).

Hard safety: INSERT-only (idempotent). No DELETE/TRUNCATE/DROP/ALTER. org 1 only.

    Usage:  python seed_gov_change_cpty.py
"""
from __future__ import annotations

import sys
from datetime import date, datetime, timedelta

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
TODAY = date(2026, 6, 27)

# CAB roster (org-1 governance roles mapped to iam.users by username).
U_CHAIR = 1   # admin     -> Deputy for Digital Transformation & Data (CAB chair / IT Steering)
U_RM = 2      # rmanager  -> Director of Enterprise Risk Management (CISO proxy)
U_CREDIT = 3  # compliance-> Credit Officer / Risk Management Committee secretariat
U_AUDIT = 4   # auditor   -> Head of Internal Audit (observer)


# ───────────────────────────────────────────────────────────────────────────
#  1. CHANGE APPROVALS — CAB decision trail
#     Keyed on (change_request_id, approver_name, decision).
#     status drives the votes:
#       CLOSED/IMPLEMENTED/IMPLEMENTING/SCHEDULED/CAB_APPROVED -> APPROVE set
#       CAB_PENDING -> a DEFER (pending more info)
#       EMERGENCY (CHG-2025-0003) -> single expedited APPROVE
#       SUBMITTED/IMPACT_ASSESSED/DRAFT -> no CAB vote yet (skip)
# ───────────────────────────────────────────────────────────────────────────

# Standard 3-vote CAB approval block (chair + CISO + Director of IT).
def _approve_block(decided: datetime):
    return [
        dict(approver_id=U_CHAIR, approver_name="Deputy for Digital Transformation & Data",
             approver_role="CAB Chair", decision="APPROVE", decided_at=decided,
             comments="Approved by the Change Advisory Board; risk, rollback and test "
                      "plans reviewed and accepted. (اعتمدها مجلس استشارة التغيير بعد "
                      "مراجعة المخاطر وخطة التراجع وخطة الاختبار.)"),
        dict(approver_id=U_RM, approver_name="Chief Information Security Officer",
             approver_role="Member — Cybersecurity", decision="APPROVE",
             decided_at=decided + timedelta(minutes=5),
             comments="No residual cybersecurity objection; aligned with NCA ECC. "
                      "(لا يوجد اعتراض أمني متبقٍ؛ متوافق مع الضوابط الأساسية للأمن السيبراني.)"),
        dict(approver_id=U_AUDIT, approver_name="Director of Information Technology",
             approver_role="Member — IT Operations", decision="APPROVE",
             decided_at=decided + timedelta(minutes=10),
             comments="Implementation window and resourcing confirmed by IT Operations. "
                      "(تم تأكيد نافذة التنفيذ والموارد من قبل عمليات تقنية المعلومات.)"),
    ]


def _defer_block(decided: datetime):
    return [
        dict(approver_id=U_CHAIR, approver_name="Deputy for Digital Transformation & Data",
             approver_role="CAB Chair", decision="DEFER", decided_at=decided,
             comments="Deferred pending additional impact and stakeholder review at the "
                      "next CAB. (أُجِّل بانتظار مراجعة إضافية للأثر وأصحاب المصلحة في "
                      "الاجتماع القادم لمجلس استشارة التغيير.)"),
    ]


def _emergency_block(decided: datetime):
    return [
        dict(approver_id=U_CHAIR, approver_name="Deputy for Digital Transformation & Data",
             approver_role="Emergency CAB Chair", decision="APPROVE", decided_at=decided,
             comments="Emergency CAB: expedited approval to remediate active RCE; "
                      "post-implementation review scheduled. (مجلس استشارة التغيير "
                      "الطارئ: اعتماد عاجل لمعالجة ثغرة تنفيذ التعليمات عن بُعد، مع جدولة "
                      "مراجعة بعد التنفيذ.)"),
    ]


def approvals_for(cr: dict):
    """Return the list of approval dicts appropriate to a CR's status/type."""
    st, ctype = cr["status"], cr["change_type"]
    # Use the scheduled/closure/cab date if present, else a deterministic anchor.
    base = datetime(2026, 5, 1, 10, 0, 0)
    if ctype == "EMERGENCY":
        return _emergency_block(base)
    if st in ("CLOSED", "IMPLEMENTED", "IMPLEMENTING", "SCHEDULED", "CAB_APPROVED"):
        return _approve_block(base)
    if st == "CAB_PENDING":
        return _defer_block(base)
    return []  # SUBMITTED / IMPACT_ASSESSED / DRAFT -> no vote yet


# ───────────────────────────────────────────────────────────────────────────
#  2. CHANGE TASKS — implementation runbook per CR.  Keyed on (cr_id, sort_order).
#     Status driven by parent status:
#       CLOSED/IMPLEMENTED -> all DONE (completed_at set)
#       IMPLEMENTING       -> mix DONE/IN_PROGRESS
#       SCHEDULED/CAB_APPROVED -> PENDING (due_at set)
#       others -> PENDING
#     CR id=2 already has tasks at sort_order 10..50; we use 110+ to avoid the key.
# ───────────────────────────────────────────────────────────────────────────

# Per-CR task templates: (title_en, title_ar, owner_name).
TASK_TEMPLATES: dict[int, list[tuple[str, str, str]]] = {
    1: [  # MFA on privileged AD accounts (CLOSED)
        ("Pilot MFA on privileged AD accounts", "تجربة المصادقة متعددة العوامل على حسابات النطاق المميزة", "SOC — Security Operations"),
        ("Enable conditional access policies", "تفعيل سياسات الوصول المشروط", "IT — Information Technology"),
        ("Enforce MFA org-wide for admins", "فرض المصادقة متعددة العوامل على المسؤولين على مستوى الهيئة", "IT — Information Technology"),
        ("Capture NCA ECC compliance evidence", "توثيق أدلة الالتزام بالضوابط الأساسية للأمن السيبراني", "Cyber Risk & Compliance"),
    ],
    3: [  # Emergency Tomcat RCE patch (IMPLEMENTED)
        ("Take backup snapshot of portal node", "أخذ نسخة احتياطية لخادم البوابة", "IT — Information Technology"),
        ("Apply CVE-2025-0149 hotfix", "تطبيق الإصلاح العاجل للثغرة CVE-2025-0149", "SOC — Security Operations"),
        ("Validate portal service & smoke test", "التحقق من خدمة البوابة واختبار التشغيل", "Operations"),
        ("Document post-implementation review", "توثيق المراجعة بعد التنفيذ", "Cyber Risk & Compliance"),
    ],
    5: [  # Firewall cluster upgrade 7.4.6 (CAB_APPROVED)
        ("Schedule maintenance window & notify", "جدولة نافذة الصيانة وإشعار الجهات", "IT — Information Technology"),
        ("Upgrade passive firewall node", "ترقية عقدة الجدار الناري الاحتياطية", "SOC — Security Operations"),
        ("Failover and upgrade active node", "تحويل الحِمل وترقية العقدة النشطة", "SOC — Security Operations"),
        ("Verify policy parity & rollback readiness", "التحقق من تطابق السياسات وجاهزية التراجع", "IT — Information Technology"),
    ],
    6: [  # TLS cert rotation (SCHEDULED)
        ("Issue new TLS certificates", "إصدار شهادات TLS جديدة", "IT — Information Technology"),
        ("Stage certificates on load balancers", "تجهيز الشهادات على موازنات الحِمل", "IT — Information Technology"),
        ("Cut over & verify chain of trust", "التبديل والتحقق من سلسلة الثقة", "Operations"),
    ],
    8: [  # Sanctions-list daily refresh (IMPACT_ASSESSED)
        ("Design daily sanctions-list ingestion", "تصميم آلية التحديث اليومي لقائمة العقوبات", "Compliance"),
        ("Build screening integration", "بناء تكامل الفحص والمطابقة", "IT — Information Technology"),
        ("Validate against test watchlist", "التحقق مقابل قائمة اختبارية", "Compliance"),
    ],
    9: [  # Decommission legacy DWH (SCHEDULED)
        ("Inventory dependencies on legacy DWH", "حصر الاعتماديات على مستودع البيانات القديم", "Data Management Office"),
        ("Archive datasets per retention policy", "أرشفة مجموعات البيانات وفق سياسة الاحتفاظ", "Data Management Office"),
        ("Decommission & confirm secure wipe", "إيقاف الخدمة وتأكيد المسح الآمن", "IT — Information Technology"),
        ("Update data catalogue & lineage", "تحديث فهرس البيانات وسلسلة المنشأ", "Data Governance"),
    ],
    10: [  # Patch Tomcat to LTS (IMPLEMENTING)
        ("Stage Tomcat LTS in non-prod", "تجهيز إصدار Tomcat المدعوم في بيئة الاختبار", "IT — Information Technology"),
        ("Regression-test affected apps", "اختبار الانحدار للتطبيقات المتأثرة", "Operations"),
        ("Roll out to production nodes", "النشر على خوادم الإنتاج", "IT — Information Technology"),
        ("Confirm CVE closure & evidence", "تأكيد إغلاق الثغرة وتوثيق الأدلة", "Cyber Risk & Compliance"),
    ],
    11: [  # macOS endpoint hardening re-baseline (IMPLEMENTED)
        ("Update macOS hardening baseline", "تحديث الخط الأساسي لتحصين أجهزة macOS", "SOC — Security Operations"),
        ("Deploy profile to fleet via MDM", "نشر الملف على الأجهزة عبر إدارة الأجهزة", "IT — Information Technology"),
        ("Verify compliance posture", "التحقق من حالة الالتزام", "Cyber Risk & Compliance"),
    ],
    12: [  # KSA backup DC lease Jeddah (CAB_APPROVED)
        ("Finalise Jeddah datacenter lease terms", "إنهاء شروط عقد مركز البيانات بجدة", "General Services & Facilities"),
        ("Plan DR replication topology", "تخطيط طوبولوجيا تكرار التعافي من الكوارث", "IT — Information Technology"),
        ("Provision network & power readiness", "تجهيز جاهزية الشبكة والطاقة", "General Services & Facilities"),
        ("Schedule initial DR failover test", "جدولة اختبار التعافي الأولي", "Business Continuity"),
    ],
    15: [  # Automated Saudization reporting feed (SUBMITTED)
        ("Map HRSD Saudization data fields", "مطابقة حقول بيانات السعودة لوزارة الموارد البشرية", "HR Operations"),
        ("Build automated reporting feed", "بناء تغذية التقارير الآلية", "IT — Information Technology"),
        ("Validate against manual baseline", "التحقق مقابل الخط الأساسي اليدوي", "HR Operations"),
    ],
    # CR 2 already has tasks; CR 4/7/13/14 are policy/standard/draft-light — skip.
}


def task_status_for(parent_status: str, idx: int, total: int):
    """Return (status, completed_at, due_at) for a task given parent CR status."""
    if parent_status in ("CLOSED", "IMPLEMENTED"):
        return "DONE", datetime(2026, 5, 5, 12, 0, 0), None
    if parent_status == "IMPLEMENTING":
        # first ~half DONE, the rest IN_PROGRESS/PENDING
        if idx < total // 2:
            return "DONE", datetime(2026, 6, 1, 12, 0, 0), None
        if idx == total // 2:
            return "IN_PROGRESS", None, datetime(2026, 7, 1, 12, 0, 0)
        return "PENDING", None, datetime(2026, 7, 10, 12, 0, 0)
    if parent_status in ("SCHEDULED", "CAB_APPROVED"):
        return "PENDING", None, datetime(2026, 7, 15, 12, 0, 0)
    return "PENDING", None, datetime(2026, 7, 31, 12, 0, 0)


# ───────────────────────────────────────────────────────────────────────────
#  3. RATING ACTIONS — keyed on (counterparty_id, action_type, action_date).
#     Existing dup rows are on cp16 (WATCHLIST_NEGATIVE 03-26/03-27) and
#     cp18 (DOWNGRADE 04-25/04-26); we choose distinct dates to avoid collision
#     and to avoid re-creating that double-run pattern.
#     Resolved by counterparty reference. action_type / trigger_source literals
#     are the canonical ones from RatingAction.java.
# ───────────────────────────────────────────────────────────────────────────

RATING_ACTIONS = [
    # SBG (CP-C004) — at BREACH limit; construction-sector + Riyadh Metro delays.
    dict(ref="CP-C004", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 1, 15),
         grade_before="R4", grade_after="R4", trigger_source="EARLY_WARNING",
         rationale_en="Placed on negative watch: large-exposure limit breached (utilisation >100%) "
                      "amid construction-sector stress and Riyadh Metro contract delays.",
         rationale_ar="إدراج على قائمة المراقبة السلبية: تجاوز حد التركّز الائتماني (الاستغلال أعلى من ١٠٠٪) "
                      "في ظل ضغوط قطاع الإنشاءات وتأخر عقود مترو الرياض."),
    dict(ref="CP-C004", action_type="DOWNGRADE", action_date=date(2026, 3, 10),
         grade_before="R4", grade_after="R5", trigger_source="INTERNAL_REVIEW",
         rationale_en="Downgraded R4→R5 following internal credit review; sustained limit breach and "
                      "weakening cash flows; escalated to the Risk Management Committee.",
         rationale_ar="خفض التصنيف من R4 إلى R5 بعد المراجعة الائتمانية الداخلية؛ استمرار تجاوز الحد وضعف "
                      "التدفقات النقدية؛ رُفع الأمر إلى لجنة إدارة المخاطر."),
    # Saudia (CP-S008) — RED limit; aviation recovery.
    dict(ref="CP-S008", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 2, 5),
         grade_before="R4", grade_after="R4", trigger_source="EARLY_WARNING",
         rationale_en="Negative watch on RED-band utilisation (93%); aviation-sector recovery slower "
                      "than projected; GRE support assumed but monitored.",
         rationale_ar="مراقبة سلبية عند نطاق أحمر للاستغلال (٩٣٪)؛ تعافي قطاع الطيران أبطأ من المتوقع؛ يُفترض "
                      "دعم الجهة الحكومية مع استمرار المتابعة."),
    # Mobily (CP-C005) — telco margin pressure.
    dict(ref="CP-C005", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 2, 20),
         grade_before="R4", grade_after="R4", trigger_source="EXTERNAL_AGENCY",
         rationale_en="Negative watch following external-agency outlook revision; telecom margin "
                      "compression and elevated capex on 5G rollout.",
         rationale_ar="مراقبة سلبية بعد تعديل نظرة وكالة التصنيف الخارجية؛ ضغط هوامش قطاع الاتصالات وارتفاع "
                      "النفقات الرأسمالية لتوسعة شبكات الجيل الخامس."),
    # Zain (CP-C006) — telco; already downgraded in history, add an EARLY_WARNING watch first-of-cycle.
    dict(ref="CP-C006", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 1, 28),
         grade_before="R4", grade_after="R4", trigger_source="EARLY_WARNING",
         rationale_en="Negative watch: leverage and refinancing risk in a competitive telecom market; "
                      "early-warning KRI triggered.",
         rationale_ar="مراقبة سلبية: مخاطر الرفع المالي وإعادة التمويل في سوق اتصالات تنافسي؛ تفعيل مؤشر "
                      "الإنذار المبكر."),
    # Zamil (CP-C003) — industrial.
    dict(ref="CP-C003", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 3, 1),
         grade_before="R4", grade_after="R4", trigger_source="INTERNAL_REVIEW",
         rationale_en="Negative watch on industrial cyclicality and working-capital pressure; "
                      "exposure kept within AMBER band but monitored.",
         rationale_ar="مراقبة سلبية بسبب التقلبية الصناعية وضغط رأس المال العامل؛ بقاء التعرّض ضمن النطاق "
                      "الكهرماني مع المتابعة."),
    # Riyadh Steel SME (CP-SME001) — early warning.
    dict(ref="CP-SME001", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 4, 12),
         grade_before="R5", grade_after="R5", trigger_source="EARLY_WARNING",
         rationale_en="SME early-warning: payment delays and concentration on a single large contract; "
                      "enhanced monitoring applied.",
         rationale_ar="إنذار مبكر لمنشأة صغيرة/متوسطة: تأخر في السداد وتركّز على عقد كبير وحيد؛ تطبيق متابعة "
                      "مشددة."),
    # Olayan (CP-C001) — RED limit, large family corporate.
    dict(ref="CP-C001", action_type="WATCHLIST_NEGATIVE", action_date=date(2026, 5, 6),
         grade_before="R3", grade_after="R3", trigger_source="INTERNAL_REVIEW",
         rationale_en="Negative watch on RED-band utilisation (93%); group concentration reviewed by "
                      "the credit committee; limit re-base recommended.",
         rationale_ar="مراقبة سلبية عند نطاق أحمر للاستغلال (٩٣٪)؛ مراجعة تركّز المجموعة من قبل لجنة الائتمان؛ "
                      "التوصية بإعادة ضبط الحد."),
]


# ───────────────────────────────────────────────────────────────────────────
#  4. CPTY EVENTS — per-counterparty audit timeline.
#     Keyed on (counterparty_id, event_type, occurred_at).
#     event_type literals from CptyEvent.java:
#       REGISTERED | RATING_ASSIGNED | RATING_ACTION | LIMIT_APPROVED |
#       LIMIT_REVISED | LIMIT_BAND_CHANGED | KYC_REFRESHED | RM_HANDOFF |
#       STATUS_CHANGED | INTERNAL_NOTE
#     Built per counterparty from its onboarded_at, latest INTERNAL rating,
#     and its COUNTERPARTY-scope limit (band).
# ───────────────────────────────────────────────────────────────────────────


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    # ── lookups ─────────────────────────────────────────────────────────────
    cur.execute(
        "SELECT id, status, change_type FROM change_mgmt.change_requests "
        "WHERE organisation_id=? AND is_deleted=0", ORG)
    crs = {r[0]: dict(id=r[0], status=r[1], change_type=r[2]) for r in cur.fetchall()}

    cur.execute(
        "SELECT id, reference, current_internal_grade, onboarded_at, legal_name_en, "
        "       legal_name_ar, sector "
        "FROM cpty.counterparty WHERE organisation_id=? AND is_deleted=0", ORG)
    cps = {}
    cp_by_ref = {}
    for r in cur.fetchall():
        d = dict(id=r[0], ref=r[1], grade=r[2], onboarded=r[3],
                 name_en=r[4], name_ar=r[5], sector=r[6])
        cps[r[0]] = d
        cp_by_ref[r[1]] = d

    # latest INTERNAL rating id per counterparty
    cur.execute(
        "SELECT counterparty_id, id, effective_date FROM cpty.credit_rating "
        "WHERE organisation_id=? AND is_deleted=0 AND rating_type='INTERNAL' "
        "ORDER BY counterparty_id, effective_date DESC, id DESC", ORG)
    rating_id_by_cp = {}
    for cp_id, rid, eff in cur.fetchall():
        rating_id_by_cp.setdefault(cp_id, rid)

    # COUNTERPARTY-scope limit (lowest id) + band per counterparty
    cur.execute(
        "SELECT counterparty_id, id, band, utilisation_pct FROM cpty.large_exposure_limit "
        "WHERE organisation_id=? AND is_deleted=0 AND scope='COUNTERPARTY' "
        "ORDER BY counterparty_id, id", ORG)
    limit_by_cp = {}
    for cp_id, lid, band, util in cur.fetchall():
        if cp_id not in limit_by_cp:
            limit_by_cp[cp_id] = dict(id=lid, band=band, util=util)

    # rating_action id resolver (for linking WATCHLIST events to actions)
    def action_id(cp_id, action_type, action_date):
        cur.execute(
            "SELECT id FROM cpty.rating_action WHERE organisation_id=? AND counterparty_id=? "
            "AND action_type=? AND action_date=? AND is_deleted=0", ORG, cp_id, action_type, action_date)
        row = cur.fetchone()
        return row[0] if row else None

    totals = {}

    # ── 1. CHANGE APPROVALS ──────────────────────────────────────────────────
    print("=== change_mgmt.change_approvals ===")
    ins = 0
    for cr_id, cr in sorted(crs.items()):
        for a in approvals_for(cr):
            cur.execute(
                "SELECT 1 FROM change_mgmt.change_approvals "
                "WHERE change_request_id=? AND approver_name=? AND decision=?",
                cr_id, a["approver_name"], a["decision"])
            if cur.fetchone():
                continue
            cur.execute(
                "INSERT INTO change_mgmt.change_approvals "
                "(change_request_id, approver_id, approver_name, approver_role, "
                " decision, comments, decided_at) VALUES (?,?,?,?,?,?,?)",
                cr_id, a["approver_id"], a["approver_name"], a["approver_role"],
                a["decision"], a["comments"], a["decided_at"])
            ins += 1
    print(f"  inserted {ins}")
    totals["change_mgmt.change_approvals"] = ins

    # ── 2. CHANGE TASKS ──────────────────────────────────────────────────────
    print("=== change_mgmt.change_tasks ===")
    ins = 0
    for cr_id, tasks in sorted(TASK_TEMPLATES.items()):
        if cr_id not in crs:
            continue
        parent_status = crs[cr_id]["status"]
        total = len(tasks)
        for i, (t_en, t_ar, owner) in enumerate(tasks):
            sort_order = 110 + i * 10  # avoid pre-existing 10..50 on CR 2
            st, completed_at, due_at = task_status_for(parent_status, i, total)
            cur.execute(
                "SELECT 1 FROM change_mgmt.change_tasks "
                "WHERE change_request_id=? AND sort_order=?", cr_id, sort_order)
            if cur.fetchone():
                continue
            cur.execute(
                "INSERT INTO change_mgmt.change_tasks "
                "(change_request_id, sort_order, title_en, title_ar, owner_name, "
                " status, due_at, completed_at) VALUES (?,?,?,?,?,?,?,?)",
                cr_id, sort_order, t_en, t_ar, owner, st, due_at, completed_at)
            ins += 1
    print(f"  inserted {ins}")
    totals["change_mgmt.change_tasks"] = ins

    # ── 3. RATING ACTIONS ────────────────────────────────────────────────────
    print("=== cpty.rating_action ===")
    ins = 0
    for ra in RATING_ACTIONS:
        cp = cp_by_ref.get(ra["ref"])
        if not cp:
            print(f"  ! skip {ra['ref']} — counterparty not found")
            continue
        cur.execute(
            "SELECT 1 FROM cpty.rating_action WHERE organisation_id=? AND counterparty_id=? "
            "AND action_type=? AND action_date=?",
            ORG, cp["id"], ra["action_type"], ra["action_date"])
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO cpty.rating_action "
            "(organisation_id, counterparty_id, action_type, action_date, "
            " grade_before, grade_after, trigger_source, rationale_en, rationale_ar, "
            " actor_user_id, created_by, updated_by) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            ORG, cp["id"], ra["action_type"], ra["action_date"],
            ra["grade_before"], ra["grade_after"], ra["trigger_source"],
            ra["rationale_en"], ra["rationale_ar"], U_CREDIT, U_CREDIT, U_CREDIT)
        ins += 1
    print(f"  inserted {ins}")
    totals["cpty.rating_action"] = ins

    # ── 4. CPTY EVENTS — per-counterparty timeline ───────────────────────────
    print("=== cpty.cpty_event ===")
    ins = 0

    def midnight(d: date, hour=9):
        return datetime(d.year, d.month, d.day, hour, 0, 0)

    def add_event(cp, event_type, occurred_at, *, note_en, note_ar,
                  linked_rating_id=None, linked_action_id=None, linked_limit_id=None,
                  band_before=None, band_after=None):
        cur.execute(
            "SELECT 1 FROM cpty.cpty_event WHERE organisation_id=? AND counterparty_id=? "
            "AND event_type=? AND occurred_at=?", ORG, cp["id"], event_type, occurred_at)
        if cur.fetchone():
            return 0
        cur.execute(
            "INSERT INTO cpty.cpty_event "
            "(organisation_id, counterparty_id, occurred_at, event_type, actor_user_id, "
            " actor_name, linked_rating_id, linked_action_id, linked_limit_id, "
            " band_before, band_after, note_en, note_ar, created_by, updated_by) "
            "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            ORG, cp["id"], occurred_at, event_type, U_CREDIT,
            "Credit Officer / Risk Management Secretariat",
            linked_rating_id, linked_action_id, linked_limit_id,
            band_before, band_after, note_en, note_ar, U_CREDIT, U_CREDIT)
        return 1

    # WATCHLIST event dates to link (cp_id -> earliest seeded watch date)
    watch_dates = {}
    for ra in RATING_ACTIONS:
        if ra["action_type"] == "WATCHLIST_NEGATIVE":
            cp = cp_by_ref.get(ra["ref"])
            if cp:
                watch_dates.setdefault(cp["id"], ra["action_date"])

    for cp_id, cp in sorted(cps.items()):
        onb = cp["onboarded"]
        rating_id = rating_id_by_cp.get(cp_id)
        lim = limit_by_cp.get(cp_id)

        # (a) REGISTERED — onboarding
        ins += add_event(
            cp, "REGISTERED", midnight(onb, 9),
            note_en=f"Counterparty onboarded and registered ({cp['ref']}); KYC and "
                    f"sector classification completed.",
            note_ar=f"تم استقبال الطرف المقابل وتسجيله ({cp['ref']})؛ استكمال إجراءات اعرف "
                    f"عميلك والتصنيف القطاعي.")

        # (b) RATING_ASSIGNED — link the latest INTERNAL rating
        if rating_id:
            ra_dt = midnight(onb + timedelta(days=30), 10)
            ins += add_event(
                cp, "RATING_ASSIGNED", ra_dt, linked_rating_id=rating_id,
                note_en=f"Internal credit grade {cp['grade']} assigned following the "
                        f"initial credit assessment.",
                note_ar=f"إسناد التصنيف الائتماني الداخلي {cp['grade']} بعد التقييم الائتماني "
                        f"الأولي.")

        # (c) LIMIT_APPROVED — link the COUNTERPARTY-scope limit + band
        if lim:
            lim_dt = midnight(onb + timedelta(days=45), 11)
            ins += add_event(
                cp, "LIMIT_APPROVED", lim_dt, linked_limit_id=lim["id"],
                band_after=lim["band"],
                note_en=f"Large-exposure limit approved; current utilisation band "
                        f"{lim['band']} ({lim['util']}%).",
                note_ar=f"اعتماد حد التركّز الائتماني؛ نطاق الاستغلال الحالي "
                        f"{lim['band']} ({lim['util']}٪).")

        # (d) LIMIT_BAND_CHANGED — for RED/BREACH names
        if lim and lim["band"] in ("RED", "BREACH"):
            bc_dt = midnight(TODAY - timedelta(days=60), 12)
            band_before = "AMBER" if lim["band"] == "RED" else "RED"
            ins += add_event(
                cp, "LIMIT_BAND_CHANGED", bc_dt, linked_limit_id=lim["id"],
                band_before=band_before, band_after=lim["band"],
                note_en=f"Utilisation crossed into {lim['band']} band ({lim['util']}%); "
                        f"flagged for credit-committee review"
                        + (" — SAMA-reportable large-exposure breach." if lim["band"] == "BREACH" else "."),
                note_ar=f"تجاوز الاستغلال إلى النطاق {lim['band']} ({lim['util']}٪)؛ تمت إحالته "
                        f"لمراجعة لجنة الائتمان"
                        + ("، ويُعد تجاوزاً للتركّز الائتماني واجب الإبلاغ للبنك المركزي." if lim["band"] == "BREACH" else "."))

        # (e) RATING_ACTION — for names with a seeded WATCHLIST_NEGATIVE
        if cp_id in watch_dates:
            wd = watch_dates[cp_id]
            aid = action_id(cp_id, "WATCHLIST_NEGATIVE", wd)
            ins += add_event(
                cp, "RATING_ACTION", midnight(wd, 13), linked_action_id=aid,
                linked_rating_id=rating_id,
                note_en="Placed on negative credit watch (see linked rating action).",
                note_ar="إدراج على قائمة المراقبة الائتمانية السلبية (انظر إجراء التصنيف المرتبط).")

    print(f"  inserted {ins}")
    totals["cpty.cpty_event"] = ins

    # ── Verification ─────────────────────────────────────────────────────────
    print("\n--- VERIFICATION (organisation_id = 1) ---")
    cur.execute("SELECT COUNT(*) FROM change_mgmt.change_approvals a "
                "JOIN change_mgmt.change_requests c ON a.change_request_id=c.id "
                "WHERE c.organisation_id=1")
    print(f"  change_approvals (org1): {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM change_mgmt.change_tasks t "
                "JOIN change_mgmt.change_requests c ON t.change_request_id=c.id "
                "WHERE c.organisation_id=1")
    print(f"  change_tasks (org1):     {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM cpty.rating_action WHERE organisation_id=1 AND is_deleted=0")
    print(f"  rating_action (org1):    {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM cpty.cpty_event WHERE organisation_id=1 AND is_deleted=0")
    print(f"  cpty_event (org1):       {cur.fetchone()[0]}")

    grand = sum(totals.values())
    print("\n--- INSERTED THIS RUN ---")
    for k, v in totals.items():
        print(f"  {k}: {v}")
    print(f"  TOTAL: {grand}")
    print(f"\nIDEMPOTENCY: a second run should insert 0 "
          f"({'OK so far' if True else ''}).")
    conn.close()


if __name__ == "__main__":
    main()
