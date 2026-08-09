import { useEffect, useMemo, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Workflow, Building2, AlertTriangle, CalendarClock, Filter, Download, Plus, BarChart3, FileText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Modal } from '../../components/ui/Modal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import { tprmApi, ApiError, type TprmVendorDto, type TprmVendorRequest } from '../../lib/api';
import { useToast } from '../../lib/toast';
import { VENDORS } from './mockTprm';
import { ModuleEngineRequestsPanel } from '../../components/workflows/ModuleEngineRequestsPanel';
import { TPRM_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { TPRM_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';
import { usePromoted, isUnsyncedId } from '../../lib/promotedRecords';
import { toVendorDto, usePromotedProvenance } from '../../lib/promotedAdapters';
import { PromotedBadge } from '../../components/intake/PromotedBadge';
import { TprmOnboardingPipeline, TprmReassessmentDashboard } from './TprmOnboardingPanel';
import { TprmConsolidatedOverview } from '../../components/tprm/TprmConsolidatedOverview';
import { RegulatoryAlignmentTab } from '../../components/regulatory/RegulatoryAlignmentTab';
import {
  listQtTemplates, listQtQuestions, transitionQtTemplate,
  createQtTemplate, updateQtTemplate,
  createQtQuestion, updateQtQuestion, deleteQtQuestion,
  listVendorAssessments, assignTemplateToAssessment, listAssessmentResponses,
  upsertResponse, completeAssessment, reviewAssessment,
  qtStatusTone, qtStatusLabel, riskRatingTone, workflowStatusTone,
  type QuestionnaireTemplate, type QuestionnaireQuestion, type QtStatus,
  type QtCategory, type AnswerType,
  type QtVendorAssessment, type QuestionnaireResponse,
} from '../../lib/tprmQuestionnaire';
import { useDialog } from '../../lib/dialog';

type TabId = 'overview' | 'vendors' | 'onboarding' | 'reassessment' | 'assessments' | 'questionnaires' | 'contracts' | 'workflows' | 'comms' | 'alignment';

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
  const { t, lang } = useI18n();
  const toast = useToast();
  const navigate = useNavigate();
  const [tab, setTab] = useState<TabId>('overview');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<TprmVendorDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<TprmVendorDto | null>(null);
  const [busy, setBusy] = useState(false);

  const vendors = useApiList<TprmVendorDto>(() => tprmApi.vendors(0, 100), VENDORS);
  // DD-71: include intake-promoted vendors in the visible register.
  const promotedVendors = usePromoted('VENDOR').map(toVendorDto);
  const allVendors = useMemo(() => [...promotedVendors, ...vendors.data], [promotedVendors, vendors.data]);
  const provenance = usePromotedProvenance('VENDOR');

  // ── DD-47: filter modal ──────────────────────────────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  const filteredVendors = useMemo(() => allVendors.filter(v => {
    if (advFilters.riskRating && v.riskRating !== advFilters.riskRating) return false;
    if (advFilters.status     && v.status !== advFilters.status) return false;
    if (advFilters.vendorType && (v.vendorType ?? '') !== advFilters.vendorType) return false;
    if (advFilters.country    && (v.country ?? '') !== advFilters.country) return false;
    if (advFilters.contractEndAfter  && (v.contractEndDate ?? '') < advFilters.contractEndAfter) return false;
    if (advFilters.contractEndBefore && (v.contractEndDate ?? '') > advFilters.contractEndBefore) return false;
    return true;
  }), [allVendors, advFilters]);
  const tprmFilterFields: FilterField[] = [
    { key: 'riskRating', label: t('filterFieldRisk'),       kind: 'select',
      options: [{value:'LOW',label:'LOW'},{value:'MEDIUM',label:'MEDIUM'},{value:'HIGH',label:'HIGH'},{value:'CRITICAL',label:'CRITICAL'}] },
    { key: 'status',     label: t('filterFieldStatus'),     kind: 'select', options: distinctValues(vendors.data, 'status') },
    { key: 'vendorType', label: t('filterFieldVendorType'), kind: 'select', options: distinctValues(vendors.data.filter(v => v.vendorType), 'vendorType') },
    { key: 'country',    label: t('filterFieldCountry'),    kind: 'select', options: distinctValues(vendors.data.filter(v => v.country), 'country') },
    { key: 'contractEndAfter',  label: 'Contract end after',  kind: 'date' },
    { key: 'contractEndBefore', label: 'Contract end before', kind: 'date' },
  ];

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
    { id: 'overview',     label: t('tabOverview') },
    { id: 'vendors',      label: t('tprmTabVendors') },
    { id: 'onboarding',   label: t('tprmTabOnboarding') },
    { id: 'reassessment', label: t('tprmTabReassessment') },
    { id: 'assessments',  label: t('tprmTabAssessments') },
    { id: 'questionnaires', label: lang === 'ar' ? 'الاستبيانات' : 'Questionnaires' },
    { id: 'contracts',    label: t('tprmTabContracts') },
    { id: 'workflows',    label: t('tprmTabWorkflows') ?? 'Workflows' },
    { id: 'comms',        label: t('detailTabComms') },
    { id: 'alignment',    label: lang === 'ar' ? 'التوافق التنظيمي' : 'Regulatory alignment' },
  ];

  const cols: Column<TprmVendorDto>[] = [
    { header: t('colVendor'),     cell: r => <span style={{ fontWeight: 600 }}>{r.vendorName}{provenance.has(r.id) && <PromotedBadge sourceReferenceCode={provenance.get(r.id)!} />}</span> },
    { header: t('colType'),       cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.vendorType ?? '—'}</span> },
    { header: t('colCountry'),    cell: r => r.country ?? '—' },
    { header: t('colRisk'),       cell: r => <Badge tone={RISK_TONE[r.riskRating] ?? 'neutral'}>{r.riskRating}</Badge> },
    { header: t('tprmReviewCol'), cell: r => r.contractEndDate ?? '—', mono: true },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '',                 align: 'end', width: '90px',
      // F2-171: promoted-intake vendors are merged in above and exist only in this browser.
      cell: r => <RowActions disabled={isDemo || isUnsyncedId(r.id)} onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
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

  const critical = allVendors.filter(v => v.riskRating === 'CRITICAL').length;
  const isDemo = vendors.isDemo;

  // Sprint 4 §2.7 — live TPRM dashboard
  const { pulse } = useLiveRefresh({
    kinds: ['changed.vendor', 'changed.vendor_assessment', 'changed.supplier_incident'],
    onRefresh: () => { vendors.reload(); },
    waitMs: 600,
    enabled: !isDemo,
  });

  return (
    <Shell title={t('modThirdParty')} sub="ISO 27036">
      <PageHeader
        eyebrow={t('tprmEyebrow')} title={t('tprmTitle')} sub={t('tprmSub')}
        actions={
          <>
            <RaiseRequestButton sourceModule="TPRM" />
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            {!isDemo && <LivePulse pulse={pulse} />}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              downloadCsv(filteredVendors as unknown as ExportRow[], stampFilename('tprm-vendors', 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            {/* DD-76: route new vendor onboarding through the Services Center
                so it lands in the onboarding pipeline (DD-74) at INTAKE stage. */}
            <Button variant="primary"   Icon={Plus} onClick={() => navigate('/intake/vendor_onboarding')}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={Building2}     label={t('tprmKpiVendors')}    value={vendors.data.length} tone="primary" onClick={() => setTab('vendors')} />
        <Kpi Icon={Workflow}      label={t('tprmKpiCritical')}   value={critical} tone="danger" onClick={() => setTab('vendors')} />
        <Kpi Icon={AlertTriangle} label={t('tprmKpiOpenRisks')}  value={vendors.data.filter(v => v.status === 'UNDER_REVIEW').length} tone="warning" onClick={() => setTab('vendors')} />
        <Kpi Icon={CalendarClock} label={t('tprmKpiReviews')}    value={expiringContracts.filter(c => c.days <= 90).length} tone="info" onClick={() => setTab('contracts')} />
      </div>

      <OverviewStrip
        storageKey="overview.tprm"
        processBarTitle={t('tprmProcessBarTitle')}
        processBarSubtitle={t('tprmProcessBarSub')}
        lifecycles={TPRM_LIFECYCLES}
        postureTitle={t('tprmPostureTitle')}
        anchors={TPRM_ANCHORS}
        />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          <TprmConsolidatedOverview />
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
        </>
      )}
      {tab === 'vendors' && (
        <Card padded={false}>
          {vendors.loading ? <LoadingRow /> : <DataTable columns={cols} rows={filteredVendors} rowHref={r => `/vendors/${r.id}`} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'onboarding' && (
        <TprmOnboardingPipeline vendors={allVendors} />
      )}
      {tab === 'reassessment' && (
        <TprmReassessmentDashboard vendors={allVendors} />
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
      {tab === 'questionnaires' && <QuestionnairesTab vendors={allVendors} />}
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
      {tab === 'workflows' && <ModuleEngineRequestsPanel sourceModule="TPRM" />}
      {tab === 'comms' && <CommsPanel module="TPRM" />}
      {tab === 'alignment' && <RegulatoryAlignmentTab domain="TPRM" families={['Third-Party', 'SAMA', 'NCA Cyber', 'Privacy']} />}

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
      <FilterModal open={filterOpen} fields={tprmFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />
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

// ─── Questionnaires tab — V81 ────────────────────────────────────────

// L2 — Questionnaire builder: form-value shapes + option catalogs.
const QT_CATEGORY_OPTIONS: { value: QtCategory; label: string }[] = [
  { value: 'CYBER', label: 'Cyber' }, { value: 'PRIVACY', label: 'Privacy' },
  { value: 'FINANCIAL', label: 'Financial' }, { value: 'OPERATIONAL', label: 'Operational' },
  { value: 'COMPLIANCE', label: 'Compliance' }, { value: 'GENERAL', label: 'General' },
];
const QT_ANSWER_TYPE_OPTIONS: { value: AnswerType; label: string }[] = [
  { value: 'SCALE_1_5', label: 'Scale 1–5' }, { value: 'BOOLEAN', label: 'Yes / No' },
  { value: 'SELECT', label: 'Select (options)' }, { value: 'TEXT', label: 'Free text' },
];

interface TemplateFormValues {
  templateCode: string; version: string;
  nameEn: string; nameAr: string;
  descriptionEn: string; descriptionAr: string;
  category: QtCategory; applicableTo: string;
  thresholdLowMax: number; thresholdMediumMax: number; thresholdHighMax: number;
  notes: string;
}
interface QuestionFormValues {
  sectionCode: string; sectionNameEn: string; sectionNameAr: string;
  questionCode: string; questionEn: string; questionAr: string;
  answerType: AnswerType; optionsJson: string;
  weight: number; sequenceOrder: number;
  mandatory: string;    // 'true' | 'false' (FormModal select returns strings)
  invertScore: string;  // 'true' | 'false'
  guidanceEn: string; guidanceAr: string;
}

function QuestionnairesTab({ vendors }: { vendors: TprmVendorDto[] }) {
  const { lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const isAr = lang === 'ar';

  const [templates, setTemplates] = useState<QuestionnaireTemplate[]>([]);
  const [statusFilter, setStatusFilter] = useState<QtStatus | ''>('');
  const [selected, setSelected] = useState<QuestionnaireTemplate | null>(null);
  const [questions, setQuestions] = useState<QuestionnaireQuestion[]>([]);
  const [loading, setLoading] = useState(true);

  // Response-filling state
  const [vendorAssessments, setVendorAssessments] = useState<QtVendorAssessment[]>([]);
  const [openFill, setOpenFill] = useState<QtVendorAssessment | null>(null);
  const [fillQuestions, setFillQuestions] = useState<QuestionnaireQuestion[]>([]);
  const [fillResponses, setFillResponses] = useState<Record<number, QuestionnaireResponse>>({});
  const [fillBusy, setFillBusy] = useState(false);

  // Builder (L2) — template + question authoring (DRAFT templates only)
  const [tplFormOpen, setTplFormOpen] = useState(false);
  const [tplEditing, setTplEditing] = useState<QuestionnaireTemplate | null>(null);
  const [qFormOpen, setQFormOpen] = useState(false);
  const [qEditing, setQEditing] = useState<QuestionnaireQuestion | null>(null);

  async function reloadVendorAssessments() {
    try { setVendorAssessments(await listVendorAssessments()); }
    catch { /* keep prior state */ }
  }

  async function reload() {
    setLoading(true);
    try { setTemplates(await listQtTemplates(statusFilter || undefined)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load templates'); setTemplates([]); }
    finally { setLoading(false); }
  }
  useEffect(() => { void reload(); }, [statusFilter]);
  useEffect(() => { void reloadVendorAssessments(); }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  // ─── Response-filling helpers ────────────────────────────────────

  async function onOpenFill(va: QtVendorAssessment) {
    if (!va.templateId) {
      toast.danger(isAr ? 'لا يوجد نموذج مُعيَّن — عيّن نموذجاً أولاً' : 'No template assigned — assign one first');
      return;
    }
    setOpenFill(va);
    setFillBusy(true);
    // Per-call .catch() — show the question form even if no responses
    // have been saved yet (404/empty), and vice versa.
    const [qs, rs] = await Promise.all([
      listQtQuestions(va.templateId).catch(() => [] as QuestionnaireQuestion[]),
      listAssessmentResponses(va.id).catch(() => [] as QuestionnaireResponse[]),
    ]);
    setFillQuestions(qs);
    const map: Record<number, QuestionnaireResponse> = {};
    for (const r of rs) map[r.questionId] = r;
    setFillResponses(map);
    setFillBusy(false);
  }

  async function onAssignTemplate(va: QtVendorAssessment) {
    const activeTemplates = templates.filter(t => t.status === 'ACTIVE');
    if (activeTemplates.length === 0) {
      toast.danger(isAr ? 'لا يوجد نموذج نشط' : 'No active templates available');
      return;
    }
    const choices = activeTemplates.map(t => `${t.id}:${t.templateCode} v${t.version}`).join('\n');
    const ans = await dialog.prompt({
      title: isAr ? 'تعيين نموذج' : 'Assign template',
      label: isAr ? `أدخل رقم النموذج. النماذج النشطة:\n${choices}` : `Enter template id. Active templates:\n${choices}`,
      type: 'number', required: true,
    });
    if (!ans) return;
    try {
      await assignTemplateToAssessment(va.id, Number(ans));
      toast.success(isAr ? 'تم تعيين النموذج' : 'Template assigned');
      await reloadVendorAssessments();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Assign failed');
    }
  }

  async function onAnswer(q: QuestionnaireQuestion, value: string) {
    if (!openFill) return;
    setFillBusy(true);
    try {
      const r = await upsertResponse(openFill.id, {
        questionId: q.id!,
        answerValue: value,
        notApplicable: false,
      });
      setFillResponses(prev => ({ ...prev, [q.id!]: r }));
      // Reload the assessment so the live score/rating updates in the table behind.
      await reloadVendorAssessments();
      const refreshed = vendorAssessments.find(v => v.id === openFill.id);
      if (refreshed) setOpenFill(refreshed);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    } finally { setFillBusy(false); }
  }

  async function onMarkNA(q: QuestionnaireQuestion) {
    if (!openFill) return;
    const reason = await dialog.prompt({
      title: isAr ? 'تبرير عدم الانطباق' : 'NA justification',
      label: isAr ? 'لماذا هذا السؤال غير منطبق؟' : 'Why is this question not applicable?',
      type: 'textarea', required: true,
    });
    if (!reason) return;
    setFillBusy(true);
    try {
      const r = await upsertResponse(openFill.id, {
        questionId: q.id!,
        notApplicable: true,
        naJustification: reason,
      });
      setFillResponses(prev => ({ ...prev, [q.id!]: r }));
      await reloadVendorAssessments();
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'NA mark failed');
    } finally { setFillBusy(false); }
  }

  async function onComplete() {
    if (!openFill) return;
    try {
      await completeAssessment(openFill.id);
      toast.success(isAr ? 'تم — جاهز للمراجعة' : 'Completed — ready for review');
      await reloadVendorAssessments();
      setOpenFill(null);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Complete failed');
    }
  }

  async function onReview() {
    if (!openFill) return;
    try {
      await reviewAssessment(openFill.id);
      toast.success(isAr ? 'تمت المراجعة' : 'Reviewed');
      await reloadVendorAssessments();
      setOpenFill(null);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Review failed');
    }
  }

  async function loadQuestions(t: QuestionnaireTemplate) {
    setSelected(t);
    try { setQuestions(await listQtQuestions(t.id!)); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load questions'); setQuestions([]); }
  }

  async function onActivate(t: QuestionnaireTemplate) {
    const ok = await dialog.confirm({
      title: isAr ? 'تفعيل النموذج' : 'Activate template',
      body: isAr
        ? `سيتم تجميد ${t.templateCode} v${t.version} — لا يمكن تعديل الأسئلة بعد التفعيل.`
        : `${t.templateCode} v${t.version} will be frozen — questions can't be edited after activation.`,
      submitLabel: isAr ? 'تفعيل' : 'Activate',
    });
    if (!ok) return;
    try {
      await transitionQtTemplate(t.id!, 'ACTIVE');
      toast.success(isAr ? 'تم التفعيل' : 'Activated');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Activate failed'); }
  }

  async function onArchive(t: QuestionnaireTemplate) {
    try {
      await transitionQtTemplate(t.id!, 'ARCHIVED');
      toast.success(isAr ? 'تمت الأرشفة' : 'Archived');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Archive failed'); }
  }

  // ─── Builder (L2) — template + question authoring ────────────────

  function openNewTemplate() { setTplEditing(null); setTplFormOpen(true); }
  function openEditTemplate(t: QuestionnaireTemplate) { setTplEditing(t); setTplFormOpen(true); }
  function openNewQuestion() { setQEditing(null); setQFormOpen(true); }
  function openEditQuestion(q: QuestionnaireQuestion) { setQEditing(q); setQFormOpen(true); }

  async function saveTemplate(v: TemplateFormValues): Promise<void> {
    const lo = Number(v.thresholdLowMax), me = Number(v.thresholdMediumMax), hi = Number(v.thresholdHighMax);
    if (!(lo < me && me < hi && hi <= 100)) {
      toast.danger(isAr
        ? 'العتبات يجب أن تتصاعد: منخفض < متوسط < مرتفع ≤ 100'
        : 'Thresholds must ascend: Low < Medium < High ≤ 100');
      return;  // keep modal open
    }
    const common: Partial<QuestionnaireTemplate> = {
      nameEn: v.nameEn.trim(), nameAr: v.nameAr.trim() || undefined,
      descriptionEn: v.descriptionEn.trim() || undefined,
      descriptionAr: v.descriptionAr.trim() || undefined,
      category: v.category, applicableTo: v.applicableTo.trim() || undefined,
      thresholdLowMax: lo, thresholdMediumMax: me, thresholdHighMax: hi,
      notes: v.notes.trim() || undefined,
    };
    try {
      if (tplEditing?.id) {
        const updated = await updateQtTemplate(tplEditing.id, common);
        toast.success(isAr ? 'تم تحديث النموذج' : 'Template updated');
        setTplFormOpen(false); setTplEditing(null);
        await reload();
        setSelected(prev => (prev?.id === updated.id ? updated : prev));
      } else {
        const created = await createQtTemplate({
          templateCode: v.templateCode.trim(),
          version: v.version.trim() || '1.0',
          status: 'DRAFT', ...common,
        });
        toast.success(isAr ? 'تم إنشاء النموذج' : 'Template created');
        setTplFormOpen(false); setTplEditing(null);
        await reload();
        if (created?.id) await loadQuestions(created);   // auto-select so questions can be added at once
      }
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  async function saveQuestion(v: QuestionFormValues): Promise<void> {
    if (!selected?.id) return;
    if (!v.questionEn.trim()) {
      toast.danger(isAr ? 'نص السؤال (EN) مطلوب' : 'Question text (EN) is required');
      return;  // keep modal open
    }
    const optionsJson = v.answerType === 'SELECT' ? (v.optionsJson.trim() || undefined) : undefined;
    if (optionsJson) {
      try { JSON.parse(optionsJson); }
      catch { toast.danger(isAr ? 'صيغة خيارات JSON غير صالحة' : 'SELECT options JSON is invalid'); return; }
    }
    const body: Partial<QuestionnaireQuestion> = {
      sectionCode: v.sectionCode.trim(),
      sectionNameEn: v.sectionNameEn.trim() || undefined,
      sectionNameAr: v.sectionNameAr.trim() || undefined,
      questionCode: v.questionCode.trim(),
      questionEn: v.questionEn.trim(),
      questionAr: v.questionAr.trim() || undefined,
      answerType: v.answerType, optionsJson,
      weight: Number(v.weight), sequenceOrder: Number(v.sequenceOrder),
      mandatory: v.mandatory === 'true', invertScore: v.invertScore === 'true',
      guidanceEn: v.guidanceEn.trim() || undefined,
      guidanceAr: v.guidanceAr.trim() || undefined,
    };
    try {
      if (qEditing?.id) {
        await updateQtQuestion(qEditing.id, body);
        toast.success(isAr ? 'تم تحديث السؤال' : 'Question updated');
      } else {
        await createQtQuestion(selected.id, body);
        toast.success(isAr ? 'تمت إضافة السؤال' : 'Question added');
      }
      setQFormOpen(false); setQEditing(null);
      await loadQuestions(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Save failed');
    }
  }

  async function onDeleteQuestion(q: QuestionnaireQuestion) {
    const ok = await dialog.confirm({
      title: isAr ? 'حذف السؤال' : 'Delete question',
      body: isAr ? `حذف السؤال ${q.questionCode}؟` : `Delete question ${q.questionCode}?`,
      submitLabel: isAr ? 'حذف' : 'Delete',
    });
    if (!ok) return;
    try {
      await deleteQtQuestion(q.id!);
      toast.success(isAr ? 'تم الحذف' : 'Deleted');
      if (selected) await loadQuestions(selected);
    } catch (e) {
      toast.danger(e instanceof ApiError ? e.message : 'Delete failed');
    }
  }

  const yesNoOptions = [
    { value: 'true', label: isAr ? 'نعم' : 'Yes' },
    { value: 'false', label: isAr ? 'لا' : 'No' },
  ];

  const tplInitial: TemplateFormValues = useMemo(() => tplEditing ? {
    templateCode: tplEditing.templateCode, version: tplEditing.version,
    nameEn: tplEditing.nameEn, nameAr: tplEditing.nameAr ?? '',
    descriptionEn: tplEditing.descriptionEn ?? '', descriptionAr: tplEditing.descriptionAr ?? '',
    category: tplEditing.category, applicableTo: tplEditing.applicableTo ?? '',
    thresholdLowMax: tplEditing.thresholdLowMax, thresholdMediumMax: tplEditing.thresholdMediumMax,
    thresholdHighMax: tplEditing.thresholdHighMax, notes: tplEditing.notes ?? '',
  } : {
    templateCode: '', version: '1.0', nameEn: '', nameAr: '',
    descriptionEn: '', descriptionAr: '', category: 'GENERAL', applicableTo: '',
    thresholdLowMax: 30, thresholdMediumMax: 60, thresholdHighMax: 80, notes: '',
  }, [tplEditing]);

  const qInitial: QuestionFormValues = useMemo(() => qEditing ? {
    sectionCode: qEditing.sectionCode, sectionNameEn: qEditing.sectionNameEn ?? '', sectionNameAr: qEditing.sectionNameAr ?? '',
    questionCode: qEditing.questionCode, questionEn: qEditing.questionEn, questionAr: qEditing.questionAr ?? '',
    answerType: qEditing.answerType, optionsJson: qEditing.optionsJson ?? '',
    weight: qEditing.weight, sequenceOrder: qEditing.sequenceOrder,
    mandatory: qEditing.mandatory ? 'true' : 'false', invertScore: qEditing.invertScore ? 'true' : 'false',
    guidanceEn: qEditing.guidanceEn ?? '', guidanceAr: qEditing.guidanceAr ?? '',
  } : {
    sectionCode: '', sectionNameEn: '', sectionNameAr: '',
    questionCode: '', questionEn: '', questionAr: '',
    answerType: 'SCALE_1_5', optionsJson: '',
    weight: 1, sequenceOrder: (questions.length + 1) * 10,
    mandatory: 'true', invertScore: 'false', guidanceEn: '', guidanceAr: '',
  }, [qEditing, questions.length]);

  const tplFields: FieldDef[] = [
    ...(tplEditing ? [] : [
      { kind: 'text', key: 'templateCode', label: isAr ? 'كود النموذج *' : 'Template code *', required: true, maxLength: 40, span: 6 },
      { kind: 'text', key: 'version', label: isAr ? 'الإصدار *' : 'Version *', required: true, maxLength: 20, span: 6 },
    ] as FieldDef[]),
    { kind: 'text', key: 'nameEn', label: isAr ? 'الاسم (EN) *' : 'Name (EN) *', required: true, span: 6 },
    { kind: 'text', key: 'nameAr', label: isAr ? 'الاسم (AR)' : 'Name (AR)', span: 6 },
    { kind: 'select', key: 'category', label: isAr ? 'الفئة *' : 'Category *', required: true, options: QT_CATEGORY_OPTIONS, span: 6 },
    { kind: 'text', key: 'applicableTo', label: isAr ? 'ينطبق على (أنواع الموردين)' : 'Applicable to (vendor types)', placeholder: 'CRITICAL,HIGH', span: 6 },
    { kind: 'textarea', key: 'descriptionEn', label: isAr ? 'الوصف (EN)' : 'Description (EN)', rows: 2, span: 12 },
    { kind: 'textarea', key: 'descriptionAr', label: isAr ? 'الوصف (AR)' : 'Description (AR)', rows: 2, span: 12 },
    { kind: 'number', key: 'thresholdLowMax', label: isAr ? 'عتبة منخفض ≤' : 'Low risk ≤', min: 1, max: 99, required: true, span: 4 },
    { kind: 'number', key: 'thresholdMediumMax', label: isAr ? 'عتبة متوسط ≤' : 'Medium risk ≤', min: 1, max: 99, required: true, span: 4 },
    { kind: 'number', key: 'thresholdHighMax', label: isAr ? 'عتبة مرتفع ≤' : 'High risk ≤', min: 1, max: 100, required: true, span: 4 },
    { kind: 'textarea', key: 'notes', label: isAr ? 'ملاحظات' : 'Notes', rows: 2, span: 12 },
  ];

  const qFields: FieldDef[] = [
    { kind: 'text', key: 'sectionCode', label: isAr ? 'كود القسم *' : 'Section code *', required: true, maxLength: 40, span: 4 },
    { kind: 'text', key: 'questionCode', label: isAr ? 'كود السؤال *' : 'Question code *', required: true, maxLength: 40, span: 4 },
    { kind: 'number', key: 'sequenceOrder', label: isAr ? 'الترتيب' : 'Order', min: 0, span: 4 },
    { kind: 'text', key: 'sectionNameEn', label: isAr ? 'اسم القسم (EN)' : 'Section name (EN)', span: 6 },
    { kind: 'text', key: 'sectionNameAr', label: isAr ? 'اسم القسم (AR)' : 'Section name (AR)', span: 6 },
    { kind: 'textarea', key: 'questionEn', label: isAr ? 'السؤال (EN) *' : 'Question (EN) *', rows: 2, span: 12 },
    { kind: 'textarea', key: 'questionAr', label: isAr ? 'السؤال (AR)' : 'Question (AR)', rows: 2, span: 12 },
    { kind: 'select', key: 'answerType', label: isAr ? 'نوع الإجابة *' : 'Answer type *', required: true, options: QT_ANSWER_TYPE_OPTIONS, span: 6 },
    { kind: 'number', key: 'weight', label: isAr ? 'الوزن *' : 'Weight *', min: 0, required: true, span: 6 },
    { kind: 'select', key: 'mandatory', label: isAr ? 'إلزامي' : 'Mandatory', options: yesNoOptions, span: 6 },
    { kind: 'select', key: 'invertScore', label: isAr ? 'مقلوب (الأقل = أفضل)' : 'Inverted (lower = better)', options: yesNoOptions, span: 6 },
    { kind: 'textarea', key: 'optionsJson', label: isAr ? 'خيارات SELECT — JSON: [{"value","label"}]' : 'SELECT options — JSON: [{"value","label"}]', rows: 2, span: 12 },
    { kind: 'textarea', key: 'guidanceEn', label: isAr ? 'إرشادات (EN)' : 'Guidance (EN)', rows: 2, span: 6 },
    { kind: 'textarea', key: 'guidanceAr', label: isAr ? 'إرشادات (AR)' : 'Guidance (AR)', rows: 2, span: 6 },
  ];

  return (
    <div className="dash-grid">
      <Card title={isAr ? 'مكتبة النماذج' : 'Template library'}
        action={
          <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
            <select value={statusFilter} onChange={e => setStatusFilter(e.target.value as QtStatus | '')}
              style={{ padding: '6px 10px', fontSize: 12, minWidth: 140, border: '1px solid var(--border)',
                       borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)' }}>
              <option value="">{isAr ? 'كل الحالات' : 'All statuses'}</option>
              <option value="DRAFT">{qtStatusLabel('DRAFT', isAr ? 'ar' : 'en')}</option>
              <option value="ACTIVE">{qtStatusLabel('ACTIVE', isAr ? 'ar' : 'en')}</option>
              <option value="ARCHIVED">{qtStatusLabel('ARCHIVED', isAr ? 'ar' : 'en')}</option>
            </select>
            <Button size="sm" variant="primary" Icon={Plus} onClick={openNewTemplate}>
              {isAr ? 'نموذج جديد' : 'New template'}
            </Button>
          </div>
        }>
        {loading ? <LoadingRow />
         : templates.length === 0
           ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
               {isAr ? 'لا توجد نماذج' : 'No questionnaire templates'}
             </div>
           : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'الكود' : 'Code'}</th>
                <th>{isAr ? 'الاسم' : 'Name'}</th>
                <th>{isAr ? 'الفئة' : 'Category'}</th>
                <th>{isAr ? 'الإصدار' : 'Version'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'العتبات' : 'Thresholds (L/M/H)'}</th>
                <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr></thead>
              <tbody>
                {templates.map(t => (
                  <tr key={t.id} style={{ cursor: 'pointer', background: selected?.id === t.id ? 'var(--surface-2)' : undefined }}
                      onClick={() => loadQuestions(t)}>
                    <td className="mono text-2xs">{t.templateCode}</td>
                    <td>
                      <div style={{ fontWeight: 600 }}>{isAr && t.nameAr ? t.nameAr : t.nameEn}</div>
                    </td>
                    <td className="text-xs">{t.category}</td>
                    <td className="mono text-2xs">v{t.version}</td>
                    <td><Badge tone={qtStatusTone(t.status)}>{qtStatusLabel(t.status, isAr ? 'ar' : 'en')}</Badge></td>
                    <td className="mono text-2xs">{t.thresholdLowMax}/{t.thresholdMediumMax}/{t.thresholdHighMax}</td>
                    <td style={{ textAlign: 'end' }} onClick={e => e.stopPropagation()}>
                      <div style={{ display: 'inline-flex', gap: 4 }}>
                        {t.status === 'DRAFT' && (
                          <>
                            <Button size="sm" variant="ghost" onClick={() => openEditTemplate(t)}>
                              {isAr ? 'تعديل' : 'Edit'}
                            </Button>
                            <Button size="sm" variant="primary" onClick={() => onActivate(t)}>
                              {isAr ? 'تفعيل' : 'Activate'}
                            </Button>
                          </>
                        )}
                        {t.status === 'ACTIVE' && (
                          <Button size="sm" variant="ghost" onClick={() => onArchive(t)}>
                            {isAr ? 'أرشفة' : 'Archive'}
                          </Button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>}
      </Card>

      {selected && (() => {
        const draft = selected.status === 'DRAFT';
        return (
        <Card title={`${selected.templateCode} v${selected.version} — ${isAr ? 'الأسئلة' : 'Questions'}`}
          action={
            <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
              <Badge tone="primary">{questions.length}</Badge>
              {draft
                ? <Button size="sm" variant="primary" Icon={Plus} onClick={openNewQuestion}>
                    {isAr ? 'سؤال' : 'Add question'}
                  </Button>
                : <span className="text-2xs text-muted">{isAr ? 'مجمّد (غير مسوّدة)' : 'Frozen (not draft)'}</span>}
            </div>
          }>
          {questions.length === 0
            ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
                {draft
                  ? (isAr ? 'لا توجد أسئلة بعد — أضِف أول سؤال لتفعيل النموذج.' : 'No questions yet — add the first question, then activate.')
                  : (isAr ? 'لا توجد أسئلة' : 'No questions')}
              </div>
            : <table className="grc-table">
                <thead><tr>
                  <th>{isAr ? 'الكود' : 'Code'}</th>
                  <th>{isAr ? 'القسم' : 'Section'}</th>
                  <th>{isAr ? 'السؤال' : 'Question'}</th>
                  <th>{isAr ? 'النوع' : 'Type'}</th>
                  <th>{isAr ? 'الوزن' : 'Weight'}</th>
                  <th>{isAr ? 'إلزامي' : 'Mandatory'}</th>
                  {draft && <th style={{ textAlign: 'end' }}>{isAr ? 'إجراءات' : 'Actions'}</th>}
                </tr></thead>
                <tbody>
                  {questions.map(q => (
                    <tr key={q.id}>
                      <td className="mono text-2xs">{q.questionCode}</td>
                      <td className="text-xs">{q.sectionCode} · {isAr && q.sectionNameAr ? q.sectionNameAr : q.sectionNameEn ?? '—'}</td>
                      <td className="text-xs" style={{ maxWidth: 360 }}>
                        {(isAr && q.questionAr ? q.questionAr : q.questionEn).substring(0, 120)}
                        {q.invertScore && <span style={{ marginInlineStart: 4 }}><Badge tone="warning">inverted</Badge></span>}
                      </td>
                      <td className="text-2xs">{q.answerType}</td>
                      <td className="mono text-2xs">{q.weight}</td>
                      <td>{q.mandatory ? <Badge tone="info">✓</Badge> : <span className="text-muted text-2xs">—</span>}</td>
                      {draft && (
                        <td style={{ textAlign: 'end' }}>
                          <div style={{ display: 'inline-flex', gap: 4 }}>
                            <Button size="sm" variant="ghost" onClick={() => openEditQuestion(q)}>{isAr ? 'تعديل' : 'Edit'}</Button>
                            <Button size="sm" variant="ghost" onClick={() => onDeleteQuestion(q)}>{isAr ? 'حذف' : 'Delete'}</Button>
                          </div>
                        </td>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>}
        </Card>
        );
      })()}

      {/* ─── Vendor assessments — fill out questionnaires ───────────── */}
      <Card title={isAr ? 'تقييمات المورّدين' : 'Vendor assessments'}
        action={<Badge tone="primary">{vendorAssessments.length}</Badge>}>
        {vendorAssessments.length === 0
          ? <div style={{ padding: 16, color: 'var(--ink-3)', fontSize: 13 }}>
              {isAr ? 'لا توجد تقييمات' : 'No vendor assessments yet'}
            </div>
          : <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'المورّد' : 'Vendor'}</th>
                <th>{isAr ? 'التاريخ' : 'Date'}</th>
                <th>{isAr ? 'النموذج' : 'Template'}</th>
                <th>{isAr ? 'التقدّم' : 'Progress'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'النسبة' : 'Risk %'}</th>
                <th>{isAr ? 'التصنيف' : 'Rating'}</th>
                <th style={{ textAlign: 'end' }}>{isAr ? 'الإجراءات' : 'Actions'}</th>
              </tr></thead>
              <tbody>
                {vendorAssessments.map(va => {
                  const vendor = vendors.find(v => v.id === va.vendorId);
                  const vendorName = vendor
                    ? (isAr && vendor.vendorNameAr ? vendor.vendorNameAr : vendor.vendorName)
                    : `#${va.vendorId}`;
                  const tpl = templates.find(t => t.id === va.templateId);
                  const total = va.totalQuestions ?? 0;
                  const ans = va.answeredCount ?? 0;
                  const pct = total > 0 ? Math.round((ans / total) * 100) : 0;
                  const status = (va.workflowStatus ?? 'NOT_STARTED') as string;
                  return (
                    <tr key={va.id}>
                      <td style={{ fontWeight: 600 }}>{vendorName}</td>
                      <td className="mono text-2xs">{va.assessmentDate?.substring(0, 10) ?? '—'}</td>
                      <td className="text-xs">
                        {tpl
                          ? <span>{tpl.templateCode} <span className="text-muted">v{tpl.version}</span></span>
                          : <span className="text-muted">{isAr ? 'لم يُعيَّن' : 'Not assigned'}</span>}
                      </td>
                      <td className="text-xs">
                        <span className="mono">{ans}/{total}</span>
                        {total > 0 && <span className="text-muted" style={{ marginInlineStart: 6 }}>({pct}%)</span>}
                      </td>
                      <td><Badge tone={workflowStatusTone(status)}>{status.replace('_', ' ')}</Badge></td>
                      <td className="mono text-2xs">{va.computedScorePct != null ? `${va.computedScorePct}%` : '—'}</td>
                      <td>{va.riskRating
                        ? <Badge tone={riskRatingTone(va.riskRating)}>{va.riskRating}</Badge>
                        : <span className="text-muted text-2xs">—</span>}</td>
                      <td style={{ textAlign: 'end' }}>
                        <div style={{ display: 'inline-flex', gap: 4 }}>
                          {!va.templateId && (
                            <Button size="sm" variant="primary" onClick={() => onAssignTemplate(va)}>
                              {isAr ? 'تعيين نموذج' : 'Assign template'}
                            </Button>
                          )}
                          {va.templateId && status !== 'REVIEWED' && (
                            <Button size="sm" variant={status === 'COMPLETED' ? 'ghost' : 'primary'} onClick={() => onOpenFill(va)}>
                              {status === 'COMPLETED' ? (isAr ? 'مراجعة' : 'Review') : (isAr ? 'إجابة' : 'Fill out')}
                            </Button>
                          )}
                          {status === 'REVIEWED' && (
                            <Button size="sm" variant="ghost" onClick={() => onOpenFill(va)}>
                              {isAr ? 'عرض' : 'View'}
                            </Button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>}
      </Card>

      {/* ─── Fill-out modal ─────────────────────────────────────────── */}
      <Modal
        open={!!openFill}
        size="lg"
        title={openFill
          ? `${isAr ? 'إجابة الاستبيان' : 'Fill questionnaire'} — ${
              vendors.find(v => v.id === openFill.vendorId)
                ? (isAr && vendors.find(v => v.id === openFill.vendorId)!.vendorNameAr
                    ? vendors.find(v => v.id === openFill.vendorId)!.vendorNameAr!
                    : vendors.find(v => v.id === openFill.vendorId)!.vendorName)
                : `#${openFill.vendorId}`
            }`
          : ''}
        onClose={() => setOpenFill(null)}
        footer={openFill && (
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', width: '100%' }}>
            <div className="text-xs text-muted">
              {openFill.computedScorePct != null
                ? <span>{isAr ? 'النسبة الحالية' : 'Current risk'}: <strong>{openFill.computedScorePct}%</strong>
                    {openFill.riskRating && <Badge tone={riskRatingTone(openFill.riskRating)} >{openFill.riskRating}</Badge>}
                  </span>
                : <span>{isAr ? 'لم تُحسب النتيجة بعد' : 'Score not yet computed'}</span>}
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <Button variant="ghost" onClick={() => setOpenFill(null)}>{isAr ? 'إغلاق' : 'Close'}</Button>
              {openFill.workflowStatus !== 'COMPLETED' && openFill.workflowStatus !== 'REVIEWED' && (
                <Button variant="primary" onClick={onComplete} disabled={fillBusy}>
                  {isAr ? 'وضع علامة مكتمل' : 'Mark complete'}
                </Button>
              )}
              {openFill.workflowStatus === 'COMPLETED' && (
                <Button variant="primary" onClick={onReview} disabled={fillBusy}>
                  {isAr ? 'وضع علامة تمت المراجعة' : 'Mark reviewed'}
                </Button>
              )}
            </div>
          </div>
        )}>
        {openFill && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
            {fillBusy && <div className="text-xs text-muted">{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>}
            {fillQuestions.length === 0 && !fillBusy && (
              <div className="text-xs text-muted">{isAr ? 'لا توجد أسئلة' : 'No questions in this template'}</div>
            )}
            {fillQuestions.map(q => {
              const resp = fillResponses[q.id!];
              const isNA = resp?.notApplicable === true;
              const answered = !!resp && (isNA || (resp.answerValue != null && resp.answerValue !== ''));
              const locked = openFill.workflowStatus === 'COMPLETED' || openFill.workflowStatus === 'REVIEWED';
              return (
                <div key={q.id} style={{
                  border: '1px solid var(--border)', borderRadius: 'var(--r-md)',
                  padding: 12, background: answered ? 'var(--surface-2)' : 'var(--surface-1)',
                }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', gap: 8, marginBottom: 8 }}>
                    <div style={{ flex: 1 }}>
                      <div className="mono text-2xs text-muted">
                        {q.questionCode} · {q.sectionCode}
                        {q.mandatory && <Badge tone="warning">{isAr ? 'إلزامي' : 'mandatory'}</Badge>}
                        {q.invertScore && <Badge tone="info">{isAr ? 'مقلوب' : 'inverted'}</Badge>}
                      </div>
                      <div style={{ fontWeight: 600, marginTop: 4 }}>
                        {isAr && q.questionAr ? q.questionAr : q.questionEn}
                      </div>
                      {(q.guidanceEn || q.guidanceAr) && (
                        <div className="text-2xs text-muted" style={{ marginTop: 4 }}>
                          {isAr && q.guidanceAr ? q.guidanceAr : q.guidanceEn}
                        </div>
                      )}
                    </div>
                    <div className="text-2xs text-muted">{isAr ? 'الوزن' : 'Weight'}: <strong>{q.weight}</strong></div>
                  </div>

                  {isNA ? (
                    <div className="text-xs" style={{ color: 'var(--ink-3)' }}>
                      <Badge tone="neutral">{isAr ? 'غير منطبق' : 'N/A'}</Badge>
                      <span style={{ marginInlineStart: 6 }}>{resp?.naJustification}</span>
                    </div>
                  ) : (
                    <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', alignItems: 'center' }}>
                      {q.answerType === 'BOOLEAN' && (
                        <>
                          <Button size="sm" disabled={locked || fillBusy}
                            variant={resp?.answerValue === 'YES' ? 'primary' : 'ghost'}
                            onClick={() => onAnswer(q, 'YES')}>{isAr ? 'نعم' : 'Yes'}</Button>
                          <Button size="sm" disabled={locked || fillBusy}
                            variant={resp?.answerValue === 'NO' ? 'primary' : 'ghost'}
                            onClick={() => onAnswer(q, 'NO')}>{isAr ? 'لا' : 'No'}</Button>
                        </>
                      )}
                      {q.answerType === 'SCALE_1_5' && (
                        [1, 2, 3, 4, 5].map(n => (
                          <Button key={n} size="sm" disabled={locked || fillBusy}
                            variant={resp?.answerValue === String(n) ? 'primary' : 'ghost'}
                            onClick={() => onAnswer(q, String(n))}>{n}</Button>
                        ))
                      )}
                      {q.answerType === 'SELECT' && q.optionsJson && (() => {
                        try {
                          const opts: { value: string; label?: string }[] = JSON.parse(q.optionsJson);
                          return opts.map(o => (
                            <Button key={o.value} size="sm" disabled={locked || fillBusy}
                              variant={resp?.answerValue === o.value ? 'primary' : 'ghost'}
                              onClick={() => onAnswer(q, o.value)}>{o.label ?? o.value}</Button>
                          ));
                        } catch { return <span className="text-2xs text-muted">{isAr ? 'خيارات غير صالحة' : 'Invalid options JSON'}</span>; }
                      })()}
                      {q.answerType === 'TEXT' && (
                        <TextAnswerField
                          initial={resp?.answerValue ?? ''}
                          disabled={locked || fillBusy}
                          placeholder={isAr ? 'الإجابة (نصية — يقيّمها المراجِع)' : 'Free text — reviewer-scored'}
                          onSave={v => onAnswer(q, v)}
                        />
                      )}
                      {!locked && (
                        <Button size="sm" variant="ghost" disabled={fillBusy} onClick={() => onMarkNA(q)}>
                          {isAr ? 'غير منطبق' : 'N/A'}
                        </Button>
                      )}
                    </div>
                  )}

                  {resp?.scoredPoints != null && !isNA && (
                    <div className="mono text-2xs text-muted" style={{ marginTop: 6 }}>
                      {isAr ? 'النقاط' : 'Score'}: {resp.scoredPoints}/{resp.maxPoints}
                      {resp.weightedPoints != null && <span> · {isAr ? 'موزون' : 'weighted'}: {resp.weightedPoints}</span>}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </Modal>

      {/* ─── Builder (L2) — template + question authoring modals ────── */}
      <FormModal<TemplateFormValues>
        open={tplFormOpen}
        title={tplEditing
          ? (isAr ? `تعديل النموذج — ${tplEditing.templateCode}` : `Edit template — ${tplEditing.templateCode}`)
          : (isAr ? 'نموذج استبيان جديد' : 'New questionnaire template')}
        fields={tplFields}
        initial={tplInitial}
        onClose={() => { setTplFormOpen(false); setTplEditing(null); }}
        onSubmit={saveTemplate}
        submitLabel={tplEditing ? (isAr ? 'حفظ' : 'Save') : (isAr ? 'إنشاء' : 'Create')}
      />
      <FormModal<QuestionFormValues>
        open={qFormOpen}
        title={qEditing
          ? (isAr ? `تعديل السؤال — ${qEditing.questionCode}` : `Edit question — ${qEditing.questionCode}`)
          : (isAr ? 'سؤال جديد' : 'New question')}
        fields={qFields}
        initial={qInitial}
        onClose={() => { setQFormOpen(false); setQEditing(null); }}
        onSubmit={saveQuestion}
        submitLabel={qEditing ? (isAr ? 'حفظ' : 'Save') : (isAr ? 'إضافة' : 'Add')}
      />
    </div>
  );
}

/** Free-text answer field with explicit Save action so we don't fire on every keystroke. */
function TextAnswerField({ initial, disabled, placeholder, onSave }: {
  initial: string; disabled?: boolean; placeholder?: string; onSave: (v: string) => void;
}) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const [v, setV] = useState(initial);
  useEffect(() => { setV(initial); }, [initial]);
  return (
    <div style={{ display: 'flex', gap: 6, alignItems: 'flex-start', flex: 1, minWidth: 280 }}>
      <textarea
        value={v} onChange={e => setV(e.target.value)} disabled={disabled}
        placeholder={placeholder} rows={2}
        style={{ flex: 1, padding: 8, fontSize: 12, border: '1px solid var(--border)',
                 borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)', resize: 'vertical' }} />
      <Button size="sm" variant="primary" disabled={disabled || v === initial} onClick={() => onSave(v)}>
        {isAr ? 'حفظ' : 'Save'}
      </Button>
    </div>
  );
}
