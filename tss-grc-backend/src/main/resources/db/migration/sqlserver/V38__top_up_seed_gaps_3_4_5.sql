-- V38 — Top-up seed so every status tab on the new pages has rows.
--
-- After V35/V36/V37 the page tables looked thin in a few places:
--   • risk.scenarios            had 0 ARCHIVED rows
--   • scm.exit_plans            had 0 ARCHIVED rows
--   • scm.alternate_suppliers   had only 6 rows across 5 primaries
--
-- This migration fattens each table so every status tab on the
-- Scenario / Resilience / Frameworks pages renders multiple rows.
-- Idempotent: each insert is guarded by NOT EXISTS on external_id /
-- code / framework_code.

SET QUOTED_IDENTIFIER ON;
SET NOCOUNT ON;
GO

-- ═══ 1. Risk scenarios — add ARCHIVED + more variety ════════════════
IF OBJECT_ID(N'risk.scenarios', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM risk.scenarios WHERE scenario_code = N'SCN-DEMO-07')
BEGIN
    INSERT INTO risk.scenarios
        (organisation_id, risk_id, risk_code, risk_title_en, scenario_code,
         title_en, title_ar, description_en, description_ar,
         scenario_type, horizon_months, currency,
         input_json, result_json,
         expected_loss, p95_loss, p99_loss,
         assumptions_en, assumptions_ar,
         methodology_notes_en, methodology_notes_ar,
         status, approved_at, approved_by, approver_name,
         archived_at, archived_by, archive_reason,
         external_id)
    VALUES
        -- 7. ARCHIVED (superseded by SCN-DEMO-01)
        (1, 4, N'RSK-2026-004', N'Aramco contract renewal delay',
         N'SCN-DEMO-07',
         N'Aramco contract delay — Monte Carlo (Q4 2025 model)',
         N'تأخر عقد أرامكو - محاكاة الربع الرابع 2025',
         N'Q4-2025 Monte-Carlo run on the renewal-delay exposure; superseded by SCN-DEMO-01 after the 2026 procurement-cycle data update.',
         N'تشغيل مونت كارلو للربع الرابع 2025 على تعرض تأخير التجديد - تم استبداله بـ SCN-DEMO-01 بعد تحديث بيانات دورة المشتريات 2026.',
         N'MONTE_CARLO', 18, N'SAR',
         N'{"pMin":0.20,"pMode":0.35,"pMax":0.60,"iMin":1800000,"iMode":4200000,"iMax":8500000,"trials":10000}',
         N'{"expectedLoss":1542000,"p95Loss":3480000,"p99Loss":5120000,"stdev":985000,"histogram":[]}',
         1542000.00, 3480000.00, 5120000.00,
         N'Q4-2025 priors — slightly more pessimistic than the 2026 update.',
         N'افتراضات الربع الرابع 2025 - أكثر تشاؤماً قليلاً من تحديث 2026.',
         N'Triangular distribution; seed=42; superseded.',
         N'توزيع مثلث؛ seed=42؛ تم استبداله.',
         N'APPROVED', DATEADD(DAY, -120, SYSUTCDATETIME()), 1, N'admin',
         DATEADD(DAY, -5, SYSUTCDATETIME()), 1, N'Superseded by SCN-DEMO-01 (Q1 2026 model refresh).',
         N'SCN-DEMO-07'),

        -- 8. APPROVED stress test — interest rate shock
        (1, 9, N'RSK-2026-009', N'Variable-rate debt servicing',
         N'SCN-DEMO-08',
         N'SAMA policy rate +200bps stress test',
         N'اختبار إجهاد - رفع سعر فائدة ساما 200 نقطة',
         N'Single-point stress on the variable-rate portion of the syndicated facility assuming SAMA hikes the policy rate by 200bps over a 12-month horizon.',
         N'اختبار إجهاد على الجزء بفائدة متغيرة من التسهيلات المجمعة بافتراض رفع ساما لسعر الفائدة بـ 200 نقطة أساس على مدى 12 شهراً.',
         N'STRESS_TEST', 12, N'SAR',
         N'{"factors":[{"name":"SAMA_policy_rate","shockPct":2}],"baseExposureSar":120000000}',
         N'{"shockedExposureSar":2400000,"deltaSar":2400000,"breachesAppetite":false,"appetiteCeilingSar":3000000}',
         2400000.00, 2400000.00, 2400000.00,
         N'Variable-rate portion = 60% of facility. Hedging horizon 90 days.',
         N'الجزء بفائدة متغيرة = 60% من التسهيلات. أفق التحوط 90 يوماً.',
         N'Single deterministic shock; aligned with SAMA 2026 outlook bulletin.',
         N'صدمة حتمية منفردة؛ متوافقة مع نشرة توقعات ساما 2026.',
         N'APPROVED', DATEADD(DAY, -6, SYSUTCDATETIME()), 1, N'admin',
         NULL, NULL, NULL,
         N'SCN-DEMO-08'),

        -- 9. DRAFT what-if — supply chain disruption
        (1, 25, N'RSK-2026-025', N'Red Sea shipping disruption',
         N'SCN-DEMO-09',
         N'Red Sea route closure — what-if (90 days)',
         N'إغلاق طريق البحر الأحمر - افتراضي (90 يوماً)',
         N'Narrative-driven what-if: assume Bab el-Mandeb / Suez transit blocked for 90 days; reroute via Cape of Good Hope adds 14 days to lead time, +30% freight cost on imports.',
         N'سيناريو افتراضي وصفي: إغلاق باب المندب وقناة السويس لمدة 90 يوماً؛ إعادة التوجيه عبر رأس الرجاء الصالح يضيف 14 يوماً لزمن التسليم و+30% لتكلفة الشحن على الواردات.',
         N'WHAT_IF', 3, N'SAR',
         N'{"narrative":"Red Sea route closure for 90 days; reroute Cape of Good Hope; +14 days lead time; +30% freight cost.","assumedImpactSar":4800000,"degradationFactor":0.30}',
         N'{"projectedLossSar":1440000,"shippingCostImpact":1100000,"inventoryHoldingImpact":340000,"notes":"Excludes opportunity cost of stock-outs."}',
         1440000.00, 1440000.00, 1440000.00,
         N'Red Sea closure of 90 days; reroute capacity available; no second supplier within Gulf.',
         N'إغلاق البحر الأحمر 90 يوماً؛ سعة إعادة التوجيه متاحة؛ لا مورد ثانوي في الخليج.',
         N'Narrative-driven; lead-time impact from 2024 Houthi-incident data.',
         N'وصفي؛ أثر زمن التسليم مستمد من بيانات حوادث الحوثيين 2024.',
         N'DRAFT', NULL, NULL, NULL,
         NULL, NULL, NULL,
         N'SCN-DEMO-09'),

        -- 10. ARCHIVED what-if (older study)
        (1, 12, N'RSK-2026-012', N'Riyadh datacentre power outage',
         N'SCN-DEMO-10',
         N'Datacentre outage — what-if (pre-Jeddah-DR)',
         N'انقطاع مركز البيانات - افتراضي (قبل تعافي جدة)',
         N'Pre-Jeddah-DR what-if from Q2 2025; archived after the Jeddah DR site went live and the SCN-DEMO-03 refresh.',
         N'سيناريو افتراضي ما قبل التعافي بجدة من الربع الثاني 2025؛ تمت أرشفته بعد تشغيل موقع التعافي في جدة وتحديث SCN-DEMO-03.',
         N'WHAT_IF', 1, N'SAR',
         N'{"narrative":"Riyadh primary OFFLINE 36h; no DR site; cold-recovery from backup ~24h.","assumedImpactSar":3200000,"degradationFactor":1.0}',
         N'{"projectedLossSar":3200000,"notes":"Pre-DR baseline. Superseded by SCN-DEMO-03."}',
         3200000.00, 3200000.00, 3200000.00,
         N'No DR site at the time; cold-recovery from off-site backup tapes.',
         N'لا يوجد موقع تعافي وقتها؛ تعافٍ بارد من نسخ احتياطية خارج الموقع.',
         N'Pre-DR baseline; archived.',
         N'خط أساس ما قبل التعافي؛ مؤرشف.',
         N'ARCHIVED', DATEADD(DAY, -300, SYSUTCDATETIME()), 1, N'admin',
         DATEADD(DAY, -180, SYSUTCDATETIME()), 1, N'Jeddah DR site validated 2026-Q1; superseded by SCN-DEMO-03.',
         N'SCN-DEMO-10'),

        -- 11. APPROVED Monte-Carlo — supplier concentration (refresh)
        (1, 22, N'RSK-2026-022', N'Concentration risk — single managed-services vendor',
         N'SCN-DEMO-11',
         N'Vendor concentration — Monte Carlo (with backup-MOU)',
         N'تركز المورد - مونت كارلو (مع مذكرة تفاهم البديل)',
         N'5-business-day outage Monte-Carlo, refreshed after the Mobily Business backup MOU was signed (alternate-supplier ALT-DEMO-01). Likelihood lowered from 0.12 to 0.07.',
         N'محاكاة انقطاع 5 أيام عمل، محدثة بعد توقيع مذكرة تفاهم البديل (المورد البديل ALT-DEMO-01). الاحتمالية خفضت من 0.12 إلى 0.07.',
         N'MONTE_CARLO', 12, N'SAR',
         N'{"pMin":0.04,"pMode":0.07,"pMax":0.15,"iMin":1500000,"iMode":3200000,"iMax":6500000,"trials":10000}',
         N'{"expectedLoss":231000,"p95Loss":900000,"p99Loss":1320000,"stdev":340000,"histogram":[{"rangeStart":0,"rangeEnd":250000,"count":7100},{"rangeStart":250000,"rangeEnd":500000,"count":1320},{"rangeStart":500000,"rangeEnd":750000,"count":680},{"rangeStart":750000,"rangeEnd":1000000,"count":420},{"rangeStart":1000000,"rangeEnd":1500000,"count":380},{"rangeStart":1500000,"rangeEnd":2000000,"count":100}]}',
         231000.00, 900000.00, 1320000.00,
         N'Mobily MOU signed 2026-02; ADP MENA assessment GAP; assumes Mobily can absorb 60% of primary load within 7 days.',
         N'مذكرة تفاهم Mobily موقعة فبراير 2026؛ تقييم ADP MENA به فجوة؛ يفترض أن Mobily يستوعب 60% من الحمل الأساسي خلال 7 أيام.',
         N'Triangular; refreshed priors after MOU signing.',
         N'مثلث؛ افتراضات أولية محدثة بعد توقيع المذكرة.',
         N'APPROVED', DATEADD(DAY, -1, SYSUTCDATETIME()), 1, N'admin',
         NULL, NULL, NULL,
         N'SCN-DEMO-11');
END
GO

-- ═══ 2. Alternate suppliers — add 4 more across statuses ═════════════
IF OBJECT_ID(N'scm.alternate_suppliers', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM scm.alternate_suppliers WHERE external_id = N'ALT-DEMO-07')
BEGIN
    INSERT INTO scm.alternate_suppliers
        (organisation_id, primary_vendor_id, primary_vendor_name,
         alternate_vendor_name, alternate_vendor_country, alternate_vendor_role,
         service_scope_en, service_scope_ar,
         estimated_onboarding_days, estimated_cost_sar,
         last_assessed_at, last_assessed_by, last_assessed_outcome,
         contractual_status, notes_en, notes_ar,
         status, approved_at, approved_by, approver_name,
         archived_at, archive_reason,
         external_id)
    VALUES
        -- Microsoft 365 → 2 alternates (Google Workspace + Zoho)
        (1, NULL, N'Microsoft 365 (productivity + email)',
         N'Google Workspace Enterprise', N'USA', N'BACKUP_PRIMARY',
         N'Email + collaborative editing + identity. KSA data-residency via Riyadh region (2026).',
         N'بريد إلكتروني + تحرير تعاوني + هوية. إقامة بيانات في المملكة عبر منطقة الرياض (2026).',
         90, 5200000.00,
         DATEADD(DAY, -45, SYSUTCDATETIME()), 2, N'CONDITIONAL',
         N'NONE',
         N'Conditional — KSA data-residency available from 2026-Q3 only; sensitive workloads gated until then.',
         N'مشروط - إقامة البيانات في المملكة متوفرة من الربع الثالث 2026 فقط؛ الأحمال الحساسة مؤجلة حتى ذلك الحين.',
         N'APPROVED', DATEADD(DAY, -40, SYSUTCDATETIME()), 1, N'admin',
         NULL, NULL,
         N'ALT-DEMO-07'),
        (1, NULL, N'Microsoft 365 (productivity + email)',
         N'Zoho Workplace', N'IND', N'BACKUP_SECONDARY',
         N'Productivity + mail fallback for SMB tenants; cost-efficient secondary option.',
         N'إنتاجية + بديل بريد للمستأجرين الصغار؛ خيار ثانوي اقتصادي.',
         60, 1800000.00,
         DATEADD(DAY, -120, SYSUTCDATETIME()), 2, N'READY',
         N'NONE',
         N'Ready — pilot tenant validated 2026-01; SLA acceptable for SMB workloads only.',
         N'جاهز - تم التحقق من المستأجر التجريبي يناير 2026؛ اتفاقية مستوى الخدمة مقبولة لأحمال الشركات الصغيرة فقط.',
         N'DRAFT', NULL, NULL, NULL,
         NULL, NULL,
         N'ALT-DEMO-08'),

        -- SAP HANA → 1 alternate (Oracle Fusion Cloud) — ARCHIVED
        (1, NULL, N'SAP HANA (finance ERP)',
         N'Oracle Fusion Cloud ERP', N'USA', N'BACKUP_PRIMARY',
         N'Full ERP fallback — finance, AP, AR, HCM. Investigated 2024; not pursued.',
         N'بديل ERP كامل - مالية، حسابات دفع، حسابات قبض، رأس مال بشري. تم بحثه 2024؛ لم يُتابع.',
         365, 18000000.00,
         DATEADD(DAY, -500, SYSUTCDATETIME()), 2, N'NOT_VIABLE',
         N'NONE',
         N'Not viable — 12-month onboarding window unacceptable; pursued S/4HANA migration instead.',
         N'غير قابل للتطبيق - نافذة تهيئة 12 شهراً غير مقبولة؛ تمت ملاحقة الترحيل إلى S/4HANA بدلاً من ذلك.',
         N'ARCHIVED', NULL, NULL, NULL,
         DATEADD(DAY, -400, SYSUTCDATETIME()), N'Not viable — superseded by S/4HANA migration roadmap.',
         N'ALT-DEMO-09'),

        -- Mimecast → 1 alternate (Proofpoint)
        (1, NULL, N'Mimecast (email security)',
         N'Proofpoint Essentials', N'USA', N'BACKUP_PRIMARY',
         N'Inbound + outbound email security, URL rewriting, attachment sandboxing. KSA POP available.',
         N'أمان البريد الوارد والصادر، إعادة كتابة الروابط، عزل المرفقات. نقطة وجود في المملكة متاحة.',
         30, 950000.00,
         DATEADD(DAY, -20, SYSUTCDATETIME()), 2, N'READY',
         N'MOU_SIGNED',
         N'Ready — MOU signed 2026-04; quarterly fail-over drill scheduled.',
         N'جاهز - مذكرة تفاهم موقعة أبريل 2026؛ تدريب تحويل ربع سنوي مجدول.',
         N'APPROVED', DATEADD(DAY, -15, SYSUTCDATETIME()), 1, N'admin',
         NULL, NULL,
         N'ALT-DEMO-10');
END
GO

-- ═══ 3. Exit plans — add 2 more (ARCHIVED + APPROVED new) ════════════
IF OBJECT_ID(N'scm.exit_plans', N'U') IS NOT NULL
AND NOT EXISTS (SELECT 1 FROM scm.exit_plans WHERE plan_code = N'EXP-DEMO-06')
BEGIN
    INSERT INTO scm.exit_plans
        (organisation_id, vendor_id, vendor_name, contract_code,
         plan_code, title_en, title_ar,
         trigger_conditions, severity_threshold, target_disengagement_days,
         steps_json,
         data_migration_plan_en, data_migration_plan_ar,
         knowledge_transfer_plan_en, knowledge_transfer_plan_ar,
         last_tested_at, last_tested_outcome, next_test_due,
         status, approved_at, approved_by, approver_name,
         archived_at, archive_reason,
         external_id)
    VALUES
        -- ARCHIVED — plan for a vendor we already exited
        (1, NULL, N'Old AV vendor (replaced by Defender ATP)', N'CON-2020-0005',
         N'EXP-DEMO-06',
         N'Legacy AV — planned exit (executed 2024)',
         N'مكافح فيروسات قديم - خروج مخطط (نُفذ 2024)',
         N'END_OF_LIFE',
         N'LOW', 30,
         N'[{"seq":1,"actionEn":"Notify vendor of non-renewal","actionAr":"إبلاغ المورد بعدم التجديد","owner":"Procurement Lead","days":7,"evidenceRequired":true,"doneAt":"2024-09-15T08:00:00Z"},{"seq":2,"actionEn":"Roll out Microsoft Defender for Endpoint","actionAr":"نشر Microsoft Defender for Endpoint","owner":"SecOps Lead","days":21,"evidenceRequired":true,"doneAt":"2024-10-10T16:00:00Z"},{"seq":3,"actionEn":"Decommission legacy agent + final invoice","actionAr":"إيقاف العميل القديم + الفاتورة الأخيرة","owner":"IT Asset Manager","days":30,"evidenceRequired":true,"doneAt":"2024-10-25T10:00:00Z"}]',
         N'Endpoint detection data exported via vendor API; retained 90 days for forensics.',
         N'تم تصدير بيانات اكتشاف نقاط النهاية عبر واجهة المورد؛ يتم الاحتفاظ بها 90 يوماً للتحقيقات الجنائية.',
         N'Runbook archived in Confluence; no live runtime knowledge required.',
         N'كتاب التشغيل محفوظ في Confluence؛ لا تتطلب معرفة وقت تشغيل حية.',
         DATEADD(DAY, -400, SYSUTCDATETIME()), N'PASSED', NULL,
         N'ARCHIVED', DATEADD(DAY, -450, SYSUTCDATETIME()), 1, N'admin',
         DATEADD(DAY, -180, SYSUTCDATETIME()), N'Disengagement executed 2024-10; archived after lessons-learned review.',
         N'EXP-DEMO-06'),

        -- APPROVED — Mimecast (new — recently approved)
        (1, NULL, N'Mimecast (email security)', N'CON-2023-0044',
         N'EXP-DEMO-07',
         N'Mimecast — managed exit plan',
         N'Mimecast - خطة الخروج المُدارة',
         N'BREACH_NCA_ECC,COST_OVERRUN,VENDOR_ACQUISITION',
         N'MEDIUM', 60,
         N'[{"seq":1,"actionEn":"Activate Proofpoint Essentials MOU","actionAr":"تفعيل مذكرة تفاهم Proofpoint Essentials","owner":"SecOps Lead","days":7,"evidenceRequired":true},{"seq":2,"actionEn":"DNS cutover for MX records (50% pilot)","actionAr":"تحويل سجلات MX (تجريبي 50%)","owner":"Network Lead","days":14,"evidenceRequired":true},{"seq":3,"actionEn":"Full MX cutover + Mimecast quarantine retention 30d","actionAr":"تحويل MX كامل + احتفاظ الحجر Mimecast 30 يوماً","owner":"SecOps Lead","days":30,"evidenceRequired":true},{"seq":4,"actionEn":"Final Mimecast tenant close-out + archive export","actionAr":"الإغلاق النهائي لمستأجر Mimecast + تصدير الأرشيف","owner":"SecOps Lead","days":60,"evidenceRequired":true}]',
         N'30-day quarantine retention required (PDPL); archive export via SFTP.',
         N'مطلوب احتفاظ بالحجر 30 يوماً (نظام حماية البيانات الشخصية)؛ تصدير الأرشيف عبر SFTP.',
         N'Proofpoint runbook + 8h knowledge-transfer session; recorded.',
         N'كتاب تشغيل Proofpoint + جلسة نقل معرفة 8 ساعات؛ موثقة.',
         NULL, N'NOT_TESTED', DATEADD(MONTH, 3, CAST(SYSUTCDATETIME() AS DATE)),
         N'APPROVED', DATEADD(DAY, -8, SYSUTCDATETIME()), 1, N'admin',
         NULL, NULL,
         N'EXP-DEMO-07');
END
GO

PRINT N'V38 — top-up: scenarios +5, alternate_suppliers +4, exit_plans +2. Every tab now has rows.';
GO
