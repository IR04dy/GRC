import { useEffect, useState, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  Users, Award, ShieldCheck, ChevronLeft, AlertCircle, BadgeCheck, UserPlus, Plus,
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
  addSkill,
  type Committee, type CommitteeMemberTerm, type CommitteeMemberSkill,
  type IndependenceStats,
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
    ]).then(([c, t, s, cov, ind]) => {
      if (cancelled) return;
      setCommittee(c.content.find(x => x.id === cid) ?? null);
      setTerms(t);
      setSkills(s);
      setCoverage(cov);
      setIndep(ind);
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
  const appointFields: FieldDef[] = [
    { key: 'memberName', kind: 'text', label: isAr ? 'اسم العضو' : 'Member name', required: true, span: 6, maxLength: 255 },
    { key: 'role', kind: 'select', label: isAr ? 'الدور' : 'Role', required: true, span: 6,
      options: MEMBER_ROLES.map(r => ({ value: r, label: isAr ? (ROLE_LABEL_AR[r] ?? r) : r })) },
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
    memberName: '', role: 'Member', isIndependent: '1', isVotingMember: '1',
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
    } catch { toast.danger(isAr ? 'تعذّر تعيين العضو' : 'Failed to appoint member'); }
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
             value={indep?.activeMembers ?? activeTerms.length} tone="primary" />
        <Kpi Icon={BadgeCheck}   label={isAr ? 'أعضاء مستقلون' : 'Independent members'}
             value={indep?.independentMembers ?? 0} tone="success" />
        <Kpi Icon={ShieldCheck}  label={isAr ? 'نسبة الاستقلالية' : 'Independence ratio'}
             value={`${indep?.independenceRatioPct ?? 0}%`}
             tone={(indep?.independenceRatioPct ?? 0) >= 50 ? 'success' : 'warning'} />
        <Kpi Icon={AlertCircle}  label={isAr ? 'انتهاء قريب (90 يوم)' : 'Expiring soon (90d)'}
             value={expiringSoon} tone={expiringSoon > 0 ? 'warning' : 'neutral'} />
      </div>

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
                    <th>{isAr ? 'مستقل' : 'Independent'}</th>
                    <th>{isAr ? 'فترة' : 'Term'}</th>
                    <th>{isAr ? 'بدء الفترة' : 'Term starts'}</th>
                    <th>{isAr ? 'انتهاء الفترة' : 'Term ends'}</th>
                    <th>{isAr ? 'تعيّن بموجب' : 'Appointed by'}</th>
                  </tr>
                </thead>
                <tbody>
                  {activeTerms.map(t => (
                    <tr key={t.id}>
                      <td>{t.memberName}</td>
                      <td>{t.role}</td>
                      <td>
                        {t.isIndependent === 1
                          ? <Badge tone="success">{isAr ? 'مستقل' : 'Independent'}</Badge>
                          : <span style={{ color: 'var(--ink-3)' }}>—</span>}
                      </td>
                      <td className="mono">#{t.termNumber}</td>
                      <td className="mono">{t.termStartsAt}</td>
                      <td className="mono">{t.termEndsAt ?? (isAr ? 'غير محدد' : 'Open-ended')}</td>
                      <td className="mono">{t.appointedByReso ?? '—'}</td>
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
    </Shell>
  );
}
