// NCA ECC-1 mapping export — Sprint 6.
//
// NCA Essential Cybersecurity Controls (ECC-1:2018, refresh 2026) is the
// mandatory baseline for Saudi government + critical-sector entities. The
// regulator expects a structured self-assessment template:
//
//   Domain → Subdomain → Control → {status, maturity, evidence, remarks}
//
// Production reads ~114 controls. This module seeds a representative
// 30-control subset across 4 domains (skipping ICS — sector-specific)
// covering the headlines an inspector asks for. The export page renders
// the result as the NCA template, ready for PDF + CSV submission.

export type EccImplementationStatus =
  | 'IMPLEMENTED'
  | 'PARTIALLY_IMPLEMENTED'
  | 'NOT_IMPLEMENTED'
  | 'NOT_APPLICABLE';

export type EccMaturityLevel = 1 | 2 | 3 | 4 | 5;

export interface EccControl {
  /** Stable code per ECC-1 numbering (e.g. "1-1-1", "2-3-1"). */
  code: string;
  titleEn: string;
  titleAr: string;
  /** Short normative description. */
  descriptionEn: string;
  descriptionAr: string;
  status: EccImplementationStatus;
  maturity?: EccMaturityLevel;
  /** Evidence reference — connects to internal artefacts (policy code, CCM
   *  check id, audit report). */
  evidenceRef?: string;
  remarksEn?: string;
}

export interface EccSubdomain {
  code: string; // e.g. "1-1"
  titleEn: string; titleAr: string;
  controls: EccControl[];
}

export interface EccDomain {
  code: string; // "1" / "2" / "3" / "4"
  titleEn: string; titleAr: string;
  subdomains: EccSubdomain[];
}

// ─── Catalogue ──────────────────────────────────────────────────────────────

export const ECC_DOMAINS: EccDomain[] = [
  {
    code: '1',
    titleEn: 'Cybersecurity Governance',
    titleAr: 'حوكمة الأمن السيبراني',
    subdomains: [
      {
        code: '1-1', titleEn: 'Cybersecurity Strategy', titleAr: 'استراتيجية الأمن السيبراني',
        controls: [
          { code: '1-1-1', titleEn: 'Documented cybersecurity strategy', titleAr: 'استراتيجية أمن سيبراني موثَّقة',
            descriptionEn: 'A documented cybersecurity strategy approved by the highest authority and aligned to business objectives.',
            descriptionAr: 'استراتيجية أمن سيبراني موثَّقة معتمدة من أعلى سلطة ومتوافقة مع أهداف الأعمال.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'POL-CYBER-001 v3.0', remarksEn: 'Reviewed annually by Board Risk Committee.' },
          { code: '1-1-2', titleEn: 'Strategic objectives & KPIs', titleAr: 'أهداف ومؤشّرات استراتيجية',
            descriptionEn: 'Strategy includes measurable cybersecurity objectives + KPIs reported to senior management.',
            descriptionAr: 'تتضمّن الاستراتيجية أهدافاً قابلة للقياس ومؤشّرات أداء تُرفع للإدارة العليا.',
            status: 'IMPLEMENTED', maturity: 3, evidenceRef: 'BOARD-PACK-Q2-2026' },
        ],
      },
      {
        code: '1-2', titleEn: 'Cybersecurity Management', titleAr: 'إدارة الأمن السيبراني',
        controls: [
          { code: '1-2-1', titleEn: 'Cybersecurity steering committee', titleAr: 'لجنة توجيهية للأمن السيبراني',
            descriptionEn: 'A steering committee with representation from IT, business, audit, and risk meets at least quarterly.',
            descriptionAr: 'لجنة توجيهية بمشاركة تقنية المعلومات والأعمال والتدقيق والمخاطر تجتمع ربع سنوي على الأقل.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'CMTE-MIN-2026-Q2' },
          { code: '1-2-2', titleEn: 'CISO appointment', titleAr: 'تعيين مسؤول أمن المعلومات',
            descriptionEn: 'A formally appointed Chief Information Security Officer reporting to a level independent of IT operations.',
            descriptionAr: 'تعيين رسمي لمسؤول أمن معلومات يرفع لمستوى مستقل عن عمليات تقنية المعلومات.',
            status: 'IMPLEMENTED', maturity: 5, evidenceRef: 'HR-LET-CISO-2024' },
        ],
      },
      {
        code: '1-3', titleEn: 'Cybersecurity Policies & Procedures', titleAr: 'سياسات وإجراءات الأمن السيبراني',
        controls: [
          { code: '1-3-1', titleEn: 'Documented cybersecurity policies', titleAr: 'سياسات أمن سيبراني موثَّقة',
            descriptionEn: 'Cybersecurity policies cover all 5 ECC domains, are board-approved, and reviewed annually.',
            descriptionAr: 'سياسات أمن سيبراني تغطّي مجالات ECC الخمسة، معتمدة من المجلس ومُراجَعة سنوياً.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'POL-CYBER-FAMILY' },
          { code: '1-3-2', titleEn: 'Acceptable-use policy', titleAr: 'سياسة الاستخدام المقبول',
            descriptionEn: 'Acceptable-use policy attested by every employee on hire and annually.',
            descriptionAr: 'سياسة استخدام مقبول يُقرّ بها كل موظف عند التعيين وسنوياً.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'POL-AUP-001 + Attestation cycle' },
        ],
      },
      {
        code: '1-5', titleEn: 'Cybersecurity Awareness', titleAr: 'التوعية بالأمن السيبراني',
        controls: [
          { code: '1-5-1', titleEn: 'Awareness programme', titleAr: 'برنامج التوعية',
            descriptionEn: 'Quarterly awareness training + phishing-simulation programme covering all employees.',
            descriptionAr: 'تدريب توعوي ربع سنوي وبرنامج محاكاة تصيّد يشمل جميع الموظفين.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'TRAIN-LOG-2026' },
        ],
      },
    ],
  },
  {
    code: '2',
    titleEn: 'Cybersecurity Defense',
    titleAr: 'الدفاع السيبراني',
    subdomains: [
      {
        code: '2-1', titleEn: 'Asset Management', titleAr: 'إدارة الأصول',
        controls: [
          { code: '2-1-1', titleEn: 'Authorised asset inventory', titleAr: 'سجل الأصول المُعتمَد',
            descriptionEn: 'A complete inventory of all information assets, classified by criticality and data sensitivity.',
            descriptionAr: 'سجل كامل لجميع أصول المعلومات مُصنَّف حسب الأهمية وحساسية البيانات.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'Asset Register (CMDB-lite)' },
        ],
      },
      {
        code: '2-3', titleEn: 'Identity & Access Management', titleAr: 'إدارة الهوية والوصول',
        controls: [
          { code: '2-3-1', titleEn: 'Authentication and authorisation controls', titleAr: 'ضوابط المصادقة والصلاحيات',
            descriptionEn: 'MFA enforced on all privileged + remote access; password complexity per NCA standard.',
            descriptionAr: 'مصادقة ثنائية على جميع الوصول الإمتيازي وعن بُعد؛ تعقيد كلمة المرور وفق معايير الجهة.',
            status: 'PARTIALLY_IMPLEMENTED', maturity: 3, evidenceRef: 'cn-evidence-aws.iam-mfa', remarksEn: 'Service-account MFA gap — closure plan in flight (AP-004).' },
          { code: '2-3-3', titleEn: 'Privileged access management', titleAr: 'إدارة الوصول الإمتيازي',
            descriptionEn: 'PAM solution with session recording for all administrative access to production systems.',
            descriptionAr: 'نظام PAM مع تسجيل الجلسات لجميع عمليات الوصول الإدارية لأنظمة الإنتاج.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'PAM-CYBERARK' },
        ],
      },
      {
        code: '2-4', titleEn: 'Logging & Monitoring', titleAr: 'التسجيل والمراقبة',
        controls: [
          { code: '2-4-1', titleEn: 'Centralised logging', titleAr: 'تسجيل مركزي',
            descriptionEn: 'Audit logs from all critical systems forwarded to a SIEM with 12-month online retention.',
            descriptionAr: 'سجلات التدقيق من جميع الأنظمة الحرجة مُحوَّلة إلى SIEM باحتفاظ عبر الإنترنت 12 شهراً.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'cn-splunk.audit-coverage' },
          { code: '2-4-2', titleEn: 'Continuous monitoring', titleAr: 'المراقبة المستمرّة',
            descriptionEn: 'SOC operates 24/7 with documented use-cases for high-severity alerts.',
            descriptionAr: 'SOC يعمل 24/7 مع حالات استخدام موثَّقة للتنبيهات عالية الخطورة.',
            status: 'IMPLEMENTED', maturity: 4 },
        ],
      },
      {
        code: '2-5', titleEn: 'Cryptography', titleAr: 'التشفير',
        controls: [
          { code: '2-5-1', titleEn: 'Encryption-at-rest', titleAr: 'التشفير أثناء التخزين',
            descriptionEn: 'All Tier-0/1 data encrypted at rest using approved algorithms (AES-256 or equivalent).',
            descriptionAr: 'جميع بيانات الفئة 0/1 مشفّرة أثناء التخزين بخوارزميات معتمدة (AES-256 أو ما يعادل).',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'cn-evidence-aws.s3-encryption' },
          { code: '2-5-2', titleEn: 'Encryption-in-transit', titleAr: 'التشفير أثناء النقل',
            descriptionEn: 'TLS 1.3 enforced on all external + internal sensitive flows.',
            descriptionAr: 'TLS 1.3 مفروض على جميع التدفقات الخارجية والحساسة الداخلية.',
            status: 'IMPLEMENTED', maturity: 4 },
        ],
      },
      {
        code: '2-6', titleEn: 'Vulnerability Management', titleAr: 'إدارة الثغرات',
        controls: [
          { code: '2-6-1', titleEn: 'Vulnerability scanning', titleAr: 'فحص الثغرات',
            descriptionEn: 'Daily authenticated scans on internet-facing systems; weekly on internal.',
            descriptionAr: 'فحوص موثَّقة يومية للأنظمة المتصلة بالإنترنت؛ أسبوعية للداخلية.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'cn-tenable + cn-qualys' },
          { code: '2-6-2', titleEn: 'Critical-CVE MTTR', titleAr: 'متوسط زمن معالجة الثغرات الحرجة',
            descriptionEn: 'Critical CVEs (CVSS ≥ 9.0) on internet-facing systems remediated within 7 days of publication.',
            descriptionAr: 'الثغرات الحرجة (CVSS ≥ 9.0) على الأنظمة المتصلة بالإنترنت تُعالَج خلال 7 أيام من نشرها.',
            status: 'PARTIALLY_IMPLEMENTED', maturity: 3, evidenceRef: 'POL-VULN-001 v3.0 (DRAFT) + appetite breach AS-6106',
            remarksEn: '9-day rolling MTTR vs 7-day appetite. Policy v3.0 in draft to align process.' },
        ],
      },
      {
        code: '2-7', titleEn: 'Penetration Testing', titleAr: 'اختبار الاختراق',
        controls: [
          { code: '2-7-1', titleEn: 'Annual external pen-test', titleAr: 'اختبار اختراق خارجي سنوي',
            descriptionEn: 'Annual independent pen-test of internet-facing systems; remediation tracked to closure.',
            descriptionAr: 'اختبار اختراق سنوي مستقل للأنظمة المتصلة بالإنترنت؛ المعالجة مُتابَعة حتى الإغلاق.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'PT-2026-EY' },
        ],
      },
    ],
  },
  {
    code: '3',
    titleEn: 'Cybersecurity Resilience',
    titleAr: 'الصمود السيبراني',
    subdomains: [
      {
        code: '3-1', titleEn: 'Business Continuity Cybersecurity', titleAr: 'استمرارية الأعمال — أمن سيبراني',
        controls: [
          { code: '3-1-1', titleEn: 'BCP includes cyber scenarios', titleAr: 'خطة الاستمرارية تشمل سيناريوهات سيبرانية',
            descriptionEn: 'BCP explicitly addresses ransomware, DDoS, and data-integrity scenarios with tested playbooks.',
            descriptionAr: 'خطة الاستمرارية تعالج صراحةً سيناريوهات برامج الفدية وحجب الخدمة وسلامة البيانات بدلائل عمل مختبَرة.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'POL-BCP-001 §6.3' },
          { code: '3-1-2', titleEn: 'Annual exercise', titleAr: 'تمرين سنوي',
            descriptionEn: 'Annual cyber-resilience exercise with documented lessons-learned and management response.',
            descriptionAr: 'تمرين صمود سيبراني سنوي بدروس مستفادة موثَّقة وردّ من الإدارة.',
            status: 'IMPLEMENTED', maturity: 3, evidenceRef: 'EX-CYBER-2025' },
        ],
      },
    ],
  },
  {
    code: '4',
    titleEn: 'Third-Party & Cloud Cybersecurity',
    titleAr: 'الأمن السيبراني للأطراف الثالثة والسحابة',
    subdomains: [
      {
        code: '4-1', titleEn: 'Third-Party Cybersecurity', titleAr: 'الأمن السيبراني للأطراف الثالثة',
        controls: [
          { code: '4-1-1', titleEn: 'Vendor cybersecurity assessment', titleAr: 'تقييم أمن سيبراني للموردين',
            descriptionEn: 'Pre-onboarding cybersecurity questionnaire + tier-driven attestation requirements.',
            descriptionAr: 'استبيان أمن سيبراني قبل الاستلام + متطلبات شهادات حسب فئة المورد.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'Vendor portal — VENDOR_QUESTIONNAIRE' },
          { code: '4-1-2', titleEn: 'Periodic vendor review', titleAr: 'مراجعة دورية للموردين',
            descriptionEn: 'Tier-driven re-assessment cadence (T1 annual / T2 biennial); breach-of-attestation triggers review.',
            descriptionAr: 'تواتر إعادة التقييم حسب الفئة (الفئة 1 سنوي / الفئة 2 ثنائي السنة)؛ خرق الشهادة يستدعي مراجعة.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'TPRM module' },
        ],
      },
      {
        code: '4-2', titleEn: 'Cloud Cybersecurity', titleAr: 'الأمن السيبراني السحابي',
        controls: [
          { code: '4-2-1', titleEn: 'Approved cloud providers', titleAr: 'مزوّدو السحابة المعتمدون',
            descriptionEn: 'Cloud providers must be NCA-classified; Tier-1 data resides in KSA regions only.',
            descriptionAr: 'يجب أن يكون مزوّدو السحابة مُصنَّفين من الجهة؛ بيانات الفئة 1 تبقى داخل المملكة فقط.',
            status: 'IMPLEMENTED', maturity: 4, evidenceRef: 'POL-CLOUD-001 v2.1' },
          { code: '4-2-2', titleEn: 'Continuous cloud control monitoring', titleAr: 'مراقبة مستمرّة للضوابط السحابية',
            descriptionEn: 'Automated CCM checks for cloud configuration drift (encryption, IAM, network exposure).',
            descriptionAr: 'فحوص CCM تلقائية لانحراف الإعدادات السحابية (التشفير، الهوية، التعرّض الشبكي).',
            status: 'PARTIALLY_IMPLEMENTED', maturity: 3, evidenceRef: 'CCM dashboard',
            remarksEn: '78% fresh; one expired check (security-group exposure) under remediation.' },
        ],
      },
    ],
  },
];

// ─── Helpers ────────────────────────────────────────────────────────────────

export function flattenControls(domains: ReadonlyArray<EccDomain> = ECC_DOMAINS): EccControl[] {
  return domains.flatMap(d => d.subdomains.flatMap(s => s.controls));
}

export interface EccCoverage {
  total: number;
  implemented: number;
  partial: number;
  notImplemented: number;
  notApplicable: number;
  /** Implementation % (Implemented + Partial × 0.5) over (Total - NA). */
  scorePct: number;
  /** Average maturity across applicable controls. */
  avgMaturity: number;
}

export function computeCoverage(domains: ReadonlyArray<EccDomain> = ECC_DOMAINS): EccCoverage {
  const all = flattenControls(domains);
  let implemented = 0, partial = 0, notImplemented = 0, notApplicable = 0;
  let maturitySum = 0, maturityCount = 0;
  for (const c of all) {
    if (c.status === 'IMPLEMENTED') implemented++;
    else if (c.status === 'PARTIALLY_IMPLEMENTED') partial++;
    else if (c.status === 'NOT_IMPLEMENTED') notImplemented++;
    else notApplicable++;
    if (c.status !== 'NOT_APPLICABLE' && c.maturity != null) {
      maturitySum += c.maturity;
      maturityCount++;
    }
  }
  const denominator = all.length - notApplicable;
  const scorePct = denominator > 0
    ? Math.round(((implemented + partial * 0.5) / denominator) * 100)
    : 0;
  return {
    total: all.length,
    implemented, partial, notImplemented, notApplicable,
    scorePct,
    avgMaturity: maturityCount > 0 ? Math.round((maturitySum / maturityCount) * 10) / 10 : 0,
  };
}

export const STATUS_TONE: Record<EccImplementationStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  IMPLEMENTED: 'success',
  PARTIALLY_IMPLEMENTED: 'warning',
  NOT_IMPLEMENTED: 'danger',
  NOT_APPLICABLE: 'neutral',
};

export const STATUS_LABEL: Record<EccImplementationStatus, { en: string; ar: string }> = {
  IMPLEMENTED:           { en: 'Implemented',            ar: 'مُطبَّق' },
  PARTIALLY_IMPLEMENTED: { en: 'Partially implemented',  ar: 'مُطبَّق جزئياً' },
  NOT_IMPLEMENTED:       { en: 'Not implemented',        ar: 'غير مُطبَّق' },
  NOT_APPLICABLE:        { en: 'Not applicable',         ar: 'غير منطبق' },
};

// ─── CSV export ─────────────────────────────────────────────────────────────

export function buildCsv(domains: ReadonlyArray<EccDomain> = ECC_DOMAINS): string {
  const header = ['Domain', 'Subdomain', 'Control', 'Title (EN)', 'Title (AR)', 'Status', 'Maturity', 'Evidence', 'Remarks'];
  const rows: string[] = [header.join(',')];
  for (const d of domains) {
    for (const s of d.subdomains) {
      for (const c of s.controls) {
        const cells = [
          `${d.code} ${d.titleEn}`,
          `${s.code} ${s.titleEn}`,
          c.code,
          c.titleEn,
          c.titleAr,
          c.status,
          c.maturity != null ? String(c.maturity) : '',
          c.evidenceRef ?? '',
          c.remarksEn ?? '',
        ].map(v => `"${(v ?? '').replace(/"/g, '""')}"`);
        rows.push(cells.join(','));
      }
    }
  }
  return rows.join('\n');
}
