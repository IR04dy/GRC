-- V285 (Green-Finance uplift G0): Saudi ESG / green-finance regulatory register.
--
-- Seeds the sustainability regulatory obligations (REG-1..REG-11 of
-- SUSTAINABILITY_GREEN_FINANCE_REVIEW_AND_UPLIFT_PLAN.md §2) into the compliance
-- obligation register so the ESG regulatory landscape is DATA (obligation → owner →
-- attestation → evidence), not documentation. Follows the V184 idiom exactly:
-- org 1, soft owner FK (1 = admin), idempotent obligation_code guards, bilingual
-- Saudi-native text, lifecycle ACTIVE. REG-6 is split into MWAN (waste) and SEEC
-- (energy) rows — distinct regulators, distinct attestations — so 12 rows total.
--
-- NOTE: effective dates for IFRS S1/S2 (SOCPA endorsement) are phased — the seeded
-- due dates are planning anchors for the demo tenant; real tenants must confirm
-- current SOCPA transition dates.

-- REG-1 · SOCPA — IFRS S1/S2 readiness
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-001')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-001',
    'IFRS S1/S2 sustainability-disclosure readiness (SOCPA endorsement)',
    N'الجاهزية لإفصاحات الاستدامة وفق معياري IFRS S1/S2 (اعتماد الهيئة السعودية للمراجعين والمحاسبين)',
    'Prepare governance, strategy, risk-management and metrics/targets disclosures per IFRS S1 and climate disclosures per IFRS S2 as endorsed by SOCPA (phased application — confirm current effective dates). Gap-assess annually until effective.',
    N'الاستعداد لإفصاحات الحوكمة والاستراتيجية وإدارة المخاطر والمقاييس والمستهدفات وفق معيار IFRS S1، وإفصاحات المناخ وفق معيار IFRS S2 كما اعتمدتهما الهيئة السعودية للمراجعين والمحاسبين (تطبيق مرحلي — يلزم التحقق من تواريخ السريان). تقييم الفجوات سنوياً حتى السريان.',
    'SOCPA', 'SOCPA endorsement of IFRS S1 & S2 (phased)', 'DOCUMENTATION', 'ANNUAL', 'HIGH',
    '2026-12-31', '2026-12-31', 60, 1, 'Sustainability', N'الاستدامة', 'ACTIVE'
);

-- REG-2 · CMA — Corporate Governance Regulations Arts 91–94
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-002')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-002',
    'CMA Corporate Governance Regulations — social/environmental disclosures (Arts 91-94)',
    N'لائحة حوكمة الشركات — إفصاحات المسؤولية الاجتماعية والبيئية (المواد ٩١–٩٤)',
    'Maintain board-approved social-responsibility policy and programmes and disclose per Articles 91-94 of the CMA Corporate Governance Regulations in the annual board report.',
    N'اعتماد مجلس الإدارة لسياسة وبرامج المسؤولية الاجتماعية والإفصاح عنها وفق المواد ٩١–٩٤ من لائحة حوكمة الشركات ضمن تقرير مجلس الإدارة السنوي.',
    'CMA', 'CMA Corporate Governance Regulations, Arts 91-94', 'REPORTING', 'ANNUAL', 'HIGH',
    '2027-03-31', '2027-03-31', 45, 1, 'Sustainability', N'الاستدامة', 'ACTIVE'
);

-- REG-3 · Tadawul — ESG Disclosure Guidelines (voluntary)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-003')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-003',
    'Saudi Exchange ESG Disclosure Guidelines — voluntary annual ESG report',
    N'إرشادات الإفصاح عن الاستدامة الصادرة عن تداول السعودية — تقرير سنوي طوعي',
    'Publish an annual ESG report aligned to the Saudi Exchange ESG Disclosure Guidelines metric set (voluntary; market-expected for listed issuers).',
    N'إصدار تقرير استدامة سنوي متوافق مع مجموعة مؤشرات إرشادات الإفصاح الصادرة عن تداول السعودية (طوعي، ومتوقع سوقياً من الشركات المدرجة).',
    'TADAWUL', 'Saudi Exchange ESG Disclosure Guidelines (2021)', 'REPORTING', 'ANNUAL', 'MEDIUM',
    '2026-09-30', '2026-09-30', 45, 1, 'Sustainability', N'الاستدامة', 'ACTIVE'
);

-- REG-4 · SGI — Saudi Green Initiative / net-zero 2060 alignment
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-004')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-004',
    'Saudi Green Initiative alignment attestation (net-zero 2060, CCE framework)',
    N'إقرار سنوي بمواءمة مستهدفات مبادرة السعودية الخضراء (صافي الصفر ٢٠٦٠ وإطار الاقتصاد الكربوني الدائري)',
    'Attest annually that corporate emission-reduction commitments and progress are aligned with Saudi Green Initiative national targets (278 mtpa CO2e reduction by 2030; 50% renewable power share 2030; net-zero 2060 via the Circular Carbon Economy framework).',
    N'الإقرار سنوياً بمواءمة التزامات خفض الانبعاثات وتقدمها مع المستهدفات الوطنية لمبادرة السعودية الخضراء (خفض ٢٧٨ مليون طن مكافئ سنوياً بحلول ٢٠٣٠، و٥٠٪ من الكهرباء من مصادر متجددة بحلول ٢٠٣٠، وصافي الصفر ٢٠٦٠ عبر إطار الاقتصاد الكربوني الدائري).',
    'SGI', 'Saudi Green Initiative national targets; CCE framework', 'ATTESTATION', 'ANNUAL', 'MEDIUM',
    '2026-10-31', '2026-10-31', 30, 1, 'Sustainability', N'الاستدامة', 'ACTIVE'
);

-- REG-5 · NCEC — environmental permits & compliance monitoring
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-005')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-005',
    'NCEC environmental permits — validity, monitoring and violation handling',
    N'التصاريح البيئية من المركز الوطني للرقابة على الالتزام البيئي — السريان والرصد ومعالجة المخالفات',
    'Maintain valid NCEC environmental permits for all facilities under the Environment Law and its implementing regulations; meet monitoring/reporting conditions; remediate violations via corrective actions.',
    N'المحافظة على سريان التصاريح البيئية لجميع المنشآت وفق نظام البيئة ولوائحه التنفيذية، والوفاء بشروط الرصد والإبلاغ، ومعالجة المخالفات عبر إجراءات تصحيحية.',
    'NCEC', 'Environment Law + implementing regulations (MEWA/NCEC)', 'REGISTRATION', 'ON_EVENT', 'CRITICAL',
    '2026-08-31', '2026-08-31', 30, 1, 'Operations', N'العمليات', 'ACTIVE'
);

-- REG-6a · MWAN — waste management reporting
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-006')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-006',
    'MWAN waste management compliance and reporting',
    N'الالتزام بأنظمة المركز الوطني لإدارة النفايات (موان) والإبلاغ عنها',
    'Comply with MWAN waste management regulations (segregation, licensed transfer/treatment, records) and file required waste data.',
    N'الالتزام بلوائح إدارة النفايات الصادرة عن المركز الوطني لإدارة النفايات (الفرز، النقل والمعالجة المرخصة، السجلات) وتقديم بيانات النفايات المطلوبة.',
    'MWAN', 'MWAN waste management regulations', 'REPORTING', 'ANNUAL', 'MEDIUM',
    '2026-11-30', '2026-11-30', 30, 1, 'Operations', N'العمليات', 'ACTIVE'
);

-- REG-6b · SEEC — energy efficiency programme
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-007')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-007',
    'SEEC energy-efficiency programme participation and energy data reporting',
    N'المشاركة في برامج المركز السعودي لكفاءة الطاقة (كفاءة) والإبلاغ عن بيانات الطاقة',
    'Track facility energy consumption/intensity and report per SEEC (Kafaah) programme requirements applicable to the sector.',
    N'رصد استهلاك الطاقة وكثافتها في المنشآت والإبلاغ عنها وفق متطلبات برامج المركز السعودي لكفاءة الطاقة المنطبقة على القطاع.',
    'SEEC', 'SEEC (Kafaah) sector programmes', 'REPORTING', 'ANNUAL', 'MEDIUM',
    '2026-11-30', '2026-11-30', 30, 1, 'Operations', N'العمليات', 'ACTIVE'
);

-- REG-7 · ICMA/AAOIFI/MoF-GFF — green sukuk post-issuance reporting
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-008')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-008',
    'Green sukuk post-issuance reporting — annual allocation & impact reports until full allocation',
    N'تقارير ما بعد إصدار الصكوك الخضراء — تقرير التخصيص وتقرير الأثر سنوياً حتى اكتمال التخصيص',
    'For each green sukuk/bond: publish annual use-of-proceeds allocation report and impact report per ICMA Green Bond Principles / green sukuk practice and the issuance framework (incl. sovereign Green Financing Framework where applicable), with external review (SPO) and AAOIFI-compliant structure documentation, until proceeds are fully allocated and upon material changes.',
    N'لكل صك أو سند أخضر: إصدار تقرير سنوي عن تخصيص المتحصلات وتقرير عن الأثر وفق مبادئ السندات الخضراء الصادرة عن ICMA وممارسات الصكوك الخضراء وإطار الإصدار (بما في ذلك الإطار السيادي للتمويل الأخضر متى انطبق)، مع مراجعة خارجية (رأي طرف ثانٍ) وتوثيق الهيكل الشرعي وفق معايير أيوفي، حتى اكتمال تخصيص المتحصلات وعند أي تغيير جوهري.',
    'ICMA', 'ICMA GBP; AAOIFI sukuk standards; MoF Green Financing Framework', 'REPORTING', 'ANNUAL', 'HIGH',
    '2027-03-31', '2027-03-31', 60, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- REG-8 · CMA — sukuk offering & continuing obligations
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-009')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-009',
    'CMA offering and continuing obligations for listed (green) sukuk',
    N'التزامات الطرح والالتزامات المستمرة لدى هيئة السوق المالية للصكوك (الخضراء) المدرجة',
    'Meet CMA rules on the offer of securities and continuing obligations for listed sukuk: prospectus disclosures, material-development announcements, and periodic reporting.',
    N'الوفاء بقواعد طرح الأوراق المالية والالتزامات المستمرة لدى هيئة السوق المالية للصكوك المدرجة: إفصاحات نشرة الإصدار، والإعلان عن التطورات الجوهرية، والتقارير الدورية.',
    'CMA', 'CMA Rules on the Offer of Securities and Continuing Obligations', 'FILING', 'ON_EVENT', 'HIGH',
    '2026-12-31', '2026-12-31', 14, 1, 'Treasury', N'الخزينة', 'ACTIVE'
);

-- REG-9 · SAMA — climate-risk supervisory readiness (FIs)
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-010')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-010',
    'SAMA climate-risk readiness — scenario analysis and financed-emissions data',
    N'الجاهزية لمتطلبات البنك المركزي السعودي بشأن مخاطر المناخ — تحليل السيناريوهات وبيانات الانبعاثات المموَّلة',
    'Maintain climate scenario-analysis capability (TCFD-aligned) and financed-emissions data quality suitable for emerging SAMA supervisory expectations (NGFS-aligned) and future climate stress tests.',
    N'المحافظة على قدرة تحليل سيناريوهات المناخ (بما يتوافق مع TCFD) وجودة بيانات الانبعاثات المموَّلة بما يلبي التوقعات الإشرافية الناشئة للبنك المركزي السعودي (المتوائمة مع شبكة NGFS) واختبارات الضغط المناخية المستقبلية.',
    'SAMA', 'SAMA supervisory direction (NGFS member); TCFD', 'DOCUMENTATION', 'ANNUAL', 'HIGH',
    '2026-12-31', '2026-12-31', 45, 1, 'Risk', N'المخاطر', 'ACTIVE'
);

-- REG-10 · EU CBAM — embedded-emissions reporting for exports
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-011')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-011',
    'EU CBAM embedded-emissions data for in-scope exports (aluminium/steel/cement/fertilisers/hydrogen)',
    N'بيانات الانبعاثات المضمّنة لآلية تعديل حدود الكربون الأوروبية (CBAM) للصادرات المشمولة (الألمنيوم/الحديد/الأسمنت/الأسمدة/الهيدروجين)',
    'Provide EU importers with product-level embedded-emissions data for CBAM-scope exports each reporting period (definitive regime from 2026); monitor carbon-cost exposure.',
    N'تزويد المستوردين الأوروبيين ببيانات الانبعاثات المضمّنة على مستوى المنتج للصادرات المشمولة بآلية CBAM في كل فترة إبلاغ (النظام النهائي اعتباراً من ٢٠٢٦)، ومراقبة التعرض لتكلفة الكربون.',
    'EU-CBAM', 'EU Regulation 2023/956 (CBAM)', 'REPORTING', 'QUARTERLY', 'HIGH',
    '2026-07-31', '2026-07-31', 21, 1, 'Operations', N'العمليات', 'ACTIVE'
);

-- REG-11 · RVCMC — voluntary carbon-credit integrity
IF NOT EXISTS (SELECT 1 FROM compliance.obligation WHERE organisation_id = 1 AND obligation_code = 'OBL-ESG-2026-012')
INSERT INTO compliance.obligation (
    organisation_id, obligation_code, title_en, title_ar, description_en, description_ar,
    regulator, regulation_reference, obligation_type, frequency, severity_if_missed,
    first_due_date, next_due_date, lead_time_days, owner_user_id, business_unit_en, business_unit_ar, lifecycle_stage
) VALUES (
    1, 'OBL-ESG-2026-012',
    'Voluntary carbon credits — registry integrity and retirement evidence (RVCMC)',
    N'أرصدة الكربون الطوعية — سلامة السجل وأدلة الإلغاء (الشركة الإقليمية لسوق الكربون الطوعي)',
    'For purchased voluntary carbon credits (e.g., via RVCMC auctions): retain registry certificates, serial ranges and retirement evidence; claim offsets only alongside disclosed gross reductions (no net-only claims).',
    N'للأرصدة الكربونية الطوعية المشتراة (مثلاً عبر مزادات الشركة الإقليمية لسوق الكربون الطوعي): الاحتفاظ بشهادات السجل ونطاقات الأرقام التسلسلية وأدلة الإلغاء، وعدم إعلان المعاوضة إلا مع الإفصاح عن الخفض الإجمالي (لا ادعاءات صافية فقط).',
    'RVCMC', 'RVCMC market practice; ICVCM Core Carbon Principles', 'DOCUMENTATION', 'ON_EVENT', 'MEDIUM',
    '2026-12-31', '2026-12-31', 14, 1, 'Sustainability', N'الاستدامة', 'ACTIVE'
);
