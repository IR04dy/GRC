import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowRight, CheckCircle2, ClipboardList, RefreshCw,
  TrendingUp, Activity, CalendarRange,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  iaApi, planStatusLabel, engagementPhaseLabel, engagementTypeLabel,
  type EngagementPhase, type IaEngagement, type IaPlan, type IaPlanDashboard,
} from '../../lib/iaPlan';

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

const PHASE_TONE: Record<EngagementPhase, BadgeTone> = {
  PLANNED:       'neutral',
  PLANNING:      'info',
  FIELDWORK:     'warning',
  REPORTING:     'warning',
  REPORT_ISSUED: 'accent',
  FOLLOW_UP:     'info',
  CLOSED:        'success',
  CANCELLED:     'neutral',
};

/**
 * Sprint 13 — Plan detail page with KPI tiles + engagement list.
 *
 * <p>The header shows the plan status (DRAFT → … → COMPLETED) and
 * approver/timestamps. KPI tiles cover the engagement-phase counts
 * pulled from {@code GET /api/v1/ia/plan/{id}/dashboard}. The
 * "Engagements" list lets the auditor jump into a specific engagement
 * detail page.
 */
export function IaPlanDetailPage() {
  const { id } = useParams<{ id: string }>();
  const planId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();

  const [state, setState] = useState<LoadState>('idle');
  const [plan, setPlan] = useState<IaPlan | null>(null);
  const [dashboard, setDashboard] = useState<IaPlanDashboard | null>(null);
  const [engagements, setEngagements] = useState<IaEngagement[]>([]);
  const [err, setErr] = useState<string | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      const [p, d, eList] = await Promise.all([
        iaApi.getPlan(planId),
        iaApi.planDashboard(planId),
        iaApi.listEngagementsForPlan(planId),
      ]);
      setPlan(p);
      setDashboard(d);
      setEngagements(eList);
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { if (!Number.isNaN(planId)) void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [planId]);

  const totalEng = useMemo(() => engagements.length, [engagements]);

  if (Number.isNaN(planId)) {
    return (
      <Shell title="Plan">
        <div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)' }}>
          {isAr ? 'مُعرّف الخطة غير صالح' : 'Invalid plan id'}{' '}
          <Link to="/audit/ia/plans">{isAr ? 'العودة' : 'Back to list'}</Link>
        </div>
      </Shell>
    );
  }

  return (
    <Shell
      title={plan ? (isAr ? plan.titleAr : plan.titleEn) : (isAr ? 'تفاصيل الخطة' : 'Plan detail')}
      sub={plan ? `${plan.planCode} — ${isAr ? 'دورة' : 'Cycle'} ${plan.cycleYear}` : undefined}
    >
      <PageHeader
        eyebrow={isAr ? 'المراجعة الداخلية' : 'Internal Audit'}
        title={plan ? (isAr ? plan.titleAr : plan.titleEn) : (isAr ? 'تفاصيل الخطة' : 'Plan detail')}
        sub={plan ? `${plan.planCode}` : ''}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Link to="/audit/ia/plans">
              <Button variant="ghost" size="sm">{isAr ? '← الخطط' : '← Plans'}</Button>
            </Link>
          </>
        }
      />

      {state === 'loading' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      )}
      {state === 'error' && (
        <Card><div style={{ padding: 32, textAlign: 'center', fontSize: 12, color: 'var(--danger-fg)' }}>{err}</div></Card>
      )}

      {state === 'ready' && plan && dashboard && (
        <>
          {/* Status strip */}
          <div className="card" style={{
            padding: 14, marginBottom: 16,
            display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap',
          }}>
            <Badge tone="info">{planStatusLabel(plan.status, isAr)}</Badge>
            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              {isAr ? 'الجَردة:' : 'Universe:'} <strong>{dashboard.auditUniverseCount}</strong>
            </span>
            <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
              {isAr ? 'مهام مُجدوَلة:' : 'Engagements planned:'} <strong>{dashboard.totalEngagementsPlanned}</strong>
            </span>
            {plan.approvedAt && (
              <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                {isAr ? 'اعتُمدت في:' : 'Approved on:'} {plan.approvedAt.slice(0, 10)}
              </span>
            )}
            {dashboard.onTimePercentage != null && (
              <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>
                {isAr ? 'نسبة الإنجاز في الموعد:' : 'On-time:'} <strong>{dashboard.onTimePercentage.toFixed(0)}%</strong>
              </span>
            )}
          </div>

          {/* KPI tiles */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
            gap: 12, marginBottom: 16,
          }}>
            <KpiTile icon={CalendarRange} value={dashboard.engagementsPlanned} label={isAr ? 'مُجدوَل' : 'Planned'} tone="neutral" />
            <KpiTile icon={ClipboardList} value={dashboard.engagementsInPlanning} label={isAr ? 'مرحلة التخطيط' : 'Planning'} tone="info" />
            <KpiTile icon={Activity}      value={dashboard.engagementsInFieldwork} label={isAr ? 'العمل الميداني' : 'Fieldwork'} tone="warning" />
            <KpiTile icon={TrendingUp}    value={dashboard.engagementsInReporting} label={isAr ? 'كتابة التقرير' : 'Reporting'} tone="warning" />
            <KpiTile icon={CheckCircle2}  value={dashboard.engagementsReportIssued} label={isAr ? 'صدر التقرير' : 'Report issued'} tone="accent" />
            <KpiTile icon={CheckCircle2}  value={dashboard.engagementsClosed}      label={isAr ? 'مُغلَق' : 'Closed'} tone="success" />
          </div>

          {/* Engagements list */}
          <Card>
            <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>
                {isAr ? `المهام الميدانية (${totalEng})` : `Engagements (${totalEng})`}
              </div>
            </div>

            {engagements.length === 0 && (
              <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                {isAr ? 'لا توجد مهام ميدانية مُجدوَلة بعد' : 'No engagements scheduled yet'}
              </div>
            )}

            {engagements.map(e => (
              <button
                key={e.id}
                type="button"
                onClick={() => navigate(`/audit/ia/engagements/${e.id}`)}
                style={{
                  width: '100%', textAlign: 'start',
                  padding: 12, borderBottom: '1px solid var(--border)',
                  background: 'transparent', cursor: 'pointer',
                  display: 'grid',
                  gridTemplateColumns: '90px 1fr auto auto',
                  gap: 12, alignItems: 'center',
                }}>
                <div>
                  <div style={{ fontFamily: 'monospace', fontSize: 11, fontWeight: 700 }}>{e.engagementCode}</div>
                  <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                    {engagementTypeLabel(e.auditType, isAr)}
                  </div>
                </div>
                <div style={{ fontSize: 12 }}>
                  {isAr ? e.titleAr : e.titleEn}
                  {e.plannedStartDate && (
                    <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      {e.plannedStartDate.slice(0, 10)} → {e.plannedEndDate?.slice(0, 10)}
                    </div>
                  )}
                </div>
                <Badge tone={PHASE_TONE[e.phase]}>{engagementPhaseLabel(e.phase, isAr)}</Badge>
                <ArrowRight size={14} style={{ opacity: 0.5 }} />
              </button>
            ))}
          </Card>
        </>
      )}
    </Shell>
  );
}

function KpiTile({ icon: Icon, value, label, tone }:
  { icon: typeof CalendarRange; value: number; label: string; tone: BadgeTone }) {
  return (
    <Card>
      <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)' }}>
          <Icon size={14} />
          <span style={{ fontSize: 11 }}>{label}</span>
        </div>
        <div style={{ fontSize: 28, fontWeight: 700 }}>
          <Badge tone={Number(value) > 0 ? tone : 'neutral'}>{value}</Badge>
        </div>
      </div>
    </Card>
  );
}
