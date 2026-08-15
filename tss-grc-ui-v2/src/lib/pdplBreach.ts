// PDPL 72-hour breach response — clock, severity rubric, and the Article 25
// field inventory.
//
// Saudi PDPL (Personal Data Protection Law) Article 25 + Implementing
// Regulations require notification to SDAIA "without undue delay and within
// no later than 72 hours of becoming aware" of a personal-data breach,
// and notification to affected data subjects "without undue delay" where
// the breach is likely to result in high risk to their rights.
//
// ─────────────────────────────────────────────────────────────────────────────
// ⚠⚠ F2-209 — this module used to GENERATE the SDAIA notification, and that
// is what was wrong with it.
//
// `generateSdaiaNotification(breach, severity)` returned a complete Article 25
// packet: the reporting controller's identity, the categories of personal data
// affected, the likely consequences for data subjects, the containment measures
// taken and the remediation measures proposed. `PdplBreachConsole` rendered it
// and offered it as a download.
//
// Measured against `privacy.data_breaches` (25 columns, read from sys.columns,
// identical in dev and prod): there is NO COLUMN for any of it. Every
// substantive field was a literal in the bundle:
//
//   controller        'TSS Saudi Holding' + CR 1010299292 + DPO 'Mona Al-Bishi'
//                     — the same hard-coded CR number F2-199 found on the
//                       fabricated NCA ECC return. A tenant-specific fact
//                       appearing as a literal in a shipped bundle cannot be
//                       right for more than one tenant.
//   categoriesAffected  derived from the SEVERITY BAND, not from any record —
//                       so "Financial / transactional data" was asserted to the
//                       regulator because a row count crossed a threshold.
//   likelyConsequences  one of three canned paragraphs.
//   containmentMeasures 'Affected systems isolated from the network within the
//                       first hour of detection. Privileged access tokens
//                       revoked. Forensic image of impacted hosts captured for
//                       SDAIA inspection.'  — identical for every breach in
//                       every tenant.
//   proposedMeasures    likewise.
//
// 🔑 The last two are not metrics, they are STATEMENTS OF FACT about what the
// organisation did, addressed to a data-protection regulator, inside a 72-hour
// statutory window — and the platform's own documented workflow is that the DPO
// transcribes them into the SDAIA portal by hand. F2-199's harm argument one
// step further: a fabricated dashboard misleads whoever looks at it; a
// fabricated export leaves the platform; a fabricated STATEMENT OF CONDUCT
// invites the regulator to ask for a forensic image that may not exist.
//
// It was not rewireable — F2-207's diff-the-tokens test has a third answer
// here: not "the column refuses these tokens" and not "the server's machine is
// coarser", but "there is no column at all". F2-198 deleted `lastReviewedAt`
// for exactly this reason: reconstructing a field the schema does not hold is
// F2-196's defect.
//
// So the generator is GONE and {@link ARTICLE_25_FIELDS} replaces it: the same
// field list, each entry declaring the `PrivacyBreachDto` property it is read
// from — or `null`, meaning the platform records no source for it and the DPO
// must supply it in the portal. Deleting the section outright would have been
// worse than leaving it: an empty space where a submission draft used to be
// reads as "nothing further is required" (F2-197).
// ─────────────────────────────────────────────────────────────────────────────

import type { PrivacyBreachDto } from './api';

// ─── Stage budget ───────────────────────────────────────────────────────────

export const PDPL_BREACH_STAGES = [
  { code: 'DETECTED',                hoursBudget: 0,   labelEn: 'Detected',                  labelAr: 'الكشف' },
  { code: 'ASSESSED',                hoursBudget: 24,  labelEn: 'Severity & scope assessed', labelAr: 'تقييم الخطورة والنطاق' },
  { code: 'CONTAINED',               hoursBudget: 24,  labelEn: 'Contained',                 labelAr: 'احتواء' },
  { code: 'NOTIFIED_DPA',            hoursBudget: 24,  labelEn: 'SDAIA notified',            labelAr: 'إبلاغ سدايا' },
  { code: 'NOTIFIED_DATA_SUBJECTS',  hoursBudget: 144, labelEn: 'Data subjects notified',    labelAr: 'إبلاغ أصحاب البيانات' }, // 7 days from SDAIA notify
  { code: 'CLOSED',                  hoursBudget: 0,   labelEn: 'Closed',                    labelAr: 'إغلاق' },
] as const;

export type PdplStageCode = typeof PDPL_BREACH_STAGES[number]['code'];

/** Total cumulative budget to a given stage (in hours from detection). */
export function cumulativeBudgetTo(stage: PdplStageCode): number {
  let sum = 0;
  for (const s of PDPL_BREACH_STAGES) {
    sum += s.hoursBudget;
    if (s.code === stage) return sum;
  }
  return sum;
}

// PDPL Article 25 hard deadlines — referenced by the console UI.
export const SDAIA_DEADLINE_HOURS = 72;       // notify regulator
export const DATA_SUBJECTS_DEADLINE_HOURS = 168; // 7 days; "without undue delay"

// ─── The SDAIA notification state, as the SERVER defines it ─────────────────
//
// ⚠⚠ F2-209: the console used to answer "has SDAIA been notified?" by reading
// the breach LIFECYCLE status (`status === 'NOTIFIED'`). The platform owns a
// dedicated column for the question — `sdaia_notification_status`, with the
// state machine below declared in `PrivacyService` — and the two disagree on
// real rows: 4 of 17 live breaches in BOTH databases are `status = NOTIFIED`
// while `sdaia_notification_status = NOT_REPORTED`, 113 to 193 days past a
// 72-hour statutory window, all four flagged as requiring notification.
//
// Reading the lifecycle column excluded exactly those four from the console's
// "needs SDAIA" pool, so the one screen built to surface an undischarged
// Article 25 clock was the screen that hid them.
//
// F2-196's question ("how many things read this status, and do they agree?")
// with F2-205's follow-up ("and which of them is the server?"). The answer was
// 200 lines below in the same file, under a comment reading
// "authoritative, server-backed".

/** The server's vocabulary — mirrors `PrivacyService.SDAIA_STATES`. */
export const SDAIA_NOTIFICATION_STATES = [
  'NOT_REPORTED', 'IN_PREPARATION', 'SUBMITTED', 'ACKNOWLEDGED',
] as const;

export type SdaiaNotificationState = typeof SDAIA_NOTIFICATION_STATES[number];

/**
 * Has the Article 31 / Article 25 notification left the building?
 *
 * The one predicate both halves of the console consult, so the question cannot
 * be asked two ways in one component again (F2-178's shape). `NOT_REPORTED` and
 * anything unrecognised are "no": for a statutory clock, an unknown status must
 * be chased rather than excused (F2-196).
 */
export function isSdaiaReported(state: string | null | undefined): boolean {
  return state === 'IN_PREPARATION' || state === 'SUBMITTED' || state === 'ACKNOWLEDGED';
}

/**
 * Has the notification actually been POSTED — i.e. is the 72-hour clock
 * discharged? `IN_PREPARATION` is progress, not discharge; the server's own
 * javadoc puts the discharge at `sdaia_submitted_at` being set.
 */
export function isSdaiaSubmitted(state: string | null | undefined): boolean {
  return state === 'SUBMITTED' || state === 'ACKNOWLEDGED';
}

/**
 * When the controller became aware of the breach — the instant every PDPL clock
 * on this record runs from.
 *
 * ⚠⚠ F2-211: both surfaces used to open with
 * `b.reportedAt ?? b.occurredAt ?? b.discoveredAt`. The first two operands are
 * fields `privacy.data_breaches` has no column for, so they are ALWAYS undefined
 * and the chain always resolved to the third — a correct outcome reached by two
 * dead operands, which reads as though the platform prefers a "reported" stamp
 * and merely falls back. It does not have one. The register table opposite made
 * the same assumption without the fallback (`if (!r.occurredAt) return '—'`) and
 * its Article 20 countdown therefore never rendered at all.
 *
 * One accessor, so the question cannot be asked two ways again (F2-178's shape),
 * and so that adding a real `reported_at` column later is one edit here rather
 * than a hunt for `??` chains.
 */
export function breachAwarenessAt(breach: Pick<PrivacyBreachDto, 'discoveredAt'>): string {
  return breach.discoveredAt ?? '';
}

/**
 * Bilingual labels for `privacy.data_breaches.breach_type`.
 *
 * ⚠⚠ F2-211: this map used to live on the detail page and carried SEVEN tokens, of
 * which exactly ONE (`UNAUTHORISED_ACCESS`) appears in any live row. The other six —
 * `CONFIDENTIALITY`, `INTEGRITY`, `AVAILABILITY`, `LOSS`, `THEFT`,
 * `UNAUTHORISED_DISCLOSURE` — are the vocabulary the DEMO CORPUS used, so the map had
 * been written against the fabrication rather than against the database. Eleven of the
 * twelve stored tokens therefore fell through to the raw-token fallback, and the
 * register had no map at all, printing `UNAUTHORIZED_ACCESS` and `ACCIDENTAL_DISCLOSURE`
 * into an Arabic-primary UI on all 17 rows (house rule 7).
 *
 * The twelve stored values were read from the column, not guessed. The seven legacy
 * tokens are KEPT: other writers may still produce them and dropping a label can only
 * make a row render worse.
 *
 * ⚠ `UNAUTHORISED_ACCESS` and `UNAUTHORIZED_ACCESS` are BOTH stored — one concept, two
 * spellings, on a column with no CHECK. They are labelled identically here because
 * labelling is display; collapsing them in the database rewrites live rows in two
 * databases and is an owner decision (F2-183/F2-196's rule), so it is filed, not done.
 */
export const BREACH_TYPE_LABEL: Record<string, { en: string; ar: string }> = {
  // measured in privacy.data_breaches (12 distinct values, 17 live rows)
  ACCIDENTAL_DISCLOSURE:   { en: 'Accidental disclosure',   ar: 'إفشاء غير مقصود' },
  LOSS_OF_DEVICE:          { en: 'Loss of device',          ar: 'فقد جهاز' },
  UNAUTHORISED_ACCESS:     { en: 'Unauthorised access',     ar: 'وصول غير مصرّح به' },
  UNAUTHORIZED_ACCESS:     { en: 'Unauthorised access',     ar: 'وصول غير مصرّح به' },
  VENDOR_BREACH:           { en: 'Vendor breach',           ar: 'اختراق لدى مورّد' },
  WEB_VULN_EXPLOIT:        { en: 'Web vulnerability',       ar: 'استغلال ثغرة ويب' },
  MISCONFIGURATION:        { en: 'Misconfiguration',        ar: 'خطأ في الإعدادات' },
  PHISHING:                { en: 'Phishing',                ar: 'تصيّد إلكتروني' },
  PHYSICAL_TAMPERING:      { en: 'Physical tampering',      ar: 'عبث مادي' },
  CYBERATTACK:             { en: 'Cyberattack',             ar: 'هجوم سيبراني' },
  DATA_EXPOSURE_LOWER_ENV: { en: 'Exposure in a lower environment', ar: 'انكشاف في بيئة غير إنتاجية' },
  INSIDER_THREAT:          { en: 'Insider threat',          ar: 'تهديد داخلي' },
  // legacy tokens no live row carries — kept so an older writer still renders
  CONFIDENTIALITY:         { en: 'Confidentiality',         ar: 'سرية البيانات' },
  INTEGRITY:               { en: 'Integrity',               ar: 'سلامة البيانات' },
  AVAILABILITY:            { en: 'Availability',            ar: 'توافر البيانات' },
  LOSS:                    { en: 'Loss',                    ar: 'فقد البيانات' },
  THEFT:                   { en: 'Theft',                   ar: 'سرقة البيانات' },
  UNAUTHORISED_DISCLOSURE: { en: 'Unauthorised disclosure', ar: 'إفشاء غير مصرّح به' },
};

/** Label a breach type, falling back to the de-underscored token. */
export function breachTypeLabel(code: string | null | undefined, lang: 'en' | 'ar'): string {
  if (!code) return '—';
  const m = BREACH_TYPE_LABEL[code];
  return m ? m[lang] : code.replace(/_/g, ' ');
}

/**
 * The severity inputs this platform can actually supply for a breach.
 *
 * `privacy.data_breaches` has no `severity` column and none of the qualitative
 * flags {@link PdplSeverityInputs} asks for, so the affected-subject count is the
 * only real input — which is what makes every band {@link severityIsProvisional}.
 * Both the console and the register read this, so the two cannot disagree about
 * a band that drives the Article 26 data-subject notification determination.
 */
export function breachSeverityInputs(
  breach: Pick<PrivacyBreachDto, 'dataSubjectsAffectedCount'>,
): PdplSeverityInputs {
  return { affectedRecords: breach.dataSubjectsAffectedCount ?? 0 };
}

// ─── Severity rubric ────────────────────────────────────────────────────────
//
// PDPL doesn't enumerate severity bands explicitly; it asks the controller
// to assess "the likelihood and severity of risk to data subjects' rights
// and freedoms". This rubric encodes the practical SDAIA test that listed
// entities follow:
//   CRITICAL — special-category data OR ≥ 100k subjects OR identity data
//              suitable for impersonation
//   HIGH     — financial / health / location data OR 10k–100k subjects
//   MEDIUM   — contact data only OR 1k–10k subjects
//   LOW      — minimal data, < 1k subjects, encrypted at rest
//
// ⚠ F2-209: the rubric is sound and the CALL SITE was not. The console passed
// `financial: breachType === 'CONFIDENTIALITY' && records > 5_000` — an
// inference no column supports, asserting that a large confidentiality breach
// involved financial data. `privacy.data_breaches` holds no special-category
// flag, no data-category list and no `severity` column, so the only input the
// platform actually has is the affected-record count. The band is therefore
// PROVISIONAL and the console says so; see {@link severityIsProvisional}.

export type PdplSeverity = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export interface PdplSeverityInputs {
  affectedRecords?: number;
  /** Boolean flags for special-category, financial, health, or
   *  identification-quality data. */
  specialCategory?: boolean;
  financial?: boolean;
  health?: boolean;
  identifying?: boolean;
  /** Whether the data was encrypted at rest. */
  encryptedAtRest?: boolean;
}

export function classifySeverity(b: PdplSeverityInputs): PdplSeverity {
  const n = b.affectedRecords ?? 0;
  if (b.specialCategory || b.identifying || n >= 100_000) return 'CRITICAL';
  if (b.financial || b.health || n >= 10_000) return 'HIGH';
  if (n >= 1_000 && !b.encryptedAtRest) return 'MEDIUM';
  return 'LOW';
}

/**
 * True when the band was derived from the record count alone because the
 * platform records none of the qualitative inputs the rubric asks for. Always
 * true today — every flag in {@link PdplSeverityInputs} except
 * `affectedRecords` maps to a column `privacy.data_breaches` does not have.
 * It is a function rather than a constant so that adding those columns turns
 * one disclosure off in one place instead of leaving a stale caveat behind.
 */
export function severityIsProvisional(inputs: PdplSeverityInputs): boolean {
  return inputs.specialCategory === undefined
      && inputs.financial === undefined
      && inputs.health === undefined
      && inputs.identifying === undefined
      && inputs.encryptedAtRest === undefined;
}

/** Whether this severity requires data-subject notification (PDPL Art. 26). */
export function requiresDataSubjectNotification(severity: PdplSeverity): boolean {
  return severity === 'HIGH' || severity === 'CRITICAL';
}

// ─── Article 25 field inventory ─────────────────────────────────────────────
//
// What SDAIA's notification asks for, and — per field — whether this platform
// holds it. This replaces the generator: it tells the DPO the same list without
// answering on the organisation's behalf.
//
// `source` is a property of `PrivacyBreachDto` or `null`. `null` is a MEASURED
// FACT about `privacy.data_breaches`, not a TODO: the column does not exist in
// dev or prod, so the value can only come from the DPO. The backend half of the
// gate (`Article25FieldBackingContractTest`) resolves every non-null `source`
// against the real `DataBreach` entity by reflection and fails the build if one
// names a field the server does not have — so a future edit cannot quietly
// claim provenance this schema does not provide.

export interface Article25Field {
  /** Stable key; the console's translation keys are `pdplArt25<Key>`. */
  key: string;
  /** Which sub-paragraph of the Implementing Regulations this discharges. */
  clause: 'a' | 'b' | 'c' | 'd' | 'contact' | 'ref';
  /**
   * The `PrivacyBreachDto` property this field is read from, or `null` when
   * the platform records no source for it.
   */
  source: keyof PrivacyBreachDto | null;
  /**
   * How to present the value. `datetime` matters: found in the browser after
   * every gate assertion was green, the awareness timestamp rendered as the raw
   * `2026-08-14T12:21:45` while every other date on the same screen was
   * localised — on an Arabic-primary surface (house rule 7), in the field that
   * starts a statutory 72-hour clock. Declared rather than inferred from the
   * property name so a new date field cannot be added without deciding.
   */
  format?: 'datetime';
}

export const ARTICLE_25_FIELDS: readonly Article25Field[] = [
  // (a) nature of the breach — the platform holds the type and the English
  //     narrative. There is no `description_ar` column, so the Arabic half of
  //     an Arabic-primary submission cannot be produced from the record
  //     (filed: house rule 7 on a statutory register).
  { key: 'Nature',        clause: 'a',       source: 'breachType' },
  { key: 'Narrative',     clause: 'a',       source: 'descriptionEn' },
  // (b) categories and approximate counts — the count is real; the CATEGORIES
  //     of personal data involved are not recorded anywhere.
  { key: 'Subjects',      clause: 'b',       source: 'dataSubjectsAffectedCount' },
  { key: 'Categories',    clause: 'b',       source: null },
  // (c) likely consequences for the data subjects — no column.
  { key: 'Consequences',  clause: 'c',       source: null },
  // (d) measures taken and measures proposed — no column. These are the two
  //     the retired generator invented as statements of fact.
  { key: 'Containment',   clause: 'd',       source: null },
  { key: 'Proposed',      clause: 'd',       source: null },
  // Controller identity and DPO contact. The breach record carries neither;
  //     the tenant's own identity is not on this table, and inventing it is
  //     how the retired packet came to name a software product, with one CR
  //     number, as every tenant's filing entity.
  { key: 'Controller',    clause: 'contact', source: null },
  { key: 'Dpo',           clause: 'contact', source: null },
  // Awareness timestamp — starts the 72-hour clock. Real.
  { key: 'AwareAt',       clause: 'ref',     source: 'discoveredAt', format: 'datetime' },
  // The portal reference SDAIA returns, and when the submission was posted.
  //     Both real, both written by the record-submission endpoint.
  { key: 'PortalRef',     clause: 'ref',     source: 'sdaiaSubmissionRef' },
  { key: 'SubmittedAt',   clause: 'ref',     source: 'sdaiaSubmittedAt', format: 'datetime' },
];

/** How many Article 25 fields the platform can fill from the record. */
export function article25Held(): number {
  return ARTICLE_25_FIELDS.filter(f => f.source !== null).length;
}

/** How many the DPO must supply in the portal because no column holds them. */
export function article25NotHeld(): number {
  return ARTICLE_25_FIELDS.filter(f => f.source === null).length;
}

/**
 * Read a field's value off a breach row. Returns `undefined` for a field with
 * no source AND for a sourced field the row leaves empty — the console
 * distinguishes the two from `source`, never from the value, because "the
 * platform cannot record this" and "nobody has filled it in" are different
 * facts and only one of them is the DPO's to fix.
 */
export function article25Value(
  f: Article25Field,
  breach: PrivacyBreachDto,
  lang: 'en' | 'ar' = 'en',
): string | undefined {
  if (f.source === null) return undefined;
  const v = breach[f.source];
  if (v === null || v === undefined || v === '') return undefined;
  if (f.format === 'datetime') {
    const d = new Date(String(v));
    if (Number.isNaN(d.getTime())) return String(v);
    return d.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB',
      { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
  }
  return String(v);
}

// ─── Hour-precision countdown ──────────────────────────────────────────────

export interface BreachClock {
  hoursElapsed: number;
  hoursRemaining: number;        // negative when over the 72h budget
  hoursToDataSubjects: number;   // negative when over the 7-day budget
  pctConsumed: number;           // 0–999 (capped above 999%)
  tier: 'GREEN' | 'AMBER' | 'RED' | 'BREACHED';
  /** Expected next stage by elapsed time. */
  expectedStage: PdplStageCode;
}

export function computeBreachClock(
  awareAt: string,
  now: Date = new Date(),
): BreachClock {
  const aware = new Date(awareAt);
  const elapsedMs = now.getTime() - aware.getTime();
  const hoursElapsed = elapsedMs / 3_600_000;
  const hoursRemaining = SDAIA_DEADLINE_HOURS - hoursElapsed;
  const hoursToDataSubjects = DATA_SUBJECTS_DEADLINE_HOURS - hoursElapsed;
  const pctConsumed = Math.min(999, Math.round((hoursElapsed / SDAIA_DEADLINE_HOURS) * 100));

  let tier: BreachClock['tier'];
  if (hoursRemaining < 0) tier = 'BREACHED';
  else if (hoursRemaining <= 12) tier = 'RED';
  else if (hoursRemaining <= 36) tier = 'AMBER';
  else tier = 'GREEN';

  // Determine the expected stage — purely informational, doesn't enforce.
  let expectedStage: PdplStageCode = 'DETECTED';
  let cumulative = 0;
  for (const s of PDPL_BREACH_STAGES) {
    cumulative += s.hoursBudget;
    if (hoursElapsed <= cumulative) { expectedStage = s.code; break; }
    expectedStage = s.code;
  }

  return {
    hoursElapsed: Math.round(hoursElapsed * 10) / 10,
    hoursRemaining: Math.round(hoursRemaining * 10) / 10,
    hoursToDataSubjects: Math.round(hoursToDataSubjects * 10) / 10,
    pctConsumed,
    tier,
    expectedStage,
  };
}

/**
 * The PDPL Article 26 data-subject notification deadline: 7 days from the
 * awareness timestamp that starts the Article 25 clock. Derived from a real
 * column (`discovered_at`), unlike the retired packet's version, which was one
 * field of an otherwise invented document.
 */
export function dataSubjectsDeadline(awareAt: string): string | undefined {
  if (!awareAt) return undefined;
  const d = new Date(awareAt);
  if (Number.isNaN(d.getTime())) return undefined;
  d.setTime(d.getTime() + DATA_SUBJECTS_DEADLINE_HOURS * 3_600_000);
  return d.toISOString();
}

// ─── Format helpers ─────────────────────────────────────────────────────────

export function formatHoursLabel(hours: number, lang: 'en' | 'ar'): string {
  const abs = Math.abs(hours);
  const days = Math.floor(abs / 24);
  const remH = Math.round(abs - days * 24);
  if (lang === 'ar') {
    if (days === 0) return `${remH} ساعة`;
    return `${days}ي ${remH}س`;
  }
  if (days === 0) return `${remH}h`;
  return `${days}d ${remH}h`;
}
