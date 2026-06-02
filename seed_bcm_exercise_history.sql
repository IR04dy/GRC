/* ============================================================================
 * seed_bcm_exercise_history.sql
 *
 * Realistic BCM exercise (test) history for the TSS GRC tenant (org_id = 1) so
 * the BCM dashboard's "exercises conducted" cadence trend has a genuine
 * 12-week series instead of a single same-day spike.
 *
 * Saudi-bank-native scenarios: core-banking (T24) DR failover, SADAD switch,
 * ATM/POS isolation, SAMA-CSF ransomware tabletop, crisis call-tree, etc.
 *
 * Honest data: created_at is the real insertion time (now); executed_date is
 * the date the exercise was actually conducted — which is what the cadence
 * trend buckets by. Idempotent: every row is guarded by IF NOT EXISTS on
 * (organisation_id, exercise_name_en, executed_date). Safe to re-run.
 *
 * Constraints respected:
 *   exercise_type ∈ {TECHNICAL_RECOVERY, COMMUNICATION, FULL, FUNCTIONAL, TABLETOP}
 *   status        ∈ {SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED}
 *   score         ∈ [0,100] or NULL
 * plan_id / scenario_id left NULL (both FK columns are nullable).
 * ==========================================================================*/
SET NOCOUNT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

DECLARE @org BIGINT = 1;

/* (name_en, name_ar, type, scenario_en, executed_date, score, findings, capa, facilitator) */
DECLARE @ex TABLE (
    name_en       NVARCHAR(200),
    name_ar       NVARCHAR(200),
    ex_type       NVARCHAR(40),
    scenario      NVARCHAR(400),
    executed_date DATE,
    score         INT,
    findings      NVARCHAR(400),
    capa_count    INT,
    facilitator   NVARCHAR(120)
);

INSERT INTO @ex VALUES
-- Baseline (just before the 12-week window) — keeps the trend starting above 0
(N'Pandemic continuity tabletop',                      N'تمرين نظري لاستمرارية الأعمال خلال جائحة',
   N'TABLETOP',          N'Wide-area staff unavailability; 40% workforce remote for 3 weeks.',
   '2026-02-26', 80, N'Remote VPN concurrency ceiling hit at 65% load.', 2, N'Faisal Al-Harbi'),

-- In-window: roughly one test every 1–2 weeks across Mar–May
(N'Core banking DR failover — T24 to Riyadh DR site',   N'تجربة التحول إلى موقع التعافي للنظام المصرفي الأساسي',
   N'TECHNICAL_RECOVERY', N'Primary T24 cluster lost; controlled failover to the Riyadh DR data centre.',
   '2026-03-19', 85, N'RTO met (52 min vs 60 min target); one batch job needed manual restart.', 1, N'Maha Al-Otaibi'),
(N'Crisis call-tree & staff notification test',          N'اختبار شجرة الاتصال وإشعار الموظفين في الأزمات',
   N'COMMUNICATION',     N'Out-of-hours mass notification via SMS + IVR to 1,200 staff.',
   '2026-04-02', 90, N'8% of mobile numbers stale in the HR directory.', 1, N'Saud Al-Qahtani'),
(N'Ransomware response tabletop (SAMA CSF aligned)',     N'تمرين نظري للاستجابة لبرامج الفدية وفق إطار ساما',
   N'TABLETOP',          N'Encryption of a file-share tier; decision-making on isolation vs ransom.',
   '2026-04-09', 88, N'Legal escalation path to SAMA not time-bound in the playbook.', 2, N'Reem Al-Dossari'),
(N'SADAD payment switch failover drill',                 N'تجربة تحويل مفتاح مدفوعات سداد',
   N'FUNCTIONAL',        N'Active payment switch node failure during peak SADAD settlement window.',
   '2026-04-16', 76, N'Reconciliation lag of 11 min on cut-over; below tolerance.', 3, N'Maha Al-Otaibi'),
(N'Data-centre power loss & generator cut-over',         N'فقدان التغذية الكهربائية وتشغيل المولدات في مركز البيانات',
   N'TECHNICAL_RECOVERY', N'Utility power loss; UPS bridge then diesel generator cut-over.',
   '2026-04-23', 82, N'Generator B reached load 9s slower than design spec.', 1, N'Faisal Al-Harbi'),
(N'Third-party cloud outage tabletop',                   N'تمرين نظري لانقطاع مزود الحوسبة السحابية',
   N'TABLETOP',          N'Regional outage of the primary IaaS provider hosting the mobile channel.',
   '2026-05-07', 84, N'No contracted RTO with the cloud provider for the mobile tier.', 2, N'Reem Al-Dossari'),
(N'ATM/POS network isolation & recovery test',           N'اختبار عزل واستعادة شبكة الصرافات ونقاط البيع',
   N'FUNCTIONAL',        N'Suspected card-network compromise; isolate then recover ATM/POS estate.',
   '2026-05-14', 79, N'Branch fallback to offline-PIN not rehearsed by all regions.', 2, N'Saud Al-Qahtani'),
(N'Executive crisis management team activation drill',   N'تمرين تفعيل فريق إدارة الأزمات التنفيذي',
   N'COMMUNICATION',     N'Unannounced activation of the CMT outside business hours.',
   '2026-05-21', 91, N'Quorum reached in 22 min; deputy chair role ambiguity noted.', 1, N'Faisal Al-Harbi'),
(N'Full-scale alternate-site relocation exercise',       N'تمرين شامل للانتقال إلى الموقع البديل',
   N'FULL',              N'HQ inaccessible; relocate critical operations to the alternate work site.',
   '2026-05-28', 87, N'Alternate-site seating short by 12 desks for Treasury.', 2, N'Maha Al-Otaibi');

INSERT INTO bcm.exercises
    (external_id, organisation_id, plan_id, exercise_name_en, exercise_name_ar,
     exercise_type, scenario, scheduled_date, executed_date, score, status,
     findings, capa_count, facilitator, is_deleted, created_at, updated_at, scenario_id)
SELECT NEWID(), @org, NULL, e.name_en, e.name_ar,
       e.ex_type, e.scenario, e.executed_date, e.executed_date, e.score, N'COMPLETED',
       e.findings, e.capa_count, e.facilitator, 0, SYSUTCDATETIME(), SYSUTCDATETIME(), NULL
FROM @ex e
WHERE NOT EXISTS (
    SELECT 1 FROM bcm.exercises x
    WHERE x.organisation_id = @org
      AND x.exercise_name_en = e.name_en
      AND x.executed_date = e.executed_date
);

DECLARE @added INT = @@ROWCOUNT;
PRINT CONCAT('seed_bcm_exercise_history: inserted ', @added, ' exercise(s).');

SELECT COUNT(*) AS completed_exercises_org1
FROM bcm.exercises
WHERE organisation_id = @org AND is_deleted = 0 AND status = 'COMPLETED';
