import { useState } from 'react';
import { ClipboardCheck, BookOpenCheck, FileCheck2, Award, Filter, Download, Plus, BarChart3, Building2, AlertTriangle } from 'lucide-react';
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
import { SEVERITY_TONE, severityLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { complianceApi, ApiError, type ComplianceControlDto, type ComplianceControlRequest, type ComplianceAssessmentDto, type ComplianceFindingDto } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { COMPLIANCE_CONTROLS } from './mockCompliance';

type TabId = 'overview' | 'controls' | 'internal' | 'external' | 'findings' | 'scorecard';

const FRAMEWORK_OPTIONS = [
  { value: 'ISO_27001', label: 'ISO 27001' }, { value: 'ISO_22301', label: 'ISO 22301' },
  { value: 'NCA_ECC', label: 'NCA ECC' }, { value: 'PDPL', label: 'Saudi PDPL' },
  { value: 'SAMA_CSF', label: 'SAMA CSF' }, { value: 'ISO_27701', label: 'ISO 27701' },
];
const STATUS_OPTIONS = [
  { value: 'IMPLEMENTED', label: 'Implemented' }, { value: 'PARTIAL', label: 'Partial' },
  { value: 'PLANNED', label: 'Planned' }, { value: 'NOT_APPLICABLE', label: 'Not Applicable' },
];
// FIELDS now built inside the component via t()
const EMPTY: ComplianceControlRequest = {
  code: '', nameEn: '', nameAr: '', framework: 'ISO_27001',
  domain: '', maturityLevel: 1, status: 'PLANNED', owner: '', description: '',
};
const STATUS_TONE_MAP: Record<string, 'success' | 'warning' | 'info' | 'neutral' | 'danger'> = {
  IMPLEMENTED: 'success', PARTIAL: 'warning', PLANNED: 'info',
  NOT_APPLICABLE: 'neutral', NOT_IMPLEMENTED: 'danger', UNDER_REVIEW: 'info',
  COMPLETED: 'success', OPEN: 'danger', CLOSED: 'success',
};

function ctrlToReq(c: ComplianceControlDto): ComplianceControlRequest {
  return {
    code: c.code, nameEn: c.nameEn, nameAr: c.nameAr,
    framework: c.framework, domain: c.domain,
    maturityLevel: c.maturityLevel, status: c.status,
    owner: c.owner, description: c.description,
  };
}

export function CompliancePage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ComplianceControlDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ComplianceControlDto | null>(null);
  const [busy, setBusy] = useState(false);

  const controls    = useApiList<ComplianceControlDto>(()    => complianceApi.controls(0, 100),    COMPLIANCE_CONTROLS as unknown as ComplianceControlDto[]);
  const assessments = useApiList<ComplianceAssessmentDto>(() => complianceApi.assessments(0, 100), []);
  const findings    = useApiList<ComplianceFindingDto>(()    => complianceApi.findings(0, 100),    []);

  const FIELDS: FieldDef[] = [
    { kind: 'text', key: 'code',           label: t('fldCode') + ' *', required: true, maxLength: 50, span: 4 },
    { kind: 'select', key: 'framework',    label: t('fldFramework') + ' *', options: FRAMEWORK_OPTIONS, required: true, span: 8 },
    { kind: 'text', key: 'nameEn',         label: t('fldNameEnLbl') + ' *', required: true, maxLength: 200, span: 6 },
    { kind: 'text', key: 'nameAr',         label: t('fldNameArLbl'), maxLength: 200, span: 6 },
    { kind: 'text', key: 'domain',         label: t('fldDomain'), span: 4 },
    { kind: 'select', key: 'status',       label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 4 },
    { kind: 'number', key: 'maturityLevel', label: t('cyberFldMaturityScore'), min: 1, max: 5, span: 4 },
    { kind: 'text', key: 'owner',          label: t('fldOwnerLbl'), span: 12 },
    { kind: 'textarea', key: 'description', label: t('fldDescriptionLbl'), rows: 2, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',  label: t('tabOverview') },
    { id: 'controls',  label: t('complianceTabControls') },
    { id: 'internal',  label: t('complianceTabInternal') },
    { id: 'external',  label: t('complianceTabExternal') },
    { id: 'findings',  label: t('complianceTabFindings') },
    { id: 'scorecard', label: t('complianceTabScorecard') },
  ];

  const ctrlCols: Column<ComplianceControlDto>[] = [
    { header: t('colFramework'),  cell: r => <Badge tone="primary">{(r.framework ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colControl'),    cell: r => <span style={{ fontWeight: 600 }}>{r.code} · {r.nameEn}</span> },
    { header: t('colMaturity'),   cell: r => <span className="mono">L{r.maturityLevel ?? 0}</span> },
    { header: t('colOwner'),      cell: r => r.owner ?? '—' },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'neutral'} dot>{(r.status ?? '').replace(/_/g, ' ')}</Badge> },
    { header: '',                 align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  const assessCols: Column<ComplianceAssessmentDto>[] = [
    { header: t('colFramework'), cell: r => <Badge tone="primary">{(r.framework ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colPeriod'),    cell: r => <span style={{ fontWeight: 600 }}>{r.period}</span> },
    { header: t('colAuditor'),   cell: r => r.auditor ?? '—' },
    { header: t('colScore'),     cell: r => <ScorePillCompliance score={r.score ?? 0} /> },
    { header: t('colDate'),      cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'neutral'} dot>{r.status}</Badge> },
  ];

  const findingCols: Column<ComplianceFindingDto>[] = [
    { header: t('colTitle'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}</span> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity] ?? 'neutral'} dot>{severityLabel(t, r.severity)}</Badge> },
    { header: t('colOwner'),    cell: r => r.owner ?? '—' },
    { header: t('colCapa'),     cell: r => <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>{(r.capa ?? '—').slice(0, 60)}</span> },
    { header: t('colDue'),      cell: r => r.dueDate ?? '—', mono: true },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'neutral'} dot>{r.status}</Badge> },
  ];

  const internalAssess = assessments.data.filter(a => !/external|bsi|kpmg|deloitte|pwc|ey/i.test(a.auditor ?? ''));
  const externalAssess = assessments.data.filter(a => /external|bsi|kpmg|deloitte|pwc|ey/i.test(a.auditor ?? ''));

  async function onSubmit(values: ComplianceControlRequest) {
    try {
      if (editing) await complianceApi.updateControl(editing.id, values);
      else await complianceApi.createControl(values);
      toast.success(editing ? 'Control updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); controls.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await complianceApi.deleteControl(confirmDel.id); toast.success('Control deleted'); controls.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const frameworkScore = (fw: string) => {
    const cs = controls.data.filter(c => c.framework === fw);
    if (cs.length === 0) return 0;
    const total = cs.reduce((s, c) => s + (c.maturityLevel ?? 0), 0);
    return Math.round((total / (cs.length * 5)) * 100);
  };

  const isDemo = controls.isDemo;

  return (
    <Shell title={t('modCompliance')} sub="ISO 27001 · NCA · SDAIA · SAMA">
      <PageHeader
        eyebrow={t('complianceEyebrow')} title={t('complianceTitle')} sub={t('complianceSub')}
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
        <Kpi Icon={BookOpenCheck}  label={t('complianceKpiFrameworks')} value={new Set(controls.data.map(c => c.framework)).size} tone="primary" />
        <Kpi Icon={ClipboardCheck} label={t('complianceKpiControls')}   value={controls.data.length} tone="success" />
        <Kpi Icon={FileCheck2}     label={t('complianceKpiEvidence')}   value="1,283" tone="info" />
        <Kpi Icon={Award}          label={t('complianceKpiScore')}      value="87%" trend={{ dir: 'up', value: '+4%' }} tone="success" />
      </div>
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <div className="dash-grid">
          <Card title={t('complianceKpiFrameworks')} Icon={BarChart3} action={<Badge tone="primary">Power BI</Badge>}>
            {['ISO_27001','NCA_ECC','PDPL','ISO_22301','SAMA_CSF','ISO_27701'].map(fw => {
              const v = frameworkScore(fw);
              return <Progress key={fw} label={fw.replace(/_/g, ' ')} value={v} tone={v >= 80 ? 'success' : v >= 60 ? 'primary' : v >= 40 ? 'accent' : 'danger'} />;
            })}
          </Card>
          <Card title={t('complianceTabFindings')} Icon={AlertTriangle}>
            <Progress label={`Critical · ${findings.data.filter(f => f.severity === 'CRITICAL' && f.status === 'OPEN').length} open`} value={findings.data.filter(f => f.severity === 'CRITICAL').length * 10} tone="danger" />
            <Progress label={`High · ${findings.data.filter(f => f.severity === 'HIGH' && f.status === 'OPEN').length} open`}     value={findings.data.filter(f => f.severity === 'HIGH').length * 10}     tone="accent" />
            <Progress label={`Medium · ${findings.data.filter(f => f.severity === 'MEDIUM' && f.status === 'OPEN').length} open`} value={findings.data.filter(f => f.severity === 'MEDIUM').length * 10} tone="primary" />
            <Progress label={`Closed · ${findings.data.filter(f => f.status === 'CLOSED').length}`} value={findings.data.filter(f => f.status === 'CLOSED').length * 10} tone="success" />
          </Card>
        </div>
      )}
      {tab === 'controls' && (
        <Card padded={false}>
          {controls.loading ? <LoadingRow /> : <DataTable columns={ctrlCols} rows={controls.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'internal' && (
        <Card padded={false}>
          {assessments.loading ? <LoadingRow /> : <DataTable columns={assessCols} rows={internalAssess} empty={<EmptyState Icon={ClipboardCheck} title="No internal audits" sub="Internal audits track first-line and second-line control testing." />} />}
        </Card>
      )}
      {tab === 'external' && (
        <Card padded={false}>
          {assessments.loading ? <LoadingRow /> : <DataTable columns={assessCols} rows={externalAssess} empty={<EmptyState Icon={Building2} title="No external audits" sub="External audits feed regulator-facing attestations and certifications." />} />}
        </Card>
      )}
      {tab === 'findings' && (
        <Card padded={false}>
          {findings.loading ? <LoadingRow /> : <DataTable columns={findingCols} rows={findings.data} empty={<EmptyState Icon={AlertTriangle} title="No open findings" sub="Findings link audit observations to remediation owners with due dates." />} />}
        </Card>
      )}
      {tab === 'scorecard' && (
        <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
          {['ISO_27001','NCA_ECC','PDPL','ISO_22301','SAMA_CSF','ISO_27701'].map(fw => {
            const v = frameworkScore(fw);
            const cs = controls.data.filter(c => c.framework === fw).length;
            return (
              <Card key={fw} title={fw.replace(/_/g, ' ')} Icon={Award} action={<Badge tone="primary">{cs} controls</Badge>}>
                <div style={{ fontFamily: 'var(--font-mono)', fontSize: 38, fontWeight: 800, color: v >= 80 ? 'var(--success-fg)' : v >= 60 ? 'var(--primary-700)' : v >= 40 ? 'var(--accent-700)' : 'var(--danger-fg)', letterSpacing: '-0.02em' }}>{v}%</div>
                <div style={{ height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden', margin: '8px 0 6px' }}>
                  <div style={{ width: `${v}%`, height: '100%', background: v >= 80 ? 'var(--success)' : v >= 60 ? 'var(--primary)' : v >= 40 ? 'var(--accent)' : 'var(--danger)' }} />
                </div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                  {controls.data.filter(c => c.framework === fw && c.status === 'IMPLEMENTED').length} implemented ·
                  {' '}{controls.data.filter(c => c.framework === fw && c.status === 'PARTIAL').length} partial
                </div>
              </Card>
            );
          })}
        </div>
      )}

      <FormModal<ComplianceControlRequest>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.code}` : t('newComplianceCtrlTitle')}
        fields={FIELDS}
        initial={editing ? ctrlToReq(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this control?"
        message={confirmDel ? `Control "${confirmDel.code} · ${confirmDel.nameEn}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />
    </Shell>
  );
}

function ScorePillCompliance({ score }: { score: number }) {
  const v = Math.round(score);
  const bg = v >= 80 ? 'var(--success)' : v >= 60 ? 'var(--primary)' : v >= 40 ? 'var(--accent)' : 'var(--danger)';
  return <span className="score-pill" style={{ background: bg, color: '#fff' }}>{v}</span>;
}
function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
