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
import { useToast } from '../../lib/toast';
import { socopsApi, type MaDueDiligence, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'in_flight' | 'critical_findings' | 'red_verdict' | 'closed';

// Canonical M&A cyber-DD pipeline (single source of truth). The fieldwork
// stage is DUE_DILIGENCE — what the engagement is literally called; the older
// generic 'ASSESSMENT' label is accepted as a synonym so any legacy row still
// places and advances correctly.
const MA_PHASES = ['PRE_LOI', 'PLANNING', 'DISCOVERY', 'DUE_DILIGENCE', 'FINDINGS', 'INTEGRATION', 'CLOSED'] as const;
const MAX_PHASE_IDX = MA_PHASES.length - 1;

const PHASE_LABEL: Record<string, { en: string; ar: string }> = {
  PRE_LOI:       { en: 'Pre-LOI',       ar: 'ما قبل خطاب النوايا' },
  PLANNING:      { en: 'Planning',      ar: 'التخطيط' },
  DISCOVERY:     { en: 'Discovery',     ar: 'الاستكشاف' },
  DUE_DILIGENCE: { en: 'Due diligence', ar: 'العناية الواجبة' },
  ASSESSMENT:    { en: 'Assessment',    ar: 'التقييم' },
  FINDINGS:      { en: 'Findings',      ar: 'الملاحظات' },
  INTEGRATION:   { en: 'Integration',   ar: 'الدمج' },
  CLOSED:        { en: 'Closed',        ar: 'مغلقة' },
};

// Forward-only transitions. FINDINGS may either integrate or close (walk-away).
const MA_NEXT_PHASES: Record<string, string[]> = {
  PRE_LOI:       ['PLANNING'],
  PLANNING:      ['DISCOVERY'],
  DISCOVERY:     ['DUE_DILIGENCE'],
  DUE_DILIGENCE: ['FINDINGS'],
  ASSESSMENT:    ['FINDINGS'],
  FINDINGS:      ['INTEGRATION', 'CLOSED'],
  INTEGRATION:   ['CLOSED'],
  CLOSED:        [],
};

const PHASE_TONE: Record<string, 'neutral' | 'info' | 'warning' | 'success'> = {
  PRE_LOI: 'neutral', PLANNING: 'neutral', DISCOVERY: 'info',
  DUE_DILIGENCE: 'warning', ASSESSMENT: 'warning',
  FINDINGS: 'warning', INTEGRATION: 'info', CLOSED: 'success',
};

const VERDICT_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  GREEN: 'success', YELLOW: 'warning', RED: 'danger', DEAL_BLOCKER: 'danger',
};

const IN_FLIGHT = new Set(['PRE_LOI', 'PLANNING', 'DISCOVERY', 'DUE_DILIGENCE', 'ASSESSMENT', 'FINDINGS', 'INTEGRATION']);

function phaseIndex(phase: string): number {
  const i = (MA_PHASES as readonly string[]).indexOf(phase);
  if (i >= 0) return i;
  if (phase === 'ASSESSMENT') return (MA_PHASES as readonly string[]).indexOf('DUE_DILIGENCE');
  return -1;
}
function phaseLabel(phase: string, isAr: boolean): string {
  const l = PHASE_LABEL[phase];
  return l ? (isAr ? l.ar : l.en) : phase;
}
function nextPhases(phase: string): string[] {
  return MA_NEXT_PHASES[phase] ?? [];
}

export function MaDueDiligencePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<MaDueDiligence> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('in_flight');
  const [errMsg, setErrMsg] = useState<string | null>(null);
  const [moveTarget, setMoveTarget] = useState<MaDueDiligence | null>(null);
  const [toPhase, setToPhase] = useState('');
  const [busy, setBusy] = useState(false);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.maDueDiligence(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  function openMove(m: MaDueDiligence) {
    setMoveTarget(m);
    setToPhase(nextPhases(m.phase)[0] ?? '');
  }
  function cancelMove() { setMoveTarget(null); setToPhase(''); }
  async function applyMove() {
    if (!moveTarget || !toPhase) return;
    setBusy(true);
    try {
      await socopsApi.maTransitionPhase(moveTarget.id, toPhase);
      toast.success(isAr
        ? `${moveTarget.engagementCode}: ${phaseLabel(toPhase, true)}`
        : `${moveTarget.engagementCode} → ${phaseLabel(toPhase, false)}`);
      cancelMove();
      await load();
    } catch (e) {
      toast.danger((e as Error)?.message ?? String(e));
    } finally { setBusy(false); }
  }

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
        sub={isAr ? 'تخطيط → استكشاف → عناية واجبة → ملاحظات → دمج — حُكم لكل صفقة'
                  : 'PLANNING → DISCOVERY → DUE DILIGENCE → FINDINGS → INTEGRATION — verdict per deal'}
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

      {moveTarget && (
        <div style={{ marginBottom: 12 }}>
          <Card padded>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, flexWrap: 'wrap' }}>
              <strong style={{ fontSize: 12 }}>{moveTarget.engagementCode}</strong>
              <Badge tone={PHASE_TONE[moveTarget.phase] ?? 'neutral'}>{phaseLabel(moveTarget.phase, isAr)}</Badge>
              <span style={{ color: 'var(--ink-3)' }}>→</span>
              <select value={toPhase} onChange={e => setToPhase(e.target.value)}
                style={{ padding: '6px 10px', fontSize: 12, fontWeight: 600,
                  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-1)' }}>
                {nextPhases(moveTarget.phase).map(p => (
                  <option key={p} value={p}>{phaseLabel(p, isAr)}</option>
                ))}
              </select>
              <Button size="sm" variant="primary" disabled={busy || !toPhase} onClick={() => void applyMove()}>
                {isAr ? 'تطبيق' : 'Apply'}
              </Button>
              <Button size="sm" variant="ghost" disabled={busy} onClick={cancelMove}>
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr ? 'تحريك مرحلة العناية الواجبة للصفقة' : 'Advance the deal’s due-diligence phase'}
              </span>
            </div>
          </Card>
        </div>
      )}

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
              <th>{isAr ? 'إجراء' : 'Action'}</th>
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
                <td><Badge tone={PHASE_TONE[m.phase] ?? 'neutral'}>{phaseLabel(m.phase, isAr)}</Badge></td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{phaseIndex(m.phase) >= 0 ? phaseIndex(m.phase) : '?'} / {MAX_PHASE_IDX}</td>
                <td className="mono" style={{ fontSize: 10, color: (m.findingsCritical ?? 0) > 0 ? 'var(--danger-fg)' : 'var(--ink-3)' }}>
                  {m.findingsCritical ?? 0}/{m.findingsHigh ?? 0}
                </td>
                <td>{m.cyberRecommendation ? <Badge tone={VERDICT_TONE[m.cyberRecommendation] ?? 'neutral'}>{m.cyberRecommendation}</Badge> : '—'}</td>
                <td>
                  {nextPhases(m.phase).length > 0
                    ? <Button size="sm" variant="secondary" onClick={() => openMove(m)}>{isAr ? 'تحريك' : 'Advance'}</Button>
                    : <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>—</span>}
                </td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
