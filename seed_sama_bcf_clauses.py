# -*- coding: utf-8 -*-
"""Seed SAMA Business Continuity Framework (BCF) clause-level mapping.

Plan reference: BCM_GRC_GAP_ANALYSIS_AND_PLAN.md Slice 7-F
Addresses gap BCM-P0-20 — BCM Manager needs clause-level SAMA BCF
mapping (8 chapters × 47 sub-clauses) for quarterly attestation,
not just the framework-level row.

Targets: `bcm.compliance_mappings`
Tenant: org_id=1 (TSS — Saudi Bank) initially; multi-tenant later.

Idempotent: every INSERT guarded by `IF NOT EXISTS` keyed on
(source_framework='SAMA_BCF', requirement_code, organisation_id).

Run:
    python seed_sama_bcf_clauses.py
"""
from __future__ import annotations

import sys
import pyodbc

sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

ORG_TSS = 1
SOURCE_FRAMEWORK = 'SAMA_BCF'

# ──────────────────────────────────────────────────────────────────────
# SAMA BCF — 8 chapters × clauses (47 total)
# Bilingual title + scope notes. Status seeded as NOT_ASSESSED.
# ──────────────────────────────────────────────────────────────────────

CLAUSES = [
    # Chapter 1 — Governance & Oversight (6)
    ('1.1', 'GOVERNANCE', 'BCM Policy Statement',
     'بيان سياسة استمرارية الأعمال',
     'Board-approved BCM policy document signed annually by CEO + Board Chair.'),
    ('1.2', 'GOVERNANCE', 'Board / Executive Committee Oversight',
     'إشراف مجلس الإدارة / اللجنة التنفيذية',
     'BCM standing agenda item on Risk Committee + quarterly board pack.'),
    ('1.3', 'GOVERNANCE', 'BCM Roles & Responsibilities',
     'الأدوار والمسؤوليات في استمرارية الأعمال',
     'BCM RACI defined: BCM Lead, BIA owners, Plan owners, Crisis Team, Comms.'),
    ('1.4', 'GOVERNANCE', 'BCM Programme Resources',
     'موارد برنامج استمرارية الأعمال',
     'Annual BCM budget approved + staffing model + tooling investment.'),
    ('1.5', 'GOVERNANCE', 'BCM Training & Awareness',
     'التدريب والتوعية في استمرارية الأعمال',
     'Annual mandatory BCM training for all staff; specialised drills for L1/L2.'),
    ('1.6', 'GOVERNANCE', 'Internal Audit of BCM',
     'التدقيق الداخلي على استمرارية الأعمال',
     'IA includes BCM in 3-year audit universe; engagement at least every 2 years.'),

    # Chapter 2 — Embedding BCM in Organisation (5)
    ('2.1', 'EMBEDDING', 'BCM Integration with Strategy',
     'تكامل استمرارية الأعمال مع الاستراتيجية',
     'BCM considerations in M&A, new-product introduction, vendor selection.'),
    ('2.2', 'EMBEDDING', 'BCM Culture & Awareness',
     'ثقافة الوعي باستمرارية الأعمال',
     'Communication campaigns, BCM week, scenario-based learning.'),
    ('2.3', 'EMBEDDING', 'Performance Metrics & KPIs',
     'مقاييس الأداء ومؤشرات استمرارية الأعمال',
     'BCM KPIs reported quarterly: % BIAs current, exercise pass rate, recovery actual vs target.'),
    ('2.4', 'EMBEDDING', 'Continuous Improvement Process',
     'عملية التحسين المستمر',
     'PDCA cycle for BCM — exercise/incident → lessons → CAPA → plan refresh.'),
    ('2.5', 'EMBEDDING', 'Documentation Management',
     'إدارة الوثائق',
     'Version control, change history, distribution + acknowledgement tracking.'),

    # Chapter 3 — Business Impact Analysis (BIA) (6)
    ('3.1', 'BIA', 'BIA Methodology',
     'منهجية تحليل تأثير الأعمال',
     'Documented methodology with time-band × impact-dimension matrix; reviewed annually.'),
    ('3.2', 'BIA', 'Critical Activities Identification',
     'تحديد الأنشطة الحرجة',
     'Process inventory with criticality tiers (TIER_1/2/3) approved by business owners.'),
    ('3.3', 'BIA', 'RTO & RPO Determination',
     'تحديد أهداف وقت التعافي ونقطة التعافي',
     'RTO/RPO/MTPD for every critical process; agreed in writing by process owner + CFO.'),
    ('3.4', 'BIA', 'Dependency Mapping (upstream + downstream)',
     'رسم الاعتمادات (المنبع والمصب)',
     'Process-to-process, process-to-system, process-to-vendor dependencies mapped.'),
    ('3.5', 'BIA', 'Recovery Resource Requirements',
     'متطلبات موارد التعافي',
     'People/systems/sites/data/vendors needed at each recovery time point.'),
    ('3.6', 'BIA', 'BIA Review & Update Cycle',
     'دورة مراجعة وتحديث تحليل تأثير الأعمال',
     'Annual BIA refresh + trigger-based (new product, M&A, regulator change).'),

    # Chapter 4 — Risk Assessment (5)
    ('4.1', 'RISK', 'Threat & Risk Identification',
     'تحديد التهديدات والمخاطر',
     'Comprehensive threat catalogue (cyber, physical, supply-chain, regulatory, climate).'),
    ('4.2', 'RISK', 'Risk Analysis & Evaluation',
     'تحليل وتقييم المخاطر',
     'Likelihood × impact scoring; residual risk after controls; risk-appetite alignment.'),
    ('4.3', 'RISK', 'Threat Scenarios',
     'سيناريوهات التهديد',
     'Scenario library covering Hajj/Ramadan peaks, regional sandstorm, OT cyber, vendor failure.'),
    ('4.4', 'RISK', 'Risk Treatment',
     'معالجة المخاطر',
     'Treatment options (accept/transfer/mitigate/avoid) with control mapping + ROI justification.'),
    ('4.5', 'RISK', 'Risk Register & Monitoring',
     'سجل المخاطر والمراقبة',
     'KRIs with thresholds + escalation; quarterly heatmap to risk committee.'),

    # Chapter 5 — Business Continuity Strategy (6)
    ('5.1', 'STRATEGY', 'Strategy Options Analysis',
     'تحليل خيارات الاستراتيجية',
     'Multi-option analysis: hot/warm/cold sites, cloud failover, manual workaround.'),
    ('5.2', 'STRATEGY', 'Strategy Approval & Investment',
     'اعتماد الاستراتيجية والاستثمار',
     'Board sign-off on strategy + capital plan; multi-year roadmap.'),
    ('5.3', 'STRATEGY', 'Workplace Recovery Strategy',
     'استراتيجية التعافي لمكان العمل',
     'Alternate site capacity, work-from-home protocols, branch redirection.'),
    ('5.4', 'STRATEGY', 'Technology Recovery Strategy',
     'استراتيجية التعافي التقني',
     'DR architecture, RPO/RTO per system, regional failover (Riyadh ↔ Jeddah / AWS Bahrain).'),
    ('5.5', 'STRATEGY', 'Supplier & Vendor Strategy',
     'استراتيجية الموردين والبائعين',
     'Critical-vendor BCP attestation; alternate suppliers; exit-plan playbooks.'),
    ('5.6', 'STRATEGY', 'Information & Records Strategy',
     'استراتيجية المعلومات والسجلات',
     'Vital records protection, immutable backups, encryption at rest/in transit.'),

    # Chapter 6 — Plans & Procedures (6)
    ('6.1', 'PLANS', 'Business Continuity Plan (BCP)',
     'خطة استمرارية الأعمال',
     'BCP for every critical process; documented, approved, distributed, acknowledged.'),
    ('6.2', 'PLANS', 'Disaster Recovery Plan (DRP)',
     'خطة التعافي من الكوارث',
     'IT/OT DRP with runbooks, RACI, cutover procedures, validation steps.'),
    ('6.3', 'PLANS', 'Incident Response Plan (IRP)',
     'خطة الاستجابة للحوادث',
     'IRP integrated with cyber incident response + crisis declaration triggers.'),
    ('6.4', 'PLANS', 'Crisis Communication Plan',
     'خطة الاتصال أثناء الأزمات',
     'Audience matrix (board, regulator, customer, employee, media); bilingual templates.'),
    ('6.5', 'PLANS', 'Pandemic / Public Health Plan',
     'خطة الجوائح والصحة العامة',
     'Post-COVID lessons baked in; work-from-home enablement; supply-chain pandemic risk.'),
    ('6.6', 'PLANS', 'Plan Maintenance',
     'صيانة الخطط',
     'Annual review + trigger-based updates + version control + distribution log.'),

    # Chapter 7 — Exercises & Testing (5)
    ('7.1', 'EXERCISES', 'Exercise Strategy & Programme',
     'استراتيجية وبرنامج التمارين',
     'Multi-year exercise plan covering each critical process + each plan type.'),
    ('7.2', 'EXERCISES', 'Exercise Types Coverage',
     'تغطية أنواع التمارين',
     'Tabletop, walkthrough, functional, full-scale, simulation — at least one of each annually.'),
    ('7.3', 'EXERCISES', 'Exercise Cadence',
     'تواتر التمارين',
     'Quarterly DR drills, annual full-scale, monthly comms-tree test, ad-hoc for new plans.'),
    ('7.4', 'EXERCISES', 'Exercise Documentation',
     'توثيق التمارين',
     'Objectives, injects, observations, gaps, attendees, decisions log per exercise.'),
    ('7.5', 'EXERCISES', 'Lessons Learned & Improvement',
     'الدروس المستفادة والتحسين',
     'Every exercise + real incident triggers CAPA with owner and target date.'),

    # Chapter 8 — Reporting & Continuous Improvement (5)
    ('8.1', 'REPORTING', 'BCM Reporting to Board / Executive',
     'تقارير استمرارية الأعمال للمجلس / الإدارة التنفيذية',
     'Quarterly board pack with BCM dashboard (top risks, exercise outcomes, CAPA status).'),
    ('8.2', 'REPORTING', 'Regulatory Reporting (SAMA Major-Incident 6h)',
     'التقارير التنظيمية (إخطار ساما للحوادث الكبرى خلال 6 ساعات)',
     'SAMA major-incident notification within 6h (Cyber Framework v1.3); workflow + countdown.'),
    ('8.3', 'REPORTING', 'Annual BCM Review',
     'المراجعة السنوية لاستمرارية الأعمال',
     'Annual management review + maturity assessment + plan refresh + budget submission.'),
    ('8.4', 'REPORTING', 'External Review (Audit / 3rd Party)',
     'المراجعة الخارجية (التدقيق / طرف ثالث)',
     'Independent BCM review at least every 2 years by IA + every 3 years by external party.'),
    ('8.5', 'REPORTING', 'Continuous Improvement Loop',
     'حلقة التحسين المستمر',
     'PDCA: Plan-Do-Check-Act applied to BCM programme + maturity progression.'),

    # SAMA-specific addenda (3) — beyond generic BCF
    ('S.1', 'SAMA_SPECIFIC', 'Hajj/Ramadan Peak-Season Continuity',
     'استمرارية فترات الذروة — الحج ورمضان',
     'Documented peak-season continuity capacity for SADAD/MADA/SAMA settlement during Hajj + Ramadan.'),
    ('S.2', 'SAMA_SPECIFIC', 'Cross-Border Data Recovery Procedures',
     'إجراءات استعادة البيانات خارج الحدود',
     'PDPL Article 29-aligned cross-border recovery scenarios + SDAIA notification chain.'),
    ('S.3', 'SAMA_SPECIFIC', 'Outsourcing Continuity (SAMA Outsourcing Framework)',
     'استمرارية الاستعانة بمصادر خارجية',
     'Critical-vendor BCP attestation, exit-plan invocation criteria, SAMA outsourcing notification.'),
]


def main():
    conn = pyodbc.connect(CONN_STR)
    conn.autocommit = True
    cur = conn.cursor()

    inserted = 0
    skipped = 0
    errors = 0

    print(f"Seeding SAMA BCF — {len(CLAUSES)} clauses for org_id={ORG_TSS}\n")

    for code, domain, title_en, title_ar, description in CLAUSES:
        full_code = f"SAMA-BCF-{code}"
        try:
            # Two-step: check existence with a real SELECT, then INSERT only if needed.
            # Avoids the multi-statement batch that broke fetchone() on the
            # IF NOT EXISTS + INSERT + SELECT @@ROWCOUNT combination.
            cur.execute(
                """SELECT 1 FROM bcm.compliance_mappings
                   WHERE source_framework = ? AND requirement_code = ?
                   AND organisation_id = ? AND is_deleted = 0""",
                [SOURCE_FRAMEWORK, full_code, ORG_TSS],
            )
            existing = cur.fetchone()
            if existing:
                skipped += 1
                continue
            cur.execute(
                """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                       source_framework, requirement_code, requirement_title_en,
                       requirement_title_ar, requirement_description, domain,
                       is_placeholder, compliance_status, notes, is_deleted, created_at)
                   VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                       'Seeded from SAMA BCF clause-level mapping (Slice 7-F).', 0, GETDATE())""",
                [ORG_TSS, SOURCE_FRAMEWORK, full_code, title_en, title_ar, description, domain],
            )
            inserted += 1
            print(f"  +1 {full_code:18} [{domain:13}] {title_en}")
        except Exception as e:
            errors += 1
            print(f"  ! {full_code}: {str(e)[:200]}")

    print(f"\n══════ Summary ══════")
    print(f"  inserted: {inserted}")
    print(f"  skipped (already present): {skipped}")
    print(f"  errors:   {errors}")
    print(f"  total clauses defined: {len(CLAUSES)}")

    cur.close()
    conn.close()


if __name__ == '__main__':
    main()
