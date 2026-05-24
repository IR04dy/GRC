import { useEffect, useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import {
  AlertOctagon, AlertTriangle, BarChart3, BookOpen,
  Camera, ClipboardCheck, Layers, Leaf,
  Microscope, Newspaper, Printer, ShieldCheck, Truck, Users,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  auditCommitteeDashboardApi,
  fmtNumber, fmtSar,
  type LiveDashboardResponse, type MeetingType,
} from '../../lib/auditCommitteeDashboard';

/**
 * Audit Committee Dashboard — Sprint 11.
 *
 * Saudi-native bilingual heading:
 *   ar: لوحة لجنة المراجعة   ← NOT literal "لوحة لجنة التدقيق"
 *   en: Audit Committee Dashboard
 *
 * Layout: 10 section cards in a responsive grid. Each card has a
 * primary KPI tile (large) + 2-3 supporting tiles + a "drill down"
 * link to the source module.
 *
 * Live vs snapshot:
 *   - Live mode (default): hits GET /api/v1/audit-committee/dashboard/live
 *     every page load — numbers reconcile to current source data.
 *   - Snapshot mode (`?snapshot=<id>`): redirects to the snapshot
 *     detail page which reads the frozen JSON.
 *
 * Print-ready: A4 portrait CSS via `@media print` so the Committee
 * can print the page directly into meeting minutes. Sidebar /
 * topbar hidden, KPI tiles laid out as a 2-column grid, page-break
 * after every 5 sections.
 */
export function AuditCommitteeDashboardPage() {
  const { lang } = useI18n();
  const t = (en: string, ar: string) => (lang === 'ar' ? ar : en);
  const demoMode = !isAuthenticated();
  const [searchParams] = useSearchParams();

  // If ?snapshot=<id> is present we surface a "viewing snapshot" hint
  // at the top — the actual frozen view lives on /audit-committee/snapshots/:id.
  const snapshotId = searchParams.get('snapshot');

  const [live, setLive] = useState<LiveDashboardResponse | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const [showGenerateModal, setShowGenerateModal] = useState(false);
  const [meetingDate, setMeetingDate] = useState<string>(() => {
    const d = new Date();
    return d.toISOString().split('T')[0];
  });
  const [meetingType, setMeetingType] = useState<MeetingType>('QUARTERLY');
  const [generateBusy, setGenerateBusy] = useState(false);
  const [generateError, setGenerateError] = useState<string | null>(null);
  const [generateSuccess, setGenerateSuccess] = useState<string | null>(null);

  async function load() {
    if (demoMode) {
      setLive(null);
      return;
    }
    setLoading(true);
    setError(null);
    try {
      setLive(await auditCommitteeDashboardApi.live());
    } catch (err) {
      setError(err instanceof ApiError ? err.message
        : t('Failed to load Audit Committee dashboard', 'تعذّر تحميل لوحة لجنة المراجعة'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, []);

  async function generate() {
    setGenerateBusy(true);
    setGenerateError(null);
    setGenerateSuccess(null);
    try {
      const snap = await auditCommitteeDashboardApi.generate({
        meetingDate, meetingType,
      });
      setGenerateSuccess(snap.snapshotCode);
      // Auto-close modal after a beat — the user can click into the snapshot.
      setTimeout(() => {
        setShowGenerateModal(false);
        setGenerateSuccess(null);
      }, 1500);
    } catch (err) {
      setGenerateError(err instanceof ApiError ? err.message
        : t('Failed to generate snapshot', 'تعذّر إنشاء اللقطة'));
    } finally {
      setGenerateBusy(false);
    }
  }

  const ccmPassPct = useMemo(
    () => Math.round((live?.controls.ccmPassRateLast24h ?? 0) * 100),
    [live]);

  return (
    <Shell title={t('Audit Committee Dashboard', 'لوحة لجنة المراجعة')}
           sub={t('SAMA CG §6 · CMA Art.54', 'SAMA الحوكمة §6 · CMA المادة 54')}>
      <PrintStyles />

      <PageHeader
        eyebrow={t('Governance · Audit Committee', 'الحوكمة · لجنة المراجعة')}
        title={t('Audit Committee Dashboard', 'لوحة لجنة المراجعة')}
        sub={t(
          'Consolidated view of risk, controls, findings, CAPA, AI, climate, obligations, incidents, vendor BCP and 3LoD — the four canonical Audit Committee questions in a single board-facing page.',
          'عرض موحَّد للمخاطر والضوابط والملاحظات والإجراءات التصحيحية والذكاء الاصطناعي والمناخ والالتزامات والحوادث وخطط استمرارية المورِّدين وخطوط الدفاع الثلاث — أسئلة لجنة المراجعة الأربعة في صفحة واحدة موجَّهة للمجلس.'
        )}
        actions={
          <div style={{ display: 'flex', gap: 8 }} className="no-print">
            <button onClick={() => window.print()}
                    style={{ padding: '6px 12px', border: '1px solid var(--border)',
                             borderRadius: 6, background: 'var(--surface)',
                             color: 'var(--text)', fontSize: 13, cursor: 'pointer' }}>
              <Printer size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {t('Print A4', 'طباعة A4')}
            </button>
            <button onClick={() => setShowGenerateModal(true)}
                    style={{ padding: '6px 12px', border: '1px solid var(--primary)',
                             borderRadius: 6, background: 'var(--primary)',
                             color: 'white', fontSize: 13, cursor: 'pointer' }}>
              <Camera size={14} style={{ verticalAlign: '-2px', marginInlineEnd: 6 }} />
              {t('Generate snapshot for meeting', 'لقطة الاجتماع')}
            </button>
            {demoMode && <Badge tone="warning">{t('Demo mode', 'وضع العرض')}</Badge>}
          </div>
        }
      />

      {snapshotId && (
        <Card padded>
          <div style={{ fontSize: 13 }}>
            {t('Snapshot reference in URL — open the snapshot detail page:',
               'مرجع لقطة في الرابط — افتح صفحة تفاصيل اللقطة:')}{' '}
            <Link to={`/audit-committee/snapshots/${snapshotId}`}>
              {t('view snapshot', 'عرض اللقطة')}
            </Link>
          </div>
        </Card>
      )}

      {error && (
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>
        </Card>
      )}
      {loading && !live && (
        <Card padded>
          <InlineEmpty>{t('Loading live dashboard…', 'جارٍ تحميل اللوحة المباشرة…')}</InlineEmpty>
        </Card>
      )}

      {!live && !loading && (
        <EmptyState
          Icon={BarChart3}
          title={t('No data yet', 'لا توجد بيانات بعد')}
          sub={t('Sign in and ensure the underlying modules are populated.',
                 'سجّل الدخول وتأكد من تعبئة الوحدات الأساسية.')} />
      )}

      {live && (
        <div className="ac-grid">
          {/* Section 1 — Risk */}
          <Card padded>
            <SectionTitle Icon={AlertTriangle} title={t('Risk posture', 'وضع المخاطر')}
                          to="/risk" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={AlertTriangle}
                   label={t('Open risks', 'المخاطر المفتوحة')}
                   value={fmtNumber(live.risk.totalRisks, lang)}
                   tone="primary" />
              <Kpi Icon={AlertOctagon}
                   label={t('Above appetite', 'تتجاوز الشهية')}
                   value={fmtNumber(live.risk.aboveAppetiteCount, lang)}
                   tone={live.risk.aboveAppetiteCount > 0 ? 'danger' : 'success'} />
              <Kpi Icon={ShieldCheck}
                   label={t('Critical', 'حرجة')}
                   value={fmtNumber(live.risk.criticalCount, lang)}
                   tone={live.risk.criticalCount > 0 ? 'danger' : 'success'} />
            </div>
            {live.risk.topAboveAppetite.length > 0 && (
              <div style={{ marginTop: 12, fontSize: 12, color: 'var(--text-muted)' }}>
                {t('Top 5 above-appetite risks:', 'أعلى 5 مخاطر تتجاوز الشهية:')}
                <ol style={{ margin: '6px 0 0 0', paddingInlineStart: 18 }}>
                  {live.risk.topAboveAppetite.map(r => (
                    <li key={r.id}>
                      <span style={{ fontWeight: 500 }}>
                        {lang === 'ar' ? (r.titleAr ?? r.titleEn) : r.titleEn}
                      </span>
                      <span style={{ marginInlineStart: 8, color: 'var(--text-muted)' }}>
                        ({r.riskLevel ?? '—'} · {fmtNumber(r.residualScore ?? 0, lang)})
                      </span>
                    </li>
                  ))}
                </ol>
              </div>
            )}
          </Card>

          {/* Section 2 — Controls */}
          <Card padded>
            <SectionTitle Icon={ShieldCheck} title={t('Control posture', 'وضع الضوابط')}
                          to="/controls/library" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={ShieldCheck}
                   label={t('Total controls', 'إجمالي الضوابط')}
                   value={fmtNumber(live.controls.totalControls, lang)}
                   tone="primary" />
              <Kpi Icon={BarChart3}
                   label={t('Effectiveness', 'الفعالية')}
                   value={`${Math.round(live.controls.effectivenessPct)}%`}
                   tone={live.controls.effectivenessPct >= 80 ? 'success' :
                         live.controls.effectivenessPct >= 50 ? 'warning' : 'danger'} />
              <Kpi Icon={ClipboardCheck}
                   label={t('CCM pass rate (24h)', 'نسبة نجاح المراقبة (24س)')}
                   value={`${ccmPassPct}%`}
                   tone={ccmPassPct >= 95 ? 'success' : ccmPassPct >= 80 ? 'warning' : 'danger'} />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('CCM enabled tests:', 'الاختبارات المُفعَّلة:')}{' '}
              {fmtNumber(live.controls.ccmEnabledTests, lang)}{' '}/{' '}
              {fmtNumber(live.controls.ccmTotalTests, lang)}
            </div>
          </Card>

          {/* Section 3 — Findings */}
          <Card padded>
            <SectionTitle Icon={Microscope} title={t('Audit findings', 'ملاحظات المراجعة')}
                          to="/audit-workspace" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Microscope}
                   label={t('Open findings', 'الملاحظات المفتوحة')}
                   value={fmtNumber(live.findings.totalOpen, lang)} tone="primary" />
              <Kpi Icon={AlertOctagon}
                   label={t('Critical', 'حرجة')}
                   value={fmtNumber(live.findings.criticalOpen, lang)}
                   tone={live.findings.criticalOpen > 0 ? 'danger' : 'success'} />
              <Kpi Icon={BarChart3}
                   label={t('Closure rate (Q)', 'نسبة الإغلاق (الربع)')}
                   value={`${Math.round(live.findings.closureRateLastQuarterPct)}%`}
                   tone={live.findings.closureRateLastQuarterPct >= 70 ? 'success' : 'warning'} />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Age buckets:', 'الفئات العمرية:')}{' '}
              0–30: {fmtNumber(live.findings.ageBucket0to30, lang)} ·
              30–90: {fmtNumber(live.findings.ageBucket30to90, lang)} · &gt;90:{' '}
              <span style={{ color: live.findings.ageBucketOver90 > 0
                                ? 'var(--danger)' : 'var(--text-muted)' }}>
                {fmtNumber(live.findings.ageBucketOver90, lang)}
              </span>
            </div>
          </Card>

          {/* Section 4 — CAPA */}
          <Card padded>
            <SectionTitle Icon={ClipboardCheck} title={t('CAPA progress', 'الإجراءات التصحيحية')}
                          to="/capa" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={ClipboardCheck}
                   label={t('Open CAPA', 'الإجراءات المفتوحة')}
                   value={fmtNumber(live.capa.totalOpen, lang)} tone="primary" />
              <Kpi Icon={AlertOctagon}
                   label={t('Overdue', 'متأخّرة')}
                   value={fmtNumber(live.capa.overdueCount, lang)}
                   tone={live.capa.overdueCount > 0 ? 'danger' : 'success'} />
              <Kpi Icon={BarChart3}
                   label={t('On-track ratio', 'النسبة على المسار')}
                   value={`${Math.round(live.capa.onTrackRatioPct)}%`}
                   tone={live.capa.onTrackRatioPct >= 80 ? 'success' : 'warning'} />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Closed this quarter:', 'مُغلقة هذا الربع:')}{' '}
              {fmtNumber(live.capa.closedThisQuarter, lang)}
            </div>
          </Card>

          {/* Section 5 — AI */}
          <Card padded>
            <SectionTitle Icon={Layers} title={t('AI / ML models', 'نماذج الذكاء الاصطناعي')}
                          to="/ai/models" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Layers}
                   label={t('Total models', 'إجمالي النماذج')}
                   value={fmtNumber(live.ai.totalModels, lang)} tone="primary" />
              <Kpi Icon={AlertTriangle}
                   label={t('Critical / High', 'حرجة / مرتفعة')}
                   value={fmtNumber(live.ai.criticalCount + live.ai.highCount, lang)}
                   tone="warning" />
              <Kpi Icon={AlertOctagon}
                   label={t('Missing fairness audit', 'بدون تدقيق إنصاف')}
                   value={fmtNumber(live.ai.missingFairnessAuditCount, lang)}
                   tone={live.ai.missingFairnessAuditCount > 0 ? 'danger' : 'success'} />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Unacknowledged drift observations:',
                 'انحرافات غير مُؤكَّدة:')}{' '}
              <span style={{ color: live.ai.unacknowledgedDriftCount > 0
                                ? 'var(--warning)' : 'var(--text-muted)' }}>
                {fmtNumber(live.ai.unacknowledgedDriftCount, lang)}
              </span>
            </div>
          </Card>

          {/* Section 6 — Climate */}
          <Card padded>
            <SectionTitle Icon={Leaf} title={t('Climate', 'المناخ')}
                          to="/esg" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Leaf}
                   label={t('Climate items', 'بنود المناخ')}
                   value={fmtNumber(live.climate.totalScenarios, lang)} tone="primary" />
              <Kpi Icon={BarChart3}
                   label={t('Expected loss (PW)', 'الخسارة المتوقّعة (مرجَّحة)')}
                   value={fmtSar(live.climate.probabilityWeightedExpectedLossSar, lang)}
                   tone="info" />
              <Kpi Icon={AlertTriangle}
                   label={t('YoY emissions', 'الانبعاثات سنوياً')}
                   value={live.climate.financedEmissionsYoyChangePct == null ? '—'
                          : `${Math.round(live.climate.financedEmissionsYoyChangePct)}%`}
                   tone={(live.climate.financedEmissionsYoyChangePct ?? 0) <= 0
                         ? 'success' : 'warning'} />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Current tCO2e:', 'tCO2e الحالي:')}{' '}
              {fmtNumber(Math.round(live.climate.financedEmissionsCurrentTco2e), lang)}
              {' '}({t('previous:', 'السابق:')}{' '}
              {fmtNumber(Math.round(live.climate.financedEmissionsPreviousTco2e), lang)})
            </div>
          </Card>

          {/* Section 7 — Obligations */}
          <Card padded>
            <SectionTitle Icon={Newspaper} title={t('Obligations', 'الالتزامات التنظيمية')}
                          to="/compliance/obligations" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Newspaper}
                   label={t('Active obligations', 'الالتزامات النشطة')}
                   value={fmtNumber(live.obligations.totalActive, lang)} tone="primary" />
              <Kpi Icon={AlertOctagon}
                   label={t('Overdue', 'متأخّرة')}
                   value={fmtNumber(live.obligations.overdueCount, lang)}
                   tone={live.obligations.overdueCount > 0 ? 'danger' : 'success'} />
              <Kpi Icon={BarChart3}
                   label={t('Attested', 'مُقَرّ بها')}
                   value={`${Math.round(live.obligations.attestedThisCyclePct)}%`}
                   tone={live.obligations.attestedThisCyclePct >= 80 ? 'success' : 'warning'} />
            </div>
            {Object.keys(live.obligations.regulatorBreakdown).length > 0 && (
              <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                {t('By regulator:', 'حسب الجهة:')}{' '}
                {Object.entries(live.obligations.regulatorBreakdown)
                  .sort((a, b) => b[1] - a[1])
                  .slice(0, 6)
                  .map(([r, n]) => `${r}: ${fmtNumber(n, lang)}`)
                  .join(' · ')}
              </div>
            )}
          </Card>

          {/* Section 8 — Incidents */}
          <Card padded>
            <SectionTitle Icon={AlertOctagon} title={t('Incidents', 'الحوادث')}
                          to="/incidents" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={AlertOctagon}
                   label={t('Last quarter', 'الربع الأخير')}
                   value={fmtNumber(live.incidents.lastQuarterTotal, lang)} tone="primary" />
              <Kpi Icon={AlertTriangle}
                   label={t('Critical / High', 'حرجة / مرتفعة')}
                   value={fmtNumber(
                       live.incidents.lastQuarterCritical + live.incidents.lastQuarterHigh,
                       lang)}
                   tone={(live.incidents.lastQuarterCritical + live.incidents.lastQuarterHigh) > 0
                         ? 'danger' : 'success'} />
              <Kpi Icon={BarChart3}
                   label={t('Avg MTTR (h)', 'متوسط MTTR (س)')}
                   value={live.incidents.mttrAverageHours == null ? '—'
                          : `${Math.round(live.incidents.mttrAverageHours)}`}
                   tone="info" />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Regulator-notifiable:', 'تُبلَّغ بها الجهة الرقابية:')}{' '}
              <span style={{ color: live.incidents.regulatorNotifiableCount > 0
                                ? 'var(--warning)' : 'var(--text-muted)' }}>
                {fmtNumber(live.incidents.regulatorNotifiableCount, lang)}
              </span>
            </div>
          </Card>

          {/* Section 9 — Vendor BCP */}
          <Card padded>
            <SectionTitle Icon={Truck} title={t('Vendor BCP', 'استمرارية أعمال المورِّدين')}
                          to="/supplier-resilience" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Truck}
                   label={t('Invited', 'المدعوون')}
                   value={fmtNumber(live.vendorBcp.latestCampaignInvited, lang)} tone="primary" />
              <Kpi Icon={BarChart3}
                   label={t('Response rate', 'نسبة الاستجابة')}
                   value={`${Math.round(live.vendorBcp.latestCampaignResponseRatePct)}%`}
                   tone={live.vendorBcp.latestCampaignResponseRatePct >= 80 ? 'success' : 'warning'} />
              <Kpi Icon={AlertOctagon}
                   label={t('Below RTO', 'تحت RTO')}
                   value={fmtNumber(live.vendorBcp.belowRtoThresholdCount, lang)}
                   tone={live.vendorBcp.belowRtoThresholdCount > 0 ? 'danger' : 'success'} />
            </div>
            {live.vendorBcp.latestCampaignCode && (
              <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
                {t('Latest campaign:', 'آخر حملة:')}{' '}
                <code>{live.vendorBcp.latestCampaignCode}</code>
              </div>
            )}
          </Card>

          {/* Section 10 — 3LoD */}
          <Card padded>
            <SectionTitle Icon={Users} title={t('Three Lines of Defense', 'خطوط الدفاع الثلاثة')}
                          to="/governance/three-lines" t={t} />
            <div className="kpi-grid-sm">
              <Kpi Icon={Users}
                   label={t('Line 1', 'الخط الأول')}
                   value={fmtNumber(live.threeLines.line1Count, lang)} tone="primary" />
              <Kpi Icon={Users}
                   label={t('Line 2', 'الخط الثاني')}
                   value={fmtNumber(live.threeLines.line2Count, lang)} tone="info" />
              <Kpi Icon={Users}
                   label={t('Line 3', 'الخط الثالث')}
                   value={fmtNumber(live.threeLines.line3Count, lang)} tone="accent" />
            </div>
            <div style={{ marginTop: 8, fontSize: 12, color: 'var(--text-muted)' }}>
              {t('Board escalations:', 'إحالات للمجلس:')}{' '}
              <span style={{ color: live.threeLines.boardEscalations > 0
                                ? 'var(--warning)' : 'var(--text-muted)' }}>
                {fmtNumber(live.threeLines.boardEscalations, lang)}
              </span>
            </div>
          </Card>
        </div>
      )}

      {live && (
        <Card padded>
          <div style={{ fontSize: 12, color: 'var(--text-muted)' }}>
            {t('Computed at:', 'حُسبت في:')}{' '}
            {new Date(live.computedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
            {' · '}
            <Link to="/audit-committee/snapshots">
              {t('past snapshots', 'اللقطات السابقة')}
            </Link>
          </div>
        </Card>
      )}

      {/* ── Generate snapshot modal ─────────────────────────────── */}
      {showGenerateModal && (
        <Modal onClose={() => setShowGenerateModal(false)}
               title={t('Generate snapshot for meeting', 'لقطة الاجتماع')}>
          <div style={{ display: 'grid', gap: 12 }}>
            <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
              <span>{t('Meeting date', 'تاريخ الاجتماع')}</span>
              <input type="date" value={meetingDate}
                     onChange={e => setMeetingDate(e.target.value)}
                     style={inputStyle} />
            </label>
            <label style={{ display: 'grid', gap: 4, fontSize: 13 }}>
              <span>{t('Meeting type', 'نوع الاجتماع')}</span>
              <select value={meetingType}
                      onChange={e => setMeetingType(e.target.value as MeetingType)}
                      style={inputStyle}>
                <option value="QUARTERLY">{t('Quarterly', 'ربع سنوي')}</option>
                <option value="ANNUAL">{t('Annual', 'سنوي')}</option>
                <option value="AD_HOC">{t('Ad-hoc', 'استثنائي')}</option>
                <option value="OFFCYCLE">{t('Off-cycle', 'خارج الجدول')}</option>
              </select>
            </label>
            {generateError && (
              <div style={{ color: 'var(--danger)', fontSize: 13 }}>{generateError}</div>
            )}
            {generateSuccess && (
              <div style={{ color: 'var(--success)', fontSize: 13 }}>
                {t('Snapshot generated:', 'تم إنشاء اللقطة:')}{' '}
                <code>{generateSuccess}</code>
              </div>
            )}
            <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end' }}>
              <button onClick={() => setShowGenerateModal(false)} disabled={generateBusy}
                      style={btnSecondaryStyle}>
                {t('Cancel', 'إلغاء')}
              </button>
              <button onClick={generate} disabled={generateBusy} style={btnPrimaryStyle}>
                {generateBusy ? t('Generating…', 'يُنشأ…')
                             : t('Generate', 'إنشاء')}
              </button>
            </div>
          </div>
        </Modal>
      )}
    </Shell>
  );
}

// ─── Internal helpers ───────────────────────────────────────────

function SectionTitle({ Icon, title, to, t }: {
  Icon: typeof BookOpen;
  title: string;
  to: string;
  t: (en: string, ar: string) => string;
}) {
  return (
    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  marginBottom: 8 }}>
      <h3 style={{ margin: 0, fontSize: 14, display: 'flex', alignItems: 'center', gap: 6 }}>
        <Icon size={16} color="var(--primary)" strokeWidth={2} />
        {title}
      </h3>
      <Link to={to} style={{ fontSize: 12, textDecoration: 'none', color: 'var(--primary)' }}
            className="no-print">
        {t('Drill down →', 'تفاصيل ←')}
      </Link>
    </div>
  );
}

function Modal({ title, children, onClose }: {
  title: string;
  children: React.ReactNode;
  onClose: () => void;
}) {
  return (
    <div onClick={onClose} role="presentation"
         style={{ position: 'fixed', inset: 0, background: 'rgba(0,0,0,0.4)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  zIndex: 1000 }}>
      <div onClick={e => e.stopPropagation()} role="dialog" aria-modal="true"
           style={{ background: 'var(--surface)', padding: 20, borderRadius: 8,
                    minWidth: 320, maxWidth: 500, boxShadow: '0 8px 32px rgba(0,0,0,0.2)' }}>
        <h3 style={{ marginTop: 0 }}>{title}</h3>
        {children}
      </div>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
  padding: '6px 10px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  fontSize: 14,
  background: 'var(--surface)',
  color: 'var(--text)',
};

const btnPrimaryStyle: React.CSSProperties = {
  padding: '6px 14px',
  border: '1px solid var(--primary)',
  borderRadius: 4,
  background: 'var(--primary)',
  color: 'white',
  fontSize: 13,
  cursor: 'pointer',
};

const btnSecondaryStyle: React.CSSProperties = {
  padding: '6px 14px',
  border: '1px solid var(--border)',
  borderRadius: 4,
  background: 'var(--surface)',
  color: 'var(--text)',
  fontSize: 13,
  cursor: 'pointer',
};

/** Print-ready A4 layout — hidden in normal view, applied on @media print. */
function PrintStyles() {
  return (
    <style>{`
      .ac-grid {
        display: grid;
        grid-template-columns: repeat(auto-fill, minmax(320px, 1fr));
        gap: 16px;
        margin-top: 16px;
      }
      @media print {
        /* A4 portrait. Hide chrome. */
        @page { size: A4 portrait; margin: 12mm; }
        .no-print, .sidebar, .topbar, nav { display: none !important; }
        .ac-grid {
          grid-template-columns: 1fr 1fr;
          gap: 8px;
        }
        .ac-grid > * {
          break-inside: avoid;
        }
        .card { box-shadow: none !important; border: 1px solid #ccc !important; }
        .kpi-grid-sm > * { font-size: 11px !important; }
        body { font-size: 12px; }
      }
    `}</style>
  );
}
