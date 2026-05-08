// Regulatory change feed (Saudi) — Sprint 5.
//
// ServiceNow + OneTrust ship "regulatory change" feeds covering US/EU
// regulators (Thomson Reuters, RegulatoryDataX). Saudi-listed entities
// have to track NCA, SAMA, SDAIA, ZATCA, CMA, MOC, HRSD, CITC, NCEMA
// every week — none of those feeds cover the Arabic-only sources.
//
// This module is the localization wedge: a Saudi-first regulatory-change
// model with seeded changes from the last ~90 days, classified by impact
// and mapped to the controls each change touches. Each change can trigger
// the chain engine (REG_CHANGE_PUBLISHED) so the platform auto-spawns an
// applicability-assessment workflow the moment a circular drops.
//
// In production each entry would come from one of:
//   • RSS / sitemap polls of regulator portals (already wired in connectors.ts)
//   • Email parsing of regulator newsletters
//   • Manual entry from a regulatory-watch desk
// The lib doesn't care — it just models the entry.

import type { RegulatorScope } from './assets';

export type RegulatoryChangeKind =
  | 'NEW_REGULATION'   // brand-new instrument
  | 'AMENDMENT'         // amendment to an existing regulation
  | 'CIRCULAR'          // regulator-issued circular / directive
  | 'GUIDANCE'          // non-binding guidance / FAQ
  | 'DEADLINE'          // implementation / reporting deadline reminder
  | 'ENFORCEMENT'       // enforcement action / fine announcement
  | 'CONSULTATION';     // public-consultation / draft-for-comment

export type ImpactLevel = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' | 'INFORMATIONAL';

export type ChangeStatus =
  | 'NEW'                  // just ingested, not yet triaged
  | 'TRIAGED'              // owner assigned, applicability under review
  | 'APPLICABLE'           // applies to us, in remediation
  | 'NOT_APPLICABLE'       // dismissed
  | 'IMPLEMENTED'          // controls / policy updated
  | 'MONITORING';          // ongoing watch

export interface RegulatoryChange {
  id: string;
  regulator: RegulatorScope;
  /** Reference number from the regulator (NCA-2026-014, SAMA-CIR-43200, …). */
  externalRef: string;
  titleEn: string;
  titleAr: string;
  summaryEn: string;
  summaryAr: string;
  kind: RegulatoryChangeKind;
  /** Date the regulator published / effective. */
  publishedAt: string;
  /** Compliance deadline if the change carries one. */
  deadline?: string;
  /** Internal classification. */
  impact: ImpactLevel;
  status: ChangeStatus;
  /** Owner email — usually the policy owner for the affected domain. */
  ownerEmail: string;
  ownerNameEn: string;
  ownerNameAr: string;
  /** Controls / framework codes touched. Use external framework codes
   *  (NCA ECC 2-3-1, PDPL Art. 25) so the impact view can map them. */
  affectedControls: string[];
  /** Affected policies (internal POL-* refs when known). */
  affectedPolicies: string[];
  /** Optional URL to the regulator's source. */
  sourceUrl?: string;
}

// ─── Saudi regulatory change feed (90-day window) ───────────────────────────

const _now = Date.now();
const _daysAgo = (n: number) => new Date(_now - n * 86_400_000).toISOString().slice(0, 10);
const _daysAhead = (n: number) => new Date(_now + n * 86_400_000).toISOString().slice(0, 10);

export const REGULATORY_CHANGES: RegulatoryChange[] = [
  // ── NCA — cybersecurity ──
  {
    id: 'rc-2026-001',
    regulator: 'NCA', externalRef: 'NCA-2026-014',
    titleEn: 'NCA ECC-1:2026 — refresh of vulnerability-management controls',
    titleAr: 'تحديث ضوابط إدارة الثغرات — ECC-1:2026',
    summaryEn: 'Tightens MTTR for critical CVEs on internet-facing systems from 14 days to 7 days. Mandatory for all government and critical-sector entities.',
    summaryAr: 'تشديد متوسط زمن المعالجة للثغرات الحرجة في الأنظمة المتصلة بالإنترنت من 14 يوماً إلى 7 أيام. إلزامي لجميع الجهات الحكومية والقطاعات الحرجة.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(8),
    deadline: _daysAhead(82),
    impact: 'CRITICAL', status: 'TRIAGED',
    ownerEmail: 'ciso@tss.sa', ownerNameEn: 'Ahmed Al-Qahtani', ownerNameAr: 'أحمد القحطاني',
    affectedControls: ['NCA ECC 2-6', 'NCA ECC 2-3-1'],
    affectedPolicies: ['POL-VULN-001'],
    sourceUrl: 'https://nca.gov.sa',
  },
  {
    id: 'rc-2026-002',
    regulator: 'NCA', externalRef: 'NCA-CSCC-2026-002',
    titleEn: 'NCA Cloud-Cybersecurity Controls — region residency for Tier-1 data',
    titleAr: 'ضوابط الأمن السيبراني السحابي — اشتراط الإقامة في المملكة لبيانات الفئة 1',
    summaryEn: 'Tier-1 (highly sensitive) data must reside exclusively in Saudi Arabia. Cross-border transfer requires NCA written approval per workload.',
    summaryAr: 'يجب أن تبقى بيانات الفئة 1 (شديدة الحساسية) داخل المملكة حصراً. النقل عبر الحدود يتطلب موافقة كتابية من الجهة لكل عبء عمل.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(20),
    deadline: _daysAhead(160),
    impact: 'HIGH', status: 'APPLICABLE',
    ownerEmail: 'cloud.platform@tss.sa', ownerNameEn: 'Layla Al-Saud', ownerNameAr: 'ليلى السعود',
    affectedControls: ['NCA CSCC 2-1', 'CITC Cloud CCRF 4.2'],
    affectedPolicies: ['POL-CLOUD-001', 'POL-DATA-CLASSIFICATION-001'],
  },

  // ── SAMA — banking / payments ──
  {
    id: 'rc-2026-003',
    regulator: 'SAMA', externalRef: 'SAMA-CIR-43200-15',
    titleEn: 'SAMA Operational Resilience Framework — Q3 2026 reporting template',
    titleAr: 'إطار المرونة التشغيلية لساما — قالب الإبلاغ للربع الثالث 2026',
    summaryEn: 'New quarterly self-attestation template covering Tier-0 RTO/RPO, third-party concentration, and BCP exercise outcomes. First submission due 15 Oct 2026.',
    summaryAr: 'قالب جديد للإقرار الذاتي الربع سنوي يشمل أهداف الاستعادة للخدمات الحرجة، وتمركز الموردين، ونتائج تمارين استمرارية الأعمال. أول تقديم مستحقّ في 15 أكتوبر 2026.',
    kind: 'CIRCULAR',
    publishedAt: _daysAgo(35),
    deadline: '2026-10-15',
    impact: 'HIGH', status: 'TRIAGED',
    ownerEmail: 'compliance.lead@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    affectedControls: ['SAMA Operational Resilience §3', 'NCEMA BCP §5'],
    affectedPolicies: ['POL-BCP-001'],
  },
  {
    id: 'rc-2026-004',
    regulator: 'SAMA', externalRef: 'SAMA-AML-2026-008',
    titleEn: 'SAMA AML circular — STR submission window tightened to 24h',
    titleAr: 'تعميم ساما لمكافحة غسل الأموال — تشديد نافذة تقديم STR إلى 24 ساعة',
    summaryEn: 'Suspicious-Transaction Reports must now be filed with the Saudi FIU within 24 hours of detection (was 72h). Effective 60 days from circular.',
    summaryAr: 'يجب تقديم بلاغات المعاملات المشبوهة لوحدة التحرّيات المالية خلال 24 ساعة من الرصد (كانت 72 ساعة). نافذ بعد 60 يوماً من التعميم.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(40),
    deadline: _daysAhead(20),
    impact: 'CRITICAL', status: 'APPLICABLE',
    ownerEmail: 'aml.officer@tss.sa', ownerNameEn: 'Mona Al-Bishi', ownerNameAr: 'منى البيشي',
    affectedControls: ['SAMA AML §4.3', 'FATF Recommendation 20'],
    affectedPolicies: ['POL-AML-001', 'POL-STR-WORKFLOW-001'],
  },
  {
    id: 'rc-2026-005',
    regulator: 'SAMA', externalRef: 'SAMA-OB-2026-003',
    titleEn: 'SAMA Open Banking — Phase 3 mandatory APIs',
    titleAr: 'المصرفية المفتوحة لساما — واجهات المرحلة الثالثة الإلزامية',
    summaryEn: 'Account-information & payment-initiation APIs mandatory for all licensed banks by Q4 2026. Sandbox onboarding by end-Q2.',
    summaryAr: 'واجهات معلومات الحسابات وبدء المدفوعات إلزامية لجميع البنوك المرخّصة بحلول الربع الرابع 2026. الانضمام إلى البيئة التجريبية بحلول نهاية الربع الثاني.',
    kind: 'CIRCULAR',
    publishedAt: _daysAgo(60),
    deadline: '2026-12-31',
    impact: 'HIGH', status: 'APPLICABLE',
    ownerEmail: 'platform.lead@tss.sa', ownerNameEn: 'Sami Al-Otaibi', ownerNameAr: 'سامي العتيبي',
    affectedControls: ['SAMA Open Banking §2'],
    affectedPolicies: ['POL-API-SECURITY-001'],
  },

  // ── SDAIA — PDPL ──
  {
    id: 'rc-2026-006',
    regulator: 'PDPL', externalRef: 'SDAIA-IR-2026-001',
    titleEn: 'PDPL Implementing Regulations — adequacy decisions list',
    titleAr: 'اللوائح التنفيذية لـPDPL — قائمة قرارات الملاءمة',
    summaryEn: 'SDAIA published the first adequacy-jurisdictions list. Transfers to listed jurisdictions no longer require SCCs. Bahrain, UAE, Qatar, Kuwait approved; remainder of GCC pending.',
    summaryAr: 'نشرت سدايا أول قائمة للدول ذات الملاءمة. النقل إلى هذه الدول لم يعد يستلزم العقود النموذجية. تشمل القائمة البحرين والإمارات وقطر والكويت؛ بقية دول الخليج قيد المراجعة.',
    kind: 'GUIDANCE',
    publishedAt: _daysAgo(15),
    impact: 'HIGH', status: 'TRIAGED',
    ownerEmail: 'dpo@tss.sa', ownerNameEn: 'Mona Al-Bishi', ownerNameAr: 'منى البيشي',
    affectedControls: ['PDPL Art. 29'],
    affectedPolicies: ['POL-CROSS-BORDER-001'],
  },
  {
    id: 'rc-2026-007',
    regulator: 'PDPL', externalRef: 'SDAIA-2026-AI',
    titleEn: 'SDAIA AI Ethics Principles — final version',
    titleAr: 'مبادئ أخلاقيات الذكاء الاصطناعي — النسخة النهائية',
    summaryEn: 'Final binding AI Ethics Principles published. All regulated entities deploying AI in customer-facing systems must complete an AI Impact Assessment within 6 months.',
    summaryAr: 'نشرت سدايا النسخة النهائية الإلزامية لمبادئ أخلاقيات الذكاء الاصطناعي. تلتزم الجهات الخاضعة بإكمال تقييم أثر الذكاء الاصطناعي خلال 6 أشهر.',
    kind: 'NEW_REGULATION',
    publishedAt: _daysAgo(50),
    deadline: _daysAhead(130),
    impact: 'MEDIUM', status: 'TRIAGED',
    ownerEmail: 'compliance.lead@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    affectedControls: ['SDAIA AI Ethics §3', 'PDPL Art. 30'],
    affectedPolicies: ['POL-AI-001'],
  },

  // ── ZATCA — tax / e-invoicing ──
  {
    id: 'rc-2026-008',
    regulator: 'ZATCA', externalRef: 'ZATCA-EI-2026-W22',
    titleEn: 'ZATCA Fatoora Wave 22 — annual revenue threshold SAR 1M',
    titleAr: 'زاتكا — موجة فاتورة 22 (حدّ إيرادات سنوي 1 مليون ريال)',
    summaryEn: 'Wave 22 brings entities with annual revenue ≥ SAR 1M into Phase-2 e-invoicing. Onboarding window opens 1 Sep 2026; integration mandatory by 1 Dec 2026.',
    summaryAr: 'تشمل الموجة 22 المنشآت ذات الإيرادات السنوية 1 مليون ريال فأكثر في المرحلة الثانية للفوترة الإلكترونية. نافذة الانضمام تفتح في 1 سبتمبر 2026؛ التكامل إلزامي بحلول 1 ديسمبر 2026.',
    kind: 'DEADLINE',
    publishedAt: _daysAgo(25),
    deadline: '2026-12-01',
    impact: 'HIGH', status: 'APPLICABLE',
    ownerEmail: 'finance.compliance@tss.sa', ownerNameEn: 'Faisal Al-Maliki', ownerNameAr: 'فيصل المالكي',
    affectedControls: ['ZATCA E-Invoicing §3'],
    affectedPolicies: ['POL-EINVOICE-001'],
  },

  // ── CMA — listed companies ──
  {
    id: 'rc-2026-009',
    regulator: 'CMA', externalRef: 'CMA-CGR-2026-AMD-3',
    titleEn: 'CMA Corporate Governance Regulations — ESG disclosure annex',
    titleAr: 'لائحة حوكمة الشركات لهيئة السوق المالية — ملحق إفصاح الاستدامة',
    summaryEn: 'Listed companies must publish a TCFD-aligned ESG disclosure with the annual report. Effective from FY2026 reporting cycle.',
    summaryAr: 'الشركات المدرجة ملزمة بنشر إفصاح استدامة وفق إطار TCFD مع التقرير السنوي. ساري المفعول من دورة تقارير السنة المالية 2026.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(70),
    deadline: '2027-03-31',
    impact: 'MEDIUM', status: 'IMPLEMENTED',
    ownerEmail: 'compliance.lead@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    affectedControls: ['CMA CGR Art. 75', 'CMA ESG Guidelines'],
    affectedPolicies: ['POL-ESG-001'],
  },

  // ── HRSD — labor / Saudization ──
  {
    id: 'rc-2026-010',
    regulator: 'HRSD', externalRef: 'HRSD-NIT-2026-Q2',
    titleEn: 'Nitaqat — Q2 2026 band thresholds refreshed',
    titleAr: 'نطاقات — تحديث عتبات الربع الثاني 2026',
    summaryEn: 'Saudization band thresholds increased by 2 percentage points across IT, financial-services, and retail sectors effective 1 Jul 2026.',
    summaryAr: 'رفع عتبات نطاق السعودة بنقطتين مئويتين في قطاعات تقنية المعلومات والخدمات المالية والتجزئة اعتباراً من 1 يوليو 2026.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(28),
    deadline: '2026-07-01',
    impact: 'MEDIUM', status: 'TRIAGED',
    ownerEmail: 'hr.lead@tss.sa', ownerNameEn: 'Noura Al-Harbi', ownerNameAr: 'نورا الحربي',
    affectedControls: ['HRSD Nitaqat §4'],
    affectedPolicies: ['POL-HR-NATIONALISATION-001'],
  },

  // ── MOC — commerce ──
  {
    id: 'rc-2026-011',
    regulator: 'CITC', externalRef: 'CITC-IOT-2026-007',
    titleEn: 'CITC IoT Regulatory Framework v2',
    titleAr: 'الإطار التنظيمي لـIoT من هيئة الاتصالات — الإصدار 2',
    summaryEn: 'Refreshed device-classification taxonomy + mandatory pre-market security testing for industrial-IoT devices.',
    summaryAr: 'تحديث تصنيف الأجهزة + اشتراط اختبار أمن قبل الطرح للأجهزة الصناعية IoT.',
    kind: 'AMENDMENT',
    publishedAt: _daysAgo(45),
    deadline: _daysAhead(45),
    impact: 'LOW', status: 'NOT_APPLICABLE',
    ownerEmail: 'platform.lead@tss.sa', ownerNameEn: 'Sami Al-Otaibi', ownerNameAr: 'سامي العتيبي',
    affectedControls: ['CITC IoT §5'],
    affectedPolicies: [],
  },

  // ── NCEMA — emergency / BCP ──
  {
    id: 'rc-2026-012',
    regulator: 'NCEMA', externalRef: 'NCEMA-BCP-2026-DRILL',
    titleEn: 'NCEMA — Q3 2026 cross-sector business-continuity drill',
    titleAr: 'NCEMA — تمرين استمرارية الأعمال عبر القطاعات للربع الثالث 2026',
    summaryEn: 'Mandatory cross-sector BCP exercise on 17 Aug 2026. Critical-infrastructure operators must submit participation confirmation by 31 Jul.',
    summaryAr: 'تمرين استمرارية أعمال إلزامي عبر القطاعات في 17 أغسطس 2026. على مشغّلي البنية التحتية الحرجة تأكيد المشاركة بحلول 31 يوليو.',
    kind: 'DEADLINE',
    publishedAt: _daysAgo(18),
    deadline: '2026-07-31',
    impact: 'HIGH', status: 'NEW',
    ownerEmail: 'bcm.lead@tss.sa', ownerNameEn: 'Sara Al-Dosari', ownerNameAr: 'سارة الدوسري',
    affectedControls: ['NCEMA BCP §6', 'ISO 22301 §8.5'],
    affectedPolicies: ['POL-BCP-001'],
  },

  // ── NAZAHA — anti-corruption ──
  {
    id: 'rc-2026-013',
    regulator: 'NCA' as RegulatorScope, externalRef: 'NAZAHA-2026-DECL',
    titleEn: 'Nazaha — Annual conflict-of-interest declaration cycle',
    titleAr: 'نزاهة — دورة الإقرار السنوي بتعارض المصالح',
    summaryEn: 'Officers and senior managers must complete the FY2026 conflict-of-interest declaration by 30 Jun 2026.',
    summaryAr: 'يجب على المسؤولين والمدراء التنفيذيين إكمال إقرار تعارض المصالح للسنة المالية 2026 بحلول 30 يونيو 2026.',
    kind: 'DEADLINE',
    publishedAt: _daysAgo(12),
    deadline: '2026-06-30',
    impact: 'MEDIUM', status: 'NEW',
    ownerEmail: 'ethics.lead@tss.sa', ownerNameEn: 'Hala Al-Tamimi', ownerNameAr: 'هالة التميمي',
    affectedControls: ['Nazaha §3'],
    affectedPolicies: ['POL-COI-001'],
  },

  // ── Public consultation example ──
  {
    id: 'rc-2026-014',
    regulator: 'SAMA', externalRef: 'SAMA-DRAFT-AI-FS',
    titleEn: '[Draft] SAMA AI in Financial Services framework — public consultation',
    titleAr: '[مسودة] إطار الذكاء الاصطناعي في الخدمات المالية لساما — استشارة عامة',
    summaryEn: 'Open consultation on a binding framework for AI/ML in credit, AML, and customer service. Comments due 60 days from publication. Likely to take effect Q2 2027.',
    summaryAr: 'استشارة عامة على إطار ملزم للذكاء الاصطناعي في الائتمان ومكافحة غسل الأموال وخدمة العملاء. التعليقات تُستقبل خلال 60 يوماً. النفاذ المتوقّع: الربع الثاني 2027.',
    kind: 'CONSULTATION',
    publishedAt: _daysAgo(5),
    deadline: _daysAhead(55),
    impact: 'INFORMATIONAL', status: 'NEW',
    ownerEmail: 'compliance.lead@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    affectedControls: [],
    affectedPolicies: [],
  },
];

// ─── Helpers ────────────────────────────────────────────────────────────────

export function classifyByDeadline(c: RegulatoryChange, now: Date = new Date()): 'OVERDUE' | 'WITHIN_30_DAYS' | 'WITHIN_90_DAYS' | 'FUTURE' | 'NONE' {
  if (!c.deadline) return 'NONE';
  const days = (new Date(c.deadline).getTime() - now.getTime()) / 86_400_000;
  if (days < 0) return 'OVERDUE';
  if (days <= 30) return 'WITHIN_30_DAYS';
  if (days <= 90) return 'WITHIN_90_DAYS';
  return 'FUTURE';
}

export const IMPACT_TONE: Record<ImpactLevel, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  CRITICAL:      'danger',
  HIGH:          'warning',
  MEDIUM:        'info',
  LOW:           'success',
  INFORMATIONAL: 'neutral',
};

export const STATUS_TONE: Record<ChangeStatus, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  NEW:            'info',
  TRIAGED:        'warning',
  APPLICABLE:     'danger',
  NOT_APPLICABLE: 'neutral',
  IMPLEMENTED:    'success',
  MONITORING:     'info',
};

export const KIND_LABEL: Record<RegulatoryChangeKind, { en: string; ar: string }> = {
  NEW_REGULATION: { en: 'New regulation',   ar: 'تنظيم جديد' },
  AMENDMENT:      { en: 'Amendment',        ar: 'تعديل' },
  CIRCULAR:       { en: 'Circular',         ar: 'تعميم' },
  GUIDANCE:       { en: 'Guidance',         ar: 'إرشادات' },
  DEADLINE:       { en: 'Deadline',         ar: 'موعد نهائي' },
  ENFORCEMENT:    { en: 'Enforcement',      ar: 'تنفيذ' },
  CONSULTATION:   { en: 'Public consultation', ar: 'استشارة عامة' },
};

export const STATUS_LABEL: Record<ChangeStatus, { en: string; ar: string }> = {
  NEW:            { en: 'New',             ar: 'جديد' },
  TRIAGED:        { en: 'Triaged',         ar: 'تمّ الفرز' },
  APPLICABLE:     { en: 'Applicable',      ar: 'منطبق' },
  NOT_APPLICABLE: { en: 'Not applicable',  ar: 'غير منطبق' },
  IMPLEMENTED:    { en: 'Implemented',     ar: 'مُطَبَّق' },
  MONITORING:     { en: 'Monitoring',      ar: 'مراقبة' },
};

// ─── Aggregation ────────────────────────────────────────────────────────────

export interface RegChangeStats {
  total: number;
  newCount: number;
  triagedCount: number;
  applicableCount: number;
  implementedCount: number;
  overdueCount: number;
  byRegulator: Record<string, number>;
}

export function aggregateRegChange(items: ReadonlyArray<RegulatoryChange> = REGULATORY_CHANGES, now: Date = new Date()): RegChangeStats {
  const byRegulator: Record<string, number> = {};
  let newCount = 0, triagedCount = 0, applicableCount = 0, implementedCount = 0, overdueCount = 0;
  for (const c of items) {
    byRegulator[c.regulator] = (byRegulator[c.regulator] ?? 0) + 1;
    if (c.status === 'NEW') newCount++;
    else if (c.status === 'TRIAGED') triagedCount++;
    else if (c.status === 'APPLICABLE') applicableCount++;
    else if (c.status === 'IMPLEMENTED') implementedCount++;
    if (classifyByDeadline(c, now) === 'OVERDUE' && c.status !== 'IMPLEMENTED' && c.status !== 'NOT_APPLICABLE') overdueCount++;
  }
  return { total: items.length, newCount, triagedCount, applicableCount, implementedCount, overdueCount, byRegulator };
}
