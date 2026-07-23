import { useEffect, useMemo, useState, type CSSProperties } from 'react';
import {
  ShieldCheck, FileText, AlertCircle, CheckCircle2, ArrowRight, Plus, Trash2, Wrench, Paperclip, Download, GitCompare, FilePen, Gavel,
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
import { ApiError, reportingApi, frameworkApi, type FrameworkPolicyRowDto } from '../../lib/api';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listPolicies, createPolicy, transitionPolicy, referPolicyToCommittee, deletePolicy, diffPolicyVersions, type PolicyVersionDiff,
  newPolicyVersion, updatePolicy, runPolicyExpirySweep, policyAggregate, getPolicyEvidencePackage, getPolicyResolvedControls,
  setPolicyControlLinks, listControlCatalog, listFrameworks,
  listExceptions, createException, transitionException, runExceptionExpirySweep,
  listAttestations, attestPolicy, declineAttestation, createAttestation,
  policyStatusTone, policyStatusLabel, nextPolicyStatuses,
  exceptionStatusTone, exceptionStatusLabel, nextExceptionStatuses,
  attestationStatusTone, attestationStatusLabel,
  type PolicyDocument, type PolicyException, type PolicyAttestation,
  type PolicyAggregate, type PolicyStatus, type ExceptionStatus, type ResolvedControl, type ControlCatalogItem, type FrameworkOption,
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
  const [controlsFor, setControlsFor] = useState<PolicyDocument | null>(null);
  const [diffFor, setDiffFor] = useState<PolicyDocument | null>(null);
  const [contentFor, setContentFor] = useState<PolicyDocument | null>(null);
  const [frameworks, setFrameworks] = useState<FrameworkOption[]>([]);
  useEffect(() => { listFrameworks().then(setFrameworks).catch(() => setFrameworks([])); }, []);
  // Framework/standard filter + per-policy chips, sourced from the canonical
  // policy<->framework mapping (FrameworkAnchorService). FE-only: invert
  // policiesFor(framework) across every framework into policyId -> frameworks[].
  // A policy legitimately satisfies many frameworks, so this is a filter over a
  // many-to-many mapping, not a single-bucket category.
  const [frameworkFilter, setFrameworkFilter] = useState<number | ''>('');
  const [policyFrameworks, setPolicyFrameworks] = useState<Map<number, FrameworkOption[]>>(new Map());
  useEffect(() => {
    if (frameworks.length === 0) { setPolicyFrameworks(new Map()); return; }
    let cancelled = false;
    Promise.all(frameworks.map(f =>
      frameworkApi.policiesFor(f.id)
        .then(rows => ({ f, rows }))
        .catch(() => ({ f, rows: [] as FrameworkPolicyRowDto[] })),
    )).then(results => {
      if (cancelled) return;
      const m = new Map<number, FrameworkOption[]>();
      for (const { f, rows } of results)
        for (const r of rows) {
          const list = m.get(r.policyId) ?? [];
          if (!list.some(x => x.id === f.id)) list.push(f);
          m.set(r.policyId, list);
        }
      setPolicyFrameworks(m);
    }).catch(() => { if (!cancelled) setPolicyFrameworks(new Map()); });
    return () => { cancelled = true; };
  }, [frameworks]);

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
      // C-12 / PM-M5: the "Related framework" picker resolves to the human-readable
      // relatedFramework name only. The dead framework_id scalar was dropped (V312);
      // the canonical FK linkage lives in policy.document_framework_mappings.
      const fwId = body.frameworkPick ? Number(body.frameworkPick) : undefined;
      const fw = frameworks.find(f => f.id === fwId);
      const { frameworkPick: _omit, ...rest } = body;
      const payload = { ...rest, relatedFramework: fw?.nameEn };
      await createPolicy(payload as unknown as Partial<PolicyDocument>);
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

  // Track D: route this policy's approval through a committee (gate CAB).
  async function onReferToCommittee(p: PolicyDocument) {
    try {
      await referPolicyToCommittee(p.id!);
      toast.success(isAr ? 'تم التوجيه إلى اللجنة' : 'Referred to committee');
      await reload();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Refer failed');
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

  // C-11: export the policy's audit evidence-package manifest as a JSON artefact.
  async function onExportPack(p: PolicyDocument) {
    try {
      const pkg = await getPolicyEvidencePackage(p.id!);
      const blob = new Blob([JSON.stringify(pkg, null, 2)], { type: 'application/json' });
      const url = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = `evidence-pack-${p.policyCode}-v${p.version}.json`;
      document.body.appendChild(a); a.click(); a.remove();
      URL.revokeObjectURL(url);
      toast.success(isAr ? `تم تصدير حزمة الأدلة (${pkg.attachmentCount} مرفق)` : `Evidence pack exported (${pkg.attachmentCount} attachments)`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل التصدير' : 'Export failed'));
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
    { kind: 'text',     key: 'titleAr', required: true, span: 6, label: isAr ? 'العنوان (عربي) *' : 'Title (AR) *' },
    { kind: 'text',     key: 'ownerName', span: 6, label: isAr ? 'المالك' : 'Owner' },
    { kind: 'text',     key: 'ownerEmail', span: 6, label: isAr ? 'بريد المالك' : 'Owner email' },
    { kind: 'select',   key: 'reviewCadence', span: 4, label: isAr ? 'دورة المراجعة' : 'Review cadence',
      options: [
        { value: 'ANNUAL', label: isAr ? 'سنوي' : 'Annual' },
        { value: 'BIENNIAL', label: isAr ? 'كل سنتين' : 'Biennial' },
        { value: 'TRIENNIAL', label: isAr ? 'كل 3 سنوات' : 'Triennial' },
        { value: 'AD_HOC', label: isAr ? 'حسب الحاجة' : 'Ad hoc' },
      ] },
    { kind: 'select',   key: 'frameworkPick', span: 4, label: isAr ? 'الإطار المرتبط' : 'Related framework',
      options: frameworks.map(f => ({ value: String(f.id), label: isAr && f.nameAr ? f.nameAr : f.nameEn })) },
    { kind: 'textarea', key: 'summaryEn', span: 12, rows: 2, label: isAr ? 'الملخص (إنجليزي)' : 'Summary (EN)' },
    { kind: 'textarea', key: 'summaryAr', span: 12, rows: 2, label: isAr ? 'الملخص (عربي)' : 'Summary (AR)' },
  ], [isAr]);

  const visibleItems = useMemo(
    () => frameworkFilter === ''
      ? items
      : items.filter(p => p.id != null && (policyFrameworks.get(p.id)?.some(f => f.id === frameworkFilter) ?? false)),
    [items, frameworkFilter, policyFrameworks],
  );

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      <Card title={isAr ? 'مستندات السياسات' : 'Policy documents'} Icon={FileText}
        action={
          <div style={{ display: 'inline-flex', gap: 6 }}>
            <label style={{ display: 'inline-flex', alignItems: 'center', gap: 4, fontSize: 12, color: 'var(--ink-2)' }}>
              <input type="checkbox" checked={latestOnly} onChange={e => setLatestOnly(e.target.checked)} />
              {isAr ? 'أحدث نسخة فقط' : 'Latest version only'}
            </label>
            <select value={frameworkFilter === '' ? '' : String(frameworkFilter)}
              onChange={e => setFrameworkFilter(e.target.value ? Number(e.target.value) : '')}
              title={isAr ? 'تصفية حسب الإطار / المعيار / اللائحة' : 'Filter by framework / standard / regulation'}
              style={{ padding: '6px 10px', fontSize: 12, minWidth: 150, border: '1px solid var(--border)',
                       borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' }}>
              <option value="">{isAr ? 'كل الأطر' : 'All frameworks'}</option>
              {frameworks.map(f => (
                <option key={f.id} value={f.id}>{isAr && f.nameAr ? f.nameAr : f.nameEn}</option>
              ))}
            </select>
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
         : visibleItems.length === 0 ? <InlineEmpty>{frameworkFilter !== '' ? (isAr ? 'لا توجد سياسات مرتبطة بهذا الإطار' : 'No policies mapped to this framework') : (isAr ? 'لا توجد سياسات' : 'No policies')}</InlineEmpty>
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
              {visibleItems.map(p => (
                <tr key={p.id}>
                  <td className="mono text-2xs">{p.policyCode}</td>
                  <td className="mono text-2xs">v{p.version}</td>
                  <td>
                    <div style={{ fontWeight: 600 }}>{isAr && p.titleAr ? p.titleAr : p.titleEn}</div>
                    {p.id != null && (policyFrameworks.get(p.id)?.length ?? 0) > 0 ? (
                      <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap', marginTop: 3 }}>
                        {policyFrameworks.get(p.id)!.map(f => (
                          <span key={f.id} title={isAr ? 'إطار مرتبط' : 'Mapped framework'}
                            style={{ fontSize: 10, padding: '1px 7px', borderRadius: 999, whiteSpace: 'nowrap',
                              background: 'var(--surface-1)', color: 'var(--ink-2)', border: '1px solid var(--border)' }}>
                            {isAr && f.nameAr ? f.nameAr : f.nameEn}
                          </span>
                        ))}
                      </div>
                    ) : p.relatedFramework ? (
                      <div className="text-2xs text-muted" style={{ marginTop: 2 }}>{p.relatedFramework}</div>
                    ) : null}
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
                      <Button size="sm" variant="ghost" Icon={FilePen} onClick={() => setContentFor(p)}
                        title={isAr ? 'محرّر المحتوى' : 'Content editor'} />
                      <Button size="sm" variant="ghost" Icon={Plus} onClick={() => onNewVersion(p)}
                        title={isAr ? 'إصدار جديد' : 'New version'} />
                      <Button size="sm" variant="ghost" Icon={GitCompare} onClick={() => setDiffFor(p)}
                        title={isAr ? 'مقارنة الإصدارات' : 'Compare versions'} />
                      <Button size="sm" variant="ghost" Icon={Paperclip} onClick={() => setDocsFor(p)}
                        title={isAr ? 'المستندات والمرفقات' : 'Documents & attachments'} />
                      <Button size="sm" variant="ghost" Icon={Download} onClick={() => onExportPack(p)}
                        title={isAr ? 'تصدير حزمة الأدلة' : 'Export evidence pack'} />
                      {p.relatedControls && (
                        <Button size="sm" variant="ghost" Icon={ShieldCheck} onClick={() => setControlsFor(p)}
                          title={isAr ? 'الضوابط المرتبطة' : 'Mapped controls'} />
                      )}
                      {p.status === 'REVIEW' && (
                        <Button size="sm" variant="ghost" Icon={Gavel} onClick={() => onReferToCommittee(p)}
                          title={isAr ? 'التوجيه إلى لجنة (CAB)' : 'Refer to committee (CAB)'} />
                      )}
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

      {controlsFor && (
        <ControlsModal policy={controlsFor} onClose={() => setControlsFor(null)} />
      )}

      {diffFor && (
        <DiffModal policy={diffFor} onClose={() => setDiffFor(null)} />
      )}

      {contentFor && (
        <ContentModal policy={contentFor} onClose={() => setContentFor(null)} onSaved={reload} />
      )}
    </div>
  );
}

// C-12: read-side panel showing a policy's free-text control codes resolved
// against the real control library, each flagged verified / not-found.
// C-12: editable control picker. Pick controls from the real library (creating
// FK links via PUT /documents/{id}/controls); legacy free-text codes that aren't
// in the library are shown read-only so nothing is hidden during the transition.
function ControlsModal({ policy, onClose }: { policy: PolicyDocument; onClose: () => void }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [linked, setLinked] = useState<ResolvedControl[] | null>(null);
  const [catalog, setCatalog] = useState<ControlCatalogItem[]>([]);
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let alive = true;
    Promise.all([getPolicyResolvedControls(policy.id!), listControlCatalog()])
      .then(([res, cat]) => {
        if (!alive) return;
        setLinked(res);
        setCatalog(cat);
        setSelected(new Set(res.filter(r => r.matched && r.controlId != null).map(r => r.controlId!)));
      })
      .catch(e => { if (alive) { setLinked([]); toast.danger(e instanceof ApiError ? e.message : 'Failed to load controls'); } });
    return () => { alive = false; };
  }, [policy.id]);

  const legacyRefs = (linked ?? []).filter(r => !r.matched);

  function toggle(id: number) {
    setSelected(prev => { const n = new Set(prev); if (n.has(id)) n.delete(id); else n.add(id); return n; });
  }
  async function save() {
    setSaving(true);
    try {
      const res = await setPolicyControlLinks(policy.id!, [...selected]);
      setLinked(res);
      toast.success(isAr ? `تم حفظ ${selected.size} ضابط` : `Saved ${selected.size} control link(s)`);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed'));
    } finally { setSaving(false); }
  }

  return (
    <Modal open size="lg"
      title={isAr ? `الضوابط المرتبطة — ${policy.policyCode} v${policy.version}` : `Mapped controls — ${policy.policyCode} v${policy.version}`}
      onClose={onClose}>
      {linked === null ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty> : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
          <div className="text-xs text-muted">
            {isAr ? `اختر الضوابط من المكتبة (${selected.size} محدّد)` : `Pick controls from the library (${selected.size} selected)`}
          </div>
          <div style={{ maxHeight: 320, overflowY: 'auto', border: '1px solid var(--border)', borderRadius: 'var(--r-sm)' }}>
            <table className="grc-table">
              <thead><tr>
                <th style={{ width: 36 }} />
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'الضابط' : 'Control'}</th>
                <th>{isAr ? 'الإطار' : 'Framework'}</th>
              </tr></thead>
              <tbody>
                {catalog.length === 0 ? (
                  <tr><td colSpan={4}><InlineEmpty>{isAr ? 'لا توجد ضوابط في المكتبة' : 'No controls in the library'}</InlineEmpty></td></tr>
                ) : catalog.map(c => (
                  <tr key={c.id} style={{ cursor: 'pointer' }} onClick={() => toggle(c.id)}>
                    <td><input type="checkbox" checked={selected.has(c.id)} onChange={() => toggle(c.id)} onClick={e => e.stopPropagation()} /></td>
                    <td className="mono text-2xs">{c.code}</td>
                    <td className="text-xs">{isAr && c.nameAr ? c.nameAr : c.nameEn}</td>
                    <td className="text-2xs text-muted">{c.framework ?? '—'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {legacyRefs.length > 0 && (
            <div className="text-2xs text-muted">
              {isAr ? 'مراجع نصّية قديمة (غير موجودة في المكتبة): ' : 'Legacy free-text references (not in library): '}
              <span className="mono">{legacyRefs.map(r => r.code).join(', ')}</span>
            </div>
          )}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
            <Button variant="secondary" onClick={onClose}>{isAr ? 'إغلاق' : 'Close'}</Button>
            <Button onClick={save} disabled={saving}>
              {saving ? (isAr ? 'جارٍ الحفظ…' : 'Saving…') : (isAr ? 'حفظ الروابط' : 'Save links')}
            </Button>
          </div>
        </div>
      )}
    </Modal>
  );
}

// ─── CH-10b: version diff viewer (surfaces the CH-12 diff endpoint) ──────
/** Segment-wise numeric version compare (so v10.0 > v2.0, not lexical). */
function cmpVersion(a: string, b: string): number {
  const pa = a.split('.').map(n => parseInt(n, 10) || 0);
  const pb = b.split('.').map(n => parseInt(n, 10) || 0);
  for (let i = 0; i < Math.max(pa.length, pb.length); i++) {
    const d = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (d !== 0) return d;
  }
  return 0;
}

function DiffModal({ policy, onClose }: { policy: PolicyDocument; onClose: () => void }) {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';
  const selStyle: CSSProperties = {
    padding: '6px 10px', fontSize: 12, minWidth: 160, border: '1px solid var(--border)',
    borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
  };

  const [versions, setVersions] = useState<PolicyDocument[]>([]);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [diff, setDiff] = useState<PolicyVersionDiff | null>(null);
  const [loading, setLoading] = useState(true);
  const [comparing, setComparing] = useState(false);

  useEffect(() => {
    let alive = true;
    listPolicies({ latestOnly: false })
      .then(all => {
        if (!alive) return;
        const mine = all
          .filter(p => p.policyCode === policy.policyCode)
          .sort((a, b) => cmpVersion(b.version, a.version));   // newest first
        setVersions(mine);
        if (mine.length >= 2) { setTo(mine[0].version); setFrom(mine[1].version); }
        else if (mine.length === 1) { setTo(mine[0].version); setFrom(mine[0].version); }
      })
      .catch(() => { if (alive) setVersions([]); })
      .finally(() => { if (alive) setLoading(false); });
    return () => { alive = false; };
  }, [policy.policyCode]);

  async function compare() {
    if (!from || !to || from === to) return;
    setComparing(true);
    try { setDiff(await diffPolicyVersions(policy.policyCode, from, to)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Compare failed'); }
    finally { setComparing(false); }
  }

  return (
    <Modal open size="lg"
      title={isAr ? `مقارنة الإصدارات — ${policy.policyCode}` : `Compare versions — ${policy.policyCode}`}
      onClose={onClose}>
      {loading ? <InlineEmpty>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</InlineEmpty>
       : versions.length < 2 ? <InlineEmpty>{isAr ? 'يلزم وجود إصدارين على الأقل للمقارنة.' : 'At least two versions are needed to compare.'}</InlineEmpty>
       : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
          <div style={{ display: 'flex', gap: 12, alignItems: 'flex-end', flexWrap: 'wrap' }}>
            <label style={{ fontSize: 12 }}>
              <div style={{ color: 'var(--ink-3)', marginBottom: 4 }}>{isAr ? 'من الإصدار' : 'From version'}</div>
              <select value={from} onChange={e => { setFrom(e.target.value); setDiff(null); }} style={selStyle}>
                {versions.map(v => <option key={v.version} value={v.version}>v{v.version} · {policyStatusLabel(v.status, isAr ? 'ar' : 'en')}</option>)}
              </select>
            </label>
            <label style={{ fontSize: 12 }}>
              <div style={{ color: 'var(--ink-3)', marginBottom: 4 }}>{isAr ? 'إلى الإصدار' : 'To version'}</div>
              <select value={to} onChange={e => { setTo(e.target.value); setDiff(null); }} style={selStyle}>
                {versions.map(v => <option key={v.version} value={v.version}>v{v.version} · {policyStatusLabel(v.status, isAr ? 'ar' : 'en')}</option>)}
              </select>
            </label>
            <Button Icon={GitCompare} onClick={compare} disabled={comparing || from === to}>
              {comparing ? (isAr ? 'جارٍ المقارنة…' : 'Comparing…') : (isAr ? 'قارن' : 'Compare')}
            </Button>
          </div>
          {from === to && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{isAr ? 'اختر إصدارين مختلفين.' : 'Pick two different versions.'}</div>}
          {diff && (
            diff.changes.length === 0
              ? <InlineEmpty>{isAr ? `لا توجد فروق بين v${diff.fromVersion} و v${diff.toVersion}.` : `No differences between v${diff.fromVersion} and v${diff.toVersion}.`}</InlineEmpty>
              : (
                <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الحقل' : 'Field'}</th>
                    <th>v{diff.fromVersion}</th>
                    <th>v{diff.toVersion}</th>
                  </tr></thead>
                  <tbody>
                    {diff.changes.map(c => (
                      <tr key={c.field}>
                        <td className="mono text-2xs">{c.field}</td>
                        <td className="text-xs" style={{ color: 'var(--ink-3)', whiteSpace: 'pre-wrap' }}>{c.from ?? '—'}</td>
                        <td className="text-xs" style={{ color: 'var(--ink-1)', fontWeight: 600, whiteSpace: 'pre-wrap' }}>{c.to ?? '—'}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )
          )}
        </div>
      )}
    </Modal>
  );
}

// ─── CH-10: structured policy content editor + controlled-output generation ──
function ContentModal({ policy, onClose, onSaved }: {
  policy: PolicyDocument; onClose: () => void; onSaved: () => void | Promise<void>;
}) {
  const { lang } = useI18n();
  const toast = useToast();
  const isAr = lang === 'ar';
  // The backend freezes body/content once a policy is PUBLISHED (and beyond);
  // the editor mirrors that — read-only, but still generatable.
  const locked = policy.status === 'PUBLISHED' || policy.status === 'ARCHIVED' || policy.status === 'EXPIRED';
  const [content, setContent] = useState(policy.contentMd ?? '');
  const [saving, setSaving] = useState(false);

  const TEMPLATE = isAr
    ? '## الغرض\n\n## النطاق\n\n## بنود السياسة\n\n## الأدوار والمسؤوليات\n\n## الإنفاذ\n\n## المراجعة\n'
    : '## Purpose\n\n## Scope\n\n## Policy Statements\n\n## Roles & Responsibilities\n\n## Enforcement\n\n## Review\n';

  async function save() {
    setSaving(true);
    try {
      await updatePolicy(policy.id!, { contentMd: content });
      toast.success(isAr ? 'تم حفظ المحتوى' : 'Content saved');
      await onSaved();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
    finally { setSaving(false); }
  }
  async function generate() {
    try { await reportingApi.policyDocumentPdf(policy.id!, isAr ? 'ar' : 'en'); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Generate failed'); }
  }

  return (
    <Modal open size="lg"
      title={isAr ? `محتوى السياسة — ${policy.policyCode} v${policy.version}` : `Policy content — ${policy.policyCode} v${policy.version}`}
      onClose={onClose}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          {!locked && (
            <Button size="sm" variant="secondary" onClick={() => setContent(c => c.trim() ? c : TEMPLATE)}>
              {isAr ? 'إدراج قالب الأقسام' : 'Insert section template'}
            </Button>
          )}
          <Button size="sm" variant="ghost" Icon={Download} onClick={generate}>
            {isAr ? 'توليد مستند PDF' : 'Generate PDF'}
          </Button>
          {locked && <span style={{ fontSize: 11, color: 'var(--ink-3)' }}>
            {isAr ? 'المحتوى مجمّد بعد النشر' : 'Content is frozen once published'}</span>}
        </div>
        <textarea value={content} onChange={e => setContent(e.target.value)} readOnly={locked} rows={18}
          placeholder={isAr ? 'اكتب نص السياسة بصيغة ماركداون (## عنوان القسم، - نقطة)…' : 'Write the policy text in markdown (## Section heading, - bullet)…'}
          style={{ width: '100%', fontFamily: 'var(--font-mono)', fontSize: 12, padding: 10,
                   border: '1px solid var(--border)', borderRadius: 'var(--r-sm)',
                   background: locked ? 'var(--surface-2)' : 'var(--surface-1)', color: 'var(--ink-1)', resize: 'vertical' }} />
        <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
          <Button variant="secondary" onClick={onClose}>{isAr ? 'إغلاق' : 'Close'}</Button>
          {!locked && <Button onClick={save} disabled={saving}>{saving ? (isAr ? 'جارٍ الحفظ…' : 'Saving…') : (isAr ? 'حفظ' : 'Save')}</Button>}
        </div>
      </div>
    </Modal>
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
