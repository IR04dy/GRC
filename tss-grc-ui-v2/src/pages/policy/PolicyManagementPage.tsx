import { useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, FileText, AlertCircle, CheckCircle2, ArrowRight, Plus, Trash2, Wrench, Paperclip,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Modal } from '../../components/ui/Modal';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { ApiError } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listPolicies, createPolicy, transitionPolicy, deletePolicy,
  newPolicyVersion, updatePolicy, runPolicyExpirySweep, policyAggregate,
  listExceptions, createException, transitionException, runExceptionExpirySweep,
  listAttestations, attestPolicy, declineAttestation, createAttestation,
  policyStatusTone, policyStatusLabel, nextPolicyStatuses,
  exceptionStatusTone, exceptionStatusLabel, nextExceptionStatuses,
  attestationStatusTone, attestationStatusLabel,
  type PolicyDocument, type PolicyException, type PolicyAttestation,
  type PolicyAggregate, type PolicyStatus, type ExceptionStatus,
} from '../../lib/policy';

type PolicyTab = 'overview' | 'documents' | 'exceptions' | 'attestations';

/**
 * Policy Management — bound to V79 backend (policy.documents,
 * policy.exceptions, policy.attestations). Single-page module with four
 * tabs covering the full policy lifecycle.
 */
export function PolicyManagementPage() {
  const { t: _t, lang } = useI18n();
  const isAr = lang === 'ar';
  void _t;

  const [tab, setTab] = useState<PolicyTab>('overview');

  const tabs: { id: PolicyTab; labelEn: string; labelAr: string }[] = [
    { id: 'overview',     labelEn: 'Overview',     labelAr: 'نظرة عامة' },
    { id: 'documents',    labelEn: 'Documents',    labelAr: 'المستندات' },
    { id: 'exceptions',   labelEn: 'Exceptions',   labelAr: 'الاستثناءات' },
    { id: 'attestations', labelEn: 'Attestations', labelAr: 'الإقرارات' },
  ];

  return (
    <Shell title={isAr ? 'إدارة السياسات' : 'Policy management'} sub={isAr ? 'السياسات والاستثناءات والإقرارات' : 'Policies, exceptions, attestations'}>
      <PageHeader
        eyebrow={isAr ? 'سياسات الجهة' : 'Entity policies'}
        title={isAr ? 'إدارة السياسات' : 'Policy management'} />

      <div style={{ display: 'flex', gap: 6, marginBottom: 16, flexWrap: 'wrap' }}>
        {tabs.map(tabDef => (
          <button key={tabDef.id} onClick={() => setTab(tabDef.id)}
            style={{
              padding: '6px 14px', fontSize: 12,
              border: `1px solid ${tab === tabDef.id ? 'var(--primary)' : 'var(--border)'}`,
              borderRadius: 'var(--r-sm)',
              background: tab === tabDef.id ? 'var(--primary-50)' : 'var(--surface-1)',
              color: tab === tabDef.id ? 'var(--primary)' : 'var(--ink-1)',
              fontWeight: tab === tabDef.id ? 600 : 400,
              cursor: 'pointer',
            }}>
            {isAr ? tabDef.labelAr : tabDef.labelEn}
          </button>
        ))}
      </div>

      {tab === 'overview'     && <OverviewTab onNavigate={setTab} />}
      {tab === 'documents'    && <DocumentsTab />}
      {tab === 'exceptions'   && <ExceptionsTab />}
      {tab === 'attestations' && <AttestationsTab />}
    </Shell>
  );
}

// ─── Overview tab ────────────────────────────────────────────────────

function OverviewTab({ onNavigate }: { onNavigate: (t: PolicyTab) => void }) {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';

  const [agg, setAgg] = useState<PolicyAggregate | null>(null);
  const [loading, setLoading] = useState(true);

  async function reload() {
    setLoading(true);
    try { setAgg(await policyAggregate()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load aggregate'); setAgg(null); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, []);

  async function onSweep() {
    try {
      const r = await runPolicyExpirySweep();
      toast.success(isAr ? `تم — ${r.flippedCount} سياسات انتهت` : `Done — ${r.flippedCount} policies flipped to EXPIRED`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Sweep failed');
    }
  }

  if (loading) return <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>;
  if (!agg) return <InlineEmpty>{isAr ? 'لا توجد بيانات' : 'No data'}</InlineEmpty>;

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card title={isAr ? 'مكتبة السياسات' : 'Policy library'} Icon={ShieldCheck}
        action={<Button size="sm" variant="ghost" Icon={Wrench} onClick={onSweep}>
          {isAr ? 'تحقق من الانتهاء' : 'Run expiry sweep'}
        </Button>}>
        <div className="kpi-grid">
          <Kpi Icon={FileText}     label={isAr ? 'إجمالي السياسات' : 'Total policies'} value={agg.total} tone="primary" onClick={() => onNavigate('documents')} />
          <Kpi Icon={CheckCircle2} label={isAr ? 'منشورة' : 'Published'}                value={agg.published} tone="success" onClick={() => onNavigate('documents')} />
          <Kpi Icon={FileText}     label={isAr ? 'قيد المراجعة' : 'In review'}          value={agg.inReview} tone="info" onClick={() => onNavigate('documents')} />
          <Kpi Icon={FileText}     label={isAr ? 'مسوّدة' : 'Draft'}                    value={agg.draft} tone="primary" onClick={() => onNavigate('documents')} />
          <Kpi Icon={AlertCircle}  label={isAr ? 'منتهية' : 'Expired'}                  value={agg.expired}
               tone={agg.expired > 0 ? 'warning' : 'success'} onClick={() => onNavigate('documents')} />
          <Kpi Icon={AlertCircle}  label={isAr ? 'تنتهي قريباً' : 'Expiring soon'}      value={agg.expiringSoon}
               tone={agg.expiringSoon > 0 ? 'warning' : 'success'} onClick={() => onNavigate('documents')} />
        </div>
      </Card>

      <Card title={isAr ? 'الاستثناءات والإقرارات' : 'Exceptions & attestations'} Icon={AlertCircle}>
        <div className="kpi-grid">
          <Kpi Icon={AlertCircle}  label={isAr ? 'استثناءات مفتوحة' : 'Open exceptions'}  value={agg.openExceptions}
               tone={agg.openExceptions > 0 ? 'warning' : 'success'} onClick={() => onNavigate('exceptions')} />
          <Kpi Icon={CheckCircle2} label={isAr ? 'استثناءات مُعتمَدة' : 'Approved exceptions'} value={agg.approvedExceptions} tone="info" onClick={() => onNavigate('exceptions')} />
          <Kpi Icon={AlertCircle}  label={isAr ? 'إقرارات معلقة' : 'Pending attestations'}    value={agg.pendingAttestations}
               tone={agg.pendingAttestations > 0 ? 'warning' : 'success'} onClick={() => onNavigate('attestations')} />
        </div>
      </Card>

      {Object.keys(agg.byCategory).length > 0 && (
        <Card title={isAr ? 'حسب الفئة' : 'By category'} Icon={FileText}>
          <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الفئة' : 'Category'}</th>
              <th style={{ textAlign: 'end' }}>{isAr ? 'العدد' : 'Count'}</th>
            </tr></thead>
            <tbody>
              {Object.entries(agg.byCategory).map(([cat, n]) => (
                <tr key={cat}>
                  <td className="text-xs">{cat}</td>
                  <td className="mono text-xs" style={{ textAlign: 'end' }}>{n}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
    </div>
  );
}

// ─── Documents tab ───────────────────────────────────────────────────

function DocumentsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<PolicyDocument[]>([]);
  const [statusFilter, setStatusFilter] = useState<PolicyStatus | ''>('');
  const [latestOnly, setLatestOnly] = useState(true);
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [docsFor, setDocsFor] = useState<PolicyDocument | null>(null);

  async function reload() {
    setLoading(true);
    try {
      setItems(await listPolicies({
        status: statusFilter || undefined,
        latestOnly,
      }));
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Failed to load policies');
      setItems([]);
    } finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [statusFilter, latestOnly]);

  async function onCreate(body: Record<string, unknown>) {
    try {
      await createPolicy(body as unknown as Partial<PolicyDocument>);
      toast.success(isAr ? 'تم إنشاء السياسة' : 'Policy created');
      setShowCreate(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Create failed');
    }
  }

  async function onTransition(p: PolicyDocument, to: PolicyStatus) {
    try {
      await transitionPolicy(p.id!, to);
      toast.success(isAr ? `تم → ${policyStatusLabel(to, 'ar')}` : `→ ${policyStatusLabel(to, 'en')}`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Transition failed');
    }
  }

  async function onNewVersion(p: PolicyDocument) {
    const next = await dialog.prompt({
      title: isAr ? 'إصدار جديد' : 'New version',
      label: isAr ? `النسخة الحالية: ${p.version}. أدخل رقم الإصدار الجديد:` : `Current: ${p.version}. New version number:`,
      type: 'text', required: true,
      initial: bumpVersion(p.version),
    });
    if (!next) return;
    try {
      await newPolicyVersion(p.policyCode, next);
      toast.success(isAr ? 'تم إنشاء النسخة الجديدة' : 'New version created');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Version failed');
    }
  }

  async function onSetEffective(p: PolicyDocument) {
    const ans = await dialog.prompt({
      title: isAr ? 'تاريخ السريان' : 'Effective date',
      label: isAr ? 'تاريخ ISO (YYYY-MM-DD)' : 'ISO date (YYYY-MM-DD)',
      type: 'text', required: true,
      initial: p.effectiveDate ?? new Date().toISOString().slice(0, 10),
    });
    if (!ans) return;
    try {
      await updatePolicy(p.id!, { effectiveDate: ans });
      toast.success(isAr ? 'تم التحديث' : 'Updated');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Update failed');
    }
  }

  async function onDelete(p: PolicyDocument) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف' : 'Delete policy',
      body: isAr
        ? `سيتم حذف ${p.policyCode} v${p.version}. PUBLISHED غير قابل للحذف — يجب أرشفته أولاً.`
        : `Soft-delete ${p.policyCode} v${p.version}. PUBLISHED cannot be deleted — archive first.`,
      destructive: true, submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deletePolicy(p.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const createFields: FieldDef[] = useMemo(() => [
    { kind: 'text', key: 'policyCode', required: true, span: 4, label: isAr ? 'كود السياسة *' : 'Policy code *' },
    { kind: 'text', key: 'version',    required: true, span: 4, label: isAr ? 'الإصدار *' : 'Version *' },
    { kind: 'select', key: 'category', required: true, span: 4, label: isAr ? 'الفئة *' : 'Category *',
      options: [
        { value: 'INFORMATION_SECURITY', label: 'Information Security' },
        { value: 'DATA_GOVERNANCE',      label: 'Data Governance' },
        { value: 'PRIVACY',              label: 'Privacy' },
        { value: 'GOVERNANCE',           label: 'Governance' },
        { value: 'COMPLIANCE',           label: 'Compliance' },
        { value: 'BUSINESS_CONTINUITY',  label: 'Business Continuity' },
        { value: 'GENERAL',              label: 'General' },
      ] },
    { kind: 'text',     key: 'titleEn', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *' },
    { kind: 'text',     key: 'titleAr', span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { kind: 'text',     key: 'ownerName', span: 6, label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text',     key: 'ownerEmail', span: 6, label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'select',   key: 'reviewCadence', span: 4, label: isAr ? 'دورة المراجعة' : 'Review cadence',
      options: [
        { value: 'ANNUAL', label: isAr ? 'سنوي' : 'Annual' },
        { value: 'BIENNIAL', label: isAr ? 'كل سنتين' : 'Biennial' },
        { value: 'TRIENNIAL', label: isAr ? 'كل 3 سنوات' : 'Triennial' },
        { value: 'AD_HOC', label: isAr ? 'حسب الحاجة' : 'Ad hoc' },
      ] },
    { kind: 'text',     key: 'relatedFramework', span: 4, label: isAr ? 'الإطار المرتبط' : 'Related framework' },
    { kind: 'textarea', key: 'summaryEn', span: 12, rows: 2, label: isAr ? 'الملخص (إنجليزي)' : 'Summary (EN)' },
    { kind: 'textarea', key: 'summaryAr', span: 12, rows: 2, label: isAr ? 'الملخص (عربي)' : 'Summary (AR)' },
  ], [isAr]);

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card title={isAr ? 'مستندات السياسات' : 'Policy documents'} Icon={FileText}
        action={
          <div style={{ display: 'inline-flex', gap: 6 }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={latestOnly} onChange={e => setLatestOnly(e.target.checked)} />
              {isAr ? 'أحدث نسخة فقط' : 'Latest version only'}
            </label>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as PolicyStatus | '')}
              style={{ padding: '6px 10px', fontSize: 12, minWidth: 140, border: '1px solid var(--border)',
                       borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' }}>
              <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
              <option value="DRAFT">{policyStatusLabel('DRAFT', isAr ? 'ar' : 'en')}</option>
              <option value="REVIEW">{policyStatusLabel('REVIEW', isAr ? 'ar' : 'en')}</option>
              <option value="APPROVED">{policyStatusLabel('APPROVED', isAr ? 'ar' : 'en')}</option>
              <option value="PUBLISHED">{policyStatusLabel('PUBLISHED', isAr ? 'ar' : 'en')}</option>
              <option value="EXPIRED">{policyStatusLabel('EXPIRED', isAr ? 'ar' : 'en')}</option>
              <option value="ARCHIVED">{policyStatusLabel('ARCHIVED', isAr ? 'ar' : 'en')}</option>
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={() => setShowCreate(true)}>
              {isAr ? 'سياسة جديدة' : 'New policy'}
            </Button>
          </div>
        }>
        {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
         : items.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد سياسات' : 'No policies'}</InlineEmpty>
         : (
          <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الكود' : 'Code'}</th>
              <th>{isAr ? 'الإصدار' : 'Version'}</th>
              <th>{isAr ? 'العنوان' : 'Title'}</th>
              <th>{isAr ? 'الفئة' : 'Category'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th>{isAr ? 'السريان' : 'Effective'}</th>
              <th>{isAr ? 'الانتهاء' : 'Expiry'}</th>
              <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
            </tr></thead>
            <tbody>
              {items.map(p => (
                <tr key={p.id}>
                  <td className="mono text-2xs">{p.policyCode}</td>
                  <td className="mono text-2xs">v{p.version}</td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{isAr && p.titleAr ? p.titleAr : p.titleEn}</div>
                    {p.relatedFramework && (
                      <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{p.relatedFramework}</div>
                    )}
                  </td>
                  <td className="text-xs">{p.category}</td>
                  <td><Badge tone={policyStatusTone(p.status)}>{policyStatusLabel(p.status, isAr ? 'ar' : 'en')}</Badge></td>
                  <td className="text-xs">{p.effectiveDate ?? <span className="text-muted">—</span>}</td>
                  <td className="text-xs">{p.expiryDate ?? <span className="text-muted">—</span>}</td>
                  <td style={{ textAlign: 'end' }}>
                    <div style={{ display: 'inline-flex', gap: 4, flexWrap: 'wrap', justifyContent: 'flex-end' }}>
                      {nextPolicyStatuses(p.status).map(next => (
                        <Button key={next} size="sm" variant="ghost" Icon={ArrowRight}
                          onClick={() => onTransition(p, next)}
                          title={policyStatusLabel(next, isAr ? 'ar' : 'en')}>
                          {policyStatusLabel(next, isAr ? 'ar' : 'en')}
                        </Button>
                      ))}
                      {p.status === 'APPROVED' && !p.effectiveDate && (
                        <Button size="sm" variant="ghost" Icon={Wrench} onClick={() => onSetEffective(p)}
                          title={isAr ? 'تعيين السريان' : 'Set effective date'} />
                      )}
                      <Button size="sm" variant="ghost" Icon={Plus} onClick={() => onNewVersion(p)}
                        title={isAr ? 'إصدار جديد' : 'New version'} />
                      <Button size="sm" variant="ghost" Icon={Paperclip} onClick={() => setDocsFor(p)}
                        title={isAr ? 'المستندات والمرفقات' : 'Documents & attachments'} />
                      <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDelete(p)}
                        title={isAr ? 'حذف' : 'Delete'} />
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Card>

      <FormModal
        open={showCreate}
        title={isAr ? 'سياسة جديدة' : 'New policy'}
        fields={createFields}
        initial={{ version: '1.0', category: 'GENERAL' }}
        size="lg"
        submitLabel={isAr ? 'إنشاء' : 'Create'}
        onClose={() => setShowCreate(false)}
        onSubmit={onCreate}
      />

      {docsFor && (
        <Modal
          open
          size="lg"
          title={isAr ? `مستندات السياسة ${docsFor.policyCode} v${docsFor.version}` : `Documents — ${docsFor.policyCode} v${docsFor.version}`}
          onClose={() => setDocsFor(null)}
        >
          <AttachmentsField
            entityType="PolicyDocument"
            entityId={docsFor.id!}
            label={isAr ? 'مستند السياسة والمرفقات' : 'Policy document & attachments'}
          />
        </Modal>
      )}
    </div>
  );
}

// ─── Exceptions tab ──────────────────────────────────────────────────

function ExceptionsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<PolicyException[]>([]);
  const [statusFilter, setStatusFilter] = useState<ExceptionStatus | ''>('');
  const [loading, setLoading] = useState(true);
  const [showCreate, setShowCreate] = useState(false);
  const [policies, setPolicies] = useState<PolicyDocument[]>([]);

  async function reload() {
    setLoading(true);
    try { setItems(await listExceptions({ status: statusFilter || undefined })); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load exceptions'); setItems([]); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [statusFilter]);

  // Parent-policy picker options (latest version of each policy).
  useEffect(() => {
    listPolicies({ latestOnly: true }).then(setPolicies).catch(() => setPolicies([]));
  }, []);

  const createFields: FieldDef[] = useMemo(() => [
    { kind: 'select', key: 'policyId', required: true, span: 12, label: isAr ? 'السياسة *' : 'Policy *',
      options: policies.map(p => ({ value: String(p.id), label: `${p.policyCode} v${p.version} — ${isAr && p.titleAr ? p.titleAr : p.titleEn}` })) },
    { kind: 'text', key: 'titleEn', required: true, span: 6, label: isAr ? 'العنوان (إنجليزي) *' : 'Title (EN) *' },
    { kind: 'text', key: 'titleAr', span: 6, label: isAr ? 'العنوان (عربي)' : 'Title (AR)' },
    { kind: 'select', key: 'riskLevel', span: 6, label: isAr ? 'مستوى الخطر' : 'Risk level',
      options: [
        { value: 'CRITICAL', label: isAr ? 'حرج' : 'Critical' },
        { value: 'HIGH',     label: isAr ? 'عالٍ' : 'High' },
        { value: 'MEDIUM',   label: isAr ? 'متوسط' : 'Medium' },
        { value: 'LOW',      label: isAr ? 'منخفض' : 'Low' },
      ] },
    { kind: 'text', key: 'requestorName', span: 6, label: isAr ? 'مقدّم الطلب' : 'Requestor' },
    { kind: 'date', key: 'expiryDate', span: 6, label: isAr ? 'تاريخ الانتهاء' : 'Expiry date' },
    { kind: 'textarea', key: 'justificationEn', required: true, span: 12, rows: 2, label: isAr ? 'المبرر (إنجليزي) *' : 'Justification (EN) *' },
    { kind: 'textarea', key: 'compensatingControlsEn', span: 12, rows: 2, label: isAr ? 'الضوابط التعويضية' : 'Compensating controls' },
  ], [isAr, policies]);

  async function onCreate(v: Record<string, unknown>) {
    const policyId = Number(v.policyId);
    if (!Number.isFinite(policyId) || policyId <= 0) { toast.danger(isAr ? 'السياسة مطلوبة' : 'Policy is required'); return; }
    if (!String(v.titleEn ?? '').trim())        { toast.danger(isAr ? 'العنوان (إنجليزي) مطلوب' : 'Title (EN) is required'); return; }
    if (!String(v.justificationEn ?? '').trim()) { toast.danger(isAr ? 'المبرر مطلوب' : 'Justification (EN) is required'); return; }
    const opt = (s: unknown) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    try {
      await createException({
        policyId,
        titleEn: String(v.titleEn).trim(), titleAr: opt(v.titleAr),
        justificationEn: String(v.justificationEn).trim(),
        compensatingControlsEn: opt(v.compensatingControlsEn),
        riskLevel: (opt(v.riskLevel) as PolicyException['riskLevel']) ?? 'MEDIUM',
        requestorName: opt(v.requestorName),
        expiryDate: opt(v.expiryDate),
        status: 'REQUESTED',
      });
      toast.success(isAr ? 'تم إنشاء الاستثناء' : 'Exception created');
      setShowCreate(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل إنشاء الاستثناء' : 'Create failed'));
    }
  }

  async function onSweep() {
    try {
      const r = await runExceptionExpirySweep();
      toast.success(isAr ? `تم — انتهت ${r.flippedCount} استثناءات` : `Done — ${r.flippedCount} exceptions flipped to EXPIRED`);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Sweep failed');
    }
  }

  async function onTransition(e: PolicyException, to: ExceptionStatus) {
    let reason: string | undefined;
    if (to === 'REJECTED') {
      const ans = await dialog.prompt({
        title: isAr ? 'سبب الرفض' : 'Rejection reason',
        label: isAr ? 'أدخل سبب الرفض.' : 'Reason for rejection.',
        type: 'textarea', required: true,
      });
      if (!ans) return;
      reason = ans;
    }
    try {
      await transitionException(e.id!, to, reason);
      toast.success(isAr ? `تم → ${exceptionStatusLabel(to, 'ar')}` : `→ ${exceptionStatusLabel(to, 'en')}`);
      await reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : 'Transition failed');
    }
  }

  return (
    <>
    <Card title={isAr ? 'الاستثناءات' : 'Policy exceptions'} Icon={AlertCircle}
      action={
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setShowCreate(true)}>
            {isAr ? 'استثناء جديد' : 'New exception'}
          </Button>
          <Button size="sm" variant="ghost" Icon={Wrench} onClick={onSweep}>
            {isAr ? 'تحقق من انتهاء الاستثناءات' : 'Run exception expiry sweep'}
          </Button>
          <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as ExceptionStatus | '')}
            style={{ padding: '6px 10px', fontSize: 12, minWidth: 160, border: '1px solid var(--border)',
                     borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' }}>
            <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
            <option value="REQUESTED">{exceptionStatusLabel('REQUESTED', isAr ? 'ar' : 'en')}</option>
            <option value="UNDER_REVIEW">{exceptionStatusLabel('UNDER_REVIEW', isAr ? 'ar' : 'en')}</option>
            <option value="APPROVED">{exceptionStatusLabel('APPROVED', isAr ? 'ar' : 'en')}</option>
            <option value="REJECTED">{exceptionStatusLabel('REJECTED', isAr ? 'ar' : 'en')}</option>
            <option value="EXPIRED">{exceptionStatusLabel('EXPIRED', isAr ? 'ar' : 'en')}</option>
          </select>
        </div>
      }>
      {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
       : items.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد استثناءات' : 'No exceptions'}</InlineEmpty>
       : (
        <table className="grc-table">
          <thead><tr>
            <th>{isAr ? 'الكود' : 'Code'}</th>
            <th>{isAr ? 'العنوان' : 'Title'}</th>
            <th>{isAr ? 'مستوى الخطر' : 'Risk'}</th>
            <th>{isAr ? 'الحالة' : 'Status'}</th>
            <th>{isAr ? 'الانتهاء' : 'Expiry'}</th>
            <th>{isAr ? 'الطالب' : 'Requestor'}</th>
            <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
          </tr></thead>
          <tbody>
            {items.map(e => (
              <tr key={e.id}>
                <td className="mono text-2xs">{e.exceptionCode ?? `#${e.id}`}</td>
                <td>
                  <div style={{ fontWeight: 600 }}>{isAr && e.titleAr ? e.titleAr : e.titleEn}</div>
                  <div className="text-2xs text-muted" style={{ marginTop: 2, maxWidth: 360 }}>
                    {(isAr && e.justificationAr ? e.justificationAr : e.justificationEn).substring(0, 100)}…
                  </div>
                </td>
                <td><Badge tone={e.riskLevel === 'CRITICAL' || e.riskLevel === 'HIGH' ? 'danger'
                                : e.riskLevel === 'MEDIUM' ? 'warning' : 'info'}>{e.riskLevel}</Badge></td>
                <td><Badge tone={exceptionStatusTone(e.status)}>{exceptionStatusLabel(e.status, isAr ? 'ar' : 'en')}</Badge></td>
                <td className="text-xs">{e.expiryDate ?? <span className="text-muted">—</span>}</td>
                <td className="text-xs">{e.requestorName ?? <span className="text-muted">—</span>}</td>
                <td style={{ textAlign: 'end' }}>
                  <div style={{ display: 'inline-flex', gap: 4 }}>
                    {nextExceptionStatuses(e.status).map(next => (
                      <Button key={next} size="sm" variant="ghost" Icon={ArrowRight}
                        onClick={() => onTransition(e, next)}>
                        {exceptionStatusLabel(next, isAr ? 'ar' : 'en')}
                      </Button>
                    ))}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>

    <FormModal
      open={showCreate}
      title={isAr ? 'استثناء سياسة جديد' : 'New policy exception'}
      fields={createFields}
      initial={{ riskLevel: 'MEDIUM' }}
      size="lg"
      submitLabel={isAr ? 'إنشاء' : 'Create'}
      onClose={() => setShowCreate(false)}
      onSubmit={onCreate}
    />
    </>
  );
}

// ─── Attestations tab ────────────────────────────────────────────────

function AttestationsTab() {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [items, setItems] = useState<PolicyAttestation[]>([]);
  const [filter, setFilter] = useState<'PENDING' | 'ATTESTED' | 'DECLINED' | ''>('');
  const [loading, setLoading] = useState(true);
  const [policies, setPolicies] = useState<PolicyDocument[]>([]);
  const [openAssign, setOpenAssign] = useState(false);

  async function reload() {
    setLoading(true);
    try { setItems(await listAttestations({ status: filter || undefined })); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load attestations'); setItems([]); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [filter]);

  // Policy options for the assignment select (published first, then others).
  useEffect(() => {
    listPolicies({ latestOnly: true })
      .then(ps => setPolicies(ps.slice().sort((a, b) =>
        (a.status === 'PUBLISHED' ? 0 : 1) - (b.status === 'PUBLISHED' ? 0 : 1))))
      .catch(() => setPolicies([]));
  }, []);

  const assignInitial: Record<string, string> = useMemo(() => ({
    policyId: '', userUsername: '', userFullName: '', userRole: '', userDepartment: '', notes: '',
  }), []);

  const assignFields: FieldDef[] = useMemo(() => [
    { key: 'policyId', kind: 'select', required: true, span: 12,
      label: isAr ? 'السياسة' : 'Policy',
      options: policies.map(p => ({
        value: String(p.id),
        label: `${p.policyCode} v${p.version} — ${isAr ? (p.titleAr ?? p.titleEn) : p.titleEn}`,
      })) },
    { key: 'userUsername', kind: 'text', required: true, span: 6, label: isAr ? 'اسم المستخدم' : 'Username' },
    { key: 'userFullName', kind: 'text', span: 6, label: isAr ? 'الاسم الكامل' : 'Full name' },
    { key: 'userRole', kind: 'text', span: 6, label: isAr ? 'الدور' : 'Role' },
    { key: 'userDepartment', kind: 'text', span: 6, label: isAr ? 'الإدارة' : 'Department' },
    { key: 'notes', kind: 'textarea', span: 12, rows: 2, label: isAr ? 'ملاحظات' : 'Notes' },
  ], [isAr, policies]);

  async function submitAssign(v: Record<string, string>) {
    const policyId = Number(v.policyId);
    const userUsername = String(v.userUsername ?? '').trim();
    if (!Number.isFinite(policyId) || policyId <= 0) {
      toast.danger(isAr ? 'السياسة مطلوبة' : 'Policy is required'); return;
    }
    if (!userUsername) { toast.danger(isAr ? 'اسم المستخدم مطلوب' : 'Username is required'); return; }
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    const pol = policies.find(p => p.id === policyId);
    try {
      await createAttestation({
        policyId,
        policyVersion: pol?.version,
        userUsername,
        userFullName: opt(v.userFullName),
        userRole: opt(v.userRole),
        userDepartment: opt(v.userDepartment),
        notes: opt(v.notes),
        status: 'PENDING',
      });
      toast.success(isAr ? 'تم إسناد الإقرار' : 'Attestation assigned');
      setOpenAssign(false);
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل إسناد الإقرار' : 'Failed to assign attestation'));
    }
  }

  async function onAttest(a: PolicyAttestation) {
    const ans = await dialog.prompt({
      title: isAr ? 'إقرار' : 'Attest',
      label: isAr ? 'مرجع الدليل (اختياري — رقم تذكرة LMS، إلخ)' : 'Evidence reference (optional — LMS completion id, etc.)',
      type: 'text',
    });
    try {
      await attestPolicy(a.id!, ans ?? undefined);
      toast.success(isAr ? 'تم الإقرار' : 'Attested');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Attest failed');
    }
  }

  async function onDecline(a: PolicyAttestation) {
    const reason = await dialog.prompt({
      title: isAr ? 'رفض' : 'Decline',
      label: isAr ? 'سبب الرفض.' : 'Reason for declining.',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    try {
      await declineAttestation(a.id!, reason);
      toast.success(isAr ? 'تم الرفض' : 'Declined');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Decline failed');
    }
  }

  return (
    <>
    <Card title={isAr ? 'الإقرارات' : 'Policy attestations'} Icon={CheckCircle2}
      action={
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <select value={filter} onChange={e => setFilter(e.target.value as 'PENDING' | 'ATTESTED' | 'DECLINED' | '')}
            style={{ padding: '6px 10px', fontSize: 12, minWidth: 160, border: '1px solid var(--border)',
                     borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' }}>
            <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
            <option value="PENDING">{attestationStatusLabel('PENDING', isAr ? 'ar' : 'en')}</option>
            <option value="ATTESTED">{attestationStatusLabel('ATTESTED', isAr ? 'ar' : 'en')}</option>
            <option value="DECLINED">{attestationStatusLabel('DECLINED', isAr ? 'ar' : 'en')}</option>
          </select>
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setOpenAssign(true)}>
            {isAr ? 'إسناد إقرار' : 'Assign attestation'}
          </Button>
        </div>
      }>
      {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
       : items.length === 0 ? <InlineEmpty>{isAr ? 'لا توجد إقرارات' : 'No attestations'}</InlineEmpty>
       : (
        <table className="grc-table">
          <thead><tr>
            <th>{isAr ? 'المستخدم' : 'User'}</th>
            <th>{isAr ? 'الدور' : 'Role'}</th>
            <th>{isAr ? 'الإدارة' : 'Department'}</th>
            <th>{isAr ? 'السياسة' : 'Policy'}</th>
            <th>{isAr ? 'الإصدار' : 'Version'}</th>
            <th>{isAr ? 'الحالة' : 'Status'}</th>
            <th>{isAr ? 'تاريخ الإقرار' : 'Attested'}</th>
            <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
          </tr></thead>
          <tbody>
            {items.map(a => (
              <tr key={a.id}>
                <td>
                  <div style={{ fontWeight: 600 }}>{a.userFullName ?? a.userUsername}</div>
                  <div className="text-2xs text-muted">{a.userUsername}</div>
                </td>
                <td className="text-xs">{a.userRole ?? <span className="text-muted">—</span>}</td>
                <td className="text-xs">{a.userDepartment ?? <span className="text-muted">—</span>}</td>
                <td className="mono text-2xs">#{a.policyId}</td>
                <td className="mono text-2xs">v{a.policyVersion ?? '—'}</td>
                <td><Badge tone={attestationStatusTone(a.status)}>{attestationStatusLabel(a.status, isAr ? 'ar' : 'en')}</Badge></td>
                <td className="text-xs">{a.attestedAt ? a.attestedAt.substring(0, 10) : <span className="text-muted">—</span>}</td>
                <td style={{ textAlign: 'end' }}>
                  {a.status === 'PENDING' && (
                    <div style={{ display: 'inline-flex', gap: 4 }}>
                      <Button size="sm" variant="primary" Icon={CheckCircle2} onClick={() => onAttest(a)}>
                        {isAr ? 'إقرار' : 'Attest'}
                      </Button>
                      <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => onDecline(a)}>
                        {isAr ? 'رفض' : 'Decline'}
                      </Button>
                    </div>
                  )}
                  {a.evidenceRef && (
                    <div className="text-2xs text-muted mono">{a.evidenceRef}</div>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </Card>

    <FormModal
      open={openAssign}
      title={isAr ? 'إسناد إقرار سياسة' : 'Assign policy attestation'}
      fields={assignFields}
      initial={assignInitial}
      onClose={() => setOpenAssign(false)}
      onSubmit={submitAssign}
      submitLabel={isAr ? 'إسناد' : 'Assign'}
    />
    </>
  );
}

// ─── Helpers ─────────────────────────────────────────────────────────

/** "1.0" → "1.1" or "v3.2" → "v3.3". */
function bumpVersion(v: string): string {
  const m = v.match(/^(v?)(\d+)\.(\d+)$/);
  if (!m) return v + '.1';
  return `${m[1]}${m[2]}.${parseInt(m[3], 10) + 1}`;
}
