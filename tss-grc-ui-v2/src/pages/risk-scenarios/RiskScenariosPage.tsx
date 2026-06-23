import { useEffect, useMemo, useState, Fragment } from 'react';
import {
  Sigma, Plus, CheckCircle2, XCircle, Archive, FileText,
  ChevronRight, ChevronDown, AlertTriangle, TrendingUp, BarChart3,
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
  listScenarios, createScenario, approveScenario, rejectScenario, archiveScenario,
  parseMonteCarloResult, fmtSar,
  SCENARIO_STATUS_TONE, SCENARIO_TYPE_LABEL_KEY,
  type RiskScenario, type ScenarioType,
} from '../../lib/riskScenarios';

/**
 * RiskScenariosPage — Saudi GRC prompt §7.3 + §3.1.
 *
 * Persisted scenario-analysis register. KPI strip + status tabs +
 * expandable rows + state-aware action buttons (approve / reject /
 * archive). Inline modal supports the three scenario types.
 */

type TabId = 'all' | 'draft' | 'approved' | 'rejected' | 'archived' | 'stats';

interface CreateValues {
  titleEn?: string;        titleAr?: string;
  scenarioType?: string;
  riskCode?: string;       riskTitleEn?: string;
  horizonMonths?: number;
  descriptionEn?: string;  descriptionAr?: string;
  inputJson?: string;
  assumptionsEn?: string;  assumptionsAr?: string;
}

const CREATE_INITIAL: CreateValues = {
  scenarioType: 'MONTE_CARLO',
  horizonMonths: 12,
};

export function RiskScenariosPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('all');
  const [rows, setRows] = useState<RiskScenario[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [createOpen, setCreateOpen] = useState(false);
  const [expanded, setExpanded] = useState<Set<number>>(new Set());

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const page = await listScenarios(undefined, 0, 200);
      setRows(page.content);
    } catch {
      toast.danger(t('scnLoadFailed'));
    } finally { setLoading(false); }
  }

  // ── Filter rows by tab ─────────────────────────────────────────────
  const filtered = useMemo(() => {
    if (tab === 'all' || tab === 'stats') return rows;
    if (tab === 'draft')    return rows.filter(r => r.status === 'DRAFT');
    if (tab === 'approved') return rows.filter(r => r.status === 'APPROVED');
    if (tab === 'rejected') return rows.filter(r => r.status === 'REJECTED');
    if (tab === 'archived') return rows.filter(r => r.status === 'ARCHIVED');
    return rows;
  }, [tab, rows]);

  // ── KPIs ───────────────────────────────────────────────────────────
  const stats = useMemo(() => {
    const s = { DRAFT: 0, APPROVED: 0, REJECTED: 0, ARCHIVED: 0,
                avgExpected: 0, sumP95: 0, mc: 0, stress: 0, whatIf: 0 };
    rows.forEach(r => {
      s[r.status]++;
      if (r.status === 'APPROVED') {
        if (r.expectedLoss != null) s.avgExpected += r.expectedLoss;
        if (r.p95Loss != null) s.sumP95 += r.p95Loss;
      }
      if (r.scenarioType === 'MONTE_CARLO') s.mc++;
      if (r.scenarioType === 'STRESS_TEST') s.stress++;
      if (r.scenarioType === 'WHAT_IF')     s.whatIf++;
    });
    if (s.APPROVED > 0) s.avgExpected = Math.round(s.avgExpected / s.APPROVED);
    return s;
  }, [rows]);

  // ── Tabs ───────────────────────────────────────────────────────────
  const tabs: TabItem<TabId>[] = [
    { id: 'all',      label: `${t('scnTabAll')} (${rows.length})` },
    { id: 'draft',    label: `${t('scnTabDraft')} (${stats.DRAFT})` },
    { id: 'approved', label: `${t('scnTabApproved')} (${stats.APPROVED})` },
    { id: 'rejected', label: `${t('scnTabRejected')} (${stats.REJECTED})` },
    { id: 'archived', label: `${t('scnTabArchived')} (${stats.ARCHIVED})` },
    { id: 'stats',    label: t('scnTabStats') },
  ];

  // ── Approve / reject / archive ─────────────────────────────────────
  async function onApprove(id: number) {
    setBusyId(id);
    try { await approveScenario(id); toast.success(t('scnApproved')); await reload(); }
    catch { toast.danger(t('scnApproveFailed')); }
    finally { setBusyId(null); }
  }
  async function onReject(id: number) {
    const reason = window.prompt(t('scnRejectReasonPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await rejectScenario(id, reason.trim()); toast.success(t('scnRejected')); await reload(); }
    catch { toast.danger(t('scnRejectFailed')); }
    finally { setBusyId(null); }
  }
  async function onArchive(id: number) {
    const reason = window.prompt(t('scnArchiveReasonPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await archiveScenario(id, reason.trim()); toast.success(t('scnArchived')); await reload(); }
    catch { toast.danger(t('scnArchiveFailed')); }
    finally { setBusyId(null); }
  }

  // ── Create modal ───────────────────────────────────────────────────
  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'titleEn', label: t('scnTitleEn') + ' *', required: true, span: 6 },
    { kind: 'text', key: 'titleAr', label: t('scnTitleAr'),  span: 6 },
    { kind: 'select', key: 'scenarioType', label: t('scnType') + ' *', required: true,
      options: [
        { value: 'MONTE_CARLO', label: t('scnTypeMonteCarlo') },
        { value: 'STRESS_TEST', label: t('scnTypeStress') },
        { value: 'WHAT_IF',     label: t('scnTypeWhatIf') },
      ], span: 6 },
    { kind: 'number', key: 'horizonMonths', label: t('scnHorizonMonths'), min: 1, max: 60, span: 6 },
    { kind: 'text', key: 'riskCode',    label: t('scnRiskCode'),    placeholder: 'RSK-2026-042', span: 6 },
    { kind: 'text', key: 'riskTitleEn', label: t('scnRiskTitleEn'), span: 6 },
    { kind: 'textarea', key: 'descriptionEn', label: t('scnDescriptionEn'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'descriptionAr', label: t('scnDescriptionAr'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'inputJson', label: t('scnInputJson') + ' *', rows: 3, span: 12 },
    { kind: 'textarea', key: 'assumptionsEn', label: t('scnAssumptionsEn'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'assumptionsAr', label: t('scnAssumptionsAr'), rows: 2, span: 12 },
  ];

  async function onCreate(values: CreateValues) {
    try {
      await createScenario({
        titleEn:       (values.titleEn ?? '').trim(),
        titleAr:       (values.titleAr ?? '').trim() || undefined,
        scenarioType:  (values.scenarioType ?? 'MONTE_CARLO') as ScenarioType,
        riskCode:      (values.riskCode ?? '').trim() || undefined,
        riskTitleEn:   (values.riskTitleEn ?? '').trim() || undefined,
        horizonMonths: values.horizonMonths != null ? Number(values.horizonMonths) : undefined,
        descriptionEn: (values.descriptionEn ?? '').trim() || undefined,
        descriptionAr: (values.descriptionAr ?? '').trim() || undefined,
        inputJson:     (values.inputJson ?? '').trim(),
        assumptionsEn: (values.assumptionsEn ?? '').trim() || undefined,
        assumptionsAr: (values.assumptionsAr ?? '').trim() || undefined,
      });
      toast.success(t('scnCreated'));
      setCreateOpen(false);
      await reload();
    } catch { toast.danger(t('scnCreateFailed')); }
  }

  function toggleExpanded(id: number) {
    const n = new Set(expanded);
    if (n.has(id)) n.delete(id); else n.add(id);
    setExpanded(n);
  }

  // ── Render ─────────────────────────────────────────────────────────
  return (
    <Shell title={t('scnPageTitle')} sub={t('scnPageSubtitle')}>
      <PageHeader
        eyebrow={t('navRisk')}
        title={t('scnPageTitle')}
        sub={t('scnPageSubtitle')}
        actions={
          <Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
            {t('scnNew')}
          </Button>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={t('scnKpiDraft')}      value={stats.DRAFT}     tone="warning" onClick={() => setTab('draft')} active={tab === 'draft'} />
        <Kpi Icon={CheckCircle2} label={t('scnKpiApproved')}   value={stats.APPROVED}  tone="success" onClick={() => setTab('approved')} active={tab === 'approved'} />
        <Kpi Icon={TrendingUp}   label={t('scnKpiAvgExpected')} value={fmtSar(stats.avgExpected || undefined)} tone="primary" to="/risk-scenarios" />
        <Kpi Icon={AlertTriangle} label={t('scnKpiSumP95')}    value={fmtSar(stats.sumP95 || undefined)} tone="danger" to="/risk-scenarios" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'stats' ? (
        <Card title={t('scnStatsTitle')} Icon={BarChart3}>
          <div className="kpi-grid">
            <Kpi Icon={BarChart3} label={t('scnTypeMonteCarlo')} value={stats.mc}     tone="primary" to="/risk-scenarios" />
            <Kpi Icon={BarChart3} label={t('scnTypeStress')}     value={stats.stress} tone="warning" to="/risk-scenarios" />
            <Kpi Icon={BarChart3} label={t('scnTypeWhatIf')}     value={stats.whatIf} tone="info" to="/risk-scenarios" />
          </div>
          <div style={{ marginTop: 12, fontSize: 13, color: 'var(--ink-3)' }}>
            {t('scnStatsExpectedHint')}{' '}
            <span className="mono" style={{ fontWeight: 700 }}>{fmtSar(stats.avgExpected || undefined)}</span>
            {' · '}
            {t('scnStatsP95Hint')}{' '}
            <span className="mono" style={{ fontWeight: 700 }}>{fmtSar(stats.sumP95 || undefined)}</span>
          </div>
        </Card>
      ) : (
        <Card title={t('scnPageTitle')} Icon={Sigma}>
          {loading
            ? <EmptyState Icon={Sigma} title={t('loading')} />
            : filtered.length === 0
              ? <EmptyState Icon={Sigma} title={t('scnEmpty')} sub={t('scnEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('scnColCode')}</th>
                      <th>{t('scnColTitle')}</th>
                      <th>{t('scnColRisk')}</th>
                      <th>{t('scnColType')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColExpected')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColP95')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filtered.map(r => {
                      const open = expanded.has(r.id);
                      const result = parseMonteCarloResult(r.resultJson);
                      return (
                        <Fragment key={r.id}>
                          <tr>
                            <td>
                              <button
                                onClick={() => toggleExpanded(r.id)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}
                                aria-label="expand"
                              >
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{r.scenarioCode}</td>
                            <td>{lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}</td>
                            <td>
                              {r.riskCode
                                ? <span className="mono">{r.riskCode}</span>
                                : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                            </td>
                            <td>{t(SCENARIO_TYPE_LABEL_KEY[r.scenarioType])}</td>
                            <td>
                              <Badge tone={SCENARIO_STATUS_TONE[r.status]}>{t(`scnStatus_${r.status}`)}</Badge>
                            </td>
                            <td style={{ textAlign: 'right' }} className="mono">{fmtSar(r.expectedLoss)}</td>
                            <td style={{ textAlign: 'right' }} className="mono">{fmtSar(r.p95Loss)}</td>
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
                              {(r.status === 'DRAFT' || r.status === 'APPROVED') && (
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
                              <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                                <div style={{ padding: 12 }}>
                                  {r.descriptionEn && (
                                    <p style={{ marginBottom: 8 }}>
                                      {lang === 'ar' && r.descriptionAr ? r.descriptionAr : r.descriptionEn}
                                    </p>
                                  )}
                                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, fontSize: 13 }}>
                                    <div>
                                      <strong>{t('scnInputJson')}</strong>
                                      <pre className="mono" style={{ whiteSpace: 'pre-wrap', fontSize: 12,
                                        background: 'var(--surface)', padding: 8, borderRadius: 4, marginTop: 4 }}>
                                        {r.inputJson}
                                      </pre>
                                    </div>
                                    {result && (
                                      <div>
                                        <strong>{t('scnResultSummary')}</strong>
                                        <ul style={{ paddingInlineStart: 18, fontSize: 13, marginTop: 4 }}>
                                          <li>{t('scnExpected')}: <span className="mono">{fmtSar(result.expectedLoss)}</span></li>
                                          <li>p95: <span className="mono">{fmtSar(result.p95Loss)}</span></li>
                                          <li>p99: <span className="mono">{fmtSar(result.p99Loss)}</span></li>
                                          <li>σ:   <span className="mono">{fmtSar(result.stdev)}</span></li>
                                        </ul>
                                      </div>
                                    )}
                                  </div>
                                  {r.assumptionsEn && (
                                    <div style={{ marginTop: 8, fontSize: 13 }}>
                                      <strong>{t('scnAssumptions')}:</strong>{' '}
                                      {lang === 'ar' && r.assumptionsAr ? r.assumptionsAr : r.assumptionsEn}
                                    </div>
                                  )}
                                  {r.status === 'REJECTED' && r.rejectionReason && (
                                    <div style={{ marginTop: 8, fontSize: 13, color: 'var(--danger)' }}>
                                      <strong>{t('scnRejectionReason')}:</strong> {r.rejectionReason}
                                    </div>
                                  )}
                                  {r.status === 'ARCHIVED' && r.archiveReason && (
                                    <div style={{ marginTop: 8, fontSize: 13, color: 'var(--ink-3)' }}>
                                      <strong>{t('scnArchiveReason')}:</strong> {r.archiveReason}
                                    </div>
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
      )}

      <FormModal<CreateValues>
        open={createOpen}
        title={t('scnNew')}
        fields={FIELDS}
        initial={CREATE_INITIAL}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </Shell>
  );
}
