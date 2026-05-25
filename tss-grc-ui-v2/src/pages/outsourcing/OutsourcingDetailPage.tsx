import { useEffect, useMemo, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, ShieldCheck, AlertTriangle, LogOut, History, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getArrangement,
  listDueDiligence,
  listEvents,
  getExitPlan,
  recordReview,
  type OutsourcingArrangement,
  type DueDiligence,
  type MonitoringEvent,
  type ExitPlan,
  LIFECYCLE_LABELS,
  SERVICE_TYPE_LABELS,
  CRITICAL_FUNCTION_LABELS,
  EVENT_TYPE_LABELS,
  SEVERITY_LABELS,
  TEST_FREQUENCY_LABELS,
  lifecycleTone,
  severityTone,
  recommendationTone,
  fmtSar,
} from '../../lib/outsourcing';

type TabId = 'overview' | 'dd' | 'events' | 'exit' | 'activity';

export function OutsourcingDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const arrangementId = Number(id);
  const [arrangement, setArrangement] = useState<OutsourcingArrangement | null>(null);
  const [dd, setDd] = useState<DueDiligence[]>([]);
  const [events, setEvents] = useState<MonitoringEvent[]>([]);
  const [exitPlan, setExitPlan] = useState<ExitPlan | null>(null);
  const [tab, setTab] = useState<TabId>('overview');
  const [loading, setLoading] = useState(false);

  async function reload() {
    if (!Number.isFinite(arrangementId)) return;
    setLoading(true);
    try {
      const [a, ddList, evList] = await Promise.all([
        getArrangement(arrangementId),
        listDueDiligence(arrangementId).catch(() => []),
        listEvents(arrangementId).catch(() => []),
      ]);
      setArrangement(a);
      setDd(ddList);
      setEvents(evList);
      try {
        const ep = await getExitPlan(arrangementId);
        setExitPlan(ep);
      } catch { setExitPlan(null); }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل الترتيب' : 'Failed to load arrangement'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [arrangementId]); // eslint-disable-line react-hooks/exhaustive-deps

  async function onRecordReview() {
    try {
      const updated = await recordReview(arrangementId);
      setArrangement(updated);
      toast.success(isAr ? 'تمّ تسجيل المراجعة الدورية' : 'Periodic review recorded');
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تسجيل المراجعة' : 'Failed to record review'));
    }
  }

  const tabs: TabItem<TabId>[] = useMemo(() => [
    { id: 'overview', label: isAr ? 'نظرة عامة' : 'Overview' },
    { id: 'dd',       label: isAr ? `العناية الواجبة (${dd.length})` : `Due Diligence (${dd.length})` },
    { id: 'events',   label: isAr ? `المراقبة المستمرّة (${events.length})` : `Monitoring (${events.length})` },
    { id: 'exit',     label: isAr ? 'خطة الخروج' : 'Exit Plan' },
    { id: 'activity', label: isAr ? 'النشاط' : 'Activity' },
  ], [isAr, dd.length, events.length]);

  if (!Number.isFinite(arrangementId)) {
    return <Shell><div className="p-6">{isAr ? 'معرّف غير صحيح' : 'Invalid id'}</div></Shell>;
  }

  if (loading && !arrangement) {
    return <Shell><div className="p-6">{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Shell>;
  }

  if (!arrangement) {
    return (
      <Shell>
        <div className="p-6">
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/tprm/outsourcing')}>
            {isAr ? 'الرجوع للسجل' : 'Back to register'}
          </Button>
          <div className="mt-4">{isAr ? 'ترتيب غير موجود' : 'Arrangement not found'}</div>
        </div>
      </Shell>
    );
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={`${arrangement.arrangementCode}`}
        title={isAr ? arrangement.titleAr : arrangement.titleEn}
        sub={isAr ? arrangement.titleEn : arrangement.titleAr}
        actions={
          <div className="flex items-center gap-2">
            <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/tprm/outsourcing')}>
              {isAr ? 'الرجوع' : 'Back'}
            </Button>
            <Button variant="secondary" Icon={RefreshCw} onClick={onRecordReview}>
              {isAr ? 'تسجيل مراجعة' : 'Record review'}
            </Button>
          </div>
        }
      />

      {/* Header summary chips */}
      <Card>
        <div className="flex flex-wrap items-center gap-2">
          {arrangement.isMaterial
            ? <Badge tone="warning">{isAr ? 'إسناد جوهري' : 'Material'}</Badge>
            : <Badge tone="neutral">{isAr ? 'غير جوهري' : 'Non-material'}</Badge>}
          <Badge tone={lifecycleTone(arrangement.lifecycleStage)}>
            {isAr ? LIFECYCLE_LABELS[arrangement.lifecycleStage].ar : LIFECYCLE_LABELS[arrangement.lifecycleStage].en}
          </Badge>
          <Badge tone="info">
            {isAr ? SERVICE_TYPE_LABELS[arrangement.serviceType].ar : SERVICE_TYPE_LABELS[arrangement.serviceType].en}
          </Badge>
          {arrangement.criticalFunction !== 'NA' && (
            <Badge tone="warning">
              {isAr ? CRITICAL_FUNCTION_LABELS[arrangement.criticalFunction].ar : CRITICAL_FUNCTION_LABELS[arrangement.criticalFunction].en}
            </Badge>
          )}
          {arrangement.samaNonObjectionRequired && (
            arrangement.samaNonObjectionObtained
              ? <Badge tone="success">{isAr ? `عدم ممانعة ساما: ${arrangement.samaNonObjectionRef ?? '✓'}` : `SAMA non-objection: ${arrangement.samaNonObjectionRef ?? '✓'}`}</Badge>
              : <Badge tone="danger">{isAr ? 'عدم ممانعة ساما — مطلوب' : 'SAMA non-objection required'}</Badge>
          )}
        </div>
      </Card>

      <Tabs items={tabs} active={tab} onChange={(id) => setTab(id as TabId)} />

      {tab === 'overview' && (
        <div className="mt-4 grid gap-4 lg:grid-cols-2">
          <Card title={isAr ? 'العقد' : 'Contract'} Icon={FileText}>
            <DataPair label={isAr ? 'القيمة السنوية' : 'Annual value'} value={fmtSar(arrangement.contractValueSarAnnual)} />
            <DataPair label={isAr ? 'بداية العقد' : 'Contract start'} value={arrangement.contractStartDate ?? '—'} />
            <DataPair label={isAr ? 'نهاية العقد' : 'Contract end'} value={arrangement.contractEndDate ?? '—'} />
            <DataPair label={isAr ? 'مرجع العقد' : 'Contract ref'} value={arrangement.contractRef ?? '—'} />
          </Card>
          <Card title={isAr ? 'المعالجة' : 'Processing'} Icon={ShieldCheck}>
            <DataPair label={isAr ? 'بلد المعالجة' : 'Country of processing'} value={arrangement.countryOfProcessing} />
            <DataPair label={isAr ? 'المنطقة' : 'Region'} value={arrangement.regionOfProcessing ?? '—'} />
            <DataPair label={isAr ? 'توطين البيانات بالمملكة' : 'KSA data residency'} value={arrangement.dataResidencyKdc ? '✓' : '✗'} />
            <DataPair label={isAr ? 'بيانات شخصية' : 'Personal data processed'} value={arrangement.personalDataProcessed ? '✓' : '✗'} />
            <DataPair label={isAr ? 'اتفاقية معالجة البيانات (PDPL)' : 'PDPL DPA'} value={arrangement.pdplDpaSigned ? (arrangement.pdplDpaDate ?? '✓') : '✗'} />
            {arrangement.crossBorderSafeguardType && (
              <DataPair label={isAr ? 'ضمانة عبر الحدود' : 'Cross-border safeguard'} value={arrangement.crossBorderSafeguardType} />
            )}
          </Card>
          <div className="lg:col-span-2">
            <Card title={isAr ? 'الجوهرية' : 'Materiality'} Icon={AlertTriangle}>
              <DataPair label={isAr ? 'مُصنَّفة' : 'Classified'} value={arrangement.isMaterial ? (isAr ? 'جوهرية' : 'Material') : (isAr ? 'غير جوهرية' : 'Non-material')} />
              {arrangement.materialityBasisEn && (
                <DataPair label={isAr ? 'الأساس' : 'Basis'} value={isAr ? (arrangement.materialityBasisAr ?? '') : arrangement.materialityBasisEn} />
              )}
            </Card>
          </div>
        </div>
      )}

      {tab === 'dd' && (
        <div className="mt-4 space-y-3">
          {dd.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد سجلات عناية واجبة' : 'No due diligence records'} /></Card>}
          {dd.map(d => (
            <Card key={d.id}>
              <div className="flex items-start justify-between">
                <div>
                  <div className="text-sm font-semibold">
                    {isAr ? 'تاريخ التقييم:' : 'Assessment date:'} {d.assessmentDate}
                  </div>
                  <div className="text-xs text-muted-foreground">
                    {isAr ? 'المُقيِّم' : 'Assessor'} #{d.assessorUserId}
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge tone={recommendationTone(d.recommendation)}>{d.recommendation}</Badge>
                  <span className="text-2xl font-bold">{d.compositeScore.toFixed(2)}</span>
                </div>
              </div>
              <div className="mt-3 grid grid-cols-3 gap-2 sm:grid-cols-6 text-xs">
                <ScoreCell label={isAr ? 'المالية' : 'Financial'} score={d.financialHealthScore} />
                <ScoreCell label={isAr ? 'التشغيلية' : 'Operational'} score={d.operationalCapabilityScore} />
                <ScoreCell label={isAr ? 'السيبراني' : 'Cyber'} score={d.cybersecurityScore} />
                <ScoreCell label={isAr ? 'BCM' : 'BCM'} score={d.bcmScore} />
                <ScoreCell label={isAr ? 'PDPL' : 'PDPL'} score={d.pdplComplianceScore} />
                <ScoreCell label={isAr ? 'السمعة' : 'Reputation'} score={d.reputationScore} />
              </div>
              <div className="mt-3 text-sm text-muted-foreground">
                {isAr ? d.rationaleAr : d.rationaleEn}
              </div>
            </Card>
          ))}
        </div>
      )}

      {tab === 'events' && (
        <div className="mt-4 space-y-3">
          {events.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد أحداث مراقبة' : 'No monitoring events'} /></Card>}
          {events.map(e => (
            <Card key={e.id}>
              <div className="flex items-start justify-between gap-2">
                <div>
                  <div className="flex items-center gap-2">
                    <Badge tone={severityTone(e.severity)}>{isAr ? SEVERITY_LABELS[e.severity].ar : SEVERITY_LABELS[e.severity].en}</Badge>
                    <Badge tone="info">{isAr ? EVENT_TYPE_LABELS[e.eventType].ar : EVENT_TYPE_LABELS[e.eventType].en}</Badge>
                    {e.resolved
                      ? <Badge tone="success">{isAr ? 'مُغلَق' : 'Resolved'}</Badge>
                      : <Badge tone="warning">{isAr ? 'مفتوح' : 'Open'}</Badge>}
                  </div>
                  <div className="mt-1 text-xs text-muted-foreground">{e.eventDate}</div>
                </div>
              </div>
              <p className="mt-2 text-sm">{isAr ? e.descriptionAr : e.descriptionEn}</p>
              {(e.actionTakenEn || e.actionTakenAr) && (
                <p className="mt-1 text-xs text-muted-foreground">
                  <strong>{isAr ? 'الإجراء المُتَّخذ: ' : 'Action: '}</strong>
                  {isAr ? e.actionTakenAr : e.actionTakenEn}
                </p>
              )}
            </Card>
          ))}
        </div>
      )}

      {tab === 'exit' && (
        <div className="mt-4">
          {!exitPlan
            ? <Card><InlineEmpty text={isAr ? 'لا توجد خطة خروج لهذا الترتيب' : 'No exit plan for this arrangement'} /></Card>
            : (
              <Card title={isAr ? 'خطة الخروج' : 'Exit plan'} Icon={LogOut} action={
                <div className="flex items-center gap-2">
                  <Badge tone={exitPlan.lastTestedDate ? 'success' : 'warning'}>
                    {isAr ? TEST_FREQUENCY_LABELS[exitPlan.testFrequency].ar : TEST_FREQUENCY_LABELS[exitPlan.testFrequency].en}
                  </Badge>
                  {exitPlan.lastTestedDate && (
                    <span className="text-xs text-muted-foreground">
                      {isAr ? 'آخر اختبار:' : 'Last test:'} {exitPlan.lastTestedDate}
                    </span>
                  )}
                </div>
              }>
                <DataPair
                  label={isAr ? 'شروط التفعيل' : 'Trigger conditions'}
                  value={isAr ? exitPlan.triggerConditionsAr : exitPlan.triggerConditionsEn}
                />
                {(exitPlan.dataRepatriationPlanEn || exitPlan.dataRepatriationPlanAr) && (
                  <DataPair
                    label={isAr ? 'خطة استرداد البيانات' : 'Data repatriation'}
                    value={isAr ? (exitPlan.dataRepatriationPlanAr ?? '') : (exitPlan.dataRepatriationPlanEn ?? '')}
                  />
                )}
                {exitPlan.exitStepsJson && (
                  <div className="mt-3">
                    <div className="text-xs uppercase tracking-wide text-muted-foreground mb-1">
                      {isAr ? 'خطوات الخروج' : 'Exit steps'}
                    </div>
                    <ExitSteps json={exitPlan.exitStepsJson} isAr={isAr} />
                  </div>
                )}
              </Card>
            )}
        </div>
      )}

      {tab === 'activity' && (
        <div className="mt-4">
          <Card title={isAr ? 'سجل النشاط' : 'Activity log'} Icon={History}>
            <p className="text-sm text-muted-foreground">
              {isAr
                ? 'يُجلب سجل التدقيق من خدمة F7 على مستوى المنصة.'
                : 'F7 platform-level audit log entries are surfaced via the audit module.'}
            </p>
          </Card>
        </div>
      )}
    </Shell>
  );
}

function DataPair({ label, value }: { label: string; value: string | number }) {
  return (
    <div className="grid grid-cols-3 gap-2 py-1 text-sm">
      <div className="text-muted-foreground">{label}</div>
      <div className="col-span-2 font-medium">{value}</div>
    </div>
  );
}

function ScoreCell({ label, score }: { label: string; score: number }) {
  const tone =
    score >= 85 ? 'text-green-600'
    : score >= 70 ? 'text-amber-600'
    : 'text-red-600';
  return (
    <div className="rounded-md border bg-muted/20 p-2">
      <div className="text-[10px] uppercase tracking-wide text-muted-foreground">{label}</div>
      <div className={`text-lg font-bold ${tone}`}>{score}</div>
    </div>
  );
}

interface ExitStep { step: string; owner?: string; target_date_relative_days?: number }

function ExitSteps({ json, isAr }: { json: string; isAr: boolean }) {
  let parsed: ExitStep[] = [];
  try { parsed = JSON.parse(json) as ExitStep[]; } catch { /* ignore */ }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    return <pre className="text-xs whitespace-pre-wrap">{json}</pre>;
  }
  return (
    <ol className="space-y-1 text-sm">
      {parsed.map((s, i) => (
        <li key={i} className="flex items-start gap-2">
          <span className="font-mono text-xs text-muted-foreground">{i + 1}.</span>
          <div className="flex-1">
            <div>{s.step}</div>
            <div className="text-xs text-muted-foreground">
              {s.owner && <span>{isAr ? 'المسؤول: ' : 'Owner: '}{s.owner}</span>}
              {typeof s.target_date_relative_days === 'number' && (
                <span className="ms-3">
                  {isAr
                    ? `الموعد المُستهدف: +${s.target_date_relative_days} يوم`
                    : `Target: T+${s.target_date_relative_days}d`}
                </span>
              )}
            </div>
          </div>
        </li>
      ))}
    </ol>
  );
}
