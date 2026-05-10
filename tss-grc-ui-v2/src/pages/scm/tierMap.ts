// Multi-tier supply-chain map — Saudi National Risk Center (NRC)
// requirement + NIST SP 800-161 r1 + ISO 28001:2007.
//
// The Saudi Vision 2030 supply-chain resilience programme (and NCEMA
// business-continuity guidance) require organisations to maintain
// visibility n-tiers deep into their supply chain so that critical-
// service single-points-of-failure can be identified, geographic
// concentration can be quantified, and foreign-dependency risk can be
// reduced as part of national sovereign-cloud / sovereign-data
// initiatives.
//
// Each row captures a single "Service → Tier-1 Vendor → Tier-2 Vendor
// (or sub-supplier) → Tier-N" chain.

export interface TierLink {
  /** Tier-1 / Tier-2 / Tier-3 — depth into the supply chain. */
  tier: 1 | 2 | 3;
  /** Vendor name as it appears at that tier. */
  vendor: string;
  /** Country of operation — feeds geographic concentration analysis. */
  country: string;
  /** Role in the chain ("Hyperscaler", "Sub-processor", "Sub-component supplier"). */
  role: string;
  /** Whether the vendor is on a Saudi-sovereign or in-Kingdom path. */
  inKingdom?: boolean;
  /** Concentration flag — only one Tier-N supplier visible. */
  concentration?: boolean;
}

export interface SupplyChainTier {
  /** End-service this chain supports ("Public cloud workloads"). */
  serviceNameEn: string;
  serviceNameAr?: string;
  /** Bottom-line criticality of the *service*. */
  criticality: 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW';
  /** Linked dependency in mockScm.ts (if any) — for cross-reference. */
  dependencyId?: number;
  /** Ordered chain from service down to the deepest visible tier. */
  chain: TierLink[];
  /** Identified single-point-of-failure (if any) anywhere in the chain. */
  spof?: string;
  /** Mitigations / alternatives recorded. */
  mitigations?: string;
  /** Last refresh date for the mapping (NRC requires annual review). */
  lastReviewedOn: string;
}

export const SUPPLY_CHAIN_TIERS: SupplyChainTier[] = [
  {
    serviceNameEn: 'Public cloud workloads (production)',
    serviceNameAr: 'أحمال العمل السحابية',
    criticality: 'CRITICAL', dependencyId: 301,
    chain: [
      { tier: 1, vendor: 'AWS Middle East',         country: 'BH', role: 'Hyperscaler (Bahrain region)' },
      { tier: 2, vendor: 'NVIDIA',                  country: 'US', role: 'GPU sub-supplier (training nodes)' },
      { tier: 2, vendor: 'Equinix',                 country: 'BH', role: 'Co-location facility' },
      { tier: 3, vendor: 'TSMC',                    country: 'TW', role: 'Foundry — chip fabrication', concentration: true },
    ],
    spof: 'Tier-3 chip foundry concentration (Taiwan).',
    mitigations: 'Multi-region failover to STC Cloud sovereign region (Riyadh) within 4h.',
    lastReviewedOn: '2026-03-30',
  },
  {
    serviceNameEn: 'Sovereign cloud (PDPL Tier-2 data)',
    serviceNameAr: 'السحابة السيادية',
    criticality: 'CRITICAL', dependencyId: 306,
    chain: [
      { tier: 1, vendor: 'STC Cloud',               country: 'KSA', role: 'Sovereign cloud platform', inKingdom: true },
      { tier: 2, vendor: 'Huawei',                  country: 'CN',  role: 'Underlying infrastructure', concentration: true },
      { tier: 3, vendor: 'SMIC',                    country: 'CN',  role: 'Chip foundry', concentration: true },
    ],
    spof: 'Single Tier-1 sovereign provider in-Kingdom.',
    mitigations: 'Workload portability validated against AWS Riyadh sovereign region (planned Q4).',
    lastReviewedOn: '2026-03-12',
  },
  {
    serviceNameEn: 'Power feed (HQ tower)',
    serviceNameAr: 'إمدادات الكهرباء',
    criticality: 'CRITICAL', dependencyId: 303,
    chain: [
      { tier: 1, vendor: 'Saudi Electricity Co.',    country: 'KSA', role: 'Sole utility provider', inKingdom: true, concentration: true },
      { tier: 2, vendor: 'GE Power',                 country: 'US',  role: 'Turbine OEM (Riyadh PP-12)' },
      { tier: 2, vendor: 'Saudi Aramco',             country: 'KSA', role: 'Fuel feedstock', inKingdom: true },
    ],
    spof: 'Single 11kV feeder; sole utility provider.',
    mitigations: '72-hour diesel + UPS for HQ; DR site on independent feeder.',
    lastReviewedOn: '2026-01-09',
  },
  {
    serviceNameEn: 'Enterprise WAN (HQ ↔ DR)',
    serviceNameAr: 'الشبكة الواسعة',
    criticality: 'CRITICAL', dependencyId: 302,
    chain: [
      { tier: 1, vendor: 'Mobily Business',          country: 'KSA', role: 'Primary carrier (MPLS + SIP)', inKingdom: true, concentration: true },
      { tier: 2, vendor: 'Cisco Systems',            country: 'US',  role: 'CPE + SD-WAN platform' },
      { tier: 2, vendor: 'Telefonica International', country: 'ES',  role: 'International transit upstream' },
    ],
    spof: 'Single primary carrier; SD-WAN diversification still pending.',
    mitigations: 'SD-WAN diversification to STC + LTE failover scheduled Q3.',
    lastReviewedOn: '2026-04-02',
  },
  {
    serviceNameEn: 'Sea freight (chemicals)',
    serviceNameAr: 'النقل البحري للكيماويات',
    criticality: 'HIGH', dependencyId: 305,
    chain: [
      { tier: 1, vendor: 'Bahri Maritime Services',  country: 'KSA', role: 'Sea freight charter', inKingdom: true },
      { tier: 2, vendor: 'Hyundai Heavy Industries', country: 'KR',  role: 'Vessel OEM' },
      { tier: 2, vendor: 'Suez Canal Authority',     country: 'EG',  role: 'Maritime transit chokepoint' },
    ],
    mitigations: 'Cape-of-Good-Hope contingency lane in BCP playbook (5-day delay).',
    lastReviewedOn: '2026-03-12',
  },
  {
    serviceNameEn: 'Networking hardware refresh',
    serviceNameAr: 'تجديد أجهزة الشبكات',
    criticality: 'MEDIUM', dependencyId: 304,
    chain: [
      { tier: 1, vendor: 'Cisco MENA Distribution',  country: 'UAE', role: 'Distribution partner' },
      { tier: 2, vendor: 'Cisco Systems',            country: 'US',  role: 'OEM' },
      { tier: 3, vendor: 'Foxconn',                  country: 'TW',  role: 'Contract manufacturer' },
    ],
    mitigations: 'HPE Aruba qualified as alternative (12-week lead time).',
    lastReviewedOn: '2026-01-22',
  },
  {
    serviceNameEn: 'Card payment authorisation',
    serviceNameAr: 'تفويض مدفوعات البطاقات',
    criticality: 'CRITICAL',
    chain: [
      { tier: 1, vendor: 'Saudi Payments (mada)',    country: 'KSA', role: 'Domestic switch', inKingdom: true },
      { tier: 1, vendor: 'Mastercard',               country: 'US',  role: 'International scheme' },
      { tier: 2, vendor: 'Thales',                   country: 'FR',  role: 'HSM hardware OEM' },
      { tier: 2, vendor: 'AWS Middle East',          country: 'BH',  role: 'Authorisation hosting' },
    ],
    mitigations: 'Dual-scheme routing; mada-only fallback for domestic cardholders.',
    lastReviewedOn: '2026-04-15',
  },
  {
    serviceNameEn: 'AML transaction screening',
    serviceNameAr: 'فحص المعاملات لمكافحة غسل الأموال',
    criticality: 'HIGH',
    chain: [
      { tier: 1, vendor: 'NICE Actimize',            country: 'IL',  role: 'AML engine' },
      { tier: 2, vendor: 'Refinitiv World-Check',    country: 'GB',  role: 'PEP / sanctions watchlist data' },
      { tier: 2, vendor: 'Microsoft Azure',          country: 'AE',  role: 'Hosting region (UAE North)' },
    ],
    spof: 'Single AML-engine vendor; data-feed and platform separately concentrated.',
    mitigations: 'Quarterly model-validation; STR fallback path validated.',
    lastReviewedOn: '2026-02-22',
  },
];
