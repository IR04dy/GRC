// Sprint 13 — Multi-step approval-chain primitives for the UI.
//
// Mirrors the backend DTOs in
// `tss-grc-backend/src/main/java/com/tss/grc/module/workflow/approval_chains/dto/ApprovalChainDtos.java`.
// The pages use mock data from `SEED_TEMPLATES` + `SEED_INSTANCES` for the
// demo; when wired to the backend the same shapes serialise from the
// /api/v1/workflow/chains endpoints.
//
// Regulatory anchors:
//   - SAMA Corporate Governance §5.3 (documented multi-step authorisation)
//   - NCA ECC-2 control 1-3-2 (chain of approvals for cyber exceptions)
//   - PDPL Article 32 (DPO sign-off chain before cross-border transfer)

export type ChainStepType = 'SERIAL' | 'PARALLEL';

export type ChainInstanceStatus =
  | 'PENDING' | 'IN_PROGRESS' | 'APPROVED' | 'REJECTED' | 'WITHDRAWN' | 'EXPIRED';

export type ChainActionType =
  | 'APPROVED' | 'REJECTED' | 'DELEGATED' | 'REQUESTED_CHANGES' | 'RECUSED'
  | 'ESCALATION_NOTE';

export interface ChainStep {
  id: number;
  templateId: number;
  stepOrder: number;
  stepType: ChainStepType;
  titleEn: string;
  titleAr: string;
  requiredRole?: string | null;
  requiredUserId?: number | null;
  requiredRoleInChain?: string | null;
  isMandatory: boolean;
  canDelegate: boolean;
  slaHours?: number | null;
  escalationRole?: string | null;
}

export interface ChainTemplate {
  id: number;
  templateCode: string;
  titleEn: string;
  titleAr: string;
  descriptionEn?: string | null;
  descriptionAr?: string | null;
  appliesToWorkflowCodes?: string | null;
  appliesToModule?: string | null;
  isDefault: boolean;
  steps: ChainStep[];
}

export interface ChainAction {
  id: number;
  instanceId: number;
  stepOrder: number;
  stepId: number;
  actorUserId: number;
  actorNameEn?: string;
  actorNameAr?: string;
  action: ChainActionType;
  decidedAt: string;
  commentEn?: string | null;
  commentAr?: string | null;
  delegatedToUserId?: number | null;
}

export interface ChainInstance {
  id: number;
  templateId: number;
  templateCode: string;
  sourceWorkflowSubmissionId?: number | null;
  sourceEntityType?: string | null;
  sourceEntityId?: number | null;
  status: ChainInstanceStatus;
  startedAt: string;
  completedAt?: string | null;
  currentStepOrder: number;
  originatorUserId: number;
  originatorNameEn?: string;
  originatorNameAr?: string;
  titleEn?: string | null;
  titleAr?: string | null;
  actions: ChainAction[];
}

// ─── Mock seed templates (mirror V192__approval_chains.sql) ───────────────

export const SEED_TEMPLATES: ChainTemplate[] = [
  {
    id: 1,
    templateCode: 'CHAIN-CAPA-CLOSURE',
    titleEn: 'CAPA Closure — Risk Owner → Department Head → DPO → CISO',
    titleAr: 'إغلاق الإجراءات التصحيحية والوقائية — مالك المخاطرة ← رئيس الإدارة ← مسؤول حماية البيانات ← رئيس أمن المعلومات',
    descriptionEn: 'Four-step serial ladder enforced before any CAPA can be moved to CLOSED. Mandated by SAMA CSF §3.5 and NCA ECC-2 control 2-13.',
    descriptionAr: 'سلسلة اعتماد تسلسلية من أربع خطوات قبل إغلاق أي إجراء تصحيحي. تستند إلى المادة §3.5 من إطار الأمن السيبراني لمؤسسة النقد وضابط 2-13 من ECC-2.',
    appliesToWorkflowCodes: 'CAPA_CLOSURE,FINDING_CLOSURE',
    appliesToModule: 'COMPLIANCE',
    isDefault: true,
    steps: [
      { id: 1, templateId: 1, stepOrder: 1, stepType: 'SERIAL', titleEn: 'Risk Owner sign-off',          titleAr: 'اعتماد مالك المخاطرة',                requiredRole: 'RISK_OWNER',        isMandatory: true, canDelegate: true,  slaHours: 48, escalationRole: 'RISK_MANAGER' },
      { id: 2, templateId: 1, stepOrder: 2, stepType: 'SERIAL', titleEn: 'Department Head review',       titleAr: 'مراجعة رئيس الإدارة',                  requiredRole: 'DEPARTMENT_HEAD',   isMandatory: true, canDelegate: true,  slaHours: 48, escalationRole: 'PLATFORM_ADMIN' },
      { id: 3, templateId: 1, stepOrder: 3, stepType: 'SERIAL', titleEn: 'DPO attestation',              titleAr: 'إقرار مسؤول حماية البيانات',           requiredRole: 'DPO',               isMandatory: true, canDelegate: false, slaHours: 72, escalationRole: 'COMPLIANCE_OFFICER' },
      { id: 4, templateId: 1, stepOrder: 4, stepType: 'SERIAL', titleEn: 'CISO final approval',          titleAr: 'الاعتماد النهائي لرئيس أمن المعلومات', requiredRole: 'CISO',              isMandatory: true, canDelegate: false, slaHours: 72, escalationRole: 'PLATFORM_ADMIN' },
    ],
  },
  {
    id: 2,
    templateCode: 'CHAIN-CROSS-BORDER-XFER',
    titleEn: 'Cross-border Personal-Data Transfer — DPO → Legal → Compliance → Board',
    titleAr: 'نقل البيانات الشخصية عبر الحدود — مسؤول حماية البيانات ← الإدارة القانونية ← مسؤول الالتزام ← المجلس',
    descriptionEn: 'Mandatory ladder for every cross-border transfer of personal data per PDPL Article 32 and its Implementing Regulations Article 24. Board signature is required for adequacy-decision transfers exceeding 10,000 records.',
    descriptionAr: 'سلسلة الاعتماد الإلزامية لأي نقل للبيانات الشخصية إلى خارج المملكة وفق المادة 32 من نظام حماية البيانات الشخصية والمادة 24 من اللائحة التنفيذية. توقيع المجلس مطلوب للنقل القائم على قرار كفاية الحماية الذي يتجاوز 10,000 سجل.',
    appliesToWorkflowCodes: 'CROSS_BORDER_TRANSFER,DPIA_APPROVAL',
    appliesToModule: 'PRIVACY',
    isDefault: true,
    steps: [
      { id: 5, templateId: 2, stepOrder: 1, stepType: 'SERIAL', titleEn: 'DPO adequacy assessment',            titleAr: 'تقييم كفاية الحماية من مسؤول حماية البيانات',                requiredRole: 'DPO',                isMandatory: true, canDelegate: false, slaHours: 72,  escalationRole: 'COMPLIANCE_OFFICER' },
      { id: 6, templateId: 2, stepOrder: 2, stepType: 'SERIAL', titleEn: 'Legal — contract review',            titleAr: 'مراجعة العقد من الإدارة القانونية',                          requiredRole: 'LEGAL_COUNSEL',      isMandatory: true, canDelegate: true,  slaHours: 72,  escalationRole: 'COMPLIANCE_OFFICER' },
      { id: 7, templateId: 2, stepOrder: 3, stepType: 'SERIAL', titleEn: 'Compliance Officer attestation',     titleAr: 'إقرار مسؤول الالتزام',                                       requiredRole: 'COMPLIANCE_OFFICER', isMandatory: true, canDelegate: true,  slaHours: 48,  escalationRole: 'PLATFORM_ADMIN' },
      { id: 8, templateId: 2, stepOrder: 4, stepType: 'SERIAL', titleEn: 'Board approval (≥10k records)',      titleAr: 'موافقة المجلس (للنقل بأكثر من 10,000 سجل)',                  requiredRole: 'BOARD_CHAIR',        isMandatory: true, canDelegate: false, slaHours: 168, escalationRole: 'PLATFORM_ADMIN' },
    ],
  },
  {
    id: 3,
    templateCode: 'CHAIN-MATERIAL-CHANGE',
    titleEn: 'Material Change — CAB Lead → CISO + DPO (parallel) → Change Manager',
    titleAr: 'تغيير جوهري — قائد لجنة استشارية للتغيير ← رئيس أمن المعلومات + مسؤول حماية البيانات (بالتوازي) ← مدير التغيير',
    descriptionEn: 'CAB ladder for material changes. Steps 2a and 2b run in parallel — both CISO and DPO must approve before the Change Manager can release. Anchored on ITIL Change Enablement and SAMA CSF §4.3.',
    descriptionAr: 'سلسلة لجنة استشارية للتغيير للتغييرات الجوهرية. الخطوتان 2أ و 2ب تجريان بالتوازي — يجب اعتماد رئيس أمن المعلومات ومسؤول حماية البيانات قبل أن يصدر مدير التغيير الموافقة. وفق إطار ITIL لتمكين التغيير والمادة §4.3 من إطار الأمن السيبراني لمؤسسة النقد.',
    appliesToWorkflowCodes: 'CHANGE_REQUEST,CAB_REVIEW',
    appliesToModule: 'CHANGE',
    isDefault: true,
    steps: [
      { id: 9,  templateId: 3, stepOrder: 1, stepType: 'SERIAL',   titleEn: 'CAB Lead triage',           titleAr: 'فرز قائد لجنة استشارية للتغيير',          requiredRole: 'CAB_LEAD',        isMandatory: true, canDelegate: true,  slaHours: 24, escalationRole: 'PLATFORM_ADMIN' },
      { id: 10, templateId: 3, stepOrder: 2, stepType: 'PARALLEL', titleEn: 'CISO security review',      titleAr: 'المراجعة الأمنية من رئيس أمن المعلومات',  requiredRole: 'CISO',            isMandatory: true, canDelegate: false, slaHours: 48, escalationRole: 'PLATFORM_ADMIN' },
      { id: 11, templateId: 3, stepOrder: 2, stepType: 'PARALLEL', titleEn: 'DPO privacy review',        titleAr: 'مراجعة الخصوصية من مسؤول حماية البيانات', requiredRole: 'DPO',             isMandatory: true, canDelegate: false, slaHours: 48, escalationRole: 'COMPLIANCE_OFFICER' },
      { id: 12, templateId: 3, stepOrder: 3, stepType: 'SERIAL',   titleEn: 'Change Manager release',    titleAr: 'إصدار مدير التغيير',                      requiredRole: 'CHANGE_MANAGER',  isMandatory: true, canDelegate: true,  slaHours: 24, escalationRole: 'PLATFORM_ADMIN' },
    ],
  },
];

// ─── Mock seed instances ──────────────────────────────────────────────────

const HOUR = 1000 * 60 * 60;
const now = Date.now();

export const SEED_INSTANCES: ChainInstance[] = [
  {
    id: 1001,
    templateId: 1,
    templateCode: 'CHAIN-CAPA-CLOSURE',
    sourceEntityType: 'CAPA',
    sourceEntityId: 4471,
    status: 'IN_PROGRESS',
    startedAt: new Date(now - 30 * HOUR).toISOString(),
    completedAt: null,
    currentStepOrder: 3,
    originatorUserId: 42,
    originatorNameEn: 'Salma Al-Otaibi (Risk Owner)',
    originatorNameAr: 'سلمى العتيبي (مالك المخاطرة)',
    titleEn: 'CAPA-2026-1124 — Privileged-account MFA roll-out closure',
    titleAr: 'CAPA-2026-1124 — إغلاق نشر المصادقة الثنائية للحسابات المميزة',
    actions: [
      { id: 5001, instanceId: 1001, stepOrder: 1, stepId: 1, actorUserId: 42, actorNameEn: 'Salma Al-Otaibi',  actorNameAr: 'سلمى العتيبي',  action: 'APPROVED', decidedAt: new Date(now - 28 * HOUR).toISOString(), commentEn: 'CAPA closed against finding F-2026-309 — evidence pack EVP-2026-882 attached.', commentAr: 'تم إغلاق الإجراء التصحيحي مقابل الملاحظة F-2026-309 — حزمة الأدلة EVP-2026-882 مرفقة.' },
      { id: 5002, instanceId: 1001, stepOrder: 2, stepId: 2, actorUserId: 21, actorNameEn: 'Ahmad Al-Shehri',  actorNameAr: 'أحمد الشهري',   action: 'APPROVED', decidedAt: new Date(now - 6  * HOUR).toISOString(), commentEn: 'Department Head review complete. Production roll-out verified across Riyadh + Jeddah DCs.', commentAr: 'انتهت مراجعة رئيس الإدارة. تم التحقق من النشر الإنتاجي في مركزَي البيانات بالرياض وجدة.' },
    ],
  },
  {
    id: 1002,
    templateId: 2,
    templateCode: 'CHAIN-CROSS-BORDER-XFER',
    sourceEntityType: 'CROSS_BORDER_TRANSFER',
    sourceEntityId: 33,
    status: 'IN_PROGRESS',
    startedAt: new Date(now - 12 * HOUR).toISOString(),
    completedAt: null,
    currentStepOrder: 2,
    originatorUserId: 17,
    originatorNameEn: 'Hassan Al-Saud (Compliance)',
    originatorNameAr: 'حسن السعود (الالتزام)',
    titleEn: 'Cross-border transfer — Snowflake EU region, payroll dataset (8,400 records)',
    titleAr: 'نقل عبر الحدود — منطقة الاتحاد الأوروبي على Snowflake، بيانات الرواتب (8,400 سجل)',
    actions: [
      { id: 5101, instanceId: 1002, stepOrder: 1, stepId: 5, actorUserId: 7, actorNameEn: 'Mona Al-Bishi', actorNameAr: 'منى البيشي', action: 'APPROVED', decidedAt: new Date(now - 4 * HOUR).toISOString(), commentEn: 'Adequacy assessed against EDPB-equivalent SCC — risk LOW. Conditional on encryption-at-rest using KSA-resident KMS.', commentAr: 'تم تقييم كفاية الحماية وفق البنود التعاقدية المعيارية المماثلة لإطار الاتحاد الأوروبي — المخاطر منخفضة. مشروط بالتشفير عند التخزين عبر خدمة إدارة المفاتيح المُقامة داخل المملكة.' },
    ],
  },
  {
    id: 1003,
    templateId: 3,
    templateCode: 'CHAIN-MATERIAL-CHANGE',
    sourceEntityType: 'CHANGE_REQUEST',
    sourceEntityId: 9012,
    status: 'APPROVED',
    startedAt: new Date(now - 96 * HOUR).toISOString(),
    completedAt: new Date(now - 20 * HOUR).toISOString(),
    currentStepOrder: 3,
    originatorUserId: 91,
    originatorNameEn: 'Layla Al-Saud (CIO)',
    originatorNameAr: 'ليلى السعود (CIO)',
    titleEn: 'CR-2026-7733 — Murex 3.1 → 4.0 upgrade in production',
    titleAr: 'CR-2026-7733 — ترقية Murex من 3.1 إلى 4.0 في بيئة الإنتاج',
    actions: [
      { id: 5201, instanceId: 1003, stepOrder: 1, stepId: 9,  actorUserId: 91, actorNameEn: 'Layla Al-Saud',    actorNameAr: 'ليلى السعود',    action: 'APPROVED', decidedAt: new Date(now - 92 * HOUR).toISOString(), commentEn: 'CAB triage — material change, requires CISO + DPO parallel review.',  commentAr: 'فرز اللجنة — تغيير جوهري، يستوجب مراجعة متوازية من CISO و DPO.' },
      { id: 5202, instanceId: 1003, stepOrder: 2, stepId: 10, actorUserId: 1,  actorNameEn: 'Ahmed Al-Qahtani', actorNameAr: 'أحمد القحطاني', action: 'APPROVED', decidedAt: new Date(now - 60 * HOUR).toISOString(), commentEn: 'Security review complete — RFC matches the threat model approved by the AppSec board on 2026-04-18.', commentAr: 'انتهت المراجعة الأمنية — طلب التغيير يتطابق مع نموذج التهديدات المعتمد من لجنة أمن التطبيقات بتاريخ 2026-04-18.' },
      { id: 5203, instanceId: 1003, stepOrder: 2, stepId: 11, actorUserId: 7,  actorNameEn: 'Mona Al-Bishi',    actorNameAr: 'منى البيشي',     action: 'APPROVED', decidedAt: new Date(now - 48 * HOUR).toISOString(), commentEn: 'Privacy review — no new PII flows, RoPA unchanged.', commentAr: 'مراجعة الخصوصية — لا تدفقات بيانات شخصية جديدة، سجل أنشطة المعالجة دون تغيير.' },
      { id: 5204, instanceId: 1003, stepOrder: 3, stepId: 12, actorUserId: 14, actorNameEn: 'Tariq Al-Harbi',   actorNameAr: 'طارق الحربي',    action: 'APPROVED', decidedAt: new Date(now - 20 * HOUR).toISOString(), commentEn: 'Release scheduled for 2026-05-29 maintenance window. Rollback runbook RB-2026-552 verified.', commentAr: 'الإصدار مجدول لنافذة الصيانة بتاريخ 2026-05-29. تم التحقق من خطة الاستعادة RB-2026-552.' },
    ],
  },
];

// ─── Helpers ──────────────────────────────────────────────────────────────

export const STEP_TYPE_LABEL: Record<ChainStepType, { en: string; ar: string }> = {
  SERIAL:   { en: 'Serial',     ar: 'تسلسلية' },
  PARALLEL: { en: 'Parallel',   ar: 'متوازية' },
};

export const STATUS_LABEL: Record<ChainInstanceStatus, { en: string; ar: string }> = {
  PENDING:     { en: 'Pending',      ar: 'بانتظار البدء' },
  IN_PROGRESS: { en: 'In progress',  ar: 'جارية' },
  APPROVED:    { en: 'Approved',     ar: 'معتمدة' },
  REJECTED:    { en: 'Rejected',     ar: 'مرفوضة' },
  WITHDRAWN:   { en: 'Withdrawn',    ar: 'مُسحبت' },
  EXPIRED:     { en: 'Expired',      ar: 'منتهية الصلاحية' },
};

export const ACTION_LABEL: Record<ChainActionType, { en: string; ar: string }> = {
  APPROVED:          { en: 'Approved',           ar: 'معتمد' },
  REJECTED:          { en: 'Rejected',           ar: 'مرفوض' },
  DELEGATED:         { en: 'Delegated',          ar: 'تفويض' },
  REQUESTED_CHANGES: { en: 'Requested changes',  ar: 'طلب تعديلات' },
  RECUSED:           { en: 'Recused',            ar: 'تنحٍّ عن النظر' },
  ESCALATION_NOTE:   { en: 'Escalation note',    ar: 'تصعيد لتجاوز المهلة' },
};

export function statusTone(status: ChainInstanceStatus): 'success' | 'danger' | 'warning' | 'info' | 'neutral' {
  switch (status) {
    case 'APPROVED':    return 'success';
    case 'REJECTED':    return 'danger';
    case 'EXPIRED':     return 'danger';
    case 'WITHDRAWN':   return 'neutral';
    case 'IN_PROGRESS': return 'info';
    case 'PENDING':     return 'warning';
    default:            return 'neutral';
  }
}

export function actionTone(action: ChainActionType): 'success' | 'danger' | 'warning' | 'info' | 'neutral' {
  switch (action) {
    case 'APPROVED':          return 'success';
    case 'REJECTED':          return 'danger';
    case 'DELEGATED':         return 'info';
    case 'REQUESTED_CHANGES': return 'warning';
    case 'RECUSED':           return 'neutral';
    case 'ESCALATION_NOTE':   return 'warning';
    default:                  return 'neutral';
  }
}

/**
 * "What approvals are waiting on me right now?" — flat list per pending
 * step the user matches. Mirrors the backend `/pending-for-me` shape.
 *
 * `userRoles` is the set of role codes the demo user carries. The
 * resolution rule matches the backend: a step is pending for the user
 * when (userId == requiredUserId) OR (userRoles ∋ requiredRole).
 */
export interface PendingForMeRow {
  instanceId: number;
  templateId: number;
  templateCode: string;
  templateTitleEn: string;
  templateTitleAr: string;
  stepId: number;
  stepOrder: number;
  stepTitleEn: string;
  stepTitleAr: string;
  stepType: ChainStepType;
  requiredRole?: string | null;
  slaHours?: number | null;
  startedAt: string;
  stepBecameCurrentAt: string;
  originatorUserId: number;
  originatorNameEn?: string;
  originatorNameAr?: string;
}

export function pendingForUser(
  userId: number | null,
  userRoles: Set<string>,
  instances: ChainInstance[],
  templates: ChainTemplate[],
): PendingForMeRow[] {
  const tplById = new Map(templates.map(t => [t.id, t] as const));
  const rows: PendingForMeRow[] = [];
  for (const i of instances) {
    if (i.status !== 'PENDING' && i.status !== 'IN_PROGRESS') continue;
    const tpl = tplById.get(i.templateId);
    if (!tpl) continue;
    const stepsAtOrder = tpl.steps.filter(s => s.stepOrder === i.currentStepOrder);
    for (const s of stepsAtOrder) {
      const matchById = userId != null && s.requiredUserId != null && s.requiredUserId === userId;
      const matchByRole = s.requiredRole != null && userRoles.has(s.requiredRole.toUpperCase());
      if (!matchById && !matchByRole) continue;
      const already = i.actions.some(
        a => a.stepId === s.id && a.actorUserId === userId && a.action === 'APPROVED',
      );
      if (already) continue;
      rows.push({
        instanceId: i.id,
        templateId: tpl.id,
        templateCode: tpl.templateCode,
        templateTitleEn: tpl.titleEn,
        templateTitleAr: tpl.titleAr,
        stepId: s.id,
        stepOrder: s.stepOrder,
        stepTitleEn: s.titleEn,
        stepTitleAr: s.titleAr,
        stepType: s.stepType,
        requiredRole: s.requiredRole,
        slaHours: s.slaHours,
        startedAt: i.startedAt,
        stepBecameCurrentAt: i.startedAt,
        originatorUserId: i.originatorUserId,
        originatorNameEn: i.originatorNameEn,
        originatorNameAr: i.originatorNameAr,
      });
    }
  }
  return rows;
}
