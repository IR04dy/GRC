// BCM lifecycle — production hardening (Option 4 — same pattern as risk +
// audit + privacy lifecycle workspaces).
//
// Wraps the existing BCM register (mockBcm.ts) with the full ISO 22301 +
// NCEMA Business Continuity Standard + SAMA Operational Resilience
// Framework lifecycle a Head of BCM in Saudi Arabia is expected to run:
//
//   1. Business Impact Analysis (BIA) workflow — DRAFT → DEPT_REVIEW →
//      BCM_APPROVED → ANNUAL_REVIEW with 12-month auto-expiry per ISO 22301
//      §8.2.2; tier classification (national / sector / operational / local)
//      maps to SAMA OPRES Article 3 + NCEMA SCC tiering.
//   2. Recovery strategy register — per-process strategy with primary site,
//      alternate site (Riyadh ↔ Jeddah tri-region), manual workaround,
//      activation owner, and dependencies on people / tech / suppliers /
//      premises.
//   3. Drill / exercise lifecycle — PLANNING → EXECUTION → AFTER_ACTION
//      with bilingual after-action report, RTO actual vs target, NCEMA
//      cross-sector flag for tier-1 entities.
//   4. Live plan invocation — STANDBY → DECLARED → INVOKED → STABILISED →
//      STAND_DOWN → POST_INCIDENT_REVIEW state machine, with regulator
//      notification log (SAMA ≤ 4h material, NCEMA ≤ 24h, MoH for
//      health-sector, CITC for telco).
//   5. Disruption history — past invocations with actual recovery time
//      versus RTO target, lessons learned narrative.
//   6. OPRES packet generator — SAMA Operational Resilience Framework
//      reporting packet with tier coverage, RTO compliance %, drill
//      cadence and outstanding remediation.

import { BCM_PLANS, BCM_PROCESSES } from '../pages/bcm/mockBcm';
import type { BcmPlanDto, BcmProcessDto, BcmBiaDto } from './api';
// F2-202: the `platformPersistence` import that used to sit here is gone with the file.
// This store is BROWSER_LOCAL — declared, with its real endpoint and measured row count,
// in `lib/lifecycleStores.ts`.

// ─── Types ──────────────────────────────────────────────────────────────────

export type ProcessTier = 'TIER-1-NATIONAL' | 'TIER-2-SECTORAL' | 'TIER-3-OPERATIONAL' | 'TIER-4-LOCAL';

export type BiaStatus = 'DRAFT' | 'DEPT_REVIEW' | 'BCM_APPROVED' | 'ANNUAL_REVIEW' | 'EXPIRED';

export type RecoverySite = 'RIYADH-PRIMARY' | 'JEDDAH-DR' | 'DAMMAM-COLD' | 'EXTERNAL-CLOUD' | 'MANUAL-PROCESS';

export type StrategyStatus = 'DRAFT' | 'TESTED' | 'APPROVED' | 'NEEDS_UPDATE';

export type DrillType = 'TABLETOP' | 'WALKTHROUGH' | 'FAILOVER' | 'SIMULATION' | 'FULL_DR' | 'CROSS_SECTOR';
export type DrillStatus = 'PLANNED' | 'IN_PROGRESS' | 'EXECUTED' | 'AFTER_ACTION' | 'CLOSED';

export type InvocationStatus =
  | 'STANDBY'
  | 'DECLARED'
  | 'INVOKED'
  | 'STABILISED'
  | 'STAND_DOWN'
  | 'POST_INCIDENT_REVIEW';

export interface BiaRecord {
  /** Stable id, e.g. "BIA-2026-001". */
  id: string;
  /** Process id from BCM_PROCESSES. */
  processId: number;
  /** Tier classification — drives notification windows + recovery priority. */
  tier: ProcessTier;
  /** RTO target in hours. */
  rtoHours: number;
  /** RPO target in hours. */
  rpoHours: number;
  /** Maximum tolerable period of disruption (hours). */
  mtpdHours: number;
  /** Financial impact per hour of disruption (SAR). */
  financialImpactSarPerHour: number;
  /** Regulatory impact (e.g. SAMA reportable, customer-facing). */
  regulatoryImpactEn: string;
  regulatoryImpactAr: string;
  /** Customer impact summary. */
  customerImpactEn: string;
  customerImpactAr: string;
  /** Process owner / steward. */
  processOwner: string;
  /** BCM team reviewer. */
  reviewer?: string;
  status: BiaStatus;
  /** ISO date of assessment. */
  assessedAt: string;
  /** ISO date of approval. */
  approvedAt?: string;
  /** ISO date when annual re-review is due. */
  nextReviewAt: string;
  /** ISO date when this BIA expires (12 months from approval per ISO 22301). */
  expiresAt: string;
}

export interface RecoveryStrategy {
  id: string;
  processId: number;
  /** Primary site / system. */
  primarySite: RecoverySite;
  /** Alternate site for failover. */
  alternateSite: RecoverySite;
  /** Manual workaround description. */
  manualWorkaroundEn: string;
  manualWorkaroundAr: string;
  /** Activation owner role. */
  activationOwner: string;
  /** People dependencies — minimum staff to recover. */
  peopleMin: number;
  /** Technology dependencies (e.g. "Core banking + DB cluster"). */
  techDependenciesEn: string;
  techDependenciesAr: string;
  /** Critical suppliers needed for recovery. */
  supplierDependencies: string[];
  /** Premises required (e.g. specific room / data center). */
  premisesEn: string;
  premisesAr: string;
  /** Last test date (links to drill). */
  lastTestedAt?: string;
  /** Strategy maturity (1-5). */
  maturity: number;
  status: StrategyStatus;
  /** Cost to maintain (SAR/year). */
  annualCostSar: number;
}

export interface Drill {
  id: string;
  /** Plan id from BCM_PLANS. */
  planId: number;
  type: DrillType;
  titleEn: string;
  titleAr: string;
  scenarioEn: string;
  scenarioAr: string;
  scheduledDate: string;
  executedDate?: string;
  status: DrillStatus;
  /** Whether NCEMA-led cross-sector drill (mandatory for SCC Tier-1/2). */
  ncemaLed: boolean;
  facilitator: string;
  participantsCount: number;
  /** Actual RTO achieved in hours. */
  rtoActualHours?: number;
  /** Target RTO in hours (from linked process BIA). */
  rtoTargetHours?: number;
  /** After-action narrative (bilingual). */
  whatWorkedEn?: string;
  whatWorkedAr?: string;
  whatDidntEn?: string;
  whatDidntAr?: string;
  /** CAPA tasks raised. */
  capaCount: number;
  /** Score 0-100. */
  score?: number;
}

export interface RegulatoryNotification {
  regulator: 'SAMA' | 'NCEMA' | 'NCA' | 'CITC' | 'MOH' | 'CIVIL-DEFENSE' | 'CMA';
  /** Maximum window in hours from invocation. */
  maxHours: number;
  /** Bilingual basis. */
  basisEn: string;
  basisAr: string;
  /** ISO timestamp when sent. */
  sentAt?: string;
  /** Reference number returned. */
  referenceId?: string;
}

export interface Invocation {
  id: string;
  /** Plan id invoked. */
  planId: number;
  /** Process ids affected. */
  affectedProcessIds: number[];
  status: InvocationStatus;
  /** Bilingual disruption summary. */
  disruptionEn: string;
  disruptionAr: string;
  declaredAt: string;
  invokedAt?: string;
  stabilisedAt?: string;
  standDownAt?: string;
  /** Actual recovery time (hours from invocation to stabilised). */
  actualRtoHours?: number;
  /** Whether real or drill. */
  isReal: boolean;
  /** Incident commander assigned. */
  commander: string;
  notifications: RegulatoryNotification[];
  /** Stage log for audit trail. */
  stageLog: { stepEn: string; stepAr: string; atTs: string; byRole: string }[];
  /** Lessons learned narrative (filled at PIR). */
  lessonsLearnedEn?: string;
  lessonsLearnedAr?: string;
}

// ─── Storage ────────────────────────────────────────────────────────────────

const BIA_KEY = 'grc.bcm.bia.v1';
const STRATEGY_KEY = 'grc.bcm.strategy.v1';
const DRILL_KEY = 'grc.bcm.drill.v1';
const INVOCATION_KEY = 'grc.bcm.invocation.v1';
const EVENT = 'grc.bcm.lifecycle.changed';

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString();
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

// ─── Seed ───────────────────────────────────────────────────────────────────

const SEED_BIA: BiaRecord[] = [
  {
    id: 'BIA-2026-001',
    processId: 11, // Online Banking Authentication
    tier: 'TIER-1-NATIONAL',
    rtoHours: 1, rpoHours: 0, mtpdHours: 4,
    financialImpactSarPerHour: 850_000,
    regulatoryImpactEn: 'SAMA Banking Operations Department reports required within 4h. Customer disclosure mandated per Article 19 of Banking Consumer Protection.',
    regulatoryImpactAr: 'تقارير لإدارة العمليات المصرفية في ساما خلال 4 ساعات. الإفصاح للعملاء وفق المادة 19 من حماية العميل المصرفي.',
    customerImpactEn: 'All retail + corporate online banking customers (~3.2M) cannot authenticate. Branch traffic surges 4-5×.',
    customerImpactAr: 'جميع عملاء الخدمات المصرفية الإلكترونية للأفراد والشركات (~3.2 مليون) يتعذّر عليهم المصادقة. حركة الفروع ترتفع 4-5 أضعاف.',
    processOwner: 'Mohammed Al-Sulaiman (Head of Digital Banking)',
    reviewer: 'Layla Al-Ghamdi (Head of BCM)',
    status: 'BCM_APPROVED',
    assessedAt: _daysAgo(60),
    approvedAt: _daysAgo(50),
    nextReviewAt: _daysAhead(305),
    expiresAt: _daysAhead(315),
  },
  {
    id: 'BIA-2026-002',
    processId: 12, // Card Payment Authorization
    tier: 'TIER-1-NATIONAL',
    rtoHours: 0.5, rpoHours: 0, mtpdHours: 2,
    financialImpactSarPerHour: 1_200_000,
    regulatoryImpactEn: 'SAMA + Visa/Mastercard reporting within 2h. SAMA Payment Systems Department escalation triggered if outage exceeds 30 min.',
    regulatoryImpactAr: 'إبلاغ ساما + فيزا/ماستركارد خلال ساعتين. تصعيد إلى إدارة أنظمة الدفع في ساما إن تجاوز الانقطاع 30 دقيقة.',
    customerImpactEn: 'All POS + e-commerce transactions decline. Estimated 2.8M transactions/hour lost.',
    customerImpactAr: 'جميع معاملات نقاط البيع والتجارة الإلكترونية تُرفض. الخسارة المقدّرة 2.8 مليون معاملة في الساعة.',
    processOwner: 'Faisal Al-Otaibi (Head of Cards)',
    reviewer: 'Layla Al-Ghamdi (Head of BCM)',
    status: 'BCM_APPROVED',
    assessedAt: _daysAgo(50),
    approvedAt: _daysAgo(40),
    nextReviewAt: _daysAhead(315),
    expiresAt: _daysAhead(325),
  },
  {
    id: 'BIA-2026-003',
    processId: 16, // Regulatory Reporting (SAMA)
    tier: 'TIER-2-SECTORAL',
    rtoHours: 24, rpoHours: 24, mtpdHours: 48,
    financialImpactSarPerHour: 75_000,
    regulatoryImpactEn: 'SAMA prudential reporting deadlines fixed by Banking Returns Manual. Late submission triggers Article 12 enforcement penalties.',
    regulatoryImpactAr: 'مواعيد التقارير الاحترازية لساما محدّدة في دليل العائدات المصرفية. التأخّر يستوجب عقوبات المادة 12.',
    customerImpactEn: 'No direct customer impact, but supervisory escalation if missed 2+ cycles.',
    customerImpactAr: 'لا أثر مباشر على العملاء، لكن تصعيد إشرافي عند تجاوز دورتَين.',
    processOwner: 'Khalid Al-Harbi (CFO)',
    reviewer: 'Layla Al-Ghamdi (Head of BCM)',
    status: 'BCM_APPROVED',
    assessedAt: _daysAgo(120),
    approvedAt: _daysAgo(110),
    nextReviewAt: _daysAhead(245),
    expiresAt: _daysAhead(255),
  },
  {
    id: 'BIA-2026-004',
    processId: 18, // Fraud Monitoring
    tier: 'TIER-2-SECTORAL',
    rtoHours: 1, rpoHours: 0.25, mtpdHours: 4,
    financialImpactSarPerHour: 420_000,
    regulatoryImpactEn: 'AML Article 18 — fraud-monitoring downtime exceeding 2h triggers SAMA Compliance Sector immediate notification.',
    regulatoryImpactAr: 'المادة 18 لمكافحة غسل الأموال — توقّف مراقبة الاحتيال أكثر من ساعتين يستوجب إبلاغ قطاع الالتزام في ساما فوراً.',
    customerImpactEn: 'Risk window opens; fraud transactions may slip through detection. Account freeze policy may be invoked.',
    customerImpactAr: 'تنفتح نافذة المخاطر؛ قد تمرّ معاملات احتيالية دون كشف. قد تُفعَّل سياسة تجميد الحسابات.',
    processOwner: 'Sara Al-Zahrani (CISO)',
    reviewer: 'Layla Al-Ghamdi (Head of BCM)',
    status: 'BCM_APPROVED',
    assessedAt: _daysAgo(45),
    approvedAt: _daysAgo(35),
    nextReviewAt: _daysAhead(320),
    expiresAt: _daysAhead(330),
  },
  {
    id: 'BIA-2026-005',
    processId: 13, // Salary Disbursement
    tier: 'TIER-3-OPERATIONAL',
    rtoHours: 24, rpoHours: 6, mtpdHours: 72,
    financialImpactSarPerHour: 18_000,
    regulatoryImpactEn: 'Labour Law Art. 90 — salaries owed monthly. GOSI contributions deadline cannot be missed.',
    regulatoryImpactAr: 'نظام العمل المادة 90 — الرواتب مستحقة شهرياً. لا يمكن تفويت موعد التأمينات الاجتماعية.',
    customerImpactEn: 'Employee + GOSI impact, no external customer impact.',
    customerImpactAr: 'أثر على الموظفين والتأمينات الاجتماعية، لا أثر على العملاء الخارجيين.',
    processOwner: 'Salman Al-Dossari (CHRO)',
    reviewer: 'Layla Al-Ghamdi (Head of BCM)',
    status: 'DEPT_REVIEW',
    assessedAt: _daysAgo(15),
    nextReviewAt: _daysAhead(350),
    expiresAt: _daysAhead(365),
  },
];

const SEED_STRATEGIES: RecoveryStrategy[] = [
  {
    id: 'STR-2026-001',
    processId: 11,
    primarySite: 'RIYADH-PRIMARY',
    alternateSite: 'JEDDAH-DR',
    manualWorkaroundEn: 'Branch authentication via fallback offline KYC sheet + post-event reconciliation. Limited to 200 customers/branch/hour.',
    manualWorkaroundAr: 'مصادقة الفرع عبر نموذج KYC ورقي ثم التوفيق لاحقاً. محدود بـ 200 عميل/فرع/ساعة.',
    activationOwner: 'Mohammed Al-Sulaiman (Head of Digital Banking)',
    peopleMin: 12,
    techDependenciesEn: 'Core banking switch + Active Directory + HSM cluster (Riyadh + Jeddah).',
    techDependenciesAr: 'قاطع النظام المصرفي الأساسي + Active Directory + مجموعة HSM (الرياض + جدّة).',
    supplierDependencies: ['Cisco network', 'Thales HSM support', 'AWS Riyadh region'],
    premisesEn: 'Riyadh primary data center; Jeddah DR site; HQ NOC.',
    premisesAr: 'مركز بيانات الرياض الأساسي؛ موقع جدّة للتعافي؛ غرفة NOC في المقر.',
    lastTestedAt: _daysAgo(90),
    maturity: 5,
    status: 'APPROVED',
    annualCostSar: 4_200_000,
  },
  {
    id: 'STR-2026-002',
    processId: 12,
    primarySite: 'RIYADH-PRIMARY',
    alternateSite: 'JEDDAH-DR',
    manualWorkaroundEn: 'Card transactions stand-in processed by Visa/Mastercard stand-in service for ≤ 4 hours. Beyond 4h: card-not-present transactions blocked.',
    manualWorkaroundAr: 'معاملات البطاقات تُعالَج بخدمة Stand-in من فيزا/ماستركارد لما لا يتجاوز 4 ساعات. بعد ذلك: تُحظَر معاملات البطاقة دون حضور.',
    activationOwner: 'Faisal Al-Otaibi (Head of Cards)',
    peopleMin: 8,
    techDependenciesEn: 'Card switch + HSM + SAMA-Net + Visa/Mastercard interfaces.',
    techDependenciesAr: 'قاطع البطاقات + HSM + شبكة ساما + واجهات فيزا/ماستركارد.',
    supplierDependencies: ['Visa', 'Mastercard', 'Saudi Payments (SARIE)'],
    premisesEn: 'Riyadh primary; Jeddah DR; SAMA-Net dedicated link.',
    premisesAr: 'الرياض الأساسي؛ جدّة للتعافي؛ خط SAMA-Net المخصّص.',
    lastTestedAt: _daysAgo(120),
    maturity: 5,
    status: 'APPROVED',
    annualCostSar: 5_800_000,
  },
  {
    id: 'STR-2026-003',
    processId: 16,
    primarySite: 'RIYADH-PRIMARY',
    alternateSite: 'EXTERNAL-CLOUD',
    manualWorkaroundEn: 'Manual SAMA submission via secure email attachment + signed cover letter. Used historically during 2018 power incident.',
    manualWorkaroundAr: 'تقديم يدوي لساما عبر مرفق بريد آمن + خطاب توضيحي موقّع. استُخدم تاريخياً في حادثة الكهرباء 2018.',
    activationOwner: 'Khalid Al-Harbi (CFO)',
    peopleMin: 4,
    techDependenciesEn: 'Reporting database + ETL pipeline + secure email gateway.',
    techDependenciesAr: 'قاعدة بيانات التقارير + خط ETL + بوابة بريد آمن.',
    supplierDependencies: ['SAS reporting suite', 'AWS Riyadh region'],
    premisesEn: 'Riyadh primary data center; CFO office.',
    premisesAr: 'مركز بيانات الرياض الأساسي؛ مكتب CFO.',
    lastTestedAt: _daysAgo(180),
    maturity: 4,
    status: 'APPROVED',
    annualCostSar: 380_000,
  },
];

const SEED_DRILLS: Drill[] = [
  {
    id: 'DRL-2026-001',
    planId: 5,
    type: 'FAILOVER',
    titleEn: 'Q1 datacenter failover drill — Riyadh → Jeddah',
    titleAr: 'تمرين تحويل مركز البيانات للربع الأول — الرياض ← جدّة',
    scenarioEn: 'Loss of primary site cooling — full failover of all Tier-1 services to Jeddah DR within 1h target.',
    scenarioAr: 'فقدان تبريد الموقع الأساسي — تحويل كامل لجميع خدمات الفئة 1 إلى موقع جدّة للتعافي في الهدف الزمني ساعة.',
    scheduledDate: _daysAgo(90).slice(0, 10),
    executedDate: _daysAgo(90).slice(0, 10),
    status: 'CLOSED',
    ncemaLed: false,
    facilitator: 'Ahmed Al-Qahtani (CTO)',
    participantsCount: 28,
    rtoActualHours: 0.9,
    rtoTargetHours: 1,
    whatWorkedEn: 'Failover succeeded under target. Auto-DNS swap completed in 4 minutes. Customer-facing services restored in 54 min.',
    whatWorkedAr: 'نجح التحوّل دون الهدف. تبديل DNS الآلي اكتمل في 4 دقائق. استُعيدت الخدمات للعملاء في 54 دقيقة.',
    whatDidntEn: 'Two runbook gaps: (1) HSM session resync took 18 min vs 5 min target; (2) SAMA notification template not pre-translated to Arabic.',
    whatDidntAr: 'فجوتان في الإجراء: (1) استئناف جلسة HSM استغرق 18 دقيقة مقابل هدف 5 دقائق؛ (2) قالب إبلاغ ساما غير مترجم مسبقاً إلى العربية.',
    capaCount: 2,
    score: 92,
  },
  {
    id: 'DRL-2026-002',
    planId: 1,
    type: 'WALKTHROUGH',
    titleEn: 'Core banking recovery walkthrough',
    titleAr: 'تجوال على استعادة النظام المصرفي الأساسي',
    scenarioEn: 'Tabletop walkthrough of core banking recovery sequence with operations + IT Ops + cards teams.',
    scenarioAr: 'تجوال نظري على تسلسل استعادة النظام المصرفي الأساسي مع فرق العمليات وتقنية المعلومات والبطاقات.',
    scheduledDate: _daysAgo(60).slice(0, 10),
    executedDate: _daysAgo(60).slice(0, 10),
    status: 'CLOSED',
    ncemaLed: false,
    facilitator: 'Mohammed Al-Otaibi',
    participantsCount: 15,
    rtoActualHours: 1.2,
    rtoTargetHours: 1,
    whatWorkedEn: 'Sequence understood by all stakeholders. Vendor escalation contacts in playbook found accurate.',
    whatWorkedAr: 'تسلسل العمل واضح لكل الأطراف. جهات تصعيد المورّدين في الدليل صحيحة.',
    whatDidntEn: 'Cards team not present at start; needed 20 min to mobilise. Should be on the auto-page list.',
    whatDidntAr: 'فريق البطاقات لم يحضر في البداية؛ احتاج 20 دقيقة للتحرّك. يجب إدراجه في قائمة النداء التلقائي.',
    capaCount: 1,
    score: 88,
  },
  {
    id: 'DRL-2026-003',
    planId: 7,
    type: 'CROSS_SECTOR',
    titleEn: 'NCEMA cross-sector cyber drill — Q3 2026',
    titleAr: 'تمرين الأمن السيبراني عبر القطاعات للربع الثالث 2026 — NCEMA',
    scenarioEn: 'Coordinated cyber-physical incident across banking sector with regulator notification cascade.',
    scenarioAr: 'حادث سيبراني-مادي منسّق عبر القطاع المصرفي مع تتالي إبلاغ الجهات التنظيمية.',
    scheduledDate: _daysAhead(48),
    status: 'PLANNED',
    ncemaLed: true,
    facilitator: 'Faisal Al-Maliki (NCEMA liaison)',
    participantsCount: 24,
    capaCount: 0,
  },
  {
    id: 'DRL-2026-004',
    planId: 4,
    type: 'FAILOVER',
    titleEn: 'Branch network LTE failover',
    titleAr: 'تحويل شبكة الفروع إلى LTE',
    scenarioEn: 'WAN-link outage in 3 branches; automatic LTE failover validation.',
    scenarioAr: 'انقطاع شبكة WAN في 3 فروع؛ التحقق من التحويل التلقائي إلى LTE.',
    scheduledDate: _daysAhead(15),
    status: 'PLANNED',
    ncemaLed: false,
    facilitator: 'Sara Al-Dosari',
    participantsCount: 12,
    capaCount: 0,
  },
];

const SEED_INVOCATIONS: Invocation[] = [
  {
    id: 'INV-2026-001',
    planId: 5,
    affectedProcessIds: [11, 12],
    status: 'POST_INCIDENT_REVIEW',
    disruptionEn: 'Primary site cooling failure due to chiller pump failure. Tier-1 services failed over to Jeddah DR.',
    disruptionAr: 'فشل تبريد الموقع الأساسي نتيجة عطل في مضخّة التبريد. الخدمات من الفئة 1 حُوِّلت إلى موقع جدّة.',
    declaredAt: _daysAgo(38) + 'T03:42:00Z',
    invokedAt: _daysAgo(38) + 'T04:15:00Z',
    stabilisedAt: _daysAgo(38) + 'T04:55:00Z',
    standDownAt: _daysAgo(37) + 'T11:15:00Z',
    actualRtoHours: 1.2,
    isReal: true,
    commander: 'Ahmed Al-Qahtani (CTO)',
    notifications: [
      { regulator: 'SAMA',  maxHours: 4,  basisEn: 'SAMA Operational Resilience Framework — Tier-1 service disruption',           basisAr: 'إطار المرونة التشغيلية ساما — انقطاع خدمة فئة 1',                            sentAt: _daysAgo(38) + 'T04:01:00Z', referenceId: 'SAMA-OPRES-2026-018' },
      { regulator: 'NCEMA', maxHours: 24, basisEn: 'NCEMA — material disruption to SCC Tier-1 banking infrastructure',             basisAr: 'NCEMA — انقطاع جوهري في بنية المصرفية الحرجة من الفئة 1 SCC',                  sentAt: _daysAgo(38) + 'T05:35:00Z', referenceId: 'NCEMA-2026-018' },
    ],
    stageLog: [
      { stepEn: 'Disruption declared by SOC',          stepAr: 'إعلان الانقطاع من SOC',           atTs: _daysAgo(38) + 'T03:42:00Z', byRole: 'SOC analyst' },
      { stepEn: 'CTO declared BCP invocation',         stepAr: 'إعلان CTO تفعيل خطة الاستمرارية', atTs: _daysAgo(38) + 'T03:55:00Z', byRole: 'CTO' },
      { stepEn: 'Failover initiated to Jeddah DR',     stepAr: 'بدء التحويل إلى موقع جدّة',       atTs: _daysAgo(38) + 'T04:15:00Z', byRole: 'IT Ops Lead' },
      { stepEn: 'Service restored on Jeddah DR',       stepAr: 'استعيدت الخدمة على موقع جدّة',     atTs: _daysAgo(38) + 'T04:55:00Z', byRole: 'IT Ops Lead' },
      { stepEn: 'Stabilised + customer comms sent',    stepAr: 'استقرّت الخدمة وأُرسلت الاتصالات',  atTs: _daysAgo(38) + 'T05:30:00Z', byRole: 'Head of Comms' },
      { stepEn: 'Failback to primary site complete',   stepAr: 'الرجوع إلى الموقع الأساسي اكتمل',  atTs: _daysAgo(37) + 'T10:45:00Z', byRole: 'IT Ops Lead' },
      { stepEn: 'Stand-down declared',                  stepAr: 'إعلان إنهاء التفعيل',              atTs: _daysAgo(37) + 'T11:15:00Z', byRole: 'CTO' },
    ],
    lessonsLearnedEn: 'RTO met (1.2h actual vs 1h target — within tolerance). HSM resync slower than runbook predicted. Updated chiller redundancy SLA with vendor.',
    lessonsLearnedAr: 'تحقّق هدف RTO (1.2 ساعة فعلي مقابل 1 ساعة هدف — ضمن التحمّل). إعادة تزامن HSM أبطأ من المتوقّع. حُدِّثت SLA لتكرار التبريد مع المورّد.',
  },
];

// ─── Persistence ────────────────────────────────────────────────────────────

export function loadBia(): BiaRecord[] {
  if (typeof window === 'undefined') return SEED_BIA;
  try {
    const raw = window.localStorage.getItem(BIA_KEY);
    return raw ? JSON.parse(raw) as BiaRecord[] : SEED_BIA;
  } catch { return SEED_BIA; }
}

export function saveBia(b: BiaRecord): BiaRecord {
  const all = loadBia().filter(x => x.id !== b.id);
  const next = [...all, b];
  try { window.localStorage.setItem(BIA_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return b;
}

export function transitionBia(id: string, status: BiaStatus, by?: string): BiaRecord | null {
  const b = loadBia().find(x => x.id === id);
  if (!b) return null;
  const updated: BiaRecord = { ...b, status };
  if (status === 'BCM_APPROVED') {
    updated.approvedAt = new Date().toISOString();
    updated.reviewer = by ?? b.reviewer;
    // 12-month auto-expiry per ISO 22301.
    updated.expiresAt = new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10);
    updated.nextReviewAt = new Date(Date.now() + 335 * 86_400_000).toISOString().slice(0, 10);
  }
  return saveBia(updated);
}

export function loadStrategies(): RecoveryStrategy[] {
  if (typeof window === 'undefined') return SEED_STRATEGIES;
  try {
    const raw = window.localStorage.getItem(STRATEGY_KEY);
    return raw ? JSON.parse(raw) as RecoveryStrategy[] : SEED_STRATEGIES;
  } catch { return SEED_STRATEGIES; }
}

export function saveStrategy(s: RecoveryStrategy): RecoveryStrategy {
  const all = loadStrategies().filter(x => x.id !== s.id);
  const next = [...all, s];
  try { window.localStorage.setItem(STRATEGY_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return s;
}

export function transitionStrategy(id: string, status: StrategyStatus): RecoveryStrategy | null {
  const s = loadStrategies().find(x => x.id === id);
  if (!s) return null;
  const updated: RecoveryStrategy = { ...s, status };
  if (status === 'TESTED') updated.lastTestedAt = new Date().toISOString();
  return saveStrategy(updated);
}

export function createStrategy(processId: number, primarySite: RecoverySite, alternateSite: RecoverySite, owner: string): RecoveryStrategy {
  const id = `STR-${new Date().getFullYear()}-${String(loadStrategies().length + 1).padStart(3, '0')}`;
  const s: RecoveryStrategy = {
    id, processId, primarySite, alternateSite,
    manualWorkaroundEn: '', manualWorkaroundAr: '',
    activationOwner: owner,
    peopleMin: 4,
    techDependenciesEn: '', techDependenciesAr: '',
    supplierDependencies: [],
    premisesEn: '', premisesAr: '',
    maturity: 1, status: 'DRAFT',
    annualCostSar: 0,
  };
  return saveStrategy(s);
}

export function createBia(processId: number, tier: ProcessTier, rtoHours: number, owner: string): BiaRecord {
  const id = `BIA-${new Date().getFullYear()}-${String(loadBia().length + 1).padStart(3, '0')}`;
  const b: BiaRecord = {
    id, processId, tier, rtoHours, rpoHours: rtoHours / 4, mtpdHours: rtoHours * 4,
    financialImpactSarPerHour: 50_000,
    regulatoryImpactEn: '', regulatoryImpactAr: '',
    customerImpactEn: '', customerImpactAr: '',
    processOwner: owner,
    status: 'DRAFT',
    assessedAt: new Date().toISOString(),
    nextReviewAt: new Date(Date.now() + 335 * 86_400_000).toISOString().slice(0, 10),
    expiresAt: new Date(Date.now() + 365 * 86_400_000).toISOString().slice(0, 10),
  };
  return saveBia(b);
}

export function scheduleDrill(planId: number, type: DrillType, titleEn: string, titleAr: string, scheduledDate: string, ncemaLed: boolean): Drill {
  const id = `DRL-${new Date().getFullYear()}-${String(loadDrills().length + 1).padStart(3, '0')}`;
  const d: Drill = {
    id, planId, type, titleEn, titleAr,
    scenarioEn: '', scenarioAr: '',
    scheduledDate, status: 'PLANNED',
    ncemaLed, facilitator: 'Layla Al-Ghamdi (Head of BCM)',
    participantsCount: 10,
    capaCount: 0,
  };
  return saveDrill(d);
}

export function loadDrills(): Drill[] {
  if (typeof window === 'undefined') return SEED_DRILLS;
  try {
    const raw = window.localStorage.getItem(DRILL_KEY);
    return raw ? JSON.parse(raw) as Drill[] : SEED_DRILLS;
  } catch { return SEED_DRILLS; }
}

export function saveDrill(d: Drill): Drill {
  const all = loadDrills().filter(x => x.id !== d.id);
  const next = [...all, d];
  try { window.localStorage.setItem(DRILL_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  emit();
  return d;
}

export function transitionDrill(id: string, status: DrillStatus): Drill | null {
  const d = loadDrills().find(x => x.id === id);
  if (!d) return null;
  const updated: Drill = { ...d, status };
  if (status === 'EXECUTED' && !d.executedDate) updated.executedDate = new Date().toISOString().slice(0, 10);
  return saveDrill(updated);
}

export function loadInvocations(): Invocation[] {
  if (typeof window === 'undefined') return SEED_INVOCATIONS;
  try {
    const raw = window.localStorage.getItem(INVOCATION_KEY);
    return raw ? JSON.parse(raw) as Invocation[] : SEED_INVOCATIONS;
  } catch { return SEED_INVOCATIONS; }
}

export function saveInvocation(inv: Invocation): Invocation {
  const existing = loadInvocations();
  const next = [...existing.filter(x => x.id !== inv.id), inv];
  try { window.localStorage.setItem(INVOCATION_KEY, JSON.stringify(next)); } catch { /* ignore */ }
  // F2-202: no REST mirror here. `mirrorSaveToBackend`/`mirrorTransitionToBackend`
  // used to be called on this line; both returned before sending anything because the
  // persistence-mode switch they read had no setter in the product. This store is
  // declared BROWSER_LOCAL in `lib/lifecycleStores.ts` with its real endpoint and
  // measured row count — that declaration is gated; a silent no-op was not.
  emit();
  return inv;
}

/** Notification rules generated from a plan based on which processes are affected. */
export function notificationsForInvocation(planId: number, affectedProcessIds: number[]): RegulatoryNotification[] {
  const bias = loadBia().filter(b => affectedProcessIds.includes(b.processId));
  const tiers = new Set(bias.map(b => b.tier));
  const notifications: RegulatoryNotification[] = [];
  if (tiers.has('TIER-1-NATIONAL') || tiers.has('TIER-2-SECTORAL')) {
    notifications.push(
      { regulator: 'SAMA',  maxHours: 4,  basisEn: 'SAMA Operational Resilience Framework — material service disruption',         basisAr: 'إطار المرونة التشغيلية ساما — انقطاع جوهري في الخدمة' },
      { regulator: 'NCEMA', maxHours: 24, basisEn: 'NCEMA Business Continuity Standard — SCC Tier-1/2 disruption',                basisAr: 'معيار استمرارية الأعمال NCEMA — انقطاع SCC من الفئة 1/2' },
    );
  }
  if (tiers.has('TIER-1-NATIONAL')) {
    notifications.push(
      { regulator: 'NCA', maxHours: 1,  basisEn: 'NCA Incident Reporting — national-critical service disruption',                  basisAr: 'إبلاغ الهيئة الوطنية للأمن السيبراني — انقطاع خدمة حرجة وطنياً' },
    );
  }
  // Plan-specific (e.g. salary plan triggers Labour-related notification)
  const plan = BCM_PLANS.find(p => p.id === planId);
  if (plan?.planNameEn?.toLowerCase().includes('crisis')) {
    notifications.push(
      { regulator: 'CIVIL-DEFENSE', maxHours: 0.25, basisEn: 'Civil Defense — physical emergency notification',  basisAr: 'الدفاع المدني — إشعار طوارئ مادية' },
    );
  }
  return notifications;
}

export function declareInvocation(planId: number, processIds: number[], disruptionEn: string, disruptionAr: string, isReal: boolean, commander: string): Invocation {
  const id = `INV-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`;
  const now = new Date().toISOString();
  const inv: Invocation = {
    id, planId, affectedProcessIds: processIds,
    status: 'DECLARED',
    disruptionEn, disruptionAr,
    declaredAt: now,
    isReal, commander,
    notifications: notificationsForInvocation(planId, processIds),
    stageLog: [{
      stepEn: 'Disruption declared',
      stepAr: 'إعلان الانقطاع',
      atTs: now,
      byRole: commander,
    }],
  };
  return saveInvocation(inv);
}

export function transitionInvocation(id: string, status: InvocationStatus, byRole: string, narrativeEn?: string, narrativeAr?: string): Invocation | null {
  const inv = loadInvocations().find(x => x.id === id);
  if (!inv) return null;
  const now = new Date().toISOString();
  const stepEn =
    status === 'INVOKED'   ? 'BCP invoked — recovery activated' :
    status === 'STABILISED' ? 'Service stabilised on alternate site' :
    status === 'STAND_DOWN' ? 'Stand-down declared — failback complete' :
    status === 'POST_INCIDENT_REVIEW' ? 'Post-incident review opened' :
    `Status changed to ${status}`;
  const stepAr =
    status === 'INVOKED'   ? 'تفعيل خطة الاستمرارية — بدء التعافي' :
    status === 'STABILISED' ? 'استقرت الخدمة على الموقع البديل' :
    status === 'STAND_DOWN' ? 'إنهاء التفعيل — اكتمل الرجوع' :
    status === 'POST_INCIDENT_REVIEW' ? 'فُتح استعراض ما بعد الحادثة' :
    `تغيّرت الحالة إلى ${status}`;
  const updated: Invocation = {
    ...inv, status,
    stageLog: [...inv.stageLog, { stepEn, stepAr, atTs: now, byRole }],
  };
  if (status === 'INVOKED' && !inv.invokedAt) updated.invokedAt = now;
  if (status === 'STABILISED' && !inv.stabilisedAt) {
    updated.stabilisedAt = now;
    if (inv.invokedAt) {
      updated.actualRtoHours = Math.round((Date.now() - new Date(inv.invokedAt).getTime()) / 360_000) / 10;
    }
  }
  if (status === 'STAND_DOWN' && !inv.standDownAt) updated.standDownAt = now;
  if (status === 'POST_INCIDENT_REVIEW') {
    updated.lessonsLearnedEn = narrativeEn ?? inv.lessonsLearnedEn;
    updated.lessonsLearnedAr = narrativeAr ?? inv.lessonsLearnedAr;
  }
  // F2-202: no REST mirror here — see `lib/lifecycleStores.ts`.
  return saveInvocation(updated);
}

export function logInvocationNotification(invId: string, regulator: RegulatoryNotification['regulator'], referenceId: string): Invocation | null {
  const inv = loadInvocations().find(x => x.id === invId);
  if (!inv) return null;
  const updated: Invocation = {
    ...inv,
    notifications: inv.notifications.map(n =>
      n.regulator === regulator ? { ...n, sentAt: new Date().toISOString(), referenceId } : n,
    ),
  };
  return saveInvocation(updated);
}

export function activeInvocation(): Invocation | undefined {
  return loadInvocations().find(i => i.status !== 'STAND_DOWN' && i.status !== 'POST_INCIDENT_REVIEW');
}

export function subscribeBcm(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if ([BIA_KEY, STRATEGY_KEY, DRILL_KEY, INVOCATION_KEY].includes(e.key ?? '')) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

function emit() { try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ } }

// ─── OPRES packet generator ─────────────────────────────────────────────────

export function buildOpresPacket(orgEn: string, orgAr: string, quarter: string): string {
  const bias = loadBia();
  const drills = loadDrills();
  const invocations = loadInvocations();
  const strategies = loadStrategies();

  const lines: string[] = [];
  const div = '─'.repeat(64);
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });

  lines.push(div);
  lines.push('SAMA OPERATIONAL RESILIENCE FRAMEWORK — REPORTING PACKET');
  lines.push('إطار المرونة التشغيلية ساما — حزمة الإبلاغ');
  lines.push(div);
  lines.push(`Reporting entity: ${orgEn}`);
  lines.push(`الجهة          : ${orgAr}`);
  lines.push(`Quarter        : ${quarter}`);
  lines.push(`Generated      : ${new Date().toISOString()}`);
  lines.push('');

  lines.push('1. CRITICAL PROCESS INVENTORY · جرد العمليات الحيوية');
  const tierCounts: Record<ProcessTier, number> = { 'TIER-1-NATIONAL': 0, 'TIER-2-SECTORAL': 0, 'TIER-3-OPERATIONAL': 0, 'TIER-4-LOCAL': 0 };
  bias.forEach(b => tierCounts[b.tier]++);
  Object.entries(tierCounts).forEach(([tier, n]) => lines.push(`   ${tier.padEnd(22)}: ${n}`));
  lines.push('');

  lines.push('2. RTO COMPLIANCE · الالتزام بأهداف RTO');
  bias.forEach(b => {
    const proc = BCM_PROCESSES.find(p => p.id === b.processId);
    lines.push(`   [${b.tier}] ${proc?.processNameEn ?? '?'}`);
    lines.push(`        RTO ${b.rtoHours}h · RPO ${b.rpoHours}h · MTPD ${b.mtpdHours}h · ${(b.financialImpactSarPerHour / 1000).toFixed(0)}k SAR/h impact`);
    lines.push(`        Status: ${b.status} · Expires: ${b.expiresAt}`);
  });
  lines.push('');

  lines.push('3. RECOVERY STRATEGY MATURITY · نضج استراتيجيات التعافي');
  strategies.forEach(s => {
    const proc = BCM_PROCESSES.find(p => p.id === s.processId);
    lines.push(`   ${proc?.processNameEn ?? '?'}: maturity ${s.maturity}/5 · ${s.primarySite} → ${s.alternateSite}`);
  });
  lines.push('');

  lines.push('4. DRILL PROGRAMME · برنامج التمارين');
  const completed = drills.filter(d => d.status === 'CLOSED');
  const planned = drills.filter(d => d.status === 'PLANNED');
  const ncemaCount = drills.filter(d => d.ncemaLed).length;
  lines.push(`   Completed     : ${completed.length}`);
  lines.push(`   Planned       : ${planned.length}`);
  lines.push(`   NCEMA-led     : ${ncemaCount}`);
  if (completed.length > 0) {
    const avgScore = Math.round(completed.reduce((s, d) => s + (d.score ?? 0), 0) / completed.length);
    lines.push(`   Avg score     : ${avgScore}/100`);
  }
  lines.push('');

  lines.push('5. INVOCATION HISTORY · سجل التفعيلات');
  invocations.forEach(inv => {
    const plan = BCM_PLANS.find(p => p.id === inv.planId);
    lines.push(`   ${inv.id} · ${plan?.planNameEn ?? '?'}`);
    lines.push(`     Status        : ${inv.status} · ${inv.isReal ? 'REAL' : 'DRILL'}`);
    lines.push(`     Declared      : ${dt(inv.declaredAt)}`);
    if (inv.actualRtoHours != null) {
      lines.push(`     Actual RTO    : ${inv.actualRtoHours}h`);
    }
    lines.push(`     Notifications : ${inv.notifications.filter(n => n.sentAt).length}/${inv.notifications.length} sent`);
  });
  lines.push('');

  lines.push(div);
  lines.push('Generated by TSS GRC Platform');
  lines.push(div);
  return lines.join('\n');
}

/** Export P5 — SAMA OpRes packet as tabular sheets for a branded .xlsx
 *  (POST /api/v1/reporting/packet.xlsx). "Status" headers drive conditional formatting. */
export function buildOpresSheets(): { name: string; headers: string[]; rows: string[][] }[] {
  const bias = loadBia();
  const strategies = loadStrategies();
  const drills = loadDrills();
  const invocations = loadInvocations();
  const completed = drills.filter(d => d.status === 'CLOSED');
  const avg = completed.length ? Math.round(completed.reduce((s, d) => s + (d.score ?? 0), 0) / completed.length) : 0;
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });
  return [
    {
      name: 'RTO Compliance',
      headers: ['Tier', 'Process', 'RTO (h)', 'RPO (h)', 'MTPD (h)', 'Impact (k SAR/h)', 'Status', 'Expires'],
      rows: bias.map(b => {
        const proc = BCM_PROCESSES.find(p => p.id === b.processId);
        return [b.tier, proc?.processNameEn ?? '?', String(b.rtoHours), String(b.rpoHours), String(b.mtpdHours),
          (b.financialImpactSarPerHour / 1000).toFixed(0), b.status, b.expiresAt];
      }),
    },
    {
      name: 'Recovery Strategies',
      headers: ['Process', 'Maturity /5', 'Primary Site', 'Alternate Site'],
      rows: strategies.map(s => {
        const proc = BCM_PROCESSES.find(p => p.id === s.processId);
        return [proc?.processNameEn ?? '?', String(s.maturity), s.primarySite, s.alternateSite];
      }),
    },
    {
      name: 'Drill Programme',
      headers: ['Metric', 'Value'],
      rows: [
        ['Completed', String(completed.length)],
        ['Planned', String(drills.filter(d => d.status === 'PLANNED').length)],
        ['NCEMA-led', String(drills.filter(d => d.ncemaLed).length)],
        ['Avg score /100', completed.length ? String(avg) : '—'],
      ],
    },
    {
      name: 'Invocation History',
      headers: ['ID', 'Plan', 'Status', 'Type', 'Declared', 'Actual RTO (h)', 'Notifications'],
      rows: invocations.map(inv => {
        const plan = BCM_PLANS.find(p => p.id === inv.planId);
        return [inv.id, plan?.planNameEn ?? '?', inv.status, inv.isReal ? 'REAL' : 'DRILL', dt(inv.declaredAt),
          inv.actualRtoHours != null ? String(inv.actualRtoHours) : '',
          `${inv.notifications.filter(n => n.sentAt).length}/${inv.notifications.length}`];
      }),
    },
  ];
}

// ─── Display helpers ───────────────────────────────────────────────────────

export const TIER_LABEL: Record<ProcessTier, { en: string; ar: string }> = {
  'TIER-1-NATIONAL':    { en: 'Tier 1 — National',    ar: 'الفئة 1 — وطنية' },
  'TIER-2-SECTORAL':    { en: 'Tier 2 — Sectoral',    ar: 'الفئة 2 — قطاعية' },
  'TIER-3-OPERATIONAL': { en: 'Tier 3 — Operational', ar: 'الفئة 3 — تشغيلية' },
  'TIER-4-LOCAL':       { en: 'Tier 4 — Local',       ar: 'الفئة 4 — محلية' },
};

export const TIER_TONE: Record<ProcessTier, 'danger' | 'warning' | 'info' | 'neutral'> = {
  'TIER-1-NATIONAL': 'danger', 'TIER-2-SECTORAL': 'warning', 'TIER-3-OPERATIONAL': 'info', 'TIER-4-LOCAL': 'neutral',
};

export const BIA_STATUS_LABEL: Record<BiaStatus, { en: string; ar: string }> = {
  DRAFT:           { en: 'Draft',          ar: 'مسوّدة' },
  DEPT_REVIEW:     { en: 'Dept review',    ar: 'مراجعة الإدارة' },
  BCM_APPROVED:    { en: 'BCM approved',   ar: 'مُعتمَدة من BCM' },
  ANNUAL_REVIEW:   { en: 'Annual review',  ar: 'مراجعة سنوية' },
  EXPIRED:         { en: 'Expired',        ar: 'منتهية' },
};

export const BIA_STATUS_TONE: Record<BiaStatus, 'neutral' | 'info' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral', DEPT_REVIEW: 'info', BCM_APPROVED: 'success', ANNUAL_REVIEW: 'warning', EXPIRED: 'danger',
};

export const SITE_LABEL: Record<RecoverySite, { en: string; ar: string }> = {
  'RIYADH-PRIMARY': { en: 'Riyadh primary',   ar: 'الرياض — الأساسي' },
  'JEDDAH-DR':      { en: 'Jeddah DR',         ar: 'جدّة — التعافي' },
  'DAMMAM-COLD':    { en: 'Dammam cold',       ar: 'الدمام — احتياطي بارد' },
  'EXTERNAL-CLOUD': { en: 'External cloud',    ar: 'سحابة خارجية' },
  'MANUAL-PROCESS': { en: 'Manual process',    ar: 'عملية يدوية' },
};

export const STRATEGY_STATUS_TONE: Record<StrategyStatus, 'neutral' | 'success' | 'warning' | 'danger'> = {
  DRAFT: 'neutral', TESTED: 'success', APPROVED: 'success', NEEDS_UPDATE: 'warning',
};

export const DRILL_TYPE_LABEL: Record<DrillType, { en: string; ar: string }> = {
  TABLETOP:     { en: 'Tabletop',     ar: 'طاولة' },
  WALKTHROUGH:  { en: 'Walkthrough',  ar: 'تجوال' },
  FAILOVER:     { en: 'Failover',     ar: 'تحويل' },
  SIMULATION:   { en: 'Simulation',   ar: 'محاكاة' },
  FULL_DR:      { en: 'Full DR',      ar: 'تعافي كامل' },
  CROSS_SECTOR: { en: 'Cross-sector', ar: 'عبر القطاعات' },
};

export const DRILL_STATUS_TONE: Record<DrillStatus, 'neutral' | 'info' | 'warning' | 'success'> = {
  PLANNED: 'info', IN_PROGRESS: 'warning', EXECUTED: 'warning', AFTER_ACTION: 'warning', CLOSED: 'success',
};

export const DRILL_STATUS_LABEL: Record<DrillStatus, { en: string; ar: string }> = {
  PLANNED:      { en: 'Planned',       ar: 'مخطّط' },
  IN_PROGRESS:  { en: 'In progress',    ar: 'قيد التنفيذ' },
  EXECUTED:     { en: 'Executed',       ar: 'مُنفَّذ' },
  AFTER_ACTION: { en: 'After-action',   ar: 'استعراض' },
  CLOSED:       { en: 'Closed',         ar: 'مُغلَق' },
};

export const INVOCATION_STATUS_LABEL: Record<InvocationStatus, { en: string; ar: string }> = {
  STANDBY:                 { en: 'Standby',                  ar: 'استعداد' },
  DECLARED:                { en: 'Declared',                  ar: 'مُعلَن' },
  INVOKED:                 { en: 'Invoked',                   ar: 'مُفعَّل' },
  STABILISED:              { en: 'Stabilised',                ar: 'مستقرّ' },
  STAND_DOWN:              { en: 'Stand-down',                ar: 'مُنهى' },
  POST_INCIDENT_REVIEW:    { en: 'Post-incident review',      ar: 'مراجعة ما بعد الحادثة' },
};

export const INVOCATION_STATUS_TONE: Record<InvocationStatus, 'neutral' | 'warning' | 'danger' | 'info' | 'success'> = {
  STANDBY: 'neutral', DECLARED: 'warning', INVOKED: 'danger',
  STABILISED: 'info', STAND_DOWN: 'success', POST_INCIDENT_REVIEW: 'info',
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface BcmLifecycleStats {
  bcmProcessTotal: number;
  biaApproved: number;
  biaExpiringSoon: number; // within 60d
  biaExpired: number;
  tier1Count: number;
  tier2Count: number;
  strategiesApproved: number;
  drillsPlanned: number;
  drillsCompleted: number;
  ncemaCrossSector: number;
  avgDrillScore: number;
  rtoComplianceRate: number; // % of drills meeting RTO
  invocationsThisYear: number;
  activeInvocation: boolean;
  totalAnnualStrategySpendK: number;
}

export function aggregateBcm(): BcmLifecycleStats {
  const bias = loadBia();
  const strategies = loadStrategies();
  const drills = loadDrills();
  const invs = loadInvocations();

  const sixtyDaysFromNow = Date.now() + 60 * 86_400_000;
  const biaExpiringSoon = bias.filter(b => b.status === 'BCM_APPROVED' && new Date(b.expiresAt).getTime() < sixtyDaysFromNow).length;
  const biaExpired = bias.filter(b => new Date(b.expiresAt).getTime() < Date.now()).length;
  const drillsCompleted = drills.filter(d => d.status === 'CLOSED').length;
  const drillsPlanned = drills.filter(d => d.status === 'PLANNED').length;
  const closed = drills.filter(d => d.status === 'CLOSED' && d.score != null);
  const avgScore = closed.length > 0 ? Math.round(closed.reduce((s, d) => s + d.score!, 0) / closed.length) : 0;
  const drillsWithRto = drills.filter(d => d.rtoActualHours != null && d.rtoTargetHours != null);
  const rtoMet = drillsWithRto.filter(d => (d.rtoActualHours ?? 0) <= (d.rtoTargetHours ?? 0)).length;
  const rtoComplianceRate = drillsWithRto.length > 0 ? Math.round((rtoMet / drillsWithRto.length) * 100) : 0;
  const yearStart = new Date(new Date().getFullYear(), 0, 1).getTime();
  const invocationsThisYear = invs.filter(i => new Date(i.declaredAt).getTime() >= yearStart).length;
  const activeInvocation = invs.some(i => i.status !== 'STAND_DOWN' && i.status !== 'POST_INCIDENT_REVIEW');

  return {
    bcmProcessTotal: BCM_PROCESSES.length,
    biaApproved: bias.filter(b => b.status === 'BCM_APPROVED').length,
    biaExpiringSoon,
    biaExpired,
    tier1Count: bias.filter(b => b.tier === 'TIER-1-NATIONAL').length,
    tier2Count: bias.filter(b => b.tier === 'TIER-2-SECTORAL').length,
    strategiesApproved: strategies.filter(s => s.status === 'APPROVED').length,
    drillsPlanned,
    drillsCompleted,
    ncemaCrossSector: drills.filter(d => d.ncemaLed).length,
    avgDrillScore: avgScore,
    rtoComplianceRate,
    invocationsThisYear,
    activeInvocation,
    totalAnnualStrategySpendK: Math.round(strategies.reduce((s, st) => s + st.annualCostSar, 0) / 1000),
  };
}

export function pickProcessById(id: number): BcmProcessDto | undefined {
  return BCM_PROCESSES.find(p => p.id === id);
}

export function pickPlanById(id: number): BcmPlanDto | undefined {
  return BCM_PLANS.find(p => p.id === id);
}

// Re-export for the page.
export { BCM_PROCESSES, BCM_PLANS };
export type { BcmProcessDto, BcmPlanDto, BcmBiaDto };
