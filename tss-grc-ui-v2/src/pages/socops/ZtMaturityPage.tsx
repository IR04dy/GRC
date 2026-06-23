import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Target, RefreshCw, TrendingUp } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type ZtMaturity } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const STAGE_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
  TRADITIONAL: 'danger',
  INITIAL:     'warning',
  ADVANCED:    'info',
  OPTIMAL:     'success',
};

const STAGE_ORDER: Record<string, number> = {
  TRADITIONAL: 1, INITIAL: 2, ADVANCED: 3, OPTIMAL: 4,
};

export function ZtMaturityPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [rows, setRows] = useState<ZtMaturity[]>([]);
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.ztMaturity(); setRows(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); }, []);

  const counts = useMemo(() => {
    return {
      total: rows.length,
      optimal: rows.filter(r => r.currentStage === 'OPTIMAL').length,
      gap: rows.filter(r => r.targetStage && (STAGE_ORDER[r.targetStage] ?? 0) > (STAGE_ORDER[r.currentStage] ?? 0)).length,
      avgScore: rows.length === 0 ? null
        : Math.round(rows.reduce((s, r) => s + (r.score ?? 0), 0) / rows.length),
    };
  }, [rows]);

  return (
    <Shell title={isAr ? 'نموذج Zero-Trust' : 'Zero Trust maturity'} sub={isAr ? 'CISA ZTMM v2.0 — ٥ ركائز' : 'CISA ZTMM v2.0 — 5 pillars'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'نموذج نضج Zero-Trust' : 'Zero Trust maturity model'}
        sub={isAr ? 'الهوية · الأجهزة · الشبكة · التطبيقات · البيانات'
                  : 'Identity · Devices · Network · Applications · Data'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Target}     label={isAr ? 'تقييمات' : 'Assessments'}      value={String(counts.total)}   tone="info" to="/socops" />
        <Kpi Icon={TrendingUp} label={isAr ? 'في مستوى مثالي' : 'At OPTIMAL'} value={String(counts.optimal)} tone={counts.optimal > 0 ? 'success' : 'warning'} to="/socops" />
        <Kpi Icon={Target}     label={isAr ? 'فجوة عن الهدف' : 'Below target'} value={String(counts.gap)}     tone={counts.gap > 0 ? 'warning' : 'success'} to="/socops" />
        <Kpi Icon={TrendingUp} label={isAr ? 'متوسط النقاط' : 'Avg score'}     value={counts.avgScore == null ? '—' : `${counts.avgScore}`} tone="info" to="/socops" />
      </div>

      <Card>
        {state === 'loading' && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
        {state === 'error' && <div style={{ padding: 32, textAlign: 'center', fontSize: 12 }}><div style={{ color: 'var(--danger-fg)' }}>{errMsg}</div></div>}
        {state === 'ready' && rows.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && rows.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'التقييم' : 'Assessment'}</th>
              <th>{isAr ? 'الركيزة' : 'Pillar'}</th>
              <th>{isAr ? 'المستوى الحالي' : 'Current stage'}</th>
              <th>{isAr ? 'الهدف' : 'Target'}</th>
              <th>{isAr ? 'النقاط' : 'Score'}</th>
              <th>{isAr ? 'الفجوة' : 'Gap'}</th>
              <th>{isAr ? 'آخر تقييم' : 'Assessed at'}</th>
              <th>{isAr ? 'بواسطة' : 'By'}</th>
            </tr></thead>
            <tbody>{rows.map(r => (
              <tr key={r.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/zt-maturity/${r.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {r.assessmentCode}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><Badge tone="info">{r.pillar}</Badge></td>
                <td><Badge tone={STAGE_TONE[r.currentStage] ?? 'neutral'}>{r.currentStage}</Badge></td>
                <td>{r.targetStage ? <Badge tone="neutral">{r.targetStage}</Badge> : '—'}</td>
                <td className="mono" style={{ fontSize: 11 }}>{r.score ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis' }}>{r.gapSummary ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10 }}>{r.assessedAt?.slice(0, 10) ?? '—'}</td>
                <td style={{ fontSize: 10 }}>{r.assessedBy ?? '—'}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
