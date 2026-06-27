# -*- coding: utf-8 -*-
"""Seed committee activity — meetings, resolutions and action items.

Companion to seed_gov_committees.py. Brings the committee calendar, board-pack,
resolutions register and action tracker to life for the seeded Saudi-government
committees (organisation_id = 1).

For every ACTIVE committee that has no meetings yet, it creates:
  • one past meeting   (status MINUTES_APPROVED) — agenda, attendees, minutes,
                         quorum met, approver;
  • one upcoming meeting (status SCHEDULED) — agenda + invitees;
  • one resolution      (APPROVED) tied to the past meeting;
  • two action items    (mix of IN_PROGRESS / OPEN / COMPLETED), some linked to
                         other modules (RISK, CYBER, PRIVACY, BCM, …).

Committees that already carry demo activity (Board, Risk, Audit, BCM — the
MTG-DEMO-* set) are left untouched. The dissolved legacy committee is skipped.

Idempotent: meetings/resolutions/actions are guarded by their unique codes
(MTG-/RES-/ACT-{stem}-26Qn), so re-running never duplicates.

    Usage:  python seed_gov_committee_activity.py   (run after the committees seed)
"""
from __future__ import annotations

import datetime as dt
import json
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
        except Exception as exc:  # noqa: BLE001
            last = exc
    raise SystemExit(f"Could not connect to SQL Server: {last}")


ORG = 1

# Per-committee meeting subject + resolution + actions. Keyed by committee code.
# action: (title_en, title_ar, owner, priority, status, linked_module, linked_code)
TOPICS: dict[str, dict] = {
    "COM-COMPLIANCE": dict(
        conf="INTERNAL", notify=0,
        res_en="Approve the 2026 Regulatory Compliance Plan",
        res_ar="اعتماد خطة الالتزام التنظيمي لعام 2026",
        body_en="The committee approved the annual compliance plan, including the obligations-register refresh and the PDPL readiness programme.",
        body_ar="اعتمدت اللجنة خطة الالتزام السنوية بما في ذلك تحديث سجل الالتزامات وبرنامج الجاهزية لنظام حماية البيانات الشخصية.",
        actions=[
            ("Close open PDPL gap-assessment findings", "إغلاق ملاحظات تقييم الفجوة لنظام حماية البيانات",
             "Director of Compliance", "HIGH", "IN_PROGRESS", "COMPLIANCE", "PDPL-GAP-2026"),
            ("Refresh the regulatory obligations register", "تحديث سجل الالتزامات التنظيمية",
             "Compliance Officer", "NORMAL", "COMPLETED", "COMPLIANCE", None),
        ]),
    "COM-NOMREM": dict(
        conf="CONFIDENTIAL", notify=0,
        res_en="Approve the 2026 Executive Succession Plan",
        res_ar="اعتماد خطة التعاقب للتنفيذيين لعام 2026",
        body_en="The committee endorsed the succession plan for key executive roles and the updated board skills matrix.",
        body_ar="أقرّت اللجنة خطة التعاقب للمناصب التنفيذية الرئيسية ومصفوفة مهارات المجلس المحدّثة.",
        actions=[
            ("Finalize the board skills matrix", "استكمال مصفوفة مهارات المجلس",
             "Director of Human Resources", "NORMAL", "IN_PROGRESS", None, None),
            ("Benchmark the remuneration policy", "مقارنة سياسة المكافآت مرجعياً",
             "HR Business Partner", "NORMAL", "OPEN", None, None),
        ]),
    "COM-ESG": dict(
        conf="INTERNAL", notify=0,
        res_en="Adopt the 2026 Sustainability Disclosure Roadmap",
        res_ar="اعتماد خارطة طريق إفصاح الاستدامة لعام 2026",
        body_en="The committee adopted the ESG disclosure roadmap aligned with Vision 2030 and TCFD recommendations.",
        body_ar="اعتمدت اللجنة خارطة طريق الإفصاح عن الاستدامة بما يتوافق مع رؤية 2030 وتوصيات فريق العمل المعني بالإفصاحات المالية المتعلقة بالمناخ.",
        actions=[
            ("Baseline Scope 1 & 2 emissions", "حساب خط الأساس للانبعاثات (النطاقان 1 و2)",
             "Director of Strategy & Performance", "HIGH", "IN_PROGRESS", None, None),
            ("Publish the ESG materiality assessment", "نشر تقييم الأهمية النسبية للاستدامة",
             "Sustainability Officer", "NORMAL", "OPEN", None, None),
        ]),
    "COM-CYBER": dict(
        conf="CONFIDENTIAL", notify=0,
        res_en="Approve the NCA ECC Remediation Plan",
        res_ar="اعتماد خطة معالجة الضوابط الأساسية للأمن السيبراني",
        body_en="The committee approved the ECC remediation plan and prioritised closure of high-severity findings.",
        body_ar="اعتمدت اللجنة خطة معالجة الضوابط الأساسية وأعطت الأولوية لإغلاق الملاحظات عالية الخطورة.",
        actions=[
            ("Remediate high-severity SOC findings", "معالجة ملاحظات مركز العمليات الأمنية عالية الخطورة",
             "Chief Information Security Officer", "CRITICAL", "IN_PROGRESS", "CYBER", "ECC-REM-014"),
            ("Run an organisation-wide phishing awareness campaign", "تنفيذ حملة توعية ضد التصيّد على مستوى الهيئة",
             "Director, Cyber Risk & Compliance", "HIGH", "OPEN", "CYBER", None),
        ]),
    "COM-INTCTRL": dict(
        conf="INTERNAL", notify=0,
        res_en="Approve the Internal Control Testing Plan 2026",
        res_ar="اعتماد خطة اختبار الرقابة الداخلية لعام 2026",
        body_en="The committee approved the annual control-testing plan and the remediation timelines.",
        body_ar="اعتمدت اللجنة خطة اختبار الضوابط السنوية والجداول الزمنية للمعالجة.",
        actions=[
            ("Remediate control deficiencies in Finance", "معالجة أوجه القصور الرقابية في الإدارة المالية",
             "Director of Finance", "HIGH", "IN_PROGRESS", "AUDIT", "ICR-2026-007"),
            ("Update the enterprise control library", "تحديث مكتبة الضوابط المؤسسية",
             "Director of Governance & Policy", "NORMAL", "OPEN", "COMPLIANCE", None),
        ]),
    "COM-ANTIFRAUD": dict(
        conf="CONFIDENTIAL", notify=0,
        res_en="Adopt the 2026 Fraud Risk Assessment",
        res_ar="اعتماد تقييم مخاطر الاحتيال لعام 2026",
        body_en="The committee adopted the fraud risk assessment and strengthened the whistleblowing programme.",
        body_ar="اعتمدت اللجنة تقييم مخاطر الاحتيال وعزّزت برنامج الإبلاغ عن المخالفات.",
        actions=[
            ("Investigate flagged anomalous transactions", "التحقيق في المعاملات غير الاعتيادية",
             "Head of Internal Audit", "HIGH", "IN_PROGRESS", "INCIDENT", "FRD-2026-003"),
            ("Roll out whistleblowing awareness", "نشر التوعية بقنوات الإبلاغ",
             "Director of Human Resources", "NORMAL", "OPEN", None, None),
        ]),
    "COM-EXEC": dict(
        conf="INTERNAL", notify=0,
        res_en="Approve Q3 2026 Operational Priorities",
        res_ar="اعتماد الأولويات التشغيلية للربع الثالث 2026",
        body_en="The committee approved the cross-deputyship operational priorities and budget reallocations for Q3.",
        body_ar="اعتمدت اللجنة الأولويات التشغيلية بين الوكالات وإعادة توزيع الميزانية للربع الثالث.",
        actions=[
            ("Deliver digital-transformation Q3 milestones", "تحقيق مستهدفات التحول الرقمي للربع الثالث",
             "Deputy for Digital Transformation & Data", "HIGH", "IN_PROGRESS", None, None),
            ("Report strategic KPI performance", "رفع تقرير أداء مؤشرات الأداء الاستراتيجية",
             "Director of Strategy & Performance", "NORMAL", "OPEN", None, None),
        ]),
    "COM-IT": dict(
        conf="INTERNAL", notify=0,
        res_en="Approve the 2026 IT & Digital Portfolio",
        res_ar="اعتماد محفظة تقنية المعلومات والتحول الرقمي لعام 2026",
        body_en="The committee approved the prioritised IT/digital portfolio and the government-cloud migration plan.",
        body_ar="اعتمدت اللجنة محفظة مشاريع التقنية والتحول الرقمي وخطة الانتقال إلى الحوسبة السحابية الحكومية.",
        actions=[
            ("Migrate core systems to the government cloud", "ترحيل الأنظمة الأساسية إلى السحابة الحكومية",
             "Director of Information Technology", "HIGH", "IN_PROGRESS", "CYBER", "CLOUD-MIG-2026"),
            ("Decommission legacy applications", "إيقاف التطبيقات القديمة",
             "Director of Digital Transformation", "NORMAL", "OPEN", None, None),
        ]),
    "COM-DATAGOV": dict(
        conf="INTERNAL", notify=0,
        res_en="Approve the NDMO Data Classification Policy",
        res_ar="اعتماد سياسة تصنيف البيانات وفق مكتب إدارة البيانات الوطنية",
        body_en="The committee approved the data-classification policy and the data-asset inventory programme per NDMO standards.",
        body_ar="اعتمدت اللجنة سياسة تصنيف البيانات وبرنامج حصر أصول البيانات وفق معايير مكتب إدارة البيانات الوطنية.",
        actions=[
            ("Complete the enterprise data-asset inventory", "استكمال حصر أصول البيانات المؤسسية",
             "Director, Data Management Office", "HIGH", "IN_PROGRESS", "PRIVACY", "NDMO-INV-2026"),
            ("Remediate priority data-quality issues", "معالجة مشكلات جودة البيانات ذات الأولوية",
             "Chief Data Officer", "NORMAL", "OPEN", None, None),
        ]),
    "COM-CRISIS": dict(
        conf="CONFIDENTIAL", notify=0,
        res_en="Approve the Crisis Management Framework",
        res_ar="اعتماد إطار إدارة الأزمات",
        body_en="The committee approved the crisis-management framework and the annual simulation-exercise schedule (NCEMA-aligned).",
        body_ar="اعتمدت اللجنة إطار إدارة الأزمات وجدول تمارين المحاكاة السنوية بما يتوافق مع المركز الوطني لإدارة الكوارث.",
        actions=[
            ("Conduct an enterprise crisis-simulation exercise", "تنفيذ تمرين محاكاة أزمة على مستوى الهيئة",
             "Director of Business Continuity", "HIGH", "OPEN", "BCM", "CRX-2026-01"),
            ("Update the emergency contact & escalation tree", "تحديث شجرة جهات الاتصال والتصعيد للطوارئ",
             "Deputy for Operations & Services", "NORMAL", "COMPLETED", "BCM", None),
        ]),
    "COM-PRIVACY": dict(
        conf="CONFIDENTIAL", notify=0,
        res_en="Approve the PDPL Compliance Programme",
        res_ar="اعتماد برنامج الالتزام بنظام حماية البيانات الشخصية",
        body_en="The committee approved the PDPL programme covering ROPA completion, DSR handling and breach response.",
        body_ar="اعتمدت اللجنة برنامج الالتزام بالنظام شاملاً استكمال سجل المعالجة وإدارة طلبات أصحاب البيانات والاستجابة للانتهاكات.",
        actions=[
            ("Complete ROPA across all processing activities", "استكمال سجل أنشطة المعالجة لكافة العمليات",
             "Data Protection Officer (DPO)", "HIGH", "IN_PROGRESS", "PRIVACY", "ROPA-2026"),
            ("Implement the data-subject request workflow", "تطبيق مسار طلبات أصحاب البيانات",
             "Director of Privacy Office", "NORMAL", "OPEN", "PRIVACY", None),
        ]),
    "COM-AIETHICS": dict(
        conf="INTERNAL", notify=0,
        res_en="Adopt AI Ethics Review Guidelines",
        res_ar="اعتماد إرشادات مراجعة أخلاقيات الذكاء الاصطناعي",
        body_en="The committee adopted AI-ethics review guidelines aligned with SDAIA AI principles and approved the model-risk register.",
        body_ar="اعتمدت اللجنة إرشادات مراجعة أخلاقيات الذكاء الاصطناعي بما يتوافق مع مبادئ سدايا وأقرّت سجل مخاطر النماذج.",
        actions=[
            ("Review high-risk AI use-cases", "مراجعة حالات استخدام الذكاء الاصطناعي عالية المخاطر",
             "Director, Data & AI", "HIGH", "IN_PROGRESS", None, None),
            ("Establish the model-risk register", "إنشاء سجل مخاطر النماذج",
             "AI Governance Lead", "NORMAL", "OPEN", None, None),
        ]),
}


def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    cur.execute(
        """SELECT id, code, name_en, name_ar, members_json, chairperson_name, secretary_name
             FROM admin.committees
            WHERE organisation_id=? AND is_deleted=0 AND status='ACTIVE'
            ORDER BY id""",
        ORG,
    )
    committees = cur.fetchall()

    def has_meeting(committee_id: int) -> bool:
        cur.execute(
            "SELECT 1 FROM admin.committee_meetings WHERE organisation_id=? AND committee_id=?",
            ORG, committee_id,
        )
        return cur.fetchone() is not None

    def meeting_id_by_code(code: str):
        cur.execute(
            "SELECT id FROM admin.committee_meetings WHERE organisation_id=? AND meeting_code=?",
            ORG, code,
        )
        r = cur.fetchone()
        return r[0] if r else None

    def exists(table: str, col: str, code: str) -> bool:
        cur.execute(
            f"SELECT 1 FROM admin.{table} WHERE organisation_id=? AND {col}=?", ORG, code
        )
        return cur.fetchone() is not None

    m_ins = r_ins = a_ins = 0

    for cid, code, name_en, name_ar, members_json, chair, secretary in committees:
        if has_meeting(cid):
            continue  # already has demo activity — leave it alone
        topic = TOPICS.get(code)
        if not topic:
            continue  # only the gov committees we authored content for

        stem = code[4:] if code.startswith("COM-") else code
        try:
            mbrs = json.loads(members_json) if members_json else []
        except Exception:  # noqa: BLE001
            mbrs = []
        voting = [m for m in mbrs if m.get("votingMember")] or mbrs
        vote_for = max(len(voting), 3)
        seconder = next((m["name"] for m in mbrs if m.get("name") != chair), "Committee Member")
        attendees = json.dumps(
            [{"name": m.get("name"), "role": m.get("role"), "present": True,
              "votingMember": bool(m.get("votingMember"))} for m in mbrs],
            ensure_ascii=False,
        )
        invitees = json.dumps(
            [{"name": m.get("name"), "role": m.get("role"), "present": None,
              "votingMember": bool(m.get("votingMember"))} for m in mbrs],
            ensure_ascii=False,
        )
        agenda = json.dumps([
            {"seq": 1, "titleEn": "Approval of previous minutes", "titleAr": "اعتماد محضر الاجتماع السابق"},
            {"seq": 2, "titleEn": topic["res_en"], "titleAr": topic["res_ar"]},
            {"seq": 3, "titleEn": "Matters arising & any other business", "titleAr": "ما يستجد من أعمال"},
        ], ensure_ascii=False)
        decisions = json.dumps(
            [{"seq": 1, "summaryEn": topic["res_en"], "summaryAr": topic["res_ar"], "outcome": "APPROVED"}],
            ensure_ascii=False,
        )

        # idx spreads the dates so the calendar isn't all on one day
        idx = cid % 18
        past_day = dt.date(2026, 5, 5) + dt.timedelta(days=idx)
        up_day = dt.date(2026, 8, 4) + dt.timedelta(days=idx)
        past_start = dt.datetime.combine(past_day, dt.time(10, 0))
        past_end = dt.datetime.combine(past_day, dt.time(12, 0))
        approved_at = dt.datetime.combine(past_day + dt.timedelta(days=5), dt.time(9, 0))
        up_start = dt.datetime.combine(up_day, dt.time(10, 0))

        # ── past meeting (MINUTES_APPROVED) ──────────────────────────────────
        past_code = f"MTG-{stem}-26Q2"
        if not exists("committee_meetings", "meeting_code", past_code):
            cur.execute(
                """INSERT INTO admin.committee_meetings
                       (organisation_id, committee_id, committee_code, committee_name_en,
                        meeting_code, title_en, title_ar, scheduled_at, started_at, ended_at,
                        location_or_link, agenda_json, attendees_json, quorum_met,
                        minutes_en, minutes_ar, decisions_json, chair_name, secretary_name,
                        status, minutes_approved_at, approver_name, external_id,
                        created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?, ?, ?, ?,
                           'MINUTES_APPROVED', ?, ?, ?, 1, 1)""",
                ORG, cid, code, name_en, past_code,
                f"{name_en} — Q2 2026 Meeting",
                f"اجتماع {name_ar} — الربع الثاني 2026",
                past_start, past_start, past_end,
                "HQ Boardroom / Microsoft Teams", agenda, attendees,
                f"The committee convened, achieved quorum and approved the agenda. {topic['body_en']}",
                f"انعقدت اللجنة واكتمل النصاب واعتُمد جدول الأعمال. {topic['body_ar']}",
                decisions, chair, secretary, approved_at, chair, f"EXT-{past_code}",
            )
            m_ins += 1
        past_id = meeting_id_by_code(past_code)

        # ── upcoming meeting (SCHEDULED) ─────────────────────────────────────
        up_code = f"MTG-{stem}-26Q3"
        if not exists("committee_meetings", "meeting_code", up_code):
            cur.execute(
                """INSERT INTO admin.committee_meetings
                       (organisation_id, committee_id, committee_code, committee_name_en,
                        meeting_code, title_en, title_ar, scheduled_at, location_or_link,
                        agenda_json, attendees_json, chair_name, secretary_name, status,
                        external_id, created_by, updated_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SCHEDULED', ?, 1, 1)""",
                ORG, cid, code, name_en, up_code,
                f"{name_en} — Q3 2026 Meeting",
                f"اجتماع {name_ar} — الربع الثالث 2026",
                up_start, "HQ Boardroom / Microsoft Teams", agenda, invitees,
                chair, secretary, f"EXT-{up_code}",
            )
            m_ins += 1

        # ── resolution (tied to the past meeting) ────────────────────────────
        res_code = f"RES-{stem}-26Q2-01"
        if past_id and not exists("committee_resolutions", "resolution_code", res_code):
            cur.execute(
                """INSERT INTO admin.committee_resolutions
                       (organisation_id, meeting_id, committee_id, resolution_code, seq,
                        agenda_item_seq, title_en, title_ar, body_en, body_ar,
                        proposed_by, seconded_by, vote_for, vote_against, vote_abstain,
                        outcome, confidentiality, effective_from, review_required_by, regulator_notify)
                   VALUES (?, ?, ?, ?, 1, 2, ?, ?, ?, ?, ?, ?, ?, 0, 0,
                           'APPROVED', ?, ?, ?, ?)""",
                ORG, past_id, cid, res_code, topic["res_en"], topic["res_ar"],
                topic["body_en"], topic["body_ar"], chair, seconder, vote_for,
                topic["conf"], past_day, past_day + dt.timedelta(days=365), topic["notify"],
            )
            r_ins += 1

        # ── action items (tied to the past meeting) ──────────────────────────
        for n, (te, ta, owner, prio, status, mod, lcode) in enumerate(topic["actions"], start=1):
            act_code = f"ACT-{stem}-26Q2-{n:02d}"
            if not past_id or exists("committee_action_items", "action_code", act_code):
                continue
            due = past_day + dt.timedelta(days=40 + n * 15)
            done_at = dt.datetime.combine(past_day + dt.timedelta(days=18), dt.time(14, 0)) \
                if status == "COMPLETED" else None
            cur.execute(
                """INSERT INTO admin.committee_action_items
                       (organisation_id, meeting_id, committee_id, action_code, seq,
                        agenda_item_seq, title_en, title_ar, owner_name, owner_role,
                        due_date, priority, status, completion_notes_en, completed_at,
                        completed_by, linked_module, linked_entity_code)
                   VALUES (?, ?, ?, ?, ?, 2, ?, ?, ?, 'Action owner', ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, past_id, cid, act_code, n, te, ta, owner, due, prio, status,
                ("Closed and verified by the secretariat." if status == "COMPLETED" else None),
                done_at, (1 if status == "COMPLETED" else None), mod, lcode,
            )
            a_ins += 1

    print(f"Done. meetings +{m_ins}, resolutions +{r_ins}, action items +{a_ins}\n")

    # ── Verification ─────────────────────────────────────────────────────────
    print("Activity per committee (org 1):\n")
    cur.execute(
        """SELECT c.name_en,
                  (SELECT COUNT(*) FROM admin.committee_meetings m WHERE m.committee_id=c.id AND m.is_deleted=0) mtg,
                  (SELECT COUNT(*) FROM admin.committee_resolutions r WHERE r.committee_id=c.id) res,
                  (SELECT COUNT(*) FROM admin.committee_action_items a WHERE a.committee_id=c.id) act
             FROM admin.committees c
            WHERE c.organisation_id=? AND c.is_deleted=0 AND c.status='ACTIVE'
            ORDER BY c.id""",
        ORG,
    )
    for name_en, mtg, res, act in cur.fetchall():
        print(f"  {name_en:<42} meetings={mtg}  resolutions={res}  actions={act}")

    for table in ("committee_meetings", "committee_resolutions", "committee_action_items"):
        cur.execute(f"SELECT COUNT(*) FROM admin.{table} WHERE organisation_id=?", ORG)
        print(f"\n  total admin.{table}: {cur.fetchone()[0]}")
    conn.close()


if __name__ == "__main__":
    main()
