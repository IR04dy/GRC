import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  FileCheck2, ArrowLeft, ShieldCheck, AlertTriangle,
  Stamp, Edit3, ClipboardCheck, CheckCircle2, XCircle, RotateCcw,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  getObligation,
  listAttestationsForObligation,
  recordAttestation,
  reviewAttestation,
  transitionObligation,
  type Obligation,
  type ObligationAttestation,
  type ReviewerDecision,
  REGULATOR_LABELS,
  TYPE_LABELS,
  FREQUENCY_LABELS,
  SEVERITY_LABELS,
  LIFECYCLE_LABELS,
  ATTESTATION_STATUS_LABELS,
  REVIEWER_DECISION_LABELS,
  lifecycleTone,
  severityTone,
  attestationStatusTone,
} from '../../lib/obligations';

/**
 * Sprint 11 — Obligation detail page.
 *
 * <p>Shows the obligation header (regulator, frequency, owner,
 * next due date, lifecycle), the attestation history, and the
 * "Record attestation" + "Reviewer outcome" modals.
 */
export function ObligationDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();

  const [obligation, setObligation] = useState<Obligation | null>(null);
  const [attestations, setAttestations] = useState<ObligationAttestation[]>([]);
  const [loading, setLoading] = useState(false);

  async function reload() {
    if (!id) return;
    setLoading(true);
    try {
      const [o, list] = await Promise.all([
        getObligation(Number(id)),
        listAttestationsForObligation(Number(id)),
      ]);
      setObligation(o);
      setAttestations(Array.isArray(list) ? list : []);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل التحميل' : 'Failed to load'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { void reload(); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps

  const fmt = (d?: string) =>
    d ? new Date(d).toLocaleString(isAr ? 'ar-SA' : 'en-GB') : '—';
  const fmtDate = (d?: string) =>
    d ? new Date(d).toLocaleDateString(isAr ? 'ar-SA' : 'en-GB') : '—';

  // ─── Actions ────────────────────────────────────────────────────

  async function onRecord() {
    if (!obligation) return;
    const status = await dialog.prompt({
      title: isAr ? 'حالة الإقرار (ATTESTED / ATTESTED_WITH_EXCEPTIONS / NOT_APPLICABLE)'
        : 'Attestation status (ATTESTED / ATTESTED_WITH_EXCEPTIONS / NOT_APPLICABLE)',
      initial: 'ATTESTED',
      required: true,
      validate: v => ['ATTESTED', 'ATTESTED_WITH_EXCEPTIONS', 'NOT_APPLICABLE'].includes(v.trim())
        ? null : (isAr ? 'قيمة غير صحيحة' : 'Invalid value'),
    });
    if (status === null) return;
    const textEn = await dialog.prompt({
      title: isAr ? 'نص الإقرار (إنجليزي)' : 'Attestation text (English)',
      type: 'textarea',
    });
    if (textEn === null) return;
    const textAr = await dialog.prompt({
      title: 'نص الإقرار (عربي)',
      type: 'textarea',
    });
    if (textAr === null) return;
    let exEn: string | null = null;
    let exAr: string | null = null;
    if (status.trim() === 'ATTESTED_WITH_EXCEPTIONS') {
      exEn = await dialog.prompt({
        title: 'Exceptions (English)',
        type: 'textarea',
        required: true,
      });
      if (exEn === null) return;
      exAr = await dialog.prompt({
        title: 'الاستثناءات (عربي)',
        type: 'textarea',
        required: true,
      });
      if (exAr === null) return;
    }
    try {
      await recordAttestation({
        obligationId: obligation.id,
        periodLabel: new Date().toISOString().slice(0, 7),
        dueDate: obligation.nextDueDate,
        attestationStatus: status.trim() as 'ATTESTED' | 'ATTESTED_WITH_EXCEPTIONS' | 'NOT_APPLICABLE',
        attestedByUserId: 1, // current user — wired via JWT claim in v2
        attestationTextEn: textEn || undefined,
        attestationTextAr: textAr || undefined,
        exceptionsNotedEn: exEn || undefined,
        exceptionsNotedAr: exAr || undefined,
      });
      toast.success(isAr ? 'تم تسجيل الإقرار' : 'Attestation recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onReview(att: ObligationAttestation, decision: ReviewerDecision) {
    const commentEn = await dialog.prompt({
      title: isAr ? 'تعليق المراجع (إنجليزي)' : 'Reviewer comment (English)',
      type: 'textarea',
    });
    if (commentEn === null) return;
    const commentAr = await dialog.prompt({
      title: 'تعليق المراجع (عربي)',
      type: 'textarea',
    });
    if (commentAr === null) return;
    try {
      await reviewAttestation(att.id, {
        reviewerDecision: decision,
        reviewerUserId: 1,
        reviewerCommentEn: commentEn || undefined,
        reviewerCommentAr: commentAr || undefined,
      });
      toast.success(isAr ? 'تم تسجيل قرار المراجعة' : 'Review recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  async function onTransition(to: 'ACTIVE' | 'SUSPENDED' | 'RETIRED') {
    if (!obligation) return;
    const ok = await dialog.confirm({
      title: isAr ? `نقل الالتزام إلى "${LIFECYCLE_LABELS[to].ar}"؟`
        : `Transition obligation to "${LIFECYCLE_LABELS[to].en}"?`,
      destructive: to === 'RETIRED',
    });
    if (!ok) return;
    try {
      await transitionObligation(obligation.id, { toStage: to });
      toast.success(isAr ? 'تم النقل' : 'Transitioned');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // ─── Render ─────────────────────────────────────────────────────

  if (loading && !obligation) {
    return (
      <Shell>
        <InlineEmpty text={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
      </Shell>
    );
  }
  if (!obligation) {
    return (
      <Shell>
        <InlineEmpty text={isAr ? 'الالتزام غير موجود' : 'Obligation not found'} />
      </Shell>
    );
  }

  return (
    <Shell>
      <PageHeader
        eyebrow={isAr ? 'الالتزام والامتثال' : 'Compliance'}
        title={isAr ? obligation.titleAr : obligation.titleEn}
        sub={`${obligation.obligationCode} · ${isAr ? REGULATOR_LABELS[obligation.regulator].ar : REGULATOR_LABELS[obligation.regulator].en}`}
        actions={
          <div className="cluster" style={{ gap: 8 }}>
            <Button variant="secondary" Icon={ArrowLeft}
              onClick={() => navigate('/compliance/obligations')}>
              {isAr ? 'رجوع' : 'Back'}
            </Button>
            <Button Icon={Stamp} onClick={onRecord}>
              {isAr ? 'تسجيل إقرار' : 'Record attestation'}
            </Button>
          </div>
        }
      />

      <Card title={isAr ? 'تفاصيل الالتزام' : 'Obligation details'} Icon={FileCheck2}>
        <div className="grid-2col" style={{ gap: 'var(--space-md)' }}>
          <Field label={isAr ? 'الجهة الرقابية' : 'Regulator'}
            value={isAr ? REGULATOR_LABELS[obligation.regulator].ar : REGULATOR_LABELS[obligation.regulator].en} />
          <Field label={isAr ? 'المرجع' : 'Reference'} value={obligation.regulationReference ?? '—'} />
          <Field label={isAr ? 'النوع' : 'Type'}
            value={isAr ? TYPE_LABELS[obligation.obligationType].ar : TYPE_LABELS[obligation.obligationType].en} />
          <Field label={isAr ? 'التكرار' : 'Frequency'}
            value={isAr ? FREQUENCY_LABELS[obligation.frequency].ar : FREQUENCY_LABELS[obligation.frequency].en} />
          <Field label={isAr ? 'الخطورة عند التخلف' : 'Severity if missed'}
            value={
              <Badge tone={severityTone(obligation.severityIfMissed)}>
                {isAr ? SEVERITY_LABELS[obligation.severityIfMissed].ar
                  : SEVERITY_LABELS[obligation.severityIfMissed].en}
              </Badge>
            } />
          <Field label={isAr ? 'الحالة' : 'Lifecycle stage'}
            value={
              <Badge tone={lifecycleTone(obligation.lifecycleStage)}>
                {isAr ? LIFECYCLE_LABELS[obligation.lifecycleStage].ar
                  : LIFECYCLE_LABELS[obligation.lifecycleStage].en}
              </Badge>
            } />
          <Field label={isAr ? 'تاريخ الاستحقاق التالي' : 'Next due'} value={fmtDate(obligation.nextDueDate)} />
          <Field label={isAr ? 'مدّة التذكير المسبق' : 'Lead-time'} value={`${obligation.leadTimeDays} ${isAr ? 'يوماً' : 'days'}`} />
          <Field label={isAr ? 'المسؤول' : 'Owner'} value={`User #${obligation.ownerUserId}`} />
          {obligation.backupOwnerUserId && (
            <Field label={isAr ? 'مسؤول احتياطي' : 'Backup owner'} value={`User #${obligation.backupOwnerUserId}`} />
          )}
          <Field label={isAr ? 'وحدة العمل' : 'Business unit'}
            value={isAr ? (obligation.businessUnitAr ?? '—') : (obligation.businessUnitEn ?? '—')} />
          <Field label={isAr ? 'آخر إقرار' : 'Last attested at'} value={fmt(obligation.lastAttestedAt)} />
        </div>

        {(obligation.descriptionEn || obligation.descriptionAr) && (
          <div style={{ marginTop: 'var(--space-md)', padding: 'var(--space-md)',
            background: 'var(--surface-2)', borderRadius: 6, fontSize: 13, lineHeight: 1.6 }}>
            {isAr ? obligation.descriptionAr : obligation.descriptionEn}
          </div>
        )}

        <div className="cluster" style={{ gap: 8, marginTop: 'var(--space-md)' }}>
          {obligation.lifecycleStage === 'DRAFT' && (
            <Button size="sm" Icon={ShieldCheck} onClick={() => onTransition('ACTIVE')}>
              {isAr ? 'تفعيل' : 'Activate'}
            </Button>
          )}
          {obligation.lifecycleStage === 'ACTIVE' && (
            <Button size="sm" variant="secondary" Icon={Edit3} onClick={() => onTransition('SUSPENDED')}>
              {isAr ? 'تعليق' : 'Suspend'}
            </Button>
          )}
          {obligation.lifecycleStage === 'SUSPENDED' && (
            <Button size="sm" Icon={ShieldCheck} onClick={() => onTransition('ACTIVE')}>
              {isAr ? 'إعادة تفعيل' : 'Re-activate'}
            </Button>
          )}
          {obligation.lifecycleStage !== 'RETIRED' && (
            <Button size="sm" variant="danger" Icon={AlertTriangle} onClick={() => onTransition('RETIRED')}>
              {isAr ? 'تقاعد' : 'Retire'}
            </Button>
          )}
        </div>
      </Card>

      <Card title={isAr ? 'سجل الإقرارات' : 'Attestation history'} Icon={ClipboardCheck}>
        {attestations.length === 0 ? (
          <InlineEmpty text={isAr ? 'لا توجد إقرارات بعد.' : 'No attestations yet.'} />
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table className="grc-table">
              <thead>
                <tr>
                  <th>{isAr ? 'الدورة' : 'Period'}</th>
                  <th>{isAr ? 'الاستحقاق' : 'Due'}</th>
                  <th>{isAr ? 'الحالة' : 'Status'}</th>
                  <th>{isAr ? 'وقت الإقرار' : 'Attested at'}</th>
                  <th>{isAr ? 'قرار المراجع' : 'Reviewer decision'}</th>
                  <th style={{ width: 240 }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                </tr>
              </thead>
              <tbody>
                {attestations.map(a => (
                  <tr key={a.id}>
                    <td style={{ fontSize: 13 }}>{a.periodLabel ?? '—'}</td>
                    <td style={{ fontSize: 13 }}>{fmtDate(a.dueDate)}</td>
                    <td>
                      <Badge tone={attestationStatusTone(a.attestationStatus)}>
                        {isAr ? ATTESTATION_STATUS_LABELS[a.attestationStatus].ar
                          : ATTESTATION_STATUS_LABELS[a.attestationStatus].en}
                      </Badge>
                      {a.exceptionsNotedEn && (
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                          {isAr ? (a.exceptionsNotedAr ?? a.exceptionsNotedEn) : a.exceptionsNotedEn}
                        </div>
                      )}
                    </td>
                    <td style={{ fontSize: 13 }}>{fmt(a.attestedAt)}</td>
                    <td style={{ fontSize: 13 }}>
                      {a.reviewerDecision ? (
                        <Badge tone={a.reviewerDecision === 'ACCEPTED' ? 'success'
                          : a.reviewerDecision === 'REJECTED' ? 'danger' : 'warning'}>
                          {isAr ? REVIEWER_DECISION_LABELS[a.reviewerDecision].ar
                            : REVIEWER_DECISION_LABELS[a.reviewerDecision].en}
                        </Badge>
                      ) : '—'}
                      {a.reviewerCommentEn && (
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                          {isAr ? (a.reviewerCommentAr ?? a.reviewerCommentEn) : a.reviewerCommentEn}
                        </div>
                      )}
                    </td>
                    <td>
                      {(a.attestationStatus === 'ATTESTED' || a.attestationStatus === 'ATTESTED_WITH_EXCEPTIONS') && (
                        <div className="cluster" style={{ gap: 6 }}>
                          <Button size="sm" Icon={CheckCircle2} onClick={() => onReview(a, 'ACCEPTED')}>
                            {isAr ? 'قبول' : 'Accept'}
                          </Button>
                          <Button size="sm" variant="danger" Icon={XCircle} onClick={() => onReview(a, 'REJECTED')}>
                            {isAr ? 'رفض' : 'Reject'}
                          </Button>
                          <Button size="sm" variant="secondary" Icon={RotateCcw} onClick={() => onReview(a, 'REQUEST_CHANGES')}>
                            {isAr ? 'تعديل' : 'Changes'}
                          </Button>
                        </div>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Shell>
  );
}

function Field({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.5 }}>
        {label}
      </div>
      <div style={{ fontSize: 14, marginTop: 4 }}>{value}</div>
    </div>
  );
}
