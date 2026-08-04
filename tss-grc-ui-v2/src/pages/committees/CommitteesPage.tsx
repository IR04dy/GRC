import { useEffect, useMemo, useState, Fragment } from 'react';
import { Link } from 'react-router-dom';
import {
  Landmark, CheckCircle2, XCircle, Calendar, Play, FileCheck2, Users,
  ChevronRight, ChevronDown, Gavel, ClipboardList, ListChecks, RefreshCw, Vote,
  ExternalLink, AlertTriangle, ShieldAlert, Plus, Ban, FileDown,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { MeetingPapersPanel } from '../../components/committees/MeetingPapersPanel';
import { ChangeAuthorityPanel } from '../../components/committees/ChangeAuthorityPanel';
import { useI18n } from '../../lib/i18n';
import { statusLabel } from '../../lib/statusLabels';
import { useToast } from '../../lib/toast';
import { canExport } from '../../lib/auth';
import { reportingApi, ApiError } from '../../lib/api';
import {
  listCommittees, listMeetings, startMeeting, completeMeeting, approveMinutes, cancelMeeting,
  listResolutions, listActions, generateAnnualCalendar, populateAgenda, createResolution, createMeeting,
  createCommittee, createAction, dissolveCommittee,
  startAction, completeAction, cancelAction, deferAction,
  parseAgenda, parseAttendees, parseDecisions, parseActions, parseMembers,
  COM_STATUS_TONE, MTG_STATUS_TONE, OUTCOME_TONE, ACTION_STATUS_TONE, ACTION_PRIORITY_TONE,
  type Committee, type CommitteeMeeting, type Resolution, type ActionItemRecord,
  type ResolutionOutcome, type ConfidentialityLevel, type CommitteeType, type MeetingCadence,
  type ActionPriority,
} from '../../lib/committees';
import { AttendanceModal } from './AttendanceModal';
import { ResolutionVotesModal } from './ResolutionVotesModal';

/** Committee type -> registry-code stem, mirroring the seeded set (COM-RISK, COM-AUDIT, …). */
const TYPE_CODE_STEM: Record<CommitteeType, string> = {
  BOARD: 'BOARD', EXEC_COMMITTEE: 'EXEC', RISK_COMMITTEE: 'RISK', AUDIT_COMMITTEE: 'AUDIT',
  BCM_COMMITTEE: 'BCM', COMPLIANCE_COMMITTEE: 'COMPLIANCE', TECH_COMMITTEE: 'TECH',
  NOMINATION_REMUNERATION: 'NOMREM', CYBER_SECURITY: 'CYBER', DATA_GOVERNANCE: 'DATAGOV',
  PRIVACY_DPO: 'PRIVACY', CRISIS_MANAGEMENT: 'CRISIS', AML: 'AML', IT_STEERING: 'IT',
  ALCO: 'ALCO', INVESTMENT: 'INVEST', AI_ETHICS: 'AIETHICS', ANTI_FRAUD: 'ANTIFRAUD',
  INTERNAL_CONTROL: 'INTCTRL', ESG_SUSTAINABILITY: 'ESG', ARCH_REVIEW: 'ARB', OTHER: 'COMMITTEE',
};
const COMMITTEE_TYPES = Object.keys(TYPE_CODE_STEM) as CommitteeType[];
const TYPE_LABEL_EN: Record<CommitteeType, string> = {
  BOARD: 'Board of Directors', EXEC_COMMITTEE: 'Executive Committee',
  RISK_COMMITTEE: 'Risk Management Committee', AUDIT_COMMITTEE: 'Audit Committee',
  BCM_COMMITTEE: 'BCM Steering Committee', COMPLIANCE_COMMITTEE: 'Compliance Committee',
  TECH_COMMITTEE: 'Technology Committee', NOMINATION_REMUNERATION: 'Nomination & Remuneration Committee',
  CYBER_SECURITY: 'Cybersecurity Committee', DATA_GOVERNANCE: 'Data Governance Committee',
  PRIVACY_DPO: 'Personal Data Protection Committee', CRISIS_MANAGEMENT: 'Crisis Management Committee',
  AML: 'Anti-Money-Laundering Committee', IT_STEERING: 'IT Steering Committee',
  ALCO: 'Asset & Liability Committee (ALCO)', INVESTMENT: 'Investment Committee',
  AI_ETHICS: 'AI Ethics Committee', ANTI_FRAUD: 'Anti-Fraud Committee',
  INTERNAL_CONTROL: 'Internal Control Committee', ESG_SUSTAINABILITY: 'Sustainability Committee',
  ARCH_REVIEW: 'Architecture Review Board', OTHER: 'Other',
};
const TYPE_LABEL_AR: Record<CommitteeType, string> = {
  BOARD: 'مجلس الإدارة', EXEC_COMMITTEE: 'اللجنة التنفيذية', RISK_COMMITTEE: 'لجنة إدارة المخاطر',
  AUDIT_COMMITTEE: 'لجنة المراجعة', BCM_COMMITTEE: 'لجنة استمرارية الأعمال',
  COMPLIANCE_COMMITTEE: 'لجنة الالتزام', TECH_COMMITTEE: 'لجنة التقنية',
  NOMINATION_REMUNERATION: 'لجنة الترشيحات والمكافآت', CYBER_SECURITY: 'لجنة الأمن السيبراني',
  DATA_GOVERNANCE: 'لجنة حوكمة البيانات', PRIVACY_DPO: 'لجنة حماية البيانات الشخصية',
  CRISIS_MANAGEMENT: 'لجنة إدارة الأزمات', AML: 'لجنة مكافحة غسل الأموال',
  IT_STEERING: 'لجنة توجيه تقنية المعلومات', ALCO: 'لجنة الأصول والخصوم',
  INVESTMENT: 'لجنة الاستثمار', AI_ETHICS: 'لجنة أخلاقيات الذكاء الاصطناعي',
  ANTI_FRAUD: 'لجنة مكافحة الاحتيال', INTERNAL_CONTROL: 'لجنة الرقابة الداخلية',
  ESG_SUSTAINABILITY: 'لجنة الاستدامة', ARCH_REVIEW: 'مجلس مراجعة المعمارية', OTHER: 'أخرى',
};
const CADENCES: MeetingCadence[] = ['WEEKLY', 'BIWEEKLY', 'MONTHLY', 'QUARTERLY', 'AD_HOC'];
const CADENCE_LABEL_EN: Record<MeetingCadence, string> = {
  WEEKLY: 'Weekly', BIWEEKLY: 'Bi-weekly', MONTHLY: 'Monthly', QUARTERLY: 'Quarterly', AD_HOC: 'Ad hoc',
};
const CADENCE_LABEL_AR: Record<MeetingCadence, string> = {
  WEEKLY: 'أسبوعي', BIWEEKLY: 'نصف شهري', MONTHLY: 'شهري', QUARTERLY: 'ربع سنوي', AD_HOC: 'حسب الحاجة',
};

type TabId = 'committees' | 'scheduled' | 'in_progress' | 'completed' | 'approved' | 'cancelled'
           | 'resolutions' | 'actions';

/** Cross-module navigation map for linked action items. The action item's
 *  linkedModule is one of RISK/INCIDENT/COMPLIANCE/AUDIT/BCM/CYBER/PRIVACY/SCM
 *  — we resolve it to the right detail route. */
const MODULE_LINK_BUILDERS: Record<string, (id: number | null, code: string | null) => string | null> = {
  RISK:       (id) => (id ? `/risk/${id}` : '/risk'),
  INCIDENT:   (id) => (id ? `/incidents` : '/incidents'),       // incident detail = /incidents (modal-based)
  COMPLIANCE: ()   => `/compliance`,
  AUDIT:      ()   => `/audit-lc`,
  BCM:        ()   => `/bcm`,
  CYBER:      ()   => `/cyber`,
  PRIVACY:    ()   => `/privacy`,
  SCM:        ()   => `/scm`,
  TPRM:       ()   => `/tprm`,
  POLICY:     ()   => `/policy`,
};

function moduleLink(module: string | null, id: number | null, _code: string | null): string | null {
  if (!module) return null;
  const builder = MODULE_LINK_BUILDERS[module.toUpperCase()];
  return builder ? builder(id, _code) : null;
}

export function CommitteesPage() {
  const { t, lang } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('committees');
  const [committees, setCommittees] = useState<Committee[]>([]);
  const [meetings, setMeetings] = useState<CommitteeMeeting[]>([]);
  const [resolutions, setResolutions] = useState<Resolution[]>([]);
  const [actions, setActions] = useState<ActionItemRecord[]>([]);
  const [loading, setLoading] = useState(true);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [attendanceMeeting, setAttendanceMeeting] = useState<CommitteeMeeting | null>(null);   // CH-14
  const [votesResolution, setVotesResolution] = useState<Resolution | null>(null);             // CH-14
  const [expanded, setExpanded] = useState<Set<string>>(new Set());
  const [openResolution, setOpenResolution] = useState(false);
  const [openMeeting, setOpenMeeting] = useState(false);
  const [openCommittee, setOpenCommittee] = useState(false);
  const [openAction, setOpenAction] = useState(false);
  const [dissolveTarget, setDissolveTarget] = useState<Committee | null>(null);

  useEffect(() => { void reload(); }, []);

  async function reload() {
    setLoading(true);
    // Per-call .catch() so partial backend failures don't blank the whole
    // page. A fresh dev DB without V154-V159 bootstrapped returns 503
    // ("Module not yet bootstrapped" from the global handler in PR #117),
    // which used to reject Promise.all and trigger the outer catch ->
    // toast.danger -> "unable to load data" on every tab. Now each tab
    // degrades to an empty list independently. Both endpoints already
    // had this guard for resolutions + actions; this commit extends it
    // to committees + meetings.
    const [c, m, r, a] = await Promise.all([
      listCommittees(undefined, 0, 200).catch(() => ({ content: [] as Committee[], totalElements: 0 })),
      listMeetings(undefined, 0, 200).catch(() => ({ content: [] as CommitteeMeeting[], totalElements: 0 })),
      listResolutions(0, 200).catch(() => ({ content: [] as Resolution[], totalElements: 0 })),
      listActions(0, 200).catch(() => ({ content: [] as ActionItemRecord[], totalElements: 0 })),
    ]);
    setCommittees(c.content);
    setMeetings(m.content);
    setResolutions(r.content);
    setActions(a.content);
    setLoading(false);
  }

  const stats = useMemo(() => ({
    activeCommittees: committees.filter(c => c.status === 'ACTIVE').length,
    upcoming:         meetings.filter(m => m.status === 'SCHEDULED').length,
    inProgress:       meetings.filter(m => m.status === 'IN_PROGRESS').length,
    completed:        meetings.filter(m => m.status === 'COMPLETED').length,
    approved:         meetings.filter(m => m.status === 'MINUTES_APPROVED').length,
    cancelled:        meetings.filter(m => m.status === 'CANCELLED').length,
    resolutions:      resolutions.length,
    openActions:      actions.filter(a => a.status === 'OPEN' || a.status === 'IN_PROGRESS').length,
    overdueActions:   actions.filter(a => a.status === 'OVERDUE').length,
  }), [committees, meetings, resolutions, actions]);

  const tabs: TabItem<TabId>[] = [
    { id: 'committees',  label: `${t('comTabCommittees')} (${committees.length})` },
    { id: 'scheduled',   label: `${t('comTabScheduled')} (${stats.upcoming})` },
    { id: 'in_progress', label: `${t('comTabInProgress')} (${stats.inProgress})` },
    { id: 'completed',   label: `${t('comTabCompleted')} (${stats.completed})` },
    { id: 'approved',    label: `${t('comTabApproved')} (${stats.approved})` },
    { id: 'cancelled',   label: `${t('comTabCancelled')} (${stats.cancelled})` },
    { id: 'resolutions', label: `${t('comTabResolutions')} (${resolutions.length})` },
    { id: 'actions',     label: `${t('comTabActionRegister')} (${stats.openActions + stats.overdueActions})` },
  ];

  const filteredMeetings = useMemo(() => {
    if (tab === 'committees' || tab === 'resolutions' || tab === 'actions') return [];
    if (tab === 'scheduled')   return meetings.filter(m => m.status === 'SCHEDULED');
    if (tab === 'in_progress') return meetings.filter(m => m.status === 'IN_PROGRESS');
    if (tab === 'completed')   return meetings.filter(m => m.status === 'COMPLETED');
    if (tab === 'approved')    return meetings.filter(m => m.status === 'MINUTES_APPROVED');
    if (tab === 'cancelled')   return meetings.filter(m => m.status === 'CANCELLED');
    return [];
  }, [tab, meetings]);

  const committeeNameFor = (committeeId: number): string => {
    const c = committees.find(x => x.id === committeeId);
    if (!c) return `#${committeeId}`;
    return lang === 'ar' && c.nameAr ? c.nameAr : c.nameEn;
  };

  // ─── Record a resolution against a meeting ───────────────────────
  const isAr = lang === 'ar';
  // S7a / F2-89 — PROPOSED first, and it is the DEFAULT. This list used to start at APPROVED and
  // `resolutionInitial` defaulted the field to it, so the only screen that creates a resolution
  // pre-filled a verdict nobody had voted on — and creating it published
  // COMMITTEE_RESOLUTION_APPROVED, which AR-146 turns into a 24 h regulator disclosure clock and
  // AR-147 into "file this in the board-resolutions register within 48 h".
  const OUTCOMES: ResolutionOutcome[] = ['PROPOSED', 'APPROVED', 'REJECTED', 'DEFERRED', 'WITHDRAWN'];
  const CONFIDENTIALITIES: ConfidentialityLevel[] = ['PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED'];
  const OUTCOME_AR: Record<ResolutionOutcome, string> = {
    PROPOSED: 'مطروح', APPROVED: 'معتمَد', REJECTED: 'مرفوض', DEFERRED: 'مؤجَّل', WITHDRAWN: 'مسحوب',
  };
  const CONF_AR: Record<ConfidentialityLevel, string> = {
    PUBLIC: 'عام', INTERNAL: 'داخلي', CONFIDENTIAL: 'سرّي', RESTRICTED: 'مقيَّد',
  };
  const CONF_EN: Record<ConfidentialityLevel, string> = {
    PUBLIC: 'Public', INTERNAL: 'Internal', CONFIDENTIAL: 'Confidential', RESTRICTED: 'Restricted',
  };
  const meetingLabel = (m: CommitteeMeeting): string => {
    const title = (isAr ? m.titleAr : m.titleEn) || m.titleEn || committeeNameFor(m.committeeId);
    return `${m.meetingCode} · ${title}`;
  };
  const resolutionFields: FieldDef[] = [
    { key: 'meetingId', label: isAr ? 'الاجتماع' : 'Meeting', kind: 'select-searchable', required: true, span: 12,
      placeholder: isAr ? 'اختر اجتماعًا…' : 'Select a meeting…',
      options: meetings.map(m => ({ value: String(m.id), label: meetingLabel(m), sub: committeeNameFor(m.committeeId) })) },
    { key: 'titleEn', label: isAr ? 'عنوان القرار (إنجليزي)' : 'Resolution title (English)', kind: 'text', required: true, maxLength: 500, span: 12 },
    { key: 'titleAr', label: isAr ? 'عنوان القرار (عربي)' : 'Resolution title (Arabic)', kind: 'text', maxLength: 500, span: 12 },
    { key: 'bodyEn', label: isAr ? 'نص القرار (إنجليزي)' : 'Resolution body (English)', kind: 'textarea', rows: 3, span: 12 },
    { key: 'bodyAr', label: isAr ? 'نص القرار (عربي)' : 'Resolution body (Arabic)', kind: 'textarea', rows: 3, span: 12 },
    { key: 'outcome', label: isAr ? 'النتيجة' : 'Outcome', kind: 'select', required: true, span: 4,
      options: OUTCOMES.map(o => ({ value: o, label: isAr ? OUTCOME_AR[o] : o.charAt(0) + o.slice(1).toLowerCase() })) },
    { key: 'confidentiality', label: isAr ? 'السرّية' : 'Confidentiality', kind: 'select', required: true, span: 4,
      options: CONFIDENTIALITIES.map(c => ({ value: c, label: isAr ? CONF_AR[c] : CONF_EN[c] })) },
    { key: 'regulatorNotify', label: isAr ? 'إشعار الجهة التنظيمية؟' : 'Notify regulator?', kind: 'select', required: true, span: 4,
      options: [{ value: 'false', label: isAr ? 'لا' : 'No' }, { value: 'true', label: isAr ? 'نعم' : 'Yes' }] },
    { key: 'voteFor', label: isAr ? 'مع' : 'For', kind: 'number', min: 0, span: 4 },
    { key: 'voteAgainst', label: isAr ? 'ضد' : 'Against', kind: 'number', min: 0, span: 4 },
    { key: 'voteAbstain', label: isAr ? 'امتناع' : 'Abstain', kind: 'number', min: 0, span: 4 },
    { key: 'proposedBy', label: isAr ? 'مُقدِّم القرار' : 'Proposed by', kind: 'text', maxLength: 255, span: 6 },
    { key: 'secondedBy', label: isAr ? 'مؤيِّد القرار' : 'Seconded by', kind: 'text', maxLength: 255, span: 6 },
    { key: 'effectiveFrom', label: isAr ? 'سارٍ من' : 'Effective from', kind: 'date', span: 6 },
    { key: 'reviewRequiredBy', label: isAr ? 'مراجعة مطلوبة قبل' : 'Review required by', kind: 'date', span: 6 },
    { key: 'dissentNotesEn', label: isAr ? 'ملاحظات الاعتراض (إنجليزي)' : 'Dissent notes (English)', kind: 'textarea', rows: 2, span: 12 },
    { key: 'dissentNotesAr', label: isAr ? 'ملاحظات الاعتراض (عربي)' : 'Dissent notes (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  const resolutionInitial = {
    meetingId: '', titleEn: '', titleAr: '', bodyEn: '', bodyAr: '',
    // F2-89 — raise the motion, do not pre-decide it. See OUTCOMES above.
    outcome: 'PROPOSED', confidentiality: 'INTERNAL', regulatorNotify: 'false',
    voteFor: '0', voteAgainst: '0', voteAbstain: '0',
    proposedBy: '', secondedBy: '', effectiveFrom: '', reviewRequiredBy: '',
    dissentNotesEn: '', dissentNotesAr: '',
  };

  async function submitResolution(v: Record<string, string>) {
    const meetingId = Number(v.meetingId);
    const meeting = meetings.find(m => m.id === meetingId);
    const titleEn = String(v.titleEn || '').trim();
    const bodyEn = String(v.bodyEn || '').trim();
    if (!meeting || !titleEn || !bodyEn || !v.outcome || !v.confidentiality) {
      toast.danger(isAr ? 'يُرجى تعبئة الحقول المطلوبة' : 'Please fill the required fields');
      return;
    }
    // seq is per-meeting; resolutionCode is unique per org. Derive both from
    // the meeting + the resolutions already on record so the new row slots in
    // sequentially and the generated code can't collide with an existing one.
    const existing = resolutions.filter(r => r.meetingId === meetingId);
    let seq = existing.reduce((mx, r) => Math.max(mx, r.seq || 0), 0) + 1;
    const codes = new Set(resolutions.map(r => r.resolutionCode));
    let resolutionCode = `RES-${meeting.meetingCode}-${String(seq).padStart(3, '0')}`;
    while (codes.has(resolutionCode)) {
      seq += 1;
      resolutionCode = `RES-${meeting.meetingCode}-${String(seq).padStart(3, '0')}`;
    }
    const numOr0 = (x: string) => { const n = Number(x); return Number.isFinite(n) ? n : 0; };
    try {
      await createResolution({
        meetingId,
        committeeId: meeting.committeeId,
        resolutionCode,
        seq,
        titleEn,
        titleAr: String(v.titleAr || '').trim() || null,
        bodyEn,
        bodyAr: String(v.bodyAr || '').trim() || null,
        proposedBy: String(v.proposedBy || '').trim() || null,
        secondedBy: String(v.secondedBy || '').trim() || null,
        voteFor: numOr0(v.voteFor),
        voteAgainst: numOr0(v.voteAgainst),
        voteAbstain: numOr0(v.voteAbstain),
        outcome: v.outcome as ResolutionOutcome,
        confidentiality: v.confidentiality as ConfidentialityLevel,
        effectiveFrom: String(v.effectiveFrom || '').trim() || null,
        reviewRequiredBy: String(v.reviewRequiredBy || '').trim() || null,
        regulatorNotify: v.regulatorNotify === 'true' ? 1 : 0,
        dissentNotesEn: String(v.dissentNotesEn || '').trim() || null,
        dissentNotesAr: String(v.dissentNotesAr || '').trim() || null,
      });
      toast.success(isAr ? `تم تسجيل القرار ${resolutionCode}` : `Resolution ${resolutionCode} recorded`);
      setOpenResolution(false);
      setTab('resolutions');
      await reload();
    } catch (e) {
      toast.danger(isAr ? 'تعذّر تسجيل القرار' : 'Failed to record the resolution');
    }
  }

  // ─── Schedule a meeting ──────────────────────────────────────────
  const MEETING_TIMES = ['08:00', '09:00', '10:00', '11:00', '12:00', '13:00', '14:00', '15:00', '16:00', '17:00'];
  const meetingFields: FieldDef[] = [
    { key: 'committeeId', label: isAr ? 'اللجنة' : 'Committee', kind: 'select-searchable', required: true, span: 12,
      placeholder: isAr ? 'اختر لجنة…' : 'Select a committee…',
      options: committees.map(c => ({ value: String(c.id), label: `${c.code} · ${lang === 'ar' && c.nameAr ? c.nameAr : c.nameEn}` })) },
    { key: 'titleEn', label: isAr ? 'عنوان الاجتماع (إنجليزي)' : 'Meeting title (English)', kind: 'text', maxLength: 255, span: 12 },
    { key: 'titleAr', label: isAr ? 'عنوان الاجتماع (عربي)' : 'Meeting title (Arabic)', kind: 'text', maxLength: 255, span: 12 },
    { key: 'meetingDate', label: isAr ? 'تاريخ الاجتماع' : 'Meeting date', kind: 'date', span: 6 },
    { key: 'meetingTime', label: isAr ? 'الوقت' : 'Time', kind: 'select', required: true, span: 6,
      options: MEETING_TIMES.map(tm => ({ value: tm, label: tm })) },
    { key: 'locationOrLink', label: isAr ? 'المكان أو الرابط' : 'Location or link', kind: 'text', maxLength: 500, span: 12 },
  ];
  const meetingInitial = {
    committeeId: '', titleEn: '', titleAr: '', meetingDate: '', meetingTime: '10:00', locationOrLink: '',
  };

  async function submitMeeting(v: Record<string, string>) {
    const committeeId = Number(v.committeeId);
    const committee = committees.find(c => c.id === committeeId);
    if (!committee || !v.meetingDate || !v.meetingTime) {
      toast.danger(isAr ? 'يُرجى اختيار اللجنة والتاريخ والوقت' : 'Please choose a committee, date and time');
      return;
    }
    const scheduledAt = `${v.meetingDate}T${v.meetingTime}:00`;
    try {
      await createMeeting({
        committeeId,
        titleEn: String(v.titleEn || '').trim() || undefined,
        titleAr: String(v.titleAr || '').trim() || undefined,
        scheduledAt,
        locationOrLink: String(v.locationOrLink || '').trim() || undefined,
      });
      toast.success(isAr ? 'تم جدولة الاجتماع' : 'Meeting scheduled');
      setOpenMeeting(false);
      setTab('scheduled');
      await reload();
    } catch (e) {
      toast.danger(isAr ? 'تعذّر جدولة الاجتماع' : 'Failed to schedule the meeting');
    }
  }

  // ─── Charter a committee ─────────────────────────────────────────
  /** Next free registry code for a type: COM-<STEM>, collision-safe against loaded committees. */
  function nextCommitteeCode(type: CommitteeType): string {
    const base = `COM-${TYPE_CODE_STEM[type]}`;
    const used = new Set(committees.map(c => c.code));
    if (!used.has(base)) return base;
    let n = 2;
    let code = `${base}-${n}`;
    while (used.has(code)) { n += 1; code = `${base}-${n}`; }
    return code;
  }

  const committeeFields: FieldDef[] = [
    { key: 'nameEn', label: isAr ? 'اسم اللجنة (إنجليزي)' : 'Committee name (English)', kind: 'text', required: true, span: 6, maxLength: 255 },
    { key: 'nameAr', label: isAr ? 'اسم اللجنة (عربي)' : 'Committee name (Arabic)', kind: 'text', span: 6, maxLength: 255 },
    { key: 'committeeType', label: isAr ? 'نوع اللجنة' : 'Committee type', kind: 'select', required: true, span: 6,
      options: COMMITTEE_TYPES.map(ct => ({ value: ct, label: isAr ? TYPE_LABEL_AR[ct] : TYPE_LABEL_EN[ct] })) },
    { key: 'meetingCadence', label: isAr ? 'وتيرة الاجتماعات' : 'Meeting cadence', kind: 'select', span: 6,
      options: CADENCES.map(cd => ({ value: cd, label: isAr ? CADENCE_LABEL_AR[cd] : CADENCE_LABEL_EN[cd] })) },
    { key: 'chairpersonName', label: isAr ? 'رئيس اللجنة' : 'Chairperson', kind: 'text', span: 6, maxLength: 255 },
    { key: 'secretaryName', label: isAr ? 'أمين السر' : 'Secretary', kind: 'text', span: 6, maxLength: 255 },
    { key: 'quorumPct', label: isAr ? 'نسبة النصاب (%)' : 'Quorum (%)', kind: 'number', span: 6, min: 0, max: 100 },
    { key: 'descriptionEn', label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)', kind: 'textarea', span: 12, rows: 2 },
    { key: 'descriptionAr', label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)', kind: 'textarea', span: 12, rows: 2 },
    { key: 'charterEn', label: isAr ? 'الميثاق (إنجليزي)' : 'Charter (English)', kind: 'textarea', span: 12, rows: 2 },
    { key: 'charterAr', label: isAr ? 'الميثاق (عربي)' : 'Charter (Arabic)', kind: 'textarea', span: 12, rows: 2 },
  ];
  // Memoised so its identity is stable across the page's live-refresh re-renders;
  // otherwise FormModal's reset effect (keyed on `initial`) would wipe the form mid-entry.
  const committeeInitial: Record<string, string> = useMemo(() => ({
    nameEn: '', nameAr: '', committeeType: 'EXEC_COMMITTEE', meetingCadence: 'MONTHLY',
    chairpersonName: '', secretaryName: '', quorumPct: '50',
    descriptionEn: '', descriptionAr: '', charterEn: '', charterAr: '',
  }), []);

  async function submitCommittee(v: Record<string, string>) {
    const nameEn = (v.nameEn ?? '').trim();
    const committeeType = v.committeeType as CommitteeType;
    if (!nameEn)        { toast.danger(isAr ? 'اسم اللجنة (إنجليزي) مطلوب' : 'Committee name (English) is required'); return; }
    if (!committeeType) { toast.danger(isAr ? 'نوع اللجنة مطلوب' : 'Committee type is required'); return; }
    const code = nextCommitteeCode(committeeType);
    const opt = (s?: string) => { const x = String(s ?? '').trim(); return x ? x : undefined; };
    // quorumPct comes back as a number from FormModal's number field (typed string), so stringify before trimming.
    const quorum = String(v.quorumPct ?? '').trim();
    try {
      await createCommittee({
        code, nameEn, nameAr: opt(v.nameAr),
        committeeType,
        descriptionEn: opt(v.descriptionEn), descriptionAr: opt(v.descriptionAr),
        chairpersonName: opt(v.chairpersonName), secretaryName: opt(v.secretaryName),
        charterEn: opt(v.charterEn), charterAr: opt(v.charterAr),
        meetingCadence: (v.meetingCadence as MeetingCadence) || undefined,
        quorumPct: quorum === '' ? undefined : Number(quorum),
      });
      toast.success(isAr ? `تم إنشاء اللجنة ${code}` : `Committee ${code} chartered`);
      setOpenCommittee(false);
      setTab('committees');
      await reload();
    } catch {
      toast.danger(isAr ? 'تعذّر إنشاء اللجنة' : 'Failed to charter the committee');
    }
  }

  // ─── Dissolve a committee ────────────────────────────────────────
  const dissolveFields: FieldDef[] = [
    { key: 'reason', label: isAr ? 'سبب الحل' : 'Dissolution reason', kind: 'textarea',
      required: true, span: 12, rows: 3 },
  ];
  const dissolveInitial: Record<string, string> = useMemo(() => ({ reason: '' }), []);

  async function submitDissolve(v: Record<string, string>) {
    if (!dissolveTarget) return;
    const reason = (v.reason ?? '').trim();
    if (!reason) { toast.danger(isAr ? 'سبب الحل مطلوب' : 'Dissolution reason is required'); return; }
    try {
      await dissolveCommittee(dissolveTarget.id, reason);
      toast.success(isAr ? `تم حلّ اللجنة ${dissolveTarget.code}` : `Committee ${dissolveTarget.code} dissolved`);
      setDissolveTarget(null);
      await reload();
    } catch {
      toast.danger(isAr ? 'تعذّر حلّ اللجنة' : 'Failed to dissolve the committee');
    }
  }

  // ─── Log an action item against a meeting ────────────────────────
  const PRIORITIES: ActionPriority[] = ['LOW', 'NORMAL', 'HIGH', 'CRITICAL'];
  const PRIORITY_AR: Record<ActionPriority, string> = {
    LOW: 'منخفضة', NORMAL: 'عادية', HIGH: 'عالية', CRITICAL: 'حرجة',
  };
  const PRIORITY_EN: Record<ActionPriority, string> = {
    LOW: 'Low', NORMAL: 'Normal', HIGH: 'High', CRITICAL: 'Critical',
  };
  const actionFields: FieldDef[] = [
    { key: 'meetingId', label: isAr ? 'الاجتماع' : 'Meeting', kind: 'select-searchable', required: true, span: 12,
      placeholder: isAr ? 'اختر اجتماعًا…' : 'Select a meeting…',
      options: meetings.map(m => ({ value: String(m.id), label: meetingLabel(m), sub: committeeNameFor(m.committeeId) })) },
    { key: 'titleEn', label: isAr ? 'عنوان المهمة (إنجليزي)' : 'Action title (English)', kind: 'text', required: true, maxLength: 500, span: 12 },
    { key: 'titleAr', label: isAr ? 'عنوان المهمة (عربي)' : 'Action title (Arabic)', kind: 'text', maxLength: 500, span: 12 },
    { key: 'ownerName', label: isAr ? 'المسؤول' : 'Owner', kind: 'text', required: true, maxLength: 255, span: 6 },
    { key: 'ownerRole', label: isAr ? 'صفة المسؤول' : 'Owner role', kind: 'text', maxLength: 120, span: 6 },
    { key: 'dueDate', label: isAr ? 'تاريخ الاستحقاق' : 'Due date', kind: 'date', span: 4 },
    { key: 'priority', label: isAr ? 'الأولوية' : 'Priority', kind: 'select', required: true, span: 4,
      options: PRIORITIES.map(p => ({ value: p, label: isAr ? PRIORITY_AR[p] : PRIORITY_EN[p] })) },
    { key: 'agendaItemSeq', label: isAr ? 'بند جدول الأعمال' : 'Agenda item #', kind: 'number', min: 1, span: 4 },
    { key: 'descriptionEn', label: isAr ? 'الوصف (إنجليزي)' : 'Description (English)', kind: 'textarea', rows: 2, span: 12 },
    { key: 'descriptionAr', label: isAr ? 'الوصف (عربي)' : 'Description (Arabic)', kind: 'textarea', rows: 2, span: 12 },
  ];
  // Memoised so the modal's reset effect (keyed on `initial`) can't wipe the
  // form during the page's live-refresh re-renders.
  const actionInitial: Record<string, string> = useMemo(() => ({
    meetingId: '', titleEn: '', titleAr: '', ownerName: '', ownerRole: '',
    dueDate: '', priority: 'NORMAL', agendaItemSeq: '', descriptionEn: '', descriptionAr: '',
  }), []);

  async function submitAction(v: Record<string, string>) {
    const meetingId = Number(v.meetingId);
    const meeting = meetings.find(m => m.id === meetingId);
    const titleEn = String(v.titleEn || '').trim();
    const ownerName = String(v.ownerName || '').trim();
    const dueDate = String(v.dueDate || '').trim();
    const priority = (v.priority as ActionPriority) || 'NORMAL';
    if (!meeting)   { toast.danger(isAr ? 'يُرجى اختيار اجتماع' : 'Please select a meeting'); return; }
    if (!titleEn)   { toast.danger(isAr ? 'عنوان المهمة (إنجليزي) مطلوب' : 'Action title (English) is required'); return; }
    if (!ownerName) { toast.danger(isAr ? 'اسم المسؤول مطلوب' : 'Owner name is required'); return; }
    if (!/^\d{4}-\d{2}-\d{2}$/.test(dueDate)) {
      toast.danger(isAr ? 'تاريخ الاستحقاق مطلوب' : 'A due date is required'); return;
    }
    // seq is per-meeting; action_code is unique per org (UQ_action_item_code).
    // Derive both collision-safe from the actions already on record so the new
    // row slots in sequentially and the generated code can't clash.
    const existing = actions.filter(a => a.meetingId === meetingId);
    let seq = existing.reduce((mx, a) => Math.max(mx, a.seq || 0), 0) + 1;
    const codes = new Set(actions.map(a => a.actionCode));
    let actionCode = `ACT-${meeting.meetingCode}-${String(seq).padStart(3, '0')}`;
    while (codes.has(actionCode)) {
      seq += 1;
      actionCode = `ACT-${meeting.meetingCode}-${String(seq).padStart(3, '0')}`;
    }
    const agendaStr = String(v.agendaItemSeq ?? '').trim();
    const agendaNum = agendaStr === '' ? null : Number(agendaStr);
    try {
      await createAction({
        meetingId,
        committeeId: meeting.committeeId,
        actionCode,
        seq,
        agendaItemSeq: agendaNum != null && Number.isFinite(agendaNum) ? agendaNum : null,
        titleEn,
        titleAr: String(v.titleAr || '').trim() || null,
        descriptionEn: String(v.descriptionEn || '').trim() || null,
        descriptionAr: String(v.descriptionAr || '').trim() || null,
        ownerName,
        ownerRole: String(v.ownerRole || '').trim() || null,
        dueDate,
        priority,
      });
      toast.success(isAr ? `تم تسجيل المهمة ${actionCode}` : `Action ${actionCode} logged`);
      setOpenAction(false);
      setTab('actions');
      await reload();
    } catch {
      toast.danger(isAr ? 'تعذّر تسجيل المهمة' : 'Failed to log the action');
    }
  }

  function toggle(key: string) {
    const n = new Set(expanded);
    if (n.has(key)) n.delete(key); else n.add(key);
    setExpanded(n);
  }

  async function onStart(id: number) {
    setBusyId(id);
    try { await startMeeting(id); toast.success(t('comMtgStarted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onComplete(id: number) {
    setBusyId(id);
    try { await completeMeeting(id); toast.success(t('comMtgCompleted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  // S7f/CE-14a — the meeting's own records. Downloads, so failures surface as a toast rather than
  // a silently-empty file: `downloadFile` throws ApiError on a non-2xx (a 403 from ADMIN:EXPORT
  // included), and swallowing that is how F2-68's "dead buttons" family started.
  async function onExportAgenda(meetingId: number) {
    try { await reportingApi.committeeAgendaPdf(meetingId, lang); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('comExportFailed')); }
  }
  async function onExportMinutes(meetingId: number) {
    try { await reportingApi.committeeMinutesPdf(meetingId, lang); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : t('comExportFailed')); }
  }

  async function onApproveMinutes(id: number) {
    setBusyId(id);
    try { await approveMinutes(id); toast.success(t('comMinutesApproved')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onCancel(id: number) {
    const reason = window.prompt(t('comCancelPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await cancelMeeting(id, reason.trim()); toast.success(t('comMtgCancelled')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Annual calendar generation (per-committee) ───────────────────────
  async function onGenerateCalendar(committeeId: number) {
    const yearStr = window.prompt(t('comCalendarPrompt'), String(new Date().getFullYear() + 1));
    if (!yearStr) return;
    const year = parseInt(yearStr.trim(), 10);
    if (!Number.isFinite(year) || year < 2020 || year > 2100) {
      toast.danger(t('comActionFailed'));
      return;
    }
    setBusyId(committeeId);
    try {
      const res = await generateAnnualCalendar(committeeId, year);
      toast.success(t('comCalendarGenerated').replace('{n}', String(res.created)).replace('{year}', String(year)));
      await reload();
    } catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Agenda auto-populator (per-meeting) ──────────────────────────────
  async function onPopulateAgenda(meetingId: number) {
    setBusyId(meetingId);
    try {
      await populateAgenda(meetingId);
      toast.success(t('comAgendaPopulated'));
      await reload();
    } catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  // ── Action item lifecycle ────────────────────────────────────────────
  async function onActionStart(id: number) {
    setBusyId(id);
    try { await startAction(id); toast.success(t('comActStarted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionComplete(id: number) {
    const notes = window.prompt(t('comActCompletePrompt'), '');
    if (notes == null) return;
    setBusyId(id);
    try { await completeAction(id, notes.trim()); toast.success(t('comActCompleted')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionDefer(id: number) {
    const newDate = window.prompt(t('comActDeferPrompt'), '');
    if (!newDate || !/^\d{4}-\d{2}-\d{2}$/.test(newDate.trim())) return;
    const reason = window.prompt(t('comActDeferReason'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await deferAction(id, newDate.trim(), reason.trim()); toast.success(t('comActDeferred')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }
  async function onActionCancel(id: number) {
    const reason = window.prompt(t('comActCancelPrompt'), '');
    if (!reason || !reason.trim()) return;
    setBusyId(id);
    try { await cancelAction(id, reason.trim()); toast.success(t('comActCancelled')); await reload(); }
    catch { toast.danger(t('comActionFailed')); }
    finally { setBusyId(null); }
  }

  return (
    <Shell title={t('comPageTitle')} sub={t('comPageSub')}>
      <PageHeader eyebrow={t('navAdministration')} title={t('comPageTitle')} sub={t('comPageSub')} />

      <div className="kpi-grid">
        <Kpi Icon={Landmark}    label={t('comKpiActive')}     value={stats.activeCommittees} tone="success"
             onClick={() => setTab('committees')} active={tab === 'committees'} />
        <Kpi Icon={Calendar}    label={t('comKpiUpcoming')}   value={stats.upcoming}         tone="primary"
             onClick={() => setTab('scheduled')} active={tab === 'scheduled'} />
        <Kpi Icon={Gavel}       label={t('comTabResolutions')} value={resolutions.length}    tone="info"
             onClick={() => setTab('resolutions')} active={tab === 'resolutions'} />
        <Kpi Icon={ListChecks}  label={t('comTabActionRegister')} value={stats.openActions}  tone={stats.overdueActions > 0 ? 'danger' : 'warning'}
             onClick={() => setTab('actions')} active={tab === 'actions'} />
      </div>

      <Tabs items={tabs} active={tab} onChange={setTab} />

      {/* ADR-017 D-2 — the screen the change-authority refusal names. Self-hiding for
          personas without ADMIN:READ, and above the register because an unconfigured
          gate blocks approvals platform-wide, not just on this page. */}
      {tab === 'committees' && <ChangeAuthorityPanel />}

      {tab === 'committees' && (
        <Card title={t('comTabCommittees')} Icon={Landmark}
          action={
            <div style={{ display: 'flex', gap: 8 }}>
              <Button variant="primary" Icon={Landmark} onClick={() => setOpenCommittee(true)}>
                {isAr ? 'إنشاء لجنة' : 'Charter committee'}
              </Button>
              <Button variant="secondary" Icon={Plus} disabled={committees.length === 0}
                      onClick={() => setOpenMeeting(true)}>
                {isAr ? 'جدولة اجتماع' : 'Schedule meeting'}
              </Button>
            </div>
          }>
          {loading
            ? <EmptyState Icon={Landmark} title={t('loading')} />
            : committees.length === 0
              ? <EmptyState Icon={Landmark} title={t('comEmpty')} sub={t('comEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comColCode')}</th>
                      <th>{t('comColName')}</th>
                      <th>{t('comColType')}</th>
                      <th>{t('comColChair')}</th>
                      <th>{t('comColCadence')}</th>
                      <th>{t('comColQuorum')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {committees.map(c => {
                      const k = `c-${c.id}`;
                      const open = expanded.has(k);
                      const members = parseMembers(c.membersJson);
                      return (
                        <Fragment key={c.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{c.code}</td>
                            <td>{lang === 'ar' && c.nameAr ? c.nameAr : c.nameEn}</td>
                            <td>{c.committeeType.replace(/_/g, ' ')}</td>
                            <td>{c.chairpersonName ?? '—'}</td>
                            <td>{c.meetingCadence}</td>
                            <td className="mono">{c.quorumPct != null ? `${c.quorumPct}%` : '—'}</td>
                            <td><Badge tone={COM_STATUS_TONE[c.status]}>{statusLabel(c.status, lang)}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              <Link to={`/committees/${c.id}/members`}
                                style={{ display:'inline-flex', alignItems:'center', gap:4, marginInlineEnd:8, fontSize:12, color:'var(--primary)' }}>
                                <Users size={12} />
                                {t('comMembers')}
                              </Link>
                              <Button size="sm" variant="secondary" Icon={Calendar}
                                disabled={busyId === c.id || c.status !== 'ACTIVE'}
                                onClick={() => onGenerateCalendar(c.id)}>
                                {t('comCalendarGenerate')}
                              </Button>
                              {c.status !== 'DISSOLVED' && (
                                <Button size="sm" variant="ghost" Icon={Ban}
                                  style={{ marginInlineStart: 8, color: 'var(--danger)' }}
                                  disabled={busyId === c.id}
                                  onClick={() => setDissolveTarget(c)}>
                                  {isAr ? 'حلّ اللجنة' : 'Dissolve'}
                                </Button>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {c.descriptionEn && (
                                    <p style={{ marginBottom: 8 }}>
                                      {lang === 'ar' && c.descriptionAr ? c.descriptionAr : c.descriptionEn}
                                    </p>
                                  )}
                                  {c.charterEn && (
                                    <p style={{ marginBottom: 8 }}>
                                      <strong>{t('comCharter')}:</strong>{' '}
                                      {lang === 'ar' && c.charterAr ? c.charterAr : c.charterEn}
                                    </p>
                                  )}
                                  {members.length > 0 && (
                                    <div>
                                      <strong>{t('comMembers')} ({members.length})</strong>
                                      <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {members.map((m, idx) => (
                                          <li key={idx}>
                                            {m.name} — {m.role}{m.votingMember ? ` · ${t('comVoting')}` : ''}
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                  {c.dissolvedReason && (
                                    <p style={{ marginTop: 8, color: 'var(--danger)' }}>
                                      <strong>{t('comDissolved')}:</strong> {c.dissolvedReason}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {(tab === 'scheduled' || tab === 'in_progress' || tab === 'completed' || tab === 'approved' || tab === 'cancelled') && (
        <Card title={t('comTabMeetings')} Icon={Calendar}>
          {loading
            ? <EmptyState Icon={Calendar} title={t('loading')} />
            : filteredMeetings.length === 0
              ? <EmptyState Icon={Calendar} title={t('comMtgEmpty')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comMtgCode')}</th>
                      <th>{t('comMtgCommittee')}</th>
                      <th>{t('comMtgTitle')}</th>
                      <th>{t('comMtgScheduled')}</th>
                      <th>{t('scnColStatus')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredMeetings.map(m => {
                      const k = `m-${m.id}`;
                      const open = expanded.has(k);
                      const agenda = parseAgenda(m.agendaJson);
                      const attendees = parseAttendees(m.attendeesJson);
                      const decisions = parseDecisions(m.decisionsJson);
                      const acts = parseActions(m.actionItemsJson);
                      return (
                        <Fragment key={m.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{m.meetingCode}</td>
                            <td>{m.committeeNameEn ?? m.committeeCode}</td>
                            <td>{lang === 'ar' && m.titleAr ? m.titleAr : m.titleEn}</td>
                            <td className="mono">{new Date(m.scheduledAt).toLocaleString()}</td>
                            <td><Badge tone={MTG_STATUS_TONE[m.status]}>{m.status.replace('_',' ')}</Badge></td>
                            <td style={{ textAlign: 'right' }}>
                              {m.status === 'SCHEDULED' && (
                                <>
                                  <Button size="sm" variant="secondary" Icon={RefreshCw}
                                    disabled={busyId === m.id} onClick={() => onPopulateAgenda(m.id)}>
                                    {t('comAgendaPopulate')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="primary" Icon={Play}
                                    disabled={busyId === m.id} onClick={() => onStart(m.id)}>
                                    {t('comStart')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="danger" Icon={XCircle}
                                    disabled={busyId === m.id} onClick={() => onCancel(m.id)}>
                                    {t('comCancel')}
                                  </Button>
                                </>
                              )}
                              {['SCHEDULED', 'IN_PROGRESS', 'COMPLETED'].includes(m.status) && (
                                <>
                                  <Button size="sm" variant="secondary" Icon={Users}
                                    disabled={busyId === m.id} onClick={() => setAttendanceMeeting(m)}>
                                    {t('comAttendanceQuorum')}
                                  </Button>
                                  {' '}
                                </>
                              )}
                              {m.status === 'IN_PROGRESS' && (
                                <Button size="sm" variant="primary" Icon={CheckCircle2}
                                  disabled={busyId === m.id} onClick={() => onComplete(m.id)}>
                                  {t('comComplete')}
                                </Button>
                              )}
                              {m.status === 'COMPLETED' && (
                                <Button size="sm" variant="primary" Icon={FileCheck2}
                                  disabled={busyId === m.id} onClick={() => onApproveMinutes(m.id)}>
                                  {t('comApproveMinutes')}
                                </Button>
                              )}
                              {/* S7f/CE-14a — the records a secretary circulates and files. Offered on
                                  every meeting, including SCHEDULED (an agenda is *for* circulating
                                  before the sitting) and drafts (the minutes PDF marks itself DRAFT
                                  rather than being withheld). Gated on CHANGE-free ADMIN:EXPORT
                                  server-side; canExport keeps a button the caller would be 403'd on
                                  out of the row. */}
                              {canExport('ADMIN') && (
                                <>
                                  {' '}
                                  <Button size="sm" variant="ghost" Icon={FileDown}
                                    onClick={() => onExportAgenda(m.id)}>
                                    {t('comExportAgenda')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="ghost" Icon={FileDown}
                                    onClick={() => onExportMinutes(m.id)}>
                                    {t('comExportMinutes')}
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={7} style={{ background: 'var(--surface-2)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {m.locationOrLink && (
                                    <p><strong>{t('comLocation')}:</strong> {m.locationOrLink}</p>
                                  )}
                                  {m.chairName && (
                                    <p><strong>{t('comChair')}:</strong> {m.chairName}
                                    {' · '}<strong>{t('comSecretary')}:</strong> {m.secretaryName}</p>
                                  )}
                                  {agenda.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comAgenda')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {agenda.map(a => (
                                          <li key={a.seq}>
                                            {lang === 'ar' && a.topicAr ? a.topicAr : a.topicEn}
                                            {a.presenter && <span style={{ color:'var(--ink-3)' }}> — {a.presenter}</span>}
                                            {a.minutesAllocated && <span className="mono"> ({a.minutesAllocated}m)</span>}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {attendees.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comAttendees')}</strong>
                                      <ul style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {attendees.map((a, i) => (
                                          <li key={i}>
                                            <Users size={12} style={{ display:'inline', marginInlineEnd:4 }}/>
                                            {a.name} — {a.role}
                                            {' · '}
                                            {a.present === true ? '✓ present' : a.present === false ? '✗ absent' : '—'}
                                          </li>
                                        ))}
                                      </ul>
                                    </div>
                                  )}
                                  {m.minutesEn && (
                                    <p style={{ marginTop: 8 }}>
                                      <strong>{t('comMinutes')}:</strong>{' '}
                                      {lang === 'ar' && m.minutesAr ? m.minutesAr : m.minutesEn}
                                    </p>
                                  )}
                                  {decisions.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comDecisions')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {decisions.map(d => (
                                          <li key={d.seq}>
                                            {lang === 'ar' && d.decisionAr ? d.decisionAr : d.decisionEn}
                                            {d.outcome && <Badge tone={d.outcome === 'APPROVED' ? 'success' : 'danger'}> {d.outcome}</Badge>}
                                            {d.voteFor != null && (
                                              <span style={{ color:'var(--ink-3)' }}>
                                                {' · '}{d.voteFor}/{(d.voteFor ?? 0) + (d.voteAgainst ?? 0) + (d.voteAbstain ?? 0)}
                                              </span>
                                            )}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  {acts.length > 0 && (
                                    <div style={{ marginTop: 8 }}>
                                      <strong>{t('comActions')}</strong>
                                      <ol style={{ paddingInlineStart: 18, marginTop: 4 }}>
                                        {acts.map(a => (
                                          <li key={a.seq}>
                                            {a.actionEn}
                                            {a.owner && <span style={{ color:'var(--ink-3)' }}> — {a.owner}</span>}
                                            {a.dueDate && <span className="mono"> · due {a.dueDate}</span>}
                                            {a.status && <Badge tone={a.status === 'COMPLETED' ? 'success' : a.status === 'IN_PROGRESS' ? 'primary' : 'info'}> {a.status}</Badge>}
                                          </li>
                                        ))}
                                      </ol>
                                    </div>
                                  )}
                                  <MeetingPapersPanel meetingId={m.id} isAr={lang === 'ar'} />
                                  {m.cancelReason && (
                                    <p style={{ marginTop: 8, color: 'var(--danger)' }}>
                                      <strong>{t('comCancelReason')}:</strong> {m.cancelReason}
                                    </p>
                                  )}
                                  {m.approverName && (
                                    <p style={{ marginTop: 8, color: 'var(--success)' }}>
                                      <strong>{t('comApprovedBy')}:</strong> {m.approverName}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {/* ── Resolutions register ─────────────────────────────────────── */}
      {tab === 'resolutions' && (
        <Card title={t('comTabResolutions')} Icon={Gavel}
          action={
            <Button variant="primary" Icon={Plus} disabled={meetings.length === 0}
                    onClick={() => setOpenResolution(true)}>
              {isAr ? 'تسجيل قرار' : 'Record resolution'}
            </Button>
          }>
          {loading
            ? <EmptyState Icon={Gavel} title={t('loading')} />
            : resolutions.length === 0
              ? <EmptyState Icon={Gavel} title={t('comResEmpty')} sub={t('comResEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comResColCode')}</th>
                      <th>{t('comResColTitle')}</th>
                      <th>{t('comResColCommittee')}</th>
                      <th>{t('comResColOutcome')}</th>
                      <th>{t('comResColVote')}</th>
                      <th>{t('comResColEffectiveFrom')}</th>
                      <th>{t('comResColReviewBy')}</th>
                      <th>{t('comResColConfidentiality')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {resolutions.map(r => {
                      const k = `r-${r.id}`;
                      const open = expanded.has(k);
                      const totalVotes = r.voteFor + r.voteAgainst + r.voteAbstain;
                      const hasDissent = (r.dissentNotesEn ?? '').trim().length > 0
                                      || (r.dissentNotesAr ?? '').trim().length > 0;
                      return (
                        <Fragment key={r.id}>
                          <tr>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{r.resolutionCode}</td>
                            <td>
                              {lang === 'ar' && r.titleAr ? r.titleAr : r.titleEn}
                              {hasDissent && (
                                <span title={t('comResDissent')} style={{ marginInlineStart: 6 }}>
                                  <AlertTriangle size={14} style={{ color: 'var(--warning)', display: 'inline' }} />
                                </span>
                              )}
                              {r.regulatorNotify === 1 && (
                                <span title={t('comResRegulatorNotify')} style={{ marginInlineStart: 4 }}>
                                  <ShieldAlert size={14} style={{ color: 'var(--danger)', display: 'inline' }} />
                                </span>
                              )}
                            </td>
                            <td>{committeeNameFor(r.committeeId)}</td>
                            <td><Badge tone={OUTCOME_TONE[r.outcome]}>{r.outcome}</Badge></td>
                            <td className="mono">{r.voteFor} / {r.voteAgainst} / {r.voteAbstain}
                              {totalVotes > 0 && <span style={{ color:'var(--ink-3)' }}> ({totalVotes})</span>}
                              {/* F2-40 — a tally recorded from the minutes and one counted from
                                  per-member votes are both legitimate and must not look alike. */}
                              {r.tallySource && r.tallySource !== 'COMPUTED' && (
                                <span title={r.tallySource === 'ASSERTED'
                                  ? (isAr
                                      ? `مُدرَج من المحضر بواسطة ${r.tallyRecordedByName ?? '—'}`
                                      : `Recorded from the minutes by ${r.tallyRecordedByName ?? 'an unnamed user'}`)
                                  : (isAr
                                      ? 'مُدرَج قبل تتبّع مصدر التصويت — لا يمكن التحقق من مصدره'
                                      : 'Recorded before vote provenance existed — source cannot be verified')}
                                  style={{ marginInlineStart: 6 }}>
                                  <Badge tone={r.tallySource === 'ASSERTED' ? 'info' : 'warning'}>
                                    {r.tallySource === 'ASSERTED'
                                      ? (isAr ? 'من المحضر' : 'from minutes')
                                      : (isAr ? 'قديم' : 'legacy')}
                                  </Badge>
                                </span>
                              )}
                            </td>
                            <td className="mono">{r.effectiveFrom ?? '—'}</td>
                            <td className="mono">{r.reviewRequiredBy ?? '—'}</td>
                            <td><Badge tone={r.confidentiality === 'RESTRICTED' ? 'danger'
                                            : r.confidentiality === 'CONFIDENTIAL' ? 'warning'
                                            : r.confidentiality === 'INTERNAL' ? 'info' : 'neutral'}>
                                  {r.confidentiality}
                                </Badge></td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  <p style={{ marginBottom: 8 }}>
                                    {lang === 'ar' && r.bodyAr ? r.bodyAr : r.bodyEn}
                                  </p>
                                  {r.proposedBy && (
                                    <p><strong>{t('comResProposedBy')}:</strong> {r.proposedBy}
                                      {r.secondedBy && <>{' · '}<strong>{t('comResSecondedBy')}:</strong> {r.secondedBy}</>}
                                    </p>
                                  )}
                                  {hasDissent && (
                                    <p style={{ marginTop: 8, color: 'var(--warning)' }}>
                                      <strong>{t('comResDissent')}:</strong>{' '}
                                      {lang === 'ar' && r.dissentNotesAr ? r.dissentNotesAr : r.dissentNotesEn}
                                    </p>
                                  )}
                                  <div style={{ marginTop: 10 }}>
                                    <Button size="sm" variant="ghost" Icon={Vote} onClick={() => setVotesResolution(r)}>
                                      {isAr ? 'التصويت الفردي' : 'Per-member votes'}
                                    </Button>
                                  </div>
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      {/* ── Action items register ────────────────────────────────────── */}
      {tab === 'actions' && (
        <Card title={t('comTabActionRegister')} Icon={ClipboardList}
          action={
            <Button variant="primary" Icon={Plus} disabled={meetings.length === 0}
                    onClick={() => setOpenAction(true)}>
              {isAr ? 'تسجيل مهمة' : 'Log action'}
            </Button>
          }>
          {loading
            ? <EmptyState Icon={ClipboardList} title={t('loading')} />
            : actions.length === 0
              ? <EmptyState Icon={ClipboardList} title={t('comActEmpty')} sub={t('comActEmptyHint')} />
              : (
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th style={{ width: 28 }} />
                      <th>{t('comActColCode')}</th>
                      <th>{t('comActColTitle')}</th>
                      <th>{t('comActColOwner')}</th>
                      <th>{t('comActColDue')}</th>
                      <th>{t('comActColPriority')}</th>
                      <th>{t('comActColStatus')}</th>
                      <th>{t('comActColLink')}</th>
                      <th style={{ textAlign: 'right' }}>{t('scnColActions')}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {actions.map(a => {
                      const k = `a-${a.id}`;
                      const open = expanded.has(k);
                      const link = moduleLink(a.linkedModule, a.linkedEntityId, a.linkedEntityCode);
                      const isOverdue = a.status === 'OVERDUE';
                      return (
                        <Fragment key={a.id}>
                          <tr style={isOverdue ? { background: 'rgba(220, 38, 38, 0.04)' } : undefined}>
                            <td>
                              <button onClick={() => toggle(k)}
                                style={{ background: 'none', border: 0, cursor: 'pointer', padding: 0 }}>
                                {open ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                              </button>
                            </td>
                            <td className="mono">{a.actionCode}</td>
                            <td>{lang === 'ar' && a.titleAr ? a.titleAr : a.titleEn}</td>
                            <td>{a.ownerName}{a.ownerRole && <span style={{ color:'var(--ink-3)' }}> — {a.ownerRole}</span>}</td>
                            <td className="mono">{a.dueDate}</td>
                            <td><Badge tone={ACTION_PRIORITY_TONE[a.priority]}>{a.priority}</Badge></td>
                            <td><Badge tone={ACTION_STATUS_TONE[a.status]}>{a.status.replace('_',' ')}</Badge></td>
                            <td>
                              {a.linkedModule && a.linkedEntityCode && link ? (
                                <Link to={link} className="mono"
                                  style={{ display:'inline-flex', alignItems:'center', gap:4 }}>
                                  {a.linkedModule}:{a.linkedEntityCode}
                                  <ExternalLink size={11} />
                                </Link>
                              ) : a.linkedModule ? (
                                <span className="mono" style={{ color:'var(--ink-3)' }}>
                                  {a.linkedModule}{a.linkedEntityCode ? `:${a.linkedEntityCode}` : ''}
                                </span>
                              ) : <span style={{ color:'var(--ink-3)' }}>—</span>}
                            </td>
                            <td style={{ textAlign: 'right' }}>
                              {a.status === 'OPEN' && (
                                <Button size="sm" variant="primary" Icon={Play}
                                  disabled={busyId === a.id} onClick={() => onActionStart(a.id)}>
                                  {t('comActStart')}
                                </Button>
                              )}
                              {(a.status === 'OPEN' || a.status === 'IN_PROGRESS' || a.status === 'OVERDUE') && (
                                <>
                                  {' '}
                                  <Button size="sm" variant="primary" Icon={CheckCircle2}
                                    disabled={busyId === a.id} onClick={() => onActionComplete(a.id)}>
                                    {t('comActComplete')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="secondary" Icon={Calendar}
                                    disabled={busyId === a.id} onClick={() => onActionDefer(a.id)}>
                                    {t('comActDefer')}
                                  </Button>
                                  {' '}
                                  <Button size="sm" variant="danger" Icon={XCircle}
                                    disabled={busyId === a.id} onClick={() => onActionCancel(a.id)}>
                                    {t('comActCancelBtn')}
                                  </Button>
                                </>
                              )}
                            </td>
                          </tr>
                          {open && (
                            <tr>
                              <td colSpan={9} style={{ background: 'var(--surface-2)' }}>
                                <div style={{ padding: 12, fontSize: 13 }}>
                                  {(a.descriptionEn || a.descriptionAr) && (
                                    <p style={{ marginBottom: 8 }}>
                                      {lang === 'ar' && a.descriptionAr ? a.descriptionAr : a.descriptionEn}
                                    </p>
                                  )}
                                  <p style={{ color:'var(--ink-3)' }}>
                                    {committeeNameFor(a.committeeId)}
                                    {' · '} meeting #{a.meetingId}
                                    {a.carriedFromMeetingId && <> {' · '} carried from meeting #{a.carriedFromMeetingId}</>}
                                  </p>
                                  {a.completionNotesEn && (
                                    <p style={{ marginTop: 8, color: 'var(--success)' }}>
                                      <strong>Completion notes:</strong> {a.completionNotesEn}
                                      {a.completedAt && <span style={{ color:'var(--ink-3)' }}> · {new Date(a.completedAt).toLocaleString()}</span>}
                                    </p>
                                  )}
                                </div>
                              </td>
                            </tr>
                          )}
                        </Fragment>
                      );
                    })}
                  </tbody>
                </table>
              )}
        </Card>
      )}

      <FormModal
        open={openResolution}
        title={isAr ? 'تسجيل قرار لجنة' : 'Record a committee resolution'}
        fields={resolutionFields}
        initial={resolutionInitial}
        submitLabel={isAr ? 'تسجيل' : 'Record'}
        size="lg"
        onClose={() => setOpenResolution(false)}
        onSubmit={submitResolution}
      />

      <FormModal
        open={openMeeting}
        title={isAr ? 'جدولة اجتماع لجنة' : 'Schedule a committee meeting'}
        fields={meetingFields}
        initial={meetingInitial}
        submitLabel={isAr ? 'جدولة' : 'Schedule'}
        size="lg"
        onClose={() => setOpenMeeting(false)}
        onSubmit={submitMeeting}
      />

      <FormModal
        open={openCommittee}
        title={isAr ? 'إنشاء لجنة جديدة' : 'Charter a new committee'}
        fields={committeeFields}
        initial={committeeInitial}
        submitLabel={isAr ? 'إنشاء' : 'Charter'}
        size="lg"
        onClose={() => setOpenCommittee(false)}
        onSubmit={submitCommittee}
      />

      <FormModal
        open={openAction}
        title={isAr ? 'تسجيل مهمة لجنة' : 'Log a committee action'}
        fields={actionFields}
        initial={actionInitial}
        submitLabel={isAr ? 'تسجيل' : 'Log action'}
        size="lg"
        onClose={() => setOpenAction(false)}
        onSubmit={submitAction}
      />

      <FormModal
        open={dissolveTarget !== null}
        title={dissolveTarget
          ? (isAr ? `حلّ اللجنة ${dissolveTarget.code}` : `Dissolve committee ${dissolveTarget.code}`)
          : ''}
        fields={dissolveFields}
        initial={dissolveInitial}
        submitLabel={isAr ? 'حلّ اللجنة' : 'Dissolve'}
        onClose={() => setDissolveTarget(null)}
        onSubmit={submitDissolve}
      />

      {attendanceMeeting && (
        <AttendanceModal
          meeting={attendanceMeeting}
          onClose={() => setAttendanceMeeting(null)}
          onSaved={reload}
        />
      )}

      {votesResolution && (
        <ResolutionVotesModal
          resolution={votesResolution}
          onClose={() => setVotesResolution(null)}
          onSaved={reload}
        />
      )}
    </Shell>
  );
}
