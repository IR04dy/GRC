// WF-7 — the PUBLIC tracker an EXTERNAL workflow-request recipient (e.g. an outside auditor)
// opens with their tokenized link. UNAUTHENTICATED: the token in the URL is the only credential.
// Shows the MINIMAL projection the backend returns (no internal ids / other recipients / timeline)
// and — when it's their turn — lets them acknowledge/respond. Mirrors the complaints/whistleblower
// public trackers (PublicShell, no sidebar, bare fetch with no JWT).
import { useCallback, useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AlertCircle, CheckCircle2, Clock } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge, type BadgeTone } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { publicWorkflowApi, type PublicRequestView } from '../../lib/workflowRequests';

const COUNTDOWN_TONE: Record<string, BadgeTone> = {
  ON_TRACK: 'success', DUE_TODAY: 'warning', OVERDUE: 'danger', NONE: 'neutral',
};

export function PublicWorkflowRequestPage() {
  const { token } = useParams<{ token: string }>();
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const [view, setView] = useState<PublicRequestView | null>(null);
  const [loading, setLoading] = useState(true);
  const [status, setStatus] = useState<number | null>(null);
  const [note, setNote] = useState('');
  const [outcome, setOutcome] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);

  const load = useCallback(() => {
    if (!token) return;
    setLoading(true);
    publicWorkflowApi.view(token)
      .then(v => { setView(v); setStatus(200); })
      .catch((e: Error & { status?: number }) => { setView(null); setStatus(e.status ?? -1); })
      .finally(() => setLoading(false));
  }, [token]);
  useEffect(() => { load(); }, [load]);

  async function submit() {
    if (!token) return;
    setBusy(true);
    try {
      const v = await publicWorkflowApi.respond(token, note.trim() || undefined, outcome.trim().toUpperCase() || undefined);
      setView(v); setDone(true); setNote(''); setOutcome('');
    } catch (e) {
      setStatus((e as Error & { status?: number }).status ?? -1);
    } finally { setBusy(false); }
  }

  const title = ar ? 'طلب موجَّه إليك' : 'A request addressed to you';

  return (
    <PublicShell title={title}
      sub={ar ? 'تفتح هذه الصفحة عبر رابط خاص بك. لا تظهر هنا أي بيانات داخلية أخرى.'
              : 'You reached this page via your private link. No other internal data is shown here.'}>
      <Card padded>
        {loading && <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>}

        {!loading && !view && (
          <div style={{ padding: '14px 16px', borderRadius: 'var(--r-sm)',
            background: 'var(--danger-bg)', color: 'var(--danger-fg)', display: 'flex', gap: 10, alignItems: 'center' }}>
            <AlertCircle size={18} />
            {status === 410 ? (ar ? 'انتهت صلاحية هذا الرابط.' : 'This access link has expired.')
              : (ar ? 'رابط غير صالح أو لم يُعثر عليه.' : 'This link is invalid or was not found.')}
          </div>
        )}

        {view && (
          <div>
            <div style={{ padding: 16, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)', marginBottom: 16 }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: '.06em' }}>
                {view.requestCode} · {view.requestType.replace(/_/g, ' ')}
              </div>
              <div style={{ fontSize: 18, fontWeight: 700, marginTop: 4 }} dir={ar ? 'rtl' : 'ltr'}>
                {ar && view.titleAr ? view.titleAr : view.titleEn}
              </div>
              <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
                <Badge tone="neutral" dot>{view.status}</Badge>
                <Badge tone={COUNTDOWN_TONE[view.countdownStatus] ?? 'neutral'}>
                  <Clock size={11} /> {view.dueDate ? `${ar ? 'يستحق' : 'due'} ${view.dueDate}` : view.countdownStatus}
                </Badge>
                <Badge tone={view.priority === 'HIGH' || view.priority === 'CRITICAL' ? 'danger' : 'neutral'}>{view.priority}</Badge>
              </div>
              {view.requestedBy && (
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 8 }}>
                  {(ar ? 'الطالب: ' : 'Requested by: ') + view.requestedBy}
                </div>
              )}
            </div>

            <dl style={{ display: 'grid', gridTemplateColumns: '150px 1fr', gap: '6px 12px', margin: '0 0 16px', fontSize: 13 }}>
              <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'الإجراء المطلوب' : 'Required action'}</dt>
              <dd style={{ margin: 0 }}>{view.requiredAction ?? view.requestType.replace(/_/g, ' ')}</dd>
              <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'إليك بصفتك' : 'You are'}</dt>
              <dd style={{ margin: 0 }}>{view.recipientLabel} · {view.recipientStatus}</dd>
              {view.description && (<>
                <dt style={{ color: 'var(--ink-3)' }}>{ar ? 'التفاصيل' : 'Details'}</dt>
                <dd style={{ margin: 0, whiteSpace: 'pre-wrap' }} dir={ar ? 'rtl' : 'ltr'}>{view.description}</dd>
              </>)}
            </dl>

            {done && (
              <div style={{ padding: '12px 16px', borderRadius: 'var(--r-sm)', background: 'var(--success-bg, #e9f7ef)',
                color: 'var(--success-fg, #1a7f4b)', display: 'flex', gap: 10, alignItems: 'center', fontSize: 13 }}>
                <CheckCircle2 size={18} /> {ar ? 'تم تسجيل ردّك. شكراً لك.' : 'Your response has been recorded. Thank you.'}
              </div>
            )}

            {!done && view.canRespond && (
              <div style={{ borderTop: '1px solid var(--border)', paddingTop: 14 }}>
                <div style={{ fontSize: 13, fontWeight: 600, marginBottom: 8 }}>
                  {ar ? 'ردّك' : 'Your response'}
                </div>
                <textarea value={note} onChange={e => setNote(e.target.value)} rows={3}
                  placeholder={ar ? 'ملاحظة (اختياري)…' : 'A note (optional)…'}
                  style={{ width: '100%', padding: '10px 14px', fontSize: 14, boxSizing: 'border-box',
                    border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                    background: 'var(--surface-1)', color: 'var(--ink-1)', resize: 'vertical' }} />
                <input value={outcome} onChange={e => setOutcome(e.target.value)}
                  placeholder={ar ? 'النتيجة (اختياري، مثل: APPROVED / REJECTED)' : 'Outcome (optional, e.g. APPROVED / REJECTED)'}
                  style={{ width: '100%', marginTop: 8, padding: '10px 14px', fontSize: 13, boxSizing: 'border-box',
                    border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                    background: 'var(--surface-1)', color: 'var(--ink-1)', fontFamily: 'monospace' }} />
                <button onClick={() => void submit()} disabled={busy} style={{
                  marginTop: 12, padding: '10px 22px', fontSize: 14, fontWeight: 500,
                  background: 'var(--primary)', color: '#fff', border: 'none', borderRadius: 'var(--r-sm)',
                  cursor: busy ? 'not-allowed' : 'pointer', opacity: busy ? 0.6 : 1,
                  display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                  <CheckCircle2 size={14} /> {ar ? 'إرسال الردّ' : 'Submit response'}
                </button>
              </div>
            )}

            {!done && !view.canRespond && (
              <div style={{ fontSize: 12, color: 'var(--ink-3)', borderTop: '1px solid var(--border)', paddingTop: 14 }}>
                {ar ? 'لا يلزم إجراء منك في الوقت الحالي.' : 'No action is required from you at this time.'}
              </div>
            )}
          </div>
        )}
      </Card>
    </PublicShell>
  );
}
