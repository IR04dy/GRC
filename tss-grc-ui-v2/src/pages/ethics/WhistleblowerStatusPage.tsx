import { useEffect, useState } from 'react';
import { useParams } from 'react-router-dom';
import { AlertCircle, Check, MessageSquare } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  whistleblowerApi,
  STATUS_LABEL, STATUS_TONE,
  CATEGORY_LABEL, SEVERITY_LABEL, SEVERITY_TONE,
  type PublicCaseResponse,
} from '../../lib/whistleblower';

/**
 * Public status page — /ethics/whistleblower/status/:token.
 *
 * <p>Reporter retrieves status + the subset of timeline updates flagged
 * {@code is_visible_to_reporter=1}. Can post a reply via the same
 * token. No authentication; token IS the credential.
 */
export function WhistleblowerStatusPage() {
  const { lang } = useI18n();
  const { token } = useParams<{ token: string }>();

  const [data, setData] = useState<PublicCaseResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const [replyEn, setReplyEn] = useState('');
  const [replyAr, setReplyAr] = useState('');
  const [replying, setReplying] = useState(false);

  async function load() {
    if (!token) { setError('Missing token'); setLoading(false); return; }
    setLoading(true);
    setError(null);
    try {
      const r = await whistleblowerApi.publicStatus(token);
      setData(r);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الحالة' : 'Failed to load status'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [token]);

  async function postReply(e: React.FormEvent) {
    e.preventDefault();
    if (!token) return;
    setReplying(true);
    try {
      const r = await whistleblowerApi.publicReply(token, { bodyEn: replyEn, bodyAr: replyAr });
      setData(r);
      setReplyEn(''); setReplyAr('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إرسال الردّ' : 'Failed to send reply'));
    } finally {
      setReplying(false);
    }
  }

  if (loading) {
    return (
      <PublicShell title={lang === 'ar' ? 'متابعة البلاغ' : 'Track report'}>
        <Card padded><div style={{ color: 'var(--ink-2)' }}>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</div></Card>
      </PublicShell>
    );
  }

  if (error || !data) {
    return (
      <PublicShell title={lang === 'ar' ? 'متابعة البلاغ' : 'Track report'}>
        <Card padded>
          <div style={{ color: 'var(--danger)', fontSize: 14, display: 'flex', alignItems: 'center', gap: 8 }}>
            <AlertCircle size={18} />
            {error || (lang === 'ar' ? 'الرمز غير صالح' : 'Invalid tracking token')}
          </div>
          <div style={{ marginTop: 8, fontSize: 13, color: 'var(--ink-2)' }}>
            {lang === 'ar'
              ? 'تحقّق من رمز التتبّع المرسل لك. لا يمكننا استرجاع الرمز في حال فقدانه.'
              : 'Check the tracking token issued to you. We cannot recover a lost token.'}
          </div>
        </Card>
      </PublicShell>
    );
  }

  const subject = lang === 'ar' ? data.subjectAr : data.subjectEn;

  return (
    <PublicShell title={`${data.caseCode}`} sub={subject}>
      <Card padded>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 16 }}>
          <Badge tone={STATUS_TONE[data.status]}>
            {STATUS_LABEL[data.status][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          <Badge tone="neutral">
            {CATEGORY_LABEL[data.category][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          <Badge tone={SEVERITY_TONE[data.severity]}>
            {SEVERITY_LABEL[data.severity][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        </div>

        <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
          {lang === 'ar' ? 'تاريخ الإرسال: ' : 'Submitted: '}
          {new Date(data.submittedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
          {data.closedAt && (
            <> · {lang === 'ar' ? 'تاريخ الإغلاق: ' : 'Closed: '}
              {new Date(data.closedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
            </>
          )}
        </div>
      </Card>

      <div style={{ marginTop: 16 }}>
      <Card padded>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 12 }}>
          {lang === 'ar' ? 'سجلّ التحديثات' : 'Timeline'}
        </div>
        {data.updates.length === 0 ? (
          <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
            {lang === 'ar' ? 'لا توجد تحديثات بعد.' : 'No updates yet.'}
          </div>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 12 }}>
            {data.updates.map((u, i) => (
              <li key={i} style={{
                padding: 12, border: '1px solid var(--border)',
                borderRadius: 'var(--r-sm)', background: u.fromReporter ? 'var(--surface-2)' : 'var(--surface-1)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  marginBottom: 6, gap: 8, flexWrap: 'wrap' }}>
                  <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    {u.fromReporter
                      ? (lang === 'ar' ? 'رسالة منك' : 'Your message')
                      : (lang === 'ar' ? 'تحديث من فريق الأخلاقيات' : 'Ethics team update')}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {new Date(u.postedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
                  </div>
                </div>
                <div style={{ fontSize: 13, color: 'var(--ink-1)', whiteSpace: 'pre-wrap' }}>
                  {lang === 'ar' ? u.bodyAr : u.bodyEn}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
      </div>

      {!data.closedAt && (
        <div style={{ marginTop: 16 }}>
        <Card padded>
          <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 8,
            display: 'flex', alignItems: 'center', gap: 6 }}>
            <MessageSquare size={16} />
            {lang === 'ar' ? 'إضافة معلومات إلى بلاغك' : 'Add information to your report'}
          </div>
          <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 10 }}>
            {lang === 'ar'
              ? 'يمكنك إرفاق تفاصيل إضافية أو الردّ على استفسار من المحقّق. تظهر رسالتك في السجلّ أعلاه فور إرسالها.'
              : 'Attach extra details or reply to an investigator question. Your message appears in the timeline above immediately.'}
          </div>
          <form onSubmit={postReply} style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <textarea
              placeholder={lang === 'ar' ? 'رسالتك بالإنجليزية' : 'Message in English'}
              value={replyEn} onChange={e => setReplyEn(e.target.value)}
              required rows={3}
              style={{ ...textareaStyle }}
            />
            <textarea
              placeholder={lang === 'ar' ? 'رسالتك بالعربية' : 'Message in Arabic'}
              value={replyAr} onChange={e => setReplyAr(e.target.value)}
              required rows={3} dir="rtl"
              style={{ ...textareaStyle }}
            />
            <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
              <button type="submit" disabled={replying} style={{
                padding: '8px 18px', fontSize: 13, fontWeight: 500,
                background: 'var(--primary)', color: 'var(--on-primary, #fff)',
                border: 'none', borderRadius: 'var(--r-sm)',
                cursor: replying ? 'not-allowed' : 'pointer',
                opacity: replying ? 0.6 : 1, display: 'inline-flex', alignItems: 'center', gap: 6,
              }}>
                <Check size={14} />
                {replying
                  ? (lang === 'ar' ? 'جارٍ الإرسال…' : 'Sending…')
                  : (lang === 'ar' ? 'إرسال' : 'Send')}
              </button>
            </div>
          </form>
        </Card>
        </div>
      )}
    </PublicShell>
  );
}

const textareaStyle: React.CSSProperties = {
  width: '100%',
  padding: '8px 10px',
  fontSize: 13,
  border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
  color: 'var(--ink-1)',
  boxSizing: 'border-box',
  resize: 'vertical',
};
