import { useCallback, useEffect, useMemo, useState, type CSSProperties } from 'react';
import { Sparkles, RefreshCw, Eye, CheckCircle2, XCircle, Save, AlertTriangle, Trash2, Download } from 'lucide-react';
import { Card } from '../ui/Card';
import { Badge } from '../ui/Badge';
import { Button } from '../ui/Button';
import { Modal } from '../ui/Modal';
import { InlineEmpty } from '../ui/InlineEmpty';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useRealtimeOrgEvent } from '../../lib/useRealtimeEntity';
import {
  listControlCatalog,
  listFrameworks,
  type ControlCatalogItem,
  type FrameworkOption,
  type ReviewCadence,
} from '../../lib/policy';
import {
  acceptPolicyDraft,
  deletePolicyDraft,
  draftStatusLabel,
  draftStatusTone,
  getPolicyDraft,
  getPolicyDraftGatewayStatus,
  listPolicyDrafts,
  rejectPolicyDraft,
  requestPolicyDraft,
  updatePolicyDraft,
  type GeneratedPolicyContent,
  type LlmStatusDto,
  type PolicyDraftResponse,
  type PolicyDraftStatus,
} from '../../lib/policyDrafts';
import { exportPolicyDraftPdf } from '../../lib/policyDraftPrint';

const CATEGORIES = [
  'INFORMATION_SECURITY',
  'CYBERSECURITY',
  'DATA_PRIVACY',
  'ACCESS_CONTROL',
  'BUSINESS_CONTINUITY',
  'THIRD_PARTY_RISK',
  'GOVERNANCE',
  'COMPLIANCE',
];

const TONES = ['FORMAL_REGULATORY', 'EXECUTIVE_CONCISE', 'OPERATIONAL_DETAILED'];
const CADENCES: ReviewCadence[] = ['ANNUAL', 'BIENNIAL', 'TRIENNIAL', 'AD_HOC'];

const inputStyle: CSSProperties = {
  width: '100%',
  padding: '7px 10px',
  fontSize: 12,
  border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)',
  background: 'var(--surface-1)',
  color: 'var(--ink-1)',
};

const labelStyle: CSSProperties = {
  display: 'block',
  fontSize: 11,
  fontWeight: 600,
  color: 'var(--ink-2)',
  marginBottom: 4,
};

export function PolicyAiDraftTab({ onAccepted }: { onAccepted?: () => void }) {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [gateway, setGateway] = useState<LlmStatusDto | null>(null);
  const [drafts, setDrafts] = useState<PolicyDraftResponse[]>([]);
  const [statusFilter, setStatusFilter] = useState<PolicyDraftStatus | ''>('');
  const [loading, setLoading] = useState(true);

  const [frameworks, setFrameworks] = useState<FrameworkOption[]>([]);
  const [controls, setControls] = useState<ControlCatalogItem[]>([]);

  // Request modal state
  const [showRequest, setShowRequest] = useState(false);
  const [submittingReq, setSubmittingReq] = useState(false);
  const [policyType, setPolicyType] = useState('');
  const [category, setCategory] = useState('INFORMATION_SECURITY');
  const [tone, setTone] = useState('FORMAL_REGULATORY');
  const [selectedFwIds, setSelectedFwIds] = useState<number[]>([]);
  const [selectedCtlIds, setSelectedCtlIds] = useState<number[]>([]);
  const [scope, setScope] = useState('');
  const [instructions, setInstructions] = useState('');

  // Review / edit modal state
  const [activeDraft, setActiveDraft] = useState<PolicyDraftResponse | null>(null);
  const [savingEdit, setSavingEdit] = useState(false);
  const [editTitleEn, setEditTitleEn] = useState('');
  const [editTitleAr, setEditTitleAr] = useState('');
  const [editSummaryEn, setEditSummaryEn] = useState('');
  const [editSummaryAr, setEditSummaryAr] = useState('');
  const [editMdEn, setEditMdEn] = useState('');
  const [editMdAr, setEditMdAr] = useState('');
  const [editControlIds, setEditControlIds] = useState<number[]>([]);
  const [editCadence, setEditCadence] = useState<ReviewCadence>('ANNUAL');
  const [editNote, setEditNote] = useState('');

  // Accept / Reject sub-dialogs
  const [showAccept, setShowAccept] = useState(false);
  const [acceptCode, setAcceptCode] = useState('');
  const [acceptVersion, setAcceptVersion] = useState('1.0');
  const [acceptOwnerName, setAcceptOwnerName] = useState('');
  const [acceptOwnerEmail, setAcceptOwnerEmail] = useState('');
  const [acceptSubmitting, setAcceptSubmitting] = useState(false);

  const [showReject, setShowReject] = useState(false);
  const [rejectNote, setRejectNote] = useState('');
  const [rejectSubmitting, setRejectSubmitting] = useState(false);

  const [draftToDelete, setDraftToDelete] = useState<PolicyDraftResponse | null>(null);
  const [deleting, setDeleting] = useState(false);

  const reload = useCallback(async (silent = false) => {
    if (!silent) setLoading(true);
    try {
      const [gw, list] = await Promise.all([
        getPolicyDraftGatewayStatus(),
        listPolicyDrafts(statusFilter || undefined),
      ]);
      setGateway(gw);
      setDrafts(list);
    } catch (e) {
      if (!silent) {
        toast.danger(e instanceof ApiError ? e.message : 'Failed to load AI policy drafts');
      }
    } finally {
      if (!silent) setLoading(false);
    }
  }, [statusFilter, toast]);

  useEffect(() => { void reload(); }, [reload]);

  useEffect(() => {
    listFrameworks().then(setFrameworks).catch(() => setFrameworks([]));
    listControlCatalog().then(setControls).catch(() => setControls([]));
  }, []);

  // Realtime STOMP updates when background worker completes
  useRealtimeOrgEvent('policy.ai_draft', (env) => {
    const data = env?.data;
    if (data?.status === 'GENERATED') {
      toast.success(isAr
        ? `اكتمل توليد مسودة السياسة (${data.externalId})`
        : `AI policy draft ready for review (${data.externalId})`);
    } else if (data?.status === 'FAILED') {
      toast.danger(isAr
        ? `فشل توليد مسودة السياسة (${data.externalId})`
        : `AI policy draft failed (${data.externalId})`);
    }
    void reload(true);
  });

  // Fallback poll while any draft is queued/generating
  const hasInFlight = useMemo(
    () => drafts.some(d => d.status === 'REQUESTED' || d.status === 'GENERATING'),
    [drafts],
  );
  useEffect(() => {
    if (!hasInFlight) return;
    const timer = window.setInterval(() => { void reload(true); }, 3000);
    return () => window.clearInterval(timer);
  }, [hasInFlight, reload]);

  function hydrateEditor(d: PolicyDraftResponse) {
    const content: GeneratedPolicyContent | undefined = d.edited ?? d.generated;
    setEditTitleEn(content?.titleEn ?? '');
    setEditTitleAr(content?.titleAr ?? '');
    setEditSummaryEn(content?.summaryEn ?? '');
    setEditSummaryAr(content?.summaryAr ?? '');
    setEditMdEn(content?.contentMdEn ?? '');
    setEditMdAr(content?.contentMdAr ?? '');
    setEditControlIds(content?.citedControlIds ?? []);
    setEditCadence(content?.suggestedReviewCadence ?? 'ANNUAL');
    setEditNote(d.reviewerNote ?? '');
  }

  async function openReview(id: number) {
    try {
      const fresh = await getPolicyDraft(id);
      setActiveDraft(fresh);
      hydrateEditor(fresh);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load draft details');
    }
  }

  async function onSubmitRequest() {
    if (!policyType.trim()) {
      toast.danger(isAr ? 'يرجى إدخال نوع أو موضوع السياسة' : 'Policy type / topic is required');
      return;
    }
    setSubmittingReq(true);
    try {
      const created = await requestPolicyDraft({
        policyType: policyType.trim(),
        category,
        tone,
        frameworkIds: selectedFwIds,
        controlIds: selectedCtlIds,
        applicabilityScope: scope.trim() || undefined,
        customInstructions: instructions.trim() || undefined,
      });
      toast.success(isAr
        ? `تم إرسال طلب الصياغة الذكية (${created.externalId}) — سنُشعرك فور الجاهزية`
        : `Draft queued (${created.externalId}) — you will be notified when ready`);
      setShowRequest(false);
      setPolicyType('');
      setInstructions('');
      await reload(true);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to request AI draft');
    } finally {
      setSubmittingReq(false);
    }
  }

  async function onSaveEdits() {
    if (!activeDraft) return;
    setSavingEdit(true);
    try {
      const updated = await updatePolicyDraft(activeDraft.id, {
        titleEn: editTitleEn,
        titleAr: editTitleAr,
        summaryEn: editSummaryEn,
        summaryAr: editSummaryAr,
        contentMdEn: editMdEn,
        contentMdAr: editMdAr,
        citedControlIds: editControlIds,
        suggestedReviewCadence: editCadence,
        assumptions: (activeDraft.edited ?? activeDraft.generated)?.assumptions ?? [],
        reviewerNote: editNote || undefined,
      });
      setActiveDraft(updated);
      hydrateEditor(updated);
      toast.success(isAr ? 'تم حفظ التعديلات على المسودة' : 'Draft edits saved');
      await reload(true);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to save draft edits');
    } finally {
      setSavingEdit(false);
    }
  }

  async function onConfirmAccept() {
    if (!activeDraft) return;
    if (!acceptCode.trim()) {
      toast.danger(isAr ? 'رمز السياسة مطلوب' : 'Policy code is required');
      return;
    }
    setAcceptSubmitting(true);
    try {
      if (activeDraft.status === 'GENERATED') {
        await updatePolicyDraft(activeDraft.id, {
          titleEn: editTitleEn,
          titleAr: editTitleAr,
          summaryEn: editSummaryEn,
          summaryAr: editSummaryAr,
          contentMdEn: editMdEn,
          contentMdAr: editMdAr,
          citedControlIds: editControlIds,
          suggestedReviewCadence: editCadence,
          assumptions: (activeDraft.edited ?? activeDraft.generated)?.assumptions ?? [],
          reviewerNote: editNote || undefined,
        });
      }
      const accepted = await acceptPolicyDraft(activeDraft.id, {
        policyCode: acceptCode.trim(),
        version: acceptVersion.trim() || '1.0',
        ownerName: acceptOwnerName.trim() || undefined,
        ownerEmail: acceptOwnerEmail.trim() || undefined,
        reviewCadence: editCadence,
        applicabilityScope: activeDraft.request?.applicabilityScope,
        reviewerNote: editNote || undefined,
      });
      toast.success(isAr
        ? `تم اعتماد المسودة وإنشاء وثيقة السياسة #${accepted.acceptedPolicyId} بحالة مسوّدة`
        : `Accepted into Policy Register (#${accepted.acceptedPolicyId}) as DRAFT (EN + AR combined)`);
      setShowAccept(false);
      setActiveDraft(null);
      await reload(true);
      onAccepted?.();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to accept draft');
    } finally {
      setAcceptSubmitting(false);
    }
  }

  async function onConfirmReject() {
    if (!activeDraft) return;
    if (!rejectNote.trim()) {
      toast.danger(isAr ? 'يرجى ذكر سبب الرفض' : 'Rejection reason is required');
      return;
    }
    setRejectSubmitting(true);
    try {
      await rejectPolicyDraft(activeDraft.id, { reviewerNote: rejectNote.trim() });
      toast.success(isAr ? 'تم رفض المسودة' : 'AI policy draft rejected');
      setShowReject(false);
      setRejectNote('');
      setActiveDraft(null);
      await reload(true);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to reject draft');
    } finally {
      setRejectSubmitting(false);
    }
  }

  async function onConfirmDelete() {
    if (!draftToDelete) return;
    setDeleting(true);
    try {
      await deletePolicyDraft(draftToDelete.id);
      toast.success(isAr ? 'تم حذف مسودة السياسة بنجاح' : 'AI policy draft deleted');
      if (activeDraft?.id === draftToDelete.id) {
        setActiveDraft(null);
      }
      setDraftToDelete(null);
      await reload(true);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to delete policy draft');
    } finally {
      setDeleting(false);
    }
  }

  function toggleId(list: number[], id: number, setter: (v: number[]) => void) {
    setter(list.includes(id) ? list.filter(x => x !== id) : [...list, id]);
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card
        title={isAr ? 'صياغة السياسات بالذكاء الاصطناعي (عربي + إنجليزي)' : 'AI Policy Drafting Studio (Bilingual EN + AR)'}
        Icon={Sparkles}
        action={
          <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
            <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void reload()}>
              {isAr ? 'تحديث' : 'Refresh'}
            </Button>
            <Button
              size="sm"
              Icon={Sparkles}
              disabled={gateway ? !gateway.enabled : false}
              onClick={() => setShowRequest(true)}
            >
              {isAr ? 'صياغة سياسة جديدة' : 'Draft policy with AI'}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, gap: 8, flexWrap: 'wrap' }}>
          <div style={{ fontSize: 12, color: 'var(--ink-2)' }}>
            {isAr
              ? 'تُولّد المسودات في الخلفية استناداً إلى ضوابط وأطر الجهة، وتُحفظ بالعربية والإنجليزية في وثيقة واحدة عند القبول كمسوّدة تخضع لدورة الاعتماد والفصل بين المهام.'
              : 'Drafts generate asynchronously from your tenant controls and frameworks. Accepting promotes a combined English + Arabic policy into DRAFT status under full C-6 SoD governance.'}
          </div>
          <select
            value={statusFilter}
            onChange={e => setStatusFilter(e.target.value as PolicyDraftStatus | '')}
            style={{ ...inputStyle, width: 180 }}
          >
            <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
            {(['REQUESTED', 'GENERATING', 'GENERATED', 'ACCEPTED', 'REJECTED', 'FAILED'] as PolicyDraftStatus[]).map(s => (
              <option key={s} value={s}>{draftStatusLabel(s, lang)}</option>
            ))}
          </select>
        </div>

        {loading ? (
          <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
        ) : drafts.length === 0 ? (
          <InlineEmpty>
            {isAr ? 'لا توجد مسودات ذكية بعد — اضغط «صياغة سياسة جديدة» للبدء.' : 'No AI policy drafts yet — click "Draft policy with AI" to generate one.'}
          </InlineEmpty>
        ) : (
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'المرجع' : 'Ref'}</th>
                <th>{isAr ? 'الموضوع / النوع' : 'Topic / Type'}</th>
                <th>{isAr ? 'الفئة' : 'Category'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'النموذج' : 'Model'}</th>
                <th>{isAr ? 'طالب الصياغة' : 'Requested by'}</th>
                <th style={{ textAlign: 'end' }}>{isAr ? 'إجراء' : 'Action'}</th>
              </tr>
            </thead>
            <tbody>
              {drafts.map(d => {
                const display = d.edited ?? d.generated;
                return (
                  <tr key={d.id}>
                    <td className="mono text-xs">{d.externalId}</td>
                    <td className="text-xs">
                      <div style={{ fontWeight: 600 }}>
                        {display ? (isAr ? display.titleAr : display.titleEn) : d.policyType}
                      </div>
                      {d.failureReason && (
                        <div style={{ color: 'var(--danger)', fontSize: 11 }}>{d.failureReason}</div>
                      )}
                    </td>
                    <td className="text-xs">{d.category}</td>
                    <td>
                      <Badge tone={draftStatusTone(d.status)} dot>
                        {draftStatusLabel(d.status, lang)}
                      </Badge>
                    </td>
                    <td className="mono text-xs">{d.provider ? `${d.provider}/${d.model ?? ''}` : '—'}</td>
                    <td className="text-xs">{d.requestedBy}</td>
                    <td style={{ textAlign: 'end' }}>
                      <div style={{ display: 'inline-flex', gap: 4, alignItems: 'center' }}>
                        {(d.generated || d.edited) && (
                          <Button
                            size="sm"
                            variant="ghost"
                            Icon={Download}
                            onClick={() => exportPolicyDraftPdf(d, 'bilingual', controls)}
                            title={isAr ? 'تحميل كملف PDF' : 'Download PDF'}
                          />
                        )}
                        <Button size="sm" variant="secondary" Icon={Eye} onClick={() => void openReview(d.id)}>
                          {isAr ? 'مراجعة' : 'Review'}
                        </Button>
                        <Button
                          size="sm"
                          variant="ghost"
                          Icon={Trash2}
                          onClick={() => setDraftToDelete(d)}
                          title={isAr ? 'حذف المسودة' : 'Delete draft'}
                          style={{ color: 'var(--danger)' }}
                        />
                      </div>
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </Card>

      {/* 1. Request AI Policy Draft Modal */}
      <Modal
        open={showRequest}
        title={isAr ? 'طلب صياغة سياسة بالذكاء الاصطناعي' : 'Generate Bilingual Policy Draft'}
        onClose={() => setShowRequest(false)}
        size="lg"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button variant="ghost" onClick={() => setShowRequest(false)}>{isAr ? 'إلغاء' : 'Cancel'}</Button>
            <Button Icon={Sparkles} disabled={submittingReq} onClick={() => void onSubmitRequest()}>
              {submittingReq ? (isAr ? 'جارٍ الإرسال…' : 'Queueing…') : (isAr ? 'بدء التوليد في الخلفية' : 'Generate asynchronously')}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
          <div style={{ gridColumn: '1 / -1' }}>
            <label style={labelStyle}>{isAr ? 'موضوع / نوع السياسة *' : 'Policy topic / type *'}</label>
            <input
              style={inputStyle}
              maxLength={60}
              value={policyType}
              onChange={e => setPolicyType(e.target.value)}
              placeholder={isAr ? 'مثال: سياسة إدارة الهوية والصلاحيات' : 'e.g. Identity & Access Management Policy'}
            />
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'الفئة *' : 'Category *'}</label>
            <select style={inputStyle} value={category} onChange={e => setCategory(e.target.value)}>
              {CATEGORIES.map(c => <option key={c} value={c}>{c}</option>)}
            </select>
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'الأسلوب' : 'Drafting tone'}</label>
            <select style={inputStyle} value={tone} onChange={e => setTone(e.target.value)}>
              {TONES.map(t => <option key={t} value={t}>{t}</option>)}
            </select>
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <label style={labelStyle}>{isAr ? 'نطاق السريان' : 'Applicability scope'}</label>
            <input
              style={inputStyle}
              value={scope}
              onChange={e => setScope(e.target.value)}
              placeholder={isAr ? 'جميع الموظفين والأنظمة السحابية والمتعاقدين' : 'All employees, contractors, cloud workloads, and third parties'}
            />
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'الأطر التنظيمية المرجعية' : 'Target frameworks'}</label>
            <div style={{ maxHeight: 130, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: 8 }}>
              {frameworks.map(fw => (
                <label key={fw.id} style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, marginBottom: 4 }}>
                  <input
                    type="checkbox"
                    checked={selectedFwIds.includes(fw.id)}
                    onChange={() => toggleId(selectedFwIds, fw.id, setSelectedFwIds)}
                  />
                  <span>{isAr && fw.nameAr ? fw.nameAr : fw.nameEn}</span>
                </label>
              ))}
            </div>
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'ضوابط الجهة المرتبطة' : 'Grounding tenant controls'}</label>
            <div style={{ maxHeight: 130, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', padding: 8 }}>
              {controls.slice(0, 60).map(ctl => (
                <label key={ctl.id} style={{ display: 'flex', gap: 6, alignItems: 'center', fontSize: 12, marginBottom: 4 }}>
                  <input
                    type="checkbox"
                    checked={selectedCtlIds.includes(ctl.id)}
                    onChange={() => toggleId(selectedCtlIds, ctl.id, setSelectedCtlIds)}
                  />
                  <span><strong className="mono">{ctl.code}</strong> — {isAr && ctl.nameAr ? ctl.nameAr : ctl.nameEn}</span>
                </label>
              ))}
            </div>
          </div>
          <div style={{ gridColumn: '1 / -1' }}>
            <label style={labelStyle}>{isAr ? 'تعليمات إضافية' : 'Custom instructions / clauses to include'}</label>
            <textarea
              rows={3}
              style={inputStyle}
              value={instructions}
              onChange={e => setInstructions(e.target.value)}
              placeholder={isAr ? 'أي متطلبات خاصة مثل المصادقة متعددة العوامل أو مراجعة الصلاحيات ربع السنوية…' : 'Specific requirements such as privileged MFA, quarterly access reviews, or SAMA/NCA clauses…'}
            />
          </div>
        </div>
      </Modal>

      {/* 2. Bilingual Review & Edit Modal */}
      <Modal
        open={activeDraft !== null && !showAccept && !showReject}
        title={activeDraft ? `${activeDraft.externalId} · ${draftStatusLabel(activeDraft.status, lang)}` : ''}
        onClose={() => setActiveDraft(null)}
        size="lg"
        footer={
          activeDraft && (
            <div style={{ display: 'flex', justifyContent: 'space-between', width: '100%', gap: 8, flexWrap: 'wrap' }}>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {(activeDraft.status === 'GENERATED' || activeDraft.status === 'FAILED') && (
                  <Button variant="danger" size="sm" Icon={XCircle} onClick={() => setShowReject(true)}>
                    {isAr ? 'رفض المسودة' : 'Reject'}
                  </Button>
                )}
                <Button
                  variant="ghost"
                  size="sm"
                  Icon={Trash2}
                  onClick={() => setDraftToDelete(activeDraft)}
                  style={{ color: 'var(--danger)' }}
                >
                  {isAr ? 'حذف' : 'Delete'}
                </Button>
              </div>
              <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                {(activeDraft.generated || activeDraft.edited) && (
                  <Button
                    variant="secondary"
                    size="sm"
                    Icon={Download}
                    onClick={() => exportPolicyDraftPdf(activeDraft, 'bilingual', controls)}
                  >
                    {isAr ? 'تحميل كملف PDF' : 'Download PDF'}
                  </Button>
                )}
                <Button variant="ghost" size="sm" onClick={() => setActiveDraft(null)}>
                  {isAr ? 'إغلاق' : 'Close'}
                </Button>
                {activeDraft.status === 'GENERATED' && (
                  <>
                    <Button variant="secondary" size="sm" Icon={Save} disabled={savingEdit} onClick={() => void onSaveEdits()}>
                      {savingEdit ? '…' : (isAr ? 'حفظ التعديلات' : 'Save edits')}
                    </Button>
                    <Button
                      size="sm"
                      Icon={CheckCircle2}
                      onClick={() => {
                        setAcceptCode(`POL-${activeDraft.category.slice(0, 4)}-${activeDraft.id}`);
                        setShowAccept(true);
                      }}
                    >
                      {isAr ? 'قبول وإدراج في سجل السياسات' : 'Accept into Policy Register'}
                    </Button>
                  </>
                )}
              </div>
            </div>
          )
        }
      >
        {activeDraft && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {activeDraft.hallucinatedCitations.length > 0 && (
              <div style={{ padding: 10, borderRadius: 'var(--r-sm)', background: 'var(--warning-50, #fffbeb)', border: '1px solid var(--warning)', fontSize: 12, display: 'flex', gap: 8, alignItems: 'center' }}>
                <AlertTriangle size={16} />
                <span>
                  {isAr
                    ? `تم استبعاد مراجع ضوابط غير موجودة في سجل الجهة تلقائياً: ${activeDraft.hallucinatedCitations.join(', ')}`
                    : `Stripped unverified control IDs cited by the model: ${activeDraft.hallucinatedCitations.join(', ')}`}
                </span>
              </div>
            )}

            {activeDraft.failureReason && (
              <div style={{ padding: 10, borderRadius: 'var(--r-sm)', border: '1px solid var(--danger)', color: 'var(--danger)', fontSize: 12 }}>
                {activeDraft.failureReason}
              </div>
            )}

            {(activeDraft.generated || activeDraft.edited) ? (
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                <div>
                  <label style={labelStyle}>Title (English) *</label>
                  <input
                    style={inputStyle}
                    maxLength={255}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editTitleEn}
                    onChange={e => setEditTitleEn(e.target.value)}
                  />
                </div>
                <div dir="rtl">
                  <label style={labelStyle}>العنوان (بالعربية) *</label>
                  <input
                    style={inputStyle}
                    maxLength={255}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editTitleAr}
                    onChange={e => setEditTitleAr(e.target.value)}
                  />
                </div>
                <div>
                  <label style={labelStyle}>Executive Summary (EN)</label>
                  <textarea
                    rows={2}
                    style={inputStyle}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editSummaryEn}
                    onChange={e => setEditSummaryEn(e.target.value)}
                  />
                </div>
                <div dir="rtl">
                  <label style={labelStyle}>الملخص التنفيذي (AR)</label>
                  <textarea
                    rows={2}
                    style={inputStyle}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editSummaryAr}
                    onChange={e => setEditSummaryAr(e.target.value)}
                  />
                </div>
                <div>
                  <label style={labelStyle}>Policy Body Markdown (EN) *</label>
                  <textarea
                    rows={10}
                    style={{ ...inputStyle, fontFamily: 'var(--font-mono, monospace)' }}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editMdEn}
                    onChange={e => setEditMdEn(e.target.value)}
                  />
                </div>
                <div dir="rtl">
                  <label style={labelStyle}>نص السياسة بصيغة ماركداون (AR) *</label>
                  <textarea
                    rows={10}
                    style={{ ...inputStyle, fontFamily: 'var(--font-mono, monospace)' }}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editMdAr}
                    onChange={e => setEditMdAr(e.target.value)}
                  />
                </div>
                <div>
                  <label style={labelStyle}>{isAr ? 'دورية المراجعة المقترحة' : 'Suggested review cadence'}</label>
                  <select
                    style={inputStyle}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editCadence}
                    onChange={e => setEditCadence(e.target.value as ReviewCadence)}
                  >
                    {CADENCES.map(c => <option key={c} value={c}>{c}</option>)}
                  </select>
                </div>
                <div>
                  <label style={labelStyle}>{isAr ? 'ملاحظة المراجع' : 'Reviewer note'}</label>
                  <input
                    style={inputStyle}
                    maxLength={1000}
                    disabled={activeDraft.status !== 'GENERATED'}
                    value={editNote}
                    onChange={e => setEditNote(e.target.value)}
                  />
                </div>

                <div style={{ gridColumn: '1 / -1' }}>
                  <label style={labelStyle}>
                    {isAr ? 'الضوابط المستشهد بها في المسودة (Citations)' : 'Cited tenant controls (Citations)'}
                  </label>
                  <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', minHeight: 32, alignItems: 'center', padding: '6px 8px', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)', background: 'var(--surface-2, rgba(0,0,0,0.02))' }}>
                    {editControlIds.length === 0 ? (
                      <span style={{ fontSize: 12, color: 'var(--ink-3, #888)' }}>
                        {isAr ? 'لم يتم الاستشهاد بضوابط محددة' : 'No specific controls cited'}
                      </span>
                    ) : (
                      editControlIds.map(cid => {
                        const ctl = controls.find(c => c.id === cid);
                        return (
                          <span
                            key={cid}
                            className="badge badge-primary"
                            style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 11 }}
                            title={ctl ? (isAr && ctl.nameAr ? ctl.nameAr : ctl.nameEn) : undefined}
                          >
                            <span className="mono font-semibold">{ctl ? ctl.code : `#${cid}`}</span>
                            {ctl && <span>· {ctl.nameEn}</span>}
                            {activeDraft.status === 'GENERATED' && (
                              <button
                                type="button"
                                onClick={() => setEditControlIds(prev => prev.filter(x => x !== cid))}
                                style={{ background: 'none', border: 'none', color: 'inherit', cursor: 'pointer', padding: 0, marginLeft: 2, display: 'inline-flex' }}
                                title={isAr ? 'إزالة الاستشهاد' : 'Remove citation'}
                              >
                                ×
                              </button>
                            )}
                          </span>
                        );
                      })
                    )}
                  </div>
                </div>

                {((activeDraft.edited ?? activeDraft.generated)?.assumptions?.length ?? 0) > 0 && (
                  <div style={{ gridColumn: '1 / -1' }}>
                    <label style={labelStyle}>{isAr ? 'الافتراضات التنظيمية والأسس المرجعية' : 'Regulatory assumptions & baseline scope'}</label>
                    <ul style={{ margin: 0, paddingLeft: 18, fontSize: 12, color: 'var(--ink-2)' }}>
                      {(activeDraft.edited ?? activeDraft.generated)!.assumptions.map((item, idx) => (
                        <li key={idx}>{item}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </div>
            ) : (
              <InlineEmpty>
                {isAr ? 'المسودة قيد التوليد حالياً… سيتم تحديث الصفحة تلقائياً فور انتهاء النموذج.' : 'Draft is currently generating… this view updates automatically when complete.'}
              </InlineEmpty>
            )}
          </div>
        )}
      </Modal>

      {/* 3. Accept into Policy Register Modal */}
      <Modal
        open={showAccept}
        title={isAr ? 'اعتماد المسودة في سجل السياسات (DRAFT)' : 'Accept into Policy Register (as DRAFT)'}
        onClose={() => setShowAccept(false)}
        size="sm"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button variant="ghost" onClick={() => setShowAccept(false)}>{isAr ? 'رجوع' : 'Back'}</Button>
            <Button Icon={CheckCircle2} disabled={acceptSubmitting} onClick={() => void onConfirmAccept()}>
              {acceptSubmitting ? '…' : (isAr ? 'تأكيد الإنشاء' : 'Create DRAFT Policy')}
            </Button>
          </div>
        }
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
          <div>
            <label style={labelStyle}>{isAr ? 'رمز السياسة *' : 'Policy code *'}</label>
            <input style={inputStyle} maxLength={40} value={acceptCode} onChange={e => setAcceptCode(e.target.value)} />
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'الإصدار' : 'Version'}</label>
            <input style={inputStyle} maxLength={20} value={acceptVersion} onChange={e => setAcceptVersion(e.target.value)} />
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'اسم مالك السياسة' : 'Owner name'}</label>
            <input style={inputStyle} maxLength={255} value={acceptOwnerName} onChange={e => setAcceptOwnerName(e.target.value)} />
          </div>
          <div>
            <label style={labelStyle}>{isAr ? 'بريد مالك السياسة' : 'Owner email'}</label>
            <input style={inputStyle} maxLength={255} value={acceptOwnerEmail} onChange={e => setAcceptOwnerEmail(e.target.value)} />
          </div>
        </div>
      </Modal>

      {/* 4. Reject Draft Modal */}
      <Modal
        open={showReject}
        title={isAr ? 'رفض المسودة الذكية' : 'Reject AI Policy Draft'}
        onClose={() => setShowReject(false)}
        size="sm"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button variant="ghost" onClick={() => setShowReject(false)}>{isAr ? 'إلغاء' : 'Cancel'}</Button>
            <Button variant="danger" Icon={XCircle} disabled={rejectSubmitting} onClick={() => void onConfirmReject()}>
              {rejectSubmitting ? '…' : (isAr ? 'تأكيد الرفض' : 'Confirm reject')}
            </Button>
          </div>
        }
      >
        <div>
          <label style={labelStyle}>{isAr ? 'سبب الرفض *' : 'Rejection reason *'}</label>
          <textarea
            rows={3}
            style={inputStyle}
            maxLength={1000}
            value={rejectNote}
            onChange={e => setRejectNote(e.target.value)}
            placeholder={isAr ? 'اذكر سبب عدم مناسبة المسودة…' : 'State why this generated draft is rejected…'}
          />
        </div>
      </Modal>

      {/* 5. Delete Confirmation Modal */}
      <Modal
        open={draftToDelete !== null}
        title={isAr ? 'تأكيد حذف مسودة السياسة' : 'Delete AI Policy Draft'}
        onClose={() => setDraftToDelete(null)}
        size="sm"
        footer={
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button variant="ghost" onClick={() => setDraftToDelete(null)}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button
              variant="danger"
              Icon={Trash2}
              disabled={deleting}
              onClick={() => void onConfirmDelete()}
            >
              {deleting ? '…' : (isAr ? 'تأكيد الحذف' : 'Confirm Delete')}
            </Button>
          </div>
        }
      >
        <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
          {isAr
            ? `هل أنت متأكد من رغبتك في حذف مسودة السياسة (${draftToDelete?.externalId})؟ لا يمكن التراجع عن هذا الإجراء.`
            : `Are you sure you want to delete policy draft "${draftToDelete?.externalId}"? This action cannot be undone.`}
        </div>
      </Modal>
    </div>
  );
}
