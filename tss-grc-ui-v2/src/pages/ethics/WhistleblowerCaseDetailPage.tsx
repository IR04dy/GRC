import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { AlertCircle, ArrowLeft, FileText, Lock, MessageSquare, ShieldAlert } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  whistleblowerApi,
  STATUS_LABEL, STATUS_TONE,
  SEVERITY_LABEL, SEVERITY_TONE,
  CATEGORY_LABEL,
  CHANNEL_LABEL, REPORTER_TYPE_LABEL,
  type CaseResponse, type UpdateResponse, type CaseStatus, type Severity,
} from '../../lib/whistleblower';

/**
 * Internal case detail — /ethics/whistleblower/cases/:id.
 *
 * <p>Displays the full case record (including reporter PII when not
 * anonymous), the timeline (internal + reporter-visible rows), and
 * three action panes:
 * <ul>
 *   <li><strong>Triage</strong> — RECEIVED → TRIAGE (revisable)</li>
 *   <li><strong>Investigation update</strong> — add a note / finding,
 *       toggle visible-to-reporter</li>
 *   <li><strong>Close</strong> — terminal transition with summary</li>
 * </ul>
 */
export function WhistleblowerCaseDetailPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const { id: idParam } = useParams<{ id: string }>();
  const id = idParam ? parseInt(idParam, 10) : NaN;

  const [data, setData] = useState<CaseResponse | null>(null);
  const [updates, setUpdates] = useState<UpdateResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [action, setAction] = useState<string | null>(null);

  // Triage form
  const [triageEn, setTriageEn] = useState('');
  const [triageAr, setTriageAr] = useState('');
  const [triageSev, setTriageSev] = useState<Severity | ''>('');

  // Investigation update form
  const [updateEn, setUpdateEn] = useState('');
  const [updateAr, setUpdateAr] = useState('');
  const [updateVisible, setUpdateVisible] = useState(false);
  const [updateType, setUpdateType] = useState<'INVESTIGATION_NOTE' | 'FINDING_RECORDED'>('INVESTIGATION_NOTE');

  // Close form
  const [closeStatus, setCloseStatus] = useState<CaseStatus>('CLOSED_NO_ACTION');
  const [closeEn, setCloseEn] = useState('');
  const [closeAr, setCloseAr] = useState('');

  async function load() {
    if (!Number.isFinite(id)) return;
    setLoading(true);
    try {
      const [c, u] = await Promise.all([
        whistleblowerApi.get(id),
        whistleblowerApi.updates(id),
      ]);
      setData(c);
      setUpdates(u);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Failed to load');
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  async function doTriage(e: React.FormEvent) {
    e.preventDefault();
    setAction('triage');
    try {
      const updated = await whistleblowerApi.triage(id, {
        triageDecisionEn: triageEn,
        triageDecisionAr: triageAr,
        severity: triageSev || undefined,
      });
      setData(updated);
      setUpdates(await whistleblowerApi.updates(id));
      setTriageEn(''); setTriageAr(''); setTriageSev('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Triage failed');
    } finally {
      setAction(null);
    }
  }

  async function doInvestigationUpdate(e: React.FormEvent) {
    e.preventDefault();
    setAction('update');
    try {
      // If still in TRIAGE, first start investigation.
      if (data?.status === 'TRIAGE' && data.assignedInvestigatorUserId) {
        await whistleblowerApi.startInvestigation(id, data.assignedInvestigatorUserId);
      }
      await whistleblowerApi.investigationUpdate(id, {
        bodyEn: updateEn,
        bodyAr: updateAr,
        isVisibleToReporter: updateVisible,
        updateType,
      });
      setData(await whistleblowerApi.get(id));
      setUpdates(await whistleblowerApi.updates(id));
      setUpdateEn(''); setUpdateAr('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Update failed');
    } finally {
      setAction(null);
    }
  }

  async function doClose(e: React.FormEvent) {
    e.preventDefault();
    setAction('close');
    try {
      const updated = await whistleblowerApi.close(id, {
        terminalStatus: closeStatus as
          'SUBSTANTIATED' | 'UNSUBSTANTIATED' | 'REFERRED'
          | 'CLOSED_NO_ACTION' | 'CLOSED_REMEDIATED',
        closureSummaryEn: closeEn,
        closureSummaryAr: closeAr,
      });
      setData(updated);
      setUpdates(await whistleblowerApi.updates(id));
      setCloseEn(''); setCloseAr('');
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Close failed');
    } finally {
      setAction(null);
    }
  }

  if (loading || !data) {
    return (
      <Shell title={lang === 'ar' ? 'تفاصيل البلاغ' : 'Case detail'}>
        <Card padded>{error || (lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…')}</Card>
      </Shell>
    );
  }

  const isTerminal = data.status === 'CLOSED_NO_ACTION' || data.status === 'CLOSED_REMEDIATED';

  return (
    <Shell title={`${data.caseCode}`} sub={lang === 'ar' ? data.subjectAr : data.subjectEn}>
      <button onClick={() => navigate('/ethics/whistleblower/cases')} style={backLinkStyle}>
        <ArrowLeft size={14} />
        {lang === 'ar' ? 'العودة للسجلّ' : 'Back to cases'}
      </button>

      {/* Status header */}
      <Card padded>
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, marginBottom: 12 }}>
          <Badge tone={STATUS_TONE[data.status]}>
            {STATUS_LABEL[data.status][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          <Badge tone="neutral">
            {CATEGORY_LABEL[data.category][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          <Badge tone={SEVERITY_TONE[data.severity]}>
            {SEVERITY_LABEL[data.severity][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          <Badge tone="neutral">
            {CHANNEL_LABEL[data.submissionChannel][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
          {data.involvesSeniorExecutive && (
            <Badge tone="danger">
              <ShieldAlert size={11} style={{ marginInlineEnd: 4 }} />
              {lang === 'ar' ? 'مسؤول تنفيذي رفيع' : 'Senior exec'}
            </Badge>
          )}
          {data.isAnonymous && (
            <Badge tone="info">
              <Lock size={11} style={{ marginInlineEnd: 4 }} />
              {lang === 'ar' ? 'مجهول الهوية' : 'Anonymous'}
            </Badge>
          )}
        </div>

        <Detail label={lang === 'ar' ? 'الموضوع (إنجليزي)' : 'Subject (English)'} value={data.subjectEn} />
        <Detail label={lang === 'ar' ? 'الموضوع (عربي)' : 'Subject (Arabic)'} value={data.subjectAr} rtl />
        <Detail label={lang === 'ar' ? 'السرد (إنجليزي)' : 'Narrative (English)'}
          value={data.narrativeEn} multi />
        <Detail label={lang === 'ar' ? 'السرد (عربي)' : 'Narrative (Arabic)'}
          value={data.narrativeAr} multi rtl />

        {!data.isAnonymous && (
          <div style={{ marginTop: 14, background: 'var(--surface-2)',
            padding: 12, borderRadius: 'var(--r-sm)', border: '1px solid var(--border)' }}>
            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-3)',
              textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 6 }}>
              {lang === 'ar' ? 'معلومات المُبلِّغ' : 'Reporter information'}
            </div>
            <div style={{ fontSize: 13 }}>
              {REPORTER_TYPE_LABEL[data.reporterType][lang === 'ar' ? 'ar' : 'en']}
              {data.reporterName && <> · {data.reporterName}</>}
              {data.reporterEmail && <> · {data.reporterEmail}</>}
              {data.reporterEmployeeId && <> · {data.reporterEmployeeId}</>}
            </div>
          </div>
        )}
      </Card>

      {/* Timeline */}
      <div style={{ marginTop: 16 }}>
      <Card padded>
        <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 10,
          display: 'flex', alignItems: 'center', gap: 6 }}>
          <FileText size={16} />
          {lang === 'ar' ? 'السجلّ' : 'Timeline'}
          <span style={{ fontSize: 12, color: 'var(--ink-3)', fontWeight: 400 }}>
            · {updates.length}
          </span>
        </div>
        {updates.length === 0 ? (
          <div style={{ fontSize: 13, color: 'var(--ink-2)' }}>
            {lang === 'ar' ? 'لا توجد تحديثات بعد.' : 'No updates yet.'}
          </div>
        ) : (
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, display: 'flex', flexDirection: 'column', gap: 10 }}>
            {updates.map(u => (
              <li key={u.id} style={{
                padding: 10, border: '1px solid var(--border)',
                borderRadius: 'var(--r-sm)', background: 'var(--surface-1)',
              }}>
                <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between',
                  marginBottom: 4, gap: 8, flexWrap: 'wrap' }}>
                  <div style={{ fontSize: 11, fontWeight: 600, color: 'var(--ink-3)',
                    textTransform: 'uppercase', letterSpacing: '0.05em' }}>
                    {u.updateType.replace(/_/g, ' ')}
                    {!u.isVisibleToReporter && <span style={{ marginInlineStart: 8 }}>
                      <Lock size={10} style={{ verticalAlign: 'middle' }} />
                      <span style={{ marginInlineStart: 4 }}>
                        {lang === 'ar' ? 'داخلي' : 'Internal'}
                      </span>
                    </span>}
                  </div>
                  <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {u.createdAt && new Date(u.createdAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
                  </div>
                </div>
                <div style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>
                  {lang === 'ar' ? u.bodyAr : u.bodyEn}
                </div>
              </li>
            ))}
          </ul>
        )}
      </Card>
      </div>

      {/* Action forms */}
      {!isTerminal && (
        <>
          {/* Triage */}
          {(data.status === 'RECEIVED' || data.status === 'TRIAGE') && (
            <div style={{ marginTop: 16 }}>
            <Card padded>
              <Heading
                icon={MessageSquare}
                title={lang === 'ar' ? 'تسجيل قرار الفرز' : 'Record triage decision'} />
              <form onSubmit={doTriage} style={formStyle}>
                <textarea placeholder={lang === 'ar' ? 'قرار الفرز (إنجليزي)' : 'Triage decision (English)'}
                  value={triageEn} onChange={e => setTriageEn(e.target.value)} required rows={3} style={inputStyle} />
                <textarea placeholder={lang === 'ar' ? 'قرار الفرز (عربي)' : 'Triage decision (Arabic)'}
                  value={triageAr} onChange={e => setTriageAr(e.target.value)} required rows={3} dir="rtl" style={inputStyle} />
                <select value={triageSev} onChange={e => setTriageSev(e.target.value as Severity | '')} style={inputStyle}>
                  <option value="">{lang === 'ar' ? '— تعديل الأهمّية (اختياري) —' : '— Revise severity (optional) —'}</option>
                  {(Object.keys(SEVERITY_LABEL) as Severity[]).map(s => (
                    <option key={s} value={s}>{SEVERITY_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</option>
                  ))}
                </select>
                <SubmitBtn label={lang === 'ar' ? 'حفظ الفرز' : 'Save triage'} busy={action === 'triage'} />
              </form>
            </Card>
            </div>
          )}

          {/* Investigation update */}
          {(data.status === 'TRIAGE' || data.status === 'UNDER_INVESTIGATION') && (
            <div style={{ marginTop: 16 }}>
            <Card padded>
              <Heading
                icon={FileText}
                title={lang === 'ar' ? 'إضافة ملاحظة تحقيق' : 'Add investigation update'} />
              <form onSubmit={doInvestigationUpdate} style={formStyle}>
                <select value={updateType}
                  onChange={e => setUpdateType(e.target.value as 'INVESTIGATION_NOTE' | 'FINDING_RECORDED')}
                  style={inputStyle}>
                  <option value="INVESTIGATION_NOTE">{lang === 'ar' ? 'ملاحظة داخلية' : 'Internal note'}</option>
                  <option value="FINDING_RECORDED">{lang === 'ar' ? 'تسجيل نتيجة' : 'Record finding'}</option>
                </select>
                <textarea placeholder={lang === 'ar' ? 'النص (إنجليزي)' : 'Body (English)'}
                  value={updateEn} onChange={e => setUpdateEn(e.target.value)} required rows={3} style={inputStyle} />
                <textarea placeholder={lang === 'ar' ? 'النص (عربي)' : 'Body (Arabic)'}
                  value={updateAr} onChange={e => setUpdateAr(e.target.value)} required rows={3} dir="rtl" style={inputStyle} />
                <label style={{ display: 'flex', alignItems: 'center', gap: 6, fontSize: 13 }}>
                  <input type="checkbox" checked={updateVisible} onChange={e => setUpdateVisible(e.target.checked)} />
                  {lang === 'ar' ? 'مرئي للمُبلِّغ على صفحة الحالة' : 'Visible to reporter on the status page'}
                </label>
                <SubmitBtn label={lang === 'ar' ? 'إضافة التحديث' : 'Add update'} busy={action === 'update'} />
              </form>
            </Card>
            </div>
          )}

          {/* Close */}
          <div style={{ marginTop: 16 }}>
          <Card padded>
            <Heading
              icon={AlertCircle}
              title={lang === 'ar' ? 'إغلاق البلاغ' : 'Close case'} />
            <form onSubmit={doClose} style={formStyle}>
              <select value={closeStatus} onChange={e => setCloseStatus(e.target.value as CaseStatus)} style={inputStyle}>
                <option value="CLOSED_NO_ACTION">{STATUS_LABEL.CLOSED_NO_ACTION[lang === 'ar' ? 'ar' : 'en']}</option>
                <option value="CLOSED_REMEDIATED">{STATUS_LABEL.CLOSED_REMEDIATED[lang === 'ar' ? 'ar' : 'en']}</option>
                <option value="SUBSTANTIATED">{STATUS_LABEL.SUBSTANTIATED[lang === 'ar' ? 'ar' : 'en']}</option>
                <option value="UNSUBSTANTIATED">{STATUS_LABEL.UNSUBSTANTIATED[lang === 'ar' ? 'ar' : 'en']}</option>
                <option value="REFERRED">{STATUS_LABEL.REFERRED[lang === 'ar' ? 'ar' : 'en']}</option>
              </select>
              <textarea placeholder={lang === 'ar' ? 'ملخّص الإغلاق (إنجليزي)' : 'Closure summary (English)'}
                value={closeEn} onChange={e => setCloseEn(e.target.value)} required rows={3} style={inputStyle} />
              <textarea placeholder={lang === 'ar' ? 'ملخّص الإغلاق (عربي)' : 'Closure summary (Arabic)'}
                value={closeAr} onChange={e => setCloseAr(e.target.value)} required rows={3} dir="rtl" style={inputStyle} />
              <SubmitBtn label={lang === 'ar' ? 'إغلاق البلاغ' : 'Close case'} busy={action === 'close'} />
            </form>
          </Card>
          </div>
        </>
      )}

      {error && (
        <div style={{
          marginTop: 14, padding: '10px 14px', borderRadius: 'var(--r-sm)',
          background: 'var(--danger-50, #fef2f2)', color: 'var(--danger)',
          fontSize: 13, display: 'flex', alignItems: 'center', gap: 8,
        }}>
          <AlertCircle size={16} /> {error}
        </div>
      )}
    </Shell>
  );
}

function Detail({ label, value, rtl, multi }:
  { label: string; value: string; rtl?: boolean; multi?: boolean }) {
  return (
    <div style={{ marginTop: 8 }}>
      <div style={{ fontSize: 11, fontWeight: 500, color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: 2 }}>
        {label}
      </div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)',
        whiteSpace: multi ? 'pre-wrap' : 'normal',
        ...(rtl ? { direction: 'rtl' as const, textAlign: 'right' as const } : {}) }}>
        {value || '—'}
      </div>
    </div>
  );
}

function Heading({ icon: Icon, title }: { icon: typeof FileText; title: string }) {
  return (
    <div style={{ fontSize: 14, fontWeight: 600, marginBottom: 10,
      display: 'flex', alignItems: 'center', gap: 6 }}>
      <Icon size={16} />
      {title}
    </div>
  );
}

function SubmitBtn({ label, busy }: { label: string; busy: boolean }) {
  return (
    <div style={{ display: 'flex', justifyContent: 'flex-end' }}>
      <button type="submit" disabled={busy} style={{
        padding: '8px 18px', fontSize: 13, fontWeight: 500,
        background: 'var(--primary)', color: 'var(--on-primary, #fff)',
        border: 'none', borderRadius: 'var(--r-sm)',
        cursor: busy ? 'not-allowed' : 'pointer',
        opacity: busy ? 0.6 : 1,
      }}>
        {busy ? '…' : label}
      </button>
    </div>
  );
}

const inputStyle: React.CSSProperties = {
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

const formStyle: React.CSSProperties = {
  display: 'flex', flexDirection: 'column', gap: 10,
};

const backLinkStyle: React.CSSProperties = {
  display: 'inline-flex', alignItems: 'center', gap: 4,
  background: 'none', border: 'none',
  color: 'var(--ink-2)', fontSize: 13, cursor: 'pointer',
  padding: '4px 0', marginBottom: 12,
};
