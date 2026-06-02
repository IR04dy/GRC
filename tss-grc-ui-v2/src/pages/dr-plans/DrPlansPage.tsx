import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Server, CheckCircle2, XCircle, Archive, Play, FlaskConical, Network,
  ChevronRight, ChevronDown, Clock,
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
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listDrPlans, approveDrPlan, rejectDrPlan, activateDrPlan, archiveDrPlan, recordDrTest,
  parseDrSteps, parseDrDependencies,
  DR_STATUS_TONE, DR_TEST_TONE, DR_SITE_TONE,
  type DrPlan,
} from '../../lib/drPlans';

type TabId = 'all' | 'draft' | 'approved' | 'activated' | 'archived' | 'rejected';

export function DrPlansPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<DrPlan[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listDrPlans(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('drpLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { DRAFT:0, APPROVED:0, ACTIVATED:0, ARCHIVED:0, REJECTED:0,
                testedPassed:0, testedGap:0, overdue:0 };
    const today = new Date();
    rows.forEach(r => {
      s[r.status]++;
      if (r.lastTestedOutcome === 'PASSED') s.testedPassed++;
      if (r.lastTestedOutcome === 'GAP' || r.lastTestedOutcome === 'FAILED') s.testedGap++;
      if (r.nextTestDue && r.status !== 'ARCHIVED' && r.status !== 'REJECTED'
          && new Date(r.nextTestDue) < today) s.overdue++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => r.status === tab.toUpperCase());
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',       label: `${t('drpTabAll')} (${rows.length})` },
    { id: 'draft',     label: `${t('drpTabDraft')} (${stats.DRAFT})` },
    { id: 'approved',  label: `${t('drpTabApproved')} (${stats.APPROVED})` },
    { id: 'activated', label: `${t('drpTabActivated')} (${stats.ACTIVATED})` },
    { id: 'archived',  label: `${t('drpTabArchived')} (${stats.ARCHIVED})` },
    { id: 'rejected',  label: `${t('drpTabRejected')} (${stats.REJECTED})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onApprove(id: number) {
    setBusyId(id);
    try { await approveDrPlan(id); toast.success(t('drpApproved')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onReject(id: number) {
    const reason = window.prompt(t('drpRejectPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await rejectDrPlan(id, reason.trim()); toast.success(t('drpRejected')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActivate(id: number) {
    const reason = window.prompt(t('drpActivatePrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await activateDrPlan(id, reason.trim()); toast.success(t('drpActivated')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onArchive(id: number) {
    const reason = window.prompt(t('drpArchivePrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await archiveDrPlan(id, reason.trim()); toast.success(t('drpArchived')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onRecordTest(id: number) {
    const outcome = window.prompt(t('drpTestPrompt'), 'PASSED');
    if (!outcome) return;
    const up = outcome.toUpperCase();
    if (up !== 'PASSED' && up !== 'GAP' && up !== 'FAILED') {
      toast.danger(t('drpTestInvalid')); return;
    }
    setBusyId(id);
    try { await recordDrTest(id, up as 'PASSED'); toast.success(t('drpTestRecorded')); await reload(); }
    catch { toast.danger(t('drpActionFailed')); }
    finally { setBusyId(null); }
  }

  return (
    <Shell title={t('drpPageTitle')} sub={t('drpPageSub')}>
      <PageHeader eyebrow={t('navBcm')} title={t('drpPageTitle')} sub={t('drpPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Server}       label={t('drpKpiApproved')}   value={stats.APPROVED + stats.ACTIVATED} tone="success" />
        <Kpi Icon={Play}          label={t('drpKpiActivated')}  value={stats.ACTIVATED} tone="primary" />
        <Kpi Icon={FlaskConical}  label={t('drpKpiPassed')}     value={stats.testedPassed} tone="success" />
        <Kpi Icon={Clock}         label={t('drpKpiOverdue')}    value={stats.overdue} tone="danger" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('drpPageTitle')} Icon={Server}>
        {loading
          ? <EmptyState Icon={Server} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Server} title={t('drpEmpty')} sub={t('drpEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('drpColCode')}</th>
                    <th>{t('drpColTitle')}</th>
                    <th>{t('drpColSystem')}</th>
                    <th>{t('drpColSite')}</th>
                    <th style={{ textAlign: 'right' }}>RTO</th>
                    <th style={{ textAlign: 'right' }}>RPO</th>
                    <th>{t('drpColLastTest')}</th>
                    <th>{t('scnColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    const failover = parseDrSteps(r.failoverStepsJson);
                    const failback = parseDrSteps(r.failbackStepsJson);
                    const deps = parseDrDependencies(r.dependenciesJson);
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggle(r.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono">{r.planCode}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td>{r.criticalSystemName ?? '—'}</td>
                          <td><Badge tone={DR_SITE_TONE[r.drSiteType]}>{r.drSiteType.replace('_',' ')}</Badge></td>
                          <td style={{ textAlign: 'right' }} className="mono">{r.rtoHours}h</td>
                          <td style={{ textAlign: 'right' }} className="mono">{r.rpoHours}h</td>
                          <td>
                            {r.lastTestedOutcome
                              ? <Badge tone={DR_TEST_TONE[r.lastTestedOutcome]}>{r.lastTestedOutcome}</Badge>
                              : <Badge tone="info">NOT TESTED</Badge>}
                          </td>
                          <td><Badge tone={DR_STATUS_TONE[r.status]}>{statusLabel(r.status, lang)}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            {r.status === 'DRAFT' && (
                              <>
                                <Button size="sm" variant="primary" Icon={CheckCircle2}
                                  disabled={busyId === r.id} onClick={() => onApprove(r.id)}>
                                  {t('scnApprove')}
                                </Button>
                                {' '}
                                <Button size="sm" variant="danger" Icon={XCircle}
                                  disabled={busyId === r.id} onClick={() => onReject(r.id)}>
                                  {t('scnReject')}
                                </Button>
                              </>
                            )}
                            {r.status === 'APPROVED' && (
                              <Button size="sm" variant="primary" Icon={Play}
                                disabled={busyId === r.id} onClick={() => onActivate(r.id)}>
                                {t('drpActivate')}
                              </Button>
                            )}
                            {(r.status === 'APPROVED' || r.status === 'ACTIVATED' || r.status === 'DRAFT') && (
                              <>{' '}
                                <Button size="sm" variant="ghost" Icon={FlaskConical}
                                  disabled={busyId === r.id} onClick={() => onRecordTest(r.id)}>
                                  {t('drpRecordTest')}
                                </Button>
                              </>
                            )}
                            {r.status !== 'ARCHIVED' && r.status !== 'REJECTED' && (
                              <>{' '}
                                <Button size="sm" variant="ghost" Icon={Archive}
                                  disabled={busyId === r.id} onClick={() => onArchive(r.id)}>
                                  {t('scnArchive')}
                                </Button>
                              </>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={10} style={{ background: 'var(--surface-2)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {r.descriptionEn && (
                                  <p style={{ marginBottom: 6 }}>
                                    {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
                                  </p>
                                )}
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                  <div>
                                    <strong>{t('drpSite')}:</strong> {r.drSiteLocation ?? '—'}
                                    {' · '}
                                    <strong>{t('drpProvider')}:</strong> {r.drSiteProvider ?? '—'}
                                    <br />
                                    <strong>{t('drpOwner')}:</strong> {r.owner ?? '—'}
                                    {r.linkedBcpCode && <><br /><strong>{t('drpBcp')}:</strong> <span className="mono">{r.linkedBcpCode}</span></>}
                                    {r.mtpdHours && <><br /><strong>MTPD:</strong> <span className="mono">{r.mtpdHours}h</span></>}
                                  </div>
                                  <div>
                                    {r.nextTestDue && <><strong>{t('drpNextTest')}:</strong> <span className="mono">{r.nextTestDue}</span><br /></>}
                                    {r.lastTestedAt && (
                                      <><strong>{t('drpLastTested')}:</strong>{' '}
                                      <span className="mono">{new Date(r.lastTestedAt).toLocaleDateString()}</span><br /></>
                                    )}
                                    {r.lastTestNotesEn && (
                                      <span style={{ color: 'var(--ink-3)' }}>{r.lastTestNotesEn}</span>
                                    )}
                                  </div>
                                </div>

                                {r.recoveryStrategyEn && (
                                  <p style={{ marginTop: 8 }}>
                                    <strong>{t('drpStrategy')}:</strong>{' '}
                                    {lang === 'ar' && r.recoveryStrategyAr ? r.recoveryStrategyAr : r.recoveryStrategyEn}
                                  </p>
                                )}

                                {deps.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpDependencies')}</strong>
                                    <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {deps.map((d, i) => (
                                        <li key={i}>
                                          <Network size={12} style={{ display:'inline', marginInlineEnd:4 }}/>
                                          {d.system} {d.type && <span style={{ color:'var(--ink-3)' }}>— {d.type}</span>}
                                          {d.criticality && <Badge tone={d.criticality === 'CRITICAL' ? 'danger' : d.criticality === 'HIGH' ? 'warning' : 'info'}> {d.criticality}</Badge>}
                                        </li>
                                      ))}
                                    </ul>
                                  </div>
                                )}

                                {failover.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpFailoverSteps')}</strong>
                                    <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {failover.map(s => (
                                        <li key={s.seq}>
                                          {lang === 'ar' && s.actionAr ? s.actionAr : s.actionEn}
                                          {s.owner && <span style={{ color:'var(--ink-3)' }}> — {s.owner}</span>}
                                          {s.minutesAllocated != null && <span className="mono"> ({s.minutesAllocated}m)</span>}
                                          {s.automated && <Badge tone="info"> AUTO</Badge>}
                                          {s.doneAt && <span style={{ color: 'var(--success)' }}> ✓ {new Date(s.doneAt).toLocaleString()}</span>}
                                        </li>
                                      ))}
                                    </ol>
                                  </div>
                                )}

                                {failback.length > 0 && (
                                  <div style={{ marginTop: 8 }}>
                                    <strong>{t('drpFailbackSteps')}</strong>
                                    <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                      {failback.map(s => (
                                        <li key={s.seq}>
                                          {lang === 'ar' && s.actionAr ? s.actionAr : s.actionEn}
                                          {s.owner && <span style={{ color:'var(--ink-3)' }}> — {s.owner}</span>}
                                          {s.minutesAllocated != null && <span className="mono"> ({s.minutesAllocated}m)</span>}
                                        </li>
                                      ))}
                                    </ol>
                                  </div>
                                )}

                                {r.dataBackupStrategyEn && (
                                  <p style={{ marginTop: 8 }}>
                                    <strong>{t('drpBackup')}:</strong>{' '}
                                    {lang === 'ar' && r.dataBackupStrategyAr ? r.dataBackupStrategyAr : r.dataBackupStrategyEn}
                                  </p>
                                )}

                                {r.activationReason && (
                                  <p style={{ marginTop: 6, color: 'var(--primary)' }}>
                                    <strong>{t('drpActivationReason')}:</strong> {r.activationReason}
                                  </p>
                                )}
                                {r.rejectionReason && (
                                  <p style={{ marginTop: 6, color: 'var(--danger)' }}>
                                    <strong>{t('scnRejectionReason')}:</strong> {r.rejectionReason}
                                  </p>
                                )}
                                {r.archiveReason && (
                                  <p style={{ marginTop: 6, color: 'var(--ink-3)' }}>
                                    <strong>{t('scnArchiveReason')}:</strong> {r.archiveReason}
                                  </p>
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
