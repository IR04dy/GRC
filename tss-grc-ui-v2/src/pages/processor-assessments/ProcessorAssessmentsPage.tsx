import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  ClipboardCheck, ChevronRight, ChevronDown, CheckCircle2, XCircle, AlertTriangle, Shield, Plus,
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
  listProcessorAssessments, approveProcessorAssessment, rejectProcessorAssessment,
  PROC_STATUS_TONE, PROC_OUTCOME_TONE, ART28_CONTROLS,
  type ProcessorAssessment,
} from '../../lib/processorAssessments';

type TabId = 'all' | 'IN_PROGRESS' | 'COMPLETED' | 'APPROVED' | 'REJECTED';

export function ProcessorAssessmentsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<ProcessorAssessment[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);

  useEffect(() => { void reload(); }, []);
  async function reload() {
    setLoading(true);
    try {
      const page = await listProcessorAssessments(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('procLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { IN_PROGRESS:0, COMPLETED:0, APPROVED:0, REJECTED:0, fail:0, pwc:0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.overallOutcome === 'FAIL') s.fail++;
      if (r.overallOutcome === 'PASS_WITH_CONDITIONS') s.pwc++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() =>
    tab === 'all' ? rows : rows.filter(r => r.status === tab), [tab, rows]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',         label: t('procTabAll') },
    { id: 'IN_PROGRESS', label: t('procTabInProgress'), count: stats.IN_PROGRESS },
    { id: 'COMPLETED',   label: t('procTabCompleted'),  count: stats.COMPLETED },
    { id: 'APPROVED',    label: t('procTabApproved'),   count: stats.APPROVED },
    { id: 'REJECTED',    label: t('procTabRejected'),   count: stats.REJECTED },
  ];

  function toggle(id: number) {
    const n = new Set(expanded); if (n.has(id)) n.delete(id); else n.add(id); setExpanded(n);
  }

  async function onApprove(id: number) {
    setBusyId(id);
    try { await approveProcessorAssessment(id, 'admin'); toast.success(t('procApproved')); await reload(); }
    catch { toast.danger(t('procActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onReject(id: number) {
    if (!window.confirm(t('procRejectConfirm'))) return;
    setBusyId(id);
    try { await rejectProcessorAssessment(id); toast.success(t('procRejected')); await reload(); }
    catch { toast.danger(t('procActionFailed')); }
    finally { setBusyId(null); }
  }

  const CREATE_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'assessmentCode', label: t('procColCode'), span: 4, placeholder: 'PROC-2026-009' },
    { kind: 'text',   key: 'processorName',  label: t('procColProcessor') + ' *', required: true, span: 4 },
    { kind: 'text',   key: 'processorCountry', label: t('procColCountry'), maxLength: 3, span: 4, placeholder: 'SAU' },
    { kind: 'select', key: 'assessmentType', label: t('procColType') + ' *', required: true, span: 12,
      options: [ {value:'ONBOARDING',label:'Onboarding'},{value:'ANNUAL',label:'Annual'},{value:'RENEWAL',label:'Renewal'},{value:'AD_HOC',label:'Ad-hoc'},{value:'INCIDENT_TRIGGERED',label:'Incident-triggered'} ] },
    { kind: 'textarea', key: 'processingScopeEn', label: t('procScopeEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'processingScopeAr', label: t('procScopeAr'), rows: 2, span: 12 },
    { kind: 'text',     key: 'evidenceRefs', label: t('procEvidenceRefs'), span: 12, placeholder: 'DPA-2026 · SOC2 · ISO27001' },
    { kind: 'date',     key: 'nextAssessmentDue', label: t('procNextDue'), span: 6 },
  ];

  async function onCreate(values: Record<string, unknown>) {
    try {
      await api.post('/api/privacy/processor-assessments', {
        assessmentCode: String(values.assessmentCode ?? '').trim() || undefined,
        processorName: String(values.processorName ?? '').trim(),
        processorCountry: String(values.processorCountry ?? '').trim() || undefined,
        assessmentType: values.assessmentType,
        processingScopeEn: String(values.processingScopeEn ?? '').trim(),
        processingScopeAr: String(values.processingScopeAr ?? '').trim() || undefined,
        evidenceRefs: String(values.evidenceRefs ?? '').trim() || undefined,
        nextAssessmentDue: values.nextAssessmentDue || undefined,
      });
      toast.success(t('procCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('procActionFailed')); }
  }

  return (
    <Shell title={t('procPageTitle')} sub={t('procPageSub')}>
      <PageHeader eyebrow={t('navPrivacy')} title={t('procPageTitle')} sub={t('procPageSub')}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('procNew')}</Button>} />

      <div className="kpi-grid">
        <Kpi Icon={ClipboardCheck} label={t('procKpiApproved')} value={stats.APPROVED} tone="success" />
        <Kpi Icon={AlertTriangle}  label={t('procKpiPwc')}      value={stats.pwc}      tone="warning" />
        <Kpi Icon={XCircle}        label={t('procKpiFailed')}   value={stats.fail}     tone="danger" />
        <Kpi Icon={Shield}         label={t('procKpiInProgress')} value={stats.IN_PROGRESS + stats.COMPLETED} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} hideEmpty />

      <Card title={t('procPageTitle')} Icon={ClipboardCheck}>
        {loading
          ? <EmptyState Icon={ClipboardCheck} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={ClipboardCheck} title={t('procEmpty')} sub={t('procEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{width:28}}/>
                    <th>{t('procColCode')}</th>
                    <th>{t('procColProcessor')}</th>
                    <th>{t('procColType')}</th>
                    <th>{t('procColOutcome')}</th>
                    <th style={{textAlign:'right'}}>{t('procColRiskScore')}</th>
                    <th>{t('procColStatus')}</th>
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
                          <td className="mono">{r.assessmentCode ?? '—'}</td>
                          <td>{r.processorName}<br/><span className="mono" style={{fontSize:10, color:'var(--muted-foreground)'}}>{r.processorCountry ?? '—'}</span></td>
                          <td><span style={{fontSize:11}}>{r.assessmentType.replace(/_/g, ' ')}</span></td>
                          <td>{r.overallOutcome ? <Badge tone={PROC_OUTCOME_TONE[r.overallOutcome]}>{r.overallOutcome.replace(/_/g, ' ')}</Badge> : <span style={{color:'var(--muted-foreground)'}}>—</span>}</td>
                          <td style={{textAlign:'right'}} className="mono">{r.riskScore ?? '—'}</td>
                          <td><Badge tone={PROC_STATUS_TONE[r.status]}>{r.status.replace('_',' ')}</Badge></td>
                          <td style={{textAlign:'right'}}>
                            {r.status === 'COMPLETED' && (
                              <>
                                <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === r.id} onClick={() => onApprove(r.id)}>{t('procApprove')}</Button>{' '}
                                <Button size="sm" variant="danger" Icon={XCircle} disabled={busyId === r.id} onClick={() => onReject(r.id)}>{t('procReject')}</Button>
                              </>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={8} style={{background:'var(--muted)'}}>
                            <div style={{padding:12, fontSize:13}}>
                              <p>{lang === 'ar' && r.processingScopeAr ? r.processingScopeAr : r.processingScopeEn}</p>
                              <div style={{marginTop:10}}>
                                <strong>{t('procArt28Title')}</strong>
                                <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:6, marginTop:6}}>
                                  {ART28_CONTROLS.map(c => {
                                    const v = (r as any)[c.key] as string | undefined;
                                    return (
                                      <div key={c.key} style={{display:'flex', justifyContent:'space-between', padding:'4px 8px', background:'var(--card)', borderRadius:4, fontSize:12}}>
                                        <span>{t(c.labelKey)}</span>
                                        {v
                                          ? <Badge tone={PROC_OUTCOME_TONE[v as keyof typeof PROC_OUTCOME_TONE]}>{v.replace(/_/g, ' ')}</Badge>
                                          : <span style={{color:'var(--muted-foreground)'}}>—</span>}
                                      </div>
                                    );
                                  })}
                                </div>
                              </div>
                              {r.findingsEn && (
                                <p style={{marginTop:10}}><strong>{t('procFindings')}:</strong> {lang === 'ar' && r.findingsAr ? r.findingsAr : r.findingsEn}</p>
                              )}
                              {r.recommendationsEn && (
                                <p style={{marginTop:6}}><strong>{t('procRecommendations')}:</strong> {lang === 'ar' && r.recommendationsAr ? r.recommendationsAr : r.recommendationsEn}</p>
                              )}
                              <p style={{marginTop:6, fontSize:12, color:'var(--muted-foreground)'}}>
                                {r.assessedAt && <>{t('procAssessed')}: {r.assessedAt.slice(0,10)} ({r.assessorName ?? '—'})</>}
                                {r.approvedAt && <> · {t('procApprovedOn')}: {r.approvedAt.slice(0,10)} ({r.approverName ?? '—'})</>}
                                {r.nextAssessmentDue && <> · {t('procNextDue')}: {r.nextAssessmentDue}</>}
                              </p>
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
        title={t('procNew')}
        fields={CREATE_FIELDS}
        initial={{ assessmentType: 'ONBOARDING' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
