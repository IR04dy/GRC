import { demoKeyed } from './demoIds';

// Policy lifecycle + bilingual versioning — Sprint 6.
//
// Existing PolicyDto already has a `status` enum (DRAFT → LEGAL_REVIEW
// → EXEC_APPROVAL → PUBLISHED → REVIEW_DUE → RETIRED). What was missing:
//   • formal version-history with bilingual change summaries
//   • visual stage progression (board members ask "where is this in the
//     pipeline?" not "what status code does it have")
//   • effective-period tracking (when did v2.0 supersede v1.0?)
//
// This module adds the versioning + helpers; components consume it.

export type PolicyStage =
  | 'DRAFT'
  | 'LEGAL_REVIEW'
  | 'EXEC_APPROVAL'
  | 'PUBLISHED'
  | 'REVIEW_DUE'
  | 'RETIRED';

export const POLICY_STAGES: PolicyStage[] = [
  'DRAFT', 'LEGAL_REVIEW', 'EXEC_APPROVAL', 'PUBLISHED', 'REVIEW_DUE', 'RETIRED',
];

export const STAGE_LABEL: Record<PolicyStage, { en: string; ar: string }> = {
  DRAFT:         { en: 'Draft',                 ar: 'مسودة' },
  LEGAL_REVIEW:  { en: 'Legal review',           ar: 'مراجعة قانونية' },
  EXEC_APPROVAL: { en: 'Executive approval',     ar: 'اعتماد تنفيذي' },
  PUBLISHED:     { en: 'Published',              ar: 'منشورة' },
  REVIEW_DUE:    { en: 'Review due',             ar: 'مراجعة مستحقة' },
  RETIRED:       { en: 'Retired',                ar: 'مُتقاعدة' },
};

export const STAGE_TONE: Record<PolicyStage, 'neutral' | 'info' | 'accent' | 'success' | 'warning' | 'danger'> = {
  DRAFT:         'neutral',
  LEGAL_REVIEW:  'info',
  EXEC_APPROVAL: 'accent',
  PUBLISHED:     'success',
  REVIEW_DUE:    'warning',
  RETIRED:       'neutral',
};

// ─── Version history model ──────────────────────────────────────────────────

export interface PolicyVersion {
  /** Display-friendly version, e.g. "1.0", "2.0", "2.1". */
  version: string;
  /** When this version was published (or the in-flight draft created). */
  effectiveFrom?: string;
  /** When superseded by the next version. Empty for current. */
  effectiveTo?: string;
  status: PolicyStage;
  /** Change summary (bilingual) — what changed since the previous version. */
  changeSummaryEn: string;
  changeSummaryAr: string;
  /** Approver (for PUBLISHED versions) — usually exec committee or board. */
  approvedBy?: string;
  /** Sample content excerpt — used for the "diff" preview. */
  excerptEn?: string;
  excerptAr?: string;
  /** Author of this version. */
  authorEn: string;
  authorAr: string;
}

// ─── Demo version histories ────────────────────────────────────────────────
//
// Keyed by policy id. Production wires backend audit trail. The dates anchor
// to "now" so the demo is always a believable timeline.

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

export const POLICY_VERSIONS_BY_ID: Record<number, PolicyVersion[]> = demoKeyed({
  // POL-AI-001 — Acceptable AI Use Policy (in EXEC_APPROVAL today per AP-001)
  55: [
    {
      version: '1.0',
      effectiveFrom: _daysAgo(7),
      status: 'LEGAL_REVIEW',
      changeSummaryEn: 'Initial release. Aligns to SDAIA AI Ethics Principles + CMA CGR Art. 71 (board-level AI risk oversight).',
      changeSummaryAr: 'الإصدار الأولي. متوافق مع مبادئ أخلاقيات الذكاء الاصطناعي من سدايا والمادة 71 من لائحة حوكمة الشركات لهيئة السوق المالية (الإشراف على المستوى المجلسي).',
      authorEn: 'Hassan Al-Saud (Chief Compliance Officer)',
      authorAr: 'حسن السعود (الرئيس التنفيذي للالتزام)',
      excerptEn: 'Employees may use generative AI tools only on classified-public data. All AI-assisted decisions affecting customer outcomes require human review and a documented decision log.',
      excerptAr: 'يمكن للموظفين استخدام أدوات الذكاء الاصطناعي التوليدية على البيانات المُصنَّفة "عامة" فقط. جميع القرارات المعتمدة على الذكاء الاصطناعي والمؤثّرة على العملاء تتطلّب مراجعة بشرية وسجلّ قرارات موثّق.',
    },
  ],
  // POL-CLOUD-001 — Cloud Computing Policy (referenced by reg-change rc-2026-002)
  56: [
    {
      version: '2.1',
      effectiveFrom: _daysAgo(30),
      status: 'PUBLISHED',
      changeSummaryEn: 'Updates to align with NCA CSCC v2 — Tier-1 data residency mandatory in KSA. SAMA Cloud Computing Framework §4.3 cross-references added.',
      changeSummaryAr: 'تحديثات للتوافق مع ضوابط الأمن السحابي الإصدار 2 — إقامة بيانات الفئة 1 إلزامياً داخل المملكة. أُضيفت إحالات إلى الإطار السحابي لساما §4.3.',
      approvedBy: 'CIO',
      authorEn: 'Layla Al-Saud (CIO)',
      authorAr: 'ليلى السعود (الرئيس التنفيذي لتقنية المعلومات)',
      excerptEn: 'All Tier-1 (highly sensitive) data must reside in KSA cloud regions. Cross-border transfer requires NCA written approval per workload, recorded in the data-residency register.',
      excerptAr: 'يجب أن تبقى بيانات الفئة 1 (شديدة الحساسية) في مناطق سحابية داخل المملكة. النقل عبر الحدود يتطلّب موافقة كتابية من الجهة لكل عبء عمل مع توثيقها في سجل إقامة البيانات.',
    },
    {
      version: '2.0',
      effectiveFrom: _daysAgo(180),
      effectiveTo: _daysAgo(30),
      status: 'RETIRED',
      changeSummaryEn: 'Major rewrite — adopted NCA CSCC v1 framework, replaced ISO 27017 reference architecture.',
      changeSummaryAr: 'إعادة صياغة كبيرة — تبنّي إطار ضوابط الأمن السحابي v1، استبدال البنية المرجعية لـISO 27017.',
      approvedBy: 'CIO',
      authorEn: 'Layla Al-Saud (CIO)',
      authorAr: 'ليلى السعود',
    },
    {
      version: '1.0',
      effectiveFrom: _daysAgo(540),
      effectiveTo: _daysAgo(180),
      status: 'RETIRED',
      changeSummaryEn: 'Initial cloud governance policy — ISO 27017-aligned.',
      changeSummaryAr: 'سياسة حوكمة السحابة الأولية — متوافقة مع ISO 27017.',
      approvedBy: 'CIO',
      authorEn: 'Layla Al-Saud (CIO)',
      authorAr: 'ليلى السعود',
    },
  ],
  // POL-VULN-001 — Vulnerability Management Policy (referenced by NCA-2026-014)
  57: [
    {
      version: '3.0',
      effectiveFrom: _daysAhead(40),
      status: 'DRAFT',
      changeSummaryEn: 'Tightens MTTR for critical CVEs from 14 days to 7 per NCA ECC-1:2026. New SLA bands: Critical 7d / High 14d / Medium 30d.',
      changeSummaryAr: 'تشديد متوسط زمن المعالجة للثغرات الحرجة من 14 إلى 7 أيام وفق NCA ECC-1:2026. مستويات SLA الجديدة: حرجة 7 أيام / عالية 14 / متوسطة 30.',
      authorEn: 'Ahmed Al-Qahtani (CISO)',
      authorAr: 'أحمد القحطاني (الرئيس التنفيذي لأمن المعلومات)',
      excerptEn: 'Critical CVEs (CVSS ≥ 9.0) on internet-facing systems must be remediated within 7 calendar days of CVE publication.',
      excerptAr: 'الثغرات الحرجة (CVSS ≥ 9.0) على الأنظمة المتصلة بالإنترنت يجب معالجتها خلال 7 أيام تقويمية من نشر الثغرة.',
    },
    {
      version: '2.0',
      effectiveFrom: _daysAgo(220),
      status: 'PUBLISHED',
      changeSummaryEn: 'Aligned with NCA ECC-1:2024 control 2-6. Added vendor pen-test cadence requirement.',
      changeSummaryAr: 'مُحاذاة مع NCA ECC-1:2024 الضابط 2-6. إضافة متطلب تواتر اختبار الاختراق للموردين.',
      approvedBy: 'CISO',
      authorEn: 'Ahmed Al-Qahtani (CISO)',
      authorAr: 'أحمد القحطاني',
    },
    {
      version: '1.0',
      effectiveFrom: _daysAgo(720),
      effectiveTo: _daysAgo(220),
      status: 'RETIRED',
      changeSummaryEn: 'Initial release.',
      changeSummaryAr: 'الإصدار الأولي.',
      approvedBy: 'CISO',
      authorEn: 'Ahmed Al-Qahtani',
      authorAr: 'أحمد القحطاني',
    },
  ],
  // POL-COI-001 — Conflict of Interest Policy (Nazaha annual cycle)
  58: [
    {
      version: '4.0',
      effectiveFrom: _daysAgo(95),
      status: 'PUBLISHED',
      changeSummaryEn: 'FY2026 cycle: aligned with Nazaha Anti-Corruption Strategy 2030 + clarified family-relations disclosure threshold (2nd-degree → 3rd-degree).',
      changeSummaryAr: 'دورة السنة المالية 2026: مُحاذاة مع استراتيجية نزاهة لمكافحة الفساد 2030 + توضيح حدّ الإفصاح عن العلاقات العائلية (الدرجة الثانية → الثالثة).',
      approvedBy: 'Audit Committee',
      authorEn: 'Hala Al-Tamimi (Head of Internal Audit)',
      authorAr: 'هالة التميمي (رئيس التدقيق الداخلي)',
      excerptEn: 'Officers, directors, and senior managers must disclose financial interests in any vendor, customer, or competitor — including those held by 1st-, 2nd-, and 3rd-degree relatives.',
      excerptAr: 'يجب على المسؤولين والمدراء والإدارة العليا الإفصاح عن أي مصالح مالية في موردين أو عملاء أو منافسين — بما في ذلك تلك المملوكة لأقارب الدرجات الأولى والثانية والثالثة.',
    },
  ],
});

// ─── Helpers ────────────────────────────────────────────────────────────────

export function stageIndex(stage: PolicyStage): number {
  return POLICY_STAGES.indexOf(stage);
}

export function isStagePassed(stage: PolicyStage, current: PolicyStage): boolean {
  // RETIRED is a special terminal that doesn't fit the strip model.
  if (current === 'RETIRED') return stage !== 'RETIRED';
  return stageIndex(stage) < stageIndex(current);
}

export function currentVersion(versions: ReadonlyArray<PolicyVersion>): PolicyVersion | undefined {
  return versions.find(v => v.effectiveFrom && !v.effectiveTo);
}

export function versionsForPolicy(policyId: number): PolicyVersion[] {
  return POLICY_VERSIONS_BY_ID[policyId] ?? [];
}
