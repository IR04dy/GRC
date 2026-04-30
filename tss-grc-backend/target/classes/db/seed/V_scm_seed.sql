-- ============================================================
-- SCM seed — values aligned to existing CHECK constraints
-- vendor.category   ∈ {IT_SERVICES, LOGISTICS, MANUFACTURING, FINANCIAL, UTILITIES, CONSULTING, OTHER}
-- vendor.status     ∈ {ACTIVE, INACTIVE, BLACKLISTED, UNDER_REVIEW, EXITED}
-- vendor.onb_status ∈ {PENDING, IN_PROGRESS, COMPLETED, REJECTED}
-- onb.status        ∈ {IN_PROGRESS, COMPLETED, REJECTED, ON_HOLD}
-- assess.type       ∈ {INITIAL, PERIODIC, TRIGGERED}
-- assess.status     ∈ {PLANNED, IN_PROGRESS, COMPLETED, ESCALATED}
-- dep.type          ∈ {CRITICAL, NON_CRITICAL, SHARED, EXCLUSIVE}
-- dep.status        ∈ {ACTIVE, INACTIVE, AT_RISK}
-- inc.type          ∈ {SERVICE_DISRUPTION, BREACH, DELAY, QUALITY, SLA_BREACH, OTHER}
-- inc.status        ∈ {OPEN, IN_PROGRESS, RESOLVED, ESCALATED, CLOSED}
-- contract.type     ∈ {SERVICE, SUPPLY, NDA, FRAMEWORK, OTHER}
-- contract.status   ∈ {ACTIVE, EXPIRED, TERMINATED, DRAFT, UNDER_REVIEW}
-- sla.freq          ∈ {DAILY, WEEKLY, MONTHLY, QUARTERLY}
-- sla.status        ∈ {MET, BREACHED, AT_RISK, NOT_MEASURED}
-- ============================================================

DECLARE @org BIGINT = 1;

-- Wipe any partial data first
DELETE FROM scm.slas               WHERE organisation_id=@org;
DELETE FROM scm.contracts          WHERE organisation_id=@org;
DELETE FROM scm.supplier_incidents WHERE organisation_id=@org;
DELETE FROM scm.dependencies       WHERE organisation_id=@org;
DELETE FROM scm.vendor_assessments WHERE organisation_id=@org;
DELETE FROM scm.vendor_onboarding  WHERE organisation_id=@org;
DELETE FROM scm.vendors            WHERE organisation_id=@org;

-- ── Vendors ──
INSERT INTO scm.vendors (organisation_id, name_en, name_ar, category, criticality, country, service_type, risk_level, status, contact_name, contact_email, contact_phone, registration_number, onboarding_status, description, created_at, is_deleted)
VALUES
(@org, N'STC Cloud Services', N'خدمات الاتصالات السعودية السحابية', N'IT_SERVICES', N'CRITICAL', N'Saudi Arabia', N'Cloud Hosting', N'LOW', N'ACTIVE', N'Khalid Al-Saif', N'kalsaif@stc.com.sa', N'+966-11-455-5555', N'CR1010101010', N'COMPLETED', N'Primary cloud infrastructure provider', SYSUTCDATETIME(), 0),
(@org, N'Mobily Business', N'موبايلي للأعمال', N'IT_SERVICES', N'HIGH', N'Saudi Arabia', N'Connectivity', N'LOW', N'ACTIVE', N'Sara Al-Otaibi', N'sotaibi@mobily.com.sa', N'+966-11-200-2200', N'CR1020202020', N'COMPLETED', N'Primary internet and MPLS connectivity', SYSUTCDATETIME(), 0),
(@org, N'Microsoft Saudi Arabia', N'مايكروسوفت السعودية', N'IT_SERVICES', N'CRITICAL', N'Saudi Arabia', N'Software Licensing', N'LOW', N'ACTIVE', N'Ahmed Hassan', N'ahassan@microsoft.com', N'+966-11-490-0000', N'CR1030303030', N'COMPLETED', N'M365, Azure, and Windows licensing', SYSUTCDATETIME(), 0),
(@org, N'Crowdstrike', N'كراودسترايك', N'IT_SERVICES', N'CRITICAL', N'United States', N'EDR Platform', N'MEDIUM', N'ACTIVE', N'Jane Doe', N'jdoe@crowdstrike.com', N'+1-408-555-0100', N'EIN-CR-001', N'COMPLETED', N'Endpoint detection & response', SYSUTCDATETIME(), 0),
(@org, N'Saudi Business Machines', N'الأعمال السعودية للحاسبات', N'MANUFACTURING', N'HIGH', N'Saudi Arabia', N'Hardware & DC', N'LOW', N'ACTIVE', N'Mohammed Al-Qahtani', N'mqahtani@sbm.com.sa', N'+966-11-291-7777', N'CR1050505050', N'COMPLETED', N'Server hardware and DC operations', SYSUTCDATETIME(), 0),
(@org, N'AWS Middle East', N'أمازون ويب سيرفسز الشرق الأوسط', N'IT_SERVICES', N'HIGH', N'Bahrain', N'Cloud Backup', N'MEDIUM', N'ACTIVE', N'Omar Al-Habib', N'ohabib@amazon.com', N'+973-17-50-0000', N'CR-BH-9001', N'COMPLETED', N'Backup region and DR cloud', SYSUTCDATETIME(), 0),
(@org, N'Cisco Systems Saudi', N'سيسكو السعودية', N'MANUFACTURING', N'MEDIUM', N'Saudi Arabia', N'Network Equipment', N'LOW', N'ACTIVE', N'Talal Al-Yousef', N'tyousef@cisco.com', N'+966-11-489-0000', N'CR1080808080', N'COMPLETED', N'Routers, switches, firewalls', SYSUTCDATETIME(), 0),
(@org, N'KPMG Saudi Arabia', N'كي بي إم جي السعودية', N'CONSULTING', N'MEDIUM', N'Saudi Arabia', N'External Audit', N'LOW', N'ACTIVE', N'Fatima Al-Zahrani', N'fzahrani@kpmg.com.sa', N'+966-11-874-8888', N'CR1090909090', N'COMPLETED', N'External audit & advisory', SYSUTCDATETIME(), 0),
(@org, N'NewCo Cyber Solutions', N'نيوكو لحلول الأمن السيبراني', N'IT_SERVICES', N'MEDIUM', N'Saudi Arabia', N'Pen-Testing', N'MEDIUM', N'UNDER_REVIEW', N'Yousef Al-Shammari', N'yshammari@newco.sa', N'+966-50-111-2233', N'CR-NEW-2025', N'IN_PROGRESS', N'New supplier under onboarding', SYSUTCDATETIME(), 0),
(@org, N'Oracle Cloud KSA', N'أوراكل الحوسبة السحابية', N'IT_SERVICES', N'HIGH', N'Saudi Arabia', N'Database Cloud', N'MEDIUM', N'ACTIVE', N'Hamad Al-Mutairi', N'hmutairi@oracle.com', N'+966-11-461-0000', N'CR1101010101', N'COMPLETED', N'Oracle DB and ERP cloud', SYSUTCDATETIME(), 0);

-- ── Onboarding ──
INSERT INTO scm.vendor_onboarding (organisation_id, vendor_id, current_stage, status, due_date, notes, completed_stages, created_at, is_deleted)
SELECT @org, v.id, 'SECURITY_REVIEW', 'IN_PROGRESS', DATEADD(day, 14, CAST(GETDATE() AS date)), N'In security review stage', N'REGISTRATION,CRITICALITY_CLASSIFICATION', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'NewCo Cyber Solutions';

INSERT INTO scm.vendor_onboarding (organisation_id, vendor_id, current_stage, status, due_date, notes, completed_stages, created_at, is_deleted)
SELECT @org, v.id, 'APPROVAL', 'COMPLETED', NULL, N'Successfully onboarded', N'REGISTRATION,CRITICALITY_CLASSIFICATION,SECURITY_REVIEW,OPERATIONAL_REVIEW,LEGAL_REVIEW,APPROVAL', DATEADD(day, -180, SYSUTCDATETIME()), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.vendor_onboarding (organisation_id, vendor_id, current_stage, status, due_date, notes, completed_stages, created_at, is_deleted)
SELECT @org, v.id, 'APPROVAL', 'COMPLETED', NULL, N'Long-term active vendor', N'REGISTRATION,CRITICALITY_CLASSIFICATION,SECURITY_REVIEW,OPERATIONAL_REVIEW,LEGAL_REVIEW,APPROVAL', DATEADD(day, -365, SYSUTCDATETIME()), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

-- ── Assessments ──
INSERT INTO scm.vendor_assessments (organisation_id, vendor_id, assessment_type, assessment_date, next_assessment_date, overall_score, risk_rating, status, findings, recommendations, escalated_to_risk, created_at, is_deleted)
SELECT @org, v.id, 'PERIODIC', DATEADD(day,-90,CAST(GETDATE() AS date)), DATEADD(day,275,CAST(GETDATE() AS date)), 4.5, 'LOW', 'COMPLETED', N'No major findings. SOC 2 Type II compliant.', N'Renew contract for 3 years', 0, SYSUTCDATETIME(), 0 FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

INSERT INTO scm.vendor_assessments (organisation_id, vendor_id, assessment_type, assessment_date, next_assessment_date, overall_score, risk_rating, status, findings, recommendations, escalated_to_risk, created_at, is_deleted)
SELECT @org, v.id, 'INITIAL', DATEADD(day,-30,CAST(GETDATE() AS date)), DATEADD(day,335,CAST(GETDATE() AS date)), 3.8, 'MEDIUM', 'COMPLETED', N'Strong technical capabilities; some process gaps in incident reporting.', N'Quarterly incident reports required', 0, SYSUTCDATETIME(), 0 FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.vendor_assessments (organisation_id, vendor_id, assessment_type, assessment_date, next_assessment_date, overall_score, risk_rating, status, findings, recommendations, escalated_to_risk, created_at, is_deleted)
SELECT @org, v.id, 'PERIODIC', DATEADD(day,-180,CAST(GETDATE() AS date)), DATEADD(day,185,CAST(GETDATE() AS date)), 2.9, 'HIGH', 'ESCALATED', N'Concentration risk identified: only KPMG provides external audit.', N'Diversify audit suppliers; add a secondary audit firm.', 1, SYSUTCDATETIME(), 0 FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'KPMG Saudi Arabia';

INSERT INTO scm.vendor_assessments (organisation_id, vendor_id, assessment_type, assessment_date, next_assessment_date, overall_score, risk_rating, status, findings, recommendations, escalated_to_risk, created_at, is_deleted)
SELECT @org, v.id, 'PERIODIC', DATEADD(day,-60,CAST(GETDATE() AS date)), DATEADD(day,305,CAST(GETDATE() AS date)), 4.2, 'LOW', 'COMPLETED', N'ISO 27001 and SOC 2 certified. Stable performance.', N'No action required', 0, SYSUTCDATETIME(), 0 FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Microsoft Saudi Arabia';

INSERT INTO scm.vendor_assessments (organisation_id, vendor_id, assessment_type, assessment_date, next_assessment_date, overall_score, risk_rating, status, findings, recommendations, escalated_to_risk, created_at, is_deleted)
SELECT @org, v.id, 'TRIGGERED', DATEADD(day,-15,CAST(GETDATE() AS date)), NULL, 3.5, 'MEDIUM', 'IN_PROGRESS', N'Initial review pending security checklist', N'Complete checklist before approval', 0, SYSUTCDATETIME(), 0 FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'NewCo Cyber Solutions';

-- ── Dependencies ──
INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Production Web Hosting', N'استضافة الويب الإنتاجية', N'web-prod-cluster', v.id, 'CRITICAL', 'HIGH', 1,
  (SELECT id FROM scm.vendors WHERE organisation_id=@org AND name_en=N'AWS Middle East'),
  'ACTIVE', N'Failure would take down customer-facing portal. AWS as DR.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Office 365 Email', N'البريد الإلكتروني أوفيس 365', N'mail-tenant', v.id, 'EXCLUSIVE', 'HIGH', 0, NULL, 'ACTIVE', N'Sole provider for corporate email and collaboration. No alternative configured.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Microsoft Saudi Arabia';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Endpoint EDR', N'حماية نقاط النهاية', N'edr-fleet', v.id, 'EXCLUSIVE', 'HIGH', 0, NULL, 'ACTIVE', N'Sole EDR; loss would degrade detection capability.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Internet Connectivity', N'الاتصال بالإنترنت', N'wan-primary', v.id, 'SHARED', 'HIGH', 1,
  (SELECT id FROM scm.vendors WHERE organisation_id=@org AND name_en=N'STC Cloud Services'),
  'ACTIVE', N'Primary internet link. STC as failover.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Mobily Business';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'ERP Database', N'قاعدة بيانات نظام تخطيط الموارد', N'erp-prod-db', v.id, 'CRITICAL', 'MEDIUM', 0, NULL, 'ACTIVE', N'Oracle DB is the system of record for finance.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Oracle Cloud KSA';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Network Firewall', N'جدار الحماية الشبكي', N'fw-perimeter', v.id, 'EXCLUSIVE', 'MEDIUM', 0, NULL, 'ACTIVE', N'Perimeter firewall protecting all ingress/egress.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Cisco Systems Saudi';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Annual Audit', N'التدقيق السنوي', N'compliance-program', v.id, 'NON_CRITICAL', 'LOW', 0, NULL, 'ACTIVE', N'Statutory annual audit.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'KPMG Saudi Arabia';

INSERT INTO scm.dependencies (organisation_id, service_name_en, service_name_ar, asset_name, vendor_id, dependency_type, dependency_level, has_alternative, alternative_vendor_id, status, impact_analysis, created_at, is_deleted)
SELECT @org, N'Server Hardware Maintenance', N'صيانة الأجهزة', N'dc-hw-pool', v.id, 'SHARED', 'MEDIUM', 0, NULL, 'ACTIVE', N'On-prem hardware maintenance contract.', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Saudi Business Machines';

-- ── Supplier Incidents ──
INSERT INTO scm.supplier_incidents (organisation_id, incident_code, vendor_id, incident_type, impact, description, sla_breached, status, backup_plan_activated, capa_created, escalated_to_management, reported_at, created_at, is_deleted)
SELECT @org, 'SCM-INC-2025-001', v.id, 'SERVICE_DISRUPTION', 'HIGH', N'4-hour outage on cloud hosting affecting customer portal.', 1, 'RESOLVED', 1, 1, 1, DATEADD(day, -45, SYSUTCDATETIME()), SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

INSERT INTO scm.supplier_incidents (organisation_id, incident_code, vendor_id, incident_type, impact, description, sla_breached, status, backup_plan_activated, capa_created, escalated_to_management, reported_at, created_at, is_deleted)
SELECT @org, 'SCM-INC-2025-002', v.id, 'QUALITY', 'MEDIUM', N'Sustained latency increase on internet links.', 0, 'OPEN', 0, 0, 0, DATEADD(day, -7, SYSUTCDATETIME()), SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Mobily Business';

INSERT INTO scm.supplier_incidents (organisation_id, incident_code, vendor_id, incident_type, impact, description, sla_breached, status, backup_plan_activated, capa_created, escalated_to_management, reported_at, created_at, is_deleted)
SELECT @org, 'SCM-INC-2025-003', v.id, 'BREACH', 'CRITICAL', N'Vendor disclosed CVE-2025-12345 affecting EDR agent.', 0, 'IN_PROGRESS', 0, 1, 0, DATEADD(day, -3, SYSUTCDATETIME()), SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.supplier_incidents (organisation_id, incident_code, vendor_id, incident_type, impact, description, sla_breached, status, backup_plan_activated, capa_created, escalated_to_management, reported_at, created_at, is_deleted)
SELECT @org, 'SCM-INC-2025-004', v.id, 'OTHER', 'LOW', N'Disputed invoice items for Q1.', 0, 'OPEN', 0, 0, 0, DATEADD(day, -10, SYSUTCDATETIME()), SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Oracle Cloud KSA';

INSERT INTO scm.supplier_incidents (organisation_id, incident_code, vendor_id, incident_type, impact, description, sla_breached, status, backup_plan_activated, capa_created, escalated_to_management, reported_at, created_at, is_deleted)
SELECT @org, 'SCM-INC-2025-005', v.id, 'DELAY', 'MEDIUM', N'Audit deliverable late by 5 business days.', 1, 'RESOLVED', 0, 0, 0, DATEADD(day, -60, SYSUTCDATETIME()), SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'KPMG Saudi Arabia';

-- ── Contracts ──
INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2024-001', N'STC Cloud Master Services Agreement', N'اتفاقية خدمات الحوسبة السحابية', 'FRAMEWORK', '2024-01-01', '2026-12-31', 0, 4500000, 'SAR', 'ACTIVE', N'3-year MSA with quarterly billing', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2023-007', N'Microsoft Enterprise Agreement', N'اتفاقية مايكروسوفت للمؤسسات', 'SERVICE', '2023-07-01', '2026-06-30', 1, 2800000, 'SAR', 'ACTIVE', N'EA with auto-renewal', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Microsoft Saudi Arabia';

INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2024-014', N'Crowdstrike Falcon Subscription', N'اشتراك كراودسترايك فالكون', 'SERVICE', '2024-04-01', '2025-06-30', 0, 800000, 'SAR', 'ACTIVE', N'Annual subscription — expiring soon', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2023-002', N'Mobily MPLS Connectivity', N'الاتصال عبر شبكة موبايلي', 'SERVICE', '2023-01-01', '2025-12-31', 0, 1200000, 'SAR', 'ACTIVE', N'WAN connectivity', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Mobily Business';

INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2024-022', N'Oracle Cloud Database', N'قاعدة بيانات أوراكل السحابية', 'SERVICE', '2024-08-01', '2027-07-31', 1, 1800000, 'SAR', 'ACTIVE', N'3-year multi-region subscription', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'Oracle Cloud KSA';

INSERT INTO scm.contracts (organisation_id, vendor_id, contract_number, title_en, title_ar, contract_type, start_date, end_date, auto_renewal, contract_value, currency, status, notes, created_at, is_deleted)
SELECT @org, v.id, 'CTR-2024-031', N'KPMG External Audit Engagement', N'ارتباط التدقيق الخارجي', 'SERVICE', '2024-10-01', '2025-09-30', 0, 350000, 'SAR', 'ACTIVE', N'Annual statutory audit', SYSUTCDATETIME(), 0
FROM scm.vendors v WHERE v.organisation_id=@org AND v.name_en=N'KPMG Saudi Arabia';

-- ── SLAs ──
INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Uptime', N'وقت التشغيل', 99.95, '%', 'MONTHLY', 99.97, 'MET', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'STC Cloud Services';

INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Response Time', N'زمن الاستجابة', 200, 'ms', 'MONTHLY', 245, 'AT_RISK', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'Mobily Business';

INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Incident Response', N'الاستجابة للحوادث', 1, 'hours', 'MONTHLY', 0.8, 'MET', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'Crowdstrike';

INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Service Availability', N'توفر الخدمة', 99.9, '%', 'MONTHLY', 99.92, 'MET', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'Microsoft Saudi Arabia';

INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Patch Lead Time', N'زمن إصدار التحديثات', 30, 'days', 'QUARTERLY', 35, 'BREACHED', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'Oracle Cloud KSA';

INSERT INTO scm.slas (organisation_id, vendor_id, contract_id, metric_name_en, metric_name_ar, target_value, unit, measurement_frequency, current_value, status, last_measured_at, created_at, is_deleted)
SELECT @org, v.id, c.id, N'Audit Delivery', N'تسليم التدقيق', 90, 'days', 'QUARTERLY', 95, 'AT_RISK', SYSUTCDATETIME(), SYSUTCDATETIME(), 0
FROM scm.vendors v INNER JOIN scm.contracts c ON c.vendor_id=v.id AND c.organisation_id=v.organisation_id
WHERE v.organisation_id=@org AND v.name_en=N'KPMG Saudi Arabia';

PRINT 'SCM seed loaded successfully';
GO
