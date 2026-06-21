# -*- coding: utf-8 -*-
"""Create and seed Cyber Security module schema for TSS GRC Platform."""
import sys, pyodbc
sys.stdout.reconfigure(encoding='utf-8')

CONN_STR = (
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)

conn = pyodbc.connect(CONN_STR)
conn.autocommit = True
cur = conn.cursor()

TOTAL_STEPS = 14
step_num = [0]

def step(label):
    step_num[0] += 1
    print(f"\nStep {step_num[0]}/{TOTAL_STEPS}: {label}")

def run(sql, params=None):
    try:
        if params:
            cur.execute(sql, params)
        else:
            cur.execute(sql)
    except Exception as e:
        print(f"  ERR: {str(e)[:200]}")
        raise

def run_quiet(sql):
    """Run DDL, ignore already-exists errors."""
    try:
        cur.execute(sql)
    except Exception as e:
        s = str(e).lower()
        if any(w in s for w in ['already', 'duplicate', 'exist']):
            pass
        else:
            print(f"  ERR: {str(e)[:200]}")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 1 – Create schema
# ─────────────────────────────────────────────────────────────────────────────
step("Create schema [cyber]")
run("IF NOT EXISTS (SELECT 1 FROM sys.schemas WHERE name='cyber') "
    "EXEC('CREATE SCHEMA cyber')")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 2 – Drop tables in safe dependency order
# ─────────────────────────────────────────────────────────────────────────────
step("Drop existing cyber tables (safe)")

drop_order = [
    'cyber.cyber_action_plans',
    'cyber.cyber_evidence',
    'cyber.vuln_exceptions',
    'cyber.cyber_incidents',
    'cyber.vulnerabilities',
    'cyber.control_assessments',
    'cyber.cyber_sub_controls',
    'cyber.cyber_controls',
    'cyber.cyber_assets',
    'cyber.cyber_frameworks',
]
for tbl in drop_order:
    run_quiet(f"IF OBJECT_ID('{tbl}','U') IS NOT NULL DROP TABLE {tbl}")
    print(f"  dropped (if existed): {tbl}")

print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 3 – Create cyber.cyber_frameworks
# ─────────────────────────────────────────────────────────────────────────────
step("Create cyber.cyber_frameworks")
run("""
CREATE TABLE cyber.cyber_frameworks (
    id               INT IDENTITY(1,1) PRIMARY KEY,
    external_id      NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id  BIGINT        NOT NULL DEFAULT 1,
    name             NVARCHAR(100) NOT NULL,
    version          NVARCHAR(20),
    description_en   NVARCHAR(500),
    is_deleted       BIT           NOT NULL DEFAULT 0,
    created_at       DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 4 – Create cyber.cyber_assets
# ─────────────────────────────────────────────────────────────────────────────
step("Create cyber.cyber_assets")
run("""
CREATE TABLE cyber.cyber_assets (
    id                          INT IDENTITY(1,1) PRIMARY KEY,
    external_id                 NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id             BIGINT        NOT NULL DEFAULT 1,
    asset_name                  NVARCHAR(300) NOT NULL,
    asset_type                  NVARCHAR(30),
    business_owner              NVARCHAR(200),
    technical_owner             NVARCHAR(200),
    classification              NVARCHAR(30),
    criticality                 NVARCHAR(20),
    ip_address                  NVARCHAR(50),
    hostname                    NVARCHAR(200),
    operating_system            NVARCHAR(100),
    linked_vulnerabilities_count INT          NOT NULL DEFAULT 0,
    linked_incidents_count      INT           NOT NULL DEFAULT 0,
    linked_controls_count       INT           NOT NULL DEFAULT 0,
    status                      NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_deleted                  BIT           NOT NULL DEFAULT 0,
    created_at                  DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 5 – Create cyber.cyber_controls
# ─────────────────────────────────────────────────────────────────────────────
step("Create cyber.cyber_controls")
run("""
CREATE TABLE cyber.cyber_controls (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id       BIGINT        NOT NULL DEFAULT 1,
    framework_id          INT,
    framework_name        NVARCHAR(100),
    domain                NVARCHAR(200),
    control_number        NVARCHAR(50),
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500),
    procedure_en          NVARCHAR(MAX),
    control_type          NVARCHAR(30),
    control_owner         NVARCHAR(200),
    review_frequency      NVARCHAR(30),
    maturity_level        NVARCHAR(30),
    implementation_status NVARCHAR(30),
    status                NVARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',
    is_deleted            BIT           NOT NULL DEFAULT 0,
    created_at            DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 6 – Create cyber.cyber_sub_controls
# ─────────────────────────────────────────────────────────────────────────────
step("Create cyber.cyber_sub_controls")
run("""
CREATE TABLE cyber.cyber_sub_controls (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id       BIGINT        NOT NULL DEFAULT 1,
    control_id            INT           NOT NULL,
    sub_control_number    NVARCHAR(50),
    title_en              NVARCHAR(500) NOT NULL,
    title_ar              NVARCHAR(500),
    description_en        NVARCHAR(MAX),
    control_owner         NVARCHAR(200),
    implementation_status NVARCHAR(30),
    maturity_level        NVARCHAR(30),
    evidence_required     BIT           NOT NULL DEFAULT 0,
    is_deleted            BIT           NOT NULL DEFAULT 0,
    created_at            DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 7 – Create remaining 6 tables
# ─────────────────────────────────────────────────────────────────────────────
step("Create control_assessments, vulnerabilities, vuln_exceptions, cyber_incidents, cyber_evidence, cyber_action_plans")

run("""
CREATE TABLE cyber.control_assessments (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id     BIGINT        NOT NULL DEFAULT 1,
    control_id          INT           NOT NULL,
    assessment_date     DATE,
    assessor_name       NVARCHAR(200),
    reviewer_name       NVARCHAR(200),
    result              NVARCHAR(30),
    maturity_score      INT,
    gap_identified      BIT           NOT NULL DEFAULT 0,
    gap_description     NVARCHAR(MAX),
    risk_level          NVARCHAR(20),
    assessor_comments   NVARCHAR(MAX),
    reviewer_comments   NVARCHAR(MAX),
    evidence_count      INT           NOT NULL DEFAULT 0,
    status              NVARCHAR(30)  NOT NULL DEFAULT 'DRAFT',
    capa_id             INT,
    is_deleted          BIT           NOT NULL DEFAULT 0,
    created_at          DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")

run("""
CREATE TABLE cyber.vulnerabilities (
    id                   INT IDENTITY(1,1) PRIMARY KEY,
    external_id          NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id      BIGINT        NOT NULL DEFAULT 1,
    asset_id             INT,
    asset_name           NVARCHAR(300),
    title_en             NVARCHAR(500) NOT NULL,
    title_ar             NVARCHAR(500),
    description_en       NVARCHAR(MAX),
    severity             NVARCHAR(20),
    cvss_score           DECIMAL(4,1),
    vulnerability_type   NVARCHAR(100),
    cve_id               NVARCHAR(50),
    owner_name           NVARCHAR(200),
    sla_days             INT,
    due_date             DATE,
    workflow_status      NVARCHAR(30)  NOT NULL DEFAULT 'REGISTERED',
    remediation_notes    NVARCHAR(MAX),
    capa_id              INT,
    overdue              BIT           NOT NULL DEFAULT 0,
    reopened_count       INT           NOT NULL DEFAULT 0,
    is_deleted           BIT           NOT NULL DEFAULT 0,
    created_at           DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")

run("""
CREATE TABLE cyber.vuln_exceptions (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id       BIGINT        NOT NULL DEFAULT 1,
    vulnerability_id      INT,
    title_en              NVARCHAR(500) NOT NULL,
    exception_reason      NVARCHAR(MAX),
    workflow_status       NVARCHAR(30)  NOT NULL DEFAULT 'REQUESTED',
    owner_name            NVARCHAR(200),
    approver_name         NVARCHAR(200),
    compensating_controls NVARCHAR(MAX),
    impact_assessment     NVARCHAR(MAX),
    expiry_date           DATE,
    approved_at           DATE,
    is_deleted            BIT           NOT NULL DEFAULT 0,
    created_at            DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")

run("""
CREATE TABLE cyber.cyber_incidents (
    id                          INT IDENTITY(1,1) PRIMARY KEY,
    external_id                 NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id             BIGINT        NOT NULL DEFAULT 1,
    incident_number             NVARCHAR(50),
    title_en                    NVARCHAR(500) NOT NULL,
    title_ar                    NVARCHAR(500),
    incident_type               NVARCHAR(50),
    affected_asset_id           INT,
    affected_asset_name         NVARCHAR(300),
    impact                      NVARCHAR(30),
    severity                    NVARCHAR(20),
    workflow_status             NVARCHAR(30)  NOT NULL DEFAULT 'DETECTION',
    detected_at                 DATETIME2,
    reported_at                 DATETIME2,
    resolved_at                 DATETIME2,
    incident_lead               NVARCHAR(200),
    notification_sent           BIT           NOT NULL DEFAULT 0,
    bcm_handoff                 BIT           NOT NULL DEFAULT 0,
    linked_vulnerabilities_count INT          NOT NULL DEFAULT 0,
    linked_evidence_count       INT           NOT NULL DEFAULT 0,
    lessons_learned             NVARCHAR(MAX),
    is_deleted                  BIT           NOT NULL DEFAULT 0,
    created_at                  DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")

run("""
CREATE TABLE cyber.cyber_evidence (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    external_id           NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id       BIGINT        NOT NULL DEFAULT 1,
    title_en              NVARCHAR(500) NOT NULL,
    evidence_type         NVARCHAR(30),
    control_id            INT,
    assessment_id         INT,
    framework_requirement NVARCHAR(200),
    owner_name            NVARCHAR(200),
    version               NVARCHAR(20),
    review_date           DATE,
    expiry_date           DATE,
    status                NVARCHAR(30)  NOT NULL DEFAULT 'CURRENT',
    file_name             NVARCHAR(300),
    is_deleted            BIT           NOT NULL DEFAULT 0,
    created_at            DATETIME2     NOT NULL DEFAULT GETDATE()
)
""")

run("""
CREATE TABLE cyber.cyber_action_plans (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    external_id         NVARCHAR(36)  NOT NULL DEFAULT NEWID(),
    organisation_id     BIGINT        NOT NULL DEFAULT 1,
    title_en            NVARCHAR(500) NOT NULL,
    title_ar            NVARCHAR(500),
    description_en      NVARCHAR(MAX),
    source_type         NVARCHAR(30),
    source_id           INT,
    source_ref          NVARCHAR(200),
    priority            NVARCHAR(20),
    owner_name          NVARCHAR(200),
    due_date            DATE,
    workflow_status     NVARCHAR(20)  NOT NULL DEFAULT 'OPEN',
    progress_pct        INT           NOT NULL DEFAULT 0,
    completion_evidence NVARCHAR(MAX),
    validation_notes    NVARCHAR(MAX),
    linked_control_id   INT,
    is_deleted          BIT           NOT NULL DEFAULT 0,
    created_at          DATETIME2     NOT NULL DEFAULT GETDATE(),
    completed_at        DATE
)
""")

print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 8 – Seed cyber.cyber_frameworks (3 rows)
# ─────────────────────────────────────────────────────────────────────────────
step("Seed cyber.cyber_frameworks")

frameworks = [
    ('NCA ECC', '1.0',
     'National Cybersecurity Authority – Essential Cybersecurity Controls framework '
     'mandated for all entities operating in Saudi Arabia.'),
    ('ISO 27001', '2022',
     'International standard for information security management systems (ISMS). '
     'Provides requirements for establishing, implementing, maintaining and continually '
     'improving an ISMS.'),
    ('PCI DSS', 'v4.0',
     'Payment Card Industry Data Security Standard – requirements for organisations '
     'that handle cardholder data for major card brands.'),
]
for name, version, desc in frameworks:
    cur.execute(
        "INSERT INTO cyber.cyber_frameworks (name, version, description_en) VALUES (?,?,?)",
        (name, version, desc)
    )
print(f"  Inserted {len(frameworks)} framework(s)")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 9 – Seed cyber.cyber_assets (5 rows)
# ─────────────────────────────────────────────────────────────────────────────
step("Seed cyber.cyber_assets")

assets = [
    # asset_name, asset_type, business_owner, technical_owner, classification, criticality,
    # ip_address, hostname, operating_system
    ('Core Banking System', 'SERVER', 'Ahmed Al-Rashidi', 'Khalid Al-Otaibi',
     'CONFIDENTIAL', 'CRITICAL', '10.10.1.5', 'CBS-PROD-01', 'RHEL 8.6'),
    ('Payment Gateway', 'APPLICATION', 'Fatima Al-Zahrani', 'Mohammed Al-Ghamdi',
     'RESTRICTED', 'CRITICAL', '10.10.1.20', 'PGW-PROD-01', 'Windows Server 2022'),
    ('Employee Workstations', 'WORKSTATION', 'HR Department', 'IT Operations Team',
     'INTERNAL', 'HIGH', None, 'WS-POOL-AD', 'Windows 11 Enterprise'),
    ('Network Firewall', 'NETWORK_DEVICE', 'IT Security Team', 'Network Engineering',
     'RESTRICTED', 'CRITICAL', '10.10.0.1', 'FW-CORE-01', 'Palo Alto PAN-OS 11'),
    ('Customer Database', 'DATABASE', 'Data Governance Office', 'DBA Team',
     'CONFIDENTIAL', 'CRITICAL', '10.10.1.50', 'DB-CUST-01', 'Oracle 19c on RHEL 8'),
]
for (asset_name, asset_type, biz_owner, tech_owner, classification,
     criticality, ip, hostname, os_) in assets:
    cur.execute(
        """INSERT INTO cyber.cyber_assets
           (asset_name, asset_type, business_owner, technical_owner, classification,
            criticality, ip_address, hostname, operating_system, status)
           VALUES (?,?,?,?,?,?,?,?,?,'ACTIVE')""",
        (asset_name, asset_type, biz_owner, tech_owner, classification,
         criticality, ip, hostname, os_)
    )
print(f"  Inserted {len(assets)} asset(s)")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 10 – Seed cyber.cyber_controls (6 rows)
# ─────────────────────────────────────────────────────────────────────────────
step("Seed cyber.cyber_controls")

controls = [
    # framework_id, framework_name, domain, control_number, title_en, title_ar,
    # procedure_en, control_type, control_owner, review_frequency, maturity_level,
    # implementation_status
    (1, 'NCA ECC', 'Access Control', 'ECC-1-1',
     'Identity and Access Management',
     'إدارة الهوية والوصول',
     'All user accounts must be managed through a centralised IAM solution. '
     'Privileged accounts must use multi-factor authentication. Access rights '
     'must be reviewed quarterly and immediately upon role change or termination.',
     'PREVENTIVE', 'IT Security Manager', 'QUARTERLY', 'DEFINED', 'IMPLEMENTED'),

    (1, 'NCA ECC', 'Network Security', 'ECC-2-1',
     'Network Segmentation and Perimeter Controls',
     'تقسيم الشبكة وضوابط المحيط',
     'Critical systems must be segregated in dedicated network segments with '
     'firewall rules enforcing least-privilege communication. DMZ zones must '
     'be established for all internet-facing services.',
     'PREVENTIVE', 'Network Security Lead', 'SEMI_ANNUAL', 'MANAGED', 'IMPLEMENTED'),

    (1, 'NCA ECC', 'Data Protection', 'ECC-3-1',
     'Data Classification and Encryption',
     'تصنيف البيانات والتشفير',
     'All data must be classified per the organisation data classification '
     'policy. Confidential and Restricted data must be encrypted at rest '
     'using AES-256 and in transit using TLS 1.2 or higher.',
     'PREVENTIVE', 'Data Protection Officer', 'ANNUAL', 'DEFINED', 'PARTIALLY_IMPLEMENTED'),

    (1, 'NCA ECC', 'Incident Response', 'ECC-4-1',
     'Cyber Incident Detection and Response',
     'اكتشاف الحوادث السيبرانية والاستجابة لها',
     'The organisation must maintain a 24/7 SOC capability with defined '
     'escalation paths. All security events must be correlated in a SIEM. '
     'Incident response playbooks must be tested at least annually.',
     'DETECTIVE', 'SOC Manager', 'QUARTERLY', 'OPTIMISING', 'IMPLEMENTED'),

    (1, 'NCA ECC', 'Asset Management', 'ECC-5-1',
     'IT Asset Inventory and Lifecycle Management',
     'جرد الأصول التقنية وإدارة دورة حياتها',
     'A complete and accurate IT asset inventory must be maintained and '
     'reviewed every 6 months. End-of-life assets must be tracked and '
     'decommissioned following the secure disposal procedure.',
     'PREVENTIVE', 'IT Asset Manager', 'SEMI_ANNUAL', 'DEFINED', 'PARTIALLY_IMPLEMENTED'),

    (2, 'ISO 27001', 'Cryptography', 'A.10.1',
     'Cryptographic Controls Policy',
     'سياسة الضوابط التشفيرية',
     'A policy on the use of cryptographic controls must be developed and '
     'implemented. Key management procedures must cover generation, '
     'distribution, storage, retirement and destruction of cryptographic keys.',
     'PREVENTIVE', 'IT Security Manager', 'ANNUAL', 'REPEATABLE', 'IMPLEMENTED'),
]

for (fw_id, fw_name, domain, ctrl_num, title_en, title_ar, procedure_en,
     ctrl_type, ctrl_owner, review_freq, maturity, impl_status) in controls:
    cur.execute(
        """INSERT INTO cyber.cyber_controls
           (framework_id, framework_name, domain, control_number, title_en, title_ar,
            procedure_en, control_type, control_owner, review_frequency,
            maturity_level, implementation_status, status)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'ACTIVE')""",
        (fw_id, fw_name, domain, ctrl_num, title_en, title_ar, procedure_en,
         ctrl_type, ctrl_owner, review_freq, maturity, impl_status)
    )
print(f"  Inserted {len(controls)} control(s)")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 11 – Seed cyber.cyber_sub_controls (3 for control_id=1)
# ─────────────────────────────────────────────────────────────────────────────
step("Seed cyber.cyber_sub_controls")

sub_controls = [
    (1, 'ECC-1-1.1',
     'User Account Provisioning and De-provisioning',
     'توفير حسابات المستخدمين وإلغاؤها',
     'Formal request, approval and fulfilment process must exist for all '
     'account provisioning. Accounts must be disabled within 24 hours of '
     'separation and deleted within 30 days.',
     'IAM Administrator', 'IMPLEMENTED', 'DEFINED', 1),

    (1, 'ECC-1-1.2',
     'Privileged Access Management',
     'إدارة الوصول المميز',
     'All privileged accounts must be managed through a Privileged Access '
     'Management (PAM) solution. Sessions must be recorded and reviewed. '
     'Shared admin credentials are prohibited.',
     'IT Security Manager', 'IMPLEMENTED', 'MANAGED', 1),

    (1, 'ECC-1-1.3',
     'Periodic Access Review',
     'مراجعة الوصول الدورية',
     'Access rights for all users must be reviewed quarterly by respective '
     'business owners. Findings must be remediated within 15 business days '
     'of review completion. Results documented and retained for 3 years.',
     'Compliance Officer', 'PARTIALLY_IMPLEMENTED', 'DEFINED', 1),
]

for (ctrl_id, sub_num, title_en, title_ar, desc_en, ctrl_owner,
     impl_status, maturity, ev_req) in sub_controls:
    cur.execute(
        """INSERT INTO cyber.cyber_sub_controls
           (control_id, sub_control_number, title_en, title_ar, description_en,
            control_owner, implementation_status, maturity_level, evidence_required)
           VALUES (?,?,?,?,?,?,?,?,?)""",
        (ctrl_id, sub_num, title_en, title_ar, desc_en, ctrl_owner,
         impl_status, maturity, ev_req)
    )
print(f"  Inserted {len(sub_controls)} sub-control(s)")
print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 12 – Seed remaining tables (assessments, vulns, exceptions, incidents,
#            evidence, action_plans)
# ─────────────────────────────────────────────────────────────────────────────
step("Seed control_assessments, vulnerabilities, vuln_exceptions, "
     "cyber_incidents, cyber_evidence, cyber_action_plans")

# ── control_assessments (4 rows) ─────────────────────────────────────────────
assessments = [
    # control_id, assessment_date, assessor_name, reviewer_name, result,
    # maturity_score, gap_identified, gap_description, risk_level,
    # assessor_comments, reviewer_comments, evidence_count, status
    (1, '2025-01-15', 'Noura Al-Qahtani', 'Saad Al-Dosari',
     'COMPLIANT', 4, 0, None, 'LOW',
     'IAM controls are fully implemented with PAM solution covering all privileged accounts.',
     'Reviewed and confirmed. PAM logs verified for last 90 days. No exceptions noted.',
     5, 'COMPLETED'),

    (2, '2025-01-20', 'Abdullah Al-Shehri', 'Noura Al-Qahtani',
     'PARTIALLY_COMPLIANT', 3, 1,
     'DMZ segmentation is in place however two legacy servers in the core '
     'network segment still communicate directly with internet endpoints without '
     'proxy. Firewall rules require tightening for VLAN-to-VLAN traffic.',
     'MEDIUM',
     'Partial compliance observed. Legacy system migration plan needed.',
     'Gap accepted as interim risk. Remediation plan required within 60 days.',
     3, 'COMPLETED'),

    (3, '2025-02-05', 'Reem Al-Harbi', 'Khalid Al-Mutairi',
     'NON_COMPLIANT', 2, 1,
     'Encryption at rest is not enabled on the Customer Database. TLS 1.0 '
     'is still permitted on two application servers. Data classification '
     'tagging has only been applied to 40% of identified data stores.',
     'HIGH',
     'Significant gaps identified. Immediate remediation required.',
     'Critical finding escalated to CISO. Corrective action plan mandated.',
     2, 'COMPLETED'),

    (4, '2025-02-18', 'Faisal Al-Amri', 'Abdullah Al-Shehri',
     'COMPLIANT', 5, 0, None, 'LOW',
     'SOC operates 24/7 with SIEM fully deployed. Incident playbooks tested '
     'in Q4 2024 tabletop exercise. Mean time to detect (MTTD) within SLA.',
     'Confirmed compliant. SOC metrics reviewed and within acceptable thresholds.',
     6, 'COMPLETED'),
]
for (ctrl_id, assess_date, assessor, reviewer, result, maturity_score,
     gap_identified, gap_desc, risk_level, assessor_comments,
     reviewer_comments, ev_count, status) in assessments:
    cur.execute(
        """INSERT INTO cyber.control_assessments
           (control_id, assessment_date, assessor_name, reviewer_name, result,
            maturity_score, gap_identified, gap_description, risk_level,
            assessor_comments, reviewer_comments, evidence_count, status)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (ctrl_id, assess_date, assessor, reviewer, result, maturity_score,
         gap_identified, gap_desc, risk_level, assessor_comments,
         reviewer_comments, ev_count, status)
    )
print(f"  Inserted {len(assessments)} assessment(s)")

# ── vulnerabilities (5 rows) ─────────────────────────────────────────────────
vulns = [
    # asset_id, asset_name, title_en, title_ar, description_en,
    # severity, cvss_score, vuln_type, cve_id, owner_name, sla_days,
    # due_date, workflow_status, remediation_notes, overdue
    (1, 'Core Banking System',
     'OpenSSL Critical Remote Code Execution Vulnerability',
     'ثغرة تنفيذ التعليمات البرمجية عن بُعد في OpenSSL',
     'A critical buffer overflow vulnerability in OpenSSL versions prior to '
     '3.0.7 allows a remote attacker to execute arbitrary code via specially '
     'crafted certificate chains.',
     'CRITICAL', 9.8, 'Software Vulnerability', 'CVE-2022-3602',
     'Khalid Al-Otaibi', 7, '2025-01-10', 'IN_REMEDIATION',
     'Patch applied in UAT. Production change window scheduled for 2025-01-08.',
     0),

    (4, 'Network Firewall',
     'Firewall Management Interface Exposed on Public Network',
     'واجهة إدارة جدار الحماية مكشوفة على الشبكة العامة',
     'The web management interface of the perimeter firewall is accessible '
     'from external IP ranges. This was discovered during a routine external '
     'penetration test and poses a severe risk of unauthorised administrative access.',
     'HIGH', 8.1, 'Misconfiguration', None,
     'Network Engineering', 15, '2025-01-22', 'REMEDIATED',
     'ACL updated to restrict management access to jump-host IPs only. '
     'Verified via external scan on 2025-01-21.',
     0),

    (5, 'Customer Database',
     'Database Transparent Data Encryption (TDE) Not Enabled',
     'عدم تفعيل تشفير قاعدة البيانات الشفاف (TDE)',
     'Oracle TDE is not enabled for the Customer Database tablespaces '
     'containing PII and financial data. This violates ECC-3-1 and PDPL '
     'Article 9 encryption requirements.',
     'HIGH', 7.5, 'Misconfiguration', None,
     'DBA Team', 30, '2025-02-15', 'IN_REMEDIATION',
     'TDE enablement plan approved. DBA team executing encryption on '
     'non-production first for testing.',
     0),

    (2, 'Payment Gateway',
     'TLS 1.0/1.1 Deprecated Protocols Enabled',
     'بروتوكولات TLS 1.0/1.1 المُهملة ممكّنة',
     'The payment gateway application server supports TLS 1.0 and 1.1 '
     'which are deprecated and vulnerable to BEAST and POODLE attacks. '
     'PCI DSS v4.0 requires TLS 1.2 as minimum.',
     'MEDIUM', 5.9, 'Configuration Weakness', None,
     'Mohammed Al-Ghamdi', 30, '2025-03-01', 'REGISTERED',
     None, 0),

    (3, 'Employee Workstations',
     'Missing Critical Security Patches on 34% of Endpoints',
     'غياب التحديثات الأمنية الحرجة على 34% من أجهزة العمل',
     'Vulnerability scan results show 34% of employee workstations are '
     'missing critical OS and application security patches older than '
     '90 days. This significantly increases risk of malware exploitation.',
     'MEDIUM', 6.5, 'Patch Management', None,
     'IT Operations Team', 45, '2025-01-30', 'OVERDUE',
     None, 1),
]
for (asset_id, asset_name, title_en, title_ar, desc_en,
     severity, cvss_score, vuln_type, cve_id, owner_name, sla_days,
     due_date, wf_status, rem_notes, overdue) in vulns:
    cur.execute(
        """INSERT INTO cyber.vulnerabilities
           (asset_id, asset_name, title_en, title_ar, description_en,
            severity, cvss_score, vulnerability_type, cve_id, owner_name,
            sla_days, due_date, workflow_status, remediation_notes, overdue)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (asset_id, asset_name, title_en, title_ar, desc_en,
         severity, cvss_score, vuln_type, cve_id, owner_name,
         sla_days, due_date, wf_status, rem_notes, overdue)
    )
print(f"  Inserted {len(vulns)} vulnerability(ies)")

# ── vuln_exceptions (2 rows) ─────────────────────────────────────────────────
exceptions = [
    # vulnerability_id, title_en, exception_reason, workflow_status,
    # owner_name, approver_name, compensating_controls, impact_assessment,
    # expiry_date, approved_at
    (4, 'TLS Deprecation Exception – Payment Gateway Legacy Integration',
     'The payment processing integration with a legacy acquiring bank '
     'mandates TLS 1.0 support until the bank completes their infrastructure '
     'upgrade (ETA Q3 2025). Immediate disablement would halt card processing.',
     'APPROVED',
     'Mohammed Al-Ghamdi', 'Saad Al-Dosari',
     'Network-level monitoring for anomalous TLS 1.0 sessions. '
     'IP whitelisting restricts TLS 1.0 to acquiring bank IP range only.',
     'Risk is considered MEDIUM during exception period due to compensating '
     'controls limiting exposure to a known and monitored IP range.',
     '2025-09-30', '2025-01-18'),

    (5, 'Endpoint Patching Exception – Legacy Manufacturing Control Systems',
     'Three specialised workstations running legacy SCADA interface software '
     'cannot be patched as the vendor has confirmed patches break the SCADA '
     'integration. Vendor support contract renewal under negotiation.',
     'REQUESTED',
     'IT Operations Team', 'Noura Al-Qahtani',
     'Workstations are on an isolated VLAN with no internet access. '
     'USB ports disabled via GPO. Application whitelisting enforced.',
     'Residual risk assessed as MEDIUM given network isolation.',
     '2025-06-30', None),
]
for (vuln_id, title_en, exc_reason, wf_status, owner_name, approver_name,
     comp_controls, impact_assess, expiry_date, approved_at) in exceptions:
    cur.execute(
        """INSERT INTO cyber.vuln_exceptions
           (vulnerability_id, title_en, exception_reason, workflow_status,
            owner_name, approver_name, compensating_controls, impact_assessment,
            expiry_date, approved_at)
           VALUES (?,?,?,?,?,?,?,?,?,?)""",
        (vuln_id, title_en, exc_reason, wf_status, owner_name, approver_name,
         comp_controls, impact_assess, expiry_date, approved_at)
    )
print(f"  Inserted {len(exceptions)} vulnerability exception(s)")

# ── cyber_incidents (4 rows) ─────────────────────────────────────────────────
incidents = [
    # incident_number, title_en, title_ar, incident_type,
    # affected_asset_id, affected_asset_name, impact, severity, workflow_status,
    # detected_at, reported_at, resolved_at, incident_lead,
    # notification_sent, bcm_handoff, linked_vuln_count, lessons_learned
    ('INC-2025-001',
     'Ransomware Attack on Employee Workstations',
     'هجوم برنامج الفدية على أجهزة حاسوب الموظفين',
     'RANSOMWARE', 3, 'Employee Workstations',
     'HIGH', 'CRITICAL', 'CLOSED',
     '2025-01-08 02:15:00', '2025-01-08 03:00:00', '2025-01-09 18:30:00',
     'Faisal Al-Amri', 1, 1, 2,
     'Workstation images restored from clean backups within 16 hours. '
     'Attack vector identified as phishing email with malicious macro. '
     'Lessons: Enforce macro blocking policy via GPO, enhance phishing '
     'awareness training frequency to quarterly.'),

    ('INC-2025-002',
     'Targeted Phishing Campaign Against Finance Department',
     'حملة تصيد احتيالي موجهة ضد قسم المالية',
     'PHISHING', 3, 'Employee Workstations',
     'MEDIUM', 'HIGH', 'INVESTIGATION',
     '2025-01-25 09:45:00', '2025-01-25 10:15:00', None,
     'Reem Al-Harbi', 1, 0, 0,
     None),

    ('INC-2025-003',
     'Suspected Data Breach – Customer PII Exfiltration Attempt',
     'اشتباه في اختراق البيانات – محاولة تسريب بيانات العملاء الشخصية',
     'DATA_BREACH', 5, 'Customer Database',
     'CRITICAL', 'CRITICAL', 'CONTAINMENT',
     '2025-02-12 22:10:00', '2025-02-12 22:45:00', None,
     'Noura Al-Qahtani', 1, 1, 1,
     None),

    ('INC-2025-004',
     'Unauthorised Access to Core Banking Administration Console',
     'وصول غير مصرح به إلى وحدة تحكم إدارة النظام المصرفي الأساسي',
     'UNAUTHORIZED_ACCESS', 1, 'Core Banking System',
     'HIGH', 'HIGH', 'ERADICATION',
     '2025-03-03 14:30:00', '2025-03-03 15:00:00', None,
     'Abdullah Al-Shehri', 1, 0, 1,
     None),
]
for (inc_num, title_en, title_ar, inc_type,
     asset_id, asset_name, impact, severity, wf_status,
     detected_at, reported_at, resolved_at, inc_lead,
     notif_sent, bcm_handoff, linked_vuln_count, lessons) in incidents:
    cur.execute(
        """INSERT INTO cyber.cyber_incidents
           (incident_number, title_en, title_ar, incident_type,
            affected_asset_id, affected_asset_name, impact, severity,
            workflow_status, detected_at, reported_at, resolved_at,
            incident_lead, notification_sent, bcm_handoff,
            linked_vulnerabilities_count, lessons_learned)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (inc_num, title_en, title_ar, inc_type,
         asset_id, asset_name, impact, severity, wf_status,
         detected_at, reported_at, resolved_at, inc_lead,
         notif_sent, bcm_handoff, linked_vuln_count, lessons)
    )
print(f"  Inserted {len(incidents)} incident(s)")

# ── cyber_evidence (4 rows) ───────────────────────────────────────────────────
evidence_rows = [
    # title_en, evidence_type, control_id, assessment_id,
    # framework_requirement, owner_name, version, review_date,
    # expiry_date, status, file_name
    ('IAM Policy and Procedure Document',
     'DOCUMENT', 1, 1, 'ECC-1-1 Identity and Access Management',
     'IT Security Manager', '2.1', '2025-01-10', '2026-01-10',
     'CURRENT', 'IAM_Policy_v2.1.pdf'),

    ('Quarterly Access Review Report – Q4 2024',
     'REPORT', 1, 1, 'ECC-1-1.3 Periodic Access Review',
     'Compliance Officer', '1.0', '2025-01-12', '2025-04-12',
     'CURRENT', 'Access_Review_Q4_2024.xlsx'),

    ('Network Segmentation Architecture Diagram',
     'DIAGRAM', 2, 2, 'ECC-2-1 Network Segmentation',
     'Network Security Lead', '3.0', '2025-01-18', '2026-01-18',
     'CURRENT', 'Network_Architecture_v3.0.vsdx'),

    ('SOC Playbook – Ransomware Response',
     'PROCEDURE', 4, 4, 'ECC-4-1 Incident Detection and Response',
     'SOC Manager', '1.4', '2025-02-01', '2026-02-01',
     'CURRENT', 'SOC_Playbook_Ransomware_v1.4.pdf'),
]
for (title_en, ev_type, ctrl_id, assess_id, fw_req,
     owner_name, version, review_date, expiry_date,
     status, file_name) in evidence_rows:
    cur.execute(
        """INSERT INTO cyber.cyber_evidence
           (title_en, evidence_type, control_id, assessment_id,
            framework_requirement, owner_name, version, review_date,
            expiry_date, status, file_name)
           VALUES (?,?,?,?,?,?,?,?,?,?,?)""",
        (title_en, ev_type, ctrl_id, assess_id, fw_req,
         owner_name, version, review_date, expiry_date,
         status, file_name)
    )
print(f"  Inserted {len(evidence_rows)} evidence record(s)")

# ── cyber_action_plans (5 rows) ───────────────────────────────────────────────
action_plans = [
    # title_en, title_ar, description_en, source_type, source_id,
    # source_ref, priority, owner_name, due_date, workflow_status,
    # progress_pct, completion_evidence, validation_notes, linked_control_id
    ('Enable TDE on Customer Database',
     'تفعيل تشفير قاعدة بيانات العملاء',
     'Enable Oracle Transparent Data Encryption (TDE) on all tablespaces '
     'containing customer PII and financial records. Coordinate with DBA '
     'team and business for maintenance window. Validate with scan post-implementation.',
     'ASSESSMENT', 3, 'ECC-3-1 Assessment Finding',
     'CRITICAL', 'DBA Team', '2025-02-28', 'IN_PROGRESS', 60,
     None, None, 3),

    ('Remediate Legacy Server DMZ Segmentation Gap',
     'معالجة ثغرة تقسيم الشبكة للخوادم القديمة',
     'Migrate two legacy servers (CBS-LEG-01, CBS-LEG-02) from core network '
     'segment to designated legacy DMZ VLAN. Update firewall rules to permit '
     'only required ports. Conduct post-migration penetration test.',
     'ASSESSMENT', 2, 'ECC-2-1 Assessment Finding',
     'HIGH', 'Network Engineering', '2025-03-15', 'OPEN', 20,
     None, None, 2),

    ('Deploy Endpoint Detection and Response (EDR) Solution',
     'نشر حل الكشف عن التهديدات والاستجابة على نقاط النهاية',
     'Procure and deploy an EDR solution across all employee workstations '
     'following the ransomware incident INC-2025-001. Coverage target is '
     '100% of managed endpoints within 60 days of tool selection.',
     'INCIDENT', 1, 'INC-2025-001 Post-Incident Action',
     'CRITICAL', 'IT Security Manager', '2025-04-30', 'OPEN', 10,
     None, None, None),

    ('Enforce Macro Blocking via Group Policy',
     'تطبيق حظر الماكرو عبر سياسة المجموعة',
     'Configure GPO to block execution of VBA macros from internet-origin '
     'Office documents across all workstations. Allow-list macros signed '
     'with the corporate code-signing certificate.',
     'INCIDENT', 1, 'INC-2025-001 Lessons Learned',
     'HIGH', 'IT Operations Team', '2025-02-15', 'COMPLETED', 100,
     'GPO deployed on 2025-02-14. Verified via compliance report showing '
     '100% of managed workstations enforcing macro block policy.',
     'Validated by IT Security Manager on 2025-02-15. Closed.', None),

    ('Patch Missing Critical Updates on Endpoint Estate',
     'تحديث الرقع الأمنية المفقودة على أجهزة نقاط النهاية',
     'Remediate all critical and high severity missing patches on employee '
     'workstations using SCCM deployment. Target: zero critical unpatched '
     'endpoints within 45 days. Monthly scan to confirm compliance.',
     'VULNERABILITY', 5, 'VULN-0005 Endpoint Patching Gap',
     'HIGH', 'IT Operations Team', '2025-03-15', 'IN_PROGRESS', 45,
     None, None, None),
]
for (title_en, title_ar, desc_en, source_type, source_id, source_ref,
     priority, owner_name, due_date, wf_status, progress_pct,
     comp_evidence, val_notes, linked_ctrl_id) in action_plans:
    completed_at = '2025-02-15' if wf_status == 'COMPLETED' else None
    cur.execute(
        """INSERT INTO cyber.cyber_action_plans
           (title_en, title_ar, description_en, source_type, source_id,
            source_ref, priority, owner_name, due_date, workflow_status,
            progress_pct, completion_evidence, validation_notes,
            linked_control_id, completed_at)
           VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
        (title_en, title_ar, desc_en, source_type, source_id, source_ref,
         priority, owner_name, due_date, wf_status, progress_pct,
         comp_evidence, val_notes, linked_ctrl_id, completed_at)
    )
print(f"  Inserted {len(action_plans)} action plan(s)")

print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 13 – Performance indexes (audit PERF-IX-004)
#   cyber.* tables are filtered by status / workflow_status in the service-layer
#   state machines but the table DDL above creates only the clustered PK. Add a
#   (organisation_id, <status>) composite per tenant-scoped cyber table so the
#   dev bootstrap matches the Flyway migration V229 (which provisions prod).
#   Idempotent: IF NOT EXISTS guards each CREATE so re-running is a safe no-op.
# ─────────────────────────────────────────────────────────────────────────────
step("Create performance indexes (status / workflow_status)")

cyber_status_indexes = [
    ('attack_surface_finding',    'status'),
    ('attack_surface_shadow_it',  'status'),
    ('cloud_services',            'status'),
    ('control_assessments',       'status'),
    ('cyber_action_plans',        'workflow_status'),
    ('cyber_assets',              'status'),
    ('cyber_controls',            'status'),
    ('cyber_evidence',            'status'),
    ('cyber_incidents',           'workflow_status'),
    ('incidents',                 'status'),
    ('root_cause_analyses',       'status'),
    ('vuln_exceptions',           'workflow_status'),
    ('vulnerabilities',           'workflow_status'),
    ('vulnerability_sla_finding', 'status'),
]
created = 0
for tbl, col in cyber_status_indexes:
    suffix = 'wfstatus' if col == 'workflow_status' else 'status'
    idx = f"ix_{tbl}_org_{suffix}"
    run_quiet(
        f"IF NOT EXISTS (SELECT 1 FROM sys.indexes "
        f"WHERE name='{idx}' AND object_id=OBJECT_ID('cyber.{tbl}')) "
        f"CREATE NONCLUSTERED INDEX {idx} ON cyber.{tbl} (organisation_id, {col})"
    )
    created += 1
print(f"  Ensured {created} cyber status index(es)")

print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

# ─────────────────────────────────────────────────────────────────────────────
# STEP 14 – Verify row counts
# ─────────────────────────────────────────────────────────────────────────────
step("Verify row counts")

tables = [
    'cyber.cyber_frameworks',
    'cyber.cyber_assets',
    'cyber.cyber_controls',
    'cyber.cyber_sub_controls',
    'cyber.control_assessments',
    'cyber.vulnerabilities',
    'cyber.vuln_exceptions',
    'cyber.cyber_incidents',
    'cyber.cyber_evidence',
    'cyber.cyber_action_plans',
]
for tbl in tables:
    cur.execute(f"SELECT COUNT(*) FROM {tbl}")
    count = cur.fetchone()[0]
    print(f"  {tbl:<40} {count:>3} row(s)")

print(f"  Step {step_num[0]}/{TOTAL_STEPS}: OK")

conn.close()
print("\nAll cyber schema steps completed.")
