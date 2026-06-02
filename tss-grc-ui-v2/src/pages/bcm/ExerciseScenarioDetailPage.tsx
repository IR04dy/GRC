import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ChevronLeft, PlayCircle, Clock, Users, Target, CheckCircle2,
  Eye, BookOpen, AlertTriangle, Megaphone, Landmark, Info,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { ApiError } from '../../lib/api';
import {
  getScenario,
  instantiateScenario,
  type ScenarioDetail,
  type InjectType,
  SCENARIO_TYPE_LABELS,
  COMPLEXITY_LABELS,
  INJECT_TYPE_LABELS,
  complexityTone,
  scenarioTypeTone,
  injectTypeTone,
  fmtDuration,
  fmtTimeOffset,
  parseFrameworks,
  frameworkLabel,
} from '../../lib/scenarioLibrary';

/**
 * Slice 10-A — Exercise Scenario detail page + instantiation wizard.
 *
 * <p>Layout:
 * <ul>
 *   <li>Hero — title (EN/AR), code, badges (type/complexity), framework chips</li>
 *   <li>"Run as exercise" CTA opens the inline wizard form</li>
 *   <li>Learning objectives + success criteria (bilingual blocks)</li>
 *   <li>Recommended observers</li>
 *   <li>Inject timeline — vertical timeline ordered by time_offset_minutes
 *       with type-coloured bullets and bilingual inject text + expected
 *       action</li>
 * </ul>
 */
export function ExerciseScenarioDetailPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const scenarioId = Number(id);

  const [s, setS] = useState<ScenarioDetail | null>(null);
  const [loading, setLoading] = useState(true);

  // Wizard state
  const [wizardOpen, setWizardOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  // Wizard inputs
  const [plannedDate, setPlannedDate] = useState<string>('');
  const [leadFacilitator, setLeadFacilitator] = useState<string>('');
  const [observers, setObservers] = useState<string>('');
  const [participants, setParticipants] = useState<string>('');
  const [exerciseNameEn, setExerciseNameEn] = useState<string>('');
  const [exerciseNameAr, setExerciseNameAr] = useState<string>('');

  // ─── Load ──────────────────────────────────────────────────────────
  useEffect(() => {
    if (!Number.isFinite(scenarioId)) return;
    let cancelled = false;
    void (async () => {
      setLoading(true);
      try {
        const detail = await getScenario(scenarioId);
        if (!cancelled) {
          setS(detail);
          // Pre-fill wizard with sensible defaults from the scenario.
          setObservers(detail.recommendedObservers ?? '');
          setParticipants(detail.targetAudience ?? '');
          setExerciseNameEn(detail.titleEn);
          setExerciseNameAr(detail.titleAr ?? '');
        }
      } catch (e) {
        if (!cancelled) toast.danger(e instanceof ApiError ? e.message
          : (isAr ? 'فشل تحميل السيناريو' : 'Failed to load scenario'));
      } finally {
        if (!cancelled) setLoading(false);
      }
    })();
    return () => { cancelled = true; };
  }, [scenarioId]); // eslint-disable-line react-hooks/exhaustive-deps

  async function instantiate() {
    if (!s) return;
    if (!plannedDate) {
      toast.warning(isAr ? 'حدد التاريخ المخطط' : 'Pick a planned date');
      return;
    }
    if (!leadFacilitator.trim()) {
      toast.warning(isAr ? 'حدد قائد الجلسة' : 'Enter the lead facilitator');
      return;
    }
    setSubmitting(true);
    try {
      const out = await instantiateScenario(s.id, {
        plannedDate,
        leadFacilitator: leadFacilitator.trim(),
        exerciseNameEn: exerciseNameEn.trim() || undefined,
        exerciseNameAr: exerciseNameAr.trim() || undefined,
        observers: observers.trim() || undefined,
        participants: participants.trim() || undefined,
      });
      toast.success(isAr
        ? `تم إنشاء التمرين — ${out.injectCount} حقن منسوخة`
        : `Exercise created — ${out.injectCount} injects attached`);
      navigate('/bcm');   // BCM workspace lists the new exercise
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إنشاء التمرين' : 'Failed to create exercise'));
    } finally {
      setSubmitting(false);
    }
  }

  if (!Number.isFinite(scenarioId)) {
    return (
      <Shell>
        <InlineEmpty text={isAr ? 'رمز السيناريو غير صالح' : 'Invalid scenario id'} />
      </Shell>
    );
  }
  if (loading || !s) {
    return (
      <Shell>
        <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
      </Shell>
    );
  }

  const frameworks = parseFrameworks(s.frameworkAlignment);

  return (
    <Shell>
      <PageHeader
        eyebrow={s.scenarioCode}
        title={isAr ? (s.titleAr || s.titleEn) : s.titleEn}
        sub={isAr ? s.descriptionAr : s.descriptionEn}
        actions={
          <div className="cluster" style={{ gap: 8 }}>
            <Button
              variant="secondary"
              Icon={ChevronLeft}
              onClick={() => navigate('/bcm/scenarios')}
            >
              {isAr ? 'عودة للمكتبة' : 'Back to library'}
            </Button>
            {!wizardOpen && (
              <Button
                Icon={PlayCircle}
                onClick={() => setWizardOpen(true)}
              >
                {isAr ? 'تنفيذ كتمرين' : 'Instantiate this exercise'}
              </Button>
            )}
          </div>
        }
      />

      {/* Badges + chips */}
      <Card>
        <div className="cluster" style={{ gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
          {s.scenarioType && (
            <Badge tone={scenarioTypeTone(s.scenarioType)}>
              {tlabel(SCENARIO_TYPE_LABELS, s.scenarioType, isAr)}
            </Badge>
          )}
          {s.complexity && (
            <Badge tone={complexityTone(s.complexity)}>
              {tlabel(COMPLEXITY_LABELS, s.complexity, isAr)}
            </Badge>
          )}
          <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
            <Clock size={13} style={{ verticalAlign: 'text-top' }} />
            {' '}
            {fmtDuration(s.recommendedDurationMinutes ?? s.durationMinutes, isAr)}
          </span>
          {s.targetAudience && (
            <span style={{ fontSize: 13, color: 'var(--ink-3)' }}>
              <Users size={13} style={{ verticalAlign: 'text-top' }} />
              {' '}
              {s.targetAudience}
            </span>
          )}
          {frameworks.length > 0 && (
            <div className="cluster" style={{ gap: 4, flexWrap: 'wrap' }}>
              {frameworks.map(code => (
                <span key={code} style={{
                  fontSize: 11, padding: '2px 8px',
                  background: 'var(--surface-2, #f3f4f6)',
                  borderRadius: 3,
                  color: 'var(--ink-3)',
                }}>
                  {frameworkLabel(code, isAr)}
                </span>
              ))}
            </div>
          )}
        </div>
      </Card>

      {/* Instantiation wizard */}
      {wizardOpen && (
        <Card
          title={isAr ? 'إنشاء تمرين من هذا السيناريو'
                     : 'Instantiate as runnable exercise'}
          Icon={PlayCircle}
        >
          <div className="stack" style={{ gap: 'var(--sp-4)' }}>
            <p style={{ fontSize: 13, color: 'var(--ink-3)', margin: 0 }}>
              {isAr
                ? 'سننسخ الأهداف ومعايير النجاح وتسلسل الحقن إلى تمرين جديد قابل للتنفيذ في وحدتك التنظيمية.'
                : 'We will copy the objectives, success criteria, and inject sequence onto a runnable exercise in your tenant.'}
            </p>

            <div style={{
              display: 'grid',
              gridTemplateColumns: '1fr 1fr',
              gap: 'var(--sp-4)',
            }}>
              <div>
                <label style={lblStyle}>
                  {isAr ? 'التاريخ المخطط *' : 'Planned date *'}
                </label>
                <input
                  type="date"
                  className="input"
                  style={{ width: '100%' }}
                  value={plannedDate}
                  onChange={e => setPlannedDate(e.target.value)}
                />
              </div>
              <div>
                <label style={lblStyle}>
                  {isAr ? 'قائد الجلسة *' : 'Lead facilitator *'}
                </label>
                <input
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  placeholder={isAr
                    ? 'مثال: مها العنزي — مدير الاستمرارية'
                    : 'e.g. Maha Al-Anazi — BCM Manager'}
                  value={leadFacilitator}
                  onChange={e => setLeadFacilitator(e.target.value)}
                />
              </div>

              <div>
                <label style={lblStyle}>
                  {isAr ? 'اسم التمرين (إنجليزي)' : 'Exercise name (EN)'}
                </label>
                <input
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  value={exerciseNameEn}
                  onChange={e => setExerciseNameEn(e.target.value)}
                />
              </div>
              <div>
                <label style={lblStyle}>
                  {isAr ? 'اسم التمرين (عربي)' : 'Exercise name (AR)'}
                </label>
                <input
                  type="text"
                  className="input"
                  style={{ width: '100%' }}
                  value={exerciseNameAr}
                  onChange={e => setExerciseNameAr(e.target.value)}
                />
              </div>

              <div style={{ gridColumn: '1 / -1' }}>
                <label style={lblStyle}>
                  {isAr ? 'المراقبون' : 'Observers'}
                </label>
                <textarea
                  className="input"
                  style={{ width: '100%', minHeight: 60 }}
                  placeholder={isAr
                    ? 'مثال: مراقب من المراجعة الداخلية، مسؤول علاقات ساما…'
                    : 'e.g. Internal Audit observer, SAMA liaison officer…'}
                  value={observers}
                  onChange={e => setObservers(e.target.value)}
                />
              </div>
              <div style={{ gridColumn: '1 / -1' }}>
                <label style={lblStyle}>
                  {isAr ? 'المشاركون' : 'Participants'}
                </label>
                <textarea
                  className="input"
                  style={{ width: '100%', minHeight: 60 }}
                  placeholder={isAr
                    ? 'الأدوار المُوصى بها معبأة مسبقاً — عدّل القائمة بأسماء فعلية.'
                    : 'Recommended roles pre-filled — replace with actual names.'}
                  value={participants}
                  onChange={e => setParticipants(e.target.value)}
                />
              </div>
            </div>

            <div className="cluster" style={{ gap: 8, justifyContent: 'flex-end' }}>
              <Button
                variant="secondary"
                onClick={() => setWizardOpen(false)}
                disabled={submitting}
              >
                {isAr ? 'إلغاء' : 'Cancel'}
              </Button>
              <Button
                Icon={PlayCircle}
                onClick={instantiate}
                disabled={submitting}
              >
                {submitting
                  ? (isAr ? 'جارٍ الإنشاء…' : 'Creating…')
                  : (isAr ? 'إنشاء التمرين' : 'Create exercise')}
              </Button>
            </div>
          </div>
        </Card>
      )}

      {/* Two-column: objectives + success criteria */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: 'var(--sp-6)',
      }}>
        <Card
          title={isAr ? 'أهداف التعلم' : 'Learning objectives'}
          Icon={Target}
        >
          {(isAr ? s.learningObjectivesAr : s.learningObjectivesEn) ? (
            <pre style={prose}>{isAr ? s.learningObjectivesAr : s.learningObjectivesEn}</pre>
          ) : (
            <InlineEmpty text={isAr ? 'لم تُسجَّل بعد' : 'Not recorded'} compact />
          )}
        </Card>

        <Card
          title={isAr ? 'معايير النجاح' : 'Success criteria'}
          Icon={CheckCircle2}
        >
          {(isAr ? s.successCriteriaAr : s.successCriteriaEn) ? (
            <pre style={prose}>{isAr ? s.successCriteriaAr : s.successCriteriaEn}</pre>
          ) : (
            <InlineEmpty text={isAr ? 'لم تُسجَّل بعد' : 'Not recorded'} compact />
          )}
        </Card>
      </div>

      {/* Observers */}
      {s.recommendedObservers && (
        <Card title={isAr ? 'المراقبون الموصى بهم' : 'Recommended observers'} Icon={Eye}>
          <p style={{ margin: 0, fontSize: 14 }}>{s.recommendedObservers}</p>
        </Card>
      )}

      {/* Inject timeline */}
      <Card
        title={isAr
          ? `تسلسل الحقن (${s.injects.length})`
          : `Inject sequence (${s.injects.length})`}
        Icon={BookOpen}
      >
        {s.injects.length === 0 ? (
          <InlineEmpty text={isAr
            ? 'لم تُضَف حقن لهذا السيناريو بعد'
            : 'No injects added to this scenario yet'} />
        ) : (
          <div className="stack" style={{ gap: 0 }}>
            {s.injects.map((inj, idx) => (
              <InjectRow
                key={inj.id}
                inj={inj}
                isAr={isAr}
                isLast={idx === s.injects.length - 1}
              />
            ))}
          </div>
        )}
      </Card>

      {/* Documents & control mapping — scenario script, exercise evidence, controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={isAr ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="EXERCISE_SCENARIO" entityId={s.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="EXERCISE_SCENARIO" entityId={s.id} />
          </div>
        </Card>
      </div>
    </Shell>
  );
}

// ─── Inject timeline row ───────────────────────────────────────────────

function InjectRow({
  inj, isAr, isLast,
}: {
  inj: { id: number; injectOrder: number; timeOffsetMinutes: number; injectType: InjectType;
        injectTextEn: string; injectTextAr?: string;
        expectedActionEn?: string; expectedActionAr?: string };
  isAr: boolean;
  isLast: boolean;
}) {
  const Icon = injectTypeIcon(inj.injectType);
  return (
    <div style={{
      display: 'grid',
      gridTemplateColumns: 'auto 1fr',
      gap: 'var(--sp-4)',
      position: 'relative',
      paddingBottom: isLast ? 0 : 'var(--sp-6)',
    }}>
      {/* Bullet + vertical line */}
      <div style={{ position: 'relative', width: 28, display: 'flex', justifyContent: 'center' }}>
        <div style={{
          width: 28, height: 28,
          borderRadius: '50%',
          background: 'var(--surface-2, #f3f4f6)',
          color: 'var(--ink-1)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          border: '2px solid var(--border)',
          zIndex: 1,
        }}>
          <Icon size={14} />
        </div>
        {!isLast && (
          <div style={{
            position: 'absolute',
            top: 28, bottom: -8,
            left: '50%',
            width: 2,
            background: 'var(--border)',
            transform: 'translateX(-1px)',
          }} />
        )}
      </div>

      {/* Body */}
      <div>
        <div className="cluster" style={{ gap: 8, alignItems: 'baseline', marginBottom: 4 }}>
          <span style={{ fontSize: 13, fontWeight: 600 }}>
            {fmtTimeOffset(inj.timeOffsetMinutes, isAr)}
          </span>
          <Badge tone={injectTypeTone(inj.injectType)}>
            {tlabel(INJECT_TYPE_LABELS, inj.injectType, isAr)}
          </Badge>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            #{inj.injectOrder}
          </span>
        </div>
        <p style={{ margin: 0, fontSize: 13, lineHeight: 1.55 }}>
          {isAr ? (inj.injectTextAr || inj.injectTextEn) : inj.injectTextEn}
        </p>
        {(isAr ? inj.expectedActionAr : inj.expectedActionEn) && (
          <div style={{
            marginTop: 6,
            padding: '6px 10px',
            background: 'var(--surface-2, #f3f4f6)',
            borderInlineStart: '3px solid var(--accent, #2563eb)',
            borderRadius: 3,
            fontSize: 12,
          }}>
            <strong>{isAr ? 'الإجراء المتوقع: ' : 'Expected action: '}</strong>
            {isAr ? inj.expectedActionAr : inj.expectedActionEn}
          </div>
        )}
      </div>
    </div>
  );
}

function injectTypeIcon(t: InjectType) {
  switch (t) {
    case 'INFORMATION': return Info;
    case 'ESCALATION':  return AlertTriangle;
    case 'DECISION':    return Target;
    case 'MEDIA':       return Megaphone;
    case 'REGULATOR':   return Landmark;
  }
}

// ─── Local style helpers ──────────────────────────────────────────────

const lblStyle: React.CSSProperties = {
  display: 'block', fontSize: 12, fontWeight: 600,
  marginBottom: 4, color: 'var(--ink-3)',
};

const prose: React.CSSProperties = {
  margin: 0, fontFamily: 'inherit', fontSize: 13, lineHeight: 1.6,
  whiteSpace: 'pre-wrap', wordBreak: 'break-word',
};
