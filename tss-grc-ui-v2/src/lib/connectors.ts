// Connector-driven intake registry — DD-62, rewritten by F2-193.
//
// Intake has three channels: web forms (DD-55..59), API pushes from the live
// system (KRI breaches → preventive CAPAs in DD-43, effectiveness re-tests in
// DD-44), and this — automated connectors pulling from external monitoring,
// scanner and regulator-watch sources.
//
// ⚠⚠ WHAT F2-193 FOUND, because it decides how this file is allowed to be written.
// Until 2026-08-12 each entry carried a hand-set `status`, a `cadence`, a
// `lastRunAt` timestamp and an `intake30d` count — "records auto-ingested in the
// last 30 days". Eight of the twelve entries read ACTIVE. Measured against the
// backend: five of those eight had no implementation of any kind (the three
// regulator feeds, the vendor SOC 2 expiry watcher, and the CAPA effectiveness
// re-test, whose description named a VERIFICATION_FAILED status that appears
// nowhere in the Java sources). One was push-only, not the 5-minute poll it
// claimed. The two with a real @Scheduled loop had the wrong cadence — and both
// are off by configuration in every profile in the repo. The connector with the
// most aggressive real trigger, CrowdStrike at 60 seconds, was not in the list
// at all. The `intake30d` values summed to 292 in a KPI that linked to a queue
// holding zero rows in dev and prod.
//
// 🔑 The fabrication did not live in the data, it lived in the TYPE: a field
// called `lastRunAt` on a catalogue no server feeds can only ever be invented.
// So the fix is structural. There is no `status` and no telemetry here. Each
// entry declares the TRIGGER that actually runs it and NAMES the backend symbol
// that provides it, and `ConnectorBackingContractTest` (backend, hermetic) reads
// this file and fails the build if that symbol is not a real @Scheduled method
// or a real mapped route. A connector cannot claim to be automated here without
// a backend that automates it.
//
// Live state — enabled/healthy/last-poll — belongs to the four real adapters on
// /api/v1/integrations and is read from the server by `integrationsApi.ts`. This
// file is the catalogue: what exists, what runs it, what gates it.
//
// Modelled on:
//   industry-standard GRC tooling Continuous Monitoring connectors
//   continuous-monitoring tooling: AWS / Okta / GitHub / Jira evidence + finding feeds
//   the industry pattern GRC Threat-Intel TAXII feed
//   industry-standard tooling Regulatory Research feed

import type { IntakeKind } from './intake';

/**
 * How a connector actually runs today — measured against the backend, not intended.
 *
 * - `SCHEDULED_POLL`     — a `@Scheduled` loop in the backend pulls on an interval.
 * - `INBOUND_WEBHOOK`    — the vendor pushes; the platform exposes a mapped route.
 * - `OPERATOR_INITIATED` — the mechanism exists but a person must press something.
 *                          A route is not a trigger (F2-193): the endpoint that
 *                          "pushes a known security event" is documented, by the
 *                          handler itself, as a manual push.
 * - `NOT_IMPLEMENTED`    — catalogued as a capability; no backend exists yet.
 */
export type ConnectorTrigger =
  | 'SCHEDULED_POLL'
  | 'INBOUND_WEBHOOK'
  | 'OPERATOR_INITIATED'
  | 'NOT_IMPLEMENTED';

/**
 * Real intervals only. Every member must be the measured cadence of a live
 * `@Scheduled` loop — add a member in the commit that adds the loop, not before.
 */
export type ConnectorCadence = 'EVERY_MINUTE' | 'EVERY_15MIN' | 'DAILY_0600';

export interface ConnectorDefinition {
  id: string;
  /** Source vendor / product. */
  source: string;
  nameEn: string;
  nameAr: string;
  /**
   * What this connector does. For `NOT_IMPLEMENTED` entries this is written in
   * the planned voice — a present-tense sentence about a connector that does not
   * exist is the defect F2-193 closed, and the badge alone does not undo it.
   */
  descriptionEn: string;
  descriptionAr: string;
  /** What kind of intake record this connector creates. */
  intakeKind?: IntakeKind;
  /** Target register (when not via intake). */
  targetEntity?: 'RISK' | 'INCIDENT' | 'VULNERABILITY' | 'EVIDENCE' | 'REG_CHANGE' | 'CAPA' | 'FINDING';
  /** Measured — see {@link ConnectorTrigger}. */
  trigger: ConnectorTrigger;
  /**
   * The backend symbol that provides the trigger: `Class#method` for a scheduled
   * poll, `VERB /path` for a webhook or an operator-initiated route. Required for
   * everything except `NOT_IMPLEMENTED`, and checked against the Java sources by
   * `ConnectorBackingContractTest`.
   */
  backing?: string;
  /**
   * The configuration property that must be true for the trigger to fire. Both
   * scanner pollers and the KRI cycle ship OFF, so an operator reading "automated"
   * needs to know what to set — omitting this is how "the poller exists" became
   * "the poller runs".
   */
  gate?: string;
  /** Present only for `SCHEDULED_POLL`, and only the loop's real interval. */
  cadence?: ConnectorCadence;
  /** Standards anchor (e.g. NCA ECC 2.6, ISO 27001 §A.8.8). */
  reference: string;
}

export const CONNECTOR_REGISTRY: ConnectorDefinition[] = [
  // ── Vulnerability scanners → cyber.vulnerabilities ──
  {
    id: 'cn-tenable',
    source: 'Tenable.io',
    nameEn: 'Tenable Vulnerability Scanner',
    nameAr: 'فاحص الثغرات Tenable',
    descriptionEn: 'Polls CVE-tagged findings and creates a cyber vulnerability record per host/CVE pair, de-duplicated on (asset, plugin).',
    descriptionAr: 'يستعلم عن ثغرات CVE ويُنشئ سجل ثغرة سيبرانية لكل ثنائية مضيف/CVE، مع استبعاد المكرَّر على (الأصل، الفاحص).',
    targetEntity: 'VULNERABILITY',
    trigger: 'SCHEDULED_POLL',
    backing: 'TenableInboundService#scheduledPoll',
    gate: 'grc.integrations.tenable.enabled + .polling-enabled',
    cadence: 'EVERY_15MIN',
    reference: 'NCA ECC 2.6, ISO 27001 §A.8.8',
  },
  {
    id: 'cn-qualys',
    source: 'Qualys VMDR',
    nameEn: 'Qualys VMDR',
    nameAr: 'Qualys VMDR',
    descriptionEn: 'Planned: continuous scanning + asset discovery feeding the vulnerability and asset registers.',
    descriptionAr: 'مخطَّط: فحص مستمر واكتشاف أصول لتغذية سجلّي الثغرات والأصول.',
    targetEntity: 'VULNERABILITY',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'NCA ECC 2.6',
  },
  // ── EDR → socops.security_event ──
  {
    id: 'cn-crowdstrike',
    source: 'CrowdStrike Falcon',
    nameEn: 'CrowdStrike Falcon EDR',
    nameAr: 'CrowdStrike Falcon EDR',
    // Absent from this registry until F2-193, while five connectors with no code
    // at all were listed as ACTIVE. It has the shortest real interval of any.
    descriptionEn: 'Polls Falcon detections into the SOC security-event queue, de-duplicated on the vendor detection id.',
    descriptionAr: 'يستعلم عن اكتشافات Falcon إلى قائمة أحداث الأمن في مركز العمليات، مع استبعاد المكرَّر بمعرّف الاكتشاف.',
    targetEntity: 'INCIDENT',
    trigger: 'SCHEDULED_POLL',
    backing: 'CrowdStrikeInboundService#scheduledPoll',
    gate: 'grc.integrations.crowdstrike.enabled + .polling-enabled',
    cadence: 'EVERY_MINUTE',
    reference: 'NCA ECC 2.5, NIST 800-61',
  },
  // ── SIEM → cyber.incidents ──
  {
    id: 'cn-splunk',
    source: 'Splunk Enterprise Security',
    nameEn: 'Splunk SIEM',
    nameAr: 'Splunk SIEM',
    // The old entry claimed a 5-minute poll. There is no Splunk poller: a saved
    // search in Splunk posts to the webhook below. The egress route on the same
    // module (POST /security-event/{id}) is documented "Manually push" by its own
    // handler, so it is not a second trigger.
    descriptionEn: 'Receives notable events posted by a Splunk saved search and drafts a cyber incident at HIGH/CRITICAL severity.',
    descriptionAr: 'يستقبل الأحداث المهمّة المُرسَلة من بحث محفوظ في Splunk ويُنشئ مسودة حادثة سيبرانية بشدّة عالية/حرجة.',
    intakeKind: 'SECURITY_INCIDENT', targetEntity: 'INCIDENT',
    trigger: 'INBOUND_WEBHOOK',
    backing: 'POST /api/v1/integrations/splunk/webhook',
    gate: 'grc.integrations.splunk.enabled + .webhook-shared-secret',
    reference: 'NCA ECC 6.1, NIST 800-61',
  },
  // ── Regulator-watch → compliance.regulatory_changes ──
  // All four shipped as ACTIVE/HOURLY with a last-run stamp and an intake count.
  // There is no RSS client, no poller and no regulator-watch service anywhere in
  // the backend; regulatory changes are entered by hand in the regchange module.
  {
    id: 'cn-sdaia-feed',
    source: 'SDAIA RSS',
    nameEn: 'SDAIA Regulator Watch',
    nameAr: 'مراقب جهة سدايا',
    descriptionEn: 'Planned: poll SDAIA publications so a new circular opens a regulatory-change record and a DPO impact-assessment task.',
    descriptionAr: 'مخطَّط: استعلام منشورات سدايا ليفتح التعميم الجديد سجل تغيير تنظيمي ومهمّة تقييم أثر للـDPO.',
    targetEntity: 'REG_CHANGE',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'PDPL Implementing Regulations',
  },
  {
    id: 'cn-nca-feed',
    source: 'NCA RSS',
    nameEn: 'NCA Regulator Watch',
    nameAr: 'مراقب جهة الأمن السيبراني',
    descriptionEn: 'Planned: poll NCA circulars and ECC updates so a new directive opens a regulatory-change record and a CISO assignment.',
    descriptionAr: 'مخطَّط: استعلام تعاميم الجهة الوطنية وتحديثات ECC ليفتح التوجيه الجديد سجل تغيير تنظيمي وإسناداً للـCISO.',
    targetEntity: 'REG_CHANGE',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'NCA Circulars 2026',
  },
  {
    id: 'cn-sama-feed',
    source: 'SAMA Circulars',
    nameEn: 'SAMA Circular Feed',
    nameAr: 'مراقب جهة ساما',
    descriptionEn: 'Planned: poll the SAMA RH portal for new circulars (CSF, AML, Outsourcing, Open Banking).',
    descriptionAr: 'مخطَّط: استعلام بوابة ساما عن التعاميم الجديدة (CSF، مكافحة غسل الأموال، الإسناد، المصرفية المفتوحة).',
    targetEntity: 'REG_CHANGE',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'SAMA Circulars + CSF',
  },
  {
    id: 'cn-zatca-feed',
    source: 'ZATCA Decisions',
    nameEn: 'ZATCA Tax Watch',
    nameAr: 'مراقب جهة الزكاة والضريبة',
    descriptionEn: 'Planned: poll ZATCA for e-invoicing waves, new tax decisions and FATOORA updates.',
    descriptionAr: 'مخطَّط: استعلام الزكاة والضريبة عن موجات الفوترة الإلكترونية والقرارات الضريبية وتحديثات FATOORA.',
    targetEntity: 'REG_CHANGE',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'ZATCA E-Invoicing',
  },
  // ── Vendor SOC 2 expiry watcher → TPRM ──
  // Shipped ACTIVE/WEEKLY with three records ingested in 30 days. No attestation
  // expiry watcher exists: the tprm module has no scheduler of any kind.
  {
    id: 'cn-vendor-attestation',
    source: 'Internal TPRM scheduler',
    nameEn: 'Vendor SOC 2 / ISO 27001 expiry watcher',
    nameAr: 'مراقب انتهاء SOC 2 / ISO 27001 للموردين',
    descriptionEn: 'Planned: when a vendor attestation lapses, open a periodic-review intake for TPRM.',
    descriptionAr: 'مخطَّط: عند انتهاء صلاحية إقرار مورد، يُفتح استلام مراجعة دورية لـTPRM.',
    intakeKind: 'VENDOR_ONBOARDING',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'SAMA Outsourcing §5.4',
  },
  // ── Threat-intel TAXII ──
  {
    id: 'cn-taxii',
    source: 'STIX/TAXII feed',
    nameEn: 'Threat Intelligence (STIX/TAXII)',
    nameAr: 'الاستخبارات السيبرانية (STIX/TAXII)',
    // The threat-intel register itself is live (IoCs are entered and actioned
    // through ThreatIntelController); what does not exist is the TAXII pull.
    descriptionEn: 'Planned: pull IoC feeds (Saudi-CERT, MISP) into the threat-intel register for downstream SIEM tagging.',
    descriptionAr: 'مخطَّط: سحب موجزات مؤشرات الاختراق (Saudi-CERT، MISP) إلى سجل الاستخبارات لوسم SIEM.',
    targetEntity: 'INCIDENT',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'NIST CSF ID.RA-2',
  },
  // ── Effectiveness check ──
  // Shipped ACTIVE/DAILY and described as auto-flagging VERIFICATION_FAILED — a
  // status token that appears in no Java source, and there is no CAPA scheduler.
  // The re-test is opened and closed by hand from the CAPA register.
  {
    id: 'cn-effectiveness',
    source: 'Internal CAPA scheduler',
    nameEn: 'CAPA effectiveness re-test',
    nameAr: 'إعادة اختبار فاعلية الإجراءات التصحيحية',
    descriptionEn: 'Planned: fire 90 days after closure and flag a corrective action whose regression re-test fails.',
    descriptionAr: 'مخطَّط: يُفعَّل بعد 90 يوماً من الإغلاق ويضع علامة على الإجراء التصحيحي الذي يفشل اختبار تراجعه.',
    targetEntity: 'CAPA',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'ISO 9001 §10.2.2, FDA 21 CFR §820.100',
  },
  // ── KRI breach (DD-43) ──
  {
    id: 'cn-kri-monitor',
    source: 'Internal KRI scheduler',
    nameEn: 'KRI threshold monitor',
    nameAr: 'مراقب عتبات المؤشرات',
    // Real, and the cadence claim was wrong in the flattering direction: the cron
    // is 06:00 daily, not hourly. It is also gated by a property set in no profile.
    descriptionEn: 'Evaluates KRI readings against their thresholds; an amber/red breach raises a preventive-CAPA suggestion.',
    descriptionAr: 'يقيّم قراءات المؤشرات مقابل عتباتها؛ التجاوز الكهرماني/الأحمر يرفع اقتراح إجراء وقائي.',
    targetEntity: 'CAPA',
    trigger: 'SCHEDULED_POLL',
    backing: 'KriSchedulerService#tick',
    gate: 'grc.kri.scheduler-enabled',
    cadence: 'DAILY_0600',
    reference: 'COSO ERM Component 5, ISO 31000 §6.5',
  },
  // ── Evidence connector ──
  {
    id: 'cn-evidence-aws',
    source: 'AWS Config + IAM',
    nameEn: 'AWS Evidence Connector',
    nameAr: 'موصِّل أدلّة AWS',
    descriptionEn: 'Planned: pull IAM policies, S3 bucket configs, RDS encryption and security-group rules to feed evidence.',
    descriptionAr: 'مخطَّط: سحب سياسات IAM وإعدادات S3 وتشفير RDS وقواعد المجموعات الأمنية لتغذية الأدلّة.',
    targetEntity: 'EVIDENCE',
    trigger: 'NOT_IMPLEMENTED',
    reference: 'SOC 2 CC-6.1, ISO 27001 §A.8.32',
  },
];

export const CADENCE_LABEL: Record<ConnectorCadence, { en: string; ar: string }> = {
  EVERY_MINUTE: { en: 'Every minute',  ar: 'كل دقيقة' },
  EVERY_15MIN:  { en: 'Every 15 min',  ar: 'كل 15 دقيقة' },
  DAILY_0600:   { en: 'Daily 06:00',   ar: 'يومياً 06:00' },
};

export const CONNECTOR_TRIGGER_TONE: Record<ConnectorTrigger, 'success' | 'info' | 'warning' | 'neutral'> = {
  SCHEDULED_POLL:     'success',
  INBOUND_WEBHOOK:    'success',
  OPERATOR_INITIATED: 'warning',
  NOT_IMPLEMENTED:    'neutral',
};

export const CONNECTOR_TRIGGER_LABEL: Record<ConnectorTrigger, { en: string; ar: string }> = {
  SCHEDULED_POLL:     { en: 'Scheduled poll',   ar: 'استعلام مُجدول' },
  INBOUND_WEBHOOK:    { en: 'Inbound webhook',  ar: 'ويب هوك وارد' },
  OPERATOR_INITIATED: { en: 'Manual only',      ar: 'يدوي فقط' },
  NOT_IMPLEMENTED:    { en: 'Not implemented',  ar: 'غير مُنفَّذ' },
};

/** A connector the platform runs by itself — a poll loop or a webhook route. */
export function isAutomated(c: ConnectorDefinition): boolean {
  return c.trigger === 'SCHEDULED_POLL' || c.trigger === 'INBOUND_WEBHOOK';
}
