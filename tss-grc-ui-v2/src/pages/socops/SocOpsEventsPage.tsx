import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Siren, AlertTriangle, Clock, CheckCircle2, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { socopsApi, type SecurityEvent, type PageResp } from '../../lib/socops';

/**
 * SIEM event register — the SOC analyst's primary workspace.
 *
 * Wires to {@code GET /api/v1/socops/events} with paging + adds two
 * inline workflow actions per row (triage, resolve) which fan out to
 * the V150 service methods and re-fire the F8 rules seeded in V151.
 */
type Filter = 'all' | 'open' | 'critical' | 'sla' | 'closed';
type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const SEVERITY_TONE: Record<string, 'risk-crit' | 'risk-high' | 'risk-med' | 'risk-low' | 'neutral'> = {
  CRITICAL: 'risk-crit',
  HIGH: 'risk-high',
  MEDIUM: 'risk-med',
  LOW: 'risk-low',
  INFO: 'neutral',
};

const STATUS_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  NEW: 'danger',
  TRIAGED: 'warning',
  IN_PROGRESS: 'info',
  ESCALATED: 'info',
  RESOLVED: 'success',
  FALSE_POSITIVE: 'neutral',
  CLOSED: 'neutral',
};

const TERMINAL = new Set(['RESOLVED', 'FALSE_POSITIVE', 'CLOSED']);

export function SocOpsEventsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<SecurityEvent> | null>(null);
  const [filter, setFilter] = useState<Filter>('open');
  const [pageIdx, setPageIdx] = useState(0);
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    try {
      const resp = await socopsApi.events(pageIdx, 50);
      setPage(resp);
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [pageIdx]);

  // Filter client-side (server paging returns 50 rows at a time).
  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'open') return all.filter(e => !TERMINAL.has(e.status));
    if (filter === 'critical') return all.filter(e => e.severity === 'CRITICAL' && !TERMINAL.has(e.status));
    if (filter === 'closed') return all.filter(e => TERMINAL.has(e.status));
    if (filter === 'sla') {
      const cutoff = Date.now() + 30 * 60 * 1000;     // 30-min window
      return all.filter(e => !TERMINAL.has(e.status) && e.slaDueAt && new Date(e.slaDueAt).getTime() < cutoff);
    }
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const open  = all.filter(e => !TERMINAL.has(e.status)).length;
    const crit  = all.filter(e => e.severity === 'CRITICAL' && !TERMINAL.has(e.status)).length;
    const cutoff = Date.now() + 30 * 60 * 1000;
    const sla   = all.filter(e => !TERMINAL.has(e.status) && e.slaDueAt && new Date(e.slaDueAt).getTime() < cutoff).length;
    const closed = all.filter(e => TERMINAL.has(e.status)).length;
    return { open, crit, sla, closed, total: all.length };
  }, [page]);

  async function doTriage(id: number) {
    try {
      await socopsApi.eventTriage(id);
      toast.success(isAr ? 'تم بدء المعالجة' : 'Triage started');
      void load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Triage failed');
    }
  }
  async function doResolve(id: number) {
    try {
      await socopsApi.eventResolve(id, isAr ? 'تم الحل من شاشة الأحداث' : 'Resolved from events screen', false);
      toast.success(isAr ? 'تم الإغلاق' : 'Resolved');
      void load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Resolve failed');
    }
  }
  async function doEscalate(id: number) {
    try {
      const ev = await socopsApi.eventEscalate(id, isAr ? 'تصعيد من شاشة الأحداث' : 'Escalated from events screen');
      toast.success(isAr ? `تم التصعيد إلى الحادثة #${ev.incidentId}` : `Escalated to incident #${ev.incidentId}`);
      void load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? 'Escalate failed');
    }
  }

  return (
    <Shell title={isAr ? 'أحداث الأمن (SIEM)' : 'SIEM events'}
           sub={isAr ? 'قائمة المحلل الأمني الرئيسية' : "The SOC analyst's working queue"}>
      <PageHeader
        eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'أحداث الأمن (SIEM)' : 'SIEM events'}
        sub={isAr ? 'ECC-2: الإقرار خلال ساعة للحرج · ٤س للعالي · ٢٤س للمتوسط · ٧٢س للمنخفض'
                  : 'ECC-2 SLAs — 1h CRITICAL · 4h HIGH · 24h MEDIUM · 72h LOW'}
        actions={
          <>
            <RaiseRequestButton sourceModule="SOCOPS" />
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
          </>
        }
      />

      <div style={{ marginBottom: 16 }}><ModuleEngineRequestsPanel sourceModule="SOCOPS" /></div>

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Siren}          label={isAr ? 'حرجة مفتوحة' : 'Open CRITICAL'} value={String(counts.crit)}   tone={counts.crit > 0 ? 'danger' : 'success'} onClick={() => setFilter('critical')} active={filter === 'critical'} />
        <Kpi Icon={AlertTriangle}  label={isAr ? 'مفتوحة (الكل)' : 'Open (all)'}  value={String(counts.open)}   tone={counts.open > 0 ? 'warning' : 'success'} onClick={() => setFilter('open')} active={filter === 'open'} />
        <Kpi Icon={Clock}          label={isAr ? 'اقتراب SLA' : 'SLA approaching'} value={String(counts.sla)}    tone={counts.sla > 0 ? 'warning' : 'success'} onClick={() => setFilter('sla')} active={filter === 'sla'} />
        <Kpi Icon={CheckCircle2}   label={isAr ? 'مغلقة' : 'Closed'}                value={String(counts.closed)} tone="info" onClick={() => setFilter('closed')} active={filter === 'closed'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'open',     label: isAr ? 'المفتوحة' : 'Open',          count: counts.open },
          { id: 'critical', label: isAr ? 'الحرجة'    : 'CRITICAL',      count: counts.crit },
          { id: 'sla',      label: isAr ? 'SLA قريب'  : 'SLA approaching',count: counts.sla },
          { id: 'closed',   label: isAr ? 'المغلقة'  : 'Closed',         count: counts.closed },
          { id: 'all',      label: isAr ? 'الكل'      : 'All',            count: counts.total },
        ] as { id: Filter; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setFilter(td.id)}
            style={{
              padding: '8px 12px', fontSize: 12, fontWeight: 600,
              border: `1px solid ${filter === td.id ? 'var(--primary)' : 'var(--border)'}`,
              background: filter === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
              borderRadius: 'var(--r-sm)', cursor: 'pointer',
              display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            {td.label}
            <Badge tone={filter === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
            {isAr ? 'جارٍ التحميل…' : 'Loading…'}
          </div>
        )}
        {state === 'error' && (
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}>
            <div style={{ color: 'var(--danger-fg)', marginBottom: 8 }}>{errMsg ?? 'Error'}</div>
            <Button size="sm" variant="primary" onClick={() => void load()}>
              {isAr ? 'إعادة المحاولة' : 'Retry'}
            </Button>
          </div>
        )}
        {state === 'ready' && rows.length === 0 && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>
        )}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{isAr ? 'الوقت' : 'Time'}</th>
                <th>{isAr ? 'المصدر' : 'Source'}</th>
                <th>{isAr ? 'الخطورة' : 'Severity'}</th>
                <th>{isAr ? 'القاعدة' : 'Rule'}</th>
                <th>{isAr ? 'المضيف/المستخدم' : 'Host / User'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'استحقاق SLA' : 'SLA due'}</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {rows.map(e => (
                <tr key={e.id}>
                  <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {e.eventTime?.slice(0, 16).replace('T', ' ')}
                  </td>
                  <td style={{ fontSize: 11 }}>{e.sourceSystem}</td>
                  <td>
                    <Badge tone={SEVERITY_TONE[e.severity] ?? 'neutral'}>{e.severity}</Badge>
                  </td>
                  <td style={{ fontSize: 11, maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                    <Link to={`/socops/events/${e.id}`}
                          style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                      {e.ruleName ?? `#${e.id}`}
                    </Link>
                  </td>
                  <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {e.sourceHost ?? e.sourceIp ?? '—'}
                    {e.username ? <div className="mono">{e.username}</div> : null}
                  </td>
                  <td>
                    <Badge tone={STATUS_TONE[e.status] ?? 'neutral'}>{e.status}</Badge>
                  </td>
                  <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {e.slaDueAt ? e.slaDueAt.slice(0, 16).replace('T', ' ') : '—'}
                  </td>
                  <td>
                    {!TERMINAL.has(e.status) && e.status === 'NEW' && (
                      <Button size="sm" variant="primary" onClick={() => void doTriage(e.id)}>
                        {isAr ? 'بدء المعالجة' : 'Triage'}
                      </Button>
                    )}
                    {!TERMINAL.has(e.status) && !e.incidentId && (
                      <Button size="sm" variant="ghost" onClick={() => void doEscalate(e.id)}>
                        {isAr ? 'تصعيد لحادثة' : 'Escalate'}
                      </Button>
                    )}
                    {!TERMINAL.has(e.status) && (
                      <Button size="sm" variant="ghost" onClick={() => void doResolve(e.id)}>
                        {isAr ? 'إغلاق' : 'Resolve'}
                      </Button>
                    )}
                    {e.incidentId ? (
                      <Link to={`/cyber?tab=incidents`} style={{ fontSize: 11, marginInlineStart: 8 }}
                            title={isAr ? 'الحادثة المرتبطة' : 'Linked incident'}>
                        {isAr ? `الحادثة #${e.incidentId}` : `INC #${e.incidentId}`}
                      </Link>
                    ) : null}
                    <Link to={`/socops/events/${e.id}`} style={{ fontSize: 11, marginInlineStart: 8 }}>
                      {isAr ? 'تفاصيل' : 'Details'}
                    </Link>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {/* Server-side pager */}
        {page && page.totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', fontSize: 11, color: 'var(--ink-3)' }}>
            <span>{isAr ? 'صفحة' : 'Page'} {page.number + 1} / {page.totalPages} · {page.totalElements} {isAr ? 'سجل' : 'rows'}</span>
            <div style={{ display: 'flex', gap: 6 }}>
              <Button size="sm" variant="ghost" disabled={page.first}
                onClick={() => setPageIdx(p => Math.max(0, p - 1))}>
                {isAr ? '◀ السابق' : '◀ Prev'}
              </Button>
              <Button size="sm" variant="ghost" disabled={page.last}
                onClick={() => setPageIdx(p => p + 1)}>
                {isAr ? 'التالي ▶' : 'Next ▶'}
              </Button>
            </div>
          </div>
        )}
      </Card>
    </Shell>
  );
}
