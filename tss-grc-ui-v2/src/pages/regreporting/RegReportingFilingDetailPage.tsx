import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  CalendarDays, CheckCircle2, Send, XCircle, Ban, Shield, Clock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { Kpi } from '../../components/ui/Kpi';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import {
  regreportingApi,
  FILING_STATUS_LABEL, FILING_STATUS_TONE,
  REGULATOR_LABEL, FREQUENCY_LABEL, EVENT_TYPE_LABEL,
  nextAllowed, isTerminal,
  type FilingResponse, type FilingEventResponse, type FilingStatus,
} from '../../lib/regreporting';

/**
 * Filing workspace — /regreporting/filings/:id.
 *
 * Day-to-day workbench for one filing cycle:
 *   * Header with reference, status, regulator, due-date countdown
 *   * KPI strip with key dates
 *   * Action panel rendering ONLY the next allowed transitions
 *     (mirrors backend ALLOWED map) plus the dedicated actions for
 *     submit / acknowledge / reject / exempt
 *   * Bilingual timeline
 */
export function RegReportingFilingDetailPage() {
  const { id } = useParams();
  const filingId = Number(id);
  const { lang } = useI18n();

  const [filing, setFiling] = useState<FilingResponse | null>(null);
  const [events, setEvents] = useState<FilingEventResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [acting, setActing] = useState(false);

  // Action-modal state.
  const [modal, setModal] =
    useState<null | 'transition' | 'submit' | 'reject' | 'exempt'>(null);
  const [pendingTo, setPendingTo] = useState<FilingStatus | null>(null);
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');
  const [ackRef, setAckRef] = useState('');
  const [artifactRef, setArtifactRef] = useState('');
  const [exemptionRef, setExemptionRef] = useState('');

  const reload = async () => {
    if (!filingId) return;
    setLoading(true); setError(null);
    try {
      const [f, evs] = await Promise.all([
        regreportingApi.getFiling(filingId),
        regreportingApi.listEvents(filingId).catch(() => [] as FilingEventResponse[]),
      ]);
      setFiling(f);
      setEvents(evs);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل التقديم' : 'Failed to load filing'));
    } finally { setLoading(false); }
  };

  useEffect(() => { reload(); /* eslint-disable-next-line */ }, [filingId]);

  const closeModal = () => {
    setModal(null); setPendingTo(null);
    setNoteEn(''); setNoteAr(''); setAckRef(''); setArtifactRef('');
    setExemptionRef('');
  };

  const doTransition = async () => {
    if (!filing || !pendingTo) return;
    setActing(true);
    try {
      await regreportingApi.transitionFiling(filing.id, {
        toStatus: pendingTo,
        noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Transition failed');
    } finally { setActing(false); }
  };

  const doSubmit = async () => {
    if (!filing || !ackRef.trim()) return;
    setActing(true);
    try {
      await regreportingApi.submitFiling(filing.id, {
        regulatorAckReference: ackRef.trim(),
        filingArtifactReference: artifactRef || undefined,
        noteEn: noteEn || undefined, noteAr: noteAr || undefined,
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Submit failed');
    } finally { setActing(false); }
  };

  const doAcknowledge = async () => {
    if (!filing) return;
    setActing(true);
    try {
      await regreportingApi.acknowledgeFiling(filing.id);
      await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Acknowledge failed');
    } finally { setActing(false); }
  };

  const doReject = async () => {
    if (!filing || !noteEn.trim() || !noteAr.trim()) return;
    setActing(true);
    try {
      await regreportingApi.rejectFiling(filing.id, {
        rejectionReasonEn: noteEn.trim(),
        rejectionReasonAr: noteAr.trim(),
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Reject failed');
    } finally { setActing(false); }
  };

  const doExempt = async () => {
    if (!filing || !noteEn.trim() || !noteAr.trim() || !exemptionRef.trim()) return;
    setActing(true);
    try {
      await regreportingApi.exemptFiling(filing.id, {
        exemptionReasonEn: noteEn.trim(),
        exemptionReasonAr: noteAr.trim(),
        exemptionReference: exemptionRef.trim(),
      });
      closeModal(); await reload();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Exempt failed');
    } finally { setActing(false); }
  };

  if (loading && !filing) {
    return (
      <Shell title={lang === 'ar' ? 'التقديم' : 'Filing'}>
        <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
      </Shell>
    );
  }
  if (!filing) {
    return (
      <Shell title={lang === 'ar' ? 'التقديم' : 'Filing'}>
        <EmptyState Icon={CalendarDays}
          title={lang === 'ar' ? 'لم يُعثر على التقديم' : 'Filing not found'}
          sub={error || ''} />
      </Shell>
    );
  }

  const next = nextAllowed(filing.status);
  const terminal = isTerminal(filing.status);
  const inActiveBucket = !terminal && filing.status !== 'SUBMITTED'
                          && filing.status !== 'ACKNOWLEDGED';

  return (
    <Shell title={filing.reference}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/regreporting/filings"
          style={{ fontSize: 13, color: 'var(--ink-muted)' }}>
          ← {lang === 'ar' ? 'العودة لسجل التقديمات' : 'Back to filings'}
        </Link>
      </div>
      <PageHeader
        eyebrow={`${filing.reference} · ${
          filing.regulator
            ? REGULATOR_LABEL[filing.regulator][lang === 'ar' ? 'ar' : 'en']
            : ''
        }`}
        title={(lang === 'ar' ? filing.obligationTitleAr : filing.obligationTitleEn)
          || filing.reference}
        sub={lang === 'ar' ? 'ورشة دورة التقديم' : 'Filing workspace'}
        actions={
          <Badge tone={FILING_STATUS_TONE[filing.status]}>
            {FILING_STATUS_LABEL[filing.status][lang === 'ar' ? 'ar' : 'en']}
          </Badge>
        }
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>
          {error}
        </div>
      )}

      {/* KPIs */}
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
        gap: 12, marginBottom: 16,
      }}>
        <Kpi Icon={CalendarDays} tone="info"
          label={lang === 'ar' ? 'الفترة' : 'Period'}
          value={`${filing.periodStart} → ${filing.periodEnd}`} />
        <Kpi Icon={Clock} tone={
          filing.overdue ? 'danger'
            : (filing.daysUntilDue != null && filing.daysUntilDue <= 7
               ? 'warning' : 'info')}
          label={lang === 'ar' ? 'الاستحقاق' : 'Due'}
          value={
            filing.dueDate
              ? `${filing.dueDate}${
                  filing.daysUntilDue != null
                    ? ` (${filing.daysUntilDue >= 0 ? '+' : ''}${filing.daysUntilDue}${
                        lang === 'ar' ? ' ي' : 'd'})`
                    : ''
                }`
              : '—'
          } />
        <Kpi Icon={Shield} tone="neutral"
          label={lang === 'ar' ? 'الدورية' : 'Frequency'}
          value={
            filing.frequency
              ? FREQUENCY_LABEL[filing.frequency][lang === 'ar' ? 'ar' : 'en']
              : '—'
          } />
        <Kpi Icon={CheckCircle2} tone={
          filing.submittedAt ? 'success' : 'neutral'}
          label={lang === 'ar' ? 'مُقدَّم في' : 'Submitted at'}
          value={
            filing.submittedAt
              ? new Date(filing.submittedAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')
              : '—'
          } />
      </div>

      {/* Action panel */}
      {!terminal && (
        <Card padded
          title={lang === 'ar' ? 'الإجراءات المتاحة' : 'Available actions'}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
            {next.map(to => (
              <Button key={to} variant="secondary"
                onClick={() => { setPendingTo(to); setModal('transition'); }}>
                → {FILING_STATUS_LABEL[to][lang === 'ar' ? 'ar' : 'en']}
              </Button>
            ))}
            {filing.status === 'APPROVED' && (
              <Button variant="primary" Icon={Send}
                onClick={() => setModal('submit')}>
                {lang === 'ar' ? 'تقديم للجهة' : 'Submit to regulator'}
              </Button>
            )}
            {filing.status === 'REJECTED' && (
              <Button variant="primary" Icon={Send}
                onClick={() => setModal('submit')}>
                {lang === 'ar' ? 'إعادة التقديم' : 'Re-submit'}
              </Button>
            )}
            {filing.status === 'SUBMITTED' && (
              <>
                <Button variant="primary" Icon={CheckCircle2}
                  onClick={doAcknowledge} disabled={acting}>
                  {lang === 'ar' ? 'تسجيل إقرار الاستلام' : 'Record acknowledgment'}
                </Button>
                <Button variant="secondary" Icon={XCircle}
                  onClick={() => setModal('reject')}>
                  {lang === 'ar' ? 'رفض من الجهة' : 'Record regulator rejection'}
                </Button>
              </>
            )}
            {inActiveBucket && (
              <Button variant="ghost" Icon={Ban}
                onClick={() => setModal('exempt')}>
                {lang === 'ar' ? 'إعفاء رسمي' : 'Grant formal exemption'}
              </Button>
            )}
          </div>
        </Card>
      )}

      {/* Notes + rejection/exemption context */}
      <div style={{ marginTop: 16,
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
        gap: 12 }}>
        <Card padded title={lang === 'ar' ? 'ملاحظات الإعداد' : 'Preparation notes'}>
          <p style={{ fontSize: 13, whiteSpace: 'pre-wrap' }}>
            {(lang === 'ar' ? filing.preparationNotesAr : filing.preparationNotesEn)
              || (lang === 'ar' ? 'لا توجد ملاحظات.' : 'No notes yet.')}
          </p>
        </Card>
        {filing.regulatorAckReference && (
          <Card padded title={lang === 'ar' ? 'مرجع التقديم' : 'Submission reference'}>
            <div style={{ fontSize: 13, lineHeight: '20px' }}>
              <div><strong>{lang === 'ar' ? 'إقرار الاستلام:' : 'Ack ref:'}</strong>{' '}
                <code>{filing.regulatorAckReference}</code></div>
              {filing.filingArtifactReference && (
                <div><strong>{lang === 'ar' ? 'الملف:' : 'Artifact:'}</strong>{' '}
                  <code>{filing.filingArtifactReference}</code></div>
              )}
              {filing.acknowledgedAt && (
                <div><strong>{lang === 'ar' ? 'تأكيد الاستلام:' : 'Acknowledged:'}</strong>{' '}
                  {new Date(filing.acknowledgedAt).toLocaleString(
                    lang === 'ar' ? 'ar-SA' : 'en-GB')}</div>
              )}
            </div>
          </Card>
        )}
        {(filing.rejectionReasonEn || filing.rejectionReasonAr) && (
          <Card padded title={lang === 'ar' ? 'سبب الرفض' : 'Rejection reason'}>
            <p style={{ fontSize: 13, whiteSpace: 'pre-wrap', color: 'var(--danger)' }}>
              {lang === 'ar' ? filing.rejectionReasonAr : filing.rejectionReasonEn}
            </p>
          </Card>
        )}
        {(filing.exemptionReasonEn || filing.exemptionReasonAr) && (
          <Card padded title={lang === 'ar' ? 'سبب الإعفاء' : 'Exemption reason'}>
            <div style={{ fontSize: 13 }}>
              <div>{lang === 'ar' ? filing.exemptionReasonAr : filing.exemptionReasonEn}</div>
              {filing.exemptionReference && (
                <div style={{ marginTop: 6 }}>
                  <strong>{lang === 'ar' ? 'المرجع:' : 'Ref:'}</strong>{' '}
                  <code>{filing.exemptionReference}</code>
                </div>
              )}
            </div>
          </Card>
        )}
      </div>

      {/* Timeline */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'السجل الزمني' : 'Timeline'}>
          {events.length === 0 ? (
            <InlineEmpty>{lang === 'ar' ? 'لا توجد أحداث.' : 'No events yet.'}</InlineEmpty>
          ) : (
            <ul style={{ listStyle: 'none', padding: 0, margin: 0 }}>
              {events.map(e => (
                <li key={e.id} style={{
                  display: 'grid',
                  gridTemplateColumns: '160px 1fr',
                  padding: '8px 0',
                  borderBottom: '1px solid var(--card-border)',
                  fontSize: 13,
                }}>
                  <div style={{ color: 'var(--ink-muted)' }}>
                    {new Date(e.occurredAt).toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
                  </div>
                  <div>
                    <Badge tone="neutral" className="m-r-6">
                      {EVENT_TYPE_LABEL[e.eventType][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                    {e.statusAfter && (
                      <Badge tone={FILING_STATUS_TONE[e.statusAfter]}>
                        {FILING_STATUS_LABEL[e.statusAfter][lang === 'ar' ? 'ar' : 'en']}
                      </Badge>
                    )}
                    {(e.noteEn || e.noteAr) && (
                      <div style={{ marginTop: 4 }}>
                        {lang === 'ar' ? e.noteAr : e.noteEn}
                      </div>
                    )}
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>

      {/* Modal: transition / submit / reject / exempt */}
      {modal && (
        <div style={{
          position: 'fixed', inset: 0,
          background: 'rgba(0,0,0,0.4)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          zIndex: 50,
        }} onClick={closeModal}>
          <div style={{
            background: 'var(--card-bg)',
            padding: 20, borderRadius: 8,
            minWidth: 420, maxWidth: 600,
          }} onClick={e => e.stopPropagation()}>
            <h3 style={{ marginTop: 0, fontSize: 16 }}>
              {modal === 'transition' && pendingTo && (
                <>{lang === 'ar' ? 'الانتقال إلى' : 'Transition to'}{' '}
                  {FILING_STATUS_LABEL[pendingTo][lang === 'ar' ? 'ar' : 'en']}</>
              )}
              {modal === 'submit' &&
                (lang === 'ar' ? 'تقديم التقرير للجهة الرقابية' : 'Submit to regulator')}
              {modal === 'reject' &&
                (lang === 'ar' ? 'تسجيل رفض من الجهة الرقابية' : 'Record regulator rejection')}
              {modal === 'exempt' &&
                (lang === 'ar' ? 'منح إعفاء رسمي' : 'Grant formal exemption')}
            </h3>

            {modal === 'submit' && (
              <>
                <Field label={lang === 'ar' ? 'رقم إقرار الاستلام' : 'Regulator ack reference'}>
                  <input value={ackRef} onChange={e => setAckRef(e.target.value)}
                    placeholder="SAMA-RBS-2026-05-20-08312"
                    style={inputStyle} />
                </Field>
                <Field label={lang === 'ar' ? 'مرجع الملف المُقدَّم'
                                              : 'Filing artifact reference'}>
                  <input value={artifactRef} onChange={e => setArtifactRef(e.target.value)}
                    placeholder="s3://compliance/sama/2026/05/dlr-2026-05-20.pdf"
                    style={inputStyle} />
                </Field>
              </>
            )}

            {(modal === 'transition' || modal === 'submit'
              || modal === 'reject' || modal === 'exempt') && (
              <>
                <Field label={
                  modal === 'reject'
                    ? (lang === 'ar' ? 'سبب الرفض (إنجليزي)' : 'Rejection reason (English)')
                    : modal === 'exempt'
                      ? (lang === 'ar' ? 'مبرّر الإعفاء (إنجليزي)'
                                        : 'Exemption justification (English)')
                      : (lang === 'ar' ? 'ملاحظة (إنجليزي)' : 'Note (English)')}>
                  <textarea value={noteEn} rows={2}
                    onChange={e => setNoteEn(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
                <Field label={
                  modal === 'reject'
                    ? (lang === 'ar' ? 'سبب الرفض (عربي)' : 'Rejection reason (Arabic)')
                    : modal === 'exempt'
                      ? (lang === 'ar' ? 'مبرّر الإعفاء (عربي)'
                                        : 'Exemption justification (Arabic)')
                      : (lang === 'ar' ? 'ملاحظة (عربي)' : 'Note (Arabic)')}>
                  <textarea value={noteAr} rows={2} dir="rtl"
                    onChange={e => setNoteAr(e.target.value)}
                    style={{ ...inputStyle, fontFamily: 'inherit' }} />
                </Field>
              </>
            )}

            {modal === 'exempt' && (
              <Field label={lang === 'ar' ? 'مرجع الإعفاء' : 'Exemption reference'}>
                <input value={exemptionRef}
                  onChange={e => setExemptionRef(e.target.value)}
                  placeholder="SAMA-EX-2026-019"
                  style={inputStyle} />
              </Field>
            )}

            <div style={{ display: 'flex', gap: 8, marginTop: 16 }}>
              <Button variant="primary" disabled={acting}
                onClick={
                  modal === 'transition' ? doTransition
                    : modal === 'submit' ? doSubmit
                    : modal === 'reject' ? doReject
                    : doExempt
                }>
                {acting
                  ? (lang === 'ar' ? 'جارٍ…' : 'Working…')
                  : (lang === 'ar' ? 'تأكيد' : 'Confirm')}
              </Button>
              <Button variant="secondary" onClick={closeModal} disabled={acting}>
                {lang === 'ar' ? 'إلغاء' : 'Cancel'}
              </Button>
            </div>
          </div>
        </div>
      )}

      {/* Documents & control mapping — filing document, regulator acknowledgement, controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="REG_FILING" entityId={filing.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="REG_FILING" entityId={filing.id} />
          </div>
        </Card>
      </div>
    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  padding: 6,
  border: '1px solid var(--card-border)',
  borderRadius: 4,
  fontSize: 13,
  width: '100%',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4, marginBottom: 8 }}>
      <span style={{ fontSize: 12, fontWeight: 600 }}>{label}</span>
      {children}
    </label>
  );
}
