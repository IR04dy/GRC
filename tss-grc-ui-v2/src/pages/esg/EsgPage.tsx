import { useEffect, useMemo, useState } from 'react';
import {
  Leaf, TrendingDown, Target, Calculator, FileText, Truck,
  Download, Trash2, CheckCircle2, AlertCircle, Building2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  EMISSIONS_TIMESERIES, CURRENT_YEAR_RECORD, EMISSION_FACTORS,
  TCFD_ITEMS, CMA_ARTICLES, SGI_COMMITMENTS,
  PILLAR_LABEL, STATUS_TONE, STATUS_LABEL, SECTOR_LABEL,
  SURVEY_TONE, SURVEY_LABEL,
  aggregateEsg, yoyChange,
  loadCalcEntries, appendCalcEntry, deleteCalcEntry,
  effectiveSurveys, saveSurveyOverride,
  subscribeEsg, buildEsgAnnex,
  type CalculatorEntry, type VendorEsgSurvey,
} from '../../lib/esg';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';

type EsgTab = 'overview' | 'calculator' | 'cma' | 'tcfd' | 'sgi' | 'survey';

/**
 * EsgPage — Sprint 6 (C2) + production hardening.
 *
 * Six-tab dashboard:
 *   • Overview   — multi-year scope trend, KPI strip, ESG annex download
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
  const [tab, setTab] = useState<EsgTab>('overview');

  // Reactive state
  const [stats, setStats] = useState(() => aggregateEsg());
  const [calcEntries, setCalcEntries] = useState<CalculatorEntry[]>(() => loadCalcEntries());
  const [surveys, setSurveys] = useState<VendorEsgSurvey[]>(() => effectiveSurveys());
  useEffect(() => subscribeEsg(() => {
    setStats(aggregateEsg());
    setCalcEntries(loadCalcEntries());
    setSurveys(effectiveSurveys());
  }), []);

  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);

  function downloadAnnex() {
    const text = buildEsgAnnex(brand.organisationNameEn, brand.organisationNameAr);
    const blob = new Blob([text], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `ESG-Annex-${CURRENT_YEAR_RECORD.year}.txt`;
    document.body.appendChild(a); a.click(); document.body.removeChild(a);
    URL.revokeObjectURL(url);
    toast.success(t('esgAnnexDownloadedToast'));
  }

  // TCFD grouping
  const tcfdByPillar = useMemo(() => {
    const map = new Map<string, typeof TCFD_ITEMS>();
    for (const item of TCFD_ITEMS) {
      if (!map.has(item.pillar)) map.set(item.pillar, []);
      map.get(item.pillar)!.push(item);
    }
    return map;
  }, []);

  // CMA grouping by article
  const cmaByArticle = useMemo(() => {
    const map = new Map<string, typeof CMA_ARTICLES>();
    for (const item of CMA_ARTICLES) {
      if (!map.has(item.article)) map.set(item.article, []);
      map.get(item.article)!.push(item);
    }
    return map;
  }, []);

  return (
    <Shell title={t('esgTitle')} sub={t('esgSub')}>
      <PageHeader
        eyebrow={t('esgEyebrow')}
        title={t('esgTitle')}
        sub={t('esgPageSub')}
        actions={<Button variant="primary" Icon={Download} onClick={downloadAnnex}>{t('esgDownloadAnnex')}</Button>}
      />

      <div className="kpi-grid" style={{ marginBottom: 16 }}>
        <Kpi Icon={Leaf}          label={t('esgKpiTotal')}     value={`${(stats.totalEmissions / 1000).toFixed(1)}k`} tone="primary" />
        <Kpi Icon={TrendingDown}   label={t('esgKpiYoY')}        value={`${stats.yoyTotal.toFixed(1)}%`} tone={stats.yoyTotal < 0 ? 'success' : 'danger'} />
        <Kpi Icon={Leaf}            label={t('esgKpiScope1')}     value={`${(stats.scope1 / 1000).toFixed(1)}k`} tone="success" />
        <Kpi Icon={Leaf}            label={t('esgKpiScope2')}     value={`${(stats.scope2 / 1000).toFixed(1)}k`} tone="success" />
        <Kpi Icon={Leaf}            label={t('esgKpiScope3')}     value={`${(stats.scope3 / 1000).toFixed(1)}k`} tone="warning" />
        <Kpi Icon={FileText}        label={t('esgKpiCma')}        value={`${stats.cmaCoveragePct}%`} tone={stats.cmaCoveragePct >= 80 ? 'success' : 'warning'} />
        <Kpi Icon={FileText}        label={t('esgKpiTcfd')}       value={`${stats.tcfdCoveragePct}%`} tone={stats.tcfdCoveragePct >= 80 ? 'success' : 'warning'} />
        <Kpi Icon={Target}          label={t('esgKpiSgi')}        value={`${stats.sgiOnTrackCount} / ${stats.sgiTotal}`} tone={stats.sgiOnTrackCount === stats.sgiTotal ? 'success' : 'warning'} />
      </div>

      {/* Tab strip */}
      <div style={{ display: 'flex', gap: 4, marginBottom: 12, flexWrap: 'wrap' }}>
        {([
          { id: 'overview',   labelKey: 'esgTabOverview',  Icon: Leaf },
          { id: 'calculator', labelKey: 'esgTabCalc',      Icon: Calculator },
          { id: 'cma',        labelKey: 'esgTabCma',       Icon: FileText },
          { id: 'tcfd',       labelKey: 'esgTabTcfd',      Icon: FileText },
          { id: 'sgi',        labelKey: 'esgTabSgi',       Icon: Target },
          { id: 'survey',     labelKey: 'esgTabSurvey',    Icon: Truck },
        ] as { id: EsgTab; labelKey: string; Icon: typeof Leaf }[]).map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            style={{
              padding: '8px 12px', borderRadius: 'var(--r-sm)', fontSize: 12, fontWeight: 600,
              border: `1px solid ${tab === tabDef.id ? 'var(--primary)' : 'var(--border)'}`,
              background: tab === tabDef.id ? 'var(--primary-50)' : 'var(--surface-1)',
              cursor: 'pointer', display: 'inline-flex', alignItems: 'center', gap: 6,
            }}>
            <tabDef.Icon size={13} />
            {t(tabDef.labelKey)}
          </button>
        ))}
      </div>

      {tab === 'overview'   && <OverviewTab />}
      {tab === 'calculator' && <CalculatorTab entries={calcEntries}
        onAdd={(e) => { appendCalcEntry(e); toast.success(t('esgCalcAddedToast')); }}
        onDelete={(id) => { deleteCalcEntry(id); toast.info(t('esgCalcDeletedToast')); }} />}
      {tab === 'cma'        && <CmaTab cmaByArticle={cmaByArticle} />}
      {tab === 'tcfd'       && <TcfdTab tcfdByPillar={tcfdByPillar} />}
      {tab === 'sgi'        && <SgiTab />}
      {tab === 'survey'     && <SurveyTab surveys={surveys}
        onSubmit={(id, patch) => { saveSurveyOverride(id, patch); toast.success(t('esgSurveySavedToast')); }} />}
    </Shell>
  );
}

// ─── Overview tab ──────────────────────────────────────────────────────────

function OverviewTab() {
  const { t } = useI18n();
  const maxTotal = Math.max(...EMISSIONS_TIMESERIES.map(r => r.scope1 + r.scope2 + r.scope3));

  return (
    <>
      <Card title={t('esgTrendTitle')} Icon={TrendingDown}>
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12 }}>
          {t('esgTrendSub')}
        </div>
        {/* Stacked bar chart */}
        <div style={{ display: 'grid', gridTemplateColumns: `repeat(${EMISSIONS_TIMESERIES.length}, 1fr)`, gap: 12, alignItems: 'end', height: 220 }}>
          {EMISSIONS_TIMESERIES.map(r => {
            const total = r.scope1 + r.scope2 + r.scope3;
            const heightPct = (total / maxTotal) * 100;
            const s1Pct = (r.scope1 / total) * 100;
            const s2Pct = (r.scope2 / total) * 100;
            const s3Pct = (r.scope3 / total) * 100;
            return (
              <div key={r.year} style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 6, height: '100%' }}>
                <div style={{ flex: 1, width: '100%', display: 'flex', flexDirection: 'column-reverse', alignItems: 'center' }}>
                  <div style={{ width: '60%', height: `${heightPct}%`, display: 'flex', flexDirection: 'column', borderRadius: '4px 4px 0 0', overflow: 'hidden', boxShadow: '0 1px 0 rgba(0,0,0,0.08)' }}>
                    <div style={{ flex: s3Pct, background: '#f59e0b', minHeight: 2 }} title={`Scope 3: ${r.scope3.toLocaleString()}`} />
                    <div style={{ flex: s2Pct, background: '#0ea5e9', minHeight: 2 }} title={`Scope 2: ${r.scope2.toLocaleString()}`} />
                    <div style={{ flex: s1Pct, background: '#10b981', minHeight: 2 }} title={`Scope 1: ${r.scope1.toLocaleString()}`} />
                  </div>
                </div>
                <div style={{ fontSize: 11, fontWeight: 700 }}>{r.year}</div>
                <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{(total / 1000).toFixed(0)}k</div>
              </div>
            );
          })}
        </div>
        <div style={{ display: 'flex', gap: 12, marginTop: 16, fontSize: 11, color: 'var(--ink-3)' }}>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#10b981', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope1')}</span>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#0ea5e9', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope2')}</span>
          <span><span style={{ display: 'inline-block', width: 12, height: 12, background: '#f59e0b', borderRadius: 2, verticalAlign: 'middle', marginInlineEnd: 4 }} />{t('esgKpiScope3')}</span>
        </div>
      </Card>

      <div style={{ marginTop: 16, display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 16 }}>
        <Card title={t('esgScopeBreakdownTitle')} Icon={Leaf}>
          <table className="tbl" style={{ width: '100%' }}>
            <thead>
              <tr>
                <th>{t('esgScope')}</th>
                <th>tCO2e</th>
                <th>{t('esgYoY')}</th>
                <th>{t('esgVerified')}</th>
              </tr>
            </thead>
            <tbody>
              {[1, 2, 3].map(scope => {
                const v = scope === 1 ? CURRENT_YEAR_RECORD.scope1 : scope === 2 ? CURRENT_YEAR_RECORD.scope2 : CURRENT_YEAR_RECORD.scope3;
                const yoy = yoyChange(scope as 1 | 2 | 3);
                const verified = scope === 1 ? CURRENT_YEAR_RECORD.verifiedScope1 : scope === 2 ? CURRENT_YEAR_RECORD.verifiedScope2 : CURRENT_YEAR_RECORD.verifiedScope3;
                return (
                  <tr key={scope}>
                    <td><Badge tone="primary">Scope {scope}</Badge></td>
                    <td className="mono">{v.toLocaleString()}</td>
                    <td className="mono">
                      <span style={{ color: yoy < 0 ? 'var(--success-fg)' : 'var(--danger-fg)' }}>
                        {yoy > 0 ? '+' : ''}{yoy}%
                      </span>
                    </td>
                    <td>
                      {verified
                        ? <Badge tone="success">{t('esgVerifiedYes')}</Badge>
                        : <Badge tone="warning">{t('esgVerifiedNo')}</Badge>}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 10, lineHeight: 1.5 }}>
            {t('esgScope3Coverage')}: {CURRENT_YEAR_RECORD.scope3CoveragePct}%
          </div>
        </Card>

        <Card title={t('esgRegulatorTitle')} Icon={Building2}>
          <ul style={{ margin: 0, paddingInlineStart: 16, fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
            <li><b>CMA</b> — {t('esgRegCma')}</li>
            <li><b>SGI</b> — {t('esgRegSgi')}</li>
            <li><b>SDAIA</b> — {t('esgRegSdaia')}</li>
            <li><b>Tadawul</b> — {t('esgRegTadawul')}</li>
            <li><b>NCEMA</b> — {t('esgRegNcema')}</li>
          </ul>
        </Card>
      </div>
    </>
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
        <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
          {t('esgCalcSub')}
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr 1fr 2fr auto', gap: 8, alignItems: 'end' }}>
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
        <div style={{ marginTop: 12, padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', fontSize: 12 }}>
          <div style={{ color: 'var(--ink-3)', marginBottom: 4 }}>
            <b>{factor.kgCo2ePerUnit}</b> kgCO2e / {factor.unit} · {t('esgCalcSource')}: {factor.source}
          </div>
          <div style={{ fontWeight: 600 }}>
            {t('esgCalcPreview')}: <span className="mono">{computed.toFixed(2)}</span> kgCO2e
            ({(computed / 1000).toFixed(3)} tCO2e)
          </div>
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Card title={t('esgCalcLogTitle')} Icon={FileText}
          action={
            <Badge tone="primary">{(totalKg / 1000).toFixed(2)} tCO2e</Badge>
          }>
          {entries.length === 0
            ? <div style={{ padding: 20, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                {t('esgCalcEmpty')}
              </div>
            : (
              <table className="tbl" style={{ width: '100%' }}>
                <thead>
                  <tr>
                    <th>{t('esgCalcLogDate')}</th>
                    <th>{t('esgCalcLogFactor')}</th>
                    <th>{t('esgCalcLogAmount')}</th>
                    <th>{t('esgCalcLogResult')}</th>
                    <th>{t('esgCalcLogNote')}</th>
                    <th style={{ width: 50 }}></th>
                  </tr>
                </thead>
                <tbody>
                  {entries.map(entry => {
                    const f = EMISSION_FACTORS.find(x => x.code === entry.factorCode);
                    return (
                      <tr key={entry.id}>
                        <td className="mono" style={{ fontSize: 11 }}>{entry.occurredAt}</td>
                        <td>
                          <Badge tone="info">Scope {f?.scope}</Badge>
                          <span style={{ marginInlineStart: 6 }}>{f ? (lang === 'ar' ? f.nameAr : f.nameEn) : entry.factorCode}</span>
                        </td>
                        <td className="mono">{entry.amount} {f?.unit}</td>
                        <td className="mono">
                          <b>{entry.computedKgCo2e.toFixed(2)}</b> kgCO2e
                        </td>
                        <td style={{ fontSize: 11, color: 'var(--ink-3)' }}>
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

// ─── CMA tab ────────────────────────────────────────────────────────────────

function CmaTab({ cmaByArticle }: { cmaByArticle: Map<string, typeof CMA_ARTICLES> }) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('esgCmaTitle')} Icon={FileText}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('esgCmaSub')}
      </div>
      {Array.from(cmaByArticle.entries()).map(([article, items]) => (
        <div key={article} style={{ marginBottom: 18 }}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8, color: 'var(--primary)' }}>
            {t('esgCmaArticle')} {article}
          </div>
          {items.map(item => {
            const tone = item.status === 'DISCLOSED' ? 'success' :
                         item.status === 'PARTIAL' ? 'warning' :
                         item.status === 'NOT_DISCLOSED' ? 'danger' : 'neutral';
            return (
              <div key={`${item.article}${item.clause}`} style={{
                padding: '12px 0', borderBottom: '1px dashed var(--border)',
                display: 'grid', gridTemplateColumns: '1fr auto', gap: 10,
              }}>
                <div>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', marginBottom: 4 }}>
                    <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                      Art. {item.article}{item.clause ?? ''}
                    </span>
                    <span style={{ fontSize: 13, fontWeight: 600 }}>
                      {lang === 'ar' ? item.titleAr : item.titleEn}
                    </span>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                    {lang === 'ar' ? item.requirementAr : item.requirementEn}
                  </div>
                  {item.evidenceRef && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }}>
                      <FileText size={10} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 3 }} />
                      {item.evidenceRef}
                    </div>
                  )}
                </div>
                <Badge tone={tone}>
                  {STATUS_LABEL[item.status][lang]}
                </Badge>
              </div>
            );
          })}
        </div>
      ))}
    </Card>
  );
}

// ─── TCFD tab ───────────────────────────────────────────────────────────────

function TcfdTab({ tcfdByPillar }: { tcfdByPillar: Map<string, typeof TCFD_ITEMS> }) {
  const { t, lang } = useI18n();
  return (
    <Card title={t('esgTcfdTitle')} Icon={FileText}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('esgTcfdSub')}
      </div>
      {Array.from(tcfdByPillar.entries()).map(([pillar, items]) => (
        <div key={pillar} style={{ marginBottom: 18 }}>
          <div style={{ fontSize: 13, fontWeight: 700, marginBottom: 8, color: 'var(--primary)' }}>
            {PILLAR_LABEL[pillar as keyof typeof PILLAR_LABEL][lang]}
          </div>
          {items.map(item => (
            <div key={`${item.pillar}-${item.recommendationCode}`} style={{
              padding: '10px 0', borderBottom: '1px dashed var(--border)',
              display: 'grid', gridTemplateColumns: '1fr auto', gap: 10, alignItems: 'center',
            }}>
              <div>
                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>({item.recommendationCode})</span>
                  <span style={{ fontSize: 12, fontWeight: 600 }}>
                    {lang === 'ar' ? item.titleAr : item.titleEn}
                  </span>
                </div>
                {item.evidenceRef && (
                  <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 2 }}>
                    {item.evidenceRef}
                  </div>
                )}
              </div>
              <Badge tone={STATUS_TONE[item.status]}>
                {STATUS_LABEL[item.status][lang]}
              </Badge>
            </div>
          ))}
        </div>
      ))}
    </Card>
  );
}

// ─── SGI tab ────────────────────────────────────────────────────────────────

function SgiTab() {
  const { t, lang } = useI18n();
  const bySector = useMemo(() => {
    const map = new Map<string, typeof SGI_COMMITMENTS>();
    for (const c of SGI_COMMITMENTS) {
      if (!map.has(c.sector)) map.set(c.sector, []);
      map.get(c.sector)!.push(c);
    }
    return map;
  }, []);

  return (
    <Card title={t('esgSgiTitle')} Icon={Target}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('esgSgiSub')}
      </div>
      {Array.from(bySector.entries()).map(([sector, items]) => (
        <div key={sector} style={{ marginBottom: 16 }}>
          <div style={{ fontSize: 12, fontWeight: 700, color: 'var(--primary)', marginBottom: 8, textTransform: 'uppercase', letterSpacing: '0.04em' }}>
            <Leaf size={12} style={{ display: 'inline-block', verticalAlign: 'middle', marginInlineEnd: 4 }} />
            {SECTOR_LABEL[sector as keyof typeof SECTOR_LABEL][lang]}
          </div>
          {items.map(c => (
            <div key={c.code} style={{
              padding: 12, marginBottom: 8, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 6, flexWrap: 'wrap' }}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {lang === 'ar' ? c.titleAr : c.titleEn}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                    <span className="mono">{c.code}</span>
                    {' · '}
                    {t('esgSgiTarget')} {c.targetYear}
                  </div>
                </div>
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  {c.onTrack
                    ? <Badge tone="success"><CheckCircle2 size={11} /> {t('esgSgiOnTrack')}</Badge>
                    : <Badge tone="warning"><AlertCircle size={11} /> {t('esgSgiBehind')}</Badge>}
                </div>
              </div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                <div style={{ flex: 1, height: 8, background: 'var(--surface-2)', borderRadius: 4, overflow: 'hidden' }}>
                  <div style={{
                    height: '100%', width: `${Math.min(100, c.progressPct)}%`,
                    background: c.onTrack ? 'var(--success)' : 'var(--warning)',
                    borderRadius: 4,
                  }} />
                </div>
                <div className="mono" style={{ fontSize: 12, fontWeight: 700, minWidth: 60, textAlign: 'end' }}>
                  {c.progressPct.toFixed(1)}%
                </div>
              </div>
              <div style={{ display: 'flex', gap: 14, marginTop: 8, fontSize: 11, color: 'var(--ink-3)' }}>
                <span>{t('esgSgiBaseline')}: <span className="mono">{c.baseline.toLocaleString()}</span> {c.unit}</span>
                <span>{t('esgSgiCurrent')}: <span className="mono">{c.current.toLocaleString()}</span> {c.unit}</span>
                <span>{t('esgSgiTargetVal')}: <span className="mono">{c.target.toLocaleString()}</span> {c.unit}</span>
              </div>
              <div style={{ marginTop: 8, fontSize: 11, color: 'var(--ink-2)', lineHeight: 1.6, padding: 8, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)' }}>
                {lang === 'ar' ? c.notesAr : c.notesEn}
              </div>
            </div>
          ))}
        </div>
      ))}
    </Card>
  );
}

// ─── Survey tab ─────────────────────────────────────────────────────────────

function SurveyTab({ surveys, onSubmit }: {
  surveys: VendorEsgSurvey[];
  onSubmit: (id: string, patch: Partial<VendorEsgSurvey>) => void;
}) {
  const { t, lang } = useI18n();
  const [editId, setEditId] = useState<string | null>(null);
  const [draftScope12, setDraftScope12] = useState<string>('');
  const [draftRenewable, setDraftRenewable] = useState<string>('');
  const [draftSaudization, setDraftSaudization] = useState<string>('');
  const [draftIso, setDraftIso] = useState(false);
  const [draftNetZero, setDraftNetZero] = useState(false);

  function startEdit(s: VendorEsgSurvey) {
    setEditId(s.id);
    setDraftScope12(s.reportedScope12?.toString() ?? '');
    setDraftRenewable(s.renewablePct?.toString() ?? '');
    setDraftSaudization(s.saudizationPct?.toString() ?? '');
    setDraftIso(!!s.iso14001);
    setDraftNetZero(!!s.hasNetZeroTarget);
  }

  function submit(id: string) {
    onSubmit(id, {
      reportedScope12: parseFloat(draftScope12) || undefined,
      renewablePct: parseFloat(draftRenewable) || undefined,
      saudizationPct: parseFloat(draftSaudization) || undefined,
      iso14001: draftIso,
      hasNetZeroTarget: draftNetZero,
      status: 'SUBMITTED',
      updatedAt: new Date().toISOString().slice(0, 10),
    });
    setEditId(null);
  }

  const totalReportedScope12 = surveys.reduce((acc, s) => acc + (s.reportedScope12 ?? 0), 0);
  const submittedSpend = surveys
    .filter(s => s.status === 'SUBMITTED')
    .reduce((acc, s) => acc + s.spendSarK, 0);
  const totalSpend = surveys.reduce((acc, s) => acc + s.spendSarK, 0);
  const spendCoveragePct = totalSpend > 0 ? Math.round((submittedSpend / totalSpend) * 100) : 0;

  return (
    <Card title={t('esgSurveyTitle')} Icon={Truck}>
      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 12, lineHeight: 1.5 }}>
        {t('esgSurveySub')}
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: 8, marginBottom: 12 }}>
        <Stat label={t('esgSurveyKpiSpendCov')} value={`${spendCoveragePct}%`} />
        <Stat label={t('esgSurveyKpiReportedScope12')} value={`${(totalReportedScope12 / 1000).toFixed(1)}k tCO2e`} />
        <Stat label={t('esgSurveyKpiVendors')} value={`${surveys.filter(s => s.status === 'SUBMITTED').length} / ${surveys.length}`} />
      </div>
      <table className="tbl" style={{ width: '100%' }}>
        <thead>
          <tr>
            <th>{t('esgSurveyVendor')}</th>
            <th>{t('esgSurveySpend')}</th>
            <th>{t('esgSurveyStatus')}</th>
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
                <div style={{ fontWeight: 600 }}>{lang === 'ar' ? s.vendorNameAr : s.vendorNameEn}</div>
                <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>{s.id}</div>
              </td>
              <td className="mono">{s.spendSarK.toLocaleString()}k SAR</td>
              <td>
                <Badge tone={SURVEY_TONE[s.status]}>{SURVEY_LABEL[s.status][lang]}</Badge>
              </td>
              {editId === s.id ? (
                <>
                  <td>
                    <input value={draftScope12} onChange={e => setDraftScope12(e.target.value)}
                      type="number" placeholder="tCO2e" className="mono" style={{ ...inputStyle, width: 90 }} />
                  </td>
                  <td>
                    <input value={draftRenewable} onChange={e => setDraftRenewable(e.target.value)}
                      type="number" placeholder="%" className="mono" style={{ ...inputStyle, width: 60 }} />
                  </td>
                  <td>
                    <input value={draftSaudization} onChange={e => setDraftSaudization(e.target.value)}
                      type="number" placeholder="%" className="mono" style={{ ...inputStyle, width: 60 }} />
                  </td>
                  <td>
                    <label style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
                      <input type="checkbox" checked={draftIso} onChange={e => setDraftIso(e.target.checked)} />
                      ISO 14001
                    </label>
                    <label style={{ fontSize: 11, display: 'flex', alignItems: 'center', gap: 4 }}>
                      <input type="checkbox" checked={draftNetZero} onChange={e => setDraftNetZero(e.target.checked)} />
                      {t('esgSurveyNetZero')}
                    </label>
                  </td>
                  <td>
                    <Button size="sm" variant="primary" onClick={() => submit(s.id)}>{t('esgSurveySave')}</Button>
                  </td>
                </>
              ) : (
                <>
                  <td className="mono">{s.reportedScope12 != null ? s.reportedScope12.toLocaleString() : '—'}</td>
                  <td className="mono">{s.renewablePct != null ? `${s.renewablePct}%` : '—'}</td>
                  <td className="mono">{s.saudizationPct != null ? `${s.saudizationPct}%` : '—'}</td>
                  <td>
                    <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>
                      {s.iso14001 && <Badge tone="success">ISO</Badge>}
                      {s.hasNetZeroTarget && <Badge tone="info">NZ</Badge>}
                    </div>
                  </td>
                  <td>
                    <Button size="sm" variant="secondary" onClick={() => startEdit(s)}>{t('esgSurveyEdit')}</Button>
                  </td>
                </>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </Card>
  );
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '0.04em' }}>{label}</span>
      {children}
    </label>
  );
}

function Stat({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: 10, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
      background: 'var(--surface-2)',
    }}>
      <div style={{ fontSize: 10, color: 'var(--ink-3)', textTransform: 'uppercase' }}>{label}</div>
      <div style={{ fontSize: 16, fontWeight: 700, marginTop: 2 }}>{value}</div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
};
