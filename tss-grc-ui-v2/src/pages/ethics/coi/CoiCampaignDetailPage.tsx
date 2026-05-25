import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import {
  ArrowLeft, Eye, PlayCircle, StopCircle, ShieldCheck, UserMinus, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../../components/layout/Shell';
import { PageHeader } from '../../../components/layout/PageHeader';
import { Card } from '../../../components/ui/Card';
import { Badge } from '../../../components/ui/Badge';
import { Button } from '../../../components/ui/Button';
import { DataTable, type Column } from '../../../components/ui/DataTable';
import { EmptyState } from '../../../components/ui/EmptyState';
import { InlineEmpty } from '../../../components/ui/InlineEmpty';
import { Modal } from '../../../components/ui/Modal';
import { Textarea } from '../../../components/ui/Input';
import { Kpi } from '../../../components/ui/Kpi';
import { useI18n } from '../../../lib/i18n';
import { ApiError } from '../../../lib/api';
import {
  coiApi,
  CAMPAIGN_STATUS_LABEL, CAMPAIGN_STATUS_TONE, CAMPAIGN_SCOPE_LABEL,
  REVIEW_OUTCOME_LABEL, REVIEW_OUTCOME_TONE, ITEM_TYPE_LABEL,
  type CampaignResponse, type DeclarationResponse, type ReviewOutcome,
} from '../../../lib/coi';
import { hasAnyRole } from '../../../lib/auth';

/**
 * COI Campaign Detail — /ethics/coi/campaigns/:id.
 *
 * Header: campaign metadata + Open/Close actions.
 * Body: table of all declarations with colored review-outcome badges.
 * Click a row → modal with full declaration details + Review form
 * (visible to ETHICS_OFFICER / PLATFORM_ADMIN).
 */
export function CoiCampaignDetailPage() {
  const { lang } = useI18n();
  const navigate = useNavigate();
  const { id: idParam } = useParams<{ id: string }>();
  const id = idParam ? parseInt(idParam, 10) : NaN;

  const [campaign, setCampaign] = useState<CampaignResponse | null>(null);
  const [declarations, setDeclarations] = useState<DeclarationResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const [selected, setSelected] = useState<DeclarationResponse | null>(null);

  async function load() {
    if (!Number.isFinite(id)) return;
    setLoading(true);
    setError(null);
    try {
      const [c, ds] = await Promise.all([
        coiApi.getCampaign(id),
        coiApi.listDeclarations(id),
      ]);
      setCampaign(c);
      setDeclarations(ds);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر تحميل الدورة' : 'Failed to load campaign'));
    } finally {
      setLoading(false);
    }
  }
  useEffect(() => { load(); /* eslint-disable-next-line */ }, [id]);

  async function doOpen() {
    if (!campaign) return;
    setBusy(true);
    try {
      const updated = await coiApi.openCampaign(campaign.id);
      setCampaign(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر فتح الدورة' : 'Failed to open campaign'));
    } finally { setBusy(false); }
  }

  async function doClose() {
    if (!campaign) return;
    setBusy(true);
    try {
      const updated = await coiApi.closeCampaign(campaign.id);
      setCampaign(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إغلاق الدورة' : 'Failed to close campaign'));
    } finally { setBusy(false); }
  }

  // Refresh single declaration after review.
  async function refreshDeclaration(declId: number) {
    try {
      const updated = await coiApi.getDeclaration(declId);
      setDeclarations(prev => prev.map(d => d.id === declId ? updated : d));
      setSelected(updated);
    } catch {/* ignore */}
  }

  const kpis = useMemo(() => {
    const total = declarations.length;
    const cleared = declarations.filter(d => d.reviewOutcome === 'CLEARED').length;
    const mitigation = declarations.filter(d => d.reviewOutcome === 'MITIGATION_REQUIRED').length;
    const recusal = declarations.filter(d => d.reviewOutcome === 'RECUSAL_REQUIRED').length;
    const escalated = declarations.filter(d => d.reviewOutcome === 'ESCALATED').length;
    return { total, cleared, mitigation, recusal, escalated };
  }, [declarations]);

  const cols: Column<DeclarationResponse>[] = [
    {
      header: lang === 'ar' ? 'رقم الموظف' : 'Employee #', width: '120px',
      cell: d => <code style={{ fontSize: 12 }}>{d.employeeNo}</code>,
    },
    {
      header: lang === 'ar' ? 'الاسم' : 'Name',
      cell: d => (
        <div>
          <div style={{ fontWeight: 500, fontSize: 13 }}>{lang === 'ar' ? d.fullNameAr : d.fullNameEn}</div>
          <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {lang === 'ar' ? d.positionTitleAr : d.positionTitleEn}
            {' · '}
            {lang === 'ar' ? d.departmentAr : d.departmentEn}
          </div>
        </div>
      ),
    },
    {
      header: lang === 'ar' ? 'تضارب' : 'Conflict', width: '110px',
      cell: d => d.hasConflict
        ? <Badge tone="danger">{lang === 'ar' ? 'فعلي' : 'Actual'}</Badge>
        : d.hasPotentialConflict
          ? <Badge tone="warning">{lang === 'ar' ? 'محتمل' : 'Potential'}</Badge>
          : <Badge tone="success">{lang === 'ar' ? 'لا يوجد' : 'None'}</Badge>,
    },
    {
      header: lang === 'ar' ? 'الإفصاحات' : 'Disclosures', width: '110px',
      cell: d => <span style={{ fontSize: 12 }}>{d.items?.length ?? 0}</span>,
    },
    {
      header: lang === 'ar' ? 'الحالة' : 'Review outcome', width: '200px',
      cell: d => (
        <Badge tone={REVIEW_OUTCOME_TONE[d.reviewOutcome]}>
          {REVIEW_OUTCOME_LABEL[d.reviewOutcome][lang === 'ar' ? 'ar' : 'en']}
        </Badge>
      ),
    },
    {
      header: lang === 'ar' ? 'تاريخ التسليم' : 'Submitted', width: '130px',
      cell: d => <span style={{ fontSize: 12 }}>
        {new Date(d.submittedAt).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')}
      </span>,
    },
    {
      header: '', width: '90px',
      cell: d => (
        <Button size="sm" variant="ghost" Icon={Eye} onClick={() => setSelected(d)}>
          {lang === 'ar' ? 'عرض' : 'View'}
        </Button>
      ),
    },
  ];

  if (!Number.isFinite(id)) {
    return (
      <Shell title={lang === 'ar' ? 'الدورة غير موجودة' : 'Campaign not found'}>
        <Card padded>
          <EmptyState
            Icon={AlertTriangle}
            title={lang === 'ar' ? 'مُعرّف غير صالح' : 'Invalid identifier'}
            sub={lang === 'ar' ? 'لا يمكن قراءة مُعرّف الدورة من الرابط.' : 'Could not parse campaign id from URL.'} />
        </Card>
      </Shell>
    );
  }

  return (
    <Shell
      title={campaign ? (lang === 'ar' ? campaign.titleAr : campaign.titleEn) : '…'}
      sub={lang === 'ar'
        ? 'تفاصيل دورة الإقرار السنوي للموظفين والمسؤولين.'
        : 'Annual declaration cycle — all submitted declarations and review outcomes.'}
    >
      <PageHeader
        eyebrow={lang === 'ar' ? 'تضارب المصالح · المرحلة ١٣' : 'COI Register · Sprint 13'}
        title={campaign ? (lang === 'ar' ? campaign.titleAr : campaign.titleEn) : '…'}
        sub={campaign ? `${campaign.code} · FY ${campaign.fiscalYear}` : undefined}
        actions={(
          <div style={{ display: 'flex', gap: 8 }}>
            <Button variant="ghost" Icon={ArrowLeft} onClick={() => navigate('/ethics/coi')}>
              {lang === 'ar' ? 'رجوع' : 'Back'}
            </Button>
            {campaign?.status === 'DRAFT' && (
              <Button variant="secondary" Icon={PlayCircle} onClick={doOpen} disabled={busy}>
                {lang === 'ar' ? 'فتح الدورة' : 'Open Campaign'}
              </Button>
            )}
            {campaign?.status === 'OPEN' && (
              <Button variant="secondary" Icon={StopCircle} onClick={doClose} disabled={busy}>
                {lang === 'ar' ? 'إغلاق الدورة' : 'Close Campaign'}
              </Button>
            )}
          </div>
        )}
      />

      {error && (
        <div style={{ color: 'var(--danger)', fontSize: 13, marginBottom: 12 }}>{error}</div>
      )}

      {/* Campaign metadata */}
      {campaign && (
        <Card padded title={lang === 'ar' ? 'بيانات الدورة' : 'Campaign info'}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 14 }}>
            <Field label={lang === 'ar' ? 'الرمز' : 'Code'} value={campaign.code} />
            <Field label={lang === 'ar' ? 'السنة المالية' : 'Fiscal year'} value={String(campaign.fiscalYear)} />
            <Field label={lang === 'ar' ? 'النطاق' : 'Scope'}
              value={CAMPAIGN_SCOPE_LABEL[campaign.scope][lang === 'ar' ? 'ar' : 'en']} />
            <Field label={lang === 'ar' ? 'الحالة' : 'Status'} valueNode={
              <Badge tone={CAMPAIGN_STATUS_TONE[campaign.status]}>
                {CAMPAIGN_STATUS_LABEL[campaign.status][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            } />
            <Field label={lang === 'ar' ? 'تاريخ الفتح' : 'Opens on'}
              value={new Date(campaign.opensOn).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} />
            <Field label={lang === 'ar' ? 'تاريخ الإغلاق' : 'Closes on'}
              value={new Date(campaign.closesOn).toLocaleDateString(lang === 'ar' ? 'ar-SA' : 'en-GB')} />
          </div>
        </Card>
      )}

      {/* KPIs */}
      <div style={{
        marginTop: 16,
        display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))', gap: 12,
      }}>
        <Kpi Icon={ShieldCheck} tone="primary"
          label={lang === 'ar' ? 'إجمالي الإقرارات' : 'Total declarations'}
          value={kpis.total} />
        <Kpi Icon={ShieldCheck} tone="success"
          label={lang === 'ar' ? 'خالٍ من التضارب' : 'Cleared'}
          value={kpis.cleared} />
        <Kpi Icon={AlertTriangle} tone="warning"
          label={lang === 'ar' ? 'مطلوب تخفيف' : 'Mitigation required'}
          value={kpis.mitigation} />
        <Kpi Icon={UserMinus} tone="danger"
          label={lang === 'ar' ? 'يستوجب التنحّي' : 'Recusal required'}
          value={kpis.recusal} />
        <Kpi Icon={AlertTriangle} tone="neutral"
          label={lang === 'ar' ? 'مُحالة للإدارة العليا' : 'Escalated'}
          value={kpis.escalated} />
      </div>

      <div style={{ marginTop: 16 }}>
        <Card padded title={lang === 'ar' ? 'الإقرارات' : 'Declarations'}>
          {loading ? (
            <InlineEmpty>{lang === 'ar' ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
          ) : (
            <DataTable
              columns={cols}
              rows={declarations}
              empty={<EmptyState
                Icon={ShieldCheck}
                title={lang === 'ar' ? 'لا توجد إقرارات' : 'No declarations'}
                sub={lang === 'ar'
                  ? 'لم يُسلِّم أي موظف إقراره في هذه الدورة بعد.'
                  : 'No declarations have been submitted for this cycle yet.'} />}
            />
          )}
        </Card>
      </div>

      <DeclarationDetailModal
        open={selected !== null}
        declaration={selected}
        onClose={() => setSelected(null)}
        onReviewed={refreshDeclaration}
      />
    </Shell>
  );
}

function Field({ label, value, valueNode }: { label: string; value?: string; valueNode?: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, color: 'var(--ink-3)', textTransform: 'uppercase', letterSpacing: 0.4, marginBottom: 4 }}>
        {label}
      </div>
      <div style={{ fontSize: 13, color: 'var(--ink-1)' }}>{valueNode ?? value ?? '—'}</div>
    </div>
  );
}

// ─── Declaration detail modal + Review form ──────────────────────────

function canReview(): boolean {
  return hasAnyRole('ETHICS_OFFICER', 'PLATFORM_ADMIN', 'COMPLIANCE_OFFICER');
}

function DeclarationDetailModal({
  open, declaration, onClose, onReviewed,
}: {
  open: boolean;
  declaration: DeclarationResponse | null;
  onClose: () => void;
  onReviewed: (id: number) => void;
}) {
  const { lang } = useI18n();

  const [outcome, setOutcome] = useState<ReviewOutcome>('CLEARED');
  const [notesEn, setNotesEn] = useState('');
  const [notesAr, setNotesAr] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (declaration) {
      setOutcome(declaration.reviewOutcome === 'PENDING' ? 'CLEARED' : declaration.reviewOutcome);
      setNotesEn(declaration.reviewNotesEn ?? '');
      setNotesAr(declaration.reviewNotesAr ?? '');
      setError(null);
    }
  }, [declaration]);

  async function submitReview(e: React.FormEvent) {
    e.preventDefault();
    if (!declaration) return;
    setSubmitting(true);
    setError(null);
    try {
      await coiApi.reviewDeclaration(declaration.id, {
        reviewOutcome: outcome,
        reviewNotesEn: notesEn,
        reviewNotesAr: notesAr,
      });
      onReviewed(declaration.id);
    } catch (err) {
      setError(err instanceof ApiError ? err.message :
        (lang === 'ar' ? 'تعذّر إرسال المراجعة' : 'Failed to submit review'));
    } finally {
      setSubmitting(false);
    }
  }

  const showReviewForm = canReview();

  return (
    <Modal
      open={open}
      title={declaration
        ? (lang === 'ar' ? `إقرار: ${declaration.fullNameAr || declaration.fullNameEn}` : `Declaration: ${declaration.fullNameEn}`)
        : ''}
      onClose={onClose}
      size="lg"
    >
      {declaration && (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
          {/* Header summary */}
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(160px, 1fr))', gap: 10 }}>
            <Field label={lang === 'ar' ? 'رقم الموظف' : 'Employee #'} value={declaration.employeeNo} />
            <Field label={lang === 'ar' ? 'المسمى الوظيفي' : 'Position'}
              value={lang === 'ar' ? declaration.positionTitleAr : declaration.positionTitleEn} />
            <Field label={lang === 'ar' ? 'الإدارة' : 'Department'}
              value={lang === 'ar' ? declaration.departmentAr : declaration.departmentEn} />
            <Field label={lang === 'ar' ? 'الحالة' : 'Outcome'} valueNode={
              <Badge tone={REVIEW_OUTCOME_TONE[declaration.reviewOutcome]}>
                {REVIEW_OUTCOME_LABEL[declaration.reviewOutcome][lang === 'ar' ? 'ar' : 'en']}
              </Badge>
            } />
          </div>

          {/* Attestation */}
          <div style={{
            padding: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
            background: 'var(--surface-1)', fontSize: 13, color: 'var(--ink-2)',
          }}>
            <div style={{ fontWeight: 500, fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
              {lang === 'ar' ? 'نص الإقرار' : 'Attestation'}
            </div>
            {declaration.attestationText}
          </div>

          {/* Disclosed items */}
          <div>
            <div style={{ fontWeight: 500, marginBottom: 8 }}>
              {lang === 'ar' ? 'الإفصاحات' : 'Disclosed items'} ({declaration.items?.length ?? 0})
            </div>
            {(declaration.items ?? []).length === 0 ? (
              <InlineEmpty>{lang === 'ar' ? 'لا توجد إفصاحات.' : 'No disclosures.'}</InlineEmpty>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                {(declaration.items ?? []).map(it => (
                  <div key={it.id} style={{
                    border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                    padding: 10, background: 'var(--surface-1)',
                  }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'baseline', gap: 10 }}>
                      <div style={{ fontWeight: 500, fontSize: 13 }}>
                        {ITEM_TYPE_LABEL[it.itemType][lang === 'ar' ? 'ar' : 'en']}
                        {' · '}
                        <span style={{ color: 'var(--ink-2)' }}>
                          {lang === 'ar' ? it.counterpartyAr : it.counterpartyEn}
                        </span>
                      </div>
                      {it.financialValueSar != null && (
                        <span style={{ fontFamily: 'var(--font-mono)', fontSize: 12, color: 'var(--ink-2)' }}>
                          {it.financialValueSar.toLocaleString(lang === 'ar' ? 'ar-SA' : 'en-US')} SAR
                        </span>
                      )}
                    </div>
                    {(it.relationshipDescriptionEn || it.relationshipDescriptionAr) && (
                      <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 4 }}>
                        {lang === 'ar' ? it.relationshipDescriptionAr : it.relationshipDescriptionEn}
                      </div>
                    )}
                    {(it.mitigationActionEn || it.mitigationActionAr) && (
                      <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 4, fontStyle: 'italic' }}>
                        {lang === 'ar' ? 'إجراء التخفيف: ' : 'Mitigation: '}
                        {lang === 'ar' ? it.mitigationActionAr : it.mitigationActionEn}
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {/* Existing review notes */}
          {(declaration.reviewNotesEn || declaration.reviewNotesAr) && (
            <div style={{
              padding: 12, border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
              background: 'var(--surface-2)', fontSize: 13,
            }}>
              <div style={{ fontWeight: 500, fontSize: 12, color: 'var(--ink-3)', marginBottom: 4 }}>
                {lang === 'ar' ? 'ملاحظات المراجعة' : 'Review notes'}
              </div>
              {lang === 'ar' ? declaration.reviewNotesAr : declaration.reviewNotesEn}
            </div>
          )}

          {/* Review form — role-gated */}
          {showReviewForm && (
            <form onSubmit={submitReview} style={{
              borderTop: '1px solid var(--border)', paddingTop: 14, display: 'flex', flexDirection: 'column', gap: 12,
            }}>
              <div style={{ fontWeight: 500 }}>
                {lang === 'ar' ? 'مراجعة الإقرار' : 'Review Declaration'}
              </div>

              <div style={{ display: 'flex', flexDirection: 'column', gap: 6 }}>
                {(['CLEARED', 'MITIGATION_REQUIRED', 'RECUSAL_REQUIRED', 'ESCALATED'] as ReviewOutcome[]).map(o => (
                  <label key={o} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 13 }}>
                    <input type="radio" name="outcome" value={o}
                      checked={outcome === o}
                      onChange={() => setOutcome(o)} />
                    <Badge tone={REVIEW_OUTCOME_TONE[o]}>
                      {REVIEW_OUTCOME_LABEL[o][lang === 'ar' ? 'ar' : 'en']}
                    </Badge>
                  </label>
                ))}
              </div>

              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 10 }}>
                <Textarea label={lang === 'ar' ? 'الملاحظات (EN)' : 'Notes (EN)'}
                  value={notesEn} onChange={e => setNotesEn(e.target.value)} rows={3} />
                <Textarea label={lang === 'ar' ? 'الملاحظات (AR)' : 'Notes (AR)'}
                  value={notesAr} onChange={e => setNotesAr(e.target.value)} rows={3} dir="rtl" />
              </div>

              {error && <div style={{ color: 'var(--danger)', fontSize: 13 }}>{error}</div>}

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                <Button type="button" variant="ghost" onClick={onClose}>
                  {lang === 'ar' ? 'إلغاء' : 'Cancel'}
                </Button>
                <Button type="submit" variant="primary" disabled={submitting}>
                  {lang === 'ar' ? 'إرسال المراجعة' : 'Submit Review'}
                </Button>
              </div>
            </form>
          )}
        </div>
      )}
    </Modal>
  );
}
