import { useEffect, useMemo, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  Globe2, Trash2, FileText, Plus, ShieldCheck, CheckCircle2, XCircle, Play, Send,
  AlertOctagon, Clock, CopyPlus,
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
import {
  listCrossBorderTransfers, createCrossBorderTransfer, cbtAssess, cbtApprove, cbtReject,
  listDisposals, createDisposal, disposalStart, disposalComplete, disposalVerify,
  listNotices, createNotice, publishNotice, archiveNotice, supersedeNotice,
  CBT_STATUS_TONE, DISPOSAL_STATUS_TONE, NOTICE_STATUS_TONE,
  type CrossBorderTransfer, type DisposalCertificate, type PrivacyNotice,
} from '../../lib/pdplExtensions';

/**
 * PdplExtensionsPage — Saudi GRC prompt §3.5 / gap-analysis items 6 / 7 / 8.
 *
 * Three tabs:
 *   - Cross-border transfers (PDPL Art. 29)
 *   - Disposal certificates    (PDPL Art. 18 retention)
 *   - Privacy notices          (PDPL Art. 12 versioned bilingual)
 *
 * Each tab: KPI strip + list + per-row action buttons (state-machine
 * aware) + "+ New" FormModal. All API calls are per-tenant scoped via
 * the JWT in sessionStorage.
 */
type TabId = 'cbt' | 'disposal' | 'notice';

export function PdplExtensionsPage() {
  const { t, lang } = useI18n();
  const toast = useToast();

  // URL-driven state — ROPA pages deep-link here with ?tab=&ropaCode=&new=1
  // so the right tab is open, the lists are filtered to that ROPA, and the
  // create modal pre-fills the ROPA reference.
  const [params, setParams] = useSearchParams();
  const urlTab = (params.get('tab') ?? 'cbt') as TabId;
  const initialTab: TabId = ['cbt', 'disposal', 'notice'].includes(urlTab) ? urlTab : 'cbt';
  const [tab, setTab] = useState<TabId>(initialTab);
  const ropaFilter = params.get('ropaCode') ?? '';
  const wantNew = params.get('new') === '1';

  const [cbts, setCbts] = useState<CrossBorderTransfer[]>([]);
  const [disposals, setDisposals] = useState<DisposalCertificate[]>([]);
  const [notices, setNotices] = useState<PrivacyNotice[]>([]);
  const [busy, setBusy] = useState<number | null>(null);

  const [createCbtOpen, setCreateCbtOpen] = useState(false);
  const [createDisposalOpen, setCreateDisposalOpen] = useState(false);
  const [createNoticeOpen, setCreateNoticeOpen] = useState(false);

  useEffect(() => { void reloadAll(); }, []);

  // Auto-open the create modal for the active tab when ?new=1 is present.
  // Then strip the param so refreshing the page doesn't reopen the modal.
  useEffect(() => {
    if (!wantNew) return;
    if (tab === 'cbt') setCreateCbtOpen(true);
    else if (tab === 'disposal') setCreateDisposalOpen(true);
    else if (tab === 'notice') setCreateNoticeOpen(true);
    const next = new URLSearchParams(params);
    next.delete('new');
    setParams(next, { replace: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [wantNew, tab]);

  // Keep the URL in sync when the user clicks tabs so refresh keeps state.
  function changeTab(next: TabId) {
    setTab(next);
    const p = new URLSearchParams(params);
    p.set('tab', next);
    setParams(p, { replace: true });
  }

  // Per-tab filtered views: when ropaCode is in the URL, narrow the list to
  // the records that reference it. The three modules expose the link
  // differently (transfers via ropaCode column, disposals via
  // relatedEntityType=ropa+entityId, notices via comma-CSV linkedRopaCodes).
  const filteredCbts = useMemo(() =>
    ropaFilter ? cbts.filter(x => x.ropaCode === ropaFilter) : cbts,
    [cbts, ropaFilter]);
  const filteredDisposals = useMemo(() =>
    ropaFilter ? disposals.filter(x => x.relatedEntityType === 'ropa' && x.relatedEntityId === ropaFilter) : disposals,
    [disposals, ropaFilter]);
  const filteredNotices = useMemo(() => {
    if (!ropaFilter) return notices;
    return notices.filter(n => {
      const csv = (n.linkedRopaCodes ?? '').split(',').map(s => s.trim()).filter(Boolean);
      return csv.includes(ropaFilter);
    });
  }, [notices, ropaFilter]);

  async function reloadAll() {
    const [a, b, c] = await Promise.all([
      listCrossBorderTransfers(0, 200).catch(() => ({ content: [], totalElements: 0 })),
      listDisposals(0, 200).catch(() => ({ content: [], totalElements: 0 })),
      listNotices(0, 200).catch(() => ({ content: [], totalElements: 0 })),
    ]);
    setCbts(a.content);
    setDisposals(b.content);
    setNotices(c.content);
  }

  const tabs: TabItem<TabId>[] = [
    { id: 'cbt',      label: `${t('pdplExtCbtTab')} (${cbts.length})` },
    { id: 'disposal', label: `${t('pdplExtDisposalTab')} (${disposals.length})` },
    { id: 'notice',   label: `${t('pdplExtNoticeTab')} (${notices.length})` },
  ];

  return (
    <Shell title={t('pdplExtTitle')} sub={t('pdplExtSub')}>
      <PageHeader
        eyebrow={t('pdplExtEyebrow')}
        title={t('pdplExtTitle')}
        sub={t('pdplExtSub')}
      />

      {ropaFilter && (
        <div style={{
          padding: '8px 12px', marginBottom: 12,
          background: 'var(--primary-50)', border: '1px solid var(--primary-200)',
          borderRadius: 'var(--r-sm)', fontSize: 12,
          display: 'flex', alignItems: 'center', gap: 8, justifyContent: 'space-between',
        }}>
          <span>
            {t('pdplExtRopaFilterLabel')}: <strong className="mono">{ropaFilter}</strong>
          </span>
          <button type="button" onClick={() => {
            const p = new URLSearchParams(params);
            p.delete('ropaCode');
            setParams(p, { replace: true });
          }} style={{
            background: 'transparent', border: '1px solid var(--primary-200)',
            borderRadius: 'var(--r-sm)', padding: '2px 8px', cursor: 'pointer',
            fontSize: 11, color: 'var(--primary-700)',
          }}>
            {t('pdplExtRopaFilterClear')}
          </button>
        </div>
      )}
      <Tabs items={tabs} active={tab} onChange={changeTab} />

      {tab === 'cbt' && (
        <CbtTab rows={filteredCbts} busy={busy} setBusy={setBusy} t={t} toast={toast}
                onChanged={reloadAll}
                createOpen={createCbtOpen} setCreateOpen={setCreateCbtOpen}
                prefillRopaCode={ropaFilter} />
      )}

      {tab === 'disposal' && (
        <DisposalTab rows={filteredDisposals} busy={busy} setBusy={setBusy} lang={lang} t={t} toast={toast}
                     onChanged={reloadAll}
                     createOpen={createDisposalOpen} setCreateOpen={setCreateDisposalOpen}
                     prefillRopaCode={ropaFilter} />
      )}

      {tab === 'notice' && (
        <NoticeTab rows={filteredNotices} busy={busy} setBusy={setBusy} lang={lang} t={t} toast={toast}
                   onChanged={reloadAll}
                   createOpen={createNoticeOpen} setCreateOpen={setCreateNoticeOpen}
                   prefillRopaCode={ropaFilter} />
      )}
    </Shell>
  );
}

// ─── Cross-border transfer tab ───────────────────────────────────────

function CbtTab({ rows, busy, setBusy, t, toast, onChanged, createOpen, setCreateOpen, prefillRopaCode }: any) {
  const stats = countByStatus(rows, 'status');
  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'ropaCode', label: 'ROPA code', required: true, span: 6 },
    { kind: 'text', key: 'ropaTitleEn', label: 'ROPA title (EN)', span: 6 },
    { kind: 'text', key: 'destinationCountry', label: t('pdplExtCbtDestCountry') + ' *', required: true, maxLength: 3, span: 4 },
    { kind: 'text', key: 'destinationOrgName', label: t('pdplExtCbtDestOrg') + ' *', required: true, span: 8 },
    { kind: 'select', key: 'transferMechanism', label: t('pdplExtCbtMechanism') + ' *', required: true,
      options: [
        { value: 'ADEQUACY', label: 'Adequacy decision' },
        { value: 'SCC', label: 'Standard Contractual Clauses' },
        { value: 'BCR', label: 'Binding Corporate Rules' },
        { value: 'EXPLICIT_CONSENT', label: 'Explicit consent' },
        { value: 'CONTRACT_NECESSITY', label: 'Contract necessity' },
        { value: 'VITAL_INTEREST', label: 'Vital interest' },
        { value: 'PUBLIC_INTEREST', label: 'Public interest' },
      ], span: 6 },
    { kind: 'select', key: 'destinationOrgRole', label: t('pdplExtCbtDestRole'),
      options: [
        { value: '', label: '—' },
        { value: 'CONTROLLER', label: 'Controller' },
        { value: 'PROCESSOR', label: 'Processor' },
        { value: 'SUB_PROCESSOR', label: 'Sub-processor' },
      ], span: 6 },
    { kind: 'textarea', key: 'dataCategories', label: t('pdplExtCbtCategories') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'safeguardsEn', label: t('pdplExtCbtSafeguards'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'justificationEn', label: t('pdplExtCbtJustification') + ' *', rows: 2, span: 12 },
  ];

  async function onCreate(values: Record<string, any>) {
    try {
      await createCrossBorderTransfer({
        ropaCode: values.ropaCode,
        ropaTitleEn: values.ropaTitleEn,
        destinationCountry: String(values.destinationCountry).toUpperCase(),
        destinationOrgName: values.destinationOrgName,
        destinationOrgRole: values.destinationOrgRole || undefined,
        dataCategories: values.dataCategories,
        transferMechanism: values.transferMechanism,
        safeguardsEn: values.safeguardsEn || undefined,
        justificationEn: values.justificationEn,
      });
      toast.success(t('pdplExtCreatedToast'));
      setCreateOpen(false);
      await onChanged();
    } catch { toast.danger(t('pdplExtCreateFailedToast')); }
  }

  async function onAssess(row: CrossBorderTransfer) {
    setBusy(row.id);
    try { await cbtAssess(row.id, 'DPO Office', 'PASS'); toast.success(t('pdplExtAssessedToast')); await onChanged(); }
    catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }
  async function onApprove(row: CrossBorderTransfer) {
    setBusy(row.id);
    try { await cbtApprove(row.id); toast.success(t('pdplExtApprovedToast')); await onChanged(); }
    catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }
  async function onReject(row: CrossBorderTransfer) {
    const reason = window.prompt(t('pdplExtRejectPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusy(row.id);
    try { await cbtReject(row.id, reason.trim()); toast.success(t('pdplExtRejectedToast')); await onChanged(); }
    catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }

  return (
    <>
      <div className="kpi-grid">
        <Kpi Icon={Send}         label={t('pdplExtCbtKpiRequested')} value={stats.REQUESTED || 0} tone="warning" />
        <Kpi Icon={ShieldCheck}  label={t('pdplExtCbtKpiAssessed')}  value={stats.ASSESSED || 0}  tone="primary" />
        <Kpi Icon={CheckCircle2} label={t('pdplExtCbtKpiApproved')}  value={stats.APPROVED || 0}  tone="success" />
        <Kpi Icon={XCircle}      label={t('pdplExtCbtKpiRejected')}  value={stats.REJECTED || 0}  tone="danger" />
      </div>

      <Card title={t('pdplExtCbtTitle')} Icon={Globe2}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
            {t('pdplExtCbtAddBtn')}
          </Button>
        }>
        {rows.length === 0
          ? <EmptyState Icon={Globe2} title={t('pdplExtCbtEmpty')} sub={t('pdplExtCbtEmptySub')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {rows.map((r: CrossBorderTransfer) => (
                <div key={r.id} style={rowStyle()}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <Badge tone={CBT_STATUS_TONE[r.status]}>{t('pdplExtCbtStatus_' + r.status)}</Badge>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.ropaCode}</span>
                    </div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>
                      {r.destinationOrgName} ({r.destinationCountry})
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                      {t('pdplExtCbtMechanism')}: <strong>{r.transferMechanism}</strong>
                      {' · '}{r.dataCategories}
                    </div>
                    {r.rejectionReason && (
                      <div style={{ fontSize: 11, color: 'var(--danger-fg)', marginTop: 4 }}>
                        ↳ {r.rejectionReason}
                      </div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {r.status === 'REQUESTED' && (
                      <Button size="sm" variant="primary" Icon={ShieldCheck} disabled={busy === r.id}
                              onClick={() => onAssess(r)}>{t('pdplExtCbtActAssess')}</Button>
                    )}
                    {r.status === 'ASSESSED' && (
                      <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busy === r.id}
                              onClick={() => onApprove(r)}>{t('pdplExtCbtActApprove')}</Button>
                    )}
                    {(r.status === 'REQUESTED' || r.status === 'ASSESSED') && (
                      <Button size="sm" variant="ghost" Icon={XCircle} disabled={busy === r.id}
                              onClick={() => onReject(r)}>{t('pdplExtCbtActReject')}</Button>
                    )}
                  </div>
                </div>
              ))}
            </div>}
      </Card>

      <FormModal<Record<string, any>>
        open={createOpen}
        title={t('pdplExtCbtAddTitle')}
        fields={FIELDS}
        initial={prefillRopaCode ? { ropaCode: prefillRopaCode } : {}}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </>
  );
}

// ─── Disposal certificate tab ────────────────────────────────────────

function DisposalTab({ rows, busy, setBusy, t, toast, onChanged, createOpen, setCreateOpen, prefillRopaCode }: any) {
  const stats = countByStatus(rows, 'status');
  const FIELDS: FieldDef[] = [
    { kind: 'select', key: 'relatedEntityType', label: t('pdplExtDspEntityType') + ' *', required: true,
      options: [
        { value: 'ropa', label: 'ROPA' },
        { value: 'data_asset', label: 'Data asset' },
        { value: 'system', label: 'System' },
      ], span: 4 },
    { kind: 'text', key: 'relatedEntityId', label: t('pdplExtDspEntityId') + ' *', required: true, span: 4 },
    { kind: 'text', key: 'relatedEntityLabel', label: 'Label', span: 4 },
    { kind: 'text', key: 'retentionRuleRef', label: t('pdplExtDspRetentionRef'), placeholder: 'RET-7Y', span: 6 },
    { kind: 'select', key: 'disposalMethod', label: t('pdplExtDspMethod') + ' *', required: true,
      options: [
        { value: 'DELETE', label: 'Delete' },
        { value: 'ANONYMIZE', label: 'Anonymise' },
        { value: 'ARCHIVE', label: 'Archive' },
        { value: 'DESTROY', label: 'Physical destruction' },
      ], span: 6 },
    { kind: 'textarea', key: 'scopeDescriptionEn', label: t('pdplExtDspScope') + ' *', rows: 2, span: 12 },
    { kind: 'number', key: 'recordsCountEst', label: t('pdplExtDspRecords'), span: 6 },
    { kind: 'text', key: 'ownerName', label: t('pdplExtDspOwner'), span: 6 },
  ];

  async function onCreate(values: Record<string, any>) {
    try {
      await createDisposal({
        relatedEntityType: values.relatedEntityType,
        relatedEntityId: values.relatedEntityId,
        relatedEntityLabel: values.relatedEntityLabel || undefined,
        retentionRuleRef: values.retentionRuleRef || undefined,
        scopeDescriptionEn: values.scopeDescriptionEn,
        disposalMethod: values.disposalMethod,
        recordsCountEst: values.recordsCountEst ? Number(values.recordsCountEst) : undefined,
        ownerName: values.ownerName || undefined,
      });
      toast.success(t('pdplExtCreatedToast'));
      setCreateOpen(false);
      await onChanged();
    } catch { toast.danger(t('pdplExtCreateFailedToast')); }
  }

  async function step(row: DisposalCertificate, action: 'start' | 'complete' | 'verify') {
    setBusy(row.id);
    try {
      if (action === 'start') await disposalStart(row.id);
      if (action === 'complete') await disposalComplete(row.id, 'Executor');
      if (action === 'verify') await disposalVerify(row.id, 'Verifier');
      toast.success(t('pdplExtDspStepToast'));
      await onChanged();
    } catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }

  return (
    <>
      <div className="kpi-grid">
        <Kpi Icon={Clock}        label={t('pdplExtDspKpiScheduled')}   value={stats.SCHEDULED || 0}   tone="warning" />
        <Kpi Icon={Play}         label={t('pdplExtDspKpiInProgress')}  value={stats.IN_PROGRESS || 0} tone="primary" />
        <Kpi Icon={CheckCircle2} label={t('pdplExtDspKpiCompleted')}   value={stats.COMPLETED || 0}   tone="primary" />
        <Kpi Icon={ShieldCheck}  label={t('pdplExtDspKpiVerified')}    value={stats.VERIFIED || 0}    tone="success" />
      </div>

      <Card title={t('pdplExtDspTitle')} Icon={Trash2}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
            {t('pdplExtDspAddBtn')}
          </Button>
        }>
        {rows.length === 0
          ? <EmptyState Icon={Trash2} title={t('pdplExtDspEmpty')} sub={t('pdplExtDspEmptySub')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {rows.map((r: DisposalCertificate) => (
                <div key={r.id} style={rowStyle()}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <Badge tone={DISPOSAL_STATUS_TONE[r.status]}>{t('pdplExtDspStatus_' + r.status)}</Badge>
                      <Badge tone="info">{r.disposalMethod}</Badge>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {r.relatedEntityType}#{r.relatedEntityId}
                      </span>
                    </div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{r.scopeDescriptionEn}</div>
                    {r.evidenceRef && (
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {t('pdplExtDspEvidence')}: <strong>{r.evidenceRef}</strong>
                      </div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {r.status === 'SCHEDULED' && (
                      <Button size="sm" variant="primary" Icon={Play} disabled={busy === r.id}
                              onClick={() => step(r, 'start')}>{t('pdplExtDspActStart')}</Button>
                    )}
                    {r.status === 'IN_PROGRESS' && (
                      <Button size="sm" variant="primary" Icon={CheckCircle2} disabled={busy === r.id}
                              onClick={() => step(r, 'complete')}>{t('pdplExtDspActComplete')}</Button>
                    )}
                    {r.status === 'COMPLETED' && (
                      <Button size="sm" variant="primary" Icon={ShieldCheck} disabled={busy === r.id}
                              onClick={() => step(r, 'verify')}>{t('pdplExtDspActVerify')}</Button>
                    )}
                  </div>
                </div>
              ))}
            </div>}
      </Card>

      <FormModal<Record<string, any>>
        open={createOpen}
        title={t('pdplExtDspAddTitle')}
        fields={FIELDS}
        initial={prefillRopaCode
          ? { relatedEntityType: 'ropa', disposalMethod: 'ANONYMIZE', relatedEntityId: prefillRopaCode }
          : { relatedEntityType: 'ropa', disposalMethod: 'ANONYMIZE' }}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </>
  );
}

// ─── Privacy notice tab ─────────────────────────────────────────────

function NoticeTab({ rows, busy, setBusy, lang, t, toast, onChanged, createOpen, setCreateOpen, prefillRopaCode }: any) {
  const ar = lang === 'ar';
  const stats = countByStatus(rows, 'status');
  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'noticeCode', label: t('pdplExtPnCode') + ' *', required: true, placeholder: 'PN-EMP', span: 6 },
    { kind: 'select', key: 'lawfulBasis', label: t('pdplExtPnLawfulBasis'),
      options: [
        { value: '', label: '—' },
        { value: 'CONSENT', label: 'Consent' },
        { value: 'CONTRACT', label: 'Contract' },
        { value: 'LEGAL_OBLIGATION', label: 'Legal obligation' },
        { value: 'VITAL_INTEREST', label: 'Vital interest' },
        { value: 'PUBLIC_INTEREST', label: 'Public interest' },
        { value: 'LEGITIMATE_INTEREST', label: 'Legitimate interest' },
      ], span: 6 },
    { kind: 'text', key: 'titleEn', label: t('pdplExtPnTitleEn') + ' *', required: true, span: 6 },
    { kind: 'text', key: 'titleAr', label: t('pdplExtPnTitleAr') + ' *', required: true, span: 6 },
    { kind: 'textarea', key: 'purposeEn', label: t('pdplExtPnPurpose') + ' *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'bodyEn', label: t('pdplExtPnBody') + ' *', rows: 4, span: 12 },
    { kind: 'text', key: 'dpoContact', label: t('pdplExtPnDpoContact'), span: 6 },
    { kind: 'text', key: 'linkedRopaCodes', label: t('pdplExtPnLinkedRopa'), placeholder: 'ROPA-001,ROPA-002', span: 6 },
  ];

  async function onCreate(values: Record<string, any>) {
    try {
      await createNotice({
        noticeCode: values.noticeCode,
        titleEn: values.titleEn,
        titleAr: values.titleAr,
        purposeEn: values.purposeEn,
        bodyEn: values.bodyEn,
        dpoContact: values.dpoContact || undefined,
        lawfulBasis: values.lawfulBasis || undefined,
        linkedRopaCodes: values.linkedRopaCodes || undefined,
      });
      toast.success(t('pdplExtCreatedToast'));
      setCreateOpen(false);
      await onChanged();
    } catch { toast.danger(t('pdplExtCreateFailedToast')); }
  }

  async function onPublish(row: PrivacyNotice) {
    setBusy(row.id);
    try { await publishNotice(row.id); toast.success(t('pdplExtPnPublishedToast')); await onChanged(); }
    catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }
  async function onArchive(row: PrivacyNotice) {
    const reason = window.prompt(t('pdplExtPnArchivePrompt'), '') ?? undefined;
    setBusy(row.id);
    try { await archiveNotice(row.id, reason); toast.success(t('pdplExtPnArchivedToast')); await onChanged(); }
    catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }
  // Supersede a published notice — the server clones it into a fresh DRAFT
  // (version + 1, supersedesId set) so the new revision can be edited and
  // re-published while the live one keeps serving until then.
  async function onSupersede(row: PrivacyNotice) {
    if (!window.confirm(ar
      ? `إنشاء نسخة جديدة (v${row.version + 1}) كمسودة من ${row.noticeCode}؟ ستبقى النسخة المنشورة سارية حتى نشر الجديدة.`
      : `Create a new draft revision (v${row.version + 1}) of ${row.noticeCode}? The published version stays live until the new one is published.`)) return;
    setBusy(row.id);
    try {
      const next = await supersedeNotice(row.id);
      toast.success(ar ? `أُنشئت المسودة v${next.version}` : `Draft v${next.version} created`);
      await onChanged();
    } catch { toast.danger(t('pdplExtActionFailedToast')); }
    finally { setBusy(null); }
  }

  return (
    <>
      <div className="kpi-grid">
        <Kpi Icon={FileText}     label={t('pdplExtPnKpiDraft')}     value={stats.DRAFT || 0}     tone="warning" />
        <Kpi Icon={CheckCircle2} label={t('pdplExtPnKpiPublished')} value={stats.PUBLISHED || 0} tone="success" />
        <Kpi Icon={AlertOctagon} label={t('pdplExtPnKpiArchived')}  value={stats.ARCHIVED || 0}  tone="primary" />
      </div>

      <Card title={t('pdplExtPnTitle')} Icon={FileText}
        action={
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => setCreateOpen(true)}>
            {t('pdplExtPnAddBtn')}
          </Button>
        }>
        {rows.length === 0
          ? <EmptyState Icon={FileText} title={t('pdplExtPnEmpty')} sub={t('pdplExtPnEmptySub')} />
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 10 }}>
              {rows.map((r: PrivacyNotice) => (
                <div key={r.id} style={rowStyle()}>
                  <div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 4 }}>
                      <Badge tone={NOTICE_STATUS_TONE[r.status]}>{t('pdplExtPnStatus_' + r.status)}</Badge>
                      <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {r.noticeCode} v{r.version}
                      </span>
                    </div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{r.titleEn}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.titleAr}</div>
                    {r.linkedRopaCodes && (
                      <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
                        ROPA: <span className="mono">{r.linkedRopaCodes}</span>
                      </div>
                    )}
                  </div>
                  <div style={{ display: 'flex', gap: 6 }}>
                    {r.status === 'DRAFT' && (
                      <Button size="sm" variant="primary" Icon={Send} disabled={busy === r.id}
                              onClick={() => onPublish(r)}>{t('pdplExtPnActPublish')}</Button>
                    )}
                    {r.status === 'PUBLISHED' && (
                      <Button size="sm" variant="secondary" Icon={CopyPlus} disabled={busy === r.id}
                              onClick={() => onSupersede(r)}>{ar ? 'إصدار نسخة جديدة' : 'Supersede'}</Button>
                    )}
                    {r.status === 'PUBLISHED' && (
                      <Button size="sm" variant="ghost" Icon={AlertOctagon} disabled={busy === r.id}
                              onClick={() => onArchive(r)}>{t('pdplExtPnActArchive')}</Button>
                    )}
                  </div>
                </div>
              ))}
            </div>}
      </Card>

      <FormModal<Record<string, any>>
        open={createOpen}
        title={t('pdplExtPnAddTitle')}
        fields={FIELDS}
        initial={prefillRopaCode ? { linkedRopaCodes: prefillRopaCode } : {}}
        onClose={() => setCreateOpen(false)}
        onSubmit={onCreate}
      />
    </>
  );
}

// ─── Shared helpers ────────────────────────────────────────────────

function rowStyle(): React.CSSProperties {
  return {
    padding: '12px 14px',
    background: 'var(--surface-2)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--r-md)',
    display: 'grid',
    gridTemplateColumns: '1fr auto',
    gap: 12,
    alignItems: 'center',
  };
}

function countByStatus(rows: Array<{ status: string }>, _key: 'status'): Record<string, number> {
  const out: Record<string, number> = {};
  for (const r of rows) out[r.status] = (out[r.status] ?? 0) + 1;
  return out;
}
