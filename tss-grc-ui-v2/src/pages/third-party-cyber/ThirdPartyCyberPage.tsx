import { useEffect, useMemo, useState, Fragment } from 'react';
import { Link } from 'react-router-dom';
import {
  Bug, ChevronRight, ChevronDown, AlertTriangle, CheckCircle2, Lock, Plus, FolderOpen,
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
import { EntityDocsModal } from '../../components/ui/EntityDocsModal';
import { api } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listTpc, submitTpc, approveTpc, retireTpc,
  TPC_STATUS_TONE, TPC_RESIDUAL_TONE, TPC_INHERENT_TONE,
  type ThirdPartyCyberAssessment,
} from '../../lib/thirdPartyCyber';

type TabId = 'all' | 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'RETIRED' | 'high-risk';

export function ThirdPartyCyberPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<ThirdPartyCyberAssessment[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);
  const [docsTarget, setDocsTarget] = useState<ThirdPartyCyberAssessment | null>(null);

  useEffect(() => { void reload(); }, []);
  async function reload() {
    setLoading(true);
    try { const p = await listTpc(undefined, 0, 200); setRows(p.content); }
    catch { toast.danger(t('tpcLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { DRAFT:0, IN_REVIEW:0, APPROVED:0, RETIRED:0, high:0, critical:0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.residualRiskTier === 'HIGH') s.high++;
      if (r.residualRiskTier === 'CRITICAL') s.critical++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    if (tab === 'high-risk') return rows.filter(r => r.residualRiskTier === 'HIGH' || r.residualRiskTier === 'CRITICAL' || r.residualRiskTier === 'ELEVATED');
    return rows.filter(r => r.status === tab);
  }, [tab, rows]);

  const highRiskCount = rows.filter(r => ['ELEVATED','HIGH','CRITICAL'].includes(r.residualRiskTier)).length;
  const tabs: TabItem<TabId>[] = [
    { id: 'all',       label: t('tpcTabAll') },
    { id: 'DRAFT',     label: t('tpcTabDraft'),     count: stats.DRAFT },
    { id: 'IN_REVIEW', label: t('tpcTabReview'),    count: stats.IN_REVIEW },
    { id: 'APPROVED',  label: t('tpcTabApproved'),  count: stats.APPROVED },
    { id: 'RETIRED',   label: t('tpcTabRetired'),   count: stats.RETIRED },
    { id: 'high-risk', label: t('tpcTabHighRisk'),  count: highRiskCount },
  ];

  function toggle(id: number) {
    const n = new Set(expanded); if (n.has(id)) n.delete(id); else n.add(id); setExpanded(n);
  }

  async function onSubmit(id: number)  { try { setBusyId(id); await submitTpc(id); toast.success(t('tpcSubmitted')); await reload(); } catch { toast.danger(t('tpcActionFailed')); } finally { setBusyId(null); } }
  async function onApprove(id: number) { try { setBusyId(id); await approveTpc(id, 'admin'); toast.success(t('tpcApproved')); await reload(); } catch { toast.danger(t('tpcActionFailed')); } finally { setBusyId(null); } }
  async function onRetire(id: number)  { if(!window.confirm(t('tpcRetireConfirm'))) return; try { setBusyId(id); await retireTpc(id); toast.success(t('tpcRetired')); await reload(); } catch { toast.danger(t('tpcActionFailed')); } finally { setBusyId(null); } }

  const CREATE_FIELDS: FieldDef[] = [
    { kind: 'text', key: 'assessmentCode', label: t('tpcColCode'), span: 4, placeholder: 'TPC-2026-009' },
    { kind: 'text', key: 'vendorName',     label: t('tpcColVendor') + ' *', required: true, span: 4 },
    { kind: 'text', key: 'vendorCountry',  label: t('tpcColCountry'), maxLength: 3, span: 4, placeholder: 'SAU' },
    { kind: 'select', key: 'inherentCyberRisk', label: t('tpcColInherent') + ' *', required: true, span: 12,
      options: [ {value:'LOW',label:'Low'},{value:'MEDIUM',label:'Medium'},{value:'HIGH',label:'High'},{value:'CRITICAL',label:'Critical'} ] },
    { kind: 'textarea', key: 'scopeDescriptionEn', label: t('tpcScopeEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'scopeDescriptionAr', label: t('tpcScopeAr'), rows: 2, span: 12 },
    { kind: 'text',     key: 'certifications', label: t('tpcCerts'), span: 12, placeholder: 'ISO27001,SOC2,PCI_DSS' },
    { kind: 'number',   key: 'externalPostureScore', label: t('tpcExternalPosture'), min: 0, max: 100, span: 4 },
    { kind: 'number',   key: 'questionnaireScore',   label: t('tpcQuestionnaireScore'),   min: 0, max: 100, span: 4 },
    { kind: 'number',   key: 'criticalVulnsOpen',    label: t('tpcCriticalVulns'),  min: 0, span: 4 },
    { kind: 'date',     key: 'nextReviewDue', label: t('tpcNextReview'), span: 6 },
  ];

  async function onCreate(values: Record<string, unknown>) {
    try {
      await api.post('/api/tprm/cyber-assessments', {
        assessmentCode: String(values.assessmentCode ?? '').trim() || undefined,
        vendorName: String(values.vendorName ?? '').trim(),
        vendorCountry: String(values.vendorCountry ?? '').trim() || undefined,
        inherentCyberRisk: values.inherentCyberRisk,
        scopeDescriptionEn: String(values.scopeDescriptionEn ?? '').trim(),
        scopeDescriptionAr: String(values.scopeDescriptionAr ?? '').trim() || undefined,
        certifications: String(values.certifications ?? '').trim() || undefined,
        externalPostureScore: values.externalPostureScore != null ? Number(values.externalPostureScore) : undefined,
        questionnaireScore:   values.questionnaireScore   != null ? Number(values.questionnaireScore)   : undefined,
        criticalVulnsOpen:    values.criticalVulnsOpen    != null ? Number(values.criticalVulnsOpen)    : undefined,
        mfaEnforced: 1, penTestWithin12m: 0, breachHistory24m: 0,
        nextReviewDue: values.nextReviewDue || undefined,
      });
      toast.success(t('tpcCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('tpcActionFailed')); }
  }

  return (
    <Shell title={t('tpcPageTitle')} sub={t('tpcPageSub')}>
      <PageHeader eyebrow={t('navThirdParty')} title={t('tpcPageTitle')} sub={t('tpcPageSub')}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('tpcNew')}</Button>} />

      <div className="kpi-grid">
        <Kpi Icon={CheckCircle2}   label={t('tpcKpiApproved')} value={stats.APPROVED} tone="success" onClick={() => setTab('APPROVED')} active={tab === 'APPROVED'} />
        <Kpi Icon={AlertTriangle}  label={t('tpcKpiHigh')}     value={stats.high}     tone="warning" />
        <Kpi Icon={Bug}            label={t('tpcKpiCritical')} value={stats.critical} tone="danger" />
        <Kpi Icon={Lock}           label={t('tpcKpiReview')}   value={stats.IN_REVIEW + stats.DRAFT} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} hideEmpty />

      <Card title={t('tpcPageTitle')} Icon={Bug}>
        {loading
          ? <EmptyState Icon={Bug} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Bug} title={t('tpcEmpty')} sub={t('tpcEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{width:28}}/>
                    <th>{t('tpcColCode')}</th>
                    <th>{t('tpcColVendor')}</th>
                    <th>{t('tpcColInherent')}</th>
                    <th>{t('tpcColResidual')}</th>
                    <th style={{textAlign:'right'}}>{t('tpcColRiskScore')}</th>
                    <th>{t('tpcColPosture')}</th>
                    <th>{t('tpcColStatus')}</th>
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
                          <td>{r.vendorName}<br/><span className="mono" style={{fontSize:10, color:'var(--ink-3)'}}>{r.vendorCountry ?? '—'}</span></td>
                          <td><Badge tone={TPC_INHERENT_TONE[r.inherentCyberRisk]}>{r.inherentCyberRisk}</Badge></td>
                          <td><Badge tone={TPC_RESIDUAL_TONE[r.residualRiskTier]}>{r.residualRiskTier}</Badge></td>
                          <td style={{textAlign:'right'}} className="mono">{r.riskScore ?? '—'}</td>
                          <td className="mono" style={{fontSize:12}}>
                            {r.externalPostureScore != null ? `ext ${r.externalPostureScore}` : ''}
                            {r.questionnaireScore != null ? ` · q ${r.questionnaireScore}` : ''}
                          </td>
                          <td><Badge tone={TPC_STATUS_TONE[r.status]}>{r.status.replace('_',' ')}</Badge></td>
                          <td style={{textAlign:'right'}}>
                            <Button size="sm" variant="ghost" Icon={FolderOpen} title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'} onClick={() => setDocsTarget(r)} />{' '}
                            {r.status === 'DRAFT' && <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onSubmit(r.id)}>{t('tpcSubmit')}</Button>}
                            {r.status === 'IN_REVIEW' && <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === r.id} onClick={() => onApprove(r.id)}>{t('tpcApprove')}</Button>}
                            {r.status === 'APPROVED' && <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onRetire(r.id)}>{t('tpcRetire')}</Button>}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={9} style={{background:'var(--surface-2)'}}>
                            <div style={{padding:12, fontSize:13}}>
                              <p>{lang === 'ar' && r.scopeDescriptionAr ? r.scopeDescriptionAr : r.scopeDescriptionEn}</p>
                              <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:12, marginTop:8, fontSize:12}}>
                                <div>
                                  <strong>{t('tpcCerts')}:</strong>{' '}<span className="mono">{r.certifications || '—'}</span><br/>
                                  <strong>{t('tpcMfa')}:</strong> {r.mfaEnforced ? '✓' : '✗'}
                                  {' · '}
                                  <strong>{t('tpcPenTest')}:</strong> {r.penTestWithin12m ? '✓' : '✗'}<br/>
                                  <strong>{t('tpcCriticalVulns')}:</strong> {r.criticalVulnsOpen ?? '—'}
                                  {' · '}
                                  <strong>{t('tpcPatchSla')}:</strong> {r.patchSlaCompliancePct != null ? `${r.patchSlaCompliancePct}%` : '—'}<br/>
                                </div>
                                <div>
                                  {r.breachHistory24m === 1 && (
                                    <>
                                      <strong style={{color:'var(--danger)'}}>{t('tpcBreachHistory')}:</strong>{' '}
                                      {r.lastKnownBreachDate ?? '—'}<br/>
                                      <span style={{fontSize:11, color:'var(--ink-3)'}}>{r.lastKnownBreachSummary}</span>
                                    </>
                                  )}
                                  {r.nextReviewDue && <><strong>{t('tpcNextReview')}:</strong> {r.nextReviewDue}<br/></>}
                                  {r.approverName && <><strong>{t('tpcApproverName')}:</strong> {r.approverName}</>}
                                </div>
                              </div>
                              {r.findingsEn && (
                                <p style={{marginTop:8}}><strong>{t('tpcFindings')}:</strong> {lang === 'ar' && r.findingsAr ? r.findingsAr : r.findingsEn}</p>
                              )}
                              {r.treatmentPlanEn && (
                                <p style={{marginTop:6}}><strong>{t('tpcTreatment')}:</strong> {lang === 'ar' && r.treatmentPlanAr ? r.treatmentPlanAr : r.treatmentPlanEn}</p>
                              )}
                              <p style={{marginTop:8, fontSize:12}}>
                                <Link to="/supplier-resilience" style={{ marginInlineEnd: 8, padding: '2px 8px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, color: 'var(--primary-700)', textDecoration: 'none' }}>{t('tpcLinkResilience')} →</Link>
                                <Link to="/processor-assessments" style={{ marginInlineEnd: 8, padding: '2px 8px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, color: 'var(--primary-700)', textDecoration: 'none' }}>{t('tpcLinkProcessor')} →</Link>
                                <Link to="/fourth-party" style={{ marginInlineEnd: 8, padding: '2px 8px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, color: 'var(--primary-700)', textDecoration: 'none' }}>{t('tpcLinkFourthParty')} →</Link>
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
        title={t('tpcNew')}
        fields={CREATE_FIELDS}
        initial={{ inherentCyberRisk: 'MEDIUM' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />

      <EntityDocsModal
        entityType="THIRD_PARTY_CYBER_ASSESSMENT"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.assessmentCode ?? undefined}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}
