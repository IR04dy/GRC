import { useState } from 'react';
import { Workflow, Building2, AlertTriangle, CalendarClock, Filter, Download, Plus, BarChart3, FileText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { tprmApi, ApiError, type TprmVendorDto, type TprmVendorRequest } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { VENDORS } from './mockTprm';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { TPRM_WORKFLOWS } from '../../lib/mockWorkflows';
import { TPRM_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { TPRM_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';

type TabId = 'overview' | 'vendors' | 'assessments' | 'contracts' | 'workflows' | 'comms';

const RISK_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' },
];
const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' }, { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'PENDING', label: 'Pending' }, { value: 'CLOSED', label: 'Closed' },
];
// FIELDS now built inside the component via t()
const EMPTY: TprmVendorRequest = {
  vendorName: '', vendorNameAr: '', vendorType: '', country: '',
  riskRating: 'MEDIUM', status: 'ACTIVE', contractEndDate: '', description: '',
};
const RISK_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

function vendorToReq(v: TprmVendorDto): TprmVendorRequest {
  return {
    vendorName: v.vendorName, vendorNameAr: v.vendorNameAr,
    vendorType: v.vendorType, country: v.country,
    riskRating: v.riskRating, status: v.status,
    contractEndDate: v.contractEndDate, description: v.description,
  };
}

export function TprmPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TprmVendorDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<TprmVendorDto | null>(null);
  const [busy, setBusy] = useState(false);

  const vendors = useApiList<TprmVendorDto>(() => tprmApi.vendors(0, 100), VENDORS);

  const FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'vendorName',      label: `${t('tprmFldVendorName')} (${t('fldTitleEnLbl').match(/\(([^)]+)\)/)?.[1] ?? 'EN'}) *`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',     key: 'vendorNameAr',    label: `${t('tprmFldVendorName')} (${t('fldTitleArLbl').match(/\(([^)]+)\)/)?.[1] ?? 'AR'})`, maxLength: 200, span: 6 },
    { kind: 'text',     key: 'vendorType',      label: t('tprmFldVendorType'), span: 4 },
    { kind: 'text',     key: 'country',         label: t('fldCountry'), span: 4 },
    { kind: 'date',     key: 'contractEndDate', label: t('tprmFldContractEnd'), span: 4 },
    { kind: 'select',   key: 'riskRating',      label: t('tprmFldRiskRating') + ' *', options: RISK_OPTIONS, required: true, span: 6 },
    { kind: 'select',   key: 'status',          label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 6 },
    { kind: 'textarea', key: 'description',     label: t('fldDescriptionLbl'), rows: 2, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('tabOverview') },
    { id: 'vendors',     label: t('tprmTabVendors') },
    { id: 'assessments', label: t('tprmTabAssessments') },
    { id: 'contracts',   label: t('tprmTabContracts') },
    { id: 'workflows',   label: t('tprmTabWorkflows') ?? 'Workflows' },
    { id: 'comms',       label: t('detailTabComms') },
  ];

  const cols: Column<TprmVendorDto>[] = [
    { header: t('colVendor'),     cell: r => <span style={{ fontWeight: 600 }}>{r.vendorName}</span> },
    { header: t('colType'),       cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.vendorType ?? '—'}</span> },
    { header: t('colCountry'),    cell: r => r.country ?? '—' },
    { header: t('colRisk'),       cell: r => <Badge tone={RISK_TONE[r.riskRating] ?? 'neutral'}>{r.riskRating}</Badge> },
    { header: t('tprmReviewCol'), cell: r => r.contractEndDate ?? '—', mono: true },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '',                 align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  async function onSubmit(values: TprmVendorRequest) {
    try {
      if (editing) await tprmApi.updateVendor(editing.id, values);
      else await tprmApi.createVendor(values);
      toast.success(editing ? 'Vendor updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); vendors.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await tprmApi.deleteVendor(confirmDel.id); toast.success('Vendor deleted'); vendors.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const sampleAssessments = vendors.data.slice(0, 6).map((v, i) => ({
    id: v.id, vendor: v.vendorName,
    type: i % 2 === 0 ? t('tprmAssessTypeAnnual') : t('tprmAssessTypeOnboarding'),
    score: Math.max(20, 92 - (i * 8) - (v.riskRating === 'CRITICAL' ? 30 : 0)),
    next: new Date(Date.now() + (60 + i * 30) * 86400_000).toISOString().slice(0, 10),
  }));
  const expiringContracts = vendors.data
    .filter(v => v.contractEndDate)
    .map(v => ({ id: v.id, vendor: v.vendorName, end: v.contractEndDate!, risk: v.riskRating, days: Math.round((new Date(v.contractEndDate!).getTime() - Date.now()) / 86400_000) }))
    .sort((a, b) => a.days - b.days)
    .slice(0, 8);

  const critical = vendors.data.filter(v => v.riskRating === 'CRITICAL').length;
  const isDemo = vendors.isDemo;

  return (
    <Shell title={t('modThirdParty')} sub="ISO 27036">
      <PageHeader
        eyebrow={t('tprmEyebrow')} title={t('tprmTitle')} sub={t('tprmSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter} onClick={() => toast.info(t('actionFilterHint'))}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              downloadCsv(vendors.data as unknown as ExportRow[], stampFilename('tprm-vendors', 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => { setEditing(null); setOpen(true); }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={Building2}     label={t('tprmKpiVendors')}    value={vendors.data.length} tone="primary" />
        <Kpi Icon={Workflow}      label={t('tprmKpiCritical')}   value={critical} tone="danger" />
        <Kpi Icon={AlertTriangle} label={t('tprmKpiOpenRisks')}  value={vendors.data.filter(v => v.status === 'UNDER_REVIEW').length} tone="warning" />
        <Kpi Icon={CalendarClock} label={t('tprmKpiReviews')}    value={expiringContracts.filter(c => c.days <= 90).length} tone="info" />
      </div>

      <OverviewStrip
        storageKey="overview.tprm"
        processBarTitle={t('tprmProcessBarTitle')}
        processBarSubtitle={t('tprmProcessBarSub')}
        lifecycles={TPRM_LIFECYCLES}
        postureTitle={t('tprmPostureTitle')}
        anchors={TPRM_ANCHORS}
        lang="en" />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('tprmCardByRisk')} Icon={BarChart3}>
            <BarChart3D items={[
              { label: t('sevCritical'), value: vendors.data.filter(v => v.riskRating === 'CRITICAL').length, color: 'var(--danger)', shade: '#7E2A25' },
              { label: t('sevHigh'),     value: vendors.data.filter(v => v.riskRating === 'HIGH').length,     color: 'var(--cat-2)',  shade: '#7A5612' },
              { label: t('sevMedium'),   value: vendors.data.filter(v => v.riskRating === 'MEDIUM').length,   color: 'var(--primary)',shade: '#062E2E' },
              { label: t('sevLow'),      value: vendors.data.filter(v => v.riskRating === 'LOW').length,      color: 'var(--cat-3)',  shade: '#324754' },
            ]} />
          </Card>
          <Card title={t('tprmCardByStatus')} Icon={AlertTriangle}>
            <BarChart3D items={[
              { label: t('statActive'),  value: vendors.data.filter(v => v.status === 'ACTIVE').length,       color: 'var(--success)', shade: '#23522F' },
              { label: t('statReview'),  value: vendors.data.filter(v => v.status === 'UNDER_REVIEW').length, color: 'var(--cat-2)',   shade: '#7A5612' },
              { label: t('statPending'), value: vendors.data.filter(v => v.status === 'PENDING').length,      color: 'var(--cat-6)',   shade: '#5C3220' },
              { label: t('statClosed'),  value: vendors.data.filter(v => v.status === 'CLOSED').length,       color: 'var(--ink-4)',   shade: '#3F4A50' },
            ]} />
          </Card>
        </div>
      )}
      {tab === 'vendors' && (
        <Card padded={false}>
          {vendors.loading ? <LoadingRow /> : <DataTable columns={cols} rows={vendors.data} rowHref={r => `/vendors/${r.id}`} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'assessments' && (
        <Card padded={false}>
          {sampleAssessments.length === 0
            ? <EmptyState Icon={FileText} title={t('tprmAssessmentsEmpty')} sub={t('tprmAssessmentsEmptySub')} />
            : <DataTable
                columns={[
                  { header: t('colVendor'),         cell: (r: { vendor: string }) => <span style={{ fontWeight: 600 }}>{r.vendor}</span> },
                  { header: t('tprmAssessColType'), cell: (r: { type: string }) => <Badge tone="info">{r.type}</Badge> },
                  { header: t('tprmAssessColScore'),cell: (r: { score: number }) => <ScorePill score={r.score} /> },
                  { header: t('tprmAssessColNext'), cell: (r: { next: string }) => r.next, mono: true },
                ]}
                rows={sampleAssessments} />}
        </Card>
      )}
      {tab === 'contracts' && (
        <div className="dash-grid">
          <Card title={t('tprmCardContractsExpiring')} Icon={CalendarClock} action={<Badge tone="warning">{t('tprmContractsBadgeWindow')}</Badge>}>
            {expiringContracts.length === 0
              ? <EmptyState Icon={CalendarClock} title={t('tprmContractsEmpty')} sub={t('tprmContractsEmptySub')} />
              : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                  {expiringContracts.map(c => {
                    const dueTone = c.days <= 30 ? 'danger' : c.days <= 90 ? 'warning' : 'info';
                    return (
                      <div key={c.id} style={{ display: 'grid', gridTemplateColumns: '1fr auto auto', gap: 12, alignItems: 'center', padding: '8px 12px', borderRadius: 'var(--r-md)', background: 'var(--surface-2)' }}>
                        <div>
                          <div style={{ fontWeight: 600, fontSize: 13 }}>{c.vendor}</div>
                          <div className="mono" style={{ fontSize: 11, color: 'var(--ink-3)' }}>{t('tprmContractsEnds')} {c.end}</div>
                        </div>
                        <Badge tone={RISK_TONE[c.risk]}>{c.risk}</Badge>
                        <Badge tone={dueTone}>{c.days < 0 ? `${-c.days}${t('tprmContractsDaysAgo')}` : `${c.days}${t('tprmContractsDays')}`}</Badge>
                      </div>
                    );
                  })}
                </div>}
          </Card>
          <Card title={t('tprmCardContractHealth')} Icon={FileText}>
            <div style={{ height: 8 }} />
          </Card>
        </div>
      )}
      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={TPRM_WORKFLOWS}
        title={t('tprmTabWorkflows') ?? 'Workflows'}
        sub={t('tprmWorkflowsSub')}
        t={t} />}
      {tab === 'comms' && <CommsPanel module="TPRM" />}

      <FormModal<TprmVendorRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.vendorName}` : t('newVendorTitle')}
        fields={FIELDS}
        initial={editing ? vendorToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title={t('tprmConfirmDeleteTitle')}
        message={confirmDel ? `"${confirmDel.vendorName}" — ${t('tprmConfirmDeleteSuffix')}` : ''}
        confirmLabel={t('tprmConfirmDeleteCta')}
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />
    </Shell>
  );
}

function ScorePill({ score }: { score: number }) {
  const v = Math.round(score);
  const bg = v >= 80 ? 'var(--success)' : v >= 60 ? 'var(--primary)' : v >= 40 ? 'var(--accent)' : 'var(--danger)';
  return <span className="score-pill" style={{ background: bg, color: '#fff' }}>{v}</span>;
}
function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
