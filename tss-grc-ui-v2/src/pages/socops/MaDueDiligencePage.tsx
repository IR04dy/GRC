import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Briefcase, RefreshCw, AlertOctagon, CheckCircle2 } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type MaDueDiligence, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'in_flight' | 'critical_findings' | 'red_verdict' | 'closed';

const PHASE_ORDER: Record<string, number> = {
  PRE_LOI: 0, PLANNING: 1, DISCOVERY: 2, ASSESSMENT: 3, FINDINGS: 4, INTEGRATION: 5, CLOSED: 6,
};

const PHASE_TONE: Record<string, 'neutral' | 'info' | 'warning' | 'success'> = {
  PRE_LOI: 'neutral', PLANNING: 'neutral', DISCOVERY: 'info', ASSESSMENT: 'warning',
  FINDINGS: 'warning', INTEGRATION: 'info', CLOSED: 'success',
};

const VERDICT_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  GREEN: 'success', YELLOW: 'warning', RED: 'danger', DEAL_BLOCKER: 'danger',
};

const IN_FLIGHT = new Set(['PRE_LOI', 'PLANNING', 'DISCOVERY', 'ASSESSMENT', 'FINDINGS', 'INTEGRATION']);

export function MaDueDiligencePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<MaDueDiligence> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('in_flight');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.maDueDiligence(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'in_flight')         return all.filter(m => IN_FLIGHT.has(m.phase));
    if (filter === 'critical_findings') return all.filter(m => (m.findingsCritical ?? 0) > 0);
    if (filter === 'red_verdict')       return all.filter(m => m.cyberRecommendation === 'RED' || m.cyberRecommendation === 'DEAL_BLOCKER');
    if (filter === 'closed')            return all.filter(m => m.phase === 'CLOSED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      inFlight: all.filter(m => IN_FLIGHT.has(m.phase)).length,
      criticalFindings: all.filter(m => (m.findingsCritical ?? 0) > 0).length,
      red: all.filter(m => m.cyberRecommendation === 'RED' || m.cyberRecommendation === 'DEAL_BLOCKER').length,
      closed: all.filter(m => m.phase === 'CLOSED').length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'العناية الواجبة (M&A)' : 'M&A cyber due diligence'} sub={isAr ? 'PIF · CMA · صناديق سيادية' : 'PIF · CMA · sovereign-fund driven'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'العناية الواجبة السيبرانية للصفقات' : 'M&A cyber due diligence'}
        sub={isAr ? 'تخطيط → اكتشاف → تقييم → ملاحظات → دمج — حُكم لكل صفقة'
                  : 'PLANNING → DISCOVERY → ASSESSMENT → FINDINGS → INTEGRATION — verdict per deal'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Briefcase}    label={isAr ? 'صفقات قيد العناية' : 'In-flight engagements'} value={String(counts.inFlight)} tone={counts.inFlight > 0 ? 'info' : 'success'} />
        <Kpi Icon={AlertOctagon} label={isAr ? 'حُكم أحمر/مانع' : 'RED / Deal-blocker'}      value={String(counts.red)}      tone={counts.red > 0 ? 'danger' : 'success'} />
        <Kpi Icon={AlertOctagon} label={isAr ? 'ملاحظات حرجة' : 'CRITICAL findings'}        value={String(counts.criticalFindings)} tone={counts.criticalFindings > 0 ? 'danger' : 'success'} />
        <Kpi Icon={CheckCircle2} label={isAr ? 'مغلقة' : 'Closed'}                          value={String(counts.closed)}   tone="neutral" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'in_flight',          label: isAr ? 'قيد التنفيذ'  : 'In-flight',         count: counts.inFlight },
          { id: 'red_verdict',        label: isAr ? 'أحمر'         : 'RED verdict',       count: counts.red },
          { id: 'critical_findings',  label: isAr ? 'حرجة'         : 'Critical findings', count: counts.criticalFindings },
          { id: 'closed',             label: isAr ? 'مغلقة'        : 'Closed',            count: counts.closed },
          { id: 'all',                label: isAr ? 'الكل'         : 'All',               count: counts.total },
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
              <th>{isAr ? 'الشركة المستهدفة' : 'Target'}</th>
              <th>{isAr ? 'نوع الصفقة' : 'Deal type'}</th>
              <th>{isAr ? 'القطاع' : 'Industry'}</th>
              <th>{isAr ? 'الدولة' : 'Country'}</th>
              <th>{isAr ? 'المرحلة' : 'Phase'}</th>
              <th>{isAr ? 'تقدّم' : 'Progress'}</th>
              <th title="Critical/High">{isAr ? 'حرج/عالي' : 'C/H'}</th>
              <th>{isAr ? 'الحكم' : 'Verdict'}</th>
            </tr></thead>
            <tbody>{rows.map(m => (
              <tr key={m.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/ma-due-diligence/${m.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {m.engagementCode}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><strong>{m.targetEntityName}</strong></td>
                <td style={{ fontSize: 11 }}>{m.dealType ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{m.targetIndustry ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{m.targetCountry ?? '—'}</td>
                <td><Badge tone={PHASE_TONE[m.phase] ?? 'neutral'}>{m.phase}</Badge></td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{PHASE_ORDER[m.phase] ?? '?'} / 6</td>
                <td className="mono" style={{ fontSize: 10, color: (m.findingsCritical ?? 0) > 0 ? 'var(--danger-fg)' : 'var(--ink-3)' }}>
                  {m.findingsCritical ?? 0}/{m.findingsHigh ?? 0}
                </td>
                <td>{m.cyberRecommendation ? <Badge tone={VERDICT_TONE[m.cyberRecommendation] ?? 'neutral'}>{m.cyberRecommendation}</Badge> : '—'}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
