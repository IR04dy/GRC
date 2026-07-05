// CY-I — Cyber policy lifecycle panel. Self-contained (SOC-B PlaybookRunsPanel
// pattern) so it drops into the CyberPage as one tab without threading state
// through the page. Wired to /api/v1/cyber/policies, which delegates to the
// shared policy engine: create → upload file → DRAFT→REVIEW→APPROVED→PUBLISHED,
// versioning, and review-date. File upload reuses the generic AttachmentsField.
import { useCallback, useEffect, useState } from 'react';
import { FileText, Plus, GitBranch, Send, CheckCircle2, UploadCloud, RotateCcw, Archive, Clock } from 'lucide-react';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { RowActions } from '../../components/ui/RowActions';
import { Modal } from '../../components/ui/Modal';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { cyberApi, ApiError, type CyberPolicyDto, type CyberPolicyRequest } from '../../lib/api';

type Tone = 'neutral' | 'warning' | 'info' | 'success' | 'danger';
const STATUS_TONE: Record<string, Tone> = {
  DRAFT: 'neutral', REVIEW: 'warning', APPROVED: 'info',
  PUBLISHED: 'success', EXPIRED: 'danger', ARCHIVED: 'neutral',
};

interface Transition { to: string; en: string; ar: string; tone?: 'success' | 'accent'; }
const NEXT: Record<string, Transition[]> = {
  DRAFT:     [{ to: 'REVIEW', en: 'Submit for review', ar: 'إرسال للمراجعة' }],
  REVIEW:    [{ to: 'APPROVED', en: 'Approve', ar: 'اعتماد', tone: 'success' }, { to: 'DRAFT', en: 'Return to draft', ar: 'إرجاع للمسودة' }],
  APPROVED:  [{ to: 'PUBLISHED', en: 'Publish', ar: 'نشر', tone: 'success' }, { to: 'REVIEW', en: 'Back to review', ar: 'إرجاع للمراجعة' }],
  PUBLISHED: [{ to: 'EXPIRED', en: 'Mark expired', ar: 'وضع كمنتهٍ' }, { to: 'ARCHIVED', en: 'Archive', ar: 'أرشفة' }],
  EXPIRED:   [{ to: 'ARCHIVED', en: 'Archive', ar: 'أرشفة' }],
};
const CATEGORIES = ['CYBER', 'INFORMATION_SECURITY', 'INCIDENT_RESPONSE'];
const CADENCES = ['ANNUAL', 'BIENNIAL', 'QUARTERLY', 'MONTHLY'];

const EMPTY: CyberPolicyRequest = {
  policyCode: '', titleEn: '', titleAr: '', category: 'CYBER',
  ownerName: '', ownerEmail: '', effectiveDate: '', nextReviewDate: '', reviewCadence: 'ANNUAL', summaryEn: '',
};

export function CyberPoliciesPanel() {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [rows, setRows] = useState<CyberPolicyDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [createOpen, setCreateOpen] = useState(false);
  const [detail, setDetail] = useState<CyberPolicyDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<CyberPolicyDto | null>(null);
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    setLoading(true);
    cyberApi.policies(false).then(setRows).catch(() => setRows([])).finally(() => setLoading(false));
  }, []);
  useEffect(() => { load(); }, [load]);

  async function transition(p: CyberPolicyDto, to: string) {
    try {
      const updated = await cyberApi.transitionPolicy(p.id, to);
      toast.success(ar ? `تم الانتقال إلى ${to}` : `Moved to ${to}`);
      setDetail(d => (d && d.id === p.id ? updated : d));
      load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الانتقال' : 'Transition failed'));
    }
  }

  async function newVersion(p: CyberPolicyDto) {
    const parts = p.version.split('.');
    const guess = `${(parseInt(parts[0] || '1', 10) + 1)}.0`;
    try {
      await cyberApi.newPolicyVersion(p.policyCode, guess);
      toast.success(ar ? `أُنشئت نسخة ${guess}` : `Version ${guess} drafted`);
      setDetail(null); load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر إنشاء نسخة' : 'New version failed'));
    }
  }

  async function del(p: CyberPolicyDto) {
    setBusy(true);
    try {
      await cyberApi.deletePolicy(p.id);
      toast.success(ar ? 'تم الحذف' : 'Deleted'); setConfirmDel(null); load();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الحذف' : 'Delete failed'));
    } finally { setBusy(false); }
  }

  const cols: Column<CyberPolicyDto>[] = [
    { header: ar ? 'الرمز' : 'Code', cell: r => <span className="mono" style={{ fontWeight: 600 }}>{r.policyCode}</span>, mono: true },
    { header: ar ? 'الإصدار' : 'Ver', cell: r => <span className="mono">v{r.version}</span>, mono: true },
    { header: ar ? 'العنوان' : 'Title', cell: r => <span style={{ fontWeight: 600 }}>{ar && r.titleAr ? r.titleAr : r.titleEn}</span> },
    { header: ar ? 'الفئة' : 'Category', cell: r => <Badge tone="info">{r.category}</Badge> },
    { header: ar ? 'الحالة' : 'Status', cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{r.status}</Badge> },
    { header: ar ? 'المالك' : 'Owner', cell: r => r.ownerName ?? '—' },
    { header: ar ? 'المراجعة القادمة' : 'Next review', cell: r => r.nextReviewDate ?? '—', mono: true },
    {
      header: '', align: 'end', width: '120px',
      cell: r => {
        const nexts = NEXT[r.status] ?? [];
        const primary = nexts[0];
        return (
          <RowActions
            extras={[
              ...(primary ? [{ Icon: advanceIcon(primary.to), title: ar ? primary.ar : primary.en, tone: primary.tone ?? 'primary' as const, onClick: () => void transition(r, primary.to) }] : []),
              { Icon: FileText, title: ar ? 'التفاصيل والمرفقات' : 'Details & attachments', onClick: () => setDetail(r) },
            ]}
            onDelete={r.status !== 'PUBLISHED' ? () => setConfirmDel(r) : undefined}
          />
        );
      },
    },
  ];

  const fields: FieldDef[] = [
    { key: 'policyCode', kind: 'text', label: ar ? 'رمز السياسة' : 'Policy code', required: true, span: 6, placeholder: 'CYB-POL-001' },
    { key: 'titleEn', kind: 'text', label: ar ? 'العنوان (EN)' : 'Title (EN)', required: true, span: 6 },
    { key: 'titleAr', kind: 'text', label: ar ? 'العنوان (AR)' : 'Title (AR)', span: 6 },
    { key: 'category', kind: 'select', label: ar ? 'الفئة' : 'Category', span: 6, options: CATEGORIES.map(c => ({ value: c, label: c })) },
    { key: 'reviewCadence', kind: 'select', label: ar ? 'دورية المراجعة' : 'Review cadence', span: 6, options: CADENCES.map(c => ({ value: c, label: c })) },
    { key: 'ownerName', kind: 'text', label: ar ? 'المالك' : 'Owner', span: 6 },
    { key: 'ownerEmail', kind: 'text', label: ar ? 'بريد المالك' : 'Owner email', span: 6 },
    { key: 'effectiveDate', kind: 'date', label: ar ? 'تاريخ السريان (مطلوب للنشر)' : 'Effective date (required to publish)', span: 6 },
    { key: 'nextReviewDate', kind: 'date', label: ar ? 'تاريخ المراجعة القادمة' : 'Next review date', span: 6 },
    { key: 'summaryEn', kind: 'textarea', label: ar ? 'الملخص' : 'Summary', span: 12, rows: 3 },
    { key: 'attachments', kind: 'attachments', label: ar ? 'المستند (يُرفع بعد الحفظ)' : 'Document (uploads after save)', entityType: 'PolicyDocument', span: 12 },
  ];

  async function create(values: CyberPolicyRequest) {
    const bag = { ...(values as unknown as Record<string, unknown>) };
    delete bag.attachments;            // staged files are handled by FormModal after create, not the JSON body
    try {
      const created = await cyberApi.createPolicy(bag as unknown as CyberPolicyRequest);
      toast.success(ar ? 'أُنشئت السياسة' : 'Policy created');
      setCreateOpen(false);
      load();
      return { entityId: created.id };   // lets FormModal upload the staged document
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : (ar ? 'تعذّر الإنشاء' : 'Create failed'));
      return undefined;
    }
  }

  return (
    <>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12 }}>
        <div style={{ display: 'inline-flex', alignItems: 'center', gap: 8, fontWeight: 700 }}>
          <FileText size={16} /> {ar ? 'سياسات الأمن السيبراني' : 'Cyber Policies'}
          <Badge tone="neutral">{rows.length}</Badge>
        </div>
        <Button variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>{ar ? 'سياسة جديدة' : 'New policy'}</Button>
      </div>

      <Card padded={false}>
        {loading
          ? <div style={{ padding: 20, color: 'var(--ink-3)', fontSize: 13 }}>{ar ? 'جارٍ التحميل…' : 'Loading…'}</div>
          : <DataTable columns={cols} rows={rows} empty={<EmptyState Icon={FileText} title={ar ? 'لا توجد سياسات سيبرانية بعد' : 'No cyber policies yet'} />} />}
      </Card>

      {createOpen && (
        <FormModal<CyberPolicyRequest>
          open={createOpen}
          title={ar ? 'سياسة سيبرانية جديدة' : 'New cyber policy'}
          fields={fields}
          initial={EMPTY}
          onClose={() => setCreateOpen(false)}
          onSubmit={create}
          submitLabel={ar ? 'إنشاء' : 'Create'}
        />
      )}

      {detail && (
        <Modal open title={`${detail.policyCode} · v${detail.version}`} size="md" onClose={() => setDetail(null)}>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
              <Badge tone={STATUS_TONE[detail.status] ?? 'neutral'} dot>{detail.status}</Badge>
              <Badge tone="info">{detail.category}</Badge>
              <span style={{ fontWeight: 600 }}>{ar && detail.titleAr ? detail.titleAr : detail.titleEn}</span>
            </div>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 8, fontSize: 12, color: 'var(--ink-2)' }}>
              <span><Clock size={12} /> {ar ? 'السريان' : 'Effective'}: {detail.effectiveDate ?? '—'}</span>
              <span><Clock size={12} /> {ar ? 'المراجعة القادمة' : 'Next review'}: {detail.nextReviewDate ?? '—'}</span>
              <span>{ar ? 'المالك' : 'Owner'}: {detail.ownerName ?? '—'}</span>
              <span>{ar ? 'نُشر في' : 'Published'}: {detail.publishedAt?.slice(0, 10) ?? '—'}</span>
            </div>

            {/* Lifecycle transitions */}
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
              {(NEXT[detail.status] ?? []).map(tr => (
                <Button key={tr.to} size="sm" variant={tr.tone === 'success' ? 'primary' : 'secondary'}
                  Icon={advanceIcon(tr.to)} onClick={() => void transition(detail, tr.to)}>
                  {ar ? tr.ar : tr.en}
                </Button>
              ))}
              {detail.status === 'PUBLISHED' && (
                <Button size="sm" variant="secondary" Icon={GitBranch} onClick={() => void newVersion(detail)}>
                  {ar ? 'نسخة جديدة' : 'New version'}
                </Button>
              )}
            </div>

            {/* File upload + versioned documents */}
            <AttachmentsField entityType="PolicyDocument" entityId={detail.id}
              label={ar ? 'مستند السياسة' : 'Policy document'} />
          </div>
        </Modal>
      )}

      <ConfirmDialog
        open={!!confirmDel}
        destructive
        busy={busy}
        title={ar ? 'حذف السياسة' : 'Delete policy'}
        message={confirmDel ? (ar ? `سيتم حذف «${confirmDel.policyCode}».` : `“${confirmDel.policyCode}” will be deleted.`) : ''}
        confirmLabel={ar ? 'حذف' : 'Delete'}
        onCancel={() => setConfirmDel(null)}
        onConfirm={() => { if (confirmDel) void del(confirmDel); }}
      />
    </>
  );
}

function advanceIcon(to: string) {
  switch (to) {
    case 'REVIEW': return Send;
    case 'APPROVED': return CheckCircle2;
    case 'PUBLISHED': return UploadCloud;
    case 'DRAFT': return RotateCcw;
    case 'ARCHIVED': return Archive;
    default: return Clock;
  }
}
