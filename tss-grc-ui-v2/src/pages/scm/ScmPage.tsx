import { useState } from 'react';
import { Truck, AlertTriangle, ListChecks, Star, Filter, Download, Plus, BarChart3, Workflow, FileSignature, ClipboardCheck } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Stepper } from '../../components/ui/Stepper';
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { scmApi, ApiError, type ScmVendorDto, type ScmVendorRequest, type ScmOnboardingDto, type ScmAssessmentDto, type ScmContractDto } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { SUPPLIERS } from './mockScm';

type TabId = 'overview' | 'vendors' | 'onboarding' | 'assessments' | 'contracts';

const ONBOARDING_STAGES = [
  { id: 'REGISTRATION',             label: 'Registration' },
  { id: 'CRITICALITY_CLASSIFICATION', label: 'Classification' },
  { id: 'SECURITY_REVIEW',          label: 'Security' },
  { id: 'OPERATIONAL_REVIEW',       label: 'Operations' },
  { id: 'LEGAL_REVIEW',             label: 'Legal' },
  { id: 'APPROVAL',                 label: 'Approval' },
];
const CATEGORY_OPTIONS = [
  { value: 'IT_SERVICES', label: 'IT Services' }, { value: 'HARDWARE', label: 'Hardware' },
  { value: 'SAAS', label: 'SaaS' }, { value: 'LOGISTICS', label: 'Logistics' },
  { value: 'PROFESSIONAL_SERVICES', label: 'Professional Services' }, { value: 'MANUFACTURING', label: 'Manufacturing' },
];
const CRITICALITY_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' }, { value: 'HIGH', label: 'High' },
  { value: 'MEDIUM', label: 'Medium' }, { value: 'LOW', label: 'Low' },
];
const STATUS_OPTIONS = [
  { value: 'ACTIVE', label: 'Active' }, { value: 'PENDING', label: 'Pending' },
  { value: 'UNDER_REVIEW', label: 'Under Review' }, { value: 'CLOSED', label: 'Closed' },
];
const RISK_OPTIONS = [
  { value: 'LOW', label: 'Low' }, { value: 'MEDIUM', label: 'Medium' },
  { value: 'HIGH', label: 'High' }, { value: 'CRITICAL', label: 'Critical' },
];
// FIELDS now built inside the component via t()
const EMPTY: ScmVendorRequest = {
  nameEn: '', nameAr: '', category: 'IT_SERVICES', criticality: 'MEDIUM',
  country: '', serviceType: '', riskLevel: 'LOW', status: 'PENDING',
  contactName: '', contactEmail: '',
};
const RISK_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

function vendorToReq(v: ScmVendorDto): ScmVendorRequest {
  return {
    nameEn: v.nameEn, nameAr: v.nameAr,
    category: v.category, criticality: v.criticality,
    country: v.country, serviceType: v.serviceType,
    riskLevel: v.riskLevel, status: v.status,
    contactName: v.contactName, contactEmail: v.contactEmail,
  };
}

export function ScmPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ScmVendorDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ScmVendorDto | null>(null);
  const [busy, setBusy] = useState(false);

  const vendors     = useApiList<ScmVendorDto>(()     => scmApi.vendors(0, 100),     SUPPLIERS as unknown as ScmVendorDto[]);
  const onboarding  = useApiList<ScmOnboardingDto>(() => scmApi.onboarding(0, 100),  []);
  const assessments = useApiList<ScmAssessmentDto>(() => scmApi.assessments(0, 100), []);
  const contracts   = useApiList<ScmContractDto>(()   => scmApi.contracts(0, 100),   []);

  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'nameEn',        label: t('fldNameEnLbl') + ' *', required: true, maxLength: 200, span: 6 },
    { kind: 'text', key: 'nameAr',        label: t('fldNameArLbl'), maxLength: 200, span: 6 },
    { kind: 'select', key: 'category',    label: t('fldCategory'), options: CATEGORY_OPTIONS, span: 6 },
    { kind: 'select', key: 'criticality', label: t('fldCriticality'), options: CRITICALITY_OPTIONS, span: 6 },
    { kind: 'text', key: 'country',       label: t('fldCountry'), span: 4 },
    { kind: 'text', key: 'serviceType',   label: t('scmFldServiceType'), span: 4 },
    { kind: 'select', key: 'riskLevel',   label: t('scmFldRiskLevel'), options: RISK_OPTIONS, span: 4 },
    { kind: 'select', key: 'status',      label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 12 },
    { kind: 'text', key: 'contactName',   label: t('fldContactName'), span: 6 },
    { kind: 'text', key: 'contactEmail',  label: t('fldEmailContact'), span: 6 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('tabOverview') },
    { id: 'vendors',     label: t('scmTabVendors') },
    { id: 'onboarding',  label: t('scmTabOnboarding') },
    { id: 'assessments', label: t('scmTabAssessments') },
    { id: 'contracts',   label: t('scmTabContracts') },
  ];

  const vendorCols: Column<ScmVendorDto>[] = [
    { header: t('scmSupplierCol'), cell: r => <span style={{ fontWeight: 600 }}>{r.nameEn}</span> },
    { header: t('scmCountryCol'),  cell: r => r.country ?? '—' },
    { header: t('scmCategoryCol'), cell: r => <Badge tone="info">{(r.category ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colCriticality'), cell: r => <Badge tone={RISK_TONE[r.criticality ?? ''] ?? 'neutral'}>{r.criticality ?? '—'}</Badge> },
    { header: t('colRisk'),        cell: r => <Badge tone={RISK_TONE[r.riskLevel ?? ''] ?? 'neutral'}>{r.riskLevel ?? '—'}</Badge> },
    { header: t('colStatus'),      cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '',                  align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  const onboardCols: Column<ScmOnboardingDto>[] = [
    { header: t('colVendor'),    cell: r => <span style={{ fontWeight: 600 }}>{r.vendorNameEn ?? `#${r.vendorId}`}</span> },
    { header: t('colStage'),     cell: r => <Badge tone="primary">{(r.currentStage ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colProgress'),  cell: r => {
      const completed = (r.completedStages ?? '').split(',').filter(Boolean).length;
      return <span className="mono">{completed}/{ONBOARDING_STAGES.length}</span>;
    } },
    { header: t('colAssignee'),  cell: r => r.assignee ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
  ];

  const assessCols: Column<ScmAssessmentDto>[] = [
    { header: t('colVendor'),    cell: r => <span style={{ fontWeight: 600 }}>{r.vendorNameEn ?? `#${r.vendorId}`}</span> },
    { header: t('colType'),      cell: r => <Badge tone="info">{(r.assessmentType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colDate'),      cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colScore'),     cell: r => <ScorePillScm score={(r.overallScore ?? 0) * 10} /> },
    { header: t('colRisk'),      cell: r => <Badge tone={RISK_TONE[r.riskRating ?? ''] ?? 'neutral'}>{r.riskRating ?? '—'}</Badge> },
    { header: t('colNext'),      cell: r => r.nextAssessmentDate ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
  ];

  const contractCols: Column<ScmContractDto>[] = [
    { header: t('colNumberLabel'),  cell: r => r.contractNumber, mono: true },
    { header: t('colTitle'),        cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}</div><div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.vendorNameEn ?? '—'}</div></div> },
    { header: t('colContractType'), cell: r => <Badge tone="info">{r.contractType ?? '—'}</Badge> },
    { header: t('colPeriod'),       cell: r => <span className="mono" style={{ fontSize: 12 }}>{r.startDate ?? '—'} → {r.endDate ?? '—'}</span> },
    { header: t('colRenewal'),      cell: r => r.autoRenewal ? <Badge tone="primary">{t('badgeAuto')}</Badge> : <Badge tone="neutral">{t('badgeManual')}</Badge> },
    { header: t('colValue'),        cell: r => r.contractValue?.toLocaleString() ?? '—', mono: true },
    { header: t('colStatus'),       cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
  ];

  const high = vendors.data.filter(v => v.criticality === 'CRITICAL' || v.criticality === 'HIGH').length;
  const pending = vendors.data.filter(v => v.status === 'PENDING' || v.status === 'UNDER_REVIEW').length;
  const avgScore = assessments.data.length
    ? (assessments.data.reduce((s, a) => s + (a.overallScore ?? 0), 0) / assessments.data.length).toFixed(1)
    : '8.2';

  async function onSubmit(values: ScmVendorRequest) {
    try {
      if (editing) await scmApi.updateVendor(editing.id, values);
      else await scmApi.createVendor(values);
      toast.success(editing ? 'Vendor updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); vendors.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await scmApi.deleteVendor(confirmDel.id); toast.success('Vendor deleted'); vendors.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const focusOnboarding = onboarding.data.find(o => o.status !== 'COMPLETED') ?? onboarding.data[0];
  const isDemo = vendors.isDemo;

  return (
    <Shell title="Supply Chain" sub={t('scmEyebrow')}>
      <PageHeader
        eyebrow={t('scmEyebrow')} title={t('scmTitle')} sub={t('scmSub')}
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
        <Kpi Icon={Truck}         label={t('scmKpiSuppliers')} value={vendors.data.length} tone="primary" />
        <Kpi Icon={AlertTriangle} label={t('scmKpiHighRisk')}  value={high} tone="danger" />
        <Kpi Icon={ListChecks}    label={t('scmKpiPending')}   value={pending} tone="warning" />
        <Kpi Icon={Star}          label={t('scmKpiAvgScore')}  value={String(avgScore)} tone="success" />
      </div>
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          <div className="dash-grid">
            <Card title="Suppliers by Country" Icon={BarChart3}>
              <BarChart3D items={countryDistribution(vendors.data)} />
            </Card>
            <Card title="Risk Distribution" Icon={AlertTriangle}>
              <BarChart3D items={[
                { label: 'Low',      value: vendors.data.filter(v => v.riskLevel === 'LOW').length,      color: 'var(--success)', shade: '#23522F' },
                { label: 'Medium',   value: vendors.data.filter(v => v.riskLevel === 'MEDIUM').length,   color: 'var(--cat-2)',   shade: '#7A5612' },
                { label: 'High',     value: vendors.data.filter(v => v.riskLevel === 'HIGH').length,     color: 'var(--cat-6)',   shade: '#5C3220' },
                { label: 'Critical', value: vendors.data.filter(v => v.riskLevel === 'CRITICAL').length, color: 'var(--danger)',  shade: '#7E2A25' },
              ]} />
            </Card>
          </div>
          {focusOnboarding && (
            <div style={{ marginTop: 16 }}>
              <Card title="Onboarding Progress" Icon={Workflow} action={<Badge tone="info">{focusOnboarding.vendorNameEn ?? '—'}</Badge>}>
                <Stepper steps={ONBOARDING_STAGES} active={focusOnboarding.currentStage ?? 'REGISTRATION'} />
              </Card>
            </div>
          )}
        </>
      )}
      {tab === 'vendors' && (
        <Card padded={false}>
          {vendors.loading ? <LoadingRow /> : <DataTable columns={vendorCols} rows={vendors.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'onboarding' && (
        <Card padded={false}>
          {onboarding.loading ? <LoadingRow /> : <DataTable columns={onboardCols} rows={onboarding.data} empty={<EmptyState Icon={Workflow} title="No onboarding in progress" sub="New vendors enter the 6-stage onboarding workflow." />} />}
        </Card>
      )}
      {tab === 'assessments' && (
        <Card padded={false}>
          {assessments.loading ? <LoadingRow /> : <DataTable columns={assessCols} rows={assessments.data} empty={<EmptyState Icon={ClipboardCheck} title="No assessments" sub="Periodic and onboarding assessments roll up to vendor risk." />} />}
        </Card>
      )}
      {tab === 'contracts' && (
        <Card padded={false}>
          {contracts.loading ? <LoadingRow /> : <DataTable columns={contractCols} rows={contracts.data} empty={<EmptyState Icon={FileSignature} title="No contracts" sub="Contracts capture term, value, SLA and renewal posture." />} />}
        </Card>
      )}

      <FormModal<ScmVendorRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.nameEn}` : t('newSupplierTitle')}
        fields={FIELDS}
        initial={editing ? vendorToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this supplier?"
        message={confirmDel ? `Supplier "${confirmDel.nameEn}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />
    </Shell>
  );
}

function ScorePillScm({ score }: { score: number }) {
  const v = Math.round(score);
  const bg = v >= 80 ? 'var(--success)' : v >= 60 ? 'var(--primary)' : v >= 40 ? 'var(--accent)' : 'var(--danger)';
  return <span className="score-pill" style={{ background: bg, color: '#fff' }}>{v}</span>;
}
function countryDistribution(vendors: ScmVendorDto[]) {
  const counts = new Map<string, number>();
  for (const v of vendors) {
    const k = (v.country ?? 'Other').slice(0, 16);
    counts.set(k, (counts.get(k) ?? 0) + 1);
  }
  const palette = ['var(--cat-1)','var(--cat-2)','var(--cat-3)','var(--cat-4)','var(--cat-5)','var(--cat-6)','var(--cat-7)','var(--cat-8)'];
  const shadeMap = ['#062E2E','#7A5612','#324754','#522F45','#23522F','#5C3220','#3F2A55','#5C5121'];
  return Array.from(counts.entries())
    .sort((a, b) => b[1] - a[1])
    .slice(0, 6)
    .map(([label, value], i) => ({ label, value, color: palette[i % palette.length], shade: shadeMap[i % shadeMap.length] }));
}
function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
