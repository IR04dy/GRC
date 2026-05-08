// Bulk CSV import — Track A2 (sales-readiness).
//
// Lets a tenant move from spreadsheets to TSS GRC in one shot. Each target
// (risks, controls, vendors) declares a strict bilingual schema, and the
// importer walks the user through:
//
//   1. Pick a target type
//   2. Upload a CSV file (or paste rows)
//   3. Map each CSV column to a target field (auto-detected by header match)
//   4. Preview with row-level validation badges
//   5. Import — successes are pushed to a localStorage overlay keyed on the
//      target. Failures stay in the preview so the user can fix and retry.

export type TargetType = 'risks' | 'controls' | 'vendors';

export interface FieldDef {
  /** Internal field name. */
  name: string;
  /** Display label (English) — also used for header auto-mapping. */
  labelEn: string;
  /** Display label (Arabic). */
  labelAr: string;
  /** Field is required for a row to validate. */
  required: boolean;
  /** Constrained value list — when set, a row must pick from these. */
  enumValues?: string[];
  /** Free-text hint shown under the column mapping dropdown. */
  hintEn?: string;
  hintAr?: string;
}

export interface SchemaDef {
  target: TargetType;
  /** Display name. */
  nameEn: string;
  nameAr: string;
  /** Per-row fields. */
  fields: FieldDef[];
  /** Sample CSV — used for the "download template" button. */
  sample: string[][];
}

export const SCHEMAS: SchemaDef[] = [
  {
    target: 'risks',
    nameEn: 'Risks',
    nameAr: 'المخاطر',
    fields: [
      { name: 'titleEn',     labelEn: 'Title (EN)',     labelAr: 'العنوان (إنجليزي)', required: true },
      { name: 'titleAr',     labelEn: 'Title (AR)',     labelAr: 'العنوان (عربي)',    required: true },
      { name: 'category',    labelEn: 'Category',       labelAr: 'الفئة',             required: true,
        enumValues: ['Operational', 'Cyber', 'Compliance', 'Strategic', 'Financial', 'Reputational', 'Third-Party'] },
      { name: 'likelihood',  labelEn: 'Likelihood',     labelAr: 'الاحتمالية',        required: true,
        enumValues: ['1', '2', '3', '4', '5'],
        hintEn: '1 (rare) – 5 (almost certain)',
        hintAr: '1 (نادر) – 5 (شبه مؤكَّد)' },
      { name: 'impact',      labelEn: 'Impact',         labelAr: 'الأثر',             required: true,
        enumValues: ['1', '2', '3', '4', '5'],
        hintEn: '1 (negligible) – 5 (catastrophic)',
        hintAr: '1 (طفيف) – 5 (كارثي)' },
      { name: 'owner',       labelEn: 'Owner',          labelAr: 'المالك',            required: true },
      { name: 'description', labelEn: 'Description',    labelAr: 'الوصف',             required: false },
    ],
    sample: [
      ['Title (EN)', 'Title (AR)', 'Category', 'Likelihood', 'Impact', 'Owner', 'Description'],
      ['Ransomware attack on core banking', 'هجوم فدية على النظام البنكي الأساسي', 'Cyber', '3', '5', 'CISO', 'Threat actors target the core banking switch.'],
      ['Vendor concentration on cloud provider', 'تركّز المورّد على مزوّد سحابي', 'Third-Party', '2', '4', 'Head of Procurement', 'Single AWS region carries production workloads.'],
    ],
  },
  {
    target: 'controls',
    nameEn: 'Controls',
    nameAr: 'الضوابط',
    fields: [
      { name: 'code',         labelEn: 'Code',          labelAr: 'الكود',             required: true,
        hintEn: 'Stable identifier, e.g. "ECC-2-1-1"',
        hintAr: 'مُعرّف ثابت مثل ECC-2-1-1' },
      { name: 'titleEn',      labelEn: 'Title (EN)',    labelAr: 'العنوان (إنجليزي)', required: true },
      { name: 'titleAr',      labelEn: 'Title (AR)',    labelAr: 'العنوان (عربي)',    required: true },
      { name: 'framework',    labelEn: 'Framework',     labelAr: 'الإطار',            required: true,
        enumValues: ['NCA ECC', 'SAMA CSF', 'SAMA OPRES', 'PDPL', 'CMA', 'CITC', 'NCEMA', 'ISO 27001'] },
      { name: 'maturity',     labelEn: 'Maturity',      labelAr: 'النضج',             required: false,
        enumValues: ['1', '2', '3', '4', '5'] },
      { name: 'owner',        labelEn: 'Owner',         labelAr: 'المالك',            required: true },
    ],
    sample: [
      ['Code', 'Title (EN)', 'Title (AR)', 'Framework', 'Maturity', 'Owner'],
      ['ECC-2-1-1', 'Cybersecurity strategy', 'استراتيجية الأمن السيبراني', 'NCA ECC', '4', 'CISO'],
      ['CSF-3-2-4', 'Privileged access management', 'إدارة الوصول المُميَّز', 'SAMA CSF', '3', 'Head of Cyber'],
    ],
  },
  {
    target: 'vendors',
    nameEn: 'Vendors',
    nameAr: 'المورّدون',
    fields: [
      { name: 'nameEn',       labelEn: 'Name (EN)',     labelAr: 'الاسم (إنجليزي)',   required: true },
      { name: 'nameAr',       labelEn: 'Name (AR)',     labelAr: 'الاسم (عربي)',      required: true },
      { name: 'cr',           labelEn: 'CR Number',     labelAr: 'رقم السجل التجاري', required: false,
        hintEn: '10-digit Saudi CR — used for the Tasattur risk index',
        hintAr: 'سجل تجاري سعودي من 10 أرقام — يُستخدم في مؤشر تَستر' },
      { name: 'criticality',  labelEn: 'Criticality',   labelAr: 'الأهمية',           required: true,
        enumValues: ['Tier-1', 'Tier-2', 'Tier-3', 'Tier-4'] },
      { name: 'spend',        labelEn: 'Annual Spend',  labelAr: 'الإنفاق السنوي',    required: false,
        hintEn: 'In SAR thousands',
        hintAr: 'بالآلاف الريال السعودي' },
      { name: 'owner',        labelEn: 'Relationship Owner', labelAr: 'المالك',       required: true },
    ],
    sample: [
      ['Name (EN)', 'Name (AR)', 'CR Number', 'Criticality', 'Annual Spend', 'Relationship Owner'],
      ['Saudi Aramco IT Services', 'خدمات تقنية أرامكو السعودية', '1010012345', 'Tier-1', '12500', 'Head of IT'],
      ['STC Cloud', 'سحابة STC', '1010054321', 'Tier-2', '4500', 'Head of Infrastructure'],
    ],
  },
];

// ─── CSV parsing ──────────────────────────────────────────────────────────────

/**
 * Minimal RFC-4180-aware CSV parser. Supports:
 *  - quoted fields (with escaped "")
 *  - commas inside quotes
 *  - both \r\n and \n line endings
 */
export function parseCsv(text: string): string[][] {
  const rows: string[][] = [];
  let row: string[] = [];
  let cur = '';
  let inQuotes = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (inQuotes) {
      if (c === '"' && text[i + 1] === '"') { cur += '"'; i++; }
      else if (c === '"') inQuotes = false;
      else cur += c;
    } else {
      if (c === '"') inQuotes = true;
      else if (c === ',') { row.push(cur); cur = ''; }
      else if (c === '\n') { row.push(cur); rows.push(row); row = []; cur = ''; }
      else if (c === '\r') { /* skip */ }
      else cur += c;
    }
  }
  if (cur || row.length) { row.push(cur); rows.push(row); }
  return rows.filter(r => r.length > 1 || (r.length === 1 && r[0].trim() !== ''));
}

/** Auto-detect column → field mapping by exact / case-insensitive label match. */
export function autoMap(headers: string[], schema: SchemaDef): Record<string, string> {
  const map: Record<string, string> = {};
  for (const field of schema.fields) {
    const normEn = field.labelEn.toLowerCase().trim();
    const normAr = field.labelAr.trim();
    const idx = headers.findIndex(h => {
      const lc = h.toLowerCase().trim();
      return lc === normEn || h.trim() === normAr || lc === field.name.toLowerCase();
    });
    if (idx >= 0) map[field.name] = headers[idx];
  }
  return map;
}

// ─── Validation ──────────────────────────────────────────────────────────────

export interface RowValidation {
  rowIndex: number;
  data: Record<string, string>;
  errors: string[];
  ok: boolean;
}

export function validateRows(
  rows: string[][],
  headers: string[],
  mapping: Record<string, string>,
  schema: SchemaDef,
): RowValidation[] {
  // Skip the header row when building data rows.
  const headerLc = headers.map(h => h.toLowerCase().trim());
  const dataRows = rows.slice(1);
  return dataRows.map((row, idx) => {
    const data: Record<string, string> = {};
    const errors: string[] = [];
    for (const field of schema.fields) {
      const sourceCol = mapping[field.name];
      if (!sourceCol) {
        if (field.required) errors.push(`Field "${field.labelEn}" is not mapped`);
        data[field.name] = '';
        continue;
      }
      const colIdx = headerLc.indexOf(sourceCol.toLowerCase().trim());
      const raw = colIdx >= 0 ? (row[colIdx] ?? '').trim() : '';
      data[field.name] = raw;
      if (field.required && !raw) errors.push(`Field "${field.labelEn}" is required`);
      if (raw && field.enumValues && !field.enumValues.includes(raw)) {
        errors.push(`"${raw}" is not a valid value for "${field.labelEn}" (expected ${field.enumValues.join(', ')})`);
      }
    }
    return { rowIndex: idx + 2, data, errors, ok: errors.length === 0 };
  });
}

// ─── Persistence ──────────────────────────────────────────────────────────────

const KEY_PREFIX = 'grc.csv-import.v1.';
const EVENT = 'grc.csv-import.changed';

export interface ImportedItem {
  id: string;
  importedAt: string;
  data: Record<string, string>;
}

export interface ImportLog {
  importedAt: string;
  target: TargetType;
  fileName?: string;
  rowsImported: number;
  rowsRejected: number;
}

const LOG_KEY = 'grc.csv-import.log.v1';

export function loadImported(target: TargetType): ImportedItem[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(KEY_PREFIX + target);
    if (!raw) return [];
    return JSON.parse(raw) as ImportedItem[];
  } catch { return []; }
}

export function appendImported(target: TargetType, items: ImportedItem[]): void {
  const existing = loadImported(target);
  const merged = [...existing, ...items];
  try { window.localStorage.setItem(KEY_PREFIX + target, JSON.stringify(merged)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function clearImported(target: TargetType): void {
  try { window.localStorage.removeItem(KEY_PREFIX + target); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function loadImportLog(): ImportLog[] {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(LOG_KEY);
    if (!raw) return [];
    return JSON.parse(raw) as ImportLog[];
  } catch { return []; }
}

export function appendImportLog(entry: ImportLog): void {
  const existing = loadImportLog();
  const merged = [entry, ...existing].slice(0, 50);
  try { window.localStorage.setItem(LOG_KEY, JSON.stringify(merged)); } catch { /* ignore */ }
  window.dispatchEvent(new CustomEvent(EVENT));
}

export function subscribeCsvImport(cb: () => void): () => void {
  const handler = () => cb();
  window.addEventListener(EVENT, handler);
  return () => window.removeEventListener(EVENT, handler);
}

/** Generate a CSV text string from the schema's sample (used for "Download template"). */
export function buildSampleCsv(schema: SchemaDef): string {
  return schema.sample
    .map(row => row.map(cell => /[",\n]/.test(cell) ? `"${cell.replace(/"/g, '""')}"` : cell).join(','))
    .join('\n');
}
