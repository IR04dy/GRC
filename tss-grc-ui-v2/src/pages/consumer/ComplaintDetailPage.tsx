import { useCallback, useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import {
  ArrowLeft, CheckCircle2, MessageSquare, Send, ShieldAlert, UserCog,
  type LucideIcon,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { Card } from '../../components/ui/Card';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ControlMappingPicker } from '../../components/ui/ControlMappingPicker';
import { Badge } from '../../components/ui/Badge';
import { useI18n } from '../../lib/i18n';
import { ApiError } from '../../lib/api';
import { canApprove, hasAnyRole } from '../../lib/auth';
import {
  complaintsApi,
  CATEGORY_LABEL, SEVERITY_LABEL, SEVERITY_TONE,
  STATUS_LABEL, STATUS_TONE,
  CHANNEL_LABEL, CUSTOMER_ID_TYPE_LABEL, UPDATE_TYPE_LABEL,
  NEXT_STATUSES,
  type ComplaintResponse, type ComplaintUpdateResponse, type ComplaintStatus,
} from '../../lib/complaints';

/**
 * Internal Complaint Detail page — /complaints/:id.
 *
 * <p>Left column: customer + complaint details, channel, category,
 * bilingual description, product info, account_last4.
 * <p>Right column: timeline of update rows (chronological).
 * <p>Action bar: permission-gated buttons mapped to the backend state machine
 * (acknowledge, transition, assign, escalate-to-SAMA, reopen).
 */
export function ComplaintDetailPage() {
  const { lang, t } = useI18n();
  const { id: idParam } = useParams<{ id: string }>();
  const id = Number(idParam);

  // F2-145(e) slice 3 — mirror the server, which now guards with hasPermission('CONSUMER', …).
  // This read `hasAnyRole('PLATFORM_ADMIN','CONSUMER_PROTECTION_OFFICER','COMPLIANCE_OFFICER')`, and
  // leaving it there would have been the divergence this programme objects to: a client gate that
  // recites names the server has stopped consulting, so a role granted CONSUMER:WRITE some other way
  // would see disabled buttons for an action the server allows. The populations are identical today,
  // which is exactly why the mismatch would have gone unnoticed.
  const canAct = hasAnyRole('PLATFORM_ADMIN', 'CONSUMER_WRITE');
  // Escalating to SAMA is CONSUMER:APPROVE server-side — an outbound regulatory act, the analogue of
  // openbanking's notifySama. Separate helper so a tenant that splits the two sees the split here.
  const canEscalateToSama = canApprove('CONSUMER');

  const [complaint, setComplaint] = useState<ComplaintResponse | null>(null);
  const [updates, setUpdates] = useState<ComplaintUpdateResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  // Inline action drawer state.
  const [drawer, setDrawer] = useState<null | 'transition' | 'assign' | 'sama'>(null);
  const [transitionTo, setTransitionTo] = useState<ComplaintStatus | ''>('');
  const [noteEn, setNoteEn] = useState('');
  const [noteAr, setNoteAr] = useState('');
  const [customerVisible, setCustomerVisible] = useState(false);
  const [resolutionSummaryEn, setResolutionSummaryEn] = useState('');
  const [resolutionSummaryAr, setResolutionSummaryAr] = useState('');
  const [compensation, setCompensation] = useState('');
  const [rootCauseCode, setRootCauseCode] = useState('');
  const [assignedToInput, setAssignedToInput] = useState('');
  const [samaRef, setSamaRef] = useState('');

  const load = useCallback(async () => {
    if (!id) return;
    setLoading(true);
    setError(null);
    try {
      const [c, u] = await Promise.all([
        complaintsApi.get(id),
        complaintsApi.updates(id),
      ]);
      setComplaint(c);
      setUpdates(u);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الشكوى' : 'Failed to load complaint'));
    } finally {
      setLoading(false);
    }
  }, [id, lang]);
  useEffect(() => { load(); }, [load]);

  const allowedNext = useMemo<ComplaintStatus[]>(() => {
    if (!complaint) return [];
    return NEXT_STATUSES[complaint.status] ?? [];
  }, [complaint]);

  function resetDrawer() {
    setDrawer(null);
    setTransitionTo('');
    setNoteEn(''); setNoteAr('');
    setCustomerVisible(false);
    setResolutionSummaryEn(''); setResolutionSummaryAr('');
    setCompensation(''); setRootCauseCode('');
    setAssignedToInput('');
    setSamaRef('');
  }

  async function handleAcknowledge() {
    if (!complaint) return;
    setBusy(true);
    try {
      await complaintsApi.acknowledge(complaint.id);
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر الإقرار' : 'Failed to acknowledge'));
    } finally {
      setBusy(false);
    }
  }

  async function handleTransition() {
    if (!complaint || !transitionTo) return;
    setBusy(true);
    try {
      await complaintsApi.transition(complaint.id, {
        toStatus: transitionTo,
        noteEn: noteEn || undefined,
        noteAr: noteAr || undefined,
        customerVisible,
        resolutionSummaryEn: transitionTo === 'RESOLVED' && resolutionSummaryEn ? resolutionSummaryEn : undefined,
        resolutionSummaryAr: transitionTo === 'RESOLVED' && resolutionSummaryAr ? resolutionSummaryAr : undefined,
        compensationAmountSar: transitionTo === 'RESOLVED' && compensation ? Number(compensation) : undefined,
        rootCauseCode: transitionTo === 'RESOLVED' && rootCauseCode ? rootCauseCode : undefined,
      });
      resetDrawer();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تغيير الحالة' : 'Failed to change status'));
    } finally {
      setBusy(false);
    }
  }

  async function handleAssign() {
    if (!complaint || !assignedToInput.trim()) return;
    setBusy(true);
    try {
      await complaintsApi.assign(complaint.id, { assignedTo: assignedToInput.trim() });
      resetDrawer();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر الإسناد' : 'Failed to assign'));
    } finally {
      setBusy(false);
    }
  }

  async function handleEscalateSama() {
    if (!complaint || !samaRef.trim()) return;
    setBusy(true);
    try {
      await complaintsApi.escalateSama(complaint.id, {
        samaReferenceNo: samaRef.trim(),
        noteEn: noteEn || undefined,
        noteAr: noteAr || undefined,
      });
      resetDrawer();
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر الإحالة لساما' : 'Failed to escalate to SAMA'));
    } finally {
      setBusy(false);
    }
  }

  async function handleReopen() {
    if (!complaint) return;
    setBusy(true);
    try {
      await complaintsApi.transition(complaint.id, {
        toStatus: 'REOPENED',
        noteEn: lang === 'ar' ? undefined : 'Customer requested reopening',
        noteAr: lang === 'ar' ? 'طلب العميل إعادة فتح الشكوى' : undefined,
        customerVisible: true,
      });
      await load();
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إعادة الفتح' : 'Failed to reopen'));
    } finally {
      setBusy(false);
    }
  }

  function fmtDate(iso?: string): string {
    if (!iso) return '—';
    try {
      const d = new Date(iso);
      return d.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-GB',
        { year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' });
    } catch { return iso; }
  }

  if (loading || !complaint) {
    return (
      <Shell>
        <div style={{ padding: 24, color: 'var(--ink-3)' }}>
          {error ?? (lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…')}
        </div>
      </Shell>
    );
  }

  const canAcknowledge = canAct && complaint.status === 'RECEIVED';
  const canTransition = canAct && allowedNext.length > 0
    && complaint.status !== 'CLOSED' && complaint.status !== 'RESOLVED';
  const canAssign = canAct
    && complaint.status !== 'CLOSED' && complaint.status !== 'REJECTED';
  // canEscalateToSama, not canAct: the server guards this one with CONSUMER:APPROVE.
  const canEscalate = canEscalateToSama && complaint.status === 'UNDER_INVESTIGATION';
  const canReopen = canAct && (complaint.status === 'RESOLVED' || complaint.status === 'CLOSED');

  return (
    <Shell>
      <div style={{ marginBottom: 12 }}>
        <Link to="/complaints" style={{
          display: 'inline-flex', alignItems: 'center', gap: 6,
          fontSize: 13, color: 'var(--ink-2)', textDecoration: 'none',
        }}>
          <ArrowLeft size={14} />
          {t('complaints.title')}
        </Link>
      </div>

      {/* Header */}
      <Card padded>
        <div style={{ display: 'flex', alignItems: 'flex-start', gap: 16, flexWrap: 'wrap' }}>
          <div style={{ flex: 1, minWidth: 280 }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase',
              letterSpacing: '0.06em', marginBottom: 4 }}>
              {t('complaints.complaintNo')}
            </div>
            <h2 style={{ margin: 0, fontSize: 22, fontWeight: 700 }}>
              <code>{complaint.complaintNo}</code>
            </h2>
            <div style={{ display: 'flex', gap: 8, marginTop: 10, flexWrap: 'wrap' }}>
              <Badge tone={STATUS_TONE[complaint.status]} dot>
                {STATUS_LABEL[complaint.status][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              <Badge tone={SEVERITY_TONE[complaint.severity]}>
                {SEVERITY_LABEL[complaint.severity][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              <Badge tone="neutral">
                {CATEGORY_LABEL[complaint.category][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
              <Badge tone="neutral">
                {CHANNEL_LABEL[complaint.channel][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            </div>
          </div>

          {/* Action bar */}
          {canAct && (
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {canAcknowledge && (
                <ActionBtn Icon={CheckCircle2} onClick={handleAcknowledge} disabled={busy} tone="primary">
                  {t('complaints.acknowledgeBtn')}
                </ActionBtn>
              )}
              {canTransition && (
                <ActionBtn Icon={MessageSquare} onClick={() => setDrawer('transition')} disabled={busy}>
                  {t('complaints.transitionBtn')}
                </ActionBtn>
              )}
              {canAssign && (
                <ActionBtn Icon={UserCog} onClick={() => setDrawer('assign')} disabled={busy}>
                  {t('complaints.assignBtn')}
                </ActionBtn>
              )}
              {canEscalate && (
                <ActionBtn Icon={Send} onClick={() => setDrawer('sama')} disabled={busy} tone="danger">
                  {t('complaints.escalateBtn')}
                </ActionBtn>
              )}
              {canReopen && (
                <ActionBtn Icon={ShieldAlert} onClick={handleReopen} disabled={busy} tone="warning">
                  {t('complaints.reopenBtn')}
                </ActionBtn>
              )}
            </div>
          )}
        </div>

        {error && (
          <div style={{
            marginTop: 12, padding: '10px 14px', borderRadius: 'var(--r-sm)',
            background: 'var(--danger-bg)', color: 'var(--danger-fg)', fontSize: 13,
          }}>{error}</div>
        )}
      </Card>

      {/* Inline drawer */}
      {drawer === 'transition' && (
        <div style={{ marginTop: 12 }}>
        <Card padded>
          <h3 style={{ marginTop: 0, fontSize: 15, fontWeight: 600 }}>{t('complaints.transitionBtn')}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Field label={t('complaints.status')}>
              <select value={transitionTo} onChange={e => setTransitionTo(e.target.value as ComplaintStatus | '')}
                style={inputStyle}>
                <option value="">{lang === 'ar' ? 'اختر الحالة الجديدة' : 'Select target status'}</option>
                {allowedNext.map(s => (
                  <option key={s} value={s}>{STATUS_LABEL[s][lang === 'ar' ? 'ar' : 'en']}</option>
                ))}
              </select>
            </Field>
            <Field label={t('complaints.noteEn')}>
              <textarea value={noteEn} onChange={e => setNoteEn(e.target.value)} rows={2}
                style={{ ...inputStyle, resize: 'vertical' }} />
            </Field>
            <Field label={t('complaints.noteAr')}>
              <textarea value={noteAr} onChange={e => setNoteAr(e.target.value)} rows={2} dir="rtl"
                style={{ ...inputStyle, resize: 'vertical' }} />
            </Field>
            <label style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13, cursor: 'pointer' }}>
              <input type="checkbox" checked={customerVisible}
                onChange={e => setCustomerVisible(e.target.checked)} />
              {t('complaints.customerVisible')}
            </label>
            {transitionTo === 'RESOLVED' && (
              <>
                <Field label={t('complaints.resolution') + ' (EN)'}>
                  <textarea value={resolutionSummaryEn} onChange={e => setResolutionSummaryEn(e.target.value)}
                    rows={2} style={{ ...inputStyle, resize: 'vertical' }} />
                </Field>
                <Field label={t('complaints.resolution') + ' (AR)'}>
                  <textarea value={resolutionSummaryAr} onChange={e => setResolutionSummaryAr(e.target.value)}
                    rows={2} dir="rtl" style={{ ...inputStyle, resize: 'vertical' }} />
                </Field>
                <Field label={t('complaints.compensation')}>
                  <input type="number" min="0" step="0.01" value={compensation}
                    onChange={e => setCompensation(e.target.value)} style={inputStyle} />
                </Field>
                <Field label={t('complaints.rootCauseCode')}>
                  <input value={rootCauseCode} onChange={e => setRootCauseCode(e.target.value)}
                    style={inputStyle} />
                </Field>
              </>
            )}
            <DrawerActions onCancel={resetDrawer}
              onConfirm={handleTransition} confirmLabel={t('complaints.transitionBtn')}
              disabled={busy || !transitionTo} />
          </div>
        </Card>
        </div>
      )}

      {drawer === 'assign' && (
        <div style={{ marginTop: 12 }}>
        <Card padded>
          <h3 style={{ marginTop: 0, fontSize: 15, fontWeight: 600 }}>{t('complaints.assignBtn')}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Field label={t('complaints.assignedTo')}>
              <input value={assignedToInput} onChange={e => setAssignedToInput(e.target.value)}
                style={inputStyle} placeholder={lang === 'ar' ? 'اسم المحقق' : 'Investigator username or name'} />
            </Field>
            <DrawerActions onCancel={resetDrawer} onConfirm={handleAssign}
              confirmLabel={t('complaints.assignBtn')}
              disabled={busy || !assignedToInput.trim()} />
          </div>
        </Card>
        </div>
      )}

      {drawer === 'sama' && (
        <div style={{ marginTop: 12 }}>
        <Card padded>
          <h3 style={{ marginTop: 0, fontSize: 15, fontWeight: 600 }}>{t('complaints.escalateBtn')}</h3>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
            <Field label={t('complaints.samaReference')}>
              <input value={samaRef} onChange={e => setSamaRef(e.target.value)}
                style={inputStyle} placeholder="SAMA-2026-…" />
            </Field>
            <Field label={t('complaints.noteEn')}>
              <textarea value={noteEn} onChange={e => setNoteEn(e.target.value)} rows={2}
                style={{ ...inputStyle, resize: 'vertical' }} />
            </Field>
            <Field label={t('complaints.noteAr')}>
              <textarea value={noteAr} onChange={e => setNoteAr(e.target.value)} rows={2} dir="rtl"
                style={{ ...inputStyle, resize: 'vertical' }} />
            </Field>
            <DrawerActions onCancel={resetDrawer} onConfirm={handleEscalateSama}
              confirmLabel={t('complaints.escalateBtn')}
              disabled={busy || !samaRef.trim()} />
          </div>
        </Card>
        </div>
      )}

      {/* Two-column body */}
      <div style={{
        marginTop: 12, display: 'grid',
        gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
        gap: 12,
      }}>
        <Card padded>
          <h3 style={{ marginTop: 0, fontSize: 14, fontWeight: 600 }}>
            {lang === 'ar' ? 'تفاصيل الشكوى' : 'Complaint details'}
          </h3>
          <DetailGrid>
            <DetailRow label={t('complaints.customerName')}
              value={lang === 'ar'
                ? (complaint.customerNameAr ?? complaint.customerNameEn ?? '—')
                : (complaint.customerNameEn ?? complaint.customerNameAr ?? '—')} />
            <DetailRow label={t('complaints.customerCity')} value={complaint.customerCity ?? '—'} />
            {complaint.customerIdType && (
              <DetailRow label={t('complaints.customerIdType')}
                value={CUSTOMER_ID_TYPE_LABEL[complaint.customerIdType][lang === 'ar' ? 'ar' : 'en']} />
            )}
            <DetailRow label={t('complaints.customerIdLast4')} value={complaint.customerIdLast4
              ? `**** ${complaint.customerIdLast4}` : '—'} />
            <DetailRow label={t('complaints.customerPhone')} value={complaint.customerPhone ?? '—'} mono />
            <DetailRow label={t('complaints.customerEmail')} value={complaint.customerEmail ?? '—'} mono />
            <DetailRow label={t('complaints.productCode')} value={complaint.productCode ?? '—'} mono />
            <DetailRow label={t('complaints.accountLast4')} value={complaint.accountLast4
              ? `**** ${complaint.accountLast4}` : '—'} mono />
            <DetailRow label={t('complaints.subcategory')} value={complaint.subcategory ?? '—'} />
            <DetailRow label={t('complaints.assignedTo')} value={complaint.assignedTo ?? '—'} />
            <DetailRow label={t('complaints.submittedOn')} value={fmtDate(complaint.submittedOn)} />
            <DetailRow label={lang === 'ar' ? 'مدّة الإقرار حتى' : 'Ack SLA due'}
              value={fmtDate(complaint.ackSlaDueOn)} />
            <DetailRow label={lang === 'ar' ? 'مدّة الحل حتى' : 'Resolution SLA due'}
              value={fmtDate(complaint.resolutionSlaDueOn)} />
            {complaint.samaReferenceNo && (
              <DetailRow label={t('complaints.samaReference')} value={complaint.samaReferenceNo} mono />
            )}
            {complaint.compensationAmountSar !== undefined && complaint.compensationAmountSar !== null && (
              <DetailRow label={t('complaints.compensation')}
                value={String(complaint.compensationAmountSar)} mono />
            )}
          </DetailGrid>

          <div style={{ marginTop: 14 }}>
            <div style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 500,
              textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
              {t('complaints.descriptionAr')}
            </div>
            <div dir="rtl" style={{
              padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
              fontSize: 13, lineHeight: 1.7, whiteSpace: 'pre-wrap',
            }}>{complaint.descriptionAr ?? '—'}</div>
          </div>
          {complaint.descriptionEn && (
            <div style={{ marginTop: 10 }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', fontWeight: 500,
                textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
                {t('complaints.descriptionEn')}
              </div>
              <div style={{
                padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                fontSize: 13, lineHeight: 1.7, whiteSpace: 'pre-wrap',
              }}>{complaint.descriptionEn}</div>
            </div>
          )}

          {(complaint.resolutionSummaryEn || complaint.resolutionSummaryAr) && (
            <div style={{ marginTop: 14 }}>
              <div style={{ fontSize: 11, color: 'var(--success-fg)', fontWeight: 500,
                textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>
                {t('complaints.resolution')}
              </div>
              <div style={{
                padding: 12, background: 'var(--success-bg)', borderRadius: 'var(--r-sm)',
                fontSize: 13, lineHeight: 1.7, whiteSpace: 'pre-wrap',
                borderInlineStart: '3px solid var(--success)',
              }} dir={lang === 'ar' ? 'rtl' : 'ltr'}>
                {lang === 'ar'
                  ? (complaint.resolutionSummaryAr ?? complaint.resolutionSummaryEn)
                  : (complaint.resolutionSummaryEn ?? complaint.resolutionSummaryAr)}
              </div>
            </div>
          )}
        </Card>

        {/* Timeline */}
        <Card padded>
          <h3 style={{ marginTop: 0, fontSize: 14, fontWeight: 600 }}>{t('complaints.timeline')}</h3>
          {updates.length === 0 ? (
            <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)', fontSize: 13 }}>
              {lang === 'ar' ? 'لا توجد تحديثات بعد.' : 'No updates yet.'}
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {updates.map(u => (
                <div key={u.id} style={{
                  padding: 12, background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                  borderInlineStart: '3px solid ' + (u.customerVisible ? 'var(--info)' : 'var(--ink-4)'),
                }}>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap',
                    fontSize: 12, color: 'var(--ink-3)' }}>
                    <Badge tone="neutral">
                      {UPDATE_TYPE_LABEL[u.updateType]?.[lang === 'ar' ? 'ar' : 'en'] ?? u.updateType}
                    </Badge>
                    {u.toStatus && (
                      <Badge tone={STATUS_TONE[u.toStatus]} dot>
                        {STATUS_LABEL[u.toStatus][lang === 'ar' ? 'ar' : 'en']}
                      </Badge>
                    )}
                    <Badge tone={u.customerVisible ? 'info' : 'neutral'}>
                      {u.customerVisible ? t('complaints.customerVisible') : t('complaints.internalOnly')}
                    </Badge>
                    <span style={{ marginInlineStart: 'auto' }}>{fmtDate(u.occurredOn)}</span>
                  </div>
                  {(u.noteEn || u.noteAr) && (
                    <div style={{ marginTop: 8, fontSize: 13, lineHeight: 1.6, whiteSpace: 'pre-wrap' }}
                      dir={lang === 'ar' ? 'rtl' : 'ltr'}>
                      {lang === 'ar' ? (u.noteAr ?? u.noteEn) : (u.noteEn ?? u.noteAr)}
                    </div>
                  )}
                  {u.actor && (
                    <div style={{ marginTop: 6, fontSize: 11, color: 'var(--ink-3)' }}>
                      {t('complaints.actor')}: <strong>{u.actor}</strong>
                    </div>
                  )}
                </div>
              ))}
            </div>
          )}
        </Card>
      </div>

      {/* Documents & control mapping — complaint letter, resolution evidence, controls */}
      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'المستندات وربط الضوابط' : 'Documents & control mapping'}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 18 }}>
            <AttachmentsField entityType="COMPLAINT" entityId={complaint.id} />
            <div style={{ borderTop: '1px solid var(--border)' }} />
            <ControlMappingPicker entityType="COMPLAINT" entityId={complaint.id} />
          </div>
        </Card>
      </div>

    </Shell>
  );
}

const inputStyle: React.CSSProperties = {
  width: '100%', padding: '8px 10px', fontSize: 13,
  border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)', color: 'var(--ink-1)', boxSizing: 'border-box',
};

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label style={{ display: 'block' }}>
      <span style={{ display: 'block', fontSize: 11, fontWeight: 500, color: 'var(--ink-3)',
        textTransform: 'uppercase', letterSpacing: '0.04em', marginBottom: 4 }}>{label}</span>
      {children}
    </label>
  );
}

function DetailGrid({ children }: { children: React.ReactNode }) {
  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: 8 }}>{children}</div>
  );
}

function DetailRow({ label, value, mono }: { label: string; value: string; mono?: boolean }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{label}</div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)',
        fontFamily: mono ? 'var(--font-mono, monospace)' : undefined }}>{value}</div>
    </div>
  );
}

function ActionBtn({ Icon, onClick, disabled, tone, children }: {
  Icon: LucideIcon;
  onClick: () => void;
  disabled?: boolean;
  tone?: 'primary' | 'danger' | 'warning';
  children: React.ReactNode;
}) {
  const palette = tone === 'danger' ? { bg: 'var(--danger)', fg: '#fff' }
                : tone === 'warning' ? { bg: 'var(--warning)', fg: '#fff' }
                : tone === 'primary' ? { bg: 'var(--primary)', fg: '#fff' }
                : { bg: 'var(--surface-1)', fg: 'var(--ink-1)' };
  return (
    <button type="button" onClick={onClick} disabled={disabled}
      style={{
        display: 'inline-flex', alignItems: 'center', gap: 6,
        padding: '8px 14px', fontSize: 13, fontWeight: 500,
        background: palette.bg, color: palette.fg,
        border: tone ? 'none' : '1px solid var(--border)',
        borderRadius: 'var(--r-sm)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.55 : 1,
      }}>
      <Icon size={14} />
      {children}
    </button>
  );
}

function DrawerActions({ onCancel, onConfirm, confirmLabel, disabled }: {
  onCancel: () => void;
  onConfirm: () => void;
  confirmLabel: string;
  disabled?: boolean;
}) {
  const { lang } = useI18n();
  return (
    <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 8 }}>
      <button onClick={onCancel} style={{
        padding: '8px 14px', fontSize: 13, fontWeight: 500,
        background: 'transparent', color: 'var(--ink-2)',
        border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
        cursor: 'pointer',
      }}>{lang === 'ar' ? 'إلغاء' : 'Cancel'}</button>
      <button onClick={onConfirm} disabled={disabled} style={{
        padding: '8px 14px', fontSize: 13, fontWeight: 500,
        background: 'var(--primary)', color: '#fff',
        border: 'none', borderRadius: 'var(--r-sm)',
        cursor: disabled ? 'not-allowed' : 'pointer',
        opacity: disabled ? 0.55 : 1,
      }}>{confirmLabel}</button>
    </div>
  );
}
