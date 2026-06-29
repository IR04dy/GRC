# -*- coding: utf-8 -*-
"""Seed government-authority Business Continuity (BCM) content for organisation_id = 1.

Companion to seed_gov_committees.py / seed_gov_hierarchy.py. Targets the default
tenant (organisation_id = 1) — re-framed in the current brief as a Saudi GENERAL
AUTHORITY (هيئة عامة) headed by a President — and fills the EMPTY runtime/lifecycle
BCM tables with NCEMA / DGA / NDMO / NCA-aligned continuity content for critical
GOVERNMENT services (licensing, inspection, beneficiary services, the national data
platform and the digital-government portal).

Hard safety: INSERT / idempotent-UPSERT ONLY. No DELETE / TRUNCATE / DROP / ALTER.
organisation_id = 1 only. Only the assigned schemas (bcm, bcm_lc, opres, rrp) are
touched, and within them only the empty tables listed below — the rich existing
registers (bcm.exercises, bcm.bcp_plans, bcm.critical_processes, rrp.rrp_cycle, …)
are used as FK PARENTS and are never modified.

Existing org-1 data in bcm / opres / rrp is BANKING-themed (the tenant was first
modelled as a Saudi bank). Those rows are left exactly as-is. Where this script
attaches to banking parents (exercise runs over existing exercises, MTPD findings
over existing process↔vendor edges, the rrp_event audit trail over the two
existing SAMA D-SIB cycles) it does NOT invent a new bank narrative — it either
re-tells the run for the Authority or only records lifecycle events for rows that
already exist. The brand-new bcm_lc lifecycle layer (bia_records / recovery_strategies
/ drills), which is empty for org 1, is where the pure government-service BIA layer
is added.

Tables FILLED (all empty for org 1 before this script):
    bcm.exercise_run                  (4)   exercise runs over existing exercises 4,6,10,3
    bcm.exercise_run_inject           (9)   timed injects on the 3 completed runs
    bcm.exercise_run_response         (9)   participant responses (decision quality / AAR)
    bcm.exercise_observer_assignment  (6)   2 observers per completed run
    bcm.exercise_observation          (10)  observer findings feeding the AAR
    bcm.mtpd_inheritance_assessment   (2)   annual MTPD-inheritance roll-ups (2025, 2026)
    bcm.mtpd_inheritance_finding      (8)   process↔provider MTPD-inheritance findings
    bcm.bcp_binder_export             (4)   controlled BCP/DRP binder exports
    bcm_lc.bia_records                (8)   BIA for 8 critical government services
    bcm_lc.recovery_strategies        (8)   recovery strategy per critical BIA
    bcm_lc.drills                     (5)   continuity drills in the lifecycle module
    rrp.rrp_event                     (14)  audit-trail timeline over existing cycles 3,4

Tables SKIPPED (already populated, shared catalogs, or bank-only constructs not
mapping to a public authority) are reported by the script at the end.

Idempotent: every INSERT is guarded by a SELECT on a natural key (a code / unique
business key) so a second run inserts 0 rows. row_version is a SQL Server rowversion
(timestamp) and is never written. created_at / is_deleted use DB defaults where they
exist; external_id uniqueidentifier columns default to NEWID().

    Usage:  python seed_gov_bcm.py
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

# Existing org-1 FK parents (verified by discovery against the live DB).
EX_PANDEMIC = 4    # bcm.exercises "Pandemic continuity tabletop"        COMPLETED
EX_CALLTREE = 6    # bcm.exercises "Crisis call-tree & staff notification" COMPLETED
EX_CLOUD = 10      # bcm.exercises "Third-party cloud outage tabletop"    COMPLETED
EX_Q2 = 3          # bcm.exercises "Tabletop · Q2 2026"                   SCHEDULED

BCP_CORE = 7       # bcm.bcp_plans "Core Banking BCP"  PUBLISHED  -> retold as Core gov-service BCP
DRP_CORE = 10      # bcm.bcp_plans "Core Banking DRP"  PUBLISHED  -> retold as Core gov-service DRP

CYCLE_FY2025 = 3   # rrp.rrp_cycle CLOSED
CYCLE_FY2026 = 4   # rrp.rrp_cycle IN_PREPARATION

# iam.users org 1
U_ADMIN, U_RMANAGER, U_COMPLIANCE, U_AUDITOR, U_RANALYST, U_VIEWER = 1, 2, 3, 4, 5, 6

summary: dict[str, int] = {}
skipped: list[tuple[str, str]] = []
blockers: list[str] = []


def note_skip(table: str, reason: str) -> None:
    skipped.append((table, reason))


# ───────────────────────── helpers ──────────────────────────────────────────
def exists(cur, sql: str, *params) -> bool:
    cur.execute(sql, *params)
    return cur.fetchone() is not None


def bump(table: str, n: int = 1) -> None:
    summary[table] = summary.get(table, 0) + n


# ═════════════════════════ 1. EXERCISE RUNS ══════════════════════════════════
def seed_exercise_runs(cur) -> dict[str, int]:
    """4 runs over existing org-1 exercises, retold for the Authority. Returns
    {run_code: id} for downstream injects/observers/observations."""
    tab = "bcm.exercise_run"
    runs = [
        dict(code="RUN-2026-0001", exercise_id=EX_PANDEMIC, status="COMPLETED",
             channel="HYBRID", facilitator=U_RMANAGER, participants=22,
             started="2026-02-11T08:00:00", ended="2026-02-11T12:30:00",
             notes_en="Pandemic-continuity tabletop for beneficiary services: 40% staff "
                      "absenteeism scenario aligned with NCEMA pandemic-continuity guidance. "
                      "Remote-service continuity mode activated within the beneficiary-service RTO.",
             notes_ar="تمرين طاولة لاستمرارية الخدمات المقدمة للمستفيدين في ظل جائحة وغياب 40% من "
                      "الموظفين، بما يتوافق مع توجيهات المركز الوطني لإدارة الكوارث (نكيما). تم تفعيل "
                      "نمط استمرارية الخدمة عن بُعد ضمن زمن التعافي المستهدف لخدمات المستفيدين."),
        dict(code="RUN-2026-0002", exercise_id=EX_CALLTREE, status="COMPLETED",
             channel="VIRTUAL_MEET", facilitator=U_RMANAGER, participants=14,
             started="2026-03-04T13:00:00", ended="2026-03-04T14:15:00",
             notes_en="Crisis call-tree and staff-notification test: validated cascade "
                      "notification to the crisis-management team and failover from the "
                      "primary SMS gateway to a secondary channel.",
             notes_ar="اختبار شجرة الاتصال في الأزمات وإشعار الموظفين: تم التحقق من تتابع إشعار "
                      "فريق إدارة الأزمات والتحول من بوابة الرسائل النصية الأساسية إلى قناة بديلة."),
        dict(code="RUN-2026-0003", exercise_id=EX_CLOUD, status="COMPLETED",
             channel="IN_PERSON", facilitator=U_RMANAGER, participants=18,
             started="2026-04-22T09:00:00", ended="2026-04-22T12:00:00",
             notes_en="Third-party cloud-outage tabletop: degradation of the national digital "
                      "platform hosted on a hyperscaler region. Regulator notification (DGA/NDMO) "
                      "and failover to an alternate region were rehearsed.",
             notes_ar="تمرين طاولة لانقطاع مزود الحوسبة السحابية الخارجي: تدهور المنصة الوطنية "
                      "الرقمية المستضافة على منطقة مزود سحابي. تم التدرب على إشعار الجهة المنظمة "
                      "(هيئة الحكومة الرقمية / مكتب إدارة البيانات الوطنية) والتحول إلى منطقة بديلة."),
        dict(code="RUN-2026-0004", exercise_id=EX_Q2, status="SCHEDULED",
             channel="HYBRID", facilitator=U_RMANAGER, participants=0,
             started=None, ended=None,
             notes_en="Q2 2026 continuity tabletop (scheduled): planned exercise of the licensing "
                      "and inspection services failover to the alternate government data centre.",
             notes_ar="تمرين طاولة لاستمرارية الأعمال للربع الثاني 2026 (مجدول): تمرين مخطط لتحويل "
                      "خدمات الترخيص والتفتيش إلى مركز البيانات الحكومي البديل."),
    ]
    ids: dict[str, int] = {}
    for r in runs:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND run_code=?",
                      ORG, r["code"]):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, exercise_id, run_code, facilitator_user_id,
                     started_at, ended_at, status, participant_count, channel,
                     facilitator_notes_en, facilitator_notes_ar, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, r["exercise_id"], r["code"], r["facilitator"],
                r["started"], r["ended"], r["status"], r["participants"], r["channel"],
                r["notes_en"], r["notes_ar"], U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {r['code']}")
        cur.execute(f"SELECT id FROM {tab} WHERE organisation_id=? AND run_code=?", ORG, r["code"])
        ids[r["code"]] = cur.fetchone()[0]
    return ids


# ═════════════════════════ 2. EXERCISE RUN INJECTS ═══════════════════════════
def seed_injects(cur, run_ids: dict[str, int]) -> dict[tuple[str, int], int]:
    """Timed injects on the 3 completed runs. Natural key = (run_id, offset, type).
    Returns {(run_code, offset): inject_id} for responses/observations."""
    tab = "bcm.exercise_run_inject"
    injects = [
        # Pandemic run
        ("RUN-2026-0001", 0, "INFORMATION",
         "HR reports 40% of beneficiary-service staff absent due to a flu outbreak; call-centre staffing at 60%.",
         "تفيد الموارد البشرية بغياب 40% من موظفي خدمة المستفيدين بسبب تفشٍّ للإنفلونزا؛ تشغيل مركز الاتصال عند 60%.",
         "Assess service-level impact and brief the crisis-management team.",
         "تقييم الأثر على مستوى الخدمة وإحاطة فريق إدارة الأزمات."),
        ("RUN-2026-0001", 30, "ESCALATION",
         "Beneficiary call-centre wait times exceed 25 minutes; complaints escalating on the unified channel.",
         "تجاوز زمن انتظار مركز اتصال المستفيدين 25 دقيقة؛ تصاعد الشكاوى عبر القناة الموحدة.",
         "Escalate to the BCM steering committee and consider remote-service mode.",
         "التصعيد إلى لجنة استمرارية الأعمال والنظر في تفعيل نمط الخدمة عن بُعد."),
        ("RUN-2026-0001", 60, "DECISION",
         "Decision required: activate remote-service continuity mode for beneficiary services?",
         "قرار مطلوب: تفعيل نمط استمرارية الخدمة عن بُعد لخدمات المستفيدين؟",
         "Approve activation and notify NCEMA per the continuity plan.",
         "اعتماد التفعيل وإشعار المركز الوطني لإدارة الكوارث وفق خطة الاستمرارية."),
        # Call-tree run
        ("RUN-2026-0002", 0, "INFORMATION",
         "Primary SMS gateway is unreachable; automated crisis notifications are failing to dispatch.",
         "بوابة الرسائل النصية الأساسية غير متاحة؛ تعذّر إرسال إشعارات الأزمات الآلية.",
         "Confirm the outage and prepare the secondary channel.",
         "تأكيد الانقطاع وتجهيز القناة البديلة."),
        ("RUN-2026-0002", 30, "DECISION",
         "Decision required: switch crisis notifications to the secondary channel (voice call-tree + email)?",
         "قرار مطلوب: تحويل إشعارات الأزمات إلى القناة البديلة (شجرة الاتصال الصوتي + البريد)؟",
         "Approve channel switch and confirm receipt across the call-tree.",
         "اعتماد تحويل القناة وتأكيد الاستلام عبر شجرة الاتصال."),
        # Cloud-outage run
        ("RUN-2026-0003", 0, "REGULATOR",
         "The national digital platform is degraded after a hyperscaler regional outage; SLA at risk.",
         "تدهور المنصة الوطنية الرقمية إثر انقطاع إقليمي لدى مزود الحوسبة السحابية؛ اتفاقية مستوى الخدمة معرضة للخطر.",
         "Prepare and dispatch the DGA/NDMO regulatory notification.",
         "تجهيز وإرسال الإشعار التنظيمي لهيئة الحكومة الرقمية / مكتب إدارة البيانات الوطنية."),
        ("RUN-2026-0003", 30, "MEDIA",
         "Citizen complaints about e-services downtime are trending on X (Twitter); media enquiries received.",
         "تصاعد شكاوى المواطنين حول تعطّل الخدمات الإلكترونية على منصة إكس؛ ورود استفسارات إعلامية.",
         "Activate the approved holding statement via Corporate Communication.",
         "تفعيل البيان التمهيدي المعتمد عبر الاتصال المؤسسي."),
        ("RUN-2026-0003", 60, "DECISION",
         "Decision required: fail the national platform over to the alternate cloud region now?",
         "قرار مطلوب: تحويل المنصة الوطنية إلى منطقة الحوسبة السحابية البديلة الآن؟",
         "Approve failover and validate data integrity post-cutover.",
         "اعتماد التحويل والتحقق من سلامة البيانات بعد التبديل."),
        ("RUN-2026-0003", 90, "INFORMATION",
         "Failover complete; the national platform is serving from the alternate region with full data sync.",
         "اكتمل التحويل؛ المنصة الوطنية تعمل من المنطقة البديلة مع مزامنة كاملة للبيانات.",
         "Confirm service restoration and log the recovery time for the AAR.",
         "تأكيد استعادة الخدمة وتسجيل زمن التعافي في تقرير ما بعد التمرين."),
    ]
    ids: dict[tuple[str, int], int] = {}
    for code, off, ityp, ten, tar, aen, aar in injects:
        run_id = run_ids[code]
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND exercise_run_id=? "
                           f"AND planned_time_offset_minutes=? AND inject_type=?",
                      ORG, run_id, off, ityp):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, exercise_run_id, planned_time_offset_minutes,
                     actual_dispatched_at, inject_type, inject_text_en, inject_text_ar,
                     expected_action_en, expected_action_ar, status, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, run_id, off, None, ityp, ten, tar, aen, aar,
                "COMPLETED", U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {code} +{off}m {ityp}")
        cur.execute(f"SELECT id FROM {tab} WHERE organisation_id=? AND exercise_run_id=? "
                    f"AND planned_time_offset_minutes=? AND inject_type=?", ORG, run_id, off, ityp)
        ids[(code, off)] = cur.fetchone()[0]
    return ids


# ═════════════════════════ 3. INJECT RESPONSES ═══════════════════════════════
def seed_responses(cur, inject_ids: dict[tuple[str, int], int]) -> None:
    """One participant response per inject. Natural key = (run_inject_id, participant)."""
    tab = "bcm.exercise_run_response"
    rsp = [
        ("RUN-2026-0001", 0, U_RANALYST, 300, 88,
         "Confirmed 40% absenteeism; modelled call-centre throughput drop and flagged beneficiary-service SLA breach risk.",
         "تم تأكيد غياب 40%؛ نُمذجت خفض إنتاجية مركز الاتصال ورُصد خطر الإخلال باتفاقية مستوى خدمة المستفيدين.",
         "Brief crisis team; recommend remote-service mode.",
         "إحاطة فريق الأزمات والتوصية بنمط الخدمة عن بُعد.",
         "Timely and quantified; good situational awareness.",
         "استجابة سريعة ومُكمَّمة؛ وعي ظرفي جيد."),
        ("RUN-2026-0001", 30, U_COMPLIANCE, 420, 84,
         "Escalated to the BCM steering committee; queued the NCEMA notification template.",
         "تم التصعيد إلى لجنة استمرارية الأعمال؛ وتجهيز نموذج إشعار المركز الوطني لإدارة الكوارث.",
         "Escalate; prepare NCEMA notification.",
         "التصعيد وتجهيز إشعار المركز الوطني لإدارة الكوارث.",
         "Correct escalation path; notification template needs the latest version.",
         "مسار تصعيد صحيح؛ يلزم استخدام أحدث نسخة من نموذج الإشعار."),
        ("RUN-2026-0001", 60, U_RMANAGER, 480, 92,
         "Activated remote beneficiary-service continuity mode within 35 minutes — within the 4-hour RTO.",
         "تم تفعيل نمط استمرارية خدمة المستفيدين عن بُعد خلال 35 دقيقة — ضمن زمن التعافي المستهدف البالغ 4 ساعات.",
         "Activate remote-service mode; notify NCEMA.",
         "تفعيل نمط الخدمة عن بُعد وإشعار المركز الوطني لإدارة الكوارث.",
         "Decisive; RTO met with margin.",
         "قرار حاسم؛ تحقق زمن التعافي بهامش جيد."),
        ("RUN-2026-0002", 0, U_RANALYST, 180, 86,
         "Confirmed primary SMS gateway outage from the monitoring console; secondary channel readied.",
         "تم تأكيد انقطاع بوابة الرسائل النصية الأساسية من لوحة المراقبة؛ وتجهيز القناة البديلة.",
         "Confirm outage; ready secondary channel.",
         "تأكيد الانقطاع وتجهيز القناة البديلة.",
         "Fast verification.",
         "تحقق سريع."),
        ("RUN-2026-0002", 30, U_RMANAGER, 240, 90,
         "Switched crisis notifications to the voice call-tree and email; 100% receipt confirmed across team leads.",
         "تم تحويل إشعارات الأزمات إلى شجرة الاتصال الصوتي والبريد؛ وتأكيد الاستلام بنسبة 100% بين قادة الفرق.",
         "Switch channel; confirm receipt.",
         "تحويل القناة وتأكيد الاستلام.",
         "Clean fallback; full receipt confirmation.",
         "تحول سلس؛ تأكيد استلام كامل."),
        ("RUN-2026-0003", 0, U_COMPLIANCE, 540, 80,
         "Drafted the DGA/NDMO regulatory notification; awaited sign-off before dispatch.",
         "تمت صياغة الإشعار التنظيمي لهيئة الحكومة الرقمية / مكتب إدارة البيانات الوطنية؛ بانتظار الاعتماد قبل الإرسال.",
         "Prepare regulator notification.",
         "تجهيز إشعار الجهة المنظمة.",
         "Notification accurate but dispatch was delayed pending approval authority.",
         "الإشعار دقيق لكن تأخر الإرسال بانتظار صلاحية الاعتماد."),
        ("RUN-2026-0003", 30, U_VIEWER, 360, 72,
         "Forwarded the media holding statement to Corporate Communication for publication.",
         "تم تحويل البيان الإعلامي التمهيدي إلى الاتصال المؤسسي للنشر.",
         "Activate holding statement.",
         "تفعيل البيان التمهيدي.",
         "Citizen-facing communication lagged the platform event by ~20 minutes.",
         "تأخر التواصل الموجه للمواطنين نحو 20 دقيقة عن وقوع الحدث.",),
        ("RUN-2026-0003", 60, U_RMANAGER, 600, 89,
         "Approved failover to the alternate cloud region; data-integrity checks queued post-cutover.",
         "تم اعتماد التحويل إلى منطقة الحوسبة السحابية البديلة؛ وجدولة فحوص سلامة البيانات بعد التبديل.",
         "Approve failover; validate data.",
         "اعتماد التحويل والتحقق من البيانات.",
         "Good judgement under pressure.",
         "حسن تقدير تحت الضغط."),
        ("RUN-2026-0003", 90, U_RANALYST, 300, 91,
         "Confirmed national-platform restoration from the alternate region; logged recovery time at 3.5 hours.",
         "تم تأكيد استعادة المنصة الوطنية من المنطقة البديلة؛ وسُجّل زمن التعافي عند 3.5 ساعة.",
         "Confirm restoration; log recovery time.",
         "تأكيد الاستعادة وتسجيل زمن التعافي.",
         "Recovery within target; well documented for the AAR.",
         "التعافي ضمن المستهدف؛ موثق جيداً لتقرير ما بعد التمرين."),
    ]
    for code, off, user, secs, score, ren, rar, den, dar, fen, far in rsp:
        inj = inject_ids[(code, off)]
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND run_inject_id=? "
                           f"AND participant_user_id=?", ORG, inj, user):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, run_inject_id, participant_user_id,
                     response_text_en, response_text_ar, decision_made_en, decision_made_ar,
                     response_time_seconds, quality_score, facilitator_comment_en,
                     facilitator_comment_ar, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, inj, user, ren, rar, den, dar, secs, score, fen, far, U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {code} +{off}m user={user}")


# ═════════════════════════ 4. OBSERVER ASSIGNMENTS ═══════════════════════════
def seed_observers(cur, run_ids: dict[str, int]) -> None:
    """2 observers per completed run. Natural key = (run_id, observer_user_id).
    NOTE: the CHECK constraint offers no NCEMA/DGA government-regulator liaison
    value; the closest allowed roles (BOARD_OBSERVER / COMPLIANCE_OBSERVER /
    INTERNAL_AUDIT) are used. Constraint NOT altered (reported as a blocker note)."""
    tab = "bcm.exercise_observer_assignment"
    obs = [
        ("RUN-2026-0001", U_COMPLIANCE, "COMPLIANCE_OBSERVER"),
        ("RUN-2026-0001", U_AUDITOR, "INTERNAL_AUDIT"),
        ("RUN-2026-0002", U_AUDITOR, "INTERNAL_AUDIT"),
        ("RUN-2026-0002", U_ADMIN, "BOARD_OBSERVER"),
        ("RUN-2026-0003", U_COMPLIANCE, "COMPLIANCE_OBSERVER"),
        ("RUN-2026-0003", U_ADMIN, "BOARD_OBSERVER"),
    ]
    for code, user, role in obs:
        run_id = run_ids[code]
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND exercise_run_id=? "
                           f"AND observer_user_id=?", ORG, run_id, user):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, exercise_run_id, observer_user_id, observer_role,
                     assigned_by_user_id, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?)""",
                ORG, run_id, user, role, U_RMANAGER, U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {code} {role} user={user}")


# ═════════════════════════ 5. OBSERVATIONS ═══════════════════════════════════
def seed_observations(cur, run_ids: dict[str, int], inject_ids: dict[tuple[str, int], int]) -> None:
    """3-4 observations per completed run, feeding the AAR. Natural key =
    (run_id, observer_user_id, observation_type)."""
    tab = "bcm.exercise_observation"
    o = [
        # Pandemic run
        ("RUN-2026-0001", U_COMPLIANCE, ("RUN-2026-0001", 60), "RECOVERY_TIME", "MINOR", False,
         "Beneficiary-service continuity mode was activated in 35 min; full RTO achieved at 3.5h against a 4h target.",
         "تم تفعيل نمط استمرارية خدمة المستفيدين خلال 35 دقيقة؛ وتحقق زمن التعافي الكامل عند 3.5 ساعة مقابل مستهدف 4 ساعات.",
         "Document the 3.5h actual as the validated RTO baseline for beneficiary services.",
         "توثيق 3.5 ساعة كزمن تعافٍ مرجعي مُتحقَّق منه لخدمات المستفيدين."),
        ("RUN-2026-0001", U_AUDITOR, ("RUN-2026-0001", 30), "COMPLIANCE", "MODERATE", True,
         "The NCEMA notification template used in the exercise was an outdated version.",
         "كان نموذج إشعار المركز الوطني لإدارة الكوارث المستخدم في التمرين نسخة قديمة.",
         "Replace with the current NCEMA template and store it in the controlled document set.",
         "استبدال النموذج بأحدث نسخة من المركز الوطني وحفظه ضمن الوثائق المحكومة."),
        ("RUN-2026-0001", U_AUDITOR, None, "GAP", "MINOR", False,
         "COM-CRISIS quorum was not reached on the first call; the alternate-member roster was needed.",
         "لم يكتمل النصاب القانوني للجنة إدارة الأزمات في الاتصال الأول؛ واحتيج إلى قائمة الأعضاء البدلاء.",
         "Confirm the alternate-member roster for COM-CRISIS ahead of the next exercise.",
         "تأكيد قائمة الأعضاء البدلاء للجنة إدارة الأزمات قبل التمرين القادم."),
        # Call-tree run
        ("RUN-2026-0002", U_AUDITOR, ("RUN-2026-0002", 30), "COMMUNICATION", "INFO", False,
         "Secondary-channel switch achieved 100% receipt confirmation across team leads.",
         "حقّق التحول إلى القناة البديلة تأكيد استلام بنسبة 100% بين قادة الفرق.",
         "Retain the voice call-tree as the validated fallback for crisis notifications.",
         "اعتماد شجرة الاتصال الصوتي كقناة احتياطية مُتحقَّق منها لإشعارات الأزمات."),
        ("RUN-2026-0002", U_ADMIN, None, "STRENGTH", "INFO", False,
         "Call-tree cascade completed within the target notification window.",
         "اكتمل تتابع شجرة الاتصال ضمن نافذة الإشعار المستهدفة.",
         "No action; capture as a continuity-programme strength.",
         "لا إجراء؛ تُسجَّل كنقطة قوة في برنامج الاستمرارية."),
        # Cloud-outage run
        ("RUN-2026-0003", U_COMPLIANCE, ("RUN-2026-0003", 30), "COMMUNICATION", "MODERATE", True,
         "Citizen-facing communication lagged the platform event by ~20 minutes.",
         "تأخر التواصل الموجه للمواطنين نحو 20 دقيقة عن وقوع حدث المنصة.",
         "Pre-approve a citizen holding statement to cut public-communication lag.",
         "اعتماد بيان تمهيدي للمواطنين مسبقاً لتقليص زمن تأخر التواصل العام."),
        ("RUN-2026-0003", U_COMPLIANCE, ("RUN-2026-0003", 0), "COMPLIANCE", "MAJOR", True,
         "DGA/NDMO regulatory notification dispatch was delayed pending approval authority.",
         "تأخر إرسال الإشعار التنظيمي لهيئة الحكومة الرقمية / مكتب إدارة البيانات الوطنية بانتظار صلاحية الاعتماد.",
         "Pre-delegate notification-approval authority in the crisis plan to avoid dispatch delay.",
         "تفويض صلاحية اعتماد الإشعار مسبقاً في خطة الأزمات لتفادي تأخر الإرسال."),
        ("RUN-2026-0003", U_ADMIN, ("RUN-2026-0003", 60), "DECISION_QUALITY", "MINOR", True,
         "The crisis team's decision authority for cloud-region failover was initially unclear.",
         "كانت صلاحية فريق الأزمات لاتخاذ قرار التحويل بين مناطق الحوسبة السحابية غير واضحة في البداية.",
         "Clarify failover decision authority in the DRP RACI.",
         "توضيح صلاحية قرار التحويل في مصفوفة المسؤوليات لخطة التعافي."),
        ("RUN-2026-0003", U_ADMIN, ("RUN-2026-0003", 90), "RECOVERY_TIME", "INFO", False,
         "National-platform recovery from the alternate region completed at 3.5h, within the DR target.",
         "اكتمل تعافي المنصة الوطنية من المنطقة البديلة عند 3.5 ساعة، ضمن مستهدف التعافي من الكوارث.",
         "Record the validated cross-region failover RTO in the DRP.",
         "تسجيل زمن التعافي المُتحقَّق منه للتحويل بين المناطق في خطة التعافي من الكوارث."),
        ("RUN-2026-0003", U_COMPLIANCE, None, "GENERAL", "INFO", False,
         "Overall the run met its objectives; two governance gaps were flagged for the AAR.",
         "حقق التمرين أهدافه بشكل عام؛ ورُصدت ثغرتان في الحوكمة لإدراجهما في تقرير ما بعد التمرين.",
         "Feed the two flagged gaps into the post-exercise corrective-action plan.",
         "إدراج الثغرتين المرصودتين في خطة الإجراءات التصحيحية بعد التمرين."),
    ]
    for code, user, inj_key, otyp, sev, flag, ten, tar, ren, rar in o:
        run_id = run_ids[code]
        rel = inject_ids.get(inj_key) if inj_key else None
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND exercise_run_id=? "
                           f"AND observer_user_id=? AND observation_type=?",
                      ORG, run_id, user, otyp):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, exercise_run_id, observer_user_id, related_inject_id,
                     observation_text_en, observation_text_ar, observation_type, severity,
                     recommendation_en, recommendation_ar, is_flagged_for_aar, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, run_id, user, rel, ten, tar, otyp, sev, ren, rar,
                1 if flag else 0, U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {code} {otyp} user={user}")


# ═════════════════════════ 6. MTPD INHERITANCE ═══════════════════════════════
def seed_mtpd(cur) -> None:
    """2 annual MTPD-inheritance assessments + 8 findings under the 2025 cycle."""
    a_tab = "bcm.mtpd_inheritance_assessment"
    f_tab = "bcm.mtpd_inheritance_finding"

    assessments = [
        dict(code="MTPD-INH-2025", year=2025, published=True,
             notes_en="Annual MTPD-inheritance roll-up: maximum tolerable period of disruption of "
                      "critical government processes versus the attested MTPD of their critical "
                      "external providers (cloud, national platform, print-house), per NCEMA "
                      "dependency-resilience expectations.",
             notes_ar="التجميع السنوي لتوريث الحد الأقصى المسموح لفترة التعطل: مقارنة الحد الأقصى "
                      "المسموح لتعطل العمليات الحكومية الحرجة مع الحد المُقرّ من مزوديها الخارجيين "
                      "الحرجين (الحوسبة السحابية، المنصة الوطنية، دار الطباعة)، وفق توقعات المركز "
                      "الوطني لإدارة الكوارث لمرونة الاعتمادية."),
        dict(code="MTPD-INH-2026", year=2026, published=False,
             notes_en="Draft MTPD-inheritance roll-up for 2026; provider attestations are being "
                      "re-collected ahead of publication.",
             notes_ar="مسودة تجميع توريث الحد الأقصى المسموح لفترة التعطل لعام 2026؛ يجري إعادة جمع "
                      "إقرارات المزودين قبل النشر."),
    ]
    a_ids: dict[str, int] = {}
    for a in assessments:
        if not exists(cur, f"SELECT 1 FROM {a_tab} WHERE organisation_id=? AND assessment_code=?",
                      ORG, a["code"]):
            cur.execute(
                f"""INSERT INTO {a_tab}
                    (organisation_id, assessment_code, cycle_year, computed_by_user_id,
                     total_processes, total_breaches, total_warnings, total_ok, total_no_attestation,
                     is_published, published_at, published_by_user_id, notes_en, notes_ar,
                     created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, a["code"], a["year"], U_RMANAGER,
                # counters set below for 2025 (matches the 8 findings); 0 for draft 2026
                (8 if a["year"] == 2025 else 0),
                (2 if a["year"] == 2025 else 0),
                (2 if a["year"] == 2025 else 0),
                (3 if a["year"] == 2025 else 0),
                (1 if a["year"] == 2025 else 0),
                1 if a["published"] else 0,
                ("2025-12-15T10:00:00" if a["published"] else None),
                (U_RMANAGER if a["published"] else None),
                a["notes_en"], a["notes_ar"], U_ADMIN, U_ADMIN,
            )
            bump(a_tab)
            print(f"  + {a_tab}: {a['code']}")
        cur.execute(f"SELECT id FROM {a_tab} WHERE organisation_id=? AND assessment_code=?",
                    ORG, a["code"])
        a_ids[a["code"]] = cur.fetchone()[0]

    aid_2025 = a_ids["MTPD-INH-2025"]
    # findings: (proc_id, proc_en, proc_ar, vendor_id, vendor_name, proc_mtpd, vendor_mtpd,
    #            finding_type, severity, dep_strength, dep_type, rec_en, rec_ar)
    findings = [
        (1, "Core Banking Operations", "عمليات البنك الأساسية", 8, "Hyperscaler Cloud Region (national platform host)",
         4, 8, "BREACH", "MAJOR", "TIGHT", "REQUIRED",
         "Negotiate a contractual recovery commitment with the cloud provider to meet the 4h process MTPD.",
         "التفاوض على التزام تعاقدي للتعافي مع مزود الحوسبة السحابية لتحقيق الحد المسموح للتعطل البالغ 4 ساعات."),
        (1, "Core Banking Operations", "عمليات البنك الأساسية", 3, "Managed Infrastructure / Data-Centre Operator",
         4, 4, "OK", "INFO", "TIGHT", "REQUIRED",
         "No action; provider attested MTPD meets the process MTPD.",
         "لا إجراء؛ الحد المُقرّ من المزود يطابق الحد المسموح للعملية."),
        (2, "Customer Onboarding", "تسجيل العملاء", 2, "Identity Verification Service (national integration)",
         8, 12, "BREACH", "MODERATE", "TIGHT", "REQUIRED",
         "Add a manual fallback onboarding path to bridge the 4h gap to the provider MTPD.",
         "إضافة مسار تسجيل يدوي بديل لسد فجوة 4 ساعات حتى الحد المُقرّ من المزود."),
        (2, "Customer Onboarding", "تسجيل العملاء", 6, "Document Print & Mail House",
         8, 8, "OK", "INFO", "LOOSE", "RECOMMENDED",
         "No action; provider MTPD aligns with the process MTPD.",
         "لا إجراء؛ الحد المُقرّ من المزود يتوافق مع الحد المسموح للعملية."),
        (4, "Regulatory Reporting", "التقارير التنظيمية", 4, "Regulatory Reporting Platform Vendor",
         12, 10, "OK", "INFO", "LOOSE", "RECOMMENDED",
         "No action; provider MTPD is within the process MTPD.",
         "لا إجراء؛ الحد المُقرّ من المزود ضمن الحد المسموح للعملية."),
        (4, "Regulatory Reporting", "التقارير التنظيمية", 10, "Secondary Reporting Connectivity Provider",
         12, 14, "WARNING", "MINOR", "TRANSIENT", "FALLBACK",
         "Provider MTPD is marginally above the process MTPD; monitor and add a tertiary path.",
         "الحد المُقرّ من المزود يتجاوز الحد المسموح بهامش ضئيل؛ المراقبة وإضافة مسار ثالث."),
        (5, "IT Infrastructure Management", "إدارة البنية التقنية", 1, "Core Platform / OS & Virtualisation Vendor",
         4, 6, "WARNING", "MODERATE", "TIGHT", "REQUIRED",
         "Escalate to a premium support tier to bring provider MTPD within the 4h process MTPD.",
         "الترقية إلى مستوى دعم متميز لجعل الحد المُقرّ من المزود ضمن الحد المسموح البالغ 4 ساعات."),
        (5, "IT Infrastructure Management", "إدارة البنية التقنية", 11, "Network Connectivity Provider (secondary)",
         4, None, "NO_ATTESTATION", "MAJOR", "LOOSE", "RECOMMENDED",
         "Provider has not submitted an MTPD attestation; request attestation and treat as a key risk.",
         "لم يقدّم المزود إقراراً بالحد المسموح للتعطل؛ طلب الإقرار والتعامل معه كخطر رئيسي."),
    ]
    for pid, pen, par, vid, vname, pmtpd, vmtpd, ftyp, sev, dstr, dtyp, ren, rar in findings:
        var = None if vmtpd is None else (vmtpd - pmtpd)
        if not exists(cur, f"SELECT 1 FROM {f_tab} WHERE organisation_id=? AND assessment_id=? "
                           f"AND business_process_id=? AND vendor_id=?",
                      ORG, aid_2025, pid, vid):
            cur.execute(
                f"""INSERT INTO {f_tab}
                    (organisation_id, assessment_id, business_process_id, business_process_name_en,
                     business_process_name_ar, vendor_id, vendor_name, process_mtpd_hours,
                     vendor_attested_mtpd_hours, variance_hours, finding_type, severity,
                     dependency_strength, dependency_type, recommendation_en, recommendation_ar,
                     created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, aid_2025, pid, pen, par, vid, vname, pmtpd, vmtpd, var,
                ftyp, sev, dstr, dtyp, ren, rar, U_ADMIN, U_ADMIN,
            )
            bump(f_tab)
            print(f"  + {f_tab}: {pen} <- {vname} [{ftyp}]")


# ═════════════════════════ 7. BCP BINDER EXPORTS ═════════════════════════════
def seed_binders(cur) -> None:
    tab = "bcm.bcp_binder_export"
    wm = "Controlled — Not for Distribution / محكوم — غير قابل للتوزيع"
    exports = [
        dict(code="EXP-2026-0001", plan=BCP_CORE, fmt="PDF", lang="BILINGUAL", status="READY",
             ver="v3.0", pages=84, bytes=2_915_000, dur=4200, downloads=6,
             gen="2026-05-02T09:15:00", last_dl="2026-06-10T11:20:00",
             path="/binders/2026/EXP-2026-0001_core-gov-service-bcp_v3.0.pdf"),
        dict(code="EXP-2026-0002", plan=DRP_CORE, fmt="PDF", lang="BILINGUAL", status="READY",
             ver="v2.0", pages=66, bytes=2_310_000, dur=3600, downloads=4,
             gen="2026-05-02T09:40:00", last_dl="2026-06-12T08:05:00",
             path="/binders/2026/EXP-2026-0002_core-gov-service-drp_v2.0.pdf"),
        dict(code="EXP-2026-0003", plan=BCP_CORE, fmt="PDF", lang="BILINGUAL", status="ARCHIVED",
             ver="v2.0", pages=80, bytes=2_780_000, dur=4100, downloads=11,
             gen="2025-11-18T14:00:00", last_dl="2026-04-29T09:00:00",
             path="/binders/2025/EXP-2026-0003_core-gov-service-bcp_v2.0.pdf"),
        dict(code="EXP-2026-0004", plan=DRP_CORE, fmt="PDF", lang="BILINGUAL", status="GENERATING",
             ver="v2.1-draft", pages=None, bytes=None, dur=None, downloads=0,
             gen="2026-06-25T07:30:00", last_dl=None, path=None),
    ]
    for e in exports:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND export_code=?",
                      ORG, e["code"]):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, bcp_plan_id, export_code, format, language, version_label,
                     watermark_text, status, generated_at, generated_by_user_id, generated_by_name,
                     generation_duration_ms, page_count, byte_size, file_path, download_count,
                     last_downloaded_at, last_downloaded_by_user_id, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, e["plan"], e["code"], e["fmt"], e["lang"], e["ver"], wm, e["status"],
                e["gen"], U_RMANAGER, "Director of Business Continuity",
                e["dur"], e["pages"], e["bytes"], e["path"], e["downloads"],
                e["last_dl"], (U_RMANAGER if e["last_dl"] else None), U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: {e['code']} ({e['status']})")


# ═════════════════════════ 8. bcm_lc BIA RECORDS ═════════════════════════════
def seed_bia(cur) -> None:
    tab = "bcm_lc.bia_records"
    bias = [
        ("BIA-LC-2026-001", "Service Licensing & Permit Issuance", "إصدار التراخيص والتصاريح",
         "Licensing Services", "Director of Licensing", "CRITICAL", 4, 1, 8,
         "Fees and licensing revenue interrupted; backlog of pending applications.",
         "Statutory licensing-issuance service levels breached; regulator scrutiny.",
         "Public trust impact from delayed permit issuance.",
         "Licensing front-office and back-office processing halted.",
         "National identity verification; e-payment gateway; national digital platform.",
         "Inspection scheduling; beneficiary notifications; ZATCA fee settlement.",
         "Q4 budget season; pre-Hajj licensing surge", "موسم ميزانية الربع الرابع؛ ذروة التراخيص قبل الحج"),
        ("BIA-LC-2026-002", "Field Inspection & Enforcement", "التفتيش الميداني والإنفاذ",
         "Inspection & Enforcement", "Director of Inspection", "CRITICAL", 8, 4, 24,
         "Enforcement fines collection paused.",
         "Statutory inspection mandate disrupted; compliance-oversight gap.",
         "Public-safety perception risk from unconducted inspections.",
         "Field inspectors lose case-management and scheduling access.",
         "Licensing register; mobile inspection app; national digital platform.",
         "Violation case files; legal-affairs referrals; beneficiary complaints.",
         "Seasonal inspection campaigns", "حملات التفتيش الموسمية"),
        ("BIA-LC-2026-003", "Beneficiary Registration & Support", "تسجيل المستفيدين والدعم",
         "Beneficiary Services", "Director of Beneficiary Services", "CRITICAL", 4, 1, 8,
         "Support-fee and subsidy disbursement delays.",
         "Beneficiary-service charter and SLA breaches.",
         "High public visibility; citizen-satisfaction impact.",
         "Call-centre and registration portal unavailable.",
         "National identity verification; unified contact channel; e-payment.",
         "Subsidy disbursement; grievances; reporting to MoF.",
         "Year-end enrolment; Ramadan support surge", "تسجيل نهاية العام؛ ذروة الدعم في رمضان"),
        ("BIA-LC-2026-004", "National Data Platform / Open-Data Exchange", "المنصة الوطنية للبيانات / تبادل البيانات المفتوحة",
         "Data Management", "Director of Data Management Office", "CRITICAL", 4, 0, 12,
         "Inter-agency data-sharing fees and SLAs affected.",
         "NDMO national-data-availability obligations breached.",
         "Cross-government dependency; reputational impact with partner entities.",
         "Government data-exchange APIs and open-data portal down.",
         "Hyperscaler cloud region; data-integration bus; identity federation.",
         "Consuming government entities; analytics; digital-portal services.",
         "Year-end reporting cycle", "دورة التقارير في نهاية العام"),
        ("BIA-LC-2026-005", "Digital Government Portal & e-Services", "بوابة الحكومة الرقمية والخدمات الإلكترونية",
         "Digital Transformation", "Director of Digital Transformation", "CRITICAL", 2, 0, 6,
         "Online fee collection interrupted across e-services.",
         "DGA digital-service-availability targets breached.",
         "Most-visible citizen-facing channel; high reputational exposure.",
         "All online self-service transactions unavailable.",
         "National data platform; identity (Nafath); e-payment; CDN/WAF.",
         "Licensing, inspection, beneficiary and grievance e-services.",
         "Hajj season; year-end deadlines", "موسم الحج؛ مواعيد نهاية العام النهائية"),
        ("BIA-LC-2026-006", "Grievances & Appeals", "التظلمات والاعتراضات",
         "Legal Affairs", "Director of Legal Affairs", "HIGH", 24, 8, 72,
         "Limited direct financial impact.",
         "Statutory grievance-handling timelines at risk.",
         "Fairness and due-process perception impact.",
         "Grievance intake and case workflow disrupted.",
         "Digital portal; case-management system; identity verification.",
         "Legal review; committee decisions; beneficiary notifications.",
         "Post-licensing-cycle appeal surge", "ذروة الاعتراضات بعد دورة التراخيص"),
        ("BIA-LC-2026-007", "Payroll & HR Operations", "الرواتب وعمليات الموارد البشرية",
         "Human Resources", "Director of Human Resources", "HIGH", 24, 8, 48,
         "Staff salary disbursement delay risk.",
         "Labour and payroll-compliance obligations at risk.",
         "Internal morale; limited external exposure.",
         "Payroll run and HR self-service unavailable.",
         "HRMS; bank payment file; Etimad/MoF payroll integration.",
         "Finance settlement; GOSI; bank disbursement.",
         "Month-end payroll run", "تشغيل الرواتب في نهاية الشهر"),
        ("BIA-LC-2026-008", "Procurement & Contracts (Etimad)", "المشتريات والعقود (اعتماد)",
         "Procurement", "Director of Procurement", "MEDIUM", 48, 24, 120,
         "Delayed supplier payments and tender cycles.",
         "Etimad and government-procurement-process compliance at risk.",
         "Supplier-relationship and transparency perception impact.",
         "Tendering and contract administration disrupted.",
         "Etimad platform; e-signature; finance system.",
         "Vendor payments; budget commitments; audit trail.",
         "Year-end procurement closeout", "إقفال المشتريات في نهاية العام"),
    ]
    for (xid, pen, par, func, owner, crit, rto, rpo, mtpd,
         imp_fin, imp_reg, imp_rep, imp_ops, up, down, peak_en, peak_ar) in bias:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND external_id=?",
                      ORG, xid):
            cur.execute(
                f"""INSERT INTO {tab}
                    (external_id, organisation_id, process_name_en, process_name_ar,
                     business_function, process_owner, criticality, rto_hours, rpo_hours,
                     mtpd_hours, impact_financial, impact_regulatory, impact_reputation,
                     impact_operations, upstream_deps_en, downstream_deps_en, peak_periods_en,
                     peak_periods_ar, status, approved_by, approved_at, review_due_at)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                xid, ORG, pen, par, func, owner, crit, rto, rpo, mtpd,
                imp_fin, imp_reg, imp_rep, imp_ops, up, down, peak_en, peak_ar,
                "APPROVED", "Director of Business Continuity", "2026-01-20T10:00:00", "2027-01-20",
            )
            bump(tab)
            print(f"  + {tab}: {xid} {pen}")


# ═════════════════════════ 9. bcm_lc RECOVERY STRATEGIES ═════════════════════
def seed_strategies(cur) -> None:
    tab = "bcm_lc.recovery_strategies"
    strats = [
        ("RS-LC-2026-001", "BIA-LC-2026-001", "ALTERNATE_SITE",
         "Relocate licensing front-office and processing to the alternate government data centre with pre-staged workstations.",
         "نقل المكتب الأمامي ومعالجة التراخيص إلى مركز البيانات الحكومي البديل مع محطات عمل مُجهّزة مسبقاً.",
         4, 1, 1_200_000.00, "Riyadh — Alternate Government Data Centre",
         "/runbooks/licensing-altsite.md", "2026-03-10T10:00:00", "RTO met (3h45m vs 4h target)."),
        ("RS-LC-2026-002", "BIA-LC-2026-002", "MANUAL",
         "Switch field inspection to an offline mobile-app mode with deferred case synchronisation.",
         "تحويل التفتيش الميداني إلى نمط تطبيق محمول دون اتصال مع مزامنة مؤجلة للحالات.",
         8, 4, 350_000.00, "Field / mobile",
         "/runbooks/inspection-offline.md", "2026-02-25T09:00:00", "PASS — RTO met"),
        ("RS-LC-2026-003", "BIA-LC-2026-003", "WFH",
         "Activate remote beneficiary-service mode: call-centre agents and registration staff work from home over secure VDI.",
         "تفعيل نمط خدمة المستفيدين عن بُعد: عمل موظفي مركز الاتصال والتسجيل من المنزل عبر بيئة سطح مكتب افتراضية آمنة.",
         4, 1, 600_000.00, "Remote (secure VDI)",
         "/runbooks/beneficiary-wfh.md", "2026-02-11T08:00:00", "PASS — RTO met"),
        ("RS-LC-2026-004", "BIA-LC-2026-004", "CLOUD_DR",
         "Fail the national data platform over to an NDMO-approved alternate cloud region with continuous replication.",
         "تحويل المنصة الوطنية للبيانات إلى منطقة حوسبة سحابية بديلة معتمدة من مكتب إدارة البيانات الوطنية مع تكرار مستمر.",
         4, 0, 2_400_000.00, "Alternate cloud region (in-Kingdom)",
         "/runbooks/ndp-cloud-dr.md", "2026-04-22T09:00:00", "PASS — RTO met (3.5h)"),
        ("RS-LC-2026-005", "BIA-LC-2026-005", "CLOUD_DR",
         "Serve the digital-government portal from the secondary region behind the CDN/WAF with read-through caching.",
         "تشغيل بوابة الحكومة الرقمية من المنطقة الثانوية خلف شبكة توصيل المحتوى وجدار الحماية مع تخزين مؤقت.",
         2, 0, 1_800_000.00, "Secondary region + edge CDN",
         "/runbooks/portal-failover.md", "2026-04-22T11:00:00", "PASS — within target"),
        ("RS-LC-2026-006", "BIA-LC-2026-006", "RECIPROCAL",
         "Process priority grievances via a mutual-aid arrangement with a sister authority's case-management capacity.",
         "معالجة التظلمات ذات الأولوية عبر ترتيب مساندة متبادلة مع قدرة إدارة الحالات لدى هيئة شقيقة.",
         24, 8, 120_000.00, "Sister-authority site (mutual aid)",
         None, None, None),
        ("RS-LC-2026-007", "BIA-LC-2026-007", "MANUAL",
         "Run an emergency manual payroll using the last validated payment file to ensure salary disbursement.",
         "تشغيل رواتب يدوية طارئة باستخدام آخر ملف دفع مُتحقَّق منه لضمان صرف الرواتب.",
         24, 8, 150_000.00, "Finance back-office",
         "/runbooks/payroll-manual.md", "2026-01-28T12:00:00", "PASS — totals reconciled"),
        ("RS-LC-2026-008", "BIA-LC-2026-008", "WFH",
         "Continue procurement and contract administration remotely via the Etimad platform on secure remote access.",
         "متابعة المشتريات وإدارة العقود عن بُعد عبر منصة اعتماد على وصول آمن.",
         48, 24, 90_000.00, "Remote (secure access)",
         None, None, None),
    ]
    for xid, bia, styp, den, dar, rto, rpo, cost, site, runbook, tested, outcome in strats:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND external_id=?",
                      ORG, xid):
            cur.execute(
                f"""INSERT INTO {tab}
                    (external_id, organisation_id, bia_id, strategy_type, description_en,
                     description_ar, estimated_rto_hours, estimated_rpo_hours, annual_cost_sar,
                     site_location, runbook_url, last_tested_at, test_outcome, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                xid, ORG, bia, styp, den, dar, rto, rpo, cost, site, runbook, tested, outcome,
                "APPROVED",
            )
            bump(tab)
            print(f"  + {tab}: {xid} ({styp})")


# ═════════════════════════ 10. bcm_lc DRILLS ═════════════════════════════════
def seed_drills(cur) -> None:
    tab = "bcm_lc.drills"
    drills = [
        ("DRL-LC-2026-001", "Licensing Service Failover to Alternate DC",
         "تحويل خدمة الترخيص إلى مركز البيانات البديل", "FAILOVER",
         "Primary data-centre loss; relocate licensing services to the alternate government DC.",
         "فقدان مركز البيانات الأساسي؛ نقل خدمات الترخيص إلى مركز البيانات الحكومي البديل.",
         "2026-03-10T08:00:00", "2026-03-10T11:45:00", 225, "Licensing, IT, BCM",
         '["BIA-LC-2026-001"]', 4, 3.75, 1.0, "PASS",
         "RTO met with margin; one runbook step needed clarification.",
         "تحقق زمن التعافي بهامش؛ احتاجت إحدى خطوات دليل التشغيل إلى توضيح.", 1, "COMPLETED"),
        ("DRL-LC-2026-002", "Beneficiary Call-Centre Relocation",
         "نقل مركز اتصال المستفيدين", "RELOCATION",
         "Call-centre site unavailable; relocate agents to remote secure VDI.",
         "عدم توفر موقع مركز الاتصال؛ نقل الموظفين إلى بيئة سطح مكتب افتراضية آمنة عن بُعد.",
         "2026-02-11T08:00:00", "2026-02-11T11:30:00", 210, "Beneficiary Services, IT, HR",
         '["BIA-LC-2026-003"]', 4, 3.5, 1.0, "PASS",
         "Remote-service mode activated within 35 minutes; SLA maintained.",
         "تم تفعيل نمط الخدمة عن بُعد خلال 35 دقيقة؛ وحُوفظ على مستوى الخدمة.", 0, "COMPLETED"),
        ("DRL-LC-2026-003", "National Data Platform Cloud DR",
         "التعافي السحابي للمنصة الوطنية للبيانات", "CLOUD_DR",
         "Hyperscaler regional outage; fail the national data platform over to the alternate region.",
         "انقطاع إقليمي لمزود الحوسبة السحابية؛ تحويل المنصة الوطنية للبيانات إلى المنطقة البديلة.",
         "2026-04-22T09:00:00", "2026-04-22T12:30:00", 210, "Data Management, IT, Digital",
         '["BIA-LC-2026-004", "BIA-LC-2026-005"]', 4, 3.5, 0.0, "PASS",
         "Cross-region failover succeeded; regulator-notification dispatch was delayed (CAPA raised).",
         "نجح التحويل بين المناطق؛ تأخر إرسال إشعار الجهة المنظمة (رُفع إجراء تصحيحي).", 1, "COMPLETED"),
        ("DRL-LC-2026-004", "Crisis-Management-Team Activation",
         "تفعيل فريق إدارة الأزمات", "TABLETOP",
         "Major-incident scenario triggering NCEMA-aligned crisis-management activation via COM-CRISIS.",
         "سيناريو حادث كبير يستدعي تفعيل إدارة الأزمات بما يتوافق مع المركز الوطني عبر لجنة إدارة الأزمات.",
         "2026-03-04T13:00:00", "2026-03-04T14:30:00", 90, "Crisis team, Compliance, Internal Audit",
         '["BIA-LC-2026-003", "BIA-LC-2026-005"]', None, None, None, "PASS",
         "Call-tree validated; COM-CRISIS quorum gap flagged for the alternate roster.",
         "تم التحقق من شجرة الاتصال؛ ورُصدت فجوة نصاب لجنة إدارة الأزمات لقائمة البدلاء.", 1, "COMPLETED"),
        ("DRL-LC-2026-005", "e-Services Portal DDoS Continuity",
         "استمرارية بوابة الخدمات الإلكترونية ضد هجمات الحرمان", "TECHNICAL",
         "Volumetric DDoS against the digital-government portal; validate NCA-aligned mitigation and continuity.",
         "هجوم حرمان حجمي على بوابة الحكومة الرقمية؛ التحقق من التخفيف والاستمرارية بما يتوافق مع الهيئة الوطنية للأمن السيبراني.",
         "2026-07-15T09:00:00", None, None, "IT, Cybersecurity, Digital",
         '["BIA-LC-2026-005"]', 2, None, None, None, None, None, 0, "PLANNED"),
    ]
    for (xid, ten, tar, dtyp, sen, sar, sched, execd, dur, parts, bia_ids,
         rto_t, rto_a, rpo_a, outcome, fen, far, capa, status) in drills:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND external_id=?",
                      ORG, xid):
            cur.execute(
                f"""INSERT INTO {tab}
                    (external_id, organisation_id, title_en, title_ar, drill_type, scenario_en,
                     scenario_ar, scheduled_at, executed_at, duration_minutes, participants,
                     bia_ids, rto_target_hours, rto_actual_hours, rpo_actual_hours, outcome,
                     findings_en, findings_ar, capa_count, status)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                xid, ORG, ten, tar, dtyp, sen, sar, sched, execd, dur, parts, bia_ids,
                rto_t, rto_a, rpo_a, outcome, fen, far, capa, status,
            )
            bump(tab)
            print(f"  + {tab}: {xid} ({dtyp}/{status})")


# ═════════════════════════ 11. rrp_event TIMELINE ═══════════════════════════
def seed_rrp_events(cur) -> None:
    """Audit-trail timeline over the 2 EXISTING org-1 rrp cycles. No new bank
    narrative invented — only lifecycle events for rows already present. Natural
    key = (cycle_id, occurred_at, event_type)."""
    tab = "rrp.rrp_event"
    # FY2025 cycle (id 3, CLOSED). Indicator id 7 = IND-DRO (deposit run-off) for breach/recover.
    events = [
        # FY2025 — CLOSED lifecycle
        (CYCLE_FY2025, "2025-01-15T09:00:00", "STATUS_CHANGED", U_RMANAGER, "DRAFT", None, None, None, None, None,
         "FY2025 recovery & resolution cycle opened in DRAFT.",
         "افتتاح دورة خطة التعافي والحل للسنة المالية 2025 بحالة مسودة."),
        (CYCLE_FY2025, "2025-03-20T11:00:00", "SCENARIO_REHEARSED", U_RANALYST, None, None, None, None, None, None,
         "Severe-but-plausible stress scenario rehearsed with the recovery team.",
         "تم التدرب على سيناريو ضغط حاد لكنه محتمل مع فريق التعافي."),
        (CYCLE_FY2025, "2025-05-10T14:00:00", "INDICATOR_BREACH", U_RANALYST, None, None, None, 7, "GREEN", "AMBER",
         "Deposit run-off (30-day) early-warning indicator moved from GREEN to AMBER.",
         "انتقل مؤشر الإنذار المبكر لتدفق الودائع الخارج (30 يوماً) من الأخضر إلى الكهرماني."),
        (CYCLE_FY2025, "2025-06-02T10:00:00", "INDICATOR_RECOVERED", U_RANALYST, None, None, None, 7, "AMBER", "GREEN",
         "Deposit run-off indicator recovered to GREEN after the liquidity action.",
         "تعافى مؤشر تدفق الودائع الخارج إلى الأخضر بعد إجراء السيولة."),
        (CYCLE_FY2025, "2025-09-30T13:00:00", "BOARD_MILESTONE", U_ADMIN, None, None, None, None, None, None,
         "Board reviewed and endorsed the FY2025 recovery & resolution plan.",
         "راجع المجلس واعتمد خطة التعافي والحل للسنة المالية 2025."),
        (CYCLE_FY2025, "2025-10-15T09:30:00", "STATUS_CHANGED", U_RMANAGER, "SUBMITTED", None, None, None, None, None,
         "FY2025 plan submitted to the regulator.",
         "تم تقديم خطة السنة المالية 2025 إلى الجهة المنظمة."),
        (CYCLE_FY2025, "2025-10-15T09:35:00", "SUBMITTED", U_RMANAGER, "SUBMITTED", None, None, None, None, None,
         "Filing artifact lodged; submission reference recorded.",
         "تم إيداع مستند التقديم وتسجيل مرجع التقديم."),
        (CYCLE_FY2025, "2025-11-05T10:00:00", "ACK_RECEIVED", U_COMPLIANCE, None, None, None, None, None, None,
         "Regulator acknowledgement received for the FY2025 filing.",
         "تم استلام إقرار الجهة المنظمة بتقديم السنة المالية 2025."),
        (CYCLE_FY2025, "2025-12-20T16:00:00", "STATUS_CHANGED", U_RMANAGER, "CLOSED", None, None, None, None, None,
         "FY2025 cycle closed after regulator acknowledgement.",
         "تم إقفال دورة السنة المالية 2025 بعد إقرار الجهة المنظمة."),
        # FY2026 — IN_PREPARATION lifecycle (indicator id 9 = IND-LCR currently AMBER)
        (CYCLE_FY2026, "2026-01-12T09:00:00", "STATUS_CHANGED", U_RMANAGER, "IN_PREPARATION", None, None, None, None, None,
         "FY2026 recovery & resolution cycle opened and set to IN_PREPARATION.",
         "افتتاح دورة خطة التعافي والحل للسنة المالية 2026 وتعيينها قيد الإعداد."),
        (CYCLE_FY2026, "2026-02-08T11:00:00", "CRITICAL_FUNCTION_ADDED", U_RANALYST, None, None, None, None, None, None,
         "Critical-function inventory refreshed and carried forward for the FY2026 cycle.",
         "تم تحديث جرد الوظائف الحرجة وترحيله لدورة السنة المالية 2026."),
        (CYCLE_FY2026, "2026-03-18T14:00:00", "OPTION_REASSESSED", U_RANALYST, None, 17, None, None, None, None,
         "A recovery option was reassessed for feasibility under the FY2026 assumptions.",
         "تمت إعادة تقييم أحد خيارات التعافي من حيث الجدوى وفق افتراضات السنة المالية 2026."),
        (CYCLE_FY2026, "2026-04-25T10:00:00", "INDICATOR_BREACH", U_RANALYST, None, None, None, 9, "GREEN", "AMBER",
         "Liquidity Coverage Ratio early-warning indicator moved to AMBER; monitoring intensified.",
         "انتقل مؤشر الإنذار المبكر لنسبة تغطية السيولة إلى الكهرماني؛ وتم تكثيف المراقبة."),
        (CYCLE_FY2026, "2026-05-30T15:00:00", "INTERNAL_NOTE", U_COMPLIANCE, None, None, None, None, None, None,
         "Internal note: FY2026 plan on track for board review; provider attestations being refreshed.",
         "ملاحظة داخلية: خطة السنة المالية 2026 تسير وفق الخطة لمراجعة المجلس؛ ويجري تحديث إقرارات المزودين."),
    ]
    for cid, occ, etyp, actor, status_after, lopt, lcf, lind, bb, ba, nen, nar in events:
        if not exists(cur, f"SELECT 1 FROM {tab} WHERE organisation_id=? AND cycle_id=? "
                           f"AND occurred_at=? AND event_type=?",
                      ORG, cid, occ, etyp):
            cur.execute(
                f"""INSERT INTO {tab}
                    (organisation_id, cycle_id, occurred_at, event_type, actor_user_id, actor_name,
                     status_after, linked_option_id, linked_indicator_id, linked_critical_function_id,
                     band_before, band_after, note_en, note_ar, created_by, updated_by)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                ORG, cid, occ, etyp, actor, None, status_after, lopt, lind, lcf,
                bb, ba, nen, nar, U_ADMIN, U_ADMIN,
            )
            bump(tab)
            print(f"  + {tab}: cycle={cid} {etyp} @ {occ[:10]}")


# ═════════════════════════ MAIN ═════════════════════════════════════════════
def main() -> None:
    conn = connect()
    conn.autocommit = True
    cur = conn.cursor()

    print("Seeding government-authority BCM content (organisation_id = 1)\n")

    print("[1] bcm.exercise_run")
    run_ids = seed_exercise_runs(cur)
    print("[2] bcm.exercise_run_inject")
    inject_ids = seed_injects(cur, run_ids)
    print("[3] bcm.exercise_run_response")
    seed_responses(cur, inject_ids)
    print("[4] bcm.exercise_observer_assignment")
    seed_observers(cur, run_ids)
    print("[5] bcm.exercise_observation")
    seed_observations(cur, run_ids, inject_ids)
    print("[6] bcm.mtpd_inheritance_assessment / finding")
    seed_mtpd(cur)
    print("[7] bcm.bcp_binder_export")
    seed_binders(cur)
    print("[8] bcm_lc.bia_records")
    seed_bia(cur)
    print("[9] bcm_lc.recovery_strategies")
    seed_strategies(cur)
    print("[10] bcm_lc.drills")
    seed_drills(cur)
    print("[11] rrp.rrp_event")
    seed_rrp_events(cur)

    # ── Skipped tables (reported, not seeded) ─────────────────────────────────
    note_skip("opres.opres_pillar", "Org-agnostic shared SAMA OpRes pillar catalog (organisation_id NULL).")
    note_skip("opres.opres_assessment", "Already has 3 org-1 rows; SAMA bank OpRes construct, not a public-authority fit.")
    note_skip("opres.opres_assessment_cell", "Already has 13 org-1 rows; child of opres_assessment.")
    note_skip("rrp.rrp_cycle", "Already seeded (2 cycles); SAMA D-SIB bank construct. Only its event timeline is deepened.")
    note_skip("rrp.rrp_critical_function", "Already richly seeded (24 org-1 rows); bank-specific.")
    note_skip("rrp.rrp_indicator", "Already richly seeded (14 org-1 rows); bank-specific.")
    note_skip("rrp.rrp_recovery_option", "Already richly seeded (16 org-1 rows); bank-specific.")
    note_skip("bcm.* core registers", "bia_assessments/bia_records/critical_processes/bcp_plans/exercises/crisis_* "
                                       "etc. already seeded and used as FK parents; do-not-modify.")
    note_skip("bcm shared catalogs", "exercise_scenarios / exercise_scenario_inject / peak_seasons / plan_templates "
                                      "are org-agnostic shared reference tables.")
    note_skip("bcm_lc.invocations", "Already has 3 org-1 banking-themed rows; not mixing a second theme into a populated table.")

    blockers.append(
        "bcm.exercise_observer_assignment CHECK (CK_exercise_observer_assignment_role) offers no "
        "NCEMA/DGA government-regulator liaison value. Closest allowed roles "
        "(BOARD_OBSERVER / COMPLIANCE_OBSERVER / INTERNAL_AUDIT) were used for the government context. "
        "Constraint NOT altered (insert-only safety). Manual action optional if a government-liaison "
        "observer role is desired."
    )

    # ── Final verification ────────────────────────────────────────────────────
    print("\n" + "=" * 78)
    print("VERIFICATION — org-1 row counts in seeded tables")
    print("=" * 78)
    verify_tables = [
        "bcm.exercise_run", "bcm.exercise_run_inject", "bcm.exercise_run_response",
        "bcm.exercise_observer_assignment", "bcm.exercise_observation",
        "bcm.mtpd_inheritance_assessment", "bcm.mtpd_inheritance_finding",
        "bcm.bcp_binder_export", "bcm_lc.bia_records", "bcm_lc.recovery_strategies",
        "bcm_lc.drills", "rrp.rrp_event",
    ]
    grand = 0
    for t in verify_tables:
        cur.execute(f"SELECT COUNT(*) FROM {t} WHERE organisation_id=1 AND is_deleted=0")
        n = cur.fetchone()[0]
        grand += n
        ins = summary.get(t, 0)
        print(f"  {t:<38} org1_total={n:<4} inserted_this_run={ins}")

    total_inserted = sum(summary.values())
    print("\n" + "-" * 78)
    print(f"TOTAL inserted this run: {total_inserted}")
    print(f"Grand total org-1 rows across seeded tables: {grand}")

    print("\nSKIPPED tables:")
    for t, why in skipped:
        print(f"  - {t}: {why}")

    print("\nBLOCKERS (no schema change made):")
    for b in blockers:
        print(f"  ! {b}")

    if total_inserted == 0:
        print("\nIdempotent re-run confirmed: 0 rows inserted.")

    conn.close()


if __name__ == "__main__":
    main()
