import { useEffect, useMemo, useState, Fragment } from 'react';
import { Link } from 'react-router-dom';
import {
  Activity, ChevronRight, ChevronDown, CheckCircle2, XCircle, AlertTriangle, Clock, Plus,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { api } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listSrt, startSrt, completeSrt, approveSrt, cancelSrt,
  SRT_STATUS_TONE, SRT_OUTCOME_TONE,
  type SupplierResilienceTest, type SrtStatus,
} from '../../lib/resilienceTests';

type TabId = 'all' | 'SCHEDULED' | 'IN_PROGRESS' | 'COMPLETED' | 'APPROVED' | 'CANCELLED';

export function ResilienceTestsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<SupplierResilienceTest[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);

  useEffect(() => { void reload(); }, []);
  async function reload() {
    setLoading(true);
    try { const p = await listSrt(undefined, 0, 200); setRows(p.content); }
    catch { toast.danger(t('srtLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { SCHEDULED:0, IN_PROGRESS:0, COMPLETED:0, APPROVED:0, CANCELLED:0, pass:0, fail:0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.outcome === 'PASS' || r.outcome === 'PASS_WITH_OBSERVATIONS') s.pass++;
      if (r.outcome === 'FAIL') s.fail++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() =>
    tab === 'all' ? rows : rows.filter(r => r.status === tab), [tab, rows]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',         label: t('srtTabAll') },
    { id: 'SCHEDULED',   label: t('srtTabScheduled'),   count: stats.SCHEDULED },
    { id: 'IN_PROGRESS', label: t('srtTabInProgress'),  count: stats.IN_PROGRESS },
    { id: 'COMPLETED',   label: t('srtTabCompleted'),   count: stats.COMPLETED },
    { id: 'APPROVED',    label: t('srtTabApproved'),    count: stats.APPROVED },
    { id: 'CANCELLED',   label: t('srtTabCancelled'),   count: stats.CANCELLED },
  ];

  function toggle(id: number) {
    const n = new Set(expanded); if (n.has(id)) n.delete(id); else n.add(id); setExpanded(n);
  }

  async function onStart(id: number) {
    setBusyId(id); try { await startSrt(id); toast.success(t('srtStarted')); await reload(); }
    catch { toast.danger(t('srtActionFailed')); } finally { setBusyId(null); }
  }
  async function onComplete(id: number) {
    const outcome = window.prompt(t('srtCompletePrompt'), 'PASS_WITH_OBSERVATIONS');
    if (!outcome) return;
    setBusyId(id); try { await completeSrt(id, { outcome: outcome as any }); toast.success(t('srtCompleted')); await reload(); }
    catch { toast.danger(t('srtActionFailed')); } finally { setBusyId(null); }
  }
  async function onApprove(id: number) {
    setBusyId(id); try { await approveSrt(id, 'admin'); toast.success(t('srtApproved')); await reload(); }
    catch { toast.danger(t('srtActionFailed')); } finally { setBusyId(null); }
  }
  async function onCancel(id: number) {
    const reason = window.prompt(t('srtCancelPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id); try { await cancelSrt(id, reason.trim()); toast.success(t('srtCancelled')); await reload(); }
    catch { toast.danger(t('srtActionFailed')); } finally { setBusyId(null); }
  }

  const CREATE_FIELDS: FieldDef[] = [
    { kind: 'text', key: 'testCode',   label: t('srtColCode'), span: 4, placeholder: 'SRT-2026-009' },
    { kind: 'text', key: 'vendorName', label: t('srtColVendor') + ' *', required: true, span: 8 },
    { kind: 'text', key: 'titleEn',    label: t('srtColTitle') + ' (EN) *', required: true, span: 12 },
    { kind: 'text', key: 'titleAr',    label: t('srtColTitle') + ' (AR)', span: 12 },
    { kind: 'select', key: 'testType', label: t('srtColType') + ' *', required: true, span: 6,
      options: [ {value:'TABLETOP',label:'Tabletop'},{value:'FAILOVER_DRILL',label:'Failover drill'},{value:'EXIT_REHEARSAL',label:'Exit rehearsal'},{value:'SLA_VALIDATION',label:'SLA validation'},{value:'JOINT_CRISIS_SIM',label:'Joint crisis sim'} ] },
    { kind: 'select', key: 'scenarioScope', label: t('srtColScope') + ' *', required: true, span: 6,
      options: [ {value:'DESKTOP',label:'Desktop'},{value:'PARTIAL_INVOCATION',label:'Partial invocation'},{value:'FULL_INVOCATION',label:'Full invocation'} ] },
    { kind: 'textarea', key: 'scenarioDescriptionEn', label: t('srtScenarioEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'scenarioDescriptionAr', label: t('srtScenarioAr'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'successCriteriaEn', label: t('srtSuccessCriteria'), rows: 2, span: 12 },
    { kind: 'text',     key: 'participants',      label: t('srtParticipants'), span: 12, placeholder: 'CISO, Head of Infra, Vendor TAM' },
    { kind: 'date',     key: 'scheduledFor',      label: t('srtColScheduled') + ' *', span: 6 },
    { kind: 'number',   key: 'rtoTargetMinutes',  label: 'RTO target (min)', min: 1, span: 6 },
  ];

  async function onCreate(values: Record<string, unknown>) {
    try {
      await api.post('/api/scm/resilience/tests', {
        testCode: String(values.testCode ?? '').trim() || undefined,
        vendorName: String(values.vendorName ?? '').trim(),
        titleEn: String(values.titleEn ?? '').trim(),
        titleAr: String(values.titleAr ?? '').trim() || undefined,
        testType: values.testType,
        scenarioScope: values.scenarioScope || 'DESKTOP',
        scenarioDescriptionEn: String(values.scenarioDescriptionEn ?? '').trim(),
        scenarioDescriptionAr: String(values.scenarioDescriptionAr ?? '').trim() || undefined,
        successCriteriaEn: String(values.successCriteriaEn ?? '').trim() || undefined,
        participants: String(values.participants ?? '').trim() || undefined,
        scheduledFor: values.scheduledFor || undefined,
        rtoTargetMinutes: values.rtoTargetMinutes != null ? Number(values.rtoTargetMinutes) : undefined,
      });
      toast.success(t('srtCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('srtActionFailed')); }
  }

  return (
    <Shell title={t('srtPageTitle')} sub={t('srtPageSub')}>
      <PageHeader eyebrow={t('navThirdParty')} title={t('srtPageTitle')} sub={t('srtPageSub')}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('srtNew')}</Button>} />

      <div className="kpi-grid">
        <Kpi Icon={CheckCircle2}  label={t('srtKpiPass')}      value={stats.pass} tone="success" />
        <Kpi Icon={XCircle}       label={t('srtKpiFail')}      value={stats.fail} tone="danger" />
        <Kpi Icon={Clock}         label={t('srtKpiScheduled')} value={stats.SCHEDULED + stats.IN_PROGRESS} tone="warning" />
        <Kpi Icon={AlertTriangle} label={t('srtKpiApproved')}  value={stats.APPROVED} tone="info" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} hideEmpty />

      <Card title={t('srtPageTitle')} Icon={Activity}>
        {loading
          ? <EmptyState Icon={Activity} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Activity} title={t('srtEmpty')} sub={t('srtEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{width:28}}/>
                    <th>{t('srtColCode')}</th>
                    <th>{t('srtColVendor')}</th>
                    <th>{t('srtColTitle')}</th>
                    <th>{t('srtColType')}</th>
                    <th>{t('srtColScope')}</th>
                    <th>{t('srtColScheduled')}</th>
                    <th>{t('srtColOutcome')}</th>
                    <th>{t('srtColStatus')}</th>
                    <th style={{textAlign:'right'}}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td><button onClick={() => toggle(r.id)} style={{background:'none',border:0,cursor:'pointer'}}>{open ? <ChevronDown size={16}/> : <ChevronRight size={16}/>}</button></td>
                          <td className="mono">{r.testCode ?? '—'}</td>
                          <td>{r.vendorName}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td><span style={{fontSize:11}}>{r.testType.replace(/_/g, ' ')}</span></td>
                          <td><span style={{fontSize:11}}>{r.scenarioScope.replace(/_/g, ' ')}</span></td>
                          <td className="mono" style={{fontSize:11}}>{r.scheduledFor ? r.scheduledFor.slice(0,10) : '—'}</td>
                          <td>{r.outcome ? <Badge tone={SRT_OUTCOME_TONE[r.outcome]}>{r.outcome.replace(/_/g, ' ')}</Badge> : <span style={{color:'var(--muted-foreground)'}}>—</span>}</td>
                          <td><Badge tone={SRT_STATUS_TONE[r.status]}>{r.status.replace('_',' ')}</Badge></td>
                          <td style={{textAlign:'right'}}>
                            {r.status === 'SCHEDULED' && (
                              <>
                                <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onStart(r.id)}>{t('srtStart')}</Button>{' '}
                                <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onCancel(r.id)}>{t('srtCancel')}</Button>
                              </>
                            )}
                            {r.status === 'IN_PROGRESS' && (
                              <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onComplete(r.id)}>{t('srtComplete')}</Button>
                            )}
                            {r.status === 'COMPLETED' && (
                              <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === r.id} onClick={() => onApprove(r.id)}>{t('srtApprove')}</Button>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={10} style={{background:'var(--muted)'}}>
                            <div style={{padding:12, fontSize:13}}>
                              <p>{lang === 'ar' && r.scenarioDescriptionAr ? r.scenarioDescriptionAr : r.scenarioDescriptionEn}</p>
                              {r.successCriteriaEn && <p style={{marginTop:6}}><strong>{t('srtSuccessCriteria')}:</strong> {r.successCriteriaEn}</p>}
                              {r.participants && <p style={{marginTop:6}}><strong>{t('srtParticipants')}:</strong> <span className="mono" style={{fontSize:12}}>{r.participants}</span></p>}
                              <p style={{marginTop:6, fontSize:12}}>
                                <Link to="/supplier-resilience" style={{ marginInlineEnd: 8, padding: '2px 8px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, color: 'var(--primary-700)', textDecoration: 'none' }}>{t('srtLinkResilience')} →</Link>
                                <Link to="/third-party-cyber" style={{ marginInlineEnd: 8, padding: '2px 8px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, color: 'var(--primary-700)', textDecoration: 'none' }}>{t('srtLinkCyber')} →</Link>
                              </p>
                              {(r.rtoTargetMinutes != null || r.rtoActualMinutes != null) && (
                                <p style={{marginTop:6}}>
                                  <strong>RTO:</strong> {r.rtoTargetMinutes ?? '—'} min target · {r.rtoActualMinutes ?? '—'} min actual
                                </p>
                              )}
                              {r.observationsEn && <p style={{marginTop:6}}><strong>{t('srtObservations')}:</strong> {lang === 'ar' && r.observationsAr ? r.observationsAr : r.observationsEn}</p>}
                              {r.actionItemsEn && <p style={{marginTop:6}}><strong>{t('srtActionItems')}:</strong> {r.actionItemsEn}</p>}
                              {r.status === 'CANCELLED' && r.cancellationReason && (
                                <p style={{marginTop:6, color:'var(--danger)'}}><strong>{t('srtCancellationReason')}:</strong> {r.cancellationReason}</p>
                              )}
                            </div>
                          </td></tr>
                        )}
                      </Fragment>
                    );
                  })}
                </tbody>
              </table>
            )
        }
      </Card>

      <FormModal
        open={createOpen}
        title={t('srtNew')}
        fields={CREATE_FIELDS}
        initial={{ testType: 'TABLETOP', scenarioScope: 'DESKTOP' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
