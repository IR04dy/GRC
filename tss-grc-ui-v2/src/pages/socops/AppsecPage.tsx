import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bug, RefreshCw, AlertOctagon, Clock, GitBranch } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { socopsApi, type AppsecFinding, type PageResp } from '../../lib/socops';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';
type Filter = 'all' | 'critical' | 'high' | 'sla_breached' | 'open';

const SEV_TONE: Record<string, 'danger' | 'warning' | 'info' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'neutral',
};

const STATUS_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  OPEN: 'danger', TRIAGED: 'warning', IN_PROGRESS: 'info', FIXED: 'success',
  SUPPRESSED: 'neutral', ACCEPTED_RISK: 'neutral',
};

const SCAN_TYPE_TONE: Record<string, 'info' | 'warning' | 'danger' | 'neutral'> = {
  SAST: 'info', DAST: 'warning', IAST: 'info', SCA: 'danger', SECRETS: 'danger', IAC: 'neutral',
};

const OPEN = new Set(['OPEN', 'TRIAGED', 'IN_PROGRESS']);

export function AppsecPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [state, setState] = useState<LoadState>('idle');
  const [page, setPage] = useState<PageResp<AppsecFinding> | null>(null);
  const [pageIdx] = useState(0);
  const [filter, setFilter] = useState<Filter>('critical');
  const [errMsg, setErrMsg] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErrMsg(null);
    try { const r = await socopsApi.appsec(pageIdx, 50); setPage(r); setState('ready'); }
    catch (e) { setErrMsg((e as Error)?.message ?? String(e)); setState('error'); }
  }
  useEffect(() => { void load(); /* eslint-disable-next-line */ }, [pageIdx]);

  const rows = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    if (filter === 'all') return all;
    if (filter === 'critical')     return all.filter(f => f.severity === 'CRITICAL' && OPEN.has(f.status));
    if (filter === 'high')         return all.filter(f => f.severity === 'HIGH' && OPEN.has(f.status));
    if (filter === 'open')         return all.filter(f => OPEN.has(f.status));
    if (filter === 'sla_breached') return all.filter(f => OPEN.has(f.status) && f.slaDueAt && new Date(f.slaDueAt).getTime() < now);
    return all;
  }, [page, filter]);

  const counts = useMemo(() => {
    const all = page?.content ?? [];
    const now = Date.now();
    return {
      total: all.length,
      critical: all.filter(f => f.severity === 'CRITICAL' && OPEN.has(f.status)).length,
      high: all.filter(f => f.severity === 'HIGH' && OPEN.has(f.status)).length,
      open: all.filter(f => OPEN.has(f.status)).length,
      slaBreached: all.filter(f => OPEN.has(f.status) && f.slaDueAt && new Date(f.slaDueAt).getTime() < now).length,
    };
  }, [page]);

  return (
    <Shell title={isAr ? 'أمن التطبيقات (AppSec)' : 'Application security findings'} sub={isAr ? 'SAST · DAST · IAST · SCA · secrets · IaC' : 'SAST · DAST · IAST · SCA · secrets · IaC'}>
      <PageHeader eyebrow={isAr ? 'عمليات الأمن السيبراني' : 'SOC Operations'}
        title={isAr ? 'مكتشفات أمن التطبيقات' : 'AppSec findings'}
        sub={isAr ? 'Veracode · Checkmarx · Snyk · SonarQube · GitHub Advanced Security · Semgrep'
                  : 'Veracode · Checkmarx · Snyk · SonarQube · GitHub Advanced Security · Semgrep'}
        actions={<Button variant="ghost" size="sm" onClick={() => void load()}><RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}</Button>} />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={AlertOctagon} label={isAr ? 'حرجة مفتوحة' : 'Open CRITICAL'} value={String(counts.critical)}    tone={counts.critical > 0 ? 'danger' : 'success'} onClick={() => setFilter('critical')} active={filter === 'critical'} />
        <Kpi Icon={Bug}          label={isAr ? 'عالية مفتوحة' : 'Open HIGH'}    value={String(counts.high)}        tone={counts.high > 0 ? 'warning' : 'success'} onClick={() => setFilter('high')} active={filter === 'high'} />
        <Kpi Icon={Clock}        label={isAr ? 'SLA تجاوزت' : 'SLA breached'}   value={String(counts.slaBreached)} tone={counts.slaBreached > 0 ? 'danger' : 'success'} onClick={() => setFilter('sla_breached')} active={filter === 'sla_breached'} />
        <Kpi Icon={GitBranch}    label={isAr ? 'الإجمالي المفتوح' : 'Total open'} value={String(counts.open)}      tone={counts.open > 0 ? 'warning' : 'success'} onClick={() => setFilter('open')} active={filter === 'open'} />
      </div>

      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'critical',     label: isAr ? 'حرجة'        : 'CRITICAL',     count: counts.critical },
          { id: 'high',         label: isAr ? 'عالية'        : 'HIGH',         count: counts.high },
          { id: 'sla_breached', label: isAr ? 'تجاوز SLA'   : 'SLA breached', count: counts.slaBreached },
          { id: 'open',         label: isAr ? 'المفتوحة'   : 'Open',         count: counts.open },
          { id: 'all',          label: isAr ? 'الكل'         : 'All',          count: counts.total },
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
              <th>{isAr ? 'الماسح' : 'Scanner'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'التطبيق' : 'Application'}</th>
              <th>{isAr ? 'الثغرة' : 'Finding'}</th>
              <th>CWE / CVE</th>
              <th>{isAr ? 'الخطورة' : 'Severity'}</th>
              <th>CVSS</th>
              <th>{isAr ? 'الموقع' : 'Location'}</th>
              <th>{isAr ? 'استحقاق SLA' : 'SLA due'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
            </tr></thead>
            <tbody>{rows.map(f => (
              <tr key={f.id}>
                <td><Badge tone="info">{f.scanner}</Badge></td>
                <td><Badge tone={SCAN_TYPE_TONE[f.scanType] ?? 'neutral'}>{f.scanType}</Badge></td>
                <td style={{ fontSize: 11 }}>
                  <strong>{f.applicationName}</strong>
                  {f.branch && <div className="mono" style={{ fontSize: 9, color: 'var(--ink-3)' }}>{f.branch}</div>}
                </td>
                <td style={{ fontSize: 11, maxWidth: 240, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  <Link to={`/socops/appsec/${f.id}`} style={{ color: 'var(--primary)', textDecoration: 'none' }}>
                    {f.findingTitle}
                  </Link>
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{[f.cwe, f.cve].filter(Boolean).join(' / ') || '—'}</td>
                <td><Badge tone={SEV_TONE[f.severity] ?? 'neutral'}>{f.severity}</Badge></td>
                <td className="mono" style={{ fontSize: 10 }}>{f.cvssScore ?? '—'}</td>
                <td className="mono" style={{ fontSize: 9, color: 'var(--ink-3)', maxWidth: 220, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                  {f.filePath ?? '—'}{f.lineNumber ? `:${f.lineNumber}` : ''}
                  {f.componentName && <div>{f.componentName}@{f.componentVersion ?? '—'}</div>}
                </td>
                <td className="mono" style={{ fontSize: 10 }}>{f.slaDueAt?.slice(0, 10) ?? '—'}</td>
                <td><Badge tone={STATUS_TONE[f.status] ?? 'neutral'}>{f.status}</Badge></td>
              </tr>))}</tbody>
          </table>
        )}
      </Card>
    </Shell>
  );
}
