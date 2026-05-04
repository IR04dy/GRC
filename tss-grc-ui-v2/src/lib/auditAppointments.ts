// External-auditor appointment workflow + audit-fee schedule.
//
// ServiceNow IRM equivalent: `audit_appointment` table — captures the
// full RFP → Shortlist → Evaluation → Selection → Engagement-letter
// → Active → Closed lifecycle the Audit Committee oversees when picking
// an external auditor.
//
// Standards anchor:
//   CMA Listing Rules §99      — Audit-Committee oversight of external
//                                 auditor selection + fee approval
//   Saudi Companies Law Art.130 — appointment by general assembly,
//                                  recommendation by Audit Committee
//   SOCPA Code of Professional Conduct — independence on appointment
//   ISA 210 (Agreeing the Terms of Audit Engagements)
//   IFAC §600 — non-audit-services tracking that informs fee mix
//   IIA IPPF 2030 — internal-audit resource management

export type AppointmentStatus =
  | 'RFP_DRAFT'           // RFP being drafted
  | 'RFP_PUBLISHED'       // RFP open to candidate firms
  | 'SHORTLIST'           // shortlist of N firms agreed
  | 'EVALUATION'          // proposals being scored
  | 'SELECTION'           // recommendation tabled at Audit Committee
  | 'ENGAGEMENT_LETTER'   // engagement letter under negotiation
  | 'ACTIVE'              // contracted + working
  | 'CLOSED';             // engagement complete + closed-out

export type FeeCategory = 'AUDIT' | 'NON_AUDIT_TAX' | 'NON_AUDIT_ADVISORY' | 'NON_AUDIT_OTHER';

export interface CandidateBid {
  /** AuditorProfile.code or free-text firm name. */
  firmCode: string;
  /** Bid amount (SAR). */
  feeBidSar: number;
  /** Independent score 0-100 (technical + commercial + experience). */
  technicalScore?: number;
  experienceScore?: number;
  commercialScore?: number;
  /** Combined weighted score the Audit Cte sees. */
  totalScore?: number;
  /** Y/N — has the firm declared no independence concerns? */
  independenceDeclared?: boolean;
}

export interface AuditAppointment {
  id: number;
  /** Stable code shown in audit-committee minutes. */
  appointmentCode: string;
  /** Bilingual name. */
  titleEn: string;
  titleAr: string;
  /** Scope — joins to AUDIT_UNIVERSE entity codes. */
  universeCodes: string[];
  /** Frameworks the engagement covers. */
  frameworks: string[];
  /** Multi-year (e.g. 3-year mandate) — number of audit cycles. */
  termYears: number;
  status: AppointmentStatus;
  /** Yyyy-mm-dd timeline checkpoints. */
  rfpPublishedAt?: string;
  shortlistAgreedAt?: string;
  evaluationCompletedAt?: string;
  audCteRecommendedAt?: string;
  generalAssemblyApprovedAt?: string;
  engagementLetterSignedAt?: string;
  /** Candidate firms with their bids/scores. */
  candidates: CandidateBid[];
  /** Selected firm — set once SELECTION concludes. */
  selectedFirmCode?: string;
  /** Total contracted fee (SAR). */
  contractedFeeSar?: number;
  /** Free-form note (decision rationale). */
  noteEn?: string;
  noteAr?: string;
}

// ── Demo appointments ─────────────────────────────────────────────────────
export const AUDIT_APPOINTMENTS: AuditAppointment[] = [
  { id: 1, appointmentCode: 'APT-2026-001',
    titleEn: 'External financial auditor — 3-year mandate (FY26-28)',
    titleAr: 'المراجع المالي الخارجي — تكليف ثلاث سنوات (السنوات المالية 2026-2028)',
    universeCodes: ['BU-TREASURY', 'PROC-FINREP'],
    frameworks: ['CMA', 'IFRS', 'ZATCA'],
    termYears: 3, status: 'ACTIVE',
    rfpPublishedAt: '2025-09-01',
    shortlistAgreedAt: '2025-10-15',
    evaluationCompletedAt: '2025-11-12',
    audCteRecommendedAt: '2025-11-20',
    generalAssemblyApprovedAt: '2025-12-05',
    engagementLetterSignedAt: '2025-12-15',
    candidates: [
      { firmCode: 'PWC',     feeBidSar: 2_400_000, technicalScore: 92, experienceScore: 95, commercialScore: 78, totalScore: 88, independenceDeclared: true },
      { firmCode: 'EY',      feeBidSar: 2_650_000, technicalScore: 88, experienceScore: 90, commercialScore: 72, totalScore: 84, independenceDeclared: true },
      { firmCode: 'KPMG',    feeBidSar: 2_550_000, technicalScore: 85, experienceScore: 90, commercialScore: 75, totalScore: 84, independenceDeclared: true },
      { firmCode: 'BDO',     feeBidSar: 1_950_000, technicalScore: 75, experienceScore: 70, commercialScore: 92, totalScore: 79, independenceDeclared: false /* IT services declared */ },
    ],
    selectedFirmCode: 'PWC',
    contractedFeeSar: 2_400_000,
    noteEn: 'Selected on combined technical + experience strength; lowest-bidder (BDO) excluded due to active IT-implementation services breaching IFAC §600.' },

  { id: 2, appointmentCode: 'APT-2026-002',
    titleEn: 'SAMA CSF external review — annual',
    titleAr: 'المراجعة الخارجية لإطار البنك المركزي — سنوي',
    universeCodes: ['FW-SAMA-CSF'],
    frameworks: ['SAMA_CSF'],
    termYears: 1, status: 'ACTIVE',
    rfpPublishedAt: '2026-01-10',
    shortlistAgreedAt: '2026-01-25',
    evaluationCompletedAt: '2026-02-15',
    audCteRecommendedAt: '2026-02-22',
    engagementLetterSignedAt: '2026-03-08',
    candidates: [
      { firmCode: 'EY',      feeBidSar: 950_000, technicalScore: 90, experienceScore: 92, commercialScore: 80, totalScore: 88, independenceDeclared: true },
      { firmCode: 'KPMG',    feeBidSar: 980_000, technicalScore: 88, experienceScore: 85, commercialScore: 78, totalScore: 84, independenceDeclared: true },
    ],
    selectedFirmCode: 'EY',
    contractedFeeSar: 950_000 },

  { id: 3, appointmentCode: 'APT-2026-003',
    titleEn: 'PDPL external compliance review',
    titleAr: 'المراجعة الخارجية للامتثال لنظام حماية البيانات',
    universeCodes: ['FW-PDPL', 'PROC-PRIVACY'],
    frameworks: ['PDPL'],
    termYears: 1, status: 'ACTIVE',
    rfpPublishedAt: '2025-11-15',
    shortlistAgreedAt: '2025-12-08',
    evaluationCompletedAt: '2026-01-22',
    audCteRecommendedAt: '2026-01-28',
    engagementLetterSignedAt: '2026-02-08',
    candidates: [
      { firmCode: 'DELOITTE', feeBidSar: 720_000, technicalScore: 92, experienceScore: 88, commercialScore: 85, totalScore: 89, independenceDeclared: true },
      { firmCode: 'PWC',      feeBidSar: 850_000, technicalScore: 90, experienceScore: 92, commercialScore: 70, totalScore: 84, independenceDeclared: true },
      { firmCode: 'EY',       feeBidSar: 780_000, technicalScore: 85, experienceScore: 80, commercialScore: 80, totalScore: 82, independenceDeclared: false /* AML advisory ongoing */ },
    ],
    selectedFirmCode: 'DELOITTE', contractedFeeSar: 720_000 },

  { id: 4, appointmentCode: 'APT-2027-001',
    titleEn: 'External financial auditor — FY29-31 mandate (rotation)',
    titleAr: 'المراجع المالي الخارجي — تكليف 2029-2031 (تدوير)',
    universeCodes: ['BU-TREASURY', 'PROC-FINREP'],
    frameworks: ['CMA', 'IFRS', 'ZATCA'],
    termYears: 3, status: 'RFP_DRAFT',
    candidates: [],
    noteEn: 'Mandatory rotation under CMA Resolution 8-127-2016 (5y lead-partner cap). RFP drafting begins Q3 2027 ahead of 2028 AGM appointment.' },
];

// ── Audit fees by firm + year ─────────────────────────────────────────────
export interface FeeRow {
  firmCode: string;
  year: number;
  category: FeeCategory;
  amountSar: number;
  appointmentCode?: string;
}

export const AUDIT_FEES: FeeRow[] = [
  // 2024 (prior year reference)
  { firmCode: 'PWC',     year: 2024, category: 'AUDIT',              amountSar: 2_300_000, appointmentCode: 'APT-2023-001' },
  { firmCode: 'PWC',     year: 2024, category: 'NON_AUDIT_TAX',      amountSar: 180_000 },
  { firmCode: 'EY',      year: 2024, category: 'AUDIT',              amountSar: 920_000 },
  { firmCode: 'EY',      year: 2024, category: 'NON_AUDIT_ADVISORY', amountSar: 420_000 },
  { firmCode: 'KPMG',    year: 2024, category: 'AUDIT',              amountSar: 380_000 },
  { firmCode: 'DELOITTE',year: 2024, category: 'AUDIT',              amountSar: 0 },

  // 2025
  { firmCode: 'PWC',     year: 2025, category: 'AUDIT',              amountSar: 2_400_000, appointmentCode: 'APT-2026-001' },
  { firmCode: 'PWC',     year: 2025, category: 'NON_AUDIT_TAX',      amountSar: 210_000 },
  { firmCode: 'EY',      year: 2025, category: 'AUDIT',              amountSar: 950_000, appointmentCode: 'APT-2026-002' },
  { firmCode: 'EY',      year: 2025, category: 'NON_AUDIT_ADVISORY', amountSar: 480_000 },
  { firmCode: 'KPMG',    year: 2025, category: 'AUDIT',              amountSar: 420_000 },
  { firmCode: 'DELOITTE',year: 2025, category: 'AUDIT',              amountSar: 720_000, appointmentCode: 'APT-2026-003' },
  { firmCode: 'BSI',     year: 2025, category: 'AUDIT',              amountSar: 280_000 },
  { firmCode: 'ATT-QSA', year: 2025, category: 'AUDIT',              amountSar: 410_000 },

  // 2026 (in flight — committed)
  { firmCode: 'PWC',     year: 2026, category: 'AUDIT',              amountSar: 2_400_000, appointmentCode: 'APT-2026-001' },
  { firmCode: 'PWC',     year: 2026, category: 'NON_AUDIT_TAX',      amountSar: 250_000 },
  { firmCode: 'EY',      year: 2026, category: 'AUDIT',              amountSar: 950_000, appointmentCode: 'APT-2026-002' },
  { firmCode: 'EY',      year: 2026, category: 'NON_AUDIT_ADVISORY', amountSar: 320_000 },
  { firmCode: 'EY',      year: 2026, category: 'NON_AUDIT_OTHER',    amountSar: 145_000 /* cyber readiness consulting */ },
  { firmCode: 'KPMG',    year: 2026, category: 'AUDIT',              amountSar: 460_000 },
  { firmCode: 'DELOITTE',year: 2026, category: 'AUDIT',              amountSar: 720_000, appointmentCode: 'APT-2026-003' },
  { firmCode: 'BSI',     year: 2026, category: 'AUDIT',              amountSar: 295_000 },
  { firmCode: 'ATT-QSA', year: 2026, category: 'AUDIT',              amountSar: 425_000 },
];

// ── Helpers ────────────────────────────────────────────────────────────────

export const APPOINTMENT_STATUS_TONE: Record<AppointmentStatus, 'neutral' | 'info' | 'primary' | 'warning' | 'accent' | 'success'> = {
  RFP_DRAFT: 'neutral', RFP_PUBLISHED: 'info', SHORTLIST: 'info',
  EVALUATION: 'warning', SELECTION: 'accent', ENGAGEMENT_LETTER: 'accent',
  ACTIVE: 'success', CLOSED: 'success',
};

export const FEE_CATEGORY_LABEL: Record<FeeCategory, { en: string; ar: string }> = {
  AUDIT:              { en: 'Audit',              ar: 'مراجعة' },
  NON_AUDIT_TAX:      { en: 'Non-audit · Tax',    ar: 'غير تدقيقي · ضرائب' },
  NON_AUDIT_ADVISORY: { en: 'Non-audit · Advisory', ar: 'غير تدقيقي · استشاري' },
  NON_AUDIT_OTHER:    { en: 'Non-audit · Other',  ar: 'غير تدقيقي · أخرى' },
};

export const FEE_CATEGORY_TONE: Record<FeeCategory, 'success' | 'warning' | 'accent' | 'info'> = {
  AUDIT:              'success',
  NON_AUDIT_TAX:      'info',
  NON_AUDIT_ADVISORY: 'warning',
  NON_AUDIT_OTHER:    'accent',
};

/** Fees by firm by year (rolled up from FeeRow). */
export function feesByFirmYear(year: number): Map<string, { audit: number; nonAudit: number; total: number; ratio: number }> {
  const m = new Map<string, { audit: number; nonAudit: number; total: number; ratio: number }>();
  for (const f of AUDIT_FEES) {
    if (f.year !== year) continue;
    const cur = m.get(f.firmCode) ?? { audit: 0, nonAudit: 0, total: 0, ratio: 0 };
    if (f.category === 'AUDIT') cur.audit += f.amountSar;
    else cur.nonAudit += f.amountSar;
    cur.total = cur.audit + cur.nonAudit;
    cur.ratio = cur.audit > 0 ? Math.round((cur.nonAudit / cur.audit) * 100) : 0;
    m.set(f.firmCode, cur);
  }
  return m;
}
