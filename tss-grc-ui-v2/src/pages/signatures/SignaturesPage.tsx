import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  PenLine, Inbox, Clock, CheckCircle2, XCircle, AlertOctagon,
  ShieldCheck, FileSignature, FilePlus2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  listMyInbox, listSignatures, signTransaction, rejectTransaction, createSignature,
  sha256Hex,
  STATUS_TONE, type DigitalSignatureTransaction, type SignatureStatus,
  type DigitalSignatureCreateRequest,
} from '../../lib/digitalSignature';

/**
 * SignaturesPage — Saudi GRC prompt §28.
 *
 * Inbox + history view for digital-signature transactions. Per-tenant
 * scoped via JWT. Three tabs:
 *   - Inbox: REQUESTED rows assigned to me. Primary action = Sign / Reject.
 *   - All:   org-wide history (paginated).
 *   - Stats: counters per status.
 *
 * Sign flow: user reviews the purpose, clicks Sign — we sha256 the
 * purpose+timestamp client-side and POST it as the document hash. For
 * production this should be a real document the signer reviews, not a
 * synthetic hash; the architecture supports either.
 */
type TabId = 'inbox' | 'all' | 'stats';

export function SignaturesPage() {
  const { t, lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const dialog = useDialog();
  const [tab, setTab] = useState<TabId>('inbox');
  const [inbox, setInbox] = useState<DigitalSignatureTransaction[]>([]);
  const [all, setAll] = useState<DigitalSignatureTransaction[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [openNew, setOpenNew] = useState(false);

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    try {
      const [ix, allPage] = await Promise.all([
        listMyInbox().catch(() => []),
        listSignatures(0, 200).catch(() => ({ content: [], totalElements: 0 })),
      ]);
      setInbox(ix);
      setAll(allPage.content);
    } finally { setLoading(false); }
  }

  async function onSign(row: DigitalSignatureTransaction) {
    setBusyId(row.id);
    try {
      // For demo: hash the purpose text + current timestamp so each sign
      // produces a unique document binding. Real integration would hash
      // the canonical payload of the related entity.
      const payload = `${row.relatedEntityType}#${row.relatedEntityId}|${row.purpose}|${new Date().toISOString()}`;
      const hash = await sha256Hex(payload);
      await signTransaction(row.id, hash, `cert:internal:${row.id}`);
      toast.success(t('sigSignedToast'));
      await reload();
    } catch (e) {
      toast.danger(t('sigSignFailedToast'));
    } finally { setBusyId(null); }
  }

  async function onReject(row: DigitalSignatureTransaction) {
    const reason = await dialog.prompt({
      title: t('sigRejectPrompt'),
      label: lang === 'ar' ? 'سبب الرفض' : 'Rejection reason',
      type: 'textarea',
      required: true,
      placeholder: lang === 'ar' ? 'مثال: المستند لا يطابق المعتمد...' : 'e.g. Document does not match the approved version...',
    });
    if (!reason) return;
    setBusyId(row.id);
    try {
      await rejectTransaction(row.id, reason);
      toast.success(t('sigRejectedToast'));
      await reload();
    } catch {
      toast.danger(t('sigRejectFailedToast'));
    } finally { setBusyId(null); }
  }

  const stats = useMemo(() => {
    const c: Record<SignatureStatus, number> = { REQUESTED: 0, SIGNED: 0, REJECTED: 0, EXPIRED: 0 };
    for (const r of all) c[r.status] = (c[r.status] ?? 0) + 1;
    return c;
  }, [all]);

  // ── Open a new signature request ──────────────────────────────────────
  // Canonical related-entity types for the picker. The backend accepts any
  // string <=50 chars, but a curated list keeps requests consistent and
  // searchable; the seed data uses the same vocabulary (board_pack,
  // risk_acceptance, bcm_plan, …).
  const ENTITY_TYPES: Array<{ value: string; en: string; ar: string }> = [
    { value: 'BOARD_RESOLUTION',            en: 'Board resolution',            ar: 'قرار مجلس الإدارة' },
    { value: 'RISK_ACCEPTANCE',             en: 'Risk acceptance',             ar: 'قبول مخاطرة' },
    { value: 'POLICY_APPROVAL',             en: 'Policy approval',             ar: 'اعتماد سياسة' },
    { value: 'BCM_PLAN',                    en: 'BCM / continuity plan',       ar: 'خطة استمرارية الأعمال' },
    { value: 'AUDIT_SIGNOFF',               en: 'Audit sign-off',              ar: 'اعتماد تقرير المراجعة' },
    { value: 'SUPPLIER_ATTESTATION',        en: 'Supplier attestation',        ar: 'إقرار مورد' },
    { value: 'NCA_COMPLIANCE_ATTESTATION',  en: 'NCA compliance attestation',  ar: 'إقرار الالتزام بضوابط الهيئة' },
    { value: 'COMMITTEE_MINUTES',           en: 'Committee minutes',           ar: 'محضر لجنة' },
  ];
  const sigFields: FieldDef[] = [
    { key: 'relatedEntityType', label: isAr ? 'نوع السجل المرتبط' : 'Related record type', kind: 'select', required: true, span: 6,
      options: ENTITY_TYPES.map(e => ({ value: e.value, label: isAr ? e.ar : e.en })) },
    { key: 'relatedEntityId', label: isAr ? 'معرّف السجل' : 'Related record ID', kind: 'number', required: true, min: 1, span: 6 },
    { key: 'relatedEntityLabel', label: isAr ? 'وصف السجل (اختياري)' : 'Record label (optional)', kind: 'text', maxLength: 255, span: 12,
      placeholder: isAr ? 'مثال: RSK-2026-004 — تجاوز الشهية' : 'e.g. RSK-2026-004 — appetite breach' },
    { key: 'signerName', label: isAr ? 'اسم الموقّع' : 'Signer name', kind: 'text', required: true, maxLength: 255, span: 6 },
    { key: 'signerRole', label: isAr ? 'صفة الموقّع' : 'Signer role', kind: 'text', maxLength: 100, span: 6 },
    { key: 'signerEmail', label: isAr ? 'بريد الموقّع (اختياري)' : 'Signer email (optional)', kind: 'text', maxLength: 255, span: 6 },
    { key: 'expiryDate', label: isAr ? 'تاريخ الانتهاء (اختياري)' : 'Expiry date (optional)', kind: 'date', span: 6 },
    { key: 'purpose', label: isAr ? 'الغرض (إنجليزي)' : 'Purpose (English)', kind: 'textarea', rows: 2, span: 12 },
    { key: 'purposeAr', label: isAr ? 'الغرض (عربي)' : 'Purpose (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  // Memoised so FormModal's reset effect can't wipe the form on a re-render.
  const newInitial: Record<string, string> = useMemo(() => ({
    relatedEntityType: 'BOARD_RESOLUTION', relatedEntityId: '', relatedEntityLabel: '',
    signerName: '', signerRole: '', signerEmail: '', expiryDate: '', purpose: '', purposeAr: '',
  }), []);

  async function submitNew(v: Record<string, string>) {
    const relatedEntityType = String(v.relatedEntityType || '').trim();
    const idStr = String(v.relatedEntityId ?? '').trim();
    const relatedEntityId = Number(idStr);
    const signerName = String(v.signerName || '').trim();
    const purpose = String(v.purpose || '').trim();
    if (!relatedEntityType) { toast.danger(isAr ? 'نوع السجل مطلوب' : 'Record type is required'); return; }
    if (!idStr || !Number.isFinite(relatedEntityId) || relatedEntityId < 1) {
      toast.danger(isAr ? 'معرّف سجل صحيح مطلوب' : 'A valid related record ID is required'); return;
    }
    if (!signerName) { toast.danger(isAr ? 'اسم الموقّع مطلوب' : 'Signer name is required'); return; }
    if (!purpose)    { toast.danger(isAr ? 'الغرض (إنجليزي) مطلوب' : 'Purpose (English) is required'); return; }
    const expiry = String(v.expiryDate || '').trim();
    const body: DigitalSignatureCreateRequest = {
      relatedEntityType,
      relatedEntityId,
      relatedEntityLabel: String(v.relatedEntityLabel || '').trim() || undefined,
      signerName,
      signerEmail: String(v.signerEmail || '').trim() || undefined,
      signerRole: String(v.signerRole || '').trim() || undefined,
      purpose,
      purposeAr: String(v.purposeAr || '').trim() || undefined,
      // DTO expiryDate is a LocalDateTime — widen a bare date to midnight so
      // Jackson can parse it (a plain "YYYY-MM-DD" would be rejected).
      expiryDate: expiry ? `${expiry}T00:00:00` : undefined,
    };
    try {
      await createSignature(body);
      toast.success(isAr ? 'تم فتح طلب التوقيع' : 'Signature request opened');
      setOpenNew(false);
      setTab('all');
      await reload();
    } catch {
      toast.danger(isAr ? 'تعذّر فتح طلب التوقيع' : 'Failed to open the signature request');
    }
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'inbox', label: `${t('sigTabInbox')} (${inbox.length})` },
    { id: 'all',   label: t('sigTabAll') },
    { id: 'stats', label: t('sigTabStats') },
  ];

  return (
    <Shell title={t('sigPageTitle')} sub={t('sigPageSub')}>
      <PageHeader
        eyebrow={t('sigPageEyebrow')}
        title={t('sigPageTitle')}
        sub={t('sigPageSub')}
        actions={
          <Button variant="primary" Icon={FilePlus2} onClick={() => setOpenNew(true)}>
            {isAr ? 'طلب توقيع' : 'Request signature'}
          </Button>
        }
      />

      <div className="kpi-grid">
        <Kpi Icon={Inbox}         label={t('sigKpiInbox')}     value={inbox.length} tone="warning" />
        <Kpi Icon={CheckCircle2}  label={t('sigKpiSigned')}    value={stats.SIGNED} tone="success" />
        <Kpi Icon={XCircle}       label={t('sigKpiRejected')}  value={stats.REJECTED} tone="danger" />
        <Kpi Icon={AlertOctagon}  label={t('sigKpiExpired')}   value={stats.EXPIRED} tone="primary" />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'inbox' && (
        <Card title={t('sigInboxTitle')} Icon={Inbox}>
          {loading
            ? <InlineEmpty>{t('loadingEllipsis')}</InlineEmpty>
            : inbox.length === 0
              ? <EmptyState Icon={ShieldCheck} title={t('sigInboxEmpty')} sub={t('sigInboxEmptySub')} />
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {inbox.map(row => (
                    <Row key={row.id} row={row} lang={lang} t={t} busyId={busyId}
                         onSign={onSign} onReject={onReject} showActions />
                  ))}
                </div>}
        </Card>
      )}

      {tab === 'all' && (
        <Card title={t('sigAllTitle')} Icon={FileSignature}>
          {loading
            ? <InlineEmpty>{t('loadingEllipsis')}</InlineEmpty>
            : all.length === 0
              ? <EmptyState Icon={FileSignature} title={t('sigAllEmpty')} sub={t('sigAllEmptySub')} />
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
                  {all.map(row => (
                    <Row key={row.id} row={row} lang={lang} t={t} busyId={busyId}
                         onSign={onSign} onReject={onReject} showActions={row.status === 'REQUESTED'} />
                  ))}
                </div>}
        </Card>
      )}

      {tab === 'stats' && (
        <div className="dash-grid">
          <Card title={t('sigStatsByStatus')} Icon={PenLine}>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              {(['REQUESTED','SIGNED','REJECTED','EXPIRED'] as SignatureStatus[]).map(s => (
                <div key={s} style={{
                  display: 'flex', justifyContent: 'space-between', alignItems: 'center',
                  padding: '8px 10px', background: 'var(--surface-2)', borderRadius: 'var(--r-sm)',
                }}>
                  <Badge tone={STATUS_TONE[s]}>{t('sigStatus_' + s)}</Badge>
                  <span className="mono" style={{ fontSize: 18, fontWeight: 700 }}>{stats[s]}</span>
                </div>
              ))}
            </div>
          </Card>
        </div>
      )}

      <FormModal
        open={openNew}
        title={isAr ? 'فتح طلب توقيع رقمي' : 'Open a digital signature request'}
        fields={sigFields}
        initial={newInitial}
        submitLabel={isAr ? 'إرسال الطلب' : 'Open request'}
        size="lg"
        onClose={() => setOpenNew(false)}
        onSubmit={submitNew}
      />
    </Shell>
  );
}

function Row({ row, lang, t, busyId, onSign, onReject, showActions }: {
  row: DigitalSignatureTransaction;
  lang: 'en' | 'ar';
  t: (k: string) => string;
  busyId: number | null;
  onSign: (r: DigitalSignatureTransaction) => Promise<void>;
  onReject: (r: DigitalSignatureTransaction) => Promise<void>;
  showActions: boolean;
}) {
  const purpose = lang === 'ar' && row.purposeAr ? row.purposeAr : row.purpose;
  return (
    <div style={{
      padding: '12px 14px', background: 'var(--surface-2)',
      border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
      display: 'grid', gridTemplateColumns: '1fr auto', gap: 12, alignItems: 'center',
    }}>
      <div>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
          <Badge tone={STATUS_TONE[row.status]}>{t('sigStatus_' + row.status)}</Badge>
          <span style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">
            {row.relatedEntityType} #{row.relatedEntityId}
            {row.relatedEntityLabel ? ` · ${row.relatedEntityLabel}` : ''}
          </span>
        </div>
        <div style={{ fontWeight: 600, fontSize: 13, marginBottom: 2 }}>{purpose}</div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
          {t('sigSigner')}: <strong>{row.signerName}</strong>
          {row.signerRole ? ` (${row.signerRole})` : ''}
          {row.expiryDate ? ` · ${t('sigExpires')}: ${row.expiryDate.slice(0, 10)}` : ''}
        </div>
        {row.status === 'SIGNED' && row.signedDocumentHash && (
          <div style={{ fontSize: 10, color: 'var(--ink-4)', marginTop: 4 }} className="mono">
            sha256: {row.signedDocumentHash.slice(0, 16)}…
          </div>
        )}
        {row.status === 'REJECTED' && row.rejectionReason && (
          <div style={{ fontSize: 11, color: 'var(--danger-fg)', marginTop: 4 }}>
            ↳ {row.rejectionReason}
          </div>
        )}
      </div>
      {showActions && row.status === 'REQUESTED' && (
        <div style={{ display: 'flex', gap: 6, flexShrink: 0 }}>
          <Button size="sm" variant="primary" Icon={CheckCircle2}
            disabled={busyId === row.id}
            onClick={() => onSign(row)}>{t('sigActSign')}</Button>
          <Button size="sm" variant="ghost" Icon={XCircle}
            disabled={busyId === row.id}
            onClick={() => onReject(row)}>{t('sigActReject')}</Button>
        </div>
      )}
      {!showActions && row.status !== 'REQUESTED' && (
        <Clock size={14} style={{ color: 'var(--ink-3)' }} />
      )}
    </div>
  );
}
