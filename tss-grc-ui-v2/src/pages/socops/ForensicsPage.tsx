import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Fingerprint, RefreshCw, ShieldX, Gavel } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type ForensicsArtefact, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'legal_hold' | 'compromised' | 'disposal_due';

const INTEGRITY_TONE: Record<string, 'success' | 'warning' | 'danger'> = {
  VERIFIED: 'success', UNKNOWN: 'warning', COMPROMISED: 'danger',
};

export function ForensicsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<ForensicsArtefact> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('all');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.forensics(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    if (filter === 'all') return all;
    if (filter === 'legal_hold')   return all.filter(f => f.legalHold);
    if (filter === 'compromised')  return all.filter(f => f.integrityStatus === 'COMPROMISED');
    if (filter === 'disposal_due') return all.filter(f => f.disposalDueAt
      && new Date(f.disposalDueAt).getTime() < now + 30 * 86400000);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    return {
      total: all.length,
      legalHold: all.filter(f => f.legalHold).length,
      compromised: all.filter(f => f.integrityStatus === 'COMPROMISED').length,
      disposal: all.filter(f => f.disposalDueAt && new Date(f.disposalDueAt).getTime() < now + 30 * 86400000).length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'الأدلة الجنائية الرقمية' : 'Forensics artefacts'} sub={isAr ? 'سلسلة حفظ مع MD5/SHA-256/SHA-512' : 'Chain-of-custody with MD5/SHA-256/SHA-512'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'الأدلة الجنائية الرقمية' : 'Digital forensics artefacts'}
        sub={isAr ? 'سلسلة الحفظ + قابلية الإثبات أمام المحاكم — سلامة موثقة كل تحويل'
                  : 'Chain-of-custody + court-admissibility — integrity verified on every transfer'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Fingerprint} label={isAr ? 'إجمالي الأدلة' : 'Total artefacts'}     value={String(counts.total)}        tone="info" />
        <Kpi Icon={Gavel}       label={isAr ? 'تحت الحجز القانوني' : 'Under legal hold'} value={String(counts.legalHold)}    tone="warning" />
        <Kpi Icon={ShieldX}     label={isAr ? 'سلامة مخترقة' : 'Integrity compromised'} value={String(counts.compromised)}  tone={counts.compromised > 0 ? 'danger' : 'success'} />
        <Kpi Icon={Gavel}       label={isAr ? 'إتلاف قادم ≤٣٠ يوم' : 'Disposal ≤30d'}  value={String(counts.disposal)}     tone={counts.disposal > 0 ? 'warning' : 'success'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'all',          label: isAr ? 'الكل'         : 'All',           count: counts.total },
          { id: 'legal_hold',   label: isAr ? 'حجز قانوني'  : 'Legal hold',    count: counts.legalHold },
          { id: 'compromised',  label: isAr ? 'سلامة مكسورة' : 'Compromised',   count: counts.compromised },
          { id: 'disposal_due', label: isAr ? 'إتلاف قادم'   : 'Disposal due',  count: counts.disposal },
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
              <th>{isAr ? 'رمز الدليل' : 'Artefact'}</th>
              <th>{isAr ? 'القضية' : 'Case'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'الاستحواذ' : 'Acquired'}</th>
              <th>{isAr ? 'الحارس' : 'Custodian'}</th>
              <th>{isAr ? 'المكان' : 'Location'}</th>
              <th>{isAr ? 'البصمة (SHA-256)' : 'Fingerprint (SHA-256)'}</th>
              <th>{isAr ? 'السلامة' : 'Integrity'}</th>
              <th>{isAr ? 'حجز قانوني' : 'Legal hold'}</th>
              <th>{isAr ? 'إتلاف' : 'Disposal'}</th>
            </tr></thead>
            <tbody>{rows.map(f => (
              <tr key={f.id}>
                <td className="mono" style={{ fontSize: 11 }}>
                  <Link to={`/socops/forensics/${f.id}`} style={{ color: 'var(--primary)', textDecoration: 'none', fontWeight: 600 }}>
                    {f.artefactCode}
                  </Link>
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{f.caseCode}</td>
                <td style={{ fontSize: 11 }}>{f.artefactType ?? '—'}</td>
                <td className="mono" style={{ fontSize: 10 }}>{f.acquiredAt?.slice(0, 16).replace('T', ' ')}</td>
                <td style={{ fontSize: 11 }}>{f.custodian}</td>
                <td style={{ fontSize: 10, color: 'var(--ink-3)' }}>{f.location ?? '—'}</td>
                <td className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>{f.sha256 ?? '—'}</td>
                <td><Badge tone={INTEGRITY_TONE[f.integrityStatus] ?? 'neutral'}>{f.integrityStatus}</Badge></td>
                <td>{f.legalHold ? <Badge tone="warning">{isAr ? 'محجوز' : 'On hold'}</Badge> : '—'}</td>
                <td className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{f.disposalDueAt?.slice(0, 10) ?? '—'}</td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
