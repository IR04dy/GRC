import { useEffect, useMemo, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  ClipboardCheck, BookOpenCheck, FileCheck2, Award, Filter, Plus, BarChart3, Building2, AlertTriangle,
  ScrollText, BookCopy, Send, CheckCheck, Archive, RotateCcw, X as XIcon, Wrench, Flag, Search,
  Briefcase, ShieldCheck, Stamp, ChevronRight, Lock, Paperclip,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { RaiseRequestButton } from '../../components/workflow/RaiseRequestButton';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { ModuleSubmissionsPanel } from '../../components/workflows/ModuleSubmissionsPanel';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { RadialGauge } from '../../components/viz/RadialGauge';
import { ragBands } from '../../lib/ragColors';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { Progress } from '../../components/ui/Progress';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Modal } from '../../components/ui/Modal';
import { AttachmentsField } from '../../components/ui/AttachmentsField';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { SEVERITY_TONE, severityLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { useLiveRefresh } from '../../lib/useLiveRefresh';
import { LivePulse } from '../../components/ui/LivePulse';
import {
  complianceApi, frameworkApi, reportingApi, evidenceApi, departmentsApi, ApiError,
  type ComplianceControlDto, type ComplianceControlRequest,
  type ComplianceAssessmentDto, type ComplianceFindingDto,
  type RegulatoryChangeDto, type RegulatoryChangeRequest,
  type AuditEngagementDto, type AuditEngagementRequest,
  type FrameworkDto, type FrameworkPolicyRowDto, type FrameworkCoverageDto, type FrameworkFindingRowDto, type FrameworkControlRowDto, type FrameworkDpiaRowDto,
  type FrameworkKriRowDto, type FrameworkRiskRowDto,
  type DepartmentDto,
} from '../../lib/api';
import { ExportMenu } from '../../components/ui/ExportMenu';
import { useToast } from '../../lib/toast';
import { useDialog } from '../../lib/dialog';
import {
  COMPLIANCE_CONTROLS, COMPLIANCE_ASSESSMENTS, COMPLIANCE_FINDINGS,
  REGULATORY_CHANGES, AUDIT_ENGAGEMENTS,
  FRAMEWORKS, FRAMEWORK_COVERAGE,
  FRAMEWORK_POLICIES_BY_ID, FRAMEWORK_FINDINGS_BY_ID, FRAMEWORK_CONTROLS_BY_ID,
  FRAMEWORK_DPIAS_BY_ID, FRAMEWORK_KRIS_BY_ID, FRAMEWORK_RISKS_BY_ID,
} from './mockCompliance';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { COMPLIANCE_WORKFLOWS } from '../../lib/mockWorkflows';
import { COMPLIANCE_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { usePromoted } from '../../lib/promotedRecords';
import { toFindingDto, toEngagementDto } from '../../lib/promotedAdapters';
import { NitaqatPanel } from '../../components/compliance/NitaqatPanel';
import { COMPLIANCE_ANCHORS } from '../../lib/moduleAnchors';

type TabId = 'overview' | 'controls' | 'internal' | 'external' | 'findings' | 'scorecard' | 'changes' | 'engagements' | 'frameworks' | 'nitaqat' | 'workflows';

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
// Expanded to cover the full set a Saudi compliance lead actually tracks:
// the original 10 + DGA (digital government), NAZAHA (anti-corruption),
// MoI (interior — physical security), MoEnergy, MoEnv (environment), GAZT
// (historical alias for ZATCA), SFDA (food/drug), SAGIA/MISA (investment),
// MoT (transport), Saudi NRC (nuclear/radiological), Diwan al-Mazalim
// (administrative judiciary), Saudi Customs (historical alias). Each row
// has an Arabic label so the regulator name reads natively in RTL.
const AUTHORITY_OPTIONS = [
  // Tier 1 — most-cited regulators
  { value: 'NCA',     label: 'NCA — National Cybersecurity Authority',           labelAr: 'NCA — الهيئة الوطنية للأمن السيبراني' },
  { value: 'SDAIA',   label: 'SDAIA — Saudi Data & AI Authority',                labelAr: 'سدايا — الهيئة السعودية للبيانات والذكاء الاصطناعي' },
  { value: 'SAMA',    label: 'SAMA — Saudi Central Bank',                        labelAr: 'ساما — البنك المركزي السعودي' },
  { value: 'CMA',     label: 'CMA — Capital Market Authority',                   labelAr: 'هيئة السوق المالية' },
  { value: 'ZATCA',   label: 'ZATCA — Zakat, Tax & Customs Authority',           labelAr: 'هيئة الزكاة والضريبة والجمارك' },
  { value: 'CITC',    label: 'CITC — Communications, Space & Technology',        labelAr: 'هيئة الاتصالات والفضاء والتقنية' },
  // Tier 2 — sector-specific + advisory
  { value: 'DGA',     label: 'DGA — Digital Government Authority',               labelAr: 'هيئة الحكومة الرقمية' },
  { value: 'NCEMA',   label: 'NCEMA — National Emergency Management Center',     labelAr: 'المركز الوطني لإدارة الكوارث والأزمات' },
  { value: 'NAZAHA',  label: 'NAZAHA — Anti-Corruption Authority',               labelAr: 'هيئة الرقابة ومكافحة الفساد (نزاهة)' },
  { value: 'MoCI',    label: 'MoCI — Ministry of Commerce',                      labelAr: 'وزارة التجارة' },
  { value: 'HRSD',    label: 'HRSD — Ministry of Human Resources',               labelAr: 'وزارة الموارد البشرية والتنمية الاجتماعية' },
  { value: 'MoH',     label: 'MoH / HCIS — Ministry of Health',                  labelAr: 'وزارة الصحة / مركز التحول الصحي' },
  { value: 'SFDA',    label: 'SFDA — Saudi Food & Drug Authority',               labelAr: 'الهيئة العامة للغذاء والدواء' },
  { value: 'MISA',    label: 'MISA — Investment Ministry',                       labelAr: 'وزارة الاستثمار' },
  { value: 'MoI',     label: 'MoI — Ministry of Interior',                       labelAr: 'وزارة الداخلية' },
  { value: 'MoT',     label: 'MoT — Ministry of Transport',                      labelAr: 'وزارة النقل والخدمات اللوجستية' },
  { value: 'MoENERGY', label: 'MoEnergy — Ministry of Energy',                   labelAr: 'وزارة الطاقة' },
  { value: 'MoENV',   label: 'MoEnv — Ministry of Environment, Water & Agric.',  labelAr: 'وزارة البيئة والمياه والزراعة' },
  { value: 'NRC',     label: 'NRC — Nuclear & Radiological Regulatory',          labelAr: 'هيئة الرقابة النووية والإشعاعية' },
  { value: 'GACA',    label: 'GACA — General Authority of Civil Aviation',        labelAr: 'الهيئة العامة للطيران المدني' },
  { value: 'GAZT',    label: 'GAZT — General Authority of Zakat & Tax (legacy)', labelAr: 'الهيئة العامة للزكاة والدخل (السابقة)' },
  { value: 'CUSTOMS', label: 'Saudi Customs (legacy)',                           labelAr: 'الجمارك السعودية (السابقة)' },
  // Catch-all
  { value: 'OTHER',   label: 'Other',                                            labelAr: 'أخرى' },
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
const CHANGE_EMPTY: RegulatoryChangeRequest = {
  sourceAuthority: 'NCA', titleEn: '', titleAr: '', citation: '',
  summaryEn: '', summaryAr: '', publishedDate: '', effectiveDate: '',
  applicability: 'UNKNOWN', impactLevel: 'UNKNOWN', impactSummary: '',
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

const TAB_IDS = ['overview', 'controls', 'internal', 'external', 'findings', 'scorecard', 'changes', 'engagements', 'frameworks', 'nitaqat', 'workflows'] as const;
function isTabId(s: string | null): s is TabId {
  return s != null && (TAB_IDS as readonly string[]).includes(s);
}

export function CompliancePage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const dialog = useDialog();
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  const initialTab = searchParams.get('tab');
  // C-4: the legacy "Policies" tab is retired (canonical register moved to
  // /policy-management). 'policies' is no longer a known TabId, so a stale
  // ?tab=policies deep link fails isTabId() and falls back to the overview.
  const [tab, setTabState] = useState<TabId>(
    isTabId(initialTab) ? initialTab : 'overview');
  function setTab(next: TabId) {
    setTabState(next);
    const qs = new URLSearchParams(searchParams);
    if (next === 'overview') qs.delete('tab'); else qs.set('tab', next);
    setSearchParams(qs, { replace: true });
  }
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<ComplianceControlDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<ComplianceControlDto | null>(null);
  const [busy, setBusy] = useState(false);
  const [docsTarget, setDocsTarget] = useState<{ entityType: string; entityId: number; label: string } | null>(null);
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);
  const [reqFor, setReqFor] = useState<ComplianceControlDto | null>(null);
  useEffect(() => { departmentsApi.list().then(setDepartments).catch(() => setDepartments([])); }, []);

  const controls    = useApiList<ComplianceControlDto>(()    => complianceApi.controls(0, 100),    COMPLIANCE_CONTROLS);
  const assessments = useApiList<ComplianceAssessmentDto>(() => complianceApi.assessments(0, 100), COMPLIANCE_ASSESSMENTS);
  const findings    = useApiList<ComplianceFindingDto>(()    => complianceApi.findings(0, 100),    COMPLIANCE_FINDINGS);
  const changes     = useApiList<RegulatoryChangeDto>(()     => complianceApi.regulatoryChanges(0, 100), REGULATORY_CHANGES);
  const engagements = useApiList<AuditEngagementDto>(()      => complianceApi.auditEngagements(0, 100), AUDIT_ENGAGEMENTS);

  // DD-75: merge intake-promoted rows into findings and engagements.
  const promotedFindings    = usePromoted('FINDING').map(toFindingDto);
  const promotedEngagements = usePromoted('ENGAGEMENT').map(toEngagementDto);
  const allFindings    = useMemo(() => [...promotedFindings, ...findings.data],       [promotedFindings, findings.data]);
  const allEngagements = useMemo(() => [...promotedEngagements, ...engagements.data], [promotedEngagements, engagements.data]);

  // Per-framework coverage for the ring cluster (playbook framework-ring hero).
  // Coverage% = implemented + half-credit for partial, over the framework's
  // control count — real data from the controls register, not a fabricated curve.
  const frameworkCoverage = useMemo(() => {
    const by = new Map<string, { total: number; impl: number; partial: number }>();
    for (const c of controls.data) {
      const fw = c.framework || 'Other';
      const e = by.get(fw) ?? { total: 0, impl: 0, partial: 0 };
      e.total++;
      if (c.status === 'IMPLEMENTED') e.impl++;
      else if (c.status === 'PARTIAL') e.partial++;
      by.set(fw, e);
    }
    return [...by.entries()]
      .map(([fw, e]) => ({ fw, pct: Math.round(((e.impl + e.partial * 0.5) / e.total) * 100), total: e.total }))
      .sort((a, b) => b.total - a.total)
      .slice(0, 6);
  }, [controls.data]);

  const changeS = useEntityState<RegulatoryChangeDto>();
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
    // C-4: the legacy "Policies" tab is retired. The policy register is now
    // canonical at /policy-management (legacy rows migrated in V232); this tab
    // is removed from the nav and a deep link to ?tab=policies redirects to the
    // overview (see the initial-tab guard above).
    { id: 'frameworks',  label: t('complianceTabFrameworks') },
    { id: 'nitaqat',     label: t('complianceTabNitaqat') },
    { id: 'engagements', label: t('complianceTabEngagements') },
    { id: 'internal',    label: t('complianceTabInternal') },
    { id: 'external',    label: t('complianceTabExternal') },
    { id: 'findings',    label: t('complianceTabFindings') },
    { id: 'scorecard',   label: t('complianceTabScorecard') },
    { id: 'workflows',   label: t('complianceTabWorkflows') ?? 'Workflows' },
  ];

  // ── Field defs for Regulatory Changes (built per-render so labels translate) ──
  // Localize the regulator dropdown so the AR name renders natively in
  // RTL. The enum value stays the same on the wire.
  const authorityOptionsLocalized = AUTHORITY_OPTIONS.map(a => ({
    value: a.value, label: lang === 'ar' ? a.labelAr : a.label,
  }));
  const CHANGE_FIELDS: FieldDef[] = [
    // 23 authorities — searchable so the user types "SAMA" / "ساما" instead
    // of scrolling.
    { kind: 'select-searchable', key: 'sourceAuthority',
      label: t('complianceFldAuthority') + ' *',
      required: true, options: authorityOptionsLocalized, span: 6,
      placeholder: lang === 'ar' ? 'اختر الجهة التنظيمية…' : 'Choose regulator…' },
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

  // ── Columns for Regulatory Changes ──
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

  // ── Submit + workflow handlers (Regulatory Changes) ──
  async function submitChange(values: RegulatoryChangeRequest) {
    try {
      if (changeS.editing) await complianceApi.updateChange(changeS.editing.id, values);
      else await complianceApi.createChange(values);
      toast.success(changeS.editing ? 'Change updated' : 'Change added');
      changeS.closeModal(); changes.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function assessApplicability(c: RegulatoryChangeDto) {
    const v = await dialog.prompt({
      title: lang === 'ar' ? 'تقييم الانطباق' : 'Assess applicability',
      label: 'APPLICABLE / PARTIAL / NOT_APPLICABLE',
      initial: 'APPLICABLE', required: true,
      validate: x => ['APPLICABLE', 'PARTIAL', 'NOT_APPLICABLE'].includes(x.trim().toUpperCase())
        ? null : (lang === 'ar' ? 'يجب أن تكون APPLICABLE أو PARTIAL أو NOT_APPLICABLE' : 'Must be APPLICABLE, PARTIAL or NOT_APPLICABLE'),
    });
    if (!v) return;
    try { await complianceApi.assessApplicability(c.id, v.trim().toUpperCase()); toast.success(lang === 'ar' ? 'تم تسجيل الانطباق' : 'Applicability recorded'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function assessImpact(c: RegulatoryChangeDto) {
    const lvl = await dialog.prompt({
      title: lang === 'ar' ? 'تقييم الأثر' : 'Assess impact',
      label: 'CRITICAL / HIGH / MEDIUM / LOW',
      initial: 'MEDIUM', required: true,
      validate: x => ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'].includes(x.trim().toUpperCase())
        ? null : (lang === 'ar' ? 'يجب أن تكون CRITICAL أو HIGH أو MEDIUM أو LOW' : 'Must be CRITICAL, HIGH, MEDIUM or LOW'),
    });
    if (!lvl) return;
    const summ = (await dialog.prompt({
      title: lang === 'ar' ? 'ملخص الأثر' : 'Impact summary',
      label: lang === 'ar' ? 'جملة أو جملتان (اختياري)' : 'One or two sentences (optional)',
      type: 'textarea',
    })) ?? '';
    try { await complianceApi.assessImpact(c.id, lvl.trim().toUpperCase(), summ); toast.success(lang === 'ar' ? 'تم تقييم الأثر' : 'Impact assessed'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function markControlsUpdated(c: RegulatoryChangeDto) {
    const n = await dialog.prompt({
      title: lang === 'ar' ? 'تحديث الضوابط' : 'Controls updated',
      label: lang === 'ar' ? 'كم عدد الضوابط التي تم تحديثها؟' : 'How many controls were updated?',
      type: 'number', initial: '1', min: 0,
    });
    if (n === null) return;
    try { await complianceApi.markControlsUpdated(c.id, Number(n) || 0); toast.success(lang === 'ar' ? 'تم تسجيل تحديث الضوابط' : 'Controls update recorded'); changes.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function closeChangeAction(c: RegulatoryChangeDto) {
    try { await complianceApi.closeChange(c.id); toast.success('Change closed'); changes.reload(); }
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
    if (!nx) { toast.danger(lang === 'ar' ? 'الارتباط في الحالة النهائية' : 'Engagement already at terminal status'); return; }
    const ok = await dialog.confirm({
      title: lang === 'ar' ? 'تقديم الارتباط' : 'Advance engagement',
      body: lang === 'ar' ? `تقديم "${e.titleEn}" إلى ${nx.replace(/_/g, ' ')}؟` : `Advance "${e.titleEn}" → ${nx.replace(/_/g, ' ')}?`,
    });
    if (!ok) return;
    try { await complianceApi.advanceAuditEngagement(e.id, nx); toast.success(`Status → ${nx}`); engagements.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Advance failed'); }
  }
  async function scheduleFollowUp(e: AuditEngagementDto) {
    const d = await dialog.prompt({
      title: lang === 'ar' ? 'جدولة المتابعة' : 'Schedule follow-up',
      label: lang === 'ar' ? 'تاريخ المتابعة (YYYY-MM-DD)' : 'Follow-up date (YYYY-MM-DD)',
      required: true,
      validate: x => /^\d{4}-\d{2}-\d{2}$/.test(x.trim()) ? null : (lang === 'ar' ? 'التنسيق يجب أن يكون YYYY-MM-DD' : 'Date must be YYYY-MM-DD'),
    });
    if (!d) return;
    try { await complianceApi.scheduleAuditFollowup(e.id, d); toast.success(lang === 'ar' ? 'تمت جدولة المتابعة' : 'Follow-up scheduled'); engagements.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Action failed'); }
  }

  // ── CAPA closure verification workflow (assessment §5.3 #6) ──────────────
  async function capaStartImpl(f: AgingFinding) {
    try { await complianceApi.capaStartImplementing(f.id); toast.success('Marked: implementing'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaOwnerTest(f: AgingFinding) {
    const r = await dialog.prompt({
      title: lang === 'ar' ? 'اختبار المالك' : 'Owner test',
      label: 'PASS / FAIL / PARTIAL', initial: 'PASS', required: true,
      validate: x => ['PASS', 'FAIL', 'PARTIAL'].includes(x.trim().toUpperCase()) ? null : (lang === 'ar' ? 'يجب أن تكون PASS أو FAIL أو PARTIAL' : 'Must be PASS, FAIL or PARTIAL'),
    });
    if (!r) return;
    const notes = (await dialog.prompt({ title: lang === 'ar' ? 'ملاحظات اختبار المالك' : 'Owner test notes', label: lang === 'ar' ? 'اختياري' : 'Optional', type: 'textarea' })) ?? '';
    try { await complianceApi.capaOwnerTest(f.id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL', notes }); toast.success(lang === 'ar' ? 'تم تسجيل اختبار المالك' : 'Owner test recorded'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaIndepTest(f: AgingFinding) {
    const r = await dialog.prompt({
      title: lang === 'ar' ? 'الاختبار المستقل' : 'Independent test',
      label: 'PASS / FAIL / PARTIAL', initial: 'PASS', required: true,
      validate: x => ['PASS', 'FAIL', 'PARTIAL'].includes(x.trim().toUpperCase()) ? null : (lang === 'ar' ? 'يجب أن تكون PASS أو FAIL أو PARTIAL' : 'Must be PASS, FAIL or PARTIAL'),
    });
    if (!r) return;
    const notes = (await dialog.prompt({ title: lang === 'ar' ? 'ملاحظات الاختبار المستقل' : 'Independent test notes', label: lang === 'ar' ? 'اختياري' : 'Optional', type: 'textarea' })) ?? '';
    try { await complianceApi.capaIndependentTest(f.id, { result: r.trim().toUpperCase() as 'PASS' | 'FAIL' | 'PARTIAL', notes }); toast.success(lang === 'ar' ? 'تم تسجيل الاختبار المستقل' : 'Independent test recorded'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaSignoff(f: AgingFinding) {
    const notes = (await dialog.prompt({ title: lang === 'ar' ? 'اعتماد التدقيق' : 'Audit sign-off', label: lang === 'ar' ? 'ملاحظات (اختياري)' : 'Notes (optional)', type: 'textarea' })) ?? '';
    try { await complianceApi.capaAuditSignoff(f.id, { notes }); toast.success(lang === 'ar' ? 'تم اعتماد التدقيق' : 'Audit signed off'); reloadAging(); findings.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function capaCloseFinding(f: AgingFinding) {
    const ok = await dialog.confirm({
      title: lang === 'ar' ? 'إغلاق الملاحظة' : 'Close finding',
      body: lang === 'ar' ? `إغلاق الملاحظة "${f.title}"؟ ستصبح مرحلة المعالجة CLOSED.` : `Close finding "${f.title}"? CAPA stage will become CLOSED.`,
      submitLabel: lang === 'ar' ? 'إغلاق' : 'Close',
    });
    if (!ok) return;
    try { await complianceApi.capaClose(f.id); toast.success(lang === 'ar' ? 'تم إغلاق الملاحظة' : 'Finding closed'); reloadAging(); findings.reload(); }
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

  // Tab-aware "New" dispatcher.
  // DD-79: route audit-engagement creation through the Services Center
  // so it gets a reference code + DPO/Audit-Lead triage. Regulatory
  // changes are admin-managed here; controls remain legacy.
  function newAction() {
    return () => {
      switch (tab) {
        case 'engagements': return navigate('/intake/audit_request');
        case 'changes':     return changeS.startCreate();
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
        extras.push({ Icon: Paperclip, onClick: () => setDocsTarget({ entityType: 'ComplianceFinding', entityId: r.id, label: lang === 'ar' ? 'أدلة الملاحظة والمرفقات' : 'Finding evidence & attachments' }), title: lang === 'ar' ? 'الأدلة' : 'Evidence' });
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
    { header: '',                 align: 'end', width: '160px',
      cell: r => <RowActions
        extras={[
          { Icon: Paperclip, onClick: () => setDocsTarget({ entityType: 'ComplianceControl', entityId: r.id, label: lang === 'ar' ? 'أدلة الضابط والمرفقات' : 'Control evidence & attachments' }), title: lang === 'ar' ? 'الأدلة' : 'Evidence' },
          { Icon: Send, onClick: () => setReqFor(r), title: lang === 'ar' ? 'طلب دليل من إدارة' : 'Request evidence from a department' },
        ]}
        onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  const assessCols: Column<ComplianceAssessmentDto>[] = [
    { header: t('colFramework'), cell: r => <Badge tone="primary">{(r.framework ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colPeriod'),    cell: r => <span style={{ fontWeight: 600 }}>{r.period}</span> },
    { header: t('colAuditor'),   cell: r => r.auditor ?? '—' },
    { header: t('colScore'),     cell: r => <ScorePillCompliance score={r.score ?? 0} /> },
    { header: t('colDate'),      cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'neutral'} dot>{statusLabel(r.status, lang)}</Badge> },
  ];

  const findingCols: Column<ComplianceFindingDto>[] = [
    { header: t('colTitle'),    cell: r => <span style={{ fontWeight: 600 }}>{r.title}</span> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity] ?? 'neutral'} dot>{severityLabel(t, r.severity)}</Badge> },
    { header: t('colOwner'),    cell: r => r.owner ?? '—' },
    { header: t('colCapa'),     cell: r => <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>{(r.capa ?? '—').slice(0, 60)}</span> },
    { header: t('colDue'),      cell: r => r.dueDate ?? '—', mono: true },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE_MAP[r.status] ?? 'neutral'} dot>{statusLabel(r.status, lang)}</Badge> },
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

  // ── Request evidence from a department (C-2) ─────────────────────────────
  // Raises a real EvidenceRequest (departmentId + dueDate) that the backend
  // EvidenceRequestSlaScheduler then chases with overdue reminders.
  const reqInitial = useMemo<Record<string, string>>(() => ({
    titleEn: reqFor ? `Evidence for control ${reqFor.code} — ${reqFor.nameEn}` : '',
    priority: 'MEDIUM',
  }), [reqFor]);
  const reqFields: FieldDef[] = [
    { kind: 'text', key: 'titleEn', required: true, span: 12,
      label: lang === 'ar' ? 'عنوان طلب الدليل *' : 'Evidence request title *' },
    { kind: 'select', key: 'departmentId', span: 6,
      label: lang === 'ar' ? 'الإدارة المسؤولة' : 'Owning department',
      options: departments.map(d => ({ value: String(d.id), label: lang === 'ar' ? (d.nameAr ?? d.nameEn) : d.nameEn })) },
    { kind: 'date', key: 'dueDate', span: 6, label: lang === 'ar' ? 'تاريخ الاستحقاق' : 'Due date' },
    { kind: 'select', key: 'priority', span: 6, label: lang === 'ar' ? 'الأولوية' : 'Priority',
      options: [
        { value: 'LOW', label: lang === 'ar' ? 'منخفضة' : 'Low' },
        { value: 'MEDIUM', label: lang === 'ar' ? 'متوسطة' : 'Medium' },
        { value: 'HIGH', label: lang === 'ar' ? 'عالية' : 'High' },
      ] },
    { kind: 'textarea', key: 'descriptionEn', span: 12, rows: 2,
      label: lang === 'ar' ? 'الوصف / ما هو الدليل المطلوب' : 'Description / what evidence is needed' },
  ];
  async function submitEvidenceRequest(v: Record<string, unknown>) {
    const titleEn = String(v.titleEn ?? '').trim();
    if (!titleEn) { toast.danger(lang === 'ar' ? 'العنوان مطلوب' : 'Title is required'); return; }
    const num = (x: unknown) => { const n = Number(x); return Number.isFinite(n) && n > 0 ? n : undefined; };
    const opt = (x: unknown) => { const s = String(x ?? '').trim(); return s || undefined; };
    try {
      await evidenceApi.createRequest({
        titleEn,
        descriptionEn: opt(v.descriptionEn),
        departmentId: num(v.departmentId),
        dueDate: opt(v.dueDate),
        priority: opt(v.priority) ?? 'MEDIUM',
      });
      toast.success(lang === 'ar' ? 'تم إنشاء طلب الدليل' : 'Evidence request created');
      setReqFor(null);
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed'); }
  }

  const frameworkScore = (fw: string) => {
    const cs = controls.data.filter(c => c.framework === fw);
    if (cs.length === 0) return 0;
    const total = cs.reduce((s, c) => s + (c.maturityLevel ?? 0), 0);
    return Math.round((total / (cs.length * 5)) * 100);
  };

  // C-7 — demo/sample-data transparency: true when ANY register fell back to
  // mock data (no auth, API error, or empty backend), not just controls — so a
  // single live register can no longer mask sample data shown on other tabs.
  const isDemo = controls.isDemo || assessments.isDemo || findings.isDemo
    || changes.isDemo || engagements.isDemo;

  // Sprint 4 §2.7 — live compliance dashboard
  const { pulse } = useLiveRefresh({
    kinds: ['changed.compliance_assessment', 'changed.audit_finding', 'changed.regulatory_change', 'changed.audit_engagement'],
    onRefresh: () => { findings.reload(); assessments.reload(); changes.reload(); engagements.reload(); },
    waitMs: 600,
    enabled: !isDemo,
  });

  return (
    <Shell title={t('modCompliance')} sub="ISO 27001 · NCA · SDAIA · SAMA">
      <PageHeader
        eyebrow={t('complianceEyebrow')} title={t('complianceTitle')} sub={t('complianceSub')}
        actions={
          <>
            <RaiseRequestButton sourceModule="COMPLIANCE" />
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            {!isDemo && <LivePulse pulse={pulse} />}
            <Button variant="secondary" Icon={Filter}
              onClick={() => window.dispatchEvent(new CustomEvent('grc:open-palette'))}>
              {t('actionFilter')}
            </Button>
            <ExportMenu
              demoMode={isDemo} t={t} toast={toast}
              onPdf={() => reportingApi.findingsPdf((typeof navigator !== 'undefined' && navigator.language?.startsWith('ar')) ? 'ar' : 'en')}
              onDocx={() => reportingApi.findingsDocx((typeof navigator !== 'undefined' && navigator.language?.startsWith('ar')) ? 'ar' : 'en')}
              onXlsx={() => reportingApi.findingsXlsx()}
            />
            {showNewButton && <Button variant="primary" Icon={Plus} onClick={newAction()}>{t('actionNew')}</Button>}
          </>
        }
      />
      <WorkflowQuickLaunch modules={['COMPLIANCE', 'GOVERNANCE']} />
      <ModuleSubmissionsPanel module={['COMPLIANCE', 'GOVERNANCE']} />
      <div className="kpi-grid">
        <Kpi Icon={BookOpenCheck}  label={t('complianceKpiFrameworks')} value={new Set(controls.data.map(c => c.framework)).size} tone="primary" onClick={() => setTab('frameworks')} />
        <Kpi Icon={ClipboardCheck} label={t('complianceKpiControls')}   value={controls.data.length} tone="success" onClick={() => setTab('controls')} />
        <Kpi Icon={FileCheck2}     label={t('complianceKpiEvidence')}   value="1,283" tone="info" onClick={() => setTab('findings')} />
        <Kpi Icon={Award}          label={t('complianceKpiScore')}      value="87%" trend={{ dir: 'up', value: '+4%' }} tone="success" onClick={() => setTab('scorecard')} />
      </div>
      <OverviewStrip
        storageKey="overview.compliance"
        processBarTitle={t('complianceProcessBarTitle')}
        processBarSubtitle={t('complianceProcessBarSub')}
        lifecycles={COMPLIANCE_LIFECYCLES}
        postureTitle={t('compliancePostureTitle')}
        anchors={COMPLIANCE_ANCHORS}
        />

      {frameworkCoverage.length > 0 && (
        <Card title={lang === 'ar' ? 'تغطية الأطر التنظيمية' : 'Framework coverage'}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--sp-8)', justifyContent: 'center', padding: 'var(--sp-4) 0' }}>
            {frameworkCoverage.map(f => (
              <button key={f.fw} type="button" onClick={() => setTab('frameworks')}
                className="card--interactive"
                style={{ background: 'none', border: 'none', cursor: 'pointer', textAlign: 'center', padding: 'var(--sp-3)', borderRadius: 'var(--r-md)' }}>
                <RadialGauge value={f.pct} max={100} size={116} bands={ragBands()} format={(v) => `${Math.round(v)}%`} />
                <div style={{ fontWeight: 700, fontSize: 13, marginTop: 2, color: 'var(--ink-1)' }} dir="ltr">{f.fw}</div>
                <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{f.total} {lang === 'ar' ? 'ضابط' : 'controls'}</div>
              </button>
            ))}
          </div>
        </Card>
      )}

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
          {controls.loading ? <LoadingRow /> : <DataTable columns={ctrlCols} rows={controls.data} rowHref={r => `/controls/${r.id}`} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'changes' && (
        <Card padded={false}>
          {changes.loading ? <LoadingRow /> : <DataTable columns={changeCols} rows={changes.data}
            empty={<EmptyState Icon={ScrollText} title="No regulatory changes tracked" sub="New laws, amendments and guidance from NCA, SDAIA, SAMA, CMA, ZATCA, CITC, NCEMA flow through here." />} />}
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
          {/* Use default .kpi-grid auto-fit instead of forcing 4 fixed cols
              at 180px min — matches the Risk page's 4-tile strip and the
              rest of the app's KPI rows. */}
          <div className="kpi-grid" style={{ marginBottom: 12 }}>
            <Kpi Icon={ClipboardCheck} label={t('agingKpiOnTrack')}        value={aging.filter(f => f.escalationTier === 'ON_TRACK').length} tone="success" to="/compliance" />
            <Kpi Icon={Flag}           label={t('agingKpiManager')}        value={aging.filter(f => f.escalationTier === 'MANAGER_ESCALATED').length} tone="info" to="/compliance" />
            <Kpi Icon={AlertTriangle}  label={t('agingKpiDirector')}       value={aging.filter(f => f.escalationTier === 'DIRECTOR_ESCALATED').length} tone="accent" to="/compliance" />
            <Kpi Icon={AlertTriangle}  label={t('agingKpiExec')}           value={aging.filter(f => f.escalationTier === 'EXEC_ESCALATED').length} tone="danger" to="/compliance" />
          </div>
          <Card padded={false}>
            {!agingLoaded ? <LoadingRow /> :
              aging.length === 0
                ? <DataTable columns={findingCols} rows={allFindings} rowHref={r => `/findings/${r.id}`} empty={<EmptyState Icon={AlertTriangle} title="No open findings" sub="Findings link audit observations to remediation owners with due dates." />} />
                : <DataTable columns={agingCols} rows={aging} rowHref={r => `/findings/${r.id}`} empty={<EmptyState />} />
            }
          </Card>
        </>
      )}
      {tab === 'engagements' && (
        <Card padded={false}>
          {engagements.loading ? <LoadingRow /> :
            <DataTable columns={engagementCols} rows={allEngagements} empty={<EmptyState Icon={Briefcase} title={t('engagementsEmptyTitle')} sub={t('engagementsEmptySub')} />} />
          }
        </Card>
      )}
      {tab === 'frameworks' && <FrameworkAnchorView t={t} toast={toast} />}
      {tab === 'nitaqat' && <NitaqatPanel />}
      {tab === 'scorecard' && (
        <div className="kpi-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}>
          {['ISO_27001','NCA_ECC','PDPL','ISO_22301','SAMA_CSF','ISO_27701'].map(fw => {
            const v = frameworkScore(fw);
            const cs = controls.data.filter(c => c.framework === fw).length;
            // Tone maps to the same threshold ladder used elsewhere — < 40 danger,
            // < 60 accent (amber), < 80 primary (teal), ≥ 80 success.
            const tone: 'success' | 'primary' | 'accent' | 'danger' =
              v >= 80 ? 'success' : v >= 60 ? 'primary' : v >= 40 ? 'accent' : 'danger';
            return (
              <Card key={fw} title={fw.replace(/_/g, ' ')} Icon={Award} action={<Badge tone="primary">{cs} controls</Badge>}>
                {/* Use the shared .stat-tile pattern so this scorecard tile reads
                 * with the same typography as the KPI strip at the top of the
                 * page — no more "this card looks different from the row above". */}
                <div className={`stat-tile stat-tile-${tone}`}>
                  <div className="stat-tile-value">{v}%</div>
                </div>
                <div style={{ height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden', margin: '8px 0 6px' }}>
                  <div style={{ width: `${v}%`, height: '100%', background: `var(--${tone === 'accent' ? 'accent' : tone === 'primary' ? 'primary' : tone === 'success' ? 'success' : 'danger'})` }} />
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

      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={COMPLIANCE_WORKFLOWS}
        title={t('complianceTabWorkflows') ?? 'Workflows'}
        sub="CAPA closure progression, regulatory-change implementation, policy approvals and audit engagements."
        t={t} />}

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

      {docsTarget && (
        <Modal open size="lg" title={docsTarget.label} onClose={() => setDocsTarget(null)}>
          <AttachmentsField entityType={docsTarget.entityType} entityId={docsTarget.entityId} label={docsTarget.label} />
        </Modal>
      )}

      <FormModal
        open={!!reqFor}
        title={lang === 'ar' ? 'طلب دليل من إدارة' : 'Request evidence from a department'}
        fields={reqFields}
        initial={reqInitial}
        size="lg"
        submitLabel={lang === 'ar' ? 'إرسال الطلب' : 'Send request'}
        onClose={() => setReqFor(null)}
        onSubmit={submitEvidenceRequest}
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
  return <InlineEmpty>{t('riskLoading')}</InlineEmpty>;
}

// ─── Framework anchor view (IA-1 — policies organised by framework sheets) ──
type SatisfactionTone = 'success' | 'primary' | 'neutral' | 'warning' | 'danger';
const SATISFACTION_TONE: Record<string, SatisfactionTone> = {
  FULL: 'success', PARTIAL: 'warning', OUT_OF_SCOPE: 'neutral', NOT_APPLICABLE: 'neutral',
};
const FAMILY_LABELS: Record<string, string> = {
  SECURITY: 'Security', PRIVACY: 'Privacy', BCM: 'Business Continuity',
  FINANCIAL: 'Financial', LABOUR: 'Labour', TAX: 'Tax', TELECOM: 'Telecom',
  ETHICS: 'Ethics & Anti-Corruption', FINCRIME: 'Financial Crime / AML',
};

function sheetTabStyle(active: boolean): React.CSSProperties {
  return {
    padding: '6px 12px', fontSize: 12, fontWeight: 600,
    background: active ? 'var(--surface)' : 'transparent',
    color: active ? 'var(--primary)' : 'var(--ink-3)',
    border: '1px solid var(--border)', borderBottom: active ? '1px solid var(--surface)' : '1px solid var(--border)',
    borderRadius: 'var(--r-sm) var(--r-sm) 0 0',
    cursor: 'pointer', position: 'relative', top: 1,
  };
}

function FrameworkAnchorView({ t, toast }: { t: (k: string) => string; toast: ReturnType<typeof useToast> }) {
  const { lang } = useI18n();
  const dialog = useDialog();
  const [frameworks, setFrameworks] = useState<FrameworkDto[]>([]);
  const [coverage, setCoverage] = useState<Record<number, FrameworkCoverageDto>>({});
  const [activeId, setActiveId] = useState<number | null>(null);
  const [sheet, setSheet] = useState<'controls' | 'policies' | 'findings' | 'dpias' | 'kris' | 'risks'>('controls');
  const [policies, setPolicies] = useState<FrameworkPolicyRowDto[]>([]);
  const [findings, setFindings] = useState<FrameworkFindingRowDto[]>([]);
  const [controls, setControls] = useState<FrameworkControlRowDto[]>([]);
  const [dpias, setDpias] = useState<FrameworkDpiaRowDto[]>([]);
  const [kris, setKris] = useState<FrameworkKriRowDto[]>([]);
  const [risks, setRisks] = useState<FrameworkRiskRowDto[]>([]);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    (async () => {
      // Per-call .catch() — coverage may be unavailable in dev DBs while the
      // catalogue is fine (and vice versa). Each call falls back to its own
      // demo slice independently so we keep partial real data on screen.
      const [fs, cov] = await Promise.all([
        frameworkApi.list().catch(() => FRAMEWORKS),
        frameworkApi.coverage().catch(() => FRAMEWORK_COVERAGE),
      ]);
      const finalFs = fs.length ? fs : FRAMEWORKS;
      setFrameworks(finalFs);
      setCoverage(Object.fromEntries(cov.map(c => [c.frameworkId, c])));
      if (finalFs.length > 0 && activeId == null) setActiveId(finalFs[0].id);
    })();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (activeId == null) return;
    setLoading(true);
    Promise.all([
      frameworkApi.policiesFor(activeId).catch(() => FRAMEWORK_POLICIES_BY_ID[activeId] ?? [] as FrameworkPolicyRowDto[]),
      frameworkApi.findingsFor(activeId).catch(() => FRAMEWORK_FINDINGS_BY_ID[activeId] ?? [] as FrameworkFindingRowDto[]),
      frameworkApi.controlsFor(activeId).catch(() => FRAMEWORK_CONTROLS_BY_ID[activeId] ?? [] as FrameworkControlRowDto[]),
      frameworkApi.dpiasFor(activeId).catch(() => FRAMEWORK_DPIAS_BY_ID[activeId] ?? [] as FrameworkDpiaRowDto[]),
      frameworkApi.krisFor(activeId).catch(() => FRAMEWORK_KRIS_BY_ID[activeId] ?? [] as FrameworkKriRowDto[]),
      frameworkApi.risksFor(activeId).catch(() => FRAMEWORK_RISKS_BY_ID[activeId] ?? [] as FrameworkRiskRowDto[]),
    ]).then(([p, f, c, d, k, r]) => { setPolicies(p); setFindings(f); setControls(c); setDpias(d); setKris(k); setRisks(r); }).finally(() => setLoading(false));
  }, [activeId]);

  async function deleteMapping(mappingId: number) {
    if (!(await dialog.confirm({ title: t('frameworkConfirmRemoveMapping'), destructive: true }))) return;
    try {
      await frameworkApi.deleteMapping(mappingId);
      toast.success(t('frameworkMappingRemoved'));
      if (activeId != null) setPolicies(await frameworkApi.policiesFor(activeId));
      const cov = await frameworkApi.coverage();
      setCoverage(Object.fromEntries(cov.map(c => [c.frameworkId, c])));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }

  // Group framework chips by family
  const byFamily = new Map<string, FrameworkDto[]>();
  for (const f of frameworks) {
    const key = f.family ?? 'OTHER';
    if (!byFamily.has(key)) byFamily.set(key, []);
    byFamily.get(key)!.push(f);
  }
  const active = frameworks.find(f => f.id === activeId) ?? null;
  const activeCov = activeId != null ? coverage[activeId] : null;
  const total = activeCov?.policyCount ?? policies.length;
  const fullPct = total > 0 ? Math.round(((activeCov?.full ?? 0) / total) * 100) : 0;

  return (
    <div className="rail-layout">
      {/* Left rail — frameworks grouped by family */}
      <Card title={t('complianceTabFrameworks')} Icon={BookOpenCheck} padded={false}>
        <div style={{ maxHeight: 540, overflowY: 'auto' }}>
          {Array.from(byFamily.entries()).map(([family, list]) => (
            <div key={family}>
              {/* Family eyebrow — reuses the canonical section-header
                * vocabulary (.text-2xs .text-muted with letter-spacing)
                * so it matches every other "uppercase metric eyebrow"
                * on the page instead of being a one-off inline style. */}
              <div className="text-2xs text-muted" style={{
                padding: '10px 12px 4px',
                fontWeight: 700,
                textTransform: 'uppercase',
                letterSpacing: '0.08em',
                background: 'var(--surface-2)',
              }}>
                {FAMILY_LABELS[family] ?? family}
              </div>
              {list.map(f => {
                const cov = coverage[f.id];
                const isActive = f.id === activeId;
                return (
                  <button key={f.id} onClick={() => setActiveId(f.id)} style={{
                    display: 'block', width: '100%', textAlign: 'start',
                    padding: '8px 12px', background: isActive ? 'var(--primary-50)' : 'transparent',
                    border: 'none', borderBottom: '1px solid var(--border)',
                    cursor: 'pointer', color: 'var(--ink-1)',
                  }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <span style={{ fontWeight: 600, fontSize: 13, flex: 1 }}>{f.code}</span>
                      <Badge tone={cov && cov.policyCount > 0 ? 'primary' : 'neutral'}>{cov?.policyCount ?? 0}</Badge>
                    </div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{f.nameEn}{f.version && ` · ${f.version}`}</div>
                  </button>
                );
              })}
            </div>
          ))}
          {frameworks.length === 0 && <InlineEmpty>{t('frameworkEmpty')}</InlineEmpty>}
        </div>
      </Card>

      {/* Right pane — active framework sheet */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
        {active && (
          <Card title={`${active.code} · ${active.nameEn}`} Icon={Award}
                action={<>
                  {active.version && <Badge tone="primary">{active.version}</Badge>}
                  {active.family   && <Badge tone="info">{FAMILY_LABELS[active.family] ?? active.family}</Badge>}
                </>}>
            {active.issuer && <div style={{ fontSize: 12, color: 'var(--ink-3)', marginBottom: 8 }}>{t('frameworkIssuer')}: {active.issuer}</div>}
            {/* 6 tiles — let the canonical .kpi-grid auto-fit handle column
                count instead of forcing 6 columns at minmax(100px). The old
                override crammed each tile to ~100px width which squeezed
                Arabic labels ("ضوابط مُطبَّقة", "نضج الإطار") against the
                box edges and made the row look distinct from every other
                page. Default 220px-minimum auto-fit gives 3-4 cols at the
                usual viewport and wraps to 2 below ~700px. */}
            <div className="kpi-grid">
              <Kpi Icon={ClipboardCheck} label={t('frameworkKpiControls')}     value={activeCov?.controlCount ?? 0} tone="primary" to="/controls/library" />
              <Kpi Icon={CheckCheck}     label={t('frameworkKpiImplemented')} value={activeCov?.implementedControls ?? 0} tone="success" to="/controls/library" />
              <Kpi Icon={Award}          label={t('frameworkKpiMaturity')}     value={typeof activeCov?.avgMaturity === 'number' ? `L${activeCov.avgMaturity}` : '—'} tone="accent" to="/controls/library" />
              <Kpi Icon={BookCopy}       label={t('frameworkKpiPolicies')}     value={total}                                   tone="info" to="/policy-management" />
              <Kpi Icon={Flag}           label={t('frameworkKpiCoverage')}     value={`${fullPct}%`}                            tone={fullPct >= 80 ? 'success' : fullPct >= 50 ? 'primary' : 'warning'} to="/compliance/posture" />
              <Kpi Icon={AlertTriangle}  label={t('frameworkKpiOpenFindings')} value={activeCov?.openFindings ?? 0} tone="danger" to="/evidence" />
            </div>
          </Card>
        )}

        <Card padded={false}>
          {/* Sheet sub-tabs: policies / findings — same artefact list, different lens */}
          <div style={{ display: 'flex', gap: 4, padding: '8px 8px 0' }}>
            <button onClick={() => setSheet('controls')} style={sheetTabStyle(sheet === 'controls')}>
              {t('complianceTabControls')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{controls.length}</span>
            </button>
            <button onClick={() => setSheet('policies')} style={sheetTabStyle(sheet === 'policies')}>
              {t('complianceTabPolicies')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{policies.length}</span>
            </button>
            <button onClick={() => setSheet('findings')} style={sheetTabStyle(sheet === 'findings')}>
              {t('complianceTabFindings')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{findings.length}</span>
            </button>
            {(activeCov?.dpiaCount ?? 0) > 0 && (
              <button onClick={() => setSheet('dpias')} style={sheetTabStyle(sheet === 'dpias')}>
                {t('frameworkSheetDpias')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{dpias.length}</span>
              </button>
            )}
            {(activeCov?.kriCount ?? 0) > 0 && (
              <button onClick={() => setSheet('kris')} style={sheetTabStyle(sheet === 'kris')}>
                {t('frameworkSheetKris')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{kris.length}</span>
              </button>
            )}
            {(activeCov?.riskCount ?? 0) > 0 && (
              <button onClick={() => setSheet('risks')} style={sheetTabStyle(sheet === 'risks')}>
                {t('frameworkSheetRisks')} <span style={{ marginInlineStart: 6, fontSize: 10, opacity: 0.7 }}>{risks.length}</span>
              </button>
            )}
          </div>
          {loading ? <LoadingRow /> : sheet === 'controls' ?
            (controls.length === 0
              ? <EmptyState Icon={ClipboardCheck} title={t('frameworkControlsEmptyTitle')} sub={t('frameworkControlsEmptySub')} />
              : (
                <DataTable<FrameworkControlRowDto>
                  columns={[
                    { header: t('fldCode'),    cell: c => <span className="mono" style={{ fontWeight: 600 }}>{c.code}</span>, width: '120px' },
                    { header: t('colControl'), cell: c => <span style={{ fontWeight: 600 }}>{c.nameEn}</span> },
                    { header: t('fldDomain'),  cell: c => c.domain ?? '—', width: '160px' },
                    { header: t('colMaturity'),cell: c => <span className="mono">L{c.maturityLevel ?? 0}</span>, width: '90px' },
                    { header: t('colOwner'),   cell: c => c.owner ?? '—', width: '160px' },
                    { header: t('colStatus'),  cell: c => <Badge tone={STATUS_TONE_MAP[c.status ?? ''] ?? 'neutral'} dot>{(c.status ?? '').replace(/_/g, ' ')}</Badge>, width: '130px' },
                  ]}
                  rows={controls}
                  empty={<EmptyState />}
                />
              )
            ) :
            sheet === 'policies' ?
            (policies.length === 0
              ? <EmptyState Icon={BookCopy} title={t('frameworkSheetEmptyTitle')} sub={t('frameworkSheetEmptySub')} />
              : (
                <DataTable<FrameworkPolicyRowDto & { id: number }>
                  columns={[
                    { header: t('fldCode'),       cell: p => <span className="mono" style={{ fontWeight: 600 }}>{p.code}</span>, width: '120px' },
                    { header: t('colTitle'),      cell: p => <span style={{ fontWeight: 600 }}>{p.titleEn}</span> },
                    { header: t('frameworkClauseRef'), cell: p => <span className="mono">{p.clauseRef ?? '—'}</span>, width: '140px' },
                    { header: t('frameworkSatisfaction'), cell: p => <Badge tone={SATISFACTION_TONE[p.satisfaction] ?? 'neutral'} dot>{p.satisfaction.replace(/_/g, ' ')}</Badge>, width: '140px' },
                    { header: t('fldVersion'),    cell: p => p.version ?? '—', mono: true, width: '90px' },
                    { header: t('colStatus'),     cell: p => <Badge tone={p.status === 'PUBLISHED' ? 'success' : p.status === 'RETIRED' ? 'neutral' : 'info'} dot>{(p.status ?? '').replace(/_/g, ' ')}</Badge>, width: '130px' },
                    { header: t('fldNextReviewDate'), cell: p => p.nextReviewDate ?? '—', mono: true, width: '120px' },
                    { header: '', align: 'end', width: '60px',
                      cell: p => (
                        <button onClick={() => deleteMapping(p.mappingId)} title={t('frameworkRemoveMapping')}
                                style={{ background: 'transparent', border: 'none', cursor: 'pointer', color: 'var(--danger-fg)', padding: 4 }}>
                          <XIcon size={14} />
                        </button>
                      ) },
                  ]}
                  rows={policies.map(p => ({ ...p, id: p.mappingId }))}
                  empty={<EmptyState />}
                />
              )
            ) : sheet === 'findings' ? (
              findings.length === 0
                ? <EmptyState Icon={AlertTriangle} title={t('frameworkFindingsEmptyTitle')} sub={t('frameworkFindingsEmptySub')} />
                : (
                  <DataTable<FrameworkFindingRowDto>
                    columns={[
                      { header: t('colTitle'),    cell: f => <span style={{ fontWeight: 600 }}>{f.title}</span> },
                      { header: t('frameworkClauseRef'), cell: f => <span className="mono">{f.clauseRef ?? '—'}</span>, width: '140px' },
                      { header: t('colSeverity'), cell: f => <Badge tone={SEVERITY_TONE[f.severity] ?? 'neutral'} dot>{severityLabel(t, f.severity)}</Badge>, width: '110px' },
                      { header: t('colOwner'),    cell: f => f.owner ?? '—', width: '160px' },
                      { header: t('colCapaStage'), cell: f => f.capaStage ? <Badge tone="info">{f.capaStage.replace(/_/g, ' ')}</Badge> : '—', width: '160px' },
                      { header: t('colDue'),      cell: f => f.dueDate ?? '—', mono: true, width: '110px' },
                      { header: t('colStatus'),   cell: f => <Badge tone={f.status === 'CLOSED' ? 'success' : 'warning'} dot>{f.status}</Badge>, width: '110px' },
                    ]}
                    rows={findings}
                    empty={<EmptyState />}
                  />
                )
            ) : sheet === 'dpias' ? (
              dpias.length === 0
                ? <EmptyState Icon={Search} title={t('frameworkDpiasEmptyTitle')} sub={t('frameworkDpiasEmptySub')} />
                : (
                  <DataTable<FrameworkDpiaRowDto>
                    columns={[
                      { header: t('colTitle'),    cell: d => <span style={{ fontWeight: 600 }}>{d.titleEn}</span> },
                      { header: t('frameworkClauseRef'), cell: d => <span className="mono">{d.clauseRef ?? '—'}</span>, width: '120px' },
                      { header: t('dpiaTrigger'), cell: d => d.triggerReason ? <Badge tone="info">{d.triggerReason.replace(/_/g, ' ')}</Badge> : '—', width: '180px' },
                      { header: t('dpiaResidualRisk'), cell: d => d.overallResidualRisk ? <Badge tone={RISK_TONE_BADGE[d.overallResidualRisk] ?? 'neutral'} dot>{d.overallResidualRisk}</Badge> : '—', width: '120px' },
                      { header: t('colDate'),     cell: d => d.assessmentDate ?? '—', mono: true, width: '110px' },
                      { header: t('fldNextReviewDate'), cell: d => d.nextReviewDate ?? '—', mono: true, width: '120px' },
                      { header: t('colStatus'),   cell: d => <Badge tone={d.status === 'APPROVED' ? 'success' : d.status === 'REJECTED' ? 'danger' : 'info'} dot>{statusLabel(d.status, lang)}</Badge>, width: '120px' },
                    ]}
                    rows={dpias}
                    empty={<EmptyState />}
                  />
                )
            ) : sheet === 'kris' ? (
              kris.length === 0
                ? <EmptyState Icon={Award} title={t('frameworkKrisEmptyTitle')} sub={t('frameworkKrisEmptySub')} />
                : (
                  <DataTable<FrameworkKriRowDto>
                    columns={[
                      { header: t('fldCode'),    cell: k => <span className="mono" style={{ fontWeight: 600 }}>{k.code ?? '—'}</span>, width: '120px' },
                      { header: t('colTitle'),   cell: k => <span style={{ fontWeight: 600 }}>{k.nameEn}</span> },
                      { header: t('frameworkClauseRef'), cell: k => <span className="mono">{k.clauseRef ?? '—'}</span>, width: '140px' },
                      { header: t('kriDirection'), cell: k => <Badge tone={k.direction === 'HIGHER_IS_WORSE' ? 'danger' : 'success'}>{k.direction?.replace(/_/g, ' ')}</Badge>, width: '160px' },
                      { header: t('kriThresholds'), cell: k => <span className="mono">{k.greenMax ?? '—'} / {k.amberMax ?? '—'} {k.unit ?? ''}</span>, width: '160px' },
                      { header: t('kriFrequency'), cell: k => k.frequency ?? '—', width: '100px' },
                      { header: t('colOwner'),   cell: k => k.owner ?? '—', width: '180px' },
                    ]}
                    rows={kris}
                    empty={<EmptyState />}
                  />
                )
            ) : (
              risks.length === 0
                ? <EmptyState Icon={Search} title={t('frameworkRisksEmptyTitle')} sub={t('frameworkRisksEmptySub')} />
                : (
                  <DataTable<FrameworkRiskRowDto>
                    columns={[
                      { header: t('colTitle'),   cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
                      { header: t('riskMatchedAs'), cell: r => (
                          <div style={{ display: 'flex', gap: 4 }}>
                            {r.matchedAsRiskFramework       && <Badge tone="primary">{t('riskFwBadge')}</Badge>}
                            {r.matchedAsComplianceFramework && <Badge tone="accent">{t('complianceFwBadge')}</Badge>}
                          </div>
                        ), width: '170px' },
                      { header: t('frameworkClauseRef'), cell: r => <span className="mono">{r.matchedAsRiskFramework ? (r.riskFrameworkClause ?? '—') : (r.complianceFrameworkClause ?? '—')}</span>, width: '140px' },
                      { header: t('riskColInherent'), cell: r => <span className="mono">{r.inherentScore ?? '—'}</span>, width: '110px' },
                      { header: t('riskColResidual'), cell: r => <span className="mono">{r.residualScore ?? '—'}</span>, width: '110px' },
                      { header: t('colStatus'),   cell: r => <Badge tone={r.status === 'CLOSED' || r.status === 'TREATED' ? 'success' : r.status === 'ACCEPTED' ? 'accent' : 'warning'} dot>{r.status?.replace(/_/g, ' ') ?? '—'}</Badge>, width: '130px' },
                    ]}
                    rows={risks}
                    empty={<EmptyState />}
                  />
                )
            )
          }
        </Card>
      </div>
    </div>
  );
}
