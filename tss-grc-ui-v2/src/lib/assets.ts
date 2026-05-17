// Asset register / CMDB-lite — Sprint 5.
//
// Until now each module kept its own asset-shaped data — Cyber had hosts,
// BCM had processes, SCM had supply-chain components, TPRM had vendors,
// CCM evidence checks referenced "systems" in narrative form. This module
// introduces a unified asset spine that all those modules can hang from,
// answering the the industry pattern CMDB question:
//
//   "If AWS Riyadh region goes down, what risks / controls / vendors /
//    findings / BCM plans / pending approvals are affected?"
//
// Saudi-leaning fields baked in:
//   • dataResidency  — KSA / GCC / GLOBAL
//   • regulatorScope — SAMA-supervised, PDPL-in-scope, NCA-critical, ZATCA, …
//   • crNumber       — vendor commercial-registry number when asset is hosted
//                      by a third-party (regulator inspection lookup)

import { CCM_CHECKS } from './ccm';
import type { ApprovalRequest } from './approvals';
import { APPROVAL_REQUESTS } from './approvals';

export type AssetType =
  | 'APPLICATION'
  | 'DATABASE'
  | 'INFRASTRUCTURE'
  | 'NETWORK'
  | 'SERVICE'
  | 'PROCESS'        // BCM-style business process
  | 'FACILITY'       // physical site
  | 'CLOUD_REGION'   // a cloud provider region (AWS-RUH, AWS-JED…)
  | 'DEVICE';

export type AssetCriticality = 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';

export type DataResidency = 'KSA' | 'GCC' | 'GLOBAL';

export type RegulatorScope =
  | 'SAMA'
  | 'NCA'
  | 'PDPL'
  | 'CMA'
  | 'ZATCA'
  | 'CITC'
  | 'NCEMA'
  | 'HRSD'
  | 'NONE';

export interface Asset {
  /** Stable asset identifier — used everywhere as a foreign key. */
  code: string;
  nameEn: string;
  nameAr: string;
  type: AssetType;
  criticality: AssetCriticality;
  /** Owner email / RACI lead. */
  ownerEmail: string;
  ownerNameEn: string;
  ownerNameAr: string;
  /** Asset hosted by a third-party? Vendor code from TPRM. */
  hostedByVendor?: string;
  /** Where the asset's data sits. */
  dataResidency: DataResidency;
  /** Which Saudi regulators care about this asset. */
  regulatorScope: RegulatorScope[];
  /** Other asset codes this asset depends on (upstream). */
  dependencies: string[];
  /** Optional descriptors. */
  descriptionEn?: string;
  descriptionAr?: string;
  /** Operational status. */
  status: 'ACTIVE' | 'RETIRED' | 'PLANNED';
  /** BCM RTO target in hours when CRITICAL — used by blast-radius for
   *  "this asset down for X hours = SLA breach" math. */
  rtoHours?: number;
}

// ─── Demo asset catalogue (~18 assets) ──────────────────────────────────────

export const ASSETS: Asset[] = [
  // ── Cloud regions (top of dependency tree) ──
  {
    code: 'CLOUD-AWS-RUH', type: 'CLOUD_REGION',
    nameEn: 'AWS Riyadh region (me-central-1)',
    nameAr: 'منطقة AWS الرياض (me-central-1)',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'cloud.platform@tss.sa', ownerNameEn: 'Layla Al-Saud', ownerNameAr: 'ليلى السعود',
    hostedByVendor: 'V-204',
    dataResidency: 'KSA', regulatorScope: ['SAMA', 'PDPL', 'NCA'],
    dependencies: [],
    rtoHours: 1,
    descriptionEn: 'Primary cloud region. Hosts all SAMA-supervised workloads.',
    descriptionAr: 'منطقة السحابة الأساسية. تستضيف جميع الأحمال الخاضعة لساما.',
  },
  {
    code: 'CLOUD-AWS-JED', type: 'CLOUD_REGION',
    nameEn: 'AWS Jeddah region (DR)',
    nameAr: 'منطقة AWS جدة (التعافي من الكوارث)',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'cloud.platform@tss.sa', ownerNameEn: 'Layla Al-Saud', ownerNameAr: 'ليلى السعود',
    hostedByVendor: 'V-204',
    dataResidency: 'KSA', regulatorScope: ['SAMA'],
    dependencies: [],
    rtoHours: 4,
  },

  // ── Core applications ──
  {
    code: 'APP-WEB-PROD-01', type: 'APPLICATION',
    nameEn: 'Customer web frontend',
    nameAr: 'الواجهة الإلكترونية للعملاء',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'product.lead@tss.sa', ownerNameEn: 'Sara Al-Dosari', ownerNameAr: 'سارة الدوسري',
    dataResidency: 'KSA', regulatorScope: ['PDPL', 'NCA'],
    dependencies: ['CLOUD-AWS-RUH', 'NET-API-GW', 'DB-PRIMARY', 'INF-LDAP'],
    rtoHours: 1,
  },
  {
    code: 'APP-MOBILE-IOS', type: 'APPLICATION',
    nameEn: 'Mobile app (iOS / Android)',
    nameAr: 'التطبيق الجوّال (iOS / Android)',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'mobile.lead@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    dataResidency: 'KSA', regulatorScope: ['PDPL', 'SAMA'],
    dependencies: ['CLOUD-AWS-RUH', 'NET-API-GW', 'DB-PRIMARY', 'INF-LDAP'],
    rtoHours: 2,
  },
  {
    code: 'APP-PAYMENT-GW', type: 'APPLICATION',
    nameEn: 'Payment gateway integration',
    nameAr: 'تكامل بوابة الدفع',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'payments.lead@tss.sa', ownerNameEn: 'Faisal Al-Maliki', ownerNameAr: 'فيصل المالكي',
    dataResidency: 'KSA', regulatorScope: ['SAMA', 'PDPL'],
    dependencies: ['CLOUD-AWS-RUH', 'DB-PRIMARY'],
    rtoHours: 1,
  },
  {
    code: 'APP-CORP-WIKI', type: 'APPLICATION',
    nameEn: 'Corporate wiki',
    nameAr: 'الويكي المؤسسي',
    criticality: 'MEDIUM', status: 'ACTIVE',
    ownerEmail: 'kb.team@tss.sa', ownerNameEn: 'Hala Al-Tamimi', ownerNameAr: 'هالة التميمي',
    dataResidency: 'KSA', regulatorScope: ['NONE'],
    dependencies: ['CLOUD-AWS-RUH'],
  },
  {
    code: 'APP-JENKINS-CI', type: 'APPLICATION',
    nameEn: 'Jenkins CI/CD',
    nameAr: 'Jenkins CI/CD',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'devops.lead@tss.sa', ownerNameEn: 'Mohammed Al-Otaibi', ownerNameAr: 'محمد العتيبي',
    dataResidency: 'KSA', regulatorScope: ['NCA'],
    dependencies: ['CLOUD-AWS-RUH'],
    descriptionEn: 'Build & deploy pipeline. Has an active policy exception (EXC-402).',
    descriptionAr: 'خط البناء والنشر. لديه استثناء سياسة نشط (EXC-402).',
  },

  // ── Databases ──
  {
    code: 'DB-PRIMARY', type: 'DATABASE',
    nameEn: 'SQL Server primary (customer + payment)',
    nameAr: 'قاعدة بيانات SQL الأساسية (العملاء والدفع)',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'data.lead@tss.sa', ownerNameEn: 'Mona Al-Bishi', ownerNameAr: 'منى البيشي',
    dataResidency: 'KSA', regulatorScope: ['PDPL', 'SAMA', 'NCA'],
    dependencies: ['CLOUD-AWS-RUH'],
    rtoHours: 1,
    descriptionEn: 'Customer master data + payment transaction store.',
    descriptionAr: 'البيانات الرئيسية للعملاء ومخزن معاملات الدفع.',
  },
  {
    code: 'DB-WAREHOUSE', type: 'DATABASE',
    nameEn: 'Analytics data warehouse',
    nameAr: 'مستودع بيانات التحليلات',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'data.lead@tss.sa', ownerNameEn: 'Mona Al-Bishi', ownerNameAr: 'منى البيشي',
    dataResidency: 'KSA', regulatorScope: ['PDPL'],
    dependencies: ['CLOUD-AWS-RUH', 'DB-PRIMARY'],
    rtoHours: 24,
  },

  // ── Network ──
  {
    code: 'NET-API-GW', type: 'NETWORK',
    nameEn: 'API gateway (public-facing)',
    nameAr: 'بوابة API (عامة)',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'platform.lead@tss.sa', ownerNameEn: 'Sami Al-Otaibi', ownerNameAr: 'سامي العتيبي',
    dataResidency: 'KSA', regulatorScope: ['NCA', 'PDPL'],
    dependencies: ['CLOUD-AWS-RUH'],
    rtoHours: 1,
  },
  {
    code: 'NET-VPN-GW', type: 'NETWORK',
    nameEn: 'VPN gateway',
    nameAr: 'بوابة VPN',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'network.lead@tss.sa', ownerNameEn: 'Khaled Al-Rasheed', ownerNameAr: 'خالد الراشد',
    dataResidency: 'KSA', regulatorScope: ['NCA'],
    dependencies: [],
  },

  // ── Identity & infrastructure ──
  {
    code: 'INF-LDAP', type: 'INFRASTRUCTURE',
    nameEn: 'LDAP / identity directory',
    nameAr: 'دليل الهوية LDAP',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'iam.team@tss.sa', ownerNameEn: 'Ahmed Al-Qahtani', ownerNameAr: 'أحمد القحطاني',
    dataResidency: 'KSA', regulatorScope: ['NCA', 'PDPL'],
    dependencies: ['CLOUD-AWS-RUH'],
    rtoHours: 1,
  },
  {
    code: 'INF-FILESHARE', type: 'INFRASTRUCTURE',
    nameEn: 'Production file share',
    nameAr: 'مشاركة الملفات الإنتاجية',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'storage.team@tss.sa', ownerNameEn: 'Saud Al-Dosari', ownerNameAr: 'سعود الدوسري',
    dataResidency: 'KSA', regulatorScope: ['PDPL'],
    dependencies: ['CLOUD-AWS-RUH'],
  },

  // ── Business processes ──
  {
    code: 'PROC-ONLINE-AUTH', type: 'PROCESS',
    nameEn: 'Online banking authentication',
    nameAr: 'مصادقة الخدمات المصرفية الإلكترونية',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'product.lead@tss.sa', ownerNameEn: 'Sara Al-Dosari', ownerNameAr: 'سارة الدوسري',
    dataResidency: 'KSA', regulatorScope: ['SAMA', 'PDPL'],
    dependencies: ['APP-WEB-PROD-01', 'APP-MOBILE-IOS', 'INF-LDAP'],
    rtoHours: 1,
  },
  {
    code: 'PROC-CARD-AUTH', type: 'PROCESS',
    nameEn: 'Card payment authorization',
    nameAr: 'تفويض دفع البطاقات',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'payments.lead@tss.sa', ownerNameEn: 'Faisal Al-Maliki', ownerNameAr: 'فيصل المالكي',
    dataResidency: 'KSA', regulatorScope: ['SAMA'],
    dependencies: ['APP-PAYMENT-GW', 'DB-PRIMARY'],
    rtoHours: 1,
  },
  {
    code: 'PROC-SALARY-RUN', type: 'PROCESS',
    nameEn: 'Salary disbursement run (WPS)',
    nameAr: 'دورة صرف الرواتب (نظام حماية الأجور)',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'hr.payroll@tss.sa', ownerNameEn: 'Noura Al-Harbi', ownerNameAr: 'نورا الحربي',
    dataResidency: 'KSA', regulatorScope: ['HRSD', 'PDPL'],
    dependencies: ['DB-PRIMARY', 'APP-PAYMENT-GW'],
    rtoHours: 24,
  },

  // ── Facility ──
  {
    code: 'FAC-HQ-RIYADH', type: 'FACILITY',
    nameEn: 'Head office — Riyadh',
    nameAr: 'المقر الرئيسي — الرياض',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'facilities.lead@tss.sa', ownerNameEn: 'Mohammed Al-Ahmadi', ownerNameAr: 'محمد الأحمدي',
    dataResidency: 'KSA', regulatorScope: ['NCEMA'],
    dependencies: [],
  },

  // ── External SaaS ──
  {
    code: 'SVC-OFFICE365', type: 'SERVICE',
    nameEn: 'Microsoft 365 (email + collaboration)',
    nameAr: 'Microsoft 365 (البريد والتعاون)',
    criticality: 'HIGH', status: 'ACTIVE',
    ownerEmail: 'it.lead@tss.sa', ownerNameEn: 'Layan Kheir', ownerNameAr: 'ليان خير',
    hostedByVendor: 'V-104',
    dataResidency: 'GLOBAL', regulatorScope: ['PDPL', 'NCA'],
    dependencies: [],
    descriptionEn: 'Outside KSA — flagged for cross-border data-transfer review under PDPL.',
    descriptionAr: 'خارج المملكة — مُحدَّد للمراجعة الدورية لنقل البيانات عبر الحدود وفق PDPL.',
  },
  {
    code: 'SVC-ZATCA-FATOORA', type: 'SERVICE',
    nameEn: 'ZATCA Fatoora (e-invoicing)',
    nameAr: 'فاتورة ZATCA (الفوترة الإلكترونية)',
    criticality: 'CRITICAL', status: 'ACTIVE',
    ownerEmail: 'finance.compliance@tss.sa', ownerNameEn: 'Hassan Al-Saud', ownerNameAr: 'حسن السعود',
    dataResidency: 'KSA', regulatorScope: ['ZATCA'],
    dependencies: ['DB-PRIMARY'],
    rtoHours: 4,
    descriptionEn: 'ZATCA Phase-2 e-invoicing integration. Reject-rate threshold 0.5%/month.',
    descriptionAr: 'تكامل المرحلة الثانية للفوترة الإلكترونية ZATCA. حدّ معدل الرفض 0.5% شهرياً.',
  },
];

// ─── Cross-reference helpers ────────────────────────────────────────────────
//
// Each module has its own data shape; these helpers translate "asset code →
// related entities" without forcing every module to adopt the asset model
// upfront. As real schemas grow these become straight DB joins.

export interface AssetImpact {
  /** Risks that reference this asset (by name match in title/description). */
  riskCount: number;
  /** CCM checks tied to controls covering this asset (via owner mapping). */
  ccmCheckCount: number;
  /** Approval requests blocked on this asset's owner. */
  approvalCount: number;
  /** Other assets that depend on this one (downstream blast radius). */
  downstreamCount: number;
  /** Other assets this one depends on (upstream). */
  upstreamCount: number;
}

/** Resolve downstream dependents — assets whose `dependencies` contain `code`. */
export function downstreamOf(code: string, all: ReadonlyArray<Asset> = ASSETS): Asset[] {
  return all.filter(a => a.dependencies.includes(code));
}

/** Resolve upstream dependencies. */
export function upstreamOf(code: string, all: ReadonlyArray<Asset> = ASSETS): Asset[] {
  const a = all.find(x => x.code === code);
  if (!a) return [];
  return a.dependencies
    .map(d => all.find(x => x.code === d))
    .filter((x): x is Asset => x != null);
}

/** Recursive transitive blast radius. Returns set of asset codes reachable
 *  downstream within `maxDepth` hops. */
export function transitiveBlastRadius(code: string, maxDepth = 5, all: ReadonlyArray<Asset> = ASSETS): Set<string> {
  const out = new Set<string>();
  const visit = (c: string, depth: number) => {
    if (depth > maxDepth) return;
    for (const dep of downstreamOf(c, all)) {
      if (out.has(dep.code)) continue;
      out.add(dep.code);
      visit(dep.code, depth + 1);
    }
  };
  visit(code, 0);
  return out;
}

/** CCM checks whose owner matches this asset's owner — used as a proxy for
 *  "checks that monitor this asset" until each check stores an explicit
 *  assetCode. Real production: add `assetCode` to CcmCheck. */
export function ccmChecksForAsset(asset: Asset): typeof CCM_CHECKS {
  return CCM_CHECKS.filter(c => c.owner === asset.ownerEmail);
}

/** Approval requests whose source ref or owner matches this asset. */
export function approvalsForAsset(asset: Asset): ApprovalRequest[] {
  return APPROVAL_REQUESTS.filter(r =>
    r.sourceRef.includes(asset.code) || (r.summaryEn ?? '').toLowerCase().includes(asset.nameEn.toLowerCase())
  );
}

/** Compose a single AssetImpact summary. Keep all the linkage logic in one
 *  place so the UI just reads counts. */
export function impactFor(asset: Asset, all: ReadonlyArray<Asset> = ASSETS): AssetImpact {
  return {
    riskCount: 0, // hooked up in production via Risk module — placeholder
    ccmCheckCount: ccmChecksForAsset(asset).length,
    approvalCount: approvalsForAsset(asset).length,
    downstreamCount: downstreamOf(asset.code, all).length,
    upstreamCount: asset.dependencies.length,
  };
}

// ─── Display helpers ────────────────────────────────────────────────────────

export const CRITICALITY_TONE: Record<AssetCriticality, 'success' | 'info' | 'warning' | 'danger'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

export const TYPE_LABEL: Record<AssetType, { en: string; ar: string }> = {
  APPLICATION:    { en: 'Application',    ar: 'تطبيق' },
  DATABASE:       { en: 'Database',       ar: 'قاعدة بيانات' },
  INFRASTRUCTURE: { en: 'Infrastructure', ar: 'بنية تحتية' },
  NETWORK:        { en: 'Network',        ar: 'شبكة' },
  SERVICE:        { en: 'Service',        ar: 'خدمة' },
  PROCESS:        { en: 'Process',        ar: 'عملية' },
  FACILITY:       { en: 'Facility',       ar: 'منشأة' },
  CLOUD_REGION:   { en: 'Cloud region',   ar: 'منطقة سحابية' },
  DEVICE:         { en: 'Device',         ar: 'جهاز' },
};

export const RESIDENCY_TONE: Record<DataResidency, 'success' | 'info' | 'warning'> = {
  KSA: 'success', GCC: 'info', GLOBAL: 'warning',
};
