// Per-kind intake form schemas — DD-59.
//
// The intake substrate (intake.ts) defines the catalog of intake kinds
// and their lifecycle metadata. This file describes the *form shape*
// for each kind: what fields the user fills, in what order, with what
// validation. A single shared IntakeFormRenderer walks the schema to
// render the right control per field.
//
// Naming + field choices benchmarked against:
//   ServiceNow IRM Service Catalog items
//   OneTrust Privacy intake forms (DSR / PIA / DPIA)
//   NAVEX EthicsPoint (whistleblower)
//   ISACA Continuous Controls Monitoring
//   ISO 27001 §6.8 — reporting information security events
//   PDPL Article 27 — breach notification
//   ISO 31000 §5.4.2 — risk identification
//   ISO 27001 §A.5.36 — exception process

import type { IntakeKind } from './intake';

export type IntakeFieldKind =
  | 'text'             // single-line text
  | 'textarea'         // multi-line description
  | 'select'           // single-pick from options
  | 'radio-cards'      // visual radio cards (primary categories)
  | 'severity-radio'   // 4 columns: LOW/MEDIUM/HIGH/CRITICAL
  | 'date'             // ISO date
  | 'datetime'         // ISO datetime-local
  | 'checkbox';        // single boolean

export interface IntakeFieldOption {
  value: string;
  labelEn: string;
  labelAr: string;
}

export interface IntakeFieldDef {
  key: string;
  kind: IntakeFieldKind;
  labelEn: string;
  labelAr: string;
  required?: boolean;
  placeholderEn?: string;
  placeholderAr?: string;
  hintEn?: string;
  hintAr?: string;
  /** For select / radio-cards. */
  options?: IntakeFieldOption[];
  /** Span 1-12 for grid layout. Default 12 (full width). */
  span?: number;
}

const SEVERITY_OPTS: IntakeFieldOption[] = [
  { value: 'LOW',      labelEn: 'Low',      labelAr: 'منخفضة' },
  { value: 'MEDIUM',   labelEn: 'Medium',   labelAr: 'متوسطة' },
  { value: 'HIGH',     labelEn: 'High',     labelAr: 'مرتفعة' },
  { value: 'CRITICAL', labelEn: 'Critical', labelAr: 'حرجة' },
];

export const INTAKE_FORM_FIELDS: Partial<Record<IntakeKind, IntakeFieldDef[]>> = {
  // ── Employee security-event report — NCA ECC 6.1, ISO 27001 §6.8 ──
  SECURITY_INCIDENT: [
    { key: 'category', kind: 'radio-cards',
      labelEn: 'What kind of event are you reporting?',
      labelAr: 'ما نوع الحدث الذي تُبلغ عنه؟',
      required: true,
      options: [
        { value: 'PHISHING',          labelEn: 'Phishing email',          labelAr: 'رسالة تصيّد' },
        { value: 'MALWARE',           labelEn: 'Suspected malware',       labelAr: 'برنامج خبيث مشتبه به' },
        { value: 'LOST_DEVICE',       labelEn: 'Lost / stolen device',    labelAr: 'فقدان / سرقة جهاز' },
        { value: 'UNAUTHORISED_ACCESS', labelEn: 'Unauthorised access',   labelAr: 'وصول غير مُصرَّح به' },
        { value: 'DATA_LEAK',         labelEn: 'Data leak / exposure',    labelAr: 'تسريب / كشف بيانات' },
        { value: 'CREDENTIAL_COMPROMISE', labelEn: 'Compromised credentials', labelAr: 'اختراق بيانات اعتماد' },
        { value: 'SUSPICIOUS_ACTIVITY', labelEn: 'Suspicious activity',   labelAr: 'نشاط مريب' },
        { value: 'OTHER',             labelEn: 'Other',                   labelAr: 'أخرى' },
      ] },
    { key: 'summary', kind: 'textarea', required: true,
      labelEn: 'Describe what you observed',
      labelAr: 'صف ما لاحظته',
      placeholderEn: 'Be specific. What happened, when, where, who/what was affected. Avoid speculation.',
      placeholderAr: 'كن محدّداً. ماذا حدث، ومتى، وأين، ومن/ما الذي تأثّر. تجنّب التخمين.',
      hintEn: 'Minimum 30 characters. Forensic detail helps the CSIRT triage faster.',
      hintAr: 'الحدّ الأدنى 30 حرفاً. التفاصيل تساعد فريق CSIRT على الفرز بسرعة أكبر.' },
    { key: 'observedAt', kind: 'datetime',
      labelEn: 'When did you observe it?',
      labelAr: 'متى لاحظته؟',
      required: true, span: 6 },
    { key: 'systemsAffected', kind: 'text',
      labelEn: 'Systems / applications affected',
      labelAr: 'الأنظمة / التطبيقات المتأثرة',
      placeholderEn: 'e.g. Email gateway, Salesforce, VPN, internal portal',
      placeholderAr: 'مثلاً: بوابة البريد، Salesforce، VPN، البوابة الداخلية',
      span: 6 },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'How urgent does this feel?',
      labelAr: 'ما مدى إلحاح هذا الحدث؟',
      hintEn: 'Self-assessed. CSIRT may re-classify after triage.',
      hintAr: 'تقييم ذاتي. قد يعيد فريق CSIRT تصنيفه عند الفرز.',
      options: SEVERITY_OPTS },
  ],

  // ── Internal privacy-breach report — PDPL Art. 27 ──
  PRIVACY_BREACH: [
    { key: 'category', kind: 'radio-cards', required: true,
      labelEn: 'What kind of breach?',
      labelAr: 'ما نوع الخرق؟',
      options: [
        { value: 'LOST_DOCUMENT',  labelEn: 'Lost paper / file',     labelAr: 'فقدان مستند ورقي' },
        { value: 'MISDIRECTED',    labelEn: 'Mis-directed email/post', labelAr: 'إرسال خاطئ' },
        { value: 'OVER_SHARING',   labelEn: 'Over-shared data',       labelAr: 'مشاركة مُفرِطة للبيانات' },
        { value: 'EXPOSED_DATABASE', labelEn: 'Exposed database / config', labelAr: 'قاعدة بيانات / إعدادات مكشوفة' },
        { value: 'INSIDER_MISUSE', labelEn: 'Insider misuse',         labelAr: 'إساءة من داخلي' },
        { value: 'CYBER_DRIVEN',   labelEn: 'Cyber-driven (linked to incident)', labelAr: 'مُحرَّك سيبرانياً' },
        { value: 'OTHER',          labelEn: 'Other',                  labelAr: 'أخرى' },
      ] },
    { key: 'summary', kind: 'textarea', required: true,
      labelEn: 'What happened?',
      labelAr: 'ماذا حدث؟',
      placeholderEn: 'Describe what data, who accessed it (if known), and how you discovered the breach.',
      placeholderAr: 'صف البيانات المعنية، ومن وصل إليها (إن عُرف)، وكيف اكتشفت الخرق.' },
    { key: 'dataCategories', kind: 'text', required: true,
      labelEn: 'Categories of personal data involved',
      labelAr: 'فئات البيانات الشخصية المعنية',
      placeholderEn: 'e.g. customer names, ID numbers, financial, health, biometric',
      placeholderAr: 'مثلاً: أسماء عملاء، أرقام هوية، مالية، صحية، حيوية' },
    { key: 'recordsCount', kind: 'text', span: 6,
      labelEn: 'Approximate number of data subjects affected',
      labelAr: 'العدد التقريبي لأصحاب البيانات المتأثرين',
      placeholderEn: 'e.g. 12, ~200, unknown',
      placeholderAr: 'مثلاً: 12، نحو 200، غير معروف' },
    { key: 'discoveredAt', kind: 'datetime', required: true, span: 6,
      labelEn: 'When did you discover the breach?',
      labelAr: 'متى اكتشفت الخرق؟' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'How severe do you assess the impact on data subjects?',
      labelAr: 'كيف تُقدِّر شدّة الأثر على أصحاب البيانات؟',
      options: SEVERITY_OPTS },
  ],

  // ── Risk-issue intake — ISO 31000 §5.4.2, COSO ERM C4 ──
  RISK_ISSUE: [
    { key: 'category', kind: 'radio-cards', required: true,
      labelEn: 'Which risk domain does this affect?',
      labelAr: 'أي مجال مخاطر يتأثر؟',
      options: [
        { value: 'CYBER',        labelEn: 'Cyber / IT',            labelAr: 'سيبراني / تقني' },
        { value: 'OPERATIONAL',  labelEn: 'Operational',           labelAr: 'تشغيلي' },
        { value: 'COMPLIANCE',   labelEn: 'Compliance / regulatory', labelAr: 'امتثال / تنظيمي' },
        { value: 'FINANCIAL',    labelEn: 'Financial',             labelAr: 'مالي' },
        { value: 'STRATEGIC',    labelEn: 'Strategic',             labelAr: 'استراتيجي' },
        { value: 'THIRD_PARTY',  labelEn: 'Third-party / vendor',  labelAr: 'أطراف ثالثة' },
        { value: 'PRIVACY',      labelEn: 'Privacy',               labelAr: 'خصوصية' },
        { value: 'BCM',          labelEn: 'BCM / resilience',      labelAr: 'استمرارية' },
      ] },
    { key: 'summary', kind: 'textarea', required: true,
      labelEn: 'Describe the emerging risk',
      labelAr: 'صف المخاطرة الناشئة',
      placeholderEn: 'Cause → event → impact. What would happen if this materialised?',
      placeholderAr: 'السبب → الحدث → الأثر. ماذا سيحدث لو تحقّقت؟' },
    { key: 'likelihoodSelfAssessed', kind: 'severity-radio', span: 6,
      labelEn: 'How likely is this in the next 12 months?',
      labelAr: 'ما احتمال حدوثها خلال 12 شهراً؟',
      options: SEVERITY_OPTS },
    { key: 'impactSelfAssessed', kind: 'severity-radio', span: 6,
      labelEn: 'How severe would the impact be?',
      labelAr: 'ما شدّة الأثر؟',
      options: SEVERITY_OPTS },
    { key: 'recommendedTreatment', kind: 'textarea',
      labelEn: 'Suggested treatment (optional)',
      labelAr: 'المعالجة المقترحة (اختياري)',
      placeholderEn: 'How would you mitigate this risk? Mention any controls already in place.',
      placeholderAr: 'كيف تقترح معالجتها؟ اذكر أي ضوابط قائمة.' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'Overall severity (likelihood × impact)',
      labelAr: 'الشدّة الإجمالية (احتمال × أثر)',
      options: SEVERITY_OPTS },
  ],

  // ── Control observation / failure report — ISO 27001 §10.2 ──
  CONTROL_OBSERVATION: [
    { key: 'controlReference', kind: 'text', required: true,
      labelEn: 'Control reference (code, name, or description)',
      labelAr: 'مرجع الضابط (الرمز، الاسم، أو الوصف)',
      placeholderEn: 'e.g. AC-2.1.3 quarterly access recertification',
      placeholderAr: 'مثلاً: AC-2.1.3 إعادة اعتماد الوصول ربع السنوية' },
    { key: 'observation', kind: 'textarea', required: true,
      labelEn: 'What is not working as designed?',
      labelAr: 'ما الذي لا يعمل كما هو مُصمَّم؟',
      placeholderEn: 'Describe the gap between the documented control and what you observed in practice.',
      placeholderAr: 'صف الفجوة بين الضابط الموثَّق وما لاحظته فعلاً.' },
    { key: 'evidenceNote', kind: 'textarea',
      labelEn: 'What evidence have you seen?',
      labelAr: 'ما الأدلّة التي رأيتها؟',
      placeholderEn: 'Logs, screenshots, recent test results, sample of failed runs.',
      placeholderAr: 'سجلات، لقطات شاشة، نتائج اختبار حديثة، عيّنات إخفاقات.' },
    { key: 'systemsAffected', kind: 'text',
      labelEn: 'Scope of impact (systems / processes / business units)',
      labelAr: 'نطاق الأثر (الأنظمة / العمليات / الوحدات)',
      span: 6 },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true, span: 6,
      labelEn: 'Severity',
      labelAr: 'الشدّة',
      options: SEVERITY_OPTS },
  ],

  // ── Vendor onboarding — SAMA Outsourcing §5.1, NRC, ISO 27036 ──
  VENDOR_ONBOARDING: [
    { key: 'vendorName', kind: 'text', required: true, span: 6,
      labelEn: 'Vendor / supplier legal name',
      labelAr: 'الاسم القانوني للمورد',
      placeholderEn: 'Full registered name', placeholderAr: 'الاسم الكامل المُسجَّل' },
    { key: 'vendorNameAr', kind: 'text', span: 6,
      labelEn: 'Vendor name (Arabic)',
      labelAr: 'اسم المورد (بالعربية)' },
    { key: 'country', kind: 'text', required: true, span: 4,
      labelEn: 'Country of incorporation',
      labelAr: 'دولة التأسيس',
      placeholderEn: 'e.g. Saudi Arabia, UAE, USA', placeholderAr: 'مثلاً: السعودية، الإمارات، الولايات المتحدة' },
    { key: 'tier', kind: 'select', required: true, span: 4,
      labelEn: 'Vendor tier (NRC classification)',
      labelAr: 'درجة المورد (تصنيف المركز الوطني للمخاطر)',
      options: [
        { value: 'TIER_1', labelEn: 'Tier 1 — direct',     labelAr: 'الدرجة 1 — مباشر' },
        { value: 'TIER_2', labelEn: 'Tier 2 — sub-supplier', labelAr: 'الدرجة 2 — مورد فرعي' },
        { value: 'TIER_3', labelEn: 'Tier 3 — beyond',     labelAr: 'الدرجة 3 — أبعد' },
      ] },
    { key: 'contactEmail', kind: 'text', required: true, span: 4,
      labelEn: 'Vendor contact email',
      labelAr: 'البريد الإلكتروني لجهة الاتصال',
      placeholderEn: 'partner@vendor.com', placeholderAr: 'partner@vendor.com' },
    { key: 'serviceDescription', kind: 'textarea', required: true,
      labelEn: 'Service / product description',
      labelAr: 'وصف الخدمة / المنتج',
      placeholderEn: 'What will this vendor do for us? Include data flows, system integrations, hosting location.',
      placeholderAr: 'ماذا سيقدّم المورد؟ اذكر تدفقات البيانات والتكاملات وموقع الاستضافة.' },
    { key: 'processesPersonalData', kind: 'select', required: true, span: 6,
      labelEn: 'Will the vendor process personal data?',
      labelAr: 'هل سيعالج المورد بيانات شخصية؟',
      options: [
        { value: 'NO',                 labelEn: 'No personal data',                labelAr: 'لا توجد بيانات شخصية' },
        { value: 'CUSTOMER_DATA',      labelEn: 'Customer personal data',          labelAr: 'بيانات العملاء الشخصية' },
        { value: 'EMPLOYEE_DATA',      labelEn: 'Employee personal data',          labelAr: 'بيانات الموظفين الشخصية' },
        { value: 'BOTH',               labelEn: 'Both customer + employee',        labelAr: 'كلا العميل والموظف' },
        { value: 'SPECIAL_CATEGORY',   labelEn: 'Special-category data (PDPL Art. 6)', labelAr: 'بيانات حساسة (المادة 6)' },
      ] },
    { key: 'expectedValueSar', kind: 'text', span: 6,
      labelEn: 'Expected annual contract value (SAR)',
      labelAr: 'قيمة العقد السنوية المتوقعة (ريال سعودي)',
      placeholderEn: 'e.g. 500,000 — used for tier-classification', placeholderAr: 'مثلاً: 500,000 — تُستخدم للتصنيف' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'Vendor criticality',
      labelAr: 'حرجية المورد',
      hintEn: 'CRITICAL = service depends on this vendor and there is no alternative.',
      hintAr: 'حرجة = الخدمة تعتمد على هذا المورد دون بديل.',
      options: SEVERITY_OPTS },
  ],

  // ── DPIA initiation — PDPL Art. 25 ──
  DPIA_INITIATION: [
    { key: 'processingActivityName', kind: 'text', required: true, span: 8,
      labelEn: 'Processing activity name',
      labelAr: 'اسم نشاط المعالجة',
      placeholderEn: 'e.g. AI-based credit scoring for retail loans', placeholderAr: 'مثلاً: تقييم الائتمان بالذكاء الاصطناعي للقروض الأفراد' },
    { key: 'triggerReason', kind: 'select', required: true, span: 4,
      labelEn: 'PDPL trigger',
      labelAr: 'سبب الاستدعاء',
      options: [
        { value: 'AUTOMATED_DECISION_MAKING', labelEn: 'Automated decision-making', labelAr: 'اتخاذ قرار آلي' },
        { value: 'LARGE_SCALE_PROFILING',     labelEn: 'Large-scale profiling',     labelAr: 'تنميط واسع النطاق' },
        { value: 'SPECIAL_CATEGORY',          labelEn: 'Special-category data',     labelAr: 'بيانات حساسة' },
        { value: 'BIOMETRIC',                 labelEn: 'Biometric processing',      labelAr: 'معالجة بيانات حيوية' },
        { value: 'LARGE_SCALE_MONITORING',    labelEn: 'Large-scale monitoring',    labelAr: 'مراقبة واسعة النطاق' },
        { value: 'CROSS_BORDER_TRANSFER',     labelEn: 'Cross-border transfer',     labelAr: 'نقل عبر الحدود' },
        { value: 'NEW_TECHNOLOGY',            labelEn: 'Novel technology',          labelAr: 'تقنية جديدة' },
        { value: 'OTHER',                     labelEn: 'Other high-risk processing', labelAr: 'معالجة عالية المخاطر أخرى' },
      ] },
    { key: 'description', kind: 'textarea', required: true,
      labelEn: 'What does this processing do?',
      labelAr: 'ماذا تفعل هذه المعالجة؟',
      placeholderEn: 'Purposes, data flows, decisions made about data subjects, legal basis.',
      placeholderAr: 'الأغراض، تدفقات البيانات، القرارات المُتخذة بشأن أصحاب البيانات، الأساس القانوني.' },
    { key: 'dataCategories', kind: 'text', required: true, span: 6,
      labelEn: 'Categories of personal data',
      labelAr: 'فئات البيانات الشخصية',
      placeholderEn: 'e.g. financial, health, biometric, location', placeholderAr: 'مثلاً: مالية، صحية، حيوية، موقع' },
    { key: 'dataSubjects', kind: 'text', required: true, span: 6,
      labelEn: 'Categories of data subjects',
      labelAr: 'فئات أصحاب البيانات',
      placeholderEn: 'e.g. customers, employees, minors', placeholderAr: 'مثلاً: عملاء، موظفون، قاصرون' },
    { key: 'proposedStartDate', kind: 'date', required: true, span: 6,
      labelEn: 'Proposed start date',
      labelAr: 'تاريخ البدء المقترح' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true, span: 6,
      labelEn: 'Inherent risk to data subjects',
      labelAr: 'الخطر المتأصل على أصحاب البيانات',
      options: SEVERITY_OPTS },
  ],

  // ── BIA submission — ISO 22301 §8.2.2 ──
  BIA_SUBMISSION: [
    { key: 'businessUnit', kind: 'text', required: true, span: 6,
      labelEn: 'Business unit',
      labelAr: 'الوحدة التشغيلية',
      placeholderEn: 'e.g. Retail Banking, Treasury', placeholderAr: 'مثلاً: الصيرفة الأفراد، الخزينة' },
    { key: 'processName', kind: 'text', required: true, span: 6,
      labelEn: 'Critical process / service',
      labelAr: 'العملية / الخدمة الحرجة',
      placeholderEn: 'e.g. Card payment authorisation', placeholderAr: 'مثلاً: تفويض مدفوعات البطاقات' },
    { key: 'rtoHours', kind: 'text', required: true, span: 4,
      labelEn: 'RTO (hours)',
      labelAr: 'هدف زمن الاستعادة (ساعات)',
      hintEn: 'Maximum tolerable downtime before significant impact.',
      hintAr: 'أقصى انقطاع مقبول قبل أثر جسيم.' },
    { key: 'rpoHours', kind: 'text', required: true, span: 4,
      labelEn: 'RPO (hours)',
      labelAr: 'هدف نقطة الاستعادة (ساعات)',
      hintEn: 'Maximum tolerable data loss measured in time.',
      hintAr: 'أقصى فقد بيانات مقبول مُقاساً بالزمن.' },
    { key: 'mtpdHours', kind: 'text', required: true, span: 4,
      labelEn: 'MTPD (hours)',
      labelAr: 'أقصى فترة انقطاع محتملة (ساعات)',
      hintEn: 'Maximum tolerable period of disruption.',
      hintAr: 'أقصى فترة انقطاع محتملة.' },
    { key: 'impactDescription', kind: 'textarea', required: true,
      labelEn: 'Impact if this process is disrupted',
      labelAr: 'الأثر عند تعطّل هذه العملية',
      placeholderEn: 'Financial loss, regulatory penalty, customer impact, reputational damage — quantify where possible.',
      placeholderAr: 'خسارة مالية، عقوبة تنظيمية، أثر على العميل، أثر سمعة — كمّم ما يمكن.' },
    { key: 'dependencies', kind: 'textarea',
      labelEn: 'Critical dependencies',
      labelAr: 'الاعتماديات الحرجة',
      placeholderEn: 'Upstream systems, vendors, people, premises this process needs to run.',
      placeholderAr: 'الأنظمة والموردين والأشخاص والمباني التي تحتاجها هذه العملية.' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'Overall criticality',
      labelAr: 'الحرجية الإجمالية',
      options: SEVERITY_OPTS },
  ],

  // ── Audit request — IIA IPPF 2010 ──
  AUDIT_REQUEST: [
    { key: 'auditTitle', kind: 'text', required: true,
      labelEn: 'Engagement title',
      labelAr: 'عنوان مهمة المراجعة',
      placeholderEn: 'e.g. Q4 Cyber Operations Review', placeholderAr: 'مثلاً: مراجعة عمليات الأمن السيبراني للربع الرابع' },
    { key: 'scopeDescription', kind: 'textarea', required: true,
      labelEn: 'Scope of the audit',
      labelAr: 'نطاق المراجعة',
      placeholderEn: 'Which processes, systems, frameworks, business units?',
      placeholderAr: 'ما العمليات والأنظمة والأطر والوحدات؟' },
    { key: 'objectives', kind: 'textarea', required: true,
      labelEn: 'Objectives — what questions should the audit answer?',
      labelAr: 'الأهداف — ما الأسئلة التي ينبغي أن تجيب عنها المراجعة؟',
      placeholderEn: 'e.g. "Are MFA controls operating effectively across all Tier-0 systems?"',
      placeholderAr: 'مثلاً: "هل تعمل ضوابط المصادقة الثنائية بفاعلية عبر أنظمة المستوى صفر؟"' },
    { key: 'frameworks', kind: 'text', span: 6,
      labelEn: 'Relevant frameworks',
      labelAr: 'الأطر ذات الصلة',
      placeholderEn: 'e.g. NCA ECC, ISO 27001, SAMA CSF', placeholderAr: 'مثلاً: NCA ECC, ISO 27001, SAMA CSF' },
    { key: 'targetCompletionDate', kind: 'date', required: true, span: 6,
      labelEn: 'Target completion date',
      labelAr: 'تاريخ الإنجاز المُستهدف' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true,
      labelEn: 'Urgency',
      labelAr: 'الإلحاح',
      options: SEVERITY_OPTS },
  ],

  // ── Change request (RFC) — ITIL v4 ──
  CHANGE_REQUEST: [
    { key: 'changeType', kind: 'radio-cards', required: true,
      labelEn: 'Change type',
      labelAr: 'نوع التغيير',
      options: [
        { value: 'STANDARD',  labelEn: 'Standard — pre-approved',  labelAr: 'قياسي — مُعتمد مسبقاً' },
        { value: 'NORMAL',    labelEn: 'Normal — needs CAB review', labelAr: 'عادي — يتطلّب مراجعة CAB' },
        { value: 'EMERGENCY', labelEn: 'Emergency — E-CAB',         labelAr: 'طارئ — E-CAB' },
      ] },
    { key: 'titleEn', kind: 'text', required: true, span: 6,
      labelEn: 'Change title (English)',
      labelAr: 'عنوان التغيير (إنجليزي)' },
    { key: 'titleAr', kind: 'text', span: 6,
      labelEn: 'Change title (Arabic)',
      labelAr: 'عنوان التغيير (عربي)' },
    { key: 'description', kind: 'textarea', required: true,
      labelEn: 'What is changing?',
      labelAr: 'ما الذي يتغيّر؟',
      placeholderEn: 'Describe the change. Include systems touched, integrations affected, downtime expected.',
      placeholderAr: 'صف التغيير. اذكر الأنظمة المعنية والتكاملات والوقت المُتوقّع للتوقف.' },
    { key: 'rollbackPlan', kind: 'textarea', required: true,
      labelEn: 'Rollback plan',
      labelAr: 'خطة الاستعادة',
      placeholderEn: 'How do you revert if the change fails? Include the trigger conditions for rollback.',
      placeholderAr: 'كيف تعيد الحال إن فشل التغيير؟ اذكر شروط التنفيذ.' },
    { key: 'proposedScheduleAt', kind: 'datetime', required: true, span: 6,
      labelEn: 'Proposed implementation window (start)',
      labelAr: 'النافذة المقترحة للتنفيذ (البداية)' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true, span: 6,
      labelEn: 'Risk level',
      labelAr: 'مستوى المخاطرة',
      options: SEVERITY_OPTS },
  ],

  // ── Ethics disclosure — Nazaha, ISO 37001 §8.3, IFAC §150 ──
  ETHICS_DISCLOSURE: [
    { key: 'disclosureType', kind: 'radio-cards', required: true,
      labelEn: 'Disclosure type',
      labelAr: 'نوع الإفصاح',
      options: [
        { value: 'CONFLICT_OF_INTEREST', labelEn: 'Conflict of interest',     labelAr: 'تضارب مصالح' },
        { value: 'GIFT',                 labelEn: 'Gift / hospitality',       labelAr: 'هدية / ضيافة' },
        { value: 'OUTSIDE_EMPLOYMENT',   labelEn: 'Outside employment',       labelAr: 'عمل خارجي' },
        { value: 'FAMILY_BUSINESS',      labelEn: 'Family-related business',  labelAr: 'علاقة عائلية مع جهة' },
        { value: 'POLITICAL_ACTIVITY',   labelEn: 'Political activity',       labelAr: 'نشاط سياسي' },
        { value: 'OTHER',                labelEn: 'Other',                    labelAr: 'أخرى' },
      ] },
    { key: 'description', kind: 'textarea', required: true,
      labelEn: 'Describe the disclosure',
      labelAr: 'صف الإفصاح',
      placeholderEn: 'Who, what, when, what value if applicable. Be specific.',
      placeholderAr: 'من، ماذا، متى، والقيمة إن أمكن. كن محدّداً.' },
    { key: 'counterparty', kind: 'text', span: 6,
      labelEn: 'Counterparty (if any)',
      labelAr: 'الطرف المقابل (إن وُجد)',
      placeholderEn: 'Vendor / customer / other party', placeholderAr: 'مورد / عميل / طرف آخر' },
    { key: 'monetaryValueSar', kind: 'text', span: 6,
      labelEn: 'Monetary value (SAR, if applicable)',
      labelAr: 'القيمة المالية (ريال سعودي، إن أمكن)' },
    { key: 'ongoing', kind: 'select', required: true, span: 6,
      labelEn: 'Is this an ongoing situation?',
      labelAr: 'هل الوضع مستمر؟',
      options: [
        { value: 'ONE_OFF',  labelEn: 'One-off event',  labelAr: 'حدث لمرة واحدة' },
        { value: 'ONGOING',  labelEn: 'Ongoing relationship', labelAr: 'علاقة مستمرة' },
      ] },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true, span: 6,
      labelEn: 'Self-assessed conflict severity',
      labelAr: 'التقييم الذاتي لشدّة التضارب',
      options: SEVERITY_OPTS },
  ],

  // ── Control attestation — ISO 27001 §9.2, SOC 2 CC-6 ──
  CONTROL_ATTESTATION: [
    { key: 'controlReference', kind: 'text', required: true,
      labelEn: 'Control I am attesting to',
      labelAr: 'الضابط الذي أُقرّ به',
      placeholderEn: 'e.g. AC-2.1.3 — Quarterly access recertification', placeholderAr: 'مثلاً: AC-2.1.3 — إعادة اعتماد الوصول ربع السنوية' },
    { key: 'attestationPeriod', kind: 'text', required: true, span: 6,
      labelEn: 'Attestation period',
      labelAr: 'فترة الإقرار',
      placeholderEn: 'e.g. Q1 2026 (Jan-Mar)', placeholderAr: 'مثلاً: الربع الأول 2026 (يناير-مارس)' },
    { key: 'operatingStatus', kind: 'select', required: true, span: 6,
      labelEn: 'Operating effectiveness',
      labelAr: 'الفاعلية التشغيلية',
      options: [
        { value: 'FULLY_EFFECTIVE',     labelEn: 'Fully effective',                labelAr: 'فعّال بالكامل' },
        { value: 'PARTIALLY_EFFECTIVE', labelEn: 'Partially effective',            labelAr: 'فعّال جزئياً' },
        { value: 'NEEDS_IMPROVEMENT',   labelEn: 'Needs improvement',              labelAr: 'يحتاج تحسيناً' },
        { value: 'NOT_OPERATING',       labelEn: 'Not operating',                  labelAr: 'غير عامل' },
      ] },
    { key: 'evidenceProvided', kind: 'textarea', required: true,
      labelEn: 'Evidence I am providing',
      labelAr: 'الأدلّة المُقدَّمة',
      placeholderEn: 'List the artefacts you have uploaded or referenced (logs, screenshots, sign-offs, sample sets).',
      placeholderAr: 'اذكر الأدلّة التي رفعتها أو أشرت إليها (سجلات، لقطات، توقيعات، عيّنات).' },
    { key: 'exceptions', kind: 'textarea',
      labelEn: 'Known exceptions or deviations during the period',
      labelAr: 'الاستثناءات أو الانحرافات خلال الفترة',
      placeholderEn: 'Any time the control did not operate as designed — disclose it here.',
      placeholderAr: 'أي حالة لم يعمل فيها الضابط كما هو مُصمَّم — أفصح عنها هنا.' },
    { key: 'attestationStatement', kind: 'checkbox', required: true,
      labelEn: 'I confirm the above is accurate to the best of my knowledge and the supporting evidence is available for audit on request.',
      labelAr: 'أُقرّ بأن ما ورد أعلاه صحيح بقدر علمي وأن الأدلّة المُسانِدة متاحة للمراجعة عند الطلب.' },
  ],

  // ── Policy-exception request — ISO 27001 §A.5.36 ──
  POLICY_EXCEPTION: [
    { key: 'policyCode', kind: 'text', required: true, span: 6,
      labelEn: 'Policy code',
      labelAr: 'رمز السياسة',
      placeholderEn: 'e.g. POL-LEG-002',
      placeholderAr: 'مثلاً: POL-LEG-002' },
    { key: 'policyTitle', kind: 'text', span: 6,
      labelEn: 'Policy title',
      labelAr: 'عنوان السياسة',
      placeholderEn: 'e.g. Legacy Crypto Sunset Policy',
      placeholderAr: 'مثلاً: سياسة إنهاء التشفير القديم' },
    { key: 'exceptionScope', kind: 'textarea', required: true,
      labelEn: 'Exception scope — what specifically is exempt?',
      labelAr: 'نطاق الاستثناء — ما المُستثنى تحديداً؟',
      placeholderEn: 'Define the precise systems / processes / time-window covered.',
      placeholderAr: 'حدّد بدقّة الأنظمة / العمليات / النافذة الزمنية.' },
    { key: 'businessJustification', kind: 'textarea', required: true,
      labelEn: 'Business justification',
      labelAr: 'المُبرِّر التشغيلي',
      placeholderEn: 'Why is the exception needed? Quantify the cost / risk of full compliance.',
      placeholderAr: 'لماذا يلزم الاستثناء؟ كمّم التكلفة / المخاطرة من الامتثال الكامل.' },
    { key: 'compensatingControls', kind: 'textarea', required: true,
      labelEn: 'Compensating controls in place',
      labelAr: 'الضوابط التعويضية القائمة',
      placeholderEn: 'What additional controls reduce residual risk during the exception window?',
      placeholderAr: 'ما الضوابط الإضافية التي تُقلِّل المخاطرة المتبقية أثناء النافذة؟' },
    { key: 'sunsetDate', kind: 'date', required: true, span: 6,
      labelEn: 'Sunset date',
      labelAr: 'تاريخ الانتهاء',
      hintEn: 'Maximum 12 months — exceptions beyond require executive sign-off.',
      hintAr: 'حدّ أقصى 12 شهراً — تتطلّب فترات أطول اعتماداً تنفيذياً.' },
    { key: 'severitySelfAssessed', kind: 'severity-radio', required: true, span: 6,
      labelEn: 'Residual risk severity',
      labelAr: 'شدّة المخاطرة المتبقية',
      options: SEVERITY_OPTS },
  ],
};
