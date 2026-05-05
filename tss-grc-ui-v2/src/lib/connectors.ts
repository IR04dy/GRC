// Connector-driven intake registry — DD-62.
//
// Until now intake had two channels: web forms (DD-55..59) and API
// pushes from the live system (KRI breaches → preventive CAPAs in
// DD-43, effectiveness re-tests in DD-44). This file catalogues the
// *third* class of intake — automated connectors pulling from
// external monitoring + scanner + regulator-watch sources.
//
// Modelled on:
//   ServiceNow IRM Continuous Monitoring connectors
//   Vanta + Drata: AWS / Okta / GitHub / Jira evidence + finding feeds
//   ServiceNow GRC Threat-Intel TAXII feed
//   OneTrust Regulatory Research feed
//
// Each connector definition records:
//   - source system (vendor, name)
//   - what it auto-creates (target intake kind, target register)
//   - cadence (every 5 min / hourly / nightly)
//   - last successful run + next scheduled run
//   - regulator anchor (why this connector matters)

import type { IntakeKind } from './intake';

export type ConnectorStatus = 'ACTIVE' | 'CONFIGURED' | 'AVAILABLE' | 'ERROR' | 'DISABLED';

export type ConnectorCadence = 'STREAMING' | 'EVERY_5MIN' | 'HOURLY' | 'EVERY_4H' | 'DAILY' | 'WEEKLY';

export interface ConnectorDefinition {
  id: string;
  /** Source vendor / product. */
  source: string;
  nameEn: string;
  nameAr: string;
  descriptionEn: string;
  descriptionAr: string;
  /** What kind of intake record this connector auto-creates. */
  intakeKind?: IntakeKind;
  /** Target register (when not via intake). */
  targetEntity?: 'RISK' | 'INCIDENT' | 'VULNERABILITY' | 'EVIDENCE' | 'REG_CHANGE' | 'CAPA' | 'FINDING';
  cadence: ConnectorCadence;
  status: ConnectorStatus;
  /** Standards anchor (e.g. NCA ECC 2.6, ISO 27001 §A.8.8). */
  reference: string;
  lastRunAt?: string;
  /** Records auto-ingested in last 30 days. */
  intake30d?: number;
  /** Demo-mode owner stakeholder code. */
  owner?: string;
}

export const CONNECTOR_REGISTRY: ConnectorDefinition[] = [
  // ── Vulnerability scanners → cyber.vulnerabilities ──
  {
    id: 'cn-tenable',
    source: 'Tenable.io',
    nameEn: 'Tenable Vulnerability Scanner',
    nameAr: 'فاحص الثغرات Tenable',
    descriptionEn: 'Daily import of CVE-tagged findings; auto-creates a CyberVulnerability record per host/CVE pair.',
    descriptionAr: 'استيراد يومي لثغرات CVE؛ يُنشئ سجل ثغرة سيبرانية لكل ثنائية مضيف/CVE.',
    intakeKind: undefined, targetEntity: 'VULNERABILITY',
    cadence: 'DAILY', status: 'ACTIVE',
    reference: 'NCA ECC 2.6, ISO 27001 §A.8.8',
    lastRunAt: '2026-05-05T03:00:00', intake30d: 247, owner: 'soc.lead@tss.sa',
  },
  {
    id: 'cn-qualys',
    source: 'Qualys VMDR',
    nameEn: 'Qualys VMDR',
    nameAr: 'Qualys VMDR',
    descriptionEn: 'Continuous scanning + asset discovery; feeds vulnerabilities + asset register.',
    descriptionAr: 'فحص مستمر واكتشاف أصول؛ يُغذّي سجل الثغرات والأصول.',
    targetEntity: 'VULNERABILITY',
    cadence: 'STREAMING', status: 'CONFIGURED',
    reference: 'NCA ECC 2.6', owner: 'soc.lead@tss.sa',
  },
  // ── SIEM → cyber.incidents ──
  {
    id: 'cn-splunk',
    source: 'Splunk Enterprise Security',
    nameEn: 'Splunk SIEM',
    nameAr: 'Splunk SIEM',
    descriptionEn: 'Notable events from SIEM correlation; auto-create cyber-incident drafts at HIGH/CRITICAL severity.',
    descriptionAr: 'الأحداث ذات الأهمية من ربط SIEM؛ تُنشئ مسودات حوادث سيبرانية بشدّة عالية/حرجة.',
    intakeKind: 'SECURITY_INCIDENT', targetEntity: 'INCIDENT',
    cadence: 'EVERY_5MIN', status: 'ACTIVE',
    reference: 'NCA ECC 6.1, NIST 800-61', lastRunAt: '2026-05-05T08:55:00', intake30d: 18, owner: 'csirt.lead@tss.sa',
  },
  // ── Regulator-watch → compliance.regulatory_changes ──
  {
    id: 'cn-sdaia-feed',
    source: 'SDAIA RSS',
    nameEn: 'SDAIA Regulator Watch',
    nameAr: 'مراقب جهة سدايا',
    descriptionEn: 'Polls SDAIA publications; new circular → regulatory-change record + DPO impact-assessment task.',
    descriptionAr: 'يستعلم من منشورات سدايا؛ التعميم الجديد → سجل تغيير تنظيمي + مهمّة تقييم أثر للـDPO.',
    targetEntity: 'REG_CHANGE',
    cadence: 'HOURLY', status: 'ACTIVE',
    reference: 'PDPL Implementing Regulations',
    lastRunAt: '2026-05-05T08:00:00', intake30d: 4, owner: 'dpo@tss.sa',
  },
  {
    id: 'cn-nca-feed',
    source: 'NCA RSS',
    nameEn: 'NCA Regulator Watch',
    nameAr: 'مراقب جهة الأمن السيبراني',
    descriptionEn: 'Polls NCA circulars and ECC updates; new directive → regulatory-change record + CISO assignment.',
    descriptionAr: 'يستعلم من تعاميم الجهة وتحديثات ECC؛ توجيه جديد → سجل تغيير تنظيمي + إسناد للـCISO.',
    targetEntity: 'REG_CHANGE',
    cadence: 'HOURLY', status: 'ACTIVE',
    reference: 'NCA Circulars 2026', lastRunAt: '2026-05-05T08:00:00', intake30d: 2, owner: 'compliance.lead@tss.sa',
  },
  {
    id: 'cn-sama-feed',
    source: 'SAMA Circulars',
    nameEn: 'SAMA Circular Feed',
    nameAr: 'مراقب جهة ساما',
    descriptionEn: 'Polls SAMA RH portal for new circulars (CSF, AML, Outsourcing, Open Banking).',
    descriptionAr: 'يستعلم من بوابة ساما عن التعاميم الجديدة (CSF، مكافحة غسل الأموال، الإسناد، المصرفية المفتوحة).',
    targetEntity: 'REG_CHANGE',
    cadence: 'HOURLY', status: 'ACTIVE',
    reference: 'SAMA Circulars + CSF', lastRunAt: '2026-05-05T08:00:00', intake30d: 6, owner: 'compliance.lead@tss.sa',
  },
  {
    id: 'cn-zatca-feed',
    source: 'ZATCA Decisions',
    nameEn: 'ZATCA Tax Watch',
    nameAr: 'مراقب جهة الزكاة والضريبة',
    descriptionEn: 'Polls ZATCA for e-invoicing waves, new tax decisions, and FATOORA updates.',
    descriptionAr: 'يستعلم من الزكاة والضريبة عن موجات الفوترة الإلكترونية والقرارات الضريبية وتحديثات FATOORA.',
    targetEntity: 'REG_CHANGE',
    cadence: 'DAILY', status: 'CONFIGURED',
    reference: 'ZATCA E-Invoicing', owner: 'finance.compliance@tss.sa',
  },
  // ── Vendor SOC 2 expiry watcher → TPRM ──
  {
    id: 'cn-vendor-attestation',
    source: 'Internal TPRM scheduler',
    nameEn: 'Vendor SOC 2 / ISO 27001 expiry watcher',
    nameAr: 'مراقب انتهاء SOC 2 / ISO 27001 للموردين',
    descriptionEn: 'When a vendor attestation lapses, auto-create a periodic-review intake for TPRM.',
    descriptionAr: 'عند انتهاء صلاحية إقرار مورد، يُنشئ تلقائياً استلام مراجعة دورية لـTPRM.',
    intakeKind: 'VENDOR_ONBOARDING',
    cadence: 'WEEKLY', status: 'ACTIVE',
    reference: 'SAMA Outsourcing §5.4',
    lastRunAt: '2026-05-04T00:00:00', intake30d: 3, owner: 'tprm.lead@tss.sa',
  },
  // ── Threat-intel TAXII ──
  {
    id: 'cn-taxii',
    source: 'STIX/TAXII feed',
    nameEn: 'Threat Intelligence (STIX/TAXII)',
    nameAr: 'الاستخبارات السيبرانية (STIX/TAXII)',
    descriptionEn: 'Pulls IoC feeds (Saudi-CERT, MISP) for downstream SIEM tagging + threat-record creation.',
    descriptionAr: 'يسحب موجزات مؤشرات الاختراق (Saudi-CERT، MISP) لوسم SIEM وإنشاء سجلات تهديدات.',
    targetEntity: 'INCIDENT',
    cadence: 'EVERY_4H', status: 'CONFIGURED',
    reference: 'NIST CSF ID.RA-2', owner: 'soc.lead@tss.sa',
  },
  // ── Effectiveness check (already shipped via DD-44 — registered for visibility) ──
  {
    id: 'cn-effectiveness',
    source: 'Internal CAPA scheduler',
    nameEn: 'CAPA effectiveness re-test',
    nameAr: 'إعادة اختبار فاعلية الإجراءات التصحيحية',
    descriptionEn: 'Fires 90 days post-closure. Auto-flags VERIFICATION_FAILED if regression detected.',
    descriptionAr: 'يُفعَّل بعد 90 يوماً من الإغلاق. يضع علامة VERIFICATION_FAILED تلقائياً عند رصد تراجع.',
    targetEntity: 'CAPA',
    cadence: 'DAILY', status: 'ACTIVE',
    reference: 'ISO 9001 §10.2.2, FDA 21 CFR §820.100',
    lastRunAt: '2026-05-05T02:00:00', intake30d: 1, owner: 'audit.lead@tss.sa',
  },
  // ── KRI breach (already in DD-43) ──
  {
    id: 'cn-kri-monitor',
    source: 'Internal KRI scheduler',
    nameEn: 'KRI threshold monitor',
    nameAr: 'مراقب عتبات المؤشرات',
    descriptionEn: 'Continuously evaluates KRI readings against thresholds. Amber/red breach → preventive-CAPA suggestion.',
    descriptionAr: 'يقيّم باستمرار قراءات المؤشرات مقابل العتبات. تجاوز كهرماني/أحمر → اقتراح إجراء وقائي.',
    targetEntity: 'CAPA',
    cadence: 'HOURLY', status: 'ACTIVE',
    reference: 'COSO ERM Component 5, ISO 31000 §6.5',
    lastRunAt: '2026-05-05T08:00:00', intake30d: 11, owner: 'risk.officer@tss.sa',
  },
  // ── Evidence connector (Vanta-style) ──
  {
    id: 'cn-evidence-aws',
    source: 'AWS Config + IAM',
    nameEn: 'AWS Evidence Connector',
    nameAr: 'موصِّل أدلّة AWS',
    descriptionEn: 'Pulls IAM policies, S3 bucket configs, RDS encryption, security-group rules to feed evidence.',
    descriptionAr: 'يسحب سياسات IAM، إعدادات S3، تشفير RDS، قواعد المجموعات الأمنية لتغذية الأدلّة.',
    targetEntity: 'EVIDENCE',
    cadence: 'DAILY', status: 'AVAILABLE',
    reference: 'SOC 2 CC-6.1, ISO 27001 §A.8.32', owner: 'cloud.platform@tss.sa',
  },
];

export const CADENCE_LABEL: Record<ConnectorCadence, { en: string; ar: string }> = {
  STREAMING:  { en: 'Real-time',     ar: 'حي' },
  EVERY_5MIN: { en: 'Every 5 min',   ar: 'كل 5 دقائق' },
  HOURLY:     { en: 'Hourly',        ar: 'كل ساعة' },
  EVERY_4H:   { en: 'Every 4 hours', ar: 'كل 4 ساعات' },
  DAILY:      { en: 'Daily',         ar: 'يومياً' },
  WEEKLY:     { en: 'Weekly',        ar: 'أسبوعياً' },
};

export const CONNECTOR_STATUS_TONE: Record<ConnectorStatus, 'success' | 'info' | 'warning' | 'danger' | 'neutral'> = {
  ACTIVE:     'success',
  CONFIGURED: 'info',
  AVAILABLE:  'neutral',
  ERROR:      'danger',
  DISABLED:   'neutral',
};

export const CONNECTOR_STATUS_LABEL: Record<ConnectorStatus, { en: string; ar: string }> = {
  ACTIVE:     { en: 'Active',     ar: 'نشط' },
  CONFIGURED: { en: 'Configured', ar: 'مُهيَّأ' },
  AVAILABLE:  { en: 'Available',  ar: 'متاح' },
  ERROR:      { en: 'Error',      ar: 'خطأ' },
  DISABLED:   { en: 'Disabled',   ar: 'مُعطَّل' },
};
