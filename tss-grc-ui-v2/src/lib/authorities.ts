// Single source of truth for the regulatory + advisory authorities the
// platform recognises. Every Framework / Control / Evidence / Finding /
// Issue / Audit Engagement is anchored to one or more of these.
//
// Mirrors industry-standard GRC tooling's "Authority Document" library pattern.
// Each authority owns one or more Frameworks; Frameworks own Controls;
// Evidence anchors back to controls via {authorityCode, frameworkCode,
// controlRef}.
//
// References:
//   Saudi NRC   = المركز الوطني لإدارة المخاطر (national risk register)
//   SAMA         = مؤسسة النقد العربي السعودي / Saudi Central Bank
//   NCA          = الهيئة الوطنية للأمن السيبراني
//   SDAIA        = الهيئة السعودية للبيانات والذكاء الاصطناعي
//   CITC         = هيئة الاتصالات وتقنية المعلومات
//   DGA          = هيئة الحكومة الرقمية
//   NCEMA        = المركز الوطني لإدارة الكوارث والأزمات
//   CMA          = هيئة السوق المالية
//   ZATCA        = هيئة الزكاة والضريبة والجمارك
//   HRSD         = وزارة الموارد البشرية والتنمية الاجتماعية
//   Nazaha       = هيئة الرقابة ومكافحة الفساد

export type AuthorityType = 'SAUDI_REGULATORY' | 'SAUDI_ADVISORY' | 'INTERNATIONAL';

export type AuthorityDomain =
  | 'CYBER' | 'PRIVACY' | 'BCM' | 'RISK' | 'TPRM' | 'SCM'
  | 'COMPLIANCE' | 'ETHICS' | 'AML' | 'FINANCE' | 'TAX' | 'HR'
  | 'GOVERNANCE' | 'TELECOM' | 'CLOUD' | 'GOVERNMENT';

export interface AuthorityDto {
  /** Stable code used for joins (`NCA`, `SAMA`, `ISO_27001`). */
  code: string;
  /** English name. */
  nameEn: string;
  /** Arabic name. */
  nameAr: string;
  /** Short tagline. */
  taglineEn?: string;
  taglineAr?: string;
  /** Jurisdiction (Saudi Arabia / International / GCC / EU / etc.). */
  jurisdiction: string;
  /** Authority classification. */
  authorityType: AuthorityType;
  /** Domains the authority issues mandates in. */
  domains: AuthorityDomain[];
  /** Public website. */
  website?: string;
  /** Brand colour token used for chips / charts. */
  colorToken: string;
  /** Codes of frameworks this authority owns (joined to FrameworkDto.code). */
  frameworkCodes: string[];
}

// ── Saudi authorities (regulatory) ───────────────────────────────────────────
export const AUTHORITIES: AuthorityDto[] = [
  {
    code: 'NRC', nameEn: 'Saudi National Risk Center', nameAr: 'المركز الوطني لإدارة المخاطر',
    taglineEn: 'National risk register & sectoral resilience',
    taglineAr: 'السجل الوطني للمخاطر والصمود القطاعي',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['RISK', 'BCM', 'SCM', 'TPRM', 'GOVERNANCE'],
    website: 'https://nrc.gov.sa',
    colorToken: 'var(--cat-1)',
    frameworkCodes: ['NRC_TIER_MAPPING', 'NRC_NATIONAL_RISK'],
  },
  {
    code: 'SAMA', nameEn: 'Saudi Central Bank (SAMA)', nameAr: 'البنك المركزي السعودي',
    taglineEn: 'Banking, payments, insurance, fintech',
    taglineAr: 'البنوك والمدفوعات والتأمين والتقنية المالية',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['CYBER', 'AML', 'BCM', 'FINANCE', 'GOVERNANCE'],
    website: 'https://sama.gov.sa',
    colorToken: 'var(--cat-3)',
    frameworkCodes: ['SAMA_CSF', 'SAMA_AML', 'SAMA_BCP', 'SAMA_ITGF', 'SAMA_OPEN_BANKING'],
  },
  {
    code: 'NCA', nameEn: 'National Cybersecurity Authority', nameAr: 'الهيئة الوطنية للأمن السيبراني',
    taglineEn: 'All-sector cybersecurity mandates',
    taglineAr: 'تفويضات الأمن السيبراني للقطاعات جميعها',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['CYBER', 'CLOUD', 'GOVERNANCE'],
    website: 'https://nca.gov.sa',
    colorToken: 'var(--primary-700)',
    frameworkCodes: ['NCA_ECC', 'NCA_CCC', 'NCA_CSCC', 'NCA_DCC', 'NCA_OTCC'],
  },
  {
    code: 'SDAIA', nameEn: 'Saudi Data & AI Authority', nameAr: 'الهيئة السعودية للبيانات والذكاء الاصطناعي',
    taglineEn: 'Personal data + AI governance',
    taglineAr: 'البيانات الشخصية وحوكمة الذكاء الاصطناعي',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['PRIVACY', 'GOVERNANCE'],
    website: 'https://sdaia.gov.sa',
    colorToken: 'var(--accent-700)',
    frameworkCodes: ['PDPL', 'PDPL_IR', 'SDAIA_AI_ETHICS'],
  },
  {
    code: 'CITC', nameEn: 'Communications & IT Commission', nameAr: 'هيئة الاتصالات وتقنية المعلومات',
    taglineEn: 'Telecom + ICT + cloud regulation',
    taglineAr: 'تنظيم الاتصالات وتقنية المعلومات والحوسبة السحابية',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['TELECOM', 'CLOUD', 'CYBER'],
    website: 'https://citc.gov.sa',
    colorToken: 'var(--cat-5)',
    frameworkCodes: ['CITC_CLOUD', 'CITC_IOT', 'CITC_CYBERSEC'],
  },
  {
    code: 'DGA', nameEn: 'Digital Government Authority', nameAr: 'هيئة الحكومة الرقمية',
    taglineEn: 'Government digital services + interop',
    taglineAr: 'الخدمات الحكومية الرقمية والتوافق التشغيلي',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['GOVERNMENT', 'CYBER'],
    website: 'https://dga.gov.sa',
    colorToken: 'var(--cat-7)',
    frameworkCodes: ['DGA_DMI', 'DGA_INTEROP'],
  },
  {
    code: 'NCEMA', nameEn: 'National Center for Emergency Management', nameAr: 'المركز الوطني لإدارة الكوارث والأزمات',
    taglineEn: 'Crisis + business continuity',
    taglineAr: 'إدارة الأزمات واستمرارية الأعمال',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['BCM', 'RISK'],
    colorToken: 'var(--cat-2)',
    frameworkCodes: ['NCEMA_BCP', 'NCEMA_CRISIS'],
  },
  {
    code: 'CMA', nameEn: 'Capital Market Authority', nameAr: 'هيئة السوق المالية',
    taglineEn: 'Listed companies + capital markets',
    taglineAr: 'الشركات المدرجة وأسواق رأس المال',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['FINANCE', 'GOVERNANCE'],
    website: 'https://cma.org.sa',
    colorToken: 'var(--cat-6)',
    frameworkCodes: ['CMA_CG', 'CMA_ESG', 'CMA_AML'],
  },
  {
    code: 'ZATCA', nameEn: 'Zakat, Tax & Customs Authority', nameAr: 'هيئة الزكاة والضريبة والجمارك',
    taglineEn: 'Tax, e-invoicing, customs',
    taglineAr: 'الزكاة والضريبة والفوترة الإلكترونية والجمارك',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['TAX', 'FINANCE'],
    website: 'https://zatca.gov.sa',
    colorToken: 'var(--cat-4)',
    frameworkCodes: ['ZATCA_FATOORA', 'ZATCA_CUSTOMS'],
  },
  {
    code: 'HRSD', nameEn: 'Ministry of Human Resources & Social Development', nameAr: 'وزارة الموارد البشرية والتنمية الاجتماعية',
    taglineEn: 'Labor law, GOSI, Nitaqat (Saudization)',
    taglineAr: 'نظام العمل والتأمينات الاجتماعية ونطاقات (السعودة)',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['HR', 'PRIVACY'],
    website: 'https://hrsd.gov.sa',
    colorToken: 'var(--cat-8)',
    // DD-83: Nitaqat (نطاقات) is HRSD's third major regime — Saudization
    // band classification driving visa quotas, work-permit issuance, and
    // government-tender eligibility.
    frameworkCodes: ['HRSD_LABOR', 'HRSD_GOSI', 'HRSD_NITAQAT'],
  },
  {
    code: 'NAZAHA', nameEn: 'Anti-Corruption Authority (Nazaha)', nameAr: 'هيئة الرقابة ومكافحة الفساد (نزاهة)',
    taglineEn: 'Anti-corruption + integrity',
    taglineAr: 'مكافحة الفساد والنزاهة',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['ETHICS', 'GOVERNANCE'],
    website: 'https://nazaha.gov.sa',
    colorToken: 'var(--cat-3)',
    frameworkCodes: ['NAZAHA_ANTI_CORRUPTION'],
  },
  // DD-85: Ministry of Commerce — Tasattur (anti-concealment), Consumer
  // Protection, and Commercial Registry. The most universal Saudi
  // regulator: every commercial license touches MOC, and Tasattur is one
  // of the most-cited violations in MOC enforcement reports.
  {
    code: 'MOC', nameEn: 'Ministry of Commerce', nameAr: 'وزارة التجارة',
    taglineEn: 'Tasattur, Consumer Protection, Commercial Registry',
    taglineAr: 'التستر، حماية المستهلك، السجل التجاري',
    jurisdiction: 'Saudi Arabia', authorityType: 'SAUDI_REGULATORY',
    domains: ['COMPLIANCE', 'GOVERNANCE'],
    website: 'https://mc.gov.sa',
    colorToken: 'var(--cat-5)',
    frameworkCodes: ['MOC_TASATTUR', 'MOC_CONSUMER_PROTECTION', 'MOC_COMMERCIAL_REGISTRY'],
  },

  // ── International benchmarks ────────────────────────────────────────────
  { code: 'ISO',  nameEn: 'ISO',  nameAr: 'الآيزو',
    taglineEn: 'International standards', jurisdiction: 'International',
    authorityType: 'INTERNATIONAL',
    domains: ['CYBER', 'PRIVACY', 'BCM', 'RISK', 'ETHICS', 'COMPLIANCE'],
    colorToken: 'var(--ink-3)',
    frameworkCodes: ['ISO_27001', 'ISO_27701', 'ISO_22301', 'ISO_31000', 'ISO_37001', 'ISO_28001'] },
  // NIST (US standards body) intentionally removed per user feedback:
  // "NIST is not suitable to Saudi". For Saudi-context dashboards the
  // equivalent posture comes from NCA ECC (cyber), NCA OTCC (operational
  // tech / supply chain) and ISO 31000 (risk) — all already in the list.
  // Anyone still wanting NIST as a benchmark can pull it from the
  // controlCrosswalk page where it's a column option, not a tile.
  { code: 'AICPA', nameEn: 'AICPA', nameAr: 'المعهد الأمريكي للمحاسبين القانونيين',
    taglineEn: 'Trust Services Criteria (SOC 2)',
    jurisdiction: 'International', authorityType: 'INTERNATIONAL',
    domains: ['COMPLIANCE', 'CYBER'],
    colorToken: 'var(--cat-2)',
    frameworkCodes: ['SOC_2'] },
  { code: 'PCI',  nameEn: 'PCI Security Standards Council', nameAr: 'مجلس معايير أمن صناعة بطاقات الدفع',
    taglineEn: 'Card-data security',
    jurisdiction: 'International', authorityType: 'INTERNATIONAL',
    domains: ['CYBER', 'FINANCE'],
    colorToken: 'var(--cat-6)',
    frameworkCodes: ['PCI_DSS'] },
  { code: 'COSO', nameEn: 'COSO', nameAr: 'لجنة الرعاية للمنظمات (الأمريكية)',
    taglineEn: 'Enterprise risk + internal control',
    jurisdiction: 'International', authorityType: 'INTERNATIONAL',
    domains: ['RISK', 'COMPLIANCE'],
    colorToken: 'var(--cat-1)',
    frameworkCodes: ['COSO_ERM', 'COSO_IC'] },
  { code: 'FATF', nameEn: 'FATF', nameAr: 'مجموعة العمل المالي',
    taglineEn: 'Anti-money-laundering + counter-terrorist financing',
    jurisdiction: 'International', authorityType: 'INTERNATIONAL',
    domains: ['AML', 'COMPLIANCE'],
    colorToken: 'var(--cat-3)',
    frameworkCodes: ['FATF'] },
];

// ── Helpers ────────────────────────────────────────────────────────────────
const AUTHORITY_BY_CODE: Record<string, AuthorityDto> = Object.fromEntries(AUTHORITIES.map(a => [a.code, a]));
export function findAuthority(code: string): AuthorityDto | undefined {
  return AUTHORITY_BY_CODE[code];
}

/** Authorities that issue mandates relevant to a given module/domain. */
export function authoritiesForDomain(domain: AuthorityDomain): AuthorityDto[] {
  return AUTHORITIES.filter(a => a.domains.includes(domain));
}

/** Locate the authority that owns a given framework code. */
const AUTHORITY_BY_FW: Record<string, AuthorityDto> = {};
for (const a of AUTHORITIES) for (const fw of a.frameworkCodes) AUTHORITY_BY_FW[fw] = a;
export function authorityForFramework(frameworkCode: string): AuthorityDto | undefined {
  return AUTHORITY_BY_FW[frameworkCode];
}
