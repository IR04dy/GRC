import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import {
  ArrowRight, CheckCircle2, ClipboardList, RefreshCw,
  TrendingUp, Activity, CalendarRange, Plus, Layers, Flame,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { isAuthenticated } from '../../lib/auth';
import { ApiError } from '../../lib/api';
import {
  iaApi, planStatusLabel, engagementPhaseLabel, engagementTypeLabel, residualBand,
  type EngagementPhase, type EngagementType, type IaEngagement, type IaPlan,
  type IaPlanDashboard, type IaUniverseEntry,
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

const BAND_TONE: Record<ReturnType<typeof residualBand>, BadgeTone> = {
  critical: 'danger',
  high:     'danger',
  medium:   'warning',
  low:      'success',
  unscored: 'neutral',
};

/** Engagement-type options, ordered by how often IA functions reach for them. */
const ENGAGEMENT_TYPES: EngagementType[] = [
  'OPERATIONAL', 'COMPLIANCE', 'FINANCIAL', 'IT_GENERAL', 'IT_APPLICATION',
  'FRAUD', 'SPECIAL_REQUEST', 'FOLLOW_UP', 'MANAGEMENT_REVIEW',
];

/**
 * Sprint 13 — Plan detail page with KPI tiles + engagement list.
 *
 * <p>The header shows the plan status (DRAFT → … → COMPLETED) and
 * approver/timestamps. KPI tiles cover the engagement-phase counts
 * pulled from {@code GET /api/v1/ia/plan/{id}/dashboard}. The
 * "Engagements" list lets the auditor jump into a specific engagement
 * detail page.
 *
 * <p>The "Audit universe" panel materialises the plan's auditable
 * entities and lets an auditor schedule one straight into an engagement
 * via {@code POST /api/v1/ia/engagements/from-universe}, which copies the
 * entry's title and synthesises a bilingual scope summary so nothing is
 * re-typed.
 */
export function IaPlanDetailPage() {
  const { id } = useParams<{ id: string }>();
  const planId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const navigate = useNavigate();
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [state, setState] = useState<LoadState>('idle');
  const [plan, setPlan] = useState<IaPlan | null>(null);
  const [dashboard, setDashboard] = useState<IaPlanDashboard | null>(null);
  const [engagements, setEngagements] = useState<IaEngagement[]>([]);
  const [universe, setUniverse] = useState<IaUniverseEntry[]>([]);
  const [err, setErr] = useState<string | null>(null);

  // Create-engagement-from-universe modal
  const [createFor, setCreateFor] = useState<IaUniverseEntry | null>(null);
  const [saving, setSaving] = useState(false);
  const [cType, setCType] = useState<EngagementType>('OPERATIONAL');
  const [cCode, setCCode] = useState('');
  const [cStart, setCStart] = useState('');
  const [cEnd, setCEnd] = useState('');

  async function load() {
    setState('loading'); setErr(null);
    try {
      const [p, d, eList, uList] = await Promise.all([
        iaApi.getPlan(planId),
        iaApi.planDashboard(planId),
        iaApi.listEngagementsForPlan(planId),
        iaApi.listUniverseForPlan(planId),
      ]);
      setPlan(p);
      setDashboard(d);
      setEngagements(eList);
      setUniverse(uList);
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  useEffect(() => { if (!Number.isNaN(planId)) void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [planId]);

  const totalEng = useMemo(() => engagements.length, [engagements]);

  // Universe entries that already have at least one engagement on this plan.
  const scheduledEntryIds = useMemo(
    () => new Set(engagements.map(e => e.universeEntryId).filter((x): x is number => x != null)),
    [engagements],
  );

  // Highest residual first so the auditor schedules the riskiest gaps next.
  const sortedUniverse = useMemo(
    () => [...universe].sort((a, b) => (b.residualRiskScore ?? 0) - (a.residualRiskScore ?? 0)),
    [universe],
  );

  const unscheduledCount = useMemo(
    () => universe.filter(u => !scheduledEntryIds.has(u.id)).length,
    [universe, scheduledEntryIds],
  );

  const planWritable = plan != null && plan.status !== 'COMPLETED' && plan.status !== 'ARCHIVED';

  function openCreate(entry: IaUniverseEntry) {
    setCreateFor(entry);
    setCType('OPERATIONAL');
    setCCode('');
    setCStart('');
    setCEnd('');
  }

  async function submitCreate() {
    if (!createFor) return;
    setSaving(true);
    try {
      const created = await iaApi.createFromUniverse({
        planId,
        universeEntryId: createFor.id,
        auditType: cType,
        engagementCode: cCode.trim() || undefined,
        plannedStartDate: cStart || undefined,
        plannedEndDate: cEnd || undefined,
      });
      toast.success(isAr
        ? `أُنشئت المهمة ${created.engagementCode} من ${createFor.entryCode}`
        : `Engagement ${created.engagementCode} created from ${createFor.entryCode}`);
      setCreateFor(null);
      await load();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر إنشاء المهمة' : 'Failed to create engagement'));
    } finally {
      setSaving(false);
    }
  }

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
            <KpiTile icon={CalendarRange} value={dashboard.engagementsPlanned} label={isAr ? 'مُجدوَل' : 'Planned'} tone="neutral" to="/audit/ia/plans" />
            <KpiTile icon={ClipboardList} value={dashboard.engagementsInPlanning} label={isAr ? 'مرحلة التخطيط' : 'Planning'} tone="info" to="/audit-workspace" />
            <KpiTile icon={Activity}      value={dashboard.engagementsInFieldwork} label={isAr ? 'العمل الميداني' : 'Fieldwork'} tone="warning" to="/audit-workspace" />
            <KpiTile icon={TrendingUp}    value={dashboard.engagementsInReporting} label={isAr ? 'كتابة التقرير' : 'Reporting'} tone="warning" to="/audit-workspace" />
            <KpiTile icon={CheckCircle2}  value={dashboard.engagementsReportIssued} label={isAr ? 'صدر التقرير' : 'Report issued'} tone="accent" to="/audit-workspace" />
            <KpiTile icon={CheckCircle2}  value={dashboard.engagementsClosed}      label={isAr ? 'مُغلَق' : 'Closed'} tone="success" to="/audit-workspace" />
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

          {/* Audit universe → schedule into engagements */}
          <div style={{ marginTop: 16 }}>
          <Card>
            <div style={{
              padding: 12, borderBottom: '1px solid var(--border)',
              display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 12, flexWrap: 'wrap',
            }}>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Layers size={15} />
                <span style={{ fontSize: 13, fontWeight: 600 }}>
                  {isAr ? `الجَردة القابلة للمراجعة (${universe.length})` : `Audit universe (${universe.length})`}
                </span>
              </div>
              <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr
                  ? `${scheduledEntryIds.size} مُجدوَلة · ${unscheduledCount} بانتظار الجدولة`
                  : `${scheduledEntryIds.size} scheduled · ${unscheduledCount} awaiting scheduling`}
              </span>
            </div>

            {universe.length === 0 && (
              <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>
                {isAr ? 'لا توجد عمليات مرتبطة بهذه الخطة' : 'No auditable entities linked to this plan'}
              </div>
            )}

            {sortedUniverse.map(u => {
              const band = residualBand(u.residualRiskScore);
              const scheduled = scheduledEntryIds.has(u.id);
              return (
                <div key={u.id} style={{
                  padding: 12, borderBottom: '1px solid var(--border)',
                  display: 'grid', gridTemplateColumns: '150px 1fr auto auto auto',
                  gap: 12, alignItems: 'center',
                }}>
                  <div style={{ fontFamily: 'monospace', fontSize: 11, fontWeight: 700 }}>{u.entryCode}</div>
                  <div>
                    <div style={{ fontSize: 12 }}>{isAr ? u.titleAr : u.titleEn}</div>
                    <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                      {isAr ? (u.businessUnitAr ?? u.processNameAr ?? '') : (u.businessUnitEn ?? u.processNameEn ?? '')}
                    </div>
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)', textAlign: 'center', display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Flame size={12} style={{ opacity: 0.7 }} />
                    <Badge tone={BAND_TONE[band]}>{u.residualRiskScore?.toFixed(2) ?? '—'}</Badge>
                  </div>
                  <div style={{ minWidth: 78, textAlign: 'center' }}>
                    {scheduled
                      ? <Badge tone="success">{isAr ? 'مُجدوَلة' : 'Scheduled'}</Badge>
                      : <span style={{ fontSize: 10, color: 'var(--ink-3)' }}>{isAr ? 'غير مُجدوَلة' : 'Not scheduled'}</span>}
                  </div>
                  <div style={{ textAlign: 'end' }}>
                    {!demoMode && planWritable && (
                      <Button
                        variant={scheduled ? 'ghost' : 'primary'}
                        size="sm"
                        onClick={() => openCreate(u)}
                        title={scheduled
                          ? (isAr ? 'لها مهمة بالفعل — إنشاء مهمة إضافية' : 'Already scheduled — create another engagement')
                          : (isAr ? 'إنشاء مهمة ميدانية من هذه العملية' : 'Create an engagement from this entry')}>
                        <Plus size={13} />
                        <span style={{ marginInlineStart: 4 }}>{isAr ? 'إنشاء مهمة' : 'Create engagement'}</span>
                      </Button>
                    )}
                  </div>
                </div>
              );
            })}
          </Card>
          </div>
        </>
      )}

      {/* Create-engagement-from-universe modal */}
      <Modal
        open={createFor != null}
        onClose={() => { if (!saving) setCreateFor(null); }}
        title={isAr
          ? `إنشاء مهمة من ${createFor?.entryCode ?? ''}`
          : `Create engagement — ${createFor?.entryCode ?? ''}`}
        footer={
          <>
            <Button variant="ghost" onClick={() => setCreateFor(null)} disabled={saving}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button variant="primary" onClick={() => void submitCreate()} disabled={saving}>
              {saving
                ? (isAr ? 'جارٍ الإنشاء…' : 'Creating…')
                : (isAr ? 'إنشاء المهمة' : 'Create engagement')}
            </Button>
          </>
        }
      >
        {createFor && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Context from the universe entry */}
            <div style={{
              padding: 12, borderRadius: 8,
              background: 'var(--surface-2)', border: '1px solid var(--border)',
              display: 'flex', flexDirection: 'column', gap: 6,
            }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>{isAr ? createFor.titleAr : createFor.titleEn}</div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr ? (createFor.businessUnitAr ?? createFor.processNameAr ?? '—') : (createFor.businessUnitEn ?? createFor.processNameEn ?? '—')}
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr ? 'الخطر المتبقي: ' : 'Residual risk: '}
                <Badge tone={BAND_TONE[residualBand(createFor.residualRiskScore)]}>
                  {createFor.residualRiskScore?.toFixed(2) ?? '—'}
                </Badge>
              </div>
              <div style={{ fontSize: 10, color: 'var(--ink-3)', lineHeight: 1.5 }}>
                {isAr
                  ? 'يُنسَخ العنوان من الجَردة، ويُولّد النظام ملخّص نطاق ثنائي اللغة من العملية ووحدة الأعمال تلقائياً. تبدأ المهمة في حالة «مُجدوَل».'
                  : 'The title is copied from the universe, and the system auto-synthesises a bilingual scope summary from the process and business unit. The engagement starts in the “Planned” phase.'}
              </div>
            </div>

            <FormField label={isAr ? 'نوع المهمة' : 'Engagement type'}>
              <select
                value={cType}
                onChange={(e) => setCType(e.target.value as EngagementType)}
                style={ctrlStyle}>
                {ENGAGEMENT_TYPES.map(t => (
                  <option key={t} value={t}>{engagementTypeLabel(t, isAr)}</option>
                ))}
              </select>
            </FormField>

            <FormField label={isAr ? 'رمز المهمة (اختياري)' : 'Engagement code (optional)'}>
              <input
                type="text"
                value={cCode}
                onChange={(e) => setCCode(e.target.value)}
                placeholder={isAr ? 'يُولَّد تلقائياً مثل ENG-2026-006' : 'Auto-generated, e.g. ENG-2026-006'}
                style={ctrlStyle} />
            </FormField>

            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
              <FormField label={isAr ? 'تاريخ البدء المُخطّط' : 'Planned start'}>
                <input type="date" value={cStart} onChange={(e) => setCStart(e.target.value)} style={ctrlStyle} />
              </FormField>
              <FormField label={isAr ? 'تاريخ الانتهاء المُخطّط' : 'Planned end'}>
                <input type="date" value={cEnd} onChange={(e) => setCEnd(e.target.value)} style={ctrlStyle} />
              </FormField>
            </div>
          </div>
        )}
      </Modal>
    </Shell>
  );
}

const ctrlStyle: CSSProperties = {
  width: '100%', padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 6,
  background: 'var(--surface)', color: 'var(--ink-1)', outline: 'none',
};

function FormField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 5 }}>
      <span style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-2)' }}>{label}</span>
      {children}
    </label>
  );
}

function KpiTile({ icon: Icon, value, label, tone, to }:
  { icon: typeof CalendarRange; value: number; label: string; tone: BadgeTone; to?: string }) {
  const inner = (
    <div style={{ padding: 14, display: 'flex', flexDirection: 'column', gap: 6 }}>
      <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)' }}>
        <Icon size={14} />
        <span style={{ fontSize: 11 }}>{label}</span>
      </div>
      <div style={{ fontSize: 28, fontWeight: 700 }}>
        <Badge tone={Number(value) > 0 ? tone : 'neutral'}>{value}</Badge>
      </div>
    </div>
  );
  return (
    <Card>
      {to ? (
        <Link to={to} style={{ display: 'block', textDecoration: 'none', color: 'inherit' }}>
          {inner}
        </Link>
      ) : inner}
    </Card>
  );
}
