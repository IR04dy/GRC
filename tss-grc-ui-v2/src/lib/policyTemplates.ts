// Policy template library — Track D2 (content moat).
//
// Bilingual policy templates calibrated for Saudi regulatory context.
// Tenants can preview, clone-and-brand and download. Cloning produces a
// branded copy stored in localStorage that the policy register and the
// board pack can render on demand.
//
// Each template carries:
//   • category (Information Security, Privacy, BCM, …)
//   • framework anchors (NCA-ECC, PDPL, SAMA-RBSF, …)
//   • a sequence of bilingual sections (numbered, ready to paginate)
//   • metadata: version, last reviewed (Hijri + Gregorian)

export type PolicyCategory =
  | 'information-security'
  | 'cybersecurity'
  | 'privacy'
  | 'acceptable-use'
  | 'incident-response'
  | 'business-continuity'
  | 'third-party'
  | 'access-control'
  | 'code-of-conduct'
  | 'ai-governance';

export interface PolicySection {
  code: string;
  titleEn: string;
  titleAr: string;
  bodyEn: string;
  bodyAr: string;
}

export interface PolicyTemplate {
  code: string;
  category: PolicyCategory;
  nameEn: string;
  nameAr: string;
  /** Tagline shown on the catalog card. */
  blurbEn: string;
  blurbAr: string;
  /** Framework codes this template aligns to. */
  frameworkAnchors: string[];
  version: string;
  /** ISO date — last template review. */
  lastReviewed: string;
  /** Estimated reading time in minutes. */
  readMinutes: number;
  sections: PolicySection[];
}

// ─── 1 · Information Security Policy ─────────────────────────────────────────

const INFOSEC_POLICY: PolicyTemplate = {
  code: 'POL-INFOSEC',
  category: 'information-security',
  nameEn: 'Information Security Policy',
  nameAr: 'سياسة أمن المعلومات',
  blurbEn: 'Cornerstone policy aligned to NCA ECC v3 — defines the information security management system, governance bodies, classification scheme and acceptable risk appetite.',
  blurbAr: 'السياسة الأم المرتبطة بالضوابط الأساسية للأمن السيبراني (الإصدار الثالث) — تحدّد نظام إدارة أمن المعلومات وهيئات الحوكمة وتصنيف البيانات وشهية المخاطر المقبولة.',
  frameworkAnchors: ['NCA-ECC-V3', 'ISO-27001'],
  version: '2.1',
  lastReviewed: '2025-08-15',
  readMinutes: 12,
  sections: [
    {
      code: '1', titleEn: 'Purpose', titleAr: 'الغرض',
      bodyEn: 'This policy establishes the rules and responsibilities for protecting information assets owned, processed or transmitted by [ORGANISATION]. It implements the National Cybersecurity Authority Essential Cybersecurity Controls (ECC v3) and aligns with ISO/IEC 27001:2022.',
      bodyAr: 'تُرسي هذه السياسة القواعد والمسؤوليات لحماية أصول المعلومات التي تملكها أو تعالجها أو تنقلها [اسم المنشأة]. تنفّذ الضوابط الأساسية للأمن السيبراني الصادرة عن الهيئة الوطنية للأمن السيبراني (الإصدار الثالث) وتتسق مع معيار آيزو 27001:2022.',
    },
    {
      code: '2', titleEn: 'Scope', titleAr: 'النطاق',
      bodyEn: 'This policy applies to all employees, contractors, vendors and any third party with access to [ORGANISATION] information systems, regardless of geographic location. It covers all information regardless of medium — electronic, physical or verbal.',
      bodyAr: 'تنطبق هذه السياسة على جميع الموظفين والمتعاقدين والمورّدين وأي طرف ثالث له صلاحية الوصول إلى أنظمة معلومات [اسم المنشأة] بصرف النظر عن موقعه الجغرافي. تغطّي جميع المعلومات بكل أشكالها — الإلكترونية والمادّية والشفهية.',
    },
    {
      code: '3', titleEn: 'Roles and Responsibilities', titleAr: 'الأدوار والمسؤوليات',
      bodyEn: 'The Board of Directors approves this policy. The Cybersecurity Steering Committee owns enforcement and meets quarterly. The CISO is accountable for the cybersecurity program. Information owners classify data and authorise access. Every employee is responsible for compliance.',
      bodyAr: 'يعتمد مجلس الإدارة هذه السياسة. تتولّى لجنة توجيه الأمن السيبراني تنفيذها وتجتمع كل ثلاثة أشهر. يتحمّل رئيس أمن المعلومات (CISO) المسؤولية عن برنامج الأمن السيبراني. يُصنّف مالكو المعلومات البيانات ويُصرّحون بالوصول. كل موظف مسؤول عن الالتزام.',
    },
    {
      code: '4', titleEn: 'Data Classification', titleAr: 'تصنيف البيانات',
      bodyEn: 'Information is classified into four levels: Top Secret, Secret, Confidential and Public. Each level defines required handling, transmission and storage controls. Default classification for unmarked information is Confidential.',
      bodyAr: 'تُصنّف المعلومات إلى أربعة مستويات: سري للغاية، سري، خاص، عام. يُحدّد كل مستوى ضوابط التداول والنقل والتخزين المطلوبة. التصنيف الافتراضي للمعلومات غير المُعلَّمة هو "خاص".',
    },
    {
      code: '5', titleEn: 'Acceptable Risk', titleAr: 'المخاطر المقبولة',
      bodyEn: 'The Board sets the cybersecurity risk appetite annually. Residual risks rated above the appetite must be either treated, transferred to a third party, or escalated to the Audit Committee for accept-with-justification.',
      bodyAr: 'يحدّد مجلس الإدارة شهية المخاطر السيبرانية سنوياً. المخاطر المتبقية التي تتجاوز الشهية يجب معالجتها أو تحويلها إلى طرف ثالث أو تصعيدها إلى لجنة المراجعة لقبولها بمسوّغ.',
    },
    {
      code: '6', titleEn: 'Review and Approval', titleAr: 'المراجعة والاعتماد',
      bodyEn: 'This policy is reviewed annually and after any significant change in the threat landscape or regulatory requirements. The next mandatory review date is 12 months from the date shown on the cover page.',
      bodyAr: 'تُراجَع هذه السياسة سنوياً وبعد أي تغيير جوهري في مشهد التهديدات أو المتطلّبات التنظيمية. تاريخ المراجعة الإلزامية القادم هو بعد 12 شهراً من تاريخ صفحة الغلاف.',
    },
  ],
};

// ─── 2 · PDPL Privacy Policy ─────────────────────────────────────────────────

const PRIVACY_POLICY: PolicyTemplate = {
  code: 'POL-PDPL',
  category: 'privacy',
  nameEn: 'Personal Data Protection (PDPL) Policy',
  nameAr: 'سياسة حماية البيانات الشخصية',
  blurbEn: 'PDPL-compliant privacy policy — covers lawful basis, data subject rights, breach notification within 72 hours and cross-border transfer safeguards.',
  blurbAr: 'سياسة خصوصية متوافقة مع نظام حماية البيانات الشخصية — تغطّي السند النظامي وحقوق أصحاب البيانات والإشعار خلال 72 ساعة وضمانات النقل العابر للحدود.',
  frameworkAnchors: ['PDPL'],
  version: '1.4',
  lastReviewed: '2025-09-21',
  readMinutes: 14,
  sections: [
    {
      code: '1', titleEn: 'Lawful Basis for Processing', titleAr: 'السند النظامي للمعالجة',
      bodyEn: '[ORGANISATION] processes personal data only when one of the lawful bases under Article 5 of the PDPL applies: consent, contract performance, legal obligation, vital interest, public interest or legitimate interest balanced against the data subject\'s rights.',
      bodyAr: 'تعالج [اسم المنشأة] البيانات الشخصية فقط عند توافر أحد السندات النظامية الواردة في المادة 5 من نظام حماية البيانات الشخصية: الموافقة، أو تنفيذ العقد، أو الالتزام النظامي، أو المصلحة الحيوية، أو المصلحة العامة، أو المصلحة المشروعة الموازنة لحقوق صاحب البيانات.',
    },
    {
      code: '2', titleEn: 'Data Subject Rights', titleAr: 'حقوق أصحاب البيانات',
      bodyEn: 'Data subjects have the right to be informed, access their data, request rectification, request erasure, withdraw consent, object to processing and request transfer of their data to another controller. Requests are handled within 30 days through the Trust Center portal.',
      bodyAr: 'لأصحاب البيانات الحقّ في العلم، والوصول إلى بياناتهم، وطلب التصحيح، وطلب الحذف، وسحب الموافقة، والاعتراض على المعالجة، وطلب نقل بياناتهم إلى مسؤول معالجة آخر. تُعالج الطلبات خلال 30 يوماً عبر بوابة مركز الثقة.',
    },
    {
      code: '3', titleEn: 'Breach Notification', titleAr: 'الإشعار بالخروقات',
      bodyEn: 'Suspected breaches involving personal data are reported within 1 hour to the DPO. Confirmed breaches are notified to SDAIA within 72 hours of discovery, including breach description, categories of affected data subjects, likely consequences and mitigation taken.',
      bodyAr: 'تُبلَّغ مسؤول حماية البيانات (DPO) عن الخروقات المشتبه بها خلال ساعة واحدة. تُخطَر هيئة سدايا بالخروقات المؤكَّدة خلال 72 ساعة من اكتشافها، شاملةً وصف الخرق وفئات أصحاب البيانات المتأثّرين والعواقب المحتملة والإجراءات المتخذة.',
    },
    {
      code: '4', titleEn: 'Cross-Border Transfers', titleAr: 'النقل العابر للحدود',
      bodyEn: 'Personal data is not transferred outside Saudi Arabia unless the destination provides an adequate level of protection or appropriate safeguards (binding corporate rules, standard contractual clauses, certification) are in place per Article 29.',
      bodyAr: 'لا تُنقل البيانات الشخصية خارج المملكة العربية السعودية إلا إذا كانت الجهة المستقبِلة توفّر مستوى حماية ملائماً أو وُضعت ضمانات مناسبة (قواعد الشركات الملزمة، البنود التعاقدية النموذجية، الشهادات) وفقاً للمادة 29.',
    },
    {
      code: '5', titleEn: 'Retention', titleAr: 'الاحتفاظ',
      bodyEn: 'Personal data is retained only for the period necessary to fulfil the original purpose plus any legal retention obligations. The retention schedule is published on the Trust Center and reviewed annually by the DPO.',
      bodyAr: 'تُحفظ البيانات الشخصية فقط للفترة اللازمة لتحقيق الغرض الأصلي مضافاً إليها أي التزامات احتفاظ نظامية. يُنشر جدول الاحتفاظ في مركز الثقة ويُراجعه مسؤول حماية البيانات سنوياً.',
    },
    {
      code: '6', titleEn: 'Contact', titleAr: 'التواصل',
      bodyEn: 'Questions about this policy or to exercise your rights, contact the Data Protection Officer at dpo@[ORGANISATION] or via the Trust Center portal.',
      bodyAr: 'للاستفسار عن هذه السياسة أو ممارسة حقوقك، تواصل مع مسؤول حماية البيانات على dpo@[اسم المنشأة] أو عبر بوابة مركز الثقة.',
    },
  ],
};

// ─── 3 · Acceptable Use Policy ───────────────────────────────────────────────

const AUP: PolicyTemplate = {
  code: 'POL-AUP',
  category: 'acceptable-use',
  nameEn: 'Acceptable Use Policy',
  nameAr: 'سياسة الاستخدام المقبول',
  blurbEn: 'Defines what constitutes acceptable and prohibited use of [ORGANISATION] systems and networks, including BYOD and remote work expectations.',
  blurbAr: 'تحدّد الاستخدامات المقبولة والمحظورة لأنظمة وشبكات [اسم المنشأة]، بما في ذلك توقّعات سياسة "أحضر جهازك الخاص" والعمل عن بُعد.',
  frameworkAnchors: ['NCA-ECC-V3'],
  version: '1.2',
  lastReviewed: '2025-06-01',
  readMinutes: 8,
  sections: [
    {
      code: '1', titleEn: 'Acceptable Use', titleAr: 'الاستخدام المقبول',
      bodyEn: 'You may use [ORGANISATION] systems for legitimate business purposes. Limited personal use is permitted provided it does not interfere with work, consume excessive resources or violate this policy.',
      bodyAr: 'يجوز لك استخدام أنظمة [اسم المنشأة] لأغراض العمل المشروعة. يُسمح بالاستخدام الشخصي المحدود شريطة ألاّ يؤثّر على العمل أو يستهلك موارد مفرطة أو يخالف هذه السياسة.',
    },
    {
      code: '2', titleEn: 'Prohibited Activities', titleAr: 'الأنشطة المحظورة',
      bodyEn: 'You may not use [ORGANISATION] systems to download or distribute illegal content, attempt to bypass security controls, share credentials, install unapproved software or process classified data on personal devices.',
      bodyAr: 'لا يجوز لك استخدام أنظمة [اسم المنشأة] لتنزيل أو توزيع محتوى غير قانوني، أو محاولة تجاوز الضوابط الأمنية، أو مشاركة بيانات الاعتماد، أو تثبيت برامج غير معتمدة، أو معالجة البيانات المُصنّفة على أجهزة شخصية.',
    },
    {
      code: '3', titleEn: 'Bring Your Own Device (BYOD)', titleAr: 'أحضر جهازك الخاص',
      bodyEn: 'Personal devices may access [ORGANISATION] resources only after enrolling in the Mobile Device Management (MDM) platform. The MDM enforces encryption, screen lock, remote wipe and prohibits jailbroken devices.',
      bodyAr: 'يُسمح للأجهزة الشخصية بالوصول إلى موارد [اسم المنشأة] فقط بعد تسجيلها في منصة إدارة الأجهزة المحمولة (MDM). تفرض المنصة التشفير وقفل الشاشة والمسح عن بُعد وتمنع الأجهزة المُختَرَقة.',
    },
    {
      code: '4', titleEn: 'Remote Work', titleAr: 'العمل عن بُعد',
      bodyEn: 'When working remotely, connect to corporate resources only via the approved VPN. Do not use public Wi-Fi without VPN. Lock your device when unattended and ensure printed materials are secured.',
      bodyAr: 'عند العمل عن بُعد، اتّصل بالموارد المؤسسية فقط عبر شبكة VPN المعتمدة. لا تستخدم شبكات Wi-Fi العامة بدون VPN. أقفل جهازك عند تركه بلا مراقبة وتأكّد من تأمين أيّ مطبوعات.',
    },
    {
      code: '5', titleEn: 'Monitoring', titleAr: 'الرقابة',
      bodyEn: '[ORGANISATION] monitors usage of corporate systems for security, performance and compliance purposes in accordance with the PDPL. Monitoring is proportionate, transparent and disclosed to employees through the Trust Center.',
      bodyAr: 'تراقب [اسم المنشأة] استخدام الأنظمة المؤسسية لأغراض الأمن والأداء والالتزام وفق نظام حماية البيانات الشخصية. الرقابة متناسبة وشفافة ومُعلَنة للموظفين عبر مركز الثقة.',
    },
  ],
};

// ─── 4 · Incident Response Policy ────────────────────────────────────────────

const IR_POLICY: PolicyTemplate = {
  code: 'POL-IR',
  category: 'incident-response',
  nameEn: 'Cybersecurity Incident Response Policy',
  nameAr: 'سياسة الاستجابة للحوادث السيبرانية',
  blurbEn: 'Nine-stage incident workflow from Detection to Lessons Learned, with NCA reporting deadlines and crisis management thresholds.',
  blurbAr: 'سير عمل من تسع مراحل من الاكتشاف إلى الدروس المستفادة، مع مواعيد إبلاغ الهيئة الوطنية للأمن السيبراني وحدود تفعيل إدارة الأزمات.',
  frameworkAnchors: ['NCA-ECC-V3', 'SAMA-RBSF'],
  version: '3.0',
  lastReviewed: '2025-10-12',
  readMinutes: 16,
  sections: [
    {
      code: '1', titleEn: 'Incident Lifecycle', titleAr: 'دورة حياة الحادثة',
      bodyEn: 'Every cybersecurity incident moves through nine stages: Detection → Triage → Containment → Eradication → Recovery → Reporting → Communication → Closure → Lessons Learned. The CSIRT lead drives the lifecycle.',
      bodyAr: 'تمرّ كل حادثة أمن سيبراني بتسع مراحل: الاكتشاف ← الفرز ← الاحتواء ← الاستئصال ← التعافي ← الإبلاغ ← التواصل ← الإغلاق ← الدروس المستفادة. يقود قائد فريق الاستجابة (CSIRT) دورة الحياة.',
    },
    {
      code: '2', titleEn: 'Severity Classification', titleAr: 'تصنيف الخطورة',
      bodyEn: 'Severity 1 (critical): nation-state actor, ransomware on production, breach of >100k records. Severity 2 (high): targeted attempt blocked. Severity 3 (medium): single-host malware. Severity 4 (low): policy violation without compromise.',
      bodyAr: 'الخطورة 1 (حرجة): جهة فاعلة على مستوى دولة، فدية على بيئة الإنتاج، خرق يفوق 100 ألف سجل. الخطورة 2 (عالية): محاولة موجَّهة تمّ صدّها. الخطورة 3 (متوسطة): برمجية خبيثة على جهاز واحد. الخطورة 4 (منخفضة): مخالفة سياسة دون اختراق.',
    },
    {
      code: '3', titleEn: 'Regulatory Notifications', titleAr: 'الإخطارات النظامية',
      bodyEn: 'Severity 1 incidents are reported to the National Cybersecurity Authority within 1 hour of confirmation per the NCA Incident Reporting Guidelines. PDPL-impacting incidents are reported to SDAIA within 72 hours. SAMA-licensed entities additionally notify SAMA within 4 hours.',
      bodyAr: 'تُبلَّغ الحوادث من الخطورة 1 إلى الهيئة الوطنية للأمن السيبراني خلال ساعة واحدة من التأكيد وفق إرشادات الإبلاغ. تُبلَّغ الحوادث المؤثّرة على البيانات الشخصية إلى هيئة سدايا خلال 72 ساعة. تُبلَّغ الجهات المرخّصة من ساما إضافةً ساما خلال 4 ساعات.',
    },
    {
      code: '4', titleEn: 'Crisis Management Activation', titleAr: 'تفعيل إدارة الأزمات',
      bodyEn: 'Crisis management is activated when an incident threatens business continuity, the [ORGANISATION] brand, or critical national infrastructure. The CEO chairs the crisis room. Communications go through approved channels only.',
      bodyAr: 'تُفعَّل إدارة الأزمات عند تهديد الحادثة لاستمرارية الأعمال أو علامة [اسم المنشأة] أو البنية التحتية الحيوية للدولة. يترأّس الرئيس التنفيذي غرفة الأزمات. تمرّ الاتصالات عبر القنوات المعتمدة حصراً.',
    },
    {
      code: '5', titleEn: 'Evidence Preservation', titleAr: 'حفظ الأدلّة',
      bodyEn: 'Memory snapshots, disk images, log exports and network captures are preserved with chain-of-custody documentation. Evidence is held for 7 years or as directed by NCA / SAMA / Public Prosecution.',
      bodyAr: 'تُحفظ لقطات الذاكرة وصور الأقراص ومُخرجات السجلّات وأَسْر حركة الشبكة مع توثيق سلسلة الحيازة. تُحفظ الأدلّة لمدة 7 سنوات أو حسب توجيهات الهيئة الوطنية للأمن السيبراني / ساما / النيابة العامة.',
    },
    {
      code: '6', titleEn: 'Post-Incident Review', titleAr: 'مراجعة ما بعد الحادثة',
      bodyEn: 'A Severity 1 or 2 incident triggers a post-incident review within 14 days, attended by the CISO, COO, CTO and Internal Audit. CAPA actions are tracked to closure in the GRC platform.',
      bodyAr: 'يُجرى استعراض ما بعد الحادثة لكل حادثة من الخطورة 1 أو 2 خلال 14 يوماً بحضور رئيس أمن المعلومات والرئيس التنفيذي للعمليات والرئيس التنفيذي للتقنية والمراجعة الداخلية. تُتابع إجراءات CAPA حتى الإغلاق في منصة الحوكمة والمخاطر والامتثال.',
    },
  ],
};

// ─── 5 · Business Continuity Policy ──────────────────────────────────────────

const BCM_POLICY: PolicyTemplate = {
  code: 'POL-BCM',
  category: 'business-continuity',
  nameEn: 'Business Continuity Management Policy',
  nameAr: 'سياسة إدارة استمرارية الأعمال',
  blurbEn: 'NCEMA-aligned BCM policy — RTO/RPO targets per critical process, Riyadh ↔ Jeddah DR replication and quarterly drill calendar.',
  blurbAr: 'سياسة استمرارية الأعمال المتسقة مع المركز الوطني لإدارة الكوارث — أهداف الاستعادة لكل عملية حيوية، النسخ المتزامن بين الرياض وجدّة، وجدول تمارين فصلي.',
  frameworkAnchors: ['NCEMA', 'SAMA-OPRES', 'NCA-ECC-V3'],
  version: '2.0',
  lastReviewed: '2025-07-30',
  readMinutes: 11,
  sections: [
    {
      code: '1', titleEn: 'Critical Process Inventory', titleAr: 'جرد العمليات الحيوية',
      bodyEn: 'Every business process is rated Tier-1 (national impact, e.g. payment switch), Tier-2 (regulatory impact), Tier-3 (customer impact) or Tier-4 (back-office). The CIO maintains the inventory.',
      bodyAr: 'تُصنَّف كل عملية إلى الفئة 1 (أثر وطني، مثل قاطع الدفع)، أو الفئة 2 (أثر تنظيمي)، أو الفئة 3 (أثر العملاء)، أو الفئة 4 (الدعم). يحافظ الرئيس التنفيذي للمعلومات على الجرد.',
    },
    {
      code: '2', titleEn: 'Recovery Targets', titleAr: 'أهداف الاستعادة',
      bodyEn: 'Tier-1 processes target RTO 4 hours and RPO 15 minutes. Tier-2 target RTO 24 hours and RPO 1 hour. Tier-3 target RTO 72 hours. Targets are validated quarterly through DR drills.',
      bodyAr: 'الفئة 1 تستهدف زمن استعادة 4 ساعات ونقطة استعادة 15 دقيقة. الفئة 2 تستهدف 24 ساعة و1 ساعة. الفئة 3 تستهدف 72 ساعة. تُتحقَّق الأهداف ربع سنوياً عبر تمارين التعافي.',
    },
    {
      code: '3', titleEn: 'Replication Architecture', titleAr: 'بنية النسخ المتزامن',
      bodyEn: 'Production data is replicated synchronously between the Riyadh primary and Jeddah secondary. Backups are stored offline in a third region (Dammam) with 7-year retention to satisfy SAMA Operational Resilience requirements.',
      bodyAr: 'تُنسخ بيانات الإنتاج بشكل متزامن بين الرياض (الأساسي) وجدّة (الثانوي). تُخزَّن النسخ الاحتياطية بلا اتصال في منطقة ثالثة (الدمام) لمدة 7 سنوات استيفاءً لمتطلّبات إطار المرونة التشغيلية لساما.',
    },
    {
      code: '4', titleEn: 'Drill Calendar', titleAr: 'جدول التمارين',
      bodyEn: 'Tabletop exercise: every quarter. Functional drill (failover only): semi-annually. Full DR drill (production swing): annually. Drill reports go to the Audit Committee within 30 days.',
      bodyAr: 'تمرين على الطاولة: كل ثلاثة أشهر. تمرين وظيفي (تبديل احتياطي فقط): نصف سنوي. تمرين تعافٍ كامل (تشغيل من الموقع البديل): سنوي. تُقدَّم تقارير التمرين إلى لجنة المراجعة خلال 30 يوماً.',
    },
    {
      code: '5', titleEn: 'Crisis Communications', titleAr: 'التواصل أثناء الأزمة',
      bodyEn: 'During a Tier-1 disruption, customer notifications are sent within 30 minutes via SMS, the mobile app and the corporate website. Regulator notifications follow the timelines in the Incident Response Policy.',
      bodyAr: 'أثناء أيّ تعطّل من الفئة 1، تُرسل إشعارات العملاء خلال 30 دقيقة عبر الرسائل النصية والتطبيق والموقع الإلكتروني. تتّبع إشعارات الجهة التنظيمية المواعيد المحدّدة في سياسة الاستجابة للحوادث.',
    },
  ],
};

// ─── 6 · Third-Party / Outsourcing Policy ────────────────────────────────────

const TPRM_POLICY: PolicyTemplate = {
  code: 'POL-TPRM',
  category: 'third-party',
  nameEn: 'Third-Party Risk Management Policy',
  nameAr: 'سياسة إدارة مخاطر الأطراف الثالثة',
  blurbEn: 'SAMA outsourcing rules + NCA third-party cybersecurity controls — onboarding due diligence, ongoing monitoring, exit planning.',
  blurbAr: 'قواعد ساما للإسناد + ضوابط الأمن السيبراني للأطراف الثالثة من الهيئة الوطنية — العناية الواجبة عند الاستقطاب، والمتابعة المستمرة، وخطط الخروج.',
  frameworkAnchors: ['NCA-ECC-V3', 'SAMA-RBSF'],
  version: '1.5',
  lastReviewed: '2025-04-19',
  readMinutes: 10,
  sections: [
    {
      code: '1', titleEn: 'Tiering', titleAr: 'التصنيف',
      bodyEn: 'Vendors are tiered based on access to confidential data, criticality of service and SAR-equivalent annual spend. Tier-1 vendors require board notification, Tier-2 senior management, Tier-3 line management.',
      bodyAr: 'تُصنَّف الأطراف الثالثة وفقاً للوصول إلى البيانات الخاصة وحرجية الخدمة والإنفاق السنوي المعادل بالريال. الفئة 1 تستلزم إشعار مجلس الإدارة، والفئة 2 الإدارة العليا، والفئة 3 الإدارة المباشرة.',
    },
    {
      code: '2', titleEn: 'Onboarding Due Diligence', titleAr: 'العناية الواجبة عند الاستقطاب',
      bodyEn: 'Tier-1 and Tier-2 vendors complete a security questionnaire (NCA ECC-mapped), submit ISO 27001 / SOC 2 certificates, undergo financial review, and confirm Tasattur compliance. Saudization (Nitaqat) verified.',
      bodyAr: 'تُكمل الأطراف من الفئة 1 و2 استبيان الأمن السيبراني (مرتبط بضوابط الهيئة الوطنية)، وتُقدّم شهادات ISO 27001 / SOC 2، وتخضع لمراجعة مالية، وتؤكّد التزام نظام مكافحة التستّر، ويُتحقَّق من السعودة (نطاقات).',
    },
    {
      code: '3', titleEn: 'Concentration Limits', titleAr: 'حدود التركّز',
      bodyEn: 'No single Tier-1 vendor may carry more than 30% of [ORGANISATION] critical IT spend. Cloud providers are dual-region (Riyadh + Jeddah). Concentration is reviewed quarterly by the Procurement Committee.',
      bodyAr: 'لا يجوز أن يحمل أيّ مورّد من الفئة 1 أكثر من 30٪ من إنفاق تقنية المعلومات الحيوي لـ[اسم المنشأة]. مزوّدو الخدمات السحابية يعملون بمنطقتين (الرياض + جدّة). يُراجَع التركّز ربع سنوياً من قِبل لجنة المشتريات.',
    },
    {
      code: '4', titleEn: 'Ongoing Monitoring', titleAr: 'المتابعة المستمرة',
      bodyEn: 'Tier-1 vendors are reassessed annually on-site. Tier-2 quarterly via questionnaire and SLA review. Continuous control monitoring (CCM) flags exceptions automatically and creates CAPA tasks.',
      bodyAr: 'تُعاد دراسة الفئة 1 سنوياً ميدانياً. الفئة 2 ربع سنوياً عبر الاستبيان ومراجعة اتفاقية مستوى الخدمة. تُحدِّد المراقبة المستمرة للضوابط الاستثناءات تلقائياً وتُولِّد مهام CAPA.',
    },
    {
      code: '5', titleEn: 'Exit Planning', titleAr: 'تخطيط الخروج',
      bodyEn: 'Every Tier-1 contract includes a documented exit plan tested every two years. Plans cover data return / destruction, knowledge transfer and continuity bridge to a successor.',
      bodyAr: 'يتضمّن كل عقد من الفئة 1 خطة خروج موثَّقة تُختبر كل سنتين. تغطّي الخطط استعادة / إتلاف البيانات ونقل المعرفة وجسر الاستمرارية إلى الخَلَف.',
    },
  ],
};

// ─── 7 · Code of Conduct ─────────────────────────────────────────────────────

const CODE_OF_CONDUCT: PolicyTemplate = {
  code: 'POL-COC',
  category: 'code-of-conduct',
  nameEn: 'Code of Conduct',
  nameAr: 'مدوّنة السلوك',
  blurbEn: 'Sets ethical expectations, conflict-of-interest rules, anti-bribery (Nazaha-aligned) and the Speak-Up channel.',
  blurbAr: 'تحدّد التوقّعات الأخلاقية وقواعد تعارض المصالح ومكافحة الرشوة (متسقة مع نزاهة) وقناة "تحدّث".',
  frameworkAnchors: ['CMA-CGR'],
  version: '4.1',
  lastReviewed: '2025-03-01',
  readMinutes: 9,
  sections: [
    {
      code: '1', titleEn: 'Integrity', titleAr: 'النزاهة',
      bodyEn: 'Employees act with honesty, transparency and respect for the laws of the Kingdom of Saudi Arabia and the values of [ORGANISATION] in every decision and interaction.',
      bodyAr: 'يتصرّف الموظفون بأمانة وشفافية واحترام لأنظمة المملكة العربية السعودية وقيم [اسم المنشأة] في كل قرار وتفاعل.',
    },
    {
      code: '2', titleEn: 'Conflict of Interest', titleAr: 'تعارض المصالح',
      bodyEn: 'Employees must disclose any actual or potential conflict of interest — including family relationships with vendors, side businesses or external board seats — to their line manager and the Compliance Officer.',
      bodyAr: 'يجب على الموظفين الإفصاح عن أيّ تعارض مصالح فعلي أو محتمل — بما في ذلك صلات القرابة بمورّدين أو أعمال جانبية أو عضويات مجالس خارجية — للرئيس المباشر ومسؤول الالتزام.',
    },
    {
      code: '3', titleEn: 'Anti-Bribery and Corruption', titleAr: 'مكافحة الرشوة والفساد',
      bodyEn: 'Bribes, kickbacks, facilitation payments and gifts above SAR 500 are strictly prohibited. The Anti-Corruption Authority (Nazaha) and Public Prosecution have jurisdiction over violations under Saudi anti-bribery law.',
      bodyAr: 'تُحظَر الرشاوى والعمولات والمدفوعات التيسيرية والهدايا التي تتجاوز 500 ريال حظراً مطلقاً. تختص هيئة الرقابة ومكافحة الفساد (نزاهة) والنيابة العامة بالمخالفات وفقاً لنظام مكافحة الرشوة.',
    },
    {
      code: '4', titleEn: 'Speak-Up Channel', titleAr: 'قناة "تحدَّث"',
      bodyEn: 'Suspected wrongdoing is reported via the anonymous Speak-Up portal hosted at /portal/speakup or by phone to the Audit Committee chair. Retaliation against reporters is itself a code violation.',
      bodyAr: 'يُبلَّغ عن أيّ مخالفات مشتبه بها عبر بوابة "تحدّث" المجهولة على /portal/speakup أو هاتفياً لرئيس لجنة المراجعة. الانتقام من المُبلِّغين بحدّ ذاته انتهاك للمدوّنة.',
    },
  ],
};

// ─── 8 · AI Governance Policy ────────────────────────────────────────────────

const AI_POLICY: PolicyTemplate = {
  code: 'POL-AI',
  category: 'ai-governance',
  nameEn: 'AI Governance Policy',
  nameAr: 'سياسة حوكمة الذكاء الاصطناعي',
  blurbEn: 'Implements SDAIA AI Ethics Principles — risk-tiering, fairness assessments, human-in-the-loop and model card requirements.',
  blurbAr: 'تنفّذ مبادئ سدايا لأخلاقيات الذكاء الاصطناعي — التصنيف بحسب المخاطر، تقييمات العدالة، الإشراف البشري، ومتطلّبات بطاقة النموذج.',
  frameworkAnchors: ['SDAIA-AI', 'PDPL'],
  version: '1.0',
  lastReviewed: '2025-11-08',
  readMinutes: 10,
  sections: [
    {
      code: '1', titleEn: 'Use Case Tiering', titleAr: 'تصنيف حالات الاستخدام',
      bodyEn: 'Every AI use case is registered and tiered as Acceptable (low risk, e.g. document summarisation), High-Risk (employment, credit, healthcare decisions) or Prohibited (social scoring, mass surveillance).',
      bodyAr: 'تُسجَّل كل حالة استخدام للذكاء الاصطناعي وتُصنَّف إلى مقبول (مخاطر منخفضة، مثل تلخيص المستندات)، أو عالي المخاطر (قرارات التوظيف والائتمان والرعاية الصحية)، أو محظور (التصنيف الاجتماعي والمراقبة الجماعية).',
    },
    {
      code: '2', titleEn: 'Fairness and Bias', titleAr: 'العدالة والتحيّز',
      bodyEn: 'High-risk use cases undergo fairness assessment before deployment and at least annually thereafter, testing across protected groups (gender, nationality, region within Saudi Arabia, language).',
      bodyAr: 'تخضع حالات الاستخدام عالية المخاطر لتقييم العدالة قبل النشر وسنوياً على الأقل بعد ذلك، باختبار التأثير على الفئات المحمية (الجنس، الجنسية، المنطقة داخل المملكة، اللغة).',
    },
    {
      code: '3', titleEn: 'Human Oversight', titleAr: 'الإشراف البشري',
      bodyEn: 'High-risk decisions cannot be fully automated. A human reviewer with authority to override the AI must be in the loop and the override decision is logged for audit.',
      bodyAr: 'لا يجوز أتمتة القرارات عالية المخاطر بالكامل. يجب أن يشترك مراجِع بشري لديه صلاحية تجاوز قرار الذكاء الاصطناعي، ويُسجَّل قرار التجاوز للمراجعة.',
    },
    {
      code: '4', titleEn: 'Model Cards and Disclosure', titleAr: 'بطاقات النموذج والإفصاح',
      bodyEn: 'Each model in production has a published model card describing inputs, outputs, training data, known limitations and intended use. Users interacting with an AI system are clearly informed.',
      bodyAr: 'لكل نموذج في الإنتاج بطاقة نموذج منشورة تصف المدخلات والمخرجات وبيانات التدريب والحدود المعروفة والاستخدام المقصود. يُبلَّغ المستخدمون بوضوح عند تفاعلهم مع نظام ذكاء اصطناعي.',
    },
    {
      code: '5', titleEn: 'Data Provenance', titleAr: 'مصدر البيانات',
      bodyEn: 'Training and fine-tuning data must comply with PDPL (lawful basis), respect copyright, and exclude data scraped without consent. Provenance is documented in the model card.',
      bodyAr: 'يجب أن تلتزم بيانات التدريب والضبط الدقيق بنظام حماية البيانات الشخصية (السند النظامي)، وتحترم حقوق المؤلف، وتستبعد البيانات المُستخرَجة دون موافقة. يُوثَّق المصدر في بطاقة النموذج.',
    },
  ],
};

export const POLICY_TEMPLATES: PolicyTemplate[] = [
  INFOSEC_POLICY, PRIVACY_POLICY, AUP, IR_POLICY,
  BCM_POLICY, TPRM_POLICY, CODE_OF_CONDUCT, AI_POLICY,
];

export const POLICY_CATEGORIES: { code: PolicyCategory; nameEn: string; nameAr: string }[] = [
  { code: 'information-security', nameEn: 'Information Security', nameAr: 'أمن المعلومات' },
  { code: 'cybersecurity',         nameEn: 'Cybersecurity',         nameAr: 'الأمن السيبراني' },
  { code: 'privacy',               nameEn: 'Privacy',               nameAr: 'الخصوصية' },
  { code: 'acceptable-use',        nameEn: 'Acceptable Use',        nameAr: 'الاستخدام المقبول' },
  { code: 'incident-response',     nameEn: 'Incident Response',     nameAr: 'الاستجابة للحوادث' },
  { code: 'business-continuity',   nameEn: 'Business Continuity',   nameAr: 'استمرارية الأعمال' },
  { code: 'third-party',           nameEn: 'Third-Party',           nameAr: 'الأطراف الثالثة' },
  { code: 'access-control',        nameEn: 'Access Control',        nameAr: 'التحكّم بالوصول' },
  { code: 'code-of-conduct',       nameEn: 'Code of Conduct',       nameAr: 'مدوّنة السلوك' },
  { code: 'ai-governance',         nameEn: 'AI Governance',         nameAr: 'حوكمة الذكاء الاصطناعي' },
];

// ─── Cloned policy persistence ───────────────────────────────────────────────

export interface ClonedPolicy {
  /** New unique code, e.g. "MYORG-POL-INFOSEC-2026-01". */
  cloneCode: string;
  /** Code of the source template. */
  sourceCode: string;
  /** Cloned at this timestamp. */
  clonedAt: string;
  /** Snapshot of organisation name baked in at clone time. */
  organisationNameEn: string;
  organisationNameAr: string;
}

const KEY = 'grc.policy-templates.cloned.v1';
const EVENT = 'grc.policy-templates.changed';

export function loadCloned(): ClonedPolicy[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return [];
    return JSON.parse(raw) as ClonedPolicy[];
  } catch { return []; }
}

export function clonePolicy(template: PolicyTemplate, orgEn: string, orgAr: string): ClonedPolicy {
  const existing = loadCloned();
  const seq = existing.filter(c => c.sourceCode === template.code).length + 1;
  const year = new Date().getFullYear();
  const slug = (orgEn || 'TENANT').replace(/[^A-Z0-9]/gi, '').slice(0, 6).toUpperCase();
  const cloned: ClonedPolicy = {
    cloneCode: `${slug}-${template.code}-${year}-${String(seq).padStart(2, '0')}`,
    sourceCode: template.code,
    clonedAt: new Date().toISOString(),
    organisationNameEn: orgEn,
    organisationNameAr: orgAr,
  };
  const next = [...existing, cloned];
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
  return cloned;
}

export function deleteCloned(cloneCode: string): void {
  const next = loadCloned().filter(c => c.cloneCode !== cloneCode);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribePolicyTemplates(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

/** Replace placeholders ([ORGANISATION] / [اسم المنشأة]) with live names. */
export function renderSection(section: PolicySection, orgEn: string, orgAr: string, lang: 'en' | 'ar'): string {
  const body = lang === 'ar' ? section.bodyAr : section.bodyEn;
  return body
    .replace(/\[ORGANISATION\]/g, orgEn || 'ORGANISATION')
    .replace(/\[اسم المنشأة\]/g, orgAr || 'اسم المنشأة');
}
