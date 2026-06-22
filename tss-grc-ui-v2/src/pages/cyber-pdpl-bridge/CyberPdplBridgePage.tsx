import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Workflow, AlertOctagon, CheckCircle2, ArrowRight, ChevronRight, ChevronDown, RefreshCw, Zap,
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
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listBridgeLog, triggerBridge,
  BRIDGE_OUTCOME_TONE, BRIDGE_EVENT_TONE,
  type BridgeLogEntry, type BridgeOutcome,
} from '../../lib/cyberPdplBridge';

type TabId = 'all' | 'CREATED' | 'LINKED' | 'SKIPPED' | 'ERROR';

export function CyberPdplBridgePage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<BridgeLogEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());
  const [openTrigger, setOpenTrigger] = useState(false);

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listBridgeLog(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('cpbLoadFailed')); }
    finally { setLoading(false); }
  }

  // ─── Manual bridge trigger ───────────────────────────────────────
  const triggerFields: FieldDef[] = [
    { key: 'cyberIncidentId', kind: 'number', required: true, min: 1, span: 6,
      label: isAr ? 'معرّف الحادثة السيبرانية' : 'Cyber incident ID' },
    { key: 'dataSubjectsAffectedEst', kind: 'number', min: 0, span: 6,
      label: isAr ? 'أصحاب البيانات المتأثرون (تقديري)' : 'Data subjects affected (est.)' },
    { key: 'dataCategoriesAffected', kind: 'text', span: 12,
      label: isAr ? 'فئات البيانات المتأثرة' : 'Data categories affected',
      placeholder: 'NATIONAL_ID, FINANCIAL, CONTACT' },
    { key: 'piiAssessment', kind: 'textarea', rows: 3, span: 12,
      label: isAr ? 'تقييم البيانات الشخصية' : 'PII assessment' },
  ];
  const triggerInitial: Record<string, string> = useMemo(() => ({
    cyberIncidentId: '', dataSubjectsAffectedEst: '', dataCategoriesAffected: '', piiAssessment: '',
  }), []);

  async function submitTrigger(v: Record<string, string>) {
    const incId = Number(v.cyberIncidentId);
    if (!incId || incId < 1) { toast.danger(isAr ? 'معرّف الحادثة مطلوب' : 'Cyber incident ID is required'); return; }
    const est = String(v.dataSubjectsAffectedEst ?? '').trim();
    try {
      const entry = await triggerBridge({
        cyberIncidentId: incId,
        dataSubjectsAffectedEst: est === '' ? undefined : Number(est),
        dataCategoriesAffected: String(v.dataCategoriesAffected ?? '').trim() || undefined,
        piiAssessment: String(v.piiAssessment ?? '').trim() || undefined,
      });
      toast.success(isAr ? `تم تشغيل الجسر — النتيجة: ${entry.outcome}` : `Bridge run — outcome: ${entry.outcome}`);
      setOpenTrigger(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تشغيل الجسر' : 'Bridge trigger failed'));
    }
  }

  const stats = useMemo(() => ({
    total:    rows.length,
    created:  rows.filter(r => r.outcome === 'CREATED').length,
    linked:   rows.filter(r => r.outcome === 'LINKED').length,
    skipped:  rows.filter(r => r.outcome === 'SKIPPED').length,
    error:    rows.filter(r => r.outcome === 'ERROR').length,
  }), [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => r.outcome === tab as BridgeOutcome);
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',     label: `${t('cpbTabAll')} (${rows.length})` },
    { id: 'CREATED', label: `${t('cpbTab_CREATED')} (${stats.created})` },
    { id: 'LINKED',  label: `${t('cpbTab_LINKED')} (${stats.linked})` },
    { id: 'SKIPPED', label: `${t('cpbTab_SKIPPED')} (${stats.skipped})` },
    { id: 'ERROR',   label: `${t('cpbTab_ERROR')} (${stats.error})` },
  ];

  function toggle(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  return (
    <Shell title={t('cpbPageTitle')} sub={t('cpbPageSub')}>
      <PageHeader
        eyebrow={t('navPrivacy')}
        title={t('cpbPageTitle')}
        sub={t('cpbPageSub')}
        actions={
          <div style={{ display: 'inline-flex', gap: 8 }}>
            <Button variant="primary" Icon={Zap} onClick={() => setOpenTrigger(true)}>
              {isAr ? 'تشغيل الجسر' : 'Trigger bridge'}
            </Button>
            <Button variant="ghost" Icon={RefreshCw} onClick={() => void reload()}>{t('cpbRefresh')}</Button>
          </div>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={Workflow}     label={t('cpbKpiTotal')}   value={stats.total}   tone="primary"
             onClick={() => setTab('all')} active={tab === 'all'} />
        <Kpi Icon={CheckCircle2} label={t('cpbKpiCreated')} value={stats.created} tone="success" to="/data-officer/breaches" />
        <Kpi Icon={ArrowRight}   label={t('cpbKpiLinked')}  value={stats.linked}  tone="primary" to="/data-officer/breaches" />
        <Kpi Icon={AlertOctagon} label={t('cpbKpiError')}   value={stats.error}   tone="danger"
             onClick={() => setTab('ERROR')} active={tab === 'ERROR'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('cpbPageTitle')} Icon={Workflow}>
        {loading
          ? <EmptyState Icon={Workflow} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Workflow} title={t('cpbEmpty')} sub={t('cpbEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('cpbColWhen')}</th>
                    <th>{t('cpbColEvent')}</th>
                    <th>{t('cpbColIncident')}</th>
                    <th>{t('cpbColBreach')}</th>
                    <th>{t('cpbColOutcome')}</th>
                    <th>{t('cpbColRule')}</th>
                    <th>{t('cpbColTrigger')}</th>
                    <th>{t('cpbColActor')}</th>
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
                          <td className="mono">{new Date(r.triggeredAt).toLocaleString()}</td>
                          <td><Badge tone={BRIDGE_EVENT_TONE[r.eventCode]}>{r.eventCode.replace('_', ' ')}</Badge></td>
                          <td className="mono">{r.cyberIncidentNumber ?? '—'}</td>
                          <td className="mono" style={{ fontSize: 11 }}>
                            {r.dataBreachExternalId
                              ? r.dataBreachExternalId.substring(0, 8) + '…'
                              : '—'}
                          </td>
                          <td><Badge tone={BRIDGE_OUTCOME_TONE[r.outcome]}>{r.outcome}</Badge></td>
                          <td className="mono" style={{ fontSize: 11 }}>{r.ruleName}</td>
                          <td style={{ fontSize: 12, maxWidth: 380 }}>
                            {r.triggerReason && r.triggerReason.length > 80
                              ? r.triggerReason.substring(0, 80) + '…'
                              : r.triggerReason}
                          </td>
                          <td>{r.triggeredByUsername ?? '—'}</td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {r.triggerReason && (
                                  <p style={{ marginBottom: 8 }}>
                                    <strong>{t('cpbTriggerReason')}:</strong> {r.triggerReason}
                                  </p>
                                )}
                                {r.outcomeDetail && (
                                  <p style={{ marginBottom: 8 }}>
                                    <strong>{t('cpbOutcomeDetail')}:</strong> {r.outcomeDetail}
                                  </p>
                                )}
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 12 }}>
                                  <div>
                                    <strong>{t('cpbCyberIncident')}:</strong>{' '}
                                    <span className="mono">{r.cyberIncidentNumber ?? '—'}</span> (id: <span className="mono">{r.cyberIncidentId ?? '—'}</span>)
                                  </div>
                                  <div>
                                    <strong>{t('cpbDataBreach')}:</strong>{' '}
                                    <span className="mono">{r.dataBreachExternalId ?? '—'}</span>{r.dataBreachId ? ` (id: ${r.dataBreachId})` : ''}
                                  </div>
                                </div>
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

      <FormModal
        open={openTrigger}
        title={isAr ? 'تشغيل جسر الأمن السيبراني ← الخصوصية' : 'Trigger cyber → PDPL bridge'}
        fields={triggerFields}
        initial={triggerInitial}
        submitLabel={isAr ? 'تشغيل الجسر' : 'Run bridge'}
        onClose={() => setOpenTrigger(false)}
        onSubmit={submitTrigger}
      />
    </Shell>
  );
}
