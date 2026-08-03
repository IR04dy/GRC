import { useEffect, useRef, useState } from 'react';
import { Download, FileText, Table, FileType2, ChevronDown } from 'lucide-react';
import { Button } from './Button';
import { ApiError } from '../../lib/api';

interface Toast {
  success: (msg: string) => void;
  danger:  (msg: string) => void;
  info?:   (msg: string) => void;
}

/**
 * Tiny PDF/Excel export dropdown. Reusable across pages.
 *
 * Props:
 *   onPdf / onXlsx / onDocx — async callbacks that perform the download
 *   demoMode — when true, the menu is disabled and shows a tooltip
 *   disabledReason — F2-98: the i18n key explaining *why* it is disabled. Defaults to
 *     `exportSignInRequired`, which was the only cause when this component was written.
 *     It is no longer: a signed-in user whose register came back empty is shown demo rows,
 *     and exporting then produces the real (empty) file while the screen shows invented
 *     ones. Blocking is right; "Sign in to export" is the wrong sentence to block with.
 */
export function ExportMenu({
  demoMode, t, toast, onPdf, onXlsx, onDocx, disabledReason = 'exportSignInRequired',
}: {
  demoMode: boolean;
  t: (k: string) => string;
  toast: Toast;
  onPdf?: () => Promise<void>;
  onXlsx?: () => Promise<void>;
  onDocx?: () => Promise<void>;
  disabledReason?: string;
}) {
  const [open, setOpen] = useState(false);
  const [busy, setBusy] = useState<'pdf' | 'xlsx' | 'docx' | null>(null);
  const ref = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!open) return;
    const onDown = (e: MouseEvent) => {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    };
    document.addEventListener('mousedown', onDown);
    return () => document.removeEventListener('mousedown', onDown);
  }, [open]);

  async function run(kind: 'pdf' | 'xlsx' | 'docx', fn?: () => Promise<void>) {
    if (!fn) return;
    setBusy(kind); setOpen(false);
    try { await fn(); toast.success(t('exportStarted')); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('exportFailed')); }
    finally { setBusy(null); }
  }

  return (
    <div ref={ref} style={{ position: 'relative' }}>
      <Button
        variant="secondary"
        Icon={Download}
        disabled={demoMode || busy != null}
        onClick={() => setOpen(o => !o)}
        title={demoMode ? t(disabledReason) : t('riskExport')}
      >
        {t('riskExport')}
        <ChevronDown size={12} style={{ marginInlineStart: 4, opacity: 0.7 }} />
      </Button>
      {open && (
        <div role="menu" style={{
          position: 'absolute', top: 'calc(100% + 6px)', insetInlineEnd: 0,
          minWidth: 180, background: 'var(--surface)',
          border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
          boxShadow: 'var(--shadow-3)', padding: 4, zIndex: 1000,
        }}>
          {onPdf && (
            <button role="menuitem" onClick={() => run('pdf', onPdf)} style={menuItem}>
              <FileText size={14} style={{ color: 'var(--ink-3)' }} />
              <span>{t('exportPdf')}</span>
            </button>
          )}
          {onDocx && (
            <button role="menuitem" onClick={() => run('docx', onDocx)} style={menuItem}>
              <FileType2 size={14} style={{ color: 'var(--ink-3)' }} />
              <span>{t('exportDocx')}</span>
            </button>
          )}
          {onXlsx && (
            <button role="menuitem" onClick={() => run('xlsx', onXlsx)} style={menuItem}>
              <Table size={14} style={{ color: 'var(--ink-3)' }} />
              <span>{t('exportXlsx')}</span>
            </button>
          )}
        </div>
      )}
    </div>
  );
}

const menuItem: React.CSSProperties = {
  display: 'flex', alignItems: 'center', gap: 10,
  width: '100%', padding: '8px 10px',
  background: 'transparent', border: 'none', cursor: 'pointer',
  borderRadius: 'var(--r-sm)', color: 'var(--ink-1)', textAlign: 'start',
  fontSize: 13,
};
