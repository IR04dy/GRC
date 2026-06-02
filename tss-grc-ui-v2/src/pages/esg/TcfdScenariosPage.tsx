import { useEffect, useMemo, useState } from 'react';
import {
  CloudLightning, Plus, ChevronRight, ChevronLeft, CheckCircle2,
  Archive, FileSearch, Globe2, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  listScenarios,
  createScenario,
  transitionScenario,
  addSectorImpact,
  getProbabilityWeightedExpectedLoss,
  SCENARIO_TYPE_LABELS, FRAMEWORK_LABELS, SCENARIO_STAGE_LABELS,
  scenarioStageTone, scenarioTypeTone,
  fmtCelsius, fmtSarMillions,
  type TcfdScenario, type TcfdScenarioRequest,
  type ScenarioType, type ReferenceFramework,
  type ScenarioLifecycleStage,
  type ProbabilityWeightedExpectedLoss,
} from '../../lib/climate';

/**
 * Sprint 11 — TCFD Climate Scenarios register.
 *
 * <p>Board-pack landing for the CFO + Sustainability Officer. Shows the
 * three (or more) NGFS-aligned scenarios applied to the loan portfolio
 * with their probability-weighted expected loss blend.
 *
 * <p>4-step wizard: basics → assumptions → sector impacts (post-create)
 * → review. Each scenario starts DRAFT and moves through IN_REVIEW →
 * BOARD_APPROVED via the in-row transition button.
 *
 * <p>Saudi-native title throughout: "تحليل سيناريوهات المناخ TCFD".
 */
export function TcfdScenariosPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [items, setItems] = useState<TcfdScenario[]>([]);
  const [loading, setLoading] = useState(false);
  const [weighted, setWeighted] = useState<ProbabilityWeightedExpectedLoss | null>(null);

  // Wizard state (when creating a new scenario).
  const [wizardOpen, setWizardOpen] = useState(false);
  const [wizardStep, setWizardStep] = useState<1 | 2 | 3 | 4>(1);
  const [draft, setDraft] = useState<TcfdScenarioRequest>({
    titleEn: '', titleAr: '',
    scenarioType: 'ORDERLY', referenceFramework: 'NGFS',
    timeHorizonYears: 30, globalWarmingCelsius: 1.5,
    probabilityWeight: 0.5,
  });

  async function reload() {
    setLoading(true);
    try {
      const [list, w] = await Promise.all([
        listScenarios(),
        getProbabilityWeightedExpectedLoss(),
      ]);
      setItems(Array.isArray(list) ? list : []);
      setWeighted(w);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل السيناريوهات' : 'Failed to load scenarios'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const approvedCount = useMemo(
    () => items.filter(s => s.lifecycleStage === 'BOARD_APPROVED').length,
    [items],
  );
  const draftCount = useMemo(
    () => items.filter(s => s.lifecycleStage === 'DRAFT' || s.lifecycleStage === 'IN_REVIEW').length,
    [items],
  );

  async function transition(item: TcfdScenario, to: ScenarioLifecycleStage) {
    try {
      await transitionScenario(item.externalId, to);
      toast.success(isAr ? `الحالة الآن: ${tlabel(SCENARIO_STAGE_LABELS, to, true)}` : `Now: ${tlabel(SCENARIO_STAGE_LABELS, to, false)}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  function nextStep() { setWizardStep((s) => (s < 4 ? ((s + 1) as 1|2|3|4) : s)); }
  function prevStep() { setWizardStep((s) => (s > 1 ? ((s - 1) as 1|2|3|4) : s)); }
  function resetWizard() {
    setWizardOpen(false);
    setWizardStep(1);
    setDraft({
      titleEn: '', titleAr: '',
      scenarioType: 'ORDERLY', referenceFramework: 'NGFS',
      timeHorizonYears: 30, globalWarmingCelsius: 1.5,
      probabilityWeight: 0.5,
    });
  }

  async function submitWizard() {
    try {
      const created = await createScenario(draft);
      toast.success(isAr ? 'تم إنشاء السيناريو' : 'Scenario created');
      resetWizard();
      // Offer to add sector impacts inline.
      const addSector = await dialog.confirm({
        title: isAr ? 'إضافة تأثير قطاعي؟' : 'Add a sector impact?',
        body: isAr
          ? 'أضف صفّاً قطاعياً واحداً الآن — يمكنك المتابعة لاحقاً من صفحة السيناريو.'
          : 'Add one sector-impact row now — you can keep adding from the scenario page.',
      });
      if (addSector) {
        const sector = await dialog.prompt({
          title: isAr ? 'رمز القطاع (GICS)' : 'Sector code (GICS)',
          placeholder: 'ENERGY / REAL_ESTATE / UTILITIES / …',
          required: true,
        });
        const sectorName = await dialog.prompt({
          title: isAr ? 'اسم القطاع' : 'Sector name',
          required: true,
        });
        const exposure = await dialog.prompt({
          title: isAr ? 'التعرّض (مليون ر.س)' : 'Exposure (M SAR)',
          required: true,
        });
        if (sector && sectorName && exposure) {
          await addSectorImpact(created.externalId, {
            sectorCode: sector,
            sectorNameEn: sectorName,
            sectorNameAr: sectorName,
            exposureSarMillions: Number(exposure),
            transitionRiskScore: 50,
            physicalRiskScore: 40,
            expectedPdUpliftBps: 50,
          });
          toast.success(isAr ? 'تمت إضافة التأثير القطاعي' : 'Sector impact added');
        }
      }
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تحليل سيناريوهات المناخ TCFD' : 'TCFD Climate Scenarios'}
        sub={isAr
          ? 'سيناريوهات NGFS منظَّمة / غير منظَّمة / احتباس حراري على محفظة القروض — يُطلَب لإفصاح IFRS S2 وخطّة المخاطر المناخية لدى البنك المركزي.'
          : 'NGFS orderly / disorderly / hot-house scenarios applied to the loan portfolio — required for IFRS S2 disclosure and the SAMA climate-risk supervisory plan.'}
      />

      <div className="kpi-grid-sm" style={{ marginBottom: 'var(--sp-6)' }}>
        <Kpi
          Icon={CloudLightning}
          label={isAr ? 'إجمالي السيناريوهات' : 'Total scenarios'}
          value={String(items.length)}
        />
        <Kpi
          Icon={CheckCircle2}
          label={isAr ? 'مُعتمَد من المجلس' : 'Board approved'}
          value={String(approvedCount)}
          tone="success"
        />
        <Kpi
          Icon={FileSearch}
          label={isAr ? 'في الإعداد / المراجعة' : 'Draft / In review'}
          value={String(draftCount)}
          tone="warning"
        />
        <Kpi
          Icon={Globe2}
          label={isAr ? 'الخسارة المتوقّعة المرجَّحة' : 'Probability-weighted EL'}
          value={fmtSarMillions(weighted?.weightedExpectedLossSarMillions, isAr)}
          tone={weighted?.warning ? 'warning' : 'info'}
        />
      </div>

      {weighted?.warning && (
        <Card>
          <div className="cluster" style={{ alignItems: 'center', gap: 'var(--sp-4)' }}>
            <AlertTriangle size={20} />
            <span>{weighted.warning}</span>
          </div>
        </Card>
      )}

      <Card>
        <div className="cluster" style={{ justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 className="card-title">
            {isAr ? 'السيناريوهات المسجَّلة' : 'Registered scenarios'}
          </h3>
          <Button Icon={Plus} variant="primary" onClick={() => setWizardOpen(true)}>
            {isAr ? 'سيناريو جديد' : 'New scenario'}
          </Button>
        </div>

        {loading && <p>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</p>}
        {!loading && items.length === 0 && (
          <InlineEmpty
            text={isAr
              ? 'لا توجد سيناريوهات بعد. ابدأ بسيناريو NGFS منظَّم (1.5°م).'
              : 'No scenarios yet. Start with an NGFS orderly (1.5°C) scenario.'}
          />
        )}
        {!loading && items.length > 0 && (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse' }}>
              <thead>
                <tr>
                  <th style={th}>{isAr ? 'الكود' : 'Code'}</th>
                  <th style={th}>{isAr ? 'العنوان' : 'Title'}</th>
                  <th style={th}>{isAr ? 'النوع' : 'Type'}</th>
                  <th style={th}>{isAr ? 'الإطار المرجعي' : 'Framework'}</th>
                  <th style={th}>{isAr ? 'الاحترار' : 'Warming'}</th>
                  <th style={th}>{isAr ? 'وزن الاحتمال' : 'Prob. weight'}</th>
                  <th style={th}>{isAr ? 'الخسارة المتوقّعة' : 'Expected loss'}</th>
                  <th style={th}>{isAr ? 'الحالة' : 'Stage'}</th>
                  <th style={th}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {items.map(s => (
                  <tr key={s.externalId}>
                    <td style={td}><code>{s.scenarioCode}</code></td>
                    <td style={td}>{isAr ? (s.titleAr || s.titleEn) : s.titleEn}</td>
                    <td style={td}>
                      <Badge tone={scenarioTypeTone(s.scenarioType)}>
                        {tlabel(SCENARIO_TYPE_LABELS, s.scenarioType, isAr)}
                      </Badge>
                    </td>
                    <td style={td}>{tlabel(FRAMEWORK_LABELS, s.referenceFramework, isAr)}</td>
                    <td style={td}>{fmtCelsius(s.globalWarmingCelsius)}</td>
                    <td style={td}>{s.probabilityWeight}</td>
                    <td style={td}>{fmtSarMillions(s.expectedLossSarMillions, isAr)}</td>
                    <td style={td}>
                      <Badge tone={scenarioStageTone(s.lifecycleStage)}>
                        {tlabel(SCENARIO_STAGE_LABELS, s.lifecycleStage, isAr)}
                      </Badge>
                    </td>
                    <td style={td}>
                      {s.lifecycleStage === 'DRAFT' && (
                        <Button variant="secondary" onClick={() => transition(s, 'IN_REVIEW')}>
                          {isAr ? 'إرسال للمراجعة' : 'Submit for review'}
                        </Button>
                      )}
                      {s.lifecycleStage === 'IN_REVIEW' && (
                        <Button variant="primary" Icon={CheckCircle2}
                                onClick={() => transition(s, 'BOARD_APPROVED')}>
                          {isAr ? 'اعتماد المجلس' : 'Board approve'}
                        </Button>
                      )}
                      {s.lifecycleStage === 'BOARD_APPROVED' && (
                        <Button variant="secondary" Icon={Archive}
                                onClick={() => transition(s, 'ARCHIVED')}>
                          {isAr ? 'أرشفة' : 'Archive'}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      {wizardOpen && (
        <Card>
          <h3 className="card-title">
            {isAr
              ? `سيناريو جديد — الخطوة ${wizardStep} من 4`
              : `New scenario — Step ${wizardStep} of 4`}
          </h3>
          {wizardStep === 1 && (
            <div className="form-stack">
              <label>{isAr ? 'العنوان (إنجليزي)' : 'Title (English)'}
                <input value={draft.titleEn}
                       onChange={e => setDraft({...draft, titleEn: e.target.value})} />
              </label>
              <label>{isAr ? 'العنوان (عربي)' : 'Title (Arabic)'}
                <input value={draft.titleAr || ''} dir="rtl"
                       onChange={e => setDraft({...draft, titleAr: e.target.value})} />
              </label>
              <label>{isAr ? 'نوع السيناريو' : 'Scenario type'}
                <select value={draft.scenarioType}
                        onChange={e => setDraft({...draft, scenarioType: e.target.value as ScenarioType})}>
                  {(Object.keys(SCENARIO_TYPE_LABELS) as ScenarioType[]).map(t => (
                    <option key={t} value={t}>
                      {tlabel(SCENARIO_TYPE_LABELS, t, isAr)}
                    </option>
                  ))}
                </select>
              </label>
              <label>{isAr ? 'الإطار المرجعي' : 'Reference framework'}
                <select value={draft.referenceFramework}
                        onChange={e => setDraft({...draft, referenceFramework: e.target.value as ReferenceFramework})}>
                  {(Object.keys(FRAMEWORK_LABELS) as ReferenceFramework[]).map(t => (
                    <option key={t} value={t}>
                      {tlabel(FRAMEWORK_LABELS, t, isAr)}
                    </option>
                  ))}
                </select>
              </label>
            </div>
          )}
          {wizardStep === 2 && (
            <div className="form-stack">
              <label>{isAr ? 'المدى الزمني (سنوات)' : 'Time horizon (years)'}
                <input type="number" value={draft.timeHorizonYears}
                       onChange={e => setDraft({...draft, timeHorizonYears: Number(e.target.value)})} />
              </label>
              <label>{isAr ? 'الاحترار العالمي (°م)' : 'Global warming (°C)'}
                <input type="number" step="0.1" value={draft.globalWarmingCelsius}
                       onChange={e => setDraft({...draft, globalWarmingCelsius: Number(e.target.value)})} />
              </label>
              <label>{isAr ? 'الافتراضات (JSON)' : 'Assumptions (JSON)'}
                <textarea rows={4} value={draft.assumptionsJson || ''}
                          onChange={e => setDraft({...draft, assumptionsJson: e.target.value})}
                          placeholder='{"carbon_price_2030_usd_per_tco2e": 200}' />
              </label>
            </div>
          )}
          {wizardStep === 3 && (
            <div className="form-stack">
              <p>{isAr
                ? 'سيتم إنشاء السيناريو بدون تأثيرات قطاعية؛ يمكنك إضافتها مباشرة بعد الحفظ.'
                : 'The scenario will be created without sector impacts; you can add them right after saving.'}</p>
              <label>{isAr ? 'المحفظة المعرَّضة (مليار ر.س)' : 'Portfolio at risk (B SAR)'}
                <input type="number" step="0.1" value={String(draft.portfolioAtRiskSarBillions ?? '')}
                       onChange={e => setDraft({...draft, portfolioAtRiskSarBillions: Number(e.target.value)})} />
              </label>
              <label>{isAr ? 'الخسارة المتوقّعة (مليون ر.س)' : 'Expected loss (M SAR)'}
                <input type="number" step="0.1" value={String(draft.expectedLossSarMillions ?? '')}
                       onChange={e => setDraft({...draft, expectedLossSarMillions: Number(e.target.value)})} />
              </label>
              <label>{isAr ? 'وزن الاحتمال (0-1)' : 'Probability weight (0-1)'}
                <input type="number" step="0.05" min="0" max="1" value={String(draft.probabilityWeight ?? '')}
                       onChange={e => setDraft({...draft, probabilityWeight: Number(e.target.value)})} />
              </label>
            </div>
          )}
          {wizardStep === 4 && (
            <div className="form-stack">
              <p><strong>{isAr ? 'مراجعة:' : 'Review:'}</strong></p>
              <ul>
                <li>{isAr ? 'العنوان:' : 'Title:'} {draft.titleEn}{draft.titleAr ? ` / ${draft.titleAr}` : ''}</li>
                <li>{isAr ? 'النوع:' : 'Type:'} {tlabel(SCENARIO_TYPE_LABELS, draft.scenarioType, false)} — {tlabel(FRAMEWORK_LABELS, draft.referenceFramework, false)}</li>
                <li>{isAr ? 'الاحترار:' : 'Warming:'} {fmtCelsius(draft.globalWarmingCelsius)} / {draft.timeHorizonYears}y</li>
                <li>{isAr ? 'وزن الاحتمال:' : 'Probability weight:'} {String(draft.probabilityWeight ?? 0)}</li>
              </ul>
            </div>
          )}
          <div className="cluster" style={{ justifyContent: 'space-between', marginTop: 'var(--sp-6)' }}>
            <Button variant="secondary" Icon={ChevronLeft} onClick={prevStep} disabled={wizardStep === 1}>
              {isAr ? 'السابق' : 'Back'}
            </Button>
            <div className="cluster" style={{ gap: 'var(--sp-4)' }}>
              <Button variant="secondary" onClick={resetWizard}>
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              {wizardStep < 4 ? (
                <Button variant="primary" Icon={ChevronRight} onClick={nextStep}>
                  {isAr ? 'التالي' : 'Next'}
                </Button>
              ) : (
                <Button variant="primary" Icon={CheckCircle2} onClick={submitWizard}>
                  {isAr ? 'إنشاء السيناريو' : 'Create scenario'}
                </Button>
              )}
            </div>
          </div>
        </Card>
      )}
    </Shell>
  );
}

const th: React.CSSProperties = {
  textAlign: 'start',
  padding: '8px 12px',
  borderBottom: '1px solid var(--border)',
  fontWeight: 600,
};
const td: React.CSSProperties = {
  padding: '8px 12px',
  borderBottom: '1px solid var(--border)',
  verticalAlign: 'top',
};
