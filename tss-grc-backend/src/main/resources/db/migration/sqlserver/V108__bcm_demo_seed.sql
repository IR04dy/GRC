-- V108: BCM demo seed — rich sample data so no page / table is blank on first login.
--
-- Why: the new "BCM cycle" track (V98-V107) ships with the schema but only
-- thin seeds: one assessment cycle, one governance policy, three communication
-- contacts, four compliance-mapping placeholders. Every other table is empty,
-- so a fresh tenant opens /bcm/* and sees blank tabs everywhere. That misleads
-- evaluators about what the platform can do.
--
-- This migration seeds organisation_id = 1 (the default tenant) with realistic
-- KSA-flavoured scenarios across every BCM table. Numbers are picked so the
-- dashboards have something interesting to show (mix of statuses, severities,
-- and bilingual names). Every block is idempotent — if any rows already exist
-- in the target table for org 1 we skip.
--
-- Tables seeded (14):
--   bcm.assessment_cycles (+ 2026 cycle)
--   bcm.bcm_risks               12 rows across threat categories + statuses
--   bcm.continuity_strategies   8 rows mixing approval status
--   bcm.bia_assessments         6 BIAs (incl. one SOVEREIGN)
--   bcm.bcp_plans               6 plans (3 BCP + 3 DRP)
--   bcm.bcp_sections           ~72 rows — 12 ISO sections per plan
--   bcm.bcp_versions            6 rows — one published snapshot per approved plan
--   bcm.lessons_learned         8 rows across source types
--   bcm.gaps                   10 rows across severities + statuses
--   bcm.corrective_actions     10 CAPAs linked to the gaps
--   bcm.crisis_events           5 events (1 active, 2 closed, 2 historical)
--   bcm.crisis_situation_reports ~12 SitReps
--   bcm.crisis_decisions       ~10 decisions
--   bcm.crisis_regulatory_notifications  6 regulator notifications
--   bcm.settings                1 tenant settings row

SET NOCOUNT ON;
GO

DECLARE @org BIGINT = 1;

-- ============================================================================
-- 1. Extra assessment cycle (2026) so the cycles tab shows >1 row
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026)
BEGIN
    INSERT INTO bcm.assessment_cycles
      (organisation_id, cycle_name_en, cycle_name_ar, reporting_year,
       start_date, end_date, scope, status,
       overall_score, bia_completion_percent, bcp_completion_percent,
       drp_completion_percent, evidence_completion_percent,
       open_gap_count, critical_gap_count)
    VALUES
      (1,
       'BCM Readiness Cycle 2026',
       N'دورة جاهزية استمرارية الأعمال 2026',
       2026,
       '2026-01-01', '2026-12-31',
       'Annual readiness cycle covering all critical services, BIA refresh, BCP/DRP exercises, and SAMA/NCEMA submissions.',
       'IN_PROGRESS',
       72.5, 80.0, 65.0, 75.0, 55.0, 8, 2);
END
GO

-- Bump the 2025 cycle's KPIs so the dashboard isn't all zeros
UPDATE bcm.assessment_cycles
   SET overall_score = 88.0,
       bia_completion_percent = 100.0,
       bcp_completion_percent = 95.0,
       drp_completion_percent = 90.0,
       evidence_completion_percent = 92.0,
       open_gap_count = 3,
       critical_gap_count = 0,
       status = 'APPROVED',
       approved_by = 'Khalid Al-Mutairi',
       approved_at = '2025-12-15 09:00:00'
 WHERE organisation_id = 1
   AND reporting_year = 2025
   AND (overall_score IS NULL OR overall_score < 50);
GO

-- ============================================================================
-- 2. BCM risks — 12 rows across the 18 threat categories
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.bcm_risks WHERE organisation_id = 1)
BEGIN
    DECLARE @cycle2026 BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.bcm_risks
      (organisation_id, cycle_id, title_en, title_ar,
       cause, event, consequence, threat_category, existing_controls,
       likelihood, impact, inherent_risk_score,
       control_effectiveness, residual_risk_score,
       treatment_plan, status)
    VALUES
      (1, @cycle2026,
       'Primary data centre power outage',
       N'انقطاع التيار في مركز البيانات الرئيسي',
       'Single feed from the SEC grid; UPS lifetime exceeds vendor recommendation.',
       'A multi-hour blackout takes core banking offline.',
       'Customer-facing services unavailable; SAMA reporting breach.',
       'POWER_FAILURE',
       'Dual-feed agreement (pending), UPS, generator with 24h fuel.',
       3, 5, 15, 'NEEDS_IMPROVEMENT', 9,
       'Activate dual-feed contract Q2; quarterly generator load test.', 'TREATMENT_PLANNED'),

      (1, @cycle2026,
       'Ransomware encrypts core banking volumes',
       N'برنامج فدية يشفّر وحدات تخزين الخدمات المصرفية',
       'Phishing-led credential compromise of privileged operator.',
       'Mass encryption across production storage.',
       'Outage > 24h, SAMA breach notification, customer harm.',
       'CYBERATTACK',
       'EDR, immutable backups, network segmentation (partial).',
       3, 5, 15, 'NEEDS_IMPROVEMENT', 12,
       'Complete segmentation, run table-top exercise, validate restore SLA.', 'ASSESSED'),

      (1, @cycle2026,
       'Hyperscaler regional outage (Riyadh)',
       N'انقطاع منطقة المزوّد السحابي (الرياض)',
       'Cloud-provider availability-zone failure.',
       'All Riyadh-hosted workloads unavailable.',
       'Digital channels down; failover to Jeddah region.',
       'CLOUD_OUTAGE',
       'Multi-region architecture for tier-1 services; runbook for failover.',
       2, 4, 8, 'EFFECTIVE', 4,
       'Annual failover drill; expand multi-region to tier-2.', 'MITIGATED'),

      (1, @cycle2026,
       'STC fibre cut between HQ and DR site',
       N'انقطاع كابلات الألياف بين المقر وموقع التعافي',
       'Construction work cuts a single STC route.',
       'DR replication lags; RPO breach risk.',
       'Cannot fail over; SAMA OPRES exception.',
       'TELECOM_OUTAGE',
       'Single STC contract — Mobily backup planned.',
       3, 4, 12, 'INEFFECTIVE', 9,
       'Sign Mobily MPLS contract; document failover path.', 'TREATMENT_PLANNED'),

      (1, @cycle2026,
       'Key supplier (clearing-house) failure',
       N'تعثّر مزوّد رئيسي (غرفة المقاصّة)',
       'Supplier financial distress or sanction.',
       'Cannot settle Mada/SARIE transactions.',
       'Customer payments fail; regulatory breach.',
       'SUPPLIER_FAILURE',
       'Annual supplier review; SLA penalty clauses.',
       2, 5, 10, 'NEEDS_IMPROVEMENT', 6,
       'Identify alternate clearing path with SAMA approval.', 'ASSESSED'),

      (1, @cycle2026,
       'Major fire at HQ Riyadh',
       N'حريق كبير في المقر الرئيسي بالرياض',
       'Electrical fault on the 14th floor.',
       'Building evacuation, infrastructure damage.',
       'Loss of HQ-hosted services for 7+ days.',
       'FIRE',
       'Fire suppression, civil-defence approved evacuation plan.',
       1, 5, 5, 'EFFECTIVE', 3,
       'Annual evacuation drill (NCEMA aligned).', 'MITIGATED'),

      (1, @cycle2026,
       'Pandemic — 30%+ workforce unavailability',
       N'جائحة — عدم توفر 30%+ من القوى العاملة',
       'Communicable disease spread.',
       'Sudden mass remote work / sickness.',
       'Service degradation, branch closures.',
       'PANDEMIC',
       'COVID-era remote-work playbook, cross-training matrix.',
       2, 4, 8, 'EFFECTIVE', 4,
       'Refresh playbook annually; test in tabletop.', 'ACCEPTED'),

      (1, @cycle2026,
       'Critical Salesforce CRM outage',
       N'انقطاع نظام Salesforce CRM الحيوي',
       'SaaS provider regional incident.',
       'Customer-service consoles unavailable.',
       'Call-centre productivity falls 60%.',
       'THIRD_PARTY_DEPENDENCY',
       'Vendor SLA 99.9%, status-page subscription.',
       3, 3, 9, 'NEEDS_IMPROVEMENT', 6,
       'Document offline call-handling workaround.', 'TREATMENT_PLANNED'),

      (1, @cycle2026,
       'Loss of treasury-systems specialist team',
       N'فقدان فريق متخصصي أنظمة الخزينة',
       'Resignation cluster; thin succession plan.',
       'Operational keys not transferable in time.',
       'Treasury operations halt for 2-5 days.',
       'WORKFORCE_UNAVAILABILITY',
       'Knowledge-base, partial cross-training.',
       3, 4, 12, 'NEEDS_IMPROVEMENT', 8,
       'Document runbooks; pair-rotation; retention plan.', 'TREATMENT_PLANNED'),

      (1, @cycle2026,
       'Regional flooding cuts off Eastern Province branch',
       N'فيضانات إقليمية تعزل فرع المنطقة الشرقية',
       'Severe weather event.',
       'Branch and ATMs unreachable for 1-3 days.',
       'Customer access disrupted in Dammam/Khobar.',
       'FLOOD',
       'Online + mobile channels remain available.',
       2, 3, 6, 'EFFECTIVE', 3,
       'Pre-position cash; coordinate with Civil Defence.', 'MITIGATED'),

      (1, @cycle2026,
       'Sovereign data residency breach',
       N'انتهاك متطلبات إقامة البيانات السيادية',
       'Misconfigured workload spills to non-KSA region.',
       'PDPL Art. 29 + NCA ECC §3-4 breach.',
       'Regulatory penalty + tenant trust damage.',
       'DATA_LOSS',
       'Quarterly residency audit, IaC policy guardrails.',
       2, 5, 10, 'NEEDS_IMPROVEMENT', 6,
       'Automate residency check in CI; alert on drift.', 'ASSESSED'),

      (1, @cycle2026,
       'Civil-defence regional disruption (Hajj season)',
       N'تعطل إقليمي خلال موسم الحج',
       'Peak-period demand + civil restrictions.',
       'Operational capacity halved in Makkah region.',
       'Service degradation during peak.',
       'REGIONAL_DISRUPTION',
       'Surge capacity plan; coordination with MoH/Civil-Defence.',
       3, 3, 9, 'NEEDS_IMPROVEMENT', 6,
       'Run pre-Hajj tabletop; load-test pilgrim payment channels.', 'TREATMENT_PLANNED');
END
GO

-- ============================================================================
-- 3. Continuity strategies — 8 rows
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.continuity_strategies WHERE organisation_id = 1)
BEGIN
    DECLARE @cycle BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.continuity_strategies
      (organisation_id, cycle_id, rto_requirement_hours, rpo_requirement_hours,
       selected_strategy, strategy_description_en, strategy_description_ar,
       cost_estimate, feasibility, residual_risk,
       required_resources, dependencies,
       approval_status, implementation_status)
    VALUES
      (1, @cycle, 4, 1,
       'ACTIVE_ACTIVE',
       'Active-active deployment of core-banking across Riyadh + Jeddah AZs.',
       N'نشر active-active لخدمات البنوك الأساسية بين مناطق الرياض وجدّة.',
       4500000.00, 'HIGH', 'LOW',
       'Cross-region replication, 2x compute, multi-master DB.',
       'STC/Mobily MPLS link, hyperscaler AZ in Jeddah.',
       'APPROVED', 'IN_PROGRESS'),

      (1, @cycle, 8, 4,
       'ACTIVE_PASSIVE',
       'Warm DR site for treasury & settlements with 4h failover SLA.',
       N'موقع تعافٍ دافئ للخزينة والتسويات بفترة تحوّل 4 ساعات.',
       1800000.00, 'HIGH', 'MEDIUM',
       'Mirrored servers, daily snapshots, dedicated IT-recovery owner.',
       'DRP runbook approval, quarterly drill.',
       'APPROVED', 'COMPLETED'),

      (1, @cycle, 24, 8,
       'CLOUD_FAILOVER',
       'Cloud-native failover for digital channels using regional load balancer.',
       N'تحوّل سحابي لقنوات الخدمة الرقمية عبر موزِّع تحميل إقليمي.',
       650000.00, 'HIGH', 'LOW',
       'Hyperscaler footprint, IaC pipelines, DNS health checks.',
       'Multi-region SaaS readiness.',
       'APPROVED', 'COMPLETED'),

      (1, @cycle, 48, 24,
       'MANUAL_WORKAROUND',
       'Branch teller paper-based fallback for cash transactions during digital outage.',
       N'إجراء يدوي ورقي لمعاملات النقد عند انقطاع القنوات الرقمية.',
       80000.00, 'HIGH', 'MEDIUM',
       'Pre-printed receipts, manual ledger, dual-control approval.',
       'Compliance & operations training.',
       'APPROVED', 'COMPLETED'),

      (1, @cycle, 12, 4,
       'ALTERNATE_WORKPLACE',
       'Backup ops centre in Jeddah for HQ workforce displacement.',
       N'مركز عمليات بديل في جدّة عند إخلاء المقر الرئيسي.',
       1200000.00, 'MEDIUM', 'MEDIUM',
       '50-seat office with full network/desktop kit.',
       'Lease contract, ergonomics setup.',
       'UNDER_REVIEW', 'IN_PROGRESS'),

      (1, @cycle, 4, 2,
       'DATA_REPLICATION',
       'Synchronous DB replication for clearing and settlement systems.',
       N'نسخ مزامن لقواعد بيانات المقاصّة والتسوية.',
       950000.00, 'HIGH', 'LOW',
       'Storage array, latency budget, capacity headroom.',
       'STC/Mobily redundant fibre.',
       'APPROVED', 'IN_PROGRESS'),

      (1, @cycle, 72, 24,
       'ALTERNATE_SUPPLIER',
       'Secondary clearing-house arrangement for SARIE rails.',
       N'ترتيب غرفة مقاصّة ثانوية لقنوات SARIE.',
       320000.00, 'MEDIUM', 'HIGH',
       'Bilateral agreement, technical onboarding.',
       'SAMA approval, supplier due-diligence.',
       'DRAFT', 'NOT_STARTED'),

      (1, @cycle, 24, 8,
       'REMOTE_WORK',
       'Full remote-work capability for HQ knowledge workers.',
       N'قدرة عمل عن بُعد لكامل العاملين المعرفيين في المقر.',
       450000.00, 'HIGH', 'LOW',
       'Laptops, VPN/ZTNA, secure printing.',
       'IT-Sec policy, awareness training.',
       'APPROVED', 'COMPLETED');
END
GO

-- ============================================================================
-- 4. BIA assessments — 6 rows (mix of statuses, one SOVEREIGN)
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.bia_assessments WHERE organisation_id = 1)
BEGIN
    DECLARE @cyc BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.bia_assessments
      (organisation_id, cycle_id, bia_code, department, description,
       mtpd_hours, rto_hours, rpo_hours, mbco_description,
       min_staff, min_tech_text, min_facility_text, min_supplier_text,
       manual_workaround_available, manual_workaround_description, peak_periods_text,
       impact_time_bands_json, impact_dimensions_json, overall_criticality,
       approval_status, approved_by, approved_at)
    VALUES
      (1, @cyc, 'BIA-2026-000001', 'Retail Banking',
       'Core-banking transaction processing — debit/credit, balance enquiry, Mada switch.',
       8, 2, 1,
       'Sustain at least 30% of normal transaction volume on core-banking failover.',
       12, 'Active-active core-banking cluster, Mada switch, SARIE gateway.',
       'Riyadh primary DC + Jeddah DR DC.',
       'STC/Mobily MPLS, SAMA Mada, clearing-house gateway.',
       1, 'Branch tellers use paper-based ledger with dual control for cash transactions.',
       'Monthly salary days (25th-30th), Eid pre-holiday week, Hajj season.',
       N'{"0-2h":{"regulatory":4,"financial":4,"operational":5,"reputation":4,"legal_contractual":3,"public_service":3,"national_sovereign":3,"data":3,"cyber":4,"supplier":3},"2-4h":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":4,"public_service":4,"national_sovereign":4,"data":3,"cyber":4,"supplier":4},"4-8h":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":4,"cyber":5,"supplier":4},"8-24h":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":5,"cyber":5,"supplier":5},"1-3d":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":5,"cyber":5,"supplier":5},"3-7d":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":5,"cyber":5,"supplier":5},">7d":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":5,"cyber":5,"supplier":5}}',
       N'{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":5,"national_sovereign":5,"data":5,"cyber":5,"supplier":5}',
       'SOVEREIGN', 'ACTIVE', 'Khalid Al-Mutairi', '2026-02-12 10:30:00'),

      (1, @cyc, 'BIA-2026-000002', 'Treasury',
       'Treasury & dealing-room operations — FX, SAR liquidity, money-market deals.',
       12, 4, 2,
       'Minimum: settle SAMA-mandated repo positions and SAR liquidity calls.',
       6, 'Bloomberg terminals, Murex, treasury OMS, SARIE gateway.',
       'Riyadh dealing room + secondary at HQ floor 12.',
       'Bloomberg, SAMA settlement, broker network.',
       1, 'Manual treasury blotter with dual control + end-of-day reconciliation.',
       'Quarter-end, Ramadan settlement windows.',
       N'{"0-2h":{"regulatory":3,"financial":4,"operational":4,"reputation":3,"legal_contractual":3,"public_service":1,"national_sovereign":2,"data":3,"cyber":3,"supplier":3},"2-4h":{"regulatory":4,"financial":5,"operational":4,"reputation":4,"legal_contractual":4,"public_service":1,"national_sovereign":3,"data":3,"cyber":4,"supplier":3},"4-8h":{"regulatory":5,"financial":5,"operational":5,"reputation":4,"legal_contractual":4,"public_service":1,"national_sovereign":3,"data":4,"cyber":4,"supplier":4},"8-24h":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":2,"national_sovereign":3,"data":4,"cyber":5,"supplier":4}}',
       N'{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":2,"national_sovereign":3,"data":4,"cyber":5,"supplier":4}',
       'CRITICAL', 'ACTIVE', 'Khalid Al-Mutairi', '2026-02-12 11:00:00'),

      (1, @cyc, 'BIA-2026-000003', 'Cards & Payments',
       'Mada/Visa/Mastercard issuing & acquiring — ATM, POS, e-commerce gateway.',
       8, 4, 2,
       'Authorise critical-value cards (e.g. fuel, healthcare) at minimum.',
       8, 'Issuing host, acquiring host, HSM cluster, Mada switch.',
       'Riyadh primary + Jeddah DR (active-passive).',
       'Mada, Visa/Mastercard schemes, Thales HSMs.',
       0, NULL,
       'Friday evenings, Eid week, salary days.',
       N'{"0-2h":{"regulatory":3,"financial":4,"operational":4,"reputation":4,"legal_contractual":3,"public_service":2,"national_sovereign":2,"data":3,"cyber":4,"supplier":3},"2-4h":{"regulatory":4,"financial":5,"operational":5,"reputation":5,"legal_contractual":4,"public_service":3,"national_sovereign":3,"data":3,"cyber":4,"supplier":4},"4-8h":{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":3,"national_sovereign":3,"data":4,"cyber":5,"supplier":4}}',
       N'{"regulatory":5,"financial":5,"operational":5,"reputation":5,"legal_contractual":5,"public_service":3,"national_sovereign":3,"data":4,"cyber":5,"supplier":4}',
       'CRITICAL', 'APPROVED', 'Khalid Al-Mutairi', '2026-02-15 14:00:00'),

      (1, @cyc, 'BIA-2026-000004', 'Digital Channels',
       'Mobile banking app, internet banking, USSD, chat-bot, WhatsApp banking.',
       24, 8, 4,
       'Sustain enquiry & low-value transfer flow; defer high-value to assisted channels.',
       10, 'App backend, API gateway, web servers, identity provider.',
       'Cloud (Riyadh + Jeddah AZs).',
       'Push-notification provider, SMS gateway, hyperscaler.',
       1, 'Direct customers to branch + ATM channels via in-app banner.',
       'Salary days, peak shopping (Eid + Black Friday).',
       N'{"0-2h":{"regulatory":2,"financial":3,"operational":4,"reputation":4,"legal_contractual":2,"public_service":3,"national_sovereign":2,"data":2,"cyber":3,"supplier":2},"2-4h":{"regulatory":3,"financial":4,"operational":4,"reputation":5,"legal_contractual":3,"public_service":3,"national_sovereign":2,"data":2,"cyber":4,"supplier":3},"4-8h":{"regulatory":3,"financial":4,"operational":5,"reputation":5,"legal_contractual":3,"public_service":3,"national_sovereign":2,"data":3,"cyber":4,"supplier":3}}',
       N'{"regulatory":3,"financial":4,"operational":5,"reputation":5,"legal_contractual":3,"public_service":3,"national_sovereign":2,"data":3,"cyber":4,"supplier":3}',
       'HIGH', 'UNDER_REVIEW', NULL, NULL),

      (1, @cyc, 'BIA-2026-000005', 'Customer Service',
       'Contact-centre + complaints handling (24/7).',
       48, 12, 8,
       'Inbound calls answered with reduced staff; complaints triaged offline.',
       30, 'IVR, ACD, CRM (Salesforce), recording platform.',
       'Riyadh + Khobar contact-centre.',
       'Salesforce, telco provider.',
       1, 'Email + chat self-service routed to backup queue.',
       'Salary days, customer-service week.',
       N'{"0-2h":{"regulatory":1,"financial":1,"operational":2,"reputation":2,"legal_contractual":1,"public_service":2,"national_sovereign":1,"data":1,"cyber":1,"supplier":1},"2-4h":{"regulatory":1,"financial":2,"operational":3,"reputation":3,"legal_contractual":2,"public_service":3,"national_sovereign":1,"data":1,"cyber":2,"supplier":1}}',
       N'{"regulatory":2,"financial":2,"operational":3,"reputation":3,"legal_contractual":2,"public_service":3,"national_sovereign":1,"data":1,"cyber":2,"supplier":1}',
       'MEDIUM', 'DRAFT', NULL, NULL),

      (1, @cyc, 'BIA-2026-000006', 'HR Operations',
       'Payroll, attendance, GOSI/Muqeem submissions.',
       72, 24, 24,
       'Payroll run can be deferred up to 48h with executive approval.',
       4, 'HRIS, payroll engine, GOSI portal, Muqeem.',
       'Cloud (Riyadh).',
       'Bank payroll partner, Muqeem.',
       1, 'Manual payroll calculation using last-month baseline; reconcile post-recovery.',
       'Month-end payroll cycle.',
       N'{"0-2h":{"regulatory":1,"financial":1,"operational":1,"reputation":1,"legal_contractual":1,"public_service":1,"national_sovereign":1,"data":1,"cyber":1,"supplier":1},"2-4h":{"regulatory":1,"financial":1,"operational":2,"reputation":1,"legal_contractual":1,"public_service":1,"national_sovereign":1,"data":1,"cyber":1,"supplier":1}}',
       N'{"regulatory":2,"financial":2,"operational":2,"reputation":1,"legal_contractual":2,"public_service":1,"national_sovereign":1,"data":1,"cyber":2,"supplier":1}',
       'LOW', 'DRAFT', NULL, NULL);
END
GO

-- ============================================================================
-- 5. BCP / DRP plans — 6 (3 BCP + 3 DRP) plus sections + versions
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.bcp_plans WHERE organisation_id = 1)
BEGIN
    DECLARE @bia1 BIGINT = (SELECT TOP 1 id FROM bcm.bia_assessments WHERE organisation_id = 1 AND bia_code = 'BIA-2026-000001');
    DECLARE @bia2 BIGINT = (SELECT TOP 1 id FROM bcm.bia_assessments WHERE organisation_id = 1 AND bia_code = 'BIA-2026-000002');
    DECLARE @bia3 BIGINT = (SELECT TOP 1 id FROM bcm.bia_assessments WHERE organisation_id = 1 AND bia_code = 'BIA-2026-000003');

    -- 3 BCPs
    INSERT INTO bcm.bcp_plans
      (organisation_id, plan_code, title_en, title_ar, plan_type, source_bia_id,
       rto_hours, rpo_hours, mtpd_hours, response_team_role,
       status, current_version, approved_by, approved_at, published_at,
       last_exercised_at, next_review_date)
    VALUES
      (1, 'BCP-2026-000001', 'Core Banking BCP', N'خطة استمرارية الأعمال — الخدمات المصرفية الأساسية', 'BCP',
       @bia1, 2, 1, 8, 'CORE_BANKING_LEAD',
       'PUBLISHED', 2, 'Khalid Al-Mutairi', '2026-02-20 09:00:00', '2026-02-22 12:00:00',
       '2026-03-15 06:00:00', '2026-08-22'),

      (1, 'BCP-2026-000002', 'Treasury BCP', N'خطة استمرارية الأعمال — الخزينة', 'BCP',
       @bia2, 4, 2, 12, 'TREASURY_LEAD',
       'PUBLISHED', 1, 'Khalid Al-Mutairi', '2026-02-25 09:00:00', '2026-02-26 09:00:00',
       NULL, '2026-08-26'),

      (1, 'BCP-2026-000003', 'Digital Channels BCP', N'خطة استمرارية الأعمال — القنوات الرقمية', 'BCP',
       NULL, 8, 4, 24, 'DIGITAL_LEAD',
       'UNDER_REVIEW', 1, NULL, NULL, NULL, NULL, '2026-09-01');

    -- 3 DRPs
    INSERT INTO bcm.bcp_plans
      (organisation_id, plan_code, title_en, title_ar, plan_type, source_bia_id,
       rto_hours, rpo_hours, mtpd_hours, response_team_role,
       status, current_version, approved_by, approved_at, published_at,
       last_exercised_at, next_review_date)
    VALUES
      (1, 'DRP-2026-000001', 'Core Banking DRP', N'خطة التعافي من الكوارث — الخدمات المصرفية الأساسية', 'DRP',
       @bia1, 2, 1, 8, 'IT_RECOVERY_OWNER',
       'PUBLISHED', 2, 'Sara Al-Anezi', '2026-02-20 11:00:00', '2026-02-22 13:00:00',
       '2026-04-02 02:00:00', '2026-08-22'),

      (1, 'DRP-2026-000002', 'Cards & Payments DRP', N'خطة التعافي — البطاقات والمدفوعات', 'DRP',
       @bia3, 4, 2, 8, 'CARDS_RECOVERY_OWNER',
       'APPROVED', 1, 'Sara Al-Anezi', '2026-03-01 10:00:00', NULL, NULL, '2026-09-01'),

      (1, 'DRP-2026-000003', 'Hyperscaler Failover DRP', N'خطة التعافي — التحوّل بين مزوّدي السحاب', 'DRP',
       NULL, 12, 4, 24, 'CLOUD_OWNER',
       'DRAFT', 1, NULL, NULL, NULL, NULL, '2026-09-30');
END
GO

-- Sections — 12 ISO 22301 §8.4.2 sections per plan
IF NOT EXISTS (SELECT 1 FROM bcm.bcp_sections WHERE plan_id IN (SELECT id FROM bcm.bcp_plans WHERE organisation_id = 1))
BEGIN
    DECLARE plan_cursor CURSOR FAST_FORWARD FOR
        SELECT id FROM bcm.bcp_plans WHERE organisation_id = 1;
    DECLARE @pid BIGINT;
    OPEN plan_cursor;
    FETCH NEXT FROM plan_cursor INTO @pid;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        INSERT INTO bcm.bcp_sections (organisation_id, plan_id, section_key, section_title_en, section_title_ar, section_order, body_en, body_ar, last_edited_by, last_edited_at)
        VALUES
          (1, @pid, 'overview',             'Plan Overview',                  N'نظرة عامة على الخطة',          1, 'Defines the scope, applicability, and recovery objectives covered by this plan. Owners review annually or after a material change.', N'يحدد نطاق الخطة وقابلية تطبيقها وأهداف التعافي. يراجَع المالكون الخطة سنوياً أو بعد أي تغيّر جوهري.', 'Khalid Al-Mutairi', '2026-02-18 09:30:00'),
          (1, @pid, 'scope',                'Scope',                          N'النطاق',                       2, 'Services and processes in scope. Out-of-scope items are listed for transparency.', N'الخدمات والعمليات الخاضعة للخطة. تُعرض البنود خارج النطاق لأغراض الشفافية.', 'Khalid Al-Mutairi', '2026-02-18 10:00:00'),
          (1, @pid, 'roles',                'Roles & Responsibilities',       N'الأدوار والمسؤوليات',          3, 'RACI for plan owner, BCM manager, executive sponsor, recovery owners, communications lead.', N'مصفوفة RACI لمالك الخطة ومدير BCM وراعي الإدارة العليا وأصحاب التعافي وقائد الاتصالات.', 'Khalid Al-Mutairi', '2026-02-18 10:30:00'),
          (1, @pid, 'activation_criteria',  'Activation Criteria',            N'معايير التفعيل',               4, 'Triggers that justify activation (impact thresholds, time-to-RTO breach indicators, regulator-imposed). Includes decision authority and timing.', N'المحفّزات المبرّرة لتفعيل الخطة (عتبات الأثر ومؤشرات اختراق RTO والإلزام التنظيمي)، مع تحديد جهة القرار والتوقيت.', 'Khalid Al-Mutairi', '2026-02-18 11:00:00'),
          (1, @pid, 'recovery_priorities',  'Recovery Priorities',            N'أولويات التعافي',              5, 'Ordered list of services to recover first based on BIA criticality and customer/regulatory impact.', N'قائمة مرتّبة بالخدمات الواجب التعافي لها أولاً استناداً إلى تقييم BIA وأثر العملاء والتنظيم.', 'Khalid Al-Mutairi', '2026-02-18 11:30:00'),
          (1, @pid, 'recovery_steps',       'Recovery Procedures',            N'إجراءات التعافي',             6, '1. Notify primary recovery owner. 2. Trigger failover playbook. 3. Verify data integrity. 4. Reroute traffic. 5. Validate end-user transactions. 6. Stabilise and monitor.', N'1) إشعار المالك الأساسي. 2) تنفيذ سجل التحوّل. 3) التحقق من سلامة البيانات. 4) إعادة توجيه الحركة. 5) التحقق من معاملات المستخدم. 6) التثبيت والمراقبة.', 'Khalid Al-Mutairi', '2026-02-19 09:00:00'),
          (1, @pid, 'comms_plan',           'Communications Plan',            N'خطة الاتصالات',                7, 'Internal: BCM committee + workforce notification via SMS/email. External: customers, regulators (SAMA / NCEMA), key suppliers.', N'داخلياً: لجنة BCM والقوى العاملة عبر الرسائل النصية والبريد. خارجياً: العملاء والجهات التنظيمية (SAMA و NCEMA) والمورّدون الرئيسيون.', 'Khalid Al-Mutairi', '2026-02-19 10:00:00'),
          (1, @pid, 'manual_workaround',    'Manual Workaround',              N'البديل اليدوي',                8, 'Paper-based ledger usage rules. Dual-control sign-off, end-of-day reconciliation, manual cap of SAR 10,000 per transaction.', N'قواعد استخدام السجل الورقي مع توقيع رقابي مزدوج وتسوية نهاية اليوم وحد يدوي قدره 10,000 ريال للمعاملة.', 'Khalid Al-Mutairi', '2026-02-19 10:30:00'),
          (1, @pid, 'resources',            'Resource Requirements',          N'متطلبات الموارد',             9, 'People: minimum 8 staff. Tech: failover cluster + DR site. Facility: Riyadh primary + Jeddah backup. Supplier: STC/Mobily, SAMA Mada gateway.', N'الأفراد: 8 موظفين كحد أدنى. التقنية: تجمّع التحوّل + موقع التعافي. المرافق: الرياض والاحتياطي بجدّة. المورّد: STC/Mobily وبوابة Mada.', 'Khalid Al-Mutairi', '2026-02-19 11:00:00'),
          (1, @pid, 'supplier_escalation',  'Supplier Escalation',            N'تصعيد المورّدين',             10, 'Suppliers and their day-1 contact: STC NOC, Mada operations, hyperscaler enterprise support, HSM vendor.', N'المورّدون وجهات الاتصال في اليوم الأول: مركز عمليات STC وعمليات Mada والدعم المؤسسي للمزوّد السحابي ومزوّد HSM.', 'Khalid Al-Mutairi', '2026-02-19 11:30:00'),
          (1, @pid, 'return_to_normal',     'Return to Normal Operations',    N'العودة إلى التشغيل الطبيعي',    11, 'Checklists: data reconciliation, transaction replay, customer notification, regulator de-brief. Owner sign-off required to formally close activation.', N'قوائم تحقق: تسوية البيانات وإعادة تشغيل المعاملات وإشعار العملاء وإحاطة الجهات التنظيمية. يلزم اعتماد المالك للإغلاق الرسمي للتفعيل.', 'Khalid Al-Mutairi', '2026-02-19 12:00:00'),
          (1, @pid, 'appendices',           'Appendices',                     N'الملاحق',                      12, 'A. Contact list. B. Detailed runbooks. C. Reference architecture diagrams. D. Glossary.', N'أ) قائمة الاتصالات. ب) أدلة التشغيل التفصيلية. ج) رسوم البنية المرجعية. د) المسرد.', 'Khalid Al-Mutairi', '2026-02-19 12:30:00');
        FETCH NEXT FROM plan_cursor INTO @pid;
    END
    CLOSE plan_cursor;
    DEALLOCATE plan_cursor;
END
GO

-- Versions — one snapshot per PUBLISHED plan
IF NOT EXISTS (SELECT 1 FROM bcm.bcp_versions WHERE plan_id IN (SELECT id FROM bcm.bcp_plans WHERE organisation_id = 1))
BEGIN
    INSERT INTO bcm.bcp_versions (plan_id, organisation_id, version_number, body_json, approved_by, approved_at, change_summary)
    SELECT p.id, 1, 1,
           N'{"snapshot":"v1","plan":"' + p.plan_code + N'","sections":12,"capturedAt":"2026-02-20T09:00:00Z"}',
           'Khalid Al-Mutairi', '2026-02-20 09:00:00',
           'Initial approved version (v1). Snapshot of all 12 sections.'
      FROM bcm.bcp_plans p
     WHERE p.organisation_id = 1
       AND p.status IN ('PUBLISHED','APPROVED');

    -- Add v2 snapshot for the two PUBLISHED plans to enable diff demo
    INSERT INTO bcm.bcp_versions (plan_id, organisation_id, version_number, body_json, approved_by, approved_at, change_summary)
    SELECT p.id, 1, 2,
           N'{"snapshot":"v2","plan":"' + p.plan_code + N'","sections":12,"capturedAt":"2026-03-15T18:00:00Z","notes":"recovery_steps tightened after Q1 exercise"}',
           'Khalid Al-Mutairi', '2026-03-15 18:00:00',
           'v2 — recovery_steps section refined after Q1 exercise findings; activation_criteria thresholds lowered.'
      FROM bcm.bcp_plans p
     WHERE p.organisation_id = 1
       AND p.status = 'PUBLISHED';
END
GO

-- ============================================================================
-- 6. Lessons learned — 8 rows across source types
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.lessons_learned WHERE organisation_id = 1)
BEGIN
    DECLARE @cyc2 BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.lessons_learned
      (organisation_id, cycle_id, source_type, source_id,
       title_en, title_ar, description, root_cause, recommendation,
       status, approved_by, approved_at)
    VALUES
      (1, @cyc2, 'EXERCISE', NULL,
       'Q1 core-banking exercise revealed stale DNS TTL slowed failover',
       N'تمرين Q1 لكشف بطء التحوّل بسبب TTL غير محدّث في DNS',
       'During Q1 failover exercise, switchover took 18 minutes instead of the 5-minute target. Root cause: external DNS TTL set to 900s.',
       'Operational hardening missed the DNS TTL on external records.',
       'Reduce TTL to 60s for tier-1 services; document in BCP recovery_steps.',
       'APPROVED', 'Khalid Al-Mutairi', '2026-04-01 14:00:00'),

      (1, @cyc2, 'CRISIS', NULL,
       'February cyber-incident: SOC paging missed weekend escalation',
       N'حادث فبراير السيبراني: فاتت SOC إخطار التصعيد في عطلة نهاية الأسبوع',
       'Detection-to-escalation latency exceeded SLA over a Friday evening.',
       'On-call rotation lacked a backup pager and the primary was offline.',
       'Implement automated backup paging and add weekend on-call dry-run.',
       'APPROVED', 'Khalid Al-Mutairi', '2026-03-12 16:30:00'),

      (1, @cyc2, 'AUDIT', NULL,
       'Internal audit found 3 BIAs without approved RTO/RPO',
       N'مراجعة داخلية: 3 تقييمات BIA دون اعتماد RTO/RPO',
       'Audit sample showed 3 BIAs (HR, Procurement, Treasury Admin) approved without RTO/RPO commitments.',
       'Wizard validation was missing on the legacy form.',
       'New BIA wizard now enforces RTO ≤ MTPD constraint.',
       'CLOSED', 'Sara Al-Anezi', '2026-04-20 11:00:00'),

      (1, @cyc2, 'INCIDENT', NULL,
       'Customer-facing app slow during salary days',
       N'بطء التطبيق للعملاء في أيام صرف الرواتب',
       'App backend autoscaling lag caused 30-second response times on the 25th-27th.',
       'HPA thresholds set for baseline, not peak.',
       'Pre-warm HPA for salary days; document in BCP capacity section.',
       'UNDER_REVIEW', NULL, NULL),

      (1, @cyc2, 'EXERCISE', NULL,
       'Call-tree test: 14% contacts unreachable on first attempt',
       N'اختبار شجرة الاتصال: 14% من جهات الاتصال غير قابلة للوصول من المحاولة الأولى',
       'Quarterly call-tree showed 14% of secondary contacts did not answer within 10 minutes.',
       'Phone numbers outdated; no escalation if primary unreachable.',
       'Quarterly contact-data refresh + auto-fallback to secondary in tooling.',
       'APPROVED', 'Khalid Al-Mutairi', '2026-03-30 09:00:00'),

      (1, @cyc2, 'MANUAL', NULL,
       'BCP printout was outdated at branch during minor outage',
       N'نسخة BCP المطبوعة في الفرع غير محدّثة أثناء انقطاع طفيف',
       'Branch staff used a v1 printout while v2 had been published.',
       'No distribution + acknowledgement tracking for plan updates.',
       'Implement plan distribution + acknowledgement workflow.',
       'DRAFT', NULL, NULL),

      (1, @cyc2, 'CRISIS', NULL,
       'NCEMA notification timing exceeded 60-minute window',
       N'إشعار NCEMA تجاوز نافذة الستين دقيقة',
       'During the February incident, NCEMA was notified at minute 78.',
       'Manual notification process; no auto-trigger when severity ≥ 4.',
       'Wire F8 rule: severity ≥ 4 auto-drafts NCEMA notification + alerts crisis manager.',
       'APPROVED', 'Khalid Al-Mutairi', '2026-03-13 12:00:00'),

      (1, @cyc2, 'AUDIT', NULL,
       'Evidence freshness drift on quarterly-attested controls',
       N'انجراف في حداثة الأدلة للضوابط المُقَرّة فصلياً',
       'Audit sampling found 8% of quarterly evidence > 120 days old.',
       'Attestation campaign not linked to evidence-expiry workflow.',
       'Wire attestation campaign completion to evidence revalidation event.',
       'UNDER_REVIEW', NULL, NULL);
END
GO

-- ============================================================================
-- 7. Gaps — 10 rows across severities
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.gaps WHERE organisation_id = 1)
BEGIN
    DECLARE @cyc3 BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.gaps
      (organisation_id, cycle_id, source, related_record_type, related_record_id,
       title_en, title_ar, description, severity, root_cause, impact,
       owner_id, due_date, status)
    VALUES
      (1, @cyc3, 'BIA', 'BIA', 0,
       'Digital-channels BIA lacks impact-time-band data',
       N'BIA القنوات الرقمية يفتقد بيانات النطاقات الزمنية',
       'BIA-2026-000004 (Digital Channels) is missing time-band entries beyond 4-8h.',
       'MEDIUM',
       'Wizard step 3 not completed.',
       'Cannot derive accurate criticality; readiness score skewed.',
       NULL, '2026-06-30', 'OPEN'),

      (1, @cyc3, 'BCP', 'BCP', 0,
       'Treasury BCP missing manual-workaround section',
       N'BCP الخزينة يفتقر إلى قسم البديل اليدوي',
       'BCP-2026-000002 has a placeholder manual_workaround section.',
       'HIGH',
       'Section drafted but not approved.',
       'SAMA OPRES will flag missing manual fallback.',
       NULL, '2026-06-15', 'ASSIGNED'),

      (1, @cyc3, 'DRP', 'DRP', 0,
       'Hyperscaler failover DRP not approved',
       N'DRP التحوّل بين مزوّدي السحاب غير معتمَد',
       'DRP-2026-000003 is still in DRAFT.',
       'CRITICAL',
       'Owner pending vendor MSA signature.',
       'No documented failover path for cloud workloads.',
       NULL, '2026-07-15', 'IN_PROGRESS'),

      (1, @cyc3, 'EXERCISE', 'EXERCISE', 0,
       'Q1 exercise missed by 2 critical roles',
       N'فاتت اثنان من الأدوار الحرجة تمرين Q1',
       'Treasury & Cards leads were absent; substitutes unavailable.',
       'MEDIUM',
       'Exercise scheduling conflict.',
       'Coverage gap on day-1 of any real activation.',
       NULL, '2026-05-31', 'OPEN'),

      (1, @cyc3, 'CALL_TREE', 'CALL_TREE', 0,
       'Call-tree test failure for 14% of contacts',
       N'فشل اختبار شجرة الاتصال لـ 14% من جهات الاتصال',
       '12 contacts (out of 86) unreachable within SLA.',
       'MEDIUM',
       'Stale phone numbers.',
       'Risk of communication delays during crisis.',
       NULL, '2026-05-15', 'IN_PROGRESS'),

      (1, @cyc3, 'AUDIT', 'AUDIT', 0,
       'Evidence repository freshness gap',
       N'فجوة حداثة مستودع الأدلة',
       '8% of quarterly evidence > 120 days old.',
       'LOW',
       'Attestation not wired to revalidation.',
       'Audit finding risk on Q2 review.',
       NULL, '2026-06-30', 'OPEN'),

      (1, @cyc3, 'RISK', 'BCM_RISK', 0,
       'No ERM linkage for high BCM risks (3 of 12)',
       N'لا يوجد ربط ERM لمخاطر BCM المرتفعة (3 من 12)',
       '3 BCM risks with residual ≥ 9 lack an ERM record.',
       'HIGH',
       'F8 rule BCM-T2 fires but no record-creation handler.',
       'Risk register inconsistency.',
       NULL, '2026-06-20', 'ASSIGNED'),

      (1, @cyc3, 'MANUAL', NULL, NULL,
       'No mass-notification provider contracted',
       N'لا يوجد عقد مع مزوّد للإشعارات الجماعية',
       'Email channel only — SMS/voice still simulated.',
       'HIGH',
       'Vendor selection ongoing.',
       'Crisis comms delayed during real event.',
       NULL, '2026-08-30', 'OPEN'),

      (1, @cyc3, 'BIA', 'BIA', 0,
       'HR Operations BIA criticality questioned by exec sponsor',
       N'مدير BIA لعمليات الموارد البشرية يشكّك في تقييم الأهمية',
       'BIA-2026-000006 marked LOW; sponsor wants MEDIUM.',
       'LOW',
       'Subjective scoring.',
       'May reduce visibility on payroll runs.',
       NULL, '2026-06-30', 'UNDER_REVIEW'),

      (1, @cyc3, 'BCP', 'BCP', 0,
       'Digital Channels BCP lacks approval',
       N'BCP القنوات الرقمية غير معتمد',
       'BCP-2026-000003 status = UNDER_REVIEW for 3+ weeks.',
       'MEDIUM',
       'Pending feedback from digital-channels lead.',
       'Plan not active for the 24/7 digital business.',
       NULL, '2026-06-10', 'OPEN');
END
GO

-- ============================================================================
-- 8. Corrective actions (CAPAs) — 10 linked to gaps
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.corrective_actions WHERE organisation_id = 1)
BEGIN
    DECLARE @cyc4 BIGINT = (SELECT TOP 1 id FROM bcm.assessment_cycles WHERE organisation_id = 1 AND reporting_year = 2026);

    INSERT INTO bcm.corrective_actions
      (organisation_id, cycle_id, gap_id, title_en, title_ar, action_plan,
       priority, due_date, progress_percent, status)
    SELECT 1, @cyc4, g.id,
           'CAPA — ' + g.title_en,
           N'إجراء تصحيحي — ' + g.title_ar,
           'Plan: 1) assign owner, 2) gather evidence, 3) implement fix, 4) verify with reviewer, 5) close.',
           CASE g.severity WHEN 'CRITICAL' THEN 'CRITICAL' WHEN 'HIGH' THEN 'HIGH' WHEN 'MEDIUM' THEN 'MEDIUM' ELSE 'LOW' END,
           DATEADD(day, 30, g.due_date),
           CASE g.status WHEN 'IN_PROGRESS' THEN 50 WHEN 'UNDER_REVIEW' THEN 80 WHEN 'ASSIGNED' THEN 25 ELSE 10 END,
           CASE g.status
                WHEN 'IN_PROGRESS' THEN 'IN_PROGRESS'
                WHEN 'UNDER_REVIEW' THEN 'UNDER_REVIEW'
                WHEN 'ASSIGNED'    THEN 'ASSIGNED'
                ELSE 'OPEN'
           END
      FROM bcm.gaps g
     WHERE g.organisation_id = 1;
END
GO

-- ============================================================================
-- 9. Crisis events — 5 events
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_events WHERE organisation_id = 1 AND event_code LIKE 'CRIS-2026-%')
BEGIN
    INSERT INTO bcm.crisis_events
      (organisation_id, event_code, title_en, title_ar, description,
       severity, trigger_source,
       affected_services_json, affected_processes_json, affected_sites_json,
       affected_suppliers_json, affected_systems_json,
       detection_time, activation_time, start_time, closure_time,
       crisis_team_json, root_cause, status,
       related_bcp_ids_json, related_drp_ids_json,
       personal_data_affected, cyber_related, supplier_involved)
    VALUES
      -- 1. Active major disruption (current)
      (1, 'CRIS-2026-000001',
       'Cards switch degradation — POS approval latency spike',
       N'تدهور أداء سويتش البطاقات — ارتفاع زمن موافقة POS',
       'Mada switch authorisation P95 latency rose from 250ms to 4.2s at 14:30. Backlog growing.',
       3, 'INCIDENT',
       N'["Cards & Payments"]', N'["POS authorisation","ATM authorisation"]',
       N'["Riyadh primary DC"]', N'["Mada","Visa"]', N'["Authorisation host","HSM cluster"]',
       '2026-05-17 14:30:00', '2026-05-17 14:48:00', '2026-05-17 14:48:00', NULL,
       N'[{"userId":1,"role":"Crisis Manager"},{"userId":2,"role":"Cards Lead"},{"userId":3,"role":"Comms Lead"}]',
       NULL, 'ASSESSING',
       N'[]', N'[]',
       0, 0, 0),

      -- 2. Recently closed (severity 4 — Crisis)
      (1, 'CRIS-2026-000002',
       'Ransomware attempt — contained at email gateway',
       N'محاولة فدية — احتُويت عند بوّابة البريد',
       'Mass phishing campaign delivered ransomware payload. EDR contained at 3 endpoints. No encryption.',
       4, 'CYBER',
       N'["Customer Service","HR Operations"]', N'["Email handling"]',
       N'["Riyadh HQ"]', N'[]', N'["Email gateway","EDR cluster"]',
       '2026-02-10 06:15:00', '2026-02-10 06:48:00', '2026-02-10 06:48:00', '2026-02-11 18:00:00',
       N'[{"userId":1,"role":"Crisis Manager"},{"userId":4,"role":"CISO"},{"userId":2,"role":"SOC Lead"}]',
       'Phishing-led credential compromise — contained by EDR before encryption.',
       'CLOSED',
       N'[]', N'[]',
       0, 1, 0),

      -- 3. Historical national-level (severity 5)
      (1, 'CRIS-2026-000003',
       'Regional weather event — Eastern Province branch closures',
       N'حدث جوي إقليمي — إغلاق فروع المنطقة الشرقية',
       'Heavy rain + flash floods cut access to Dammam/Khobar branches for 36h.',
       3, 'EXTERNAL_ALERT',
       N'["Branch banking"]', N'["Branch cash handling"]',
       N'["Dammam branch","Khobar branch"]', N'[]', N'["Branch network"]',
       '2026-01-23 06:00:00', '2026-01-23 06:30:00', '2026-01-23 06:30:00', '2026-01-25 09:00:00',
       N'[{"userId":1,"role":"Crisis Manager"},{"userId":5,"role":"Branch Ops Lead"}]',
       'Severe weather; Civil Defence advisory in effect.',
       'CLOSED',
       N'[]', N'[]',
       0, 0, 0),

      -- 4. Recovered, awaiting closure
      (1, 'CRIS-2026-000004',
       'Hyperscaler Jeddah AZ — partial outage',
       N'مزوّد السحاب جدّة — انقطاع جزئي',
       'Riyadh AZ remained healthy; mobile banking served from primary AZ; mild degradation.',
       2, 'EXTERNAL_ALERT',
       N'["Digital Channels"]', N'["Mobile banking enquiry"]',
       N'["Jeddah AZ"]', N'["Hyperscaler"]', N'["App backend pods"]',
       '2026-04-08 09:11:00', '2026-04-08 09:25:00', '2026-04-08 09:25:00', '2026-04-08 13:40:00',
       N'[{"userId":1,"role":"Crisis Manager"},{"userId":6,"role":"Cloud Lead"}]',
       'Provider AZ-level networking issue; rerouted to Riyadh.',
       'RECOVERED',
       N'[]', N'[]',
       0, 0, 1),

      -- 5. Detected, false alarm
      (1, 'CRIS-2026-000005',
       'Suspected DDoS — turned out to be load test',
       N'اشتباه DDoS — تبيّن أنّه اختبار حمل',
       'Edge WAF triggered alert at 02:18. Investigation found it was a sanctioned load test by app team.',
       1, 'MANUAL',
       N'["Digital Channels"]', N'[]', N'[]', N'[]', N'["WAF"]',
       '2026-04-25 02:18:00', '2026-04-25 02:30:00', '2026-04-25 02:30:00', '2026-04-25 02:55:00',
       N'[{"userId":4,"role":"CISO"}]',
       'Sanctioned load test not communicated to SOC.',
       'CANCELLED',
       N'[]', N'[]',
       0, 0, 0);
END
GO

-- ============================================================================
-- 10. Crisis SitReps — ~12 across events
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_situation_reports WHERE organisation_id = 1)
BEGIN
    DECLARE @e1 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000001');
    DECLARE @e2 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000002');
    DECLARE @e3 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000003');
    DECLARE @e4 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000004');

    INSERT INTO bcm.crisis_situation_reports
      (organisation_id, crisis_event_id, reported_at, summary, status_update, severity_change)
    VALUES
      -- CRIS-001 ongoing
      (1, @e1, '2026-05-17 14:48:00', 'Activation issued. Cards lead engaged. HSM cluster utilisation at 92%.', 'WORSENING', NULL),
      (1, @e1, '2026-05-17 15:30:00', 'P95 latency reduced to 1.8s after restarting HSM-A. Continuing root-cause hunt.', 'STABILISING', NULL),
      (1, @e1, '2026-05-17 16:15:00', 'Vendor engaged; suspected firmware glitch on HSM-A. Mada notified informally.', 'STABILISING', NULL),

      -- CRIS-002 ransomware
      (1, @e2, '2026-02-10 06:48:00', 'EDR contained payload on 3 endpoints. No lateral movement. SOC + IR team engaged.', 'STABILISING', NULL),
      (1, @e2, '2026-02-10 09:30:00', 'Containment confirmed. Email gateway rules tightened. Awareness email queued.', 'RECOVERING', 3),
      (1, @e2, '2026-02-11 09:00:00', 'Forensic snapshots complete. Affected endpoints rebuilt from gold image.', 'RECOVERING', 2),
      (1, @e2, '2026-02-11 17:00:00', 'Lessons-learned workshop scheduled for tomorrow. Closing event.', 'NORMAL', NULL),

      -- CRIS-003 weather
      (1, @e3, '2026-01-23 06:30:00', 'Branches Dammam + Khobar closed for safety. ATMs in safe shelters operational.', 'STABILISING', NULL),
      (1, @e3, '2026-01-24 09:00:00', 'Civil Defence cleared one access route. Limited service from 12:00.', 'RECOVERING', NULL),
      (1, @e3, '2026-01-25 08:00:00', 'Both branches fully reopened. Operations normal.', 'NORMAL', 1),

      -- CRIS-004 hyperscaler
      (1, @e4, '2026-04-08 09:25:00', 'Failover to Riyadh AZ initiated. App still serving via primary region.', 'STABILISING', NULL),
      (1, @e4, '2026-04-08 13:40:00', 'Jeddah AZ restored. Failback completed. Monitoring continues.', 'NORMAL', 1);
END
GO

-- ============================================================================
-- 11. Crisis decisions — ~10
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_decisions WHERE organisation_id = 1)
BEGIN
    DECLARE @ev1 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000001');
    DECLARE @ev2 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000002');
    DECLARE @ev3 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000003');
    DECLARE @ev4 BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000004');

    INSERT INTO bcm.crisis_decisions
      (organisation_id, crisis_event_id, decided_at, decided_by_name,
       decision_text, rationale, action_required, action_due_date)
    VALUES
      (1, @ev1, '2026-05-17 14:55:00', 'Khalid Al-Mutairi',
       'Engage Mada hotline and stand-by HSM vendor.', 'Containment risk if HSM-A continues to degrade.',
       'Open vendor P1 ticket; route to vendor lead.', '2026-05-17'),

      (1, @ev1, '2026-05-17 15:20:00', 'Khalid Al-Mutairi',
       'Hold customer-facing comms; internal-only for now.',
       'Issue is degraded performance, not outage; avoid alarm.',
       'Comms lead to draft holding statement for sign-off.', '2026-05-17'),

      (1, @ev2, '2026-02-10 06:55:00', 'Sara Al-Anezi (CISO)',
       'Disable affected mailbox accounts pending forensics.',
       'Prevent further phishing reply chains.',
       'IAM lead disables 3 accounts; reset on completion.', '2026-02-10'),

      (1, @ev2, '2026-02-10 07:30:00', 'Khalid Al-Mutairi',
       'Notify SAMA informally; no PDPL trigger (no PII exposure).',
       'Maintain trust + transparency without escalating PDPL.',
       'BCM Manager calls SAMA contact; logs reference.', '2026-02-10'),

      (1, @ev2, '2026-02-11 10:00:00', 'CEO',
       'Mandatory phishing awareness refresher for all staff.',
       'Workforce-wide reinforcement after near-miss.',
       'L&D launches campaign; HR tracks completion.', '2026-03-01'),

      (1, @ev3, '2026-01-23 06:45:00', 'Khalid Al-Mutairi',
       'Close affected branches; redirect customers to online + ATMs.',
       'Staff safety; access blocked by flooding.',
       'Branch ops issues SMS to customers.', '2026-01-23'),

      (1, @ev3, '2026-01-24 09:30:00', 'Branch Ops Lead',
       'Reopen with limited services from 12:00 on accessible route.',
       'Partial recovery feasible without safety risk.',
       'Branch teams confirm staffing & cash levels.', '2026-01-24'),

      (1, @ev4, '2026-04-08 09:30:00', 'Cloud Lead',
       'Force failover to Riyadh AZ.', 'Pre-empt further degradation.',
       'Cloud team executes runbook.', '2026-04-08'),

      (1, @ev4, '2026-04-08 13:50:00', 'Cloud Lead',
       'Hold off failback for 1 hour to verify provider RCA.',
       'Avoid flip-flopping.',
       'Continue monitoring; decision review at 14:50.', '2026-04-08'),

      (1, @ev1, '2026-05-17 16:00:00', 'Khalid Al-Mutairi',
       'Activate vendor escalation matrix to executive sponsor.',
       'Issue exceeds tier-2 vendor SLA.',
       'CIO calls vendor account director.', '2026-05-17');
END
GO

-- ============================================================================
-- 12. Crisis regulatory notifications — 6
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.crisis_regulatory_notifications WHERE organisation_id = 1)
BEGIN
    DECLARE @ev1b BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000001');
    DECLARE @ev2b BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000002');
    DECLARE @ev3b BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000003');
    DECLARE @ev4b BIGINT = (SELECT id FROM bcm.crisis_events WHERE event_code = 'CRIS-2026-000004');

    INSERT INTO bcm.crisis_regulatory_notifications
      (organisation_id, crisis_event_id, regulator, notified_at,
       notification_method, reference_number, body_summary,
       acknowledgement_received, acknowledgement_at, acknowledgement_reference)
    VALUES
      (1, @ev1b, 'SAMA',  '2026-05-17 15:30:00', 'PORTAL',
       'SAMA-OPRES-2026-0512',
       'Reporting cards-switch degradation; current P95 latency 1.8s; recovery on-going.',
       0, NULL, NULL),

      (1, @ev2b, 'SAMA',  '2026-02-10 07:35:00', 'PHONE',
       'SAMA-OPRES-2026-0198',
       'Informal notification of contained ransomware attempt; no data exposure.',
       1, '2026-02-10 08:10:00', 'SAMA-ACK-2026-0198'),

      (1, @ev2b, 'NCA',   '2026-02-10 08:00:00', 'EMAIL',
       'NCA-IR-2026-031',
       'Cyber-incident report: phishing-led ransomware, contained, EDR-validated.',
       1, '2026-02-10 09:45:00', 'NCA-ACK-2026-031'),

      (1, @ev3b, 'NCEMA', '2026-01-23 07:00:00', 'PORTAL',
       'NCEMA-2026-0042',
       'Regional weather event closure of 2 branches; staff safe.',
       1, '2026-01-23 09:30:00', 'NCEMA-ACK-2026-0042'),

      (1, @ev3b, 'CIVIL_DEFENCE', '2026-01-23 08:30:00', 'PORTAL',
       'CD-2026-EP-0019',
       'Branch closures aligned to Civil Defence weather advisory.',
       1, '2026-01-23 10:00:00', 'CD-ACK-2026-0019'),

      (1, @ev4b, 'SAMA',  '2026-04-08 12:00:00', 'EMAIL',
       'SAMA-OPRES-2026-0298',
       'Hyperscaler partial outage; rerouted to Riyadh AZ; no customer impact.',
       1, '2026-04-08 13:15:00', 'SAMA-ACK-2026-0298');
END
GO

-- ============================================================================
-- 13. BCM settings — one row per tenant (singleton)
-- ============================================================================
IF NOT EXISTS (SELECT 1 FROM bcm.settings WHERE organisation_id = 1)
BEGIN
    INSERT INTO bcm.settings
      (organisation_id, rto_warning_hours, rto_critical_hours,
       rpo_warning_hours, rpo_critical_hours,
       exercise_frequency_months_default, exercise_frequency_months_critical,
       evidence_expiry_warning_days, capa_overdue_grace_days,
       risk_matrix_likelihood_max, risk_matrix_impact_max,
       approval_matrix_json, notification_rules_json,
       iso_22301_version, sama_opres_version, dga_framework_version, ncema_framework_version,
       regulatory_import_status, regulatory_import_notes)
    VALUES
      (1, 12, 24, 4, 12,
       12, 6,
       60, 7,
       5, 5,
       N'{"BIA":["BCM_MANAGER","EXECUTIVE_SPONSOR"],"BCP":["BCM_MANAGER"],"DRP":["BCM_MANAGER","IT_RECOVERY_OWNER"],"STRATEGY_HIGH_COST":["EXECUTIVE_SPONSOR","CFO"]}',
       N'{"BCM_CALL_TREE_FAILED":[{"role":"BCM_MANAGER","channel":"EMAIL"}],"BCM_DR_TEST_FAILED":[{"role":"IT_RECOVERY_OWNER","channel":"EMAIL"},{"role":"BCM_MANAGER","channel":"EMAIL"}],"BCM_CAPA_OVERDUE":[{"role":"BCM_MANAGER","channel":"EMAIL"}]}',
       'ISO 22301:2019', 'SAMA OPRES 2022', 'DGA Continuity v1.0', 'NCEMA Framework 2024',
       'PLACEHOLDER',
       'Demo configuration. Replace placeholder framework imports with the official XML/CSV before regulator-facing use.');
END
GO

PRINT 'V108 demo seed complete — BCM tables populated for organisation_id = 1.';
GO
