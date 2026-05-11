import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Share2, ChevronRight, ChevronDown, CheckCircle2, FileText, Globe, AlertOctagon,
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
  listDsas, submitDsa, activateDsa, expireDsa, terminateDsa,
  DSA_STATUS_TONE, type DataSharingAgreement, type DsaStatus,
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
    { id: 'all',        label: `${t('dsaTabAll')} (${rows.length})` },
    { id: 'DRAFT',      label: `${t('dsaTabDraft')} (${stats.DRAFT})` },
    { id: 'IN_REVIEW',  label: `${t('dsaTabReview')} (${stats.IN_REVIEW})` },
    { id: 'ACTIVE',     label: `${t('dsaTabActive')} (${stats.ACTIVE})` },
    { id: 'EXPIRED',    label: `${t('dsaTabExpired')} (${stats.EXPIRED})` },
    { id: 'TERMINATED', label: `${t('dsaTabTerminated')} (${stats.TERMINATED})` },
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

  return (
    <Shell title={t('dsaPageTitle')} sub={t('dsaPageSub')}>
      <PageHeader eyebrow={t('navPrivacy')} title={t('dsaPageTitle')} sub={t('dsaPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Share2}      label={t('dsaKpiActive')}     value={stats.ACTIVE} tone="success" />
        <Kpi Icon={FileText}    label={t('dsaKpiDraft')}      value={stats.DRAFT + stats.IN_REVIEW} tone="warning" />
        <Kpi Icon={Globe}       label={t('dsaKpiCrossBorder')} value={stats.crossBorder} tone="info" />
        <Kpi Icon={AlertOctagon} label={t('dsaKpiRegulator')}  value={stats.regulator} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

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
                          <td>{r.recipientName}<br/><span className="mono" style={{fontSize:10, color:'var(--muted-foreground)'}}>{r.recipientCountry ?? '—'}</span></td>
                          <td><span style={{fontSize:11}}>{r.recipientType.replace(/_/g, ' ')}</span></td>
                          <td><span style={{fontSize:11}}>{r.lawfulBasis.replace(/_/g, ' ')}</span></td>
                          <td>{r.crossBorderMechanism !== 'NONE' ? <Badge tone="warning">{r.crossBorderMechanism}</Badge> : <span style={{color:'var(--muted-foreground)'}}>—</span>}</td>
                          <td><Badge tone={DSA_STATUS_TONE[r.status]}>{r.status.replace('_',' ')}</Badge></td>
                          <td style={{textAlign:'right'}}>
                            {r.status === 'DRAFT' && <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onSubmit(r.id)}>{t('dsaSubmit')}</Button>}
                            {r.status === 'IN_REVIEW' && <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busyId === r.id} onClick={() => onActivate(r.id)}>{t('dsaActivate')}</Button>}
                            {r.status === 'ACTIVE' && <>
                              <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onExpire(r.id)}>{t('dsaExpire')}</Button>{' '}
                              <Button size="sm" variant="danger" disabled={busyId === r.id} onClick={() => onTerminate(r.id)}>{t('dsaTerminate')}</Button>
                            </>}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={9} style={{background:'var(--muted)'}}>
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
                                  {r.linkedRopaCodes && <><strong>{t('dsaLinkedRopa')}:</strong> <span className="mono">{r.linkedRopaCodes}</span><br/></>}
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
    </Shell>
  );
}
