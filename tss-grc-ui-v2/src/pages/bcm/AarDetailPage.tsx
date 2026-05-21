import { useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, CheckCheck, FileCheck2, Save, Send, ShieldOff,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadableSection } from '../../components/ui/LoadableSection';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useLoadable } from '../../lib/useLoadable';
import { aar, type CrisisAar, type AarStatus } from '../../lib/aar';

/**
 * NCEMA After-Action Report detail page.
 *
 * <p>Backend exposes:
 *   GET    /api/v1/bcm/aars/{id}
 *   PUT    /api/v1/bcm/aars/{id}             (editable in DRAFT / UNDER_REVIEW)
 *   POST   /api/v1/bcm/aars/{id}/transitions/{target}
 *
 * <p>This page renders the 7 markdown sections (exec summary, timeline,
 * decisions, what-worked, what-failed, root-cause, recommendations) +
 * regulatory notifications + status-machine actions in a single column.
 * Editable in DRAFT / UNDER_REVIEW; read-only in APPROVED / PUBLISHED /
 * RESCINDED.
 */
const STATUS_TONE: Record<AarStatus, BadgeTone> = {
  DRAFT:        'neutral',
  UNDER_REVIEW: 'info',
  APPROVED:     'warning',
  PUBLISHED:    'success',
  RESCINDED:    'danger',
};

export function AarDetailPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  const aarId = Number(id);

  const aarL = useLoadable<CrisisAar>(() => aar.get(aarId), { skip: !aarId });

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Partial<CrisisAar>>({});
  const [busy, setBusy] = useState(false);

  function startEdit(a: CrisisAar) {
    setDraft({ ...a });
    setEditing(true);
  }
  function cancelEdit() {
    setDraft({});
    setEditing(false);
  }
  function setField<K extends keyof CrisisAar>(key: K, value: CrisisAar[K]) {
    setDraft(d => ({ ...d, [key]: value }));
  }

  async function save() {
    setBusy(true);
    try {
      await aar.update(aarId, draft);
      toast.success(isAr ? 'تم حفظ التعديلات' : 'Changes saved');
      setEditing(false);
      void aarL.reload();
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setBusy(false); }
  }

  async function transition(target: AarStatus) {
    setBusy(true);
    try {
      await aar.transition(aarId, target);
      toast.success(isAr ? `تم الانتقال إلى ${target}` : `Transitioned to ${target}`);
      void aarL.reload();
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setBusy(false); }
  }

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'تقرير ما بعد الحدث' : 'After-Action Report'}
        sub={isAr ? 'وفق إطار NCEMA' : 'NCEMA-aligned post-event report'}
        eyebrow={isAr ? 'استمرارية الأعمال' : 'BCM'}
      />

      <div style={{ marginBottom: 16 }}>
        <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/buyer-gap')}>
          {isAr ? 'العودة' : 'Back to list'}
        </Button>
      </div>

      <LoadableSection
        loading={aarL.loading}
        error={aarL.error}
        onRetry={aarL.reload}
        data={aarL.data}
        emptyTitle={isAr ? 'لم يتم العثور على التقرير' : 'AAR not found'}
      >
        {(a) => {
          const editable = a.status === 'DRAFT' || a.status === 'UNDER_REVIEW';
          const current = editing ? { ...a, ...draft } : a;

          return (
            <>
              {/* Header card */}
              <Card title={current.reportCode} Icon={FileCheck2}
                    action={<Badge tone={STATUS_TONE[a.status]}>{a.status}</Badge>}>
                {editing ? (
                  <div className="wf-form-grid">
                    <Field label={isAr ? 'العنوان (إنجليزي)' : 'Title (English)'} required>
                      <input className="input" value={current.titleEn ?? ''}
                             onChange={e => setField('titleEn', e.target.value)}/>
                    </Field>
                    <Field label={isAr ? 'العنوان (عربي)' : 'Title (Arabic)'}>
                      <input className="input" value={current.titleAr ?? ''}
                             onChange={e => setField('titleAr', e.target.value)}/>
                    </Field>
                  </div>
                ) : (
                  <div>
                    <div style={{ fontSize: 17, fontWeight: 600 }}>{a.titleEn}</div>
                    {a.titleAr && (
                      <div className="text-sm" style={{ color: 'var(--ink-3)', marginTop: 4 }} dir="rtl">
                        {a.titleAr}
                      </div>
                    )}
                  </div>
                )}

                <div style={{ display: 'flex', gap: 8, marginTop: 16, flexWrap: 'wrap' }}>
                  {!editing && editable && (
                    <Button variant="secondary" onClick={() => startEdit(a)}>
                      {isAr ? 'تعديل' : 'Edit draft'}
                    </Button>
                  )}
                  {editing && (
                    <>
                      <Button variant="primary" Icon={Save} disabled={busy} onClick={save}>
                        {isAr ? 'حفظ' : 'Save'}
                      </Button>
                      <Button variant="ghost" onClick={cancelEdit}>
                        {isAr ? 'إلغاء' : 'Cancel'}
                      </Button>
                    </>
                  )}

                  {!editing && a.status === 'DRAFT' && (
                    <Button variant="secondary" Icon={Send} disabled={busy}
                            onClick={() => transition('UNDER_REVIEW')}>
                      {isAr ? 'إرسال للمراجعة' : 'Submit for review'}
                    </Button>
                  )}
                  {!editing && a.status === 'UNDER_REVIEW' && (
                    <Button variant="primary" Icon={CheckCheck} disabled={busy}
                            onClick={() => transition('APPROVED')}>
                      {isAr ? 'اعتماد' : 'Approve'}
                    </Button>
                  )}
                  {!editing && a.status === 'APPROVED' && (
                    <Button variant="primary" Icon={Send} disabled={busy}
                            onClick={() => transition('PUBLISHED')}>
                      {isAr ? 'نشر' : 'Publish'}
                    </Button>
                  )}
                  {!editing && (a.status === 'APPROVED' || a.status === 'PUBLISHED') && (
                    <Button variant="danger" Icon={ShieldOff} disabled={busy}
                            onClick={() => transition('RESCINDED')}>
                      {isAr ? 'إلغاء النشر' : 'Rescind'}
                    </Button>
                  )}
                </div>
              </Card>

              {/* Markdown body sections */}
              <div style={{ marginTop: 16, display: 'grid', gap: 12 }}>
                <Section title={isAr ? 'الملخص التنفيذي' : 'Executive summary'}
                  editable={editing} valueEn={current.executiveSummaryEn} valueAr={current.executiveSummaryAr}
                  onChange={(en, ar) => { setField('executiveSummaryEn', en); setField('executiveSummaryAr', ar); }} />
                <Section title={isAr ? 'الجدول الزمني' : 'Timeline'}
                  editable={editing} valueEn={current.timelineMdEn} valueAr={current.timelineMdAr} rows={10}
                  onChange={(en, ar) => { setField('timelineMdEn', en); setField('timelineMdAr', ar); }} />
                <Section title={isAr ? 'القرارات' : 'Decisions'}
                  editable={editing} valueEn={current.decisionsMdEn} valueAr={current.decisionsMdAr} rows={8}
                  onChange={(en, ar) => { setField('decisionsMdEn', en); setField('decisionsMdAr', ar); }} />
                <Section title={isAr ? 'ما نجح' : 'What worked'}
                  editable={editing} valueEn={current.whatWorkedMdEn} valueAr={current.whatWorkedMdAr}
                  onChange={(en, ar) => { setField('whatWorkedMdEn', en); setField('whatWorkedMdAr', ar); }} />
                <Section title={isAr ? 'ما فشل' : 'What failed'}
                  editable={editing} valueEn={current.whatFailedMdEn} valueAr={current.whatFailedMdAr}
                  onChange={(en, ar) => { setField('whatFailedMdEn', en); setField('whatFailedMdAr', ar); }} />
                <Section title={isAr ? 'السبب الجذري' : 'Root cause'}
                  editable={editing} valueEn={current.rootCauseMdEn} valueAr={current.rootCauseMdAr}
                  onChange={(en, ar) => { setField('rootCauseMdEn', en); setField('rootCauseMdAr', ar); }} />
                <Section title={isAr ? 'التوصيات' : 'Recommendations'}
                  editable={editing} valueEn={current.recommendationsMdEn} valueAr={current.recommendationsMdAr}
                  onChange={(en, ar) => { setField('recommendationsMdEn', en); setField('recommendationsMdAr', ar); }} />
                <Section title={isAr ? 'الإشعارات التنظيمية' : 'Regulatory notifications'}
                  editable={editing} valueEn={current.regulatoryNotificationsMd} singleLang rows={6}
                  onChange={(en) => setField('regulatoryNotificationsMd', en)} />
              </div>
            </>
          );
        }}
      </LoadableSection>
    </Shell>
  );
}

function Field({ label, required, children }: {
  label: string; required?: boolean; children: React.ReactNode;
}) {
  return (
    <label className="wf-field wf-field-full">
      <span className="wf-label">
        {label}{required && <span className="wf-required"> *</span>}
      </span>
      {children}
    </label>
  );
}

function Section({
  title, valueEn, valueAr, editable, onChange, rows = 5, singleLang = false,
}: {
  title: string;
  valueEn?: string | null;
  valueAr?: string | null;
  editable: boolean;
  onChange: (en: string, ar?: string) => void;
  rows?: number;
  singleLang?: boolean;
}) {
  const hasContent = (valueEn && valueEn.trim()) || (valueAr && valueAr.trim());
  return (
    <Card title={title}>
      {editable ? (
        <div className="wf-form-grid">
          <Field label="English">
            <textarea className="input" rows={rows} value={valueEn ?? ''}
                      onChange={e => onChange(e.target.value, valueAr ?? undefined)} />
          </Field>
          {!singleLang && (
            <Field label="العربية">
              <textarea className="input" rows={rows} value={valueAr ?? ''} dir="rtl"
                        onChange={e => onChange(valueEn ?? '', e.target.value)} />
            </Field>
          )}
        </div>
      ) : hasContent ? (
        <>
          {valueEn && (
            <pre className="aar-md-pre">{valueEn}</pre>
          )}
          {valueAr && !singleLang && (
            <pre className="aar-md-pre" dir="rtl" style={{ marginTop: 12 }}>{valueAr}</pre>
          )}
        </>
      ) : (
        <p className="text-xs" style={{ color: 'var(--ink-3)', padding: 8 }}>
          (empty)
        </p>
      )}
    </Card>
  );
}
