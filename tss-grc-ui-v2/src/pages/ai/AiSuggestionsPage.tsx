import { useEffect, useMemo, useState } from 'react';
import {
  Activity, AlertOctagon, CheckCheck, FileCheck2, Sparkles, X,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { LoadableSection } from '../../components/ui/LoadableSection';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { ai, type AiFeature, type AiStatus, type AiSuggestion } from '../../lib/ai';

/**
 * AI Suggestions admin page — surfaces every draft/accepted/rejected
 * suggestion from {@code ai.ai_suggestions} so reviewers can:
 *   - Filter by feature (5 categories)
 *   - Inspect input + output JSON pretty-printed
 *   - Accept or reject inline with an optional reviewer note
 *
 * <p>Backend endpoints:
 *   GET  /api/v1/ai/suggestions?feature=X
 *   POST /api/v1/ai/suggestions/{id}/review  {decision, reviewerNote}
 */
const FEATURES: { code: AiFeature; en: string; ar: string }[] = [
  { code: 'RISK_DESCRIPTION',           en: 'Risk description',     ar: 'وصف الخطر' },
  { code: 'BIA_RTO_RPO',                en: 'BIA RTO/RPO',          ar: 'أهداف التعافي' },
  { code: 'EVIDENCE_QUALITY',           en: 'Evidence quality',     ar: 'جودة الأدلة' },
  { code: 'INCIDENT_ROOT_CAUSE',        en: 'Incident RCA',         ar: 'تحليل السبب الجذري' },
  { code: 'BOARD_SUMMARY',              en: 'Board summary',        ar: 'ملخص المجلس' },
  { code: 'WORKFLOW_SUBMISSION_REVIEW', en: 'Workflow submission review', ar: 'إرشاد مراجعة السجل' },
];

const STATUS_TONE: Record<AiStatus, BadgeTone> = {
  DRAFT:    'warning',
  ACCEPTED: 'success',
  REJECTED: 'danger',
  EXPIRED:  'neutral',
};

export function AiSuggestionsPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [feature, setFeature] = useState<AiFeature>('BOARD_SUMMARY');
  const [items, setItems] = useState<AiSuggestion[] | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [reviewNote, setReviewNote] = useState<Record<number, string>>({});

  async function reload() {
    setLoading(true); setError(null);
    try {
      const rows = await ai.listByFeature(feature);
      setItems(rows);
    } catch (e) {
      setError((e as Error).message);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); /* eslint-disable-next-line */ }, [feature]);

  async function review(id: number, decision: 'ACCEPTED' | 'REJECTED') {
    setBusyId(id);
    try {
      await ai.review(id, { decision, reviewerNote: reviewNote[id] });
      toast.success(isAr ? `تم ${decision === 'ACCEPTED' ? 'قبول' : 'رفض'} الاقتراح`
                        : `Suggestion ${decision.toLowerCase()}`);
      void reload();
    } catch (e) {
      toast.danger((e as Error).message);
    } finally { setBusyId(null); }
  }

  const counts = useMemo(() => {
    if (!items) return { draft: 0, accepted: 0, rejected: 0 };
    return {
      draft:    items.filter(s => s.status === 'DRAFT').length,
      accepted: items.filter(s => s.status === 'ACCEPTED').length,
      rejected: items.filter(s => s.status === 'REJECTED').length,
    };
  }, [items]);

  return (
    <Shell>
      <PageHeader
        title={isAr ? 'اقتراحات الذكاء الاصطناعي' : 'AI Suggestions'}
        sub={isAr ? '٥ خصائص — كل اقتراح يحتاج مراجعة بشرية قبل تطبيقه'
                  : '5 features — every suggestion needs human review before it changes a record'}
        eyebrow={isAr ? 'الذكاء الاصطناعي' : 'AI module'}
      />

      {/* Feature picker */}
      <div className="wf-hub-filters" style={{ marginTop: 20 }}>
        <span className="wf-label" style={{ alignSelf: 'center' }}>
          {isAr ? 'الميزة:' : 'Feature:'}
        </span>
        {FEATURES.map(f => (
          <Button key={f.code}
                  variant={feature === f.code ? 'primary' : 'ghost'}
                  size="sm"
                  Icon={iconFor(f.code)}
                  onClick={() => setFeature(f.code)}>
            {isAr ? f.ar : f.en}
          </Button>
        ))}
      </div>

      {/* Stats row */}
      {items && items.length > 0 && (
        <div style={{ display: 'flex', gap: 12, margin: '16px 0', flexWrap: 'wrap' }}>
          <Badge tone="warning">{isAr ? 'مسودات' : 'Draft'}: {counts.draft}</Badge>
          <Badge tone="success">{isAr ? 'مقبولة' : 'Accepted'}: {counts.accepted}</Badge>
          <Badge tone="danger">{isAr ? 'مرفوضة' : 'Rejected'}: {counts.rejected}</Badge>
        </div>
      )}

      <LoadableSection
        loading={loading}
        error={error}
        onRetry={reload}
        data={items}
        emptyTitle={isAr ? 'لا اقتراحات' : 'No suggestions yet'}
        emptySub={isAr
          ? 'سيظهر هنا أي اقتراح ينشأ من معالجات الذكاء الاصطناعي.'
          : 'Suggestions appear here as the AI heuristics fire from each wizard.'}>
        {(rows) => (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            {rows.map(s => (
              <Card key={s.id}
                    title={`#${s.id} · ${formatDate(s.createdAt)}`}
                    action={(
                      <div style={{ display: 'flex', gap: 6 }}>
                        <Badge tone={STATUS_TONE[s.status]}>{s.status}</Badge>
                        {s.confidence != null && (
                          <Badge tone="info">{Number(s.confidence).toFixed(0)}%</Badge>
                        )}
                      </div>
                    )}>
                {s.relatedEntity && (
                  <div className="text-xs" style={{ color: 'var(--ink-3)', marginBottom: 8 }}>
                    {isAr ? 'مرتبط بـ' : 'Linked to'}: <code>{s.relatedEntity}#{s.relatedEntityId ?? '—'}</code>
                  </div>
                )}

                {s.rationale && (
                  <div className="wf-summary" style={{ marginBottom: 12 }}>
                    <strong>{isAr ? 'المنطق' : 'Rationale'}:</strong> {s.rationale}
                  </div>
                )}

                <details style={{ marginBottom: 12 }}>
                  <summary className="text-xs" style={{ cursor: 'pointer', color: 'var(--ink-3)' }}>
                    {isAr ? 'المدخلات' : 'Input'}
                  </summary>
                  <pre className="ai-json-pre">{prettyJson(s.inputJson)}</pre>
                </details>

                <details open style={{ marginBottom: 12 }}>
                  <summary className="text-xs" style={{ cursor: 'pointer', color: 'var(--ink-3)' }}>
                    {isAr ? 'النتيجة' : 'Output'}
                  </summary>
                  <pre className="ai-json-pre">{prettyJson(s.outputJson)}</pre>
                </details>

                {s.status === 'DRAFT' ? (
                  <div className="wf-form-grid" style={{ marginTop: 12 }}>
                    <Field label={isAr ? 'ملاحظة المراجع (اختيارية)' : 'Reviewer note (optional)'} full>
                      <input className="input" value={reviewNote[s.id] ?? ''}
                             onChange={e => setReviewNote(r => ({ ...r, [s.id]: e.target.value }))}
                             placeholder={isAr ? 'سبب القبول أو الرفض…' : 'Why accept or reject…'}/>
                    </Field>
                    <div style={{ display: 'flex', gap: 8, gridColumn: '1 / -1' }}>
                      <Button variant="primary" Icon={CheckCheck}
                              disabled={busyId === s.id}
                              onClick={() => review(s.id, 'ACCEPTED')}>
                        {isAr ? 'قبول' : 'Accept'}
                      </Button>
                      <Button variant="danger" Icon={X}
                              disabled={busyId === s.id}
                              onClick={() => review(s.id, 'REJECTED')}>
                        {isAr ? 'رفض' : 'Reject'}
                      </Button>
                    </div>
                  </div>
                ) : (
                  s.reviewerNote && (
                    <div className="text-xs" style={{ color: 'var(--ink-3)', marginTop: 12 }}>
                      <strong>{isAr ? 'ملاحظة المراجع:' : 'Reviewer:'}</strong> {s.reviewerNote}
                      {s.reviewedAt && (
                        <span style={{ marginInlineStart: 8 }}>
                          ({formatDate(s.reviewedAt)})
                        </span>
                      )}
                    </div>
                  )
                )}
              </Card>
            ))}
          </div>
        )}
      </LoadableSection>
    </Shell>
  );
}

function iconFor(f: AiFeature) {
  switch (f) {
    case 'RISK_DESCRIPTION':    return AlertOctagon;
    case 'BIA_RTO_RPO':         return Activity;
    case 'EVIDENCE_QUALITY':    return FileCheck2;
    case 'INCIDENT_ROOT_CAUSE': return AlertOctagon;
    case 'BOARD_SUMMARY':       return Sparkles;
    default:                    return Sparkles;
  }
}

function prettyJson(raw: string): string {
  try { return JSON.stringify(JSON.parse(raw), null, 2); }
  catch { return raw; }
}

function formatDate(iso: string): string {
  try { return new Date(iso).toLocaleString(); }
  catch { return iso; }
}

function Field({ label, required, full, children }: {
  label: string; required?: boolean; full?: boolean; children: React.ReactNode;
}) {
  return (
    <label className={'wf-field' + (full ? ' wf-field-full' : '')}>
      <span className="wf-label">
        {label}{required && <span className="wf-required"> *</span>}
      </span>
      {children}
    </label>
  );
}
