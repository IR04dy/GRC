-- V28 — Knowledge Base seed — Sprint 4 §2.9 completion.
--
-- 6 real-content articles + 4 categories. Bilingual EN/AR. Idempotent.
-- These articles correspond to the help-icons we'll wire in follow-ups.

-- ─── Categories ─────────────────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.categories WHERE organisation_id = 1 AND code = 'getting-started')
    INSERT INTO kb.categories (organisation_id, code, name_en, name_ar, sort_order, is_deleted)
    VALUES (1, 'getting-started', 'Getting Started', 'البداية', 10, 0);

IF NOT EXISTS (SELECT 1 FROM kb.categories WHERE organisation_id = 1 AND code = 'risk')
    INSERT INTO kb.categories (organisation_id, code, name_en, name_ar, sort_order, is_deleted)
    VALUES (1, 'risk', 'Risk Management', 'إدارة المخاطر', 20, 0);

IF NOT EXISTS (SELECT 1 FROM kb.categories WHERE organisation_id = 1 AND code = 'compliance')
    INSERT INTO kb.categories (organisation_id, code, name_en, name_ar, sort_order, is_deleted)
    VALUES (1, 'compliance', 'Compliance', 'الامتثال', 30, 0);

IF NOT EXISTS (SELECT 1 FROM kb.categories WHERE organisation_id = 1 AND code = 'privacy')
    INSERT INTO kb.categories (organisation_id, code, name_en, name_ar, sort_order, is_deleted)
    VALUES (1, 'privacy', 'Privacy & PDPL', 'الخصوصية ونظام PDPL', 40, 0);
GO

-- ─── Article 1: Risk severity matrix ───────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'risk-severity-matrix')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-001', 1, 'risk-severity-matrix', 'risk',
         'Risk severity matrix (5x5)',
         'مصفوفة شدة المخاطر (5×5)',
         'How likelihood × impact maps to a residual risk score, and what each band means in practice.',
         'كيف يُحسب التقييم بضرب الاحتمالية في الأثر، وماذا تعني كل فئة عملياً.',
         '## Overview' + CHAR(13)+CHAR(10) +
         'TSS GRC uses a 5×5 matrix where Likelihood (1=Rare, 5=Almost certain) is multiplied by Impact (1=Negligible, 5=Catastrophic) to produce a Residual Risk Score (1-25).' + CHAR(13)+CHAR(10)+CHAR(13)+CHAR(10) +
         '### Bands' + CHAR(13)+CHAR(10) +
         '- **Low (1-4)**: monitor only' + CHAR(13)+CHAR(10) +
         '- **Medium (5-9)**: treatment plan required within 90 days' + CHAR(13)+CHAR(10) +
         '- **High (10-15)**: treatment plan required within 30 days; CRO sign-off' + CHAR(13)+CHAR(10) +
         '- **Critical (16-25)**: immediate escalation; Board notification' + CHAR(13)+CHAR(10),
         '## نظرة عامة' + CHAR(13)+CHAR(10) +
         'تستخدم منصة TSS GRC مصفوفة 5×5 حيث تُضرب الاحتمالية في الأثر للحصول على درجة الخطر المتبقي (1-25).',
         'risk,scoring,methodology', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO

-- ─── Article 2: PDPL DSR 30-day SLA ────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'pdpl-dsr-sla')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-002', 1, 'pdpl-dsr-sla', 'privacy',
         'PDPL Article 4 — DSR 30/60-day SLA',
         'PDPL المادة 4 — مدة استجابة طلبات أصحاب البيانات 30/60 يوم',
         'When the 30-day clock starts, when you can extend to 60 days, and what NAFATH verification looks like.',
         'متى يبدأ احتساب الـ30 يوماً، ومتى يحق التمديد إلى 60 يوماً، وكيف يتم التحقق عبر نفاذ.',
         '## SLA timeline' + CHAR(13)+CHAR(10) +
         'The 30-day clock starts at INTAKE (when the request is filed). It can be extended by 30 days (total 60) when status moves to EXTENDED, citing one of the Article 4(2) grounds.' + CHAR(13)+CHAR(10) + CHAR(13)+CHAR(10) +
         '### Identity verification' + CHAR(13)+CHAR(10) +
         'Use NAFATH for KSA residents. ID_DOCUMENT, IN_PERSON, and VIDEO_KYC are alternatives but require additional approval.',
         '## الجدول الزمني' + CHAR(13)+CHAR(10) +
         'يبدأ احتساب الـ30 يوماً عند الاستلام (INTAKE). يمكن التمديد 30 يوماً إضافياً (60 إجمالاً) بالانتقال إلى الحالة EXTENDED مع ذكر أحد مبررات المادة 4(2).',
         'pdpl,dsr,privacy,sla,nafath', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO

-- ─── Article 3: NCA ECC SLA matrix ─────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'nca-ecc-vuln-sla')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-003', 1, 'nca-ecc-vuln-sla', 'compliance',
         'NCA ECC vulnerability remediation SLAs',
         'مدد معالجة الثغرات وفق NCA ECC',
         'CRITICAL=7d, HIGH=30d, MEDIUM=60d, LOW=90d. Computed automatically when a vulnerability moves to TRIAGED.',
         'حرج=7 أيام، عالي=30 يوماً، متوسط=60 يوماً، منخفض=90 يوماً. تُحسب تلقائياً عند فرز الثغرة.',
         '## SLA matrix (per NCA ECC)' + CHAR(13)+CHAR(10) +
         '| Severity | SLA |' + CHAR(13)+CHAR(10) +
         '|---|---|' + CHAR(13)+CHAR(10) +
         '| CRITICAL | 7 days |' + CHAR(13)+CHAR(10) +
         '| HIGH     | 30 days |' + CHAR(13)+CHAR(10) +
         '| MEDIUM   | 60 days |' + CHAR(13)+CHAR(10) +
         '| LOW      | 90 days |' + CHAR(13)+CHAR(10) + CHAR(13)+CHAR(10) +
         'The deadline is computed when the vulnerability transitions from NEW to TRIAGED. To extend, the CISO must approve a RISK_ACCEPTED transition with a documented expiry date.',
         '## مصفوفة المدد (وفق NCA ECC)' + CHAR(13)+CHAR(10) +
         'تحسب المدة الزمنية عند انتقال الثغرة من جديدة إلى مفروزة.',
         'cyber,nca,ecc,sla,vulnerability', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO

-- ─── Article 4: BCM invocation playbook ────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'bcm-invocation-playbook')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-004', 1, 'bcm-invocation-playbook', 'risk',
         'BCM invocation playbook — when to declare',
         'دليل تفعيل خطة الاستمرارية — متى يُعلَن',
         'Decision tree for declaring a BCM event, escalation path through the BCM Coordinator and Incident Commander, and notification triggers for SAMA and NCEMA.',
         'شجرة القرار لإعلان حدث استمرارية الأعمال، ومسار التصعيد عبر منسق الاستمرارية وقائد الحادثة، ومحفزات إخطار ساما و NCEMA.',
         '## Decision tree' + CHAR(13)+CHAR(10) +
         '1. Is service availability < 50% for any TIER1 service? → declare SEV1' + CHAR(13)+CHAR(10) +
         '2. Is RTO at risk for any TIER2 service? → declare SEV2' + CHAR(13)+CHAR(10) +
         '3. Otherwise → manage as standard incident' + CHAR(13)+CHAR(10) + CHAR(13)+CHAR(10) +
         '### Notification triggers' + CHAR(13)+CHAR(10) +
         '- **SAMA** (financial sector): SEV1 or SEV2 within 4 hours' + CHAR(13)+CHAR(10) +
         '- **NCEMA**: SEV1 only, within 24 hours' + CHAR(13)+CHAR(10),
         '## شجرة القرار' + CHAR(13)+CHAR(10) +
         '1. توفر الخدمة أقل من 50% لأي خدمة من الفئة الأولى؟ → أعلن SEV1' + CHAR(13)+CHAR(10) +
         '2. زمن التعافي مهدد لأي خدمة من الفئة الثانية؟ → أعلن SEV2' + CHAR(13)+CHAR(10),
         'bcm,sama,ncema,playbook', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO

-- ─── Article 5: Approval delegation ────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'approval-delegation')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-005', 1, 'approval-delegation', 'getting-started',
         'Delegating approvals when you''re away',
         'تفويض الموافقات أثناء غيابك',
         'How to set a delegate, who can re-delegate, and how the audit trail captures the chain.',
         'كيف تعيّن مفوضاً، ومن يحق له إعادة التفويض، وكيف يلتقط سجل التدقيق سلسلة التفويض.',
         '## How to delegate' + CHAR(13)+CHAR(10) +
         'On any approval in your inbox, click the menu → Delegate. Pick a colleague and add a reason (mandatory for audit).' + CHAR(13)+CHAR(10) + CHAR(13)+CHAR(10) +
         '### Audit trail' + CHAR(13)+CHAR(10) +
         'The delegated_from field captures the original assignee, and original_assigned_to never changes — so the audit log always shows the chain even after multiple delegations.',
         '## كيف تفوّض' + CHAR(13)+CHAR(10) +
         'في أي طلب موافقة في صندوقك، اضغط القائمة → تفويض. اختر زميلاً وأضف السبب (إلزامي للتدقيق).',
         'approvals,delegation', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO

-- ─── Article 6: Saved views ────────────────────────────────────────────
IF NOT EXISTS (SELECT 1 FROM kb.articles WHERE organisation_id = 1 AND slug = 'saved-views')
    INSERT INTO kb.articles
        (external_id, organisation_id, slug, category_code,
         title_en, title_ar, summary_en, summary_ar, body_en_md, body_ar_md,
         tags, status, visibility, author, published_at, view_count, is_deleted)
    VALUES
        ('KB-2026-006', 1, 'saved-views', 'getting-started',
         'Saved views — your personal lists',
         'العروض المحفوظة — قوائمك الشخصية',
         'Build a filter, save it, set it as default. Views are per-user; share them with your team via the share toggle.',
         'أنشئ فلتراً، احفظه، اجعله الافتراضي. العروض شخصية لكل مستخدم، شاركها مع فريقك عبر زر المشاركة.',
         '## Saving a view' + CHAR(13)+CHAR(10) +
         '1. Build a filter using the **Filter** button (operators, AND/OR groups)' + CHAR(13)+CHAR(10) +
         '2. Sort columns the way you want them' + CHAR(13)+CHAR(10) +
         '3. Click **Views → Save current**' + CHAR(13)+CHAR(10) +
         '4. Star it to make it your default' + CHAR(13)+CHAR(10),
         '## حفظ العرض' + CHAR(13)+CHAR(10) +
         'أنشئ فلتراً من زر التصفية، رتّب الأعمدة، ثم اضغط العروض ← حفظ الحالي.',
         'views,filter,personalisation', 'PUBLISHED', 'INTERNAL', 'admin',
         SYSUTCDATETIME(), 0, 0);
GO
