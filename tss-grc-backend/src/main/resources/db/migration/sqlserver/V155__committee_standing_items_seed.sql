-- V155 — Standing agenda items for the 19 committee templates.
--
-- Each committee template has 6-10 recurring agenda items. These auto-
-- populate every meeting's agenda before the secretary even opens the
-- agenda builder — the secretary only has to add ad-hoc items.
--
-- The {source_module} column drives cross-module auto-population: items
-- with source_module='RISK' pre-load the latest risk-register highlights,
-- 'AUDIT' pre-loads open finding stats, 'CYBER' pre-loads SOC dashboard,
-- and so on. NULL source_module = manual item.
--
-- Mandatory-item flag: items marked is_mandatory=1 cannot be removed
-- from the agenda by the secretary — only the chair can authorise the
-- exclusion (e.g. when the financial-results topic isn't relevant because
-- it's a special-purpose meeting).

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

IF NOT EXISTS (SELECT 1 FROM admin.committee_standing_items WHERE template_code = N'CTPL-BOARD')
BEGIN

-- ─── 1. Board of Directors (quarterly + annual off-site) ───────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-BOARD', 1, N'CEO report on business performance', N'تقرير الرئيس التنفيذي عن أداء الأعمال', N'CEO', 30, 1, NULL,
 N'Quarterly business update: revenue, margin, customer metrics, strategic milestones.',
 N'تحديث ربع سنوي للأعمال: الإيرادات، الهامش، مقاييس العملاء، المعالم الاستراتيجية.'),

(N'CTPL-BOARD', 2, N'Quarterly financial results', N'النتائج المالية الربع سنوية', N'CFO', 30, 1, NULL,
 N'Income statement, balance sheet, cash flow, variance vs budget, forecast update.',
 N'قائمة الدخل، الميزانية العمومية، التدفقات النقدية، الانحراف مقابل الميزانية، تحديث التوقعات.'),

(N'CTPL-BOARD', 3, N'Top 10 enterprise risks + appetite review', N'أعلى 10 مخاطر مؤسسية + مراجعة الشهية', N'CRO', 25, 1, N'RISK',
 N'Top-10 risks from the register, KRI breaches, appetite breaches, scenario-analysis highlights.',
 N'أعلى 10 مخاطر من السجل، تجاوزات مؤشرات المخاطر، تجاوزات الشهية، أبرز تحليلات السيناريوهات.'),

(N'CTPL-BOARD', 4, N'Compliance & regulator engagement update', N'تحديث الالتزام والتواصل مع الجهات التنظيمية', N'Chief Compliance Officer', 20, 1, N'COMPLIANCE',
 N'Material regulator correspondence, examination status, compliance KRIs.',
 N'المراسلات التنظيمية الجوهرية، حالة الفحوصات، مؤشرات الالتزام.'),

(N'CTPL-BOARD', 5, N'Cyber-security posture', N'وضع الأمن السيبراني', N'CISO', 20, 1, N'CYBER',
 N'NCA ECC compliance %, P1/P2 incidents, top threats, cyber-insurance status.',
 N'نسبة الامتثال لـ NCA ECC، حوادث P1/P2، أعلى التهديدات، وضع التأمين السيبراني.'),

(N'CTPL-BOARD', 6, N'Strategic initiatives progress', N'تقدم المبادرات الاستراتيجية', N'COO', 25, 1, NULL,
 N'Top 5 strategic initiatives — milestone, budget, risk, change of scope.',
 N'أعلى 5 مبادرات استراتيجية - المعلم، الميزانية، المخاطر، تغيير النطاق.'),

(N'CTPL-BOARD', 7, N'M&A pipeline + capital allocation', N'خط أنابيب الاندماج والاستحواذ + تخصيص رأس المال', N'CFO', 20, 0, NULL,
 N'Active M&A discussions, capital-deployment plan, dividend policy.',
 N'مناقشات الاندماج والاستحواذ النشطة، خطة نشر رأس المال، سياسة توزيع الأرباح.'),

(N'CTPL-BOARD', 8, N'ESG & Saudization status', N'وضع الاستدامة والسعودة', N'Head of Sustainability', 15, 1, NULL,
 N'Carbon, diversity, Saudization band, material disclosures.',
 N'الكربون، التنوع، نطاق السعودة، الإفصاحات الجوهرية.'),

(N'CTPL-BOARD', 9, N'Committee chair reports', N'تقارير رؤساء اللجان', N'Each committee chair', 30, 1, NULL,
 N'Brief from each standing committee chair on material matters from their last cycle.',
 N'إيجاز من رئيس كل لجنة دائمة عن الأمور الجوهرية من دورتها الأخيرة.'),

(N'CTPL-BOARD', 10, N'AOB + executive session', N'أمور أخرى + جلسة تنفيذية', N'Chairman', 15, 1, NULL,
 N'Any other business; executive session without management for independent-director discussion.',
 N'أمور أخرى؛ جلسة تنفيذية دون الإدارة لمناقشة الأعضاء المستقلين.');

-- ─── 2. Audit Committee ────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-AUDIT', 1, N'External auditor update', N'تحديث المراجع الخارجي', N'External Audit Partner', 25, 1, NULL,
 N'External-audit progress, management-letter points, fee status, independence confirmations.',
 N'تقدم المراجعة الخارجية، نقاط خطاب الإدارة، حالة الأتعاب، تأكيدات الاستقلالية.'),

(N'CTPL-AUDIT', 2, N'Internal audit plan & findings', N'خطة المراجعة الداخلية والملاحظات', N'Head of Internal Audit', 30, 1, N'AUDIT',
 N'IA plan progress, top findings by severity, CAPA aging, ratings issued.',
 N'تقدم خطة المراجعة الداخلية، أعلى الملاحظات حسب الخطورة، عمر إجراءات التصحيح، التصنيفات الصادرة.'),

(N'CTPL-AUDIT', 3, N'Financial reporting integrity review', N'مراجعة نزاهة التقارير المالية', N'CFO + External Auditor', 25, 1, NULL,
 N'Critical accounting estimates, judgement areas, related-party transactions, going-concern review.',
 N'التقديرات المحاسبية الحرجة، مجالات الحكم، صفقات الأطراف ذات العلاقة، مراجعة الاستمرارية.'),

(N'CTPL-AUDIT', 4, N'Whistle-blower channel status', N'حالة قناة المبلّغين عن المخالفات', N'Head of Internal Audit', 15, 1, NULL,
 N'Reports received, investigations status, dispositions, no-retaliation confirmations.',
 N'البلاغات المستلمة، حالة التحقيقات، الفصل في الحالات، تأكيدات عدم الانتقام.'),

(N'CTPL-AUDIT', 5, N'NCA ECC + SAMA cyber attestation evidence', N'أدلة اعتماد NCA ECC وضوابط ساما السيبرانية', N'CISO', 20, 1, N'CYBER',
 N'Evidence-pack readiness, control gaps, remediation status before regulator submission.',
 N'جاهزية حزمة الأدلة، فجوات الضوابط، حالة المعالجة قبل التقديم للجهة التنظيمية.'),

(N'CTPL-AUDIT', 6, N'Internal-control deficiency log', N'سجل أوجه القصور في الرقابة الداخلية', N'Head of Internal Control', 20, 1, N'CONTROL',
 N'Material weaknesses, SoD exceptions, ICOFR readiness if applicable.',
 N'نقاط الضعف الجوهرية، استثناءات فصل المهام، جاهزية الرقابة الداخلية على التقارير المالية إن وُجدت.'),

(N'CTPL-AUDIT', 7, N'Regulator examination tracker', N'متتبع الفحوصات التنظيمية', N'Chief Compliance Officer', 15, 1, N'COMPLIANCE',
 N'Active examinations, response deadlines, findings, regulatory correspondence.',
 N'الفحوصات النشطة، المهل النهائية للرد، الملاحظات، المراسلات التنظيمية.'),

(N'CTPL-AUDIT', 8, N'AOB', N'أمور أخرى', N'Chair', 10, 1, NULL,
 N'Any other business; executive session with auditors only.',
 N'أمور أخرى؛ جلسة تنفيذية مع المراجعين فقط.');

-- ─── 3. Risk Committee ─────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-RISK', 1, N'Risk register review (top movers)', N'مراجعة سجل المخاطر (أعلى الحركات)', N'CRO', 25, 1, N'RISK',
 N'Top 10 risks, movers since last meeting, new risks identified, retired risks.',
 N'أعلى 10 مخاطر، الحركات منذ الاجتماع الأخير، مخاطر جديدة، مخاطر أُغلقت.'),

(N'CTPL-RISK', 2, N'KRI dashboard + breach review', N'لوحة مؤشرات المخاطر + مراجعة التجاوزات', N'Risk Manager', 20, 1, N'RISK',
 N'KRI status (green/amber/red), breach analysis, root-cause for amber/red.',
 N'حالة مؤشرات المخاطر (أخضر/كهرماني/أحمر)، تحليل التجاوزات، السبب الجذري للكهرماني/الأحمر.'),

(N'CTPL-RISK', 3, N'Risk acceptances pipeline', N'خط أنابيب قبول المخاطر', N'CRO', 25, 1, N'RISK',
 N'Open acceptance requests within committee authority, ones to recommend to Board.',
 N'طلبات القبول المفتوحة ضمن صلاحية اللجنة، تلك التي يجب التوصية بها للمجلس.'),

(N'CTPL-RISK', 4, N'Scenario analysis & stress testing', N'تحليل السيناريوهات واختبار الضغط', N'CRO', 25, 0, N'RISK',
 N'Outputs of latest scenario run, residual exposures, capital / liquidity impact (banks).',
 N'مخرجات أحدث محاكاة سيناريو، التعرضات المتبقية، الأثر على رأس المال / السيولة (للبنوك).'),

(N'CTPL-RISK', 5, N'Cyber-risk briefing', N'إحاطة المخاطر السيبرانية', N'CISO', 15, 1, N'CYBER',
 N'P1/P2 incidents, top threats, NCA ECC posture, cyber-risk acceptance pipeline.',
 N'حوادث P1/P2، أعلى التهديدات، وضع NCA ECC، خط أنابيب قبول المخاطر السيبرانية.'),

(N'CTPL-RISK', 6, N'Emerging risks scan', N'مسح المخاطر الناشئة', N'CRO + business heads', 15, 1, NULL,
 N'Horizon-scanning output: geopolitical, regulatory, technology, market.',
 N'مخرجات مسح الأفق: جيوسياسية، تنظيمية، تقنية، سوقية.'),

(N'CTPL-RISK', 7, N'Three-lines-of-defence health check', N'فحص صحة خطوط الدفاع الثلاثة', N'Risk Manager', 10, 1, NULL,
 N'Are 1st/2nd/3rd lines functioning as designed; any breakdown of independence.',
 N'هل تعمل الخطوط الأول/الثاني/الثالث كما صُمّمت؛ أي انهيار في الاستقلالية.'),

(N'CTPL-RISK', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL,
 N'Any other business.', N'أمور أخرى.');

-- ─── 4. Nomination & Remuneration ──────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-NOMREM', 1, N'Board skills-gap analysis', N'تحليل فجوة مهارات المجلس', N'Chair', 30, 1, NULL,
 N'Compare current skills matrix vs target; identify gaps to address in next nomination cycle.',
 N'مقارنة مصفوفة المهارات الحالية بالمستهدفة؛ تحديد الفجوات للمعالجة في دورة الترشيح القادمة.'),

(N'CTPL-NOMREM', 2, N'Director independence reassessment', N'إعادة تقييم استقلالية الأعضاء', N'Chair', 20, 1, NULL,
 N'Annual reassessment of independence for each non-executive director per CMA criteria.',
 N'إعادة تقييم سنوية للاستقلالية لكل عضو غير تنفيذي وفق معايير هيئة السوق المالية.'),

(N'CTPL-NOMREM', 3, N'Executive remuneration framework', N'إطار مكافآت التنفيذيين', N'CHRO', 30, 1, NULL,
 N'Review compensation policy, benchmarking, performance metrics, deferred compensation.',
 N'مراجعة سياسة المكافآت، المقارنة المرجعية، مقاييس الأداء، المكافآت المؤجلة.'),

(N'CTPL-NOMREM', 4, N'CEO succession plan', N'خطة خلافة الرئيس التنفيذي', N'Chair', 25, 1, NULL,
 N'Succession candidates, readiness assessment, development plans, emergency-succession plan.',
 N'مرشحو الخلافة، تقييم الجاهزية، خطط التطوير، خطة الخلافة الطارئة.'),

(N'CTPL-NOMREM', 5, N'Director candidate pipeline', N'خط أنابيب مرشحي العضوية', N'Chair + external advisor', 20, 0, NULL,
 N'Active search status, candidate shortlist, due-diligence status.',
 N'حالة البحث النشطة، القائمة المختصرة للمرشحين، حالة العناية الواجبة.'),

(N'CTPL-NOMREM', 6, N'Annual board evaluation results', N'نتائج التقييم السنوي للمجلس', N'Chair', 20, 1, NULL,
 N'Output of board self-evaluation + committee evaluations + chair evaluation.',
 N'مخرجات التقييم الذاتي للمجلس + تقييمات اللجان + تقييم الرئيس.'),

(N'CTPL-NOMREM', 7, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL,
 N'Any other business.', N'أمور أخرى.');

-- ─── 5. Compliance Committee ───────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-COMPLIANCE', 1, N'Regulatory change tracker', N'متتبع التغييرات التنظيمية', N'Chief Compliance Officer', 20, 1, N'COMPLIANCE',
 N'New regulations, amendments, consultation responses, implementation status.',
 N'لوائح جديدة، تعديلات، ردود الاستشارة، حالة التطبيق.'),

(N'CTPL-COMPLIANCE', 2, N'Compliance KRI / KPI dashboard', N'لوحة مؤشرات الالتزام', N'Chief Compliance Officer', 15, 1, N'COMPLIANCE',
 N'Compliance health indicators (training, breaches, escalations, regulator interactions).',
 N'مؤشرات صحة الالتزام (التدريب، الانتهاكات، التصعيدات، التفاعلات التنظيمية).'),

(N'CTPL-COMPLIANCE', 3, N'AML / sanctions monitoring summary', N'ملخص رقابة مكافحة غسل الأموال / العقوبات', N'MLRO', 20, 1, N'AML',
 N'STR / SAFIU stats, sanctions hits, PEP cases, KYC backlog.',
 N'إحصاءات بلاغات الاشتباه / SAFIU، نتائج العقوبات، حالات الأشخاص المعرضين سياسياً، تراكم اعرف عميلك.'),

(N'CTPL-COMPLIANCE', 4, N'Conduct breaches & whistle-blower cases', N'مخالفات السلوك وبلاغات المخالفات', N'Chief Compliance Officer + HR', 15, 1, NULL,
 N'Conduct cases, whistle-blower disclosures, sanctions / dismissals.',
 N'حالات السلوك، إفصاحات المبلّغين، العقوبات / الفصل.'),

(N'CTPL-COMPLIANCE', 5, N'Regulator correspondence', N'المراسلات التنظيمية', N'Chief Compliance Officer', 10, 1, NULL,
 N'Material letters / examinations / queries from SAMA, CMA, NCA, SDAIA.',
 N'الرسائل / الفحوصات / الاستفسارات الجوهرية من ساما، هيئة السوق المالية، الهيئة الوطنية للأمن السيبراني، SDAIA.'),

(N'CTPL-COMPLIANCE', 6, N'Training compliance status', N'حالة الالتزام بالتدريب', N'Chief Compliance Officer + HR', 10, 1, NULL,
 N'Mandatory-training completion rate, overdue staff, refresher cycles.',
 N'معدل إكمال التدريب الإلزامي، الموظفون المتأخرون، دورات التحديث.'),

(N'CTPL-COMPLIANCE', 7, N'Policy review status', N'حالة مراجعة السياسات', N'Chief Compliance Officer', 10, 1, N'POLICY',
 N'Policies due for review, recently approved, overdue.',
 N'السياسات المستحقة للمراجعة، المعتمدة حديثاً، المتأخرة.'),

(N'CTPL-COMPLIANCE', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 6. Cyber Security Committee ───────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-CYBER', 1, N'SOC dashboard + KRI review', N'لوحة مركز عمليات الأمن + مراجعة المؤشرات', N'CISO', 25, 1, N'CYBER',
 N'MTTD, MTTR, event volumes, SLA breaches, top categories.',
 N'متوسط زمن الاكتشاف، متوسط زمن الحل، أحجام الأحداث، انتهاكات اتفاقية مستوى الخدمة، أعلى الفئات.'),

(N'CTPL-CYBER', 2, N'Threat intelligence briefing', N'إحاطة الاستخبارات السيبرانية', N'Threat Intel Lead', 20, 1, N'CYBER',
 N'Saudi-CERT advisories, sector-specific threats, IOCs requiring action.',
 N'تحذيرات المركز السعودي للاستجابة لطوارئ الحاسب، التهديدات القطاعية، المؤشرات التي تتطلب إجراءً.'),

(N'CTPL-CYBER', 3, N'Incident summary (P1/P2)', N'ملخص الحوادث (P1/P2)', N'IR Lead', 20, 1, N'CYBER',
 N'P1/P2 cases since last meeting, root causes, lessons learned, regulator notifications.',
 N'حالات P1/P2 منذ الاجتماع الأخير، الأسباب الجذرية، الدروس المستفادة، إخطارات الجهات التنظيمية.'),

(N'CTPL-CYBER', 4, N'Vulnerability programme status', N'حالة برنامج الثغرات', N'Vulnerability Manager', 15, 1, N'CYBER',
 N'Open critical/high CVEs, SLA aging, remediation pipeline, scanner coverage.',
 N'الثغرات المفتوحة الحرجة / العالية، عمر اتفاقية مستوى الخدمة، خط أنابيب المعالجة، تغطية الماسحات.'),

(N'CTPL-CYBER', 5, N'Pentest & red-team progress', N'تقدم اختبارات الاختراق والفريق الأحمر', N'CISO', 15, 0, N'CYBER',
 N'Active engagements, NCA-certified vendor status, key findings + retest.',
 N'المشاركات النشطة، حالة الموردين المعتمدين من الهيئة الوطنية للأمن السيبراني، الملاحظات الرئيسية + إعادة الاختبار.'),

(N'CTPL-CYBER', 6, N'NCA ECC posture & gap closure', N'وضع NCA ECC وإغلاق الفجوات', N'CISO + Compliance', 15, 1, N'CYBER',
 N'Control-coverage %, open gaps, remediation plans, attestation readiness.',
 N'نسبة تغطية الضوابط، الفجوات المفتوحة، خطط المعالجة، جاهزية الاعتماد.'),

(N'CTPL-CYBER', 7, N'Awareness & phishing metrics', N'مقاييس الوعي والتصيد', N'Security Awareness Lead', 10, 1, N'CYBER',
 N'Campaign pass rates, repeat-offenders, click-through, training completion.',
 N'معدلات نجاح الحملات، المخالفون المتكررون، النقر، إكمال التدريب.'),

(N'CTPL-CYBER', 8, N'Cyber-insurance policy status', N'حالة بوليصة التأمين السيبراني', N'Risk Manager', 10, 0, N'CYBER',
 N'Coverage adequacy, renewal timeline, claims activity.',
 N'كفاية التغطية، الجدول الزمني للتجديد، نشاط المطالبات.'),

(N'CTPL-CYBER', 9, N'Regulator examinations', N'الفحوصات التنظيمية', N'Compliance + CISO', 10, 1, N'COMPLIANCE',
 N'NCA / SAMA cyber examinations, response status, follow-up actions.',
 N'فحوصات الهيئة الوطنية للأمن السيبراني / ساما، حالة الرد، الإجراءات اللاحقة.'),

(N'CTPL-CYBER', 10, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 7. Data Governance Committee ──────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-DATAGOV', 1, N'Data domain stewardship status', N'حالة الإشراف على نطاقات البيانات', N'CDO', 20, 1, NULL,
 N'Stewards per domain, vacancies, escalations, RACI updates.',
 N'المشرفون لكل نطاق، الشواغر، التصعيدات، تحديثات RACI.'),
(N'CTPL-DATAGOV', 2, N'Data-quality KPIs by domain', N'مؤشرات جودة البيانات حسب النطاق', N'CDO + domain owners', 25, 1, NULL,
 N'Completeness, accuracy, timeliness, consistency per critical data element.',
 N'الاكتمال، الدقة، التوقيت، الاتساق لكل عنصر بيانات حرج.'),
(N'CTPL-DATAGOV', 3, N'Master & reference data changes', N'تغييرات البيانات الرئيسية والمرجعية', N'Data Architect', 15, 1, NULL,
 N'Approved/proposed changes, impact analysis, downstream notification.',
 N'التغييرات المعتمدة / المقترحة، تحليل الأثر، الإخطار النهائي.'),
(N'CTPL-DATAGOV', 4, N'Data classification & NDC alignment', N'تصنيف البيانات والتوافق مع NDC', N'CISO + CDO', 15, 1, NULL,
 N'NDC label coverage, mis-classifications corrected, training.',
 N'تغطية تسميات NDC، التصحيحات للتصنيفات الخاطئة، التدريب.'),
(N'CTPL-DATAGOV', 5, N'Cross-border transfer requests', N'طلبات النقل عبر الحدود', N'DPO + CDO', 20, 1, N'PRIVACY',
 N'Open requests, SDAIA approvals required, BCR / contractual clauses status.',
 N'الطلبات المفتوحة، موافقات SDAIA المطلوبة، حالة القواعد الملزمة / الشروط التعاقدية.'),
(N'CTPL-DATAGOV', 6, N'NDMO assessment progress', N'تقدم تقييم مكتب إدارة البيانات الوطني', N'CDO', 15, 1, NULL,
 N'Self-assessment status, evidence package, gap-closure plan.',
 N'حالة التقييم الذاتي، حزمة الأدلة، خطة إغلاق الفجوات.'),
(N'CTPL-DATAGOV', 7, N'DAMA-DMBOK adoption status', N'حالة تبني DAMA-DMBOK', N'CDO', 10, 0, NULL,
 N'Maturity by capability area, planned improvements.',
 N'النضج حسب مجال القدرة، التحسينات المخططة.'),
(N'CTPL-DATAGOV', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 8. Privacy / DPO Committee ────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-PRIVACY', 1, N'DSR (data-subject request) pipeline', N'خط أنابيب طلبات أصحاب البيانات', N'DPO', 20, 1, N'PRIVACY',
 N'Open requests, aging, SLA breaches per PDPL Article 4 (30 days).',
 N'الطلبات المفتوحة، التقادم، انتهاكات SLA وفق المادة 4 من نظام حماية البيانات (30 يوماً).'),
(N'CTPL-PRIVACY', 2, N'Cross-border transfer approvals', N'اعتمادات النقل عبر الحدود', N'DPO + General Counsel', 20, 1, N'PRIVACY',
 N'PDPL Article 29 SDAIA approvals, BCR rollouts, vendor processor reviews.',
 N'موافقات SDAIA وفق المادة 29 من نظام حماية البيانات، نشر القواعد الملزمة، مراجعات معالجي البيانات.'),
(N'CTPL-PRIVACY', 3, N'DPIA review & high-risk processing', N'مراجعة تقييم أثر حماية البيانات والمعالجة عالية الخطورة', N'DPO', 20, 1, N'PRIVACY',
 N'Active DPIAs, mitigations, residual risk acceptance.',
 N'تقييمات أثر حماية البيانات النشطة، التخفيفات، قبول المخاطر المتبقية.'),
(N'CTPL-PRIVACY', 4, N'Consent metrics & RoPA updates', N'مقاييس الموافقة وتحديثات سجل المعالجة', N'DPO + Marketing', 15, 1, N'PRIVACY',
 N'Consent rates, opt-out rates, RoPA changes, processing-purpose drift.',
 N'معدلات الموافقة، معدلات الانسحاب، تغييرات سجل المعالجة، انحراف الغرض من المعالجة.'),
(N'CTPL-PRIVACY', 5, N'Privacy-breach summary', N'ملخص خروقات الخصوصية', N'DPO + CISO', 15, 1, N'PRIVACY',
 N'Open breaches, severity, SDAIA notifications, lessons learned.',
 N'الخروقات المفتوحة، الخطورة، إخطارات SDAIA، الدروس المستفادة.'),
(N'CTPL-PRIVACY', 6, N'Vendor processor compliance', N'الالتزام لمعالجي البيانات الخارجيين', N'DPO + Procurement', 10, 1, NULL,
 N'Processor due-diligence status, DPA renewals, audit findings.',
 N'حالة العناية الواجبة للمعالج، تجديدات اتفاقية معالجة البيانات، ملاحظات المراجعة.'),
(N'CTPL-PRIVACY', 7, N'Awareness & training', N'الوعي والتدريب', N'DPO + HR', 10, 1, NULL,
 N'Mandatory privacy training completion, refresher cycles.',
 N'إكمال التدريب الإلزامي للخصوصية، دورات التحديث.'),
(N'CTPL-PRIVACY', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 9. BCM Steering Committee ─────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-BCM', 1, N'BIA refresh status (critical processes)', N'حالة تحديث تحليل الأثر (العمليات الحرجة)', N'BCM Officer', 20, 1, N'BCM',
 N'BIAs due, refreshed, RTO/RPO changes, process-criticality re-rating.',
 N'تحليلات الأثر المستحقة، المحدّثة، تغييرات RTO/RPO، إعادة تصنيف أهمية العمليات.'),
(N'CTPL-BCM', 2, N'DRP / BCP test results', N'نتائج اختبارات الاستمرارية والتعافي', N'BCM Officer', 25, 1, N'BCM',
 N'Pass/fail outcomes, lessons learned, follow-up actions.',
 N'نتائج النجاح/الفشل، الدروس المستفادة، الإجراءات اللاحقة.'),
(N'CTPL-BCM', 3, N'Crisis-exercise outcomes', N'نتائج تمارين الأزمات', N'BCM Officer', 20, 1, N'BCM',
 N'Tabletop / full-scale exercise results, observation log, AAR.',
 N'نتائج التمارين المكتبية / الكاملة، سجل الملاحظات، المراجعة بعد الحدث.'),
(N'CTPL-BCM', 4, N'Supplier resilience posture', N'وضع صمود الموردين', N'BCM + Procurement', 15, 1, N'TPRM',
 N'Critical-supplier health, recent failures, contingency-supplier readiness.',
 N'صحة الموردين الحرجين، الإخفاقات الأخيرة، جاهزية الموردين البديلين.'),
(N'CTPL-BCM', 5, N'Site readiness & infrastructure', N'جاهزية المواقع والبنية التحتية', N'Facilities Director', 15, 1, NULL,
 N'Primary / DR site status, capacity, recent drills.',
 N'حالة الموقع الرئيسي / الاحتياطي، السعة، التدريبات الأخيرة.'),
(N'CTPL-BCM', 6, N'BCM training & awareness', N'تدريب ووعي استمرارية الأعمال', N'BCM Officer', 10, 1, NULL,
 N'Training completion, role-based exercises, key-person rotation.',
 N'إكمال التدريب، التمارين القائمة على الأدوار، تدوير الأشخاص الرئيسيين.'),
(N'CTPL-BCM', 7, N'Annual exercise calendar', N'تقويم التمارين السنوي', N'BCM Officer', 10, 0, NULL,
 N'Upcoming exercises, scenario plans, scope.',
 N'التمارين القادمة، خطط السيناريوهات، النطاق.'),
(N'CTPL-BCM', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 10. Crisis Management ─────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-CRISIS', 1, N'Active crises status', N'حالة الأزمات النشطة', N'CEO / COO', 30, 1, NULL,
 N'Current crisis status, decisions pending, regulator-notification deadlines.',
 N'حالة الأزمة الحالية، القرارات المعلقة، المهل النهائية لإخطار الجهات التنظيمية.'),
(N'CTPL-CRISIS', 2, N'Crisis-playbook reviews', N'مراجعات أدلة الأزمات', N'BCM Officer', 20, 1, NULL,
 N'Playbook coverage by crisis type, recent updates, gaps.',
 N'تغطية الأدلة حسب نوع الأزمة، التحديثات الأخيرة، الفجوات.'),
(N'CTPL-CRISIS', 3, N'External communications log', N'سجل الاتصالات الخارجية', N'Head of Communications', 15, 1, NULL,
 N'Statements issued, press / social-media response, stakeholder Q&A.',
 N'البيانات الصادرة، الرد الصحفي / على وسائل التواصل، أسئلة وأجوبة أصحاب المصلحة.'),
(N'CTPL-CRISIS', 4, N'Regulator-notification log', N'سجل إخطار الجهات التنظيمية', N'General Counsel', 15, 1, NULL,
 N'Notifications sent, timing vs requirement, follow-up correspondence.',
 N'الإخطارات المرسلة، التوقيت مقابل الشرط، المراسلات اللاحقة.'),
(N'CTPL-CRISIS', 5, N'AAR from recent incidents', N'المراجعة بعد الحدث للحوادث الأخيرة', N'BCM Officer', 15, 1, NULL,
 N'Lessons learned, structural changes, training updates.',
 N'الدروس المستفادة، التغييرات الهيكلية، تحديثات التدريب.'),
(N'CTPL-CRISIS', 6, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 11. AML Committee ─────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-AML', 1, N'STR pipeline & SAFIU submissions', N'خط أنابيب بلاغات الاشتباه وتقديمات SAFIU', N'MLRO', 25, 1, N'AML',
 N'Pending STRs, submitted to SAFIU, feedback received.',
 N'بلاغات الاشتباه المعلقة، المقدمة لـ SAFIU، الملاحظات المستلمة.'),
(N'CTPL-AML', 2, N'Sanctions-screening hits review', N'مراجعة نتائج فحص العقوبات', N'MLRO', 20, 1, N'AML',
 N'True / false positives, OFAC / EU / UN list updates, escalations.',
 N'الإيجابيات الحقيقية / الكاذبة، تحديثات قوائم OFAC / EU / UN، التصعيدات.'),
(N'CTPL-AML', 3, N'PEP review & customer risk re-rating', N'مراجعة الأشخاص المعرضين سياسياً وإعادة تصنيف مخاطر العملاء', N'MLRO', 15, 1, N'AML',
 N'PEP cases, re-rating triggered, KYC remediation status.',
 N'حالات الأشخاص المعرضين سياسياً، إعادة التصنيف المستحقة، حالة معالجة اعرف عميلك.'),
(N'CTPL-AML', 4, N'KYC remediation backlog', N'تراكم معالجة اعرف عميلك', N'Head of Operations', 15, 1, N'AML',
 N'Open KYC items, aging, completion vs target.',
 N'بنود اعرف عميلك المفتوحة، التقادم، الإكمال مقابل المستهدف.'),
(N'CTPL-AML', 5, N'AML training compliance', N'الالتزام بتدريب مكافحة غسل الأموال', N'MLRO + HR', 10, 1, NULL,
 N'Mandatory completion rate, refresher cycles.',
 N'معدل الإكمال الإلزامي، دورات التحديث.'),
(N'CTPL-AML', 6, N'FATF / SAMA typology updates', N'تحديثات FATF / ساما للأنماط', N'MLRO', 10, 1, NULL,
 N'New typologies issued, application to current monitoring.',
 N'أنماط جديدة صادرة، التطبيق على الرقابة الحالية.'),
(N'CTPL-AML', 7, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 12. IT Steering ───────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-IT', 1, N'Project portfolio status (RAG)', N'حالة حافظة المشاريع (أحمر/كهرماني/أخضر)', N'PMO Lead', 25, 1, NULL,
 N'Top projects, RAG status, key milestones, budget variance.',
 N'أعلى المشاريع، الحالة، المعالم الرئيسية، انحراف الميزانية.'),
(N'CTPL-IT', 2, N'IT budget & spend', N'ميزانية وإنفاق تقنية المعلومات', N'CFO + CIO', 15, 1, NULL,
 N'Run / change split, variance, savings, reforecasts.',
 N'تقسيم التشغيل / التغيير، الانحراف، الوفورات، إعادة التوقعات.'),
(N'CTPL-IT', 3, N'Major incidents (P1)', N'الحوادث الكبرى (P1)', N'CIO + IR Lead', 15, 1, N'INCIDENT',
 N'P1 incidents since last meeting, RCA, follow-up.',
 N'حوادث P1 منذ الاجتماع الأخير، تحليل السبب الجذري، المتابعة.'),
(N'CTPL-IT', 4, N'Change-management major changes', N'التغييرات الكبرى لإدارة التغيير', N'Change Manager', 15, 1, N'CHANGE',
 N'CAB approvals, emergency changes, success rate.',
 N'موافقات لجنة الموافقة على التغييرات، التغييرات الطارئة، معدل النجاح.'),
(N'CTPL-IT', 5, N'Cloud / SaaS portfolio', N'حافظة السحابة / SaaS', N'CTO', 15, 1, NULL,
 N'Spend by provider, NCA CCC compliance, vendor lock-in.',
 N'الإنفاق حسب المزود، الامتثال لـ NCA CCC، قفل المورد.'),
(N'CTPL-IT', 6, N'Tech-debt remediation', N'معالجة الدين التقني', N'Chief Architect', 10, 1, NULL,
 N'Top debt items, remediation status, write-off candidates.',
 N'أعلى بنود الدين، حالة المعالجة، مرشحو الشطب.'),
(N'CTPL-IT', 7, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 13. ALCO ──────────────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-ALCO', 1, N'NIM (Net Interest Margin) trend', N'اتجاه هامش الفائدة الصافي', N'Treasurer', 15, 1, NULL,
 N'NIM by segment, drivers of change.', N'هامش الفائدة حسب القطاع، محركات التغيير.'),
(N'CTPL-ALCO', 2, N'LCR & NSFR status', N'حالة LCR و NSFR', N'Treasurer', 20, 1, NULL,
 N'LCR / NSFR ratios, components, regulatory buffer.', N'نسب LCR/NSFR، المكونات، الاحتياطي التنظيمي.'),
(N'CTPL-ALCO', 3, N'Liquidity gap analysis', N'تحليل فجوة السيولة', N'Treasurer', 15, 1, NULL,
 N'Gap by maturity bucket, contingency-funding plan triggers.',
 N'الفجوة حسب فترة الاستحقاق، محفزات خطة التمويل الطارئ.'),
(N'CTPL-ALCO', 4, N'IRR-BB (Interest Rate Risk in the Banking Book)', N'مخاطر معدل الفائدة في دفتر البنك', N'Risk Manager', 15, 1, NULL,
 N'EVE / NII sensitivity, hedge effectiveness.', N'حساسية القيمة الاقتصادية / صافي الدخل، فعالية التحوط.'),
(N'CTPL-ALCO', 5, N'FX exposure', N'التعرض لسعر الصرف', N'Treasurer', 10, 1, NULL,
 N'Open positions, hedging strategy, P&L impact.',
 N'المراكز المفتوحة، استراتيجية التحوط، الأثر على الأرباح والخسائر.'),
(N'CTPL-ALCO', 6, N'Concentration risk', N'مخاطر التركز', N'Risk Manager', 10, 1, NULL,
 N'Single-name, sector, geographic, depositor concentration.',
 N'تركز الاسم الواحد، القطاع، الجغرافي، المودعين.'),
(N'CTPL-ALCO', 7, N'Capital ratios & ICAAP/ILAAP status', N'نسب رأس المال وحالة ICAAP/ILAAP', N'CFO + Risk', 15, 1, NULL,
 N'CET1, Tier 1, Total Capital, leverage, ICAAP / ILAAP filing readiness.',
 N'CET1، الشريحة الأولى، إجمالي رأس المال، الرافعة المالية، جاهزية تقديم ICAAP/ILAAP.'),
(N'CTPL-ALCO', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 14. Investment Committee ──────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-INVEST', 1, N'Portfolio performance vs benchmark', N'أداء الحافظة مقابل المعيار', N'CIO-Investment', 25, 1, NULL,
 N'Returns, attribution, peer comparison.', N'العوائد، الإسناد، المقارنة مع الأقران.'),
(N'CTPL-INVEST', 2, N'New mandate approvals', N'اعتمادات التفويض الجديدة', N'CIO-Investment', 20, 1, NULL,
 N'Pending mandates, scoring, recommended action.',
 N'التفويضات المعلقة، التقييم، الإجراء الموصى به.'),
(N'CTPL-INVEST', 3, N'Concentration & limit monitoring', N'رقابة التركز والحدود', N'Risk Manager', 15, 1, NULL,
 N'Asset / issuer / sector / geographic concentration vs limits.',
 N'التركز حسب الأصل / المُصدر / القطاع / الجغرافي مقابل الحدود.'),
(N'CTPL-INVEST', 4, N'Asset-allocation review', N'مراجعة توزيع الأصول', N'CIO-Investment', 15, 0, NULL,
 N'Tactical vs strategic allocation, rebalancing needs.',
 N'التوزيع التكتيكي مقابل الاستراتيجي، احتياجات إعادة التوازن.'),
(N'CTPL-INVEST', 5, N'Manager performance review', N'مراجعة أداء المديرين', N'CIO-Investment', 15, 1, NULL,
 N'External-manager performance, hire/fire recommendations.',
 N'أداء المديرين الخارجيين، توصيات التعيين / الإنهاء.'),
(N'CTPL-INVEST', 6, N'Stewardship & proxy voting report', N'تقرير الإشراف والتصويت بالوكالة', N'CIO-Investment', 10, 0, NULL,
 N'Voting record, engagement outcomes.', N'سجل التصويت، نتائج التواصل.'),
(N'CTPL-INVEST', 7, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 15. AI Ethics Committee ───────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-AIETHICS', 1, N'AI use-case pipeline', N'خط أنابيب حالات استخدام الذكاء الاصطناعي', N'CAIO', 25, 1, NULL,
 N'New use-cases proposed, risk-scored, gating decisions.',
 N'حالات الاستخدام الجديدة المقترحة، المصنفة حسب المخاطر، قرارات البوابات.'),
(N'CTPL-AIETHICS', 2, N'Model risk inventory', N'جرد مخاطر النماذج', N'Model Risk Lead', 20, 1, NULL,
 N'Active models, validation status, validation findings.',
 N'النماذج النشطة، حالة التحقق، ملاحظات التحقق.'),
(N'CTPL-AIETHICS', 3, N'Bias & fairness reviews', N'مراجعات التحيز والعدالة', N'CAIO + DPO', 20, 1, NULL,
 N'Fairness-metric outcomes, mitigation plans, residual bias.',
 N'نتائج مقاييس العدالة، خطط التخفيف، التحيز المتبقي.'),
(N'CTPL-AIETHICS', 4, N'Explainability assessments', N'تقييمات قابلية التفسير', N'CAIO', 15, 1, NULL,
 N'XAI status for high-risk models, customer-facing explanations.',
 N'حالة XAI للنماذج عالية الخطورة، تفسيرات تواجه العملاء.'),
(N'CTPL-AIETHICS', 5, N'Data lineage for AI training', N'نسب البيانات للتدريب', N'CDO', 15, 1, NULL,
 N'Source datasets, consent status, third-party data licences.',
 N'مجموعات البيانات المصدرية، حالة الموافقة، تراخيص بيانات الجهات الخارجية.'),
(N'CTPL-AIETHICS', 6, N'Vendor AI procurement', N'مشتريات الذكاء الاصطناعي من الموردين', N'Procurement + CAIO', 10, 0, NULL,
 N'New AI vendor due-diligence, contract terms, exit-clauses.',
 N'العناية الواجبة لموردي الذكاء الاصطناعي الجدد، شروط العقد، شروط الخروج.'),
(N'CTPL-AIETHICS', 7, N'SDAIA assessment progress', N'تقدم تقييم SDAIA', N'CAIO + Compliance', 10, 1, NULL,
 N'Assessment status, evidence-pack readiness.', N'حالة التقييم، جاهزية حزمة الأدلة.'),
(N'CTPL-AIETHICS', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 16. Anti-Fraud Committee ──────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-ANTIFRAUD', 1, N'Active fraud cases', N'حالات الاحتيال النشطة', N'Head of Fraud', 25, 1, NULL,
 N'Open cases, investigation status, dispositions.',
 N'الحالات المفتوحة، حالة التحقيق، الفصل في الحالات.'),
(N'CTPL-ANTIFRAUD', 2, N'Loss summary & trends', N'ملخص الخسائر والاتجاهات', N'Head of Fraud', 15, 1, NULL,
 N'Loss by category, vs prior period, root causes.',
 N'الخسائر حسب الفئة، مقارنة بالفترة السابقة، الأسباب الجذرية.'),
(N'CTPL-ANTIFRAUD', 3, N'Whistle-blower cases (fraud-related)', N'بلاغات المخالفات (المتعلقة بالاحتيال)', N'Internal Audit + HR', 10, 1, NULL,
 N'Cases received, investigation status, dispositions, retaliation checks.',
 N'الحالات المستلمة، حالة التحقيق، الفصل في الحالات، فحوصات عدم الانتقام.'),
(N'CTPL-ANTIFRAUD', 4, N'Sanctions & dismissals', N'العقوبات والفصل', N'HR + Head of Fraud', 10, 1, NULL,
 N'Staff sanctions applied, reasons, regulator notifications.',
 N'العقوبات المفروضة على الموظفين، الأسباب، إخطارات الجهات التنظيمية.'),
(N'CTPL-ANTIFRAUD', 5, N'Recoveries', N'التعافي', N'Head of Fraud + Legal', 10, 1, NULL,
 N'Recoveries achieved, ongoing legal action.',
 N'التعافي المحقق، الإجراءات القانونية الجارية.'),
(N'CTPL-ANTIFRAUD', 6, N'Process-gap remediation', N'معالجة فجوات العمليات', N'Head of Fraud + Operations', 15, 1, NULL,
 N'Gaps identified, remediation plans, status.',
 N'الفجوات المحددة، خطط المعالجة، الحالة.'),
(N'CTPL-ANTIFRAUD', 7, N'Anti-fraud training', N'تدريب مكافحة الاحتيال', N'Head of Fraud + HR', 10, 1, NULL,
 N'Training completion, scenario-based exercises.',
 N'إكمال التدريب، التمارين القائمة على السيناريوهات.'),
(N'CTPL-ANTIFRAUD', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 17. Internal Control Committee ────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-INTCTRL', 1, N'Control test results by process', N'نتائج اختبار الضوابط حسب العملية', N'Head of Internal Control', 25, 1, NULL,
 N'Pass / fail by process, sample sizes, exceptions.',
 N'النجاح / الفشل حسب العملية، أحجام العينات، الاستثناءات.'),
(N'CTPL-INTCTRL', 2, N'Deficiency log review', N'مراجعة سجل أوجه القصور', N'Head of Internal Control', 20, 1, NULL,
 N'Open deficiencies, severity, remediation status, aging.',
 N'أوجه القصور المفتوحة، الخطورة، حالة المعالجة، التقادم.'),
(N'CTPL-INTCTRL', 3, N'Management''s control assertion', N'تأكيد الإدارة على الضوابط', N'CFO / business heads', 15, 1, NULL,
 N'Sub-process assertions, evidence sufficiency.',
 N'تأكيدات العمليات الفرعية، كفاية الأدلة.'),
(N'CTPL-INTCTRL', 4, N'Process changes affecting controls', N'تغييرات العمليات التي تؤثر على الضوابط', N'Process owners', 15, 1, NULL,
 N'Recent changes, control re-design, re-testing required.',
 N'التغييرات الأخيرة، إعادة تصميم الضوابط، إعادة الاختبار المطلوبة.'),
(N'CTPL-INTCTRL', 5, N'External auditor findings', N'ملاحظات المراجع الخارجي', N'Head of Internal Control + External Auditor', 15, 1, NULL,
 N'Auditor-identified deficiencies, management responses.',
 N'أوجه القصور التي حددها المراجع، ردود الإدارة.'),
(N'CTPL-INTCTRL', 6, N'ICOFR readiness (if applicable)', N'جاهزية الرقابة الداخلية على التقارير المالية (إن انطبق)', N'CFO + Internal Control', 10, 0, NULL,
 N'Year-end readiness, key-control identification, walkthrough completion.',
 N'جاهزية نهاية السنة، تحديد الضوابط الرئيسية، إكمال السير.'),
(N'CTPL-INTCTRL', 7, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 18. ESG Committee ─────────────────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-ESG', 1, N'Carbon footprint & climate metrics', N'البصمة الكربونية ومقاييس المناخ', N'Head of Sustainability', 20, 1, NULL,
 N'Scope 1/2/3 emissions, energy intensity, reduction targets.',
 N'انبعاثات النطاق 1/2/3، كثافة الطاقة، أهداف التخفيض.'),
(N'CTPL-ESG', 2, N'Diversity & inclusion metrics', N'مقاييس التنوع والشمول', N'CHRO', 15, 1, NULL,
 N'Gender, nationality, age representation; pay-gap analysis.',
 N'تمثيل الجنس، الجنسية، العمر؛ تحليل فجوة الأجور.'),
(N'CTPL-ESG', 3, N'Saudization (Nitaqat) compliance', N'الالتزام بالسعودة (نطاقات)', N'CHRO', 15, 1, NULL,
 N'Current band, trajectory, regulator interactions.',
 N'النطاق الحالي، المسار، التفاعلات التنظيمية.'),
(N'CTPL-ESG', 4, N'ESG ratings update', N'تحديث تصنيفات الاستدامة', N'Investor Relations', 10, 1, NULL,
 N'Latest scores from major rating agencies, drivers.',
 N'أحدث الدرجات من وكالات التصنيف الكبرى، المحركات.'),
(N'CTPL-ESG', 5, N'Stakeholder engagement', N'تواصل أصحاب المصلحة', N'Investor Relations + Head of Sustainability', 15, 1, NULL,
 N'Investor / community / regulator engagements; material topics.',
 N'تواصل المستثمرين / المجتمع / الجهات التنظيمية؛ الموضوعات الجوهرية.'),
(N'CTPL-ESG', 6, N'Reporting framework progress (GRI/SASB)', N'تقدم إطار التقارير (GRI/SASB)', N'Head of Sustainability', 15, 1, NULL,
 N'Reporting-framework adoption progress, gaps.',
 N'تقدم تبني إطار التقارير، الفجوات.'),
(N'CTPL-ESG', 7, N'Climate scenario analysis', N'تحليل سيناريوهات المناخ', N'Head of Sustainability + CRO', 15, 0, NULL,
 N'Physical / transition-risk scenarios, business-strategy implications.',
 N'سيناريوهات المخاطر الفيزيائية / الانتقالية، آثار استراتيجية العمل.'),
(N'CTPL-ESG', 8, N'AOB', N'أمور أخرى', N'Chair', 5, 1, NULL, N'Any other business.', N'أمور أخرى.');

-- ─── 19. Architecture Review Board ─────────────────────────────────────
INSERT INTO admin.committee_standing_items (template_code, seq, topic_en, topic_ar, presenter_role, minutes_allocated, is_mandatory, source_module, description_en, description_ar) VALUES
(N'CTPL-ARB', 1, N'Architecture exception requests', N'طلبات الاستثناء المعماري', N'Chief Architect', 25, 1, NULL,
 N'Deviation requests from established standards, risk assessment, conditions.',
 N'طلبات الانحراف عن المعايير المقررة، تقييم المخاطر، الشروط.'),
(N'CTPL-ARB', 2, N'Major-system design reviews', N'مراجعات تصميم الأنظمة الكبرى', N'Solution architects', 30, 1, NULL,
 N'New system / major change designs, security review, integration patterns.',
 N'تصاميم الأنظمة الجديدة / التغييرات الكبرى، مراجعة الأمن، أنماط التكامل.'),
(N'CTPL-ARB', 3, N'Tech-stack decisions', N'قرارات حزمة التقنية', N'Chief Architect', 15, 1, NULL,
 N'New technology adoption, retirement of legacy, vendor selection.',
 N'تبني تقنية جديدة، تقاعد الأنظمة القديمة، اختيار المورد.'),
(N'CTPL-ARB', 4, N'Cloud / NCA-CCC migration approvals', N'اعتمادات الترحيل السحابي / NCA-CCC', N'Cloud Lead + CISO', 20, 1, NULL,
 N'Cloud migrations, NCA CCC compliance check, data classification.',
 N'الترحيلات السحابية، فحص الامتثال لـ NCA CCC، تصنيف البيانات.'),
(N'CTPL-ARB', 5, N'Integration patterns governance', N'حوكمة أنماط التكامل', N'Integration Architect', 10, 1, NULL,
 N'API standards, message-broker choices, deprecation roadmap.',
 N'معايير API، خيارات وسيط الرسائل، خريطة طريق الإيقاف.'),
(N'CTPL-ARB', 6, N'Standards updates', N'تحديثات المعايير', N'Chief Architect', 10, 1, NULL,
 N'Updates to architecture principles, reference models.',
 N'تحديثات مبادئ المعمارية، النماذج المرجعية.'),
(N'CTPL-ARB', 7, N'AOB', N'أمور أخرى', N'Chair', 10, 1, NULL, N'Any other business.', N'أمور أخرى.');

END
GO

PRINT N'V155 — admin.committee_standing_items seeded with ~150 items across 19 committees.';
GO
