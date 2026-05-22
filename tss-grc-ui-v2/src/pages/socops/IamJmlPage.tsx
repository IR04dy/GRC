import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Users, RefreshCw, ShieldAlert, UserCheck, UserX } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type IamJmlEvent, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'sod_violations' | 'privileged' | 'pending' | 'completed';

const TYPE_TONE: Record<string, 'success' | 'info' | 'danger' | 'warning' | 'neutral'> = {
  JOINER: 'success', MOVER: 'info', LEAVER: 'danger',
  PRIVILEGED_GRANT: 'warning', PRIVILEGED_REVOKE: 'info', RECERTIFICATION: 'neutral',
};

const OUTCOME_TONE: Record<string, 'warning' | 'success' | 'info' | 'danger' | 'neutral'> = {
  PENDING: 'warning', APPROVED: 'info', COMPLETED: 'success', REJECTED: 'danger', CANCELLED: 'neutral',
};

export function IamJmlPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<IamJmlEvent> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('sod_violations');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.iamJml(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all')            return all;
    if (filter === 'sod_violations') return all.filter(e => e.sodViolationDetected);
    if (filter === 'privileged')     return all.filter(e => e.privileged);
    if (filter === 'pending')        return all.filter(e => e.outcome === 'PENDING');
    if (filter === 'completed')      return all.filter(e => e.outcome === 'COMPLETED');
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      sod: all.filter(e => e.sodViolationDetected).length,
      privileged: all.filter(e => e.privileged).length,
      pending: all.filter(e => e.outcome === 'PENDING').length,
      completed: all.filter(e => e.outcome === 'COMPLETED').length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'دورة حياة الهويات (JML)' : 'IAM joiner-mover-leaver'} sub={isAr ? 'ECC-2 ضوابط ٢ — سجل من-كان-يملك-ماذا' : 'ECC-2 Family 2 — who-had-what audit'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'دورة حياة الهويات والصلاحيات' : 'IAM joiner-mover-leaver + privileged access'}
        sub={isAr ? 'كل تغيير صلاحية موثّق — يكشف فصل المهام تلقائياً'
                  : 'Every entitlement change documented — SoD violations flagged automatically'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={ShieldAlert} label={isAr ? 'مخالفات SoD' : 'SoD violations'}        value={String(counts.sod)}        tone={counts.sod > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Users}       label={isAr ? 'صلاحيات حرجة' : 'Privileged events'}    value={String(counts.privileged)} tone={counts.privileged > 0 ? 'warning' : 'neutral'} />
        <Kpi Icon={UserCheck}   label={isAr ? 'قيد الانتظار' : 'Pending'}              value={String(counts.pending)}    tone={counts.pending > 0 ? 'warning' : 'success'} />
        <Kpi Icon={UserX}       label={isAr ? 'مكتمل' : 'Completed'}                    value={String(counts.completed)}  tone="info" />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'sod_violations', label: isAr ? 'مخالفات SoD'  : 'SoD violations', count: counts.sod },
          { id: 'privileged',     label: isAr ? 'حرجة'         : 'Privileged',     count: counts.privileged },
          { id: 'pending',        label: isAr ? 'قيد الانتظار' : 'Pending',         count: counts.pending },
          { id: 'completed',      label: isAr ? 'مكتمل'        : 'Completed',       count: counts.completed },
          { id: 'all',            label: isAr ? 'الكل'         : 'All',             count: counts.total },
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
              <th>{isAr ? 'الوقت' : 'When'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الشخص' : 'Subject'}</th>
              <th>{isAr ? 'القسم/الوظيفة' : 'Dept / Title'}</th>
              <th>{isAr ? 'النظام' : 'Source → target'}</th>
              <th>{isAr ? 'الدور/الصلاحية' : 'Role / entitlement'}</th>
              <th>{isAr ? 'صلاحية حرجة' : 'Priv.'}</th>
              <th>SoD</th>
              <th>{isAr ? 'النتيجة' : 'Outcome'}</th>
            </tr></thead>
            <tbody>{rows.map(e => (
              <tr key={e.id}>
                <td className="mono" style={{ fontSize: 10 }}>
                  <Link to={`/socops/iam-jml/${e.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {e.eventCode}
                  </Link>
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{e.occurredAt?.slice(0, 16).replace('T', ' ')}</td>
                <td><Badge tone={TYPE_TONE[e.eventType] ?? 'neutral'}>{e.eventType}</Badge></td>
                <td style={{ fontSize: 11 }}>
                  <strong>{e.subjectUsername ?? '—'}</strong>
                  {e.subjectEmail && <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{e.subjectEmail}</div>}
                </td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                  {e.subjectDepartment ?? '—'}{e.subjectJobTitle && <div>{e.subjectJobTitle}</div>}
                </td>
                <td style={{ fontSize: 10 }}>{e.sourceSystem}{e.targetSystem && <> → {e.targetSystem}</>}</td>
                <td style={{ fontSize: 10 }}>{e.roleOrEntitlement ?? '—'}</td>
                <td>{e.privileged ? <Badge tone="warning">{isAr ? 'نعم' : 'Priv'}</Badge> : '—'}</td>
                <td>
                  {e.sodViolationDetected
                    ? <Badge tone="danger">{isAr ? 'مخالفة' : 'Violation'}</Badge>
                    : <Badge tone="success">{isAr ? 'سليم' : 'OK'}</Badge>}
                  {e.sodViolationDetail && <div style={{ fontSize: 9, color: 'var(--danger-fg)' }}>{e.sodViolationDetail.slice(0, 60)}</div>}
                </td>
                <td><Badge tone={OUTCOME_TONE[e.outcome] ?? 'neutral'}>{e.outcome}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
