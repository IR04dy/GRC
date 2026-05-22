-- V157 — Worked-example resolutions + action items seeded against the
-- existing V40 demo meetings.
--
-- The V40 migration shipped 8 demo committee meetings with their decisions
-- and action items as JSON blobs inside committee_meetings. V154 introduced
-- first-class committee_resolutions + committee_action_items tables. This
-- migration migrates the most-useful demo meetings into the new tables so
-- a user opening the platform sees realistic, queryable Saudi-context
-- content the moment they load it — not empty tables.
--
-- We seed:
--   8 resolutions  (3 from MTG-DEMO-01 Board Q1, 2 from MTG-DEMO-02 Risk
--                   April, 2 from MTG-DEMO-03 Audit Q1, 1 from MTG-DEMO-05
--                   Risk March)
--   9 action items (matching the original JSON action items, with proper
--                   linkedModule + linkedEntityCode for cross-module
--                   navigation)
--
-- All bilingual EN/AR — Saudi-native GRC content, not literal back-
-- translation. Resolutions carry their vote breakdown (voteFor /
-- voteAgainst / voteAbstain), dissent notes where relevant, and the
-- regulatorNotify flag for the ones that require CMA / SAMA / SDAIA
-- disclosure.

SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;
GO

DECLARE @org BIGINT = 1;
DECLARE @mtg01 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-01');
DECLARE @mtg02 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-02');
DECLARE @mtg03 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-03');
DECLARE @mtg05 BIGINT = (SELECT id FROM admin.committee_meetings WHERE meeting_code = N'MTG-DEMO-05');

DECLARE @board BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-BOARD');
DECLARE @risk  BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-RISK');
DECLARE @audit BIGINT  = (SELECT id FROM admin.committees WHERE code = N'COM-AUDIT');

IF @mtg01 IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM admin.committee_resolutions WHERE resolution_code = N'RES-2026-Q1-BRD-001')
BEGIN

-- ═════════════════════════════════════════════════════════════════════
-- RESOLUTIONS — 8 worked examples
-- ═════════════════════════════════════════════════════════════════════

INSERT INTO admin.committee_resolutions
    (organisation_id, meeting_id, committee_id, resolution_code, seq, agenda_item_seq,
     title_en, title_ar, body_en, body_ar,
     proposed_by, seconded_by,
     vote_for, vote_against, vote_abstain, outcome, confidentiality,
     effective_from, review_required_by, regulator_notify,
     dissent_notes_en, dissent_notes_ar)
VALUES

-- Board Q1 — Resolution 1: 2026 strategic plan
(@org, @mtg01, @board, N'RES-2026-Q1-BRD-001', 1, 2,
 N'Approve 2026 Strategic Plan with V2030 alignment expansion',
 N'اعتماد الخطة الاستراتيجية 2026 مع توسعة التوافق مع رؤية 2030',
 N'RESOLVED THAT the Board approves the 2026 Strategic Plan as tabled by the CEO, subject to one amendment: the Vision 2030 alignment section is to be expanded to explicitly cover the digital-economy and human-capital pillars per the National Transformation Programme. The amended plan shall be circulated to all directors within 14 days and shall constitute the binding annual plan for the financial year 2026.',
 N'قرر المجلس اعتماد الخطة الاستراتيجية 2026 كما عُرضت من قبل الرئيس التنفيذي، رهناً بتعديل واحد: توسعة قسم التوافق مع رؤية 2030 ليغطي صراحة ركيزتي الاقتصاد الرقمي ورأس المال البشري وفق برنامج التحول الوطني. تُعمَّم الخطة المعدّلة على جميع الأعضاء خلال 14 يوماً، وتشكّل الخطة السنوية الملزمة للسنة المالية 2026.',
 N'Layla Al-Otaibi (Vice-Chair)', N'Mohammed Al-Ghamdi (Independent Director)',
 5, 0, 0, N'APPROVED', N'INTERNAL',
 '2026-03-15', NULL, 0,
 NULL, NULL),

-- Board Q1 — Resolution 2: 12% interim dividend
(@org, @mtg01, @board, N'RES-2026-Q1-BRD-002', 2, 2,
 N'Approve 12% interim dividend subject to FY2025 audit confirmation',
 N'اعتماد توزيع أرباح مرحلية بنسبة 12% رهناً بتأكيد المراجعة المالية للسنة المالية 2025',
 N'RESOLVED THAT the Board approves the payment of an interim dividend equal to 12% of the issued share capital, subject to (a) confirmation of the FY2025 audited financial statements by the external auditor, (b) filing with ZATCA and CMA per applicable disclosure rules, and (c) publication in two daily newspapers within 30 days of the General Assembly''s ratification.',
 N'قرر المجلس اعتماد دفع توزيع أرباح مرحلية بنسبة 12% من رأس المال المُصدر، رهناً بـ (أ) تأكيد القوائم المالية المدققة للسنة 2025 من المراجع الخارجي، (ب) التقديم إلى هيئة الزكاة والضريبة والجمارك وهيئة السوق المالية وفق قواعد الإفصاح المعمول بها، (ج) النشر في صحيفتين يوميتين خلال 30 يوماً من تصديق الجمعية العامة.',
 N'CFO', N'Abdullah Al-Saud (Chairman)',
 5, 0, 0, N'APPROVED', N'INTERNAL',
 '2026-03-31', NULL, 1,
 NULL, NULL),

-- Board Q1 — Resolution 3: FX appetite raised — has dissent
(@org, @mtg01, @board, N'RES-2026-Q1-BRD-003', 3, 3,
 N'Raise FX-exposure risk appetite to SAR 700,000 single-currency P&L',
 N'رفع شهية مخاطر التعرض لسعر الصرف إلى 700,000 ريال خسارة دفترية لعملة واحدة',
 N'RESOLVED THAT the single-currency-exposure FX appetite is raised from SAR 500,000 to SAR 700,000 monthly mark-to-market P&L impact, with a review trigger at SAR 600,000 (amber). The amended appetite shall take effect from 2026-04-01 and the Risk Committee shall provide a monthly attestation of exposure-against-appetite for 12 months from that date.',
 N'قرر المجلس رفع شهية مخاطر التعرض لعملة واحدة من 500,000 ريال إلى 700,000 ريال أثر شهري على الأرباح والخسائر بنظام السوق، مع محفز مراجعة عند 600,000 ريال (كهرماني). يسري التعديل من 2026-04-01 وتقدم لجنة المخاطر تأكيداً شهرياً للتعرض مقابل الشهية لمدة 12 شهراً من ذلك التاريخ.',
 N'CRO', N'CFO',
 4, 1, 0, N'APPROVED', N'INTERNAL',
 '2026-04-01', '2027-04-01', 0,
 N'Mr. Nasser Al-Dosari (Independent) voted against, reasoning that the 40% appetite increase is not commensurate with the corresponding hedging-capacity uplift and could materially weaken FY2026 earnings in a prolonged SAR-stress scenario. His dissent is recorded per CMA Article 31.',
 N'صوّت السيد ناصر الدوسري (مستقل) ضد القرار، مُعلِّلاً أن زيادة الشهية بنسبة 40% لا تتناسب مع رفع طاقة التحوط المقابلة وقد تُضعف جوهرياً أرباح 2026 في سيناريو ضغط مطوّل على الريال. سُجّل اعتراضه وفق المادة 31 من لائحة هيئة السوق المالية.'),

-- Risk Apr — Resolution 1: Aramco scenario recommendation
(@org, @mtg02, @risk, N'RES-2026-Q2-RSK-001', 1, 2,
 N'Recommend Board acceptance of Aramco-contract-delay scenario (SCN-DEMO-01)',
 N'التوصية بقبول المجلس لسيناريو تأخر عقد أرامكو (SCN-DEMO-01)',
 N'RESOLVED THAT the Risk Committee recommends to the Board the acceptance of the Aramco-contract-renewal-delay scenario per Risk RSK-2026-004, with the following conditions: (a) the residual exposure remains within the amber band of the existing risk appetite, (b) an 18-month re-review trigger is set, (c) the COO provides a quarterly contract-progress update to the Risk Committee from Q2 2026.',
 N'قرر اللجنة التوصية للمجلس بقبول سيناريو تأخر تجديد عقد أرامكو ضمن المخاطرة RSK-2026-004، بالشروط التالية: (أ) أن يظل التعرض المتبقي ضمن النطاق الكهرماني للشهية الحالية، (ب) تحديد محفز إعادة مراجعة بعد 18 شهراً، (ج) أن يقدم مدير العمليات تحديثاً ربع سنوياً عن تقدم العقد للجنة المخاطر من الربع الثاني 2026.',
 N'CRO', N'CFO',
 4, 0, 1, N'APPROVED', N'CONFIDENTIAL',
 NULL, '2027-10-15', 0,
 N'General Counsel abstained pending clarification of the indemnity language in the underlying contract.',
 N'امتنع المستشار العام عن التصويت في انتظار توضيح صيغة التعويض في العقد الأصلي.'),

-- Risk Apr — Resolution 2: Enterprise Copilot rollout
(@org, @mtg02, @risk, N'RES-2026-Q2-RSK-002', 2, 3,
 N'Sponsor enterprise Microsoft 365 Copilot rollout under controlled-AI framework',
 N'رعاية نشر Microsoft 365 Copilot المؤسسي تحت إطار الذكاء الاصطناعي المضبوط',
 N'RESOLVED THAT the Risk Committee sponsors the enterprise Microsoft 365 Copilot rollout, subject to (a) AI Ethics Committee pre-approval of the use-case inventory, (b) NCA ECC + SAMA cyber-security review of the data-residency arrangement (Saudi-region tenancy required), (c) blocking of consumer-LLM domains via DNS by 2026-05-20, (d) phased rollout starting with the Finance and Legal pilots.',
 N'قرر اللجنة رعاية نشر Microsoft 365 Copilot المؤسسي، رهناً بـ (أ) موافقة لجنة أخلاقيات الذكاء الاصطناعي المسبقة على جرد حالات الاستخدام، (ب) مراجعة NCA ECC وضوابط ساما السيبرانية لترتيب موقع البيانات (إيواء المنطقة السعودية إلزامي)، (ج) حجب نطاقات نماذج اللغة الاستهلاكية عبر DNS بحلول 2026-05-20، (د) النشر المرحلي بدءاً بتجربتي المالية والقانونية.',
 N'CISO', N'COO',
 4, 0, 0, N'APPROVED', N'INTERNAL',
 '2026-05-01', '2026-12-31', 0,
 NULL, NULL),

-- Audit Q1 — Resolution 1: Internal audit plan
(@org, @mtg03, @audit, N'RES-2026-Q1-AUD-001', 1, 2,
 N'Approve Internal Audit plan for Q1 2026',
 N'اعتماد خطة المراجعة الداخلية للربع الأول 2026',
 N'RESOLVED THAT the Audit Committee approves the Internal Audit plan for Q1 2026 as drafted by the Head of Internal Audit, comprising five engagements: (1) IT general-controls assertion, (2) Procure-to-pay process review, (3) Revenue-recognition end-to-end audit, (4) Cyber-incident-response readiness assessment, (5) Outsourced-service-provider attestation review. Total resource allocation: 230 audit-days.',
 N'قرر اللجنة اعتماد خطة المراجعة الداخلية للربع الأول 2026 كما صاغها رئيس المراجعة الداخلية، وتشمل خمسة تكليفات: (1) تأكيد ضوابط تقنية المعلومات العامة، (2) مراجعة عملية الشراء حتى الدفع، (3) مراجعة شاملة لاعتراف الإيرادات، (4) تقييم جاهزية الاستجابة للحوادث السيبرانية، (5) مراجعة اعتمادات مزودي الخدمات الخارجية. إجمالي الموارد المخصصة: 230 يوم مراجعة.',
 N'Head of Internal Audit', N'Mohammed Al-Ghamdi (Chair)',
 3, 0, 0, N'APPROVED', N'INTERNAL',
 '2026-01-15', NULL, 0,
 NULL, NULL),

-- Audit Q1 — Resolution 2: Endorse FY2025 audit opinion
(@org, @mtg03, @audit, N'RES-2026-Q1-AUD-002', 2, 1,
 N'Endorse FY2025 external audit opinion and recommend to Board',
 N'تأييد رأي المراجعة الخارجية للسنة المالية 2025 والتوصية للمجلس',
 N'RESOLVED THAT the Audit Committee endorses the FY2025 external audit opinion issued by KPMG (unqualified, with two emphasis-of-matter paragraphs covering (a) the SAMA cyber-attestation evidence-collection process and (b) the related-party-transaction disclosures), and recommends to the Board that the financial statements be tabled for ratification at the General Assembly. The two management-letter points are tracked as ACT-2026-Q1-AUD-001 and ACT-2026-Q1-AUD-002.',
 N'قرر اللجنة تأييد رأي المراجعة الخارجية للسنة 2025 الصادر عن KPMG (رأي غير متحفظ، مع فقرتين للتأكيد تغطيان (أ) عملية جمع أدلة الاعتماد السيبراني لساما، (ب) الإفصاح عن صفقات الأطراف ذات العلاقة)، والتوصية للمجلس بطرح القوائم المالية للتصديق في الجمعية العامة. تُتبَّع نقطتا خطاب الإدارة كإجراءين ACT-2026-Q1-AUD-001 وACT-2026-Q1-AUD-002.',
 N'External Auditor (KPMG)', N'Sara Al-Qahtani (Independent)',
 3, 0, 0, N'APPROVED', N'INTERNAL',
 '2026-02-15', NULL, 1,
 NULL, NULL),

-- Risk Mar — Resolution 1: Cross-border transfer
(@org, @mtg05, @risk, N'RES-2026-Q1-RSK-001', 1, 2,
 N'Approve cross-border data transfer to TCS India subject to BCR completion',
 N'اعتماد نقل البيانات عبر الحدود إلى TCS الهند رهناً بإكمال القواعد الملزمة',
 N'RESOLVED THAT the Risk Committee approves the cross-border transfer of operational support data to Tata Consultancy Services (India) per RoPA-2026-007, subject to (a) the data controller completing the Binding Corporate Rules (BCR) package and obtaining SDAIA approval per PDPL Article 29, (b) the standard contractual clauses being incorporated into the master services agreement, (c) annual re-attestation of the processor''s technical and organisational measures.',
 N'قرر اللجنة اعتماد نقل بيانات الدعم التشغيلي عبر الحدود إلى شركة Tata Consultancy Services (الهند) ضمن سجل المعالجة RoPA-2026-007، رهناً بـ (أ) إكمال المتحكم في البيانات حزمة القواعد الملزمة (BCR) والحصول على موافقة SDAIA وفق المادة 29 من نظام حماية البيانات الشخصية، (ب) تضمين الشروط التعاقدية القياسية في اتفاقية الخدمات الرئيسية، (ج) إعادة الاعتماد السنوي للتدابير التقنية والتنظيمية للمعالج.',
 N'DPO', N'General Counsel',
 4, 0, 0, N'APPROVED', N'CONFIDENTIAL',
 NULL, '2027-03-15', 1,
 NULL, NULL);

-- ═════════════════════════════════════════════════════════════════════
-- ACTION ITEMS — 9 worked examples
-- ═════════════════════════════════════════════════════════════════════

INSERT INTO admin.committee_action_items
    (organisation_id, meeting_id, committee_id, action_code, seq, agenda_item_seq,
     title_en, title_ar, description_en, description_ar,
     owner_user_id, owner_name, owner_role,
     due_date, priority, status, completion_notes_en, completed_at,
     linked_module, linked_entity_type, linked_entity_id, linked_entity_code,
     carried_from_meeting_id)
VALUES

-- Board Q1 — Action 1 (completed)
(@org, @mtg01, @board, N'ACT-2026-Q1-BRD-001', 1, 2,
 N'Publish revised V2030 alignment section in 2026 plan',
 N'نشر قسم التوافق المنقّح مع رؤية 2030 في خطة 2026',
 N'CEO Office to incorporate the digital-economy and human-capital pillar expansions agreed at the Board Q1 meeting, recirculate the amended plan to all directors, and publish on the intranet within 14 days.',
 N'مكتب الرئيس التنفيذي يدمج توسعات ركيزتي الاقتصاد الرقمي ورأس المال البشري المتفق عليها في اجتماع الربع الأول، ويعيد تعميم الخطة المعدّلة على جميع الأعضاء، وينشرها على الشبكة الداخلية خلال 14 يوماً.',
 NULL, N'CEO Office', N'Chief of Staff',
 '2026-03-15', N'HIGH', N'COMPLETED',
 N'Distributed to all directors 2026-03-12. Published on intranet 2026-03-14. Confirmed by Chief of Staff.',
 '2026-03-14T16:00:00',
 NULL, NULL, NULL, NULL, NULL),

-- Board Q1 — Action 2 (in progress)
(@org, @mtg01, @board, N'ACT-2026-Q1-BRD-002', 2, 3,
 N'Quarterly Aramco contract progress update to Board',
 N'تحديث ربع سنوي عن تقدم عقد أرامكو للمجلس',
 N'COO to deliver a structured quarterly briefing on the Aramco contract renewal — counterparty engagement status, milestone achievement, risk-status changes, and any change-request implications. First update due at Q2 2026 Board meeting.',
 N'مدير العمليات يقدم إيجازاً ربع سنوياً منظماً عن تجديد عقد أرامكو — حالة التفاعل مع الطرف المقابل، تحقيق المعالم، تغيرات حالة المخاطر، وأي آثار لطلبات التغيير. أول تحديث في اجتماع المجلس للربع الثاني 2026.',
 NULL, N'COO', N'Chief Operating Officer',
 '2026-06-15', N'HIGH', N'IN_PROGRESS', NULL, NULL,
 N'RISK', N'Risk', NULL, N'RSK-2026-004', NULL),

-- Board Q1 — Action 3 (completed)
(@org, @mtg01, @board, N'ACT-2026-Q1-BRD-003', 3, 2,
 N'Submit dividend approval to ZATCA',
 N'تقديم اعتماد توزيع الأرباح إلى هيئة الزكاة',
 N'CFO + Tax Director to file the Board-approved 12% interim dividend declaration with ZATCA within statutory timelines, attaching the Board resolution + audit attestation.',
 N'المدير المالي ومدير الضرائب يقدمان إعلان توزيع الأرباح المرحلية بنسبة 12% المعتمد من المجلس إلى هيئة الزكاة والضريبة والجمارك خلال المواعيد القانونية، مع إرفاق قرار المجلس وتأكيد المراجعة.',
 NULL, N'CFO', N'Chief Financial Officer',
 '2026-03-31', N'HIGH', N'COMPLETED',
 N'Filed 2026-03-28 — confirmation reference ZATCA-DD-2026-04481. Tax Director confirmed receipt.',
 '2026-03-28T11:30:00',
 NULL, NULL, NULL, NULL, NULL),

-- Risk Apr — Action 1 (pending)
(@org, @mtg02, @risk, N'ACT-2026-Q2-RSK-001', 1, 2,
 N'Submit Aramco scenario to Board Q2 with 18-month review trigger',
 N'تقديم سيناريو أرامكو للمجلس في الربع الثاني مع محفز مراجعة 18 شهراً',
 N'CRO to prepare the Risk Committee''s formal recommendation paper for the Board Q2 meeting — include scenario narrative, residual-exposure analysis, hedging-capacity assessment, and the proposed 18-month re-review trigger.',
 N'مدير المخاطر يُعد ورقة التوصية الرسمية للجنة المخاطر لاجتماع المجلس الربع الثاني — تشمل سرد السيناريو، تحليل التعرض المتبقي، تقييم طاقة التحوط، ومحفز إعادة المراجعة المقترح بعد 18 شهراً.',
 NULL, N'CRO', N'Chief Risk Officer',
 '2026-06-15', N'HIGH', N'OPEN', NULL, NULL,
 N'RISK', N'Risk', NULL, N'RSK-2026-004', NULL),

-- Risk Apr — Action 2 (in progress)
(@org, @mtg02, @risk, N'ACT-2026-Q2-RSK-002', 2, 3,
 N'Issue enterprise Microsoft 365 Copilot RFP',
 N'إصدار طلب عرض أسعار Microsoft 365 Copilot المؤسسي',
 N'CISO + Procurement to issue the RFP for enterprise M365 Copilot, ensuring requirements cover Saudi-region data residency, NCA ECC + SAMA cyber baseline compliance, AI Ethics Committee gate-review process, and DLP integration. Target award by Q3 2026.',
 N'مدير أمن المعلومات والمشتريات يصدران طلب عرض الأسعار لـ Copilot المؤسسي، مع ضمان أن تغطي المتطلبات إيواء البيانات في المنطقة السعودية، الامتثال لخط الأساس السيبراني لـ NCA ECC وساما، عملية مراجعة بوابة لجنة أخلاقيات الذكاء الاصطناعي، وتكامل DLP. الترسية المستهدفة بحلول الربع الثالث 2026.',
 NULL, N'CISO', N'Chief Information Security Officer',
 '2026-05-30', N'NORMAL', N'IN_PROGRESS', NULL, NULL,
 N'CYBER', N'AiEthicsCase', NULL, N'AIEC-2026-001', NULL),

-- Risk Apr — Action 3 (open, blocked on infrastructure)
(@org, @mtg02, @risk, N'ACT-2026-Q2-RSK-003', 3, 3,
 N'Block consumer-LLM domains via DNS',
 N'حجب نطاقات نماذج اللغة الاستهلاكية عبر DNS',
 N'SecOps Lead to add consumer LLM domains (openai.com chat, anthropic.com web, gemini.google.com, etc.) to the DNS RPZ block-list ahead of the enterprise Copilot rollout, with measured logging via Netskope CASB to track residual shadow-IT.',
 N'قائد SecOps يضيف نطاقات نماذج اللغة الاستهلاكية (openai.com chat، anthropic.com web، gemini.google.com، إلخ) إلى قائمة حجب RPZ في DNS قبل نشر Copilot المؤسسي، مع التسجيل المُقاس عبر Netskope CASB لتتبع الظل التقني المتبقي.',
 NULL, N'SecOps Lead', N'Security Operations Lead',
 '2026-05-20', N'NORMAL', N'OPEN', NULL, NULL,
 N'CYBER', N'CyberControl', NULL, N'CTRL-NETSEG-007', NULL),

-- Audit Q1 — Action 1 (completed)
(@org, @mtg03, @audit, N'ACT-2026-Q1-AUD-001', 1, 1,
 N'Implement journal-posting segregation-of-duties control',
 N'تطبيق ضابط فصل المهام في قيد اليومية',
 N'Finance Director to implement the SoD control in the ERP — same individual cannot enter, post, and approve a journal — addressing the external auditor''s management-letter point ML-2025-A.',
 N'مدير المالية يطبق ضابط فصل المهام في نظام ERP — لا يمكن للشخص نفسه إدخال وقيد واعتماد القيد — لمعالجة نقطة خطاب الإدارة ML-2025-A من المراجع الخارجي.',
 NULL, N'Finance Director', N'Finance Director',
 '2026-04-30', N'CRITICAL', N'COMPLETED',
 N'Three-segregation roles configured in Oracle EBS 2026-04-22. External Auditor confirmed adequacy in walkthrough 2026-04-28.',
 '2026-04-28T15:45:00',
 N'AUDIT', N'AuditFinding', NULL, N'FND-2025-ML-A', NULL),

-- Audit Q1 — Action 2 (in progress)
(@org, @mtg03, @audit, N'ACT-2026-Q1-AUD-002', 2, 1,
 N'Quarterly IT GC user-access reviews',
 N'مراجعات وصول مستخدمي ضوابط IT العامة ربع سنوياً',
 N'CISO + IT Identity Lead to institute formal quarterly user-access reviews on the in-scope financial-system accounts, with sign-off retained for SOX/ICOFR-equivalent attestation. Addresses external auditor management-letter point ML-2025-B.',
 N'مدير أمن المعلومات وقائد الهوية في IT يضعان مراجعات ربع سنوية رسمية لوصول المستخدمين على حسابات الأنظمة المالية ضمن النطاق، مع الاحتفاظ بالاعتماد لأغراض اعتماد المعادل لـ SOX/ICOFR. لمعالجة نقطة خطاب الإدارة ML-2025-B.',
 NULL, N'CISO', N'Chief Information Security Officer',
 '2026-06-30', N'HIGH', N'IN_PROGRESS', NULL, NULL,
 N'AUDIT', N'AuditFinding', NULL, N'FND-2025-ML-B', NULL),

-- Audit Q1 — Action 3 (in progress)
(@org, @mtg03, @audit, N'ACT-2026-Q1-AUD-003', 3, 3,
 N'Close NCA ECC backup-verification frequency gap',
 N'إغلاق فجوة تواتر التحقق من النسخ الاحتياطية في NCA ECC',
 N'SecOps Lead to close the remaining NCA ECC gap (control 2-7-2: backup-verification testing frequency) — increase from semi-annual to quarterly, with restore-rehearsal evidence per Saudi-region site. Target NCA ECC attestation by 2026-Q3.',
 N'قائد SecOps يغلق الفجوة المتبقية في NCA ECC (الضابط 2-7-2: تواتر اختبار التحقق من النسخ الاحتياطية) — رفعه من نصف سنوي إلى ربع سنوي، مع أدلة تمرين الاستعادة لكل موقع في المنطقة السعودية. اعتماد NCA ECC المستهدف بحلول الربع الثالث 2026.',
 NULL, N'SecOps Lead', N'Security Operations Lead',
 '2026-06-30', N'HIGH', N'IN_PROGRESS', NULL, NULL,
 N'CYBER', N'CyberControl', NULL, N'CTRL-NCA-ECC-2-7-2', NULL);

END
GO

PRINT N'V157 — committee_resolutions (8 rows) + committee_action_items (9 rows) worked-example seed applied.';
GO
