import { useCallback, useEffect, useState } from 'react';
import { ScrollText, Activity, AlertTriangle, AlertOctagon, RefreshCw, ChevronLeft, ChevronRight, Filter, Download } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { auditLogApi, ApiError, type AuditLogEntryDto } from '../../lib/api';

const SEV_TONE: Record<string, BadgeTone> = {
  INFO: 'info', WARN: 'warning', WARNING: 'warning', ERROR: 'danger', CRITICAL: 'danger',
};

const SELECT_STYLE: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 130, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};

const PAGE_SIZE = 50;

/**
 * Platform-wide Audit Log console (F7). Surfaces the global
 * /api/v1/audit-log endpoints — filterable + paginated activity, plus the
 * rolling 24-hour stats — that were previously only reachable per-entity via
 * the detail-page Activity timeline.
 */
export function AuditLogPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [rows, setRows] = useState<AuditLogEntryDto[]>([]);
  const [stats, setStats] = useState<Record<string, number> | null>(null);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [total, setTotal] = useState(0);

  // applied filters (drive the query) vs. draft (edited in the toolbar)
  const [filters, setFilters] = useState({ severity: '', sourceModule: '', actionType: '' });
  const [draft, setDraft] = useState({ severity: '', sourceModule: '', actionType: '' });

  const load = useCallback(async () => {
    setLoading(true);
    try {
      const p = await auditLogApi.list({
        severity: filters.severity || undefined,
        sourceModule: filters.sourceModule || undefined,
        actionType: filters.actionType || undefined,
        page, size: PAGE_SIZE,
      });
      setRows(p.content ?? []);
      setTotalPages(p.totalPages ?? 0);
      setTotal(p.totalElements ?? 0);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل سجل التدقيق' : 'Failed to load audit log'));
      setRows([]);
    } finally { setLoading(false); }
  }, [filters, page]);  // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { void load(); }, [load]);
  useEffect(() => {
    auditLogApi.stats().then(setStats).catch(() => setStats(null));
  }, []);

  function applyFilters() { setPage(0); setFilters({ ...draft }); }
  function clearFilters() { setDraft({ severity: '', sourceModule: '', actionType: '' }); setFilters({ severity: '', sourceModule: '', actionType: '' }); setPage(0); }
  // One-click export-history view — the ExportAuditInterceptor records every download as actionType=EXPORT.
  function toggleExports() {
    const on = filters.actionType === 'EXPORT';
    const next = on ? '' : 'EXPORT';
    setDraft(d => ({ ...d, actionType: next }));
    setFilters(f => ({ ...f, actionType: next }));
    setPage(0);
  }

  const fmtTime = (s?: string) => (s ? s.replace('T', ' ').slice(0, 19) : '—');

  return (
    <Shell title={isAr ? 'سجل التدقيق' : 'Audit log'} sub={isAr ? 'نشاط المنصّة بالكامل' : 'Platform-wide activity'}>
      <PageHeader
        eyebrow={isAr ? 'المنصّة · F7' : 'Platform · F7'}
        title={isAr ? 'سجل التدقيق' : 'Audit log'}
        sub={isAr ? 'كل إجراء مُغيِّر للحالة عبر الوحدات — قابل للتصفية والتتبّع.' : 'Every state-changing action across modules — filterable and traceable.'}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Activity}      label={isAr ? 'أحداث (24س)' : 'Events (24h)'} value={stats?.events ?? '—'} tone="primary" onClick={() => { setDraft({ severity: '', sourceModule: '', actionType: '' }); setFilters({ severity: '', sourceModule: '', actionType: '' }); setPage(0); }} />
        <Kpi Icon={AlertTriangle} label={isAr ? 'تحذيرات (24س)' : 'Warnings (24h)'} value={stats?.warns ?? '—'} tone="warning" onClick={() => { setDraft(d => ({ ...d, severity: 'WARN' })); setFilters(f => ({ ...f, severity: 'WARN' })); setPage(0); }} active={filters.severity === 'WARN'} />
        <Kpi Icon={AlertOctagon}  label={isAr ? 'أخطاء (24س)' : 'Errors (24h)'} value={stats?.errors ?? '—'} tone="danger" onClick={() => { setDraft(d => ({ ...d, severity: 'ERROR' })); setFilters(f => ({ ...f, severity: 'ERROR' })); setPage(0); }} active={filters.severity === 'ERROR'} />
        <Kpi Icon={ScrollText}    label={isAr ? 'إجمالي السجلّات' : 'Total entries'} value={total} tone="info" onClick={() => { setDraft({ severity: '', sourceModule: '', actionType: '' }); setFilters({ severity: '', sourceModule: '', actionType: '' }); setPage(0); }} />
      </div>

      <Card title={isAr ? 'النشاط' : 'Activity'} Icon={ScrollText}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <select value={draft.severity} onChange={e => setDraft(d => ({ ...d, severity: e.target.value }))} style={SELECT_STYLE}
              aria-label={isAr ? 'الخطورة' : 'Severity'}>
              <option value="">{isAr ? 'كل الخطورات' : 'All severities'}</option>
              <option value="INFO">INFO</option>
              <option value="WARN">WARN</option>
              <option value="ERROR">ERROR</option>
            </select>
            <input value={draft.sourceModule} onChange={e => setDraft(d => ({ ...d, sourceModule: e.target.value }))}
              placeholder={isAr ? 'الوحدة' : 'Module'} style={{ ...SELECT_STYLE, minWidth: 120 }}
              onKeyDown={e => { if (e.key === 'Enter') applyFilters(); }} />
            <input value={draft.actionType} onChange={e => setDraft(d => ({ ...d, actionType: e.target.value }))}
              placeholder={isAr ? 'نوع الإجراء' : 'Action type'} style={{ ...SELECT_STYLE, minWidth: 150 }}
              onKeyDown={e => { if (e.key === 'Enter') applyFilters(); }} />
            <Button size="sm" variant="primary" Icon={Filter} onClick={applyFilters}>{isAr ? 'تطبيق' : 'Apply'}</Button>
            <Button size="sm" variant={filters.actionType === 'EXPORT' ? 'primary' : 'ghost'} Icon={Download}
              onClick={toggleExports}>{isAr ? 'التصديرات' : 'Exports'}</Button>
            {(filters.severity || filters.sourceModule || filters.actionType) && (
              <Button size="sm" variant="ghost" onClick={clearFilters}>{isAr ? 'مسح' : 'Clear'}</Button>
            )}
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void load()} disabled={loading}>{isAr ? 'تحديث' : 'Refresh'}</Button>
          </div>
        }>
        {loading && rows.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : rows.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>{isAr ? 'لا توجد سجلّات مطابقة' : 'No matching entries'}</div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الوقت' : 'Time'}</th>
                  <th>{isAr ? 'الإجراء' : 'Action'}</th>
                  <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                  <th>{isAr ? 'الكيان' : 'Entity'}</th>
                  <th>{isAr ? 'المستخدم' : 'User'}</th>
                  <th>{isAr ? 'الرسالة' : 'Message'}</th>
                </tr></thead>
                <tbody>
                  {rows.map(r => (
                    <tr key={r.id}>
                      <td className="mono text-2xs" style={{ whiteSpace: 'nowrap' }}>{fmtTime(r.createdAt)}</td>
                      <td className="mono text-2xs">{r.actionType}</td>
                      <td><Badge tone={SEV_TONE[(r.severity ?? '').toUpperCase()] ?? 'neutral'} dot>{r.severity ?? '—'}</Badge></td>
                      <td className="text-2xs">
                        {r.entityType
                          ? <span>{r.entityType}{r.entityId != null && <span className="text-muted">#{r.entityId}</span>}</span>
                          : <span className="text-muted">—</span>}
                      </td>
                      <td className="text-2xs">
                        {r.userUsername ?? '—'}
                        {r.userRole && <span className="text-muted" style={{ marginInlineStart: 4 }}>· {r.userRole}</span>}
                      </td>
                      <td className="text-xs" style={{ maxWidth: 420 }}>
                        <div style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={r.message ?? ''}>
                          {r.message ?? '—'}
                        </div>
                        {r.sourceEndpoint && <div className="mono text-2xs text-muted">{r.sourceEndpoint}</div>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>}

        {/* Pager */}
        {totalPages > 1 && (
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'flex-end', gap: 10, marginTop: 12 }}>
            <span className="text-2xs text-muted">
              {isAr ? `صفحة ${page + 1} من ${totalPages} · ${total} سجلاً` : `Page ${page + 1} of ${totalPages} · ${total} entries`}
            </span>
            <Button size="sm" variant="ghost" Icon={ChevronLeft} disabled={page === 0 || loading} onClick={() => setPage(p => Math.max(0, p - 1))}>
              {isAr ? 'السابق' : 'Prev'}
            </Button>
            <Button size="sm" variant="ghost" Icon={ChevronRight} disabled={page >= totalPages - 1 || loading} onClick={() => setPage(p => Math.min(totalPages - 1, p + 1))}>
              {isAr ? 'التالي' : 'Next'}
            </Button>
          </div>
        )}
      </Card>
    </Shell>
  );
}
