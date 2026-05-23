# -*- coding: utf-8 -*-
"""Closeout seed — expand DGA + SAMA_OPRES + add multi-tenant BCM clauses.

Final data-driven slice in the sprint-7 arc. Completes the
bcm.compliance_mappings picker so every framework dropdown is
fully populated.

Run:
    python seed_bcm_closeout.py
"""
from __future__ import annotations
import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')
sys.stdout = sys.stdout  # noqa

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

ORG_TSS = 1
inserted = 0
skipped = 0
errors = 0


def run_insert(cur, check_sql, check_params, insert_sql, insert_params, label):
    global inserted, skipped, errors
    try:
        cur.execute(check_sql, check_params)
        if cur.fetchone():
            skipped += 1
            return 0
        cur.execute(insert_sql, insert_params)
        inserted += 1
        print(f"  +1 {label}")
        return 1
    except Exception as e:
        errors += 1
        print(f"  ! {label}: {str(e)[:200]}")
        return 0


def section(name):
    print(f"\n══ {name} ═══════════════════════════════════════════════")


def main():
    conn = pyodbc.connect(CONN_STR)
    conn.autocommit = True
    cur = conn.cursor()

    # ──────────────────────────────────────────────────────────────────
    # DGA — Digital Government Authority BCM expansion
    # ──────────────────────────────────────────────────────────────────
    section("DGA — Digital Government Authority BCM clauses")

    DGA = [
        ('DGA-BCM-04', 'CONTINUITY', 'Digital service uptime guarantee — government services',
         'ضمان جاهزية الخدمات الرقمية الحكومية',
         'Government-facing digital services maintain ≥99.5% availability monthly.'),
        ('DGA-BCM-05', 'CONTINUITY', 'Citizen service channel redundancy',
         'تعدد قنوات الخدمات للمواطن',
         'Multiple channels (web, mobile, IVR, branch) for every citizen-facing service.'),
        ('DGA-BCM-06', 'GOVERNANCE', 'DGA-aligned BCM reporting cadence',
         'تواتر تقارير BCM المتوافقة مع DGA',
         'Quarterly DGA-aligned BCM reports for government-service-providing entities.'),
        ('DGA-BCM-07', 'EXERCISES', 'DGA joint exercise participation',
         'المشاركة في التمارين المشتركة مع DGA',
         'Annual joint exercise with DGA-coordinated government-service ecosystem.'),
        ('DGA-BCM-08', 'INCIDENTS', 'DGA notification of incidents impacting govt services',
         'إخطار DGA بالحوادث المؤثرة على الخدمات الحكومية',
         'DGA notified within 4h for incidents impacting government-service delivery.'),
        ('DGA-BCM-09', 'INTEROP', 'Government-data interoperability continuity',
         'استمرارية تبادل البيانات الحكومية',
         'Continuity of inter-agency data flows (Tadawul, ZATCA, MoF, MOH integrations).'),
        ('DGA-BCM-10', 'RECOVERY', 'Citizen-data integrity verification post-recovery',
         'التحقق من سلامة بيانات المواطنين بعد التعافي',
         'Sample-based verification of citizen data after any DR / BC activation.'),
    ]

    for code, domain, title_en, title_ar, description in DGA:
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework='DGA' AND requirement_code=?
               AND organisation_id=? AND is_deleted=0""",
            [code, ORG_TSS],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), 'DGA', ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'DGA digital-services BCM mapping (closeout).', 0, GETDATE())""",
            [ORG_TSS, code, title_en, title_ar, description, domain],
            f"{code:14} [{domain:12}] {title_en[:55]}")

    # ──────────────────────────────────────────────────────────────────
    # SAMA Operational Resilience (OPRES) — full mapping
    # ──────────────────────────────────────────────────────────────────
    section("SAMA OPRES — Operational Resilience expansion")

    OPRES = [
        ('OPRES-6', 'IDENT', 'Important business services identification',
         'تحديد الخدمات التجارية المهمة',
         'IBS register maintained; each IBS mapped to delivery chain (people/process/tech/3rd-party).'),
        ('OPRES-7', 'TOLERANCE', 'Impact tolerance setting for severe-but-plausible',
         'تحديد حدود التحمّل لسيناريوهات شديدة لكنّها معقولة',
         'For each IBS, impact tolerance set considering severe-but-plausible scenarios.'),
        ('OPRES-8', 'MAPPING', 'End-to-end mapping of IBS delivery',
         'الرسم الكامل لتسليم الخدمات التجارية المهمة',
         'Each IBS mapped through every system + dependency + 3rd-party.'),
        ('OPRES-9', 'TESTING', 'Operational resilience testing programme',
         'برنامج اختبار المرونة التشغيلية',
         'Annual testing of each IBS against severe-but-plausible scenarios.'),
        ('OPRES-10', 'GOVERN', 'Board operational-resilience oversight',
         'إشراف مجلس الإدارة على المرونة التشغيلية',
         'Board reviews operational-resilience posture quarterly; signs annual statement.'),
        ('OPRES-11', 'COMMS', 'Communications during operational disruption',
         'الاتصالات خلال الاضطرابات التشغيلية',
         'Multi-channel customer + employee + regulator comms during IBS disruption.'),
        ('OPRES-12', 'LEARN', 'Lessons + continuous-improvement cycle',
         'الدروس وحلقة التحسين المستمر',
         'Each disruption + test + exercise feeds lessons + CAPA + plan refresh.'),
    ]

    for code, domain, title_en, title_ar, description in OPRES:
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework='SAMA_OPRES' AND requirement_code=?
               AND organisation_id=? AND is_deleted=0""",
            [code, ORG_TSS],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), 'SAMA_OPRES', ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'SAMA Operational Resilience mapping (closeout).', 0, GETDATE())""",
            [ORG_TSS, code, title_en, title_ar, description, domain],
            f"{code:14} [{domain:10}] {title_en[:55]}")

    # ──────────────────────────────────────────────────────────────────
    # Aramco (org=2) — copy core ISO 22301 + SAMA BCF baseline
    # ──────────────────────────────────────────────────────────────────
    section("Aramco (org=2) — replicate ISO 22301 + SAMA BCF baseline")

    ORG_ARAMCO = 2
    cur.execute(
        """SELECT source_framework, requirement_code, requirement_title_en,
                  requirement_title_ar, requirement_description, domain
           FROM bcm.compliance_mappings
           WHERE organisation_id=1 AND source_framework IN ('ISO_22301','SAMA_BCF','NCA_OTCC')
           AND is_deleted=0 ORDER BY source_framework, requirement_code""",
    )
    org1_rows = cur.fetchall()
    print(f"  found {len(org1_rows)} TSS rows to replicate to Aramco")
    for fw, code, t_en, t_ar, desc, domain in org1_rows:
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework=? AND requirement_code=?
               AND organisation_id=? AND is_deleted=0""",
            [fw, code, ORG_ARAMCO],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'Multi-tenant replicate for Aramco (closeout).', 0, GETDATE())""",
            [ORG_ARAMCO, fw, code, t_en, t_ar, desc, domain],
            f"AR {fw:11} {code:18} {t_en[:45]}")

    # ──────────────────────────────────────────────────────────────────
    # STC Bank (org=3) — same replication
    # ──────────────────────────────────────────────────────────────────
    section("STC Bank (org=3) — replicate ISO 22301 + SAMA BCF baseline")

    ORG_STC = 3
    for fw, code, t_en, t_ar, desc, domain in org1_rows:
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework=? AND requirement_code=?
               AND organisation_id=? AND is_deleted=0""",
            [fw, code, ORG_STC],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'Multi-tenant replicate for STC (closeout).', 0, GETDATE())""",
            [ORG_STC, fw, code, t_en, t_ar, desc, domain],
            f"STC {fw:11} {code:18} {t_en[:45]}")

    print(f"\n══════ Summary ══════")
    print(f"  inserted: {inserted}")
    print(f"  skipped (already present): {skipped}")
    print(f"  errors:   {errors}")

    cur.close()
    conn.close()


if __name__ == '__main__':
    main()
