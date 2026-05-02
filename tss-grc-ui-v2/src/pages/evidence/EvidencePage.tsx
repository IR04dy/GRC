import { useState } from 'react';
import { Files, Inbox, CalendarX, BadgeCheck, Filter, Download, Plus, BarChart3, FileText, Send, ClipboardCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
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
import { evidenceApi, ApiError, type EvidenceItemDto, type EvidenceItemRequest, type EvidenceRequestDto, type EvidenceReviewDto } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { EVIDENCE } from './mockEvidence';

type TabId = 'overview' | 'catalog' | 'requests' | 'review' | 'expired';

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

export function EvidencePage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<EvidenceItemDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<EvidenceItemDto | null>(null);
  const [busy, setBusy] = useState(false);

  const items    = useApiList<EvidenceItemDto>(()    => evidenceApi.items(0, 100),    EVIDENCE as unknown as EvidenceItemDto[]);
  const requests = useApiList<EvidenceRequestDto>(() => evidenceApi.requests(0, 100), []);
  const reviews  = useApiList<EvidenceReviewDto>(()  => evidenceApi.reviews(0, 100),  []);

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
    { id: 'overview', label: t('tabOverview') },
    { id: 'catalog',  label: t('evidenceTabCatalog') },
    { id: 'requests', label: t('evidenceTabRequests') },
    { id: 'review',   label: t('evidenceTabReview') },
    { id: 'expired',  label: t('evidenceTabExpired') },
  ];

  const itemCols: Column<EvidenceItemDto>[] = [
    { header: t('colCode'),      cell: r => r.evidenceCode, mono: true },
    { header: t('colTitle'),     cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colType'),      cell: r => <Badge tone="neutral">{(r.evidenceType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colFramework'), cell: r => <Badge tone="primary">{r.frameworkCode ?? '—'}</Badge> },
    { header: t('colExpiry'),    cell: r => <span className="mono">{r.expiryDate ?? '—'}</span> },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '',                align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  const reqCols: Column<EvidenceRequestDto>[] = [
    { header: t('colCode'),       cell: r => r.requestCode, mono: true },
    { header: t('colTitle'),      cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colSubControl'), cell: r => r.subControlCode ?? '—', mono: true },
    { header: t('colFramework'),  cell: r => <Badge tone="primary">{r.frameworkCode ?? '—'}</Badge> },
    { header: t('colOwner'),      cell: r => r.ownerName ?? '—' },
    { header: t('colDue'),        cell: r => r.dueDate ?? '—', mono: true },
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
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => { setEditing(null); setOpen(true); }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={Files}      label={t('evidenceKpiTotal')}    value={items.data.length} tone="primary" />
        <Kpi Icon={Inbox}      label={t('evidenceKpiPending')}  value={items.data.filter(i => i.status === 'PENDING' || i.status === 'UNDER_REVIEW').length + requests.data.filter(r => r.status === 'PENDING' || r.status === 'IN_PROGRESS').length} tone="warning" />
        <Kpi Icon={CalendarX}  label={t('evidenceKpiExpiring')} value={expiredItems.length} tone="danger" />
        <Kpi Icon={BadgeCheck} label={t('evidenceKpiApproved')} value={`${pct}%`} tone="success" />
      </div>
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
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
          <Card title="Type Mix" Icon={FileText}>
            {['POLICY','DOCUMENT','SCREENSHOT','AUDIT_REPORT','CONFIGURATION','CERTIFICATE'].map(type => {
              const cnt = items.data.filter(i => i.evidenceType === type).length;
              const p2 = Math.round((cnt / Math.max(1, items.data.length)) * 100);
              return <Progress key={type} label={`${type.replace(/_/g, ' ')} · ${cnt}`} value={p2} tone={type === 'POLICY' ? 'primary' : type === 'CERTIFICATE' ? 'success' : 'accent'} />;
            })}
          </Card>
        </div>
      )}
      {tab === 'catalog' && (
        <Card padded={false}>
          {items.loading ? <LoadingRow /> : <DataTable columns={itemCols} rows={items.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'requests' && (
        <Card padded={false}>
          {requests.loading ? <LoadingRow /> : <DataTable columns={reqCols} rows={requests.data} empty={<EmptyState Icon={Send} title="No evidence requests" sub="Auditors raise requests here; owners upload artifacts in response." />} />}
        </Card>
      )}
      {tab === 'review' && (
        <Card padded={false}>
          {reviews.loading ? <LoadingRow /> : <DataTable columns={reviewCols} rows={reviews.data} empty={<EmptyState Icon={ClipboardCheck} title="No reviews yet" sub="Each evidence item gets at least one reviewer sign-off." />} />}
        </Card>
      )}
      {tab === 'expired' && (
        <Card padded={false} title="Expired Evidence" Icon={CalendarX} action={<Badge tone="danger">{expiredItems.length} expired</Badge>}>
          {expiredItems.length === 0
            ? <EmptyState Icon={CalendarX} title="Nothing expired" sub="All evidence is currently within its validity window." />
            : <DataTable columns={itemCols} rows={expiredItems} />}
        </Card>
      )}

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
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
