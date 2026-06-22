import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Library, Calendar, ClipboardList } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  listPlanTemplates, clonePlanTemplate,
  listExerciseScenarios,
  listPeakSeasons, upcomingPeakSeasons,
  type BcpPlanTemplate, type ExerciseScenario, type PeakSeason,
} from '../../lib/bcmLibrary';

/**
 * BCM Library — audit Tier-3 #15 + #16 + Tier-4 #28.
 *
 * <p>Three tabs over read-only system catalogs:
 * <ul>
 *   <li><b>Plan templates</b> — 8 canonical BCP/DRP scenarios. Each row
 *       has a "Clone to new plan" button that creates a DRAFT plan in
 *       the current tenant pre-populated with the template's sections.</li>
 *   <li><b>Exercise scenarios</b> — 10 ready-to-run exercises with timed
 *       injects + success criteria.</li>
 *   <li><b>KSA peak seasons</b> — Hajj, Umrah, Ramadan, Eid, National
 *       Day — with a count of "upcoming in next 120 days" at the top.</li>
 * </ul>
 */
type TabId = 'plan_templates' | 'exercises' | 'peaks';

export function BcmLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();

  const [tab, setTab]                 = useState<TabId>('plan_templates');
  const [templates, setTemplates]     = useState<BcpPlanTemplate[]>([]);
  const [scenarios, setScenarios]     = useState<ExerciseScenario[]>([]);
  const [peaks, setPeaks]             = useState<PeakSeason[]>([]);
  const [upcoming, setUpcoming]       = useState<PeakSeason[]>([]);
  const [loading, setLoading]         = useState(false);

  useEffect(() => {
    void (async () => {
      setLoading(true);
      // Per-call .catch() so a partial backend failure (e.g. missing-table
      // 503 on a fresh dev DB) doesn't blank the whole library. Each panel
      // degrades independently to its empty default.
      const [t, s, p, u] = await Promise.all([
        listPlanTemplates().catch(() => []),
        listExerciseScenarios().catch(() => []),
        listPeakSeasons().catch(() => []),
        upcomingPeakSeasons(120).catch(() => []),
      ]);
      setTemplates(Array.isArray(t) ? t : []);
      setScenarios(Array.isArray(s) ? s : []);
      setPeaks(Array.isArray(p) ? p : []);
      setUpcoming(Array.isArray(u) ? u : []);
      setLoading(false);
    })();
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  async function cloneTemplate(t: BcpPlanTemplate) {
    try {
      const created = await clonePlanTemplate(t.templateCode);
      toast.success(isAr
        ? `أُنشئ ${created.planCode} من القالب`
        : `${created.planCode} created from template`);
      // Navigate to the plan builder with the freshly-created plan opened
      navigate(`/bcm/plans?openPlan=${created.id}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الاستنساخ' : 'Clone failed'));
    }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'plan_templates', label: isAr ? `قوالب الخطط (${templates.length})` : `Plan templates (${templates.length})` },
    { id: 'exercises',      label: isAr ? `سيناريوهات التمارين (${scenarios.length})` : `Exercise scenarios (${scenarios.length})` },
    { id: 'peaks',          label: isAr ? `مواسم الذروة (${peaks.length})` : `Peak seasons (${peaks.length})` },
  ];

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'مكتبة BCM' : 'BCM Library'}
        sub={isAr
          ? 'قوالب جاهزة لخطط الاستمرارية والتعافي · سيناريوهات تمارين بحقن وأهداف · تقويم الذروة السعودي.'
          : 'Ready-to-use BCP/DRP templates · exercise scenarios with injects + success criteria · KSA peak-season calendar.'}
        eyebrow={isAr ? 'BCM' : 'BCM'}
      />

      {/* Upcoming-peak strip ALWAYS visible — it's the most actionable
          piece of the library so we surface it above the tabs. */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(180px,1fr))', gap: 12, marginBottom: 14 }}>
        <Kpi Icon={Library}       label={isAr ? 'قوالب خطط'       : 'Plan templates'}      value={templates.length} tone="info" to="/bcm/plans" />
        <Kpi Icon={ClipboardList} label={isAr ? 'سيناريوهات'      : 'Exercise scenarios'}  value={scenarios.length} tone="info" to="/bcm/scenarios" />
        <Kpi Icon={Calendar}      label={isAr ? 'مواسم ذروة'      : 'KSA peak seasons'}    value={peaks.length}     tone="info" onClick={() => setTab('peaks')} active={tab === 'peaks'} />
        <Kpi Icon={Calendar}      label={isAr ? 'قادمة (120 يوم)' : 'Upcoming (120d)'}     value={upcoming.length}  tone={upcoming.length > 0 ? 'warning' : 'success'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      <div style={{ marginTop: 12 }}>
        {loading && <div className="text-muted" style={{ fontSize: 11, marginBottom: 6 }}>
          {isAr ? 'جارٍ التحميل…' : 'Loading…'}
        </div>}

        {tab === 'plan_templates' && (
          <Card title={isAr ? 'قوالب الخطط' : 'Plan templates'}>
            {templates.length === 0
              ? <InlineEmpty>{isAr ? 'لا قوالب' : 'No templates'}</InlineEmpty>
              : <div style={{ overflowX: 'auto' }}>
                  <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                    <thead><tr>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الكود' : 'Code'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الاسم' : 'Name'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'النوع' : 'Type'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الفئة' : 'Category'}</th>
                      <th style={{ textAlign: 'start' }}>RTO/RPO/MTPD</th>
                      <th style={{ textAlign: 'end' }}>{isAr ? 'إجراء' : 'Action'}</th>
                    </tr></thead>
                    <tbody>
                      {templates.map(t => (
                        <tr key={t.id}>
                          <td className="mono">{t.templateCode}</td>
                          <td>
                            <div>{isAr && t.templateNameAr ? t.templateNameAr : t.templateNameEn}</div>
                            {t.descriptionEn && <div className="text-muted" style={{ fontSize: 11, marginTop: 2 }}>
                              {(isAr ? t.descriptionAr : t.descriptionEn)?.slice(0, 120) ?? ''}…
                            </div>}
                          </td>
                          <td><Badge tone={t.planType === 'BCP' ? 'info' : 'primary'}>{t.planType}</Badge></td>
                          <td><Badge tone="neutral">{t.scenarioCategory}</Badge></td>
                          <td className="mono" style={{ fontSize: 11 }}>
                            {t.defaultRtoHours ?? '—'}h / {t.defaultRpoHours ?? '—'}h / {t.defaultMtpdHours ?? '—'}h
                          </td>
                          <td style={{ textAlign: 'end' }}>
                            <Button variant="primary" onClick={() => void cloneTemplate(t)}>
                              {isAr ? 'استنساخ' : 'Clone to new plan'}
                            </Button>
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>}
          </Card>
        )}

        {tab === 'exercises' && (
          <Card title={isAr ? 'سيناريوهات التمارين' : 'Exercise scenarios'}>
            {scenarios.length === 0
              ? <InlineEmpty>{isAr ? 'لا سيناريوهات' : 'No scenarios'}</InlineEmpty>
              : <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(380px,1fr))', gap: 12 }}>
                  {scenarios.map(s => <ScenarioCard key={s.id} scenario={s} isAr={isAr} />)}
                </div>}
          </Card>
        )}

        {tab === 'peaks' && (
          <Card title={isAr ? 'مواسم الذروة في المملكة العربية السعودية' : 'KSA peak seasons'}>
            {peaks.length === 0
              ? <InlineEmpty>{isAr ? 'لا مواسم' : 'No peak seasons'}</InlineEmpty>
              : <div style={{ overflowX: 'auto' }}>
                  <table className="data-table" style={{ width: '100%', fontSize: 13 }}>
                    <thead><tr>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الكود' : 'Code'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الموسم' : 'Season'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'النوع' : 'Type'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'البداية' : 'Start'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'النهاية' : 'End'}</th>
                      <th style={{ textAlign: 'start' }}>{isAr ? 'الخدمات المتأثرة' : 'Affected services'}</th>
                    </tr></thead>
                    <tbody>
                      {peaks.map(p => (
                        <tr key={p.id}>
                          <td className="mono">{p.seasonCode}</td>
                          <td>{isAr ? p.seasonNameAr : p.seasonNameEn}</td>
                          <td><Badge tone="info">{p.seasonType}</Badge></td>
                          <td className="mono" style={{ fontSize: 11 }}>{p.gregorianStartDate}</td>
                          <td className="mono" style={{ fontSize: 11 }}>{p.gregorianEndDate}</td>
                          <td style={{ fontSize: 12 }}>{p.affectedServices ?? '—'}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>}
          </Card>
        )}
      </div>
    </Shell>
  );
}

// ─── Scenario card (used in the Exercises tab) ────────────────────

function ScenarioCard({ scenario: s, isAr }: { scenario: ExerciseScenario; isAr: boolean }) {
  // Parse injects + criteria for at-a-glance counts; gracefully fall back
  // to 0 if a template's JSON is malformed (shouldn't happen — seed is fixed).
  let injectCount = 0, criteriaCount = 0;
  try { injectCount = JSON.parse(s.injectsJson).length; } catch { /* noop */ }
  try { criteriaCount = JSON.parse(s.successCriteriaJson).length; } catch { /* noop */ }

  return (
    <div style={{ border: '1px solid var(--border)', borderRadius: 6, padding: 12 }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', marginBottom: 6 }}>
        <div className="mono" style={{ fontSize: 11 }}>{s.scenarioCode}</div>
        <Badge tone="info">{s.exerciseType}</Badge>
      </div>
      <div style={{ fontWeight: 600, fontSize: 14, marginBottom: 4 }}>
        {isAr && s.titleAr ? s.titleAr : s.titleEn}
      </div>
      <div className="text-muted" style={{ fontSize: 12, marginBottom: 8, lineHeight: 1.4 }}>
        {(isAr ? s.descriptionAr : s.descriptionEn)?.slice(0, 200) ?? '—'}…
      </div>
      <div style={{ display: 'flex', gap: 8, fontSize: 11 }}>
        <Badge tone="neutral">{s.durationMinutes} min</Badge>
        <Badge tone="neutral">{injectCount} {isAr ? 'حقنة' : 'injects'}</Badge>
        <Badge tone="neutral">{criteriaCount} {isAr ? 'معيار' : 'criteria'}</Badge>
        <Badge tone="neutral">{s.scenarioCategory}</Badge>
      </div>
    </div>
  );
}
