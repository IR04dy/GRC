// AI governance — Sprint 6 follow-on (C1).
//
// SDAIA published binding AI Ethics Principles in 2026; SAMA has a draft
// AI in Financial Services framework out for consultation. Every regulated
// Saudi entity now needs:
//   • An AI inventory (what models we run, which use case, what data)
//   • Risk tier per model (UNACCEPTABLE / HIGH / LIMITED / MINIMAL)
//   • AI Impact Assessment (AIA) before high-risk deployment
//   • Continuous monitoring for bias, drift, hallucinations, fairness
//
// Modelled on the EU AI Act tiering + SDAIA principles. AI inventory is
// the foundation — every other artefact (AIA, monitoring, board pack)
// joins back to it.

export type AiRiskTier =
  | 'UNACCEPTABLE'  // Banned (social scoring, real-time biometric)
  | 'HIGH'           // Credit decisions, AML, hiring, claims adjudication
  | 'LIMITED'        // Customer chatbot, content moderation, recommendation
  | 'MINIMAL';       // Spam filter, code completion, internal productivity

export type AiLifecycleStage =
  | 'PROPOSED'       // Initial proposal under review
  | 'AIA_IN_PROGRESS' // AI Impact Assessment ongoing
  | 'PILOT'          // Pilot deployment to subset
  | 'PRODUCTION'     // In production
  | 'MONITORING'     // Production with active drift/bias monitoring
  | 'DEPRECATED'     // Retired
  | 'BLOCKED';       // AIA failed or risk tier UNACCEPTABLE

export type AiUseCase =
  | 'CREDIT_DECISIONING'
  | 'AML_TRANSACTION_MONITORING'
  | 'CUSTOMER_SERVICE_CHATBOT'
  | 'FRAUD_DETECTION'
  | 'KYC_DOCUMENT_OCR'
  | 'CV_SCREENING'
  | 'CONTENT_MODERATION'
  | 'CODE_COMPLETION'
  | 'SALES_FORECAST'
  | 'OPERATIONAL_OPTIMISATION';

export interface AiModel {
  /** Stable code, e.g. "AI-CREDIT-2024-1". */
  code: string;
  nameEn: string;
  nameAr: string;
  useCase: AiUseCase;
  /** Risk tier per SDAIA + EU AI Act mapping. */
  riskTier: AiRiskTier;
  stage: AiLifecycleStage;
  /** Owner team email. */
  ownerEmail: string;
  /** Vendor (if procured) or "in-house". */
  vendor: string;
  /** Modality. */
  modality: 'LLM' | 'CLASSICAL_ML' | 'COMPUTER_VISION' | 'NLP' | 'OTHER';
  /** Training data residency. */
  dataResidency: 'KSA' | 'GCC' | 'GLOBAL';
  /** Whether human is always in the loop for decisions affecting customers. */
  humanInTheLoop: boolean;
  /** Last bias / fairness audit date. */
  lastBiasAuditAt?: string;
  /** Latest fairness score (0–1, higher = better). */
  fairnessScore?: number;
  /** Whether SDAIA AIA has been signed off. */
  aiaSignedOff: boolean;
  /** AIA score (0–100, higher = better). */
  aiaScore?: number;
  descriptionEn: string;
  descriptionAr: string;
}

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);

export const AI_MODELS: AiModel[] = [
  {
    code: 'AI-CREDIT-2024',
    nameEn: 'Personal-loan credit decision model',
    nameAr: 'نموذج قرارات الائتمان للقروض الشخصية',
    useCase: 'CREDIT_DECISIONING', riskTier: 'HIGH',
    stage: 'MONITORING',
    ownerEmail: 'credit.lead@tss.sa', vendor: 'In-house',
    modality: 'CLASSICAL_ML', dataResidency: 'KSA',
    humanInTheLoop: true,
    lastBiasAuditAt: _daysAgo(28), fairnessScore: 0.91,
    aiaSignedOff: true, aiaScore: 88,
    descriptionEn: 'Gradient-boosted decision tree predicting probability-of-default. Always human-in-the-loop for declines.',
    descriptionAr: 'شجرة قرار معزّزة بالتدرّج تتنبّأ باحتمالية التعثّر. وجود الإنسان دائماً في حلقة القرار للرفض.',
  },
  {
    code: 'AI-AML-2025',
    nameEn: 'AML transaction-monitoring model',
    nameAr: 'نموذج مراقبة المعاملات لمكافحة غسل الأموال',
    useCase: 'AML_TRANSACTION_MONITORING', riskTier: 'HIGH',
    stage: 'PRODUCTION',
    ownerEmail: 'aml.officer@tss.sa', vendor: 'NICE Actimize',
    modality: 'CLASSICAL_ML', dataResidency: 'KSA',
    humanInTheLoop: true,
    lastBiasAuditAt: _daysAgo(85), fairnessScore: 0.84,
    aiaSignedOff: true, aiaScore: 79,
    descriptionEn: 'Anomaly detection over transaction graphs. Triggers STR review by AML analyst.',
    descriptionAr: 'كشف الحالات الشاذّة على رسوم بيانية للمعاملات. يُطلق مراجعة بلاغ المعاملات المشبوهة من المحلّل.',
  },
  {
    code: 'AI-CHATBOT-2026',
    nameEn: 'Customer-service chatbot (LLM)',
    nameAr: 'روبوت محادثة خدمة العملاء (LLM)',
    useCase: 'CUSTOMER_SERVICE_CHATBOT', riskTier: 'LIMITED',
    stage: 'PRODUCTION',
    ownerEmail: 'customer.success@tss.sa', vendor: 'Anthropic Claude',
    modality: 'LLM', dataResidency: 'GLOBAL',
    humanInTheLoop: true,
    lastBiasAuditAt: _daysAgo(60), fairnessScore: 0.87,
    aiaSignedOff: true, aiaScore: 82,
    descriptionEn: 'Bilingual customer-service chatbot. Hands off to human for any account or transaction action.',
    descriptionAr: 'روبوت محادثة ثنائي اللغة. يحوّل إلى موظّف بشري لأي إجراء حساب أو معاملة.',
  },
  {
    code: 'AI-FRAUD-2025',
    nameEn: 'Card-fraud detection model',
    nameAr: 'نموذج كشف احتيال البطاقات',
    useCase: 'FRAUD_DETECTION', riskTier: 'HIGH',
    stage: 'PRODUCTION',
    ownerEmail: 'fraud.lead@tss.sa', vendor: 'In-house',
    modality: 'CLASSICAL_ML', dataResidency: 'KSA',
    humanInTheLoop: false,
    lastBiasAuditAt: _daysAgo(15), fairnessScore: 0.93,
    aiaSignedOff: true, aiaScore: 91,
    descriptionEn: 'Real-time fraud scoring on card transactions. Auto-blocks at score > 0.92.',
    descriptionAr: 'تسجيل الاحتيال في الوقت الحقيقي لمعاملات البطاقات. حظر تلقائي عند التسجيل > 0.92.',
  },
  {
    code: 'AI-KYC-OCR-2026',
    nameEn: 'KYC document OCR + extraction',
    nameAr: 'استخراج بيانات وثائق اعرف عميلك (OCR)',
    useCase: 'KYC_DOCUMENT_OCR', riskTier: 'LIMITED',
    stage: 'PILOT',
    ownerEmail: 'compliance.lead@tss.sa', vendor: 'IQVIA Mizan',
    modality: 'COMPUTER_VISION', dataResidency: 'KSA',
    humanInTheLoop: true,
    aiaSignedOff: true, aiaScore: 76,
    descriptionEn: 'Extracts name, ID, address from national ID and Iqama scans. Verified by KYC analyst.',
    descriptionAr: 'يستخرج الاسم والهوية والعنوان من صور الهوية والإقامة. مُتحقَّق من قِبل المحلّل.',
  },
  {
    code: 'AI-CV-SCREEN-2025',
    nameEn: 'CV screening model (pre-shortlist)',
    nameAr: 'نموذج فرز السيرة الذاتية (قبل القائمة المختصرة)',
    useCase: 'CV_SCREENING', riskTier: 'HIGH',
    stage: 'AIA_IN_PROGRESS',
    ownerEmail: 'hr.lead@tss.sa', vendor: 'HireVue',
    modality: 'NLP', dataResidency: 'GLOBAL',
    humanInTheLoop: true,
    aiaSignedOff: false, aiaScore: 58,
    descriptionEn: 'Pre-screens CVs against role requirements. AIA in progress; bias risk on gender + nationality flagged.',
    descriptionAr: 'فرز أوّلي للسيرة الذاتية مقابل متطلّبات الوظيفة. تقييم الأثر قيد التنفيذ؛ مخاطر تحيّز على الجنس + الجنسية مُشار إليها.',
  },
  {
    code: 'AI-CODE-COMP-2025',
    nameEn: 'Code completion (engineering productivity)',
    nameAr: 'إكمال البرمجة (إنتاجية الهندسة)',
    useCase: 'CODE_COMPLETION', riskTier: 'MINIMAL',
    stage: 'PRODUCTION',
    ownerEmail: 'eng.lead@tss.sa', vendor: 'GitHub Copilot Enterprise',
    modality: 'LLM', dataResidency: 'GLOBAL',
    humanInTheLoop: true,
    aiaSignedOff: true, aiaScore: 85,
    descriptionEn: 'Engineer-only IDE assistant. No customer data. PII scrubbing on prompts.',
    descriptionAr: 'مساعد IDE للمهندسين فقط. لا توجد بيانات عملاء. تنظيف البيانات الشخصية من المُدخلات.',
  },
  {
    code: 'AI-SOCIAL-SCORE-2024',
    nameEn: '[BLOCKED] Social-credit scoring proposal',
    nameAr: '[محظور] مقترح تقييم اجتماعي ائتماني',
    useCase: 'CREDIT_DECISIONING', riskTier: 'UNACCEPTABLE',
    stage: 'BLOCKED',
    ownerEmail: 'product.lead@tss.sa', vendor: 'Proposal',
    modality: 'OTHER', dataResidency: 'KSA',
    humanInTheLoop: false,
    aiaSignedOff: false, aiaScore: 12,
    descriptionEn: 'Proposal to score customers using social-media + lifestyle data. BLOCKED at AIA — incompatible with SDAIA AI Ethics + PDPL fairness principles.',
    descriptionAr: 'مقترح لتقييم العملاء باستخدام بيانات وسائل التواصل + نمط الحياة. محظور في تقييم الأثر — متعارض مع مبادئ سدايا للأخلاقيات + عدالة PDPL.',
  },
];

// ─── Display helpers ───────────────────────────────────────────────────────

export const RISK_TIER_TONE: Record<AiRiskTier, 'success' | 'info' | 'warning' | 'danger'> = {
  MINIMAL:      'success',
  LIMITED:      'info',
  HIGH:         'warning',
  UNACCEPTABLE: 'danger',
};

export const RISK_TIER_LABEL: Record<AiRiskTier, { en: string; ar: string }> = {
  MINIMAL:      { en: 'Minimal',      ar: 'منخفض' },
  LIMITED:      { en: 'Limited',      ar: 'محدود' },
  HIGH:         { en: 'High',         ar: 'عالٍ' },
  UNACCEPTABLE: { en: 'Unacceptable', ar: 'غير مقبول' },
};

export const STAGE_TONE: Record<AiLifecycleStage, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  PROPOSED:        'info',
  AIA_IN_PROGRESS: 'warning',
  PILOT:           'info',
  PRODUCTION:      'success',
  MONITORING:      'success',
  DEPRECATED:      'neutral',
  BLOCKED:         'danger',
};

export const STAGE_LABEL: Record<AiLifecycleStage, { en: string; ar: string }> = {
  PROPOSED:        { en: 'Proposed',         ar: 'مُقتَرح' },
  AIA_IN_PROGRESS: { en: 'AIA in progress',  ar: 'تقييم الأثر قيد التنفيذ' },
  PILOT:           { en: 'Pilot',            ar: 'تجريبي' },
  PRODUCTION:      { en: 'Production',       ar: 'إنتاجي' },
  MONITORING:      { en: 'Monitoring',       ar: 'مراقبة' },
  DEPRECATED:      { en: 'Deprecated',       ar: 'مُتقاعد' },
  BLOCKED:         { en: 'Blocked',          ar: 'محظور' },
};

export const USE_CASE_LABEL: Record<AiUseCase, { en: string; ar: string }> = {
  CREDIT_DECISIONING:           { en: 'Credit decisioning',           ar: 'قرارات الائتمان' },
  AML_TRANSACTION_MONITORING:   { en: 'AML transaction monitoring',    ar: 'مراقبة معاملات مكافحة غسل الأموال' },
  CUSTOMER_SERVICE_CHATBOT:     { en: 'Customer-service chatbot',      ar: 'روبوت خدمة العملاء' },
  FRAUD_DETECTION:              { en: 'Fraud detection',               ar: 'كشف الاحتيال' },
  KYC_DOCUMENT_OCR:             { en: 'KYC document OCR',              ar: 'OCR وثائق اعرف عميلك' },
  CV_SCREENING:                 { en: 'CV screening',                  ar: 'فرز السيرة الذاتية' },
  CONTENT_MODERATION:           { en: 'Content moderation',            ar: 'الإشراف على المحتوى' },
  CODE_COMPLETION:              { en: 'Code completion',               ar: 'إكمال البرمجة' },
  SALES_FORECAST:               { en: 'Sales forecast',                ar: 'تنبّؤ المبيعات' },
  OPERATIONAL_OPTIMISATION:     { en: 'Operational optimisation',      ar: 'تحسين العمليات' },
};

export interface AiStats {
  total: number;
  byTier: Record<AiRiskTier, number>;
  inProduction: number;
  aiaSignedOff: number;
  blocked: number;
  avgFairness: number;
  needsAudit: number; // last bias audit > 90 days ago
}

export function aggregateAi(models: ReadonlyArray<AiModel> = AI_MODELS): AiStats {
  const byTier: Record<AiRiskTier, number> = { UNACCEPTABLE: 0, HIGH: 0, LIMITED: 0, MINIMAL: 0 };
  let inProduction = 0, aiaSignedOff = 0, blocked = 0;
  let fairnessSum = 0, fairnessCount = 0, needsAudit = 0;
  const now = Date.now();
  for (const m of models) {
    byTier[m.riskTier]++;
    if (m.stage === 'PRODUCTION' || m.stage === 'MONITORING') inProduction++;
    if (m.aiaSignedOff) aiaSignedOff++;
    if (m.stage === 'BLOCKED') blocked++;
    if (m.fairnessScore != null) {
      fairnessSum += m.fairnessScore;
      fairnessCount++;
    }
    if (m.lastBiasAuditAt != null) {
      const days = (now - new Date(m.lastBiasAuditAt).getTime()) / 86_400_000;
      if (days > 90 && m.riskTier === 'HIGH') needsAudit++;
    } else if (m.riskTier === 'HIGH') {
      needsAudit++;
    }
  }
  return {
    total: models.length,
    byTier,
    inProduction, aiaSignedOff, blocked,
    avgFairness: fairnessCount > 0 ? Math.round((fairnessSum / fairnessCount) * 100) / 100 : 0,
    needsAudit,
  };
}
