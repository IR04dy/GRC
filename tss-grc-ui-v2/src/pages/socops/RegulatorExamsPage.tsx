import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Landmark, RefreshCw, Clock, AlertTriangle, CheckCircle2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type RegulatorExamination, type PageResp } from '../../lib/socops';

/**
 * Regulator examinations register — every SAMA / NCA / SDAIA / CITC /
 * CMA / ZATCA examination, notification through closure. Includes
 * findings rollup so the executive view can answer "where did we get
 * dinged last cycle?" without a single document hunt.
 */
type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'open' | 'response_due' | 'fieldwork' | 'closed';

const STATUS_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  NOTIFIED: 'danger',
  PRE_FIELDWORK: 'warning',
  FIELDWORK: 'warning',
  DRAFT_REPORT: 'info',
  RESPONSE_PENDING: 'warning',
  RESPONSE_SUBMITTED: 'info',
  FINAL_REPORT: 'info',
  CLOSED: 'success',
};

const RATING_TONE: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  SATISFACTORY: 'success',
  ADEQUATE: 'info',
  NEEDS_IMPROVEMENT: 'warning',
  UNSATISFACTORY: 'danger',
};

const TERMINAL = new Set(['CLOSED']);

export function RegulatorExamsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<RegulatorExamination> | null>(null);
  const [pageIdx, setPageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('open');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    try {
      const resp = await socopsApi.regulatorExams(pageIdx, 50);
      setPage(resp);
      setState('ready');
    } catch (e) {
      setErrMsg((e as Error)?.message ?? String(e));
      setState('error');
    }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'open')      return all.filter(e => !TERMINAL.has(e.status));
    if (filter === 'fieldwork') return all.filter(e => e.status === 'FIELDWORK' || e.status === 'PRE_FIELDWORK');
    if (filter === 'closed')    return all.filter(e => TERMINAL.has(e.status));
    if (filter === 'response_due') {
      const cutoff = Date.now() + 14 * 24 * 60 * 60 * 1000;
      return all.filter(e => e.responseDueAt && !e.responseSubmittedAt
        && new Date(e.responseDueAt).getTime() < cutoff);
    }
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const cutoff = Date.now() + 14 * 24 * 60 * 60 * 1000;
    const responseDue = all.filter(e => e.responseDueAt && !e.responseSubmittedAt
      && new Date(e.responseDueAt).getTime() < cutoff).length;
    return {
      total: all.length,
      open: all.filter(e => !TERMINAL.has(e.status)).length,
      fieldwork: all.filter(e => e.status === 'FIELDWORK' || e.status === 'PRE_FIELDWORK').length,
      responseDue,
      closed: all.filter(e => TERMINAL.has(e.status)).length,
      criticalOpen: all.filter(e => !TERMINAL.has(e.status)).reduce((s, e) => s + (e.findingsCritical ?? 0), 0),
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'تفتيشات الجهات التنظيمية' : 'Regulator examinations'}
           sub={isAr ? 'إخطار → عمل ميداني → ردود → إغلاق' : 'Notification → fieldwork → response → closure'}>
      <PageHeader
        eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'تفتيشات الجهات التنظيمية' : 'Regulator examinations'}
        sub={isAr ? 'ساما · NCA · سدايا · CITC · CMA · ZATCA — دورة كاملة'
                  : 'SAMA · NCA · SDAIA · CITC · CMA · ZATCA — full cycle'}
        actions={
          <Button variant="ghost" size="sm" onClick={() => void load()}>
            <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
          </Button>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Landmark}      label={isAr ? 'مفتوحة' : 'Open'}                value={String(counts.open)}         tone={counts.open > 0 ? 'warning' : 'success'} onClick={() => setFilter('open')} active={filter === 'open'} />
        <Kpi Icon={Clock}         label={isAr ? 'ردود قادمة ≤١٤ يوم' : 'Response due ≤14d'} value={String(counts.responseDue)}  tone={counts.responseDue > 0 ? 'warning' : 'success'} onClick={() => setFilter('response_due')} active={filter === 'response_due'} />
        <Kpi Icon={AlertTriangle} label={isAr ? 'ملاحظات حرجة مفتوحة' : 'Open CRITICAL findings'} value={String(counts.criticalOpen)} tone={counts.criticalOpen > 0 ? 'danger' : 'success'} to="/socops" />
        <Kpi Icon={CheckCircle2}  label={isAr ? 'مغلقة' : 'Closed'}                value={String(counts.closed)}       tone="info" onClick={() => setFilter('closed')} active={filter === 'closed'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'open',         label: isAr ? 'المفتوحة'      : 'Open',           count: counts.open },
          { id: 'fieldwork',    label: isAr ? 'في الميدان'    : 'In fieldwork',   count: counts.fieldwork },
          { id: 'response_due', label: isAr ? 'ردود قادمة'    : 'Response due',   count: counts.responseDue },
          { id: 'closed',       label: isAr ? 'مغلقة'         : 'Closed',         count: counts.closed },
          { id: 'all',          label: isAr ? 'الكل'          : 'All',            count: counts.total },
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
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && (
          <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}>
            <div style={{ color: 'var(--danger-fg)', marginBottom: 8 }}>{errMsg ?? 'Error'}</div>
            <Button size="sm" variant="primary" onClick={() => void load()}>{isAr ? 'إعادة المحاولة' : 'Retry'}</Button>
          </div>
        )}
        {state === 'ready' && rows.length === 0 && (
          <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>
        )}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{isAr ? 'الرمز' : 'Code'}</th>
                <th>{isAr ? 'الجهة' : 'Regulator'}</th>
                <th>{isAr ? 'العنوان' : 'Title'}</th>
                <th>{isAr ? 'النوع' : 'Type'}</th>
                <th>{isAr ? 'الإخطار' : 'Notified'}</th>
                <th>{isAr ? 'فترة العمل' : 'Fieldwork'}</th>
                <th>{isAr ? 'استحقاق الرد' : 'Response due'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'ملاحظات (الكل / حرج / عالي)' : 'Findings (T / C / H)'}</th>
                <th>{isAr ? 'التقييم' : 'Rating'}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(e => (
                <tr key={e.id}>
                  <td className="mono" style={{ fontSize: 11 }}>
                    <Link to={`/socops/regulator-exams/${e.id}`}
                          style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                      {e.examinationCode}
                    </Link>
                  </td>
                  <td>
                    <Badge tone="info">{e.regulator}</Badge>
                    {e.relatedFramework && <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>{e.relatedFramework}</div>}
                  </td>
                  <td style={{ fontSize: 11 }}>
                    <strong>{isAr ? (e.titleAr ?? e.titleEn) : e.titleEn}</strong>
                    {e.leadExaminerName && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{e.leadExaminerName}</div>}
                  </td>
                  <td style={{ fontSize: 11 }}>{e.examinationType}</td>
                  <td className="mono" style={{ fontSize: 10 }}>{e.notifiedAt?.slice(0, 10) ?? '—'}</td>
                  <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {e.fieldworkStartAt?.slice(0, 10) ?? '—'} → {e.fieldworkEndAt?.slice(0, 10) ?? '—'}
                  </td>
                  <td className="mono" style={{ fontSize: 10 }}>
                    {e.responseDueAt
                      ? <span style={{ color: e.responseSubmittedAt ? 'var(--ink-3)' : 'var(--warning-fg)' }}>{e.responseDueAt.slice(0, 10)}</span>
                      : '—'}
                    {e.responseSubmittedAt && <Badge tone="success" className="ms-1">{isAr ? 'مُقدّم' : 'Sent'}</Badge>}
                  </td>
                  <td><Badge tone={STATUS_TONE[e.status] ?? 'neutral'}>{e.status}</Badge></td>
                  <td className="mono" style={{ fontSize: 10 }}>
                    {e.findingsTotal ?? 0} / <span style={{ color: 'var(--danger-fg)' }}>{e.findingsCritical ?? 0}</span> / <span style={{ color: 'var(--warning-fg)' }}>{e.findingsHigh ?? 0}</span>
                  </td>
                  <td>
                    {e.overallRating
                      ? <Badge tone={RATING_TONE[e.overallRating] ?? 'neutral'}>{e.overallRating}</Badge>
                      : <span style={{ color: 'var(--ink-3)', fontSize: 11 }}>—</span>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}

        {page && page.totalPages > 1 && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '8px 12px', fontSize: 11, color: 'var(--ink-3)' }}>
            <span>{isAr ? 'صفحة' : 'Page'} {page.number + 1} / {page.totalPages} · {page.totalElements} {isAr ? 'سجل' : 'rows'}</span>
            <div style={{ display: 'flex', gap: 6 }}>
              <Button size="sm" variant="ghost" disabled={page.first} onClick={() => setPageIdx(p => Math.max(0, p - 1))}>{isAr ? '◀ السابق' : '◀ Prev'}</Button>
              <Button size="sm" variant="ghost" disabled={page.last} onClick={() => setPageIdx(p => p + 1)}>{isAr ? 'التالي ▶' : 'Next ▶'}</Button>
            </div>
          </div>
        )}
      </Card>
    </Shell>
  );
}
