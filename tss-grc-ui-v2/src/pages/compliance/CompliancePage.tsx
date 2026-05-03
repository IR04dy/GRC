import { useEffect, useState } from 'react';
import {
  ClipboardCheck, BookOpenCheck, FileCheck2, Award, Filter, Plus, BarChart3, Building2, AlertTriangle,
  ScrollText, BookCopy, Send, CheckCheck, Archive, RotateCcw, X as XIcon, Wrench, Flag, Search,
  Briefcase, ShieldCheck, Stamp, ChevronRight, Lock,
} from 'lucide-react';
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
import {
  complianceApi, reportingApi, ApiError,
  type ComplianceControlDto, type ComplianceControlRequest,
  type ComplianceAssessmentDto, type ComplianceFindingDto,
  type RegulatoryChangeDto, type RegulatoryChangeRequest,
  type PolicyDto, type PolicyRequest,
  type AuditEngagementDto, type AuditEngagementRequest,
} from '../../lib/api';
import { ExportMenu } from '../../components/ui/ExportMenu';
import { useToast } from '../../lib/toast';
import { COMPLIANCE_CONTROLS } from './mockCompliance';

type TabId = 'overview' | 'controls' | 'internal' | 'external' | 'findings' | 'scorecard' | 'changes' | 'policies' | 'engagements';

// External audit lifecycle stages — assessment §5.3 #3
const AUDIT_TYPE_OPTIONS = [
  { value: 'INTERNAL',      label: 'Internal' },
  { value: 'EXTERNAL',      label: 'External' },
  { value: 'REGULATORY',    label: 'Regulatory' },
  { value: 'CERTIFICATION', label: 'Certification' },
];
const AUDIT_FRAMEWORK_OPTIONS = [
  { value: 'ISO 27001:2022', label: 'ISO 27001:2022' },
  { value: 'ISO 27701',      label: 'ISO 27701' },
  { value: 'ISO 22301',      label: 'ISO 22301' },
  { value: 'NCA ECC v2.0',   label: 'NCA ECC v2.0' },
  { value: 'NCA CCC',        label: 'NCA CCC' },
  { value: 'SAMA CSF v1.0',  label: 'SAMA CSF v1.0' },
  { value: 'PDPL',           label: 'SDAIA PDPL' },
  { value: 'ZATCA E-invoicing', label: 'ZATCA E-invoicing' },
  { value: 'CMA',            label: 'CMA' },
];
const AUDIT_STATUS_FLOW = ['PLANNED', 'ENGAGED', 'OPENING_MEETING', 'FIELDWORK', 'REPORTING', 'MGMT_RESPONSE', 'CLOSED', 'FOLLOW_UP'] as const;
const AUDIT_STATUS_TONE: Record<string, 'neutral' | 'info' | 'primary' | 'accent' | 'warning' | 'success' | 'danger'> = {
  PLANNED: 'neutral', ENGAGED: 'info', OPENING_MEETING: 'info',
  FIELDWORK: 'primary', REPORTING: 'accent', MGMT_RESPONSE: 'warning',
  CLOSED: 'success', FOLLOW_UP: 'info',
};
const ENGAGEMENT_EMPTY: AuditEngagementRequest = {
  engagementCode: '', titleEn: '', titleAr: '',
  auditType: 'EXTERNAL', framework: 'ISO 27001:2022',
  auditorFirm: '', leadAuditor: '',
  engagementLetterDate: '', openingMeetingDate: '',
  fieldworkStartDate: '', fieldworkEndDate: '',
  reportIssuedDate: '', closureDate: '', nextFollowupDate: '',
  scopeSummary: '', status: 'PLANNED',
};
function engagementToReq(e: AuditEngagementDto): AuditEngagementRequest {
  return {
    engagementCode: e.engagementCode, titleEn: e.titleEn, titleAr: e.titleAr,
    auditType: e.auditType, framework: e.framework,
    auditorFirm: e.auditorFirm, leadAuditor: e.leadAuditor,
    engagementLetterDate: e.engagementLetterDate, openingMeetingDate: e.openingMeetingDate,
    fieldworkStartDate: e.fieldworkStartDate, fieldworkEndDate: e.fieldworkEndDate,
    reportIssuedDate: e.reportIssuedDate, closureDate: e.closureDate,
    nextFollowupDate: e.nextFollowupDate,
    scopeSummary: e.scopeSummary, status: e.status,
  };
}

// CAPA stage badges — assessment §5.3 #6
const CAPA_STAGE_TONE: Record<string, 'neutral' | 'info' | 'primary' | 'accent' | 'warning' | 'success' | 'danger'> = {
  IDENTIFIED:        'neutral',
  IMPLEMENTING:      'info',
  OWNER_TESTED:      'primary',
  INDEPENDENT_TESTED: 'accent',
  AUDIT_SIGNED_OFF:  'warning',
  CLOSED:            'success',
};
const TIER_TONE: Record<string, 'success' | 'info' | 'warning' | 'danger'> = {
  ON_TRACK:          'success',
  MANAGER_ESCALATED: 'info',
  DIRECTOR_ESCALATED: 'warning',
  EXEC_ESCALATED:    'danger',
};

interface AgingFinding extends ComplianceFindingDto {
  daysOpen: number;
  escalationTier: string;
  createdAt?: string;
  capaStage?: string;
}

const FRAMEWORK_OPTIONS = [
  { value: 'ISO_27001', label: 'ISO 27001' }, { value: 'ISO_22301', label: 'ISO 22301' },
  { value: 'NCA_ECC', label: 'NCA ECC' }, { value: 'PDPL', label: 'Saudi PDPL' },
  { value: 'SAMA_CSF', label: 'SAMA CSF' }, { value: 'ISO_27701', label: 'ISO 27701' },
];
const STATUS_OPTIONS = [
  { value: 'IMPLEMENTED', label: 'Implemented' }, { value: 'PARTIAL', label: 'Partial' },
  { value: 'PLANNED', label: 'Planned' }, { value: 'NOT_APPLICABLE', label: 'Not Applicable' },
];

// Saudi authorities that publish regulations relevant to enterprise GRC.
const AUTHORITY_OPTIONS = [
  { value: 'NCA',    label: 'NCA — National Cybersecurity Authority' },
  { value: 'SDAIA',  label: 'SDAIA — Saudi Data & AI Authority' },
  { value: 'SAMA',   label: 'SAMA — Saudi Central Bank' },
  { value: 'CMA',    label: 'CMA — Capital Market Authority' },
  { value: 'ZATCA',  label: 'ZATCA — Zakat, Tax & Customs Authority' },
  { value: 'CITC',   label: 'CITC — Communications, Space & Technology Commission' },
  { value: 'NCEMA',  label: 'NCEMA — National Center for Emergency Management' },
  { value: 'MoCI',   label: 'MoCI — Ministry of Commerce' },
  { value: 'HRSD',   label: 'HRSD — Ministry of Human Resources' },
  { value: 'MoH',    label: 'MoH / HCIS — Ministry of Health' },
  { value: 'OTHER',  label: 'Other' },
];
const APPLICABILITY_OPTIONS = [
  { value: 'UNKNOWN',         label: 'Unknown' },
  { value: 'APPLICABLE',      label: 'Applicable' },
  { value: 'PARTIAL',         label: 'Partial' },
  { value: 'NOT_APPLICABLE',  label: 'Not Applicable' },
];
const IMPACT_LEVEL_OPTIONS = [
  { value: 'UNKNOWN',  label: 'Unknown' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
const POLICY_CATEGORY_OPTIONS = [
  { value: 'INFORMATION_SECURITY', label: 'Information Security' },
  { value: 'PRIVACY',              label: 'Privacy / PDPL' },
  { value: 'RISK_MANAGEMENT',      label: 'Risk Management' },
  { value: 'BUSINESS_CONTINUITY',  label: 'Business Continuity' },
  { value: 'ACCEPTABLE_USE',       label: 'Acceptable Use' },
  { value: 'INCIDENT_RESPONSE',    label: 'Incident Response' },
  { value: 'HR',                   label: 'Human Resources' },
  { value: 'FINANCE',              label: 'Finance' },
];
const CHANGE_EMPTY: RegulatoryChangeRequest = {
  sourceAuthority: 'NCA', titleEn: '', titleAr: '', citation: '',
  summaryEn: '', summaryAr: '', publishedDate: '', effectiveDate: '',
  applicability: 'UNKNOWN', impactLevel: 'UNKNOWN', impactSummary: '',
};
const POLICY_EMPTY: PolicyRequest = {
  code: '', titleEn: '', titleAr: '',
  version: '1.0', category: 'INFORMATION_SECURITY',
  contentEn: '', effectiveFrom: '', nextReviewDate: '',
};

const RISK_TONE_BADGE: Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success', UNKNOWN: 'neutral',
};

// Generic per-entity state hook — same pattern used in BCM/SCM/Cyber pages.
function useEntityState<T>() {
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<T | null>(null);
  const [confirmDel, setConfirmDel] = useState<T | null>(null);
  return {
    open, editing, confirmDel,
    startCreate: () => { setEditing(null); setOpen(true); },
    startEdit: (r: T) => { setEditing(r); setOpen(true); },
    askDelete: (r: T) => setConfirmDel(r),
    closeModal: () => { setOpen(false); setEditing(null); },
    closeConfirm: () => setConfirmDel(null),
  };
}

function changeToReq(c: RegulatoryChangeDto): RegulatoryChangeRequest {
  return {
    sourceAuthority: c.sourceAuthority, titleEn: c.titleEn, titleAr: c.titleAr,
    citation: c.citation, summaryEn: c.summaryEn, summaryAr: c.summaryAr,
    publishedDate: c.publishedDate, effectiveDate: c.effectiveDate,
    applicability: c.applicability, impactLevel: c.impactLevel, impactSummary: c.impactSummary,
  };
}
function policyToReq(p: PolicyDto): PolicyRequest {
  return {
    code: p.code, titleEn: p.titleEn, titleAr: p.titleAr,
    version: p.version, category: p.category, contentEn: p.contentEn, contentAr: p.contentAr,
    effectiveFrom: p.effectiveFrom, nextReviewDate: p.nextReviewDate,
  };
}
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
  const changes     = useApiList<RegulatoryChangeDto>(()     => complianceApi.regulatoryChanges(0, 100), []);
  const policies    = useApiList<PolicyDto>(()               => complianceApi.policies(0, 100),    []);
  const engagements = useApiList<AuditEngagementDto>(()      => complianceApi.auditEngagements(0, 100), []);

  const changeS = useEntityState<RegulatoryChangeDto>();
  const policyS = useEntityState<PolicyDto>();
  const engageS = useEntityState<AuditEngagementDto>();

  // Findings aging — pulled separately so daysOpen + escalationTier + capaStage are present
  const [aging, setAging] = useState<AgingFinding[]>([]);
  const [agingLoaded, setAgingLoaded] = useState(false);
  useEffect(() => {
    if (tab !== 'findings' || agingLoaded) return;
    complianceApi.findingsAging()
      .then(rows => setAging(rows as AgingFinding[]))
      .catch(() => setAging([]))
      .finally(() => setAgingLoaded(true));
  }, [tab, agingLoaded]);
  const reloadAging = () => { setAgingLoaded(false); };

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
    { id: 'overview',    label: t('tabOverview') },
    { id: 'controls',    label: t('complianceTabControls') },
    { id: 'changes',     label: t('complianceTabChanges') },
    { id: 'policies',    label: t('complianceTabPolicies') },
    { id: 'engagements', label: t('complianceTabEngagements') },
    { id: 'internal',    label: t('complianceTabInternal') },
    { id: 'external',    label: t('complianceTabExternal') },
    { id: 'findings',    label: t('complianceTabFindings') },
    { id: 'scorecard',   label: t('complianceTabScorecard') },
  ];

  // ── Field defs for Regulatory Changes + Policies (built per-render so labels translate) ──
  const CHANGE_FIELDS: FieldDef[] = [
    { kind: 'select',   key: 'sourceAuthority', label: t('complianceFldAuthority') + ' *', required: true, options: AUTHORITY_OPTIONS, span: 6 },
    { kind: 'text',     key: 'citation',        label: t('complianceFldCitation'), span: 6 },
    { kind: 'text',     key: 'titleEn',         label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',         label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'date',     key: 'publishedDate',   label: t('complianceFldPublishedDate'), span: 6 },
    { kind: 'date',     key: 'effectiveDate',   label: t('complianceFldEffectiveDate'), span: 6 },
    { kind: 'select',   key: 'applicability',   label: t('complianceFldApplicability'), options: APPLICABILITY_OPTIONS, span: 6 },
    { kind: 'select',   key: 'impactLevel',     label: t('complianceFldImpactLevel'),   options: IMPACT_LEVEL_OPTIONS, span: 6 },
    { kind: 'textarea', key: 'summaryEn',       label: t('complianceFldSummary'), rows: 3, span: 12 },
    { kind: 'textarea', key: 'impactSummary',   label: t('complianceFldImpactSummary'), rows: 2, span: 12 },
  ];
  const POLICY_FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'code',     label: t('fldCode') + ' *', required: true, maxLength: 50, placeholder: 'POL-INFO-001', span: 4 },
    { kind: 'text',     key: 'version',  label: t('fldVersion'), placeholder: '1.0', span: 4 },
    { kind: 'select',   key: 'category', label: t('fldCategory'), options: POLICY_CATEGORY_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleEn',  label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',  label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'date',     key: 'effectiveFrom',   label: t('complianceFldEffectiveFrom'), span: 6 },
    { kind: 'date',     key: 'nextReviewDate',  label: t('fldNextReviewDate'), span: 6 },
    { kind: 'textarea', key: 'contentEn',       label: t('complianceFldPolicyContent'), rows: 4, span: 12 },
  ];

  // ── Columns for Regulatory Changes + Policies ──
  const changeCols: Column<RegulatoryChangeDto>[] = [
    { header: t('complianceFldAuthority'), cell: r => <Badge tone="primary">{r.sourceAuthority}</Badge> },
    { header: t('colTitle'),               cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}</div>{r.citation && <div style={{ fontSize: 11, color: 'var(--ink-3)' }} className="mono">{r.citation}</div>}</div> },
    { header: t('complianceFldEffectiveDate'), cell: r => r.effectiveDate ?? '—', mono: true },
    { header: t('complianceFldApplicability'), cell: r => <Badge tone={r.applicability === 'APPLICABLE' ? 'warning' : r.applicability === 'NOT_APPLICABLE' ? 'success' : r.applicability === 'PARTIAL' ? 'info' : 'neutral'}>{r.applicability}</Badge> },
    { header: t('complianceFldImpactLevel'),   cell: r => <Badge tone={RISK_TONE_BADGE[r.impactLevel] ?? 'neutral'}>{r.impactLevel}</Badge> },
    { header: t('colStatus'),                  cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? (r.status === 'CLOSED' ? 'success' : 'info')} dot>{r.status.replace(/_/g, ' ')}</Badge> },
    { header: '', align: 'end', width: '210px',
      cell: r => {
        const extras: Array<{ Icon: typeof Search; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'NEW')                     extras.push({ Icon: Search, onClick: () => assessApplicability(r), title: t('complianceActAssessApplicability'), tone: 'primary' });
        if (r.status === 'APPLICABILITY_ASSESSED')  extras.push({ Icon: Flag,   onClick: () => assessImpact(r),         title: t('complianceActAssessImpact'),         tone: 'accent' });
        if (r.status === 'IMPACT_ASSESSED')         extras.push({ Icon: Wrench, onClick: () => markControlsUpdated(r),  title: t('complianceActMarkControlsUpdated'),  tone: 'primary' });
        if (r.status === 'CONTROLS_UPDATED')        extras.push({ Icon: Archive, onClick: () => closeChangeAction(r),   title: t('complianceActCloseChange'),          tone: 'success' });
        return <RowActions extras={extras} onEdit={() => changeS.startEdit(r)} onDelete={() => changeS.askDelete(r)} />;
      } },
  ];

  const policyCols: Column<PolicyDto>[] = [
    { header: t('fldCode'),     cell: r => <span className="mono" style={{ fontWeight: 600 }}>{r.code}</span> },
    { header: t('colTitle'),    cell: r => <div><div style={{ fontWeight: 600 }}>{r.titleEn}</div>{r.category && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.category.replace(/_/g, ' ')}</div>}</div> },
    { header: t('fldVersion'),  cell: r => r.version, mono: true },
    { header: t('complianceFldEffectiveFrom'), cell: r => r.effectiveFrom ?? '—', mono: true },
    { header: t('fldNextReviewDate'),          cell: r => r.nextReviewDate ?? '—', mono: true },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'info'} dot>{r.status.replace(/_/g, ' ')}</Badge> },
    { header: '', align: 'end', width: '230px',
      cell: r => {
        const extras: Array<{ Icon: typeof Send; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status === 'DRAFT' || r.status === 'REJECTED') extras.push({ Icon: Send,        onClick: () => submitForLegal(r),   title: t('complianceActSubmitLegal'),  tone: 'primary' });
        if (r.status === 'LEGAL_REVIEW')                     extras.push({ Icon: CheckCheck,  onClick: () => legalApprove(r),     title: t('complianceActLegalApprove'), tone: 'accent' });
        if (r.status === 'EXEC_APPROVAL')                    extras.push({ Icon: CheckCheck,  onClick: () => execApprove(r),      title: t('complianceActExecApprove'),  tone: 'success' });
        if (r.status === 'PUBLISHED')                        extras.push({ Icon: RotateCcw,   onClick: () => markReviewDue(r),    title: t('complianceActMarkReview'),   tone: 'accent' });
        if (r.status === 'PUBLISHED' || r.status === 'REVIEW_DUE') extras.push({ Icon: Archive, onClick: () => retire(r),         title: t('complianceActRetire'),       tone: 'primary' });
        if (r.status === 'LEGAL_REVIEW' || r.status === 'EXEC_APPROVAL') extras.push({ Icon: XIcon, onClick: () => rejectPolicy(r), title: t('complianceActReject') });
        return <RowActions extras={extras} onEdit={() => policyS.startEdit(r)} onDelete={() => policyS.askDelete(r)} />;
      } },
  ];

  // ── Submit + workflow handlers (Regulatory Changes + Policies) ──
  async function submitChange(values: RegulatoryChangeRequest) {
    try {
      if (changeS.editing) await complianceApi.updateChange(changeS.editing.id, values);
      else await complianceApi.createChange(values);
      toast.success(changeS.editing ? 'Change updated' : 'Change added');
      changeS.closeModal(); changes.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitPolicy(values: PolicyRequest) {
    try {
      if (policyS.editing) await complianceApi.updatePolicy(policyS.editing.id, values);
      else await complianceApi.createPolicy(values);
      toast.success(policyS.editing ? 'Policy updated' : 'Policy added');
      policyS.closeModal(); policies.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function assessApplicability(c: RegulatoryChangeDto) {
    const v = window.prompt('Applicability — APPLICABLE / PARTIAL / NOT_APPLICABLE', 'APPLICABLE');
    if (!v) return;
    try { await complianceApi.assessApplicability(c.id, v.trim().toUpperCase()); toast.success('Applicability recorded'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function assessImpact(c: RegulatoryChangeDto) {
    const lvl = window.prompt('Impact level — CRITICAL / HIGH / MEDIUM / LOW', 'MEDIUM');
    if (!lvl) return;
    const summ = window.prompt('Impact summary (1-2 sentences)?', '') ?? '';
    try { await complianceApi.assessImpact(c.id, lvl.trim().toUpperCase(), summ); toast.success('Impact assessed'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function markControlsUpdated(c: RegulatoryChangeDto) {
    const n = window.prompt('How many controls were updated?', '1');
    if (n === null) return;
    try { await complianceApi.markControlsUpdated(c.id, Number(n) || 0); toast.success('Controls update recorded'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function closeChangeAction(c: RegulatoryChangeDto) {
    try { await complianceApi.closeChange(c.id); toast.success('Change closed'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function submitForLegal(p: PolicyDto) {
    try { await complianceApi.submitForLegalReview(p.id); toast.success('Submitted for legal review'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function legalApprove(p: PolicyDto) {
    try { await complianceApi.legalApprovePolicy(p.id); toast.success('Legal review approved'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function execApprove(p: PolicyDto) {
    if (!window.confirm(`Publish policy "${p.titleEn}" via executive approval? This sets effective_from = today and nextReviewDate = +12 months if blank.`)) return;
    try { await complianceApi.execApprovePolicy(p.id); toast.success('Policy published'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function markReviewDue(p: PolicyDto) {
    try { await complianceApi.markPolicyReviewDue(p.id); toast.success('Marked review-due'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function retire(p: PolicyDto) {
    if (!window.confirm(`Retire policy "${p.titleEn}"? It will no longer be enforceable.`)) return;
    try { await complianceApi.retirePolicy(p.id); toast.success('Policy retired'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function rejectPolicy(p: PolicyDto) {
    const reason = window.prompt('Rejection reason?', '') ?? '';
    if (!reason.trim()) return;
    try { await complianceApi.rejectPolicy(p.id, reason); toast.success('Policy rejected'); policies.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  // ── External audit engagement workflow (assessment §5.3 #3) ──────────────
  async function submitEngagement(values: AuditEngagementRequest) {
    try {
      if (engageS.editing) await complianceApi.updateAuditEngagement(engageS.editing.id, values);
      else await complianceApi.createAuditEngagement(values);
      toast.success(engageS.editing ? 'Engagement updated' : 'Engagement created');
      engageS.closeModal(); engagements.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  function nextStatus(current: string): string | null {
    const idx = AUDIT_STATUS_FLOW.indexOf(current as typeof AUDIT_STATUS_FLOW[number]);
    if (idx < 0 || idx >= AUDIT_STATUS_FLOW.length - 1) return null;
    return AUDIT_STATUS_FLOW[idx + 1];
  }
  async function advanceEngagement(e: AuditEngagementDto) {
    const nx = nextStatus(e.status);
    if (!nx) { toast.danger('Engagement already at terminal status'); return; }
    if (!window.confirm(`Advance "${e.titleEn}" → ${nx.replace(/_/g, ' ')}?`)) return;
    try { await complianceApi.advanceAuditEngagement(e.id, nx); toast.success(`Status → ${nx}`); engagements.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Advance failed'); }
  }
  async function scheduleFollowUp(e: AuditEngagementDto) {
    const d = window.prompt('Follow-up date (YYYY-MM-DD)?', '');
    if (!d || !/^\d{4}-\d{2}-\d{2}$/.test(d)) return;
    try { await complianceApi.scheduleAuditFollowup(e.id, d); toast.success('Follow-up scheduled'); engagements.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Action failed'); }
  }

  // ── CAPA closure verification workflow (assessment §5.3 #6) ──────────────
  async function capaStartImpl(f: AgingFinding) {
    try { await complianceApi.capaStartImplementing(f.id); toast.success('Marked: implementing'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaOwnerTest(f: AgingFinding) {
    const r = window.prompt('Owner test result — PASS / FAIL / PARTIAL', 'PASS');
    if (!r) return;
    const notes = window.prompt('Owner test notes (optional)?', '') ?? '';
    try { await complianceApi.capaOwnerTest(f.id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL', notes }); toast.success('Owner test recorded'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaIndepTest(f: AgingFinding) {
    const r = window.prompt('Independent test result — PASS / FAIL / PARTIAL', 'PASS');
    if (!r) return;
    const notes = window.prompt('Independent test notes (optional)?', '') ?? '';
    try { await complianceApi.capaIndependentTest(f.id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL', notes }); toast.success('Independent test recorded'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaSignoff(f: AgingFinding) {
    const notes = window.prompt('Audit sign-off notes (optional)?', '') ?? '';
    try { await complianceApi.capaAuditSignoff(f.id, { notes }); toast.success('Audit signed off'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaCloseFinding(f: AgingFinding) {
    if (!window.confirm(`Close finding "${f.title}"? CAPA stage will become CLOSED.`)) return;
    try { await complianceApi.capaClose(f.id); toast.success('Finding closed'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  async function execDelete<T extends { id: number }>(
    target: T | null,
    apiFn: (id: number) => Promise<unknown>,
    onClose: () => void,
    reload: () => void,
    label: string,
  ) {
    if (!target) return;
    setBusy(true);
    try { await apiFn(target.id); toast.success(`${label} deleted`); reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Delete failed'); }
    finally { setBusy(false); onClose(); }
  }

  // Tab-aware "New" dispatcher
  function newAction() {
    return () => {
      switch (tab) {
        case 'changes':     return changeS.startCreate();
        case 'policies':    return policyS.startCreate();
        case 'engagements': return engageS.startCreate();
        default:            return setOpen(true);  // Controls modal (legacy)
      }
    };
  }
  const showNewButton = tab !== 'overview' && tab !== 'findings' && tab !== 'scorecard';

  // Engagement form fields
  const ENGAGEMENT_FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'engagementCode', label: t('engagementFldCode'),  placeholder: 'AUD-2025-001', span: 4 },
    { kind: 'select',   key: 'auditType',      label: t('engagementFldType') + ' *', required: true, options: AUDIT_TYPE_OPTIONS, span: 4 },
    { kind: 'select',   key: 'framework',      label: t('engagementFldFramework'), options: AUDIT_FRAMEWORK_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleEn',        label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 500, span: 6 },
    { kind: 'text',     key: 'titleAr',        label: t('fldTitleArLbl'), maxLength: 500, span: 6 },
    { kind: 'text',     key: 'auditorFirm',    label: t('engagementFldAuditorFirm'), span: 6 },
    { kind: 'text',     key: 'leadAuditor',    label: t('engagementFldLeadAuditor'), span: 6 },
    { kind: 'date',     key: 'engagementLetterDate', label: t('engagementFldLetterDate'), span: 4 },
    { kind: 'date',     key: 'openingMeetingDate',   label: t('engagementFldOpeningMeeting'), span: 4 },
    { kind: 'date',     key: 'fieldworkStartDate',   label: t('engagementFldFieldworkStart'), span: 4 },
    { kind: 'date',     key: 'fieldworkEndDate',     label: t('engagementFldFieldworkEnd'), span: 4 },
    { kind: 'date',     key: 'reportIssuedDate',     label: t('engagementFldReportIssued'), span: 4 },
    { kind: 'date',     key: 'closureDate',          label: t('engagementFldClosureDate'), span: 4 },
    { kind: 'textarea', key: 'scopeSummary',         label: t('engagementFldScope'), rows: 3, span: 12 },
  ];

  const engagementCols: Column<AuditEngagementDto>[] = [
    { header: t('engagementFldCode'),   cell: r => <span className="mono" style={{ fontWeight: 600 }}>{r.engagementCode ?? '—'}</span> },
    { header: t('colTitle'),            cell: r => (
        <div>
          <div style={{ fontWeight: 600 }}>{r.titleEn}</div>
          {r.framework && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.framework} · {r.auditorFirm ?? '—'}</div>}
        </div>
    ) },
    { header: t('engagementFldType'),   cell: r => <Badge tone={r.auditType === 'CERTIFICATION' ? 'accent' : r.auditType === 'REGULATORY' ? 'warning' : r.auditType === 'EXTERNAL' ? 'primary' : 'neutral'}>{r.auditType}</Badge> },
    { header: t('engagementFldFieldwork'), cell: r => r.fieldworkStartDate ? `${r.fieldworkStartDate} → ${r.fieldworkEndDate ?? '?'}` : '—', mono: true },
    { header: t('colStatus'),           cell: r => <Badge tone={AUDIT_STATUS_TONE[r.status] ?? 'neutral'} dot>{r.status.replace(/_/g, ' ')}</Badge> },
    { header: '', align: 'end', width: '210px',
      cell: r => {
        const extras: Array<{ Icon: typeof ChevronRight; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status !== 'CLOSED' && r.status !== 'FOLLOW_UP') {
          const nx = nextStatus(r.status);
          if (nx) extras.push({ Icon: ChevronRight, onClick: () => advanceEngagement(r), title: `${t('engagementActAdvance')} → ${nx}`, tone: 'primary' });
        }
        if (r.status === 'CLOSED') extras.push({ Icon: RotateCcw, onClick: () => scheduleFollowUp(r), title: t('engagementActScheduleFollowup'), tone: 'accent' });
        return <RowActions extras={extras} onEdit={() => engageS.startEdit(r)} onDelete={() => engageS.askDelete(r)} />;
      } },
  ];

  // Findings aging table columns (with CAPA workflow buttons)
  const agingCols: Column<AgingFinding>[] = [
    { header: t('colTitle'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}</span> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity] ?? 'neutral'} dot>{severityLabel(t, r.severity)}</Badge> },
    { header: t('colDaysOpen'), cell: r => <span className="mono">{r.daysOpen}d</span> },
    { header: t('colTier'),     cell: r => <Badge tone={TIER_TONE[r.escalationTier] ?? 'neutral'} dot>{r.escalationTier.replace(/_/g, ' ')}</Badge> },
    { header: t('colCapaStage'), cell: r => <Badge tone={CAPA_STAGE_TONE[r.capaStage ?? 'IDENTIFIED'] ?? 'neutral'}>{(r.capaStage ?? 'IDENTIFIED').replace(/_/g, ' ')}</Badge> },
    { header: t('colOwner'),    cell: r => r.owner ?? '—' },
    { header: '', align: 'end', width: '230px',
      cell: r => {
        const stage = r.capaStage ?? 'IDENTIFIED';
        const extras: Array<{ Icon: typeof Wrench; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (stage === 'IDENTIFIED')         extras.push({ Icon: Wrench,      onClick: () => capaStartImpl(r),    title: t('capaActStart'),      tone: 'primary' });
        if (stage === 'IMPLEMENTING')       extras.push({ Icon: ShieldCheck, onClick: () => capaOwnerTest(r),    title: t('capaActOwnerTest'),  tone: 'primary' });
        if (stage === 'OWNER_TESTED')       extras.push({ Icon: Briefcase,   onClick: () => capaIndepTest(r),    title: t('capaActIndepTest'),  tone: 'accent' });
        if (stage === 'INDEPENDENT_TESTED') extras.push({ Icon: Stamp,       onClick: () => capaSignoff(r),      title: t('capaActSignoff'),    tone: 'accent' });
        if (stage === 'AUDIT_SIGNED_OFF')   extras.push({ Icon: Lock,        onClick: () => capaCloseFinding(r), title: t('capaActClose'),      tone: 'success' });
        return <RowActions extras={extras} />;
      } },
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
            <ExportMenu
              demoMode={isDemo} t={t} toast={toast}
              onPdf={() => reportingApi.findingsPdf((typeof navigator !== 'undefined' && navigator.language?.startsWith('ar')) ? 'ar' : 'en')}
              onXlsx={() => reportingApi.findingsXlsx()}
            />
            {showNewButton && <Button variant="primary" Icon={Plus} onClick={newAction()}>{t('actionNew')}</Button>}
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
      {tab === 'changes' && (
        <Card padded={false}>
          {changes.loading ? <LoadingRow /> : <DataTable columns={changeCols} rows={changes.data}
            empty={<EmptyState Icon={ScrollText} title="No regulatory changes tracked" sub="New laws, amendments and guidance from NCA, SDAIA, SAMA, CMA, ZATCA, CITC, NCEMA flow through here." />} />}
        </Card>
      )}
      {tab === 'policies' && (
        <Card padded={false}>
          {policies.loading ? <LoadingRow /> : <DataTable columns={policyCols} rows={policies.data}
            empty={<EmptyState Icon={BookCopy} title="No policies on file" sub="Author, review, approve and publish enterprise policies through the lifecycle." />} />}
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
        <>
          <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(4, minmax(180px, 1fr))', marginBottom: 12 }}>
            <Kpi Icon={ClipboardCheck} label={t('agingKpiOnTrack')}        value={aging.filter(f => f.escalationTier === 'ON_TRACK').length} tone="success" />
            <Kpi Icon={Flag}           label={t('agingKpiManager')}        value={aging.filter(f => f.escalationTier === 'MANAGER_ESCALATED').length} tone="info" />
            <Kpi Icon={AlertTriangle}  label={t('agingKpiDirector')}       value={aging.filter(f => f.escalationTier === 'DIRECTOR_ESCALATED').length} tone="accent" />
            <Kpi Icon={AlertTriangle}  label={t('agingKpiExec')}           value={aging.filter(f => f.escalationTier === 'EXEC_ESCALATED').length} tone="danger" />
          </div>
          <Card padded={false}>
            {!agingLoaded ? <LoadingRow /> :
              aging.length === 0
                ? <DataTable columns={findingCols} rows={findings.data} empty={<EmptyState Icon={AlertTriangle} title="No open findings" sub="Findings link audit observations to remediation owners with due dates." />} />
                : <DataTable columns={agingCols} rows={aging} empty={<EmptyState />} />
            }
          </Card>
        </>
      )}
      {tab === 'engagements' && (
        <Card padded={false}>
          {engagements.loading ? <LoadingRow /> :
            <DataTable columns={engagementCols} rows={engagements.data} empty={<EmptyState Icon={Briefcase} title={t('engagementsEmptyTitle')} sub={t('engagementsEmptySub')} />} />
          }
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

      {/* Regulatory Change modal + confirm */}
      <FormModal<RegulatoryChangeRequest> open={changeS.open}
        title={changeS.editing ? `${t('editPrefix')} · ${changeS.editing.titleEn}` : t('newRegulatoryChangeTitle')}
        fields={CHANGE_FIELDS}
        initial={changeS.editing ? changeToReq(changeS.editing) : CHANGE_EMPTY}
        onClose={changeS.closeModal} onSubmit={submitChange} />
      <ConfirmDialog
        open={!!changeS.confirmDel} destructive busy={busy}
        title={t('complianceConfirmDeleteChange')}
        message={changeS.confirmDel ? `Change "${changeS.confirmDel.titleEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={changeS.closeConfirm}
        onConfirm={() => execDelete(changeS.confirmDel, complianceApi.deleteChange, changeS.closeConfirm, changes.reload, 'Change')} />

      {/* Policy modal + confirm */}
      <FormModal<PolicyRequest> open={policyS.open}
        title={policyS.editing ? `${t('editPrefix')} · ${policyS.editing.code}` : t('newPolicyTitle')}
        fields={POLICY_FIELDS}
        initial={policyS.editing ? policyToReq(policyS.editing) : POLICY_EMPTY}
        onClose={policyS.closeModal} onSubmit={submitPolicy} />
      <ConfirmDialog
        open={!!policyS.confirmDel} destructive busy={busy}
        title={t('complianceConfirmDeletePolicy')}
        message={policyS.confirmDel ? `Policy "${policyS.confirmDel.code} · ${policyS.confirmDel.titleEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={policyS.closeConfirm}
        onConfirm={() => execDelete(policyS.confirmDel, complianceApi.deletePolicy, policyS.closeConfirm, policies.reload, 'Policy')} />

      {/* External audit engagement modal + confirm — §5.3 #3 */}
      <FormModal<AuditEngagementRequest> open={engageS.open}
        title={engageS.editing ? `${t('editPrefix')} · ${engageS.editing.titleEn}` : t('newEngagementTitle')}
        fields={ENGAGEMENT_FIELDS}
        initial={engageS.editing ? engagementToReq(engageS.editing) : ENGAGEMENT_EMPTY}
        onClose={engageS.closeModal} onSubmit={submitEngagement} />
      <ConfirmDialog
        open={!!engageS.confirmDel} destructive busy={busy}
        title={t('engagementConfirmDelete')}
        message={engageS.confirmDel ? `Engagement "${engageS.confirmDel.titleEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={engageS.closeConfirm}
        onConfirm={() => execDelete(engageS.confirmDel, complianceApi.deleteAuditEngagement, engageS.closeConfirm, engagements.reload, 'Engagement')} />
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
