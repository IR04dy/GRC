import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from 'react';
import { BookOpen, Landmark, AlertTriangle, Plus, Pencil } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Modal } from '../../components/ui/Modal';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { PillTabs } from '../../components/ui/PillTabs';
import { ApiError } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import {
  listAuthorityDocuments, listAuthorityDocumentCitations,
  rollupAuthorityDocumentsByFamily, createAuthorityDocument, updateAuthorityDocument, createCitation,
  citationsToTree,
  authorityStatusTone, translationTone, translationLabel,
  type AuthorityDocument, type AuthorityDocumentCitation,
  type AuthorityFamily, type AuthorityStatus, type TranslationStatus,
  type CitationNode,
} from '../../lib/regulatoryAuthority';

const FAMILIES: AuthorityFamily[] = [
  'NCA Cyber', 'SAMA', 'Privacy', 'BCM', 'Risk',
  'Third-Party', 'Digital-Gov', 'Resilience', 'International', 'Cyber',
];
const JURISDICTIONS = ['KSA', 'GCC', 'International'];
const STATUSES: AuthorityStatus[] = ['DRAFT', 'ACTIVE', 'DEPRECATED', 'SUPERSEDED'];
const TRANSLATIONS: TranslationStatus[] = ['EN_ONLY', 'AR_AUTO', 'AR_HUMAN_REVIEWED', 'AR_OFFICIAL'];

interface CreateForm {
  code: string; titleEn: string; titleAr: string;
  family: AuthorityFamily; jurisdiction: string; issuer: string; version: string;
  status: AuthorityStatus; translationStatus: TranslationStatus;
  summaryEn: string; summaryAr: string; isPlaceholder: boolean;
}
const EMPTY_CREATE: CreateForm = {
  code: '', titleEn: '', titleAr: '',
  family: 'NCA Cyber', jurisdiction: 'KSA', issuer: '', version: '1.0',
  status: 'ACTIVE', translationStatus: 'EN_ONLY',
  summaryEn: '', summaryAr: '', isPlaceholder: true,
};

interface CitForm {
  citationCode: string; parentCitationCode: string;
  titleEn: string; titleAr: string;
  requirementTextEn: string; requirementTextAr: string;
  mandatory: boolean; weight: string;
}
const EMPTY_CIT: CitForm = {
  citationCode: '', parentCitationCode: '',
  titleEn: '', titleAr: '',
  requirementTextEn: '', requirementTextAr: '',
  mandatory: true, weight: '1',
};

const ctrl: CSSProperties = {
  width: '100%', padding: '8px 10px', borderRadius: 'var(--r-sm)',
  border: '1px solid var(--border)', background: 'var(--surface)',
  color: 'var(--ink-1)', fontSize: 13,
};

function FormField({ label, required, children }:
  { label: string; required?: boolean; children: ReactNode }) {
  return (
    <label style={{ display: 'flex', flexDirection: 'column', gap: 4 }}>
      <span style={{ fontSize: 12, fontWeight: 600, color: 'var(--ink-2)' }}>
        {label}{required && <span style={{ color: 'var(--danger)' }}> *</span>}
      </span>
      {children}
    </label>
  );
}

/**
 * Regulatory Library — Framework Intersection plan slice F1.
 *
 * Read-only view of platform.authority_documents +
 * authority_document_citations. The plan is explicit: regulators are
 * data, not modules. This page is the catalog browser; downstream
 * domain pages (Risk, Cyber, BCM, …) will overlay control coverage
 * against these citations in slice F5.
 *
 * Every seeded document is is_placeholder=1 until the official text
 * is loaded — the banner is intentional, mirroring the NDI Rule-0
 * guardrail so the platform never overclaims regulatory completeness.
 */
export function RegulatoryLibraryPage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const demoMode = !isAuthenticated();

  const [documents, setDocuments] = useState<AuthorityDocument[]>([]);
  const [rollup, setRollup] = useState<Record<string, number>>({});
  const [familyFilter, setFamilyFilter] = useState<string>('');
  const [loading, setLoading] = useState(true);

  const [selected, setSelected] = useState<AuthorityDocument | null>(null);
  const [citations, setCitations] = useState<AuthorityDocumentCitation[]>([]);
  const [citationsLoading, setCitationsLoading] = useState(false);

  const [showCreate, setShowCreate] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [form, setForm] = useState<CreateForm>(EMPTY_CREATE);
  const set = <K extends keyof CreateForm>(k: K, v: CreateForm[K]) =>
    setForm(f => ({ ...f, [k]: v }));
  const isEditing = editingId !== null;

  function openCreate() { setEditingId(null); setForm(EMPTY_CREATE); setShowCreate(true); }

  function openEdit(d: AuthorityDocument) {
    setEditingId(d.id);
    setForm({
      code: d.code, titleEn: d.titleEn, titleAr: d.titleAr ?? '',
      family: (d.family as AuthorityFamily), jurisdiction: d.jurisdiction,
      issuer: d.issuer, version: d.version,
      status: d.status, translationStatus: d.translationStatus,
      summaryEn: d.summaryEn ?? '', summaryAr: d.summaryAr ?? '',
      isPlaceholder: d.isPlaceholder,
    });
    setShowCreate(true);
  }

  const [showCit, setShowCit] = useState(false);
  const [savingCit, setSavingCit] = useState(false);
  const [citForm, setCitForm] = useState<CitForm>(EMPTY_CIT);
  const setCit = <K extends keyof CitForm>(k: K, v: CitForm[K]) =>
    setCitForm(f => ({ ...f, [k]: v }));

  function openAddCit() { setCitForm(EMPTY_CIT); setShowCit(true); }

  async function submitAddCit() {
    if (!selected) return;
    const citationCode = citForm.citationCode.trim();
    const titleEn = citForm.titleEn.trim();
    if (!citationCode || !titleEn) {
      toast.danger(isAr
        ? 'كود البند والعنوان (إنجليزي) حقول مطلوبة'
        : 'Citation code and English title are required');
      return;
    }
    // Derive depth from the chosen parent; top-level → 0.
    const parent = citForm.parentCitationCode
      ? citations.find(c => c.citationCode === citForm.parentCitationCode)
      : undefined;
    const depth = parent ? parent.depth + 1 : 0;
    const weight = Number(citForm.weight);
    setSavingCit(true);
    try {
      await createCitation(selected.id, {
        citationCode,
        parentCitationCode: citForm.parentCitationCode || undefined,
        depth,
        sequenceOrder: citations.length,
        titleEn,
        titleAr: citForm.titleAr.trim() || undefined,
        requirementTextEn: citForm.requirementTextEn.trim() || undefined,
        requirementTextAr: citForm.requirementTextAr.trim() || undefined,
        mandatory: citForm.mandatory,
        weight: Number.isFinite(weight) && weight > 0 ? weight : 1,
      });
      toast.success(isAr
        ? `تمت إضافة البند ${citationCode}`
        : `Citation ${citationCode} added`);
      setShowCit(false);
      await selectDoc(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'تعذّرت إضافة البند' : 'Failed to add citation'));
    } finally {
      setSavingCit(false);
    }
  }

  async function submitCreate() {
    const code = form.code.trim();
    const titleEn = form.titleEn.trim();
    const issuer = form.issuer.trim();
    if (!code || !titleEn || !form.family || !form.jurisdiction || !issuer) {
      toast.danger(isAr
        ? 'الكود والعنوان (إنجليزي) والعائلة والاختصاص والجهة المُصدرة حقول مطلوبة'
        : 'Code, English title, family, jurisdiction and issuer are required');
      return;
    }
    setSaving(true);
    try {
      if (isEditing) {
        // code/version/status are immutable here (status is lifecycle-managed
        // via the transition endpoint) — the service ignores them on update.
        const updated = await updateAuthorityDocument(editingId!, {
          titleEn,
          titleAr: form.titleAr.trim() || undefined,
          family: form.family,
          jurisdiction: form.jurisdiction,
          issuer,
          translationStatus: form.translationStatus,
          summaryEn: form.summaryEn.trim() || undefined,
          summaryAr: form.summaryAr.trim() || undefined,
          isPlaceholder: form.isPlaceholder,
        });
        toast.success(isAr
          ? `تم تحديث الوثيقة ${updated.code}`
          : `Document ${updated.code} updated`);
        setShowCreate(false);
        setSelected(prev => (prev && prev.id === updated.id ? updated : prev));
        await reload();
      } else {
        const created = await createAuthorityDocument({
          code,
          titleEn,
          titleAr: form.titleAr.trim() || undefined,
          family: form.family,
          jurisdiction: form.jurisdiction,
          issuer,
          version: form.version.trim() || '1.0',
          status: form.status,
          translationStatus: form.translationStatus,
          summaryEn: form.summaryEn.trim() || undefined,
          summaryAr: form.summaryAr.trim() || undefined,
          isPlaceholder: form.isPlaceholder,
        });
        toast.success(isAr
          ? `تم تسجيل الوثيقة ${created.code} في المكتبة`
          : `Document ${created.code} registered in the library`);
        setShowCreate(false);
        await reload();
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isEditing
            ? (isAr ? 'تعذّر تحديث الوثيقة' : 'Failed to update document')
            : (isAr ? 'تعذّر تسجيل الوثيقة' : 'Failed to register document')));
    } finally {
      setSaving(false);
    }
  }

  async function reload() {
    setLoading(true);
    // Per-call .catch() — show the document list even if the rollup endpoint
    // fails (e.g. missing aggregation view in dev DB), and vice versa.
    const [docs, agg] = await Promise.all([
      listAuthorityDocuments(familyFilter || undefined).catch(() => [] as AuthorityDocument[]),
      rollupAuthorityDocumentsByFamily().catch(() => ({} as Record<string, number>)),
    ]);
    setDocuments(docs);
    setRollup(agg);
    setLoading(false);
  }
  useEffect(() => { void reload(); }, [familyFilter]); // eslint-disable-line react-hooks/exhaustive-deps

  async function selectDoc(d: AuthorityDocument) {
    setSelected(d);
    setCitationsLoading(true);
    try { setCitations(await listAuthorityDocumentCitations(d.id)); }
    catch (e) {
      toast.danger(e instanceof ApiError ? e.message
        : (isAr ? 'فشل تحميل العناصر' : 'Failed to load citations'));
      setCitations([]);
    }
    finally { setCitationsLoading(false); }
  }

  const families = useMemo(() => Object.keys(rollup).sort(), [rollup]);

  const placeholderCount = useMemo(() =>
    documents.filter(d => d.isPlaceholder).length, [documents]);
  const arOfficialCount = useMemo(() =>
    documents.filter(d => d.translationStatus === 'AR_OFFICIAL').length, [documents]);

  const citationTree = useMemo(() => citationsToTree(citations), [citations]);

  return (
    <Shell
      title={isAr ? 'مكتبة المرجعيات التنظيمية' : 'Regulatory library'}
      sub={isAr ? 'الجهات والأنظمة والمعايير التي تتبعها المنصة' : 'Authority documents and citations the platform tracks'}>
      <PageHeader
        eyebrow={isAr ? 'الإطار التنظيمي' : 'Regulatory framework'}
        title={isAr ? 'مكتبة المرجعيات' : 'Authority documents catalog'}
        actions={demoMode
          ? <Badge tone="neutral">{isAr ? 'وضع العرض' : 'Demo mode'}</Badge>
          : <Button variant="primary" Icon={Plus} onClick={openCreate}>
              {isAr ? 'تسجيل وثيقة' : 'Register document'}
            </Button>} />

      {/* Placeholder guardrail banner (NDI Rule-0 pattern) */}
      {placeholderCount > 0 && (
        <div style={{
          marginBottom: 14, padding: '10px 14px', borderRadius: 'var(--r-md)',
          background: 'var(--warning-soft, #fff8e1)', border: '1px solid var(--warning, #f6c149)',
          display: 'flex', gap: 10, alignItems: 'flex-start',
        }}>
          <AlertTriangle size={18} style={{ flexShrink: 0, color: 'var(--warning, #c47e00)' }} />
          <div style={{ fontSize: 13 }}>
            <div style={{ fontWeight: 600, marginBottom: 2 }}>
              {isAr ? 'تنبيه: عناصر مؤقتة' : 'Placeholder data in use'}
            </div>
            <div style={{ color: 'var(--ink-2)' }}>
              {isAr
                ? `${placeholderCount} وثيقة مرجعية بحاجة لاستيراد النص الرسمي من الجهة المُصدرة قبل اعتمادها للامتثال.`
                : `${placeholderCount} authority document(s) require the official text to be imported before they can be used for compliance attestation.`}
            </div>
          </div>
        </div>
      )}

      {/* KPI strip */}
      <div className="kpi-strip-4">
        <Kpi Icon={BookOpen} label={isAr ? 'عدد الوثائق' : 'Documents'} value={documents.length} />
        <Kpi Icon={Landmark} label={isAr ? 'العائلات' : 'Families'} value={families.length} />
        <Kpi Icon={AlertTriangle} label={isAr ? 'عناصر مؤقتة' : 'Placeholders'} value={placeholderCount}
             tone={placeholderCount > 0 ? 'warning' : 'success'} />
        <Kpi Icon={BookOpen} label={isAr ? 'نص عربي رسمي' : 'AR official'} value={arOfficialCount} />
      </div>

      {/* Family filter — one shared pill control */}
      <div style={{ marginBottom: 14 }}>
        <PillTabs
          aria-label={isAr ? 'تصفية بالعائلة' : 'Filter by family'}
          active={familyFilter}
          onChange={setFamilyFilter}
          options={[
            { id: '', label: isAr ? 'الكل' : 'All', count: Object.values(rollup).reduce((a, b) => a + b, 0) },
            ...families.map(f => ({ id: f, label: f, count: rollup[f] })),
          ]}
        />
      </div>

      <div className="dash-grid" style={{ gridTemplateColumns: 'minmax(420px, 1fr) minmax(0, 1.4fr)' }}>
        {/* Document list */}
        <Card title={isAr ? 'الوثائق المرجعية' : 'Authority documents'}
          action={<Badge tone="primary">{documents.length}</Badge>}>
          {loading
            ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                {isAr ? 'جاري التحميل…' : 'Loading…'}
              </div>
            : documents.length === 0
              ? <InlineEmpty>{isAr ? 'لا توجد مرجعيات' : 'No authority documents'}</InlineEmpty>
              : <table className="grc-table">
                  <thead><tr>
                    <th>{isAr ? 'الكود' : 'Code'}</th>
                    <th>{isAr ? 'العنوان' : 'Title'}</th>
                    <th>{isAr ? 'العائلة' : 'Family'}</th>
                    <th>{isAr ? 'الإصدار' : 'Version'}</th>
                    <th>{isAr ? 'الحالة' : 'Status'}</th>
                    <th>{isAr ? 'اللغة' : 'Translation'}</th>
                  </tr></thead>
                  <tbody>
                    {documents.map(d => (
                      <tr key={d.id} style={{
                        cursor: 'pointer',
                        background: selected?.id === d.id ? 'var(--surface-2)' : undefined,
                      }} onClick={() => selectDoc(d)}>
                        <td className="mono text-2xs">{d.code}</td>
                        <td>
                          <div style={{ fontWeight: 600 }}>
                            {isAr && d.titleAr ? d.titleAr : d.titleEn}
                          </div>
                          <div className="text-2xs text-muted">{d.issuer} · {d.jurisdiction}</div>
                          {d.isPlaceholder && (
                            <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>
                          )}
                        </td>
                        <td className="text-xs">{d.family}</td>
                        <td className="mono text-2xs">v{d.version}</td>
                        <td><Badge tone={authorityStatusTone(d.status)}>{statusLabel(d.status, lang)}</Badge></td>
                        <td>
                          <Badge tone={translationTone(d.translationStatus)}>
                            {translationLabel(d.translationStatus, isAr ? 'ar' : 'en')}
                          </Badge>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>}
        </Card>

        {/* Citations panel */}
        {selected ? (
          <Card title={`${selected.code} v${selected.version} — ${isAr ? 'العناصر' : 'Citations'}`}
            action={
              <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                <Badge tone="primary">{citations.length}</Badge>
                {selected.isPlaceholder && <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>}
                {!demoMode && (
                  <Button variant="ghost" size="sm" Icon={Pencil} onClick={() => openEdit(selected)}>
                    {isAr ? 'تعديل' : 'Edit'}
                  </Button>
                )}
                {!demoMode && (selected.status === 'DRAFT' || selected.status === 'ACTIVE') && (
                  <Button variant="secondary" size="sm" Icon={Plus} onClick={openAddCit}>
                    {isAr ? 'إضافة بند' : 'Add citation'}
                  </Button>
                )}
              </div>
            }>
            {selected.summaryEn && (
              <div className="text-xs text-muted" style={{ padding: '0 4px 12px' }}>
                {isAr && selected.summaryAr ? selected.summaryAr : selected.summaryEn}
              </div>
            )}
            {citationsLoading
              ? <div style={{ padding: 24, textAlign: 'center', color: 'var(--ink-3)' }}>
                  {isAr ? 'جاري التحميل…' : 'Loading…'}
                </div>
              : citations.length === 0
                ? <InlineEmpty>
                    <div style={{ fontWeight: 600, marginBottom: 4 }}>
                      {isAr ? 'لا توجد عناصر بعد' : 'No citations yet'}
                    </div>
                    <div className="text-2xs text-muted">
                      {isAr ? 'يلزم استيراد البنود من النص الرسمي' : 'Import citations from the official document'}
                    </div>
                  </InlineEmpty>
                : <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                    {citationTree.map(n => <CitationRow key={n.citation.id} node={n} isAr={isAr} depth={0} />)}
                  </div>}
          </Card>
        ) : (
          <Card title={isAr ? 'العناصر' : 'Citations'}>
            <InlineEmpty>
              <div style={{ fontWeight: 600, marginBottom: 4 }}>
                {isAr ? 'اختر وثيقة لعرض عناصرها' : 'Pick a document to view its citations'}
              </div>
              <div className="text-2xs text-muted">
                {isAr ? 'يمكنك تصفية حسب العائلة من أعلى' : 'Filter by family using the chips above'}
              </div>
            </InlineEmpty>
          </Card>
        )}
      </div>

      <Modal
        open={showCreate}
        onClose={() => setShowCreate(false)}
        size="lg"
        title={isEditing
          ? (isAr ? `تعديل الوثيقة ${form.code}` : `Edit document ${form.code}`)
          : (isAr ? 'تسجيل وثيقة مرجعية' : 'Register authority document')}
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowCreate(false)} disabled={saving}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button variant="primary" Icon={isEditing ? Pencil : Plus} onClick={submitCreate} disabled={saving}>
              {saving
                ? (isAr ? 'جارٍ الحفظ…' : 'Saving…')
                : isEditing ? (isAr ? 'حفظ التغييرات' : 'Save changes') : (isAr ? 'تسجيل' : 'Register')}
            </Button>
          </>
        }>
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ fontSize: 12, color: 'var(--ink-3)' }}>
            {isEditing
              ? (isAr
                  ? 'الكود والإصدار والحالة تُدار من خلال دورة حياة الوثيقة ولا يمكن تعديلها هنا. عند استيراد النص الرسمي، حدِّث حالة الترجمة وألغِ علامة «مؤقت».'
                  : 'Code, version and status are managed through the document lifecycle and cannot be changed here. Once the official text is imported, update the translation status and clear the placeholder flag.')
              : (isAr
                  ? 'الوثائق الجديدة تُسجَّل كعنصر مؤقت حتى استيراد النص الرسمي من الجهة المُصدرة.'
                  : 'New documents register as placeholders until the official text is imported from the issuer.')}
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1.4fr 0.6fr', gap: 12 }}>
            <FormField label={isAr ? 'الكود' : 'Code'} required>
              <input style={isEditing ? { ...ctrl, opacity: 0.6 } : ctrl} value={form.code}
                placeholder="e.g. SAMA-CRMG-V1" disabled={isEditing}
                onChange={e => set('code', e.target.value)} />
            </FormField>
            <FormField label={isAr ? 'الإصدار' : 'Version'}>
              <input style={isEditing ? { ...ctrl, opacity: 0.6 } : ctrl} value={form.version}
                placeholder="1.0" disabled={isEditing}
                onChange={e => set('version', e.target.value)} />
            </FormField>
          </div>

          <FormField label={isAr ? 'العنوان (إنجليزي)' : 'Title (English)'} required>
            <input style={ctrl} value={form.titleEn}
              onChange={e => set('titleEn', e.target.value)} />
          </FormField>
          <FormField label={isAr ? 'العنوان (عربي)' : 'Title (Arabic)'}>
            <input style={ctrl} value={form.titleAr} dir="rtl"
              onChange={e => set('titleAr', e.target.value)} />
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'العائلة' : 'Family'} required>
              <select style={ctrl} value={form.family}
                onChange={e => set('family', e.target.value as AuthorityFamily)}>
                {FAMILIES.map(f => <option key={f} value={f}>{f}</option>)}
              </select>
            </FormField>
            <FormField label={isAr ? 'الاختصاص' : 'Jurisdiction'} required>
              <select style={ctrl} value={form.jurisdiction}
                onChange={e => set('jurisdiction', e.target.value)}>
                {JURISDICTIONS.map(j => <option key={j} value={j}>{j}</option>)}
              </select>
            </FormField>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
            <FormField label={isAr ? 'الجهة المُصدرة' : 'Issuer'} required>
              <input style={ctrl} value={form.issuer} placeholder="e.g. SAMA, NCA, SDAIA, ISO"
                onChange={e => set('issuer', e.target.value)} />
            </FormField>
            <FormField label={isAr ? 'الحالة' : 'Status'}>
              <select style={isEditing ? { ...ctrl, opacity: 0.6 } : ctrl} value={form.status}
                disabled={isEditing}
                onChange={e => set('status', e.target.value as AuthorityStatus)}>
                {STATUSES.map(s => <option key={s} value={s}>{statusLabel(s, lang)}</option>)}
              </select>
            </FormField>
          </div>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, alignItems: 'end' }}>
            <FormField label={isAr ? 'حالة الترجمة' : 'Translation status'}>
              <select style={ctrl} value={form.translationStatus}
                onChange={e => set('translationStatus', e.target.value as TranslationStatus)}>
                {TRANSLATIONS.map(t => (
                  <option key={t} value={t}>{translationLabel(t, isAr ? 'ar' : 'en')}</option>
                ))}
              </select>
            </FormField>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, padding: '8px 0' }}>
              <input type="checkbox" checked={form.isPlaceholder}
                onChange={e => set('isPlaceholder', e.target.checked)} />
              {isAr ? 'عنصر مؤقت (النص الرسمي غير مستورد)' : 'Placeholder (official text not yet imported)'}
            </label>
          </div>

          <FormField label={isAr ? 'ملخص (إنجليزي)' : 'Summary (English)'}>
            <textarea style={{ ...ctrl, minHeight: 64, resize: 'vertical' }} value={form.summaryEn}
              onChange={e => set('summaryEn', e.target.value)} />
          </FormField>
          <FormField label={isAr ? 'ملخص (عربي)' : 'Summary (Arabic)'}>
            <textarea style={{ ...ctrl, minHeight: 64, resize: 'vertical' }} value={form.summaryAr} dir="rtl"
              onChange={e => set('summaryAr', e.target.value)} />
          </FormField>
        </div>
      </Modal>

      <Modal
        open={showCit}
        onClose={() => setShowCit(false)}
        size="md"
        title={selected
          ? `${isAr ? 'إضافة بند إلى' : 'Add citation to'} ${selected.code}`
          : (isAr ? 'إضافة بند' : 'Add citation')}
        footer={
          <>
            <Button variant="ghost" onClick={() => setShowCit(false)} disabled={savingCit}>
              {isAr ? 'إلغاء' : 'Cancel'}
            </Button>
            <Button variant="primary" Icon={Plus} onClick={submitAddCit} disabled={savingCit}>
              {savingCit ? (isAr ? 'جارٍ الحفظ…' : 'Saving…') : (isAr ? 'إضافة' : 'Add')}
            </Button>
          </>
        }>
        <div style={{ display: 'grid', gap: 12 }}>
          <div style={{ display: 'grid', gridTemplateColumns: '0.8fr 1.2fr', gap: 12 }}>
            <FormField label={isAr ? 'كود البند' : 'Citation code'} required>
              <input style={ctrl} value={citForm.citationCode} placeholder="e.g. 2-1-1"
                onChange={e => setCit('citationCode', e.target.value)} />
            </FormField>
            <FormField label={isAr ? 'البند الأب (اختياري)' : 'Parent citation (optional)'}>
              <select style={ctrl} value={citForm.parentCitationCode}
                onChange={e => setCit('parentCitationCode', e.target.value)}>
                <option value="">{isAr ? '— مستوى أعلى —' : '— Top level —'}</option>
                {citations.map(c => (
                  <option key={c.id} value={c.citationCode}>
                    {c.citationCode} — {(isAr && c.titleAr ? c.titleAr : c.titleEn).slice(0, 40)}
                  </option>
                ))}
              </select>
            </FormField>
          </div>

          <FormField label={isAr ? 'العنوان (إنجليزي)' : 'Title (English)'} required>
            <input style={ctrl} value={citForm.titleEn}
              onChange={e => setCit('titleEn', e.target.value)} />
          </FormField>
          <FormField label={isAr ? 'العنوان (عربي)' : 'Title (Arabic)'}>
            <input style={ctrl} value={citForm.titleAr} dir="rtl"
              onChange={e => setCit('titleAr', e.target.value)} />
          </FormField>

          <FormField label={isAr ? 'نص المتطلب (إنجليزي)' : 'Requirement text (English)'}>
            <textarea style={{ ...ctrl, minHeight: 60, resize: 'vertical' }} value={citForm.requirementTextEn}
              onChange={e => setCit('requirementTextEn', e.target.value)} />
          </FormField>
          <FormField label={isAr ? 'نص المتطلب (عربي)' : 'Requirement text (Arabic)'}>
            <textarea style={{ ...ctrl, minHeight: 60, resize: 'vertical' }} value={citForm.requirementTextAr} dir="rtl"
              onChange={e => setCit('requirementTextAr', e.target.value)} />
          </FormField>

          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12, alignItems: 'end' }}>
            <FormField label={isAr ? 'الوزن' : 'Weight'}>
              <input style={ctrl} type="number" min="0" step="0.5" value={citForm.weight}
                onChange={e => setCit('weight', e.target.value)} />
            </FormField>
            <label style={{ display: 'flex', gap: 8, alignItems: 'center', fontSize: 13, padding: '8px 0' }}>
              <input type="checkbox" checked={citForm.mandatory}
                onChange={e => setCit('mandatory', e.target.checked)} />
              {isAr ? 'إلزامي' : 'Mandatory'}
            </label>
          </div>
        </div>
      </Modal>
    </Shell>
  );
}

function CitationRow({ node, depth, isAr }: { node: CitationNode; depth: number; isAr: boolean }) {
  const c = node.citation;
  const title = isAr && c.titleAr ? c.titleAr : c.titleEn;
  const isLeaf = node.children.length === 0;
  return (
    <>
      <div style={{
        display: 'grid',
        gridTemplateColumns: 'auto 1fr auto',
        gap: 8,
        alignItems: 'center',
        padding: '6px 8px',
        marginInlineStart: depth * 18,
        borderInlineStart: depth > 0 ? '2px solid var(--border)' : undefined,
        background: depth === 0 ? 'var(--surface-2)' : undefined,
        borderRadius: 'var(--r-sm)',
      }}>
        <span className="mono text-2xs" style={{ minWidth: 56, color: 'var(--ink-3)' }}>{c.citationCode}</span>
        <span style={{ fontSize: 12, fontWeight: depth === 0 ? 600 : 400 }}>{title}</span>
        <span style={{ display: 'inline-flex', gap: 4 }}>
          {c.mandatory && <Badge tone="info">{isAr ? 'إلزامي' : 'mandatory'}</Badge>}
          {c.isPlaceholder && isLeaf && <Badge tone="warning">{isAr ? 'مؤقت' : 'placeholder'}</Badge>}
        </span>
      </div>
      {node.children.map(child =>
        <CitationRow key={child.citation.id} node={child} depth={depth + 1} isAr={isAr} />)}
    </>
  );
}

