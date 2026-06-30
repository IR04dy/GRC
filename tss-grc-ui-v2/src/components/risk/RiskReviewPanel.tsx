import { useCallback, useEffect, useState } from 'react';
import { CalendarClock, History, RefreshCw } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { riskReviewApi, REVIEW_DECISIONS, type RiskReviewDto, ApiError } from '../../lib/api';

/**
 * ERM epic E-J — scheduled review / reassessment.
 *
 * A structured re-evaluation of a risk on a cadence (distinct from the lighter
 * periodic owner attestation): optionally re-score residual L/I, reset the
 * review clock, and keep an audit trail of what each review changed. The
 * "overdue" state is derived from the next-review date (the backend daily
 * sweep persists the same flag + emits an F8 escalation event).
 */
const DECISION_AR: Record<string, string> = {
  CONFIRMED: 'مؤكَّد', RESCORED: 'أُعيد التقييم', ESCALATE: 'تصعيد', RETIRE_SUGGESTED: 'يُقترح الإلغاء',
};

function fmt(d?: string | null): string {
  if (!d) return '—';
  const t = new Date(d);
  return isNaN(t.getTime()) ? '—' : t.toLocaleDateString();
}

export function RiskReviewPanel({ riskId, reviewDate, onSaved }: {
  riskId: number; reviewDate?: string | null; onSaved?: () => void;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [history, setHistory] = useState<RiskReviewDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [busy, setBusy] = useState(false);

  // Reassess form
  const [rl, setRl] = useState(0);          // 0 = keep current
  const [ri, setRi] = useState(0);
  const [cadence, setCadence] = useState('');
  const [decision, setDecision] = useState('');
  const [note, setNote] = useState('');

  const load = useCallback(async () => {
    setLoading(true);
    try { setHistory(await riskReviewApi.history(riskId)); }
    catch { setHistory([]); }
    finally { setLoading(false); }
  }, [riskId]);
  useEffect(() => { void load(); }, [load]);

  const overdue = !!reviewDate && new Date(reviewDate) < new Date(new Date().toDateString());
  const lastReviewed = history.length ? history[0].reviewedAt : null;

  async function submit() {
    setBusy(true);
    try {
      const body: Record<string, unknown> = {};
      if (rl > 0) body.residualLikelihood = rl;
      if (ri > 0) body.residualImpact = ri;
      if (cadence && Number(cadence) > 0) body.cadenceDays = Number(cadence);
      if (decision) body.decision = decision;
      if (note.trim()) body.noteEn = note.trim();
      const rv = await riskReviewApi.reassess(riskId, body);
      toast.success(isAr
        ? `تمت المراجعة (${DECISION_AR[rv.decision] ?? rv.decision}) — المراجعة القادمة ${fmt(rv.nextReviewDate)}`
        : `Reviewed (${rv.decision}) — next review ${fmt(rv.nextReviewDate)}`);
      setRl(0); setRi(0); setCadence(''); setDecision(''); setNote('');
      await load();
      onSaved?.();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل حفظ المراجعة' : 'Reassessment failed'));
    } finally { setBusy(false); }
  }

  const sel: React.CSSProperties = {
    padding: '4px 6px', borderRadius: 6, border: '1px solid var(--border)',
    background: 'var(--surface)', color: 'var(--ink-1)', fontSize: 13,
  };

  return (
    <Card title={isAr ? 'المراجعة وإعادة التقييم' : 'Review & reassessment'} Icon={CalendarClock}
      action={<Badge tone={overdue ? 'danger' : 'success'}>
        {overdue ? (isAr ? 'مراجعة متأخرة' : 'Review overdue') : (isAr ? 'محدَّث' : 'Current')}
      </Badge>}>

      <div style={{ display: 'flex', gap: 16, flexWrap: 'wrap', fontSize: 13, marginBottom: 12 }}>
        <span className="text-muted">{isAr ? 'المراجعة القادمة' : 'Next review'}: <strong>{fmt(reviewDate)}</strong></span>
        <span className="text-muted">{isAr ? 'آخر مراجعة' : 'Last reviewed'}: <strong>{fmt(lastReviewed)}</strong></span>
      </div>

      {/* Reassess form */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(150px,1fr))', gap: 8, alignItems: 'end' }}>
        <label style={{ fontSize: 12 }}>{isAr ? 'الاحتمالية (المتبقية)' : 'Residual likelihood'}
          <select value={rl} onChange={e => setRl(Number(e.target.value))} style={{ ...sel, width: '100%' }}>
            <option value={0}>{isAr ? 'بدون تغيير' : 'No change'}</option>
            {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <label style={{ fontSize: 12 }}>{isAr ? 'الأثر (المتبقي)' : 'Residual impact'}
          <select value={ri} onChange={e => setRi(Number(e.target.value))} style={{ ...sel, width: '100%' }}>
            <option value={0}>{isAr ? 'بدون تغيير' : 'No change'}</option>
            {[1, 2, 3, 4, 5].map(n => <option key={n} value={n}>{n}</option>)}
          </select>
        </label>
        <label style={{ fontSize: 12 }}>{isAr ? 'الدورة (أيام)' : 'Cadence (days)'}
          <input type="number" min={1} value={cadence} onChange={e => setCadence(e.target.value)}
                 placeholder="365" style={{ ...sel, width: '100%' }} />
        </label>
        <label style={{ fontSize: 12 }}>{isAr ? 'القرار' : 'Decision'}
          <select value={decision} onChange={e => setDecision(e.target.value)} style={{ ...sel, width: '100%' }}>
            <option value="">{isAr ? 'تلقائي' : 'Auto'}</option>
            {REVIEW_DECISIONS.map(d => <option key={d} value={d}>{isAr ? (DECISION_AR[d] ?? d) : d}</option>)}
          </select>
        </label>
      </div>
      <textarea value={note} onChange={e => setNote(e.target.value)} rows={2}
                placeholder={isAr ? 'ملاحظة المراجعة (اختياري)' : 'Review note (optional)'}
                style={{ ...sel, width: '100%', marginTop: 8, resize: 'vertical' }} />
      <div style={{ display: 'flex', justifyContent: 'flex-end', marginTop: 8 }}>
        <Button size="sm" variant="primary" Icon={RefreshCw} disabled={busy} onClick={() => void submit()}>
          {busy ? (isAr ? 'جارٍ…' : 'Saving…') : (isAr ? 'إعادة التقييم' : 'Reassess')}
        </Button>
      </div>

      {/* History */}
      <div style={{ marginTop: 14, borderTop: '1px solid var(--border)', paddingTop: 10 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 12, fontWeight: 600, marginBottom: 6 }}>
          <History size={14} /> {isAr ? 'سجل المراجعات' : 'Review history'}
        </div>
        {loading ? (
          <div className="text-muted" style={{ fontSize: 12 }}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
        ) : history.length === 0 ? (
          <div className="text-muted" style={{ fontSize: 12 }}>{isAr ? 'لا توجد مراجعات بعد.' : 'No reviews yet.'}</div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
            {history.map(h => (
              <div key={h.id} style={{ display: 'flex', justifyContent: 'space-between', gap: 8,
                                       fontSize: 12, borderBottom: '1px solid var(--border)', paddingBottom: 4 }}>
                <span><Badge tone="neutral">{isAr ? (DECISION_AR[h.decision] ?? h.decision) : h.decision}</Badge>
                  {h.residualLikelihoodAfter != null && (
                    <span className="text-muted" style={{ marginInlineStart: 6 }}>
                      L{h.residualLikelihoodBefore ?? '?'}×I{h.residualImpactBefore ?? '?'} → L{h.residualLikelihoodAfter}×I{h.residualImpactAfter}
                    </span>
                  )}
                  {h.noteEn && <span className="text-muted" style={{ marginInlineStart: 6 }}>· {h.noteEn}</span>}
                </span>
                <span className="text-muted" style={{ whiteSpace: 'nowrap' }}>{fmt(h.reviewedAt)}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </Card>
  );
}
