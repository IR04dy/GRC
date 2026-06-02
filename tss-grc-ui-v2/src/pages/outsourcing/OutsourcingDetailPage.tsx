import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { ArrowLeft, FileText, ShieldCheck, AlertTriangle, LogOut, History, RefreshCw } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { EvidenceReadinessPanel } from '../../components/ui/EvidenceReadinessPanel';
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

// Shared token-driven styles (the page previously used non-existent Tailwind
// utility classes — these render the intended layout with design tokens).
const row: CSSProperties = { display: 'flex', alignItems: 'center', gap: 8 };
const rowStart: CSSProperties = { display: 'flex', alignItems: 'flex-start', justifyContent: 'space-between', gap: 8 };
const stack: CSSProperties = { display: 'flex', flexDirection: 'column', gap: 12, marginTop: 16 };
const overviewGrid: CSSProperties = { display: 'grid', gap: 16, marginTop: 16, gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))' };
const fullSpan: CSSProperties = { gridColumn: '1 / -1' };
const muted: CSSProperties = { color: 'var(--ink-3)' };

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
    { id: 'dd',       label: isAr ? 'العناية الواجبة' : 'Due Diligence', count: dd.length },
    { id: 'events',   label: isAr ? 'المراقبة المستمرّة' : 'Monitoring', count: events.length },
    { id: 'exit',     label: isAr ? 'خطة الخروج' : 'Exit Plan' },
    { id: 'activity', label: isAr ? 'النشاط' : 'Activity' },
  ], [isAr, dd.length, events.length]);

  if (!Number.isFinite(arrangementId)) {
    return <Shell><div style={{ padding: 24 }}>{isAr ? 'معرّف غير صحيح' : 'Invalid id'}</div></Shell>;
  }

  if (loading && !arrangement) {
    return <Shell><div style={{ padding: 24 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Shell>;
  }

  if (!arrangement) {
    return (
      <Shell>
        <div style={{ padding: 24 }}>
          <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/tprm/outsourcing')}>
            {isAr ? 'الرجوع للسجل' : 'Back to register'}
          </Button>
          <div style={{ marginTop: 16 }}>{isAr ? 'ترتيب غير موجود' : 'Arrangement not found'}</div>
        </div>
      </Shell>
    );
  }

  return (
    <Shell>
      <PageHeader
        backTo="/tprm/outsourcing"
        eyebrow={`${arrangement.arrangementCode}`}
        title={isAr ? arrangement.titleAr : arrangement.titleEn}
        sub={isAr ? arrangement.titleEn : arrangement.titleAr}
        badges={
          <>
            {arrangement.isMaterial
              ? <Badge tone="warning">{isAr ? 'إسناد جوهري' : 'Material'}</Badge>
              : <Badge tone="neutral">{isAr ? 'غير جوهري' : 'Non-material'}</Badge>}
            <Badge tone={lifecycleTone(arrangement.lifecycleStage)}>
              {LIFECYCLE_LABELS[arrangement.lifecycleStage]?.[isAr ? 'ar' : 'en'] ?? arrangement.lifecycleStage}
            </Badge>
          </>
        }
        actions={
          <Button variant="secondary" Icon={RefreshCw} onClick={onRecordReview}>
            {isAr ? 'تسجيل مراجعة' : 'Record review'}
          </Button>
        }
      />

      {/* Regulatory-context chips (status + materiality now sit by the title) */}
      <Card>
        <div style={{ ...row, flexWrap: 'wrap' }}>
          <Badge tone="info">
            {SERVICE_TYPE_LABELS[arrangement.serviceType]?.[isAr ? 'ar' : 'en'] ?? arrangement.serviceType}
          </Badge>
          {arrangement.criticalFunction !== 'NA' && (
            <Badge tone="warning">
              {CRITICAL_FUNCTION_LABELS[arrangement.criticalFunction]?.[isAr ? 'ar' : 'en'] ?? arrangement.criticalFunction}
            </Badge>
          )}
          {arrangement.samaNonObjectionRequired && (
            arrangement.samaNonObjectionObtained
              ? <Badge tone="success">{isAr ? `عدم ممانعة ساما: ${arrangement.samaNonObjectionRef ?? '✓'}` : `SAMA non-objection: ${arrangement.samaNonObjectionRef ?? '✓'}`}</Badge>
              : <Badge tone="danger">{isAr ? 'عدم ممانعة ساما — مطلوب' : 'SAMA non-objection required'}</Badge>
          )}
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
        <Tabs items={tabs} active={tab} onChange={(id) => setTab(id as TabId)} />
      </div>

      {tab === 'overview' && (
        <div style={overviewGrid}>
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
          <div style={fullSpan}>
            <Card>
              <AttachmentsField entityType="OUTSOURCING" entityId={arrangement.id} />
            </Card>
          </div>
          <div style={fullSpan}>
            <Card>
              <ControlMappingPicker entityType="OUTSOURCING" entityId={arrangement.id} evidence={false} />
            </Card>
          </div>
          <div style={fullSpan}>
            <Card title={isAr ? 'جاهزية الأدلة' : 'Evidence readiness'}>
              <EvidenceReadinessPanel entityType="OUTSOURCING" entityId={arrangement.id} />
            </Card>
          </div>
          <div style={fullSpan}>
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
        <div style={stack}>
          {dd.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد سجلات عناية واجبة' : 'No due diligence records'} /></Card>}
          {dd.map(d => (
            <Card key={d.id}>
              <div style={rowStart}>
                <div>
                  <div style={{ fontSize: 13, fontWeight: 600 }}>
                    {isAr ? 'تاريخ التقييم:' : 'Assessment date:'} {d.assessmentDate}
                  </div>
                  <div style={{ fontSize: 11, ...muted }}>
                    {isAr ? 'المُقيِّم' : 'Assessor'} #{d.assessorUserId}
                  </div>
                </div>
                <div style={row}>
                  <Badge tone={recommendationTone(d.recommendation)}>{d.recommendation}</Badge>
                  <span className="tnum" style={{ fontSize: 24, fontWeight: 800, letterSpacing: 'var(--tracking-num)' }}>{d.compositeScore.toFixed(2)}</span>
                </div>
              </div>
              <div style={{ marginTop: 12, display: 'grid', gap: 8, gridTemplateColumns: 'repeat(auto-fit, minmax(96px, 1fr))' }}>
                <ScoreCell label={isAr ? 'المالية' : 'Financial'} score={d.financialHealthScore} />
                <ScoreCell label={isAr ? 'التشغيلية' : 'Operational'} score={d.operationalCapabilityScore} />
                <ScoreCell label={isAr ? 'السيبراني' : 'Cyber'} score={d.cybersecurityScore} />
                <ScoreCell label={isAr ? 'BCM' : 'BCM'} score={d.bcmScore} />
                <ScoreCell label={isAr ? 'PDPL' : 'PDPL'} score={d.pdplComplianceScore} />
                <ScoreCell label={isAr ? 'السمعة' : 'Reputation'} score={d.reputationScore} />
              </div>
              <div style={{ marginTop: 12, fontSize: 13, ...muted }}>
                {isAr ? d.rationaleAr : d.rationaleEn}
              </div>
            </Card>
          ))}
        </div>
      )}

      {tab === 'events' && (
        <div style={stack}>
          {events.length === 0 && <Card><InlineEmpty text={isAr ? 'لا توجد أحداث مراقبة' : 'No monitoring events'} /></Card>}
          {events.map(e => (
            <Card key={e.id}>
              <div style={rowStart}>
                <div>
                  <div style={{ ...row, flexWrap: 'wrap' }}>
                    <Badge tone={severityTone(e.severity)}>{SEVERITY_LABELS[e.severity]?.[isAr ? 'ar' : 'en'] ?? e.severity}</Badge>
                    <Badge tone="info">{EVENT_TYPE_LABELS[e.eventType]?.[isAr ? 'ar' : 'en'] ?? e.eventType}</Badge>
                    {e.resolved
                      ? <Badge tone="success">{isAr ? 'مُغلَق' : 'Resolved'}</Badge>
                      : <Badge tone="warning">{isAr ? 'مفتوح' : 'Open'}</Badge>}
                  </div>
                  <div style={{ marginTop: 4, fontSize: 11, ...muted }}>{e.eventDate}</div>
                </div>
              </div>
              <p style={{ marginTop: 8, fontSize: 13 }}>{isAr ? e.descriptionAr : e.descriptionEn}</p>
              {(e.actionTakenEn || e.actionTakenAr) && (
                <p style={{ marginTop: 4, fontSize: 11, ...muted }}>
                  <strong>{isAr ? 'الإجراء المُتَّخذ: ' : 'Action: '}</strong>
                  {isAr ? e.actionTakenAr : e.actionTakenEn}
                </p>
              )}
            </Card>
          ))}
        </div>
      )}

      {tab === 'exit' && (
        <div style={{ marginTop: 16 }}>
          {!exitPlan
            ? <Card><InlineEmpty text={isAr ? 'لا توجد خطة خروج لهذا الترتيب' : 'No exit plan for this arrangement'} /></Card>
            : (
              <Card title={isAr ? 'خطة الخروج' : 'Exit plan'} Icon={LogOut} action={
                <div style={row}>
                  <Badge tone={exitPlan.lastTestedDate ? 'success' : 'warning'}>
                    {TEST_FREQUENCY_LABELS[exitPlan.testFrequency]?.[isAr ? 'ar' : 'en'] ?? exitPlan.testFrequency}
                  </Badge>
                  {exitPlan.lastTestedDate && (
                    <span style={{ fontSize: 11, ...muted }}>
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
                  <div style={{ marginTop: 12 }}>
                    <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.06em', marginBottom: 4, ...muted }}>
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
        <div style={{ marginTop: 16 }}>
          <Card title={isAr ? 'سجل النشاط' : 'Activity log'} Icon={History}>
            <p style={{ fontSize: 13, ...muted }}>
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
    <div style={{ display: 'grid', gridTemplateColumns: 'minmax(120px, 1fr) 2fr', gap: 8, padding: '4px 0', fontSize: 13, alignItems: 'baseline' }}>
      <div style={{ color: 'var(--ink-3)' }}>{label}</div>
      <div style={{ fontWeight: 500, color: 'var(--ink-1)' }}>{value}</div>
    </div>
  );
}

function ScoreCell({ label, score }: { label: string; score: number }) {
  const color =
    score >= 85 ? 'var(--success)'
    : score >= 70 ? 'var(--warning)'
    : 'var(--danger)';
  return (
    <div style={{ borderRadius: 'var(--r-md)', border: '1px solid var(--border)', background: 'var(--surface-2)', padding: 8 }}>
      <div style={{ fontSize: 10, textTransform: 'uppercase', letterSpacing: '0.06em', color: 'var(--ink-3)' }}>{label}</div>
      <div className="tnum" style={{ fontSize: 18, fontWeight: 700, color }}>{score}</div>
    </div>
  );
}

interface ExitStep { step: string; owner?: string; target_date_relative_days?: number }

function ExitSteps({ json, isAr }: { json: string; isAr: boolean }) {
  let parsed: ExitStep[] = [];
  try { parsed = JSON.parse(json) as ExitStep[]; } catch { /* ignore */ }
  if (!Array.isArray(parsed) || parsed.length === 0) {
    return <pre style={{ fontSize: 11, whiteSpace: 'pre-wrap' }}>{json}</pre>;
  }
  return (
    <ol style={{ display: 'flex', flexDirection: 'column', gap: 4, fontSize: 13, margin: 0, paddingInlineStart: 0, listStyle: 'none' }}>
      {parsed.map((s, i) => (
        <li key={i} style={{ display: 'flex', alignItems: 'flex-start', gap: 8 }}>
          <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{i + 1}.</span>
          <div style={{ flex: 1 }}>
            <div>{s.step}</div>
            <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
              {s.owner && <span>{isAr ? 'المسؤول: ' : 'Owner: '}{s.owner}</span>}
              {typeof s.target_date_relative_days === 'number' && (
                <span style={{ marginInlineStart: 12 }}>
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
