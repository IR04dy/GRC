import { useEffect, useState } from 'react';
import { Users, UserCheck, UserPlus, Shield, Filter, Download, Plus, Building2, ScrollText } from 'lucide-react';
import { Shell } from '../../components/layout/Shell';
import { PageHeader } from '../../components/layout/PageHeader';
import { Tabs, type TabItem } from '../../components/ui/Tabs';
import { Card } from '../../components/ui/Card';
import { Button } from '../../components/ui/Button';
import { Kpi } from '../../components/ui/Kpi';
import { Badge } from '../../components/ui/Badge';
import { DataTable, type Column } from '../../components/ui/DataTable';
import { EmptyState } from '../../components/ui/EmptyState';
import { FormModal, type FieldDef } from '../../components/ui/FormModal';
import { Timeline, type TimelineEvent } from '../../components/ui/Timeline';
import { RowActions } from '../../components/ui/RowActions';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { useI18n } from '../../lib/i18n';
import { useApiList } from '../../lib/useApiResource';
import { adminApi, ApiError, type AdminUserDto, type AdminUserRequest, type AdminRoleDto } from '../../lib/api';
import { isAuthenticated } from '../../lib/auth';
import { useToast } from '../../lib/toast';
import { ADMIN_USERS, ADMIN_ROLES, ADMIN_AUDIT_EVENTS } from './mockAdmin';

type TabId = 'users' | 'roles' | 'orgs' | 'audit';

const ROLE_OPTIONS = [
  { value: 'PLATFORM_ADMIN', label: 'Platform Admin' },
  { value: 'RISK_MANAGER',   label: 'Risk Manager' },
  { value: 'AUDITOR',        label: 'Auditor' },
  { value: 'PRIVACY_OFFICER',label: 'Privacy Officer' },
  { value: 'COMPLIANCE_LEAD',label: 'Compliance Lead' },
  { value: 'BCM_COORDINATOR',label: 'BCM Coordinator' },
  { value: 'CYBER_ANALYST',  label: 'Cyber Analyst' },
];

interface UserFormShape {
  username: string; email: string;
  fullNameEn?: string; fullNameAr?: string;
  password: string; role: string;
}
const EMPTY: UserFormShape = {
  username: '', email: '', fullNameEn: '', fullNameAr: '', password: '', role: 'AUDITOR',
};
function fieldsFor(isEdit: boolean, t: (k: string, fb?: string) => string): FieldDef[] {
  return [
    { kind: 'text', key: 'username',   label: t('adminFldUsername') + ' *', required: true, maxLength: 50, span: 6 },
    { kind: 'text', key: 'email',      label: t('fldEmail') + ' *',         required: true, maxLength: 200, span: 6 },
    { kind: 'text', key: 'fullNameEn', label: t('adminFldFullEn'), maxLength: 200, span: 6 },
    { kind: 'text', key: 'fullNameAr', label: t('adminFldFullAr'), maxLength: 200, span: 6 },
    { kind: 'text', key: 'password',   label: isEdit ? t('adminFldPwEdit') : t('adminFldPwInit') + ' *', required: !isEdit, maxLength: 100, span: 6 },
    { kind: 'select', key: 'role',     label: t('adminFldRole') + ' *', options: ROLE_OPTIONS, required: true, span: 6 },
  ];
}
function userToShape(u: AdminUserDto): UserFormShape {
  return {
    username: u.username, email: u.email,
    fullNameEn: u.fullNameEn ?? '', fullNameAr: u.fullNameAr ?? '',
    password: '',
    role: u.roles?.[0] ?? 'AUDITOR',
  };
}

export function AdminPage() {
  const { t } = useI18n();
  const toast = useToast();
  const [tab, setTab] = useState<TabId>('users');
  const [open, setOpen] = useState(false);
  const [editing, setEditing] = useState<AdminUserDto | null>(null);
  const [confirmDel, setConfirmDel] = useState<AdminUserDto | null>(null);
  const [busy, setBusy] = useState(false);

  const users = useApiList<AdminUserDto>(() => adminApi.users(0, 100), ADMIN_USERS as unknown as AdminUserDto[]);

  const [roles, setRoles] = useState<AdminRoleDto[]>([]);
  const [rolesLoading, setRolesLoading] = useState<boolean>(isAuthenticated());
  useEffect(() => {
    if (!isAuthenticated()) {
      setRoles(ADMIN_ROLES as unknown as AdminRoleDto[]);
      setRolesLoading(false);
      return;
    }
    adminApi.roles()
      .then(r => setRoles(r && r.length > 0 ? r : (ADMIN_ROLES as unknown as AdminRoleDto[])))
      .catch(() => setRoles(ADMIN_ROLES as unknown as AdminRoleDto[]))
      .finally(() => setRolesLoading(false));
  }, []);

  const tabs: TabItem<TabId>[] = [
    { id: 'users', label: t('adminTabUsers') },
    { id: 'roles', label: t('adminTabRoles') },
    { id: 'orgs',  label: t('adminTabOrganisations') },
    { id: 'audit', label: t('adminTabAudit') },
  ];

  const userCols: Column<AdminUserDto>[] = [
    { header: t('adminUserCol'), cell: r => (
      <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
        <div className="avatar" style={{ width: 30, height: 30, fontSize: 11 }}>{initialsFor(r)}</div>
        <div>
          <div style={{ fontWeight: 600 }}>{r.fullNameEn ?? r.username}</div>
          <div className="mono" style={{ fontSize: 11 }}>{r.username}</div>
        </div>
      </div>
    ) },
    { header: t('colEmail'),          cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.email}</span> },
    { header: t('colRoles'),          cell: r => <div style={{ display: 'flex', gap: 4, flexWrap: 'wrap' }}>{(r.roles ?? []).map(role => <Badge key={role} tone="primary">{role.replace(/_/g, ' ')}</Badge>)}</div> },
    { header: t('colCreated'),        cell: r => fmtDate(r.createdAt), mono: true },
    { header: t('colStatus'),         cell: r => <Badge tone={r.isLocked ? 'danger' : r.isActive ? 'success' : 'neutral'} dot>{r.isLocked ? t('badgeLocked') : r.isActive ? t('statusActive') : t('badgeInactive')}</Badge> },
    { header: '',                     align: 'end', width: '90px',
      cell: r => <RowActions onEdit={() => { setEditing(r); setOpen(true); }} onDelete={() => setConfirmDel(r)} /> },
  ];

  const roleCols: Column<AdminRoleDto>[] = [
    { header: t('colCode'),       cell: r => r.roleName, mono: true },
    { header: t('colDisplayEn'),  cell: r => <span style={{ fontWeight: 600 }}>{r.displayNameEn ?? r.roleName}</span> },
    { header: t('colDisplayAr'),  cell: r => <span style={{ color: 'var(--ink-2)' }}>{r.displayNameAr ?? '—'}</span> },
    { header: t('colSystem'),     cell: r => r.isSystemRole ? <Badge tone="primary">{t('badgeSystem')}</Badge> : <Badge tone="neutral">{t('badgeCustom')}</Badge> },
    { header: t('colActive'),     cell: r => <Badge tone={r.isActive ? 'success' : 'neutral'} dot>{r.isActive ? t('statusActive') : t('badgeDisabled')}</Badge> },
  ];

  const orgs = [
    { id: 1, name: 'TSS Demo Org',        country: 'Saudi Arabia', users: users.data.length, modules: 9, status: 'ACTIVE' },
    { id: 2, name: 'TSS Sandbox',         country: 'Saudi Arabia', users: 4, modules: 4, status: 'ACTIVE' },
    { id: 3, name: 'Pilot Customer A',    country: 'UAE', users: 12, modules: 6, status: 'ACTIVE' },
  ];
  const auditEvents: TimelineEvent[] = ADMIN_AUDIT_EVENTS.map(ev => ({
    id: ev.id,
    time: ev.at,
    title: `${ev.actor} · ${ev.action.replace(/_/g, ' ')}`,
    Icon: Users,
    tone: ev.outcome === 'DENIED' || ev.outcome === 'ERROR' ? 'danger'
        : ev.action.startsWith('CREATE') || ev.action.startsWith('GRANT') ? 'success'
        : 'primary',
    body: <span><Badge tone={ev.outcome === 'SUCCESS' ? 'success' : 'danger'}>{ev.outcome}</Badge> · <span className="mono" style={{ fontSize: 11 }}>{ev.entity}</span> · <span style={{ color: 'var(--ink-3)' }}>{ev.ip}</span></span>,
  }));

  const active = users.data.filter(u => u.isActive && !u.isLocked).length;
  const pending = users.data.filter(u => !u.isActive).length;

  async function onSubmit(values: UserFormShape) {
    const body: AdminUserRequest = {
      username: values.username, email: values.email,
      fullNameEn: values.fullNameEn, fullNameAr: values.fullNameAr,
      ...(values.password ? { password: values.password } : {}),
      roles: values.role ? [values.role] : [],
      isActive: true,
    };
    try {
      if (editing) await adminApi.updateUser(editing.id, body);
      else await adminApi.createUser(body);
      toast.success(editing ? 'User updated' : t('toastRiskAdded'));
      setOpen(false); setEditing(null); users.reload();
    } catch (err) {
      toast.danger(err instanceof ApiError ? err.message : t('toastRiskFailed'));
    }
  }
  async function executeDelete() {
    if (!confirmDel) return;
    setBusy(true);
    try { await adminApi.deleteUser(confirmDel.id); toast.success('User deleted'); users.reload(); }
    catch (err) { toast.danger(err instanceof ApiError ? err.message : 'Delete failed'); }
    finally { setBusy(false); setConfirmDel(null); }
  }

  const isDemo = users.isDemo;

  return (
    <Shell title={t('adminTitle')} sub="Platform Administration">
      <PageHeader
        eyebrow={t('adminEyebrow')} title={t('adminTitle')} sub={t('adminSub')}
        actions={
          <>
            {isDemo && <Badge tone="warning">{t('demoMode')}</Badge>}
            <Button variant="secondary" Icon={Filter}>{t('actionFilter')}</Button>
            <Button variant="secondary" Icon={Download}>{t('actionExport')}</Button>
            <Button variant="primary"   Icon={Plus} onClick={() => { setEditing(null); setOpen(true); }}>{t('actionNew')}</Button>
          </>
        }
      />
      <div className="kpi-grid">
        <Kpi Icon={Users}     label={t('adminKpiUsers')}   value={users.data.length} tone="primary" />
        <Kpi Icon={UserCheck} label={t('adminKpiActive')}  value={active} tone="success" />
        <Kpi Icon={UserPlus}  label={t('adminKpiPending')} value={pending} tone="warning" />
        <Kpi Icon={Shield}    label={t('adminKpiRoles')}   value={roles.length || ROLE_OPTIONS.length} tone="info" />
      </div>
      <Tabs items={tabs} active={tab} onChange={setTab} />

      {tab === 'users' && (
        <Card padded={false}>
          {users.loading ? <LoadingRow /> : <DataTable columns={userCols} rows={users.data} empty={<EmptyState />} />}
        </Card>
      )}
      {tab === 'roles' && (
        <Card padded={false}>
          {rolesLoading ? <LoadingRow /> : roles.length === 0
            ? <EmptyState Icon={Shield} title="No roles loaded" sub="Roles cluster permissions per module · action." />
            : <DataTable columns={roleCols} rows={roles} />}
        </Card>
      )}
      {tab === 'orgs' && (
        <Card padded={false}>
          <DataTable
            columns={[
              { header: t('colOrganisation'),  cell: (o: typeof orgs[number]) => <span style={{ fontWeight: 600 }}>{o.name}</span> },
              { header: t('colCountry'),       cell: (o: typeof orgs[number]) => o.country },
              { header: t('colUsers'),         cell: (o: typeof orgs[number]) => o.users, mono: true },
              { header: t('colModules'),       cell: (o: typeof orgs[number]) => <Badge tone="primary">{o.modules}</Badge> },
              { header: t('colStatus'),        cell: (o: typeof orgs[number]) => <Badge tone="success" dot>{o.status}</Badge> },
            ]}
            rows={orgs}
            empty={<EmptyState Icon={Building2} />}
          />
        </Card>
      )}
      {tab === 'audit' && (
        <Card title={t('cardAuditTrail')} Icon={ScrollText} action={<Badge tone="primary">{auditEvents.length}</Badge>}>
          {auditEvents.length === 0 ? <EmptyState Icon={ScrollText} title="—" /> : <Timeline events={auditEvents} />}
        </Card>
      )}

      <FormModal<UserFormShape>
        open={open}
        title={editing ? `${t('editPrefix')} · ${editing.username}` : t('newUserTitle')}
        fields={fieldsFor(!!editing, t)}
        initial={editing ? userToShape(editing) : EMPTY}
        onClose={() => { setOpen(false); setEditing(null); }}
        onSubmit={onSubmit}
      />
      <ConfirmDialog
        open={!!confirmDel} destructive busy={busy}
        title="Delete this user?"
        message={confirmDel ? `User "${confirmDel.username}" will be removed. This cannot be undone.` : ''}
        confirmLabel="Delete"
        onCancel={() => setConfirmDel(null)} onConfirm={executeDelete}
      />
    </Shell>
  );
}

function initialsFor(u: AdminUserDto): string {
  const name = u.fullNameEn ?? u.username ?? '';
  const parts = name.trim().split(/\s+/).slice(0, 2);
  return parts.map(p => p.charAt(0).toUpperCase()).join('') || '??';
}
function fmtDate(s?: string): string { return s ? s.slice(0, 10) : '—'; }
function LoadingRow() {
  const { t } = useI18n();
  return <div style={{ padding: 32, textAlign: 'center', color: 'var(--ink-3)' }}>{t('riskLoading')}</div>;
}
