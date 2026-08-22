import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, KeyRound, Grid3X3, UserSearch, Plus, Save, Trash2, RefreshCw,
  Inbox, ClipboardCheck, Scale, Siren, BarChart3, Copy, Lock,
} from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Card } from '../../components/ui/Card';
import { Badge } from '../../components/ui/Badge';
import { Button } from '../../components/ui/Button';
import { Tabs } from '../../components/ui/Tabs';
import { useI18n } from '../../lib/i18n';
import { useToast } from '../../lib/toast';
import {
  governanceApi, adminApi, ApiError,
  type GovRoleDto, type GovMatrixDto, type GovEffectiveAccessDto, type AdminUserDto,
} from '../../lib/api';
import { hasAnyRole } from '../../lib/auth';
import { RequestsTab, CampaignsTab, SodTab, BreakGlassTab, AnalyticsTab } from './AccessAssuranceTabs';

type TabId = 'roles' | 'matrix' | 'access' | 'requests' | 'campaigns' | 'sod' | 'breakglass' | 'analytics';

/**
 * Access-Governance console (RBAC plan §6.1/§6.8 — P0): the runtime management
 * surface over the platform's RBAC — role catalogue (create/retire; system roles
 * are protected templates), the role × module × action permission-matrix editor
 * (structural rules enforced server-side: APPROVE⇒READ, DELETE⇒WRITE), and the
 * per-user effective-access view (which role delivers which grant).
 *
 * <p>F2-63 — the matrix renders a column for <em>every</em> action the catalogue returns,
 * derived from the response rather than declared, because a screen that claims to answer
 * "who can do what" must not silently omit an action class. Two of the seven are disclosed
 * read-only rather than grantable here; see {@link ACTION_POLICY} for which and why.
 */
export function AccessGovernancePage() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();

  const [tab, setTab] = useState<TabId>('roles');
  const [roles, setRoles] = useState<GovRoleDto[]>([]);
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [loading, setLoading] = useState(true);

  const loadRoles = useCallback(async () => {
    setLoading(true);
    try { setRoles(await governanceApi.roles()); }
    catch (e) { toast.danger(e instanceof ApiError ? e.message : (isAr ? 'تعذّر تحميل الأدوار' : 'Failed to load roles')); }
    finally { setLoading(false); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { void loadRoles(); }, [loadRoles]);
  useEffect(() => {
    adminApi.users(0, 100).then(p => setUsers(p.content ?? [])).catch(() => setUsers([]));
  }, []);

  return (
    <Shell title={isAr ? 'حوكمة الصلاحيات' : 'Access Governance'}
      sub={isAr ? 'إدارة الأدوار ومصفوفة الصلاحيات والوصول الفعلي.' : 'Manage roles, the permission matrix, and effective access.'}>
      <PageHeader
        eyebrow={isAr ? 'المنصّة · IAM' : 'Platform · IAM'}
        title={isAr ? 'حوكمة الصلاحيات' : 'Access Governance'}
        sub={isAr
          ? 'من يستطيع فعل ماذا — أدوار قابلة للإدارة، مصفوفة وحدة×إجراء، ورؤية الوصول الفعلي لكل مستخدم.'
          : 'Who can do what — managed roles, a module × action matrix, and per-user effective access.'}
      />
      <Tabs<TabId>
        items={[
          { id: 'roles',      label: isAr ? 'الأدوار' : 'Roles',            Icon: KeyRound },
          { id: 'matrix',     label: isAr ? 'مصفوفة الصلاحيات' : 'Permission Matrix', Icon: Grid3X3 },
          { id: 'access',     label: isAr ? 'الوصول الفعلي' : 'Effective Access',     Icon: UserSearch },
          { id: 'requests',   label: isAr ? 'طلبات الوصول' : 'Access Requests',       Icon: Inbox },
          { id: 'campaigns',  label: isAr ? 'حملات المراجعة' : 'Review Campaigns',    Icon: ClipboardCheck },
          { id: 'sod',        label: isAr ? 'فصل المهام' : 'SoD',                     Icon: Scale },
          { id: 'breakglass', label: isAr ? 'وصول الطوارئ' : 'Break-glass',           Icon: Siren },
          { id: 'analytics',  label: isAr ? 'التحليلات' : 'Analytics',                Icon: BarChart3 },
        ]}
        active={tab}
        onChange={setTab}
      />
      <div style={{ marginTop: 16 }}>
        {tab === 'roles'      && <RolesTab roles={roles} loading={loading} reload={loadRoles} />}
        {tab === 'matrix'     && <MatrixTab roles={roles} goTo={setTab} />}
        {tab === 'access'     && <AccessTab />}
        {tab === 'requests'   && <RequestsTab roles={roles} users={users} />}
        {tab === 'campaigns'  && <CampaignsTab users={users} />}
        {tab === 'sod'        && <SodTab />}
        {tab === 'breakglass' && <BreakGlassTab />}
        {tab === 'analytics'  && <AnalyticsTab />}
      </div>
    </Shell>
  );
}

// ─── Roles tab ───────────────────────────────────────────────────────────────

function RolesTab({ roles, loading, reload }: { roles: GovRoleDto[]; loading: boolean; reload: () => Promise<void> }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [form, setForm] = useState({ roleName: '', displayNameEn: '', displayNameAr: '' });
  const [busy, setBusy] = useState(false);

  async function create() {
    // displayNameAr is backend-mandatory (bilingual-data rule) — gate it here
    // so the button gives a precise reason instead of a validation 400.
    if (!form.roleName || !form.displayNameEn || !form.displayNameAr) {
      toast.danger(isAr ? 'اسم الدور والاسمان المعروضان (EN وAR) مطلوبة' : 'Role name and both display names (EN and AR) are required'); return;
    }
    setBusy(true);
    try {
      await governanceApi.createRole({ roleName: form.roleName.toUpperCase(), displayNameEn: form.displayNameEn, displayNameAr: form.displayNameAr });
      toast.success(isAr ? 'أُنشئ الدور' : 'Role created');
      setForm({ roleName: '', displayNameEn: '', displayNameAr: '' });
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإنشاء' : 'Create failed')); }
    finally { setBusy(false); }
  }

  async function retire(r: GovRoleDto) {
    if (!confirm(isAr ? `إيقاف الدور ${r.roleName}؟` : `Retire role ${r.roleName}?`)) return;
    try {
      await governanceApi.deleteRole(r.id);
      toast.success(isAr ? 'أُوقف الدور' : 'Role retired');
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الإيقاف' : 'Retire failed')); }
  }

  /**
   * F2-56 — clone a shared role into this tenant. The new name must be supplied because the
   * source's name is taken (by the source), and both display names are backend-mandatory,
   * so they are gated here rather than surfaced as a validation 400.
   */
  async function clone(r: GovRoleDto) {
    if (!form.roleName || !form.displayNameEn || !form.displayNameAr) {
      toast.danger(isAr
        ? 'املأ اسم الدور والاسمين المعروضين (EN وAR) أعلاه، ثم انسخ'
        : 'Fill the role name and both display names (EN and AR) above, then clone');
      return;
    }
    setBusy(true);
    try {
      const made = await governanceApi.cloneRole(r.id, {
        roleName: form.roleName.toUpperCase(),
        displayNameEn: form.displayNameEn,
        displayNameAr: form.displayNameAr,
      });
      toast.success(isAr
        ? `نُسخ ${r.roleName} إلى ${made.roleName}`
        : `Cloned ${r.roleName} to ${made.roleName}`);
      setForm({ roleName: '', displayNameEn: '', displayNameAr: '' });
      await reload();
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل النسخ' : 'Clone failed')); }
    finally { setBusy(false); }
  }

  return (
    <Card title={isAr ? 'كتالوج الأدوار' : 'Role catalogue'} Icon={KeyRound}
      action={
        <div style={{ display: 'inline-flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
          <input value={form.roleName} onChange={e => setForm(f => ({ ...f, roleName: e.target.value }))}
            placeholder="ROLE_NAME" style={INPUT} />
          <input value={form.displayNameEn} onChange={e => setForm(f => ({ ...f, displayNameEn: e.target.value }))}
            placeholder={isAr ? 'الاسم (EN)' : 'Display (EN)'} style={INPUT} />
          <input value={form.displayNameAr} onChange={e => setForm(f => ({ ...f, displayNameAr: e.target.value }))}
            placeholder={isAr ? 'الاسم (AR)' : 'Display (AR)'} style={INPUT} dir="rtl" />
          <Button size="sm" variant="primary" Icon={Plus} onClick={() => void create()} disabled={busy}>
            {isAr ? 'إنشاء' : 'Create'}
          </Button>
          <Button size="sm" variant="ghost" Icon={RefreshCw} onClick={() => void reload()} disabled={loading} />
        </div>
      }>
      {loading
        ? <div style={EMPTY}>{isAr ? 'جارٍ التحميل…' : 'Loading…'}</div>
        : <table className="grc-table">
            <thead><tr>
              <th>{isAr ? 'الدور' : 'Role'}</th>
              <th>{isAr ? 'الاسم (EN)' : 'Display (EN)'}</th>
              <th>{isAr ? 'الاسم (AR)' : 'Display (AR)'}</th>
              <th>{isAr ? 'النوع' : 'Type'}</th>
              <th>{isAr ? 'النطاق' : 'Scope'}</th>
              {/* F2-280 — the privileged-access classification the server now reports. It is the
                  EFFECTIVE answer (the stored is_privileged flag OR an APPROVE/DELETE grant),
                  i.e. exactly the predicate the quarterly NCA ECC 2-2 / SAMA CSF 3.3.5 review and
                  the break-glass "Privileged holders" KPI share. A badge derived some other way
                  would disagree with the review it summarises, which is worse than none. */}
              <th>{isAr ? 'مميّز' : 'Privileged'}</th>
              <th>{isAr ? 'الحالة' : 'Status'}</th>
              <th></th>
            </tr></thead>
            <tbody>
              {roles.map(r => (
                <tr key={r.id}>
                  <td className="mono text-2xs">{r.roleName}</td>
                  <td className="text-2xs">{r.displayNameEn}</td>
                  <td className="text-2xs" dir="rtl">{r.displayNameAr ?? '—'}</td>
                  <td><Badge tone={r.systemRole ? 'info' : 'neutral'} dot>{r.systemRole ? (isAr ? 'نظامي' : 'System') : (isAr ? 'مخصص' : 'Custom')}</Badge></td>
                  <td>
                    <Badge tone={r.organisationId == null ? 'info' : 'neutral'} dot>
                      {r.organisationId == null ? (isAr ? 'مشترك' : 'Shared') : (isAr ? 'خاص بالمستأجر' : 'Tenant')}
                    </Badge>
                  </td>
                  <td>
                    {r.privileged
                      ? <Badge tone="danger" dot>{isAr ? 'مميّز' : 'Privileged'}</Badge>
                      : <Badge tone="neutral" dot>{isAr ? 'عادي' : 'Standard'}</Badge>}
                  </td>
                  <td><Badge tone={r.active ? 'success' : 'warning'} dot>{r.active ? (isAr ? 'نشط' : 'Active') : (isAr ? 'موقوف' : 'Inactive')}</Badge></td>
                  <td>
                    <Button size="sm" variant="ghost" Icon={Copy} onClick={() => void clone(r)} disabled={busy}
                      title={isAr
                        ? 'انسخ إلى هذا المستأجر (املأ الأسماء أعلاه أولاً)'
                        : 'Clone into this tenant (fill the names above first)'} />
                    {!r.systemRole && (
                      <Button size="sm" variant="ghost" Icon={Trash2} onClick={() => void retire(r)}
                        title={isAr ? 'إيقاف' : 'Retire'} />
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>}
    </Card>
  );
}

// ─── Matrix tab ──────────────────────────────────────────────────────────────

/**
 * F2-63 — per-action policy for this screen: the bilingual label, and whether the
 * matrix may *grant* the action as opposed to merely *show* it.
 *
 * <p>The two are different questions and were previously conflated into one
 * hardcoded five-element list, which answered "show" with "no" for the two actions
 * it omitted. For role `CISO` that hid **14 of 40 grants (35%)** — 13 `*:EXPORT`
 * and 1 `IAM:BREAK_GLASS` — on the screen whose stated job is "who can do what".
 * Save always round-tripped them, so nothing was lost; it was simply not disclosed.
 */
type ActionPolicy = {
  /** May this screen toggle the grant? `false` ⇒ shown, disclosed, and not editable here. */
  editable: boolean;
  labelEn: string;
  labelAr: string;
  /** Why it is not editable here. Required whenever `editable` is false. */
  whyEn?: string;
  whyAr?: string;
};

const ACTION_POLICY: Record<string, ActionPolicy> = {
  READ:    { editable: true, labelEn: 'Read',    labelAr: 'قراءة' },

  // AML's need-to-know read gradient (F2-145(e) slice 5, V370). Three tiers rather than one because
  // the hit queue names customers who matched a sanctions list, and disclosing that to front-line
  // onboarding staff is the FATF R.21 / SAMA tipping-off offence — so WHICH roles hold which tier is
  // exactly the decision this screen exists to make, and each needs its own column to make it.
  // Placed immediately after READ so the columns read as ascending sensitivity.
  // (Single-module actions already have precedent here: TRIAGE is INTAKE's alone, BREAK_GLASS is IAM's.)
  READ_ENTRIES: { editable: true, labelEn: 'Read entries', labelAr: 'قراءة مُدخلات القائمة' },
  READ_HITS:    { editable: true, labelEn: 'Read hits',    labelAr: 'قراءة التطابقات' },

  // Ethics separates the conflict-of-interest register (READ) from the whistleblower hotline
  // (F2-145(e) slice 6, V371). A board secretary curates the register and is deliberately outside case
  // files that may name or concern the board, so the two reads cannot share a column: putting them in
  // one is exactly the widening this action exists to prevent.
  READ_CASES: { editable: true, labelEn: 'Read cases', labelAr: 'قراءة الحالات' },

  WRITE:   { editable: true, labelEn: 'Write',   labelAr: 'كتابة' },

  // AML list curation, split from WRITE (which is "run a screening") and from APPROVE (which is
  // "dispose of a hit") so a tenant can grant curating the list without also granting disposal of the
  // hits that curation produces — the two halves of a maker/checker pair.
  MANAGE_LISTS:  { editable: true, labelEn: 'Manage lists',  labelAr: 'إدارة القوائم' },
  REFRESH_LISTS: { editable: true, labelEn: 'Refresh lists', labelAr: 'تحديث القوائم' },

  APPROVE: { editable: true, labelEn: 'Approve', labelAr: 'اعتماد' },
  DELETE:  { editable: true, labelEn: 'Delete',  labelAr: 'حذف' },
  TRIAGE:  { editable: true, labelEn: 'Triage',  labelAr: 'فرز' },

  // Disposing of a retaliation complaint, held apart from ETHICS:TRIAGE because a retaliation claim is
  // usually about the handling of the case underneath it — so whoever investigated that case must not
  // rule on the claim. Granting this to a role that also holds TRIAGE re-creates the self-review defect
  // V367 removed from sharia, and V371's self-check refuses it.
  PROTECT: { editable: true, labelEn: 'Protect', labelAr: 'حماية من الانتقام' },

  // Disclosed but not grantable here — each for a reason recorded in the programme,
  // not for tidiness. Both are still saved back exactly as loaded.
  EXPORT: {
    editable: false, labelEn: 'Export', labelAr: 'تصدير',
    whyEn: 'Shown, not editable. Which personas may extract a whole register as a file is an '
      + 'open policy decision (F2-61) — F2-27 made these 26 permissions enforce, and the grants '
      + 'behind them have not been re-reviewed since. Existing grants are preserved on save.',
    whyAr: 'معروضة وغير قابلة للتعديل. تحديد الجهات التي يحق لها تصدير سجل كامل كملف قرارٌ '
      + 'سياسي لم يُحسم بعد (F2-61) — إذ فعّل F2-27 إنفاذ هذه الصلاحيات الست والعشرين دون '
      + 'إعادة مراجعة منحها. وتُحفظ الصلاحيات القائمة كما هي.',
  },
  BREAK_GLASS: {
    editable: false, labelEn: 'Break-glass', labelAr: 'وصول الطوارئ',
    whyEn: 'Shown, not editable. Emergency access is not an ordinary grant: it is activated per '
      + 'incident from the Break-glass tab, time-boxed to 24 h, and gated by four-eyes review. '
      + 'Held today by 3 roles. Existing grants are preserved on save.',
    whyAr: 'معروضة وغير قابلة للتعديل. وصول الطوارئ ليس منحاً اعتيادياً: يُنشَّط لكل حادثة من '
      + 'تبويب «وصول الطوارئ»، ومحدَّد بمدة أقصاها ٢٤ ساعة، وخاضع لمراجعة مبدأ الأربع أعين. '
      + 'تحمله اليوم ٣ أدوار. وتُحفظ الصلاحيات القائمة كما هي.',
  },
};

/** Canonical column order. An action absent from this map is appended, never dropped. */
export const ACTION_ORDER = Object.keys(ACTION_POLICY);

/**
 * The rendered column set, **derived from the response** rather than declared.
 *
 * <p>This is the structural half of F2-63: the previous code declared its columns, so a
 * catalogued action the list did not mention could not be displayed and nothing failed.
 * Deriving them means the screen shows every action the catalogue returns — known ones in
 * a legible order, then anything unrecognised, appended so it is visible on the day it is
 * catalogued rather than on the day someone notices.
 *
 * <p>An unrecognised action is shown **read-only** (see {@link isActionEditable}): being
 * displayed is the floor, being grantable is a decision, and fail-closed is the right
 * default for an action class nobody has classified yet.
 */
export function matrixColumns(cells: { action: string }[]): string[] {
  const present = new Set(cells.map(c => c.action));
  return [
    ...ACTION_ORDER.filter(a => present.has(a)),
    ...[...present].filter(a => !(a in ACTION_POLICY)).sort(),
  ];
}

/** Fail closed: an action with no policy entry is disclosed but not grantable. */
export function isActionEditable(action: string): boolean {
  return ACTION_POLICY[action]?.editable ?? false;
}

export function MatrixTab({ roles, goTo }: { roles: GovRoleDto[]; goTo?: (t: TabId) => void }) {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [roleId, setRoleId] = useState<number | ''>('');
  const [matrix, setMatrix] = useState<GovMatrixDto | null>(null);
  const [granted, setGranted] = useState<Set<string>>(new Set());
  const [busy, setBusy] = useState(false);
  // V294/§6.1 — side-by-side compare: diff this role against another one.
  const [compareId, setCompareId] = useState<number | ''>('');
  const [compareGrants, setCompareGrants] = useState<Set<string> | null>(null);

  useEffect(() => {
    if (compareId === '') { setCompareGrants(null); return; }
    governanceApi.matrix(compareId)
      .then(m => setCompareGrants(new Set(m.cells.filter(c => c.granted).map(c => `${c.module}/${c.action}`))))
      .catch(() => setCompareGrants(null));
  }, [compareId]);

  const load = useCallback(async (id: number) => {
    try {
      const m = await governanceApi.matrix(id);
      setMatrix(m);
      setGranted(new Set(m.cells.filter(c => c.granted).map(c => `${c.module}/${c.action}`)));
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : 'Failed to load matrix'); }
  }, []);  // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => { if (roleId !== '') void load(roleId); }, [roleId, load]);

  const modules = useMemo(() => {
    if (!matrix) return [] as { module: string; actions: Set<string> }[];
    const byModule = new Map<string, Set<string>>();
    matrix.cells.forEach(c => {
      if (!byModule.has(c.module)) byModule.set(c.module, new Set());
      byModule.get(c.module)!.add(c.action);
    });
    return [...byModule.entries()].map(([module, actions]) => ({ module, actions }));
  }, [matrix]);

  function toggle(module: string, action: string) {
    const key = `${module}/${action}`;
    setGranted(prev => {
      const next = new Set(prev);
      if (next.has(key)) next.delete(key); else next.add(key);
      return next;
    });
  }

  async function save() {
    if (roleId === '') return;
    setBusy(true);
    try {
      const grants = [...granted].map(k => { const [module, action] = k.split('/'); return { module, action }; });
      const m = await governanceApi.saveMatrix(roleId, grants);
      setMatrix(m);
      setGranted(new Set(m.cells.filter(c => c.granted).map(c => `${c.module}/${c.action}`)));
      toast.success(isAr ? 'حُفظت المصفوفة' : 'Matrix saved');
    } catch (e) { toast.danger(e instanceof ApiError ? e.message : (isAr ? 'فشل الحفظ' : 'Save failed')); }
    finally { setBusy(false); }
  }

  // F2-56 — a shared (global) role is one row for every tenant, so editing it from inside a
  // tenant would rewrite authorisation for the others. Platform scope may; a tenant may not,
  // and clones instead. Before V334 only 12 roles were global and this read `organisationId
  // != null` alone, which meant the editor was dead for all 12 even for PLATFORM_ADMIN.
  const selected = roles.find(r => r.id === roleId);
  const shared = selected != null && selected.organisationId == null;
  const platformScope = hasAnyRole('PLATFORM_ADMIN');
  const editable = matrix != null && (!shared || platformScope);

  /** F2-63 — columns come from the response, so a newly catalogued action cannot go missing. */
  const actions = useMemo(() => matrixColumns(matrix?.cells ?? []), [matrix]);

  /**
   * F2-63 — two independent gates, and they answer different questions. `editable` is about the
   * *role* (F2-56: may this scope rewrite this row at all); {@link isActionEditable} is about the
   * *action* (may this screen grant this class of authority). A cell needs both.
   */
  const canToggle = (action: string) => editable && isActionEditable(action);

  /** Read-only action classes actually present, with how many grants each one holds. */
  const disclosedOnly = useMemo(
    () => actions.filter(a => !isActionEditable(a)).map(a => ({
      action: a,
      held: [...granted].filter(k => k.split('/')[1] === a).length,
    })),
    [actions, granted],
  );

  return (
    <Card title={isAr ? 'مصفوفة وحدة × إجراء' : 'Module × action matrix'} Icon={Grid3X3}
      action={
        <div style={{ display: 'inline-flex', gap: 8, alignItems: 'center' }}>
          <select value={roleId} onChange={e => setRoleId(e.target.value === '' ? '' : Number(e.target.value))} style={INPUT}>
            <option value="">{isAr ? 'اختر دوراً…' : 'Select a role…'}</option>
            {roles.map(r => <option key={r.id} value={r.id}>{r.roleName}</option>)}
          </select>
          <select value={compareId} onChange={e => setCompareId(e.target.value === '' ? '' : Number(e.target.value))} style={INPUT}>
            <option value="">{isAr ? 'قارن مع…' : 'Compare with…'}</option>
            {roles.filter(r => r.id !== roleId).map(r => <option key={r.id} value={r.id}>{r.roleName}</option>)}
          </select>
          <Button size="sm" variant="primary" Icon={Save} onClick={() => void save()}
            disabled={busy || !editable}>{isAr ? 'حفظ' : 'Save'}</Button>
        </div>
      }>
      {!matrix
        ? <div style={EMPTY}>{isAr ? 'اختر دوراً لعرض مصفوفته.' : 'Select a role to view its matrix.'}</div>
        : <>
            {!editable && (
              <div style={{ ...EMPTY, paddingTop: 0 }}>
                {isAr
                  ? 'هذا دور مشترك بين جميع المستأجرين، ولا يمكن تعديله من داخل مستأجر واحد. انسخه من كتالوج الأدوار للحصول على نسخة خاصة بك يمكنك تعديلها.'
                  : 'This role is shared by every tenant, so it cannot be edited from inside one. Clone it from the Role catalogue to get a copy of your own that you can tune.'}
              </div>
            )}
            <table className="grc-table" data-testid="matrix-table">
              <thead><tr>
                <th>{isAr ? 'الوحدة' : 'Module'}</th>
                {actions.map(a => {
                  const p = ACTION_POLICY[a];
                  const label = (isAr ? p?.labelAr : p?.labelEn) ?? a;
                  const why = isAr ? p?.whyAr : p?.whyEn;
                  return (
                    <th key={a} data-action={a} style={{ textAlign: 'center', whiteSpace: 'nowrap' }}
                      title={why ? `${label} — ${why}` : label}>
                      {a}
                      {!isActionEditable(a) && (
                        <Lock size={11} aria-hidden style={{ verticalAlign: -1, marginInlineStart: 4, color: 'var(--ink-3)' }} />
                      )}
                    </th>
                  );
                })}
              </tr></thead>
              <tbody>
                {modules.map(({ module, actions: present }) => (
                  <tr key={module}>
                    <td className="mono text-2xs">{module}</td>
                    {actions.map(a => {
                      const key = `${module}/${a}`;
                      return (
                        <td key={a} style={{ textAlign: 'center' }}>
                          {present.has(a)
                            ? <input type="checkbox" checked={granted.has(key)}
                                aria-label={key}
                                disabled={!canToggle(a)}
                                onChange={() => toggle(module, a)} />
                            : <span style={{ color: 'var(--ink-3)' }}>·</span>}
                        </td>
                      );
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
            {disclosedOnly.length > 0 && (
              <div style={{ marginTop: 12, display: 'grid', gap: 8 }}>
                {disclosedOnly.map(({ action, held }) => {
                  const p = ACTION_POLICY[action];
                  const label = (isAr ? p?.labelAr : p?.labelEn) ?? action;
                  const why = (isAr ? p?.whyAr : p?.whyEn)
                    ?? (isAr
                      ? 'إجراء غير مصنَّف في هذه الشاشة — يُعرض ولا يُمنح من هنا حتى تُقرَّر سياسته.'
                      : 'Unclassified action — disclosed here, and not grantable until its policy is decided.');
                  return (
                    <div key={action} data-testid={`readonly-note-${action}`}
                      style={{ display: 'flex', gap: 8, alignItems: 'flex-start', fontSize: 12, color: 'var(--ink-2)' }}>
                      <Lock size={13} aria-hidden style={{ flexShrink: 0, marginTop: 2, color: 'var(--ink-3)' }} />
                      <div>
                        <Badge tone="neutral">{action}</Badge>
                        <span style={{ marginInlineStart: 6 }}>
                          {label} · {isAr ? `${held} صلاحية محفوظة` : `${held} grant${held === 1 ? '' : 's'} held`}
                        </span>
                        <div style={{ marginTop: 2 }}>{why}</div>
                        {action === 'BREAK_GLASS' && goTo && (
                          <Button size="sm" variant="ghost" Icon={Siren} onClick={() => goTo('breakglass')}>
                            {isAr ? 'إلى تبويب وصول الطوارئ' : 'Go to the Break-glass tab'}
                          </Button>
                        )}
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
            {compareGrants != null && compareId !== '' && (() => {
              const compareName = roles.find(r => r.id === compareId)?.roleName ?? '';
              const onlyA = [...granted].filter(k => !compareGrants.has(k)).sort();
              const onlyB = [...compareGrants].filter(k => !granted.has(k)).sort();
              return (
                <div style={{ marginTop: 14 }}>
                  <div style={{ fontSize: 12, fontWeight: 600, marginBottom: 6 }}>
                    {isAr ? `الفروق مقابل ${compareName}` : `Diff vs ${compareName}`}
                  </div>
                  <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 12 }}>
                    <div>
                      <Badge tone="success" dot>{isAr ? `هنا فقط (${onlyA.length})` : `Only here (${onlyA.length})`}</Badge>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
                        {onlyA.length === 0 ? <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>
                          : onlyA.map(k => <Badge key={k} tone="neutral">{k}</Badge>)}
                      </div>
                    </div>
                    <div>
                      <Badge tone="warning" dot>{isAr ? `في ${compareName} فقط (${onlyB.length})` : `Only in ${compareName} (${onlyB.length})`}</Badge>
                      <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
                        {onlyB.length === 0 ? <span style={{ color: 'var(--ink-3)', fontSize: 12 }}>—</span>
                          : onlyB.map(k => <Badge key={k} tone="neutral">{k}</Badge>)}
                      </div>
                    </div>
                  </div>
                </div>
              );
            })()}
          </>}
    </Card>
  );
}

// ─── Effective access tab ────────────────────────────────────────────────────

function AccessTab() {
  const { lang } = useI18n();
  const isAr = lang === 'ar';
  const toast = useToast();
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [userId, setUserId] = useState<number | ''>('');
  const [ea, setEa] = useState<GovEffectiveAccessDto | null>(null);

  useEffect(() => {
    adminApi.users(0, 100).then(p => setUsers(p.content ?? [])).catch(() => setUsers([]));
  }, []);

  useEffect(() => {
    if (userId === '') { setEa(null); return; }
    governanceApi.effectiveAccess(userId)
      .then(setEa)
      .catch(e => toast.danger(e instanceof ApiError ? e.message : 'Failed to load effective access'));
  }, [userId]);  // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Card title={isAr ? 'الوصول الفعلي للمستخدم' : 'Per-user effective access'} Icon={UserSearch}
      action={
        <select value={userId} onChange={e => setUserId(e.target.value === '' ? '' : Number(e.target.value))} style={INPUT}>
          <option value="">{isAr ? 'اختر مستخدماً…' : 'Select a user…'}</option>
          {users.map(u => <option key={u.id} value={u.id}>{u.username}</option>)}
        </select>
      }>
      {!ea
        ? <div style={EMPTY}>{isAr ? 'اختر مستخدماً لعرض صلاحياته المحسوبة.' : 'Select a user to see their resolved grants.'}</div>
        : <>
            <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', padding: '4px 0 12px' }}>
              {ea.platformAdmin && <Badge tone="danger" dot>{isAr ? 'تجاوز مدير المنصّة' : 'PLATFORM_ADMIN bypass'}</Badge>}
              {/* F2-62: an inactive role is still assigned but grants nothing. Rendering it as a
                  plain info badge would make an inert assignment look like a live one and leave
                  its absence from the grants table below looking like a bug. */}
              {ea.roles.map(r => (ea.inactiveRoles ?? []).includes(r)
                ? <Badge key={r} tone="warning">{r} · {isAr ? 'غير مفعّل — لا يمنح صلاحيات' : 'inactive — grants nothing'}</Badge>
                : <Badge key={r} tone="info">{r}</Badge>)}
            </div>
            <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'الوحدة' : 'Module'}</th>
                <th>{isAr ? 'الإجراء' : 'Action'}</th>
                <th>{isAr ? 'عبر الأدوار' : 'Via roles'}</th>
              </tr></thead>
              <tbody>
                {ea.grants.map(g => (
                  <tr key={`${g.module}/${g.action}`}>
                    <td className="mono text-2xs">{g.module}</td>
                    <td><Badge tone="neutral">{g.action}</Badge></td>
                    <td className="text-2xs">{g.viaRoles.join(', ')}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <div style={{ ...EMPTY, paddingBottom: 4 }}>
              <ShieldCheck size={13} style={{ verticalAlign: -2, marginInlineEnd: 4 }} />
              {isAr ? `${ea.grants.length} صلاحية محسوبة` : `${ea.grants.length} resolved grants`}
            </div>
          </>}
    </Card>
  );
}

const INPUT: React.CSSProperties = {
  padding: '6px 10px', fontSize: 12, minWidth: 140, border: '1px solid var(--border)',
  borderRadius: 'var(--r-sm)', background: 'var(--surface-1)', color: 'var(--ink-1)',
};
const EMPTY: React.CSSProperties = { padding: 14, color: 'var(--ink-3)', fontSize: 13 };
