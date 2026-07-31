import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  ShieldCheck, KeyRound, Grid3X3, UserSearch, Plus, Save, Trash2, RefreshCw,
  Inbox, ClipboardCheck, Scale, Siren, BarChart3, Copy,
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
        {tab === 'matrix'     && <MatrixTab roles={roles} />}
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

const ACTIONS = ['READ', 'WRITE', 'APPROVE', 'DELETE', 'TRIAGE'] as const;

function MatrixTab({ roles }: { roles: GovRoleDto[] }) {
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
            <table className="grc-table">
              <thead><tr>
                <th>{isAr ? 'الوحدة' : 'Module'}</th>
                {ACTIONS.map(a => <th key={a} style={{ textAlign: 'center' }}>{a}</th>)}
              </tr></thead>
              <tbody>
                {modules.map(({ module, actions }) => (
                  <tr key={module}>
                    <td className="mono text-2xs">{module}</td>
                    {ACTIONS.map(a => (
                      <td key={a} style={{ textAlign: 'center' }}>
                        {actions.has(a)
                          ? <input type="checkbox" checked={granted.has(`${module}/${a}`)}
                              disabled={!editable}
                              onChange={() => toggle(module, a)} />
                          : <span style={{ color: 'var(--ink-3)' }}>·</span>}
                      </td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
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
