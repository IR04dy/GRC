import { useState } from 'react';
import {
  ShieldCheck, Bug, Flame, ClipboardCheck, Filter, Download, Plus, Activity, BarChart3,
  Shield, Target, Cpu, FileCheck2, ListTodo, ShieldOff,
  CheckCheck, TrendingUp, PlayCircle, Check, GitBranchPlus,
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
import { Stepper } from '../../components/ui/Stepper';
import { BarChart3D } from '../../components/viz/BarChart3D';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { SEVERITY_TONE, severityLabel, STATUS_TONE, statusLabel } from '../../lib/labels';
import { useApiList } from '../../lib/useApiResource';
import { downloadCsv, stampFilename, type ExportRow } from '../../lib/clientExport';
import {
  cyberApi, ApiError,
  type CyberControlDto, type CyberControlRequest,
  type CyberSubControlDto, type CyberSubControlRequest,
  type CyberAssessmentDto, type CyberAssessmentRequest,
  type CyberVulnDto, type CyberVulnRequest,
  type CyberExceptionDto, type CyberExceptionRequest,
  type CyberIncidentDto, type CyberIncidentRequest,
  type CyberAssetDto, type CyberAssetRequest,
  type CyberEvidenceDto, type CyberEvidenceRequest,
  type CyberActionPlanDto, type CyberActionPlanRequest,
} from '../../lib/api';
import { useToast } from '../../lib/toast';
import {
  CONTROLS, VULNS, SUB_CONTROLS, ASSESSMENTS, EXCEPTIONS,
  INCIDENTS, ASSETS, EVIDENCE, ACTION_PLANS,
} from './mockCyber';
import { ActiveWorkflowsPanel } from '../../components/workflows/ActiveWorkflowsPanel';
import { CYBER_WORKFLOWS } from '../../lib/mockWorkflows';
import { CYBER_LIFECYCLES } from '../../lib/moduleLifecycles';
import { OverviewStrip } from '../../components/layout/OverviewStrip';
import { CYBER_ANCHORS } from '../../lib/moduleAnchors';
import { CommsPanel } from '../../components/comms/CommsPanel';

type TabId = 'overview' | 'controls' | 'subcontrols' | 'assessments' | 'vuln' | 'exceptions' | 'incidents' | 'assets' | 'evidence' | 'actions' | 'workflows' | 'comms';

// ─── Workflow stage steppers (overview tab) ─────────────────────────────────
const VULN_STAGES = [
  { id: 'REGISTERED',     label: 'Registered' },
  { id: 'CLASSIFICATION', label: 'Classified' },
  { id: 'OWNER_ASSIGNMENT', label: 'Owner' },
  { id: 'REMEDIATION',    label: 'Remediation' },
  { id: 'VERIFICATION',   label: 'Verification' },
  { id: 'CLOSED',         label: 'Closed' },
];
const INCIDENT_STAGES = [
  { id: 'DETECTION',      label: 'Detection' },
  { id: 'TRIAGE',         label: 'Triage' },
  { id: 'CONTAINMENT',    label: 'Containment' },
  { id: 'INVESTIGATION',  label: 'Investigation' },
  { id: 'ERADICATION',    label: 'Eradication' },
  { id: 'RECOVERY',       label: 'Recovery' },
  { id: 'CLOSURE',        label: 'Closure' },
];

// ─── Shared option lists (lifted from v1 forms verbatim) ────────────────────
const FRAMEWORK_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'NCA ECC',    label: 'NCA ECC' },
  { value: 'ISO 27001',  label: 'ISO 27001' },
  { value: 'PCI DSS',    label: 'PCI DSS' },
  { value: 'NIST CSF',   label: 'NIST CSF' },
  { value: 'SAMA CSF',   label: 'SAMA CSF' },
];
const CONTROL_TYPE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'PREVENTIVE',   label: 'Preventive' },
  { value: 'DETECTIVE',    label: 'Detective' },
  { value: 'CORRECTIVE',   label: 'Corrective' },
  { value: 'COMPENSATING', label: 'Compensating' },
];
const REVIEW_FREQUENCY_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'MONTHLY',     label: 'Monthly' },
  { value: 'QUARTERLY',   label: 'Quarterly' },
  { value: 'SEMI_ANNUAL', label: 'Semi-Annual' },
  { value: 'ANNUAL',      label: 'Annual' },
];
const MATURITY_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: '1 - Initial',     label: '1 — Initial' },
  { value: '2 - Developing',  label: '2 — Developing' },
  { value: '3 - Defined',     label: '3 — Defined' },
  { value: '4 - Managed',     label: '4 — Managed' },
  { value: '5 - Optimized',   label: '5 — Optimized' },
];
const IMPL_STATUS_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'COMPLIANT',     label: 'Compliant' },
  { value: 'NON_COMPLIANT', label: 'Non-Compliant' },
  { value: 'PARTIAL',       label: 'Partial' },
  { value: 'NOT_ASSESSED',  label: 'Not Assessed' },
];
const CTRL_STATUS_OPTIONS = [
  { value: 'ACTIVE',   label: 'Active' },
  { value: 'INACTIVE', label: 'Inactive' },
];
const RESULT_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'PASS',    label: 'Pass' },
  { value: 'FAIL',    label: 'Fail' },
  { value: 'PARTIAL', label: 'Partial' },
  { value: 'N/A',     label: 'N/A' },
];
const RISK_LEVEL_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
const ASSESS_STATUS_OPTIONS = [
  { value: 'DRAFT',        label: 'Draft' },
  { value: 'IN_PROGRESS',  label: 'In Progress' },
  { value: 'UNDER_REVIEW', label: 'Under Review' },
  { value: 'APPROVED',     label: 'Approved' },
  { value: 'CLOSED',       label: 'Closed' },
];
const SEVERITY_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
const VULN_TYPE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'NETWORK',            label: 'Network' },
  { value: 'APPLICATION',        label: 'Application' },
  { value: 'OS',                 label: 'OS' },
  { value: 'CONFIGURATION',      label: 'Configuration' },
  { value: 'PHYSICAL',           label: 'Physical' },
  { value: 'SOCIAL_ENGINEERING', label: 'Social Engineering' },
];
const VULN_WORKFLOW_OPTIONS = [
  { value: 'REGISTERED',       label: 'Registered' },
  { value: 'CLASSIFICATION',   label: 'Classification' },
  { value: 'OWNER_ASSIGNMENT', label: 'Owner Assignment' },
  { value: 'REMEDIATION',      label: 'Remediation' },
  { value: 'VERIFICATION',     label: 'Verification' },
  { value: 'CLOSED',           label: 'Closed' },
];
const EXCEPTION_STATUS_OPTIONS = [
  { value: 'REQUESTED',             label: 'Requested' },
  { value: 'IMPACT_ASSESSMENT',     label: 'Impact Assessment' },
  { value: 'COMPENSATING_CONTROLS', label: 'Compensating Controls' },
  { value: 'APPROVED',              label: 'Approved' },
  { value: 'EXPIRED',               label: 'Expired' },
];
const INCIDENT_TYPE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'MALWARE',             label: 'Malware' },
  { value: 'PHISHING',            label: 'Phishing' },
  { value: 'DATA_BREACH',         label: 'Data Breach' },
  { value: 'RANSOMWARE',          label: 'Ransomware' },
  { value: 'DDoS',                label: 'DDoS' },
  { value: 'INSIDER_THREAT',      label: 'Insider Threat' },
  { value: 'UNAUTHORIZED_ACCESS', label: 'Unauthorized Access' },
  { value: 'OTHER',               label: 'Other' },
];
const IMPACT_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'CONFIDENTIALITY', label: 'Confidentiality' },
  { value: 'INTEGRITY',       label: 'Integrity' },
  { value: 'AVAILABILITY',    label: 'Availability' },
  { value: 'MULTIPLE',        label: 'Multiple' },
];
const INCIDENT_WORKFLOW_OPTIONS = [
  { value: 'DETECTION',       label: 'Detection' },
  { value: 'LOGGING',         label: 'Logging' },
  { value: 'TRIAGE',          label: 'Triage' },
  { value: 'CONTAINMENT',     label: 'Containment' },
  { value: 'INVESTIGATION',   label: 'Investigation' },
  { value: 'ERADICATION',     label: 'Eradication' },
  { value: 'RECOVERY',        label: 'Recovery' },
  { value: 'CLOSURE',         label: 'Closure' },
  { value: 'LESSONS_LEARNED', label: 'Lessons Learned' },
];
const BOOL_YN_OPTIONS = [
  { value: 'false', label: 'No' },
  { value: 'true',  label: 'Yes' },
];
const ASSET_TYPE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'SERVER',       label: 'Server' },
  { value: 'WORKSTATION',  label: 'Workstation' },
  { value: 'NETWORK',      label: 'Network' },
  { value: 'APPLICATION',  label: 'Application' },
  { value: 'DATABASE',     label: 'Database' },
  { value: 'CLOUD',        label: 'Cloud' },
  { value: 'MOBILE',       label: 'Mobile' },
  { value: 'IoT',          label: 'IoT' },
];
const CLASSIFICATION_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'TOP_SECRET',   label: 'Top Secret' },
  { value: 'SECRET',       label: 'Secret' },
  { value: 'CONFIDENTIAL', label: 'Confidential' },
  { value: 'RESTRICTED',   label: 'Restricted' },
  { value: 'PUBLIC',       label: 'Public' },
];
const ASSET_STATUS_OPTIONS = [
  { value: 'ACTIVE',         label: 'Active' },
  { value: 'DECOMMISSIONED', label: 'Decommissioned' },
  { value: 'MAINTENANCE',    label: 'Maintenance' },
];
const EVIDENCE_TYPE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'DOCUMENT',     label: 'Document' },
  { value: 'SCREENSHOT',   label: 'Screenshot' },
  { value: 'LOG',          label: 'Log' },
  { value: 'SCAN_REPORT',  label: 'Scan Report' },
  { value: 'AUDIT_REPORT', label: 'Audit Report' },
  { value: 'CERTIFICATE',  label: 'Certificate' },
];
const EVIDENCE_STATUS_OPTIONS = [
  { value: 'CURRENT', label: 'Current' },
  { value: 'EXPIRED', label: 'Expired' },
  { value: 'PENDING', label: 'Pending' },
];
const PRIORITY_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'CRITICAL', label: 'Critical' },
  { value: 'HIGH',     label: 'High' },
  { value: 'MEDIUM',   label: 'Medium' },
  { value: 'LOW',      label: 'Low' },
];
const ACTION_SOURCE_OPTIONS = [
  { value: '', label: 'Select…' },
  { value: 'ASSESSMENT',    label: 'Assessment' },
  { value: 'VULNERABILITY', label: 'Vulnerability' },
  { value: 'INCIDENT',      label: 'Incident' },
  { value: 'AUDIT',         label: 'Audit' },
  { value: 'MANUAL',        label: 'Manual' },
];
const ACTION_WORKFLOW_OPTIONS = [
  { value: 'OPEN',        label: 'Open' },
  { value: 'IN_PROGRESS', label: 'In Progress' },
  { value: 'COMPLETED',   label: 'Completed' },
  { value: 'CANCELLED',   label: 'Cancelled' },
];

// ─── Static field defs (no data dependencies) ───────────────────────────────
// Note: most fields are wrapped in `getXxxFields(t)` helpers below so labels translate.
// Only `*_EMPTY` and `*ToReq` mappers stay at module scope.
const CONTROL_EMPTY: CyberControlRequest = {
  controlNumber: '', titleEn: '', titleAr: '', frameworkName: '', domain: '', procedureEn: '',
  controlType: '', controlOwner: '', reviewFrequency: '',
  maturityLevel: '', implementationStatus: '', status: 'ACTIVE',
};
const controlToReq = (c: CyberControlDto): CyberControlRequest => ({
  controlNumber: c.controlNumber, titleEn: c.titleEn, titleAr: c.titleAr,
  frameworkName: c.frameworkName, domain: c.domain, procedureEn: c.procedureEn,
  controlType: c.controlType, controlOwner: c.controlOwner, reviewFrequency: c.reviewFrequency,
  maturityLevel: c.maturityLevel, implementationStatus: c.implementationStatus, status: c.status,
});

const SUBCTRL_EMPTY: CyberSubControlRequest = {
  controlId: 0, subControlNumber: '', titleEn: '', titleAr: '', descriptionEn: '',
  controlOwner: '', maturityLevel: '', implementationStatus: '', evidenceRequired: false,
};
const subCtrlToReq = (s: CyberSubControlDto): CyberSubControlRequest => ({
  controlId: s.controlId, subControlNumber: s.subControlNumber,
  titleEn: s.titleEn, titleAr: s.titleAr, descriptionEn: s.descriptionEn,
  controlOwner: s.controlOwner, maturityLevel: s.maturityLevel,
  implementationStatus: s.implementationStatus, evidenceRequired: s.evidenceRequired,
});

const ASSESS_EMPTY: CyberAssessmentRequest = {
  controlId: 0, assessorName: '', reviewerName: '',
  assessmentDate: '', result: '', maturityScore: 3,
  riskLevel: '', gapIdentified: false, gapDescription: '',
  assessorComments: '', reviewerComments: '', status: 'DRAFT',
};
const assessToReq = (a: CyberAssessmentDto): CyberAssessmentRequest => ({
  controlId: a.controlId,
  assessorName: a.assessorName, reviewerName: a.reviewerName,
  assessmentDate: a.assessmentDate, result: a.result, maturityScore: a.maturityScore,
  riskLevel: a.riskLevel, gapIdentified: a.gapIdentified, gapDescription: a.gapDescription,
  assessorComments: a.assessorComments, reviewerComments: a.reviewerComments, status: a.status,
});

const VULN_EMPTY: CyberVulnRequest = {
  titleEn: '', titleAr: '', descriptionEn: '',
  severity: 'MEDIUM', cvssScore: 5,
  vulnerabilityType: '', cveId: '', ownerName: '',
  slaDays: 30, dueDate: '', workflowStatus: 'REGISTERED', remediationNotes: '',
};
const vulnToReq = (v: CyberVulnDto): CyberVulnRequest => ({
  assetId: v.assetId,
  titleEn: v.titleEn, titleAr: v.titleAr, descriptionEn: v.descriptionEn,
  severity: v.severity, cvssScore: v.cvssScore,
  vulnerabilityType: v.vulnerabilityType, cveId: v.cveId, ownerName: v.ownerName,
  slaDays: v.slaDays, dueDate: v.dueDate, workflowStatus: v.workflowStatus,
  remediationNotes: v.remediationNotes,
});

// EXC_FIELDS now built inside the component via getExcFields(t)
const EXC_EMPTY: CyberExceptionRequest = {
  titleEn: '', ownerName: '', approverName: '', expiryDate: '',
  workflowStatus: 'REQUESTED', impactAssessment: '', exceptionReason: '', compensatingControls: '',
};
const excToReq = (e: CyberExceptionDto): CyberExceptionRequest => ({
  vulnerabilityId: e.vulnerabilityId,
  titleEn: e.titleEn,
  ownerName: e.ownerName, approverName: e.approverName,
  expiryDate: e.expiryDate, workflowStatus: e.workflowStatus,
  impactAssessment: e.impactAssessment, exceptionReason: e.exceptionReason,
  compensatingControls: e.compensatingControls,
});

const INC_EMPTY_BASE: Record<string, unknown> = {
  titleEn: '', titleAr: '', incidentType: '',
  incidentLead: '', severity: 'MEDIUM', impact: '',
  workflowStatus: 'DETECTION', bcmHandoff: 'false', lessonsLearned: '',
  affectedAssetId: '',
};
const incToShape = (i: CyberIncidentDto): Record<string, unknown> => ({
  titleEn: i.titleEn, titleAr: i.titleAr ?? '',
  incidentType: i.incidentType ?? '',
  incidentLead: i.incidentLead ?? '',
  severity: i.severity ?? 'MEDIUM',
  impact: i.impact ?? '',
  workflowStatus: i.workflowStatus ?? i.status ?? 'DETECTION',
  bcmHandoff: i.bcmHandoff ? 'true' : 'false',
  lessonsLearned: i.lessonsLearned ?? '',
  affectedAssetId: i.affectedAssetId != null ? String(i.affectedAssetId) : '',
});

// ASSET_FIELDS now built inside the component via getAssetFields(t)
const ASSET_EMPTY: CyberAssetRequest = {
  assetName: '', assetType: '', businessOwner: '', technicalOwner: '',
  classification: '', criticality: '', status: 'ACTIVE',
  ipAddress: '', hostname: '', operatingSystem: '',
};
const assetToReq = (a: CyberAssetDto): CyberAssetRequest => ({
  assetName: a.assetName, assetType: a.assetType,
  businessOwner: a.businessOwner, technicalOwner: a.technicalOwner,
  classification: a.classification, criticality: a.criticality, status: a.status,
  ipAddress: a.ipAddress, hostname: a.hostname, operatingSystem: a.operatingSystem,
});

const EV_EMPTY: CyberEvidenceRequest = {
  titleEn: '', evidenceType: '', frameworkRequirement: '', ownerName: '',
  version: '', status: 'CURRENT', reviewDate: '', expiryDate: '', fileName: '',
};
const evToReq = (e: CyberEvidenceDto): CyberEvidenceRequest => ({
  titleEn: e.titleEn, evidenceType: e.evidenceType, controlId: e.controlId,
  frameworkRequirement: e.frameworkRequirement, ownerName: e.ownerName,
  version: e.version, status: e.status,
  reviewDate: e.reviewDate, expiryDate: e.expiryDate, fileName: e.fileName,
});

// AP_FIELDS now built inside the component via getApFields(t)
const AP_EMPTY: CyberActionPlanRequest = {
  titleEn: '', titleAr: '', descriptionEn: '',
  priority: '', ownerName: '', dueDate: '',
  workflowStatus: 'OPEN', progressPct: 0, sourceType: '', completionEvidence: '',
};
const apToReq = (a: CyberActionPlanDto): CyberActionPlanRequest => ({
  titleEn: a.titleEn, titleAr: a.titleAr, descriptionEn: a.descriptionEn,
  priority: a.priority, ownerName: a.ownerName,
  dueDate: a.dueDate, workflowStatus: a.workflowStatus,
  progressPct: a.progressPct ?? a.progressPercent,
  sourceType: a.sourceType, completionEvidence: a.completionEvidence,
});

// ─── Generic per-entity modal/delete state hook ─────────────────────────────
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

// ─── Component ──────────────────────────────────────────────────────────────
export function CyberPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('overview');
  const [busy, setBusy] = useState(false);

  const ctrlS    = useEntityState<CyberControlDto>();
  const subS     = useEntityState<CyberSubControlDto>();
  const assessS  = useEntityState<CyberAssessmentDto>();
  const vulnS    = useEntityState<CyberVulnDto>();
  const excS     = useEntityState<CyberExceptionDto>();
  const incS     = useEntityState<CyberIncidentDto>();
  const assetS   = useEntityState<CyberAssetDto>();
  const evS      = useEntityState<CyberEvidenceDto>();
  const apS      = useEntityState<CyberActionPlanDto>();

  const controls       = useApiList<CyberControlDto>(()    => cyberApi.controls(0, 200),    CONTROLS);
  const subControls    = useApiList<CyberSubControlDto>(() => cyberApi.subControls(0, 200), SUB_CONTROLS);
  const assessments    = useApiList<CyberAssessmentDto>(() => cyberApi.assessments(0, 200), ASSESSMENTS);
  const vulns          = useApiList<CyberVulnDto>(()       => cyberApi.vulns(0, 200),       VULNS);
  const exceptions     = useApiList<CyberExceptionDto>(()  => cyberApi.exceptions(0, 200),  EXCEPTIONS);
  const cyberIncidents = useApiList<CyberIncidentDto>(()   => cyberApi.incidents(0, 200),   INCIDENTS);
  const assets         = useApiList<CyberAssetDto>(()      => cyberApi.assets(0, 200),      ASSETS);
  const cyberEvidence  = useApiList<CyberEvidenceDto>(()   => cyberApi.evidence(0, 200),    EVIDENCE);
  const actionPlans    = useApiList<CyberActionPlanDto>(() => cyberApi.actionPlans(0, 200), ACTION_PLANS);

  const isDemo = controls.isDemo || vulns.isDemo;

  // Data-driven dropdowns — built per render so they reflect the latest list
  const controlOptions = [
    { value: '', label: 'Select…' },
    ...controls.data.map(c => ({ value: String(c.id), label: `${c.controlNumber} · ${c.titleEn}` })),
  ];
  const assetOptions = [
    { value: '', label: 'Select Asset…' },
    ...assets.data.map(a => ({ value: String(a.id), label: a.assetName })),
  ];

  // ── Field defs (built per-render so labels translate via t()) ──
  // Cyber Control: 12-col layout matching v1's wide modal
  const CONTROL_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'frameworkName', label: t('cyberFldFramework'), options: FRAMEWORK_OPTIONS, span: 6 },
    { kind: 'text',   key: 'domain',        label: t('cyberFldDomain'), maxLength: 100, placeholder: 'e.g. Access Control', span: 6 },
    { kind: 'text',   key: 'controlNumber', label: t('cyberFldCtrlNumber') + ' *', required: true, maxLength: 50, placeholder: 'e.g. AC-1', span: 4 },
    { kind: 'text',   key: 'titleEn',       label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'text',   key: 'titleAr',       label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'textarea', key: 'procedureEn', label: t('cyberFldProcedure'), rows: 3, span: 12 },
    { kind: 'select', key: 'controlType',     label: t('cyberFldCtrlType'),   options: CONTROL_TYPE_OPTIONS, span: 4 },
    { kind: 'text',   key: 'controlOwner',    label: t('cyberFldCtrlOwner'),  span: 4 },
    { kind: 'select', key: 'reviewFrequency', label: t('cyberFldReviewFreq'), options: REVIEW_FREQUENCY_OPTIONS, span: 4 },
    { kind: 'select', key: 'maturityLevel',   label: t('cyberFldMaturity'),   options: MATURITY_OPTIONS, span: 4 },
    { kind: 'select', key: 'implementationStatus', label: t('cyberFldImplStatus'), options: IMPL_STATUS_OPTIONS, span: 4 },
    { kind: 'select', key: 'status',          label: t('fldStatusLbl'),       options: CTRL_STATUS_OPTIONS, span: 4 },
  ];
  const SUB_FIELDS: FieldDef[] = [
    { kind: 'select', key: 'controlId',  label: t('cyberFldParentCtrl') + ' *', options: controlOptions, required: true, span: 12 },
    { kind: 'text',   key: 'subControlNumber', label: t('cyberFldSubNumber'), placeholder: 'e.g. AC-1.1', span: 4 },
    { kind: 'text',   key: 'titleEn',         label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'text',   key: 'titleAr',         label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'textarea', key: 'descriptionEn', label: t('fldDescriptionLbl'), rows: 2, span: 12 },
    { kind: 'text',   key: 'controlOwner',    label: t('cyberFldCtrlOwner'), span: 4 },
    { kind: 'select', key: 'maturityLevel',   label: t('cyberFldMaturity'), options: MATURITY_OPTIONS, span: 4 },
    { kind: 'select', key: 'implementationStatus', label: t('cyberFldImplStatus'), options: IMPL_STATUS_OPTIONS, span: 4 },
  ];
  const ASSESS_FIELDS: FieldDef[] = [
    { kind: 'select',   key: 'controlId',     label: t('cyberFldParentCtrl') + ' *', options: controlOptions, required: true, span: 12 },
    { kind: 'text',     key: 'assessorName',  label: t('cyberFldAssessor'), span: 6 },
    { kind: 'text',     key: 'reviewerName',  label: t('cyberFldReviewer'), span: 6 },
    { kind: 'date',     key: 'assessmentDate', label: t('fldAssessmentDate'), span: 4 },
    { kind: 'select',   key: 'result',        label: t('cyberFldResult'), options: RESULT_OPTIONS, span: 4 },
    { kind: 'number',   key: 'maturityScore', label: t('cyberFldMaturityScore'), min: 1, max: 5, span: 4 },
    { kind: 'select',   key: 'riskLevel',     label: t('cyberFldRiskLevel'), options: RISK_LEVEL_OPTIONS, span: 4 },
    { kind: 'select',   key: 'gapIdentified', label: t('cyberFldGapId'), options: BOOL_YN_OPTIONS, span: 4 },
    { kind: 'select',   key: 'status',        label: t('fldStatusLbl'), options: ASSESS_STATUS_OPTIONS, span: 4 },
    { kind: 'textarea', key: 'gapDescription',     label: t('cyberFldGapDesc'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'assessorComments',   label: t('cyberFldAssessorCmt'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'reviewerComments',   label: t('cyberFldReviewerCmt'), rows: 2, span: 12 },
  ];
  const VULN_FIELDS: FieldDef[] = [
    { kind: 'select',   key: 'assetId',     label: t('cyberFldAsset'), options: assetOptions, span: 12 },
    { kind: 'text',     key: 'titleEn',     label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'text',     key: 'cveId',       label: t('cyberFldCveId'), placeholder: 'CVE-2024-XXXX', span: 4 },
    { kind: 'text',     key: 'titleAr',     label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'textarea', key: 'descriptionEn', label: t('fldDescriptionLbl'), rows: 2, span: 12 },
    { kind: 'select',   key: 'severity',    label: t('fldSeverity') + ' *', options: SEVERITY_OPTIONS, required: true, span: 4 },
    { kind: 'select',   key: 'vulnerabilityType', label: t('cyberFldVulnType'), options: VULN_TYPE_OPTIONS, span: 4 },
    { kind: 'text',     key: 'ownerName',   label: t('fldOwnerLbl'), span: 4 },
    { kind: 'number',   key: 'cvssScore',   label: t('cyberFldCvssScore'), min: 0, max: 10, span: 4 },
    { kind: 'number',   key: 'slaDays',     label: t('cyberFldSlaDays'), min: 1, span: 4 },
    { kind: 'date',     key: 'dueDate',     label: t('fldDueDate'), span: 4 },
    { kind: 'select',   key: 'workflowStatus', label: t('cyberFldWorkflow'), options: VULN_WORKFLOW_OPTIONS, span: 12 },
    { kind: 'textarea', key: 'remediationNotes', label: t('cyberFldRemediation'), rows: 2, span: 12 },
  ];
  const EXC_FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'titleEn',                label: t('cyberFldExcTitle') + ' *', required: true, maxLength: 200, span: 12 },
    { kind: 'text',     key: 'ownerName',              label: t('fldOwnerLbl'),    span: 6 },
    { kind: 'text',     key: 'approverName',           label: t('cyberFldApprover'), span: 6 },
    { kind: 'date',     key: 'expiryDate',             label: t('fldExpiryDate'),  span: 6 },
    { kind: 'select',   key: 'workflowStatus',         label: t('fldStatusLbl'),   options: EXCEPTION_STATUS_OPTIONS, span: 6 },
    { kind: 'textarea', key: 'impactAssessment',       label: t('cyberFldImpactAssess'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'exceptionReason',        label: t('cyberFldExcReason'), rows: 2, span: 12 },
    { kind: 'textarea', key: 'compensatingControls',   label: t('cyberFldCompCtrl'), rows: 2, span: 12 },
  ];
  const EV_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'titleEn',              label: t('cyberFldEvidenceTitle') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'evidenceType',         label: t('cyberFldEvidenceType'), options: EVIDENCE_TYPE_OPTIONS, span: 4 },
    { kind: 'select', key: 'controlId',            label: t('cyberFldLinkedCtrl'), options: controlOptions, span: 6 },
    { kind: 'text',   key: 'frameworkRequirement', label: t('cyberFldFwReq'), placeholder: 'e.g. ISO 27001 A.5.1', span: 6 },
    { kind: 'text',   key: 'ownerName',            label: t('fldOwnerLbl'), span: 4 },
    { kind: 'text',   key: 'version',              label: t('fldVersion'), placeholder: 'v1.0', span: 4 },
    { kind: 'select', key: 'status',               label: t('fldStatusLbl'), options: EVIDENCE_STATUS_OPTIONS, span: 4 },
    { kind: 'date',   key: 'reviewDate',           label: t('fldReviewDate'), span: 6 },
    { kind: 'date',   key: 'expiryDate',           label: t('fldExpiryDate'), span: 6 },
    { kind: 'text',   key: 'fileName',             label: t('cyberFldFileName'), placeholder: 'evidence_file.pdf', span: 12 },
  ];
  const INC_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'titleEn',          label: t('cyberFldIncTitle') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'incidentType',     label: t('cyberFldIncType'), options: INCIDENT_TYPE_OPTIONS, span: 4 },
    { kind: 'text',   key: 'titleAr',          label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'select', key: 'affectedAssetId',  label: t('cyberFldAffectedAsset'), options: assetOptions, span: 6 },
    { kind: 'text',   key: 'incidentLead',     label: t('cyberFldIncLead'), span: 6 },
    { kind: 'select', key: 'severity',         label: t('fldSeverity') + ' *', options: SEVERITY_OPTIONS, required: true, span: 4 },
    { kind: 'select', key: 'impact',           label: t('cyberFldImpact'), options: IMPACT_OPTIONS, span: 4 },
    { kind: 'select', key: 'workflowStatus',   label: t('fldStatusLbl'), options: INCIDENT_WORKFLOW_OPTIONS, span: 4 },
    { kind: 'select', key: 'bcmHandoff',       label: t('cyberFldBcmHandoff'), options: [
        { value: 'false', label: t('selectPlaceholder') === 'اختر…' ? 'لا' : 'No' },
        { value: 'true',  label: t('cyberFldBcmYes') },
      ], span: 6 },
    { kind: 'textarea', key: 'lessonsLearned', label: t('cyberFldLessons'), rows: 2, span: 12 },
  ];
  const ASSET_FIELDS: FieldDef[] = [
    { kind: 'text',   key: 'assetName',       label: t('fldNameEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select', key: 'assetType',       label: t('cyberFldAssetType'), options: ASSET_TYPE_OPTIONS, span: 4 },
    { kind: 'text',   key: 'businessOwner',   label: t('cyberFldBusinessOwner'), span: 6 },
    { kind: 'text',   key: 'technicalOwner',  label: t('cyberFldTechOwner'), span: 6 },
    { kind: 'select', key: 'classification',  label: t('cyberFldClassification'), options: CLASSIFICATION_OPTIONS, span: 4 },
    { kind: 'select', key: 'criticality',     label: t('fldCriticality'), options: SEVERITY_OPTIONS, span: 4 },
    { kind: 'select', key: 'status',          label: t('fldStatusLbl'), options: ASSET_STATUS_OPTIONS, span: 4 },
    { kind: 'text',   key: 'ipAddress',       label: t('cyberFldIp'), placeholder: '192.168.x.x', span: 4 },
    { kind: 'text',   key: 'hostname',        label: t('cyberFldHostname'), span: 4 },
    { kind: 'text',   key: 'operatingSystem', label: t('cyberFldOs'), placeholder: 'Windows Server 2022', span: 4 },
  ];
  const AP_FIELDS: FieldDef[] = [
    { kind: 'text',     key: 'titleEn',            label: t('fldTitleEnLbl') + ' *', required: true, maxLength: 200, span: 8 },
    { kind: 'select',   key: 'priority',           label: t('fldPriority'), options: PRIORITY_OPTIONS, span: 4 },
    { kind: 'text',     key: 'titleAr',            label: t('fldTitleArLbl'), maxLength: 200, span: 12 },
    { kind: 'textarea', key: 'descriptionEn',      label: t('fldDescriptionLbl'), rows: 2, span: 12 },
    { kind: 'select',   key: 'sourceType',         label: t('cyberFldSourceType'), options: ACTION_SOURCE_OPTIONS, span: 4 },
    { kind: 'text',     key: 'ownerName',          label: t('fldOwnerLbl'), span: 4 },
    { kind: 'date',     key: 'dueDate',            label: t('fldDueDate'), span: 4 },
    { kind: 'select',   key: 'workflowStatus',     label: t('cyberFldWorkflow'), options: ACTION_WORKFLOW_OPTIONS, span: 6 },
    { kind: 'number',   key: 'progressPct',        label: t('cyberFldProgress'), min: 0, max: 100, span: 6 },
    { kind: 'textarea', key: 'completionEvidence', label: t('cyberFldCompletionEv'), rows: 2, span: 12 },
  ];

  const tabs: TabItem<TabId>[] = [
    { id: 'overview',    label: t('tabOverview') },
    { id: 'controls',    label: t('cyberTabControls') },
    { id: 'subcontrols', label: t('cyberTabSubControls') },
    { id: 'assessments', label: t('cyberTabAssessments') },
    { id: 'vuln',        label: t('cyberTabVuln') },
    { id: 'exceptions',  label: 'Exceptions' },
    { id: 'incidents',   label: t('cyberTabIncidents') },
    { id: 'assets',      label: t('cyberTabAssets') },
    { id: 'evidence',    label: t('cyberTabEvidence') },
    { id: 'actions',     label: t('cyberTabActions') },
    { id: 'workflows',   label: t('cyberTabWorkflows') ?? 'Workflows' },
    { id: 'comms',       label: t('detailTabComms') },
  ];

  // ── Submit handlers ──
  async function submitControl(values: CyberControlRequest) {
    try {
      if (ctrlS.editing) await cyberApi.updateControl(ctrlS.editing.id, values);
      else await cyberApi.createControl(values);
      toast.success(ctrlS.editing ? 'Control updated' : 'Control added'); ctrlS.closeModal(); controls.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitSub(values: CyberSubControlRequest) {
    try {
      const body = { ...values, controlId: Number(values.controlId) };
      if (subS.editing) await cyberApi.updateSubControl(subS.editing.id, body);
      else await cyberApi.createSubControl(body);
      toast.success(subS.editing ? 'Sub-control updated' : 'Sub-control added'); subS.closeModal(); subControls.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitAssess(values: CyberAssessmentRequest) {
    try {
      const body = { ...values, controlId: Number(values.controlId), gapIdentified: values.gapIdentified === true || String(values.gapIdentified) === 'true' };
      if (assessS.editing) await cyberApi.updateAssessment(assessS.editing.id, body);
      else await cyberApi.createAssessment(body);
      toast.success(assessS.editing ? 'Assessment updated' : 'Assessment added'); assessS.closeModal(); assessments.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitVuln(values: CyberVulnRequest) {
    try {
      const body = { ...values, assetId: values.assetId ? Number(values.assetId) : undefined };
      if (vulnS.editing) await cyberApi.updateVuln(vulnS.editing.id, body);
      else await cyberApi.createVuln(body);
      toast.success(vulnS.editing ? 'Vulnerability updated' : 'Vulnerability added'); vulnS.closeModal(); vulns.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitExc(values: CyberExceptionRequest) {
    try {
      if (excS.editing) await cyberApi.updateException(excS.editing.id, values);
      else await cyberApi.createException(values);
      toast.success(excS.editing ? 'Exception updated' : 'Exception added'); excS.closeModal(); exceptions.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitInc(values: Record<string, unknown>) {
    const body: CyberIncidentRequest = {
      titleEn: String(values.titleEn ?? ''),
      titleAr: String(values.titleAr ?? '') || undefined,
      incidentType: String(values.incidentType ?? '') || undefined,
      affectedAssetId: values.affectedAssetId ? Number(values.affectedAssetId) : undefined,
      severity: String(values.severity ?? 'MEDIUM'),
      impact: String(values.impact ?? '') || undefined,
      workflowStatus: String(values.workflowStatus ?? 'DETECTION'),
      incidentLead: String(values.incidentLead ?? '') || undefined,
      bcmHandoff: String(values.bcmHandoff) === 'true',
      lessonsLearned: String(values.lessonsLearned ?? '') || undefined,
    };
    try {
      if (incS.editing) await cyberApi.updateIncident(incS.editing.id, body);
      else await cyberApi.createIncident(body);
      toast.success(incS.editing ? 'Incident updated' : 'Incident added'); incS.closeModal(); cyberIncidents.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitAsset(values: CyberAssetRequest) {
    try {
      if (assetS.editing) await cyberApi.updateAsset(assetS.editing.id, values);
      else await cyberApi.createAsset(values);
      toast.success(assetS.editing ? 'Asset updated' : 'Asset added'); assetS.closeModal(); assets.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitEv(values: CyberEvidenceRequest) {
    try {
      const body = { ...values, controlId: values.controlId ? Number(values.controlId) : undefined };
      if (evS.editing) await cyberApi.updateEvidence(evS.editing.id, body);
      else await cyberApi.createEvidence(body);
      toast.success(evS.editing ? 'Evidence updated' : 'Evidence added'); evS.closeModal(); cyberEvidence.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
  }
  async function submitAp(values: CyberActionPlanRequest) {
    try {
      if (apS.editing) await cyberApi.updateActionPlan(apS.editing.id, values);
      else await cyberApi.createActionPlan(values);
      toast.success(apS.editing ? 'Action plan updated' : 'Action plan added'); apS.closeModal(); actionPlans.reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Save failed'); }
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

  // ── Workflow actions ──
  async function approveAssess(a: CyberAssessmentDto) {
    try { await cyberApi.approveAssessment(a.id); toast.success('Assessment approved'); assessments.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Approve failed'); }
  }
  async function convertGap(a: CyberAssessmentDto) {
    try { await cyberApi.convertAssessmentGap(a.id); toast.success('Gap converted to Action Plan'); assessments.reload(); actionPlans.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Convert failed'); }
  }
  async function escalateVuln(v: CyberVulnDto) {
    try { await cyberApi.escalateVuln(v.id); toast.success('Vulnerability escalated'); vulns.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Escalate failed'); }
  }
  async function approveExc(e: CyberExceptionDto) {
    try { await cyberApi.approveException(e.id); toast.success('Exception approved'); exceptions.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Approve failed'); }
  }
  async function startResponse(i: CyberIncidentDto) {
    try { await cyberApi.startResponse(i.id); toast.success('Response started'); cyberIncidents.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to start response'); }
  }
  async function completePlan(p: CyberActionPlanDto) {
    try { await cyberApi.completePlan(p.id); toast.success('Action plan completed'); actionPlans.reload(); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to complete'); }
  }

  // ── Columns ──
  const controlCols: Column<CyberControlDto>[] = [
    { header: t('colControl'),    cell: r => <span style={{ fontWeight: 600 }}>{r.controlNumber} · {r.titleEn}</span> },
    { header: t('colFramework'),  cell: r => <Badge tone="info">{r.frameworkName ?? '—'}</Badge> },
    { header: t('colDomain'),     cell: r => r.domain ?? '—' },
    { header: t('colType'),       cell: r => r.controlType ?? '—' },
    { header: t('colOwner'),      cell: r => r.controlOwner ?? '—' },
    { header: t('colMaturity'),   cell: r => r.maturityLevel ?? '—', mono: true },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.implementationStatus ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.implementationStatus ?? '—')}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => ctrlS.startEdit(r)} onDelete={() => ctrlS.askDelete(r)} /> },
  ];
  const subCtrlCols: Column<CyberSubControlDto>[] = [
    { header: t('colSubControl'), cell: r => <span style={{ fontWeight: 600 }}>{r.subControlNumber} · {r.titleEn}</span> },
    { header: t('colParent'),     cell: r => `${t('colControl')} #${r.controlId}`, mono: true },
    { header: t('colOwner'),      cell: r => r.controlOwner ?? '—' },
    { header: t('colMaturity'),   cell: r => r.maturityLevel ?? '—', mono: true },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.implementationStatus ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.implementationStatus ?? '—')}</Badge> },
    { header: t('colEvidence'),   cell: r => r.evidenceRequired ? <Badge tone="warning">{t('badgeRequired')}</Badge> : <Badge tone="neutral">{t('badgeOptional')}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => subS.startEdit(r)} onDelete={() => subS.askDelete(r)} /> },
  ];
  const assessCols: Column<CyberAssessmentDto>[] = [
    { header: t('colDate'),     cell: r => r.assessmentDate ?? '—', mono: true },
    { header: t('colControl'),  cell: r => `#${r.controlId}`, mono: true },
    { header: t('colAssessor'), cell: r => <span style={{ fontWeight: 600 }}>{r.assessorName ?? '—'}</span> },
    { header: t('colResult'),   cell: r => <Badge tone={r.result === 'PASS' ? 'success' : r.result === 'PARTIAL' ? 'warning' : r.result === 'FAIL' ? 'danger' : 'neutral'}>{r.result ?? '—'}</Badge> },
    { header: t('colMaturity'), cell: r => <MaturityBadge level={r.maturityScore ?? 0} /> },
    { header: t('colRisk'),     cell: r => <Badge tone={SEVERITY_TONE[r.riskLevel ?? ''] ?? 'neutral'} dot>{severityLabel(t, r.riskLevel ?? '—')}</Badge> },
    { header: t('colGap'),      cell: r => r.gapIdentified ? <Badge tone="danger">{t('colGap')}</Badge> : <Badge tone="success">{t('badgeNone')}</Badge> },
    { header: t('colStatus'),   cell: r => <Badge tone={STATUS_TONE[r.status ?? ''] ?? 'neutral'} dot>{statusLabel(t, r.status ?? '—')}</Badge> },
    { header: '', align: 'end', width: '160px',
      cell: r => {
        const extras: Array<{ Icon: typeof CheckCheck; onClick: () => void; title: string; tone?: 'primary' | 'success' | 'accent' }> = [];
        if (r.status !== 'APPROVED' && r.status !== 'CLOSED') {
          extras.push({ Icon: CheckCheck, onClick: () => approveAssess(r), title: t('actionApprove'), tone: 'success' });
        }
        if (r.gapIdentified && !r.capaId) {
          extras.push({ Icon: GitBranchPlus, onClick: () => convertGap(r), title: t('cyberActConvertGap'), tone: 'accent' });
        }
        return (
          <RowActions extras={extras}
            onEdit={() => assessS.startEdit(r)}
            onDelete={() => assessS.askDelete(r)} />
        );
      } },
  ];
  const vulnCols: Column<CyberVulnDto>[] = [
    { header: t('colAsset'),    cell: r => <span style={{ fontWeight: 600 }}>{r.assetName ?? '—'}</span> },
    { header: t('colTitle'),    cell: r => r.titleEn },
    { header: t('colCve'),      cell: r => r.cveId ?? '—', mono: true },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity] ?? 'neutral'} dot>{severityLabel(t, r.severity)}</Badge> },
    { header: t('colCvss'),     cell: r => r.cvssScore != null ? Number(r.cvssScore).toFixed(1) : '—', mono: true },
    { header: t('colOwner'),    cell: r => r.ownerName ?? '—' },
    { header: t('colDue'),      cell: r => r.dueDate ?? '—', mono: true },
    { header: t('colStage'),    cell: r => <Badge tone={STATUS_TONE[r.workflowStatus ?? ''] ?? 'neutral'} dot>{(r.workflowStatus ?? '').replace(/_/g, ' ') || '—'}</Badge> },
    { header: '', align: 'end', width: '120px',
      cell: r => (
        <RowActions
          extras={r.workflowStatus !== 'CLOSED' ? [{ Icon: TrendingUp, onClick: () => escalateVuln(r), title: t('actionAdvance'), tone: 'accent' }] : []}
          onEdit={() => vulnS.startEdit(r)}
          onDelete={() => vulnS.askDelete(r)}
        />
      ) },
  ];
  const excCols: Column<CyberExceptionDto>[] = [
    { header: t('colTitle'),    cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colOwner'),    cell: r => r.ownerName ?? '—' },
    { header: t('colApprover'), cell: r => r.approverName ?? '—' },
    { header: t('colExpiry'),   cell: r => r.expiryDate ?? '—', mono: true },
    { header: t('colStage'),    cell: r => <Badge tone={STATUS_TONE[r.workflowStatus ?? ''] ?? 'neutral'} dot>{(r.workflowStatus ?? '').replace(/_/g, ' ') || '—'}</Badge> },
    { header: '', align: 'end', width: '120px',
      cell: r => (
        <RowActions
          extras={r.workflowStatus !== 'APPROVED' && r.workflowStatus !== 'EXPIRED' ? [{ Icon: CheckCheck, onClick: () => approveExc(r), title: t('actionApprove'), tone: 'success' }] : []}
          onEdit={() => excS.startEdit(r)}
          onDelete={() => excS.askDelete(r)}
        />
      ) },
  ];
  const incidentCols: Column<CyberIncidentDto>[] = [
    { header: t('colId'),       cell: r => r.incidentNumber, mono: true },
    { header: t('colTitle'),    cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colType'),     cell: r => <Badge tone="info">{r.incidentType ?? '—'}</Badge> },
    { header: t('colSeverity'), cell: r => <Badge tone={SEVERITY_TONE[r.severity ?? ''] ?? 'neutral'} dot>{severityLabel(t, r.severity ?? '—')}</Badge> },
    { header: t('colLead'),     cell: r => r.incidentLead ?? '—', mono: true },
    { header: t('colAsset'),    cell: r => r.affectedAssetName ?? '—' },
    { header: t('colStage'),    cell: r => <Badge tone={STATUS_TONE[r.workflowStatus ?? r.status ?? ''] ?? 'neutral'} dot>{((r.workflowStatus ?? r.status ?? '')).replace(/_/g, ' ') || '—'}</Badge> },
    { header: '', align: 'end', width: '120px',
      cell: r => (
        <RowActions
          extras={r.workflowStatus === 'DETECTION' || r.workflowStatus === 'LOGGING' ? [{ Icon: PlayCircle, onClick: () => startResponse(r), title: t('actionAdvance'), tone: 'success' }] : []}
          onEdit={() => incS.startEdit(r)}
          onDelete={() => incS.askDelete(r)}
        />
      ) },
  ];
  const assetCols: Column<CyberAssetDto>[] = [
    { header: t('colAsset'),          cell: r => <span style={{ fontWeight: 600 }}>{r.assetName}</span> },
    { header: t('colType'),           cell: r => <Badge tone="info">{r.assetType ?? '—'}</Badge> },
    { header: t('colHostname'),       cell: r => r.hostname ?? '—', mono: true },
    { header: t('colIp'),             cell: r => r.ipAddress ?? '—', mono: true },
    { header: t('colClassification'), cell: r => <Badge tone="primary">{r.classification ?? '—'}</Badge> },
    { header: t('colCriticality'),    cell: r => <Badge tone={r.criticality === 'CRITICAL' ? 'danger' : r.criticality === 'HIGH' ? 'warning' : 'info'}>{r.criticality ?? '—'}</Badge> },
    { header: t('colStatus'),         cell: r => <Badge tone={r.status === 'ACTIVE' ? 'success' : 'neutral'} dot>{r.status ?? '—'}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => assetS.startEdit(r)} onDelete={() => assetS.askDelete(r)} /> },
  ];
  const evidenceCols: Column<CyberEvidenceDto>[] = [
    { header: t('colTitle'),     cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colType'),      cell: r => <Badge tone="neutral">{(r.evidenceType ?? '').replace(/_/g, ' ')}</Badge> },
    { header: t('colFramework'), cell: r => <Badge tone="primary">{r.frameworkRequirement ?? '—'}</Badge> },
    { header: t('colOwner'),     cell: r => r.ownerName ?? '—' },
    { header: t('colVersion'),   cell: r => r.version ?? '—', mono: true },
    { header: t('colExpiry'),    cell: r => r.expiryDate ?? '—', mono: true },
    { header: t('colStatus'),    cell: r => <Badge tone={r.status === 'CURRENT' ? 'success' : r.status === 'EXPIRED' ? 'danger' : 'warning'} dot>{r.status ?? '—'}</Badge> },
    { header: '', align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => evS.startEdit(r)} onDelete={() => evS.askDelete(r)} /> },
  ];
  const actionCols: Column<CyberActionPlanDto>[] = [
    { header: t('colPlan'),       cell: r => <span style={{ fontWeight: 600 }}>{r.titleEn}</span> },
    { header: t('colOwner'),      cell: r => r.ownerName ?? '—' },
    { header: t('colPriority'),   cell: r => <Badge tone={r.priority === 'HIGH' || r.priority === 'CRITICAL' ? 'danger' : r.priority === 'MEDIUM' ? 'warning' : 'info'}>{r.priority ?? '—'}</Badge> },
    { header: t('colSourceType'), cell: r => <Badge tone="info">{r.sourceType ?? '—'}</Badge> },
    { header: t('colDue'),        cell: r => r.dueDate ?? '—', mono: true },
    { header: t('colProgress'),   cell: r => <ProgressInline value={r.progressPct ?? r.progressPercent ?? 0} /> },
    { header: t('colStatus'),     cell: r => <Badge tone={STATUS_TONE[r.workflowStatus ?? ''] ?? 'neutral'} dot>{(r.workflowStatus ?? '').replace(/_/g, ' ') || '—'}</Badge> },
    { header: '', align: 'end', width: '120px',
      cell: r => (
        <RowActions
          extras={r.workflowStatus !== 'COMPLETED' && r.workflowStatus !== 'CANCELLED' ? [{ Icon: Check, onClick: () => completePlan(r), title: t('actionApprove'), tone: 'success' }] : []}
          onEdit={() => apS.startEdit(r)}
          onDelete={() => apS.askDelete(r)}
        />
      ) },
  ];

  // ── New-button dispatcher (current tab determines the modal) ──
  function newAction() {
    return () => {
      switch (tab) {
        case 'subcontrols': return subS.startCreate();
        case 'assessments': return assessS.startCreate();
        case 'vuln':        return vulnS.startCreate();
        case 'exceptions':  return excS.startCreate();
        case 'incidents':   return incS.startCreate();
        case 'assets':      return assetS.startCreate();
        case 'evidence':    return evS.startCreate();
        case 'actions':     return apS.startCreate();
        default:            return ctrlS.startCreate();
      }
    };
  }

  const focusVuln = vulns.data.find(v => v.workflowStatus !== 'CLOSED' && v.workflowStatus !== 'VERIFICATION') ?? vulns.data[0];

  // ── CSV export of the active tab — DD-46 ──────────────────────────────────
  function exportActiveTab() {
    const map: Record<string, { rows: ExportRow[]; name: string }> = {
      controls:    { rows: controls.data    as unknown as ExportRow[], name: 'cyber-controls' },
      subcontrols: { rows: subControls.data as unknown as ExportRow[], name: 'cyber-subcontrols' },
      assessments: { rows: assessments.data as unknown as ExportRow[], name: 'cyber-assessments' },
      vuln:        { rows: vulns.data       as unknown as ExportRow[], name: 'cyber-vulnerabilities' },
      exceptions:  { rows: exceptions.data  as unknown as ExportRow[], name: 'cyber-exceptions' },
      incidents:   { rows: cyberIncidents.data as unknown as ExportRow[], name: 'cyber-incidents' },
      assets:      { rows: assets.data      as unknown as ExportRow[], name: 'cyber-assets' },
      evidence:    { rows: cyberEvidence.data as unknown as ExportRow[], name: 'cyber-evidence' },
      actions:     { rows: actionPlans.data as unknown as ExportRow[], name: 'cyber-action-plans' },
    };
    const cur = map[tab] ?? map.controls;
    downloadCsv(cur.rows, stampFilename(cur.name, 'csv'));
    toast.success(t('exportCsvDone'));
  }

  return (
    <Shell title={t('modCyber')} sub="NCA ECC · ISO 27001:2022">
      <PageHeader
        eyebrow={t('cyberEyebrow')} title={t('cyberTitle')} sub={t('cyberSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter} onClick={() => toast.info(t('actionFilterHint'))}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download} onClick={() => exportActiveTab()}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={newAction()}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={ShieldCheck}    label={t('cyberKpiControls')}   value={controls.data.length} tone="primary" />
        <Kpi Icon={Bug}            label={t('cyberKpiVuln')}       value={vulns.data.filter(v => v.workflowStatus !== 'CLOSED').length} tone="warning" />
        <Kpi Icon={Flame}          label={t('cyberKpiIncidents')}  value={cyberIncidents.data.filter(i => i.severity === 'CRITICAL' || i.severity === 'HIGH').length} tone="danger" />
        <Kpi Icon={ClipboardCheck} label={t('cyberKpiCompliance')} value="87%" trend={{ dir: 'up', value: '+4%' }} tone="success" />
      </div>

      <OverviewStrip
        storageKey="overview.cyber"
        processBarTitle="Cybersecurity — Processes & Lifecycles"
        processBarSubtitle="NIST CSF 2.0 + NIST SP 800-61 + ISO 27001:2022 + NCA ECC — every named process active in this domain."
        lifecycles={CYBER_LIFECYCLES}
        postureTitle="Regulator Posture · Cybersecurity"
        anchors={CYBER_ANCHORS}
        lang="en" />

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'overview' && (
        <>
          <div className="dash-grid">
            <Card title={t('cardVulnsBySev')} Icon={BarChart3}>
              <BarChart3D items={[
                { label: t('severityCritical'), value: vulns.data.filter(v => v.severity === 'CRITICAL').length, color: 'var(--danger)', shade: '#7E2A25' },
                { label: t('severityHigh'),     value: vulns.data.filter(v => v.severity === 'HIGH').length,     color: 'var(--cat-6)',  shade: '#5C3220' },
                { label: t('severityMedium'),   value: vulns.data.filter(v => v.severity === 'MEDIUM').length,   color: 'var(--cat-2)',  shade: '#7A5612' },
                { label: t('severityLow'),      value: vulns.data.filter(v => v.severity === 'LOW').length,      color: 'var(--cat-3)',  shade: '#324754' },
              ]} />
            </Card>
            <Card title={t('cardFwReadiness')} Icon={Activity}>
              <Progress label="NCA ECC"     value={87} tone="primary" />
              <Progress label="ISO 27001"   value={91} tone="success" />
              <Progress label="MITRE ATT&CK" value={64} tone="accent" />
              <Progress label="SAMA CSF"    value={73} tone="primary" />
            </Card>
          </div>
          {focusVuln && (
            <Card title={t('cardVulnLifecycle')} Icon={Target} action={<Badge tone="info">{focusVuln.titleEn?.slice(0, 50) ?? '—'}</Badge>}>
              <Stepper steps={VULN_STAGES} active={focusVuln.workflowStatus ?? 'REGISTERED'} />
            </Card>
          )}
        </>
      )}

      {tab === 'controls'    && <Card padded={false}>{controls.loading    ? <LoadingRow /> : <DataTable columns={controlCols}  rows={controls.data}    empty={<EmptyState />} />}</Card>}
      {tab === 'subcontrols' && <Card padded={false}>{subControls.loading ? <LoadingRow /> : <DataTable columns={subCtrlCols}  rows={subControls.data} empty={<EmptyState Icon={Shield}        title={t('cyberEmptySubControls')} />} />}</Card>}
      {tab === 'assessments' && <Card padded={false}>{assessments.loading ? <LoadingRow /> : <DataTable columns={assessCols}   rows={assessments.data} empty={<EmptyState Icon={ClipboardCheck} title={t('cyberEmptyAssessments')} />} />}</Card>}
      {tab === 'vuln'        && <Card padded={false}>{vulns.loading       ? <LoadingRow /> : <DataTable columns={vulnCols}     rows={vulns.data}       empty={<EmptyState Icon={Bug} />} />}</Card>}
      {tab === 'exceptions'  && <Card padded={false}>{exceptions.loading  ? <LoadingRow /> : <DataTable columns={excCols}      rows={exceptions.data}  empty={<EmptyState Icon={ShieldOff}      title={t('cyberEmptyExceptions')} sub={t('cyberEmptyExceptionsSub')} />} />}</Card>}
      {tab === 'incidents'   && (
        <>
          <Card padded={false}>{cyberIncidents.loading ? <LoadingRow /> : <DataTable columns={incidentCols} rows={cyberIncidents.data} empty={<EmptyState Icon={Flame} title={t('cyberEmptyIncidents')} />} />}</Card>
          {cyberIncidents.data[0] && (
            <div style={{ marginTop: 16 }}>
              <Card title={t('cardRespWorkflow')} Icon={Target} action={<Badge tone="info">{cyberIncidents.data[0].incidentNumber}</Badge>}>
                <Stepper steps={INCIDENT_STAGES} active={mapIncidentStage(cyberIncidents.data[0].workflowStatus ?? cyberIncidents.data[0].status)} />
              </Card>
            </div>
          )}
        </>
      )}
      {tab === 'assets'   && <Card padded={false}>{assets.loading        ? <LoadingRow /> : <DataTable columns={assetCols}    rows={assets.data}        empty={<EmptyState Icon={Cpu}        title={t('cyberEmptyAssets')} />} />}</Card>}
      {tab === 'evidence' && <Card padded={false}>{cyberEvidence.loading ? <LoadingRow /> : <DataTable columns={evidenceCols} rows={cyberEvidence.data} empty={<EmptyState Icon={FileCheck2} title={t('cyberEmptyEvidence')} />} />}</Card>}
      {tab === 'actions'  && <Card padded={false}>{actionPlans.loading   ? <LoadingRow /> : <DataTable columns={actionCols}   rows={actionPlans.data}   empty={<EmptyState Icon={ListTodo}   title={t('cyberEmptyActions')} />} />}</Card>}
      {tab === 'workflows' && <ActiveWorkflowsPanel
        workflows={CYBER_WORKFLOWS}
        title={t('cyberTabWorkflows') ?? 'Workflows'}
        sub="Cyber incident response (NIST 800-61), vulnerability remediation, control assessments, and exception approvals."
        t={t} />}
      {tab === 'comms' && <CommsPanel module="INCIDENT" />}

      {/* ── Modals ── */}
      <FormModal<CyberControlRequest> open={ctrlS.open}
        title={ctrlS.editing ? `${t('editPrefix')} · ${ctrlS.editing.controlNumber}` : t('newCyberControlTitle')}
        fields={CONTROL_FIELDS}
        initial={ctrlS.editing ? controlToReq(ctrlS.editing) : CONTROL_EMPTY}
        onClose={ctrlS.closeModal} onSubmit={submitControl} />

      <FormModal<CyberSubControlRequest> open={subS.open}
        title={subS.editing ? `${t('editPrefix')} · ${subS.editing.subControlNumber}` : t('newSubControlTitle')}
        fields={SUB_FIELDS}
        initial={subS.editing ? subCtrlToReq(subS.editing) : SUBCTRL_EMPTY}
        onClose={subS.closeModal} onSubmit={submitSub} />

      <FormModal<CyberAssessmentRequest> open={assessS.open}
        title={assessS.editing ? `${t('editPrefix')} · #${assessS.editing.id}` : t('newAssessmentTitle')}
        fields={ASSESS_FIELDS}
        initial={assessS.editing ? assessToReq(assessS.editing) : ASSESS_EMPTY}
        onClose={assessS.closeModal} onSubmit={submitAssess} />

      <FormModal<CyberVulnRequest> open={vulnS.open}
        title={vulnS.editing ? `${t('editPrefix')} · ${vulnS.editing.titleEn}` : t('newVulnTitle')}
        fields={VULN_FIELDS}
        initial={vulnS.editing ? vulnToReq(vulnS.editing) : VULN_EMPTY}
        onClose={vulnS.closeModal} onSubmit={submitVuln} />

      <FormModal<CyberExceptionRequest> open={excS.open}
        title={excS.editing ? `${t('editPrefix')} · ${excS.editing.titleEn}` : t('newExceptionTitle')}
        fields={EXC_FIELDS}
        initial={excS.editing ? excToReq(excS.editing) : EXC_EMPTY}
        onClose={excS.closeModal} onSubmit={submitExc} />

      <FormModal<Record<string, unknown>> open={incS.open}
        title={incS.editing ? `${t('editPrefix')} · ${incS.editing.incidentNumber}` : t('newCyberIncidentTitle')}
        fields={INC_FIELDS}
        initial={incS.editing ? incToShape(incS.editing) : INC_EMPTY_BASE}
        onClose={incS.closeModal} onSubmit={submitInc} />

      <FormModal<CyberAssetRequest> open={assetS.open}
        title={assetS.editing ? `${t('editPrefix')} · ${assetS.editing.assetName}` : t('newAssetTitle')}
        fields={ASSET_FIELDS}
        initial={assetS.editing ? assetToReq(assetS.editing) : ASSET_EMPTY}
        onClose={assetS.closeModal} onSubmit={submitAsset} />

      <FormModal<CyberEvidenceRequest> open={evS.open}
        title={evS.editing ? `${t('editPrefix')} · ${evS.editing.titleEn}` : t('newCyberEvidenceTitle')}
        fields={EV_FIELDS}
        initial={evS.editing ? evToReq(evS.editing) : EV_EMPTY}
        onClose={evS.closeModal} onSubmit={submitEv} />

      <FormModal<CyberActionPlanRequest> open={apS.open}
        title={apS.editing ? `${t('editPrefix')} · ${apS.editing.titleEn}` : t('newActionPlanTitle')}
        fields={AP_FIELDS}
        initial={apS.editing ? apToReq(apS.editing) : AP_EMPTY}
        onClose={apS.closeModal} onSubmit={submitAp} />

      {/* ── Confirms ── */}
      <ConfirmDialog open={!!ctrlS.confirmDel}    destructive busy={busy} title="Delete this control?"        message={ctrlS.confirmDel    ? `Control "${ctrlS.confirmDel.controlNumber} · ${ctrlS.confirmDel.titleEn}" will be removed.` : ''}        confirmLabel="Delete" onCancel={ctrlS.closeConfirm}    onConfirm={() => execDelete(ctrlS.confirmDel,    cyberApi.deleteControl,    ctrlS.closeConfirm,    controls.reload, 'Control')} />
      <ConfirmDialog open={!!subS.confirmDel}     destructive busy={busy} title="Delete this sub-control?"    message={subS.confirmDel     ? `Sub-control "${subS.confirmDel.subControlNumber}" will be removed.` : ''}                                  confirmLabel="Delete" onCancel={subS.closeConfirm}     onConfirm={() => execDelete(subS.confirmDel,     cyberApi.deleteSubControl, subS.closeConfirm,     subControls.reload, 'Sub-control')} />
      <ConfirmDialog open={!!assessS.confirmDel}  destructive busy={busy} title="Delete this assessment?"      message={assessS.confirmDel  ? `Assessment #${assessS.confirmDel.id} will be removed.` : ''}                                                  confirmLabel="Delete" onCancel={assessS.closeConfirm}  onConfirm={() => execDelete(assessS.confirmDel,  cyberApi.deleteAssessment, assessS.closeConfirm,  assessments.reload, 'Assessment')} />
      <ConfirmDialog open={!!vulnS.confirmDel}    destructive busy={busy} title="Delete this vulnerability?"   message={vulnS.confirmDel    ? `Vulnerability "${vulnS.confirmDel.titleEn}" will be removed.` : ''}                                          confirmLabel="Delete" onCancel={vulnS.closeConfirm}    onConfirm={() => execDelete(vulnS.confirmDel,    cyberApi.deleteVuln,       vulnS.closeConfirm,    vulns.reload, 'Vulnerability')} />
      <ConfirmDialog open={!!excS.confirmDel}     destructive busy={busy} title="Delete this exception?"       message={excS.confirmDel     ? `Exception "${excS.confirmDel.titleEn}" will be removed.` : ''}                                                confirmLabel="Delete" onCancel={excS.closeConfirm}     onConfirm={() => execDelete(excS.confirmDel,     cyberApi.deleteException,  excS.closeConfirm,     exceptions.reload, 'Exception')} />
      <ConfirmDialog open={!!incS.confirmDel}     destructive busy={busy} title="Delete this incident?"        message={incS.confirmDel     ? `Incident "${incS.confirmDel.incidentNumber} · ${incS.confirmDel.titleEn}" will be removed.` : ''}              confirmLabel="Delete" onCancel={incS.closeConfirm}     onConfirm={() => execDelete(incS.confirmDel,     cyberApi.deleteIncident,   incS.closeConfirm,     cyberIncidents.reload, 'Incident')} />
      <ConfirmDialog open={!!assetS.confirmDel}   destructive busy={busy} title="Delete this asset?"           message={assetS.confirmDel   ? `Asset "${assetS.confirmDel.assetName}" will be removed.` : ''}                                                confirmLabel="Delete" onCancel={assetS.closeConfirm}   onConfirm={() => execDelete(assetS.confirmDel,   cyberApi.deleteAsset,      assetS.closeConfirm,   assets.reload, 'Asset')} />
      <ConfirmDialog open={!!evS.confirmDel}      destructive busy={busy} title="Delete this evidence?"        message={evS.confirmDel      ? `Evidence "${evS.confirmDel.titleEn}" will be removed.` : ''}                                                  confirmLabel="Delete" onCancel={evS.closeConfirm}      onConfirm={() => execDelete(evS.confirmDel,      cyberApi.deleteEvidence,   evS.closeConfirm,      cyberEvidence.reload, 'Evidence')} />
      <ConfirmDialog open={!!apS.confirmDel}      destructive busy={busy} title="Delete this action plan?"     message={apS.confirmDel      ? `Action plan "${apS.confirmDel.titleEn}" will be removed.` : ''}                                              confirmLabel="Delete" onCancel={apS.closeConfirm}      onConfirm={() => execDelete(apS.confirmDel,      cyberApi.deleteActionPlan, apS.closeConfirm,      actionPlans.reload, 'Action plan')} />
    </Shell>
  );
}

function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
function ProgressInline({ value }: { value: number }) {
  const safe = Math.max(0, Math.min(100, value));
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, minWidth: 120 }}>
      <div style={{ flex: 1, height: 6, background: 'var(--surface-3)', borderRadius: 'var(--r-pill)', overflow: 'hidden' }}>
        <div style={{ width: `${safe}%`, height: '100%', background: 'linear-gradient(90deg, var(--primary-300), var(--primary))' }} />
      </div>
      <span className="mono" style={{ minWidth: 32 }}>{safe}%</span>
    </div>
  );
}
export function MaturityBadge({ level }: { level: number }) {
  const cls = `maturity maturity-${Math.max(1, Math.min(5, Math.round(level)))}`;
  const labels = ['Initial', 'Developing', 'Defined', 'Managed', 'Optimised'];
  return (
    <span className={cls}>
      <span className="maturity-pip" />
      L{level} · {labels[Math.max(0, Math.min(4, Math.round(level) - 1))]}
    </span>
  );
}
function mapIncidentStage(stage?: string): string {
  if (!stage) return 'DETECTION';
  const u = stage.toUpperCase();
  for (const s of ['DETECTION','TRIAGE','CONTAINMENT','INVESTIGATION','ERADICATION','RECOVERY','CLOSURE']) {
    if (u.includes(s)) return s;
  }
  return 'DETECTION';
}
