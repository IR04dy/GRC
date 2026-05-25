import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { AlertCircle, Search, User } from 'lucide-react';
import { PublicShell } from '../../components/layout/PublicShell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  complaintsApi,
  CATEGORY_LABEL, SEVERITY_LABEL, SEVERITY_TONE,
  STATUS_LABEL, STATUS_TONE, UPDATE_TYPE_LABEL,
  type PublicTrackingResponse,
} from '../../lib/complaints';

/**
 * Public tracking page — /public/track-complaint.
 *
 * <p>UNAUTHENTICATED. Customer pastes the UUID tracking token returned
 * by /public/complaint to inspect status + customer-visible timeline.
 * Backend strips PII — only initials, status, severity, and any
 * customer_visible update notes are returned.
 */
export function PublicComplaintTrackingPage() {
  const { lang } = useI18n();
  const [params] = useSearchParams();
  const initialToken = params.get('token') ?? '';

  const [token, setToken] = useState(initialToken);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<PublicTrackingResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [notFound, setNotFound] = useState(false);

  // Auto-track if token arrived in URL (e.g. customer clicks "Track" after submit).
  useEffect(() => {
    if (initialToken && !result && !loading) {
      void doTrack(initialToken);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function doTrack(tk: string) {
    if (!tk.trim()) return;
    setError(null);
    setNotFound(false);
    setResult(null);
    setLoading(true);
    try {
      const r = await complaintsApi.publicTrack(tk.trim());
      setResult(r);
    } catch (err) {
      if (err instanceof ApiError && err.status === 404) {
        setNotFound(true);
      } else {
        setError(err instanceof ApiError ? err.message :
          (lang === 'ar' ? 'تعذّر جلب الحالة' : 'Failed to fetch status'));
      }
    } finally {
      setLoading(false);
    }
  }

  function fmtDate(iso?: string): string {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB', {
        year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
      });
    } catch { return iso; }
  }

  return (
    <PublicShell
      title={lang === 'ar' ? 'تتبّع شكواي' : 'Track my complaint'}
      sub={lang === 'ar'
        ? 'أدخل رمز التتبّع الذي حصلت عليه عند تقديم الشكوى لمتابعة الحالة. لا تظهر هنا أي بيانات شخصية.'
        : 'Enter the tracking code you received when filing the complaint to view its status. No personal data is shown on this page.'}
    >
      <Card padded>
        <form onSubmit={e => { e.preventDefault(); void doTrack(token); }}
          style={{ display: 'flex', gap: 10, marginBottom: 16, flexWrap: 'wrap' }}>
          <input
            value={token}
            onChange={e => setToken(e.target.value)}
            placeholder={lang === 'ar'
              ? 'ألصق رمز التتبّع هنا (UUID)'
              : 'Paste your tracking code (UUID)'}
            style={{
              flex: 1, minWidth: 280,
              padding: '10px 14px', fontSize: 14,
              border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-1)', color: 'var(--ink-1)',
              fontFamily: 'monospace',
            }}
          />
          <button type="submit" disabled={loading || !token.trim()} style={{
            padding: '10px 22px', fontSize: 14, fontWeight: 500,
            background: 'var(--primary)', color: '#fff',
            border: 'none', borderRadius: 'var(--r-sm)',
            cursor: (loading || !token.trim()) ? 'not-allowed' : 'pointer',
            opacity: (loading || !token.trim()) ? 0.6 : 1,
            display: 'inline-flex', alignItems: 'center', gap: 6,
          }}>
            <Search size={14} />
            {loading
              ? (lang === 'ar' ? 'جارٍ البحث…' : 'Searching…')
              : (lang === 'ar' ? 'بحث' : 'Track')}
          </button>
        </form>

        {notFound && (
          <div style={{
            padding: '14px 16px', marginBottom: 12, borderRadius: 'var(--r-sm)',
            background: 'var(--warning-bg, #fff7e6)', borderInlineStart: '3px solid var(--warning)',
            display: 'flex', alignItems: 'center', gap: 10, fontSize: 13,
          }}>
            <AlertCircle size={18} style={{ color: 'var(--warning)' }} />
            {lang === 'ar'
              ? 'لم يتم العثور على رمز التتبّع. تأكّد من نسخ الرمز كاملاً وحاول مجدداً.'
              : 'Tracking code not found. Please double-check the code and try again.'}
          </div>
        )}
        {error && !notFound && (
          <div style={{
            padding: '10px 14px', marginBottom: 12, borderRadius: 'var(--r-sm)',
            background: 'var(--danger-bg)', color: 'var(--danger-fg)',
            fontSize: 13, display: 'flex', alignItems: 'center', gap: 8,
          }}>
            <AlertCircle size={16} /> {error}
          </div>
        )}

        {result && (
          <div>
            {/* Header strip */}
            <div style={{
              padding: 16, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
              marginBottom: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap',
            }}>
              <div style={{ flex: 1, minWidth: 200 }}>
                <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
                  letterSpacing: '0.06em', marginBottom: 4 }}>
                  {lang === 'ar' ? 'رقم الشكوى' : 'Complaint #'}
                </div>
                <div style={{ fontSize: 18, fontWeight: 700, fontFamily: 'monospace' }}>
                  {result.complaintNo}
                </div>
                {result.customerInitials && (
                  <div style={{ marginTop: 4, fontSize: 12, color: 'var(--ink-3)',
                    display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                    <User size={12} />
                    <span dir={lang === 'ar' ? 'rtl' : 'ltr'}>{result.customerInitials}</span>
                  </div>
                )}
              </div>
              <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                <Badge tone={STATUS_TONE[result.status]} dot>
                  {STATUS_LABEL[result.status][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
                <Badge tone={SEVERITY_TONE[result.severity]}>
                  {SEVERITY_LABEL[result.severity][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
                <Badge tone="neutral">
                  {CATEGORY_LABEL[result.category][lang === 'ar' ? 'ar' : 'en']}
                </Badge>
              </div>
            </div>

            {/* Milestones */}
            <div style={{
              display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))',
              gap: 8, marginBottom: 16,
            }}>
              <Milestone label={lang === 'ar' ? 'تاريخ التقديم' : 'Submitted'}
                value={fmtDate(result.submittedOn)} />
              <Milestone label={lang === 'ar' ? 'تاريخ الإقرار' : 'Acknowledged'}
                value={fmtDate(result.acknowledgedOn)} />
              <Milestone label={lang === 'ar' ? 'تاريخ الحل' : 'Resolved'}
                value={fmtDate(result.resolvedOn)} />
              {result.escalatedToSamaOn && (
                <Milestone label={lang === 'ar' ? 'الإحالة لساما' : 'Escalated to SAMA'}
                  value={fmtDate(result.escalatedToSamaOn)} />
              )}
            </div>

            {/* Timeline */}
            <div style={{ fontSize: 13, fontWeight: 600, color: 'var(--ink-1)', marginBottom: 8 }}>
              {lang === 'ar' ? 'سجلّ الأحداث' : 'Timeline'}
            </div>
            {result.timeline.length === 0 ? (
              <div style={{
                padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13,
                background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
              }}>
                {lang === 'ar'
                  ? 'لم تُسجَّل تحديثات بعد. سيتواصل البنك معك قريباً.'
                  : 'No updates yet. The bank will contact you soon.'}
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
                {result.timeline.map((u, i) => (
                  <div key={i} style={{
                    padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                    borderInlineStart: '3px solid var(--info)',
                  }}>
                    <div style={{ display: 'flex', gap: 8, alignItems: 'center',
                      flexWrap: 'wrap', fontSize: 12, color: 'var(--ink-3)' }}>
                      <Badge tone="neutral">
                        {UPDATE_TYPE_LABEL[u.updateType]?.[lang === 'ar' ? 'ar' : 'en'] ?? u.updateType}
                      </Badge>
                      {u.toStatus && (
                        <Badge tone={STATUS_TONE[u.toStatus]} dot>
                          {STATUS_LABEL[u.toStatus][lang === 'ar' ? 'ar' : 'en']}
                        </Badge>
                      )}
                      <span style={{ marginInlineStart: 'auto' }}>{fmtDate(u.occurredOn)}</span>
                    </div>
                    {(u.noteEn || u.noteAr) && (
                      <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.6,
                        whiteSpace: 'pre-wrap' }} dir={lang === 'ar' ? 'rtl' : 'ltr'}>
                        {lang === 'ar' ? (u.noteAr ?? u.noteEn) : (u.noteEn ?? u.noteAr)}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </Card>
    </PublicShell>
  );
}

function Milestone({ label, value }: { label: string; value: string }) {
  return (
    <div style={{
      padding: 10, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
      border: '1px solid var(--border)',
    }}>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
        letterSpacing: '0.04em', marginBottom: 4 }}>{label}</div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{value}</div>
    </div>
  );
}
