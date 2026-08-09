import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Users, Award, ShieldCheck, ChevronLeft, AlertCircle, BadgeCheck, UserPlus, Plus,
  RefreshCw, UserMinus, Link2,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Kpi } from '../../components/ui/Kpi';
import { Button } from '../../components/ui/Button';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  listCommittees, listMemberTerms, listSkills, skillCoverage, getIndependenceStats, appointMember,
  renewMemberTerm, endMemberTerm,
  addSkill,
  linkSeatUser, listLinkableUsers, getSeatLinkage,
  type Committee, type CommitteeMemberTerm, type CommitteeMemberSkill,
  type IndependenceStats, type LinkableUser, type SeatLinkageReport, type SeatLinkState,
} from '../../lib/committees';

/** Term-of-office roles (CMA/SAMA committee composition). */
const MEMBER_ROLES = ['Chair', 'Vice-Chair', 'Member', 'Secretary', 'Standing Invitee', 'External Advisor'] as const;
const ROLE_LABEL_AR: Record<string, string> = {
  'Chair': 'رئيس', 'Vice-Chair': 'نائب الرئيس', 'Member': 'عضو',
  'Secretary': 'أمين السر', 'Standing Invitee': 'مدعو دائم', 'External Advisor': 'مستشار خارجي',
};

/** GRC competency dimensions regulators test for (CMA Art 54, NCA ECC-2,
 *  ISO 22301, AML Law Art 41). Matches the backend skill_dimension vocab. */
const SKILL_DIMENSIONS: Array<{ value: string; en: string; ar: string }> = [
  { value: 'FINANCIAL_EXPERT', en: 'Financial expert',  ar: 'خبير مالي' },
  { value: 'CYBER_EXPERT',     en: 'Cyber expert',      ar: 'خبير أمن سيبراني' },
  { value: 'BCM_EXPERT',       en: 'BCM expert',        ar: 'خبير استمرارية الأعمال' },
  { value: 'AML_EXPERT',       en: 'AML expert',        ar: 'خبير مكافحة غسل الأموال' },
  { value: 'LEGAL',            en: 'Legal',             ar: 'قانوني' },
  { value: 'INDUSTRY',         en: 'Industry',          ar: 'خبرة قطاعية' },
  { value: 'DIGITAL',          en: 'Digital',           ar: 'رقمي' },
  { value: 'ESG',              en: 'ESG',               ar: 'الاستدامة' },
  { value: 'RISK_MGMT',        en: 'Risk management',   ar: 'إدارة المخاطر' },
];
const SKILL_LEVELS: Array<{ value: string; en: string; ar: string }> = [
  { value: 'BASIC',        en: 'Basic',        ar: 'أساسي' },
  { value: 'INTERMEDIATE', en: 'Intermediate', ar: 'متوسط' },
  { value: 'EXPERT',       en: 'Expert',       ar: 'خبير' },
];

/** F2-26 — the three states of a seat's platform-user link, and how each reads. */
const LINK_STATE: Record<SeatLinkState, { en: string; ar: string; tone: 'success' | 'neutral' | 'warning' }> = {
  LINKED:   { en: 'Linked',       ar: 'مرتبط',        tone: 'success' },
  EXTERNAL: { en: 'External',     ar: 'خارجي',        tone: 'neutral' },
  UNLINKED: { en: 'Not linked',   ar: 'غير مرتبط',    tone: 'warning' },
};

/** Label for a user in the picker: name, then the username as the disambiguator between
 *  colleagues with similar names — the same shape WorkflowActorSelector uses. */
function userLabel(u: LinkableUser, isAr: boolean): string {
  const name = (isAr ? u.fullNameAr : u.fullNameEn) || u.fullNameEn || u.username;
  return `${name} (${u.username})`;
}

/**
 * Member registry page — shows the current panel + the historical
 * appointments table + the skills matrix + the independence ratio for
 * one committee.
 *
 * <p>The independence-ratio KPI is anchored to CMA Corporate Governance
 * Regulation Article 54 (audit-committee majority-independents) and SAMA
 * RGov § 5.3.2 (board independence test). The skills-coverage card
 * answers "does this committee have a financial expert / cyber expert /
 * AML expert?" without the user having to scroll the matrix.
 *
 * <p>Route: `/committees/:committeeId/members`
 */
export function CommitteeMemberRegistryPage() {
  const { committeeId } = useParams<{ committeeId: string }>();
  const cid = committeeId ? Number(committeeId) : NaN;
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [committee, setCommittee] = useState<Committee | null>(null);
  const [terms, setTerms] = useState<CommitteeMemberTerm[]>([]);
  const [skills, setSkills] = useState<CommitteeMemberSkill[]>([]);
  const [coverage, setCoverage] = useState<Record<string, number>>({});
  const [indep, setIndep] = useState<IndependenceStats | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadKey, setReloadKey] = useState(0);
  const [openAppoint, setOpenAppoint] = useState(false);
  const [openSkill, setOpenSkill] = useState(false);
  const [renewTarget, setRenewTarget] = useState<CommitteeMemberTerm | null>(null);
  const [endTarget, setEndTarget] = useState<CommitteeMemberTerm | null>(null);
  // F2-26
  const [linkage, setLinkage] = useState<SeatLinkageReport | null>(null);
  const [linkable, setLinkable] = useState<LinkableUser[]>([]);
  const [linkTarget, setLinkTarget] = useState<CommitteeMemberTerm | null>(null);

  useEffect(() => {
    if (!Number.isFinite(cid)) return;
    let cancelled = false;
    setLoading(true);
    Promise.all([
      listCommittees(undefined, 0, 200).catch(() => ({ content: [] as Committee[], totalElements: 0 })),
      listMemberTerms(cid).catch(() => [] as CommitteeMemberTerm[]),
      listSkills(cid).catch(() => [] as CommitteeMemberSkill[]),
      skillCoverage(cid).catch(() => ({}) as Record<string, number>),
      getIndependenceStats(cid).catch(() => null as IndependenceStats | null),
      getSeatLinkage(cid).catch(() => null as SeatLinkageReport | null),
      // The picker needs ADMIN:WRITE; a read-only viewer gets 403 here and simply sees no
      // picker, which is correct — they cannot link seats either.
      listLinkableUsers(cid).catch(() => [] as LinkableUser[]),
    ]).then(([c, t, s, cov, ind, lnk, lu]) => {
      if (cancelled) return;
      setCommittee(c.content.find(x => x.id === cid) ?? null);
      setTerms(t);
      setSkills(s);
      setCoverage(cov);
      setIndep(ind);
      setLinkage(lnk);
      setLinkable(lu);
    }).finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [cid, reloadKey]);

  // Build a member → {dimension → level} matrix from the flat skills array.
  const matrix = useMemo(() => {
    const m: Record<string, Record<string, string>> = {};
    for (const s of skills) {
      if (!m[s.memberName]) m[s.memberName] = {};
      m[s.memberName][s.skillDimension] = s.skillLevel;
    }
    return m;
  }, [skills]);

  const dimensions = useMemo(() => {
    const set = new Set<string>();
    for (const s of skills) set.add(s.skillDimension);
    return Array.from(set).sort();
  }, [skills]);

  const activeTerms = terms.filter(t => t.renewalStatus === 'ACTIVE');
  const expiringSoon = activeTerms.filter(t => {
    if (!t.termEndsAt) return false;
    const days = (new Date(t.termEndsAt).getTime() - Date.now()) / 86_400_000;
    return days > 0 && days < 90;
  }).length;

  const title = committee
    ? (isAr && committee.nameAr ? committee.nameAr : committee.nameEn)
    : (isAr ? 'تسجيل أعضاء اللجنة' : 'Committee member registry');

  // ---- Appoint member ------------------------------------------------------
  // F2-26: only users with no active seat on THIS committee are offered — a second seat
  // would count one person twice toward quorum, so the service 409s. Greying them out here
  // means the secretary never has to discover that by submitting.
  const freeUsers = useMemo(() => linkable.filter(u => u.heldSeats === 0), [linkable]);

  const appointFields: FieldDef[] = [
    { key: 'memberName', kind: 'text', label: isAr ? 'اسم العضو' : 'Member name', required: true, span: 6, maxLength: 255 },
    { key: 'role', kind: 'select', label: isAr ? 'الدور' : 'Role', required: true, span: 6,
      options: MEMBER_ROLES.map(r => ({ value: r, label: isAr ? (ROLE_LABEL_AR[r] ?? r) : r })) },
    // Optional on purpose: a charter legitimately seats an external independent director
    // who has no login. Forcing a link would either block the appointment or invent a user.
    { key: 'memberUserId', kind: 'select-searchable', span: 12,
      label: isAr ? 'مستخدم المنصة (اختياري)' : 'Platform user (optional)',
      placeholder: isAr ? 'اتركه فارغاً للأعضاء الخارجيين…' : 'Leave empty for external members…',
      options: freeUsers.map(u => ({ value: String(u.id), label: userLabel(u, isAr) })),
      emptyHint: isAr
        ? 'الربط يتيح للعضو التصويت بنفسه؛ بدونه تُسجّل الأصوات نيابةً عنه.'
        : 'Linking lets the member cast their own vote; without it, votes are recorded on their behalf.' },
    { key: 'isIndependent', kind: 'select', label: isAr ? 'مستقل' : 'Independent', span: 6,
      options: [{ value: '1', label: isAr ? 'نعم' : 'Yes' }, { value: '0', label: isAr ? 'لا' : 'No' }] },
    { key: 'isVotingMember', kind: 'select', label: isAr ? 'عضو مصوّت' : 'Voting member', span: 6,
      options: [{ value: '1', label: isAr ? 'نعم' : 'Yes' }, { value: '0', label: isAr ? 'لا' : 'No' }] },
    { key: 'appointedAt', kind: 'date', label: isAr ? 'تاريخ التعيين' : 'Appointed on', span: 6 },
    { key: 'appointedByReso', kind: 'text', label: isAr ? 'تعيّن بموجب قرار' : 'Appointed by resolution', span: 6,
      placeholder: isAr ? 'مثال: RES-2026-COM-AUDIT-001' : 'e.g. RES-2026-COM-AUDIT-001' },
    { key: 'termStartsAt', kind: 'date', label: isAr ? 'بدء الفترة' : 'Term starts', span: 6 },
    { key: 'termEndsAt', kind: 'date', label: isAr ? 'انتهاء الفترة (اختياري)' : 'Term ends (optional)', span: 6 },
    { key: 'termNumber', kind: 'number', label: isAr ? 'رقم الفترة' : 'Term number', span: 6, min: 1 },
    { key: 'independenceBasisEn', kind: 'textarea', label: isAr ? 'أساس الاستقلالية' : 'Independence basis', span: 12, rows: 2 },
  ];

  const appointInitial: Record<string, string> = {
    memberName: '', role: 'Member', memberUserId: '', isIndependent: '1', isVotingMember: '1',
    appointedAt: '2026-06-01', appointedByReso: '', termStartsAt: '2026-06-01',
    termEndsAt: '2029-05-31', termNumber: '1', independenceBasisEn: '',
  };

  async function submitAppoint(v: Record<string, string>) {
    const memberName = (v.memberName ?? '').trim();
    const role = v.role;
    if (!memberName)      { toast.danger(isAr ? 'اسم العضو مطلوب' : 'Member name is required'); return; }
    if (!role)            { toast.danger(isAr ? 'الدور مطلوب' : 'Role is required'); return; }
    if (!v.appointedAt)   { toast.danger(isAr ? 'تاريخ التعيين مطلوب' : 'Appointment date is required'); return; }
    if (!v.termStartsAt)  { toast.danger(isAr ? 'تاريخ بدء الفترة مطلوب' : 'Term start date is required'); return; }
    if (v.termEndsAt && v.termEndsAt < v.termStartsAt) {
      toast.danger(isAr ? 'تاريخ انتهاء الفترة يجب أن يكون بعد تاريخ البدء' : 'Term end must be after term start'); return;
    }
    const opt = (s?: string) => { const x = (s ?? '').trim(); return x ? x : undefined; };
    try {
      await appointMember({
        committeeId: cid,
        memberName,
        role,
        memberUserId: v.memberUserId ? Number(v.memberUserId) : null,
        isIndependent: Number(v.isIndependent) === 1 ? 1 : 0,
        isVotingMember: Number(v.isVotingMember) === 1 ? 1 : 0,
        independenceBasisEn: opt(v.independenceBasisEn) ?? null,
        appointedAt: v.appointedAt,
        appointedByReso: opt(v.appointedByReso) ?? null,
        termStartsAt: v.termStartsAt,
        termEndsAt: opt(v.termEndsAt) ?? null,
        termNumber: v.termNumber ? Number(v.termNumber) : 1,
      });
      toast.success(isAr ? `تم تعيين ${memberName}` : `${memberName} appointed`);
      setOpenAppoint(false);
      setReloadKey(k => k + 1);
    } catch (e) {
      // The server now names the field (F2-25) and the conflict (F2-26 — "already holds an
      // active seat"). Swallowing that into a generic toast would waste both fixes: it is
      // the difference between "failed" and "that person already sits on this committee".
      toast.danger(e instanceof Error ? e.message
        : (isAr ? 'تعذّر تعيين العضو' : 'Failed to appoint member'));
    }
  }

  // ---- F2-26: link a seat to a platform user -------------------------------
  // Three states in one submit, because two endpoints ("link" / "declare external") could
  // disagree and leave a seat both linked and declared external.
  const linkFields: FieldDef[] = [
    { key: 'mode', kind: 'select', span: 12, required: true,
      label: isAr ? 'حالة المقعد' : 'Seat state',
      options: [
        { value: 'LINK',     label: isAr ? 'ربط بمستخدم في المنصة' : 'Link to a platform user' },
        { value: 'EXTERNAL', label: isAr ? 'عضو خارجي — لا يوجد حساب' : 'External member — no platform account' },
        { value: 'CLEAR',    label: isAr ? 'إلغاء الربط' : 'Clear the link' },
      ] },
    { key: 'memberUserId', kind: 'select-searchable', span: 12,
      label: isAr ? 'المستخدم' : 'Platform user',
      placeholder: isAr ? 'ابحث بالاسم أو اسم المستخدم…' : 'Search by name or username…',
      options: linkable
        // The seat being edited must still offer its own current holder, or a re-save of an
        // unrelated field would look like the link had been lost.
        .filter(u => u.heldSeats === 0 || u.id === linkTarget?.memberUserId)
        .map(u => ({ value: String(u.id), label: userLabel(u, isAr) })) },
    // FormModal's textarea takes no placeholder, so the example lives in the label — the
    // secretary needs to know what a *sufficient* basis looks like, not just that one is due.
    { key: 'basisEn', kind: 'textarea', span: 12, rows: 2,
      label: isAr
        ? 'سبب عدم وجود حساب (مطلوب للأعضاء الخارجيين) — مثال: شريك مكتب المراجعة الخارجي، يحضر بموجب المادة 54 ولا يملك حساباً'
        : 'Why there is no platform account (required for external) — e.g. “External audit firm partner, attends under CMA Article 54, holds no account”' },
    { key: 'reason', kind: 'textarea', span: 12, rows: 2,
      label: isAr ? 'ملاحظة للسجل (اختياري)' : 'Note for the audit ledger (optional)' },
  ];

  const linkInitial: Record<string, string> = useMemo(() => ({
    mode: linkTarget?.noPlatformUser ? 'EXTERNAL' : 'LINK',
    memberUserId: linkTarget?.memberUserId ? String(linkTarget.memberUserId) : '',
    basisEn: linkTarget?.noPlatformUserBasisEn ?? '',
    reason: '',
  }), [linkTarget]);

  async function submitLink(v: Record<string, string>) {
    if (!linkTarget) return;
    const mode = v.mode;
    if (mode === 'LINK' && !v.memberUserId) {
      toast.danger(isAr ? 'يُرجى اختيار مستخدم' : 'Please select a platform user'); return;
    }
    if (mode === 'EXTERNAL' && !String(v.basisEn ?? '').trim()) {
      toast.danger(isAr
        ? 'سبب عدم وجود حساب مطلوب للأعضاء الخارجيين'
        : 'A reason is required when declaring that a seat has no platform account');
      return;
    }
    const reason = String(v.reason ?? '').trim() || null;
    try {
      await linkSeatUser(linkTarget.id, mode === 'LINK'
        ? { memberUserId: Number(v.memberUserId), reason }
        : mode === 'EXTERNAL'
          ? { noPlatformUser: true, basisEn: String(v.basisEn).trim(), reason }
          : { reason });
      toast.success(mode === 'CLEAR'
        ? (isAr ? `تم إلغاء ربط ${linkTarget.memberName}` : `${linkTarget.memberName} unlinked`)
        : (isAr ? `تم تحديث مقعد ${linkTarget.memberName}` : `${linkTarget.memberName}'s seat updated`));
      setLinkTarget(null);
      setReloadKey(k => k + 1);
    } catch (e) {
      toast.danger(e instanceof Error ? e.message
        : (isAr ? 'تعذّر تحديث الربط' : 'Failed to update the link'));
    }
  }

  /** The linkage state of one active seat, from the report (which resolves the username). */
  function seatLink(termId: number) {
    return linkage?.seats.find(s => s.termId === termId);
  }

  // ---- Add a skill to the matrix -------------------------------------------
  const skillFields: FieldDef[] = [
    { key: 'memberTermId', kind: 'select-searchable', label: isAr ? 'العضو' : 'Member', required: true, span: 12,
      placeholder: isAr ? 'اختر عضواً…' : 'Select a member…',
      options: activeTerms.map(tm => ({ value: String(tm.id), label: tm.memberName, sub: tm.role })) },
    { key: 'skillDimension', kind: 'select', label: isAr ? 'بُعد المهارة' : 'Skill dimension', required: true, span: 6,
      options: SKILL_DIMENSIONS.map(d => ({ value: d.value, label: isAr ? d.ar : d.en })) },
    { key: 'skillLevel', kind: 'select', label: isAr ? 'المستوى' : 'Level', required: true, span: 6,
      options: SKILL_LEVELS.map(l => ({ value: l.value, label: isAr ? l.ar : l.en })) },
    { key: 'yearsExperience', kind: 'number', label: isAr ? 'سنوات الخبرة' : 'Years of experience', span: 6, min: 0, max: 70 },
    { key: 'certification', kind: 'text', label: isAr ? 'الشهادات' : 'Certifications', span: 6, maxLength: 255,
      placeholder: 'CPA, CFA, CISA, CISSP' },
    { key: 'lastReviewedAt', kind: 'date', label: isAr ? 'آخر مراجعة' : 'Last reviewed', span: 6 },
    { key: 'evidenceEn', kind: 'textarea', label: isAr ? 'مبرّر التأهيل (للجهة التنظيمية)' : 'Qualification evidence (for the regulator)', span: 12, rows: 2 },
  ];
  // Memoised so FormModal's reset effect can't wipe the form on a re-render.
  const skillInitial: Record<string, string> = useMemo(() => ({
    memberTermId: '', skillDimension: 'FINANCIAL_EXPERT', skillLevel: 'EXPERT',
    yearsExperience: '', certification: '', lastReviewedAt: '', evidenceEn: '',
  }), []);

  async function submitSkill(v: Record<string, string>) {
    const term = activeTerms.find(tm => tm.id === Number(v.memberTermId));
    const skillDimension = String(v.skillDimension || '').trim();
    const skillLevel = String(v.skillLevel || '').trim();
    if (!term)           { toast.danger(isAr ? 'يُرجى اختيار عضو' : 'Please select a member'); return; }
    if (!skillDimension) { toast.danger(isAr ? 'بُعد المهارة مطلوب' : 'Skill dimension is required'); return; }
    if (!skillLevel)     { toast.danger(isAr ? 'المستوى مطلوب' : 'Level is required'); return; }
    const yrsStr = String(v.yearsExperience ?? '').trim();
    const yrs = yrsStr === '' ? null : Number(yrsStr);
    try {
      await addSkill({
        committeeId: cid,
        memberTermId: term.id,
        memberName: term.memberName,
        skillDimension,
        skillLevel: skillLevel as 'BASIC' | 'INTERMEDIATE' | 'EXPERT',
        yearsExperience: yrs != null && Number.isFinite(yrs) && yrs >= 0 ? yrs : null,
        certification: String(v.certification || '').trim() || null,
        evidenceEn: String(v.evidenceEn || '').trim() || null,
        lastReviewedAt: String(v.lastReviewedAt || '').trim() || null,
      });
      toast.success(isAr ? `تمت إضافة مهارة لـ ${term.memberName}` : `Skill added for ${term.memberName}`);
      setOpenSkill(false);
      setReloadKey(k => k + 1);
    } catch { toast.danger(isAr ? 'تعذّر إضافة المهارة' : 'Failed to add the skill'); }
  }

  // ---- Renew a term (closes the current ACTIVE term as RENEWED, opens a new
  // ACTIVE term #n+1 with fresh dates under the renewing resolution) ----------
  const renewFields: FieldDef[] = [
    { key: 'newStart', kind: 'date', label: isAr ? 'بدء الفترة الجديدة' : 'New term starts', required: true, span: 6 },
    { key: 'newEnd', kind: 'date', label: isAr ? 'انتهاء الفترة الجديدة' : 'New term ends', required: true, span: 6 },
    { key: 'resolutionCode', kind: 'text', label: isAr ? 'قرار التجديد' : 'Renewing resolution', required: true, span: 12,
      maxLength: 64, placeholder: isAr ? 'مثال: RES-2029-COM-AUDIT-014' : 'e.g. RES-2029-COM-AUDIT-014' },
  ];
  // Memoised on the target so FormModal's reset effect reflects the new term
  // without churning. Defaults the new start to the day the current term ends.
  const renewInitial: Record<string, string> = useMemo(() => ({
    newStart: renewTarget?.termEndsAt ?? '', newEnd: '', resolutionCode: '',
  }), [renewTarget]);

  async function submitRenew(v: Record<string, string>) {
    if (!renewTarget) return;
    const newStart = String(v.newStart ?? '').trim();
    const newEnd = String(v.newEnd ?? '').trim();
    const resolutionCode = String(v.resolutionCode ?? '').trim();
    if (!newStart)        { toast.danger(isAr ? 'تاريخ بدء الفترة الجديدة مطلوب' : 'New term start is required'); return; }
    if (!newEnd)          { toast.danger(isAr ? 'تاريخ انتهاء الفترة الجديدة مطلوب' : 'New term end is required'); return; }
    if (newEnd < newStart){ toast.danger(isAr ? 'تاريخ الانتهاء يجب أن يكون بعد البدء' : 'End must be after start'); return; }
    if (!resolutionCode)  { toast.danger(isAr ? 'قرار التجديد مطلوب' : 'Renewing resolution is required'); return; }
    try {
      await renewMemberTerm(renewTarget.id, newStart, newEnd, resolutionCode);
      toast.success(isAr ? `تم تجديد فترة ${renewTarget.memberName}` : `${renewTarget.memberName}'s term renewed`);
      setRenewTarget(null);
      setReloadKey(k => k + 1);
    } catch { toast.danger(isAr ? 'تعذّر تجديد الفترة' : 'Failed to renew the term'); }
  }

  // ---- End a term (ACTIVE -> RESIGNED / REMOVED / EXPIRED) ------------------
  const endFields: FieldDef[] = [
    { key: 'renewalStatus', kind: 'select', label: isAr ? 'سبب الإنهاء' : 'End status', required: true, span: 12,
      options: [
        { value: 'RESIGNED', label: isAr ? 'استقالة' : 'Resigned' },
        { value: 'REMOVED',  label: isAr ? 'إقالة' : 'Removed' },
        { value: 'EXPIRED',  label: isAr ? 'انتهاء الفترة' : 'Term expired' },
      ] },
    { key: 'reason', kind: 'textarea', label: isAr ? 'البيان / السبب' : 'Reason / note', span: 12, rows: 3 },
  ];
  const endInitial: Record<string, string> = useMemo(() => ({
    renewalStatus: 'RESIGNED', reason: '',
  }), [endTarget]);

  async function submitEnd(v: Record<string, string>) {
    if (!endTarget) return;
    const renewalStatus = String(v.renewalStatus ?? '').trim();
    const reason = String(v.reason ?? '').trim();
    if (!renewalStatus) { toast.danger(isAr ? 'سبب الإنهاء مطلوب' : 'End status is required'); return; }
    try {
      await endMemberTerm(endTarget.id, renewalStatus, reason);
      toast.success(isAr ? `تم إنهاء فترة ${endTarget.memberName}` : `${endTarget.memberName}'s term ended`);
      setEndTarget(null);
      setReloadKey(k => k + 1);
    } catch { toast.danger(isAr ? 'تعذّر إنهاء الفترة' : 'Failed to end the term'); }
  }

  if (!Number.isFinite(cid)) {
    return (
      <Shell title={isAr ? 'تسجيل أعضاء اللجنة' : 'Committee member registry'}>
        <PageHeader
          eyebrow={isAr ? 'سجل الأعضاء' : 'Member registry'}
          title={isAr ? 'معرّف لجنة غير صالح' : 'Invalid committee ID'} />
        <EmptyState Icon={AlertCircle} title={isAr ? 'افتح من صفحة اللجان' : 'Open from the Committees page'} />
      </Shell>
    );
  }

  return (
    <Shell title={title} sub={committee?.code}>
      <div style={{ marginBottom: 8 }}>
        <Link to="/committees" style={{ display: 'inline-flex', alignItems: 'center', gap: 4, color: 'var(--ink-3)', textDecoration: 'none', fontSize: 12 }}>
          <ChevronLeft size={14} />
          {isAr ? 'العودة إلى اللجان' : 'Back to committees'}
        </Link>
      </div>
      <PageHeader
        eyebrow={isAr ? 'سجل الأعضاء' : 'Member registry'}
        title={title}
        sub={committee
          ? (isAr ? `سجل أعضاء — ${committee.code}` : `Member registry — ${committee.code}`)
          : (isAr ? 'يتم تحميل اللجنة…' : 'Loading committee…')} />

      <div className="kpi-grid">
        <Kpi Icon={Users}        label={isAr ? 'أعضاء نشطون' : 'Active members'}
             value={indep?.activeMembers ?? activeTerms.length} tone="primary"
             to="/committees" />
        <Kpi Icon={BadgeCheck}   label={isAr ? 'أعضاء مستقلون' : 'Independent members'}
             value={indep?.independentMembers ?? 0} tone="success"
             to="/committees" />
        <Kpi Icon={ShieldCheck}  label={isAr ? 'نسبة الاستقلالية' : 'Independence ratio'}
             value={`${indep?.independenceRatioPct ?? 0}%`}
             tone={(indep?.independenceRatioPct ?? 0) >= 50 ? 'success' : 'warning'}
             to="/committees" />
        <Kpi Icon={AlertCircle}  label={isAr ? 'انتهاء قريب (90 يوم)' : 'Expiring soon (90d)'}
             value={expiringSoon} tone={expiringSoon > 0 ? 'warning' : 'neutral'}
             to="/committees" />
        {/* F2-26 — voting coverage, not blended coverage. Non-voting invitees never cast a
            counted vote, so a single percentage can read healthy while every voting chair
            is unlinked and no member can vote as themselves. */}
        <Kpi Icon={Link2}
             label={isAr ? 'مقاعد مصوّتة مرتبطة' : 'Voting seats linked'}
             value={linkage ? `${linkage.votingLinked}/${linkage.votingSeats}` : '—'}
             tone={!linkage || linkage.votingSeats === 0 ? 'neutral'
                   : linkage.votingUnlinked === 0 ? 'success'
                   : linkage.votingLinked === 0 ? 'danger' : 'warning'}
             to="/committees" />
      </div>

      {/* F2-26 — the gap becomes a sentence on the page the secretariat already uses,
          instead of a discovery made in a meeting. */}
      {linkage && linkage.votingUnlinked > 0 && (
        <Card title={isAr ? 'ربط المقاعد بمستخدمي المنصة' : 'Seat-to-user linkage'} Icon={Link2}>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
            {isAr
              ? `${linkage.votingUnlinked} من ${linkage.votingSeats} مقعد مصوّت غير مرتبط بمستخدم في المنصة، لذلك لا يمكن لهؤلاء الأعضاء التصويت بأنفسهم — تُسجّل أصواتهم أمانة اللجنة نيابةً عنهم. اربط المقعد من عمود «مستخدم المنصة»، أو صرّح بأن العضو خارجي إن لم يكن لديه حساب.`
              : `${linkage.votingUnlinked} of ${linkage.votingSeats} voting seats are not linked to a platform user, so those members cannot cast their own vote — the secretariat has to record it on their behalf. Link a seat from the “Platform user” column, or declare the member external if they will never hold an account.`}
            {linkage.declaredExternal > 0 && (
              <> {isAr
                ? `(${linkage.declaredExternal} مقعد خارجي معلَن — لا يُحتسب كنقص.)`
                : `(${linkage.declaredExternal} declared external — not counted as a gap.)`}</>
            )}
          </div>
        </Card>
      )}

      {/* F2-178 — the divergence between "linked" and "confers access".
          `linked` counts by renewal_status alone, which is what F2-26's question needed. F2-177's
          seat guard also requires the term window to contain today, and NOTHING in this platform
          ever moves an ACTIVE term to EXPIRED — the sweep only sends reminders. So a roster can
          read fully linked while every one of its seats has lapsed and authorises nothing. This
          card fires only when the two numbers actually disagree, so it stays silent until it has
          something to say. */}
      {linkage && linkage.currentLinked < linkage.linked && (
        <Card title={isAr ? 'مقاعد مرتبطة منتهية المدة' : 'Linked seats that have lapsed'}
              Icon={AlertCircle}>
          <div style={{ fontSize: 12, color: 'var(--ink-2)', lineHeight: 1.7 }}>
            {isAr
              ? `${linkage.linked - linkage.currentLinked} من ${linkage.linked} مقعد مرتبط خارج مدّته اليوم، ومع ذلك ما زال سجلّه يقرأ «نشط» — لا شيء في المنصة ينقل الفترة إلى «منتهية» تلقائياً. هذه المقاعد لا تمنح صلاحية: حَرَس المقعد يرفض حاملها. جدّد الفترة أو أنهِها بشكل صريح.`
              : `${linkage.linked - linkage.currentLinked} of ${linkage.linked} linked seats are outside their term window today and still read ACTIVE — nothing moves a term to EXPIRED on its own. Those seats confer no access: the seat guard refuses whoever holds them. Renew the term, or end it explicitly.`}
          </div>
        </Card>
      )}

      {/* Active members panel */}
      <Card title={isAr ? 'الأعضاء النشطون' : 'Active members'} Icon={Users}
        action={
          <Button size="sm" variant="primary" Icon={UserPlus} onClick={() => setOpenAppoint(true)}>
            {isAr ? 'تعيين عضو' : 'Appoint member'}
          </Button>
        }>
        {loading
          ? <EmptyState Icon={Users} title={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
          : activeTerms.length === 0
            ? <EmptyState Icon={Users}
                title={isAr ? 'لا توجد فترات عضوية مسجّلة بعد' : 'No member terms recorded yet'}
                sub={isAr
                  ? 'أضف عضواً عبر POST /api/v1/committees/registry/terms مع اسم العضو والدور وتاريخ التعيين.'
                  : 'Appoint members via POST /api/v1/committees/registry/terms (name, role, appointed_at).'} />
            : (
              <table className="grc-table">
                <thead>
                  <tr>
                    <th>{isAr ? 'العضو' : 'Member'}</th>
                    <th>{isAr ? 'الدور' : 'Role'}</th>
                    <th>{isAr ? 'مستخدم المنصة' : 'Platform user'}</th>
                    <th>{isAr ? 'مستقل' : 'Independent'}</th>
                    <th>{isAr ? 'فترة' : 'Term'}</th>
                    <th>{isAr ? 'بدء الفترة' : 'Term starts'}</th>
                    <th>{isAr ? 'انتهاء الفترة' : 'Term ends'}</th>
                    <th>{isAr ? 'تعيّن بموجب' : 'Appointed by'}</th>
                    <th style={{ textAlign: isAr ? 'left' : 'right' }}>{isAr ? 'إجراءات' : 'Actions'}</th>
                  </tr>
                </thead>
                <tbody>
                  {activeTerms.map(t => (
                    <tr key={t.id}>
                      <td>{t.memberName}</td>
                      <td>{t.role}</td>
                      {/* F2-26. The resolved username is shown, not just "linked": a mis-link
                          — the CRO's seat pointing at the auditor's account — is only visible
                          if the report says whose account it is. */}
                      <td>
                        {(() => {
                          const s = seatLink(t.id);
                          const state = s?.state ?? (t.memberUserId ? 'LINKED'
                                       : t.noPlatformUser ? 'EXTERNAL' : 'UNLINKED');
                          const meta = LINK_STATE[state];
                          return (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
                              <Badge tone={meta.tone}>{isAr ? meta.ar : meta.en}</Badge>
                              {state === 'LINKED' && s?.username && (
                                <span className="mono" style={{ fontSize: 10, color: 'var(--ink-3)' }}>
                                  {s.username}
                                </span>
                              )}
                              {state === 'EXTERNAL' && s?.basisEn && (
                                <span style={{ fontSize: 10, color: 'var(--ink-3)' }} title={s.basisEn}>
                                  {s.basisEn.length > 32 ? `${s.basisEn.slice(0, 32)}…` : s.basisEn}
                                </span>
                              )}
                            </div>
                          );
                        })()}
                      </td>
                      <td>
                        {t.isIndependent === 1
                          ? <Badge tone="success">{isAr ? 'مستقل' : 'Independent'}</Badge>
                          : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                      </td>
                      <td className="mono">#{t.termNumber}</td>
                      <td className="mono">{t.termStartsAt}</td>
                      <td className="mono">{t.termEndsAt ?? (isAr ? 'غير محدد' : 'Open-ended')}</td>
                      <td className="mono">{t.appointedByReso ?? '—'}</td>
                      <td>
                        <div style={{ display: 'flex', gap: 6, justifyContent: isAr ? 'flex-start' : 'flex-end' }}>
                          <Button size="sm" variant="ghost" Icon={Link2} onClick={() => setLinkTarget(t)}>
                            {isAr ? 'الربط' : 'Link'}
                          </Button>
                          <Button size="sm" variant="secondary" Icon={RefreshCw} onClick={() => setRenewTarget(t)}>
                            {isAr ? 'تجديد' : 'Renew'}
                          </Button>
                          <Button size="sm" variant="ghost" Icon={UserMinus} onClick={() => setEndTarget(t)}>
                            {isAr ? 'إنهاء' : 'End'}
                          </Button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
      </Card>

      {/* Skills matrix */}
      <Card title={isAr ? 'مصفوفة المهارات' : 'Skills matrix'} Icon={Award}
        action={
          <Button size="sm" variant="primary" Icon={Plus} disabled={activeTerms.length === 0}
            onClick={() => setOpenSkill(true)}>
            {isAr ? 'إضافة مهارة' : 'Add skill'}
          </Button>
        }>
        {loading
          ? <EmptyState Icon={Award} title={isAr ? 'جارٍ التحميل…' : 'Loading…'} />
          : skills.length === 0
            ? <EmptyState Icon={Award}
                title={isAr ? 'لم تُسجَّل مهارات بعد' : 'No skills recorded yet'}
                sub={isAr
                  ? 'أضف مهارة عبر POST /api/v1/committees/registry/skills مع البُعد (FINANCIAL_EXPERT…) والمستوى.'
                  : 'Add via POST /api/v1/committees/registry/skills with dimension (FINANCIAL_EXPERT…) and level.'} />
            : (
              <>
                {/* Coverage bar */}
                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 6, marginBottom: 12 }}>
                  {Object.entries(coverage).map(([dim, count]) => (
                    <Badge key={dim} tone={count > 0 ? 'info' : 'neutral'}>
                      {dim} — {count}
                    </Badge>
                  ))}
                </div>
                <table className="grc-table">
                  <thead>
                    <tr>
                      <th>{isAr ? 'العضو' : 'Member'}</th>
                      {dimensions.map(d => <th key={d}>{d.replace(/_/g, ' ')}</th>)}
                    </tr>
                  </thead>
                  <tbody>
                    {Object.entries(matrix).map(([member, dimMap]) => (
                      <tr key={member}>
                        <td>{member}</td>
                        {dimensions.map(d => {
                          const lvl = dimMap[d];
                          return (
                            <td key={d}>
                              {lvl
                                ? <Badge tone={lvl === 'EXPERT' ? 'success' : lvl === 'INTERMEDIATE' ? 'info' : 'neutral'}>{lvl}</Badge>
                                : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                            </td>
                          );
                        })}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </>
            )}
      </Card>

      {/* Historical terms */}
      {terms.length > activeTerms.length && (
        <Card title={isAr ? 'فترات تاريخية' : 'Historical terms'} Icon={Users}>
          <table className="grc-table">
            <thead>
              <tr>
                <th>{isAr ? 'العضو' : 'Member'}</th>
                <th>{isAr ? 'الدور' : 'Role'}</th>
                <th>{isAr ? 'فترة' : 'Term'}</th>
                <th>{isAr ? 'الحالة' : 'Status'}</th>
                <th>{isAr ? 'بدء' : 'Start'}</th>
                <th>{isAr ? 'انتهاء' : 'End'}</th>
                <th>{isAr ? 'سبب الانتهاء' : 'End reason'}</th>
              </tr>
            </thead>
            <tbody>
              {terms.filter(t => t.renewalStatus !== 'ACTIVE').map(t => (
                <tr key={t.id}>
                  <td>{t.memberName}</td>
                  <td>{t.role}</td>
                  <td className="mono">#{t.termNumber}</td>
                  <td>
                    <Badge tone={t.renewalStatus === 'RENEWED' ? 'info'
                              : t.renewalStatus === 'EXPIRED' ? 'neutral'
                              : t.renewalStatus === 'RESIGNED' ? 'warning' : 'danger'}>
                      {t.renewalStatus}
                    </Badge>
                  </td>
                  <td className="mono">{t.termStartsAt}</td>
                  <td className="mono">{t.termEndsAt ?? '—'}</td>
                  <td>{t.endReason ?? '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}

      <FormModal
        open={openAppoint}
        title={isAr ? `تعيين عضو${committee ? ` — ${title}` : ''}` : `Appoint member${committee ? ` — ${title}` : ''}`}
        fields={appointFields}
        initial={appointInitial}
        submitLabel={isAr ? 'تعيين' : 'Appoint'}
        onClose={() => setOpenAppoint(false)}
        onSubmit={submitAppoint}
      />

      <FormModal
        open={openSkill}
        title={isAr ? `إضافة مهارة${committee ? ` — ${title}` : ''}` : `Add skill${committee ? ` — ${title}` : ''}`}
        fields={skillFields}
        initial={skillInitial}
        submitLabel={isAr ? 'إضافة' : 'Add skill'}
        onClose={() => setOpenSkill(false)}
        onSubmit={submitSkill}
      />

      <FormModal
        open={linkTarget != null}
        title={isAr
          ? `ربط المقعد بمستخدم${linkTarget ? ` — ${linkTarget.memberName}` : ''}`
          : `Seat platform user${linkTarget ? ` — ${linkTarget.memberName}` : ''}`}
        fields={linkFields}
        initial={linkInitial}
        submitLabel={isAr ? 'حفظ' : 'Save'}
        onClose={() => setLinkTarget(null)}
        onSubmit={submitLink}
      />

      <FormModal
        open={renewTarget != null}
        title={isAr
          ? `تجديد فترة${renewTarget ? ` — ${renewTarget.memberName}` : ''}`
          : `Renew term${renewTarget ? ` — ${renewTarget.memberName}` : ''}`}
        fields={renewFields}
        initial={renewInitial}
        submitLabel={isAr ? 'تجديد الفترة' : 'Renew term'}
        onClose={() => setRenewTarget(null)}
        onSubmit={submitRenew}
      />

      <FormModal
        open={endTarget != null}
        title={isAr
          ? `إنهاء فترة${endTarget ? ` — ${endTarget.memberName}` : ''}`
          : `End term${endTarget ? ` — ${endTarget.memberName}` : ''}`}
        fields={endFields}
        initial={endInitial}
        submitLabel={isAr ? 'إنهاء الفترة' : 'End term'}
        onClose={() => setEndTarget(null)}
        onSubmit={submitEnd}
      />
    </Shell>
  );
}
