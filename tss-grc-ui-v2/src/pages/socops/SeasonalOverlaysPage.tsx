import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarClock, RefreshCw, Flag, AlertCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type SeasonalOverlay } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'active' | 'upcoming';

export function SeasonalOverlaysPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [rows, setRows] = useState<SeasonalOverlay[]>([]);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.allOverlays(); setRows(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); }, []);

  const visible = useMemo(() => {
    const now = Date.now();
    if (filter === 'all') return rows;
    if (filter === 'active')   return rows.filter(o => o.isActive
      || (new Date(o.startsAt).getTime() <= now && new Date(o.endsAt).getTime() >= now));
    if (filter === 'upcoming') return rows.filter(o => new Date(o.startsAt).getTime() > now
      && new Date(o.startsAt).getTime() < now + 90 * 86400000);
    return rows;
  }, [rows, filter]);

  const counts = useMemo(() => {
    const now = Date.now();
    return {
      total: rows.length,
      active: rows.filter(o => o.isActive || (new Date(o.startsAt).getTime() <= now && new Date(o.endsAt).getTime() >= now)).length,
      upcoming: rows.filter(o => new Date(o.startsAt).getTime() > now && new Date(o.startsAt).getTime() < now + 90 * 86400000).length,
      tighteningNow: rows.filter(o => (o.autoTightenKris || o.autoTightenSlas) && (o.isActive || (new Date(o.startsAt).getTime() <= now && new Date(o.endsAt).getTime() >= now))).length,
    };
  }, [rows]);

  return (
    <Shell title={isAr ? 'النوافذ الموسمية' : 'Seasonal overlays'} sub={isAr ? 'حج · عمرة · رمضان · اليوم الوطني' : 'Hajj · Umrah · Ramadan · National Day'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'النوافذ الموسمية' : 'Seasonal threat overlays'}
        sub={isAr ? 'يشد F8 عتبات KRI/SLA أثناء النوافذ الحرجة' : 'F8 auto-tightens KRI/SLA during critical windows'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={CalendarClock} label={isAr ? 'نشطة الآن' : 'Active now'}             value={String(counts.active)}        tone={counts.active > 0 ? 'warning' : 'success'} onClick={() => setFilter('active')} active={filter === 'active'} />
        <Kpi Icon={AlertCircle}   label={isAr ? 'يشد العتبات' : 'Tightening KRI/SLA'} value={String(counts.tighteningNow)} tone={counts.tighteningNow > 0 ? 'warning' : 'success'} to="/risk/kri/dashboard" />
        <Kpi Icon={Flag}          label={isAr ? 'قادمة خلال ٩٠ يوم' : 'Upcoming ≤90d'}  value={String(counts.upcoming)}      tone="info" onClick={() => setFilter('upcoming')} active={filter === 'upcoming'} />
        <Kpi Icon={CalendarClock} label={isAr ? 'الإجمالي' : 'Total catalog'}            value={String(counts.total)}         tone="neutral" onClick={() => setFilter('all')} active={filter === 'all'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',      label: isAr ? 'الكل'        : 'All',         count: counts.total },
          { id: 'active',   label: isAr ? 'نشطة الآن'  : 'Active now',  count: counts.active },
          { id: 'upcoming', label: isAr ? 'قادمة'       : 'Upcoming',    count: counts.upcoming },
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
        {state === 'ready' && visible.length === 0 && <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>—</div>}
        {state === 'ready' && visible.length > 0 && (
          <table className="tbl" style={{ width: '100%' }}>
            <thead><tr>
              <th>{isAr ? 'الرمز' : 'Code'}</th>
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'البداية' : 'Starts'}</th>
              <th>{isAr ? 'النهاية' : 'Ends'}</th>
              <th>{isAr ? 'هجري' : 'Hijri'}</th>
              <th>{isAr ? 'ملف التهديد' : 'Threat profile'}</th>
              <th>KRI</th>
              <th>SLA</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{visible.map(o => (
              <tr key={o.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/seasonal-overlays/${o.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {o.overlayCode}
                  </Link>
                </td>
                <td style={{ fontSize: 11 }}><strong>{isAr ? (o.titleAr ?? o.titleEn) : o.titleEn}</strong></td>
                <td className="mono" style={{ fontSize: 10 }}>{o.startsAt?.slice(0, 10)}</td>
                <td className="mono" style={{ fontSize: 10 }}>{o.endsAt?.slice(0, 10)}</td>
                <td style={{ fontSize: 10 }}>{o.hijriLabel ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)', maxWidth: 280, overflow: 'hidden', textOverflow: 'ellipsis' }}>{o.threatProfile ?? '—'}</td>
                <td>{o.autoTightenKris ? <Badge tone="warning">{isAr ? 'مشدّد' : 'Tight'}</Badge> : '—'}</td>
                <td>{o.autoTightenSlas ? <Badge tone="warning">{isAr ? 'مشدّد' : 'Tight'}</Badge> : '—'}</td>
                <td>{o.isActive ? <Badge tone="warning">{isAr ? 'نشط' : 'Active'}</Badge> : <Badge tone="neutral">{isAr ? 'خامل' : 'Idle'}</Badge>}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
