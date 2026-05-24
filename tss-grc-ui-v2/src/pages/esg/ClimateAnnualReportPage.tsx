import { useEffect, useState } from 'react';
import {
  FileSearch, Download, CloudLightning, Factory, Target as TargetIcon,
  AlertTriangle, Calendar,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getAnnualReport,
  SCENARIO_TYPE_LABELS, FRAMEWORK_LABELS,
  ASSET_CLASS_LABELS, ALIGNMENT_LABELS,
  scenarioTypeTone,
  fmtTco2e, fmtSarMillions, fmtPercent, fmtCelsius,
  type AnnualClimateReport,
} from '../../lib/climate';

/**
 * Sprint 11 — Annual IFRS S2 / TCFD disclosure pack viewer.
 *
 * <p>One-page read-only assembly of every climate-related artefact the
 * external auditor will ask for:
 * <ul>
 *   <li>Board-approved scenarios + per-scenario sector impacts</li>
 *   <li>Probability-weighted expected loss blend</li>
 *   <li>Financed-emissions total + per-asset-class records</li>
 *   <li>Target progress (vs SBTi / NZBA)</li>
 * </ul>
 *
 * <p>Saudi-native title throughout: "التقرير المناخي السنوي".
 *
 * <p>Export-to-PDF stub: the OpenPDF infrastructure exists for other
 * annexes but not yet wired for this view — emits a TODO toast for now.
 */
export function ClimateAnnualReportPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [year, setYear] = useState<number>(2026);
  const [report, setReport] = useState<AnnualClimateReport | null>(null);
  const [loading, setLoading] = useState(false);

  async function loadReport(y: number) {
    setLoading(true);
    try {
      const r = await getAnnualReport(y);
      setReport(r);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل التقرير' : 'Failed to load report'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void loadReport(year); }, [year]); // eslint-disable-line react-hooks/exhaustive-deps

  function exportToPdf() {
    // TODO Sprint 12 — wire to OpenPDF (Arabic RTL infrastructure already
    // used for board-pack and sustainability annex). Stubbed today.
    toast.info(isAr
      ? 'تصدير PDF — قيد التنفيذ (Sprint 12). يُعتمَد بنية OpenPDF القائمة.'
      : 'PDF export — Sprint 12 (will reuse the existing OpenPDF infrastructure).');
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'التقرير المناخي السنوي' : 'Annual Climate Report (IFRS S2 / TCFD)'}
        sub={isAr
          ? 'حزمة الإفصاح السنوي وفق IFRS S2 وتوصيات TCFD — السيناريوهات، الانبعاثات المُموَّلة، الأهداف.'
          : 'Annual IFRS S2 + TCFD disclosure pack — scenarios, financed emissions, targets.'}
        actions={
          <div className="cluster" style={{ gap: 'var(--space-sm)' }}>
            <label className="cluster" style={{ alignItems: 'center', gap: 4 }}>
              <Calendar size={16} />
              <select value={year} onChange={e => setYear(Number(e.target.value))}>
                {[2025, 2026, 2027, 2028].map(y => <option key={y} value={y}>{y}</option>)}
              </select>
            </label>
            <Button variant="primary" Icon={Download} onClick={exportToPdf}>
              {isAr ? 'تصدير PDF' : 'Export PDF'}
            </Button>
          </div>
        }
      />

      {loading && <Card><p>{isAr ? 'جارٍ تحميل التقرير…' : 'Loading report…'}</p></Card>}
      {!loading && !report && (
        <Card>
          <InlineEmpty text={isAr ? 'لا توجد بيانات تقرير' : 'No report data'} />
        </Card>
      )}
      {!loading && report && (
        <>
          <div className="kpi-grid-sm" style={{ marginBottom: 'var(--space-md)' }}>
            <Kpi
              Icon={CloudLightning}
              label={isAr ? 'السيناريوهات المُعتمَدة' : 'Approved scenarios'}
              value={String(report.scenarios.length)}
              tone="success"
            />
            <Kpi
              Icon={Factory}
              label={isAr ? 'إجمالي الانبعاثات' : 'Total emissions'}
              value={fmtTco2e(report.financedEmissionsTotal.totalFinancedEmissionsTco2e)}
              tone="info"
            />
            <Kpi
              Icon={AlertTriangle}
              label={isAr ? 'الخسارة المتوقّعة المرجَّحة' : 'Weighted EL'}
              value={fmtSarMillions(report.weightedExpectedLoss.weightedExpectedLossSarMillions, isAr)}
              tone={report.weightedExpectedLoss.warning ? 'warning' : 'info'}
            />
            <Kpi
              Icon={TargetIcon}
              label={isAr ? 'الأهداف المسجَّلة' : 'Targets'}
              value={String(report.targetProgress.length)}
              tone="primary"
            />
          </div>

          <Card>
            <h3 className="card-title">
              <CloudLightning size={18} style={{ verticalAlign: 'middle' }} />
              {' '}
              {isAr ? 'السيناريوهات المعتمدة من المجلس' : 'Board-approved scenarios'}
            </h3>
            {report.scenarios.length === 0 ? (
              <InlineEmpty text={isAr ? 'لا توجد سيناريوهات معتمَدة' : 'No board-approved scenarios'} />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={th}>{isAr ? 'الكود' : 'Code'}</th>
                      <th style={th}>{isAr ? 'النوع' : 'Type'}</th>
                      <th style={th}>{isAr ? 'الإطار' : 'Framework'}</th>
                      <th style={th}>{isAr ? 'الاحترار' : 'Warming'}</th>
                      <th style={th}>{isAr ? 'الأفق' : 'Horizon'}</th>
                      <th style={th}>{isAr ? 'الوزن' : 'Weight'}</th>
                      <th style={th}>{isAr ? 'الخسارة المتوقّعة' : 'Expected loss'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.scenarios.map(s => (
                      <tr key={s.externalId}>
                        <td style={td}><code>{s.scenarioCode}</code></td>
                        <td style={td}>
                          <Badge tone={scenarioTypeTone(s.scenarioType)}>
                            {isAr ? SCENARIO_TYPE_LABELS[s.scenarioType].ar : SCENARIO_TYPE_LABELS[s.scenarioType].en}
                          </Badge>
                        </td>
                        <td style={td}>{isAr ? FRAMEWORK_LABELS[s.referenceFramework].ar : FRAMEWORK_LABELS[s.referenceFramework].en}</td>
                        <td style={td}>{fmtCelsius(s.globalWarmingCelsius)}</td>
                        <td style={td}>{s.timeHorizonYears}{isAr ? ' سنة' : 'y'}</td>
                        <td style={td}>{s.probabilityWeight}</td>
                        <td style={td}>{fmtSarMillions(s.expectedLossSarMillions, isAr)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
            {report.weightedExpectedLoss.warning && (
              <p style={{ color: 'var(--color-warning, #d97706)', marginTop: 'var(--space-sm)' }}>
                <AlertTriangle size={16} style={{ verticalAlign: 'middle' }} />
                {' '}{report.weightedExpectedLoss.warning}
              </p>
            )}
          </Card>

          <Card>
            <h3 className="card-title">
              {isAr ? 'تفصيل التأثير القطاعي' : 'Sector-impact breakdown'}
            </h3>
            {report.sectorImpacts.length === 0 ? (
              <InlineEmpty text={isAr ? 'لا توجد تأثيرات قطاعية' : 'No sector impacts'} />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={th}>{isAr ? 'القطاع' : 'Sector'}</th>
                      <th style={th}>{isAr ? 'التعرّض' : 'Exposure'}</th>
                      <th style={th}>{isAr ? 'مخاطر الانتقال' : 'Transition risk'}</th>
                      <th style={th}>{isAr ? 'المخاطر المادّية' : 'Physical risk'}</th>
                      <th style={th}>{isAr ? 'ارتفاع PD' : 'PD uplift'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.sectorImpacts.map(si => (
                      <tr key={si.externalId}>
                        <td style={td}>{isAr ? (si.sectorNameAr || si.sectorNameEn) : si.sectorNameEn}</td>
                        <td style={td}>{fmtSarMillions(si.exposureSarMillions, isAr)}</td>
                        <td style={td}>{Number(si.transitionRiskScore).toFixed(0)}/100</td>
                        <td style={td}>{Number(si.physicalRiskScore).toFixed(0)}/100</td>
                        <td style={td}>+{si.expectedPdUpliftBps} bps</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <Card>
            <h3 className="card-title">
              <Factory size={18} style={{ verticalAlign: 'middle' }} />
              {' '}
              {isAr ? 'الانبعاثات المُموَّلة لكل فئة أصل' : 'Financed emissions by asset class'}
            </h3>
            {report.emissionsRecords.length === 0 ? (
              <InlineEmpty text={isAr ? 'لا توجد سجلّات' : 'No records'} />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={th}>{isAr ? 'فئة الأصل' : 'Asset class'}</th>
                      <th style={th}>{isAr ? 'القائم' : 'Outstanding'}</th>
                      <th style={th}>{isAr ? 'جودة PCAF' : 'PCAF quality'}</th>
                      <th style={th}>{isAr ? 'الإجمالي' : 'Total'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.emissionsRecords.map(r => (
                      <tr key={r.externalId}>
                        <td style={td}>{isAr ? ASSET_CLASS_LABELS[r.assetClass].ar : ASSET_CLASS_LABELS[r.assetClass].en}</td>
                        <td style={td}>{fmtSarMillions(r.outstandingAmountSarMillions, isAr)}</td>
                        <td style={td}><Badge>PCAF {r.dataQualityScore}</Badge></td>
                        <td style={td}>{fmtTco2e(r.totalFinancedEmissionsTco2e)}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <Card>
            <h3 className="card-title">
              <TargetIcon size={18} style={{ verticalAlign: 'middle' }} />
              {' '}
              {isAr ? 'تقدّم الأهداف' : 'Target progress'}
            </h3>
            {report.targetProgress.length === 0 ? (
              <InlineEmpty text={isAr ? 'لا توجد أهداف' : 'No targets'} />
            ) : (
              <div style={{ overflowX: 'auto' }}>
                <table style={{ width: '100%', borderCollapse: 'collapse' }}>
                  <thead>
                    <tr>
                      <th style={th}>{isAr ? 'العنوان' : 'Title'}</th>
                      <th style={th}>{isAr ? 'الإطار' : 'Framework'}</th>
                      <th style={th}>{isAr ? 'المُلتزَم' : 'Committed'}</th>
                      <th style={th}>{isAr ? 'المُتحقَّق' : 'Achieved'}</th>
                      <th style={th}>{isAr ? 'النسبة' : 'Ratio'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {report.targetProgress.map(p => (
                      <tr key={p.targetExternalId}>
                        <td style={td}>{isAr ? (p.titleAr || p.titleEn) : p.titleEn}</td>
                        <td style={td}>
                          <Badge tone={p.sbtiAligned ? 'success' : 'info'}>
                            {isAr ? ALIGNMENT_LABELS[p.alignmentFramework].ar : ALIGNMENT_LABELS[p.alignmentFramework].en}
                          </Badge>
                        </td>
                        <td style={td}>{fmtPercent(p.reductionPercent)}</td>
                        <td style={td}>{fmtPercent(p.achievedReductionPercent)}</td>
                        <td style={td}>
                          <Badge tone={Number(p.achievementRatio) >= 1 ? 'success'
                                       : Number(p.achievementRatio) >= 0.5 ? 'warning' : 'danger'}>
                            {Number(p.achievementRatio).toFixed(2)}×
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </Card>

          <Card>
            <small>
              <FileSearch size={14} style={{ verticalAlign: 'middle' }} />
              {' '}
              {isAr ? 'تم التوليد في:' : 'Generated at:'} {report.generatedAtIso}
            </small>
          </Card>
        </>
      )}
    </Shell>
  );
}

const th: React.CSSProperties = {
  textAlign: 'start',
  padding: '8px 12px',
  borderBottom: '1px solid var(--color-border)',
  fontWeight: 600,
};
const td: React.CSSProperties = {
  padding: '8px 12px',
  borderBottom: '1px solid var(--color-border)',
  verticalAlign: 'top',
};
