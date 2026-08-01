import { useEffect, useState } from 'react';
import {
  ShieldCheck, Printer, Activity, AlertOctagon, ClipboardCheck, Stamp,
  Fingerprint, GitBranch, Landmark, FileText, AlertTriangle, Lock, Leaf,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import { fetchBoardPack, type BoardPackAggregate } from '../../lib/boardPackAggregate';
import { ApiError } from '../../lib/api';
import { canExport } from '../../lib/auth';
import { formatHijri } from '../../lib/sla';
import { loadWhiteLabel, subscribeWhiteLabel } from '../../lib/whiteLabel';
import './boardPack.css';

/**
 * BoardPackPage — the executive board pack, rendered from the server aggregate.
 *
 * <p>Print-styled bilingual artefact a board chair can carry to the audit
 * committee. "Print" triggers {@code window.print()} with the {@code @media print}
 * rules in boardPack.css that hide the chrome and lay sections out as A4 pages.
 *
 * <h3>F2-42 / ADR-017 D-5 — what changed, and why the content is different</h3>
 * <p>This page used to render {@code buildBoardPack()} from {@code lib/boardPack.ts},
 * which assembled a pack client-side from <b>eight fabricated sources</b> — three
 * named {@code mockCompliance}, {@code mockPrivacy}, {@code mockWorkflows}, one a
 * function called {@code synthesiseAllBreaches()}. Meanwhile the server built a
 * real pack from live repositories and served it as PDF and PPTX. <b>Two board
 * packs existed and the PDF was the correct one.</b>
 *
 * <p>⚠ <b>The consolidation was not a swap, and the plan row assumed it was.</b>
 * The two packs shared only <i>two</i> of ten sections. The fabricated page showed
 * appetite breaches, board approvals, audit engagements, PDPL 72h posture, CCM
 * freshness, asset blast-radius, chain fires and a regulator tally; the server
 * aggregate carries top risks, findings aging, oldest open findings, KRI bands,
 * framework coverage, change outcomes, ethics and sustainability. So the sections
 * on this page are <b>different sections</b>, not the same ones re-sourced —
 * because the fabricated eight had no real data behind them to re-source from.
 * ADR-017 D-5 chose exactly this: "expose the server aggregate as JSON and render
 * it; retire the fabricated page."
 *
 * <p>The sample-data banner is gone because the figures are now real. That is the
 * only honest reason to remove it.
 *
 * <h3>Why the audience narrowed</h3>
 * <p>The aggregate carries {@code hasPermission('BOARD','EXPORT')} — the same guard
 * as the PDF and the deck, since the same figures leave the building either way.
 * ADR-017 D-10a narrowed all three from 24 roles to 8. Anyone outside those 8 now
 * sees an explicit "board-confidential" notice rather than a page of errors: a 403
 * here is the control working, and it must not look like an outage.
 *
 * <p>⚠ Deliberately <b>not</b> fetched through {@code useApiList}: that hook
 * substitutes supplied mock data whenever a request fails or returns nothing. On a
 * board pack that is F2-42 rebuilt behind a different door.
 */
export function BoardPackPage() {
  const { t, lang } = useI18n();
  const ar = lang === 'ar';

  const [pack, setPack] = useState<BoardPackAggregate | null>(null);
  const [state, setState] = useState<'loading' | 'ready' | 'forbidden' | 'error'>('loading');
  const [errMsg, setErrMsg] = useState('');

  useEffect(() => {
    let alive = true;
    fetchBoardPack()
      .then(p => { if (alive) { setPack(p); setState('ready'); } })
      .catch((e: unknown) => {
        if (!alive) return;
        // 403 is the BOARD:EXPORT guard, not a failure. Distinguishing them is the
        // point: one is a permission conversation, the other is a bug report.
        if (e instanceof ApiError && e.status === 403) { setState('forbidden'); return; }
        setErrMsg(e instanceof ApiError ? e.message : String(e));
        setState('error');
      });
    return () => { alive = false; };
  }, []);

  // Track A4 — tenant branding on the cover, in lieu of the default TSS shield.
  const [brand, setBrand] = useState(() => loadWhiteLabel());
  useEffect(() => subscribeWhiteLabel(() => setBrand(loadWhiteLabel())), []);
  const brandName = ar ? brand.organisationNameAr : brand.organisationNameEn;

  if (state === 'loading') {
    return (
      <Shell title={t('boardPackTitle')} sub={t('boardPackSub')}>
        <div className="page" aria-busy="true" style={{ padding: 48, textAlign: 'center', opacity: 0.6 }}>
          {ar ? 'جارٍ تحميل حزمة المجلس…' : 'Loading the board pack…'}
        </div>
      </Shell>
    );
  }

  if (state === 'forbidden') {
    return (
      <Shell title={t('boardPackTitle')} sub={t('boardPackSub')}>
        <div className="card" data-testid="board-pack-forbidden"
             style={{ maxWidth: 620, margin: '48px auto', textAlign: 'center', padding: 32 }}>
          <Lock size={40} style={{ opacity: 0.5, marginBottom: 16 }} aria-hidden="true" />
          <h2 style={{ marginBottom: 12 }}>{t('boardPackForbiddenTitle')}</h2>
          <p style={{ opacity: 0.8, marginBottom: 8 }}>{t('boardPackForbiddenBody')}</p>
          <p style={{ opacity: 0.7, fontSize: 14 }}>{t('boardPackForbiddenHint')}</p>
        </div>
      </Shell>
    );
  }

  if (state === 'error' || !pack) {
    return (
      <Shell title={t('boardPackTitle')} sub={t('boardPackSub')}>
        <div className="card" data-testid="board-pack-error"
             style={{ maxWidth: 620, margin: '48px auto', textAlign: 'center', padding: 32 }}>
          <AlertTriangle size={40} style={{ opacity: 0.5, marginBottom: 16 }} aria-hidden="true" />
          <h2 style={{ marginBottom: 12 }}>{t('boardPackErrorTitle')}</h2>
          {/* No fabricated fallback. A board pack that silently shows demo figures
              on a failed request is the defect this slice removed. */}
          <p style={{ opacity: 0.8, marginBottom: 8 }}>{t('boardPackErrorBody')}</p>
          {errMsg && <p className="mono" style={{ opacity: 0.6, fontSize: 12 }}>{errMsg}</p>}
        </div>
      </Shell>
    );
  }

  const h = pack.header;
  const ex = pack.executive;
  const orgName = (ar ? h?.organisationNameAr : h?.organisationNameEn) || brandName;
  const generated = h?.generatedAt ? h.generatedAt.replace('T', ' ').slice(0, 16) : '—';
  const asOf = h?.asOf ?? '';

  return (
    <Shell title={t('boardPackTitle')} sub={t('boardPackSub')}>
      {/* Action bar — hidden during print */}
      <div className="bp-actions" style={{
        display: 'flex', justifyContent: 'flex-end', gap: 8, marginBottom: 16,
      }}>
        <Button variant="primary" Icon={Printer} onClick={() => window.print()}>
          {t('boardPackPrint')}
        </Button>
      </div>

      <div className="bp-document" dir={ar ? 'rtl' : 'ltr'}>
        {/* ───── 1. COVER ───── */}
        <section className="bp-page bp-cover">
          <div className="bp-cover-mark" style={brand.logoDataUrl ? { background: 'transparent', boxShadow: 'none' } : undefined}>
            {brand.logoDataUrl
              ? <img src={brand.logoDataUrl} alt={orgName ?? ''} style={{ width: 96, height: 96, objectFit: 'contain' }} />
              : <ShieldCheck size={48} />}
          </div>
          <div className="bp-cover-eyebrow">{orgName}</div>
          <h1 className="bp-cover-title">{t('boardPackTitle')}</h1>
          <div className="bp-cover-period">
            {asOf ? `${t('boardPackAsOf')} ${asOf}` : ''}
          </div>
          <div className="bp-cover-meta">
            <div>
              <span className="bp-meta-label">{t('boardPackGenerated')}</span>
              <span className="bp-meta-value">
                {generated}
                {asOf && <span className="bp-meta-hijri"> · {formatHijri(asOf, lang)}</span>}
              </span>
            </div>
            <div>
              <span className="bp-meta-label">{t('boardPackClassification')}</span>
              <span className="bp-meta-value">{t('boardPackConfidential')}</span>
            </div>
          </div>
        </section>

        {/* ───── 2. EXECUTIVE SUMMARY ───── */}
        <section className="bp-page">
          <SectionHeader idx={2} icon={Activity} title={t('boardPackS1Title')} sub={t('boardPackS1Sub')} />
          <div className="bp-kpi-grid">
            <KpiTile label={t('bpKpiTotalRisks')}   value={ex?.totalRisks ?? 0}    tone="primary" />
            <KpiTile label={t('bpKpiCriticalRisks')} value={ex?.criticalRisks ?? 0} tone={(ex?.criticalRisks ?? 0) > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('bpKpiOpenRisks')}    value={ex?.openRisks ?? 0}     tone="info" />
            <KpiTile label={t('bpKpiOpenFindings')} value={ex?.openFindings ?? 0}  tone={(ex?.openFindings ?? 0) > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('bpKpiControls')}     value={ex?.totalControls ?? 0} tone="primary" />
            <KpiTile label={t('bpKpiKris')}         value={ex?.totalKris ?? 0}     tone="info" />
            <KpiTile label={t('bpKpiEthicsCases')}  value={ex?.totalEthicsCases ?? 0} tone="info" />
            <KpiTile label={t('bpKpiStrs')}         value={ex?.strsFiled ?? 0}     tone={(ex?.strsFiled ?? 0) > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('bpKpiAmlAlerts')}    value={ex?.amlAlerts ?? 0}     tone={(ex?.amlAlerts ?? 0) > 0 ? 'warning' : 'success'} />
          </div>
        </section>

        {/* ───── 3. TOP RISKS ───── */}
        <section className="bp-page">
          <SectionHeader idx={3} icon={AlertOctagon} title={t('bpS3Title')} sub={t('bpS3Sub')} />
          {!pack.topRisks?.length
            ? <p className="bp-empty">{t('bpNoRows')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('bpTblRef')}</th>
                    <th>{t('bpTblRisk')}</th>
                    <th>{t('bpTblInherent')}</th>
                    <th>{t('bpTblResidual')}</th>
                    <th>{t('bpTblStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.topRisks.map(r => (
                    <tr key={r.id ?? r.reference}>
                      <td className="mono">{r.reference ?? '—'}</td>
                      <td>{(ar ? r.titleAr : r.titleEn) || r.titleEn || '—'}</td>
                      <td className="mono">{r.inherentScore ?? '—'}</td>
                      <td className="mono">
                        <span className={`bp-pill bp-pill-${scoreTone(r.residualScore)}`}>{r.residualScore ?? '—'}</span>
                      </td>
                      <td>{r.status ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </section>

        {/* ───── 4. FINDINGS AGING ───── */}
        <section className="bp-page">
          <SectionHeader idx={4} icon={ClipboardCheck} title={t('bpS4Title')} sub={t('bpS4Sub')} />
          <div className="bp-kpi-grid bp-kpi-grid-3">
            <KpiTile label={t('bpAgingOpen')}      value={pack.aging?.totalOpen ?? 0}  tone="primary" />
            <KpiTile label={t('bpAgingOnTrack')}   value={pack.aging?.onTrack ?? 0}    tone="success" />
            <KpiTile label={t('bpAgingManager')}   value={pack.aging?.manager ?? 0}    tone="info" />
            <KpiTile label={t('bpAgingDirector')}  value={pack.aging?.director ?? 0}   tone="warning" />
            <KpiTile label={t('bpAgingExecutive')} value={pack.aging?.executive ?? 0}  tone={(pack.aging?.executive ?? 0) > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('bpAgingClosed')}    value={pack.aging?.closed ?? 0}     tone="success" />
          </div>
        </section>

        {/* ───── 5. OLDEST OPEN FINDINGS ───── */}
        <section className="bp-page">
          <SectionHeader idx={5} icon={Stamp} title={t('bpS5Title')} sub={t('bpS5Sub')} />
          {!pack.oldestOpenFindings?.length
            ? <p className="bp-empty">{t('bpNoRows')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('bpTblFinding')}</th>
                    <th>{t('bpTblSeverity')}</th>
                    <th>{t('bpTblOwner')}</th>
                    <th>{t('bpTblDaysOpen')}</th>
                    <th>{t('bpTblStatus')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.oldestOpenFindings.map(f => (
                    <tr key={f.id ?? f.title}>
                      <td>{f.title ?? '—'}</td>
                      <td><span className={`bp-pill bp-pill-${severityTone(f.severity)}`}>{f.severity ?? '—'}</span></td>
                      <td>{f.owner ?? '—'}</td>
                      <td className="mono">{f.daysOpen ?? '—'}</td>
                      <td>{f.status ?? '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </section>

        {/* ───── 6. KRI BANDS ───── */}
        <section className="bp-page">
          <SectionHeader idx={6} icon={Fingerprint} title={t('bpS6Title')} sub={t('bpS6Sub')} />
          <div className="bp-kpi-grid bp-kpi-grid-3">
            <KpiTile label={t('bpKriGreen')}  value={pack.kris?.green ?? 0}  tone="success" />
            <KpiTile label={t('bpKriAmber')}  value={pack.kris?.amber ?? 0}  tone="warning" />
            <KpiTile label={t('bpKriRed')}    value={pack.kris?.red ?? 0}    tone={(pack.kris?.red ?? 0) > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('bpKriNoData')} value={pack.kris?.noData ?? 0} tone="info" />
          </div>
          {!!pack.kris?.rows?.length && (
            <table className="bp-table" style={{ marginTop: 12 }}>
              <thead>
                <tr>
                  <th>{t('bpTblKri')}</th>
                  <th>{t('bpTblBand')}</th>
                  <th>{t('bpTblLatest')}</th>
                  <th>{t('bpTblGreen')}</th>
                  <th>{t('bpTblAmber')}</th>
                </tr>
              </thead>
              <tbody>
                {pack.kris.rows.map(k => (
                  <tr key={k.id ?? k.code}>
                    <td><strong className="mono">{k.code ?? '—'}</strong> — {(ar ? k.titleAr : k.titleEn) || k.titleEn || ''}</td>
                    <td><span className={`bp-pill bp-pill-${bandTone(k.band)}`}>{k.band ?? '—'}</span></td>
                    <td className="mono">{k.latestValue ?? '—'}</td>
                    <td className="mono">{k.greenThreshold ?? '—'}</td>
                    <td className="mono">{k.amberThreshold ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </section>

        {/* ───── 7. FRAMEWORK COVERAGE ───── */}
        <section className="bp-page">
          <SectionHeader idx={7} icon={FileText} title={t('bpS7Title')} sub={t('bpS7Sub')} />
          {!pack.frameworkCoverage?.length
            ? <p className="bp-empty">{t('bpNoRows')}</p>
            : <table className="bp-table">
                <thead>
                  <tr>
                    <th>{t('bpTblFramework')}</th>
                    <th>{t('bpTblControls')}</th>
                    <th>{t('bpTblPolicies')}</th>
                    <th>{t('bpTblFullPct')}</th>
                    <th>{t('bpTblOpenFindings')}</th>
                  </tr>
                </thead>
                <tbody>
                  {pack.frameworkCoverage.map(f => (
                    <tr key={f.code}>
                      <td><strong>{f.code}</strong> — {(ar ? f.nameAr : f.nameEn) || f.nameEn || ''}</td>
                      <td className="mono">{f.controls}</td>
                      <td className="mono">{f.policies}</td>
                      <td className="mono">
                        <span className={`bp-pill bp-pill-${pctTone(f.fullPct)}`}>{f.fullPct == null ? '—' : `${f.fullPct}%`}</span>
                      </td>
                      <td className="mono">{f.openFindings}</td>
                    </tr>
                  ))}
                </tbody>
              </table>}
        </section>

        {/* ───── 8. CHANGE OUTCOMES ───── */}
        <section className="bp-page">
          <SectionHeader idx={8} icon={GitBranch} title={t('bpS8Title')} sub={t('bpS8Sub')} />
          <div className="bp-kpi-grid bp-kpi-grid-3">
            <KpiTile label={t('bpChgTotal')}      value={pack.changes?.total ?? 0}      tone="primary" />
            <KpiTile label={t('bpChgInFlight')}   value={pack.changes?.inFlight ?? 0}   tone="info" />
            <KpiTile label={t('bpChgSuccess')}    value={pack.changes?.success ?? 0}    tone="success" />
            <KpiTile label={t('bpChgRolledBack')} value={pack.changes?.rolledBack ?? 0} tone={(pack.changes?.rolledBack ?? 0) > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('bpChgEmergency')}  value={pack.changes?.emergency ?? 0}  tone={(pack.changes?.emergency ?? 0) > 0 ? 'warning' : 'success'} />
          </div>
        </section>

        {/* ───── 9. ETHICS & FINANCIAL CRIME ───── */}
        <section className="bp-page">
          <SectionHeader idx={9} icon={Landmark} title={t('bpS9Title')} sub={t('bpS9Sub')} />
          <div className="bp-kpi-grid bp-kpi-grid-3">
            <KpiTile label={t('bpEthTotal')}         value={pack.ethics?.totalCases ?? 0}        tone="primary" />
            <KpiTile label={t('bpEthOpen')}          value={pack.ethics?.open ?? 0}              tone="info" />
            <KpiTile label={t('bpEthSubstantiated')} value={pack.ethics?.substantiated ?? 0}     tone={(pack.ethics?.substantiated ?? 0) > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('bpEthBribery')}       value={pack.ethics?.briberyOrKickback ?? 0} tone={(pack.ethics?.briberyOrKickback ?? 0) > 0 ? 'danger' : 'success'} />
            <KpiTile label={t('bpEthAml')}           value={pack.ethics?.amlSuspicion ?? 0}      tone={(pack.ethics?.amlSuspicion ?? 0) > 0 ? 'warning' : 'success'} />
            <KpiTile label={t('bpEthRetaliation')}   value={pack.ethics?.retaliation ?? 0}       tone={(pack.ethics?.retaliation ?? 0) > 0 ? 'danger' : 'success'} />
          </div>
        </section>

        {/* ───── 10. SUSTAINABILITY ───── */}
        <section className="bp-page">
          <SectionHeader idx={10} icon={Leaf} title={t('bpS10Title')} sub={t('bpS10Sub')} />
          {/* `available: false` renders as unavailable, NOT as zeroes — a board pack
              reporting 0 tCO2e reads as "we emit nothing", which is a different and
              much worse claim than "this section could not be computed". */}
          {!pack.sustainability?.available
            ? <p className="bp-empty">{t('bpSustUnavailable')}</p>
            : <div className="bp-kpi-grid bp-kpi-grid-3">
                <KpiTile label={t('bpSustScore')}   value={fmt(pack.sustainability.esgScore)} tone="primary" />
                <KpiTile label={t('bpSustBand')}    value={pack.sustainability.esgBand ?? '—'} tone="info" />
                <KpiTile label={t('bpSustGhg')}     value={fmt(pack.sustainability.ghgTotal)} tone="info" />
                <KpiTile label={t('bpSustScope3')}  value={fmt(pack.sustainability.scope3Total)} tone="info" />
                <KpiTile label={t('bpSustCma')}     value={pct(pack.sustainability.cmaReadinessPct)} tone="primary" />
                <KpiTile label={t('bpSustTcfd')}    value={pct(pack.sustainability.tcfdAlignmentPct)} tone="primary" />
                <KpiTile label={t('bpSustSgi')}     value={`${pack.sustainability.sgiOnTrack}/${pack.sustainability.sgiTotal}`} tone="success" />
                <KpiTile label={t('bpSustCapas')}   value={fmt(pack.sustainability.openCapas)} tone={(pack.sustainability.openCapas ?? 0) > 0 ? 'warning' : 'success'} />
                <KpiTile label={t('bpSustGaps')}    value={pack.sustainability.topGapCount} tone={pack.sustainability.topGapCount > 0 ? 'warning' : 'success'} />
              </div>}
        </section>

        {/* ───── SIGN-OFF ───── */}
        <section className="bp-page bp-signoff">
          <SectionHeader idx={11} icon={AlertTriangle} title={t('boardPackS10Title')} sub={t('boardPackS10Sub')} />
          <div className="bp-signoff-grid">
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignBoardChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignAuditChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignRiskChair')}</div>
            </div>
            <div className="bp-signoff-cell">
              <div className="bp-signoff-line"></div>
              <div className="bp-signoff-label">{t('boardPackSignCEO')}</div>
            </div>
          </div>
          <p className="bp-methodology">{t('bpProvenance')}</p>
        </section>
      </div>
    </Shell>
  );
}

/** Does the current user hold BOARD:EXPORT? Used by the sidebar to hide the entry. */
export function canSeeBoardPack(): boolean {
  return canExport('BOARD');
}

// ─── Helpers ────────────────────────────────────────────────────────────────

function SectionHeader({ idx, icon: Icon, title, sub }: { idx: number; icon: typeof Activity; title: string; sub: string }) {
  return (
    <div className="bp-section-header">
      <div className="bp-section-num">{idx}</div>
      <div className="bp-section-text">
        <div className="bp-section-title"><Icon size={16} /> {title}</div>
        <div className="bp-section-sub">{sub}</div>
      </div>
    </div>
  );
}

function KpiTile({ label, value, tone }: { label: string; value: string | number; tone: 'primary' | 'success' | 'warning' | 'danger' | 'info' }) {
  return (
    <div className={`bp-kpi bp-kpi-${tone}`}>
      <div className="bp-kpi-value">{value}</div>
      <div className="bp-kpi-label">{label}</div>
    </div>
  );
}

/** `null` renders as an em dash, never as 0 — an absent figure is not a zero one. */
function fmt(v: number | null | undefined): string {
  return v == null ? '—' : Number.isInteger(v) ? String(v) : v.toFixed(1);
}
function pct(v: number | null | undefined): string {
  return v == null ? '—' : `${Math.round(v)}%`;
}
function scoreTone(s: number | null): string {
  if (s == null) return 'info';
  return s >= 20 ? 'danger' : s >= 12 ? 'warning' : s >= 6 ? 'info' : 'success';
}
function severityTone(s: string | null): string {
  return s === 'CRITICAL' ? 'danger' : s === 'HIGH' ? 'warning' : s === 'MEDIUM' ? 'info' : 'success';
}
function bandTone(b: string | null): string {
  return b === 'RED' ? 'danger' : b === 'AMBER' ? 'warning' : b === 'GREEN' ? 'success' : 'info';
}
function pctTone(p: number | null): string {
  if (p == null) return 'info';
  return p >= 80 ? 'success' : p >= 50 ? 'warning' : 'danger';
}
