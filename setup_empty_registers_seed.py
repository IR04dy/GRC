"""Seed the remaining genuine *registers* that the inventory (task #22) found
empty — the ones that have real parent rows to hang off and are read by the
app's pages/dashboards. Runtime/log/event tables (audit logs, workflow
instances, notification outboxes, exercise runs, integration logs) are left
empty *by design* and are NOT touched here. Superseded parallel tables
(risk.kri_measurement / risk.kri_breach — the active KRI data lives in
risk.kri_readings / risk.kri_breach_events) are also skipped.

Registers seeded (org 1 unless the parent says otherwise):
  admin.committee_member_terms     <- members parsed from committees.members_json
  admin.committee_member_skills    <- one competency per member term
  admin.committee_meeting_papers   <- agenda papers per non-cancelled meeting
  bcm.process_vendor_dependency    <- critical process -> vendor dependency map
  consumer.complaint_update        <- status timeline rebuilt per complaint
  tprm.vendor_bcp_attestation      <- vendor BCP attestations + campaign rollup
  risk_lc.treatment_actions        <- actions under each treatment plan

Idempotent: every INSERT is gated by an existence check, so re-running on a
fresh DB or after a refresh is safe.
"""
from __future__ import annotations
import json
import uuid
import pyodbc
from datetime import datetime, timedelta, date

CONN_STR = (
    "DRIVER={ODBC Driver 17 for SQL Server};"
    "SERVER=localhost,1433;DATABASE=TSS_GRC;UID=sa;PWD=tss.123;"
    "Encrypt=yes;TrustServerCertificate=yes;sendStringParametersAsUnicode=yes;"
)
ORG = 1
NOW = datetime.now()
TODAY = NOW.date()

# committee_type -> (primary GRC competency, Arabic label fragment for notes)
COMMITTEE_SKILL = {
    "BOARD": "INDUSTRY",
    "RISK_COMMITTEE": "RISK_MGMT",
    "AUDIT_COMMITTEE": "FINANCIAL_EXPERT",
    "BCM_COMMITTEE": "BCM_EXPERT",
    "TECH_COMMITTEE": "DIGITAL",
    "COMPLIANCE_COMMITTEE": "AML_EXPERT",
}


def seed_committee_member_terms(cur):
    cur.execute("SELECT id, committee_type, members_json FROM admin.committees "
                "WHERE organisation_id = ? AND is_deleted = 0 ORDER BY id", ORG)
    committees = cur.fetchall()
    n = 0
    for cid, ctype, members_json in committees:
        if not members_json:
            continue
        try:
            members = json.loads(members_json)
        except (ValueError, TypeError):
            continue
        legacy = (ctype == "TECH_COMMITTEE")
        for idx, m in enumerate(members):
            name = (m.get("name") or "").strip()
            if not name:
                continue
            role = (m.get("role") or "Member").strip()
            voting = 1 if m.get("votingMember") else 0
            independent = 1 if "independent" in role.lower() else 0
            appointed = date(2023, 3, 1) + timedelta(days=idx * 7)
            term_starts = appointed
            term_ends = date(2026, 3, 1) if legacy else date(2027, 3, 1)
            renewal = "EXPIRED" if legacy else "ACTIVE"
            cur.execute(
                "SELECT 1 FROM admin.committee_member_terms "
                "WHERE organisation_id = ? AND committee_id = ? AND member_name = ? AND term_number = 1",
                ORG, cid, name)
            if cur.fetchone():
                continue
            cur.execute(
                "INSERT INTO admin.committee_member_terms "
                "(organisation_id, committee_id, member_name, role, is_voting_member, is_independent, "
                " independence_basis_en, appointed_at, appointed_by_reso, term_starts_at, term_ends_at, "
                " term_number, renewal_status, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, ?, ?)",
                ORG, cid, name, role, voting, independent,
                ("No material relationship with the bank in the last 3 years (CMA CG Reg. Art. 20)."
                 if independent else None),
                appointed, f"RES-{appointed.year}-{cid:02d}", term_starts, term_ends, renewal, NOW)
            n += 1
    return n


def seed_committee_member_skills(cur):
    cur.execute("SELECT id, committee_id, member_name, role, is_independent "
                "FROM admin.committee_member_terms WHERE organisation_id = ? ORDER BY id", ORG)
    terms = cur.fetchall()
    ctype_by_committee = {}
    cur.execute("SELECT id, committee_type FROM admin.committees WHERE organisation_id = ?", ORG)
    for cid, ctype in cur.fetchall():
        ctype_by_committee[cid] = ctype
    n = 0
    for term_id, cid, name, role, independent in terms:
        dimension = COMMITTEE_SKILL.get(ctype_by_committee.get(cid), "RISK_MGMT")
        is_chair = any(k in (role or "").lower() for k in ("chair", "chairman", "chairperson"))
        level = "EXPERT" if (is_chair or independent) else "INTERMEDIATE"
        cur.execute(
            "SELECT 1 FROM admin.committee_member_skills "
            "WHERE organisation_id = ? AND committee_id = ? AND member_name = ? AND skill_dimension = ?",
            ORG, cid, name, dimension)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO admin.committee_member_skills "
            "(organisation_id, committee_id, member_term_id, member_name, skill_dimension, skill_level, "
            " evidence_en, years_experience, last_reviewed_at, created_at) "
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
            ORG, cid, term_id, name, dimension, level,
            f"Self-declared + verified competency in {dimension.replace('_', ' ').title()}.",
            8 + (term_id % 12), TODAY - timedelta(days=120), NOW)
        n += 1
    return n


def seed_committee_meeting_papers(cur):
    cur.execute("SELECT id, committee_code, meeting_code, status FROM admin.committee_meetings "
                "WHERE organisation_id = ? AND is_deleted = 0 ORDER BY id", ORG)
    meetings = cur.fetchall()
    # (suffix, title_en, title_ar, confidentiality, seq, author_role)
    papers = [
        ("P1", "Agenda & minutes of the previous meeting",
         "جدول الأعمال ومحضر الاجتماع السابق", "INTERNAL", 1, "Company Secretary"),
        ("P2", "Management report & key metrics pack",
         "تقرير الإدارة وحزمة المؤشرات الرئيسية", "CONFIDENTIAL", 2, "Chief Executive Officer"),
        ("P3", "Matters arising & action-item tracker",
         "المسائل المستجدة ومتابعة بنود الإجراءات", "INTERNAL", 3, "Committee Secretary"),
    ]
    n = 0
    for mid, ccode, mcode, status in meetings:
        if status == "CANCELLED":
            continue
        for suffix, t_en, t_ar, conf, seq, author_role in papers:
            paper_code = f"{mcode}-{suffix}"
            cur.execute("SELECT 1 FROM admin.committee_meeting_papers "
                        "WHERE organisation_id = ? AND paper_code = ?", ORG, paper_code)
            if cur.fetchone():
                continue
            late = 1 if (suffix == "P2" and mid % 4 == 0) else 0
            cur.execute(
                "INSERT INTO admin.committee_meeting_papers "
                "(organisation_id, meeting_id, paper_code, title_en, title_ar, agenda_item_seq, "
                " author_name, author_role, circulated_at, confidentiality, late_paper, late_paper_reason, created_at) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                ORG, mid, paper_code, t_en, t_ar, seq,
                f"{ccode} Secretariat", author_role, NOW - timedelta(days=6),
                conf, late, ("Data dependency on month-end close" if late else None), NOW)
            n += 1
    return n


def seed_process_vendor_dependency(cur):
    cur.execute("SELECT id, process_name_en FROM bcm.critical_processes "
                "WHERE organisation_id = ? AND is_deleted = 0", ORG)
    procs = {row[1]: row[0] for row in cur.fetchall()}
    cur.execute("SELECT id, vendor_name FROM tprm.vendors WHERE organisation_id = ? AND is_deleted = 0", ORG)
    vendors = {row[1]: row[0] for row in cur.fetchall()}

    def pid(name):  # match by leading words
        for k, v in procs.items():
            if k.lower().startswith(name.lower()):
                return v
        return None

    # (process prefix, vendor name, type, strength, note_en, note_ar)
    edges = [
        ("Core Banking",        "STC Solutions",        "REQUIRED",    "TIGHT",
         "Core banking hosting & managed operations.", "استضافة النظام البنكي الأساسي وتشغيله المُدار."),
        ("Core Banking",        "SAP SE",               "REQUIRED",    "TIGHT",
         "GL / sub-ledger integration.", "تكامل دفتر الأستاذ العام والفرعي."),
        ("Customer Onboarding", "Microsoft Azure",      "REQUIRED",    "TIGHT",
         "Cloud identity & onboarding workloads.", "أحمال الهوية والإلحاق السحابية."),
        ("Customer Onboarding", "Microsoft Saudi Arabia","RECOMMENDED","LOOSE",
         "Local support & data-residency assurance.", "الدعم المحلي وضمان إقامة البيانات."),
        ("Payroll",             "SAP SE",               "REQUIRED",    "TIGHT",
         "Payroll engine (GOSI / Mudad feeds).", "محرك الرواتب (تغذية التأمينات ومُدد)."),
        ("Regulatory Reporting","Deloitte & Touche",    "RECOMMENDED", "LOOSE",
         "Regulatory-reporting assurance & review.", "ضمان ومراجعة التقارير التنظيمية."),
        ("Regulatory Reporting","KPMG Saudi Arabia",    "FALLBACK",    "TRANSIENT",
         "Backup assurance provider.", "مزود ضمان احتياطي."),
        ("IT Infrastructure",   "Cisco Systems",        "REQUIRED",    "TIGHT",
         "Network & data-centre fabric.", "شبكة ونسيج مركز البيانات."),
        ("IT Infrastructure",   "Amazon Web Services KSA","REQUIRED",  "TIGHT",
         "KSA-region cloud infrastructure.", "البنية السحابية في منطقة المملكة."),
        ("IT Infrastructure",   "Tenable",              "RECOMMENDED", "LOOSE",
         "Vulnerability management platform.", "منصة إدارة الثغرات."),
        ("Retail Payments",     "STC Solutions",        "REQUIRED",    "TIGHT",
         "SARIE / SADAD / mada connectivity.", "الربط مع سريع وسداد ومدى."),
        ("Retail Payments",     "Tata Consultancy Services","RECOMMENDED","LOOSE",
         "Payments app maintenance & support.", "صيانة ودعم تطبيق المدفوعات."),
    ]
    n = 0
    for pproc, vname, dtype, dstr, note_en, note_ar in edges:
        p = pid(pproc)
        v = vendors.get(vname)
        if not p or not v:
            continue
        cur.execute("SELECT 1 FROM bcm.process_vendor_dependency "
                    "WHERE organisation_id = ? AND business_process_id = ? AND vendor_id = ? AND is_deleted = 0",
                    ORG, p, v)
        if cur.fetchone():
            continue
        cur.execute(
            "INSERT INTO bcm.process_vendor_dependency "
            "(organisation_id, external_id, business_process_id, vendor_id, dependency_type, "
            " dependency_strength, notes_en, notes_ar, created_at, is_deleted) "
            "VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, ?, 0)",
            ORG, p, v, dtype, dstr, note_en, note_ar, NOW)
        n += 1
    return n


# canonical predecessor path to each complaint status (creation = RECEIVED)
COMPLAINT_PATH = {
    "RECEIVED": ["RECEIVED"],
    "ACKNOWLEDGED": ["RECEIVED", "ACKNOWLEDGED"],
    "UNDER_INVESTIGATION": ["RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION"],
    "AWAITING_CUSTOMER": ["RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "AWAITING_CUSTOMER"],
    "RESOLVED": ["RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "RESOLVED"],
    "ESCALATED_TO_SAMA": ["RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "ESCALATED_TO_SAMA"],
    "CLOSED": ["RECEIVED", "ACKNOWLEDGED", "UNDER_INVESTIGATION", "RESOLVED", "CLOSED"],
}
COMPLAINT_NOTE = {
    "ACKNOWLEDGED": ("Complaint acknowledged; SLA clock started.",
                     "تم استلام الشكوى وبدء احتساب مهلة الخدمة.", 1),
    "UNDER_INVESTIGATION": ("Assigned to investigator; root-cause analysis started.",
                            "أُسندت للمحقق وبدأ تحليل السبب الجذري.", 0),
    "AWAITING_CUSTOMER": ("Additional information requested from the customer.",
                          "طُلبت معلومات إضافية من العميل.", 1),
    "RESOLVED": ("Resolution communicated to the customer.",
                 "تم إبلاغ العميل بالتسوية.", 1),
    "ESCALATED_TO_SAMA": ("Escalated to SAMA per consumer-protection rules.",
                          "تم التصعيد إلى البنك المركزي وفق قواعد حماية العميل.", 0),
    "CLOSED": ("Complaint closed.", "أُغلقت الشكوى.", 1),
}


def seed_complaint_update(cur):
    cur.execute("SELECT id, status, submitted_on FROM consumer.complaint "
                "WHERE is_deleted = 0 ORDER BY id")
    rows = cur.fetchall()
    n = 0
    for cid, status, submitted_on in rows:
        path = COMPLAINT_PATH.get(status)
        if not path:
            continue
        base = submitted_on or NOW
        for i in range(1, len(path)):
            frm, to = path[i - 1], path[i]
            note_en, note_ar, visible = COMPLAINT_NOTE.get(to, ("Status updated.", "تم تحديث الحالة.", 0))
            cur.execute("SELECT 1 FROM consumer.complaint_update "
                        "WHERE complaint_id = ? AND update_type = 'STATUS_CHANGE' AND to_status = ? AND is_deleted = 0",
                        cid, to)
            if cur.fetchone():
                continue
            occurred = base + timedelta(days=i, hours=(cid * 3) % 12)
            cur.execute(
                "INSERT INTO consumer.complaint_update "
                "(complaint_id, update_type, from_status, to_status, note_en, note_ar, actor, "
                " customer_visible, occurred_on, created_at, is_deleted) "
                "VALUES (?, 'STATUS_CHANGE', ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                cid, frm, to, note_en, note_ar, "Complaints Officer", visible, occurred, NOW)
            n += 1
    return n


def seed_vendor_bcp_attestation(cur):
    cur.execute("SELECT TOP 1 id FROM tprm.vendor_bcp_campaign WHERE organisation_id = ? AND is_deleted = 0 ORDER BY id", ORG)
    row = cur.fetchone()
    if not row:
        return 0, False
    campaign_id = int(row[0])
    cur.execute("SELECT id, vendor_name FROM tprm.vendors WHERE organisation_id = ? AND is_deleted = 0", ORG)
    vendors = {r[1]: r[0] for r in cur.fetchall()}
    # (vendor, status, rto_attested, mtpd_attested, contractual_rto, contractual_mtpd, tested_bcp)
    plan = [
        ("STC Solutions",             "ACCEPTED",       4,  8,  4,  8, True),
        ("Amazon Web Services KSA",   "ACCEPTED",       2,  4,  4,  8, True),
        ("Microsoft Azure",           "ACCEPTED",       2,  4,  4,  8, True),
        ("SAP SE",                    "SUBMITTED",      8, 24,  6, 12, True),
        ("Cisco Systems",             "SUBMITTED",     12, 24, 12, 24, True),
        ("Tata Consultancy Services", "OVERDUE",     None, None, 8, 24, None),
        ("Tenable",                   "INVITED",     None, None, 24, 72, None),
        ("Aramex",                    "NA_OUT_OF_SCOPE", None, None, None, None, None),
    ]
    counts = {"INVITED": 0, "SUBMITTED": 0, "ACCEPTED": 0, "OVERDUE": 0, "NA_OUT_OF_SCOPE": 0}
    n = 0
    for vname, status, rto_a, mtpd_a, c_rto, c_mtpd, tested in plan:
        v = vendors.get(vname)
        if not v:
            continue
        counts[status] = counts.get(status, 0) + 1
        cur.execute("SELECT 1 FROM tprm.vendor_bcp_attestation "
                    "WHERE organisation_id = ? AND campaign_id = ? AND vendor_id = ? AND is_deleted = 0",
                    ORG, campaign_id, v)
        if cur.fetchone():
            continue
        invited_at = NOW - timedelta(days=21)
        submitted_at = (NOW - timedelta(days=10)) if status in ("SUBMITTED", "ACCEPTED") else None
        reviewed = status in ("ACCEPTED",)
        rto_breach = 1 if (rto_a is not None and c_rto is not None and rto_a > c_rto) else 0
        mtpd_breach = 1 if (mtpd_a is not None and c_mtpd is not None and mtpd_a > c_mtpd) else 0
        overdue_at = (NOW - timedelta(days=2)) if status == "OVERDUE" else None
        cur.execute(
            "INSERT INTO tprm.vendor_bcp_attestation "
            "(organisation_id, external_id, campaign_id, vendor_id, submission_token, invited_at, overdue_at, "
            " submitted_at, submitted_by_contact_name, rto_hours_attested, mtpd_hours_attested, "
            " contractual_rto_hours, contractual_mtpd_hours, rto_breach, mtpd_breach, has_tested_bcp, "
            " last_bcp_test_date, status, reviewed_by_name, reviewed_at, created_at, is_deleted) "
            "VALUES (?, NEWID(), ?, ?, NEWID(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
            ORG, campaign_id, v, invited_at, overdue_at, submitted_at,
            ("Vendor BCM Coordinator" if submitted_at else None),
            rto_a, mtpd_a, c_rto, c_mtpd, rto_breach, mtpd_breach,
            (1 if tested else 0) if tested is not None else None,
            (TODAY - timedelta(days=90)) if tested else None,
            status, ("TPRM Reviewer" if reviewed else None),
            (NOW - timedelta(days=5)) if reviewed else None, NOW)
        n += 1
    # roll the campaign up to OPEN with aggregate counters
    total_invited = sum(counts.values())
    cur.execute(
        "UPDATE tprm.vendor_bcp_campaign SET status = 'OPEN', opened_at = COALESCE(opened_at, ?), "
        " total_invited = ?, total_submitted = ?, total_accepted = ?, total_overdue = ?, total_na = ?, "
        " updated_at = ? WHERE id = ?",
        NOW - timedelta(days=21), total_invited,
        counts["SUBMITTED"] + counts["ACCEPTED"], counts["ACCEPTED"], counts["OVERDUE"],
        counts["NA_OUT_OF_SCOPE"], NOW, campaign_id)
    return n, True


def seed_treatment_actions(cur):
    cur.execute("SELECT id, organisation_id, strategy, status FROM risk_lc.treatment_plans WHERE is_deleted = 0 ORDER BY id")
    plans = cur.fetchall()
    # per-plan actions: (title_en, title_ar, owner_role, status, reduction, months_out)
    # expected_risk_reduction is a 0.000-1.000 ratio (decimal precision 4, scale 3)
    templ = {
        "MITIGATE": [
            ("Deploy compensating control & monitoring", "نشر ضابط تعويضي ومراقبة", "Control Owner", "IN_PROGRESS", 0.30, 2),
            ("Independent effectiveness review", "مراجعة مستقلة للفعالية", "Internal Audit", "DRAFT", 0.15, 5),
        ],
        "TRANSFER": [
            ("Place / renew insurance cover", "إبرام أو تجديد التغطية التأمينية", "Treasury", "APPROVED", 0.40, 1),
        ],
        "AVOID": [
            ("Decommission the exposed activity", "إيقاف النشاط المُعرَّض", "Business Owner", "DRAFT", 0.60, 3),
        ],
        "ACCEPT": [
            ("Document formal risk acceptance", "توثيق قبول المخاطر الرسمي", "Risk Owner", "SUBMITTED", 0.0, 1),
        ],
    }
    n = 0
    for pid_, org, strategy, pstatus in plans:
        actions = templ.get(strategy, templ["MITIGATE"])
        for k, (t_en, t_ar, owner, astatus, reduction, months) in enumerate(actions, start=1):
            ext = f"TA-{pid_:03d}-{k}"
            # finished plans -> mark actions completed
            status = "COMPLETED" if pstatus == "COMPLETED" else astatus
            cur.execute("SELECT 1 FROM risk_lc.treatment_actions WHERE external_id = ?", ext)
            if cur.fetchone():
                continue
            cur.execute(
                "INSERT INTO risk_lc.treatment_actions "
                "(external_id, organisation_id, plan_id, title_en, title_ar, owner_role, target_date, "
                " status, expected_risk_reduction, completed_at, evidence_ref, is_deleted) "
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)",
                ext, org, pid_, t_en, t_ar, owner,
                TODAY + timedelta(days=30 * months), status, reduction,
                (NOW - timedelta(days=7)) if status == "COMPLETED" else None,
                (f"EVD-{ext}" if status == "COMPLETED" else None))
            n += 1
    return n


def main():
    print("Seed remaining empty *registers* (task #22 — realistic Saudi banking data)")
    with pyodbc.connect(CONN_STR, autocommit=False) as conn:
        cur = conn.cursor()
        terms = seed_committee_member_terms(cur)
        skills = seed_committee_member_skills(cur)
        papers = seed_committee_meeting_papers(cur)
        deps = seed_process_vendor_dependency(cur)
        cupd = seed_complaint_update(cur)
        bcp, camp = seed_vendor_bcp_attestation(cur)
        tact = seed_treatment_actions(cur)
        conn.commit()
        print(f"  [+] Committee member terms   : {terms}")
        print(f"  [+] Committee member skills  : {skills}")
        print(f"  [+] Committee meeting papers : {papers}")
        print(f"  [+] Process-vendor deps      : {deps}")
        print(f"  [+] Complaint updates        : {cupd}")
        print(f"  [+] Vendor BCP attestations  : {bcp} (campaign rolled to OPEN: {camp})")
        print(f"  [+] Treatment actions        : {tact}")
        print("Done.")


if __name__ == "__main__":
    main()
