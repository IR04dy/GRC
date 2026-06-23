import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Telescope, Plus, CheckCircle2, XCircle, ArrowUpRight, Eye, FileText,
  ChevronRight, ChevronDown, AlertTriangle,
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
import {
  listEmergingRisks, createEmergingRisk, reviewEmergingRisk, escalateEmergingRisk,
  matureEmergingRisk, dismissEmergingRisk,
  EM_STATUS_TONE, EM_SIGNAL_TONE,
  type EmergingRisk, type EmRiskCategory, type EmRiskHorizon,
} from '../../lib/emergingRisks';

type TabId = 'all' | 'horizon' | 'monitoring' | 'escalated' | 'matured' | 'dismissed';

interface FormValues {
  titleEn?: string; titleAr?: string;
  descriptionEn?: string; descriptionAr?: string;
  category?: string; horizon?: string; signalStrength?: string; velocity?: string; source?: string;
  estimatedLikelihood?: number; estimatedImpact?: number;
  sourceCitation?: string; mitigationThoughtsEn?: string; owner?: string; tags?: string;
}
const INITIAL: FormValues = {
  category: 'REGULATORY', horizon: 'MID_TERM_12M',
  signalStrength: 'MODERATE', velocity: 'MEDIUM', source: 'HORIZON_SCAN',
};

export function EmergingRiskPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<EmergingRisk[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listEmergingRisks(undefined, 0, 200);
      setRows(page.content);
    } catch { toast.danger(t('emrLoadFailed')); }
    finally { setLoading(false); }
  }

  const stats = useMemo(() => {
    const s = { HORIZON:0, MONITORING:0, ESCALATED:0, MATURED:0, DISMISSED:0,
                confirmed:0, fast:0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.signalStrength === 'CONFIRMED') s.confirmed++;
      if (r.velocity === 'FAST') s.fast++;
    });
    return s;
  }, [rows]);

  const filtered = useMemo(() => {
    if (tab === 'all') return rows;
    return rows.filter(r => r.status === tab.toUpperCase());
  }, [rows, tab]);

  const tabs: TabItem<TabId>[] = [
    { id: 'all',        label: `${t('emrTabAll')} (${rows.length})` },
    { id: 'horizon',    label: `${t('emrTabHorizon')} (${stats.HORIZON})` },
    { id: 'monitoring', label: `${t('emrTabMonitoring')} (${stats.MONITORING})` },
    { id: 'escalated',  label: `${t('emrTabEscalated')} (${stats.ESCALATED})` },
    { id: 'matured',    label: `${t('emrTabMatured')} (${stats.MATURED})` },
    { id: 'dismissed',  label: `${t('emrTabDismissed')} (${stats.DISMISSED})` },
  ];

  function toggleExpanded(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  async function onReview(id: number) {
    const notes = window.prompt(t('emrReviewPrompt'), '');
    if (!notes || !notes.trim()) return;
    setBusyId(id);
    try { await reviewEmergingRisk(id, notes.trim()); toast.success(t('emrReviewed')); await reload(); }
    catch { toast.danger(t('emrActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onEscalate(id: number) {
    const code = window.prompt(t('emrEscalatePrompt'), 'RSK-2026-');
    if (!code || !code.trim()) return;
    setBusyId(id);
    try { await escalateEmergingRisk(id, code.trim()); toast.success(t('emrEscalated')); await reload(); }
    catch { toast.danger(t('emrActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onMature(id: number) {
    if (!window.confirm(t('emrMatureConfirm'))) return;
    setBusyId(id);
    try { await matureEmergingRisk(id); toast.success(t('emrMatured')); await reload(); }
    catch { toast.danger(t('emrActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onDismiss(id: number) {
    const reason = window.prompt(t('emrDismissPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await dismissEmergingRisk(id, reason.trim()); toast.success(t('emrDismissed')); await reload(); }
    catch { toast.danger(t('emrActionFailed')); }
    finally { setBusyId(null); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'titleEn', label: t('emrTitleEn') + ' *', required: true, span: 12 },
    { kind: 'text', key: 'titleAr', label: t('emrTitleAr'), span: 12 },
    { kind: 'select', key: 'category', label: t('emrCategory') + ' *', required: true, span: 6,
      options: [
        { value: 'REGULATORY',    label: t('emrCat_REGULATORY')    },
        { value: 'GEOPOLITICAL',  label: t('emrCat_GEOPOLITICAL')  },
        { value: 'TECHNOLOGY',    label: t('emrCat_TECHNOLOGY')    },
        { value: 'ENVIRONMENTAL', label: t('emrCat_ENVIRONMENTAL') },
        { value: 'ECONOMIC',      label: t('emrCat_ECONOMIC')      },
        { value: 'SOCIAL',        label: t('emrCat_SOCIAL')        },
        { value: 'OTHER',         label: t('emrCat_OTHER')         },
      ] },
    { kind: 'select', key: 'horizon', label: t('emrHorizon') + ' *', required: true, span: 6,
      options: [
        { value: 'NEAR_TERM_6M',  label: t('emrHor_NEAR') },
        { value: 'MID_TERM_12M',  label: t('emrHor_MID')  },
        { value: 'LONG_TERM_24M', label: t('emrHor_LONG') },
      ] },
    { kind: 'select', key: 'velocity', label: t('emrVelocity'), span: 4,
      options: [{value:'SLOW',label:'SLOW'},{value:'MEDIUM',label:'MEDIUM'},{value:'FAST',label:'FAST'}] },
    { kind: 'select', key: 'signalStrength', label: t('emrSignal'), span: 4,
      options: [{value:'WEAK',label:'WEAK'},{value:'MODERATE',label:'MODERATE'},{value:'STRONG',label:'STRONG'},{value:'CONFIRMED',label:'CONFIRMED'}] },
    { kind: 'select', key: 'source', label: t('emrSource'), span: 4,
      options: [
        { value: 'HORIZON_SCAN',    label: 'Horizon scan' },
        { value: 'INDUSTRY_REPORT', label: 'Industry report' },
        { value: 'NEWS',            label: 'News' },
        { value: 'REGULATOR',       label: 'Regulator' },
        { value: 'INTERNAL_INTEL',  label: 'Internal intel' },
        { value: 'OTHER',           label: 'Other' },
      ] },
    { kind: 'number', key: 'estimatedLikelihood', label: t('emrLikelihood'), min: 1, max: 5, span: 6 },
    { kind: 'number', key: 'estimatedImpact',     label: t('emrImpact'),     min: 1, max: 5, span: 6 },
    { kind: 'textarea', key: 'descriptionEn', label: t('emrDescriptionEn'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'descriptionAr', label: t('emrDescriptionAr'), rows: 2, span: 12 },
    { kind: 'text', key: 'sourceCitation', label: t('emrSourceCitation'), span: 12 },
    { kind: 'textarea', key: 'mitigationThoughtsEn', label: t('emrMitigationEn'), rows: 2, span: 12 },
    { kind: 'text', key: 'owner', label: t('emrOwner'), span: 8 },
    { kind: 'text', key: 'tags',  label: t('emrTags'),  span: 4 },
  ];

  async function onCreate(v: FormValues) {
    try {
      await createEmergingRisk({
        titleEn:        (v.titleEn ?? '').trim(),
        titleAr:        (v.titleAr ?? '').trim() || undefined,
        descriptionEn:  (v.descriptionEn ?? '').trim() || undefined,
        descriptionAr:  (v.descriptionAr ?? '').trim() || undefined,
        category:       (v.category ?? 'REGULATORY') as EmRiskCategory,
        horizon:        (v.horizon ?? 'MID_TERM_12M') as EmRiskHorizon,
        velocity:       v.velocity as 'SLOW'|'MEDIUM'|'FAST' | undefined,
        signalStrength: v.signalStrength as 'WEAK'|'MODERATE'|'STRONG'|'CONFIRMED' | undefined,
        source:         v.source as 'HORIZON_SCAN'|'INDUSTRY_REPORT'|'NEWS'|'REGULATOR'|'INTERNAL_INTEL'|'OTHER' | undefined,
        estimatedLikelihood: v.estimatedLikelihood ? Number(v.estimatedLikelihood) : undefined,
        estimatedImpact:     v.estimatedImpact ? Number(v.estimatedImpact) : undefined,
        sourceCitation:      (v.sourceCitation ?? '').trim() || undefined,
        mitigationThoughtsEn: (v.mitigationThoughtsEn ?? '').trim() || undefined,
        owner:               (v.owner ?? '').trim() || undefined,
        tags:                (v.tags  ?? '').trim() || undefined,
      });
      toast.success(t('emrCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('emrActionFailed')); }
  }

  return (
    <Shell title={t('emrPageTitle')} sub={t('emrPageSub')}>
      <PageHeader
        eyebrow={t('navRisk')}
        title={t('emrPageTitle')}
        sub={t('emrPageSub')}
        actions={<Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{t('emrNew')}</Button>}
      />

      <div className="kpi-grid">
        <Kpi Icon={Eye}            label={t('emrKpiHorizon')}    value={stats.HORIZON}    tone="info"
          onClick={() => setTab('horizon')}    active={tab === 'horizon'} />
        <Kpi Icon={Telescope}      label={t('emrKpiMonitoring')} value={stats.MONITORING} tone="primary"
          onClick={() => setTab('monitoring')} active={tab === 'monitoring'} />
        <Kpi Icon={ArrowUpRight}   label={t('emrKpiEscalated')}  value={stats.ESCALATED}  tone="warning"
          onClick={() => setTab('escalated')}  active={tab === 'escalated'} />
        <Kpi Icon={AlertTriangle}  label={t('emrKpiConfirmed')}  value={stats.confirmed}  tone="danger" to="/emerging-risk" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <Card title={t('emrPageTitle')} Icon={Telescope}>
        {loading
          ? <EmptyState Icon={Telescope} title={t('loading')} />
          : filtered.length === 0
            ? <EmptyState Icon={Telescope} title={t('emrEmpty')} sub={t('emrEmptyHint')} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th style={{ width: 28 }} />
                    <th>{t('emrColCode')}</th>
                    <th>{t('emrColTitle')}</th>
                    <th>{t('emrColCategory')}</th>
                    <th>{t('emrColHorizon')}</th>
                    <th>{t('emrColSignal')}</th>
                    <th style={{ textAlign: 'right' }}>L×I</th>
                    <th>{t('scnColStatus')}</th>
                    <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {filtered.map(r => {
                    const open = expanded.has(r.id);
                    const li = (r.estimatedLikelihood ?? 0) * (r.estimatedImpact ?? 0);
                    const active = r.status === 'HORIZON' || r.status === 'MONITORING';
                    return (
                      <Fragment key={r.id}>
                        <tr>
                          <td>
                            <button onClick={() => toggleExpanded(r.id)}
                              style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                              {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                            </button>
                          </td>
                          <td className="mono">{r.riskCode}</td>
                          <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                          <td>{t(`emrCat_${r.category}`)}</td>
                          <td>{t(`emrHor_${r.horizon.startsWith('NEAR') ? 'NEAR' : r.horizon.startsWith('MID') ? 'MID' : 'LONG'}`)}</td>
                          <td><Badge tone={EM_SIGNAL_TONE[r.signalStrength]}>{r.signalStrength}</Badge></td>
                          <td style={{ textAlign: 'right' }} className="mono">{li || '—'}</td>
                          <td><Badge tone={EM_STATUS_TONE[r.status]}>{t(`emrStatus_${r.status}`)}</Badge></td>
                          <td style={{ textAlign: 'right' }}>
                            {active && (
                              <>
                                <Button size="sm" variant="primary" Icon={FileText}
                                  disabled={busyId === r.id} onClick={() => onReview(r.id)}>
                                  {t('emrReview')}
                                </Button>
                                {' '}
                                <Button size="sm" variant="primary" Icon={ArrowUpRight}
                                  disabled={busyId === r.id} onClick={() => onEscalate(r.id)}>
                                  {t('emrEscalate')}
                                </Button>
                                {' '}
                                <Button size="sm" variant="ghost" Icon={CheckCircle2}
                                  disabled={busyId === r.id} onClick={() => onMature(r.id)}>
                                  {t('emrMature')}
                                </Button>
                                {' '}
                                <Button size="sm" variant="danger" Icon={XCircle}
                                  disabled={busyId === r.id} onClick={() => onDismiss(r.id)}>
                                  {t('emrDismiss')}
                                </Button>
                              </>
                            )}
                          </td>
                        </tr>
                        {open && (
                          <tr>
                            <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                              <div style={{ padding: 12, fontSize: 13 }}>
                                {r.descriptionEn && (
                                  <p style={{ marginBottom: 6 }}>
                                    {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
                                  </p>
                                )}
                                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                                  <div>
                                    <strong>{t('emrSource')}:</strong> {r.source}
                                    {r.sourceCitation && <span style={{ color:'var(--ink-3)' }}> — {r.sourceCitation}</span>}
                                    <br />
                                    <strong>{t('emrVelocity')}:</strong> {r.velocity}
                                    {' · '}
                                    <strong>{t('emrOwner')}:</strong> {r.owner ?? '—'}
                                    <br />
                                    {r.tags && <><strong>{t('emrTags')}:</strong> <span className="mono">{r.tags}</span></>}
                                  </div>
                                  <div>
                                    <strong>{t('emrFirstObserved')}:</strong>{' '}
                                    <span className="mono">{new Date(r.firstObservedAt).toLocaleDateString()}</span>
                                    <br />
                                    {r.lastReviewedAt && (
                                      <><strong>{t('emrLastReview')}:</strong>{' '}
                                      <span className="mono">{new Date(r.lastReviewedAt).toLocaleDateString()}</span><br /></>
                                    )}
                                    {r.nextReviewDue && (
                                      <><strong>{t('emrNextReview')}:</strong>{' '}
                                      <span className="mono">{r.nextReviewDue}</span></>
                                    )}
                                  </div>
                                </div>
                                {r.mitigationThoughtsEn && (
                                  <p style={{ marginTop: 6 }}>
                                    <strong>{t('emrMitigationEn')}:</strong>{' '}
                                    {lang === 'ar' && r.mitigationThoughtsAr ? r.mitigationThoughtsAr : r.mitigationThoughtsEn}
                                  </p>
                                )}
                                {r.lastReviewNotes && (
                                  <p style={{ marginTop: 6, color: 'var(--ink-3)' }}>
                                    <strong>{t('emrLastReviewNotes')}:</strong> {r.lastReviewNotes}
                                  </p>
                                )}
                                {r.linkedRiskCode && (
                                  <p style={{ marginTop: 6, color: 'var(--primary)' }}>
                                    <strong>{t('emrLinkedRisk')}:</strong>{' '}
                                    <span className="mono">{r.linkedRiskCode}</span>
                                  </p>
                                )}
                                {r.dismissReason && (
                                  <p style={{ marginTop: 6, color: 'var(--danger)' }}>
                                    <strong>{t('emrDismissReason')}:</strong> {r.dismissReason}
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

      <FormModal<FormValues>
        open={createOpen}
        title={t('emrNew')}
        fields={FIELDS}
        initial={INITIAL}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
