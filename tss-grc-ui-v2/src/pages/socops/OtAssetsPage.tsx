import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Cog, RefreshCw, AlertTriangle, Network, Activity } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type OtAsset, type PageResp } from '../../lib/socops';

/**
 * OT / ICS asset inventory — Purdue-aware register. Built for the Aramco
 * / SABIC / Ma'aden / SEC / NWC reality where OT cybersecurity sits under
 * NCA's OTCC framework and IEC 62443 / 61511. Surfaces remote-access
 * flag, SIL rating, criticality, and last-patched-at — the four
 * questions an OT auditor opens with.
 */
type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'critical' | 'remote' | 'unpatched' | 'safety';

const CRITICALITY_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger',
  HIGH: 'warning',
  MEDIUM: 'info',
  LOW: 'neutral',
};

const PURDUE_LABELS: Record<number, { en: string; ar: string }> = {
  0: { en: 'L0 Physical', ar: 'L0 المرحلة الفيزيائية' },
  1: { en: 'L1 Basic control', ar: 'L1 التحكم الأساسي' },
  2: { en: 'L2 Supervisory', ar: 'L2 الإشراف' },
  3: { en: 'L3 Operations', ar: 'L3 العمليات' },
  4: { en: 'L4 Site biz', ar: 'L4 أعمال الموقع' },
  5: { en: 'L5 Enterprise', ar: 'L5 المؤسسة' },
};

const STALE_PATCH_DAYS = 180;

export function OtAssetsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<OtAsset> | null>(null);
  const [pageIdx, setPageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading');
    setErrMsg(null);
    try {
      const resp = await socopsApi.otAssets(pageIdx, 50);
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
    if (filter === 'critical')  return all.filter(a => a.criticality === 'CRITICAL' || a.criticality === 'HIGH');
    if (filter === 'remote')    return all.filter(a => a.remoteAccessEnabled);
    if (filter === 'safety')    return all.filter(a => a.safetyIntegrityLevel != null && a.safetyIntegrityLevel !== '');
    if (filter === 'unpatched') {
      const cutoff = Date.now() - STALE_PATCH_DAYS * 24 * 60 * 60 * 1000;
      return all.filter(a => !a.lastPatchedAt || new Date(a.lastPatchedAt).getTime() < cutoff);
    }
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const cutoff = Date.now() - STALE_PATCH_DAYS * 24 * 60 * 60 * 1000;
    return {
      total: all.length,
      critical: all.filter(a => a.criticality === 'CRITICAL' || a.criticality === 'HIGH').length,
      remote: all.filter(a => a.remoteAccessEnabled).length,
      unpatched: all.filter(a => !a.lastPatchedAt || new Date(a.lastPatchedAt).getTime() < cutoff).length,
      safety: all.filter(a => a.safetyIntegrityLevel != null && a.safetyIntegrityLevel !== '').length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'أصول التشغيل (OT/ICS)' : 'OT / ICS asset inventory'}
           sub={isAr ? 'بحسب نموذج Purdue · NCA OTCC · IEC 62443' : 'Purdue-level register · NCA OTCC · IEC 62443'}>
      <PageHeader
        eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'أصول التحكم الصناعي (OT/ICS)' : 'OT / ICS asset inventory'}
        sub={isAr ? 'Purdue 0–5 · سلامة SIL · بروتوكولات صناعية · وصول عن بُعد'
                  : 'Purdue 0–5 · Safety Integrity Levels · Industrial protocols · Remote-access flag'}
        actions={
          <Button variant="ghost" size="sm" onClick={() => void load()}>
            <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
          </Button>
        }
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Cog}            label={isAr ? 'إجمالي الأصول' : 'Total assets'}                value={String(counts.total)}    tone="info" />
        <Kpi Icon={AlertTriangle}  label={isAr ? 'حرجة / عالية' : 'CRITICAL / HIGH'}              value={String(counts.critical)} tone={counts.critical > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Network}        label={isAr ? 'وصول عن بُعد مفعّل' : 'Remote access enabled'} value={String(counts.remote)}   tone={counts.remote > 0 ? 'warning' : 'success'} />
        <Kpi Icon={Activity}       label={isAr ? 'بدون ترقيع ≥٦ أشهر' : 'Unpatched ≥6 months'}    value={String(counts.unpatched)} tone={counts.unpatched > 0 ? 'danger' : 'success'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',       label: isAr ? 'الكل'           : 'All',                count: counts.total },
          { id: 'critical',  label: isAr ? 'حرجة/عالية'    : 'CRITICAL / HIGH',    count: counts.critical },
          { id: 'remote',    label: isAr ? 'وصول عن بُعد' : 'Remote-access on',   count: counts.remote },
          { id: 'unpatched', label: isAr ? 'بدون ترقيع'    : 'Unpatched',          count: counts.unpatched },
          { id: 'safety',    label: isAr ? 'بسلامة SIL'    : 'Safety-rated (SIL)', count: counts.safety },
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
                <th>{isAr ? 'العنوان' : 'Asset'}</th>
                <th>{isAr ? 'مستوى Purdue' : 'Purdue'}</th>
                <th>{isAr ? 'الفئة' : 'Class'}</th>
                <th>{isAr ? 'المزوّد / الطراز' : 'Vendor / model'}</th>
                <th>{isAr ? 'الموقع' : 'Site'}</th>
                <th>{isAr ? 'الأهمية' : 'Criticality'}</th>
                <th>{isAr ? 'SIL' : 'SIL'}</th>
                <th>{isAr ? 'وصول عن بُعد' : 'Remote'}</th>
                <th>{isAr ? 'آخر ترقيع' : 'Last patched'}</th>
              </tr>
            </thead>
            <tbody>
              {rows.map(a => (
                <tr key={a.id}>
                  <td className="mono" style={{ fontSize: 11 }}>
                    <Link to={`/socops/ot-assets/${a.id}`}
                          style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                      {a.assetCode}
                    </Link>
                  </td>
                  <td style={{ fontSize: 11 }}>
                    <strong>{isAr ? (a.titleAr ?? a.titleEn) : a.titleEn}</strong>
                    {a.processFunction && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{a.processFunction}</div>}
                  </td>
                  <td style={{ fontSize: 10 }}>
                    <Badge tone="info">{isAr ? PURDUE_LABELS[a.purdueLevel]?.ar : PURDUE_LABELS[a.purdueLevel]?.en}</Badge>
                  </td>
                  <td style={{ fontSize: 11 }}>{a.assetClass}</td>
                  <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {a.vendor ?? '—'}
                    {a.model && <div className="mono">{a.model}</div>}
                  </td>
                  <td style={{ fontSize: 11 }}>
                    {a.siteName ?? a.siteCode ?? '—'}
                    {a.networkSegment && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{a.networkSegment}</div>}
                  </td>
                  <td><Badge tone={CRITICALITY_TONE[a.criticality] ?? 'neutral'}>{a.criticality}</Badge></td>
                  <td>
                    {a.safetyIntegrityLevel
                      ? <Badge tone="warning">{a.safetyIntegrityLevel}</Badge>
                      : <span style={{ color: 'var(--ink-3)', fontSize: 11 }}>—</span>}
                  </td>
                  <td>
                    {a.remoteAccessEnabled
                      ? <Badge tone="warning">{isAr ? 'مفعّل' : 'ON'}</Badge>
                      : <Badge tone="success">{isAr ? 'مغلق' : 'OFF'}</Badge>}
                  </td>
                  <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {a.lastPatchedAt ? a.lastPatchedAt.slice(0, 10) : '—'}
                    {a.patchWindow && <div style={{ fontSize: 9 }}>{a.patchWindow}</div>}
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
