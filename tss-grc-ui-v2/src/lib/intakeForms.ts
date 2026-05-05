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
