import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Link2, AlertOctagon, CheckCircle2, ShieldCheck, ChevronRight, ChevronDown, RefreshCw, Hash,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listChainItems, getChainReport, verifyChain,
  CHAIN_STATUS_TONE, shortHash,
  type ChainItemSummary, type ChainReport, type ChainStatus,
} from '../../lib/evidenceChain';

type TabId = 'all' | 'VALID' | 'BROKEN' | 'UNVERIFIED';

export function EvidenceChainPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<ChainItemSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [reports, setReports] = useState<Map<number, ChainReport>>(new Map());
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const items = await listChainItems();
      setRows(items);
    } catch { toast.danger(t('echLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => ({
    total:      rows.length,
    valid:      rows.filter(r => r.chainStatus === 'VALID').length,
    broken:     rows.filter(r => r.chainStatus === 'BROKEN').length,
    unverified: rows.filter(r => r.chainStatus === 'UNVERIFIED' || !r.chainStatus).length,
  }), [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => (r.chainStatus ?? 'UNVERIFIED') === tab as ChainStatus);
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',        label: `${t('echTabAll')} (${rows.length})` },
    { id: 'VALID',      label: `${t('echTab_VALID')} (${stats.valid})` },
    { id: 'BROKEN',     label: `${t('echTab_BROKEN')} (${stats.broken})` },
    { id: 'UNVERIFIED', label: `${t('echTab_UNVERIFIED')} (${stats.unverified})` },
  ];

  async function onExpand(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) {
      n.delete(id); setExpanded(n); return;
    }
    n.add(id); setExpanded(n);
    if (!reports.has(id)) {
      try {
        const rep = await getChainReport(id);
        const m = new Map(reports); m.set(id, rep); setReports(m);
      } catch { toast.danger(t('echLoadFailed')); }
    }
  }

  async function onVerify(id: number) {
    setBusyId(id);
    try {
      const rep = await verifyChain(id);
      const m = new Map(reports); m.set(id, rep); setReports(m);
      await reload();
      toast.success(rep.brokenCount === 0
        ? t('echVerifyPassed')
        : `${t('echVerifyFailed')} — ${rep.brokenCount} ${t('echBrokenLinks')}`);
    } catch { toast.danger(t('echActionFailed')); }
    finally { setBusyId(null); }
  }

  return (
    <Shell title={t('echPageTitle')} sub={t('echPageSub')}>
      <PageHeader
        eyebrow={t('navAudit')}
        title={t('echPageTitle')}
        sub={t('echPageSub')}
        actions={<Button variant="ghost" Icon={RefreshCw} onClick={() => void reload()}>{t('echRefresh')}</Button>}
      />

      <div className="kpi-grid">
        <Kpi Icon={Link2}         label={t('echKpiTotal')}      value={stats.total}      tone="primary" onClick={() => setTab('all')}        active={tab === 'all'} />
        <Kpi Icon={CheckCircle2}  label={t('echKpiValid')}      value={stats.valid}      tone="success" onClick={() => setTab('VALID')}      active={tab === 'VALID'} />
        <Kpi Icon={AlertOctagon}  label={t('echKpiBroken')}     value={stats.broken}     tone="danger"  onClick={() => setTab('BROKEN')}     active={tab === 'BROKEN'} />
        <Kpi Icon={ShieldCheck}   label={t('echKpiUnverified')} value={stats.unverified} tone="warning" onClick={() => setTab('UNVERIFIED')} active={tab === 'UNVERIFIED'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('echPageTitle')} Icon={Link2}>
        {loading
          ? <EmptyState Icon={Link2} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Link2} title={t('echEmpty')} sub={t('echEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('echColExtId')}</th>
                    <th>{t('echColTitle')}</th>
                    <th style={{ textAlign: 'right' }}>{t('echColVersions')}</th>
                    <th style={{ textAlign: 'right' }}>{t('echColFirstBreak')}</th>
                    <th>{t('echColLastVerified')}</th>
                    <th>{t('echColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('echColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.evidenceId);
                    const status = r.chainStatus ?? 'UNVERIFIED';
                    const rep = reports.get(r.evidenceId);
                    return (
                      <Fragment key={r.evidenceId}>
                        <tr>
                          <td>
                            <button onClick={() => void onExpand(r.evidenceId)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono" style={{ fontSize: 11 }}>{shortHash(r.externalId)}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td style={{ textAlign: 'right' }} className="mono">{r.chainVersionCount ?? 0}</td>
                          <td style={{ textAlign: 'right' }} className="mono">
                            {r.chainFirstBreakVersion != null
                              ? `v${r.chainFirstBreakVersion}`
                              : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                          </td>
                          <td className="mono" style={{ fontSize: 12 }}>
                            {r.chainLastVerifiedAt ? new Date(r.chainLastVerifiedAt).toLocaleString() : '—'}
                          </td>
                          <td><Badge tone={CHAIN_STATUS_TONE[status]}>{status}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            <Button size="sm" variant={status === 'BROKEN' ? 'danger' : 'primary'}
                              Icon={ShieldCheck} disabled={busyId === r.evidenceId}
                              onClick={() => void onVerify(r.evidenceId)}>
                              {t('echVerify')}
                            </Button>
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={8} style={{ background: 'var(--surface-2)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {!rep ? (
                                  <span style={{ color: 'var(--ink-3)' }}>{t('loading')}</span>
                                ) : (
                                  <>
                                    <div style={{ marginBottom: 8 }}>
                                      <strong>{t('echReportHead')}:</strong>{' '}
                                      <span className="mono">{rep.versionCount}</span> {t('echVersions')}
                                      {' · '}
                                      <span style={{ color: 'var(--success)' }}>{rep.validCount} VALID</span>
                                      {rep.brokenCount > 0 && (
                                        <>{' · '}
                                          <span style={{ color: 'var(--danger)' }}>{rep.brokenCount} BROKEN</span>
                                          {rep.firstBreakVersion != null && (
                                            <span style={{ color: 'var(--danger)' }}> (first break at v{rep.firstBreakVersion})</span>
                                          )}
                                        </>
                                      )}
                                    </div>
                                    <table className="grc-table" style={{ fontSize: 12 }}>
                                      <thead>
                                        <tr>
                                          <th>v</th>
                                          <th>{t('echLinkContent')}</th>
                                          <th>{t('echLinkChain')}</th>
                                          <th>{t('echColStatus')}</th>
                                          <th>{t('echLinkFile')}</th>
                                          <th>{t('echLinkSnapshotted')}</th>
                                        </tr>
                                      </thead>
                                      <tbody>
                                        {rep.versions.map(v => (
                                          <tr key={v.versionId}>
                                            <td className="mono">v{v.versionNo}</td>
                                            <td className="mono" style={{ fontSize: 10 }}>{shortHash(v.contentHashSha256)}</td>
                                            <td className="mono" style={{ fontSize: 10 }}>
                                              <Hash size={10} style={{ display: 'inline', marginInlineEnd: 4 }} />
                                              {shortHash(v.chainHashSha256)}
                                            </td>
                                            <td>
                                              {v.chainStatus
                                                ? <Badge tone={CHAIN_STATUS_TONE[v.chainStatus]}>{v.chainStatus}</Badge>
                                                : '—'}
                                            </td>
                                            <td className="mono" style={{ fontSize: 11 }}>{v.fileName ?? '—'}</td>
                                            <td className="mono" style={{ fontSize: 11 }}>
                                              {v.snapshottedAt ? new Date(v.snapshottedAt).toLocaleDateString() : '—'}
                                            </td>
                                          </tr>
                                        ))}
                                      </tbody>
                                    </table>
                                    {rep.versions.some(v => v.chainBreakReason) && (
                                      <div style={{ marginTop: 8, padding: 8, background: 'var(--danger-bg)',
                                                    borderInlineStart: '3px solid var(--danger)', fontSize: 12 }}>
                                        <strong>{t('echBreakDetails')}</strong>
                                        <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                          {rep.versions.filter(v => v.chainBreakReason).map(v => (
                                            <li key={v.versionId}>
                                              <span className="mono">v{v.versionNo}</span>: {v.chainBreakReason}
                                            </li>
                                          ))}
                                        </ul>
                                      </div>
                                    )}
                                  </>
                                )}
                              </div>
                            </td>
                          </tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            )
        }
      </Card>
    </Shell>
  );
}
