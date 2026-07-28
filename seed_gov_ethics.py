# -*- coding: utf-8 -*-
"""Seed a Nazaha-aligned PUBLIC-SECTOR INTEGRITY layer into the ethics / fraud schemas.

Companion to seed_gov_committees.py and seed_gov_hierarchy.py. Targets the default
tenant (organisation_id = 1) -- a Saudi GENERAL AUTHORITY (هيئة عامة) headed by a
President. All pre-existing data in these schemas is BANK-archetype (SADAD, ATM
skimming, SWIFT, SIMAH, money-mules, sanctions screening). This script does NOT
touch that data; it ADDS a public-authority integrity layer that a regulator/
authority actually faces:

  * Whistleblowing register (بلاغات المخالفات) -- bribery, tender-rigging,
    embezzlement of public funds, conflict-of-interest, falsified inspections,
    misuse of authority assets -- tied to the Integrity & Anti-Fraud Committee.
  * Whistleblower-protection (retaliation) complaints (حماية المُبلِّغ).
  * Conflict-of-interest declaration programme (EXEC + high-risk functions) with
    disclosed items and recusal events (محاضر تنحٍّ).
  * Legacy ethics integrity cases + investigation actions + polymorphic case-links
    that are the MECHANISM for tying cases to COM-ANTIFRAUD (no committee FK exists).
  * Public-sector gifts/hospitality/outside-activity disclosures (Nazaha gift rules).
  * Public-sector fraud typologies (procurement / public-funds) + fraud cases +
    case timelines (bid-rigging, ghost employees, grant fraud, expense fraud,
    asset misappropriation), referrals styled for Nazaha / Public Prosecution.

DELIBERATELY SKIPPED (domain-incorrect for a public authority -- these are
financial-institution AML/sanctions controls, already coherently seeded for the
bank archetype): ethics.aml_alerts, ethics.screening_hits, aml.sanctions_list,
aml.sanctions_list_entry, aml.screening_check, aml.screening_hit.

Idempotent: every INSERT is guarded by a natural key (existence check); a second
run inserts 0. We never modify pre-existing rows other than our own re-write of
rows this script inserted (UPDATE-on-match keeps content current without dupes).
Arabic is always passed as Unicode parameters, never string-formatted into SQL.

Hard safety: INSERT / idempotent-UPSERT ONLY. No DELETE/TRUNCATE/DROP/ALTER.
organisation_id = 1 only. Only the ethics + fraud schemas are written.

    Usage:  python seed_gov_ethics.py
"""
from __future__ import annotations

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

# Core FK anchors (verified present, org 1).
U_ADMIN, U_RMGR, U_COMPLIANCE, U_AUDITOR, U_RANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6
COM_ANTIFRAUD, COM_AUDIT, COM_INTCTRL = 11, 3, 10
DEPT = dict(PROCUREMENT=6, INTERNAL_AUD=7, COMPLIANCE=8, LEGAL=10,
            HR=18, FINANCE=19, OPERATIONS=25, LICENSING=26, INSPECTION=27,
            BENEFICIARY=28)
FW_NAZAHA, FW_ISO37001, FW_FATF = 16, 17, 20

stats: dict[str, list[int]] = {}            # table -> [inserted, updated]
blockers: list[str] = []


def bump(table: str, inserted: bool) -> None:
    s = stats.setdefault(table, [0, 0])
    s[0 if inserted else 1] += 1


# ---------------------------------------------------------------------------
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    # =======================================================================
    # 1. ethics.whistleblower_case  (guard: case_code)
    # =======================================================================
    WB = [
        dict(code="WB-2026-004", category="CORRUPTION", severity="CRITICAL",
             channel="PUBLIC_PORTAL", rtype="ANONYMOUS", senior=1, status="REFERRED",
             inv=U_AUDITOR, triage=U_COMPLIANCE,
             subj_en="Bribe solicited to expedite a licence issuance",
             subj_ar="طلب رشوة لتسريع إصدار ترخيص",
             nar_en="A licensing officer is alleged to have solicited a cash payment "
                    "from an applicant in exchange for expediting the issuance of an "
                    "operating licence, bypassing the standard review queue. The matter "
                    "has been referred to the National Anti-Corruption Authority (Nazaha).",
             nar_ar="يُزعم أن أحد موظفي إدارة التراخيص طلب مبلغاً نقدياً من أحد المتقدمين "
                    "مقابل تسريع إصدار ترخيص تشغيلي وتجاوز دور المراجعة النظامي. "
                    "وقد أُحيلت الواقعة إلى الهيئة الوطنية لمكافحة الفساد (نزاهة)."),
        dict(code="WB-2026-005", category="FRAUD", severity="HIGH",
             channel="INTERNAL_PORTAL", rtype="EMPLOYEE", senior=0, status="SUBSTANTIATED",
             inv=U_AUDITOR, triage=U_COMPLIANCE,
             subj_en="Bid collusion / tender-rigging in a procurement competition",
             subj_ar="تلاعب وتكسير عطاءات في إحدى المنافسات",
             nar_en="Three bidders in an IT procurement competition appear to have "
                    "coordinated their offers (cover pricing) to steer the award to a "
                    "pre-selected vendor. Internal review substantiated the pattern of "
                    "coordinated bidding.",
             nar_ar="يبدو أن ثلاثة من المتنافسين في منافسة لتقنية المعلومات نسّقوا عروضهم "
                    "(تسعير صوري) لتوجيه الترسية إلى مورّد محدد مسبقاً. وقد أثبتت المراجعة "
                    "الداخلية نمط التنسيق في تقديم العطاءات."),
        dict(code="WB-2026-006", category="FINANCIAL_MISCONDUCT", severity="HIGH",
             channel="INTERNAL_PORTAL", rtype="EMPLOYEE", senior=0, status="UNDER_INVESTIGATION",
             inv=U_AUDITOR, triage=U_COMPLIANCE,
             subj_en="Embezzlement of petty-cash custody and per-diem allowances",
             subj_ar="اختلاس عُهد نقدية وبدلات انتداب",
             nar_en="An employee responsible for a petty-cash custody is alleged to have "
                    "misappropriated funds and submitted inflated per-diem allowance "
                    "claims over several months. The case is under active investigation.",
             nar_ar="يُزعم أن موظفاً مسؤولاً عن عهدة نقدية اختلس مبالغ وقدّم مطالبات بدل "
                    "انتداب مُضخّمة على مدى عدة أشهر. والقضية قيد التحقيق الفعلي."),
        dict(code="WB-2026-007", category="CONFLICT_OF_INTEREST", severity="MEDIUM",
             channel="INTERNAL_PORTAL", rtype="EMPLOYEE", senior=1, status="CLOSED_REMEDIATED",
             inv=U_AUDITOR, triage=U_COMPLIANCE,
             subj_en="Committee member steered a contract to a relative's firm",
             subj_ar="توجيه عضو لجنة عقداً لشركة يملكها أحد أقاربه",
             nar_en="A member of an evaluation committee participated in awarding a "
                    "services contract to a company owned by a first-degree relative "
                    "without disclosing the relationship. The conflict was substantiated; "
                    "the member recused, the award was re-evaluated and the matter remediated.",
             nar_ar="شارك أحد أعضاء لجنة تقييم في ترسية عقد خدمات على شركة يملكها قريب من "
                    "الدرجة الأولى دون الإفصاح عن العلاقة. وقد ثبت تعارض المصالح، فتنحّى "
                    "العضو وأُعيد تقييم الترسية وعولجت الواقعة."),
        dict(code="WB-2026-008", category="REGULATORY_NON_COMPLIANCE", severity="MEDIUM",
             channel="PHONE", rtype="ANONYMOUS", senior=0, status="UNDER_INVESTIGATION",
             inv=U_AUDITOR, triage=U_COMPLIANCE,
             subj_en="Inspector falsifying field inspection reports",
             subj_ar="تزوير أحد المفتشين لمحاضر التفتيش الميداني",
             nar_en="A field inspector is alleged to have recorded inspection visits that "
                    "did not take place and signed off compliance results without "
                    "conducting the inspections. The allegation is under investigation.",
             nar_ar="يُزعم أن أحد المفتشين الميدانيين سجّل زيارات تفتيشية لم تُنفّذ واعتمد "
                    "نتائج التزام دون إجراء التفتيش فعلياً. والبلاغ قيد التحقيق."),
        dict(code="WB-2026-009", category="OTHER", severity="LOW",
             channel="INTERNAL_PORTAL", rtype="EMPLOYEE", senior=0, status="CLOSED_NO_ACTION",
             inv=U_RANALYST, triage=U_COMPLIANCE,
             subj_en="Misuse of authority vehicles and assets",
             subj_ar="إساءة استخدام مركبات الهيئة وأصولها",
             nar_en="A report that an official vehicle was used for personal errands "
                    "outside working hours. After review the usage was found to be within "
                    "the approved logistics arrangement; closed with no action.",
             nar_ar="بلاغ بأن إحدى المركبات الرسمية استُخدمت لأغراض شخصية خارج أوقات العمل. "
                    "وبعد المراجعة تبيّن أن الاستخدام ضمن ترتيب الدعم اللوجستي المعتمد، "
                    "فأُغلق دون اتخاذ إجراء."),
    ]
    for w in WB:
        cur.execute("SELECT id FROM ethics.whistleblower_case WHERE organisation_id=? AND case_code=?",
                    ORG, w["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.whistleblower_case
                      SET category=?, severity=?, submission_channel=?, reporter_type=?,
                          involves_senior_executive=?, status=?,
                          assigned_investigator_user_id=?, triage_by_user_id=?,
                          subject_en=?, subject_ar=?, narrative_en=?, narrative_ar=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                w["category"], w["severity"], w["channel"], w["rtype"], w["senior"],
                w["status"], w["inv"], w["triage"], w["subj_en"], w["subj_ar"],
                w["nar_en"], w["nar_ar"], U_ADMIN, row[0])
            bump("ethics.whistleblower_case", False)
        else:
            cur.execute(
                """INSERT INTO ethics.whistleblower_case
                       (organisation_id, case_code, submission_channel, reporter_type,
                        is_anonymous, category, severity, subject_en, subject_ar,
                        narrative_en, narrative_ar, involves_senior_executive, status,
                        assigned_investigator_user_id, triage_by_user_id, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, w["code"], w["channel"], w["rtype"],
                1 if w["rtype"] == "ANONYMOUS" else 0, w["category"], w["severity"],
                w["subj_en"], w["subj_ar"], w["nar_en"], w["nar_ar"], w["senior"],
                w["status"], w["inv"], w["triage"], U_ADMIN)
            bump("ethics.whistleblower_case", True)

    def wb_id(code: str):
        cur.execute("SELECT id FROM ethics.whistleblower_case WHERE organisation_id=? AND case_code=?",
                    ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 2. ethics.whistleblower_case_update  (guard: case_id + update_type + body prefix)
    # =======================================================================
    WBU = [
        ("WB-2026-004", "TRIAGE_DECISION", 1, U_COMPLIANCE,
         "Triaged as CRITICAL: credible bribery allegation involving a public official; "
         "prioritised for immediate investigation and external referral.",
         "صُنّف البلاغ بدرجة حرجة: ادعاء رشوة موثوق يخص موظفاً عاماً؛ أُعطي أولوية للتحقيق "
         "الفوري والإحالة الخارجية."),
        ("WB-2026-004", "INVESTIGATOR_ASSIGNED", 0, U_COMPLIANCE,
         "Assigned to Internal Audit for fact-finding; Legal Affairs consulted on referral.",
         "أُسند إلى المراجعة الداخلية لتقصّي الحقائق؛ واستُشيرت الإدارة القانونية بشأن الإحالة."),
        ("WB-2026-004", "FINDING_RECORDED", 0, U_AUDITOR,
         "Interim finding: applicant statement and call records support the allegation.",
         "نتيجة أولية: تدعم إفادة المتقدم وسجلات الاتصالات الادعاء."),
        ("WB-2026-004", "CLOSURE", 1, U_COMPLIANCE,
         "Referred to the National Anti-Corruption Authority (Nazaha); case set to REFERRED.",
         "أُحيلت إلى الهيئة الوطنية لمكافحة الفساد (نزاهة)؛ وحُدّدت حالة البلاغ كمُحالة."),
        ("WB-2026-005", "TRIAGE_DECISION", 1, U_COMPLIANCE,
         "Triaged HIGH: indicators of bid collusion in a procurement competition.",
         "صُنّف بدرجة عالية: مؤشرات تواطؤ في عطاءات إحدى المنافسات."),
        ("WB-2026-005", "INVESTIGATOR_ASSIGNED", 0, U_COMPLIANCE,
         "Assigned to Internal Audit; procurement file and bid documents requested.",
         "أُسند إلى المراجعة الداخلية؛ وطُلب ملف المنافسة ووثائق العطاءات."),
        ("WB-2026-005", "FINDING_RECORDED", 0, U_AUDITOR,
         "Substantiated: pricing patterns and common drafting indicate coordinated bidding.",
         "ثبت البلاغ: أنماط التسعير وتشابه الصياغة تشير إلى تنسيق في تقديم العطاءات."),
        ("WB-2026-006", "TRIAGE_DECISION", 1, U_COMPLIANCE,
         "Triaged HIGH: alleged misappropriation of petty-cash custody and per-diem claims.",
         "صُنّف بدرجة عالية: ادعاء اختلاس عهدة نقدية ومطالبات بدل انتداب."),
        ("WB-2026-006", "INVESTIGATOR_ASSIGNED", 0, U_COMPLIANCE,
         "Assigned to Internal Audit; Finance asked to reconcile the custody account.",
         "أُسند إلى المراجعة الداخلية؛ وطُلب من الإدارة المالية تسوية حساب العهدة."),
        ("WB-2026-007", "TRIAGE_DECISION", 1, U_COMPLIANCE,
         "Triaged MEDIUM: undisclosed relationship between an evaluator and an awardee.",
         "صُنّف بدرجة متوسطة: علاقة غير مُفصَح عنها بين عضو تقييم وجهة مُرسى عليها."),
        ("WB-2026-007", "CLOSURE", 1, U_COMPLIANCE,
         "Substantiated; member recused, award re-evaluated, COI control reinforced. Remediated.",
         "ثبت البلاغ؛ تنحّى العضو وأُعيد تقييم الترسية وعُزّز ضابط تعارض المصالح. عولجت الواقعة."),
        ("WB-2026-008", "TRIAGE_DECISION", 1, U_COMPLIANCE,
         "Triaged MEDIUM: allegation of falsified field inspection reports.",
         "صُنّف بدرجة متوسطة: ادعاء تزوير محاضر تفتيش ميداني."),
        ("WB-2026-008", "INVESTIGATION_NOTE", 0, U_AUDITOR,
         "Cross-checking inspection logs, GPS visit records and beneficiary confirmations.",
         "تجري مطابقة سجلات التفتيش وبيانات مواقع الزيارات وتأكيدات المستفيدين."),
        ("WB-2026-009", "CLOSURE", 1, U_COMPLIANCE,
         "Reviewed: vehicle use within approved logistics arrangement. Closed, no action.",
         "بعد المراجعة: استخدام المركبة ضمن الترتيب اللوجستي المعتمد. أُغلق دون إجراء."),
        ("WB-2026-002", "INVESTIGATION_NOTE", 0, U_COMPLIANCE,
         "Investigation ongoing; witness interviews scheduled with HR support.",
         "التحقيق جارٍ؛ جُدولت مقابلات الشهود بدعم من الموارد البشرية."),
        ("WB-2026-003", "INVESTIGATOR_ASSIGNED", 0, U_COMPLIANCE,
         "Assigned to Compliance for conflict-of-interest assessment.",
         "أُسند إلى إدارة الالتزام لتقييم تعارض المصالح."),
    ]
    for code, utype, vis, actor, ben, bar in WBU:
        cid = wb_id(code)
        if cid is None:
            continue
        prefix = ben[:40]
        cur.execute(
            "SELECT id FROM ethics.whistleblower_case_update "
            "WHERE case_id=? AND update_type=? AND LEFT(body_en,40)=?",
            cid, utype, prefix)
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.whistleblower_case_update
                      SET body_en=?, body_ar=?, is_visible_to_reporter=?, actor_user_id=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                ben, bar, vis, actor, U_ADMIN, row[0])
            bump("ethics.whistleblower_case_update", False)
        else:
            cur.execute(
                """INSERT INTO ethics.whistleblower_case_update
                       (organisation_id, case_id, update_type, actor_user_id,
                        is_visible_to_reporter, body_en, body_ar, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, cid, utype, actor, vis, ben, bar, U_ADMIN)
            bump("ethics.whistleblower_case_update", True)

    # =======================================================================
    # 3. ethics.whistleblower_retaliation_complaint  (guard: related_case_id + claimed_actor)
    # =======================================================================
    WBR = [
        dict(case="WB-2026-004", actor="مدير إدارة التراخيص",
             status="VALIDATED", prot=U_COMPLIANCE,
             action_en="Claimant alleges a punitive transfer to a remote office shortly "
                       "after reporting the licensing-bribery case.",
             action_ar="يدّعي المُبلِّغ نقله تعسفياً إلى مكتب بعيد بُعيد إبلاغه عن قضية "
                       "الرشوة في التراخيص."),
        dict(case="WB-2026-005", actor="رئيس قسم المشتريات",
             status="RECEIVED", prot=U_COMPLIANCE,
             action_en="Claimant alleges an unjustified downgrade in the annual "
                       "performance review after reporting the tender-rigging case.",
             action_ar="يدّعي المُبلِّغ خفض تقييم الأداء السنوي دون مبرر بعد إبلاغه عن "
                       "قضية تكسير العطاءات."),
    ]
    for r in WBR:
        cid = wb_id(r["case"])
        if cid is None:
            continue
        cur.execute(
            "SELECT id FROM ethics.whistleblower_retaliation_complaint "
            "WHERE related_case_id=? AND claimed_actor=?", cid, r["actor"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.whistleblower_retaliation_complaint
                      SET claimed_action=?, status=?, assigned_protector_user_id=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                r["action_en"], r["status"], r["prot"], U_ADMIN, row[0])
            bump("ethics.whistleblower_retaliation_complaint", False)
        else:
            cur.execute(
                """INSERT INTO ethics.whistleblower_retaliation_complaint
                       (organisation_id, related_case_id, claimed_actor, claimed_action,
                        status, assigned_protector_user_id, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?)""",
                ORG, cid, r["actor"], r["action_en"], r["status"], r["prot"], U_ADMIN)
            bump("ethics.whistleblower_retaliation_complaint", True)

    # =======================================================================
    # 4. ethics.coi_declaration_campaign  (guard: code)
    # =======================================================================
    CAMP = [
        dict(code="COI-2026-EXEC", scope="EXEC", status="OPEN", target=30,
             ten="COI Annual Cycle FY2026 - Executive Leadership",
             tar="دورة إقرارات تعارض المصالح للسنة المالية 2026 - القيادة التنفيذية"),
        dict(code="COI-2026-HIGHRISK", scope="HIGH_RISK_ROLES", status="OPEN", target=120,
             ten="COI Cycle FY2026 - High-Risk Functions (Procurement / Licensing / Inspection)",
             tar="دورة إقرارات تعارض المصالح 2026 - الوظائف عالية المخاطر (المشتريات/التراخيص/التفتيش)"),
    ]
    for c in CAMP:
        cur.execute("SELECT id FROM ethics.coi_declaration_campaign WHERE organisation_id=? AND code=?",
                    ORG, c["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.coi_declaration_campaign
                      SET title_en=?, title_ar=?, scope=?, status=?, target_user_count=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                c["ten"], c["tar"], c["scope"], c["status"], c["target"], U_ADMIN, row[0])
            bump("ethics.coi_declaration_campaign", False)
        else:
            cur.execute(
                """INSERT INTO ethics.coi_declaration_campaign
                       (organisation_id, code, title_en, title_ar, fiscal_year,
                        opens_on, closes_on, status, scope, target_user_count, created_by)
                   VALUES (?, ?, ?, ?, 2026, '2026-01-01', '2026-12-31', ?, ?, ?, ?)""",
                ORG, c["code"], c["ten"], c["tar"], c["status"], c["scope"],
                c["target"], U_ADMIN)
            bump("ethics.coi_declaration_campaign", True)

    def camp_id(code: str):
        cur.execute("SELECT id FROM ethics.coi_declaration_campaign WHERE organisation_id=? AND code=?",
                    ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 5. ethics.coi_declaration  (guard: campaign_id + employee_no)
    # =======================================================================
    DECL = [
        ("COI-2026-EXEC", "EMP-1001", "Khalid Al-Otaibi", "خالد العتيبي",
         "Deputy for Shared Services", "وكيل الخدمات المشتركة",
         "Shared Services", "الخدمات المشتركة", 0, 0, "CLEARED"),
        ("COI-2026-EXEC", "EMP-1002", "Sara Al-Qahtani", "سارة القحطاني",
         "Director of Finance", "مديرة الإدارة المالية",
         "Finance", "الإدارة المالية", 0, 1, "CLEARED"),
        ("COI-2026-EXEC", "EMP-1003", "Faisal Al-Harbi", "فيصل الحربي",
         "Director of Procurement", "مدير إدارة المشتريات",
         "Procurement", "إدارة المشتريات", 1, 0, "RECUSAL_REQUIRED"),
        ("COI-2026-EXEC", "EMP-1004", "Noura Al-Shammari", "نورة الشمري",
         "Director of Human Resources", "مديرة الموارد البشرية",
         "Human Resources", "الموارد البشرية", 0, 0, "CLEARED"),
        ("COI-2026-HIGHRISK", "EMP-2001", "Abdullah Al-Dossary", "عبدالله الدوسري",
         "Procurement Specialist", "أخصائي مشتريات",
         "Procurement", "إدارة المشتريات", 1, 0, "RECUSAL_REQUIRED"),
        ("COI-2026-HIGHRISK", "EMP-2002", "Maha Al-Zahrani", "مها الزهراني",
         "Licensing Officer", "موظفة تراخيص",
         "Licensing", "إدارة التراخيص", 1, 0, "MITIGATION_REQUIRED"),
        ("COI-2026-HIGHRISK", "EMP-2003", "Turki Al-Mutairi", "تركي المطيري",
         "Field Inspector", "مفتش ميداني",
         "Inspection", "إدارة التفتيش", 0, 1, "CLEARED"),
        ("COI-2026-HIGHRISK", "EMP-2004", "Reem Al-Ghamdi", "ريم الغامدي",
         "Procurement Evaluation Lead", "رئيسة لجنة تقييم المشتريات",
         "Procurement", "إدارة المشتريات", 1, 0, "ESCALATED"),
        ("COI-2026-HIGHRISK", "EMP-2005", "Bandar Al-Subaie", "بندر السبيعي",
         "Licensing Reviewer", "مراجع تراخيص",
         "Licensing", "إدارة التراخيص", 0, 0, "CLEARED"),
        ("COI-2026-HIGHRISK", "EMP-2006", "Hessa Al-Anazi", "حصة العنزي",
         "Grants & Subsidies Officer", "موظفة المنح والدعم",
         "Beneficiary Services", "خدمات المستفيدين", 1, 0, "MITIGATION_REQUIRED"),
        ("COI-2026-HIGHRISK", "EMP-2007", "Majed Al-Shehri", "ماجد الشهري",
         "Procurement Analyst", "محلل مشتريات",
         "Procurement", "إدارة المشتريات", 0, 1, "CLEARED"),
        ("COI-2026-HIGHRISK", "EMP-2008", "Latifa Al-Juhani", "لطيفة الجهني",
         "Inspection Supervisor", "مشرفة تفتيش",
         "Inspection", "إدارة التفتيش", 0, 0, "CLEARED"),
    ]
    for camp, emp, nen, nar, pen, par, den, dar, hc, hp, outcome in DECL:
        cid = camp_id(camp)
        if cid is None:
            continue
        cur.execute(
            "SELECT id FROM ethics.coi_declaration WHERE organisation_id=? AND campaign_id=? AND employee_no=?",
            ORG, cid, emp)
        row = cur.fetchone()
        rev = U_COMPLIANCE if outcome != "PENDING" else None
        if row:
            cur.execute(
                """UPDATE ethics.coi_declaration
                      SET full_name_en=?, full_name_ar=?, position_title_en=?, position_title_ar=?,
                          department_en=?, department_ar=?, has_conflict=?, has_potential_conflict=?,
                          review_outcome=?, reviewed_by=?, reviewed_on=SYSUTCDATETIME(),
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                nen, nar, pen, par, den, dar, hc, hp, outcome, rev, U_ADMIN, row[0])
            bump("ethics.coi_declaration", False)
        else:
            cur.execute(
                """INSERT INTO ethics.coi_declaration
                       (organisation_id, campaign_id, employee_no, full_name_en, full_name_ar,
                        position_title_en, position_title_ar, department_en, department_ar,
                        has_conflict, has_potential_conflict, review_outcome, reviewed_by,
                        reviewed_on, submitted_on, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                           SYSUTCDATETIME(), SYSUTCDATETIME(), ?)""",
                ORG, cid, emp, nen, nar, pen, par, den, dar, hc, hp, outcome, rev, U_ADMIN)
            bump("ethics.coi_declaration", True)

    def decl_id(emp: str):
        cur.execute("SELECT id FROM ethics.coi_declaration WHERE organisation_id=? AND employee_no=?",
                    ORG, emp)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 6. ethics.coi_disclosed_item  (guard: declaration_id + item_type + counterparty_en)
    # =======================================================================
    ITEM = [
        ("EMP-1003", "RELATED_PARTY", "Al-Faisaliah Contracting Co.", "شركة الفيصلية للمقاولات",
         "Spouse is a majority owner; the firm bids on the Authority's tenders.",
         "الزوجة مالكة أغلبية في الشركة التي تتقدم لمنافسات الهيئة.", 850000,
         "Recuse from any procurement decision involving this firm.",
         "التنحّي عن أي قرار مشتريات يخص هذه الشركة."),
        ("EMP-2001", "RELATED_PARTY", "Najd Technical Services", "خدمات نجد الفنية",
         "Brother is the general manager of a vendor bidding on IT tenders.",
         "الأخ مدير عام لأحد الموردين المتقدمين لمنافسات تقنية المعلومات.", None,
         "Recuse from evaluation of bids submitted by this vendor.",
         "التنحّي عن تقييم العطاءات المقدمة من هذا المورّد."),
        ("EMP-2002", "SHAREHOLDING", "Tamkeen Licensed Trading Est.", "مؤسسة تمكين للتجارة المرخّصة",
         "Holds a shareholding above 5% in an entity regulated/licensed by the Authority.",
         "تملك حصة تتجاوز 5% في منشأة تخضع لترخيص الهيئة.", 120000,
         "Reassign licensing files related to this entity to another officer.",
         "إعادة إسناد ملفات الترخيص المتعلقة بهذه المنشأة إلى موظف آخر."),
        ("EMP-2004", "FAMILY_RELATIONSHIP", "Horizon Integrated Solutions", "حلول الأفق المتكاملة",
         "A first-degree relative heads a vendor participating in an active competition.",
         "قريب من الدرجة الأولى يرأس مورّداً مشاركاً في منافسة قائمة.", None,
         "Step down as evaluation lead for the affected competition; escalate to committee.",
         "التنحّي عن رئاسة لجنة تقييم المنافسة المعنية وتصعيد الأمر إلى اللجنة."),
        ("EMP-2004", "DIRECTORSHIP", "Ataa Charitable Society", "جمعية عطاء الخيرية",
         "Holds a board seat in a charity that receives grants administered by the Authority.",
         "تشغل مقعداً في مجلس جمعية خيرية تتلقى منحاً تديرها الهيئة.", None,
         "Recuse from grant decisions involving this society.",
         "التنحّي عن قرارات المنح التي تخص هذه الجمعية."),
        ("EMP-2006", "RELATED_PARTY", "Wafaa Beneficiary Cooperative", "تعاونية وفاء للمستفيدين",
         "Cousin manages a grant-recipient cooperative under review.",
         "ابن العم يدير تعاونية متلقية للدعم قيد المراجعة.", 60000,
         "Transfer the cooperative's grant file to another officer.",
         "تحويل ملف دعم التعاونية إلى موظف آخر."),
        ("EMP-2003", "OUTSIDE_EMPLOYMENT", "Public University (part-time lecturer)",
         "جامعة حكومية (محاضر غير متفرغ)",
         "Part-time evening lecturing role disclosed for transparency; no overlap with duties.",
         "عمل محاضرة مسائية غير متفرغة مُفصَح عنه للشفافية دون تعارض مع المهام.", 0,
         "Confirm lecturing hours are outside official working time.",
         "التأكد من أن ساعات المحاضرة خارج وقت العمل الرسمي."),
    ]
    for emp, itype, cpen, cpar, ren, rar, val, men, mar in ITEM:
        did = decl_id(emp)
        if did is None:
            continue
        cur.execute(
            "SELECT id FROM ethics.coi_disclosed_item "
            "WHERE declaration_id=? AND item_type=? AND counterparty_en=?",
            did, itype, cpen)
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.coi_disclosed_item
                      SET counterparty_ar=?, relationship_description_en=?,
                          relationship_description_ar=?, financial_value_sar=?,
                          mitigation_action_en=?, mitigation_action_ar=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                cpar, ren, rar, val, men, mar, U_ADMIN, row[0])
            bump("ethics.coi_disclosed_item", False)
        else:
            cur.execute(
                """INSERT INTO ethics.coi_disclosed_item
                       (declaration_id, item_type, counterparty_en, counterparty_ar,
                        relationship_description_en, relationship_description_ar,
                        financial_value_sar, mitigation_action_en, mitigation_action_ar,
                        created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                did, itype, cpen, cpar, ren, rar, val, men, mar, U_ADMIN)
            bump("ethics.coi_disclosed_item", True)

    # =======================================================================
    # 7. ethics.coi_recusal_event  (guard: user_id + meeting_reference)
    # =======================================================================
    RECUSAL = [
        dict(user=U_RMGR, decl="EMP-1003", mref="COM-ANTIFRAUD-2026-03",
             ccode="COM-ANTIFRAUD", agenda="3.2|4.1",
             ren="Member recused from the tender-award vote where a spouse-owned firm had bid.",
             rar="تنحّى العضو عن التصويت على ترسية المنافسة التي تقدمت لها شركة تملكها الزوجة."),
        dict(user=U_RANALYST, decl="EMP-2001", mref="PROC-EVAL-2026-07",
             ccode="COM-ANTIFRAUD", agenda="2.1",
             ren="Evaluator recused from scoring bids submitted by a relative's vendor.",
             rar="تنحّى عضو التقييم عن تقييم العطاءات المقدمة من مورّد يملكه أحد أقاربه."),
        dict(user=U_AUDITOR, decl=None, mref="COM-AUDIT-2026-02",
             ccode="COM-AUDIT", agenda="5.3",
             ren="Auditor recused from an audit item touching their former department.",
             rar="تنحّى المراجع عن بند تدقيق يخص إدارته السابقة."),
        dict(user=U_COMPLIANCE, decl="EMP-2002", mref="LIC-DECISION-2026-11",
             ccode="COM-INTCTRL", agenda="1.4",
             ren="Member recused from a licensing decision on a related regulated entity.",
             rar="تنحّى العضو عن قرار ترخيص يخص منشأة خاضعة لها علاقة."),
        dict(user=U_VIEWER, decl="EMP-2004", mref="PROC-EVAL-2026-09",
             ccode="COM-ANTIFRAUD", agenda="3.1|3.2",
             ren="Evaluation lead recused; a first-degree relative heads a bidding vendor.",
             rar="تنحّت رئيسة لجنة التقييم لوجود قريب من الدرجة الأولى يرأس أحد الموردين المتنافسين."),
    ]
    for r in RECUSAL:
        did = decl_id(r["decl"]) if r["decl"] else None
        cur.execute(
            "SELECT id FROM ethics.coi_recusal_event WHERE organisation_id=? AND user_id=? AND meeting_reference=?",
            ORG, r["user"], r["mref"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.coi_recusal_event
                      SET declaration_id=?, committee_code=?, agenda_items=?,
                          recusal_reason_en=?, recusal_reason_ar=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                did, r["ccode"], r["agenda"], r["ren"], r["rar"], U_ADMIN, row[0])
            bump("ethics.coi_recusal_event", False)
        else:
            cur.execute(
                """INSERT INTO ethics.coi_recusal_event
                       (organisation_id, declaration_id, user_id, meeting_reference,
                        committee_code, recusal_reason_en, recusal_reason_ar, agenda_items,
                        created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, did, r["user"], r["mref"], r["ccode"], r["ren"], r["rar"],
                r["agenda"], U_ADMIN)
            bump("ethics.coi_recusal_event", True)

    # =======================================================================
    # 8. ethics.cases  (legacy register; guard: case_code) -- continue ETH-2026-0NN
    # =======================================================================
    AF_COMMITTEE = "Anti-Fraud & Integrity Committee / لجنة مكافحة الاحتيال والنزاهة"
    CASES = [
        dict(code="ETH-2026-011", cat="ABUSE_OF_OFFICE", sev="HIGH", status="INVESTIGATION",
             ch="PORTAL", inv=U_AUDITOR,
             ten="Abuse of office to favour a related party",
             tar="استغلال المنصب لمحاباة طرف ذي علاقة",
             desc="An official allegedly used their position to direct a benefit to a "
                  "related party outside due process.",
             dar="يُزعم أن مسؤولاً استغل منصبه لتوجيه منفعة لطرف ذي علاقة خارج الإجراءات النظامية."),
        dict(code="ETH-2026-012", cat="FRAUD", sev="HIGH", status="SUBSTANTIATED",
             ch="HOTLINE", inv=U_AUDITOR,
             ten="Misuse of public funds in operational spending",
             tar="إساءة استخدام المال العام في الإنفاق التشغيلي",
             desc="Operational expenditure was diverted from its approved purpose; "
                  "substantiated by audit review.",
             dar="حُوّل إنفاق تشغيلي عن غرضه المعتمد؛ وثبت ذلك بمراجعة التدقيق."),
        dict(code="ETH-2026-013", cat="BRIBERY", sev="CRITICAL", status="INVESTIGATION",
             ch="PORTAL", inv=U_AUDITOR,
             ten="Bribery in a licensing decision",
             tar="رشوة في قرار ترخيص",
             desc="Allegation that a facilitation payment influenced a licensing decision.",
             dar="ادعاء بأن دفعة تيسير أثّرت في قرار ترخيص."),
        dict(code="ETH-2026-014", cat="CONFLICT_OF_INTEREST", sev="MEDIUM", status="TRIAGE",
             ch="EMAIL", inv=U_COMPLIANCE,
             ten="Nepotism / wasta in a hiring decision",
             tar="محاباة (واسطة) في قرار توظيف",
             desc="Concern that a hiring decision favoured a relative of a hiring-panel member.",
             dar="مخاوف من أن قرار توظيف حابى قريباً لأحد أعضاء لجنة التوظيف."),
        dict(code="ETH-2026-015", cat="POLICY_VIOLATION", sev="HIGH", status="INVESTIGATION",
             ch="HOTLINE", inv=U_AUDITOR,
             ten="Leaking confidential tender information",
             tar="تسريب معلومات سرية عن منافسة",
             desc="Confidential bid information may have been disclosed to a bidder pre-award.",
             dar="ربما أُفصح عن معلومات سرية عن العطاءات لأحد المتنافسين قبل الترسية."),
        dict(code="ETH-2026-016", cat="FRAUD", sev="MEDIUM", status="SUBSTANTIATED",
             ch="PORTAL", inv=U_AUDITOR,
             ten="False travel-expense claims",
             tar="مطالبات سفر ومصروفات غير صحيحة",
             desc="An employee submitted falsified travel and expense claims; substantiated.",
             dar="قدّم موظف مطالبات سفر ومصروفات مزوّرة؛ وثبت ذلك."),
    ]
    for c in CASES:
        cur.execute("SELECT id FROM ethics.cases WHERE organisation_id=? AND case_code=?",
                    ORG, c["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.cases
                      SET title_en=?, title_ar=?, category=?, severity=?, intake_channel=?,
                          status=?, description=?, framework_id=?, committee_name=?,
                          investigator_id=?, updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                c["ten"], c["tar"], c["cat"], c["sev"], c["ch"], c["status"], c["desc"],
                FW_NAZAHA, AF_COMMITTEE, c["inv"], U_ADMIN, row[0])
            bump("ethics.cases", False)
        else:
            cur.execute(
                """INSERT INTO ethics.cases
                       (organisation_id, case_code, title_en, title_ar, category, severity,
                        intake_channel, description, status, framework_id, committee_name,
                        investigator_id, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, c["code"], c["ten"], c["tar"], c["cat"], c["sev"], c["ch"],
                c["desc"], c["status"], FW_NAZAHA, AF_COMMITTEE, c["inv"], U_ADMIN)
            bump("ethics.cases", True)

    def case_id(code: str):
        cur.execute("SELECT id FROM ethics.cases WHERE organisation_id=? AND case_code=?",
                    ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 9. ethics.case_actions  (guard: case_id + action_type + title)
    # =======================================================================
    ACTIONS = [
        ("ETH-2026-011", "COMMITTEE_REFERRAL", U_COMPLIANCE,
         "Referred to the Anti-Fraud & Integrity Committee",
         "Case escalated to the Anti-Fraud & Integrity Committee for oversight.",
         "أُحيلت القضية إلى لجنة مكافحة الاحتيال والنزاهة للإشراف."),
        ("ETH-2026-011", "INTERVIEW", U_AUDITOR,
         "Interview of the implicated official",
         "Subject interviewed; statement recorded for the investigation file.",
         "قُوبل المسؤول المعني وسُجّلت إفادته في ملف التحقيق."),
        ("ETH-2026-012", "EVIDENCE_REQUEST", U_AUDITOR,
         "Evidence request to the Finance department",
         "Requested ledgers and approval trail from Finance.",
         "طُلبت السجلات المالية ومسار الاعتماد من الإدارة المالية."),
        ("ETH-2026-012", "FINDING", U_AUDITOR,
         "Finding: expenditure diverted from approved purpose",
         "Audit finding confirms diversion of operational expenditure.",
         "نتيجة التدقيق تؤكد تحويل الإنفاق التشغيلي عن غرضه المعتمد."),
        ("ETH-2026-013", "COMMITTEE_REFERRAL", U_COMPLIANCE,
         "Referred to the Anti-Fraud & Integrity Committee",
         "Bribery allegation escalated to the committee; external referral considered.",
         "صُعّد ادعاء الرشوة إلى اللجنة مع دراسة الإحالة الخارجية."),
        ("ETH-2026-014", "TRIAGE", U_COMPLIANCE,
         "Triage of hiring conflict-of-interest concern",
         "Triaged; HR records requested to verify the panel composition.",
         "تمت الفرزة وطُلبت سجلات الموارد البشرية للتحقق من تشكيل اللجنة."),
        ("ETH-2026-015", "EVIDENCE_REQUEST", U_AUDITOR,
         "Evidence request to the Procurement department",
         "Requested access logs and bid-handling records from Procurement.",
         "طُلبت سجلات الوصول وإجراءات تداول العطاءات من إدارة المشتريات."),
        ("ETH-2026-015", "INTERVIEW", U_AUDITOR,
         "Interview of procurement staff",
         "Procurement staff interviewed regarding bid confidentiality.",
         "قُوبل موظفو المشتريات بشأن سرية العطاءات."),
        ("ETH-2026-016", "FINDING", U_AUDITOR,
         "Finding: falsified travel claims substantiated",
         "Reconciliation confirmed duplicated and falsified travel claims.",
         "أكدت المطابقة وجود مطالبات سفر مكررة ومزوّرة."),
        ("ETH-2026-016", "CLOSURE", U_COMPLIANCE,
         "Recovery and disciplinary referral",
         "Amounts recovered; matter referred for disciplinary action.",
         "استُردّت المبالغ وأُحيلت الواقعة للإجراء التأديبي."),
    ]
    for code, atype, actor, title, den, dar in ACTIONS:
        cid = case_id(code)
        if cid is None:
            continue
        cur.execute(
            "SELECT id FROM ethics.case_actions WHERE case_id=? AND action_type=? AND title=?",
            cid, atype, title)
        row = cur.fetchone()
        actor_name = ("Internal Audit / المراجعة الداخلية" if actor == U_AUDITOR
                      else "Compliance / الالتزام")
        if row:
            cur.execute(
                "UPDATE ethics.case_actions SET description=?, actor_id=?, actor_name=? WHERE id=?",
                den, actor, actor_name, row[0])
            bump("ethics.case_actions", False)
        else:
            cur.execute(
                """INSERT INTO ethics.case_actions
                       (case_id, action_type, title, description, actor_id, actor_name)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                cid, atype, title, den, actor, actor_name)
            bump("ethics.case_actions", True)

    # =======================================================================
    # 10. ethics.case_links  (guard: case_id + entity_type + entity_id)
    # =======================================================================
    LINKS = [
        ("ETH-2026-011", "COMMITTEE", COM_ANTIFRAUD, "ESCALATED_TO"),
        ("ETH-2026-011", "USER", U_AUDITOR, "OWNED_BY"),
        ("ETH-2026-012", "COMMITTEE", COM_ANTIFRAUD, "ESCALATED_TO"),
        ("ETH-2026-012", "DEPARTMENT", DEPT["FINANCE"], "RELATES_TO"),
        ("ETH-2026-013", "COMMITTEE", COM_ANTIFRAUD, "ESCALATED_TO"),
        ("ETH-2026-013", "DEPARTMENT", DEPT["LICENSING"], "RELATES_TO"),
        ("ETH-2026-014", "DEPARTMENT", DEPT["HR"], "RELATES_TO"),
        ("ETH-2026-015", "COMMITTEE", COM_ANTIFRAUD, "ESCALATED_TO"),
        ("ETH-2026-015", "DEPARTMENT", DEPT["PROCUREMENT"], "RELATES_TO"),
        ("ETH-2026-016", "COMMITTEE", COM_AUDIT, "ESCALATED_TO"),
    ]
    for code, etype, eid, ltype in LINKS:
        cid = case_id(code)
        if cid is None:
            continue
        cur.execute(
            "SELECT id FROM ethics.case_links WHERE case_id=? AND entity_type=? AND entity_id=?",
            cid, etype, eid)
        row = cur.fetchone()
        if row:
            cur.execute("UPDATE ethics.case_links SET link_type=? WHERE id=?", ltype, row[0])
            bump("ethics.case_links", False)
        else:
            cur.execute(
                "INSERT INTO ethics.case_links (case_id, entity_type, entity_id, link_type) "
                "VALUES (?, ?, ?, ?)", cid, etype, eid, ltype)
            bump("ethics.case_links", True)
    # Cross-link the licensing-bribery legacy case to its whistleblower equivalent.
    lic_case = case_id("ETH-2026-013")
    wb_lic = wb_id("WB-2026-004")
    if lic_case and wb_lic:
        cur.execute(
            "SELECT id FROM ethics.case_links WHERE case_id=? AND entity_type='WHISTLEBLOWER_CASE' AND entity_id=?",
            lic_case, wb_lic)
        if cur.fetchone():
            bump("ethics.case_links", False)
        else:
            cur.execute(
                "INSERT INTO ethics.case_links (case_id, entity_type, entity_id, link_type) "
                "VALUES (?, 'WHISTLEBLOWER_CASE', ?, 'RELATES_TO')", lic_case, wb_lic)
            bump("ethics.case_links", True)

    # =======================================================================
    # 11. ethics.disclosures  (guard: disclosure_code) -- public-sector gifts/activity
    # =======================================================================
    DISC = [
        dict(code="DISC-2026-0001", dtype="GIFT_RECEIVED", status="APPROVED",
             name="Maha Al-Zahrani", dep="إدارة التراخيص", val=1200,
             ten="Gift received from a licensed entity during a review (returned)",
             tar="هدية مستلمة من منشأة مرخّصة أثناء المراجعة (أُعيدت)",
             desc="A gift offered by a licensed entity during an active review was "
                  "declined and returned per the gifts policy."),
        dict(code="DISC-2026-0002", dtype="HOSPITALITY", status="REVIEW",
             name="Faisal Al-Harbi", dep="إدارة المشتريات", val=8500,
             ten="Conference hospitality sponsored by a regulated vendor",
             tar="ضيافة مؤتمر برعاية مورّد خاضع للتنظيم",
             desc="Sponsored travel/hospitality to a sector conference offered by a "
                  "vendor; disclosed for review of any conflict."),
        dict(code="DISC-2026-0003", dtype="OUTSIDE_BUSINESS", status="SUBMITTED",
             name="Turki Al-Mutairi", dep="إدارة التفتيش", val=None,
             ten="Part-time external consultancy",
             tar="استشارة خارجية بدوام جزئي",
             desc="Part-time advisory work outside the Authority disclosed for "
                  "transparency; checked for overlap with official duties."),
        dict(code="DISC-2026-0004", dtype="RELATED_PARTY", status="APPROVED",
             name="Hessa Al-Anazi", dep="خدمات المستفيدين", val=None,
             ten="Spouse employed by a grant-recipient entity",
             tar="الزوج موظف لدى جهة متلقية للدعم",
             desc="Disclosed that a spouse is employed by an entity that receives "
                  "grants; recusal applied to related decisions."),
        dict(code="DISC-2026-0005", dtype="GIFT_GIVEN", status="APPROVED",
             name="Khalid Al-Otaibi", dep="الخدمات المشتركة", val=600,
             ten="Protocol gift to a visiting delegation",
             tar="هدية بروتوكولية لوفد زائر",
             desc="A modest protocol gift presented to a visiting official delegation "
                  "within approved hospitality limits."),
        dict(code="DISC-2026-0006", dtype="CHARITABLE", status="SUBMITTED",
             name="Reem Al-Ghamdi", dep="إدارة المشتريات", val=None,
             ten="Board seat in a charitable society",
             tar="عضوية مجلس جمعية خيرية",
             desc="Unpaid board membership in a charitable society disclosed; recusal "
                  "from grant decisions involving the society."),
        dict(code="DISC-2026-0007", dtype="GIFT_RECEIVED", status="REJECTED",
             name="Bandar Al-Subaie", dep="إدارة التراخيص", val=3000,
             ten="Gift from an applicant pending a licensing decision",
             tar="هدية من متقدم لديه قرار ترخيص معلّق",
             desc="A gift from an applicant with a pending licensing decision was "
                  "rejected as improper and reported."),
        dict(code="DISC-2026-0008", dtype="OUTSIDE_BUSINESS", status="REVIEW",
             name="Sara Al-Qahtani", dep="الإدارة المالية", val=None,
             ten="Post-employment notification (revolving door)",
             tar="إشعار ما بعد الخدمة (الباب الدوار)",
             desc="Advance notification of a prospective post-employment role with a "
                  "regulated entity, disclosed under the revolving-door rules."),
    ]
    for d in DISC:
        cur.execute("SELECT id FROM ethics.disclosures WHERE organisation_id=? AND disclosure_code=?",
                    ORG, d["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.disclosures
                      SET disclosure_type=?, title_en=?, title_ar=?, description=?,
                          discloser_name=?, discloser_department=?, monetary_value=?,
                          status=?, framework_id=?, updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                d["dtype"], d["ten"], d["tar"], d["desc"], d["name"], d["dep"],
                d["val"], d["status"], FW_NAZAHA, U_ADMIN, row[0])
            bump("ethics.disclosures", False)
        else:
            cur.execute(
                """INSERT INTO ethics.disclosures
                       (organisation_id, disclosure_code, disclosure_type, title_en, title_ar,
                        description, discloser_name, discloser_department, monetary_value,
                        currency, status, framework_id, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'SAR', ?, ?, ?)""",
                ORG, d["code"], d["dtype"], d["ten"], d["tar"], d["desc"], d["name"],
                d["dep"], d["val"], d["status"], FW_NAZAHA, U_ADMIN)
            bump("ethics.disclosures", True)

    # =======================================================================
    # 12. ethics.kyc_subjects  (guard: subject_code) -- vendor/grantee integrity DD
    # =======================================================================
    KYC = [
        dict(code="KYC-VND-0001", stype="LEGAL_ENTITY", link="VENDOR", risk="STANDARD",
             pep=0, edd=0, status="APPROVED", cr="1010111222",
             name="Najd Technical Services", nar="خدمات نجد الفنية", fw=FW_NAZAHA,
             notes="Pre-award integrity / debarment vetting before tender award."),
        dict(code="KYC-VND-0002", stype="LEGAL_ENTITY", link="VENDOR", risk="HIGH",
             pep=1, edd=1, status="UNDER_REVIEW", cr="1010333444",
             name="Horizon Integrated Solutions", nar="حلول الأفق المتكاملة", fw=FW_NAZAHA,
             notes="Owner is a former public official (revolving door); EDD required."),
        dict(code="KYC-VND-0003", stype="LEGAL_ENTITY", link="VENDOR", risk="STANDARD",
             pep=0, edd=0, status="APPROVED", cr="1010555666",
             name="Al-Faisaliah Contracting Co.", nar="شركة الفيصلية للمقاولات", fw=FW_NAZAHA,
             notes="Bidding vendor; related-party flag cross-checked against COI register."),
        dict(code="KYC-VND-0004", stype="LEGAL_ENTITY", link="VENDOR", risk="STANDARD",
             pep=0, edd=0, status="PENDING", cr="1010777888",
             name="Tamkeen Licensed Trading Est.", nar="مؤسسة تمكين للتجارة المرخّصة", fw=FW_NAZAHA,
             notes="Integrity due-diligence pending for an upcoming competition."),
        dict(code="KYC-GRT-0001", stype="LEGAL_ENTITY", link="GRANTEE", risk="STANDARD",
             pep=0, edd=0, status="APPROVED", cr="7001234567",
             name="Ataa Charitable Society", nar="جمعية عطاء الخيرية", fw=FW_NAZAHA,
             notes="Grant-recipient eligibility and integrity vetting."),
        dict(code="KYC-GRT-0002", stype="LEGAL_ENTITY", link="GRANTEE", risk="HIGH",
             pep=0, edd=1, status="UNDER_REVIEW", cr="7007654321",
             name="Wafaa Beneficiary Cooperative", nar="تعاونية وفاء للمستفيدين", fw=FW_NAZAHA,
             notes="Eligibility documents under enhanced review following a fraud signal."),
    ]
    for k in KYC:
        cur.execute("SELECT id FROM ethics.kyc_subjects WHERE organisation_id=? AND subject_code=?",
                    ORG, k["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE ethics.kyc_subjects
                      SET subject_type=?, full_name=?, full_name_ar=?, commercial_registration=?,
                          subject_link_type=?, risk_rating=?, is_pep=?, edd_required=?,
                          status=?, framework_id=?, notes=?, updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                k["stype"], k["name"], k["nar"], k["cr"], k["link"], k["risk"], k["pep"],
                k["edd"], k["status"], k["fw"], k["notes"], U_ADMIN, row[0])
            bump("ethics.kyc_subjects", False)
        else:
            cur.execute(
                """INSERT INTO ethics.kyc_subjects
                       (organisation_id, subject_code, subject_type, full_name, full_name_ar,
                        commercial_registration, subject_link_type, risk_rating, is_pep,
                        edd_required, status, framework_id, notes, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, k["code"], k["stype"], k["name"], k["nar"], k["cr"], k["link"],
                k["risk"], k["pep"], k["edd"], k["status"], k["fw"], k["notes"], U_ADMIN)
            bump("ethics.kyc_subjects", True)

    # =======================================================================
    # 13. fraud.fraud_typology  (guard: code; org-1 scoped, additive to global set)
    # =======================================================================
    TYPO = [
        dict(code="PROCUREMENT_FRAUD", origin="COLLUSION", chan="PROCUREMENT", sev="HIGH",
             sama="PROCUREMENT", nen="Procurement Fraud", nar="تلاعب في المشتريات",
             den="Manipulation of procurement processes to obtain improper benefit.",
             dar="التلاعب في إجراءات المشتريات للحصول على منفعة غير مشروعة."),
        dict(code="BID_RIGGING", origin="COLLUSION", chan="PROCUREMENT", sev="HIGH",
             sama="PROCUREMENT", nen="Bid Rigging", nar="تكسير العطاءات",
             den="Collusive bidding to steer a tender award to a pre-selected party.",
             dar="تواطؤ في العطاءات لتوجيه الترسية لطرف محدد مسبقاً."),
        dict(code="GHOST_EMPLOYEE", origin="INTERNAL", chan="PAYROLL", sev="HIGH",
             sama="INTERNAL", nen="Ghost Employee", nar="موظف وهمي",
             den="Fictitious employees added to payroll to divert salary payments.",
             dar="إدراج موظفين وهميين في الرواتب لتحويل المدفوعات."),
        dict(code="PAYROLL_FRAUD", origin="INTERNAL", chan="PAYROLL", sev="MEDIUM",
             sama="INTERNAL", nen="Payroll & Allowance Fraud", nar="احتيال الرواتب والبدلات",
             den="Manipulation of salaries, allowances or deductions for improper gain.",
             dar="التلاعب في الرواتب أو البدلات أو الحسومات لتحقيق كسب غير مشروع."),
        dict(code="EXPENSE_CLAIM_FRAUD", origin="INTERNAL", chan="EXPENSE", sev="MEDIUM",
             sama="INTERNAL", nen="Expense Claim Fraud", nar="احتيال مطالبات المصروفات",
             den="Falsified, inflated or duplicated expense and per-diem claims.",
             dar="مطالبات مصروفات أو بدلات مزوّرة أو مُضخّمة أو مكررة."),
        dict(code="GRANT_SUBSIDY_FRAUD", origin="EXTERNAL", chan="GRANTS", sev="HIGH",
             sama="EXTERNAL", nen="Grant & Subsidy Fraud", nar="احتيال المنح والدعم",
             den="False eligibility or claims to obtain public grants or subsidies.",
             dar="تقديم بيانات أو مطالبات غير صحيحة للحصول على منح أو دعم حكومي."),
        dict(code="ASSET_MISAPPROPRIATION", origin="INTERNAL", chan="ASSETS", sev="MEDIUM",
             sama="INTERNAL", nen="Public Asset Misappropriation", nar="اختلاس أصول عامة",
             den="Diversion or theft of public assets and equipment.",
             dar="تحويل أو اختلاس الأصول والمعدات العامة."),
    ]
    for t in TYPO:
        cur.execute("SELECT id FROM fraud.fraud_typology WHERE organisation_id=? AND code=?",
                    ORG, t["code"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE fraud.fraud_typology
                      SET name_en=?, name_ar=?, description_en=?, description_ar=?,
                          sama_category_code=?, perpetrator_origin=?, primary_channel=?,
                          typical_severity=?, requires_str_filing=0,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                t["nen"], t["nar"], t["den"], t["dar"], t["sama"], t["origin"],
                t["chan"], t["sev"], U_ADMIN, row[0])
            bump("fraud.fraud_typology", False)
        else:
            cur.execute(
                """INSERT INTO fraud.fraud_typology
                       (organisation_id, code, name_en, name_ar, description_en, description_ar,
                        sama_category_code, perpetrator_origin, primary_channel, typical_severity,
                        requires_str_filing, is_active, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 1, ?)""",
                ORG, t["code"], t["nen"], t["nar"], t["den"], t["dar"], t["sama"],
                t["origin"], t["chan"], t["sev"], U_ADMIN)
            bump("fraud.fraud_typology", True)

    def typo_id(code: str):
        cur.execute("SELECT id FROM fraud.fraud_typology WHERE organisation_id=? AND code=?",
                    ORG, code)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 14. fraud.fraud_case  (guard: organisation_id + external_reference)
    # =======================================================================
    FC = [
        dict(ref="FR-2026-06-0101", typo="BID_RIGGING", status="CONFIRMED", sev="HIGH",
             src="AUDIT_FINDING", inv=U_AUDITOR, loss=1200000, le=1, le_ref="NAZAHA-REF-2026-0007",
             sen="Coordinated bids on an IT tender", sar="عطاءات منسّقة على منافسة تقنية معلومات",
             nen="Three vendors submitted coordinated (cover-priced) bids on an IT tender; "
                 "confirmed by an internal audit finding and referred to Nazaha.",
             nar="قدّم ثلاثة موردين عطاءات منسّقة (تسعير صوري) على منافسة لتقنية المعلومات؛ "
                 "تأكدت الواقعة بنتيجة تدقيق داخلي وأُحيلت إلى نزاهة."),
        dict(ref="FR-2026-06-0102", typo="GHOST_EMPLOYEE", status="CLOSED", sev="HIGH",
             src="INTERNAL_REPORT", inv=U_AUDITOR, loss=430000, le=1, le_ref="PP-REF-2026-0031",
             sen="Three fictitious staff on payroll", sar="ثلاثة موظفين وهميين في الرواتب",
             nen="The payroll contained three non-existent employees diverting salary "
                 "payments; confirmed, partially recovered and closed.",
             nar="احتوت الرواتب على ثلاثة موظفين غير موجودين لتحويل المدفوعات؛ تأكدت "
                 "الواقعة واستُرد جزء من المبالغ وأُغلقت القضية."),
        dict(ref="FR-2026-06-0103", typo="GRANT_SUBSIDY_FRAUD", status="INVESTIGATING", sev="MEDIUM",
             src="DETECTION_SYSTEM", inv=U_AUDITOR, loss=180000, le=0, le_ref=None,
             sen="Falsified eligibility documents by a beneficiary",
             sar="تزوير مستندات أهلية من أحد المستفيدين",
             nen="A beneficiary submitted falsified eligibility documents to obtain a "
                 "subsidy; flagged by the detection system and under investigation.",
             nar="قدّم أحد المستفيدين مستندات أهلية مزوّرة للحصول على دعم؛ رصده النظام "
                 "والقضية قيد التحقيق."),
        dict(ref="FR-2026-06-0104", typo="EXPENSE_CLAIM_FRAUD", status="CONFIRMED", sev="MEDIUM",
             src="AUDIT_FINDING", inv=U_AUDITOR, loss=75000, le=0, le_ref=None,
             sen="Duplicated per-diem claims by a manager",
             sar="مطالبات بدل انتداب مكررة من أحد المديرين",
             nen="A manager submitted duplicated per-diem claims across trips; confirmed "
                 "by audit reconciliation.",
             nar="قدّم أحد المديرين مطالبات بدل انتداب مكررة عبر عدة رحلات؛ تأكدت بمطابقة "
                 "التدقيق."),
        dict(ref="FR-2026-06-0105", typo="PROCUREMENT_FRAUD", status="INVESTIGATING", sev="MEDIUM",
             src="INTERNAL_REPORT", inv=U_AUDITOR, loss=95000, le=0, le_ref=None,
             sen="Split purchase orders to evade the tender threshold",
             sar="تجزئة أوامر الشراء لتجاوز حد المنافسة",
             nen="Purchases were split into multiple orders to stay below the tender "
                 "threshold and avoid competition; under investigation.",
             nar="جُزّئت المشتريات إلى عدة أوامر للبقاء دون حد المنافسة وتجنّب المنافسة؛ "
                 "القضية قيد التحقيق."),
        dict(ref="FR-2026-06-0106", typo="ASSET_MISAPPROPRIATION", status="DETECTED", sev="LOW",
             src="OTHER", inv=U_RANALYST, loss=22000, le=0, le_ref=None,
             sen="Authority equipment diverted", sar="تحويل معدات تابعة للهيئة",
             nen="Authority equipment was reported diverted from its assigned location; "
                 "newly detected and pending triage.",
             nar="أُبلغ عن تحويل معدات تابعة للهيئة عن موقعها المخصص؛ رُصدت حديثاً وبانتظار "
                 "الفرزة."),
    ]
    for f in FC:
        tid = typo_id(f["typo"])
        if tid is None:
            blockers.append(f"fraud.fraud_case {f['ref']}: typology {f['typo']} not found")
            continue
        cur.execute("SELECT id FROM fraud.fraud_case WHERE organisation_id=? AND external_reference=?",
                    ORG, f["ref"])
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE fraud.fraud_case
                      SET typology_id=?, typology_code_snapshot=?, subject_en=?, subject_ar=?,
                          narrative_en=?, narrative_ar=?, status=?, severity=?, detection_source=?,
                          estimated_loss_sar=?, assigned_investigator_user_id=?,
                          law_enforcement_engaged=?, law_enforcement_reference=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                tid, f["typo"], f["sen"], f["sar"], f["nen"], f["nar"], f["status"],
                f["sev"], f["src"], f["loss"], f["inv"], f["le"], f["le_ref"], U_ADMIN, row[0])
            bump("fraud.fraud_case", False)
        else:
            cur.execute(
                """INSERT INTO fraud.fraud_case
                       (organisation_id, external_reference, typology_id, typology_code_snapshot,
                        subject_en, subject_ar, narrative_en, narrative_ar, status, severity,
                        detection_source, estimated_loss_sar, loss_currency, affected_customer_count,
                        assigned_investigator_user_id, law_enforcement_engaged,
                        law_enforcement_reference, str_filed, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'SAR', 0, ?, ?, ?, 0, ?)""",
                ORG, f["ref"], tid, f["typo"], f["sen"], f["sar"], f["nen"], f["nar"],
                f["status"], f["sev"], f["src"], f["loss"], f["inv"], f["le"],
                f["le_ref"], U_ADMIN)
            bump("fraud.fraud_case", True)

    def fc_id(ref: str):
        cur.execute("SELECT id FROM fraud.fraud_case WHERE organisation_id=? AND external_reference=?",
                    ORG, ref)
        r = cur.fetchone()
        return r[0] if r else None

    # =======================================================================
    # 15. fraud.fraud_case_update  (guard: case_id + update_type + note prefix)
    # =======================================================================
    FCU = [
        ("FR-2026-06-0101", "TRIAGE_DECISION", None, U_AUDITOR,
         "Triaged HIGH following an internal audit finding of coordinated bidding.",
         "صُنّفت بدرجة عالية إثر نتيجة تدقيق داخلي بوجود تنسيق في العطاءات."),
        ("FR-2026-06-0101", "FINDING_RECORDED", None, U_AUDITOR,
         "Forensic review confirmed cover-pricing across the three bids.",
         "أكدت المراجعة الجنائية وجود تسعير صوري عبر العطاءات الثلاثة."),
        ("FR-2026-06-0101", "STATUS_CHANGED", "CONFIRMED", U_COMPLIANCE,
         "Status set to CONFIRMED; referred to the National Anti-Corruption Authority (Nazaha).",
         "حُدّدت الحالة كمؤكدة؛ وأُحيلت إلى الهيئة الوطنية لمكافحة الفساد (نزاهة)."),
        ("FR-2026-06-0102", "TRIAGE_DECISION", None, U_AUDITOR,
         "Triaged HIGH after an internal report of fictitious staff on payroll.",
         "صُنّفت بدرجة عالية بعد بلاغ داخلي بوجود موظفين وهميين في الرواتب."),
        ("FR-2026-06-0102", "FINDING_RECORDED", None, U_AUDITOR,
         "Payroll reconciliation identified three non-existent employee records.",
         "حدّدت مطابقة الرواتب ثلاثة سجلات لموظفين غير موجودين."),
        ("FR-2026-06-0102", "CLOSURE", "CLOSED", U_COMPLIANCE,
         "Partial recovery achieved; referred to the Public Prosecution and closed.",
         "تحقق استرداد جزئي؛ وأُحيلت إلى النيابة العامة وأُغلقت."),
        ("FR-2026-06-0103", "TRIAGE_DECISION", None, U_AUDITOR,
         "Detection-system alert triaged; potential falsified eligibility documents.",
         "فُرز تنبيه النظام؛ احتمال تزوير مستندات أهلية."),
        ("FR-2026-06-0103", "EVIDENCE_ADDED", None, U_AUDITOR,
         "Beneficiary documents collected for forensic verification.",
         "جُمعت مستندات المستفيد للتحقق الجنائي."),
        ("FR-2026-06-0104", "FINDING_RECORDED", None, U_AUDITOR,
         "Audit reconciliation confirmed duplicated per-diem claims.",
         "أكدت مطابقة التدقيق تكرار مطالبات بدل الانتداب."),
        ("FR-2026-06-0104", "STATUS_CHANGED", "CONFIRMED", U_AUDITOR,
         "Status set to CONFIRMED; recovery and disciplinary referral initiated.",
         "حُدّدت الحالة كمؤكدة؛ وبوشر الاسترداد والإحالة التأديبية."),
        ("FR-2026-06-0105", "TRIAGE_DECISION", None, U_AUDITOR,
         "Internal report of split purchase orders triaged for investigation.",
         "فُرز بلاغ داخلي بتجزئة أوامر الشراء للتحقيق."),
        ("FR-2026-06-0106", "TRIAGE_DECISION", None, U_RANALYST,
         "Newly detected asset-diversion report awaiting triage.",
         "بلاغ تحويل أصول مرصود حديثاً بانتظار الفرزة."),
    ]
    for ref, utype, sa, actor, nen, nar in FCU:
        cid = fc_id(ref)
        if cid is None:
            continue
        prefix = nen[:40]
        cur.execute(
            "SELECT id FROM fraud.fraud_case_update "
            "WHERE case_id=? AND update_type=? AND LEFT(note_en,40)=?",
            cid, utype, prefix)
        row = cur.fetchone()
        if row:
            cur.execute(
                """UPDATE fraud.fraud_case_update
                      SET note_en=?, note_ar=?, status_after=?, actor_user_id=?,
                          updated_at=SYSUTCDATETIME(), updated_by=?
                    WHERE id=?""",
                nen, nar, sa, actor, U_ADMIN, row[0])
            bump("fraud.fraud_case_update", False)
        else:
            cur.execute(
                """INSERT INTO fraud.fraud_case_update
                       (organisation_id, case_id, update_type, actor_user_id, actor_name,
                        note_en, note_ar, status_after, created_by)
                   VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                ORG, cid, utype, actor, "Internal Audit / المراجعة الداخلية",
                nen, nar, sa, U_ADMIN)
            bump("fraud.fraud_case_update", True)

    # =======================================================================
    # Report
    # =======================================================================
    SKIPPED = [
        ("ethics.aml_alerts", "Bank transaction-monitoring AML alerts; not a public-authority control."),
        ("ethics.screening_hits", "Sanctions/watchlist screening hits (bank AML construct)."),
        ("aml.sanctions_list", "Sanctions-list master (global FI reference data)."),
        ("aml.sanctions_list_entry", "Sanctioned-party entries (global FI reference data)."),
        ("aml.screening_check", "Name-screening engine output (FI sanctions control)."),
        ("aml.screening_hit", "Screening hits from the FI sanctions engine."),
    ]

    print("\n" + "=" * 70)
    print("SEED SUMMARY (ethics / fraud -- organisation_id = 1)")
    print("=" * 70)
    total_ins = total_upd = 0
    for tbl in sorted(stats):
        ins, upd = stats[tbl]
        total_ins += ins
        total_upd += upd
        print(f"  {tbl:<44} +{ins:<3} ~{upd}")
    print(f"  {'TOTAL':<44} +{total_ins:<3} ~{total_upd}")

    print("\nSKIPPED (out of scope -- financial-institution AML/sanctions controls):")
    for tbl, why in SKIPPED:
        print(f"  - {tbl}: {why}")
    if blockers:
        print("\nBLOCKERS:")
        for b in blockers:
            print(f"  ! {b}")
    else:
        print("\nBLOCKERS: none")

    print("\nVERIFICATION (org 1 row counts after seed):")
    for sch, tbl in [("ethics", "whistleblower_case"), ("ethics", "whistleblower_case_update"),
                     ("ethics", "whistleblower_retaliation_complaint"),
                     ("ethics", "coi_declaration_campaign"), ("ethics", "coi_declaration"),
                     ("ethics", "coi_recusal_event"), ("ethics", "cases"),
                     ("ethics", "disclosures"), ("ethics", "kyc_subjects"),
                     ("fraud", "fraud_typology"), ("fraud", "fraud_case"),
                     ("fraud", "fraud_case_update")]:
        cur.execute(f"SELECT COUNT(*) FROM {sch}.{tbl} WHERE organisation_id=1")
        print(f"  {sch}.{tbl:<40} {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ethics.coi_disclosed_item i "
                "JOIN ethics.coi_declaration d ON i.declaration_id=d.id WHERE d.organisation_id=1")
    print(f"  ethics.coi_disclosed_item (via org1 decls)     {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ethics.case_actions a "
                "JOIN ethics.cases c ON a.case_id=c.id WHERE c.organisation_id=1")
    print(f"  ethics.case_actions (via org1 cases)           {cur.fetchone()[0]}")
    cur.execute("SELECT COUNT(*) FROM ethics.case_links l "
                "JOIN ethics.cases c ON l.case_id=c.id WHERE c.organisation_id=1")
    print(f"  ethics.case_links (via org1 cases)             {cur.fetchone()[0]}")

    conn.close()
    print("\nDone.")


if __name__ == "__main__":
    main()
