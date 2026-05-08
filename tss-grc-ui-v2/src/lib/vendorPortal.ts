// Vendor self-service portal — Sprint 5.
//
// Public-facing portal where a vendor logs in with their access code and
// completes onboarding tasks: security questionnaire, attestation upload,
// CR-number / ZATCA-cert / Sharia-compliance flag, periodic-review
// confirmations. Closes the TPRM loop where today vendors email PDFs to
// a TPRM analyst inbox.
//
// Saudi-flavored: CR (commercial-registry) number entry, ZATCA tax-cert
// upload, NCA-aligned questionnaire questions, Sharia-compliance flag for
// Islamic-finance institutions whose Sharia Committee asks every vendor
// to confirm.

export type VendorTier = 'T1_CRITICAL' | 'T2_HIGH' | 'T3_MEDIUM' | 'T4_LOW';

export type OnboardingStage =
  | 'PROFILE'           // CR / contact / address
  | 'QUESTIONNAIRE'      // SIG-style security questionnaire
  | 'ATTESTATIONS'       // upload SOC 2 / ISO 27001 / insurance
  | 'CONTRACT'           // signed contract upload
  | 'COMPLETE';          // all done — pending TPRM review

export type ActionItemStatus = 'OPEN' | 'IN_PROGRESS' | 'DONE';

export interface AttestationRequirement {
  code: string;
  nameEn: string;
  nameAr: string;
  /** Whether this attestation is mandatory at the vendor's tier. */
  mandatory: boolean;
  /** Days the attestation is valid for. */
  validityDays: number;
  /** Currently uploaded — undefined means not yet provided. */
  uploadedAt?: string;
  expiresAt?: string;
  fileName?: string;
}

export interface QuestionnaireAnswer {
  questionId: string;
  answer: 'YES' | 'NO' | 'NA' | string; // string for free-text
  evidence?: string;
}

export interface VendorProfile {
  /** TPRM internal vendor code. */
  vendorCode: string;
  /** Access code the vendor uses to log in. */
  accessCode: string;
  legalNameEn: string;
  legalNameAr: string;
  /** Saudi commercial-registry number when KSA-registered. */
  crNumber?: string;
  vatNumber?: string;
  countryOfIncorporation: string;
  contactName: string;
  contactEmail: string;
  contactPhone: string;
  tier: VendorTier;
  currentStage: OnboardingStage;
  /** Whether the buyer is a Saudi Islamic-finance institution requiring
   *  Sharia-compliance confirmation per vendor. */
  shariaCompliantRequired: boolean;
  shariaCompliantConfirmed: boolean;
  attestations: AttestationRequirement[];
  questionnaireAnswers: QuestionnaireAnswer[];
  /** Action items the vendor still owes. */
  actionItems: Array<{
    code: string;
    titleEn: string;
    titleAr: string;
    dueDate?: string;
    status: ActionItemStatus;
  }>;
  /** Last buyer-side review. */
  lastReviewedAt?: string;
  /** Next periodic review due. */
  nextReviewDate?: string;
}

// ─── Questionnaire schema (NCA / SAMA / SIG-aligned) ────────────────────────

export interface QuestionnaireSection {
  id: string;
  titleEn: string;
  titleAr: string;
  questions: Array<{
    id: string;
    promptEn: string;
    promptAr: string;
    /** YES_NO_NA = standard tri-state; FREE_TEXT for narratives. */
    kind: 'YES_NO_NA' | 'FREE_TEXT';
    /** Regulator anchor. */
    citation?: string;
  }>;
}

export const VENDOR_QUESTIONNAIRE: QuestionnaireSection[] = [
  {
    id: 'governance',
    titleEn: 'Governance & compliance',
    titleAr: 'الحوكمة والامتثال',
    questions: [
      { id: 'g1', kind: 'YES_NO_NA', citation: 'NCA ECC-1 1-1-1',
        promptEn: 'Do you maintain a board-approved cybersecurity policy?',
        promptAr: 'هل لديك سياسة أمن سيبراني معتمدة من المجلس؟' },
      { id: 'g2', kind: 'YES_NO_NA', citation: 'SAMA Outsourcing §5.4',
        promptEn: 'Have you been subject to a SAMA, NCA, or SDAIA enforcement action in the last 24 months?',
        promptAr: 'هل خضعت لإجراء تنفيذي من ساما أو الأمن السيبراني أو سدايا خلال الـ24 شهراً الماضية؟' },
      { id: 'g3', kind: 'YES_NO_NA',
        promptEn: 'Are you a Sharia-compliant entity (Islamic-finance institutions only)?',
        promptAr: 'هل أنتم منشأة متوافقة مع الشريعة الإسلامية (للمؤسسات المالية الإسلامية)؟' },
    ],
  },
  {
    id: 'security',
    titleEn: 'Security controls',
    titleAr: 'الضوابط الأمنية',
    questions: [
      { id: 's1', kind: 'YES_NO_NA', citation: 'NCA ECC 2-3',
        promptEn: 'Is multi-factor authentication enforced for all privileged accounts?',
        promptAr: 'هل تُفرَض المصادقة الثنائية على جميع الحسابات الإمتيازية؟' },
      { id: 's2', kind: 'YES_NO_NA', citation: 'NCA ECC 2-5',
        promptEn: 'Is data encrypted at rest and in transit using approved algorithms?',
        promptAr: 'هل البيانات مشفّرة أثناء التخزين والنقل باستخدام خوارزميات معتمدة؟' },
      { id: 's3', kind: 'YES_NO_NA', citation: 'NCA ECC 2-6',
        promptEn: 'Do you apply security patches to internet-facing systems within 7 days of release?',
        promptAr: 'هل تطبّقون التحديثات الأمنية على الأنظمة المتصلة بالإنترنت خلال 7 أيام من صدورها؟' },
    ],
  },
  {
    id: 'privacy',
    titleEn: 'Privacy (PDPL)',
    titleAr: 'الخصوصية (PDPL)',
    questions: [
      { id: 'p1', kind: 'YES_NO_NA', citation: 'PDPL Art. 25',
        promptEn: 'Will you process or store personal data of Saudi residents in the course of this engagement?',
        promptAr: 'هل ستعالجون أو تخزّنون بيانات شخصية لمقيمين في المملكة ضمن هذا التعاقد؟' },
      { id: 'p2', kind: 'YES_NO_NA', citation: 'PDPL Art. 29',
        promptEn: 'If yes — does the data leave Saudi Arabia at any point?',
        promptAr: 'إذا نعم — هل تغادر البيانات المملكة في أي مرحلة؟' },
      { id: 'p3', kind: 'FREE_TEXT', citation: 'PDPL Art. 25',
        promptEn: 'How would you notify us of a personal-data breach? (max 24 hours expected)',
        promptAr: 'كيف ستُبلِغوننا باختراق بيانات شخصية؟ (المتوقّع خلال 24 ساعة)' },
    ],
  },
  {
    id: 'continuity',
    titleEn: 'Business continuity',
    titleAr: 'استمرارية الأعمال',
    questions: [
      { id: 'b1', kind: 'YES_NO_NA',
        promptEn: 'Do you maintain a tested business-continuity plan with annual exercise evidence?',
        promptAr: 'هل لديكم خطة استمرارية أعمال مختبَرة بدليل تمرين سنوي؟' },
      { id: 'b2', kind: 'FREE_TEXT',
        promptEn: 'What is your committed RTO for our service?',
        promptAr: 'ما هو هدف زمن الاستعادة (RTO) المُلتزَم به لخدمتنا؟' },
    ],
  },
];

// ─── Tier → required attestations ───────────────────────────────────────────

export function attestationsForTier(tier: VendorTier): AttestationRequirement[] {
  const base: AttestationRequirement[] = [
    { code: 'CR_CERT',         nameEn: 'Commercial Registry certificate',
      nameAr: 'شهادة السجل التجاري', mandatory: true, validityDays: 365 },
    { code: 'INSURANCE',       nameEn: 'Liability insurance certificate',
      nameAr: 'شهادة تأمين المسؤولية', mandatory: true, validityDays: 365 },
    { code: 'ZATCA_TAX',       nameEn: 'ZATCA tax-compliance certificate',
      nameAr: 'شهادة الالتزام الضريبي من زاتكا', mandatory: true, validityDays: 365 },
  ];
  if (tier === 'T1_CRITICAL' || tier === 'T2_HIGH') {
    base.push(
      { code: 'SOC_2',           nameEn: 'SOC 2 Type II report',
        nameAr: 'تقرير SOC 2 Type II', mandatory: true, validityDays: 365 },
      { code: 'ISO_27001',       nameEn: 'ISO 27001 certificate',
        nameAr: 'شهادة ISO 27001', mandatory: true, validityDays: 1095 },
      { code: 'PEN_TEST',        nameEn: 'Last penetration-test summary',
        nameAr: 'ملخّص آخر اختبار اختراق', mandatory: true, validityDays: 365 },
    );
  }
  if (tier === 'T1_CRITICAL') {
    base.push(
      { code: 'BCP_EXERCISE',    nameEn: 'BCP exercise evidence',
        nameAr: 'دليل تمرين خطة استمرارية الأعمال', mandatory: true, validityDays: 365 },
      { code: 'NCA_COMPLIANCE',  nameEn: 'NCA ECC compliance attestation',
        nameAr: 'إقرار التوافق مع ECC من الأمن السيبراني', mandatory: true, validityDays: 365 },
    );
  }
  return base;
}

// ─── Stage progression ──────────────────────────────────────────────────────

const STAGE_ORDER: OnboardingStage[] = ['PROFILE', 'QUESTIONNAIRE', 'ATTESTATIONS', 'CONTRACT', 'COMPLETE'];

export function stageIndex(stage: OnboardingStage): number {
  return STAGE_ORDER.indexOf(stage);
}

export function nextStage(stage: OnboardingStage): OnboardingStage {
  const i = stageIndex(stage);
  return i >= 0 && i < STAGE_ORDER.length - 1 ? STAGE_ORDER[i + 1] : stage;
}

export const STAGE_LABEL: Record<OnboardingStage, { en: string; ar: string }> = {
  PROFILE:       { en: 'Company profile',       ar: 'ملف الشركة' },
  QUESTIONNAIRE: { en: 'Security questionnaire', ar: 'استبيان الأمن' },
  ATTESTATIONS:  { en: 'Attestations & evidence', ar: 'الشهادات والأدلة' },
  CONTRACT:      { en: 'Contract execution',     ar: 'توقيع العقد' },
  COMPLETE:      { en: 'Complete',                ar: 'مكتمل' },
};

export const TIER_LABEL: Record<VendorTier, { en: string; ar: string; tone: 'success' | 'info' | 'warning' | 'danger' }> = {
  T1_CRITICAL: { en: 'Tier 1 — Critical',  ar: 'الفئة 1 — حرج',  tone: 'danger' },
  T2_HIGH:     { en: 'Tier 2 — High',      ar: 'الفئة 2 — عالٍ', tone: 'warning' },
  T3_MEDIUM:   { en: 'Tier 3 — Medium',    ar: 'الفئة 3 — متوسط', tone: 'info' },
  T4_LOW:      { en: 'Tier 4 — Low',       ar: 'الفئة 4 — منخفض', tone: 'success' },
};

// ─── Demo vendor accounts ───────────────────────────────────────────────────

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

export const DEMO_VENDORS: VendorProfile[] = [
  // ── Vendor still in onboarding ──
  {
    vendorCode: 'V-204', accessCode: 'NEOM-2026-A4Z9',
    legalNameEn: 'NEOM Tech Services LLC',
    legalNameAr: 'نيوم لخدمات التقنية المحدودة',
    crNumber: '2050098765',
    vatNumber: '300012345600003',
    countryOfIncorporation: 'Saudi Arabia',
    contactName: 'Khaled Al-Rasheed',
    contactEmail: 'khaled.r@neom-tech.example.sa',
    contactPhone: '+966 11 200 0001',
    tier: 'T1_CRITICAL',
    currentStage: 'QUESTIONNAIRE',
    shariaCompliantRequired: true,
    shariaCompliantConfirmed: false,
    attestations: attestationsForTier('T1_CRITICAL').map(a => ({
      ...a,
      ...(a.code === 'CR_CERT'   ? { uploadedAt: _daysAgo(8),  expiresAt: _daysAhead(357), fileName: 'CR-2050098765.pdf' } : {}),
      ...(a.code === 'INSURANCE' ? { uploadedAt: _daysAgo(8),  expiresAt: _daysAhead(180), fileName: 'liability-2026.pdf' } : {}),
      ...(a.code === 'ZATCA_TAX' ? { uploadedAt: _daysAgo(8),  expiresAt: _daysAhead(85),  fileName: 'zatca-tax-2026.pdf' } : {}),
    })),
    questionnaireAnswers: [
      { questionId: 'g1', answer: 'YES' },
      { questionId: 'g2', answer: 'NO' },
    ],
    actionItems: [
      { code: 'AI-001', titleEn: 'Complete security-controls section of the questionnaire',
        titleAr: 'إكمال قسم الضوابط الأمنية في الاستبيان', dueDate: _daysAhead(2), status: 'IN_PROGRESS' },
      { code: 'AI-002', titleEn: 'Upload SOC 2 Type II report',
        titleAr: 'رفع تقرير SOC 2 Type II', dueDate: _daysAhead(5), status: 'OPEN' },
      { code: 'AI-003', titleEn: 'Upload ISO 27001 certificate',
        titleAr: 'رفع شهادة ISO 27001', dueDate: _daysAhead(5), status: 'OPEN' },
      { code: 'AI-004', titleEn: 'Confirm Sharia-compliance status',
        titleAr: 'تأكيد حالة التوافق مع الشريعة', dueDate: _daysAhead(7), status: 'OPEN' },
    ],
  },
  // ── Active vendor — annual review approaching ──
  {
    vendorCode: 'V-104', accessCode: 'TABUK-LOG-2026-X7M2',
    legalNameEn: 'Tabuk Logistics Group',
    legalNameAr: 'مجموعة تبوك للخدمات اللوجستية',
    crNumber: '4032456789',
    vatNumber: '300054321000003',
    countryOfIncorporation: 'Saudi Arabia',
    contactName: 'Layan Kheir',
    contactEmail: 'layan.k@tabuk-log.example.sa',
    contactPhone: '+966 14 320 0500',
    tier: 'T2_HIGH',
    currentStage: 'COMPLETE',
    shariaCompliantRequired: true,
    shariaCompliantConfirmed: true,
    attestations: attestationsForTier('T2_HIGH').map(a => ({
      ...a,
      uploadedAt: _daysAgo(330),
      expiresAt: _daysAhead(35),
      fileName: `${a.code.toLowerCase()}-2025.pdf`,
    })),
    questionnaireAnswers: VENDOR_QUESTIONNAIRE.flatMap(s => s.questions.map(q => ({
      questionId: q.id, answer: q.kind === 'YES_NO_NA' ? 'YES' as const : 'Reviewed and confirmed.',
    }))),
    actionItems: [
      { code: 'AI-101', titleEn: 'Upload refreshed SOC 2 attestation (current expires in 35 days)',
        titleAr: 'رفع إقرار SOC 2 المُحدَّث (الحالي ينتهي خلال 35 يوماً)', dueDate: _daysAhead(35), status: 'OPEN' },
      { code: 'AI-102', titleEn: 'Confirm 2026 contact list',
        titleAr: 'تأكيد قائمة جهات الاتصال لعام 2026', dueDate: _daysAhead(14), status: 'OPEN' },
    ],
    lastReviewedAt: _daysAgo(330),
    nextReviewDate: _daysAhead(35),
  },
  // ── Low-tier vendor — minimal requirements ──
  {
    vendorCode: 'V-318', accessCode: 'JEDDAH-CON-2026-B2K8',
    legalNameEn: 'Jeddah Consulting House',
    legalNameAr: 'بيت جدة للاستشارات',
    crNumber: '4030789012',
    countryOfIncorporation: 'Saudi Arabia',
    contactName: 'Hala Al-Tamimi',
    contactEmail: 'hala@jch.example.sa',
    contactPhone: '+966 12 600 0700',
    tier: 'T4_LOW',
    currentStage: 'ATTESTATIONS',
    shariaCompliantRequired: false,
    shariaCompliantConfirmed: false,
    attestations: attestationsForTier('T4_LOW').map(a => ({
      ...a,
      ...(a.code === 'CR_CERT'   ? { uploadedAt: _daysAgo(2),  expiresAt: _daysAhead(363), fileName: 'CR-4030789012.pdf' } : {}),
    })),
    questionnaireAnswers: VENDOR_QUESTIONNAIRE.flatMap(s => s.questions.map(q => ({
      questionId: q.id, answer: q.kind === 'YES_NO_NA' ? 'YES' as const : 'See attached evidence.',
    }))),
    actionItems: [
      { code: 'AI-201', titleEn: 'Upload liability insurance certificate',
        titleAr: 'رفع شهادة تأمين المسؤولية', dueDate: _daysAhead(10), status: 'OPEN' },
      { code: 'AI-202', titleEn: 'Upload ZATCA tax certificate',
        titleAr: 'رفع شهادة الالتزام الضريبي من زاتكا', dueDate: _daysAhead(10), status: 'OPEN' },
    ],
  },
];

export function findVendorByAccessCode(code: string): VendorProfile | undefined {
  return DEMO_VENDORS.find(v => v.accessCode.toLowerCase() === code.toLowerCase());
}

// ─── Aggregations ───────────────────────────────────────────────────────────

export function onboardingProgressPct(profile: VendorProfile): number {
  // Profile counts as 1, questionnaire by section completion, attestations
  // by mandatory upload count, contract = stage reached.
  let total = 0; let done = 0;

  // Profile
  total += 1; done += 1;

  // Questionnaire — count answered questions vs total questions
  const totalQuestions = VENDOR_QUESTIONNAIRE.reduce((acc, s) => acc + s.questions.length, 0);
  const answered = profile.questionnaireAnswers.length;
  total += totalQuestions;
  done += Math.min(answered, totalQuestions);

  // Attestations — mandatory uploaded count
  const mandatory = profile.attestations.filter(a => a.mandatory);
  total += mandatory.length;
  done += mandatory.filter(a => a.uploadedAt).length;

  // Contract / complete
  total += 1;
  if (profile.currentStage === 'COMPLETE') done += 1;

  return Math.round((done / Math.max(1, total)) * 100);
}
