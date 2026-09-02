import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Leaf, TrendingDown, Target, Calculator, FileText, Truck,
  Trash2,
  CloudLightning, Wrench,
  Paperclip,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  EMISSION_FACTORS,
  loadCalcEntries, appendCalcEntry, deleteCalcEntry,
  subscribeEsg,
  type CalculatorEntry, type EsgStats,
} from '../../lib/esg';
// S-O — Vendor ESG surveillance reads the real backend (was VENDOR_SURVEYS mock).
import {
  vendorEsgApi, SURVEY_TONE, SURVEY_LABEL,
  type VendorEsgSurvey,
} from '../../lib/vendorEsg';
// S-P — Overview KPIs + trend chart read the live dashboard/trajectory API
// (was the aggregateEsg / EMISSIONS_TIMESERIES mock).
import {
  esgDashboardApi, mapDashboardToStats, EMPTY_ESG_STATS,
  isEsgTab,
  type EsgTab,
} from '../../lib/esgDashboard';
// Green-finance plan §5.4–§5.7 — regulatory-horizon tabs (targets / IFRS / env+CBAM).
import { EsgTargetsTab, IfrsTab, EnvComplianceTab, AssuranceTab } from './EsgHorizonTabs';
import { OverviewTab } from './EsgOverviewTab';
import { CmaTab } from './EsgCmaTab';
import { TcfdTab } from './EsgTcfdTab';
import { MaterialityTab } from './EsgMaterialityTab';
import { MetricsTab } from './EsgMetricsTab';
import { ClimateTab } from './EsgClimateTab';
import { CapaTab } from './EsgCapaTab';
import { EvidenceTab } from './EsgEvidenceTab';
import { AnnexTab } from './EsgAnnexTab';
import { GhgInventoryTab } from './EsgGhgInventoryTab';
import { SgiTab } from './EsgSgiTab';
import { SettingsTab } from './EsgSettingsTab';
import '../../styles/pages/esg.css';
// EsgTab + isEsgTab are imported from lib/esgDashboard (single source of truth for the tab ids).

/**
 * EsgPage — Sprint 6 (C2) + production hardening.
 *
 * Six-tab dashboard:
 *   • Overview   — multi-year scope trend, KPI strip (live dashboard aggregate)
 *   • Calculator — GHG-Protocol emission-factor calculator (Saudi grid +
 *                  IPCC fuels + DEFRA travel)
 *   • CMA        — Articles 91-94 disclosure checklist
 *   • TCFD       — 4-pillar disclosure tracker
 *   • SGI        — Saudi Green Initiative commitments by sector
 *   • Survey     — Vendor ESG survey for Scope 3 supplier engagement
 */
export function EsgPage() {
  const { t } = useI18n();
  const toast = useToast();
  // S-P — deep-linkable tabs: initial tab from ?tab=, written back on change.
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  const [tab, setTabState] = useState<EsgTab>(isEsgTab(initialTab) ? initialTab : 'overview');
  const setTab = (next: EsgTab) => {
    setTabState(next);
    const sp = new URLSearchParams(searchParams);
    sp.set('tab', next);
    setSearchParams(sp, { replace: true });
  };

  // Reactive state
  // S-P — Overview KPIs from the live dashboard aggregate (kills the aggregateEsg mock).
  const REPORT_YEAR = 2025;
  const [stats, setStats] = useState<EsgStats>(EMPTY_ESG_STATS);
  useEffect(() => {
    esgDashboardApi.get(REPORT_YEAR)
      .then(d => setStats(mapDashboardToStats(d)))
      .catch(() => { /* keep the zeroed placeholder on failure — no mock fallback */ });
  }, []);
  const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(() => loadCalcEntries());
  useEffect(() => subscribeEsg(() => {
    setCalcEntries(loadCalcEntries());
  }), []);


  // TCFD + CMA grouping were previously static useMemo blocks against
  // TCFD_ITEMS / CMA_ARTICLES; V60 + V61 promoted both tabs to API-
  // driven so they self-load and self-group.

  return (
    <Shell title={t('esgTitle')} sub={t('esgSub')}>
      <PageHeader
        eyebrow={t('esgEyebrow')}
        title={t('esgTitle')}
        sub={t('esgPageSub')}
        actions={<>
          <RaiseRequestButton sourceModule="ESG" />
          {/* F2-435 — these four buttons emitted a CMA Art. 91-94 / TCFD annual-report
              annex built entirely from lib/esg.ts constants. The real annex is generated
              server-side from the live registers on the Annex tab, with a
              DRAFT → SUBMITTED → APPROVED → PUBLISHED lifecycle and its own export. A control
              must land somewhere true (F2-137), so this opens that tab rather than vanishing. */}
          <Button variant="primary" Icon={FileText} onClick={() => setTab('annex')}>{t('esgOpenAnnexTab')}</Button>
        </>}
      />

      <div style={{ marginBottom: 16 }}><ModuleEngineRequestsPanel sourceModule="ESG" /></div>

      <div className="kpi-grid esg-kpi-strip">
        <Kpi Icon={Leaf}          label={t('esgKpiTotal')}     value={`${(stats.totalEmissions / 1000).toFixed(1)}k`} tone="primary" onClick={() => setTab('ghg')} active={tab === 'ghg'} />
        <Kpi Icon={TrendingDown}   label={t('esgKpiYoY')}        value={`${stats.yoyTotal.toFixed(1)}%`} tone={stats.yoyTotal < 0 ? 'success' : 'danger'} onClick={() => setTab('ghg')} active={tab === 'ghg'} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope1')}     value={`${(stats.scope1 / 1000).toFixed(1)}k`} tone="success" onClick={() => setTab('ghg')} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope2')}     value={`${(stats.scope2 / 1000).toFixed(1)}k`} tone="success" onClick={() => setTab('ghg')} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope3')}     value={`${(stats.scope3 / 1000).toFixed(1)}k`} tone="warning" onClick={() => setTab('ghg')} />
        <Kpi Icon={FileText}        label={t('esgKpiCma')}        value={`${stats.cmaCoveragePct}%`} tone={stats.cmaCoveragePct >= 80 ? 'success' : 'warning'} onClick={() => setTab('cma')} active={tab === 'cma'} />
        <Kpi Icon={FileText}        label={t('esgKpiTcfd')}       value={`${stats.tcfdCoveragePct}%`} tone={stats.tcfdCoveragePct >= 80 ? 'success' : 'warning'} onClick={() => setTab('tcfd')} active={tab === 'tcfd'} />
        <Kpi Icon={Target}          label={t('esgKpiSgi')}        value={`${stats.sgiOnTrackCount} / ${stats.sgiTotal}`} tone={stats.sgiOnTrackCount === stats.sgiTotal ? 'success' : 'warning'} onClick={() => setTab('sgi')} active={tab === 'sgi'} />
      </div>

      {/* Tab strip */}
      <div className="esg-tab-strip">
        {([
          { id: 'overview',   labelKey: 'esgTabOverview',  Icon: Leaf },
          { id: 'annex',      labelKey: 'esgTabAnnex',     Icon: FileText },
          { id: 'capa',       labelKey: 'esgTabCapa',      Icon: Wrench },
          { id: 'calculator', labelKey: 'esgTabCalc',      Icon: Calculator },
          { id: 'cma',        labelKey: 'esgTabCma',       Icon: FileText },
          { id: 'tcfd',       labelKey: 'esgTabTcfd',      Icon: FileText },
          { id: 'materiality',labelKey: 'esgTabMateriality', Icon: Target },
          { id: 'metrics',    labelKey: 'esgTabMetrics',   Icon: TrendingDown },
          { id: 'evidence',   labelKey: 'esgTabEvidence',  Icon: Paperclip },
          { id: 'sgi',        labelKey: 'esgTabSgi',       Icon: Target },
          { id: 'ghg',        labelKey: 'esgTabGhg',       Icon: Leaf },
          { id: 'climate',    labelKey: 'esgTabClimate',   Icon: CloudLightning },
          { id: 'survey',     labelKey: 'esgTabSurvey',    Icon: Truck },
          { id: 'targets',    labelKey: 'esgTabTargets',   Icon: Target },
          { id: 'ifrs',       labelKey: 'esgTabIfrs',      Icon: FileText },
          { id: 'env',        labelKey: 'esgTabEnv',       Icon: Wrench },
          { id: 'assurance',  labelKey: 'esgTabAssurance', Icon: Paperclip },
          { id: 'settings',   labelKey: 'esgTabSettings',  Icon: Wrench },
        ] as { id: EsgTab; labelKey: string; Icon: typeof Leaf }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            className={`esg-tab-btn${tab === tabDef.id ? ' is-active' : ''}`}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'overview'   && <OverviewTab onNavigate={setTab} />}
      {tab === 'annex'      && <AnnexTab />}
      {tab === 'capa'       && <CapaTab onNavigate={setTab} />}
      {tab === 'calculator' && <CalculatorTab entries={calcEntries}
        onAdd={(e) => { appendCalcEntry(e); toast.success(t('esgCalcAddedToast')); }}
        onDelete={(id) => { deleteCalcEntry(id); toast.info(t('esgCalcDeletedToast')); }} />}
      {tab === 'cma'        && <CmaTab />}
      {tab === 'tcfd'       && <TcfdTab />}
      {tab === 'materiality' && <MaterialityTab />}
      {tab === 'metrics'    && <MetricsTab />}
      {tab === 'evidence'   && <EvidenceTab />}
      {tab === 'sgi'        && <SgiTab />}
      {tab === 'ghg'        && <GhgInventoryTab />}
      {tab === 'climate'    && <ClimateTab />}
      {tab === 'settings'   && <SettingsTab />}
      {tab === 'targets'    && <EsgTargetsTab />}
      {tab === 'ifrs'       && <IfrsTab />}
      {tab === 'env'        && <EnvComplianceTab />}
      {tab === 'assurance'  && <AssuranceTab />}
      {tab === 'survey'     && <SurveyTab />}
    </Shell>
  );
}




// ─── Calculator tab ─────────────────────────────────────────────────────────

function CalculatorTab({ entries, onAdd, onDelete }: {
  entries: CalculatorEntry[];
  onAdd: (e: { factorCode: string; amount: number; occurredAt: string; noteEn?: string; noteAr?: string }) => void;
  onDelete: (id: string) => void;
}) {
  const { t, lang } = useI18n();
  const [factorCode, setFactorCode] = useState(EMISSION_FACTORS[0].code);
  const [amount, setAmount] = useState<string>('');
  const [occurredAt, setOccurredAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [note, setNote] = useState('');

  const factor = EMISSION_FACTORS.find(f => f.code === factorCode)!;
  const computed = (parseFloat(amount) || 0) * factor.kgCo2ePerUnit;

  const totalKg = entries.reduce((acc, e) => acc + e.computedKgCo2e, 0);

  function submit() {
    const amt = parseFloat(amount);
    if (!amt || amt <= 0) return;
    onAdd({
      factorCode, amount: amt, occurredAt,
      noteEn: lang === 'en' ? note : undefined,
      noteAr: lang === 'ar' ? note : undefined,
    });
    setAmount(''); setNote('');
  }

  return (
    <>
      <Card title={t('esgCalcTitle')} Icon={Calculator}>
        <div className="esg-text-eyebrow-sm">
          {t('esgCalcSub')}
        </div>
        <div className="esg-calc-form">
          <Field label={t('esgCalcFactor')}>
            <select value={factorCode} onChange={e => setFactorCode(e.target.value)} style={inputStyle}>
              {EMISSION_FACTORS.map(f => (
                <option key={f.code} value={f.code}>
                  Scope {f.scope} · {lang === 'ar' ? f.nameAr : f.nameEn}
                </option>
              ))}
            </select>
          </Field>
          <Field label={`${t('esgCalcAmount')} (${factor.unit})`}>
            <input type="number" value={amount} onChange={e => setAmount(e.target.value)}
              placeholder="0" className="mono" style={inputStyle} />
          </Field>
          <Field label={t('esgCalcDate')}>
            <input type="date" value={occurredAt} onChange={e => setOccurredAt(e.target.value)} style={inputStyle} />
          </Field>
          <Field label={t('esgCalcNote')}>
            <input value={note} onChange={e => setNote(e.target.value)} placeholder={t('esgCalcNotePlaceholder')} style={inputStyle} />
          </Field>
          <Button variant="primary" onClick={submit}>{t('esgCalcAdd')}</Button>
        </div>
        <div className="esg-calc-preview">
          <div className="esg-calc-preview__meta">
            <b>{factor.kgCo2ePerUnit}</b> kgCO2e / {factor.unit} · {t('esgCalcSource')}: {factor.source}
          </div>
          <div className="esg-text-strong">
            {t('esgCalcPreview')}: <span className="mono">{computed.toFixed(2)}</span> kgCO2e
            ({(computed / 1000).toFixed(3)} tCO2e)
          </div>
        </div>
      </Card>

      <div className="mt-lg">
        <Card title={t('esgCalcLogTitle')} Icon={FileText}
          action={
            <Badge tone="primary">{(totalKg / 1000).toFixed(2)} tCO2e</Badge>
          }>
          {entries.length === 0
            ? <div className="esg-calc-empty">
                {t('esgCalcEmpty')}
              </div>
            : (
              <table className="tbl esg-calc-tbl">
                <thead>
                  <tr>
                    <th>{t('esgCalcLogDate')}</th>
                    <th>{t('esgCalcLogFactor')}</th>
                    <th>{t('esgCalcLogAmount')}</th>
                    <th>{t('esgCalcLogResult')}</th>
                    <th>{t('esgCalcLogNote')}</th>
                    <th style={{ width: 50 }} />
                  </tr>
                </thead>
                <tbody>
                  {entries.map(entry => {
                    const f = EMISSION_FACTORS.find(x => x.code === entry.factorCode);
                    return (
                      <tr key={entry.id}>
                        <td className="mono esg-calc-row-mono">{entry.occurredAt}</td>
                        <td>
                          <Badge tone="info">Scope {f?.scope}</Badge>
                          <span className="ms-sm">{f ? (lang === 'ar' ? f.nameAr : f.nameEn) : entry.factorCode}</span>
                        </td>
                        <td className="mono">{entry.amount} {f?.unit}</td>
                        <td className="mono">
                          <b>{entry.computedKgCo2e.toFixed(2)}</b> kgCO2e
                        </td>
                        <td className="esg-calc-row-note">
                          {entry.noteEn || entry.noteAr || '—'}
                        </td>
                        <td>
                          <Button variant="ghost" Icon={Trash2} onClick={() => onDelete(entry.id)}>{''}</Button>
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
        </Card>
      </div>
    </>
  );
}






// ─── Survey tab ─────────────────────────────────────────────────────────────

function SurveyTab() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [surveys, setSurveys] = useState<VendorEsgSurvey[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const [busyId, setBusyId] = useState<number | null>(null);

  const [editId, setEditId] = useState<number | null>(null);
  const [draftScope12, setDraftScope12] = useState<string>('');
  const [draftRenewable, setDraftRenewable] = useState<string>('');
  const [draftSaudization, setDraftSaudization] = useState<string>('');
  const [draftIso, setDraftIso] = useState(false);
  const [draftNetZero, setDraftNetZero] = useState(false);

  // S-O — the Survey tab now reads the REAL backend (kills the VENDOR_SURVEYS
  // mock). reloadKey bumps re-run the fetch after any write.
  useEffect(() => {
    let alive = true;
    setLoading(true);
    vendorEsgApi.list()
      .then(page => { if (alive) { setSurveys(page.content); setError(false); } })
      .catch(() => { if (alive) setError(true); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [reloadKey]);
  const reload = () => setReloadKey(k => k + 1);

  const numOrUndef = (s: string): number | undefined => {
    const n = parseFloat(s);
    return Number.isFinite(n) ? n : undefined;
  };

  function startEdit(s: VendorEsgSurvey) {
    setEditId(s.id);
    setDraftScope12(s.reportedScope12?.toString() ?? '');
    setDraftRenewable(s.renewablePct?.toString() ?? '');
    setDraftSaudization(s.saudizationPct?.toString() ?? '');
    setDraftIso(!!s.iso14001);
    setDraftNetZero(!!s.hasNetZeroTarget);
  }

  async function saveResponse(id: number) {
    setBusyId(id);
    try {
      await vendorEsgApi.recordResponse(id, {
        reportedScope12: numOrUndef(draftScope12),
        renewablePct: numOrUndef(draftRenewable),
        saudizationPct: numOrUndef(draftSaudization),
        iso14001: draftIso,
        hasNetZeroTarget: draftNetZero,
      });
      toast.success(t('esgSurveySavedToast'));
      setEditId(null);
      reload();
    } catch { toast.danger(t('esgSurveyErrorToast')); }
    finally { setBusyId(null); }
  }

  async function act(id: number, fn: () => Promise<unknown>, okKey: string) {
    setBusyId(id);
    try { await fn(); toast.success(t(okKey)); reload(); }
    catch { toast.danger(t('esgSurveyErrorToast')); }
    finally { setBusyId(null); }
  }

  const isResponded = (s: VendorEsgSurvey) =>
    s.status === 'RESPONDED' || s.status === 'ACCEPTED' || s.status === 'REJECTED';
  const totalReportedScope12 = surveys.reduce((acc, s) => acc + (s.reportedScope12 ?? 0), 0);
  const respondedSpend = surveys.filter(isResponded).reduce((acc, s) => acc + (s.spendSarK ?? 0), 0);
  const totalSpend = surveys.reduce((acc, s) => acc + (s.spendSarK ?? 0), 0);
  const spendCoveragePct = totalSpend > 0 ? Math.round((respondedSpend / totalSpend) * 100) : 0;
  const respondedCount = surveys.filter(isResponded).length;

  return (
    <Card title={t('esgSurveyTitle')} Icon={Truck}>
      <div className="esg-text-eyebrow-sm">
        {t('esgSurveySub')}
      </div>
      {loading && <div className="esg-text-eyebrow-sm">{t('esgSurveyLoading')}</div>}
      {error && <div className="esg-text-eyebrow-sm">{t('esgSurveyLoadError')}</div>}
      {!loading && !error && surveys.length === 0 && (
        <div className="esg-text-eyebrow-sm">{t('esgSurveyEmpty')}</div>
      )}
      {!loading && !error && surveys.length > 0 && (<>
      <div className="esg-survey-stats">
        <Stat label={t('esgSurveyKpiSpendCov')} value={`${spendCoveragePct}%`} />
        <Stat label={t('esgSurveyKpiReportedScope12')} value={`${(totalReportedScope12 / 1000).toFixed(1)}k tCO2e`} />
        <Stat label={t('esgSurveyKpiVendors')} value={`${respondedCount} / ${surveys.length}`} />
      </div>
      <table className="tbl esg-calc-tbl">
        <thead>
          <tr>
            <th>{t('esgSurveyVendor')}</th>
            <th>{t('esgSurveySpend')}</th>
            <th>{t('esgSurveyStatus')}</th>
            <th>{t('esgSurveyScore')}</th>
            <th>{t('esgSurveyScope12')}</th>
            <th>{t('esgSurveyRenewable')}</th>
            <th>{t('esgSurveySaudization')}</th>
            <th>{t('esgSurveyCerts')}</th>
            <th></th>
          </tr>
        </thead>
        <tbody>
          {surveys.map(s => (
            <tr key={s.id}>
              <td>
                <div style={{ fontWeight: 600 }}>{lang === 'ar' ? (s.vendorNameAr || s.vendorNameEn) : s.vendorNameEn}</div>
                <div className="mono esg-survey-row-id">{s.externalId ?? `#${s.id}`}</div>
              </td>
              <td className="mono">{s.spendSarK != null ? `${s.spendSarK.toLocaleString()}k SAR` : '—'}</td>
              <td>
                <Badge tone={SURVEY_TONE[s.status]}>{SURVEY_LABEL[s.status][lang]}</Badge>
              </td>
              {editId === s.id ? (
                <>
                  <td className="mono">—</td>
                  <td>
                    <input value={draftScope12} onChange={e => setDraftScope12(e.target.value)}
                      type="number" placeholder="tCO2e" className="mono esg-survey-input-90" style={inputStyle} />
                  </td>
                  <td>
                    <input value={draftRenewable} onChange={e => setDraftRenewable(e.target.value)}
                      type="number" placeholder="%" className="mono esg-survey-input-60" style={inputStyle} />
                  </td>
                  <td>
                    <input value={draftSaudization} onChange={e => setDraftSaudization(e.target.value)}
                      type="number" placeholder="%" className="mono esg-survey-input-60" style={inputStyle} />
                  </td>
                  <td>
                    <label className="esg-survey-checkbox">
                      <input type="checkbox" checked={draftIso} onChange={e => setDraftIso(e.target.checked)} />
                      ISO 14001
                    </label>
                    <label className="esg-survey-checkbox">
                      <input type="checkbox" checked={draftNetZero} onChange={e => setDraftNetZero(e.target.checked)} />
                      {t('esgSurveyNetZero')}
                    </label>
                  </td>
                  <td>
                    <Button size="sm" variant="primary" disabled={busyId === s.id} onClick={() => saveResponse(s.id)}>{t('esgSurveySave')}</Button>
                  </td>
                </>
              ) : (
                <>
                  <td className="mono">{s.esgScore != null ? Number(s.esgScore).toFixed(0) : '—'}</td>
                  <td className="mono">{s.reportedScope12 != null ? s.reportedScope12.toLocaleString() : '—'}</td>
                  <td className="mono">{s.renewablePct != null ? `${s.renewablePct}%` : '—'}</td>
                  <td className="mono">{s.saudizationPct != null ? `${s.saudizationPct}%` : '—'}</td>
                  <td>
                    <div className="row-tight">
                      {s.iso14001 && <Badge tone="success">ISO</Badge>}
                      {s.hasNetZeroTarget && <Badge tone="info">NZ</Badge>}
                    </div>
                  </td>
                  <td>
                    <div className="row-tight">
                      {s.status === 'DRAFT' && (
                        <Button size="sm" variant="primary" disabled={busyId === s.id}
                          onClick={() => act(s.id, () => vendorEsgApi.send(s.id), 'esgSurveySentToast')}>{t('esgSurveySend')}</Button>
                      )}
                      {(s.status === 'SENT' || s.status === 'ESCALATED') && (
                        <Button size="sm" variant="secondary" onClick={() => startEdit(s)}>{t('esgSurveyRespond')}</Button>
                      )}
                      {s.status === 'RESPONDED' && (<>
                        <Button size="sm" variant="primary" disabled={busyId === s.id}
                          onClick={() => act(s.id, () => vendorEsgApi.accept(s.id), 'esgSurveyAcceptedToast')}>{t('esgSurveyAccept')}</Button>
                        <Button size="sm" variant="secondary" disabled={busyId === s.id}
                          onClick={() => { const r = window.prompt(t('esgSurveyRejectPrompt')); if (r !== null) act(s.id, () => vendorEsgApi.reject(s.id, r), 'esgSurveyRejectedToast'); }}>{t('esgSurveyReject')}</Button>
                      </>)}
                    </div>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
      </>)}
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="stack-xs">
      <span className="esg-stat-row__label">{label}</span>
      {children}
    </label>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div className="esg-stat-tile">
      <div className="esg-stat-tile__label">{label}</div>
      <div className="esg-stat-tile__value">{value}</div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};

