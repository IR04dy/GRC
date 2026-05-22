import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { GraduationCap, RefreshCw, MailCheck, MousePointerClick, Repeat } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type AwarenessCampaign, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'running' | 'completed' | 'planned';

const STATUS_TONE: Record<string, 'success' | 'info' | 'warning' | 'neutral'> = {
  RUNNING: 'success', COMPLETED: 'info', PLANNED: 'warning', CANCELLED: 'neutral',
};

const TYPE_TONE: Record<string, 'danger' | 'info' | 'success' | 'neutral'> = {
  PHISH_SIM: 'danger', TRAINING: 'info', NEWSLETTER: 'neutral', QUIZ: 'success',
};

export function AwarenessPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<AwarenessCampaign> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.awareness(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all')       return all;
    if (filter === 'running')   return all.filter(c => c.status === 'RUNNING');
    if (filter === 'completed') return all.filter(c => c.status === 'COMPLETED');
    if (filter === 'planned')   return all.filter(c => c.status === 'PLANNED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      running: all.filter(c => c.status === 'RUNNING').length,
      completed: all.filter(c => c.status === 'COMPLETED').length,
      planned: all.filter(c => c.status === 'PLANNED').length,
      repeatOff: all.reduce((s, c) => s + (c.repeatOffenderCount ?? 0), 0),
      avgPassRate: (() => {
        const withRate = all.filter(c => c.passRatePercent != null);
        if (withRate.length === 0) return null;
        return Math.round(withRate.reduce((s, c) => s + Number(c.passRatePercent ?? 0), 0) / withRate.length);
      })(),
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'التوعية والتصيد المحاكي' : 'Awareness & phishing simulations'} sub={isAr ? 'حملات NCA الموجّهة' : 'NCA-aligned awareness programmes'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'التوعية الأمنية' : 'Security awareness'}
        sub={isAr ? 'محاكاة تصيد · تدريب · معدلات نجاح · متابعة المتكررين'
                  : 'Phishing sims · training · pass rates · repeat-offender tracking'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={GraduationCap}      label={isAr ? 'حملات نشطة' : 'Running campaigns'}    value={String(counts.running)}     tone="info" />
        <Kpi Icon={MailCheck}          label={isAr ? 'مكتملة' : 'Completed'}                value={String(counts.completed)}   tone="info" />
        <Kpi Icon={MousePointerClick}  label={isAr ? 'متوسط النجاح %' : 'Avg pass rate %'} value={counts.avgPassRate == null ? '—' : `${counts.avgPassRate}%`} tone="info" />
        <Kpi Icon={Repeat}             label={isAr ? 'متكرر فشل' : 'Repeat offenders'}     value={String(counts.repeatOff)}   tone={counts.repeatOff > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',       label: isAr ? 'الكل'      : 'All',       count: counts.total },
          { id: 'running',   label: isAr ? 'تعمل'     : 'Running',   count: counts.running },
          { id: 'planned',   label: isAr ? 'مخططة'   : 'Planned',   count: counts.planned },
          { id: 'completed', label: isAr ? 'مكتملة'  : 'Completed', count: counts.completed },
        ] as { id: Filter; label: string; count: number }[]).map(td => (
          <button key={td.id} onClick={() => setFilter(td.id)} style={{ padding: '8px 12px', fontSize: 12, fontWeight: 600,
            border: `1px solid ${filter === td.id ? 'var(--primary)' : 'var(--border)'}`,
            background: filter === td.id ? 'var(--primary-50)' : 'var(--surface-1)',
            borderRadius: 'var(--r-sm)', cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6 }}>
            {td.label}<Badge tone={filter === td.id ? 'primary' : 'neutral'}>{td.count}</Badge>
          </button>
        ))}
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}><div style={{ color: 'var(--danger-fg)' }}>{errMsg}</div></div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الجمهور' : 'Audience'}</th>
              <th>{isAr ? 'الفترة' : 'Period'}</th>
              <th>{isAr ? 'مُسلَّم' : 'Delivered'}</th>
              <th>{isAr ? 'نقر' : 'Clicked'}</th>
              <th>{isAr ? 'مُبلَّغ' : 'Reported'}</th>
              <th>{isAr ? 'مكتمل' : 'Completed'}</th>
              <th>{isAr ? '% نجاح' : '% pass'}</th>
              <th>{isAr ? 'متكرر' : 'Repeat'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/awareness/${c.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {c.campaignCode}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><strong>{isAr ? (c.titleAr ?? c.titleEn) : c.titleEn}</strong></td>
                <td><Badge tone={TYPE_TONE[c.campaignType] ?? 'neutral'}>{c.campaignType}</Badge></td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.targetAudience ?? '—'}{c.targetUserCount > 0 && <div className="mono">{c.targetUserCount}</div>}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.startedAt?.slice(0, 10) ?? '—'} → {c.endedAt?.slice(0, 10) ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.deliveredCount}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--danger-fg)' }}>{c.clickedCount}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--success-fg)' }}>{c.reportedCount}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.completedCount}</td>
                <td className="mono" style={{ fontSize: 10 }}>{c.passRatePercent == null ? '—' : `${c.passRatePercent}%`}</td>
                <td className="mono" style={{ fontSize: 10, color: c.repeatOffenderCount > 0 ? 'var(--warning-fg)' : 'var(--ink-3)' }}>{c.repeatOffenderCount}</td>
                <td><Badge tone={STATUS_TONE[c.status] ?? 'neutral'}>{c.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
