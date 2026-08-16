import { useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  FileCheck2, ArrowLeft, ShieldCheck, AlertTriangle,
  Stamp, Edit3, ClipboardCheck, CheckCircle2, XCircle, RotateCcw, Pencil, Paperclip, Download,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Modal } from '../../components/ui/Modal';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { useI18n } from '../../lib/i18n';
import { tlabel } from '../../lib/labels';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import { ApiError } from '../../lib/api';
import {
  getObligation,
  getObligationEvidencePackage,
  listAttestationsForObligation,
  recordAttestation,
  reviewAttestation,
  transitionObligation,
  updateObligation,
  type Obligation,
  type ObligationAttestation,
  type ReviewerDecision,
  type ObligationUpdateRequest,
  type Regulator,
  type ObligationType,
  type Frequency,
  type Severity,
  REGULATORS,
  TYPES,
  FREQUENCIES,
  SEVERITIES,
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
  const [editOpen, setEditOpen] = useState(false);
  const [docsTarget, setDocsTarget] = useState<{ entityType: string; entityId: number; label: string } | null>(null);

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

  // C-11: export this obligation's audit evidence-package manifest as a JSON
  // artefact — the obligation, its attachments, and every attestation cycle's
  // uploaded evidence (sha256 + download links).
  async function onExportPack() {
    if (!obligation) return;
    try {
      const pkg = await getObligationEvidencePackage(obligation.id);
      const blob = new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `evidence-pack-${obligation.obligationCode}.json`;
      document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
      const n = pkg.obligationAttachments.length
        + pkg.attestations.reduce((s, x) => s + x.attachments.length, 0);
      toast.success(isAr ? `تم تصدير حزمة الأدلة (${n} مرفق)` : `Evidence pack exported (${n} attachments)`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التصدير' : 'Export failed'));
    }
  }

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
        // F2-224 — the attester is no longer sent. This line read
        // `attestedByUserId: 1, // current user — wired via JWT claim in v2`, so every
        // attestation recorded from this screen was filed against user 1 regardless of who was
        // signed in. The server now takes it from the authenticated principal, which is what
        // the comment was promising; "v2" is here.
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
        // F2-224 — reviewerUserId: 1 removed; the server stamps the principal.
        reviewerCommentEn: commentEn || undefined,
        reviewerCommentAr: commentAr || undefined,
      });
      toast.success(isAr ? 'تم تسجيل قرار المراجعة' : 'Review recorded');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed');
    }
  }

  // Edit the obligation's descriptive / classification / scheduling fields.
  // Owner + backup-owner pickers and the related control/risk links are left
  // out of this form; PATCH is null-means-keep, so omitting them preserves
  // the current values. Lifecycle stage is owned by the transition actions.
  const editFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 6, maxLength: 255,
      label: isAr ? 'العنوان (إنجليزي)' : 'Title (English)' },
    { kind: 'text', key: 'titleAr', required: true, span: 6, maxLength: 255,
      label: isAr ? 'العنوان (عربي)' : 'Title (Arabic)' },
    { kind: 'select', key: 'regulator', required: true, span: 4,
      label: isAr ? 'الجهة الرقابية' : 'Regulator',
      options: REGULATORS.map(r => ({ value: r, label: tlabel(REGULATOR_LABELS, r, isAr) })) },
    { kind: 'select', key: 'obligationType', required: true, span: 4,
      label: isAr ? 'النوع' : 'Type',
      options: TYPES.map(t => ({ value: t, label: tlabel(TYPE_LABELS, t, isAr) })) },
    { kind: 'select', key: 'frequency', required: true, span: 4,
      label: isAr ? 'التكرار' : 'Frequency',
      options: FREQUENCIES.map(f => ({ value: f, label: tlabel(FREQUENCY_LABELS, f, isAr) })) },
    { kind: 'select', key: 'severityIfMissed', required: true, span: 4,
      label: isAr ? 'الخطورة عند التخلف' : 'Severity if missed',
      options: SEVERITIES.map(s => ({ value: s, label: tlabel(SEVERITY_LABELS, s, isAr) })) },
    { kind: 'date', key: 'nextDueDate', span: 4,
      label: isAr ? 'تاريخ الاستحقاق التالي' : 'Next due date' },
    { kind: 'number', key: 'leadTimeDays', span: 4, min: 0,
      label: isAr ? 'مدّة التذكير المسبق (أيام)' : 'Lead-time (days)' },
    { kind: 'text', key: 'regulationReference', span: 12, maxLength: 255,
      label: isAr ? 'المرجع التنظيمي' : 'Regulation reference' },
    { kind: 'text', key: 'businessUnitEn', span: 6, maxLength: 255,
      label: isAr ? 'وحدة العمل (إنجليزي)' : 'Business unit (English)' },
    { kind: 'text', key: 'businessUnitAr', span: 6, maxLength: 255,
      label: isAr ? 'وحدة العمل (عربي)' : 'Business unit (Arabic)' },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)' },
    { kind: 'textarea', key: 'descriptionAr', span: 12, rows: 2,
      label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)' },
  ];

  const editInitial: Record<string, unknown> = obligation ? {
    titleEn:             obligation.titleEn,
    titleAr:             obligation.titleAr,
    regulator:           obligation.regulator,
    obligationType:      obligation.obligationType,
    frequency:           obligation.frequency,
    severityIfMissed:    obligation.severityIfMissed,
    nextDueDate:         obligation.nextDueDate ? obligation.nextDueDate.slice(0, 10) : '',
    leadTimeDays:        obligation.leadTimeDays,
    regulationReference: obligation.regulationReference ?? '',
    businessUnitEn:      obligation.businessUnitEn ?? '',
    businessUnitAr:      obligation.businessUnitAr ?? '',
    descriptionEn:       obligation.descriptionEn ?? '',
    descriptionAr:       obligation.descriptionAr ?? '',
  } : {};

  async function submitEdit(values: Record<string, unknown>) {
    if (!obligation) return;
    const titleEn = String(values.titleEn ?? '').trim();
    const titleAr = String(values.titleAr ?? '').trim();
    if (!titleEn || !titleAr) {
      toast.danger(isAr ? 'العنوان بالإنجليزية والعربية مطلوب' : 'English and Arabic titles are required');
      return;
    }
    const str = (v: unknown) => { const s = String(v ?? '').trim(); return s || undefined; };
    const num = (v: unknown) => { const n = Number(v); return v === '' || v == null || !Number.isFinite(n) ? undefined : n; };
    const body: ObligationUpdateRequest = {
      titleEn,
      titleAr,
      descriptionEn: str(values.descriptionEn),
      descriptionAr: str(values.descriptionAr),
      regulator: String(values.regulator ?? '').trim() as Regulator,
      regulationReference: str(values.regulationReference),
      obligationType: String(values.obligationType ?? '').trim() as ObligationType,
      frequency: String(values.frequency ?? '').trim() as Frequency,
      severityIfMissed: String(values.severityIfMissed ?? '').trim() as Severity,
      nextDueDate: str(values.nextDueDate),
      leadTimeDays: num(values.leadTimeDays),
      businessUnitEn: str(values.businessUnitEn),
      businessUnitAr: str(values.businessUnitAr),
    };
    try {
      await updateObligation(obligation.id, body);
      toast.success(isAr ? 'تم تحديث الالتزام' : 'Obligation updated');
      setEditOpen(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التحديث' : 'Update failed'));
    }
  }

  async function onTransition(to: 'ACTIVE' | 'SUSPENDED' | 'RETIRED') {
    if (!obligation) return;
    const ok = await dialog.confirm({
      title: isAr ? `نقل الالتزام إلى "${tlabel(LIFECYCLE_LABELS, to, true)}"؟`
        : `Transition obligation to "${tlabel(LIFECYCLE_LABELS, to, false)}"?`,
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
        sub={`${obligation.obligationCode} · ${tlabel(REGULATOR_LABELS, obligation.regulator, isAr)}`}
        actions={
          <div className="cluster" style={{ gap: 8 }}>
            <Button variant="secondary" Icon={ArrowLeft}
              onClick={() => navigate('/compliance/obligations')}>
              {isAr ? 'رجوع' : 'Back'}
            </Button>
            <Button variant="secondary" Icon={Pencil} onClick={() => setEditOpen(true)}>
              {isAr ? 'تعديل' : 'Edit'}
            </Button>
            <Button variant="secondary" Icon={Paperclip}
              onClick={() => setDocsTarget({ entityType: 'Obligation', entityId: obligation.id, label: isAr ? 'وثائق الالتزام والأدلة' : 'Obligation documents & evidence' })}>
              {isAr ? 'الوثائق' : 'Documents'}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={onExportPack}>
              {isAr ? 'حزمة الأدلة' : 'Evidence pack'}
            </Button>
            <Button Icon={Stamp} onClick={onRecord}>
              {isAr ? 'تسجيل إقرار' : 'Record attestation'}
            </Button>
          </div>
        }
      />

      <Card title={isAr ? 'تفاصيل الالتزام' : 'Obligation details'} Icon={FileCheck2}>
        <div className="grid-2col" style={{ gap: 'var(--sp-6)' }}>
          <Field label={isAr ? 'الجهة الرقابية' : 'Regulator'}
            value={tlabel(REGULATOR_LABELS, obligation.regulator, isAr)} />
          <Field label={isAr ? 'المرجع' : 'Reference'} value={obligation.regulationReference ?? '—'} />
          <Field label={isAr ? 'النوع' : 'Type'}
            value={tlabel(TYPE_LABELS, obligation.obligationType, isAr)} />
          <Field label={isAr ? 'التكرار' : 'Frequency'}
            value={tlabel(FREQUENCY_LABELS, obligation.frequency, isAr)} />
          <Field label={isAr ? 'الخطورة عند التخلف' : 'Severity if missed'}
            value={
              <Badge tone={severityTone(obligation.severityIfMissed)}>
                {tlabel(SEVERITY_LABELS, obligation.severityIfMissed, isAr)}
              </Badge>
            } />
          <Field label={isAr ? 'الحالة' : 'Lifecycle stage'}
            value={
              <Badge tone={lifecycleTone(obligation.lifecycleStage)}>
                {tlabel(LIFECYCLE_LABELS, obligation.lifecycleStage, isAr)}
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
          <div style={{ marginTop: 'var(--sp-6)', padding: 'var(--sp-6)',
            background: 'var(--surface-2)', borderRadius: 6, fontSize: 13, lineHeight: 1.6 }}>
            {isAr ? obligation.descriptionAr : obligation.descriptionEn}
          </div>
        )}

        <div className="cluster" style={{ gap: 8, marginTop: 'var(--sp-6)' }}>
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
                        {tlabel(ATTESTATION_STATUS_LABELS, a.attestationStatus, isAr)}
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
                          {tlabel(REVIEWER_DECISION_LABELS, a.reviewerDecision, isAr)}
                        </Badge>
                      ) : '—'}
                      {a.reviewerCommentEn && (
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                          {isAr ? (a.reviewerCommentAr ?? a.reviewerCommentEn) : a.reviewerCommentEn}
                        </div>
                      )}
                    </td>
                    <td>
                      <div className="cluster" style={{ gap: 6 }}>
                        <Button size="sm" variant="ghost" Icon={Paperclip}
                          onClick={() => setDocsTarget({ entityType: 'ObligationAttestation', entityId: a.id, label: isAr ? `أدلة الإقرار · ${a.periodLabel ?? a.id}` : `Attestation evidence · ${a.periodLabel ?? a.id}` })}
                          title={isAr ? 'الأدلة والمرفقات' : 'Evidence & attachments'} />
                        {(a.attestationStatus === 'ATTESTED' || a.attestationStatus === 'ATTESTED_WITH_EXCEPTIONS') && (
                          <>
                            <Button size="sm" Icon={CheckCircle2} onClick={() => onReview(a, 'ACCEPTED')}>
                              {isAr ? 'قبول' : 'Accept'}
                            </Button>
                            <Button size="sm" variant="danger" Icon={XCircle} onClick={() => onReview(a, 'REJECTED')}>
                              {isAr ? 'رفض' : 'Reject'}
                            </Button>
                            <Button size="sm" variant="secondary" Icon={RotateCcw} onClick={() => onReview(a, 'REQUEST_CHANGES')}>
                              {isAr ? 'تعديل' : 'Changes'}
                            </Button>
                          </>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>

      <FormModal
        open={editOpen}
        title={isAr ? `تعديل الالتزام · ${obligation.obligationCode}` : `Edit obligation · ${obligation.obligationCode}`}
        fields={editFields}
        initial={editInitial}
        size="lg"
        submitLabel={isAr ? 'حفظ التغييرات' : 'Save changes'}
        onClose={() => setEditOpen(false)}
        onSubmit={submitEdit}
      />

      {docsTarget && (
        <Modal open size="lg" title={docsTarget.label} onClose={() => setDocsTarget(null)}>
          <AttachmentsField entityType={docsTarget.entityType} entityId={docsTarget.entityId} label={docsTarget.label} />
        </Modal>
      )}
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
