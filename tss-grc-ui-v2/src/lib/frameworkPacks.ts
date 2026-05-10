// Framework packs — Track D1 (content moat).
//
// Installable, regulator-blessed packages that ship with TSS GRC out of the
// box. Each pack carries a bundle of:
//
//   • Domains (top-level grouping for the controls library)
//   • Controls (numbered control statements, EN + AR)
//   • Evidence templates (the artefacts each control expects)
//   • Default policy mapping (which TSS policy template satisfies which control)
//
// The packs are designed for Saudi-first deployments. Installing a pack
// stores the pack code + installation timestamp in localStorage and emits a
// change event — the rest of the platform picks that up to filter the
// controls library, populate the regulator-pivot view, and pre-check the
// onboarding wizard's framework selection.

export type Mandate = 'mandatory' | 'recommended' | 'voluntary';

export interface Domain {
  code: string;
  nameEn: string;
  nameAr: string;
}

export interface Control {
  /** Stable code, e.g. "ECC-2-1-1". */
  code: string;
  /** Domain code this control belongs to. */
  domain: string;
  titleEn: string;
  titleAr: string;
  /** Tier-1 = baseline; Tier-2 = additional for sensitive entities. */
  tier: 1 | 2;
}

export interface EvidenceTemplate {
  code: string;
  nameEn: string;
  nameAr: string;
  /** Recommended cadence — annual / quarterly / monthly / event-driven. */
  cadenceEn: string;
  cadenceAr: string;
}

export interface FrameworkPack {
  code: string;
  nameEn: string;
  nameAr: string;
  regulator: string;
  version: string;
  mandate: Mandate;
  effectiveYear: number;
  /** Short marketing-style blurb shown on the install card. */
  blurbEn: string;
  blurbAr: string;
  /** What this pack solves for the GRC officer. */
  outcomesEn: string[];
  outcomesAr: string[];
  domains: Domain[];
  controls: Control[];
  evidenceTemplates: EvidenceTemplate[];
}

// ─── PACK 1 · NCA ECC v3 ─────────────────────────────────────────────────────

const NCA_ECC: FrameworkPack = {
  code: 'NCA-ECC-V3',
  nameEn: 'NCA Essential Cybersecurity Controls v3',
  nameAr: 'الضوابط الأساسية للأمن السيبراني — الإصدار الثالث',
  regulator: 'National Cybersecurity Authority (NCA)',
  version: '3.0',
  mandate: 'mandatory',
  effectiveYear: 2024,
  blurbEn: 'The mandatory baseline for every Saudi entity that processes citizen data or is part of critical infrastructure. Updated for the 2024 revision.',
  blurbAr: 'الإطار الأساسي الإلزامي لكل جهة سعودية تعالج بيانات المواطنين أو ترتبط بالبنية التحتية الحساسة. مُحدَّث وفق إصدار 2024.',
  outcomesEn: [
    'Map every cybersecurity control to its NCA domain in one click',
    'Auto-generate the NCA self-assessment export (Excel + PDF)',
    'Trigger CAPA workflows on Tier-1 control gaps',
  ],
  outcomesAr: [
    'ربط كلّ ضابط أمن سيبراني بمجاله في الإطار بنقرة واحدة',
    'توليد تلقائي للتقييم الذاتي المطلوب من الهيئة (Excel + PDF)',
    'تشغيل تلقائي لإجراءات CAPA على فجوات الضوابط من الفئة الأولى',
  ],
  domains: [
    { code: '1', nameEn: 'Cybersecurity Governance',           nameAr: 'حوكمة الأمن السيبراني' },
    { code: '2', nameEn: 'Cybersecurity Defense',              nameAr: 'الدفاع السيبراني' },
    { code: '3', nameEn: 'Cybersecurity Resilience',           nameAr: 'الصمود السيبراني' },
    { code: '4', nameEn: 'Third-Party Cybersecurity',          nameAr: 'الأمن السيبراني للأطراف الثالثة' },
    { code: '5', nameEn: 'Industrial Control Systems',         nameAr: 'أنظمة التحكّم الصناعية' },
  ],
  controls: [
    { code: 'ECC-1-1-1', domain: '1', tier: 1, titleEn: 'Cybersecurity strategy aligned to business objectives', titleAr: 'استراتيجية الأمن السيبراني المتسقة مع أهداف الأعمال' },
    { code: 'ECC-1-2-1', domain: '1', tier: 1, titleEn: 'Cybersecurity policy approved by the board',             titleAr: 'سياسة الأمن السيبراني المعتمدة من مجلس الإدارة' },
    { code: 'ECC-1-3-1', domain: '1', tier: 1, titleEn: 'Roles and responsibilities (RACI)',                       titleAr: 'الأدوار والمسؤوليات (RACI)' },
    { code: 'ECC-2-1-1', domain: '2', tier: 1, titleEn: 'Asset inventory and classification',                      titleAr: 'جرد الأصول وتصنيفها' },
    { code: 'ECC-2-2-1', domain: '2', tier: 1, titleEn: 'Identity and access management',                          titleAr: 'إدارة الهويات والوصول' },
    { code: 'ECC-2-3-1', domain: '2', tier: 2, titleEn: 'Privileged access management',                            titleAr: 'إدارة الوصول المُميَّز' },
    { code: 'ECC-2-4-1', domain: '2', tier: 1, titleEn: 'Network segmentation and zero trust',                     titleAr: 'تجزئة الشبكة وثقة معدومة' },
    { code: 'ECC-2-5-1', domain: '2', tier: 1, titleEn: 'Vulnerability management',                                titleAr: 'إدارة الثغرات' },
    { code: 'ECC-2-6-1', domain: '2', tier: 1, titleEn: 'Endpoint protection',                                     titleAr: 'حماية النقاط الطرفية' },
    { code: 'ECC-3-1-1', domain: '3', tier: 1, titleEn: 'Incident response plan',                                  titleAr: 'خطة الاستجابة للحوادث' },
    { code: 'ECC-3-2-1', domain: '3', tier: 1, titleEn: 'Business continuity & disaster recovery',                 titleAr: 'استمرارية الأعمال والتعافي من الكوارث' },
    { code: 'ECC-4-1-1', domain: '4', tier: 1, titleEn: 'Third-party risk assessment before contract',             titleAr: 'تقييم مخاطر الأطراف الثالثة قبل التعاقد' },
    { code: 'ECC-4-2-1', domain: '4', tier: 2, titleEn: 'Cloud service provider due diligence',                    titleAr: 'العناية الواجبة لمزوّدي الخدمات السحابية' },
    { code: 'ECC-5-1-1', domain: '5', tier: 2, titleEn: 'OT / ICS network isolation',                              titleAr: 'عزل شبكات أنظمة التحكّم الصناعية' },
  ],
  evidenceTemplates: [
    { code: 'ECC-EV-001', nameEn: 'Cybersecurity strategy document',  nameAr: 'وثيقة استراتيجية الأمن السيبراني', cadenceEn: 'Annual',     cadenceAr: 'سنوي' },
    { code: 'ECC-EV-002', nameEn: 'Asset inventory export',           nameAr: 'تصدير جرد الأصول',                  cadenceEn: 'Quarterly',   cadenceAr: 'ربع سنوي' },
    { code: 'ECC-EV-003', nameEn: 'Vulnerability scan reports',       nameAr: 'تقارير فحص الثغرات',                cadenceEn: 'Monthly',     cadenceAr: 'شهري' },
    { code: 'ECC-EV-004', nameEn: 'Incident response drill report',   nameAr: 'تقرير تمرين الاستجابة للحوادث',       cadenceEn: 'Annual',     cadenceAr: 'سنوي' },
    { code: 'ECC-EV-005', nameEn: 'Third-party assessment register',  nameAr: 'سجل تقييم الأطراف الثالثة',           cadenceEn: 'Continuous', cadenceAr: 'مستمر' },
  ],
};

// ─── PACK 2 · SAMA RBSF (Risk-Based Supervision Framework) ───────────────────

const SAMA_RBSF: FrameworkPack = {
  code: 'SAMA-RBSF',
  nameEn: 'SAMA Risk-Based Supervision Framework',
  nameAr: 'إطار الرقابة القائم على المخاطر — البنك المركزي السعودي (ساما)',
  regulator: 'Saudi Central Bank (SAMA)',
  version: '2024',
  mandate: 'mandatory',
  effectiveYear: 2024,
  blurbEn: 'Mandatory for SAMA-licensed banks, insurers, finance companies and payment service providers. Covers the full risk universe — credit, market, liquidity, operational, compliance.',
  blurbAr: 'إلزامي للبنوك وشركات التأمين وشركات التمويل ومزوّدي خدمات الدفع المرخّصين من ساما. يغطّي عالم المخاطر بالكامل — الائتمان والسوق والسيولة والتشغيل والالتزام.',
  outcomesEn: [
    'Pre-built ICAAP / ILAAP submission templates',
    'Operational risk loss event reporting aligned to SAMA taxonomy',
    'Compliance breach 7-day notification workflow',
  ],
  outcomesAr: [
    'قوالب جاهزة لتقديم ICAAP / ILAAP',
    'الإبلاغ عن حوادث الخسارة التشغيلية وفق تصنيف ساما',
    'سير عمل إشعار اختراق الالتزام خلال 7 أيام',
  ],
  domains: [
    { code: 'CR', nameEn: 'Credit Risk',          nameAr: 'مخاطر الائتمان' },
    { code: 'MR', nameEn: 'Market Risk',          nameAr: 'مخاطر السوق' },
    { code: 'LR', nameEn: 'Liquidity Risk',       nameAr: 'مخاطر السيولة' },
    { code: 'OR', nameEn: 'Operational Risk',     nameAr: 'المخاطر التشغيلية' },
    { code: 'CR2', nameEn: 'Compliance Risk',     nameAr: 'مخاطر الالتزام' },
    { code: 'IT', nameEn: 'IT & Cyber Risk',      nameAr: 'مخاطر تقنية المعلومات والأمن السيبراني' },
  ],
  controls: [
    { code: 'RBSF-CR-001',  domain: 'CR',  tier: 1, titleEn: 'Credit risk appetite statement',                       titleAr: 'بيان شهية المخاطر الائتمانية' },
    { code: 'RBSF-CR-002',  domain: 'CR',  tier: 1, titleEn: 'Borrower internal rating system',                      titleAr: 'نظام التصنيف الداخلي للمقترضين' },
    { code: 'RBSF-MR-001',  domain: 'MR',  tier: 1, titleEn: 'Trading book VaR computation',                         titleAr: 'حساب القيمة المعرَّضة للخطر في دفتر المتاجرة' },
    { code: 'RBSF-LR-001',  domain: 'LR',  tier: 1, titleEn: 'Liquidity coverage ratio (LCR) monitoring',            titleAr: 'مراقبة نسبة تغطية السيولة' },
    { code: 'RBSF-OR-001',  domain: 'OR',  tier: 1, titleEn: 'Operational loss event collection',                    titleAr: 'جمع حوادث الخسارة التشغيلية' },
    { code: 'RBSF-OR-002',  domain: 'OR',  tier: 1, titleEn: 'Risk and control self-assessment (RCSA)',              titleAr: 'التقييم الذاتي للمخاطر والضوابط' },
    { code: 'RBSF-CR2-001', domain: 'CR2', tier: 1, titleEn: 'AML / CFT customer due diligence',                     titleAr: 'العناية الواجبة بالعملاء — مكافحة غسل الأموال وتمويل الإرهاب' },
    { code: 'RBSF-IT-001',  domain: 'IT',  tier: 1, titleEn: 'IT outsourcing register and exit plans',               titleAr: 'سجل إسناد تقنية المعلومات وخطط الخروج' },
  ],
  evidenceTemplates: [
    { code: 'RBSF-EV-001', nameEn: 'ICAAP submission package',     nameAr: 'حزمة ICAAP المُقدَّمة',          cadenceEn: 'Annual',    cadenceAr: 'سنوي' },
    { code: 'RBSF-EV-002', nameEn: 'Operational loss register',    nameAr: 'سجل الخسائر التشغيلية',          cadenceEn: 'Monthly',   cadenceAr: 'شهري' },
    { code: 'RBSF-EV-003', nameEn: 'RCSA workshop output',         nameAr: 'مخرجات ورشة RCSA',                cadenceEn: 'Quarterly', cadenceAr: 'ربع سنوي' },
    { code: 'RBSF-EV-004', nameEn: 'AML training attendance',      nameAr: 'حضور تدريب مكافحة غسل الأموال',   cadenceEn: 'Annual',    cadenceAr: 'سنوي' },
  ],
};

// ─── PACK 3 · SDAIA AI Ethics ────────────────────────────────────────────────

const SDAIA_AI: FrameworkPack = {
  code: 'SDAIA-AI',
  nameEn: 'SDAIA AI Ethics Principles',
  nameAr: 'مبادئ أخلاقيات الذكاء الاصطناعي — هيئة سدايا',
  regulator: 'Saudi Data and AI Authority (SDAIA)',
  version: '2.0',
  mandate: 'recommended',
  effectiveYear: 2023,
  blurbEn: 'Saudi national AI governance — fairness, transparency, accountability and human oversight for organisations deploying AI.',
  blurbAr: 'حوكمة الذكاء الاصطناعي السعودية — العدالة والشفافية والمساءلة والإشراف البشري للجهات التي تنشر أنظمة ذكاء اصطناعي.',
  outcomesEn: [
    'Risk-tier every AI use case (Acceptable / High-Risk / Prohibited)',
    'Generate fairness + bias evaluation evidence',
    'Maintain a human-in-the-loop register for high-risk decisions',
  ],
  outcomesAr: [
    'تصنيف كل حالة استخدام للذكاء الاصطناعي حسب المخاطر (مقبول / عالي المخاطر / محظور)',
    'إنتاج أدلّة تقييم العدالة والتحيّز',
    'إدارة سجل الإشراف البشري للقرارات عالية المخاطر',
  ],
  domains: [
    { code: 'F',  nameEn: 'Fairness',         nameAr: 'العدالة' },
    { code: 'T',  nameEn: 'Transparency',     nameAr: 'الشفافية' },
    { code: 'A',  nameEn: 'Accountability',   nameAr: 'المساءلة' },
    { code: 'HO', nameEn: 'Human Oversight',  nameAr: 'الإشراف البشري' },
    { code: 'P',  nameEn: 'Privacy + Security', nameAr: 'الخصوصية والأمان' },
  ],
  controls: [
    { code: 'AI-F-001',  domain: 'F',  tier: 1, titleEn: 'AI use case fairness assessment',                titleAr: 'تقييم عدالة حالات استخدام الذكاء الاصطناعي' },
    { code: 'AI-F-002',  domain: 'F',  tier: 1, titleEn: 'Training data bias review',                      titleAr: 'مراجعة تحيّز بيانات التدريب' },
    { code: 'AI-T-001',  domain: 'T',  tier: 1, titleEn: 'Model card publication',                         titleAr: 'نشر بطاقة النموذج (Model Card)' },
    { code: 'AI-T-002',  domain: 'T',  tier: 1, titleEn: 'Disclosure when interacting with AI system',     titleAr: 'إفصاح عند التفاعل مع نظام ذكاء اصطناعي' },
    { code: 'AI-A-001',  domain: 'A',  tier: 1, titleEn: 'AI system owner accountability matrix',          titleAr: 'مصفوفة مساءلة مالكي أنظمة الذكاء الاصطناعي' },
    { code: 'AI-HO-001', domain: 'HO', tier: 1, titleEn: 'Human-in-the-loop for high-risk decisions',       titleAr: 'الإشراف البشري في القرارات عالية المخاطر' },
    { code: 'AI-P-001',  domain: 'P',  tier: 1, titleEn: 'AI training data PDPL compliance',               titleAr: 'التزام بيانات التدريب بنظام حماية البيانات الشخصية' },
  ],
  evidenceTemplates: [
    { code: 'AI-EV-001', nameEn: 'AI use case register',           nameAr: 'سجل حالات استخدام الذكاء الاصطناعي', cadenceEn: 'Continuous', cadenceAr: 'مستمر' },
    { code: 'AI-EV-002', nameEn: 'Fairness evaluation report',     nameAr: 'تقرير تقييم العدالة',                cadenceEn: 'Per release', cadenceAr: 'لكل إصدار' },
    { code: 'AI-EV-003', nameEn: 'Model card',                     nameAr: 'بطاقة النموذج',                       cadenceEn: 'Per model',   cadenceAr: 'لكل نموذج' },
    { code: 'AI-EV-004', nameEn: 'Human review log',               nameAr: 'سجل المراجعة البشرية',                cadenceEn: 'Continuous',  cadenceAr: 'مستمر' },
  ],
};

// ─── PACK 4 · PDPL ───────────────────────────────────────────────────────────

const PDPL: FrameworkPack = {
  code: 'PDPL',
  nameEn: 'Personal Data Protection Law',
  nameAr: 'نظام حماية البيانات الشخصية',
  regulator: 'Saudi Data and AI Authority (SDAIA)',
  version: '2023 + Implementing Regulations 2024',
  mandate: 'mandatory',
  effectiveYear: 2023,
  blurbEn: '72-hour breach notification, ROPA, DPIA, cross-border transfer safeguards and data subject rights for Saudi residents.',
  blurbAr: 'إشعار الخرق خلال 72 ساعة، سجل أنشطة المعالجة، تقييم الأثر، ضمانات النقل العابر للحدود، وحقوق أصحاب البيانات للمقيمين في السعودية.',
  outcomesEn: [
    'PDPL article-by-article control mapping',
    'Breach console with 72h SLA + Hijri calendar',
    'Data subject request portal pre-wired',
  ],
  outcomesAr: [
    'ربط الضوابط مادّة-بمادّة من نظام حماية البيانات',
    'لوحة الخروقات مع التزام 72 ساعة وتقويم هجري',
    'بوابة طلبات أصحاب البيانات جاهزة',
  ],
  domains: [
    { code: 'L',  nameEn: 'Lawfulness',          nameAr: 'المشروعية' },
    { code: 'R',  nameEn: 'Data Subject Rights', nameAr: 'حقوق صاحب البيانات' },
    { code: 'S',  nameEn: 'Security',            nameAr: 'الأمان' },
    { code: 'B',  nameEn: 'Breach Notification', nameAr: 'إشعار الخروقات' },
    { code: 'CB', nameEn: 'Cross-Border Transfer', nameAr: 'النقل العابر للحدود' },
  ],
  controls: [
    { code: 'PDPL-L-001',  domain: 'L',  tier: 1, titleEn: 'Lawful basis for every processing activity',     titleAr: 'سند نظامي لكلّ نشاط معالجة' },
    { code: 'PDPL-L-002',  domain: 'L',  tier: 1, titleEn: 'Record of Processing Activities (ROPA)',          titleAr: 'سجل أنشطة المعالجة' },
    { code: 'PDPL-R-001',  domain: 'R',  tier: 1, titleEn: 'Data subject access request portal',              titleAr: 'بوابة طلبات الوصول لأصحاب البيانات' },
    { code: 'PDPL-S-001',  domain: 'S',  tier: 1, titleEn: 'Encryption of personal data at rest',             titleAr: 'تشفير البيانات الشخصية أثناء التخزين' },
    { code: 'PDPL-S-002',  domain: 'S',  tier: 1, titleEn: 'Encryption in transit',                           titleAr: 'تشفير البيانات أثناء النقل' },
    { code: 'PDPL-B-001',  domain: 'B',  tier: 1, titleEn: '72-hour breach notification to SDAIA',            titleAr: 'إشعار الخرق إلى سدايا خلال 72 ساعة' },
    { code: 'PDPL-CB-001', domain: 'CB', tier: 1, titleEn: 'Cross-border transfer adequacy assessment',       titleAr: 'تقييم كفاية النقل العابر للحدود' },
  ],
  evidenceTemplates: [
    { code: 'PDPL-EV-001', nameEn: 'ROPA export',                 nameAr: 'تصدير سجل أنشطة المعالجة',         cadenceEn: 'Quarterly',   cadenceAr: 'ربع سنوي' },
    { code: 'PDPL-EV-002', nameEn: 'DPIA report',                 nameAr: 'تقرير تقييم أثر الخصوصية',          cadenceEn: 'Per project', cadenceAr: 'لكل مشروع' },
    { code: 'PDPL-EV-003', nameEn: 'Breach incident report',      nameAr: 'تقرير حادثة الخرق',                  cadenceEn: 'Event-driven', cadenceAr: 'حسب الحدث' },
    { code: 'PDPL-EV-004', nameEn: 'Cross-border transfer log',   nameAr: 'سجل النقل العابر للحدود',            cadenceEn: 'Continuous',  cadenceAr: 'مستمر' },
  ],
};

export const FRAMEWORK_PACKS: FrameworkPack[] = [NCA_ECC, SAMA_RBSF, SDAIA_AI, PDPL];

// ─── Persistence ─────────────────────────────────────────────────────────────

const KEY = 'grc.framework-packs.installed.v1';
const EVENT = 'grc.framework-packs.changed';

export interface InstalledPack {
  code: string;
  installedAt: string;
}

export function loadInstalled(): InstalledPack[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY);
    if (!raw) return [];
    return JSON.parse(raw) as InstalledPack[];
  } catch { return []; }
}

export function isInstalled(code: string): boolean {
  return loadInstalled().some(p => p.code === code);
}

export function installPack(code: string): void {
  const existing = loadInstalled();
  if (existing.some(p => p.code === code)) return;
  const next = [...existing, { code, installedAt: new Date().toISOString() }];
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function uninstallPack(code: string): void {
  const next = loadInstalled().filter(p => p.code !== code);
  try { window.localStorage.setItem(KEY, JSON.stringify(next)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribeFrameworkPacks(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

/** Aggregate stats for the page header. */
export function packStats() {
  const installed = loadInstalled();
  const totalControls = FRAMEWORK_PACKS
    .filter(p => installed.some(i => i.code === p.code))
    .reduce((acc, p) => acc + p.controls.length, 0);
  const totalEvidence = FRAMEWORK_PACKS
    .filter(p => installed.some(i => i.code === p.code))
    .reduce((acc, p) => acc + p.evidenceTemplates.length, 0);
  return {
    installedCount: installed.length,
    availableCount: FRAMEWORK_PACKS.length,
    totalControls,
    totalEvidence,
  };
}
