// Crisis management — Sprint 6 follow-on (C4) + production hardening.
//
// NCEMA (National Centre for Emergency Management) regulates Saudi
// crisis-management capability. Critical-infrastructure operators must:
//   • Maintain a Crisis Management Plan (CMP) covering scenarios from
//     cyber to physical to pandemic
//   • Hold annual / semi-annual exercises (tabletop or simulation)
//   • Participate in cross-sector NCEMA drills
//   • Maintain a notification + decision-making protocol with response
//     times in hours (not days)
//   • Classify themselves against the SCC (Saudi Critical Capabilities)
//     tiering used by NCEMA to drive notification + assistance priority.
//
// This module models scenarios, the SCC tier, the activation state machine,
// the call tree per scenario, the exercise calendar, the historical
// activation log AND the NCEMA report packet generator.

export type CrisisScenarioCategory =
  | 'CYBER'
  | 'PHYSICAL_THREAT'
  | 'NATURAL_DISASTER'
  | 'PANDEMIC'
  | 'SUPPLY_CHAIN'
  | 'FINANCIAL'
  | 'REPUTATIONAL';

export type ExerciseType = 'TABLETOP' | 'SIMULATION' | 'LIVE_DRILL' | 'CROSS_SECTOR';

export type ActivationStatus =
  | 'STANDBY'
  | 'ELEVATED'      // Pre-activation — monitoring escalated
  | 'ACTIVATED'     // Full crisis-team in-room
  | 'STAND_DOWN';

/**
 * Saudi Critical Capabilities (SCC) tiering used by NCEMA. Determines:
 *   • Mandatory NCEMA notification within X hours
 *   • Eligibility for cross-sector drill participation
 *   • Required CMP review cadence
 */
export type SccTier = 'TIER-1-NATIONAL' | 'TIER-2-SECTORAL' | 'TIER-3-OPERATIONAL' | 'TIER-4-LOCAL';

export interface CallTreeContact {
  /** Stable id within the call tree. */
  id: string;
  /** Step number in the cascade (1-based). */
  step: number;
  /** Bilingual role title. */
  roleEn: string;
  roleAr: string;
  /** Person's name as a snapshot (rotates). */
  nameEn: string;
  nameAr: string;
  /** E.164-formatted contact phone (Saudi mobile e.g. +9665...). */
  phone: string;
  /** Backup phone (alternate contact / on-call line). */
  backupPhone?: string;
  /** Email for the post-call confirmation. */
  email: string;
  /** Maximum time after step trigger to acknowledge, in minutes. */
  maxAckMinutes: number;
}

export interface CrisisScenario {
  code: string;
  titleEn: string;
  titleAr: string;
  category: CrisisScenarioCategory;
  /** Likelihood (1–5). */
  likelihood: number;
  /** Impact (1–5). */
  impact: number;
  /** Designated response leader role. */
  leaderRole: string;
  /** Target activation time in minutes (T+0 to T+x). */
  activationTargetMinutes: number;
  /** Last tabletop / drill date. */
  lastExerciseAt?: string;
  /** Last drill outcome score (0–100). */
  lastExerciseScore?: number;
  descriptionEn: string;
  descriptionAr: string;
  /** Per-scenario call tree (RACI cascade). */
  callTree: CallTreeContact[];
  /**
   * Regulators that need notification when this scenario is ACTIVATED, with
   * the maximum window allowed by Saudi law / regulation.
   */
  regulatoryNotifications: RegulatoryNotificationRule[];
}

export interface RegulatoryNotificationRule {
  /** Regulator code. */
  regulator: 'NCA' | 'SAMA' | 'SDAIA' | 'CMA' | 'CITC' | 'NCEMA' | 'MOH' | 'CIVIL-DEFENSE';
  /** Maximum notification window in hours from activation. */
  maxHours: number;
  /** Bilingual reason / legal anchor. */
  basisEn: string;
  basisAr: string;
}

export interface Exercise {
  code: string;
  titleEn: string;
  titleAr: string;
  scenarioCode?: string;
  type: ExerciseType;
  scheduledFor: string;
  status: 'PLANNED' | 'IN_PROGRESS' | 'COMPLETED' | 'CANCELLED';
  /** Cross-sector NCEMA-led? */
  ncemaLed: boolean;
  /** Score / readiness improvement (only if COMPLETED). */
  outcomeScore?: number;
  participantsCount: number;
  /** Bilingual lessons-learned summary (only if COMPLETED). */
  lessonsLearnedEn?: string;
  lessonsLearnedAr?: string;
}

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

// ─── Default call trees (reusable across scenarios) ───────────────────────────

const CT_CYBER: CallTreeContact[] = [
  { id: 'ct-1', step: 1, roleEn: 'On-call SOC analyst',  roleAr: 'محلّل SOC المناوب',           nameEn: 'Faisal Al-Otaibi',   nameAr: 'فيصل العتيبي', phone: '+966500000001', backupPhone: '+966500000901', email: 'soc-oncall@org.sa',     maxAckMinutes: 5 },
  { id: 'ct-2', step: 2, roleEn: 'CSIRT lead',           roleAr: 'قائد فريق الاستجابة',            nameEn: 'Mohammed Al-Sulaiman', nameAr: 'محمد السليمان', phone: '+966500000002', backupPhone: '+966500000902', email: 'csirt-lead@org.sa',    maxAckMinutes: 10 },
  { id: 'ct-3', step: 3, roleEn: 'CISO',                 roleAr: 'رئيس أمن المعلومات',            nameEn: 'Sara Al-Zahrani',     nameAr: 'سارة الزهراني', phone: '+966500000003', backupPhone: '+966500000903', email: 'ciso@org.sa',          maxAckMinutes: 15 },
  { id: 'ct-4', step: 4, roleEn: 'CIO',                  roleAr: 'الرئيس التنفيذي للتقنية',        nameEn: 'Khalid Al-Harbi',     nameAr: 'خالد الحربي',  phone: '+966500000004', email: 'cio@org.sa',           maxAckMinutes: 20 },
  { id: 'ct-5', step: 5, roleEn: 'CEO',                  roleAr: 'الرئيس التنفيذي',                  nameEn: 'Abdullah Al-Saud',    nameAr: 'عبدالله آل سعود', phone: '+966500000005', email: 'ceo@org.sa',           maxAckMinutes: 30 },
  { id: 'ct-6', step: 5, roleEn: 'DPO',                  roleAr: 'مسؤول حماية البيانات',           nameEn: 'Layla Al-Ghamdi',    nameAr: 'ليلى الغامدي', phone: '+966500000006', email: 'dpo@org.sa',           maxAckMinutes: 30 },
  { id: 'ct-7', step: 6, roleEn: 'Head of Communications', roleAr: 'مدير الاتصال المؤسسي',         nameEn: 'Hessa Al-Rasheed',  nameAr: 'حصة الرشيد',  phone: '+966500000007', email: 'comms@org.sa',         maxAckMinutes: 45 },
];

const CT_PHYSICAL: CallTreeContact[] = [
  { id: 'cp-1', step: 1, roleEn: 'Building security supervisor', roleAr: 'مشرف أمن المبنى',         nameEn: 'Yousef Al-Anazi',     nameAr: 'يوسف العنزي',  phone: '+966500000010', backupPhone: '+966500000910', email: 'physical-sec@org.sa', maxAckMinutes: 2 },
  { id: 'cp-2', step: 2, roleEn: 'Civil Defense liaison',        roleAr: 'منسّق الدفاع المدني',       nameEn: 'Mansour Al-Qahtani', nameAr: 'منصور القحطاني', phone: '+966500000011', email: 'civil-defense@org.sa', maxAckMinutes: 5 },
  { id: 'cp-3', step: 3, roleEn: 'COO',                          roleAr: 'الرئيس التنفيذي للعمليات',  nameEn: 'Norah Al-Mutairi',   nameAr: 'نورة المطيري', phone: '+966500000012', email: 'coo@org.sa',           maxAckMinutes: 10 },
  { id: 'cp-4', step: 4, roleEn: 'CHRO',                         roleAr: 'الرئيس التنفيذي للموارد البشرية', nameEn: 'Salman Al-Dossari', nameAr: 'سلمان الدوسري', phone: '+966500000013', email: 'chro@org.sa', maxAckMinutes: 15 },
  { id: 'cp-5', step: 5, roleEn: 'CEO',                          roleAr: 'الرئيس التنفيذي',           nameEn: 'Abdullah Al-Saud',  nameAr: 'عبدالله آل سعود', phone: '+966500000005', email: 'ceo@org.sa', maxAckMinutes: 30 },
];

const CT_SUPPLY: CallTreeContact[] = [
  { id: 'cs-1', step: 1, roleEn: 'Vendor relationship manager', roleAr: 'مدير علاقة المورّد',     nameEn: 'Reem Al-Shehri',    nameAr: 'ريم الشهري',  phone: '+966500000020', email: 'vendor-mgmt@org.sa',   maxAckMinutes: 10 },
  { id: 'cs-2', step: 2, roleEn: 'TPRM lead',                   roleAr: 'قائد إدارة مخاطر الأطراف الثالثة', nameEn: 'Tariq Al-Maliki',  nameAr: 'طارق المالكي', phone: '+966500000021', email: 'tprm-lead@org.sa',     maxAckMinutes: 20 },
  { id: 'cs-3', step: 3, roleEn: 'Head of Procurement',         roleAr: 'مدير المشتريات',          nameEn: 'Bandar Al-Subaie',  nameAr: 'بندر السبيعي', phone: '+966500000022', email: 'procurement@org.sa',   maxAckMinutes: 30 },
  { id: 'cs-4', step: 4, roleEn: 'COO',                         roleAr: 'الرئيس التنفيذي للعمليات', nameEn: 'Norah Al-Mutairi',  nameAr: 'نورة المطيري', phone: '+966500000012', email: 'coo@org.sa',         maxAckMinutes: 60 },
];

const CT_PANDEMIC: CallTreeContact[] = [
  { id: 'cd-1', step: 1, roleEn: 'Health & safety officer', roleAr: 'مسؤول الصحة والسلامة',     nameEn: 'Hanan Al-Yami',    nameAr: 'حنان اليامي', phone: '+966500000030', email: 'hsse@org.sa',           maxAckMinutes: 30 },
  { id: 'cd-2', step: 2, roleEn: 'CHRO',                    roleAr: 'الرئيس التنفيذي للموارد البشرية', nameEn: 'Salman Al-Dossari', nameAr: 'سلمان الدوسري', phone: '+966500000013', email: 'chro@org.sa', maxAckMinutes: 60 },
  { id: 'cd-3', step: 3, roleEn: 'COO',                     roleAr: 'الرئيس التنفيذي للعمليات',  nameEn: 'Norah Al-Mutairi',  nameAr: 'نورة المطيري', phone: '+966500000012', email: 'coo@org.sa', maxAckMinutes: 120 },
  { id: 'cd-4', step: 4, roleEn: 'MoH liaison',             roleAr: 'منسّق وزارة الصحة',          nameEn: 'Adel Al-Jubair',    nameAr: 'عادل الجبير',  phone: '+966500000031', email: 'moh-liaison@org.sa',  maxAckMinutes: 240 },
];

// ─── Scenarios ─────────────────────────────────────────────────────────────

export const SCENARIOS: CrisisScenario[] = [
  {
    code: 'CRS-RANSOMWARE-PROD',
    titleEn: 'Ransomware on production systems',
    titleAr: 'هجوم فدية على أنظمة الإنتاج',
    category: 'CYBER', likelihood: 3, impact: 5,
    leaderRole: 'CISO',
    activationTargetMinutes: 30,
    lastExerciseAt: _daysAgo(75), lastExerciseScore: 87,
    descriptionEn: 'Encryption ransomware impacting Tier-0 production. Activates IR + BCM + Crisis Comms in parallel.',
    descriptionAr: 'فدية تشفير تؤثّر على إنتاج الفئة 0. تُفعّل الاستجابة للحوادث + BCM + اتصالات الأزمات بالتوازي.',
    callTree: CT_CYBER,
    regulatoryNotifications: [
      { regulator: 'NCA',   maxHours: 1,  basisEn: 'NCA Incident Reporting Guidelines — Severity 1',                  basisAr: 'إرشادات إبلاغ حوادث الهيئة الوطنية للأمن السيبراني — خطورة 1' },
      { regulator: 'NCEMA', maxHours: 4,  basisEn: 'NCEMA cross-sector situational awareness for SCC Tier-1/2',         basisAr: 'وعي الموقف عبر القطاعات للفئة 1/2 من القدرات الحرجة' },
      { regulator: 'SAMA',  maxHours: 4,  basisEn: 'SAMA Cyber Resilience Framework Art. 4.5 (financial entities)',     basisAr: 'إطار المرونة السيبراني لساما المادة 4.5 (الجهات المالية)' },
      { regulator: 'SDAIA', maxHours: 72, basisEn: 'PDPL Art. 25 — personal-data-impacting incidents',                  basisAr: 'المادة 25 من نظام حماية البيانات الشخصية — الحوادث المؤثّرة على البيانات الشخصية' },
    ],
  },
  {
    code: 'CRS-AWS-OUTAGE',
    titleEn: 'AWS Riyadh region full outage',
    titleAr: 'انقطاع كامل لمنطقة AWS الرياض',
    category: 'CYBER', likelihood: 2, impact: 5,
    leaderRole: 'CIO',
    activationTargetMinutes: 60,
    lastExerciseAt: _daysAgo(120), lastExerciseScore: 76,
    descriptionEn: 'Sustained outage of primary cloud region. Failover to AWS Jeddah DR; SAMA notification at T+2h.',
    descriptionAr: 'انقطاع مستمرّ للمنطقة السحابية الأساسية. التحوّل إلى موقع جدّة للتعافي؛ إبلاغ ساما عند T+2 ساعة.',
    callTree: CT_CYBER,
    regulatoryNotifications: [
      { regulator: 'SAMA',  maxHours: 2,  basisEn: 'SAMA Operational Resilience — material outage',          basisAr: 'إطار المرونة التشغيلية ساما — انقطاع جوهري' },
      { regulator: 'NCEMA', maxHours: 4,  basisEn: 'NCEMA — cloud-region outage with national footprint',    basisAr: 'NCEMA — انقطاع منطقة سحابية بأثر وطني' },
    ],
  },
  {
    code: 'CRS-DATA-EXFIL',
    titleEn: 'Major customer-data exfiltration',
    titleAr: 'تسريب كبير لبيانات العملاء',
    category: 'CYBER', likelihood: 2, impact: 5,
    leaderRole: 'CEO + DPO',
    activationTargetMinutes: 30,
    lastExerciseAt: _daysAgo(45), lastExerciseScore: 91,
    descriptionEn: 'Confirmed exfiltration ≥ 100k personal records. SDAIA 72h + SAMA + media-statement protocol.',
    descriptionAr: 'تأكيد تسريب ≥ 100 ألف سجل شخصي. بروتوكول 72 ساعة لسدايا + ساما + بيان إعلامي.',
    callTree: CT_CYBER,
    regulatoryNotifications: [
      { regulator: 'SDAIA', maxHours: 72, basisEn: 'PDPL Art. 25 — breach affecting personal data',         basisAr: 'المادة 25 من نظام حماية البيانات الشخصية — خرق البيانات الشخصية' },
      { regulator: 'NCA',   maxHours: 1,  basisEn: 'NCA Incident Reporting Guidelines — Severity 1',         basisAr: 'إرشادات إبلاغ حوادث الهيئة الوطنية للأمن السيبراني — خطورة 1' },
      { regulator: 'SAMA',  maxHours: 4,  basisEn: 'SAMA Cyber Resilience Framework — material breach',     basisAr: 'إطار المرونة السيبراني لساما — خرق جوهري' },
    ],
  },
  {
    code: 'CRS-HQ-EVACUATION',
    titleEn: 'HQ evacuation (fire / threat)',
    titleAr: 'إخلاء المقر الرئيسي (حريق/تهديد)',
    category: 'PHYSICAL_THREAT', likelihood: 1, impact: 4,
    leaderRole: 'COO',
    activationTargetMinutes: 15,
    lastExerciseAt: _daysAgo(180), lastExerciseScore: 82,
    descriptionEn: 'Evacuate HQ + activate alternate-site working. Coordinate with Civil Defense.',
    descriptionAr: 'إخلاء المقر + تفعيل العمل من الموقع البديل. التنسيق مع الدفاع المدني.',
    callTree: CT_PHYSICAL,
    regulatoryNotifications: [
      { regulator: 'CIVIL-DEFENSE', maxHours: 0.25, basisEn: 'Civil Defense — physical emergency notification (15 min)', basisAr: 'الدفاع المدني — إشعار طوارئ مادية (15 دقيقة)' },
      { regulator: 'NCEMA',         maxHours: 4,    basisEn: 'NCEMA — physical incident at SCC site',                   basisAr: 'NCEMA — حادث مادي في موقع تصنيف SCC' },
    ],
  },
  {
    code: 'CRS-PANDEMIC-WAVE',
    titleEn: 'Pandemic resurgence',
    titleAr: 'موجة وبائية',
    category: 'PANDEMIC', likelihood: 1, impact: 3,
    leaderRole: 'CHRO + COO',
    activationTargetMinutes: 240,
    lastExerciseAt: _daysAgo(365), lastExerciseScore: 70,
    descriptionEn: 'Activate WFH-by-default protocol + branch-rotation schedule. Coordinate with MoH.',
    descriptionAr: 'تفعيل بروتوكول العمل من المنزل + جدول تناوب الفروع. التنسيق مع وزارة الصحة.',
    callTree: CT_PANDEMIC,
    regulatoryNotifications: [
      { regulator: 'MOH',   maxHours: 24, basisEn: 'MoH workplace health protocol activation',               basisAr: 'تفعيل بروتوكول الصحة في بيئة العمل لوزارة الصحة' },
      { regulator: 'NCEMA', maxHours: 24, basisEn: 'NCEMA — public health crisis with operational impact',   basisAr: 'NCEMA — أزمة صحية عامة بأثر تشغيلي' },
    ],
  },
  {
    code: 'CRS-VENDOR-FAIL',
    titleEn: 'Critical vendor sudden failure',
    titleAr: 'فشل مفاجئ لمورد حرج',
    category: 'SUPPLY_CHAIN', likelihood: 2, impact: 4,
    leaderRole: 'TPRM Lead + COO',
    activationTargetMinutes: 120,
    lastExerciseAt: _daysAgo(150), lastExerciseScore: 79,
    descriptionEn: 'Tier-1 vendor goes offline (bankruptcy / sanctions / hostile takeover). Activate fallback contracts.',
    descriptionAr: 'مورد فئة 1 ينقطع (إفلاس / عقوبات / استحواذ عدائي). تفعيل العقود الاحتياطية.',
    callTree: CT_SUPPLY,
    regulatoryNotifications: [
      { regulator: 'SAMA',  maxHours: 24, basisEn: 'SAMA Outsourcing Rules — material vendor failure',        basisAr: 'قواعد الإسناد لساما — فشل مورّد جوهري' },
      { regulator: 'NCEMA', maxHours: 24, basisEn: 'NCEMA — supply-chain shock to SCC operations',            basisAr: 'NCEMA — صدمة سلسلة إمداد لعمليات SCC' },
    ],
  },
];

// ─── Exercises ─────────────────────────────────────────────────────────────

export const EXERCISES: Exercise[] = [
  {
    code: 'EX-2026-Q3-NCEMA',
    titleEn: 'NCEMA cross-sector cyber drill — Q3 2026',
    titleAr: 'تمرين الأمن السيبراني عبر القطاعات للربع الثالث 2026',
    scenarioCode: 'CRS-RANSOMWARE-PROD',
    type: 'CROSS_SECTOR',
    scheduledFor: _daysAhead(48),
    status: 'PLANNED',
    ncemaLed: true,
    participantsCount: 24,
  },
  {
    code: 'EX-2026-Q2-AWS',
    titleEn: 'AWS Riyadh failover simulation',
    titleAr: 'محاكاة التحوّل عن AWS الرياض',
    scenarioCode: 'CRS-AWS-OUTAGE',
    type: 'SIMULATION',
    scheduledFor: _daysAgo(15),
    status: 'COMPLETED',
    ncemaLed: false,
    outcomeScore: 76,
    participantsCount: 18,
    lessonsLearnedEn: 'Failover succeeded but SAMA notification was 18 minutes late. Update on-call escalation matrix to include the alternate Treasurer when primary is unreachable.',
    lessonsLearnedAr: 'نجح التحوّل لكن إبلاغ ساما تأخّر 18 دقيقة. تحديث مصفوفة التصعيد لتشمل أمين الخزينة البديل عند تعذّر الوصول للأساسي.',
  },
  {
    code: 'EX-2026-Q2-DATA',
    titleEn: 'Data exfiltration tabletop',
    titleAr: 'تمرين طاولة لتسريب البيانات',
    scenarioCode: 'CRS-DATA-EXFIL',
    type: 'TABLETOP',
    scheduledFor: _daysAgo(45),
    status: 'COMPLETED',
    ncemaLed: false,
    outcomeScore: 91,
    participantsCount: 12,
    lessonsLearnedEn: 'PDPL Art. 25 packet generation worked end-to-end. Decision tree for data-subject notification revised to clarify HIGH-impact threshold.',
    lessonsLearnedAr: 'إنشاء حزمة المادة 25 من PDPL تمّ بنجاح. مراجعة شجرة قرار إبلاغ أصحاب البيانات لتوضيح عتبة الأثر العالي.',
  },
  {
    code: 'EX-2026-Q1-EVAC',
    titleEn: 'HQ evacuation drill',
    titleAr: 'تمرين إخلاء المقر',
    scenarioCode: 'CRS-HQ-EVACUATION',
    type: 'LIVE_DRILL',
    scheduledFor: _daysAgo(180),
    status: 'COMPLETED',
    ncemaLed: false,
    outcomeScore: 82,
    participantsCount: 410,
    lessonsLearnedEn: 'Full evacuation in 11 minutes (target 15). Two stairwells need re-marking; signage refresh ordered.',
    lessonsLearnedAr: 'إخلاء كامل في 11 دقيقة (الهدف 15). سلّمَين بحاجة إعادة وسم؛ تجديد اللافتات تمّ طلبه.',
  },
  {
    code: 'EX-2026-Q4-RISK',
    titleEn: 'Quarterly Risk Committee tabletop',
    titleAr: 'تمرين طاولة فصلي للجنة المخاطر',
    type: 'TABLETOP',
    scheduledFor: _daysAhead(110),
    status: 'PLANNED',
    ncemaLed: false,
    participantsCount: 8,
  },
];

// ─── Activation log + state machine ──────────────────────────────────────────

export interface ActivationStageLog {
  /** Stage code matching the call-tree contact id, OR a system stage. */
  stageCode: string;
  /** Bilingual label — for system stages, this is set inline; for contacts, mirrored from CallTreeContact. */
  labelEn: string;
  labelAr: string;
  /** Time entered (ISO). */
  enteredAt: string;
  /** Time exited (ISO) — empty while still on this stage. */
  exitedAt?: string;
  /** Optional note recorded by the operator. */
  noteEn?: string;
  noteAr?: string;
  /** Whether this stage was acknowledged by the contact. */
  acked?: boolean;
  /** ISO timestamp for the acknowledgement. */
  ackedAt?: string;
}

export interface RegulatoryNotificationLog {
  regulator: RegulatoryNotificationRule['regulator'];
  /** ISO timestamp when sent. */
  sentAt?: string;
  /** Reference number returned by the regulator's portal. */
  referenceId?: string;
  /** Free-text status. */
  notesEn?: string;
  notesAr?: string;
}

export interface ActivationEvent {
  id: string;
  scenarioCode: string;
  status: ActivationStatus;
  startedAt: string;
  endedAt?: string;
  /** Time-to-activation actual (minutes T+0 → declared). */
  actualMinutes?: number;
  notesEn: string;
  notesAr: string;
  /** Per-stage log captured as the operator advances through the call tree. */
  stageLog: ActivationStageLog[];
  /** Regulator notification log. */
  notifications: RegulatoryNotificationLog[];
  /** Whether this is a real activation (true) or a drill (false). */
  isReal: boolean;
}

export const ACTIVATION_HISTORY: ActivationEvent[] = [
  {
    id: 'ACT-2026-001',
    scenarioCode: 'CRS-RANSOMWARE-PROD',
    status: 'STAND_DOWN',
    startedAt: _daysAgo(38) + 'T03:42:00Z',
    endedAt: _daysAgo(37) + 'T11:15:00Z',
    actualMinutes: 22,
    isReal: true,
    notesEn: 'Phishing-driven ransomware attempt on legacy file share. Containment within 90 min. No data exfiltration confirmed.',
    notesAr: 'محاولة فدية بدأت من تصيّد على نظام ملفات قديم. الاحتواء خلال 90 دقيقة. لم يتأكد تسريب بيانات.',
    stageLog: [],
    notifications: [
      { regulator: 'NCA',   sentAt: _daysAgo(38) + 'T04:01:00Z', referenceId: 'NCA-IR-2026-0142' },
      { regulator: 'NCEMA', sentAt: _daysAgo(38) + 'T04:35:00Z', referenceId: 'NCEMA-2026-018' },
    ],
  },
];

// ─── Persistence (live activation state) ────────────────────────────────────

const ACTIVE_KEY = 'grc.crisis.active.v1';
const HISTORY_KEY = 'grc.crisis.history.v1';
const SCC_KEY = 'grc.crisis.scc.v1';
const EVENT = 'grc.crisis.changed';

export function loadActive(): ActivationEvent | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(ACTIVE_KEY);
    return raw ? JSON.parse(raw) as ActivationEvent : null;
  } catch { return null; }
}

export function loadHistory(): ActivationEvent[] {
  if (typeof window === 'undefined') return ACTIVATION_HISTORY;
  try {
    const raw = window.localStorage.getItem(HISTORY_KEY);
    return raw ? [...ACTIVATION_HISTORY, ...JSON.parse(raw) as ActivationEvent[]] : ACTIVATION_HISTORY;
  } catch { return ACTIVATION_HISTORY; }
}

export function loadSccTier(): SccTier {
  if (typeof window === 'undefined') return 'TIER-2-SECTORAL';
  try {
    const raw = window.localStorage.getItem(SCC_KEY);
    return (raw as SccTier) ?? 'TIER-2-SECTORAL';
  } catch { return 'TIER-2-SECTORAL'; }
}

export function saveSccTier(tier: SccTier): void {
  try { window.localStorage.setItem(SCC_KEY, tier); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

function emit() {
  try { window.dispatchEvent(new CustomEvent(EVENT)); } catch { /* ignore */ }
}

export function startActivation(scenarioCode: string, isDrill: boolean = false): ActivationEvent {
  const scenario = SCENARIOS.find(s => s.code === scenarioCode);
  if (!scenario) throw new Error(`Unknown scenario: ${scenarioCode}`);
  const id = `ACT-${new Date().getFullYear()}-${Math.random().toString(36).slice(2, 8).toUpperCase()}`;
  const startedAt = new Date().toISOString();
  const event: ActivationEvent = {
    id, scenarioCode,
    status: 'ELEVATED',
    startedAt,
    isReal: !isDrill,
    notesEn: '', notesAr: '',
    stageLog: [{
      stageCode: 'system:elevated',
      labelEn: 'Elevated — monitoring escalated', labelAr: 'مرتفع — تصعيد المراقبة',
      enteredAt: startedAt,
    }],
    notifications: scenario.regulatoryNotifications.map(r => ({ regulator: r.regulator })),
  };
  try { window.localStorage.setItem(ACTIVE_KEY, JSON.stringify(event)); } catch { /* ignore */ }
  emit();
  return event;
}

export function transitionActivation(status: ActivationStatus, noteEn?: string, noteAr?: string): ActivationEvent | null {
  const active = loadActive();
  if (!active) return null;
  const now = new Date().toISOString();
  // Close the current stage.
  const updated: ActivationEvent = {
    ...active, status,
    stageLog: [
      ...active.stageLog.map((s, i, arr) => i === arr.length - 1 && !s.exitedAt ? { ...s, exitedAt: now } : s),
      {
        stageCode: `system:${status.toLowerCase()}`,
        labelEn: status === 'ACTIVATED' ? 'Crisis room activated' :
                 status === 'STAND_DOWN' ? 'Stand-down' :
                 status === 'STANDBY' ? 'Standby' : 'Elevated',
        labelAr: status === 'ACTIVATED' ? 'تفعيل غرفة الأزمات' :
                 status === 'STAND_DOWN' ? 'إنهاء التفعيل' :
                 status === 'STANDBY' ? 'استعداد' : 'مرتفع',
        enteredAt: now,
        noteEn, noteAr,
      },
    ],
  };
  if (status === 'ACTIVATED' && !active.actualMinutes) {
    updated.actualMinutes = Math.round((Date.now() - new Date(active.startedAt).getTime()) / 60_000);
  }
  if (status === 'STAND_DOWN') {
    updated.endedAt = now;
    // Push to history and clear active.
    const persisted = loadHistory().filter(h => h.id !== updated.id);
    try {
      window.localStorage.setItem(HISTORY_KEY, JSON.stringify([
        ...persisted.filter(h => !ACTIVATION_HISTORY.some(b => b.id === h.id)),
        updated,
      ]));
      window.localStorage.removeItem(ACTIVE_KEY);
    } catch { /* ignore */ }
  } else {
    try { window.localStorage.setItem(ACTIVE_KEY, JSON.stringify(updated)); } catch { /* ignore */ }
  }
  emit();
  return updated;
}

export function ackContact(contactId: string): ActivationEvent | null {
  const active = loadActive();
  if (!active) return null;
  const scenario = SCENARIOS.find(s => s.code === active.scenarioCode);
  const contact = scenario?.callTree.find(c => c.id === contactId);
  if (!contact) return null;
  const now = new Date().toISOString();
  const existing = active.stageLog.find(s => s.stageCode === contactId);
  let stageLog: ActivationStageLog[];
  if (existing) {
    stageLog = active.stageLog.map(s => s.stageCode === contactId ? { ...s, acked: true, ackedAt: now } : s);
  } else {
    stageLog = [
      ...active.stageLog,
      {
        stageCode: contactId,
        labelEn: `Step ${contact.step}: ${contact.roleEn} (${contact.nameEn})`,
        labelAr: `الخطوة ${contact.step}: ${contact.roleAr} (${contact.nameAr})`,
        enteredAt: now,
        acked: true, ackedAt: now,
      },
    ];
  }
  const updated = { ...active, stageLog };
  try { window.localStorage.setItem(ACTIVE_KEY, JSON.stringify(updated)); } catch { /* ignore */ }
  emit();
  return updated;
}

export function logRegulatoryNotification(
  regulator: RegulatoryNotificationRule['regulator'],
  referenceId: string,
): ActivationEvent | null {
  const active = loadActive();
  if (!active) return null;
  const now = new Date().toISOString();
  const updated: ActivationEvent = {
    ...active,
    notifications: active.notifications.map(n =>
      n.regulator === regulator ? { ...n, sentAt: now, referenceId } : n,
    ),
  };
  try { window.localStorage.setItem(ACTIVE_KEY, JSON.stringify(updated)); } catch { /* ignore */ }
  emit();
  return updated;
}

export function subscribeCrisis(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  const storage = (e: StorageEvent) => {
    if (e.key === ACTIVE_KEY || e.key === HISTORY_KEY || e.key === SCC_KEY) cb();
  };
  window.addEventListener('storage', storage);
  return () => {
    window.removeEventListener(EVENT, handler);
    window.removeEventListener('storage', storage);
  };
}

// ─── NCEMA report packet generator ───────────────────────────────────────────

/**
 * Build a NCEMA-compliant initial situational-awareness packet. Format mirrors
 * the National Cybersecurity Authority + NCEMA joint reporting template:
 *
 *   1. Reporting entity (name, sector, SCC tier)
 *   2. Incident identifiers (timestamp, scenario code, severity)
 *   3. Description (English + Arabic narrative)
 *   4. Impact assessment (operational, customer, regulatory)
 *   5. Containment + response actions taken
 *   6. Regulatory notification log
 *   7. Next steps + named decision-maker
 *
 * Returns a downloadable plain-text packet (UTF-8) so it can be uploaded
 * to the NCA "Haseen" portal or NCEMA situational room directly.
 */
export function buildNcemaPacket(
  activation: ActivationEvent,
  organisationNameEn: string,
  organisationNameAr: string,
  sccTier: SccTier,
): string {
  const scenario = SCENARIOS.find(s => s.code === activation.scenarioCode);
  if (!scenario) return '';
  const lines: string[] = [];
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });
  const div = '─'.repeat(64);

  lines.push(div);
  lines.push('NCEMA / NCA — INITIAL SITUATIONAL AWARENESS PACKET');
  lines.push('حزمة الإحاطة الأولية — NCEMA / NCA');
  lines.push(div);
  lines.push('');
  lines.push('1. REPORTING ENTITY · الجهة المُبلِّغة');
  lines.push(`   Name (EN): ${organisationNameEn}`);
  lines.push(`   الاسم  : ${organisationNameAr}`);
  lines.push(`   SCC Tier: ${sccTier}`);
  lines.push('');
  lines.push('2. INCIDENT IDENTIFIERS · بيانات الحادثة');
  lines.push(`   Activation ID    : ${activation.id}`);
  lines.push(`   Scenario code    : ${scenario.code}`);
  lines.push(`   Scenario (EN)    : ${scenario.titleEn}`);
  lines.push(`   Scenario (AR)    : ${scenario.titleAr}`);
  lines.push(`   Started (KSA)    : ${dt(activation.startedAt)}`);
  if (activation.actualMinutes != null) {
    lines.push(`   Time-to-activate : ${activation.actualMinutes} minutes (target ${scenario.activationTargetMinutes})`);
  }
  lines.push(`   Real / Drill     : ${activation.isReal ? 'REAL' : 'DRILL'}`);
  lines.push('');
  lines.push('3. DESCRIPTION · الوصف');
  lines.push(`   EN: ${scenario.descriptionEn}`);
  lines.push(`   AR: ${scenario.descriptionAr}`);
  if (activation.notesEn) lines.push(`   Operator notes (EN): ${activation.notesEn}`);
  if (activation.notesAr) lines.push(`   ملاحظات المشغّل (AR): ${activation.notesAr}`);
  lines.push('');
  lines.push('4. IMPACT ASSESSMENT · تقييم الأثر');
  lines.push(`   Likelihood × Impact: ${scenario.likelihood} × ${scenario.impact}`);
  lines.push(`   Lead role         : ${scenario.leaderRole}`);
  lines.push('');
  lines.push('5. RESPONSE ACTIONS · إجراءات الاستجابة');
  if (activation.stageLog.length === 0) {
    lines.push('   (none recorded)');
  } else {
    activation.stageLog.forEach((s, idx) => {
      lines.push(`   ${String(idx + 1).padStart(2, '0')}. ${s.labelEn}`);
      lines.push(`        ${s.labelAr}`);
      lines.push(`        Entered: ${dt(s.enteredAt)}${s.acked ? ` · ACK ${dt(s.ackedAt!)}` : ''}`);
      if (s.noteEn) lines.push(`        EN: ${s.noteEn}`);
      if (s.noteAr) lines.push(`        AR: ${s.noteAr}`);
    });
  }
  lines.push('');
  lines.push('6. REGULATORY NOTIFICATIONS · الإخطارات النظامية');
  scenario.regulatoryNotifications.forEach(rule => {
    const log = activation.notifications.find(n => n.regulator === rule.regulator);
    const sent = log?.sentAt ? `SENT ${dt(log.sentAt)}` : 'PENDING';
    const ref = log?.referenceId ? ` · ref ${log.referenceId}` : '';
    lines.push(`   ${rule.regulator.padEnd(13)} ${sent}${ref}`);
    lines.push(`     window: ≤ ${rule.maxHours}h · ${rule.basisEn}`);
    lines.push(`     ${rule.basisAr}`);
  });
  lines.push('');
  lines.push('7. NEXT STEPS · الخطوات التالية');
  lines.push(`   Decision-maker on duty: ${scenario.leaderRole}`);
  lines.push(`   Status                : ${activation.status}`);
  lines.push(`   Next review           : T+1h from packet generation`);
  lines.push('');
  lines.push(div);
  lines.push(`Generated by TSS GRC Platform · ${new Date().toISOString()}`);
  lines.push(div);
  return lines.join('\n');
}

/** Export P5 — NCEMA notification as tabular sheets for a branded .xlsx/.docx. */
export function buildNcemaSheets(
  activation: ActivationEvent,
  sccTier: SccTier,
): { name: string; headers: string[]; rows: string[][] }[] {
  const scenario = SCENARIOS.find(s => s.code === activation.scenarioCode);
  if (!scenario) return [];
  const dt = (iso: string) => new Date(iso).toLocaleString('en-GB', { timeZone: 'Asia/Riyadh' });
  return [
    {
      name: 'Incident Summary',
      headers: ['Field', 'Value'],
      rows: [
        ['SCC Tier', String(sccTier)],
        ['Activation ID', activation.id],
        ['Scenario code', scenario.code],
        ['Scenario', scenario.titleEn],
        ['Started (KSA)', dt(activation.startedAt)],
        ['Real / Drill', activation.isReal ? 'REAL' : 'DRILL'],
        ['Likelihood × Impact', `${scenario.likelihood} × ${scenario.impact}`],
        ['Lead role', scenario.leaderRole],
        ['Status', activation.status],
      ],
    },
    {
      name: 'Response Actions',
      headers: ['#', 'Stage', 'Entered', 'Acknowledged'],
      rows: activation.stageLog.map((s, i) => [String(i + 1), s.labelEn, dt(s.enteredAt),
        s.acked && s.ackedAt ? dt(s.ackedAt) : '—']),
    },
    {
      name: 'Regulatory Notifications',
      headers: ['Regulator', 'Status', 'Reference', 'Window (h)'],
      rows: scenario.regulatoryNotifications.map(rule => {
        const log = activation.notifications.find(n => n.regulator === rule.regulator);
        return [rule.regulator, log?.sentAt ? 'SENT' : 'PENDING', log?.referenceId ?? '—', String(rule.maxHours)];
      }),
    },
  ];
}

// ─── Stats + display helpers ───────────────────────────────────────────────

export const CATEGORY_LABEL: Record<CrisisScenarioCategory, { en: string; ar: string }> = {
  CYBER:            { en: 'Cyber',                 ar: 'سيبراني' },
  PHYSICAL_THREAT:  { en: 'Physical threat',        ar: 'تهديد مادي' },
  NATURAL_DISASTER: { en: 'Natural disaster',       ar: 'كارثة طبيعية' },
  PANDEMIC:         { en: 'Pandemic',               ar: 'وباء' },
  SUPPLY_CHAIN:     { en: 'Supply chain',           ar: 'سلسلة الإمداد' },
  FINANCIAL:        { en: 'Financial',              ar: 'مالي' },
  REPUTATIONAL:     { en: 'Reputational',           ar: 'سمعي' },
};

export const TYPE_LABEL: Record<ExerciseType, { en: string; ar: string }> = {
  TABLETOP:     { en: 'Tabletop',     ar: 'طاولة' },
  SIMULATION:   { en: 'Simulation',   ar: 'محاكاة' },
  LIVE_DRILL:   { en: 'Live drill',   ar: 'تمرين حي' },
  CROSS_SECTOR: { en: 'Cross-sector', ar: 'عبر القطاعات' },
};

export const ACTIVATION_TONE: Record<ActivationStatus, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  STANDBY:    'success',
  ELEVATED:   'warning',
  ACTIVATED:  'danger',
  STAND_DOWN: 'neutral',
};

export const ACTIVATION_LABEL: Record<ActivationStatus, { en: string; ar: string }> = {
  STANDBY:    { en: 'Standby',     ar: 'استعداد' },
  ELEVATED:   { en: 'Elevated',    ar: 'مرتفع' },
  ACTIVATED:  { en: 'Activated',   ar: 'مُفعَّل' },
  STAND_DOWN: { en: 'Stand-down',  ar: 'إنهاء' },
};

export const SCC_LABEL: Record<SccTier, { en: string; ar: string; descriptionEn: string; descriptionAr: string }> = {
  'TIER-1-NATIONAL': {
    en: 'Tier 1 — National Critical', ar: 'الفئة 1 — حرج وطنياً',
    descriptionEn: 'Operations whose interruption affects Saudi national security or essential public services. Mandatory notification within 1 hour.',
    descriptionAr: 'العمليات التي يؤثّر انقطاعها على الأمن الوطني أو الخدمات العامة الحيوية. الإبلاغ الإلزامي خلال ساعة واحدة.',
  },
  'TIER-2-SECTORAL': {
    en: 'Tier 2 — Sector Critical', ar: 'الفئة 2 — حرج قطاعياً',
    descriptionEn: 'Operations whose interruption affects an entire sector (e.g. major bank, primary telco). Notification within 4 hours.',
    descriptionAr: 'العمليات التي يؤثّر انقطاعها على قطاع كامل (بنك رئيسي، مزوّد اتصالات أوّلي). الإبلاغ خلال 4 ساعات.',
  },
  'TIER-3-OPERATIONAL': {
    en: 'Tier 3 — Operational', ar: 'الفئة 3 — تشغيلي',
    descriptionEn: 'Operations limited to a single organisation with significant size. Notification within 24 hours.',
    descriptionAr: 'العمليات المحدودة بمنشأة واحدة كبيرة الحجم. الإبلاغ خلال 24 ساعة.',
  },
  'TIER-4-LOCAL': {
    en: 'Tier 4 — Local', ar: 'الفئة 4 — محلي',
    descriptionEn: 'Limited operations with low impact outside the entity. NCEMA notification voluntary; record-keeping mandatory.',
    descriptionAr: 'عمليات محدودة بأثر منخفض خارج المنشأة. إبلاغ NCEMA طوعي؛ التوثيق إلزامي.',
  },
};

export interface CrisisStats {
  totalScenarios: number;
  cyberScenarios: number;
  highImpactScenarios: number; // impact >= 4
  exercisesPlanned: number;
  exercisesCompleted: number;
  ncemaCrossSector: number;
  avgExerciseScore: number;
  oldestExercise: number; // days since last exercise across scenarios
}

export function aggregateCrisis(): CrisisStats {
  const cyberScenarios = SCENARIOS.filter(s => s.category === 'CYBER').length;
  const highImpactScenarios = SCENARIOS.filter(s => s.impact >= 4).length;
  const exercisesPlanned = EXERCISES.filter(e => e.status === 'PLANNED' || e.status === 'IN_PROGRESS').length;
  const completed = EXERCISES.filter(e => e.status === 'COMPLETED');
  const exercisesCompleted = completed.length;
  const ncemaCrossSector = EXERCISES.filter(e => e.ncemaLed).length;
  const scoreSum = completed.reduce((s, e) => s + (e.outcomeScore ?? 0), 0);
  const avgExerciseScore = completed.length > 0 ? Math.round(scoreSum / completed.length) : 0;
  const now = Date.now();
  const oldest = SCENARIOS.reduce((max, s) => {
    if (!s.lastExerciseAt) return max;
    const days = Math.floor((now - new Date(s.lastExerciseAt).getTime()) / 86_400_000);
    return Math.max(max, days);
  }, 0);
  return {
    totalScenarios: SCENARIOS.length,
    cyberScenarios, highImpactScenarios,
    exercisesPlanned, exercisesCompleted, ncemaCrossSector,
    avgExerciseScore,
    oldestExercise: oldest,
  };
}
