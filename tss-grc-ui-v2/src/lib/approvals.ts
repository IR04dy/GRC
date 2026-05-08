// Approval engine — Sprint 5.
//
// ServiceNow IRM / OneTrust / Workiva ship a generic approval primitive
// that every workflow uses (parallel approvers, sequential gates,
// conditional routing by data, delegation, out-of-office, SLA timers).
// Until now this platform's "approvals" were just role-gated transitions
// inside per-module workflows — no queues, no delegation, no SLAs.
//
// This module adds the missing primitive. Saudi-leaning: the default
// approver matrix maps to the governance bodies a CMA-listed entity is
// expected to have — Risk Committee, Audit Committee, Sharia Committee
// (Islamic finance), CISO, DPO, CFO, Board.
//
// Pattern: every approval is data — a `ApprovalRequest` row — and the
// SLA engine drives the countdown. Synthesised WorkflowInstances feed
// the cross-module pulse the same way appetite breaches and CCM checks
// already do.

import type { WorkflowInstance } from './workflows';

// ─── Approver registry (Saudi-aligned governance roles) ─────────────────────

export type ApproverRole =
  | 'CISO'
  | 'DPO'
  | 'CFO'
  | 'CEO'
  | 'CHRO'
  | 'COO'
  | 'CIO'
  | 'GENERAL_COUNSEL'
  | 'RISK_COMMITTEE'
  | 'AUDIT_COMMITTEE'
  | 'SHARIA_COMMITTEE'
  | 'BOARD'
  | 'COMPLIANCE_OFFICER'
  | 'TPRM_LEAD'
  | 'CSIRT_LEAD'
  | 'INTERNAL_AUDIT_LEAD';

export interface ApproverProfile {
  role: ApproverRole;
  /** Display name for demo; in production resolves from IAM. */
  nameEn: string;
  nameAr: string;
  /** Title shown on approval queue rows. */
  titleEn: string;
  titleAr: string;
  /** Who covers this role when out-of-office. */
  delegateRole?: ApproverRole;
}

export const APPROVER_REGISTRY: Record<ApproverRole, ApproverProfile> = {
  CISO:                { role: 'CISO',                nameEn: 'Ahmed Al-Qahtani',    nameAr: 'أحمد القحطاني',   titleEn: 'Chief Information Security Officer', titleAr: 'الرئيس التنفيذي لأمن المعلومات', delegateRole: 'CSIRT_LEAD' },
  DPO:                 { role: 'DPO',                 nameEn: 'Mona Al-Bishi',       nameAr: 'منى البيشي',      titleEn: 'Data Protection Officer',            titleAr: 'مسؤول حماية البيانات',          delegateRole: 'GENERAL_COUNSEL' },
  CFO:                 { role: 'CFO',                 nameEn: 'Faisal Al-Maliki',    nameAr: 'فيصل المالكي',    titleEn: 'Chief Financial Officer',            titleAr: 'الرئيس التنفيذي للشؤون المالية', delegateRole: 'AUDIT_COMMITTEE' },
  CEO:                 { role: 'CEO',                 nameEn: 'Khaled Al-Rasheed',   nameAr: 'خالد الراشد',     titleEn: 'Chief Executive Officer',            titleAr: 'الرئيس التنفيذي',               delegateRole: 'BOARD' },
  CHRO:                { role: 'CHRO',                nameEn: 'Noura Al-Harbi',      nameAr: 'نورا الحربي',     titleEn: 'Chief Human Resources Officer',      titleAr: 'الرئيس التنفيذي للموارد البشرية' },
  COO:                 { role: 'COO',                 nameEn: 'Sami Al-Otaibi',      nameAr: 'سامي العتيبي',    titleEn: 'Chief Operating Officer',            titleAr: 'الرئيس التنفيذي للعمليات' },
  CIO:                 { role: 'CIO',                 nameEn: 'Layla Al-Saud',       nameAr: 'ليلى السعود',     titleEn: 'Chief Information Officer',          titleAr: 'الرئيس التنفيذي لتقنية المعلومات' },
  GENERAL_COUNSEL:     { role: 'GENERAL_COUNSEL',     nameEn: 'Saud Al-Dosari',      nameAr: 'سعود الدوسري',    titleEn: 'General Counsel',                    titleAr: 'المستشار القانوني العام' },
  RISK_COMMITTEE:      { role: 'RISK_COMMITTEE',      nameEn: 'Risk Committee',      nameAr: 'لجنة المخاطر',    titleEn: 'Board Risk Committee',               titleAr: 'لجنة المخاطر بالمجلس' },
  AUDIT_COMMITTEE:     { role: 'AUDIT_COMMITTEE',     nameEn: 'Audit Committee',     nameAr: 'لجنة المراجعة',   titleEn: 'Board Audit Committee',              titleAr: 'لجنة المراجعة بالمجلس' },
  SHARIA_COMMITTEE:    { role: 'SHARIA_COMMITTEE',    nameEn: 'Sharia Committee',    nameAr: 'الهيئة الشرعية',  titleEn: 'Sharia Supervisory Committee',       titleAr: 'الهيئة الشرعية الرقابية' },
  BOARD:               { role: 'BOARD',               nameEn: 'Board of Directors',  nameAr: 'مجلس الإدارة',     titleEn: 'Board of Directors',                  titleAr: 'مجلس الإدارة' },
  COMPLIANCE_OFFICER:  { role: 'COMPLIANCE_OFFICER',  nameEn: 'Hassan Al-Saud',      nameAr: 'حسن السعود',      titleEn: 'Chief Compliance Officer',           titleAr: 'الرئيس التنفيذي للالتزام' },
  TPRM_LEAD:           { role: 'TPRM_LEAD',           nameEn: 'Sami Al-Qahtani',     nameAr: 'سامي القحطاني',  titleEn: 'Third-Party Risk Lead',              titleAr: 'مدير مخاطر الأطراف الخارجية' },
  CSIRT_LEAD:          { role: 'CSIRT_LEAD',          nameEn: 'Mohammed Al-Ahmadi', nameAr: 'محمد الأحمدي',    titleEn: 'CSIRT Lead',                         titleAr: 'قائد فريق الاستجابة للحوادث' },
  INTERNAL_AUDIT_LEAD: { role: 'INTERNAL_AUDIT_LEAD', nameEn: 'Hala Al-Tamimi',      nameAr: 'هالة التميمي',    titleEn: 'Head of Internal Audit',             titleAr: 'رئيس التدقيق الداخلي' },
};

// ─── Approval request model ─────────────────────────────────────────────────

export type ApprovalMode =
  | 'SINGLE'       // one approver
  | 'PARALLEL_ANY' // any one of N approves (default for delegation)
  | 'PARALLEL_ALL' // all of N must approve
  | 'SEQUENTIAL';  // approvers in order; next one only sees it after the previous

export type ApprovalStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'DELEGATED'
  | 'EXPIRED'
  | 'WITHDRAWN';

export type ApprovalCategory =
  | 'POLICY_PUBLICATION'
  | 'RISK_ACCEPTANCE'
  | 'POLICY_EXCEPTION'
  | 'VENDOR_ONBOARDING'
  | 'DSR_ERASURE'
  | 'BREACH_NOTIFICATION'
  | 'DPIA_SIGN_OFF'
  | 'CHANGE_REQUEST'
  | 'AUDIT_FINDING_CLOSURE'
  | 'CAPITAL_EXPENDITURE'
  | 'CONTROL_OVERRIDE';

export interface ApprovalRequest {
  id: string;
  category: ApprovalCategory;
  /** Bilingual subject line. */
  titleEn: string;
  titleAr: string;
  /** What's being approved (1-2 sentences). */
  summaryEn: string;
  summaryAr: string;
  /** Which module triggered this approval. */
  module: 'Risk' | 'Compliance' | 'Privacy' | 'Cyber' | 'TPRM' | 'BCM' | 'Ethics' | 'Audit' | 'Change';
  /** Stable reference of the source entity ("R-0042", "DPIA-45"…). */
  sourceRef: string;
  /** Approval routing mode. */
  mode: ApprovalMode;
  /** Approver roles (in order if SEQUENTIAL). */
  approverRoles: ApproverRole[];
  /** Who has acted so far. */
  decisions: Array<{
    role: ApproverRole;
    decision: 'APPROVED' | 'REJECTED' | 'DELEGATED';
    delegateTo?: ApproverRole;
    at: string;
    commentEn?: string;
  }>;
  /** Aggregate status. */
  status: ApprovalStatus;
  /** Saudi regulator citation (NCA, SAMA, CMA, etc.) — drives audit trail. */
  regulatorCitation?: string;
  /** ISO timestamp the request was raised. */
  raisedAt: string;
  /** ISO date by which it must be decided. */
  dueDate: string;
  /** SLA budget in calendar days (drives the SLA engine). */
  slaDays: number;
  /** Optional reference into the originating record. */
  href?: string;
}

// ─── Demo data — 12 pending approvals across the platform ──────────────────

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
const _daysAhead = (n: number) =>
  new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

export const APPROVAL_REQUESTS: ApprovalRequest[] = [
  {
    id: 'AP-001', category: 'POLICY_PUBLICATION',
    titleEn: 'Acceptable AI Use Policy v1.0',
    titleAr: 'سياسة الاستخدام المقبول للذكاء الاصطناعي v1.0',
    summaryEn: 'Board-level policy governing employee use of generative AI tools. Drafted by Legal + CISO; pending Risk Committee and Audit Committee sign-off.',
    summaryAr: 'سياسة على مستوى المجلس تنظّم استخدام الموظفين لأدوات الذكاء الاصطناعي التوليدي. صياغة القانونية والـCISO؛ بانتظار اعتماد لجنتَي المخاطر والمراجعة.',
    module: 'Compliance', sourceRef: 'POL-AI-001', mode: 'PARALLEL_ALL',
    approverRoles: ['RISK_COMMITTEE', 'AUDIT_COMMITTEE'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'CMA CGR Art. 71; SDAIA AI Ethics Principles',
    raisedAt: _daysAgo(3), dueDate: _daysAhead(11), slaDays: 14,
    href: '/policies/55',
  },
  {
    id: 'AP-002', category: 'RISK_ACCEPTANCE',
    titleEn: 'Formal acceptance of cross-border vendor concentration risk',
    titleAr: 'القبول الرسمي لمخاطر تمركز الموردين عبر الحدود',
    summaryEn: 'AWS regional spend exceeds 25% concentration tolerance. CFO requests formal risk acceptance with 18-month re-balance plan.',
    summaryAr: 'تجاوزت حصة AWS الإقليمية حدّ التمركز 25%. طلب المدير المالي قبولاً رسمياً للمخاطر بخطة إعادة توازن خلال 18 شهراً.',
    module: 'Risk', sourceRef: 'AS-5104', mode: 'SEQUENTIAL',
    approverRoles: ['CFO', 'RISK_COMMITTEE', 'BOARD'],
    decisions: [
      { role: 'CFO', decision: 'APPROVED', at: _daysAgo(2),
        commentEn: 'Acceptance pre-approved with 18-month divestiture plan attached.' },
    ],
    status: 'PENDING',
    regulatorCitation: 'SAMA Cloud Computing Framework §4.3',
    raisedAt: _daysAgo(4), dueDate: _daysAhead(2), slaDays: 7,
    href: '/risk?tab=appetite&id=5104',
  },
  {
    id: 'AP-003', category: 'BREACH_NOTIFICATION',
    titleEn: 'SDAIA breach notification — customer DB export',
    titleAr: 'إبلاغ سدايا — تصدير قاعدة بيانات العملاء',
    summaryEn: 'PDPL Article 25 notification packet drafted for the 47k-record export breach. DPO + General Counsel sign-off then SDAIA dispatch.',
    summaryAr: 'حزمة إبلاغ المادة 25 لاختراق تصدير 47 ألف سجل. اعتماد DPO والمستشار القانوني ثم إرسال إلى سدايا.',
    module: 'Privacy', sourceRef: 'BR-10', mode: 'PARALLEL_ALL',
    approverRoles: ['DPO', 'GENERAL_COUNSEL'],
    decisions: [
      { role: 'DPO', decision: 'APPROVED', at: _daysAgo(0.5),
        commentEn: 'Packet reviewed; ready for legal sign-off.' },
    ],
    status: 'PENDING',
    regulatorCitation: 'PDPL Art. 25 (72-hour rule)',
    raisedAt: _daysAgo(1.2), dueDate: _daysAhead(1.8), slaDays: 3,
    href: '/privacy?tab=breaches',
  },
  {
    id: 'AP-004', category: 'POLICY_EXCEPTION',
    titleEn: 'Jenkins authentication bypass — segmented network',
    titleAr: 'تجاوز المصادقة لـ Jenkins — شبكة مجزّأة',
    summaryEn: 'Compensating-control exception for legacy Jenkins server pending modernisation. CISO + General Counsel review.',
    summaryAr: 'استثناء بضوابط بديلة لخادم Jenkins القديم بانتظار التحديث. مراجعة الـCISO والمستشار القانوني.',
    module: 'Cyber', sourceRef: 'EXC-402', mode: 'PARALLEL_ALL',
    approverRoles: ['CISO', 'GENERAL_COUNSEL'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'NCA ECC-1 control 2-3-3',
    raisedAt: _daysAgo(2), dueDate: _daysAhead(5), slaDays: 7,
    href: '/cyber',
  },
  {
    id: 'AP-005', category: 'VENDOR_ONBOARDING',
    titleEn: 'NEOM Tech Services — Tier 1 critical vendor',
    titleAr: 'NEOM Tech Services — مورد حرج من الفئة 1',
    summaryEn: 'New Tier-1 vendor handling regulated data. TPRM + CISO + DPO joint approval required.',
    summaryAr: 'مورد جديد من الفئة 1 يتعامل مع بيانات منظّمة. اعتماد مشترك TPRM وCISO وDPO.',
    module: 'TPRM', sourceRef: 'V-204', mode: 'PARALLEL_ALL',
    approverRoles: ['TPRM_LEAD', 'CISO', 'DPO'],
    decisions: [
      { role: 'TPRM_LEAD', decision: 'APPROVED', at: _daysAgo(1) },
    ],
    status: 'PENDING',
    regulatorCitation: 'SAMA Outsourcing Framework §5.4',
    raisedAt: _daysAgo(2), dueDate: _daysAhead(4), slaDays: 7,
    href: '/vendors/204',
  },
  {
    id: 'AP-006', category: 'DSR_ERASURE',
    titleEn: 'Data subject erasure — Layla Al-Saud',
    titleAr: 'حذف بيانات صاحب البيانات — ليلى السعود',
    summaryEn: 'PDPL Art. 17 erasure request. Legal hold check passed; awaiting DPO sign-off.',
    summaryAr: 'طلب حذف وفق المادة 17. اجتاز الفحص القانوني؛ بانتظار اعتماد DPO.',
    module: 'Privacy', sourceRef: 'DSR-2026-013', mode: 'SINGLE',
    approverRoles: ['DPO'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'PDPL Art. 17',
    raisedAt: _daysAgo(0.5), dueDate: _daysAhead(0.5), slaDays: 1,
  },
  {
    id: 'AP-007', category: 'CHANGE_REQUEST',
    titleEn: 'Production deployment — payment-gateway upgrade',
    titleAr: 'نشر إنتاجي — ترقية بوابة الدفع',
    summaryEn: 'Major change to PCI-scoped systems; CAB review then CIO sign-off.',
    summaryAr: 'تغيير كبير في الأنظمة الخاضعة لـPCI؛ مراجعة CAB ثم اعتماد CIO.',
    module: 'Change', sourceRef: 'CHG-2026-099', mode: 'SEQUENTIAL',
    approverRoles: ['CISO', 'CIO'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'PCI-DSS v4.0 Req 6.5; ITIL v4 change-enablement',
    raisedAt: _daysAgo(1), dueDate: _daysAhead(3), slaDays: 5,
    href: '/changes',
  },
  {
    id: 'AP-008', category: 'AUDIT_FINDING_CLOSURE',
    titleEn: 'CCTV retention finding — closure sign-off',
    titleAr: 'ملاحظة احتفاظ كاميرات المراقبة — اعتماد الإغلاق',
    summaryEn: 'Privacy finding remediated; awaiting Internal Audit closure approval.',
    summaryAr: 'تمت معالجة ملاحظة الخصوصية؛ بانتظار اعتماد التدقيق الداخلي للإغلاق.',
    module: 'Audit', sourceRef: 'AUD-F-038', mode: 'SINGLE',
    approverRoles: ['INTERNAL_AUDIT_LEAD'],
    decisions: [],
    status: 'PENDING',
    raisedAt: _daysAgo(5), dueDate: _daysAhead(2), slaDays: 7,
    href: '/findings/38',
  },
  {
    id: 'AP-009', category: 'DPIA_SIGN_OFF',
    titleEn: 'Cross-border payroll vendor DPIA',
    titleAr: 'تقييم أثر — مورد كشوف الرواتب عبر الحدود',
    summaryEn: 'High-risk DPIA pending DPO + General Counsel + Risk Committee approval.',
    summaryAr: 'تقييم أثر بمخاطر عالية بانتظار اعتماد DPO والمستشار القانوني ولجنة المخاطر.',
    module: 'Privacy', sourceRef: 'DPIA-42', mode: 'PARALLEL_ALL',
    approverRoles: ['DPO', 'GENERAL_COUNSEL', 'RISK_COMMITTEE'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'PDPL Art. 24 — DPIA requirement',
    raisedAt: _daysAgo(6), dueDate: _daysAhead(1), slaDays: 7,
    href: '/dpias/42',
  },
  {
    id: 'AP-010', category: 'CONTROL_OVERRIDE',
    titleEn: 'Override SLA breach on outsourced KYC vendor',
    titleAr: 'تجاوز خرق SLA لمورد اعرف عميلك المُستعان بمصادر',
    summaryEn: 'TPRM Lead requests one-time override of vendor SLA breach pending Q3 contract renegotiation.',
    summaryAr: 'يطلب مدير مخاطر الموردين تجاوزاً لمرة واحدة لخرق SLA لحين إعادة التفاوض في الربع الثالث.',
    module: 'TPRM', sourceRef: 'V-118', mode: 'SEQUENTIAL',
    approverRoles: ['TPRM_LEAD', 'CISO', 'AUDIT_COMMITTEE'],
    decisions: [
      { role: 'TPRM_LEAD', decision: 'APPROVED', at: _daysAgo(2) },
      { role: 'CISO', decision: 'APPROVED', at: _daysAgo(1) },
    ],
    status: 'PENDING',
    regulatorCitation: 'SAMA Outsourcing Framework — material outsourcing',
    raisedAt: _daysAgo(4), dueDate: _daysAhead(6), slaDays: 14,
    href: '/vendors/118',
  },
  {
    id: 'AP-011', category: 'CAPITAL_EXPENDITURE',
    titleEn: 'CCTV expansion budget (SAR 1.2M) — Sharia compliance review',
    titleAr: 'موازنة توسعة كاميرات المراقبة (1.2 مليون ريال) — مراجعة الالتزام الشرعي',
    summaryEn: 'Capex above SAR 1M threshold. Sharia Committee + CFO review under Islamic-finance compliance policy.',
    summaryAr: 'إنفاق رأسمالي يتجاوز عتبة المليون ريال. مراجعة الهيئة الشرعية والمدير المالي ضمن سياسة الالتزام الشرعي.',
    module: 'Compliance', sourceRef: 'BUDGET-2026-Q3-008', mode: 'PARALLEL_ALL',
    approverRoles: ['SHARIA_COMMITTEE', 'CFO'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'SAMA Sharia Governance Framework',
    raisedAt: _daysAgo(2), dueDate: _daysAhead(8), slaDays: 10,
  },
  {
    id: 'AP-012', category: 'POLICY_EXCEPTION',
    titleEn: 'Saudization band exception — specialist FX trader role',
    titleAr: 'استثناء نطاق السعودة — وظيفة متخصص صرف أجنبي',
    summaryEn: 'CHRO + Compliance Officer joint approval for one non-Saudi specialist hire under HRSD scarce-skill exemption.',
    summaryAr: 'اعتماد مشترك من المدير التنفيذي للموارد البشرية والمسؤول القانوني لتعيين متخصص غير سعودي ضمن استثناء المهارات النادرة.',
    module: 'Compliance', sourceRef: 'HR-SAU-2026-014', mode: 'PARALLEL_ALL',
    approverRoles: ['CHRO', 'COMPLIANCE_OFFICER'],
    decisions: [],
    status: 'PENDING',
    regulatorCitation: 'HRSD Nitaqat — scarce-skills exemption',
    raisedAt: _daysAgo(1), dueDate: _daysAhead(13), slaDays: 14,
  },
];

// ─── Helpers ────────────────────────────────────────────────────────────────

/** Approvers who haven't yet decided. */
export function pendingApprovers(req: ApprovalRequest): ApproverRole[] {
  if (req.mode === 'SEQUENTIAL') {
    // Find first role that hasn't approved.
    for (const role of req.approverRoles) {
      const decision = req.decisions.find(d => d.role === role);
      if (!decision) return [role];
      if (decision.decision === 'REJECTED') return [];
    }
    return [];
  }
  const decided = new Set(req.decisions.map(d => d.role));
  return req.approverRoles.filter(r => !decided.has(r));
}

export function approvalProgress(req: ApprovalRequest): { decided: number; total: number; pct: number } {
  const total = req.approverRoles.length;
  const decided = req.decisions.filter(d => d.decision === 'APPROVED').length;
  return { decided, total, pct: Math.round((decided / Math.max(1, total)) * 100) };
}

// ─── WorkflowInstance synthesis ─────────────────────────────────────────────

const APPROVAL_STAGES = ['DRAFTED', 'SUBMITTED', 'IN_REVIEW', 'APPROVED', 'CLOSED'] as const;

function approvalCurrentStage(req: ApprovalRequest): string {
  if (req.status === 'APPROVED' || req.status === 'REJECTED') return 'CLOSED';
  return req.decisions.length === 0 ? 'SUBMITTED' : 'IN_REVIEW';
}

export function synthesiseApprovalWorkflows(
  reqs: ReadonlyArray<ApprovalRequest> = APPROVAL_REQUESTS,
): WorkflowInstance[] {
  return reqs
    .filter(r => r.status === 'PENDING')
    .map(r => {
      const ageDays = Math.max(0, Math.round((Date.now() - new Date(r.raisedAt).getTime()) / 86_400_000));
      const next = pendingApprovers(r);
      const ownerRole = next[0];
      const owner = ownerRole ? APPROVER_REGISTRY[ownerRole].nameEn : 'Approval queue';
      const ownerTitle = ownerRole ? APPROVER_REGISTRY[ownerRole].titleEn : undefined;
      return {
        id: r.id,
        module: r.module,
        workflow: 'Approval',
        entityType: 'Approval',
        entityRef: r.sourceRef,
        title: r.titleEn,
        stages: [...APPROVAL_STAGES],
        currentStage: approvalCurrentStage(r),
        progress: approvalProgress(r).pct,
        owner,
        ownerRole: ownerTitle,
        dueDate: r.dueDate,
        ageDays,
        slaDays: r.slaDays,
        escalation: 'AT_RISK', // overridden by deriveLiveSla
        nextStep: next.length > 0 ? `${next.length} approver(s) outstanding` : 'Decision recorded',
        reason: r.regulatorCitation,
        href: r.href ?? '/approvals',
      };
    });
}

// ─── Status display helpers ─────────────────────────────────────────────────

export const APPROVAL_STATUS_TONE: Record<ApprovalStatus, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  PENDING:   'info',
  APPROVED:  'success',
  REJECTED:  'danger',
  DELEGATED: 'warning',
  EXPIRED:   'danger',
  WITHDRAWN: 'neutral',
};

export const APPROVAL_MODE_LABEL: Record<ApprovalMode, { en: string; ar: string }> = {
  SINGLE:       { en: 'Single approver',       ar: 'موافِق واحد' },
  PARALLEL_ANY: { en: 'Any of multiple',       ar: 'أيٌّ من مجموعة' },
  PARALLEL_ALL: { en: 'All approvers (parallel)', ar: 'جميع الموافقين (متوازٍ)' },
  SEQUENTIAL:   { en: 'Sequential',            ar: 'تسلسلي' },
};
