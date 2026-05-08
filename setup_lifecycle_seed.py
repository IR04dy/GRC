# -*- coding: utf-8 -*-
"""Seed bilingual demo data into the five lifecycle schemas.

Run this AFTER setup_lifecycle_db.py — it assumes the schemas + tables exist.
Idempotent: deletes prior demo rows for the demo organisation_id, then inserts
fresh ones. Safe to re-run.

Volumes (per the original Phase F plan):
  risk_lc.treatment_plans      5 rows
  audit_lc.findings            8 rows
  privacy_lc.dsr_cases        12 rows
  bcm_lc.invocations           3 rows
  cyber_lc.vulnerabilities    30 rows

All rows belong to organisation_id = 1 (the seeded demo tenant). User-visible
strings are bilingual EN/AR — Saudi-native phrasing per
feedback_saudi_native_arabic.md.
"""
import os
import random
import sys
from datetime import date, datetime, timedelta

import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

ORG_ID = 1                                     # demo tenant
random.seed(1337)                              # reproducible demo data
NOW = datetime(2026, 5, 7, 10, 0, 0)
TODAY = NOW.date()


# ─── Helpers ────────────────────────────────────────────────────────────────

def days(n): return NOW - timedelta(days=n)
def days_ahead(n): return (TODAY + timedelta(days=n))


def clear(cur, table, where=f'organisation_id = {ORG_ID}'):
    cur.execute(f'DELETE FROM {table} WHERE {where}')


# ─── Risk lifecycle — treatment plans ──────────────────────────────────────

def seed_risk(cur):
    print("\n── risk_lc.treatment_plans (5 rows) ──")
    clear(cur, 'risk_lc.treatment_plans')

    rows = [
        # (external_id, risk_id, strategy, status, cost_sar_k, rationale_en, rationale_ar)
        ('TP-2026-001', 1, 'MITIGATE', 'IN_PROGRESS',  450,
         'Deploy MFA across all SAMA-regulated systems; phased rollout starting with privileged users',
         'تطبيق المصادقة متعددة العوامل على الأنظمة الخاضعة لساما؛ نشر تدريجي يبدأ بالمستخدمين الخاصين'),
        ('TP-2026-002', 2, 'TRANSFER', 'APPROVED',     2_400,
         'Cyber-insurance renewal with expanded ransomware coverage; per Tadawul listing requirements',
         'تجديد التأمين السيبراني مع توسيع تغطية برامج الفدية؛ وفق متطلبات إدراج تداول'),
        ('TP-2026-003', 3, 'MITIGATE', 'SUBMITTED',    180,
         'Quarterly RCSA refresh aligned to RBSF Operational Risk',
         'تحديث ربعي للتقييم الذاتي للمخاطر متوافق مع إطار المخاطر التشغيلية'),
        ('TP-2026-004', 4, 'AVOID',    'DRAFT',          0,
         'Discontinue legacy on-prem core banking module; migrate to managed cloud tier',
         'إيقاف وحدة النواة المصرفية القديمة محلياً؛ الانتقال إلى الطبقة السحابية المُدارة'),
        ('TP-2026-005', 5, 'MITIGATE', 'COMPLETED',     320,
         'Vendor concentration cap — diversify CSP across two regions',
         'الحد من تركز المورّدين — توزيع مزود السحابة عبر منطقتين'),
    ]
    sql = """INSERT INTO risk_lc.treatment_plans
        (external_id, organisation_id, risk_id, strategy, status,
         rationale_en, rationale_ar, cost_sar_k, cost_benefit_en, cost_benefit_ar,
         created_at, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"""
    for ext, risk_id, strat, status, cost, ren, ran in rows:
        cur.execute(sql, ext, ORG_ID, risk_id, strat, status, ren, ran, cost,
                    f'NPV +{cost*2}K SAR over 3y', f'صافي القيمة الحالية +{cost*2} ألف ريال على 3 سنوات',
                    days(random.randint(15, 90)))
    print(f"  inserted {len(rows)} treatment plans")


# ─── Audit lifecycle — findings ────────────────────────────────────────────

def seed_audit(cur):
    print("\n── audit_lc.findings (8 rows) ──")
    clear(cur, 'audit_lc.findings')

    rows = [
        # (engagement, severity, status, title_en, title_ar, condition_en, condition_ar)
        ('ENG-IA-001', 'HIGH',     'OPEN',
         'User access reviews not completed quarterly for SAMA-scoped applications',
         'لم تُجرَ مراجعات صلاحيات المستخدمين ربعياً للتطبيقات الخاضعة لساما',
         'Sample of 12 SAMA-scoped systems showed last UAR was 8+ months ago for 7 of them.',
         'أظهرت عينة من 12 نظاماً خاضعاً لساما أن آخر مراجعة كانت قبل 8 أشهر أو أكثر لـ7 منها.'),
        ('ENG-IA-001', 'MEDIUM',   'MGMT_RESPONSE',
         'Segregation of duties exception — payments approver also creates vendor master',
         'مخالفة فصل المهام — معتمد المدفوعات ينشئ بيانات الموردين أيضاً',
         'In 4 of 30 sampled vendors, the same user created the master record and approved the first payment.',
         'في 4 من أصل 30 مورّداً عينة، أنشأ المستخدم نفسه السجل الرئيسي واعتمد أول دفعة.'),
        ('ENG-IA-002', 'CRITICAL', 'IN_REMEDIATION',
         'Privileged accounts without MFA on production database servers',
         'حسابات صلاحيات عالية بدون مصادقة متعددة على خوادم قواعد البيانات الإنتاجية',
         'Discovered 3 sysadmin accounts in production with single-factor authentication only.',
         'تم اكتشاف 3 حسابات إدارة نظام في الإنتاج بمصادقة عامل واحد فقط.'),
        ('ENG-IA-002', 'LOW',      'CLOSED',
         'Backup retention documentation outdated',
         'وثائق فترة الاحتفاظ بالنسخ الاحتياطية قديمة',
         'Backup policy v2.1 still references the decommissioned tape rotation schedule from 2023.',
         'سياسة النسخ الاحتياطي الإصدار 2.1 ما زالت تشير إلى جدول الأشرطة الموقوف منذ 2023.'),
        ('ENG-IA-003', 'HIGH',     'AWAITING_VERIFICATION',
         'Vendor due diligence missing for 2 critical TPRM vendors',
         'العناية الواجبة مفقودة لمورّدَين حرجَين في إدارة مخاطر الأطراف الثالثة',
         'TPRM register contains 24 critical vendors; 2 had no DD on file as of audit fieldwork.',
         'سجل إدارة مخاطر الأطراف الثالثة يضم 24 مورّداً حرجاً؛ 2 منهم بدون عناية واجبة وقت الميدان.'),
        ('ENG-IA-003', 'MEDIUM',   'OPEN',
         'IT change management — emergency-CR rate exceeds policy threshold',
         'إدارة التغيير لتقنية المعلومات — معدل التغييرات الطارئة يتجاوز الحد المسموح',
         'Q1 2026 emergency-change ratio was 18% versus the policy ceiling of 10%.',
         'بلغت نسبة التغييرات الطارئة في الربع الأول 2026 ما قدره 18% مقابل سقف السياسة 10%.'),
        ('ENG-IA-004', 'MEDIUM',   'MGMT_RESPONSE',
         'PDPL data retention not enforced on legacy CRM',
         'الاحتفاظ بالبيانات حسب نظام حماية البيانات الشخصية غير مفعّل على نظام إدارة العملاء القديم',
         'Customer records older than the 7-year retention period were still active in legacy CRM.',
         'سجلات عملاء أقدم من فترة الاحتفاظ المحددة 7 سنوات لا تزال نشطة في نظام العملاء القديم.'),
        ('ENG-IA-004', 'HIGH',     'IN_REMEDIATION',
         'NCA ECC sub-control 2-3-1 — network segmentation gaps in OT environment',
         'NCA ECC الضابط الفرعي 2-3-1 — فجوات في تقسيم الشبكة في بيئة التشغيل',
         'OT network shares physical VLAN with corporate IT in 2 of 5 sites tested.',
         'شبكة التشغيل تشترك في VLAN فيزيائي مع الشبكة المؤسسية في موقعين من 5 مواقع تم اختبارها.'),
    ]
    sql = """INSERT INTO audit_lc.findings
        (external_id, organisation_id, engagement_code, ref_number,
         title_en, title_ar, severity, status, control_crosswalk,
         condition_en, condition_ar, criteria_en, criteria_ar,
         cause_en, cause_ar, recommendation_en, recommendation_ar,
         target_date, owner_role, raised_at, capa_ids, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"""
    for i, (eng, sev, status, ten, tan, cen, can) in enumerate(rows, 1):
        ext = f'FIN-2026-{i:03d}'
        ref = f'FIN-{i:03d}'
        cur.execute(sql, ext, ORG_ID, eng, ref, ten, tan, sev, status,
                    'NCA-ECC,SAMA-CSF',
                    cen, can,
                    'Per IA charter and applicable framework standards', 'وفق ميثاق المراجعة الداخلية والمعايير الإطارية',
                    'Control design weakness; manual process without compensating control', 'ضعف في تصميم الضابط؛ عملية يدوية بدون ضابط تعويضي',
                    'Implement compensating control + automate where possible', 'تطبيق ضابط تعويضي + الأتمتة حيث أمكن',
                    days_ahead(random.randint(30, 90)), 'CISO',
                    days(random.randint(10, 60)), '')
    print(f"  inserted {len(rows)} findings")


# ─── Privacy lifecycle — DSR cases ──────────────────────────────────────────

def seed_privacy(cur):
    print("\n── privacy_lc.dsr_cases (12 rows) ──")
    clear(cur, 'privacy_lc.dsr_cases')

    types = ['ACCESS', 'RECTIFICATION', 'ERASURE', 'OBJECTION', 'PORTABILITY', 'WITHDRAW_CONSENT']
    statuses = ['INTAKE', 'VERIFYING_IDENTITY', 'COLLECTING', 'REDACTING', 'DELIVERED',
                'EXTENDED', 'REJECTED']
    names_en_ar = [
        ('Mohammed Al-Qahtani', 'محمد القحطاني'),
        ('Sara Al-Zahrani',     'سارة الزهراني'),
        ('Khalid Al-Otaibi',    'خالد العتيبي'),
        ('Fatimah Al-Harbi',    'فاطمة الحربي'),
        ('Abdullah Al-Ghamdi',  'عبدالله الغامدي'),
        ('Noura Al-Sulaiman',   'نورة السليمان'),
        ('Yousef Al-Dosari',    'يوسف الدوسري'),
        ('Reem Al-Mutairi',     'ريم المطيري'),
        ('Bandar Al-Shahrani',  'بندر الشهراني'),
        ('Lina Al-Asiri',       'لينا العسيري'),
        ('Saud Al-Anazi',       'سعود العنزي'),
        ('Maha Al-Subaie',      'مها السبيعي'),
    ]

    sql = """INSERT INTO privacy_lc.dsr_cases
        (external_id, organisation_id, type, subject_name, national_id_last4,
         contact_email, status, filed_at, sla_deadline, extended,
         identity_verified_at, verification_method, data_collected_at,
         redaction_completed_at, delivery_method, delivered_at,
         ropa_ids, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"""

    for i, (en, ar) in enumerate(names_en_ar, 1):
        ext = f'DSR-2026-{i:03d}'
        dsr_type = types[i % len(types)]
        status = statuses[i % len(statuses)]
        filed_at = days(random.randint(2, 28))
        deadline = (filed_at + timedelta(days=30)).date()
        extended = 1 if status == 'EXTENDED' else 0
        if extended:
            deadline = deadline + timedelta(days=30)

        verified_at = filed_at + timedelta(days=2) if status not in ('INTAKE',) else None
        collected_at = filed_at + timedelta(days=8) if status in ('COLLECTING', 'REDACTING', 'DELIVERED') else None
        redacted_at  = filed_at + timedelta(days=16) if status in ('REDACTING', 'DELIVERED') else None
        delivered_at = filed_at + timedelta(days=24) if status == 'DELIVERED' else None

        cur.execute(sql,
                    ext, ORG_ID, dsr_type, f'{en} | {ar}',
                    str(1000 + i)[-4:], f'subject{i:02d}@example.sa',
                    status, filed_at, deadline, extended,
                    verified_at, 'NAFATH' if verified_at else None,
                    collected_at,
                    redacted_at,
                    'SECURE_PORTAL' if delivered_at else None, delivered_at,
                    'ROPA-2026-001,ROPA-2026-007')
    print(f"  inserted {len(names_en_ar)} DSR cases")


# ─── BCM lifecycle — invocations ────────────────────────────────────────────

def seed_bcm(cur):
    print("\n── bcm_lc.invocations (3 rows) ──")
    clear(cur, 'bcm_lc.invocations')

    rows = [
        ('INV-2026-001', 'SEV2', 'STAND_DOWN',
         'JED-DC power outage — failover to RIY-DC2',
         'انقطاع كهرباء مركز بيانات جدة — تحويل إلى الرياض-DC2',
         'Sudden grid outage at primary Jeddah DC; UPS held for 8 minutes; failover successful',
         'انقطاع مفاجئ للشبكة في مركز جدة الأساسي؛ صمدت بطاريات الـUPS 8 دقائق؛ نجح التحويل',
         'BIA-2026-001,BIA-2026-003', 'JED-DC,RIY-DC2'),
        ('INV-2026-002', 'SEV1', 'POST_INCIDENT_REVIEW',
         'Core banking ransomware-precursor activity — preventive failover',
         'نشاط مشتبه بسلوك فدية في النواة المصرفية — تحويل وقائي',
         'EDR detected lateral-movement IOCs; CISO + BCM activated full DR site as precaution',
         'كشف نظام الحماية مؤشرات حركة جانبية؛ فعّل CISO + استمرارية الأعمال موقع التعافي كاملاً احتياطاً',
         'BIA-2026-002', 'RIY-HQ,JED-DC'),
        ('INV-2026-003', 'SEV3', 'INVOKED',
         'Tadawul market-data feed disruption — manual reconciliation invoked',
         'انقطاع تغذية بيانات السوق من تداول — تفعيل المطابقة اليدوية',
         'Tadawul vendor feed gap of 23 minutes; manual fallback playbook in effect',
         'فجوة في تغذية تداول لمدة 23 دقيقة؛ تطبيق دليل الاحتياط اليدوي',
         'BIA-2026-005', 'RIY-HQ'),
    ]
    sql = """INSERT INTO bcm_lc.invocations
        (external_id, organisation_id, title_en, title_ar, severity,
         trigger_event_en, trigger_event_ar, affected_bia_ids, affected_sites,
         status, detected_at, declared_at, invoked_at, stabilised_at, stand_down_at,
         actual_rto_hours, sama_notified, ncema_notified, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"""
    for ext, sev, status, ten, tan, ren, ran, bias, sites in rows:
        detected = days(random.randint(5, 30))
        cur.execute(sql, ext, ORG_ID, ten, tan, sev, ren, ran, bias, sites, status,
                    detected,
                    detected + timedelta(minutes=12),
                    detected + timedelta(minutes=25) if status != 'STAND_DOWN' or sev == 'SEV2' else None,
                    detected + timedelta(hours=4)   if status in ('STAND_DOWN', 'POST_INCIDENT_REVIEW') else None,
                    detected + timedelta(hours=6)   if status in ('STAND_DOWN', 'POST_INCIDENT_REVIEW') else None,
                    4.5 if status in ('STAND_DOWN', 'POST_INCIDENT_REVIEW') else None,
                    1 if sev in ('SEV1', 'SEV2') else 0,
                    1 if sev == 'SEV1' else 0)
    print(f"  inserted {len(rows)} invocations")


# ─── Cyber lifecycle — vulnerabilities ─────────────────────────────────────

def seed_cyber(cur):
    print("\n── cyber_lc.vulnerabilities (30 rows) ──")
    clear(cur, 'cyber_lc.vulnerabilities')

    severities = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
    sla_days   = {'CRITICAL': 7, 'HIGH': 30, 'MEDIUM': 60, 'LOW': 90}
    statuses   = ['NEW', 'TRIAGED', 'OWNER_ASSIGNED', 'IN_REMEDIATION',
                  'AWAITING_VERIFICATION', 'CLOSED', 'RISK_ACCEPTED', 'FALSE_POSITIVE']
    sources    = ['TENABLE', 'QUALYS', 'CROWDSTRIKE', 'PEN_TEST', 'MANUAL']

    titles = [
        ('Apache Log4j RCE (CVE-2021-44228 echo)', 'تنفيذ تعليمات عن بُعد في Log4j'),
        ('OpenSSL buffer overflow in TLS handshake', 'تجاوز الذاكرة في مصافحة TLS بـ OpenSSL'),
        ('Outdated Spring Boot exposing actuator', 'إصدار Spring Boot قديم يكشف نقاط الإدارة'),
        ('SQL injection in legacy reporting endpoint', 'حقن SQL في نقطة تقارير قديمة'),
        ('Cross-site scripting on customer portal', 'برمجة عبر المواقع في بوابة العملاء'),
        ('Weak TLS ciphers on internal LB', 'تشفير TLS ضعيف على الموازن الداخلي'),
        ('Default credentials on switch management', 'بيانات اعتماد افتراضية على إدارة المبدّل'),
        ('Insecure direct object reference in API', 'إشارة كائن مباشر غير آمنة في الواجهة البرمجية'),
        ('SMBv1 enabled on file server', 'تفعيل SMBv1 على خادم الملفات'),
        ('Privileged container in K8s namespace', 'حاوية صلاحيات عالية في فضاء K8s'),
    ]

    sql = """INSERT INTO cyber_lc.vulnerabilities
        (external_id, organisation_id, asset_id, cve_id, title_en, title_ar,
         description_en, description_ar, severity, cvss_score, detection_source,
         status, detected_at, sla_deadline, breach_count, sla_extended, is_deleted)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)"""

    for i in range(1, 31):
        ext = f'VULN-2026-{i:04d}'
        ten, tan = titles[i % len(titles)]
        sev = severities[i % len(severities)]
        cvss = {'CRITICAL': 9.8, 'HIGH': 7.5, 'MEDIUM': 5.4, 'LOW': 3.1}[sev]
        status = statuses[i % len(statuses)]
        source = sources[i % len(sources)]
        asset_id = f'AST-{1 + (i % 12):03d}'
        cve = f'CVE-2026-{1000 + i}'
        detected = days(random.randint(3, 60))
        deadline = (detected + timedelta(days=sla_days[sev])).date()
        breach = 1 if (TODAY - deadline).days > 0 and status not in ('CLOSED',) else 0

        cur.execute(sql, ext, ORG_ID, asset_id, cve, ten, tan,
                    f'Vulnerability {ten} affecting {asset_id}; remediation per NCA ECC 2-13.',
                    f'ثغرة {tan} تؤثر على الأصل {asset_id}؛ المعالجة وفق NCA ECC 2-13.',
                    sev, cvss, source, status, detected, deadline, breach, 0)
    print(f"  inserted 30 vulnerabilities")


# ─── Main ───────────────────────────────────────────────────────────────────

def main():
    print(f"Connecting to {CONN_STR.split(';')[1]}…")
    conn = pyodbc.connect(CONN_STR)
    conn.autocommit = True
    cur = conn.cursor()

    seed_risk(cur)
    seed_audit(cur)
    seed_privacy(cur)
    seed_bcm(cur)
    seed_cyber(cur)

    print("\n" + "=" * 60)
    print("All lifecycle schemas seeded for organisation_id = 1.")
    print("Hit GET /api/risk-lc/treatment-plans (etc.) to verify.")
    print("=" * 60)


if __name__ == '__main__':
    main()
