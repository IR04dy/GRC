import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  Activity, CalendarDays, CheckCircle2, Circle, Clock,
  FileSignature, RefreshCw, Target, Users,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { useI18n } from '../../lib/i18n';
import {
  iaApi, engagementPhaseLabel, engagementTypeLabel, milestoneTypeLabel,
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

/**
 * Sprint 13 — Engagement detail page with phase timeline + milestones.
 *
 * <p>Renders the full lifecycle for one engagement (the "Mahimmah
 * Maydaniyyah" — المهمّة الميدانية). Phase pills walk left-to-right
 * through the state machine; milestones list the planned vs actual
 * dates per IIA 2200/2300/2400/2500 standard checkpoints.
 */
export function IaEngagementDetailPage() {
  const { id } = useParams<{ id: string }>();
  const engId = Number(id);
  const { lang } = useI18n();
  const isAr = lang === 'ar';

  const [state, setState] = useState<LoadState>('idle');
  const [eng, setEng] = useState<IaEngagement | null>(null);
  const [err, setErr] = useState<string | null>(null);

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

  useEffect(() => { if (!Number.isNaN(engId)) void load(); /* eslint-disable-next-line react-hooks/exhaustive-deps */ }, [engId]);

  if (Number.isNaN(engId)) {
    return <Shell title="Engagement"><div style={{ padding: 32, color: 'var(--danger-fg)' }}>Invalid id</div></Shell>;
  }

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
            <div style={{ padding: 12, borderBottom: '1px solid var(--border)' }}>
              <div style={{ fontSize: 13, fontWeight: 600 }}>
                {isAr ? 'دورة حياة المهمّة الميدانية' : 'Engagement lifecycle'}
              </div>
              <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                {isAr ? 'IIA 2200 / 2300 / 2400 / 2500' : 'IIA 2200 / 2300 / 2400 / 2500'}
              </div>
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
                {isAr ? 'مُجدوَلة مقابل الفعلية' : 'Planned vs actual'}
              </div>
            </div>

            {MILESTONE_ORDER.map(type => {
              const m = eng.milestones.find(x => x.milestoneType === type);
              const completed = m?.actualDate != null;
              return (
                <div key={type} style={{
                  padding: 10, borderBottom: '1px solid var(--border)',
                  display: 'grid', gridTemplateColumns: '24px 1fr 120px 120px',
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
