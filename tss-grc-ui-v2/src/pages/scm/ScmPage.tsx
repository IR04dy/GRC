import { useEffect, useState } from 'react';
import { InlineEmpty } from '../../components/ui/InlineEmpty';
import {
  Truck, AlertTriangle, ListChecks, Star, Filter, Download, Plus, BarChart3,
  Workflow, FileSignature, ClipboardCheck, Network, ShieldAlert,
  ArrowRight, Check, X as XIcon, AlertOctagon, Wrench, Flag, Power,
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
import { Stepper } from '../../components/ui/Stepper';
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import { FilterModal, distinctValues, type FilterField } from '../../components/ui/FilterModal';
import {
  scmApi, ApiError,
  type ScmVendorDto, type ScmVendorRequest,
  type ScmOnboardingDto, type ScmOnboardingRequest,
  type ScmAssessmentDto, type ScmAssessmentRequest,
  type ScmDependencyDto, type ScmDependencyRequest,
  type ScmIncidentDto, type ScmIncidentRequest,
  type ScmContractDto, type ScmContractRequest,
  type ScmStats,
} from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { SUPPLIERS, ONBOARDING, ASSESSMENTS, DEPENDENCIES, CONTRACTS, DISRUPTIONS } from './mockScm';
import { SCM_LIFECYCLES } from '../../lib/moduleLifecycles';
import { TierMapPanel } from './TierMapPanel';
import { SUPPLY_CHAIN_TIERS } from './tierMap';
import { Layers } from 'lucide-react';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { WorkflowQuickLaunch } from '../../components/workflows/WorkflowQuickLaunch';
import { SCM_ANCHORS } from '../../lib/moduleAnchors';

type TabId = 'overview' | 'suppliers' | 'onboarding' | 'workflow' | 'assessments' | 'dependencies' | 'contracts' | 'disruptions';

// ── Static option lists ───────────────────────────────────────────────────
// IMPORTANT: every option `value` below is constrained at the DB level by a
// CHECK constraint. The canonical allowed sets are documented at the top of
// `tss-grc-backend/src/main/resources/db/seed/V_scm_seed.sql`. Do not add new
// values here without first migrating the CHECK constraint, or POSTs will be
// rejected by SQL Server with a 500.
const ONBOARDING_STAGES = [
  { id: 'REGISTRATION',               label: 'Registration' },
  { id: 'CRITICALITY_CLASSIFICATION', label: 'Classification' },
  { id: 'SECURITY_REVIEW',            label: 'Security' },
  { id: 'OPERATIONAL_REVIEW',         label: 'Operations' },
  { id: 'LEGAL_REVIEW',               label: 'Legal' },
  { id: 'APPROVAL',                   label: 'Approval' },
];
const STAGE_OPTIONS = ONBOARDING_STAGES.map(s => ({ value: s.id, label: s.label }));

// Vendor category taxonomy — expanded from the original 7 to cover the
// supplier mix a Saudi enterprise (banking, telecom, energy, healthcare,
// government, retail) actually procures from. Aligned with MoCI sector
// classification + ZATCA tax categories so a procurement officer can pick
// without having to invent "OTHER" for half their spend.
const CATEGORY_OPTIONS = [
  // Technology
  { value: 'IT_SERVICES',        label: 'IT Services' },
  { value: 'CLOUD_SAAS',         label: 'Cloud / SaaS' },
  { value: 'CYBERSECURITY',      label: 'Cybersecurity Services' },
  { value: 'TELECOM',            label: 'Telecom / Connectivity' },
  { value: 'HARDWARE_DEVICES',   label: 'Hardware & Devices' },
  { value: 'SOFTWARE_LICENSING', label: 'Software Licensing' },
  // Industrial & operations
  { value: 'MANUFACTURING',      label: 'Manufacturing' },
  { value: 'LOGISTICS',          label: 'Logistics & Shipping' },
  { value: 'CONSTRUCTION',       label: 'Construction & Engineering' },
  { value: 'ENERGY',             label: 'Energy & Oil/Gas' },
  { value: 'UTILITIES',          label: 'Utilities (Power / Water)' },
  { value: 'FACILITIES_MGMT',    label: 'Facilities Management' },
  { value: 'SECURITY_GUARDING',  label: 'Physical Security / Guarding' },
  { value: 'TRANSPORTATION',     label: 'Transportation' },
  // Professional services
  { value: 'CONSULTING',         label: 'Management Consulting' },
  { value: 'LEGAL',              label: 'Legal & Compliance Advisory' },
  { value: 'AUDIT_ASSURANCE',    label: 'Audit & Assurance' },
  { value: 'STAFFING_HR',        label: 'Staffing / Outsourced HR' },
  { value: 'MARKETING_ADVERTISING', label: 'Marketing & Advertising' },
  { value: 'TRAINING',           label: 'Training & Development' },
  // Finance / insurance
  { value: 'FINANCIAL',          label: 'Financial Services' },
  { value: 'INSURANCE',          label: 'Insurance' },
  { value: 'PAYMENT_PROCESSING', label: 'Payment Processing' },
  // Public-facing
  { value: 'HEALTHCARE',         label: 'Healthcare Services' },
  { value: 'EDUCATION',          label: 'Education' },
  { value: 'REAL_ESTATE',        label: 'Real Estate' },
  { value: 'HOSPITALITY',        label: 'Hospitality & Catering' },
  { value: 'PRINTING_PUBLISHING', label: 'Printing & Publishing' },
  // Catch-all
  { value: 'OTHER',              label: 'Other' },
];
// vendor.criticality + supplier_incidents.impact ∈ {CRITICAL,HIGH,MEDIUM,LOW}
const CRITICALITY_OPTIONS = [
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
// vendors.risk_level + dependencies.dependency_level ∈ {LOW,MEDIUM,HIGH} only
const RISK_LEVEL_OPTIONS = [
  { value: 'HIGH',   label: 'High' },
  { value: 'MEDIUM', label: 'Medium' },
  { value: 'LOW',    label: 'Low' },
];
// vendor.status ∈ {ACTIVE, INACTIVE, BLACKLISTED, UNDER_REVIEW, EXITED}
const STATUS_OPTIONS = [
  { value: 'ACTIVE',       label: 'Active' },
  { value: 'INACTIVE',     label: 'Inactive' },
  { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'BLACKLISTED',  label: 'Blacklisted' },
  { value: 'EXITED',       label: 'Exited' },
];
// dep.status ∈ {ACTIVE, INACTIVE, AT_RISK} — narrower than vendor.status
const DEP_STATUS_OPTIONS = [
  { value: 'ACTIVE',   label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
  { value: 'AT_RISK',  label: 'At Risk' },
];
// onboarding.status ∈ {IN_PROGRESS, COMPLETED, REJECTED, ON_HOLD}
const ONB_STATUS_OPTIONS = [
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED',   label: 'Completed' },
  { value: 'REJECTED',    label: 'Rejected' },
  { value: 'ON_HOLD',     label: 'On Hold' },
];
// assess.type ∈ {INITIAL, PERIODIC, TRIGGERED}
const ASSESSMENT_TYPE_OPTIONS = [
  { value: 'INITIAL',   label: 'Initial' },
  { value: 'PERIODIC',  label: 'Periodic' },
  { value: 'TRIGGERED', label: 'Event-triggered' },
];
// assess.status ∈ {PLANNED, IN_PROGRESS, COMPLETED, ESCALATED}
const ASSESS_STATUS_OPTIONS = [
  { value: 'PLANNED',     label: 'Planned' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED',   label: 'Completed' },
  { value: 'ESCALATED',   label: 'Escalated' },
];
// dep.type ∈ {CRITICAL, NON_CRITICAL, SHARED, EXCLUSIVE}
const DEPENDENCY_TYPE_OPTIONS = [
  { value: 'CRITICAL',     label: 'Critical' },
  { value: 'NON_CRITICAL', label: 'Non-Critical' },
  { value: 'SHARED',       label: 'Shared' },
  { value: 'EXCLUSIVE',    label: 'Exclusive' },
];
const DEPENDENCY_LEVEL_OPTIONS = RISK_LEVEL_OPTIONS;  // dep.dependency_level ∈ {LOW,MEDIUM,HIGH}
// contract.type ∈ {SERVICE, SUPPLY, NDA, FRAMEWORK, OTHER}
const CONTRACT_TYPE_OPTIONS = [
  { value: 'SERVICE',   label: 'Service Agreement' },
  { value: 'SUPPLY',    label: 'Supply Agreement' },
  { value: 'NDA',       label: 'NDA' },
  { value: 'FRAMEWORK', label: 'Framework Agreement' },
  { value: 'OTHER',     label: 'Other' },
];
// contract.status ∈ {ACTIVE, EXPIRED, TERMINATED, DRAFT, UNDER_REVIEW}
const CONTRACT_STATUS_OPTIONS = [
  { value: 'ACTIVE',       label: 'Active' },
  { value: 'DRAFT',        label: 'Draft' },
  { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'EXPIRED',      label: 'Expired' },
  { value: 'TERMINATED',   label: 'Terminated' },
];
const CURRENCY_OPTIONS = [
  { value: 'SAR', label: 'SAR' }, { value: 'USD', label: 'USD' },
  { value: 'EUR', label: 'EUR' }, { value: 'AED', label: 'AED' },
];
// inc.type ∈ {SERVICE_DISRUPTION, BREACH, DELAY, QUALITY, SLA_BREACH, OTHER}
const INCIDENT_TYPE_OPTIONS = [
  { value: 'SERVICE_DISRUPTION', label: 'Service Disruption' },
  { value: 'BREACH',             label: 'Breach' },
  { value: 'DELAY',              label: 'Delay' },
  { value: 'QUALITY',            label: 'Quality Defect' },
  { value: 'SLA_BREACH',         label: 'SLA Breach' },
  { value: 'OTHER',              label: 'Other' },
];

const RISK_TONE: Record<string, 'danger' | 'warning' | 'info' | 'success'> = {
  CRITICAL: 'danger', HIGH: 'warning', MEDIUM: 'info', LOW: 'success',
};

// ── Empty defaults (every default value must satisfy the matching DB CHECK) ─
const SUPPLIER_EMPTY: ScmVendorRequest = {
  nameEn: '', nameAr: '', category: 'IT_SERVICES', criticality: 'MEDIUM',
  country: 'Saudi Arabia', serviceType: '', riskLevel: 'MEDIUM', status: 'ACTIVE',
  contactName: '', contactEmail: '', contactPhone: '',
  registrationNumber: '', website: '', description: '',
};
const ONBOARDING_EMPTY: ScmOnboardingRequest = {
  vendorId: 0, currentStage: 'REGISTRATION', status: 'IN_PROGRESS',
  assignedToId: null, dueDate: '', notes: '',
};
const ASSESSMENT_EMPTY: ScmAssessmentRequest = {
  vendorId: 0, assessmentType: 'INITIAL', assessmentDate: '', nextAssessmentDate: '',
  overallScore: null, riskRating: 'MEDIUM', status: 'PLANNED',
  findings: '', recommendations: '',
};
const DEPENDENCY_EMPTY: ScmDependencyRequest = {
  serviceNameEn: '', serviceNameAr: '', assetName: '',
  vendorId: null, dependencyType: 'CRITICAL', dependencyLevel: 'MEDIUM',
  hasAlternative: false, alternativeVendorId: null,
  status: 'ACTIVE', impactAnalysis: '',
};
const CONTRACT_EMPTY: ScmContractRequest = {
  vendorId: 0, contractNumber: '', titleEn: '', titleAr: '',
  contractType: 'SERVICE', startDate: '', endDate: '',
  autoRenewal: false, contractValue: null, currency: 'SAR',
  status: 'ACTIVE', notes: '',
};
const INCIDENT_EMPTY: ScmIncidentRequest = {
  vendorId: 0, incidentType: 'SERVICE_DISRUPTION', impact: 'MEDIUM',
  description: '', slaBreached: false, assignedToId: null,
};

// Generic per-entity state hook (mirrors the BCM/Cyber pattern)
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

// ── Mappers from DTO to Request ───────────────────────────────────────────
function vendorToReq(v: ScmVendorDto): ScmVendorRequest {
  return {
    nameEn: v.nameEn, nameAr: v.nameAr,
    category: v.category, criticality: v.criticality,
    country: v.country, serviceType: v.serviceType,
    riskLevel: v.riskLevel, status: v.status,
    contactName: v.contactName, contactEmail: v.contactEmail, contactPhone: v.contactPhone,
    registrationNumber: v.registrationNumber, website: v.website, description: v.description,
  };
}
function onbToReq(o: ScmOnboardingDto): ScmOnboardingRequest {
  return {
    vendorId: o.vendorId, currentStage: o.currentStage,
    status: o.status, dueDate: o.dueDate, notes: o.notes ?? '',
  };
}
function assessToReq(a: ScmAssessmentDto): ScmAssessmentRequest {
  return {
    vendorId: a.vendorId, assessmentType: a.assessmentType,
    assessmentDate: a.assessmentDate, nextAssessmentDate: a.nextAssessmentDate,
    overallScore: a.overallScore ?? null, riskRating: a.riskRating, status: a.status,
    findings: a.findings, recommendations: a.recommendations,
  };
}
function depToReq(d: ScmDependencyDto): ScmDependencyRequest {
  return {
    serviceNameEn: d.serviceNameEn, serviceNameAr: d.serviceNameAr,
    assetName: d.assetName, vendorId: d.vendorId ?? null,
    dependencyType: d.dependencyType, dependencyLevel: d.dependencyLevel,
    hasAlternative: !!d.hasAlternative, alternativeVendorId: d.alternativeVendorId ?? null,
    status: d.status, impactAnalysis: d.impactAnalysis,
  };
}
function contractToReq(c: ScmContractDto): ScmContractRequest {
  return {
    vendorId: c.vendorId, contractNumber: c.contractNumber,
    titleEn: c.titleEn, titleAr: c.titleAr,
    contractType: c.contractType, startDate: c.startDate, endDate: c.endDate,
    autoRenewal: !!c.autoRenewal, contractValue: c.contractValue ?? null,
    currency: c.currency, status: c.status, notes: c.notes,
  };
}

// ── Component ─────────────────────────────────────────────────────────────
export function ScmPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [busy, setBusy] = useState(false);

  const supS = useEntityState<ScmVendorDto>();
  const onbS = useEntityState<ScmOnboardingDto>();
  const asmS = useEntityState<ScmAssessmentDto>();
  const depS = useEntityState<ScmDependencyDto>();
  const conS = useEntityState<ScmContractDto>();
  const incS = useEntityState<ScmIncidentDto>();

  const vendors      = useApiList<ScmVendorDto>(()      => scmApi.vendors(0, 200),      SUPPLIERS);
  const onboarding   = useApiList<ScmOnboardingDto>(()  => scmApi.onboarding(0, 200),   ONBOARDING);
  const assessments  = useApiList<ScmAssessmentDto>(()  => scmApi.assessments(0, 200),  ASSESSMENTS);
  const dependencies = useApiList<ScmDependencyDto>(()  => scmApi.dependencies(0, 200), DEPENDENCIES);
  const contracts    = useApiList<ScmContractDto>(()    => scmApi.contracts(0, 200),    CONTRACTS);
  const incidents    = useApiList<ScmIncidentDto>(()    => scmApi.incidents(0, 200),    DISRUPTIONS);

  // ── DD-48: filter modal — tab-aware ─────────────────────────────────
  const [filterOpen, setFilterOpen] = useState(false);
  const [advFilters, setAdvFilters] = useState<Record<string, string | undefined>>({});
  function scmMatches(row: any): boolean {
    for (const [k, v] of Object.entries(advFilters)) {
      if (!v) continue;
      const cell = (row[k] ?? '').toString();
      if (k.includes('contains') || k === 'assignee' || k === 'assessor') {
        if (!cell.toLowerCase().includes(v.toLowerCase())) return false;
      } else if (cell !== v) return false;
    }
    return true;
  }
  const scmDataset = (() => {
    switch (tab) {
      case 'suppliers':    return vendors.data;
      case 'onboarding':   return onboarding.data;
      case 'assessments':  return assessments.data;
      case 'dependencies': return dependencies.data;
      case 'contracts':    return contracts.data;
      case 'disruptions':  return incidents.data;
      default:             return vendors.data;
    }
  })();
  const filteredScm = scmDataset.filter(scmMatches);
  const scmFilterFields: FilterField[] = (() => {
    switch (tab) {
      case 'suppliers': return [
        { key: 'criticality', label: 'Criticality',                kind: 'select', options: distinctValues(vendors.data, 'criticality') },
        { key: 'status',      label: t('filterFieldStatus'),       kind: 'select', options: distinctValues(vendors.data, 'status') },
        { key: 'category',    label: t('filterFieldCategory'),     kind: 'select', options: distinctValues(vendors.data, 'category') },
        { key: 'country',     label: t('filterFieldCountry'),      kind: 'select', options: distinctValues(vendors.data, 'country') },
      ];
      case 'onboarding': return [
        { key: 'status',       label: t('filterFieldStatus'), kind: 'select', options: distinctValues(onboarding.data, 'status') },
        { key: 'currentStage', label: 'Stage',                kind: 'select', options: distinctValues(onboarding.data, 'currentStage') },
      ];
      case 'assessments': return [
        { key: 'status',          label: t('filterFieldStatus'), kind: 'select', options: distinctValues(assessments.data, 'status') },
        { key: 'assessmentType',  label: t('filterFieldType'),   kind: 'select', options: distinctValues(assessments.data, 'assessmentType') },
      ];
      case 'dependencies': return [
        { key: 'dependencyType',  label: t('filterFieldType'),   kind: 'select', options: distinctValues(dependencies.data, 'dependencyType') },
        { key: 'dependencyLevel', label: 'Level',                kind: 'select', options: distinctValues(dependencies.data, 'dependencyLevel') },
        { key: 'status',          label: t('filterFieldStatus'), kind: 'select', options: distinctValues(dependencies.data, 'status') },
      ];
      case 'contracts': return [
        { key: 'status',       label: t('filterFieldStatus'), kind: 'select', options: distinctValues(contracts.data, 'status') },
        { key: 'contractType', label: t('filterFieldType'),   kind: 'select', options: distinctValues(contracts.data, 'contractType') },
      ];
      case 'disruptions': return [
        { key: 'impact', label: t('filterFieldSeverity'), kind: 'select', options: distinctValues(incidents.data, 'impact') },
        { key: 'status', label: t('filterFieldStatus'),   kind: 'select', options: distinctValues(incidents.data, 'status') },
        { key: 'incidentType', label: t('filterFieldType'), kind: 'select', options: distinctValues(incidents.data, 'incidentType') },
      ];
      default: return [
        { key: 'status', label: t('filterFieldStatus'), kind: 'select', options: distinctValues(vendors.data, 'status') },
      ];
    }
  })();

  const [stats, setStats] = useState<ScmStats | null>(null);
  useEffect(() => {
    if (!isAuthenticated()) {
      // Demo mode — surface derived counts so KPIs and overview cards never
      // read 0 just because we have no backend.
      setStats({
        totalVendors: SUPPLIERS.length,
        criticalVendors: SUPPLIERS.filter(v => v.criticality === 'CRITICAL' || v.criticality === 'HIGH').length,
        activeVendors: SUPPLIERS.filter(v => v.status === 'ACTIVE').length,
        pendingOnboarding: ONBOARDING.filter(o => o.status === 'IN_PROGRESS' || o.status === 'ON_HOLD').length,
        totalDependencies: DEPENDENCIES.length,
        highDependencies: DEPENDENCIES.filter(d => (d.dependencyLevel ?? '').toUpperCase() === 'HIGH').length,
        openIncidents: DISRUPTIONS.filter(i => i.status !== 'CLOSED' && i.status !== 'RESOLVED').length,
        slaBreaches: DISRUPTIONS.filter(i => i.slaBreached).length,
        activeContracts: CONTRACTS.filter(c => (c.status ?? 'ACTIVE') !== 'EXPIRED').length,
        expiringContracts: CONTRACTS.filter(c => c.status === 'EXPIRING_SOON').length,
      } as ScmStats);
      return;
    }
    scmApi.stats().then(setStats).catch(() => setStats(null));
  }, []);

  const supplierOptions = [
    { value: '', label: t('selectPlaceholder') },
    ...vendors.data.map(v => ({ value: String(v.id), label: v.nameEn })),
  ];

  // ── Field defs (rebuilt per render so labels translate) ─────────────────
  const SUPPLIER_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'nameEn',          label: `${t('fldNameEnLbl')} *`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',   key: 'nameAr',          label: t('fldNameArLbl'), maxLength: 200, span: 6 },
    // 30 categories — long enough that scanning is slow; typeahead helps.
    { kind: 'select-searchable', key: 'category', label: t('fldCategory'),
      options: CATEGORY_OPTIONS, span: 6,
      placeholder: 'Search category…' },
    { kind: 'select', key: 'criticality',     label: t('fldCriticality'), options: CRITICALITY_OPTIONS, span: 6 },
    { kind: 'text',   key: 'country',         label: t('fldCountry'), span: 4 },
    { kind: 'text',   key: 'serviceType',     label: t('scmFldServiceType'), span: 4 },
    { kind: 'select', key: 'riskLevel',       label: t('scmFldRiskLevel'), options: RISK_LEVEL_OPTIONS, span: 4 },
    { kind: 'select', key: 'status',          label: t('fldStatusLbl'), options: STATUS_OPTIONS, span: 6 },
    { kind: 'text',   key: 'registrationNumber', label: t('scmFldRegistrationNo'), span: 6 },
    { kind: 'text',   key: 'website',         label: t('scmFldWebsite'), span: 12 },
    { kind: 'text',   key: 'contactName',     label: t('fldContactName'), span: 4 },
    { kind: 'text',   key: 'contactEmail',    label: t('fldEmailContact'), span: 4 },
    { kind: 'text',   key: 'contactPhone',    label: t('fldPhone'), span: 4 },
    { kind: 'textarea', key: 'description',   label: t('scmFldDescription'), rows: 2, span: 12 },
  ];
  const ONBOARDING_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'vendorId',     label: `${t('colVendor')} *`, required: true, options: supplierOptions, span: 12 },
    { kind: 'select', key: 'currentStage', label: t('scmFldOnboardingStage'), options: STAGE_OPTIONS, span: 6 },
    { kind: 'select', key: 'status',       label: t('fldStatusLbl'), options: ONB_STATUS_OPTIONS, span: 6 },
    { kind: 'date',   key: 'dueDate',      label: t('scmFldDueDate'), span: 6 },
    { kind: 'textarea', key: 'notes',      label: t('scmFldNotes'), rows: 3, span: 12 },
  ];
  const ASSESSMENT_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'vendorId',           label: `${t('colVendor')} *`, required: true, options: supplierOptions, span: 12 },
    { kind: 'select', key: 'assessmentType',     label: t('scmFldAssessmentType'), options: ASSESSMENT_TYPE_OPTIONS, span: 6 },
    { kind: 'select', key: 'status',             label: t('fldStatusLbl'), options: ASSESS_STATUS_OPTIONS, span: 6 },
    { kind: 'date',   key: 'assessmentDate',     label: t('fldAssessmentDate'), span: 6 },
    { kind: 'date',   key: 'nextAssessmentDate', label: t('scmFldNextAssessment'), span: 6 },
    { kind: 'number', key: 'overallScore',       label: t('scmFldOverallScore'), min: 0, max: 10, span: 6 },
    { kind: 'select', key: 'riskRating',         label: t('scmFldRiskRating'), options: CRITICALITY_OPTIONS, span: 6 },
    { kind: 'textarea', key: 'findings',         label: t('scmFldFindings'), rows: 3, span: 12 },
    { kind: 'textarea', key: 'recommendations',  label: t('scmFldRecommendations'), rows: 3, span: 12 },
  ];
  const DEPENDENCY_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'serviceNameEn',     label: `${t('scmFldServiceName')} *`, required: true, span: 6 },
    { kind: 'text',   key: 'serviceNameAr',     label: `${t('scmFldServiceName')} (AR)`, span: 6 },
    { kind: 'text',   key: 'assetName',         label: t('scmFldAssetName'), span: 6 },
    { kind: 'select', key: 'vendorId',          label: t('colVendor'), options: supplierOptions, span: 6 },
    { kind: 'select', key: 'dependencyType',    label: t('scmFldDependencyType'),  options: DEPENDENCY_TYPE_OPTIONS,  span: 6 },
    { kind: 'select', key: 'dependencyLevel',   label: t('scmFldDependencyLevel'), options: DEPENDENCY_LEVEL_OPTIONS, span: 6 },
    { kind: 'select', key: 'hasAlternative',    label: t('scmFldHasAlternative'),
      options: [{ value: 'false', label: t('selectNone') }, { value: 'true', label: 'Yes' }], span: 6 },
    { kind: 'select', key: 'alternativeVendorId', label: t('scmFldAlternativeVendor'), options: supplierOptions, span: 6 },
    { kind: 'select', key: 'status',            label: t('fldStatusLbl'), options: DEP_STATUS_OPTIONS, span: 6 },
    { kind: 'textarea', key: 'impactAnalysis',  label: t('scmFldImpactAnalysis'), rows: 3, span: 12 },
  ];
  const CONTRACT_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'vendorId',       label: `${t('colVendor')} *`, required: true, options: supplierOptions, span: 12 },
    { kind: 'text',   key: 'contractNumber', label: t('scmFldContractNumber'), span: 6 },
    { kind: 'select', key: 'contractType',   label: t('scmFldContractType'), options: CONTRACT_TYPE_OPTIONS, span: 6 },
    { kind: 'text',   key: 'titleEn',        label: `${t('fldTitleEnLbl')} *`, required: true, maxLength: 200, span: 6 },
    { kind: 'text',   key: 'titleAr',        label: t('fldTitleArLbl'), maxLength: 200, span: 6 },
    { kind: 'date',   key: 'startDate',      label: t('scmFldStartDate'), span: 4 },
    { kind: 'date',   key: 'endDate',        label: t('scmFldEndDate'),   span: 4 },
    { kind: 'select', key: 'status',         label: t('fldStatusLbl'), options: CONTRACT_STATUS_OPTIONS, span: 4 },
    { kind: 'number', key: 'contractValue',  label: t('scmFldContractValue'), min: 0, span: 6 },
    { kind: 'select', key: 'currency',       label: t('scmFldCurrency'), options: CURRENCY_OPTIONS, span: 3 },
    { kind: 'select', key: 'autoRenewal',    label: t('scmFldAutoRenewal'),
      options: [{ value: 'false', label: t('badgeManual') }, { value: 'true', label: t('badgeAuto') }], span: 3 },
    { kind: 'textarea', key: 'notes',        label: t('scmFldNotes'), rows: 2, span: 12 },
  ];
  const INCIDENT_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'vendorId',     label: `${t('colVendor')} *`, required: true, options: supplierOptions, span: 12 },
    { kind: 'select', key: 'incidentType', label: t('scmFldIncidentType'), options: INCIDENT_TYPE_OPTIONS, span: 6 },
    { kind: 'select', key: 'impact',       label: t('scmFldImpact'), options: CRITICALITY_OPTIONS, span: 6 },
    { kind: 'select', key: 'slaBreached',  label: t('scmFldSlaBreached'),
      options: [{ value: 'false', label: t('selectNone') }, { value: 'true', label: 'Yes' }], span: 6 },
    { kind: 'textarea', key: 'description', label: t('scmFldDescription2'), rows: 3, span: 12 },
  ];

  // ── Tabs ────────────────────────────────────────────────────────────────
  const tabs: TabItem<TabId>[] = [
    { id: 'overview',     label: t('tabOverview') },
    { id: 'suppliers',    label: t('scmTabSuppliers') },
    { id: 'onboarding',   label: t('scmTabOnboarding') },
    { id: 'workflow',     label: t('scmTabWorkflow') },
    { id: 'assessments',  label: t('scmTabAssessments') },
    { id: 'dependencies', label: t('scmTabDependencies') },
    { id: 'contracts',    label: t('scmTabContracts') },
    { id: 'disruptions',  label: t('scmTabDisruptions') },
  ];

  // ── Columns ─────────────────────────────────────────────────────────────
  const supplierCols: Column<ScmVendorDto>[] = [
    { header: t('scmSupplierCol'), cell: r => <span style={{ fontWeight: 600 }}>{r.nameEn}</span> },
    { header: t('scmCountryCol'),  cell: r => r.country ?? '—' },
    { header: t('scmCategoryCol'), cell: r => <Badge tone="info">{(r.category ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colCriticality'), cell: r => <Badge tone={RISK_TONE[r.criticality ?? ''] ?? 'neutral'}>{r.criticality ?? '—'}</Badge> },
    { header: t('colRisk'),        cell: r => <Badge tone={RISK_TONE[r.riskLevel ?? ''] ?? 'neutral'}>{r.riskLevel ?? '—'}</Badge> },
    { header: t('colStatus'),      cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => supS.startEdit(r)} onDelete={() => supS.askDelete(r)} /> },
  ];

  const onbCols: Column<ScmOnboardingDto>[] = [
    { header: t('colVendor'),    cell: r => <span style={{ fontWeight: 600 }}>{r.vendorNameEn ?? `#${r.vendorId}`}</span> },
    { header: t('scmStageCol'),  cell: r => <Badge tone="primary">{(r.currentStage ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colProgress'),  cell: r => {
      const completed = (r.completedStages ?? '').split(',').filter(Boolean).length;
      return <span className="mono">{completed}/{ONBOARDING_STAGES.length}</span>;
    } },
    { header: t('scmFldDueDate'), cell: r => r.dueDate ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '260px',
      cell: r => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
          {r.status === 'IN_PROGRESS' && (
            <>
              <Button variant="ghost" size="sm" Icon={ArrowRight} onClick={() => advanceOnb(r)}>{t('scmActAdvance')}</Button>
              <Button variant="ghost" size="sm" Icon={Check}      onClick={() => approveOnb(r)}>{t('scmActApprove')}</Button>
              <Button variant="ghost" size="sm" Icon={XIcon}       onClick={() => rejectOnb(r)}>{t('scmActReject')}</Button>
            </>
          )}
          <RowActions onEdit={() => onbS.startEdit(r)} />
        </div>
      ) },
  ];

  const assessCols: Column<ScmAssessmentDto>[] = [
    { header: t('scmAssesseeCol'), cell: r => <span style={{ fontWeight: 600 }}>{r.vendorNameEn ?? `#${r.vendorId}`}</span> },
    { header: t('colType'),        cell: r => <Badge tone="info">{(r.assessmentType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colDate'),        cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colScore'),       cell: r => <ScorePillScm score={(r.overallScore ?? 0) * 10} /> },
    { header: t('colRisk'),        cell: r => <Badge tone={RISK_TONE[r.riskRating ?? ''] ?? 'neutral'}>{r.riskRating ?? '—'}</Badge> },
    { header: t('colNext'),        cell: r => r.nextAssessmentDate ?? '—', mono: true },
    { header: t('colStatus'),      cell: r => <Badge tone={STATUS_TONE[r.status] ?? 'neutral'} dot>{statusLabel(t, r.status)}</Badge> },
    { header: '', align: 'end', width: '170px',
      cell: r => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end' }}>
          {!r.escalatedToRisk && (
            <Button variant="ghost" size="sm" Icon={Flag} onClick={() => escalateAsm(r)}>{t('scmActEscalate')}</Button>
          )}
          <RowActions onEdit={() => asmS.startEdit(r)} />
        </div>
      ) },
  ];

  const depCols: Column<ScmDependencyDto>[] = [
    { header: t('scmServiceCol'),    cell: r => (
      <div>
        <div style={{ fontWeight: 600 }}>{r.serviceNameEn}</div>
        {r.assetName && <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.assetName}</div>}
      </div>
    ) },
    { header: t('colVendor'),        cell: r => r.vendorNameEn ?? `#${r.vendorId ?? '—'}` },
    { header: t('colType'),          cell: r => <Badge tone="info">{(r.dependencyType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('scmDepLevelCol'),   cell: r => <Badge tone={RISK_TONE[r.dependencyLevel ?? ''] ?? 'neutral'}>{r.dependencyLevel ?? '—'}</Badge> },
    { header: t('scmAlternativeCol'), cell: r => r.hasAlternative
      ? <span style={{ color: 'var(--success-fg)' }}>{r.alternativeVendorNameEn ?? '✓'}</span>
      : <span style={{ color: 'var(--danger)' }}>—</span> },
    { header: t('colStatus'),        cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => depS.startEdit(r)} onDelete={() => depS.askDelete(r)} /> },
  ];

  const contractCols: Column<ScmContractDto>[] = [
    { header: t('colNumberLabel'),  cell: r => r.contractNumber, mono: true },
    { header: t('colTitle'),        cell: r => (
      <div>
        <div style={{ fontWeight: 600 }}>{r.titleEn}</div>
        <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{r.vendorNameEn ?? '—'}</div>
      </div>
    ) },
    { header: t('colContractType'), cell: r => <Badge tone="info">{(r.contractType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colPeriod'),       cell: r => <span className="mono" style={{ fontSize: 12 }}>{r.startDate ?? '—'} → {r.endDate ?? '—'}</span> },
    { header: t('colRenewal'),      cell: r => r.autoRenewal ? <Badge tone="primary">{t('badgeAuto')}</Badge> : <Badge tone="neutral">{t('badgeManual')}</Badge> },
    { header: t('colValue'),        cell: r => r.contractValue != null
      ? `${r.contractValue.toLocaleString()} ${r.currency ?? ''}` : '—', mono: true },
    { header: t('colStatus'),       cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => conS.startEdit(r)} /> },
  ];

  const incidentCols: Column<ScmIncidentDto>[] = [
    { header: t('scmIncidentCodeCol'), cell: r => r.incidentCode ?? `#${r.id}`, mono: true },
    { header: t('colVendor'),  cell: r => <span style={{ fontWeight: 600 }}>{r.vendorNameEn ?? `#${r.vendorId}`}</span> },
    { header: t('colType'),    cell: r => <Badge tone="info">{(r.incidentType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('scmImpactCol'), cell: r => <Badge tone={RISK_TONE[r.impact ?? ''] ?? 'neutral'}>{r.impact ?? '—'}</Badge> },
    { header: 'SLA',           cell: r => r.slaBreached ? <Badge tone="danger">Breached</Badge> : <Badge tone="success">OK</Badge> },
    { header: t('scmReportedCol'), cell: r => r.reportedAt ? r.reportedAt.slice(0, 10) : '—', mono: true },
    { header: t('colStatus'),  cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '380px',
      cell: r => (
        <div style={{ display: 'flex', gap: 6, justifyContent: 'flex-end', flexWrap: 'wrap' }}>
          {r.status !== 'RESOLVED' && r.status !== 'CLOSED' && (
            <>
              {!r.backupPlanActivated && (
                <Button variant="ghost" size="sm" Icon={Power} onClick={() => activateBackup(r)}>{t('scmActActivateBackup')}</Button>
              )}
              {!r.capaCreated && (
                <Button variant="ghost" size="sm" Icon={Wrench} onClick={() => createCapa(r)}>{t('scmActCreateCapa')}</Button>
              )}
              {!r.escalatedToManagement && (
                <Button variant="ghost" size="sm" Icon={Flag} onClick={() => escalateInc(r)}>{t('scmActEscalate')}</Button>
              )}
              <Button variant="ghost" size="sm" Icon={Check} onClick={() => resolveInc(r)}>{t('scmActResolve')}</Button>
            </>
          )}
        </div>
      ) },
  ];

  // ── Submit handlers ─────────────────────────────────────────────────────
  async function submitSupplier(values: ScmVendorRequest) {
    try {
      if (supS.editing) await scmApi.updateVendor(supS.editing.id, values);
      else await scmApi.createVendor(values);
      toast.success(supS.editing ? 'Supplier updated' : 'Supplier added');
      supS.closeModal(); vendors.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitOnboarding(values: ScmOnboardingRequest) {
    try {
      const body: ScmOnboardingRequest = { ...values, vendorId: Number(values.vendorId) };
      if (!body.vendorId) { toast.danger('Pick a supplier first'); return; }
      // Update path: backend doesn't expose PUT for onboarding — start a new one only if creating.
      if (!onbS.editing) await scmApi.startOnboarding(body);
      else toast.danger('Edit not supported via this form yet — use stage actions');
      toast.success('Onboarding started');
      onbS.closeModal(); onboarding.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitAssessment(values: ScmAssessmentRequest) {
    try {
      const body: ScmAssessmentRequest = {
        ...values,
        vendorId: Number(values.vendorId),
        overallScore: values.overallScore != null && values.overallScore !== ('' as unknown as number)
          ? Number(values.overallScore) : null,
      };
      if (!body.vendorId) { toast.danger('Pick a supplier first'); return; }
      if (asmS.editing) await scmApi.updateAssessment(asmS.editing.id, body);
      else await scmApi.createAssessment(body);
      toast.success(asmS.editing ? 'Assessment updated' : 'Assessment added');
      asmS.closeModal(); assessments.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitDependency(values: ScmDependencyRequest) {
    try {
      const body: ScmDependencyRequest = {
        ...values,
        vendorId: values.vendorId ? Number(values.vendorId) : null,
        alternativeVendorId: values.alternativeVendorId ? Number(values.alternativeVendorId) : null,
        hasAlternative: String(values.hasAlternative) === 'true',
      };
      if (depS.editing) await scmApi.updateDependency(depS.editing.id, body);
      else await scmApi.createDependency(body);
      toast.success(depS.editing ? 'Dependency updated' : 'Dependency added');
      depS.closeModal(); dependencies.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitContract(values: ScmContractRequest) {
    try {
      const body: ScmContractRequest = {
        ...values,
        vendorId: Number(values.vendorId),
        autoRenewal: String(values.autoRenewal) === 'true',
        contractValue: values.contractValue != null && values.contractValue !== ('' as unknown as number)
          ? Number(values.contractValue) : null,
      };
      if (!body.vendorId) { toast.danger('Pick a supplier first'); return; }
      if (conS.editing) await scmApi.updateContract(conS.editing.id, body);
      else await scmApi.createContract(body);
      toast.success(conS.editing ? 'Contract updated' : 'Contract added');
      conS.closeModal(); contracts.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }
  async function submitIncident(values: ScmIncidentRequest) {
    try {
      const body: ScmIncidentRequest = {
        ...values, vendorId: Number(values.vendorId),
        slaBreached: String(values.slaBreached) === 'true',
      };
      if (!body.vendorId) { toast.danger('Pick a supplier first'); return; }
      await scmApi.createIncident(body);
      toast.success('Disruption logged');
      incS.closeModal(); incidents.reload();
    } catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Save failed'); }
  }

  // ── Delete handlers ─────────────────────────────────────────────────────
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

  // ── Workflow shortcut handlers ──────────────────────────────────────────
  async function advanceOnb(r: ScmOnboardingDto) {
    try { await scmApi.advanceOnboarding(r.id); toast.success('Stage advanced'); onboarding.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Advance failed'); }
  }
  async function approveOnb(r: ScmOnboardingDto) {
    try { await scmApi.approveOnboarding(r.id); toast.success('Onboarding approved'); onboarding.reload(); vendors.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Approve failed'); }
  }
  async function rejectOnb(r: ScmOnboardingDto) {
    const reason = window.prompt('Rejection reason?') ?? '';
    if (!reason.trim()) return;
    try { await scmApi.rejectOnboarding(r.id, reason); toast.success('Onboarding rejected'); onboarding.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Reject failed'); }
  }
  async function escalateAsm(r: ScmAssessmentDto) {
    try { await scmApi.escalateAssessment(r.id); toast.success('Escalated to Enterprise Risk'); assessments.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Escalate failed'); }
  }
  async function activateBackup(r: ScmIncidentDto) {
    try { await scmApi.activateBackup(r.id); toast.success('Backup plan activated'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function createCapa(r: ScmIncidentDto) {
    try { await scmApi.createIncidentCapa(r.id); toast.success('CAPA created'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function escalateInc(r: ScmIncidentDto) {
    try { await scmApi.escalateIncident(r.id); toast.success('Escalated to management'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Action failed'); }
  }
  async function resolveInc(r: ScmIncidentDto) {
    const notes = window.prompt('Resolution notes?') ?? '';
    if (!notes.trim()) return;
    try { await scmApi.resolveIncident(r.id, notes); toast.success('Disruption resolved'); incidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Resolve failed'); }
  }

  // ── Tab-aware New button ────────────────────────────────────────────────
  function newAction() {
    return () => {
      switch (tab) {
        case 'suppliers':    return supS.startCreate();
        case 'onboarding':   return onbS.startCreate();
        case 'assessments':  return asmS.startCreate();
        case 'dependencies': return depS.startCreate();
        case 'contracts':    return conS.startCreate();
        case 'disruptions':  return incS.startCreate();
        default:             return supS.startCreate();
      }
    };
  }
  const showNewButton = tab !== 'overview';

  // ── KPIs (prefer live stats, fall back to derived counts) ───────────────
  const high = vendors.data.filter(v => v.criticality === 'CRITICAL' || v.criticality === 'HIGH').length;
  const inProgress = onboarding.data.filter(o => o.status === 'IN_PROGRESS').length;
  const openInc = incidents.data.filter(i => i.status !== 'RESOLVED' && i.status !== 'CLOSED').length;
  const expiringContracts = stats?.expiringContracts ?? 0;

  const focusOnboarding = onboarding.data.find(o => o.status === 'IN_PROGRESS') ?? onboarding.data[0];
  const isDemo = vendors.isDemo;

  return (
    <Shell title="Supply Chain" sub={t('scmEyebrow')}>
      <PageHeader
        eyebrow={t('scmEyebrow')} title={t('scmTitle')} sub={t('scmSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter} onClick={() => setFilterOpen(true)}>
              {t('actionFilter')}{Object.values(advFilters).filter(Boolean).length > 0 ? ` · ${Object.values(advFilters).filter(Boolean).length}` : ''}
            </Button>
            <Button variant="secondary" Icon={Download} onClick={() => {
              const nameMap: Record<string, string> = {
                suppliers: 'scm-suppliers', onboarding: 'scm-onboarding',
                assessments: 'scm-assessments', dependencies: 'scm-dependencies',
                contracts: 'scm-contracts', disruptions: 'scm-disruptions',
              };
              const name = nameMap[tab] ?? 'scm-suppliers';
              downloadCsv(filteredScm as unknown as ExportRow[], stampFilename(name, 'csv'));
              toast.success(t('exportCsvDone'));
            }}>{t('actionExport')}</Button>
            {showNewButton && (
              <Button variant="primary" Icon={Plus} onClick={newAction()}>{t('actionNew')}</Button>
            )}
          </>
        }
      />
      <WorkflowQuickLaunch modules="SCM" />
      <div className="kpi-grid">
        <Kpi Icon={Truck}         label={t('scmKpiSuppliers')}        value={stats?.totalVendors ?? vendors.data.length} tone="primary" />
        <Kpi Icon={AlertTriangle} label={t('scmKpiHighRisk')}         value={stats?.criticalVendors ?? high} tone="danger" />
        <Kpi Icon={ListChecks}    label={t('scmKpiPending')}          value={stats?.pendingOnboarding ?? inProgress} tone="warning" />
        <Kpi Icon={ShieldAlert}   label={t('scmKpiOpenDisruptions')}  value={stats?.openIncidents ?? openInc} tone="danger" />
        <Kpi Icon={FileSignature} label={t('scmKpiExpiringContracts')} value={expiringContracts} tone="info" />
        <Kpi Icon={Star}          label={t('scmKpiAvgScore')}          value={String(avgAssessmentScore(assessments.data))} tone="success" />
      </div>
      <OverviewStrip
        storageKey="overview.scm"
        processBarTitle="Supply Chain — Process Center"
        processBarSubtitle="ISO 28001 + ISO 28000 + ISO 31000 + Saudi NRC + NIST SP 800-161 — pick a process to launch it."
        postureTitle="Regulator Posture · Supply Chain"
        anchors={SCM_ANCHORS}
       
        lifecycles={[
          // ── 1. Supplier Onboarding (6-stage) ──
          // Workflow: REGISTRATION → CRITICALITY_CLASSIFICATION → SECURITY → OPERATIONAL → LEGAL → APPROVAL
          // Trigger: opens the Start Onboarding modal so a new supplier
          // immediately enters the 6-stage onboarding pipeline.
          { ...SCM_LIFECYCLES[0],
            primary:   { label: 'Start',     onClick: onbS.startCreate },
            secondary: { label: 'Pipeline',  href: '/scm?tab=onboarding' } },

          // ── 2. Supply Chain Tier Mapping (Saudi NRC requirement) ──
          // Workflow: Tier-1 mapped → Tier-2 identified → SPOF analysis → Geographic concentration → Validated
          // Trigger: jumps to the Workflow & Map tab where the multi-tier
          // map (Tier-1 → Tier-N) lives — Saudi NRC + NIST SP 800-161 +
          // ISO 28001 require this annually.
          { name: 'Supply Chain Tier Mapping',
            nameAr: 'تعيين طبقات سلسلة الإمداد',
            reference: 'Saudi NRC + NIST SP 800-161 + ISO 28001',
            Icon: Layers,
            activeCount: SUPPLY_CHAIN_TIERS.length,
            overdueCount: SUPPLY_CHAIN_TIERS.filter(s => !!s.spof).length,
            steward: 'Supply Chain Risk Office',
            sla: 'Annual review',
            primary:   { label: 'Map',      onClick: () => setTab('workflow') },
            secondary: { label: 'Open map', onClick: () => setTab('workflow') } },

          // ── 3. Supplier Risk Re-assessment ──
          // Workflow: TRIGGERED → QUESTIONNAIRE → SCORED → ESCALATED (if needed)
          // Trigger: opens New Risk Assessment modal scoped to a supplier.
          { ...SCM_LIFECYCLES[1],
            primary:   { label: 'Trigger',     onClick: asmS.startCreate },
            secondary: { label: 'Assessments', href: '/scm?tab=assessments' } },

          // ── 4. Disruption Response & Recovery ──
          // Workflow: REPORT → CONTAIN → RECOVER → CAPA → CLOSE
          // Trigger: opens Report Disruption modal — incident enters the
          // 5-stage response workflow visible on the Workflow & Map tab.
          { ...SCM_LIFECYCLES[2],
            primary:   { label: 'Report',      onClick: incS.startCreate },
            secondary: { label: 'Disruptions', href: '/scm?tab=disruptions' } },

          // ── 5. Dependency & Concentration Risk ──
          // Workflow: MAPPED → RISK_RATED → TREATED → MONITORED
          // Trigger: opens New Dependency modal so the user can register a
          // service-to-vendor binding (with Tier-1 / has-alternative flags).
          { ...SCM_LIFECYCLES[3],
            primary:   { label: 'Map',          onClick: depS.startCreate },
            secondary: { label: 'Dependencies', href: '/scm?tab=dependencies' } },

          // ── 6. Contract Renewal & SLA Management ──
          // Workflow: EXPIRING → RFP → NEGOTIATION → SIGNED
          // Trigger: opens New Contract modal for the active supplier set.
          { ...SCM_LIFECYCLES[4],
            primary:   { label: 'Add',         onClick: conS.startCreate },
            secondary: { label: 'Contracts',   href: '/scm?tab=contracts' } },
        ]} />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          <div className="dash-grid">
            <Card title="Suppliers by Country" Icon={BarChart3}>
              <BarChart3D items={countryDistribution(vendors.data)} />
            </Card>
            <Card title={t('cardRiskDist')} Icon={AlertTriangle}>
              <BarChart3D items={[
                { label: t('severityLow'),      value: vendors.data.filter(v => v.riskLevel === 'LOW').length,      color: 'var(--success)', shade: '#23522F' },
                { label: t('severityMedium'),   value: vendors.data.filter(v => v.riskLevel === 'MEDIUM').length,   color: 'var(--cat-2)',   shade: '#7A5612' },
                { label: t('severityHigh'),     value: vendors.data.filter(v => v.riskLevel === 'HIGH').length,     color: 'var(--cat-6)',   shade: '#5C3220' },
                { label: t('severityCritical'), value: vendors.data.filter(v => v.riskLevel === 'CRITICAL').length, color: 'var(--danger)',  shade: '#7E2A25' },
              ]} />
            </Card>
          </div>
          {focusOnboarding && (
            <div style={{ marginTop: 16 }}>
              <Card title={t('scmCardOnboardingPipeline')} Icon={Workflow} action={<Badge tone="info">{focusOnboarding.vendorNameEn ?? '—'}</Badge>}>
                <Stepper steps={ONBOARDING_STAGES} active={focusOnboarding.currentStage ?? 'REGISTRATION'} />
              </Card>
            </div>
          )}
          <div className="dash-grid" style={{ marginTop: 16 }}>
            <Card title={t('scmCardRecentDisruptions')} Icon={AlertOctagon}>
              {incidents.data.length === 0
                ? <InlineEmpty compact>No recent disruptions.</InlineEmpty>
                : incidents.data.slice(0, 5).map(i => (
                  <div key={i.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px dashed var(--border)' }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>{i.vendorNameEn ?? `#${i.vendorId}`}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{(i.incidentType ?? '').replace(/_/g, ' ')}</div>
                    </div>
                    <Badge tone={RISK_TONE[i.impact ?? ''] ?? 'neutral'}>{i.impact ?? '—'}</Badge>
                  </div>
                ))}
            </Card>
            <Card title={t('scmCardTopCriticalSuppliers')} Icon={Star}>
              {vendors.data.filter(v => v.criticality === 'CRITICAL').slice(0, 5).map(v => (
                <div key={v.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '6px 0', borderBottom: '1px dashed var(--border)' }}>
                  <div>
                    <div style={{ fontWeight: 600, fontSize: 13 }}>{v.nameEn}</div>
                    <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{v.country ?? '—'}</div>
                  </div>
                  <Badge tone={RISK_TONE[v.riskLevel ?? ''] ?? 'neutral'}>{v.riskLevel ?? '—'}</Badge>
                </div>
              ))}
              {vendors.data.filter(v => v.criticality === 'CRITICAL').length === 0 && (
                <InlineEmpty compact>No critical suppliers.</InlineEmpty>
              )}
            </Card>
          </div>
        </>
      )}
      {tab === 'suppliers' && (
        <Card padded={false}>
          {vendors.loading ? <LoadingRow /> : <DataTable columns={supplierCols} rows={filteredScm as ScmVendorDto[]} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'onboarding' && (
        <Card padded={false}>
          {onboarding.loading ? <LoadingRow /> : <DataTable columns={onbCols} rows={filteredScm as ScmOnboardingDto[]}
            empty={<EmptyState Icon={Workflow} title={t('scmEmptyOnboarding')} sub={t('scmEmptyOnboardingSub')} />} />}
        </Card>
      )}
      {tab === 'assessments' && (
        <Card padded={false}>
          {assessments.loading ? <LoadingRow /> : <DataTable columns={assessCols} rows={filteredScm as ScmAssessmentDto[]}
            empty={<EmptyState Icon={ClipboardCheck} title={t('scmEmptyAssessments')} sub={t('scmEmptyAssessmentsSub')} />} />}
        </Card>
      )}
      {tab === 'dependencies' && (
        <Card padded={false}>
          {dependencies.loading ? <LoadingRow /> : <DataTable columns={depCols} rows={filteredScm as ScmDependencyDto[]}
            empty={<EmptyState Icon={Network} title={t('scmEmptyDependencies')} sub={t('scmEmptyDependenciesSub')} />} />}
        </Card>
      )}
      {tab === 'contracts' && (
        <Card padded={false}>
          {contracts.loading ? <LoadingRow /> : <DataTable columns={contractCols} rows={filteredScm as ScmContractDto[]}
            empty={<EmptyState Icon={FileSignature} title={t('scmEmptyContracts')} sub={t('scmEmptyContractsSub')} />} />}
        </Card>
      )}
      {tab === 'disruptions' && (
        <Card padded={false}>
          {incidents.loading ? <LoadingRow /> : <DataTable columns={incidentCols} rows={filteredScm as ScmIncidentDto[]}
            empty={<EmptyState Icon={AlertOctagon} title={t('scmEmptyDisruptions')} sub={t('scmEmptyDisruptionsSub')} />} />}
        </Card>
      )}
      {tab === 'workflow' && (
        <ScmWorkflowMap
          vendors={vendors.data} onboarding={onboarding.data}
          dependencies={dependencies.data} incidents={incidents.data}
          stages={ONBOARDING_STAGES} t={t} lang={lang} />
      )}

      {/* ── Modals ── */}
      <FormModal<ScmVendorRequest> open={supS.open}
        title={supS.editing ? `${t('editPrefix')} · ${supS.editing.nameEn}` : t('newSupplierTitleScm')}
        fields={SUPPLIER_FIELDS}
        initial={supS.editing ? vendorToReq(supS.editing) : SUPPLIER_EMPTY}
        onClose={supS.closeModal} onSubmit={submitSupplier} />

      <FormModal<ScmOnboardingRequest> open={onbS.open}
        title={onbS.editing ? `${t('editPrefix')} · #${onbS.editing.id}` : t('newOnboardingTitle')}
        fields={ONBOARDING_FIELDS}
        initial={onbS.editing ? onbToReq(onbS.editing) : ONBOARDING_EMPTY}
        onClose={onbS.closeModal} onSubmit={submitOnboarding} />

      <FormModal<ScmAssessmentRequest> open={asmS.open}
        title={asmS.editing ? `${t('editPrefix')} · #${asmS.editing.id}` : t('newAssessmentTitleScm')}
        fields={ASSESSMENT_FIELDS}
        initial={asmS.editing ? assessToReq(asmS.editing) : ASSESSMENT_EMPTY}
        onClose={asmS.closeModal} onSubmit={submitAssessment} />

      <FormModal<ScmDependencyRequest> open={depS.open}
        title={depS.editing ? `${t('editPrefix')} · ${depS.editing.serviceNameEn}` : t('newDependencyTitle')}
        fields={DEPENDENCY_FIELDS}
        initial={depS.editing ? depToReq(depS.editing) : DEPENDENCY_EMPTY}
        onClose={depS.closeModal} onSubmit={submitDependency} />

      <FormModal<ScmContractRequest> open={conS.open}
        title={conS.editing ? `${t('editPrefix')} · ${conS.editing.contractNumber || conS.editing.titleEn}` : t('newContractTitle')}
        fields={CONTRACT_FIELDS}
        initial={conS.editing ? contractToReq(conS.editing) : CONTRACT_EMPTY}
        onClose={conS.closeModal} onSubmit={submitContract} />

      <FormModal<ScmIncidentRequest> open={incS.open}
        title={t('newDisruptionTitle')}
        fields={INCIDENT_FIELDS}
        initial={INCIDENT_EMPTY}
        onClose={incS.closeModal} onSubmit={submitIncident} />

      {/* ── Confirms ── */}
      <ConfirmDialog
        open={!!supS.confirmDel} destructive busy={busy}
        title={t('scmConfirmDeleteSupplier')}
        message={supS.confirmDel ? `Supplier "${supS.confirmDel.nameEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={supS.closeConfirm}
        onConfirm={() => execDelete(supS.confirmDel, scmApi.deleteVendor, supS.closeConfirm, vendors.reload, 'Supplier')}
      />
      <ConfirmDialog
        open={!!depS.confirmDel} destructive busy={busy}
        title={t('scmConfirmDeleteDep')}
        message={depS.confirmDel ? `Dependency "${depS.confirmDel.serviceNameEn}" ${t('confirmDeleteSuffix')}` : ''}
        confirmLabel={t('deleteBtn')}
        onCancel={depS.closeConfirm}
        onConfirm={() => execDelete(depS.confirmDel, scmApi.deleteDependency, depS.closeConfirm, dependencies.reload, 'Dependency')}
      />
      <FilterModal open={filterOpen} fields={scmFilterFields} value={advFilters}
        onApply={v => { setAdvFilters(v); toast.success(t('filterModalApplied')); }}
        onClose={() => setFilterOpen(false)} />
    </Shell>
  );
}

function avgAssessmentScore(items: ScmAssessmentDto[]): string {
  if (items.length === 0) return '—';
  const sum = items.reduce((s, a) => s + (a.overallScore ?? 0), 0);
  return (sum / items.length).toFixed(1);
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
  return <InlineEmpty>{t('riskLoading')}</InlineEmpty>;
}

// ── Workflow & Map tab ─────────────────────────────────────────────────────
// Three stacked panels:
//  1. Onboarding pipeline (every in-progress vendor as its own row of stages)
//  2. Supply-chain dependency map (Service → Vendor → Alternative)
//  3. Disruption response workflow (REPORT → CONTAIN → RECOVER → CAPA → CLOSE)
function ScmWorkflowMap({
  onboarding, dependencies, incidents, stages, t, lang,
}: {
  vendors: ScmVendorDto[];
  onboarding: ScmOnboardingDto[];
  dependencies: ScmDependencyDto[];
  incidents: ScmIncidentDto[];
  stages: { id: string; label: string }[];
  t: (k: string) => string;
  lang: string;
}) {
  const inFlight = onboarding.filter(o => o.status === 'IN_PROGRESS' || o.status === 'ON_HOLD');
  const responseSteps = [
    { id: 'REPORT',   label: t('scmWfStepReport') },
    { id: 'CONTAIN',  label: t('scmWfStepContain') },
    { id: 'RECOVER',  label: t('scmWfStepRecover') },
    { id: 'CAPA',     label: t('scmWfStepCapa') },
    { id: 'CLOSE',    label: t('scmWfStepClose') },
  ];
  function activeStepFor(i: ScmIncidentDto): string {
    if (i.status === 'CLOSED' || i.status === 'RESOLVED') return 'CLOSE';
    if (i.capaCreated)              return 'CAPA';
    if (i.backupPlanActivated)      return 'RECOVER';
    if (i.status === 'CONTAINED')   return 'CONTAIN';
    return 'REPORT';
  }
  const tone: Record<string, string> = { CRITICAL: 'var(--danger)', HIGH: 'var(--cat-6)', MEDIUM: 'var(--cat-2)', LOW: 'var(--success)' };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* 0. Multi-tier supply-chain map — Saudi NRC requirement */}
      <TierMapPanel t={t} lang={lang} />

      {/* 1. Onboarding pipeline */}
      <Card title={t('scmWfPanelOnboarding')} Icon={Workflow} action={<Badge tone="info">{inFlight.length}</Badge>}>
        {inFlight.length === 0
          ? <InlineEmpty compact>{t('scmWfOnboardingEmpty')}</InlineEmpty>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {inFlight.map(o => (
                <div key={o.id} style={{ padding: '10px 4px', borderBottom: '1px dashed var(--border)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8, flexWrap: 'wrap', gap: 8 }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>{o.vendorNameEn ?? `#${o.vendorId}`}</div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>
                        {t('scmWfDue')}: <span className="mono">{o.dueDate ?? '—'}</span>
                        {o.assignee && <> · {o.assignee}</>}
                      </div>
                    </div>
                    <Badge tone={o.status === 'ON_HOLD' ? 'warning' : 'info'}>{o.status.replace(/_/g, ' ')}</Badge>
                  </div>
                  <Stepper steps={stages} active={o.currentStage} />
                  {o.notes && <div style={{ fontSize: 12, color: 'var(--ink-2)', marginTop: 6, fontStyle: 'italic' }}>{o.notes}</div>}
                </div>
              ))}
            </div>}
      </Card>

      {/* 2. Dependency map */}
      <Card title={t('scmWfPanelDependencyMap')} Icon={Network} action={<Badge tone="primary">{dependencies.length}</Badge>}>
        {dependencies.length === 0
          ? <InlineEmpty compact>{t('scmWfDepEmpty')}</InlineEmpty>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
              <div style={{ fontSize: 11, color: 'var(--ink-3)', display: 'flex', gap: 16, alignItems: 'center', flexWrap: 'wrap' }}>
                <span>{t('scmWfDepLegend')}:</span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ width: 10, height: 10, background: tone.CRITICAL, borderRadius: 2 }} /> {t('critCritical')}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ width: 10, height: 10, background: tone.HIGH, borderRadius: 2 }} /> {t('critHigh')}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ width: 10, height: 10, background: tone.MEDIUM, borderRadius: 2 }} /> {t('critMedium')}
                </span>
                <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                  <span style={{ width: 10, height: 10, background: tone.LOW, borderRadius: 2 }} /> {t('critLow')}
                </span>
              </div>
              {dependencies.map(d => {
                const lvl = (d.dependencyLevel ?? 'LOW').toUpperCase();
                const t2 = (d.dependencyType ?? '').toUpperCase();
                const critical = t2 === 'CRITICAL' || t2 === 'EXCLUSIVE' || lvl === 'HIGH';
                return (
                  <div key={d.id} style={{
                    display: 'grid',
                    gridTemplateColumns: 'minmax(180px, 1.2fr) 28px minmax(160px, 1fr) 28px minmax(160px, 1fr) auto',
                    gap: 8, alignItems: 'center', padding: '10px 6px',
                    background: critical ? 'rgba(180, 60, 50, 0.04)' : 'transparent',
                    borderInlineStart: `3px solid ${tone[lvl] ?? 'var(--border)'}`,
                    borderRadius: 'var(--r-sm)',
                  }}>
                    {/* Service node */}
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>{d.serviceNameEn}</div>
                      <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{d.assetName ?? ''}</div>
                    </div>
                    <ArrowRight size={14} style={{ color: 'var(--ink-3)' }} />
                    {/* Primary vendor */}
                    <div>
                      <div style={{ fontSize: 12, fontWeight: 600 }}>{d.vendorNameEn ?? '—'}</div>
                      <Badge tone={d.status === 'AT_RISK' ? 'danger' : d.status === 'INACTIVE' ? 'neutral' : 'success'}>
                        {(d.status ?? '').replace(/_/g, ' ')}
                      </Badge>
                    </div>
                    {/* Alt vendor link */}
                    {d.hasAlternative
                      ? <ArrowRight size={14} style={{ color: 'var(--success-fg)' }} />
                      : <XIcon size={14} style={{ color: 'var(--danger-fg)' }} aria-label={t('scmWfNoAlternative')} />}
                    <div>
                      {d.hasAlternative
                        ? <>
                            <div style={{ fontSize: 12, fontWeight: 600, color: 'var(--success-fg)' }}>{d.alternativeVendorNameEn ?? '—'}</div>
                            <div style={{ fontSize: 10, color: 'var(--ink-3)' }}>{t('scmWfAlternative')}</div>
                          </>
                        : <Badge tone="danger">{t('scmWfNoAlternative')}</Badge>}
                    </div>
                    <div style={{ display: 'flex', flexDirection: 'column', gap: 3, alignItems: 'flex-end' }}>
                      <Badge tone="primary">{d.dependencyType ?? '—'}</Badge>
                      <Badge tone={lvl === 'HIGH' ? 'warning' : lvl === 'MEDIUM' ? 'info' : 'success'}>{lvl}</Badge>
                    </div>
                  </div>
                );
              })}
            </div>}
      </Card>

      {/* 3. Disruption response workflow */}
      <Card title={t('scmWfPanelResponse')} Icon={AlertOctagon} action={<Badge tone="warning">{incidents.filter(i => i.status !== 'CLOSED' && i.status !== 'RESOLVED').length}</Badge>}>
        {incidents.length === 0
          ? <InlineEmpty compact>{t('scmWfResponseEmpty')}</InlineEmpty>
          : <div style={{ display: 'flex', flexDirection: 'column', gap: 12 }}>
              {incidents.slice(0, 6).map(i => (
                <div key={i.id} style={{ padding: '10px 4px', borderBottom: '1px dashed var(--border)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, flexWrap: 'wrap', gap: 6 }}>
                    <div>
                      <div style={{ fontWeight: 600, fontSize: 13 }}>
                        <span className="mono" style={{ fontSize: 11, color: 'var(--ink-3)', marginInlineEnd: 6 }}>{i.incidentCode ?? `#${i.id}`}</span>
                        {i.vendorNameEn ?? `#${i.vendorId}`} · {(i.incidentType ?? '').replace(/_/g, ' ')}
                      </div>
                      <div style={{ fontSize: 11, color: 'var(--ink-3)' }}>{i.description}</div>
                    </div>
                    <div style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                      <Badge tone={(RISK_TONE as Record<string, 'danger' | 'warning' | 'info' | 'success' | 'neutral'>)[(i.impact ?? '').toUpperCase()] ?? 'neutral'}>{i.impact ?? '—'}</Badge>
                      {i.slaBreached && <Badge tone="danger">{t('scmWfSlaBreached')}</Badge>}
                      <Badge tone={i.status === 'CLOSED' || i.status === 'RESOLVED' ? 'success' : 'warning'}>{i.status ?? '—'}</Badge>
                    </div>
                  </div>
                  <Stepper steps={responseSteps} active={activeStepFor(i)} />
                </div>
              ))}
            </div>}
      </Card>
    </div>
  );
}
