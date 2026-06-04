import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Activity, ArrowRightCircle, CalendarDays, CheckCircle2, Circle, ClipboardCheck,
  Clock, FileSignature, RefreshCw, Target, Users,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  iaApi, engagementPhaseLabel, engagementTypeLabel, milestoneTypeLabel,
  nextEngagementPhases,
  type EngagementPhase, type IaEngagement, type MilestoneType,
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

const PHASE_ORDER: EngagementPhase[] = [
  'PLANNED', 'PLANNING', 'FIELDWORK', 'REPORTING',
  'REPORT_ISSUED', 'FOLLOW_UP', 'CLOSED',
];

const MILESTONE_ORDER: MilestoneType[] = [
  'KICKOFF', 'FIELDWORK_START', 'DRAFT_REPORT',
  'MANAGEMENT_RESPONSE', 'FINAL_REPORT', 'FOLLOW_UP_DUE',
];

/** Today as an ISO yyyy-mm-dd string (browser-local). */
function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

/**
 * Sprint 13 — Engagement detail page with phase timeline + milestones.
 *
 * <p>Renders the full lifecycle for one engagement (the "Mahimmah
 * Maydaniyyah" — المهمّة الميدانية). Phase pills walk left-to-right
 * through the state machine; milestones list the planned vs actual
 * dates per IIA 2200/2300/2400/2500 standard checkpoints.
 *
 * <p>The lead auditor drives the engagement forward here: advancing the
 * phase through the validated state machine (POST …/transition) and
 * recording milestone actuals as checkpoints are met (POST …/milestones/record).
 */
export function IaEngagementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const engId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [state, setState] = useState<LoadState>('idle');
  const [eng, setEng] = useState<IaEngagement | null>(null);
  const [err, setErr] = useState<string | null>(null);

  // Action modals
  const [transitionOpen, setTransitionOpen] = useState(false);
  const [recordType, setRecordType] = useState<MilestoneType | null>(null);

  async function load() {
    setState('loading'); setErr(null);
    try {
      setEng(await iaApi.getEngagement(engId));
      setState('ready');
    } catch (e) {
      setErr((e as Error)?.message ?? String(e));
      setState('error');
    }
  }

  // Re-fetch without flipping the whole page back to its loading skeleton.
  async function refresh() {
    try { setEng(await iaApi.getEngagement(engId)); } catch { /* keep last good state */ }
  }

  useEffect(() => { if (!Number.isNaN(engId)) void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [engId]);

  const nextPhases = useMemo<EngagementPhase[]>(
    () => (eng ? nextEngagementPhases(eng.phase) : []),
    [eng],
  );

  // ── Phase transition ───────────────────────────────────────────────
  const transitionInitial = useMemo(
    () => ({ newPhase: nextPhases[0] ?? '', noteEn: '', noteAr: '' }),
    [nextPhases],
  );

  const transitionFields: FieldDef[] = [
    {
      kind: 'select', key: 'newPhase', span: 12, required: true,
      label: isAr ? 'المرحلة التالية' : 'Next phase',
      options: nextPhases.map(p => ({ value: p, label: engagementPhaseLabel(p, isAr) })),
    },
    { kind: 'textarea', key: 'noteEn', rows: 2, span: 12,
      label: isAr ? 'ملاحظة (إنجليزي)' : 'Note (EN)' },
    { kind: 'textarea', key: 'noteAr', rows: 2, span: 12,
      label: isAr ? 'ملاحظة (عربي)' : 'Note (AR)' },
  ];

  async function submitTransition(v: { newPhase: string; noteEn: string; noteAr: string }): Promise<void> {
    if (!v.newPhase) { toast.danger(isAr ? 'اختر المرحلة التالية' : 'Pick the next phase'); return; }
    try {
      const updated = await iaApi.transitionEngagement(engId, {
        newPhase: v.newPhase as EngagementPhase,
        noteEn: v.noteEn.trim() || undefined,
        noteAr: v.noteAr.trim() || undefined,
      });
      setEng(updated);
      setTransitionOpen(false);
      toast.success(isAr
        ? `انتقلت المهمّة إلى: ${engagementPhaseLabel(updated.phase, true)}`
        : `Engagement advanced to ${engagementPhaseLabel(updated.phase, false)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تغيير المرحلة' : 'Phase change failed'));
    }
  }

  // ── Milestone actual recording ─────────────────────────────────────
  const recordMilestone = recordType ? eng?.milestones.find(m => m.milestoneType === recordType) : undefined;
  const recordInitial = useMemo(
    () => ({
      actualDate: recordMilestone?.plannedDate?.slice(0, 10) ?? todayIso(),
      notesEn: recordMilestone?.notesEn ?? '',
      notesAr: recordMilestone?.notesAr ?? '',
    }),
    // Re-seed each time a different milestone's modal opens.
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [recordType],
  );

  const recordFields: FieldDef[] = [
    { kind: 'date', key: 'actualDate', span: 12,
      label: isAr ? 'التاريخ الفعلي *' : 'Actual date *' },
    { kind: 'textarea', key: 'notesEn', rows: 2, span: 12,
      label: isAr ? 'ملاحظات (إنجليزي)' : 'Notes (EN)' },
    { kind: 'textarea', key: 'notesAr', rows: 2, span: 12,
      label: isAr ? 'ملاحظات (عربي)' : 'Notes (AR)' },
  ];

  async function submitRecord(v: { actualDate: string; notesEn: string; notesAr: string }): Promise<void> {
    if (!recordType) return;
    if (!v.actualDate) { toast.danger(isAr ? 'حدّد التاريخ الفعلي' : 'Set the actual date'); return; }
    try {
      await iaApi.recordMilestone(engId, {
        milestoneType: recordType,
        actualDate: v.actualDate,
        notesEn: v.notesEn.trim() || undefined,
        notesAr: v.notesAr.trim() || undefined,
      });
      setRecordType(null);
      await refresh();
      toast.success(isAr
        ? `تم تسجيل محطّة: ${milestoneTypeLabel(recordType, true)}`
        : `Recorded milestone: ${milestoneTypeLabel(recordType, false)}`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل تسجيل المحطّة' : 'Milestone record failed'));
    }
  }

  if (Number.isNaN(engId)) {
    return <Shell title="Engagement"><div style={{ padding: 32, color: 'var(--danger-fg)' }}>Invalid id</div></Shell>;
  }

  const terminal = eng != null && nextPhases.length === 0;

  return (
    <Shell
      title={eng ? (isAr ? eng.titleAr : eng.titleEn) : (isAr ? 'تفاصيل المهمّة' : 'Engagement detail')}
      sub={eng ? eng.engagementCode : undefined}
    >
      <PageHeader
        eyebrow={isAr ? 'المراجعة الداخلية — مهمّة ميدانية' : 'Internal Audit — Engagement'}
        title={eng ? (isAr ? eng.titleAr : eng.titleEn) : (isAr ? 'تفاصيل المهمّة' : 'Engagement detail')}
        sub={eng ? `${eng.engagementCode} · ${engagementTypeLabel(eng.auditType, isAr)}` : ''}
        actions={
          <>
            <Button variant="ghost" size="sm" onClick={() => void load()}>
              <RefreshCw size={14} /> {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            {eng && !terminal && (
              <Button variant="primary" size="sm" onClick={() => setTransitionOpen(true)}>
                <ArrowRightCircle size={14} /> {isAr ? 'تغيير المرحلة' : 'Advance phase'}
              </Button>
            )}
            {eng && (
              <Link to={`/audit/ia/plans/${eng.planId}`}>
                <Button variant="ghost" size="sm">{isAr ? '← الخطة' : '← Plan'}</Button>
              </Link>
            )}
          </>
        }
      />

      {state === 'loading' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)', fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      )}
      {state === 'error' && (
        <Card><div style={{ padding: 32, textAlign: 'center', color: 'var(--danger-fg)', fontSize: 12 }}>{err}</div></Card>
      )}

      {state === 'ready' && eng && (
        <>
          {/* Phase timeline */}
          <Card>
            <div style={{
              padding: 12, borderBottom: '1px solid var(--border)',
              display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 12,
            }}>
              <div>
                <div style={{ fontSize: 13, fontWeight: 600 }}>
                  {isAr ? 'دورة حياة المهمّة الميدانية' : 'Engagement lifecycle'}
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {isAr ? 'IIA 2200 / 2300 / 2400 / 2500' : 'IIA 2200 / 2300 / 2400 / 2500'}
                </div>
              </div>
              {terminal ? (
                <Badge tone={eng.phase === 'CLOSED' ? 'success' : 'neutral'}>
                  {isAr ? 'مرحلة نهائية — لا انتقالات' : 'Terminal — no further transitions'}
                </Badge>
              ) : (
                <Button variant="secondary" size="sm" onClick={() => setTransitionOpen(true)}>
                  <ArrowRightCircle size={13} /> {isAr ? 'تغيير المرحلة' : 'Advance phase'}
                </Button>
              )}
            </div>
            <div style={{
              padding: 16,
              display: 'flex', gap: 6, flexWrap: 'wrap',
              alignItems: 'center',
            }}>
              {PHASE_ORDER.map((p, idx) => {
                const currentIdx = PHASE_ORDER.indexOf(eng.phase);
                const past = idx < currentIdx;
                const current = eng.phase === p;
                const tone: BadgeTone = current ? PHASE_TONE[p] : past ? 'success' : 'neutral';
                return (
                  <div key={p} style={{ display: 'flex', alignItems: 'center', gap: 4 }}>
                    <Badge tone={tone}>
                      {past ? '✓ ' : current ? '● ' : ''}
                      {engagementPhaseLabel(p, isAr)}
                    </Badge>
                    {idx < PHASE_ORDER.length - 1 && (
                      <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{isAr ? '←' : '→'}</span>
                    )}
                  </div>
                );
              })}
              {eng.phase === 'CANCELLED' && (
                <Badge tone="danger">{engagementPhaseLabel('CANCELLED', isAr)}</Badge>
              )}
            </div>
          </Card>

          {/* Metadata */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: 12, marginTop: 16,
          }}>
            <MetaCard icon={CalendarDays} label={isAr ? 'الفترة المُجدوَلة' : 'Planned period'}
                      value={eng.plannedStartDate && eng.plannedEndDate
                        ? `${eng.plannedStartDate.slice(0, 10)} → ${eng.plannedEndDate.slice(0, 10)}`
                        : '—'} />
            <MetaCard icon={Activity} label={isAr ? 'الفترة الفعلية' : 'Actual period'}
                      value={eng.actualStartDate
                        ? `${eng.actualStartDate.slice(0, 10)} → ${eng.actualEndDate?.slice(0, 10) ?? '—'}`
                        : '—'} />
            <MetaCard icon={Target} label={isAr ? 'رأي المراجعة' : 'Audit opinion'}
                      value={eng.opinion ?? '—'} />
            <MetaCard icon={Users} label={isAr ? 'المراجع الرئيسي' : 'Lead auditor'}
                      value={eng.leadAuditorUserId != null ? `#${eng.leadAuditorUserId}` : '—'} />
            <MetaCard icon={FileSignature} label={isAr ? 'مذكّرة التخطيط' : 'Planning memo'}
                      value={eng.planningMemoSignedOffAt
                        ? `${isAr ? 'مُعتمدة' : 'Signed off'} ${eng.planningMemoSignedOffAt.slice(0, 10)}`
                        : (isAr ? '— لم تُعتمد بعد' : '— Not signed off')} />
          </div>

          {/* Scope */}
          {(eng.scopeSummaryEn || eng.scopeSummaryAr) && (
            <Card>
              <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
                <div style={{ fontSize: 13, fontWeight: 600 }}>{isAr ? 'النطاق' : 'Scope'}</div>
              </div>
              <div style={{ padding: 12, fontSize: 12, whiteSpace: 'pre-wrap' }}>
                {isAr ? eng.scopeSummaryAr : eng.scopeSummaryEn}
              </div>
            </Card>
          )}

          {/* Milestones */}
          <Card>
            <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>
                {isAr ? 'محطّات المهمّة' : 'Milestones'}
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr ? 'مُجدوَلة مقابل الفعلية — سجّل التاريخ الفعلي عند بلوغ كل محطّة' : 'Planned vs actual — record the actual date as each checkpoint is met'}
              </div>
            </div>

            {MILESTONE_ORDER.map(type => {
              const m = eng.milestones.find(x => x.milestoneType === type);
              const completed = m?.actualDate != null;
              return (
                <div key={type} style={{
                  padding: 10, borderBottom: '1px solid var(--border)',
                  display: 'grid', gridTemplateColumns: '24px 1fr 120px 120px auto',
                  gap: 12, alignItems: 'center',
                }}>
                  {completed
                    ? <CheckCircle2 size={16} color="var(--success-fg)" />
                    : <Circle size={16} color="var(--ink-3)" />}
                  <div>
                    <div style={{ fontSize: 12, fontWeight: 600 }}>{milestoneTypeLabel(type, isAr)}</div>
                    {m?.notesEn && !isAr && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{m.notesEn}</div>}
                    {m?.notesAr && isAr && <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{m.notesAr}</div>}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    <Clock size={10} /> {isAr ? 'مُخطّط' : 'Planned'}: {m?.plannedDate?.slice(0, 10) ?? '—'}
                  </div>
                  <div style={{ fontSize: 11 }}>
                    {completed
                      ? <Badge tone="success">{m?.actualDate?.slice(0, 10)}</Badge>
                      : <Badge tone="neutral">{isAr ? '— لم يتم' : '— Pending'}</Badge>}
                  </div>
                  <div style={{ textAlign: 'end' }}>
                    <Button variant="ghost" size="sm" onClick={() => setRecordType(type)}
                      title={completed
                        ? (isAr ? 'تحديث التاريخ الفعلي' : 'Update actual date')
                        : (isAr ? 'تسجيل التاريخ الفعلي' : 'Record actual date')}>
                      <ClipboardCheck size={13} />
                      <span style={{ marginInlineStart: 4 }}>
                        {completed ? (isAr ? 'تحديث' : 'Update') : (isAr ? 'تسجيل' : 'Record')}
                      </span>
                    </Button>
                  </div>
                </div>
              );
            })}
          </Card>

          {/* Findings count */}
          {(eng.findingCount > 0 || eng.criticalFindingCount > 0) && (
            <Card>
              <div style={{ padding: 12, display: 'flex', gap: 24, fontSize: 12 }}>
                <div>
                  {isAr ? 'إجمالي الملاحظات:' : 'Total findings:'}{' '}
                  <Badge tone="warning">{eng.findingCount}</Badge>
                </div>
                <div>
                  {isAr ? 'حرجة:' : 'Critical:'}{' '}
                  <Badge tone={eng.criticalFindingCount > 0 ? 'danger' : 'neutral'}>
                    {eng.criticalFindingCount}
                  </Badge>
                </div>
              </div>
            </Card>
          )}
        </>
      )}

      {/* Documents & control mapping — engagement workpapers, report, controls tested */}
      {eng && (
        <div style={{ marginTop: 16 }}>
          <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
              <AttachmentsField entityType="IA_ENGAGEMENT" entityId={eng.id} />
              <div style={{ borderTop: '1px solid var(--border)' }} />
              <ControlMappingPicker entityType="IA_ENGAGEMENT" entityId={eng.id} />
            </div>
          </Card>
        </div>
      )}

      {/* Phase transition modal */}
      <FormModal<{ newPhase: string; noteEn: string; noteAr: string }>
        open={transitionOpen}
        size="sm"
        title={isAr
          ? `تغيير مرحلة المهمّة${eng ? ` — ${eng.engagementCode}` : ''}`
          : `Advance engagement phase${eng ? ` — ${eng.engagementCode}` : ''}`}
        fields={transitionFields}
        initial={transitionInitial}
        onClose={() => setTransitionOpen(false)}
        onSubmit={submitTransition}
        submitLabel={isAr ? 'تغيير المرحلة' : 'Advance phase'}
      />

      {/* Milestone record modal */}
      <FormModal<{ actualDate: string; notesEn: string; notesAr: string }>
        open={recordType !== null}
        size="sm"
        title={recordType
          ? (isAr
            ? `تسجيل محطّة — ${milestoneTypeLabel(recordType, true)}`
            : `Record milestone — ${milestoneTypeLabel(recordType, false)}`)
          : ''}
        fields={recordFields}
        initial={recordInitial}
        onClose={() => setRecordType(null)}
        onSubmit={submitRecord}
        submitLabel={isAr ? 'تسجيل' : 'Record'}
      />
    </Shell>
  );
}

function MetaCard({ icon: Icon, label, value }:
  { icon: typeof CalendarDays; label: string; value: string }) {
  return (
    <Card>
      <div style={{ padding: 12 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, color: 'var(--ink-3)', marginBottom: 6 }}>
          <Icon size={12} />
          <span style={{ fontSize: 10 }}>{label}</span>
        </div>
        <div style={{ fontSize: 13, fontWeight: 600 }}>{value}</div>
      </div>
    </Card>
  );
}
