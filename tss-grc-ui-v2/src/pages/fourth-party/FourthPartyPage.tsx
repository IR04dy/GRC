import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Network, ChevronRight, ChevronDown, AlertTriangle, CheckCircle2, Shield, Globe, Plus,
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
  listFourthParty, validateFourthParty, mitigateFourthParty, retireFourthParty,
  FP_STATUS_TONE, FP_CRIT_TONE, FP_CONC_TONE,
  type FourthPartyExposure, type FpStatus,
} from '../../lib/fourthParty';

/**
 * FourthPartyPage — gap-analysis #1 (SCM sub-tier visibility).
 *
 * Lists every (primary vendor → fourth party) edge with concentration
 * tier, downstream criticality, and validation status. State machine:
 * DISCOVERED → VALIDATED → MITIGATED → RETIRED.
 */
type TabId = 'all' | 'DISCOVERED' | 'VALIDATED' | 'MITIGATED' | 'RETIRED' | 'concentration';

export function FourthPartyPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<FourthPartyExposure[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [createOpen, setCreateOpen] = useState(false);

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listFourthParty(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('f4pLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { DISCOVERED: 0, VALIDATED: 0, MITIGATED: 0, RETIRED: 0,
                highConc: 0, critical: 0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.concentrationRisk === 'HIGH') s.highConc++;
      if (r.downstreamCriticality === 'CRITICAL') s.critical++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all')           return rows;
    if (tab === 'concentration') return rows.filter(r => r.concentrationRisk === 'HIGH' || r.concentrationRisk === 'MEDIUM');
    return rows.filter(r => r.status === tab);
  }, [tab, rows]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',           label: `${t('f4pTabAll')} (${rows.length})` },
    { id: 'DISCOVERED',    label: `${t('f4pTabDiscovered')} (${stats.DISCOVERED})` },
    { id: 'VALIDATED',     label: `${t('f4pTabValidated')} (${stats.VALIDATED})` },
    { id: 'MITIGATED',     label: `${t('f4pTabMitigated')} (${stats.MITIGATED})` },
    { id: 'RETIRED',       label: `${t('f4pTabRetired')} (${stats.RETIRED})` },
    { id: 'concentration', label: `${t('f4pTabConcentration')} (${stats.highConc + rows.filter(r=>r.concentrationRisk==='MEDIUM').length})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onValidate(id: number) {
    setBusyId(id);
    try { await validateFourthParty(id, 'admin'); toast.success(t('f4pValidated')); await reload(); }
    catch { toast.danger(t('f4pActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onMitigate(id: number) {
    const notes = window.prompt(t('f4pMitigatePrompt'), '');
    if (!notes || !notes.trim()) return;
    setBusyId(id);
    try { await mitigateFourthParty(id, notes.trim()); toast.success(t('f4pMitigated')); await reload(); }
    catch { toast.danger(t('f4pActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onRetire(id: number) {
    if (!window.confirm(t('f4pRetireConfirm'))) return;
    setBusyId(id);
    try { await retireFourthParty(id); toast.success(t('f4pRetired')); await reload(); }
    catch { toast.danger(t('f4pActionFailed')); }
    finally { setBusyId(null); }
  }

  const CREATE_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'primaryVendorName',     label: t('f4pColPrimary') + ' *', required: true, span: 6 },
    { kind: 'text',   key: 'fourthPartyName',       label: t('f4pColFourth')  + ' *', required: true, span: 6 },
    { kind: 'text',   key: 'fourthPartyCountry',    label: t('f4pColCountry'),        span: 3, maxLength: 3, placeholder: 'USA' },
    { kind: 'select', key: 'dependencyType',        label: t('f4pColType') + ' *', required: true, span: 4,
      options: [ {value:'DATA_PROCESSING',label:'Data processing'},{value:'INFRASTRUCTURE',label:'Infrastructure'},{value:'SOFTWARE',label:'Software'},{value:'LOGISTICS',label:'Logistics'},{value:'FINANCIAL',label:'Financial'},{value:'SUPPORT',label:'Support'} ] },
    { kind: 'select', key: 'downstreamCriticality', label: t('f4pColCrit') + ' *', required: true, span: 4,
      options: [ {value:'LOW',label:'Low'},{value:'MEDIUM',label:'Medium'},{value:'HIGH',label:'High'},{value:'CRITICAL',label:'Critical'} ] },
    { kind: 'textarea', key: 'serviceDescriptionEn', label: t('f4pServiceEn') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'serviceDescriptionAr', label: t('f4pServiceAr'),       rows: 2, span: 12 },
    { kind: 'text',     key: 'dataCategoriesShared', label: t('f4pColCategories'),    span: 12 },
  ];

  async function onCreate(values: Record<string, unknown>) {
    try {
      await api.post('/api/scm/fourth-party', {
        primaryVendorName:     String(values.primaryVendorName ?? '').trim(),
        fourthPartyName:       String(values.fourthPartyName ?? '').trim(),
        fourthPartyCountry:    String(values.fourthPartyCountry ?? '').trim() || undefined,
        dependencyType:        values.dependencyType,
        downstreamCriticality: values.downstreamCriticality,
        serviceDescriptionEn:  String(values.serviceDescriptionEn ?? '').trim(),
        serviceDescriptionAr:  String(values.serviceDescriptionAr ?? '').trim() || undefined,
        dataCategoriesShared:  String(values.dataCategoriesShared ?? '').trim() || undefined,
      });
      toast.success(t('f4pCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('f4pActionFailed')); }
  }

  return (
    <Shell title={t('f4pPageTitle')} sub={t('f4pPageSub')}>
      <PageHeader eyebrow={t('navThirdParty')} title={t('f4pPageTitle')} sub={t('f4pPageSub')}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('f4pNew')}</Button>} />

      <div className="kpi-grid">
        <Kpi Icon={Network}       label={t('f4pKpiTotal')}       value={rows.length} tone="primary" />
        <Kpi Icon={CheckCircle2}  label={t('f4pKpiValidated')}   value={stats.VALIDATED} tone="success" />
        <Kpi Icon={AlertTriangle} label={t('f4pKpiHighConc')}    value={stats.highConc} tone="danger" />
        <Kpi Icon={Shield}        label={t('f4pKpiCritical')}    value={stats.critical} tone="warning" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('f4pPageTitle')} Icon={Network}>
        {loading
          ? <EmptyState Icon={Network} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Network} title={t('f4pEmpty')} sub={t('f4pEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('f4pColPrimary')}</th>
                    <th>{t('f4pColFourth')}</th>
                    <th><Globe size={14}/></th>
                    <th>{t('f4pColType')}</th>
                    <th>{t('f4pColCrit')}</th>
                    <th>{t('f4pColConc')}</th>
                    <th>{t('f4pColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggle(r.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td>{r.primaryVendorName}</td>
                          <td><strong>{r.fourthPartyName}</strong></td>
                          <td className="mono">{r.fourthPartyCountry ?? '—'}</td>
                          <td>{r.dependencyType.replace(/_/g, ' ')}</td>
                          <td><Badge tone={FP_CRIT_TONE[r.downstreamCriticality]}>{r.downstreamCriticality}</Badge></td>
                          <td><Badge tone={FP_CONC_TONE[r.concentrationRisk]}>{r.concentrationRisk}</Badge></td>
                          <td><Badge tone={FP_STATUS_TONE[r.status]}>{r.status}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            {r.status === 'DISCOVERED' && (
                              <Button size="sm" variant="primary" disabled={busyId === r.id} onClick={() => onValidate(r.id)}>
                                {t('f4pValidate')}
                              </Button>
                            )}
                            {r.status === 'VALIDATED' && (
                              <Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onMitigate(r.id)}>
                                {t('f4pMitigate')}
                              </Button>
                            )}
                            {(r.status === 'MITIGATED' || r.status === 'VALIDATED') && (
                              <>{' '}<Button size="sm" variant="ghost" disabled={busyId === r.id} onClick={() => onRetire(r.id)}>
                                {t('f4pRetire')}
                              </Button></>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr><td colSpan={9} style={{ background: 'var(--muted)' }}>
                            <div style={{ padding: 12, fontSize: 13 }}>
                              <p>{lang === 'ar' && r.serviceDescriptionAr ? r.serviceDescriptionAr : r.serviceDescriptionEn}</p>
                              {r.dataCategoriesShared && (
                                <p style={{ marginTop: 6 }}><strong>{t('f4pColCategories')}:</strong> <span className="mono">{r.dataCategoriesShared}</span></p>
                              )}
                              <p style={{ marginTop: 6 }}>
                                <strong>{t('f4pSubcontracting')}:</strong>{' '}{r.subContractingAllowed ? '✓' : '✗'}
                                {' · '}
                                <strong>{t('f4pFlowDown')}:</strong>{' '}{r.flowDownClausesPresent ? '✓' : '✗'}
                              </p>
                              {r.lastValidatedAt && (
                                <p style={{ marginTop: 6, fontSize: 12, color: 'var(--muted-foreground)' }}>
                                  {t('f4pLastValidated')}: {r.lastValidatedAt.slice(0, 10)} ({r.validatorName ?? '—'})
                                  {r.nextValidationDue ? ` · ${t('f4pNextDue')}: ${r.nextValidationDue}` : ''}
                                </p>
                              )}
                              {r.mitigationNotesEn && (
                                <p style={{ marginTop: 6 }}>
                                  <strong>{t('f4pMitigationNotes')}:</strong>{' '}
                                  {lang === 'ar' && r.mitigationNotesAr ? r.mitigationNotesAr : r.mitigationNotesEn}
                                </p>
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
        title={t('f4pNew')}
        fields={CREATE_FIELDS}
        initial={{ dependencyType: 'INFRASTRUCTURE', downstreamCriticality: 'MEDIUM' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
