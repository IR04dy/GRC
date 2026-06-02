import { useEffect, useMemo, useState } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Shield, BookOpen, Gauge, Repeat, BookText,
  ArrowLeft, Save, Send, CheckCircle2, Lock, ChevronRight, ChevronDown,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  opresApi, PILLAR_LABEL, STATUS_LABEL, STATUS_TONE, MATURITY_TONE,
  MATURITY_LABEL, parseRubric,
  type AssessmentResponse, type PillarResponse, type PillarCode,
  type MaturityRubricLevel,
} from '../../lib/opres';

/**
 * Op-Res Assessment Detail — /opres/assessments/:id.
 *
 * The cell editor. One panel per pillar with current + target maturity
 * dropdowns, the SAMA rubric inline as a guide, bilingual rationale,
 * and an evidence-reference free-text field. Lifecycle actions
 * (submit / approve / close) at the top.
 *
 * Saudi-native AR throughout — operator-natural wording, not literal
 * translation. Disabled when assessment is CLOSED.
 */

const PILLAR_ICON: Record<PillarCode, typeof Shield> = {
  GOVERNANCE:      Shield,
  IBS_MAPPING:     BookOpen,
  TOLERANCES:      Gauge,
  MAPPING_TESTING: Repeat,
  LESSONS:         BookText,
};

interface CellDraft {
  pillarId: number;
  currentScore: number | '';
  targetScore:  number | '';
  rationaleEn:  string;
  rationaleAr:  string;
  evidenceReference: string;
  saving: boolean;
  expanded: boolean;
}

export function OpResAssessmentDetailPage() {
  const { id } = useParams<{ id: string }>();
  const { lang } = useI18n();
  const assessmentId = id ? parseInt(id, 10) : 0;

  const [assessment, setAssessment] = useState<AssessmentResponse | null>(null);
  const [pillars, setPillars] = useState<PillarResponse[]>([]);
  const [drafts, setDrafts] = useState<Record<number, CellDraft>>({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [actionPending, setActionPending] = useState(false);

  const reload = async () => {
    if (!assessmentId) return;
    setLoading(true);
    setError(null);
    try {
      const [a, pl] = await Promise.all([
        opresApi.getAssessment(assessmentId),
        opresApi.listPillars(),
      ]);
      setAssessment(a);
      setPillars(pl);

      // Build drafts keyed by pillarId — seed from existing cells if any.
      const byPillar = new Map<number, AssessmentResponse['cells'][number]>();
      for (const c of a.cells) byPillar.set(c.pillarId, c);
      const next: Record<number, CellDraft> = {};
      for (const p of pl) {
        const existing = byPillar.get(p.id);
        next[p.id] = {
          pillarId: p.id,
          currentScore: existing?.currentScore ?? '',
          targetScore:  existing?.targetScore  ?? '',
          rationaleEn:  existing?.rationaleEn  ?? '',
          rationaleAr:  existing?.rationaleAr  ?? '',
          evidenceReference: existing?.evidenceReference ?? '',
          saving: false,
          expanded: !existing,  // open by default if not yet scored
        };
      }
      setDrafts(next);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل التقييم' : 'Failed to load assessment'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [assessmentId]);

  const editable = assessment != null
    && assessment.status !== 'CLOSED'
    && assessment.status !== 'APPROVED';

  const updateDraft = (pillarId: number, patch: Partial<CellDraft>) =>
    setDrafts(d => ({ ...d, [pillarId]: { ...d[pillarId], ...patch } }));

  const saveCell = async (pillarId: number) => {
    if (!assessment) return;
    const d = drafts[pillarId];
    updateDraft(pillarId, { saving: true });
    try {
      await opresApi.upsertCell(assessment.id, {
        pillarId,
        currentScore: d.currentScore === '' ? undefined : Number(d.currentScore),
        targetScore:  d.targetScore  === '' ? undefined : Number(d.targetScore),
        rationaleEn:  d.rationaleEn || undefined,
        rationaleAr:  d.rationaleAr || undefined,
        evidenceReference: d.evidenceReference || undefined,
      });
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر حفظ الركيزة' : 'Failed to save pillar'));
    } finally {
      updateDraft(pillarId, { saving: false });
    }
  };

  const transition = async (
    fn: (id: number) => Promise<AssessmentResponse>,
    confirmMsg: string,
  ) => {
    if (!assessment) return;
    if (!confirm(confirmMsg)) return;
    setActionPending(true);
    try {
      await fn(assessment.id);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تنفيذ الإجراء' : 'Action failed'));
    } finally {
      setActionPending(false);
    }
  };

  const sortedPillars = useMemo(
    () => [...pillars].sort((a, b) => a.sortOrder - b.sortOrder),
    [pillars],
  );

  if (loading) {
    return (
      <Shell title="—" sub="—">
        <div style={{ padding: 24, fontSize: 13, opacity: 0.7 }}>
          {lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}
        </div>
      </Shell>
    );
  }

  if (!assessment) {
    return (
      <Shell title="—" sub="—">
        <div style={{ padding: 24, color: 'var(--danger)' }}>
          {error ?? (lang === 'ar' ? 'لم يتم العثور على التقييم' : 'Assessment not found')}
        </div>
      </Shell>
    );
  }

  const title = lang === 'ar' ? assessment.titleAr : assessment.titleEn;

  return (
    <Shell title={title} sub={`FY${assessment.fiscalYear}`}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/opres/assessments"
              style={{ color: 'var(--ink-3)', textDecoration: 'none', fontSize: 12 }}>
          <ArrowLeft size={12} style={{ verticalAlign: 'middle', marginRight: 4 }}/>
          {lang === 'ar' ? 'كل التقييمات' : 'All assessments'}
        </Link>
      </div>
      <PageHeader
        eyebrow={`${lang === 'ar' ? 'الصمود التشغيلي · FY' : 'Operational Resilience · FY'}${assessment.fiscalYear}`}
        title={title}
        sub={lang === 'ar'
          ? 'تعبئة الركائز الخمس بحسب إطار الصمود التشغيلي 2024 لدى ساما.'
          : 'Fill in the 5 pillars under SAMA Op-Res 2024.'}
        actions={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
            <Badge tone={STATUS_TONE[assessment.status]}>
              {STATUS_LABEL[assessment.status][lang === 'ar' ? 'ar' : 'en']}
            </Badge>
            {(assessment.status === 'IN_PROGRESS' || assessment.status === 'DRAFT') && (
              <Button variant="primary" Icon={Send}
                disabled={actionPending}
                onClick={() => transition(opresApi.submit,
                  lang === 'ar'
                    ? 'هل أنت متأكّد من تقديم التقييم للاعتماد؟'
                    : 'Submit this assessment for board sign-off?')}>
                {lang === 'ar' ? 'تقديم للاعتماد' : 'Submit for sign-off'}
              </Button>
            )}
            {assessment.status === 'APPROVED' && !assessment.approvedByUserId && (
              <Button variant="primary" Icon={CheckCircle2}
                disabled={actionPending}
                onClick={() => transition(opresApi.approve,
                  lang === 'ar'
                    ? 'هل أنت متأكّد من تسجيل الاعتماد؟'
                    : 'Record board approval?')}>
                {lang === 'ar' ? 'تسجيل الاعتماد' : 'Record approval'}
              </Button>
            )}
            {assessment.status === 'APPROVED' && (
              <Button variant="secondary" Icon={Lock}
                disabled={actionPending}
                onClick={() => transition(opresApi.close,
                  lang === 'ar'
                    ? 'هل أنت متأكّد من إغلاق التقييم؟ لن يكون قابلاً للتعديل بعد ذلك.'
                    : 'Close this assessment? It will become read-only.')}>
                {lang === 'ar' ? 'إغلاق' : 'Close'}
              </Button>
            )}
          </div>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* Summary KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12,
        marginBottom: 16,
      }}>
        <Kpi Icon={Shield} tone="info"
          label={lang === 'ar' ? 'الاكتمال' : 'Completeness'}
          value={assessment.completeness ?? '0/5'} />
        <Kpi Icon={Gauge}
          tone={assessment.avgCurrentScore != null && Number(assessment.avgCurrentScore) >= 3
            ? 'success' : 'warning'}
          label={lang === 'ar' ? 'متوسّط النضج الحالي' : 'Avg Current'}
          value={assessment.avgCurrentScore != null
            ? Number(assessment.avgCurrentScore).toFixed(1) : '—'} />
        <Kpi Icon={Repeat} tone="info"
          label={lang === 'ar' ? 'متوسّط النضج المستهدف' : 'Avg Target'}
          value={assessment.avgTargetScore != null
            ? Number(assessment.avgTargetScore).toFixed(1) : '—'} />
        <Kpi Icon={BookText} tone="neutral"
          label={lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due Date'}
          value={assessment.dueDate
            ? new Date(assessment.dueDate)
                .toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')
            : '—'} />
      </div>

      {/* Pillar cells */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {sortedPillars.map(p => {
          const d = drafts[p.id];
          if (!d) return null;
          const rubric: MaturityRubricLevel[] = parseRubric(p.maturityRubricJson);
          const Icon = PILLAR_ICON[p.code] || Shield;
          const curN = d.currentScore === '' ? null : Number(d.currentScore);
          const tgtN = d.targetScore  === '' ? null : Number(d.targetScore);
          const gap = (curN != null && tgtN != null) ? tgtN - curN : null;
          const desc = lang === 'ar' ? p.descriptionAr : p.descriptionEn;
          return (
            <Card key={p.id} padded
              title={`${PILLAR_LABEL[p.code][lang === 'ar' ? 'ar' : 'en']}`
                + (p.samaReference ? `  ·  SAMA Op-Res ${p.samaReference}` : '')}
              Icon={Icon}
              action={
                <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  {curN != null && (
                    <Badge tone={MATURITY_TONE[curN]}>
                      {lang === 'ar' ? 'الحالي' : 'Current'} {curN}
                    </Badge>
                  )}
                  {tgtN != null && (
                    <Badge tone="info">
                      {lang === 'ar' ? 'المستهدف' : 'Target'} {tgtN}
                    </Badge>
                  )}
                  {gap != null && gap > 0 && (
                    <Badge tone={gap >= 2 ? 'danger' : 'warning'}>
                      {lang === 'ar' ? `فجوة ${gap}` : `Gap ${gap}`}
                    </Badge>
                  )}
                  <button onClick={() => updateDraft(p.id, { expanded: !d.expanded })}
                          style={{
                            background: 'transparent', border: 'none',
                            cursor: 'pointer', padding: 4,
                          }}
                          aria-label={d.expanded ? 'collapse' : 'expand'}>
                    {d.expanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                  </button>
                </div>
              }>

              {desc && (
                <div style={{ fontSize: 12, opacity: 0.75, marginBottom: 10,
                              lineHeight: 1.5 }}>
                  {desc}
                </div>
              )}

              {d.expanded && (
                <>
                  {/* Maturity rubric (read-only guide) */}
                  {rubric.length > 0 && (
                    <div style={{ marginBottom: 12 }}>
                      <div style={{ fontSize: 11, fontWeight: 600,
                                    marginBottom: 6, opacity: 0.75 }}>
                        {lang === 'ar' ? 'مقياس النضج (مرجع)' : 'Maturity ladder (reference)'}
                      </div>
                      <div style={{ display: 'grid',
                                    gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
                                    gap: 6 }}>
                        {rubric.map(r => (
                          <div key={r.level}
                               style={{
                                 border: '1px solid var(--border)',
                                 borderRadius: 6, padding: 8,
                                 fontSize: 11, lineHeight: 1.4,
                                 background: curN === r.level
                                   ? 'var(--surface-2)' : 'transparent',
                               }}>
                            <div style={{ display: 'flex', alignItems: 'center',
                                          gap: 6, marginBottom: 4 }}>
                              <Badge tone={MATURITY_TONE[r.level]}>{r.level}</Badge>
                              <strong style={{ fontSize: 11 }}>
                                {MATURITY_LABEL[r.level][lang === 'ar' ? 'ar' : 'en']}
                              </strong>
                            </div>
                            <div style={{ opacity: 0.8 }}>
                              {lang === 'ar' ? r.ar : r.en}
                            </div>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}

                  {/* Score selectors */}
                  <div style={{ display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
                                gap: 12, marginBottom: 12 }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>
                        {lang === 'ar' ? 'النضج الحالي' : 'Current maturity'}
                      </span>
                      <select disabled={!editable}
                        value={d.currentScore}
                        onChange={e => updateDraft(p.id, {
                          currentScore: e.target.value === '' ? '' : Number(e.target.value),
                        })}
                        style={{ padding: 6, border: '1px solid var(--border)',
                                 borderRadius: 4, fontSize: 13 }}>
                        <option value="">— {lang === 'ar' ? 'لم تُحدَّد' : 'unset'} —</option>
                        {[1,2,3,4,5].map(n => (
                          <option key={n} value={n}>
                            {n} · {MATURITY_LABEL[n][lang === 'ar' ? 'ar' : 'en']}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>
                        {lang === 'ar' ? 'النضج المستهدف' : 'Target maturity'}
                      </span>
                      <select disabled={!editable}
                        value={d.targetScore}
                        onChange={e => updateDraft(p.id, {
                          targetScore: e.target.value === '' ? '' : Number(e.target.value),
                        })}
                        style={{ padding: 6, border: '1px solid var(--border)',
                                 borderRadius: 4, fontSize: 13 }}>
                        <option value="">— {lang === 'ar' ? 'لم يُحدَّد' : 'unset'} —</option>
                        {[1,2,3,4,5].map(n => (
                          <option key={n} value={n}>
                            {n} · {MATURITY_LABEL[n][lang === 'ar' ? 'ar' : 'en']}
                          </option>
                        ))}
                      </select>
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>
                        {lang === 'ar' ? 'مرجع الدليل' : 'Evidence reference'}
                      </span>
                      <input type="text" maxLength={512} disabled={!editable}
                        value={d.evidenceReference}
                        onChange={e => updateDraft(p.id, { evidenceReference: e.target.value })}
                        placeholder={lang === 'ar'
                          ? 'مثال: BCM-2026-Q1-EVID-1، شاريبوينت/درايف…'
                          : 'e.g. BCM-2026-Q1-EVID-1, SharePoint/Drive link…'}
                        style={{ padding: 6, border: '1px solid var(--border)',
                                 borderRadius: 4, fontSize: 13 }} />
                    </label>
                  </div>

                  <div style={{ display: 'grid',
                                gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
                                gap: 12, marginBottom: 12 }}>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>
                        {lang === 'ar' ? 'المبرّر (إنجليزي)' : 'Rationale (English)'}
                      </span>
                      <textarea rows={4} maxLength={4000} disabled={!editable}
                        value={d.rationaleEn}
                        onChange={e => updateDraft(p.id, { rationaleEn: e.target.value })}
                        placeholder="What evidence supports your current and target maturity claims?"
                        style={{ padding: 6, border: '1px solid var(--border)',
                                 borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
                    </label>
                    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
                      <span style={{ fontSize: 12, fontWeight: 600 }}>
                        {lang === 'ar' ? 'المبرّر (عربي)' : 'Rationale (Arabic)'}
                      </span>
                      <textarea rows={4} maxLength={4000} dir="rtl" disabled={!editable}
                        value={d.rationaleAr}
                        onChange={e => updateDraft(p.id, { rationaleAr: e.target.value })}
                        placeholder="ما الأدلّة الداعمة للنضج الحالي والمستهدف؟"
                        style={{ padding: 6, border: '1px solid var(--border)',
                                 borderRadius: 4, fontSize: 13, fontFamily: 'inherit' }} />
                    </label>
                  </div>

                  <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button variant="primary" Icon={Save}
                      disabled={!editable || d.saving}
                      onClick={() => saveCell(p.id)}>
                      {d.saving
                        ? (lang === 'ar' ? 'جارٍ الحفظ…' : 'Saving…')
                        : (lang === 'ar' ? 'حفظ الركيزة' : 'Save pillar')}
                    </Button>
                  </div>
                </>
              )}
            </Card>
          );
        })}
      </div>

      {/* Executive summary panel — visible once approved */}
      {(assessment.executiveSummaryEn || assessment.executiveSummaryAr) && (
        <div style={{ marginTop: 16 }}>
          <Card padded title={lang === 'ar' ? 'الملخّص التنفيذي' : 'Executive summary'}>
            <div style={{ fontSize: 13, lineHeight: 1.6,
                          whiteSpace: 'pre-wrap' }}>
              {lang === 'ar'
                ? (assessment.executiveSummaryAr ?? '—')
                : (assessment.executiveSummaryEn ?? '—')}
            </div>
          </Card>
        </div>
      )}

      {/* Documents & control mapping — assessment report, evidence, cross-framework controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="OPRES_ASSESSMENT" entityId={assessment.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="OPRES_ASSESSMENT" entityId={assessment.id} />
          </div>
        </Card>
      </div>
    </Shell>
  );
}
