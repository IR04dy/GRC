# -*- coding: utf-8 -*-
"""Extend BCM schema and seed data for TSS GRC Platform."""
import sys, pyodbc
sys.stdout.reconfigure(encoding='utf-8')

conn = pyodbc.connect(
    'DRIVER={ODBC Driver 17 for SQL Server};SERVER=localhost;DATABASE=TSS_GRC;'
    'UID=sa;PWD=tss.123;Unicode_Results=Yes;'
)
conn.autocommit = True
cur = conn.cursor()

def run(label, sql):
    try:
        cur.execute(sql)
        print(f"  OK  {label}")
    except Exception as e:
        s = str(e)
        if any(w in s.lower() for w in ['already', 'duplicate', 'exist']):
            print(f"  --  {label} (skip)")
        else:
            print(f"  ERR {label}: {s[:120]}")

def add_col(tbl, col, defn):
    run(f"{tbl}.{col}",
        f"IF NOT EXISTS (SELECT 1 FROM sys.columns WHERE object_id=OBJECT_ID('{tbl}') AND name='{col}')"
        f" ALTER TABLE {tbl} ADD {col} {defn}")

# ── critical_processes extensions ────────────────────────────────────────────
print("=== critical_processes ===")
add_col('bcm.critical_processes','rto_hours',      'INT NULL')
add_col('bcm.critical_processes','rpo_hours',      'INT NULL')
add_col('bcm.critical_processes','mtpd_hours',     'INT NULL')
add_col('bcm.critical_processes','business_unit',  'NVARCHAR(255) NULL')
add_col('bcm.critical_processes','description_en', 'NVARCHAR(MAX) NULL')

# ── bia_records extensions ────────────────────────────────────────────────────
print("=== bia_records ===")
add_col('bcm.bia_records','organisation_id',         "BIGINT NOT NULL DEFAULT 1")
add_col('bcm.bia_records','financial_impact',        'NVARCHAR(20) NULL')
add_col('bcm.bia_records','operational_impact',      'NVARCHAR(20) NULL')
add_col('bcm.bia_records','regulatory_impact',       'NVARCHAR(20) NULL')
add_col('bcm.bia_records','reputational_impact',     'NVARCHAR(20) NULL')
add_col('bcm.bia_records','criticality',             "NVARCHAR(20) NOT NULL DEFAULT 'MEDIUM'")
add_col('bcm.bia_records','risk_level',              'NVARCHAR(20) NULL')
add_col('bcm.bia_records','owner_name',              'NVARCHAR(255) NULL')
add_col('bcm.bia_records','business_unit',           'NVARCHAR(255) NULL')
add_col('bcm.bia_records','dependencies_count',      'INT NOT NULL DEFAULT 0')
add_col('bcm.bia_records','last_review_date',        'DATE NULL')
add_col('bcm.bia_records','version_number',          'INT NOT NULL DEFAULT 1')
add_col('bcm.bia_records','approved_by',             'NVARCHAR(255) NULL')
add_col('bcm.bia_records','approved_at',             'DATETIME2 NULL')
add_col('bcm.bia_records','notes',                   'NVARCHAR(MAX) NULL')
add_col('bcm.bia_records','is_deleted',              'BIT NOT NULL DEFAULT 0')
add_col('bcm.bia_records','converted_to_plan_id',    'BIGINT NULL')
add_col('bcm.bia_records','recovery_tasks_generated','BIT NOT NULL DEFAULT 0')

# Drop any CHECK on status so we can use extended status values
run("drop bia status check",
    "DECLARE @c NVARCHAR(200); SELECT @c=name FROM sys.check_constraints "
    "WHERE parent_object_id=OBJECT_ID('bcm.bia_records') AND name LIKE '%status%'; "
    "IF @c IS NOT NULL EXEC('ALTER TABLE bcm.bia_records DROP CONSTRAINT '+@c)")

# ── bcm_plans extensions ──────────────────────────────────────────────────────
print("=== bcm_plans ===")
add_col('bcm.bcm_plans','description_en','NVARCHAR(MAX) NULL')
add_col('bcm.bcm_plans','owner_name',    'NVARCHAR(255) NULL')

# ── new tables ────────────────────────────────────────────────────────────────
print("=== new tables ===")
run("bcm_exercises", """
IF NOT EXISTS(SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID('bcm') AND name='bcm_exercises')
CREATE TABLE bcm.bcm_exercises(
  id BIGINT IDENTITY(1,1) NOT NULL, external_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
  organisation_id BIGINT NOT NULL, plan_id BIGINT NULL,
  title_en NVARCHAR(300) NOT NULL, title_ar NVARCHAR(300) NULL,
  exercise_type NVARCHAR(50) NOT NULL DEFAULT 'TABLETOP',
  scheduled_date DATE NULL, executed_date DATE NULL,
  status NVARCHAR(50) NOT NULL DEFAULT 'PLANNED',
  facilitator NVARCHAR(255) NULL, objectives NVARCHAR(MAX) NULL,
  findings NVARCHAR(MAX) NULL, overall_result NVARCHAR(50) NULL,
  is_deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  deleted_by BIGINT NULL,
  CONSTRAINT PK_bcm_exercises PRIMARY KEY(id))""")

run("recovery_operations", """
IF NOT EXISTS(SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID('bcm') AND name='recovery_operations')
CREATE TABLE bcm.recovery_operations(
  id BIGINT IDENTITY(1,1) NOT NULL, external_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
  organisation_id BIGINT NOT NULL, title_en NVARCHAR(300) NOT NULL, title_ar NVARCHAR(300) NULL,
  operation_type NVARCHAR(50) NOT NULL DEFAULT 'BCP_ACTIVATION',
  bia_id BIGINT NULL, plan_id BIGINT NULL,
  triggered_at DATETIME2 NULL, resolved_at DATETIME2 NULL,
  rto_target_hours INT NULL, rto_actual_hours INT NULL,
  status NVARCHAR(50) NOT NULL DEFAULT 'OPEN',
  assigned_to NVARCHAR(255) NULL, description_en NVARCHAR(MAX) NULL,
  is_deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  deleted_by BIGINT NULL,
  CONSTRAINT PK_recovery_operations PRIMARY KEY(id))""")

run("crisis_events", """
IF NOT EXISTS(SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID('bcm') AND name='crisis_events')
CREATE TABLE bcm.crisis_events(
  id BIGINT IDENTITY(1,1) NOT NULL, external_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
  organisation_id BIGINT NOT NULL, title_en NVARCHAR(300) NOT NULL, title_ar NVARCHAR(300) NULL,
  crisis_type NVARCHAR(100) NOT NULL DEFAULT 'OPERATIONAL',
  severity NVARCHAR(20) NOT NULL DEFAULT 'HIGH',
  occurred_at DATETIME2 NULL, resolved_at DATETIME2 NULL,
  status NVARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
  incident_commander NVARCHAR(255) NULL, activated_plan_id BIGINT NULL,
  description_en NVARCHAR(MAX) NULL,
  is_deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  deleted_by BIGINT NULL,
  CONSTRAINT PK_crisis_events PRIMARY KEY(id))""")

run("critical_resources", """
IF NOT EXISTS(SELECT 1 FROM sys.tables WHERE schema_id=SCHEMA_ID('bcm') AND name='critical_resources')
CREATE TABLE bcm.critical_resources(
  id BIGINT IDENTITY(1,1) NOT NULL, external_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWID(),
  organisation_id BIGINT NOT NULL, resource_name NVARCHAR(300) NOT NULL,
  resource_type NVARCHAR(50) NOT NULL DEFAULT 'SYSTEM', process_id BIGINT NULL,
  criticality NVARCHAR(20) NOT NULL DEFAULT 'HIGH', rto_hours INT NULL,
  owner_name NVARCHAR(255) NULL, description_en NVARCHAR(MAX) NULL,
  status NVARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
  is_deleted BIT NOT NULL DEFAULT 0, created_at DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
  deleted_by BIGINT NULL,
  CONSTRAINT PK_critical_resources PRIMARY KEY(id))""")

# ── seed data ─────────────────────────────────────────────────────────────────
print("=== seed data ===")
run("seed processes", """
IF NOT EXISTS(SELECT 1 FROM bcm.critical_processes WHERE organisation_id=1)
INSERT INTO bcm.critical_processes(organisation_id,process_name_en,process_name_ar,criticality,business_unit,rto_hours,rpo_hours,mtpd_hours,description_en,is_deleted,created_at)VALUES
(1,N'Core Banking Operations',N'عمليات البنك الأساسية','CRITICAL','Finance',2,1,4,N'Primary banking transaction processing',0,SYSUTCDATETIME()),
(1,N'Customer Identity Verification',N'التحقق من هوية العميل','HIGH','Operations',8,4,24,N'KYC and identity management processes',0,SYSUTCDATETIME()),
(1,N'Payment Processing Gateway',N'بوابة معالجة المدفوعات','CRITICAL','Finance',2,1,4,N'Real-time payment processing and routing',0,SYSUTCDATETIME()),
(1,N'Human Resources Management',N'إدارة الموارد البشرية','MEDIUM','HR',24,12,72,N'Employee data and payroll management',0,SYSUTCDATETIME()),
(1,N'IT Infrastructure & Data Centers',N'البنية التحتية لتقنية المعلومات','CRITICAL','IT',2,1,4,N'Servers, network, and data center ops',0,SYSUTCDATETIME()),
(1,N'Regulatory Reporting',N'التقارير التنظيمية','HIGH','Compliance',24,12,48,N'SAMA regulatory submissions',0,SYSUTCDATETIME()),
(1,N'Customer Support Center',N'مركز دعم العملاء','MEDIUM','Customer Svc',8,4,24,N'Call center and digital support',0,SYSUTCDATETIME()),
(1,N'Cybersecurity Operations',N'عمليات الأمن السيبراني','HIGH','Security',4,2,12,N'SOC operations and threat monitoring',0,SYSUTCDATETIME())""")

run("seed bia_records", """
IF NOT EXISTS(SELECT 1 FROM bcm.bia_records WHERE organisation_id=1)
BEGIN
  INSERT INTO bcm.bia_records(process_id,assessment_date,financial_impact,operational_impact,regulatory_impact,reputational_impact,criticality,rto_hours,rpo_hours,mtpd_hours,overall_impact,status,owner_name,business_unit,organisation_id,created_at)
  SELECT id,'2026-03-15','CATASTROPHIC','CATASTROPHIC','MAJOR','CATASTROPHIC','CRITICAL',2,1,4,'CRITICAL','APPROVED',N'Ahmed Al-Rashidi',N'Finance',1,SYSUTCDATETIME() FROM bcm.critical_processes WHERE organisation_id=1 AND process_name_en='Core Banking Operations';
  INSERT INTO bcm.bia_records(process_id,assessment_date,financial_impact,operational_impact,regulatory_impact,reputational_impact,criticality,rto_hours,rpo_hours,mtpd_hours,overall_impact,status,owner_name,business_unit,organisation_id,created_at)
  SELECT id,'2026-03-18','MAJOR','MAJOR','CATASTROPHIC','MAJOR','HIGH',4,2,12,'HIGH','APPROVED',N'Sara Al-Mutairi',N'Operations',1,SYSUTCDATETIME() FROM bcm.critical_processes WHERE organisation_id=1 AND process_name_en='Customer Identity Verification';
  INSERT INTO bcm.bia_records(process_id,assessment_date,financial_impact,operational_impact,regulatory_impact,reputational_impact,criticality,rto_hours,rpo_hours,mtpd_hours,overall_impact,status,owner_name,business_unit,organisation_id,created_at)
  SELECT id,'2026-03-20','CATASTROPHIC','CATASTROPHIC','MAJOR','CATASTROPHIC','CRITICAL',2,1,4,'CRITICAL','UNDER_REVIEW',N'Khalid Al-Zahrani',N'Finance',1,SYSUTCDATETIME() FROM bcm.critical_processes WHERE organisation_id=1 AND process_name_en='Payment Processing Gateway';
  INSERT INTO bcm.bia_records(process_id,assessment_date,financial_impact,operational_impact,regulatory_impact,reputational_impact,criticality,rto_hours,rpo_hours,mtpd_hours,overall_impact,status,owner_name,business_unit,organisation_id,created_at)
  SELECT id,'2026-04-01','MODERATE','MODERATE','MINOR','MINOR','MEDIUM',24,12,72,'MEDIUM','IN_PROGRESS',N'Fatima Al-Ghamdi',N'HR',1,SYSUTCDATETIME() FROM bcm.critical_processes WHERE organisation_id=1 AND process_name_en='Human Resources Management';
  INSERT INTO bcm.bia_records(process_id,assessment_date,financial_impact,operational_impact,regulatory_impact,reputational_impact,criticality,rto_hours,rpo_hours,mtpd_hours,overall_impact,status,owner_name,business_unit,organisation_id,created_at)
  SELECT id,'2026-04-05','MAJOR','MAJOR','MAJOR','MAJOR','HIGH',8,4,24,'HIGH','DRAFT',N'Mohammed Al-Harbi',N'Compliance',1,SYSUTCDATETIME() FROM bcm.critical_processes WHERE organisation_id=1 AND process_name_en='Regulatory Reporting';
END""")

run("seed bcm_plans", """
IF NOT EXISTS(SELECT 1 FROM bcm.bcm_plans WHERE organisation_id=1)
INSERT INTO bcm.bcm_plans(organisation_id,plan_name_en,plan_name_ar,plan_type,version,status,next_review_date,is_deleted,created_at)VALUES
(1,N'Business Continuity Plan — Finance',N'خطة استمرارية الأعمال — المالية','BCP','v2.1','ACTIVE','2026-09-30',0,SYSUTCDATETIME()),
(1,N'IT Disaster Recovery Plan',N'خطة التعافي من كوارث تقنية المعلومات','DRP','v1.3','APPROVED','2026-06-30',0,SYSUTCDATETIME()),
(1,N'Crisis Management Plan',N'خطة إدارة الأزمات','CRISIS_MGMT','v1.0','DRAFT','2026-12-31',0,SYSUTCDATETIME()),
(1,N'Emergency Response Plan',N'خطة الاستجابة للطوارئ','ERP','v1.1','ACTIVE','2026-09-30',0,SYSUTCDATETIME())""")

run("seed bcm_exercises", """
IF NOT EXISTS(SELECT 1 FROM bcm.bcm_exercises WHERE organisation_id=1)
INSERT INTO bcm.bcm_exercises(organisation_id,title_en,title_ar,exercise_type,scheduled_date,status,facilitator,objectives,is_deleted,created_at)VALUES
(1,N'Q1 Tabletop — Payment Failure',N'تمرين طاولة Q1 — فشل المدفوعات','TABLETOP','2026-03-15','COMPLETED',N'Ahmed Al-Rashidi',N'Test response to payment gateway outage',0,SYSUTCDATETIME()),
(1,N'IT DR Full-Scale Drill',N'تمرين التعافي الكامل من كوارث تقنية المعلومات','FULL_SCALE','2026-05-20','PLANNED',N'Khalid Al-Zahrani',N'Full failover to DR site',0,SYSUTCDATETIME()),
(1,N'Call Tree Activation Test',N'اختبار تفعيل شجرة الاتصال','CALL_TREE','2026-04-10','COMPLETED',N'Sara Al-Mutairi',N'Verify all contact lists are current',0,SYSUTCDATETIME()),
(1,N'Cybersecurity Incident Simulation',N'محاكاة حادثة الأمن السيبراني','SIMULATION','2026-06-15','PLANNED',N'Mohammed Al-Harbi',N'Simulate ransomware attack response',0,SYSUTCDATETIME())""")

run("seed recovery_operations", """
IF NOT EXISTS(SELECT 1 FROM bcm.recovery_operations WHERE organisation_id=1)
INSERT INTO bcm.recovery_operations(organisation_id,title_en,title_ar,operation_type,rto_target_hours,status,assigned_to,description_en,is_deleted,created_at)VALUES
(1,N'Core Banking Failover Activation',N'تفعيل التعافي للبنك الأساسي','BCP_ACTIVATION',4,'CLOSED',N'Ahmed Al-Rashidi',N'Activated fallback banking systems',0,SYSUTCDATETIME()),
(1,N'Payment Gateway Restore',N'استعادة بوابة المدفوعات','SYSTEM_RESTORE',2,'OPEN',N'Khalid Al-Zahrani',N'Restore payment processing after failure',0,SYSUTCDATETIME()),
(1,N'HR System DR Activation',N'تفعيل التعافي لنظام الموارد البشرية','BCP_ACTIVATION',24,'PLANNED',N'Fatima Al-Ghamdi',N'Activate HR system backup at DR site',0,SYSUTCDATETIME())""")

run("seed crisis_events", """
IF NOT EXISTS(SELECT 1 FROM bcm.crisis_events WHERE organisation_id=1)
INSERT INTO bcm.crisis_events(organisation_id,title_en,title_ar,crisis_type,severity,status,incident_commander,description_en,is_deleted,created_at)VALUES
(1,N'Core Banking Outage — March 2026',N'انقطاع نظام البنك الأساسي — مارس 2026','SYSTEM_OUTAGE','CRITICAL','RESOLVED',N'Ahmed Al-Rashidi',N'4-hour outage affecting all transactions',0,SYSUTCDATETIME()),
(1,N'Data Center Power Failure',N'انقطاع الكهرباء في مركز البيانات','INFRASTRUCTURE','HIGH','RESOLVED',N'Khalid Al-Zahrani',N'UPS failure led to 2-hour system downtime',0,SYSUTCDATETIME()),
(1,N'Ransomware Containment Event',N'حدث احتواء برنامج الفدية','CYBER_ATTACK','CRITICAL','ACTIVE',N'Mohammed Al-Harbi',N'Suspected ransomware in isolated network segment',0,SYSUTCDATETIME())""")

run("seed critical_resources", """
IF NOT EXISTS(SELECT 1 FROM bcm.critical_resources WHERE organisation_id=1)
INSERT INTO bcm.critical_resources(organisation_id,resource_name,resource_type,criticality,rto_hours,owner_name,description_en,status,is_deleted,created_at)VALUES
(1,N'Core Banking Application Servers','SYSTEM','CRITICAL',2,N'Khalid Al-Zahrani',N'Primary and failover servers for core banking','ACTIVE',0,SYSUTCDATETIME()),
(1,N'Primary Data Center — Riyadh','FACILITY','CRITICAL',4,N'Ahmed Al-Rashidi',N'Tier 3 data center hosting all primary workloads','ACTIVE',0,SYSUTCDATETIME()),
(1,N'Payment Gateway API License','VENDOR','HIGH',2,N'Sara Al-Mutairi',N'Third-party payment processing vendor','ACTIVE',0,SYSUTCDATETIME()),
(1,N'DR Data Center — Jeddah','FACILITY','CRITICAL',8,N'Ahmed Al-Rashidi',N'Secondary site for disaster recovery failover','ACTIVE',0,SYSUTCDATETIME()),
(1,N'IT Operations Team','PEOPLE','CRITICAL',1,N'Khalid Al-Zahrani',N'24/7 on-call IT staff for critical systems','ACTIVE',0,SYSUTCDATETIME()),
(1,N'Internet Service Provider (ISP)','VENDOR','HIGH',4,N'Mohammed Al-Harbi',N'Primary and backup ISP connectivity','ACTIVE',0,SYSUTCDATETIME())""")

conn.close()
print("\nDone.")
