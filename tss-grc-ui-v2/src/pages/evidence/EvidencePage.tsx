import { useEffect, useMemo, useRef, useState, type ChangeEvent } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Files, Inbox, CalendarX, BadgeCheck, Filter, Download, Plus, BarChart3, FileText, Send, ClipboardCheck,
  Layers, Plug, ChevronDown, ChevronRight, History, Power, Zap, Paperclip, Sparkles, CheckCircle2, AlertTriangle,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { FreshnessCalendar } from '../../components/viz/FreshnessCalendar';
import { PillTabs } from '../../components/ui/PillTabs';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Progress } from '../../components/ui/Progress';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import {
  evidenceApi, ApiError,
  type EvidenceItemDto, type EvidenceItemRequest,
  type EvidenceRequestDto, type EvidenceReviewDto,
  type FrameworkBlock, type SubControlBlock,
  type ConnectorDefinitionDto, type EvidenceVersionDto,
} from '../../lib/api';
import { Modal } from '../../components/ui/Modal';
import { ai, type AiSuggestion } from '../../lib/ai';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { EVIDENCE, EVIDENCE_REQUESTS, EVIDENCE_REVIEWS, EVIDENCE_ANCHORS_BY_ID } from './mockEvidence';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { EVIDENCE_WORKFLOWS } from '../../lib/mockWorkflows';
import { EVIDENCE_LIFECYCLES } from '../../lib/moduleLifecycles';
import { AnchorChips } from '../../components/authority/AnchorChips';
import { OverviewStrip } from '../../components/layout/OverviewStrip';

type TabId = 'overview' | 'frameworks' | 'connectors' | 'catalog' | 'requests' | 'review' | 'expired' | 'workflows';

const FRESHNESS_TONE: Record<string, 'success' | 'warning' | 'danger' | 'neutral'> = {
  GREEN: 'success', AMBER: 'warning', RED: 'danger', GREY: 'neutral',
};
const CONNECTOR_STATUS_TONE: Record<string, 'success' | 'warning' | 'info' | 'neutral'> = {
  AVAILABLE: 'info', CONFIGURED: 'warning', RUNNING: 'success', DISABLED: 'neutral',
};

const TYPE_OPTIONS = [
  { value: 'POLICY', label: 'Policy' }, { value: 'SCREENSHOT', label: 'Screenshot' },
  { value: 'AUDIT_REPORT', label: 'Audit Report' }, { value: 'CONFIGURATION', label: 'Configuration' },
  { value: 'CERTIFICATE', label: 'Certificate' }, { value: 'DOCUMENT', label: 'Document' },
];
// FIELDS now built inside the component via t()
const EMPTY: EvidenceItemRequest = {
  titleEn: '', titleAr: '', evidenceType: 'POLICY',
  collectedDate: '', expiryDate: '', descriptionEn: '',
};

function itemToReq(i: EvidenceItemDto): EvidenceItemRequest {
  return {
    titleEn: i.titleEn, titleAr: i.titleAr,
    descriptionEn: i.descriptionEn,
    evidenceType: i.evidenceType,
    collectedDate: i.collectedDate, expiryDate: i.expiryDate,
  };
}

// Evidence-request SLA: render the due date with an overdue / due-soon badge.
// Closed/satisfied requests just show the date (no SLA pressure).
const REQ_DONE = new Set(['SATISFIED', 'COMPLETED', 'CLOSED', 'APPROVED', 'FULFILLED']);
function dueCell(r: EvidenceRequestDto, lang: string) {
  const ar = lang === 'ar';
  if (!r.dueDate) return <span style={{ color: 'var(--ink-3)' }}>—</span>;
  const date = <span className="mono">{r.dueDate}</span>;
  if (REQ_DONE.has((r.status ?? '').toUpperCase())) return date;
  const today = new Date().toISOString().slice(0, 10);
  const days = Math.round((new Date(r.dueDate).getTime() - new Date(today).getTime()) / 86_400_000);
  if (Number.isNaN(days)) return date;
  const badge = days < 0
    ? <Badge tone="danger">{ar ? `متأخر ${-days} ي` : `${-days}d overdue`}</Badge>
    : days <= 7
      ? <Badge tone="warning">{ar ? `خلال ${days} ي` : `in ${days}d`}</Badge>
      : null;
  return badge
    ? <span style={{ display: 'inline-flex', gap: 6, alignItems: 'center' }}>{date}{badge}</span>
    : date;
}

export function EvidencePage() {
  const { t, lang } = useI18n();
  const ar = lang === 'ar';
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<EvidenceItemDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<EvidenceItemDto | null>(null);
  const [busy, setBusy] = useState(false);

  // ── Binary file upload (multipart) — wires POST /evidence/items/{id}/file ──
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [uploadTargetId, setUploadTargetId] = useState<number | null>(null);
  const [uploading, setUploading] = useState(false);
  function pickFileFor(item: EvidenceItemDto) {
    setUploadTargetId(item.id);
    fileInputRef.current?.click();
  }
  async function onFileChosen(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    e.target.value = '';                       // allow re-selecting the same file
    if (!file || uploadTargetId == null) return;
    setUploading(true);
    try {
      await evidenceApi.uploadFile(uploadTargetId, file);
      toast.success(t('evidenceFileUploaded'));
      items.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('evidenceFileUploadFailed'));
    } finally {
      setUploading(false);
      setUploadTargetId(null);
    }
  }

  const items    = useApiList<EvidenceItemDto>(()    => evidenceApi.items(0, 100),    EVIDENCE);
  const requests = useApiList<EvidenceRequestDto>(() => evidenceApi.requests(0, 100), EVIDENCE_REQUESTS);
  const reviews  = useApiList<EvidenceReviewDto>(()  => evidenceApi.reviews(0, 100),  EVIDENCE_REVIEWS);

  // ── DD-47: filter modal ────────────────────────────────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  const filteredEvidence = useMemo(() => items.data.filter(i => {
    if (advFilters.status && i.status !== advFilters.status) return false;
    if (advFilters.evidenceType && (i.evidenceType ?? '') !== advFilters.evidenceType) return false;
    if (advFilters.framework && (i.frameworkCode ?? '') !== advFilters.framework) return false;
    if (advFilters.expiringBefore && (i.expiryDate ?? '9999-12-31') > advFilters.expiringBefore) return false;
    return true;
  }), [items.data, advFilters]);
  const evidenceFilterFields: FilterField[] = [
    { key: 'status',         label: t('filterFieldStatus'),    kind: 'select', options: distinctValues(items.data, 'status') },
    { key: 'evidenceType',   label: t('filterFieldType'),      kind: 'select', options: distinctValues(items.data, 'evidenceType') },
    { key: 'framework',      label: t('filterFieldFramework'), kind: 'select', options: distinctValues(items.data.filter(i => i.frameworkCode), 'frameworkCode') },
    { key: 'expiringBefore', label: 'Expiring before', kind: 'date' },
  ];

  // ── Matrix (frameworks → controls → sub-controls), Connectors, Version history ──
  const [matrix, setMatrix] = useState<FrameworkBlock[]>([]);
  const [matrixLoading, setMatrixLoading] = useState(false);
  const [activeFrameworkId, setActiveFrameworkId] = useState<number | null>(null);
  const [expandedControlIds, setExpandedControlIds] = useState<Set<number>>(new Set());
  const [connectors, setConnectors] = useState<ConnectorDefinitionDto[]>([]);
  const [connectorsLoading, setConnectorsLoading] = useState(false);
  const [versionsModal, setVersionsModal] = useState<{ item: EvidenceItemDto; versions: EvidenceVersionDto[] } | null>(null);

  function loadMatrix() {
    if (!isAuthenticated()) return;
    setMatrixLoading(true);
    evidenceApi.matrix()
      .then(rows => {
        setMatrix(rows);
        if (rows.length > 0 && activeFrameworkId == null) setActiveFrameworkId(rows[0].frameworkId);
      })
      .catch(() => setMatrix([]))
      .finally(() => setMatrixLoading(false));
  }
  function loadConnectors() {
    if (!isAuthenticated()) return;
    setConnectorsLoading(true);
    evidenceApi.connectors()
      .then(setConnectors)
      .catch(() => setConnectors([]))
      .finally(() => setConnectorsLoading(false));
  }
  // Lazy-load each tab's data on first activation.
  useEffect(() => { if (tab === 'frameworks' && matrix.length === 0 && !matrixLoading) loadMatrix(); }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (tab === 'connectors' && connectors.length === 0 && !connectorsLoading) loadConnectors(); }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps

  function toggleControl(id: number) {
    setExpandedControlIds(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }
  async function openVersions(item: EvidenceItemDto) {
    try {
      const versions = await evidenceApi.versions(item.id);
      setVersionsModal({ item, versions });
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Could not load versions'); }
  }
  async function snapshotVersion(itemId: number) {
    const summary = window.prompt('Change summary?', '') ?? '';
    if (!summary.trim()) return;
    try {
      await evidenceApi.snapshot(itemId, summary);
      toast.success('Snapshot recorded');
      const versions = await evidenceApi.versions(itemId);
      setVersionsModal(prev => prev ? { ...prev, versions } : null);
      items.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Snapshot failed'); }
  }
  // ── AI evidence-quality check (POST /api/v1/ai/evidence-quality) ──
  // Rule-based scorer on age / signature / type / control linkage.
  // Persists a DRAFT AiSuggestion to the AI Suggestions review queue.
  const [aiCheck, setAiCheck] = useState<{ item: EvidenceItemDto; result: AiSuggestion | null; loading: boolean } | null>(null);
  async function runAiCheck(item: EvidenceItemDto) {
    setAiCheck({ item, result: null, loading: true });
    const collected = item.collectedAt ?? item.collectedDate ?? item.createdAt;
    const ageDays = collected
      ? Math.max(0, Math.round((Date.now() - new Date(collected).getTime()) / 86_400_000))
      : undefined;
    try {
      const result = await ai.evidenceQuality({
        evidenceId:    item.id,
        evidenceTitle: item.titleEn,
        evidenceType:  item.evidenceType,
        controlCode:   item.controlCode ?? item.subControlCode,
        ageDays,
        hasSignature:  item.fileName ? undefined : undefined,
      });
      setAiCheck({ item, result, loading: false });
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message
        : (ar ? 'تعذّر تقييم جودة الدليل' : 'AI quality check failed'));
      setAiCheck(null);
    }
  }

  async function runConnector(c: ConnectorDefinitionDto) {
    try {
      const updated = await evidenceApi.runConnector(c.id);
      toast.success(`Connector "${c.code}" run recorded`);
      setConnectors(prev => prev.map(x => x.id === updated.id ? updated : x));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Run failed'); }
  }
  async function toggleConnectorStatus(c: ConnectorDefinitionDto) {
    const next = c.status === 'DISABLED' ? 'AVAILABLE' : 'DISABLED';
    try {
      const updated = await evidenceApi.setConnectorStatus(c.id, next);
      setConnectors(prev => prev.map(x => x.id === updated.id ? updated : x));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Status change failed'); }
  }

  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'titleEn',         label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'evidenceType',  label: t('cyberFldEvidenceType') + ' *', options: TYPE_OPTIONS, required: true, span: 4 },
    { kind: 'text', key: 'titleAr',         label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'number', key: 'subControlId',  label: t('cyberFldLinkedCtrl') + ' (ID)', span: 4 },
    { kind: 'date', key: 'collectedDate',   label: t('fldReviewDate'), span: 4 },
    { kind: 'date', key: 'expiryDate',      label: t('fldExpiryDate'), span: 4 },
    { kind: 'textarea', key: 'descriptionEn', label: t('fldDescriptionLbl'), rows: 3, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',   label: t('tabOverview') },
    { id: 'frameworks', label: t('evidenceTabFrameworks') },
    { id: 'connectors', label: t('evidenceTabConnectors') },
    { id: 'catalog',    label: t('evidenceTabCatalog') },
    { id: 'requests',   label: t('evidenceTabRequests') },
    { id: 'review',     label: t('evidenceTabReview') },
    { id: 'expired',    label: t('evidenceTabExpired') },
    { id: 'workflows',  label: t('evidenceTabWorkflows') ?? 'Workflows' },
  ];

  const activeFramework = matrix.find(f => f.frameworkId === activeFrameworkId) ?? matrix[0];

  const itemCols: Column<EvidenceItemDto>[] = [
    { header: t('colCode'),      cell: r => r.evidenceCode, mono: true },
    { header: t('colTitle'),     cell: r => (
        <span style={{ fontWeight: 600, display: 'inline-flex', alignItems: 'center', gap: 6 }}>
          {r.titleEn}
          {r.fileName && (
            <span title={r.fileName} style={{ display: 'inline-flex', alignItems: 'center', gap: 3, fontSize: 11, fontWeight: 400, color: 'var(--ink-3)' }}>
              <Paperclip size={12} /> {r.fileName.length > 24 ? r.fileName.slice(0, 24) + '…' : r.fileName}
            </span>
          )}
        </span>
      ) },
    { header: t('colType'),      cell: r => <Badge tone="neutral">{(r.evidenceType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: 'Regulator anchors', cell: r => <AnchorChips anchors={EVIDENCE_ANCHORS_BY_ID[r.id] ?? []} max={3} /> },
    { header: t('colExpiry'),    cell: r => <span className="mono">{r.expiryDate ?? '—'}</span> },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '120px',
      cell: r => (
        <RowActions
          extras={[
            { Icon: Sparkles, onClick: () => runAiCheck(r), title: ar ? 'تقييم جودة الدليل (ذكاء اصطناعي)' : 'AI quality check', tone: 'primary' },
            { Icon: Paperclip, onClick: () => pickFileFor(r), title: t('evidenceAttachFile'), tone: 'primary' },
            { Icon: History, onClick: () => openVersions(r), title: t('evidenceVersionHistory'), tone: 'primary' },
          ]}
          onEdit={() => { setEditing(r); setOpen(true); }}
          onDelete={() => setConfirmDel(r)} />
      ) },
  ];

  const reqCols: Column<EvidenceRequestDto>[] = [
    { header: t('colCode'),       cell: r => r.requestCode, mono: true },
    { header: t('colTitle'),      cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colSubControl'), cell: r => r.subControlCode ?? '—', mono: true },
    { header: t('colFramework'),  cell: r => <Badge tone="primary">{r.frameworkCode ?? '—'}</Badge> },
    { header: t('colOwner'),      cell: r => r.ownerName ?? '—' },
    { header: t('colDue'),        cell: r => dueCell(r, lang) },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
  ];

  const reviewCols: Column<EvidenceReviewDto>[] = [
    { header: t('colEvidence'),  cell: r => <span style={{ fontWeight: 600 }}>{r.evidenceTitleEn ?? `#${r.evidenceId}`}</span> },
    { header: t('colReviewer'),  cell: r => r.reviewerName ?? '—' },
    { header: t('colDate'),      cell: r => r.reviewDate ?? '—', mono: true },
    { header: t('colResult'),    cell: r => <Badge tone={r.result === 'APPROVED' ? 'success' : r.result === 'REJECTED' ? 'danger' : 'info'} dot>{r.result ?? '—'}</Badge> },
    { header: t('colNotes'),     cell: r => <span style={{ fontSize: 12, color: 'var(--ink-3)' }}>{(r.notesEn ?? '—').slice(0, 60)}</span> },
  ];

  const expiredItems = items.data.filter(i => i.expiryDate && new Date(i.expiryDate) < new Date());

  const approved = items.data.filter(i => i.status === 'APPROVED').length;
  const pct = items.data.length ? Math.round((approved / items.data.length) * 100) : 0;

  async function onSubmit(values: EvidenceItemRequest) {
    try {
      if (editing) await evidenceApi.updateItem(editing.id, values);
      else await evidenceApi.createItem(values);
      toast.success(editing ? 'Evidence updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); items.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await evidenceApi.deleteItem(confirmDel.id); toast.success('Evidence deleted'); items.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const isDemo = items.isDemo;
  const frameworks = Array.from(new Set(items.data.map(i => i.frameworkCode).filter(Boolean))) as string[];

  return (
    <Shell title={t('evidenceTitle')} sub="Evidence Catalog">
      <PageHeader
        eyebrow={t('evidenceEyebrow')} title={t('evidenceTitle')} sub={t('evidenceSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              downloadCsv(filteredEvidence as unknown as ExportRow[], stampFilename('evidence-register', 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => { setEditing(null); setOpen(true); }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={Files}      label={t('evidenceKpiTotal')}    value={items.data.length} tone="primary" onClick={() => setTab('catalog')} active={tab === 'catalog'} />
        <Kpi Icon={Inbox}      label={t('evidenceKpiPending')}  value={items.data.filter(i => i.status === 'PENDING' || i.status === 'UNDER_REVIEW').length + requests.data.filter(r => r.status === 'PENDING' || r.status === 'IN_PROGRESS').length} tone="warning" onClick={() => setTab('requests')} active={tab === 'requests'} />
        <Kpi Icon={CalendarX}  label={t('evidenceKpiExpiring')} value={expiredItems.length} tone="danger" onClick={() => setTab('expired')} active={tab === 'expired'} />
        <Kpi Icon={BadgeCheck} label={t('evidenceKpiApproved')} value={`${pct}%`} tone="success" onClick={() => setTab('review')} active={tab === 'review'} />
      </div>

      <OverviewStrip
        storageKey="overview.evidence"
        processBarTitle="Evidence Management — Processes & Lifecycles"
        processBarSubtitle="ISO 19011 + IIA Std 2330 + ISACA CCM — every named process active in this domain."
        lifecycles={EVIDENCE_LIFECYCLES}
        postureTitle="Regulator Posture · Evidence Coverage"
        anchors={items.data.flatMap(i => EVIDENCE_ANCHORS_BY_ID[i.id] ?? [])}
        />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          {items.data.some(i => i.collectedAt ?? i.collectedDate ?? i.createdAt) && (
            <div style={{ marginBottom: 16 }}>
              <Card title={ar ? 'وتيرة جمع الأدلة — ٦ أشهر' : 'Evidence collection cadence — 6 months'} Icon={BarChart3}
                action={<Badge tone={expiredItems.length > 0 ? 'danger' : 'success'}>{expiredItems.length} {ar ? 'منتهية' : 'expired'}</Badge>}>
                <FreshnessCalendar
                  ar={ar}
                  weeks={26}
                  data={items.data
                    .map(i => i.collectedAt ?? i.collectedDate ?? i.createdAt)
                    .filter((d): d is string => !!d)
                    .map(d => ({ date: d, value: 1 }))} />
                <div style={{ fontSize: 12, color: 'var(--ink-3)', marginTop: 8, lineHeight: 1.6 }}>
                  {ar
                    ? 'كل خلية = الأدلة المُجمَّعة في ذلك اليوم. الفجوات الباهتة المتتالية تكشف توقّف الجمع («الفترة الباردة») — وهو السبب الأول لفشل التدقيق.'
                    : 'Each cell = evidence collected that day. A run of faint cells is a collection lapse (the "cold streak") — the #1 cause of audit failure.'}
                </div>
              </Card>
            </div>
          )}
          <div className="dash-grid">
            <Card title={t('evidenceTabCatalog')} Icon={BarChart3} action={<Badge tone="primary">By framework</Badge>}>
              {frameworks.length === 0
                ? <Progress label="No data yet" value={0} tone="primary" />
                : frameworks.slice(0, 6).map(fw => {
                    const cnt = items.data.filter(i => i.frameworkCode === fw).length;
                    const p2 = Math.round((cnt / items.data.length) * 100);
                    return <Progress key={fw} label={`${fw} · ${cnt} items`} value={p2} tone="primary" />;
                  })}
            </Card>
            <Card title={t('evCardTypeMix')} Icon={FileText}>
              {['POLICY','DOCUMENT','SCREENSHOT','AUDIT_REPORT','CONFIGURATION','CERTIFICATE'].map(type => {
                const cnt = items.data.filter(i => i.evidenceType === type).length;
                const p2 = Math.round((cnt / Math.max(1, items.data.length)) * 100);
                return <Progress key={type} label={`${type.replace(/_/g, ' ')} · ${cnt}`} value={p2} tone={type === 'POLICY' ? 'primary' : type === 'CERTIFICATE' ? 'success' : 'accent'} />;
              })}
            </Card>
          </div>
        </>
      )}
      {/* ── Frameworks tab — control/sub-control as prime key, framework as tab ── */}
      {tab === 'frameworks' && (
        <>
          {matrixLoading
            ? <LoadingRow />
            : matrix.length === 0
              ? <EmptyState Icon={Layers} title={t('evFrameworksEmpty')} sub="NCA ECC · NCA CCC · NCA DCC · ISO 27001 · PDPL" />
              : (
                <>
                  {/* Framework selector tabs (sub-tabs) */}
                  <div style={{ padding: '8px 0 16px' }}>
                    <PillTabs
                      aria-label={ar ? 'اختيار الإطار' : 'Select framework'}
                      active={activeFramework?.frameworkId ?? -1}
                      onChange={(id) => setActiveFrameworkId(id as number)}
                      options={matrix.map(fw => ({ id: fw.frameworkId, label: fw.code, count: fw.controlCount }))}
                    />
                  </div>

                  {activeFramework && (
                    <Card padded={false}>
                      <div style={{ padding: '12px 16px', borderBottom: '1px solid var(--border)' }}>
                        <div style={{ fontWeight: 700, fontSize: 14 }}>{activeFramework.nameEn}</div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                          {activeFramework.issuer} · v{activeFramework.version} · {activeFramework.controlCount} controls · {activeFramework.evidenceCount} evidence items
                        </div>
                      </div>
                      <div style={{ padding: 0 }}>
                        {activeFramework.controls.map(ctrl => {
                          const isExpanded = expandedControlIds.has(ctrl.controlId);
                          return (
                            <div key={ctrl.controlId} style={{ borderBottom: '1px solid var(--border)' }}>
                              <button onClick={() => toggleControl(ctrl.controlId)}
                                style={{
                                  width: '100%', padding: '12px 16px', display: 'flex',
                                  justifyContent: 'space-between', alignItems: 'center', gap: 12,
                                  background: 'var(--surface)', border: 'none', cursor: 'pointer', textAlign: 'left',
                                }}>
                                <div style={{ display: 'flex', gap: 10, alignItems: 'center', flex: 1, minWidth: 0 }}>
                                  {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                                  <span className="mono" style={{ fontWeight: 700, fontSize: 13 }}>{ctrl.controlCode}</span>
                                  <span style={{ fontWeight: 600, fontSize: 13, overflow: 'hidden', textOverflow: 'ellipsis' }}>{ctrl.nameEn}</span>
                                </div>
                                <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
                                  <Badge tone="info">{ctrl.evidenceCount} evidence</Badge>
                                  <Badge tone={FRESHNESS_TONE[ctrl.freshness]} dot>{ctrl.freshness}</Badge>
                                </div>
                              </button>
                              {isExpanded && (
                                <div style={{ padding: '0 16px 12px 38px', background: 'var(--surface-2)' }}>
                                  {ctrl.subControls.length === 0 && (
                                    <InlineEmpty compact>No sub-controls catalogued for this control.</InlineEmpty>
                                  )}
                                  {ctrl.subControls.map((sub: SubControlBlock) => (
                                    <div key={sub.subControlId} style={{
                                      display: 'grid', gridTemplateColumns: '120px 1fr auto auto auto',
                                      gap: 10, padding: '10px 12px', borderBottom: '1px dashed var(--border)',
                                      alignItems: 'center', fontSize: 12,
                                    }}>
                                      <span className="mono" style={{ fontWeight: 600 }}>{sub.subControlCode}</span>
                                      <div>
                                        <div style={{ fontWeight: 500 }}>{sub.nameEn}</div>
                                        {sub.evidenceGuidance && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{sub.evidenceGuidance.slice(0, 120)}</div>}
                                      </div>
                                      <Badge tone="info">{sub.evidenceCount} ev</Badge>
                                      <Badge tone={FRESHNESS_TONE[sub.freshness]} dot>{sub.freshness}</Badge>
                                      <span style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">
                                        {sub.freshestExpiry ? `expires ${sub.freshestExpiry}` : '—'}
                                      </span>
                                    </div>
                                  ))}
                                </div>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </Card>
                  )}
                </>
              )}
        </>
      )}

      {/* ── Connectors tab — connector library MVP ── */}
      {tab === 'connectors' && (
        <Card padded={false}>
          {connectorsLoading
            ? <LoadingRow />
            : connectors.length === 0
              ? <EmptyState Icon={Plug} title={t('evConnectorsEmpty')} sub="AD · Defender · AWS Config · NCA HEY" />
              : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
                  {connectors.map(c => (
                    <div key={c.id} style={{
                      display: 'grid', gridTemplateColumns: '1fr auto auto auto',
                      gap: 12, padding: '14px 16px', borderBottom: '1px solid var(--border)',
                      alignItems: 'center', background: 'var(--surface)',
                    }}>
                      <div>
                        <div style={{ fontWeight: 700, fontSize: 13 }}>
                          {c.nameEn} <span style={{ fontWeight: 400, color: 'var(--ink-3)' }}>· {c.vendor}</span>
                        </div>
                        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 2 }}>
                          <Badge tone="neutral">{c.connectorType.replace(/_/g, ' ')}</Badge>
                          {c.targetFrameworks && <span style={{ marginLeft: 8 }}>Frameworks: <span className="mono">{c.targetFrameworks}</span></span>}
                        </div>
                        {c.collectionPlanEn && (
                          <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 4, maxWidth: 720 }}>
                            {c.collectionPlanEn.slice(0, 240)}{c.collectionPlanEn.length > 240 ? '…' : ''}
                          </div>
                        )}
                        {c.lastRunAt && (
                          <div style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4 }} className="mono">
                            Last run: {c.lastRunAt.slice(0, 16).replace('T', ' ')} · status {c.lastRunStatus} · {c.evidenceCollectedCount} evidence collected
                          </div>
                        )}
                      </div>
                      <Badge tone={CONNECTOR_STATUS_TONE[c.status] ?? 'neutral'} dot>{c.status}</Badge>
                      <Button size="sm" variant="ghost" Icon={c.status === 'DISABLED' ? Power : Power}
                        onClick={() => toggleConnectorStatus(c)}>
                        {c.status === 'DISABLED' ? t('evidenceConnEnable') : t('evidenceConnDisable')}
                      </Button>
                      <Button size="sm" variant="primary" Icon={Zap}
                        onClick={() => runConnector(c)}>
                        {t('evidenceConnRun')}
                      </Button>
                    </div>
                  ))}
                </div>
              )}
        </Card>
      )}

      {tab === 'catalog' && (
        <Card padded={false}>
          {items.loading ? <LoadingRow /> : <DataTable columns={itemCols} rows={filteredEvidence} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'requests' && (
        <Card padded={false}>
          {requests.loading ? <LoadingRow /> : <DataTable columns={reqCols} rows={requests.data} empty={<EmptyState Icon={Send} title={t('evReqEmpty')} sub={t('evReqEmptySub')} />} />}
        </Card>
      )}
      {tab === 'review' && (
        <Card padded={false}>
          {reviews.loading ? <LoadingRow /> : <DataTable columns={reviewCols} rows={reviews.data} empty={<EmptyState Icon={ClipboardCheck} title={t('evReviewsEmpty')} sub={t('evReviewsEmptySub')} />} />}
        </Card>
      )}
      {tab === 'expired' && (
        <Card padded={false} title={t('evCardExpired')} Icon={CalendarX} action={<Badge tone="danger">{expiredItems.length}</Badge>}>
          {expiredItems.length === 0
            ? <EmptyState Icon={CalendarX} title={t('evExpiredEmpty')} sub={t('evExpiredEmptySub')} />
            : <DataTable columns={itemCols} rows={expiredItems} />}
        </Card>
      )}
      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={EVIDENCE_WORKFLOWS}
        title={t('evidenceTabWorkflows') ?? 'Workflows'}
        sub="Evidence reviews, request fulfilment and renewal of expiring artefacts."
        t={t} />}

      <FormModal<EvidenceItemRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.evidenceCode}` : t('newEvidenceTitle')}
        fields={FIELDS}
        initial={editing ? itemToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this evidence?"
        message={confirmDel ? `Evidence "${confirmDel.evidenceCode} · ${confirmDel.titleEn}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />

      {/* ── Version history modal (assessment §5.9 #1) ── */}
      <Modal
        open={!!versionsModal}
        title={versionsModal ? `${t('evidenceVersionsTitle')} · ${versionsModal.item.evidenceCode}` : ''}
        size="lg"
        onClose={() => setVersionsModal(null)}
        footer={
          versionsModal && (
            <Button variant="primary" Icon={History} onClick={() => snapshotVersion(versionsModal.item.id)}>
              {t('evidenceSnapshotNew')}
            </Button>
          )
        }
      >
        {versionsModal && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            <p style={{ fontSize: 12, color: 'var(--ink-3)', margin: 0 }}>{t('evidenceVersionsSub')}</p>
            {versionsModal.versions.length === 0
              ? <InlineEmpty>No prior versions on file.</InlineEmpty>
              : versionsModal.versions.map(v => (
                <div key={v.id} style={{
                  display: 'grid', gridTemplateColumns: '60px 1fr auto', gap: 12,
                  padding: '10px 12px', border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                  background: 'var(--surface-2)',
                }}>
                  <span className="mono" style={{ fontWeight: 700, fontSize: 14 }}>v{v.versionNo}</span>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{v.titleEn ?? '—'}</div>
                    {v.changeSummary && <div style={{ fontSize: 11, color: 'var(--ink-2)', marginTop: 2 }}>{v.changeSummary}</div>}
                    {v.evidenceHash && (
                      <div className="mono" style={{ fontSize: 10, color: 'var(--ink-3)', marginTop: 4, overflow: 'hidden', textOverflow: 'ellipsis' }}>
                        sha: {v.evidenceHash.slice(0, 24)}…
                      </div>
                    )}
                  </div>
                  <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                    {v.snapshottedAt ? v.snapshottedAt.slice(0, 16).replace('T', ' ') : '—'}
                  </span>
                </div>
              ))}
          </div>
        )}
      </Modal>
      <FilterModal open={filterOpen} fields={evidenceFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />

      {/* ── AI evidence-quality check result ── */}
      <Modal
        open={!!aiCheck}
        title={aiCheck ? `${ar ? 'تقييم جودة الدليل' : 'AI quality check'} · ${aiCheck.item.evidenceCode}` : ''}
        size="lg"
        onClose={() => setAiCheck(null)}
      >
        {aiCheck?.loading && (
          <InlineEmpty>{ar ? 'جارٍ تحليل جودة الدليل…' : 'Scoring evidence quality…'}</InlineEmpty>
        )}
        {aiCheck && !aiCheck.loading && aiCheck.result && (() => {
          let out: { qualityScore?: number; strengths?: string[]; issues?: string[]; verdict?: string } = {};
          try { out = JSON.parse(aiCheck.result.outputJson); } catch { /* keep empty */ }
          const score = out.qualityScore ?? Number(aiCheck.result.confidence ?? 0);
          const verdict = out.verdict ?? '';
          const vTone = verdict === 'ACCEPTABLE' ? 'success' : verdict === 'NEEDS_IMPROVEMENT' ? 'warning' : 'danger';
          const vLabel = verdict === 'ACCEPTABLE'
            ? (ar ? 'مقبول' : 'Acceptable')
            : verdict === 'NEEDS_IMPROVEMENT'
              ? (ar ? 'يحتاج تحسيناً' : 'Needs improvement')
              : (ar ? 'غير كافٍ' : 'Insufficient');
          const scoreTone = score >= 75 ? 'success' : score >= 50 ? 'warning' : 'danger';
          return (
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div style={{ display: 'flex', gap: 12, alignItems: 'center', flexWrap: 'wrap' }}>
                <Badge tone={scoreTone}>{ar ? 'الدرجة' : 'Score'}: {score}/100</Badge>
                <Badge tone={vTone} dot>{vLabel}</Badge>
              </div>
              {aiCheck.result.rationale && (
                <div style={{ fontSize: 13, color: 'var(--ink-2)', lineHeight: 1.6 }}>
                  {aiCheck.result.rationale}
                </div>
              )}
              {(out.strengths?.length ?? 0) > 0 && (
                <div>
                  <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <CheckCircle2 size={15} style={{ color: 'var(--success)' }} /> {ar ? 'نقاط القوة' : 'Strengths'}
                  </div>
                  <ul style={{ margin: 0, paddingInlineStart: 20, fontSize: 13, lineHeight: 1.8, color: 'var(--ink-1)' }}>
                    {out.strengths!.map((s, i) => <li key={i}>{s}</li>)}
                  </ul>
                </div>
              )}
              {(out.issues?.length ?? 0) > 0 && (
                <div>
                  <div style={{ fontWeight: 700, fontSize: 13, marginBottom: 6, display: 'flex', alignItems: 'center', gap: 6 }}>
                    <AlertTriangle size={15} style={{ color: 'var(--warning)' }} /> {ar ? 'الملاحظات' : 'Issues to address'}
                  </div>
                  <ul style={{ margin: 0, paddingInlineStart: 20, fontSize: 13, lineHeight: 1.8, color: 'var(--ink-1)' }}>
                    {out.issues!.map((s, i) => <li key={i}>{s}</li>)}
                  </ul>
                </div>
              )}
              <div style={{ fontSize: 11, color: 'var(--ink-3)', borderTop: '1px solid var(--border)', paddingTop: 10, lineHeight: 1.6 }}>
                {ar
                  ? 'حُفظ هذا التقييم كمسودّة في قائمة «اقتراحات الذكاء الاصطناعي» لمراجعته بشرياً قبل أي إجراء. التقييم قائم على قواعد ولا يغيّر سجل الدليل من تلقاء نفسه.'
                  : 'Saved as a draft in the AI Suggestions review queue for human sign-off. This is a rule-based score and never changes the evidence record on its own.'}
              </div>
            </div>
          );
        })()}
      </Modal>

      {/* Hidden picker for the binary file-upload row action (multipart). */}
      <input ref={fileInputRef} type="file" hidden onChange={onFileChosen}
        disabled={uploading}
        accept=".pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.png,.jpg,.jpeg,.gif,.txt,.csv,.json,.xml,.zip" />
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <InlineEmpty>{t('riskLoading')}</InlineEmpty>;
}
