# -*- coding: utf-8 -*-
"""Seed Saudi-government reporting & workflow data — domain "reporting_wf".

Schemas: reporting + workflow. Tenant: organisation_id = 1 ONLY (a Saudi General
Authority — هيئة عامة — headed by a President, رئيس الهيئة).

This script demonstrates the platform's board/regulator reporting calendar and
its government approval flows. Two parallel workflow engines coexist:

  (A) FSM engine  — workflow.definitions/states/transitions/instances/action_log
                    (already richly seeded; we only ADD cross-module instances +
                    their action-log history on the EXISTING definitions).
  (B) SLA-matrix runtime — workflow.workflow_definitions (20-row catalog, kept) +
                    workflow.workflow_instances + workflow.workflow_tasks (PRIME
                    fill — these JPA-backed tables drive approval dashboards).
  (C) Generic wizard ledger — workflow.workflow_submissions (light deepen).
  (D) Sprint-13 approval-chain family — approval_chain_template/step (add 3 gov
                    ladders) + approval_chain_instance/action (PRIME fill).

Reporting: report_definitions(20) + report_executions(0) are entity-less (no JPA
entity, no backend reader) → SKIPPED (already rich / dead to backend). The
entity-backed report_schedules + report_runs are deepened.

IDEMPOTENT: every INSERT is guarded by a natural key; a second run inserts 0.
INSERT/UPSERT ONLY — no DELETE/TRUNCATE/DROP/ALTER. Arabic passed as Unicode
parameters. Regulators woven in: NCA/ECC, NDMO+SDAIA, PDPL, NCEMA, Nazaha, DGA,
ZATCA, CMA, Vision 2030.

    Usage:  python seed_gov_reporting_wf.py   (run twice; 2nd run must be +0)
"""
from __future__ import annotations

import json
import sys
from datetime import datetime, timedelta

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
NOW = datetime(2026, 6, 27)          # "today" anchor for determinism
ADMIN, RMANAGER, COMPLIANCE, AUDITOR, RANALYST, VIEWER = 1, 2, 3, 4, 5, 6

# Running tallies (per-table) ------------------------------------------------
counts: dict[str, int] = {}
skips: list[str] = []
blockers: list[str] = []


def bump(table: str, n: int = 1) -> None:
    counts[table] = counts.get(table, 0) + n


def j(d: dict) -> str:
    return json.dumps(d, ensure_ascii=False)


def out_id(cur) -> int:
    """Read the id returned by an INSERT ... OUTPUT INSERTED.id statement.

    With autocommit=True each execute is its own batch, so SCOPE_IDENTITY()/
    @@IDENTITY do not survive into a follow-up query. The OUTPUT clause returns
    the new id as a result set of the INSERT itself, which is reliable."""
    return int(cur.fetchone()[0])


# ===========================================================================
# A. reporting.report_schedules  (deepen: +8 government schedules)
# ===========================================================================
# recipients = role-mailbox placeholders; next_run_at forward of 2026-06-27.
SCHEDULES = [
    dict(name="Quarterly Board Risk & Compliance Pack",
         name_ar="حزمة المخاطر والالتزام الفصلية لمجلس الإدارة",
         report_type="BOARD_RISK_COMPLIANCE_PACK", fmt="PDF", lang="ar",
         cadence="QUARTERLY", anchor="1", tod="07:00",
         recipients="board-secretariat@authority.gov.sa,board@authority.gov.sa",
         cc="risk-committee@authority.gov.sa",
         desc="Quarterly consolidated risk, compliance and governance pack submitted to the Board of Directors via the Board & Committees Secretariat.",
         desc_ar="حزمة فصلية موحدة للمخاطر والالتزام والحوكمة تُرفع لمجلس إدارة الهيئة عبر أمانة المجلس واللجان.",
         next_run=datetime(2026, 7, 1, 7, 0)),
    dict(name="Monthly Executive Committee KRI Dashboard",
         name_ar="لوحة مؤشرات المخاطر الرئيسية الشهرية للجنة التنفيذية",
         report_type="KRI_DASHBOARD", fmt="PDF", lang="ar",
         cadence="MONTHLY", anchor="1", tod="06:30",
         recipients="exec-committee@authority.gov.sa,cro@authority.gov.sa",
         cc="president-office@authority.gov.sa",
         desc="Monthly key-risk-indicator dashboard for the Executive Committee chaired by the President of the Authority.",
         desc_ar="لوحة شهرية لمؤشرات المخاطر الرئيسية تُعرض على اللجنة التنفيذية برئاسة رئيس الهيئة.",
         next_run=datetime(2026, 7, 1, 6, 30)),
    dict(name="Quarterly NCA ECC Cybersecurity Posture",
         name_ar="تقرير الوضع السيبراني الفصلي وفق الضوابط الأساسية",
         report_type="CYBER_POSTURE_ECC", fmt="PDF", lang="ar",
         cadence="QUARTERLY", anchor="5", tod="08:00",
         recipients="cyber-committee@authority.gov.sa,ciso@authority.gov.sa",
         cc="cyber-grc@authority.gov.sa",
         desc="Quarterly NCA Essential Cybersecurity Controls (ECC) posture report to the Cybersecurity Supervisory Committee.",
         desc_ar="تقرير فصلي عن الالتزام بالضوابط الأساسية للأمن السيبراني للهيئة الوطنية يُرفع للجنة الإشرافية للأمن السيبراني.",
         next_run=datetime(2026, 7, 5, 8, 0)),
    dict(name="Quarterly Vendor / TPRM Risk Posture",
         name_ar="تقرير مخاطر الأطراف الثالثة الفصلي",
         report_type="TPRM_RISK_POSTURE", fmt="PDF", lang="ar",
         cadence="QUARTERLY", anchor="10", tod="09:00",
         recipients="risk-committee@authority.gov.sa,procurement@authority.gov.sa",
         cc="vendor-risk@authority.gov.sa",
         desc="Quarterly third-party / vendor risk posture report covering critical suppliers and outsourcing concentration.",
         desc_ar="تقرير فصلي لمخاطر الأطراف الثالثة والموردين يغطي الموردين الحرجين وتركّز الإسناد.",
         next_run=datetime(2026, 7, 10, 9, 0)),
    dict(name="Quarterly Internal Audit Plan Progress",
         name_ar="تقرير تقدم خطة المراجعة الداخلية الفصلي",
         report_type="AUDIT_PLAN_PROGRESS", fmt="PDF", lang="ar",
         cadence="QUARTERLY", anchor="15", tod="07:30",
         recipients="audit-committee@authority.gov.sa,internal-audit@authority.gov.sa",
         cc="board-secretariat@authority.gov.sa",
         desc="Quarterly internal audit plan execution and findings-ageing report to the Audit Committee.",
         desc_ar="تقرير فصلي عن تنفيذ خطة المراجعة الداخلية وأعمار الملاحظات يُرفع للجنة المراجعة.",
         next_run=datetime(2026, 7, 15, 7, 30)),
    dict(name="Quarterly ESG / Sustainability Annex",
         name_ar="ملحق الاستدامة والإفصاح البيئي والاجتماعي الفصلي",
         report_type="ESG_ANNEX", fmt="PDF", lang="ar",
         cadence="QUARTERLY", anchor="20", tod="08:30",
         recipients="sustainability-committee@authority.gov.sa,strategy@authority.gov.sa",
         cc="board-secretariat@authority.gov.sa",
         desc="Quarterly ESG / sustainability disclosure annex aligned with CMA Articles 91-94 and Vision 2030.",
         desc_ar="ملحق فصلي للإفصاح عن الاستدامة والحوكمة البيئية والاجتماعية بما يتوافق مع مواد هيئة السوق المالية 91-94 ورؤية 2030.",
         next_run=datetime(2026, 7, 20, 8, 30)),
    dict(name="Monthly PDPL DSR SLA & Breach Tracker",
         name_ar="متابعة شهرية لطلبات أصحاب البيانات ومستوى الخدمة والانتهاكات",
         report_type="PDPL_DSR_BREACH_TRACKER", fmt="XLSX", lang="ar",
         cadence="MONTHLY", anchor="1", tod="06:00",
         recipients="privacy-committee@authority.gov.sa,dpo@authority.gov.sa",
         cc="compliance@authority.gov.sa",
         desc="Monthly PDPL data-subject-request SLA compliance and personal-data breach tracker to the Personal Data Protection Committee.",
         desc_ar="متابعة شهرية لمستوى خدمة طلبات أصحاب البيانات وسجل انتهاكات البيانات الشخصية تُرفع للجنة حماية البيانات الشخصية.",
         next_run=datetime(2026, 7, 1, 6, 0)),
    dict(name="Semi-annual ISO 22301 / NCEMA BCM Readiness",
         name_ar="تقرير جاهزية استمرارية الأعمال نصف السنوي",
         report_type="BCM_READINESS", fmt="PDF", lang="ar",
         cadence="SEMIANNUAL", anchor="1", tod="09:30",
         recipients="bcm-committee@authority.gov.sa,bcm@authority.gov.sa",
         cc="crisis-committee@authority.gov.sa",
         desc="Semi-annual business continuity readiness report (ISO 22301 / NCEMA) to the BCM Steering Committee.",
         desc_ar="تقرير نصف سنوي عن جاهزية استمرارية الأعمال وفق آيزو 22301 والمركز الوطني لإدارة الأزمات يُرفع للجنة الإشرافية لاستمرارية الأعمال.",
         next_run=datetime(2026, 8, 1, 9, 30)),
]


def seed_schedules(cur) -> dict[str, int]:
    """Insert schedules idempotently by (organisation_id, name). Returns name->id."""
    ids: dict[str, int] = {}
    for s in SCHEDULES:
        cur.execute(
            "SELECT id FROM reporting.report_schedules WHERE organisation_id=? AND name=?",
            ORG, s["name"],
        )
        row = cur.fetchone()
        if row:
            ids[s["name"]] = row[0]
            continue
        cur.execute(
            """INSERT INTO reporting.report_schedules
                   (organisation_id, name, name_ar, description, description_ar,
                    report_type, format, language, cadence, cadence_anchor,
                    time_of_day, recipients, cc_recipients, is_active,
                    last_run_at, next_run_at, last_run_status, last_run_message,
                    is_deleted, created_at, updated_at, created_by, updated_by)
               OUTPUT INSERTED.id
               VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,1,NULL,?,NULL,NULL,0,
                       SYSUTCDATETIME(), SYSUTCDATETIME(), ?, ?)""",
            ORG, s["name"], s["name_ar"], s["desc"], s["desc_ar"],
            s["report_type"], s["fmt"], s["lang"], s["cadence"], s["anchor"],
            s["tod"], s["recipients"], s["cc"], s["next_run"], ADMIN, ADMIN,
        )
        ids[s["name"]] = out_id(cur)
        bump("reporting.report_schedules")
        print(f"  + schedule  {s['name']}")
    return ids


# ===========================================================================
# B. reporting.report_runs  (deepen: 2 prior SUCCESS runs per NEW schedule)
# ===========================================================================
_CADENCE_DAYS = {"MONTHLY": 30, "QUARTERLY": 91, "SEMIANNUAL": 182}


def seed_runs(cur, sched_ids: dict[str, int]) -> None:
    for s in SCHEDULES:
        sid = sched_ids[s["name"]]
        step = _CADENCE_DAYS.get(s["cadence"], 30)
        # last two cadence periods before NOW
        for k in (1, 2):
            triggered_at = NOW - timedelta(days=step * k)
            duration = 800 + (step * k) % 5200          # 800..6000-ish
            size = 40_000 + (sid * 9973 + k * 31_337) % 860_000  # 40KB..900KB
            msg = (f"Rendered {size} bytes for {s['report_type']} — "
                   f"تم توليد التقرير ({s['cadence'].lower()}) وإرساله للجنة المختصة.")
            # idempotency guard: (schedule_id, deterministic triggered_at, message)
            cur.execute(
                "SELECT 1 FROM reporting.report_runs WHERE schedule_id=? AND "
                "triggered_at=? AND message=?", sid, triggered_at, msg,
            )
            if cur.fetchone():
                continue
            cur.execute(
                """INSERT INTO reporting.report_runs
                       (schedule_id, organisation_id, triggered_at, triggered_by,
                        status, duration_ms, bytes, recipients, message)
                   VALUES (?,?,?, 'CRON', 'SUCCESS', ?,?,?,?)""",
                sid, ORG, triggered_at, duration, size, s["recipients"], msg,
            )
            bump("reporting.report_runs")
    print(f"  report_runs inserted so far: {counts.get('reporting.report_runs',0)}")


# ===========================================================================
# C. workflow.instances + workflow.action_log  (FSM engine: cross-module)
# ===========================================================================
# Resolve state ids by (definition code, state code).
def fsm_lookup(cur):
    cur.execute(
        """SELECT d.code, s.code, s.id, d.id
             FROM workflow.states s JOIN workflow.definitions d ON s.definition_id=d.id
            WHERE d.organisation_id=?""", ORG)
    state_id: dict[tuple[str, str], int] = {}
    def_id: dict[str, int] = {}
    for dcode, scode, sid, did in cur.fetchall():
        state_id[(dcode, scode)] = sid
        def_id[dcode] = did
    # transitions keyed by (def code, from_state_id, to_state_id) -> transition_id
    cur.execute(
        """SELECT d.code, t.from_state_id, t.to_state_id, t.id, t.action_code
             FROM workflow.transitions t JOIN workflow.definitions d ON t.definition_id=d.id
            WHERE d.organisation_id=?""", ORG)
    trans: dict[tuple[str, int, int], tuple[int, str]] = {}
    for dcode, frm, to, tid, ac in cur.fetchall():
        trans[(dcode, frm, to)] = (tid, ac)
    return def_id, state_id, trans


# Each FSM instance: def code, target_module, target_entity, target_entity_id,
# current state code, owner, started_offset_days, and the ordered path of state
# codes walked from initial to current (for action_log).
FSM_INSTANCES = [
    dict(defc="WF-PDPL-BREACH", mod="PRIVACY", ent="CrisisEvent", eid=9001,
         cur="AUTHORITY_NOTIFIED", owner=COMPLIANCE, started=12,
         path=["DETECTED", "ASSESSING", "AUTHORITY_NOTIFIED"],
         comments=["تم رصد واقعة تسريب بيانات شخصية محتملة.",
                   "تم تقييم الأثر وتصنيف الواقعة.",
                   "تم إخطار الهيئة المختصة (SDAIA) خلال المدة النظامية."]),
    dict(defc="WF-INCIDENT-NIST", mod="INCIDENT", ent="Incident", eid=9002,
         cur="CONTAINING", owner=ADMIN, started=6,
         path=["DETECTED", "ANALYSING", "CONTAINING"],
         comments=["رصد مركز العمليات حادثة سيبرانية.",
                   "تحليل المؤشرات وتحديد نطاق التأثر.",
                   "جارٍ احتواء الحادثة وعزل الأنظمة المتأثرة."]),
    dict(defc="WF-VENDOR-ONBOARDING", mod="SCM", ent="Vendor", eid=9003,
         cur="DUE_DILIGENCE", owner=RMANAGER, started=20,
         path=["INTAKE", "TIERING", "DUE_DILIGENCE"],
         comments=["استلام طلب تأهيل مورد جديد.",
                   "تصنيف المورد ضمن الفئة الحرجة.",
                   "جارٍ إجراء العناية الواجبة على المورد."]),
    dict(defc="WF-POLICY-LIFECYCLE", mod="POLICY", ent="Policy", eid=9004,
         cur="EXEC_APPROVAL", owner=ADMIN, started=15,
         path=["DRAFT", "LEGAL_REVIEW", "EXEC_APPROVAL"],
         comments=["إعداد مسودة سياسة الحوكمة.",
                   "مراجعة الإدارة القانونية للمسودة.",
                   "رفع السياسة لاعتماد اللجنة التنفيذية."]),
    dict(defc="WF-GENERIC-APPROVAL", mod="REPORTING", ent="BoardPack", eid=9005,
         cur="UNDER_REVIEW", owner=ADMIN, started=8,
         path=["DRAFT", "UNDER_REVIEW"],
         comments=["إعداد مسودة حزمة مجلس الإدارة للربع الثاني 2026.",
                   "الحزمة قيد مراجعة لجنة المراجعة."]),
    dict(defc="RISK_LIFECYCLE_V1", mod="RISK", ent="Risk", eid=9006,
         cur="MITIGATING", owner=RANALYST, started=18,
         path=["OPEN", "UNDER_REVIEW", "MITIGATING"],
         comments=["فتح بند مخاطر مؤسسية جديد.",
                   "مراجعة المخاطر وتقدير الأثر.",
                   "جارٍ تنفيذ خطة المعالجة."]),
]


def seed_fsm(cur):
    def_id, state_id, trans = fsm_lookup(cur)
    inst_ids: dict[int, int] = {}  # eid -> instance id
    for ins in FSM_INSTANCES:
        did = def_id[ins["defc"]]
        cur_sid = state_id[(ins["defc"], ins["cur"])]
        # idempotency: (definition_id, target_module, target_entity, target_entity_id)
        cur.execute(
            """SELECT id FROM workflow.instances WHERE organisation_id=? AND
               definition_id=? AND target_module=? AND target_entity=? AND target_entity_id=?""",
            ORG, did, ins["mod"], ins["ent"], ins["eid"])
        row = cur.fetchone()
        if row:
            inst_ids[ins["eid"]] = row[0]
            continue
        started = NOW - timedelta(days=ins["started"])
        last_trans = NOW - timedelta(days=1)
        cur.execute(
            """INSERT INTO workflow.instances
                   (organisation_id, definition_id, target_module, target_entity,
                    target_entity_id, current_state_id, owner_id, started_at,
                    completed_at, last_transition_at)
               OUTPUT INSERTED.id
               VALUES (?,?,?,?,?,?,?,?, NULL, ?)""",
            ORG, did, ins["mod"], ins["ent"], ins["eid"], cur_sid, ins["owner"],
            started, last_trans)
        iid = out_id(cur)
        inst_ids[ins["eid"]] = iid
        bump("workflow.instances")
        print(f"  + fsm instance  {ins['defc']} -> {ins['cur']} (eid {ins['eid']})")

    # action_log: walk the path; one row per transition (from->to)
    for ins in FSM_INSTANCES:
        iid = inst_ids[ins["eid"]]
        path = ins["path"]
        base = NOW - timedelta(days=ins["started"])
        for step in range(len(path) - 1):
            frm = state_id[(ins["defc"], path[step])]
            to = state_id[(ins["defc"], path[step + 1])]
            tinfo = trans.get((ins["defc"], frm, to))
            tid = tinfo[0] if tinfo else None
            ac = tinfo[1] if tinfo else "advance"
            comment = ins["comments"][step + 1] if step + 1 < len(ins["comments"]) else ins["comments"][-1]
            # idempotency: (instance_id, to_state_id, action_code)
            cur.execute(
                "SELECT 1 FROM workflow.action_log WHERE instance_id=? AND to_state_id=? AND action_code=?",
                iid, to, ac)
            if cur.fetchone():
                continue
            executed_at = base + timedelta(days=step + 1, hours=3)
            cur.execute(
                """INSERT INTO workflow.action_log
                       (instance_id, transition_id, action_code, from_state_id,
                        to_state_id, executed_by_id, executed_at, comments)
                   VALUES (?,?,?,?,?,?,?,?)""",
                iid, tid, ac, frm, to, ins["owner"], executed_at, comment)
            bump("workflow.action_log")
    print(f"  action_log inserted so far: {counts.get('workflow.action_log',0)}")


# ===========================================================================
# D. workflow.workflow_instances  (PRIME fill: 14) + workflow_tasks (fill: ~22)
# ===========================================================================
def wf_def_ids(cur) -> dict[str, int]:
    cur.execute(
        "SELECT entity_type, id FROM workflow.workflow_definitions WHERE organisation_id=?", ORG)
    return {et: i for et, i in cur.fetchall()}


# instance: entity_type (resolves definition), entity_id, subject_en, status,
# initiated_by, started_offset, due_offset(+/- days from start via SLA), overdue,
# completed_offset (None if open), plus tasks list.
# task: task_type, subject_en, instructions_en(bilingual), assigned_to, status,
#       due_offset, completed_offset(None=open), completed_by, decision, comments_en
WF_INSTANCES = [
    dict(et="CYBER_INCIDENT_MAJOR", eid=9101, status="ESCALATED", by=ADMIN,
         started=2, overdue=1, completed=None,
         subject="Major cyber incident — public e-service outage; SAMA/NCA 6h notification (Cybersecurity Supervisory Committee)",
         tasks=[dict(tt="APPROVAL", subj="CISO containment sign-off", to=ADMIN,
                     status="OVERDUE", due_h=6, comp=None, by=None, dec=None,
                     instr="Authorise containment and confirm NCA/SAMA 6-hour notification. اعتماد الاحتواء وتأكيد الإبلاغ خلال 6 ساعات.")]),
    dict(et="PDPL_DSR", eid=9102, status="IN_PROGRESS", by=COMPLIANCE,
         started=10, overdue=0, completed=None,
         subject="PDPL data-subject access request — beneficiary record (Personal Data Protection Committee)",
         tasks=[dict(tt="REVIEW", subj="DPO verification of data-subject request", to=COMPLIANCE,
                     status="IN_PROGRESS", due_h=720, comp=None, by=None, dec=None,
                     instr="Verify identity and compile responsive personal data. التحقق من الهوية وتجميع البيانات الشخصية ذات الصلة.")]),
    dict(et="PDPL_CBT", eid=9103, status="SUBMITTED", by=COMPLIANCE,
         started=4, overdue=0, completed=None,
         subject="PDPL cross-border transfer assessment — cloud analytics processor",
         tasks=[dict(tt="REVIEW", subj="Legal adequacy review of transfer", to=COMPLIANCE,
                     status="PENDING", due_h=168, comp=None, by=None, dec=None,
                     instr="Assess adequacy and safeguards for cross-border transfer. تقييم كفاية الحماية للنقل خارج المملكة.")]),
    dict(et="BOARD_PACK", eid=9104, status="IN_PROGRESS", by=ADMIN,
         started=12, overdue=0, completed=None,
         subject="Board Pack Q2-2026 — Board & Committees Secretariat compilation (President endorsement)",
         tasks=[dict(tt="APPROVAL", subj="Board Secretariat compilation", to=ADMIN,
                     status="COMPLETED", due_h=72, comp=3, by=ADMIN, dec="APPROVE",
                     instr="Compile and quality-check the board pack. تجميع حزمة المجلس ومراجعة جودتها.",
                     comments="تم تجميع الحزمة واعتمادها للرفع."),
                dict(tt="REVIEW", subj="Audit Committee review", to=AUDITOR,
                     status="IN_PROGRESS", due_h=168, comp=None, by=None, dec=None,
                     instr="Audit Committee review of board pack contents. مراجعة لجنة المراجعة لمحتوى الحزمة.")]),
    dict(et="AUDIT_FINDING_HIGH", eid=9105, status="IN_PROGRESS", by=AUDITOR,
         started=20, overdue=0, completed=None,
         subject="High-severity audit finding — access-control gap (Audit Committee)",
         tasks=[dict(tt="REVIEW", subj="Remediation plan review", to=AUDITOR,
                     status="IN_PROGRESS", due_h=240, comp=None, by=None, dec=None,
                     instr="Review management remediation plan and target dates. مراجعة خطة المعالجة وتواريخها المستهدفة.")]),
    dict(et="VENDOR_ONBOARD_CRIT", eid=9106, status="IN_PROGRESS", by=RMANAGER,
         started=18, overdue=0, completed=None,
         subject="Critical vendor onboarding — core hosting provider due diligence",
         tasks=[dict(tt="REVIEW", subj="Vendor due-diligence assessment", to=RMANAGER,
                     status="IN_PROGRESS", due_h=240, comp=None, by=None, dec=None,
                     instr="Complete due diligence and security review. إكمال العناية الواجبة والمراجعة الأمنية.")]),
    dict(et="BCP_APPROVAL", eid=9107, status="APPROVED", by=ADMIN,
         started=30, overdue=0, completed=5,
         subject="Business Continuity Plan approval — critical service line (BCM Steering Committee)",
         tasks=[dict(tt="APPROVAL", subj="BCP final approval", to=ADMIN,
                     status="COMPLETED", due_h=120, comp=6, by=ADMIN, dec="APPROVE",
                     instr="Approve the updated BCP per ISO 22301 / NCEMA. اعتماد خطة استمرارية الأعمال وفق آيزو 22301.",
                     comments="تمت الموافقة على الخطة بعد استيفاء المتطلبات.")]),
    dict(et="ESG_ANNEX", eid=9108, status="APPROVED", by=ADMIN,
         started=40, overdue=0, completed=10,
         subject="ESG / Sustainability annex approval — CMA Art.91-94 (Sustainability Committee)",
         tasks=[dict(tt="APPROVAL", subj="ESG annex approval", to=ADMIN,
                     status="COMPLETED", due_h=240, comp=12, by=ADMIN, dec="APPROVE",
                     instr="Approve ESG disclosure annex for the annual report. اعتماد ملحق الإفصاح عن الاستدامة.",
                     comments="اعتُمد الملحق ضمن التقرير السنوي.")]),
    dict(et="RISK_TREATMENT", eid=9109, status="APPROVED", by=RMANAGER,
         started=25, overdue=0, completed=8,
         subject="Risk treatment plan approval — supplier concentration risk",
         tasks=[dict(tt="APPROVAL", subj="Risk treatment approval", to=RMANAGER,
                     status="COMPLETED", due_h=72, comp=9, by=RMANAGER, dec="APPROVE",
                     instr="Approve the mitigation plan and residual target. اعتماد خطة المعالجة والهدف المتبقي.",
                     comments="تمت الموافقة على خطة المعالجة.")]),
    dict(et="RISK_ACCEPT", eid=9110, status="SUBMITTED", by=RMANAGER,
         started=3, overdue=0, completed=None,
         subject="Risk acceptance — residual above appetite, legacy system retirement window",
         tasks=[dict(tt="ATTESTATION", subj="Senior management risk acceptance", to=RMANAGER,
                     status="PENDING", due_h=120, comp=None, by=None, dec=None,
                     instr="Attest acceptance of residual risk above appetite. الإقرار بقبول المخاطر المتبقية فوق حدود التقبل.")]),
    dict(et="CAPA_CLOSURE", eid=9111, status="IN_PROGRESS", by=COMPLIANCE,
         started=14, overdue=0, completed=None,
         subject="CAPA closure — corrective action for compliance finding",
         tasks=[dict(tt="REVIEW", subj="CAPA effectiveness review", to=COMPLIANCE,
                     status="IN_PROGRESS", due_h=96, comp=None, by=None, dec=None,
                     instr="Verify corrective action effectiveness before closure. التحقق من فاعلية الإجراء التصحيحي قبل الإغلاق.")]),
    dict(et="REG_CHANGE", eid=9112, status="DRAFT", by=COMPLIANCE,
         started=1, overdue=0, completed=None,
         subject="Regulatory change assessment — NCA/NDMO updated controls",
         tasks=[dict(tt="REVIEW", subj="Impact assessment of regulatory change", to=COMPLIANCE,
                     status="PENDING", due_h=120, comp=None, by=None, dec=None,
                     instr="Assess impact of new NCA/NDMO requirements. تقييم أثر متطلبات الهيئة الوطنية ومكتب إدارة البيانات الجديدة.")]),
    dict(et="VULN_CRITICAL", eid=9113, status="ESCALATED", by=ADMIN,
         started=2, overdue=1, completed=None,
         subject="Critical vulnerability remediation — internet-facing asset (24h SLA)",
         tasks=[dict(tt="APPROVAL", subj="Emergency patch approval", to=ADMIN,
                     status="OVERDUE", due_h=24, comp=None, by=None, dec=None,
                     instr="Approve emergency remediation within 24h. اعتماد المعالجة الطارئة خلال 24 ساعة.")]),
    dict(et="POLICY_PUBLISH", eid=9114, status="APPROVED", by=ADMIN,
         started=22, overdue=0, completed=4,
         subject="Policy publication approval — information security policy (Executive Committee)",
         tasks=[dict(tt="APPROVAL", subj="Policy publication approval", to=ADMIN,
                     status="COMPLETED", due_h=72, comp=5, by=ADMIN, dec="APPROVE",
                     instr="Approve publication of the updated policy. اعتماد نشر السياسة المحدثة.",
                     comments="اعتُمدت السياسة للنشر.")]),
]


def seed_wf_runtime(cur):
    defids = wf_def_ids(cur)
    for wi in WF_INSTANCES:
        did = defids.get(wi["et"])
        if did is None:
            blockers.append(f"workflow_instances: no definition for entity_type {wi['et']}")
            continue
        # idempotency: (definition_id, entity_type, entity_id)
        cur.execute(
            "SELECT id FROM workflow.workflow_instances WHERE organisation_id=? AND "
            "definition_id=? AND entity_type=? AND entity_id=?",
            ORG, did, wi["et"], wi["eid"])
        row = cur.fetchone()
        if row:
            iid = row[0]
        else:
            started = NOW - timedelta(days=wi["started"])
            # SLA hours from the definition
            cur.execute("SELECT sla_hours FROM workflow.workflow_definitions WHERE id=?", did)
            sla = cur.fetchone()[0] or 72
            due = started + timedelta(hours=sla)
            completed = (NOW - timedelta(days=wi["completed"])) if wi["completed"] is not None else None
            cur.execute(
                """INSERT INTO workflow.workflow_instances
                       (organisation_id, definition_id, entity_type, entity_id,
                        subject_en, current_status, initiated_by, initiated_at,
                        due_date, completed_at, is_overdue)
                   OUTPUT INSERTED.id
                   VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, did, wi["et"], wi["eid"], wi["subject"], wi["status"],
                wi["by"], started, due, completed, wi["overdue"])
            iid = out_id(cur)
            bump("workflow.workflow_instances")
            print(f"  + wf_instance  {wi['et']} [{wi['status']}] (eid {wi['eid']})")
        # tasks
        for t in wi["tasks"]:
            cur.execute(
                "SELECT 1 FROM workflow.workflow_tasks WHERE instance_id=? AND task_type=? AND subject_en=?",
                iid, t["tt"], t["subj"])
            if cur.fetchone():
                continue
            started = NOW - timedelta(days=wi["started"])
            due = started + timedelta(hours=t["due_h"])
            comp_at = (NOW - timedelta(days=t["comp"])) if t["comp"] is not None else None
            cur.execute(
                """INSERT INTO workflow.workflow_tasks
                       (instance_id, task_type, assigned_to, subject_en,
                        instructions_en, status, due_date, completed_at,
                        completed_by, decision, comments_en)
                   VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
                iid, t["tt"], t["to"], t["subj"], t["instr"], t["status"],
                due, comp_at, t.get("by"), t.get("dec"), t.get("comments"))
            bump("workflow.workflow_tasks")
    print(f"  wf_instances: {counts.get('workflow.workflow_instances',0)}, "
          f"wf_tasks: {counts.get('workflow.workflow_tasks',0)}")


# ===========================================================================
# E. workflow.workflow_submissions  (deepen: +9 thin-module regulator rounds)
# ===========================================================================
SUBMISSIONS = [
    dict(wid="bcm-bia-approval", mod="BCM", status="APPROVED", anchor="ISO 22301 / NCEMA",
         title="BCM Business Impact Analysis approval — critical services",
         title_ar="اعتماد تحليل أثر الأعمال لاستمرارية الأعمال للخدمات الحرجة",
         summary="Approval of the BIA for the Authority's critical services (BCM Steering Committee).",
         summary_ar="اعتماد تحليل أثر الأعمال للخدمات الحرجة للهيئة عبر اللجنة الإشرافية لاستمرارية الأعمال.",
         payload={"rtoHours": 4, "rpoHours": 1, "criticalServices": 7},
         by=ADMIN, rev=ADMIN),
    dict(wid="ndmo-data-classification", mod="DATAGOV", status="UNDER_REVIEW", anchor="NDMO",
         title="NDMO data-classification register approval",
         title_ar="اعتماد سجل تصنيف البيانات وفق مكتب إدارة البيانات الوطنية",
         summary="Data-classification register submitted to the Data Governance Committee for NDMO alignment.",
         summary_ar="سجل تصنيف البيانات يُرفع للجنة حوكمة البيانات للمواءمة مع مكتب إدارة البيانات الوطنية.",
         payload={"datasets": 42, "levels": ["TOP_SECRET", "SECRET", "RESTRICTED", "PUBLIC"]},
         by=COMPLIANCE, rev=None),
    dict(wid="nazaha-integrity-review", mod="GOVERNANCE", status="SUBMITTED", anchor="Nazaha",
         title="Integrity & whistleblowing programme review",
         title_ar="مراجعة برنامج النزاهة والإبلاغ عن المخالفات",
         summary="Annual integrity and whistleblowing review per Nazaha guidelines (Integrity & Anti-Fraud Committee).",
         summary_ar="المراجعة السنوية للنزاهة والإبلاغ عن المخالفات وفق ضوابط نزاهة عبر لجنة النزاهة ومكافحة الاحتيال.",
         payload={"cases": 3, "channelsActive": True},
         by=AUDITOR, rev=None),
    dict(wid="sdaia-ai-ethics-usecase", mod="DATAGOV", status="UNDER_REVIEW", anchor="SDAIA",
         title="SDAIA AI-ethics use-case review — beneficiary triage model",
         title_ar="مراجعة حالة استخدام أخلاقيات الذكاء الاصطناعي وفق سدايا — نموذج فرز المستفيدين",
         summary="AI use-case submitted to the AI Ethics Committee for SDAIA AI-principles review.",
         summary_ar="حالة استخدام للذكاء الاصطناعي تُرفع للجنة أخلاقيات الذكاء الاصطناعي لمراجعتها وفق مبادئ سدايا.",
         payload={"model": "triage", "biasAssessed": True, "humanInLoop": True},
         by=COMPLIANCE, rev=None),
    dict(wid="dga-digital-service-attestation", mod="GOVERNANCE", status="APPROVED", anchor="DGA",
         title="DGA digital-service compliance attestation",
         title_ar="إقرار التزام الخدمة الرقمية وفق هيئة الحكومة الرقمية",
         summary="Digital-service compliance attestation aligned with DGA standards (IT Steering Committee).",
         summary_ar="إقرار التزام الخدمة الرقمية بمعايير هيئة الحكومة الرقمية عبر اللجنة الإشرافية لتقنية المعلومات.",
         payload={"services": 5, "maturity": "MANAGED"},
         by=ADMIN, rev=ADMIN),
    dict(wid="ncema-crisis-exercise-aar", mod="BCM", status="CLOSED", anchor="NCEMA",
         title="NCEMA crisis-exercise after-action report",
         title_ar="تقرير ما بعد التمرين لتمرين إدارة الأزمات وفق المركز الوطني",
         summary="After-action report for the crisis simulation exercise (Crisis Management Committee).",
         summary_ar="تقرير ما بعد التمرين لتمرين محاكاة الأزمات عبر لجنة إدارة الأزمات.",
         payload={"exerciseType": "tabletop", "findings": 4, "actionsClosed": 4},
         by=ADMIN, rev=ADMIN),
    dict(wid="zatca-einvoicing-attestation", mod="COMPLIANCE", status="APPROVED", anchor="ZATCA",
         title="ZATCA e-invoicing control attestation",
         title_ar="إقرار ضوابط الفوترة الإلكترونية وفق هيئة الزكاة والضريبة والجمارك",
         summary="E-invoicing (FATOORA) control attestation submitted to the Compliance Committee.",
         summary_ar="إقرار ضوابط الفوترة الإلكترونية (فاتورة) يُرفع للجنة الالتزام.",
         payload={"phase": 2, "controlsPassed": 18},
         by=COMPLIANCE, rev=ADMIN),
    dict(wid="nca-scc-cloud-reassessment", mod="NCA", status="SUBMITTED", anchor="NCA SCC",
         title="NCA SCC cloud cybersecurity re-assessment",
         title_ar="إعادة تقييم الأمن السيبراني السحابي وفق ضوابط الأمن السيبراني للحوسبة السحابية",
         summary="Cloud cybersecurity re-assessment under NCA Cloud Cybersecurity Controls (Cybersecurity Supervisory Committee).",
         summary_ar="إعادة تقييم الأمن السيبراني للحوسبة السحابية وفق ضوابط الهيئة الوطنية عبر اللجنة الإشرافية للأمن السيبراني.",
         payload={"csp": "approved-local", "controlsAssessed": 96},
         by=ADMIN, rev=None),
    dict(wid="records-retention-schedule", mod="GOVERNANCE", status="APPROVED", anchor="NDMO / DGA",
         title="Records-retention schedule approval",
         title_ar="اعتماد جدول الاحتفاظ بالسجلات والوثائق",
         summary="Records-retention and disposal schedule approved per NDMO/DGA records-management requirements.",
         summary_ar="اعتماد جدول الاحتفاظ بالسجلات والتخلص منها وفق متطلبات إدارة الوثائق لمكتب إدارة البيانات وهيئة الحكومة الرقمية.",
         payload={"recordClasses": 24, "maxRetentionYears": 10},
         by=ADMIN, rev=ADMIN),
]


def seed_submissions(cur):
    for s in SUBMISSIONS:
        # idempotency: (organisation_id, workflow_id, title_en)
        cur.execute(
            "SELECT 1 FROM workflow.workflow_submissions WHERE organisation_id=? AND workflow_id=? AND title_en=?",
            ORG, s["wid"], s["title"])
        if cur.fetchone():
            continue
        reviewed_at = NOW - timedelta(days=2) if s["rev"] else None
        cur.execute(
            """INSERT INTO workflow.workflow_submissions
                   (organisation_id, workflow_id, workflow_module, title_en, title_ar,
                    status, summary_en, summary_ar, regulatory_anchor, payload_json,
                    submitted_by, submitted_at, reviewed_by, reviewed_at,
                    is_deleted, created_at, created_by, updated_by)
               VALUES (?,?,?,?,?,?,?,?,?,?,?, ?, ?, ?, 0, SYSUTCDATETIME(), ?, ?)""",
            ORG, s["wid"], s["mod"], s["title"], s["title_ar"], s["status"],
            s["summary"], s["summary_ar"], s["anchor"], j(s["payload"]),
            s["by"], NOW - timedelta(days=5), s["rev"], reviewed_at,
            s["by"], s["rev"] or s["by"])
        bump("workflow.workflow_submissions")
        print(f"  + submission  {s['wid']} [{s['status']}]")


# ===========================================================================
# F. approval_chain_template (+3) + approval_chain_step (+12)
# ===========================================================================
CHAIN_TEMPLATES = [
    dict(code="CHAIN-BOARD-PACK", module="GOVERNANCE",
         title="Board Pack — Secretariat → Exec Committee → President → Board Chair",
         title_ar="حزمة المجلس — الأمانة ← اللجنة التنفيذية ← رئيس الهيئة ← رئيس المجلس",
         desc="Approval ladder for the quarterly board pack endorsed by the President of the Authority.",
         desc_ar="سلسلة اعتماد حزمة المجلس الفصلية المعتمدة من رئيس الهيئة.",
         steps=[
             dict(o=1, t="Board Secretariat compile", t_ar="تجميع أمانة المجلس",
                  role="BOARD_SECRETARY", role_chain="BOARD_SECRETARY", esc="PLATFORM_ADMIN", sla=72),
             dict(o=2, t="Executive Committee review", t_ar="مراجعة اللجنة التنفيذية",
                  role="EXEC_COMMITTEE", role_chain="EXEC_COMMITTEE", esc="PRESIDENT", sla=120),
             dict(o=3, t="President endorsement", t_ar="اعتماد رئيس الهيئة",
                  role="PRESIDENT", role_chain="PRESIDENT", esc="BOARD_CHAIR", sla=72),
             dict(o=4, t="Board Chair approval", t_ar="موافقة رئيس المجلس",
                  role="BOARD_CHAIR", role_chain="BOARD_CHAIR", esc="BOARD_CHAIR", sla=168),
         ]),
    dict(code="CHAIN-RISK-ACCEPTANCE", module="RISK",
         title="Risk Acceptance — Risk Owner → Dept Head → CRO/Risk Committee → President",
         title_ar="قبول المخاطر — مالك المخاطر ← مدير الإدارة ← رئيس المخاطر/لجنة المخاطر ← رئيس الهيئة",
         desc="Approval ladder for accepting residual risk above appetite.",
         desc_ar="سلسلة اعتماد قبول المخاطر المتبقية التي تتجاوز حدود التقبل.",
         steps=[
             dict(o=1, t="Risk Owner request", t_ar="طلب مالك المخاطر",
                  role="RISK_OWNER", role_chain="RISK_OWNER", esc="DEPARTMENT_HEAD", sla=24),
             dict(o=2, t="Department Head review", t_ar="مراجعة مدير الإدارة",
                  role="DEPARTMENT_HEAD", role_chain="DEPARTMENT_HEAD", esc="CRO", sla=48),
             dict(o=3, t="CRO / Risk Committee endorsement", t_ar="اعتماد رئيس المخاطر/لجنة المخاطر",
                  role="CRO", role_chain="CRO", esc="PRESIDENT", sla=72),
             dict(o=4, t="President approval", t_ar="موافقة رئيس الهيئة",
                  role="PRESIDENT", role_chain="PRESIDENT", esc="PRESIDENT", sla=120),
         ]),
    dict(code="CHAIN-PROCUREMENT", module="PROCUREMENT",
         title="Procurement Award — Requesting Dept → Procurement → Finance → Exec Committee",
         title_ar="ترسية المشتريات — الإدارة الطالبة ← المشتريات ← المالية ← اللجنة التنفيذية",
         desc="Approval ladder for awarding a procurement contract.",
         desc_ar="سلسلة اعتماد ترسية عقد المشتريات.",
         steps=[
             dict(o=1, t="Requesting Department justification", t_ar="مبررات الإدارة الطالبة",
                  role="DEPARTMENT_HEAD", role_chain="DEPARTMENT_HEAD", esc="PROCUREMENT_LEAD", sla=48),
             dict(o=2, t="Procurement evaluation", t_ar="تقييم المشتريات",
                  role="PROCUREMENT_LEAD", role_chain="PROCUREMENT_LEAD", esc="FINANCE_DIRECTOR", sla=72),
             dict(o=3, t="Finance budget confirmation", t_ar="تأكيد المالية للميزانية",
                  role="FINANCE_DIRECTOR", role_chain="FINANCE_DIRECTOR", esc="EXEC_COMMITTEE", sla=72),
             dict(o=4, t="Executive Committee award", t_ar="ترسية اللجنة التنفيذية",
                  role="EXEC_COMMITTEE", role_chain="EXEC_COMMITTEE", esc="PRESIDENT", sla=120),
         ]),
]


def seed_chain_templates(cur) -> dict[str, int]:
    tmpl_ids: dict[str, int] = {}
    for t in CHAIN_TEMPLATES:
        cur.execute(
            "SELECT id FROM workflow.approval_chain_template WHERE organisation_id=? AND template_code=?",
            ORG, t["code"])
        row = cur.fetchone()
        if row:
            tmpl_ids[t["code"]] = row[0]
        else:
            cur.execute(
                """INSERT INTO workflow.approval_chain_template
                       (organisation_id, template_code, title_en, title_ar,
                        description_en, description_ar, applies_to_module, is_default,
                        created_by_user_id, created_at, created_by, updated_by, is_deleted)
                   OUTPUT INSERTED.id
                   VALUES (?,?,?,?,?,?,?,0,?, SYSUTCDATETIME(), ?, ?, 0)""",
                ORG, t["code"], t["title"], t["title_ar"], t["desc"], t["desc_ar"],
                t["module"], ADMIN, ADMIN, ADMIN)
            tid = out_id(cur)
            tmpl_ids[t["code"]] = tid
            bump("workflow.approval_chain_template")
            print(f"  + chain template  {t['code']}")
        # steps
        tid = tmpl_ids[t["code"]]
        for st in t["steps"]:
            cur.execute(
                "SELECT 1 FROM workflow.approval_chain_step WHERE template_id=? AND step_order=? AND title_en=?",
                tid, st["o"], st["t"])
            if cur.fetchone():
                continue
            cur.execute(
                """INSERT INTO workflow.approval_chain_step
                       (organisation_id, template_id, step_order, step_type,
                        title_en, title_ar, required_role, required_role_in_chain,
                        is_mandatory, can_delegate, sla_hours, escalation_role,
                        created_by_user_id, created_at, created_by, updated_by, is_deleted)
                   VALUES (?,?,?, 'SERIAL', ?,?,?,?, 1, 1, ?,?, ?, SYSUTCDATETIME(), ?, ?, 0)""",
                ORG, tid, st["o"], st["t"], st["t_ar"], st["role"], st["role_chain"],
                st["sla"], st["esc"], ADMIN, ADMIN, ADMIN)
            bump("workflow.approval_chain_step")
    print(f"  chain templates: {counts.get('workflow.approval_chain_template',0)}, "
          f"steps: {counts.get('workflow.approval_chain_step',0)}")
    return tmpl_ids


# ===========================================================================
# G. approval_chain_instance (fill: 6) + approval_chain_action (fill: ~12)
# ===========================================================================
def chain_template_id(cur, code: str):
    cur.execute(
        "SELECT id FROM workflow.approval_chain_template WHERE organisation_id=? AND template_code=?",
        ORG, code)
    r = cur.fetchone()
    return r[0] if r else None


def chain_step_id(cur, template_id: int, step_order: int):
    cur.execute(
        "SELECT id FROM workflow.approval_chain_step WHERE template_id=? AND step_order=? AND is_deleted=0",
        template_id, step_order)
    r = cur.fetchone()
    return r[0] if r else None


def submission_id(cur, wid: str, title: str):
    cur.execute(
        "SELECT id FROM workflow.workflow_submissions WHERE organisation_id=? AND workflow_id=? AND title_en=?",
        ORG, wid, title)
    r = cur.fetchone()
    return r[0] if r else None


# Each chain instance + the cleared rungs (actions). action per cleared step.
CHAIN_INSTANCES = [
    dict(code="CHAIN-BOARD-PACK", status="IN_PROGRESS", step=2, by=ADMIN,
         title="Board Pack Q2-2026 approval ladder",
         title_ar="سلسلة اعتماد حزمة المجلس للربع الثاني 2026",
         link=("governance-board-pack", None),  # resolved below; fallback no-link
         actions=[dict(o=1, actor=ADMIN, act="APPROVED",
                       c="Board pack compiled and quality-checked.",
                       c_ar="تم تجميع حزمة المجلس ومراجعة جودتها.")]),
    dict(code="CHAIN-RISK-ACCEPTANCE", status="IN_PROGRESS", step=3, by=RMANAGER,
         title="Risk acceptance ladder — residual above appetite",
         title_ar="سلسلة قبول المخاطر — متبقٍ يتجاوز حدود التقبل",
         link=(None, None),
         actions=[dict(o=1, actor=RMANAGER, act="APPROVED",
                       c="Risk owner submitted acceptance request.",
                       c_ar="قدّم مالك المخاطر طلب القبول."),
                  dict(o=2, actor=RMANAGER, act="APPROVED",
                       c="Department head reviewed and endorsed.",
                       c_ar="راجع مدير الإدارة الطلب واعتمده.")]),
    dict(code="CHAIN-CROSS-BORDER-XFER", status="IN_PROGRESS", step=2, by=COMPLIANCE,
         title="Cross-border transfer approval ladder",
         title_ar="سلسلة اعتماد النقل خارج المملكة",
         link=(None, None),
         actions=[dict(o=1, actor=COMPLIANCE, act="APPROVED",
                       c="DPO approved adequacy of safeguards.",
                       c_ar="وافق مسؤول حماية البيانات على كفاية الضمانات.")]),
    dict(code="CHAIN-CAPA-CLOSURE", status="APPROVED", step=4, by=COMPLIANCE,
         title="CAPA closure approval ladder",
         title_ar="سلسلة اعتماد إغلاق الإجراء التصحيحي",
         link=(None, None), completed=2,
         actions=[dict(o=1, actor=COMPLIANCE, act="APPROVED", c="Risk owner sign-off.", c_ar="اعتماد مالك المخاطر."),
                  dict(o=2, actor=RMANAGER, act="APPROVED", c="Department head review.", c_ar="مراجعة مدير الإدارة."),
                  dict(o=3, actor=COMPLIANCE, act="APPROVED", c="DPO attestation.", c_ar="إقرار مسؤول حماية البيانات."),
                  dict(o=4, actor=ADMIN, act="APPROVED", c="CISO final approval.", c_ar="الاعتماد النهائي من رئيس الأمن السيبراني.")]),
    dict(code="CHAIN-PROCUREMENT", status="PENDING", step=1, by=RMANAGER,
         title="Procurement award approval ladder — core hosting contract",
         title_ar="سلسلة اعتماد ترسية المشتريات — عقد الاستضافة الأساسي",
         link=(None, None),
         actions=[]),  # nothing cleared yet
    dict(code="CHAIN-MATERIAL-CHANGE", status="REJECTED", step=2, by=ADMIN,
         title="Material change approval ladder — production change",
         title_ar="سلسلة اعتماد التغيير الجوهري — تغيير على بيئة الإنتاج",
         link=(None, None), completed=1,
         actions=[dict(o=1, actor=ADMIN, act="APPROVED", c="Originator submitted change.", c_ar="قدّم صاحب الطلب التغيير."),
                  dict(o=2, actor=ADMIN, act="REJECTED",
                       c="Rejected — insufficient rollback plan.",
                       c_ar="رُفض — خطة التراجع غير كافية.")]),
]


def seed_chain_runtime(cur):
    for ci in CHAIN_INSTANCES:
        tid = chain_template_id(cur, ci["code"])
        if tid is None:
            blockers.append(f"approval_chain_instance: template {ci['code']} not found")
            continue
        # optional link to a workflow_submission
        link_sub = None
        wid, title = ci.get("link", (None, None))
        if wid == "governance-board-pack":
            # No such submission seeded here; leave NULL (entity-less link ok)
            link_sub = None
        # idempotency: (organisation_id, template_id, title_en)
        cur.execute(
            "SELECT id FROM workflow.approval_chain_instance WHERE organisation_id=? AND template_id=? AND title_en=?",
            ORG, tid, ci["title"])
        row = cur.fetchone()
        if row:
            iid = row[0]
        else:
            started = NOW - timedelta(days=10)
            completed = (NOW - timedelta(days=ci["completed"])) if ci.get("completed") is not None else None
            cur.execute(
                """INSERT INTO workflow.approval_chain_instance
                       (organisation_id, template_id, source_workflow_submission_id,
                        status, started_at, completed_at, current_step_order,
                        originator_user_id, title_en, title_ar,
                        created_by_user_id, created_at, created_by, updated_by, is_deleted)
                   OUTPUT INSERTED.id
                   VALUES (?,?,?,?,?,?,?,?,?,?,?, SYSUTCDATETIME(), ?, ?, 0)""",
                ORG, tid, link_sub, ci["status"], started, completed, ci["step"],
                ci["by"], ci["title"], ci["title_ar"], ci["by"], ci["by"], ci["by"])
            iid = out_id(cur)
            bump("workflow.approval_chain_instance")
            print(f"  + chain instance  {ci['code']} [{ci['status']}]")
        # actions for cleared rungs
        for idx, a in enumerate(ci["actions"]):
            sid = chain_step_id(cur, tid, a["o"])
            if sid is None:
                blockers.append(f"approval_chain_action: step {a['o']} of {ci['code']} not found")
                continue
            # idempotency: (instance_id, step_order, actor_user_id)
            cur.execute(
                "SELECT 1 FROM workflow.approval_chain_action WHERE instance_id=? AND step_order=? AND actor_user_id=?",
                iid, a["o"], a["actor"])
            if cur.fetchone():
                continue
            decided_at = NOW - timedelta(days=9 - idx)
            cur.execute(
                """INSERT INTO workflow.approval_chain_action
                       (organisation_id, instance_id, step_order, step_id, actor_user_id,
                        action, decided_at, comment_en, comment_ar,
                        created_by_user_id, created_at, created_by, updated_by, is_deleted)
                   VALUES (?,?,?,?,?,?,?,?,?, ?, SYSUTCDATETIME(), ?, ?, 0)""",
                ORG, iid, a["o"], sid, a["actor"], a["act"], decided_at,
                a["c"], a["c_ar"], a["actor"], a["actor"], a["actor"])
            bump("workflow.approval_chain_action")
    print(f"  chain instances: {counts.get('workflow.approval_chain_instance',0)}, "
          f"actions: {counts.get('workflow.approval_chain_action',0)}")


# ===========================================================================
# main
# ===========================================================================
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("== reporting.report_schedules (deepen) ==")
    sched_ids = seed_schedules(cur)
    print("== reporting.report_runs (deepen) ==")
    seed_runs(cur, sched_ids)
    print("== workflow.instances + action_log (FSM, cross-module) ==")
    seed_fsm(cur)
    print("== workflow.workflow_instances + workflow_tasks (PRIME fill) ==")
    seed_wf_runtime(cur)
    print("== workflow.workflow_submissions (deepen) ==")
    seed_submissions(cur)
    print("== approval_chain_template + approval_chain_step (deepen) ==")
    seed_chain_templates(cur)
    print("== approval_chain_instance + approval_chain_action (PRIME fill) ==")
    seed_chain_runtime(cur)

    skips.extend([
        "reporting.report_definitions (entity-less; already rich 20-row catalog)",
        "reporting.report_executions (entity-less; no backend reader)",
        "workflow.definitions (FSM defs already complete)",
        "workflow.states (scoped by definition; no new defs)",
        "workflow.transitions (scoped by definition; no new defs)",
        "workflow.workflow_definitions (20-row SLA catalog already complete)",
    ])

    total = sum(counts.values())
    print("\n" + "=" * 60)
    print("INSERTED THIS RUN:")
    for tbl in sorted(counts):
        print(f"  {tbl:<42} {counts[tbl]}")
    print(f"  {'TOTAL':<42} {total}")
    print("\nSKIPPED:")
    for s in skips:
        print(f"  - {s}")
    if blockers:
        print("\nBLOCKERS:")
        for b in blockers:
            print(f"  ! {b}")

    # ── Final verification: live counts for org 1 ────────────────────────────
    print("\nVERIFICATION (org 1 counts):")
    verif = [
        ("reporting.report_schedules", "organisation_id=1 AND is_deleted=0"),
        ("reporting.report_runs", "organisation_id=1"),
        ("workflow.instances", "organisation_id=1"),
        ("workflow.action_log", "instance_id IN (SELECT id FROM workflow.instances WHERE organisation_id=1)"),
        ("workflow.workflow_instances", "organisation_id=1"),
        ("workflow.workflow_tasks", "instance_id IN (SELECT id FROM workflow.workflow_instances WHERE organisation_id=1)"),
        ("workflow.workflow_submissions", "organisation_id=1 AND is_deleted=0"),
        ("workflow.approval_chain_template", "organisation_id=1 AND is_deleted=0"),
        ("workflow.approval_chain_step", "organisation_id=1 AND is_deleted=0"),
        ("workflow.approval_chain_instance", "organisation_id=1 AND is_deleted=0"),
        ("workflow.approval_chain_action", "organisation_id=1 AND is_deleted=0"),
    ]
    for tbl, where in verif:
        cur.execute(f"SELECT COUNT(*) FROM {tbl} WHERE {where}")
        print(f"  {tbl:<42} {cur.fetchone()[0]}")
    conn.close()


if __name__ == "__main__":
    main()
