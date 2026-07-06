// WF-3 — the §5 reusable WorkflowActorSelector. Lets a requester pick exactly WHO receives a
// request: by user / role / department (live lists), committee (id) or external contact (email),
// each with an actor role (assignee / reviewer / approver / observer / escalation) and — in
// SEQUENTIAL routing — a step number. Client-side validation mirrors the backend §5 gates so
// obvious mistakes never leave the browser; the server re-validates everything anyway.
import { useEffect, useMemo, useState } from 'react';
import { Plus, Trash2 } from 'lucide-react';
import { Button } from '../ui/Button';
import { Badge } from '../ui/Badge';
import { Select } from '../ui/Select';
import { Input } from '../ui/Input';
import { useI18n } from '../../lib/i18n';
import { adminApi, departmentsApi, type AdminUserDto, type DepartmentDto, type AdminRoleDto } from '../../lib/api';
import type { RecipientType, ActorRole, RoutingMode, WorkflowRecipientInput } from '../../lib/workflowRequests';

const RECIPIENT_TYPES: RecipientType[] = ['USER', 'ROLE', 'DEPARTMENT', 'COMMITTEE', 'EXTERNAL'];
const ACTOR_ROLES: ActorRole[] = ['ASSIGNEE', 'REVIEWER', 'APPROVER', 'OBSERVER', 'ESCALATION'];
/** Fallback when the caller can't read /admin/roles (non-admin composer users). */
const FALLBACK_ROLES = ['CISO', 'SECURITY_ANALYST', 'RISK_MANAGER', 'COMPLIANCE_OFFICER',
  'BCM_MANAGER', 'AUDITOR', 'INTERNAL_AUDITOR', 'CYBER_MANAGER', 'PLATFORM_ADMIN'];

/** §5 client-side gates — returns an error message or null. Mirrors the backend validation. */
export function validateRecipients(recipients: WorkflowRecipientInput[], routingMode: RoutingMode, ar: boolean): string | null {
  if (recipients.length === 0)
    return ar ? 'مطلوب مستلم واحد على الأقل' : 'At least one recipient is required';
  for (const r of recipients) {
    if ((r.recipientType === 'USER' || r.recipientType === 'DEPARTMENT' || r.recipientType === 'COMMITTEE') && !r.recipientId)
      return ar ? `حدد الهدف لمستلم ${r.recipientType}` : `Select a target for the ${r.recipientType} recipient`;
    if (r.recipientType === 'ROLE' && !r.recipientKey)
      return ar ? 'حدد اسم الدور' : 'Select a role name for the ROLE recipient';
    if (r.recipientType === 'EXTERNAL' && !(r.recipientKey ?? '').includes('@'))
      return ar ? 'المستلم الخارجي يتطلب بريداً صالحاً' : 'EXTERNAL recipient requires a valid contact email';
  }
  if (routingMode === 'SEQUENTIAL') {
    const steps = [...new Set(recipients.map(r => r.stepNo ?? 1))].sort((a, b) => a - b);
    for (let i = 0; i < steps.length; i++) {
      if (steps[i] !== i + 1)
        return ar ? 'الخطوات التسلسلية يجب أن تكون متتابعة تبدأ من 1' : 'Sequential steps must be contiguous starting at 1';
    }
  }
  return null;
}

interface Props {
  recipients: WorkflowRecipientInput[];
  onChange: (next: WorkflowRecipientInput[]) => void;
  routingMode: RoutingMode;
}

export function WorkflowActorSelector({ recipients, onChange, routingMode }: Props) {
  const { lang } = useI18n();
  const ar = lang === 'ar';
  const [users, setUsers] = useState<AdminUserDto[]>([]);
  const [departments, setDepartments] = useState<DepartmentDto[]>([]);
  const [roles, setRoles] = useState<string[]>(FALLBACK_ROLES);
  const sequential = routingMode === 'SEQUENTIAL';

  useEffect(() => {
    // Live lists where the caller may read them; graceful fallbacks otherwise.
    adminApi.users(0, 200).then(p => setUsers(p.content.filter(u => u.isActive))).catch(() => setUsers([]));
    departmentsApi.list().then(setDepartments).catch(() => setDepartments([]));
    adminApi.roles().then((r: AdminRoleDto[]) => setRoles(r.map(x => x.roleName))).catch(() => setRoles(FALLBACK_ROLES));
  }, []);

  const userOptions = useMemo(() => users.map(u => ({
    value: String(u.id), label: `${u.fullNameEn ?? u.username} (${u.username})`,
  })), [users]);
  const deptOptions = useMemo(() => departments.map(d => ({
    value: String(d.id), label: d.code ? `${d.code} · ${d.nameEn}` : d.nameEn,
  })), [departments]);

  function patch(i: number, p: Partial<WorkflowRecipientInput>) {
    onChange(recipients.map((r, idx) => idx === i ? { ...r, ...p } : r));
  }
  function add() {
    onChange([...recipients, {
      recipientType: 'USER', actorRole: 'ASSIGNEE',
      stepNo: sequential ? Math.max(1, ...recipients.map(r => r.stepNo ?? 1)) : 1,
    }]);
  }
  function remove(i: number) { onChange(recipients.filter((_, idx) => idx !== i)); }

  const error = validateRecipients(recipients, routingMode, ar);

  return (
    <div>
      <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
        <span style={{ fontWeight: 700, fontSize: 13 }}>{ar ? 'المستلمون' : 'Recipients'}</span>
        <Badge tone="neutral">{recipients.length}</Badge>
        <span style={{ marginInlineStart: 'auto' }}>
          <Button size="sm" variant="secondary" onClick={add}>
            <Plus size={13} /> {ar ? 'إضافة مستلم' : 'Add recipient'}
          </Button>
        </span>
      </div>

      {recipients.length === 0 && (
        <div style={{ padding: 14, fontSize: 12, color: 'var(--ink-3)', border: '1px dashed var(--border)', borderRadius: 8 }}>
          {ar ? 'لا يُرسل الطلب "في الهواء" — أضف مستخدماً أو دوراً أو إدارة.' : 'A request is never sent "into the air" — add a user, role, or department.'}
        </div>
      )}

      {recipients.map((r, i) => (
        <div key={i} style={{
          display: 'grid', gap: 8, alignItems: 'end', padding: '10px 12px', marginBottom: 8,
          gridTemplateColumns: sequential ? '110px 1.6fr 130px 70px auto' : '110px 1.6fr 130px auto',
          border: '1px solid var(--border)', borderRadius: 8, background: 'var(--surface-1)',
        }}>
          <Select label={ar ? 'الفئة' : 'Category'} value={r.recipientType}
            onChange={e => patch(i, { recipientType: e.target.value as RecipientType, recipientId: undefined, recipientKey: undefined })}
            options={RECIPIENT_TYPES.map(t => ({ value: t, label: t }))} />

          {r.recipientType === 'USER' && (userOptions.length > 0 ? (
            <Select label={ar ? 'المستخدم' : 'User'} value={r.recipientId ? String(r.recipientId) : ''}
              onChange={e => patch(i, { recipientId: e.target.value ? Number(e.target.value) : undefined })}
              options={[{ value: '', label: ar ? '— اختر —' : '— select —' }, ...userOptions]} />
          ) : (
            <Input label={ar ? 'معرّف المستخدم' : 'User id'} type="number" value={r.recipientId ?? ''}
              onChange={e => patch(i, { recipientId: e.target.value ? Number(e.target.value) : undefined })} />
          ))}
          {r.recipientType === 'ROLE' && (
            <Select label={ar ? 'الدور' : 'Role'} value={r.recipientKey ?? ''}
              onChange={e => patch(i, { recipientKey: e.target.value || undefined })}
              options={[{ value: '', label: ar ? '— اختر —' : '— select —' }, ...roles.map(x => ({ value: x, label: x }))]} />
          )}
          {r.recipientType === 'DEPARTMENT' && (
            <Select label={ar ? 'الإدارة' : 'Department'} value={r.recipientId ? String(r.recipientId) : ''}
              onChange={e => patch(i, { recipientId: e.target.value ? Number(e.target.value) : undefined })}
              options={[{ value: '', label: ar ? '— اختر —' : '— select —' }, ...deptOptions]} />
          )}
          {r.recipientType === 'COMMITTEE' && (
            <Input label={ar ? 'معرّف اللجنة' : 'Committee id'} type="number" value={r.recipientId ?? ''}
              onChange={e => patch(i, { recipientId: e.target.value ? Number(e.target.value) : undefined })} />
          )}
          {r.recipientType === 'EXTERNAL' && (
            <Input label={ar ? 'بريد المستلم الخارجي' : 'External contact email'} value={r.recipientKey ?? ''}
              placeholder="auditor@firm.com"
              onChange={e => patch(i, { recipientKey: e.target.value || undefined })} />
          )}

          <Select label={ar ? 'دور الفاعل' : 'Actor role'} value={r.actorRole ?? 'ASSIGNEE'}
            onChange={e => patch(i, { actorRole: e.target.value as ActorRole })}
            options={ACTOR_ROLES.map(x => ({ value: x, label: x }))} />

          {sequential && (
            <Input label={ar ? 'الخطوة' : 'Step'} type="number" min={1} value={r.stepNo ?? 1}
              onChange={e => patch(i, { stepNo: e.target.value ? Number(e.target.value) : 1 })} />
          )}

          <Button size="sm" variant="ghost" onClick={() => remove(i)} title={ar ? 'إزالة' : 'Remove'}>
            <Trash2 size={14} />
          </Button>
        </div>
      ))}

      {error && recipients.length > 0 && (
        <div style={{ fontSize: 11, color: 'var(--danger-fg)', marginTop: 4 }}>{error}</div>
      )}
      {sequential && !error && recipients.length > 0 && (
        <div style={{ fontSize: 11, color: 'var(--ink-3)', marginTop: 4 }}>
          {ar ? 'التوجيه التسلسلي: يعمل مستلمو كل خطوة بالترتيب — تنتقل العصا للخطوة التالية عند اكتمال السابقة.'
              : 'Sequential routing: each step acts in order — the baton passes when the previous step completes.'}
        </div>
      )}
    </div>
  );
}
