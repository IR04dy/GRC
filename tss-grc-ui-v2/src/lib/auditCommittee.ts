// Audit Committee charter + meeting register.
//
// ServiceNow IRM equivalent: `audit_committee_meeting` table — each
// meeting is its own record with agenda + minutes + attendance +
// decisions, all linked back to the charter that mandates the
// committee's authority.
//
// Standards anchor:
//   CMA Listing Rules §53 — listed-entity Audit Committee composition,
//                           independence (≥2/3 non-executive), charter,
//                           meeting cadence (≥4/year), reporting duties
//   Saudi Companies Law Art.103 — Audit Committee mandate
//   IIA IPPF Three Lines (Audit Committee = ultimate Internal-Audit
//                          oversight)
//   COSO ERM Component 1 — Governance + Culture, Audit-Cte role
//   COBIT EDM05 — stakeholder reporting + transparency

export type CommitteeRole =
  | 'CHAIR'                  // Committee chair (must be independent non-exec per CMA §53)
  | 'INDEPENDENT_MEMBER'     // Independent non-executive director
  | 'NON_EXECUTIVE_MEMBER'   // Non-executive director (not necessarily independent)
  | 'EXECUTIVE_MEMBER'       // Capped at 1/3 per CMA §53
  | 'SECRETARY';             // Records minutes; usually General Counsel

export interface CommitteeMember {
  /** Stakeholder code (joins to STAKEHOLDERS) — typically a Board member. */
  stakeholderCode: string;
  /** Display name (mirror — kept for the rare case where the member is
      not in STAKEHOLDERS, e.g. an external advisor). */
  nameEn: string;
  nameAr: string;
  role: CommitteeRole;
  /** Yyyy-mm-dd appointed to the committee. */
  appointedAt: string;
  /** Yyyy-mm-dd term ends (CMA §53 caps at 3y renewable). */
  termEndsAt: string;
  /** Has financial-expertise designation (CMA §53.2). */
  financialExpert: boolean;
  /** Optional brief bio. */
  noteEn?: string;
}

export const AUDIT_COMMITTEE_MEMBERS: CommitteeMember[] = [
  { stakeholderCode: 'BOARD', nameEn: 'Hassan Al-Rajhi (Independent NED)', nameAr: 'حسن الراجحي (عضو مجلس مستقل)',
    role: 'CHAIR', appointedAt: '2024-04-15', termEndsAt: '2027-04-15',
    financialExpert: true,
    noteEn: 'Former CFO listed entity; CFA + CIA. CMA §53.2 financial-expert designation.' },
  { stakeholderCode: 'BOARD', nameEn: 'Dr. Amal Al-Suhaimi (Independent NED)', nameAr: 'د. أمل السهيمي (عضو مجلس مستقلة)',
    role: 'INDEPENDENT_MEMBER', appointedAt: '2025-01-10', termEndsAt: '2028-01-10',
    financialExpert: true,
    noteEn: 'Former audit partner KPMG ME; SOCPA-licensed.' },
  { stakeholderCode: 'BOARD', nameEn: 'Tariq Al-Mahmoud (Non-Exec)', nameAr: 'طارق المحمود (غير تنفيذي)',
    role: 'NON_EXECUTIVE_MEMBER', appointedAt: '2024-04-15', termEndsAt: '2027-04-15',
    financialExpert: false,
    noteEn: 'Brings sectoral risk experience.' },
  { stakeholderCode: 'GENERAL_COUNSEL', nameEn: 'Sara Al-Dosari (Secretary)', nameAr: 'سارة الدوسري (أمين السر)',
    role: 'SECRETARY', appointedAt: '2024-04-15', termEndsAt: '2027-04-15',
    financialExpert: false,
    noteEn: 'General Counsel; non-voting; records minutes.' },
];

// ── Charter ────────────────────────────────────────────────────────────────
export interface CommitteeCharter {
  approvedByBoardOn: string;
  nextReviewDueOn: string;
  /** Mandatory composition rules (drives compliance check). */
  minMembers: number;
  minIndependentRatio: number; // e.g. 0.667 → 2/3 independent per CMA §53
  minMeetingsPerYear: number;
  /** Authority granted by the Board (bilingual list). */
  authorityEn: string[];
  authorityAr: string[];
  /** Standing responsibilities (bilingual list). */
  responsibilitiesEn: string[];
  responsibilitiesAr: string[];
}

export const AUDIT_COMMITTEE_CHARTER: CommitteeCharter = {
  approvedByBoardOn: '2024-04-15',
  nextReviewDueOn:   '2027-04-15',
  minMembers: 3,
  minIndependentRatio: 0.667,
  minMeetingsPerYear: 4,
  authorityEn: [
    'Direct access to all books, records, facilities and employees of the company',
    'Engage external advisors at the company’s expense without management pre-approval',
    'Recommend appointment / dismissal / remuneration of the external auditor (CMA §53)',
    'Approve internal-audit charter, plan, budget, KPIs and resourcing',
    'Approve any non-audit services performed by the external auditor (IFAC §600)',
  ],
  authorityAr: [
    'الوصول المباشر لكافة سجلات ومنشآت وموظفي الشركة',
    'الاستعانة بمستشارين خارجيين على نفقة الشركة دون موافقة الإدارة',
    'اقتراح تعيين/عزل/أتعاب المراجع الخارجي (المادة 53 من قواعد الإدراج)',
    'اعتماد ميثاق المراجعة الداخلية وخطّتها وميزانيتها ومؤشراتها ومواردها',
    'اعتماد أي خدمات غير تدقيقية يُقدّمها المراجع الخارجي (IFAC §600)',
  ],
  responsibilitiesEn: [
    'Oversee the integrity of financial reporting + internal controls over financial reporting',
    'Oversee the work of the internal-audit function (Three Lines model)',
    'Oversee the appointment + work + independence + fees of the external auditor',
    'Receive whistleblower disclosures and oversee investigation outcomes',
    'Review compliance with applicable laws + regulations + the Code of Conduct',
    'Report quarterly to the Board on committee activities',
  ],
  responsibilitiesAr: [
    'الإشراف على نزاهة التقارير المالية والضوابط الداخلية',
    'الإشراف على أعمال المراجعة الداخلية (نموذج الخطوط الثلاثة)',
    'الإشراف على تعيين وعمل واستقلالية وأتعاب المراجع الخارجي',
    'استقبال بلاغات حماية المُبلِغين والإشراف على نتائج التحقيقات',
    'مراجعة الامتثال للأنظمة واللوائح وميثاق السلوك',
    'رفع تقرير ربعي للمجلس عن أعمال اللجنة',
  ],
};

// ── Meetings ───────────────────────────────────────────────────────────────
export type MeetingStatus = 'SCHEDULED' | 'IN_PROGRESS' | 'CONCLUDED' | 'CANCELLED';

export interface AuditCommitteeMeeting {
  id: number;
  /** Stable code shown on minutes. */
  meetingCode: string;
  /** Bilingual title (e.g. "Q1 2026 standing review"). */
  titleEn: string;
  titleAr: string;
  /** ISO date-time the meeting was held / will be held. */
  scheduledAt: string;
  /** Yyyy-mm-dd of the actual meeting (set when CONCLUDED). */
  heldOn?: string;
  status: MeetingStatus;
  /** Composite minute reference used in board reporting. */
  minuteRef?: string;
  /** Stakeholder codes of attendees (subset of AUDIT_COMMITTEE_MEMBERS). */
  attendeeCodes: string[];
  /** Agenda items — bilingual + reference + decision. */
  agenda: Array<{
    titleEn: string;
    titleAr: string;
    /** Optional reference (engagement code, plan year, finding id, etc.). */
    ref?: string;
    /** Decision recorded for this agenda item. */
    decisionEn?: string;
    decisionAr?: string;
  }>;
}

export const AUDIT_COMMITTEE_MEETINGS: AuditCommitteeMeeting[] = [
  // 2025-12 — annual plan approval
  { id: 1, meetingCode: 'AC-2025-12',
    titleEn: 'Q4 2025 standing review + 2026 plan approval',
    titleAr: 'المراجعة الفصلية للربع الرابع 2025 + اعتماد خطة 2026',
    scheduledAt: '2025-12-12T15:00:00', heldOn: '2025-12-12',
    status: 'CONCLUDED', minuteRef: 'AUDIT-CTE-MIN-2025-12-12',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL'],
    agenda: [
      { titleEn: '2026 audit plan adoption', titleAr: 'اعتماد خطة المراجعة لعام 2026',
        ref: 'PLAN-2026',
        decisionEn: 'APPROVED — 18 plan rows; 2027 plan drafting kick-off Q4 2026.',
        decisionAr: 'اعتُمدت — 18 عنصر خطة؛ بدء صياغة خطة 2027 في الربع الأخير 2026.' },
      { titleEn: 'Q4 2025 critical-finding review', titleAr: 'مراجعة الملاحظات الحرجة للربع الرابع 2025',
        decisionEn: '5 critical findings reviewed; 4 closed, 1 deferred to Q1 2026.',
        decisionAr: 'تمّ استعراض 5 ملاحظات حرجة؛ 4 مغلقة و1 مؤجّلة للربع الأول 2026.' },
      { titleEn: 'External-auditor appointment recommendation (PwC, 3y)',
        titleAr: 'توصية تعيين المراجع الخارجي (PwC، 3 سنوات)',
        ref: 'APT-2026-001',
        decisionEn: 'RECOMMENDED to AGM; BDO bid declined on IFAC §600 grounds.',
        decisionAr: 'أُوصِي بها للجمعية العامة؛ رُفض عطاء BDO وفق IFAC §600.' },
    ] },
  // 2026-Q1 review
  { id: 2, meetingCode: 'AC-2026-03',
    titleEn: 'Q1 2026 standing review',
    titleAr: 'المراجعة الفصلية للربع الأول 2026',
    scheduledAt: '2026-03-15T15:00:00', heldOn: '2026-03-15',
    status: 'CONCLUDED', minuteRef: 'AUDIT-CTE-MIN-2026-03-15',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL', 'INTERNAL_AUDIT'],
    agenda: [
      { titleEn: 'ISO 27001 surveillance audit (KPMG) — 2 minor findings',
        titleAr: 'مراجعة الإشراف للآيزو 27001 (KPMG) — ملاحظتان طفيفتان',
        ref: 'AUD-2026-001',
        decisionEn: 'NOTED — corrective actions tracked.',
        decisionAr: 'علمنا بها — يُتابَع تنفيذ الإجراءات التصحيحية.' },
      { titleEn: 'Q1 plan-vs-actual coverage (28% complete)',
        titleAr: 'مراجعة الخطة مقابل التنفيذ للربع الأول (28% مكتمل)',
        decisionEn: 'On track per cadence.', decisionAr: 'ضمن الجدول المعتاد.' },
      { titleEn: 'Whistleblower disclosure WB-2026-002', titleAr: 'بلاغ حماية المُبلِغين WB-2026-002',
        decisionEn: 'Investigation handed to General Counsel + HR; quarterly update due Q2.',
        decisionAr: 'إحالة التحقيق إلى المستشار العام والموارد البشرية؛ تحديث ربعي مستحق Q2.' },
    ] },
  // 2026-Q2 — current quarter
  { id: 3, meetingCode: 'AC-2026-06',
    titleEn: 'Q2 2026 standing review',
    titleAr: 'المراجعة الفصلية للربع الثاني 2026',
    scheduledAt: '2026-06-18T15:00:00',
    status: 'SCHEDULED',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL', 'INTERNAL_AUDIT'],
    agenda: [
      { titleEn: 'PDPL external review — Deloitte report-issued',
        titleAr: 'المراجعة الخارجية لنظام حماية البيانات — صدر تقرير ديلويت',
        ref: 'AUD-2026-002' },
      { titleEn: 'BDO independence breach — escalation + remediation plan',
        titleAr: 'إخلال BDO بالاستقلالية — التصعيد وخطة المعالجة' },
      { titleEn: 'Q1 + Q2 plan-vs-actual coverage', titleAr: 'مراجعة الخطة مقابل التنفيذ للربعين الأول والثاني' },
    ] },
  // 2026-Q3 + Q4 placeholders
  { id: 4, meetingCode: 'AC-2026-09',
    titleEn: 'Q3 2026 standing review',
    titleAr: 'المراجعة الفصلية للربع الثالث 2026',
    scheduledAt: '2026-09-17T15:00:00',
    status: 'SCHEDULED',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL'],
    agenda: [
      { titleEn: 'SAMA CSF external review — provisional findings', titleAr: 'مراجعة إطار البنك المركزي الخارجية — ملاحظات أوّلية' },
      { titleEn: 'ISO 22301 stage-2 certification readiness',         titleAr: 'جاهزية شهادة الآيزو 22301 — المرحلة الثانية' },
    ] },
  { id: 5, meetingCode: 'AC-2026-12',
    titleEn: 'Q4 2026 standing review + 2027 plan adoption',
    titleAr: 'المراجعة الفصلية للربع الرابع 2026 + اعتماد خطة 2027',
    scheduledAt: '2026-12-15T15:00:00',
    status: 'SCHEDULED',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL'],
    agenda: [
      { titleEn: '2027 audit plan adoption', titleAr: 'اعتماد خطة 2027' },
      { titleEn: 'External-auditor mandatory rotation (CMA §53.5)', titleAr: 'تدوير المراجع الخارجي الإلزامي (المادة 53.5)' },
      { titleEn: 'Annual report to Board',     titleAr: 'التقرير السنوي للمجلس' },
    ] },
  // Ad-hoc — emergency cyber incident escalation
  { id: 6, meetingCode: 'AC-2026-04-EMERGENCY',
    titleEn: 'Emergency: ransomware attempt CYB-INC-2026-018',
    titleAr: 'استثنائي: محاولة فدية CYB-INC-2026-018',
    scheduledAt: '2026-04-19T18:00:00', heldOn: '2026-04-19',
    status: 'CONCLUDED', minuteRef: 'AUDIT-CTE-MIN-2026-04-19E',
    attendeeCodes: ['BOARD', 'GENERAL_COUNSEL'],
    agenda: [
      { titleEn: 'Ransomware-attempt briefing', titleAr: 'إحاطة محاولة برنامج الفدية',
        ref: 'CYBER_INCIDENT:502',
        decisionEn: 'Briefing accepted; CMT activated; SAMA + NCA notifications confirmed within SLA.',
        decisionAr: 'قُبلت الإحاطة؛ فُعِّل فريق إدارة الأزمات؛ تأكيد إخطار SAMA + NCA ضمن المهلة.' },
    ] },
];

// ── Helpers ────────────────────────────────────────────────────────────────

export const MEETING_STATUS_TONE: Record<MeetingStatus, 'neutral' | 'info' | 'warning' | 'success'> = {
  SCHEDULED: 'info', IN_PROGRESS: 'warning',
  CONCLUDED: 'success', CANCELLED: 'neutral',
};

export const ROLE_LABEL_CTE: Record<CommitteeRole, { en: string; ar: string }> = {
  CHAIR:                { en: 'Chair',                ar: 'الرئيس' },
  INDEPENDENT_MEMBER:   { en: 'Independent member',   ar: 'عضو مستقل' },
  NON_EXECUTIVE_MEMBER: { en: 'Non-executive member', ar: 'عضو غير تنفيذي' },
  EXECUTIVE_MEMBER:     { en: 'Executive member',     ar: 'عضو تنفيذي' },
  SECRETARY:            { en: 'Secretary',            ar: 'أمين السر' },
};

/** Compliance check: does the committee composition meet the charter? */
export function complianceCheck(): { compliant: boolean; reasons: string[] } {
  const reasons: string[] = [];
  const voting = AUDIT_COMMITTEE_MEMBERS.filter(m => m.role !== 'SECRETARY');
  if (voting.length < AUDIT_COMMITTEE_CHARTER.minMembers) {
    reasons.push(`Only ${voting.length} voting members — charter requires ≥${AUDIT_COMMITTEE_CHARTER.minMembers}.`);
  }
  const independent = voting.filter(m => m.role === 'CHAIR' || m.role === 'INDEPENDENT_MEMBER').length;
  const ratio = voting.length > 0 ? independent / voting.length : 0;
  // Use a tiny epsilon so that exact 2/3 (0.6666…) passes a "≥ 2/3" charter rule.
  if (ratio + 0.001 < AUDIT_COMMITTEE_CHARTER.minIndependentRatio) {
    reasons.push(`Independent ratio is ${Math.round(ratio * 100)}% — charter requires ≥${Math.round(AUDIT_COMMITTEE_CHARTER.minIndependentRatio * 100)}%.`);
  }
  if (!voting.some(m => m.financialExpert)) {
    reasons.push('No designated financial expert (CMA §53.2).');
  }
  return { compliant: reasons.length === 0, reasons };
}

export function meetingsHeldThisYear(year: number): number {
  return AUDIT_COMMITTEE_MEETINGS.filter(m => m.status === 'CONCLUDED' && (m.heldOn ?? '').startsWith(String(year))).length;
}
