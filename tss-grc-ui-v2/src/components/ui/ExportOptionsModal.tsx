import { useMemo, useState } from 'react';
import { FileText, FileType2, FileSpreadsheet, Table, FileJson, Download } from 'lucide-react';
import { Modal } from './Modal';
import { Button } from './Button';
import { ApiError } from '../../lib/api';

/**
 * Reusable export-options modal (Export P2). Instead of firing a fixed PDF/XLSX
 * download immediately (the old ExportMenu dropdown), it lets the user choose,
 * per export: the format, the document language (AR/EN — independent of the UI
 * language), and any page-specific "include" toggles (charts, evidence, cover…),
 * with a live filename preview that mirrors the server ExportNaming convention
 * ([Module]_[Type]_[LANG]_[YYYYMMDD]_v[N].[ext]).
 *
 * The page supplies an onExport(selection) callback that performs the actual
 * download for the chosen format/language.
 */

export type ExportFormat = 'pdf' | 'docx' | 'xlsx' | 'csv' | 'json';

export interface ExportIncludeOption {
  key: string;
  label: string;      // English label
  labelAr?: string;   // Arabic label (falls back to label)
  default?: boolean;  // initial checked state (default false)
}

export interface ExportSelection {
  format: ExportFormat;
  lang: 'ar' | 'en';
  includes: Record<string, boolean>;
}

interface Toast {
  success: (m: string) => void;
  danger: (m: string) => void;
  info?: (m: string) => void;
}

const FMT_META: Record<ExportFormat, { Icon: typeof FileText; label: string; langAware: boolean }> = {
  pdf:  { Icon: FileText,        label: 'PDF',   langAware: true  },
  docx: { Icon: FileType2,       label: 'Word',  langAware: true  },
  xlsx: { Icon: FileSpreadsheet, label: 'Excel', langAware: false },
  csv:  { Icon: Table,           label: 'CSV',   langAware: false },
  json: { Icon: FileJson,        label: 'JSON',  langAware: false },
};

/** YYYYMMDD in local time — matches the server ExportNaming date segment. */
function todayYmd(): string {
  const d = new Date();
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}${p(d.getMonth() + 1)}${p(d.getDate())}`;
}

export function ExportOptionsModal({
  open, onClose, t, toast,
  moduleCode, exportType,
  formats = ['pdf', 'xlsx'],
  includes = [],
  defaultLang,
  onExport,
  title,
}: {
  open: boolean;
  onClose: () => void;
  t: (k: string) => string;
  toast: Toast;
  /** Module code for the filename preview, e.g. "ERM". */
  moduleCode: string;
  /** Export type for the filename preview, e.g. "RiskRegister". */
  exportType: string;
  /** Formats offered, in display order. */
  formats?: ExportFormat[];
  /** Optional page-specific content toggles. */
  includes?: ExportIncludeOption[];
  /** Initial document language (usually the current UI language). */
  defaultLang: 'ar' | 'en';
  /** Performs the actual download for the chosen selection. */
  onExport: (sel: ExportSelection) => Promise<void>;
  title?: string;
}) {
  const isAr = defaultLang === 'ar';
  const [format, setFormat] = useState<ExportFormat>(formats[0] ?? 'pdf');
  const [lang, setLang] = useState<'ar' | 'en'>(defaultLang);
  const [inc, setInc] = useState<Record<string, boolean>>(
    () => Object.fromEntries(includes.map(i => [i.key, i.default ?? false])),
  );
  const [busy, setBusy] = useState(false);

  const langAware = FMT_META[format].langAware;

  const filename = useMemo(
    () => `${moduleCode}_${exportType}_${lang.toUpperCase()}_${todayYmd()}_v1.${format}`,
    [moduleCode, exportType, lang, format],
  );

  async function doExport() {
    setBusy(true);
    try {
      await onExport({ format, lang, includes: inc });
      toast.success(t('exportStarted'));
      onClose();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : t('exportFailed'));
    } finally {
      setBusy(false);
    }
  }

  return (
    <Modal
      open={open}
      onClose={onClose}
      size="sm"
      title={title ?? (isAr ? 'خيارات التصدير' : 'Export options')}
      footer={
        <>
          <Button variant="ghost" onClick={onClose} disabled={busy}>{isAr ? 'إلغاء' : 'Cancel'}</Button>
          <Button variant="primary" Icon={Download} onClick={() => void doExport()} disabled={busy}>
            {busy ? (isAr ? 'جارٍ التصدير…' : 'Exporting…') : (isAr ? 'تصدير' : 'Export')}
          </Button>
        </>
      }
    >
      {/* Format */}
      <div style={SECTION}>
        <label style={LABEL}>{isAr ? 'الصيغة' : 'Format'}</label>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
          {formats.map(f => {
            const { Icon, label } = FMT_META[f];
            const active = f === format;
            return (
              <button key={f} type="button" onClick={() => setFormat(f)} style={chip(active)}>
                <Icon size={14} /> {label}
              </button>
            );
          })}
        </div>
      </div>

      {/* Language */}
      <div style={SECTION}>
        <label style={LABEL}>{isAr ? 'لغة المستند' : 'Document language'}</label>
        <div style={{ display: 'flex', gap: 8 }}>
          {(['ar', 'en'] as const).map(l => (
            <button key={l} type="button" onClick={() => setLang(l)} disabled={!langAware}
              style={chip(lang === l, !langAware)}>
              {l === 'ar' ? 'العربية' : 'English'}
            </button>
          ))}
        </div>
        {!langAware && (
          <div style={HINT}>
            {isAr ? 'اللغة تنطبق على ملفات PDF وWord فقط.' : 'Language applies to PDF and Word files only.'}
          </div>
        )}
      </div>

      {/* Include toggles (optional) */}
      {includes.length > 0 && (
        <div style={SECTION}>
          <label style={LABEL}>{isAr ? 'تضمين' : 'Include'}</label>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {includes.map(o => (
              <label key={o.key} style={CHECK_ROW}>
                <input type="checkbox" checked={inc[o.key] ?? false}
                  onChange={e => setInc(s => ({ ...s, [o.key]: e.target.checked }))} />
                <span>{isAr ? (o.labelAr ?? o.label) : o.label}</span>
              </label>
            ))}
          </div>
        </div>
      )}

      {/* Filename preview */}
      <div style={SECTION}>
        <label style={LABEL}>{isAr ? 'اسم الملف' : 'File name'}</label>
        <code style={FILENAME}>{filename}</code>
      </div>
    </Modal>
  );
}

const SECTION: React.CSSProperties = { marginBottom: 16 };
const LABEL: React.CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 600, color: 'var(--ink-2)', marginBottom: 6,
};
const HINT: React.CSSProperties = { fontSize: 11, color: 'var(--ink-3)', marginTop: 6 };
const CHECK_ROW: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, color: 'var(--ink-1)', cursor: 'pointer',
};
const FILENAME: React.CSSProperties = {
  display: 'block', fontFamily: 'var(--font-mono, monospace)', fontSize: 12,
  padding: '8px 10px', background: 'var(--surface-2, var(--surface))',
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', color: 'var(--ink-2)',
  wordBreak: 'break-all',
};

function chip(active: boolean, disabled = false): React.CSSProperties {
  return {
    display: 'inline-flex', alignItems: 'center', gap: 6,
    padding: '7px 12px', fontSize: 12.5, fontWeight: 500,
    border: `1px solid ${active ? 'var(--primary)' : 'var(--border)'}`,
    borderRadius: 'var(--r-sm)',
    background: active ? 'var(--primary-soft, var(--primary))' : 'var(--surface-1, var(--surface))',
    color: active ? 'var(--primary-ink, var(--primary))' : 'var(--ink-1)',
    cursor: disabled ? 'not-allowed' : 'pointer',
    opacity: disabled ? 0.45 : 1,
  };
}
