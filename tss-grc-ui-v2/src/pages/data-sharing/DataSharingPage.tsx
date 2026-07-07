import { useEffect, useMemo, useState, Fragment } from 'react';
import { Link } from 'react-router-dom';
import {
  Share2, ChevronRight, ChevronDown, CheckCircle2, FileText, Globe, AlertOctagon, Plus, FolderOpen,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { DataSharingRequestButton } from '../../components/workflow/Section9RequestForms';
import { DataSharingApprovalButton } from '../../components/workflow/ApprovalRequestForms';
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
  listDsas, submitDsa, activateDsa, expireDsa, terminateDsa,
  DSA_STATUS_TONE, type DataSharingAgreement,
} from '../../lib/dataSharing';

type TabId = 'all' | 'DRAFT' | 'IN_REVIEW' | 'ACTIVE' | 'EXPIRED' | 'TERMINATED';

export function DataSharingPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<DataSharingAgreement[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);
  const [docsTarget, setDocsTarget] = useState<DataSharingAgreement | null>(null);

  useEffect(() => { void reload(); }, []);
  async function reload() {
    setLoading(true);
    try {
      const page = await listDsas(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('dsaLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { DRAFT:0, IN_REVIEW:0, ACTIVE:0, EXPIRED:0, TERMINATED:0, crossBorder:0, regulator:0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.crossBorderMechanism && r.crossBorderMechanism !== 'NONE') s.crossBorder++;
      if (r.recipientType === 'REGULATOR' || r.recipientType === 'PUBLIC_AUTHORITY') s.regulator++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => r.status === tab);
  }, [tab, rows]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',        label: t('dsaTabAll') },
    { id: 'DRAFT',      label: t('dsaTabDraft'),      count: stats.DRAFT },
    { id: 'IN_REVIEW',  label: t('dsaTabReview'),     count: stats.IN_REVIEW },
    { id: 'ACTIVE',     label: t('dsaTabActive'),     count: stats.ACTIVE },
    { id: 'EXPIRED',    label: t('dsaTabExpired'),    count: stats.EXPIRED },
    { id: 'TERMINATED', label: t('dsaTabTerminated'), count: stats.TERMINATED },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onSubmit(id: number)   { try { setBusyId(id); await submitDsa(id);   toast.success(t('dsaSubmitted'));  await reload(); } catch { toast.danger(t('dsaActionFailed')); } finally { setBusyId(null); } }
  async function onActivate(id: number) { try { setBusyId(id); await activateDsa(id, 'admin', 'counterparty'); toast.success(t('dsaActivated')); await reload(); } catch { toast.danger(t('dsaActionFailed')); } finally { setBusyId(null); } }
  async function onExpire(id: number)   { if(!window.confirm(t('dsaExpireConfirm'))) return; try { setBusyId(id); await expireDsa(id); toast.success(t('dsaExpired')); await reload(); } catch { toast.danger(t('dsaActionFailed')); } finally { setBusyId(null); } }
  async function onTerminate(id: number){ const reason = window.prompt(t('dsaTerminatePrompt'), ''); if(!reason||!reason.trim()) return; try { setBusyId(id); await terminateDsa(id, reason.trim()); toast.success(t('dsaTerminated')); await reload(); } catch { toast.danger(t('dsaActionFailed')); } finally { setBusyId(null); } }

  const CREATE_FIELDS: FieldDef[] = [
    { kind: 'text', key: 'agreementCode', label: t('dsaColCode'), span: 4, placeholder: 'DSA-2026-009' },
    { kind: 'text', key: 'titleEn',  label: t('dsaColTitle') + ' (EN) *', required: true, span: 8 },
    { kind: 'text', key: 'titleAr',  label: t('dsaColTitle') + ' (AR)', span: 12 },
    { kind: 'text', key: 'recipientName', label: t('dsaColRecipient') + ' *', required: true, span: 8 },
    { kind: 'text', key: 'recipientCountry', label: t('dsaColCountry'), maxLength: 3, span: 4, placeholder: 'SAU' },
    { kind: 'select', key: 'recipientType', label: t('dsaColType') + ' *', required: true, span: 6,
      options: [ {value:'REGULATOR',label:'Regulator'},{value:'CREDIT_BUREAU',label:'Credit bureau'},{value:'GROUP_AFFILIATE',label:'Group affiliate'},{value:'JOINT_VENTURE',label:'Joint venture'},{value:'PUBLIC_AUTHORITY',label:'Public authority'},{value:'RESEARCH',label:'Research'},{value:'COMMERCIAL_PARTNER',label:'Commercial partner'} ] },
    { kind: 'select', key: 'recipientLegalRole', label: t('dsaRecipientRole') + ' *', required: true, span: 6,
      options: [ {value:'CONTROLLER',label:'Controller'},{value:'JOINT_CONTROLLER',label:'Joint controller'},{value:'PROCESSOR',label:'Processor'} ] },
    { kind: 'select', key: 'lawfulBasis', label: t('dsaColBasis') + ' *', required: true, span: 6,
      options: [ {value:'CONSENT',label:'Consent'},{value:'LEGAL_OBLIGATION',label:'Legal obligation'},{value:'CONTRACT',label:'Contract'},{value:'VITAL_INTEREST',label:'Vital interest'},{value:'PUBLIC_INTEREST',label:'Public interest'},{value:'LEGITIMATE_INTEREST',label:'Legitimate interest'} ] },
    { kind: 'select', key: 'crossBorderMechanism', label: t('dsaColCrossBorder'), span: 6,
      options: [ {value:'NONE',label:'None (in-country)'},{value:'ADEQUACY',label:'Adequacy'},{value:'SCC',label:'SCC'},{value:'BCR',label:'BCR'},{value:'EXPLICIT_CONSENT',label:'Explicit consent'},{value:'CONTRACT_NECESSITY',label:'Contract necessity'},{value:'VITAL_INTEREST',label:'Vital interest'},{value:'PUBLIC_INTEREST',label:'Public interest'} ] },
    { kind: 'text', key: 'dataCategories', label: t('dsaCategories') + ' *', required: true, span: 8, placeholder: 'IDENTITY,CONTACT,FINANCIAL' },
    { kind: 'text', key: 'dataSubjects', label: t('dsaSubjects') + ' *', required: true, span: 4, placeholder: 'CUSTOMER' },
    { kind: 'textarea', key: 'purposeEn', label: t('dsaPurposeEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'purposeAr', label: t('dsaPurposeAr'), rows: 2, span: 12 },
    { kind: 'date', key: 'effectiveFrom', label: t('dsaEffectiveFrom'), span: 6 },
    { kind: 'date', key: 'effectiveTo',   label: t('dsaEffectiveTo'),   span: 6 },
    { kind: 'text', key: 'linkedRopaCodes', label: t('dsaLinkedRopa'), span: 12, placeholder: 'ROPA-001,ROPA-004' },
  ];

  async function onCreate(values: Record<string, unknown>) {
    try {
      await api.post('/api/privacy/data-sharing', {
        agreementCode: String(values.agreementCode ?? '').trim() || undefined,
        titleEn: String(values.titleEn ?? '').trim(),
        titleAr: String(values.titleAr ?? '').trim() || undefined,
        recipientName: String(values.recipientName ?? '').trim(),
        recipientCountry: String(values.recipientCountry ?? '').trim() || undefined,
        recipientType: values.recipientType,
        recipientLegalRole: values.recipientLegalRole,
        lawfulBasis: values.lawfulBasis,
        crossBorderMechanism: values.crossBorderMechanism || 'NONE',
        dataCategories: String(values.dataCategories ?? '').trim(),
        dataSubjects: String(values.dataSubjects ?? '').trim(),
        purposeEn: String(values.purposeEn ?? '').trim(),
        purposeAr: String(values.purposeAr ?? '').trim() || undefined,
        effectiveFrom: values.effectiveFrom || undefined,
        effectiveTo: values.effectiveTo || undefined,
        linkedRopaCodes: String(values.linkedRopaCodes ?? '').trim() || undefined,
      });
      toast.success(t('dsaCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('dsaActionFailed')); }
  }

  return (
    <Shell title={t('dsaPageTitle')} sub={t('dsaPageSub')}>
      <PageHeader eyebrow={t('navPrivacy')} title={t('dsaPageTitle')} sub={t('dsaPageSub')}
        actions={<>
          <DataSharingRequestButton />
          <Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('dsaNew')}</Button>
        </>} />

      <div className="kpi-grid">
        <Kpi Icon={Share2}      label={t('dsaKpiActive')}     value={stats.ACTIVE} tone="success" onClick={() => setTab('ACTIVE')} active={tab === 'ACTIVE'} />
        <Kpi Icon={FileText}    label={t('dsaKpiDraft')}      value={stats.DRAFT + stats.IN_REVIEW} tone="warning" onClick={() => setTab('DRAFT')} active={tab === 'DRAFT'} />
        <Kpi Icon={Globe}       label={t('dsaKpiCrossBorder')} value={stats.crossBorder} tone="info" to="/data-sharing" />
        <Kpi Icon={AlertOctagon} label={t('dsaKpiRegulator')}  value={stats.regulator} tone="primary" to="/data-sharing" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} hideEmpty />

      <Card title={t('dsaPageTitle')} Icon={Share2}>
        {loading
          ? <EmptyState Icon={Share2} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Share2} title={t('dsaEmpty')} sub={t('dsaEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{width:28}} />
                    <th>{t('dsaColCode')}</th>
                    <th>{t('dsaColTitle')}</th>
                    <th>{t('dsaColRecipient')}</th>
                    <th>{t('dsaColType')}</th>
                    <th>{t('dsaColBasis')}</th>
                    <th>{t('dsaColCrossBorder')}</th>
                    <th>{t('dsaColStatus')}</th>
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
                          <td className="mono">{r.agreementCode ?? '—'}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td>{r.recipientName}<br/><span className="mono" style={{fontSize:10, color:'var(--ink-3)'}}>{r.recipientCountry ?? '—'}</span></td>
                          <td><span style={{fontSize:11}}>{r.recipientType.replace(/_/g, ' ')}</span></td>
                          <td><span style={{fontSize:11}}>{r.lawfulBasis.replace(/_/g, ' ')}</span></td>
                          <td>{r.crossBorderMechanism !== 'NONE' ? <Badge tone="warning">{r.crossBorderMechanism}</Badge> : <span style={{color:'var(--ink-3)'}}>—</span>}</td>
                          <td><Badge tone={DSA_STATUS_TONE[r.status]}>{r.status.replace('_',' ')}</Badge></td>
                          <td style={{textAlign:'right'}}>
                            <Button size="sm" variant="ghost" Icon={FolderOpen} title={lang === 'ar' ? 'المستندات والضوابط' : 'Docs & controls'} onClick={() => setDocsTarget(r)} />{' '}
                            {r.status === 'DRAFT' && <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onSubmit(r.id)}>{t('dsaSubmit')}</Button>}
                            {r.status === 'IN_REVIEW' && <><DataSharingApprovalButton size="sm" sourceRecordId={r.id}
                              defaultTitleEn={`DSA approval — ${r.agreementCode ?? r.titleEn}`} />{' '}
                              <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === r.id} onClick={() => onActivate(r.id)}>{t('dsaActivate')}</Button></>}
                            {r.status === 'ACTIVE' && <>
                              <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onExpire(r.id)}>{t('dsaExpire')}</Button>{' '}
                              <Button size="sm" variant="danger" disabled={busyId === r.id} onClick={() => onTerminate(r.id)}>{t('dsaTerminate')}</Button>
                            </>}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={9} style={{background:'var(--surface-2)'}}>
                            <div style={{padding:12, fontSize:13}}>
                              <p>{lang === 'ar' && r.purposeAr ? r.purposeAr : r.purposeEn}</p>
                              <div style={{display:'grid', gridTemplateColumns:'1fr 1fr', gap:12, marginTop:8}}>
                                <div>
                                  <strong>{t('dsaCategories')}:</strong> <span className="mono">{r.dataCategories}</span><br/>
                                  <strong>{t('dsaSubjects')}:</strong> <span className="mono">{r.dataSubjects}</span><br/>
                                  <strong>{t('dsaRecipientRole')}:</strong> {r.recipientLegalRole.replace(/_/g, ' ')}<br/>
                                  {r.retentionPeriodDays && <><strong>{t('dsaRetention')}:</strong> {r.retentionPeriodDays} days<br/></>}
                                </div>
                                <div>
                                  {r.effectiveFrom && <><strong>{t('dsaEffective')}:</strong> {r.effectiveFrom} → {r.effectiveTo ?? t('dsaIndefinite')}<br/></>}
                                  {r.signedAt && <><strong>{t('dsaSigned')}:</strong> {r.signedAt.slice(0,10)} ({r.signerName ?? '—'})<br/></>}
                                  {r.linkedRopaCodes && (
                                    <><strong>{t('dsaLinkedRopa')}:</strong>{' '}
                                      {r.linkedRopaCodes.split(',').map(c => c.trim()).filter(Boolean).map(code => (
                                        <Link key={code} to={`/ropa`} state={{ ropaCode: code }}
                                          style={{ marginInlineEnd: 6, padding: '1px 6px', background: 'var(--primary-50)', border: '1px solid var(--primary-200)', borderRadius: 4, fontSize: 11, color: 'var(--primary-700)', textDecoration: 'none' }} className="mono">{code}</Link>
                                      ))}
                                      <br/>
                                    </>
                                  )}
                                  <strong>{t('dsaSubjectNotified')}:</strong> {r.subjectNotified ? '✓' : '✗'}{' · '}
                                  <strong>{t('dsaSubAllowed')}:</strong> {r.subDisclosureAllowed ? '✓' : '✗'}
                                </div>
                              </div>
                              {r.status === 'TERMINATED' && r.terminationReasonEn && (
                                <p style={{marginTop:8, color:'var(--danger)'}}><strong>{t('dsaTerminationReason')}:</strong> {r.terminationReasonEn}</p>
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
        title={t('dsaNew')}
        fields={CREATE_FIELDS}
        initial={{ recipientLegalRole: 'CONTROLLER', lawfulBasis: 'LEGAL_OBLIGATION', crossBorderMechanism: 'NONE' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />

      <EntityDocsModal
        entityType="DATA_SHARING_AGREEMENT"
        entityId={docsTarget?.id ?? null}
        open={!!docsTarget}
        recordLabel={docsTarget?.agreementCode ?? undefined}
        onClose={() => setDocsTarget(null)}
      />
    </Shell>
  );
}
