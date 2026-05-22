import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Package, RefreshCw, AlertOctagon, ArrowUpCircle } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type SbomComponent, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'with_cves' | 'critical_cve' | 'upgrade_available';

const SEV_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

export function SbomPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<SbomComponent> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('with_cves');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.sbom(pageIdx, 100); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    if (filter === 'all') return all;
    if (filter === 'with_cves')         return all.filter(c => c.hasKnownCves);
    if (filter === 'critical_cve')      return all.filter(c => c.hasKnownCves && c.highestCveSeverity === 'CRITICAL');
    if (filter === 'upgrade_available') return all.filter(c => c.upgradeAvailable);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    return {
      total: all.length,
      withCves: all.filter(c => c.hasKnownCves).length,
      criticalCve: all.filter(c => c.hasKnownCves && c.highestCveSeverity === 'CRITICAL').length,
      upgrade: all.filter(c => c.upgradeAvailable).length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'قائمة مكوّنات البرمجيات (SBOM)' : 'Software Bill of Materials'} sub={isAr ? 'CISA SBOM Lifecycle · purl' : 'CISA SBOM Lifecycle · purl spec'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'قائمة مكوّنات البرمجيات (SBOM)' : 'Software Bill of Materials'}
        sub={isAr ? 'مكتبات وحزم وتراخيص — مع ربط CVE وترقيات متاحة'
                  : 'Components · libraries · licences with CVE + upgrade tracking'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Package}      label={isAr ? 'الإجمالي' : 'Total components'} value={String(counts.total)}    tone="info" />
        <Kpi Icon={AlertOctagon} label={isAr ? 'بثغرات معروفة' : 'With known CVEs'} value={String(counts.withCves)} tone={counts.withCves > 0 ? 'danger' : 'success'} />
        <Kpi Icon={AlertOctagon} label={isAr ? 'بحرج' : 'CRITICAL CVE'}          value={String(counts.criticalCve)} tone={counts.criticalCve > 0 ? 'danger' : 'success'} />
        <Kpi Icon={ArrowUpCircle} label={isAr ? 'ترقية متاحة' : 'Upgrade available'} value={String(counts.upgrade)} tone={counts.upgrade > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'with_cves',         label: isAr ? 'بثغرات'      : 'With CVEs',       count: counts.withCves },
          { id: 'critical_cve',      label: isAr ? 'حرج'          : 'CRITICAL CVE',    count: counts.criticalCve },
          { id: 'upgrade_available', label: isAr ? 'ترقية متاحة' : 'Upgrade avail.',  count: counts.upgrade },
          { id: 'all',               label: isAr ? 'الكل'         : 'All',             count: counts.total },
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
              <th>{isAr ? 'التطبيق' : 'Application'}</th>
              <th>{isAr ? 'المكوّن' : 'Component'}</th>
              <th>{isAr ? 'الإصدار' : 'Version'}</th>
              <th>{isAr ? 'النظام' : 'Ecosystem'}</th>
              <th>{isAr ? 'المورد' : 'Supplier'}</th>
              <th>{isAr ? 'الترخيص' : 'License'}</th>
              <th>{isAr ? 'CVE' : 'CVE'}</th>
              <th>{isAr ? 'ترقية' : 'Upgrade'}</th>
              <th>{isAr ? 'آخر مسح' : 'Last scan'}</th>
            </tr></thead>
            <tbody>{rows.map(c => (
              <tr key={c.id}>
                <td style={{ fontSize: 11 }}>{c.applicationName}</td>
                <td style={{ fontSize: 11 }}>
                  <Link to={`/socops/sbom/${c.id}`} style={{ color: 'var(--primary)', textDecoration: 'none' }}>
                    <strong>{c.componentName}</strong>
                  </Link>
                  {c.purl && <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{c.purl}</div>}
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{c.componentVersion ?? '—'}</td>
                <td style={{ fontSize: 10 }}>{c.packageEcosystem ?? '—'}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.supplier ?? '—'}</td>
                <td style={{ fontSize: 10 }}>{c.license ?? '—'}</td>
                <td>
                  {c.hasKnownCves
                    ? <Badge tone={SEV_TONE[c.highestCveSeverity ?? 'MEDIUM'] ?? 'neutral'}>{c.highestCveSeverity ?? 'CVE'}</Badge>
                    : <Badge tone="success">{isAr ? 'لا توجد' : 'None'}</Badge>}
                </td>
                <td>{c.upgradeAvailable ? <Badge tone="warning">{isAr ? 'متاح' : 'Available'}</Badge> : <span style={{ color: 'var(--ink-3)', fontSize: 11 }}>—</span>}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{c.lastScannedAt?.slice(0, 10) ?? '—'}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
