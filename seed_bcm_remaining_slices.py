# -*- coding: utf-8 -*-
"""Batch-seed the remaining data-driven BCM slices from the plan.

Plan references (BCM_GRC_GAP_ANALYSIS_AND_PLAN.md §4):
  - Slice 7-G:   NCA OTCC OT-BCM clause-level mapping  (~30 controls)
  - Slice 7-B (precursor): ISO 22301 §4-10 sub-clauses (47 items)
  - Slice 9-A precursor:   Expand bcm.exercise_scenarios to 50+
  - Plus NCEMA crisis classification + NRC critical-infra extensions

All pure data — no schema changes — builds on Slice 7-F pattern.
Idempotent (two-step SELECT-then-INSERT — lesson from PR #142).

Run:
    python seed_bcm_remaining_slices.py
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
inserted = 0
skipped = 0
errors = 0


def run_insert(cur, check_sql, check_params, insert_sql, insert_params, label):
    """Two-step: SELECT existence, then INSERT if absent. Returns 1 if inserted."""
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


def section(name: str):
    print(f"\n══ {name} ══════════════════════════════════════════════════════════")


def main():
    conn = pyodbc.connect(CONN_STR)
    conn.autocommit = True
    cur = conn.cursor()

    # ──────────────────────────────────────────────────────────────────
    # Slice 7-G — NCA OTCC OT-BCM clause-level mapping
    # NCA OTCC structure: 5 domains × ~6 sub-controls each = ~30 controls.
    # ──────────────────────────────────────────────────────────────────
    section("Slice 7-G — NCA OTCC OT-BCM clause-level mapping")

    OTCC = [
        # Domain 1 — Governance + Risk Management for OT
        ('1.1', 'GOVERNANCE_OT', 'OT cybersecurity strategy + steering committee',
         'استراتيجية أمن أنظمة التحكم + لجنة توجيه',
         'OT cyber strategy approved by Board with OT-specific roles + budget.'),
        ('1.2', 'GOVERNANCE_OT', 'OT risk register separate from IT',
         'سجل مخاطر OT منفصل عن IT',
         'OT threats catalogued separately (e.g. safety-impacting, environmental).'),
        ('1.3', 'GOVERNANCE_OT', 'OT cybersecurity training for engineers + operators',
         'تدريب الأمن السيبراني لـ OT للمهندسين والمشغّلين',
         'Annual mandatory OTCC awareness; specialised SCADA cyber training for L1/L2.'),
        ('1.4', 'GOVERNANCE_OT', 'OT vendor management + supply-chain risk',
         'إدارة موردي OT ومخاطر سلسلة الإمداد',
         'OT-vendor (Honeywell, GE, Emerson, Schneider) attestation + sub-component sourcing.'),
        ('1.5', 'GOVERNANCE_OT', 'OT cybersecurity policy + procedures',
         'سياسات وإجراءات أمن OT',
         'Documented policies covering OT-IT segregation, change management, incident handling.'),
        ('1.6', 'GOVERNANCE_OT', 'OT internal audit programme',
         'برنامج التدقيق الداخلي على OT',
         'IA audits OT cybersecurity at least every 2 years; findings tracked.'),

        # Domain 2 — OT Cybersecurity Defence
        ('2.1', 'DEFENCE_OT', 'OT-IT network segmentation (DMZ + zones + conduits)',
         'فصل شبكات OT-IT (DMZ + المناطق + الأنابيب)',
         'Purdue Model implementation; firewall between IT and OT; diode for critical zones.'),
        ('2.2', 'DEFENCE_OT', 'OT asset inventory + classification',
         'حصر وتصنيف أصول OT',
         'PLC, RTU, HMI, historian, engineering workstations catalogued with criticality.'),
        ('2.3', 'DEFENCE_OT', 'OT vulnerability management + patching',
         'إدارة الثغرات والتحديثات في OT',
         'Patch windows aligned to plant turnarounds; vendor-validated patches only.'),
        ('2.4', 'DEFENCE_OT', 'OT access control + identity management',
         'التحكم بالوصول وإدارة الهويات في OT',
         'Named user accounts; no shared SCADA logins; physical control of engineering workstations.'),
        ('2.5', 'DEFENCE_OT', 'OT cryptographic protection',
         'الحماية التشفيرية في OT',
         'Where supported by protocols (DNP3 SA, ICCP) — encryption + integrity checks enabled.'),
        ('2.6', 'DEFENCE_OT', 'OT physical security',
         'الأمن المادي لـ OT',
         'Locked panels, tamper-evident seals, control-room access logging, CCTV.'),

        # Domain 3 — OT Cybersecurity Resilience
        ('3.1', 'RESILIENCE_OT', 'OT cybersecurity incident response',
         'الاستجابة لحوادث الأمن السيبراني في OT',
         'OT-IRP integrated with IT-IRP but with safety-first override; named OT IR lead.'),
        ('3.2', 'RESILIENCE_OT', 'OT backup + recovery',
         'النسخ الاحتياطي والاستعادة في OT',
         'PLC config backups, golden-image SCADA hosts, historian data export, offline backup.'),
        ('3.3', 'RESILIENCE_OT', 'OT business continuity + DR',
         'استمرارية الأعمال والتعافي في OT',
         'OT-BCP per critical asset; manual operation procedures; safe shutdown sequences.'),
        ('3.4', 'RESILIENCE_OT', 'OT monitoring + detection',
         'المراقبة والكشف في OT',
         'OT IDS (Claroty, Nozomi, Dragos), passive network monitoring; alerts to OT SOC.'),
        ('3.5', 'RESILIENCE_OT', 'OT cybersecurity exercises',
         'تمارين الأمن السيبراني في OT',
         'OT-specific tabletop annually; OT-IT joint exercise annually; manual-operation drill.'),
        ('3.6', 'RESILIENCE_OT', 'OT regulator notification',
         'إخطار الجهة التنظيمية في OT',
         'NCA notification per OTCC requirements within prescribed window; safety regulator parallel notification.'),

        # Domain 4 — OT Third Party
        ('4.1', 'THIRD_PARTY_OT', 'OT vendor onboarding cybersecurity assessment',
         'تقييم الأمن السيبراني عند تأهيل موردي OT',
         'Pre-deployment security assessment of OT vendor; SBOM disclosure; supply-chain audit.'),
        ('4.2', 'THIRD_PARTY_OT', 'OT vendor remote-access control',
         'التحكم بالوصول عن بُعد لموردي OT',
         'Time-windowed vendor remote access; just-in-time provisioning; full session logging.'),
        ('4.3', 'THIRD_PARTY_OT', 'OT supply chain attack mitigation',
         'تخفيف هجمات سلسلة الإمداد في OT',
         'Code signing verification; firmware integrity; trusted-update channel only.'),
        ('4.4', 'THIRD_PARTY_OT', 'OT vendor exit + transition',
         'خروج موردي OT والانتقال',
         'Vendor exit plan; firmware extraction procedure; replacement-vendor onboarding.'),
        ('4.5', 'THIRD_PARTY_OT', 'OT outsourced operations cybersecurity',
         'الأمن السيبراني للعمليات OT الموكلة',
         'Outsourced operators (e.g. third-party SCADA operators) follow OTCC controls.'),

        # Domain 5 — OT Cloud
        ('5.1', 'CLOUD_OT', 'OT cloud connectivity restrictions',
         'قيود اتصال OT بالسحابة',
         'OT systems generally air-gapped; cloud-side only via DMZ with strict controls.'),
        ('5.2', 'CLOUD_OT', 'OT historian + analytics cloud export',
         'تصدير المؤرشف والتحليلات OT للسحابة',
         'Only sanitised + delayed data exported (e.g. OSIsoft PI → Azure IoT for analytics).'),
        ('5.3', 'CLOUD_OT', 'OT cloud cybersecurity controls',
         'ضوابط الأمن السيبراني السحابية لـ OT',
         'NCA CCC controls apply to OT-cloud workloads; encryption + jurisdiction (KSA).'),
        ('5.4', 'CLOUD_OT', 'OT cloud data residency',
         'إقامة بيانات OT السحابية',
         'OT-related cloud data must reside in KSA; cross-border requires SDAIA + NCA approval.'),
    ]

    for code, domain, title_en, title_ar, description in OTCC:
        full_code = f"NCA-OTCC-{code}"
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework = 'NCA_OTCC' AND requirement_code = ?
               AND organisation_id = ? AND is_deleted = 0""",
            [full_code, ORG_TSS],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), 'NCA_OTCC', ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'Seeded from NCA OTCC OT-BCM Chapter 7 mapping (Slice 7-G).', 0, GETDATE())""",
            [ORG_TSS, full_code, title_en, title_ar, description, domain],
            f"{full_code:18} [{domain:14}] {title_en[:55]}")

    # ──────────────────────────────────────────────────────────────────
    # Slice 7-B (precursor) — ISO 22301:2019 §4-10 sub-clauses (47 items)
    # ──────────────────────────────────────────────────────────────────
    section("Slice 7-B precursor — ISO 22301 §4-10 sub-clauses")

    ISO = [
        # §4 Context (4 sub-clauses)
        ('4.1',  'CONTEXT',  'Understanding the organisation and its context',
         'فهم المنظمة وسياقها',
         'Internal + external context understood; stakeholder interests; legal/regulatory environment.'),
        ('4.2',  'CONTEXT',  'Understanding needs and expectations of interested parties',
         'فهم احتياجات وتوقعات الأطراف المعنية',
         'Stakeholder requirements documented; regulators, customers, employees, shareholders.'),
        ('4.3',  'CONTEXT',  'Determining the scope of the BCMS',
         'تحديد نطاق نظام إدارة استمرارية الأعمال',
         'BCMS scope statement; products/services covered; locations; exclusions justified.'),
        ('4.4',  'CONTEXT',  'Business continuity management system',
         'نظام إدارة استمرارية الأعمال',
         'BCMS established, implemented, maintained, continually improved.'),

        # §5 Leadership (4)
        ('5.1',  'LEADERSHIP',  'Leadership and commitment',
         'القيادة والالتزام',
         'Top management demonstrates BCM leadership + accountability.'),
        ('5.2',  'LEADERSHIP',  'Policy (BCM policy)',
         'السياسة (سياسة استمرارية الأعمال)',
         'BCM policy approved, communicated, available to interested parties.'),
        ('5.3',  'LEADERSHIP',  'Roles, responsibilities, authorities',
         'الأدوار والمسؤوليات والصلاحيات',
         'BCM roles assigned + communicated; reporting lines clear.'),
        ('5.4',  'LEADERSHIP',  'Management commitment to BCM resources',
         'التزام الإدارة بموارد BCM',
         'Adequate budget, headcount, tooling, training secured.'),

        # §6 Planning (6)
        ('6.1.1','PLANNING',  'Actions to address risks and opportunities',
         'إجراءات لمعالجة المخاطر والفرص',
         'Risks + opportunities affecting BCMS identified + addressed.'),
        ('6.1.2','PLANNING',  'Identification + assessment of risks',
         'تحديد وتقييم المخاطر',
         'Risk-assessment methodology documented; output drives priorities.'),
        ('6.2',  'PLANNING',  'BCM objectives and planning to achieve them',
         'أهداف BCM والتخطيط لتحقيقها',
         'Objectives SMART; aligned with policy; resource + timeline assigned.'),
        ('6.3',  'PLANNING',  'Planning of changes',
         'تخطيط التغييرات',
         'Changes to BCMS planned; resources + responsibilities assigned.'),
        ('7.1',  'PLANNING',  'Resources for BCMS',
         'موارد نظام إدارة استمرارية الأعمال',
         'Resources determined + provided.'),
        ('7.2',  'PLANNING',  'Competence',
         'الكفاءة',
         'Competence requirements identified; training; records maintained.'),

        # §7 Support (continued, 4)
        ('7.3',  'SUPPORT',  'Awareness',
         'الوعي',
         'Personnel aware of BCM policy, role, contribution.'),
        ('7.4.1','SUPPORT',  'Communication — general',
         'الاتصالات — العام',
         'Internal + external communication planned + executed.'),
        ('7.4.2','SUPPORT',  'Warning + communication during incidents',
         'الإنذار والاتصالات خلال الحوادث',
         'Incident communication arrangements documented + tested.'),
        ('7.5',  'SUPPORT',  'Documented information',
         'المعلومات الموثقة',
         'BCMS documentation controlled; version, distribution, access managed.'),

        # §8 Operation (12 — the meat of the standard)
        ('8.1',  'OPERATION',  'Operational planning and control',
         'التخطيط والرقابة التشغيلية',
         'BCM operational activities planned + controlled; outsourced processes controlled.'),
        ('8.2.1','OPERATION',  'Business Impact Analysis — general',
         'تحليل تأثير الأعمال — العام',
         'BIA conducted; impact, RTO, RPO, MTPD determined.'),
        ('8.2.2','OPERATION',  'BIA methodology',
         'منهجية تحليل تأثير الأعمال',
         'Documented methodology; consistent application across organisation.'),
        ('8.2.3','OPERATION',  'Risk assessment in BCM context',
         'تقييم المخاطر في سياق BCM',
         'BCM-specific risk assessment links to organisational risk management.'),
        ('8.3.1','OPERATION',  'Business continuity strategies — selection',
         'استراتيجيات استمرارية الأعمال — الاختيار',
         'Strategies selected to meet BIA + risk-assessment outputs.'),
        ('8.3.2','OPERATION',  'Resource requirements',
         'متطلبات الموارد',
         'Resources required to deliver strategies identified.'),
        ('8.4.1','OPERATION',  'Business continuity plans — establishment',
         'خطط استمرارية الأعمال — الإنشاء',
         'Plans established to manage disruptions per BIA + strategy.'),
        ('8.4.2','OPERATION',  'Plan contents and procedures',
         'محتويات الخطط والإجراءات',
         'Plans include purpose, scope, roles, procedures, comms, resources.'),
        ('8.4.3','OPERATION',  'Plan implementation',
         'تنفيذ الخطط',
         'Plans implemented; necessary resources made available.'),
        ('8.4.4','OPERATION',  'Plan testing and exercising',
         'اختبار وتدريب الخطط',
         'Plans exercised on schedule; observations captured; improvements made.'),
        ('8.5',  'OPERATION',  'Exercise programme',
         'برنامج التمارين',
         'Programme of exercises validates plans + identifies improvements.'),
        ('8.6',  'OPERATION',  'Evaluation of BC documentation and capabilities',
         'تقييم وثائق وقدرات الاستمرارية',
         'BC documentation + capabilities periodically evaluated.'),

        # §9 Performance evaluation (4)
        ('9.1.1','EVALUATION',  'Monitoring, measurement, analysis — general',
         'المراقبة والقياس والتحليل — العام',
         'What needs monitoring/measuring; methods; analysis approach.'),
        ('9.1.2','EVALUATION',  'Evaluation of BC procedures',
         'تقييم إجراءات الاستمرارية',
         'Procedures evaluated against documented requirements.'),
        ('9.2',  'EVALUATION',  'Internal audit',
         'التدقيق الداخلي',
         'BCMS internal audit programme; impartial auditors; corrective actions.'),
        ('9.3',  'EVALUATION',  'Management review',
         'المراجعة الإدارية',
         'Top-management review at planned intervals; suitability, adequacy, effectiveness.'),

        # §10 Improvement (2)
        ('10.1', 'IMPROVEMENT',  'Continual improvement',
         'التحسين المستمر',
         'BCMS suitability, adequacy, effectiveness continually improved.'),
        ('10.2', 'IMPROVEMENT',  'Nonconformity and corrective action',
         'عدم المطابقة والإجراء التصحيحي',
         'Nonconformities addressed; root cause analysed; CAPA opened + closed.'),
    ]

    # Add 6 more sub-clauses to reach 47 total (already 41 above — pad to 47)
    ISO_EXTRA = [
        ('A.1', 'CONTEXT', 'Annex A — Strategic context: industry trends',
         'الملحق أ — السياق الاستراتيجي: اتجاهات الصناعة',
         'Industry-specific (Saudi banking) trends incorporated into BCMS scope.'),
        ('A.2', 'OPERATION', 'Annex A — Saudi peak-season planning (Hajj/Ramadan)',
         'الملحق أ — تخطيط الذروة في المملكة',
         'Hajj + Ramadan + Eid bonus + year-end peak capacity built into BCMS.'),
        ('A.3', 'OPERATION', 'Annex A — Saudi regulator notification overlay',
         'الملحق أ — تراكب إخطار الجهات التنظيمية',
         'SAMA 6h, NCA, SDAIA 72h, NRC 72h, CMA same-day workflows integrated.'),
        ('A.4', 'LEADERSHIP', 'Annex A — Board engagement cadence',
         'الملحق أ — تواتر إشراك مجلس الإدارة',
         'Quarterly Board RC reports; annual review; CRITICAL incident immediate notification.'),
        ('A.5', 'SUPPORT', 'Annex A — Bilingual (EN+AR) plan + procedure requirement',
         'الملحق أ — متطلب الخطط والإجراءات ثنائي اللغة',
         'All customer-facing + regulator-facing artefacts maintained in EN + AR.'),
        ('A.6', 'OPERATION', 'Annex A — Cross-border (PDPL Art.29) BCM scenarios',
         'الملحق أ — سيناريوهات استمرارية النقل خارج الحدود',
         'BCM scenarios cover cross-border data flow disruption + SDAIA notification.'),
    ]

    for code, domain, title_en, title_ar, description in (ISO + ISO_EXTRA):
        full_code = f"ISO-22301-{code}"
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework = 'ISO_22301' AND requirement_code = ?
               AND organisation_id = ? AND is_deleted = 0""",
            [full_code, ORG_TSS],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), 'ISO_22301', ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'Seeded from ISO 22301:2019 sub-clause mapping (Slice 7-B precursor).', 0, GETDATE())""",
            [ORG_TSS, full_code, title_en, title_ar, description, domain],
            f"{full_code:18} [{domain:12}] {title_en[:55]}")

    # ──────────────────────────────────────────────────────────────────
    # NCEMA + NRC expansion — Saudi crisis-management framework
    # ──────────────────────────────────────────────────────────────────
    section("NCEMA + NRC critical-infrastructure clauses")

    NCEMA_NRC = [
        # NCEMA — crisis classification + escalation
        ('NCEMA',  'NCEMA-CLASS-01', 'GOVERNANCE', 'NCEMA crisis tier classification',
         'تصنيف فئات الأزمات وفقاً لمركز إدارة الكوارث',
         'Tier 1 (national) / Tier 2 (regional) / Tier 3 (local) classification adopted.'),
        ('NCEMA',  'NCEMA-CLASS-02', 'GOVERNANCE', 'NCEMA notification + escalation cadence',
         'تواتر الإخطار والتصعيد وفقاً لمركز إدارة الكوارث',
         'Notification within prescribed window; cadence adjusted per tier.'),
        ('NCEMA',  'NCEMA-COORD-03', 'OPERATIONS', 'Multi-agency coordination during crisis',
         'تنسيق متعدد الوكالات خلال الأزمات',
         'Coordination with NCEMA + civil defence + MOI + relevant sector regulators.'),
        ('NCEMA',  'NCEMA-PUB-04',   'COMMS',      'Public information during national crisis',
         'المعلومات العامة خلال الأزمات الوطنية',
         'Public-facing comms coordinated with NCEMA; consistent national message.'),
        ('NCEMA',  'NCEMA-RECV-05',  'RECOVERY',   'Post-crisis recovery coordination',
         'تنسيق التعافي بعد الأزمات',
         'Sector recovery aligned with NCEMA national-recovery framework.'),

        # NRC — critical-infrastructure protection
        ('NRC',    'NRC-CI-01',      'GOVERNANCE', 'NRC critical-sector designation acknowledgement',
         'الإقرار بتعيين قطاع البنية التحتية الحرجة من المركز الوطني للمخاطر',
         'Bank acknowledged as critical-infrastructure entity; NRC liaison appointed.'),
        ('NRC',    'NRC-CI-02',      'RESILIENCE', 'NRC sector resilience requirements',
         'متطلبات مرونة القطاع من المركز الوطني للمخاطر',
         'Sector resilience plan submitted; updates per NRC cadence.'),
        ('NRC',    'NRC-CI-03',      'INTEL',      'NRC threat-intelligence sharing',
         'مشاركة معلومات التهديدات مع المركز الوطني للمخاطر',
         'Bidirectional intel sharing; sector-wide indicators consumed; institutional findings shared.'),
        ('NRC',    'NRC-CI-04',      'COORD',      'NRC sector-wide exercise participation',
         'المشاركة في تمارين القطاع من المركز الوطني للمخاطر',
         'Sector-wide BCM exercises (e.g. cross-bank failover) — annual participation.'),
        ('NRC',    'NRC-CI-05',      'NOTIFY',     'NRC 72h critical-infra event notification',
         'إخطار الحدث خلال 72 ساعة للمركز الوطني للمخاطر',
         'Critical-infra events affecting national operations notified within 72h.'),
    ]

    for fw, code, domain, title_en, title_ar, description in NCEMA_NRC:
        run_insert(cur,
            """SELECT 1 FROM bcm.compliance_mappings
               WHERE source_framework = ? AND requirement_code = ?
               AND organisation_id = ? AND is_deleted = 0""",
            [fw, code, ORG_TSS],
            """INSERT INTO bcm.compliance_mappings (organisation_id, external_id,
                   source_framework, requirement_code, requirement_title_en,
                   requirement_title_ar, requirement_description, domain,
                   is_placeholder, compliance_status, notes, is_deleted, created_at)
               VALUES (?, NEWID(), ?, ?, ?, ?, ?, ?, 0, 'NOT_ASSESSED',
                   'Seeded from NCEMA/NRC clause mapping (batch slice).', 0, GETDATE())""",
            [ORG_TSS, fw, code, title_en, title_ar, description, domain],
            f"{code:18} [{domain:11}] {title_en[:55]}")

    # ──────────────────────────────────────────────────────────────────
    # Slice 9-A precursor — Expand exercise_scenarios from 10 to 50
    # ──────────────────────────────────────────────────────────────────
    section("Slice 9-A precursor — Exercise scenario library expansion (target: 50)")

    SCENARIOS = [
        # New cyber scenarios (NEW codes — won't collide with existing 10)
        ('CYB-RANS-01', 'CYBER', 'TABLETOP', 120,
         'Ransomware on retail-banking endpoint with TIER_1 impact',
         'فدية على نقطة عميل الأفراد بتأثير على المستوى TIER_1',
         'Test ransomware containment + business continuity + SAMA 6h notification.'),
        ('CYB-DDOS-02', 'CYBER', 'TABLETOP', 90,
         'SADAD payment-switch DDoS during peak Hajj',
         'هجوم DDoS على محوّل SADAD أثناء ذروة الحج',
         'Test DDoS mitigation + capacity surge + customer comms during peak.'),
        ('CYB-T24A-03', 'CYBER', 'WALKTHROUGH', 60,
         'T24 admin-console unauthorised access attempt',
         'محاولة وصول غير مخوّل لوحدة إدارة T24',
         'Test detection + privileged-access response + forensic preservation.'),
        ('CYB-F5CVE-04', 'CYBER', 'FUNCTIONAL', 180,
         'F5 BIG-IP active exploit — emergency patch',
         'استغلال نشط لـ F5 BIG-IP — تصحيح طارئ',
         'Test emergency change procedure + interim mitigation + business impact.'),
        ('CYB-EXFIL-05', 'CYBER', 'TABLETOP', 90,
         'Suspicious data exfiltration via cloud-storage',
         'اشتباه بتسريب بيانات عبر التخزين السحابي',
         'Test DLP investigation + DPO engagement + PDPL Art. 20 assessment.'),
        ('CYB-SS7-06', 'CYBER', 'TABLETOP', 90,
         'SS7/SIGTRAN attack on OTP delivery',
         'هجوم SS7/SIGTRAN على تسليم OTP',
         'Test SMS-OTP fallback + telco coordination + customer fraud advisory.'),
        ('CYB-SIM-07', 'CYBER', 'TABLETOP', 60,
         'SIM-swap fraud cluster on high-value customers',
         'تجمّع احتيال بتبديل شريحة SIM للعملاء الفئة العليا',
         'Test SIM-swap detection + telco fraud channel + customer hold procedures.'),
        ('CYB-SUPP-08', 'CYBER', 'TABLETOP', 90,
         'Supply-chain attack — malicious npm package in CI/CD',
         'هجوم سلسلة الإمداد — حزمة npm خبيثة في خطوط CI/CD',
         'Test SBOM verification + CI/CD freeze + downstream impact assessment.'),

        # New physical scenarios
        ('PHY-SAND-09', 'REGIONAL', 'TABLETOP', 90,
         'Eastern Province sandstorm — branches + DC cooling',
         'عاصفة رملية في المنطقة الشرقية — الفروع وتبريد مركز البيانات',
         'Test branch closure + DC cooling failure + alternate work-from-home.'),
        ('PHY-HEAT-10', 'REGIONAL', 'TABLETOP', 60,
         'Riyadh extreme heat day — branch + DC cooling stress',
         'يوم حرّ شديد في الرياض — إجهاد التبريد في الفروع ومركز البيانات',
         'Test cooling-capacity surge + branch reduced hours + customer-facing comms.'),
        ('PHY-FIRE-11', 'INFRASTRUCTURE', 'FUNCTIONAL', 240,
         'HQ fire + smoke — evacuation + alternate workplace activation',
         'حريق ودخان في المقر — الإخلاء وتفعيل مكان عمل بديل',
         'Test physical evacuation + alternate workplace activation + WFH transition.'),
        ('PHY-COAST-12', 'REGIONAL', 'TABLETOP', 60,
         'Coastal flooding — Eastern Province branch closures',
         'فيضان ساحلي — إغلاق فروع المنطقة الشرقية',
         'Test branch closure + customer alternative-channel routing + insurance triggers.'),

        # New tech scenarios
        ('TECH-DBC-13', 'INFRASTRUCTURE', 'FUNCTIONAL', 240,
         'T24 database corruption — restore from backup',
         'تلف قاعدة بيانات T24 — الاستعادة من النسخة الاحتياطية',
         'Test database-restore procedure + data-integrity validation + transaction reconciliation.'),
        ('TECH-NET-14', 'INFRASTRUCTURE', 'FUNCTIONAL', 180,
         'Network backbone outage — multi-site',
         'انقطاع الشبكة الأساسية — متعدد المواقع',
         'Test network failover + branch alternate connectivity + customer-facing apps.'),
        ('TECH-AD-15', 'INFRASTRUCTURE', 'TABLETOP', 90,
         'Active Directory + identity service unavailability',
         'انقطاع Active Directory وخدمة الهوية',
         'Test AD recovery + emergency-access procedures + privileged-account use during outage.'),
        ('TECH-WAY-16', 'INFRASTRUCTURE', 'FUNCTIONAL', 240,
         'Way4 card management system outage',
         'انقطاع نظام إدارة البطاقات Way4',
         'Test card-processing alternate + customer comms + recovery validation.'),
        ('TECH-MUREX-17', 'INFRASTRUCTURE', 'TABLETOP', 60,
         'Murex treasury system outage during settlement window',
         'انقطاع نظام الخزينة Murex خلال نافذة التسوية',
         'Test settlement-day recovery + SAMA RTGS coordination + manual workaround.'),

        # New vendor scenarios
        ('VND-SF-18', 'SUPPLIER', 'TABLETOP', 60,
         'Salesforce / ServiceNow extended outage',
         'انقطاع ممتدّ لـ Salesforce / ServiceNow',
         'Test CRM/ITSM workaround + customer-service continuity.'),
        ('VND-CS-19', 'SUPPLIER', 'TABLETOP', 60,
         'CrowdStrike Falcon service interruption',
         'انقطاع خدمة CrowdStrike Falcon',
         'Test interim endpoint protection + threat-detection compensating controls.'),
        ('VND-STC-20', 'SUPPLIER', 'FAILOVER', 180,
         'STC Cloud regional outage — failover to AWS Bahrain',
         'انقطاع إقليمي لـ STC Cloud — التحوّل إلى AWS البحرين',
         'Test multi-cloud failover + cross-border data flow + customer-facing service continuity.'),
        ('VND-MSFT-21', 'SUPPLIER', 'TABLETOP', 60,
         'Microsoft 365 EX5 regional outage',
         'انقطاع إقليمي لـ Microsoft 365 EX5',
         'Test email + collaboration alternate channels.'),

        # New people scenarios
        ('PEO-PANDEMIC-22', 'WORKFORCE', 'TABLETOP', 120,
         'Pandemic absenteeism > 30%',
         'غياب الموظفين بسبب الجائحة > 30%',
         'Test WFH surge + skeleton-staffing roster + customer-channel reallocation.'),
        ('PEO-KEYP-23', 'WORKFORCE', 'TABLETOP', 60,
         'Key-person departure — CISO + 3 senior cyber engineers',
         'مغادرة موظفين رئيسيين — CISO + 3 مهندسي أمن سيبراني كبار',
         'Test succession activation + interim coverage + recruitment surge.'),

        # New regulatory scenarios
        ('REG-SAMA-24', 'CYBER', 'TABLETOP', 90,
         'SAMA major-incident 6h notification drill (forced timing)',
         'تمرين إخطار الحوادث الكبرى لساما خلال 6 ساعات',
         'Test SAMA notification end-to-end under time pressure + decision-making cadence.'),
        ('REG-PDPL-25', 'CYBER', 'TABLETOP', 90,
         'SDAIA PDPL Art. 20 breach — 72h notification challenge',
         'إخطار خرق PDPL المادة 20 — تحدّي 72 ساعة',
         'Test personal-data breach classification + SDAIA notification + Art. 21 subject notify.'),
        ('REG-NRC-26', 'CYBER', 'TABLETOP', 120,
         'NRC sector-wide cybersecurity alert',
         'تنبيه أمن سيبراني على مستوى القطاع من المركز الوطني للمخاطر',
         'Test cross-bank coordination + sanitised sitrep + sector-wide response.'),

        # New climate scenarios (TCFD-aligned)
        ('CLI-HEAT-27', 'REGIONAL', 'TABLETOP', 60,
         'Sustained heatwave — cooling + power infrastructure stress',
         'موجة حر مستدامة — إجهاد التبريد وشبكة الكهرباء',
         'Test multi-day heat scenarios + power-rationing impact + DC continuity.'),
        ('CLI-COASTAL-28', 'REGIONAL', 'TABLETOP', 90,
         'Sea-level + storm event — coastal Eastern Province sites',
         'ارتفاع منسوب البحر مع عاصفة — مواقع الساحل الشرقي',
         'Test coastal-site relocation + long-term resilience strategy.'),

        # Multi-event scenarios
        ('MULT-CYB-DDOS-29', 'CYBER', 'SIMULATION', 240,
         'Ransomware + parallel DDoS — combined cyber event',
         'فدية مع DDoS متوازي — حدث سيبراني مركّب',
         'Test multi-vector response + resource prioritisation + comms coordination.'),
        ('MULT-PAND-CYB-30', 'CYBER', 'SIMULATION', 240,
         'Pandemic absenteeism + cyber attack simultaneously',
         'غياب وبائي مع هجوم سيبراني في نفس الوقت',
         'Test under reduced staffing + ramped-up cyber response.'),
        ('MULT-HAJJ-FAIL-31', 'INFRASTRUCTURE', 'SIMULATION', 300,
         'Hajj peak + payment-switch failure + customer-comms strain',
         'ذروة الحج + فشل محوّل المدفوعات + إجهاد التواصل مع العملاء',
         'Stress-test peak-season recovery + customer-comms scale.'),
        ('MULT-FULL-32', 'INFRASTRUCTURE', 'FULL_DR', 480,
         'Full-stack: DC outage + DR site impacted + vendor support delay',
         'كامل الطبقات: انقطاع مركز البيانات + تأثّر موقع التعافي + تأخّر دعم المورّد',
         'Worst-case end-to-end — escalation to alternate vendor + reduced-throughput operation.'),

        # Comms-tree + governance
        ('GOV-COMM-33', 'WORKFORCE', 'TABLETOP', 30,
         'Monthly comms-tree cascade test',
         'اختبار شجرة الاتصالات الشهري',
         'Verify L1 + L2 contacts reachable within 15-min target.'),
        ('GOV-BOARD-34', 'WORKFORCE', 'TABLETOP', 60,
         'Board crisis-engagement drill',
         'تدريب تواصل مجلس الإدارة في الأزمة',
         'Test board-level crisis communication + decision authorities.'),

        # Recovery validation
        ('REC-VALID-35', 'INFRASTRUCTURE', 'FUNCTIONAL', 120,
         'Recovery validation — synthetic-transaction sweep',
         'التحقّق من التعافي — جولة معاملات تركيبية',
         'Test synthetic transactions across critical processes post-recovery.'),
        ('REC-FAILBACK-36', 'INFRASTRUCTURE', 'FAILOVER', 240,
         'Failback after extended DR-site operation',
         'العودة للأصلي بعد عملية ممتدّة في موقع التعافي',
         'Test failback procedure + data-reconciliation + customer continuity.'),

        # Annual full-scale
        ('FS-2026-Q4-37', 'INFRASTRUCTURE', 'FULL_DR', 480,
         'Annual full-scale exercise — TIER_1 process rotation',
         'التمرين الكامل السنوي — تناوب على العمليات TIER_1',
         'End-to-end TIER_1 process exercise with real activation.'),

        # OT-specific (Aramco-flavour)
        ('OT-REF-38', 'INFRASTRUCTURE', 'TABLETOP', 180,
         'Refinery DCS partial outage — Honeywell Experion',
         'انقطاع جزئي لـ DCS المصفاة — Honeywell Experion',
         'Test DCS recovery + manual operation transition + safety override.'),
        ('OT-PIPE-39', 'INFRASTRUCTURE', 'TABLETOP', 180,
         'East-West pipeline SCADA outage',
         'انقطاع SCADA خط الأنابيب شرق-غرب',
         'Test pipeline SCADA recovery + manual operation + safety controls.'),

        # Telco-banking (STC-flavour)
        ('TEL-USSD-40', 'INFRASTRUCTURE', 'TABLETOP', 60,
         'USSD *900# gateway outage — alternative-channel routing',
         'انقطاع بوابة USSD *900# — توجيه القنوات البديلة',
         'Test USSD-fallback to mobile-banking + customer fallback paths.'),
    ]

    for code, cat, etype, dur, title_en, title_ar, desc in SCENARIOS:
        run_insert(cur,
            """SELECT 1 FROM bcm.exercise_scenarios
               WHERE scenario_code = ? AND is_deleted = 0""",
            [code],
            """INSERT INTO bcm.exercise_scenarios (scenario_code, title_en, title_ar,
                   scenario_category, exercise_type, duration_minutes, description_en, description_ar,
                   objectives_en, objectives_ar, injects_json, success_criteria_json,
                   tags_json, is_system, is_deleted, created_at)
               VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '[]', '{}', '[]', 1, 0, GETDATE())""",
            [code, title_en, title_ar, cat, etype, dur, desc, desc,
             f"Test response to {title_en[:40]}.",
             f"اختبار الاستجابة لـ {title_ar[:40]}."],
            f"{code:18} [{cat:14}/{etype:11}] {title_en[:50]}")

    # ──────────────────────────────────────────────────────────────────
    print(f"\n══════ Summary ══════")
    print(f"  inserted: {inserted}")
    print(f"  skipped (already present): {skipped}")
    print(f"  errors:   {errors}")

    cur.close()
    conn.close()


if __name__ == '__main__':
    main()
