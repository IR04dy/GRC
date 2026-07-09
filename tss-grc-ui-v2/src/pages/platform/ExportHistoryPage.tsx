import { useCallback, useEffect, useMemo, useState } from 'react';
import { Download, RefreshCw, ChevronLeft, ChevronRight, FileText, Table, FileType2, FileArchive } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { auditLogApi, ApiError, type AuditLogEntryDto } from '../../lib/api';

const PAGE_SIZE = 50;

/** Parsed detail from the export audit event's newValueJson. */
interface ExportDetail { format?: string; language?: string; fileName?: string; path?: string; status?: number; }

function parseDetail(json?: string | null): ExportDetail {
  if (!json) return {};
  try { return JSON.parse(json) as ExportDetail; } catch { return {}; }
}

const FMT_ICON: Record<string, typeof FileText> = {
  pdf: FileText, docx: FileType2, xlsx: Table, csv: Table, zip: FileArchive, pptx: FileText, json: FileText,
};

/**
 * Export History — a focused, cross-module view over the platform audit log's
 * {@code action=EXPORT} events (recorded by the server-side ExportAuditInterceptor).
 * Shows who exported what, when, in which format + language, and the file name —
 * parsed from each event's detail JSON, which the generic Audit Log table doesn't surface.
 */
export function ExportHistoryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<AuditLogEntryDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);
  const [moduleFilter, setModuleFilter] = useState('');
  const [formatFilter, setFormatFilter] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await auditLogApi.list({
        actionType: 'EXPORT',
        sourceModule: moduleFilter || undefined,
        page, size: PAGE_SIZE,
      });
      setRows(p.content ?? []);
      setTotalPages(p.totalPages ?? 0);
      setTotal(p.totalElements ?? 0);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل سجل التصدير' : 'Failed to load export history'));
      setRows([]);
    } finally { setLoading(false); }
  }, [moduleFilter, page]);  // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { void load(); }, [load]);

  // Format filter is applied client-side over the loaded page (format lives in the detail JSON).
  const view = useMemo(() => {
    const withDetail = rows.map(r => ({ r, d: parseDetail(r.newValueJson) }));
    return formatFilter ? withDetail.filter(x => (x.d.format ?? '').toLowerCase() === formatFilter) : withDetail;
  }, [rows, formatFilter]);

  const formats = useMemo(
    () => Array.from(new Set(rows.map(r => parseDetail(r.newValueJson).format).filter(Boolean))) as string[],
    [rows],
  );

  const fmtTime = (s?: string) => (s ? s.replace('T', ' ').slice(0, 19) : '—');

  return (
    <Shell title={isAr ? 'سجل التصدير' : 'Export History'} sub={isAr ? 'كل ملف صُدِّر عبر المنصّة.' : 'Every file exported across the platform.'}>
      <PageHeader
        eyebrow={isAr ? 'المنصّة' : 'Platform'}
        title={isAr ? 'سجل التصدير' : 'Export History'}
        sub={isAr ? 'مَن صدّر ماذا ومتى وبأي صيغة ولغة — من سجل تدقيق المنصّة.' : 'Who exported what, when, in which format and language — from the platform audit trail.'}
      />

      <div className="kpi-grid mb-lg">
        <Kpi Icon={Download} label={isAr ? 'إجمالي التصديرات' : 'Total exports'} value={total} tone="primary" />
        <Kpi Icon={FileText} label={isAr ? 'الصيغ' : 'Formats'} value={formats.length || '—'} tone="info" />
      </div>

      <Card title={isAr ? 'التصديرات' : 'Exports'} Icon={Download}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <input value={moduleFilter} onChange={e => { setPage(0); setModuleFilter(e.target.value); }}
              placeholder={isAr ? 'الوحدة' : 'Module'} style={INPUT} />
            <select value={formatFilter} onChange={e => setFormatFilter(e.target.value)} style={INPUT} aria-label={isAr ? 'الصيغة' : 'Format'}>
              <option value="">{isAr ? 'كل الصيغ' : 'All formats'}</option>
              {formats.map(f => <option key={f} value={f.toLowerCase()}>{f.toUpperCase()}</option>)}
            </select>
            {(moduleFilter || formatFilter) && (
              <Button size="sm" variant="ghost" onClick={() => { setModuleFilter(''); setFormatFilter(''); setPage(0); }}>
                {isAr ? 'مسح' : 'Clear'}
              </Button>
            )}
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
          </div>
        }>
        {loading && rows.length === 0
          ? <div style={EMPTY}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : view.length === 0
            ? <div style={EMPTY}>{isAr ? 'لا توجد تصديرات' : 'No exports yet'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الوقت' : 'Time'}</th>
                  <th>{isAr ? 'الوحدة' : 'Module'}</th>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'الصيغة' : 'Format'}</th>
                  <th>{isAr ? 'اللغة' : 'Lang'}</th>
                  <th>{isAr ? 'اسم الملف' : 'File name'}</th>
                  <th>{isAr ? 'المستخدم' : 'User'}</th>
                </tr></thead>
                <tbody>
                  {view.map(({ r, d }) => {
                    const Icon = FMT_ICON[(d.format ?? '').toLowerCase()] ?? FileText;
                    return (
                      <tr key={r.id}>
                        <td className="mono text-2xs" style={{ whiteSpace: 'nowrap' }}>{fmtTime(r.createdAt)}</td>
                        <td className="text-2xs">{r.sourceModule ?? '—'}</td>
                        <td className="text-2xs">{r.entityCode ?? '—'}</td>
                        <td><Badge tone="info" dot><Icon size={11} style={{ marginInlineEnd: 3 }} />{(d.format ?? '—').toUpperCase()}</Badge></td>
                        <td className="text-2xs">{d.language ? d.language.toUpperCase() : '—'}</td>
                        <td className="mono text-2xs" style={{ wordBreak: 'break-all' }}>{d.fileName ?? '—'}</td>
                        <td className="text-2xs">{r.userUsername ?? '—'}</td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>}

        {totalPages > 1 && (
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', justifyContent: 'flex-end', padding: 10 }}>
            <Button size="sm" variant="ghost" Icon={ChevronLeft} disabled={page <= 0} onClick={() => setPage(p => Math.max(0, p - 1))} />
            <span className="text-2xs">{isAr ? `صفحة ${page + 1} من ${totalPages}` : `Page ${page + 1} of ${totalPages}`}</span>
            <Button size="sm" variant="ghost" Icon={ChevronRight} disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)} />
          </div>
        )}
      </Card>
    </Shell>
  );
}

const INPUT: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 120, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const EMPTY: React.CSSProperties = { padding: 16, color: 'var(--ink-3)', fontSize: 13 };
