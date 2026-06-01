// AI governance — Sprint 6 follow-on (C1).
//
// SDAIA published its AI Ethics Principles in 2023; SAMA has a draft
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

// ─── Sprint 11 — live API surface (AI/ML Model Registry) ───────────────────
//
// The original Sprint-6 mock above (AiModel + AI_MODELS demo list) stays in
// place so the existing AiGovernancePage continues to render unchanged. The
// live registry lives under a new MlModel* prefix talking to V183
// `ai_governance.ml_model*` tables.
//
// Tri-track regulator surface:
//   • SDAIA AI Ethics Principles (2023)
//   • SAMA AI Governance Principles (Dec 2024)
//   • EU AI Act Article 16 (for European subsidiaries)
//
// Lifecycle: PROPOSED → DEVELOPMENT → VALIDATION → APPROVED_FOR_PROD →
//            IN_PRODUCTION → MONITORING ⇄ REVISION → DECOMMISSIONED

import { api } from './api';

export type MlModelType =
  | 'CLASSIFICATION' | 'REGRESSION' | 'CLUSTERING' | 'NLP'
  | 'COMPUTER_VISION' | 'RECOMMENDATION' | 'FORECASTING'
  | 'LLM' | 'GENERATIVE' | 'RULE_BASED' | 'HYBRID';

export type MlBusinessDomain =
  | 'CREDIT_DECISION' | 'FRAUD_DETECTION' | 'AML_MONITORING'
  | 'CUSTOMER_CHURN' | 'DOCUMENT_CLASSIFICATION' | 'KYC_OCR'
  | 'CHATBOT' | 'RECOMMENDER' | 'RISK_SCORING'
  | 'MARKETING_OPTIMIZATION' | 'OPERATIONS_AUTOMATION';

export type MlRiskTier = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type MlLifecycleStage =
  | 'PROPOSED' | 'DEVELOPMENT' | 'VALIDATION' | 'APPROVED_FOR_PROD'
  | 'IN_PRODUCTION' | 'MONITORING' | 'REVISION' | 'DECOMMISSIONED';

export type MlFramework =
  | 'TENSORFLOW' | 'PYTORCH' | 'SCIKIT_LEARN' | 'XGBOOST'
  | 'LIGHTGBM' | 'HUGGINGFACE' | 'AZURE_ML' | 'AWS_SAGEMAKER'
  | 'OPENAI_API' | 'CUSTOM' | 'HYBRID';

export type MlHosting =
  | 'ON_PREM' | 'AZURE_KSA' | 'AWS_MIDDLE_EAST' | 'GCP' | 'EDGE' | 'MIXED';

export type MlComplianceStatus =
  | 'COMPLIANT' | 'PARTIAL' | 'NON_COMPLIANT' | 'NOT_ASSESSED';

// Human oversight mode — SDAIA "Humanity" principle · EU AI Act Art.14.
export type MlHumanOversight =
  | 'HUMAN_IN_THE_LOOP' | 'HUMAN_ON_THE_LOOP' | 'HUMAN_IN_COMMAND' | 'NONE';

// Data residency — PDPL Art.29 · SDAIA data classification.
export type MlDataResidency = 'KSA' | 'GCC' | 'GLOBAL';

// Cross-border transfer legal basis — PDPL Art.29.
export type MlTransferBasis =
  | 'NONE_KSA_RESIDENT' | 'SDAIA_ADEQUACY' | 'EXPLICIT_CONSENT'
  | 'BCR_SCC' | 'VITAL_INTEREST' | 'NOT_ASSESSED';

export type MlAuditType =
  | 'AIA' | 'FAIRNESS' | 'BIAS' | 'EXPLAINABILITY' | 'ROBUSTNESS'
  | 'PRIVACY' | 'SECURITY' | 'PERFORMANCE';

export type MlAuditStatus =
  | 'PASSED' | 'PASSED_WITH_CONDITIONS' | 'FAILED' | 'REMEDIATED';

export type MlDriftType =
  | 'DATA_DRIFT' | 'CONCEPT_DRIFT'
  | 'PERFORMANCE_DEGRADATION' | 'FAIRNESS_REGRESSION';

export type MlDriftSeverity = 'INFO' | 'MINOR' | 'MODERATE' | 'MAJOR' | 'CRITICAL';

export interface MlModelResponse {
  id: number;
  externalId: string;
  modelCode: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  modelType: MlModelType;
  businessDomain: MlBusinessDomain;
  riskTier: MlRiskTier;
  lifecycleStage: MlLifecycleStage;
  versionLabel?: string;
  versionHistoryJson?: string;
  modelOwnerUserId: number;
  businessSponsorUserId: number;
  validatorUserId?: number;
  validationDate?: string;
  productionDate?: string;
  decommissionDate?: string;
  decommissionReasonEn?: string;
  decommissionReasonAr?: string;
  trainingDataProvenanceEn?: string;
  trainingDataProvenanceAr?: string;
  trainingDataSizeRecords?: number;
  trainingDataPeriodStart?: string;
  trainingDataPeriodEnd?: string;
  featureCount?: number;
  externalDatasetUsed: boolean;
  externalDatasetProvenance?: string;
  framework?: MlFramework;
  hosting?: MlHosting;
  inferenceLatencyMs?: number;
  trainingComputeHours?: number;
  humanOversight?: MlHumanOversight;
  humanOversightNotesEn?: string;
  humanOversightNotesAr?: string;
  dataResidency?: MlDataResidency;
  crossBorderTransferBasis?: MlTransferBasis;
  hasPdplDpia: boolean;
  pdplDpiaId?: number;
  hasFairnessAudit: boolean;
  lastFairnessAuditDate?: string;
  hasExplainabilityDoc: boolean;
  isHighRiskEuAiAct: boolean;
  sdaiaAiEthicsCompliance: MlComplianceStatus;
  samaAiPrinciplesCompliance: MlComplianceStatus;
  createdAt?: string;
  updatedAt?: string;
}

export interface MlModelRequest {
  modelCode?: string;
  titleEn: string;
  titleAr?: string;
  descriptionEn?: string;
  descriptionAr?: string;
  modelType: MlModelType;
  businessDomain: MlBusinessDomain;
  riskTier: MlRiskTier;
  versionLabel?: string;
  versionHistoryJson?: string;
  modelOwnerUserId: number;
  businessSponsorUserId: number;
  validatorUserId?: number;
  validationDate?: string;
  productionDate?: string;
  trainingDataProvenanceEn?: string;
  trainingDataProvenanceAr?: string;
  trainingDataSizeRecords?: number;
  trainingDataPeriodStart?: string;
  trainingDataPeriodEnd?: string;
  featureCount?: number;
  externalDatasetUsed?: boolean;
  externalDatasetProvenance?: string;
  framework?: MlFramework;
  hosting?: MlHosting;
  inferenceLatencyMs?: number;
  trainingComputeHours?: number;
  humanOversight?: MlHumanOversight;
  humanOversightNotesEn?: string;
  humanOversightNotesAr?: string;
  dataResidency?: MlDataResidency;
  crossBorderTransferBasis?: MlTransferBasis;
  hasPdplDpia?: boolean;
  pdplDpiaId?: number;
  hasFairnessAudit?: boolean;
  lastFairnessAuditDate?: string;
  hasExplainabilityDoc?: boolean;
  isHighRiskEuAiAct?: boolean;
  sdaiaAiEthicsCompliance?: MlComplianceStatus;
  samaAiPrinciplesCompliance?: MlComplianceStatus;
}

export interface LifecycleTransitionRequest { toStage: MlLifecycleStage }

export interface DecommissionRequest {
  reasonEn: string;
  reasonAr?: string;
  decommissionDate?: string;
}

export interface MlModelAuditResponse {
  id: number;
  externalId: string;
  modelId: number;
  auditType: MlAuditType;
  auditDate: string;
  auditorUserId?: number;
  auditorExternalParty?: string;
  methodologyEn?: string;
  methodologyAr?: string;
  findingsEn?: string;
  findingsAr?: string;
  status: MlAuditStatus;
  metricsJson?: string;
  capaTaskId?: number;
  createdAt?: string;
}

export interface MlModelAuditRequest {
  auditType: MlAuditType;
  auditDate: string;
  auditorUserId?: number;
  auditorExternalParty?: string;
  methodologyEn?: string;
  methodologyAr?: string;
  findingsEn?: string;
  findingsAr?: string;
  status: MlAuditStatus;
  metricsJson?: string;
  capaTaskId?: number;
}

export interface DriftObservationResponse {
  id: number;
  externalId: string;
  modelId: number;
  observedAt: string;
  observationType: MlDriftType;
  severity: MlDriftSeverity;
  metricName: string;
  metricValue: number;
  baselineValue?: number;
  driftThreshold?: number;
  notesEn?: string;
  notesAr?: string;
  isAcknowledged: boolean;
  acknowledgedAt?: string;
  acknowledgedByUserId?: number;
  triggeredIssueId?: number;
  createdAt?: string;
}

export interface DriftObservationRequest {
  observedAt?: string;
  observationType: MlDriftType;
  severity: MlDriftSeverity;
  metricName: string;
  metricValue: number;
  baselineValue?: number;
  driftThreshold?: number;
  notesEn?: string;
  notesAr?: string;
}

// ─── REST client ────────────────────────────────────────────────────────────

export const mlModelApi = {
  list:    () => api.get<MlModelResponse[]>('/api/v1/ai/models'),
  get:     (externalId: string) => api.get<MlModelResponse>(`/api/v1/ai/models/${externalId}`),
  highRiskWithoutFairness:
           () => api.get<MlModelResponse[]>('/api/v1/ai/models/high-risk-without-fairness'),
  create:  (body: MlModelRequest) => api.post<MlModelResponse>('/api/v1/ai/models', body),
  update:  (externalId: string, body: MlModelRequest) =>
              api.put<MlModelResponse>(`/api/v1/ai/models/${externalId}`, body),
  transition: (externalId: string, toStage: MlLifecycleStage) =>
              api.post<MlModelResponse>(`/api/v1/ai/models/${externalId}/transition`, { toStage }),
  decommission: (externalId: string, body: DecommissionRequest) =>
              api.post<MlModelResponse>(`/api/v1/ai/models/${externalId}/decommission`, body),

  // Audits.
  listAudits:  (externalId: string) =>
              api.get<MlModelAuditResponse[]>(`/api/v1/ai/models/${externalId}/audits`),
  recordAudit: (externalId: string, body: MlModelAuditRequest) =>
              api.post<MlModelAuditResponse>(`/api/v1/ai/models/${externalId}/audits`, body),

  // Drift.
  listDrift:   (externalId: string) =>
              api.get<DriftObservationResponse[]>(`/api/v1/ai/models/${externalId}/drift-observations`),
  recordDrift: (externalId: string, body: DriftObservationRequest) =>
              api.post<DriftObservationResponse>(`/api/v1/ai/models/${externalId}/drift-observations`, body),
  acknowledgeDrift: (externalId: string, obsId: string) =>
              api.post<DriftObservationResponse>(
                `/api/v1/ai/models/${externalId}/drift-observations/${obsId}/acknowledge`),
};

// ─── Display constants ──────────────────────────────────────────────────────
//
// Saudi-native labels (not literal translations) — لاحظ مثلاً
//   "في الإنتاج" (live in production) ≠ "في الإنتاج" literal
//   "خارج الخدمة" (out of service) — the natural KSA phrasing
//                 vs. literal "تم إيقاف التشغيل"
//   "سجل نماذج الذكاء الاصطناعي" — the page heading, NOT literal
//                 "نماذج التعلم الآلي"

export const ML_STAGE_TONE: Record<MlLifecycleStage, 'neutral' | 'info' | 'accent' | 'warning' | 'success' | 'danger'> = {
  PROPOSED:          'neutral',
  DEVELOPMENT:       'info',
  VALIDATION:        'accent',
  APPROVED_FOR_PROD: 'accent',
  IN_PRODUCTION:     'success',
  MONITORING:        'success',
  REVISION:          'warning',
  DECOMMISSIONED:    'danger',
};

export const ML_STAGE_LABEL: Record<MlLifecycleStage, { en: string; ar: string }> = {
  PROPOSED:          { en: 'Proposed',            ar: 'مُقترح' },
  DEVELOPMENT:       { en: 'Development',         ar: 'قيد التطوير' },
  VALIDATION:        { en: 'Validation',          ar: 'قيد التحقق' },
  APPROVED_FOR_PROD: { en: 'Approved for prod',   ar: 'معتمد للإنتاج' },
  IN_PRODUCTION:     { en: 'In production',       ar: 'في الإنتاج' },
  MONITORING:        { en: 'Monitoring',          ar: 'تحت المراقبة' },
  REVISION:          { en: 'Revision',            ar: 'قيد المراجعة' },
  DECOMMISSIONED:    { en: 'Decommissioned',      ar: 'خارج الخدمة' },
};

export const ML_TIER_TONE: Record<MlRiskTier, 'success' | 'accent' | 'warning' | 'danger'> = {
  LOW:      'success',
  MEDIUM:   'accent',
  HIGH:     'warning',
  CRITICAL: 'danger',
};

export const ML_TIER_LABEL: Record<MlRiskTier, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرج' },
  HIGH:     { en: 'High',     ar: 'مرتفع' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسط' },
  LOW:      { en: 'Low',      ar: 'منخفض' },
};

export const ML_DOMAIN_LABEL: Record<MlBusinessDomain, { en: string; ar: string }> = {
  CREDIT_DECISION:         { en: 'Credit decision',          ar: 'قرارات التمويل' },
  FRAUD_DETECTION:         { en: 'Fraud detection',          ar: 'كشف الاحتيال' },
  AML_MONITORING:          { en: 'AML monitoring',           ar: 'مكافحة غسل الأموال' },
  CUSTOMER_CHURN:          { en: 'Customer churn',           ar: 'تخارج العملاء' },
  DOCUMENT_CLASSIFICATION: { en: 'Document classification',  ar: 'تصنيف المستندات' },
  KYC_OCR:                 { en: 'KYC OCR',                  ar: 'استخراج وثائق اعرف عميلك' },
  CHATBOT:                 { en: 'Chatbot',                  ar: 'روبوت محادثة' },
  RECOMMENDER:             { en: 'Recommender',              ar: 'محرّك التوصيات' },
  RISK_SCORING:            { en: 'Risk scoring',             ar: 'تسجيل المخاطر' },
  MARKETING_OPTIMIZATION:  { en: 'Marketing optimisation',   ar: 'تحسين التسويق' },
  OPERATIONS_AUTOMATION:   { en: 'Operations automation',    ar: 'أتمتة العمليات' },
};

export const ML_AUDIT_TYPE_LABEL: Record<MlAuditType, { en: string; ar: string }> = {
  AIA:            { en: 'AI Impact Assessment', ar: 'تقييم أثر الذكاء الاصطناعي' },
  FAIRNESS:       { en: 'Fairness',       ar: 'عدالة' },
  BIAS:           { en: 'Bias',           ar: 'تحيز' },
  EXPLAINABILITY: { en: 'Explainability', ar: 'قابلية التفسير' },
  ROBUSTNESS:     { en: 'Robustness',     ar: 'متانة' },
  PRIVACY:        { en: 'Privacy',        ar: 'خصوصية' },
  SECURITY:       { en: 'Security',       ar: 'أمان' },
  PERFORMANCE:    { en: 'Performance',    ar: 'أداء' },
};

export const ML_AUDIT_STATUS_TONE: Record<MlAuditStatus, 'success' | 'accent' | 'warning' | 'danger'> = {
  PASSED:                 'success',
  PASSED_WITH_CONDITIONS: 'accent',
  REMEDIATED:             'accent',
  FAILED:                 'danger',
};

export const ML_AUDIT_STATUS_LABEL: Record<MlAuditStatus, { en: string; ar: string }> = {
  PASSED:                 { en: 'Passed',                 ar: 'ناجح' },
  PASSED_WITH_CONDITIONS: { en: 'Passed with conditions', ar: 'ناجح بشروط' },
  FAILED:                 { en: 'Failed',                 ar: 'فاشل' },
  REMEDIATED:             { en: 'Remediated',             ar: 'تمت المعالجة' },
};

export const ML_DRIFT_TYPE_LABEL: Record<MlDriftType, { en: string; ar: string }> = {
  DATA_DRIFT:              { en: 'Data drift',              ar: 'انحراف البيانات' },
  CONCEPT_DRIFT:           { en: 'Concept drift',           ar: 'انحراف المفاهيم' },
  PERFORMANCE_DEGRADATION: { en: 'Performance degradation', ar: 'تراجع الأداء' },
  FAIRNESS_REGRESSION:     { en: 'Fairness regression',     ar: 'تراجع العدالة' },
};

export const ML_DRIFT_SEVERITY_TONE: Record<MlDriftSeverity, 'success' | 'accent' | 'warning' | 'danger' | 'neutral'> = {
  INFO:     'neutral',
  MINOR:    'success',
  MODERATE: 'accent',
  MAJOR:    'warning',
  CRITICAL: 'danger',
};

export const ML_DRIFT_SEVERITY_LABEL: Record<MlDriftSeverity, { en: string; ar: string }> = {
  INFO:     { en: 'Info',     ar: 'معلوماتي' },
  MINOR:    { en: 'Minor',    ar: 'طفيف' },
  MODERATE: { en: 'Moderate', ar: 'متوسط' },
  MAJOR:    { en: 'Major',    ar: 'مهم' },
  CRITICAL: { en: 'Critical', ar: 'حرج' },
};

export const ML_COMPLIANCE_LABEL: Record<MlComplianceStatus, { en: string; ar: string }> = {
  COMPLIANT:     { en: 'Compliant',     ar: 'مُمتثل' },
  PARTIAL:       { en: 'Partial',       ar: 'امتثال جزئي' },
  NON_COMPLIANT: { en: 'Non-compliant', ar: 'غير مُمتثل' },
  NOT_ASSESSED:  { en: 'Not assessed',  ar: 'لم يُقيَّم' },
};

export const ML_COMPLIANCE_TONE: Record<MlComplianceStatus, 'success' | 'accent' | 'warning' | 'danger' | 'neutral'> = {
  COMPLIANT:     'success',
  PARTIAL:       'accent',
  NON_COMPLIANT: 'danger',
  NOT_ASSESSED:  'neutral',
};

export const ML_OVERSIGHT_LABEL: Record<MlHumanOversight, { en: string; ar: string }> = {
  HUMAN_IN_THE_LOOP: { en: 'Human in the loop', ar: 'الإنسان في الحلقة' },
  HUMAN_ON_THE_LOOP: { en: 'Human on the loop', ar: 'الإنسان على الحلقة' },
  HUMAN_IN_COMMAND:  { en: 'Human in command',  ar: 'الإنسان في القيادة' },
  NONE:              { en: 'No human oversight', ar: 'بدون إشراف بشري' },
};

export const ML_OVERSIGHT_TONE: Record<MlHumanOversight, 'success' | 'accent' | 'warning' | 'danger'> = {
  HUMAN_IN_THE_LOOP: 'success',
  HUMAN_ON_THE_LOOP: 'accent',
  HUMAN_IN_COMMAND:  'accent',
  NONE:              'danger',
};

export const ML_RESIDENCY_LABEL: Record<MlDataResidency, { en: string; ar: string }> = {
  KSA:    { en: 'KSA (in-Kingdom)', ar: 'داخل المملكة' },
  GCC:    { en: 'GCC',              ar: 'دول الخليج' },
  GLOBAL: { en: 'Global (cross-border)', ar: 'خارج المملكة' },
};

export const ML_RESIDENCY_TONE: Record<MlDataResidency, 'success' | 'warning' | 'danger'> = {
  KSA:    'success',
  GCC:    'warning',
  GLOBAL: 'danger',
};

export const ML_TRANSFER_BASIS_LABEL: Record<MlTransferBasis, { en: string; ar: string }> = {
  NONE_KSA_RESIDENT: { en: 'Not required (in-Kingdom)', ar: 'غير مطلوب (داخل المملكة)' },
  SDAIA_ADEQUACY:    { en: 'Adequate-country (SDAIA)',  ar: 'دولة ذات حماية كافية (سدايا)' },
  EXPLICIT_CONSENT:  { en: 'Explicit consent',          ar: 'موافقة صريحة' },
  BCR_SCC:           { en: 'BCR / standard clauses',    ar: 'قواعد ملزمة/بنود تعاقدية معيارية' },
  VITAL_INTEREST:    { en: 'Vital interest',            ar: 'مصلحة حيوية' },
  NOT_ASSESSED:      { en: 'Not assessed',              ar: 'لم يُقيَّم' },
};

// ─── Aggregate KPIs across the live registry ───────────────────────────────

export interface MlRegistryStats {
  total: number;
  inProduction: number;             // IN_PRODUCTION + MONITORING
  highOrCritical: number;
  missingFairnessAudit: number;     // HIGH+ in prod-ish without fairness audit
  unacknowledgedDrift: number;      // computed against pre-fetched drift list
  euAiActHighRisk: number;
  sdaiaCompliant: number;
  samaCompliant: number;
}

export function aggregateMlRegistry(
  models: ReadonlyArray<MlModelResponse>,
  unacknowledgedDriftCount = 0,
): MlRegistryStats {
  const PROD_STAGES: ReadonlySet<MlLifecycleStage> = new Set(['IN_PRODUCTION', 'MONITORING']);
  const PROD_OR_APPROVED: ReadonlySet<MlLifecycleStage> = new Set([
    'APPROVED_FOR_PROD', 'IN_PRODUCTION', 'MONITORING',
  ]);
  let inProduction = 0;
  let highOrCritical = 0;
  let missingFairnessAudit = 0;
  let euAiActHighRisk = 0;
  let sdaiaCompliant = 0;
  let samaCompliant = 0;
  for (const m of models) {
    if (PROD_STAGES.has(m.lifecycleStage)) inProduction++;
    if (m.riskTier === 'CRITICAL' || m.riskTier === 'HIGH') {
      highOrCritical++;
      if (PROD_OR_APPROVED.has(m.lifecycleStage) && !m.hasFairnessAudit) {
        missingFairnessAudit++;
      }
    }
    if (m.isHighRiskEuAiAct) euAiActHighRisk++;
    if (m.sdaiaAiEthicsCompliance === 'COMPLIANT') sdaiaCompliant++;
    if (m.samaAiPrinciplesCompliance === 'COMPLIANT') samaCompliant++;
  }
  return {
    total: models.length,
    inProduction,
    highOrCritical,
    missingFairnessAudit,
    unacknowledgedDrift: unacknowledgedDriftCount,
    euAiActHighRisk,
    sdaiaCompliant,
    samaCompliant,
  };
}

// ─── Lifecycle-gate preview (UI mirror of MlModelService gates) ────────────
//
// Mirrors the server-side approval gate at APPROVED_FOR_PROD for HIGH+ tier
// models, so the detail page can disable the transition button + show a
// readable reason. The server enforces this in MlModelService — this is a
// pure UI helper that returns a list of missing prerequisite keys.

/** Audit statuses that count as a signed-off (passed) AIA — mirrors the server. */
const AIA_SIGNED_OFF: ReadonlySet<string> = new Set(['PASSED', 'PASSED_WITH_CONDITIONS', 'REMEDIATED']);

export function approvalGateMissing(
  m: MlModelResponse,
  audits?: ReadonlyArray<{ auditType: string; status: string }>,
): string[] {
  if (m.riskTier !== 'CRITICAL' && m.riskTier !== 'HIGH') return [];
  const missing: string[] = [];
  if (!m.validatorUserId) missing.push('validator_user_id');
  if (!m.validationDate) missing.push('validation_date');
  if (!m.hasFairnessAudit) missing.push('has_fairness_audit');
  if (!m.hasPdplDpia) missing.push('has_pdpl_dpia');
  if (!m.hasExplainabilityDoc) missing.push('has_explainability_doc');
  // SDAIA "Humanity" + EU AI Act Art.14 — a defined, non-NONE oversight mode.
  if (!m.humanOversight || m.humanOversight === 'NONE') missing.push('human_oversight');
  // PDPL Art.29 — cross-border residency needs a documented transfer basis.
  if ((m.dataResidency === 'GCC' || m.dataResidency === 'GLOBAL')
      && (!m.crossBorderTransferBasis || m.crossBorderTransferBasis === 'NOT_ASSESSED')) {
    missing.push('cross_border_transfer_basis');
  }
  // SDAIA — a signed-off AI Impact Assessment (distinct from the PDPL DPIA).
  // Only evaluable when the caller supplies the audit list; otherwise the
  // server remains the source of truth and enforces it on transition.
  if (audits && !audits.some(a => a.auditType === 'AIA' && AIA_SIGNED_OFF.has(a.status))) {
    missing.push('signed_off_aia');
  }
  return missing;
}

/**
 * Allowed next lifecycle stages from each current stage — mirrors the
 * server-side state machine in {@code MlModelService}.
 */
export const ML_NEXT_STAGES: Record<MlLifecycleStage, MlLifecycleStage[]> = {
  PROPOSED:          ['DEVELOPMENT', 'DECOMMISSIONED'],
  DEVELOPMENT:       ['VALIDATION', 'DECOMMISSIONED'],
  VALIDATION:        ['APPROVED_FOR_PROD', 'DEVELOPMENT', 'DECOMMISSIONED'],
  APPROVED_FOR_PROD: ['IN_PRODUCTION', 'DECOMMISSIONED'],
  IN_PRODUCTION:     ['MONITORING', 'REVISION', 'DECOMMISSIONED'],
  MONITORING:        ['IN_PRODUCTION', 'REVISION', 'DECOMMISSIONED'],
  REVISION:          ['VALIDATION', 'IN_PRODUCTION', 'DECOMMISSIONED'],
  DECOMMISSIONED:    [],
};

