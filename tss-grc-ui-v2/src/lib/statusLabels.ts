/**
 * Bilingual status-label helpers.
 *
 * Replaces the anti-pattern where status enums (OPEN, CRITICAL,
 * IN_PROGRESS, …) were rendered via `.charAt(0).toUpperCase() + …`
 * — that pattern showed Arabic users English badges everywhere.
 *
 * Lookup is intentionally case-insensitive on the input so callers
 * can pass either "OPEN" or "Open" — the table renders whatever the
 * backend ships.
 *
 * Use:
 *   const { lang } = useI18n();
 *   <Badge>{statusLabel('OPEN', lang)}</Badge>
 *
 * Severity / priority / strategy / decision enums get their own
 * helpers for the same reason. All four tables are exhaustive for
 * the enums the platform actually uses today.
 */
/** Accepts any string and narrows to 'ar' / 'en' internally so the
 *  helper composes with `useI18n().lang` regardless of how widely
 *  the i18n module types that field. Defaults to 'en' for anything
 *  other than the literal 'ar'. */
type Lang = string;

/** Generic status enum used across risk, incident, capa, issue, etc. */
const STATUS_LABELS: Record<string, { en: string; ar: string }> = {
  // ── Generic / risk / incident workflow ──────────────────────────
  OPEN:                     { en: 'Open',             ar: 'مفتوحة' },
  IN_PROGRESS:              { en: 'In progress',      ar: 'قيد التنفيذ' },
  INVESTIGATING:            { en: 'Investigating',    ar: 'قيد التحقيق' },
  BLOCKED:                  { en: 'Blocked',          ar: 'معلَّقة' },
  RESOLVED:                 { en: 'Resolved',         ar: 'مُعالجة' },
  CLOSED:                   { en: 'Closed',           ar: 'مُغلقة' },
  CANCELLED:                { en: 'Cancelled',        ar: 'مُلغاة' },
  // ── Approval / treatment / workflow review ──────────────────────
  DRAFT:                    { en: 'Draft',            ar: 'مسودة' },
  SUBMITTED:                { en: 'Submitted',        ar: 'مُرسلة' },
  PENDING:                  { en: 'Pending',          ar: 'بالانتظار' },
  UNDER_REVIEW:             { en: 'Under review',     ar: 'قيد المراجعة' },
  PENDING_REVIEW:           { en: 'Pending review',   ar: 'بانتظار المراجعة' },
  APPROVED:                 { en: 'Approved',         ar: 'مُعتمدة' },
  REJECTED:                 { en: 'Rejected',         ar: 'مرفوضة' },
  EXPIRED:                  { en: 'Expired',          ar: 'منتهية' },
  ARCHIVED:                 { en: 'Archived',         ar: 'مؤرشفة' },
  PUBLISHED:                { en: 'Published',        ar: 'منشورة' },
  COMPLETED:                { en: 'Completed',        ar: 'مكتملة' },
  DEPRIORITISED:            { en: 'Deprioritised',    ar: 'مُؤجلة' },
  // ── Vulnerability state machine ─────────────────────────────────
  NEW:                      { en: 'New',              ar: 'جديدة' },
  TRIAGED:                  { en: 'Triaged',          ar: 'مُصنَّفة' },
  OWNER_ASSIGNED:           { en: 'Owner assigned',   ar: 'مُسند للمالك' },
  IN_REMEDIATION:           { en: 'In remediation',   ar: 'قيد المعالجة' },
  AWAITING_VERIFICATION:    { en: 'Awaiting verify',  ar: 'بانتظار التحقق' },
  RISK_ACCEPTED:            { en: 'Risk accepted',    ar: 'مخاطرة مقبولة' },
  FALSE_POSITIVE:           { en: 'False positive',   ar: 'إنذار كاذب' },
  // ── DSR / privacy ───────────────────────────────────────────────
  INTAKE:                   { en: 'Intake',           ar: 'استلام' },
  VERIFYING_IDENTITY:       { en: 'Verifying ID',     ar: 'التحقق من الهوية' },
  COLLECTING:               { en: 'Collecting',       ar: 'جمع البيانات' },
  REDACTING:                { en: 'Redacting',        ar: 'إخفاء البيانات' },
  DELIVERED:                { en: 'Delivered',        ar: 'مُسلَّمة' },
  EXTENDED:                 { en: 'Extended',         ar: 'مُمددة' },
  // ── Consent ─────────────────────────────────────────────────────
  ACTIVE:                   { en: 'Active',           ar: 'نشطة' },
  WITHDRAWN:                { en: 'Withdrawn',        ar: 'مسحوبة' },
  SUPERSEDED:               { en: 'Superseded',       ar: 'مستبدلة' },
  // ── Regulatory change ───────────────────────────────────────────
  ASSESSED:                 { en: 'Assessed',         ar: 'مُقيَّمة' },
  IMPACT_ANALYSED:          { en: 'Impact analysed',  ar: 'تم تحليل الأثر' },
  IN_IMPLEMENTATION:        { en: 'Implementing',     ar: 'قيد التطبيق' },
  IMPLEMENTED:              { en: 'Implemented',      ar: 'مُطبَّقة' },
  // ── Change management ──────────────────────────────────────────
  IMPACT_ASSESSED:          { en: 'Impact assessed',  ar: 'تم تقييم الأثر' },
  CAB_PENDING:              { en: 'CAB pending',      ar: 'بانتظار اللجنة' },
  CAB_APPROVED:             { en: 'CAB approved',     ar: 'اعتمدت اللجنة' },
  CAB_REJECTED:             { en: 'CAB rejected',     ar: 'رفضتها اللجنة' },
  EMERGENCY_APPROVED:       { en: 'Emergency',        ar: 'طارئ معتمد' },
  SCHEDULED:                { en: 'Scheduled',        ar: 'مجدولة' },
  IMPLEMENTING:             { en: 'Implementing',     ar: 'قيد التنفيذ' },
  POST_REVIEW:              { en: 'Post review',      ar: 'مراجعة بعدية' },
  ROLLED_BACK:              { en: 'Rolled back',      ar: 'مُتراجَع عنها' },
};

/** Severity / impact / risk-level enum. */
const SEVERITY_LABELS: Record<string, { en: string; ar: string }> = {
  CRITICAL: { en: 'Critical', ar: 'حرجة' },
  HIGH:     { en: 'High',     ar: 'عالية' },
  MEDIUM:   { en: 'Medium',   ar: 'متوسطة' },
  LOW:      { en: 'Low',      ar: 'منخفضة' },
  INFO:     { en: 'Info',     ar: 'معلوماتية' },
};

/** Priority enum (workflow submission, approval, etc.). */
const PRIORITY_LABELS: Record<string, { en: string; ar: string }> = {
  P1:     { en: 'P1 — Critical',  ar: 'أ1 — حرجة' },
  P2:     { en: 'P2 — High',      ar: 'أ2 — عالية' },
  P3:     { en: 'P3 — Normal',    ar: 'أ3 — عادية' },
  P4:     { en: 'P4 — Low',       ar: 'أ4 — منخفضة' },
  P5:     { en: 'P5 — Info',      ar: 'أ5 — معلوماتية' },
  URGENT: { en: 'Urgent',         ar: 'عاجلة' },
  NORMAL: { en: 'Normal',         ar: 'عادية' },
};

/** Risk-treatment strategy enum. */
const STRATEGY_LABELS: Record<string, { en: string; ar: string }> = {
  AVOID:    { en: 'Avoid',    ar: 'تجنب' },
  MITIGATE: { en: 'Mitigate', ar: 'تخفيف' },
  TRANSFER: { en: 'Transfer', ar: 'تحويل' },
  ACCEPT:   { en: 'Accept',   ar: 'قبول' },
};

/** Lookup helper — falls back to a title-cased version of the raw
 *  value if the enum isn't in the table yet. Falling back instead of
 *  throwing keeps demo data + new backend enums rendering correctly. */
function lookup(
  table: Record<string, { en: string; ar: string }>,
  value: string | null | undefined,
  lang: Lang,
): string {
  if (value == null || value === '') return '';
  const key = value.toUpperCase().replace(/[\s-]/g, '_');
  const row = table[key];
  if (row) return lang === 'ar' ? row.ar : row.en;
  // Fallback: humanize "PARTIAL_SUCCESS" → "Partial Success"
  return value
    .replace(/[_-]/g, ' ')
    .toLowerCase()
    .replace(/\b\w/g, c => c.toUpperCase());
}

export function statusLabel(status: string | null | undefined, lang: Lang): string {
  return lookup(STATUS_LABELS, status, lang);
}

export function severityLabel(severity: string | null | undefined, lang: Lang): string {
  return lookup(SEVERITY_LABELS, severity, lang);
}

export function priorityLabel(priority: string | null | undefined, lang: Lang): string {
  return lookup(PRIORITY_LABELS, priority, lang);
}

export function strategyLabel(strategy: string | null | undefined, lang: Lang): string {
  return lookup(STRATEGY_LABELS, strategy, lang);
}
